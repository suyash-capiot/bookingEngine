package com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;

public class CarSearchListenerResponder implements CarConstants {
	private List<ProductSupplier> mProdSuppliers;
	private List<JSONArray> mSearchRes;
	private boolean mSentFirstResponse;
	private JSONObject mReqJson;
	private JSONObject mReqHeaderJson;
	private URL mCallbackURL;
	private boolean mIsAsync;
	
	CarSearchListenerResponder(List<ProductSupplier> prodSuppliers, JSONObject reqJson) {
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
		JSONObject resBody = suppResJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray vehicleAvailJsonArr = resBody.getJSONArray(JSON_PROP_CARRENTALARR).getJSONObject(0).getJSONArray(JSON_PROP_VEHICLEAVAIL);
		mSearchRes.add(vehicleAvailJsonArr);
		if (mIsAsync) {
			if (mSentFirstResponse == false || mSearchRes.size() == mProdSuppliers.size()) {
				mSentFirstResponse = true;
				CarSearchSorterResponder.respondToCallback(mCallbackURL, mReqHeaderJson, mSearchRes);
			}
		}
		
		if (mSearchRes.size() == mProdSuppliers.size()) {
			// TODO: Is this right?
			synchronized(this) {
				notify();
			}
		}
	}
	

	public JSONObject getSearchResponse() {
		
		return CarSearchSorterResponder.getSortedResponse(mReqHeaderJson, mSearchRes);
	}
	
}
