package com.coxandkings.travel.bookingengine.orchestrator.acco;

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

import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class AccoSearchListenerResponder implements AccoConstants {
	private static final Logger logger = LogManager.getLogger(AccoSearchListenerResponder.class);
	private List<ProductSupplier> mProdSuppliers;
	private List<JSONArray> mSearchRes;
	private boolean mSentFirstResponse;
	private JSONObject mReqJson;
	private JSONObject mReqHeaderJson;
	private URL mCallbackURL;
	private boolean mIsAsync;
	
	AccoSearchListenerResponder(List<ProductSupplier> prodSuppliers, JSONObject reqJson) {
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
				// TODO: Handle this exception properly. That probably means that it should be logged.
				x.printStackTrace();
			}
		}
	}
	
	void receiveSupplierResponse(JSONObject suppResJson) {
		JSONArray roomStayArr = suppResJson == null?new JSONArray():suppResJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR).getJSONObject(0).getJSONArray(JSON_PROP_ROOMSTAYARR);
		mSearchRes.add(roomStayArr);
		if (mIsAsync) {
			if (mSentFirstResponse == false || mSearchRes.size() == mProdSuppliers.size()) {
				mSentFirstResponse = true;
				Map<String, String> httpHdrs = new HashMap<String, String>();
				httpHdrs.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
				try {
					HTTPServiceConsumer.consumeJSONService("CallBack", mCallbackURL, httpHdrs, getSearchResponse());
				}
				catch (Exception x) {
					logger.warn(String.format("An exception occurred while responding to callback address %s", mCallbackURL.toString()), x);
				}
			}
		}
		
		if (mSearchRes.size() == mProdSuppliers.size()) {
			// TODO: Is this right?
			synchronized(this) {
				notify();
			}
		}
	}
	
	JSONObject getSearchResponse() {
		JSONObject resJson = new JSONObject();
		JSONArray roomStayJsonArr = new JSONArray();
		JSONObject resBodyJson = (new JSONObject()).append(JSON_PROP_ACCOMODATIONARR, (new JSONObject()).put(JSON_PROP_ROOMSTAYARR, roomStayJsonArr));
		
		/*for(JSONArray tempJsonArr:mSearchRes) {
			for(Object roomStayJson:tempJsonArr) {
				roomStayJsonArr.put(roomStayJson);
			}
		}*/
		//this will return the response in a sorted order according to incentives or price
		JSONArray sortedArr = AccoSearchSorterResponder.getSortedResponse(mReqHeaderJson,mSearchRes);
			for(Object roomStayJson:sortedArr) {
				roomStayJsonArr.put(roomStayJson);
			}
		resJson.put(JSON_PROP_RESHEADER, mReqHeaderJson);
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		return resJson;
}
}
