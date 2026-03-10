/**
 *  
 */
package biologics.adapter.fastahp.isolate;

import genedata.bx.adapter.IsolateBasedFastaHeaderParser;
import genedata.bx.adapter.assay.Isolate;
import genedata.bx.adapter.assay.IsolateInformationProvider;
import genedata.bx.adapter.plate.Plate;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 * Retrieves the Plate and Well position from a fasta header which starts with 
 * the plate barcode and then adds the well position last.  
 * <p>
 * Example header lines:
 * <ul>
 * <li>{@code >A10}</li>
 * <li>{@code >23183_G3_9970}</li>
 * <li>{@code >23326_A10_9970_A10}</li>
 * </ul>
 */
public class BarcodedPlateBasedIsolateFastaHeaderParser implements IsolateBasedFastaHeaderParser {

	private static Logger log = Logger.getLogger(BarcodedPlateBasedIsolateFastaHeaderParser.class);
	
	private static String wellAddressPattern = "[A-Z]{1,2}\\d+";

	private int wellRow;
	private int wellColumn;
	private Plate plate;
	private List<Plate> plates;
	
	@Override
	public boolean handlesPlateBasedIsolates() {
		return true;
	}
	
	@Override
	public boolean handlesPlateFreeIsolates() {
		return false;
	}

	@Override
	public Plate getIdentifiedPlate() {
		return this.plate;
	}

	@Override
	public int getIdentifiedWellColumn() {
		return this.wellColumn;
	}

	@Override
	public int getIdentifiedWellRow() {
		return this.wellRow;
	}
	
	@Override
	public void setPlates(List<Plate> plates) {
		if (null == plates) {
			throw new IllegalArgumentException("No Plates to identify given.");
		}
		this.plates = plates;
	}
	
	@Override
	public void parse(String headerLine) {
		
		log.debug("Header line: "+headerLine);
		reset();
		
		if (null == headerLine || 0 == headerLine.length()) {
			return;
		}
		
		recognizePlate (headerLine);
		recognizeWellAdress (headerLine);
	}
	
	private void reset() {
		this.plate = null;
		this.wellRow = 0;
		this.wellColumn = 0;
	}
	
	private void recognizePlate (String headerLine) {
		
		if (headerLine.startsWith(">")) {
			headerLine = headerLine.substring(1);
		}
		
		if (null == plates || 0 == plates.size()) {
			return;
		}
		
		for (Plate plate : plates) {
			
			String barcode = null == plate.getBarcode() ? null : plate.getBarcode().trim();
			if (null == barcode || 0 == barcode.length()) {
				continue;
			}
			
			if (headerLine.startsWith(barcode+"-") || headerLine.startsWith(barcode+"_")) {
				this.plate = plate;
				break;
			}
		}
		
		// if only 1 plate is present and the header consists of only the 
		// well address we report back the one plate 
		if (null == this.plate && 1 == plates.size()) {
			
			if (headerLine.matches(wellAddressPattern) || headerLine.matches(wellAddressPattern+"\\s+.*")) {
				this.plate = plates.get(0);
			}
		}
	}
	
	private void recognizeWellAdress (String headerLine) {
		
		if (headerLine.startsWith(">")) {
			headerLine = headerLine.substring(1);
		}
		
		if (null == this.plate) {
			return;
		} 
		
		String wellAdress;
		try {
			wellAdress = headerLine.split("\\s+", 2)[0];
		} catch (Exception e) {
			log.error("Error while recognizing the well address", e);
			return;
		}
		
		Pattern p = Pattern.compile(wellAddressPattern);
		Matcher m = p.matcher(wellAdress);
		
		String a = null;
		while (m.find()) {		// find last occurrence
			a = m.group();
		}
		if (null != a) {
			parseWellAdress(a);
		}
	}

	@Override
	public String getGroupName() {
		// no group will be recognized
		return null;
	}

	@Override
	public String getIdentifiedChainInfo() {
		// no chain info will be recognized
		return null;
	}

	@Override
	public void setConfiguration(Map<String, String> config) {
		// not needed
	}

	@Override
	public Isolate getIdentifiedIsolate() {
		// we are plate based thus no isolate identification
		return null;
	}

	@Override
	public void setIsolateInformationProvider(IsolateInformationProvider provider) {
		// not needed
	}
	
	private void parseWellAdress (String wellAdress) {
		log.debug("Recognize well address from "+wellAdress);
		
		// Possible Formats:
		// AB02 X9 C00004 
		
		String wa = wellAdress;
		if (null == wa || 0 == wa.length()) {
			return;
		}
		Pattern startChars = Pattern.compile("^[A-Z]");
		Pattern startDigits = Pattern.compile("^[0-9]");
		
		int row = 0;
		while (startChars.matcher(wa).find()) {
			char c = wa.charAt(0);
			wa = wa.substring(1);
			
			row = row * 26 + (c-'A');
		}
		this.wellRow = row + 1;	// counting starts at 1
		log.debug("Row in Well Address '"+wellAdress+"' recognized as "+this.wellRow);
		
		int column = 0;
		while (startDigits.matcher(wa).find()) {
			char c = wa.charAt(0);
			wa = wa.substring(1);
			
			column = column * 10 + (c-'0');
		}
		this.wellColumn = column;
		log.debug("Column in Well Address '"+wellAdress+"' recognized as "+this.wellColumn);
	}
	
}
