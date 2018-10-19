package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.orchestrator.air.AirRepriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirSearchSorterResponder;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class CruiseSearchSorterResponder implements CruiseConstants {

	private static final DecimalFormat decFmt = new DecimalFormat("000000000.000000"); 
	private static final Logger logger = LogManager.getLogger(CruiseSearchSorterResponder.class);
	
	
	public static void respondToCallback(URL callbackURL, JSONObject reqHdrJson, List<JSONArray> sailingOptsList) {
		JSONObject resJson = getSortedResponse(reqHdrJson, sailingOptsList);
		Map<String, String> httpHdrs = new HashMap<String, String>();
		httpHdrs.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
		try {
			HTTPServiceConsumer.consumeJSONService("CallBack", callbackURL, httpHdrs, resJson);
		}
		catch (Exception x) {
			logger.warn(String.format("An exception occurred while responding to callback address %s", callbackURL.toString()), x);
		}
	}
	
	static JSONObject getSortedResponse(JSONObject reqHdrJson, List<JSONArray> sailingOptsList) {
		JSONObject resBodyJson = new JSONObject();
		JSONArray sailingOptionJsonArr = sortItinsByPrice(sailingOptsList);
		
        resBodyJson.put("cruiseOptions", sailingOptionJsonArr);
        
        System.out.println(resBodyJson.toString());
        
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		return resJson;
	}
	
	private static JSONArray sortItinsByPrice(List<JSONArray> sailingOptsList) {
		
		Map<String, JSONObject> sailingOptsSorterMap = new TreeMap<String, JSONObject>();
		StringBuilder strBldr = new StringBuilder();
		for (JSONArray sailingOpts : sailingOptsList) {
			for(int i=0;i<sailingOpts.length();i++)
			{
				JSONObject sailingOpt =	sailingOpts.getJSONObject(i);
				System.out.println(sailingOpt.toString());
				strBldr.setLength(0);
				strBldr.append(decFmt.format(sailingOpt.getJSONObject("lowestFare").getJSONObject("totalInfo").getBigDecimal("amount")));
				strBldr.append(String.format("%-15s",sailingOpt.getString("supplierRef")));
//				strBldr.append(CruiseRePriceProcessor.getRedisKeyForSailingOption(sailingOpt));
				
				sailingOptsSorterMap.put(strBldr.toString(), sailingOpt);
			}
		}
		
		JSONArray sailingOptsJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> sailingOptsIter = sailingOptsSorterMap.entrySet().iterator();
		while (sailingOptsIter.hasNext()) {
			Entry<String, JSONObject> sailingOptEntry = sailingOptsIter.next();
			sailingOptsJsonArr.put(sailingOptEntry.getValue());
		}
		
		return sailingOptsJsonArr;
	}
}
