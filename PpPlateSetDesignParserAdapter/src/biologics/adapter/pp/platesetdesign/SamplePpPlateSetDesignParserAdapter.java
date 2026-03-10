package biologics.adapter.pp.platesetdesign;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.entity.PpPlateSetDesign;
import genedata.bx.adapter.entity.PpPlateSetDesignPlate;
import genedata.bx.adapter.entity.Ppt;
import genedata.bx.adapter.pp.platesetdesign.PpPlateSetDesignParserAdapter;
import genedata.bx.adapter.pp.platesetdesign.PpPlateSetDesignParserAdapterCallback;
import genedata.bx.adapter.pp.platesetdesign.PpPlateSetDesignParserAdapterOptions;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reads Plate layout maps filled with Target Product Protein ID/Names and creates
 * a Protein Production Plate Set Design from them.
 * <p> 
 * Each layout map starts with the term "Plate Number" and resolves into one Design Plate. 
 * It is allowed for single wells to be empty. The layout map may look like this:
 * <pre>
 * Plate Number	1
 * hOOT1-slc-Glu	hOOT1-slc-Ano	hOOT1-slc-Hep1	hOOT1-slc-Hep2
 * Trex-HiLa-Glu	Trex-HiLa-Glu	Trex-HiLa-Hep1	Trex-HiLa-Hep2
 * Expi-ChoZ-Glu	Expi-ChoZ-Ano	Expi-ChoZ-Hep1	Expi-ChoZ-Hep2
 * </pre>
 * All Target Product Proteins need to be linked to the current Production Dataset.
 * 
 * @author Genedata Biologics (tg)
 * @since GDB-9.2
 */
public class SamplePpPlateSetDesignParserAdapter implements PpPlateSetDesignParserAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	
	private Pattern delimiter = Pattern.compile("\t"); // default: tab
	private Reporter reporter;
	private PpPlateSetDesignParserAdapterCallback callback;
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		this.delimiter = Pattern.compile("csv".equalsIgnoreCase(configuration.get("table_export_import_format")) ? "," : "\t");
	}
	
	@Override
	public void options(PpPlateSetDesignParserAdapterOptions options) {
		// no custom parameters
	}
	
	@Override
	public PpPlateSetDesign perform(PpPlateSetDesignParserAdapterCallback callback) {
		this.callback = callback;
		this.reporter = callback.getInvocationContext().getReporter();
		
		PpPlateSetDesign ps = callback.createPpPlateSetDesign();
		
		try {
			parseContent(ps);
		}
		catch (Exception e) {
			reporter.error("Error when reading file: #0.", e.getMessage());
		}
		
		return 0 == reporter.getNumberOfErrors() ? ps : null;
	}

	private void parseContent(PpPlateSetDesign ps) throws Exception {
		LineNumberReader reader = new LineNumberReader(
				new InputStreamReader(callback.getInputStream(), Charset.forName("UTF-8")));
		
		String[] fields;
		int lineNr = 0;
		int plateRow = 0;
		PpPlateSetDesignPlate plate = null;
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
				plate = callback.getMutator().getOrCreateDesignPlate(ps, plateNumber);
				plateRow = 0;	// start counting again
				continue;
			}
			// Ignore everything until first plate
			if (null == plate) {
				continue;
			}
			
			// Check number of columns
			if (fields.length > callback.getPlateFormat().getNumberOfColumns()) {
				reporter.error("Line #0: Wrong number of columns for Plate #1, expected #2 but got #3.",
						lineNr,
						plate.getPlateNumber(),
						callback.getPlateFormat().getNumberOfColumns(),
						fields.length);
				continue;
			}
			
			// Next row
			plateRow++;
			if (plateRow > callback.getPlateFormat().getNumberOfRows()) {
				reporter.error("Line #0: Wrong number of rows for Plate #1, expected #2 but got #3.",
						lineNr,
						plate.getPlateNumber(),
						callback.getPlateFormat().getNumberOfRows(),
						plateRow);
				continue;
			}
			
			countCreated += parsePpts(lineNr, plate, plateRow, fields);
		}
		
		if (0 == reporter.getNumberOfErrors()) {
			if (0 == countCreated) {
				reporter.error("The file did not contain any valid wells to fill.");
			}
			else {
				reporter.info("Successfully filled #0 #1 with #2.", 
						countCreated,
						1 == countCreated ? "well" : "wells",
						callback.getPptInformationProvider().getPluralEntityLabel());
			}
		}
	}

	private int parsePpts(int lineNr, PpPlateSetDesignPlate plate, int plateRow, String[] fields) {
		int count = 0;
		for (int i=0; i<fields.length; i++) {
			int plateCol = i+1;
			String idOrAlias = fields[i].trim();
			
			// leave wells empty
			if (idOrAlias.isEmpty()) {
				continue;
			}
			
			// try to find PPT
			Ppt ppt = callback.getPptInformationProvider().retrieve(idOrAlias);
			if (null == ppt) {
				reporter.error("Line #0: Cannot find #1 for ID or Name '#2'.",
						lineNr,
						callback.getPptInformationProvider().getSingularEntityLabel(),
						idOrAlias);
				continue;
			}
			
			try {
				callback.getMutator().setWell(plate, plateRow, plateCol, ppt);
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
