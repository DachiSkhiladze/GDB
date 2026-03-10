package biologics.sc.adapter.assay;

import genedata.bx.adapter.assay.AssayAttribute;
import genedata.bx.adapter.assay.AssayValue;
import genedata.bx.adapter.assay.AssayValueFactory;
import genedata.bx.adapter.assay.Isolate;
import genedata.bx.adapter.assay.MismatchedDateFormatException;
import genedata.bx.adapter.assay.NumericValueTooLargeException;
import genedata.bx.adapter.assay.StringValueTooLongException;
import genedata.bx.adapter.assay.UnknownCvValueException;
import genedata.bx.adapter.plate.Plate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The generic implementation of the Assay Data Adapter interface.
 * Imports Assay Values from tab-separated, column-based text files.
 * 
 * With adaptions for the 'BD Bioanalyzer' format.
 * 
 * @deprecated use V2 AssayData API instead
 */
@Deprecated
public class BdBioanalyzerAssayDataAdapter extends AbstractAssayDataAdapter {
	
//	private final static String EXPERIMENT_NAME = "Experiment Name";
	private final static String PLATE_ID = "Plate ID";
	private final static String WELL_ID = "Well ID";
	
	/** The supported quote characters (single and double quote). */
	private final static String QUOTE_CHARACTERS = "\"'";
	
	/** Defines a mapping between the column names in the file and the required assay attribute labels. */
	private final static String[][] COLUMN_NAME_LABELS = {
		{"P2 #Events", "All"},
		{"P2 %Parent", "Parent"},
		{"P2 Yellow-A Geometric Mean", "Y-Geo Mean"}		
	};
	
	private final List<AssayValue> assayValues = new ArrayList<AssayValue>();
	private int currentLineNumber;
	private boolean foundHeaderLineFlag;
	private int plateIdColumnIndex;
	private int wellIdColumnIndex;
	private final Map<Integer, AssayAttribute> indexAssayAttributeMap = new HashMap<Integer, AssayAttribute>();
	private final Map<String,Plate> plateNamePlateMap = new HashMap<String,Plate>();
	private final Set<String> invalidPlateNames = new HashSet<String>();
	private final Set<String> validPlateNames = new HashSet<String>();
	private final Map<String,String> columnNameLabelMap = new HashMap<String,String>();

	private int expectedColumnCount;
	private final List<String> matchedColumnLabels = new ArrayList<String>();
	private final List<String> unmatchedColumnLabels = new ArrayList<String>();
	private final List<String> multipleColumnLabels = new ArrayList<String>();
	private final List<String> unfoundAssayAttributes = new ArrayList<String>();
	private final List<String> linesWithInvalidColumnCount = new ArrayList<String>();
	private final List<String> linesMissingRequiredValues = new ArrayList<String>();
	private final Set<String> knownIsolateAttributeNames = new HashSet<String>();
	private final Set<String> duplicateIsolateNames = new HashSet<String>();

	@Override
	public boolean requiresInputStream() {
		return true;
	}
	
	@Override
	public boolean requiresPlateSet() {
		return true;
	}
	
	@Override
	public List<AssayValue> perform(AssayValueFactory assayValueFactory, List<AssayAttribute> assayAttributes)
	{
		setAssayValueFactory(assayValueFactory);
		setAssayAttributes(assayAttributes);
		
//		long startTimeMillis = System.currentTimeMillis();
		
		if (!prepareForParsing()) {
			return assayValues;
		}
		
		currentLineNumber = 0;
		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader( new InputStreamReader(inputStream, getCharsetName() ));
			
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
		catch (Exception e) {
			log.error("Failed to parse the contents of the input stream.", e);
		}
		finally {
			if (bufferedReader != null) {
				try { bufferedReader.close(); } catch (Exception e) { /* ignored */ }
			}
		}
		
		reportAfterParsing();
		
//		addInfoMessage("Required time: " + (System.currentTimeMillis() - startTimeMillis) / 1000.0 + " sec");
		
		return assayValues;
	}

	private boolean prepareForParsing()
	{
		clear();
		assayValues.clear();
		foundHeaderLineFlag = false;
		plateIdColumnIndex = -1;
		wellIdColumnIndex = -1;
		
		plateNamePlateMap.clear();
		columnNameLabelMap.clear();
		invalidPlateNames.clear();
		validPlateNames.clear();
		matchedColumnLabels.clear();
		unmatchedColumnLabels.clear();
		multipleColumnLabels.clear();
		unfoundAssayAttributes.clear();
		linesWithInvalidColumnCount.clear();
		linesMissingRequiredValues.clear();
		knownIsolateAttributeNames.clear();
		duplicateIsolateNames.clear();
		
		if (inputStream == null) {
			addErrorMessage("Failed to import the Assay Data from file: The required input stream is not available.");
			return false;
		}
		
		if ((plates == null) || (plates.size() == 0)) {
			addErrorMessage("Failed to generate the Assay Data: The required plates are not available.");
			return false;
		}
		
		for (Plate plate : plates) {
			plateNamePlateMap.put( plate.getAlias(), plate);
		}
		
		for (String[] columnNameLabel : COLUMN_NAME_LABELS) {
			columnNameLabelMap.put(columnNameLabel[0], columnNameLabel[1]);
			log.debug("Defined mapping between column name '" + columnNameLabel[0] + "' and assay attribute label '" + columnNameLabel[1] + "'.");
		}
		
		return true;
	}

	/**
	 * Reports summary information after the header line has been found.
	 */
	private void reportAfterHeaderLine()
	{
		if (multipleColumnLabels.size() > 0) {
			String message = (multipleColumnLabels.size() > 1) ?
					"The following column labels were found multiple times in the header line: " :
					"The following column label was found multiple times in the header line: ";
			
			addWarningMessage(message + joinStrings(multipleColumnLabels, ", ", true));
			addInfoMessage("Please note that each column label should appear only once in the header line. " +
					"If a column label appears several times, then only the contents of the first column will be taken into account.");
		}
		
		if (matchedColumnLabels.size() > 0) {
			addInfoMessage("The following column labels have been recognized: "
					+ joinStrings(matchedColumnLabels, ", ", true));
		}
		
		if (unmatchedColumnLabels.size() > 0) {
			addInfoMessage("Column labels that were not assigned to an assay attribute: "
					+ joinStrings(unmatchedColumnLabels, ", ", true));
			addInfoMessage("Please note that the contents of these columns will not be imported.");
		}
		
		if (unfoundAssayAttributes.size() > 0) {
			addInfoMessage("Optional assay attributes which have not been found in the file: "
					+ joinStrings(unfoundAssayAttributes, ", ", true) );
		}
	}

	/**
	 * Reports summary information after the parsing has been done.
	 */
	private void reportAfterParsing() {
		
		if (! foundHeaderLineFlag) {
			addErrorMessage("Assay Data has not been imported: Could not find a valid header line in file.");
		}
		
		if (plateIdColumnIndex == -1) {
			addInfoMessage("Note that the column with Plate identifiers is required. " +
					"It must be labeled '" + PLATE_ID + "'.");
		}
		
		if (wellIdColumnIndex == -1) {
			addInfoMessage("Note that the column with Well identifiers is required. " +
					"It must be labeled '" + WELL_ID + "'.");
		}
		
		if (validPlateNames.isEmpty()) {
			addErrorMessage("No valid plate names have been found in the file.");
		}
		else {
			List<String> names = new ArrayList<String>(validPlateNames);
			Collections.sort(names);
			addInfoMessage("Assay Data has been loaded for the plates: " + joinStrings(names, ", ") );
		}
		
		if (invalidPlateNames.size() > 0) {
			List<String> names = new ArrayList<String>(invalidPlateNames);
			Collections.sort(names);
			addWarningMessage("Found plates which are not part of the selected plate set: " + joinStrings(names, ", ") );
			addInfoMessage("Please note that no Assay Data has been loaded for these plates.");
		}
		
		if (linesWithInvalidColumnCount.size() > 0) {
			addWarningMessage("Wrong number of columns was found in the lines: "
					+ joinStrings(linesWithInvalidColumnCount, ", "));
		}
		
		if (linesMissingRequiredValues.size() > 0) {
			addWarningMessage("Required Assay Values were missing in the lines: "
					+ joinStrings(linesMissingRequiredValues, ", "));
		}
		
		if (linesWithInvalidColumnCount.size() + linesMissingRequiredValues.size() > 0) {
			addInfoMessage("Please note that the Assay Data from these lines has not been imported.");
		}
		
		if (duplicateIsolateNames.size() > 0) {
			List<String> list = new ArrayList<String>(duplicateIsolateNames);
			Collections.sort(list);
			
			addWarningMessage("Duplicate Assay Values were found for the following Antibody Clones: "
					+ joinStrings(list, ", "));
			addInfoMessage("Please note that the replicated Assay Values have not been imported. Please check your input file to avoid loss of data.");
		}
	}

	private void parseLine(String line) {
		log.debug("parseLine");
		
		if (line == null) {
			return;
		}
		
		if (!foundHeaderLineFlag) {
			if (plateIdColumnIndex == -1 || wellIdColumnIndex == -1) {
				foundHeaderLineFlag = parseHeaderLine(line);
			}
			
			if (foundHeaderLineFlag) {
				reportAfterHeaderLine();
			}
		}
		else {
			parseAssayDataLine(line);
		}
	}

	private boolean parseHeaderLine(String line) {
		log.debug("parseHeaderLine");
		
		String[] items = splitString(line, CONFIGURED_DELIMITER, QUOTE_CHARACTERS);
		expectedColumnCount = items.length;
		
		for (int index = 0; index < items.length; index++) {
			String label = unquote(items[index]).trim();
			
			if (PLATE_ID.equalsIgnoreCase(label)) {
				if (plateIdColumnIndex > -1) {
					addWarningMessage("Already found a column with Plate identifiers. The column '" + label + "' is therefore ignored.");
				}
				else {
					if (!matchedColumnLabels.contains(label)) {
						plateIdColumnIndex = index;
						matchedColumnLabels.add(label);
					}
					else {
						multipleColumnLabels.add(label);
					}
				}
			}
			
			if (WELL_ID.equalsIgnoreCase(label)) {
				if (wellIdColumnIndex > -1) {
					addWarningMessage("Already found a column with Well identifiers. The column '" + label + "' is therefore ignored.");
				}
				else {
					if (!matchedColumnLabels.contains(label)) {
						wellIdColumnIndex = index;
						matchedColumnLabels.add(label);
					}
					else {
						multipleColumnLabels.add(label);
					}
				}
			}
		}
		
		if (plateIdColumnIndex == -1 || wellIdColumnIndex == -1) {
			log.debug("Not recognized as header line: " + line);
			return false;
		}
		
		List<AssayAttribute> assayAttributes = new ArrayList<AssayAttribute>();
		assayAttributes.addAll( getAssayAttributes() );
		
		indexAssayAttributeMap.clear();
		
		for (int index = 0; index < items.length; index++) {
			String label = unquote(items[index]).trim();
			
			if (PLATE_ID.equals(label) || WELL_ID.equals(label)) {
				// already handled
				continue;
			}
			
			if (matchedColumnLabels.contains(label) || unmatchedColumnLabels.contains(label)) {
				multipleColumnLabels.add(label);
				continue;
			}
			
			AssayAttribute assayAttribute = getAssayAttribute(assayAttributes, label);
			
			if (assayAttribute != null) {
				// report only the non-trivial assay attribute assignments
				if (!label.equalsIgnoreCase( assayAttribute.getName() ) && !label.startsWith( assayAttribute.getName() )) {
					addInfoMessage("Column label '" + label + "' has been assigned to assay attribute '" + assayAttribute.getName() + "'.");
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
		
		// check if all required assay attributes have been found
		for (AssayAttribute assayAttribute : assayAttributes) {
			unfoundAssayAttributes.add( assayAttribute.getName() );
			
			if (assayAttribute.isRequired()) {
				addErrorMessage("Import of Assay Data failed: Mandatory assay attribute '" + assayAttribute.getName() + "' not found in header line.");
				return false;
			}
		}
		
		return true;
	}

	private void parseAssayDataLine(String line) {
		
		String[] items = splitString(line, CONFIGURED_DELIMITER, QUOTE_CHARACTERS);
		
//		addInfoMessage("Number of items in line " + currentLineNumber + ": " + items.length);
		
		if (items.length != expectedColumnCount) {
			linesWithInvalidColumnCount.add( Integer.toString(currentLineNumber) );
			return;
		}
		
		String plateName = unquote(items[plateIdColumnIndex]).trim();
		String platePosition = unquote(items[wellIdColumnIndex]).trim();
		
		if (invalidPlateNames.contains(plateName)) {
			log.debug("Plate name already found to be invalid: " + plateName);
			return;
		}
		
		if (!plateNamePlateMap.containsKey(plateName)) {
			invalidPlateNames.add(plateName);
			return;
		}
		
		Plate plate = plateNamePlateMap.get(plateName);
		
		validPlateNames.add(plateName);
		
		int rowIndex = AssayDataUtil.getRowIndex(platePosition);
		int columnIndex = AssayDataUtil.getColumnIndex(platePosition);
		
		if (!WELL_ROLE_VALUE.equals( plate.getRole(rowIndex, columnIndex) )) {
			addInfoMessage("Found well with role " + plate.getRole(rowIndex, columnIndex) + " at row " + rowIndex + ", column " + columnIndex + " (" + platePosition + ").");
			return;
		}
		
		Long identifier = plate.getWell(rowIndex, columnIndex);
		
		if (identifier == null) {
			addInfoMessage("Found empty well at row " + rowIndex + ", column " + columnIndex + " in line " + currentLineNumber + ".");
			return;
		}
		
		Isolate isolate = isolateInformationProvider.retrieveIsolate(identifier);
		
		if (isolate == null) {
			addWarningMessage("Antibody Clone not available for identifier '" + identifier + "' at row " + rowIndex + ", column " + columnIndex + " (" + platePosition + ") in line " + currentLineNumber + ".");
			return;
		}
		
		String isolateName = isolate.getQualifiedId();
		
		List<AssayValue> newAssayValues = new ArrayList<AssayValue>();
		
		boolean foundRequiredValues = true;
		for (Integer index : indexAssayAttributeMap.keySet()) {
			String valueString = unquote(items[ index.intValue() ]).trim();
			
			AssayAttribute assayAttribute = indexAssayAttributeMap.get( index.intValue() );
			
			if (valueString.length() == 0) {
				if (assayAttribute.isRequired()) {
					foundRequiredValues = false;
					
					addWarningMessage("Empty string was found for required assay attribute '" +
							assayAttribute.getName() + "' in line " + currentLineNumber + ".");
				}
			}
			else {
				// check for duplicates
				String isolateAttributeName = isolateName + "////" + assayAttribute.getName();
				if (knownIsolateAttributeNames.contains(isolateAttributeName)) {
					duplicateIsolateNames.add(isolateName);
					return;
				}
				
				knownIsolateAttributeNames.add(isolateAttributeName);
				
				AssayValue assayValue = null;
				boolean addedWarningMessage = false;
				try {
					assayValue = generateAssayValue(isolate, assayAttribute, valueString);
				}
				catch (NumericValueTooLargeException e) {
					addWarningMessage("Numeric value exceeds the absolute value limit of " + e.getAbsoluteValueLimit() + " for assay attribute '" +
							assayAttribute.getName() + "' in line " + currentLineNumber + ": " + valueString);
					addedWarningMessage = true;
				}
				catch (StringValueTooLongException e) {
					addWarningMessage("String value exceeds the size limit of " + e.getSizeLimit() + " for assay attribute '" +
							assayAttribute.getName() + "' in line " + currentLineNumber + ": " + valueString);
					addedWarningMessage = true;
				}
				catch (UnknownCvValueException e) {
					addWarningMessage("CV value is not part of the controlled vocabulary for assay attribute '" +
							assayAttribute.getName() + "' in line " + currentLineNumber + ": " + valueString);
					addedWarningMessage = true;
				}
				catch (MismatchedDateFormatException e) {
					addWarningMessage("Could not parse the date '" + valueString + "' for assay attribute '" +
							assayAttribute.getName() + "' in line " + currentLineNumber + ". The required date format is '" + e.getDateFormatPattern() + "'.");
					addedWarningMessage = true;
				}
				
				if (assayValue != null) {
					newAssayValues.add(assayValue);
				}
				else {
					if (assayAttribute.isRequired()) {
						foundRequiredValues = false;
					}
					
					if (!addedWarningMessage) {
						addWarningMessage("Unknown string was found for assay attribute '" +
								assayAttribute.getName() + "' in line " + currentLineNumber + ": " + valueString);
					}
				}
			}
		}
		
		if (foundRequiredValues) {
			assayValues.addAll(newAssayValues);
		}
		else {
			linesMissingRequiredValues.add( Integer.toString(currentLineNumber) );
		}
	}
	
	@Override
	protected AssayAttribute getAssayAttribute(List<AssayAttribute> assayAttributes, String columnName) {
		if ((assayAttributes == null) || (columnName == null)) {
			log.warn("Failed to retrieve matching assay attribute: Empty reference to assay or column name.");
			return null;
		}
		
		// try to find a mapping between column name and label
		String label;
		if (columnNameLabelMap.containsKey(columnName)) {
			label = columnNameLabelMap.get(columnName);
		}
		else {
			label = columnName;
		}
		
		return super.getAssayAttribute(assayAttributes, label);
	}
	
	private String[] splitString(String string, char delimiter, String quoteCharacters) {
		log.debug("splitString");
		
		List<String> list = new ArrayList<String>();
		
		int pos;
		while (-1 != (pos = findNextDelimiterPosition(string, delimiter, quoteCharacters))) {
			list.add( string.substring(0, pos) );
			string = (string.length() > pos) ? string.substring(pos + 1) : "";
		}
		
		list.add(string);
		
		String[] answer = new String[ list.size() ];
		for (int i = 0; i < list.size(); i++) {
			answer[i] = list.get(i);
		}
		
		log.debug("answer: " + Arrays.toString(answer) );
		
		return answer;
	}
	
	private static int findNextDelimiterPosition(String string, char delimiter, String quoteCharacters) {
		int fromIndex = 0;
		
		for (int i = 0; i < quoteCharacters.length(); i++) {
			char quote = quoteCharacters.charAt(i);
			if (quote == string.charAt(0)) {
				int closeQuotePos = string.indexOf(quote, 1);
				if (closeQuotePos > -1) {
					fromIndex = closeQuotePos;
				}
				break;
			}
		}
		
		return string.indexOf(delimiter, fromIndex);
	}
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		super.setConfiguration(configuration);
		
		// hard code
		CONFIGURED_DELIMITER = ',';
	}
	
}
