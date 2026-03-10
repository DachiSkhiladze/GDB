package biologics.adapter.assay;

import genedata.bx.adapter.ParameterRegistry;
import genedata.bx.adapter.ParameterValue;
import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.assay.v2.AssayDataAdapter;
import genedata.bx.adapter.assay.v2.AssayDataCallback;
import genedata.bx.adapter.assay.v2.AssayDataOptions;
import genedata.bx.adapter.assay.v2.AssayValue;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This example demonstrates how custom input parameters are registered 
 * in Genedata Biologics, and how parameter values are retrieved.
 * 
 * The adapter actually does not process any assay data.
 * 
 */
public class CustomInputParameterExample implements AssayDataAdapter {

	// custom input parameter names:
	private static final String NAME = "name";
	private static final String SUCCESS = "success";
	private static final String QA = "qa";
	private static final String ANALYSES = "analyses";
	private static final String DATE = "date";
	private static final String DATE_TIME = "dateTime";
	private static final String COMMENT = "comment";
	
	// values
	private static String[] SUCCESS_MODE = {"Error", "Warning", "OK"};
	private static final String[] ANALYSES_NAMES = new String[] {"ELISA", "SPR", "FACS", "MS"};
	
	// state
	private Reporter reporter;

	@Override
	public void setConfiguration(Map<String, String> arg0) {
		// nothing to do
	}

	@Override
	public void options(AssayDataOptions options) {
		ParameterRegistry parameterRegistry = options.getParameterRegistry();

		// register custom input parameters
		parameterRegistry.addTextBoxInputParameter(  NAME,      "Name", "");
		parameterRegistry.addSelectionListParameter( SUCCESS,   "Success", SUCCESS_MODE, SUCCESS_MODE[2]);
		parameterRegistry.addCheckBoxParameter(      QA,        "Passed initial QA?", false);
		parameterRegistry.addMultiCheckBoxParameter( ANALYSES,  "Request additional analyses", ANALYSES_NAMES, new String[] {ANALYSES_NAMES[1], ANALYSES_NAMES[2]});
		parameterRegistry.addDateParameter(          DATE,      "Date", new Date());
		parameterRegistry.addDateTimeParameter(      DATE_TIME, "Date and Time", ZonedDateTime.now());
		parameterRegistry.addTextAreaInputParameter( COMMENT,   "Comment", "");
	}

	@Override
	public List<AssayValue> perform(AssayDataCallback callback) {
		reporter = callback.getReporter();
		
		// retrieve values for custom input parameters from Genedata Biologics 
		List<ParameterValue> parameterValues = callback.getParameterValueList();

		// map parameter values by name
		Map<String,ParameterValue> parameterValuesByName = parameterValues.stream().collect( Collectors.toMap( p->p.getName(), p->p));
		
		// retrieve and report values for custom input parameters
		reportParameterValue( NAME,      parameterValuesByName.get(NAME).getStringValue() );
		reportParameterValue( SUCCESS,   parameterValuesByName.get(SUCCESS).getStringValue() );
		reportParameterValue( QA,        parameterValuesByName.get(QA).getBooleanValue() );
		reportParameterValue( ANALYSES,  parameterValuesByName.get(ANALYSES).getSelectedStringValues() );
		reportParameterValue( DATE,      parameterValuesByName.get(DATE).getDateValue() );
		reportParameterValue( DATE_TIME, parameterValuesByName.get(DATE_TIME).getDateTimeValue() );
		reportParameterValue( COMMENT,   parameterValuesByName.get(COMMENT).getStringValue() );
		
		// we do not actually process any assay data, so there are no AssayValues to return
		return Collections.emptyList();
	}
	
	private void reportParameterValue(String name, Object value) {
		reporter.info( "#0 : #1", name, value);
	}
}
