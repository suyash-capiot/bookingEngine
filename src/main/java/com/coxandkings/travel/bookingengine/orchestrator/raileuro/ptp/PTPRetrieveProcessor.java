package com.coxandkings.travel.bookingengine.orchestrator.raileuro.ptp;


import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.raileuro.RailEuroConfig;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.RailEuropeConstants;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;


public class PTPRetrieveProcessor implements RailEuropeConstants{
	
	private static final Logger logger = LogManager.getLogger(PTPRetrieveProcessor.class);
	
	public static String process(JSONObject reqJson)
	{
		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		Element reqElem = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		JSONObject resJson = new JSONObject();
		JSONObject reqBodyJson = null;
		
		try {
			TrackingContext.setTrackingContext(reqJson);
			opConfig =RailEuroConfig.getOperationConfig("retrieve");
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument(); 
			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./wwr:OTA_WWRailReadRQWrapper");
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			UserContext usrContxt = UserContext.getUserContextForSession(reqHdrJson);
			createHeader(reqElem, reqHdrJson);
			getSupplierCredentials(reqElem, ownerDoc, usrContxt);
			XMLUtils.setValueAtXPath(wrapperElem, "./wwr:Sequence", "0");
			Element suppIDElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./wwr:SupplierID");
			suppIDElem.setTextContent("RAILEUROPE");
			Element railReadRQ = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody/wwr:OTA_WWRailReadRQWrapper/ota:OTA_WWRailReadRQ");
			Element bookingId = ownerDoc.createElementNS(Constants.NS_OTA, "ota:BookingId");
			bookingId.setTextContent(reqBodyJson.optString("supplierBookingId"));
			railReadRQ.appendChild(bookingId);
			wrapperElem.appendChild(railReadRQ);
//			System.out.println(XMLTransformer.toString(reqElem));
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), RailEuroConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
//			System.out.println(XMLTransformer.toString(resElem));
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			//populate retrieve response json
			JSONObject resBodyJson = new JSONObject();
			Element resBodyElem = XMLUtils.getFirstElementAtXPath(resElem,
					"./wwr1:ResponseBody/wwr:OTA_WWRailResRetrieveDetailRSWrapper");
			Element bookingDetails = XMLUtils.getFirstElementAtXPath(resBodyElem,
					"./ota:OTA_WWRailResRetrieveDetailRS/ota:Content/ota:BookingDetailsSearchContentList/ota:BookingDetails");
			resBodyJson.put(JSON_PROP_DOCTYPE_EURONETUSERID, XMLUtils.getValueAtXPath(bookingDetails, "./ota:EuronetUserId"));
			resBodyJson.put(JSON_PROP_DOCTYPE_EURONETUSERNAME, XMLUtils.getValueAtXPath(bookingDetails, "./ota:EuronetUserName"));
			resBodyJson.put(JSON_PROP_SUPPBOOKINGID, XMLUtils.getValueAtXPath(bookingDetails, "./ota:BookingId"));
			resBodyJson.put(JSON_PROP_PRINTINGOPTION, XMLUtils.getValueAtXPath(bookingDetails, "./ota:SelectedPrintingOption"));
			resBodyJson.put(JSON_PROP_AGENTNAME, XMLUtils.getValueAtXPath(bookingDetails, "./ota:AgentName"));
			resBodyJson.put(JSON_PROP_BOOKINGSTATUS, XMLUtils.getValueAtXPath(bookingDetails, "./ota:BookingStatus"));
			resBodyJson.put(JSON_PROP_PAYMENTREADY_CHECK, XMLUtils.getValueAtXPath(bookingDetails, "./ota:IsReadyForPayment"));
			resBodyJson.put(JSON_PROP_BOOKINGPRINTOPTION, XMLUtils.getValueAtXPath(bookingDetails, "./ota:BookingPrintingOptionList"));
			JSONObject contactInformation = new JSONObject();
			JSONObject address = getContactInfo(resBodyJson, bookingDetails, contactInformation);
			resBodyJson.put(JSON_PROP_BOOKINGEXPIRYDATE, XMLUtils.getValueAtXPath(bookingDetails, "./ota:BookingExpirationDate"));
			getLeadPassengerName(resBodyJson, bookingDetails);
			resBodyJson.put(JSON_PROP_DEPARTUREDATE, XMLUtils.getValueAtXPath(bookingDetails, "./ota:DepartureDateToEurope"));
			getAgentInfo(resBodyJson, bookingDetails, contactInformation);
			getTrackingInfo(resBodyJson, bookingDetails);
			getShippingDetails(resBodyJson, bookingDetails, address);
			getBillingDetails(resBodyJson, bookingDetails);
			resBodyJson.put(JSON_PROP_NUMBEROFCOUPONS, XMLUtils.getValueAtXPath(bookingDetails, "./ota:NCouponsPrinted"));
			resBodyJson.put(JSON_PROP_INVOICENUMBER, XMLUtils.getValueAtXPath(bookingDetails, "./ota:InvoiceNumber"));
			//loop PTPProducts
			Element[] bookedPTPProductElement = XMLUtils.getElementsAtXPath(bookingDetails,
					"./ota:PTPProducts/ota:BookedPTPProduct");
			JSONArray ptpProducts = new JSONArray();
			getPTPProducts(bookedPTPProductElement, ptpProducts);
			resBodyJson.put("ptpProducts", ptpProducts);
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			logger.info("SI res: " + resJson);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
		}
		catch (Exception x) {
			x.printStackTrace();
			return "{\"status\": \"ERROR\"}";
		}
		return resJson.toString();
	}

	public static void getPTPProducts(Element[] bookedPTPProductElement, JSONArray ptpProducts) {
		for (Element bookedPTPProd : bookedPTPProductElement)
		{
			JSONObject bookedPTPProduct = new JSONObject();
			bookedPTPProduct.put(JSON_PROP_PRODSTATUS, XMLUtils.getValueAtXPath(bookedPTPProd, "./ota:StatusOfTheProduct"));
			bookedPTPProduct.put(JSON_PROP_BOOKTICKETOPTION, XMLUtils.getValueAtXPath(bookedPTPProd, "./ota:BookedProductAvailableTicketingOptionList"));
			bookedPTPProduct.put(JSON_PROP_ASSGTICKETOPTION, XMLUtils.getValueAtXPath(bookedPTPProd, "./ota:AssignedTicketingOption"));
			bookedPTPProduct.put(JSON_PROP_BOOKEDPRODID, XMLUtils.getValueAtXPath(bookedPTPProd, "./ota:BookedProductId"));
			bookedPTPProduct.put("IsTicketlessEligible", XMLUtils.getValueAtXPath(bookedPTPProd, "./ota:IsTicketlessEligible"));
			getOriginAndDestn(bookedPTPProduct, bookedPTPProd);
			getArrDepDateTime(bookedPTPProduct, bookedPTPProd);
			
			Element[] bookedFaresElement = XMLUtils.getElementsAtXPath(bookedPTPProd,
					"./ota:BookedFares/ota:BookedFare");
			JSONArray bookedFares = new JSONArray();
			getBookedFares(bookedFaresElement, bookedFares);
			bookedPTPProduct.put("bookedFares", bookedFares);
			
			Element[] bookedSegmentsElement = XMLUtils.getElementsAtXPath(bookedPTPProd,
					"./ota:BookedSegments/ota:BookedSegment");
			JSONArray bookedSegments = new JSONArray();
			getBookedSegments(bookedSegmentsElement, bookedSegments);
			bookedPTPProduct.put("bookedSegments", bookedSegments);
			
			bookedPTPProduct.put("railProtectionPlan", XMLUtils.getValueAtXPath(bookedPTPProd, "./ota:RailProtectionPlan"));
			
			Element packagePriceElement =  XMLUtils.getFirstElementAtXPath(bookedPTPProd,
					"./ota:PackagePrice");
			JSONObject  packagePrice = new JSONObject();
			insertCurrencyCodeAndAmt(packagePriceElement, packagePrice);
			bookedPTPProduct.put("packagePrice", packagePrice);
			ptpProducts.put(bookedPTPProduct);
		}
	}

	public static void getBookedSegments(Element[] bookedSegmentsElement, JSONArray bookedSegments) {
		for(Element bookedSegment : bookedSegmentsElement)
		{
			JSONObject bookedSegmentObj = new JSONObject();
			bookedSegmentObj.put("segmentId", XMLUtils.getValueAtXPath(bookedSegment, "./ota:SegmentId"));
			getArrDepDateTime(bookedSegmentObj, bookedSegment);
			getOriginAndDestn(bookedSegmentObj, bookedSegment);
			getTrainDetails(bookedSegment, bookedSegmentObj);
			bookedSegmentObj.put("numberOfPassengers", XMLUtils.getValueAtXPath(bookedSegment, "./ota:NPassengers"));
			bookedSegments.put(bookedSegmentObj);
		}
	}

	public static void getTrainDetails(Element bookedSegment, JSONObject bookedSegmentObj) {
		Element trainElement =  XMLUtils.getFirstElementAtXPath(bookedSegment,"./ota:Train");
		JSONObject trainObj = new JSONObject();
		trainObj.put("trainName", XMLUtils.getValueAtXPath(trainElement, "./ota:TrainName"));
		trainObj.put("equipmentCode", XMLUtils.getValueAtXPath(trainElement, "./ota:EquipmentCode"));
		trainObj.put("trainNumber", XMLUtils.getValueAtXPath(trainElement, "./ota:TrainNumber"));
		trainObj.put("trainCategoryId", XMLUtils.getValueAtXPath(trainElement, "./ota:TrainCategoryId"));
		bookedSegmentObj.put("trainDetails", trainObj);
	}

	public static void getBookedFares(Element[] bookedFaresElement, JSONArray bookedFares) {
		for(Element bookedFare : bookedFaresElement)
		{
			JSONObject bookedFareObj = new JSONObject();
			bookedFareObj.put(JSON_PROP_FAMILYID, XMLUtils.getValueAtXPath(bookedFare, "./ota:FamilyId"));
			bookedFareObj.put(JSON_PROP_FAMILYNAME, XMLUtils.getValueAtXPath(bookedFare, "./ota:FamilyName"));
			bookedFareObj.put("cosCode", XMLUtils.getValueAtXPath(bookedFare, "./ota:CosCode"));
			bookedFareObj.put("fbc", XMLUtils.getValueAtXPath(bookedFare, "./ota:Fbc"));
			bookedFareObj.put("passengerTypeCode", XMLUtils.getValueAtXPath(bookedFare, "./ota:PassengerTypeCode"));
			bookedFareObj.put("fareName", XMLUtils.getValueAtXPath(bookedFare, "./ota:FareName"));
			bookedFareObj.put("fareDescription", XMLUtils.getValueAtXPath(bookedFare, "./ota:FareDescription"));
			
			Element fareRuleDetail =  XMLUtils.getFirstElementAtXPath(bookedFare,"./ota:FareRuleDetail");
			JSONObject FareRuleDetailObj = new JSONObject();
			FareRuleDetailObj.put("afterSaleRules", XMLUtils.getValueAtXPath(fareRuleDetail, "./ota:AfterSaleRules"));
			FareRuleDetailObj.put("webSalesConditions", XMLUtils.getValueAtXPath(fareRuleDetail, "./ota:WebSalesConditions"));
			bookedFareObj.put("fareRuleDetail", FareRuleDetailObj);
			
			bookedFareObj.put(JSON_PROP_NUMBEROFPAX, XMLUtils.getValueAtXPath(bookedFare, "./ota:NPassengers"));
			bookedFareObj.put("infantsTravellingWithAdult", XMLUtils.getValueAtXPath(bookedFare, "./ota:InfantsTravellingWithAdult"));
			bookedFareObj.put("basePassengerType", XMLUtils.getValueAtXPath(bookedFare, "./ota:BasePassengerType"));
			
			// optional-=-=-=-==-=
			
				Element price =  XMLUtils.getFirstElementAtXPath(bookedFare,
						"./ota:Price");
				JSONObject priceObj = new JSONObject();
				insertCurrencyCodeAndAmt(price, priceObj);
				bookedFareObj.put("price", priceObj);
				
				Element commission =  XMLUtils.getFirstElementAtXPath(bookedFare,"./ota:Commission");
				JSONObject commissionObj = new JSONObject();
				insertCurrencyCodeAndAmt(commission, commissionObj);
				bookedFareObj.put("commission", commissionObj);
			
			//-=-=--==-=-=-=-==-=-=-=
				
				bookedFareObj.put("pnr", XMLUtils.getValueAtXPath(bookedFare, "./ota:PNR"));
				getTicketExpiryDate(bookedFare, bookedFareObj);
				
				bookedFareObj.put("immediateTicketing", XMLUtils.getValueAtXPath(bookedFare, "./ota:ImmediateTicketing"));
				bookedFareObj.put("isTicketOnDepartureOptionAvailable", XMLUtils.getValueAtXPath(bookedFare, "./ota:IsTicketOnDepartureOptionAvailable"));
				bookedFareObj.put("isPrintAtHomeOptionAvailable", XMLUtils.getValueAtXPath(bookedFare, "./ota:IsPrintAtHomeOptionAvailable"));
				
				
				Element[] accommodationsElement = XMLUtils.getElementsAtXPath(bookedFare,
						"./ota:Accommodations/ota:Accommodation");
				JSONArray accommodationsArr = new JSONArray();
				getAcco(accommodationsElement, accommodationsArr);
				bookedFareObj.put("accommodations", accommodationsArr);
				
				Element railProtectionPlan =  XMLUtils.getFirstElementAtXPath(bookedFare,"./ota:RailProtectionPlan");
				JSONObject railProtectionPlanObj = new JSONObject();
				railProtectionPlanObj.put("isSelected", XMLUtils.getValueAtXPath(railProtectionPlan, "./ota:IsSelected"));
				railProtectionPlanObj.put("count", XMLUtils.getValueAtXPath(railProtectionPlan, "./ota:Count"));
				bookedFareObj.put("railProtectionPlan", railProtectionPlanObj);
				
				// DOUBT
				Element[] applicableSegmentsElement = XMLUtils.getElementsAtXPath(bookedFare,
						"./ota:ApplicableSegments/ota:SegmentId");
				JSONArray applicableSegmentsArr = new JSONArray();
				getSegmentIDs(price, applicableSegmentsElement, applicableSegmentsArr);
				
				//-=-=-=--
				
				Element[] passengersElement = XMLUtils.getElementsAtXPath(bookedFare,
						"./ota:Passengers/ota:Passenger");
				JSONArray passengers = new JSONArray();
				getPassengerDetails(passengersElement, passengers);
				
				bookedFareObj.put("reservationIncluded", XMLUtils.getValueAtXPath(bookedFare, "./ota:ReservationIncluded"));
				bookedFareObj.put("reservationConfirmationStatus", XMLUtils.getValueAtXPath(bookedFare, "./ota:ReservationConfirmationStatus"));
				
				bookedFares.put(bookedFareObj);	
		}
	}

	public static void getSegmentIDs(Element price, Element[] applicableSegmentsElement,
			JSONArray applicableSegmentsArr) {
		for(Element segments : applicableSegmentsElement)
		{
			JSONObject segmentID = new JSONObject();
			segmentID.put("segmentId", XMLUtils.getValueAtXPath(segments, "./ota:SegmentId"));
			segmentID.put(JSON_PROP_PRICE, price);
			
			applicableSegmentsArr.put(segmentID);
		}
	}
	
	public static void getPassengerDetails(Element[] passengersElement, JSONArray passengers) {
		for(Element passenger : passengersElement)
		{
			JSONObject passengerObj = new JSONObject();
			passengerObj.put(JSON_PROP_TITLE, XMLUtils.getValueAtXPath(passenger, "./ota:Title"));
			
			Element passengerName = XMLUtils.getFirstElementAtXPath(passenger,"./ota:Name");
			JSONObject nameObj = new JSONObject();
			nameObj.put(JSON_PROP_FIRSTNAME, XMLUtils.getValueAtXPath(passengerName, "./ota:FirstName"));
			nameObj.put(JSON_PROP_LASTNAME, XMLUtils.getValueAtXPath(passengerName, "./ota:LastName"));
			passengerObj.put(JSON_PROP_NAME, nameObj);
			
			passengerObj.put(JSON_PROP_AGE, XMLUtils.getValueAtXPath(passenger, "./ota:Age"));
			passengerObj.put(JSON_PROP_GENDER, XMLUtils.getValueAtXPath(passenger, "./ota:Gender"));
			passengerObj.put(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(passenger, "./ota:Type"));
			passengerObj.put(JSON_PROP_COUNTRYOFRESIDENCE, XMLUtils.getValueAtXPath(passenger, "./ota:CountryOfResidence"));
			
			Element additionalInfo = XMLUtils.getFirstElementAtXPath(passenger,"./ota:AdditionalInfo");
			JSONObject additionalInfoObj = new JSONObject();
			additionalInfoObj.put(JSON_PROP_PRIMDRIVERCHECK, XMLUtils.getValueAtXPath(additionalInfo, "./ota:IsPrimaryDriver"));
			additionalInfoObj.put(JSON_PROP_DOB, XMLUtils.getValueAtXPath(additionalInfo, "./ota:DateOfBirth"));
			additionalInfoObj.put(JSON_PROP_PAXQUANTITY, XMLUtils.getValueAtXPath(additionalInfo, "./ota:PaxQuantity"));
			additionalInfoObj.put(JSON_PROP_EMAIL, XMLUtils.getValueAtXPath(additionalInfo, "./ota:Email"));
			passengerObj.put(JSON_PROP_ADDINFO, additionalInfoObj);
			
			passengers.put(passengerObj);
		}
	}

	public static void getAcco(Element[] accommodationsElement, JSONArray accommodationsArr) {
		for(Element acco : accommodationsElement)
		{
			JSONObject accoObj = new JSONObject();
			accoObj.put(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(acco, "./ota:Type"));
			accoObj.put("positionCode", XMLUtils.getValueAtXPath(acco, "./ota:PositionCode"));
			accoObj.put("spaceType", XMLUtils.getValueAtXPath(acco, "./ota:SpaceType"));
			accoObj.put("coachNumber", XMLUtils.getValueAtXPath(acco, "./ota:CoachNumber"));
			accoObj.put("placeNumber", XMLUtils.getValueAtXPath(acco, "./ota:PlaceNumber"));
			accommodationsArr.put(accoObj);
			
		}
	}

	public static void getTicketExpiryDate(Element bookedFare, JSONObject bookedFareObj) {
		Element ticketExpirationDate =  XMLUtils.getFirstElementAtXPath(bookedFare,"./ota:TicketExpirationDate");
		JSONObject ticketExpirationDateObj = new JSONObject();
		ticketExpirationDateObj.put("timezone", XMLUtils.getValueAtXPath(ticketExpirationDate, "./ota:Timezone"));
		insertDateAndTime(ticketExpirationDate, ticketExpirationDateObj);
		bookedFareObj.put("ticketExpirationDate", ticketExpirationDateObj);
	}

	public static void getArrDepDateTime(JSONObject insertIntoJson, Element getFromElement) {
		Element depDateTime =  XMLUtils.getFirstElementAtXPath(getFromElement,"./ota:DepartureDateTime");
		JSONObject depDateTimeObj = new JSONObject();
		insertDateAndTime(depDateTime, depDateTimeObj);
		insertIntoJson.put("departureDateTime", depDateTimeObj);
		
		Element arrDateTime =  XMLUtils.getFirstElementAtXPath(getFromElement,"./ota:ArrivalDateTime");
		JSONObject arrDateTimeObj = new JSONObject();
		insertDateAndTime(arrDateTime, arrDateTimeObj);
		insertIntoJson.put("arrivalDateTime", arrDateTimeObj);
	}

	public static void getOriginAndDestn(JSONObject insertIntoJson, Element getFromElement) {
		Element originCity =  XMLUtils.getFirstElementAtXPath(getFromElement,"./ota:OriginCity");
		JSONObject originCityObj = new JSONObject();
		insertCodeAndDescription(originCity, originCityObj);
		insertIntoJson.put("originCity", originCityObj);
		
		Element originStn =  XMLUtils.getFirstElementAtXPath(getFromElement,"./ota:OriginStation");
		JSONObject originStnObj = new JSONObject();
		insertCodeAndDescription(originStn, originStnObj);
		insertIntoJson.put("originStation", originStnObj);
		
		Element destnCity =  XMLUtils.getFirstElementAtXPath(getFromElement,"./ota:DestinationCity");
		JSONObject destnCityObj = new JSONObject();
		insertCodeAndDescription(destnCity, destnCityObj);
		insertIntoJson.put("destinationCity", destnCityObj);
		
		Element destnStn =  XMLUtils.getFirstElementAtXPath(getFromElement,"./ota:DestinationStation");
		JSONObject destnStnObj = new JSONObject();
		insertCodeAndDescription(destnStn, destnStnObj);
		insertIntoJson.put("destinationStaion", destnStnObj);
	}
	
	public static void createHeader(Element reqElem, JSONObject reqHdrJson) {
		String sessionId = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionId = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userId = reqHdrJson.getString(JSON_PROP_USERID);
		XMLUtils.setValueAtXPath(reqElem, "./wwr:RequestHeader/com:SessionID", sessionId);
		XMLUtils.setValueAtXPath(reqElem, "./wwr:RequestHeader/com:TransactionID", transactionId);
		XMLUtils.setValueAtXPath(reqElem, "./wwr:RequestHeader/com:UserID", userId);
	}
	
	public static void getBillingDetails(JSONObject resBodyJson, Element bookingDetails) {
		JSONObject billingDet = new JSONObject();
		Element billingDetails = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:BillingDetails");
		Element appliedAmount = XMLUtils.getFirstElementAtXPath(billingDetails,"./ota:AppliedAmount");
		JSONObject appliedAmountObj = new JSONObject();
		PTPBookProcessor.insertCurrencyCodeAndAmt(appliedAmount, appliedAmountObj);
		billingDet.put(JSON_PROP_DOCTYPE_APPLIEDAMOUNT, appliedAmountObj);
		Element paidAmount = XMLUtils.getFirstElementAtXPath(billingDetails,"./ota:PaidAmount");
		JSONObject paidAmountObj = new JSONObject();
		PTPBookProcessor.insertCurrencyCodeAndAmt(paidAmount, paidAmountObj);
		billingDet.put(JSON_PROP_DOCTYPE_PAIDAMOUNT, paidAmountObj);
		billingDet.put(JSON_PROP_DOCTYPE_PAYMENTDATE, XMLUtils.getValueAtXPath(billingDetails, "./ota:PaymentDate"));
		billingDet.put(JSON_PROP_DOCTYPE_PAYMENTTYPE, XMLUtils.getValueAtXPath(billingDetails, "./ota:PaymentType"));
		resBodyJson.put(JSON_PROP_BILLINGDET, billingDet);
	}
	
	public static void insertCurrencyCodeAndAmt(Element toPickFrom, JSONObject toInsertInto)
	{
		toInsertInto.put(XML_ATTR_CURRENCYCODE, XMLUtils.getValueAtXPath(toPickFrom, "./ota:CurrencyCode"));
		toInsertInto.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(toPickFrom, "./ota:Amount"));
	}
	
	public static void insertCodeAndDescription(Element toPickFrom, JSONObject toInsertInto)
	{
		toInsertInto.put("code", XMLUtils.getValueAtXPath(toPickFrom, "./ota:Code"));
		toInsertInto.put("description", XMLUtils.getValueAtXPath(toPickFrom, "./ota:Description"));
	}
	
	public static void insertDateAndTime(Element toPickFrom, JSONObject toInsertInto)
	{
		toInsertInto.put("date", XMLUtils.getValueAtXPath(toPickFrom, "./ota:Date"));
		toInsertInto.put("time", XMLUtils.getValueAtXPath(toPickFrom, "./ota:Time"));
	}
	
	public static void getShippingDetails(JSONObject resBodyJson, Element bookingDetails, JSONObject address) {
		JSONObject shippingDet = new JSONObject();
		Element shippingDetails = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:ShippingDetails");
		shippingDet.put(JSON_PROP_PROVIDERDESC, XMLUtils.getValueAtXPath(shippingDetails, "./ota:ProviderTypeDescription"));
		shippingDet.put(JSON_PROP_SERVICETYPEDESC, XMLUtils.getValueAtXPath(shippingDetails, "./ota:ServiceTypeDescription"));
		Element name = XMLUtils.getFirstElementAtXPath(shippingDetails,"./ota:Name");
		JSONObject nameX = new JSONObject();
		nameX.put(JSON_PROP_FIRSTNAME, XMLUtils.getValueAtXPath(name, "./ota:FirstName"));
		nameX.put(JSON_PROP_LASTNAME, XMLUtils.getValueAtXPath(name, "./ota:LastName"));
		shippingDet.put(JSON_PROP_NAME, nameX);
		shippingDet.put(JSON_PROP_ADDRESS, address);
		shippingDet.put(JSON_PROP_PHONE, XMLUtils.getValueAtXPath(shippingDetails, "./ota:Phone"));
		shippingDet.put(JSON_PROP_EMAIL, XMLUtils.getValueAtXPath(shippingDetails, "./ota:Email"));
		resBodyJson.put(JSON_PROP_SHIPPINGINFO, shippingDet);
	}
	
	
	public static void getTrackingInfo(JSONObject resBodyJson, Element bookingDetails) {
		JSONObject trackingInfo = new JSONObject();
		Element trackingInformation = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:TrackingInformation");
		trackingInfo.put(JSON_PROP_BOOKINGORIGINID, XMLUtils.getValueAtXPath(trackingInformation, "./ota:BookingOriginId"));
		trackingInfo.put(JSON_PROP_DISTCHANNELID, XMLUtils.getValueAtXPath(trackingInformation, "./ota:DistributionChannelId"));
		resBodyJson.put(JSON_PROP_TRACKINGINFO, trackingInfo);
	}
	
	public static JSONObject getContactInfo(JSONObject resBodyJson, Element bookingDetails,
			JSONObject contactInformation) {
		Element contactInfo = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:ContactInformation");
		contactInformation.put(JSON_PROP_WORKPHONENUMBER, XMLUtils.getValueAtXPath(contactInfo, "./ota:WorkPhoneNumber"));
		JSONObject address = getAddress(contactInfo);
		contactInformation.put(JSON_PROP_ADDRESS, address);
		resBodyJson.put(JSON_PROP_CONTACTINFO, contactInformation);
		return address;
	}
	
	public static JSONObject getAddress(Element contactInfo) {
		Element addressXML = XMLUtils.getFirstElementAtXPath(contactInfo,"./ota:Address");
		JSONObject address = new JSONObject();
		address.put(JSON_PROP_DOCTYPE_STREETADDR, XMLUtils.getValueAtXPath(addressXML, "./ota:Address1"));
		address.put(JSON_PROP_CITY, XMLUtils.getValueAtXPath(addressXML, "./ota:City"));
		address.put(JSON_PROP_STATE, XMLUtils.getValueAtXPath(addressXML, "./ota:State"));
		address.put(JSON_PROP_ZIP, XMLUtils.getValueAtXPath(addressXML, "./ota:Zip"));
		address.put(JSON_PROP_COUNTRY, XMLUtils.getValueAtXPath(addressXML, "./ota:Country"));
		return address;
	}
	
	public static void getLeadPassengerName(JSONObject resBodyJson, Element bookingDetails) {
		JSONObject leadPassName = new JSONObject();
		Element leadPassDet = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:LeadPassengerName");
		leadPassName.put(JSON_PROP_FIRSTNAME, XMLUtils.getValueAtXPath(leadPassDet, "./ota:FirstName"));
		leadPassName.put(JSON_PROP_LASTNAME, XMLUtils.getValueAtXPath(leadPassDet, "./ota:LastName"));
		resBodyJson.put(JSON_PROP_LEADPAXNAME, leadPassName);
	}
	
	public static void getAgentInfo(JSONObject resBodyJson, Element bookingDetails, JSONObject contactInformation) {
		JSONObject agentInfo = new JSONObject();
		Element agentInformation = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:AgentInformation");
		agentInfo.put(JSON_PROP_AGENTNAME, XMLUtils.getValueAtXPath(agentInformation, "./ota:AgentName"));
		agentInfo.put(JSON_PROP_AGENCYNAME, XMLUtils.getValueAtXPath(agentInformation, "./ota:AgencyName"));
		agentInfo.put(JSON_PROP_IATANUMBER, XMLUtils.getValueAtXPath(agentInformation, "./ota:IataNumber"));
		agentInfo.put(JSON_PROP_CONTACTINFO, contactInformation);
		resBodyJson.put(JSON_PROP_AGENTINFO, agentInfo);
	}
	
	public static void getSupplierCredentials(Element reqElem, Document ownerDoc, UserContext usrContxt) throws Exception {
		List<ProductSupplier> productSuppliers = usrContxt.getSuppliersForProduct(PROD_CATEG_TRANSPORT,
				PROD_CATEG_SUBTYPE_RAILEUROPE);
		Element suppCredsList = XMLUtils.getFirstElementAtXPath(reqElem,
				"./wwr:RequestHeader/com:SupplierCredentialsList");
		if (productSuppliers == null) {
			throw new Exception("Product supplier not found for user/client");
		}
		for (ProductSupplier prodSupplier : productSuppliers) {
			suppCredsList.appendChild(prodSupplier.toElement(ownerDoc));
		}
	}


}