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

public class FacsCellSortingReport implements Iterable<FacsCellSortingReport.ReportLine> {
	private static final Charset CHARSET = Charset.forName("UTF-8");
	private static final String COLUMN_SEP = "\t";
	
	private ArrayList<ReportLine> report;
	
	//private final Logger log = Logger.getLogger(getClass());
	
	public FacsCellSortingReport(InputStream inputStream) throws LhsException {
		this.report = new ArrayList<ReportLine>() ;

		LineNumberReader reader = new LineNumberReader(new InputStreamReader(inputStream, CHARSET));
		try{
			List<String> header = getNextTableCells(reader, COLUMN_SEP);
			if (header == null
					|| header.size() < 2
					|| (! "Clone Pool ID".equals(header.get(0)))
					|| (! "Plate Barcode".equals(header.get(1)))
					) {
				throw new LhsException("Expecting header with 'Clone Pool ID' and 'Plate Barcode' in line " + reader.getLineNumber());
			}

			for (List<String> tableCells = getNextTableCells(reader, COLUMN_SEP); tableCells != null; tableCells = getNextTableCells(reader, COLUMN_SEP)) {
				if (tableCells.size() < 2) {
					throw new LhsException("Expecting values for 'Clone Pool ID' and 'Plate Barcode' in line " + reader.getLineNumber());
				}
				ReportLine reportLine = new ReportLine(reader.getLineNumber(), tableCells.get(0), tableCells.get(1));
				report.add(reportLine);
			}
		} catch (IOException e) {
			throw new LhsException(e);
		} finally {
			close(reader);
		}
	}

	@Override
	public Iterator<ReportLine> iterator() {
		return report.iterator();
	}

	public int size() {
		return report.size();
	}
	

	private static List<String> getNextTableCells(LineNumberReader reader, String columnSep) throws IOException {
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
				/* We ignore problems with close since there's nothing we can do anyway */
			}
		}
		return false;
	}

	
	public static class ReportLine {
		private int lineNumber;
		private String clonePoolQid;
		private String plateBarcode;
		
		public ReportLine(int lineNumber, String clonePoolQid, String plateBarcode) {
			this.lineNumber = lineNumber;
			this.clonePoolQid = clonePoolQid;
			this.plateBarcode = plateBarcode;
		}
		
		public int getLineNumber() {
			return lineNumber;
		}
		
		public String getClonePoolQid() {
			return clonePoolQid;
		}
		public String getPlateBarcode() {
			return plateBarcode;
		}
	}

}
