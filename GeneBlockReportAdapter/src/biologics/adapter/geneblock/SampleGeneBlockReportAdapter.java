package biologics.adapter.geneblock;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.entity.Ppt.PptChain;
import genedata.bx.adapter.entity.Vector;
import genedata.bx.adapter.entity.Vector.VectorChain;
import genedata.bx.adapter.geneblockreport.GeneBlockRecord;
import genedata.bx.adapter.geneblockreport.GeneBlockReportAdapter;
import genedata.bx.adapter.geneblockreport.GeneBlockReportCallback;
import genedata.bx.adapter.geneblockreport.GeneBlockReportOptions;

import java.util.List;
import java.util.Map;

public class SampleGeneBlockReportAdapter implements GeneBlockReportAdapter {
	
	private StringBuilder fileContent = null;
	private static final String delimiter = "\t";
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// no configuration
	}
	
	@Override
	public void options(GeneBlockReportOptions options) {
		// no options
	}
	
	@Override
	public boolean perform(GeneBlockReportCallback callback) {
		fileContent = new StringBuilder();
		Reporter reporter = callback.getInvocationContext().getReporter();
		
		if (callback.getGeneBlockRecords().isEmpty()) {
			reporter.error("No Gene Blocks selected for the report generation.");
			return false;
		}
		
		int numberOfRecords = writeGeneBlocks(callback.getGeneBlockRecords());
		
		callback.createReportFile(
				"GeneBlockReport.txt", 
				fileContent.toString().getBytes(), 
				"text/plain");
		
		reporter.info("Successfully created Gene Block Report with #0 record#1.",
				numberOfRecords, numberOfRecords > 1 ? "s" : "" );
		
		return true;
	}
	
	private void writeHeader(boolean showVectorChainInfo) {
		append("Protein Format").append(delimiter);
		append("Chain Info").append(delimiter);
		append("Vector ID").append(delimiter);
		append("Vector Name").append(delimiter);
		if (showVectorChainInfo) {
			append("Encoded Chain").append(delimiter);
		}
		append("Destination Vector ID").append(delimiter);
		append("Destination Vector Name").append(delimiter);
		append("Cloning Strategy").append(delimiter);
		append("Cloning Fragment").append(delimiter);
		append("DNA Sequence");
	}
	
	private int writeGeneBlocks(List<GeneBlockRecord> records) {
		int numberOfRecords = 0;

		boolean showVectorChainInfo = pptChainInfoProvidesFurtherInformation(records);
		
		writeHeader(showVectorChainInfo);
		for (GeneBlockRecord record : records) {
			for (PptChain pptChain : record.getPptChains()) {
				numberOfRecords++;
				newLine();
				
				appendPptFormatLabel(pptChain);
				appendPptChainInfo(pptChain);
				appendVectorQualifiedId(record);
				appendVectorAlias(record);
				appendVectorChainInfo(showVectorChainInfo, record);
				appendBaseVectorQualifiedId(record);
				appendBaseVectorAlias(record);
				appendCloningStrategy(record);
				appendCloningFragment(record);
				append(record.getDnaSequence());
			}
		}
		
		return numberOfRecords;
	}

	/**
	 * @return {@code true} if the ppt ChainInfo differs from the vector chain info for any PptChain. 
	 */
	private boolean pptChainInfoProvidesFurtherInformation(List<GeneBlockRecord> records) {
		for (GeneBlockRecord record : records) {
			for (PptChain pptChain : record.getPptChains()) {
				String pptChainInfo = renderPptChainInfo(pptChain);
				String vecChainInfo = renderVectorChainInfo(record);
				
				if (!pptChainInfo.equals(vecChainInfo)) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	private void appendPptFormatLabel(PptChain pptChain) {
		if (null != pptChain.getPpt() && null != pptChain.getPpt().getPptFormat()) {
			append(pptChain.getPpt().getPptFormat().getLabel());
		}
		append(delimiter);
	}
	
	private void appendPptChainInfo(PptChain pptChain) {
		String pptChainInfo = renderPptChainInfo(pptChain);
		
		append(pptChainInfo);
		
		append(delimiter);
	}

	private String renderPptChainInfo(PptChain pptChain) {
		String result = "";
		
		if (null != pptChain.getChainInfo()) {
			result += pptChain.getChainInfo().getLabel();
		}
		
		return result;
	}
	
	private void appendVectorQualifiedId(GeneBlockRecord record) {
		Vector v = null == record.getVectorChain() ? null : record.getVectorChain().getVector();
		if (null != v) {
			append(v.getQualifiedId());
		}
		append(delimiter);
	}
	
	private void appendVectorAlias(GeneBlockRecord record) {
		Vector v = null == record.getVectorChain() ? null : record.getVectorChain().getVector();
		if (null != v) {
			append(v.getAlias());
		}
		append(delimiter);
	}
	
	private void appendVectorChainInfo(boolean show, GeneBlockRecord record) {
		if (! show) {
			return;
		}
		String vectorChainInfo = renderVectorChainInfo(record);
		append(vectorChainInfo);
		
		append(delimiter);
	}

	private String renderVectorChainInfo(GeneBlockRecord record) {
		String result = "";
		
		VectorChain v = record.getVectorChain();
		if (null != v.getChainInfo()) {
			result += v.getChainInfo().getLabel();
			if (null != v.getChainIndex()) {
				result += " [" + v.getChainIndex() + "]";
			}
			if (null != v.getChainCopyStart()) {
				result += " at " + v.getChainCopyStart();
			}
		}
		
		return result;
	}
	
	private void appendBaseVectorQualifiedId(GeneBlockRecord record) {
		Vector v = null == record.getVectorChain() ? null : record.getVectorChain().getVector();
		if (null != v && null != v.getBaseVector()) {
			append(v.getBaseVector().getQualifiedId());
		}
		append(delimiter);
	}
	
	private void appendBaseVectorAlias(GeneBlockRecord record) {
		Vector v = null == record.getVectorChain() ? null : record.getVectorChain().getVector();
		if (null != v && null != v.getBaseVector()) {
			append(v.getBaseVector().getAlias());
		}
		append(delimiter);
	}
	
	private void appendCloningStrategy(GeneBlockRecord record) {
		if (null != record.getCloningStrategy()) {
			append(record.getCloningStrategy().getLabel());
		}
		append(delimiter);
	}
	
	private void appendCloningFragment(GeneBlockRecord record) {
		if (null != record.getMoleculeTemplateRegion()) {
			append(record.getMoleculeTemplateRegion().getLabel());
		}
		append(delimiter);
	}
	
	private void newLine() {
		append("\n");
	}
	
	private StringBuilder append(String cell) {
		return fileContent.append(cell == null ? "" : cell);
	}

}
