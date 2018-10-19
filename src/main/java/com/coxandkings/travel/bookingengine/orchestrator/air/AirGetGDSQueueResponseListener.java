package com.coxandkings.travel.bookingengine.orchestrator.air;

import org.json.JSONArray;
import org.json.JSONObject;

public class AirGetGDSQueueResponseListener implements AirConstants {
	
	private int mThreadsStarted = 0;
	private int mThreadsResponded = 0;
	private JSONArray mThreadResJsonArr = new JSONArray();
	

	synchronized void incrementStartedThreadsCount() {
		mThreadsStarted++;
	}
	
	void receiveSupplierResponse(JSONObject suppResJson) {
		JSONObject suppResBodyJson = suppResJson.optJSONObject(JSON_PROP_RESBODY);

		// If multiple threads respond at the same time, the responses must be
		// appended in single threaded manner. 
		synchronized(mThreadResJsonArr) {
			if (suppResBodyJson != null) {
				mThreadResJsonArr.put(suppResBodyJson);
			}
			
			// If response has been received from all the threads that were started 
			mThreadsResponded++;
			if (mThreadsResponded >= mThreadsStarted) {
				synchronized(this) {
					notify();
				}
			}
		}

	}
	
	JSONArray getAggregatedResponse() {
		return mThreadResJsonArr;
	}

}
