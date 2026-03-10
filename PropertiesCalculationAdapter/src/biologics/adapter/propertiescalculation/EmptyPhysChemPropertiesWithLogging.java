package biologics.adapter.propertiescalculation;

import genedata.bx.adapter.Message;
import genedata.bx.adapter.MessageFactory;
import genedata.bx.adapter.entity.ControlledVocabulary;
import genedata.bx.adapter.entity.SmallMoleculeConjugate;
import genedata.bx.adapter.propertiescalculation.NucleotidePhysChemPropertiesRecord;
import genedata.bx.adapter.propertiescalculation.PhysChemPropertiesCalculation;
import genedata.bx.adapter.propertiescalculation.PhysChemPropertiesRecord;
import genedata.bx.adapter.propertiescalculation.ProteinPhysChemPropertiesRecord;
import genedata.bx.adapter.register.Chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Implementation of the PhysChemPropertiesCalculation interface logging the records' properties.
 * 
 * <div style="font-size:x-small">
 * Copyright 2024 Genedata AG. All Rights Reserved.
 * </div>
 */
public class EmptyPhysChemPropertiesWithLogging implements PhysChemPropertiesCalculation {
	private static final Logger logger = Logger.getLogger(EmptyPhysChemPropertiesWithLogging.class);

	private MessageFactory messageFactory;
	private final List<Message> messages = new ArrayList<>();

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// not required for this implementation
	}
	
	@Override
	public void setMessageFactory(MessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}

	@Override
	public void process(List<PhysChemPropertiesRecord> records) {
		for (PhysChemPropertiesRecord record : records) {
			reportPhysicoChemicalRecordInLogFile(record);
		}
	}

	private void reportPhysicoChemicalRecordInLogFile(PhysChemPropertiesRecord record) {
		if (record instanceof ProteinPhysChemPropertiesRecord proteinRecord) {
            // Called here to avoid including method's warnings in the middle of the logging.
			List<Chain> chains = proteinRecord.getChains();

			reportProteinRecordInLogFile(proteinRecord, chains);
		} else if (record instanceof NucleotidePhysChemPropertiesRecord nucleotideRecord) {
            // Called here to avoid including method's warnings in the middle of the logging.
			List<Chain> chains = nucleotideRecord.getChains();

			reportNucleotideRecordInLogFile(nucleotideRecord, chains);
		}

		logger.info("PptFormat: " + controlledVocabularyToString(record.getPptFormat()));
		String pptType = record.getPptFormat() == null ? "null" : controlledVocabularyToString(record.getPptFormat().getPptType());
		logger.info("PptType: " + pptType);
		logger.info("Purpose: " + controlledVocabularyToString(record.getPurpose()));

		if (record.getSmallMoleculeConjugates() != null && !record.getSmallMoleculeConjugates().isEmpty()) {
			logger.info("SmallMoleculeConjugates:");
			record.getSmallMoleculeConjugates().forEach(this::reportSmallMoleculeConjugateInLogFile);
		} else {
			logger.info("SmallMoleculeConjugates: None");
		}
	}

	private void reportProteinRecordInLogFile(ProteinPhysChemPropertiesRecord proteinRecord, List<Chain> chains) {
		logger.info("=== Protein's Physico-Chemical Properties Record ===");

		logger.info("ProteinSequence: " + proteinRecord.getProteinSequence());
		logger.info("Complexation: " + proteinRecord.getComplexation());
		logger.info("NumberOfConjugatesPerProtein: " + proteinRecord.getNumberOfConjugatesPerProtein());

		logger.info("MolecularWeightOfConjugate: " + proteinRecord.getMolecularWeightOfConjugate());
		logger.info("MolecularWeightOfConjugateOxidized: " + proteinRecord.getMolecularWeightOfConjugateOxidized());

		logger.info("NumberOfChains: " + proteinRecord.getNumberOfChains());
		reportChainsInLogFile(chains);

		logger.info("CysteineInDisulfideBond: " + proteinRecord.getCysteineInDisulfideBond());

		if (proteinRecord.getModificationsCv() != null && !proteinRecord.getModificationsCv().isEmpty()) {
			logger.info("Modifications:");
			proteinRecord.getModificationsCv().forEach(
					modification -> logger.info("  " + controlledVocabularyToString(modification))
			);
		} else {
			logger.info("Modifications: None");
		}

		logger.info("IsolateFormat: " + controlledVocabularyToString(proteinRecord.getIsolateFormat()));
	}

	private void reportNucleotideRecordInLogFile(NucleotidePhysChemPropertiesRecord nucleotideRecord, List<Chain> chains) {
		logger.info("=== Nucleotide's Physico Chemical Properties Record ===");

		logger.info("NucleotideSequence: " + nucleotideRecord.getNucleotideSequence());
		logger.info("IsDNA: " + booleanToString(nucleotideRecord.isDNA()));
		logger.info("IsRNA: " + booleanToString(nucleotideRecord.isRNA()));

		reportChainsInLogFile(chains);
	}

	private void reportChainsInLogFile(List<Chain> chains) {
		logger.info("Chains:");
		if (chains != null && !chains.isEmpty()) {
			chains.forEach(this::reportChainInLogFile);
		} else {
			logger.info("  None");
		}
	}

	private void reportChainInLogFile(Chain chain) {
		logger.info("  Chain:");
		if (chain.getAaSequence() != null) {
			logger.info("    AaSequence: " + chain.getAaSequence().getResidues());
		}
		if (chain.getNuSequence() != null) {
			logger.info("    NuSequence: " + chain.getNuSequence().getResidues());
		}
		if (chain.getChainInfoCv() != null && chain.getChainInfoCv().getSequenceType() != null) {
			logger.info("    SequenceType: " + chain.getChainInfoCv().getSequenceType().getLabel());
		}
		logger.info("    ChainInfo: " + controlledVocabularyToString(chain.getChainInfoCv()));

		// getIsotype() provides String label only, no ControlledVocabulary
		logger.info("    Isotype: " + chain.getIsotype());
		logger.info("    ChainMultiplicity: " + chain.getChainMultiplicity());
	}

	private void reportSmallMoleculeConjugateInLogFile(SmallMoleculeConjugate smallMoleculeConjugate) {
		logger.info("  ExternalSmallMoleculeIdentifier: " + smallMoleculeConjugate.getExternalSmallMoleculeIdentifier());
		logger.info("  SmallMoleculeMw: " + smallMoleculeConjugate.getSmallMoleculeMw());
		logger.info("  MwManuallyEntered: " + smallMoleculeConjugate.getMwManuallyEntered());
		logger.info("  DrugAntibodyRatio: " + smallMoleculeConjugate.getDrugAntibodyRatio());
		logger.info("  ConjugationChemistry: " + controlledVocabularyToString(smallMoleculeConjugate.getConjugationChemistry()));
		if (smallMoleculeConjugate.getConjugationChemistry() != null) {
			logger.info("    MwDelta: " + smallMoleculeConjugate.getConjugationChemistry().getMwDelta());
		}
	}

	private String controlledVocabularyToString(ControlledVocabulary controlledVocabulary) {
		if (controlledVocabulary == null) {
			return "null";
		}

		return controlledVocabulary.getLabel();
	}

	private String booleanToString(boolean bool) {
		return bool ? "Yes" : "No";
	}

	@Override
	public List<Message> getMessages() {
		return Collections.unmodifiableList(messages);
	}
}
