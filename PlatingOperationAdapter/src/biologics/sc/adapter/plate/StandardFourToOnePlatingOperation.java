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
 * <p>
 * The four plates are transferred to the bigger plate so that the wells
 * <ul>
 * <li>A1,A2,B1,B2 are mapped from the first (A1) wells of the plates 1,2,3,4</li>
 * <li>C1,C2,D1,D2 are mapped from the second (B1) wells of the plates 1,2,3,4</li>
 * <li>... and so on, until finally</li>
 * <li>O23,O24,P23,P24 are mapped from the last (H12) wells of the plates 1,2,3,4.</li>
 * </ul>
 * </p>
 * <div style="font-size:x-small">
 * Copyright 2010-12 Genedata AG. All Rights Reserved.
 * </div>
 */
public class StandardFourToOnePlatingOperation implements PlatingOperationAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	
	private int sourceRows;
	private int sourceCols;
	
	@Override
	public void setFilterSet(Set<Long> ids) {
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
		
		int startRowPos = 0;
		int startColPos = 0;
		int plateCount = 0;
		Plate destinationPlate = null;
		
		for (Plate sourcePlate : sourcePlates) {
			
			switch (plateCount % 4) {
				case 0: 
					startRowPos = 0;
					startColPos = 0;
					if (null != destinationPlate) {
						destinationPlates.add(destinationPlate);
					}
					destinationPlate = plateFactory.createPlate();
					break;
				case 1:
					startRowPos = 0;
					startColPos = 1;
					break;
				case 2:
					startRowPos = 1;
					startColPos = 0;
					break;
				case 3:
					startRowPos = 1;
					startColPos = 1;
					break;
			}
			
			// copy plate
			for (int row = 1; row <= sourceRows; row ++) {
				for (int col = 1; col <= sourceCols; col ++) {
					destinationPlate.setWell(2 * row + startRowPos - 1, 2 * col + startColPos - 1, sourcePlate,  row, col);
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
