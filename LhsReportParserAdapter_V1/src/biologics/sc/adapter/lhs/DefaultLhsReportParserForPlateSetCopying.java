/**
 * 
 */
package biologics.sc.adapter.lhs;

import genedata.bx.adapter.ParseException;
import genedata.bx.adapter.lhs.BarcodeException;
import genedata.bx.adapter.lhs.LhsReportParserForPlateSetCopying;
import genedata.bx.adapter.plate.Plate;
import genedata.bx.adapter.plate.PlateFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultLhsReportParserForPlateSetCopying implements LhsReportParserForPlateSetCopying {
	
	private final Set<String> sourceBarcodes = new HashSet<String>();
	private final Map<String, String> indxToSrcBarcodeMapping = new HashMap<String, String>();
	private final Map<String, String> indxToDestBarcodeMapping = new HashMap<String, String>();
	private final Map<String, String> srcToDestBarcodeMapping = new HashMap<String, String>();
	private final Set<String> sourcePlateIds = new HashSet<String>();
	
	private PlateFactory plateFactory = null;
	
	@Override
	public void perform(PlateFactory plateFactory, InputStream input) throws ParseException, BarcodeException {
		this.plateFactory = plateFactory;
		this.indxToSrcBarcodeMapping.clear();
		this.indxToDestBarcodeMapping.clear();
		this.srcToDestBarcodeMapping.clear();
		
		BufferedReader br = new BufferedReader(new InputStreamReader(input));
		String line;
		int lineNr = 0;
		
		try {
			// skip header line
			br.readLine();
			
			while (null != (line = br.readLine())) {
				
				lineNr ++;
				line = line.trim();
				if (0 == line.length()) {
					continue;
				}
				
				String[] fields = line.split(",");
				if (fields.length < 6) {
					throw new IllegalArgumentException("Wrong number of columns.");
				}
				
				if (isSourceEntry(fields[3])) {
					registerPlateBarcode(this.indxToSrcBarcodeMapping, fields[4], fields[5]); // #, PlateId
				} else  {
					registerPlateBarcode(this.indxToDestBarcodeMapping, fields[4], fields[5]); // #, PlateId
				} 
			}
		} catch (Exception e) {
			throw new ParseException(e.getMessage(), lineNr);
		}
		
		validateSourceBarcodes();
		validateSourceToDestinationMapping();
		validateDestinationToSourceMapping();
		createSourceToDestinationMapping();
	}
	
	private boolean isSourceEntry (String plateIdentifier) {
		plateIdentifier = plateIdentifier.trim().toUpperCase();
		for (String sourcePlateId : sourcePlateIds) {
			if (plateIdentifier.contains(sourcePlateId.toUpperCase())) {
				return true;
			}
		}
		return false;
	}
	
	private void registerPlateBarcode(Map<String, String> indxToBarcodeMapping, String indx, String barcode) throws BarcodeException {
		if (indxToBarcodeMapping.containsKey(indx)) {
			throw new BarcodeException("Index "+indx+" occurred multiple times, e.g. for Plates "+indxToBarcodeMapping.get(indx)+" and "+barcode+". Please check that the source plates are identified by the string(s): "+sourcePlateIds);
		} else if (indxToBarcodeMapping.containsValue(barcode)) {
			throw new BarcodeException("Barcode "+barcode+" occurs multiple times.");
		} else {
			indxToBarcodeMapping.put(indx, barcode);
		}
	}
	
	/**
	 * Each and every source barcode must have appeared in the file and there
	 * shall be no excess barcodes in the file.
	 * 
	 * @throws BarcodeException
	 */
	private void validateSourceBarcodes() throws BarcodeException {
		List<String> missingBarcodes = new ArrayList<String>();
		for (String sourceBarcode : sourceBarcodes) {
			if (! indxToSrcBarcodeMapping.values().contains(sourceBarcode)) {
				missingBarcodes.add(sourceBarcode);
			}
		}
		if (0 != missingBarcodes.size()) {
			throw new BarcodeException("Missing entries for "+missingBarcodes.size()+" Plate(s) from the source Plate Set: "+missingBarcodes);
		}
		List<String> excessBarcodes = new ArrayList<String>();
		for (String indexedBarcode : indxToSrcBarcodeMapping.values()) {
			if (! sourceBarcodes.contains(indexedBarcode)) {
				excessBarcodes.add(indexedBarcode);
			}
		}
		if (0 != excessBarcodes.size()) {
			throw new BarcodeException("Found "+excessBarcodes.size()+" Plate(s) not part of the source Plate Set: "+excessBarcodes);
		}
	}
	
	private void validateSourceToDestinationMapping() throws BarcodeException {
		List<String> missingBarcodes = new ArrayList<String>();
		for (String sourceIndex : indxToSrcBarcodeMapping.keySet()) {
			if (! indxToDestBarcodeMapping.keySet().contains(sourceIndex)) {
				missingBarcodes.add(indxToSrcBarcodeMapping.get(sourceIndex));
			}
		}
		if (0 != missingBarcodes.size()) {
			throw new BarcodeException("Found "+missingBarcodes.size()+" source Plate(s) without destination Plate entry: "+missingBarcodes);
		}
	}

	private void validateDestinationToSourceMapping() throws BarcodeException {
		List<String> missingBarcodes = new ArrayList<String>();
		for (String destIndex : indxToDestBarcodeMapping.keySet()) {
			if (! indxToSrcBarcodeMapping.keySet().contains(destIndex)) {
				missingBarcodes.add(indxToDestBarcodeMapping.get(destIndex));
			}
		}
		if (0 != missingBarcodes.size()) {
			throw new BarcodeException("Found "+missingBarcodes.size()+" destination Plate(s) without source Plate entry: "+missingBarcodes);
		}
	}
	
	private void createSourceToDestinationMapping() {
		for (String sourceIndex : indxToSrcBarcodeMapping.keySet()) {
			String sourceBarcode = indxToSrcBarcodeMapping.get(sourceIndex);
			String destBarcode = indxToDestBarcodeMapping.get(sourceIndex);
			if (null != sourceBarcode && null != destBarcode) {
				srcToDestBarcodeMapping.put(sourceBarcode, destBarcode);
			}
		}
	}
	
	@Override
	public void setSourcePlateDimensions(int numberOfRows, int numberOfColumns) {
		// info is not needed
	}
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		if (null == configuration) {
			return;
		}
		
		// reset
		sourcePlateIds.clear();
		
		initPlateIdentifiers (sourcePlateIds, configuration.get(PROPERTY_SOURCE_PLATE_IDENTIFIER));
	}
	
	private void initPlateIdentifiers (Set<String> store, String plateIdentifiers) {
		
		if (null == plateIdentifiers || 0 == plateIdentifiers.trim().length()) {
			return;
		}
		for (String identifier : plateIdentifiers.trim().split("\\s+")) {
			store.add(identifier);
		}
	}

	
	@Override
	public Plate getDestinationPlate(Plate source) {
		String destBarcode = null;
		if (LhsReportParserUtil.hasBarcodeOrAlias(source)) {
			destBarcode = srcToDestBarcodeMapping.get( LhsReportParserUtil.getBarcodeOrAlias(source) );
		}
		
		Plate plate = null;
		if (null != destBarcode) {
			plate = plateFactory.createPlate();
			plate.setBarcode(destBarcode);
		}
		return plate;
	}

	@Override
	public void setSourcePlates(List<Plate> sourcePlates) {
		sourceBarcodes.clear();
		for (Plate srcPlate : sourcePlates) {
			if (LhsReportParserUtil.hasBarcodeOrAlias(srcPlate)) {
				sourceBarcodes.add( LhsReportParserUtil.getBarcodeOrAlias(srcPlate) );
			}
		}
	}
}
