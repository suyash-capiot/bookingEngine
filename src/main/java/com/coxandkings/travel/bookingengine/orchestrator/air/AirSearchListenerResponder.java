package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;

public class AirSearchListenerResponder implements AirConstants {
	private List<ProductSupplier> mProdSuppliers;
	private List<JSONArray> mSearchRes;
	private boolean mSentFirstResponse;
	private JSONObject mReqJson;
	private JSONObject mReqHeaderJson;
	private URL mCallbackURL;
	private boolean mIsAsync;
	
	private static final Logger logger = LogManager.getLogger(AirSearchListenerResponder.class);
	
	AirSearchListenerResponder(List<ProductSupplier> prodSuppliers, JSONObject reqJson) {
		mProdSuppliers = prodSuppliers;
		mSearchRes = Collections.synchronizedList(new ArrayList<JSONArray>());
		mReqJson = reqJson;
		mReqHeaderJson = mReqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject clientCtxJson = mReqHeaderJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String callbackAddr = clientCtxJson.optString(JSON_PROP_CLIENTCALLBACK);
		if (callbackAddr != null && callbackAddr.isEmpty() == false) { 
			try {
				mCallbackURL = new URL(clientCtxJson.getString(JSON_PROP_CLIENTCALLBACK));
				mIsAsync = true;
			}
			catch (Exception x) {
				logger.warn("An error occurred when setting asynchronous callback URL: {}", x);
			}
		}
	}
	
	void receiveSupplierResponse(JSONObject suppResJson) {
		logger.trace("Received supplier response: {}", suppResJson);
		JSONObject resBody = suppResJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray itinsJsonArr = resBody.getJSONArray(JSON_PROP_PRICEDITIN);
		mSearchRes.add(itinsJsonArr);
		logger.trace("Added supplier response to supplier responses list: {}", suppResJson);
		if (mIsAsync) {
			if ( (mSentFirstResponse == false && itinsJsonArr.length() > 0) || mSearchRes.size() == mProdSuppliers.size() ) {
				mSentFirstResponse = true;
				AirSearchSorterResponder.respondToCallback(mCallbackURL, mReqHeaderJson, mSearchRes);
			}
		}
		
		logger.trace("Checking search response size of {} with number of product suppliers {}", mSearchRes.size(), mProdSuppliers.size());
		if (mSearchRes.size() == mProdSuppliers.size()) {
			synchronized(this) {
				notify();
			}
		}
	}
	
	void receiveSupplierResponseV2(JSONObject suppResJson, Integer cabinClassJsonArrLength) {
		logger.trace("Received supplier response: {}", suppResJson);
		JSONObject resBody = suppResJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray itinsJsonArr = resBody.getJSONArray(JSON_PROP_PRICEDITIN);
		//Integer cabinClassJsonArrLength=reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_CABINTYPE).length();
		mSearchRes.add(itinsJsonArr);
		logger.trace("Added supplier response to supplier responses list: {}", suppResJson);
		if (mIsAsync) {
			if ( (mSentFirstResponse == false && itinsJsonArr.length() > 0) || mSearchRes.size() == mProdSuppliers.size()*cabinClassJsonArrLength ) {
				mSentFirstResponse = true;
				AirSearchSorterResponder.respondToCallback(mCallbackURL, mReqHeaderJson, mSearchRes);
			}
		}
		
		logger.trace("Checking search response size of {} with number of product suppliers {}", mSearchRes.size(), mProdSuppliers.size());
		if (mSearchRes.size() == mProdSuppliers.size()*cabinClassJsonArrLength) {
			synchronized(this) {
				notify();
			}
		}
	}
	
	JSONObject getSearchResponse() {
		return AirSearchSorterResponder.getSortedResponse(mReqHeaderJson, mSearchRes);
	}

}
