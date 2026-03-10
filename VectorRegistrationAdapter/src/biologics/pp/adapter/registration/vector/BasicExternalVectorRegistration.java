package biologics.pp.adapter.registration.vector;


import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.annotation.Feature;
import genedata.bx.adapter.annotation.Sequence;
import genedata.bx.adapter.register.AsynchronousRegistrationException;
import genedata.bx.adapter.register.RegistrationException;
import genedata.bx.adapter.register.vector.VectorRegistration;
import genedata.bx.adapter.register.vector.VectorRegistrationCallback;
import genedata.bx.adapter.register.vector.VectorRegistrationRecord;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Basic implementation of an adapter using the external vector registration API.
 *
 * <div style="font-size:x-small">
 * Copyright 2012 Genedata AG. All Rights Reserved.
 * </div>
 */
public class BasicExternalVectorRegistration implements VectorRegistration, Serializable {
	private static final long serialVersionUID = 1L;

	private static Logger log = Logger.getLogger(BasicExternalVectorRegistration.class);
	
	private Reporter reporter;
		
	public BasicExternalVectorRegistration() {
	}	

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// nothing needed from configuration	
	}

	@Override
	public void register(VectorRegistrationRecord record, Reporter reporter)
			throws RegistrationException {			
		
		try {
			log.debug("Vector registration: "+ record);
			this.reporter = reporter;			
			if (validateRecord(record)) {
				continueRunning();
				if (null != record.getExternalIdentifier()) {
					// External identifier already available. A new registration is not possible.
					throw new RegistrationException("External identifier for " + record.getName() 
							+ " already available. Registration not possible.");					
				}
				
				initialRegistration(record);
			}
		} catch (InterruptedException e) {
			log.debug("Vector registration has been interrupted: " + e.getMessage());
		}
		
	}
	
	@Override
	public void update(VectorRegistrationRecord record, Reporter reporter)
			throws RegistrationException {
		
		try {
			log.debug("Vector registration update: "+ record);
			this.reporter = reporter;			
			if (validateRecord(record)) {
				continueRunning();
				if (null == record.getExternalIdentifier()) {
					// External identifier not yet available. Update not possible.
					throw new RegistrationException("External identifier for " + 
						record.getName() + " not yet available. Update not possible.");					
				}
				
				updateRegistration(record);
			}
		} catch (InterruptedException e) {
			log.debug("Vector registration update has been interrupted: " + e.getMessage());
		}		
	}	
	
	private void continueRunning() throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException("Driver is stopping external Vector registration.");
		}
	}	
	
	private boolean validateRecord(VectorRegistrationRecord record) throws InterruptedException{
		
		if (record == null) { 
			reporter.error("An empty record entity has been provided.");
			return false;
		}		
		
		// Test adapter feedback in case of errors, warnings, and time-outs.
		// Set the name of the vector before registration to 
		// provoke_error_message, provoke_warning_message, or provoke_timeout.
		// The parameter external_vector_registration_timeout must be smaller than 
		// the hard-coded time-out (currently set to 100s) in order to test for time-outs.

		if ("provoke_error_message".equals(record.getName())) {
			reporter.error("Demonstrate Error: Registration to the External System failed intentionally.");
			return false;
		} else if ("provoke_warning_message".equals(record.getName())) {
			reporter.warn("Demonstrate Warning: Please consider using another name for the Vector.");
			return true;
		} else if ("provoke_timeout".equals(record.getName())) {
			Thread.sleep(100*1000L);
			return true;
		} 
		
		// Check for available sequence or source ID and lot number.
		// Otherwise, the vector should not be registered into the external system.
		
		boolean sequenceAvailable = false;		
		if (record.getVectorDNA() != null && record.getVectorDNA().getResidues() != null && 
				!record.getVectorDNA().getResidues().isEmpty()) {			
			sequenceAvailable = true;			
		}
		boolean sourceAndLotAvailable = false;		
		if  (record.getSourceIdentification() != null && !record.getSourceIdentification().isEmpty() &&
				record.getSourceLotNo() != null && !record.getSourceLotNo().isEmpty()) {		     
			sourceAndLotAvailable = true;
		}
		if (sequenceAvailable == false && sourceAndLotAvailable == false) {
			
			String message = "Cannot register " + (null != record.getName() ? record.getName() : record.getId())+   
			 " to the external system because neither a sequence nor supplier and lot information are available.";
			
			reporter.error(message);
			return false;
		}
		
		
		return true;
		
	}	
	
	private void initialRegistration(VectorRegistrationRecord record) {
		
		// Simulate registering Vector in external system
		// and retrieve external identifiers
		String externalIdentifier= ExternalRegistrationSystem.getInstance().getIdentifier();
		String secondaryExternalIdentifier= ExternalRegistrationSystem.getInstance().getSecondaryIdentifier();
		
		// Set identifiers in record
		record.setExternalIdentifier(externalIdentifier);
		record.setSecondaryExternalIdentifier(secondaryExternalIdentifier);		
		
		// Logging
		log.debug("Initial Vector registration. External ID = " + record.getExternalIdentifier() 
				+ " Secondary external ID = " + record.getSecondaryExternalIdentifier());
		reportRecordInformationInLogFile(record);
	}
	
	private void updateRegistration(VectorRegistrationRecord record) {
		
		// Update Vector entry in external system
		// (Nothing to do in this sample implementation)
		ExternalRegistrationSystem.getInstance().update();
				
		// let's pretend that this creates another secondary id 
		// add it to the one we have
		String newSecondaryExternalIdentifier = ExternalRegistrationSystem.getInstance().getSecondaryIdentifier();
		String oldSecondaryExternalIdentifier = record.getSecondaryExternalIdentifier();
		record.setSecondaryExternalIdentifier(oldSecondaryExternalIdentifier + "," + newSecondaryExternalIdentifier);
				
		// Logging
		log.debug("Update Vector registration information. External ID = " + record.getExternalIdentifier() 
				+ " Secondary external ID = " + record.getSecondaryExternalIdentifier());
		reportRecordInformationInLogFile(record);
		
		}		

	@Override
	public void registerBulk(List<VectorRegistrationRecord> recordList,
			Reporter reporter, VectorRegistrationCallback callback) {
		
		log.debug("Called registerBulk(...) for " + recordList.size() + " vectors:");
		
		try {		
		for (int i=0; i<recordList.size(); i++) {
			VectorRegistrationRecord record = recordList.get(i);
			log.debug("----------------");
			log.debug("Vector " + (i+1) + "/" + recordList.size() + ": " + record.getId());
			log.debug("----------------");
			
			Boolean recordIsValid = validateRecord(record);
			if (recordIsValid == false) {
				log.debug("Record is not valid. Bulk registration will be aborted. Calling bulkRegistrationAborted() of VectorRegistrationCallback API...");
				try {
					callback.bulkRegistrationAborted();
				} catch (AsynchronousRegistrationException e) {
					log.debug("Call to bulkRegistrationAborted() of VectorRegistrationCallback API was not successful. Error message: " + e.getMessage());
				}
				return;
			}
			
			// Simulate registering Vector in external system
			// and retrieve external identifiers
			String externalIdentifier= ExternalRegistrationSystem.getInstance().getIdentifier();
			String secondaryExternalIdentifier= ExternalRegistrationSystem.getInstance().getSecondaryIdentifier();
			
			// Set identifiers in record
			record.setExternalIdentifier(externalIdentifier);
			record.setSecondaryExternalIdentifier(secondaryExternalIdentifier);	
			
			// Logging
			log.debug("Initial Vector registration. External ID = " + record.getExternalIdentifier() 
					+ " Secondary external ID = " + record.getSecondaryExternalIdentifier());
			
			reportRecordInformationInLogFile(record);
			
		}
		
		try {
			callback.bulkRegistrationCompleted();
		} catch (AsynchronousRegistrationException e) {
			log.debug("Call to bulkRegistrationAborted() of VectorRegistrationCallback API was not successful. Error message: " + e.getMessage());
		}		
		
		}
		catch (Exception ex) {
			try {
				callback.bulkRegistrationAborted();
			} 
			catch (AsynchronousRegistrationException e) {
				log.debug("Call to bulkRegistrationAborted() of VectorRegistrationCallback API was not successful. Error message: " + e.getMessage());
			}
		}
		
	}

	private void reportRecordInformationInLogFile(VectorRegistrationRecord record) {
		
		log.debug("getAdditionalSourceInfo(): " + record.getAdditionalSourceInfo());
		if (record.getAmount() != null) {
			log.debug("getAmount().toString(): " + record.getAmount().toString());
		}
		else {
			log.debug("getAmount(): null");
		}		
		log.debug("getConstructType(): " + record.getConstructType());
		log.debug("getDateCreated(): " + record.getDateCreated());
		log.debug("getDescription(): " + record.getDescription());
		if (record.getDetailsPage() != null) {
			log.debug("getDetailsPage().toString(): " + record.getDetailsPage().toString());
		}
		else {
			log.debug("getDetailsPage(): null");		
		}
		log.debug("getElnReference(): " + record.getElnReference());
		log.debug("getExternalIdentifier(): " + record.getExternalIdentifier());		
		log.debug("getExternalRegistrationComment(): " + record.getExternalRegistrationComment());
		log.debug("getGmmClass(): " + record.getGmmClass());
		log.debug("getLabBookNo(): " + record.getLabBookNo());
		log.debug("getLabBookPage(): " + record.getLabBookPage());
		log.debug("getId(): " + record.getId());
		log.debug("getName(): " + record.getName());				
		log.debug("getOrganization(): " + (record.getOrganization() == null ? "(null)" : record.getOrganization().getLabel()));
		log.debug("getSecondaryExternalIdentifier(): " + record.getSecondaryExternalIdentifier());
		log.debug("getSelectedExternalProjectId(): " + record.getSelectedExternalProjectId());
		log.debug("getSelectedProjectId(): " + record.getSelectedProjectId());
		log.debug("getSourceIdentification(): " + record.getSourceIdentification());
		log.debug("getSourceLotNo(): " + record.getSourceLotNo());		
		log.debug("getUserComment(): " + record.getUserComment());
		log.debug("getSelectedUser(): " + record.getSelectedUser());
		log.debug("getUserCreated(): " + record.getUserCreated());
		if (record.getVectorDNA() != null) {
			log.debug("getVectorDNA().getResidues(): " + record.getVectorDNA().getResidues());
		}
		else {
			log.debug("getVectorDNA(): null");
		}
		log.debug("isSelectAgent(): " + record.isSelectAgent());
		
		log.debug("getExternalProjectIds():"); for(String s :   record.getExternalProjectIds()) { log.debug("   " + s); }
		log.debug("getPayloadFeatures():");    for(Feature s :  record.getPayloadFeatures())    { log.debug("   " + s); }
		log.debug("getPayloadDNA():");         
		for(Sequence s : record.getPayloadDNA())
		{
			log.debug("   Name: " + s.getName());			
			log.debug("   Residues: " + s.getResidues());
		}		
		log.debug("getProjectIds():");         for(String s :   record.getProjectIds())         { log.debug("   " + s); }
		log.debug("getPromoters():");          for(String s :   record.getPromoters())          { log.debug("   " + s); }
		log.debug("getProteinIds():");         for(String s :   record.getProteinIds())         { log.debug("   " + s); }
		log.debug("getResistanceGenes():");    for(String s :   record.getResistanceGenes())    { log.debug("   " + s); }
		log.debug("getSpecies():");            for(String s :   record.getSpecies())            { log.debug("   " + s); }
		
		log.debug("Protein Sequence based attributes (getEncodedProteinSequences()):");
		for(Sequence s : record.getEncodedProteinSequences()){
			log.debug("   Name: " + s.getName());
			log.debug("   Residues: " + s.getResidues());
			log.debug("   Extinction coefficient: " + s.getExtinctionCoefficient());
			log.debug("   Isoelectric point: " + s.getIsoelectricPoint());
			log.debug("   Molecular weight: " + s.getMolecularWeight());			
			log.debug("   Absorbance: " + s.getAbsorbance());			
		}
		log.debug("getEncodedProteinFeatures():");    for(Feature s :  record.getEncodedProteinFeatures())  { log.debug("   " + s); }
		log.debug("getEncodedProteinChains():");    for(String s :  record.getEncodedProteinChains())       { log.debug("   " + s); }		
		log.debug("getGenbankFormattedVector() (=created Genbank file):\n\n" + record.getGenbankFormattedVector() + "\n\n");
		log.debug("getVectorNtiSource() (=original Genbank file):\n\n" + record.getVectorNtiSource() + "\n\n");
	}

}
