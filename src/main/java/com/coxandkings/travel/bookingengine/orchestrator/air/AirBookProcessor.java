package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.Map.Entry;

import com.coxandkings.travel.bookingengine.config.MDMConfig;
import com.mongodb.client.MongoCollection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.Credential;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.BookingPriority;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.air.enums.PhoneLocationType;
import com.coxandkings.travel.bookingengine.orchestrator.air.enums.TripIndicator;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;

import redis.clients.jedis.Jedis;

public class AirBookProcessor implements AirConstants {

	private static final Logger logger = LogManager.getLogger(AirBookProcessor.class);

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException, ValidationException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		KafkaBookProducer bookProducer = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		Map<String,Integer> pricedItinKeyIndexMap=null;

		try {
			TrackingContext.setTrackingContext(reqJson);
			opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			validateRequestParameters(reqJson);
			TripIndicator tripInd = AirSearchProcessor.deduceTripIndicator(reqHdrJson, reqBodyJson);
			reqBodyJson.put(JSON_PROP_TRIPIND, tripInd.toString());

			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			pricedItinKeyIndexMap=new HashMap<String,Integer>();
			
			enrichRequestBodyJson(reqHdrJson, reqBodyJson,usrCtx,pricedItinKeyIndexMap);
			reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson,reqJson);
			bookProducer = new KafkaBookProducer();
			try {
				sendPreBookingKafkaMessage(bookProducer, reqJson, usrCtx,pricedItinKeyIndexMap);
			}
			catch(Exception x) {
				logger.error("Exception during kafka processing" +x);
			}
		} catch (ValidationException x) {
			throw x;
		}catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}

		try {
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(),  opConfig.getHttpHeaders(), "POST", opConfig.getServiceTimeoutMillis(), reqElem);
			if(reqBodyJson.optBoolean(JSON_PROP_ISTIMELIMIT)) {
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.TIME_LIMIT_BOOKING,
						"", reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
			}else {
				//Commented For Now as Operations is creating ToDo Task when kafka message is received.
				/*OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.BOOKING,
						"", reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");*/
			}
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			

			JSONObject resBodyJson = new JSONObject();
			JSONArray suppBooksJsonArr = new JSONArray();

			Element[] wrapprElems = AirPriceProcessor.sortWrapperElementsBySequence(
					XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper"));
			for (Element wrapprElem : wrapprElems) {
				JSONObject suppBookJson = new JSONObject();
				suppBookJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(wrapprElem, "./air:SupplierID"));
				String bookRefID=XMLUtils.getValueAtXPath(wrapprElem,
						"./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@Type='14']/@ID");
				if(Utils.isStringNullOrEmpty(bookRefID)) {
					putErrorMessage(suppBookJson);
				}
				else {
				suppBookJson.put(JSON_PROP_BOOKREFID, XMLUtils.getValueAtXPath(wrapprElem,
						"./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@Type='14']/@ID"));
				suppBookJson.put(JSON_PROP_AIRLINEPNR, XMLUtils.getValueAtXPath(wrapprElem,
						"./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@Type='14']/@ID"));
				suppBookJson.put(JSON_PROP_GDSPNR, XMLUtils.getValueAtXPath(wrapprElem,
						"./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@Type='24']/@ID"));
				suppBookJson.put(JSON_PROP_TICKETPNR, XMLUtils.getValueAtXPath(wrapprElem,
						"./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@Type='30']/@ID"));
				if(Utils.isStringNotNullAndNotEmpty(suppBookJson.getString(JSON_PROP_BOOKREFID)))
				suppBookJson.put(JSON_PROP_STATUS, "Confirmed");
				
				}
				// TODO: Ticket date from GDS for time limit booking
				suppBooksJsonArr.put(suppBookJson);
			}
			resBodyJson.put(JSON_PROP_SUPPBOOKREFS, suppBooksJsonArr);

			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			
			try {
				sendPostBookingKafkaMessage(bookProducer, reqJson, resJson,usrCtx);
			}
			catch(Exception x) {
				logger.error("Exception during kafka processing");
			}
			

			return resJson.toString();
		} catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}

	private static void putErrorMessage(JSONObject suppBookJson) {
		//suppBookJson.put("errorCode", "TRLERR500");
		//suppBookJson.put("errorMessage", "Internal Processing Error");
		suppBookJson.put(JSON_PROP_STATUS, "Failed");
		
	}

	private static void validateRequestParameters(JSONObject reqJson) throws ValidationException {
		AirRequestValidator.validateTripType(reqJson);
		AirRequestValidator.validatePassengerCounts(reqJson);
		AirRequestValidator.validatePaxDetails(reqJson);
		AirRequestValidator.validatePaymentInfo(reqJson);
		AirRequestValidator.validatebookID(reqJson);

	}
	
	static String getFareInfoKeyForPricedItinerary(JSONObject pricedItinJson) {
		StringBuilder strBldr = new StringBuilder(pricedItinJson.optString(JSON_PROP_SUPPREF));
		
		JSONArray fareInfoJsonArr=pricedItinJson.getJSONArray(JSON_PROP_FAREINFO);
		String previousKey="";
		String presentKey="";
		for(int i=0;i<fareInfoJsonArr.length();i++) {
			strBldr.append('[');
			JSONObject fareInfoJson=fareInfoJsonArr.getJSONObject(i);
			JSONArray flightSegJsonArr=fareInfoJson.getJSONObject("fareRuleInfo").getJSONArray(JSON_PROP_FLIGHTSEG);
			if (flightSegJsonArr == null) {
				break;
			}
			
			for(int j=0;j<flightSegJsonArr.length();j++) {
				JSONObject flSegJson = flightSegJsonArr.getJSONObject(j);
				JSONObject opAirlineJson = flSegJson.getJSONObject(JSON_PROP_OPERAIRLINE);
				presentKey=opAirlineJson.getString(JSON_PROP_AIRLINECODE).concat(opAirlineJson.getString(JSON_PROP_FLIGHTNBR));
				if(Utils.isStringNotNullAndNotEmpty(previousKey)) {
					if(previousKey.equalsIgnoreCase(opAirlineJson.getString(JSON_PROP_AIRLINECODE).concat(opAirlineJson.getString(JSON_PROP_FLIGHTNBR))))
					{
						continue;
					}
				}
				strBldr.append(opAirlineJson.getString(JSON_PROP_AIRLINECODE).concat(opAirlineJson.getString(JSON_PROP_FLIGHTNBR)).concat("|"));
			}
			if(Utils.isStringNotNullAndNotEmpty(previousKey)) {
				if(previousKey.equalsIgnoreCase(presentKey))
				{
					strBldr.deleteCharAt(strBldr.length()-1);
					continue;
				}
				else {
					previousKey=presentKey;
				}
			}
			else {
				previousKey=presentKey;
			}
			
			strBldr.setLength(strBldr.length() - 1);
			strBldr.append(']');
		}
		
		return strBldr.toString();
	}

	public static void createTPA_Extensions(Document ownerDoc, Element tPA_Extensions, JSONObject reqBodyJson, UserContext usrCtx) throws RequestProcessingException {
		Element tripType = XMLUtils.getFirstElementAtXPath(tPA_Extensions, "./air:TripType");
		tripType.setTextContent(reqBodyJson.getString(JSON_PROP_TRIPTYPE));

		Element customerInfo = ownerDoc.createElementNS(NS_AIR, "air:CustomerInfo");
		tPA_Extensions.appendChild(customerInfo);
			
		
		JSONObject paxInfo = reqBodyJson.getJSONArray(JSON_PROP_PAXDETAILS).getJSONObject(0);

		customerInfo.setAttribute("BirthDate", paxInfo.getString(JSON_PROP_DATEOFBIRTH));

		customerInfo.setAttribute("PassengerTypeCode", paxInfo.getString(JSON_PROP_PAXTYPE));

		Element personName = ownerDoc.createElementNS(NS_OTA, "ota:PersonName");
		customerInfo.appendChild(personName);

		Element namePrefix = ownerDoc.createElementNS(NS_OTA, "ota:NamePrefix");
		namePrefix.setTextContent(paxInfo.getString(JSON_PROP_TITLE));
		personName.appendChild(namePrefix);

		Element givenName = ownerDoc.createElementNS(NS_OTA, "ota:GivenName");
		givenName.setTextContent(paxInfo.getString(JSON_PROP_FIRSTNAME));
		personName.appendChild(givenName);

		Element surname = ownerDoc.createElementNS(NS_OTA, "ota:Surname");
		surname.setTextContent(paxInfo.getString(JSON_PROP_SURNAME));
		personName.appendChild(surname);

		Element telephone = ownerDoc.createElementNS(NS_OTA, "ota:Telephone");
		customerInfo.appendChild(telephone);
		
		JSONObject contactInfo=null;
		if(paxInfo.has(JSON_PROP_CONTACTDTLS) && paxInfo.getJSONArray(JSON_PROP_CONTACTDTLS).length()>0) {
		    contactInfo = paxInfo.getJSONArray(JSON_PROP_CONTACTDTLS).getJSONObject(0).getJSONObject(JSON_PROP_CONTACTINFO);
			createTelephoneEmailDetails(ownerDoc, customerInfo, contactInfo);
			
		}
		/*JSONObject contactInfo = paxInfo.getJSONArray(JSON_PROP_CONTACTDTLS).getJSONObject(0)
				.getJSONObject(JSON_PROP_CONTACTINFO);
		telephone.setAttribute("CountryAccessCode", contactInfo.getString(JSON_PROP_COUNTRYCODE));
		telephone.setAttribute("PhoneNumber", contactInfo.getString(JSON_PROP_MOBILENBR));*/

		/*Element email = ownerDoc.createElementNS(NS_OTA, "ota:Email");
		email.setTextContent(contactInfo.getString(JSON_PROP_EMAIL));
		customerInfo.appendChild(email);*/

		Element address = ownerDoc.createElementNS(NS_OTA, "ota:Address");
		customerInfo.appendChild(address);
	
		if(paxInfo.has(JSON_PROP_ADDRDTLS) && paxInfo.getJSONObject(JSON_PROP_ADDRDTLS).length()>0) {
			 address = ownerDoc.createElementNS(NS_OTA, "ota:Address");
			customerInfo.appendChild(address);
			createAddress(ownerDoc, paxInfo, address);
			
		}
		else {
			 address = ownerDoc.createElementNS(NS_OTA, "ota:Address");
			customerInfo.appendChild(address);
			org.bson.Document orgEntityDoc =MDMUtils.getOrgHierarchyDocumentById(MDM_VAL_TYPECOMPANY, usrCtx.getOrganizationHierarchy().getCompanyId());
			JSONObject orgEntityJson=new JSONObject(new JSONTokener(orgEntityDoc.toJson()));
			createCompanyAddress(ownerDoc, address, orgEntityJson);
		}

		

	
		if(paxInfo.has(JSON_PROP_DOCDTLS) && paxInfo.getJSONObject(JSON_PROP_DOCDTLS).length()>0) {
			JSONArray documentInfo = paxInfo.getJSONObject(JSON_PROP_DOCDTLS).getJSONArray(JSON_PROP_DOCINFO);
			Element document = ownerDoc.createElementNS(NS_OTA, "ota:Document");
			customerInfo.appendChild(document);
			document.setAttribute("DocIssueAuthority", documentInfo.getJSONObject(0).getString(JSON_PROP_ISSUEAUTH));
			document.setAttribute("DocIssueLocation", documentInfo.getJSONObject(0).getString(JSON_PROP_ISSUELOC));
			document.setAttribute("DocID", documentInfo.getJSONObject(0).getString(JSON_PROP_DOCNBR));
			document.setAttribute("DocType", documentInfo.getJSONObject(0).getString(JSON_PROP_DOCTYPE));
			//if nationality is not provided set default as IN(Ratan Kumar)
			String nationality = documentInfo.getJSONObject(0).optString(JSON_PROP_NATIONALITY,"IN");
			if(Utils.isStringNullOrEmpty(nationality))
				nationality="IN";
			
			document.setAttribute("DocHolderNationality", nationality);
			document.setAttribute("Gender", paxInfo.getString(JSON_PROP_GENDER));
			document.setAttribute("BirthDate", paxInfo.getString(JSON_PROP_DATEOFBIRTH));
			document.setAttribute("EffectiveDate", documentInfo.getJSONObject(0).getString(JSON_PROP_EFFDATE));
			document.setAttribute("ExpireDate", documentInfo.getJSONObject(0).getString(JSON_PROP_EXPDATE));

			Element docHolderName = ownerDoc.createElementNS(NS_OTA, "ota:DocHolderName");
			docHolderName.setTextContent(
					String.format("%s %s", paxInfo.getString(JSON_PROP_FIRSTNAME), paxInfo.getString(JSON_PROP_SURNAME)));
			document.appendChild(docHolderName);
			
		}
		

		

	}

	private static void createAddress(Document ownerDoc, JSONObject paxInfo, Element address) {
		JSONObject addressDetails = paxInfo.getJSONObject(JSON_PROP_ADDRDTLS);

		Element addressLine = ownerDoc.createElementNS(NS_OTA, "ota:AddressLine");
		addressLine.setTextContent(
				addressDetails.getString(JSON_PROP_ADDRLINE1) + " " + addressDetails.getString(JSON_PROP_ADDRLINE2));
		address.appendChild(addressLine);

		Element cityName = ownerDoc.createElementNS(NS_OTA, "ota:CityName");
		cityName.setTextContent(addressDetails.getString(JSON_PROP_CITY));
		address.appendChild(cityName);

		Element postalCode = ownerDoc.createElementNS(NS_OTA, "ota:PostalCode");
		postalCode.setTextContent(addressDetails.getString(JSON_PROP_ZIP));
		address.appendChild(postalCode);

		
		String locCode=addressDetails.optString(JSON_PROP_STATE);
		if(Utils.isStringNotNullAndNotEmpty(locCode)) {
			if( locCode.length()>2) {
				JSONObject stateAnciJson=MDMUtils.getState(locCode);
				locCode=stateAnciJson.getJSONObject(MDM_PROP_DATA).getString(MDM_PROP_STATECODE);
				if(locCode.length()>2) {
					locCode=locCode.substring(locCode.indexOf("-")+1);
				}
			}
		}
		
		Element stateProv = ownerDoc.createElementNS(NS_OTA, "ota:StateProv");
		stateProv.setAttribute("StateCode", locCode);
		address.appendChild(stateProv);

		//check countryCode here
		 locCode=addressDetails.optString(JSON_PROP_COUNTRY);
		if(Utils.isStringNotNullAndNotEmpty(locCode)) {
			if( locCode.length()>2) {
				JSONObject countryAnciJson=MDMUtils.getState(locCode);
				locCode=countryAnciJson.getJSONObject(MDM_PROP_DATA).getString(MDM_PROP_CODE);
			}
		}
			
		Element countryName = ownerDoc.createElementNS(NS_OTA, "ota:CountryName");
		countryName.setAttribute("Code", locCode);
		address.appendChild(countryName);
	}

	public static void createFulfillment(Document ownerDoc, Element oTA_AirBookRQ, Element tPA_Extensions,
			JSONObject paymentInfoNode, JSONObject bookRefJson, ProductSupplier prodSupplier) {
		Element fulfillment = ownerDoc.createElementNS(NS_OTA, "ota:Fulfillment");
		oTA_AirBookRQ.appendChild(fulfillment);

		oTA_AirBookRQ.insertBefore(fulfillment, tPA_Extensions);

		Element paymentDetails = ownerDoc.createElementNS(NS_OTA, "ota:PaymentDetails");
		fulfillment.appendChild(paymentDetails);

		Element paymentDetail = prodSupplier.getPaymentDetailsElement(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT,
				ownerDoc);
		paymentDetails.appendChild(paymentDetail);

		// ***** IMPORTANT - BEGIN ******
		createPaymentDetail(ownerDoc, paymentDetail, paymentInfoNode, bookRefJson);
		// ***** IMPORTANT - END ******
	}

	private static void createPaymentDetail(Document ownerDoc, Element paymentDetail, JSONObject paymentInfoNode,
			JSONObject bookRefJson) {
		Element paymentAmount = ownerDoc.createElementNS(NS_OTA, "ota:PaymentAmount");
		paymentDetail.appendChild(paymentAmount);

		JSONObject suppBookFareJson = bookRefJson.getJSONObject(JSON_PROP_SUPPBOOKFARE);
		paymentAmount.setAttribute(XML_ATTR_CURRENCYCODE, suppBookFareJson.getString(JSON_PROP_CCYCODE));
		paymentAmount.setAttribute(XML_ATTR_AMOUNT, suppBookFareJson.getBigDecimal(JSON_PROP_AMOUNT).toString());
	}

	private static void createTraveler(Document ownerDoc, JSONObject paxInfo, Element travelerInfo, int i, UserContext usrCtx, JSONObject leadPaxJsonObj, int adtPaxListIdx, List<Integer> adtPaxRphList) {
		
		Element airTraveler = ownerDoc.createElementNS(NS_OTA, "ota:AirTraveler");
		travelerInfo.appendChild(airTraveler);

		String dataStr;

		dataStr = paxInfo.get(JSON_PROP_GENDER).toString();
		dataStr = dataStr.replace("\"", "");
		airTraveler.setAttribute("Gender", dataStr);

		String dob = paxInfo.get(JSON_PROP_DATEOFBIRTH).toString();
		if (dob != null && dob.toString().trim().length() > 0) {
			dataStr = dob.toString();
			dataStr = dataStr.replace("\"", "");
			airTraveler.setAttribute("BirthDate", dataStr);
		}
		
		LocalDate today = LocalDate.now();
		LocalDate birthday = LocalDate.parse(dob);
		
		int age=Period.between(birthday, today).getYears();
		if(age==0) {
			age=1;
		}
		airTraveler.setAttribute("Age",Integer.toString(age));

		dataStr = paxInfo.get(JSON_PROP_PAXTYPE).toString();
		dataStr = dataStr.replace("\"", "");
		airTraveler.setAttribute("PassengerTypeCode", dataStr);
		

		if((paxInfo.getString(JSON_PROP_PAXTYPE)).equalsIgnoreCase(JSON_PROP_INF)) {
			Element profileRefElem = ownerDoc.createElementNS(NS_OTA, "ota:ProfileRef");
			airTraveler.appendChild(profileRefElem);
			Element uniqueIDElem = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
			uniqueIDElem.setAttribute("Type", "36");
			uniqueIDElem.setAttribute("ID", adtPaxRphList.get(adtPaxListIdx).toString());
			profileRefElem.appendChild(uniqueIDElem);
		}

		Element PersonName = ownerDoc.createElementNS(NS_OTA, "ota:PersonName");
		airTraveler.appendChild(PersonName);

		Element NamePrefix = ownerDoc.createElementNS(NS_OTA, "ota:NamePrefix");
		PersonName.appendChild(NamePrefix);

		NamePrefix.setTextContent(paxInfo.get(JSON_PROP_TITLE).toString().replace("\"", ""));

		Element GivenName = ownerDoc.createElementNS(NS_OTA, "ota:GivenName");
		PersonName.appendChild(GivenName);
		GivenName.setTextContent(paxInfo.getString(JSON_PROP_FIRSTNAME).replace("\"", ""));

		Element Surname = ownerDoc.createElementNS(NS_OTA, "ota:Surname");
		PersonName.appendChild(Surname);
		Surname.setTextContent(paxInfo.getString(JSON_PROP_SURNAME).replace("\"", ""));

		if(paxInfo.has(JSON_PROP_CONTACTDTLS) && paxInfo.getJSONArray(JSON_PROP_CONTACTDTLS).length()>0) {
			int contactLength = paxInfo.getJSONArray(JSON_PROP_CONTACTDTLS).length();
			for (int l = 0; l < contactLength; l++) {
				JSONObject contactInfo = paxInfo.getJSONArray(JSON_PROP_CONTACTDTLS).getJSONObject(l)
						.getJSONObject(JSON_PROP_CONTACTINFO);
				createTelephoneEmailDetails(ownerDoc, airTraveler, contactInfo);
			}
		}
		else if(leadPaxJsonObj.has(JSON_PROP_CONTACTDTLS) && leadPaxJsonObj.getJSONArray(JSON_PROP_CONTACTDTLS).length()>0) {
			int contactLength = leadPaxJsonObj.getJSONArray(JSON_PROP_CONTACTDTLS).length();
			for (int l = 0; l < contactLength; l++) {
				JSONObject contactInfo = leadPaxJsonObj.getJSONArray(JSON_PROP_CONTACTDTLS).getJSONObject(l).getJSONObject(JSON_PROP_CONTACTINFO);
				AirBookProcessor.createTelephoneEmailDetails(ownerDoc, airTraveler, contactInfo);
			}
			
		}

		// Address

		if(paxInfo.has(JSON_PROP_ADDRDTLS) && paxInfo.getJSONObject(JSON_PROP_ADDRDTLS).length()>0) {
			Element Address = ownerDoc.createElementNS(NS_OTA, "ota:Address");
			airTraveler.appendChild(Address);
			createAddress(ownerDoc, airTraveler, Address, paxInfo);
		}
		else if(!leadPaxJsonObj.has(JSON_PROP_ADDRDTLS) || leadPaxJsonObj.getJSONObject(JSON_PROP_ADDRDTLS).length()==0){
			Element Address = ownerDoc.createElementNS(NS_OTA, "ota:Address");
			airTraveler.appendChild(Address);
			//JSONObject orgEntityJson =MDMUtils.getOrgHierarchyDocumentByIdv2(MDM_VAL_TYPECOMPANY, usrCtx.getOrganizationHierarchy().getCompanyId());
			org.bson.Document orgEntityDoc =MDMUtils.getOrgHierarchyDocumentById(MDM_VAL_TYPECOMPANY, usrCtx.getOrganizationHierarchy().getCompanyId());
			JSONObject orgEntityJson =new JSONObject(new JSONTokener(orgEntityDoc.toJson()));
			createCompanyAddress(ownerDoc, Address, orgEntityJson);
		}
		else {
			Element Address = ownerDoc.createElementNS(NS_OTA, "ota:Address");
			airTraveler.appendChild(Address);
			AirBookProcessor.createAddress(ownerDoc, airTraveler, Address, leadPaxJsonObj);
		}
		

		// For minors documentDetails information might not be available
		JSONObject documentDetails = paxInfo.optJSONObject(JSON_PROP_DOCDTLS);
		if (documentDetails != null) {
			JSONArray documentInfoJsonArr = documentDetails.getJSONArray(JSON_PROP_DOCINFO);
			for (int l = 0; l < documentInfoJsonArr.length(); l++) {
				JSONObject documentInfoJson = documentInfoJsonArr.getJSONObject(l);
				if (documentInfoJson != null && documentInfoJson.toString().trim().length() > 0)
					createDocuments(ownerDoc, airTraveler, documentInfoJson, paxInfo);
			}
		}

		Element travelerRefNumber = ownerDoc.createElementNS(NS_OTA, "ota:TravelerRefNumber");
		airTraveler.appendChild(travelerRefNumber);

		travelerRefNumber.setAttribute("RPH", String.valueOf(i));
	}

	public static void createDocuments(Document ownerDoc, Element airTraveler, JSONObject documentInfo,
			JSONObject paxInfo) {

		Element document = ownerDoc.createElementNS(NS_OTA, "ota:Document");
		airTraveler.appendChild(document);

		createDocumentAttr(ownerDoc, document, documentInfo, "DocIssueAuthority", JSON_PROP_ISSUEAUTH);
		createDocumentAttr(ownerDoc, document, documentInfo, "DocIssueLocation", JSON_PROP_ISSUELOC);
		createDocumentAttr(ownerDoc, document, documentInfo, "DocHolderNationality", JSON_PROP_NATIONALITY);
		createDocumentAttr(ownerDoc, document, paxInfo, "Gender", JSON_PROP_GENDER);
		createDocumentAttr(ownerDoc, document, paxInfo, "BirthDate", JSON_PROP_DATEOFBIRTH);
		createDocumentAttr(ownerDoc, document, documentInfo, "EffectiveDate", JSON_PROP_EFFDATE);
		createDocumentAttr(ownerDoc, document, documentInfo, "ExpireDate", JSON_PROP_EXPDATE);
		createDocumentAttr(ownerDoc, document, documentInfo, "DocID", JSON_PROP_DOCNBR);
		createDocumentAttr(ownerDoc, document, documentInfo, "DocType", JSON_PROP_DOCTYPE);

		Element docHolderName = ownerDoc.createElementNS(NS_OTA, "ota:DocHolderName");
		document.appendChild(docHolderName);
		if (paxInfo.get(JSON_PROP_FIRSTNAME) != null
				&& paxInfo.get(JSON_PROP_FIRSTNAME).toString().trim().length() > 0) {
			if (paxInfo.get(JSON_PROP_SURNAME) != null
					&& paxInfo.get(JSON_PROP_SURNAME).toString().trim().length() > 0) {
				String firstName = paxInfo.get(JSON_PROP_FIRSTNAME).toString();
				String surname = paxInfo.get(JSON_PROP_SURNAME).toString();
				String name = firstName.toString() + " " + surname.toString();
				docHolderName.setTextContent(name);
			}
		}

	}

	private static void createDocumentAttr(Document ownerDoc, Element document, JSONObject documentInfo, String attrStr,
			String attrValue) {
		String dataStr = documentInfo.get(attrValue).toString();
		if (dataStr != null && dataStr.trim().isEmpty() == false) {
			document.setAttribute(attrStr, dataStr);
		}
	}

	public static void createAddress(Document ownerDoc, Element airTraveler, Element address, JSONObject paxInfo) {

		Element StreetNmbr = ownerDoc.createElementNS(NS_OTA, "ota:StreetNmbr");
		address.appendChild(StreetNmbr);

		JSONObject addressDetails = paxInfo.getJSONObject(JSON_PROP_ADDRDTLS);
		if (addressDetails != null) {
			
			String addrLine1 = addressDetails.get(JSON_PROP_ADDRLINE1).toString();
			String addrLine2 = addressDetails.get(JSON_PROP_ADDRLINE2).toString();
			
			if (addrLine1 != null && addrLine1.toString().trim().length() > 0) {
				StreetNmbr.setTextContent(addrLine1.toString().replace("\"", ""));
			}

			Element BldgRoom = ownerDoc.createElementNS(NS_OTA, "ota:BldgRoom");
			address.appendChild(BldgRoom);

			
			if (addrLine1 != null && addrLine1.toString().trim().length() > 0) {
				BldgRoom.setTextContent(addrLine1.toString().replace("\"", ""));
			}

			BldgRoom.setAttribute("BldgNameIndicator", "false");

			Element AddressLine = ownerDoc.createElementNS(NS_OTA, "ota:AddressLine");
			address.appendChild(AddressLine);

			String addrLine = addressDetails.get(JSON_PROP_ADDRLINE2).toString();
			if (addrLine != null && addrLine.toString().trim().length() > 0) {
				AddressLine.setTextContent(addrLine.toString()+" "+addrLine2.toString().replace("\"", ""));
			}

			Element CityName = ownerDoc.createElementNS(NS_OTA, "ota:CityName");
			address.appendChild(CityName);

			String city = addressDetails.get(JSON_PROP_CITY).toString();
			if (city != null && city.toString().trim().length() > 0) {
				CityName.setTextContent(city.toString().replace("\"", ""));
			}

			Element PostalCode = ownerDoc.createElementNS(NS_OTA, "ota:PostalCode");
			address.appendChild(PostalCode);

			String zip = addressDetails.get(JSON_PROP_ZIP).toString();
			if (zip != null && zip.toString().trim().length() > 0) {
				PostalCode.setTextContent(zip.toString().replace("\"", ""));
			}

			Element County = ownerDoc.createElementNS(NS_OTA, "ota:County");
			address.appendChild(County);
			String country = addressDetails.get(JSON_PROP_COUNTRY).toString();
			if (country != null && country.toString().trim().length() > 0) {
				County.setTextContent(country.toString().replace("\"", ""));
			}

			Element StateProv = ownerDoc.createElementNS(NS_OTA, "ota:StateProv");
			address.appendChild(StateProv);
			String state = addressDetails.optString(JSON_PROP_STATE).replace("\"", "");
			if (state != null && state.toString().trim().length() > 0) {
				StateProv.setTextContent(state.toString().replace("\"", ""));
			}

			Element CountryName = ownerDoc.createElementNS(NS_OTA, "ota:CountryName");
			address.appendChild(CountryName);
			CountryName.setAttribute("Code", addressDetails.getString(JSON_PROP_COUNTRY));
		}

	}

	private static void createSSRElem(Document ownerDoc, Element specReqDetails, int travellerRPH, JSONObject traveller,
			Map<String, String> flightMap) {
		// Element ssrElem=ownerDoc.createElementNS(NS_AIR, "air:SSR");
		
		Element specialServiceRequestsElem=XMLUtils.getFirstElementAtXPath(specReqDetails, "./ota:SpecialServiceRequests");
		
		
		if(specialServiceRequestsElem==null) {
			specialServiceRequestsElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequests");
		}
		
		
		//Element specialServiceRequestsElem = ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequests");

		JSONArray specialServiceRequests = traveller.getJSONObject("specialRequests")
				.getJSONArray("specialRequestInfo");

		for (int s = 0; s < specialServiceRequests.length(); s++) {

			JSONObject specialServiceRequestJson = specialServiceRequests.getJSONObject(s);

			if (!flightMap
					.containsKey(specialServiceRequestJson.getString((JSON_PROP_FLIGHTNBR)).replaceAll("\\s", ""))) {
				continue;
			}
			
			String flightMapKey=flightMap.get(specialServiceRequestJson.getString((JSON_PROP_FLIGHTNBR)).replaceAll("\\s", ""));
			Element specialServiceRequestElem = ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequest");

			specialServiceRequestElem.setAttribute("TravelerRefNumberRPHList", Integer.toString(travellerRPH));

			if((specialServiceRequestJson.optString(JSON_PROP_FLIGHREFNBR).equals(""))) {
				specialServiceRequestElem.setAttribute("FlightRefNumberRPHList",
						(specialServiceRequestJson.getString((JSON_PROP_FLIGHTNBR)).replaceAll("\\s", "")));
			}
			else {
				specialServiceRequestElem.setAttribute("FlightRefNumberRPHList",
						(specialServiceRequestJson.getString((JSON_PROP_FLIGHREFNBR)).replaceAll("\\s", "")));
			}

			specialServiceRequestElem.setAttribute("SSRCode",
					specialServiceRequestJson.optString(JSON_PROP_SSRCODE, ""));

			specialServiceRequestElem.setAttribute("ServiceQuantity",
					specialServiceRequestJson.optString(JSON_PROP_SVCQTY, ""));

			specialServiceRequestElem.setAttribute("Type", specialServiceRequestJson.optString(JSON_PROP_TYPE, ""));

			specialServiceRequestElem.setAttribute("Status", specialServiceRequestJson.optString(JSON_PROP_STATUS, ""));

			if (specialServiceRequestJson.has(JSON_PROP_NUMBER)
					&& !specialServiceRequestJson.get(JSON_PROP_NUMBER).equals("")) {
				specialServiceRequestElem.setAttribute("Number",
						specialServiceRequestJson.optString(JSON_PROP_NUMBER, "0"));
			}
			Element airlineElem = ownerDoc.createElementNS(NS_OTA, "ota:Airline");

			airlineElem.setAttribute("CompanyShortName",
					specialServiceRequestJson.optString((JSON_PROP_COMPANYSHORTNAME), ""));

			airlineElem.setAttribute("Code", specialServiceRequestJson.optString((JSON_PROP_AIRLINECODE), ""));

			specialServiceRequestElem.appendChild(airlineElem);

			Element textElem = ownerDoc.createElementNS(NS_OTA, "ota:Text");

			textElem.setTextContent(specialServiceRequestJson.optString((JSON_PROP_DESC), ""));

			specialServiceRequestElem.appendChild(textElem);

			Element flightLegElem = ownerDoc.createElementNS(NS_OTA, "ota:FlightLeg");

			flightLegElem.setAttribute("FlightNumber", specialServiceRequestJson.getString((JSON_PROP_FLIGHTNBR)));

		/*	flightLegElem.setAttribute("ResBookDesigCode",
					flightMap.get((specialServiceRequestJson.getString((JSON_PROP_FLIGHTNBR))).replaceAll("\\s", ""))
							.toString());*/
			
			
			flightLegElem.setAttribute("ResBookDesigCode",flightMapKey.substring(0, flightMapKey.indexOf("|")));

			specialServiceRequestElem.appendChild(flightLegElem);

			Element categoryCodeElem = ownerDoc.createElementNS(NS_OTA, "ota:CategoryCode");

			categoryCodeElem.setTextContent(specialServiceRequestJson.optString((JSON_PROP_CATCODE), ""));

			specialServiceRequestElem.appendChild(categoryCodeElem);

			Element travelerRefElem = ownerDoc.createElementNS(NS_OTA, "ota:TravelerRef");

			travelerRefElem.setTextContent(traveller.getString(JSON_PROP_PAXTYPE));

			specialServiceRequestElem.appendChild(travelerRefElem);

			if (specialServiceRequestJson.has(JSON_PROP_AMOUNT)) {
				Element servicePriceElem = ownerDoc.createElementNS(NS_OTA, "ota:ServicePrice");

				servicePriceElem.setAttribute("Total", specialServiceRequestJson.optString(JSON_PROP_AMOUNT));
				servicePriceElem.setAttribute("CurrencyCode", specialServiceRequestJson.optString(JSON_PROP_CCYCODE));

				if (specialServiceRequestJson.has(JSON_PROP_SVCPRC)) {
					Element basePriceElem = ownerDoc.createElementNS(NS_OTA, "ota:BasePrice");

					basePriceElem.setAttribute("Amount",
							specialServiceRequestJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_BASEPRICE));

					servicePriceElem.appendChild(basePriceElem);
				}

				if (specialServiceRequestJson.has(JSON_PROP_TAXES)) {
					Element taxesElem = ownerDoc.createElementNS(NS_OTA, "ota:Taxes");

					taxesElem.setAttribute("Amount",
							specialServiceRequestJson.getJSONObject(JSON_PROP_TAXES).optString(JSON_PROP_AMOUNT));

					servicePriceElem.appendChild(taxesElem);
				}

				specialServiceRequestElem.appendChild(servicePriceElem);
			}

			specialServiceRequestsElem.appendChild(specialServiceRequestElem);

			specReqDetails.appendChild(specialServiceRequestsElem);

		}

	}

	public static void createTelephoneEmailDetails(Document ownerDoc, Element airTraveler, JSONObject contactInfo) {

		Element Telephone = ownerDoc.createElementNS(NS_OTA, "ota:Telephone");
		airTraveler.appendChild(Telephone);

		String dataStr;

		String countryCode = contactInfo.get(JSON_PROP_COUNTRYCODE).toString();
		if (countryCode != null && countryCode.toString().trim().length() > 0) {
			dataStr = countryCode.toString();
			dataStr = dataStr.replace("\"", "");
			Telephone.setAttribute("CountryAccessCode", dataStr);
		}

		String mobileNo = contactInfo.get(JSON_PROP_MOBILENBR).toString();
		if (countryCode != null && countryCode.toString().trim().length() > 0) {
			dataStr = mobileNo.toString();
			dataStr = dataStr.replace("\"", "");
			Telephone.setAttribute("PhoneNumber", dataStr);
		}

		PhoneLocationType phoneLocationType;
		String contactType = contactInfo.optString(JSON_PROP_CONTACTTYPE);
		switch (contactType.toUpperCase()) {
		case "WORK":
			phoneLocationType = PhoneLocationType.WORK;
			break;
		case "HOME":
			phoneLocationType = PhoneLocationType.HOME;
			break;
		case "MOBILE":
			phoneLocationType = PhoneLocationType.MOBILE;
			break;
		case "OTHER":
			phoneLocationType = PhoneLocationType.OTHER;
			break;
		default:
			phoneLocationType = PhoneLocationType.OTHER;
		}
		if (countryCode != null && countryCode.toString().trim().length() > 0) {
			dataStr = mobileNo.toString();
			dataStr = dataStr.replace("\"", "");
			Telephone.setAttribute("PhoneLocationType", phoneLocationType.toString());
		}

		Element Email = ownerDoc.createElementNS(NS_OTA, "ota:Email");
		airTraveler.appendChild(Email);
		String email = contactInfo.get(JSON_PROP_EMAIL).toString();
		if (email != null && email.toString().trim().length() > 0)
			Email.setTextContent(email.toString().replace("\"", ""));

	}

	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson, JSONObject reqJson) throws Exception {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson, JSONObject reqJson) throws Exception {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();

		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_AirBookRQWrapper");
		reqBodyElem.removeChild(wrapperElem);

		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);

		Map<String, String> ptcBrkDwnMap = null;
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			String ptcRedisKey = (sessionID.concat("|").concat(PRODUCT_AIR)).concat(JSON_PROP_PTCXML);
			ptcBrkDwnMap = redisConn.hgetAll(ptcRedisKey);
			if (ptcBrkDwnMap == null || ptcBrkDwnMap.isEmpty()) {
				throw new Exception(String.format("Ptc Break down not found for %s", ptcRedisKey));
			}
		}

		String prevSuppID = "";
		JSONArray pricedItinsJSONArr = reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
		String bookId = reqBodyJson.optString(JSON_PROP_BOOKID);
		for (int y = 0; y < pricedItinsJSONArr.length(); y++) {
			JSONObject pricedItinJson = pricedItinsJSONArr.getJSONObject(y);
			Map<String, String> flightMap = new HashMap<String, String>();
			Map<String, JSONObject> flightSegJsonMap = new HashMap<String, JSONObject>();

			String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
			Element suppWrapperElem = null;
			Element otaReqElem = null;
			Element travelerInfoElem = null;
			Element odosElem = null;
			Element specialRqDetElem = null;
			Element priceInfoElem = null;
			String credsName=null;
			Credential pccCredential =null;
			
			JSONArray paxDetailsJsonArr = reqBodyJson.getJSONArray(JSON_PROP_PAXDETAILS);
			AirRequestValidator.validatePaxInfo(reqHdrJson, paxDetailsJsonArr);
			List<Integer> adtPaxRphList=new ArrayList<Integer>();
			for (int i = 0; i < paxDetailsJsonArr.length(); i++)
			{
				JSONObject paxInfo=paxDetailsJsonArr.getJSONObject(i);
				
				if((paxInfo.getString(JSON_PROP_PAXTYPE)).equalsIgnoreCase(JSON_PROP_ADT)) {
					adtPaxRphList.add(i);
				}
			}
			
			if (suppID.equals(prevSuppID)) {
				suppWrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem,
						String.format("./air:OTA_AirBookRQWrapper[air:SupplierID = '%s']", suppID));
				travelerInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem,
						"./ota:OTA_AirBookRQ/ota:TravelerInfo");
				otaReqElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirBookRQ");
				priceInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirBookRQ/ota:PriceInfo");
				odosElem = XMLUtils.getFirstElementAtXPath(otaReqElem,
						"./ota:AirItinerary/ota:OriginDestinationOptions");
				if (odosElem == null) {
					logger.warn(String.format("XML element for ota:OriginDestinationOptions not found for supplier %s",
							suppID));
				}
			} else {
				suppWrapperElem = (Element) wrapperElem.cloneNode(true);
				reqBodyElem.appendChild(suppWrapperElem);

				ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT,
						PROD_CATEG_SUBTYPE_FLIGHT, suppID);
				
				
				if (prodSupplier == null) {
					throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
				}
				credsName = prodSupplier.getCredentialsName();
				 pccCredential = prodSupplier.getCredentialForKey(MDM_VAL_PCC);
				 if(pccCredential==null)
					 pccCredential = prodSupplier.getCredentialForKey("Pseudo City Code");
				
				 
				//List<ProductSupplier> pccHAPProdSupps = PccHAPCredentials.getProductSuppliers(usrCtx, reqJson);
				
				Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
						"./air:RequestHeader/com:SupplierCredentialsList");
				Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem,
						String.format("./com:SupplierCredentials[com:SupplierID = '%s']", suppID));
				if (suppCredsElem == null) {
					suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
				}

				Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:SupplierID");
				suppIDElem.setTextContent(suppID);

				Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:Sequence");
				sequenceElem.setTextContent(String.valueOf(y));

				travelerInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem,
						"./ota:OTA_AirBookRQ/ota:TravelerInfo");
				otaReqElem = (Element) travelerInfoElem.getParentNode();
				
                //////////////////////************************************************/////////////////////////////
				String orderId = pricedItinJson.optString(JSON_PROP_ORDERID);
				//This case is implemented for Ops
				if(Utils.isStringNotNullAndNotEmpty(orderId)) {
					Map<String,String> pnrValueMap=AirRepriceProcessor.getPNRsFromDb(bookId,reqHdrJson,orderId);
					Map<String,String> typeIdMap=AirRepriceProcessor.getBookRefIdsFromRetrieveresponse(reqHdrJson,pricedItinJson,pnrValueMap);
					if(!typeIdMap.isEmpty()) {
						Element airPriceRqElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirBookRQ");
						Iterator<Entry<String, String>> typeIdItr = typeIdMap.entrySet().iterator();
						while(typeIdItr.hasNext()) {
							Entry<String, String> Itr = typeIdItr.next();
							Element bookRefId = ownerDoc.createElementNS(NS_OTA, "ota:BookingReferenceID"); 
							bookRefId.setAttribute("ID",Itr.getValue());
							bookRefId.setAttribute("Type",Itr.getKey());
							airPriceRqElem.appendChild(bookRefId);
						}
					}						
				}

				// Element bookeRqElem= XMLUtils.getFirstElementAtXPath(suppWrapperElem,
				// "./ota:OTA_AirBookRQ");
				otaReqElem.setAttribute("TransactionIdentifier", pricedItinJson.optString(JSON_PROP_TRANSACTID));

				Element airItinElem = ownerDoc.createElementNS(NS_OTA, "ota:AirItinerary");
				airItinElem.setAttribute("DirectionInd", reqBodyJson.getString(JSON_PROP_TRIPTYPE));
				odosElem = ownerDoc.createElementNS(NS_OTA, "ota:OriginDestinationOptions");
				airItinElem.appendChild(odosElem);
				otaReqElem.insertBefore(airItinElem, travelerInfoElem);
			}
			if (pccCredential != null) {
				pricedItinJson.put(JSON_PROP_PSEUDOCC, pccCredential.getValue());
			}
			else {
				pricedItinJson.put(JSON_PROP_PSEUDOCC,"");
			}
			pricedItinJson.put(JSON_PROP_CREDSNAME,credsName);
			JSONObject airItinJson = pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY);
			JSONArray odoJsonArr = airItinJson.optJSONArray(JSON_PROP_ORIGDESTOPTS);
			if (odoJsonArr == null) {
				throw new ValidationException("TRLERR025");
			}
			for (int i = 0; i < odoJsonArr.length(); i++) {
				JSONObject odoJson = odoJsonArr.getJSONObject(i);
				Element odoElem = ownerDoc.createElementNS(NS_OTA, "ota:OriginDestinationOption");
				JSONArray flSegJsonArr = odoJson.getJSONArray(JSON_PROP_FLIGHTSEG);
				for (int j = 0; j < flSegJsonArr.length(); j++) {
					JSONObject flSegJson = flSegJsonArr.getJSONObject(j);
					Element flSegElem = ownerDoc.createElementNS(NS_OTA, "ota:FlightSegment");

					Element depAirportElem = ownerDoc.createElementNS(NS_OTA, "ota:DepartureAirport");
					depAirportElem.setAttribute("LocationCode", flSegJson.getString(JSON_PROP_ORIGLOC));
					flSegElem.appendChild(depAirportElem);

					Element arrAirportElem = ownerDoc.createElementNS(NS_OTA, "ota:ArrivalAirport");
					arrAirportElem.setAttribute("LocationCode", flSegJson.getString(JSON_PROP_DESTLOC));
					flSegElem.appendChild(arrAirportElem);

					Element opAirlineElem = ownerDoc.createElementNS(NS_OTA, "ota:OperatingAirline");
					JSONObject opAirlineJson = flSegJson.getJSONObject(JSON_PROP_OPERAIRLINE);
					flSegElem.setAttribute("FlightNumber", opAirlineJson.getString(JSON_PROP_FLIGHTNBR));
					/*flightMap.put((opAirlineJson.getString(JSON_PROP_FLIGHTNBR).replaceAll("\\s", "")).toString(),
							flSegJson.getString(JSON_PROP_RESBOOKDESIG));*/
					flightMap.put((opAirlineJson.getString(JSON_PROP_FLIGHTNBR).replaceAll("\\s", "")).toString(),
							flSegJson.getString(JSON_PROP_RESBOOKDESIG).concat("|").concat(flSegJson.getString(JSON_PROP_EXTENDEDRPH)));
					flightSegJsonMap.put(opAirlineJson.getString(JSON_PROP_FLIGHTNBR), flSegJson);
					flSegElem.setAttribute("DepartureDateTime", flSegJson.getString(JSON_PROP_DEPARTDATE));
					flSegElem.setAttribute("ArrivalDateTime", flSegJson.getString(JSON_PROP_ARRIVEDATE));
					flSegElem.setAttribute("Status", flSegJson.getString(JSON_PROP_STATUS));
					String connType = flSegJson.optString(JSON_PROP_CONNTYPE);
					if (connType != null) {
						flSegElem.setAttribute("ConnectionType", connType);
					}
					opAirlineElem.setAttribute("Code", opAirlineJson.getString(JSON_PROP_AIRLINECODE));
					opAirlineElem.setAttribute("FlightNumber", opAirlineJson.getString(JSON_PROP_FLIGHTNBR));
					String companyShortName = opAirlineJson.optString(JSON_PROP_COMPANYSHORTNAME, "");
					if (companyShortName.isEmpty() == false) {
						opAirlineElem.setAttribute("CompanyShortName", companyShortName);
					}
					flSegElem.appendChild(opAirlineElem);

					Element tpaExtsElem = ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
					Element extRPHElem = ownerDoc.createElementNS(NS_AIR, "air:ExtendedRPH");
					extRPHElem.setTextContent(flSegJson.getString(JSON_PROP_EXTENDEDRPH));
					tpaExtsElem.appendChild(extRPHElem);

					Element quoteElem = ownerDoc.createElementNS(NS_AIR, "air:QuoteID");
					quoteElem.setTextContent(flSegJson.getString(JSON_PROP_QUOTEID));
					tpaExtsElem.appendChild(quoteElem);

					flSegElem.appendChild(tpaExtsElem);

					Element mkAirlineElem = ownerDoc.createElementNS(NS_OTA, "ota:MarketingAirline");
					JSONObject mkAirlineJson = flSegJson.getJSONObject(JSON_PROP_MARKAIRLINE);
					mkAirlineElem.setAttribute("Code", mkAirlineJson.getString(JSON_PROP_AIRLINECODE));
					
					
					
					String mkAirlineShortName = mkAirlineJson.optString(JSON_PROP_COMPANYSHORTNAME, "");
					if (mkAirlineShortName.isEmpty() == false) {
						mkAirlineElem.setAttribute("CompanyShortName", mkAirlineShortName);
					}
					flSegElem.appendChild(mkAirlineElem);

					Element bookClsAvailsElem = ownerDoc.createElementNS(NS_OTA, "BookingClassAvails");
					if (flSegJson.optString(JSON_PROP_CABINTYPE) == null) {
						throw new ValidationException("TRLERR035");
					}
					bookClsAvailsElem.setAttribute("CabinType", flSegJson.getString(JSON_PROP_CABINTYPE));
					Element bookClsAvailElem = ownerDoc.createElementNS(NS_OTA, "ota:BookingClassAvail");
					bookClsAvailElem.setAttribute("ResBookDesigCode", flSegJson.getString(JSON_PROP_RESBOOKDESIG));
					bookClsAvailElem.setAttribute("RPH", flSegJson.getString(JSON_PROP_RPH));
					bookClsAvailsElem.appendChild(bookClsAvailElem);
					flSegElem.appendChild(bookClsAvailsElem);

					odoElem.appendChild(flSegElem);
				}

				odosElem.appendChild(odoElem);
			}
			String pricedItinRedisKey = AirRepriceProcessor.getRedisKeyForPricedItinerary(pricedItinJson);
			String ptcBrkDwnKey = (pricedItinRedisKey).concat(JSON_PROP_PTCXML);
			String ptcBrkDwnsXmlStr = ptcBrkDwnMap.get(ptcBrkDwnKey);

			Element ptcBrkDownsElem = (Element) ownerDoc.importNode(XMLTransformer.toXMLElement(ptcBrkDwnsXmlStr),
					true);

			if (!suppID.equals(prevSuppID)) {
				priceInfoElem = ownerDoc.createElementNS(NS_OTA, "ota:PriceInfo");
			}

			priceInfoElem.appendChild(ptcBrkDownsElem);

			otaReqElem.insertBefore(priceInfoElem, travelerInfoElem);

			if (suppID.equals(prevSuppID) == false) {
				/*JSONArray paxDetailsJsonArr = reqBodyJson.getJSONArray(JSON_PROP_PAXDETAILS);
				AirRequestValidator.validatePaxInfo(reqHdrJson, paxDetailsJsonArr);
				List<Integer> adtPaxRphList=new ArrayList<Integer>();
				for (int i = 0; i < paxDetailsJsonArr.length(); i++)
				{
					JSONObject paxInfo=paxDetailsJsonArr.getJSONObject(i);
					
					if((paxInfo.getString(JSON_PROP_PAXTYPE)).equalsIgnoreCase(JSON_PROP_ADT)) {
						adtPaxRphList.add(i);
					}
				}*/
				
				for (int i = 0,adtPaxListIdx=0; i < paxDetailsJsonArr.length(); i++) {
					JSONObject leadPaxJsonObj=null;
					if(i==0) {
					 leadPaxJsonObj=paxDetailsJsonArr.getJSONObject(i);	
					}
					JSONObject traveller = (JSONObject) paxDetailsJsonArr.get(i);
					createTraveler(ownerDoc, traveller, travelerInfoElem, i,usrCtx,leadPaxJsonObj,adtPaxListIdx,adtPaxRphList);
					if((traveller.getString(JSON_PROP_PAXTYPE)).equalsIgnoreCase(JSON_PROP_INF)) {
						adtPaxListIdx++;
					}
				}

				Element tPA_Extensions = XMLUtils.getFirstElementAtXPath(otaReqElem, "./ota:TPA_Extensions");
				// TODO:Create paymentDetails as array

				JSONObject paymentInfoJson = reqBodyJson.getJSONArray(JSON_PROP_PAYINFO).getJSONObject(0);

				JSONObject suppPriceBookInfoJson = pricedItinJson.getJSONObject(JSON_PROP_SUPPINFO);
				JSONObject bookRefJson = suppPriceBookInfoJson.getJSONObject(JSON_PROP_BOOKREFS);

				ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT,
						PROD_CATEG_SUBTYPE_FLIGHT, suppID);
				createFulfillment(ownerDoc, otaReqElem, tPA_Extensions, paymentInfoJson, bookRefJson, prodSupplier);

				createTPA_Extensions(ownerDoc, tPA_Extensions, reqBodyJson,usrCtx);
			}

			JSONArray paxDetailsJsArr = reqBodyJson.getJSONArray(JSON_PROP_PAXDETAILS);

			if (XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:SpecialReqDetails") == null) {
				specialRqDetElem = ownerDoc.createElementNS(NS_OTA, "ota:SpecialReqDetails");
				travelerInfoElem.appendChild(specialRqDetElem);
			} else {
				specialRqDetElem = XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:SpecialReqDetails");
			}

			// specialRqDetElem=XMLUtils.getFirstElementAtXPath(travelerInfoElem,
			// "./ota:SpecialReqDetails");
			if (prevSuppID.equalsIgnoreCase(suppID)) {
				specialRqDetElem = XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:SpecialReqDetails");
			} else {
				specialRqDetElem = ownerDoc.createElementNS(NS_OTA, "ota:SpecialReqDetails");
				travelerInfoElem.appendChild(specialRqDetElem);
				specialRqDetElem = XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:SpecialReqDetails");
			}
			for (int i = 0,adtPaxListIdx=0; i < paxDetailsJsArr.length(); i++) {
				JSONObject traveller = (JSONObject) paxDetailsJsArr.get(i); 
				
				String paxType=traveller.getString(JSON_PROP_PAXTYPE);
				
				if(paxType.equalsIgnoreCase(JSON_PROP_INF)) {
					createINFSSRElem(ownerDoc, specialRqDetElem, i, traveller, flightMap,flightSegJsonMap,adtPaxRphList,adtPaxListIdx);
					adtPaxListIdx++;
				}
				
				// getSSR for passengers
				if (!traveller.isNull(JSON_PROP_SPECIALREQS)) {

					createSSRElem(ownerDoc, specialRqDetElem, i, traveller, flightMap);
				} else if (!traveller.isNull(JSON_PROP_SEATMAP)) {

					createSeatMapElem(ownerDoc, specialRqDetElem, i, traveller, flightMap, flightSegJsonMap);
				}
			}
			// to here
			prevSuppID = suppID;
		}

		return reqElem;
	}

	private static void createINFSSRElem(Document ownerDoc, Element specialRqDetElem, int i, JSONObject traveller,
			Map<String, String> flightMap, Map<String, JSONObject> flightSegJsonMap, List<Integer> adtPaxRphList, int adtPaxListIdx) {
		
		Element specialServiceRequestsElem=XMLUtils.getFirstElementAtXPath(specialRqDetElem, "./ota:SpecialServiceRequests");
		
		
		if(specialServiceRequestsElem==null) {
			specialServiceRequestsElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequests");
		}
		
		//Element specialServiceRequestsElem = ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequests");

		//JSONArray specialServiceRequests = traveller.getJSONObject("specialRequests")
			//	.getJSONArray("specialRequestInfo");

		for(Map.Entry<String, JSONObject> entry :flightSegJsonMap.entrySet())
		{	
			JSONObject flightSegJson=entry.getValue();

			
			Element specialServiceRequestElem = ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequest");

			specialServiceRequestElem.setAttribute("TravelerRefNumberRPHList", Integer.toString(adtPaxRphList.get(adtPaxListIdx)));

			specialServiceRequestElem.setAttribute("FlightRefNumberRPHList",
					flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).optString(JSON_PROP_FLIGHTNBR));
			

			specialServiceRequestElem.setAttribute("SSRCode","INFT");
					

			specialServiceRequestElem.setAttribute("ServiceQuantity","1");
					

			specialServiceRequestElem.setAttribute("Type", "");

			specialServiceRequestElem.setAttribute("Status", "");

			specialServiceRequestElem.setAttribute("Number","");
					
			Element airlineElem = ownerDoc.createElementNS(NS_OTA, "ota:Airline");

			airlineElem.setAttribute("CompanyShortName",
					flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).optString(JSON_PROP_AIRLINECODE));

			airlineElem.setAttribute("Code","");

			specialServiceRequestElem.appendChild(airlineElem);

			Element textElem = ownerDoc.createElementNS(NS_OTA, "ota:Text");

			textElem.setTextContent("");

			specialServiceRequestElem.appendChild(textElem);

			Element flightLegElem = ownerDoc.createElementNS(NS_OTA, "ota:FlightLeg");

			flightLegElem.setAttribute("FlightNumber", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).optString(JSON_PROP_FLIGHTNBR));

		/*	flightLegElem.setAttribute("ResBookDesigCode",
					flightMap.get((specialServiceRequestJson.getString((JSON_PROP_FLIGHTNBR))).replaceAll("\\s", ""))
							.toString());*/
			
			
			flightLegElem.setAttribute("ResBookDesigCode",flightSegJson.optString(JSON_PROP_RESBOOKDESIG));

			specialServiceRequestElem.appendChild(flightLegElem);

			Element categoryCodeElem = ownerDoc.createElementNS(NS_OTA, "ota:CategoryCode");

			categoryCodeElem.setTextContent("");

			specialServiceRequestElem.appendChild(categoryCodeElem);

			Element travelerRefElem = ownerDoc.createElementNS(NS_OTA, "ota:TravelerRef");

			travelerRefElem.setTextContent(traveller.getString(JSON_PROP_PAXTYPE));

			specialServiceRequestElem.appendChild(travelerRefElem);

			/*if (specialServiceRequestJson.has(JSON_PROP_AMOUNT)) {
				Element servicePriceElem = ownerDoc.createElementNS(NS_OTA, "ota:ServicePrice");

				servicePriceElem.setAttribute("Total", specialServiceRequestJson.optString(JSON_PROP_AMOUNT));
				servicePriceElem.setAttribute("CurrencyCode", specialServiceRequestJson.optString(JSON_PROP_CCYCODE));

				if (specialServiceRequestJson.has(JSON_PROP_SVCPRC)) {
					Element basePriceElem = ownerDoc.createElementNS(NS_OTA, "ota:BasePrice");

					basePriceElem.setAttribute("Amount",
							specialServiceRequestJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_BASEPRICE));

					servicePriceElem.appendChild(basePriceElem);
				}

				if (specialServiceRequestJson.has(JSON_PROP_TAXES)) {
					Element taxesElem = ownerDoc.createElementNS(NS_OTA, "ota:Taxes");

					taxesElem.setAttribute("Amount",
							specialServiceRequestJson.getJSONObject(JSON_PROP_TAXES).optString(JSON_PROP_AMOUNT));

					servicePriceElem.appendChild(taxesElem);
				}

				specialServiceRequestElem.appendChild(servicePriceElem);
			}*/

			specialServiceRequestsElem.appendChild(specialServiceRequestElem);

			specialRqDetElem.appendChild(specialServiceRequestsElem);

		}
		
	}

	private static void createSeatMapElem(Document ownerDoc, Element specialRqDetElem, int i, JSONObject traveller,
			Map<String, String> flightMap, Map<String, JSONObject> flightSegJsonMap) {

		Element seatRequests = XMLUtils.getFirstElementAtXPath(specialRqDetElem, "./ota:SeatRequests");

		if (seatRequests == null) {
			seatRequests = ownerDoc.createElementNS(NS_OTA, "ota:SeatRequests");
		}
		JSONArray seatMapJsonArr = traveller.getJSONArray(JSON_PROP_SEATMAP);
		JSONObject seatMapObj = seatMapJsonArr.getJSONObject(0);
		if (flightSegJsonMap.containsKey(seatMapObj.optString(JSON_PROP_FLIGHTNBR))) {
			JSONObject flightSegJson = flightSegJsonMap.get(seatMapObj.optString(JSON_PROP_FLIGHTNBR));

			JSONArray seatMapArr = traveller.getJSONArray(JSON_PROP_SEATMAP);

			for (int j = 0; j < seatMapArr.length(); j++) {

				JSONObject seatMapJson = seatMapArr.getJSONObject(j);

				// cabin class Array

				JSONArray cabinClassArr = seatMapJson.getJSONArray(JSON_PROP_CABINCLASS);
				for (int y = 0; y < cabinClassArr.length(); y++) {

					JSONObject cabinClassJSon = cabinClassArr.getJSONObject(y);

					JSONArray rowInfoArr = cabinClassJSon.getJSONArray(JSON_PROP_ROWINFO);
					for (int z = 0; z < rowInfoArr.length(); z++) {
						JSONObject rowInfoJson = rowInfoArr.getJSONObject(z);

						JSONArray seatInfoArr = rowInfoJson.getJSONArray(JSON_PROP_SEATINFO);

						for (int t = 0; t < seatInfoArr.length(); t++) {
							JSONObject seatInfoJson = seatInfoArr.getJSONObject(t);

							// for(int i = 0 ;i < ; i++)
							Element seatMapRequestElem = ownerDoc.createElementNS(NS_OTA, "ota:SeatRequest");
							seatMapRequestElem.setAttribute("FlightNumber",
									flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).optString(JSON_PROP_FLIGHTNBR));
							seatMapRequestElem.setAttribute("RowNumber", rowInfoJson.optString(JSON_PROP_ROWNBR));
							seatMapRequestElem.setAttribute("SeatNumber", seatInfoJson.optString(JSON_PROP_SEATNBR));
							seatMapRequestElem.setAttribute("TravelerRefNumberRPHList", Integer.toString(i));
							seatMapRequestElem.setAttribute("FlightRefNumberRPHList",
									seatMapJson.getString(JSON_PROP_FLIGHTREFRPH));
							seatMapRequestElem.setAttribute("SeatPreference", "");
							// seatMapRequestElem.setAttribute("SeatSequenceNumber",
							// seatInfoJson.optString("seatSequenceNumber"));

							Element deptElem = ownerDoc.createElementNS(NS_OTA, "ota:DepartureAirport");
							deptElem.setAttribute("LocationCode", flightSegJson.getString(JSON_PROP_ORIGLOC));
							// deptElem.setAttribute("CodeContext", "");
							seatMapRequestElem.appendChild(deptElem);

							Element arrivalElem = ownerDoc.createElementNS(NS_OTA, "ota:ArrivalAirport");
							arrivalElem.setAttribute("LocationCode", flightSegJson.getString(JSON_PROP_DESTLOC));
							// arrivalElem.setAttribute("CodeContext", "");
							seatMapRequestElem.appendChild(arrivalElem);

							Element airlineElem = ownerDoc.createElementNS(NS_OTA, "ota:Airline");
							/*
							 * airlineElem.setAttribute("CompanyShortName", "");
							 * airlineElem.setAttribute("TravelSector", "");
							 */
							airlineElem.setAttribute("Code", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE)
									.optString(JSON_PROP_AIRLINECODE));
							/*
							 * airlineElem.setAttribute("CodeContext", "");
							 * airlineElem.setAttribute("CountryCode", "");
							 * airlineElem.setAttribute("Division", "");
							 * airlineElem.setAttribute("Department", "");
							 */

							seatMapRequestElem.appendChild(airlineElem);

							seatRequests.appendChild(seatMapRequestElem);
						}
					}
				}
			}
		}
		specialRqDetElem.appendChild(seatRequests);

	}

	// This method retrieves information that was saved during reprice for the same
	// sessionID and enriches reqBodyJson
	private static void enrichRequestBodyJson(JSONObject reqHdrJson, JSONObject reqBodyJson, UserContext usrCtx, Map<String, Integer> pricedItinKeyIndexMap) throws Exception {
		// Retrieve sessionID from request header. The sessionID is part of the key
		// to retrieve context information from Redis cache
		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);

		Map<String, String> reprcSuppFaresMap = null;
		
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			String redisKey = sessionID.concat("|").concat(PRODUCT_AIR);
			reprcSuppFaresMap = redisConn.hgetAll(redisKey);
			
			if (reprcSuppFaresMap == null || reprcSuppFaresMap.isEmpty()) {
				logger.warn(String.format("Booking context not found for %s", redisKey));
				throw new ValidationException(reqHdrJson,"TRLERR400");
				
			}
		}
		String clientCcy = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		String clientMkt = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		JSONArray pricedItinsJSONArr = reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
		for (int y = 0; y < pricedItinsJSONArr.length(); y++) {
			JSONObject pricedItinJson = pricedItinsJSONArr.getJSONObject(y);
			String pricedItinRedisKey = AirRepriceProcessor.getRedisKeyForPricedItinerary(pricedItinJson);
			pricedItinKeyIndexMap.put(pricedItinRedisKey, y);
			String suppCcy = pricedItinJson.getJSONObject(JSON_PROP_AIRPRICEINFO).getJSONObject(JSON_PROP_ITINTOTALFARE)
					.getString(JSON_PROP_CCYCODE);
			pricedItinJson.put(JSON_PROP_ROE, RedisRoEData.getRateOfExchange(suppCcy, clientCcy, clientMkt));
			JSONObject suppPriceBookInfoJson = new JSONObject(reprcSuppFaresMap.get(pricedItinRedisKey));
			
			//put enabler and source supplierID
			pricedItinJson.put(JSON_PROP_ENABLERSUPPID, suppPriceBookInfoJson.optString(JSON_PROP_ENABLERSUPPID));
			pricedItinJson.put(JSON_PROP_SOURCESUPPID, suppPriceBookInfoJson.optString(JSON_PROP_SOURCESUPPID));
			
			JSONArray clientCommercialItinInfoArr = suppPriceBookInfoJson.getJSONArray(JSON_PROP_CLIENTCOMMITININFO);
			suppPriceBookInfoJson.remove(JSON_PROP_CLIENTCOMMITININFO);
			JSONArray clientCommercialItinTotalInfoArr = suppPriceBookInfoJson
					.getJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			suppPriceBookInfoJson.remove(JSON_PROP_CLIENTENTITYTOTALCOMMS);

			String suppID=pricedItinJson.getString(JSON_PROP_SUPPREF);
			ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT,
					PROD_CATEG_SUBTYPE_FLIGHT, suppID);
			
			
			if (prodSupplier == null) {
				logger.warn(String.format("Product supplier %s not found for user/client", suppID));
				throw new ValidationException(reqHdrJson,"TRLERR400");
			}
			String credsName= prodSupplier.getCredentialsName();
			String suppName= prodSupplier.getSuppName();
			 Credential pccCredential = prodSupplier.getCredentialForKey(MDM_VAL_PCC);
			 if(pccCredential==null)
				 pccCredential = prodSupplier.getCredentialForKey("Pseudo City Code");
			 if (pccCredential != null) {
					pricedItinJson.put(JSON_PROP_PSEUDOCC, pccCredential.getValue());
				}
				else {
					pricedItinJson.put(JSON_PROP_PSEUDOCC,"");
				}
				pricedItinJson.put(JSON_PROP_CREDSNAME,credsName);
				pricedItinJson.put(JSON_PROP_SUPPNAME,suppName);
			// Book request should not be contain any airItinerayPricingInfo in
			// pricedItinerary.
			// This JSON subelement should always be retrieved from reprice context.
			JSONObject airPriceInfoJson = suppPriceBookInfoJson.optJSONObject(JSON_PROP_AIRPRICEINFO);
			if (airPriceInfoJson == null) {
				throw new Exception(
						String.format("Pricing details for %s were not found in booking context", pricedItinRedisKey));
			}
			pricedItinJson.put(JSON_PROP_AIRPRICEINFO, airPriceInfoJson);

			JSONObject airPriceItinInfo = pricedItinJson.getJSONObject(JSON_PROP_AIRPRICEINFO);
			airPriceItinInfo.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercialItinTotalInfoArr);
			JSONArray paxTypeFaresJsonArr = airPriceItinInfo.getJSONArray(JSON_PROP_PAXTYPEFARES);

			Map<String, JSONArray> clientCommPaxMap = new HashMap<String, JSONArray>();
			for (int t = 0; t < clientCommercialItinInfoArr.length(); t++) {
				JSONObject clientCommercialItin = new JSONObject();
				clientCommercialItin = clientCommercialItinInfoArr.getJSONObject(t);
				clientCommPaxMap.put(clientCommercialItin.get(JSON_PROP_PAXTYPE).toString(),
						clientCommercialItin.getJSONArray(JSON_PROP_CLIENTENTITYCOMMS));
			}
			airPriceItinInfo.put(JSON_PROP_OFFERS, airPriceInfoJson.optJSONArray(JSON_PROP_OFFERS));
			
			//This part is to get with Redemption OfferCodes from BRMS
			JSONArray airPriceItinOfferRedisArr = airPriceInfoJson.optJSONArray(JSON_PROP_OFFERS);
			JSONArray OfferCodeArr=new JSONArray();
			if(!(airPriceItinOfferRedisArr==null || airPriceItinOfferRedisArr.length()==0)) {
				getWithRedemptionOfferIds(airPriceItinOfferRedisArr,OfferCodeArr);
			}
			
			
			JSONArray paxTypeFaresRedisJsonArr=airPriceInfoJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
			for (int z = 0; z < paxTypeFaresJsonArr.length(); z++) {
				JSONObject paxTypeFareJson = paxTypeFaresJsonArr.getJSONObject(z);
				JSONObject paxTypeFareRedisJson = paxTypeFaresRedisJsonArr.getJSONObject(z);
				clientCommPaxMap.get(paxTypeFareJson.get(JSON_PROP_PAXTYPE).toString());
				paxTypeFareJson.put(JSON_PROP_CLIENTENTITYCOMMS,
						clientCommPaxMap.get(paxTypeFareJson.get(JSON_PROP_PAXTYPE).toString()));
				paxTypeFareJson.put(JSON_PROP_OFFERS, paxTypeFareRedisJson.optJSONArray(JSON_PROP_OFFERS));
				
				//This part is to get with Redemption OfferCodes from BRMS
				JSONArray paxTypeFareofferRedisArr = paxTypeFareRedisJson.optJSONArray(JSON_PROP_OFFERS);
				if(!(paxTypeFareofferRedisArr==null || paxTypeFareofferRedisArr.length()==0)) {
					getWithRedemptionOfferIds(paxTypeFareofferRedisArr,OfferCodeArr);
				}	
			}

			if(OfferCodeArr.length()!=0) {
				JSONObject offerRedeemptionReq=new JSONObject();
				offerRedeemptionReq.put(JSON_PROP_USERID, reqHdrJson.getString(JSON_PROP_USERID));
				offerRedeemptionReq.put(JSON_PROP_BOOKID, reqBodyJson.getString(JSON_PROP_BOOKID));
				offerRedeemptionReq.put("offerIDs", OfferCodeArr);
				offerRedeemptionReq.put("operation", "booking");
				
				ServiceConfig commTypeConfig = AirConfig.getOffersTypeConfig(OffersType.REDEEM_OFFER_CODE_GENERATION);
				JSONObject offerRedeemptionRes = null;
				try
				{
					logger.info("Before opening HttpURLConnection to BRMS for code generation");
					offerRedeemptionRes = HTTPServiceConsumer.consumeJSONService("BRMS_COMPANY_OFFER_CODE_GENERATION", commTypeConfig.getServiceURL(),commTypeConfig.getHttpHeaders(), offerRedeemptionReq);
					logger.info("HttpURLConnection to BRMS closed");
				}
			    catch(Exception x)
				{
					logger.warn("An exception occurred when calling code generation of Offer codes", x);
				}
				if(offerRedeemptionRes==null) {
					logger.info("Codes not generated for request"+offerRedeemptionReq.toString());
				}	
				else {
					pricedItinJson.put("offerCodes",offerRedeemptionRes.getJSONArray("redemptionOfferCodes"));
				}
			}
			
			
			pricedItinJson.put(JSON_PROP_SUPPINFO, suppPriceBookInfoJson);
			
			//Adding FinanceControlId
			ProductSupplier productSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, pricedItinJson.getString("supplierRef"));
			String financeControlId = productSupplier.getFinanceControlId()!=null? productSupplier.getFinanceControlId() : "";
			
			pricedItinJson.put("financeControlId", financeControlId);
			JSONObject airJsonObject = pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY);
			JSONArray destOptionArray = airJsonObject.getJSONArray(JSON_PROP_ORIGDESTOPTS);
			for (int i=0;i<destOptionArray.length();i++ )
			{
				JSONObject destOptionJsonObject = destOptionArray.getJSONObject(i);
				JSONArray flightSegmentJsonArray = destOptionJsonObject.getJSONArray(JSON_PROP_FLIGHTSEG);
				for (int j=0;j<flightSegmentJsonArray.length();j++)
				{
					JSONObject flightJsonObject = (JSONObject) flightSegmentJsonArray.get(j);
					JSONObject marketingLine = flightJsonObject.getJSONObject(JSON_PROP_MARKAIRLINE);
					String marketLineAirlineCode = marketingLine.optString(JSON_PROP_AIRLINECODE);
					String marketLineFlightNumber = marketingLine.optString(JSON_PROP_FLIGHTNBR);
					String marketLineAirlineName = getAirlineName(marketLineAirlineCode,marketLineFlightNumber);
					marketingLine.put(JSON_PROP_AIRLINENAME,marketLineAirlineName);

					JSONObject operatingJson = flightJsonObject.getJSONObject(JSON_PROP_OPERAIRLINE);
					String operatingAirlineCode = operatingJson.optString(JSON_PROP_AIRLINECODE);
					String operatingFlightNumber = operatingJson.optString(JSON_PROP_FLIGHTNBR);
					String operatingAirlineName = getAirlineName(operatingAirlineCode,operatingFlightNumber);
					operatingJson.put(JSON_PROP_AIRLINENAME,operatingAirlineName);
				}
			}
		}
		
		
	}

	
	private static void getWithRedemptionOfferIds(JSONArray offerDetailsSetArr,JSONArray offerCodesArr) {
		for(int i=0;i<offerDetailsSetArr.length();i++) {
			JSONObject offrDetailSetObj = offerDetailsSetArr.getJSONObject(i);
			String withOrWithoutRedemption = offrDetailSetObj.getString("offerApplicability");
			if("WithRedemption".equalsIgnoreCase(withOrWithoutRedemption)) {
				offerCodesArr.put(offrDetailSetObj.getString(JSON_PROP_OFFERID));
			}
		}
	}

	public static String getAirlineName(String airlineCode, String flightNumber)
	{
		String airlineName = new String();
	try {
		if (!StringUtils.isEmpty(airlineCode))
		{
			Map<String, Object> iataFilters = new HashMap<String, Object>();
			iataFilters.put(MDM_PROP_SCREEN1.concat(".").concat(MDM_PROP_SCREEN1_IATACODE),airlineCode);
			org.bson.Document docs = org.bson.Document.parse(MDMUtils.getData(MDM_PROP_PRODUCT_AIR,iataFilters,null));
			List<org.bson.Document> dataDoc = (List<org.bson.Document>) docs.get("data");
			if (dataDoc!=null && dataDoc.size()>0)
			{
				org.bson.Document document = (org.bson.Document) dataDoc.get(0);
				JSONObject productAirJson=new JSONObject(new JSONTokener(document.toJson()));
				JSONObject screen1 = productAirJson.getJSONObject(MDM_PROP_SCREEN1);
				if (screen1.optBoolean(MDM_PROP_BRAND_AIRLINE))
				{
					boolean brandAirline = screen1.getBoolean(MDM_PROP_BRAND_AIRLINE);
					if (brandAirline)
					{
						JSONArray conditionForFSCAndLCC = screen1.getJSONArray(MDM_PROP_FSC_LCC);
						for (int i =0;i<conditionForFSCAndLCC.length();i++)
						{
							JSONObject conditionForFSCAndLCCJsonObject = (JSONObject)conditionForFSCAndLCC.get(i);
							BigDecimal flightNumberTo = conditionForFSCAndLCCJsonObject.getBigDecimal(MDM_PROP_FLIGHT_NUMBER_TO);
							BigDecimal flightNumberFrom = conditionForFSCAndLCCJsonObject.getBigDecimal(MDM_PROP_FLIGHT_NUMBER_FROM);
							BigDecimal fNumber = new BigDecimal(flightNumber);
							int lesser = flightNumberFrom.compareTo(fNumber);
							int greater = flightNumberTo.compareTo(fNumber);
							if ((lesser==-1 || lesser==0) && (greater==1 || greater==0)) {
								airlineName = screen1.getString(MDM_PROP_PRODUCT_NAME);
								return airlineName;
							}
						}
					}
				}
				else {
					airlineName = screen1.getString(MDM_PROP_PRODUCT_NAME);
					return airlineName;
				}
			}
		}
	}
	catch(Exception ex) {
		logger.error("error while fetching airlineName");
	}
		return airlineName;
	}


	private static void sendPreBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson,
			UserContext usrCtx, Map<String, Integer> pricedItinKeyIndexMap) throws Exception {
		// JSONObject kafkaMsgJson = reqJson;
		JSONObject kafkaMsg = new JSONObject(new JSONTokener(reqJson.toString()));
		JSONObject reqBodyJson = kafkaMsg.getJSONObject(JSON_PROP_REQBODY);
		addFareInfo(kafkaMsg,pricedItinKeyIndexMap);
		JSONObject reqHeaderJson = kafkaMsg.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYNAME, usrCtx.getOrganizationHierarchy().getCompanyName());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		reqHeaderJson.put(JSON_PROP_COMPANYMKT, usrCtx.getOrganizationHierarchy().getCompanyMarket());
		reqHeaderJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
		reqHeaderJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
		reqHeaderJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, PRODUCT_AIR);
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());

		setPriorityRQ(PRODUCT_AIR, kafkaMsg);

		bookProducer.runProducer(1, kafkaMsg);
		logger.trace(String.format("BOOKKAFKA_RQ: %s", kafkaMsg.toString()));
	}

	private static void addFareInfo(JSONObject reqJson, Map<String, Integer> pricedItinKeyIndexMap) {
		try {
			JSONObject kafkaMsg = new JSONObject(new JSONTokener(AirFareRuleProcessor.process(reqJson)));
			JSONObject reqBody=reqJson.getJSONObject(JSON_PROP_REQBODY); 
			JSONArray pricedItinJsonArr=reqBody.getJSONArray(JSON_PROP_PRICEDITIN);
			JSONArray fareRulePricedItinJsonArr=kafkaMsg.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRICEDITIN);
		/*	if((kafkaMsg!=null) && (kafkaMsg.optJSONObject(JSON_PROP_RESBODY)!=null)) {
				
				reqBody.put(JSON_PROP_FAREINFO, kafkaMsg.getJSONObject(JSON_PROP_RESBODY).optJSONArray(JSON_PROP_FAREINFO));
			}*/
			
			if(fareRulePricedItinJsonArr!=null && fareRulePricedItinJsonArr.length()>0) {
				for(int i=0;i<pricedItinJsonArr.length();i++) {
					JSONObject fareRulePricedItinJson=fareRulePricedItinJsonArr.getJSONObject(i);
					String fareInfoPriceItinKey=getFareInfoKeyForPricedItinerary(fareRulePricedItinJson);
					if(pricedItinKeyIndexMap.containsKey(fareInfoPriceItinKey)) {
						JSONObject pricedItinJson=(JSONObject) pricedItinJsonArr.get(pricedItinKeyIndexMap.get(fareInfoPriceItinKey));
						pricedItinJson.put(JSON_PROP_FAREINFO, fareRulePricedItinJson.getJSONArray(JSON_PROP_FAREINFO));
					}
					
					
				}
			}
			
			
			
		} catch (RequestProcessingException | InternalProcessingException e) {
			
			e.printStackTrace();
		}
		
	}
	/*private static void addFareInfoV2(JSONObject pricedItinJson, String pricedItinRedisKey) {
		try {
		
			
		
			
			
			
		} catch (RequestProcessingException | InternalProcessingException e) {
			
			e.printStackTrace();
		}
		
	}*/

	private static void sendPostBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson,
			JSONObject resJson, UserContext usrCtx) throws Exception {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		resJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID,
				reqBodyJson.get(JSON_PROP_BOOKID).toString());
		JSONObject kafkaMsgJson = new JSONObject(new JSONTokener(resJson.toString()));
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PROD, PRODUCT_AIR);

		// kafkaMsgJson.put("messageType", "CONFIRM");
	/*	kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID,
				reqBodyJson.get(JSON_PROP_BOOKID).toString());*/

		setPriorityRS(PRODUCT_AIR, reqJson, kafkaMsgJson);
		JSONArray suppBookRefJsonArr=resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_SUPPBOOKREFS);
		JSONArray kafkaSuppBookRefJsonArr=kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_SUPPBOOKREFS);
		JSONArray pricedItinJsonArr=reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
	
		
		if(suppBookRefJsonArr.length()!=pricedItinJsonArr.length()) {
			String prevSuppId="";
			JSONObject suppBookRefJson=null;
			JSONArray newKafkaSuppBookRefJsonArr=new JSONArray();
			for(int j=0,k=0;j<pricedItinJsonArr.length();j++) {
				JSONObject pricedItinJson=pricedItinJsonArr.getJSONObject(j);
				String suppId=pricedItinJson.getString(JSON_PROP_SUPPREF);
				if(suppId.equalsIgnoreCase(prevSuppId)) {
					
					newKafkaSuppBookRefJsonArr.put(suppBookRefJson);
					continue;
				}
			    suppBookRefJson=kafkaSuppBookRefJsonArr.getJSONObject(k);
				ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppId);
				if (prodSupplier == null) {
					throw new Exception(String.format("Product supplier %s not found for user/client", suppId));
				}
				
				suppBookRefJson.put(JSON_PROP_ISGDS, prodSupplier.isGDS());
			    k++;
				newKafkaSuppBookRefJsonArr.put(suppBookRefJson);
				prevSuppId=suppId;
				
			}
			kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_SUPPBOOKREFS,newKafkaSuppBookRefJsonArr);
		}
		else {
			JSONArray newKafkaSuppBookRefJsonArr=new JSONArray();
			for(int j=0;j<kafkaSuppBookRefJsonArr.length();j++) {
				JSONObject suppBookRefJson=kafkaSuppBookRefJsonArr.getJSONObject(j);
				String suppId=suppBookRefJson.getString(JSON_PROP_SUPPREF);
				ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppId);
				if (prodSupplier == null) {
					throw new Exception(String.format("Product supplier %s not found for user/client", suppId));
				}
				
				suppBookRefJson.put(JSON_PROP_ISGDS, prodSupplier.isGDS());
				newKafkaSuppBookRefJsonArr.put(suppBookRefJson);
			}
			kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_SUPPBOOKREFS,newKafkaSuppBookRefJsonArr);
			
		}
		for(int i=0;i<suppBookRefJsonArr.length();i++) {
			JSONObject suppBookRefJson=suppBookRefJsonArr.getJSONObject(i);
			suppBookRefJson.remove("errorCode");
			suppBookRefJson.remove("errorMessage");
		}
			
		
		
		bookProducer.runProducer(1, kafkaMsgJson);
		logger.trace(String.format("BOOKKAFKA_RS: %s", kafkaMsgJson.toString()));
		String sessionID = reqJson.getJSONObject(JSON_PROP_REQHEADER).getString(JSON_PROP_SESSIONID);
		
		removePriorityObj(kafkaMsgJson);
		
		Map<String, String> reprcSuppFaresMap = null;
		String redisKey=null;
		
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			 redisKey = sessionID.concat("|").concat(PRODUCT_AIR);
			reprcSuppFaresMap = redisConn.hgetAll(redisKey);
			
			if (reprcSuppFaresMap == null || reprcSuppFaresMap.isEmpty()) {
				throw new Exception(String.format("Booking context not found for %s", redisKey));
			}
			
			redisConn.del(redisKey,redisKey+"|ptcXML");
		}
	}

	private static void removePriorityObj(JSONObject resJson)
	{
		JSONArray suppBookRefs = resJson.getJSONObject("responseBody").getJSONArray("supplierBookReferences");
		
		for(int i=0;i<suppBookRefs.length();i++)
		{
			JSONObject suppBookRef = suppBookRefs.getJSONObject(i);
			suppBookRef.remove("priority");
		}
	}
	
	private static void setPriorityRQ(String product, JSONObject kafkaMsg) {
		String priorityStr = BookingPriority.getPriorityForBE(product, kafkaMsg, "On request");

		JSONArray priorityArr = new JSONArray(priorityStr.toString());
		JSONArray pricedItinArr = kafkaMsg.getJSONObject("requestBody").getJSONArray("pricedItinerary");

		for (int i = 0; i < pricedItinArr.length(); i++) {
			JSONObject pricedItin = pricedItinArr.getJSONObject(i);
			pricedItin.put("priority", priorityArr.getJSONObject(i));
		}
	}

	private static void setPriorityRS(String product, JSONObject reqJson, JSONObject kafkaMsg) {
		String priorityStr = BookingPriority.getPriorityForBE(product, reqJson, "Confirmed");

		JSONArray priorityArr = new JSONArray(priorityStr.toString());
		JSONArray suppBookArr = kafkaMsg.getJSONObject("responseBody").getJSONArray("supplierBookReferences");

		for (int i = 0; i < suppBookArr.length(); i++) {
			JSONObject suppBook = suppBookArr.getJSONObject(i);
			suppBook.put("priority", priorityArr.getJSONObject(i));
		}
	}

	public static void createCompanyAddress(Document ownerDoc,  Element address,
			JSONObject orgEntityJson) {
		
		JSONObject addrJson = orgEntityJson.optJSONObject(MDM_PROP_ADDRESS);
		
		Element StreetNmbr = ownerDoc.createElementNS(NS_OTA, "ota:StreetNmbr");
		address.appendChild(StreetNmbr);
		StreetNmbr.setTextContent(addrJson.getString(MDM_PROP_STREET).replace("\"", ""));

			Element BldgRoom = ownerDoc.createElementNS(NS_OTA, "ota:BldgRoom");
			address.appendChild(BldgRoom);
			BldgRoom.setTextContent(addrJson.getString(MDM_PROP_HOUSENO).replace("\"", ""));

			BldgRoom.setAttribute("BldgNameIndicator", "false");

			Element AddressLine = ownerDoc.createElementNS(NS_OTA, "ota:AddressLine");
			address.appendChild(AddressLine);
			AddressLine.setTextContent((addrJson.getString(MDM_PROP_STREET).replace("\"", "")));
			

			Element CityName = ownerDoc.createElementNS(NS_OTA, "ota:CityName");
			address.appendChild(CityName);
			CityName.setTextContent((addrJson.getString(MDM_PROP_CITY)).replace("\"", ""));
			

			Element PostalCode = ownerDoc.createElementNS(NS_OTA, "ota:PostalCode");
			address.appendChild(PostalCode);
			PostalCode.setTextContent(addrJson.getString(MDM_PROP_POSTCODE).replace("\"", ""));
			

			Element County = ownerDoc.createElementNS(NS_OTA, "ota:County");
			address.appendChild(County);
			JSONObject countryAnciJson=MDMUtils.getCountryData(addrJson.getString(MDM_PROP_COUNTRY));
			//County.setTextContent((addrJson.getString(MDM_PROP_COUNTRY)).replace("\"", ""));
			County.setTextContent((countryAnciJson.getJSONObject(MDM_PROP_DATA).getString(MDM_PROP_CODE)).replace("\"", ""));
		
			Element StateProv = ownerDoc.createElementNS(NS_OTA, "ota:StateProv");
			address.appendChild(StateProv);
			JSONObject stateAnciJson=MDMUtils.getState(addrJson.getString(MDM_PROP_STATE));
			String stateCode=stateAnciJson.getJSONObject(MDM_PROP_DATA).getString(MDM_PROP_STATECODE);
			if(stateCode.length()>2) {
				stateCode=stateCode.substring(stateCode.indexOf("-")+1);
			}
			StateProv.setTextContent(stateCode.replace("\"", ""));
			

			Element CountryName = ownerDoc.createElementNS(NS_OTA, "ota:CountryName");
			address.appendChild(CountryName);
			CountryName.setAttribute("Code",countryAnciJson.getJSONObject(MDM_PROP_DATA).getString(MDM_PROP_CODE));
		}

		
	}


