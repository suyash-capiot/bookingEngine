package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.cruise.CruiseConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirSearchProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class CruiseSupplierCommercials implements CruiseConstants {
	
	private static SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");
	private static SimpleDateFormat mDateFormat2 = new SimpleDateFormat("yyyy-MM-dd");
	private static final Logger logger = LogManager.getLogger(AirSearchProcessor.class);
	
	 public static JSONObject getSupplierCommercialsV1(JSONObject req, JSONObject res,Map<String,String> SI2BRMSSailingOptionMap) throws Exception{
    	
        //CommercialsConfig commConfig = CruiseConfig.getCommercialsConfig();
        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		 ServiceConfig commTypeConfig = CruiseConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
        JSONObject breSuppReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));

        JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);

        JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);

        JSONObject breHdrJson = new JSONObject();
        breHdrJson.put("sessionID", resHeader.getString(JSON_PROP_SESSIONID));
        breHdrJson.put("transactionID", resHeader.getString(JSON_PROP_TRANSACTID));
        breHdrJson.put("userID", resHeader.getString(JSON_PROP_USERID));
        breHdrJson.put("operationName", "Search");

        JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.cruise_commercialscalculationengine.suppliertransactionalrules.Root");
        rootJson.put("header", breHdrJson);
        
        int a=0;int b=0;
        JSONArray briJsonArr = new JSONArray();
        JSONArray cruiseOptionsJsonArr = resBody.getJSONArray("cruiseOptions");
        for(int j=0;j<cruiseOptionsJsonArr.length();j++)
        {
        	Map<String, JSONObject> bussRuleIntakeBySupp = new HashMap<String, JSONObject>();
        	
	        int sailingIdx=0;int briIdx =-1;
	        JSONArray cruiseDetailsJsonArr = null;
	        for (int i=0; i < cruiseOptionsJsonArr.length(); i++) {
	        	JSONObject briJson = null;
	            JSONObject sailingOptionJson = cruiseOptionsJsonArr.getJSONObject(i);
	            String suppID = sailingOptionJson.getString(JSON_PROP_SUPPREF);
	            
	            if(bussRuleIntakeBySupp.containsKey(suppID))
	            {
	            	briJson = bussRuleIntakeBySupp.get(suppID);
	            }
	            else
	            {
	            	briJson = createBusinessRuleIntakeForSupplier(reqHeader, reqBody, resHeader, resBody, sailingOptionJson);
	            	bussRuleIntakeBySupp.put(suppID, briJson);
	            	briIdx++;
	            }
	            
	            cruiseDetailsJsonArr = briJson.getJSONArray("cruiseDetails");
	            cruiseDetailsJsonArr.put(getCruiseDetailsJSON(sailingOptionJson));
	            SI2BRMSSailingOptionMap.put(String.format("%s|%s", j,sailingIdx), String.format("%s|%s", briIdx,cruiseDetailsJsonArr.length()-1));
	            sailingIdx++;
	        }
	        Iterator<Map.Entry<String, JSONObject>> briEntryIter = bussRuleIntakeBySupp.entrySet().iterator();
	        while (briEntryIter.hasNext()) {
	        	Map.Entry<String, JSONObject> briEntry = briEntryIter.next();
	        	briJsonArr.put(briEntry.getValue());
	        }
        }
        rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
		 
        System.out.println("SUPPLIERRQJSON");
        System.out.println(breSuppReqJson.toString());
        
        JSONObject breSuppResJson = null;
        
       /* Scanner in = null;
        
        in = new Scanner(new FileReader("D:\\BookingEngine\\SupplierTransactionalNewRQ.json"));
 
	    StringBuilder sb = new StringBuilder();
	    while(in.hasNext()) {
	        sb.append(in.next());
	    }
	    in.close();
	    String jsonStr = sb.toString();
    	JSONObject json = new JSONObject(jsonStr);*/
        
        try {
            //breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS/SuppComm", commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breSuppReqJson);
        	breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS/SuppComm", commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breSuppReqJson);
        }
        catch (Exception x) {
            logger.warn("An exception occurred when calling supplier commercials", x);
        }
        
        System.out.println("SUPPLIERRSJSON");
        System.out.println(breSuppResJson.toString());
        
        JSONObject parentSupplTransJson = new JSONObject();
        parentSupplTransJson.put(PRODUCT_CRUISE_SUPPLTRANSRQ, breSuppReqJson);
        parentSupplTransJson.put(PRODUCT_CRUISE_SUPPLTRANSRS, breSuppResJson);
        
		return parentSupplTransJson;
	 }
	
	 public static JSONObject getSupplierCommercialsV2(JSONObject req, JSONObject res,Map<String,String> SI2BRMSSailingOptionMap) throws Exception{
	    	
	        //CommercialsConfig commConfig = CruiseConfig.getCommercialsConfig();
	        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		 	ServiceConfig commTypeConfig = CruiseConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
	        JSONObject breSuppReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));

	        JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
	        JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);

	        JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
	        JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);

	        JSONObject breHdrJson = new JSONObject();
	        breHdrJson.put("sessionID", resHeader.getString(JSON_PROP_SESSIONID));
	        breHdrJson.put("transactionID", resHeader.getString(JSON_PROP_TRANSACTID));
	        breHdrJson.put("userID", resHeader.getString(JSON_PROP_USERID));
	        breHdrJson.put("operationName", "Search");

	        JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.cruise_commercialscalculationengine.suppliertransactionalrules.Root");
	        rootJson.put("header", breHdrJson);
	        
	        int a=0;int b=0;
	        JSONArray briJsonArr = new JSONArray();
	        JSONArray cruiseOptionsJsonArr = resBody.getJSONArray("cruiseOptions");
	        int briIdx =-1;
	        Map<String, JSONObject> bussRuleIntakeBySupp = new HashMap<String, JSONObject>();
	        for(int j=0;j<cruiseOptionsJsonArr.length();j++)
	        {
		        JSONArray cruiseDetailsJsonArr = null;
		        
	        	JSONObject briJson = null;
	            JSONObject sailingOptionJson = cruiseOptionsJsonArr.getJSONObject(j);
	            String suppID = sailingOptionJson.getString(JSON_PROP_SUPPREF);
	            
	            if(bussRuleIntakeBySupp.containsKey(suppID))
	            {
	            	briJson = bussRuleIntakeBySupp.get(suppID);
	            }
	            else
	            {
	            	briJson = createBusinessRuleIntakeForSupplier(reqHeader, reqBody, resHeader, resBody, sailingOptionJson);
	            	bussRuleIntakeBySupp.put(suppID, briJson);
	            	briIdx++;
	            }
	            
	            cruiseDetailsJsonArr = briJson.getJSONArray("cruiseDetails");
	            cruiseDetailsJsonArr.put(getCruiseDetailsJSON(sailingOptionJson));
	            SI2BRMSSailingOptionMap.put(String.format("%s", j), String.format("%s|%s", briIdx,cruiseDetailsJsonArr.length()-1));
		            
		        Iterator<Map.Entry<String, JSONObject>> briEntryIter = bussRuleIntakeBySupp.entrySet().iterator();
		        while (briEntryIter.hasNext()) {
		        	Map.Entry<String, JSONObject> briEntry = briEntryIter.next();
		        	briJsonArr.put(briEntry.getValue());
		        }
	        }
	        rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
			 
	        System.out.println("SUPPLIERRQJSON");
	        System.out.println(breSuppReqJson.toString());
	        
	        JSONObject breSuppResJson = null;
	        
	        try {
	            //breSuppResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breSuppReqJson);
	        	breSuppResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breSuppReqJson);
	        }
	        catch (Exception x) {
	            logger.warn("An exception occurred when calling supplier commercials", x);
	        }
	        
	        System.out.println("SUPPLIERRSJSON");
	        System.out.println(breSuppResJson.toString());
	        
	        JSONObject parentSupplTransJson = new JSONObject();
	        parentSupplTransJson.put(PRODUCT_CRUISE_SUPPLTRANSRQ, breSuppReqJson);
	        parentSupplTransJson.put(PRODUCT_CRUISE_SUPPLTRANSRS, breSuppResJson);
	        
			return parentSupplTransJson;
		 }
	 
	 public static JSONObject getCruiseDetailsJSON(JSONObject sailingOptionJson)throws Exception {
		 
		 JSONObject cruiseDetailsJson = new JSONObject();
		 Random random = new Random();
//		 cruiseDetailsJson.put("cruiseNumber", String.format("%s%s",sailingOptionJson.getJSONObject("SelectedSailing").getString("VoyageID"),sailingOptionJson.getJSONObject("TPA_Extensions").getJSONObject("Cruise").getJSONObject("SailingDates").getJSONObject("Sailing").getString("SailingID")));
		 cruiseDetailsJson.put("cruiseNumber","302");
//		 cruiseDetailsJson.put("UniqueId", sailingOptionJson.get("UniqueID"));
//		 cruiseDetailsJson.put("bookingEngineNumber",sailingOptionJson.getJSONObject("SelectedSailing").getString("Start") );
//		 cruiseDetailsJson.put("cruiseTiming", mDateFormat.format(mDateFormat2.parse(sailingOptionJson.getString("startDate"))));
		 cruiseDetailsJson.put("cruiseTiming", mDateFormat.format(mDateFormat2.parse("2017-12-28")));
		 cruiseDetailsJson.put("cabinCategory","CC");
		 cruiseDetailsJson.put("cabinType","OceanView");
		 cruiseDetailsJson.put("fromContinent","Asia");
		 cruiseDetailsJson.put("fromCountry","India");
		 cruiseDetailsJson.put("fromCity","Mumbai");
		 cruiseDetailsJson.put("toContinent","Asia");
		 cruiseDetailsJson.put("toCountry","India");
		 cruiseDetailsJson.put("toCity","Delhi");
		 
//		 passDetailsJson.put("passengerType", "ADT");
//		 passDetailsJson.put("totalFare", sailingOptionJson.getJSONObject("Prices").getString("TotalPrice"));
//		 JSONObject fareBreakUpJson = new JSONObject();
		 
		 JSONArray rsCategoryJsonArr = sailingOptionJson.getJSONArray("category");
		 JSONArray cabinDetailsJsonArr = new JSONArray();
		 for(int i=0;i<rsCategoryJsonArr.length();i++)
		 {
			 JSONObject cabinDetailsJson = new JSONObject();
			 JSONObject rsCategoryJson = rsCategoryJsonArr.getJSONObject(i);
			 
			 /*cabinDetailsJson.put("cabinCategory", rsCategoryJson.optString("PricedCategoryCode"));
			 cabinDetailsJson.put("cabinType", rsCategoryJson.optString("CategoryName"));*/
			 cabinDetailsJson.put("cabinCategory", "CC");
			 cabinDetailsJson.put("cabinType", "OceanView");
			 
			 JSONArray rsPassengerPricesJsonArr = rsCategoryJson.getJSONArray("passengerPrices");
			 JSONArray passengerDetailsArr = new JSONArray();
			 
			 for(int k=0;k<rsPassengerPricesJsonArr.length();k++)
			 {
				JSONObject rsPassengerPriceJson = rsPassengerPricesJsonArr.getJSONObject(k);
				 
				JSONObject passengerDetailsJson = new JSONObject();
				 
				passengerDetailsJson.put(JSON_PROP_TOTALFARE, rsPassengerPriceJson.optString(JSON_PROP_TOTALFARE));
				passengerDetailsJson.put("passengerType", rsPassengerPriceJson.optString("passengerType"));
			 	passengerDetailsJson.put("fareBreakUp",rsPassengerPriceJson.getJSONObject("fareBreakUp"));
			 	
			 	passengerDetailsArr.put(passengerDetailsJson);
			 }
			 cabinDetailsJson.put("passengerDetails", passengerDetailsArr);
			 cabinDetailsJsonArr.put(cabinDetailsJson);
		 }
		 
		 cruiseDetailsJson.put("cabinDetails", cabinDetailsJsonArr);
		/* fareBreakUpJson.put("baseFare", String.valueOf(Double.valueOf(sailingOptionJson.getJSONObject("Prices").getString("TotalPrice"))-100));
		 JSONArray taxDetailsJsonArray = new JSONArray();
         JSONObject taxDetailsJson = new JSONObject();
		 taxDetailsJson.put("taxName", "YQ");
		 taxDetailsJson.put("taxValue", "50");
		 taxDetailsJsonArray.put(taxDetailsJson);
		 
		 fareBreakUpJson.put("taxDetails", taxDetailsJsonArray);
		 
		 passDetailsJson.put("fareBreakUp",fareBreakUpJson);
		 
		 passDetailsjsonArray.put(passDetailsJson);
		 cruiseDetailsJson.put("passengerDetails", passDetailsjsonArray);*/
		 
//		 
//		 fareBreakUpJson.put("baseFare", "1600");
//		 
//		 JSONArray taxDetailsJsonArray = new JSONArray();
//		 
//		 JSONObject taxDetailsJson = new JSONObject();
//		 taxDetailsJson.put("taxName", "YQ");
//		 taxDetailsJson.put("taxValue", "150");
		 
//		 taxDetailsJsonArray.put(taxDetailsJson);
//		 passDetailsJson.
		 
		 return cruiseDetailsJson;
	 }
	 
	 public static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqBody, JSONObject resHeader, JSONObject resBody, JSONObject sailingOptionJson) throws JSONException, ParseException {
	 
		JSONObject briJson = new JSONObject();
        JSONObject commonElemsJson = new JSONObject();
        String suppID = sailingOptionJson.getString(JSON_PROP_SUPPREF);
//        commonElemsJson.put("supplier", suppID);
//        commonElemsJson.put("supplier", suppID.substring(0, 1).toUpperCase() + suppID.substring(1).toLowerCase());
        commonElemsJson.put(JSON_PROP_SUPP, "Tourico");

        // TODO: Supplier market is hard-coded below. Where will this come from? This should be ideally come from supplier credentials.
        commonElemsJson.put(JSON_PROP_SUPPMARKET, "UAE");//hard code
        commonElemsJson.put("contractValidity", mDateFormat.format(mDateFormat2.parse("2017-02-10")));
        commonElemsJson.put(JSON_PROP_PRODNAME, PRODUCT_CRUISE_PRODS);
        // TODO: Check how the value for segment should be set?
        commonElemsJson.put(JSON_PROP_SEGMENT, "Active");
        JSONObject clientCtx = reqHeader.getJSONObject("clientContext");
        commonElemsJson.put(JSON_PROP_CLIENTTYPE, clientCtx.getString("clientType"));
        commonElemsJson.put(JSON_PROP_CLIENTGROUP, "TravelAgent");
        commonElemsJson.put(JSON_PROP_CLIENTNAME, "AkbarTravels");
        // TODO: Properties for clientGroup, clientName, iatanumber are not yet set. Are these required for B2C? What will be BRMS behavior if these properties are not sent.
        briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);

        
        //-----x---------------x--------------x----------------x--------------x----------------x----------------x-------------------x----------------x---------------x----------------x------------------x-------------x-----------
        JSONObject advDefnJson = new JSONObject();
        advDefnJson.put("ticketingDate", mDateFormat.format(mDateFormat2.parse("2018-01-10")));
        // TODO: How to set travelType?
//        advDefnJson.put("travelDate", mDateFormat.format(mDateFormat2.parse(sailingOptionJson.getString("startDate"))));
        advDefnJson.put(JSON_PROP_TRAVELTYPE, "SITI");
        advDefnJson.put(JSON_PROP_JOURNEYTYPE, "OneWay");
        advDefnJson.put("cruiseType", "From");
        advDefnJson.put("cruiseLineType", "Online");
//        advDefnJson.put("cabinType", "OceanView");
        advDefnJson.put("travelProductName", "Cruise");

        /*JSONArray resFlSegJsonArr = sailingOptionJson.getJSONObject(JSON_PROP_AIRITINERARY).getJSONArray(JSON_PROP_ORIGDESTOPTS).getJSONObject(0).getJSONArray(JSON_PROP_FLIGHTSEG); 
        
        String origAirport = resFlSegJsonArr.getJSONObject(0).getString("originLocation");
        Map<String,String> origAirportAttrs = RedisAirportData.getAirportInfo(origAirport);
        advDefnJson.put("fromContinent", origAirportAttrs.get("continent"));
        advDefnJson.put("fromCountry", origAirportAttrs.get("country"));
        advDefnJson.put("fromCity", origAirportAttrs.get("city"));

        String destAirport = resFlSegJsonArr.getJSONObject(resFlSegJsonArr.length() - 1).getString("destinationLocation");
        Map<String,String> destAirportAttrs = RedisAirportData.getAirportInfo(destAirport);
        advDefnJson.put("toContinent", destAirportAttrs.get("continent"));
        advDefnJson.put("toCountry", destAirportAttrs.get("country"));
        advDefnJson.put("toCity", destAirportAttrs.get("city"));*/

        // TODO: connectivitySupplierType hard-coded to 'LCC'. How should this value be assigned?
        advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "LCC");
        // TODO: What value to set for connectivitySupplier? For now, it is set to the same value as supplierID.
        //advDefnJson.put("connectivitySupplier", resBody.getString("supplierRef"));
        advDefnJson.put(JSON_PROP_CONNECTSUPP, "StarCruise");
        // TODO: credentialsName hard-coded to 'StarCruise'. This should come from product suppliers list in user context.
        advDefnJson.put(JSON_PROP_CREDSNAME, "StarCruise");
        // TODO: bookingType hard-coded to 'Online'. How should this value be assigned?
        advDefnJson.put(JSON_PROP_BOOKINGTYPE, "Online");
        
        //Values hardcoded for Cruise
//        advDefnJson.put("cabinClass", "Economy");
//        advDefnJson.put("rbd", "S");
//        advDefnJson.put("fareBasisValue", "YEEYEE");
//        advDefnJson.put("dealCode", "DC01");
        
        briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);
        
        JSONArray cruiseDetailsArr = new JSONArray();
        briJson.put("cruiseDetails", cruiseDetailsArr);
		 
		return briJson;
	 }
	 
	 
	 public static JSONObject createBusinessRuleIntakeForSupplierV2(JSONObject reqHeader, JSONObject reqBody, JSONObject resHeader, JSONObject resBody, JSONObject sailingOptionJson) throws JSONException, ParseException {
	 {
		JSONObject briJson = new JSONObject();
        JSONObject commonElemsJson = new JSONObject();
        String suppID = sailingOptionJson.getString(JSON_PROP_SUPPREF);
//	        commonElemsJson.put("supplier", suppID);
//	        commonElemsJson.put("supplier", suppID.substring(0, 1).toUpperCase() + suppID.substring(1).toLowerCase());
        commonElemsJson.put(JSON_PROP_SUPP, suppID);
        
        UserContext userCtx = UserContext.getUserContextForSession(reqHeader);
        
        // TODO: Supplier market is hard-coded below. Where will this come from? This should be ideally come from supplier credentials.
        commonElemsJson.put(JSON_PROP_SUPPMARKET, "India");//hard code
        commonElemsJson.put("contractValidity", mDateFormat.format(mDateFormat2.parse("2017-02-10")));//not sure which date to put here
        commonElemsJson.put(JSON_PROP_PRODNAME, PRODUCT_CRUISE_PRODS);
        // TODO: Check how the value for segment should be set?
        commonElemsJson.put(JSON_PROP_SEGMENT, "Active");
        JSONObject clientCtx = reqHeader.getJSONObject("clientContext");
        commonElemsJson.put(JSON_PROP_CLIENTTYPE, clientCtx.getString(JSON_PROP_CLIENTTYPE));
        if (userCtx != null) {
        	commonElemsJson.put(JSON_PROP_CLIENTNAME, (userCtx != null) ? userCtx.getClientName() : "");
        	List<ClientInfo> clientHierarchyList = userCtx.getClientHierarchy();
        	if (clientHierarchyList != null && clientHierarchyList.size() > 0) {
        		ClientInfo clInfo = clientHierarchyList.get(clientHierarchyList.size() - 1);
        		if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
        			commonElemsJson.put(JSON_PROP_CLIENTGROUP, clInfo.getCommercialsEntityId());
        		}
        	}
        }
        
        // TODO: Properties for clientGroup, clientName, iatanumber are not yet set. Are these required for B2C? What will be BRMS behavior if these properties are not sent.
        briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);

        
        //-----x---------------x--------------x----------------x--------------x----------------x----------------x-------------------x----------------x---------------x----------------x------------------x-------------x-----------
        JSONObject advDefnJson = new JSONObject();
        advDefnJson.put("ticketingDate", mDateFormat.format(new Date()));
        // TODO: How to set travelType?
//	        advDefnJson.put("travelDate", mDateFormat.format(mDateFormat2.parse(sailingOptionJson.getString("startDate"))));
        advDefnJson.put(JSON_PROP_TRAVELTYPE, "SITI");
        advDefnJson.put(JSON_PROP_JOURNEYTYPE, "OneWay");
        advDefnJson.put("cruiseType", "From");
        advDefnJson.put("cruiseLineType", "Online");
//	        advDefnJson.put("cabinType", "OceanView");
        advDefnJson.put("travelProductName", "Cruise");

        /*JSONArray resFlSegJsonArr = sailingOptionJson.getJSONObject(JSON_PROP_AIRITINERARY).getJSONArray(JSON_PROP_ORIGDESTOPTS).getJSONObject(0).getJSONArray(JSON_PROP_FLIGHTSEG); 
        
        String origAirport = resFlSegJsonArr.getJSONObject(0).getString("originLocation");
        Map<String,String> origAirportAttrs = RedisAirportData.getAirportInfo(origAirport);
        advDefnJson.put("fromContinent", origAirportAttrs.get("continent"));
        advDefnJson.put("fromCountry", origAirportAttrs.get("country"));
        advDefnJson.put("fromCity", origAirportAttrs.get("city"));

        String destAirport = resFlSegJsonArr.getJSONObject(resFlSegJsonArr.length() - 1).getString("destinationLocation");
        Map<String,String> destAirportAttrs = RedisAirportData.getAirportInfo(destAirport);
        advDefnJson.put("toContinent", destAirportAttrs.get("continent"));
        advDefnJson.put("toCountry", destAirportAttrs.get("country"));
        advDefnJson.put("toCity", destAirportAttrs.get("city"));*/

        // TODO: connectivitySupplierType hard-coded to 'LCC'. How should this value be assigned?
        advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "LCC");
        // TODO: What value to set for connectivitySupplier? For now, it is set to the same value as supplierID.
        //advDefnJson.put("connectivitySupplier", resBody.getString("supplierRef"));
        advDefnJson.put(JSON_PROP_CONNECTSUPP, suppID);
        // TODO: credentialsName hard-coded to 'StarCruise'. This should come from product suppliers list in user context.
        advDefnJson.put(JSON_PROP_CREDSNAME, "StarCruise");
        // TODO: bookingType hard-coded to 'Online'. How should this value be assigned?
        advDefnJson.put(JSON_PROP_BOOKINGTYPE, "Online");
        
        //Values hardcoded for Cruise
//	        advDefnJson.put("cabinClass", "Economy");
//	        advDefnJson.put("rbd", "S");
//	        advDefnJson.put("fareBasisValue", "YEEYEE");
//	        advDefnJson.put("dealCode", "DC01");
        
        briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);
        
        JSONArray cruiseDetailsArr = new JSONArray();
        briJson.put("cruiseDetails", cruiseDetailsArr);
		 
		return briJson;
	 }
	 
  }
}
