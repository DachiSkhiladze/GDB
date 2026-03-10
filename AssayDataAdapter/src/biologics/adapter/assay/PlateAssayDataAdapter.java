package biologics.adapter.assay;

import genedata.bx.adapter.Identifiable;
import genedata.bx.adapter.Module;
import genedata.bx.adapter.assay.v2.AbstractAssayDataAdapter;
import genedata.bx.adapter.assay.v2.AssayAttribute;
import genedata.bx.adapter.assay.v2.AssayAttributeException;
import genedata.bx.adapter.assay.v2.AssayDataCallback;
import genedata.bx.adapter.assay.v2.AssayDataOptions;
import genedata.bx.adapter.assay.v2.AssayDataUtil;
import genedata.bx.adapter.assay.v2.AssayPlate;
import genedata.bx.adapter.assay.v2.AssayValue;
import genedata.bx.adapter.assay.v2.AssayValueException;
import genedata.bx.adapter.assay.v2.AssayValueException.EmptyInputValueException;
import genedata.bx.adapter.entity.CellLine;
import genedata.bx.adapter.entity.InformationProvider;
import genedata.bx.adapter.entity.Isolate;
import genedata.bx.adapter.entity.Plate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * The implementation of the AssayDataAdapter Version 2 interface for
 * the import of AssayValues from plate-based, tab-separated files.
 *
 * Expected input format:


<pre>
>>>\tPLATE_ALIAS\tASSAY_ATTRIBUTE
VALUE\tVALUE\tVALUE...\n
VALUE\tVALUE\tVALUE...\n
...
</pre>

 *
 * For Example:
 *

<pre>
>>>	024B-S000	S
0.0577	0.0716	0.066	0.0564	0.0599	0.0589	0.0614	0.0572	0.0537	0.0765	0.066	0.074
0.0562	0.0577	0.0802	0.0527	0.0825	0.0531	0.0701	0.065	0.0643	0.0588	0.057	0.0569
0.0748	0.0837	0.0869	0.0807	0.0861	0.0853	0.0696	0.0698	0.0742	0.0672	0.0694	0.0739
0.0659	0.0841	0.0822	0.0822	0.0827	0.0862	0.0753	0.069	0.0668	0.0653	0.0569	0.069
0.0757	0.0842	0.0827	0.0797	0.0761	0.0826	0.0751	0.0714	0.0682	0.0669	0.0648	0.076
0.0684	0.0756	0.0786	0.0809	0.0713	0.0778	0.0718	0.0715	0.0711	0.0637	0.0646	0.0671
0.0777	0.0794	0.0828	0.0906	0.0782	0.0719	0.0711	0.074	0.0736	0.071	0.0689	0.0699
0.0714	0.1026	0.0992	0.1418	0.0912	0.0783	0.0674	0.0606	0.0616	0.064	0.0599	0.0523

...
</pre>

*/

public class PlateAssayDataAdapter extends AbstractAssayDataAdapter {

	private final Logger log = Logger.getLogger(this.getClass());

	/** The string which indicates the start of a header line. */
	private final String HEADER_LINE_INDICATOR = ">>>";

	/**
	 * Line number in input file during parsing
	 */
	private int currentLineNumber;

	/**
	 * Map plate names to plates
	 */
	private final Map<String,Plate> plateNamePlateMap = new HashMap<String,Plate>();

	/**
	 * Map plate barcodes to plates
	 */
	private final Map<String,Plate> plateBarcodePlateMap = new HashMap<String,Plate>();
	

	/**
	 * The following Sets collect information for reporting
	 */
	private final Set<String> invalidPlateNames = new HashSet<String>();
	private final Set<String> validPlateNames = new HashSet<String>();
	private final Set<String> invalidAssayAttributeLabels = new HashSet<String>();
	private final Set<String> validAssayAttributeLabels = new HashSet<String>();
	private final Set<String> knownAttributePlateNames = new HashSet<String>();
	private final Set<String> platesIdentifiedByBarcode = new HashSet<String>();

	/**
	 * Assay or Measurement
	 */
	private String ASSAY_TYPE = "Assay";

	/**
	 * The plate for which we are currently parsing input
	 */
	private Plate currentPlate;

	/**
	 * Label read from input of current assay attribute
	 */
	private String currentAssayAttributeLabel;
	
	/**
	 * {@link #referenceEntityAssayAttribute}  Label NAME_SUFFIX
	 */
	private final static String NAME_SUFFIX = "Name";
	/**
	 * {@link #referenceEntityAssayAttribute} Label ID_SUFFIX
	 */
	private final static String ID_SUFFIX = "ID";
	/**
	 * The referenceEntityAssayAttribute is derived from {@link #currentAssayAttributeLabel};
	 */
	private AssayAttribute referenceEntityAssayAttribute;
	/**
	 * {@link #referenceEntityAssayAttribute} data provider
	 */
	private RefEntityAssayAttributeDataProvider refEntityAssayAttributeDataProvider;

	/**
	 * Current row in current plate
	 */
	private int currentRow;

	/**
	 * number of values read from input
	 */
	private int totalCountValuesRead;

	/**
	 * number of values successfully parsed and added
	 */
	private int totalCountValuesAdded;

	@Override
	public void options(AssayDataOptions options) {
		super.options(options);

		options.setRequirePlateSet(true);

		// tell Biologics we do not assign Antigens explicitly
		// see biologics.adapter.assay.PlateMultipleAntigenAssayDataAdapter for other scenarios
		options.setAdapterAssignsAntigen(false);
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

	/**
	 * Clear all internal state
	 */
	private void reset() {
		if (biologics.getModule() == Module.SC) {
			ASSAY_TYPE = "Assay";
		}
		else if (biologics.getModule() == Module.CLD) {
			ASSAY_TYPE = "Measurement";
		}
		plateNamePlateMap.clear();
		plateBarcodePlateMap.clear();
		invalidPlateNames.clear();
		validPlateNames.clear();
		invalidAssayAttributeLabels.clear();
		validAssayAttributeLabels.clear();
		knownAttributePlateNames.clear();
		platesIdentifiedByBarcode.clear();

		currentLineNumber = 0;

		totalCountValuesRead= 0;
		totalCountValuesAdded= 0;
		currentPlate = null;
		currentAssayAttributeLabel = null;
		referenceEntityAssayAttribute = null;
		refEntityAssayAttributeDataProvider = null;
		
	}

	/**
	 * Validate input and initialize
	 * @return true if initialization was successful
	 */
	private boolean validateInput() {

		if (biologics.getInputStream() == null) {
			reporter.error("Failed to import #0 data from file: The required input stream is not available.",
					ASSAY_TYPE.toLowerCase()
					);
			return false;
		}

		List<AssayPlate> plates = biologics.getAssayPlates();
		if ((plates == null) || (plates.size() == 0)) {
			reporter.error("Failed to generate #0 data: The required plates are not available.",
					ASSAY_TYPE.toLowerCase()
					);
			return false;
		}

		for (AssayPlate plate : plates) {
			String name =  plate.getAlias();
			if (name != null) {
				plateNamePlateMap.put(name, plate);
			}

			String barcode = plate.getBarcode();
			if (barcode != null) {
				plateBarcodePlateMap.put(barcode, plate);
			}
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
	 * @throws AssayAttributeException
	 */
	private void processLines(BufferedReader bufferedReader) throws IOException, AssayAttributeException {
		String line = null;
		while (null != (line = bufferedReader.readLine())) {
			currentLineNumber++;
			String trimmedLine = line.trim();

			if (trimmedLine.startsWith(COMMENT_LINE_INDICATOR)) {
				addCommentLine(trimmedLine);
			}
			else {
				parseLine(line);
			}
		}
	}

	/**
	 * Parse a single line of input.
	 * @param line
	 * @throws AssayAttributeException
	 */
	private void parseLine(String line) throws AssayAttributeException {
		log.debug("parseLine");

		if (line == null) {
			return;
		}

		if (isLookingForHeaderLine()) {
			if (line.startsWith(HEADER_LINE_INDICATOR)) {
				parseHeaderLine(line);
			}
			else if(0 != line.length()) {
				log.debug("Looking for header line, skipping non-header line: " + line + lineContext());
			}
		}
		else {
			parseAssayDataLine(line);
		}
	}

	/**
	 * Whether or not the parser is expecting a header line at this point
	 */
	protected boolean isLookingForHeaderLine() {
		return currentPlate == null || currentAssayAttributeLabel == null;
	}

	/**
	 * Parse header, validate, and assign the currentPlate and currentAssayAttribute
	 * @param line
	 * @throws AssayAttributeException
	 */
	private void parseHeaderLine(String line) throws AssayAttributeException {
		String[] items = split(line);
		currentRow = 0;

		if (items.length != 3) {
			reporter.warn("Could not parse the plate-section header : Wrong number of items." + lineContext());
			return;
		}

		String plateName = items[1];
		currentAssayAttributeLabel = items[2];
		prepareForEntityReferenceAssayAttribute(biologics, currentAssayAttributeLabel);
		currentPlate = validatePlate(plateName);

		if (currentPlate != null) {
			checkForDuplicates(currentPlate.getAlias(), currentPlate.getBarcode(), currentAssayAttributeLabel);
		}
	}

	/**
	 * Validate plate name read from input against Plates available in the system.
	 * If the plate could not be found by name, then the barcode is checked.
	 * @param plateName The plate name, or barcode if no plate matches by name.
	 * @return The plate, or {@code null} if no matching plate was found.
	 */
	protected Plate validatePlate(String plateName) {
		if (invalidPlateNames.contains(plateName)) {
			log.debug("Plate name already found to be invalid: " + plateName);
			return null;
		}

		if (! plateNamePlateMap.containsKey(plateName)
				&& ! plateBarcodePlateMap.containsKey(plateName)) {
			reporter.error("The plate name or barcode '#0' is invalid.", plateName);
			invalidPlateNames.add(plateName);
			return null;
		}

		Plate plate = plateNamePlateMap.get(plateName);
		if (plate == null) {
			plate = plateBarcodePlateMap.get(plateName);
			platesIdentifiedByBarcode.add(plateName);
		}

		validPlateNames.add(plateName);
		return plate;
	}

	/**
	 * Ensure that there is only one plate per AssayAttribute.
	 *
	 * @param plateName
	 * @param barcode
	 * @param assayAttributeLabel
	 */
	protected void checkForDuplicates(String plateName, String barcode, String assayAttributeLabel) {
		String attributePlateNameKey = currentAssayAttributeLabel + "////" + plateName + "////" + barcode;
		if (knownAttributePlateNames.contains(attributePlateNameKey)) {
			String plateNameString = (plateName != null && ! "".equals(plateName.trim())) ? "'" + plateName + "'" : "";
			String barcodeString = (barcode != null && ! "".equals(barcode.trim())) ? " with barcode '" + barcode + "'" : "";

			reporter.warn("Duplicate #0 Values were found for the Plate #1#2 and the #3 Attribute '#4'#5",
					ASSAY_TYPE,
					plateNameString,
					barcodeString,
					ASSAY_TYPE,
					currentAssayAttributeLabel,
					lineContext()
					);
			reporter.info("Please note that the replicated #0 Values have not been imported. Please check your input file to avoid loss of data.",
					ASSAY_TYPE
					);
			return;
		}

		knownAttributePlateNames.add(attributePlateNameKey);
	}

	/**
	 * Parse one line of data and generate an AssayValue for each field
	 * @param line
	 * @throws AssayAttributeException
	 */
	private void parseAssayDataLine(String line) throws AssayAttributeException {
		currentRow++;

		if(0 == line.length()) {
			checkPlateAllRowsProcessed();
			return;
		}
		String[] valueStrings = split(line, -1);

		int numberOfColumns = biologics.getNumberOfColumns();
		if (valueStrings.length != numberOfColumns) {
			reporter.warn("Found wrong number of columns (" + valueStrings.length + " instead of " + numberOfColumns + "). Skipping line. " + lineContext());
			return;
		}

		int rowIndex = currentRow;
		int columnIndex = 0;
		for (String valueString : valueStrings) {
			columnIndex++;

			// Only read as many columns as have been configured in the plate format
			if (columnIndex > numberOfColumns) {
				break;
			}

			// Skip wells with role other than VALUE
			if ( !AssayDataUtil.isValueWell(currentPlate, rowIndex, columnIndex) ) {
				reporter.debug("Found well with role " + currentPlate.getRole(rowIndex, columnIndex) + " " + parsingContext(rowIndex, columnIndex));
				continue;
			}

			Long visibleId = currentPlate.getWell(rowIndex, columnIndex);

			// Skip empty wells
			if (visibleId == null) {
				reporter.info("Found empty well at row " + parsingContext(rowIndex, columnIndex));
				continue;
			}

			if (biologics.getModule() == Module.SC) {
				Isolate isolate = biologics.getIsolateInformationProvider().retrieveByVisibleId(visibleId);
				totalCountValuesRead++;

				// Skip wells where we can't retrieve an Isolate from the system
				if (isolate == null) {
					String entityLabel = biologics.getIsolateInformationProvider().getSingularEntityLabel();
					reporter.warn(entityLabel + " not available for identifier '" + visibleId + "' " + parsingContext(rowIndex, columnIndex));
					continue;
				}
			}
			else if (biologics.getModule() == Module.CLD) {
				CellLine cellLine = biologics.getCellLineInformationProvider().retrieveByVisibleId(visibleId);
				totalCountValuesRead++;

				// Skip wells where we can't retrieve an Cell Line from the system
				if (cellLine == null) {
					String entityLabel = biologics.getCellLineInformationProvider().getSingularEntityLabel();
					reporter.warn(entityLabel + " not available for identifier '" + visibleId + "' " + parsingContext(rowIndex, columnIndex));
					continue;
				}
			}
			
			assayValue(rowIndex, columnIndex, valueString);
		}

		checkPlateAllRowsProcessed();
	}

	/**
	 * Checks whether all rows for the current plate have been processed.
	 * If true, a (new) header line is expected in the next line.
	 * See {@link PlateAssayDataAdapter#isLookingForHeaderLine()}.
	 */
	private void checkPlateAllRowsProcessed() {
		if (currentRow == biologics.getNumberOfRows()) {
			currentPlate = null;
			currentAssayAttributeLabel= null;
			referenceEntityAssayAttribute = null;
			refEntityAssayAttributeDataProvider = null;
		}
	}

	/**
	 * Create the resulting assayValue, handle potential exceptions,
	 * and store it in the list of all results
	 * @param rowIndex
	 * @param columnIndex
	 * @param valueString
	 * @throws AssayAttributeException
	 */
	private void assayValue(int rowIndex, int columnIndex, String valueString) throws AssayAttributeException {
		AssayValue assayValue = null;
		Object value = valueString;
		AssayAttribute currentAssayAttribute;
		try {
			if (referenceEntityAssayAttribute != null) {
				currentAssayAttribute = referenceEntityAssayAttribute;
				value = findReferenceEntity(valueString,  String.valueOf(rowIndex), String.valueOf(columnIndex));
			} else {
				currentAssayAttribute = currentAssayAttribute(rowIndex, columnIndex);
			}
			
			if (isValidInputForAssayValue(currentPlate,rowIndex, columnIndex, currentAssayAttribute, valueString)) {
				assayValue = biologics.getAssayValueFactory().createAssayValue(currentPlate, rowIndex, columnIndex, currentAssayAttribute, value);
			}
			// else: already noted as invalid attribute
		}
		catch (EmptyInputValueException e) {
			reporter.info(e.getMessage() + " " + parsingContext(rowIndex, columnIndex));
		}
		catch (AssayValueException e) {
			reporter.warn(e.getMessage() + " " + parsingContext(rowIndex, columnIndex));
		}
		catch (Throwable e) {
			reporter.error("Error parsing '" + valueString + " " + parsingContext(rowIndex, columnIndex));
			log.error("Error parsing ", e);
		}

		if (assayValue != null) {
			addAssayValue(assayValue);
			totalCountValuesAdded++;
		}
	}

	/**
	 * Allow subclasses to veto inclusion of a given value
	 *
	 * This implementation only requires that the current AssayAttribute is not null
	 * @param plate
	 * @param rowIndex
	 * @param columnIndex
	 * @param assayAttribute
	 * @param valueString
	 * @return
	 */
	protected boolean isValidInputForAssayValue(Plate plate, int rowIndex, int columnIndex, AssayAttribute assayAttribute, String valueString) {
		return assayAttribute != null;
	}

	/**
	 * Dynamically determine the AssayAttribute the current value should be assigned to.
	 * In this implementation, no dynamic behavior is needed. Subclasses mapping multiple antigens to multiple
	 * AssayAttributes can override this behavior.
	 * @return
	 */
	protected AssayAttribute currentAssayAttribute(int rowIndex, int columnIndex) throws AssayAttributeException {
		
		AssayAttribute assayAttribute = mapAssayAttribute(currentAssayAttributeLabel);
		
		if (assayAttribute == null) {
			assayAttribute = referenceEntityAssayAttribute;
		}

		if (assayAttribute == null) {
			invalidAssayAttributeLabels.add(currentAssayAttributeLabel);
			return null;
		}

		// report all assay attribute assignments that are not exact matches of the label
		if (!currentAssayAttributeLabel.equalsIgnoreCase( assayAttribute.getName() )) {
			reporter.info("Label '#0' has been assigned to #1 attribute '#2' #3",
					currentAssayAttributeLabel,
					ASSAY_TYPE.toLowerCase(),
					assayAttribute.getName(),
					lineContext()
					);
		}

		validAssayAttributeLabels.add(currentAssayAttributeLabel);
		return assayAttribute;
	}

	/**
	 * Provide standard message revealing position when parsing rows and columns
	 * @param rowIndex
	 * @param columnIndex
	 * @return
	 * @throws AssayAttributeException
	 */
	private String parsingContext(int rowIndex, int columnIndex) throws AssayAttributeException {
		// in case of dynamic attributes, currentAssayAttribute may be null
		AssayAttribute currentAssayAttribute = currentAssayAttribute(rowIndex, columnIndex);
		String attributeName = currentAssayAttribute != null  ? currentAssayAttribute.getName() : "DYNAMIC_ATTRIBUTE";
		return String.format(" [attribute '%s' at line: %d; row: %d; column: %d]", attributeName, currentLineNumber, rowIndex, columnIndex);
	}

	/**
	 * Provide standard message revealing position when parsingContext is not available
	 * @return
	 */
	private String lineContext() {
		return String.format(" [line: %d]", currentLineNumber);
	}


	/**
	 * Reports summary information after the parsing has been done.
	 */
	protected void reportAfterProcessing() {
		if (validAssayAttributeLabels.isEmpty()) {
			reporter.error("No valid #0 attributes have been found in the file.",
					ASSAY_TYPE.toLowerCase()
					);
		}
		else {
			List<String> names = new ArrayList<String>(validAssayAttributeLabels);
			Collections.sort(names);
			reporter.info("The following #0 attribute labels have been recognized: #1",
					ASSAY_TYPE.toLowerCase(),
					AssayDataUtil.joinStrings(names, ", ")
					);
		}

		if (invalidAssayAttributeLabels.size() > 0) {
			List<String> names = new ArrayList<String>(invalidAssayAttributeLabels);
			Collections.sort(names);
			reporter.warn("#0 attribute labels that were not assigned to #1 #2 attribute: #3",
					ASSAY_TYPE,
					ASSAY_TYPE.equalsIgnoreCase("Assay") ? "an" : "a",
					ASSAY_TYPE.toLowerCase(),
					AssayDataUtil.joinStrings(names, ", ")
					);
		}

		if (validAssayAttributeLabels.isEmpty()) {
			return;
		}

		if (validPlateNames.isEmpty()) {
			reporter.error("No valid plate names have been found in the file.");
		}
		else {
			List<String> names = new ArrayList<String>(validPlateNames);
			Collections.sort(names);
			reporter.info("#0 Data has been loaded for the plates: #1",
					ASSAY_TYPE,
					AssayDataUtil.joinStrings(names, ", ")
					);
		}

		if (! platesIdentifiedByBarcode.isEmpty()) {
			List<String> names = new ArrayList<String>(platesIdentifiedByBarcode);
			Collections.sort(names);
			reporter.info("The following plates have been identified by barcode: " + AssayDataUtil.joinStrings(names, ", ") );
		}

		if (invalidPlateNames.size() > 0) {
			List<String> names = new ArrayList<String>(invalidPlateNames);
			Collections.sort(names);
			reporter.warn("Found plates which are not part of the selected plate set: " + AssayDataUtil.joinStrings(names, ", ") );
			reporter.info("Please note that no #0 Data has been loaded for these plates.",
					ASSAY_TYPE);
		}

		if (invalidPlateNames.size() + validPlateNames.size() == 0) {
			reporter.info("Please make sure that the imported file has the required plate-based format. " +
					"The header lines of plate sections must start with the indicator string " +
					"'" + HEADER_LINE_INDICATOR + "', followed by the plate name and the " +
					ASSAY_TYPE.toLowerCase() + " attribute label.");
		}

		if (totalCountValuesAdded < totalCountValuesRead) {
			reporter.warn("Not all values in input file were imported successfully. There were #0 values, but only #1 could be processed.", totalCountValuesRead, totalCountValuesAdded);
		}
		else {
			reporter.info("Read #0 values from input, imported #1.", totalCountValuesRead, totalCountValuesAdded);
		}
	}

	protected String getHeaderLineIndicator() {
		return HEADER_LINE_INDICATOR;
	}

	protected int getCurrentLineNumber() {
		return currentLineNumber;
	}

	protected Map<String, Plate> getPlateNamePlateMap() {
		return plateNamePlateMap;
	}

	protected Set<String> getInvalidPlateNames() {
		return invalidPlateNames;
	}

	protected Set<String> getValidPlateNames() {
		return validPlateNames;
	}

	protected Set<String> getInvalidAssayAttributeLabels() {
		return invalidAssayAttributeLabels;
	}

	protected Set<String> getValidAssayAttributeLabels() {
		return validAssayAttributeLabels;
	}

	protected Set<String> getKnownAttributePlateNames() {
		return knownAttributePlateNames;
	}

	protected Plate getCurrentPlate() {
		return currentPlate;
	}

	protected int getCurrentRow() {
		return currentRow;
	}

	protected String getCurrentAssayAttributeLabel() {
		return currentAssayAttributeLabel;
	}
	
	
	private void prepareForEntityReferenceAssayAttribute(AssayDataCallback biologics, String label)
		{
			List<AssayAttribute> entityReferenceAssayAttributes = biologics.getAssayAttributes().stream()
					.filter(AssayAttribute::isReferenceToEntityValueType)
					.toList();
			
			for (AssayAttribute aa : entityReferenceAssayAttributes) {
				if (! aa.isReferenceToEntityValueType()) {
					continue;
				}
				
				if (label.equals(aa.getName())) {
					refEntityAssayAttributeDataProvider = new WrapperForQualifiedIdOrAlias(aa.getReferencedEntityInformationProvider());
					referenceEntityAssayAttribute = aa;
				}
				if (label.equals(aa.getName() + " " + ID_SUFFIX)) {
					refEntityAssayAttributeDataProvider = new WrapperForQualifiedId(aa.getReferencedEntityInformationProvider());
					referenceEntityAssayAttribute = aa;
				}
				if (label.equals(aa.getName() + " " + NAME_SUFFIX)) {
					refEntityAssayAttributeDataProvider = new WrapperForAlias(aa.getReferencedEntityInformationProvider());
					referenceEntityAssayAttribute = aa;
				}
			}
		}
		
		private Identifiable findReferenceEntity(String value, String rowIndex, String columnIndex) {
			Identifiable identifiable = refEntityAssayAttributeDataProvider.retrieveEntity(value);
			
			if (identifiable == null) {
				reporter.error("Cannot find the referenced #0 with value '#1' at row '#2' and column #3.",
						getCurrentAssayAttributeLabel(),
						value,
						rowIndex,
						columnIndex
						);
				return null;
			}
			
			return identifiable;
		}
		
		interface RefEntityAssayAttributeDataProvider {
			Identifiable retrieveEntity(String identifier);
		}
		
		record WrapperForQualifiedIdOrAlias(InformationProvider<?> ip) implements RefEntityAssayAttributeDataProvider {
			@Override
			public Identifiable retrieveEntity(String identifier) {
				return ip.retrieve(identifier);
			}
		}
		
		record WrapperForQualifiedId(InformationProvider<?> ip) implements RefEntityAssayAttributeDataProvider {
			@Override
			public Identifiable retrieveEntity(String identifier) {
				return ip.retrieveByQualifiedId(identifier);
			}
		}
		
		record WrapperForAlias(InformationProvider<?> ip) implements RefEntityAssayAttributeDataProvider {
			@Override
			public Identifiable retrieveEntity(String identifier) {
				return ip.retrieveByName(identifier);
			}
		}
}
