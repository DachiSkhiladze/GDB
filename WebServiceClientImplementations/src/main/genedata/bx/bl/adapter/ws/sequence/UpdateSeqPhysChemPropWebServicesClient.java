package genedata.bx.bl.adapter.ws.sequence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Base64;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.validation.Schema;

import org.xml.sax.SAXException;

import genedata.bx.bl.adapter.ws.sequence.schema.ObjectFactory;
import genedata.bx.bl.adapter.ws.sequence.schema.Sequence;
import genedata.bx.bl.adapter.ws.sequence.schema.SequenceContainer;


/**
 * Basic example implementation of a web service client for the modification of
 * sequences in the Genedata Biologics database.

 * This client retrieves a Sequence Container XML file from the web service.
 * First, it marshals the XML into Java 'SequenceContainer' objects. Next, it
 * alters the physicochemical properties of the 'Sequences'. Finally, the updated
 * SequenceContainer is unmarshalled back into XML and posted to the web
 * service for updating in the database.
 */
public class UpdateSeqPhysChemPropWebServicesClient {

	private static final String AUTHORIZATION = "Authorization";
	//private static final String WS_XML_SCHEMA = "ws_sequence_v1_annotated.xsd";
	private static final String GET = "GET";
	private static final String POST = "POST";
	private static final String CONTENT_TYPE = "Content-Type";
	private static final String APPLICATION_XML = "application/xml";
	private static final int TIMEOUT_MS = 30000;
	
	public static void main(String[] args) throws Exception  {
		
		if (args.length != 2){
			printUsage();
			System.exit(1);
		}
		
		String getWsURL = args[0];
		String postWsURL = args[1]; 	
		
		URL getUrl = new URL(getWsURL);
		URL postUrl = new URL(postWsURL);
		
		// Disable optional XML validation
		Schema xmlSchema = null; 
		
		updatePhysicalChemicalProperties(getUrl, postUrl, xmlSchema);
	}

	private static void updatePhysicalChemicalProperties(URL getUrl, URL postUrl, Schema xmlSchema)	throws MalformedURLException, IOException, JAXBException, SAXException, ProtocolException {
		
		// Fetch SequenceContainer (containing sequence IDs)
		SequenceContainer container = getSequenceContainer(getUrl, xmlSchema);
		
		// Modify sequences properties
		printOut("Updating %d sequence(s)", container.getSequences().getSequence().size());
		for (Sequence sequence : container.getSequences().getSequence()){
			recalculateAndSetPhysChemProperties(sequence);
		}
		
		// Upload changes
		postSequenceContainer(postUrl, xmlSchema, container);
	}

	private static void postSequenceContainer(URL postUrl, Schema xmlSchema, SequenceContainer container) throws IOException, ProtocolException, JAXBException {	
		
		printOut("Attempting to POST modified Sequences to: " + postUrl);
		
		HttpURLConnection conn = (HttpURLConnection) postUrl.openConnection(); //HttpURLConnection cast required for conn.getOutputStream()
		conn.setDoOutput(true);
		conn.setRequestMethod(POST);
		conn.setConnectTimeout(TIMEOUT_MS); // set timeout to 5 minutes
															 
		String userInfo = postUrl.getUserInfo();
		if (userInfo != null) {
			String credentials = "Basic " + Base64.getEncoder().encodeToString(userInfo.getBytes());
			conn.setRequestProperty(AUTHORIZATION, credentials);
		}
		conn.setRequestProperty(CONTENT_TYPE, APPLICATION_XML); // POST must be Content-Type "application/xml"
		conn.connect();

		OutputStream outputStream = conn.getOutputStream();
		JAXBElement<SequenceContainer> jaxbElement = new ObjectFactory().createSequenceContainer(container);
		XmlSerializeUtil.marshal(outputStream, jaxbElement, xmlSchema);

		outputStream.close();

		int responseCode = conn.getResponseCode();

		prettyPrintResponseMessage(conn, POST);

		conn.disconnect();

		if (responseCode != HttpURLConnection.HTTP_OK) {
			System.exit(1);
		}
		
	}

	private static SequenceContainer getSequenceContainer(URL getUrl, Schema xmlSchema)
			throws IOException, ProtocolException, JAXBException, SAXException {

		printOut("Attempting to GET Sequences via: " + getUrl);

		/* Create input stream from WS response */
		HttpURLConnection httpConnectionObject = (HttpURLConnection) getUrl.openConnection(); //HttpURLConnection cast required for conn.getInputStream()
		httpConnectionObject.setRequestMethod(GET);
		String userInfo = getUrl.getUserInfo();
		
		if (userInfo != null) {
			String credentials = "Basic " + Base64.getEncoder().encodeToString(userInfo.getBytes());
			httpConnectionObject.setRequestProperty(AUTHORIZATION, credentials);
		}
		httpConnectionObject.connect();

		int responseCode = httpConnectionObject.getResponseCode();

		prettyPrintResponseMessage(httpConnectionObject, GET);

		if (responseCode != HttpURLConnection.HTTP_OK) {
			System.exit(1);
		}

		// Default ValidationEventHandler: throws Exception
		ValidationEventHandler validationEventHandler = null; // XmlSerializeUtil.logValidationEventHandler(log);
		
		InputStream responseInputStream = httpConnectionObject.getInputStream();
		SequenceContainer container = XmlSerializeUtil.unmarshal(responseInputStream, SequenceContainer.class, xmlSchema, validationEventHandler);

		responseInputStream.close();
		httpConnectionObject.disconnect();

		return container;
	}
	
	private static void recalculateAndSetPhysChemProperties(Sequence sequence) {
		@SuppressWarnings("unused")
		String seqString = sequence.getResidues();
		/*
		 * Dummy implementation. Change calculation method here as needed.
		 */
		if (sequence.getMolecularWeight() != null) {
			Double newMolWeight = 1.0 * sequence.getMolecularWeight();
			sequence.setMolecularWeight(newMolWeight);
		}
	}
	
	private static void copyStream (InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int len = in.read(buffer);
		while (len != -1) {
		    out.write(buffer, 0, len);
		    len = in.read(buffer);
		}
	}

	private static void prettyPrintResponseMessage(HttpURLConnection connection, String httpMethod) throws IOException{
		int responseCode = connection.getResponseCode();
		String responseMsg = connection.getResponseMessage();
		
		boolean isOK = responseCode == HttpURLConnection.HTTP_OK;
		
		printOut("%s Operation %s -- Response code: %s %s ", httpMethod , isOK ? "Succeeded" : "Failed", responseCode, responseMsg);
		
		if (! isOK) {
			InputStream errorStream = connection.getErrorStream();
			if (errorStream != null) {
				copyStream(errorStream, System.err);
			}
		}
	}

	private static void printOut(String format, Object...args) {
		System.out.println(String.format(format, args));
	}
	
	private static void printUsage() {
		
		String usage = String.format(
				"This program takes the following arguments and uses them to query the Biologics Sequence Web Service. <get_url> <post_url>\n"
				+ "e.g. http://webserviceadmin:password@<tomcat>:8080/Biologics/ws/rest/sequence/aa/qid/cl-1,cl-2 "
				+ "http://webserviceadmin:password@<tomcat>:8080/Biologics/ws/rest/sequence"
				);
		printOut(usage);
	}

}
