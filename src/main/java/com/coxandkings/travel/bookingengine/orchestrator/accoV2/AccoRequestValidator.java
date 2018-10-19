package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.enums.PassengerType;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.enums.AccoSubType;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;

public class AccoRequestValidator implements AccoConstants {

	static void validateCityCode(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException {
		String cityCode = reqBodyJson.optString(JSON_PROP_CITYCODE,null);
		 //Map<String, Object> cityInfoMap = RedisCityData.getCityCodeInfo(cityCode);
		if(cityCode==null || cityCode.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR1009");
		}
	}
	
	static void validateCountryCode(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException {
		String countryCode = reqBodyJson.optString(JSON_PROP_COUNTRYCODE,null);
		
		if(countryCode==null || countryCode.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR1010");
		}
	}
	
	static void validatePaxNationality(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException {
		String paxNationality = reqBodyJson.optString(JSON_PROP_PAXNATIONALITY,null);
		
		if(paxNationality==null || paxNationality.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR1013");
		}
	}

	static void validateRoomConfig(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException {
		JSONArray roomConfig = reqBodyJson.optJSONArray(JSON_PROP_REQUESTEDROOMARR);
		if(roomConfig==null) {
			throw new ValidationException(reqHdrJson,"TRLERR1016");
		}
		int roomConfigLength = roomConfig.length();
		if(roomConfigLength<1) {
			throw new ValidationException(reqHdrJson,"TRLERR1016");
		}
		for(int i=0;i<roomConfigLength;i++) {
			 int adtCnt = roomConfig.getJSONObject(i).optInt(JSON_PROP_ADTCNT);
			 JSONArray childAges = roomConfig.getJSONObject(i).optJSONArray(JSON_PROP_CHDAGESARR);
			if(adtCnt<1) {
				throw new ValidationException(reqHdrJson,"TRLERR1014");
			}
			for(int j=0;j<childAges.length();j++) {
				if(!(0<childAges.getInt(j) && childAges.getInt(j)<=12)) {
					throw new ValidationException(reqHdrJson,"TRLERR1015");
				}
			}
		}
	}
	
	static void validateAccoSubTypeArr(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException {
		JSONArray accoSubTypeArr = reqBodyJson.optJSONArray(JSON_PROP_ACCOSUBTYPEARR);
		if(accoSubTypeArr == null) {
			throw new ValidationException(reqHdrJson,"TRLERR1008");
		}
		for(int i=0;i<accoSubTypeArr.length();i++) {
			String accoSubTypeStr = accoSubTypeArr.optString(i);
			AccoSubType accoSubType = AccoSubType.forString(accoSubTypeStr);
			if(accoSubType==null) {
				throw new ValidationException(reqHdrJson,"TRLERR1008");
			}
		}
		
	}
	
	static void validateAccoSubType(JSONObject roomObjectJson,JSONObject reqHdrJson) throws ValidationException {
			String accoSubTypeStr = roomObjectJson.optString("accommodationSubType");
			AccoSubType accoSubType = AccoSubType.forString(accoSubTypeStr);
			if(accoSubType==null) {
				throw new ValidationException(reqHdrJson,"TRLERR1008");
			}
		}
	
	 static void validateDates(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException, ParseException {

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Date currentDate = sdf.parse(sdf.format(new Date()));
			Date startDate,endDate = null;
			if (reqBodyJson.has(JSON_PROP_CHKIN)) {
				String startDateString = reqBodyJson.getString(JSON_PROP_CHKIN);
				startDate = sdf.parse(startDateString);
			    if (!sdf.format(startDate).equals(startDateString)) {
			   	  throw new ValidationException(reqHdrJson,"TRLERR1011");
			    }
			} else {
				throw new ValidationException(reqHdrJson,"TRLERR1011");
			}
			if (startDate.compareTo(currentDate) < 0) {
				throw new ValidationException(reqHdrJson,"TRLERR1011");
			}

			if (reqBodyJson.has(JSON_PROP_CHKOUT)) {
				String endDateString = reqBodyJson.getString(JSON_PROP_CHKOUT);
			    endDate = sdf.parse(endDateString);
			    if (!sdf.format(endDate).equals(endDateString)) {
			    	  throw new ValidationException(reqHdrJson,"TRLERR1012");
			    }
			}else {
				throw new ValidationException(reqHdrJson,"TRLERR1012");
			}
			
			if (endDate.compareTo(startDate) < 0) {
				throw new ValidationException(reqHdrJson,"TRLERR1012");
			}
		}
	 
	 static void validateRequestBody(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException {
			
			if(reqBodyJson.length()==0) {
				throw new ValidationException(reqHdrJson,"TRLERR1007");
			}
		}
	
	 static void validateOrderAndRoomandPaxId(JSONObject roomObjectJson,JSONObject reqHdrJson) throws ValidationException {
		 String orderID = roomObjectJson.optString("orderID");	
		 String modType = roomObjectJson.getString("modificationType");
		 if(!("FULLCANCELLATION".equals(modType)) && !("CHANGEPERIODOFSTAY").equals(modType)) {
				String roomID = roomObjectJson.optString("roomID");
				if(roomID.isEmpty() || roomID==null) {
					throw new ValidationException(reqHdrJson,"TRLERR1030");
				}
				if(orderID.isEmpty() || orderID==null) {
					throw new ValidationException(reqHdrJson,"TRLERR1029");
				}
			}
			else {
				if(orderID.isEmpty() || orderID==null) {
					throw new ValidationException(reqHdrJson,"TRLERR1029");
				}
			}
		 //in case of addpax we wont get paxids in all obj
		/* if(modType.endsWith("PASSENGER")) {
			 JSONArray paxInfoArr = roomObjectJson.getJSONArray(JSON_PROP_PAXINFOARR);
			 for(int i=0;i<paxInfoArr.length();i++) {
				 JSONObject paxObj = paxInfoArr.getJSONObject(i);
				 String paxID = paxObj.optString("paxID");
				 if(paxID.isEmpty()) {
					 throw new ValidationException(reqHdrJson,"TRLERR1021");
				 }
			 }
		 }*/
			
		}
	 
	 static void validatePaxInfo(JSONObject roomObjectJson,JSONObject reqHdrJson) throws ValidationException {
		 JSONArray paxInfoArr = roomObjectJson.optJSONArray(JSON_PROP_PAXINFOARR);
		 if(paxInfoArr==null || paxInfoArr.length()==0) 
			 throw new ValidationException(reqHdrJson,"TRLERR1019");
		 
		 for(int i=0;i<paxInfoArr.length();i++) {
			 JSONObject paxObj = paxInfoArr.getJSONObject(i);
			 String title = paxObj.optString("title",null);
			 if(Utils.isStringNullOrEmpty(title))
				 throw new ValidationException(reqHdrJson, "TRLERR1019");
			 
			 String firstName = paxObj.optString("firstName",null);
			 if(Utils.isStringNullOrEmpty(firstName))
				 throw new ValidationException(reqHdrJson, "TRLERR1019");
			 
			 String surname = paxObj.optString("surname",null);
			 if(Utils.isStringNullOrEmpty(surname))
				 throw new ValidationException(reqHdrJson, "TRLERR1019");
				 
			 PassengerType paxType = PassengerType.forString(paxObj.optString("paxType"));
			 if(paxType==null) 
				 throw new ValidationException(reqHdrJson,"TRLERR1020");
			 
			 
			 String dob = paxObj.getString("dob");
			 
			 int age = Utils.calculateAge(dob);
			 
			 if(PassengerType.CHILD.equals(paxType) && (age<0 || age>12)) 
				throw new ValidationException(reqHdrJson,"TRLERR1034",PassengerType.CHILD);	
			 
			 
			 if(PassengerType.ADULT.equals(paxType) && (age<12)) 
				throw new ValidationException(reqHdrJson,"TRLERR1034",PassengerType.ADULT);	
			 
			 
			 JSONObject addDtls = paxObj.optJSONObject("addressDetails");
			 if(addDtls==null) 
				 throw new ValidationException(reqHdrJson,"TRLERR1017");
			 
			 
			if(paxObj.optBoolean("isLeadPax")) {
				JSONArray cntctDtls = paxObj.optJSONArray("contactDetails");
				if(cntctDtls==null || cntctDtls.length()==0)
					 throw new ValidationException(reqHdrJson,"TRLERR1018");
				
				 
				JSONObject contactInfo = cntctDtls.getJSONObject(0).optJSONObject(JSON_PROP_CONTACTINFO);
				if(contactInfo==null || contactInfo.length()==0)
					throw new ValidationException(reqHdrJson,"TRLERR1018");
						
				String mobileNbr = contactInfo.optString(JSON_PROP_MOBILENBR,null);
				String email = contactInfo.optString(JSON_PROP_EMAIL,null);
				if(mobileNbr==null || mobileNbr.isEmpty() || email==null || email.isEmpty()) 
					throw new ValidationException(reqHdrJson,"TRLERR1018");
			    }	
			}
		 }
	 
	 
	 static void validateBookId(JSONObject roomObjectJson,JSONObject reqHdrJson) throws ValidationException {
		 String bookID = roomObjectJson.optString("bookID");
		 if(bookID==null || bookID.isEmpty()) {
			 throw new ValidationException(reqHdrJson,"TRLERR1031");
		 }
	 }
	 
	 static void validatePaymentInfo(JSONObject reqBdy,JSONObject reqHdrJson) throws ValidationException {
		  JSONArray paymentInfo = reqBdy.optJSONArray("paymentInfo");
		 if(paymentInfo==null || paymentInfo.length()==0) {
			 throw new ValidationException(reqHdrJson,"TRLERR1022");
		 }
	 }
	 
	 static void validateModType(JSONObject roomObjectJson,JSONObject reqHdrJson) throws ValidationException {
		String modType = roomObjectJson.optString("modificationType");
		if(modType.isEmpty()||modType==null) {
			 throw new ValidationException(reqHdrJson,"TRLERR1023");
		}
	 }
	 
	 static void validateSuppIds(JSONObject roomObjectJson,JSONObject reqHdrJson) throws ValidationException {
			//ErrCode not included in document
			String suppRefId = roomObjectJson.optString("supplierReferenceId",null);
			if(suppRefId==null) {
				 throw new ValidationException(reqHdrJson,"TRLERR1032");
			}
			String clntRefId = roomObjectJson.optString("clientReferenceId",null);
			if(clntRefId==null) {
				 throw new ValidationException(reqHdrJson,"TRLERR1025");
			}
			String suppCanId = roomObjectJson.optString("supplierCancellationId",null);
			if(suppCanId==null) {
				 throw new ValidationException(reqHdrJson,"TRLERR1026");
			}
			/*String suppRoomIndx = roomObjectJson.optString("supplierRoomIndex",null);
			if(suppRoomIndx==null || suppRoomIndx.isEmpty()) {
				 throw new ValidationException(reqHdrJson,"TRLERR1027");
			}*/
		 }
	 
	 static void validateSuppResId(JSONObject roomObjectJson,JSONObject reqHdrJson) throws ValidationException {
		    String suppResId = roomObjectJson.optString("supplierReservationId",null);
			if(suppResId==null ||suppResId.isEmpty()) {
				 throw new ValidationException(reqHdrJson,"TRLERR1024");
			}
	 }
	 
	 static void validateSuppRef(JSONObject roomObjectJson,JSONObject reqHdrJson) throws ValidationException {
			String supRef = roomObjectJson.optString("supplierRef",null);
			if(supRef==null || supRef.isEmpty()) {
				throw new ValidationException(reqHdrJson,"TRLERR1028");
			}
		}
}
