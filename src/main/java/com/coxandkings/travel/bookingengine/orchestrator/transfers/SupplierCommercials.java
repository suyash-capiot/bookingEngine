package com.coxandkings.travel.bookingengine.orchestrator.transfers;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.transfers.TransfersConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class SupplierCommercials implements TransfersConstants {
	
	 public static final String OPERATION = "LowFareSearch";
	    public static final String HTTP_AUTH_BASIC_PREFIX = "Basic ";
	    private static final Logger logger = LogManager.getLogger(TransfersSearchProcessor.class);
	

	    private static SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");
	
	  public static JSONObject getSupplierCommercialsV2(CommercialsOperation op, JSONObject req, JSONObject res) throws Exception{
	    	Map<String, JSONObject> bussRuleIntakeBySupp = new HashMap<String, JSONObject>();
	    	
	        //CommercialsConfig commConfig = TransfersConfig.getCommercialsConfig();
	        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
	    	ServiceConfig commTypeConfig = TransfersConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
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

	        JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.transfers_commercialscalculationengine.suppliertransactionalrules.Root");
	        rootJson.put("header", breHdrJson);

	        JSONArray briJsonArr = new JSONArray();
	        JSONArray groundServiceJsonArr = resBody.getJSONArray(JSON_PROP_GROUNDSERVICES);
	        JSONArray transfersDetailsDetailsJsonArr = null;
	        for (int i=0; i < groundServiceJsonArr.length(); i++) {
	            JSONObject groundServiceJson = groundServiceJsonArr.getJSONObject(i);
	            String suppID = groundServiceJson.getString(JSON_PROP_SUPPREF);
	           // String suppID ="ACAMPORA";
	            JSONObject briJson = null;
	            if (bussRuleIntakeBySupp.containsKey(suppID)) {
	            	briJson = bussRuleIntakeBySupp.get(suppID);
	            }
	            else {
	            	briJson = createBusinessRuleIntakeForSupplier(reqHeader, reqBody, resHeader, resBody, groundServiceJson);
	            	bussRuleIntakeBySupp.put(suppID, briJson);
	            }
	            
	            transfersDetailsDetailsJsonArr = briJson.getJSONArray(JSON_PROP_TRANSFERSDETAILS);
	            transfersDetailsDetailsJsonArr.put(getBRMSTransfersDetailsJSON(groundServiceJson));
	        }

	        Iterator<Map.Entry<String, JSONObject>> briEntryIter = bussRuleIntakeBySupp.entrySet().iterator();
	        while (briEntryIter.hasNext()) {
	        	Map.Entry<String, JSONObject> briEntry = briEntryIter.next();
	        	briJsonArr.put(briEntry.getValue());
	        }
	        rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);

	        JSONObject breSuppResJson = null;
	        try {
	            //breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS/SuppComm", commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breSuppReqJson);
	        	breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS/SuppComm", commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breSuppReqJson);
	         
	        }
	        catch (Exception x) {
	            logger.warn("An exception occurred when calling supplier commercials", x);
	        }

	        return breSuppResJson;
	    }

	private static JSONObject getBRMSTransfersDetailsJSON(JSONObject groundServiceJson) {
		/* JSONArray transfersDetailsJsonArr = new JSONArray();*/
	      /*  int j;
	        for(j=0 ; j<1 ; j++) {*/
	        	JSONObject transfersDetailsJson = new JSONObject();
	        	
	        	//TODO: HardCode for transfersDetails
	        	//transfersDetailsJson.put("transfersNumber", "450");
	        	transfersDetailsJson.put("vehicleClass", "Car");
	        	transfersDetailsJson.put("fromContinent", "Asia");
	        	transfersDetailsJson.put("fromCountry", "India");
	        	transfersDetailsJson.put("fromCity", "Mumbai");
	        	transfersDetailsJson.put("fromState", "Maharashtra");
	        	transfersDetailsJson.put("toContinent", "Asia");
	        	transfersDetailsJson.put("toCountry", "India");
	        	transfersDetailsJson.put("toCity", "Delhi");
	        	transfersDetailsJson.put("toState", "Delhi");
	        	transfersDetailsJson.put("vehicleCategory", "Mini");
	        	transfersDetailsJson.put("vehicleName", "Centro");
	        	transfersDetailsJson.put("pickupPoint", "Hotel");
	        	transfersDetailsJson.put("dropoffPoint", "Airport");
	        	transfersDetailsJson.put("modeofPayment", "Prepaid");
	        	//transfersDetailsJson.put("sippAcrissCode" , null);
	        	//transfersDetailsJsonArr.put(transfersDetailsJson);
	        	
	        	JSONArray passengerDetailsArr = new JSONArray();
	        	//TODO: Need of for loop for passengerDetails.
	        	//TODO: HardCode for passengerDetails.
	        	JSONObject passengerDetailsJson = new JSONObject();
	        	JSONObject totalChargeJson = groundServiceJson.getJSONObject(JSON_PROP_TOTALCHARGE);
	        	//passengerDetailsJson.put("dealCode", "DC01");
	        //	passengerDetailsJson.put("passengerType", "ADT");
	        	//passengerDetailsJson.put("totalFare",  totalChargeJson.getBigDecimal("estimatedTotalAmount").toString());
	        	JSONObject psgDetail = getPsgDetailsJson(groundServiceJson);
	    		//psgDetailsArr.put(psgDetail);
	        
	        	passengerDetailsArr.put(psgDetail);
	        	transfersDetailsJson.put("passengerDetails",passengerDetailsArr);
	        //	JSONObject fareBreakUp = getFareBreakUpJson(groundServiceJson);
	        	
	        	//TODO :  Need to remove farbreakup and taxDeatils
	        	JSONObject fareBreakUpJson = new JSONObject();
	        	fareBreakUpJson.put("baseFare", groundServiceJson.getJSONObject(JSON_PROP_TOTALCHARGE));
	        	passengerDetailsJson.put("fareBreakUp",fareBreakUpJson);
	        	
	        	
	        	
	        	JSONArray taxDetailsArr = new JSONArray();
	        	//TODO: HardCode for taxDetails
	        	//TODO: Need of For Loop
	        	JSONObject taxDetailsJson = new JSONObject();
	        	/*taxDetailsJson.put("taxName", "YQ");
	        	taxDetailsJson.put("taxValue", "300");
	      
	        	taxDetailsArr.put(taxDetailsJson);
	        	fareBreakUpJson.put("taxDetails", taxDetailsArr);*/
	        	
	        
	        	 
	        
	       // briJson.put(JSON_PROP_TRANSFERSDETAILS, transfersDetailsJsonArr);
	        	fareBreakUpJson.put("taxDetails", taxDetailsArr);
	        	return transfersDetailsJson;
	}

	private static JSONObject getPsgDetailsJson(JSONObject groundServiceJson) {
		JSONObject psgDetails = new JSONObject();
    	JSONObject totalChargeJson = groundServiceJson.getJSONObject(JSON_PROP_TOTALCHARGE);
    	//psgDetails.put("passengerType","ADT");
    	//psgDetails.put("dealCode", "DC01");
    	psgDetails.put("totalFare", totalChargeJson.getBigDecimal("amount").toString());
    	/*JSONObject fareBreakUp = getFareBreakUpJson(groundServiceJson);
    	psgDetails.put("fareBreakUp", fareBreakUp);*/
    	
    	return psgDetails;
    	}

/*	private static JSONObject getFareBreakUpJson(JSONObject groundServiceJson) {
		
		JSONObject totalChargeJson = groundServiceJson.getJSONObject(JSON_PROP_TOTALCHARGE);
	//JSONArray taxDetailsJsonArr = groundServiceJson.optJSONArray(JSON_PROP_TAXDETAILS);
		
		JSONObject fareBreakUp = new JSONObject();
    	JSONArray taxDetailsArr = new JSONArray();
    	JSONObject taxDetail = new JSONObject();
    	//This will eventually be handled by SI
    	
    	
    	if(taxDetailsJsonArr!=null && taxDetailsJsonArr.length()!=0) {
    		for(int i=0;i<taxDetailsJsonArr.length();i++) {
    			JSONObject taxDetailsJson = taxDetailsJsonArr.getJSONObject(i);
    			taxDetail.put("taxName",taxDetailsJson.optString("TaxCode"));
    			taxDetail.put("taxValue",taxDetailsJson.getBigDecimal("Total"));
    	    	taxDetailsArr.put(taxDetail);
    		}
    	}
    	else {
    		BigDecimal tax = totalChargeJson.getBigDecimal("estimatedTotalAmount").subtract(totalChargeJson.getBigDecimal("rateTotalAmount"));
	    	taxDetail.put("taxName","YQ");
	    	taxDetail.put("taxValue",300);
	    	taxDetailsArr.put(taxDetail);
    	}
    	fareBreakUp.put("baseFare", totalChargeJson.getBigDecimal("rateTotalAmount"));
    	//fareBreakUp.put("taxDetails", taxDetailsArr);
    	return fareBreakUp;
		
	}*/

	private static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqBody,
			JSONObject resHeader, JSONObject resBody, JSONObject groundServiceJson) {
		 	JSONObject briJson = new JSONObject();
		 	JSONObject commonElemsJson = new JSONObject();
	       String suppID = groundServiceJson.getString(JSON_PROP_SUPPREF);
		 	// String suppID ="ACAMPORA";
	       // commonElemsJson.put("supplier", String.format("%s%s",suppID.substring(0, 1).toUpperCase(),suppID.substring(1).toLowerCase()));
	        commonElemsJson.put("supplier",suppID);
	        // TODO: Supplier market is hard-coded below. Where will this come from? This should be ideally come from supplier credentials.
	        commonElemsJson.put("supplierMarket", "India");
	        commonElemsJson.put("contractValidity","2017-02-10T00:00:00");
	        commonElemsJson.put("productName", PRODUCT_NAME_BRMS);
	        // TODO: Check how the value for segment should be set?
	        commonElemsJson.put("segment", "Active");
	        JSONObject clientCtx = reqHeader.getJSONObject("clientContext");
	        commonElemsJson.put("clientType", clientCtx.getString("clientType"));
	       //TODO: commonElemsJson.put("clientGroup", clientCtx.getString("clientGroup"));
	        commonElemsJson.put("clientGroup", "TravelAgent");
	       //TODO:  commonElemsJson.put("clientName", clientCtx.getString("clientName"));
	        commonElemsJson.put("clientName", "ZoyRide");
	        //TODO: Check from where iatanumber value will come
	        commonElemsJson.put("iatanumber", "32r093249u");
	        
	        // TODO: Properties for clientGroup, clientName, iatanumber are not yet set. Are these required for B2C? What will be BRMS behavior if these properties are not sent.
	        briJson.put("commonElements", commonElemsJson);

	        JSONArray slabDetailsJsonArr = new JSONArray();
	        //TODO: HardCode value for Slab Details.
	        //TODO: need for loop
	        JSONObject slabDetailsJson = new JSONObject();
	        slabDetailsJson.put("slabType","NumberOfBookings");
	        slabDetailsJson.put("slabTypeValue","502");
	        slabDetailsJsonArr.put(slabDetailsJson);
	        briJson.put("slabDetails", slabDetailsJsonArr);
	        
	        JSONObject advDefnJson = new JSONObject();
	        advDefnJson.put("salesDate", "2017-02-10T00:00:00");
	        // TODO: Hardcode value of travelDate.
	        advDefnJson.put("travelDate", "2017-04-10T00:00:00");
	        // TODO: How to set travelType?
	      //  advDefnJson.put("travelType", "SITO");
	        //TODO: need to find indicator for OneWay & Return?
	       /* JSONArray resServiceJsonArr = groundServiceJson.getJSONArray(JSON_PROP_SERVICE);
	        int serviceArrLen = resServiceJsonArr.length();
	        if(serviceArrLen>1) {
	        	   advDefnJson.put("journeyType", "Return");
	        }
	        	   advDefnJson.put("journeyType", "OneWay");
	        //TODO: Hardcode value for transfersType
*/	  
	   /*   advDefnJson.put("journeyType", reqBody.get("tripType"));*/
	       // advDefnJson.put("journeyType", "OneWay");
	        advDefnJson.put("travelProductName", "Transfers");
	        advDefnJson.put("transfersType", "From");
	        advDefnJson.put("nationality", "Indian");
	        
	        advDefnJson.put("productName", "Transfers");
	        
	     /*   advDefnJson.put("travelProductName", "Transfers");

	        //JSONArray resServiceJsonArr = groundServiceJson.getJSONArray(JSON_PROP_SERVICE);
	        
	        //TODO: Hardcode value for Continent,Country,City;
	        advDefnJson.put("fromContinent", "Asia");
	        advDefnJson.put("fromCountry", "India");
	        advDefnJson.put("fromCity", "Hyderabad");
	        advDefnJson.put("fromState", "Telangana");

	        advDefnJson.put("toContinent", "Asia");
	        advDefnJson.put("toCountry", "India");
	        advDefnJson.put("toCity", "Hyderabad");
	        advDefnJson.put("toState", "Telangana");*/
	        
	        // TODO: connectivitySupplierType hard-coded to 'LCC'. How should this value be assigned?
	        advDefnJson.put("connectivitySupplierType", "LCC");
	        // TODO: What value to set for connectivitySupplier? For now, it is set to the same value as supplierID.
	        //advDefnJson.put("connectivitySupplier", resBody.getString("supplierRef"));
	      //  advDefnJson.put("connectivitySupplier", String.format("%s%s",suppID.substring(0, 1).toUpperCase(),suppID.substring(1).toLowerCase()));
	        advDefnJson.put("connectivitySupplier", suppID);
	        // TODO: credentialsName hard-coded to 'Indigo'. This should come from product suppliers list in user context.
	       // advDefnJson.put("credentialsName",  String.format("%s%s",suppID.substring(0, 1).toUpperCase(),suppID.substring(1).toLowerCase()));
	        advDefnJson.put("credentialsName",  suppID);
	        // TODO: bookingType hard-coded to 'Online'. How should this value be assigned?
	        advDefnJson.put("bookingType", "Online");
	        briJson.put("advancedDefinition", advDefnJson);
	        
	        //For Loop for transfersDetails
	      //  int transfersDetailsJsonArrLen =  groundServiceJson.getJSONArray("Service").length();
	       /* JSONArray transfersDetailsJsonArr = new JSONArray();
	        int j;
	        for(j=0 ; j<1 ; j++) {
	        	JSONObject transfersDetailsJson = new JSONObject();
	        	
	        	//TODO: HardCode for transfersDetails
	        	transfersDetailsJson.put("transfersNumber", "302");
	        	transfersDetailsJson.put("vehicleClass", "Car");
	        	transfersDetailsJson.put("transfersTiming", "2017-04-10T06:00:00");
	        	transfersDetailsJson.put("vehicleCategory", "Micro");
	        	transfersDetailsJson.put("vehicleName", "Indica");
	        	transfersDetailsJson.put("pickupPoint", "Hotel");
	        	transfersDetailsJson.put("dropoffPoint", "Airport");
	        	transfersDetailsJson.put("modeofPayment", "Postpaid");
	        	transfersDetailsJsonArr.put(transfersDetailsJson);
	        	
	        	JSONArray passengerDetailsArr = new JSONArray();
	        	//TODO: Need of for loop for passengerDetails.
	        	//TODO: HardCode for passengerDetails.
	        	JSONObject passengerDetailsJson = new JSONObject();
	        	passengerDetailsJson.put("dealCode", "DC01");
	        	passengerDetailsJson.put("passengerType", "ADT");
	        	passengerDetailsJson.put("totalFare", "1300");
	        
	        	passengerDetailsArr.put(passengerDetailsJson);
	        	transfersDetailsJson.put("passengerDetails",passengerDetailsArr);
	        
	        	JSONObject fareBreakUpJson = new JSONObject();
	        	fareBreakUpJson.put("baseFare", "1000");
	        	passengerDetailsJson.put("fareBreakUp",fareBreakUpJson);
	        	
	        	
	        	
	        	JSONArray taxDetailsArr = new JSONArray();
	        	//TODO: HardCode for taxDetails
	        	//TODO: Need of For Loop
	        	JSONObject taxDetailsJson = new JSONObject();
	        	taxDetailsJson.put("taxName", "YQ");
	        	taxDetailsJson.put("taxValue", "300");
	      
	        	taxDetailsArr.put(taxDetailsJson);
	        	fareBreakUpJson.put("taxDetails", taxDetailsArr);
	        	
	        
	        	 
	        }
	        briJson.put(JSON_PROP_TRANSFERSDETAILS, transfersDetailsJsonArr);*/
	     
	        
	        
	       /* JSONArray jrnyDtlsJsonArr = new JSONArray();
	        briJson.put(JSON_PROP_JOURNEYDETAILS, jrnyDtlsJsonArr);*/
	        JSONArray transfersDetailsJsonArr = new JSONArray();
	        briJson.put(JSON_PROP_TRANSFERSDETAILS, transfersDetailsJsonArr);
		 	return briJson;
	}
} 
