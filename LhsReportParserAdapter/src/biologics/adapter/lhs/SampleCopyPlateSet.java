package biologics.adapter.lhs;

import genedata.bx.adapter.lhs.v2.LhsCallback;
import genedata.bx.adapter.lhs.v2.LhsCopyPlateSet;
import genedata.bx.adapter.lhs.v2.LhsException;
import genedata.bx.adapter.lhs.v2.LhsPlate;
import genedata.bx.adapter.lhs.v2.LhsPlateInformationProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Sample implementation of LhsCopyPlateSet adapter
 * 
 * input:
 * <1>  <2>
 * 0001	D00B
 * 0002	D00A
 * 0003 DOOC
 * 
 * <1> source plate barcode
 * <2> destination plate barcode
 */
final public class SampleCopyPlateSet implements LhsCopyPlateSet {

	private LhsCallback.CopyPlateSet callback;
	/**
	 * Current line number in input
	 */
	private int lineNumber;
	/**
	 * current input line
	 */
	private String line;
	
	/**
	 * resulting destination plates
	 */
	private List<LhsPlate> destinationPlates;

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// no configuration needed in this example
	}

	@Override
	public void options(genedata.bx.adapter.lhs.v2.LhsOptions.CopyPlateSet options) {
		// no options set in this example
	}

	@Override
	public List<LhsPlate> perform(LhsCallback.CopyPlateSet callback) throws LhsException {
		init(callback);	
		processInputStream();		
		return destinationPlates;
	}
	
	/**
	 * Initialize all fields.
	 * @param callback
	 */
	private void init(LhsCallback.CopyPlateSet callback) {
		this.callback= callback;
		lineNumber= 0;
		line= "";
		destinationPlates = new ArrayList<LhsPlate>();
	}

	/**
	 * Process the input stream, handling IoExceptions.
	 * @throws LhsException
	 */
	private void processInputStream() throws LhsException {
		BufferedReader reader= null;		
		try {
			reader = new BufferedReader( new InputStreamReader(callback.getInputStream()));
			processLines(reader);
		} catch (IOException e) {
			throw new LhsException("Processing input", e);
		}
		finally {
			closeReader(reader);
		}
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
	 * Process one line like this:
	 * 0001	D00B
	 */
	private void processLine() {
		String[] fields = line.split("\t");
		if (fields.length != 2) {
			reportError("Expected 2 columns, but found: " + fields.length + ". Skipping line.");
			return;
		}
		
		String sourceBarcode= validateBarcode("Source Barcode", fields[0]);
		String destBarcode= validateBarcode("Destination Barcode", fields[1]);
		if (sourceBarcode == null || destBarcode == null) {
			return;
		}
		
		process(sourceBarcode, destBarcode);
	}

	/**
	 * Validate barcode is not empty
	 * @param fieldName
	 * @param value
	 * @return
	 */
	String validateBarcode(String fieldName, String value) {
		if (value == null || value.length() == 0) {
			reportError("Invalid empty " + fieldName + ".");
			return null;
		}
		return value;
	}

	/**
	 * Process one record
	 * @param sourceBarcode
	 * @param destBarcode
	 */
	void process(String sourceBarcode, String destBarcode) {
		LhsPlateInformationProvider sourcePlateInformationProvider = callback.getSourcePlateInformationProvider();
		LhsPlate sourcePlate = sourcePlateInformationProvider.retrieveByBarcode(sourceBarcode);
		if (sourcePlate == null) {
			reportError("Could not find source plate with barcode: " + sourceBarcode);
			return;
		}
		
		LhsPlate destPlate = callback.copy(sourcePlate);
		destPlate.setBarcode(destBarcode);
		destinationPlates.add(destPlate);
	}

	private void closeReader(BufferedReader reader) throws LhsException{
		if (reader != null) {
			try {
				reader.close();
			} catch (IOException e) {
				throw new LhsException("Close reader", e);
			}
		}
	}

	/**
	 * Report an error together with the input line number and line
	 * @param reason
	 */
	private void reportError(String reason) {
		callback.getReporter().error( "Error reading line ##0 |#1| : #2", lineNumber, line, reason);
	}
}
