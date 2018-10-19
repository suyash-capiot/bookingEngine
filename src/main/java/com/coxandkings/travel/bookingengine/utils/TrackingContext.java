package com.coxandkings.travel.bookingengine.utils;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import com.coxandkings.travel.bookingengine.config.mongo.MongoCommonConfig;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;
import org.json.JSONObject;
import org.w3c.dom.Element;

public class TrackingContext {
	private List<SimpleEntry<String,String>> mTrackVals = new ArrayList<SimpleEntry<String,String>>();
	private String mTrackParamsStr;
	private static Map<Long, TrackingContext> mTrackCtx = new HashMap<Long, TrackingContext>();
	private static TrackingContext DEFAULT_TRACKING_CONTEXT = new TrackingContext();

	private TrackingContext() {
		mTrackParamsStr = "";
	}
	
	private TrackingContext(Element header) {
		String key, val;
		StringBuilder strBldr = new StringBuilder();
		List<SimpleEntry<String,String>> trackElems = MongoCommonConfig.getTrackingElements();
		for (SimpleEntry<String,String> trackElem : trackElems) {
			key = trackElem.getKey();
			val = XMLUtils.getValueAtXPath(header, trackElem.getValue());
			mTrackVals.add(new SimpleEntry<String,String>(key, val));
			strBldr.append(String.format("[%s: %s] ", key, val));
		}
		
		strBldr.setLength(strBldr.length() - 1);
		mTrackParamsStr = strBldr.toString();
	}

	private TrackingContext(JSONObject header) throws Exception {
		String key, val;
		StringBuilder strBldr = new StringBuilder();
		List<SimpleEntry<String,String>> trackElems = MongoCommonConfig.getTrackingElements();
		for (SimpleEntry<String,String> trackElem : trackElems) {
			key = trackElem.getKey();
			String jsonKey = convertToInitLower(key);
			val = header.optString(jsonKey, "");
			mTrackVals.add(new SimpleEntry<String,String>(key, val));
			strBldr.append(String.format("[%s: %s] ", key, val));
		}
		
		strBldr.setLength(strBldr.length() - 1);
		mTrackParamsStr = strBldr.toString();
	}

	public String getTrackingParameter(String paramName) {
		for (SimpleEntry<String,String> trackVal : mTrackVals) {
			if (trackVal.getKey().equals(paramName)) {
				return trackVal.getValue();
			}
		}
		
		return "";
	}
	
	public String toString() {
		return mTrackParamsStr;
	}

	public static void setTrackingContext(JSONObject req) throws Exception{
		JSONObject reqHdr = (req.optJSONObject("requestHeader") != null) ? req.optJSONObject("requestHeader") : new JSONObject();
		TrackingContext trckngCtx = new TrackingContext(reqHdr);
		trckngCtx.addRestServiceTrackingParam();
		mTrackCtx.put(Thread.currentThread().getId(),trckngCtx);
	}

	
	public static void setTrackingContext(Element req) {
		QName reHeaderQN = new QName(req.getNamespaceURI(), "RequestHeader");
		Element reqHdr = XMLUtils.getChildElement(req, reHeaderQN, null);
		mTrackCtx.put(Thread.currentThread().getId(), new TrackingContext(reqHdr));
	}
	
	public static void duplicateContextFromThread(long sourceThreadID) {
		TrackingContext trkngCtx = mTrackCtx.get(sourceThreadID);
		if (trkngCtx != null) {
			mTrackCtx.put(Thread.currentThread().getId(), trkngCtx);
		}
	}
	
	public static TrackingContext getTrackingContext() {
		TrackingContext trackCtx = mTrackCtx.get(Thread.currentThread().getId()); 
		return (trackCtx != null) ? trackCtx : DEFAULT_TRACKING_CONTEXT;
	}
	
	private static String convertToInitLower(String trackPropName) {
		StringBuilder strBldr = new StringBuilder(trackPropName);
		return strBldr.replace(0, 1, String.valueOf(Character.toLowerCase(strBldr.charAt(0)))).toString();
	}

	private void addRestServiceTrackingParam() {
		List<SimpleEntry<String, String>> trackParams = ServletContext.getRestTrackingParamList();
		mTrackVals.addAll(trackParams);
		trackParams.forEach(trackElem -> mTrackParamsStr=mTrackParamsStr.concat(String.format(" [%s: %s]", trackElem.getKey(),trackElem.getValue())));
	}
}
