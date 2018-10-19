package com.coxandkings.travel.bookingengine.eticket.templatemaster.enums;

public enum ProductTypes {ACCO("Accommodation"), AIR("Flight"), BUS("Bus"),RAIL("Rail");
	private String value;

	private ProductTypes(String value){  
	this.value=value;  
	}
}
