package com.coxandkings.travel.bookingengine.config;

import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;


public class ServiceConfig implements Constants {

	private String mTypeName;
	//private URL mServiceURL;
	private UriComponentsBuilder mServiceURIBldr;
	private String mServiceURLQuery;
	private String mReqJSONShell;
	private Element mReqXMLShell;
	private long mReqTimeoutMillis;
	private String mHttpMethod;
	private Map<String, String> mHttpHeaders;
	private Proxy mHttpProxy;
	
	private static final Logger logger = LogManager.getLogger(ServiceConfig.class);
	
	public ServiceConfig(ServicesGroupConfig svcsGrpCfg, org.bson.Document serviceConfig) {
		mTypeName = serviceConfig.getString(CONFIG_PROP_TYPE);
//		// TODO: Following is temporary. Once all the booking engine configuration is migrated, 
//		// the following 'if' condition should be removed. 
//		if (mTypeName == null) {
//			mTypeName = serviceConfig.getString(CONFIG_PROP_NAME);
//		}
		mReqJSONShell = serviceConfig.getString(Constants.CONFIG_PROP_REQ_JSON_SHELL);
		mReqXMLShell = XMLTransformer.fromEscapedString(serviceConfig.getString(CONFIG_PROP_REQ_XML_SHELL));
		
		String svcBaseURIStr = (svcsGrpCfg != null && svcsGrpCfg.getServiceBaseURL() != null) ? svcsGrpCfg.getServiceBaseURL() : "";
		String svcURIStr = (String) serviceConfig.getOrDefault(CONFIG_PROP_SERVICE_URL, "");
		try {
			//URI svcBaseURI = new URI(svcBaseURIStr); 
			//URI svcURI = new URI(svcURIStr);
			//mServiceURL = (svcURI.isAbsolute()) ? svcURI.toURL() : svcBaseURI.resolve(svcURI).toURL();
			UriComponentsBuilder svcUriBldr = UriComponentsBuilder.fromUriString(svcURIStr);
			mServiceURIBldr = (svcUriBldr.build().toUri().isAbsolute()) ? svcUriBldr : UriComponentsBuilder.fromUriString(svcBaseURIStr).path(svcURIStr);
		}
		catch (Exception x) {
			logger.warn("Error occurred while initializing service URL for operation {}. Service base URL is <{}> and service URL is <{}>. Error: <{}>", mTypeName, svcBaseURIStr, svcURIStr, x);
		}

		mServiceURLQuery = serviceConfig.getString(CONFIG_PROP_SERVICE_URL_QUERY);
		if(Utils.isStringNotNullAndNotEmpty(mServiceURLQuery)) {
			mServiceURIBldr.query(mServiceURLQuery);
		}
		mReqTimeoutMillis = (long) serviceConfig.getInteger(CONFIG_PROP_SERVICE_TIMEOUT_MILLIS, (int) HTTPServiceConsumer.DEFAULT_SERVICE_TIMEOUT_MILLIS);
		String httpMthd = serviceConfig.getString(CONFIG_PROP_SERVICE_HTTP_METHOD);
		mHttpMethod = (httpMthd != null) ? httpMthd : HTTPServiceConsumer.HTTP_METHOD_POST;
		
		Map<String, String> serviceHttpHeaders = ServicesGroupConfig.loadHttpHeaders(serviceConfig);
		mHttpHeaders = (serviceHttpHeaders != null) ? serviceHttpHeaders : ((svcsGrpCfg != null && svcsGrpCfg.getHttpHeaders() != null) ? svcsGrpCfg.getHttpHeaders() : null);
		
		Proxy serviceHttpProxy = ServicesGroupConfig.loadHttpProxy(serviceConfig);
		mHttpProxy = (serviceHttpProxy != null) ? serviceHttpProxy : ((svcsGrpCfg != null && svcsGrpCfg.getHttpProxy() != null) ? svcsGrpCfg.getHttpProxy() : null);
	}
	
	public String getOperationName() {
		return getTypeName();
	}
	
	public String getRequestJSONShell() {
		return mReqJSONShell;
	}
	
	public Element getRequestXMLShell() {
		return mReqXMLShell;
	}

	public Map<String, String> getHttpHeaders() {
		return mHttpHeaders;
	}
	
	public String getHttpMethod() {
		return mHttpMethod;
	}

	public Proxy getHttpProxy() {
		return mHttpProxy;
	}
	
	public long getServiceTimeoutMillis() {
		return mReqTimeoutMillis;
	}
	
	/*public URL getServiceURL(Object... queryParams) {
		if (mServiceURLQuery != null && mServiceURLQuery.isEmpty() == false) {
			try {
				URI svcURI = mServiceURL.toURI();
				return (new URI(svcURI.getScheme(), svcURI.getAuthority(), svcURI.getPath(), String.format(mServiceURLQuery, queryParams), svcURI.getFragment())).toURL();
			}
			catch (Exception x) {
				logger.warn("An error occurred while setting query parameters in query part {} of URL {}: {}", mServiceURLQuery, mServiceURL, x);
			}
		}
		return mServiceURL;
	}*/

	public URL getServiceURL(Object... pathParams) {
		if (mServiceURIBldr != null) {
			try {
				return mServiceURIBldr.buildAndExpand(pathParams).toUri().toURL();
				//return (new URI(svcURI.getScheme(), svcURI.getAuthority(), svcURI.getPath(), String.format(mServiceURLQuery, queryParams), svcURI.getFragment())).toURL();
			}
			catch (Exception x) {
				logger.warn(String.format("An error occurred while setting path parameters of url", mServiceURIBldr.build().toUriString()));
			}
		}
		return null;
	}
	

	public URL getServiceURL(Map<String,?> pathParamsMap) {
		if (mServiceURIBldr != null) {
			try {
				return mServiceURIBldr.buildAndExpand(pathParamsMap).toUri().toURL();
				//return (new URI(svcURI.getScheme(), svcURI.getAuthority(), svcURI.getPath(), String.format(mServiceURLQuery, queryParams), svcURI.getFragment())).toURL();
			}
			catch (Exception x) {
				logger.warn(String.format("An error occurred while setting path parameters of url", mServiceURIBldr.build().toUriString()));
			}
		}
		return null;
	}

	public String getTypeName() {
		return mTypeName;
	}
	
	protected void setRequestXMLShell(Element reqXMLShell) {
		mReqXMLShell = reqXMLShell;
	}
	
	@Deprecated
	protected void setServiceURL(URL svcURL) {
		//mServiceURL = svcURL;
	}
	
}
