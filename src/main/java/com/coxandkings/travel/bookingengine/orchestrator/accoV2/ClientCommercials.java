package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class ClientCommercials implements AccoConstants {
	private static final Logger logger = LogManager.getLogger(AccoSearchProcessor.class);

	public static JSONObject getClientCommercials(JSONObject reqJson,JSONObject suppCommRes) {

		//CommercialsConfig commConfig = AccoConfig.getCommercialsConfig();
		//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
		//ServiceConfig commTypeConfig = AccoConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
		ServiceConfig commTypeConfig = AccoConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_CLIENT_TRANSACTIONAL);
		JSONObject breClientReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
		JSONObject breClientReqRoot = breClientReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject(JSON_PROP_CLIENTTRANRULES);
		
		JSONObject suppCommResRoot = suppCommRes.getJSONObject(JSON_PROP_RESULT).getJSONObject(JSON_PROP_EXECUTIONRES).getJSONArray(JSON_PROP_RESULTS).getJSONObject(0).getJSONObject(JSON_PROP_VALUE).getJSONObject(JSON_PROP_SUPPTRANRULES);
		JSONObject suppCommResHdr = suppCommResRoot.getJSONObject(JSON_PROP_HEADER);
		breClientReqRoot.put(JSON_PROP_HEADER, suppCommResHdr);
		
		UserContext usrCtx = UserContext.getUserContextForSession(reqJson.getJSONObject(JSON_PROP_REQHEADER));
        
                JSONArray briJsonArr = new JSONArray();
		JSONArray suppCommResBRIArr = suppCommResRoot.getJSONArray(JSON_PROP_BUSSRULEINTAKE);
               
		//JSONArray entityDtlsJsonArr = new JSONArray();
		
		for (int i = 0; i < suppCommResBRIArr.length(); i++) {
			JSONObject currSuppCommResBRI = suppCommResBRIArr.getJSONObject(i);
		    currSuppCommResBRI.remove("ruleFlowName");
		    currSuppCommResBRI.remove("mdmruleId");
		   // currSuppCommResBRI.remove("selectedRow");
		   /* JSONObject entityDtlsJson = new JSONObject();
			entityDtlsJson.put("entityType", "Client Entity");
			entityDtlsJson.put("entityName", "CLIENTTYPE229");
			entityDtlsJson.put("entityMarket", "India");
			entityDtlsJsonArr.put(entityDtlsJson);
			currSuppCommResBRI.put(JSON_PROP_ENTITYDETAILS, entityDtlsJsonArr);
			*/
	        /*MongoCollection<Document> ccbmColl = MDMConfig.getCollection(MDM_COLL_CLIENTCOMMBUDMARGINS);
			for(int j=0;j<usrCtx.getClientCommercialsHierarchy().length();j++) {
				Map<String, Object> filters = new HashMap<String, Object>();
				
				//TODO: now we can take both market and entity type from usercontext and no need to connect to this mongo doc here
				filters.put(MDM_PROP_DELETED, false);
				filters.put(MDM_PROP_ENTITYID, usrCtx.getClientCommercialsHierarchy().getJSONObject(j).getString("entityName"));
				Document temp = UserContext.getLatestUpdatedDocument(ccbmColl.find(new Document(filters)));
				JSONObject entityDtlsJson = new JSONObject();
				if(temp!=null) {
				Object t1 = temp.get("budgetMarginAttachedTo");
				 entityDtlsJson.put("entityType", ((Document) t1).getString("entityType"));
				//entityDtlsJson.put("entityName",((Document) t1).getString("entityName"));
			    entityDtlsJson.put("entityName",usrCtx.getClientCommercialsHierarchy().getJSONObject(j).getString("entityName"));
				entityDtlsJson.put("entityMarket", ((Document) t1).getString("companyMarket"));
				entityDtlsJsonArr.put(entityDtlsJson);
				currSuppCommResBRI.put(JSON_PROP_ENTITYDETAILS, entityDtlsJsonArr);
				}
				else {
					currSuppCommResBRI.put(JSON_PROP_ENTITYDETAILS,  usrCtx.getClientCommercialsHierarchy());
				}
				
				
			}*/
			currSuppCommResBRI.put(JSON_PROP_ENTITYDETAILS,  usrCtx.getClientCommercialsHierarchy());
			
			//Ops Amendments Block
			if(reqJson.getJSONObject(JSON_PROP_REQBODY).has("opsAmendments") && reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONObject("opsAmendments")
					.getString("actionItem").equalsIgnoreCase("amendEntityCommercials")) {
				JSONObject opsAmendmentJson=reqJson.getJSONObject("requestBody").getJSONObject("opsAmendments");
				if(currSuppCommResBRI.getJSONObject(JSON_PROP_COMMONELEMS).getString(JSON_PROP_SUPP).equals(opsAmendmentJson.getString("supplierId"))) {
					JSONArray hotelDetailsJsonArr=currSuppCommResBRI.getJSONArray(JSON_PROP_HOTELDETAILS);
					for(int hotelIdx=0;hotelIdx<hotelDetailsJsonArr.length();hotelIdx++) {
						JSONObject hotelDetailsJson=hotelDetailsJsonArr.getJSONObject(hotelIdx);
						if(hotelDetailsJson.getString("productName").equals(opsAmendmentJson.getString("hotelName"))) {
							JSONArray roomDetailsJsonArr=hotelDetailsJson.getJSONArray(JSON_PROP_ROOMDETAILS);
							for(int roomIdx=0;roomIdx<roomDetailsJsonArr.length();roomIdx++) {
								JSONObject roomDetailsJson=roomDetailsJsonArr.getJSONObject(roomIdx);
								if(roomDetailsJson.get("roomType").equals(opsAmendmentJson.getString(JSON_PROP_ROOMTYPENAME))&&
								   roomDetailsJson.get("roomCategory").equals(opsAmendmentJson.getString(JSON_PROP_ROOMCATEGNAME))&&
								   roomDetailsJson.get("rateType").equals(opsAmendmentJson.getString(JSON_PROP_RATEPLANNAME))&&
								   roomDetailsJson.get("rateCode").equals(opsAmendmentJson.getString(JSON_PROP_RATEPLANCODE))) {
								roomDetailsJson.put("bookingId", opsAmendmentJson.getString("bookingId"));
								roomDetailsJson.put("ineligibleCommercials", opsAmendmentJson.getJSONArray("ineligibleCommercials"));
								}
							}
						}
					}
					
				}
			}

			briJsonArr.put(currSuppCommResBRI);

		}
                breClientReqRoot.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
		
		breClientReqRoot.put("businessRuleIntake", briJsonArr);
		JSONObject breClientResJson = null;
		try {
			//long start = System.currentTimeMillis();
			//breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breClientReqJson);
			breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breClientReqJson);
			//logger.info(String.format("Time taken to get client commercials response : %s ms", System.currentTimeMillis()-start));
		} catch (Exception x) {
			logger.warn("An exception occurred when calling client commercials", x);
		}
		return breClientResJson;
	}

}
