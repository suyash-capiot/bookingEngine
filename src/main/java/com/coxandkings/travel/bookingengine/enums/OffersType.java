package com.coxandkings.travel.bookingengine.enums;

public enum OffersType {
	COMPANY_SEARCH_TIME("companySearchTime"),
	COMPANY_REDEEM_TIME("companyRedeemTime"), 
	SUPPLIER_SEARCH_TIME("supplierSearchTime"), 
	SUPPLIER_REDEEM_TIME("supplierRedeemType"),
	REDEEM_OFFER_CODE_GENERATION("redeemOfferCodeGeneration");
	
	private String mTypeString;
	private OffersType(String typeString) {
		mTypeString = typeString;
	}
	
	public String toString() {
		return mTypeString;
	}

}
