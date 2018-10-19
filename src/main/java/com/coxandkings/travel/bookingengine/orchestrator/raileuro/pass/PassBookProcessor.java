package com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Date;
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
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class PassBookProcessor implements RailEuropeConstants{
	
	private static final Logger logger = LogManager.getLogger(PassBookProcessor.class);
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException
	{
		Element reqElem = null;
		JSONObject resJson = new JSONObject();
		JSONObject reqHdrJson = null, reqBodyJson = null;
		KafkaBookProducer bookProducer = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		try {
			    opConfig =RailEuroConfig.getOperationConfig("book");
			    //kafka
				bookProducer = new KafkaBookProducer();
				
			    reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
				Document ownerDoc = reqElem.getOwnerDocument(); 
				
				Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody");
//				System.out.println(XMLTransformer.toString(reqBodyElem));
				Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./wwr:OTA_WWRailBookRQWrapper");
//				System.out.println(XMLTransformer.toString(wrapperElem));
				
				reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
				reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
				UserContext usrContxt = UserContext.getUserContextForSession(reqHdrJson);
				
//				String sessionId = reqHdrJson.getString("sessionID");
//				String transactionId = reqHdrJson.getString("transactionID");
//				String userId = reqHdrJson.getString("userID");
//				XMLUtils.setValueAtXPath(reqElem, "./wwr:RequestHeader/com:SessionID", sessionId);
//				XMLUtils.setValueAtXPath(reqElem, "./wwr:RequestHeader/com:TransactionID", transactionId);
//				XMLUtils.setValueAtXPath(reqElem, "./wwr:RequestHeader/com:UserID", userId);
				
				PassSearchProcessor.createHeader(reqElem, reqHdrJson);
				
				//supplier credentials 
				PassSearchProcessor.getSupplierCredentials(reqElem, ownerDoc, usrContxt);
				
				String redisKey = null;
				Map<String, String> suppFaresMap = null;
				try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
					redisKey = String.format("%s%c%s", reqHdrJson.optString(JSON_PROP_SESSIONID), '|', "EuropeanRail");
					suppFaresMap = redisConn.hgetAll(redisKey);
				}
				if (suppFaresMap == null) {
					throw new Exception(String.format("Fares not found,for %s", redisKey));
				}
				
				XMLUtils.setValueAtXPath(wrapperElem, "./wwr:Sequence", "0");
				Element suppIDElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./wwr:SupplierID");
				suppIDElem.setTextContent(JSON_PROP_SUPPID);
				
				
				Element railBookRQ = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody/wwr:OTA_WWRailBookRQWrapper/ota:OTA_WWRailBookRQ");
//				System.out.println(XMLTransformer.toString(railBookRQ));
				
				Element depDateToEurope = ownerDoc.createElementNS(NS_OTA, "ota:DepartureDateToEurope");
				depDateToEurope.setTextContent(DATE_FORMAT.format(new Date()));
				railBookRQ.appendChild(depDateToEurope);
				
				Element passProduct = ownerDoc.createElementNS(NS_OTA, "ota:PassProducts");
				
				JSONArray passProductsArr = reqBodyJson.getJSONArray(JSON_PROP_PASSPROD);
				//create SI request
				createSIRequest(reqBodyJson, ownerDoc, suppFaresMap, passProduct, passProductsArr);
				
				Element agentBookingId = ownerDoc.createElementNS(NS_OTA, "ota:AgentBookingId");
				agentBookingId.setTextContent(reqBodyJson.optString("agentBookingId"));
				
				Element email = ownerDoc.createElementNS(NS_OTA, "ota:Email");
				//System.out.println(reqBodyJson);
				email.setTextContent(reqBodyJson.optString(JSON_PROP_EMAIL));
				//System.out.println(XMLTransformer.toString(email));
				railBookRQ.appendChild(passProduct);
				railBookRQ.appendChild(agentBookingId);
				railBookRQ.appendChild(email);
				wrapperElem.appendChild(railBookRQ);
				//System.out.println(XMLTransformer.toString(reqElem));
				
				 sendPreBookingKafkaMessage(bookProducer, reqJson,usrContxt);
				 
				
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
			//System.out.println(XMLTransformer.toString(resBodyElem));
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
			
			resBodyJson.put(JSON_PROP_DEPARTUREDATETOEUR, XMLUtils.getValueAtXPath(bookingDetails, "./ota:DepartureDateToEurope"));
			
			getAgentInfo(resBodyJson, bookingDetails, contactInformation);
			
			getTrackingInfo(resBodyJson, bookingDetails);
			
			getShippingDetails(resBodyJson, bookingDetails, address);
			
			//if amount is not needed in book response, comment this block-=--=-=-=-=-=
			getBillingDetails(resBodyJson, bookingDetails);
			//=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-==-=-=-=-=-=-=-=-=-=-=-
			
			resBodyJson.put(JSON_PROP_NUMBEROFCOUPONS, XMLUtils.getValueAtXPath(bookingDetails, "./ota:NCouponsPrinted"));
			resBodyJson.put(JSON_PROP_INVOICENUMBER, XMLUtils.getValueAtXPath(bookingDetails, "./ota:InvoiceNumber"));
			
			//loop passProducts
			Element[] bookedPassProductElement = XMLUtils.getElementsAtXPath(bookingDetails,
					"./ota:PassProducts/ota:BookedPassProduct");
			
			JSONArray passProducts = new JSONArray();
			getBookedPassProducts(bookedPassProductElement, passProducts);
			
			resBodyJson.put(JSON_PROP_PASSPROD, passProducts);
			
			//final Total Price
			Element totalRemainingToBePaid =  XMLUtils.getFirstElementAtXPath(bookingDetails,
					"./ota:TotalRemainingToBePaid");
			JSONObject totalAmountToBePaid = new JSONObject();
			insertCurrencyCodeAndAmt(totalRemainingToBePaid, totalAmountToBePaid);
			resBodyJson.put(JSON_PROP_REMAININGTOTAL, totalAmountToBePaid);
			
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			
			sendPostBookingKafkaMessage(bookProducer, reqJson, resJson);
			
			logger.info("SI res: " + resJson);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
			
		}
		catch(Exception x) {
			logger.warn(String.format("An exception occured when booking trainPass"), x);
			return "{\"status\": \"ERROR\"}";
		}
		
		
		return resJson.toString();
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
			railProtectionPlan.put(JSON_PROP_COUNT, XMLUtils.getValueAtXPath(railProtecPlan, "./ota:Count"));
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
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, "EuroBookingPass");
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put("operation", "book");
		bookProducer.runProducer(1, kafkaMsg);
		//System.out.println(kafkaMsg);
		logger.trace(String.format("European Rail Book Request Kafka Message: %s", kafkaMsg.toString()));
	}
	
	private static void sendPostBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, JSONObject resJson) throws Exception {
		JSONObject kafkaMsgJson = resJson;
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PROD, "EuroBookingPass");
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqBodyJson.getString(JSON_PROP_BOOKID));
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("operation", "book");
		bookProducer.runProducer(1, kafkaMsgJson);
//		System.out.println(kafkaMsgJson);
	}
	

	public static void createSIRequest(JSONObject reqBodyJson, Document ownerDoc, Map<String, String> suppFaresMap,
			Element passProduct, JSONArray passProductsArr) throws Exception {
		for (int i = 0; i < passProductsArr.length(); i++)
		{
			JSONObject passProducts = passProductsArr.getJSONObject(i);
			JSONObject pricingInfo = passProducts.getJSONObject(JSON_PROP_PRICINGINFO);
//			System.out.println(passProductsArr);
			String productIDasKey = PassSearchProcessor.getRedisKeyForPassProducts(passProducts);
			//Append Commercials and Fares in KafkaBookReq For Database Population
			String suppPriceBookInfo = suppFaresMap.get(productIDasKey);
			if(suppPriceBookInfo==null) {
				throw new Exception(String.format("SupplierPriceInfo not found for passProduct with productID %s ", productIDasKey)); 
			}
			JSONObject suppPriceBookInfoJson = new JSONObject(suppPriceBookInfo);
			JSONObject suppPricingInfo = suppPriceBookInfoJson.getJSONObject(JSON_PROP_SUPPPRICINFO);
			passProducts.put(JSON_PROP_SUPPPRICINFO, suppPricingInfo);
			JSONArray clientCommercialItinTotalInfoArr = suppPriceBookInfoJson.getJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			pricingInfo.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercialItinTotalInfoArr);
			
			//POPULATE BODY
			Element bookingPassProduct = ownerDoc.createElementNS(NS_OTA, "ota:BookingPassProduct"); //append this to passP
			Element productID = ownerDoc.createElementNS(NS_OTA, "ota:ProductId");
			productID.setTextContent(passProducts.optString(JSON_PROP_PRODUCTID));
			bookingPassProduct.appendChild(productID);
			Element passengers = ownerDoc.createElementNS(NS_OTA, "ota:Passengers");
			
//			JSONArray paxDetailsArr = reqBodyJson.getJSONArray(JSON_PROP_PAXDETAILS);
			JSONArray paxDetailsArr = passProducts.getJSONArray(JSON_PROP_PAXDETAILS);
//			System.out.println(paxDetailsArr);
			populatePassengers(ownerDoc, bookingPassProduct, passengers, paxDetailsArr);
			Element firstTravelDate = ownerDoc.createElementNS(NS_OTA, "ota:FirstTravelDate");
			firstTravelDate.setTextContent(DATE_FORMAT.format(new Date()));   
			bookingPassProduct.appendChild(firstTravelDate);
			passProduct.appendChild(bookingPassProduct);
		}
	}

	public static void populatePassengers(Document ownerDoc, Element bookingPassProduct, Element passengers,
			JSONArray paxDetailsArr) {
		for (int j=0; j<paxDetailsArr.length();j++)
		{
			JSONObject paxDetails = paxDetailsArr.getJSONObject(j);
			Element passenger = ownerDoc.createElementNS(NS_OTA, "ota:Passenger");
			//append below to passenger
			Element title = ownerDoc.createElementNS(NS_OTA, "ota:Title");
			title.setTextContent(paxDetails.optString(JSON_PROP_TITLE));
			Element name = ownerDoc.createElementNS(NS_OTA, "ota:Name");
			//append below 3 to name
			Element firstName = ownerDoc.createElementNS(NS_OTA, "ota:FirstName");
			firstName.setTextContent(paxDetails.optString(JSON_PROP_FIRSTNAME));
			Element lastName = ownerDoc.createElementNS(NS_OTA, "ota:LastName");
			lastName.setTextContent(paxDetails.optString(JSON_PROP_SURNAME));
			Element middleName = ownerDoc.createElementNS(NS_OTA, "ota:MiddleName");
			middleName.setTextContent(paxDetails.optString(JSON_PROP_MIDDLENAME));
			name.appendChild(firstName);
			name.appendChild(lastName);
			name.appendChild(middleName);
			//-=-=-=-=-==-==-=
			Element age = ownerDoc.createElementNS(NS_OTA, "ota:Age");
			//get age
//						StringBuilder ageFormatted = new StringBuilder();
//						ageFormatted.append(paxDetails.optString("dob"));
//						ageFormatted=ageFormatted.reverse();
			int finalAge = calculateAge( paxDetails.optString(JSON_PROP_DATEOFBIRTH));
			age.setTextContent(finalAge+"");
			//-=-==-=-=-
			Element gender = ownerDoc.createElementNS(NS_OTA, "ota:Gender");
			gender.setTextContent(paxDetails.optString(JSON_PROP_GENDER));
			Element type = ownerDoc.createElementNS(NS_OTA, "ota:Type");
			type.setTextContent(paxDetails.optString(JSON_PROP_PAXTYPE));
//			System.out.println(XMLTransformer.toString(type));
			Element countryOfRes = ownerDoc.createElementNS(NS_OTA, "ota:CountryOfResidence");
			countryOfRes.setTextContent(paxDetails.getJSONObject("addressDetails").optString("country"));
			
			Element additionalInfo = ownerDoc.createElementNS(NS_OTA, "ota:AdditionalInfo");
			//append below to additionalInfo
			Element dateOfBirth = ownerDoc.createElementNS(NS_OTA, "ota:DateOfBirth");
			dateOfBirth.setTextContent(paxDetails.optString(JSON_PROP_DATEOFBIRTH));
			Element passportNumber = ownerDoc.createElementNS(NS_OTA, "ota:PassportNumber");
			if(paxDetails.optJSONObject(JSON_PROP_DOCDTLS)!=null)
			{
				if(paxDetails.getJSONObject(JSON_PROP_DOCDTLS).optJSONArray(JSON_PROP_DOCINFO)!=null)
				{
					String docType = paxDetails.optJSONObject(JSON_PROP_DOCDTLS).optJSONArray(JSON_PROP_DOCINFO).optJSONObject(j).optString(JSON_PROP_DOCTYPE);
					passportNumber.setTextContent(docType.equals(JSON_PROP_DOCTYPE_PASSPORT)? paxDetails.optJSONObject(JSON_PROP_DOCDTLS).optJSONArray(JSON_PROP_DOCINFO).optJSONObject(j).optString(JSON_PROP_DOCNBR): "abcdefg");
				}
				
			}
			//tag paxQuantity is not important
			Element email = ownerDoc.createElementNS(NS_OTA, "ota:Email");
			email.setTextContent(paxDetails.getJSONArray(MDM_PROP_CONTACTDETAILS).getJSONObject(0).optString(JSON_PROP_EMAIL));
			additionalInfo.appendChild(dateOfBirth);
			additionalInfo.appendChild(passportNumber);
			additionalInfo.appendChild(email);
			//=-=-=-=-=-==
//			System.out.println(XMLTransformer.toString(additionalInfo));
			passenger.appendChild(title);
			passenger.appendChild(name);
			passenger.appendChild(age);
			passenger.appendChild(gender);
			passenger.appendChild(type);
			passenger.appendChild(countryOfRes);
			passenger.appendChild(additionalInfo);
			passengers.appendChild(passenger);
			bookingPassProduct.appendChild(passengers);
//			System.out.println(XMLTransformer.toString(bookingPassProduct));
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
	
	public static void insertCurrencyCodeAndAmt(Element toPickFrom, JSONObject toInsertInto)
	{
		toInsertInto.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(toPickFrom, "./ota:CurrencyCode"));
		toInsertInto.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(toPickFrom, "./ota:Amount"));
	}
	

}
