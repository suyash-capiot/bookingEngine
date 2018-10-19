package com.coxandkings.travel.bookingengine.config;

import java.net.URL;
import java.util.Map;

import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.utils.Constants;

public class CommercialCacheConfig implements Constants{

	
//	private static Map<String, URL> mCCConfig = new HashMap<String, URL>();
//	private static Map<String, String> mHttpHeaders = new HashMap<String, String>();
	
	private static ServicesGroupConfig mOpConfig;

	@LoadConfig (configType = ConfigType.COMMON)
//	@SuppressWarnings("unchecked")
	public static void loadConfig() {
//		mHttpHeaders.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
//		org.bson.Document configDoc = MongoProductConfig.getConfig("CommercialCache");
//		List<Document> opConfigDocs = (List<Document>) configDoc.get("operations");
//		if (opConfigDocs != null) {
//			for (Document opConfigDoc : opConfigDocs) {
//				String opName = opConfigDoc.getString("name");
//				try {
//					URL serviceURL = new URL(opConfigDoc.getString("serviceURL"));
//					mCCConfig.put(opName, serviceURL);
//
//				} catch (MalformedURLException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//
//			}
//		}

		mOpConfig = new ServicesGroupConfig(CONFIG_PROP_SERVICES, MongoProductConfig.getConfig("CommercialCache"));
	}

	public static URL getServiceURL(String operationName) {
		//return mCCConfig.get(operationName);
		ServiceConfig svcConfig = mOpConfig.getServiceConfig(operationName);
		return ((svcConfig != null) ? svcConfig.getServiceURL() : null);
	}

	public static Map<String, String> getmHttpHeaders() {
		//return mHttpHeaders;
		return mOpConfig.getHttpHeaders();
	}
	

	
}