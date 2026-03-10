package biologics.adapter.pp.plateset;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.entity.PpPlate;
import genedata.bx.adapter.entity.PpPlateSet;
import genedata.bx.adapter.entity.PpPlateWell;
import genedata.bx.adapter.entity.ProteinPurificationBatch;
import genedata.bx.adapter.pp.plateset.PpPlateSetCreateFromFileAdapter;
import genedata.bx.adapter.pp.plateset.PpPlateSetCreateFromFileAdapterCallback;
import genedata.bx.adapter.pp.plateset.PpPlateSetCreateFromFileAdapterOptions;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads Plate layout maps filled with Protein Purification Batch ID/Names and creates a Protein Production
 * Plate Set from them.
 * <p>
 * Each layout map starts with the term "Plate Number" and resolves into one Plate. 
 * It is allowed for single wells to be empty. The layout map may look like this:
 * <pre>
 * Plate Number	1
 * Plate Name	abc
 * Plate Barcode	BC123
 * B629a-Fab-Glu	B629a-Fab-Ano	B629a-Fab-Hep1	B629a-Fab-Hep2
 * B629a-Igg-Glu	B629a-Igg-Ano	B629a-Igg-Hep1	B629a-Igg-Hep2
 * B633m-kap-Glu	B633m-kap-Ano	B633m-kap-Hep1	B633m-kap-Hep2
 * </pre>
 * All Protein Purification Batches need to be linked to the current Production Dataset.
 * 
 * @author Genedata Biologics (tg)
 * @since GDB-10.1
 */
public class SamplePpPlateSetCreateFromFileAdapter implements PpPlateSetCreateFromFileAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	
	private Pattern delimiter = Pattern.compile("\t"); // default: tab
	private Reporter reporter;
	private PpPlateSetCreateFromFileAdapterCallback callback;
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		this.delimiter = Pattern.compile("csv".equalsIgnoreCase(configuration.get("table_export_import_format")) ? "," : "\t");
	}
	
	@Override
	public void options(PpPlateSetCreateFromFileAdapterOptions options) {
		// no custom parameters
	}
	
	@Override
	public PpPlateSet perform(PpPlateSetCreateFromFileAdapterCallback callback) {
		this.callback = callback;
		this.reporter = callback.getInvocationContext().getReporter();
		
		if (! "P".equals(callback.getWellContentEntityType())) {
			reporter.error("This adapter can only handle #0 and is not suited for the selected Material Entity Type.",
					callback.getProteinPurificationBatchInformationProvider().getPluralEntityLabel());
			return null;
		}
		
		PpPlateSet ps = callback.createPpPlateSet();
		
		try {
			parseContent(ps);
		}
		catch (Exception e) {
			reporter.error("Error when reading file: #0.", e.getMessage());
		}
		
		return 0 == reporter.getNumberOfErrors() ? ps : null;
	}
	
	private void parseContent(PpPlateSet ps) throws Exception {
		LineNumberReader reader = new LineNumberReader(
				new InputStreamReader(callback.getInputStream(), Charset.forName("UTF-8")));
		
		String[] fields;
		int lineNr = 0;
		int plateRow = 0;
		PpPlate plate = null;
		int countCreated = 0;
		
		while (null != (fields = parseNextLine(reader))) {
			lineNr ++;
			if (0 == fields.length) {
				continue;
			}
			
			// New plate?
			if ("Plate Number".equalsIgnoreCase(fields[0].trim())) {
				Integer plateNumber = parseInt(lineNr, fields.length > 1 ? fields[1] : null, "Plate Number");
				if (null == plateNumber) {
					continue;	// already reported
				}
				plate = callback.getPlateSetMutator().getOrCreatePpPlate(ps, plateNumber);
				plateRow = 0;	// start counting again
				continue;
			}
			// Ignore everything until first plate
			if (null == plate) {
				continue;
			}
			
			if ("Plate Name".equalsIgnoreCase(fields[0].trim())) {
				String name = fields.length > 1 ? fields[1] : null;
				callback.getPlateMutator().setAlias(plate, name);
				continue;
			}
			if ("Plate Barcode".equalsIgnoreCase(fields[0].trim())) {
				String bc = fields.length > 1 ? fields[1] : null;
				callback.getPlateMutator().setBarcode(plate, bc);
				continue;
			}
			
			// Check number of columns
			if (fields.length > plate.getPlateFormat().getNumberOfColumns()) {
				reporter.error("Line #0: Wrong number of columns for Plate #1, expected #2 but got #3.",
						lineNr,
						plate.getPlateNumber(),
						plate.getPlateFormat().getNumberOfColumns(),
						fields.length);
				continue;
			}
			
			// Next row
			plateRow++;
			if (plateRow > plate.getPlateFormat().getNumberOfRows()) {
				reporter.error("Line #0: Wrong number of rows for Plate #1, expected #2 but got #3.",
						lineNr,
						plate.getPlateNumber(),
						plate.getPlateFormat().getNumberOfRows(),
						plateRow);
				continue;
			}
			
			countCreated += parseProteinPurificationBatches(lineNr, plate, plateRow, fields);
		}
		
		if (0 == reporter.getNumberOfErrors()) {
			if (0 == countCreated) {
				reporter.error("The file did not contain any valid wells to fill.");
			}
			else {
				reporter.info("Successfully filled #0 #1 with #2.", 
						countCreated,
						1 == countCreated ? "well" : "wells",
						callback.getProteinPurificationBatchInformationProvider().getPluralEntityLabel());
			}
		}
	}

	private int parseProteinPurificationBatches(int lineNr, PpPlate plate, int plateRow, String[] fields) {
		int count = 0;
		for (int i=0; i<fields.length; i++) {
			int plateCol = i+1;
			String idOrAlias = fields[i].trim();
			
			// leave wells empty
			if (idOrAlias.isEmpty()) {
				continue;
			}
			
			// try to find Protein Purification Batch
			ProteinPurificationBatch ppb = callback.getProteinPurificationBatchInformationProvider().retrieve(idOrAlias);
			if (null == ppb) {
				reporter.error("Line #0: Cannot find #1 for ID or Name '#2'.",
						lineNr,
						callback.getProteinPurificationBatchInformationProvider().getSingularEntityLabel(),
						idOrAlias);
				continue;
			}
			
			try {
				PpPlateWell well = callback.getPlateMutator().getOrCreatePpPlateWell(plate, plateRow, plateCol);
				callback.getPlateWellMutator().setProteinPurificationBatch(well, ppb);
				count++;
			}
			catch (Exception e) {
				reporter.error("Line #0: #1.", lineNr, e.getMessage());
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
	
	private Integer parseInt(int lineNumber, String value, String column) {
		try {
			return Integer.valueOf(value);
		} 
		catch (NumberFormatException e) {
			reporter.error("Line #0: #1 '#2' is not a number.",
					lineNumber, column, value);
			return null;
		}
	}
}
