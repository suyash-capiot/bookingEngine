package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.enums.PassengerType;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.utils.Utils;

public class HolidaysRequestValidator implements HolidayConstants {

	static void validateTourCode(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		if(reqBodyJson.has(JSON_PROP_DYNAMICPACKAGE)) {
			for(Object dynPkg : reqBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE)) {
				JSONObject dynPackg = (JSONObject)dynPkg;
				String tourCode = dynPackg.optString(JSON_PROP_TOURCODE);
				if ((tourCode == null) || (tourCode.equals(""))) {
					throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3019");
				}
			}	
		}
		else {
		String tourCode = reqBodyJson.optString(JSON_PROP_TOURCODE);
		if ((tourCode == null) || (tourCode.equals(""))) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3019");
		}}
	}
	
	static void validateSubTourCode(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		if(reqBodyJson.has(JSON_PROP_DYNAMICPACKAGE)) {
			for(Object dynPkg : reqBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE)) {
				JSONObject dynPackg = (JSONObject)dynPkg;
				String subTourCode = dynPackg.optString(JSON_PROP_SUBTOURCODE);
				if ((subTourCode == null) || (subTourCode.equals(""))) {
					throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3020");
				}
			}	
		}
		else {
		String subTourCode = reqBodyJson.optString(JSON_PROP_SUBTOURCODE);
		if ((subTourCode == null) || (subTourCode.equals(""))) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3020");
		}}
	}

	static void validateBrandName(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		if(reqBodyJson.has(JSON_PROP_DYNAMICPACKAGE)) {
			for(Object dynPkg : reqBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE)) {
				JSONObject dynPackg = (JSONObject)dynPkg;
				String brandName = dynPackg.optString(JSON_PROP_BRANDNAME);
				if ((brandName == null) || (brandName.equals(""))) {
					throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3021");
				}
			}	
		}
		else {
		String brandName = reqBodyJson.optString(JSON_PROP_BRANDNAME);
		if ((brandName == null) || (brandName.equals(""))) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3021");
		}}
	}
	
	static void validatePassengerCounts(JSONObject reqJson) throws ValidationException {
		JSONObject reqHeader = reqJson.getJSONObject(JSON_PROP_REQHEADER);
	  for(Object dynPkg : reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE)) {
		JSONObject currentDynPkg = (JSONObject)dynPkg;
		BigDecimal zeroBigDecimal = new BigDecimal(0);
		Map<String, BigDecimal> psgrCounts = getPaxCountsFromRequest(currentDynPkg, reqHeader);
		
		int adtCount = psgrCounts.getOrDefault(PassengerType.ADULT.toString(), zeroBigDecimal).intValue();
		int chdCount = psgrCounts.getOrDefault(PassengerType.CHILD.toString(), zeroBigDecimal).intValue();
		int infCount = psgrCounts.getOrDefault(PassengerType.INFANT.toString(), zeroBigDecimal).intValue();
		
		if (adtCount < PASSENGER_COUNT_MINIMUM_ADULT || adtCount>PASSENGER_COUNT_MAXIMUM_ADULT) {
			throw new ValidationException(reqHeader,"TRLERR3023");
		}
		
		if (chdCount < PASSENGER_COUNT_MINIMUM_CHILD || chdCount>PASSENGER_COUNT_MAXIMUM_CHILD) {
			throw new ValidationException(reqHeader,"TRLERR3022");
		}
		if (infCount > adtCount) {
			throw new ValidationException(reqHeader,"TRLERR3025");
		}
	  }
	}
	
	private static Map<String, BigDecimal> getPaxCountsFromRequest(JSONObject currentDynPkg, JSONObject reqHeader) throws ValidationException {
    	Map<String,BigDecimal> paxInfoMap=new LinkedHashMap<String,BigDecimal>();
        JSONObject paxInfo=null;

        JSONArray reqPaxInfoJsonArr = currentDynPkg.getJSONArray(JSON_PROP_PAXINFO);
        for(int i=0;i<reqPaxInfoJsonArr.length();i++) {
        	paxInfo = reqPaxInfoJsonArr.getJSONObject(i);
        	String paxType = paxInfo.optString("paxType");
        	String quantity = paxInfo.optString("quantity");
        	
        	if(paxType==null || paxType.equals(""))
        		throw new ValidationException(reqHeader, "TRLERR3052");
        	if(quantity==null)
        		throw new ValidationException(reqHeader, "TRLERR3052");
        	
        	paxInfoMap.put(paxInfo.getString(JSON_PROP_PAXTYPE), new BigDecimal(paxInfo.getInt(JSON_PROP_QTY)));
        }

        return paxInfoMap;
    }

	public static void validateResGuestsInfo(JSONObject reqJson) throws ValidationException {
		JSONObject reqHeader = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		for(Object dynPkg : reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE)) {
			JSONObject dynPackg = (JSONObject)dynPkg;
			JSONArray resGuests = dynPackg.getJSONArray(JSON_PROP_RESGUESTS);
			for(int i=0;i<resGuests.length();i++)
			{
				JSONObject paxJson = resGuests.getJSONObject(i);
				
				String resGuestRPH = paxJson.optString("resGuestRPH");
				if(resGuestRPH==null || resGuestRPH=="")
					throw new ValidationException(reqHeader, "TRLERR3053");
				
				String paxType = paxJson.getString("paxType");
				if(paxType==null)
					throw new ValidationException(reqHeader,"TRLERR3029");
				
				int age = Utils.calculateAge(paxJson.getString(JSON_PROP_DATEOFBIRTH));
				if(age<=0)
					throw new ValidationException(reqHeader,"TRLERR3032");
				
				JSONObject addDtls = paxJson.optJSONObject("addressDetails");
				if(addDtls==null) 
					throw new ValidationException(reqHeader,"TRLERR3026");
				 
				JSONArray cntctDtls = paxJson.optJSONArray("contactDetails");
				if(cntctDtls==null) 
					throw new ValidationException(reqHeader,"TRLERR3027");
			}
		}
	}

	public static void validatebookID(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson=reqJson.getJSONObject(JSON_PROP_REQBODY);
		if(!(reqBodyJson.has(JSON_PROP_BOOKID)) || (reqBodyJson.getString(JSON_PROP_BOOKID)).equals("")){
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3033");
		}
	}

	public static void validatePaymentInfo(JSONObject reqJson) throws ValidationException {
		JSONArray paymentInfosJsonArr=reqJson.getJSONObject(JSON_PROP_REQBODY).optJSONArray(JSON_PROP_PAYINFO);
		if(paymentInfosJsonArr==null || paymentInfosJsonArr.length() == 0) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3031");
		}
	}

	public static void validateComponents(JSONObject requestJson) throws ValidationException {
		for(Object dynPkg : requestJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE)) {
			JSONObject reqHeader = requestJson.getJSONObject(JSON_PROP_REQHEADER);
			JSONObject currentDynPkg = (JSONObject)dynPkg;
			JSONObject componentsJson = currentDynPkg.optJSONObject(JSON_PROP_COMPONENTS);
			JSONObject hotelJson = componentsJson.optJSONObject(JSON_PROP_HOTEL_COMPONENT);
			JSONObject cruiseJson = componentsJson.optJSONObject(JSON_PROP_CRUISE_COMPONENT);
			JSONArray insuranceArray = componentsJson.optJSONArray(JSON_PROP_INSURANCE_COMPONENT);
			JSONArray transferArray = componentsJson.optJSONArray(JSON_PROP_TRANSFER_COMPONENT);
			JSONArray activitiesArray = componentsJson.optJSONArray(JSON_PROP_ACTIVITY_COMPONENT);
			JSONObject preNightComponent = componentsJson.optJSONObject(JSON_PROP_PRENIGHT);
			JSONObject postNightComponent = componentsJson.optJSONObject(JSON_PROP_POSTNIGHT);
			
			if(componentsJson == null || componentsJson.length() == 0) 
				throw new ValidationException(reqHeader ,"TRLERR3035");
			
			if(hotelJson == null || hotelJson.length() == 0) {
				if(cruiseJson == null || cruiseJson.length() == 0)
					throw new ValidationException(reqHeader ,"TRLERR3036");
				else 
					checkCruiseContents(cruiseJson, reqHeader);
			}
			else 
				checkAccommodationContents(hotelJson, reqHeader);
			
			if(insuranceArray != null && insuranceArray.length() != 0) 
				checkInsuranceContents(insuranceArray, reqHeader);
			if(transferArray!=null && transferArray.length()!=0) 
				checkTransferContents(transferArray, reqHeader);
			if(activitiesArray!=null && activitiesArray.length()!=0) 
				checkActivityContents(activitiesArray, reqHeader);
			if(preNightComponent!=null && preNightComponent.length()!=0)
				checkAccommodationContents(preNightComponent, reqHeader);
			if(postNightComponent!=null && postNightComponent.length()!=0)
				checkAccommodationContents(postNightComponent, reqHeader);
		}
	}

	private static void checkTransferContents(JSONArray transferArray, JSONObject reqHeader) throws ValidationException {
		for(int i=0;i<transferArray.length();i++) {
			JSONObject transferJson = transferArray.getJSONObject(i);
			checkDynamicPkgAction(transferJson, reqHeader);
			JSONArray groundServiceArray = transferJson.getJSONArray("groundService");
			for(int j=0;j<groundServiceArray.length();j++) {
				JSONObject groundServiceJson = groundServiceArray.getJSONObject(j);
				
				String pickUpLocation = groundServiceJson.getJSONObject("location").optString("pickUpLocation");
				if(pickUpLocation==null || pickUpLocation.equals(""))
					throw new ValidationException(reqHeader, "TRLERR3048");
				
				JSONArray totalCharge = groundServiceJson.optJSONArray("totalCharge");
				if(totalCharge==null || totalCharge.length()==0)
					throw new ValidationException(reqHeader, "TRLERR3054");
				for(int k=0;k<totalCharge.length();k++) {
					String rateTotalAmount = totalCharge.getJSONObject(k).optString("rateTotalAmount");
					if(rateTotalAmount== null || rateTotalAmount.equals(""))
						throw new ValidationException(reqHeader, "TRLERR3049");
				}
				
				String start = groundServiceJson.getJSONObject("timeSpan").optString("start");
				if(start==null || start.equals(""))
					throw new ValidationException(reqHeader, "TRLERR3050");
				
				String end = groundServiceJson.getJSONObject("timeSpan").optString("end");
				if(end==null || end.equals(""))
					throw new ValidationException(reqHeader, "TRLERR3051");
			}
			
		}
		
	}

	private static void checkActivityContents(JSONArray activitiesArray, JSONObject reqHeader) {
		// TODO: Add validations once Gadventures is developed
		
	}

	private static void checkInsuranceContents(JSONArray insuranceArray, JSONObject reqHeader) throws ValidationException {
		for(int i=0;i<insuranceArray.length();i++) {
			JSONObject insuranceJson = insuranceArray.getJSONObject(i);
			checkDynamicPkgAction(insuranceJson, reqHeader);
			JSONObject insCoverageDetail = insuranceJson.getJSONObject("insCoverageDetail");
			
			String name = insCoverageDetail.getString("name");
			String description = insCoverageDetail.getString("description");
			String type = insCoverageDetail.getString("type");
			
			if(name == null || name.equals(""))
				throw new ValidationException(reqHeader, "TRLERR3045");
			if(description == null || description.equals(""))
				throw new ValidationException(reqHeader, "TRLERR3046");
			if(type == null || type.equals(""))
				throw new ValidationException(reqHeader, "TRLERR3047");
			
		}
	}

	private static void checkCruiseContents(JSONObject cruiseJson, JSONObject reqHeader) throws ValidationException {
		checkDynamicPkgAction(cruiseJson, reqHeader);
		JSONArray categoryOptions = cruiseJson.getJSONArray("categoryOptions");
		for(int i=0;i<categoryOptions.length();i++) {
			JSONObject categoryOptionsJson = categoryOptions.getJSONObject(i);
			JSONArray categoryOption = categoryOptionsJson.getJSONArray("categoryOption");
			for(int j=0;j<categoryOption.length();j++) {
				JSONObject categoryOptionJson = categoryOption.getJSONObject(j);
				
				String cabinType = categoryOptionJson.optString("cabinType");
				String cabinCategory = categoryOptionJson.optString("cabinCategory");
				
				if(cabinType == null || cabinType.equals(""))
					throw new ValidationException(reqHeader, "TRLERR3043");
				if(cabinCategory == null || cabinCategory.equals(""))
					throw new ValidationException(reqHeader, "TRLERR3044");
			}
		}
		
	}

	private static void checkAccommodationContents(JSONObject accoJson, JSONObject reqHeader) throws ValidationException {
		checkDynamicPkgAction(accoJson, reqHeader);
		JSONArray roomStay = accoJson.getJSONObject("roomStays").getJSONArray("roomStay");
		for(int i=0; i<roomStay.length();i++) {
			JSONObject currentRoom = roomStay.getJSONObject(i);
			
			String roomType = currentRoom.optString("roomType");
			String ratePlanCategory = currentRoom.optString(JSON_PROP_RATEPLANCATEGORY);
			JSONObject timeSpan = currentRoom.optJSONObject("timeSpan");
			
			if(roomType == null || roomType.equals(""))
				throw new ValidationException(reqHeader, "TRLERR3039");
			if(ratePlanCategory == null || ratePlanCategory=="") {
				if(accoJson.getString(JSON_PROP_DYNAMICPKGACTION).contains("Night")){
					if(timeSpan == null) {
						throw new ValidationException(reqHeader, "TRLERR3041");
						}
					else {
						String duration = timeSpan.optString("duration");
						if(duration == null)
							throw new ValidationException(reqHeader, "TRLERR3042");
						}
					}
				else
					throw new ValidationException(reqHeader, "TRLERR3040");
			}
		}
	}

	private static void checkDynamicPkgAction(JSONObject json, JSONObject reqHeader) throws ValidationException {
		String dynamicPkgAction = json.optString(JSON_PROP_DYNAMICPKGACTION);
		if(dynamicPkgAction== null || dynamicPkgAction.equals(""))
			throw new ValidationException(reqHeader, "TRLERR3038");	
	}

	public static void validateGlobalInfo(JSONObject requestJson) throws ValidationException {
		for(Object dynPkg : requestJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE)) {
			JSONObject currentDynPkg = (JSONObject)dynPkg;
			JSONObject globalInfoJson = currentDynPkg.optJSONObject(JSON_PROP_GLOBALINFO);
			if(globalInfoJson == null || globalInfoJson.length() == 0) {
				throw new ValidationException(requestJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3034");
			}
		}
		
	}

	public static void validateDynamicPkg(JSONObject requestJson) throws ValidationException {
		JSONObject reqBody = requestJson.optJSONObject(JSON_PROP_REQBODY);
		if(reqBody == null || reqBody.length() == 0) {
			throw new ValidationException(requestJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3018");
		}
		JSONArray dynpkgArray = reqBody.optJSONArray(JSON_PROP_DYNAMICPACKAGE);
		if(dynpkgArray == null || dynpkgArray.length() == 0) {
			throw new ValidationException(requestJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3018");
		}
	}
	
	public static void validateInvoiceNumberAndIDContext(JSONObject requestJson) throws ValidationException {
		for(Object dynPkg : requestJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE)) {
			JSONObject currentDynPkg = (JSONObject)dynPkg;
			String invoiceNumber = currentDynPkg.optString("invoiceNumber");
			String idContext = currentDynPkg.optString("idContext");
			if(invoiceNumber== null || invoiceNumber.equals(""))
				throw new ValidationException(requestJson.getJSONObject(JSON_PROP_REQHEADER), "TRLERR3055");
			if(idContext== null || idContext.equals(""))
				throw new ValidationException(requestJson.getJSONObject(JSON_PROP_REQHEADER), "TRLERR3056");
			}
		}

	public static void validateSupplierID(JSONObject reqJson) throws ValidationException {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		if(reqBodyJson.has(JSON_PROP_DYNAMICPACKAGE)) {
			for(Object dynPkg : reqBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE)) {
				JSONObject dynPackg = (JSONObject)dynPkg;
				String suppID = dynPackg.optString(JSON_PROP_SUPPLIERID);
				if ((suppID == null) || (suppID.equals(""))) {
					throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3057");
				}
			}	
		}
		else {
		String suppID = reqBodyJson.optString(JSON_PROP_SUPPLIERID);
		if ((suppID == null) || (suppID.equals(""))) {
			throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR3057");
		}}
	}
}

