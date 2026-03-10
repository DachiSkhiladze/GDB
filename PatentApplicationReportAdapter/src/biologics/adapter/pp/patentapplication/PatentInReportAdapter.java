package biologics.adapter.pp.patentapplication;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.annotation.Feature;
import genedata.bx.adapter.patentapplication.PatentApplicationReport;
import genedata.bx.adapter.patentapplication.PatentApplicationReportAdapter;
import genedata.bx.adapter.patentapplication.PatentApplicationReportRecord;
import genedata.bx.adapter.patentapplication.PatentApplicationSequence;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Generates a patent application report file for the PatentIn application.
 * 
 * Format:
 * <SEQ ID NO:[incrementing sequence id];[PRT or DNA];[species if non-antibody, otherwise 'Artificial Sequence']>
 * Sequence (either feature sequence, or full sequence)
 * 
 * <div style="font-size:x-small">
 * Copyright 2013 Genedata AG. All Rights Reserved.
 * </div>
 */
public class PatentInReportAdapter implements PatentApplicationReportAdapter, Serializable {
	private static final long serialVersionUID = 1L;
	private static Logger log = Logger.getLogger(PatentInReportAdapter.class);
	private static final char NEWLINE = '\n';
	
	private long currentSequenceId = -1L;
	private final List<PatentInRecord> records = new ArrayList<PatentInRecord>();
	
	private class PatentInRecord {
		public long sequenceId;
		public boolean isDNA;
		public boolean isAntibody;
		public String species;
		public String sequence;
		
		PatentInRecord(long sequenceId, boolean isDNA, boolean isAntibody,
				String species, String sequence) {
			this.sequenceId = sequenceId;
			this.isDNA = isDNA;
			this.isAntibody = isAntibody;
			this.species = species;
			this.sequence = sequence;
		}
	}
	
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// no custom configuration needed
	}
	
	@Override
	public void setReporter(Reporter reporter) {
		// no reporting needed
	}


	@Override
	public String getMimeType() {
		return "text/plain";
	}

	@Override
	public void perform(PatentApplicationReport report) throws Exception {
		records.clear();
		currentSequenceId = report.getStartSequenceId();
		
		process(report);
	}

	@Override
	public void writeFileContent(OutputStream stream) throws IOException {
		StringBuffer buffer = new StringBuffer();
		boolean isFirst = true;
		
		for(PatentInRecord r : records) {
			String header =
					String.format("<SEQ ID NO:%d;%s;%s>",
							r.sequenceId,
							r.isDNA ? "DNA" : "PRT",
							r.isAntibody 
								? "Artificial Sequence" : r.species == null ? "" : r.species);
			
			if(!isFirst) {
				buffer.append(NEWLINE);
			}
			isFirst = false;
			
			buffer.append(header);
			buffer.append(NEWLINE);
			buffer.append(r.sequence);
		}
		
		String content = buffer.toString();
		stream.write(content.getBytes());
	}
	
	
	private void process(PatentApplicationReport report) {
		log.debug(String.format("Processing %d record(s).", report.getRecords().size()));
		for(PatentApplicationReportRecord r : 
				sort(report.getRecords(), new PatentApplicationReportRecordComparator())) {
			processRecord(r);
		}
		
		log.debug(String.format("Processed %d record(s).", report.getRecords().size()));
	}
	
	private void processRecord(PatentApplicationReportRecord record) {
		log.debug(String.format("Processing %d sequence(s) for %s.", 
				record.getSequences().size(), record.getQualifiedId()));
		for(PatentApplicationSequence s :
				sort(record.getSequences(), new PatentApplicationSequenceComparator())) {
			processSequence(record, s);
		}
		
		log.debug(String.format("Processed %d sequence(s) for %s.", 
				record.getSequences().size(), record.getQualifiedId()));
	}
	
	private void processSequence(PatentApplicationReportRecord record, PatentApplicationSequence sequence) {
		// first add full sequence
		records.add(new PatentInRecord(
							nextSequenceId(),
							sequence.isDna(),
							record.isAntibody(),
							record.getSpecies(),
							sequence.getResidues()));
		
		
		log.debug(String.format("Processing %d feature(s) for %s - chain %s (%s).", 
				sequence.getFeatures().size(), record.getQualifiedId(), sequence.getChainInfo(),
				sequence.isProtein() ? "Protein" : "DNA"));
		
		// then add selected feature sequences
		for(Feature f : 
				sort(sequence.getFeatures(), new PatentApplicationFeatureComparator())) {
			records.add(new PatentInRecord(
								nextSequenceId(),
								sequence.isDna(),
								record.isAntibody(),
								record.getSpecies(),
								f.getResidues()));
		}
		
		log.debug(String.format("Processed %d feature(s) for %s - chain %s (%s).", 
				sequence.getFeatures().size(), record.getQualifiedId(), sequence.getChainInfo(),
				sequence.isProtein() ? "Protein" : "DNA"));
	}
	
	private long nextSequenceId() {
		return currentSequenceId++;
	}
	
	
	/**
	 * Creates a shallow copy of the given list, sorts it with the given comparator
	 * and returns the copied and sorted list.
	 */
	private static <T> List<T> sort(List<T> list, Comparator<T> comparator) {
		List<T> result = new ArrayList<T>(list);
		Collections.sort(result, comparator);
		return result;
	}
	
	/**
	 * Sorts the records by the visible id of the parent PPT.
	 */
	private static class PatentApplicationReportRecordComparator implements Comparator<PatentApplicationReportRecord> {
		@Override
		public int compare(PatentApplicationReportRecord rec1, PatentApplicationReportRecord rec2) {
			return Long.valueOf(rec1.getVisibleId()).compareTo(rec2.getVisibleId());
		}
	}
	
	/**
	 * Sorts the patent application sequences by sequence type first, then by chain info.
	 */
	private static class PatentApplicationSequenceComparator implements Comparator<PatentApplicationSequence> {
		@Override
		public int compare(PatentApplicationSequence seq1, PatentApplicationSequence seq2) {
			// first order by sequence type (Protein, DNA)
			int answer = seq1.isProtein() == seq2.isProtein() ? 0 
							: seq1.isProtein() ? -1 : 1;
			
			// then by chain info ([H]eavy chain, [L]ight chain)
			if(answer == 0) {
				answer = seq1.getChainInfo().compareToIgnoreCase(seq2.getChainInfo());
			}
			return answer;
		}
	}
	
	/**
	 * Sorts the patent application features by feature type first, then by feature name.
	 */
	private static class PatentApplicationFeatureComparator implements Comparator<Feature> {
		@Override
		public int compare(Feature f1, Feature f2) {
			// first order by feature type
			int answer = f1.getType().compareToIgnoreCase(f2.getType());
			
			// then by feature name
			if(answer == 0) {
				answer = f1.getName().compareToIgnoreCase(f2.getName());
			}
			return answer;
		}
	}
}
