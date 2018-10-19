package com.coxandkings.travel.bookingengine.orchestrator.air.enums;

public enum CabinClass {
	ECONOMY("Economy"), BUSINESS("Business"), FIRST("First");
	
	private String mCabinClassCode;
	private CabinClass(String cabClsCd) {
		mCabinClassCode = cabClsCd;
	}
	
	public String toString() {
		return mCabinClassCode;
	}
	
	public static CabinClass forString(String cabinClassStr) { 
		CabinClass[] cabinClasses = CabinClass.values();
		for (CabinClass cabinClass : cabinClasses) {
			if (cabinClass.toString().equals(cabinClassStr)) {
				return cabinClass;
			}
		}
		
		return null;
	}

}
