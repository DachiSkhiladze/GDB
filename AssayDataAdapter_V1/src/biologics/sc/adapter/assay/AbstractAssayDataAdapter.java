package biologics.sc.adapter.assay;


import genedata.bx.adapter.MessageType;
import genedata.bx.adapter.ParameterRegistry;
import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.assay.AssayAttribute;
import genedata.bx.adapter.assay.AssayDataAdapter;
import genedata.bx.adapter.assay.AssayValue;
import genedata.bx.adapter.assay.AssayValueFactory;
import genedata.bx.adapter.assay.Isolate;
import genedata.bx.adapter.assay.IsolateInformationProvider;
import genedata.bx.adapter.assay.MismatchedDateFormatException;
import genedata.bx.adapter.assay.MissingValueIndicator;
import genedata.bx.adapter.assay.NumericValueTooLargeException;
import genedata.bx.adapter.assay.StringValueTooLongException;
import genedata.bx.adapter.assay.UnknownCvValueException;
import genedata.bx.adapter.plate.Plate;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * The abstract base class for implementations of the Assay Data Adapter interface.
 * 
 * <div style="font-size:x-small">
 * Copyright 2010-2011 Genedata AG. All Rights Reserved.
 * </div>
 * 
 * @deprecated use V2 AssayData API instead
 */
@Deprecated
abstract class AbstractAssayDataAdapter implements AssayDataAdapter {
	
	protected Logger log = Logger.getLogger(this.getClass());
	
	/** The string which indicates the start of a comment line. */
	protected final String COMMENT_LINE_INDICATOR = "#";
	
	/** The well role which indicates a value well (not a reserved or control well). */
	protected static final String WELL_ROLE_VALUE = "VALUE";
	
	/** 
	 * The delimiter character as read from the configuration, maybe comma or
	 * tab. Will be available after {@link #setConfiguration(Map)} was called 
	 */
	protected static char CONFIGURED_DELIMITER;
	
	/** Determines which character will be used to quote strings. */
	private static final char QUOTE_STRING_CHARACTER = '\'';
	
	protected Map<String, String> configuration;
	protected Reporter reporter;
	protected ParameterRegistry parameterRegistry;
	protected IsolateInformationProvider isolateInformationProvider;
	protected List<MissingValueIndicator> missingValueIndicators;
	protected InputStream inputStream;
	protected List<Plate> plates;
	protected int numberOfRows;
	protected int numberOfColumns;
	
	private Map<String,String> parameterValueMap;
	
	private AssayValueFactory assayValueFactory;
	private List<AssayAttribute> assayAttributes;
	
	private final List<String> commentLines = new ArrayList<String>();
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.GenericAdapter#setConfiguration(java.util.Map)
	 */
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		this.configuration = configuration;
		
		String delim = configuration.get("table_export_import_format");
		if (null == delim || 0 == delim.trim().length() || ! "CSV".equalsIgnoreCase(delim.trim())) {
			CONFIGURED_DELIMITER = '\t'; // also fall back 
		}
		else {
			CONFIGURED_DELIMITER = ',';
		}
		log.debug("Read '"+delim+"' from config: set '"+CONFIGURED_DELIMITER+"' as delimiter");
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#setReporter(genedata.bx.adapter.Reporter)
	 */
	@Override
	public void setReporter(Reporter reporter) {
		this.reporter = reporter;
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#setParameterRegistry(genedata.bx.adapter.ParameterRegistry)
	 */
	@Override
	public void setParameterRegistry(ParameterRegistry parameterRegistry) {
		this.parameterRegistry = parameterRegistry;
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#setIsolateInformationProvider(genedata.bx.adapter.assay.IsolateInformationProvider)
	 */
	@Override
	public void setIsolateInformationProvider(IsolateInformationProvider isolateInformationProvider) {
		this.isolateInformationProvider = isolateInformationProvider;
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#setMissingValueIndicators(java.util.List)
	 */
	@Override
	public void setMissingValueIndicators(List<MissingValueIndicator> missingValueIndicators) {
		this.missingValueIndicators = missingValueIndicators;
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#requiresInputStream()
	 */
	@Override
	public boolean requiresInputStream() {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#setInputStream(java.io.InputStream)
	 */
	@Override
	public void setInputStream(InputStream inputStream) {
		this.inputStream = inputStream;
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#requiresPlateSet()
	 */
	@Override
	public boolean requiresPlateSet() {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#setPlates(java.util.List)
	 */
	@Override
	public void setPlates(List<Plate> plates) {
		this.plates = plates;
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#setPlateDimensions(int, int)
	 */
	@Override
	public void setPlateDimensions(int numberOfRows, int numberOfColumns) {
		this.numberOfRows = numberOfRows;
		this.numberOfColumns = numberOfColumns;
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#initialize()
	 */
	@Override
	public void initialize() {
		// empty (override this method if initialization is required)
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#setParameterValues(java.util.Map)
	 */
	@Override
	public void setParameterValues(Map<String,String> parameterValueMap) {
		this.parameterValueMap = parameterValueMap;
	}
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#perform(genedata.bx.adapter.assay.AssayValueFactory, java.util.List)
	 */
	@Override
	abstract public List<AssayValue> perform(AssayValueFactory assayValueFactory, List<AssayAttribute> assayAttributes);
	
	/* (non-Javadoc)
	 * @see genedata.bx.adapter.assay.AssayDataAdapter#getCommentLines()
	 */
	@Override
	public List<String> getCommentLines() {
		return commentLines;
	}

	/**
	 * @return The assay value factory.
	 */
	protected AssayValueFactory getAssayValueFactory() {
		return assayValueFactory;
	}

	/**
	 * @param assayValueFactory The assay value factory.
	 */
	protected void setAssayValueFactory(AssayValueFactory assayValueFactory) {
		this.assayValueFactory = assayValueFactory;
	}

	/**
	 * @return The list of assay attributes.
	 */
	protected List<AssayAttribute> getAssayAttributes() {
		return assayAttributes;
	}

	/**
	 * @param assayAttributes The list of assay attributes.
	 */
	protected void setAssayAttributes(List<AssayAttribute> assayAttributes) {
		this.assayAttributes = assayAttributes;
	}

	/**
	 * @param parameterName The name of the parameter.
	 * @return The parameter value or {@code null} if the parameter has not been found.
	 */
	protected String getParameterValue(String parameterName) {
		String valueString = null;
		
		if ((parameterValueMap != null) && (parameterValueMap.containsKey(parameterName))) {
			valueString = parameterValueMap.get(parameterName);
		}
		
		return valueString;
	}

	/**
	 * @param valueString The value string of interest.
	 * @return The missing value indicator which matches the value string, or {@code null} if not found.
	 */
	protected MissingValueIndicator getMissingValueIndicator(String valueString)
	{
		MissingValueIndicator missingValueIndicator = null;
		
		for (MissingValueIndicator indicator : missingValueIndicators) {
			if (indicator.getName().equalsIgnoreCase(valueString)) {
				missingValueIndicator = indicator;
				break;
			}
		}
		
		return missingValueIndicator;
	}

	/**
	 * @param valueString The identifier string or name of an isolate.
	 * @return The matching isolate, or {@code null} if not found.
	 */
	protected Isolate getIsolate(String valueString)
	{
		Isolate isolate = isolateInformationProvider.retrieveIsolate(valueString);
		
		return isolate;
	}

	/**
	 * @param identifier The identifier number of an isolate.
	 * @return The matching isolate, or {@code null} if not found.
	 */
	protected Isolate getIsolate(Long identifier)
	{
		Isolate isolate = isolateInformationProvider.retrieveIsolate(identifier);
		
		return isolate;
	}

	/**
	 * @param plate The plate of interest.
	 * @param rowIndex The row index.
	 * @param columnIndex The column index.
	 * @return {@code true} if the well contains a value (i.e. it is not reserved or used as control).
	 */
	protected boolean isValueWell(Plate plate, int rowIndex, int columnIndex) {
		return (plate != null) ? WELL_ROLE_VALUE.equals( plate.getRole(rowIndex, columnIndex) ) : false;
	}

	/**
	 * @param assayAttributes The list of assay attributes.
	 * @param label The label of interest.
	 * @return The assay attribute which matches the label, or {@code null} if not found.
	 */
	protected AssayAttribute getAssayAttribute(List<AssayAttribute> assayAttributes, String label)
	{
		if ((assayAttributes == null) || (label == null)) {
			log.warn("Failed to retrieve matching assay attribute: Empty reference to assay or column label.");
			return null;
		}
		
		AssayAttribute assayAttribute = null;
		
		for (AssayAttribute attribute : assayAttributes) {
			if (label.equalsIgnoreCase( attribute.getName() )) {
				assayAttribute = attribute;
				break;
			}
		}
		
		if (assayAttribute == null) {
			for (AssayAttribute attribute : assayAttributes) {
				boolean found = false;
				
				if (matchAttributeNameWithinLabel(attribute, label)) {
					found = true;
				}
				else if (attribute.getHeaderLabelPattern() != null) {
					Pattern pattern = Pattern.compile(attribute.getHeaderLabelPattern(), Pattern.CASE_INSENSITIVE);
					
					Matcher matcher = pattern.matcher(label);
					
					if (matcher.find()) {
						found = true;
					}
				}
				
				if (found) {
					assayAttribute = attribute;
					break;
				}
			}
		}
		
		return assayAttribute;
	}

	/**
	 * Try to match the name of the assay attribute within the label.
	 * The following cases do not match:
	 * <ul>
	 * <li>Alphabetical char directly before the name (e.g. "ko" is not matched in "loko").</li>
	 * <li>Alphabetical char directly after the name (e.g. "ca" is not matched in "cape").</li>
	 * <li>Alphabetical char after the name, separated by a whitespace (e.g. "Purity" is not matched in "Purity Range").</li>
	 * </ul>
	 * Examples for matches:
	 * <ul>
	 * <li>"Purity" is matched in "AG-17 : Purity"</li>
	 * <li>"Purity" is matched in "Purity (percent)"</li>
	 * </ul>
	 * @param attribute The assay attribute.
	 * @param label The label of interest.
	 * @return {@code true} if the name of the assay attribute has been found within the label.
	 */
	private static boolean matchAttributeNameWithinLabel(AssayAttribute attribute, String label)
	{
		int index = label.toLowerCase().indexOf( attribute.getName().toLowerCase() );
		
		return ((index > -1)
				&& !isAlphabeticalChar(label, index - 1)
				&& !isAlphabeticalChar(label, index + attribute.getName().length() )
				&& !(isWhitespaceChar(label, index + attribute.getName().length() )
					&& isAlphabeticalChar(label, index + attribute.getName().length() + 1)));
	}

	/**
	 * @param string The string of interest.
	 * @param i The index of a character in the string.
	 * @return {@code true} if this character is a space or a tab.
	 */
	private static boolean isWhitespaceChar(String string, int i)
	{
		if ((i < 0) || (i >= string.length())) {
			return false;
		}
		
		char c = string.charAt(i);
		
		return (c == ' ') || (c == '\t');
	}

	/**
	 * @param string The string of interest.
	 * @param i The index of a character in the string.
	 * @return {@code true} if this character is an uppercase or lowercase letter.
	 */
	private static boolean isAlphabeticalChar(String string, int i)
	{
		if ((i < 0) || (i >= string.length())) {
			return false;
		}
		
		char c = string.charAt(i);
		
		return ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'));
	}

	/**
	 * Removes double and single quotes. The string is not trimmed at all.
	 * See genedata.bdp.bl.util.StringUtil#unquote
	 * 
	 * @param str The string to be un-quoted.
	 * @return The un-quoted string.
	 */
	protected static String unquote(String str)
	{
		String answer = str;
		
		if (str != null) {
			if (str.startsWith("\"") && str.endsWith("\"")) {
				answer = str.substring(1).replaceAll("\"$", "");
			}
			else if (str.startsWith("'") && str.endsWith("'")){
				answer = str.substring(1).replaceAll("'$", "");
			}
		}
		
		return answer;
	}

	/**
	 * Answer String representation of items separated by delimiter.
	 * See genedata.bdp.bl.util.StringUtil#join
	 * 
	 * @param strings The list of strings.
	 * @param delimiter The delimiter characters.
	 * @param quoteStringsFlag Determines whether the strings will be enclosed in single quotes.
	 * @return The concatenated string.
	 */
	protected String joinStrings(List<String> strings, String delimiter, boolean quoteStringsFlag)
	{
		if (strings == null) {
			return "";
		}
		
		StringBuilder builder = new StringBuilder();
		
		String del = "";
		for (String string : strings) {
			builder.append(del);
			
			if (quoteStringsFlag) {
				builder.append(QUOTE_STRING_CHARACTER);
			}
			
			builder.append( string.toString() );
			
			if (quoteStringsFlag) {
				builder.append(QUOTE_STRING_CHARACTER);
			}
			
			del = delimiter;
		}
		
		return builder.toString();
	}
	
	/**
	 * Answer String representation of items separated by delimiter.
	 * See genedata.bdp.bl.util.StringUtil#join
	 * 
	 * @param strings The list of strings.
	 * @param delimiter The delimiter characters.
	 * @return The concatenated string.
	 */
	protected String joinStrings(List<String> strings, String delimiter)
	{
		return joinStrings(strings, delimiter, false);
	}

	/**
	 * @param isolate The isolate of interest.
	 * @param assayAttribute The assay attribute of interest.
	 * @param valueString The value string (may contain a double value or a missing value indicator string).
	 * @return The newly generated assay value, or {@code null} if the value string is neither a numeric value nor a missing value.
	 * @throws StringValueTooLongException in the case that the string value exceeds the size limit.
	 * @throws NumericValueTooLargeException in the case that the absolute numeric value is too large.
	 * @throws MismatchedDateFormatException in the case the date value could not be parsed.
	 * @throws UnknownCvValueException in the case that the CV value is unknown.
	 */
	protected AssayValue generateAssayValue(Isolate isolate, AssayAttribute assayAttribute, String valueString)
		throws StringValueTooLongException, NumericValueTooLargeException, MismatchedDateFormatException, UnknownCvValueException
	{
		AssayValue assayValue = null;
		
		if (assayAttribute.isStringValueType()) {
			try {
				assayValue = assayValueFactory.createAssayValue(isolate, assayAttribute, valueString);
			}
			catch (StringValueTooLongException e) {
				throw e;
			}
			catch (Exception e) {
				log.warn("Could not create assay value for string: " + valueString, e);
			}
		}
		else if (assayAttribute.isNumericValueType()) {
			Double numericValue = null;
			try {
				numericValue = Double.parseDouble(valueString);
				
				log.debug("Found value for assay attribute '" + assayAttribute.getName() + "': " + numericValue.toString() );
				
				assayValue = assayValueFactory.createAssayValue(isolate, assayAttribute, numericValue);
			}
			catch (NumericValueTooLargeException e) {
				throw e;
			}
			catch (Exception e) {
				log.debug("Could not parse string to double value: " + valueString);
			}
			
			if (numericValue == null) {
				try {
					// replace decimal comma by decimal point
					numericValue = Double.parseDouble( valueString.replace(',', '.') );
					
					log.debug("Found value for assay attribute '" + assayAttribute.getName() + "': " + numericValue.toString() );
					
					assayValue = assayValueFactory.createAssayValue(isolate, assayAttribute, numericValue);
				}
				catch (NumericValueTooLargeException e) {
					throw e;
				}
				catch (Exception e) {
					log.debug("Could not parse string to double value: " + valueString);
				}
			}
			
			if (numericValue == null) {
				MissingValueIndicator missingValueIndicator = getMissingValueIndicator(valueString);
				
				if (missingValueIndicator != null) {
					log.debug("Found missing value indicator for assay attribute '" + assayAttribute.getName() + "': " + missingValueIndicator.getName() );
					
					try {
						assayValue = assayValueFactory.createAssayValue(isolate, assayAttribute, missingValueIndicator);
					}
					catch (Exception e) {
						log.warn("Could not create missing assay value indicator for string: " + valueString, e);
					}
				}
			}
		}
//		else if (assayAttribute.isDateValueType()) {
//			String dateFormatPattern = getDateFormatPattern();
//			try {
//				SimpleDateFormat formatter = new SimpleDateFormat(dateFormatPattern);
//				Date date = formatter.parse(valueString);
//				
//				log.debug("Found date for assay attribute '" + assayAttribute.getName() + "': " + date);
//				
//				assayValue = assayValueFactory.createAssayValue(isolate, assayAttribute, date);
//			}
//			catch (ParseException e) {
//				throw new MismatchedDateFormatException( e.getMessage(), dateFormatPattern);
//			}
//			catch (Exception e) {
//				log.warn("Could not parse string to date value: " + valueString);
//			}
//		}
		else if (assayAttribute.isCvValueType()) {
			try {
				List<String> cvValues = assayAttribute.getCvValues();
				
				if (cvValues.contains(valueString)) {
					log.debug("Found CV value for assay attribute '" + assayAttribute.getName() + "': " + valueString);
					
					assayValue = assayValueFactory.createAssayValue(isolate, assayAttribute, valueString);
				}
				else {
					throw new UnknownCvValueException("Unknown CV value.");
				}
			}
			catch (UnknownCvValueException e) {
				throw e;
			}
			catch (Exception e) {
				log.warn("Could not parse string to CV value: " + valueString);
			}
		}
		else {
			log.warn("Found assay attribute with unsupported type: " + assayAttribute.getName() );
		}
		
		return assayValue;
	}
	
	/**
	 * @param line The line of interest.
	 * @return {@code true} if the line has the required size and contains numbers.
	 */
	protected boolean isDataLine(String line) {
		log.debug("isDataLine, line: " + line);
		
		if (line == null) {
			return false;
		}
		
		boolean answer = true;
		
		String[] tokens = split(line);
		
		if (tokens.length != numberOfColumns) {
			log.debug("The number of columns does not match.");
			answer = false;
		}
		else {
			for (String token : tokens) {
				try {
					Double.parseDouble(token);
				}
				catch (Exception e) {
					log.debug("Could not parse to double value: " + token);
					answer = false;
				}
			}
		}
		
		return answer;
	}

	/**
	 * Clears the list of comment lines and the messages.
	 */
	protected void clear() {
		commentLines.clear();
	}

	/**
	 * Removes the comment indicator character (i.e. '#') from the comment line and adds it to the list of comments.
	 * @param commentLine The comment line.
	 */
	protected void addCommentLine(String commentLine)
	{
		log.info("addCommentLine: " + commentLine);
		
		if (commentLine == null) {
			return;
		}
		
		commentLine = commentLine.trim();
		
		if (commentLine.startsWith(COMMENT_LINE_INDICATOR)) {
			commentLine = commentLine.substring(1).trim();
		}
		
		if (commentLine.length() > 0) {
			commentLines.add(commentLine);
		}
	}

	/**
	 * @param messageType The type of the message.
	 * @param text The text of the message.
	 */
	protected void addMessage(MessageType messageType, String text)
	{
		if ((messageType == null) || (text == null)) {
			return;
		}
		
		switch (messageType) {
			case INFO:
				addInfoMessage(text);
				break;
			case WARNING:
				addWarningMessage(text);
				break;
			case ERROR:
				addErrorMessage(text);
				break;
			default:
				log.warn("Unknown message type: " + messageType);
		}
	}

	/**
	 * @param text The information message which will be added to the message list.
	 */
	protected void addInfoMessage(String text)
	{
		log.info("Info message: " + text);
		
		try {
			reporter.info(text);
		}
		catch (Exception e) {
			log.warn("Failed to create info message with text: " + text);
		}
	}

	/**
	 * @param text The warning message which will be added to the message list.
	 */
	protected void addWarningMessage(String text)
	{
		log.warn("Warning message: " + text);
		
		try {
			reporter.warn(text);
		}
		catch (Exception e) {
			log.warn("Failed to create warning message with text: " + text);
		}
	}

	/**
	 * @param text The error message which will be added to the message list.
	 */
	protected void addErrorMessage(String text)
	{
		log.error("Error message: " + text);
		
		try {
			reporter.error(text);
		}
		catch (Exception e) {
			log.warn("Failed to create error message with text: " + text);
		}
	}
	
	/**
	 * The charset name may be configured in the parameter settings
	 * with a adapter class specific key, like
	 * 'genedata_bx_bl_sc_adapter_assay_DefaultAssayDataAdapter_charset'
	 * 
	 * Example:
	 * insert into parameter (id, user_specific, key, value) values(
	 *   parameter_seq.nextval,
	 *   'N', 
	 *   'genedata_bx_bl_sc_adapter_assay_DefaultAssayDataAdapter_charset',
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
	 * 
	 * @param s
	 * 
	 * @return The given string split on the {@link #CONFIGURED_DELIMITER}
	 */
	protected String[] split(String s) {
		if (null == s) {
			return new String[] {""};
		}
		return s.split("["+CONFIGURED_DELIMITER+"]");
	}
	
	/**
	 * 
	 * @param s
	 * @param limit
	 * 
	 * @return The given string split on the {@link #CONFIGURED_DELIMITER}
	 */
	protected String[] split(String s, int limit) {
		if (null == s) {
			return new String[] {""};
		}
		return s.split("["+CONFIGURED_DELIMITER+"]", limit);
	}
	
}
