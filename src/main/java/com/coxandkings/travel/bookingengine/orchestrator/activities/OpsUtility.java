package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.math.BigDecimal;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;

public class OpsUtility implements ActivityConstants {

	public static String applyCommercials(JSONObject opsRqJson) throws Exception {

		JSONObject reqJson = new JSONObject();
		JSONObject resJson = new JSONObject();
		CommercialsOperation op = null;

		HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap = new HashMap<>();

		try {
			JSONObject reqHdr = opsRqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqJson.put(JSON_PROP_REQHEADER, reqHdr);
			reqJson.put(JSON_PROP_REQBODY, opsRqJson.getJSONObject(JSON_PROP_REQBODY));
			resJson.put(JSON_PROP_RESHEADER, reqHdr);
			resJson.put(JSON_PROP_RESBODY, opsRqJson.getJSONObject(JSON_PROP_RESBODY));
			op = CommercialsOperation.forString(opsRqJson.getString("commercialsOperation"));

			// To create the hashmap for paxCount
			JSONArray activityInfoArr = reqJson.getJSONObject("requestBody").getJSONArray(JSON_PROP_ACTIVITYINFO);

			for (int i = 0; i < activityInfoArr.length(); i++) {

				JSONObject activityInfo = activityInfoArr.getJSONObject(i);

				ActivityRepriceProcessor.getPaxTypeCountMap(activityInfo, paxTypeCountMap, i);

			}
		}

		catch (Exception e) {
			throw new RequestProcessingException(e.getCause());
		}

		try {

			JSONObject resSupplierComJson = SupplierCommercials.getSupplierCommercialsV2(op,
					reqJson, resJson);

			JSONObject resClientComJson = ClientCommercials.getClientCommercials(reqJson, resJson, resSupplierComJson);

			ActivitySearchProcessor.calculatePricesV2(reqJson, resJson, resSupplierComJson, resClientComJson, true);

			return resJson.toString();

		}

		catch (Exception e) {
			throw new InternalProcessingException(e.getCause());
		}

	}

}
