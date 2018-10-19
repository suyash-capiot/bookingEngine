package com.coxandkings.travel.bookingengine.orchestrator.car.rental;

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

import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class RentalSearchSorterResponder implements CarConstants {
	
	private static final DecimalFormat decFmt = new DecimalFormat("000000000.000000"); 
	private static final Logger logger = LogManager.getLogger(RentalSearchSorterResponder.class);
	
	private static JSONArray sortVehicleAvailByPrice(List<JSONArray> itinsList) {
		Map<String, JSONObject> vehicleAvailSorterMap = new TreeMap<String, JSONObject>();
		StringBuilder strBldr = new StringBuilder();
		for (JSONArray itins : itinsList) {
			for (int i=0; i < itins.length(); i++) {
				JSONObject vehicleAvail = itins.getJSONObject(i);
				JSONObject pricingInfoJson = vehicleAvail.getJSONObject(JSON_PROP_TOTALPRICEINFO);
				JSONObject totalFareJson = pricingInfoJson.getJSONObject(JSON_PROP_TOTALFARE);
				
				strBldr.setLength(0);
				strBldr.append(decFmt.format(totalFareJson.getDouble(JSON_PROP_AMOUNT)));
				strBldr.append(String.format("%-15s", vehicleAvail.getString(JSON_PROP_SUPPREF)));
				strBldr.append(RentalSearchProcessor.getRedisKeyForVehicleAvail(vehicleAvail));
				vehicleAvailSorterMap.put(strBldr.toString(), vehicleAvail);
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
		JSONArray carRentalArr = new JSONArray();
		carRentalArr.put(new JSONObject().put(JSON_PROP_VEHICLEAVAIL, vehicleAvailArr));
		resBodyJson.put(JSON_PROP_CARRENTALARR, carRentalArr);
		
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, resBodyJson);

		return resJson;
	}

}
