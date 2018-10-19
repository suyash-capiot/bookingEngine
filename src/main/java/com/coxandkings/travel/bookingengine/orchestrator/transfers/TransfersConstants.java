package com.coxandkings.travel.bookingengine.orchestrator.transfers;

import com.coxandkings.travel.bookingengine.utils.Constants;

public interface TransfersConstants  extends Constants {
	
	public static final String JSON_PROP_LOCATIONTYPE = "LocationType";
	public static final String JSON_PROP_LOCATIONCODE = "LocationCode";
	public static final String JSON_PROP_DATETIME = "DateTime";
	public static final String JSON_PROP_LONGITUDE = "Longitude";
	public static final String JSON_PROP_LATITUDE = "Latitude";
	
	public static final String JSON_PROP_GROUNDSERVICES = "groundServices";
	public static final String JSON_PROP_GROUNDWRAPPER = "groundBookRSWrapper";
	public static final String JSON_PROP_SERVICE = "service";
	public static final String JSON_PROP_SERVICES = "service";
	public static final String JSON_PROP_RESTRICTIONS = "restrictions";
	public static final String JSON_PROP_TOTALCHARGE = "totalCharge";
	public static final String JSON_PROP_COUNTRYCODE = "countryCode";
	public static final String JSON_PROP_TAXDETAILS = "TaxDetails";
	public static final String JSON_PROP_REFERENCE = "reference";
	public static final String JSON_PROP_REFERENCE1 = "reference";
	public static final String JSON_PROP_TIMELINES = "timelines";
	public static final String PRODUCT_TRANSFER = "TRANSFER";
	public static final String PRODUCT_TRANSFERS = "TRANSFERS";
	public static final String JSON_PROP_TRIPTYPE = "serviceType";
	public static final String JSON_PROP_TRANSFERSDETAILS = "transfersDetails";
	public static final String JSON_PROP_SERVICECHARGES = "ServiceCharges";
	public static final String JSON_PROP_RESERVATION = "Reservation";
	public static final String JSON_PROP_BOOKRS = "GroundBookRS";
	public static final String JSON_PROP_TRANSFERINFORMATION = "transferInformation";
	public static final String PRODUCT_NAME_BRMS = "Transfers";
	public static final String JSON_PROP_CURRENCYCODE = "currencyCode";
	public static final String JSON_PROP_ESTIMATEDAMOUNT = "estimatedTotalAmount";
	public static final String JSON_PROP_RATEAMOUNT = "rateTotalAmount";
	public static final String JSON_PROP_AMOUNT = "amount";
	public static final String JSON_PROP_SUPPPRICEINFO = "supplierPricingInfo";
	public static final String JSON_PROP_BOOKREFIDX ="bookRefIdx";
	public static final String JSON_PROP_PICKUPDATE =  "pickUpdateTime";
	public static final String JSON_PROP_PICKUPLOCCODE =  "pickUpLocationCode";
	public static final String JSON_PROP_DROPOFFDATE =  "dropOffdateTime";
	public static final String JSON_PROP_DROPOFFLOCCODE =  "dropOffLocationCode";
	//public static final String JSON_PROP_SERVICECHARGE = "ServiceCharges";
	public static final String JSON_PROP_DESCRIPTION = "description";
	public static final String XML_PROP_AGE = "Age";
	public static final String XML_PROP_MAXBAG = "MaximumBaggage";
	public static final String JSON_PROP_DETAILDESCRIPTION = "detailedDescription";
	public static final String JSON_PROP_GUIDELINES = "guidelines";
	public static final String JSON_PROP_LANGCODE = "languagecode";
	public static final String JSON_PROP_LANG = "Language";
	public static final String JSON_PROP_IMAGELIST = "imageList";
	public static final String JSON_PROP_TEXT = "text";
	public static final String JSON_PROP_URL = "url";
	public static final String JSON_PROP_PICKLOCCODE = "pickLocationCode";
	public static final String JSON_PROP_PICKCITY = "pickUpCity";
	public static final String JSON_PROP_DROPCITY = "dropOffCity";
	public static final String JSON_PROP_DROPLOCCODE = "dropLocationCode";
	public static final String JSON_PROP_MAXPASS = "maximumPassengers";
	public static final String JSON_PROP_MAXBAG = "maximumBaggage";
	public static final String JSON_PROP_ID = "id";
	public static final String JSON_PROP_ID_CONTEXT = "id_Context";
	public static final String JSON_PROP_SERVICETYPE = "serviceType";
	public static final String JSON_PROP_SERTYPE = "ServiceType";
	public static final String JSON_PROP_REFTYPE = "Type";
	public static final String JSON_PROP_UNIQUEID = "uniqueID";
	public static final String JSON_PROP_CANCELPENTCHRG = "cancellationPenaltyInd";
	public static final String JSON_PROP_FEES = "fees";
	public static final String JSON_PROP_ISOCURRENCY = "ISOCurrency";
	public static final String JSON_PROP_TPAEXTENTION = "tpa_Extensions";
	public static final String PROD_CATEG_SUBTYPE_TRANSFERS = "Transfers";
	public static final String JSON_PROP_RESERVATIONID = "reservationId";
	public static final String JSON_PROP_REFERENCEID = "referenceID";
	public static final String JSON_PROP_RESERVATIONIDS = "reservationIds";
	public static final String JSON_PROP_REFERENCES = "references";
	public static final String JSON_PROP_TIME = "time";
	
	public static final String PROD_CATEG_SUBTYPE_TRANSFER = "Transfer";
	public static final String JSON_PROP_TRANSFERSINFO = "transfersInfo";
	public static final String JSON_PROP_CLIENTCOMMITININFO = "clientCommercialItinInfo";
	public static final String JSON_PROP_ADDCOMMDETAILS = "additionalCommercialDetails";
	public static final String JSON_PROP_RETENTIONCOMMDETAILS = "retentionCommercialDetails";
	public static final String JSON_PROP_FIXEDCOMMDETAILS = "fixedCommercialDetails";
	public static final String JSON_PROP_MARKUPCOMMDTLS = "markUpCommercialDetails";
	public static final String JSON_PROP_PSGRDETAILS = "passengerDetails";
	public static final String JSON_PROP_ENTITYCOMMS = "entityCommercials";
	public static final String JSON_PROP_CLIENTCOMMTOTAL ="totalPricingInfo";
	public static final String JSON_PROP_TOTALFARE ="totalFare";
	public static final String JSON_PROP_SUPPTOTALFARE ="suppTotalFare";
	public static final String JSON_PROP_SUPPCOMMTOTAL = "supplierCommercialsTotals";
	public static final char KEYSEPARATOR = '|';
	public static final String JSON_PROP_VEHICLETYPE = "vehicleType";
	public static final String JSON_PROP_SUPPRESNO = "supplierReservationNumber";
	public static final String JSON_PROP_SUPPREFSNO = "supplierReferenceNumber";
	
	//cancel
	public static final String CANCEL_SI_ERR = "SI-NotSupported";
	public static final String JSON_PROP_CANCELTYPE = "cancelType";
	public static final String CANCEL_TYPE_PAX = "PAX";	
	public static final String CANCEL_TYPE_CANCEL = "Cancel";
	public static final String JSON_PROP_TRAN_CANCEL_STATUS = "status";
		
		//Offers
		public static final String JSON_PROP_VEHICLEDETAILS = "vehicleDetails";
		public static final String JSON_PROP_VEHICLEBRANDNAME = "vehicleBrandName";
		public static final String JSON_PROP_VEHICLENAME = "vehicleName";
		public static final String JSON_PROP_DESTINANTION = "destination";
		public static final String JSON_PROP_NO_OF_NIGHTS = "noOfNights";
		public static final String JSON_PROP_VEHICLEPACKNAME = "vehiclePackageName";
		public static final String JSON_PROP_ANCILLARYTYPE = "ancillaryType";
		public static final String JSON_PROP_ANCILLARYNAME = "ancillaryName";
		public static final String JSON_PROP_VEHICLECATAGORY = "vehicleCategory";
		public static final String JSON_PROP_FARENAME = "fareName";
		public static final String JSON_PROP_FAREVAL = "fareValue";
		public static final String JSON_PROP_PAXDETL  = "passengerDetails";
		public static final String JSON_PROP_PRODDETAILS = "productDetails";
		public static final String JSON_PROP_PSGRTYPE = "passengerType";
	
}
