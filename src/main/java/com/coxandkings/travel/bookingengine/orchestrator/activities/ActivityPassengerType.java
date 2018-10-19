package com.coxandkings.travel.bookingengine.orchestrator.activities;

public enum ActivityPassengerType {
	ADT("Adult"), CHD("Child"), INF("Infant"), SUMMARY("Summary"), Adult("ADT"), Child("CHD"), Infant("INF"), Summary("SUMMARY");

	private String mPsgrTypeCode;

	private ActivityPassengerType(String ptCode) {
		mPsgrTypeCode = ptCode;
	}

	public String toString() {
		return mPsgrTypeCode;
	}

	public static ActivityPassengerType forString(String psgrTypeStr) {
		ActivityPassengerType[] psgrTypes = ActivityPassengerType.values();
		for (ActivityPassengerType psgrType : psgrTypes) {
			if (psgrType.toString().equals(psgrTypeStr)) {
				return psgrType;
			}
		}

		return null;
	}

}
