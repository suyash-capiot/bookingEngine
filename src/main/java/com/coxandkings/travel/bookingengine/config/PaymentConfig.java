package com.coxandkings.travel.bookingengine.config;

import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.utils.Constants;

public class PaymentConfig implements Constants {

	private static final String CONFIG_PROP_PAYMENT_CONFIG = "PaymentConfig"; 
//	private static Map<String, String> mPaymentConfig = new HashMap<String, String>();
//	private static Map<String, String> mHttpHeaders = new HashMap<String, String>();
	private static ServicesGroupConfig mPaymentServicesConfig;

	private static boolean enablePaymentVerify;
	@LoadConfig (configType = ConfigType.COMMON)
//	@SuppressWarnings("unchecked")
	public static void loadConfig() {
		
//		mHttpHeaders.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
//		org.bson.Document configDoc = MongoProductConfig.getConfig("PaymentConfig");
		mPaymentServicesConfig = new ServicesGroupConfig(CONFIG_PROP_PAYMENT_CONFIG, MongoProductConfig.getConfig(CONFIG_PROP_PAYMENT_CONFIG));
		enablePaymentVerify = MongoProductConfig.getConfig(CONFIG_PROP_PAYMENT_CONFIG).getBoolean("enablePaymentVerify", false);
//		List<Document> opConfigDocs = (List<Document>) configDoc.get("operations");
//		if (opConfigDocs != null) {
//			for (Document opConfigDoc : opConfigDocs) {
//				
//					String opName = opConfigDoc.getString("name");
//					String serviceURL =opConfigDoc.getString("serviceURL");
//					mPaymentConfig.put(opName, serviceURL);
//
//			}
//		}

	}

	@Deprecated
	public static String getServiceURL(String operationName) {
//		return mPaymentConfig.get(operationName);
		ServiceConfig svcCfg = (mPaymentServicesConfig != null) ? mPaymentServicesConfig.getServiceConfig(operationName) : null;
		return (svcCfg != null) ? svcCfg.getServiceURL().toString() : "";
	}

//	public static Map<String, String> getmHttpHeaders() {
//		return mHttpHeaders;
//	}
	
	public static ServiceConfig getServiceConfig(String svcType) {
		return mPaymentServicesConfig.getServiceConfig(svcType);
	}

	public static boolean isEnablePaymentVerify() {
		return enablePaymentVerify;
	}
	
}
