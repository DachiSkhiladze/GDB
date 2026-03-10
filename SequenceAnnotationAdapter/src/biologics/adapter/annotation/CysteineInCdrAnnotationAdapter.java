package biologics.adapter.annotation;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.annotation.AaSequenceAnnotationAdapter;
import genedata.bx.adapter.annotation.DuplicateFeatureException;
import genedata.bx.adapter.annotation.Feature;
import genedata.bx.adapter.annotation.Sequence;
import genedata.bx.adapter.annotation.SequenceAnnotationException;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Example of the sequence annotation adapter.
 * All cysteines that are found within a CDR are annotated.
 *
 * <div style="font-size:x-small">
 * Copyright 2010-2013 Genedata AG. All Rights Reserved.
 * </div>
 */
public class CysteineInCdrAnnotationAdapter
		implements AaSequenceAnnotationAdapter, Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private Reporter reporter;

	private String featureName = "Free C";;
	private String featureDescription = "Free Cys in CDR";
	private String featureTypeName = "misc_feature";

	@Override
	public void setConfiguration(Map<String, String> conf) {
		String key1 = getClass().getName();
		String key;
		
		key = key1 + "." + "feature_type_name";
		if (conf.containsKey(key)) {
			this.featureTypeName = conf.get(key);
		}
		
		key = key1 + "." + "feature_name";
		if (conf.containsKey(key)) {
			this.featureName = conf.get(key);
		}
		
		key = key1 + "." + "feature_description";
		if (conf.containsKey(key)) {
			this.featureDescription = conf.get(key);
		}
	}

	@Override
	public void setReporter(Reporter reporter) {
		this.reporter = reporter;
	}

	@Override
	public void process(List<Sequence> sequences) {
		reporter.info("Processing #0 sequences", sequences.size());
		for (Sequence sequence : sequences) {
			process(sequence);
		}
	}

	private void process(Sequence sequence) {
		
		// Process sequence only within CDR regions.
		// CDRs are provided as a feature of type = "CDR".
		
		List<Feature> features = sequence.getFeatures();
		for (Feature feature : features) {
			
			// Continue if the current feature is not of type CDR
			if (! isCdrFeature(feature)) {
				continue;
			}
			
			process(sequence, feature);
		}
	}

	private void process(Sequence sequence, Feature cdrFeature) {

		// Verify that the provided feature is of type CDR and
		// determine start and stop position of the CDR
		
		if (! isCdrFeature(cdrFeature)) {
			return;
		}		
		long start = cdrFeature.getStart();
		long stop = cdrFeature.getStop();

		if (start <= stop) {
			
			process(sequence, start, stop);
			
		} else {
			
			// This CDR spans the origin (if sequence.isDNA() is true).
			// We process it therefore in two steps.
			long length = sequence.getResidues().length();
			process(sequence, start, length);
			process(sequence, 1, stop);
			
		}
		
	}

	private void process(Sequence sequence, long start, long stop) {

		String residues = sequence.getResidues();
		for (long i = start; i <= stop; i++) {
			String residue = String.valueOf(residues.charAt((int) i-1));
			if ("C".equalsIgnoreCase(residue)) {
				addCysteineFeature(sequence, i);
			}
		}
	}

	private void addCysteineFeature(Sequence sequence, long i) {
		reporter.warn("Found free cysteine at position #0 in #1.",
				i, sequence.getName());
		try {
			long start = i;
			long stop = i;
			sequence.addFeature(featureName, featureDescription, featureTypeName, start, stop);
		} catch (DuplicateFeatureException e) {
			// Ignore - fine with us
		} catch (SequenceAnnotationException e) {
			reporter.error(
				"There was a problem when trying to add a cysteine feature to sequence #0 : #1 #2",
				sequence.getName(),
				e.getClass().getSimpleName(),
				e.getMessage());
		}
	}

	private boolean isCdrFeature(Feature feature) {
		return feature.getType().equals("CDR");
	}

}
