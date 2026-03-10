package biologics.adapter.fastahp;

import genedata.bx.adapter.FastaHeaderParser;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;

public abstract class AbstractRegExpParser implements FastaHeaderParser {

	private List<Pattern> groupChainPatterns = new LinkedList<Pattern>();
	private MatchResult matchResult;
	
	private static Logger log = Logger.getLogger(AbstractRegExpParser.class);

	
	public AbstractRegExpParser() throws PatternSyntaxException {
		for (String patStr : getPatternDefaultString()) {
			groupChainPatterns.add(Pattern.compile(patStr));
		}
		
		matchResult = null;
	}

	/**
	 * Initialise parser with these patterns
	 * @return list of patterns
	 */
	protected abstract List<String> getPatternDefaultString();
		

	/**
	 * A successful MatchResult or null
	 * @return MatchResult
	 */
	protected MatchResult getMatchResult() {
		return matchResult;
	}

	@Override
	public void parse(String arg0) {
		matchResult  = null;
		
		// stop with the first matching pattern
		for (Pattern pat : groupChainPatterns) {
			Matcher mat = pat.matcher(arg0);
			if (mat.find()) {
				log.debug("Using matcher pattern " + pat + " for header: " + arg0);
				
				matchResult = mat;	// save this Matcher as MatchResult
				return;				
			}
		}
		
		for (Pattern pat : groupChainPatterns) {
			log.info("No pattern match of '" + arg0 + "' with pattern: " + pat);
		}
	}


	/**
	 * Example (optional) SQL configuration:
	 * insert into parameter (id, user_specific, key, value) values(parameter_seq.nextval, 'N', 'fasta_header_group_chain_pattern_1', '^([\d.]+)([HL])()_');
	 * insert into parameter (id, user_specific, key, value) values(parameter_seq.nextval, 'N', 'fasta_header_group_chain_pattern_2', '^()([HL])(\d+)');
	 */
	@Override
	public void setConfiguration(Map<String, String> conf) {
		// check if there is pattern configuration
		final String keyType = "pattern";
		
		if (! (conf.containsKey(getPatternConfigurationKey(keyType, null)) 
			|| conf.containsKey(getPatternConfigurationKey(keyType, 1))))
			return;

		// clear the pattern list
		groupChainPatterns.clear();
		
		/*
		 * fill the groupChainPatterns list again
		 */
		
		// search value for key (without suffix)
		String key = getPatternConfigurationKey(keyType, null);
		
		if (conf.containsKey(key)) {
			String patStr = conf.get(key);
			addGroupChainPattern(patStr);
		}
		

		// search value for keys with suffixes "_1", "_2", "_3", etc.
		int counter = 1;
		key = getPatternConfigurationKey(keyType, counter);

		while (conf.containsKey(key)) {
			String patStr = conf.get(key);
			addGroupChainPattern(patStr);

			key = getPatternConfigurationKey(keyType, ++counter);
		}
		
	}


	private void addGroupChainPattern(String patStr) {
		try {
			groupChainPatterns.add(Pattern.compile(patStr));
			log.debug("Using pattern: " + patStr);
		} catch (PatternSyntaxException e) {
			log.warn("Setting RegExp pattern failed", e);
		}
	}


	/**
	 * .e.g. genedata_bdp_bl_sc_adapter_fastaHeaderParser_AbstractRegExpParser_pattern_1
	 * 
	 * @param whatKey 
	 * @param number
	 *  
	 * @return class name with key and number.
	 */
	protected String getPatternConfigurationKey(String whatKey, Integer number) {
		String key = getClass().getName().replace('.', '_');
		
		key += "_" + whatKey;
		
		if (number != null)
			key += "_" + number;
		
		return key;
	}

}
