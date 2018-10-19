package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class AccoSearchListenerResponder implements AccoConstants {
	private static final Logger logger = LogManager.getLogger(AccoSearchListenerResponder.class);
	private List<ProductSupplier> mProdSuppliers;
	private List<JSONArray> mSearchRes;
	private boolean mSentFirstResponse;
	private JSONObject mReqJson;
	private JSONObject mReqHeaderJson;
	private URL mCallbackURL;
	private boolean mIsAsync;
	private RoomComponentsGroup mRoomCompsGrp = new RoomComponentsGroup();
	private int mReqRoomCnt;
	private int count=1;
	
	AccoSearchListenerResponder(List<ProductSupplier> prodSuppliers, JSONObject reqJson) {
		mProdSuppliers = prodSuppliers;
		System.out.println("prodSupplier size"+prodSuppliers.size());
		mSearchRes = Collections.synchronizedList(new ArrayList<JSONArray>());
		mReqJson = reqJson;
		mReqHeaderJson = mReqJson.getJSONObject(JSON_PROP_REQHEADER);
		mReqRoomCnt = mReqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_REQUESTEDROOMARR).length();
		JSONObject clientCtxJson = mReqHeaderJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String callbackAddr = clientCtxJson.optString(JSON_PROP_CLIENTCALLBACK);
		if (callbackAddr != null && callbackAddr.isEmpty() == false) { 
			try {
				mCallbackURL = new URL(clientCtxJson.getString(JSON_PROP_CLIENTCALLBACK));
				mIsAsync = true;
			}
			catch (Exception x) {
				// TODO: Handle this exception properly. That probably means that it should be logged.
				x.printStackTrace();
			}
		}
	}
	
	void receiveSupplierResponse(JSONObject suppResJson) {
		JSONArray roomStayArr = suppResJson == null?new JSONArray():suppResJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR).getJSONObject(0).getJSONArray(JSON_PROP_ROOMSTAYARR);
		System.out.println("response recieved"+count);
		count++;
		mRoomCompsGrp.addRoomComponents(roomStayArr);
		mSearchRes.add(roomStayArr);
		if (mIsAsync) {
			if ((mSentFirstResponse == false && mRoomCompsGrp.getRoomIdxsCount() == mReqRoomCnt)|| mSearchRes.size() == mProdSuppliers.size()) {
				mSentFirstResponse = true;
				Map<String, String> httpHdrs = new HashMap<String, String>();
				httpHdrs.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
				try {
					JSONObject resJson = getMultiRoomSearchResponse();
					MRCompanyOffers.getCompanyOffers(CommercialsOperation.Search,mReqJson, resJson,OffersType.COMPANY_SEARCH_TIME);
					HTTPServiceConsumer.consumeJSONService("CallBack", mCallbackURL, httpHdrs, resJson);
				}
				catch (Exception x) {
					logger.warn(String.format("An exception occurred while responding to callback address %s", mCallbackURL.toString()), x);
				}
			}
		}
		
		if (mSearchRes.size() == mProdSuppliers.size()) {
			// TODO: Is this right?
			synchronized(this) {
				notify();
			}
		}
	}
	
	JSONObject getSearchResponse() {
		JSONObject resJson = new JSONObject();
		JSONArray roomStayJsonArr = new JSONArray();
		JSONObject resBodyJson = (new JSONObject()).append(JSON_PROP_ACCOMODATIONARR, (new JSONObject()).put(JSON_PROP_ROOMSTAYARR, roomStayJsonArr));
		
		/*for(JSONArray tempJsonArr:mSearchRes) {
			for(Object roomStayJson:tempJsonArr) {
				roomStayJsonArr.put(roomStayJson);
			}
		}*/
		//this will return the response in a sorted order according to incentives or price
		JSONArray sortedArr = AccoSearchSorterResponder.getSortedResponse(mReqHeaderJson,mSearchRes);
			for(Object roomStayJson:sortedArr) {
				roomStayJsonArr.put(roomStayJson);
			}
		resJson.put(JSON_PROP_RESHEADER, mReqHeaderJson);
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		return resJson;
}
	
	JSONObject getMultiRoomSearchResponse() {
		//Initialize response
		JSONObject resJson = new JSONObject();
		JSONObject resBodyJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, mReqHeaderJson);
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		resBodyJson.put(JSON_PROP_ACCOMODATIONARR, new JSONArray());
		
		//if not all rooms respond
		if(mRoomCompsGrp.getRoomIdxsCount()!=mReqRoomCnt)
			return resJson;
		
		//If hotels not common in all responses they need to be discarded
		//So get minimum hotel lst count for a particualr room index to reduce iterations
		Map<Integer, Map<String, List<JSONObject>>> roomHierarchyMap = mRoomCompsGrp.getRoomHierarchy();
		Set<String> refHtlLst = null;
		int minHtlCnt = Integer.MAX_VALUE;
		for(Map<String, List<JSONObject>> htlRoomMap :roomHierarchyMap.values()) {
			int htlCnt =htlRoomMap.keySet().size();
			if(htlCnt<minHtlCnt) {
				refHtlLst = htlRoomMap.keySet();
				minHtlCnt=htlCnt;
			}
		}
		
		//combine rooms per hotel
		for(String hotel:refHtlLst) {
			JSONArray combinedRoomJsonArr = new JSONArray();
			int insertedRooms = 0;
			for(int roomIdx:roomHierarchyMap.keySet()) {
				List<JSONObject> roomLst = mRoomCompsGrp.getRoomsFromHierarchy(roomIdx, hotel);
				if(roomLst == null || roomLst.isEmpty())
					continue;
				JSONObject minPrcRoomJson =  (JSONObject) AccoSearchSorterResponder.getSortedResponse(mReqHeaderJson,new JSONArray(roomLst)).get(0);
				//int currRoomIdx = (int) minPrcRoomJson.remove(JSON_PROP_ROOMINDEX);
				int currRoomIdx = (int) minPrcRoomJson.get(JSON_PROP_ROOMINDEX);
				combinedRoomJsonArr.put(currRoomIdx,minPrcRoomJson);//get min price sorted data here
				insertedRooms++;
			}
			if(insertedRooms != mReqRoomCnt)
				continue;
			resBodyJson.append(JSON_PROP_ACCOMODATIONARR, new JSONObject().put(JSON_PROP_ROOMSTAYARR, combinedRoomJsonArr));
		}
		
		return resJson;
	}

}
