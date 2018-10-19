package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;

public class ActivitySearchListenerResponder implements ActivityConstants {

	private static final Logger logger = LogManager.getLogger(ActivitySearchListenerResponder.class);
	private List<ProductSupplier> mProdSuppliers;
	private List<JSONArray> mSearchRes;
	private boolean mSentFirstResponse;
	private JSONObject mReqJson;
	private JSONObject mReqHeaderJson;
	private URL mCallbackURL;
	private boolean mIsAsync;

	ActivitySearchListenerResponder(List<ProductSupplier> prodSuppliers, JSONObject reqJson) {
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

	void receiveSupplierResponse(JSONObject newReqJson, JSONObject suppResJson) {
		JSONObject resBody = suppResJson.getJSONObject(JSON_PROP_RESBODY);
		int index = newReqJson.getJSONObject(JSON_PROP_REQBODY).getInt("activityInfoIndex");
		JSONArray tourActivityInfoArr = resBody.getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(0).getJSONArray(JSON_PROP_TOURACTIVITYINFO);
		
		/*		
		// Below code checks if there is already an entry with the ongoing index then it add in that array else it sets on that particular index
		
 		if(mSearchRes.remove(newReqJson.getJSONObject(JSON_PROP_REQBODY).getInt("activityInfoIndex"))!=null) {
 			ArrayList<JSONArray> temp = new ArrayList<JSONArray>();
 			temp.add(mSearchRes.remove(newReqJson.getJSONObject(JSON_PROP_REQBODY).getInt("activityInfoIndex")));
 			temp.addAll(Arrays.asList(tourActivityInfoArr));
 			mSearchRes.set(newReqJson.getJSONObject(JSON_PROP_REQBODY).getInt("activityInfoIndex"), new JSONArray(temp.toArray()));
 		}
 		else {
 			mSearchRes.set(newReqJson.getJSONObject(JSON_PROP_REQBODY).getInt("activityInfoIndex"), tourActivityInfoArr);

 		} */

		JSONArray activityInfoArrTemp = new JSONArray();

		if(mSearchRes.isEmpty()) {
			JSONArray activityInfoArr = new JSONArray();
			mSearchRes.add(activityInfoArr);
		}

		activityInfoArrTemp = mSearchRes.remove(0);

		if(activityInfoArrTemp.opt(index)==null) {
			activityInfoArrTemp.put(index, tourActivityInfoArr);
		}
		else {
			JSONArray tourActivityInfoArrTemp = new JSONArray();
			tourActivityInfoArrTemp = activityInfoArrTemp.getJSONArray(index);
			tourActivityInfoArrTemp = mergeTourActivityInfoArr(tourActivityInfoArr, tourActivityInfoArrTemp);
			activityInfoArrTemp.put(index, tourActivityInfoArrTemp);
		}

		mSearchRes.add(activityInfoArrTemp);


		if (mIsAsync) {
			if (mSentFirstResponse == false || mSearchRes.size() == mProdSuppliers.size()) {
				mSentFirstResponse = true;
				ActivitySearchSorterResponder.respondToCallback(mCallbackURL, mReqHeaderJson, mSearchRes);
			}
		}

		if (mSearchRes.size() == mProdSuppliers.size()) {
			// TODO: Is this right?
			synchronized (this) {
				notify();
			}
		}

	}

	private JSONArray mergeTourActivityInfoArr(JSONArray tourActivityInfoArr, JSONArray tourActivityInfoArrTemp) {
		int index = 0;
		JSONArray result = new JSONArray();

		for(Object obj : tourActivityInfoArr) {
			obj = (JSONObject)obj;
			result.put(index, obj);
			index++;
		}

		for(Object obj : tourActivityInfoArrTemp) {
			obj = (JSONObject)obj;
			result.put(index, obj);
			index++;
		}

		return result;
	}

	public JSONObject getSearchResponse() {

		return ActivitySearchSorterResponder.getSortedResponse(mReqHeaderJson, mSearchRes);
	}
}
