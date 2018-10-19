package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.enums.PassengerType;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.enums.CabinClass;
import com.coxandkings.travel.bookingengine.orchestrator.air.enums.TripType;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirportData;

public class AirRequestValidator implements AirConstants {
	
	private static String DATE_FORMAT_TZ = "%sT00:00:00Z";

	static void validateCabinType(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		String cabinClassStr = reqBodyJson.optString(JSON_PROP_CABINTYPE);
		
		CabinClass cabinClass = CabinClass.forString(cabinClassStr);
		/*if (cabinClass == null) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"CKIL231556_BR09_ER01");
		}*/
		
		if (cabinClass == null) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR029");
		}
	}
	

	static void validateCabinTypeV2(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);	 
         String reqCabinTypeStr=reqBodyJson.getString(JSON_PROP_CABINTYPE);
         if(Utils.isStringNotNullAndNotEmpty(reqCabinTypeStr)) {
        	 int strFirstIdx=0;
        	 if(reqCabinTypeStr.contains(","))
        	 {
        		 Boolean hasCabinClass=true;
        		 while(hasCabinClass==true)
        		 {
        			 int strLstIdx=reqCabinTypeStr.indexOf(",", strFirstIdx);
            		 if(strLstIdx==-1)
            		 {
            			 CabinClass cabinClass = CabinClass.forString((reqCabinTypeStr.substring(strFirstIdx)));
            			 if (cabinClass == null) {
            					throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR029");
            				}
            			 hasCabinClass=false;
            		 }
            		 else {
            			 CabinClass cabinClass = CabinClass.forString((reqCabinTypeStr.substring(strFirstIdx, strLstIdx)));
            			 if (cabinClass == null) {
            					throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR029");
            				}
            			 
            		 }
            		
            		strFirstIdx=strLstIdx+1;
        			 
        		 }
        		
        	 }
        	 else {
        		 CabinClass cabinClass = CabinClass.forString(reqCabinTypeStr);
        		 if (cabinClass == null) {
 					throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR029");
 				}
        	 }
         }
		
	}
	
	static void validateTripType(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		String tripTypeStr = reqBodyJson.optString(JSON_PROP_TRIPTYPE);
		TripType tripType = TripType.forString(tripTypeStr);
		/*if (tripType == null) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"CKIL231556_BR01_ER01");
		}*/
		
		if (tripType == null) {
		throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR024");
	}
	}

	static void validateSectors(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONArray odoInfoJsonArr = reqBodyJson.optJSONArray(JSON_PROP_ORIGDESTINFO);
		if (odoInfoJsonArr == null || odoInfoJsonArr.length() == 0) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR043");
		}
		
		JSONObject odoInfoJson = null;
		Map<String,Object> airportInfoMap = null;
		String originAirport, destinAirport;
		for (int i=0; i < odoInfoJsonArr.length(); i++) {
			odoInfoJson = odoInfoJsonArr.getJSONObject(i);
			
			originAirport = odoInfoJson.optString(JSON_PROP_ORIGLOC);
			airportInfoMap = RedisAirportData.getAirportInfo(originAirport);
			if (airportInfoMap == null || airportInfoMap.isEmpty()) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR044", originAirport, i);
			}
			
			destinAirport = odoInfoJson.optString(JSON_PROP_DESTLOC);
			airportInfoMap = RedisAirportData.getAirportInfo(destinAirport);
			if (airportInfoMap == null || airportInfoMap.isEmpty()) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR045", destinAirport, i);
			}
			
			if (destinAirport.isEmpty() == false && destinAirport.equals(originAirport)) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR046", destinAirport, i);
			}
		}
	}
	
	static void validatePassengerCounts(JSONObject reqJson) throws ValidationException {
		
		if(reqJson.has(JSON_PROP_PAXINFO)) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR026");
		}
		
		
		BigDecimal zeroBigDecimal = new BigDecimal(0);
		Map<String, BigDecimal> psgrCounts = AirSearchProcessor.getPaxCountsFromRequest(reqJson);
		
		int adtCount = psgrCounts.getOrDefault(PassengerType.ADULT.toString(), zeroBigDecimal).intValue();
		int chdCount = psgrCounts.getOrDefault(PassengerType.CHILD.toString(), zeroBigDecimal).intValue();
		int infCount = psgrCounts.getOrDefault(PassengerType.INFANT.toString(), zeroBigDecimal).intValue();
		
		if (adtCount < PASSENGER_COUNT_MINIMUM_ADULT || adtCount>PASSENGER_COUNT_MAXIMUM_ADULT) {
//			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"CKIL231556_BR05_ER01");
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR004");
		}
		
		if (chdCount < PASSENGER_COUNT_MINIMUM_CHILD || chdCount>PASSENGER_COUNT_MAXIMUM_CHILD) {
//			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"CKIL231556_BR05_ER01");
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR005");
		}
		if (infCount > adtCount) {
//			throw new ValidationException("CKIL231556_BR05_ER02");
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR006");
		}
		
		if ((adtCount + chdCount) > PASSENGER_COUNT_MAXIMUM_ADULT_CHILD) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR047", PASSENGER_COUNT_MAXIMUM_ADULT_CHILD);
		}
	}
	
	public static TripType deduceTripType(JSONObject reqJson) throws ValidationException {
		TripType tripType = null;
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONArray odoInfoJsonArr = reqBodyJson.getJSONArray(JSON_PROP_ORIGDESTINFO);
		
		if (odoInfoJsonArr.length() == 1) {
			tripType = TripType.ONEWAY;
		}
		else if (odoInfoJsonArr.length() == 2) {
			JSONObject outboundJson = odoInfoJsonArr.getJSONObject(0); 
			JSONObject inboundJson = odoInfoJsonArr.getJSONObject(1);
			if(outboundJson==null && inboundJson!=null)
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR017");
			if(outboundJson!=null && inboundJson==null)
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR018");
			String outOrig = outboundJson.optString(JSON_PROP_ORIGLOC, "");
			String outDest = outboundJson.optString(JSON_PROP_DESTLOC, "");
			
			String inOrig = inboundJson.optString(JSON_PROP_ORIGLOC, "");
			String inDest = inboundJson.optString(JSON_PROP_DESTLOC, "");
			
			tripType = (outOrig.equals(inDest) && outDest.equals(inOrig)) ? TripType.RETURN : TripType.MULTICITY;
		}
		else {
			tripType = TripType.MULTICITY;
		}

		return tripType;
	}
	
	static void validateTravelDates(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONArray odoInfoJsonArr = reqBodyJson.optJSONArray(JSON_PROP_ORIGDESTINFO);
		if (odoInfoJsonArr == null || odoInfoJsonArr.length() == 0) {
//			throw new ValidationException("CKIL231556_BR04_ER00");
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR009");
		}
		
		JSONObject odoInfoJson = null;
		String prevDateStr = "";
		long midnightToday = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
		long prevDate = Instant.EPOCH.toEpochMilli();
		
		for (int i=0; i < odoInfoJsonArr.length(); i++) {
			odoInfoJson = odoInfoJsonArr.getJSONObject(i);
			
			Instant departInstant = null;
			String departDateStr = odoInfoJson.getString(JSON_PROP_DEPARTDATE);
			try {
				departInstant = Instant.parse(String.format(DATE_FORMAT_TZ, departDateStr));
			}
			catch(DateTimeParseException px) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR040", departDateStr, i);
			}
			
			long departDateMillis = departInstant.toEpochMilli(); 
			if (midnightToday > departDateMillis) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR041", departDateStr, i);
			}
			
			if (prevDate > departDateMillis) {
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR042", departDateStr, i, prevDateStr);
			}
			
			prevDateStr = departDateStr;
			prevDate = departDateMillis;

		}
		
	}

	public static void validatePaxInfo(JSONObject reqHeader,JSONArray paxInfoArr) throws ValidationException {
		for(int i=0;i<paxInfoArr.length();i++)
		{
			JSONObject paxJson = paxInfoArr.getJSONObject(i);
			
			//lead Pax will be the first object
			if(i==0) {
				validateLeadPaxInfo(reqHeader,paxJson);
			}
			
			int age = Utils.calculateAge(paxJson.getString(JSON_PROP_DATEOFBIRTH));
			
			if(age<0)
				throw new ValidationException(reqHeader,"TRLERR020");
			
			if(paxJson.getString(JSON_PROP_PAXTYPE).equals("ADT") && age<12)
				throw new ValidationException(reqHeader,"TRLERR021");
			
			if(paxJson.getString(JSON_PROP_PAXTYPE).equals("CHD") && (age<2 || age>11))
				throw new ValidationException(reqHeader,"TRLERR021");
			
			if(paxJson.getString(JSON_PROP_PAXTYPE).equals("INF") && (age<0 || age>1))
				throw new ValidationException(reqHeader,"TRLERR021");
			
			String title = paxJson.optString("title",null);
			String firstName = paxJson.optString("firstName",null);
			String lastName = paxJson.optString("surname",null);
			String gender = paxJson.optString("gender",null);
			
			if(title==null || title.isEmpty() || firstName==null || firstName.isEmpty() || lastName==null || lastName.isEmpty() || gender==null || gender.isEmpty())
				throw new ValidationException(reqHeader,"TRLERR021");
			
		}
		
	}

	public static void validateLeadPaxInfo(JSONObject reqHeader,JSONObject leadPaxInfo) throws ValidationException{
		JSONArray cntctDtls = leadPaxInfo.optJSONArray(JSON_PROP_CONTACTDTLS);
		if(cntctDtls==null || cntctDtls.length()==0) 
			throw new ValidationException(reqHeader,"TRLERR021");
		
		JSONObject contactInfo = cntctDtls.getJSONObject(0).optJSONObject(JSON_PROP_CONTACTINFO);
		if(contactInfo==null || contactInfo.length()==0)
			throw new ValidationException(reqHeader,"TRLERR021");
			
		String mobileNbr = contactInfo.optString(JSON_PROP_MOBILENBR,null);
		String email = contactInfo.optString(JSON_PROP_EMAIL,null);
		if(mobileNbr==null || mobileNbr.isEmpty() || email==null || email.isEmpty()) 
			throw new ValidationException(reqHeader,"TRLERR021");
		
	}
	
	public static void validateOriginDestInfo(JSONObject reqJson) throws JSONException, ValidationException {
		JSONArray originDestInfo=reqJson.getJSONObject(JSON_PROP_REQBODY).optJSONArray(JSON_PROP_ORIGDESTINFO);
		if(originDestInfo==null) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR025");
		}	
	}

	public static void validatePaxDetails(JSONObject reqJson) throws JSONException, ValidationException {
		JSONArray paxDetailsJsonArr=reqJson.getJSONObject(JSON_PROP_REQBODY).optJSONArray(JSON_PROP_PAXDETAILS);
		if(paxDetailsJsonArr==null) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR030");
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

	public static void validateSupplierBookRefs(JSONObject reqJson) throws JSONException, ValidationException {
		JSONArray supplierBookRefJsonArr=reqJson.getJSONObject(JSON_PROP_REQBODY).optJSONArray(JSON_PROP_SUPPBOOKREFS);
		if(supplierBookRefJsonArr==null) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR032");
		}
		
	}
	
}
