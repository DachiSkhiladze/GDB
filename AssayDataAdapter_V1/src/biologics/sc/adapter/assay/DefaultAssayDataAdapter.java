package biologics.sc.adapter.assay;

import genedata.bx.adapter.assay.AssayAttribute;
import genedata.bx.adapter.assay.AssayValue;
import genedata.bx.adapter.assay.AssayValueFactory;
import genedata.bx.adapter.assay.Isolate;
import genedata.bx.adapter.assay.MismatchedDateFormatException;
import genedata.bx.adapter.assay.NumericValueTooLargeException;
import genedata.bx.adapter.assay.StringValueTooLongException;
import genedata.bx.adapter.assay.UnknownCvValueException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The generic implementation of the Assay Data Adapter interface.
 * Imports Assay Values from tab-separated, column-based text files
 * 
 * @deprecated use V2 AssayData API instead
 */
@Deprecated
public class DefaultAssayDataAdapter extends AbstractAssayDataAdapter {
	
	private final static String ISOLATE_PREFIX = "Antibody Clone";
	private final static String NAME_SUFFIX = "Name";
	private final static String ID_SUFFIX = "ID";
	
	private final List<AssayValue> assayValues = new ArrayList<AssayValue>();
	private int currentLineNumber;
	private boolean foundHeaderLineFlag;
	private int isolateNameColumnIndex;
	private final Map<Integer, AssayAttribute> indexAssayAttributeMap = new HashMap<Integer, AssayAttribute>();

	private int expectedColumnCount;
	private final List<String> matchedColumnLabels = new ArrayList<String>();
	private final List<String> unmatchedColumnLabels = new ArrayList<String>();
	private final List<String> multipleColumnLabels = new ArrayList<String>();
	private final List<String> unfoundAssayAttributes = new ArrayList<String>();
	private final List<String> linesWithInvalidColumnCount = new ArrayList<String>();
	private final List<String> linesMissingRequiredValues = new ArrayList<String>();
	private final List<String> invalidIsolateNames = new ArrayList<String>();
	private final Set<String> knownIsolateAttributeNames = new HashSet<String>();
	private final Set<String> duplicateIsolateNames = new HashSet<String>();

	/* (non-Javadoc)
	 * @see biologics.sc.adapter.assay.AbstractAssayDataAdapter#requiresInputStream()
	 */
	@Override
	public boolean requiresInputStream() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see biologics.sc.adapter.assay.AbstractAssayDataAdapter#requiresPlateSet()
	 */
	@Override
	public boolean requiresPlateSet() {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see biologics.sc.adapter.assay.AbstractAssayDataAdapter#perform(genedata.bx.adapter.assay.AssayValueFactory, java.util.List)
	 */
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
			bufferedReader = new BufferedReader( new InputStreamReader(inputStream, getCharsetName() ) );
			
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
		isolateNameColumnIndex = -1;
		foundHeaderLineFlag = false;
		
		matchedColumnLabels.clear();
		unmatchedColumnLabels.clear();
		multipleColumnLabels.clear();
		unfoundAssayAttributes.clear();
		linesWithInvalidColumnCount.clear();
		linesMissingRequiredValues.clear();
		invalidIsolateNames.clear();
		knownIsolateAttributeNames.clear();
		duplicateIsolateNames.clear();
		
		if (inputStream == null) {
			addErrorMessage("Failed to import the Assay Data from file: The required input stream is not available.");
			return false;
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
	private void reportAfterParsing()
	{
		if (!foundHeaderLineFlag && isolateNameColumnIndex == -1) {
			addErrorMessage("Assay Data has not been imported: Could not find a valid header line in file.");
			addInfoMessage("Note that a column with Antibody Clone identifiers or names is required. It must be labeled '" +
					ISOLATE_PREFIX + " " + ID_SUFFIX + "' or '" +
					ISOLATE_PREFIX + " " + NAME_SUFFIX + "'.");
		}
		
		if (linesWithInvalidColumnCount.size() > 0) {
			addWarningMessage("Wrong number of columns was found in the lines: "
					+ joinStrings(linesWithInvalidColumnCount, ", "));
		}
		
		if (linesMissingRequiredValues.size() > 0) {
			addErrorMessage("Required Assay Values were missing in the lines: "
					+ joinStrings(linesMissingRequiredValues, ", "));
		}
		
		if (linesWithInvalidColumnCount.size() + linesMissingRequiredValues.size() > 0) {
			addInfoMessage("Please note that the Assay Data from these lines has not been imported.");
		}
		
		if (invalidIsolateNames.size() > 0) {
			addWarningMessage("Unrecognized Antibody Clone names were found (in line): "
					+ joinStrings(invalidIsolateNames, ", "));
			addInfoMessage("Please note that the Antibody Clones must be a part of the Campaign in order to be recognized.");
		}
		
		if (duplicateIsolateNames.size() > 0) {
			List<String> list = new ArrayList<String>(duplicateIsolateNames);
			Collections.sort(list);
			
			addWarningMessage("Duplicate Assay Values were found for the following Antibody Clones: "
					+ joinStrings(list, ", "));
			addInfoMessage("Please note that the replicated Assay Values have not been imported. Please check your input file to avoid loss of data.");
		}
	}

	private void parseLine(String line)
	{
		log.debug("parseLine");
		
		if (line == null) {
			return;
		}
		
		if (!foundHeaderLineFlag) {
			if (isolateNameColumnIndex == -1) {
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

	private boolean parseHeaderLine(String line)
	{
		log.debug("parseHeaderLine");
		
		// make sure that trailing empty items are included by setting the limit to a negative value
		String[] items = split(line, -1);
		expectedColumnCount = items.length;
		
		for (int index = 0; index < items.length; index++) {
			String label = unquote(items[index]).trim();
			
			if (matchesIsolateNamePattern(label)) {
				if (isolateNameColumnIndex > -1) {
					addWarningMessage("Already found a column with Antibody Clone identifiers or names. The column '" + label + "' is therefore ignored.");
				}
				else {
					if (!matchedColumnLabels.contains(label)) {
						isolateNameColumnIndex = index;
						matchedColumnLabels.add(label);
					}
					else {
						multipleColumnLabels.add(label);
					}
				}
			}
		}
		
		if (isolateNameColumnIndex == -1) {
			log.debug("Not recognized as header line: " + line);
			return false;
		}
		
		List<AssayAttribute> assayAttributes = new ArrayList<AssayAttribute>();
		assayAttributes.addAll( getAssayAttributes() );
		
		indexAssayAttributeMap.clear();
		
		for (int index = 0; index < items.length; index++) {
			String label = unquote(items[index]).trim();
			
			if (matchesIsolateNamePattern(label)) {
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

	private void parseAssayDataLine(String line)
	{
		// make sure that trailing empty items are included by setting the limit to a negative value
		String[] items = split(line, -1);
		
//		addInfoMessage("Number of items in line " + currentLineNumber + ": " + items.length);
		
		if (items.length != expectedColumnCount) {
			linesWithInvalidColumnCount.add( Integer.toString(currentLineNumber) );
			return;
		}
		
		String isolateName = unquote(items[isolateNameColumnIndex]).trim();
		Isolate isolate = getIsolate(isolateName);
		
		if (isolate == null) {
			invalidIsolateNames.add(isolateName + " (line "  + currentLineNumber + ")");
			return;
		}
		
		List<AssayValue> newAssayValues = new ArrayList<AssayValue>();
		
		boolean foundRequiredValues = true;
		for (Integer index : indexAssayAttributeMap.keySet()) {
			String valueString = unquote(items[ index.intValue() ]).trim();
			
			AssayAttribute assayAttribute = indexAssayAttributeMap.get( index.intValue() );
			
			if (valueString.length() == 0) {
				if (assayAttribute.isRequired()) {
					foundRequiredValues = false;
					
					addErrorMessage("Empty string was found for required assay attribute '" +
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

	/**
	 * @param label The column header label.
	 * @return {@code true} if the label matches the 'isolate name' pattern.
	 */
	private static boolean matchesIsolateNamePattern(String label)
	{
		return (label != null)
				&& label.toUpperCase().startsWith( ISOLATE_PREFIX.toUpperCase() )
				&& (label.toUpperCase().endsWith( ID_SUFFIX.toUpperCase() )
						|| label.toUpperCase().endsWith( NAME_SUFFIX.toUpperCase() ));
	}
}
