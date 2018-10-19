package com.coxandkings.travel.bookingengine.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;

public class HTTPServiceConsumer {
	
	public static final long DEFAULT_SERVICE_TIMEOUT_MILLIS = 30000;
	// The below constants are already defined in javax.ws.rs.HttpMethod and org.springframework.http.HttpMethod classes. However, 
	// using those constants would add dependency on those packages. Therefore, redefining the constants for HTTP methods again. 
	// It is preferable to use whatever constants are available from javax.ws.rs classes as it is Java EE. 
	public static final String HTTP_METHOD_DELETE = "DELETE";
	public static final String HTTP_METHOD_GET = "GET";
	public static final String HTTP_METHOD_HEAD = "HEAD";
	public static final String HTTP_METHOD_OPTIONS = "OPTIONS";
	public static final String HTTP_METHOD_POST = "POST";
	public static final String HTTP_METHOD_PUT = "PUT";
	
	private static final String HTTP_ENCODING_DEFLATE = "deflate";
	private static final String HTTP_ENCODING_GZIP = "gzip";
	private static final String HTTP_ENCODING_XGZIP = "x-gzip";
	private static final String HTTP_HEADER_ACCEPT_ENCODING = "Accept-Encoding";
	private static final String HTTP_HEADER_ACCEPT_ENCODING_VALUES = HTTP_ENCODING_DEFLATE.concat(", ").concat(HTTP_ENCODING_GZIP).concat(", ").concat(HTTP_ENCODING_XGZIP);
	private static final Pattern PATTERN_CONTENT_TYPE_CHARSET = Pattern.compile("(charset[ ]*=[ ]*[^; ]+)");
	
	private static final Logger logger = LogManager.getLogger(HTTPServiceConsumer.class);
	
	public static HttpTemplate consumeService(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, String payload) throws Exception {
		return consumeServiceV3(tgtSysId, tgtSysURL, null, httpHdrs, HTTP_METHOD_POST, DEFAULT_SERVICE_TIMEOUT_MILLIS, payload);
	}

	public static HttpTemplate consumeService(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, String httpMethod, String payload) throws Exception {
		return consumeServiceV3(tgtSysId, tgtSysURL, null, httpHdrs, httpMethod, DEFAULT_SERVICE_TIMEOUT_MILLIS, payload);
	}

	public static HttpTemplate consumeService(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, String httpMethod, long svcTimeout, String payload) throws Exception {
		return consumeServiceV3(tgtSysId, tgtSysURL, null, httpHdrs, httpMethod, svcTimeout, payload);
	}
	
	public static Element consumeXMLService(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, Element reqElem) throws Exception {
		return consumeXMLService(tgtSysId, tgtSysURL, null, httpHdrs, HTTP_METHOD_POST, DEFAULT_SERVICE_TIMEOUT_MILLIS, reqElem);
	}

	public static Element consumeXMLService(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, String httpMethod, Element reqElem) throws Exception {
		return consumeXMLService(tgtSysId, tgtSysURL, null, httpHdrs, httpMethod, DEFAULT_SERVICE_TIMEOUT_MILLIS, reqElem);
	}

	public static Element consumeXMLService(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, String httpMethod, long svcTimeout, Element reqElem) throws Exception {
		return consumeXMLService(tgtSysId, tgtSysURL, null, httpHdrs, httpMethod, svcTimeout, reqElem);
	}

	public static Element consumeXMLService(String tgtSysId, URL tgtSysURL, Proxy httpProxy, Map<String, String> httpHdrs, String httpMethod, long svcTimeout, Element reqElem) throws Exception {
		String resStr = consumeServiceV3(tgtSysId, tgtSysURL, httpProxy, httpHdrs, httpMethod, svcTimeout, XMLTransformer.toString(reqElem)).getPayloadString();
		if (resStr != null) {
			try {
				//return XMLTransformer.getNewDocumentBuilder().parse(new ByteArrayInputStream(resStr.getBytes(Charset.forName("UTF-8")))).getDocumentElement();
				return XMLTransformer.getNewDocumentBuilder().parse(new InputSource(new StringReader(resStr))).getDocumentElement();
			}
			catch (Exception x) {
				logger.warn(String.format("%s_ERR Error parsing XML response from service <%s>: %s", tgtSysId, tgtSysURL, resStr), x);
			}
		}
		
		return null;
	}

	public static JSONObject consumeJSONService(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, JSONObject reqJson) throws Exception {
		return consumeJSONService(tgtSysId, tgtSysURL, null, httpHdrs, HTTP_METHOD_POST, DEFAULT_SERVICE_TIMEOUT_MILLIS, reqJson);
	}

	public static JSONObject consumeJSONService(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, String httpMethod, JSONObject reqJson) throws Exception {
		return consumeJSONService(tgtSysId, tgtSysURL, null, httpHdrs, httpMethod, DEFAULT_SERVICE_TIMEOUT_MILLIS, reqJson);
	}

	public static JSONObject consumeJSONService(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, String httpMethod, long svcTimeout, JSONObject reqJson) throws Exception {
		return consumeJSONService(tgtSysId, tgtSysURL, null, httpHdrs, httpMethod, svcTimeout, reqJson); 	
	}

	public static JSONObject consumeJSONService(String tgtSysId, URL tgtSysURL, Proxy httpProxy, Map<String, String> httpHdrs, String httpMethod, long svcTimeout, JSONObject reqJson) throws Exception {
		String resStr = consumeServiceV3(tgtSysId, tgtSysURL, httpProxy, httpHdrs, httpMethod, svcTimeout, (reqJson != null) ? reqJson.toString() : "").getPayloadString();
		if (resStr != null) {
			try {
				return new JSONObject(new JSONTokener(new ByteArrayInputStream(resStr.getBytes())));
			}
			catch (Exception x) {
				logger.warn(String.format("%s_ERR Error parsing JSONObject response from service <%s>: %s", tgtSysId, tgtSysURL, resStr), x);
			}
		}
		
		return null;
	}
	
	public static JSONArray consumeJSONReturnJSONArray(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, String httpMethod, JSONObject reqJson) throws Exception {
		return consumeJSONReturnJSONArray(tgtSysId, tgtSysURL, httpHdrs, httpMethod, DEFAULT_SERVICE_TIMEOUT_MILLIS, reqJson);
	}
	
	public static JSONArray consumeJSONReturnJSONArray(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs, String httpMethod, long svcTimeout, JSONObject reqJson) throws Exception {
		String resStr = consumeServiceV3(tgtSysId, tgtSysURL, null, httpHdrs, httpMethod, svcTimeout, (reqJson != null) ? reqJson.toString() : "").getPayloadString();
		if (resStr != null) {
			try {
				return new JSONArray(new JSONTokener(new ByteArrayInputStream(resStr.getBytes())));
			}
			catch (Exception x) {
				logger.warn(String.format("%s_ERR Error parsing JSONArray response from service <%s>: %s", tgtSysId, tgtSysURL, resStr), x);
			}
		}
		
		return null;
	}

	@Deprecated
	private static String consumeServiceV2(String tgtSysId, URL tgtSysURL, Proxy httpProxy, Map<String, String> httpHdrs, String httpMethod, long serviceTimeout, String payload) {
		HttpURLConnection svcConn = null;
		
		try {
			svcConn = (HttpURLConnection) ((httpProxy != null) ? tgtSysURL.openConnection(httpProxy) : tgtSysURL.openConnection());
			svcConn.setRequestMethod(httpMethod);
			svcConn.setReadTimeout((int) serviceTimeout);
			
			// Set HTTP headers. Irrespective of whether service has specified HTTP headers, always add
			// the 'Accept-Encoding' header with value as 'deflate, gzip'. This class supports decoding
			// of only deflate and gzip encodings.
			svcConn.setRequestProperty(HTTP_HEADER_ACCEPT_ENCODING, HTTP_HEADER_ACCEPT_ENCODING_VALUES);
			if (httpHdrs != null) {
				Set<Entry<String,String>> httpHeaders = httpHdrs.entrySet();
				if (httpHeaders != null && httpHeaders.size() > 0) {
					Iterator<Entry<String,String>> httpHeadersIter = httpHeaders.iterator();
					while (httpHeadersIter.hasNext()) {
						Entry<String,String> httpHeader = httpHeadersIter.next();
						svcConn.setRequestProperty(httpHeader.getKey(), httpHeader.getValue());
					}
				}
			}
			
			if (logger.isInfoEnabled()) {
				logger.info(String.format("%s_RQ = %s", tgtSysId, payload));
			}
			
			if (HTTP_METHOD_POST.equals(httpMethod) || HTTP_METHOD_PUT.equals(httpMethod)) {
				svcConn.setDoOutput(true);
				OutputStream httpOut = svcConn.getOutputStream();
				httpOut.write(payload.getBytes());
				httpOut.flush();
				httpOut.close();
			}
			
			int resCode = svcConn.getResponseCode();
			logger.debug(String.format("Receiving response from %s with HTTP response status: %s", tgtSysId, resCode));
			
			if (resCode == HttpURLConnection.HTTP_OK || (HttpURLConnection.HTTP_ACCEPTED == resCode && "COMMCACHE".equals(tgtSysId))) {
				String resStr = readInputStreamAsString(svcConn.getInputStream(), svcConn.getContentType(), svcConn.getContentEncoding());
				if (logger.isInfoEnabled()) {
					logger.info(String.format("%s_RS = %s", tgtSysId, resStr));
				}
				return resStr;
			}
		}
		catch (Exception x) {
			logger.warn(String.format("%s_ERR Service <%s> Consume Error", tgtSysId, tgtSysURL), x);
		}
		finally {
			if (svcConn != null) {
				svcConn.disconnect();
			}
		}
		
		return null;
	}
	
	private static HttpTemplate consumeServiceV3(String tgtSysId, URL tgtSysURL, Proxy httpProxy, Map<String, String> httpHdrs, String httpMethod, long serviceTimeout, String payload) {
		HttpURLConnection svcConn = null;
		HttpTemplate httpTemplate = new HttpTemplate();
		
		try {
			svcConn = (HttpURLConnection) ((httpProxy != null) ? tgtSysURL.openConnection(httpProxy) : tgtSysURL.openConnection());
			svcConn.setRequestMethod(httpMethod);
			svcConn.setReadTimeout((int) serviceTimeout);
			
			// Set HTTP headers. Irrespective of whether service has specified HTTP headers, always add
			// the 'Accept-Encoding' header with value as 'deflate, gzip'. This class supports decoding
			// of only deflate and gzip encodings.
			svcConn.setRequestProperty(HTTP_HEADER_ACCEPT_ENCODING, HTTP_HEADER_ACCEPT_ENCODING_VALUES);
			if (httpHdrs != null) {
				Set<Entry<String,String>> httpHeaders = httpHdrs.entrySet();
				if (httpHeaders != null && httpHeaders.size() > 0) {
					Iterator<Entry<String,String>> httpHeadersIter = httpHeaders.iterator();
					while (httpHeadersIter.hasNext()) {
						Entry<String,String> httpHeader = httpHeadersIter.next();
						svcConn.setRequestProperty(httpHeader.getKey(), httpHeader.getValue());
					}
				}
			}
			
			if (logger.isInfoEnabled()) {
				logger.info(String.format("%s_RQ = %s", tgtSysId, payload));
			}
			
			if (HTTP_METHOD_POST.equals(httpMethod) || HTTP_METHOD_PUT.equals(httpMethod)) {
				svcConn.setDoOutput(true);
				OutputStream httpOut = svcConn.getOutputStream();
				httpOut.write(payload.getBytes());
				httpOut.flush();
				httpOut.close();
			}
			
			int resCode = svcConn.getResponseCode();
			httpTemplate.setStatusCode(resCode);
			httpTemplate.setHtttpHeaders(svcConn.getHeaderFields());
			logger.debug(String.format("Receiving response from %s with HTTP response status: %s", tgtSysId, resCode));
			
			if (resCode == HttpURLConnection.HTTP_OK || (HttpURLConnection.HTTP_ACCEPTED == resCode && "COMMCACHE".equals(tgtSysId) || (HttpURLConnection.HTTP_CREATED == resCode && "OPSTODO".equals(tgtSysId)))) {
				String resStr = readInputStreamAsString(svcConn.getInputStream(), svcConn.getContentType(), svcConn.getContentEncoding());
				if (logger.isInfoEnabled()) {
					logger.info(String.format("%s_RS = %s", tgtSysId, resStr));
				}
				httpTemplate.setPayload(resStr);
			}
			else if (resCode >=400) {
				InputStream errStream = svcConn.getErrorStream();
				String errStr =  new BufferedReader(new InputStreamReader(errStream)).lines().collect(Collectors.joining("\n"));
				httpTemplate.setError(errStr);
			}
		}
		catch (Exception x) {
			logger.warn(String.format("%s_ERR Service <%s> Consume Error", tgtSysId, tgtSysURL), x);
		}
		finally {
			if (svcConn != null) {
				svcConn.disconnect();
			}
		}
		
		return httpTemplate;
	}

//	private static InputStream consumeService(String tgtSysId, HttpURLConnection svcConn, Map<String, String> httpHdrs, String httpMethod, long serviceTimeout, byte[] payload) throws Exception {
//		svcConn.setDoOutput(true);
//		//svcConn.setRequestMethod("POST");
//		svcConn.setRequestMethod(httpMethod);
//		svcConn.setReadTimeout((int) serviceTimeout);
//		
//		if (httpHdrs != null) {
//			Set<Entry<String,String>> httpHeaders = httpHdrs.entrySet();
//			if (httpHeaders != null && httpHeaders.size() > 0) {
//				Iterator<Entry<String,String>> httpHeadersIter = httpHeaders.iterator();
//				while (httpHeadersIter.hasNext()) {
//					Entry<String,String> httpHeader = httpHeadersIter.next();
//					svcConn.setRequestProperty(httpHeader.getKey(), httpHeader.getValue());
//				}
//			}
//		}
//		
//		logger.trace(String.format("Sending request to %s",tgtSysId));
//		if ("POST".equals(httpMethod) || "PUT".equals(httpMethod)) {
//			OutputStream httpOut = svcConn.getOutputStream();
//			httpOut.write(payload);
//			httpOut.flush();
//			httpOut.close();
//		}
//		
//		int resCode = svcConn.getResponseCode();
//		logger.debug(String.format("Receiving response from %s with HTTP response status: %s", tgtSysId, resCode));
//		
//		if (resCode == HttpURLConnection.HTTP_OK || (HttpURLConnection.HTTP_ACCEPTED == resCode && "COMMCACHE".equals(tgtSysId))) {
//			return svcConn.getInputStream();
//		}
//		
//		return null;
//	}

//	@Deprecated
//	public static JSONArray produceJSONArrayResponse(String tgtSysId, String urlToRead, Map<String, String> httpHdrs) throws Exception {
//		HttpURLConnection conn = null;
//		try {
//			URL url = new URL(urlToRead);
//			// To Handle space and special Characters in URL
//			URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
//			url = uri.toURL();
//	
//			conn = (HttpURLConnection) url.openConnection();
//	
//			InputStream httpResStream = produceService(tgtSysId, conn, httpHdrs);
//			if (httpResStream != null) {
//				
//				JSONArray resJson = new JSONArray(new JSONTokener(httpResStream));
//				if (logger.isInfoEnabled()) {
//					logger.info(String.format("%s_RS = %s", tgtSysId, resJson.toString()));
//				}
//				return resJson;
//				
//			}
//		}
//		catch (Exception x) {
//			logger.warn(String.format("%s_ERR JSON Service <%s> Consume Error", tgtSysId, urlToRead), x);
//		}
//		finally {
//			if (conn != null) {
//				conn.disconnect();
//			}
//		}
//		return null;
//	}

//	/**
//	 * @deprecated replaced by consumeJSONService(String tgtSysId, String urlToRead,  Map<String, String> httpHdrs, String httpMethod, null)
//	 */
//	@Deprecated
//	public static JSONObject produceJSONObjectResponse(String tgtSysId, String urlToRead,  Map<String, String> httpHdrs) throws Exception {
//		HttpURLConnection conn = null;
//		try {
//			URL url = new URL(urlToRead);
//			// To Handle space and special Characters in URL
//			URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
//			url = uri.toURL();
//	
//			conn = (HttpURLConnection) url.openConnection();
//	
//			InputStream httpResStream = produceService(tgtSysId, conn, httpHdrs);
//			if (httpResStream != null) {
//				
//				JSONObject resJson = new JSONObject(new JSONTokener(httpResStream));
//				if (logger.isInfoEnabled()) {
//					logger.info(String.format("%s_RS = %s", tgtSysId, resJson.toString()));
//				}
//				return resJson;
//				
//			}
//		}
//		catch (Exception x) {
//			logger.warn(String.format("%s_ERR JSON Service <%s> Consume Error", tgtSysId, urlToRead), x);
//		}
//		finally {
//			if (conn != null) {
//				conn.disconnect();
//			}
//		}
//		return null;
//	}
	
//	@Deprecated
//	private static InputStream produceService(String tgtSysId, HttpURLConnection svcConn, Map<String, String> httpHdrs)
//			throws Exception {
//
//		svcConn.setRequestMethod(HTTP_METHOD_GET);
//
//		if (httpHdrs != null) {
//			Set<Entry<String, String>> httpHeaders = httpHdrs.entrySet();
//			if (httpHeaders != null && httpHeaders.size() > 0) {
//				Iterator<Entry<String, String>> httpHeadersIter = httpHeaders.iterator();
//				while (httpHeadersIter.hasNext()) {
//					Entry<String, String> httpHeader = httpHeadersIter.next();
//					svcConn.setRequestProperty(httpHeader.getKey(), httpHeader.getValue());
//				}
//			}
//		}
//		
//		logger.trace(String.format("Sending request to %s", tgtSysId));
//		
//		int resCode = svcConn.getResponseCode();
//		logger.debug(String.format("Receiving response from %s with HTTP response status: %s", tgtSysId, resCode));
//		if (resCode == HttpURLConnection.HTTP_OK) {
//			return svcConn.getInputStream();
//		}
//
//		return null;
//	}

	private static String readInputStreamAsString(InputStream inStream, String contentType, String contentEncoding) throws IOException {
		ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();
		
		String[] contentEncodings = (contentEncoding != null) ? contentEncoding.split(", ") : new String[0];
		try (InputStream readStream = getInputStreamForContentEncodings(inStream, Arrays.asList(contentEncodings))) {
			int readBytesLen = 0;
			byte[] readBytesBuffer = new byte[2048];
			while ((readBytesLen = readStream.read(readBytesBuffer)) > 0) {
				byteOutStream.write(readBytesBuffer, 0, readBytesLen);
			}
		}
		
		Charset strCharset = Charset.defaultCharset();
		if (contentType != null) {
			// Content-Type string converted to lower case because RFC7231 (sections 3.1.1.1 & 3.1.1.2) 
			// specifies parameter name and value tokens as case-insensitive 
			Matcher matcher = PATTERN_CONTENT_TYPE_CHARSET.matcher(contentType.toLowerCase());
			if (matcher.find()) {
				// The replaceAll method removes spaces before and after '=' character
				String charsetStr = matcher.group().replaceAll("[ ]*", "").substring("charset=".length());
				try {
					strCharset = Charset.forName(charsetStr);
				}
				catch (Exception x) {
					logger.warn(String.format("Character set %s not found", charsetStr), x);
				}
			}
		}
		
		byte[] inStreamBytes = byteOutStream.toByteArray();
		return (new String(inStreamBytes, strCharset)).replaceAll("\n", "");
	}
	
	// Following code handles only deflate, gzip and x-gzip encoding format. To fully support RFC7231 (section 3.1.2), support for 
	// 'compress' should be added. 
	private static InputStream getInputStreamForContentEncodings(InputStream inStream, List<String> contentEncodings) throws IOException {
		if (contentEncodings == null || contentEncodings.size() == 0) {
			return inStream;
		}
		
		String contentEncoding = contentEncodings.remove(contentEncodings.size() - 1);
		if (HTTP_ENCODING_DEFLATE.equals(contentEncoding)) {
			return getInputStreamForContentEncodings(new InflaterInputStream(inStream), contentEncodings);
		}
		if (HTTP_ENCODING_GZIP.equals(contentEncoding) || HTTP_ENCODING_XGZIP.equals(contentEncoding)) {
			return getInputStreamForContentEncodings(new GZIPInputStream(inStream), contentEncodings);
		}
		
		return inStream;
	}
	
//	@Deprecated
//	public static Element ConsumeAtomService(String tgtSysId, URL tgtSysURL, Map<String, String> httpHdrs){
//		
//		HttpURLConnection svcConn = null;
//		try {
//			//TODO: once it is whitelisted in SIT we will need to remove
//			Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("10.18.1.42",8888));
//			svcConn = (HttpURLConnection)tgtSysURL.openConnection(proxy);
//			
//			//svcConn = (HttpURLConnection) tgtSysURL.openConnection();
//			svcConn.setRequestMethod("GET");
//
//			if (httpHdrs != null) {
//				Set<Entry<String, String>> httpHeaders = httpHdrs.entrySet();
//				if (httpHeaders != null && httpHeaders.size() > 0) {
//					Iterator<Entry<String, String>> httpHeadersIter = httpHeaders.iterator();
//					while (httpHeadersIter.hasNext()) {
//						Entry<String, String> httpHeader = httpHeadersIter.next();
//						svcConn.setRequestProperty(httpHeader.getKey(), httpHeader.getValue());
//					}
//				}
//			}
//			int responseCode = svcConn.getResponseCode();
//			logger.debug("GET Response Code :: " + responseCode);
//
//			if (responseCode == HttpURLConnection.HTTP_OK) { // success
//				Element resElem = XMLTransformer.getNewDocumentBuilder().parse(svcConn.getInputStream())
//						.getDocumentElement();
//				return resElem;
//			}
//		} catch (Exception x) {
//			x.printStackTrace();
//			logger.warn(String.format("%s_ERR XML Service <%s> Consume Error", tgtSysId, tgtSysURL), x);
//		}
//
//		finally {
//			if (svcConn != null) {
//				svcConn.disconnect();
//			}
//		}
//		return null;
//	}
	
	@Deprecated
	public static JSONObject getErrorMessage(String tgtSysId, String urlToRead, Map<String, String> httpHdrs,
			String httpMethod, JSONObject reqJson) {
		
		HttpURLConnection svcConn = null;
		
		try {
			URL url = new URL(urlToRead);
			// To Handle space and special Characters in URL
			URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
			url = uri.toURL();
			
			svcConn = (HttpURLConnection) url.openConnection();
			svcConn.setRequestMethod(httpMethod);
			String reqJsonStr = reqJson!=null ? reqJson.toString() : Constants.JSON_OBJECT_EMPTY; 
			
			if (logger.isInfoEnabled()) {
				logger.info(String.format("%s_RQ = %s", tgtSysId, reqJsonStr));
			}
			InputStream httpResStream=null;
			
			if (httpHdrs != null) {
				Set<Entry<String,String>> httpHeaders = httpHdrs.entrySet();
				if (httpHeaders != null && httpHeaders.size() > 0) {
					Iterator<Entry<String,String>> httpHeadersIter = httpHeaders.iterator();
					while (httpHeadersIter.hasNext()) {
						Entry<String,String> httpHeader = httpHeadersIter.next();
						svcConn.setRequestProperty(httpHeader.getKey(), httpHeader.getValue());
					}
				}
			}

			logger.trace(String.format("Sending request to %s", tgtSysId));
			if(httpMethod.equals(HTTP_METHOD_POST) || HTTP_METHOD_PUT.equals(httpMethod)) {
				svcConn.setDoOutput(true);
				OutputStream httpOut = svcConn.getOutputStream();
				httpOut.write(reqJsonStr.getBytes());
				httpOut.flush();
				httpOut.close();
			}
			
			int resCode = svcConn.getResponseCode();
			logger.debug(String.format("Receiving Error response from %s with HTTP response status: %s", tgtSysId, resCode));
			
			if(resCode == HttpURLConnection.HTTP_INTERNAL_ERROR || resCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT || resCode == HttpURLConnection.HTTP_UNAUTHORIZED){
				httpResStream =svcConn.getErrorStream();
			}
			if (httpResStream != null) {
				JSONObject resJson = new JSONObject(new JSONTokener(httpResStream));
				if (logger.isInfoEnabled()) {
					logger.info(String.format("Error %s_RS = %s", tgtSysId, resJson.toString()));
				}
				return resJson;
			}
		}
		catch (Exception x) {
			logger.warn(String.format("%s_ERR JSON Service <%s> Error", tgtSysId, urlToRead), x);
		}
		finally {
			if (svcConn != null) {
				svcConn.disconnect();
			}
		}
		
		return null;
	}
	
}
