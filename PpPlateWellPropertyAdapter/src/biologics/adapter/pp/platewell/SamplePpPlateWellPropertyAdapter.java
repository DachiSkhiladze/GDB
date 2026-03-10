package biologics.adapter.pp.platewell;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.entity.PpPlate;
import genedata.bx.adapter.entity.PpPlateWell;
import genedata.bx.adapter.pp.platewell.PpPlateWellPropertyAdapter;
import genedata.bx.adapter.pp.platewell.PpPlateWellPropertyAdapterCallback;
import genedata.bx.adapter.pp.platewell.PpPlateWellPropertyAdapterOptions;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads Plate layout maps filled with Aliquot barcodes and updates the Plates of
 * an existing Protein Production Plate Set.
 * <p> 
 * Each layout map starts with the term "Plate Barcode", followed by a delimiter
 * and the barcode of the Plate which is updated.
 * It is allowed for single wells to be empty. The layout map may look like this:
 * <pre>
 * Plate Barcode	BC123
 * E017-0123-A01	E017-0123-A02	E017-0123-A03	E017-0123-A04
 * E017-0123-B01	E017-0123-B02	E017-0123-B03	E017-0123-B04
 * E017-0123-C01	E017-0123-C02	E017-0123-C03	E017-0123-C04
 * E017-0123-D01	E017-0123-D02	E017-0123-D03	E017-0123-D04
 * </pre>
 * Note that Aliquot barcodes can only be set if an Aliquot Group is assigned to 
 * the specific well.
 * 
 * @author Genedata Biologics (tg)
 * @since GDB-9.2
 */
public class SamplePpPlateWellPropertyAdapter implements PpPlateWellPropertyAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	
	private Pattern delimiter = Pattern.compile("\t"); // default: tab
	private Reporter reporter;
	private PpPlateWellPropertyAdapterCallback callback;
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		this.delimiter = Pattern.compile("csv".equalsIgnoreCase(configuration.get("table_export_import_format")) ? "," : "\t");
	}
	
	@Override
	public void options(PpPlateWellPropertyAdapterOptions options) {
		// no custom parameters
	}
	
	@Override
	public void perform(PpPlateWellPropertyAdapterCallback callback) {
		this.callback = callback;
		this.reporter = callback.getInvocationContext().getReporter();
		
		try {
			parseContent();
		}
		catch (Exception e) {
			reporter.error("Error when reading file: #0.", e.getMessage());
		}
	}

	private void parseContent() throws Exception {
		final String PLATE_BARCODE = "Plate Barcode";
		LineNumberReader reader = new LineNumberReader(
				new InputStreamReader(callback.getInputStream(), Charset.forName("UTF-8")));
		
		String[] fields;
		int lineNr = 0;
		int plateRow = 0;
		int countPlates = 0;
		int countUpdated = 0;
		PpPlate plate = null;
		
		while (null != (fields = parseNextLine(reader))) {
			lineNr ++;
			
			// New plate?
			if (fields.length > 0 && PLATE_BARCODE.equalsIgnoreCase(fields[0].trim())) {
				plate = identifyPlate(lineNr, fields);
				if (null == plate) {
					// do not continue otherwise we would get errors about too many rows
					return;	
				}
				countPlates ++;
				plateRow = 0;	// start counting again
				continue;
			}
			// Ignore everything until first plate
			if (null == plate) {
				continue;
			}
			
			// Check number of columns
			if (fields.length > plate.getPlateFormat().getNumberOfColumns()) {
				reporter.error("Line #0: Wrong number of columns for #1 #2, expected #3 but got #4.",
						lineNr,
						callback.getPpPlateInformationProvider().getSingularEntityLabel(),
						plate.getBarcode(),
						plate.getPlateFormat().getNumberOfColumns(),
						fields.length);
				continue;
			}
			
			// Next row
			plateRow++;
			if (0 == fields.length) {	// empty lines within a plate block are counted, if after they shall not lead to error
				continue;
			}
			if (plateRow > plate.getPlateFormat().getNumberOfRows()) {
				reporter.error("Line #0: Wrong number of rows for #1 #2, expected #3 but got #4.",
						lineNr,
						callback.getPpPlateInformationProvider().getSingularEntityLabel(),
						plate.getBarcode(),
						plate.getPlateFormat().getNumberOfRows(),
						plateRow);
				continue;
			}
			
			countUpdated += parseAliquotBarcodes(lineNr, plate, plateRow, fields);
		}
		
		if (0 == reporter.getNumberOfErrors()) {
			reporter.info("Successfully parsed #0 #1.", 
					lineNr,
					1 == lineNr ? "line" : "lines");
			
			if (0 != countUpdated) {
				reporter.info("Updated the Aliquot barcode for #0 #1 #2.", 
						countUpdated,
						callback.getPpPlateInformationProvider().getSingularEntityLabel(),
						1 == countUpdated ? "well" : "wells");
			}
			else if (0 != countPlates) {
				reporter.info("No Aliquot barcode was updated."); 
			}
			else if (lineNr > 0) {
				reporter.error("Could not identify a line starting with '#0'.", PLATE_BARCODE);
			}
		}
	}
	
	private PpPlate identifyPlate(int lineNr, String[] fields) {
		String barcode = fields.length > 1 ? fields[1] : null;
		
		if (null == barcode || barcode.trim().isEmpty()) {
			reporter.error("Line #0: Unable to identify #1 because barcode is missing.", 
					lineNr,
					callback.getPpPlateInformationProvider().getSingularEntityLabel());
			return null;
		}
		
		PpPlate plate = callback.getPpPlateInformationProvider().retrieveByBarcode(barcode);
		if (null == plate) {
			reporter.error("Line #0: Could not find #1 with barcode '#2'.", 
					lineNr,
					callback.getPpPlateInformationProvider().getSingularEntityLabel(),
					barcode);
		}
		return plate;
	}
	
	private int parseAliquotBarcodes(int lineNr, PpPlate plate, int plateRow, String[] fields) {
		int count = 0;
		for (int i=0; i<fields.length; i++) {
			int plateCol = i+1;
			String barcode = fields[i].trim();
			
			// empty wells are ignored, no support for clearing away barcodes
			if (barcode.isEmpty()) {
				continue;
			}
			
			String address = callback.getWellAddressConverter().formatWellAddress(plateRow, plateCol);
			try {
				PpPlateWell well = callback.getPpPlateWell(plate, plateRow, plateCol);
				
				// warn if wells do not exist but we have a barcode to set
				if (null == well) {
					reporter.warn("Line #0: Cannot assign Aliquot barcode '#1' to empty well #2.", 
							lineNr, barcode, address);
					continue;
				}
				
				if (null == well.getAliquot() || ! barcode.equals(well.getAliquot().getBarcode())) {
					callback.getPlateWellMutator().setAliquot(well, barcode);
					count++;
				}
			}
			catch (Exception e) {
				reporter.error("Line #0, well #1: #2.", lineNr, address, e.getMessage());
				// continue as we want to report additional errors
			}
		}
		return count;
	}
	
	private String[] parseNextLine(LineNumberReader reader) throws Exception {
		String line = reader.readLine();
		if (null == line) {					// EOF
			return null;
		}
		// if delimiter (esp. tab) is found the line can never be empty
		if (! delimiter.matcher(line).find() && line.trim().isEmpty()) {
			return new String[0];
		}
		return delimiter.split(line, -1);
	}
}
