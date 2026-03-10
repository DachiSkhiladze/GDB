package biologics.adapter.labresult;

/**
 * The implementation of the LabResultDataAdapter interface for
 * the import of Laboratory Results, tab-separated or comma-separated files.
 * 
 * Expected input format:
 *
 * <pre>
 * Header:
 * <Parent Entity ID columns><Assay Attributes columns>
 * ...
 * </pre>
 *
 * Example:
 *
 *
 * <pre>
 * Protein Expression Batch ID	Cell Line Batch ID	S	B	S/B	IC50	S0	S/S0
 * PEB-9		5	2	1			
 * PEB-13		16	3		6		
 * 	CLIB-1	3	6	9	12		
 *
 * ...
 * </pre>
 *
 */

import genedata.bx.adapter.Identifiable;
import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.assay.v2.AssayAttribute;
import genedata.bx.adapter.assay.v2.AssayAttributeException;
import genedata.bx.adapter.assay.v2.AssayDataUtil;
import genedata.bx.adapter.assay.v2.AssayValueException;
import genedata.bx.adapter.entity.InformationProvider;
import genedata.bx.adapter.entity.LaboratoryResult;
import genedata.bx.adapter.entity.Sample;
import genedata.bx.adapter.entity.SampleInformationProvider;
import genedata.bx.adapter.labresult.GroupingCriterionException;
import genedata.bx.adapter.labresult.LabResultDataAdapter;
import genedata.bx.adapter.labresult.LabResultDataCallback;
import genedata.bx.adapter.labresult.LabResultDataOptions;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

public class GenericLabResultDataAdapter implements LabResultDataAdapter, java.io.Serializable {
	private static final long serialVersionUID = 1L;

    private Logger log = Logger.getLogger(this.getClass());

	private static final String DELIMITER_PARAMETER = "table_export_import_format";
	private static final String FORMAT_DATE_PARAMETER = "format_pattern_date_with_time";
	private static final String GROUPING_CRITERION_COLUMN_NAME = "Grouping Criterion";
	
	// input
	private LabResultDataCallback biologics;
	private Reporter reporter;
	private static String DELIMITER = "\t";
	private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss", new Locale("en", "IE"));
	
	// process
	private int currentRow;
	private int noOfColumns;
	private Map<Integer, WrappedInformationProvider> entityHeadersIPMap = new HashMap<>();
	private Map<Integer, AssayAttribute> attributeHeadersMap = new HashMap<>();
	private int groupingCriterionColumnIndex = -1;
	
	// output
	private List<LaboratoryResult> labResults = new ArrayList<LaboratoryResult>();
	
	interface WrappedInformationProvider {
        Identifiable retrieveEntity(String identifier);
        String getSingularEntityLabel();
        String getColumnName();
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
	};
	
	class WrapperForBarcode implements WrappedInformationProvider {
        final SampleInformationProvider ip;
        // constructor
        WrapperForBarcode(SampleInformationProvider ip) {
        	this.ip = ip;
        }
        @Override
        public Identifiable retrieveEntity(String identifier) {
                return ip.retrieveByBarcode(identifier);
        }
        @Override
        public String getSingularEntityLabel() {
                return ip.getSingularEntityLabel();
        }
        @Override
        public String getColumnName() {
                return "Barcode";
        }
	}
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// setting column delimiter based on the system parameter
		if (configuration.get(DELIMITER_PARAMETER) != null) {
			if ("csv".equals(configuration.get(DELIMITER_PARAMETER))) {
				DELIMITER = ",";
			}
		}
		
		// setting date/time format based on the system parameter
		if (configuration.get(FORMAT_DATE_PARAMETER) != null) {
			DATE_FORMAT = new SimpleDateFormat(configuration.get(FORMAT_DATE_PARAMETER));
		}
	}

	@Override
	public void options(LabResultDataOptions options) {
		// indicates to Biologics that it requires input file
		options.setRequireInputStream(true);
		// indicates to Biologics that it doesn't explicitly assigns Antigens
		options.setAdapterAssignsAntigen(false);
	}

	@Override
	public List<LaboratoryResult> perform(LabResultDataCallback callback) {
		biologics = callback;
		reporter = biologics.getReporter();
		
		if (validateInput()) {
			processInput();
		}
		
		return labResults;
	}
	
	/**
	 * Validates input and initializes collections.
	 * @return true if initialization was successful.
	 */
	private boolean validateInput() {	
		labResults.clear();
		entityHeadersIPMap.clear();
		attributeHeadersMap.clear();
		
		if (biologics.getInputStream() == null) {
			reporter.error("Failed to import Laboratory Results data from file: The required input stream is not available.");
			return false;
		}
		return true;
	}

	/**
	 * Processes input and handles exceptions when reading and parsing input.
	 */
	private void processInput() {
		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader(new InputStreamReader(biologics.getInputStream()));
			
			String line = null;
			
			// parse header
			line = bufferedReader.readLine();
			boolean isValidHeader = parseHeaderLine(line);
			
			// parse content
			if (isValidHeader) {
				while(null != (line = bufferedReader.readLine())) {
					currentRow++;
					parseInputLine(line);
				}
			}
		} catch (GroupingCriterionException e) {
			reporter.error("Invalid column Grouping Criterion.");
			log.error("processInput", e);
		} catch  (AssayValueException e) {
			reporter.error(e.getMessage());
			log.error("processInput", e);
		} catch (Exception e) {
			reporter.error("Failed to parse the contents of the input stream.");
			log.error("processInput", e);
		} finally {
			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				}
				catch (Exception e) {
					reporter.error("Failed to close input.");
				}
			}
		}
	}
	
	/**
	 * Parses and validates header line. Maps parent entities and assay attributes to the column indexes.
	 * @param line
	 * @throws AssayAttributeException
	 * @return true if parsing was successful.
	 */
	private boolean parseHeaderLine(String line) throws AssayAttributeException {
		boolean result = true;
		String[] items = line.split(DELIMITER, -1);
		currentRow = 0;
		noOfColumns = items.length;
		
		Map<String, AssayAttribute> labelsToAttributes = 
				biologics.getAssayAttributes().stream()
					.collect(Collectors.toMap(AssayAttribute::getName, c -> c));
	
		for (int i = 0; i < items.length; i++) {
			AssayAttribute headerLabelAttribute = AssayDataUtil.findByMatchOnHeaderLabelPattern(items[i], biologics.getAssayAttributes());
			if(GROUPING_CRITERION_COLUMN_NAME.equals(items[i])) {
				groupingCriterionColumnIndex = i;
			} else if (labelsToAttributes.containsKey(items[i]) ) {
				attributeHeadersMap.put(i, labelsToAttributes.get(items[i]));
				result &= checkForDoubleHeaders(items, labelsToAttributes.get(items[i]));
			} else if (headerLabelAttribute != null) {
				attributeHeadersMap.put(i, headerLabelAttribute);
				result &= checkForDoubleHeaders(items, headerLabelAttribute);
			}
			
			else if (! parseEntityHeader(items[i], i)) {
				reporter.warn("Column '#0' will be ignored", items[i]);
			}
		}
		
		if (entityHeadersIPMap.isEmpty()) {
			reporter.error("No parental entities specified in the input file header.");
			result &= false;
		}
		
		if (attributeHeadersMap.isEmpty()) {
			reporter.error("No assay attributes specified in the input file header.");
			result &= false;
		}
		
		return result;
	}

	private boolean checkForDoubleHeaders(String[] items, AssayAttribute attribute) {
		List<String> existingColumns = attributeHeadersMap.entrySet().stream()
				.filter(entry -> attribute.equals(entry.getValue()))
				.map(entry -> items[entry.getKey()])
				.collect(Collectors.toList());
		
		if (existingColumns.size() > 1) {
			String columnListString = existingColumns.stream().collect(Collectors.joining(", "));
			reporter.error("There are multiple columns existing for attribute #0: #1.", attribute.getName(), columnListString);
			return false;
		}
		return true;
	}
	
	/**
	 * Maps parent entity columns to the entity information providers.
	 */
	private boolean parseEntityHeader(String entityHeader, int index) {
		if (entityHeader.equals(biologics.getVectorBatchInformationProvider().getSingularEntityLabel() + " ID")) {
			entityHeadersIPMap.put(index, new WrapperForQualifiedId(biologics.getVectorBatchInformationProvider()));
		} else if (entityHeader.equals(biologics.getHostCellLineBatchInformationProvider().getSingularEntityLabel() + " ID")) {
			entityHeadersIPMap.put(index, new WrapperForQualifiedId(biologics.getHostCellLineBatchInformationProvider()));
		} else if (entityHeader.equals(biologics.getCellLineBatchInformationProvider().getSingularEntityLabel() + " ID")) {
			entityHeadersIPMap.put(index, new WrapperForQualifiedId(biologics.getCellLineBatchInformationProvider()));
		} else if (entityHeader.equals(biologics.getProteinExpressionBatchInformationProvider().getSingularEntityLabel() + " ID")) {
			entityHeadersIPMap.put(index, new WrapperForQualifiedId(biologics.getProteinExpressionBatchInformationProvider()));
		} else if (entityHeader.equals(biologics.getProteinPurificationBatchInformationProvider().getSingularEntityLabel() + " ID")) {
			entityHeadersIPMap.put(index, new WrapperForQualifiedId(biologics.getProteinPurificationBatchInformationProvider()));
		} else if (entityHeader.equals(biologics.getSampleInformationProvider().getSingularEntityLabel() + " ID")) {
			entityHeadersIPMap.put(index, new WrapperForQualifiedId(biologics.getSampleInformationProvider()));
		} else if (entityHeader.equals(biologics.getSampleInformationProvider().getSingularEntityLabel() + " Barcode")) {
			entityHeadersIPMap.put(index, new WrapperForBarcode(biologics.getSampleInformationProvider()));
		} else {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Parses and validates input line. Sets and validates parental entity as well as the assay attributes.
	 * Creates laboratory results and assigns the values to the assay attributes.
	 * @param line
	 * @throws AssayValueException
	 * @throws GroupingCriterionException 
	 * @return true if parsing was successful.
	 */
	private boolean parseInputLine(String line) throws AssayValueException, GroupingCriterionException {
		String[] items = line.split(DELIMITER, -1);
		Identifiable entity = null;
		
		// validate number of columns
		if (items.length != noOfColumns) {
			reporter.error("#0: Invalid number of columns.", getLineContext());
			return false;
		}
		
		// setting parent entity
		entity = getParentEntity(items);
		
		// setting assay attributes values
		LaboratoryResult labResult = null;
		if (entity != null) {
			
			String groupingCriterion = null;
			if (groupingCriterionColumnIndex > 0) {
				groupingCriterion = items[groupingCriterionColumnIndex];
			}
			labResult = biologics.getLabResultFactory().createLaboratoryResult(entity, groupingCriterion); // creating the lab result
			
			if (setAssayValues(items, labResult)) {
				labResults.add(labResult); // add valid lab result to the list to be returned
				return true;
			}
		}
		
		return false;
	}
	
	/** Retrieves the parent entity from the input line
	 * @param tokenized input line
	 * @return parent entity if found, otherwise null.
	 */
	private Identifiable getParentEntity(String[] items) {
	    Identifiable entity = null;
	    boolean isValid = true;
	    
	    for (int index : entityHeadersIPMap.keySet()) {
	        String item = items[index] == null ? "" : items[index].trim();
	        if (item.isEmpty()) {
	            continue;
	        }
	        
	        WrappedInformationProvider wrapper = entityHeadersIPMap.get(index);

	        // retrieve entity from the respective information provider
	        Identifiable subsequentEntity = wrapper.retrieveEntity(item);
	        if (subsequentEntity == null) {
	            reporter.error("#0: Cannot find #1 with #2 #3.", 
	                    getLineContext(), 
	                    wrapper.getSingularEntityLabel(),
	                    wrapper.getColumnName(), 
	                    item);
	            isValid = false;
	            continue;
	        }
	        
	        // validate consistency and choose one over the other
	        entity = validate(entity, subsequentEntity);
	        if (null == entity) {
	            isValid = false;
	            break;
	        }
	    }

	    if (isValid && entity == null) { // no parent entity set
	        reporter.error("#0: Parent entity not set.", getLineContext());
	    }

	    return entity;
	}
	
	private Sample validateSampleAgaistParentEntity(Identifiable entity, Sample sample) {
		Identifiable parent = sample.getParent();
		if (! entity.equals(parent) ) {
			reporter.error("#0: Sample #1 is not consistent with #2.", 
					getLineContext(), 
					sample.getQualifiedId(),
					entity.getQualifiedId());
			return null;	
		}
		return sample;
	}
	
	private Identifiable validate(Identifiable entity, Identifiable subsequentEntity) {
		if (entity == null) {
			return subsequentEntity;
		} else if (subsequentEntity == null) {
			return entity;
		}
		
		if (entity instanceof Sample) {
			Sample sample = (Sample) entity;
			// if both are sample compare them directly, not their parents
			if (subsequentEntity instanceof Sample) {
				Sample subsequentSample = (Sample) subsequentEntity;
				if (! sample.equals(subsequentSample)) {
					reporter.error("#0: Sample identified with ID is not the same identified with Barcode.", 
							getLineContext());
					return null;	
				}
			} else {
				return validateSampleAgaistParentEntity(subsequentEntity, sample);
			}
		} 
		else if (subsequentEntity instanceof Sample) {
			Sample sample = (Sample) subsequentEntity;
			return validateSampleAgaistParentEntity(entity, sample);
		} 
		// both are not sample so compare them
		else if (! entity.equals(subsequentEntity)) {
			reporter.error("#0: Cannot set multiple parents.", getLineContext());
			return null;	
		}
		return entity;
	}
	
	/**
	 * Validates and assigns the values to the assay attributes.
	 * @param tokenized input line
	 * @return true if all values are set correctly.
	 */
	private boolean setAssayValues(String[] items, LaboratoryResult labResult) throws AssayValueException {
		boolean result = true;
		
		for (int index : attributeHeadersMap.keySet()) {
			result &= setAssayValue(labResult, attributeHeadersMap.get(index), items[index]);
		}
		return result;
	}
	
	/**
	 * Validates and assigns the value to the assay attribute.
	 */
	private boolean setAssayValue(LaboratoryResult labResult, AssayAttribute att, String rawValue) throws AssayValueException{
		Object value = null;
		
		if (att.isRequired() && (rawValue == null || rawValue.isEmpty())) {
			reporter.error("#0: Mandatory #1 attribute is missing.", getLineContext(), att.getName());
			return false;
		}
		
		if (rawValue == null || rawValue.isEmpty()) {
			return true;
		}
		
		try {
			if (att.isNumericValueType()) {
				value = Double.parseDouble(rawValue);
			}

			if (att.isDateValueType()) {
				value = DATE_FORMAT.parse(rawValue);
			}
		} catch (Exception e) {
			reporter.error("#0: Error while parsing #1 attribute value '#2'.", getLineContext(), att.getName(), rawValue);
			return false;
		}
		
		if (att.isStringValueType()) {
			value = rawValue;
		}
		
		if (att.isReferenceToImageValueType()) {
			value = rawValue;
		}

		if (att.isCvValueType()) {
			value = rawValue;
		}
		
		biologics.getLabResultFactory().addValue(labResult, att, value); // assigning value to the assay attribute
		
		return true;
	}
	
	/**
	 * Returns the line context used in the messages.
	 */
	private String getLineContext() {
		return "Line " + currentRow;
	}
}

