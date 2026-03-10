package genedata.bx.bl.adapter.ws.sequence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


/**
 * Basic example implementation of a web service client for the retrieval of
 * sequences.
 * 
 * Depending on the provided URL, Fasta, Genbank, or XML formatted sequences
 * can be extracted.
 */
public class GetSequenceWsClient {

	private static final String AUTHORIZATION = "Authorization";
	private static final String GET = "GET";
	private static final int TIMEOUT_MS = 60000;

	public static void main(String[] args) throws IOException {
		
		if (args.length != 1){
			printUsage();
			System.exit(1);
		}
		
		String getWsURL = args[0];
		URL url = new URL(getWsURL);
		String credentials = "Basic " + new String(Base64.getEncoder().encode(url.getUserInfo().getBytes()));
		
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestProperty(AUTHORIZATION, credentials);

		conn.setRequestMethod(GET);
		conn.setConnectTimeout(TIMEOUT_MS);
		conn.setReadTimeout(TIMEOUT_MS);
		conn.connect();

		if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
			prettyPrintResponseMessage(conn, GET);
			System.exit(1);
		}

		BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
		String output;

		while ((output = br.readLine()) != null) {
			System.out.println(output);
		}
		br.close();
		
		conn.disconnect();

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
				"This program takes a single required argument and uses it access the Biologics Sequence Web Service. <get_url>\n"
				+ "e.g. http://webserviceadmin:password@<tomcat>:8080/Biologics/ws/rest/sequence/genbank/aa/qid/tpp-1,tpp-2 "
				);
		System.out.println(usage);
	}
	
}
