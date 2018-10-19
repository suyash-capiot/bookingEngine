package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

import com.coxandkings.travel.bookingengine.utils.Constants;

public interface ActivityConstants extends Constants{
	
	public static final String JSON_PROP_TOTALCOST = "totalCost";
	public static final String JSON_PROP_PRODUCTS = "products";
	public static final String JSON_PROP_SUPPBOOKREF = "supp_booking_reference";
	public static final String JSON_PROP_ORDER_SUPPPRICEINFO = "orderSupplierPriceInfo";
	
	
	public static final String JSON_PROP_TYPE_DELETEPAX  = "DELETEPAX";
	public static final String JSON_PROP_TYPE_CHANGEPOS = "CHANGEPOS";
	public static final String JSON_PROP_CLIENTCURRENCY = "clientCurrency";
	public static final String JSON_PROP_SERVICEFEE = "serviceFee";
	public static final String JSON_PROP_TIMESLOT = "timeSlot";
	
//	public static final String JSON_PROP_TYPE = "type";
//	public static final String JSON_PROP_ID = "ID";
	
	
	
	
	
	
	
//	------------------------------------------------------------------------------------------------------------------------------------------------------
	
	
	public static final String PRODUCT = "SIGHTSEEING";
	public static final String NS_SIGHTSEEING = "http://www.coxandkings.com/integ/suppl/sightseeing";
	public static final String NS_OTA = "http://www.opentravel.org/OTA/2003/05";
	public static DateTimeFormatter mDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	public static DateTimeFormatter mDateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
	
	
	public static final String JSON_PROP_RESHEADER = "responseHeader";
	public static final String JSON_PROP_RESBODY = "responseBody";
	public static final String JSON_PROP_REQHEADER = "requestHeader";
	public static final String JSON_PROP_REQBODY = "requestBody";

	public static final String JSON_PROP_SESSIONID = "sessionID";
	public static final String JSON_PROP_TRANSACTID = "transactionID";
	public static final String JSON_PROP_USERID = "userID";
	
	public static final String JSON_PROP_CLIENTCONTEXT = "clientContext";
	public static final String JSON_PROP_CLIENTMARKET = "clientMarket";
	public static final String JSON_PROP_CLIENTCCY = "clientCurrency";
	public static final String JSON_PROP_CLIENTID = "clientID";	
	public static final String JSON_PROP_CLIENTLANG = "clientLanguage";
	public static final String JSON_PROP_CLIENTTYPE = "clientType";
	public static final String JSON_PROP_CLIENTPOS = "pointOfSale";

	//------Body-----
	public static final String JSON_PROP_ACTIVITYINFO = "activityInfo";
	public static final String JSON_PROP_TOURACTIVITYINFO = "tourActivityInfo";
	public static final String SUPPLIER_ID = "supplierID";
	public static final String JSON_PROP_SUPPLIERPRODUCTCODE = "supplierProductCode";
	public static final String JSON_PROP_SUPPLIERBRANDCODE = "supplierBrandCode";
	
	public static final String JSON_PROP_TOURLANGUAGE = "tourLanguage";
	public static final String JSON_PROP_CODE = "code";
	public static final String JSON_PROP_LANGUAGELISTCODE="languageListCode";
	public static final String JSON_PROP_VALUE = "value";
	
	public static final String JSON_PROP_COUNTRYCODE = "countryCode";
	public static final String JSON_PROP_CITYCODE = "cityCode";
	
	public static final String JSON_PROP_STARTDATE = "startDate";
	public static final String JSON_PROP_ENDDATE = "endDate";
	
	public static final String JSON_PROP_TIME_SLOT_DETAILS = "timeSlotDetails";
	public static final String JSON_PROP_STARTTIME = "startTime";
	public static final String JSON_PROP_ENDTIME = "endTime";
	
	public static final String JSON_PROP_PAXINFO = "paxInfo";
	public static final String JSON_PROP_PAXTYPE = "paxType";
	public static final String JSON_PROP_QUANTITY = "quantity";
	
	public static final String JSON_PROP_PARTICIPANT_INFO = "participantInfo";
	public static final String JSON_PROP_ISLEADPAX = "isLeadPax";
	public static final String JSON_PROP_QUALIFIERINFO = "qualifierInfo";
	public static final String JSON_PROP_PERSONNAME = "personName";
	public static final String JSON_PROP_NAME_PREFIX = "namePrefix";
	public static final String JSON_PROP_NAME_TITLE = "nameTitle";
	public static final String JSON_PROP_GIVEN_NAME = "givenName";
	public static final String JSON_PROP_MIDDLE_NAME = "middleName";
	public static final String JSON_PROP_SURNAME = "surname";
	public static final String JSON_PROP_DOB = "DOB";
	public static final String JSON_PROP_CONTACTDETAILS = "contactDetails";
	public static final String JSON_PROP_CTRYACESCODE = "countryAccessCode";
	public static final String JSON_PROP_PHONE_NUMBER = "phoneNumber";
	public static final String JSON_PROP_TELEPHONE = "telephone";
	public static final String JSON_PROP_ADDRESS = "address";
	public static final String JSON_PROP_BLDG_ROOM = "bldgRoom";
	public static final String JSON_PROP_ADDRESSLINE="addressLine";
	public static final String JSON_PROP_EMAIL = "email";
	
	public static final String JSON_PROP_ACT_SUPP_SUM_PRICE = "activitySupplierSummaryPrice";
	public static final String JSON_PROP_PAX_PRICE_INFO = "paxPriceInfo";
	public static final String JSON_PROP_ACT_TOT_PRICING_INFO = "activityTotalPricingInfo";
	public static final String JSON_PROP_CLIENT_ENTITY_PAXWISE_COMM = "clientEntityPaxWiseCommercials";
	public static final String JSON_PROP_ACT_WISE_PRICING_FLAG = "activityWisePricing";
	public static final String JSON_PROP_ACT_SUM_PRICE = "activitySummaryPrice";
	
	public static final String JSON_PROP_BASICINFO = "basicInfo";
	public static final String JSON_PROP_SUPPLIER_DETAILS="supplier_Details";
	public static final String JSON_PROP_REFERENCE = "reference";
	public static final String JSON_PROP_NAME = "name";
	public static final String JSON_PROP_ID = "ID";
	public static final String JSON_PROP_RATEKEY = "rateKey";
	public static final String JSON_PROP_UNIQUEKEY = "uniqueKey";
	
	public static final String JSON_PROP_CANCELLATION_POLICY = "cancellationPolicy";
	public static final String JSON_PROP_UNIT = "unit";
	public static final String JSON_PROP_FROM_VALUE = "fromValue";
	public static final String JSON_PROP_CHARGE_TYPE = "chargeType";
	public static final String JSON_PROP_RATE = "rate";
	
	public static final String JSON_PROP_SHIPPINGDETAILS = "shipping_Details";
	public static final String JSON_PROP_SHIPPINGAREAS = "shippingAreas";
	public static final String JSON_PROP_AREAID = "areaID";
	public static final String JSON_PROP_COST = "cost";
	public static final String JSON_PROP_DETAILS = "details";
	public static final String JSON_PROP_CURRENCY = "currency";
	public static final String JSON_PROP_OPTION_NAME = "optionName";
	public static final String JSON_PROP_TOTAL_COST = "totalCost";
	
	public static final String JSON_PROP_ANSWERS = "answers";
	public static final String JSON_PROP_QUESTION_ID = "questionID";
	public static final String JSON_PROP_QUESTION_TEXT = "questionText";
	public static final String JSON_PROP_ANSWER_TYPE = "answerType";
	public static final String JSON_PROP_ANSWER_EXAMPLE = "answerExample";
	public static final String JSON_PROP_REQUIRED_FLAG = "requiredFlag";
	public static final String JSON_PROP_EXTRA_INFO = "extraInfo";
	public static final String JSON_PROP_ANSWER = "answer";
	
	public static final String JSON_PROP_POS = "POS";
	public static final String JSON_PROP_SOURCE = "source";
	public static final String JSON_PROP_ISO_CURRENCY = "ISOCurrency";
	public static final String JSON_PROP_AGENT_NAME = "agent_Name";
	public static final String JSON_PROP_COUNTRY = "country";
	
	public static final String JSON_PROP_SCHEDULE = "schedule";
	public static final String JSON_PROP_START = "start";
	public static final String JSON_PROP_END = "end";
	
	public static final String JSON_PROP_PICKUPDROPOFF = "pickupDropOff";
	public static final String JSON_PROP_DATE_TIME = "dateTime";
	public static final String JSON_PROP_LOCATION_NAME = "locationName";
	
	public static final String JSON_PROP_PARTICIPANT = "participant";
	public static final String JSON_PROP_ALLOWINFANTS = "allowInfants";
	public static final String JSON_PROP_MINADULTAGE = "minAdultAge";
	public static final String JSON_PROP_MAXINFANTAGE = "maxInfantAge";
	public static final String JSON_PROP_ALLOWCHILDREN = "allowChildren";
	public static final String JSON_PROP_MININDIVIDUALS = "minIndividuals";
	public static final String JSON_PROP_MAXADULTAGE = "maxAdultAge";
	public static final String JSON_PROP_MAXCHILDAGE = "maxChildAge";
	public static final String JSON_PROP_MAXINDIVIDUALS = "maxIndividuals";
	public static final String JSON_PROP_MINCHILDAGE = "minChildAge";
	public static final String JSON_PROP_MININFANTAGE = "minInfantAge";
	
	
	
	

	
	
	
	
	public static final String PRODUCT_SIGHTSEEING = "";

	public static final char KEYSEPARATOR = '|';
	
	
	public static final String PRODUCT_CATEGORY = "Activities";
	public static final String PRODUCT_SUBCATEGORY = "Tours & Sightseeing";

	
	
	public static final String JSON_PROP_AMOUNT = "amount";
	public static final String JSON_PROP_CCYCODE = "currencyCode";
	public static final String JSON_PROP_COUNTRYNAME = "countryName";
	public static final String JSON_PROP_BOOKREFID = "bookRefId";
	public static final String JSON_PROP_TYPE = "type";
	public static final String JSON_PROP_PRODUCT = "product";
	public static final String JSON_PROP_DURATION = "duration";
	public static final String JSON_PROP_DESCRIPTION = "description";
	public static final String JSON_PROP_LONGDESCRIPTION = "longDescription";
	public static final String JSON_PROP_SHORTDESCRIPTION = "shortDescription";
	public static final String JSON_PROP_AVAILABILITYSTATUS = "availabilty_status";
	public static final String JSON_PROP_PRICING = "pricing";
	public static final String JSON_PROP_RESERVATIONS = "reservations";
	public static final String JSON_PROP_COMMERCIALNAME = "commercialName";
	public static final String JSON_PROP_COMMERCIALTYPE = "commercialType";
	public static final String JSON_PROP_COMMERCIALAMOUNT = "commercialAmount";
	public static final String JSON_PROP_MARKUPCOMMERCIALDETAILS = "markUpCommercialDetails";
	public static final String JSON_PROP_ENTITYNAME = "entityName";
	public static final String JSON_PROP_ENTITYCOMMERCIALS = "entityCommercials";
	public static final String JSON_PROP_CLIENTCOMMERCIALS = "clientCommercials";
	public static final String JSON_PROP_SUPPLIERCOMMERCIALS = "supplierCommercials";
	public static final String JSON_PROP_DAYS = "days";
	public static final String JSON_PROP_OPERATIONTIMES = "operationTimes";
	public static final String JSON_PROP_CATEGORY = "category";
	public static final String JSON_PROP_CATEGORYANDTYPE = "categoryAndType";
	public static final String JSON_PROP_SUPPPRICEINFO = "suppPriceInfo";
	public static final String JSON_PROP_TOTALPRICE = "totalPrice";
	public static final String JSON_PROP_ADULT = "Adult";
	public static final String JSON_PROP_CHILD = "Child";
	public static final String JSON_PROP_PARTICIPANTCATEGORY = "participantCategory";
	public static final String JSON_PROP_SUMMARY = "SUMMARY";
	public static final String JSON_PROP_EXTRA = "extra";
	public static final String JSON_PROP_SUPPLIEROPERATOR = "supplieroperator";
	public static final String JSON_PROP_LOCATION = "location";
	public static final String JSON_PROP_COMMISIONINFO = "commisionInfo";
	public static final String JSON_PROP_MEETINGLOCATION = "meetingLocation";
	public static final String JSON_PROP_PICKUPIND = "pickupInd";
	public static final String JSON_PROP_OTHERINFO = "otherInfo";
	public static final String JSON_PROP_STATEPROV = "stateProv";
	public static final String JSON_PROP_LONGITUDE="longitude";
	public static final String JSON_PROP_LATITUDE="latitude";
	public static final String JSON_PROP_POSITION="position";
	public static final String JSON_PROP_PARTICIPANT_CATEGORY_ID = "participantCategoryID";
	public static final String JSON_PROP_STATUS = "status";
	public static final String JSON_PROP_CONFIRMATION = "confirmation";
	public static final String JSON_PROP_TOTAL_PRICE_INFO = "totalPriceInfo";
	public static final String JSON_PROP_COMMERCIAL_HEAD_NAME = "commercialHeadName";
	public static final String JSON_PROP_COMMERCIAL_HEAD = "commercialHead";
	public static final String JSON_PROP_ENTITY_DETAILS = "entityDetails";
	public static final String JSON_PROP_TOTAL_FARE = "totalFare";
	public static final String JSON_PROP_COMMERCIAL_DETAILS = "commercialDetails";
	public static final String JSON_PROP_CCE_ACTIVITY_DETAILS = "activityDetails";
	public static final String JSON_PROP_CCE_PRICING = "pricingDetails";
	public static final String JSON_PROP_BUSINESS_RULE_INTAKE = "businessRuleIntake";
	public static final String JSON_PROP_CCE_FAREBREAKUP = "fareBreakup";
	public static final String JSON_PROP_PRICE_BREAKUP = "priceBreakup";
	public static final String JSON_PROP_CONTRACT_VALIDITY = "contractValidity";
	public static final String JSON_PROP_SUPPLIER_MARKET = "supplierMarket";
	public static final String JSON_PROP_SUPPLIER_NAME = "supplierName";
	public static final String JSON_PROP_OPERATION_NAME = "operationName";
	
	
	public static final String JSON_PROP_CLIENTCOMMENTITYDTLS ="clientCommercialsEntityDetails";
	public static final String JSON_PROP_COMMENTITYID = "commercialEntityID";
	public static final String JSON_PROP_PARENTCLIENTID = "parentClientID";
	public static final String JSON_PROP_COMMENTITYTYPE = "commercialEntityType";
	public static final String JSON_PROP_COMMERCIALCURRENCY = "commercialCurrency";
	public static final String COMM_TYPE_RECEIVABLE = "Receivable";
	public static final String JSON_PROP_COMMTYPE = "commercialType";
	
	public static final String XPATH_REQUESTHEADER_USERID = "./sig1:RequestHeader/com:UserID";
	public static final String XPATH_REQUESTHEADER_TRANSACTIONID = "./sig1:RequestHeader/com:TransactionID";
	public static final String XPATH_REQUESTHEADER_SESSIONID = "./sig1:RequestHeader/com:SessionID";
	public static final String XPATH_REQUESTHEADER_SUPPLIERCREDENTIALSLIST = "./sig1:RequestHeader/com:SupplierCredentialsList";
	public static final String XPATH_SUPPLIERCREDENTIALS_CREDENTIALSNAME = "./sig1:RequestHeader/com:SupplierCredentialsList/com:SupplierCredentials/com:Credentials/@name";

	
	public static final String ERROR_MSG_NULL_RESPONSE_SI = "Null response received from SI";
	public static final String STATUS_ERROR = "{\"status\": \"ERROR\"}";
	
	
	public static final String JSON_PROP_TYPE_ADDPAX = "ADDPASSENGER";
	public static final String JSON_PROP_TYPE_CANCELPAX  = "CANCELPASSENGER";
	public static final String JSON_PROP_TYPE_UPDATEPAX = "UPDATEPASSENGER";
	public static final String JSON_PROP_TYPE_FULLCANCEL  = "FULLCANCELLATION";
	
	public static final String JSON_PROP_AMEND = "amend";
	public static final String JSON_PROP_REQUEST_TYPE = "requestType";
	
	
	public static final String JSON_PROP_SUPPLIER_CHARGES_CURRENCY_CODE = "supplierChargesCurrencyCode";
	public static final String JSON_PROP_SUPPLIER_CHARGES = "supplierCharges";
	public static final String JSON_PROP_COMPANY_CHARGES_CURRENCY_CODE = "companyChargesCurrencyCode";
	public static final String JSON_PROP_COMPANY_CHARGES = "companyCharges";
	public static final String JSON_PROP_SUPP_PRICE_INFO = "suppPriceInfo";
	public static final String JSON_PROP_CURRENCY_CODE = "currencyCode";
	public static final String JSON_PROP_TOTAL_PRICE = "totalPrice";
	public static final String JSON_PROP_PARTICIPANT_CATEGORY = "participantCategory";
	
	public static final String JSON_PROP_ENTITY_ID = "entityId";
	public static final String JSON_PROP_ENTITY_NAME = "entityName";
	public static final String JSON_PROP_INSTANCE = "instance";
	
	public static final String JSON_PROP_CANCEL = "cancel";
	
//	public static final String JSON_PROP_TYPE = "type";
//	public static final String JSON_PROP_ID = "ID";
	
	public static final String JSON_PROP_OPERATIONNAME = "operationName";
	public static final String JSON_PROP_HEADER = "header";
	public static final String JSON_PROP_COMPANYNAME = "companyName";
	public static final String JSON_PROP_COMPANYMKT = "companyMarket";
	public static final String JSON_PROP_STATE = "state";
	public static final String JSON_PROP_CLIENTCAT = "clientCategory";
	public static final String JSON_PROP_CLIENTSUBCAT = "clientSubCategory";
	public static final String JSON_PROP_CLIENTNAME = "clientName";
	public static final String JSON_PROP_PRODCATEG = "productCategory";
	public static final String JSON_PROP_PRODCATEGSUBTYPE = "productCategorySubType";
	public static final String PROD_CATEG_ACTIVITY = "Activities";
	public static final String JSON_PROP_VALIDITY = "validity";
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");
	public static final String JSON_PROP_SUPPPRICINGDTLS = "supplierPricingDetails";
	public static final String JSON_PROP_GSTCALCINTAKE = "gstCalculationIntake";
	public static final String TARGET_SYSTEM_TAXENGINE = "TAXENG";
	public static final String BRMS_STATUS_TYPE_FAILURE = "FAILURE";
	public static final String BRMS_STATUS_TYPE_SUCCESS = "SUCCESS";
	public static final String JSON_PROP_SUPPRATETYPE = "supplierRateType";
	public static final String JSON_PROP_SUPPRATECODE = "supplierRateCode";
	public static final String JSON_PROP_ISTHIRDPARTY = "isThirdParty";
	public static final String JSON_PROP_RATEFOR = "rateFor";
	
	public static final String JSON_PROP_TRAVELDTLS = "travelDetails";
	public static final String JSON_PROP_LOCOFSALE = "los";
	public static final String JSON_PROP_PTOFSALE = "pos";
	public static final String JSON_PROP_TRAVELLINGCOUNTRY = "travellingCountry";
	public static final String JSON_PROP_TRAVELLINGSTATE = "travellingState";
	public static final String JSON_PROP_TRAVELLINGCITY = "travellingCity";
	public static final String JSON_PROP_FAREBRKUP = "fareBreakUp";
	public static final String JSON_PROP_FAREDETAILS = "fareDetails";
	public static final String JSON_PROP_ISMARKEDUP = "isMarkedUp";
	public static final String JSON_PROP_ADDCHARGES = "additionalCharges";
	public static final String JSON_PROP_SELLINGPRICE = "sellingPrice";
	public static final String JSON_PROP_PROD = "product";
	public static final String JSON_PROP_ISPKG = "isPackage";
	public static final String JSON_PROP_APPLIEDTAXDTLS = "appliedTaxDetails";
	public static final String JSON_PROP_TAXVALUE = "taxValue";
	public static final String JSON_PROP_TAXCODE = "taxCode";
	public static final String JSON_PROP_TAXPCT = "taxPercent";
	public static final String JSON_PROP_TAXNAME = "taxName";
	public static final String JSON_PROP_TAXPERCENTAGE = "taxPercentage";
	public static final String JSON_PROP_HSNCODE = "hsnCode";
	public static final String JSON_PROP_SACCODE = "sacCode";
	public static final String JSON_PROP_COMPANYTAX = "companyTax";
	public static final String JSON_PROP_COMPANYTAXES = "companyTaxes";
	
	
	public static final String JSON_PROP_SBU = "sbu";
	public static final String JSON_PROP_BU = "bu";
	public static final String JSON_PROP_DIVISION = "division";
	public static final String JSON_PROP_SALESOFFICELOC = "salesOfficeLoc";
	public static final String JSON_PROP_SALESOFFICE = "salesOffice";
	public static final String JSON_PROP_COMPANYDETAILS = "companyDetails";
	public static final String JSON_PROP_CLIENTGROUP = "clientGroup";
	public static final String JSON_PROP_NATIONALITY = "nationality";
	public static final String JSON_PROP_CLIENTDETAILS = "clientDetails";
	public static final String JSON_PROP_PRODNAME = "productName";
	public static final String JSON_PROP_CITY = "city";
	public static final String JSON_PROP_SEGMENT = "segment";
	public static final String JSON_PROP_IATANBR = "iatanumber";	// This is for BRMS
	
	
	public static final String JSON_PROP_OFFERDETAILSSET = "offerDetailsSet";
	public static final String JSON_PROP_OFFERS = "offers";
	public static final String JSON_PROP_PSGRDETAILS = "passengerDetails";
	public static final String JSON_PROP_PSGRTYPE = "passengerType";
	public static final String JSON_PROP_OFFERID = "offerID";
	
	
	public static final String JSON_PROP_MARKUPCOMMDTLS = "markUpCommercialDetails";
	public static final String JSON_PROP_ADDCOMMDETAILS = "additionalCommercialDetails";
	public static final String JSON_PROP_FAREBREAKUP = "fareBreakUp";
	public static final String JSON_PROP_TAXES = "taxes";
	public static final String JSON_PROP_TAX = "tax";
	public static final String JSON_PROP_TOTAL = "total";
	public static final String JSON_PROP_FEES = "fees";
	public static final String JSON_PROP_FEE = "fee";
	public static final String JSON_PROP_PAXTYPEFARES = "paxTypeFares";
	public static final String JSON_PROP_CLIENTCOMMITININFO = "clientCommercialItinInfo";
	public static final String JSON_PROP_ORDERDETAILS = "orderDetails";
	public static final String JSON_PROP_ACTIVITYDETAILS ="activityDetails";
	
	
	
	
	
	
	
	
	
}
