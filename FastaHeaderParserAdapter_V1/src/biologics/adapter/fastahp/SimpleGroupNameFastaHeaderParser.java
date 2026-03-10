package biologics.adapter.fastahp;

import genedata.bx.adapter.FastaHeaderParser;

import org.apache.log4j.Logger;

public class SimpleGroupNameFastaHeaderParser extends AbstractFastaHeaderParser implements FastaHeaderParser {
	private static Logger log = Logger.getLogger(SimpleGroupNameFastaHeaderParser.class);

	private String group;

	private static final String LIGHT_CHAIN_GROUPING = "L";
	
	private static final String HEAVY_CHAIN_GROUPING = "H";
	
	@Override
	protected void reset() {
		super.reset();
		group = null;
	}
	
	@Override
	public void parse(String headerLine) {
		log.debug("Header line: " + headerLine);
		reset();

		if (null == headerLine || 0 == headerLine.length()) {
			return;
		}
		// group parsing
		String chainString;
		if (headerLine.contains(LIGHT_CHAIN_GROUPING)) {
			chainInfo = LIGHT_CHAIN_KEY;
			chainString = LIGHT_CHAIN_GROUPING;
		} else if (headerLine.contains(HEAVY_CHAIN_GROUPING)){
			chainInfo = HEAVY_CHAIN_KEY;
			chainString = HEAVY_CHAIN_GROUPING;
		} else {
			return;
		}
		
		String[] split = headerLine.split(chainString);
		if (split.length == 0) {
			return;
		}
		
		group = split[0];
		if (group.startsWith(">")) {
			group = group.substring(1);
		}
	}

	@Override
	public String getGroupName() {
		return group;
	}

}
