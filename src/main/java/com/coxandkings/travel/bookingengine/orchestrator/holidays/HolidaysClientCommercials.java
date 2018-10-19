package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class HolidaysClientCommercials implements HolidayConstants {
	private static final Logger logger = LogManager.getLogger(HolidaysClientCommercials.class);
	public static JSONObject getClientCommercials(JSONObject reqJson, JSONObject supplierCommRes) {
		
		JSONObject clientReqShell = null ;

			//CommercialsConfig commConfig = HolidaysConfig.getCommercialsConfig();
			//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
			ServiceConfig commTypeConfig = HolidaysConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_CLIENT_TRANSACTIONAL);

			 clientReqShell = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
			JSONObject clientReqRoot = clientReqShell.getJSONArray("commands").getJSONObject(0)
					.getJSONObject("insert").getJSONObject("object")
					.getJSONObject("cnk.holidays_commercialscalculationengine.clienttransactionalrules.Root");

			JSONObject supplierCommercialResponseRoot = supplierCommRes.getJSONObject("result").getJSONObject("execution-results")
					.getJSONArray("results").getJSONObject(0).getJSONObject("value")
					.getJSONObject("cnk.holidays_commercialscalculationengine.suppliertransactionalrules.Root");

			JSONObject supplierCommercialResponseHeader = supplierCommercialResponseRoot.getJSONObject("header");
			
			UserContext usrCtx = UserContext.getUserContextForSession(reqJson.getJSONObject(JSON_PROP_REQHEADER));
			
			//--- Started
			 JSONArray briJsonArr = new JSONArray();
			JSONArray suppCommResBRIArr = supplierCommercialResponseRoot.getJSONArray("businessRuleIntake");
			for (int i=0; i < suppCommResBRIArr.length(); i++) {
	        	JSONObject supplierCommercialResponseBRI = suppCommResBRIArr.getJSONObject(i);
	        	supplierCommercialResponseBRI.remove("ruleFlowName");
	           	
	        	//supplierCommercialResponseBRI.remove("commercialHead");

				/*JSONArray entityDtlsJsonArr = new JSONArray();
				JSONObject entityDtlsJson = new JSONObject();
				//TODO: hardcoded entity details, fetched from where??
				entityDtlsJson.put("entityType", "ClientType");
				entityDtlsJson.put("entityName", "B2B");
				entityDtlsJson.put("entityMarket", "India");
				entityDtlsJsonArr.put(entityDtlsJson);
				supplierCommercialResponseBRI.put("entityDetails", entityDtlsJsonArr);*/
	        	
				supplierCommercialResponseBRI.put(JSON_PROP_ENTITYDETAILS,  usrCtx.getClientCommercialsHierarchy());

				JSONObject advancedDefinition = supplierCommercialResponseBRI.getJSONObject("advancedDefinition");
				
				//TODO: put advanced definition into request,but fetched from where??
				
				/*advancedDefinition.put("ancillaryName", "AN1");
				advancedDefinition.put("ancillaryType", "AT1");
				advancedDefinition.put("applicableOn", "AO");*/
				supplierCommercialResponseBRI.put("advancedDefinition", advancedDefinition);

				briJsonArr.put(supplierCommercialResponseBRI);
       	
			}
			clientReqRoot.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
			clientReqRoot.put("header", supplierCommercialResponseHeader);
			
			logger.info(String.format("Client commercial request: %s", clientReqShell));
			
			JSONObject breClientResJson = null;
			try {
				logger.trace(String.format("BRMS Holidays Client Commercials Request = %s", clientReqShell.toString()));
				
				//breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), clientReqShell);
				breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), clientReqShell);
		        logger.trace(String.format("BRMS Holidays Client Commercials Response = %s", breClientResJson.toString()));
		        
			}
			catch (Exception x) {
				logger.warn("An exception occurred when calling client commercials", x);
			}
			
	        return breClientResJson;
	}

}
