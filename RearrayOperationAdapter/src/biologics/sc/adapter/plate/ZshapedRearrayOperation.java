/**
 * 
 */
package biologics.sc.adapter.plate;

import genedata.bx.adapter.plate.Plate;
import genedata.bx.adapter.plate.PlateFactory;
import genedata.bx.adapter.plate.RearrayOperationAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Distributes a list of isolates onto new plates. The fill order is z-shaped,
 * meaning a row is fully filled first before jumping to the next row:
 * A1, A2, A3, ..., B1, B2, ...
 *
 * @author tfiedler
 */
public class ZshapedRearrayOperation implements RearrayOperationAdapter {

	final String VALUE_WELL_ROLE = "VALUE";
	
	private int srcRows;
	private int srcCols;
	private int destRows;
	private int destCols;
	
	private Set<Long> filteredIds = null;
	
	@Override
	public void setFilterSet (Set<Long> ids) {
		this.filteredIds = ids;
	}

	@Override
	public List<Plate> perform(PlateFactory plateFactory, List<Plate> source) {
		
		return fillPlates (plateFactory, extractIds (source));
	}
	
	private List<Long> extractIds (List<Plate> source) {
		List<Long> answer = new ArrayList<Long>();
		
		for (Plate srcPlate : source) {
			for (int row = 1; row <= srcRows; row ++) {
				for (int col = 1; col <= srcCols; col ++) {
					
					if (isIsolateAllowedOnWell(srcPlate, row, col)) {
						Long id = srcPlate.getWell(row, col);
						if (null != id) {
							if (filteredIds != null
									&& filteredIds.contains(id)) {
								answer.add(id);
							}
							else if (filteredIds == null) { // no filter set
								answer.add(id);
							}
						}
					}
				}
			}
		}
		
		return answer;
	}

	private List<Plate> fillPlates (PlateFactory plateFactory, List<Long> ids) {
		List<Plate> answer = new ArrayList<Plate>();
		
		int row = 1; // start row
		int col = 1; // start col
		Plate dest = null; 
		
		while (! ids.isEmpty()) {
			Long id = ids.remove(0);
			
			// search next writable well - z-shaped
			OuterWhile: while (true) {
				if (col > destCols) {
					col = 1;
					row ++;
				}
				if (null == dest || row > destRows) {
					row = col = 1;
					dest = plateFactory.createPlate();
					answer.add (dest);
				}
				for (; col <= destCols; col ++) {
					if (isIsolateAllowedOnWell (dest, row, col)) {
						break OuterWhile;
					}
				}
			}
			
			dest.setWell(row, col, id);
			col ++;
		}
		
		return answer;
	}
	
	/**
	 * @param p The plate
	 * @param row
	 * @param col
	 * 
	 * @return {@code true} if an Isolate is allowed at that well position,
	 * {@code false} otherwise.
	 */
	private boolean isIsolateAllowedOnWell (Plate p, int row, int col) {
		
		String wellRole = p.getRole (row, col);
		
		if (VALUE_WELL_ROLE.equals (wellRole)) {
			return true;
		} else {
			return false;
		}
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
