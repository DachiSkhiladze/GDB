package biologics.pp.adapter.registration.vector;

/**
 * This class simulates the external registration system.
 * 
 * It does not do anything useful as such -- it just creates new identifiers based on the current time. 
 * 
 */
public class ExternalRegistrationSystem {

	
	public static ExternalRegistrationSystem getInstance() {
		return instance;
	}

	private static ExternalRegistrationSystem instance= new ExternalRegistrationSystem();
	
	private final long mintime = System.currentTimeMillis();	
	private long lasttime;	
	

	synchronized public String getIdentifier() {
		// (In this sample implementation the identifiers will be set to semi-random numbers, 
		// e.g. EXT-12345 and EXT-12345-Secondary)		
		String identifier;
		long answer = (System.currentTimeMillis() - mintime) / 1000;
		if (answer <= lasttime) {
			answer = lasttime + 1;
		}
		lasttime = answer;
		identifier = ""+answer;
		identifier = "EXT-" + 
				(identifier.length() > 6 ?
				identifier.substring(identifier.length() - 6) :
				identifier);
		return identifier;
	}
	
	synchronized public String getSecondaryIdentifier() {
		return getIdentifier() + "-secondary";
	}	
	
	synchronized public void update() {
		// do nothing
	}
}
