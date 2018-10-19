package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.UserContext;

public class OpsUtility implements HolidayConstants{
	public static String applyCommercials(JSONObject opsRqJson) throws Exception {

		JSONObject rqJson = new JSONObject();
		JSONObject rsJson = new JSONObject();
		CommercialsOperation op = null;UserContext usrCtx=null;

		try {
			JSONObject reqHdr = opsRqJson.getJSONObject(JSON_PROP_REQHEADER);
			rqJson.put(JSON_PROP_REQHEADER, reqHdr); 
			rqJson.put(JSON_PROP_REQBODY, opsRqJson.getJSONObject(JSON_PROP_REQBODY));
			rsJson.put(JSON_PROP_RESHEADER, reqHdr); 
			rsJson.put(JSON_PROP_RESBODY, opsRqJson.getJSONObject(JSON_PROP_RESBODY));
			op = CommercialsOperation.forString(opsRqJson.getString("commercialsOperation"));
			usrCtx = UserContext.getUserContextForSession(reqHdr);
		}
		catch (Exception e) {
			throw new RequestProcessingException(e.getCause());
		}
		try {
			
			JSONObject resSupplierComJson =  HolidaysSupplierCommercials.getSupplierCommercials(rqJson, rsJson, op);
			JSONObject resClientComJson = HolidaysClientCommercials.getClientCommercials(rqJson, resSupplierComJson);
			
			if(op.toString().equalsIgnoreCase("Search"))
				HolidaysSearchProcessor.calculatePricesV2(rqJson, rsJson, resSupplierComJson, resClientComJson, usrCtx);
			
			if(op.toString().equalsIgnoreCase("Reprice")||op.toString().equalsIgnoreCase("Book")) {
				JSONObject transformedRequestJSON = HolidaysBookProcessor.requestJSONTransformation(rqJson);
				HolidaysRepriceProcessorV2.calculatePricesV2(transformedRequestJSON, rsJson, resSupplierComJson, resClientComJson, true, usrCtx);
			}
			return rsJson.toString();
		}
		catch(Exception e) {
			e.printStackTrace();
			throw new InternalProcessingException(e.getCause()); 
		}
	}
}
