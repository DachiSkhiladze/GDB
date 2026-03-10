package genedata.bx.bl.adapter.ws.entity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


/**
 * Basic example implementation of a web service client for the creation of
 * entities using a tab-separated bulk upload file.
 * 
 * In this case, new Target Product Proteins are created.
 */
public class CreatePptWebServicesClient {

	private static final String postWsURL = "http://webserviceadmin:password@<tomcat>:8080/Biologics/ws/rest/ppt";
	private static final String pptFilename  = "pptExampleFile.tsv";

	private static final String AUTHORIZATION = "Authorization";
	private static final String POST = "POST";
	private static final String CONTENT_TYPE = "Content-Type";
	private static final Charset UTF8 = StandardCharsets.UTF_8;
	private static final int TIMEOUT_MS = 30000;

	public static void main(String[] args) throws Exception  {

		URL ppts = CreatePptWebServicesClient.class.getResource(pptFilename);
		
		URL postUrl = new URL(postWsURL);
		
		File pptFile = new File(ppts.toURI());
		
		uploadPpt(pptFile, postUrl);
	}
	
	/**
	 * Creates a connection via the URL parameter and uploads the provided file.
	 */
	public static void uploadPpt(File file, URL postUrl) throws IOException {
        
		// Create a connection from the POST URL
		HttpURLConnection conn = (HttpURLConnection) postUrl.openConnection();
		conn.setRequestMethod(POST);
		conn.setConnectTimeout(TIMEOUT_MS);
		conn.setDoOutput(true);
		conn.setRequestProperty(CONTENT_TYPE, "application/xml");
		
		// Retrieve the user credentials from the POST URL and set them
		String userInfo = postUrl.getUserInfo();
		if (userInfo != null) {
			String credentials = "Basic " + Base64.getEncoder().encodeToString(userInfo.getBytes(UTF8));
			conn.setRequestProperty(AUTHORIZATION, credentials);
		}
		
		try {
			conn.connect();

			OutputStream outputStream = conn.getOutputStream();

			uploadFile(file, outputStream);

			outputStream.close();

			prettyPrintResponseMessage(conn);

		} catch (Exception e){
			e.printStackTrace();
		}
		
	}

	/**
	 * Writes the contents of the file to be uploaded to the output stream
	 */
	private static void uploadFile(File uploadFile, OutputStream outputStream) throws IOException {

		FileInputStream inputStream = new FileInputStream(uploadFile);

		copyStream(inputStream, outputStream);

		outputStream.flush();
        inputStream.close();
        
	}
	
	private static void copyStream (InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int len = in.read(buffer);
		while (len != -1) {
		    out.write(buffer, 0, len);
		    len = in.read(buffer);
		}
	}
	
	private static void prettyPrintResponseMessage(HttpURLConnection connection) throws IOException{
		int responseCode = connection.getResponseCode();
		String responseMsg = connection.getResponseMessage();
		String httpMethod = connection.getRequestMethod();
		
		boolean isOK = responseCode == HttpURLConnection.HTTP_OK;
		
		printOut("%s Operation %s -- Response code: %s %s ", httpMethod , isOK ? "Succeeded" : "Failed", responseCode, responseMsg);
		
		if (! isOK) {
			InputStream errorStream = connection.getErrorStream();
			if (errorStream != null) {
				copyStream(errorStream, System.err);
			}
		} else {
			InputStream in = connection.getInputStream();
			if (in != null){
				copyStream(in, System.out);
			}
		}
	}
	
	private static void printOut(String format, Object...args) {
		System.out.println(String.format(format, args));
	}
	
}
