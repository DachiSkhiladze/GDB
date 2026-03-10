/**
 * 
 */
package biologics.adapter.fastahp.isolate;

import genedata.bx.adapter.plate.Plate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

public class DefaultPlateBasedIsolateFastaHeaderParser extends AbstractIsolatedBasedFastaHeaderParser {

	private static Logger log = Logger.getLogger(DefaultPlateBasedIsolateFastaHeaderParser.class);
	
	private int wellRow;
	private int wellColumn;
	private Plate plate;
	private List<Plate> plates;
	
	private final List<Pattern> regexps = new ArrayList<Pattern>();
	
	@Override
	public boolean handlesPlateBasedIsolates() {
		return true;
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
		initRegularExpressions();
	}
	
	private void initRegularExpressions() {
		regexps.clear();
		
		for (Plate id : plates) {
			regexps.add(getRegularExpressionFromName(id.getAlias()));
		}
	}
	
	private Pattern getRegularExpressionFromName(String plateName) {
		StringBuilder rxbuilder = new StringBuilder();

		String[] seps = plateName.split("-");
		if (2 != seps.length) {
			log.debug("Plate Name '"+plateName+"' used as is.");
			rxbuilder.append(Pattern.quote(plateName));

		} else {
			// use GDB naming scheme as template of naming of plates

			// remove leading zeros of first part of the plate name
			rxbuilder.append("[0]*" + Pattern.quote(seps[0].replaceAll("^[0]*", "")));

			rxbuilder.append("[-_]?");

			// keep first char of second part of the plate name
			rxbuilder.append(Pattern.quote(seps[1].substring(0, 1)));
			seps[1] = seps[1].substring(1);
			// remove leading zeros of plate counter
			rxbuilder.append("[0]*" + Pattern.quote(seps[1].replaceAll("^[0]*", "")));
		}

		// add regexp for well address
		rxbuilder.append("[-_]"); // separator
		rxbuilder.append("([A-Z]{1,2}\\d{1,2})"); // AA01 B10 X9 grouped

		String regexp = rxbuilder.toString();
		log.debug("Plate Name '"+plateName+"' transformed to regexp '"+regexp+"'.");

		return Pattern.compile(regexp, Pattern.CASE_INSENSITIVE);
	}
	
	@Override
	public void parse(String headerLine) {
		
		log.debug("Header line: "+headerLine);
		reset();
		
		if (null == headerLine || 0 == headerLine.length()) {
			return;
		}
		
		String wellAdress = recognizePlate (headerLine);
		recognizeWellAdress (wellAdress);
		recognizePrimer (headerLine);
	}
	
	@Override
	protected void reset() {
		super.reset();
		this.plate = null;
		this.wellRow = 0;
		this.wellColumn = 0;
	}
	
	private String recognizePlate (String headerLine) {
		
		if (null == plates || 0 == plates.size()) {
			return null;
		}
		
		int plateIndx;
		for (plateIndx=0; plateIndx<plates.size(); plateIndx++) {
			Matcher m = regexps.get(plateIndx).matcher (headerLine);
			
			if (m.find()) {
				log.debug("Regexp '"+regexps.get(plateIndx)+"' matched '"+m.group()+"'");
				
				this.plate = plates.get(plateIndx);
				// return well address group
				return m.group(1);
			}
		}
		return null;
	}
	
	private void recognizeWellAdress (String wellAdress) {
		
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
		log.debug("Row in Well Adress '"+wellAdress+"' recognized as "+this.wellRow);
		
		int column = 0;
		while (startDigits.matcher(wa).find()) {
			char c = wa.charAt(0);
			wa = wa.substring(1);
			
			column = column * 10 + (c-'0');
		}
		this.wellColumn = column;
		log.debug("Column in Well Adress '"+wellAdress+"' recognized as "+this.wellColumn);
	}
}
