package biologics.adapter.namegen;

import genedata.bx.adapter.NameGenerator;
import genedata.bx.adapter.NumberingFactory;

import java.util.Map;

/**
 * Sample implementation of a NameGenerator for an AntigenBatch using the NumberingFactory. 
 * 
 */
public class NumberingAntigenBatchExample implements NameGenerator {
	private static final long serialVersionUID = 1L;

	// according to the name generation documentation
	private final String ANTIGEN_ALIAS = "ANTIGEN_ALIAS";

	@Override
	public void setConfiguration(Map<String, String> arg0) {
		// this implementation does not use the configuration information
	}

	@Override
	public String prefillName(Map<String, String> arg0) {
		// not required
		return "";
	}

	@Override
	public boolean showNameToUserForEdit() {
		return false;
	}

	/**
	 * Generates the name by extending the antigen alias with a running number.
	 */
	@Override
	public String generateName(Map<String, String> context,	NumberingFactory numbering) {
		String antigenAlias = context.get(ANTIGEN_ALIAS);
		int nextNumber = numbering.nextNumber(antigenAlias);
		String numberString = String.format("%d", nextNumber);
		return antigenAlias + "." + numberString;
	}

}
