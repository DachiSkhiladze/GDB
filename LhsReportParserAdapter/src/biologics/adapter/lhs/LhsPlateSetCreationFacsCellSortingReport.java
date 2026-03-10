package biologics.adapter.lhs;

import genedata.bx.adapter.entity.ClonePool;
import genedata.bx.adapter.entity.ClonePoolInformationProvider;
import genedata.bx.adapter.lhs.v2.LhsCallback;
import genedata.bx.adapter.lhs.v2.LhsCreatePlateSet;
import genedata.bx.adapter.lhs.v2.LhsException;
import genedata.bx.adapter.lhs.v2.LhsOptions.CreatePlateSet;
import genedata.bx.adapter.lhs.v2.LhsPlate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LhsPlateSetCreationFacsCellSortingReport implements LhsCreatePlateSet {
	//private final Logger log = Logger.getLogger(getClass());
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// not needed
	}

	@Override
	public void options(CreatePlateSet options) {
		//not needed, no options for now
	}

	@Override
	public List<LhsPlate> perform(LhsCallback.CreatePlateSet callback) {
		FacsCellSortingReport facsCellSortingReport;
		try {
			facsCellSortingReport = new FacsCellSortingReport(callback.getInputStream());
		} catch (LhsException e) {
			callback.getReporter().error(e.getMessage());
			return Collections.emptyList();
		}

		ArrayList<LhsPlate> answer = new ArrayList<LhsPlate>(facsCellSortingReport.size());
		ClonePoolInformationProvider clonePoolInfo = callback.getClonePoolInformationProvider();
		
		for(FacsCellSortingReport.ReportLine line : facsCellSortingReport) {
			try {
				int lineNumber = line.getLineNumber();
				String clonePoolQid = line.getClonePoolQid();
				ClonePool clonePool = findClonePoolByQualifiedId(clonePoolInfo,	clonePoolQid, lineNumber);
				LhsPlate plate = callback.createPlate(clonePool);
				plate.setBarcode(line.getPlateBarcode());
				answer.add(plate);
			} catch (LhsException e) {
				callback.getReporter().error(e.getMessage());
			}
		}

		return answer;
	}

	
	private static ClonePool findClonePoolByQualifiedId(ClonePoolInformationProvider clonePoolInfo, String clonePoolQid, int lineNumber) throws LhsException {
		if (clonePoolQid == null || clonePoolQid.isEmpty()) {
			throw new LhsException("Unknown clone pool ID in line " + lineNumber);
		}
		
		ClonePool clonePool = clonePoolInfo.retrieveByQualifiedId(clonePoolQid);
		if (clonePool == null) {
			throw new LhsException(String.format("Unknown clone pool ID '%s' in line %d", clonePoolQid, lineNumber));
		}
		
		return clonePool;
	}
	
}
