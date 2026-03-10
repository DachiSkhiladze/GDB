package biologics.sc.adapter.lhs;

import genedata.bx.adapter.LhsControlFileAdapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Produces an LHS report corresponding to the Biomek output format. Wells on
 * plates are consecutively numbered, within a row, source plate is first then
 * destination plate. Plate barcodes are not part of the output.
 * 
 * This example implementation sorts the content lines by source and destination plate names and well positions.
 * 
 * <div style="font-size:x-small">
 * Copyright 2013 Genedata AG. All Rights Reserved.
 * </div>
 */
public class BiomekRowsFirstWithSortingAdapter implements LhsControlFileAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	
	private final List<MappedWellInfo> mappedWellInfos;
	
	private int sourcePlateNumberOfColumns;
	private int destinationPlateNumberOfColumns;
	
	public BiomekRowsFirstWithSortingAdapter() {
		mappedWellInfos = new ArrayList<MappedWellInfo>();
	}
	
	@Override
	public String getControlFileContent() {
		StringBuilder content = new StringBuilder();
		
		content.append( createHeader() );
		
		// sort the mapped well information
		Collections.sort(mappedWellInfos);
		
		for (MappedWellInfo mappedWellInfo : mappedWellInfos) {
			content.append( mappedWellInfo.getMappedWellLine() );
		}
		
		return content.toString();
	}
	
	private static String createHeader() {
		StringBuilder header = new StringBuilder();
		
		header.append("SourceWell");
		header.append(',');
		header.append("SourcePlate");
		header.append(',');
		header.append("DestWell");
		header.append(',');
		header.append("DestPlate");
		header.append('\n');
		
		return header.toString();
	}

	@Override
	public void mapWell(
			String sourcePlateId, String sourcePlateName, String sourceBarcode, int sourceRow, int sourceColumn,
			String destinationPlateId, String destinationPlateName, String destinationBarcode, int destinationRow, int destinationColumn) {
		
		StringBuilder mappedWellLine = new StringBuilder();
		
		int sourcePlateWellPosition = getSourcePlateWellPosition(sourceRow, sourceColumn);
		int destinationPlateWellPosition = getDestinationPlateWellPosition(destinationRow, destinationColumn);
		
		mappedWellLine.append(sourcePlateWellPosition);
		mappedWellLine.append(',');
		mappedWellLine.append(sourcePlateName);
		mappedWellLine.append(',');
		mappedWellLine.append(destinationPlateWellPosition);
		mappedWellLine.append(',');
		mappedWellLine.append(destinationPlateName);
		mappedWellLine.append('\n');
		
		MappedWellInfo mappedWellInfo = new MappedWellInfo(
				sourcePlateName, sourcePlateWellPosition,
				destinationPlateName, destinationPlateWellPosition,
				mappedWellLine.toString() );
		
		mappedWellInfos.add(mappedWellInfo);
	}

	@Override
	public void setSourcePlateDimensions(int numberOfRows, int numberOfColumns) {
		this.sourcePlateNumberOfColumns = numberOfColumns;
	}

	@Override
	public void setDestinationPlateDimensions(int numberOfRows, int numberOfColumns) {
		this.destinationPlateNumberOfColumns = numberOfColumns;
	}

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// not needed in this implementation
	}
	
	private int getSourcePlateWellPosition(int row, int column) {
		return ((row-1) * sourcePlateNumberOfColumns) + column;
	}
	
	private int getDestinationPlateWellPosition(int row, int column) {
		return ((row-1) * destinationPlateNumberOfColumns) + column;
	}
	
	
	/**
	 * This simple helper class is used to hold and sort the mapped well information.
	 */
	private class MappedWellInfo implements Comparable<MappedWellInfo> {
		private final String sourcePlateName;
		private final int sourcePlateWellPosition;
		private final String destinationPlateName;
		private final int destinationPlateWellPosition;
		private final String mappedWellLine;
		
		public MappedWellInfo(String sourcePlateName, int sourcePlateWellPosition, String destinationPlateName, int destinationPlateWellPosition, String mappedWellLine) {
			this.sourcePlateName = sourcePlateName; 
			this.sourcePlateWellPosition = sourcePlateWellPosition; 
			this.destinationPlateName = destinationPlateName; 
			this.destinationPlateWellPosition = destinationPlateWellPosition; 
			this.mappedWellLine = mappedWellLine; 
		}
		
		String getMappedWellLine() {
			return mappedWellLine;
		}
		
		@Override
		public int compareTo(MappedWellInfo other) {
			// first sort by source plate name
			int outcome = (sourcePlateName != null) ? sourcePlateName.compareTo(other.sourcePlateName) :
					(other.sourcePlateName != null) ? -1 : 0;
			
			// then sort by destination plate name
			if (outcome == 0) {
				outcome = (destinationPlateName != null) ? destinationPlateName.compareTo(other.destinationPlateName) :
						(other.destinationPlateName != null) ? -1 : 0;
			}
			
			// then sort by source plate well position
			if (outcome == 0) {
				outcome = sourcePlateWellPosition - other.sourcePlateWellPosition;
			}
			
			// finally sort by destination plate well position
			if (outcome == 0) {
				outcome = destinationPlateWellPosition - other.destinationPlateWellPosition;
			}
			
			return outcome;
		}
	}
}
