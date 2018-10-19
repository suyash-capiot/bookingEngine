package com.coxandkings.travel.bookingengine.orchestrator.raileuro;

import com.coxandkings.travel.bookingengine.utils.Constants;

public interface RailEuropeConstants extends Constants{
	
	public static final String PRODUCT_RAILEURO = "RAILEURO";	//To fetch from Mongo
	public static final String PROD_CATEG_SUBTYPE_RAILEURO = "European Rail";
	public static final char KEYSEPARATOR = '|';
	
	
	
	public static final String PRODUCT_RAILEUROPE = "RAILEUROPE";	
	public static final String PROD_CATEG_SUBTYPE_RAILEUROPE="European Rail";
	public static final String PRODUCT_NAME_BRMS = "Rail";
	
	public static final String PRODUCTTYPE_PTP = "PTP";	
	public static final String PRODUCTTYPE_NERO_PTP = "NERO_PTP";
	
	
	
	public static final String JSON_PROP_ADDCOMMDETAILS = "additionalCommercialDetails";
	public static final String JSON_PROP_RETENTIONCOMMDETAILS = "retentionCommercialDetails";
	public static final String JSON_PROP_FIXEDCOMMDETAILS = "fixedCommercialDetails";
	public static final String JSON_PROP_MARKUPCOMMDTLS = "markUpCommercialDetails";
	
	public static final String JSON_PROP_TYPE = "type";
	public static final String JSON_PROP_AMOUNT = "amount";
	public static final String JSON_PROP_BASEFARE = "baseFare";
	public static final String JSON_PROP_TOTALFARE = "totalFare";
	public static final String JSON_PROP_PRODTYPE = "productType";
	public static final String JSON_PROP_RAILPASS = "railPass";
	public static final String JSON_PROP_FAMILYID = "familyId";
	public static final String JSON_PROP_ITEMID = "itemId";
	public static final String JSON_PROP_PASSPROD = "passProducts";
	public static final String JSON_PROP_PRICINGINFO = "pricingInfo";
	public static final String JSON_PROP_TRAINDETAILS = "trainDetails";
	public static final String JSON_PROP_PASSENGERDET = "passengerDetails";
	public static final String JSON_PROP_SUPPPRICINFO = "supplierPricingInfo";
	public static final String JSON_PROP_SALESDATE = "salesDate";
	
	
	public static final String JSON_PROP_PRODTYPE_PASS = "PASS";
	public static final String JSON_PROP_CONTRACTVAL = "contractValidity";
	public static final String JSON_PROP_GRPMODE = "groupMode";
	public static final String JSON_PROP_TRAVELDATE = "travelDate";
	public static final String JSON_PROP_BOOKINGTYPE = "bookingType";
	public static final String JSON_PROP_CONNECTSUPPTYPE = "connectivitySupplierType";
	public static final String JSON_PROP_CONNECTSUPPNAME = "connectivitySupplier";
	public static final String JSON_PROP_PAXINFO = "paxInfo";
	public static final String JSON_PROP_PRODUCTID = "productId";
	public static final String JSON_PROP_PASSNAME = "passName";
	public static final String JSON_PROP_FAMILYNAME = "familyName";
	public static final String JSON_PROP_COUNT = "count";
	public static final String JSON_PROP_NUMBEROFPAX = "numberOfPassengers";
	public static final String JSON_PROP_COUNTRIESCOVERED = "countriesCovered";
	public static final String JSON_PROP_COUNTRYNAME = "countryName";
	public static final String JSON_PROP_PAXTYPE = "paxType";
	public static final String JSON_PROP_SEATCLASS = "seatClass";
	public static final String JSON_PROP_ANCILLARYNAME = "ancillaryName";
	public static final String JSON_PROP_ANCILLARYTYPE = "ancillaryType";
	public static final String JSON_PROP_APPLICABLEON = "applicableOn";
	public static final String JSON_PROP_CLIENTTYPE = "clientType";
	public static final String JSON_PROP_PAXTYPE_YOUTH = "youth";
	public static final String JSON_PROP_PAXTYPE_ADULT = "adult";
	public static final String JSON_PROP_PAXTYPE_SENIOR = "senior";
	public static final String JSON_PROP_PAXTYPE_CHILD = "child";
	public static final String JSON_PROP_PAX_MINAGE = "minAge";
	public static final String JSON_PROP_PAX_MAXAGE = "maxAge";
	public static final String JSON_PROP_PAXTYPE_DEF = "defaultPassengerType";
	public static final String JSON_PROP_QUALITYENABLED = "quantityEnabled";
	public static final String JSON_PROP_PASSTYPE = "passType";
	public static final String JSON_PROP_TRAVELPERIOD = "travelPeriod";
	public static final String JSON_PROP_VALIDITYPERIOD = "validityPeriod";
	public static final String JSON_PROP_COUNTRYCODE = "countryCode";
	public static final String JSON_PROP_FIRSTTRAVELDATECHECK = "needFirstTravelDate";
	public static final String JSON_PROP_TnC = "termsAndConditions";
	public static final String JSON_PROP_INTERRAILPASS_CHECK = "isInterrailPass";
	public static final String JSON_PROP_RETRIEVEDEPOSIT = "retrieveDeposit";
	public static final String JSON_PROP_SUPPID = "RAILEUROPE";
	public static final String JSON_PROP_DEPARTUREDATETOEUR = "departureDateToEurope";
	public static final String JSON_PROP_PAXDETAILS = "paxDetails";
	public static final String JSON_PROP_PASSENGERDETAILS = "passengerDetails";
	public static final String JSON_PROP_SUPPBOOKINGID = "supplierBookingId";
	public static final String JSON_PROP_PRINTINGOPTION = "selectedPrintingOption";
	public static final String JSON_PROP_PRICE = "price";
	public static final String JSON_PROP_AGE = "age";
	public static final String JSON_PROP_COUNTRYOFRESIDENCE = "countryOfResidence";
	public static final String JSON_PROP_PRIMDRIVERCHECK = "isPrimaryDriver";
	public static final String JSON_PROP_DOB = "dateOfBirth";
	public static final String JSON_PROP_PAXQUANTITY = "paxQuantity";
	public static final String JSON_PROP_ADDINFO = "additionalInfo";
	public static final String JSON_PROP_DUEAMOUNT = "amountDue";
	public static final String JSON_PROP_BILLINGDET = "billingDetails";
	public static final String JSON_PROP_PROVIDERDESC = "providerTypeDescription";
	public static final String JSON_PROP_SERVICETYPEDESC = "serviceTypeDescription";
	public static final String JSON_PROP_PHONE = "phone";
	public static final String JSON_PROP_SHIPPINGINFO = "shippingDetails";
	public static final String JSON_PROP_AGENCYNAME = "agencyName";
	public static final String JSON_PROP_IATANUMBER = "iataNumber";
	public static final String JSON_PROP_AGENTINFO = "agentInformation";
	public static final String JSON_PROP_DOCTYPE_PASSPORT = "Passport";
	public static final String JSON_PROP_DOCTYPE_EURONETUSERID = "euronetUserId";
	public static final String JSON_PROP_DOCTYPE_EURONETUSERNAME = "euronetUserName";
	public static final String JSON_PROP_DOCTYPE_APPLIEDAMOUNT = "appliedAmount";
	public static final String JSON_PROP_DOCTYPE_STREETADDR = "streetAddress";
	public static final String JSON_PROP_DOCTYPE_PAIDAMOUNT = "paidAmount";
	public static final String JSON_PROP_DOCTYPE_PAYMENTDATE = "paymentDate";
	public static final String JSON_PROP_DOCTYPE_PAYMENTTYPE = "paymentType";
	
		
		
		
		public static final String PACKAGETYPE = "FLEXIBILITY_DRIVEN";
		public static final String FARETYPES = "RETAIL";
		public static final String GROUPMODE = "INDIVIDUAL";
		
		
		public static final String JSON_PROP_RAILEUROPEOBJ = "wwRail";
		public static final String JSON_PROP_FEE = "fee";
		public static final String JSON_PROP_FEECODE = "feeCode";
		public static final String JSON_PROP_FEES = "fees";
		
		public static final String JSON_PROP_PRODUCTTYPE = "productType";
		public static final String JSON_PROP_PRODUCTTYPE_PTP  = "PTP";
		public static final String JSON_PROP_PRODUCTTYPE_NERO_PTP = "NERO_PTP";
		
		public static final String JSON_PROP_ORIGINCITYNAME = "originCityName";
		public static final String JSON_PROP_ORIGINCITYCODE = "originCityCode";
		public static final String JSON_PROP_ORIGINSTATIONCODE = "originStationCode";
		public static final String JSON_PROP_DESTINATIONCITYNAME = "destinationCityName";
		public static final String JSON_PROP_DESTINATIONCITYCODE = "destinationCityCode";
		public static final String JSON_PROP_DESTINATIONSTATIONCODE = "destinationStationCode";
		public static final String JSON_PROP_JOURNEYTYPE = "journeyType";
		
		public static final String JSON_PROP_DEPARTUREDATE = "departureDate";
		public static final String JSON_PROP_DEPARTURETIME = "departureTime";
		public static final String JSON_PROP_RETURNDATE = "returnDate";
		public static final String JSON_PROP_RETURNTIME = "returnTime";
		
		public static final String JSON_PROP_NPASSENGERS = "nPassengers";
		public static final String JSON_PROP_PAXAGE = "paxAge";
		
		public static final String JSON_PROP_WWRAILARR = "wwRailInfo";
		public static final String JSON_PROP_SUPPLIERID= "supplierID";
		public static final String JSON_PROP_ISROUNDTRIP = "isRoundTrip";
		public static final String JSON_PROP_SOLUTIONS = "solutions";
		public static final String JSON_PROP_ITINERARYTYPE = "itineraryType";
		public static final String JSON_PROP_PACKAGEDETAILS = "packageDetails";
		public static final String JSON_PROP_PRODUCTFARE = "productFare";
		public static final String JSON_PROP_TOTALPRICEINFO = "totalPriceInfo";
		public static final String JSON_PROP_PCKGFAREID = "packageFareId";
		public static final String JSON_PROP_PRODUCTPRICEINFO = "productPriceInfo";
		public static final String JSON_PROP_SEGMENTID = "segmentId";
		public static final String JSON_PROP_BASEPAXTYPE = "basePassengerType";
		public static final String JSON_PROP_PAXTYPEFARES = "paxTypeFares";
		public static final String JSON_PROP_SUPPPRICEINFO = "supplierPricingInfo";
		public static final String JSON_PROP_CLIENTCOMMINFO = "clientCommercialInfo";
		
		public static final String JSON_PROP_SEGMENT = "segment";
		public static final String JSON_PROP_TICKETINGDATE = "ticketingDate";
		public static final String JSON_PROP_TRAINCATEGORY = "trainCategory";
		public static final String JSON_PROP_TRAINNUMBER = "trainNumber";
		public static final String JSON_PROP_CONNECTSUPP = "connectivitySupplier";
		
		public static final String JSON_PROP_TAX = "tax";
		public static final String JSON_PROP_TAXES = "taxes";
		public static final String JSON_PROP_TAXNAME = "taxName";
		public static final String JSON_PROP_TAXVALUE = "taxValue";
		public static final String JSON_PROP_TAXCODE = "taxCode";
		
		
		public static final String JSON_PROP_BOOKINGORIGINID = "bookingOriginId";
		public static final String JSON_PROP_DISTCHANNELID = "distributionChannelId";
		public static final String JSON_PROP_TRACKINGINFO = "trackingInformation";
		public static final String JSON_PROP_LASTNAME = "lastName";
		public static final String JSON_PROP_LEADPAXNAME = "leadPassengerName";
		public static final String JSON_PROP_WORKPHONENUMBER = "workPhoneNumber";
		public static final String JSON_PROP_ADDRESS = "address";
		public static final String JSON_PROP_CONTACTINFO = "contactInformation";
		public static final String JSON_PROP_PRODSTATUS = "statusOfTheProduct";
		public static final String JSON_PROP_BOOKTICKETOPTION = "bookedProductAvailableTicketingOptionList";
		public static final String JSON_PROP_ASSGTICKETOPTION = "assignedTicketingOption";
		public static final String JSON_PROP_BOOKEDPRODID = "bookedProductId";
		public static final String JSON_PROP_CLASSOFSERV = "physicalClassOfService";
		public static final String JSON_PROP_CHECKED = "isSelected";
		public static final String JSON_PROP_RAILPROTECTPLAN = "railProtectionPlan";
		public static final String JSON_PROP_AGENTNAME = "agentName";
		public static final String JSON_PROP_BOOKINGSTATUS = "bookingStatus";
		public static final String JSON_PROP_PAYMENTREADY_CHECK = "isReadyForPayment";
		public static final String JSON_PROP_BOOKINGPRINTOPTION = "bookingPrintingOptionList";
		public static final String JSON_PROP_BOOKINGEXPIRYDATE = "bookingExpirationDate";
		public static final String JSON_PROP_NUMBEROFCOUPONS = "nCouponsPrinted";
		public static final String JSON_PROP_INVOICENUMBER = "invoiceNumber";
		public static final String JSON_PROP_REMAININGTOTAL = "totalRemainingToBePaid";

	
}
