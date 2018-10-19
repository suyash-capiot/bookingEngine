package com.coxandkings.travel.bookingengine.config.mongo;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

@SuppressWarnings("unchecked")
public class MongoCommonConfig {

	private static final Logger logger = LogManager.getLogger(MongoCommonConfig.class);
	private static Document mConfigDoc = null;
	private static List<Document> nsContext = new ArrayList<Document>();
	private static List<SimpleEntry<String,String>> mTrackElems = new ArrayList<SimpleEntry<String,String>>();
	private static List<String> mOperations = new ArrayList<String>();
	
	private static final int DEFAULT_REDIS_SESSIONCTX_TTL_MINS = 30;
	private static int mRedisSessCtxTTLMins; 
	
	static {
		loadCommonConfig();
	
		mRedisSessCtxTTLMins = mConfigDoc.getInteger("redisSessionContextTTLMinutes", DEFAULT_REDIS_SESSIONCTX_TTL_MINS);
		nsContext = (ArrayList<Document>) mConfigDoc.get("NamespacesContext");
		if (nsContext == null) {
			logger.warn("Common configuration does not contain <NamespacesContext> definition");
		}
		
		// Tracking elements configuration retrieval
		ArrayList<Document> trackingElems = (ArrayList<Document>) mConfigDoc.get("TrackingElements");
		if (trackingElems == null) {
			logger.warn("Common configuration for does not contain <TrackingElements> definition");
		}
		else {
			for (Document trackingElem : trackingElems) {
				mTrackElems.add(new SimpleEntry<String,String>(trackingElem.getString("ElementName"), trackingElem.getString("ElementXPath")));
			}
		}
		
		//get List of operation on which Txn Id needs to be generated
		String operationsLstKey =  "CreateTxnIdOnOperations";
		if(mConfigDoc.containsKey(operationsLstKey)) {
			mOperations = (ArrayList<String>) mConfigDoc.get(operationsLstKey);
		}
	}

	public static List<Document> getNamespacesContextConfig() {
		return nsContext;
	}
	
	public static List<SimpleEntry<String,String>> getTrackingElements() {
		return mTrackElems;
	}
	
	public static List<String> getOperationListForTxnIdCreation() {
		return mOperations;
	}
	
	private static void loadCommonConfig() {
		mConfigDoc = MongoProductConfig.getConfig("COMMON");
		logger.info(String.format("MongoCommonConfig: configDoc=<%s>", (mConfigDoc != null) ? mConfigDoc : "null"));
	}
	
	public static int getRedisSessionContextTTLMins() {
		return mRedisSessCtxTTLMins;
	}
}
