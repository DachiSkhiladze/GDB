package biologics.sc.adapter.plate;

import genedata.bx.adapter.plate.Plate;
import genedata.bx.adapter.plate.PlateFactory;
import genedata.bx.adapter.plate.PlatingOperationAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each source well content (e.g. Antibody Clone, Cell Line) is plated n times on one Column, 
 * where n is the number of 'value wells' for the respective Column, based on the destination Plate Layout.
 *
 * @author tfiedler
 */
public class WellToColumnPlatingOperation implements PlatingOperationAdapter {
	
	final String VALUE_WELL_ROLE = "VALUE";
	
	private int srcRows;
	private int srcCols;
	private int destRows;
	private int destCols;
	
	@Override
	public List<Plate> perform(PlateFactory plateFactory, List<Plate> source) {
		return fillPlates (plateFactory, source);
	}
	
	private List<Plate> fillPlates (PlateFactory plateFactory, List<Plate> sourcePlates) {
		List<Plate> answer = new ArrayList<Plate>();
		List<Integer> applicableColumns = getApplicableColumns(plateFactory);
		
		if(applicableColumns.isEmpty()) {
			throw new IllegalArgumentException("Destination Plate Layout does not contain a single non-reserved Well Role.");
		}
		
		Plate dest = null;
		List<Integer> targetColumns = new ArrayList<Integer>();
		
		for (Plate sourcePlate : sourcePlates) {
			for (int row = 1; row <= srcRows; row ++) {
				for (int col = 1; col <= srcCols; col ++) {
					
					if (!sourcePlate.getWellRole(row, col).isReserved()) {
					
						
						// new plate
						if(dest == null 
								|| targetColumns.isEmpty()) {
							targetColumns.addAll(applicableColumns);
							dest = plateFactory.createPlate();
							answer.add (dest);
						}
						
						int colDest = targetColumns.remove(0).intValue();
						
						// write column
						for (int rowDest = 1; rowDest <= destRows; rowDest ++) {
							if (!dest.getWellRole(rowDest, colDest).isReserved()) {
								dest.setWell(rowDest, colDest, sourcePlate, row, col);
							}
						}
					}
				}
			}
		}
		
		return answer;
	}
	
	
	private boolean isColumnApplicable(Plate plate, int column) {
		if(plate == null) {
			return false;
		}
		
		for(int row = 1; row <= destRows; row++) {
			if(!plate.getWellRole(row, column).isReserved()) {
				return true;
			}
		}
		
		return false;
	}
	
	private List<Integer> getApplicableColumns(PlateFactory plateFactory) {
		List<Integer> answer = new ArrayList<Integer>();
		
		Plate dummyPlate = plateFactory.createPlate();
		for(int col = 1; col <= destCols; col++) {
			if(isColumnApplicable(dummyPlate, col)) {
				answer.add(col);
			}
		}
		
		return answer;
	}
	
	/**
	 * Filtering is disabled, so the list is not processed.
	 * 
	 * {@inheritDoc}
	 */
	@Override
	public void setFilterSet(Set<Long> ids) {
		// empty
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
