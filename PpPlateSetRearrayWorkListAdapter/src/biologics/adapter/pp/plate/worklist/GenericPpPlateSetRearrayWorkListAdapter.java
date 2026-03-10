package biologics.adapter.pp.plate.worklist;

import genedata.bx.adapter.Identifiable;
import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.entity.PpPlate;
import genedata.bx.adapter.entity.PpPlateSet;
import genedata.bx.adapter.entity.PpPlateWell;
import genedata.bx.adapter.pp.plateset.PlateOperationException;
import genedata.bx.adapter.pp.plateset.PpPlateSetRearrayWorklistAdapter;
import genedata.bx.adapter.pp.plateset.PpPlateSetRearrayWorklistAdapterCallback;
import genedata.bx.adapter.pp.plateset.PpPlateSetRearrayWorklistAdapterOptions;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

/**
 * Produces a Rearray WorkList containing the Source PlateSet Well Content.
 *
 * File content example:
 *
 * <pre>
 * Source Plate Name	Source Plate Barcode	Source Well	Well Content ID	Status
 * All PPBs 	BC001 	A03 	PPB-9 	True
 * All PPBs 	BC001 	A04 	PPB-10 	True
 * </pre>
 * <div style="font-size:x-small"> Copyright 2024 Genedata AG. All Rights Reserved. </div>
 *
 * @since GDB-14.2
 */

public class GenericPpPlateSetRearrayWorkListAdapter implements PpPlateSetRearrayWorklistAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	private static Logger log = Logger.getLogger(GenericPpPlateSetRearrayWorkListAdapter.class);

	private static String delimiter = "\t";
	private static String file_extension =".tsv";
	private static Charset binary_file_charset = StandardCharsets.UTF_8;
	private static String mime_type ="text/tab-separated-values; charset=" + binary_file_charset;
	private static final String NEWLINE = System.getProperty("line.separator");

	private List<String> qIdsFromUser = new ArrayList<>();
	private Set<String> qIdsFromMatchedWell = new HashSet<>();
	private Reporter reporter;

	enum Column {
		SourcePlateName("Source Plate Name"),
		SourcePlateBarcode("Source Plate Barcode"),
		SourceWell("Well Address"),
		WellContentID("Well Content ID"),
		Status("Status");

		final String label;

		Column(String label) {
			this.label = label;
		}
	}

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		if("csv".equalsIgnoreCase(configuration.get("table_export_import_format"))) {
			delimiter = ",";
			file_extension =".csv";
			mime_type = "text/csv; charset=" + binary_file_charset;
		}
	}

	@Override
	public void options(PpPlateSetRearrayWorklistAdapterOptions options) {
	}

	@Override
	public void perform(PpPlateSetRearrayWorklistAdapterCallback callback) {
		Charset charset = callback.getCharset() == null ? StandardCharsets.UTF_8 : callback.getCharset();
		
		if (callback.getInputStream() !=null) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(callback.getInputStream(), charset));
			qIdsFromUser = reader
						.lines()
						.filter(Objects::nonNull)
						.filter(qid -> qid != null && !qid.isEmpty())
						.map(String::toUpperCase)
						.distinct()
						.collect(Collectors.toList());
		}
		
		reporter = callback.getInvocationContext().getReporter();
		
		if (qIdsFromUser.isEmpty()) {
			reporter.info("No entities have been provided for selection, processing full plate content.");
		} else {
			reporter.info("Found #0 unique entity ID#1 in the uploaded file: #2.",
						qIdsFromUser.size(),
						qIdsFromUser.size() >1 ? "s": "",
						getDisplayedItems(qIdsFromUser, 50));
		}
		
		List<WorkListItem> workList = new ArrayList<>();		

		WorkListItem header = new WorkListItem(
						Column.SourcePlateName.label,
						Column.SourcePlateBarcode.label,
						Column.SourceWell.label,
						Column.WellContentID.label,
						Column.Status.label);
		
		workList.add(header);

		List<WorkListItem> plateWellList = getPlateWellWorkList(callback);
		
		if (plateWellList.isEmpty()) {
			reporter.error("No #0s of the plate wells from source #1 #2 match any entity ID#3 in the uploaded file.",
						Column.WellContentID.label,
						callback.getLabelProvider().singular(PpPlateSet.class),
						callback.getSourcePlateSet().getQualifiedId(),
						qIdsFromUser.size() >1 ? "s": "");
			return;
		} else {
			if(qIdsFromUser.isEmpty()) {
				reporter.info("Source #0 #1 contains #2 filled plate well#3.",
						callback.getLabelProvider().singular(PpPlateSet.class),
						callback.getSourcePlateSet().getQualifiedId(),
						plateWellList.size(),
						plateWellList.size() >1 ? "s": "");
			} else {
				reporter.info("Source #0 #1 contains #2 plate well#3 matching an entity ID provided in the uploaded file.",
						callback.getLabelProvider().singular(PpPlateSet.class),
						callback.getSourcePlateSet().getQualifiedId(),
						plateWellList.size(),
						plateWellList.size() >1 ? "s": "");
			}
		}
		
		List<String> notMatchedQidsFromUser = qIdsFromUser.stream().filter(qid -> ! qIdsFromMatchedWell.contains(qid)).collect(Collectors.toList());
		if (!notMatchedQidsFromUser.isEmpty()) {
			reporter.error("#0 entity ID#1 provided in the uploaded file #2 not found in any plate well from source #3 #4: #5.",
						notMatchedQidsFromUser.size(),
						notMatchedQidsFromUser.size() >1 ? "s": "",
						notMatchedQidsFromUser.size() >1 ? "are": "is",
						callback.getLabelProvider().singular(PpPlateSet.class),
						callback.getSourcePlateSet().getQualifiedId(),
						getDisplayedItems(notMatchedQidsFromUser, 50));
			return;
		}
		
		workList.addAll(plateWellList);
		
		StringBuilder workListFileContent = new StringBuilder();
		workList.stream().forEach(item -> appendWorkListItem(workListFileContent, item));
		byte[] bytes = workListFileContent.toString().getBytes(binary_file_charset );
		callback.setFileContent(bytes);
		callback.setFileName(callback.getSourcePlateSet().getQualifiedId()+"_"+callback.getLabelProvider().singular(PpPlateSet.class).replaceAll("\\s+", "") + "RearrayWorklist" + file_extension);
		callback.setMimeType(mime_type);
	}

	private String getDisplayedItems(List<String> itemlList, int displaySize) {
		int size = itemlList.size();
		String displayedItems;
		if (size > displaySize) {
			List<String> partialItems = itemlList.subList(0, displaySize);
			displayedItems = String.join(", ", partialItems) + ", and " + (size-displaySize) + " more";
		} else {
			displayedItems = String.join(", ", itemlList) + "";
		}
		return displayedItems;
	}

	private List<WorkListItem> getPlateWellWorkList(PpPlateSetRearrayWorklistAdapterCallback callback) {	
		List<WorkListItem> plateWellList = new ArrayList<>();
		List<PpPlate> plates = callback.getSourcePlateSet().getPlates();
		for (PpPlate p : plates) {
			if(p == null) {
				continue;
			}
			int numberOfRows = p.getPlateFormat().getNumberOfRows();
			int numberOfColumns = p.getPlateFormat().getNumberOfColumns();
			for (int row = 1; row <= numberOfRows; row++) {
				for (int col = 1; col <= numberOfColumns; col++) {
					PpPlateWell plateWell;
					try {
						plateWell = callback.getPpPlateWell(p, row, col);
						if (plateWell == null) {
							continue;
						}
						String sourceWellLabel = callback.getWellAddressConverter().formatWellAddress(row, col);
						List<String> wellContentQids =  getWellContentQids(plateWell);
						if (wellContentQids.isEmpty()){
							continue;
						}
						boolean addToWorklist = false;
						if (qIdsFromUser.isEmpty()) { // process all plate wells
							 addToWorklist = true;
						} else {
							addToWorklist = wellContentQids.stream().anyMatch(qIdsFromUser::contains);
						}
						if (addToWorklist) {
							qIdsFromMatchedWell.addAll(wellContentQids);
							plateWellList.add(new WorkListItem(p.getAlias(), p.getBarcode(), sourceWellLabel, String.join(", ", wellContentQids)));
							log.debug("Adding QID " + String.join(", ", wellContentQids) + ".");
						}
					} catch (PlateOperationException e) {
						log.error("Cannot get plate well for " + p.getQualifiedId() + " on row " + row + " and on column " + col + ". " + e.getMessage());
					}
				}
			}
		}
		return plateWellList;
	}

	private List<String> getWellContentQids(PpPlateWell plateWell) {
		
		if (plateWell == null) {
			return Collections.emptyList();
		}
		
		Set<String> qids = new HashSet<>();

		addQualifiedId(qids, plateWell.getProteinExpressionBatch());
		addQualifiedId(qids, plateWell.getCellLineBatch());
		addQualifiedId(qids, plateWell.getProteinPurificationBatch());
		addQualifiedId(qids, plateWell.getVectorBatch());
		addQualifiedId(qids, plateWell.getAliquotGroup());

		return qids.stream()
				.filter(Objects::nonNull)
				.map(String::toUpperCase)
				.sorted()
				.collect(Collectors.toList());
	}

	private void addQualifiedId(Set<String> qids, Identifiable qualifiedIdProvider) {
		if (qualifiedIdProvider != null) {
			qids.add(qualifiedIdProvider.getQualifiedId());
		}
	}

	private void appendWorkListItem(StringBuilder workListFilecontent, WorkListItem item) {
		workListFilecontent.append(String.join( delimiter ,
				item.sourcePlateName,
				item.sourcePlateBarcode,
				item.sourceWell,
				item.wellContentQID,
				item.status) + NEWLINE
			);
	}
	
	private static class WorkListItem {
		String sourcePlateName;
		String sourcePlateBarcode;
		String sourceWell;
		String wellContentQID;
		String status = "True";

		public WorkListItem(String sourcePlateName, String sourcePlateBarcode, String sourceWell,
				String wellContentQID) {
			this.sourcePlateName = getStringForExport(sourcePlateName);
			this.sourcePlateBarcode = getStringForExport(sourcePlateBarcode);
			this.sourceWell = getStringForExport(sourceWell);
			this.wellContentQID = getStringForExport(wellContentQID);
		}

		public WorkListItem(String sourcePlateName, String sourcePlateBarcode, String sourceWell, String wellContentQID,
				String status) {
			this(sourcePlateName, sourcePlateBarcode, sourceWell, wellContentQID);
			this.status = status;
		}

		private String getStringForExport(String value) {
			if (value == null) {
				return "";
			}
			return value.replaceAll(delimiter, " ");
		}
	}
}
