package com.coxandkings.travel.bookingengine.orchestrator.air;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class ClientCommercials implements AirConstants {

    private static Logger logger = LogManager.getLogger(ClientCommercials.class);

    public static JSONObject getClientCommercialsV2(JSONObject reqJson, JSONObject resJson, JSONObject suppCommRes) {
        //CommercialsConfig commConfig = AirConfig.getCommercialsConfig();
        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
    	//ServiceConfig commTypeConfig = AirConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
    	ServiceConfig commTypeConfig = AirConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_CLIENT_TRANSACTIONAL);
        JSONObject breClientReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
        JSONObject breClientReqRoot = breClientReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.air_commercialscalculationengine.clienttransactionalrules.Root"); 
        
        JSONObject suppCommResRoot = suppCommRes.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.air_commercialscalculationengine.suppliertransactionalrules.Root");
        JSONObject suppCommResHdr = suppCommResRoot.getJSONObject(JSON_PROP_HEADER);
        breClientReqRoot.put(JSON_PROP_HEADER, suppCommResHdr);
        
        JSONObject reqHeaderJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHeaderJson);
        
        JSONArray briJsonArr = new JSONArray();
        JSONArray suppCommResBRIArr = suppCommResRoot.getJSONArray(JSON_PROP_BUSSRULEINTAKE);
        
        for (int i=0; i < suppCommResBRIArr.length(); i++) {
        	
        	JSONObject suppCommResBRI = suppCommResBRIArr.getJSONObject(i);
        	suppCommResBRI.remove("ruleFlowName");
        	//suppCommResBRI.remove("selectedRow");
        	
        	//suppCommResBRI.remove("commercialHead");
        	
	     suppCommResBRI.put(JSON_PROP_ENTITYDETAILS, usrCtx.getClientCommercialsHierarchy());
	     	
	     //Ops Amendments Block
			if(reqJson.getJSONObject(JSON_PROP_REQBODY).has("opsAmendments")/* && reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONObject("opsAmendments")
					.getString("actionItem").equalsIgnoreCase("amendEntityCommercials")*/) {
				JSONObject opsAmendmentJson=reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONObject("opsAmendments");
				if(suppCommResBRI.getJSONObject(JSON_PROP_COMMONELEMS).getString(JSON_PROP_SUPP).equals(opsAmendmentJson.getString("supplierId"))) {
					JSONObject journeyDetailsJson=suppCommResBRI.getJSONArray(JSON_PROP_JOURNEYDETAILS).getJSONObject(opsAmendmentJson.getInt("journeyDetailsIdx"));
					JSONArray psgrDtlsJsonArr =journeyDetailsJson.getJSONArray(JSON_PROP_PSGRDETAILS);
					for(int idx=0;idx<psgrDtlsJsonArr.length();idx++) {
						JSONObject psgrDtlsJson = psgrDtlsJsonArr.getJSONObject(idx);
						if(psgrDtlsJson.getString(JSON_PROP_PSGRTYPE).equals(opsAmendmentJson.getString(JSON_PROP_PAXTYPE))) {
							psgrDtlsJson.put("bookingId", opsAmendmentJson.getString("bookingId"));
							psgrDtlsJson.put("ineligibleCommercials", opsAmendmentJson.getJSONArray("ineligibleCommercials"));	
						}
						
					}
				}
			}
	        briJsonArr.put(suppCommResBRI);
        }
        breClientReqRoot.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
        
		JSONObject breClientResJson = null;
		try {
			breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breClientReqJson);			
		}
		catch (Exception x) {
			logger.warn("An exception occurred when calling supplier commercials", x);
		}
		
        return breClientResJson;
    }
    
}
