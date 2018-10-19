package com.coxandkings.travel.bookingengine.enums;

public enum CommercialsType {
	COMMERCIAL_CLIENT_TRANSACTIONAL("clientTransactional"), 
	COMMERCIAL_COMPANY_POLICIES("companyPolicies"), 
	COMMERCIAL_SUPPLIER_TRANSACTIONAL("supplierTransactional");
	
	private String mTypeString;
	private CommercialsType(String typeString) {
		mTypeString = typeString;
	}
	
	public String toString() {
		return mTypeString;
	}
	
}
