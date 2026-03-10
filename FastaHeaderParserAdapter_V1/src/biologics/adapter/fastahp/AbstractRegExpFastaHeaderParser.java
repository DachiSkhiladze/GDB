package biologics.adapter.fastahp;

import genedata.bx.adapter.FastaHeaderParser;

import java.util.regex.MatchResult;

public abstract class AbstractRegExpFastaHeaderParser extends AbstractRegExpParser implements FastaHeaderParser {
	
	@Override
	public String getGroupName() {
		MatchResult matchResult = getMatchResult();
		if (matchResult == null) return null;
		
		return getGroupName(matchResult);
	}

	abstract protected String getGroupName(MatchResult matchResult);


	@Override
	public String getIdentifiedChainInfo() {
		MatchResult matchResult = getMatchResult();
		if (matchResult == null) return null;
		
		return getIdentifiedChainInfo(matchResult);
	}

	abstract protected String getIdentifiedChainInfo(MatchResult matchResult);

}
