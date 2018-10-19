package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import com.coxandkings.travel.bookingengine.utils.Constants;

public interface CruiseConstants extends Constants {

	public static final String PRODUCT_CRUISE = "CRUISE";
	public static final String PRODUCT_CRUISE_PRODS = "Cruise";
	public static final String PRODUCT_CRUISE_SUPPLTRANSRQ = "SUPPLTRANSRQ";
	public static final String PRODUCT_CRUISE_SUPPLTRANSRS = "SUPPLTRANSRS";
	
	public static final String JSON_PROP_SUPPLCRUISE = "http://www.coxandkings.com/integ/suppl/cruise";
	public static final String JSON_PROP_TOTALFARE = "totalFare";
	public static final String JSON_PROP_REQUESTORID ="RequestorID";
	public static final String JSON_PROP_GUESTS ="Guests";
	public static final String JSON_PROP_SAILINGDATERANGE ="sailingDateRange";   
	public static final String JSON_PROP_ENDDATE ="endDate";
	public static final String JSON_PROP_STARTDATE ="startDate";
	public static final String PRODUCT_CRUISE_XML_END ="End";
	public static final String PRODUCT_CRUISE_XML_START ="Start";
	public static final String JSON_PROP_CRUISELINEPREF ="cruiseLinePref";  
	public static final String JSON_PROP_REGIONPREF ="regionPref";
	public static final String JSON_PROP_REGIONCODE ="regionCode";
	public static final String JSON_PROP_MAXCABINOCCUPANCY ="maxCabinOccupancy";
	public static final String JSON_PROP_CATEGORYLOCATION ="categoryLocation";
	public static final String JSON_PROP_INCLUSIVEPACKAGEOPTION ="inclusivePackageOption";
	public static final String JSON_PROP_INFORMATION ="information";
	public static final String JSON_PROP_ITINERARYID ="itineraryID";
	public static final String JSON_PROP_USESDYNAMICPRICING ="usesDynamicPricing"; 
	public static final String JSON_PROP_SHOPINSURANCE ="shopInsurance";
	public static final String JSON_PROP_DISCOUNTABLE ="discountable";
	public static final String JSON_PROP_FARECODE ="fareCode";
	public static final String JSON_PROP_PRICEDCATEGORYCODE ="pricedCategoryCode"; 
	public static final String JSON_PROP_FARENAME ="fareName";
	public static final String JSON_PROP_FAREVAL ="fareValue";
	public static final String JSON_PROP_TAXES ="taxes";
	public static final String JSON_PROP_TAX ="tax";
	public static final String JSON_PROP_FEES ="fees";
	public static final String JSON_PROP_FEE ="fee";
	public static final String JSON_PROP_FEECODE ="feeCode";
	public static final String JSON_PROP_PSGRTYPE = "passengerType";
	public static final String JSON_PROP_PRODDETAILS = "productDetails";
	public static final String CANCEL_SI_ERR = "SI-NotSupported";
	
	
	public static final char KEYSEPARATOR = '|';
	
	public static final String JSON_PROP_RECEIVABLE = "receivable";
	public static final String JSON_PROP_RECEIVABLES = "receivables";
	
	public static final String JSON_PROP_CRUISETOTALFARE = "cruiseTotalFare";
	public static final String JSON_PROP_BASEFARE = "baseFare";
	
	public static final String JSON_PROP_CRUISEOPTIONS = "cruiseOptions";
	public static final String JSON_PROP_CATEGORY = "category";
	public static final String JSON_PROP_PRICINGINFO = "pricingInfo";
	public static final String JSON_PROP_SELECTEDSAILING = "selectedSailing";
	public static final String JSON_PROP_DEPARTUREPORT = "departurePort";
	public static final String JSON_PROP_FAREBREAKUP = "fareBreakUp";
	public static final String JSON_PROP_TOTALINFO = "totalInfo";
	
	public static final String JSON_PROP_JOURNEYTYPE = "journeyType";
	public static final String JSON_PROP_CONNECTSUPP = "connectivitySupplier";
	public static final String JSON_PROP_CONNECTSUPPTYPE = "connectivitySupplierType";
	public static final String JSON_PROP_BOOKINGTYPE = "bookingType";
	public static final String JSON_PROP_SEGMENT = "segment";
}
