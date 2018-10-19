package com.coxandkings.travel.bookingengine.orchestrator.acco;

import com.coxandkings.travel.bookingengine.enums.PassengerType;
import com.coxandkings.travel.bookingengine.utils.Constants;

public interface AccoConstants extends Constants{
	

	public static final String JSON_PROP_ACCOMODATIONARR = "accommodationInfo";
	
	//public static final String JSON_PROP_COUNTRYCODE = "countryCode";
	//public static final String JSON_PROP_CITYCODE = "cityCode";
	public static final String JSON_PROP_CHKIN = "checkIn";
	public static final String JSON_PROP_CHKOUT = "checkOut";
	public static final String JSON_PROP_ACCOSUBTYPEARR = "accommodationSubTypes";
	public static final String JSON_PROP_ACCOSUBTYPE = "accommodationSubType";
	public static final String JSON_PROP_REQUESTEDROOMARR = "roomConfig";
	public static final String JSON_PROP_ADTCNT = "adultCount";
	public static final String JSON_PROP_CHDAGESARR = "childAges";
	public static final String JSON_PROP_PAXNATIONALITY = "paxNationality";

	public static final String JSON_PROP_ROOMSTAYARR = "roomStay";
	public static final String JSON_PROP_ROOMPRICE = "totalPriceInfo";
	public static final String JSON_PROP_NIGHTLYPRICEARR = "nightlyPriceInfo";
	public static final String JSON_PROP_SUPPROOMPRICE = "supplierTotalPriceInfo";
	public static final String JSON_PROP_SUPPNIGHTLYPRICEARR = "supplierNightlyPriceInfo";
	public static final String JSON_PROP_BOOKPRICE= "bookingPriceInfo";
	public static final String JSON_PROP_SUPPBOOKPRICE= "supplierBookingPriceInfo";
	public static final String JSON_PROP_ROOMINFO = "roomInfo";
	
	//public static final String JSON_PROP_TOTALTAX = "taxes";
	//public static final String JSON_PROP_TAXBRKPARR = "tax";
	//public static final String JSON_PROP_AMOUNT = "amount";
	//public static final String JSON_PROP_CCYCODE = "currencyCode";
	//public static final String JSON_PROP_TAXCODE = "taxCode";
	public static final String JSON_PROP_EFFECTIVEDATE = "effectiveDate";
	
	public static final String JSON_PROP_ROOMTYPEINFO = "roomTypeInfo";
	public static final String JSON_PROP_ROOMTYPECODE = "roomTypeCode";
	public static final String JSON_PROP_ROOMTYPENAME = "roomTypeName";
	public static final String JSON_PROP_ROOMCATEGCODE = "roomCategoryCode";
	public static final String JSON_PROP_ROOMCATEGNAME = "roomCategoryName";
	public static final String JSON_PROP_ROOMREF = "roomRef";

	public static final String JSON_PROP_HOTELINFO = "hotelInfo";
	public static final String JSON_PROP_HOTELCODE = "hotelCode";
	public static final String JSON_PROP_HOTELNAME = "hotelName";
	public static final String JSON_PROP_HOTELREF = "hotelRef";
	
	public static final String JSON_PROP_RATEPLANINFO = "ratePlanInfo";
	public static final String JSON_PROP_RATEPLANCODE = "ratePlanCode";
	public static final String JSON_PROP_RATEPLANNAME = "ratePlanName";
	public static final String JSON_PROP_RATEPLANREF = "ratePlanRef";
	public static final String JSON_PROP_RATEBOOKINGREF = "bookingRef";

	public static final String JSON_PROP_OCCUPANCYARR = "occupancyInfo";
	public static final String JSON_PROP_MAXOCCUPANCY ="maxOccupancy";
	public static final String JSON_PROP_MINOCCUPANCY ="minOccupancy";
	public static final String JSON_PROP_MAXAGE ="maxAge";
	public static final String JSON_PROP_MINAGE ="minAge";
	
	public static final String JSON_PROP_MEALINFO = "mealInfo";
	public static final String JSON_PROP_MEALCODE = "mealCode";
	public static final String JSON_PROP_MEALNAME = "mealName";
	
	//public static final String JSON_PROP_PAXINFOARR = "paxInfo";
	//public static final String JSON_PROP_PAXTYPE = "paxType";
	//public static final String JSON_PROP_LEADPAX_IND = "isLeadPax";
	//public static final String JSON_PROP_FIRSTNAME = "firstName";
	//public static final String JSON_PROP_MIDDLENAME = "middleName";
	//public static final String JSON_PROP_SURNAME =  "surname";
	//public static final String JSON_PROP_PAXTITLE= "title";
	//public static final String JSON_PROP_DOB= "dob";
	//public static final String JSON_PROP_CONTACTDTLS= "contactDetails";
	//public static final String JSON_PROP_ADDRDTLS= "address";
	//public static final String JSON_PROP_CTRYACESCODE= "countryAccessCode";
	//public static final String JSON_PROP_CONTCTNUMBR= "contactNumber";
	//public static final String JSON_PROP_EMAIL= "email";
	//public static final String JSON_PROP_CITYNAME= "cityName";
	//public static final String JSON_PROP_ZIPCODE= "zipCode";
	//public static final String JSON_PROP_COUNTRYNAME= "countryName";
	//public static final String JSON_PROP_ADDRESSLINES= "addressLines";
	
	public static final String JSON_PROP_REFERENCESARR = "references";
	public static final String JSON_PROP_REFCODE = "refCode";
	public static final String JSON_PROP_REFNAME = "refName";
	public static final String JSON_PROP_REFVALUE = "refValue";
	public static final String JSON_PROP_SUPPBOOKREFERENCES= "supplierBookReferences";
	public static final String JSON_PROP_SUPPROOMREFERENCES= "supplierRoomReferences";
	public static final String JSON_PROP_SUPPROOMINDEX= "supplierRoomIndex";
	
	public static final String JSON_PROP_ROOMINDEX = "requestedRoomIndex";
	public static final String JSON_PROP_AVAILSTATUS = "availabilityStatus";
	public static final String JSON_PROP_PAYATHOTEL = "payAtHotel";
	
	//public static final String JSON_PROP_BOOKREFID = "bookRefId";
	//public static final String JSON_PROP_BOOKREFTYPE = "bookRefType";
	//public static final String JSON_PROP_CANCELID = "Id";
	//public static final String JSON_PROP_TYPE = "type";
	//public static final String JSON_PROP_CANCELSTATUS = "cancelStatus";
	//public static final String JSON_PROP_CANCELRULE = "CancelRule";
	//public static final String JSON_PROP_UNIQUEID = "UniqueID";
	//public static final String JSON_PROP_CANCELRESERVATION = "cancelReservation";
	
	//public static final String JSON_PROP_PERSONALDETAILS = "personalDetails";
	//public static final String JSON_PROP_BIRTHDATE = "birthDate";
	//public static final String JSON_PROP_PRODUCT = "product";
	//public static final String JSON_PROP_BOOKID = "bookID";
	public static final String JSON_PROP_SUPPTRANRULES = "cnk.acco_commercialscalculationengine.suppliertransactionalrules.Root";
	public static final String JSON_PROP_CLIENTTRANRULES = "cnk.acco_commercialscalculationengine.clienttransactionalrules.Root";
	public static final String JSON_PROP_HOTELDETAILS = "hotelDetails";
	public static final String JSON_PROP_ROOMDETAILS = "roomDetails";
	//public static final String JSON_PROP_COMMONELEMS = "commonElements";
	//public static final String JSON_PROP_TOTALFARE = "totalFare";
	//public static final String JSON_PROP_FAREBRKUP = "fareBreakUp";
	//public static final String JSON_PROP_BASEFARE = "baseFare";
	//public static final String JSON_PROP_TAXDETAILS = "taxDetails";
	//public static final String JSON_PROP_TAXVALUE = "taxValue";
	//public static final String JSON_PROP_TAXNAME = "taxName";
	
	public static final String JSON_PROP_ADDCOMMDETAILS = "additionalCommercialDetails";
	//public static final String JSON_PROP_BRAND = "brand";
	//public static final String JSON_PROP_CHAIN = "chain";
	//public static final String JSON_PROP_COUNTRY= "country";
	//public static final String JSON_PROP_PRODCTNAME= "productName";
	//public static final String JSON_PROP_NUMOFNIGHTS= "noOfNights";
	//public static final String JSON_PROP_NAME= "name";
	//public static final String JSON_PROP_POINTOFSALE= "pointOfSale";
	//public static final String JSON_PROP_ROOMCATEGRY= "roomCategory";
	//public static final String JSON_PROP_FARENAME= "fareName";
	//public static final String JSON_PROP_FAREVALUE= "fareValue";
	//public static final String JSON_PROP_FAREDTLS= "fareDetails";
	//public static final String JSON_PROP_NIGHTDTLS= "nightDetails";
	//public static final String JSON_PROP_COMPANYDTLS= "companyDetails";
	//public static final String JSON_PROP_CLIENTDTLS= "clientDetails";
	//public static final String JSON_PROP_PRODUCTDTLS= "productDetails";
	//public static final String JSON_PROP_CLIENTCALLBACK = "clientCallbackAddress";
	
	//public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");

	public static final String PRODUCT_ACCO = "ACCO";
    public static final String NS_ACCO = "http://www.coxandkings.com/integ/suppl/acco";
	public static final String Pax_ADT = PassengerType.ADULT.toString();
	public static final String Pax_CHD = PassengerType.CHILD.toString();
	public static final String Pax_ADT_ID = "10";
	public static final String Pax_CHD_ID = "8";
	public static final String DEF_SUPPID = "";
	public static final char KEYSEPARATOR = '|';
	
}
