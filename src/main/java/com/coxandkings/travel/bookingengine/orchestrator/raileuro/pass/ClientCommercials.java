package com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.raileuro.RailEuroConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.RailEuropeConstants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;


public class ClientCommercials implements RailEuropeConstants{
	
	private static Logger logger = LogManager.getLogger(ClientCommercials.class);
	public static JSONObject getClientCommercials(JSONObject reqJson, JSONObject resJson, JSONObject suppCommRes)
	{
		//CommercialsConfig commConfig = RailEuroConfig.getCommercialsConfig();
		//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
		ServiceConfig commTypeConfig = RailEuroConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_CLIENT_TRANSACTIONAL);
        JSONObject breClientReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
        JSONObject breClientReqRoot = breClientReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.rail_commercialscalculationengine.clienttransactionalrules.Root");
        
        JSONObject suppCommResRoot = suppCommRes.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.rail_commercialscalculationengine.suppliertransactionalrules.Root");
        JSONObject suppCommResHdr = suppCommResRoot.getJSONObject(JSON_PROP_HEADER);
        breClientReqRoot.put(JSON_PROP_HEADER, suppCommResHdr);
        
//        JSONObject reqHeaderJson = reqJson.getJSONObject("requestHeader");
        
        JSONArray briJsonArr = new JSONArray();
        JSONArray suppCommResBRIArr = suppCommResRoot.getJSONArray(JSON_PROP_BUSSRULEINTAKE);
//        System.out.println(suppCommResBRIArr);
        for (int i=0; i < suppCommResBRIArr.length(); i++) {
        	JSONObject suppCommResBRI = suppCommResBRIArr.getJSONObject(i);
        	JSONObject advDefnJson = suppCommResBRI.getJSONObject(JSON_PROP_ADVANCEDDEF);
        	advDefnJson.put(JSON_PROP_ANCILLARYNAME, "AN");
        	advDefnJson.put(JSON_PROP_ANCILLARYTYPE, "AT");
        	advDefnJson.put(JSON_PROP_APPLICABLEON, "");
        	
//        	JSONObject commElemJson = suppCommResBRI.getJSONObject("commonElements");
        	
  	      /*  suppCommResBRI.put(JSON_PROP_ENTITYDETAILS, usrCtx.getClientCommercialsHierarchy());
  	        briJsonArr.put(suppCommResBRI);*/
	        // TODO: Following entity details have been hard-coded. These should be retrieved from client context and then added here.
	        JSONArray entityDtlsJsonArr = new JSONArray();
	        JSONObject entityDtlsJson = new JSONObject();
	        entityDtlsJson.put(JSON_PROP_ENTITYTYPE, "ClientType");
	        entityDtlsJson.put(MDM_PROP_ENTITYNAME, "CnKB2BIndEng");
	        entityDtlsJson.put(JSON_PROP_ENTITYMARKET, "EUROPE");
	        entityDtlsJsonArr.put(entityDtlsJson);
	        suppCommResBRI.put(JSON_PROP_ENTITYDETAILS, entityDtlsJsonArr);

	        suppCommResBRI.remove("ruleFlowName");
	        briJsonArr.put(suppCommResBRI);
        }
        breClientReqRoot.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
//        System.out.println("ClientCommercialRequestCCE" + breClientReqJson);
		JSONObject breClientResJson = null;
		try {
//			breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breClientReqJson);	
//			breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), "POST", 120000, breClientReqJson);
			breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), "POST", 120000, breClientReqJson);
//			System.out.println("BRE RESPONSE" + breClientResJson);
		}
		catch (Exception x) {
			logger.warn("An exception occurred when calling supplier commercials", x);
		}
//        System.out.println("ClientCommercialResponseCCE" + breClientResJson.toString());
		return breClientResJson;
		
	}

}
