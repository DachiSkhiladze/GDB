package biologics.sc.adapter.plate;

import genedata.bx.adapter.plate.Plate;
import genedata.bx.adapter.plate.PlateFactory;
import genedata.bx.adapter.plate.PlatingOperationAdapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arrange the contents of source plates on bigger destination plates. 
 * For each four source plates, one destination plate is generated.
 * This means either 4x96 go to one 384, or 4x24 go to one 96-well MTP.
 * The four plates are transferred to the bigger in Z-shaped order like this:
 * 
 *   1 --> 2
 *      /
 *     /  
 *   3 --> 4
 *
 * <div style="font-size:x-small">
 * Copyright 2010 Genedata AG. All Rights Reserved.
 * </div>
 */
public class FourToOneZshapedPlatingOperation implements PlatingOperationAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	
	private int sourceRows;
	private int sourceCols;
	
	@Override
	public void setFilterSet (Set<Long> ids) {
		// in this implementation, no filtering is needed
	}

	@Override
	public void setSourcePlateDimensions(int numberOfRows, int numberOfColumns) {
		this.sourceRows = numberOfRows;
		this.sourceCols = numberOfColumns;
	}

	@Override
	public void setDestinationPlateDimensions(int numberOfRows, int numberOfColumns) {
		// the destination plate dimensions are not used, as they are implied by the 
		// source dimensions
	}

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// this plating operation does not need access to additional configuration information
	}

	@Override
	public List<Plate> perform(PlateFactory plateFactory, List<Plate> sourcePlates) {
		List<Plate> destinationPlates = new ArrayList<Plate>();
		
		int rowOffset = 0;
		int colOffset = 0;
		int plateCount = 0;
		Plate destinationPlate = null;
		
		for (Plate sourcePlate : sourcePlates) {
			
			switch (plateCount%4) {
				case 0: 
					rowOffset = 0;
					colOffset = 0;
					if (null != destinationPlate) {
						destinationPlates.add (destinationPlate);
					}
					destinationPlate = plateFactory.createPlate();
					break;
				case 1:
					rowOffset = 0;
					colOffset = sourceCols;
					break;
				case 2:
					rowOffset = sourceRows;
					colOffset = 0;
					break;
				case 3:
					rowOffset = sourceRows;
					colOffset = sourceCols;
					break;
			}
			
			// copy plate
			for (int row = 1; row <= sourceRows; row ++) {
				for (int col = 1; col <= sourceCols; col ++) {					
					destinationPlate.setWell(row+rowOffset, col+colOffset, sourcePlate, row, col);
				}
			}
			
			plateCount ++;
		}
		// add last plate
		if (null != destinationPlate) {
			destinationPlates.add (destinationPlate);
		}
		
		return destinationPlates;
	}
}
