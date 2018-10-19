package com.coxandkings.travel.bookingengine.orchestrator.holidays;

public enum PassengerType {
	ADT("ADT"), CHD("CHD"), INF("INF");
	
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
