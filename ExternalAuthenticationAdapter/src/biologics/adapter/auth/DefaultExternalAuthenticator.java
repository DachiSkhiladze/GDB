package biologics.adapter.auth;


import genedata.bx.adapter.ExternalAuthenticator;

import java.io.Serializable;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jboss.seam.annotations.Create;
import org.jboss.seam.annotations.Destroy;

/**
 * Sample implementation of ExternalAuthenticator.  
 * 
 * Copyright 2009 Genedata AG. All Rights Reserved.
 */
public class DefaultExternalAuthenticator implements ExternalAuthenticator, Serializable {
	private static final long serialVersionUID = 1L;
	
	private static Logger log = Logger.getLogger(DefaultExternalAuthenticator.class);

	/**
	 * Called by framework when this object is instantiated. 
	 * Put setup code here, for example connection to external system. 
	 */
	@Create
	public void onCreate() {
		log.info("onCreate");
	}
	
	/**
	 * Called by framework when this object is destroyed
	 */
	@Destroy
	public void onDestroy() {
		log.info("onDestroy");
	}
		
	@Override
	public void setConfiguration(Map<String, String> configuration) {
		for (String key : configuration.keySet()) {
			log.debug("setConfiguration: " + key + " = " + configuration.get(key));
		}
	}

	/* (non-Javadoc)
	 * @see genedata.bx.adapter.ExternalAuthenticator#authenticate(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean authenticate(String accountName, String password) {
		log.info("authenticate 2");
		boolean answer= true;
		if (password.equals("provoke_exception")) {
			IllegalArgumentException iae= new IllegalArgumentException("Exception invoked by password: " + password);
			IllegalStateException ise= new IllegalStateException("Cannot log in", iae);
			log.debug("authenticate: Exception");
			throw new Error("Exception intentionally provoked for testing purposes",ise);
		} else if (password.equals("false")) {
			answer= false;
		}
		log.debug("authenticate: " + answer);

		return answer;
	}
}
