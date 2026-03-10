package biologics.adapter.lhs;

import genedata.bx.adapter.entity.Isolate;
import genedata.bx.adapter.entity.PlateInfo;
import genedata.bx.adapter.entity.PlateWell;
import genedata.bx.adapter.entity.PlateWellInformationProvider;
import genedata.bx.adapter.entity.Well;
import genedata.bx.adapter.entity.WellContentClass;
import genedata.bx.adapter.lhs.BarcodeException;
import genedata.bx.adapter.lhs.v2.LhsCallback;
import genedata.bx.adapter.lhs.v2.LhsException;
import genedata.bx.adapter.lhs.v2.LhsOptions.RearrayPlateSet;
import genedata.bx.adapter.lhs.v2.LhsPlate;
import genedata.bx.adapter.lhs.v2.LhsRearrayPlateSet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sample implementation of the version 2 LhsRearrayPlateSet interface
 */
public class SampleLhsRearrayPlateSet implements LhsRearrayPlateSet {
	
	private static final String SOURCE_PLATE = "Source Plate Barcode";
	private static final String SOURCE_WELL = "Source Well Address";
	private static final String DESTINATION_PLATE = "Destination Plate Barcode";
	private static final String DESTINATION_WELL = "Destination Well Address";
	private static final String COLUMN_SEPARATOR = "\t";
	
	private Map<String, Integer> columnMap = new HashMap<String, Integer>();
	

	private LhsCallback.RearrayPlateSet callback;
	private List<LhsPlate> destinationPlates = new ArrayList<LhsPlate>();
	private String line;
	private int lineNo;
	private String singularIsolateLabel;

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// nothing from the configuration needed
	}

	@Override
	public void options(RearrayPlateSet options) {
		// no options currently
	}

	@Override
	public List<LhsPlate> perform(LhsCallback.RearrayPlateSet callback) throws LhsException {
		this.callback = callback;
		this.destinationPlates.clear();
		this.singularIsolateLabel = callback.getLabelProvider().singular(Isolate.class);
		line = null;
		lineNo = 0;

		try (BufferedReader br = 
				new BufferedReader(new InputStreamReader(callback.getInputStream(), "UTF8"))) {
			performWithException(br);
		} catch (Exception e) {
			throw new LhsException("Line " + lineNo + ": " + e.getMessage(), e);
		}

		return destinationPlates;
	}

	private void performWithException(BufferedReader br) throws Exception {
		line = br.readLine(); // header
		lineNo++;
		if (parseHeader(line)) {
			while (null != (line = br.readLine())) {
				performLine();
			}
		}
	}
	
	private boolean parseHeader(String header) {
		List<String> columnNames = Arrays.asList(header.split(COLUMN_SEPARATOR));
		List<String> missingColumns = new ArrayList<String>();
		
		if (! mapColumn(columnNames, SOURCE_PLATE)) {
			missingColumns.add(SOURCE_PLATE);
		}
		if (! mapColumn(columnNames, DESTINATION_PLATE)) {
			missingColumns.add(DESTINATION_PLATE);
		}
		
		// not mandatory as they can be omitted if it is a tube.
		mapColumn(columnNames, SOURCE_WELL);
		mapColumn(columnNames, DESTINATION_WELL);

		if (! missingColumns.isEmpty()) {
			callback.getReporter().error("The following mandatory column(s) not found: #0.", missingColumns.stream().collect(Collectors.joining(", ")));
			return false;
		}
		
		return true;
	}
	
	private boolean mapColumn(List<String> columns, String columnLabel) {
		if (columns.contains(columnLabel)) {
			columnMap.put(columnLabel, columns.indexOf(columnLabel));
		} else {
			return false;
		}
		
		return true;
	}

	private void performLine() throws Exception {
		if (0 == line.length()) {
			lineNo ++;
			return;
		}

		String[] fields = line.split(COLUMN_SEPARATOR, -1);
		String sourcePlateBarcode = getValue(SOURCE_PLATE, fields);
		String sourceWell = getValue(SOURCE_WELL, fields);
		String destPlateBarcode = getValue(DESTINATION_PLATE, fields);
		String destWell = getValue(DESTINATION_WELL, fields);

		mapPlateWell(sourcePlateBarcode, sourceWell, destPlateBarcode, destWell);
		lineNo ++;
	}
	
	private String getValue(String column, String[] row) {
		Integer columnIndex = columnMap.get(column);
		
		if (columnIndex == null) {
			return null;
		}
		
		if (columnIndex > row.length) {
			callback.getReporter().error("Line #0: Missing value for #1.", lineNo, column);
			return null;
		}
		
		return row[columnIndex];
	}

	private boolean mapPlateWell(String sourcePlateBarcode, String sourceWellAddress, String destPlateBarcode, String destWellAddress) throws Exception {
		if (sourcePlateBarcode == null || destPlateBarcode == null) {
			return false;
		}
		
		Well sourceWell = parseAddress(sourceWellAddress);
		Well destWell = parseAddress(destWellAddress);
		
		if (destWell == null) {
			if (callback.getDestinationPlateNumberOfColumns() == 1 
					&& callback.getDestinationPlateNumberOfRows() == 1) {
				destWell = parseAddress("A1");
			} else {
				callback.getReporter().error("Line #0: Cannot identify Destination #1 position as the #2 is not specified.", 
						lineNo, singularIsolateLabel, DESTINATION_WELL);
				return false;
			}
		}
		
		PlateInfo sourcePlate = callback.getPlateWellInformationProvider().retrieveByBarcode(sourcePlateBarcode);
		if (sourcePlate == null) {
			throw new BarcodeException("Cannot find source plate with barcode " + sourcePlateBarcode + ".");
		}
		
		PlateWell sourcePlateWell = getSourcePlateWell(sourcePlate, sourceWell);
		
		WellContentClass material = sourcePlateWell == null ? null : sourcePlateWell.getWellContent();
		if (material == null) {
			if (sourceWell != null) {
				callback.getReporter().error("Line #0: Source plate #1 does not contain an #2 at position #3.", 
						lineNo, sourcePlateBarcode, singularIsolateLabel, sourceWellAddress);
			} else {
				callback.getReporter().error("Line #0: Cannot identify Source #1 on the Source plate #2 as the #3 is not specified.", 
						lineNo, singularIsolateLabel, sourcePlateBarcode, SOURCE_WELL);
			}
			return false;
		}
		
		LhsPlate plate = findDestinationPlate(destPlateBarcode);
		if (plate == null) {
			plate= createPlate(destPlateBarcode);
		}
		
		addParentFromSourcePlateWell(plate, destWell.getRow(), destWell.getColumn(), sourcePlateWell);
		return true;
	}

	private PlateWell getSourcePlateWell(PlateInfo sourcePlate, Well sourceWell) {
		PlateWellInformationProvider plateInfoProvider = callback.getPlateWellInformationProvider();
		if (sourceWell == null) {
			if (sourcePlate.getPlateFormat().getNumberOfColumns() == 1 && sourcePlate.getPlateFormat().getNumberOfRows() == 1) {
				return plateInfoProvider.retrieve(sourcePlate, 1, 1);
			} else {
				return null;
			}
		} else {
			return plateInfoProvider.retrieve(sourcePlate, sourceWell);
		}
	}

	private Well parseAddress(String wellAddress) {
		Well well = null;
		if (null == wellAddress || wellAddress.isBlank()) {
			return null;
		}
		
		try {
			well = callback.getWellAddressConverter().parseWellAddress(wellAddress);
		} catch (Throwable e) {
			callback.getReporter().error(e.getMessage());
		}
		
		return well;
	}

	private LhsPlate findDestinationPlate (String barcode) throws BarcodeException {
		return findPlate(barcode, destinationPlates);
	}

	private static LhsPlate findPlate(String barcode, Iterable<LhsPlate> plates) throws BarcodeException {
		List<LhsPlate> barcodedPlates = new ArrayList<LhsPlate>();
		for (LhsPlate plate : plates) {
			String plateBarcode = plate.getBarcode();
			if (plateBarcode != null && plateBarcode.equals(barcode)) {
				barcodedPlates.add(plate);
			}
		}
		
		if (barcodedPlates.isEmpty()) {
			return null;
		} else if (barcodedPlates.size() > 1) {
			throw new BarcodeException("Multiple source plates with barcode '" + barcode + "' found.");
		}
		
		return barcodedPlates.get(0);
	}

	private LhsPlate createPlate(String barcode) {
		LhsPlate plate = callback.createEmptyPlate();
		plate.setBarcode(barcode);
		destinationPlates.add(plate);
		return plate;
	}
	
	private void addParentFromSourcePlateWell(LhsPlate plate, int row, int column, PlateWell sourcePlateWell) {
		Set<PlateWell> sourcePlateWells = new HashSet<>();
		
		Set<PlateWell> existingSourcePlateWells = plate.getSourceWells(row, column);
		if (existingSourcePlateWells != null
				&& ! existingSourcePlateWells.isEmpty()) {
			sourcePlateWells.addAll(existingSourcePlateWells);
			clearExistingSourcePlateWells(plate, row, column);
		}
		
		if (sourcePlateWells.contains(sourcePlateWell)) {
			throw new UnsupportedOperationException("Cannot set a source plate well multiple times to the same destination plate well.");
		}
		
		sourcePlateWells.add(sourcePlateWell);
		plate.setParentsFromSourceWells(row, column, sourcePlateWells);
	}
	
	private void clearExistingSourcePlateWells(LhsPlate plate, int row, int column) {
		// remove already existing source plate wells
		plate.setParentsFromSourceWells(row, column, null);
	}
	
}
