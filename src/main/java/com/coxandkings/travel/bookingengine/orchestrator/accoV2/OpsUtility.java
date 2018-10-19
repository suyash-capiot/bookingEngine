package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;

public class OpsUtility implements AccoConstants{

	public static String applyCommercials(JSONObject opsRqJson) throws Exception {

		JSONObject rqJson = new JSONObject();
		JSONObject rsJson = new JSONObject();
		CommercialsOperation op = null;

		try {
			JSONObject reqHdr = opsRqJson.getJSONObject(JSON_PROP_REQHEADER);
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
			Map<Integer,String> SI2BRMSRoomMap= new HashMap<Integer,String>();
			JSONObject resSupplierComJson =  SupplierCommercials.getSupplierCommercials(op,rqJson, rsJson, SI2BRMSRoomMap);
			JSONObject resClientComJson = ClientCommercials.getClientCommercials(rqJson,resSupplierComJson);

			AccoSearchProcessor.calculatePrices(rqJson,rsJson,resClientComJson,resSupplierComJson,SI2BRMSRoomMap,true);
			TaxEngine.getCompanyTaxes(rqJson, rsJson);
			
			return rsJson.toString();
		}
		catch(Exception e) {
			throw new InternalProcessingException(e.getCause()); 
		}
	}
}
