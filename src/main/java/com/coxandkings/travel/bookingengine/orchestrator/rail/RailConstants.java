package com.coxandkings.travel.bookingengine.orchestrator.rail;

import java.text.SimpleDateFormat;

import com.coxandkings.travel.bookingengine.utils.Constants;

public interface RailConstants extends Constants{
	
	public static final String PRODUCT_RAIL = "RAIL";
	
	
	public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00");
	
	public static final String PROD_CAT_SUBTYPE="Rail";
	public static final String JSON_PROP_ORIGINDESTOPTS="originDestinationOptions";
	public static final String JSON_PROP_ORIGINLOC="originLocation";
	public static final String JSON_PROP_DESTLOC="destinationLocation";
	public static final String JSON_PROP_TRAVELDATE="travelDate";
	public static final String JSON_PROP_CLASSAVAIL="classAvailInfo";
	public static final String JSON_PROP_TRAINDETAILS="trainDetails";
	public static final String JSON_PROP_RESERVATIONCLASS="reservationClass";
	public static final String JSON_PROP_RESERVATIONTYPE="reservationType";
	public static final String JSON_PROP_PRICING="pricing";
	public static final String JSON_PROP_AVAILABILITYINFO="availabilityDetail";
	public static final String JSON_PROP_AVAILDATE="availablityDate";
	public static final String JSON_PROP_AVAILSTATUS="availablityStatus";
	public static final String JSON_PROP_AVAILTYPE="availablityType";
	public static final String JSON_PROP_AMOUNT="amount";
	public static final String JSON_PROP_CURRENCYCODE="currencyCode";
	public static final String JSON_PROP_TOTAL="totalFare";
	public static final String JSON_PROP_FAREBREAKUP="fareBreakup";
	public static final String JSON_PROP_BASE="baseFare";
	public static final String JSON_PROP_DEPARTTIME="departureTime";
	public static final String JSON_PROP_ARRIVALTIME="arrivalTime";
	public static final String JSON_PROP_JOURNEYDURATION="journeyDuration";
	public static final String JSON_PROP_OPERATIONSCHEDULE="operationSchedule";
	public static final String JSON_PROP_TRAININFO="trainInfo";
	public static final String JSON_PROP_TRAINNUM="trainNumber";
	public static final String JSON_PROP_TRAINNAME="trainName";
	public static final String JSON_PROP_TRAINTYPE="trainType";
	public static final String JSON_PROP_CODECONTEXT="codeContext";
	public static final String JSON_PROP_ANCILLARY="ancillaryCharges";
	public static final String JSON_PROP_FEES="fees";
	public static final String JSON_PROP_TAXES="taxes";
	public static final String JSON_PROP_PAXPREF="passengerPreferences";
	public static final String JSON_PROP_APPBERTH="applicableBerth";
	public static final String JSON_PROP_BOARDINGSTN="boardingStation";
	public static final String JSON_PROP_STATIONCODE="stationCode";
	public static final String JSON_PROP_STATIONNAME="stationName";
	public static final String JSON_PROP_JOURNEYDIST="distance";
	public static final String JSON_PROP_HALTTIME="haltTime";
	public static final String JSON_PROP_DAYCOUNT="dayCount";
	public static final String JSON_PROP_ROUTENUM="route";
	public static final String JSON_PROP_JOURNEYDET="journeyDetails";
	public static final String JSON_PROP_ITINERARY="itinerary";
	
	public static final String JSON_PROP_OPERATIONNAME="operationName";
	public static final String JSON_PROP_BRMSHEADER="header";
	public static final String JSON_PROP_COMMONELEMS="commonElements";
	public static final String JSON_PROP_ADVANCEDEF="advancedDefinition";
	public static final String NS_RAIL="http://www.coxandkings.com/integ/suppl/rail";
	
	//added on 05-04-2018 for cancellation
	public static final String JSON_PROP_UNIQUE_ID="uniqueID";
	public static final String JSON_PROP_UNIQUE_TYPE_ID="uniqueIDType";
	public static final String JSON_PROP_OTA_CancelRSWrapper="ota_CancelRSWrapper";
	public static final String JSON_PROP_SEQUENCE="sequence";
	public static final String JSON_PROP_ID="id";
	public static final String JSON_PROP_ID_CONTEXT="id_Context";
	public static final String JSON_PROP_OTA_CANCELRS="ota_CancelRS";
	public static final String JSON_PROP_CANCEL_DETAILS="cancelDetails";
	public static final String JSON_PROP_AMOUNT_COLLECTED="amountCollected";
	public static final String JSON_PROP_CASH_DEDUCTED="cashDeducted";
	public static final String JSON_PROP_REFUND_AMOUNT="refundAmount";
	public static final String JSON_PROP_REFUND_CURRENCY="currency";
	public static final String JSON_PROP_NAME="name";
	public static final String JSON_PROP_STATUS="status";
	public static final String JSON_PROP_RAIL_TPA="rail_Tpa";
	public static final String JSON_PROP_TPA_EXTENSIONS="tpa_Extensions";
	public static final String JSON_PROP_PASSENGER_STATUS="passengerStatus";
	public static final String PRODUCT="rail";
	public static final String JSON_PROP_CANCELTYPE="cancelType";
	public static final String JSON_PROP_DOCID="docID";
	public static final String JSON_PROP_REQUEST_TYPE="requestType";
	
	//added on 20-04-2018 Rail
	public static final String JSON_PROP_FROM_CONTINENT="fromContinent";
	public static final String JSON_PROP_FROM_COUNTRY="fromCountry";
	public static final String JSON_PROP_FROM_STATE="fromState";
	public static final String JSON_PROP_FROM_CITY="fromCity";
	public static final String JSON_PROP_TO_CONTINENT="toContinent";
	public static final String JSON_PROP_TO_COUNTRY="toCountry";
	public static final String JSON_PROP_TO_STATE="toState";
	public static final String JSON_PROP_TO_CITY="cityTo";
	public static final String JSON_PROP_TICKET_TYPE="ticketType";
	public static final String JSON_PROP_PRODUCT_CATEGORY="productCategory";
	public static final String JSON_PROP_PRODUCT_CATEGORY_SUBTYPE="productCategorySubType";
	public static final String JSON_PROP_PRODUCT_NAME="productName";
	public static final String JSON_PROP_JOURNEYTYPE="journeyType";
	public static final String JSON_PROP_TOTAL_FEES="totalFees";
	public static final String JSON_PROP_TOTAL_PRICE_ANCILLARY="totalPriceAncillary";
	public static final String JSON_PROP_TOTAL_PRICE_TAX="totalPriceTax";
	public static final String JSON_PROP_FARE_NAME="fareName";
	public static final String JSON_PROP_FARE_VALUE="fareValue";
	public static final String JSON_PROP_FARE_DETAILS_VALUE="fareDetailsSet";
	public static final String JSON_PROP_CHARGES_VALUE="charges";
	public static final String JSON_PROP_CONTINENT="continent";
	public static final String JSON_PROP_COUNTRY="country";
	public static final String JSON_PROP_STATE="state";
	public static final String JSON_PROP_CITY="city";
	public static final String JSON_PROP_STANDARD="Standard";
	public static final String JSON_PROP_RETENTION_COMMERCIAL_DETAILS="retentionCommercialDetails";
	public static final String JSON_PROP_PASSENGER_DETAILS="passengerDetails";
	public static final String JSON_PROP_COMMANDS="commands";
	public static final String JSON_PROP_INSERT="insert";
	public static final String JSON_PROP_OBJECT="object";
	public static final String JSON_PROP_RAILCOMMERCIALSRULESROOT="cnk.rail_commercialscalculationengine.clienttransactionalrules.Root";
	public static final String JSON_SALES_DATE="salesDate";
	public static final String JSON_TICKETING_DATE="ticketingDate";
	public static final String JSON_TRAIN_CATEGORY="trainCategory";
	public static final String JSON_TRAIN_LINE_TYPE="trainLineType";
	public static final String JSON_DEAL_CODE="dealCode";
	public static final String JSON_CONNECTIVITY_SUPPLIER_TYPE="connectivitySupplierType";
	public static final String JSON_CONNECTIVITY_SUPPLIER="connectivitySupplier";
	public static final String JSON_BOOKING_TYPE="bookingType";
	public static final String JSON_RETURN_OBJECT="return-object";
	public static final String JSON_ENTRY_POINT="entry-point";
	public static final String JSON_SLAB_DETAILS="slabDetails";
	public static final String JSON_GROUP_MODE="groupMode";
	public static final String JSON_CONTRACT_VALIDITY="contractValidity";
	public static final String JSON_TYPE="type";
	public static final String JSON_PROP_PRODUCT_DETAILS="productDetails";
	public static final String JSON_PROP_CITY_FROM="cityFrom";
	public static final String JSON_PROP_TRAVELER="traveler";
	public static final String JSON_PROP_ENTITY_ID="entityId";
	public static final String JSON_PROP_ENTITY_IDS="entityIds";
	public static final String JSON_PROP_PRODUCT="product";
	
	//added on 08-05-2018
	public static final String JSON_PROP_CITY_TO="cityTo";
	public static final String JSON_PROP_COUNTRY_TO="countryTo";
	public static final String JSON_PROP_COUNTRY_FROM="countryFrom";
	public static final String JSON_PROP_PASS_NAME="passName";
	public static final String JSON_PROP_FARE_TYPE="fareType";
	public static final String JSON_PROP_SEAT_CLASS="seatClass";
	public static final String JSON_PROP_PASSENGER_TYPE="passengerType";
	public static final String JSON_PROP_COMMERCIAL_NAME="commercialName";
	public static final String JSON_PROP_SUPPLIER_NAME="supplierName";
	public static final String JSON_PROP_ADDITIONAL_COMMERCIAL_DETAILS="additionalCommercialDetails";
	public static final String JSON_PROP_TOTAL_RECEIVABLES="totalReceivables";
	public static final String JSON_PROP_TOTAL_PAYABLES="totalPayables";
	public static final String JSON_PROP_COMMERCIAL_AMOUNT="commercialAmount";
	public static final String JSON_PROP_CLIENT_ENTITY_TOTAL_COMMERCIALS="clientEntityTotalCommercials";
	public static final String JSON_PROP_RESERVATION_DETAILS="reservationDetails";
	public static final String JSON_PROP_PNR="pnr";
	public static final String JSON_PROP_CLIENTTRANSACTION_ID="clienttransactionid";
	
	//added for DB kafka msg
	public static final String JSON_PROP_DB_PRODUCT="RAIL";
	public static final String JSON_PROP_REQUEST="request";
	public static final String JSON_PROP_SUPPLIER_REF="supplierRef";
	public static final String JSON_PROP_ARRIVALSTATION_CODE="arrivalStationCode";
	public static final String JSON_PROP_DEPARTURESTATION_CODE="departureStationCode";
	public static final String JSON_PROP_DEPARTUREDATETIME="departureDateTime";
	public static final String JSON_PROP_ARRIVALDATETIME="arrivalDateTime";
	public static final String JSON_PROP_ORIGIN_DESTINATION_DETAILS="originAndDestinationDetails";
	public static final String JSON_PROP_INSURANCE_DETAILS="insuranceDetails";
	public static final String JSON_PROP_RAIL_ARRAY="railArray";
	public static final String JSON_PROP_SUPPLIERTOTALPRICEINFO="supplierTotalPriceInfo";
	public static final String JSON_PROP_ADDRESS_DETAILS="addressDetails";
	public static final String JSON_PROP_ADDRESS="address";
	public static final String JSON_PROP_TELEPHONE="telephone";
	public static final String JSON_PROP_BOOKINGBERTHDETAILS="BookingBerthDetails";
	public static final String JSON_PROP_CURRENTBERTHDETAILS="CurrentBerthDetails";
	public static final String JSON_PROP_KFKA_AMOUNT="Amount";
	public static final String JSON_PROP_CURRENCY="currency";
	public static final String JSON_PROP_PASSENGER_FARE="passengerFare";
	public static final String JSON_PROP_PASSENGER_INFO="passengerInfo";
}
