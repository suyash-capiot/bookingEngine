package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import java.text.ParseException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.cruise.CruiseConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class CruiseClientCommercials implements CruiseConstants{

	private static Logger logger = LogManager.getLogger(CruiseClientCommercials.class);
	
	 public static JSONObject getClientCommercialsV1(JSONObject parentSupplTransJsonOG) throws JSONException, ParseException {
	        //CommercialsConfig commConfig = CruiseConfig.getCommercialsConfig();
	        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
		 	ServiceConfig commTypeConfig = CruiseConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_CLIENT_TRANSACTIONAL);
	        JSONObject breClientReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
	        JSONObject breClientReqRoot = breClientReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.cruise_commercialscalculationengine.clienttransactionalrules.Root"); 
	        
	        JSONObject parentSupplTransJson = new JSONObject(parentSupplTransJsonOG.toString());
	        JSONObject bReResSupplierJson =parentSupplTransJson.getJSONObject(PRODUCT_CRUISE_SUPPLTRANSRS);
	        
	        JSONObject suppCommResRoot = bReResSupplierJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.cruise_commercialscalculationengine.suppliertransactionalrules.Root");
	        JSONObject suppCommResHdr = suppCommResRoot.getJSONObject("header");
	        breClientReqRoot.put("header", suppCommResHdr);
	        
	        JSONArray briJsonArr = new JSONArray();
	        JSONArray suppCommResBRIArr = suppCommResRoot.getJSONArray("businessRuleIntake");
	        for (int i=0; i < suppCommResBRIArr.length(); i++) {
	        	JSONObject suppCommResBRI = suppCommResBRIArr.getJSONObject(i);
	        	
		        // TODO: Following entity details have been hard-coded. These should be retrieved from user context and then added here.
		        JSONArray entityDtlsJsonArr = new JSONArray();
		        JSONObject entityDtlsJson = new JSONObject();
		        entityDtlsJson.put("entityType", "ClientSpecific");
		        entityDtlsJson.put("entityName", "AkbarTravels");
		        entityDtlsJson.put("entityMarket", "India");
		        entityDtlsJson.put("parentEntityName", "CnKB2BIndEng");
		        JSONObject entityDtlsJson1 = new JSONObject();
		        entityDtlsJson1.put("entityType", "ClientType");
		        entityDtlsJson1.put("entityName", "CnKB2BIndEng");
		        entityDtlsJson1.put("entityMarket", "India");
		        entityDtlsJsonArr.put(entityDtlsJson);
		        entityDtlsJsonArr.put(entityDtlsJson1);
		        suppCommResBRI.put("entityDetails", entityDtlsJsonArr);
		        
		        suppCommResBRI.remove("commercialHead");
		        suppCommResBRI.remove("selectedRow");
		        suppCommResBRI.remove("ruleFlowName");
		        
		        /*JSONArray cruiseDetailsArr = suppCommResBRI.getJSONArray("cruiseDetails");
		        for(int j=0;j<cruiseDetailsArr.length();j++)
		        {
	        		JSONObject cruiseDetailsJson = cruiseDetailsArr.getJSONObject(j);
	        		
	        		int cruiseNumber = cruiseDetailsJson.getInt("cruiseNumber");
	        		cruiseDetailsJson.put("cruiseNumber", String.valueOf(cruiseNumber));
	        		
	        		JSONArray cabinDetailsJsonArr =	cruiseDetailsJson.getJSONArray("cabinDetails");
	        		for(int l=0;l<cabinDetailsJsonArr.length();l++)
	        		{
	        			JSONObject cabinDetailsJson = cabinDetailsJsonArr.getJSONObject(l);
		        		JSONArray passengerDetailsArr =	cabinDetailsJson.getJSONArray("passengerDetails");
		        		
			        	for(int k=0;k<passengerDetailsArr.length();k++)	
			        	{
		        			JSONObject passengerDetailsJson = passengerDetailsArr.getJSONObject(k);
		        			
		        			int totalFare = passengerDetailsJson.getInt("totalFare");//[1]To convert it into string because BRMS request needs the value to be string
		        			passengerDetailsJson.put("totalFare", String.valueOf(totalFare));
		        			
		        			passengerDetailsJson.remove("totalPayables");
		        			passengerDetailsJson.remove("commercialsApplied");
		        			passengerDetailsJson.remove("totalReceivables");
		        			
		        			JSONObject fareBreakUpJson = passengerDetailsJson.getJSONObject("fareBreakUp");
		        			int baseFare = fareBreakUpJson.getInt("baseFare");//[1]
		        			fareBreakUpJson.put("baseFare", String.valueOf(baseFare));
			        	}
	        		}
		        }*/
		        briJsonArr.put(suppCommResBRI);
	        }
	        breClientReqRoot.put("businessRuleIntake", briJsonArr);
	        
	        System.out.println("CLIENTTRANSRQ");
	        System.out.println(breClientReqJson.toString());
	        
			JSONObject breClientResJson = null;
			try {
				//breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breClientReqJson);
				breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breClientReqJson);
			}
			catch (Exception x) {
				logger.warn("An exception occurred when calling supplier commercials", x);
			}
			
			System.out.println("CLIENTTRANSRS");
			System.out.println(breClientResJson.toString());
			
	        return breClientResJson;
	    }
}
