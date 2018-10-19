package com.coxandkings.travel.bookingengine.orchestrator.bus;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.bus.BusConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class ClientCommercials implements BusConstants {

	public static JSONObject getClientCommercials(JSONObject reqJson,JSONObject resSupplierJson) {
		
		//CommercialsConfig commConfig = BusConfig.getCommercialsConfig();
		//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_CLIENT_TRANSACTIONAL);
		ServiceConfig commTypeConfig = BusConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_CLIENT_TRANSACTIONAL);
		JSONObject breClientReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
		JSONObject breClientReqRoot = breClientReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.bus_commercialscalculationengine.clienttransactionalrules.Root");
		
		JSONObject suppCommResRoot = resSupplierJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.bus_commercialscalculationengine.suppliertransactionalrules.Root");
        JSONObject suppCommResHdr = suppCommResRoot.getJSONObject("header");
        breClientReqRoot.put("header", suppCommResHdr);
        
        UserContext usrCtx = UserContext.getUserContextForSession(reqJson.getJSONObject(JSON_PROP_REQHEADER));
        
        JSONArray briJsonArr = new JSONArray();
        JSONArray suppCommResBRIArr = suppCommResRoot.getJSONArray("businessRuleIntake");
        
        for (int i=0; i < suppCommResBRIArr.length(); i++) {
        	JSONObject suppCommResBRI = suppCommResBRIArr.getJSONObject(i);

	        // TODO: Following entity details have been hard-coded. These should be retrieved from client context and then added here.
	        JSONArray entityDtlsJsonArr = new JSONArray();
	        JSONObject entityDtlsJson = new JSONObject();
	        entityDtlsJson.put("entityType", "ClientType");
	        entityDtlsJson.put("entityName", "CnKB2BIndEng");
	        entityDtlsJson.put("entityMarket", "India");
	        entityDtlsJsonArr.put(entityDtlsJson);
//	        MongoCollection<Document> ccbmColl = MDMConfig.getCollection(MDM_COLL_CLIENTCOMMBUDMARGINS);
//			for(int j=0;j<usrCtx.getClientCommercialsHierarchy().length();j++) {
//				Map<String, Object> filters = new HashMap<String, Object>();
//				filters.put(MDM_PROP_DELETED, false);
//				filters.put(MDM_PROP_ENTITYID, usrCtx.getClientCommercialsHierarchy().getJSONObject(j).getString("entityName"));
//				
//				//TODO: now we can take both market and entity type from UserContext and no need to connect to this mongo doc here
//				Document temp = UserContext.getLatestUpdatedDocument(ccbmColl.find(new Document(filters)));
//				Object t1 = temp.get("budgetMarginAttachedTo");
//				
//				JSONObject entityDtlsJson = new JSONObject();
//			    entityDtlsJson.put("entityType", ((Document) t1).getString("entityType"));
//				//entityDtlsJson.put("entityName",((Document) t1).getString("entityName"));
//			    entityDtlsJson.put("entityName",usrCtx.getClientCommercialsHierarchy().getJSONObject(j).getString("entityName"));
//				entityDtlsJson.put("entityMarket", ((Document) t1).getString("companyMarket"));
//				entityDtlsJsonArr.put(entityDtlsJson);
//				suppCommResBRI.put(JSON_PROP_ENTITYDETAILS, entityDtlsJsonArr);
//			}
//	        suppCommResBRI.put(JSON_PROP_ENTITYDETAILS,  usrCtx.getClientCommercialsHierarchy());
	        suppCommResBRI.put("entityDetails", entityDtlsJsonArr);
	        suppCommResBRI.remove("commercialHead");
	        suppCommResBRI.remove("ruleFlowName");
	        suppCommResBRI.remove("selectedRow");
	        suppCommResBRI.getJSONObject("commonElements").put("rateCode", "RT1");
	        suppCommResBRI.getJSONObject("commonElements").put("rateType", "RC1");
	        briJsonArr.put(suppCommResBRI);
        }
        breClientReqRoot.put("businessRuleIntake", briJsonArr);
       
//        PrintWriter pw2;
//		try {
//			pw2 = new PrintWriter(new File("D:\\BE\\temp\\clientcommreqgenJson.txt"));
//			pw2.write(breClientReqJson.toString());
//			pw2.close();
//		} catch (FileNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
        JSONObject breClientResJson = null;
		try {
			//breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(),"POST", 60000, breClientReqJson);
			breClientResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_CLIENTCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(),"POST", 60000, breClientReqJson);
		}
		catch (Exception x) {
			//logger.warn("An exception occurred when calling supplier commercials", x);
			x.printStackTrace();
		}
		
        return breClientResJson;

	}

}
