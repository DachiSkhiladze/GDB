package genedata.bx.bl.adapter.ws.entity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


/**
 * Basic example implementation of a web service client for the creation of
 * an attachment for a Parent Entity.
 * 
 * It demonstrates the usage of 'multipart/form-data' input which is required
 * when more than one data file needs to be transmitted with one web service call.
 *
 * It also demonstrates how to set Content-Disposition with filename* when a file name contains special characters.
 * 
 * In this case the uploaded files are a "content" file and an "attributes" file.
 * 
 */
public class CreateAttachmentWebServicesClient {

	private static final String postWsURL = "http://webserviceadmin:password@<tomcat>:8080/Biologics/ws/rest/attachment/create";
	private static final String attributesFilename  = "attachmentAttributesExampleFile.txt";
	private static final String contentFilename = "attachmentContentExampleFileα.pdf";

	private static final String AUTHORIZATION = "Authorization";
	private static final String POST = "POST";
	private static final String CONTENT_TYPE = "Content-Type";
	private static final Charset UTF8 = StandardCharsets.UTF_8;
	private static final String LINE_FEED = "\r\n";
	private static final int TIMEOUT_MS = 30000;
	
	private static String boundary;

	public static void main(String[] args) throws Exception  {

		URL attributes = CreateAttachmentWebServicesClient.class.getResource(attributesFilename);
		URL content = CreateAttachmentWebServicesClient.class.getResource(contentFilename);
		URL postUrl = new URL(postWsURL);
		
		File attributesFile = new File(attributes.toURI());
		File contentFile = new File(content.toURI());
		createAttachment(attributesFile, contentFile, postUrl);
		
	}
	
	/**
	 * Creates an attachment by combining an attributes file and a content file, then sending it to a specified URL.
	 *
	 * @param attributesFile The file containing Parent Entity ID for the attachment. Must not be null.
	 * @param contentFile The file containing the main content of the attachment. Must not be null.
	 * @param postUrl The URL to which the attachment will be sent. Must be a valid URL and not null.
	 * @throws IllegalArgumentException if any of the provided files or URL are null.
	 * @throws IOException if an error occurs while reading the files or communicating with the URL.
	 */
	private static void createAttachment(File attributesFile, File contentFile, URL postUrl) throws IOException {

		// Create a unique boundary based on time stamp
        boundary = "===" + System.currentTimeMillis() + "===";
        
		HttpURLConnection conn = (HttpURLConnection) postUrl.openConnection();
		conn.setRequestMethod(POST);
		conn.setConnectTimeout(TIMEOUT_MS);
		conn.setDoOutput(true);
		
		// Inform connection that the HttpRequest will be multipart/form-data
		// and specify the boundary separating the forms
		conn.setRequestProperty(CONTENT_TYPE, "multipart/form-data; boundary=" + boundary);

		conn.setRequestProperty("Accept", "application/xml");
		
		// Retrieve Web Service User Credentials from PostUrl and set them in
		// the connection.
		String userInfo = postUrl.getUserInfo();
		if (userInfo != null) {
			String credentials = "Basic " + Base64.getEncoder().encodeToString(userInfo.getBytes(UTF8));
			conn.setRequestProperty(AUTHORIZATION, credentials);
		}
		
		try {
			conn.connect();
			OutputStream outputStream = conn.getOutputStream();

			addFilePart("attributes", attributesFile, outputStream);
			addFilePart("content", contentFile, outputStream);

			cleanup(outputStream);

			prettyPrintResponseMessage(conn);

		} catch (Exception e){
			e.printStackTrace();
		}
		
	}

	private static void cleanup(OutputStream outputStream) throws IOException {
		outputStream.write(new String(LINE_FEED + "--" + boundary + "--" + LINE_FEED).getBytes(UTF8));
		outputStream.close();
	}

	/**
	 * This method takes a field (multi-part form) name and its corresponding
	 * file attachment and copies it to an Output Stream.
	 */
	private static void addFilePart(String fieldName, File uploadFile, OutputStream outputStream) throws IOException {

		StringBuilder sb = new StringBuilder();
        String fileName = uploadFile.getName();

        // Write content details to the connection output stream
		sb.append("--" + boundary).append(LINE_FEED);
		sb.append("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"" +
			" filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8)).append(LINE_FEED);
		sb.append("Content-Type: text/plain; charset=" + UTF8).append(LINE_FEED);
		sb.append(LINE_FEED);
		
		outputStream.write(sb.toString().getBytes(UTF8));
		
		FileInputStream inputStream = new FileInputStream(uploadFile);

		// Write contents of the file to the connection output stream
		copyStream(inputStream, outputStream);

		outputStream.write(LINE_FEED.getBytes(UTF8));
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
