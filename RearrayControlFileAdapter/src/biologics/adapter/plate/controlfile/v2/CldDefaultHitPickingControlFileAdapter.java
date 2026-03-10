package biologics.adapter.plate.controlfile.v2;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.entity.Plate;
import genedata.bx.adapter.entity.WellContentClass;
import genedata.bx.adapter.plate.controlfile.v2.RearrayControlFileAdapter;
import genedata.bx.adapter.plate.controlfile.v2.RearrayControlFileAdapterCallback;
import genedata.bx.adapter.plate.controlfile.v2.RearrayControlFileAdapterOptions;
import genedata.bx.adapter.util.WellAddressConverter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Produces a CLD Hit Picking Rearray Control File in the following format:
 *  
 * Plate Barcode<tab>Well Address<tab>Picking (y/n)<tab>Cell Line ID
 * 
 * <div style="font-size:x-small">
 * Copyright 2015 Genedata AG. All Rights Reserved.
 * </div>
 */
public class CldDefaultHitPickingControlFileAdapter implements RearrayControlFileAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	private static Logger log = Logger.getLogger(CldDefaultHitPickingControlFileAdapter.class);
	
	private static final String SEPARATOR_PARAMETER = "cld_adapter_export_import_separator";
	private static final String DEFAULT_SEPARATOR = "\t";
	private static final char NEWLINE = '\n';
	private static final char DEFAULT_STATUS_VALUE = 'y';
	
	private Map<String, String> configuration = null;
	private String separator = DEFAULT_SEPARATOR;
	private int numberOfRows = 0;
	private int numberOfColumns = 0;
	private int skippedCellLineCount = 0;
	private List<String> skippedCellLineNames = null;
	private Reporter reporter = null;
	private StringBuilder content;
	private WellAddressConverter wellAddressConverter;
	
	private enum Column {
		barcode("Plate Barcode"),
		wellAddress("Well Address"),
		pickingStatus("Picking (y/n)"),
		cellLineId("Cell Line ID");
		
		private String defaultColumnName;
		Column(String defaultColumnName) {
			this.defaultColumnName = defaultColumnName;
		}
		
		String getColumnName(Map<String, String> configuration) {
			String configKey = String.format("%s.%s",
					CldDefaultHitPickingControlFileAdapter.class.getSimpleName(),
					name());
			String fromConfig = configuration.get(configKey);
			return fromConfig != null ? fromConfig : defaultColumnName;
		}
	}
	
	private String getColumnName(Column c) {
		return c.getColumnName(configuration);
	}
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		this.configuration = configuration;
		
		String separator = configuration.get(SEPARATOR_PARAMETER);
		if (separator != null) {
			separator = separator.trim();
			if (separator.length() > 0) {
				this.separator = separator;
			}
		}
	}

	@Override
	public void options(RearrayControlFileAdapterOptions arg0) {
		// No options available.
	}

	@Override
	public int perform(RearrayControlFileAdapterCallback callback) {
		
		initialize(callback);
		
		List<Plate> plates = callback.getPlates();
		
		validateBarcodes(plates);
		
		Map<Long, List<PlateWellMapping>> plateWellMappings =
			createPlateWellMappings(plates);
		
		if (plateWellMappings.isEmpty()) {
			reporter.error("No Cell Lines found in the Plate Set.");
			return 0;
		}
	
		List<? extends WellContentClass> pickedCellLines = new ArrayList<>(
			callback.getPickedWellContentEntities());
		Collections.sort(pickedCellLines, new CellLineByIdSorter());
	
		int numberOfEntries = 0;
		for (WellContentClass cellLine : pickedCellLines) {
			Long visibleId = cellLine.getVisibleId();
			if (visibleId == null || visibleId <= 0) {
				addSkippedCellLine(cellLine);
			}
			else {
				if (plateWellMappings.containsKey(visibleId)
						&& createEntry(cellLine, plateWellMappings.get(visibleId))) {
					numberOfEntries++;
				}
				else {
					addSkippedCellLine(cellLine);
				}
			}
		}
		
		report();
		callback.setFileContent(content.toString());
		
		return numberOfEntries;
	}

	private boolean validateBarcodes(List<Plate> plates) {
		boolean valid = true;
		for (Plate plate : plates) {
			String barcode = plate.getBarcode();
			if (barcode == null || barcode.length() == 0) {
				valid = false;
				reporter.error("No barcode found for " + plate.getQualifiedId());				
			}
		}
		return valid;
	}
	
	class CellLineByIdSorter implements Comparator<WellContentClass> {
		@Override
		public int compare(WellContentClass cl1, WellContentClass cl2) {
			if (cl1.getVisibleId() == null) {
				return 0;
			}
			
			return cl1.getVisibleId().compareTo(cl2.getVisibleId());
		}
	}
	
	class PlateWellMapping {	
		PlateWellMapping(Long cellLineId, Plate plate, int row, int column) {
			this.cellLineId = cellLineId;
			this.plate = plate;
			this.row = row;
			this.column = column;
		}
		
		Long cellLineId;
		Plate plate;
		int row;
		int column;
	}
	
	public String getFileContent() {
		return content.toString();
	}

	public void initialize(RearrayControlFileAdapterCallback callback) {
		content = new StringBuilder();
		
		reporter = callback.getReporter();
		
		numberOfRows = callback.getNumberOfRows();
		numberOfColumns = callback.getNumberOfColumns();
		
		skippedCellLineCount = 0;
		skippedCellLineNames = new ArrayList<String>();
		
		wellAddressConverter = callback.getWellAddressConverter();
		
		createHeader();
	}


	public void setPlateDimensions(int numberOfRows, int numberOfColumns) {
		this.numberOfRows = numberOfRows;
		this.numberOfColumns = numberOfColumns;
	}
	
	
	private void addSkippedCellLine(WellContentClass cellLine) {
		if (cellLine == null) {
			return;
		}
		
		if (skippedCellLineCount < 50) {
			String title = cellLine.getQualifiedId();
			if(cellLine.getAlias() != null
					&& !cellLine.getAlias().isEmpty()) {
				title = title + " (" + cellLine.getAlias() + ")";
			}
			skippedCellLineNames.add(title);
		}
		skippedCellLineCount++;
	}
	
	private void report() {
		if (skippedCellLineCount > 50) {
			skippedCellLineNames.add("... and " + (skippedCellLineCount-50) + " more.");
		}
		if (skippedCellLineNames.isEmpty()) {
			return;
		}
		
		reporter.warn("The following entities were skipped, as they were not found in the Plate Set: #0", 
					  toString(skippedCellLineNames, ", "));
	}
	
	private void createHeader() {
		content.append(getColumnName(Column.barcode));
		content.append(separator);
		content.append(getColumnName(Column.wellAddress));
		content.append(separator);
		content.append(getColumnName(Column.pickingStatus));
		content.append(separator);
		content.append(getColumnName(Column.cellLineId));
		content.append(NEWLINE);
	}
	
	private boolean createEntry(WellContentClass cellLine, List<PlateWellMapping> mappings) {
		PlateWellMapping mapping = getMappingFromList(mappings);
		if(mapping == null) {
			return false;
		}
		
		content.append(getStringForExport(mapping.plate.getBarcode()));
		content.append(separator);
		content.append(wellAddress(mapping.row, mapping.column));
		content.append(separator);
		content.append(DEFAULT_STATUS_VALUE);
		content.append(separator);
		content.append(getStringForExport(cellLine.getQualifiedId()));
	
		content.append(NEWLINE);
		return true;
	}
	
	private String getStringForExport(String value) {
		if(value == null) {
			return "";
		}
		
		return value.replaceAll(separator, " ");
	}
	
	private PlateWellMapping getMappingFromList(List<PlateWellMapping> mappings) {
		PlateWellMapping result = null;
		for(PlateWellMapping m : mappings) {
			if(result == null) {
				result = m;
			}
			// from first (oldest) plate 
			else if(m.plate.getVisibleId() < result.plate.getVisibleId()) {
				result = m;
			}
		}
		
		return result;
	}
	
	private Map<Long, List<PlateWellMapping>> createPlateWellMappings(List<Plate> plates) {
		Map<Long, List<PlateWellMapping>> result =
				new HashMap<Long, List<PlateWellMapping>>();
		
		for (Plate p : plates) {			
			for (int row = 1; row <= numberOfRows; row++) {
				for (int col = 1; col <= numberOfColumns; col++) {
					try {
						Long cellLineId = p.getWell(row, col);
						if (cellLineId != null && cellLineId > 0) {
							if (!result.containsKey(cellLineId)) {
								result.put(cellLineId, new ArrayList<PlateWellMapping>());
							}
							result.get(cellLineId).add(new PlateWellMapping(
											cellLineId,
											p,
											row,
											col));
						}
					} catch(Throwable t) { 
						log.error("Error while creating well mappings: " + t.getMessage(), t);
					}
				}
			}
		}
		
		return result;
	}
	
	
	private String wellAddress(int row, int col) {
		return String.format("%s%d", wellAddressConverter.formatRowRepresentation(row), col);
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

}

