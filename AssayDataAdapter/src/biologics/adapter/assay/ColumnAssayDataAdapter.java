package biologics.adapter.assay;

import static genedata.bx.adapter.assay.v2.AssayDataUtil.findMatchingAssayAttribute;
import static genedata.bx.adapter.assay.v2.AssayDataUtil.joinStrings;
import static genedata.bx.adapter.assay.v2.AssayDataUtil.unquote;

import genedata.bx.adapter.Identifiable;
import genedata.bx.adapter.Module;
import genedata.bx.adapter.assay.v2.AbstractAssayDataAdapter;
import genedata.bx.adapter.assay.v2.AssayAttribute;
import genedata.bx.adapter.assay.v2.AssayAttributeException;
import genedata.bx.adapter.assay.v2.AssayDataOptions;
import genedata.bx.adapter.assay.v2.AssayValue;
import genedata.bx.adapter.assay.v2.AssayValueException;
import genedata.bx.adapter.entity.CellLine;
import genedata.bx.adapter.entity.InformationProvider;
import genedata.bx.adapter.entity.Isolate;
import genedata.bx.adapter.entity.Sample;

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
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

/**
 * The generic implementation of the Assay Data Adapter interface.
 * Imports Assay Values from tab-separated, column-based text files
 *
 */
public class ColumnAssayDataAdapter extends AbstractAssayDataAdapter {

	private final Logger log = Logger.getLogger(this.getClass());

	/**
	 * Assay or Measurement
	 */
	private String assayType = "Assay";
	private final static String NAME_SUFFIX = "Name";
	private final static String ID_SUFFIX = "ID";
	private final static String BARCODE_SUFFIX = "Barcode";
	private final static String RUN_GROUP_LABEL = "Run Group Label";

	private int currentLineNumber;
	private boolean foundHeaderLineFlag;
	private int groupColumnIndex = -1;

	private final Map<Integer, AssayAttribute> indexAssayAttributeMap = new HashMap<Integer, AssayAttribute>();
	private final Map<Integer, WrappedInformationProvider> indexInformationProviderMap = new HashMap<>();

	private int expectedColumnCount;
	private final List<String> matchedColumnLabels = new ArrayList<String>();
	private final List<String> unmatchedColumnLabels = new ArrayList<String>();
	private final List<String> multipleColumnLabels = new ArrayList<String>();
	private final List<String> unfoundAssayAttributes = new ArrayList<String>();
	private final List<String> linesWithInvalidColumnCount = new ArrayList<String>();
	private final List<String> linesMissingRequiredValues = new ArrayList<String>();
	private final List<String> invalids = new ArrayList<String>();
	private final Set<String> knownCloneAttributeNames = new HashSet<String>();
	private final Set<String> duplicateCloneNames = new HashSet<String>();

	/**
	 * number of values read from input
	 */
	private int totalCountValuesRead;
	/**
	 * number of values successfully parsed and added
	 */
	private int totalCountValuesAdded;

	private int isolateIdColumnIndex = -1;

	private int isolateNameColumnIndex = -1;

	private int cellLineIdColumnIndex = -1;

	private int cellLineNameColumnIndex = -1;

	private int sampleIdColumnIndex = -1;

	private int sampleNameColumnIndex = -1;

	private int sampleBarcodeColumnIndex = -1;

	@Override
	public void options(AssayDataOptions arg0) {
		super.options(arg0);
		options.setRequirePlateSet(false);
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
		if (biologics.getModule() == Module.SC) {
			assayType = "Assay";
		}
		else if (biologics.getModule() == Module.CLD) {
			assayType = "Measurement";
		}

		currentLineNumber = 0;
		groupColumnIndex = -1;
		foundHeaderLineFlag = false;

		matchedColumnLabels.clear();
		unmatchedColumnLabels.clear();
		multipleColumnLabels.clear();
		unfoundAssayAttributes.clear();
		linesWithInvalidColumnCount.clear();
		linesMissingRequiredValues.clear();
		invalids.clear();
		knownCloneAttributeNames.clear();
		duplicateCloneNames.clear();

		totalCountValuesRead= 0;
		totalCountValuesAdded= 0;
		isolateIdColumnIndex = -1;
		isolateNameColumnIndex = -1;
		cellLineIdColumnIndex = -1;
		cellLineNameColumnIndex = -1;
		sampleIdColumnIndex = -1;
		sampleNameColumnIndex = -1;
		sampleBarcodeColumnIndex = -1;
		return true;
	}

	/**
	 * Validate input and initialize
	 * @return true if initialization was successful
	 */
	private boolean validateInput() {

		if (biologics.getInputStream() == null) {
			reporter.error("Failed to import #0 data from file: The required input stream is not available.",
					assayType.toLowerCase()
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
			reporter.error("Failed to parse the contents of the file: #0", e.getMessage());
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

			if (0 == trimmedLine.length()) {
				// skip over empty line
			}
			else if (trimmedLine.startsWith(COMMENT_LINE_INDICATOR)) {
				addCommentLine(trimmedLine);
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
					assayType.toLowerCase(),
					joinStrings(unmatchedColumnLabels, ", ", true)
					);
			reporter.info("Please note that the contents of these columns will not be imported.");
		}

		if (unfoundAssayAttributes.size() > 0) {
			reporter.info("Optional #0 attributes which have not been found in the file: #1",
					assayType.toLowerCase(),
					joinStrings(unfoundAssayAttributes, ", ", true)
					 );
		}
	}

	private boolean foundEntityHeaderFlag() {
		return isolateIdColumnIndex != -1 || isolateNameColumnIndex != -1 ||
				cellLineIdColumnIndex != -1 || cellLineNameColumnIndex != -1 ||
				sampleIdColumnIndex != -1 || sampleNameColumnIndex != -1 || sampleBarcodeColumnIndex != -1;
	}

	/**
	 * Reports summary information after the parsing has been done.
	 */
	private void reportAfterProcessing() {

		if (!foundHeaderLineFlag && !foundEntityHeaderFlag()) {
			reporter.error("#0 Data has not been imported: Could not find a valid header line in file.",
					assayType
					);
			reporter.info("Note that a column with #0 identifiers or names is required. It must be labeled '#1' or '#2'.",
					getCloneEntityName(),
					getCloneEntityName() + " " + ID_SUFFIX,
					getCloneEntityName() + " " + NAME_SUFFIX);
		}

		if (linesWithInvalidColumnCount.size() > 0) {
			reporter.warn("Wrong number of columns was found in the lines: "
					+ joinStrings(linesWithInvalidColumnCount, ", "));
		}

		if (linesMissingRequiredValues.size() > 0) {
			reporter.error("Required #0 Values were missing in line(s): #1",
					assayType,
					joinStrings(linesMissingRequiredValues, ", ")
					);
		}

		if (linesWithInvalidColumnCount.size() + linesMissingRequiredValues.size() > 0) {
			reporter.info("Please note that the #0 Data from these line(s) have not been imported.",
					assayType
					);
		}

		if (invalids.size() > 0) {
			reporter.warn("#0 #1 not found in the Campaign: #2",
					getCloneEntityName(),
					invalids.size() == 1 ? "was" : "were",
					joinStrings(invalids, ", ")
					);
			reporter.info("Please note that the #0 must be a part of the Campaign in order to be recognized.", getCloneEntityName());
		}

		if (duplicateCloneNames.size() > 0) {
			List<String> list = new ArrayList<String>(duplicateCloneNames);
			Collections.sort(list);

			reporter.warn("Duplicate #0 Values were found for the following #1: #2",
					assayType,
					getCloneEntityName(),
					joinStrings(list, ", ")
					);
			reporter.info("Please note that the replicated #0 Values have not been imported. Please check your input file to avoid loss of data.",
					assayType
					);
		}

		if (totalCountValuesAdded < totalCountValuesRead) {
			reporter.warn("Not all values in input file were imported successfully. There were #0 values, but only #1 could be processed.", totalCountValuesRead, totalCountValuesAdded);
		}
		else {
			reporter.info("Read #0 values from input, imported #1.", totalCountValuesRead, totalCountValuesAdded);
		}
	}

	private void parseLine(String line) throws AssayAttributeException {
		log.debug("parseLine");

		if (line == null) {
			return;
		}

		if (!foundHeaderLineFlag) {
			foundHeaderLineFlag = parseHeaderLine(line);

			if (foundHeaderLineFlag) {
				reportAfterHeaderLine();
			}
		}
		else {
			parseAssayDataLine(line);
		}
	}

	private boolean parseHeaderLine(String line) throws AssayAttributeException {
		log.debug("parseHeaderLine");

		String[] items = validateColumnLabels(line);

		if (! foundEntityHeaderFlag()) {
			log.debug("Not recognized as header line: " + line);
			return false;
		}

		List<AssayAttribute> assayAttributes = initAssayAttributes(items);

		return checkRequiredAssayAttributes(assayAttributes);
	}

	/**
	 * @param label The column header label.
	 * @return {@code true} if the label matches the 'Antibody Clone ID/Name' or 'Cell Line ID/Name'
	 * or 'Sample ID/Name/Barcode' pattern.
	 */
	private boolean matchesEntityColumnNamePattern(String label) {
		if (biologics.getModule() == Module.SC) {
			return matchesIsolateColumnPattern(label);
		} else if (biologics.getModule() == Module.CLD) {
			return matchesCellLineColumnPattern(label)
			|| matchesSampleColumnPattern(label);
		}

		return false;
	}

	private String getIsolateEntityLabel() {
		return biologics.getIsolateInformationProvider().getSingularEntityLabel();
	}

	private boolean matchesIsolateColumnPattern(String label) {
		return (label != null)
				&& (matchesIsolateIdColumnPattern(label)
				|| matchesIsolateNameColumnPattern(label));
	}

	private boolean matchesIsolateIdColumnPattern(String label) {
		return (label != null)
				&& label.equals(getIsolateEntityLabel() + " " + ID_SUFFIX);
	}

	private boolean matchesIsolateNameColumnPattern(String label) {
		return (label != null)
				&& label.equals(getIsolateEntityLabel() + " " + NAME_SUFFIX);
	}

	private String getCellLineEntityLabel() {
		return biologics.getCellLineInformationProvider().getSingularEntityLabel();
	}

	private boolean matchesCellLineIdColumnPattern(String label) {
		return (label != null)
				&& label.equals(getCellLineEntityLabel() + " " + ID_SUFFIX);
	}

	private boolean matchesCellLineNameColumnPattern(String label) {
		return (label != null)
				&& label.equals(getCellLineEntityLabel() + " " + NAME_SUFFIX);
	}

	private boolean matchesCellLineColumnPattern(String label) {
		return (label != null)
				&& (matchesCellLineIdColumnPattern(label)
				|| matchesCellLineNameColumnPattern(label));
	}

	private String getSampleEntityLabel() {
		return biologics.getSampleInformationProvider().getSingularEntityLabel();
	}

	private boolean matchesSampleIdColumnPattern(String label) {
		return (label != null)
				&& label.equals(getSampleEntityLabel() + " " + ID_SUFFIX);
	}

	private boolean matchesSampleNameColumnPattern(String label) {
		return (label != null)
				&& label.equals(getSampleEntityLabel() + " " + NAME_SUFFIX);
	}

	private boolean matchesSampleBarcodeColumnPattern(String label) {
		return (label != null)
				&& label.equals(getSampleEntityLabel() + " " + BARCODE_SUFFIX);
	}

	private boolean matchesSampleColumnPattern(String label) {
		return (label != null)
				&& label.toUpperCase().startsWith(getSampleEntityLabel().toUpperCase())
				&& (matchesSampleIdColumnPattern(label)
				|| matchesSampleNameColumnPattern(label)
				|| matchesSampleBarcodeColumnPattern(label));
	}

	private String getCloneEntityName() {
		if (biologics.getModule() == Module.SC) {
			return getIsolateEntityLabel();
		} else if (biologics.getModule() == Module.CLD) {
			if (cellLineIdColumnIndex !=-1 || cellLineNameColumnIndex !=-1) {
				return getCellLineEntityLabel();
			} else {
				return getSampleEntityLabel();
			}
		}
		return "";
	}

	String[] validateColumnLabels(String line) {
		// make sure that trailing empty items are included by setting the limit to a negative value
		String[] items = split(line, -1);
		expectedColumnCount = items.length;

		for (int index = 0; index < items.length; index++) {
			String label = unquote(items[index]).trim();

			if (matchesEntityColumnNamePattern(label)) {

				if (!matchedColumnLabels.contains(label)) {

					matchedColumnLabels.add(label);
					if (biologics.getModule() == Module.SC) {
						if (matchesIsolateIdColumnPattern(label)) {
							isolateIdColumnIndex = index;
						} else if (matchesIsolateNameColumnPattern(label)) {
							isolateNameColumnIndex = index;
						}
					}
					else if (biologics.getModule() == Module.CLD) {
						if (matchesCellLineIdColumnPattern(label)) {
							cellLineIdColumnIndex = index;
						} else if (matchesCellLineNameColumnPattern(label)) {
							cellLineNameColumnIndex = index;
						} else if (matchesSampleIdColumnPattern(label)) {
							sampleIdColumnIndex = index;
						} else if (matchesSampleNameColumnPattern(label)) {
							sampleNameColumnIndex = index;
						} else if (matchesSampleBarcodeColumnPattern(label)) {
							sampleBarcodeColumnIndex = index;
						}
					}
				}
				else {
					multipleColumnLabels.add(label);
				}
			} else if (matchesGroupLabelPattern(label)) {
				if (groupColumnIndex > -1) {
					reporter.warn("Already found a column '#0'. The column '#1' is therefore ignored."
							, RUN_GROUP_LABEL, label);
				} else {
					if (!matchedColumnLabels.contains(label)) {
						groupColumnIndex = index;
						matchedColumnLabels.add(label);
					}
				}
			}
		}
		return items;
	}

	List<AssayAttribute> initAssayAttributes(String[] items)
			throws AssayAttributeException {
		List<AssayAttribute> assayAttributes = new ArrayList<AssayAttribute>(biologics.getAssayAttributes());
		List<AssayAttribute> entityReferenceAssayAttributes = assayAttributes.stream()
				.filter(AssayAttribute::isReferenceToEntityValueType)
				.collect(Collectors.toList());

		indexAssayAttributeMap.clear();
		indexInformationProviderMap.clear();

		for (int index = 0; index < items.length; index++) {
			String label = unquote(items[index]).trim();

			if (matchesEntityColumnNamePattern(label) || matchesGroupLabelPattern(label)) {
				// already handled
				continue;
			}

			if (matchedColumnLabels.contains(label) || unmatchedColumnLabels.contains(label)) {
				multipleColumnLabels.add(label);
				continue;
			}

			AssayAttribute assayAttribute = findEntityReferenceAssayAttribute(index, label, entityReferenceAssayAttributes);
			
			if (assayAttribute == null) {
				assayAttribute = findMatchingAssayAttribute(label, assayAttributes);
			}

			if (assayAttribute != null) {
				// report only the non-trivial assay attribute assignments
				if (!label.equalsIgnoreCase( assayAttribute.getName() ) && !label.startsWith( assayAttribute.getName() )) {
					reporter.info("Column label '#0' has been assigned to #1 attribute '#2'.",
							label,
							assayType.toLowerCase(),
							assayAttribute.getName()
							);
				}

				indexAssayAttributeMap.put( Integer.valueOf(index), assayAttribute);
				assayAttributes.remove(assayAttribute);

				matchedColumnLabels.add(label);
			}
			else {
				if (label.length() > 0) {
					unmatchedColumnLabels.add(label);
				}
			}
		}
		return assayAttributes;
	}

	private AssayAttribute findEntityReferenceAssayAttribute(int index, String label, List<AssayAttribute> entityReferenceAssayAttributes) {
		for (AssayAttribute aa : entityReferenceAssayAttributes) {
			if (! aa.isReferenceToEntityValueType()) {
				continue;
			}
			
			if (label.equals(aa.getName())) {
				indexInformationProviderMap.put(index, new WrapperForQualifiedIdOrAlias(aa.getReferencedEntityInformationProvider()));
				return aa;
			}
			if (label.equals(aa.getName() + " " + ID_SUFFIX)) {
				indexInformationProviderMap.put(index, new WrapperForQualifiedId(aa.getReferencedEntityInformationProvider()));
				return aa;
			}
			if (label.equals(aa.getName() + " " + NAME_SUFFIX)) {
				indexInformationProviderMap.put(index, new WrapperForAlias(aa.getReferencedEntityInformationProvider()));
				return aa;
			}
		}
		
		return null;
	}

	boolean checkRequiredAssayAttributes(List<AssayAttribute> assayAttributes) {
		// check if all required assay attributes have been found
		for (AssayAttribute assayAttribute : assayAttributes) {
			unfoundAssayAttributes.add( assayAttribute.getName() );

			if (assayAttribute.isRequired()) {
				reporter.error("Import of #0 Data failed: Mandatory #1 attribute '#2' not found in header line.",
						assayType,
						assayType.toLowerCase(),
						assayAttribute.getName()
						);
				return false;
			}
		}
		return true;
	}

	private Identifiable parseEntity(String[] items, int idColumnIndex, int nameColumnIndex, InformationProvider<?> informationProvider, String entityLabel) {
		Identifiable byId = null;
		Identifiable byName = null;
		if (idColumnIndex != -1) {
			String item = items[idColumnIndex];
			String unquote = unquote(item);
			if (!isEmpty(unquote)) {
				String trimmed = unquote.trim();
				if (! isEmpty(trimmed)) {
					byId = informationProvider.retrieveByQualifiedId(trimmed);
					if (byId == null) {
						invalids.add(trimmed + lineContext() );
					}
				}
			}
		}
		if (nameColumnIndex != -1) {
			String item = items[nameColumnIndex];
			String unquote = unquote(item);
			if (!isEmpty(unquote)) {
				String trimmed = unquote.trim();
				if (! isEmpty(trimmed)) {
					byName = informationProvider.retrieveByName(trimmed);
					if (byName == null) {
						invalids.add(trimmed + lineContext() );
					}
				}
			}
		}
		if (byId != null && byName != null) {
			if (!byId.equals(byName)) {
				reporter.error("#0 #1 does not match with #2 '#3'",
						getCloneEntityName()  + " " + ID_SUFFIX,
						byId.getQualifiedId(),
						getCloneEntityName() + " " + NAME_SUFFIX,
						byName.getAlias());
				return null;
			}
		}
		return nvl(byId, byName);
	}

	private Identifiable parseIsolate(String[] items) {
		return parseEntity(items, isolateIdColumnIndex, isolateNameColumnIndex, biologics.getIsolateInformationProvider(), getIsolateEntityLabel());
	}

	private Identifiable parseCellLine(String[] items) {
		return parseEntity(items, cellLineIdColumnIndex, cellLineNameColumnIndex, biologics.getCellLineInformationProvider(), getCellLineEntityLabel());
	}

	private Sample parseSample(String[] items) {
		Identifiable byIdName = parseEntity(items, sampleIdColumnIndex, sampleNameColumnIndex, biologics.getSampleInformationProvider(), getSampleEntityLabel());
		Sample byBarcode = null;
		if (sampleBarcodeColumnIndex != -1) {
			String item = items[sampleBarcodeColumnIndex];
			String unquote = unquote(item);
			if (!isEmpty(unquote)) {
				String trimmed = unquote.trim();
				if (! isEmpty(trimmed)) {
					byBarcode = biologics.getSampleInformationProvider().retrieveByBarcode(trimmed);
					if (byBarcode == null) {
						invalids.add(trimmed + lineContext() );
					}
				}
			}
		}
		if (byIdName != null && byBarcode != null) {
			if (!byIdName.equals(byBarcode)) {
				reporter.error("#0 #1 does not match with #2 '#3'",
						sampleIdColumnIndex != -1 ? getSampleEntityLabel() + " " + ID_SUFFIX :
							getSampleEntityLabel() + " " + NAME_SUFFIX,
							sampleIdColumnIndex != -1 ? byIdName.getQualifiedId() : "'" + byIdName.getAlias() + "'",
						getSampleEntityLabel() + " " + "Barcode",
						byBarcode.getBarcode());
				return null;
			}
		}
		return (Sample)nvl(byIdName, byBarcode);
	}

	void parseAssayDataLine(String line) throws AssayAttributeException {

		// make sure that trailing empty items are included by setting the limit to a negative value
		String[] items = split(line, -1);

//		reporter.info("Number of items in line " + currentLineNumber + ": " + items.length);

		if (items.length != expectedColumnCount) {
			linesWithInvalidColumnCount.add( Integer.toString(currentLineNumber) );
			return;
		}


		Identifiable clone = null;
		if (biologics.getModule() == Module.SC) {
			clone = parseIsolate(items);
		}
		else if (biologics.getModule() == Module.CLD) {
			clone = parseCellLine(items);
			Sample sample = parseSample(items);
			if (!(clone != null ^ sample != null)) {
				reporter.error("Please provide either a #0 or a #1. Skipping line #2",
						getCellLineEntityLabel(),
						getSampleEntityLabel(),
						lineContext());
				return;
			}
			if (sample != null) {
				clone = sample;
			}
		}
		// Skip wells where we can't retrieve a Clone from the system
		if (clone == null) {
			return;
		}
		
		Map<AssayAttribute, Set<Identifiable>> attributeIdentifiablesMap = new HashMap<>();
		List<AssayValue> newAssayValues = new ArrayList<AssayValue>();
		for (Integer index : indexAssayAttributeMap.keySet()) {
			totalCountValuesRead++;
			String valueString = unquote(items[ index.intValue() ]).trim();
			AssayAttribute assayAttribute = indexAssayAttributeMap.get( index.intValue() );
			AssayValue assayValue = null;
			String groupLabel = null;
			if (groupColumnIndex > -1) {
				groupLabel = unquote(items[groupColumnIndex]);
				if (groupLabel != null) {
					groupLabel.trim();
				}
			}
			if (! assayAttribute.isReferenceToEntityValueType()
					&& isDuplicate(clone.getQualifiedId(), assayAttribute, groupLabel)) {
				reporter.error("Duplicate attribute #0 for #1: #2. Skipping line #3",
						assayAttribute.getName(),
						getCloneEntityName(),
						clone.getQualifiedId(),
						lineContext());
				duplicateCloneNames.add(clone.getQualifiedId());
				return;
			}
			
			Object value = valueString;
			
			if (assayAttribute.isReferenceToEntityValueType()) {
				if (valueString.isEmpty()) {
					totalCountValuesRead--; // do not count this empty value
				}
				else {
					WrappedInformationProvider informationProvider = indexInformationProviderMap.get(index);
					if (informationProvider == null) {
						reporter.error("Failed to retrieve the referenced entity for value '#0' #1.",
								valueString,
								lineContext());
						return;
					}
					
					Identifiable identifiable = informationProvider.retrieveEntity(valueString);
					if (identifiable == null) {
						reporter.error("Cannot find the referenced #0 with #1 '#2' #3.",
								informationProvider.getSingularEntityLabel(),
								informationProvider.getColumnName(),
								valueString,
								lineContext());
						return;
					}
					boolean valueAlreadyAdded = attributeIdentifiablesMap.containsKey(assayAttribute);
					attributeIdentifiablesMap.computeIfAbsent(assayAttribute, s -> new HashSet<>()).add(identifiable);
					
					if (valueAlreadyAdded) {
						value = null;
						totalCountValuesRead--; // do not count this already added value 
					}
					else {
						value = identifiable;
					}
				}
			}

			if (valueString.length() == 0) {
				if (assayAttribute.isRequired()) {
					reporter.error("Found empty input value for required #0 attribute '#1' #2",
							assayType.toLowerCase(),
							assayAttribute.getName(),
							lineContext()
							);
				}
			}
			else if (value != null) {
				assayValue = assayValue(clone, assayAttribute, valueString, value);
			}

			if (assayAttribute.isRequired() && assayValue == null) {
				newAssayValues= null;
				break; // skipping rest of values
			}

			if (assayValue != null) {
				assayValue.setGroupLabel(groupLabel);
				newAssayValues.add(assayValue);
			}

		}

		for (AssayAttribute att : attributeIdentifiablesMap.keySet()) {
			if (attributeIdentifiablesMap.get(att).size() > 1) {
				reporter.error("Found more than one referenced entity for #0 attribute '#1': #2 #3.",
						assayType.toLowerCase(),
						att.getName(),
						attributeIdentifiablesMap.get(att).stream()
							.map(id -> id.getQualifiedId())
							.sorted()
							.collect(Collectors.joining(", ")),
						lineContext());
				return;
			}
		}
		
		if (newAssayValues != null) {
			for (AssayValue assayValue : newAssayValues) {
				totalCountValuesAdded++;
				addAssayValue(assayValue);
			}
		}
		else {
			linesMissingRequiredValues.add( Integer.toString(currentLineNumber) );
		}
	}

	boolean isDuplicate(String cloneName, AssayAttribute assayAttribute, String groupLabel) {
		String cloneAttributeName = cloneName + "////" + assayAttribute.getName() + (groupLabel != null ? "////" + groupLabel : "");
		if (knownCloneAttributeNames.contains(cloneAttributeName)) {
			duplicateCloneNames.add(cloneName);
			return true;
		}

		knownCloneAttributeNames.add(cloneAttributeName);
		return false;
	}

	/**
	 * Create the resulting assayValue, handle potential exceptions,
	 * and store it in the list of all results
	 * @param newAssayValues
	 * @param entity
	 * @param rowIndex
	 * @param columnIndex
	 * @param valueString
	 * @param value
	 * @return
	 * @throws AssayAttributeException
	 */
	private AssayValue assayValue(Identifiable entity, AssayAttribute assayAttribute, String valueString, Object value) throws AssayAttributeException {
		AssayValue assayValue = null;
		try {
			if (assayAttribute != null) {
				if (biologics.getModule() == Module.SC) {
					assayValue = biologics.getAssayValueFactory().createAssayValue((Isolate)entity, assayAttribute, value);
				}
				else if (biologics.getModule() == Module.CLD) {
					if (entity instanceof CellLine) {
						assayValue = biologics.getAssayValueFactory().createAssayValue((CellLine)entity, assayAttribute, value);
					} else {
						if (entity instanceof Sample) {
							assayValue = biologics.getAssayValueFactory().createAssayValue((Sample)entity, assayAttribute, value);
						} else {
							reporter.error("#0 is of unsupported type.", entity.getQualifiedId());
						}
					}
				}
			}
			// else: already noted as invalid attribute
		}
		catch (AssayValueException e) {
			reporter.warn(e.getMessage() + " " + lineContext());
		}
		catch (Throwable e) {
			reporter.error("Error parsing the value '#0' #1", valueString, lineContext());
			log.error("Error parsing ", e);
		}

		return assayValue;
	}

	/**
	 * Provide standard message revealing position when parsingContext is not available
	 * @return
	 */
	private String lineContext() {
		return String.format(" [line: %d]", currentLineNumber);
	}

	/**
	 * @param label The column header label.
	 * @return {@code true} if the label matches the 'Run Group Label' pattern.
	 */
	private static boolean matchesGroupLabelPattern(String label) {
		return (label != null)
				&& label.equalsIgnoreCase(RUN_GROUP_LABEL);
	}

	final int getGroupColumnIndex() {
		return groupColumnIndex;
	}

	private static <T> T nvl(T a, T b) {
		if (a != null) {
			return a;
		}
		return b;
	}

	private static boolean isEmpty(String s) {
		return null == s || 0 == s.trim().length();
	}
	
	interface WrappedInformationProvider {
		Identifiable retrieveEntity(String identifier);
		String getSingularEntityLabel();
		String getColumnName();
	}

	class WrapperForQualifiedIdOrAlias implements WrappedInformationProvider {
		final InformationProvider<?> ip;
		// constructor
		WrapperForQualifiedIdOrAlias(InformationProvider<?> ip) {
			this.ip = ip;
		}
		
		@Override
		public Identifiable retrieveEntity(String identifier) {
				return ip.retrieve(identifier);
		}
		@Override
		public String getSingularEntityLabel() {
				return ip.getSingularEntityLabel();
		}
		@Override
		public String getColumnName() {
				return "ID or Name";
		}
	}
	
	class WrapperForQualifiedId implements WrappedInformationProvider {
		final InformationProvider<?> ip;
		// constructor
		WrapperForQualifiedId(InformationProvider<?> ip) {
			this.ip = ip;
		}
		
		@Override
		public Identifiable retrieveEntity(String identifier) {
				return ip.retrieveByQualifiedId(identifier);
		}
		@Override
		public String getSingularEntityLabel() {
				return ip.getSingularEntityLabel();
		}
		@Override
		public String getColumnName() {
				return "ID";
		}
	}
	
	class WrapperForAlias implements WrappedInformationProvider {
		final InformationProvider<?> ip;
		// constructor
		WrapperForAlias(InformationProvider<?> ip) {
			this.ip = ip;
		}
		
		@Override
		public Identifiable retrieveEntity(String identifier) {
				return ip.retrieveByName(identifier);
		}
		@Override
		public String getSingularEntityLabel() {
				return ip.getSingularEntityLabel();
		}
		@Override
		public String getColumnName() {
				return "Name";
		}
	}
}
