package com.coxandkings.travel.bookingengine.orchestrator.car.rental.enums;

public enum TripIndicator {
	
	Local("LOCAL_RENTAL") , Outstation("OUTSTATION") ; 
	
	private String mTripIndcCode;
	
	private TripIndicator(String tripIndcCode) {
		mTripIndcCode = tripIndcCode;
	}
	
	public String toString() {
		return mTripIndcCode;
	}
	
	public static TripIndicator forString(String tripIndcStr) {
		if (tripIndcStr == null || tripIndcStr.isEmpty()) {
			return null;
		}
		
		TripIndicator[] tripIndicators = TripIndicator.values();
		for (TripIndicator tripIndicator : tripIndicators) {
			if (tripIndicator.name().equalsIgnoreCase(tripIndcStr)) {
				
				return tripIndicator;
			}
		}
		
		return null;
	}
	
	 public static String getEnumForValue(String value){
		 TripIndicator[] values = TripIndicator.values();
	        String enumValue = "";
	        for(TripIndicator eachValue : values) {
	            enumValue = eachValue.toString();

	            if (enumValue.equals(value)) {
	                return eachValue.name();
	            }
	        }
	    return enumValue;
	}
	
}
