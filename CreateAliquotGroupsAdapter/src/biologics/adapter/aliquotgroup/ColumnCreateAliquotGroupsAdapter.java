package biologics.adapter.aliquotgroup;

import static genedata.bx.adapter.assay.v2.AssayDataUtil.joinStrings;
import static genedata.bx.adapter.assay.v2.AssayDataUtil.unquote;
import genedata.bx.adapter.aliquotgroup.AliquotGroupRecord;
import genedata.bx.adapter.entity.CellLineBatch;
import genedata.bx.adapter.entity.InvalidWellAddressError;
import genedata.bx.adapter.entity.PlateInfo;
import genedata.bx.adapter.entity.PlateWell;
import genedata.bx.adapter.entity.Well;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The generic implementation of the Create Aliquot Groups Adapter interface.
 * Imports Aliquot Groups from tab-separated, column-based text files.
 */
public class ColumnCreateAliquotGroupsAdapter extends AbstractCreateAliquotGroupsAdapter {
	
	private final String ALIQUOT_GROUP_ENTITY_LABEL = "Aliquot Group";
	private final static String NAME_SUFFIX = "Name";
	private final static String ID_SUFFIX = "ID";
	
	private String cellLineBatchEntityLabel;
	private String plateWellEntityLabel;
	private boolean foundHeaderLineFlag;
	
	private final Map<String, Attribute> labelAttributeMap = new HashMap<>();
	private final Map<Integer, Attribute> indexAttributeMap = new HashMap<>();
	
	private int expectedColumnCount;
	private final List<String> matchedColumnLabels = new ArrayList<String>();
	private final List<String> unmatchedColumnLabels = new ArrayList<String>();
	private final List<String> multipleColumnLabels = new ArrayList<String>();
	private final List<String> linesWithInvalidColumnCount = new ArrayList<String>();
	private final List<String> invalidCellLineBatchNames = new ArrayList<String>();
	private final List<String> invalidPlateBarcodes = new ArrayList<String>();
	
	@Override
	protected void initialize() {
		super.initialize();
		
		cellLineBatchEntityLabel = biologics.getCellLineBatchInformationProvider().getSingularEntityLabel();
		plateWellEntityLabel = biologics.getPlateWellInformationProvider().getSingularEntityLabel();
		
		labelAttributeMap.clear();
		labelAttributeMap.put(cellLineBatchEntityLabel + " ID", Attribute.cellLineBatchId);
		labelAttributeMap.put(cellLineBatchEntityLabel + " Name", Attribute.cellLineBatchName);
		labelAttributeMap.put("Plate Barcode", Attribute.plateBarcode);
		labelAttributeMap.put("Well Address", Attribute.plateWellAddress);
		labelAttributeMap.put("Aliquot Group Name", Attribute.aliquotGroupName);
		labelAttributeMap.put("Description", Attribute.description);
		labelAttributeMap.put("Aliquots Initial Number", Attribute.aliquotsInitialNumber);
		labelAttributeMap.put("Aliquot Barcodes", Attribute.aliquotBarcodes);
		labelAttributeMap.put("Number Of Cells Per Aliquot", Attribute.numberOfCellsPerAliquot);
		labelAttributeMap.put("Reference Sample", Attribute.referenceSample);
		labelAttributeMap.put("Archived", Attribute.archived);
		labelAttributeMap.put("Freezing Date", Attribute.freezingDate);
		labelAttributeMap.put("Freezer Location", Attribute.freezerLocation);
		labelAttributeMap.put("Freezer Rack Number", Attribute.freezerRackNumber);
		labelAttributeMap.put("Rack Barcode", Attribute.rackBarcode);
		labelAttributeMap.put("Rack Position", Attribute.rackPosition);
	}
	
	@Override
	protected void performInternal() {
		reset();
		if (!validateInput()) {
			return;
		}
		
		processInput();
		reportAfterProcessing();
	}
	
	private boolean reset() {
		currentLineNumber = 0;
		foundHeaderLineFlag = false;
		
		matchedColumnLabels.clear();
		unmatchedColumnLabels.clear();
		multipleColumnLabels.clear();
		linesWithInvalidColumnCount.clear();
		invalidCellLineBatchNames.clear();
		invalidPlateBarcodes.clear();
		
		return true;
	}
	
	/**
	 * Validate input and initialize
	 * @return true if initialization was successful
	 */
	private boolean validateInput() {
		
		if (biologics.getInputStream() == null) {
			reporter.error("Failed to import #0 data from file: The required input stream is not available.", 
					ALIQUOT_GROUP_ENTITY_LABEL.toLowerCase()
					);
			return false;
		}
		
		return true;
	}
	
	/**
	 * Process input and handle Exceptions when reading and parsing input
	 */
	private void processInput() {
		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader( new InputStreamReader(biologics.getInputStream(), getCharsetName() ) );
			processLines(bufferedReader);
		}
		catch (Exception e) {
			reporter.error("Failed to parse the contents of the input stream.");
			log.error("processInput", e);
		}
		finally {
			if (bufferedReader != null) {
				try { 
					bufferedReader.close(); 
				} 
				catch (Exception e) { 
					reporter.error("Failed to close input.");
					log.error("processInput", e);
				}
			}
		}
	}
	
	/**
	 * Iterate over all lines of input
	 * @param bufferedReader
	 * @throws IOException
	 */
	private void processLines(BufferedReader bufferedReader) throws IOException, Exception {
		String line = null;
		while (null != (line = bufferedReader.readLine())) {
			currentLineNumber++;
			
			String trimmedLine = line.trim();
			
			if (0 == trimmedLine.length()) {
				// skip over empty line
			}
			else if (trimmedLine.startsWith(COMMENT_LINE_INDICATOR)) {
				// skip over comment line
			}
			else {
				parseLine(line);
			}
		}
	}
	
	/**
	 * Reports summary information after the header line has been found.
	 */
	private void reportAfterHeaderLine() {
		if (multipleColumnLabels.size() > 0) {
			String message = (multipleColumnLabels.size() > 1) ?
					"The following column labels were found multiple times in the header line: " :
					"The following column label was found multiple times in the header line: ";
			
			reporter.warn(message + joinStrings(multipleColumnLabels, ", ", true));
			reporter.info("Please note that each column label should appear only once in the header line. " +
					"If a column label appears several times, then only the contents of the first column will be taken into account.");
		}
		
		if (matchedColumnLabels.size() > 0) {
			reporter.info("The following column labels have been recognized: "
					+ joinStrings(matchedColumnLabels, ", ", true));
		}
		
		if (unmatchedColumnLabels.size() > 0) {
			reporter.info("Column labels that were not assigned to an #0 attribute: #1",
					ALIQUOT_GROUP_ENTITY_LABEL.toLowerCase(),
					joinStrings(unmatchedColumnLabels, ", ", true));
			reporter.info("Please note that the contents of these columns will not be imported.");
		}
	}
	
	/**
	 * Reports summary information after the parsing has been done.
	 */
	private void reportAfterProcessing() {
		
		if (! foundHeaderLineFlag) {
			reporter.error("#0 data has not been imported: Could not find a valid header line in file.",
					ALIQUOT_GROUP_ENTITY_LABEL);
			reporter.info("Note that a column with #0 identifiers or names is required. It must be labeled '#1' or '#2'.",
					cellLineBatchEntityLabel,
					cellLineBatchEntityLabel + " " + ID_SUFFIX,
					cellLineBatchEntityLabel + " " + NAME_SUFFIX);
		}
		
		if (linesWithInvalidColumnCount.size() > 0) {
			reporter.warn("Wrong number of columns was found in the line(s): "
					+ joinStrings(linesWithInvalidColumnCount, ", "));
		}
		
		if (linesWithInvalidColumnCount.size() > 0) {
			reporter.info("Please note that the #0 data from these line(s) has not been imported.",
					ALIQUOT_GROUP_ENTITY_LABEL.toLowerCase());
		}
		
		if (invalidCellLineBatchNames.size() > 0) {
			reporter.error("The following #0 #1 not found in the campaign: #2",
					cellLineBatchEntityLabel,
					invalidCellLineBatchNames.size() == 1 ? "ID or Name was" : "IDs or Names were",
					joinStrings(invalidCellLineBatchNames, ", "));
			reporter.info("Please note that the #0 must exist and be a part of the Campaign in order to be recognized.", cellLineBatchEntityLabel);
		}
		
		if (invalidPlateBarcodes.size() > 0) {
			reporter.error("The following #0 #1 not found in the campaign: #2",
					plateWellEntityLabel,
					invalidPlateBarcodes.size() == 1 ? "barcode was" : "barcodes were",
					joinStrings(invalidPlateBarcodes, ", "));
			reporter.info("Please note that the #0 must exist and be a part of the Campaign in order to be recognized.", plateWellEntityLabel);
		}
	}
	
	private void parseLine(String line) throws Exception {
		log.debug("parseLine");
		
		if (line == null) {
			return;
		}
		
		if (! foundHeaderLineFlag) {
			foundHeaderLineFlag = parseHeaderLine(line);
			
			if (foundHeaderLineFlag) {
				reportAfterHeaderLine();
			}
		}
		else {
			parseDataLine(line);
		}
	}
	
	private boolean parseHeaderLine(String line) throws Exception {
		log.debug("parseHeaderLine");
		
		// make sure that trailing empty items are included by setting the limit to a negative value
		String[] items = split(line, -1);
		expectedColumnCount = items.length;
		initAttributes(items);
		
		return checkRequiredAttributes();
	}
	
	private void initAttributes(String[] items) throws Exception {
		indexAttributeMap.clear();
		
		for (int index = 0; index < items.length; index++) {
			String label = unquote(items[index]).trim();
			
			if (matchedColumnLabels.contains(label) || unmatchedColumnLabels.contains(label)) {
				multipleColumnLabels.add(label);
				continue;
			}
			
			Attribute attribute = labelAttributeMap.get(label);
			
			if (attribute != null) {
				indexAttributeMap.put( Integer.valueOf(index), attribute);
				matchedColumnLabels.add(label);
			}
			else {
				if (label.length() > 0) {
					unmatchedColumnLabels.add(label);
				}
			}
		}
	}
	
	private boolean checkRequiredAttributes() {
		return foundCellLineBatchColumn()
				|| foundPlateWellColumns();
	}
	
	void parseDataLine(String line) throws Exception {
		
		// make sure that trailing empty items are included by setting the limit to a negative value
		String[] items = split(line, -1);
		
		if (items.length != expectedColumnCount) {
			linesWithInvalidColumnCount.add( Integer.toString(currentLineNumber) );
			return;
		}
		
		Map<Attribute,String> attributeValueMap = new HashMap<>();
		for (Integer index : indexAttributeMap.keySet()) {
			String valueString = unquote(items[ index.intValue() ]).trim();
			Attribute attribute = indexAttributeMap.get( index.intValue() );
			if (! isEmpty(valueString)) {
				attributeValueMap.put(attribute, valueString);
			}
		}
		
		if (attributeValueMap.isEmpty()) {
			log.debug("Skipped empty line." + lineContext());
			return;
		}
		
		if (! attributeValueMap.containsKey(Attribute.plateBarcode)
				&& ! attributeValueMap.containsKey(Attribute.cellLineBatchId)
				&& ! attributeValueMap.containsKey(Attribute.cellLineBatchName)) {
			reportError("No value provided for Plate Barcode, Cell Line Batch ID or Name.");
			return;
		}
		
		PlateWell plateWell = retrievePlateWell(attributeValueMap);
		CellLineBatch cellLineBatch = null;
		if (plateWell == null) {
			cellLineBatch = retrieveCellLineBatch(attributeValueMap);
		}
		if (cellLineBatch == null && plateWell == null) {
			return;
		}
		
		AliquotGroupRecord aliquotGroup = null;
		if (plateWell != null) {
			if (! plateWell.hasMaterial()) {
				reporter.error("Could not create " + ALIQUOT_GROUP_ENTITY_LABEL + ": "
					+ cellLineBatchEntityLabel + " not available for the specified plate well." + lineContext());
			}
			else {
				try {
					aliquotGroup = biologics.getAliquotGroupRecordFactory().create(plateWell);
				}
				catch (IllegalArgumentException e) {
					reportException(e);
				}
			}
		}
		else if (cellLineBatch != null) {
			aliquotGroup = biologics.getAliquotGroupRecordFactory().create(cellLineBatch);
		}
		if (aliquotGroup != null) {
			setAttributeValues(aliquotGroup, attributeValueMap);
			addAliquotGroup(aliquotGroup);
		}
	}
	
	private CellLineBatch retrieveCellLineBatch(Map<Attribute,String> attributeValueMap) {
		CellLineBatch answer = null;
		String cellLineBatchId = null;
		String cellLineBatchName = null;
		if (indexAttributeMap.containsValue(Attribute.cellLineBatchId)) {
			cellLineBatchId = attributeValueMap.get(Attribute.cellLineBatchId);
			answer = biologics.getCellLineBatchInformationProvider().retrieveByQualifiedId(cellLineBatchId);
		}
		if (answer == null
				&& indexAttributeMap.containsValue(Attribute.cellLineBatchName)) {
			cellLineBatchName = attributeValueMap.get(Attribute.cellLineBatchName);
			answer = biologics.getCellLineBatchInformationProvider().retrieveByName(cellLineBatchName);
		}
		if (answer == null) {
			if (! isEmpty(cellLineBatchId)) {
				invalidCellLineBatchNames.add(cellLineBatchId + lineContext() );
			}
			if (! isEmpty(cellLineBatchName)) {
				invalidCellLineBatchNames.add(cellLineBatchName + lineContext() );
			}
		}
		return answer;
	}
	
	private PlateWell retrievePlateWell(Map<Attribute,String> attributeValueMap) {
		PlateWell answer = null;
		String plateBarcode = null;
		PlateInfo plateInfo = null;
		if (indexAttributeMap.containsValue(Attribute.plateBarcode)
				&& indexAttributeMap.containsValue(Attribute.plateWellAddress)
				&& attributeValueMap.containsKey(Attribute.plateBarcode)) {
			plateBarcode = attributeValueMap.get(Attribute.plateBarcode);
			plateInfo = biologics.getPlateWellInformationProvider().retrieveByBarcode(plateBarcode);
			String plateWellAddress = attributeValueMap.get(Attribute.plateWellAddress);
			Well well = null;
			if (isEmpty(plateWellAddress)) {
				reportError("The Well Address is missing.");
			}
			else {
				try {
					well = biologics.getWellAddressConverter().parseWellAddress(plateWellAddress);
				}
				catch (IllegalArgumentException e) {
					reportException(e);
				}
			}
			if (plateInfo != null && well != null) {
				try {
					answer = biologics.getPlateWellInformationProvider().retrieve(plateInfo, well);
				}
				catch (InvalidWellAddressError e) {
					reportException(e);
				}
			}
		}
		if (answer == null
				&& plateBarcode != null
				&& plateInfo == null) {
			invalidPlateBarcodes.add(plateBarcode + lineContext());
		}
		return answer;
	}
	
	private boolean foundCellLineBatchColumn() {
		return indexAttributeMap.containsValue(Attribute.cellLineBatchId)
				|| indexAttributeMap.containsValue(Attribute.cellLineBatchName);
	}
	
	private boolean foundPlateWellColumns() {
		return indexAttributeMap.containsValue(Attribute.plateBarcode)
				&& indexAttributeMap.containsValue(Attribute.plateWellAddress);
	}
	
	private void reportException(Exception e) {
		reportError(e.getMessage());
	}
	
	private void reportError(String message) {
		reporter.error("Could not create " + ALIQUOT_GROUP_ENTITY_LABEL + ": " + message + lineContext());
	}
	
}
