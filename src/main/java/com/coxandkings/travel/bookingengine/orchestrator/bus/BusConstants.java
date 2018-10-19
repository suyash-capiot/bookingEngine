package com.coxandkings.travel.bookingengine.orchestrator.bus;

import com.coxandkings.travel.bookingengine.utils.Constants;

public interface BusConstants extends Constants{
	
	public static final String PRODUCT_BUS = "BUS";
	
	public static final String JSON_PROP_SERVICE = "service";
	public static final String JSON_PROP_BUSSERVICEDETAILS = "busServiceDetails";
	public static final String JSON_PROP_SUPPFAREINFO = "Suppfare";
	public static final String JSON_PROP_BOOKREFIDX = "bookRefIdx";
	public static final String JSON_PROP_SOURCE = "sourceStationId";
	public static final String JSON_PROP_DESTINATION = "destinationStationId";
	public static final String JSON_PROP_JOURNEYDATE = "journeyDate";
	public static final String JSON_PROP_FROMCITY = "fromCity";
	public static final String JSON_PROP_FROMCOUNTRY = "fromCountry";
	public static final String JSON_PROP_FROMCONTINENT = "fromContinent";
	public static final String JSON_PROP_FROMSTATE = "fromState";
	public static final String JSON_PROP_TOCITY = "toCity";
	public static final String JSON_PROP_TOCOUNTRY = "toCountry";
	public static final String JSON_PROP_TOCONTINENT = "toContinent";
	public static final String JSON_PROP_TOSTATE = "toState";
	public static final String JSON_PROP_AVAILABILITY ="availability";
	public static final String JSON_PROP_FARESARRAY ="fares";
	public static final String JSON_PROP_PASSANGERDETAILS ="passengerDetails";
//	public static final String JSON_PROP_ENTITYCOMMERCIALS = "entityCommercials";
	public static final String JSON_PROP_MARKUPCOMMERCIALS = "markUpCommercialDetails";
	public static final String JSON_PROP_SUPPLIERFARES = "supplierFares";
	public static final String JSON_PROP_OPERATORID = "operatorId";
	public static final String JSON_PROP_SERVICEID = "serviceId";
	public static final String JSON_PROP_LAYOUTID = "layoutId";
	public static final String DEF_SUPPID = "";
	public static final String JSON_PROP_CLIENTTRANRULES = "cnk.bus_commercialscalculationengine.clienttransactionalrules.Root";
	public static final String JSON_PROP_SUPPLIERTRANRULES = "cnk.bus_commercialscalculationengine.suppliertransactionalrules.Root";
	public static final String JSON_PROP_RECEIVABLES = "receivables";
	public static final String JSON_PROP_RECEIVABLE = "receivable";
	public static final String JSON_PROP_ITINTOTALFARE = "itinTotalFare";
	public static final String JSON_PROP_ADDITIONCOMMDETAILS = "additionalCommercialDetails";
	public static final String JSON_PROP_SEATNO = "seatNumber";
	public static final String JSON_PROP_CURRENCY = "currency";
	public static final String JSON_PROP_RETENSIONCOMMDETAILS = "retentionCommercialDetails";
	public static final String JSON_PROP_FIXEDCOMMDETAILS = "fixedCommercialDetails";
	public static final String PROD_CATEG_SUBTYPE_BUS = "Bus";
	public static final String JSON_PROP_BASEFARE= "baseFare";
	public static final String JSON_PROP_TOTALFARE= "totalFare";
	public static final String JSON_PROP_BUSSERVICETOTALFARE= "serviceTotalFare";
	public static final String JSON_PROP_PAXSEATFARES = "paxSeatFares";
	public static final String JSON_PROP_SUPPPRICEINFO = "supplierTotalPricingInfo";
	public static final String JSON_PROP_BUSTOTALPRICEINFO = "busTotalPricingInfo";
	public static final String JSON_PROP_TICKETNO = "ticketNo";
	public static final String JSON_PROP_PNRNO = "PNRNo";
	public static final String JSON_PROP_FAREBREAKUP = "fareBreakUp";
	public static final String JSON_PROP_CLIENTCOMMITININFO = "clientCommercialInfo";
	//amend and cancel
	public static final String CANCEL_SI_ERR = "SI-NotSupported";
	public static final String JSON_PROP_CANCELTYPE = "cancelType";

	public static final String JSON_PROP_BUS_CANNCELTYPE_CANCELPAX  = "CANCELPASSENGER";
	public static final String JSON_PROP_BUS_CANNCELTYPE_FULLCANCEL  = "FULLCANCELLATION";
	
	//offers
	public static final String JSON_PROP_VEHICLEDETAILS = "vehicleDetails";
	public static final String JSON_PROP_FARENAME = "fareName";
	public static final String JSON_PROP_FAREVALUE = "fareValue";
	public static final String JSON_PROP_PRODDETAILS = "productDetails";

//	public static final String JSON_PROP_COMMERCIALHEAD = "commercialHead";
//	public static final String JSON_PROP_COMMERCIALDETAILS = "commercialDetails";
}
