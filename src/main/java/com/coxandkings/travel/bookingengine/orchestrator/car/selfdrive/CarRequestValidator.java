package com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;

public class CarRequestValidator implements CarConstants {

	static void validateTravelDates(JSONObject reqJson) throws ValidationException {
		
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject carRentalInfo = reqBodyJson.optJSONObject(JSON_PROP_CARRENTALARR);
		if (carRentalInfo == null || carRentalInfo.length() == 0) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER), "TRLERR7001");
		}
		Instant pickUpDate, dropOffDate;
		
		long currentInstant = Instant.now().toEpochMilli();
		String pickUpDateTime = carRentalInfo.optString(JSON_PROP_PICKUPDATE);
		String dropOffDateTime = carRentalInfo.optString(JSON_PROP_RETURNDATE);
		
		try {
			pickUpDate = Instant.parse(pickUpDateTime.concat("Z"));
		}catch(DateTimeParseException px) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR7002", pickUpDateTime);
		}
		
		long pickUpDateMillis = pickUpDate.toEpochMilli(); 
		if (currentInstant > pickUpDateMillis) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR7003", pickUpDateTime);
		}
		
		try {
			dropOffDate = Instant.parse(dropOffDateTime.concat("Z"));
		}catch(DateTimeParseException px) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR7004", dropOffDateTime);
		}
		
		long dropOffDateMillis = dropOffDate.toEpochMilli();
		if(pickUpDateMillis > dropOffDateMillis){
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR7005", dropOffDateTime, pickUpDateTime);
		}
	}
	
	static void validateTravelLocation(JSONObject reqJson) throws ValidationException {
		
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject carRentalInfo = reqBodyJson.optJSONObject(JSON_PROP_CARRENTALARR);
		
		String pickUpLoc = carRentalInfo.optString(JSON_PROP_PICKUPLOCCODE);
		String returnLoc = carRentalInfo.optString(JSON_PROP_RETURNLOCCODE);
		
		if(pickUpLoc==null || pickUpLoc.isEmpty()) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER), "TRLERR7006");
		}
		if(returnLoc==null || returnLoc.isEmpty()) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER), "TRLERR7007");
		}
	}
	
	public static void validatePaxDetails(JSONObject reqJson) throws JSONException, ValidationException {
		JSONArray carRentalInfoArr=reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_CARRENTALARR);
		
		for(int i=0; i<carRentalInfoArr.length();i++) {
			JSONArray paxDetailsJsonArr= carRentalInfoArr.getJSONObject(i).optJSONArray(JSON_PROP_PAXDETAILS);
			if(paxDetailsJsonArr==null) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR030");
			}
		}
	}

	public static void validatePaymentInfo(JSONObject reqJson) throws JSONException, ValidationException {
		JSONArray paymentInfosJsonArr=reqJson.getJSONObject(JSON_PROP_REQBODY).optJSONArray(JSON_PROP_PAYINFO);
		if(paymentInfosJsonArr==null) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR031");
		}
	}

	public static void validatebookID(JSONObject reqJson) throws JSONException, ValidationException {
		JSONObject reqBodyJson=reqJson.getJSONObject(JSON_PROP_REQBODY);
		if(!(reqBodyJson.has(JSON_PROP_BOOKID)) || (reqBodyJson.getString(JSON_PROP_BOOKID)).equals("")){
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR036");
		}
	}

}
