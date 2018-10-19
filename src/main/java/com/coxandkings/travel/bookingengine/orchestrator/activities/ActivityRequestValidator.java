package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.exception.ValidationException;

public class ActivityRequestValidator implements ActivityConstants {

	static void validateSearchRequest(JSONObject reqHdrJson, JSONObject reqBodyJson) throws ValidationException, ParseException {
		JSONObject activityInfoJson = reqBodyJson.getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(0);

		if (activityInfoJson.has(JSON_PROP_STARTDATE)) {
			validateStartDate(reqHdrJson, activityInfoJson);
		}

		if (activityInfoJson.has(JSON_PROP_ENDDATE)) {
			validateEndDate(reqHdrJson, activityInfoJson);
		}

		if (activityInfoJson.has(JSON_PROP_PARTICIPANT_INFO)) {
			validateParticipantInfo(reqHdrJson, activityInfoJson);
		}
	}

	static void validatePriceRequest(JSONObject reqHdrJson, JSONObject reqBodyJson) throws ValidationException, ParseException {

		JSONArray activityInfoArr = reqBodyJson.getJSONArray("activityInfo");
		
		for(int i = 0; i<activityInfoArr.length(); i++) {

			validateAdultCountChildCount(reqHdrJson, activityInfoArr.getJSONObject(i));

			validateStartDate(reqHdrJson, activityInfoArr.getJSONObject(i));

			validateEndDate(reqHdrJson, activityInfoArr.getJSONObject(i));

		}
		
		
	}

	static void validateRepriceRequest(JSONObject reqHdrJson, JSONObject reqBodyJson) throws ValidationException, ParseException {
		JSONArray activityInfoArr = reqBodyJson.getJSONArray("activityInfo");

		for(int i = 0; i<activityInfoArr.length(); i++) {

			JSONObject activityInfo = activityInfoArr.getJSONObject(i);

			validatePaxInfoParticipantInfo(reqHdrJson, activityInfo);
		}
	}


	static void validateBookRequest(JSONObject reqHdrJson, JSONObject reqBodyJson) throws ValidationException, ParseException {
		JSONArray reservationsArr = reqBodyJson.getJSONArray("reservations");

		for(int i = 0; i<reservationsArr.length(); i++) {

			JSONObject reservations = reservationsArr.getJSONObject(i);

			validatePaxInfoParticipantInfo(reqHdrJson, reservations);
		}
	}

	static void validateAmendRequest(JSONObject reqHdrJson, JSONObject reqBodyJson) throws ValidationException, ParseException {

	}

	static void validateCancelRequest(JSONObject reqHdrJson, JSONObject reqBodyJson) throws ValidationException, ParseException {

	}

	static void validatePaxInfoParticipantInfo(JSONObject reqHdrJson, JSONObject activityInfoJson) throws ValidationException, ParseException{
		JSONArray paxInfoArr = activityInfoJson.getJSONArray("paxInfo");
		JSONArray participantInfoArr = activityInfoJson.getJSONArray("participantInfo");
		int isLeadPaxCountCheck = 0;

		int adultCountPaxInfo=0,childCountPaxInfo=0;
		int adultCountParticipantInfo=0,childCountParticipantInfo=0;

		for(int i=0; i<paxInfoArr.length(); i++) {
			JSONObject paxInfo = paxInfoArr.getJSONObject(i);

			ActivityPassengerType paxType = ActivityPassengerType.forString(paxInfo.getString("paxType"));

			switch(paxType) {

			case Adult: adultCountPaxInfo = paxInfo.getInt("quantity");
			break;

			case Child: childCountPaxInfo = paxInfo.getInt("quantity");
			break;
			}
		}

		for(int i=0; i<participantInfoArr.length(); i++) {
			JSONObject participantInfo = participantInfoArr .getJSONObject(i);


			if(participantInfo.getBoolean("isLeadPax")) {
				isLeadPaxCountCheck++;
			}

			ActivityPassengerType qualifierInfo = ActivityPassengerType.forString(participantInfo.getString("qualifierInfo"));

			int age = Integer.parseInt(ActivityBookProcessor.calculateAge(participantInfo.getString(JSON_PROP_DOB)));

			switch(qualifierInfo) {

			case Adult: 
				adultCountParticipantInfo++;
				if(age<18) {
					throw new ValidationException(reqHdrJson, "TRLERR2027");
				}
				break;

			case Child: 
				childCountParticipantInfo++;
				if(age<0||age>17) {
					throw new ValidationException(reqHdrJson, "TRLERR2024");
				}
				break;
			}
		}


		if(isLeadPaxCountCheck!=1) {
			throw new ValidationException(reqHdrJson, "TRLERR2022");
		}

		if((adultCountPaxInfo<0 || childCountPaxInfo<0)||(adultCountPaxInfo==0 && childCountPaxInfo==0)) {
			throw new ValidationException(reqHdrJson, "TRLERR2025");
		}

		if((adultCountPaxInfo+childCountPaxInfo)!=(adultCountParticipantInfo+childCountParticipantInfo)) {
			throw new ValidationException(reqHdrJson, "TRLERR2023");
		}

		if(adultCountPaxInfo != adultCountParticipantInfo || childCountPaxInfo != childCountParticipantInfo) {
			throw new ValidationException(reqHdrJson, "TRLERR2026");
		}
	}

	static void validateAdultCountChildCount(JSONObject reqHdrJson, JSONObject activityInfoJson) throws ValidationException, ParseException {
		int adultCount = activityInfoJson.getInt("adultCount");
		int childCount = activityInfoJson.getInt("childCount");

		if((adultCount<0 || childCount<0)||(adultCount==0 && childCount==0)) {
			throw new ValidationException(reqHdrJson, "TRLERR2025");
		}
	}

	static void validateStartDate(JSONObject reqHdrJson, JSONObject activityInfoJson) throws ValidationException, ParseException {
		String startDateString = activityInfoJson.getString(JSON_PROP_STARTDATE);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

		Date startDate = sdf.parse(startDateString);
		Date currentDate = sdf.parse(sdf.format(new Date()));

		if (startDate.compareTo(currentDate) < 0) {
			throw new ValidationException(reqHdrJson, "TRLERR2019");
		}

	}

	static void validateEndDate(JSONObject reqHdrJson, JSONObject activityInfoJson) throws ValidationException, ParseException {

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

		String endDateString = activityInfoJson.getString(JSON_PROP_ENDDATE);
		Date endDate = sdf.parse(endDateString);

		Date startDate;
		if (activityInfoJson.has(JSON_PROP_STARTDATE)) {
			String startDateString = activityInfoJson.getString(JSON_PROP_STARTDATE);
			startDate = sdf.parse(startDateString);
			if (endDate.compareTo(startDate) < 0) {
				throw new ValidationException(reqHdrJson, "TRLERR2020");
			}
		} else {
			startDate = sdf.parse(sdf.format(new Date()));
			if (endDate.compareTo(startDate) < 0) {
				throw new ValidationException(reqHdrJson, "TRLERR2021");
			}
		}

	}

	static void validateParticipantInfo(JSONObject reqHdrJson, JSONObject activityInfoJson) throws ValidationException {
		JSONObject participantInfoJson = activityInfoJson.getJSONObject(JSON_PROP_PARTICIPANT_INFO);
		int adultCount = 0, childAgesCount = 0;

		if (participantInfoJson.has("adultCount")) {
			adultCount = participantInfoJson.getInt("adultCount");

		}

		if (participantInfoJson.has("childAges")) {
			JSONArray childAgesArr = participantInfoJson.getJSONArray("childAges");
			childAgesCount = childAgesArr.length();

			for (int i = 0; i < childAgesArr.length(); i++) {
				if (childAgesArr.getInt(i) > 17 || childAgesArr.getInt(i) < 0) {
					throw new ValidationException(reqHdrJson, "TRLERR2024");
				}
			}
		}

		if((adultCount<0 || childAgesCount<0)||(adultCount==0 && childAgesCount==0)) {
			throw new ValidationException(reqHdrJson, "TRLERR2025");
		}


	}

}
