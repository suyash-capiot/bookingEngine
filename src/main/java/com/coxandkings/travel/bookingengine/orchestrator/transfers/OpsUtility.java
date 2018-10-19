package com.coxandkings.travel.bookingengine.orchestrator.transfers;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.UserContext;

public class OpsUtility implements TransfersConstants{

	
		public static String applyCommercials(JSONObject opsRqJson) throws Exception {

			JSONObject rqJson = new JSONObject();
			JSONObject rsJson = new JSONObject();
			CommercialsOperation op = null;
			JSONObject reqHdr;

			try {
				reqHdr = opsRqJson.getJSONObject(JSON_PROP_REQHEADER);
				rqJson.put(JSON_PROP_REQHEADER, reqHdr); 
				rqJson.put(JSON_PROP_REQBODY, opsRqJson.getJSONObject(JSON_PROP_REQBODY));
				rsJson.put(JSON_PROP_RESHEADER, reqHdr); 
				rsJson.put(JSON_PROP_RESBODY, opsRqJson.getJSONObject(JSON_PROP_RESBODY));
				op = CommercialsOperation.forString(opsRqJson.getString("commercialsOperation"));
			}
			catch (Exception e) {
				throw new RequestProcessingException(e.getCause());
			}
			try {
				//Map<Integer,String> SI2BRMSRoomMap= new HashMap<Integer,String>();
				JSONObject resSupplierComJson =  SupplierCommercials.getSupplierCommercialsV2(op,rqJson, rsJson);
				JSONObject resClientComJson = ClientCommercials.getClientCommercialsV2(resSupplierComJson);

				TransfersSearchProcessor.calculatePrices(rqJson, rsJson, resSupplierComJson, resClientComJson,  true, UserContext.getUserContextForSession(reqHdr));
				
				return rsJson.toString();
			}
			catch(Exception e) {
				throw new InternalProcessingException(e.getCause()); 
			}
		}
	}


