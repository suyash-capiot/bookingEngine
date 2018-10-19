package com.coxandkings.travel.bookingengine.orchestrator.bus;

import java.text.SimpleDateFormat;

import java.util.Date;

import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.exception.ValidationException;


public class BusRequestValidator implements BusConstants{

	static void validateSource(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		String sourceStationId = reqBodyJson.optString(JSON_PROP_SOURCE);
		
		if (sourceStationId.isEmpty()) {
			System.out.println("source empty");
			//TODO:add exception in BEResources and make respective change 
//			throw new ValidationException("CKIL231556_BR09_ER01");
		}
	}
	
	static void validateDestination(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		String sourceStationId = reqBodyJson.optString(JSON_PROP_DESTINATION);
		
		if (sourceStationId.isEmpty()) {
			System.out.println("dest empty");
			//TODO:add exception in BEResources and make respective change 
//			throw new ValidationException("CKIL231556_BR09_ER01");
		}
	}
	
	static void validateJourneyDate(JSONObject reqJson) throws ValidationException 
	{
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		String journeyDate = reqBodyJson.optString(JSON_PROP_JOURNEYDATE);
		try 
		{
			if (journeyDate.isEmpty()) {
				
				//TODO:add exception in BEResources and make respective change 
				throw new ValidationException("Journey Date not specified");
			}
			else
			{
				 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
				 Date currentDate = new Date();
				
				 
				 Date reqDate = sdf.parse(journeyDate);
				 
				 if(sdf.format(reqDate).compareTo(sdf.format(currentDate)) < 0)
				 {
					 throw new ValidationException("Journey Date is invalid");
					//TODO:add exception in BEResources and make respective change 
//						throw new ValidationException("CKIL231556_BR09_ER01");
				 }
			}
		}
	
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}
