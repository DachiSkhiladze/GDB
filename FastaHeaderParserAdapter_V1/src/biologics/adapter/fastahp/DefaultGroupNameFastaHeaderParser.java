package biologics.adapter.fastahp;

import genedata.bx.adapter.FastaHeaderParser;

import java.util.Arrays;
import java.util.List;
import java.util.regex.MatchResult;

import org.apache.log4j.Logger;

/**
 * Default implementation for all incarnations of the Fasta Header Parser using regular expressions.
 * (Specific incarnation may want to use their own version) 
 */
public class DefaultGroupNameFastaHeaderParser extends AbstractRegExpFastaHeaderParser implements FastaHeaderParser {

	private static Logger log = Logger.getLogger(DefaultGroupNameFastaHeaderParser.class);

	/**
	 * Default patterns, maybe overwritten by database PARAMETERs
	 * .e.g. genedata_bx_bl_adapter_fastahp_DefaultGroupNameFastaHeaderParser_pattern_1
	 */
	@Override
	protected List<String> getPatternDefaultString() {
		// get everything before and after H/L in the first word
		return Arrays.asList("(\\S*)([HL])(\\S*)");
	}
	

	@Override
	protected String getGroupName(MatchResult matchResult) {
		if (matchResult.groupCount() < 3) {
			log.debug("Ignoring match " + matchResult.group() + " since groupCount " + matchResult.groupCount() + " < 3");
			return null;
		}
	
		return matchResult.group(1) + matchResult.group(3);
	}


	@Override
	protected String getIdentifiedChainInfo(MatchResult matchResult) {
		if (matchResult.groupCount() < 3) {
			log.debug("Ignoring match " + matchResult.group() + " since groupCount " + matchResult.groupCount() + " < 3");
			return null;
		}
			
		Character chainId = matchResult.group(2).charAt(0);
		
		switch(chainId) {
		case 'H':
			return HEAVY_CHAIN_KEY;
		case 'L':
			return LIGHT_CHAIN_KEY;
		default:
			return null;
		}
	}

}
