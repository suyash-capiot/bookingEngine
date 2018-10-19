package com.coxandkings.travel.bookingengine.orchestrator.insurance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.activities.ActivityConstants;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;

public class InsuranceAmendProcessor implements InsuranceConstants{

	private static final Logger logger = LogManager.getLogger(InsuranceAmendProcessor.class);
	
	public static String process(JSONObject reqJson) throws ValidationException{
		
		try {
			
			TrackingContext.setTrackingContext(reqJson);
			JSONObject reqHdrJson = reqJson.getJSONObject(ActivityConstants.JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(ActivityConstants.JSON_PROP_REQBODY);
			
			//TODO: push into ops todo queue
			
			JSONObject resJson = new JSONObject();
			
			
			
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			JSONObject resBodyJson = new JSONObject();
			
			resBodyJson.put(JSON_PROP_BOOKID, reqBodyJson.getString(JSON_PROP_BOOKID));
			resBodyJson.put(JSON_PROP_ORDERID, reqBodyJson.getString(JSON_PROP_ORDERID));
			resBodyJson.put("orderBookingDate", reqBodyJson.getString("orderBookingDate"));
			resBodyJson.put("enamblerSupplierName", reqBodyJson.getString("enamblerSupplierName"));
			resBodyJson.put("productCategory", reqBodyJson.getString("productCategory"));
			resBodyJson.put("cityCode", reqBodyJson.getString("cityCode"));
			resBodyJson.put("operation", reqBodyJson.getString("operation"));
			resBodyJson.put("status", "On Request");
			
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			
			return resJson.toString();

		}
		
		catch (Exception x) {
			x.printStackTrace();
			return STATUS_ERROR;
		}

		
		
		
		
	}
	
}
