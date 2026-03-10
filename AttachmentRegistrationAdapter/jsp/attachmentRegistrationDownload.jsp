<%@ page import="java.net.URLDecoder"%>
<%  
	String scheme = request.getScheme();
	String serverName = request.getServerName();
	int serverPort = request.getServerPort();
	
	String storage = request.getParameter("storage");
	String name = request.getParameter("name");
	
	if (storage != null && name != null) {
		String decodedStorage = URLDecoder.decode(storage, "UTF-8");
		String decodedName = URLDecoder.decode(name, "UTF-8");
		response.sendRedirect(scheme + "://" +serverName + ":" + serverPort + "/" + decodedStorage + "/" + decodedName);
	} else {
		out.println("<html><body><h1>Missing parameter 'storage' or 'name'.</h1></body></html>");
	}
%>