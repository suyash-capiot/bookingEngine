package com.coxandkings.travel.bookingengine.orchestrator.activities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class ClientCommercials implements ActivityConstants {

	private static Logger logger = LogManager.getLogger(ClientCommercials.class);

	public static JSONObject getClientCommercials(JSONObject reqJson, JSONObject resJson, JSONObject suppCommRes) {
		// CommercialsConfig commConfig = ActivitiesConfig.getCommercialsConfig();
		// CommercialTypeConfig commTypeConfig =
		// commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
		ServiceConfig commTypeConfig = ActivitiesConfig
				.getCommercialTypeConfig(CommercialsType.COMMERCIAL_CLIENT_TRANSACTIONAL);
		JSONObject breClientReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
		JSONObject breClientReqRoot = breClientReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert")
				.getJSONObject("object")
				.getJSONObject("cnk.activities_commercialscalculationengine.clienttransactionalrules.Root");

		JSONObject suppCommResRoot = suppCommRes.getJSONObject("result").getJSONObject("execution-results")
				.getJSONArray("results").getJSONObject(0).getJSONObject("value")
				.getJSONObject("cnk.activities_commercialscalculationengine.suppliertransactionalrules.Root");
		JSONObject suppCommResHdr = suppCommResRoot.getJSONObject(JSON_PROP_HEADER);

		breClientReqRoot.put(JSON_PROP_HEADER, suppCommResHdr);

		JSONObject reqHeaderJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHeaderJson);

		JSONArray briJsonArr = new JSONArray();

		JSONArray suppCommResBRIArr = suppCommResRoot.getJSONArray(JSON_PROP_BUSINESS_RULE_INTAKE);

		// Following entity details have been hard-coded. These should be
		// retrieved from client context and then added here.
		// JSONArray entityDtlsJsonArr = new JSONArray();
		// JSONObject entityDtlsJson1 = new JSONObject();
		// entityDtlsJson1.put("entityType", "ClientSpecific");
		// entityDtlsJson1.put("entityName", "ABCTravels");
		// entityDtlsJson1.put("entityMarket", "India");
		//
		// JSONObject entityDtlsJson2 = new JSONObject();
		// entityDtlsJson2.put("entityType", "ClientSpecific");
		// entityDtlsJson2.put("entityName", "AkbarTravels");
		// entityDtlsJson2.put("entityMarket", "India");
		// entityDtlsJson2.put("parentEntityName", "CnKB2BIndEng");
		//
		// entityDtlsJsonArr.put(entityDtlsJson1);
		// entityDtlsJsonArr.put(entityDtlsJson2);

		for (int i = 0; i < suppCommResBRIArr.length(); i++) {

			JSONObject suppCommResBRI = suppCommResBRIArr.getJSONObject(i);
			suppCommResBRI.remove("ruleFlowName");
			// suppCommResBRI.remove("selectedRow");

			// suppCommResBRI.remove("commercialHead");

			suppCommResBRI.put(JSON_PROP_ENTITYDETAILS, usrCtx.getClientCommercialsHierarchy());

			// JSONObject suppCommResBRI = suppCommResBRIArr.getJSONObject(i);
			// suppCommResBRI.put("entityDetails", entityDtlsJsonArr);

			// //Ops Amendments Block 11-07-18
			// if(reqJson.getJSONObject(JSON_PROP_REQBODY).has("opsAmendments") &&
			// reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONObject("opsAmendments")
			// .getString("actionItem").equalsIgnoreCase("amendEntityCommercials")) {
			// JSONObject
			// opsAmendmentJson=reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONObject("opsAmendments");
			// if(suppCommResBRI.getJSONObject(JSON_PROP_COMMONELEMS).getString(JSON_PROP_SUPP).equals(opsAmendmentJson.getString("supplierId")))
			// {
			// JSONObject
			// activityDetailsJson=suppCommResBRI.getJSONArray("activityDetails").getJSONObject(opsAmendmentJson.getInt("activityDetailsIdx"));
			// JSONArray psgrDtlsJsonArr
			// =activityDetailsJson.getJSONArray(JSON_PROP_PSGRDETAILS);
			// for(int idx=0;idx<psgrDtlsJsonArr.length();idx++) {
			// JSONObject psgrDtlsJson = psgrDtlsJsonArr.getJSONObject(idx);
			// if(psgrDtlsJson.getString(JSON_PROP_PSGRTYPE).equals(opsAmendmentJson.getString(JSON_PROP_PAXTYPE)))
			// {
			// psgrDtlsJson.put("bookingId", opsAmendmentJson.getString("bookingId"));
			// psgrDtlsJson.put("ineligibleCommercials",
			// opsAmendmentJson.getJSONArray("ineligibleCommercials"));
			// }
			//
			// }
			// }
			// }

			briJsonArr.put(suppCommResBRI);
		}
		breClientReqRoot.put(JSON_PROP_BUSINESS_RULE_INTAKE, briJsonArr);

		JSONObject breClientResJson = null;
		try {
			//long start = System.currentTimeMillis();
			//breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breClientReqJson);
			breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breClientReqJson);
			//logger.info(String.format("Time taken to get client commercials response : %s ms", System.currentTimeMillis()-start));
		} catch (Exception x) {
			logger.warn("An exception occurred when calling supplier commercials", x);
		}

		return breClientResJson;
	}
}
