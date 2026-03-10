package biologics.sc.adapter.lhs;

import genedata.bx.adapter.plate.Plate;

public class LhsReportParserUtil {

	/**
	 * @param sourcePlate The source plate.
	 * @return {@code true} if either the barcode or the alias
	 *  of the source plate is available.
	 */
	static boolean hasBarcodeOrAlias(Plate sourcePlate) {
		return (null != sourcePlate) &&
				(null != sourcePlate.getBarcode() || null != sourcePlate.getAlias());
	}

	/**
	 * @param sourcePlate The source plate.
	 * @return The barcode or the alias (if the barcode is not available)
	 *  or {@code null} if both is not available.
	 */
	static String getBarcodeOrAlias(Plate sourcePlate) {
		if (null == sourcePlate) {
			return null;
		}
		
		return (null != sourcePlate.getBarcode()) ? sourcePlate.getBarcode() : sourcePlate.getAlias();
	}
}
