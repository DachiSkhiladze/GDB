package biologics.adapter.plate.controlfile.v2;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.assay.Isolate;
import genedata.bx.adapter.entity.Plate;
import genedata.bx.adapter.entity.PlateWell;
import genedata.bx.adapter.entity.WellContentClass;
import genedata.bx.adapter.plate.controlfile.v2.RearrayControlFileAdapter;
import genedata.bx.adapter.plate.controlfile.v2.RearrayControlFileAdapterCallback;
import genedata.bx.adapter.plate.controlfile.v2.RearrayControlFileAdapterOptions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Produces a Biomek Hit Picking Rearray Control File in the following format: 
 * SourcePlateName, SourcePlateBC, SourceWell, AntibodyCloneName, Status (always true)
 * 
 * <div style="font-size:x-small">
 * Copyright 2019 Genedata AG. All Rights Reserved.
 * </div>
 */
public class BiomekHitPickingControlFileAdapter implements RearrayControlFileAdapter, Serializable{
	private static final long serialVersionUID = 1L;

	private static Logger log = Logger.getLogger(BiomekHitPickingControlFileAdapter.class);
	
	private static final String SEPARATOR = "\t";
	private static final String NEWLINE = System.getProperty("line.separator");
	private static final boolean DEFAULT_STATUS_VALUE = true;
	
	private int numberOfRows = 0;
	private int numberOfColumns = 0;
	private int skippedIsolatesCount = 0;
	private List<String> skippedIsolateNames = null;
	private Reporter reporter = null;
	private String pluralIsolateLabel;
	private StringBuilder content;
	
	enum Column {
		PLATE_NAME("SourcePlateName"),
		PLATE_BARCODE("SourcePlateBC"),
		WELL("SourceWell"),		
		ISOLATE_NAME("AntibodyCloneName"),
		STATUS("Status"), 
		;
		
		final String label;
		Column(String label) {
			this.label = label;
		}
	}
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {

	}

	@Override
	public void options(RearrayControlFileAdapterOptions options) {
		// No options available.
	}

	private void createHeader() {
		content.append(Column.PLATE_NAME.label);
		content.append(SEPARATOR);
		content.append(Column.PLATE_BARCODE.label);
		content.append(SEPARATOR);
		content.append(Column.WELL.label);
		content.append(SEPARATOR);
		content.append(Column.ISOLATE_NAME.label);
		content.append(SEPARATOR);
		content.append(Column.STATUS.label);
		content.append(NEWLINE);
	}
	
	private void initialize(RearrayControlFileAdapterCallback callback) {
		content = new StringBuilder();
		
		reporter = callback.getReporter();
		pluralIsolateLabel = callback.getLabelProvider().plural(Isolate.class);
		
		numberOfRows = callback.getNumberOfRows();
		numberOfColumns = callback.getNumberOfColumns();
		
		skippedIsolatesCount = 0;
		skippedIsolateNames = new ArrayList<String>();
		
		createHeader();
	}
	
	private Map<Long, List<PlateWell>> createPlateWellMappings(List<Plate> plates) {
		Map<Long, List<PlateWell>> result =
				new HashMap<Long, List<PlateWell>>();
		
		for (Plate p : plates) {			
			for (int row = 1; row <= numberOfRows; row++) {
				for (int col = 1; col <= numberOfColumns; col++) {
					try {
						PlateWell plateWell = p.getPlateWell(row, col);
						if (plateWell != null && plateWell.getWellContent() != null) {
							Long contentId = plateWell.getWellContent().getVisibleId();
							if (contentId > 0 ) {
								if (!result.containsKey(contentId)) {
									result.put(contentId, new ArrayList<PlateWell>());
								}
								result.get(contentId).add(plateWell);	
							}
						}
					} catch(Throwable t) { 
						log.error("Error while creating well mappings: " + t.getMessage(), t);
					}
				}
			}
		}
		
		return result;
	}
	
	private void addSkippedIsolate(WellContentClass isolate) {
		if(isolate == null) {
			return;
		}
		
		if(skippedIsolatesCount < 50) {
			String title = isolate.getQualifiedId();
			if(isolate.getAlias() != null
					&& !isolate.getAlias().isEmpty()) {
				title = title + " (" + isolate.getAlias() + ")";
			}
			skippedIsolateNames.add(title);
		}
		skippedIsolatesCount++;
	}
	
	private void report() {
		if(skippedIsolatesCount > 50) {
			skippedIsolateNames.add("... and " + (skippedIsolatesCount-50) + " more.");
		}
		if(skippedIsolateNames.isEmpty()) {
			return;
		}
		
		reporter.warn("The following #0 were skipped, as they were not found on the Plate Set: #1",
				pluralIsolateLabel,
				toString(skippedIsolateNames, ", "));
	}
	
	public static String toString(List<?> l, String separator) {
		StringBuilder sb = new StringBuilder();
        String sep = "";
        for (Object object : l) {
            sb.append(sep).append(object.toString());
            sep = separator;
        }
        return sb.toString();
	}
	
	private PlateWell getPlateWellFromList(List<PlateWell> plateWells) {
		PlateWell result = null;
		for(PlateWell pl : plateWells) {
			if(result == null) {
				result = pl;
			}
			// from first (oldest) plate 
			else if(pl.getPlate().getVisibleId() < result.getPlate().getVisibleId()) {
				result = pl;
			}
		}
		
		return result;
	}
	
	private String getStringForExport(String value) {
		if(value == null) {
			return "";
		}
		
		return value.replaceAll(SEPARATOR, " ");
	}
	
	private boolean createEntry(WellContentClass is, List<PlateWell> mappings) {
		PlateWell plateWell = getPlateWellFromList(mappings);
		if(plateWell == null) {
			return false;
		}
		
		content.append(getStringForExport(plateWell.getPlate().getAlias()));
		content.append(SEPARATOR);
		content.append(getStringForExport(plateWell.getPlate().getBarcode()));
		content.append(SEPARATOR);
		content.append(plateWell.getWell().getWellAddress());
		content.append(SEPARATOR);
		content.append(getStringForExport(is.getAlias()));
		content.append(SEPARATOR);
		content.append(DEFAULT_STATUS_VALUE);
		content.append(NEWLINE);
		return true;
	}
	
	class IsolateByIdSorter implements Comparator<WellContentClass> {
		@Override
		public int compare(WellContentClass is1, WellContentClass is2) {
			if(is1.getVisibleId() == null) {
				return 0;
			}
			
			return is1.getVisibleId().compareTo(is2.getVisibleId());
		}
	}
	
	@Override
	public int perform(RearrayControlFileAdapterCallback callback) {
		initialize(callback);
		
		List<Plate> plates = callback.getPlates();
		
		Map<Long, List<PlateWell>> plateWellMappings =
			createPlateWellMappings(plates);
		
		if (plateWellMappings.isEmpty()) {
			reporter.error("Nothing found in the Plate Set.");
			return 0;
		}
	
		List<? extends WellContentClass> pickedIsolates = new ArrayList<>(
			callback.getPickedWellContentEntities());
		Collections.sort(pickedIsolates, new IsolateByIdSorter());
	
		int numberOfEntries = 0;
		for (WellContentClass isolate : pickedIsolates) {
			Long visibleId = isolate.getVisibleId();
			if (visibleId == null || visibleId <= 0) {
				addSkippedIsolate(isolate);
			}
			else {
				if (plateWellMappings.containsKey(visibleId)
						&& createEntry(isolate, plateWellMappings.get(visibleId))) {
					numberOfEntries++;
				}
				else {
					addSkippedIsolate(isolate);
				}
			}
		}
		
		report();
		callback.setFileContent(content.toString());
		
		return numberOfEntries;
	}

}
