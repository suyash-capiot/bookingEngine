package com.coxandkings.travel.bookingengine.orchestrator.rail;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.rail.RailConfig;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRailData;


public class Offers implements RailConstants {
	private static final Logger logger = LogManager.getLogger(Offers.class);
	public static void getOffers(JSONObject req, JSONObject res, OffersType invocationType, String operationName) {
		System.out.println("Offer invocation: "+invocationType.toString());
		JSONObject reqHdrJson = req.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = req.getJSONObject(JSON_PROP_REQBODY);
        JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

        JSONObject resBodyJson = res.getJSONObject(JSON_PROP_RESBODY);
        
        //OffersConfig offConfig = RailConfig.getOffersConfig();
		//CommercialTypeConfig offTypeConfig = offConfig.getOfferTypeConfig(invocationType);
        ServiceConfig offTypeConfig = RailConfig.getOffersTypeConfig(invocationType);
		
		JSONObject breCpnyOffReqJson = new JSONObject(new JSONTokener(offTypeConfig.getRequestJSONShell()));
		JSONObject briJson = new JSONObject();
		
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		List<ClientInfo> clientCommHierarchyList = usrCtx.getClientHierarchy();
		String clientGroup = "";
		if (clientCommHierarchyList != null && clientCommHierarchyList.size() > 0) {
			ClientInfo clInfo = clientCommHierarchyList.get(clientCommHierarchyList.size() - 1);
			if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
				clientGroup = clInfo.getCommercialsEntityId();
			}
		}
		
		
		
		
		JSONObject commonElemsJson = new JSONObject();
		//TODO:For now current date passsed as booking date. Need to discuss
		commonElemsJson.put(JSON_PROP_BOOKINGDATE, DATE_FORMAT.format(new Date()));
		commonElemsJson.put(JSON_PROP_JOURNEYTYPE, "OneWay");
		commonElemsJson.put(JSON_PROP_SUPPMARKET, "India");
		commonElemsJson.put(JSON_PROP_TRAVELDATE, "");//need to discuss
		
		//TODO:For target set now values are hard coded. From where we can get it?
		JSONArray targetSetJsonArray=new JSONArray();
		
		JSONObject noOfPassengersJsonObject=new JSONObject();
		noOfPassengersJsonObject.put(JSON_TYPE, "Number of Passengers");
		noOfPassengersJsonObject.put(JSON_PROP_VALUE, 25);
		targetSetJsonArray.put(noOfPassengersJsonObject);
		
		JSONObject noOfCabinsJsonObject=new JSONObject();
		noOfCabinsJsonObject.put(JSON_TYPE, "Number of Cabins");
		noOfCabinsJsonObject.put(JSON_PROP_VALUE, 25);
		targetSetJsonArray.put(noOfCabinsJsonObject);
		
		JSONObject noOfBookingsJsonObject=new JSONObject();
		noOfBookingsJsonObject.put(JSON_TYPE, "Number of Bookings");
		noOfBookingsJsonObject.put(JSON_PROP_VALUE, 500);
		targetSetJsonArray.put(noOfBookingsJsonObject);
		
		JSONArray productDetailsJsonArray=new JSONArray();
		
		if(!operationName.equals("reprice")) {
		//access element for "fareDetailSet"
		JSONArray originDestinationOptionsJsonArray=resBodyJson.getJSONArray(JSON_PROP_ORIGINDESTOPTS);
		JSONArray fareDetailsSetJsonArray=new JSONArray();
		
		String totalFareAmount = "";
		int baseFareAmount = 0,totalFees=0,totalFeesAmount=0,totalAncillaryChargesAmount=0,totalTaxAmount=0;
		
		//TODO:Add below amount structure in "fareDetailsSet" of offer request
		for (Object object : originDestinationOptionsJsonArray) {
			JSONObject jsonObj=(JSONObject)object;
			
			JSONObject clientDtlsJson = new JSONObject();
			JSONObject clientDetailsJsonObject=new JSONObject();
			//TODO:For now hard coded. Should be dynamic.
//			clientDtlsJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
			clientDtlsJson.put(JSON_PROP_CLIENTNAME, "E");
//			clientDtlsJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
			clientDtlsJson.put(JSON_PROP_CLIENTCAT, "abc");
//			clientDtlsJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
			clientDtlsJson.put(JSON_PROP_CLIENTSUBCAT, "Marketing");
//			clientDtlsJson.put(JSON_PROP_CLIENTTYPE, clientCtxJson.getString(JSON_PROP_CLIENTTYPE));
			clientDtlsJson.put(JSON_PROP_CLIENTTYPE, "B2B");
//			clientDtlsJson.put(JSON_PROP_POS, clientCtxJson.optString(JSON_PROP_POS, ""));
			clientDtlsJson.put(JSON_PROP_POS, "kolkata");
//			clientDtlsJson.put(JSON_PROP_CLIENTGROUP, clientGroup);
			clientDtlsJson.put(JSON_PROP_CLIENTGROUP, "Pune");
//			clientDtlsJson.put(JSON_PROP_NATIONALITY, clientCtxJson.getString(JSON_PROP_CLIENTMARKET));
			clientDtlsJson.put(JSON_PROP_NATIONALITY, "India");
			clientDetailsJsonObject.put(JSON_PROP_CLIENTDETAILS, clientDtlsJson);
			
			//TODO:Now hard coded value. Should be dynamic.
			OrgHierarchy orgHier = usrCtx.getOrganizationHierarchy();
			JSONObject cpnyDtlsJson = new JSONObject();
//			cpnyDtlsJson.put(JSON_PROP_SBU, orgHier.getSBU());
			cpnyDtlsJson.put(JSON_PROP_SBU,"abc");
//			cpnyDtlsJson.put(JSON_PROP_BU, orgHier.getBU());
			cpnyDtlsJson.put(JSON_PROP_BU, "Marketing");
//			cpnyDtlsJson.put(JSON_PROP_DIVISION, orgHier.getDivision());
			cpnyDtlsJson.put(JSON_PROP_DIVISION, "E");
//			cpnyDtlsJson.put(JSON_PROP_SALESOFFICELOC, orgHier.getSalesOfficeLoc());
			cpnyDtlsJson.put(JSON_PROP_SALESOFFICELOC, "Pune");
//			cpnyDtlsJson.put(JSON_PROP_SALESOFFICE, orgHier.getSalesOfficeName());
			cpnyDtlsJson.put(JSON_PROP_SALESOFFICE, "Akbar Travels");
//			cpnyDtlsJson.put(JSON_PROP_COMPANYMKT, clientCtxJson.getString(JSON_PROP_CLIENTMARKET));
			cpnyDtlsJson.put(JSON_PROP_COMPANYMKT,"India");
			clientDetailsJsonObject.put(JSON_PROP_COMPANYDETAILS, cpnyDtlsJson);
			
			commonElemsJson.put(JSON_PROP_SUPPNAME, jsonObj.get(JSON_PROP_SUPPREF));
			
			clientDetailsJsonObject.put(JSON_PROP_PRODUCT_CATEGORY, "Transportation");
			clientDetailsJsonObject.put(JSON_PROP_PRODUCT_CATEGORY_SUBTYPE, "IndianRailways");
			JSONObject trainDetailsJsonObject=new JSONObject();
			JSONArray classAvailInfoJsonArray = jsonObj.getJSONArray(JSON_PROP_CLASSAVAIL);
			for (Object object2 : classAvailInfoJsonArray) {
				JSONObject jsonObj2=(JSONObject)object2;
				totalFareAmount=jsonObj2.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_TOTAL).getInt(JSON_PROP_AMOUNT)+"";
				baseFareAmount=jsonObj2.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_FAREBREAKUP).getJSONObject(JSON_PROP_BASE).getInt(JSON_PROP_AMOUNT);
				JSONArray feesJsonArray=jsonObj2.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_FAREBREAKUP).getJSONArray(JSON_PROP_FEES);
				for (Object object3 : feesJsonArray) {
					JSONObject jsonObj3=(JSONObject)object3;
					if(jsonObj3.has(JSON_PROP_AMOUNT)) {
					totalFeesAmount=totalFeesAmount+jsonObj3.getInt(JSON_PROP_AMOUNT);
					}
				}
				JSONArray ancillaryChargesJsonArray=jsonObj2.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_FAREBREAKUP).getJSONArray(JSON_PROP_ANCILLARY);
				for (Object object3 : ancillaryChargesJsonArray) {
					JSONObject jsonObj3=(JSONObject)object3;
					if(jsonObj3.has(JSON_PROP_AMOUNT)) {
					totalAncillaryChargesAmount=totalAncillaryChargesAmount+jsonObj3.getInt(JSON_PROP_AMOUNT);
					}
				}
				
				JSONArray taxesJsonArray=jsonObj2.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_FAREBREAKUP).getJSONArray(JSON_PROP_TAXES);
				for (Object object3 : taxesJsonArray) {
					JSONObject jsonObj3=(JSONObject)object3;
					if(jsonObj3.has(JSON_PROP_AMOUNT)) {
						totalTaxAmount=totalTaxAmount+jsonObj3.getInt(JSON_PROP_AMOUNT);
					}
				}
				
				
			}
			
			
			JSONObject baseFareJson=new JSONObject();
			baseFareJson.put(JSON_PROP_FARE_NAME, JSON_PROP_BASE);
			baseFareJson.put(JSON_PROP_FARE_VALUE, baseFareAmount);
			
			JSONObject totalFeesJson=new JSONObject();
			totalFeesJson.put(JSON_PROP_FARE_NAME, JSON_PROP_TOTAL_FEES);
			totalFeesJson.put(JSON_PROP_FARE_VALUE, totalFeesAmount);
			
			JSONObject totalAncillaryChargesJson=new JSONObject();
			totalAncillaryChargesJson.put(JSON_PROP_FARE_NAME, JSON_PROP_ANCILLARY);
			totalAncillaryChargesJson.put(JSON_PROP_FARE_VALUE, totalAncillaryChargesAmount);
			
			JSONObject totalTaxJson=new JSONObject();
			totalTaxJson.put(JSON_PROP_FARE_NAME, JSON_PROP_TOTAL_PRICE_TAX);
			totalTaxJson.put(JSON_PROP_FARE_VALUE, totalTaxAmount);
			
			fareDetailsSetJsonArray.put(baseFareJson);
			fareDetailsSetJsonArray.put(totalFeesJson);
			fareDetailsSetJsonArray.put(totalAncillaryChargesJson);
			fareDetailsSetJsonArray.put(totalTaxJson);
			
//			JSONObject fareDetailsSetJsonObject=new JSONObject();
			clientDetailsJsonObject.put(JSON_PROP_FARE_DETAILS_VALUE, fareDetailsSetJsonArray);
			
			JSONObject passDetailJson=new JSONObject();
			//TODO:In case of search passenger type will be ADT
			passDetailJson.put(JSON_PROP_PASSENGER_TYPE, "ADT");
			passDetailJson.put(JSON_PROP_FARE_DETAILS_VALUE, fareDetailsSetJsonArray);
			clientDetailsJsonObject.put(JSON_PROP_PASSENGER_DETAILS, passDetailJson);
			
//			JSONObject totalFareJsonObject=new JSONObject();
			clientDetailsJsonObject.put(JSON_PROP_TOTAL, totalFareAmount);
			
			//TODO:For train details values are hard coded should be dynamic
			JSONArray trainDetailsJsonArray=new JSONArray();
			
//			trainDetailsJsonObject.put(JSON_PROP_TRAINNAME, jsonObj.getJSONObject(JSON_PROP_TRAINDETAILS).getString(JSON_PROP_TRAINNAME));
			trainDetailsJsonObject.put(JSON_PROP_TRAINNAME, "Rajdhani");
			trainDetailsJsonObject.put(JSON_PROP_PASS_NAME, "Swiss Pass");
			//TODO:Values are hard coded. should be dynamic
			trainDetailsJsonObject.put(JSON_TRAIN_CATEGORY, "o");
			
			trainDetailsJsonObject.put(JSON_PROP_FROM_STATE, "Maharashtra");
			trainDetailsJsonObject.put(JSON_PROP_COUNTRY_FROM, "India");
			//TODO:For now hardcoded values
			trainDetailsJsonObject.put(JSON_PROP_PASS_NAME, "Swiss Pass");
			trainDetailsJsonObject.put(JSON_PROP_COUNTRY, "Switzerland");
			
			Map<String, Object> originLocationMap=new HashMap<String,Object>();
			originLocationMap=RedisRailData.getStationCodeInfo(reqBodyJson.getString(JSON_PROP_ORIGINLOC));
			//TODO:uncomment below code to get cityfrom dynmaically
//			trainDetailsJsonObject.put(JSON_PROP_CITY_FROM, originLocationMap.getOrDefault(JSON_PROP_CITY, ""));
			trainDetailsJsonObject.put(JSON_PROP_CITY_FROM, "Mumbai");
			trainDetailsJsonObject.put(JSON_PROP_COUNTRY_FROM, "India");
			
			
			Map<String, Object> destinationLocationMap=new HashMap<String,Object>();
			destinationLocationMap=RedisRailData.getStationCodeInfo(reqBodyJson.getString(JSON_PROP_DESTLOC));
			//TODO:Uncomment below code to get cityto dynamically
//			trainDetailsJsonObject.put(JSON_PROP_CITY_TO, destinationLocationMap.getOrDefault(JSON_PROP_CITY, ""));
			trainDetailsJsonObject.put(JSON_PROP_CITY_TO, "Goa");
			trainDetailsJsonObject.put(JSON_PROP_COUNTRY_TO, "India");
			
			
			//Add fare details array in train details array
			trainDetailsJsonObject.put(JSON_PROP_FAREDETAILS, fareDetailsSetJsonArray);
			trainDetailsJsonObject.put(JSON_PROP_TOTAL, totalFareAmount);
			
			trainDetailsJsonArray.put(trainDetailsJsonObject);
			clientDetailsJsonObject.put(JSON_PROP_TRAINDETAILS, trainDetailsJsonArray);
			productDetailsJsonArray.put(clientDetailsJsonObject);
		}
	}else if (operationName.equals("retrieve"))
	{
		String totalAmountStr=resBodyJson.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_TOTALFARE).getString(JSON_PROP_AMOUNT);
		int totalAmount=0;
		double totalTaxesAmount=0.0,totalCharges=0.0;
		if (totalAmountStr!=null && !totalAmountStr.equals("")) {
			totalAmount=Integer.parseInt(totalAmountStr);
		}
		JSONArray taxesJsonArray=resBodyJson.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_TOTALFARE).getJSONArray(JSON_PROP_TAXES);
	
		//Calculation of total tax amount
		for (Object object : taxesJsonArray) {
			JSONObject jsonObj=(JSONObject)object;
			String taxAmount=jsonObj.getString(JSON_PROP_AMOUNT);
			if (taxAmount!=null && !taxAmount.equals("")) {
				totalTaxesAmount=totalTaxesAmount+Double.parseDouble(taxAmount);
			}
		}
		
		//Calculation of charges amount
		JSONArray chargesJsonArray=resBodyJson.getJSONObject(JSON_PROP_PRICING).getJSONArray(JSON_PROP_CHARGES_VALUE);
		for (Object object : chargesJsonArray) {
			JSONObject jsonObj=(JSONObject)object;
			String amount=jsonObj.getString(JSON_PROP_AMOUNT);
			if (amount!=null && !amount.equals("")) {
				totalCharges=totalCharges+Double.parseDouble(amount);
			}
		}
		
		JSONArray fareDetailsSetJsonArray=new JSONArray();
		JSONObject totalChargesAmount=new JSONObject();
		totalChargesAmount.put(JSON_PROP_FARE_NAME, JSON_PROP_ANCILLARY);
		totalChargesAmount.put(JSON_PROP_FARE_VALUE, totalCharges);
		
		JSONObject totalTaxJson=new JSONObject();
		totalTaxJson.put(JSON_PROP_FARE_NAME, JSON_PROP_TAXES);
		totalTaxJson.put(JSON_PROP_FARE_VALUE, totalTaxesAmount);
		
		fareDetailsSetJsonArray.put(totalChargesAmount);
		fareDetailsSetJsonArray.put(totalTaxJson);
		
		briJson.put(JSON_PROP_FARE_DETAILS_VALUE, fareDetailsSetJsonArray);
		briJson.put(JSON_PROP_TOTAL, totalAmount);
	}
		
		// TODO: Populate Target Set (Slabs)
		briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
		briJson.put(JSON_PROP_PRODUCT_DETAILS, productDetailsJsonArray);
		commonElemsJson.put(JSON_PROP_TARGETSET, targetSetJsonArray);
		breCpnyOffReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.rail_companyoffers.rail_companyoffers_wredemption.Root").getJSONArray("businessRuleIntake").put(briJson);
		System.out.println("Offer request structure: "+breCpnyOffReqJson.toString());
		JSONObject breOffResJson = null;
        try {
            //breOffResJson = HTTPServiceConsumer.consumeJSONService("BRMS/ComOffers", offTypeConfig.getServiceURL(), offConfig.getHttpHeaders(), breCpnyOffReqJson);
        	breOffResJson = HTTPServiceConsumer.consumeJSONService("BRMS/ComOffers", offTypeConfig.getServiceURL(), offTypeConfig.getHttpHeaders(), breCpnyOffReqJson);
            System.out.println("Offer Response: "+breOffResJson);
        }
        catch (Exception x) {
            logger.warn("An exception occurred when calling company offers", x);
        }
		
	}
}
