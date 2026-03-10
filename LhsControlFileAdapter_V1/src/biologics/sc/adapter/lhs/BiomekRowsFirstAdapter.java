package biologics.sc.adapter.lhs;

import genedata.bx.adapter.LhsControlFileAdapter;

import java.io.Serializable;
import java.util.Map;

/**
 * Produces an LHS report corresponding to the Biomek output format. Wells on
 * plates are consecutively numbered, within a row, source plate is first then
 * destination plate. Plate barcodes are not part of the output.
 * 
 * <div style="font-size:x-small">
 * Copyright 2010 Genedata AG. All Rights Reserved.
 * </div>
 */
public class BiomekRowsFirstAdapter implements LhsControlFileAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	
	private StringBuilder content;
	private int sourcePlateNumberOfColumns;
	private int destinationPlateNumberOfColumns;
	
	public BiomekRowsFirstAdapter() {
		content= new StringBuilder();
		createHeader();
	}
	
	@Override
	public String getControlFileContent() {
		return content.toString();
	}
	
	private void createHeader() {
		content.append("SourceWell");
		content.append(',');
		content.append("SourcePlate");
		content.append(',');
		content.append("DestWell");
		content.append(',');
		content.append("DestPlate");
		content.append('\n');
	}

	@Override
	public void mapWell(String sourcePlateId, String sourcePlateName, String sourceBarcode, int sourceRow, int sourceColumn, String destinationPlateId, String destinationPlateName, String destinationBarcode, int destinationRow, int destinationColumn) {
		content.append(getSourcePlateWellPosition(sourceRow, sourceColumn));
		content.append(',');
		content.append(sourcePlateName);
		content.append(',');
		content.append(getDestinationPlateWellPosition(destinationRow, destinationColumn));
		content.append(',');
		content.append(destinationPlateName);
		content.append('\n');
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
}
