package com.coxandkings.travel.bookingengine.orchestrator.bus;

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

import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;




public class BusSearchSorterResponder implements BusConstants{

	private static final DecimalFormat decFmt = new DecimalFormat("000000000.000000"); 
	private static final Logger logger = LogManager.getLogger(BusSearchSorterResponder.class);
	
	static JSONObject getSortedResponse(JSONObject reqHdrJson, List<JSONArray> itinsList) {
		JSONObject resBodyJson = new JSONObject();
		JSONArray serviceJsonArr = sortServicesByPrice(itinsList);
//		resBodyJson.put(JSON_PROP_SERVICE, serviceJsonArr);
		JSONObject availJson = new JSONObject();
		JSONArray availabilityJsonArr = new JSONArray();
		availJson.put(JSON_PROP_SERVICE, serviceJsonArr);
		availabilityJsonArr.put(availJson);
		resBodyJson.put(JSON_PROP_AVAILABILITY, availabilityJsonArr);
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, resBodyJson);

		return resJson;
	}

	private static JSONArray sortServicesByPrice(List<JSONArray> servicesList) {
		
		Map<String, JSONObject> serviceSorterMap = new TreeMap<String, JSONObject>();
		StringBuilder strBldr = new StringBuilder();
		
		
		for (JSONArray service : servicesList) 
		{
			for (int i=0; i < service.length(); i++) {
				JSONObject serviceJson = service.getJSONObject(i);
				JSONArray fareJsonArr = serviceJson.getJSONArray("fares");
				for(int j=0;j<fareJsonArr.length();j++)
				{
					JSONObject fareJson = fareJsonArr.getJSONObject(j);
					JSONObject serviceTotalFareJson = fareJson.getJSONObject(JSON_PROP_BUSSERVICETOTALFARE);
					JSONObject baseFareJson = serviceTotalFareJson.getJSONObject(JSON_PROP_BASEFARE);
					
					strBldr.setLength(0);
					strBldr.append(decFmt.format(baseFareJson.getDouble(JSON_PROP_AMOUNT)));
					strBldr.append(String.format("%-15s", serviceJson.getString(JSON_PROP_SUPPREF)));
					strBldr.append(BusSearchProcessor.getRedisKeyForBusService(serviceJson));
					serviceSorterMap.put(strBldr.toString(), serviceJson);
				}
				
			}
		}
		
		JSONArray serviceJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> serviceIter = serviceSorterMap.entrySet().iterator();
		while (serviceIter.hasNext()) {
			Entry<String, JSONObject> pricedItinEntry = serviceIter.next();
			serviceJsonArr.put(pricedItinEntry.getValue());
		}
		
		return serviceJsonArr;
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
	
	
	
}
