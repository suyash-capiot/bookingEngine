package com.coxandkings.travel.bookingengine.orchestrator.rail;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.rail.RailConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRailData;

public class ClientCommercialStructure implements RailConstants{

	public static JSONObject getClientCommercialsStructure(JSONObject requestJson, JSONObject responseJson,String operationName, Element resElem) {
		//CommercialsConfig commConfig = RailConfig.getCommercialsConfig();
		//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
		ServiceConfig commTypeConfig = RailConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_CLIENT_TRANSACTIONAL);
		JSONObject breClientReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
		
		
		JSONArray commandsJsonArray=breClientReqJson.getJSONArray(JSON_PROP_COMMANDS);
		for (int i = 0; i < commandsJsonArray.length(); i++) {
			//add header start
			if (commandsJsonArray.getJSONObject(i).has(JSON_PROP_INSERT)) {
			JSONObject breClientReqRoot = commandsJsonArray.getJSONObject(i).getJSONObject(JSON_PROP_INSERT)
					.getJSONObject(JSON_PROP_OBJECT).getJSONObject(JSON_PROP_RAILCOMMERCIALSRULESROOT);
			
			//Take transactionID,sessionID from request header
			JSONObject headerJson=new JSONObject();
			String transactionID=requestJson.getJSONObject(JSON_PROP_REQHEADER).getString(JSON_PROP_TRANSACTID);
			headerJson.put(JSON_PROP_TRANSACTID, transactionID);
			
			String sessionId=requestJson.getJSONObject(JSON_PROP_REQHEADER).getString(JSON_PROP_SESSIONID);
			headerJson.put(JSON_PROP_SESSIONID, sessionId);
			if (operationName.equals("search")) {
				headerJson.put(JSON_PROP_OPERATIONNAME, "Search");
			}else if (operationName.equals("reprice")) {
				headerJson.put(JSON_PROP_OPERATIONNAME, "Reprice");
			}
			
			
			breClientReqRoot.put(JSON_PROP_HEADER, headerJson);
			//header end
			//create businessRuleIntek array start
			JSONArray businessRuleIntakeJsonArray=new JSONArray();
			JSONObject advancedDefinitionJson=new JSONObject();
			//put values in advancedDefinition start
			JSONObject advancedDefinitionJsonObj=new JSONObject();
			JSONObject requestBodyJson=requestJson.getJSONObject(JSON_PROP_REQBODY);
				//get traveldate from request body start
			if (requestBodyJson.has(JSON_PROP_TRAVELDATE)) {
				advancedDefinitionJsonObj.put(JSON_PROP_TRAVELDATE, requestBodyJson.get(JSON_PROP_TRAVELDATE));	
			}else {
				advancedDefinitionJsonObj.put(JSON_PROP_TRAVELDATE, "");
			}
				//end
			//TODO:discuss from where we can get sales date from request or SI response(not found)
				advancedDefinitionJsonObj.put(JSON_SALES_DATE, "2018-04-20T00:00:00");//check
			//TODO:discuss from where we can get ticketingDate from request or SI response(not found). Will it be current system date or same as travel date?
				advancedDefinitionJsonObj.put(JSON_TICKETING_DATE, "2018-04-20T00:00:00");//check
			//TODO:leave it empty if not in request and SI response. For now it is hardcoded
				advancedDefinitionJsonObj.put(JSON_PROP_TRAVELTYPE, "");
				advancedDefinitionJsonObj.put(JSON_PROP_JOURNEYTYPE,"");//empty
				
				advancedDefinitionJsonObj.put(JSON_TRAIN_LINE_TYPE,"");//empty
				advancedDefinitionJsonObj.put(JSON_PROP_TRAVELPRODNAME,"");//empty
				advancedDefinitionJsonObj.put(JSON_DEAL_CODE, "");	//empty
				advancedDefinitionJsonObj.put(JSON_CONNECTIVITY_SUPPLIER_TYPE, "CST1");//empty
				advancedDefinitionJsonObj.put(JSON_CONNECTIVITY_SUPPLIER, "");//empty
				advancedDefinitionJsonObj.put(JSON_PROP_CREDSNAME, "");//check
				advancedDefinitionJsonObj.put(JSON_BOOKING_TYPE, "");//check
			advancedDefinitionJson.put(JSON_PROP_ADVANCEDDEF, advancedDefinitionJsonObj);
			//end
			//crate slab details array start. Keep empty for now
			JSONArray slabDetailsJsonArray=new JSONArray();
			advancedDefinitionJson.put(JSON_SLAB_DETAILS, slabDetailsJsonArray);
			//end
			//create common elements object start. Access supplierId value from reqElement
			JSONObject commonElementsJson=new JSONObject();
			/*requestJson.getJSONObject(JSON_PROP_REQBODY).get("supplierRef");
			Element[] responsebodyElemArray=XMLUtils.getElementsAtXPath(resElem, "./raili:ResponseBody/rail:OTA_RailShopRSWrapper");
			for (Element element : responsebodyElemArray) {
				System.out.println(XMLTransformer.toString(element));
				supplierID=String.valueOf(XMLUtils.getValueAtXPath(element, "./rail:SupplierID"));
			}*/
			commonElementsJson.put(JSON_PROP_SUPP,requestBodyJson.get(JSON_PROP_SUPPREF));
			//TODO:Need to discuss from where "groupMode" and "contractValidity" will come.
			commonElementsJson.put(JSON_GROUP_MODE, "GroupFare");
			commonElementsJson.put(JSON_CONTRACT_VALIDITY, DATE_FORMAT.format(new Date()));
			advancedDefinitionJson.put(JSON_PROP_COMMONELEMS, commonElementsJson);
			//end
			//create "entityDetails" JSON start 
			JSONArray entityDetailsJsonArray=new JSONArray();
			JSONObject entityDetailsJson=new JSONObject();
			//TODO:Need to discuss. Whether it will be hardcoded or not
			entityDetailsJson.put(MDM_PROP_ENTITYTYPE, "ClientType");
			entityDetailsJson.put(JSON_PROP_ENTITYNAME, "CnKB2BIndEng");
			entityDetailsJson.put(JSON_PROP_ENTITYMARKET, "India");
			entityDetailsJsonArray.put(entityDetailsJson);
			advancedDefinitionJson.put(JSON_PROP_ENTITYDETAILS, entityDetailsJsonArray);
			//end
			//create Train Details json start
			JSONArray trainDetailsJsonArray=new JSONArray();
			//put trainName and trainNumber from response
			JSONArray originDestinationOptionsJsonArray=new JSONArray();
			if (!operationName.equals("retrieve")) {
			originDestinationOptionsJsonArray=responseJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ORIGINDESTOPTS);
			for (Object object : originDestinationOptionsJsonArray) {
				JSONObject jsonObj=(JSONObject)object;
				JSONObject trainDetailsJsonObj=new JSONObject();
//				String trainName=jsonObj.getJSONObject(JSON_PROP_TRAINDETAILS).getString(JSON_PROP_TRAINNAME);
				if (jsonObj.has(JSON_PROP_TRAINDETAILS)) {
					trainDetailsJsonObj.put(JSON_PROP_TRAINNAME, jsonObj.getJSONObject(JSON_PROP_TRAINDETAILS).getString(JSON_PROP_TRAINNAME));
					trainDetailsJsonObj.put(JSON_PROP_TRAINNUM, jsonObj.getJSONObject(JSON_PROP_TRAINDETAILS).getString(JSON_PROP_TRAINNUM));
//					advancedDefinitionJsonObj.put(JSON_TRAIN_CATEGORY,jsonObj.getString(JSON_PROP_TRAINTYPE));//train type value
					trainDetailsJsonObj.put(JSON_TRAIN_CATEGORY,"O");//hardcoded value O. It will accept only O. Need to discuss. From above code we can get it from json
				}else {
					////TODO:Need to check in case of reprice. "trainDetails" not in reprice response.
				trainDetailsJsonObj.put(JSON_PROP_TRAINNAME,"");
				trainDetailsJsonObj.put(JSON_PROP_TRAINNUM, "");
				trainDetailsJsonObj.put(JSON_TRAIN_CATEGORY,"");
				}
				//TODO:Need to discuss about below fields. Hard coded temporary
				
				
				Map<String, Object> originalLocationMap=new HashMap<String,Object>();
				originalLocationMap= RedisRailData.getStationCodeInfo(jsonObj.getString(JSON_PROP_ORIGINLOC));
				trainDetailsJsonObj.put(JSON_PROP_FROM_CONTINENT, originalLocationMap.getOrDefault(JSON_PROP_CONTINENT, ""));
				trainDetailsJsonObj.put(JSON_PROP_FROM_COUNTRY, originalLocationMap.getOrDefault(JSON_PROP_COUNTRY, ""));
				trainDetailsJsonObj.put(JSON_PROP_FROM_STATE,originalLocationMap.getOrDefault(JSON_PROP_STATE, ""));
				trainDetailsJsonObj.put(JSON_PROP_FROM_CITY,originalLocationMap.getOrDefault(JSON_PROP_CITY, ""));
				
				Map<String, Object> destinationLocationMap=new HashMap<String,Object>();
				destinationLocationMap= RedisRailData.getStationCodeInfo(jsonObj.getString(JSON_PROP_DESTLOC));
				trainDetailsJsonObj.put(JSON_PROP_TO_CONTINENT, destinationLocationMap.getOrDefault(JSON_PROP_CONTINENT, ""));
				trainDetailsJsonObj.put(JSON_PROP_TO_COUNTRY, destinationLocationMap.getOrDefault(JSON_PROP_COUNTRY, ""));
				trainDetailsJsonObj.put(JSON_PROP_TO_STATE,destinationLocationMap.getOrDefault(JSON_PROP_STATE, ""));
				trainDetailsJsonObj.put(JSON_PROP_TO_CITY,destinationLocationMap.getOrDefault(JSON_PROP_CITY, ""));
				
				trainDetailsJsonObj.put(JSON_PROP_TICKET_TYPE, "Reservation");
				//TODO:Need to check in case of reprice. "trainDetails" not in reprice response.
//				map=RedisCityData.getCityCodeInfo(jsonObj.getString(JSON_PROP_DESTLOC));
				
				//TODO:Hard coded. should be dynamic
				trainDetailsJsonObj.put(JSON_PROP_PRODUCT_CATEGORY, "Transportation");
				trainDetailsJsonObj.put(JSON_PROP_PRODUCT_CATEGORY_SUBTYPE, "RAIL");
				trainDetailsJsonObj.put(JSON_PROP_PRODUCT_NAME, "");
				
				
				//TODO:Hard coded. should be dynamic
				trainDetailsJsonObj.put(JSON_PROP_FARE_TYPE, "RetailFare");
				
				trainDetailsJsonObj.put(JSON_PROP_PASS_NAME, "FrancePass");
				//This method will return passenger details array including fare break up.
				//In case of search operation passenger details value will be taken from search response.
				JSONArray passengerDetailJsonArray=new JSONArray();
				if (operationName.equals("search") || operationName.equals("reprice")) {
					JSONArray classAvailJsonArray=jsonObj.getJSONArray(JSON_PROP_CLASSAVAIL);
					passengerDetailJsonArray=getPassengerDetailsArrayFromSearch(classAvailJsonArray,trainDetailsJsonObj);	
				}
				/*else if (operationName.equals("reprice")) {
					//In case of reprice operation passengers details value will be taken from reprice request.
					//TODO:Need to confirm whether we will get fare break up per passenger only then we can create structure as per CC
					JSONArray passengersJsonArray=requestBodyJson.getJSONArray(JSON_PROP_CLASSAVAIL);
					passengerDetailJsonArray=getPassengerDetailsArrayFromReprice(passengersJsonArray,trainDetailsJsonObj);
				}*/
				trainDetailsJsonObj.put(JSON_PROP_PASSENGER_DETAILS, passengerDetailJsonArray);
				trainDetailsJsonArray.put(trainDetailsJsonObj);
			}
			}
			//end
			advancedDefinitionJson.put(JSON_PROP_TRAINDETAILS, trainDetailsJsonArray);
//			businessRuleIntakeJsonArray.put(trainDetailsJson);
			businessRuleIntakeJsonArray.put(advancedDefinitionJson);
			breClientReqRoot.put(JSON_PROP_BUSSRULEINTAKE, businessRuleIntakeJsonArray);
			//end
			JSONObject insertJsonObj=commandsJsonArray.getJSONObject(i).getJSONObject(JSON_PROP_INSERT);
			insertJsonObj.put(JSON_RETURN_OBJECT, true);
			insertJsonObj.put(JSON_ENTRY_POINT, "DEFAULT");
			}
		}
		return breClientReqJson;
	}

	//In case of reprice operation passengers details value will be taken from reprice request.
	//TODO:Need to confirm whether we will get fare break up per passenger only then we can create structure as per CC
	private static JSONArray getPassengerDetailsArrayFromReprice(JSONArray passengersJsonArray,
			JSONObject trainDetailsJsonObj) {
		for (Object object1 : passengersJsonArray) {
			JSONObject jsonObject1=(JSONObject)object1;
		}
		return null;
	}

	//In case of search operation passenger details value will be taken from search response.
	private static JSONArray getPassengerDetailsArrayFromSearch(JSONArray classAvailJsonArray,JSONObject trainDetailsJsonObj) {
		JSONArray passengerDetailJsonArray=new JSONArray();
		for (Object object1 : classAvailJsonArray) {
			JSONObject jsonObject1=(JSONObject)object1;
				trainDetailsJsonObj.put(JSON_PROP_SEAT_CLASS, jsonObject1.get(JSON_PROP_RESERVATIONCLASS));
			
			JSONObject passengersJsonObject=new JSONObject();
//			if (operationName.equals("search") || operationName.equals("reprice")) {
				passengersJsonObject.put(JSON_PROP_PASSENGER_TYPE, "ADT");
				passengersJsonObject.put(JSON_PROP_TOTALFARE,jsonObject1.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_TOTALFARE).getInt(JSON_PROP_AMOUNT));
				
				//fareBreakUp structure
				JSONObject fareBreakUpJsonObject=new JSONObject();
				fareBreakUpJsonObject.put(JSON_PROP_BASEFARE, jsonObject1.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_FAREBREAKUP).getJSONObject(JSON_PROP_BASEFARE).getInt(JSON_PROP_AMOUNT));
				
				//count total fees and ancillary charges, put in taxDetailsJsonObject
				JSONArray taxDetailsJsonArray=new JSONArray();
				JSONObject feesJsonObj=new JSONObject();
				JSONArray feesJsonArray=jsonObject1.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_FAREBREAKUP).getJSONArray(JSON_PROP_FEES);
				int totalFees=0;
				for (Object object2 : feesJsonArray) {
					JSONObject jsonObject2=(JSONObject)object2;
					totalFees=totalFees+jsonObject2.getInt(JSON_PROP_AMOUNT);
				}
				feesJsonObj.put(JSON_PROP_TAXNAME, JSON_PROP_FEES);
				feesJsonObj.put(JSON_PROP_TAXVALUE, totalFees);
				taxDetailsJsonArray.put(feesJsonObj);
				
				JSONObject ancillaryChargesJsonObj=new JSONObject();
				JSONArray ancillaryChargesJsonArray=jsonObject1.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_FAREBREAKUP).getJSONArray(JSON_PROP_ANCILLARY);
				int totalAncillaryCharges=0;
				for (Object object2 : ancillaryChargesJsonArray) {
					JSONObject jsonObject2=(JSONObject)object2;
					totalAncillaryCharges=totalAncillaryCharges+jsonObject2.getInt(JSON_PROP_AMOUNT);
				}
				
				ancillaryChargesJsonObj.put(JSON_PROP_TAXNAME, JSON_PROP_ANCILLARY);
				ancillaryChargesJsonObj.put(JSON_PROP_TAXVALUE, totalAncillaryCharges);
				taxDetailsJsonArray.put(ancillaryChargesJsonObj);
				
				JSONArray taxDetailsArray=jsonObject1.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_FAREBREAKUP).getJSONArray(JSON_PROP_TAXES);
				
				for (Object object2 : taxDetailsArray) {
					JSONObject jsonObject2=(JSONObject)object2;
					JSONObject taxDetailsJsonObject=new JSONObject();
					taxDetailsJsonObject.put(JSON_PROP_TAXNAME, jsonObject2.getString(JSON_PROP_CODECONTEXT));
					taxDetailsJsonObject.put(JSON_PROP_TAXVALUE, jsonObject2.getInt(JSON_PROP_AMOUNT));
					
					taxDetailsJsonArray.put(taxDetailsJsonObject);
				}
				
				fareBreakUpJsonObject.put(JSON_PROP_TAXDETAILS, taxDetailsJsonArray);
				passengersJsonObject.put(JSON_PROP_FAREBRKUP, fareBreakUpJsonObject);
//			}
			
			passengerDetailJsonArray.put(passengersJsonObject);
			
		}
		return passengerDetailJsonArray;
	}
}
