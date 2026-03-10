package biologics.adapter.namegen;

import java.util.Map;

import genedata.bx.adapter.NameGenerator;
import genedata.bx.adapter.NumberingFactory;

/**
 * Sample implementation of a NameGenerator for a Ppt. 
 * 
 * 
 * Please note: This example is meant to demonstrate the mechanics of name generation only.
 * We do not suggest that the naming scheme implemented here is particularly meaningful. 
 */
public class SimplePptExample implements NameGenerator{
	private static final long serialVersionUID = 1L;

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// this implementation does not use the configuration information
	}

	/**
	 * This sample implementation just takes whatever the user entered as name
	 * from the context and appends the String " [GENERATED]" 
	 */
	@Override
	public String generateName(Map<String, String> context, NumberingFactory numbering) {
		return context.get("ALIAS") + " [GENERATED]";
	}

	@Override
	public String prefillName(Map<String, String> arg0) {
		// not needed
		return null;
	}

	@Override
	public boolean showNameToUserForEdit() {
		return true;
	}
	
}
