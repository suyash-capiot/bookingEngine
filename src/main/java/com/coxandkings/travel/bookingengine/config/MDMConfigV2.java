package com.coxandkings.travel.bookingengine.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.mongo.MongoConnect;
import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.mongodb.client.MongoCollection;

@Deprecated
public class MDMConfigV2 implements Constants{

	private static MongoConnect mMongoConn;
    private static URL mloginServiceURL;
   
    private static URL mdmUserLoginURL;
    private static URL  mdmUserLogoutURL;
 
    private static Map<String, String> mHttpHeaders = new HashMap<String, String>();
    private static  Map<String, String> mURLmap = new HashMap<String,String>();
    private static String mUserID;
	private static transient String mPassword;
    private static int bufferSize;
    private static final int DEFAULT_BUFFER_SIZE = 25;
    private static int clientLoginRedisTTLMins;
    
    private static final int DEFAULT_REDIS_TTL_MINS = 60;

    @LoadConfig (configType = ConfigType.COMMON)
    @SuppressWarnings("unchecked")
	public static void loadConfig() throws MalformedURLException {
    	
		Document configDoc = MongoProductConfig.getConfig("MDMAPI");
	    Document connDoc = (Document) configDoc.get("connection");
	    mMongoConn = MongoConnect.newInstance(connDoc);
	    mHttpHeaders.put(HTTP_HEADER_CONTENT_TYPE,  HTTP_CONTENT_TYPE_APP_JSON);
	    //Added for login through MDM
	    mdmUserLoginURL = new URL(configDoc.getString("mdmUserLogin"));
	    mdmUserLogoutURL = new URL(configDoc.getString("mdmUserLogout"));
	    mURLmap = (Map<String, String>) configDoc.get("serviceURLs");
	    mUserID = configDoc.getString("username");
		mPassword = configDoc.getString("password");
		bufferSize = configDoc.getInteger("bufferSize", DEFAULT_BUFFER_SIZE);
		clientLoginRedisTTLMins = configDoc.getInteger("clientLoginRedisTTLMins", DEFAULT_REDIS_TTL_MINS);
   }

    public static URL getMdmUserLogoutURL() {
		return mdmUserLogoutURL;
	}

	public static MongoCollection<Document> getCollection(String collName) {
	
        return mMongoConn.getCollection(collName);
    }


	public static String getmUserID() {
		return mUserID;
	}

	public static void setmUserID(String mUserID) {
		MDMConfigV2.mUserID = mUserID;
	}

	public static String getmPassword() {
		return mPassword;
	}

	public static void setmPassword(String mPassword) {
		MDMConfigV2.mPassword = mPassword;
	}

	public static URL getMloginServiceURL() {
		return mloginServiceURL;
	}

	public static void setMloginServiceURL(URL mloginServiceURL) {
		MDMConfigV2.mloginServiceURL = mloginServiceURL;
	}

	public static URL getMdmUserLoginURL() {
		return mdmUserLoginURL;
	}

	public static Map<String, String> getmHttpHeaders() {
		return mHttpHeaders;
	}

	public static String getURL(String key) {
		return mURLmap.getOrDefault(key, "");
	}
	public static int getBufferSize() {
		return bufferSize;
	}

	public static void setBufferSize(int bufferSize) {
		MDMConfigV2.bufferSize = bufferSize;
	}
	
    public static int getClientLoginRedisTTLMins() {
		return clientLoginRedisTTLMins;
	}

	public static void setClientLoginRedisTTLMins(int clientLoginRedisTTLMins) {
		MDMConfigV2.clientLoginRedisTTLMins = clientLoginRedisTTLMins;
	}
}
