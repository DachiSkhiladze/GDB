package biologics.pp.adapter.registration;

import genedata.bx.adapter.Message;
import genedata.bx.adapter.MessageFactory;
import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.annotation.Feature;
import genedata.bx.adapter.entity.Isolate;
import genedata.bx.adapter.entity.Modification;
import genedata.bx.adapter.entity.SmallMoleculeConjugate;
import genedata.bx.adapter.entity.VariableRegion;
import genedata.bx.adapter.register.AsynchronousRegistrationException;
import genedata.bx.adapter.register.BulkProteinRegistration;
import genedata.bx.adapter.register.Chain;
import genedata.bx.adapter.register.LaboratoryResultValue;
import genedata.bx.adapter.register.ProteinRegistrationCallback;
import genedata.bx.adapter.register.ProteinRegistrationRecord;
import genedata.bx.adapter.register.RegistrationException;
import genedata.bx.adapter.register.v2.AppRegistrationRecord;
import genedata.bx.adapter.register.v2.DeliverySystemComponent;
import genedata.bx.adapter.register.v2.EntityAttribute;
import genedata.bx.adapter.register.v2.RegistrationRetriggerEvent;
import genedata.bx.adapter.register.v2.RegistrationRetriggerEvents;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

/**
 * Basic implementation of the external registration framework.
 * External identifiers are generated semi-randomly.
 *
 * <div style="font-size:x-small">
 * Copyright 2010-2014 Genedata AG. All Rights Reserved.
 * </div>
 */
public class BasicExternalRegistration implements BulkProteinRegistration, Serializable {
	private static final long serialVersionUID = 1L;
	
	private static Logger log = Logger.getLogger(BasicExternalRegistration.class);
	
	// set as alias to the APP 
	private static String PROVOKE_ABORTION          = "provoke_abortion";
	private static String PROVOKE_ERROR_MESSAGE     = "provoke_error_message"; 
	private static String PROVOKE_WARNING_MESSAGE   = "provoke_warning_message";
	private static String PROVOKE_TIMEOUT           = "provoke_timeout";
	// set as alias to the PPB 
	private static String PROVOKE_SKIP_REGISTRATION = "provoke_skip_registration";
	private static String PROVOKE_SKIP_UPDATE       = "provoke_skip_update";
	
	
	private MessageFactory messageFactory;
	private List<Message> messages = new ArrayList<Message>();
	private Random random;
	
	public BasicExternalRegistration() {
		random = new Random(System.currentTimeMillis());
	}
	
	@Override
	public void register(ProteinRegistrationRecord record) throws RegistrationException {
		log.debug("External Protein Registration of "+record.getQualifiedId());
		
		try {
			if (validateRecord(record, null)) {
				continueRunning();
				registerWithExceptions(record);
			}
			else {
				log.debug("Record validation failed.");
			}
			reportRecordInformationInLogFile(record);
		} catch (InterruptedException e) {
			log.debug("Protein Registration was interrupted: " + e.getMessage());
		}
		finally {
			// clean up
		}
		
		log.debug("External Protein Registration of "+record.getQualifiedId()+" done");
	}
	
	@Override
	public void registerBulk(List<ProteinRegistrationRecord> recordList, Reporter reporter, ProteinRegistrationCallback callback) {
		log.debug("Bulk External Protein Registration for " + recordList.size() + " proteins.");
		
		try {
			registerBulkWithExceptions(recordList, reporter, callback);		
		}
		catch (Exception ex) {
			try {
				String message = "An error happened in registerBulk of the proteinRegistrationCallback API. Error message: " + ex.getMessage();
				if (ex.getCause() != null && ex.getCause().getMessage() != null) {
					message += "\nInner error message: " + ex.getCause().getMessage();
				}
				log.error(message, ex);
				callback.bulkRegistrationAborted();
			}
			catch (AsynchronousRegistrationException e) {
				log.debug("Call to bulkRegistrationAborted() of the ProteinRegistrationCallback API was not successfull. Error message: " + e.getMessage());
			}
		}
		finally {
			// clean up
		}
		
		log.debug("Bulk External Protein Registration done.");
	}
	
	private void registerWithExceptions(ProteinRegistrationRecord record) {
		// early opt out
		if (skipRegistration(record)) {
			record.registrationIsSkipped();
			return;
		}
		
		for (AppRegistrationRecord appRecord : getAppRegistrationRecords(record)) {
			if (isAppAlreadyRegistered(appRecord)) {
				updateAppRegistration(appRecord);
			}
			else {
				initialAppRegistration(appRecord);
			}
		}
		
		if(record.getPpbRegistrationRecord() != null) {
			if(isAlreadyRegistered(record)) {
				updatePpbRegistration(record);
			} else {
				initialPpbRegistration(record);
			}
		} else if(record.getIsolateBatchRegistrationRecord() != null) { 
			if(isAlreadyRegistered(record)) {
				updateIsolateBatchRegistration(record);
			} else {
				initialIsolateBatchRegistration(record);
			}
		}
		
	}
	
	private boolean skipRegistration(ProteinRegistrationRecord record) {
		if (PROVOKE_SKIP_REGISTRATION.equals(record.getAlias())) {
			if (record.getRetriggerEvents().getEvents().isEmpty()) {	// user invoked
				messages.add(messageFactory.createWarningMessage("External registration skipped because protein name is '"+record.getAlias()+"'."));
			}
			return true;
		}
		if (PROVOKE_SKIP_UPDATE.equals(record.getAlias()) && isAlreadyRegistered(record)) {
			if (record.getRetriggerEvents().getEvents().isEmpty()) {	// user invoked
				messages.add(messageFactory.createWarningMessage("Updating external registration skipped because protein name is '"+record.getAlias()+"'."));
			}
			return true;
		}
		
		return false;
	}
	
	private void registerBulkWithExceptions(List<ProteinRegistrationRecord> recordList, Reporter reporter,
			ProteinRegistrationCallback callback) {
		for (int i=0; i<recordList.size(); i++) {
			ProteinRegistrationRecord record = recordList.get(i);
			reportRecordInList(i, recordList.size(), record.getAlias());
			
			Boolean recordIsValid = validateRecord(record, reporter);
			if (recordIsValid == false || PROVOKE_ABORTION.equals(record.getName())) {
				abort(callback, record);
				return;
			}
			
			registerWithExceptions(record);
			reportRecordInformationInLogFile(record);
		}
		
		try {
			log.debug("Calling callback.bulkRegistrationCompleted()");
			callback.bulkRegistrationCompleted();
		} catch (AsynchronousRegistrationException e) {
			log.debug("Call to bulkRegistrationCompleted() of ProteinRegistrationCallback API was not successfull. Error message: " + e.getMessage());
		}
	}
	
	private void abort(ProteinRegistrationCallback callback, ProteinRegistrationRecord record) {
		log.debug("Record \"" + record.getName() + "\" is not valid. Bulk registration will be aborted. Calling bulkRegistrationAborted() of ProteinRegistrationCallback API...");
		try {
			callback.bulkRegistrationAborted();
		} catch (AsynchronousRegistrationException e) {
			log.debug("Call to bulkRegistrationAborted() of ProteinRegistrationCallback API was not successfull. Error message: " + e.getMessage());
		}
	}
	
	private boolean isAlreadyRegistered(ProteinRegistrationRecord record) {
		return null != record.getExternalBatchIdentifier();
	}
	
	private boolean isAppAlreadyRegistered(AppRegistrationRecord appRecord) {
		return null != appRecord.getExternalIdentifier();
	}
	
	/**
	 * Normalize access to APPs. 
	 * @param record
	 * @return
	 */
	private List<AppRegistrationRecord> getAppRegistrationRecords(ProteinRegistrationRecord record) {
		if (isMixture(record)) {
			return record.getAppsForMixture();
		}
		return Collections.singletonList( record);
	}
	
	private boolean isMixture(ProteinRegistrationRecord record) {
		return "Mixture".equals(record.getCompositionType());
	}
	
	private boolean validateRecord(ProteinRegistrationRecord record, Reporter reporter) {
		
		String message;
		if (PROVOKE_ERROR_MESSAGE.equals(record.getName())) {
			message = "Registration to the External System failed for the moment. Please try again later.";
			if (reporter == null) {
				messages.add(messageFactory.createErrorMessage(message));
			}
			else {
				reporter.error(message);
			}
			return false;
			
		} 
		else if (PROVOKE_WARNING_MESSAGE.equals(record.getName())) {
			
			message = "Please consider using another name for the Actual Product Protein.";
			if (reporter == null) {
				messages.add(messageFactory.createWarningMessage(message));
			}
			else {
				reporter.warn(message);
			}
			return true;
			
		} 
		else if (PROVOKE_TIMEOUT.equals(record.getName())) {
			// wait 20s
			try {
				Thread.sleep(20000);
			} catch (InterruptedException e) {				
				log.debug("Provoking of timeout not possible", e);
			}
			return true;
		} else {
			return true;
		}
	}
	
	/**
	 * Send information to external system regarding about an APP that has not yet been registered externally
	 * and obtain an external identifier.
	 * 
	 * @param record
	 */
	private void initialAppRegistration(AppRegistrationRecord record) {
		
		String externalIdentifier = obtainExternalIdentifier();
		log.debug("Initial Protein Registration: externalIdentifier="+externalIdentifier);
		record.setExternalIdentifier(externalIdentifier);
		
		String secondaryExternalIdentifier = obtainSecondaryExternalIdentifier();
		log.debug("Initial Protein Registration: secondaryExternalIdentifier="+secondaryExternalIdentifier);
		record.setSecondaryExternalIdentifier(secondaryExternalIdentifier);
	}
	
	/**
	 * Send information to external system regarding updated APP data.
	 * In this example we only write to the log file
	 * @param record
	 */
	private void updateAppRegistration(AppRegistrationRecord record) {
		log.debug("Update Protein Registration: externalIdentifier="+record.getExternalIdentifier());
	}
	
	/**
	 * Send information to external system regarding about an PPB that has not yet been registered externally
	 * and obtain an external identifier.
	 * 
	 * @param record
	 */
	private void initialPpbRegistration(ProteinRegistrationRecord record) {
		String externalBatchIdentifier = obtainExternalBatchIdentifier();
		record.setExternalBatchIdentifier(externalBatchIdentifier);
	}
	
	/**
	 * Send information to external system regarding updated PPB data.
	 * In this example we only write to the log file
	 * @param record
	 */
	private void updatePpbRegistration(ProteinRegistrationRecord record) {
		log.debug("Update Protein Purification Batch Registration: externalBatchIdentifier="+record.getExternalBatchIdentifier()); 
	}
		
	/**
	 * Send information to external system regarding about an Isolate Batch that has not yet been registered externally
	 * and obtain an external identifier.
	 * 
	 * @param record
	 */
	private void initialIsolateBatchRegistration(ProteinRegistrationRecord record) {
		String externalBatchIdentifier = obtainExternalBatchIdentifier();
		record.setExternalBatchIdentifier(externalBatchIdentifier);
	}
	
	/**
	 * Send information to external system regarding updated IsolateBatch data.
	 * In this example we only write to the log file
	 * @param record
	 */
	private void updateIsolateBatchRegistration(ProteinRegistrationRecord record) {
		log.debug("Update Isolate Batch Registration: externalBatchIdentifier="+record.getExternalBatchIdentifier()); 
	}

	/**
	 * Communicate with external system to obtain identifier.
	 * In this example the identifier simply is generated. 
	 * @return
	 */
	private String obtainExternalIdentifier() {
		return randomIdentifier( "EXT");
	}
	
	/**
	 * Communicate with external system to obtain secondary identifier.
	 * In this example the identifier simply is generated. 
	 * @return
	 */
	private String obtainSecondaryExternalIdentifier() {
		return randomIdentifier( "SEC-EXT");
	}
	
	/**
	 * Communicate with external system to obtain the batch identifier.
	 * In this example the identifier simply is generated. 
	 * @return
	 */
	private String obtainExternalBatchIdentifier() {
		return randomIdentifier( "EXT-BAT");
	}
	
	private String randomIdentifier(String prefix) {
		return prefix + "-" + random.nextInt(10000); 
	}
	
	@Override
	public void setConfiguration(Map<String, String> arg0) {
		// nothing needed from configuration
	}
	
	@Override
	public void setMessageFactory(MessageFactory messageFactory) {
		this.messageFactory = messageFactory;
	}
	
	@Override
	public List<Message> getMessages() {
		return messages;
	}
	
	@Override
	public boolean validate(List<ProteinRegistrationRecord> records, Reporter reporter) {
		
		boolean result = true;
		for (ProteinRegistrationRecord record : records) {
			
			boolean currentResult = validateRecord(record, reporter);
			result &= currentResult;			
		}
		return result;
	}
	
	private void continueRunning() throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException("Driver wishes us to stop");
		}
	}
	
	private void reportRecordInList(int i, int size, String alias) {
		log.debug("----------------");
		log.debug("Protein " + (i+1) + "/" + size + " : " + alias);
		log.debug("----------------");
	}
	
	/**
	 * Example code reporting the content of the registration record to the log file for debugging purposes.
	 * @param record
	 */
	private void reportRecordInformationInLogFile(ProteinRegistrationRecord record) {
		
		log.debug("Called reportRecordInformationInLogFile.");
		
		if (record.getPpbRegistrationRecord() != null) {
			// Attributes inherited from PreparationRecord interface:
			log.debug("=== PreparationRecord (Protein Purification Batch / PPB) ===");
			logRetriggerEvents(record.getRetriggerEvents());
			log.debug("VisibleID: " + record.getVisibleId());
			log.debug("QualifiedID: " + record.getQualifiedId());
			log.debug("Alias: " + record.getAlias());		
			log.debug("ExternalBatchIdentifier: " + record.getExternalBatchIdentifier());		
			log.debug("PpbExternalRegistrationDate: " + record.getPpbExternalRegistrationDate());
			log.debug("PpbExternalRegistrationUser: " + record.getPpbExternalRegistrationUser());
			log.debug("PpbDateCreated: " + record.getPpbDateCreated());
			log.debug("PpbUserCreated: " + record.getPpbUserCreated());			   
			log.debug("DateAcquired: " + record.getDateAcquired());
			log.debug("ElnReference: " + record.getElnReference());
			log.debug("ExpressionSystemType: " + record.getExpressionSystemType());
			log.debug("Site: " + record.getSite());
			log.debug("UserAccount: " + record.getUserAccount());
			log.debug("HostType: " + record.getHostType());
			log.debug("HostTypeExternalKey: " + record.getHostTypeExternalKey());
			log.debug("HostCellLine: " + record.getHostCellLine());
			log.debug("CellLine: " + record.getCellLine());
			log.debug("Vectors:"); for(String s : record.getVectors()) { log.debug("   " + s); }
			log.debug("VectorChainInfos:"); 
			if (record.getVectorChainInfos() != null) {
				for(List<String> c : record.getVectorChainInfos()) { 
					String ss = "" ; for(String s : c) { ss += (ss == "" ? "" : ", ") + s;};
					log.debug("   " + ss); 
				}
			}
			log.debug("VectorExternalIdentifiers:"); 
			if (record.getVectorExternalIdentifiers() != null) {
				for(String s : record.getVectorExternalIdentifiers()) { log.debug("   " + s); }
			}
			log.debug("VectorSecondaryExternalIdentifiers:"); 
			if (record.getVectorSecondaryExternalIdentifiers() != null) {
				for(String s : record.getVectorSecondaryExternalIdentifiers()) { log.debug("   " + s); }
			}
			log.debug("VectorBatchIds:"); 
			if (record.getVectorBatchIds() != null) {
				for(String s : record.getVectorBatchIds()) { log.debug("   " + s); }
			}
			log.debug("VectorBatchElnReferences:"); 
			if (record.getVectorBatchElnReferences() != null) {
				for(String s : record.getVectorBatchElnReferences()) { log.debug("   " + s); }
			}
			log.debug("Concentration: " + record.getConcentration());
			log.debug("ConcentrationUnit: " + record.getConcentrationUnit());
			log.debug("ConcentrationMethod: " + record.getConcentrationMethod());
			log.debug("ConcDetermination: " + record.getConcDetermination());
			log.debug("ProteinAmountAfter: " + record.getProteinAmountAfter());
			log.debug("BufferComposition: " + record.getBufferComposition());
			log.debug("PurityMethod: " + record.getPurityMethod());
			log.debug("Purity: " + record.getPurity());
			log.debug("PurityNumber: " + record.getPurityNumber());
			log.debug("PurityUnit: " + record.getPurityUnit());
			log.debug("EndotoxinLevel: " + record.getEndotoxinLevel());
			log.debug("EndotoxinLevelNumber: " + record.getEndotoxinLevelNumber());
			log.debug("EndotoxinLevelUnit: " + record.getEndotoxinLevelUnit());
			log.debug("ActivityTestMethod: " + record.getActivityTestMethod());
			log.debug("Activity: " + record.getActivity());
			log.debug("ActivityNumber: " + record.getActivityNumber());
			log.debug("ActivityUnit: " + record.getActivityUnit());
			log.debug("SpecificActivity: " + record.getSpecificActivity());
			log.debug("SpecificActivityNumber: " + record.getSpecificActivityNumber());
			log.debug("SpecificActivityUnit: " + record.getSpecificActivityUnit());
			log.debug("StorageCondition: " + record.getStorageCondition());
			log.debug("CertificateOfAnalysisReference: " + record.getCertificateOfAnalysisReference());
			log.debug("ExternalBatchIdentifier: " + record.getExternalBatchIdentifier());
			log.debug("LaboratoryResults:");
			if (record.getLaboratoryResults() != null) {
				for(LaboratoryResultValue o : record.getLaboratoryResults()) {
					log.debug("   Name: " + o.getLaboratoryResultName());
					log.debug("	   Assay attribute name: " + o.getAssayAttributeName());
					log.debug("	   Assay name: " + o.getAssayName());
					log.debug("	   Antigen: " + (o.getAntigen() != null ? o.getAntigen().getName() : "(null)"));
					log.debug("	   Value type: " + o.getValueType());
					log.debug("	   Value: " + o.getValue());
					log.debug("	   Unit: " + o.getUnit());
				}
			}
			log.debug("SourceIdentification: " + record.getPpbSourceIdentification());
			log.debug("Organization: " + (record.getPpbOrganization() == null ? "(null)" : record.getPpbOrganization().getLabel()));
			log.debug("ProductComment: " + record.getProductComment());
			log.debug("Requester: " + record.getRequester());
			log.debug("ProteinAmountAfterCalculated: " + record.getProteinAmountAfterCalculated());
			
			log.debug("ProjectIds:"); for(String s : record.getProjectIds()) { log.debug("   " + s); }
			log.debug("SelectedProjectId: " + record.getSelectedProjectId());
			log.debug("ExternalProjectIds:"); for(String s : record.getExternalProjectIds()) { log.debug("   " + s); }
			log.debug("SelectedExternalProjectId: " + record.getSelectedExternalProjectId());
			
			log.debug("Species: " + record.getSpecies());
			log.debug("ProjectTargetName: " + record.getProjectTargetName());
			log.debug("ProjectSpecies: " + record.getProjectSpecies());
			
			log.debug("IsolateBatches:"); record.getPpbRegistrationRecord().getIsolateBatches().forEach(ib -> { 
				log.debug("  ID: " + ib.getQualifiedId());
				log.debug("    Alias: " + ib.getAlias());
				log.debug("    ExternalBatchIdentifier: " + ib.getExternalBatchIdentifier());
			});
			
			if (isMixture(record)) {
				List<AppRegistrationRecord> apps = record.getAppsForMixture();
				log.debug("=== Current PPB is a mixture with " + apps.size() + " assigned APPs ===");
				int count = 1;
				for (AppRegistrationRecord app : apps) {
					log.debug("=== APP #" + count++ + ": ");
					reportAppRecordInLogFile(app);
				}
			} else {
				// Attributes from the ProteinRegistrationRecord interface:
				reportAppRecordInLogFile(record);
			}
			
		} else if(record.getIsolateBatchRegistrationRecord() != null) {
			
			// Attributes inherited from PreparationRecord interface:
			log.debug("=== PreparationRecord (IsolateBatch) ===");
			logRetriggerEvents(record.getRetriggerEvents());
			log.debug("VisibleId: " + record.getVisibleId());
			log.debug("QualifiedId: " + record.getQualifiedId());
			log.debug("Alias: " + record.getAlias());		
			log.debug("ExternalBatchIdentifier: " + record.getExternalBatchIdentifier());		
			
			log.debug("DateCreated: " + record.getIsolateBatchRegistrationRecord().getDateCreated());
			log.debug("UserCreated: " + record.getIsolateBatchRegistrationRecord().getUserCreated());
	
			log.debug("Project: " + record.getProjectIds().get(0));
			log.debug("MaterialType: " + (record.getIsolateBatchRegistrationRecord().getMaterialType() == null ? "(null)" :record.getIsolateBatchRegistrationRecord().getMaterialType().getLabel()));
			final Isolate isolate = record.getIsolateBatchRegistrationRecord().getIsolate();
			if (isolate != null) {
				log.debug("Isolate Properties: ");
				log.debug("   Id: " + isolate.getVisibleId());
				log.debug("   Alias: " + isolate.getAlias());
				log.debug("   IsolateFormat: " + (isolate.getIsolateFormat() == null ? "(null)" :isolate.getIsolateFormat().getLabel()));
			}
			
			if (!record.getIsolateBatchRegistrationRecord().getProteinPurificationBatches().isEmpty()) {
				log.debug("Protein Purification Batches:");
				record.getIsolateBatchRegistrationRecord().getProteinPurificationBatches().forEach(ppb -> {
					log.debug("  ID: " + ppb.getQualifiedId());
					log.debug("    Alias: " + ppb.getAlias());
					log.debug("    ExternalBatchIdentifier: " + ppb.getExternalBatchIdentifier());
				});
			}
				
			//Linked AppRegistrationRecord
			reportAppRecordInLogFile(record);
			
		}
	
		
		log.debug("Leaving reportRecordInformationInLogFile.");
	}

	private void logRetriggerEvents(RegistrationRetriggerEvents retriggerEvents) {
		log.debug("RetriggerEvents:");
		if (retriggerEvents != null) {
			for (RegistrationRetriggerEvent e : retriggerEvents.getEvents()) {
				Collection<String> attributes = collectRetriggerEventAttributes(e);
				String s = "   Entity Type: " + e.getEntityType().getName()
						+ "; Visible ID: " + e.getEntityVisibleId()
						+ (attributes.isEmpty() ? "" : "; Changed attributes: ")
						;
				log.debug(s);
				
				for (String changedAttribute : attributes) {
					log.debug(changedAttribute);
				}
			}
		}
	}
	
	private Collection<String> collectRetriggerEventAttributes(RegistrationRetriggerEvent retriggerEvent) {
		List<EntityAttribute> sortedAttributes = new ArrayList<>(retriggerEvent.getChangedAttributes());
		sortedAttributes.sort(Comparator.comparing(EntityAttribute::getSectionLabel)
				.thenComparing(EntityAttribute::getLabel));
		
		return sortedAttributes.stream()
				.filter(a -> !a.getSectionLabel().isBlank() && !a.getLabel().isBlank())
				.map(a -> "      " + a.getSectionLabel() + " : " + a.getLabel())
				.collect(Collectors.toList());
	}
	
	@SuppressWarnings("deprecation")
	private void reportAppRecordInLogFile(AppRegistrationRecord record) {
		log.debug("=== ProteinRegistrationRecord (Actual Product Protein / APP) ===");
		log.debug("Name: " + record.getName());
		log.debug("AppExternalRegistrationDate: " + record.getAppExternalRegistrationDate());
		log.debug("AppExternalRegistrationUser: " + record.getAppExternalRegistrationUser());
		log.debug("AppDateCreated: " + record.getAppDateCreated());
		log.debug("AppUserCreated: " + record.getAppUserCreated());
		log.debug("ExternalIdentifier: " + record.getExternalIdentifier());
		log.debug("SecondaryExternalIdentifier: " + record.getSecondaryExternalIdentifier());
		log.debug("Chains:"); 
		showChains(record.getChains());
		log.debug("ProteinSequences: " + record.getProteinSequences());
		log.debug("ProteinSequencesGenPept: " + record.getProteinSequencesGenPept());
		log.debug("NucleotideSequences: " + record.getNucleotideSequences());
		log.debug("NucleotideSequencesGenBank: " + record.getNucleotideSequencesGenBank());
		log.debug("Modifications:");
		for(Modification m : record.getModificationsCv()) {
			log.debug("   Modification Label: " + m.getLabel());
			log.debug("       EntryKey: " + m.getEntryKey());
			log.debug("       ExternalKey: " + m.getExternalKey());
			if (m.getMwDelta() != null) {
				log.debug("       MwDelta: " + m.getMwDelta());
			}
			if (m.getNucleoside() != null) {
				log.debug("       Nucleoside: " + m.getNucleoside());				
			}
			log.debug("       SortOrder: " + m.getSortOrder());
		}
		log.debug("ModificationExternalKeys:"); 
		if (record.getModificationExternalKeys() != null) {
			for(String s : record.getModificationExternalKeys()) { log.debug("   " + s); }
		}
		log.debug("ModificationDescription: " + record.getModificationDescription());
		log.debug("MwOfProtein: " + record.getMwOfProtein());
		log.debug("NoOfConjugatesPerProteinMonomer: " + record.getNoOfConjugatesPerProteinMonomer());
		log.debug("MwOfEachConjugateMolecule: " + record.getMwOfEachConjugateMolecule());
		log.debug("MwOfConjugatedProtein: " + record.getMwOfConjugatedProtein());
		log.debug("SourceIdentification: " + record.getSourceIdentification());
		log.debug("CellLineBatchSourceIdentification: " + record.getCellLineBatchSourceIdentification());
		log.debug("ProteinType: " + record.getProteinType());
		if (record.getAppType() != null) {
			log.debug("AppType Label: " + record.getAppType().getLabel());
			log.debug("AppType EntryKey: " + record.getAppType().getEntryKey());
		}
		log.debug("Organization: " + (record.getAppOrganization() == null ? "(null)" :record.getAppOrganization().getLabel()));
		log.debug("HostType: " + record.getHostType());
		log.debug("Complexation: " + record.getComplexation());
		log.debug("AdditionalInformation: " + record.getAdditionalInformation());
		log.debug("Purpose: " + record.getPurpose());
		log.debug("Format: " + record.getFormat());
		if (record.getVariableRegions() != null && record.getVariableRegions().size()>0 ) {
			log.debug("Variable region Ids:"); for(VariableRegion s : record.getVariableRegions()) { log.debug("   " + s.getQualifiedId()); }
			log.debug("Variable region names:"); for(VariableRegion s : record.getVariableRegions()) { log.debug("   " + s.getAlias()); }
			log.debug("Variable region recognized antigens and chains:");
			for(VariableRegion vr : record.getVariableRegions()) { 
				if (vr.getAntigens() != null && vr.getAntigens().size()>0) {
					log.debug("Antigens for Variable region:" + vr.getQualifiedId());
					for(genedata.bx.adapter.entity.Antigen antigen : vr.getAntigens()) { 
						log.debug("	   Name: " + antigen.getName());
						log.debug("	   ID: " + antigen.getQualifiedId());
						log.debug("	   External identifier: " + antigen.getExternalIdentifier());
						log.debug("	   External name: " + antigen.getExternalName());
						log.debug("	   Gene name: " + antigen.getGeneName());
						log.debug("	   Species: " + antigen.getSpecies());
						log.debug("	   ------ "); 
					}
				}
				else {
					log.debug("No one antigen for variable region " + vr.getQualifiedId() + " was found.");
				}
				log.debug("Chans for Variable region:" + vr.getQualifiedId());
				showChains(vr.getChains());
			}
		}
		else {
			log.debug("No variable region was found.");
		}
		if (record.getSmallMoleculeConjugates() != null && ! record.getSmallMoleculeConjugates().isEmpty()) {
			log.debug("External Small Molecule Identifiers:");
			for(SmallMoleculeConjugate s : record.getSmallMoleculeConjugates()) {
				log.debug("   " + s.getExternalSmallMoleculeIdentifier());
			}
			log.debug("Drug Antibody Ratios:");
			for(SmallMoleculeConjugate s : record.getSmallMoleculeConjugates()) {
				log.debug("   " + s.getDrugAntibodyRatio());
			}

			log.debug("Conjugate Sites:");
			for (SmallMoleculeConjugate s : record.getSmallMoleculeConjugates()) {
				String value = s.getConjugationSite() == null ? null : s.getConjugationSite().getLabel();
				log.debug("   " + value);
			}
			
			log.debug("Conjugate Scales:");
			for(SmallMoleculeConjugate s : record.getSmallMoleculeConjugates()) {
				log.debug("   " + s.getConjugationScale());
			}
		}
		if (record.getDeliverySystemComponents() != null && ! record.getDeliverySystemComponents().isEmpty()) {
			log.debug("Delivery System Components:");
			for(DeliverySystemComponent dsc : record.getDeliverySystemComponents()) {
				log.debug("   ExternalComponentName: " + dsc.getExternalComponentName() + " - Ratio: " + dsc.getRatio());
			}
		}
		else {
			log.debug("No External Small Molecule Identifiers were found.");
		}
	}
	
	@SuppressWarnings("deprecation")
	private void showChains(List<Chain> chains) {
		if (chains != null) {
			for (int i=0; i<chains.size(); i++) {
				Chain chain = chains.get(i);
				log.debug("   Chain Info " + (i+1) + ": " + chain.getChainInfo()); 
				if (chain.getChainInfoCv() != null) {
					log.debug("	   SequenceType: " + chain.getChainInfoCv().getSequenceType());
				}
				log.debug("	   Isotype: " + chain.getIsotype()); 
				log.debug("	   Length: " + chain.getLength()); 
				log.debug("	   Sort order: " + chain.getSortOrder()); 
				if (chain.getAaSequence() == null) {
					log.debug("	   Aa sequence: (null)");
				}
				else {
					String name = "(name is null)"; 
					if (chain.getAaSequence().getName() != null) {
						name = chain.getAaSequence().getName();
					}
					log.debug("	   AA sequence: " + name + ", " + chain.getAaSequence().getResidues());
					
					if (chain.getAaSequence().getFeatures() != null && chain.getAaSequence().getFeatures().size() > 0) {
						List<Feature> features = chain.getAaSequence().getFeatures();
						for (Feature feature : features) {
							log.debug("	   Feature " + feature.getType() +" : " + feature.getName());
						}
					}
					else {
						log.debug("	   No features ");
					}
				}
				if (chain.getReferenceSequence() == null) {
					log.debug("	   Reference sequence: (null)");
				}
				else {
					String name = "(name is null)"; 
					if (chain.getAaSequence() != null && chain.getAaSequence().getName() != null) {
						name = chain.getReferenceSequence().getName();
					}
					log.debug("	   Reference sequence: " + name + ", " + chain.getReferenceSequence().getResidues());
				}
			}
		}
	}
}
