package com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.car.CarConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class ClientCommercials implements CarConstants{

    private static Logger logger = LogManager.getLogger(ClientCommercials.class);

    public static JSONObject getClientCommercialsV2(JSONObject reqJson, JSONObject resJson, JSONObject suppCommRes) {
        //CommercialsConfig commConfig = CarConfig.getCommercialsConfig();
        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
    	ServiceConfig commTypeConfig = CarConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_CLIENT_TRANSACTIONAL);
        JSONObject breClientReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
        JSONObject breClientReqRoot = breClientReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.carrentals_commercialscalculationengine.clienttransactionalrules.Root"); 
        
        JSONObject suppCommResRoot = suppCommRes.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.carrentals_commercialscalculationengine.suppliertransactionalrules.Root");
        JSONObject suppCommResHdr = suppCommResRoot.getJSONObject("header");
        breClientReqRoot.put(JSON_PROP_HEADER, suppCommResHdr);
        
        JSONObject reqHeaderJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHeaderJson);
        
        JSONArray briJsonArr = new JSONArray();
        JSONArray suppCommResBRIArr = suppCommResRoot.getJSONArray(JSON_PROP_BUSSRULEINTAKE);
        for (int i=0; i < suppCommResBRIArr.length(); i++) {
        	JSONObject suppCommResBRI = suppCommResBRIArr.getJSONObject(i);
        	JSONObject advDefnJson = suppCommResBRI.getJSONObject(JSON_PROP_ADVANCEDDEF);
        	advDefnJson.put("ancillaryName", "AN1");
        	advDefnJson.put("ancillaryType", "AT1");
        	advDefnJson.put("applicableOn", "AO1");
        	
        	JSONObject commElemJson = suppCommResBRI.getJSONObject(JSON_PROP_COMMONELEMS);
        	
        	commElemJson.put("rateCode", "RT1");
        	commElemJson.put("rateType", "RC1");
        	commElemJson.put("segment", "Active");
        	
  	      /*  suppCommResBRI.put(JSON_PROP_ENTITYDETAILS, usrCtx.getClientCommercialsHierarchy());
  	        briJsonArr.put(suppCommResBRI);*/
	        // TODO: Following entity details have been hard-coded. These should be retrieved from client context and then added here.
	        JSONArray entityDtlsJsonArr = new JSONArray();
	        JSONObject entityDtlsJson = new JSONObject();
	        entityDtlsJson.put("entityType", "ClientType");
	        entityDtlsJson.put("entityName", "SitaramTravels");
	        entityDtlsJson.put("entityMarket", "India");
	        entityDtlsJsonArr.put(entityDtlsJson);
	        suppCommResBRI.put(JSON_PROP_ENTITYDETAILS, entityDtlsJsonArr);

	        suppCommResBRI.remove("ruleFlowName");
	        briJsonArr.put(suppCommResBRI);
        }
        breClientReqRoot.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
		JSONObject breClientResJson = null;
		try {
			//breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breClientReqJson);
			breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breClientReqJson);
		}
		catch (Exception x) {
			logger.warn("An exception occurred when calling supplier commercials", x);
		}
        return breClientResJson;
    }
    
}
