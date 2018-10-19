package com.coxandkings.travel.bookingengine.orchestrator.transfers;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class TransfersSearchSorterResponder implements TransfersConstants {
	
	private static final DecimalFormat decFmt = new DecimalFormat("000000000.000000"); 
	private static final Logger logger = LogManager.getLogger(TransfersSearchSorterResponder.class);
	
	private static JSONArray sortVehicleAvailByPrice(List<JSONArray> itinsList) {
		Map<String, JSONObject> vehicleAvailSorterMap = new TreeMap<String, JSONObject>();
		StringBuilder strBldr = new StringBuilder();
		for (JSONArray itins : itinsList) {
			for (int i=0; i < itins.length(); i++) {
				JSONObject groundServiceJson = itins.getJSONObject(i);
				JSONObject pricingInfoJson = groundServiceJson.getJSONObject(JSON_PROP_TOTALCHARGE);
				//JSONObject totalFareJson = pricingInfoJson.getJSONObject(JSON_PROP_TOTALFARE);
				
				strBldr.setLength(0);
				strBldr.append(decFmt.format(pricingInfoJson.getDouble(JSON_PROP_AMOUNT)));
				strBldr.append(String.format("%-15s", groundServiceJson.getString(JSON_PROP_SUPPREF)));
				strBldr.append(TransfersSearchProcessor.getRedisKeyForGroundService(groundServiceJson));
				vehicleAvailSorterMap.put(strBldr.toString(), groundServiceJson);
			}
		}
		
		JSONArray vehicleAvailJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> vehicleAvailIter = vehicleAvailSorterMap.entrySet().iterator();
		while (vehicleAvailIter.hasNext()) {
			Entry<String, JSONObject> pricedItinEntry = vehicleAvailIter.next();
			vehicleAvailJsonArr.put(pricedItinEntry.getValue());
		}
		
		return vehicleAvailJsonArr;
	}
	
	public static void respondToCallback(URL callbackURL, JSONObject reqHdrJson, List<JSONArray> itinsList) {
		JSONObject resJson = getSortedResponse(reqHdrJson, itinsList);
		Map<String, String> httpHdrs = new HashMap<String, String>();
		httpHdrs.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
		try {
			HTTPServiceConsumer.consumeJSONService("CallBack", callbackURL, httpHdrs, resJson);
		}
		catch (Exception x) {
			logger.warn(String.format("An exception occurred while responding to callback address %s", callbackURL.toString()), x);
		}
	}
	
	static JSONObject getSortedResponse(JSONObject reqHdrJson, List<JSONArray> itinsList) {
		JSONObject resBodyJson = new JSONObject();
		JSONArray vehicleAvailArr = sortVehicleAvailByPrice(itinsList);
		/*JSONArray transfersArr = new JSONArray();
		transfersArr.put(new JSONObject().put(JSON_PROP_GROUNDSERVICES, vehicleAvailArr));*/
		/*resBodyJson.put(JSON_PROP_CARRENTALARR, transfersArr);*/
		resBodyJson.put(JSON_PROP_GROUNDSERVICES,vehicleAvailArr);
		
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, resBodyJson);

		return resJson;
	}

}
