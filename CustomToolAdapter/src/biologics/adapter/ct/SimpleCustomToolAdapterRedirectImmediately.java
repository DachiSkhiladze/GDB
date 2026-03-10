package biologics.adapter.ct;

import genedata.bx.adapter.Module;
import genedata.bx.adapter.ct.CustomToolAdapter;
import genedata.bx.adapter.ct.CustomToolAdapter.SelectionContext;
import genedata.bx.adapter.ct.CustomToolAdapter.Supports;
import genedata.bx.adapter.ct.CustomToolCallback;
import genedata.bx.adapter.ct.CustomToolOptions;
import genedata.bx.adapter.entity.EntityReference;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple custom tool adapter exemplifying how a redirect URL can be assembled 
 * based on a predefined base URL stored in the parameters table
 * and a list of qualified IDs of all entities selected by the user when triggering
 * the custom tool adapter.
 *
 * <div style="font-size:x-small">
 * Copyright 2021 Genedata AG. All Rights Reserved.
 * </div>
 */
@Supports(value = SelectionContext.REQUIRE_SELECTION, modules = {Module.PP, Module.SC})
public class SimpleCustomToolAdapterRedirectImmediately implements CustomToolAdapter {
	
	private final static String BASE_URL_CONFIG = "simple_custom_tool_adapter_redirect_base_url";
	private String redirectBaseUrl = null;
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// Retrieve the base URL from the configuration
		redirectBaseUrl = configuration.getOrDefault(BASE_URL_CONFIG, null);
	}
	
	@Override
	public void options(CustomToolOptions options) {
		options.setOptionalValidator(context -> {
			if (null == redirectBaseUrl) {
				context.getReporter().error("Redirect cannot be performed because base URL is missing. Please contact your system administrator.");
				return false;
			}
			return true;
		});
	}
	
	private String concatIds(Stream<EntityReference> entitiesAsStream) {
		return entitiesAsStream
			.map(EntityReference::getQualifiedId)
			.collect(Collectors.joining(","));
	}
	
	private String paramPair(String name, String value) {
		String encodedValue;
		
		try {
			encodedValue = URLEncoder.encode(value, "UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			encodedValue = e.getMessage();
		}
		
		return String.format("%s=%s", name, encodedValue);
	}
	
	@Override
	public void perform(CustomToolCallback biologics) {
		String uri = redirectBaseUrl
				+ "?" + paramPair("ids", concatIds(biologics.getContext().getEntities()))
				+ "&" + paramPair("originURI", biologics.getContext().getOriginURI());
		
		biologics
			.viaImmediateRedirect()
			.setRedirectURI(uri)
			.setOpenInNewTab(true);
	}
}
