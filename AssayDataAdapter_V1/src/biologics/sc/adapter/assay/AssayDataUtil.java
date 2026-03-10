package biologics.sc.adapter.assay;

public class AssayDataUtil {
	
	/**
	 * @param platePosition The position on the plate in the form "E2".
	 * @return The extracted row index.
	 */
	static int getRowIndex(String platePosition) {
		if (null == platePosition || platePosition.trim().length() < 2) {
			throw new IllegalArgumentException("Failed to extract row index from plate position: " + platePosition);
		}
		
		char rowChar = platePosition.trim().toUpperCase().charAt(0);
		
		if (rowChar < 'A' || rowChar > 'Z') {
			throw new IllegalArgumentException("Row index '" + rowChar + "' is invalid.");
		}
		
		return 1 + (rowChar - 'A');
	}
	
	/**
	 * @param platePosition The position on the plate in the form "E2".
	 * @return The extracted column index.
	 */
	static int getColumnIndex(String platePosition) {
		if (null == platePosition || platePosition.trim().length() < 2) {
			throw new IllegalArgumentException("Failed to extract column index from plate position: " + platePosition);
		}
		
		try {
			return Integer.parseInt( platePosition.substring(1) );
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException("Column index '" + platePosition.substring(1) + "' is invalid.");
		}
	}
	
}
