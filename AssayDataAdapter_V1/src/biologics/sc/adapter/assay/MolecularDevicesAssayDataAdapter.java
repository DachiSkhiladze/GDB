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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The implementation of the Assay Data Adapter interface for
 * the import of Assay Values from plate-based, tab-separated files.
 * 
 * With adaptions for the 'Molecular Devices' format.
 * 
 * @deprecated use V2 AssayData API instead
 */
@Deprecated
public class MolecularDevicesAssayDataAdapter extends AbstractAssayDataAdapter {

	private final List<AssayValue> assayValues = new ArrayList<AssayValue>();
	private int currentLineNumber;
	private final Map<String,Plate> plateNamePlateMap = new HashMap<String,Plate>();
	private final Set<String> invalidPlateNames = new HashSet<String>();
	private final Set<String> validPlateNames = new HashSet<String>();
	private final Set<String> invalidAssayAttributeLabels = new HashSet<String>();
	private final Set<String> validAssayAttributeLabels = new HashSet<String>();
	private final Set<String> knownAttributePlateNames = new HashSet<String>();
	private final List<String> skippedDataLines = new ArrayList<String>();

	private Plate currentPlate;
	private AssayAttribute currentAssayAttribute;
	private int currentRow;

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
				else if ((tokenCount(trimmedLine) <= 20) && (currentPlate == null)) {
					if (!isDataLine(line)) {
						addCommentLine(trimmedLine);
					}
					else {
						skippedDataLines.add( Integer.toString(currentLineNumber) );
					}
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

	private boolean prepareForParsing() {
		clear();
		assayValues.clear();
		plateNamePlateMap.clear();
		invalidPlateNames.clear();
		validPlateNames.clear();
		invalidAssayAttributeLabels.clear();
		validAssayAttributeLabels.clear();
		knownAttributePlateNames.clear();
		skippedDataLines.clear();
		
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
		
		return true;
	}

	/**
	 * Reports summary information after the parsing has been done.
	 */
	private void reportAfterParsing() {
		if (validAssayAttributeLabels.isEmpty()) {
			addErrorMessage("No valid assay attributes have been found in the file.");
		}
		else {
			List<String> names = new ArrayList<String>(validAssayAttributeLabels);
			Collections.sort(names);
			addInfoMessage("The following assay attribute labels have been recognized: " + joinStrings(names, ", ") );
		}
		
		if (invalidAssayAttributeLabels.size() > 0) {
			List<String> names = new ArrayList<String>(invalidAssayAttributeLabels);
			Collections.sort(names);
			addWarningMessage("Assay attribute labels that were not assigned to an assay attribute: " + joinStrings(names, ", ") );
		}
		
		if (validAssayAttributeLabels.isEmpty()) {
			return;
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
		
		if (skippedDataLines.size() > 0
				&& (invalidPlateNames.size() * numberOfRows != skippedDataLines.size())) {
			addWarningMessage("The following lines of Assay Data have not been imported: " + joinStrings(skippedDataLines, ", ") );
			addInfoMessage("Please check the format of the imported file.");
		}
		
		if (invalidPlateNames.size() + validPlateNames.size() == 0) {
			addInfoMessage("Please make sure that the imported file has the required plate-based format. " +
					"The header lines of plate sections must contain only the plate name in order to be recognized.");
		}
	}

	private void parseLine(String line) {
		log.debug("parseLine");
		
		if (line == null) {
			return;
		}
		
		if ((currentPlate == null) || (currentAssayAttribute == null)) {
			if (tokenCount(line) >= 20) {
				parseHeaderLine(line);
			}
		}
		else {
			parseAssayDataLine(line);
		}
	}

	/**
	 * @param line The line of interest.
	 * @return The number of tokens in the trimmed line.
	 */
	protected int tokenCount(String line) {
		if (line == null) {
			return 0;
		}
		String[] items = split(line.trim());
		return items.length;
	}

	private void parseHeaderLine(String line) {
		String[] items = split(line);
		currentRow = 0;
		
		if (items.length < 2) {
			addWarningMessage("Could not parse the Plate header line " + currentLineNumber + ": The Plate header line must contain the Plate name.");
			return;
		}
		
		if (! "Plate:".equals(items[0])) {
			addWarningMessage("Could not parse the Plate header line " + currentLineNumber + ": The Plate header line must start with 'Plate:'.");
			return;
		}
		
		// add the header line to the comments
		addCommentLine(line);
		
		String assayAttributeLabel = "S";
		AssayAttribute assayAttribute = getAssayAttribute( getAssayAttributes(), assayAttributeLabel);
		
		if (assayAttribute == null) {
			invalidAssayAttributeLabels.add(assayAttributeLabel);
			return;
		}
		
		// report all assay attribute assignments that are not exact matches of the label
		if (!assayAttributeLabel.equalsIgnoreCase( assayAttribute.getName() )) {
			addInfoMessage("Label '" + assayAttributeLabel + "' has been assigned to assay attribute '" + assayAttribute.getName() + "' in line " + currentLineNumber + ".");
		}
		
		validAssayAttributeLabels.add(assayAttributeLabel);
		
		String plateName = items[1];
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
		
		// check for duplicates
		String attributePlateName = assayAttribute.getName() + "////" + plateName;
		if (knownAttributePlateNames.contains(attributePlateName)) {
			addWarningMessage("Duplicate Assay Values were found for the Plate '" + plateName + "' and the Assay Attribute '" + assayAttribute.getName() + "' in line " + currentLineNumber + ".");
			addInfoMessage("Please note that the replicated Assay Values have not been imported. Please check your input file to avoid loss of data.");
			return;
		}
		
		knownAttributePlateNames.add(attributePlateName);
				
		currentAssayAttribute = assayAttribute;
		currentPlate = plate;
	}

	private void parseAssayDataLine(String line) {
		log.debug("parseAssayDataLine");
		
		String[] valueStrings = split(line);
		
		if (! "".equals(valueStrings[1])) {
			addCommentLine(valueStrings[1]);
		}
		
		if (valueStrings[1].startsWith("Temperature")) {
			return;
		}
		
		currentRow++;
		log.debug("row: " + currentRow + ", length: " + valueStrings.length);
		
		if (valueStrings.length != numberOfColumns + 2) {
			addWarningMessage("Found wrong number of columns (" + valueStrings.length + " instead of " + numberOfColumns + ") in line " + currentLineNumber + ".");
		}
		
		int rowIndex = currentRow;
		int columnIndex = 0;
		for (int i = 2; i < valueStrings.length; i++) {
			String valueString = valueStrings[i];
			columnIndex++;
			
			if (columnIndex > numberOfColumns) {
				break;
			}
			
			if (!WELL_ROLE_VALUE.equals( currentPlate.getRole(rowIndex, columnIndex) )) {
//				addInfoMessage("Found well with role " + currentPlate.getRole(rowIndex, columnIndex) + " at row " + rowIndex + ", column " + columnIndex + ".");
				continue;
			}
			
			Long identifier = currentPlate.getWell(rowIndex, columnIndex);
			
			if (identifier == null) {
				addInfoMessage("Found empty well at row " + rowIndex + ", column " + columnIndex + " in line " + currentLineNumber + ".");
				continue;
			}
			
			Isolate isolate = isolateInformationProvider.retrieveIsolate(identifier);
			
			if (isolate == null) {
				addWarningMessage("Antibody Clone not available for identifier '" + identifier + "' at row " + rowIndex + ", column " + columnIndex + " in line " + currentLineNumber + ".");
				continue;
			}
			
			AssayValue assayValue = null;
			boolean addedWarningMessage = false;
			try {
				assayValue = generateAssayValue(isolate, currentAssayAttribute, valueString);
			}
			catch (NumericValueTooLargeException e) {
				addWarningMessage("Numeric value exceeds the absolute value limit of " + e.getAbsoluteValueLimit() + " for assay attribute '" +
						currentAssayAttribute.getName() + "' at row " + rowIndex + ", column " + columnIndex + " in line " + currentLineNumber + ": " + valueString);
				addedWarningMessage = true;
			}
			catch (StringValueTooLongException e) {
				addWarningMessage("String value exceeds the size limit of " + e.getSizeLimit() + " for assay attribute '" +
						currentAssayAttribute.getName() + "' at row " + rowIndex + ", column " + columnIndex + " in line " + currentLineNumber + ": " + valueString);
				addedWarningMessage = true;
			}
			catch (UnknownCvValueException e) {
				addWarningMessage("CV value is not part of the controlled vocabulary for assay attribute '" +
						currentAssayAttribute.getName() + "' at row " + rowIndex + ", column " + columnIndex + " in line " + currentLineNumber + ": " + valueString);
				addedWarningMessage = true;
			}
			catch (MismatchedDateFormatException e) {
				addWarningMessage("Could not parse the date '" + valueString + "' for assay attribute '" +
						currentAssayAttribute.getName() + "' at row " + rowIndex + ", column " + columnIndex + " in line " + currentLineNumber + ". The required date format is '" + e.getDateFormatPattern() + "'.");
				addedWarningMessage = true;
			}
			
			if (assayValue != null) {
				assayValues.add(assayValue);
			}
			else if (!addedWarningMessage) {
				addWarningMessage("Assay value '" + valueString + "' not recognized at row " + rowIndex + ", column " + columnIndex + " in line " + currentLineNumber + ".");
			}
		}
		
		if (currentRow == numberOfRows) {
			currentPlate = null;
			currentAssayAttribute = null;
		}
	}
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		super.setConfiguration(configuration);
		
		// hard code
		CONFIGURED_DELIMITER = '\t';
	}
	
}
