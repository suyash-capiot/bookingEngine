package com.coxandkings.travel.bookingengine.orchestrator.raileuro.ptp;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.raileuro.RailEuroConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.RailEuropeConstants;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class PTPBookProcessor implements RailEuropeConstants{

	private static final Logger logger = LogManager.getLogger(PTPBookProcessor.class);
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException{

		Element reqElem = null;
		JSONObject resJson = new JSONObject();
		JSONObject reqHdrJson = null, reqBodyJson = null;
		KafkaBookProducer bookProducer = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;

		try {
			opConfig = RailEuroConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			bookProducer = new KafkaBookProducer();
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./wwr:OTA_WWRailBookRQWrapper");
			//reqBodyElem.removeChild(wrapperElem);

			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_RAILEUROPE);
			PTPSearchProcessor.createHeader(reqHdrJson, reqElem);

			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./wwr:RequestHeader/com:SupplierCredentialsList");

			for (ProductSupplier prodSupplier : prodSuppliers) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}
			String redisKey = null;
			Map<String, String> searchSuppFaresMap = null;
			try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
				redisKey = String.format("%s%c%s", reqHdrJson.optString(JSON_PROP_SESSIONID), '|', "EuropeanRailPTP");
				searchSuppFaresMap = redisConn.hgetAll(redisKey);
			}
			if (searchSuppFaresMap == null) {   
				throw new Exception(String.format("Search context not found,for %s", redisKey)); 
			}

			XMLUtils.setValueAtXPath(wrapperElem, "./wwr:Sequence", "0");
			Element suppIDElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./wwr:SupplierID");
			suppIDElem.setTextContent("RAILEUROPE");

			Element railBookRQ = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody/wwr:OTA_WWRailBookRQWrapper/ota:OTA_WWRailBookRQ");

			Element depDateToEurope = ownerDoc.createElementNS(NS_OTA, "ota:DepartureDateToEurope");
			depDateToEurope.setTextContent(reqBodyJson.getString("departureDateToEurope"));
			railBookRQ.appendChild(depDateToEurope);

			Element ptpProduct = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PTPProducts");

			JSONArray wwRailInfoArr = reqBodyJson.getJSONArray(JSON_PROP_WWRAILARR);
			//create SI request
			createSIRequest(reqBodyJson, ownerDoc, searchSuppFaresMap, ptpProduct, wwRailInfoArr);
			railBookRQ.appendChild(ptpProduct);
			Element agentBookID = ownerDoc.createElementNS(Constants.NS_OTA, "ota:AgentBookingId");
			railBookRQ.appendChild(agentBookID);
			Element agentName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:AgentName");
			railBookRQ.appendChild(agentName);
			Element email = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Email");
			email.setTextContent(reqBodyJson.optString(JSON_PROP_EMAIL));
			railBookRQ.appendChild(email);
			wrapperElem.appendChild(railBookRQ);
//			System.out.println(XMLTransformer.toString(reqElem));

			sendPreBookingKafkaMessage(bookProducer, reqJson, usrCtx);
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}

		try {
			Element resElem = null;
			
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), RailEuroConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
//			System.out.println(XMLTransformer.toString(resElem));
			if(reqBodyJson.optBoolean(JSON_PROP_ISTIMELIMIT)) {
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.TIME_LIMIT_BOOKING,
						"", reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
			}else {
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.BOOKING,
						"", reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
			}
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			JSONObject resBodyJson = new JSONObject();
			//			JSONObject resJson = new JSONObject();
			//populate book response json
			Element resBodyElem = XMLUtils.getFirstElementAtXPath(resElem,
					"./wwr1:ResponseBody/wwr:OTA_WWRailBookRSWrapper");
//			System.out.println(XMLTransformer.toString(resBodyElem));
			resBodyJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(resBodyElem, "./wwr:SupplierID"));
			Element bookingDetails = XMLUtils.getFirstElementAtXPath(resBodyElem,
					"./ota:OTA_WWRailBookRS/ota:Content/ota:BookingCreationContentList/ota:BookingDetails");
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
			
			getShippingDetails(resBodyJson, bookingDetails, address);
			
			//if amount is not needed in book response, comment this block-=--=-=-=-=-=
			getBillingDetails(resBodyJson, bookingDetails);
			//=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-==-=-=-=-=-=-=-=-=-=-=-
			
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
			
			sendPostBookingKafkaMessage(bookProducer, reqJson, resJson);
			
			
			logger.info("SI res: " + resJson);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));

		}
		catch(Exception x) {
			logger.warn(String.format("An exception occured when booking Car"), x);
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
	
	public static void getTicketExpiryDate(Element bookedFare, JSONObject bookedFareObj) {
		Element ticketExpirationDate =  XMLUtils.getFirstElementAtXPath(bookedFare,"./ota:TicketExpirationDate");
		JSONObject ticketExpirationDateObj = new JSONObject();
		ticketExpirationDateObj.put("timezone", XMLUtils.getValueAtXPath(ticketExpirationDate, "./ota:Timezone"));
		insertDateAndTime(ticketExpirationDate, ticketExpirationDateObj);
		bookedFareObj.put("ticketExpirationDate", ticketExpirationDateObj);
	}
	
	public static void getAcco(Element[] accommodationsElement, JSONArray accommodationsArr) {
		for(Element acco : accommodationsElement)
		{
			JSONObject accoObj = new JSONObject();
			accoObj.put(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(acco, "./ota:Type"));
			accoObj.put("positionCode", XMLUtils.getValueAtXPath(acco, "./ota:PositionCode"));
			accoObj.put("spaceType", XMLUtils.getValueAtXPath(acco, "./ota:SpaceType"));
			accoObj.put("coachNumber", XMLUtils.getValueAtXPath(acco, "./CoachNumber"));
			accoObj.put("placeNumber", XMLUtils.getValueAtXPath(acco, "./ota:PlaceNumber"));
			accommodationsArr.put(accoObj);
			
		}
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
	
	
	
	public static void getTrackingInfo(JSONObject resBodyJson, Element bookingDetails) {
		JSONObject trackingInfo = new JSONObject();
		Element trackingInformation = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:TrackingInformation");
		trackingInfo.put(JSON_PROP_BOOKINGORIGINID, XMLUtils.getValueAtXPath(trackingInformation, "./ota:BookingOriginId"));
		trackingInfo.put(JSON_PROP_DISTCHANNELID, XMLUtils.getValueAtXPath(trackingInformation, "./ota:DistributionChannelId"));
		resBodyJson.put(JSON_PROP_TRACKINGINFO, trackingInfo);
	}


	public static void getLeadPassengerName(JSONObject resBodyJson, Element bookingDetails) {
		JSONObject leadPassName = new JSONObject();
		Element leadPassDet = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:LeadPassengerName");
		leadPassName.put(JSON_PROP_FIRSTNAME, XMLUtils.getValueAtXPath(leadPassDet, "./ota:FirstName"));
		leadPassName.put(JSON_PROP_LASTNAME, XMLUtils.getValueAtXPath(leadPassDet, "./ota:LastName"));
		resBodyJson.put(JSON_PROP_LEADPAXNAME, leadPassName);
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


	public static void getBookedPassProducts(Element[] bookedPassProductElement, JSONArray passProducts) {
		for (Element bookedPassProd : bookedPassProductElement)
		{
			JSONObject bookedPassProduct = new JSONObject();
			bookedPassProduct.put(JSON_PROP_PRODSTATUS, XMLUtils.getValueAtXPath(bookedPassProd, "./ota:StatusOfTheProduct"));
			bookedPassProduct.put(JSON_PROP_BOOKTICKETOPTION, XMLUtils.getValueAtXPath(bookedPassProd, "./ota:BookedProductAvailableTicketingOptionList"));
			bookedPassProduct.put(JSON_PROP_ASSGTICKETOPTION, XMLUtils.getValueAtXPath(bookedPassProd, "./ota:AssignedTicketingOption"));
			bookedPassProduct.put(JSON_PROP_BOOKEDPRODID, XMLUtils.getValueAtXPath(bookedPassProd, "./ota:BookedProductId"));
			bookedPassProduct.put(JSON_PROP_FAMILYID, XMLUtils.getValueAtXPath(bookedPassProd, "./ota:FamilyId"));
			bookedPassProduct.put(JSON_PROP_PRODUCTID, XMLUtils.getValueAtXPath(bookedPassProd, "./ota:ProductId"));
			bookedPassProduct.put(JSON_PROP_FAMILYNAME, XMLUtils.getValueAtXPath(bookedPassProd, "./ota:FamilyName"));
			bookedPassProduct.put(JSON_PROP_PRODNAME, XMLUtils.getValueAtXPath(bookedPassProd, "./ota:ProductName"));
			bookedPassProduct.put(JSON_PROP_PASSTYPE, XMLUtils.getValueAtXPath(bookedPassProd, "./ota:PassType"));
			bookedPassProduct.put(JSON_PROP_CLASSOFSERV, XMLUtils.getValueAtXPath(bookedPassProd, "./ota:PhysicalClassOfService"));
			
			Element[] passengersElement = XMLUtils.getElementsAtXPath(bookedPassProd,
					"./ota:Passengers/ota:Passenger");
			JSONArray passengers = new JSONArray();
			getPassengerDetails(passengersElement, passengers);
			
			bookedPassProduct.put("nAdditionalRailDays", XMLUtils.getValueAtXPath(bookedPassProd, "./ota:NAdditionalRailDays"));
			bookedPassProduct.put("nAdditionalCarDays", XMLUtils.getValueAtXPath(bookedPassProd, "./ota:NAdditionalCarDays"));
			bookedPassProduct.put("nAdditionalCountries", XMLUtils.getValueAtXPath(bookedPassProd, "./ota:NAdditionalCountries"));
			
			Element railProtecPlan = XMLUtils.getFirstElementAtXPath(bookedPassProd,"./ota:RailProtectionPlan");
			JSONObject railProtectionPlan = new JSONObject();
			railProtectionPlan.put(JSON_PROP_CHECKED, XMLUtils.getValueAtXPath(railProtecPlan, "./ota:IsSelected"));
			railProtectionPlan.put("count", XMLUtils.getValueAtXPath(railProtecPlan, "./ota:Count"));
			bookedPassProduct.put(JSON_PROP_RAILPROTECTPLAN, railProtectionPlan);
			
			Element priceElem = XMLUtils.getFirstElementAtXPath(bookedPassProd,"./ota:Price");
			JSONObject price = new JSONObject();
			price.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(priceElem, "./ota:CurrencyCode"));
			price.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(priceElem, "./ota:Amount"));
			bookedPassProduct.put(JSON_PROP_PRICE, price);
			
			passProducts.put(bookedPassProduct);
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


	public static void getBillingDetails(JSONObject resBodyJson, Element bookingDetails) {
		JSONObject billingDet = new JSONObject();
		Element billingDetails = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:BillingDetails");
		Element amountDueX = XMLUtils.getFirstElementAtXPath(billingDetails,"./ota:AmountDue");
		JSONObject amountDue = new JSONObject();
		insertCurrencyCodeAndAmt(amountDueX, amountDue);
		billingDet.put(JSON_PROP_DUEAMOUNT, amountDue);
		resBodyJson.put(JSON_PROP_BILLINGDET, billingDet);
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


	public static void getAgentInfo(JSONObject resBodyJson, Element bookingDetails, JSONObject contactInformation) {
		JSONObject agentInfo = new JSONObject();
		Element agentInformation = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:AgentInformation");
		agentInfo.put(JSON_PROP_AGENTNAME, XMLUtils.getValueAtXPath(agentInformation, "./ota:AgentName"));
		agentInfo.put(JSON_PROP_AGENCYNAME, XMLUtils.getValueAtXPath(agentInformation, "./ota:AgencyName"));
		agentInfo.put(JSON_PROP_IATANUMBER, XMLUtils.getValueAtXPath(agentInformation, "./ota:IataNumber"));
		agentInfo.put(JSON_PROP_CONTACTINFO, contactInformation);
		resBodyJson.put(JSON_PROP_AGENTINFO, agentInfo);
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
	
	
	private static void sendPreBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, UserContext usrCtx) throws Exception {
		//JSONObject kafkaMsgJson = reqJson;
		JSONObject kafkaMsg=new JSONObject(new JSONTokener(reqJson.toString()));
		JSONObject reqHeaderJson = kafkaMsg.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, "EuroBookingPTP");
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put("operation", "book");
		bookProducer.runProducer(1, kafkaMsg);
//		System.out.println(kafkaMsg);
		logger.trace(String.format("European Rail Book Request Kafka Message: %s", kafkaMsg.toString()));
	}
	
	private static void sendPostBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, JSONObject resJson) throws Exception {
		JSONObject kafkaMsgJson = resJson;
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PROD, "EuroBookingPTP");
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqBodyJson.getString(JSON_PROP_BOOKID));
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("operation", "book");
		bookProducer.runProducer(1, kafkaMsgJson);
//		System.out.println(kafkaMsgJson);
	}

	public static void insertCurrencyCodeAndAmt(Element toPickFrom, JSONObject toInsertInto)
	{
		toInsertInto.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(toPickFrom, "./ota:CurrencyCode"));
		toInsertInto.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(toPickFrom, "./ota:Amount"));
	}

	public static void createSIRequest(JSONObject reqBodyJson, Document ownerDoc,
			Map<String, String> searchSuppFaresMap, Element ptpProduct, JSONArray wwRailInfoArr) throws Exception {

		for (int i = 0; i < wwRailInfoArr.length(); i++) {
			JSONArray solutionsArr = wwRailInfoArr.getJSONObject(i).getJSONArray(JSON_PROP_SOLUTIONS);
			Element bookingPTPProduct =  ownerDoc.createElementNS(Constants.NS_OTA, "ota:BookingPTPProduct");
			for(int j = 0; j < solutionsArr.length(); j++) {
				JSONObject solutionObj = solutionsArr.getJSONObject(j);
				JSONObject packageDtls = solutionObj.getJSONObject(JSON_PROP_PACKAGEDETAILS);
				JSONObject pricingInfo = packageDtls.getJSONObject("totalPriceInfo");
				
				String pckgFareIDasKey = PTPSearchProcessor.getRedisKeyForPackageDetails(packageDtls);
				//Append Commercials and Fares in KafkaBookReq For Database Population
				String suppPriceBookInfo = searchSuppFaresMap.get(pckgFareIDasKey);
				if(suppPriceBookInfo==null) {
					throw new Exception(String.format("SupplierPriceInfo not found for solution with packageID %s ", pckgFareIDasKey)); 
				}
				
				JSONObject suppPriceBookInfoJson = new JSONObject(suppPriceBookInfo);
				JSONObject suppPricingInfo = suppPriceBookInfoJson.getJSONObject("supplierPricingInfo");
				packageDtls.put("supplierPricingInfo", suppPricingInfo);
				JSONArray clientCommercialItinTotalInfoArr = suppPriceBookInfoJson.getJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
				pricingInfo.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercialItinTotalInfoArr);
				
				
				if(solutionObj.getString(JSON_PROP_ITINERARYTYPE).equalsIgnoreCase("forward")) {
					Element forwardFare =  ownerDoc.createElementNS(Constants.NS_OTA, "ota:ForwardFare");
					forwardFare.setTextContent(packageDtls.getString("packageFareId"));
					bookingPTPProduct.appendChild(forwardFare);
					if(j<1) {
						JSONObject returnFareObject = solutionsArr.getJSONObject(j+1);
						JSONObject packageDtlsReturn = returnFareObject.getJSONObject(JSON_PROP_PACKAGEDETAILS);
						Element returnFare =  ownerDoc.createElementNS(Constants.NS_OTA, "ota:ReturnFare");
						returnFare.setTextContent(packageDtlsReturn.getString("packageFareId"));
						bookingPTPProduct.appendChild(returnFare);
					}
					
					Element ticketingOptionSelected =  ownerDoc.createElementNS(Constants.NS_OTA, "ota:TicketingOptionSelected");
					ticketingOptionSelected.setTextContent(packageDtls.getString("ticketingOptionSelected"));
					bookingPTPProduct.appendChild(ticketingOptionSelected);
					
					Element passengers =  ownerDoc.createElementNS(Constants.NS_OTA, "ota:Passengers");
					JSONArray paxDetailsArr = packageDtls.getJSONArray("paxDetails");
					populatePassengers(ownerDoc, bookingPTPProduct, passengers, paxDetailsArr);
					
					Element railProtPlan = ownerDoc.createElementNS(Constants.NS_OTA, "ota:RailProtectionPlan");
//					set this value when it comes from SI
					bookingPTPProduct.appendChild(railProtPlan);
					
					Element firstTravelDate =  ownerDoc.createElementNS(Constants.NS_OTA, "ota:FirstTravelDate");
					firstTravelDate.setTextContent(DATE_FORMAT.format(new Date()));
					bookingPTPProduct.appendChild(firstTravelDate);
					
					getFarePreferences(ownerDoc, bookingPTPProduct, packageDtls);
				}
				ptpProduct.appendChild(bookingPTPProduct);
			}
		}

	}


	public static void getFarePreferences(Document ownerDoc, Element bookingPTPProduct, JSONObject packageDtls) {
		Element farePreferences = ownerDoc.createElementNS(Constants.NS_OTA, "ota:FarePreferences");					
		JSONArray productFare = packageDtls.getJSONArray(JSON_PROP_PRODUCTFARE);
		for(int k = 0; k< productFare.length(); k++) {
			JSONObject productFareObj = productFare.getJSONObject(k);
			JSONArray farePrefArr = productFareObj.getJSONArray("farePreferences");
			for(int l=0; l<farePrefArr.length();l++)
			{
				JSONObject farePrefObj = farePrefArr.getJSONObject(l);
				Element farePreference = ownerDoc.createElementNS(Constants.NS_OTA, "ota:FarePreference");
				Element segmentId = ownerDoc.createElementNS(Constants.NS_OTA, "ota:SegmentId");
				segmentId.setTextContent(farePrefObj.optString("segmentId"));
				Element preferenceType = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PreferenceType");
				preferenceType.setTextContent(farePrefObj.optString("preferenceType"));
				Element preferenceCode = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PreferenceCode");
//				preferenceType.setTextContent(farePrefObj.optString("preferenceCode"));
				Element isInbound = ownerDoc.createElementNS(Constants.NS_OTA, "ota:IsInbound");
				Element attributes = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Attributes");
				Element attribute = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Attribute");
				Element attrName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Name");
				attrName.setTextContent(farePrefObj.optString("attributeName"));
				Element attrValue = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Value");
				attrValue.setTextContent(farePrefObj.optString("attributeValue"));
				attribute.appendChild(attrName);
				attribute.appendChild(attrValue);
				attributes.appendChild(attribute);
				farePreference.appendChild(segmentId);
				farePreference.appendChild(preferenceType);
				farePreference.appendChild(preferenceCode);
				farePreference.appendChild(isInbound);
				farePreference.appendChild(attributes);
				
				farePreferences.appendChild(farePreference);
			}
		}
		bookingPTPProduct.appendChild(farePreferences);
	}

	public static void populatePassengers(Document ownerDoc, Element bookingPTPProduct, Element passengers,
			JSONArray paxDetailsArr) {
		for (int j=0; j<paxDetailsArr.length();j++)
		{
			JSONObject paxDetails = paxDetailsArr.getJSONObject(j);
			Element passenger = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Passenger");
			//append below to passenger
			Element title = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Title");
			title.setTextContent(paxDetails.optString("title"));
			Element name = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Name");
			//append below 3 to name
			Element firstName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:FirstName");
			firstName.setTextContent(paxDetails.optString("firstName"));
			Element lastName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:LastName");
			lastName.setTextContent(paxDetails.optString("surname"));
			Element middleName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:MiddleName");
			middleName.setTextContent(paxDetails.optString("middleName"));
			name.appendChild(firstName);
			name.appendChild(lastName);
			name.appendChild(middleName);
			//-=-=-=-=-==-==-=
			Element age = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Age");
			//get age
			//						StringBuilder ageFormatted = new StringBuilder();
			//						ageFormatted.append(paxDetails.optString("dob"));
			//						ageFormatted=ageFormatted.reverse();
			int finalAge = calculateAge( paxDetails.optString("dob"));
			age.setTextContent(finalAge+"");
			//-=-==-=-=-
			Element gender = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Gender");
			gender.setTextContent(paxDetails.optString("gender"));
			Element type = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Type");
			type.setTextContent(paxDetails.optString("paxType"));
			Element countryOfRes = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CountryOfResidence");
			countryOfRes.setTextContent(paxDetails.getJSONObject("addressDetails").optString("country"));

			Element additionalInfo = ownerDoc.createElementNS(Constants.NS_OTA, "ota:AdditionalInfo");
			//append below to additionalInfo
			Element dateOfBirth = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DateOfBirth");
			dateOfBirth.setTextContent(paxDetails.optString("dob"));
			/*Element passportNumber = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PassportNumber");
			if(paxDetails.optJSONObject("documentDetails")!=null)
			{
				if(paxDetails.getJSONObject("documentDetails").optJSONArray("documentInfo")!=null)
				{
					String docType = paxDetails.optJSONObject("documentDetails").optJSONArray("documentInfo").optJSONObject(j).optString("docType");
					passportNumber.setTextContent(docType.equals("Passport")? paxDetails.optJSONObject("documentDetails").optJSONArray("documentInfo").optJSONObject(j).optString("docNumber"): "abcdefg");
				}

			}*/
			//tag paxQuantity is not important
			Element email = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Email");
			email.setTextContent(paxDetails.getJSONArray("contactDetails").getJSONObject(0).optString("email"));
			additionalInfo.appendChild(dateOfBirth);
			//additionalInfo.appendChild(passportNumber);
			additionalInfo.appendChild(email);
			//=-=-=-=-=-==
			passenger.appendChild(title);
			passenger.appendChild(name);
			passenger.appendChild(age);
			passenger.appendChild(gender);
			passenger.appendChild(type);
			passenger.appendChild(countryOfRes);
			passenger.appendChild(additionalInfo);
			passengers.appendChild(passenger);
			bookingPTPProduct.appendChild(passengers);
		}
	}

	public static int calculateAge(String birthdate) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		int age = 1;
		if(birthdate==null || birthdate.isEmpty())
			return age;
		if (birthdate.matches("([0-9]{4})-([0-9]{2})-([0-9]{2})")) {
			LocalDate dob = LocalDate.parse(birthdate);
			LocalDate curDate = LocalDate.now();
			age = Period.between(dob, curDate).getYears();
		} else if (birthdate.matches("([0-9]{2})-([0-9]{2})-([0-9]{4})")) {
			LocalDate dob = LocalDate.parse(birthdate, formatter);
			LocalDate curDate = LocalDate.now();
			age = Period.between(dob, curDate).getYears();
		}
		return age;
	}


}
