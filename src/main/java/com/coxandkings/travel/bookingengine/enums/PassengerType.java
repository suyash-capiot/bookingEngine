package com.coxandkings.travel.bookingengine.enums;

public enum PassengerType {
	ADULT("ADT"), CHILD("CHD"), INFANT("INF");
	
	private String mPsgrTypeCode;
	private PassengerType(String ptCode) {
		mPsgrTypeCode = ptCode;
	}
	
	public String toString() {
		return mPsgrTypeCode;
	}
	
	public static PassengerType forString(String psgrTypeStr) {
		PassengerType[] psgrTypes = PassengerType.values();
		for (PassengerType psgrType : psgrTypes) {
			if (psgrType.toString().equals(psgrTypeStr)) {
				return psgrType;
			}
		}
		
		return null;
	}
}
