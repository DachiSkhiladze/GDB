package biologics.adapter.ct;

import genedata.bx.adapter.ct.CustomToolAdapter;
import genedata.bx.adapter.ct.CustomToolCallback;
import genedata.bx.adapter.ct.CustomToolOptions;
import genedata.bx.adapter.entity.EntityReference;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple custom tool adapter exemplifying how a file can be created which the user 
 * may immediately download.
 *
 * <div style="font-size:x-small">
 * Copyright 2021 Genedata AG. All Rights Reserved.
 * </div>
 */
public class SimpleCustomToolAdapterDownloadImmediately implements CustomToolAdapter {
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// nothing to do
	}
	
	@Override
	public void options(CustomToolOptions options) {
		// nothing to do
	}
	
	private String concatIds(Stream<EntityReference> entitiesAsStream) {
		return entitiesAsStream
			.map(EntityReference::getQualifiedId)
			.collect(Collectors.joining(","));
	}
	
	@Override
	public void perform(CustomToolCallback biologics) {
		biologics.viaImmediateFileDownload().setFileProvider((ctx, file) -> {
			final String ids = concatIds(ctx.getEntities());
			
			file.setBytes(ids.getBytes(Charset.forName("UTF-8")));
			file.setFileName("selectedEntities.txt");
			file.setMimeType("text/plain");
			
			return true;
		});
	}
}
