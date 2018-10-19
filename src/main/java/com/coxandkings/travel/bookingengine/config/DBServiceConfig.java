package com.coxandkings.travel.bookingengine.config;

import java.util.HashMap;
import java.util.Map;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.utils.Constants;

public class DBServiceConfig implements Constants{

	private static Map<String, String> mHttpHeaders = new HashMap<String, String>();
	private static String mDBServiceURL;
	private static String mDBServiceUpdateURL;
	
	@LoadConfig (configType = ConfigType.COMMON)
	public static void loadConfig() {
	
		mHttpHeaders.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
		
		Document configDoc = MongoProductConfig.getConfig("BookingDBConfig");
		mDBServiceURL = configDoc.getString("dBGetBookingURL");
		mDBServiceUpdateURL = configDoc.getString("dBUpdateURL");
	}
	
	public static Map<String, String> getHttpHeaders() {
		return mHttpHeaders;
	}

	public static String getDBServiceURL() {
		return mDBServiceURL;
	}
	
	public static String getDBUpdateURL() {
		return mDBServiceUpdateURL;
	}
	
}
