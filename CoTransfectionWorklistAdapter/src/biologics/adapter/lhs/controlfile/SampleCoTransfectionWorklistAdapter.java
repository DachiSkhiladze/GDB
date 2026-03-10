package biologics.adapter.lhs.controlfile;

import genedata.bx.adapter.entity.ChainInfo;
import genedata.bx.adapter.entity.Isolate;
import genedata.bx.adapter.entity.PlateInfo;
import genedata.bx.adapter.entity.PlateWell;
import genedata.bx.adapter.entity.WellContentClass;
import genedata.bx.adapter.plate.controlfile.v2.CoTransfectionWorklistAdapter;
import genedata.bx.adapter.plate.controlfile.v2.CoTransfectionWorklistAdapter.Context;
import genedata.bx.adapter.plate.controlfile.v2.CoTransfectionWorklistAdapter.Supports;
import genedata.bx.adapter.plate.controlfile.v2.CoTransfectionWorklistAdapterCallback;
import genedata.bx.adapter.plate.controlfile.v2.CoTransfectionWorklistAdapterCallback.CoTransfectionSource;
import genedata.bx.adapter.plate.controlfile.v2.CoTransfectionWorklistAdapterCallback.CoTransfectionSourcesMapping;
import genedata.bx.adapter.plate.controlfile.v2.CoTransfectionWorklistAdapterOptions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sample implementation for creating a Co-Transfection Worklist File.
 * 
 *<pre>
Destination Antibody Clone Number	Source Plate Barcode	Source Well Address	Source Antibody Clone ID	Source Antibody Clone Name
1	bc	A01	CL-555	l2-1/1
1	bc	A02	CL-558	l2-2/1
2	bc	A03	CL-556	l2-1/2
2	bc	A04	CL-559	l2-2/2
</pre>
 */
@Supports({Context.TRIGGER_FROM_HIT_LIST, Context.TRIGGER_FROM_PLATE_SET})
public class SampleCoTransfectionWorklistAdapter implements CoTransfectionWorklistAdapter, Serializable {
	private static final long serialVersionUID = 1L;

	// file content
	private StringBuilder fileContent = null;

	private String singularIsolateLabel;
	
	private static final String delimiter = "\t";
	
	private Set<PlateInfo> platesWithoutBarcode = new HashSet<PlateInfo>();

	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
	}

	@Override
	public void options(CoTransfectionWorklistAdapterOptions options) {
	}

	@Override
	public void perform(CoTransfectionWorklistAdapterCallback callback) {
		this.fileContent = new StringBuilder();
		this.singularIsolateLabel = callback.getLabelProvider().singular(Isolate.class);
		
		writeHeader();
		List<CoTransfectionSourcesMapping> sourcePlateWells = callback.getSourceMappings();
		int number = sourcePlateWells.size();
		for (int i = 0; i < number; i++) {
			CoTransfectionSourcesMapping mapping = sourcePlateWells.get(i);
			if (validateCompleteness(callback, mapping)) {
				writeBlock(callback, mapping, i);
			}
		}
		
		callback.setMimeType("text/tsv");
		callback.setFileContent(fileContent.toString().getBytes());
	}
	
	private Map<ChainInfo, PlateWell> map(
			CoTransfectionWorklistAdapterCallback callback,
			CoTransfectionSourcesMapping mapping) {

		List<CoTransfectionSource> coTransfectionSources = mapping.getCoTransfectionSources();
		Map<ChainInfo, PlateWell> map = new LinkedHashMap<ChainInfo, PlateWell>();
		Set<WellContentClass> uniqueSet = new HashSet<WellContentClass>();
		
		for (CoTransfectionSource  coTransfectionSource: coTransfectionSources) {
			PlateWell plateWell = coTransfectionSource.getPlateWell();
			ChainInfo plateSetChainInfo = coTransfectionSource.getPlateSetChainInfo();
			if (plateWell == null || plateSetChainInfo == null) {
				continue;
			}
			if (map.keySet().contains(plateSetChainInfo)) {
				callback.getReporter().warn("For common ancestor #0 #1, more than one descendant found for Chain #2.", 
						singularIsolateLabel,
						mapping.getCommonAncestorIsolate().getQualifiedId(),
						plateSetChainInfo.getLabel());
			} else {
				if (!uniqueSet.contains(plateWell.getWellContent())) {
					uniqueSet.add(plateWell.getWellContent());
					map.put(plateSetChainInfo, plateWell);
				} else {
					callback.getReporter().warn("For common ancestor #0 #1, descendant #2 for Chain #3 is excluded as it has been added to another chain.", 
							singularIsolateLabel,
							mapping.getCommonAncestorIsolate().getQualifiedId(),
							plateWell.getWellContent().getQualifiedId(),
							plateSetChainInfo.getLabel());
				}
			}
		}
		return map;
	}

	private boolean validateCompleteness(
			CoTransfectionWorklistAdapterCallback callback,
			CoTransfectionSourcesMapping mapping) {

		Set<ChainInfo> destinationIsolateChainInfos = callback.getDestinationIsolateChainInfos();
		Map<ChainInfo, PlateWell> map = map(callback, mapping);
		Isolate commonAncestorIsolate = mapping.getCommonAncestorIsolate();

		if (!map.isEmpty()) {
			Set<ChainInfo> keySet = map.keySet();
			if (equals(destinationIsolateChainInfos, keySet)) {
				return true;
			}else {
				List<ChainInfo> missingChains = new ArrayList<ChainInfo>(destinationIsolateChainInfos);
				missingChains.removeAll(keySet);
				callback.getReporter().warn("Common ancestor #0 #1 is not complete. Missing: #2.", 
						singularIsolateLabel,
						commonAncestorIsolate.getQualifiedId(),
						missingChains.stream().map(c->c.getLabel()).collect(Collectors.joining(", ")));
				return false;
			}
		} else {
			callback.getReporter().warn("Could not verify if common ancestor #0 #1 is complete due to lack of Chain Info.", 
					singularIsolateLabel,
					commonAncestorIsolate.getQualifiedId());
			return true;	
		}
	}
	
	private static <T> boolean equals(Collection<T> coll1, Collection<T> coll2) {
		return  ! new ArrayList<T>(coll1).retainAll(coll2)
				&&
				! new ArrayList<T>(coll2).retainAll(coll1);
	}
	
	private void writeHeader() {
		 append("Destination " + singularIsolateLabel + " Number")
		.append("Source Plate Barcode")
		.append("Source Well Address") // e.g. A01
		.append("Source " + singularIsolateLabel + " ID") 
		.append("Source " + singularIsolateLabel + " Name", false)
		;
	}
	
	private void writeBlock(CoTransfectionWorklistAdapterCallback callback, CoTransfectionSourcesMapping mapping, int number) {
		// one mapping entry for each destination well
		List<CoTransfectionSource> coTransfectionSources = mapping.getCoTransfectionSources();
		for(CoTransfectionSource coTransfectionSource : coTransfectionSources) {
			PlateWell plateWell = coTransfectionSource.getPlateWell();
			writeRow(callback, plateWell, number + 1 );
		}
	}
	
	private void writeRow(CoTransfectionWorklistAdapterCallback callback, PlateWell srcWell, int number ) {
		/*
		 * Well address as character representation, 
		 * e.g. Row: 2, Column: 6 => "B06"
		 */
		PlateInfo plate = srcWell.getPlate();
		String barcode = plate.getBarcode();
		
		if (isEmpty(barcode)) {
			if (!platesWithoutBarcode.contains(plate)) {
				platesWithoutBarcode.add(plate);
				callback.getReporter().error("No barcode found for Plate #0.", 
						 plate.getQualifiedId());
				return;
			}
		}
		newline()
		.append(Integer.toString(number))
		.append(barcode)
		.append(srcWell.getWell().getWellAddress())
		.append(srcWell.getWellContent().getQualifiedId())
		.append(srcWell.getWellContent().getAlias(), false)		
		;
	}
	
	private SampleCoTransfectionWorklistAdapter newline() {
		fileContent.append("\n");
		return this;
	}
	
	private SampleCoTransfectionWorklistAdapter append(String str) {
		return append(str, true);
	}
	
	private SampleCoTransfectionWorklistAdapter append(String str, boolean appendDelimiter) {
		fileContent.append(nvl(str));
		if(appendDelimiter) {
			fileContent.append(delimiter);
		}
		return this;
	}
	
	private static String nvl(String... strs) {
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
	
	static boolean isEmpty(String s) {
		return null == s || 0 == s.trim().length();
	}
}
