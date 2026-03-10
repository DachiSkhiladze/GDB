package biologics.adapter.assay;

import genedata.bx.adapter.assay.v2.AssayAttribute;
import genedata.bx.adapter.assay.v2.AssayDataOptions;
import genedata.bx.adapter.entity.Antigen;
import genedata.bx.adapter.entity.AntigenLayout;
import genedata.bx.adapter.entity.AntigenMaterial;
import genedata.bx.adapter.entity.AntigenRole;
import genedata.bx.adapter.entity.Plate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The implementation of the AssayDataAdapter Version 2 interface for
 * the import of AssayValues from plate-based, tab-separated files.
 * 
 * It processes the same input data format as its base class, PlateAssayDataAdapter. 
 * In addition, it is able to process plates containing multiple antigens. 
 * 
 * @see biologics.adapter.assay.PlateAssayDataAdapter
 */
public class PlateMultipleAntigenAssayDataAdapter extends PlateAssayDataAdapter {
	private Map<AntigenRole, Integer> countIgnoredValuesForAntigenRole= new HashMap<AntigenRole, Integer>();

	@Override
	public void options(AssayDataOptions options) {
		super.options(options);
		
		// tell Biologics we are assigning antigens
		// @see biologics.adapter.assay.PlateAssayDataAdapter for an example where the adapter does not handle antigens
		options.setAdapterAssignsAntigen(true);
	}
	
	@Override
	protected Plate validatePlate(String plateName) {
		Plate plate = super.validatePlate(plateName);
		
		AntigenLayout antigenLayout = plate.getAntigenLayout();
		if (antigenLayout == null) {
			reporter.error("Plate #0 is not connected to any Antigen Layout. It will be skipped.", plate.getAlias());
			return null;
		}
		
		// check whether there is an Antigen(/Material) for every AntigenRole
		Set<AntigenRole> antigenRoles = antigenLayout.getAntigenRoles();
		for (AntigenRole antigenRole : antigenRoles) {
			Antigen antigen = plate.antigenForAntigenRole(antigenRole);
			AntigenMaterial antigenMaterial = plate.antigenMaterialForAntigenRole(antigenRole);
			if (antigen == null && antigenMaterial == null) {
				reporter.info("No Antigen or Antigen Material has been set for Antigen Role #0 on Plate #1. All values found for Antigen Role #0 will be ignored.", antigenRole.getLabel(), plate.getAlias());
				countIgnoredValuesForAntigenRole.put(antigenRole, 0);
			}
		}
		
		return plate;
	}
	
	
	/**
	 * This implementation only allows values which have an associated antigen or antigen material
	 */
	@Override
	protected boolean isValidInputForAssayValue(Plate plate, int rowIndex,
			int columnIndex, AssayAttribute assayAttribute, String valueString) {
		
		AntigenRole antigenRole= null;
		AntigenLayout antigenLayout = plate.getAntigenLayout();
		if (antigenLayout != null) {
			antigenRole= antigenLayout.antigenRoleForWell(rowIndex, columnIndex);
		}
		
		Antigen antigen= null;
		AntigenMaterial antigenMaterial= null;
		if (antigenRole != null) {
			antigen = plate.antigenForAntigenRole(antigenRole);
			antigenMaterial = plate.antigenMaterialForAntigenRole(antigenRole);
		}

		return super.isValidInputForAssayValue(plate, rowIndex, columnIndex, assayAttribute, valueString)
			&& (antigen != null || antigenMaterial != null);
	}

	@Override
	protected void reportAfterProcessing() {
		super.reportAfterProcessing();
		
		for (Map.Entry<AntigenRole,Integer> entry : countIgnoredValuesForAntigenRole.entrySet()) {
			reportIgnoredValues(entry.getKey(), entry.getValue());
		}
	}

	private void reportIgnoredValues(AntigenRole antigenRole, Integer count) {
		if (count > 0) {
			reporter.info("Ignored #0 values for unassigned Antigen Role #1.", count, antigenRole.getLabel());		
		}
	}
	
}
