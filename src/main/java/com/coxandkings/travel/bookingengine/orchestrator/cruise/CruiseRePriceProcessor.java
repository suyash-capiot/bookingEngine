package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.cruise.CruiseConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class CruiseRePriceProcessor implements CruiseConstants {
	
	private static final Logger logger = LogManager.getLogger(CruiseSearchProcessor.class);
	
	public static String process(JSONObject reqJson) throws Exception
	{
		//JSONObject reqHdrJson =null;JSONObject reqBodyJson =null;UserContext userctx =null;OperationConfig opConfig =null;Element reqElem =null;
		JSONObject reqHdrJson =null;JSONObject reqBodyJson =null;UserContext userctx =null;ServiceConfig opConfig =null;Element reqElem =null;
		try
		{
			TrackingContext.setTrackingContext(reqJson);
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			
			userctx = UserContext.getUserContextForSession(reqHdrJson);
			
			opConfig = CruiseConfig.getOperationConfig("reprice");
			reqElem = createSIRequest(reqHdrJson, reqBodyJson, userctx, opConfig);
		}
		catch(Exception e)
		{
			logger.error("Exception during request processing", e);
			throw new RequestProcessingException(e);
		}
		try
		{
			Element resElem = null;
	        //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), CruiseConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
	        if (resElem == null) {
	        	throw new Exception("Null response received from SI");
	        }
	        
	        JSONObject resBodyJson = new JSONObject();
	        JSONArray cruiseRepriceDetailsJsonArr = new JSONArray();
	        Element[] otaCategoryAvailWrapperElems = XMLUtils.getElementsAtXPath(resElem, "./cru:ResponseBody/cru1:OTA_CruisePriceBookingRSWrapper");
	        for(Element otaCategoryAvailWrapperElem : otaCategoryAvailWrapperElems)
	        {
	        	//IF SI gives an error
	        	Element[] errorListElems = XMLUtils.getElementsAtXPath(otaCategoryAvailWrapperElem, "./ota:OTA_CruisePriceBookingRS/ota:Errors/ota:Error");
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
	        	
	        	getSupplierResponseJSONV2(otaCategoryAvailWrapperElem,cruiseRepriceDetailsJsonArr,"./ota:OTA_CruisePriceBookingRS");
	        }
	        
	        resBodyJson.put("cruiseOptions", cruiseRepriceDetailsJsonArr);
	        
	        JSONObject resJson = new JSONObject();
	        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
	        resJson.put(JSON_PROP_RESBODY, resBodyJson);
	        
	    	Map<String,String> SI2BRMSSailingOptionMap = new HashMap<String,String>();
	    	JSONObject parentSupplTransJson = CruiseSupplierCommercials.getSupplierCommercialsV2(reqJson,resJson,SI2BRMSSailingOptionMap);
	    	if (BRMS_STATUS_TYPE_FAILURE.equals(parentSupplTransJson.getJSONObject(PRODUCT_CRUISE_SUPPLTRANSRS).getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", parentSupplTransJson.toString()));
			}
	    	
	        JSONObject breResClientJson = CruiseClientCommercials.getClientCommercialsV1(parentSupplTransJson);
	        if (BRMS_STATUS_TYPE_FAILURE.equals(breResClientJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", breResClientJson.toString()));
			}
	        
	        CruiseSearchProcessor.calculatePricesV6(reqJson, resJson, parentSupplTransJson, breResClientJson, SI2BRMSSailingOptionMap, true,userctx);
	        System.out.println(resJson.toString());
	        
	//        TaxEngine.getCompanyTaxes(reqJson, resJson);
	        
	        pushSuppFaresToRedisAndRemove(resJson);
	        
			return resJson.toString();
		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}

	//private static Element createSIRequest(JSONObject reqHdrJson, JSONObject reqBodyJson, UserContext userctx, OperationConfig opConfig) throws Exception {
	private static Element createSIRequest(JSONObject reqHdrJson, JSONObject reqBodyJson, UserContext userctx, ServiceConfig opConfig) throws Exception {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./cru1:OTA_CruisePriceBookingRQWrapper");
		reqBodyElem.removeChild(wrapperElem);


		CruiseSearchProcessor.createHeader(reqElem,reqHdrJson);
		
		Element suppWrapperElem = null;
		int seqItr =0;
		String prevSuppID = "";
		JSONArray cruisePriceDetailsArr = reqBodyJson.getJSONArray("cruiseOptions");
		
		for(int i=0;i<cruisePriceDetailsArr.length();i++)
		{
			JSONObject supplierBody = cruisePriceDetailsArr.getJSONObject(i);
			
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
//------------------------------------------------------------------------------------------------------------------------------------------------
			Element supplierCredentials = prodSupplier.toElement(ownerDoc);
			
//			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			//Request Body starts
				
			Element sequence = ownerDoc.createElementNS(Constants.NS_COM, "Sequence");
			sequence.setTextContent(String.valueOf(seqItr));
			
			Element credentialsElem = XMLUtils.getFirstElementAtXPath(supplierCredentials, "./com:Credentials");
			supplierCredentials.insertBefore(sequence, credentialsElem);
			
			suppCredsListElem.appendChild(supplierCredentials);
			
//------------------------------------------------------------------------------------------------------------------------------------------------
			Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./cru1:SupplierID");
			suppIDElem.setTextContent(suppID);
			
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./cru1:Sequence");
			sequenceElem.setTextContent(String.valueOf(seqItr));
			
			Element otaCategoryAvail = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_CruisePriceBookingRQ");
			CruisePriceProcessor.createPOS(ownerDoc, otaCategoryAvail);
			
			Element guestCountsElem = XMLUtils.getFirstElementAtXPath(otaCategoryAvail, "./ota:GuestCounts");
			
			JSONArray guestJsonArr = supplierBody.getJSONArray("Guests");
			
			int adt=0,inf=0;
			for(int k=0;k<guestJsonArr.length();k++)
			{
				JSONObject guestJson = guestJsonArr.getJSONObject(k);
				int age = guestJson.getInt("age");
				if(age>=18)
				{
					adt++;
				}else {
					inf++;
				}
			}
			{
				Element guestCountElem = ownerDoc.createElementNS(Constants.NS_OTA, "GuestCount");
				guestCountElem.setAttribute("Code", "10");
				guestCountElem.setAttribute("Quantity", String.valueOf(adt));
				guestCountsElem.appendChild(guestCountElem);
			}
			{
				Element guestCountElem = ownerDoc.createElementNS(Constants.NS_OTA, "GuestCount");
				guestCountElem.setAttribute("Code", "08");
				guestCountElem.setAttribute("Quantity", String.valueOf(inf));
				guestCountsElem.appendChild(guestCountElem);
			}
			
//=--=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=SailingInfo creation Starts-=-=-=-==-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
			
			Element sailingInfoElem = XMLUtils.getFirstElementAtXPath(otaCategoryAvail, "./ota:SailingInfo");
			JSONObject sailingInfoJson = supplierBody.getJSONObject("sailingInfo");
			
			Element selectedSailingElem = XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:SelectedSailing");
			selectedSailingElem.setAttribute("VoyageID", sailingInfoJson.getJSONObject("selectedSailing").getString("voyageId"));
			
			Element cruiseLineElem = XMLUtils.getFirstElementAtXPath(selectedSailingElem, "./ota:CruiseLine");
			cruiseLineElem.setAttribute("VendorCodeContext", sailingInfoJson.getJSONObject("selectedSailing").getJSONObject("cruiseLine").getString("vendorCodeCotext"));
			
			Element currencyCodeElem = XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:Currency");
			currencyCodeElem.setAttribute("CurrencyCode", sailingInfoJson.getJSONObject("selectedSailing").getJSONObject("cruiseLine").getString("currencyCode"));
			
			Element selectedCategoryElem = XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:SelectedCategory");
			selectedCategoryElem.setAttribute("PricedCategoryCode", sailingInfoJson.getJSONObject("selectedCategory").getString("pricedCategoryCode"));
			selectedCategoryElem.setAttribute("FareCode", sailingInfoJson.getJSONObject("selectedCategory").getString("fareCode"));
			
			Element selectedCabin = XMLUtils.getFirstElementAtXPath(selectedCategoryElem, "./ota:SelectedCabin");
			selectedCabin.setAttribute("CabinNumber", sailingInfoJson.getJSONObject("selectedCategory").getJSONObject("selectedCabin").getString("CabinNumber"));
			selectedCabin.setAttribute("Status", sailingInfoJson.getJSONObject("selectedCategory").getJSONObject("selectedCabin").getString("Status"));
			selectedCabin.setAttribute("CabinCategoryStatusCode", sailingInfoJson.getJSONObject("selectedCategory").getJSONObject("selectedCabin").getString("cabinCategoryStatusCode"));
			selectedCabin.setAttribute("MaxOccupancy", sailingInfoJson.getJSONObject("selectedCategory").getJSONObject("selectedCabin").getString("maxOccupancy"));
			
			Element cruiseElem = XMLUtils.getFirstElementAtXPath(selectedCategoryElem, "./ota:TPA_Extensions/cru1:Cruise");
			cruiseElem.setAttribute("Tax", sailingInfoJson.getJSONObject("selectedCategory").getString("tax"));
			
			Element sailingElem = XMLUtils.getFirstElementAtXPath(cruiseElem, "./cru1:SailingDates/cru1:Sailing");
			sailingElem.setAttribute("SailingID", sailingInfoJson.getJSONObject("selectedCategory").getString("sailingID"));
			
//=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-Guest Details Creation=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-==-=-=-==-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
			
			Element guestDetailsElem = XMLUtils.getFirstElementAtXPath(otaCategoryAvail, "./ota:ReservationInfo/ota:GuestDetails");
			
			for(int j=0;j<guestJsonArr.length();j++)
			{
				JSONObject guestJson = guestJsonArr.getJSONObject(j);
				
				Element guestDetailElem = ownerDoc.createElementNS(NS_OTA, "GuestDetail");
				
				Element contactInfo = ownerDoc.createElementNS(NS_OTA, "ContactInfo");
				contactInfo.setAttribute("GuestRefNumber",guestJson.getString("guestRefNumber"));
				contactInfo.setAttribute("LoyaltyMembershipID",guestJson.getString("loyaltyMembershipId"));
				contactInfo.setAttribute("PersonBirthDate",guestJson.getString("personBirthDate"));
				contactInfo.setAttribute("Gender", guestJson.getString("gender"));
				contactInfo.setAttribute("Age", guestJson.getString("age"));
				
				Element personName = ownerDoc.createElementNS(NS_OTA, "PersonName");
				
				Element surName = ownerDoc.createElementNS(NS_OTA, "Surname");
				surName.setTextContent(guestJson.getJSONObject("guestName").getString("surName"));
				
				Element middleName = ownerDoc.createElementNS(NS_OTA, "MiddleName");
				middleName.setTextContent(guestJson.getJSONObject("guestName").getString("middleName"));
				
				Element givenName = ownerDoc.createElementNS(NS_OTA, "GivenName");
				givenName.setTextContent(guestJson.getJSONObject("guestName").getString("givenName"));
				
				Element namePrefix = ownerDoc.createElementNS(NS_OTA, "NamePrefix");
				namePrefix.setTextContent(guestJson.getJSONObject("guestName").getString("namePrefix"));
				
				personName.appendChild(surName);
				personName.insertBefore(middleName, surName);
				personName.insertBefore(givenName, middleName);
				personName.insertBefore(namePrefix, givenName);
				
				Element telephoneElem =	ownerDoc.createElementNS(NS_OTA, "Telephone");
				telephoneElem.setAttribute("CountryAccessCode", guestJson.getJSONObject("guestName").getJSONObject("Telephone").getString("CountryAccessCode"));
				telephoneElem.setAttribute("PhoneNumber", guestJson.getJSONObject("guestName").getJSONObject("Telephone").getString("PhoneNumber"));
				
				contactInfo.appendChild(telephoneElem);
				contactInfo.insertBefore(personName, telephoneElem);
				
				Element travelDocument = ownerDoc.createElementNS(NS_OTA, "TravelDocument");
				travelDocument.setAttribute("DocIssueCountry",guestJson.getJSONObject("guestName").getJSONObject("TravelDocument").getString("DocIssueCountry"));
				travelDocument.setAttribute("DocIssueStateProv",guestJson.getJSONObject("guestName").getJSONObject("TravelDocument").getString("DocIssueStateProv"));
				
				Element selectedDining = ownerDoc.createElementNS(NS_OTA, "SelectedDining");
				selectedDining.setAttribute("Sitting", guestJson.getJSONObject("guestName").getJSONObject("SelectedDining").getString("Sitting"));
				selectedDining.setAttribute("Status", guestJson.getJSONObject("guestName").getJSONObject("SelectedDining").getString("Status"));
				
				guestDetailElem.appendChild(selectedDining);
				guestDetailElem.insertBefore(travelDocument, selectedDining);
				guestDetailElem.insertBefore(contactInfo, travelDocument);
				
				guestDetailsElem.appendChild(guestDetailElem);
			}
//-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=Payment Options Creations-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=			
			Element paymentOptionsElem = XMLUtils.getFirstElementAtXPath(otaCategoryAvail, "./ota:ReservationInfo/ota:PaymentOptions");
			
			Element paymentOption =	ownerDoc.createElementNS(NS_OTA,"PaymentOption");
			paymentOption.setAttribute("SplitPaymentInd", supplierBody.getJSONObject("PaymentOptions").getString("SplitPaymentInd"));
			
			Element paymentAmount = ownerDoc.createElementNS(NS_OTA, "PaymentAmount");
			paymentAmount.setAttribute("Amount", supplierBody.getJSONObject("PaymentOptions").getString("PaymentAmount"));
			
			paymentOption.appendChild(paymentAmount);
			paymentOptionsElem.appendChild(paymentOption);
		}
		return reqElem;
	}
	
	private static void pushSuppFaresToRedisAndRemove(JSONObject resJson) {
		JSONObject resHeaderJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();
		
		JSONArray cruiseOptionsJsonArr = resBodyJson.optJSONArray("cruiseOptions");
		
		if (cruiseOptionsJsonArr == null) {
			// TODO: This should never happen. Log a warning message here.
			return;
		}
		for(int j=0;j<cruiseOptionsJsonArr.length();j++)
		{
			JSONObject redisJson = new JSONObject();
			
			JSONObject cruiseOptionsJson = cruiseOptionsJsonArr.getJSONObject(j);
//			JSONArray sailingOptionsJsonArr = cruiseOptionsJson.optJSONArray("sailingOption");//will always be Object in this array
			JSONObject categoryJson = cruiseOptionsJsonArr.getJSONObject(0).getJSONArray("category").getJSONObject(0);
	//		JSONArray bookRefsJsonArr = resBodyJson.optJSONArray(JSON_PROP_BOOKREFS);
	//		resBodyJson.remove(JSON_PROP_BOOKREFS);
			
			/*JSONObject suppPriceInfoJson = cruiseOptionsJson.optJSONObject("BookingPayment").getJSONObject("suppPaymentSchedule");
			redisJson.put("paymentSchedule", suppPriceInfoJson);
			
			if (sailingOptionsJsonArr == null) {
				// TODO: This should never happen. Log a warning message here.
				return;
			}
			
			for (int i=0; i < sailingOptionsJsonArr.length(); i++) {//will iterate once
				JSONObject sailingOptionJson = sailingOptionsJsonArr.getJSONObject(i);
//				sailingOptionJson.getJSONObject("BookingPayment").remove("PaymentSchedule");
				
				JSONArray categoryJsonArr =	sailingOptionJson.getJSONArray("Category");
				
				for(int k=0;k<categoryJsonArr.length();k++) //will iterate once
				{
					JSONObject categoryJson = categoryJsonArr.getJSONObject(k);
					
					redisJson.put("SuppPassengerPrices", categoryJson.getJSONArray("SuppPassengerPrices"));
					redisJson.put("PassengerPrices", categoryJson.getJSONArray("PassengerPrices"));
				}
				
				if (suppPriceInfoJson == null) {
					// TODO: This should never happen. Log a warning message here.
					continue;
				}
				
			}*/
			JSONObject pricingInfoJson = categoryJson.getJSONObject("pricingInfo");
//			cruiseOptionsJson.remove("pricingInfo");
			redisJson.put("pricingInfo",pricingInfoJson);
			
			JSONObject suppPricingInfoJson = categoryJson.getJSONObject("suppPricingInfo");
			categoryJson.remove("suppPricingInfo");
			redisJson.put("suppPricingInfo", suppPricingInfoJson);
			
			
			redisJson.put("sailingInfo", cruiseOptionsJsonArr.getJSONObject(0).getJSONObject("selectedSailing"));
			
			reprcSuppFaresMap.put(getRedisKeyForSailingOption(cruiseOptionsJson), redisJson.toString());
		}
		String redisKey = resHeaderJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT_CRUISE);
		try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
		redisConn.hmset(redisKey, reprcSuppFaresMap);
		redisConn.pexpire(redisKey, (long) (CruiseConfig.getRedisTTLMinutes() * 60 * 1000));
		}
		//RedisConfig.releaseRedisConnectionToPool(redisConn);
	}
	
	static String getRedisKeyForSailingOption(JSONObject sailingOptionJson) {
		
		String suppID;String voyageID;String itineraryID;String pricedCategoryCode=new String();String categoryName=new String();String cabinNo=new String();
		
		suppID = sailingOptionJson.optString("supplierRef");
		
		voyageID = sailingOptionJson.optJSONObject("selectedSailing").optString("voyageID");
		itineraryID = sailingOptionJson.optString("itineraryID");
		
		JSONArray categoryJsonArr = sailingOptionJson.optJSONArray("category");
		
		for(int i=0;i<categoryJsonArr.length();i++)//will always iterate once
		{
			JSONObject categoryJson =	categoryJsonArr.getJSONObject(i);
			JSONArray cabinOptionsJsonArr =	categoryJson.getJSONArray("cabinOptions");
			
			pricedCategoryCode = categoryJson.optString("pricedCategoryCode");
			
			for(int j=0;j<cabinOptionsJsonArr.length();j++)//will iterate once
			{
				JSONObject cabinOptionsJson = cabinOptionsJsonArr.getJSONObject(j);
				
				cabinNo = cabinOptionsJson.getString("cabinNumber");
			}
			
//			categoryName = categoryJson.optString("CategoryName");
		}
		System.out.println(String.format("%s%c%s%c%s%c%s%c%s", suppID,KEYSEPARATOR,voyageID,KEYSEPARATOR,itineraryID,KEYSEPARATOR,pricedCategoryCode,KEYSEPARATOR,cabinNo));
		return String.format("%s%c%s%c%s%c%s%c%s", suppID,KEYSEPARATOR,voyageID,KEYSEPARATOR,itineraryID,KEYSEPARATOR,pricedCategoryCode,KEYSEPARATOR,cabinNo);
	}
	
	public static void getSupplierResponseJSON(Element otaCategoryAvailWrapperElem,JSONArray cruiseRepriceDetailsJsonArr,String otaName) {
		
		JSONObject repriceJson = new JSONObject();
		
		Element otaCruiseCategoryAvailRs = XMLUtils.getFirstElementAtXPath(otaCategoryAvailWrapperElem, otaName);
		String suppID = XMLUtils.getValueAtXPath(otaCategoryAvailWrapperElem, "./cru1:SupplierID");
		repriceJson.put("supplierRef", suppID);
		
		String errorMsg = XMLUtils.getValueAtXPath(otaCategoryAvailWrapperElem, "./cru1:ErrorList/com:Error/com:ErrorMsg");
		if(errorMsg!=null)
		repriceJson.put("errorMsg", errorMsg);
		
		Element[] reservationIDElems =	XMLUtils.getElementsAtXPath(otaCruiseCategoryAvailRs, "./ota:ReservationID");
		if(reservationIDElems!=null)
		{
			JSONArray reservationIDJsonArr = getReservationJson(reservationIDElems);
			repriceJson.put("reservationID", reservationIDJsonArr);
		}
		
		Element sailingInfoElem = XMLUtils.getFirstElementAtXPath(otaCruiseCategoryAvailRs, "./ota:SailingInfo");
		if(sailingInfoElem!=null)
		{
			JSONObject sailingOptionJson = getsailingOptionJSON(sailingInfoElem,suppID);
			JSONArray sailingArr = new JSONArray();
			sailingArr.put(sailingOptionJson);
			repriceJson.put("sailingOption", sailingArr);
		}
		
		Element bookingPaymentElem = XMLUtils.getFirstElementAtXPath(otaCruiseCategoryAvailRs, "./ota:BookingPayment");
		if(bookingPaymentElem!=null)
		{
			JSONObject bookingPaymentJson = getBookingPaymentJson(bookingPaymentElem);
			repriceJson.put("bookingPayment", bookingPaymentJson);
		}
		
		if(sailingInfoElem!=null && bookingPaymentElem!=null)
		addPriceInfos(repriceJson,bookingPaymentElem);
		
		cruiseRepriceDetailsJsonArr.put(repriceJson);
	}
	
	public static void getSupplierResponseJSONV2(Element otaCategoryAvailWrapperElem,JSONArray cruiseRepriceDetailsJsonArr,String otaName) {
		
		JSONObject repriceJson = new JSONObject();
		
		Element otaCruiseCategoryAvailRs = XMLUtils.getFirstElementAtXPath(otaCategoryAvailWrapperElem, otaName);
		String suppID = XMLUtils.getValueAtXPath(otaCategoryAvailWrapperElem, "./cru1:SupplierID");
		repriceJson.put("supplierRef", suppID);
		
		String errorMsg = XMLUtils.getValueAtXPath(otaCategoryAvailWrapperElem, "./cru1:ErrorList/com:Error/com:ErrorMsg");
		if(errorMsg!=null)
		repriceJson.put("errorMsg", errorMsg);
		
		Element[] reservationIDElems =	XMLUtils.getElementsAtXPath(otaCruiseCategoryAvailRs, "./ota:ReservationID");
		if(reservationIDElems!=null)
		{
			JSONArray reservationIDJsonArr = getReservationJson(reservationIDElems);
			repriceJson.put("reservationID", reservationIDJsonArr);
		}
		
		Element sailingInfoElem = XMLUtils.getFirstElementAtXPath(otaCruiseCategoryAvailRs, "./ota:SailingInfo");
		if(sailingInfoElem!=null)
		{
			Random random = new Random();
			 
			repriceJson.put("supplierRef", suppID);
			 
			Element selectedSailingElem = XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:SelectedSailing");
			if(selectedSailingElem!=null)
				repriceJson.put("selectedSailing", getSelectedSailingJSON(selectedSailingElem));
			 
			Element currencyElem =	XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:Currency");
			if(currencyElem!=null)
				repriceJson.put("currencyCode", XMLUtils.getValueAtXPath(currencyElem, "./@CurrencyCode"));
			 
			repriceJson.put("itineraryID", "");
			 
			Element selectedCategoryElem = XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:SelectedCategory");
			 
			if(selectedCategoryElem!=null)
				repriceJson.put("category", getCategoryJsonArr(selectedCategoryElem));
			 
			repriceJson.put("cruiseNumber",String.valueOf(random.nextInt(900) + 100 ));
		}
		
		Element bookingPaymentElem = XMLUtils.getFirstElementAtXPath(otaCruiseCategoryAvailRs, "./ota:BookingPayment");
		if(bookingPaymentElem!=null)
		{
			JSONObject bookingPaymentJson = getBookingPaymentJson(bookingPaymentElem);
			repriceJson.put("bookingPayment", bookingPaymentJson);
		}
		
		if(sailingInfoElem!=null && bookingPaymentElem!=null)
		addPriceInfos(repriceJson,bookingPaymentElem);
		
		cruiseRepriceDetailsJsonArr.put(repriceJson);
	}
	
	private static JSONArray getReservationJson(Element[] reservationIDElems)
 	{
		JSONArray reservationJsonArr = new JSONArray();
		for(Element reservationIDElem : reservationIDElems)
		{
			JSONObject reservationIDJson = new JSONObject();
			
			reservationIDJson.put("statusCode", XMLUtils.getValueAtXPath(reservationIDElem, "./@StatusCode"));
			reservationIDJson.put("bookedDate", XMLUtils.getValueAtXPath(reservationIDElem, "./@BookedDate"));
			reservationIDJson.put("type", XMLUtils.getValueAtXPath(reservationIDElem, "./@Type"));
			reservationIDJson.put("id", XMLUtils.getValueAtXPath(reservationIDElem, "./@ID"));
			reservationIDJson.put("id_Context", XMLUtils.getValueAtXPath(reservationIDElem, "./@ID_Context"));
			reservationIDJson.put("companyName", XMLUtils.getValueAtXPath(reservationIDElem, "./ota:CompanyName"));
			
			reservationJsonArr.put(reservationIDJson);
		}
		
		return reservationJsonArr;
 	}
	
	private static void addPriceInfos(JSONObject repriceJson, Element bookingPaymentElem)
	 {
		JSONObject categoryJson = repriceJson.getJSONArray("category").getJSONObject(0);
		
		JSONArray priceInfosArr = new JSONArray();
		Element[] guestPriceElems = XMLUtils.getElementsAtXPath(bookingPaymentElem, "./ota:GuestPrices/ota:GuestPrice");
		
		for(Element guestPriceElem : guestPriceElems)
		{
			Element[] priceInfosElems =	XMLUtils.getElementsAtXPath(guestPriceElem, "./ota:PriceInfos/ota:PriceInfo");
			
			priceInfosArr.put(CruiseSearchProcessor.getPassengerPrices(priceInfosElems,false,0.0));
			
			JSONArray passengerPriceJsonArr = categoryJson.optJSONArray("passengerPrices");
			if(passengerPriceJsonArr==null)
			{
				categoryJson.put("passengerPrices", CruiseSearchProcessor.getPassengerPrices(priceInfosElems,false,0.0));
			}
			else
			{
				
				JSONArray passPriceJsonArr = CruiseSearchProcessor.getPassengerPrices(priceInfosElems,false,0.0);
				for(int i=0;i<passPriceJsonArr.length();i++)
				{
					passengerPriceJsonArr.put(passPriceJsonArr.getJSONObject(i));
				}
			}
		}
	 }
	
	private static JSONObject getBookingPaymentJson(Element bookingPaymentElem)
	 {
		JSONObject bookingPaymentJson = new JSONObject();
		
		Element bookingPricesElem =	XMLUtils.getFirstElementAtXPath(bookingPaymentElem, "./ota:BookingPrices");
		if(bookingPricesElem!=null)
		{
			JSONArray bookingPriceArr = getBookingPriceJsonArr(bookingPricesElem);
			bookingPaymentJson.put("bookingPrice", bookingPriceArr);
		}
		
		Element paymentScheduleElem = XMLUtils.getFirstElementAtXPath(bookingPaymentElem, "./ota:PaymentSchedule");
		if(paymentScheduleElem!=null)
		{
			JSONArray paymentJsonArr = getPaymentJsonArr(paymentScheduleElem);
			JSONObject paymentJson = new JSONObject();
			
			paymentJson.put("payment", paymentJsonArr);
			bookingPaymentJson.put("paymentSchedule", paymentJson);
		}
		
		Element guestPricesElems = XMLUtils.getFirstElementAtXPath(bookingPaymentElem, "./ota:GuestPrices");
		if(guestPricesElems!=null)
		{
			JSONArray guestPriceJsonArr = getGuestPriceJsonArr(guestPricesElems);
			bookingPaymentJson.put("guestPrice", guestPriceJsonArr);
			
		}
		
		return bookingPaymentJson;
	 }
	
	private static JSONArray getGuestPriceJsonArr(Element paymentScheduleElem)
	{
		JSONArray guestPriceJsonArr = new JSONArray();
		Element[] guestPriceElems =	XMLUtils.getElementsAtXPath(paymentScheduleElem, "");
		
		for(Element guestPriceElem : guestPriceElems)
		{
			JSONObject guestPriceJson = new JSONObject();
			
			guestPriceJson.put("guestRefNumber", XMLUtils.getValueAtXPath(guestPriceElem, "./@GuestRefNumber"));
			
			Element priceInfosElem = XMLUtils.getFirstElementAtXPath(guestPriceElem, "./ota:PriceInfos");
			if(priceInfosElem!=null)
			{
				JSONArray priceInfosJsonArr = getPriceInfosJsonArr(guestPriceElem);
				guestPriceJson.put("priceInfo", priceInfosJsonArr);
			}
			
			guestPriceJsonArr.put(guestPriceJson);
		}
		return guestPriceJsonArr;
	}
	private static JSONArray getPriceInfosJsonArr(Element paymentScheduleElem)
	{
		JSONArray priceInfosJsonArr = new JSONArray();
		Element[] priceInfoElems =	XMLUtils.getElementsAtXPath(paymentScheduleElem, "./ota:PriceInfo");
		
		for(Element priceInfoElem : priceInfoElems)
		{
			JSONObject priceInfoJson = new JSONObject();
			
			priceInfoJson.put("priceTypeCode",XMLUtils.getValueAtXPath(priceInfoElem, "./@PriceTypeCode"));
			priceInfoJson.put("amount",XMLUtils.getValueAtXPath(priceInfoElem, "./@Amount"));
			priceInfoJson.put("codeDetail",XMLUtils.getValueAtXPath(priceInfoElem, "./@CodeDetail"));
			priceInfoJson.put("restrictedIndicator",XMLUtils.getValueAtXPath(priceInfoElem, "./@RestrictedIndicator"));
			
			priceInfosJsonArr.put(priceInfoJson);
		}
		
		return priceInfosJsonArr;
	}
	
	private static JSONArray getPaymentJsonArr(Element paymentScheduleElem)
	{
		JSONArray paymentJsonArr = new JSONArray();
		Element[] paymentElems = XMLUtils.getElementsAtXPath(paymentScheduleElem, "./ota:Payment");
		
		for(Element paymentElem : paymentElems)
		{
			JSONObject paymentJson = new JSONObject();
			
			paymentJson.put("paymentNumber", XMLUtils.getValueAtXPath(paymentElem, "./@PaymentNumber"));
			paymentJson.put("dueDate", XMLUtils.getValueAtXPath(paymentElem, "./@DueDate"));
			paymentJson.put("currencyCode", XMLUtils.getValueAtXPath(paymentElem, "./@CurrencyCode"));
			paymentJson.put("amount", XMLUtils.getValueAtXPath(paymentElem, "./@Amount"));
			
			paymentJsonArr.put(paymentJson);
		}
		
		return paymentJsonArr;
	}
	
	private static JSONArray getBookingPriceJsonArr(Element bookingPricesElem)
	{
		JSONArray bookingPriceArr = new JSONArray();
		
		Element[] bookingPriceElems = XMLUtils.getElementsAtXPath(bookingPricesElem, "./ota:BookingPrice");
		
		for(Element bookingPriceElem : bookingPriceElems)
		{
			JSONObject bookingPriceJson = new JSONObject();
			
			bookingPriceJson.put("priceTypeCode", XMLUtils.getValueAtXPath(bookingPriceElem, "./@PriceTypeCode"));
			bookingPriceJson.put("amount", XMLUtils.getValueAtXPath(bookingPriceElem, "./@Amount"));
			bookingPriceJson.put("codeDetail", XMLUtils.getValueAtXPath(bookingPriceElem, "./@CodeDetail"));
			
			bookingPriceArr.put(bookingPriceJson);
		}
		
		return bookingPriceArr;
		
	}
	
	 private static JSONObject getsailingOptionJSON(Element sailingInfoElem,String suppID)
	 {
//		 XMLUtils.getValueAtXPath((Element)sailingInfoElem.getParentNode().getParentNode(), "");
		 
		 Random random = new Random();
		 JSONObject sailingOptionJSON = new JSONObject();
		 
		 sailingOptionJSON.put("supplierRef", suppID);
		 
		 Element selectedSailingElem = XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:SelectedSailing");
		 if(selectedSailingElem!=null)
		 sailingOptionJSON.put("selectedSailing", getSelectedSailingJSON(selectedSailingElem));
		 
		 Element currencyElem =	XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:Currency");
		 if(currencyElem!=null)
		 sailingOptionJSON.put("currencyCode", XMLUtils.getValueAtXPath(currencyElem, "./@CurrencyCode"));
		 
		 sailingOptionJSON.put("itineraryID", "");
		 
		 Element selectedCategoryElem = XMLUtils.getFirstElementAtXPath(sailingInfoElem, "./ota:SelectedCategory");
		 
		 if(selectedCategoryElem!=null)
		 sailingOptionJSON.put("category", getCategoryJsonArr(selectedCategoryElem));
		 
		 sailingOptionJSON.put("cruiseNumber",String.valueOf(random.nextInt(900) + 100 ));
		 
		 return sailingOptionJSON;
	}
	
	private static JSONArray getCategoryJsonArr(Element selectedCategoryElem)
    {
		JSONArray categoryJsonArr = new JSONArray(); // it will always have one category since we are inside reprice and the category is already chosen in the previous operation.
		
//		for(Element selectedCategoryElem : selectedCategoryElems)
		{
			JSONObject categoryJson = new JSONObject();
			
			categoryJson.put("pricedCategoryCode", XMLUtils.getValueAtXPath(selectedCategoryElem, "./@PricedCategoryCode"));
			categoryJson.put("fareCode", XMLUtils.getValueAtXPath(selectedCategoryElem, "./@FareCode"));
			categoryJson.put("waitlistIndicator", XMLUtils.getValueAtXPath(selectedCategoryElem, "./@WaitlistIndicator"));
			categoryJson.put("categoryName", XMLUtils.getValueAtXPath(selectedCategoryElem, "./@CategoryName"));
			
			Element[] selectedCabinElems = XMLUtils.getElementsAtXPath(selectedCategoryElem, "./ota:SelectedCabin");
			if(selectedCabinElems!=null)
			{
				categoryJson.put("cabinOptions", getCabinOptionsJsonArr(selectedCabinElems));
			}
			
			categoryJsonArr.put(categoryJson);
		}
		
		return categoryJsonArr;
    }
	 
	private static JSONArray getCabinOptionsJsonArr(Element[] selectedCabinElems)
    {
		JSONArray cabinOptionsJsonArr = new JSONArray();
		
		for(Element selectedCabinElem : selectedCabinElems)
		{
			JSONObject cabinOptionJson = new JSONObject();
			
			cabinOptionJson.put("status", XMLUtils.getValueAtXPath(selectedCabinElem, "./@Status"));
			cabinOptionJson.put("cabinCategoryStatusCode", XMLUtils.getValueAtXPath(selectedCabinElem, "./@CabinCategoryStatusCode"));
			cabinOptionJson.put("cabinCategoryCode", XMLUtils.getValueAtXPath(selectedCabinElem, "./@CabinCategoryCode"));
			cabinOptionJson.put("cabinRanking", XMLUtils.getValueAtXPath(selectedCabinElem, "./@CabinRanking"));
			cabinOptionJson.put("cabinNumber", XMLUtils.getValueAtXPath(selectedCabinElem, "./@CabinNumber"));
			cabinOptionJson.put("maxOccupancy", XMLUtils.getValueAtXPath(selectedCabinElem, "./@MaxOccupancy"));
			cabinOptionJson.put("deckNumber", XMLUtils.getValueAtXPath(selectedCabinElem, "./@DeckNumber"));
			cabinOptionJson.put("deckName", XMLUtils.getValueAtXPath(selectedCabinElem, "./@DeckName"));
			cabinOptionJson.put("dimensionInfo", XMLUtils.getValueAtXPath(selectedCabinElem, "./ota:MeasurementInfo/@DimensionInfo"));
			cabinOptionJson.put("remark", XMLUtils.getValueAtXPath(selectedCabinElem, "./ota:Remark"));
			
			cabinOptionJson.put("cabinAttributeCode", XMLUtils.getValueAtXPath(selectedCabinElem, "./ota:CabinAttributes/ota:CabinAttribute/@CabinAttributeCode"));
			cabinOptionsJsonArr.put(cabinOptionJson);
			
		}
		
		return cabinOptionsJsonArr;
    }
	
    private static JSONObject getSelectedSailingJSON(Element selectedSailingElem)
    {
    	JSONObject selectedSailingJSON = new JSONObject();
    	
    	selectedSailingJSON.put("voyageID", XMLUtils.getValueAtXPath(selectedSailingElem, "./@VoyageID"));
    	selectedSailingJSON.put("status", XMLUtils.getValueAtXPath(selectedSailingElem, "./Status"));
    	selectedSailingJSON.put("portsOfCallQuantity", XMLUtils.getValueAtXPath(selectedSailingElem, "./@PortsOfCallQuantity"));
    	selectedSailingJSON.put("start", XMLUtils.getValueAtXPath(selectedSailingElem, "./@Start"));
    	selectedSailingJSON.put("duration", XMLUtils.getValueAtXPath(selectedSailingElem, "./@Duration"));
    	selectedSailingJSON.put("end", XMLUtils.getValueAtXPath(selectedSailingElem, "./@End"));
    	
    	Element cruiseLineElem = XMLUtils.getFirstElementAtXPath(selectedSailingElem, "./ota:CruiseLine");
    	if(cruiseLineElem!=null)
    	selectedSailingJSON.put("cruiseLine", getCruiseLineJSON(cruiseLineElem));
    	
    	Element regionElem = XMLUtils.getFirstElementAtXPath(selectedSailingElem, "./ota:Region");
    	if(regionElem!=null)
    	selectedSailingJSON.put("region", getRegionJSON(regionElem));
    	
    	Element departurePortElem = XMLUtils.getFirstElementAtXPath(selectedSailingElem, "./ota:DeparturePort");
    	if(departurePortElem!=null)
    	selectedSailingJSON.put("departurePort", getDeparturePortJSON(departurePortElem));
    	
    	Element arrivalPortElem = XMLUtils.getFirstElementAtXPath(selectedSailingElem, "./ota:ArrivalPort");
    	if(arrivalPortElem!=null)
    	selectedSailingJSON.put("arrivalPort", getArrivalPortJSON(arrivalPortElem));
    	
    	return selectedSailingJSON;
    }
 
	private static JSONObject getCruiseLineJSON(Element cruiseLineElem)
    {
    	JSONObject cruiseLineJSON = new JSONObject();
    	
		cruiseLineJSON.put("vendorCode", XMLUtils.getValueAtXPath(cruiseLineElem, "./@VendorCode"));
		cruiseLineJSON.put("shipCode", XMLUtils.getValueAtXPath(cruiseLineElem, "./@ShipCode"));
		cruiseLineJSON.put("shipName", XMLUtils.getValueAtXPath(cruiseLineElem, "./@ShipName"));
		cruiseLineJSON.put("vendorName", XMLUtils.getValueAtXPath(cruiseLineElem, "./@VendorName"));
		cruiseLineJSON.put("vendorCodeContext", XMLUtils.getValueAtXPath(cruiseLineElem, "./@VendorCodeContext"));
    	
    	return cruiseLineJSON;
    }
	
	private static JSONObject getRegionJSON(Element regionElem)
    {
    	JSONObject regionJSON = new JSONObject();
    	
		regionJSON.put("regionName", XMLUtils.getValueAtXPath(regionElem, "./@RegionName"));
		regionJSON.put("regionCode", XMLUtils.getValueAtXPath(regionElem, "./@RegionCode"));
		regionJSON.put("subRegionName", XMLUtils.getValueAtXPath(regionElem, "./@SubRegionName"));
    	
    	return regionJSON;
    }
	    
    private static JSONObject getDeparturePortJSON(Element departurePortElem)
    {
    	JSONObject departurePortJSON = new JSONObject();
    	
		departurePortJSON.put("embarkationTime", XMLUtils.getValueAtXPath(departurePortElem, "./@EmbarkationTime"));
		departurePortJSON.put("locationCode", XMLUtils.getValueAtXPath(departurePortElem, "./@LocationCode"));
		departurePortJSON.put("codeContext", XMLUtils.getValueAtXPath(departurePortElem, "./ota:DeparturePort"));
    	
    	return departurePortJSON;
    }
    private static JSONObject getArrivalPortJSON(Element arrivalPortElem)
    {
    	JSONObject arrivalPortJSON = new JSONObject();
    	
		arrivalPortJSON.put("debarkationDateTime", XMLUtils.getValueAtXPath(arrivalPortElem, "./@DebarkationDateTime"));
		arrivalPortJSON.put("locationCode", XMLUtils.getValueAtXPath(arrivalPortElem, "./@LocationCode"));
		arrivalPortJSON.put("codeContext", XMLUtils.getValueAtXPath(arrivalPortElem, "/ota:ArrivalPort"));
    	
    	return arrivalPortJSON;
    }
}
