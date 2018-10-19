package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.cruise.CruiseConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class CruiseBookProcessor implements CruiseConstants {
	
	public static String process(JSONObject reqJson) throws Exception
	{
		KafkaBookProducer bookProducer = new KafkaBookProducer();
		JSONObject kafkaMsgJson = new JSONObject();
		//JSONObject reqHdrJson=null;JSONObject reqBodyJson=null;UserContext userctx=null;OperationConfig opConfig=null;Element reqElem=null;
		JSONObject reqHdrJson=null;JSONObject reqBodyJson=null;UserContext userctx=null;ServiceConfig opConfig=null;Element reqElem=null;
		try
		{
			TrackingContext.setTrackingContext(reqJson);
			
			reqHdrJson = reqJson.getJSONObject("requestHeader");
			reqBodyJson = reqJson.getJSONObject("requestBody");
			
			userctx = UserContext.getUserContextForSession(reqHdrJson);
			opConfig = CruiseConfig.getOperationConfig("book");
			//kafkaMsgJson = reqJson;
			 kafkaMsgJson=new JSONObject(new JSONTokener(reqJson.toString()));
			reqElem = createSIRequest(reqHdrJson, reqBodyJson, userctx, opConfig);
		}
		catch(Exception e)
		{
			
		}
		
		Element resElem = null;
		// resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), CruiseConfig.getHttpHeaders(), reqElem);
		resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
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
        JSONArray cruiseRepriceDetailsJsonArr = new JSONArray();
        Element[] otaCruiseBookWrapperElems = XMLUtils.getElementsAtXPath(resElem, "./cru:ResponseBody/cru1:OTA_CruiseBookRSWrapper");
        for(Element otaCruiseBookWrapperElem : otaCruiseBookWrapperElems)
        {
        	//IF SI gives an error
        	Element[] errorListElems = XMLUtils.getElementsAtXPath(otaCruiseBookWrapperElem, "./ota:OTA_CruiseBookRS/ota:Errors/ota:Error");
        	if(errorListElems!=null && errorListElems.length!=0)
        	{
        		int errorInt=0;
        		JSONObject errorObj = new JSONObject();
        		for(Element errorListElem : errorListElems)
        		{
        			errorObj.put(String.format("%s %s", "Error",errorInt), XMLUtils.getValueAtXPath(errorListElem, "/ota:Error"));
        		}
        		return errorObj.toString();//Code will stop here if SI gives an error
        	}
        	CruiseRePriceProcessor.getSupplierResponseJSONV2(otaCruiseBookWrapperElem,cruiseRepriceDetailsJsonArr,"./ota:OTA_CruiseBookRS");
        }
        
        JSONObject reqHeaderJson = kafkaMsgJson.getJSONObject(JSON_PROP_REQHEADER);
        reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, userctx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, userctx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, userctx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, userctx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, userctx.getOrganizationHierarchy().getBU());
        kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put("product", PRODUCT_CRUISE);
        kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
        
        System.out.println(kafkaMsgJson);
		bookProducer.runProducer(1, kafkaMsgJson);
        
        resBodyJson.put("bookResp", cruiseRepriceDetailsJsonArr);
        
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, resBodyJson);
        
        kafkaMsgJson = new JSONObject();
        kafkaMsgJson = resJson;
        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("product", PRODUCT_CRUISE);
        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
        System.out.println(kafkaMsgJson);
        bookProducer.runProducer(1, kafkaMsgJson);
        
		return resJson.toString();
	}

	//private static Element createSIRequest(JSONObject reqHdrJson, JSONObject reqBodyJson, UserContext userctx, OperationConfig opConfig) throws Exception {
	private static Element createSIRequest(JSONObject reqHdrJson, JSONObject reqBodyJson, UserContext userctx, ServiceConfig opConfig) throws Exception {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();

		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./cru1:OTA_CruiseBookRQWrapper");
		reqBodyElem.removeChild(wrapperElem);


		String sessionID = reqHdrJson.getString("sessionID");

		
		CruiseSearchProcessor.createHeader(reqElem,reqHdrJson);
		
		/*Element sessionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SessionID");
		sessionElem.setTextContent(sessionID);

		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:TransactionID");
		transactionElem.setTextContent(transactionID);

		Element userElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:UserID");
		userElem.setTextContent(userID);*/
		
		Map<String, String> reprcSuppFaresMap = null;
		String redisKey = null;
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			redisKey = sessionID.concat("|").concat(PRODUCT_CRUISE);
			reprcSuppFaresMap = redisConn.hgetAll(redisKey);
		}
		if (reprcSuppFaresMap == null) {
			throw new Exception(String.format("Reprice context not found,for %s", redisKey));
		}
		
		JSONObject redisJson = new JSONObject();
		
		Element suppWrapperElem = null;
		int seqItr =0;
		
		JSONArray cruiseDetailsJsonArr = reqBodyJson.getJSONArray("cruiseDetails");
		
		for(int i=0;i<cruiseDetailsJsonArr.length();i++)
		{
			JSONObject supplierBody = cruiseDetailsJsonArr.getJSONObject(i);
			
			String suppID =	supplierBody.getString("supplierRef");
			
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
			
			//Request Header starts
			ProductSupplier prodSupplier = userctx.getSupplierForProduct(PROD_CATEG_TRANSPORT, "Cruise", suppID);
			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}
			seqItr++;
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru1:RequestHeader/com:SupplierCredentialsList");
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
			Element supplierCredentials = prodSupplier.toElement(ownerDoc);
			
//			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			//Request Body starts
				
			Element sequence = ownerDoc.createElementNS(Constants.NS_COM, "Sequence");
			sequence.setTextContent(String.valueOf(seqItr));
			
			Element credentialsElem = XMLUtils.getFirstElementAtXPath(supplierCredentials, "./com:Credentials");
			supplierCredentials.insertBefore(sequence, credentialsElem);
			
			suppCredsListElem.appendChild(supplierCredentials);
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
			Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./cru1:SupplierID");
			suppIDElem.setTextContent(suppID);
			
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./cru1:Sequence");
			sequenceElem.setTextContent(String.valueOf(seqItr));
			
			Element otaCruiseBookRqElem =	XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_CruiseBookRQ");
			
			CruisePriceProcessor.createPOS(ownerDoc, otaCruiseBookRqElem);
			
			Element sailingInfoElem = XMLUtils.getFirstElementAtXPath(otaCruiseBookRqElem, "./ota:SailingInfo");
			JSONObject sailingInfoJson = supplierBody.optJSONObject("sailingInfo");
			
			Element selectedSailingElem = XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:SelectedSailing");
			String voyageID = sailingInfoJson.optJSONObject("selectedSailing").optString("voyageId");
			selectedSailingElem.setAttribute("VoyageID", voyageID);
			
			Element selectedCategoryElem = XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:SelectedCategory");
			String pricedCategoryCode = sailingInfoJson.optJSONObject("selectedCategory").optString("pricedCategoryCode");
			selectedCategoryElem.setAttribute("PricedCategoryCode", pricedCategoryCode);
			selectedCategoryElem.setAttribute("FareCode", sailingInfoJson.optJSONObject("selectedCategory").optString("fareCode"));
			selectedCategoryElem.setAttribute("WaitlistIndicator", sailingInfoJson.optJSONObject("selectedCategory").optString("waitListIndicator"));
			
			Element selectedCabinElem =	XMLUtils.getFirstElementAtXPath(selectedCategoryElem, "./ota:SelectedCabin");
			String cabinNo = sailingInfoJson.optJSONObject("selectedCategory").optJSONObject("selectedCabin").optString("CabinNumber");
			selectedCabinElem.setAttribute("CabinNumber", cabinNo);
			selectedCabinElem.setAttribute("Status", sailingInfoJson.optJSONObject("selectedCategory").optJSONObject("selectedCabin").optString("Status"));
			selectedCabinElem.setAttribute("CabinCategoryStatusCode", sailingInfoJson.optJSONObject("selectedCategory").optJSONObject("selectedCabin").optString("cabinCategoryStatusCode"));
			selectedCabinElem.setAttribute("CabinCategoryCode", sailingInfoJson.optJSONObject("selectedCategory").optJSONObject("selectedCabin").optString("cabinCategoryCode"));
			selectedCabinElem.setAttribute("MinOccupancy", sailingInfoJson.optJSONObject("selectedCategory").optJSONObject("selectedCabin").optString("minOccupancy"));
			selectedCabinElem.setAttribute("MaxOccupancy", sailingInfoJson.optJSONObject("selectedCategory").optJSONObject("selectedCabin").optString("maxOccupancy"));
			selectedCabinElem.setAttribute("PositionInShip", sailingInfoJson.optJSONObject("selectedCategory").optJSONObject("selectedCabin").optString("positionInShip"));
			
			Element tpa_ExtensionsElem = XMLUtils.getFirstElementAtXPath(selectedCategoryElem, "./ota:TPA_Extensions");
			
			Element itineraryIdElem = XMLUtils.getFirstElementAtXPath(tpa_ExtensionsElem, "./cru1:ItineraryID");
			String itineraryID = sailingInfoJson.optJSONObject("selectedCategory").optJSONObject("selectedCabin").optString("itineraryId");
			itineraryIdElem.setTextContent(itineraryID);
			
			Element isMilitaryElem = XMLUtils.getFirstElementAtXPath(tpa_ExtensionsElem, "./cru1:IsMilitary");
			isMilitaryElem.setTextContent(sailingInfoJson.optJSONObject("selectedCategory").optString("isMilitary"));
			
			Element cruiseElem = XMLUtils.getFirstElementAtXPath(tpa_ExtensionsElem, "./cru1:Cruise");
			cruiseElem.setAttribute("Tax", sailingInfoJson.optJSONObject("selectedCategory").optString("tax"));
			
			Element sailingElem = XMLUtils.getFirstElementAtXPath(cruiseElem, "./cru1:SailingDates/cru1:Sailing");
			sailingElem.setAttribute("SailingID", sailingInfoJson.optJSONObject("selectedCategory").optString("sailingID"));
			
			JSONArray guestJsonArr = supplierBody.getJSONArray("Guests");
			
			setGuestDetails(ownerDoc,guestJsonArr,otaCruiseBookRqElem);
			
			JSONObject paymentOptionJson = supplierBody.optJSONArray("PaymentOptions").getJSONObject(0);
			
			Element paymentOptionElem =	XMLUtils.getFirstElementAtXPath(otaCruiseBookRqElem, "./ota:ReservationInfo/ota:PaymentOptions/ota:PaymentOption");
			paymentOptionElem.setAttribute("SplitPaymentInd", paymentOptionJson.optString("SplitPaymentInd"));
			
			String redisString = reprcSuppFaresMap.get(String.format("%s%c%s%c%s%c%s%c%s", suppID,KEYSEPARATOR,voyageID,KEYSEPARATOR,itineraryID,KEYSEPARATOR,pricedCategoryCode,KEYSEPARATOR,cabinNo));
			redisJson = new JSONObject(redisString);
			Element paymentAmountElem =	XMLUtils.getFirstElementAtXPath(paymentOptionElem, "./ota:PaymentAmount");
			paymentAmountElem.setAttribute("Amount", String.valueOf(redisJson.getJSONObject("suppPricingInfo").getJSONObject("suppTotalInfo").getInt("amount")));
//			paymentAmountElem.setAttribute("Amount", reprcSuppFaresMap.get(""));
			
			supplierBody.put("suppPricingInfo", redisJson.get("suppPricingInfo"));
			supplierBody.put("pricingInfo", redisJson.get("pricingInfo"));
			supplierBody.put("sailingInfoReprice", redisJson.get("sailingInfo"));
		}
		return reqElem;
	}
	
	public static void setGuestDetails(Document ownerDoc,JSONArray guestsReqJsonArr,Element otaCategoryAvail) throws Exception {
		 
		Element guestDetailsElem = XMLUtils.getFirstElementAtXPath(otaCategoryAvail, "./ota:ReservationInfo/ota:GuestDetails");
		
		for(int i=0; i<guestsReqJsonArr.length();i++)
		{
			Element guestDetailElem = ownerDoc.createElementNS(NS_OTA, "GuestDetail");
			JSONObject guestsReqJson = guestsReqJsonArr.getJSONObject(i);
			
			Element contactInfoElem = ownerDoc.createElementNS(NS_OTA, "ContactInfo");
			contactInfoElem.setAttribute("PersonBirthDate", guestsReqJson.optString("personBirthDate"));
			contactInfoElem.setAttribute("Gender", guestsReqJson.optString("gender"));
			contactInfoElem.setAttribute("Age", guestsReqJson.optString("age"));
			contactInfoElem.setAttribute("Nationality", guestsReqJson.optString("Nationality"));
			contactInfoElem.setAttribute("GuestRefNumber", guestsReqJson.optString("guestRefNumber"));
			contactInfoElem.setAttribute("LoyaltyMembershipID", guestsReqJson.optString("loyaltyMembershipId"));
			
			JSONObject guestNameJson = guestsReqJson.getJSONObject("guestName");
			Element personNameElem = ownerDoc.createElementNS(NS_OTA, "PersonName");
			
			Element namePrefixElem = ownerDoc.createElementNS(NS_OTA, "NamePrefix");
			namePrefixElem.setTextContent(guestNameJson.optString("namePrefix"));
			
			Element givenNameElem = ownerDoc.createElementNS(NS_OTA, "GivenName");
			givenNameElem.setTextContent(guestNameJson.optString("givenName"));
			
			Element middleNameElem = ownerDoc.createElementNS(NS_OTA, "MiddleName");
			middleNameElem.setTextContent(guestNameJson.optString("middleName"));
			
			Element surNameElem = ownerDoc.createElementNS(NS_OTA, "Surname");
			surNameElem.setTextContent(guestNameJson.optString("surName"));
			
			personNameElem.appendChild(surNameElem);
			personNameElem.insertBefore(middleNameElem, surNameElem);
			personNameElem.insertBefore(givenNameElem, middleNameElem);
			personNameElem.insertBefore(namePrefixElem, givenNameElem);
			
			JSONObject telephoneJson = guestsReqJson.getJSONObject("Telephone");
			
			Element telephoneElem =	ownerDoc.createElementNS(NS_OTA, "Telephone");
			telephoneElem.setAttribute("CountryAccessCode",telephoneJson.optString("CountryAccessCode"));
			telephoneElem.setAttribute("PhoneNumber",telephoneJson.optString("PhoneNumber"));
			
			Element emailElem =	ownerDoc.createElementNS(NS_OTA, "Email");
			emailElem.setTextContent(guestsReqJson.getString("Email"));
//-------------------------------------------------------------------Address------------------------------------------------------------
			
			JSONObject addressJson = guestsReqJson.optJSONObject("Address");
			Element addressElem = ownerDoc.createElementNS(NS_OTA, "Address");
			addressElem.setAttribute("Type", addressJson.optString("Type"));
			
			Element addressLineElem = ownerDoc.createElementNS(NS_OTA, "AddressLine");
			addressLineElem.setTextContent(addressJson.optString("AddressLine"));
			
			Element cityNameElem = ownerDoc.createElementNS(NS_OTA, "CityName");
			cityNameElem.setTextContent(addressJson.optString("CityName"));
			
			Element postalCodeElem = ownerDoc.createElementNS(NS_OTA, "PostalCode");
			postalCodeElem.setTextContent(addressJson.optString("PostalCode"));
			
			Element stateProvElem = ownerDoc.createElementNS(NS_OTA, "StateProv");
			stateProvElem.setTextContent(addressJson.optString("StateProv"));
			
			Element countryNameElem = ownerDoc.createElementNS(NS_OTA, "CountryName");
			countryNameElem.setTextContent(addressJson.optJSONObject("CountryName").optString("Name"));
			countryNameElem.setAttribute("Code", addressJson.optJSONObject("CountryName").optString("Code"));
			
			addressElem.appendChild(countryNameElem);
			addressElem.insertBefore(stateProvElem, countryNameElem);
			addressElem.insertBefore(postalCodeElem, stateProvElem);
			addressElem.insertBefore(cityNameElem, postalCodeElem);
			addressElem.insertBefore(addressLineElem, cityNameElem);
//------------------------------------------------------------------Address ends-----------------------------------------------------------------------
			contactInfoElem.appendChild(emailElem);
			contactInfoElem.insertBefore(addressElem,emailElem);
			contactInfoElem.insertBefore(telephoneElem, addressElem);
			contactInfoElem.insertBefore(personNameElem, telephoneElem);
			
			JSONObject travelDocumentJson = guestsReqJson.getJSONObject("TravelDocument");
			
			Element travelDocumentElem = ownerDoc.createElementNS(NS_OTA, "TravelDocument");
			travelDocumentElem.setAttribute("DocIssueCountry", travelDocumentJson.optString("DocIssueCountry"));
			travelDocumentElem.setAttribute("DocHolderNationality", travelDocumentJson.optString("DocHolderNationality"));
//			travelDocumentElem.setAttribute("EffectiveDate", travelDocumentJson.optString("EffectiveDate"));
//			travelDocumentElem.setAttribute("ExpireDate", travelDocumentJson.optString("ExpireDate"));
			travelDocumentElem.setAttribute("DocIssueStateProv", travelDocumentJson.optString("DocIssueStateProv"));
			
			JSONObject selectedDiningJson = guestsReqJson.getJSONObject("SelectedDining");
			
			Element selectedDiningElem = ownerDoc.createElementNS(NS_OTA, "SelectedDining");
			selectedDiningElem.setAttribute("Sitting", selectedDiningJson.optString("Sitting"));
			selectedDiningElem.setAttribute("Status", selectedDiningJson.optString("Status"));
			
			guestDetailElem.appendChild(selectedDiningElem);
			guestDetailElem.insertBefore(travelDocumentElem, selectedDiningElem);
			guestDetailElem.insertBefore(contactInfoElem, travelDocumentElem);
			
			guestDetailsElem.appendChild(guestDetailElem);
		}
		
		
    }
	
}
