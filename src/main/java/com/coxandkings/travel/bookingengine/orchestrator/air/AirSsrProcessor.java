package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;


public class AirSsrProcessor implements AirConstants {
	
	private static final Logger logger = LogManager.getLogger(AirSsrProcessor.class);
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		String clientCcy=null;
		
		try {
			TrackingContext.setTrackingContext(reqJson);
			opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		    clientCcy=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			validateRequestParameters(reqJson);
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson);
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}

		try {
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AirConfig.getHttpHeaders(), reqElem);
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			// Response XML To JSON

			JSONObject resBodyJson = new JSONObject();
			JSONArray pricedItinsJsonArr = new JSONArray();

			Element[] resWrapperElems = XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirGetSSRSWrapper");
			for (Element resWrapperElem : resWrapperElems) {
				JSONObject priceItinJson = new JSONObject();
				priceItinJson = getSupplierPriceItin(priceItinJson, resWrapperElem,clientCcy);
				pricedItinsJsonArr.put(priceItinJson);
			}
			resBodyJson.put(JSON_PROP_PRICEDITIN, pricedItinsJsonArr);

			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			
			pushSsrCallIndToRedis(resJson);
			
			return resJson.toString();
		} 
		catch (Exception x) {
			logger.error("Exception received while processing", x);
            throw new InternalProcessingException(x);
		}
	}

	private static void validateRequestParameters(JSONObject reqJson) throws JSONException, ValidationException {
		AirRequestValidator.validateTripType(reqJson);
		AirRequestValidator.validatePassengerCounts(reqJson);
	}

	private static void pushSsrCallIndToRedis(JSONObject resJson) {
		JSONArray priceItinJsonArr=resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRICEDITIN);
		Map<String, String> reprcPriceItinSSRsMap = new HashMap<String, String>();
		String sessionID = resJson.getJSONObject(JSON_PROP_RESHEADER).optString(JSON_PROP_SESSIONID);
		for(int i=0;i<priceItinJsonArr.length();i++) {
			//String pricedItinRedisKey =AirRepriceProcessor.getRedisKeyForPricedItinerary(priceItinJson);
			
			reprcPriceItinSSRsMap.put("AIR|GetSSR", "true");
		}
		String redisKey=String.format("%s|AIR|GetSSR",sessionID);
		try ( Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
			redisConn.hmset(redisKey, reprcPriceItinSSRsMap);
			redisConn.pexpire(redisKey, (long) (AirConfig.getRedisTTLMinutes() * 60 * 1000));
			}
	}

	private static JSONObject getSupplierPriceItin(JSONObject priceItinJson, Element resWrapperElem, String clientCcy) {
		// TODO Auto-generated method stub
		Element supplierRefElem= XMLUtils.getFirstElementAtXPath(resWrapperElem, "./air:SupplierID");
		priceItinJson.put(JSON_PROP_SUPPREF, supplierRefElem.getTextContent());
		
		Element airItinElem=XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_AirGetSSRS/ota:AirItinerary");
		
		JSONObject airItinJson=new JSONObject();
		airItinJson = getAirItinJson(airItinElem,airItinJson);
		priceItinJson.put(JSON_PROP_AIRITINERARY, airItinJson);
		
		//SSR info
		JSONArray ssrJsonArr=new JSONArray();
		//Element specialRq=XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_AirGetSSRS/ota:TravelerInfo/ota:SpecialReqDetails");
		Element specialRqElems[]= XMLUtils.getElementsAtXPath(resWrapperElem, "./ota:OTA_AirGetSSRS/ota:TravelerInfo/ota:SpecialReqDetails");
		
		for (Element specialRqElem : specialRqElems) 
		{
		Element ssrElems[]= XMLUtils.getElementsAtXPath(specialRqElem, "./ota:SpecialServiceRequests/ota:SpecialServiceRequest");
        for (Element ssrElem : ssrElems) {
			JSONObject ssrJson=new JSONObject();
			//TODO:Temporary fix for removing seats from SSRS
			if(ssrElem.getAttribute("SSRCode").equals("SEAT")) {
				continue;
			}
			
			XMLUtils.getValueAtXPath(ssrElem, "./@SSRCode");
			
			/*ssrJson.put(JSON_PROP_SSRCODE,ssrElem.getAttribute("SSRCode"));
			ssrJson.put(JSON_PROP_NUMBER,ssrElem.getAttribute("Number"));
			ssrJson.put(JSON_PROP_SVCQTY,ssrElem.getAttribute("ServiceQuantity"));
			ssrJson.put(JSON_PROP_STATUS,ssrElem.getAttribute("Status"));
			ssrJson.put(JSON_PROP_TYPE,ssrElem.getAttribute("Type"));*/
			
			ssrJson.put(JSON_PROP_SSRCODE,XMLUtils.getValueAtXPath(ssrElem, "./@SSRCode"));
			ssrJson.put(JSON_PROP_NUMBER,XMLUtils.getValueAtXPath(ssrElem, "./@Number"));
			ssrJson.put(JSON_PROP_SVCQTY,XMLUtils.getValueAtXPath(ssrElem, "./@ServiceQuantity"));
			ssrJson.put(JSON_PROP_STATUS,XMLUtils.getValueAtXPath(ssrElem, "./@Status"));
			ssrJson.put(JSON_PROP_TYPE,XMLUtils.getValueAtXPath(ssrElem, "./@Type"));
			
			
			
			
			
			ssrJson.put(JSON_PROP_FLIGHREFNBR, XMLUtils.getValueAtXPath(specialRqElem, "./@FlightRefNumberRPHList"));
			/*
			if(ssrElem.getAttribute("FlightRefNumberRPHList")!=null)
			{
				ssrJson.put(JSON_PROP_FLIGHREFNBR, ssrElem.getAttribute("FlightRefNumberRPHList"));
			}*/
			
			Element flightLegElem=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:FlightLeg");
			
			ssrJson.put(JSON_PROP_FLIGHTNBR, XMLUtils.getValueAtXPath(flightLegElem, "./@FlightNumber"));
			
			/*if(flightLegElem.getAttribute("FlightNumber")!=null)
			{
				ssrJson.put(JSON_PROP_FLIGHTNBR, flightLegElem.getAttribute("FlightNumber"));
			}*/
			
			ssrJson.put(JSON_PROP_DATE, flightLegElem.getAttribute("Date"));
			
			Element departureElem=XMLUtils.getFirstElementAtXPath(flightLegElem, "./ota:DepartureAirport");
			Element arrivalElem=XMLUtils.getFirstElementAtXPath(flightLegElem, "./ota:ArrivalAirport");
			
			ssrJson.put(JSON_PROP_DESTLOC, XMLUtils.getValueAtXPath(arrivalElem, "./@LocationCode"));
			
		/*	if(arrivalElem!=null)
			{
				ssrJson.put(JSON_PROP_DESTLOC, arrivalElem.getAttribute("LocationCode"));
			}*/
			
			ssrJson.put(JSON_PROP_ORIGLOC, XMLUtils.getValueAtXPath(departureElem, "./@LocationCode"));
			
			/*if(departureElem!=null)
			{
				ssrJson.put(JSON_PROP_ORIGLOC, departureElem.getAttribute("LocationCode"));
			}
			*/
			Element airLineElem=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:Airline");
			
			ssrJson.put(JSON_PROP_AIRLINECODE, XMLUtils.getValueAtXPath(airLineElem, "./@Code"));
			ssrJson.put(JSON_PROP_COMPANYSHORTNAME,XMLUtils.getValueAtXPath(airLineElem, "./@CompanyShortName"));
			
			/*if(airLineElem!=null)
			{
				ssrJson.put(JSON_PROP_AIRLINECODE, airLineElem.getAttribute("Code"));
				ssrJson.put(JSON_PROP_COMPANYSHORTNAME, airLineElem.getAttribute("CompanyShortName"));
			}*/
			
			Element servicePrice=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:ServicePrice");
			Element taxesElem=XMLUtils.getFirstElementAtXPath(servicePrice, "./ota:Taxes");
			
			JSONObject servicePriceJson=new JSONObject();
			JSONObject taxesJson=new JSONObject();
			
			String amount=XMLUtils.getValueAtXPath(ssrElem, "./ota:Amount");
			
			if(Utils.isStringNullOrEmpty(amount)) {
				amount="0";
			}
			ssrJson.put(JSON_PROP_AMOUNT, amount);
			ssrJson.put(JSON_PROP_CCYCODE,XMLUtils.getValueAtXPath(ssrElem, "./ota:currencyCode"));
			
			servicePriceJson.put(JSON_PROP_BASEPRICE,XMLUtils.getValueAtXPath(servicePrice, "./ota:BasePrice/@Amount"));
			
			taxesJson.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(taxesElem, "./@Amount"));
			ssrJson.put(JSON_PROP_TAXES, taxesJson);
			ssrJson.put(JSON_PROP_SVCPRC, servicePriceJson);
			
		/*	if (servicePrice != null) {
				JSONObject servicePriceJson=new JSONObject();
				JSONObject taxesJson=new JSONObject();
				Element basePrice=XMLUtils.getFirstElementAtXPath(servicePrice, "./ota:BasePrice");
			
				//RedisRoEData.getRateOfExchange(XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:currencyCode").getTextContent(), clientCcy);
				BigDecimal amount=new BigDecimal( XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:Amount").getTextContent());
				ssrJson.put(JSON_PROP_AMOUNT, amount.toString());
				//ssrJson.put(JSON_PROP_AMOUNT, amount.multiply(RedisRoEData.getRateOfExchange(XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:currencyCode").getTextContent(), clientCcy)));
				//ssrJson.put(JSON_PROP_CCYCODE,clientCcy );
				//ssrJson.put(JSON_PROP_CCYCODE,XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:currencyCode"));
				ssrJson.put(JSON_PROP_CCYCODE,XMLUtils.getValueAtXPath(ssrElem, "./ota:currencyCode"));
				
				servicePriceJson.put(JSON_PROP_BASEPRICE, basePrice.getAttribute(XML_ATTR_AMOUNT));
				
				if (taxesElem != null) {
					 amount=new BigDecimal( taxesElem.getAttribute(XML_ATTR_AMOUNT));
					 taxesJson.put(JSON_PROP_AMOUNT, amount.toString());
					//taxesJson.put(JSON_PROP_AMOUNT, amount.multiply(RedisRoEData.getRateOfExchange(XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:currencyCode").getTextContent(), clientCcy)));
					ssrJson.put(JSON_PROP_TAXES, taxesJson);
				}
				
				ssrJson.put(JSON_PROP_SVCPRC, servicePriceJson);
			}*/
			
			//Element categoryCode=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:CategoryCode");
			
			ssrJson.put(JSON_PROP_CATCODE, XMLUtils.getValueAtXPath(ssrElem, "./ota:CategoryCode"));
		/*	
			if(categoryCode!=null)
			{
				ssrJson.put(JSON_PROP_CATCODE, categoryCode.getTextContent());
			}*/
			
			//Element textElem=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:Text");
			
		ssrJson.put(JSON_PROP_DESC, XMLUtils.getValueAtXPath(ssrElem, "./ota:Text"));
			
			/*if(textElem!=null)
			{
				ssrJson.put(JSON_PROP_DESC, textElem.getTextContent());
			}*/
			ssrJsonArr.put(ssrJson);
		}
		}
        priceItinJson.put(JSON_PROP_SPECIALSVCREQS, ssrJsonArr);
		return priceItinJson;
	}

	private static JSONObject getAirItinJson(Element airItinElem, JSONObject airItinJson) {
		// TODO Auto-generated method stub
		JSONArray odoJsonArray= new JSONArray();
		Element odosElem[]=XMLUtils.getElementsAtXPath(airItinElem, "./ota:OriginDestinationOptions/ota:OriginDestinationOption");
		 
		for (Element odoElem : odosElem) {
			JSONObject odoJson= new JSONObject();
			JSONArray flightSegArrJson=new JSONArray();
			Element flightSegElemArr[]=XMLUtils.getElementsAtXPath(odoElem, "./ota:FlightSegment");
			for (Element flightSegElem : flightSegElemArr) {
				JSONObject flightSegJson=new JSONObject();
				
				flightSegJson.put(JSON_PROP_FLIGHTNBR, flightSegElem.getAttribute("FlightNumber"));
				flightSegJson.put(JSON_PROP_DEPARTDATE, flightSegElem.getAttribute("DepartureDateTime"));
				
				Element departureAirportElem=XMLUtils.getFirstElementAtXPath(flightSegElem, "./ota:DepartureAirport");
				Element arrivalAirportElem=XMLUtils.getFirstElementAtXPath(flightSegElem, "./ota:ArrivalAirport");
				flightSegJson.put(JSON_PROP_ORIGLOC, departureAirportElem.getAttribute("LocationCode"));
				flightSegJson.put(JSON_PROP_DESTLOC, arrivalAirportElem.getAttribute("LocationCode"));
				
				JSONObject operatingAirlineJson=new JSONObject();
				Element operatingAirlineElem=XMLUtils.getFirstElementAtXPath(flightSegElem, "./ota:OperatingAirline");
				operatingAirlineJson.put(JSON_PROP_AIRLINECODE,operatingAirlineElem.getAttribute("Code"));
				operatingAirlineJson.put(JSON_PROP_FLIGHTNBR,operatingAirlineElem.getAttribute("FlightNumber"));
				
				flightSegJson.put(JSON_PROP_OPERAIRLINE, operatingAirlineJson );
				flightSegArrJson.put(flightSegJson);
			}
			odoJson.put(JSON_PROP_FLIGHTSEG, flightSegArrJson);
			odoJsonArray.put(odoJson);
		}
		airItinJson.put(JSON_PROP_ORIGDESTOPTS, odoJsonArray);
		return airItinJson;
	}
	
	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();

		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_AirGetSSRQWrapper");
		reqBodyElem.removeChild(wrapperElem);

		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		String prevSuppID = "";
		JSONArray pricedItinsJSONArr = reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
		for (int x = 0; x < pricedItinsJSONArr.length(); x++) {
			JSONObject pricedItinJson = pricedItinsJSONArr.getJSONObject(x);
			String supplierID = pricedItinJson.getString(JSON_PROP_SUPPREF);
			Element ssrRQWrapper = null;
			Element ssrRQ = null;
			Element travelInfoElem = null;
			if (supplierID.equals(prevSuppID)) {
				ssrRQWrapper = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestBody/air:OTA_AirGetSSRQWrapper");
				ssrRQ = XMLUtils.getFirstElementAtXPath(ssrRQWrapper, "./ota:OTA_AirGetSSRQ");
				travelInfoElem = XMLUtils.getFirstElementAtXPath(ssrRQ, "./ota:TravelerInfoSummary");
			} 
			else {
				ssrRQWrapper = (Element) wrapperElem.cloneNode(true);
				reqBodyElem.appendChild(ssrRQWrapper);

				ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, supplierID);
				if (prodSupplier == null) {
					throw new Exception(String.format("Product supplier %s not found for user/client", supplierID));
				}

				Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
				Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem, String.format("./com:SupplierCredentials[com:SupplierID = '%s']", supplierID));
				if (suppCredsElem == null) {
					suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
				}

				ssrRQ = XMLUtils.getFirstElementAtXPath(ssrRQWrapper, "./ota:OTA_AirGetSSRQ");
				ssrRQ.setAttribute("TransactionIdentifier", pricedItinJson.optString(JSON_PROP_TRANSACTID));
				travelInfoElem = XMLUtils.getFirstElementAtXPath(ssrRQ, "./ota:TravelerInfoSummary");
				Element suppID = ownerDoc.createElementNS(NS_AIR, "air:SupplierID");

				suppID.setTextContent(pricedItinJson.getString(JSON_PROP_SUPPREF));
				Element sequence = ownerDoc.createElementNS(NS_AIR, "air:Sequence");
				sequence.setTextContent(Integer.toString(x));

				ssrRQWrapper.insertBefore(suppID, ssrRQ);
				ssrRQWrapper.insertBefore(sequence, ssrRQ);

				// loop for airTravelerAvail
				JSONArray airTravelerAvailArr = reqBodyJson.getJSONArray(JSON_PROP_PAXINFO);
				Element priceReqElem = XMLUtils.getFirstElementAtXPath(travelInfoElem, "./ota:PriceRequestInformation");
				int count=0;
				for (int i = 0; i < airTravelerAvailArr.length(); i++) {
					JSONObject airTravlerAvailJson = airTravelerAvailArr.getJSONObject(i);

					int travellerQuantity = airTravlerAvailJson.getInt(JSON_PROP_QTY);
					for (int rphCount = 0; rphCount < travellerQuantity; rphCount++) {
						Element airTravelerAvail = ownerDoc.createElementNS(Constants.NS_OTA, "ota:AirTravelerAvail");
						Element airTraveler = ownerDoc.createElementNS(Constants.NS_OTA, "ota:AirTraveler");
						airTraveler.setAttribute("PassengerTypeCode", airTravlerAvailJson.getString(JSON_PROP_PAXTYPE));

						Element travelerRefNo = ownerDoc.createElementNS(Constants.NS_OTA, "ota:TravelerRefNumber");
						travelerRefNo.setAttribute("RPH", Integer.toString(count++));
						airTraveler.appendChild(travelerRefNo);
						airTravelerAvail.appendChild(airTraveler);
						travelInfoElem.insertBefore(airTravelerAvail, priceReqElem);
					}
				}
			}
			Element originDestinationInformation = null;
			Element odosElem = null;
			if (supplierID.equals(prevSuppID)) {
				originDestinationInformation = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody/air:OTA_AirGetSSRQWrapper/ota:OTA_AirGetSSRQ/ota:OriginDestinationInformation");
				odosElem = XMLUtils.getFirstElementAtXPath(originDestinationInformation, "./ota:OriginDestinationOptions");
				if (odosElem == null) {
					logger.warn(String.format("XML element for ota:OriginDestinationOptions not found for supplier %s", supplierID));
				}
			} 
			else {
				originDestinationInformation = XMLUtils.getFirstElementAtXPath(ssrRQ, "./ota:OriginDestinationInformation");
				odosElem = ownerDoc.createElementNS(NS_OTA, "ota:OriginDestinationOptions");
			}

			createOriginDestinationOptions(ownerDoc, pricedItinJson, odosElem);
			originDestinationInformation.appendChild(odosElem);
			prevSuppID = supplierID;

		}

		return reqElem;
	}
	
	protected static void createOriginDestinationOptions(Document ownerDoc, JSONObject pricedItinJson, Element odosElem) throws ValidationException {
		JSONObject airItinJson = pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY);
		JSONArray odoJsonArr = airItinJson.optJSONArray(JSON_PROP_ORIGDESTOPTS);
		if(odoJsonArr==null) {
			throw new ValidationException("TRLERR025");
		}
		for (int i=0; i < odoJsonArr.length(); i++) {
			JSONObject odoJson = odoJsonArr.getJSONObject(i);
			Element odoElem = ownerDoc.createElementNS(NS_OTA, "ota:OriginDestinationOption");
			JSONArray flSegJsonArr = odoJson.getJSONArray(JSON_PROP_FLIGHTSEG);
			for (int j=0; j < flSegJsonArr.length(); j++) {
				JSONObject flSegJson = flSegJsonArr.getJSONObject(j);
				Element flSegElem = ownerDoc.createElementNS(NS_OTA, "ota:FlightSegment");

				Element depAirportElem = ownerDoc.createElementNS(NS_OTA, "ota:DepartureAirport");
				depAirportElem.setAttribute("LocationCode", flSegJson.getString("originLocation"));
				flSegElem.appendChild(depAirportElem);

				Element arrAirportElem = ownerDoc.createElementNS(NS_OTA, "ota:ArrivalAirport");
				arrAirportElem.setAttribute("LocationCode", flSegJson.getString("destinationLocation"));
				flSegElem.appendChild(arrAirportElem);

				JSONObject mkAirlineJson = flSegJson.getJSONObject(JSON_PROP_MARKAIRLINE);
				Element opAirlineElem = ownerDoc.createElementNS(NS_OTA,"ota:OperatingAirline");
				JSONObject opAirlineJson = flSegJson.getJSONObject(JSON_PROP_OPERAIRLINE);
				flSegElem.setAttribute("FlightNumber", mkAirlineJson.optString((JSON_PROP_FLIGHTNBR),""));
				flSegElem.setAttribute("DepartureDateTime", flSegJson.getString(JSON_PROP_DEPARTDATE));
				flSegElem.setAttribute("ArrivalDateTime", flSegJson.getString(JSON_PROP_ARRIVEDATE));
				flSegElem.setAttribute("ConnectionType", flSegJson.optString(JSON_PROP_CONNTYPE));
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
				
				mkAirlineElem.setAttribute("Code", mkAirlineJson.getString(JSON_PROP_AIRLINECODE));
				/*String mkAirlineFlNbr = mkAirlineJson.optString(JSON_PROP_FLIGHTNBR, "");*/
				/*if (mkAirlineFlNbr.isEmpty() == false) {
					mkAirlineElem.setAttribute("FlightNumber", mkAirlineFlNbr);
				}*/
				String mkAirlineShortName = mkAirlineJson.optString(JSON_PROP_COMPANYSHORTNAME, "");
				if (mkAirlineShortName.isEmpty() == false) {
					mkAirlineElem.setAttribute("CompanyShortName", mkAirlineShortName);
				}
				flSegElem.appendChild(mkAirlineElem);

				Element bookClsAvailsElem = ownerDoc.createElementNS(NS_OTA, "BookingClassAvails");
				if(flSegJson.optString(JSON_PROP_CABINTYPE)==null) {
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
	}
}
