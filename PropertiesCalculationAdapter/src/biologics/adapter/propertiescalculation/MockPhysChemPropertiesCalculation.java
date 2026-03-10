package biologics.adapter.propertiescalculation;

import genedata.bx.adapter.Message;
import genedata.bx.adapter.MessageFactory;
import genedata.bx.adapter.propertiescalculation.NucleotidePhysChemPropertiesRecord;
import genedata.bx.adapter.propertiescalculation.PhysChemPropertiesCalculation;
import genedata.bx.adapter.propertiescalculation.PhysChemPropertiesRecord;
import genedata.bx.adapter.propertiescalculation.ProteinPhysChemPropertiesRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The Mock implementation of the PhysChemPropertiesCalculation interface.
 * 
 * <div style="font-size:x-small">
 * Copyright 2012 Genedata AG. All Rights Reserved.
 * </div>
 */
public class MockPhysChemPropertiesCalculation implements PhysChemPropertiesCalculation {
	
	private MessageFactory messageFactory;
	private final List<Message> messages = new ArrayList<Message>();
	
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// not required for this mock implementation
	}
	
	@Override
	public void setMessageFactory(MessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}
	
	@Override
	public void process(List<PhysChemPropertiesRecord> records) {
		
		for (PhysChemPropertiesRecord record : records) {
			if (record instanceof ProteinPhysChemPropertiesRecord proteinRecord) {
				String proteinSequence = proteinRecord.getProteinSequence();
				
				if (null != proteinSequence) {
					// perform the calculations here
				}
				
				proteinRecord.setExtinctionCoefficient(2);
				proteinRecord.setExtinctionCoefficientOxidized( 2.1 );

				proteinRecord.setMolecularWeight(3);
				proteinRecord.setMolecularWeightOxidized(3.1);
				
				proteinRecord.setIsoelectricPoint(4);
				proteinRecord.setIsoelectricPointOxidized(4.1);

				proteinRecord.setPropertyCalculationMethod("Mock Implementation.");
				
				// check the availability of the conjugate information
				if (null != proteinRecord.getComplexation()
						&& null != proteinRecord.getNumberOfConjugatesPerProtein()
						&& null != proteinRecord.getMolecularWeightOfConjugate()) {
					
					proteinRecord.setMolecularWeightOfConjugatedProtein(5);
					proteinRecord.setMolecularWeightOfConjugatedProteinOxidized(5.1);
					proteinRecord.setMolecularWeightBiophysical(66.6);
					proteinRecord.setMolecularWeightBiophysicalOxidized(44.44);
				}
				else {
					proteinRecord.setMolecularWeightOfConjugatedProtein(null);
					proteinRecord.setMolecularWeightOfConjugatedProteinOxidized(null);
				}
			} else if (record instanceof NucleotidePhysChemPropertiesRecord nucleotideRecord) {
				nucleotideRecord.setExtinctionCoefficient(7);
				nucleotideRecord.setExtinctionCoefficientOxidized(7.1);
				nucleotideRecord.setIsoelectricPoint(8);
				nucleotideRecord.setIsoelectricPointOxidized(8.1);

			}
		}
		
		if (null != messageFactory) {
			messages.add( messageFactory.createInfoMessage(
					"Generated mock physico-chemical property values for " +
					records.size() + " properties."));
		}
		
		return;
	}
	
	@Override
	public List<Message> getMessages() {
		return Collections.unmodifiableList(messages);
	}
}
