package biologics.adapter.lhs;

import genedata.bx.adapter.lhs.v2.LhsException;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class SampleCoTransfectReport implements Iterable<SampleCoTransfectRecord> {
	private static final Charset CHARSET = Charset.forName("UTF-8");
	private static final String COLUMN_SEP = "\t";
	
	private ArrayList<SampleCoTransfectRecord> report;
	
	private int columnCount = 4;
	private String sourceBarcodeLabel = "Source Plate Barcode";
	private String sourceWellAddressLabel = "Source Well Address";
	private String destinationBarcodeLabel = "Destination Plate Barcode";
	private String destinationWellAddressLabel = "Destination Well Address";
	
	public SampleCoTransfectReport(InputStream inputStream) throws LhsException {
		this.report = new ArrayList<SampleCoTransfectRecord>() ;
		
		LineNumberReader reader = new LineNumberReader(new InputStreamReader(inputStream, CHARSET));
		try {
			List<String> header = getNextTableCells(reader, COLUMN_SEP);
			if (header == null
					|| header.size() < columnCount
					|| (! sourceBarcodeLabel.equals(header.get(0)))
					|| (! sourceWellAddressLabel.equals(header.get(1)))
					|| (! destinationBarcodeLabel.equals(header.get(2)))
					|| (! destinationWellAddressLabel.equals(header.get(3)))
					) {
				throw new LhsException(getHeaderLineExceptionMessage(reader.getLineNumber()));
			}
			
			for (List<String> tableCells = getNextTableCells(reader, COLUMN_SEP);
					tableCells != null; tableCells = getNextTableCells(reader, COLUMN_SEP)) {
				if (tableCells.size() < columnCount) {
					throw new LhsException(getHeaderLineExceptionMessage(reader.getLineNumber()));
				}
				SampleCoTransfectRecord record = new SampleCoTransfectRecord(reader.getLineNumber(),
						tableCells.get(0), tableCells.get(1), tableCells.get(2), tableCells.get(3));
				report.add(record);
			}
		} catch (IOException e) {
			throw new LhsException(e);
		} finally {
			close(reader);
		}
	}
	
	private String getHeaderLineExceptionMessage(int lineNumber) {
		return "Expecting header with '"+sourceBarcodeLabel+"', '"+sourceWellAddressLabel+"',"
				+ " '"+destinationBarcodeLabel+"' and '"+destinationWellAddressLabel+"'"
				+ " in line " + lineNumber;
	}
	
	@Override
	public Iterator<SampleCoTransfectRecord> iterator() {
		return report.iterator();
	}
	
	public int size() {
		return report.size();
	}
	
	private static List<String> getNextTableCells(LineNumberReader reader, String columnSep)
			throws IOException {
		String line = reader.readLine();
		
		List<String> answer = null;
		if (line != null) {
			answer = Arrays.asList(line.split(columnSep, -1));
		}
		return answer;
	}
	
	private static final boolean close(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
				return true;
			} catch (IOException e) { 
				// ignored
			}
		}
		return false;
	}
	
}
