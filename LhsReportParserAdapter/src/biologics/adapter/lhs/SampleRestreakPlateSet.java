package biologics.adapter.lhs;

import genedata.bx.adapter.entity.PlateInfo;
import genedata.bx.adapter.entity.PlateWell;
import genedata.bx.adapter.entity.PlateWellInformationProvider;
import genedata.bx.adapter.lhs.v2.LhsCallback;
import genedata.bx.adapter.lhs.v2.LhsException;
import genedata.bx.adapter.lhs.v2.LhsOptions;
import genedata.bx.adapter.lhs.v2.LhsPlate;
import genedata.bx.adapter.lhs.v2.LhsRestreakPlateSet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sample implementation of an LhsRestreakPlateSet adapter.
 * 
 * Expected input:
 * 
 * <pre>
 * #SBC	SR	SC	DBC		DR	DC
 * 0001	1	1	D001	1	1
 * 0001	1	1	D001	1	2
 * 0001	1	1	D001	1	3
 * ...
 * 0002 8	12	D008	8	12
 * </pre>
 * 
 * Columns are separated by tabs.
 * 
 * SBC: source plate barcode
 * SR:	source plate well row
 * SC:	source plate well column
 * DBC: destination plate barcode
 * DR:	destination plate well row
 * DC:	destination plate well column
 * 
 * 
 */
final public class SampleRestreakPlateSet implements LhsRestreakPlateSet {
	
	private LhsCallback.RestreakPlateSet callback;

	/**
	 * Map from barcode to destination plate
	 */
	private Map<String,LhsPlate> barcodeDestinationPlateMap;
	/**
	 * Current line number in input
	 */
	private int lineNumber;
	/**
	 * current input line
	 */
	private String line;
			

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// no configuration needed in this example
	}

	@Override
	public void options(LhsOptions.RestreakPlateSet options) {
		// no options set in this example
	}

	@Override
	public List<LhsPlate> perform(LhsCallback.RestreakPlateSet callback) throws LhsException {
		init(callback);	
		processInputStream();		
		return new ArrayList<LhsPlate>( barcodeDestinationPlateMap.values() );
	}

	/**
	 * Initialize all fields.
	 * @param callback
	 */
	private void init(LhsCallback.RestreakPlateSet callback) {
		this.callback= callback;
		barcodeDestinationPlateMap = new HashMap<String,LhsPlate>();
		lineNumber= 0;
		line= "";
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
	 * Process one line:
	 * 0001	1	1	D001	1	1
	 */
	private void processLine() {
		String[] fields = line.split("\t");
		if (fields.length != 6) {
			reportError("Expected 6 columns, but found: " + fields.length + ". Skipping line.");
			return;
		}
		
		String sourceBarcode;
		int sourceRow;
		int sourceColumn;
		String destBarcode;
		int destRow;
		int destColumn;
		try {
			sourceBarcode = validateBarcode("Source Barcode", fields[0]);
			sourceRow = parseInteger("Source Row", fields[1]);
			sourceColumn = parseInteger("Source Column", fields[2]);
			
			destBarcode = validateBarcode("Destination Barcode",fields[3]);
			destRow = parseInteger("Destination Row", fields[4]);
			destColumn = parseInteger("Destination Column", fields[5]);
		} catch (IllegalArgumentException e) {
			return; // skip this line; errors have been reported earlier
		}
		
		process(sourceBarcode, sourceRow, sourceColumn, destBarcode, destRow, destColumn);
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
			throw new IllegalArgumentException();
		}
		return value;
	}
	
	/**
	 * Parse integer field value, report errors
	 * @param fieldName
	 * @param value
	 * @return integer read or -1 if invalid
	 */
	private int parseInteger(String fieldName, String value) {
		int field= -1;
		try {
			field= Integer.parseInt(value);
		} catch (NumberFormatException e) {
			reportError("Could not parse field " + fieldName + " : |" + value + "| : invalid number format. " );
			throw new IllegalArgumentException();
		}
		return field;
	}

	/**
	 * Process one input record, mapping form source to destination plate
	 * @param sourceBarcode
	 * @param sourceRow
	 * @param sourceColumn
	 * @param destBarcode
	 * @param destRow
	 * @param destColumn
	 */
	private void process(
			String sourceBarcode, int sourceRow, int sourceColumn,
			String destBarcode,   int destRow,   int destColumn) {
		
		PlateWell sourcePlateWell = findSourcePlateWell(sourceBarcode, sourceRow, sourceColumn);		
		if (sourcePlateWell == null || sourcePlateWell.getWellContent() == null) {
			return; // error has been reported earlier
		}
		
		putDestination(destBarcode, destRow, destColumn, sourcePlateWell);
	}

	/**
	 * Find the well content on plate identified by barcode
	 * @param barcode
	 * @param row
	 * @param column
	 * @return
	 */
	private PlateWell findSourcePlateWell(String barcode, int row, int column) {
		PlateWellInformationProvider plateWellInformationProvider = callback.getPlateWellInformationProvider();
		PlateInfo sourcePlate = plateWellInformationProvider.retrieveByBarcode(barcode);
		if (sourcePlate == null) {
			reportError("Could not identify source plate using barcode: " + barcode);
			return null;
		}
		PlateWell sourcePlateWell = plateWellInformationProvider.retrieve(sourcePlate, row, column);
		
		if (sourcePlateWell == null || sourcePlateWell.getWellContent() == null) {
			reportError("Could not find well content for plate " + barcode + " at row: " + row + " column: " + column);
		}
		return sourcePlateWell;
	}

	/**
	 * Put the well content on the destination plate identified by barcode. 
	 * If destination plate doesn't exist yet, create one. 
	 * @param barcode
	 * @param row
	 * @param column
	 * @param wellContent
	 */
	private void putDestination(String barcode, int row, int column, PlateWell sourceWell) {
		LhsPlate destPlate = barcodeDestinationPlateMap.get(barcode);
		if (destPlate == null) { // first time
			destPlate= callback.createEmptyPlate();
			destPlate.setBarcode(barcode);
			barcodeDestinationPlateMap.put(barcode, destPlate);
		}
		
		destPlate.setParent(row, column, sourceWell);
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
