package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.userctx.UserContext;

public class AccoSearchSorterResponder implements AccoConstants {
	
	private static final DecimalFormat decFmt = new DecimalFormat("000000000.000000"); 
	
	public static JSONArray getSortedResponse(JSONObject reqHeader,JSONArray mSearchRes) {
		List<JSONArray> jsonArrLst = new ArrayList<JSONArray>();
		jsonArrLst.add(mSearchRes);
		return getSortedResponse(reqHeader, jsonArrLst);
	}
	
	public static JSONArray getSortedResponse(JSONObject reqHeader,List<JSONArray> mSearchRes) {
		UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
		Map<String, JSONObject> totalPriceInfoSorterMap = new TreeMap<String, JSONObject>();
		StringBuilder strBldr = new StringBuilder();
		for (JSONArray roomStayArr : mSearchRes) {
			for (int i=0; i < roomStayArr.length(); i++) {
				JSONObject roomStayObj = roomStayArr.getJSONObject(i);
				JSONObject totalPriceInfoJson = roomStayObj.getJSONObject(JSON_PROP_ROOMPRICE);
				JSONObject sortComponentJson = getJSONObjectForSorting(usrCtx, totalPriceInfoJson);
				
				strBldr.setLength(0);
				strBldr.append(decFmt.format(sortComponentJson.getDouble(JSON_PROP_AMOUNT)));
				strBldr.append(String.format("%-15s", roomStayObj.getString(JSON_PROP_SUPPREF)));
				strBldr.append(AccoRepriceProcessor.getRedisKeyForRoomStay(roomStayObj.getJSONObject(JSON_PROP_ROOMINFO)));
				totalPriceInfoSorterMap.put(strBldr.toString(), roomStayObj);
			}
		}
		
		JSONArray totalPriceInfoJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> totalPriceInfoIter = totalPriceInfoSorterMap.entrySet().iterator();
		while (totalPriceInfoIter.hasNext()) {
			Entry<String, JSONObject> totalPriceInfoEntry = totalPriceInfoIter.next();
			totalPriceInfoJsonArr.put(totalPriceInfoEntry.getValue());
		}

		return totalPriceInfoJsonArr;
		
	}
	
	public static JSONArray getMRSortedResponse(JSONObject reqHeader,JSONArray accoInfoArray) {
		UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
		Map<String, JSONObject> totalPriceInfoSorterMap = new TreeMap<String, JSONObject>();
		StringBuilder strBldr = new StringBuilder();
		for (int i =0;i<accoInfoArray.length();i++) {
				JSONObject accoInfoObj = (JSONObject) accoInfoArray.get(i);
				JSONObject combinedTotalPriceInfoJson =  accoInfoObj.getJSONObject("combinedTotalPriceInfo");
				JSONObject sortComponentJson = getJSONObjectForSorting(usrCtx, combinedTotalPriceInfoJson);
				strBldr.setLength(0);
				strBldr.append(decFmt.format(sortComponentJson.getDouble(JSON_PROP_AMOUNT)));
				//so that same amounts are not overriden
				strBldr.append(i);
				totalPriceInfoSorterMap.put(strBldr.toString(), (JSONObject) accoInfoObj);
		}
		
		JSONArray totalPriceInfoJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> totalPriceInfoIter = totalPriceInfoSorterMap.entrySet().iterator();
		while (totalPriceInfoIter.hasNext()) {
			Entry<String, JSONObject> totalPriceInfoEntry = totalPriceInfoIter.next();
			totalPriceInfoJsonArr.put(totalPriceInfoEntry.getValue());
		}

		return totalPriceInfoJsonArr;
		
	}

	public static JSONObject getJSONObjectForSorting(UserContext usrCtx, JSONObject totalPriceInfoJson) {
		switch(usrCtx.getClientSortOrder()) {
		case Incentives: {
			JSONObject incentivesObj = totalPriceInfoJson.optJSONObject(JSON_PROP_INCENTIVES);
			return incentivesObj!=null?incentivesObj:totalPriceInfoJson;
		}
		case Price :
		default: {
			return totalPriceInfoJson;
		}
	}
	}
}
