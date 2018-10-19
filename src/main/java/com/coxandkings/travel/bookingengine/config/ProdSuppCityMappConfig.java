package com.coxandkings.travel.bookingengine.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;

public class ProdSuppCityMappConfig implements Constants{
	
	private static Map<String, String> mHttpHeaders = new HashMap<String, String>();
	private static String getSupplierNameURL;
	private static Element mReqXMLShell;
	private static String suppCityMappingFromNameURL; 
	private static String suppCityMappingFromCodeURL;
	private static URL mTlgxSupplierCrossRefURL;
	
	private static final String PRODSUPPCITYMAPPING = "ProdSuppCityMapping";
	private static final String TLGXSUPPCROSSREF_URL = "tlgxSupplierCrossRefURL";
	private static final String GETSUPPNAME_URL = "getSupplierNameURL";
	private static final String SUPPCITYMAPFROMNAME_URL = "suppCityMappingFromNameURL";
	private static final String SUPPCITYMAPFROMCODE_URL = "suppCityMappingFromCodeURL";
	
	private static final Logger logger = LogManager.getLogger(ProdSuppCityMappConfig.class);
	
	@LoadConfig (configType = ConfigType.COMMON)
	public static void loadConfig() {
		
		mHttpHeaders.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
		
		Document configDoc = MongoProductConfig.getConfig(PRODSUPPCITYMAPPING);
		getSupplierNameURL = configDoc.getString(GETSUPPNAME_URL);
		mReqXMLShell = XMLTransformer.fromEscapedString(configDoc.getString(CONFIG_PROP_SI_REQ_XML_SHELL));
		
		try {
			mTlgxSupplierCrossRefURL = new URL(configDoc.getString(TLGXSUPPCROSSREF_URL));
		} catch (MalformedURLException e) {
			logger.warn(String.format("Error occurred while initializing TlgxSupplierCrossReference service URL in %s configuration", "ProdSuppCityMapping"), e);
		}
		suppCityMappingFromNameURL = configDoc.getString(SUPPCITYMAPFROMNAME_URL);
		suppCityMappingFromCodeURL = configDoc.getString(SUPPCITYMAPFROMCODE_URL);
	}
	
	public static Element getmReqXMLShell() {
		return mReqXMLShell;
	}

	public static Map<String, String> getHttpHeaders() {
		return mHttpHeaders;
	}
	
	public static URL getTlgxSupplierCrossRefURL() {
		return mTlgxSupplierCrossRefURL;
	}
	
	public static String getSuppCityMappingFromNameURL() {
		return suppCityMappingFromNameURL;
	}

	public static String getSuppCityMappingFromCodeURL() {
		return suppCityMappingFromCodeURL;
	}

	public static String getGetSupplierNameURL() {
		return getSupplierNameURL;
	}

}
