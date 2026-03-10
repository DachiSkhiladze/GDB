package biologics.adapter.lhs;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.entity.PlateWell;
import genedata.bx.adapter.entity.Well;
import genedata.bx.adapter.entity.WellRole;
import genedata.bx.adapter.lhs.v2.LhsCallback;
import genedata.bx.adapter.lhs.v2.LhsCoTransfectPlateSet;
import genedata.bx.adapter.lhs.v2.LhsException;
import genedata.bx.adapter.lhs.v2.LhsOptions;
import genedata.bx.adapter.lhs.v2.LhsPlate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SampleCoTransfectPlateSet implements LhsCoTransfectPlateSet {
	
	private Reporter reporter = null;
	private LhsCallback.CoTransfectPlateSet callback = null;
	
	private Map<String, LhsPlate> destinationBarcodeToPlates = null;
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// not required
	}
	
	@Override
	public void options(LhsOptions.CoTransfectPlateSet options) {
		// no options
	}
	
	@Override
	public List<LhsPlate> perform(LhsCallback.CoTransfectPlateSet callback) throws LhsException {
		this.callback = callback;
		this.reporter = callback.getReporter();
		
		SampleCoTransfectReport sampleCoTransfectReport;
		try {
			sampleCoTransfectReport = new SampleCoTransfectReport(callback.getInputStream());
		} catch (LhsException e) {
			callback.getReporter().error(e.getMessage());
			return Collections.emptyList();
		}
		
		List<LhsPlate> plates = new ArrayList<LhsPlate>();
		destinationBarcodeToPlates = new HashMap<String, LhsPlate>();
		Map<String, List<SampleCoTransfectRecord>> recordsPerWell =
				new HashMap<String, List<SampleCoTransfectRecord>>();
		
		boolean isValid = true;
		for (SampleCoTransfectRecord record : sampleCoTransfectReport) {
			
			if (record.validateRecord(callback, reporter)) {
				String key = record.getDestinationKey();
				if (recordsPerWell.containsKey(key)) {
					recordsPerWell.get(key).add(record);
				}
				else {
					List<SampleCoTransfectRecord> records = new ArrayList<SampleCoTransfectRecord>();
					records.add(record);
					recordsPerWell.put(key, records);
				}
			}
			else {
				isValid = false;
			}
		}
		
		if (isValid) {
			for (List<SampleCoTransfectRecord> records : recordsPerWell.values()) {
				isValid &= processDestRecords(records);
			}
			if (isValid) {
				plates.addAll(destinationBarcodeToPlates.values());
			}
		}
		
		return plates;
	}
	
	private boolean validateWellAddress(int lineNumber, Well well, int numberOfRows, int numberOfColumns) {
		if (well.getRow() <= numberOfRows && well.getColumn() <= numberOfColumns
				&& well.getRow() > 0 && well.getColumn() > 0) {
			return true;
		}
		
		reporter.error("Line #0: Well address '#1' format is wrong.", lineNumber, well.getWellAddress());
		return false;
	}
	
	private boolean validateDestWellRole(LhsPlate destPlate, Well destWell) {
		WellRole role = destPlate.getWellRole(destWell.getRow(), destWell.getColumn());
		if (role.isReserved()) {
			reporter.error("Unable to add content to well '#0' with well role '#1'.", 
					destWell.getWellAddress(), role.getLabel());
			return false;
		}
		return true;
	}	
	
	private boolean processDestRecords(List<SampleCoTransfectRecord> records) {
		SampleCoTransfectRecord firstRecord = null;
		if (! records.isEmpty()) {
			firstRecord = records.get(0); // given records should be grouped by destination well
		}
		if (firstRecord == null) {
			return false;
		}
		int lineNumber = firstRecord.getLineNumber();
		Well destWell = firstRecord.getDestinationWell();
		
		List<PlateWell> sources = new ArrayList<>();
		for (SampleCoTransfectRecord record : records) {
			sources.add(record.getSourceWell());
		}
		
		LhsPlate destPlate = destinationBarcodeToPlates.get(firstRecord.getDestinationBarcode());
		if (destPlate == null) {
			destPlate = createDestinationPlate(firstRecord.getDestinationBarcode(), callback);
		}
		
		if (validateWellAddress(lineNumber, destWell,
				callback.getDestinationPlateNumberOfRows(),
				callback.getDestinationPlateNumberOfColumns())){
			if (validateDestWellRole(destPlate, destWell)) {
				destPlate.setParentsFromSourceWells(destWell.getRow(), destWell.getColumn(), sources);
				return true;
			}
		}
		
		return false;
	}
	
	private LhsPlate createDestinationPlate(String barcode, LhsCallback.CoTransfectPlateSet callback){
		reporter.debug("createDestinationPlate(); barcode: " + barcode);
		
		LhsPlate plate = callback.createEmptyPlate();
		plate.setBarcode(barcode);
		destinationBarcodeToPlates.put(barcode, plate);
		return plate;
	}
	
}
