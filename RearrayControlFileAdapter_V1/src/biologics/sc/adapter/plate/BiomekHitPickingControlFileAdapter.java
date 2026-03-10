package biologics.sc.adapter.plate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.assay.Isolate;
import genedata.bx.adapter.assay.IsolateInformationProvider;
import genedata.bx.adapter.plate.Plate;
import genedata.bx.adapter.plate.RearrayControlFileAdapter;

/**
 * Produces a Biomek Hit Picking Rearray Control File in the following format: 
 * SourcePlateName, SourcePlateBC, SourceWell, AntibodyCloneName, Status (always true)
 * 
 * <div style="font-size:x-small">
 * Copyright 2013 Genedata AG. All Rights Reserved.
 * </div>
 */
public class BiomekHitPickingControlFileAdapter implements RearrayControlFileAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	private static Logger log = Logger.getLogger(BiomekHitPickingControlFileAdapter.class);
	
	private static final char SEPARATOR = '\t';
	private static final char NEWLINE = '\n';
	private static final boolean DEFAULT_STATUS_VALUE = true;
	
	private static final String LABEL_PLATE_NAME = "SourcePlateName";
	private static final String LABEL_PLATE_BARCODE = "SourcePlateBC";
	private static final String LABEL_WELL = "SourceWell";
	private static final String LABEL_ISOLATE_NAME = "AntibodyCloneName";
	private static final String LABEL_STATUS = "Status";
	
	private int numberOfRows = 0;
	private int numberOfColumns = 0;
	private int skippedIsolatesCount = 0;
	private List<String> skippedIsolateNames = null;
	private Reporter reporter = null;
	private StringBuilder content;
	
	
	class IsolateByIdSorter implements Comparator<Isolate> {
		@Override
		public int compare(Isolate is1, Isolate is2) {
			if(is1.getId() == null) {
				return 0;
			}
			
			return is1.getId().compareTo(is2.getId());
		}
	}
	
	class IsolatePlateWellMapping {	
		IsolatePlateWellMapping(Long isolateId, Plate plate, int row, int column) {
			this.isolateId = isolateId;
			this.plate = plate;
			this.row = row;
			this.column = column;
		}
		
		Long isolateId;
		Plate plate;
		int row;
		int column;
	}
	

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// no custom configuration needed
	}

	@Override
	public String getFileContent() {
		return content.toString();
	}

	@Override
	public void initialize() {
		skippedIsolatesCount = 0;
		skippedIsolateNames = new ArrayList<String>();
		content = new StringBuilder();
		createHeader();
	}

	@Override
	public int perform(List<Plate> plates, List<Isolate> pickedIsolates) {
		Map<Long, List<IsolatePlateWellMapping>> isolatePlateWellMappings 
			= createIsolatePlateWellMappings(plates);
		if(isolatePlateWellMappings.isEmpty()) {
			reporter.error("No Antibody Clones found in the Plate Set.");
			return 0;
		}
		
		Collections.sort(pickedIsolates, new IsolateByIdSorter());
		
		int numberOfEntries = 0;
		for(Isolate is : pickedIsolates) {
			Long isolateId = is.getId();
			if(isolateId == null || isolateId <= 0) {
				addSkippedIsolate(is);
			}
			else {
				if(isolatePlateWellMappings.containsKey(isolateId)
						&& createEntry(is, isolatePlateWellMappings.get(isolateId))) {
					numberOfEntries++;
				}
				else {
					addSkippedIsolate(is);
				}
			}
		}
		
		report();
		return numberOfEntries;
	}

	@Override
	public void setIsolateInformationProvider(IsolateInformationProvider isolateInformationProvider) {
		// no IsolateInformationProvider needed
	}

	@Override
	public void setReporter(Reporter reporter) {
		this.reporter = reporter;
	}
	
	@Override
	public void setPlateDimensions(int numberOfRows, int numberOfColumns) {
		this.numberOfRows = numberOfRows;
		this.numberOfColumns = numberOfColumns;
	}
	
	
	private void addSkippedIsolate(Isolate isolate) {
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
		
		reporter.warn("The following Antibody Clones were skipped, as they were not found on the Plate Set: #0", 
					  toString(skippedIsolateNames, ", "));
	}
	
	private void createHeader() {
		content.append(LABEL_PLATE_NAME);
		content.append(SEPARATOR);
		content.append(LABEL_PLATE_BARCODE);
		content.append(SEPARATOR);
		content.append(LABEL_WELL);
		content.append(SEPARATOR);
		content.append(LABEL_ISOLATE_NAME);
		content.append(SEPARATOR);
		content.append(LABEL_STATUS);
		
		content.append(NEWLINE);
	}
	

	private boolean createEntry(Isolate is, List<IsolatePlateWellMapping> mappings) {
		IsolatePlateWellMapping mapping = getMappingFromList(mappings);
		if(mapping == null) {
			return false;
		}
		
		content.append(getStringForExport(mapping.plate.getAlias()));
		content.append(SEPARATOR);
		content.append(getStringForExport(mapping.plate.getBarcode()));
		content.append(SEPARATOR);
		content.append(wellAddress(mapping.row, mapping.column));
		content.append(SEPARATOR);
		content.append(getStringForExport(is.getAlias()));
		content.append(SEPARATOR);
		content.append(DEFAULT_STATUS_VALUE);
	
		content.append(NEWLINE);
		return true;
	}
	
	private String getStringForExport(String value) {
		if(value == null) {
			return "";
		}
		
		return value.replace(SEPARATOR, ' ');
	}
	
	private IsolatePlateWellMapping getMappingFromList(List<IsolatePlateWellMapping> mappings) {
		IsolatePlateWellMapping result = null;
		for(IsolatePlateWellMapping m : mappings) {
			if(result == null) {
				result = m;
			}
			// from first (oldest) plate 
			else if(m.plate.getId() < result.plate.getId()) {
				result = m;
			}
		}
		
		return result;
	}
	
	private Map<Long, List<IsolatePlateWellMapping>> createIsolatePlateWellMappings(List<Plate> plates) {
		Map<Long, List<IsolatePlateWellMapping>> result =
				new HashMap<Long, List<IsolatePlateWellMapping>>();
		
		for(Plate p : plates) {			
			for(int row = 1; row <= numberOfRows; row++) {
				for(int col = 1; col <= numberOfColumns; col++) {
					try {
						Long isolateId = p.getWell(row, col);
						if(isolateId != null && isolateId > 0) {
							if(!result.containsKey(isolateId)) {
								result.put(isolateId, new ArrayList<IsolatePlateWellMapping>());
							}
							result.get(isolateId).add(new IsolatePlateWellMapping(
											isolateId,
											p,
											row,
											col));
						}
					} catch(Throwable t)
					{ 
						log.error("Error while creating isolate / plate / well mappings: " + t.getMessage(), t);
					}
				}
			}
		}
		
		return result;
	}
	
	
	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	
	public static String wellAddress(int row, int col) {
		return rowAddress(row)+String.format("%d", col);
	}
	
	/**
	 * 
	 * @param row
	 * 
	 * @return The letter representation of a row, e.g. row 1 has letter A,
	 * row 26 and 27 have Z and AA.
	 */
	public static String rowAddress(int row) {
		String answer = "";
		while (true) {
			answer = ALPHABET.charAt((row-1) % ALPHABET.length()) + answer;
			
			if (row <= ALPHABET.length()) {
				break;
			} else {
				row /= ALPHABET.length();
			}
		}
		
		return answer;
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

