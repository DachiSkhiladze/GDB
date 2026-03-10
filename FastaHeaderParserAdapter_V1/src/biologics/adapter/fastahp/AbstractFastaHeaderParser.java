package biologics.adapter.fastahp;

import genedata.bx.adapter.FastaHeaderParser;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class AbstractFastaHeaderParser implements FastaHeaderParser {

//	private static Logger log = Logger.getLogger(AbstractFastaHeaderParser.class);

	protected String chainInfo;

	protected final Set<String> lightChainPrimer = new HashSet<String>();
	protected final Set<String> heavyChainPrimer = new HashSet<String>();
	protected final Set<String> scFvPrimer = new HashSet<String>();

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.FastaHeaderParser#getIdentifiedChainInfo()
	 */
	@Override
	public String getIdentifiedChainInfo() {
		return this.chainInfo;
	}

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.GenericAdapter#setConfiguration(java.util.Map)
	 */
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		
		if (null == configuration) {
			return;
		}
		
		// reset
		lightChainPrimer.clear();
		heavyChainPrimer.clear();
		scFvPrimer.clear();
		
		initPrimerNames(lightChainPrimer, configuration.get(PROPERTY_KEY_LIGHT_CHAIN) );
		initPrimerNames(heavyChainPrimer, configuration.get(PROPERTY_KEY_HEAVY_CHAIN) );
		initPrimerNames(scFvPrimer, configuration.get(PROPERTY_KEY_SCFV) );
	}

	private void initPrimerNames (Set<String> store, String primerList) {
		
		if (null == primerList || 0 == primerList.trim().length()) {
			return;
		}
		for (String primer : primerList.trim().split("\\s+")) {
			store.add(primer);
		}
	}

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.FastaHeaderParser#parse(java.lang.String)
	 */
	@Override
	public abstract void parse(String headerLine);

	protected void reset() {
		this.chainInfo = null;
	}	

	protected void recognizePrimer(String headerLine) {
		
		if (matchPrimer(headerLine, lightChainPrimer)) {
			chainInfo = LIGHT_CHAIN_KEY;
		}
		else if (matchPrimer(headerLine, heavyChainPrimer)) {
			chainInfo = HEAVY_CHAIN_KEY;
		}
		else if (matchPrimer(headerLine, scFvPrimer)) {
			chainInfo = SCFV_KEY;
		}
		else {
			chainInfo = null;
		}
	}

	private boolean matchPrimer(String headerLine, Set<String> primerList) {
		
		if (null == headerLine || null == primerList) {
			return false;
		}
		for (String primer : primerList) {
			if (headerLine.contains(primer)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public String getGroupName(){
		return null;
	}
}
