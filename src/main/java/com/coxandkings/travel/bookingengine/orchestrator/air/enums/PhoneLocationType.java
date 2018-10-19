package com.coxandkings.travel.bookingengine.orchestrator.air.enums;

public enum PhoneLocationType {
	HOME("6"), WORK("7"), MOBILE("10"), OTHER("7");
	
	private String mPhoneLocationTypeCode;
	private PhoneLocationType(String PhoneLocationTypeCode) {
		mPhoneLocationTypeCode = PhoneLocationTypeCode;
	}
	
	public String toString() {
		return mPhoneLocationTypeCode;
	}
}
