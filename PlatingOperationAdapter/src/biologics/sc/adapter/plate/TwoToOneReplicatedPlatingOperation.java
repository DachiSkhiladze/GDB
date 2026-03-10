/**
 * 
 */
package biologics.sc.adapter.plate;

import genedata.bx.adapter.plate.Plate;
import genedata.bx.adapter.plate.PlateFactory;
import genedata.bx.adapter.plate.PlatingOperationAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Takes a lot of plates and arranges each 2 of them on a bigger plate. Each of
 * the 2 is replicated. This means either 2x96 go to one 384, or 2x24 go to one 
 * 96 plate. 
 *
 * @author tfiedler
 */
public class TwoToOneReplicatedPlatingOperation implements PlatingOperationAdapter {

	private int srcRows;
	private int srcCols;
	@SuppressWarnings("unused")
	private int destRows;
	@SuppressWarnings("unused")
	private int destCols;
	
	/**
	 * Filtering is disabled, so the list is not processed.
	 * 
	 * {@inheritDoc}
	 */
	@Override
	public void setFilterSet (Set<Long> ids) { /* empty */ }

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.plate.PlatingOperationAdapter#perform(genedata.bx.adapter.plate.PlateFactory, java.util.List)
	 */
	@Override
	public List<Plate> perform(PlateFactory plateFactory, List<Plate> source) {
		List<Plate> destinationPlates = new ArrayList<Plate>();
		
		int rowOffset = 0;
		int colOffset = 0;
		int plateCount = 0;
		Plate destinationPlate = null;
		
		for (int i = 0; i < source.size(); ) {
			Plate srcPlate = source.get (i);
			
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
					i++;
					rowOffset = 0;
					colOffset = srcCols;
					break;
				case 2:
					rowOffset = srcRows;
					colOffset = 0;
					break;
				case 3:
					i++;
					rowOffset = srcRows;
					colOffset = srcCols;
					break;
			}
			
			// copy plate
			for (int row = 1; row <= srcRows; row ++) {
				for (int col = 1; col <= srcCols; col ++) {
					destinationPlate.setWell(row+rowOffset, col+colOffset, srcPlate, row, col);
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

	@Override
	public void setDestinationPlateDimensions(int numberOfRows, int numberOfColumns) {
		this.destRows = numberOfRows;
		this.destCols = numberOfColumns;
	}

	@Override
	public void setSourcePlateDimensions(int numberOfRows, int numberOfColumns) {
		this.srcRows = numberOfRows;
		this.srcCols = numberOfColumns;
	}

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// no custom settings available for this plating operation 
	}

}
