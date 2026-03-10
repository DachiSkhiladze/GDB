/**
 * 
 */
package biologics.adapter.fastahp.isolate;

import genedata.bx.adapter.assay.Isolate;

import org.apache.log4j.Logger;

public class DefaultPlateFreeIsolateFastaHeaderParser extends AbstractIsolatedBasedFastaHeaderParser{

	private static Logger log = Logger.getLogger(DefaultPlateFreeIsolateFastaHeaderParser.class);

	private Isolate isolate;

	@Override
	public boolean handlesPlateFreeIsolates() {
		return true;
	}

	@Override
	public Isolate getIdentifiedIsolate() {
		return this.isolate;
	}

	@Override
	public void parse(String headerLine) {
		log.debug("parse, header line: " + headerLine);
		reset();
		
		if (null == headerLine || 0 == headerLine.length()) {
			return;
		}
		
		recognizeIsolate(headerLine);
		recognizePrimer(headerLine);
	}

	/**
	 * Retrieves the Isolate by name or qualified identifier (e.g. 'CL-3').
	 * It is assumed that the Isolate name is in the first (whitespace-separated)
	 * part of the header line. It may be postfixed by the primer information
	 * (separated by an underscore).
	 * <p>
	 * Example header lines:
	 * <ul>
	 * <li>WXYZ-006-H08-19-1_BioB</li>
	 * <li>CL-3_VLup.ab1   1502     28    814  ABI</li>
	 * </ul>
	 * @param headerLine The header line.
	 */
	protected void recognizeIsolate(String headerLine) {
		
		String[] items = headerLine.split("\\s+");
		String firstItem = items[0];
		
		firstItem = removeCounterAppendix(firstItem);
		
		int pos = firstItem.lastIndexOf('_');
		
		String isolateName;
		if (pos > 0) {
			isolateName = firstItem.substring(0, pos);
		}
		else {
			isolateName = firstItem;
		}
		
		isolate = getIsolate(isolateName);
	}

	@Override
	protected void reset() {
		super.reset();
		this.isolate = null;
	}

	/**
	 * Removes the counter appendix from a string in the
	 * form of '<part A>_<part B>_<integer number>'.
	 * Example: The string 'BB_VL_1' will be shortened
	 * to 'BB_VL'.
	 * 
	 * @param string The string of interest.
	 * @return The string without counter appendix.
	 */
	private String removeCounterAppendix(String string) {
		log.debug("removeCounterAppendix, string: " + string);
		
		String answer = string;
		
		int pos1 = string.indexOf('_');
		int pos2 = string.lastIndexOf('_');
		
		if ((pos1 > -1) && (pos1 != pos2) && (string.length() > pos2 + 1)) {
			String lastPart = string.substring(pos2 + 1);
			
			try {
				Integer.parseInt(lastPart);
				
				answer = string.substring(0, pos2);
				
				log.debug("Removed the counter appendix '" + string.substring(pos2) + "'.");
			}
			catch (Exception e) {
				// ignored
			}
		}
		
		return answer;
	}
}
