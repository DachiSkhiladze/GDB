package biologics.adapter.lhs;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.entity.PlateInfo;
import genedata.bx.adapter.entity.PlateWell;
import genedata.bx.adapter.entity.PlateWellInformationProvider;
import genedata.bx.adapter.entity.Well;
import genedata.bx.adapter.lhs.v2.LhsCallback;

class SampleCoTransfectRecord {
	
	private int lineNumber;
	private String srcBarcode;
	private String srcWell;
	private String destinationBarcode;
	private String destWell;
	
	private PlateWell sourceWell;
	private Well destinationWell;
	
	public SampleCoTransfectRecord(int lineNumber,
			String srcBarcode, String srcWell, String destinationBarcode, String destWell) {
		this.lineNumber = lineNumber;
		this.srcBarcode = srcBarcode;
		this.srcWell = srcWell;
		this.destinationBarcode = destinationBarcode;
		this.destWell = destWell;
	}
	
	int getLineNumber() {
		return lineNumber;
	}
	
	PlateWell getSourceWell() {
		return sourceWell;
	}
	
	Well getDestinationWell() {
		return destinationWell;
	}
	
	String getDestinationKey() {
		return destinationBarcode + "-" + destWell;
	}
	String getDestinationBarcode() {
		return destinationBarcode;
	}
	
	boolean validateRecord(LhsCallback.CoTransfectPlateSet callback, Reporter reporter) {
		PlateWellInformationProvider plateInfoProvider = callback.getPlateWellInformationProvider();
		
		PlateInfo srcPlate = null;
		if (null != srcBarcode && ! srcBarcode.isBlank()) {
			srcPlate = plateInfoProvider.retrieveByBarcode(srcBarcode);
			if (srcPlate == null) {
				reporter.error("Line #0: Source plate with barcode '#1' doesn't exist.", 
						lineNumber,
						srcBarcode);
				
				return false;
			}
		}
		
		PlateWell sourcePlateWell = null;
		if (null != srcWell && ! srcWell.isBlank()) {
			try {
				Well well = callback.getWellAddressConverter().parseWellAddress(srcWell);
				sourcePlateWell = plateInfoProvider.retrieve(srcPlate, well);
				if (sourcePlateWell == null) {
					reporter.error("Line #0: There is no content in the well '#1' "
							+ "on the source plate with barcode '#2'.",
							lineNumber,
							well.getWellAddress(),
							srcPlate.getBarcode());

					return false;
				}
			} catch (Exception e) {
				reporter.error("Line #0: #1.", lineNumber, e.getMessage());
				return false;
			}
			sourceWell = sourcePlateWell;
		}
		
		try {
			destinationWell = callback.getWellAddressConverter().parseWellAddress(destWell);
		} catch (Exception e) {
			reporter.error("Line #0: #1.", lineNumber, e.getMessage());
			return false;
		}
		
		return true;
	}
	
}
