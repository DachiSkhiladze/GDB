package biologics.adapter.ct;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.ct.CustomToolAdapter;
import genedata.bx.adapter.ct.CustomToolAdapter.SelectionContext;
import genedata.bx.adapter.ct.CustomToolAdapter.Supports;
import genedata.bx.adapter.ct.CustomToolCallback;
import genedata.bx.adapter.ct.CustomToolOptions;
import genedata.bx.adapter.entity.EntityReference;
import genedata.bx.adapter.ws.InternalWebServiceInvoker;
import genedata.bx.adapter.ws.InternalWebServiceInvoker.HttpMethod;
import genedata.bx.adapter.ws.InternalWebServiceInvoker.Request;
import genedata.bx.adapter.ws.InternalWebServiceInvoker.Response;
import genedata.bx.adapter.ws.InternalWebServiceInvoker.WebServiceInvocationException;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple custom tool adapter exemplifying the use of internal web services to retrieve 
 * information from Genedata Biologics. The web service invoker is available everywhere
 * where the context is available.
 * <p>
 * In this example, the Sequence Web Service is queried for the sequence information of all 
 * selected PPT entities.
 *
 * <div style="font-size:x-small">
 * Copyright 2021 Genedata AG. All Rights Reserved.
 * </div>
 */
@Supports(SelectionContext.REQUIRE_SELECTION)
public class SimpleCustomToolAdapterInvokeWebService implements CustomToolAdapter {
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// nothing to do 
	}

	@Override
	public void options(CustomToolOptions options) {
		// at this point in time no selection is available, but the validator is invoked with the
		// selected entities available, so we do the web service call in the validator
		
		options.setOptionalValidator(ctx -> {
			String query = prepareQuery(ctx.getEntities());
			ctx.getReporter().info("Query: #0", query);
			
			InternalWebServiceInvoker wsInvoker = ctx.getInternalWebServiceInvoker();
			Request request = wsInvoker.createRequest(HttpMethod.GET, query);

			try {
				Response response = wsInvoker.invoke(request);
				processResponse(ctx.getReporter(), response, query);
				
			} catch (WebServiceInvocationException e) {
				ctx.getReporter().error(e.getMessage());
			}
			
			ctx.getReporter().error("Stay on page for this sample implementation.");
			return false;
		});
	}
	
	@Override
	public void perform(CustomToolCallback biologics) {
		// method is never called because validator in options method returns false
	}

	private String prepareQuery(Stream<EntityReference> entitiesAsStream) {
		String entityIds = entitiesAsStream
				.map(EntityReference::getQualifiedId)
				.collect(Collectors.joining(","));
		
		return "/sequence/fasta/aa/qid/" + entityIds;
	}
	
	private void processResponse(Reporter reporter, Response response, String query) throws WebServiceInvocationException {
		// check whether the web service reported an error
		if (response.getStatusCode() != 200) {
			reporter.error("Error occurred during sequence retrieval for query: #0 : #1", query, response.getString());
		}
		else { // 200 OK
			// in a real world implementation, we would use the retrieved data and pass it on to an external tool
			// here we just report it to the user interface 
			reporter.info(response.getString());
		}
	}
}
