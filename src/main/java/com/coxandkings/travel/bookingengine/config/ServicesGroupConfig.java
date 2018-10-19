package com.coxandkings.travel.bookingengine.config;

import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.CryptoUtil;
import com.coxandkings.travel.bookingengine.utils.Utils;

public class ServicesGroupConfig implements Constants {
	
	private static final Logger logger = LogManager.getLogger(ServicesGroupConfig.class);
	private String mServiceGroupName;
	private String mServiceBaseURL;
	private Map<String, String> mHttpHeaders;
	private Proxy mHttpProxy;
	
	private Map<String, ServiceConfig> mServicesConfig;

	public ServicesGroupConfig(String groupName, Document grpConfigDoc) {
		this(groupName, grpConfigDoc, ServiceConfig.class);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public ServicesGroupConfig(String groupName, Document grpConfigDoc, Class svcConfigClass) {
		mServiceGroupName = groupName;
		mServiceBaseURL = grpConfigDoc.getString(CONFIG_PROP_SERVICE_BASE_URL);
		try { 
			new URL(mServiceBaseURL);
		}
		catch (MalformedURLException mux) {
			logger.warn("The service group base URL <{}> is not valid. Error: <{}>", mServiceBaseURL, mux);
		}
		mHttpHeaders = ServicesGroupConfig.loadHttpHeaders(grpConfigDoc);
		mHttpProxy = ServicesGroupConfig.loadHttpProxy(grpConfigDoc);
		
		try { 
			Constructor svcCfgConstructor = svcConfigClass.getConstructor(ServicesGroupConfig.class, Document.class);
			mServicesConfig = new HashMap<String, ServiceConfig>();
			List<Document> servicesConfig =  (List<Document>) grpConfigDoc.get(CONFIG_PROP_SERVICES);
			if (servicesConfig != null) {
				for (Document serviceConfig : servicesConfig) {
					ServiceConfig svcConfig = (ServiceConfig) svcCfgConstructor.newInstance(this, serviceConfig);
					mServicesConfig.put(svcConfig.getTypeName(), svcConfig);
				}
			}
		}
		catch (Exception x) {
			logger.warn(String.format("An error occurred when loading services configuration for group %s", mServiceGroupName), x);
		}
	}
	
	public Map<String, String> getHttpHeaders() {
		return mHttpHeaders;
	}
	
	Proxy getHttpProxy() {
		return mHttpProxy;
	}

	String getServiceBaseURL() {
		return mServiceBaseURL;
	}

	String getServiceGroupName() {
		return mServiceGroupName;
	}
	
	public ServiceConfig getServiceConfig(String typeName) {
		return mServicesConfig.get(typeName);
	}

	public static Proxy loadHttpProxy(Document grpConfigDoc) {
		if (grpConfigDoc == null) {
			return null;
		}
		
		Document httpProxyDoc = (Document) grpConfigDoc.get(CONFIG_PROP_HTTP_PROXY);
		if (httpProxyDoc == null) {
			return null;
		}
		
		String httpProxyServer = httpProxyDoc.getString(CONFIG_PROP_HTTP_PROXY_SERVER);
		int httpProxyPort = httpProxyDoc.getInteger(CONFIG_PROP_HTTP_PROXY_PORT, 80);
		
		return (httpProxyServer != null && httpProxyServer.isEmpty() == false && httpProxyPort > 0) ? new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpProxyServer, httpProxyPort)) : null;
	}

	@SuppressWarnings("unchecked")
	public
	static Map<String, String> loadHttpHeaders(Document grpConfigDoc) {
		if (grpConfigDoc == null) {
			return null;
		}
		
		List<Document> httpHeadersDoc = new ArrayList<Document>();
		Object httpHeadersObj = grpConfigDoc.get(CONFIG_PROP_HTTP_HEADERS);
		if (httpHeadersObj != null && httpHeadersObj instanceof List<?>) {
			List<Object> hdrsList = (List<Object>)  httpHeadersObj;
			for (Object hdrObj : hdrsList) {
				if (hdrObj instanceof Document) {
					httpHeadersDoc.add((Document) hdrObj);
				}
			}
		}
		
		if (httpHeadersDoc == null || httpHeadersDoc.size() == 0) {
			return null;
		}
		
		Map<String, String> httpHeaders = new HashMap<String, String>();
		for (Document httpHeaderDoc : httpHeadersDoc) {
			String httpHeader = httpHeaderDoc.getString(CONFIG_PROP_HEADER);
			if (httpHeader == null || httpHeader.isEmpty()) {
				continue;
			}
			
			Object valObj = httpHeaderDoc.get(CONFIG_PROP_VALUE);
			if (valObj == null) {
				continue;
			}
			
			if (HTTP_HEADER_AUTHORIZATION.equals(httpHeader)) {
				if (valObj instanceof Document) { 
					Document valDoc = (Document) valObj;
					// TODO: Remove hard-coded string "Basic"
					String authVal = getAuthorizationVal(valDoc);
					
					if (Utils.isStringNotNullAndNotEmpty(authVal)) {
						httpHeaders.put(HTTP_HEADER_AUTHORIZATION, authVal);
					}
				}
			}
			else {
				httpHeaders.put(httpHeader, valObj.toString());
			}
		}
		
		return (httpHeaders.size() > 0) ? httpHeaders : null;
	}

	private static String getAuthorizationVal(Document valDoc) {
		//TODO:remove hardcoding
		String authType = (String) valDoc.getOrDefault(CONFIG_PROP_TYPE, "Basic");
		switch(authType) {
		case "Basic":{
			String userID = valDoc.getString(CONFIG_PROP_USERID);
			String password = valDoc.getString(CONFIG_PROP_PASSWORD);
			if (Utils.isStringNotNullAndNotEmpty(userID) && Utils.isStringNotNullAndNotEmpty(password)) {
				return String.format("%s %s", authType, Base64.getEncoder().encodeToString(userID.concat(":").concat(CryptoUtil.decrypt(password)).getBytes()));
			}
		}
		case "OAuth 2.0":{
			String token = valDoc.getString("token");
			if(Utils.isStringNotNullAndNotEmpty(token))
				return String.format("%s %s", "Bearer", token);
		}
		default:return "";
		}
	}

}
