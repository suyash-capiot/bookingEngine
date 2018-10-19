package com.coxandkings.travel.bookingengine.utils;

public interface PartPaymentConstants {
	
	public static final String JSON_PROP_REQUESTBODY = "requestBody";
	public static final String JSON_PROP_BOOKID = "bookID";
	public static final String JSON_PROP_REQUESTHEADER = "requestHeader";
	public static final String JSON_PROP_CLIENTCONTEXT = "clientContext";
	public static final String JSON_PROP_CLIENTID = "clientID";
	public static final String JSON_PROP_CLIENTTYPE = "clientType";
	public static final String JSON_PROP_CLIENTMARKET = "clientMarket";
	public static final String JSON_PROP_RESPONSEBODY = "responseBody";
	public static final String JSON_PROP_PAYMENTINFO = "paymentInfo";
	public static final String JSON_PROP_PAYMENTAMOUNT = "totalAmount";
	
	public static final String JSON_PROP_ACCO_ACCOINFO = "accommodationInfo";
	public static final String JSON_PROP_ACCO_ROOMSTAY = "roomStay";
	public static final String JSON_PROP_ACCO_ROOMCONFIG = "roomConfig";
	public static final String JSON_PROP_ACCO_ROOMINFO = "roomInfo";
	public static final String JSON_PROP_ACCO_ROOMTYPEINFO = "roomTypeInfo";
	public static final String JSON_PROP_ACCO_ROOMTYPENAME = "roomTypeName";
	public static final String JSON_PROP_ACCO_ROOMCATEGORYNAME = "roomCategoryName";
	public static final String JSON_PROP_ACCO_TOTALPRICEINFO = "totalPriceInfo";
	public static final String JSON_PROP_ACCO_SUPPLIERREF = "supplierRef";
	public static final String JSON_PROP_ACCO_AMOUNT = "amount";
	public static final String JSON_PROP_ACCO_ACCOSUBTYPE = "accommodationSubType";
	public static final String JSON_PROP_ACCO_HOTELINFO = "hotelInfo";
	public static final String JSON_PROP_ACCO_HOTELCODE = "hotelCode";
	public static final String JSON_PROP_ACCO_CHECKIN = "checkIn";
	
	public static final String CHAIN = "chain";
	public static final String CITY = "city";
	public static final String COUNTRY = "country";
	public static final String NAME = "name";
	public static final String BRAND = "brand";
	public static final String CONTINENT = "continent";
	public static final String ACCOMMODATION = "Accommodation";
	public static final String ENDVALIDITY = "End Of Validity";
	public static final String MONTHLY = "Monthly";
	public static final String QUARTERLY = "Quarterly";
	public static final String FORTNIGHTLY = "Fortnightly";
	public static final String HALFYEARLY = "Half Yearly";
	public static final String WEEKLY = "Weekly";
	public static final String YEARLY = "Yearly";
	
	public static final String AIR_COUNTRY = "country";
	public static final String AIR_CITY = "city";
	public static final String AIR_AIRLINENAME = "name";
	public static final String AIR_PRODUCTCAT = "Transportation";
	public static final String AIR_PRODUCTCATSUBTYPE = "Flight";
	
	public static final String JSON_PROP_AIR_TRIPTYPE = "tripType";
	public static final String JSON_PROP_AIR_PRICEDITINERARY = "pricedItinerary";
	public static final String JSON_PROP_AIR_AIRITINERARYPRICINGINFO = "airItineraryPricingInfo";
	public static final String JSON_PROP_AIR_ITINTOTALFARE = "itinTotalFare";
	public static final String JSON_PROP_AIR_ITINAMOUNT = "amount";
	public static final String JSON_PROP_AIR_PAXTYPEFARES = "paxTypeFares";
	public static final String JSON_PROP_AIR_PAXTYPETOTALFARE = "totalFare";
	public static final String JSON_PROP_AIR_PAXTYPEAMOUNT = "amount";
	public static final String JSON_PROP_AIR_SUPPLIERREF = "supplierRef";
	public static final String JSON_PROP_AIR_AIRITINERARY = "airItinerary";
	public static final String JSON_PROP_AIR_ORIGINDESTINATIONOPTIONS = "originDestinationOptions";
	public static final String JSON_PROP_AIR_FLIGHTSEGMENT = "flightSegment";
	public static final String JSON_PROP_AIR_OPERATINGAIRLINE = "operatingAirline";
	public static final String JSON_PROP_AIR_AIRLINECODE = "airlineCode";
	public static final String JSON_PROP_AIR_FLIGHTNUMBER = "flightNumber";
	public static final String JSON_PROP_AIR_CABINTYPE = "cabinType";
	public static final String JSON_PROP_AIR_ORIGINLOCATION = "originLocation";
	public static final String JSON_PROP_AIR_DESTINATIONLOCATION = "destinationLocation";
	public static final String JSON_PROP_AIR_DEPARTUREDATE = "departureDate";
	public static final String JSON_PROP_AIR_ARRIVALDATE = "arrivalDate";
	
	public static final String MDM_PART_PRODUCTACCO = "productAccomodation";
	public static final String MDM_PART_PRODUCTFLIGHT = "productsFlight";
	public static final String MDM_PART_MODE = "mode";
	public static final String MDM_PART_PAYMENTSCHEDULE = "paymentSchedule";
	public static final String MDM_PART_PAYMENTSCHDTYPE = "paymentSchdType";
	public static final String MDM_PART_PAYMENTDUEDATE = "paymentDueDate";
	public static final String MDM_PART_PAYMENTDUE = "paymentDue";
	public static final String MDM_PART_FROMBILLINGAMOUNT = "fromBillingAmt";
	public static final String MDM_PART_TOBILLINGAMOUNT = "toBillingAmt";
	public static final String MDM_PART_BILLINGAMTASPER = "billingAmtAsPer";
	public static final String MDM_PART_BILLINGAMTASPER_PERPAX = "Per Passenger";
	public static final String MDM_PART_BILLINGAMTASPER_PERTRANS = "Per Transaction";
	public static final String MDM_PART_CHAIN = "chain";
	public static final String MDM_PART_BRAND = "brand";
	public static final String MDM_PART_CITY = "city";
	public static final String MDM_PART_CONTINENT = "continent";
	public static final String MDM_PART_COUNTRY = "country";
	public static final String MDM_PART_PRODUCTNAME= "productName";
	public static final String MDM_PART_MODE_INCLUDE = "inclusion";
	public static final String MDM_PART_MODE_EXCLUDE = "exclusion";
	public static final String MDM_PART_DURATIONTYPE = "durationType";
	public static final String MDM_PART_REFUNDABLE = "refundable";
	public static final String MDM_PART_COUNT = "count";
	public static final String MDM_PART_VALUE = "value";
	public static final String MDM_PART_VALUE_MODE = "mode";
	public static final String MDM_PART_PERCENTAGE = "percentage";
	public static final String MDM_PART_AMOUNT = "amount";
	public static final String MDM_PART_CURRENCYAMT = "currencyAmount";
	public static final String MDM_PART_PRICECOMPONENT = "priceComponent";
	public static final String MDM_PART_DEPOSIT = "deposit";
	public static final String MDM_PART_PAYMENTDUEDATE_FROMBOOKDATE = "From Booking Date";
	public static final String MDM_PART_PAYMENTDUEDATE_PRIORTRAVELDATE = "Prior To Travel Date";
	public static final String MDM_PART_PAYMENTDUEDATE_SUPPLIERSETTLEMENT = "Supplier Settlement";
	public static final String MDM_PART_DURATIONTYPE_DAYS = "Days";
	public static final String MDM_PART_DURATIONTYPE_HOURS = "Hours";
	public static final String MDM_PART_DURATIONTYPE_MONTHS = "Months";
	public static final String MDM_PART_COUNTRYFROM = "countryFrom";
	public static final String MDM_PART_COUNTRYTO = "countryTo";
	public static final String MDM_PART_CITYFROM = "cityFrom";
	public static final String MDM_PART_CITYTO = "cityTo";
	public static final String MDM_PART_AIRLINENAME = "airName";
	
	public static final String MDM_SS_NONCOMMISSIONABLECOMM = "nonCommissionableCommercials";
	public static final String MDM_SS_COMMISSIONABLECOMM = "commissionableCommercials";
	public static final String MDM_SS_RECEIVABLECOMM = "receivableCommercials";
	public static final String MDM_SS_PAYABLECOMM = "payableCommercials";
	public static final String MDM_SS_COMMERCIALHEADS = "commercialHeads";
	public static final String MDM_SS_PERIODICITY = "periodicity";
	public static final String MDM_SS_CREDITSETTLE = "creditSettlement";
	public static final String MDM_SS_DEFINEPERIODICTY = "definePeriodicity";
	public static final String MDM_SS_DP_PERIODICITY = "periodicity";
	public static final String MDM_SS_COMMISSION = "commission";
	public static final String MDM_SS_COMM_PERIODICITY = "periodicity";
	
	public static final String MDM_SS_P_REPEATFREQ = "repeatFreq";
	
	public static final String MDM_SS_P_MONTHLY = "monthly";
	public static final String MDM_SS_P_MONTHLY_SETTLEDUEDAY = "settlementDueDay";
	
	public static final String MDM_SS_P_QUARTERLY = "quarterly";
	
	public static final String MDM_SS_P_FORTNIGHTLY = "fortnightly";
	public static final String MDM_SS_P_FORTNIGHT = "fortnight";
	public static final String MDM_SS_P_FORTNIGHTLY_SETTLEDUEDAY = "settleDueDay";
	public static final String MDM_SS_P_FORTNIGHTLY_SETTLEDUEMONTH = "settlementDueMonth";
	
	public static final String MDM_SS_P_HALFYEARLY = "halfYearly";
	public static final String MDM_SS_P_HALFYEARLY_FROMMONTH = "fromMonth";
	public static final String MDM_SS_P_HALFYEARLY_FROMYEAR = "fromYear";
	public static final String MDM_SS_P_HALFYEARLY_TOMONTH = "toMonth";
	public static final String MDM_SS_P_HALFYEARLY_TOYEAR = "toYear";
	public static final String MDM_SS_P_HALFYEARLY_SETTLEDUEDATE = "settlementDueDate";
	public static final String MDM_SS_P_HALFYEARLY_SETTLEDUEMONTH = "settlementDueMonth";
	public static final String MDM_SS_P_HALFYEARLY_SETTLEDUEYEAR = "settlementDueYear";
	
	public static final String MDM_SS_P_WEEKLY = "weekly";
	public static final String MDM_SS_P_W_WEEKLYSCHD = "weeklySchedule";
	public static final String MDM_SS_P_W_WEEKLYSCHD_DAY = "Day";
	public static final String MDM_SS_P_W_DAY = "day";
	public static final String MDM_SS_P_W_DAY_FROMDAY = "fromDay";
	public static final String MDM_SS_P_W_DAY_TODAY = "toDay";
	public static final String MDM_SS_P_W_DAY_SETTLEDUEDAY = "settlementDueDay";
	public static final String MDM_SS_P_W_WEEKLYSCHD_DATES = "Dates";
	public static final String MDM_SS_P_W_DATES = "dates";
	public static final String MDM_SS_P_W_DATES_FROMDATE = "fromDate";
	public static final String MDM_SS_P_W_DATES_FROMMONTH = "fromMonth";
	public static final String MDM_SS_P_W_DATES_FROMYEAR = "fromYear";
	public static final String MDM_SS_P_W_DATES_TODATE = "toDate";
	public static final String MDM_SS_P_W_DATES_TOMONTH = "toMonth";
	public static final String MDM_SS_P_W_DATES_TOYEAR = "toYear";
	public static final String MDM_SS_P_W_DATES_SETTLEDUEDATE = "settlementDueDate";
	public static final String MDM_SS_P_W_DATES_SETTLEDUEMONTH = "settlementDueMonth";
	public static final String MDM_SS_P_W_DATES_SETTLEDUEYEAR = "settlementDueYear";
	
	public static final String MDM_SS_P_YEARLY = "yearly";
	public static final String MDM_SS_P_YEARLY_FROMDATE = "fromDate";
	public static final String MDM_SS_P_YEARLY_FROMMONTH = "fromMonth";
	public static final String MDM_SS_P_YEARLY_FROMYEAR = "fromYear";
	public static final String MDM_SS_P_YEARLY_TODATE = "toDate";
	public static final String MDM_SS_P_YEARLY_TOMONTH = "toMonth";
	public static final String MDM_SS_P_YEARLY_TOYEAR = "toYear";
	public static final String MDM_SS_P_YEARLY_SETTLEDUEDATE = "settlementDueDate";
	public static final String MDM_SS_P_YEARLY_SETTLEDUEMONTH = "settlementDueMonth";
	public static final String MDM_SS_P_YEARLY_SETTLEDUEYEAR = "settlementDueYear";
	
}

