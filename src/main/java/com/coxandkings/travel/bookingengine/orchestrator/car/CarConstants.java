package com.coxandkings.travel.bookingengine.orchestrator.car;

import com.coxandkings.travel.bookingengine.utils.Constants;

public interface CarConstants extends Constants {
	
	public static final String PRODUCT_CAR = "CAR";	//To fetch from Mongo
	public static final String PRODUCT_NAME_BRMS = "Cars";
	public static final String PROD_CATEG_SUBTYPE_CAR = "Car"; //Self-Drive
	public static final String PROD_CATEG_SUBTYPE_RENTAL = "Rental"; //Indian
	public static final String NS_CAR = "http://www.coxandkings.com/integ/suppl/carrentals";
	public static final char KEYSEPARATOR = '|';
	public static final String CARRENTAL_DRIVER_AGE = "26";
	
	public static final String JSON_PROP_ADDCOMMDETAILS = "additionalCommercialDetails";
	public static final String JSON_PROP_RETENTIONCOMMDETAILS = "retentionCommercialDetails";
	public static final String JSON_PROP_FIXEDCOMMDETAILS = "fixedCommercialDetails";
	
	public static final String JSON_PROP_CARRENTALARR = "carRentalInfo";
	
	public static final String JSON_PROP_TYPE = "type";
	
	public static final String JSON_PROP_AMOUNT = "amount";
	public static final String JSON_PROP_AREACITYCODE = "areaCityCode";
	public static final String JSON_PROP_BASEFARE = "baseFare";
	public static final String JSON_PROP_BASEFARE_COMM = "baseFare";
	public static final String JSON_PROP_BOOKID = "bookID";
	public static final String JSON_PROP_BOOKINGDURATION =  "bookingDuration";
	public static final String JSON_PROP_BOOKINGUNIT =  "bookingUnit";
	public static final String JSON_PROP_BOOKREFID = "bookRefId";
	public static final String JSON_PROP_BOOKREFIDX ="bookRefIdx";
	public static final String JSON_PROP_CANCELPOLICY = "cancellationPolicy";
	public static final String JSON_PROP_CODECONTEXT = "codeContext";
	public static final String JSON_PROP_COVERAGETYPE = "coverageType";
	public static final String JSON_PROP_CITY = "city";
	public static final String JSON_PROP_CONNECTSUPPTYPE = "connectivitySupplierType";
	public static final String JSON_PROP_CONNECTSUPPNAME = "connectivitySupplierName";
	public static final String JSON_PROP_CLIENTCOMMSTOTAL = "clientCommercialsTotal";
	public static final String JSON_PROP_PAXDETAILS = "paxDetails";
	public static final String JSON_PROP_DRIVERAGE = "driverAge";
	public static final String JSON_PROP_DESCRIPTION = "description";
	public static final String JSON_PROP_ISPREPAID = "isPrepaid";
	public static final String JSON_PROP_ISLEAD = "isLeadPax";
	public static final String JSON_PROP_ISINCLDINBASE = "isIncludedInBase";
	public static final String JSON_PROP_ESTIMATEDTOTALAMOUNT = "estimatedTotalAmount";
	public static final String JSON_PROP_EQUIPTYPE = "equipType";
	public static final String JSON_PROP_EQUIPNAME = "equipName";
	public static final String JSON_PROP_FEE = "fee";
	public static final String JSON_PROP_FEECODE = "feeCode";
	public static final String JSON_PROP_FEES = "fees";
	public static final String JSON_PROP_FLEET = "fleet";
	public static final String JSON_PROP_FAREDETAILS = "fareDetails";
	public static final String JSON_PROP_FARENAME = "fareName";
	public static final String JSON_PROP_FAREVAL = "fareValue";
	public static final String JSON_PROP_ID = "id";
	public static final String JSON_PROP_LOCATIONDETAIL = "locationDetails";
	public static final String JSON_PROP_MARKUPCOMMDTLS = "markUpCommercialDetails";
	public static final String JSON_PROP_ONEWAYINDC = "oneWayIndicator";
	public static final String JSON_PROP_PAYMENTRULES = "paymentRules";
	public static final String JSON_PROP_PSGRDETAILS = "passengerDetails";
	public static final String JSON_PROP_PICKUPDATE =  "pickUpDateTime";
	public static final String JSON_PROP_PICKUPLOCCODE =  "pickUpLocationCode";
	public static final String JSON_PROP_PICKUPLOCNAME = "pickUpLocationName";
	public static final String JSON_PROP_PICKUPLOCCODECONTXT = "pickUpLocationCodeContext";
	public static final String JSON_PROP_PICKUPLOCEXTCODE = "pickUpLocationExtendedCode";
	public static final String JSON_PROP_PACKAGE =  "package";
	public static final String JSON_PROP_PRICEDCOVRG = "pricedCoverage";
	public static final String JSON_PROP_PRICEDCOVRGS = "pricedCoverages";
	public static final String JSON_PROP_PRODDETAILS = "productDetails";
	public static final String JSON_PROP_RATEDISTANCE = "rateDistance";
	public static final String JSON_PROP_RATEQUALIFIER = "rateQualifier";
	public static final String JSON_PROP_RECEIVABLE = "receivable";
	public static final String JSON_PROP_RECEIVABLES = "receivables";
	public static final String JSON_PROP_REFERENCE = "reference";
	public static final String JSON_PROP_RESERVATIONID = "reservationId";
	public static final String JSON_PROP_REFERENCES = "references";
	public static final String JSON_PROP_RETURNDATE =  "returnDateTime";
	public static final String JSON_PROP_RETURNLOCCODE = "returnLocationCode";
	public static final String JSON_PROP_RETURNLOCNAME = "returnLocationName";
	public static final String JSON_PROP_RETURNLOCCODECONTXT = "returnLocationCodeContext";
	public static final String JSON_PROP_RETURNLOCEXTCODE = "returnLocationExtendedCode";
	public static final String JSON_PROP_ISREQUIRED = "isRequired";
	public static final String JSON_PROP_SPLEQUIPS = "specialEquips";
	public static final String JSON_PROP_SPLEQUIP = "specialEquip";
	public static final String JSON_PROP_SALESDATE = "salesDate";
	public static final String JSON_PROP_TOTAL = "total";
	public static final String JSON_PROP_TOTALFARE = "totalFare";
	public static final String JSON_PROP_TOTALFARE_COMM = "totalFare";
	
	public static final String JSON_PROP_TAX = "tax";
	public static final String JSON_PROP_TAXES = "taxes";
	public static final String JSON_PROP_TAXNAME = "taxName";
	public static final String JSON_PROP_TAXVALUE = "taxValue";
	public static final String JSON_PROP_TAXCODE = "taxCode";
	
	public static final String JSON_PROP_TOCITY = "toCity";
	public static final String JSON_PROP_TOCONTINENT = "toContinent";
	public static final String JSON_PROP_TOCOUNTRY = "toCountry";
	public static final String JSON_PROP_TOSTATE = "toState";
	
	public static final String JSON_PROP_TRIPTYPE = "tripType";
	public static final String JSON_PROP_TRIPINDC = "tripIndicator";
	public static final String JSON_PROP_VEHICLEAVAIL = "vehicleAvail";
	public static final String JSON_PROP_VEHICLECATEGORY = "vehicleCategory";
	public static final String JSON_PROP_VEHICLEDETAILS = "vehicleDetails";
	public static final String JSON_PROP_VEHICLEINFO = "vehicleInfo";
	public static final String JSON_PROP_VEHICLENAME = "vehicleName";
	public static final String JSON_PROP_VEHMAKEMODELNAME = "vehMakeModelName";
	public static final String JSON_PROP_VEHMAKEMODELCODE = "vehMakeModelCode";
	public static final String JSON_PROP_VEHPRICINGINFO ="vehiclePricingInfo";
	public static final String JSON_PROP_VEHRENTAL = "vehRentalInfo";
	public static final String JSON_PROP_VENDORDIVISION = "vendorDivison";
	public static final String JSON_PROP_VENDORINFO = "vendorInfo";
	
	public static final String JSON_PROP_SUPPCOMMINFO = "suppCommericalInfo";
	public static final String JSON_PROP_CLIENTCOMMINFO = "clientCommericalInfo";
	public static final String JSON_PROP_SUPPPRICEINFO = "suppPricingInfo";
	public static final String JSON_PROP_TOTALPRICEINFO = "totalPricingInfo";
	
/*	//Different Amends/Cancel
	public static final String ADDANCILLARY = "ADDANC"; //Equipments, Coverages and Additional Driver
	public static final String CANCELANCILLARY = "CANANC"; //Equipments, Coverages and Additional Driver
	public static final String PAXINFOUPDATE = "PIU"; //Other than Names
	public static final String PAXNAMECHANGE = "PNC";
	public static final String CHAGERENTALINFO = "changeRentalInfo";
	public static final String UPGRADECAR = "upgradeCar";
	public static final String FULLCANCEL = "ALL";*/
	
	public static final String ADDANCILLARY_SI_ERR  = "TOO MANY SPECIAL EQUIPMENT ITEMS REQUESTED";
	public static final String CANCEL_SI_ERR = "SI-NotSupported";
	
	public static final String JSON_PROP_QTY = "quantity";
}
