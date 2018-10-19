package com.coxandkings.travel.bookingengine.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.mongo.MongoConnect;
import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.CryptoUtil;
import com.mongodb.client.MongoCollection;

public class MDMConfig implements Constants {

    private static MongoConnect mMongoConn;
   // private static URL mloginServiceURL;
    //private static Map<String, String> mHttpHeaders = new HashMap<String, String>();
    private static int mRedisTTLMins;
    private static boolean mCallApi;
    private static String mApiUserID;
	private static transient String mApiPassword;
	private static ServicesGroupConfig mApiSvcsGrp;
    
    //TODO: change it based on the MDM config
    private static final int DEFAULT_REDIS_TTL_MINS = 60;

    @LoadConfig (configType = ConfigType.COMMON)
    public static void loadConfig() throws MalformedURLException {
        Document configDoc = MongoProductConfig.getConfig("MDM");
        Document connDoc = (Document) configDoc.get("connection");
        mMongoConn = MongoConnect.newInstance(connDoc);
        
        //added for login through MDM
        // mloginServiceURL = new URL(configDoc.getString("loginURL"));
        // mHttpHeaders.put(HTTP_HEADER_CONTENT_TYPE,  HTTP_CONTENT_TYPE_APP_JSON);
        mRedisTTLMins = configDoc.getInteger("clientLoginRedisTTLMins", DEFAULT_REDIS_TTL_MINS);
        mCallApi = configDoc.getBoolean("callApi");
        
        //added for api config
        Document apiCfgDoc = (Document) configDoc.get("apiConfig");
        if(apiCfgDoc!=null) {
        	mApiSvcsGrp = new ServicesGroupConfig("apiConfig", apiCfgDoc);
        	mApiUserID = (String) apiCfgDoc.getOrDefault("username", "");
        	mApiPassword = CryptoUtil.decrypt((String) apiCfgDoc.getOrDefault("password", ""));
        }
        
    }

    public static MongoCollection<Document> getCollection(String collName) {
        return mMongoConn.getCollection(collName);
    }

    public static void unloadConfig() {
        mMongoConn.close();
    }

	/*public static URL getLoginServiceURL() {
		return mloginServiceURL;
	}

	public static Map<String, String> getmHttpHeaders() {
		return mHttpHeaders;
	}*/

	public static int getClientLoginRedisTTLMins() {
		return mRedisTTLMins;
	}

	public static ServiceConfig getApiConfig(String typeName) {
		return mApiSvcsGrp.getServiceConfig(typeName);
	}
	
	public static ServicesGroupConfig getApiConfigGroup() {
		return mApiSvcsGrp;
	}

	public static String getApiUser() {
		return mApiUserID;
	}
	
	public static String getApiPassword() {
		return mApiPassword;
	}
	
	public static boolean callApi() {
		return mCallApi;
	}
}
