package com.coxandkings.travel.bookingengine.orchestrator.transfers;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.exception.ValidationException;

public class TransfersRequestValidator implements TransfersConstants {

	static void validateTravelDates(JSONObject reqJson) throws ValidationException {
		
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		if (reqBodyJson == null) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER), "TRLTRANSERR001");
		}
		
		Instant pickUpDate, dropOffDate;
		
		long currentInstant = Instant.now().toEpochMilli();
		JSONArray serviceArr = reqBodyJson.optJSONArray(JSON_PROP_SERVICE);
		for(int i = 0 ; i < serviceArr.length() ; i++) {
			/*if(serviceArr ==  null) {
				
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER), "TRLTRANSERR002");
			}*/
			
			JSONObject serviceJson = serviceArr.optJSONObject(i);
			
			JSONObject pickUpJson = serviceJson.optJSONObject("pickup");
			JSONObject dropOffJson = serviceJson.optJSONObject("dropoff");
			
			String pickUpDateTime = pickUpJson.optString("dateTime");
			String dropOffDateTime = dropOffJson.optString("dateTime");
			
			try {
				pickUpDate = Instant.parse(pickUpDateTime.concat("Z"));
			}catch(DateTimeParseException px) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLTRANSERR002", pickUpDateTime);
			}
			
			long pickUpDateMillis = pickUpDate.toEpochMilli(); 
			if (currentInstant > pickUpDateMillis) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLTRANSERR003", pickUpDateTime);
			}
			
			try {
				dropOffDate = Instant.parse(dropOffDateTime.concat("Z"));
			}catch(DateTimeParseException px) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLTRANSERR004", dropOffDateTime);
			}
			
			long dropOffDateMillis = dropOffDate.toEpochMilli();
			if(pickUpDateMillis > dropOffDateMillis){
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLTRANSERR005", dropOffDateTime, pickUpDateTime);
			}
		}
		
	}
	
	static void validateTravelLocation(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.optJSONObject(JSON_PROP_REQBODY);
		JSONArray serviceArr = reqBodyJson.optJSONArray(JSON_PROP_SERVICE);
		for(int i = 0 ; i < serviceArr.length() ; i++) {
			JSONObject serviceJson = serviceArr.getJSONObject(i);
			
			JSONObject pickUpJson = serviceJson.optJSONObject("pickup");
			JSONObject dropOffJson = serviceJson.optJSONObject("dropoff");
			
			String pickUpLoc = pickUpJson.optString("locationCode");
			String dropOffLoc = dropOffJson.optString("locationCode");
			
			if(pickUpLoc==null || pickUpLoc.isEmpty()) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLTRANSERR006");
			}
			if(dropOffLoc==null || dropOffLoc.isEmpty()) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLTRANSERR007");
			}
		}
	}
}
