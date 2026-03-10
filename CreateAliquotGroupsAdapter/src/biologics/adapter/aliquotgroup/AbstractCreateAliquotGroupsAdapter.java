package biologics.adapter.aliquotgroup;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.aliquotgroup.AliquotGroupRecord;
import genedata.bx.adapter.aliquotgroup.CreateAliquotGroupsAdapter;

import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * The abstract base class for implementations of the Create Aliquot Groups Adapter interface.
 * 
 * <div style="font-size:x-small">
 * Copyright Genedata AG. All Rights Reserved.
 * </div>
 */
abstract public class AbstractCreateAliquotGroupsAdapter implements CreateAliquotGroupsAdapter {
	
	protected Logger log = Logger.getLogger(this.getClass());
	
	enum Attribute {
		cellLineBatchId,
		cellLineBatchName,
		plateBarcode,
		plateWellAddress,
		aliquotGroupName,
		description,
		aliquotsInitialNumber,
		aliquotBarcodes,
		numberOfCellsPerAliquot,
		referenceSample,
		archived,
		freezingDate,
		freezerLocation,
		freezerRackNumber,
		rackBarcode,
		rackPosition,
	}
	
	/** The string which indicates the start of a comment line. */
	protected static final String COMMENT_LINE_INDICATOR = "#";
	
	/** Determines which character will be used to quote strings. */
	protected static final char QUOTE_STRING_CHARACTER = '\'';
	
	private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", new Locale("en", "IE"));
	private static SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", new Locale("en", "IE"));
	
	/** 
	 * The delimiter character as read from the configuration, maybe comma or
	 * tab. Will be available after {@link #setConfiguration(Map)} was called 
	 */
	protected char fieldDelimiter;
	
	protected int currentLineNumber;
	
	/**
	 * The callback object allowing to invoke operations on Genedata Biologics
	 */
	protected Callback biologics;
	
	/**
	 * Configuration as passed in in method setConfiguration()
	 */
	protected Map<String, String> configuration;
	
	/**
	 * The options object passed in in method options()
	 */
	protected Options options;
	
	/**
	 * The Reporter object. Identical to biologics.getReporter()
	 */
	protected Reporter reporter;
	
	private final List<Attribute> attributes = new ArrayList<>();
	
	private final List<AliquotGroupRecord> aliquotGroups = new ArrayList<>();
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		this.configuration = configuration;
		
		String delim = configuration.get("table_export_import_format");
		if (null == delim || 0 == delim.trim().length() || ! "CSV".equalsIgnoreCase(delim.trim())) {
			fieldDelimiter = '\t'; // also fall back 
		}
		else {
			fieldDelimiter = ',';
		}
		log.debug("Read '"+delim+"' from config: set '"+fieldDelimiter+"' as delimiter");
	}
	
	@Override
	public void options(Options options) {
		this.options = options;
	}
	
	/**
	 * Child classes need to override this method to implement the specifics of
	 * the respective adapter. When this method is called,
	 * all internal objects like `biologics` or `reporter` have been initialized.
	 */
	protected abstract void performInternal();
	
	@Override
	public List<AliquotGroupRecord> perform(Callback callback) {
		biologics = callback;
		
		initialize();
		performInternal();
		
		return aliquotGroups;
	}
	
	protected void initialize() {
		aliquotGroups.clear();
		attributes.clear();
		attributes.addAll( Arrays.asList(Attribute.values()) );
		
		reporter = biologics.getReporter();
	}
	
	/**
	 * Add generated Aliquot Group to the list of Aliquot Groups that will be returned to 
	 * Genedata Biologics when the adapter is finished. 
	 * @param aliquotGroup
	 */
	protected void addAliquotGroup(AliquotGroupRecord aliquotGroup) {
		aliquotGroups.add(aliquotGroup);
	}
	
	/**
	 * The charset name may be configured in the parameter settings
	 * with a adapter class specific key, like
	 * 'genedata_bx_bl_adapter_aliquotgroup_ColumnCreateAliquotGroupsAdapter_charset'
	 * 
	 * Example:
	 * insert into parameter (id, user_specific, key, value) values(
	 *   parameter_seq.nextval,
	 *   'N', 
	 *   'genedata_bx_bl_adapter_aliquotgroup_ColumnCreateAliquotGroupsAdapter_charset',
	 *   'MacRoman');
	 * 
	 * @return The charset name as configured in the parameter settings, or
	 *  the default charset name if the parameter setting is not available.
	 */
	protected String getCharsetName() {
		log.debug("getCharsetName");
		
		String key = getClass().getName().replace('.', '_') + "_charset";
		
		String name = null;
		if (null != configuration && configuration.containsKey(key)) {
			name = configuration.get(key);
			
			try {
				Charset.forName(name);
				log.debug("Using the configured charset with name: " + name);
			}
			catch (Exception e) {
				log.error("The configured charset with name '" + name + "' is not available: " + e.toString() );
				name = null;
			}
		}
		
		if (null == name) {
			name = System.getProperty("file.encoding");
			log.debug("Using the default charset with name: " + name);
		}
		
		return name;
	}
	
	/**
	 * Split String s using {@link #fieldDelimiter} into &lt;limit&gt; strings. 
	 * @param s
	 * @param limit
	 * 
	 * @return The given string split on the {@link #fieldDelimiter}
	 */
	protected String[] split(String s, int limit) {
		if (null == s) {
			return new String[] {""};
		}
		return s.split("["+fieldDelimiter+"]", limit);
	}
	
	protected void setAttributeValues(AliquotGroupRecord aliquotGroup, Map<Attribute, String> attributeValueMap) {
		
		if (attributeValueMap.containsKey(Attribute.aliquotGroupName)) {
			aliquotGroup.setAlias( attributeValueMap.get(Attribute.aliquotGroupName) );
		}
		if (attributeValueMap.containsKey(Attribute.description)) {
			aliquotGroup.setDescription( attributeValueMap.get(Attribute.description) );
		}
		if (attributeValueMap.containsKey(Attribute.aliquotsInitialNumber)) {
			aliquotGroup.setAliquotsInitialNumber( parseInteger(attributeValueMap.get(Attribute.aliquotsInitialNumber)) );
		}
		if (attributeValueMap.containsKey(Attribute.aliquotBarcodes)) {
			String[] barcodes = parseStringArray(attributeValueMap.get(Attribute.aliquotBarcodes));
			for (String barcode : barcodes) {
				aliquotGroup.addAliquotBarcode(barcode);
			}
		}
		if (attributeValueMap.containsKey(Attribute.numberOfCellsPerAliquot)) {
			aliquotGroup.setNumberOfCellsPerAliquot( parseDouble(attributeValueMap.get(Attribute.numberOfCellsPerAliquot)) );
		}
		if (attributeValueMap.containsKey(Attribute.referenceSample)) {
			aliquotGroup.setReferenceSample( parseBoolean(attributeValueMap.get(Attribute.referenceSample)) );
		}
		if (attributeValueMap.containsKey(Attribute.archived)) {
			aliquotGroup.setArchived( parseBoolean(attributeValueMap.get(Attribute.archived)) );
		}
		if (attributeValueMap.containsKey(Attribute.freezingDate)) {
			aliquotGroup.setFreezingDate( parseDate(attributeValueMap.get(Attribute.freezingDate)) );
		}
		if (attributeValueMap.containsKey(Attribute.freezerLocation)) {
			aliquotGroup.setFreezerLocation( attributeValueMap.get(Attribute.freezerLocation) );
		}
		if (attributeValueMap.containsKey(Attribute.freezerRackNumber)) {
			aliquotGroup.setFreezerRackNumber( attributeValueMap.get(Attribute.freezerRackNumber) );
		}
		if (attributeValueMap.containsKey(Attribute.rackBarcode)) {
			aliquotGroup.setRackBarcode( attributeValueMap.get(Attribute.rackBarcode) );
		}
		if (attributeValueMap.containsKey(Attribute.rackPosition)) {
			aliquotGroup.setRackPosition( attributeValueMap.get(Attribute.rackPosition) );
		}
	}
	
	private Integer parseInteger(String string) {
		if (isEmpty(string)) {
			return null;
		}
		Integer value = null;
		try {
			value = Integer.parseInt(string);
		}
		catch (NumberFormatException e) {
			reportErrorLineNo("Cannot parse integer value: " + string + ".");
		}
		return value;
	}
	
	private Double parseDouble(String string) {
		if (isEmpty(string)) {
			return null;
		}
		Double value = null;
		try {
			value = Double.parseDouble(string);
		}
		catch (NumberFormatException e) {
			reportErrorLineNo("Cannot parse double value: " + string + ".");
		}
		return value;
	}
	
	/**
	 * First tries to match 'date with time' format, then date-only format.
	 * @param string The string which will be parsed.
	 * @return The parsed date or {@code null}.
	 */
	private Date parseDate(String string) {
		if (isEmpty(string)) {
			return null;
		}
		Date date = null;
		try {
			date = DATE_TIME_FORMAT.parse(string);
		}
		catch (ParseException e) {
			// ignored
		}
		if (date == null) {
			try {
				date = DATE_FORMAT.parse(string);
			}
			catch (ParseException e) {
				reportErrorLineNo("Cannot parse date: " + string + ". "
						+ "Expected formats are '" + DATE_FORMAT.toPattern() + "' or '" + DATE_TIME_FORMAT.toPattern() + "'.");
			}
		}
		return date;
	}
	
	private Boolean parseBoolean(String string) {
		if (isEmpty(string)) {
			return null;
		}
		Boolean answer =
			"Y".equalsIgnoreCase(string) ? Boolean.TRUE :
			"N".equalsIgnoreCase(string) ? Boolean.FALSE : null;
		
		if (answer == null) {
			reportErrorLineNo("Cannot parse boolean value: " + string + ". "
					+ "Expected boolean values are 'Y' and 'N'.");
		}
		return answer;
	}
	
	private String[] parseStringArray(String string) {
		if (isEmpty(string)) {
			return new String[] {""};
		}
		return string.split("[, ]");
	}
	
	/**
	 * Report an error together with the input line number
	 * @param reason
	 */
	protected void reportErrorLineNo(String reason) {
		reporter.error( "Error reading line #0: #1", currentLineNumber, reason);
	}
	
	/**
	 * Provide standard message revealing position when parsingContext is not available
	 * @return
	 */
	protected String lineContext() {
		return String.format(" [line: %d]", currentLineNumber);
	}
	
	protected static boolean isEmpty(String s) {
		return null == s || 0 == s.trim().length();
	}
}
