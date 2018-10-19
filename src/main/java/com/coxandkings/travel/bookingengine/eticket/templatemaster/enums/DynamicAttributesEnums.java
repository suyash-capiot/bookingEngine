package com.coxandkings.travel.bookingengine.eticket.templatemaster.enums;

public enum DynamicAttributesEnums {
	PASSENGERNAME("RECEPIENT_Name"), RECEPIENT_GSTIN_OF_RECIPIENT("RECEPIENT_GSTIN_OF_RECIPIENT"), RECEPIENT_ADDRESS("RECEPIENT_Address");
	private String value;  
	private DynamicAttributesEnums(String value){  
	this.value=value;  
	}  
}
