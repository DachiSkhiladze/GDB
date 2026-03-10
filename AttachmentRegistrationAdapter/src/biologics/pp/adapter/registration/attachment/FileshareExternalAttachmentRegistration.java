package biologics.pp.adapter.registration.attachment;

import genedata.bx.adapter.Reporter;
import genedata.bx.adapter.register.RegistrationException;
import genedata.bx.adapter.register.attachment.AttachmentRegistration;
import genedata.bx.adapter.register.attachment.AttachmentRegistrationRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Basic implementation of an adapter using the external attachment registration API.
 *
 * <div style="font-size:x-small">
 * Copyright 2013 Genedata AG. All Rights Reserved.
 * </div>
 */
public class FileshareExternalAttachmentRegistration implements AttachmentRegistration, Serializable {
	
	private static final long serialVersionUID = 1L;
	private static Logger log = Logger.getLogger(FileshareExternalAttachmentRegistration.class);	
	private final String baseDirectoryParameterKey = "external_attachment_registration_base_directory";	
	private String baseDirectory = null;	
	private Reporter reporter;
	private AttachmentRegistrationRecord record;
	
	public FileshareExternalAttachmentRegistration() {		
		log.debug("FileshareExternalAttachmentRegistration Version 2015-01-29");
	}	

	@Override
	public void setConfiguration(Map<String, String> configuration) {
		// Set base directory where attachments will be stored
		if (configuration.containsKey(baseDirectoryParameterKey)) {
			baseDirectory = configuration.get(baseDirectoryParameterKey);
			log.debug("Base directory: " + baseDirectory);
		}
	}	
	
	@Override
	public void update(AttachmentRegistrationRecord record, Reporter reporter) throws RegistrationException {
		log.debug("update");
		registerAttachment(record, reporter);
	}	

	@Override
	public void register(AttachmentRegistrationRecord record, Reporter reporter) throws RegistrationException {
		log.debug("register");
		registerAttachment(record, reporter);
	}

	@Override
	public void delete(AttachmentRegistrationRecord record, Reporter reporter) throws RegistrationException {
		
		String message = "";
		
		if (this.baseDirectory == null){
			message = "No base directory has been configured for file sharing. File cannot be deleted.";
			log.error(message);
			reporter.error(message);
			return;
		}
		if (record == null){
			message = "No file record object available. File cannot be deleted.";
			log.error(message);
			reporter.error(message);
			return;
		}
		
		if (!record.isUploadedViaAdapter()){
			reporter.info("No external file is deleted, since the Attachment has been created without uploading a file.");
			return;
		}
		
		String path = this.getFilePath(record);
		File file = new File(path);
		if (!file.exists()){
			message = String.format("File does not exist and cannot be deleted: \"%s\"", path);
			log.warn(message);
			reporter.warn(message);
			return;
		}
		try {
			if (file.delete()){
				message = String.format("File has been deleted: \"%s\"",  path);
				log.debug(message);
				reporter.info(message);
			}
			else {
				message = String.format("File could not be deleted: \"%s\"",  path);			
				log.error(message);
				reporter.error(message);
			}
		}
		catch (Exception ex) {
			message = String.format("An error occurred during deletion of %s: %s",  path, ex.getMessage());
			log.error(message);
			reporter.error(message);
		}
		
	}	
	
	public void registerAttachment(AttachmentRegistrationRecord record, Reporter reporter) throws RegistrationException {		
		try {
			this.reporter = reporter;	
			this.record = record;
			
			log.debug("Attachment registration: \""+ record.getFileName() + "\"");
			
			if (ensureFolders() == false){
				return;
			}
			
			if (validateRecord()) {
				continueRunning();		
				
				boolean result = storeAttachmentAndUpdateReference();
				
				if (result == true) {						
					log.debug("Successfully registered attachment. Attachment reference: \"" 
				        + record.getAttachmentReference() + "\"");
				}
				else {			
					reporter.error("Failed to register attachment.");
				}			
				
				reportRecordInformationInLogFile();				
				
			}
		} catch (InterruptedException e) {
			reporter.error("Attachment registration has been interrupted: " + e.getMessage());
		}
		
	}	
	
	private boolean validateRecord() throws InterruptedException{
		
		if (record == null) { 
			reporter.error("An empty record entity has been provided.");
			return false;
		}		
		
		// Notes:
		//
		// In order to test adapter feedback in case of errors, warnings, and time-outs,
		// set the file name of the uploaded attachment before registration to 
		// "provoke_error_message", "provoke_warning_message", or "provoke_timeout".
		//
		// The parameter external_attachment_registration_timeout must be smaller than 
		// the hard-coded time-out (currently set to 100s, see Thread.sleep call below) 
		// in order to test for time-outs.

		if ("provoke_error_message".equals(record.getFileName())) {
			reporter.error("Demonstrate Error: Registration to the external system failed intentionally.");
			return false;
		} else if ("provoke_warning_message".equals(record.getFileName())) {
			reporter.warn("Demonstrate Warning: Please consider using another name for the attachment.");
			return true;
		} else if ("provoke_timeout".equals(record.getFileName())) {
			Thread.sleep(100*1000L);
			return true;
		} 			
		
		return true;
		
	}		
	
	private void continueRunning() throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException("Driver is stopping external attachment registration.");
		}
	}	
	
	/**
	 * Ensures that all relevant folders are available.
	 * Creates subdirectories if required.
	 * @throws RegistrationException 
	 */
	private boolean ensureFolders() throws RegistrationException{		
		
		if (this.baseDirectory == null) {
			String message = "No base directory has been specified in the configuration using parameter key " 
			        + this.baseDirectoryParameterKey;
			log.error(message);
			reporter.error(message);
			return false;
		}
		
		File baseFolder = new File(this.baseDirectory);
		
		if (!baseFolder.exists()){
			String message = "Base directory does not exist: \"" + this.baseDirectory + "\"";
			log.error(message);
			reporter.error(message);
			return false;
		}
	
		File subfolder = new File(this.baseDirectory, record.getEntityQualifiedID());
		if (!subfolder.exists()) {
			boolean result = subfolder.mkdir();
			
			if (result == false){
				String message = "Subfolder could not be created: \"" + subfolder.getPath() + "\"";
				log.error(message);
				reporter.error(message);
				return false;			
			}
			else {
				setFolderPermissions(subfolder);
			}
		}
		
		return true;
	}
	
	/**
	 * Store attachment to:
	 * <ul>
	 * <li> basefolder given by configuration parameter external_attachment_registration_base_directory
	 * <li> subfolder given by record.getEntityQualifiedID()
	 * <li> filename given by record.getFileName()
	 * </ul>
	 * Update reference via record.setAttachmentReference("subfolder/filename")
	 * @throws Exception 
	 */
	private boolean storeAttachmentAndUpdateReference() throws RegistrationException {
		
		InputStream inputStream = null;
		OutputStream outputStream = null;
		File outputFile = null;
		String attachmentReference = this.getTargetAttachmentReference(record);
		String path = this.getFilePath(record);
		
		log.debug("Storing attachment to \"" + path + "\".");
		
		boolean result = true;
		
		try {
			inputStream = record.getFileContent();
			
			outputFile = new File(path);
			if(outputFile.exists()) {
				reporter.warn("Overwriting existing file \"#0\".", record.getFileName());
			}
			
			outputStream = new FileOutputStream(outputFile);		
			copyInputToOutputStream(inputStream, outputStream);	
			setFilePermissions(outputFile);
	 
			log.debug("Finished writing to output stream.");
			
		} catch (IOException e) {
			String message = "Exception when writing to " + path + ": " + e.getMessage(); 
			log.error(message, e);
			reporter.error(message);
			result = false;
		} finally {
			if (outputStream != null) {
				try {
					outputStream.close();
					if (outputFile != null) {
						setFilePermissions(outputFile);
					}
				} catch (IOException e) {
					String message = "Exception when closing the output stream for externally stored attachment file: " 
				        + e.getMessage();
					log.error(message, e);
					reporter.error(message);
					throw new RegistrationException(message);
				}
			}
		}
		
		if (result == true) {
			// Set or update the attachment reference
			record.setAttachmentReference(attachmentReference);	
		}
		
		return result;
	}

	private void copyInputToOutputStream(InputStream inputStream, OutputStream outputStream) throws IOException {
		int read = 0;
		byte[] bytes = new byte[1024];
 
		while ((read = inputStream.read(bytes)) != -1) {
			outputStream.write(bytes, 0, read);
		}
	}

	private void setFilePermissions(File file) {
		file.setExecutable(false, false);
		file.setReadable(true, false);
		file.setWritable(true, false);
	}
	
	private void setFolderPermissions(File folder) {
		folder.setExecutable(true, false);
		folder.setReadable(true, false);
		folder.setWritable(true, false);
	}
	
	private void reportRecordInformationInLogFile() throws RegistrationException {
		
		log.debug("Information on registered attachment: ");
		log.debug("getAttachmentReference():      " + record.getAttachmentReference());
		log.debug("getEntityQualifiedID():        " + record.getEntityQualifiedID());
		log.debug("getUserAccount():              " + record.getUserAccount());
		log.debug("getFileName():                 " + record.getFileName());	
		log.debug("isUploadedViaAdapter():        " + record.isUploadedViaAdapter());
		log.debug("isSubmitCertificateOfAnalysis: " + record.isSubmitCertificateOfAnalysis());
		log.debug("TargetAttachmentReference:     " + getTargetAttachmentReference(record));
		log.debug("Path:                          " + getFilePath(record));
	}
	
	private String getTargetAttachmentReference(AttachmentRegistrationRecord record) throws RegistrationException {
		if(record == null) {
			return null;
		}
		
		if(record.getFileName() != null && !record.getFileName().isEmpty()) {
			return record.getEntityQualifiedID() + "/" + record.getFileName();
		}
		else if(record.getAttachmentReference() != null && !record.getAttachmentReference().isEmpty()) {
			return record.getAttachmentReference();
		}
		
		String message = "Unable to deduce attachment reference, either file name or existing attachment reference must be set.";
		reporter.error(message);
		throw new RegistrationException(message);
	}
	
	private String getFilePath(AttachmentRegistrationRecord record) throws RegistrationException {
		return String.format("%s/%s", 
				baseDirectory,
				getTargetAttachmentReference(record));
	}
}
