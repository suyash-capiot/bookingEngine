package com.coxandkings.travel.bookingengine.orchestrator.activities;

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

public class ActivitySearchSorterResponder implements ActivityConstants {

	private static final DecimalFormat decFmt = new DecimalFormat("000000000.000000");
	private static final Logger logger = LogManager.getLogger(ActivitySearchSorterResponder.class);

	public static void respondToCallback(URL callbackURL, JSONObject reqHdrJson, List<JSONArray> itinsList) {
		JSONObject resJson = getSortedResponse(reqHdrJson, itinsList);
		Map<String, String> httpHdrs = new HashMap<String, String>();
		httpHdrs.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
		try {
			HTTPServiceConsumer.consumeJSONService("CallBack", callbackURL, httpHdrs, resJson);
		} catch (Exception x) {
			logger.warn(String.format("An exception occurred while responding to callback address %s",
					callbackURL.toString()), x);
		}
	}

	static JSONObject getSortedResponse(JSONObject reqHdrJson, List<JSONArray> itinsList) {

		JSONObject resBodyJson = new JSONObject();
		JSONArray activityInfoArr = sortTourActivityInfoByPrice(itinsList);
		resBodyJson.put(JSON_PROP_ACTIVITYINFO, activityInfoArr);

		//		JSONObject resBodyJson = new JSONObject();
		//		JSONArray tourActivityInfoArr = sortTourActivityInfoByPrice(itinsList);
		//		JSONArray activityInfoArr = new JSONArray();
		//		activityInfoArr.put(new JSONObject().put(JSON_PROP_TOURACTIVITYINFO, tourActivityInfoArr));
		//		resBodyJson.put(JSON_PROP_ACTIVITYINFO, activityInfoArr);

		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, resBodyJson);

		return resJson;
	}

	private static JSONArray sortTourActivityInfoByPrice(List<JSONArray> itinsList) {

		Map<Integer, Map<String, JSONObject>> activityInfoSorterMap = new TreeMap<>();

		JSONArray activityInfoArrTemp = itinsList.get(0);

		StringBuilder strBldr = new StringBuilder();

		if(activityInfoArrTemp.length()>0)
		{
			for(int i = 0; i<activityInfoArrTemp.length(); i++) {

				JSONArray tourActivityInfoArr = new JSONArray();
				tourActivityInfoArr = activityInfoArrTemp.getJSONArray(i);

				Map<String, JSONObject> tourActivityInfoSorterMap = new TreeMap<>();

				for (int j = 0; j < tourActivityInfoArr.length(); j++) {

					JSONObject tourActivityInfoJson = tourActivityInfoArr.getJSONObject(j);


					JSONObject activitySummaryPrice = tourActivityInfoJson.getJSONObject("activityTotalPricingInfo").getJSONObject("activitySummaryPrice");

					strBldr.setLength(0);
					strBldr.append(decFmt.format(activitySummaryPrice.getBigDecimal("amount")));
					strBldr.append(String.format("%-15s", tourActivityInfoJson.getString("supplierID")));

					// TODO: check if this is right. For now have appended uniquekey instead of getRedisKey as done in cars
					strBldr.append(tourActivityInfoJson.getJSONObject("basicInfo").getString("uniqueKey"));

					tourActivityInfoSorterMap.put(strBldr.toString(), tourActivityInfoJson);

				}

				activityInfoSorterMap.put(i, tourActivityInfoSorterMap);

			}




			JSONArray activityInfoArrResult = new JSONArray();

			for(Map.Entry<Integer, Map<String, JSONObject>> entryActivityInfo : activityInfoSorterMap.entrySet()) {

				JSONObject activityInfoJson = new JSONObject();
				JSONArray tourActivityInfoResult = new JSONArray();

				Map<String, JSONObject> tourActivityInfoSorterMap = new TreeMap<>();
				tourActivityInfoSorterMap = entryActivityInfo.getValue();

				for(Map.Entry<String, JSONObject> entryTourActivityInfo: tourActivityInfoSorterMap.entrySet()) {
					tourActivityInfoResult.put(entryTourActivityInfo.getValue());
				}

				activityInfoJson.put(JSON_PROP_TOURACTIVITYINFO, tourActivityInfoResult);
				activityInfoArrResult.put(entryActivityInfo.getKey(), activityInfoJson);

			}

			return activityInfoArrResult;
		}

		else {
			return null;
		}
	}


}
