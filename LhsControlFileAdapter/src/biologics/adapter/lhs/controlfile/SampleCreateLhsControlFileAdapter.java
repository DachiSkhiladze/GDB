package biologics.adapter.lhs.controlfile;

import genedata.bx.adapter.entity.PlateWell;
import genedata.bx.adapter.plate.controlfile.v2.LhsCreateControlFile;
import genedata.bx.adapter.plate.controlfile.v2.LhsCreateControlFile.DestinationToSourceMapping.SourceWellContent;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Sample implementation for creating a LHS Control File
 * Note: This sample implementation is meant as an exemplification only 
 * (and hence is not recommended for production use).
 * <pre>
 * SourceWell,SourcePlate,SourceBarcode,DestWell,DestPlate,DestBarcode
 * A01,007ABC-001,BC001,A01,008ABC-001,BC004
 * B11,007ABC-003,BC003,A02,008ABC-001,BC004
 * </pre>
 */
public final class SampleCreateLhsControlFileAdapter implements LhsCreateControlFile, Serializable {
	private static final long serialVersionUID = 1L;
	private static final String delimiter = ",";
	
	// file content
	private StringBuilder fileContent = null;
	

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// no configuration needed in this example
	}

	@Override
	public void options(LhsCreateControlFileOptions options) {
		// no options set in this example
	}

	@Override
	public void perform(LhsCreateControlFileCallback callback) {
		this.fileContent = new StringBuilder();
		
		writeHeader();
		writeBody(callback.getMappings());
		
		callback.setMimeType("text/csv");
		callback.setFileContent(fileContent.toString());
	}
	
	
	/**
	 * <pre>SourceWell,SourcePlate,SourceBarcodei,DestWell,DestPlate,DestBarcode</pre>
	 */
	private void writeHeader() {
		 append("SourceWell") // e.g. A01
		.append("SourcePlate") // alias or qualified id
		.append("SourceBarcode")
		
		.append("DestWell") // e.g. B12
		.append("DestPlate") // alias or qualified id
		.append("DestBarcode", false)
		
		.newline();
	}
	
	private void writeBody(List<DestinationToSourceMapping> mappings) {
		// one mapping entry for each destination well
		for(DestinationToSourceMapping m : mappings) {
			writeEntry(m);
		}
	}

	/**
	 * Writes an entry/line for the given destination well mapping.
	 */
	private void writeEntry(DestinationToSourceMapping m) {
		PlateWell destWell = m.getDestinationPlateWell();
		
        for (SourceWellContent swc : m.getSourceWellContents()) {
			// in case the source isolate|cell line is present on multiple source wells 
			// of the source plate set, pick the 'first' one
			PlateWell srcWell = swc.getSourcePlateWellCandidates().get(0);
			write(srcWell, destWell);
		}
	}
	
	private void write(PlateWell srcWell, PlateWell destWell) {
		/*
		 * Well address as character representation, 
		 * e.g. Row: 2, Column: 6 => "B06"
		 */
		
		 append(srcWell.getWell().getWellAddress())
		.append(nvl(srcWell.getPlate().getAlias(), srcWell.getPlate().getQualifiedId()))
		.append(srcWell.getPlate().getBarcode())
		
		.append(destWell.getWell().getWellAddress())
		.append(nvl(destWell.getPlate().getAlias(), destWell.getPlate().getQualifiedId()))
		.append(destWell.getPlate().getBarcode(), false)
		
		.newline();
	}
	

	private SampleCreateLhsControlFileAdapter append(String str) {
		return append(str, true);
	}
	
	private SampleCreateLhsControlFileAdapter append(String str, boolean appendDelimiter) {
		fileContent.append(nvl(str));
		if(appendDelimiter) {
			fileContent.append(delimiter);
		}
		
		return this;
	}
	
	private SampleCreateLhsControlFileAdapter newline() {
		fileContent.append("\n");
		return this;
	}
	
	
	static boolean isEmpty(String s) {
		return null == s || 0 == s.trim().length();
	}
	
	static String nvl(String... strs) {
		String answer = "";
		
		if(null != strs) {
			for(String s : strs) {
				if(!isEmpty(s)) {
					answer = s;
					break;
				}
			}
		}
		
		return answer;
	}
}
