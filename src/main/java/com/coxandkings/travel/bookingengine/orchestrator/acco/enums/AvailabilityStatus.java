package com.coxandkings.travel.bookingengine.orchestrator.acco.enums;

public enum AvailabilityStatus {
    
	AVAILABLE("AvailableForSale"), NOTAVAILABLE("NotAvailable"), ONREQUEST("OnRequest");
	
	private String mAvailStatus;
	private AvailabilityStatus(String availStatus) {
		mAvailStatus = availStatus;
	}
	
	public String toString() {
		return mAvailStatus;
	}
	
	public static AvailabilityStatus forString(String availStatusStr) { 
		AvailabilityStatus[] availStatuses = AvailabilityStatus.values();
		for (AvailabilityStatus availStatus : availStatuses) {
			if (availStatus.toString().equals(availStatusStr)) {
				return availStatus;
			}
		}
		
		return null;
	}
}
