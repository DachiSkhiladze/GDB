package genedata.bx.bl.adapter.ws.sequence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.validation.Schema;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import genedata.bx.bl.adapter.ws.sequence.schema.Feature;
import genedata.bx.bl.adapter.ws.sequence.schema.ObjectFactory;
import genedata.bx.bl.adapter.ws.sequence.schema.Sequence;
import genedata.bx.bl.adapter.ws.sequence.schema.SequenceContainer;


/**
 * Basic example implementation of a web service client for the creation of
 * annotation for sequences stored within the Genedata Biologics database.
 * 
 * This client retrieves sequences from the Genedata Biologics database in the
 * form of SequenceContainer XML. The sequences are marshalled to Java objects,
 * the cysteine residues found within the CDR regions of the sequences are
 * annotated as new features, and finally the new sequence features are posted
 * to the the web service and stored in the database.
 * 
 */
public class AnnotateFreeCysWebServicesClient {

	private static final String AUTHORIZATION = "Authorization";
	private static final String WS_XML_SCHEMA = "ws_sequence_v1_annotated.xsd";
	private static final String GET = "GET";
	private static final String PUT = "PUT";
	private static final String CONTENT_TYPE = "Content-Type";
	private static final String APPLICATION_XML = "application/xml";
	private static final int TIMEOUT_MS = 30000;
	
	private String featureName = "Free C";
	private String featureDescription = "Free Cys in CDR";
	private String featureTypeName = "misc_feature";
	
	private Logger log = Logger.getLogger(getClass());


	public static void main(String[] args) throws Exception  {
		
		if (args.length != 2){
			printUsage();
			System.exit(1);
		}
		
		String getWsURL = args[0];
		String postWsURL = args[1]; 	
		
		URL getUrl = new URL(getWsURL);
		URL postUrl = new URL(postWsURL);
		
		Schema xmlSchema = XmlSerializeUtil.newSchema(AnnotateFreeCysWebServicesClient.class.getResourceAsStream(WS_XML_SCHEMA));
		// Disable optional validation against XML schema
		// Schema xmlSchema = null;
		
		new AnnotateFreeCysWebServicesClient().fetchFeaturesAndAdd(getUrl, postUrl, xmlSchema);
	}

	private void fetchFeaturesAndAdd(URL getUrl, URL postUrl, Schema xmlSchema) 
			throws MalformedURLException, IOException, JAXBException, SAXException, ProtocolException {
		
		// Fetch sequences
		SequenceContainer container = fetchSequenceContainer(getUrl, xmlSchema);
		
		// Create sequence features
		annotateFreeCys(container);
		
		// Upload to the database
		uploadSequenceContainer(postUrl, xmlSchema, container);
	}

	private SequenceContainer fetchSequenceContainer(URL getUrl, Schema xmlSchema)
			throws IOException, ProtocolException, JAXBException, SAXException {
		
		printOut("Attempting to GET Sequences via: " + getUrl);
		
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
		
		// Custom ValidationEventHandler: only warn, do not abort
		ValidationEventHandler validationEventHandler = XmlSerializeUtil.logValidationEventHandler(log);
		
		InputStream responseInputStream = httpConnectionObject.getInputStream();
		SequenceContainer container = XmlSerializeUtil.unmarshal(responseInputStream, SequenceContainer.class, xmlSchema, validationEventHandler);
		
		responseInputStream.close();
		httpConnectionObject.disconnect();
		
		return container;
	}
	
	private void annotateFreeCys(SequenceContainer container) {
		
		printOut("Updating %d sequence(s)", container.getSequences().getSequence().size());

		Iterator<Sequence> seqIterator= container.getSequences().getSequence().iterator();
		while (seqIterator.hasNext()) {
			Sequence sequence = seqIterator.next();
			boolean hasNewFeatures = setNewFeatuesInContainer(sequence);
			if (! hasNewFeatures) {
				seqIterator.remove();
			}
		}
	}

	private void uploadSequenceContainer(URL postUrl, Schema xmlSchema, SequenceContainer container)
			throws IOException, ProtocolException, JAXBException {
		
		printOut("Attempting to POST modified Sequences to: " + postUrl);

		HttpURLConnection httpConnectionObject = (HttpURLConnection) postUrl.openConnection(); //HttpURLConnection cast required for conn.getOutputStream()
		httpConnectionObject.setDoOutput(true);
		httpConnectionObject.setRequestMethod(PUT);
		httpConnectionObject.setConnectTimeout(TIMEOUT_MS);	//set timeout to 5 minutes
		String userInfo = postUrl.getUserInfo();
		if (userInfo != null) {
			String credentials = "Basic " + Base64.getEncoder().encodeToString(userInfo.getBytes());
			httpConnectionObject.setRequestProperty(AUTHORIZATION, credentials);
		}
		httpConnectionObject.setRequestProperty(CONTENT_TYPE, APPLICATION_XML); // POST must be Content-Type "application/xml"
		httpConnectionObject.connect();

		OutputStream outputStream = httpConnectionObject.getOutputStream();
		JAXBElement<SequenceContainer> jaxbElement = new ObjectFactory().createSequenceContainer(container);
		XmlSerializeUtil.marshal(outputStream, jaxbElement, xmlSchema);

		outputStream.close(); // close output stream; should close connection

		int responseCode = httpConnectionObject.getResponseCode();
		
		prettyPrintResponseMessage(httpConnectionObject, PUT);
		
		if (responseCode != HttpURLConnection.HTTP_OK) {
			System.exit(1);
		}
		
		httpConnectionObject.disconnect();
	}

	private boolean setNewFeatuesInContainer(Sequence sequence) {
		
		List<Feature> freeCysFeatures = new ArrayList<Feature>();
		
		// Process sequence only within CDR regions.
		// CDRs are provided as a feature of type = "CDR".
		
		List<Feature> features = sequence.getFeatures().getFeature();
		for (Feature feature : features) {
			
			// Continue if the current feature is not of type CDR
			if (! isCdrFeature(feature)) {
				continue;
			}
			
			freeCysFeatures.addAll(createFreeCysFeatures(sequence, feature));
		}

		features.clear();
		features.addAll(freeCysFeatures);
		
		return ! freeCysFeatures.isEmpty();
	}

	private List<Feature> createFreeCysFeatures(Sequence sequence, Feature cdrFeature) {

		// Verify that the provided feature is of type CDR and
		// determine start and stop position of the CDR
		
		if (! isCdrFeature(cdrFeature)) {
			return Collections.emptyList();
		}		
		long start = cdrFeature.getStart();
		long stop = cdrFeature.getStop();

		List<Feature> freeCysFeatures;
		
		if (start <= stop) {
			
			freeCysFeatures = createFreeCysFeatures(sequence, start, stop);
			
		} else {
			
			// This CDR spans the origin (if sequence.isDNA() is true).
			// We process it therefore in two steps.
			freeCysFeatures = new ArrayList<Feature>();
			long length = sequence.getResidues().length();
			freeCysFeatures.addAll(createFreeCysFeatures(sequence, start, length));
			freeCysFeatures.addAll(createFreeCysFeatures(sequence, 1, stop));
			
		}
		
		return freeCysFeatures;
		
	}

	private List<Feature> createFreeCysFeatures(Sequence sequence, long start, long stop) {

		List<Feature> newFeatures = new ArrayList<Feature>();
		
		String residues = sequence.getResidues();
		for (long i = start; i <= stop; i++) {
			String residue = String.valueOf(residues.charAt((int) i-1));
			if ("C".equalsIgnoreCase(residue)) {
				newFeatures.add(createFreeCysFeature(sequence, i));
			}
		}
		
		return newFeatures;
	}

	private Feature createFreeCysFeature(Sequence sequence, long i) {
		printOut("WARN: Found free cysteine at position %d in %s", i, sequence.getName());
		long start = i;
		long stop = i;
		
		Feature answer = new Feature();

		answer.setName(featureName);
		answer.setDescription(featureDescription);
		answer.setTypeKey(featureTypeName);
		answer.setStart(start);
		answer.setStop(stop);
		
		return answer;
	}

	private boolean isCdrFeature(Feature feature) {
		return feature.getType().equals("CDR");
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
