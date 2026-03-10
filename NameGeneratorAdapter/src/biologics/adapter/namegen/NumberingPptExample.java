package biologics.adapter.namegen;

import java.util.Map;

import genedata.bx.adapter.NameGenerator;
import genedata.bx.adapter.NumberingFactory;

/**
 * Sample implementation of a NameGenerator for a Ppt using NumberingFactory. 
 * 
 * Please note: This example is meant to demonstrate the mechanics of name generation only.
 * We do not suggest that the naming scheme implemented here is particularly meaningful. 
 */
public class NumberingPptExample implements NameGenerator {
	private static final long serialVersionUID = 1L;

	private String appName;

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		//	Query the configuration from table PARAMETER. 
		//	This implementation just asks for the name of the application.
		
		//	Instead of using a predefined parameter,
		//	clients could set up their own parameters to pass them to this adapter.
		appName= configuration.get("app_name"); // see table PARAMETER, key "app_name"
	}

	/**
	 * Generate name like this: "<application name>-Protein_<running number>"
	 */
	@Override
	public String generateName(Map<String, String> context, NumberingFactory numberFactory) {
		
		// The name space is made unique by prefixing it with the classname
		String nameSpace= this.getClass().getName() + "_" + appName;
		
		// ask for the next number in namespace
		// inspect database afterwards: select * from name_generation_numbering;
		int nextNumber= numberFactory.nextNumber(nameSpace);
		
		return appName + "-Protein_" + nextNumber;
	}

	@Override
	public String prefillName(Map<String, String> arg0) {
		// not needed
		return null;
	}

	@Override
	public boolean showNameToUserForEdit() {
		return false;
	}
}
