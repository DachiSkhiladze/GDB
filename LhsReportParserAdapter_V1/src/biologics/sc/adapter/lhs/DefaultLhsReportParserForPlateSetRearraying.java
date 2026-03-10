/**
 * 
 */
package biologics.sc.adapter.lhs;

import genedata.bx.adapter.ParseException;
import genedata.bx.adapter.lhs.BarcodeException;
import genedata.bx.adapter.lhs.LhsReportParserForPlateSetRearraying;
import genedata.bx.adapter.plate.Plate;
import genedata.bx.adapter.plate.PlateFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultLhsReportParserForPlateSetRearraying implements LhsReportParserForPlateSetRearraying {
	
	private List<Plate> sourcePlates = null;
	
	private List<Plate> destinationPlates = new ArrayList<Plate>();
	
	private PlateFactory plateFactory = null;
	
	@Override
	public void perform(PlateFactory plateFactory, InputStream input) throws ParseException {
		this.plateFactory = plateFactory;
		this.destinationPlates.clear();
		
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
				if (fields.length <= 6) {
					throw new IllegalArgumentException("Wrong number of columns, expected more than 6.");
				}
				
				// sample line (after trimming):
				// #       dest               src                date             
				// 0          1  2   3          4  5   6            7        8
				// 3     000013  A   1     022284  L   9    22Jul2010 11:42:18
				
				mapPlateWell(fields[4], fields[5], fields[6], fields[1], fields[2], fields[3]);
			}
		} catch (Exception e) {
			throw new ParseException(e.getMessage(), lineNr);
		}

	}
	
	@Override
	public void setSourcePlateDimensions(int numberOfRows, int numberOfColumns) {
		// info is not needed in this implementation, though we could use
		// it for verifications
	}
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// nothing from the configuration needed
	}

	@Override
	public void setSourcePlates(List<Plate> sourcePlates) {
		this.sourcePlates = sourcePlates;
	}

	@Override
	public List<Plate> getDestinationPlates() {
		return this.destinationPlates;
	}

	@Override
	public void setDestinationPlateDimensions(int numberOfRows, int numberOfColumns) {
		// info is not needed in this implementation, though we could use
		// it for verifications
	}
	
	private void mapPlateWell(
		String sourcePlateBarcode, String sourceRow, String sourceCol,
		String destPlateBarcode, String destRow, String destCol) throws BarcodeException {
		
		Long id = findSourcePlateId(sourcePlateBarcode, row(sourceRow), col(sourceCol));
		if (null == id) {
			throw new IllegalArgumentException("Source plate "+sourcePlateBarcode+" does not contain an Antibody Clone at position "+sourceRow+sourceCol+".");
		}
		
		Plate plate = findOrCreatePlate(destPlateBarcode);
		
		int row = row(destRow);
		int col = col(destCol);
		
		if (! plate.isWellEmpty(row, col)) {
			throw new IllegalArgumentException("Cannot fill well ("+destRow+destCol+") of Plate "+destPlateBarcode+" multiple times.");
		}
		
		plate.setWell(row, col, id);
	}
	
	private Long findSourcePlateId (String barcode, int row, int col) throws BarcodeException {
		for (Plate plate : sourcePlates) {
			if (LhsReportParserUtil.hasBarcodeOrAlias(plate) &&
					LhsReportParserUtil.getBarcodeOrAlias(plate).equals(barcode)) {
				return plate.getWell(row, col);
			}
		}
		throw new BarcodeException("Could not find Plate with barcode "+barcode+" in the source Plate Set.");
	}
	
	private Plate findOrCreatePlate (String barcode) {
		for (Plate plate : destinationPlates) {
			if (LhsReportParserUtil.hasBarcodeOrAlias(plate) &&
					LhsReportParserUtil.getBarcodeOrAlias(plate).equals(barcode)) {
				return plate;
			}
		}
		
		Plate plate = plateFactory.createPlate();
		plate.setBarcode(barcode);
		destinationPlates.add(plate);
		return plate;
	}
	
	private int row(String row) {
		if (null == row || 1 != row.trim().length()) {
			throw new IllegalArgumentException("Row "+row+" is invalid.");
		}
		return 1 + (row.toUpperCase().charAt(0)-'A');
	}
	
	private int col(String col) {
		try {
			return Integer.parseInt(col);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Column "+col+" is invalid.");
		}
	}

}
