package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

public class RoomComponentsGroup implements AccoConstants{

	private Map<Integer,Map<String,List<JSONObject>>> mRoomHierarchyMap = new ConcurrentHashMap<Integer,Map<String,List<JSONObject>>>();
	
	public void addRoomComponents(JSONArray roomJsonArr) {
		if(roomJsonArr==null)
			return;
		for(Object roomJson:roomJsonArr) {
			if(roomJson instanceof JSONObject) {
				addRoom2Hierarchy(new RoomComponent((JSONObject) roomJson));
			}
		}
	}
	
	public void addRoomComponent(JSONObject roomJson) {
		if(roomJson == null)
			return;
		addRoom2Hierarchy(new RoomComponent(roomJson));
	}


	private void addRoom2Hierarchy(RoomComponent roomComp) {
		Map<String, List<JSONObject>> htlRoomMap = mRoomHierarchyMap.get(roomComp.getRoomIdx());
		if(htlRoomMap == null) {
			htlRoomMap = new ConcurrentHashMap<String, List<JSONObject>>();
			mRoomHierarchyMap.put(roomComp.getRoomIdx(), htlRoomMap);
		}
		List<JSONObject> roomLst = htlRoomMap.get(roomComp.getHotelCode());
		if(roomLst == null) {
			roomLst = Collections.synchronizedList(new ArrayList<JSONObject>());
			htlRoomMap.put(roomComp.getHotelCode(),roomLst);
		}
		roomLst.add(roomComp.toJson());
	}
	
	public int getRoomIdxsCount() {
		return mRoomHierarchyMap.size();
	}
	
	public Map<Integer, Map<String, List<JSONObject>>> getRoomHierarchy(){
		return mRoomHierarchyMap;
	}
	
	public Set<String> getHotelsFromHierarchy(int roomIdx){
		Map<String, List<JSONObject>> htlRoomMap = mRoomHierarchyMap.get(roomIdx);
		if(htlRoomMap != null) {
			return htlRoomMap.keySet();
		}
		return null;
	}
	
	public List<JSONObject> getRoomsFromHierarchy(int roomIdx,String hotel){
		Map<String, List<JSONObject>> htlRoomMap = mRoomHierarchyMap.get(roomIdx);
		if(htlRoomMap != null && htlRoomMap.containsKey(hotel)) {
			return htlRoomMap.get(hotel);
		}
		return null;
	}
	
}
