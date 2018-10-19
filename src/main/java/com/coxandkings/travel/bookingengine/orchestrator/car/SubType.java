package com.coxandkings.travel.bookingengine.orchestrator.car;

public enum SubType {
	
	CAR("Car"), RENTAL("Rental");
	
	private String mSubType;
	
	private SubType(String subType){
		mSubType = subType;
	}
	
	public String toString() {
		return mSubType;
	}
	
	public static SubType forString(String subTypeStr) {
		if (subTypeStr == null || subTypeStr.isEmpty()) {
			return null;
		}
		
		SubType[] subTypes = SubType.values();
		for (SubType subType : subTypes) {
			if (subType.toString().equalsIgnoreCase(subTypeStr)) {
				return subType;
			}
		}
		return null;
	}
	
	
}
