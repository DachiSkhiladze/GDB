package biologics.adapter.mpd;

import genedata.bx.adapter.entity.CellLine;
import genedata.bx.adapter.measurementprofile.MeasurementProfileDataAdapter;
import genedata.bx.adapter.measurementprofile.MeasurementProfileDataCallback;
import genedata.bx.adapter.measurementprofile.MeasurementProfileDataOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Sample implementation for MeasurementProfileDataAdapter API.
 * 
 * Process tab-delimited input like:
 * <pre>
	Timestamp            Glutamine | CLI-17  Glutamine | CLI-23  Lactate | CLI-17  Lactate | CLI-23
	2014-06-25 17:28:26  2.4	             3.1	             2.3               3.7
	2014-06-25 17:29:25  2.5                 3.2                 2.4               3.8
	2014-06-25 17:30:27  3.7                 3.1                 3.3               3.2
	2014-06-25 17:31:23  4.1                 3.0                 4.3               3.1
 * </pre>
 */
public final class SampleMeasurementProfileDataAdapter implements MeasurementProfileDataAdapter {
	static SimpleDateFormat DATE_FORMAT= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private MeasurementProfileDataCallback callback;

	/**
	 * current line in input
	 */
	private String line;

	/**
	 * current line number in input
	 */
	private int lineNumber;
	
	/**
	 * A value column is characterized by the name of measured value
	 * and the cell line for which is was measured.  
	 */
	private class Column {
		String name;
		CellLine cellLine;		
	}
	
	/**
	 * All columns containing numerical values (as opposed to the timestamp column)
	 */
	private List<Column> valueColumns;
		
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// configuration not used in this example
	}

	@Override
	public void options(MeasurementProfileDataOptions options) {
		// no options set in this example
	}

	@Override
	public void perform(MeasurementProfileDataCallback callback) {
		init(callback);
		processInputStream();
	}
	
	/**
	 * Initialize all fields.
	 * @param callback
	 */
	private void init(MeasurementProfileDataCallback callback) {
		this.callback= callback;
		lineNumber= 0;
		line= "";
		valueColumns= new ArrayList<SampleMeasurementProfileDataAdapter.Column>();
	}

	/**
	 * Process the input stream, handling IoExceptions.
	 */
	private void processInputStream() {
		BufferedReader reader= null;		
		try {
			reader = new BufferedReader( new InputStreamReader(callback.getInputStream()));
			line= reader.readLine();
			lineNumber++;
			processHeaderLine();
			if (noError()) { // don't attempt parsing values if there were errors parsing the header
				processLines(reader);
			}
		} catch (IOException e) {
			reportError(e.getMessage());
			throw new Error("Processing input", e);
		}
		finally {
			closeReader(reader);
		}
	}

	/**
	 * Process header line:	
	   <pre>
	   Timestamp  Glutamine | CLI-17  Glutamine | CLI-23  Lactate | CLI-17  Lactate | CLI-2
	   </pre>
	 */
	private void processHeaderLine() {
		String[] headers = line.split("\t", -1);
		if (!headers[0].equalsIgnoreCase("Timestamp")) {
			reportError("Expected 'Timestamp' in first column of header.");
		}
		
		for (int i = 1; i < headers.length; i++) {
			String header = headers[i];
			valueColumns.add( parseColumnHeader(header) );
		}
	}

	/**
	 * Parse column header, like: 'Glutamine | CLI-17'
	 * @param header
	 * @return column
	 */
	private Column parseColumnHeader(String header) {
		String[] fields = header.split(" [|] ", -1);
		if (fields.length != 2) {
			reportError("Expected 2 fields separated by ' | ', but found: " + fields.length + " in header: " + header);
			return null;
		}
		Column column = new Column();
		column.name= fields[0];
		String cellLineId = fields[1];
		column.cellLine= callback.getCellLineInformationProvider().retrieve(cellLineId);
		if (column.cellLine == null) {
			String cellLineLabel = callback.getCellLineInformationProvider().getSingularEntityLabel();
			reportError("Cannot find " + cellLineLabel + " " + cellLineId );
		}
		return column;
	}

	/**
	 * Process all lines in input; set current values for line and lineNumber
	 * @param reader
	 * @throws IOException
	 */
	private void processLines(BufferedReader reader) throws IOException {
		while ( null != (line= reader.readLine())) {
			lineNumber++;
			processLine();
		}
	}

	/**
	 * Process one line of values like this:
	 * <pre>
	 2014-06-25 17:28:26  2.4  3.1  2.3  3.7
	 * </pre>
	 */
	private void processLine() {
		String[] fields = line.split("\t", -1);
		int numColumnsInclTimestamp = valueColumns.size() + 1;
		if (fields.length != numColumnsInclTimestamp) {
			reportError("Expected " + numColumnsInclTimestamp + " columns, but found: " + fields.length + ". Skipping line.");
			return;
		}
		
		processFields(fields);
	}

	/**
	 * First field is timestamp, all following are data values 
	 * @param fields
	 */
	private void processFields(String[] fields) {
		Date timestamp= parseTimestamp(fields[0]);	
		if (timestamp == null) {
			return;
		}
		
		// all other columns
		for (int i = 1; i < fields.length; i++) {
			Number value= parseValue(fields[i], i+1);
			if (value == null) {
				continue;
			}
			Column column = valueColumns.get(i-1);
			callback.addDataPoint(column.name, column.cellLine, timestamp, value);
		}
	}

	/**
	 * Parse timestamp and report format errors 
	 * @param timestampStr
	 * @return
	 */
	private Date parseTimestamp(String timestampStr) {
		Date timestamp = null;
		try {
			timestamp = DATE_FORMAT.parse(timestampStr);
		} catch (ParseException e) {
			reportError("Cannot parse time stamp: " + timestampStr + ". Expected format: " + DATE_FORMAT.toPattern()  );
		}
		return timestamp;
	}

	/**
	 * Parse numerical value and report format errors
	 * @param valueStr
	 * @param column
	 * @return
	 */
	private Number parseValue(String valueStr, int column) {
		// allow empty values
		if (valueStr == null || valueStr.length() == 0) {
			return null;
		}
		
		Number value= null;
		try {
			value= Double.parseDouble(valueStr);
		} catch (NumberFormatException e) {
			reportError("Cannot parse value: " + valueStr + " in column " + column );
		}
		return value;
	}

	/**
	 * Safely close the reader we opened at the beginning
	 * @param reader
	 */
	private void closeReader(BufferedReader reader) {
		if (reader != null) {
			try {
				reader.close();
			} catch (IOException e) {
				throw new Error("Close reader", e);
			}
		}
	}

	/**
	 * Report an error together with the input line number and line
	 * @param reason
	 */
	private void reportError(String reason) {
		callback.getReporter().error( "Error reading line ##0 |#1| :\n #2", lineNumber, line, reason);
	}

	/**
	 * Check whether errors were reported
	 * @return
	 */
	private boolean noError() {
		return callback.getReporter().getNumberOfErrors() == 0;
	}
}
