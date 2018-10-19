package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoSearchSorterResponder;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirSearchSorterResponder;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class HolidaysSearchListnerResponder implements HolidayConstants {
	private List<ProductSupplier> mProdSuppliers;
	private List<JSONArray> mSearchRes;
	private boolean mSentFirstResponse;
	private JSONObject mReqJson;
	private JSONObject mReqHeaderJson;
	private URL mCallbackURL;
	private boolean mIsAsync;
	
	private static final Logger logger = LogManager.getLogger(HolidaysSearchListnerResponder.class);
	
	HolidaysSearchListnerResponder(List<ProductSupplier> prodSuppliers, JSONObject reqJson) {
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
	  
	    logger.trace("Received supplier response: ", suppResJson);
		JSONObject resBody = suppResJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray dynPkgArr = resBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		mSearchRes.add(dynPkgArr);
		logger.trace("Added supplier response to supplier responses list: {}", suppResJson);
		
		if (mIsAsync) {
			if ( (mSentFirstResponse == false && dynPkgArr.length() > 0) || mSearchRes.size() == mProdSuppliers.size() ) {
				mSentFirstResponse = true;
				
				Map<String, String> httpHdrs = new HashMap<String, String>();
				httpHdrs.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
				try {
					HTTPServiceConsumer.consumeJSONService("CallBack", mCallbackURL, httpHdrs, suppResJson);
				}
				catch (Exception x) {
					logger.warn(String.format("An exception occurred while responding to callback address %s", mCallbackURL.toString()), x);
				}
			}
		}
		
		logger.trace("Checking search response size of {} with number of product suppliers {}", mSearchRes.size(), mProdSuppliers.size());
		if (mSearchRes.size() == mProdSuppliers.size()) {
			synchronized(this) {
				notify();
			}
		}
	}
	
	JSONObject getSearchResponse() {
	    return HolidaysSearchSorterResponder.getSortedResponse(mReqHeaderJson, mSearchRes);
	}

}
