package biologics.sc.adapter.assay;

import genedata.bx.adapter.assay.AssayAttribute;
import genedata.bx.adapter.assay.AssayValue;
import genedata.bx.adapter.assay.AssayValueFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The implementation of the Assay Data Adapter interface for
 * the testing of parameter definitions.
 * 
 * 
 * @deprecated use V2 AssayData API instead
 */
@Deprecated
public class TestParameterAssayDataAdapter extends AbstractAssayDataAdapter {
	
	private final List<AssayValue> assayValues = new ArrayList<AssayValue>();
	
	/* (non-Javadoc)
	 * @see biologics.sc.adapter.assay.AbstractAssayDataAdapter#initialize()
	 */
	@Override
	public void initialize() {
		parameterRegistry.addSelectionListParameter("color", "Favorite Color", new String[] {"Red", "Green", "Blue"}, null);
		parameterRegistry.addSelectionListParameter("animal", "Favorite Pet", new String[] {"Mouse", "Cat", "Dog", "Pony", "Buffalo"}, "Cat");
		parameterRegistry.addSelectionListParameter("movie", "Favorite Movie", null, "Bambi");
		parameterRegistry.addTextBoxInputParameter("quote", "Favorite Quote", null);
		parameterRegistry.addTextAreaInputParameter("contact", "Contact information", "<Please enter your contact information>");
	}
	
	/* (non-Javadoc)
	 * @see biologics.sc.adapter.assay.AbstractAssayDataAdapter#perform(genedata.bx.adapter.assay.AssayValueFactory, java.util.List)
	 */
	@Override
	public List<AssayValue> perform(AssayValueFactory assayValueFactory, List<AssayAttribute> assayAttributes) {
		clear();
		
		addInfoMessage("You have selected the color: " + getParameterValue("color") );
		addInfoMessage("You would like to pet this animal: " + getParameterValue("animal") );
		addInfoMessage("Your favorite movie is: " + getParameterValue("movie") );
		addInfoMessage("You entered this quote: " + getParameterValue("quote") );
		addInfoMessage("You entered this contact information: " + getParameterValue("contact") );
		
		addWarningMessage("This adapter implementation does not generate any Assay Values.");
		addInfoMessage("Its sole purpose is to test the mechanism that allows the adapter to register custom input parameters.");
		
		return assayValues;
	}
}
