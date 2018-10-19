package com.coxandkings.travel.bookingengine.orchestrator.bus;

//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.bus.BusConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.Utils;

public class SupplierCommercials implements BusConstants{
	private static SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");

	public static JSONObject getSupplierCommercials(CommercialsOperation op,JSONObject reqJson, JSONObject resJson,Map<String,Integer> BRMS2SIBusMap) {
		// TODO Auto-generated method stub
		Map<String, JSONObject> bussRuleIntakeBySupp = new HashMap<String, JSONObject>();
		 //CommercialsConfig commConfig = BusConfig.getCommercialsConfig();
	        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		ServiceConfig commTypeConfig = BusConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
	        JSONObject breSuppReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));

	        JSONObject reqHeader = reqJson.getJSONObject(JSON_PROP_REQHEADER);
	        JSONObject reqBody = reqJson.getJSONObject(JSON_PROP_REQBODY);
	        JSONObject clntCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
	        
	        JSONObject resHeader = resJson.getJSONObject(JSON_PROP_RESHEADER);
	        JSONObject resBody = resJson.getJSONObject(JSON_PROP_RESBODY);

	        JSONObject breHdrJson = new JSONObject();
	        breHdrJson.put("sessionID", resHeader.getString(JSON_PROP_SESSIONID));
	        breHdrJson.put("transactionID", resHeader.getString(JSON_PROP_TRANSACTID));
	        breHdrJson.put("userID", resHeader.getString(JSON_PROP_USERID));
	        breHdrJson.put("operationName", op.toString());

	        JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.bus_commercialscalculationengine.suppliertransactionalrules.Root");
	        rootJson.put("header", breHdrJson);
	        
	        JSONArray briJsonArr = new JSONArray();
	        JSONArray availArr = resBody.getJSONArray(JSON_PROP_AVAILABILITY);
//	        JSONArray serviceJsonJsonArr = resBody.getJSONArray(JSON_PROP_SERVICE);
	        JSONArray busServiceDetailsJsonArr = null;
	        String prevSuppId,suppId;
	        int briIndex = 0;
	        JSONObject briJson = null;
	        for (int i=0; i < availArr.length(); i++) 
	        {
		        	JSONArray serviceJsonArr = availArr.getJSONObject(i).optJSONArray(JSON_PROP_SERVICE);
		        
		        	if(serviceJsonArr!=null)
		        	{
		        		prevSuppId = DEF_SUPPID;
		        		for(int j=0;j<serviceJsonArr.length();j++)
			        	{
			        		
			        		JSONObject serviceJson = serviceJsonArr.getJSONObject(j);
			        		suppId = serviceJson.getString(JSON_PROP_SUPPREF);
			        		
			        		JSONArray faresArr = serviceJson.getJSONArray("fares");
			        		for(int k=0;k<faresArr.length();k++)
			        		{
			        			JSONObject fareJson = faresArr.getJSONObject(k);
				        		if(!prevSuppId.equals(suppId))
				        		{
				        			prevSuppId = suppId;
				        			briJson = new JSONObject();
	//			         			ABHIBUS
		//			        		 JSONObject briJson = new JSONObject();
		        			        JSONObject commonElemsJson = new JSONObject();
		        			        UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
		        			        briJson = createBusinessRuleIntakeForSupplier(reqHeader, reqBody, resHeader, resBody, serviceJson);
	
		        			        
		        			        
		        			    	
		        			        
	//TODO: uncomment
	//			        			        commonElemsJson.put("contractValidity", DATE_FORMAT.format(new Date()));
	//			        			        JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
	//			        			        commonElemsJson.put(JSON_PROP_CLIENTTYPE, clientCtx.getString(JSON_PROP_CLIENTTYPE));
	//			        			        if (usrCtx != null) {
	//			        			        	commonElemsJson.put(JSON_PROP_CLIENTNAME, (usrCtx != null) ? usrCtx.getClientName() : "");
	//			        			        	
	//			        			        	List<ClientInfo> clientHierarchyList = usrCtx.getClientHierarchy();
	//			        			        	if (clientHierarchyList != null && clientHierarchyList.size() > 0) {
	//			        			        		ClientInfo clInfo = clientHierarchyList.get(clientHierarchyList.size() - 1);
	//			        			        		if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
	//			        			        			commonElemsJson.put(JSON_PROP_CLIENTGROUP, clInfo.getCommercialsEntityId());
	//			        			        		}
	//			        			        	}
	//		        			       		        			        }
		        			        briJsonArr.put(briIndex++,briJson);
				        		}
			        			BRMS2SIBusMap.put(String.format("%d%c%d%c%d",i,'|',j,'|',k),Integer.valueOf(briIndex));
						           
			        			busServiceDetailsJsonArr = briJson.getJSONArray(JSON_PROP_BUSSERVICEDETAILS);
						        busServiceDetailsJsonArr.put(getBusServiceDetailsJson(fareJson,reqBody));
						           
			        		}
			        	}
		        	}
	         }
	        
	        Iterator<Map.Entry<String, JSONObject>> briEntryIter = bussRuleIntakeBySupp.entrySet().iterator();
	        while (briEntryIter.hasNext()) 
	        {
	        	Map.Entry<String, JSONObject> briEntry = briEntryIter.next();
	        	briJsonArr.put(briEntry.getValue());
	        }
            
	        rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
//	        PrintWriter pw2;
//			try {
//				pw2 = new PrintWriter(new File("D:\\BE\\temp\\suppcommreqgenJson.txt"));
//				pw2.write(breSuppReqJson.toString());
//				pw2.close();
//			} catch (FileNotFoundException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			
	        JSONObject breSuppResJson = null;
	        try {
	            //breSuppResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breSuppReqJson);
	        	breSuppResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breSuppReqJson);
	        }
	        catch (Exception x) {
	           // logger.warn("An exception occurred when calling supplier commercials", x);
	        	x.printStackTrace();
	        }

	        return breSuppResJson;

	}

	private static JSONObject getBusServiceDetailsJson(JSONObject serviceJson,JSONObject reqBody) {
		
				// ABHIBUS
		  JSONObject serviceDetails = new JSONObject();
//		serviceDetails.put("routeId", serviceJson.get("routeId"));
		serviceDetails.put("routeId", 402);
//		serviceDetails.put("operatorId", serviceJson.get("operatorId"));
		//serviceDetails.put("busType", getBusType(serviceJson));
		serviceDetails.put("busType", "AC_Sleeper");
//		serviceDetails.put("slabDetails", getSlabDetailsJsonArr(serviceJson));
		//remaining values are hardcoded
		serviceDetails.put("operatorCountry", "India");
		serviceDetails.put("operatorId", 1234);
//		serviceDetails.putOnce("operatorName", "null");
		
		
//		DATA FROM RADIS
//		String source = reqBody.getString(JSON_PROP_SOURCE);
//    	Map<String,Object> cityInfo = RedisCityData.getCityInfo(source);
//    	serviceDetails.put(JSON_PROP_FROMCITY, cityInfo.getOrDefault("value", ""));
//    	serviceDetails.put(JSON_PROP_FROMCOUNTRY, cityInfo.getOrDefault("country", ""));
//    	serviceDetails.put(JSON_PROP_FROMCONTINENT, cityInfo.getOrDefault("continent", ""));
//    	serviceDetails.put(JSON_PROP_FROMSTATE, cityInfo.getOrDefault("state", ""));
//
//    	String destination = reqBody.getString(JSON_PROP_SOURCE);
//    	 cityInfo = RedisCityData.getCityInfo(destination);
//    	serviceDetails.put(JSON_PROP_TOCITY, cityInfo.getOrDefault("value", ""));
//    	serviceDetails.put(JSON_PROP_TOCOUNTRY, cityInfo.getOrDefault("country", ""));
//    	serviceDetails.put(JSON_PROP_TOCONTINENT, cityInfo.getOrDefault("continent", ""));
//    	serviceDetails.put(JSON_PROP_TOSTATE, cityInfo.getOrDefault("toState", ""));
    	
		serviceDetails.put("fromContinent", "Asia");
		serviceDetails.put("fromCountry", "India");
		serviceDetails.put("fromCity", "Mumbai");
		serviceDetails.put("fromState", "Maharashtra");
		serviceDetails.put("toContinent", "Asia");
		serviceDetails.put("toCountry", "India");
		serviceDetails.put("toCity", "Nasik");
		serviceDetails.put("toState", "Maharashtra");
		
//		serviceDetails.put("clientNationality", "Indian");
		serviceDetails.put("passengerDetails",getPassangerDetails(serviceJson));
		
		return serviceDetails;
		
		//			REDBUS
		
		/*JSONObject serviceDetails = new JSONObject();
//		serviceDetails.put("routeId", serviceJson.get("routeId"));
		serviceDetails.put("routeId", 302);
//		serviceDetails.put("operatorId", serviceJson.get("operatorId"));
		//serviceDetails.put("busType", getBusType(serviceJson));
		serviceDetails.put("busType", "AC_Sleeper");
//		serviceDetails.put("slabDetails", getSlabDetailsJsonArr(serviceJson));
		//remaining values are hardcoded
		serviceDetails.put("operatorCountry", "India");
		serviceDetails.put("operatorId", 7094);
		serviceDetails.putOnce("operatorName", JSONObject.NULL);
		serviceDetails.put("fromContinent", "Asia");
		serviceDetails.put("fromCountry", "India");
		serviceDetails.put("fromCity", "Mumbai");
		serviceDetails.put("fromState", "Maharashtra");
		serviceDetails.put("toContinent", "Asia");
		serviceDetails.put("toCountry", "India");
		serviceDetails.put("toCity", "Pune");
		serviceDetails.put("toState", "Maharashtra");
		serviceDetails.put("clientNationality", "Indian");
		serviceDetails.put("passengerDetails",getPassangerDetails(serviceJson));
		return serviceDetails;*/
		
	}

	private static JSONArray getSlabDetailsJsonArr(JSONObject serviceJson) {
		
			JSONArray slabDetailsArr = new JSONArray();
		    JSONObject slabDetail = new JSONObject();
			slabDetail.put("slabType", "NoOfSeats");
			slabDetail.put("slabTypeValue", serviceJson.get("availableSeats"));
			slabDetailsArr.put(slabDetail);
		return slabDetailsArr;
	}

	private static JSONArray getPassangerDetails(JSONObject serviceJson) {
		//TODO: passtype , farebrekeup not available
		// multiple totalfares 
		
//		JSONArray psgdetails = new JSONArray();
//		JSONObject details = new JSONObject();
//		JSONArray passArr = new JSONArray();
//		passArr = serviceJson.getJSONArray("fares");
//		double avg = 0;
//		for(int i = 0;i<passArr.length();i++)
//		{
//		   JSONObject passanger = (JSONObject)	passArr.get(i);
//		    avg=avg +Double.valueOf(passanger.get("fare").toString());
//		}
//		avg/=passArr.length();
//		details.put("totalFare", avg);
//		details.put("passengerType", "ADT");
//		JSONObject fareBrkup = new JSONObject();
//		fareBrkup.put("baseFare", 10);
//		JSONArray farebrArr = new JSONArray();
//		JSONObject stax = new JSONObject();
//		stax.put("taxName", "ServiceTax");
//		stax.put("taxValue", avg-10);
//		farebrArr.put(stax);
//		fareBrkup.put("taxDetails", farebrArr);
//		details.put("fareBreakUp", fareBrkup);
//		psgdetails.put(details);
		
		JSONArray psgdetails = new JSONArray();
		JSONObject details = new JSONObject();
		details.put("totalFare", Utils.convertToBigDecimal(serviceJson.get("fare").toString(), 0));
		psgdetails.put(details);
		return psgdetails;
	}

	private static String getBusType(JSONObject serviceJson) {
		
		if(serviceJson.get("AC").equals("true") && serviceJson.get("sleeper").equals("true"))
    {
	      return "AC_Sleeper";
    }
		else if(serviceJson.get("AC").equals("true") && serviceJson.get("Seater").equals("true"))
          return "AC_Seater";
		else if(serviceJson.get("AC").equals("false") && serviceJson.get("Seater").equals("true"))
	      return "NAC_Seater";
		else if(serviceJson.get("AC").equals("false") && serviceJson.get("sleeper").equals("false"))
	      return "NAC_Sleeper";
		
		else
			return "";
	}

	private static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqBody,
			JSONObject resHeader, JSONObject resBody, JSONObject serviceJson) {
		
		 // 			ABHIBUS
	  JSONObject briJson = new JSONObject();
        JSONObject commonElemsJson = new JSONObject();
        UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
      
        JSONObject clntCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
        //commonElements
        String suppID = serviceJson.getString(JSON_PROP_SUPPREF);
        String sid = suppID.substring(0, 1).toUpperCase() + suppID.substring(1).toLowerCase();
        commonElemsJson.put("supplier", sid);
        //remaining all values are hardcoded
        commonElemsJson.put("supplierMarket", "India");
        commonElemsJson.put("productCategory", "Transportation");
        commonElemsJson.put("productCategorySubType", "Bus");
        commonElemsJson.put("productName", "Bus");
        commonElemsJson.put("contractValidity", "2017-02-10T00:00:00+05:30");
        commonElemsJson.put("clientType", "B2B");
        commonElemsJson.put("clientGroup", "TravelAgent");
        commonElemsJson.put("clientName", "ABC");
        
        //TODO: uncomment
//        commonElemsJson.put(JSON_PROP_SUPP, suppID);
//        commonElemsJson.put(JSON_PROP_SUPPMARKET, clntCtx.getString(JSON_PROP_CLIENTMARKET));
//         commonElemsJson.put("productCategorySubType", "Bus");
//        commonElemsJson.put(JSON_PROP_CLIENTTYPE, clntCtx.getString(JSON_PROP_CLIENTTYPE));
//         commonElemsJson.put("contractValidity", DATE_FORMAT.format(new Date()));
//        JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
//        commonElemsJson.put(JSON_PROP_CLIENTTYPE, clientCtx.getString(JSON_PROP_CLIENTTYPE));
//        if (usrCtx != null) {
//        	commonElemsJson.put(JSON_PROP_CLIENTNAME, (usrCtx != null) ? usrCtx.getClientName() : "");
//        	
//        	List<ClientInfo> clientHierarchyList = usrCtx.getClientHierarchy();
//        	if (clientHierarchyList != null && clientHierarchyList.size() > 0) {
//        		ClientInfo clInfo = clientHierarchyList.get(clientHierarchyList.size() - 1);
//        		if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
//        			commonElemsJson.put(JSON_PROP_CLIENTGROUP, clInfo.getCommercialsEntityId());
//        		}
//        	}
//        }
        
        
        briJson.put("commonElements", commonElemsJson);
        
        
        //advancedDefinition
        JSONObject advDefnJson = new JSONObject();
        advDefnJson.put("ticketingDate", "2017-03-10T00:00:00+05:30");
        // TODO: remaining values are hardcoded
        advDefnJson.put("travelDate", "2017-04-10T00:00:00+05:30");
        advDefnJson.put("travelType", "SITO"); //abhibus
//        advDefnJson.put("travelType", "SOTO"); //mantis
        advDefnJson.put("salesDate", "2017-03-12T00:00:00");
        advDefnJson.put("journeyType", "OneWay");
        advDefnJson.put("clientNationality", "Indian");
        advDefnJson.put("connectivitySupplier",JSONObject.NULL);
        advDefnJson.put("connectivitySupplierType", JSONObject.NULL);
        advDefnJson.put("travelProductName", "Bus");
        advDefnJson.put("credentialsName", "Abhibus");
        advDefnJson.put("bookingType", "Online");
        briJson.put("advancedDefinition", advDefnJson);
       
       
        JSONArray busServiceDtlsJsonArr = new JSONArray();
        briJson.put(JSON_PROP_BUSSERVICEDETAILS, busServiceDtlsJsonArr);
        return briJson;
		
		
		//			REDBUS
		
		/*JSONObject briJson = new JSONObject();
        JSONObject commonElemsJson = new JSONObject();
        //commonElements
        String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
        String sid = suppID.substring(0, 1).toUpperCase() + suppID.substring(1).toLowerCase();
        commonElemsJson.put("supplier", sid);
        //remaining all values are hardcoded
        commonElemsJson.put("supplierMarket", "India");
        commonElemsJson.put("productCategory", "Transportation");
        commonElemsJson.put("productCategorySubType", "Bus");
        commonElemsJson.put("productName", "Bus");
        commonElemsJson.put("contractValidity", "2017-02-10T00:00:00+05:30");
        commonElemsJson.put("clientType", "B2B");
        commonElemsJson.put("clientGroup", "TravelAgent");
        commonElemsJson.put("clientName", "ABC");
        briJson.put("commonElements", commonElemsJson);
        
        
        //advancedDefinition
        JSONObject advDefnJson = new JSONObject();
        advDefnJson.put("ticketingDate", "2017-02-10T00:00:00+05:30");
        // TODO: remaining values are hardcoded
        advDefnJson.put("travelDate", "2017-06-25T00:00:00+05:30");
        advDefnJson.put("travelType", "SOTO");
        advDefnJson.put("salesDate", "2017-03-12T00:00:00");
        advDefnJson.put("journeyType", "OneWay");
        advDefnJson.put("clientNationality", "Indian");
       
        advDefnJson.put("connectivitySupplier","Redbus");
        advDefnJson.put("connectivitySupplierType", "LCC");
        advDefnJson.put("travelProductName", "Bus");
        advDefnJson.put("credentialsName", "Redbus");
        advDefnJson.put("bookingType", "Online");
        briJson.put("advancedDefinition", advDefnJson);
        JSONArray busServiceDtlsJsonArr = new JSONArray();
        briJson.put(JSON_PROP_BUSSERVICEDETAILS, busServiceDtlsJsonArr);
        return briJson;*/
	}

}
