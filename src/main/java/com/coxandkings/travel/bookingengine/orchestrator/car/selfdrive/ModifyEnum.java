package com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive;


public enum ModifyEnum {

	ADDANC("ADDANCILLARY"),
	CANANC("CANCELANCILLARY"),
	PIU("PASSENGERINFOUPDATE"),
	PNC("PASSENGERNAMECHANGE"),
	ALL("FULLCANCEL");
	
	private String modifyType;
	
	ModifyEnum(String modifyEnum){
		modifyType = modifyEnum;
    }
	
    public String getDescription(){
	        return modifyType;
    }
	  
    public static ModifyEnum forString(String modifyTypeStr) {
    	
		if (modifyTypeStr == null || modifyTypeStr.isEmpty()) {
			return null;
		}
		
		ModifyEnum[] tripTypes = ModifyEnum.values();
		for (ModifyEnum tripType : tripTypes) {
			if (tripType.name().equalsIgnoreCase(modifyTypeStr)) {
				return tripType;
			}
		}
		
		return null;
	}
}
