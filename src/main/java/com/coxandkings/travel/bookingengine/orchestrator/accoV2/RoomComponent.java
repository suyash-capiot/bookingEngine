package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.math.BigDecimal;

import org.json.JSONObject;

public class RoomComponent implements AccoConstants{

	private int mRoomIdx;
	private String mHotelCode;
	private String mSuppCode;
	private BigDecimal mRoomPrice;
	private JSONObject mRoomJson;
	
	public RoomComponent(JSONObject roomJson) {
		mHotelCode = roomJson.getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_HOTELINFO).getString(JSON_PROP_HOTELCODE);
		mRoomIdx = roomJson.getInt(JSON_PROP_ROOMINDEX);
		mSuppCode = roomJson.getString(JSON_PROP_SUPPREF);
		mRoomPrice = roomJson.getJSONObject(JSON_PROP_ROOMPRICE).getBigDecimal(JSON_PROP_AMOUNT);
		mRoomJson = roomJson;
	}

	public int getRoomIdx() {
		return mRoomIdx;
	}
	
	public String getHotelCode() {
		return mHotelCode;
	}
	
	public String getSupplierCode() {
		return mSuppCode;
	}
	
	public BigDecimal getRoomPrice() {
		return mRoomPrice;
	}
	
	public JSONObject toJson() {
		return mRoomJson;
	}
	
}
