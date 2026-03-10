package biologics.sc.adapter.lhs;

import genedata.bx.adapter.ParseException;
import genedata.bx.adapter.lhs.BarcodeException;
import genedata.bx.adapter.lhs.LhsReportParserForPlateSetCreation;
import genedata.bx.adapter.plate.Plate;
import genedata.bx.adapter.plate.PlateFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The adapter reads an LHS report for Plate Set creation and provides a list of
 * {@link Plate}s that are created by the Genedata Biologics system. The file format recognized 
 * by this implementation is: <pre>
 * 0.0	1000001	C	5	200
 * 0.0	1000001	C	6	200
 * </pre>
 * The first column is ignored. The second column contains the barcode of the Plate
 * to create, columns three and four contain row and column (C5, C6) in the Plate 
 * to be filled with an Antibody Clone from Agar Plate with barcode of the fifth column. 
 * <p>
 * 
 * <div style="font-size:x-small">
 * Copyright 2010 Genedata AG. All Rights Reserved.
 * </div>
 */
public class DefaultLhsReportParserForPlateSetCreation 
		implements LhsReportParserForPlateSetCreation, Serializable {
	private static final long serialVersionUID = 1L;

	private List<Plate> plates = new ArrayList<Plate>();
	
	private PlateFactory plateFactory = null;
	
	private int nrRows = -1;
	private int nrCols = -1;
	
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
		int lineNr = 0;
		
		try {
			while (null != (line = br.readLine())) {
				
				lineNr ++;
				line = line.trim();
				if (0 == line.length()) {
					continue;
				}
				
				String[] fields = line.split("\\s+");
				if (fields.length <= 4) {
					throw new IllegalArgumentException("Wrong number of columns, expected more than 4.");
				}
				
				fillPlateWell(fields[1], fields[4], fields[2], fields[3]);
			}
		} catch (Exception e) {
			throw new ParseException(e.getMessage(), lineNr);
		}

	}

	@Override
	public void setPlateDimensions(int numberOfRows, int numberOfColumns) {
		this.nrRows = numberOfRows;
		this.nrCols = numberOfColumns;
	}
	
	private void fillPlateWell(
			String barcode, String agarPlateBarcode, 
			String rowString, String colString) throws BarcodeException {
		Plate plate = findOrCreatePlate(barcode);
		
		int row = 1 + (rowString.toUpperCase().charAt(0)-'A');
		int col = Integer.parseInt(colString);
		
		if (row > nrRows) {
			throw new IllegalArgumentException("Row position "+rowString+" is invalid. The plate has only "+nrRows+" rows.");
		}
		if (col > nrCols) {
			throw new IllegalArgumentException("Column position "+colString+" is invalid. The plate has only "+nrCols+" columns.");
		}
		if (! plate.isWellEmpty(row, col)) {
			throw new IllegalArgumentException("Cannot fill well ("+rowString+colString+") of Plate "+barcode+" multiple times.");
		}
		
		plate.setWellWithAgarPlateBarcode(row, col, agarPlateBarcode);
	}
	
	private Plate findOrCreatePlate (String barcode) {
		for (Plate plate : plates) {
			if (LhsReportParserUtil.hasBarcodeOrAlias(plate) &&
					LhsReportParserUtil.getBarcodeOrAlias(plate).equals(barcode)) {
				return plate;
			}
		}
		
		Plate plate = plateFactory.createPlate();
		plate.setBarcode(barcode);
		plates.add(plate);
		return plate;
	}

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// nothing from the configuration needed
	}
	
}
