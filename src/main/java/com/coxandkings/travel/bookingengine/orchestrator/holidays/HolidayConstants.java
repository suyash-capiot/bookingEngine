package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.text.SimpleDateFormat;
import com.coxandkings.travel.bookingengine.utils.Constants;

public interface HolidayConstants extends Constants {
	
	public static final String PRODUCT = "HOLIDAYS";
	
	public static final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");

	public static final String NS_PAC = "http://www.coxandkings.com/integ/suppl/packages";
	public static final String NS_COM = "http://www.coxandkings.com/integ/suppl/common";
	public static final String NS_PAC1 = "http://www.coxandkings.com/integ/suppl/packagesinterface";
	public static final String NS_OTA = "http://www.opentravel.org/OTA/2003/05";

	public static final String JSON_PROP_AMENDTYPE ="amendType";
	
	public static final String JSON_PROP_BRANDNAME = "brandName";
	public static final String JSON_PROP_TOURCODE = "tourCode";
	public static final String JSON_PROP_SUBTOURCODE = "subTourCode";
	public static final String JSON_PROP_OTA_DYNAMICPKGAVAILRSWRAPPER = "oTA_DynamicPkgAvailRSWrapper";
	public static final String JSON_PROP_OTA_DYNAMICPKGBOOKRSWRAPPER ="oTA_DynamicPkgBookRSWrapper";
	public static final String JSON_PROP_DYNAMICPACKAGE= "dynamicPackage";
	public static final String JSON_PROP_COMPONENTS = "components";
	public static final String JSON_PROP_DYNAMICPKGACTION= "dynamicPkgAction";
	public static final String JSON_PROP_HOTEL_COMPONENT = "hotelComponent";
	public static final String JSON_PROP_AIR_COMPONENT = "airComponent";
	public static final String JSON_PROP_CRUISE_COMPONENT = "cruiseComponent";
	public static final String JSON_PROP_TRANSFER_COMPONENT = "transferComponent";
	public static final String JSON_PROP_INSURANCE_COMPONENT = "insuranceComponent";
	public static final String JSON_PROP_ACTIVITY_COMPONENT = "activityComponent";
	public static final String JSON_PROP_ACTIVITY_COMPONENTS = "activityComponents";
	public static final String JSON_PROP_EXTRA_COMPONENT = "extraComponent";
	public static final String JSON_PROP_PACKAGEOPTIONCOMPONENT = "packageOptionComponent";
	public static final String JSON_PROP_RESGUESTS = "resGuests";
	public static final String JSON_PROP_GLOBALINFO = "globalInfo";
	public static final String JSON_PROP_COMMENT = "comment";
	public static final String JSON_PROP_FEES = "fees";
	public static final String JSON_PROP_BOOKINGRULE = "bookingRule";
	public static final String JSON_PROP_PROMOTION = "promotion";
	public static final String JSON_PROP_ORIGINDESTOPTIONS = "originDestinationOptions";
	public static final String JSON_PROP_AIRITINERARY = "airItinerary";
	public static final String JSON_PROP_GROUNDSERVICE = "groundService";
	public static final String JSON_PROP_NAME = "name";
	public static final String JSON_PROP_DESCRIPTION = "description";
	public static final String JSON_PROP_TPA_EXTENSIONS = "tPA_Extensions";
	public static final String JSON_PROP_TYPE = "type";
	public static final String JSON_PROP_CATEGORYOPTION = "categoryOption";
	public static final String JSON_PROP_CATEGORYOPTIONS = "categoryOptions";
	public static final String JSON_PROP_AMOUNTBEFORETAX ="amountBeforeTax";
	public static final String JSON_PROP_CURRENCYCODE ="currencyCode";
	public static final String JSON_PROP_AMOUNTAFTERTAX = "amountAfterTax";
	public static final String JSON_PROP_BOOKREFERENCES = "bookReferences";
	public static final String JSON_PROP_DEPOSITS = "deposits";
	
	//Added for GetDetails and Search
	public static final String JSON_PROP_SUPPLIERID = "supplierID";
	public static final String JSON_PROP_SUPPLIERREF = "supplierRef";
	public static final String JSON_PROP_SEQUENCE = "sequence";
	public static final String JSON_PROP_ALLOWOVERRIDEAIRDATES = "allowOverrideAirDates";
	public static final String JSON_PROP_PACKAGEOPTION = "packageOption";
	public static final String JSON_PROP_PACKAGEOPTIONS = "packageOptions";
	public static final String JSON_PROP_TIMESPAN = "timeSpan";
	public static final String JSON_PROP_CANCELPOLICYINDICATOR = "cancelPolicyIndicator";
	public static final String JSON_PROP_CANCELPENALTIES = "cancelPenalties";
	public static final String JSON_PROP_FEE = "fee";
	public static final String JSON_PROP_DYNAMICPKGID = "dynamicPkgID";
	public static final String JSON_PROP_ID = "id";
	public static final String JSON_PROP_ID_CONTEXT = "id_Context";
	public static final String JSON_PROP_COMPANYSHORTNAME = "companyShortName";
	public static final String JSON_PROP_COMPANYNAME = "companyName";
	public static final String JSON_PROP_TOURREFID = "tourRefID";
	public static final String JSON_PROP_TOURNAME = "tourName";
	public static final String JSON_PROP_TOURSTARTCITY = "tourStartCity";
	public static final String JSON_PROP_TOURENDCITY = "tourEndCity";
	public static final String JSON_PROP_TOURDETAILS = "tourDetails";
	public static final String JSON_PROP_PKGS_TPA = "pkgs_TPA";
	public static final String JSON_PROP_TEXT = "text";
	public static final String JSON_PROP_AMOUNT = "amount";
	
	public static final String JSON_PROP_MAXCHARGEUNITAPPLIES = "maxChargeUnitApplies";
	public static final String JSON_PROP_TAXES = "taxes";
	public static final String JSON_PROP_ISINCLUDEDINTOUR = "isIncludedInTour";
	public static final String JSON_PROP_REQUIREDFORBOOKING = "RequiredForBooking";
	public static final String JSON_PROP_END = "end";
	public static final String JSON_PROP_START = "start";
	public static final String JSON_PROP_DURATION = "duration";
	public static final String JSON_PROP_TRAVELENDDATE = "travelEndDate";
	public static final String JSON_PROP_TRAVELSTARTDATE = "travelStartDate";
	public static final String JSON_PROP_MINIMUMAGE = "minimumAge";
	public static final String JSON_PROP_MAXIMUMAGE = "maximumAge";
	public static final String JSON_PROP_ADULT = "adult";
	public static final String JSON_PROP_CHILD = "child";
	public static final String JSON_PROP_QUOTEID = "quoteID";
	public static final String JSON_PROP_AVAILABILITYSTATUS = "availabilityStatus";
	public static final String JSON_PROP_INSCOVERAGEDETAIL = "insCoverageDetail";
	public static final String JSON_PROP_PLANCOST = "planCost";
	public static final String JSON_PROP_INSURANCECOMPONENT = "insuranceComponent";
	public static final String JSON_PROP_COUNT = "count";
	public static final String JSON_PROP_CABINNUMBER = "cabinNumber";
	public static final String JSON_PROP_STATUS = "status";
	public static final String JSON_PROP_CABINOPTION = "cabinOption";
	
	public static final String JSON_PROP_PRICE = "price";
	public static final String JSON_PROP_GUESTCOUNT = "guestCount";
	public static final String JSON_PROP_CABINOPTIONS = "cabinOptions";
	public static final String JSON_PROP_BASEPREMIUM = "basePremium";
	public static final String JSON_PROP_CHARGE = "charge";
	public static final String JSON_PROP_TAXDESCRIPTION = "taxDescription";
	public static final String JSON_PROP_TAX = "tax";
	public static final String JSON_PROP_LOCATION = "location";
	public static final String JSON_PROP_TOTALCHARGE = "totalCharge";
	public static final String JSON_PROP_AIRINCLUSIVEBOOKING = "airInclusiveBooking";
	public static final String JSON_PROP_WITHEXTRANIGHTS = "withExtraNights";
	public static final String JSON_PROP_PURCHASABLE = "purchasable";
	public static final String JSON_PROP_FLIGHTINFOREQUIRED = "flightInfoRequired";
	public static final String JSON_PROP_PERSERVICEPRICING = "perServicePricing";
	public static final String JSON_PROP_MAXPASSENGERSINPARTY = "maxPassengersInParty";
	public static final String JSON_PROP_MINPASSENGERSINPARTY = "minPassengersInParty";
	public static final String JSON_PROP_RATETOTALAMOUNT = "rateTotalAmount";
	public static final String JSON_PROP_AIRPORTNAME = "airportName";
	public static final String JSON_PROP_CODECONTEXT = "codeContext";
	public static final String JSON_PROP_LOCATIONCODE = "locationCode";
	public static final String JSON_PROP_DEPARTURE = "departure";
	public static final String JSON_PROP_AIRPORTINFO = "airportInfo";
	public static final String JSON_PROP_PICKUP = "pickup";
	public static final String JSON_PROP_FLIGHTSEGMENT = "flightSegment";
	public static final String JSON_PROP_ORIGINDESTINATIONOPTION = "originDestinationOption";
	public static final String JSON_PROP_DEPARTUREAIRPORT = "departureAirport";
	public static final String JSON_PROP_ARRIVALAIRPORT = "arrivalAirport";
	public static final String JSON_PROP_BOOKINGCLASSAVAILS = "bookingClassAvails";
	public static final String JSON_PROP_CABINTYPE = "cabinType";
	public static final String JSON_PROP_ROOMSTAY = "roomStay";
	public static final String JSON_PROP_ROOMSTAYS = "roomStays";
	public static final String JSON_PROP_ROOMSTAYSTATUS = "roomStayStatus";
	public static final String JSON_PROP_ROOMTYPE = "roomType";
	public static final String JSON_PROP_ROOMRATE = "roomRate";
	public static final String JSON_PROP_GUESTCOUNTS = "guestCounts";
	public static final String JSON_PROP_LENGTHSOFSTAY = "lengthsOfStay";
	public static final String JSON_PROP_MINMAXMESSAGETYPE = "minMaxMessageType";
	public static final String JSON_PROP_TIME = "time";
	public static final String JSON_PROP_TIMEUNIT = "timeUnit";
	public static final String JSON_PROP_PRODUCTNAME = "productName";
	public static final String JSON_PROP_PRODUCTBRAND = "productBrand";
	public static final String JSON_PROP_TOTALFARE = "totalFare";
	public static final String JSON_PROP_COMPTOTALFARE = "componentTotalFare";
	public static final String JSON_PROP_BASEFARE = "baseFare";
	public static final String JSON_PROP_TAXDETAILS = "taxDetails";
	public static final String JSON_PROP_FAREBREAKUP = "fareBreakUp";
	public static final String JSON_PROP_TAXNAME = "taxName";
	public static final String JSON_PROP_TAXVALUE = "taxValue";
	public static final String JSON_PROP_PASSENGERTYPE = "passengerType";
	public static final String JSON_PROP_AIRITINERARYPRICINGINFO = "airItineraryPricingInfo";
	public static final String JSON_PROP_ITINTOTALFARE = "itinTotalFare";
	public static final String JSON_PROP_TOTAL = "total";
	public static final String JSON_PROP_PASSENGERDETAILS = "passengerDetails";
	public static final String JSON_PROP_ROOMDETAILS ="roomDetails";
	public static final String JSON_PROP_RATEDESC = "rateDescription";
	public static final String JSON_PROP_CODE ="Code";
	public static final String JSON_PROP_CABINCATEGORY = "cabinCategory";
	public static final String JSON_PROP_RATEPLANCATEGORY ="ratePlanCategory";
	public static final String JSON_PROP_PAXTYPEFARES ="paxTypeFares";
	public static final String JSON_PROP_CLIENTCOMMERCIALINFO ="clientCommercialInfo";
	public static final String JSON_PROP_SUPPLIERPRICINGINFO = "supplierPricingInfo";
	public static final String JSON_PROP_TOTALPRICINGINFO = "totalPricingInfo";
	public static final String JSON_PROP_CLIENTCOMMERCIALSTOTAL ="clientCommercialsTotal";
	public static final String JSON_PROP_CLIENTCOMMERCIALSTOTALCOMM ="clientEntityTotalCommercials";
	public static final String JSON_PROP_ADDITIONALINFO ="additionalInfo";
	
	public static final String DYNAMICPKGACTION = "DynamicPkgAction";
	
	public static final String DYNAMICPKGACTION_HOTEL_TOUR = "PackageAccomodation";
	public static final String DYNAMICPKGACTION_HOTEL_PRE = "PreNightPackageAccomodation";
	public static final String DYNAMICPKGACTION_HOTEL_POST = "PostNightPackageAccomodation";
	
	public static final String DYNAMICPKGACTION_AIR_DEPARR = "PackageDepartureAndArrivalFlights";
    public static final String DYNAMICPKGACTION_AIR_INTERNAL = "InternalPackageFlights";
    
    public static final String DYNAMICPKGACTION_CRUISE_TOUR = "PackageCruise";
    public static final String DYNAMICPKGACTION_CRUISE_PRE = "PackagePreNightsCruise";
    public static final String DYNAMICPKGACTION_CRUISE_POST = "PackagePostNightsCruise";

    public static final String DYNAMICPKGACTION_TRANSFER_DEPARTURE = "PackageDepartureTransfer";
    public static final String DYNAMICPKGACTION_TRANSFER_IARRIVAL = "PackageArrivalTransfer";
    
    public static final String DYNAMICPKGACTION_INSURANCE = "PackageInsurance";
    public static final String DYNAMICPKGACTION_TRANSFER = "PackageTransfers";
    public static final String DYNAMICPKGACTION_EXTRAS = "Extras";
    public static final String DYNAMICPKGACTION_SURCHARGES = "Surcharges";
    
    public static final String JSON_PROP_CLIENTTRANRULES ="cnk.holidays_commercialscalculationengine.clienttransactionalrules.Root";
   	public static final String JSON_PROP_SUPPTRANRULES = "cnk.holidays_commercialscalculationengine.suppliertransactionalrules.Root";
   	public static final String JSON_PROP_PACKAGEDETAILS = "packageDetails";
   	public static final String JSON_PROP_COMMONELEMENTS = "commonElements";
   	public static final String JSON_PROP_SUPP = "supplier";
   	public static final String JSON_PROP_ADDCOMMDETAILS = "additionalCommercialDetails";
   	public static final String JSON_PROP_PRENIGHT ="preNight";
   	public static final String JSON_PROP_POSTNIGHT ="postNight";
	public static final String JSON_PROP_TRAVELDATEFROM = "travelDateFrom";
 	public static final String JSON_PROP_TRAVELDATETO = "travelDateTo";
	public static final String JSON_PROP_PRODUCTDETAILS = "productDetails";
	public static final String JSON_PROP_ROOMCATEGORY ="roomCategory";
	public static final String JSON_PROP_FAREDETAILS = "fareDetails";
	public static final String JSON_PROP_FARENAME = "fareName";
	public static final String JSON_PROP_FAREVAL = "fareValue";
	public static final String JSON_PROP_SALESOFFICELOC = "salesOfficeLocation";
	public static final String JSON_PROP_HOTELDETAILS = "hotelDetails";
	public static final String JSON_PROP_MARKUPCOMMDTLS = "markUpCommercialDetails";

	public static final String CANCEL_SI_ERR = "SI-NotSupported";
	public static final String JSON_PROP_CANCELREQUESTS="cancelRequests";
	public static final String JSON_PROP_CANCELRULES="cancelRules";
	public static final String JSON_PROP_BOOKREFID = "bookRefID";
	public static final String JSON_PROP_SUPPBOOKREFS = "supplierBookReferences";
	public static final String JSON_PROP_CANCELTYPE = "cancelType";
	public static final String CANCEL_TYPE_PAX = "PAX";
	public static final String CANCEL_TYPE_PAC = "PACKAGE";
	
	public static final String JSON_PROP_ROE = "roe";
	public static final String JSON_PROP_HOLIDAYS = "Holidays";
	public static final String JSON_PROP_BOOKINGID= "bookingID";
	public static final int PASSENGER_COUNT_MINIMUM_ADULT = 1;
	public static final int PASSENGER_COUNT_MAXIMUM_ADULT_CHILD = 20;
	public static final int PASSENGER_COUNT_MAXIMUM_ADULT = 9;
	public static final int PASSENGER_COUNT_MINIMUM_CHILD = 0;
	public static final int PASSENGER_COUNT_MAXIMUM_CHILD = 8;
	public static final String JSON_PROP_PAXINFO = "paxInfo";
}
