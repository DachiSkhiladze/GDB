package biologics.sc.adapter.lhs;

import genedata.bx.adapter.ParseException;
import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.lhs.FileParserForPlateSetCreationAndIsolateReuse;
import genedata.bx.adapter.plate.Plate;
import genedata.bx.adapter.plate.PlateFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * The adapter reads an LHS report for Plate Set creation and provides a list of
 * {@link Plate}s that are created by Genedata Biologics. The file format recognized 
 * by this implementation is a grid of Antibody Clone IDs or Names: <pre>
 * Plate     Some description (Plate keyword required, description is optional)
 * Barcode   ABCD (barcode line is optional)
 * CL-1      CL-2    CL-3     CL-4 ...
 * CL-11     CL-12   CL-13   CL-14 ...
 * ...
 * </pre>
 * The number of rows and columns of the grid are provided through a PlateFactory.
 * Column separator may be configured and defaults to the tabulator character. 
 * <p>
 * 
 * <div style="font-size:x-small">
 * Copyright 2013 Genedata AG. All Rights Reserved.
 * </div>
 */
public class LhsReportParserForWellLayoutMapBasedPlateSetCreation 
		implements FileParserForPlateSetCreationAndIsolateReuse, Serializable {
	private static final long serialVersionUID = 1L;
	private static Logger log = Logger.getLogger(LhsReportParserForWellLayoutMapBasedPlateSetCreation.class);

	private List<Plate> plates = new ArrayList<Plate>();	
	private PlateFactory plateFactory = null;
	private int nrRows;
	private int nrCols;
	
	private Reporter reporter;
	
    /**
     * The delimiter character as read from the configuration, maybe comma or
     * tab. Will be available after {@link #setConfiguration(Map)} was called
     */
    protected static String CONFIGURED_DELIMITER;	
	
	@Override
	public List<Plate> getPlates() {
		return this.plates;
	}

	@Override
	public void perform(PlateFactory plateFactory, InputStream input) throws ParseException {
		this.plateFactory = plateFactory;
		this.plates.clear();			
		
		BufferedReader br = new BufferedReader(new InputStreamReader(input));
		String line;
		String barcode = "";
		String plateDescription = "";
		int lineNr = 0;
		int plateNr = 0;
		int plateRow = 0;
		Boolean newPlate = false; // True, if new plate is to be created
		Boolean inPlate = false; // True, if current line belongs to a plate definition
		Plate plate = null;
		
		int nNonEmptyLinesOutsidePlateDefinition = 0;
		try {
			while (null != (line = br.readLine())) {
				
				lineNr ++;				

				String[] fields = line.split(CONFIGURED_DELIMITER, -1);
				
				// New plate?
				if (fields.length > 0 && fields[0].equalsIgnoreCase("plate")){
					plateDescription = "";
					if (fields.length > 1) {
						plateDescription = fields[1];
					}
					barcode = "";
					newPlate = true;
					inPlate = true;
					log.debug("New plate. Description: " + plateDescription);
					
					continue;
				}
				
				// Only empty lines are allowed outside of plates
				if (inPlate == false) {
					if (line.trim().length() > 0) {
						nNonEmptyLinesOutsidePlateDefinition++;
						if (nNonEmptyLinesOutsidePlateDefinition < 6) {
							reporter.error("Found non-empty line (#0) outside of (#1x#2)-plate definition: #3", lineNr, this.nrRows, this.nrCols, line);
						}
					}
					continue;
				}				
								
				// Handle the plate barcode
				if (fields.length > 1 && fields[0].equalsIgnoreCase("barcode")){
					barcode = fields[1];
					log.debug("Reading barcode: " + barcode);
					continue;
				}
				
				String barcodeInfo = "";
				if (barcode.trim().isEmpty() == false) {
					barcodeInfo = " (Barcode " + barcode + ")";
				}				
				
				// Create new plate if necessary (
				if (newPlate == true) {
					
					// Create new plate
					plate = findOrCreatePlate(barcode, plateDescription);
					newPlate = false;
					plateNr++;
					
					// Reset plate row counter
					plateRow = 0;
					
				}
				
				// Check number of columns
				if (fields.length > nrCols) {
					reporter.error("Wrong number of columns in plate " + plateNr + barcodeInfo + ". Expected " + nrCols + ", got " + fields.length + ".");
				}
								
				// Set ID or Alias of current row
				plateRow++;
				if (plateRow > this.nrRows) {
					reporter.error("Wrong number of rows in plate " + plateNr + barcodeInfo + ". Expected " + nrRows + ", got " + plateRow + " or more.");
				}
				for (int i=0; i<fields.length; i++) {
					int plateCol = i+1;
					String idOrAlias = fields[i].trim();
					if (idOrAlias.length() > 0) {
						plate.setWellIsolateQualifiedIdOrAlias(plateRow, plateCol, idOrAlias);
					}
				}								
				
				// Reached expected end of plate?
				if (plateRow == nrRows) {
					inPlate = false;
				}
				
			}
		} catch (Exception e) {
			throw new ParseException(e.getMessage(), lineNr);
		}

		if (nNonEmptyLinesOutsidePlateDefinition > 5) {
			reporter.error("Found #0 non-empty lines outside of (#1x#2)-plate definition.", 
					nNonEmptyLinesOutsidePlateDefinition, this.nrRows, this.nrCols);
		}
	}

	@Override
	public void setPlateDimensions(int numberOfRows, int numberOfColumns) {
		 this.nrCols = numberOfColumns;
		 this.nrRows = numberOfRows;
	}
	
	
	private Plate findOrCreatePlate (String barcode, String description) {
		
		// Check that the barcode is unique
		if (barcode.isEmpty() == false) {
			for (Plate plate : plates) {
				if (LhsReportParserUtil.hasBarcodeOrAlias(plate) &&
						LhsReportParserUtil.getBarcodeOrAlias(plate).equals(barcode)) {
					reporter.error("Barcode " + barcode + " is already used, but should be unique.");
					return plate;
				}
			}
		}
		
		Plate plate = plateFactory.createPlate();
		plate.setBarcode(barcode);
		plate.setDescription(description);
		plates.add(plate);
		log.debug("Created plate with barcode " + barcode + " and description " + description + " and added it to the list of currently " + plates.size() + " plates.");
		return plate;
	}
	
    /* (non-Javadoc)
     * @see genedata.bx.adapter.GenericAdapter#setConfiguration(java.util.Map)
     */
    @Override
    public void setConfiguration(Map<String, String> configuration) {

            String delim = configuration.get("table_export_import_format");
            if (null == delim || 0 == delim.trim().length() || ! "CSV".equalsIgnoreCase(delim.trim())) {
                    CONFIGURED_DELIMITER = "\t"; // also fall back
            }
            else {
                    CONFIGURED_DELIMITER = ",";
            }
            log.debug("Read '"+delim+"' from config: set '"+CONFIGURED_DELIMITER+"' as delimiter");
    
    }
	
	@Override
	public void setReporter(Reporter reporter) {
		this.reporter = reporter;
	}
        
}
