package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.OperationOrderStatus;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplierUtils;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AirRetrieveProcessor implements AirConstants {
	
	private static final Logger logger = LogManager.getLogger(AirRetrieveProcessor.class);
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		try {
			TrackingContext.setTrackingContext(reqJson);
			opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson);
				
			//---------- End of Request Processing ---------------	
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
		try {	
			
            Element resElem = null;
            //resElem = HTTPServiceConsumer.consumeXMLService("SI/Retrieve", opConfig.getSIServiceURL(), AirConfig.getHttpHeaders(), reqElem);
            //resElem = HTTPServiceConsumer.consumeXMLService("SI/Retrieve", opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
            resElem = HTTPServiceConsumer.consumeXMLService("SI/Retrieve", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
            if (resElem == null) {
            	throw new Exception("Null response received from SI");
            }
          
            JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
          
            JSONObject resBodyJson = new JSONObject();
            JSONArray pricedItinsJsonArr = new JSONArray();
           
            Element[] resWrapperElems = AirPriceProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper"));
            for (Element resWrapperElem : resWrapperElems) {
            	JSONObject priceItinJson=new JSONObject();
            	Element resBodyElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_AirBookRS");
            	if(resBodyElem==null) {
            		break;
            	}
            	priceItinJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath((Element)resWrapperElem, "./air:SupplierID"));
            	getSupplierResponsePricedItinerariesJSON(resBodyElem, priceItinJson);
            	JSONArray paxDetailSupplierJsonArr=new JSONArray();
            	getPaxDetailsFromResponse(resBodyElem,paxDetailSupplierJsonArr,priceItinJson);
            	priceItinJson.put(JSON_PROP_PAXDETAILS, paxDetailSupplierJsonArr);
            	
            	
            	
            	 //JSONArray supplierBookReferencesReqJsonArr = new JSONArray();
            	JSONObject suppBookJson = new JSONObject();
				suppBookJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(resWrapperElem, "./air:SupplierID"));
				suppBookJson.put(JSON_PROP_BOOKREFID, XMLUtils.getValueAtXPath(resWrapperElem, "./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@Type='14']/@ID"));
				suppBookJson.put(JSON_PROP_AIRLINEPNR, XMLUtils.getValueAtXPath(resWrapperElem, "./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@Type='14']/@ID"));
				suppBookJson.put(JSON_PROP_GDSPNR, XMLUtils.getValueAtXPath(resWrapperElem, "./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@Type='16']/@ID"));
				suppBookJson.put(JSON_PROP_TICKETPNR, XMLUtils.getValueAtXPath(resWrapperElem, "./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@Type='30']/@ID"));
				
				//supplierBookReferencesReqJsonArr.put(suppBookJson);
				priceItinJson.put(JSON_PROP_SUPPBOOKREFS, suppBookJson);
				
				
				
				JSONArray paymentInfoJsonArr=new JSONArray();
				getPaymentDetailsFromResponse(resBodyElem,paymentInfoJsonArr,priceItinJson);
            	priceItinJson.put(JSON_PROP_PAYINFO, paymentInfoJsonArr);
				
            	Element ticketingElem=XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:OTA_AirBookRS/ota:AirReservation/ota:Ticketing");
            	if(ticketingElem!=null) {
            		getTicketingFromResponse(ticketingElem,priceItinJson);
            	}
            	if(priceItinJson.isNull(JSON_PROP_AIRITINERARY)) {
            		priceItinJson.put(JSON_PROP_ORDERSTATUS, OperationOrderStatus.XL.toString());
            	}
            	else {
            		priceItinJson.put(JSON_PROP_ORDERSTATUS, OperationOrderStatus.OK.toString());
            	}
				
				pricedItinsJsonArr.put(priceItinJson);
            }
            
           
            resBodyJson.put(JSON_PROP_PRICEDITIN, pricedItinsJsonArr);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);
            return resJson.toString();
            
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
	
		
	}
	
	private static void getTicketingFromResponse(Element ticketingElem, JSONObject priceItinJson) {
		JSONObject ticketing=new JSONObject();
		ticketing.put(JSON_PROP_TICKETINGSTATUS, XMLUtils.getValueAtXPath(ticketingElem, "./@TicketingStatus"));
		ticketing.put(JSON_PROP_TICKETINGVENDORCODE, XMLUtils.getValueAtXPath(ticketingElem, "./ota:TicketingVendor/@CompanyShortName"));
		priceItinJson.put(JSON_PROP_TICKETINGINFO, ticketing);
		
	}

	private static void getPaymentDetailsFromResponse(Element resBodyElem, JSONArray paymentInfoJsonArr,
			JSONObject priceItinJson) {
		
		
		Element[] resPaymentElems=XMLUtils.getElementsAtXPath(resBodyElem, "./ota:AirReservation/ota:Fulfillment/ota:PaymentDetails/ota:PaymentDetail");
		for(Element resPaymentElem : resPaymentElems) {
			JSONObject paymentInfoJson=new JSONObject();
			//paymentInfoJson.put(JSON_PROP_COSTCENTERID, XMLUtils.getValueAtXPath(resPaymentElem, "./@CostCenterID"));
			//paymentInfoJson.put(JSON_PROP_GAURANTEEID, XMLUtils.getValueAtXPath(resPaymentElem, "./@GuaranteeID"));
			//paymentInfoJson.put(JSON_PROP_GAURANTEETYPECODE, XMLUtils.getValueAtXPath(resPaymentElem, "./@GuaranteeTypeCode"));
			//TODO:Create Enums
			String contactType=XMLUtils.getValueAtXPath(resPaymentElem, "./@PaymentType");
			String cardType="";
			  switch(contactType){  
			    case "9": cardType="Business account";break;  
			    case "5": cardType="Credit card";break;  
			    case "6": cardType="Debit card";break;  
	
			    default:cardType="";  
			    }  
			paymentInfoJson.put(JSON_PROP_PAYMETHOD,cardType);
			//Should this hardcoding be there?
			paymentInfoJson.put(JSON_PROP_PAYTYPE, "Full");
			//paymentInfoJson.put(JSON_PROP_RPH, XMLUtils.getValueAtXPath(resPaymentElem, "./@RPH"));
			paymentInfoJson.put(JSON_PROP_AMTPAID, XMLUtils.getValueAtXPath(resPaymentElem, "./ota:PaymentAmount/@Amount"));
			paymentInfoJson.put(JSON_PROP_AMTCCY, XMLUtils.getValueAtXPath(resPaymentElem, "./ota:PaymentAmount/@CurrencyCode"));
			
			
			Element cardDetailsElem=XMLUtils.getFirstElementAtXPath(resPaymentElem, "./ota:PaymentCard");
			
			if(cardDetailsElem!=null) {
				/*JSONObject cardDetailsJson=new JSONObject();
				cardDetailsJson.put(JSON_PROP_EXPIREDATE,cardDetailsElem.getAttribute("ExpireDate"));
				cardDetailsJson.put(JSON_PROP_CARDTYPE,XMLUtils.getValueAtXPath(cardDetailsElem, "./ota:CardType"));
				cardDetailsJson.put(JSON_PROP_CARDHOLDERNAME,XMLUtils.getValueAtXPath(cardDetailsElem,  "./ota:CardHolderName"));
				cardDetailsJson.put(JSON_PROP_CARDENCRYPTVAL,XMLUtils.getValueAtXPath(cardDetailsElem, "./ota:CardNumber/@EncryptedValue"));	
				paymentInfoJson.put(JSON_PROP_CARDDETAILS, cardDetailsJson);*/
				paymentInfoJson.put(JSON_PROP_NAMEONCARD,XMLUtils.getValueAtXPath(cardDetailsElem,  "./ota:CardHolderName"));
				paymentInfoJson.put(JSON_PROP_CARDTYPE,XMLUtils.getValueAtXPath(cardDetailsElem, "./ota:CardType"));
				paymentInfoJson.put(JSON_PROP_EXPIREDATE,cardDetailsElem.getAttribute("ExpireDate"));
			}
			
			paymentInfoJsonArr.put(paymentInfoJson);
		}
	}

	private static void getPaxDetailsFromResponse(Element resBodyElem, JSONArray paxDetailSupplierJsonArr, JSONObject priceItinJson) {
		Element travelerInfoElem=XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:AirReservation/ota:TravelerInfo");
		Map<String,ArrayList<Element>> ssrElemMap=null;
		Map<String,ArrayList<Element>> seatMapElemMap=null;
		if(XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:SpecialReqDetails/ota:SpecialServiceRequests")!=null) {
			 ssrElemMap=getSsrElemMap(travelerInfoElem);
		}
		if(XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:SpecialReqDetails/ota:SeatRequests")!=null) {
			seatMapElemMap=getSeatMapElemMap(travelerInfoElem);
		}
		Element[] airTravelerElems=XMLUtils.getElementsAtXPath(resBodyElem, "./ota:AirReservation/ota:TravelerInfo/ota:AirTraveler");
		
		
		
		for(Element airTravelerElem : airTravelerElems) {
			JSONObject paxDetailJson=new JSONObject();
			paxDetailJson.put(JSON_PROP_TITLE, XMLUtils.getValueAtXPath(airTravelerElem, "./ota:PersonName/ota:NameTitle"));
			paxDetailJson.put(JSON_PROP_FIRSTNAME, XMLUtils.getValueAtXPath(airTravelerElem, "./ota:PersonName/ota:GivenName"));
			paxDetailJson.put(JSON_PROP_SURNAME, XMLUtils.getValueAtXPath(airTravelerElem, "./ota:PersonName/ota:Surname"));
			paxDetailJson.put(JSON_PROP_GENDER, XMLUtils.getValueAtXPath(airTravelerElem, "./@Gender"));
			paxDetailJson.put(JSON_PROP_PAXTYPE, XMLUtils.getValueAtXPath(airTravelerElem, "./@PassengerTypeCode"));
			paxDetailJson.put(JSON_PROP_TRAVELERRPH, XMLUtils.getValueAtXPath(airTravelerElem, "./ota:TravelerRefNumber/@RPH"));
			
			
			
			if((XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Email"))!=null || (XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Telephone"))!=null)
			{
				JSONArray contactDetailsJsonArr=new JSONArray();
				createContactDetailsArr(paxDetailJson,airTravelerElem,contactDetailsJsonArr);
				paxDetailJson.put(JSON_PROP_CONTACTDTLS, contactDetailsJsonArr);
			}
			
			
			if((XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Address"))!=null)
			{
				JSONObject addressDetailsJson = new JSONObject();
				createAddresssArr(paxDetailJson,airTravelerElem,addressDetailsJson);
				paxDetailJson.put(JSON_PROP_ADDRDTLS, addressDetailsJson);
			}
			
			Element specReqDetails=(XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:SpecialReqDetails/ota:SpecialServiceRequests"));
			if(specReqDetails!=null && ssrElemMap.containsKey(paxDetailJson.getString(JSON_PROP_TRAVELERRPH))){
				createSsrReqArr(paxDetailJson,ssrElemMap.get(paxDetailJson.getString(JSON_PROP_TRAVELERRPH)));
			}
			
			
			Element seatDetails=(XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:SpecialReqDetails/ota:SeatRequests"));
			if(seatDetails!=null && ssrElemMap.containsKey(paxDetailJson.getString(JSON_PROP_TRAVELERRPH))){
				createSeatMapArr(paxDetailJson,seatMapElemMap.get(paxDetailJson.getString(JSON_PROP_TRAVELERRPH)));
			}
			
			
			paxDetailSupplierJsonArr.put(paxDetailJson);
		}
		
	}
	

	private static void createSeatMapArr(JSONObject paxDetailJson, ArrayList<Element> seatMapElems) {
		
		JSONArray seatMapJsonArr=new JSONArray();
		for(Element seatMapElem:seatMapElems) {
			JSONObject seatMapJson=new JSONObject();
			seatMapJson.put(JSON_PROP_FLIGHTREFRPH, XMLUtils.getValueAtXPath(seatMapElem, "./@FlightRefNumberRPHList"));
			seatMapJson.put(JSON_PROP_SEATNBR, XMLUtils.getValueAtXPath(seatMapElem, "./@SeatNumber"));
			seatMapJson.put(JSON_PROP_ROWNBR, XMLUtils.getValueAtXPath(seatMapElem, "./@RowNumber"));
			//System.out.println("seatmap->"+XMLTransformer.toString(XMLUtils.getFirstElementAtXPath(seatMapElem, "./ota:TPA_Extensions")));
			seatMapJson.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(seatMapElem, "./ota:TPA_Extensions/air:Price/@Amount"));
			seatMapJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(seatMapElem, "./ota:TPA_Extensions/air:Price/@CurrencyCode"));
			seatMapJsonArr.put(seatMapJson);
		}
		paxDetailJson.put(JSON_PROP_SEATMAP, seatMapJsonArr);
		
	}

	private static void createAddresssArr(JSONObject paxDetailJson, Element airTravelerElem,
			JSONObject addressDetailsJson) {
		Element[] addrLineElems=XMLUtils.getElementsAtXPath(airTravelerElem, "./ota:Address/ota:AddressLine");
		int count=0;
		for(Element addrLineElem:addrLineElems) {
			addressDetailsJson.put(JSON_PROP_ADDRLINE1,addrLineElem.getTextContent());
			if(count==1)
			addressDetailsJson.put(JSON_PROP_ADDRLINE2,addrLineElem.getTextContent());
			count++;
		}
		
		addressDetailsJson.put(JSON_PROP_CITY, XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Address/ota:CityName"));
		addressDetailsJson.put(JSON_PROP_STATE, XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Address/ota:StateProv"));
		addressDetailsJson.put(JSON_PROP_COUNTRY, XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Address/ota:County"));
		addressDetailsJson.put(JSON_PROP_ZIP, XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Address/ota:PostalCode"));
		
	}

	private static void createSsrReqArr(JSONObject paxDetailJson, ArrayList<Element> ssrElemList) {
		JSONArray ssrJsonArr=new JSONArray();
		JSONObject ssrJsonInfo=new JSONObject();
		for(Element ssrElem : ssrElemList) {
			JSONObject ssrJson=new JSONObject();
			
			ssrJson.put(JSON_PROP_SSRCODE,ssrElem.getAttribute("SSRCode"));
			ssrJson.put(JSON_PROP_NUMBER,ssrElem.getAttribute("Number"));
			ssrJson.put(JSON_PROP_SVCQTY,ssrElem.getAttribute("ServiceQuantity"));
			ssrJson.put(JSON_PROP_STATUS,ssrElem.getAttribute("Status"));
			ssrJson.put(JSON_PROP_TYPE,ssrElem.getAttribute("Type"));
			
			if(ssrElem.getAttribute("FlightRefNumberRPHList")!=null)
			{
				ssrJson.put(JSON_PROP_FLIGHREFNBR, ssrElem.getAttribute("FlightRefNumberRPHList"));
			}
			
			Element flightLegElem=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:FlightLeg");
			
			if(flightLegElem!=null && flightLegElem.getAttribute("FlightNumber")!=null)
			{
				ssrJson.put(JSON_PROP_DATE, flightLegElem.getAttribute("Date"));
				ssrJson.put(JSON_PROP_FLIGHTNBR, flightLegElem.getAttribute("FlightNumber"));
			}
			
			
			
			Element departureElem=XMLUtils.getFirstElementAtXPath(flightLegElem, "./ota:DepartureAirport");
			Element arrivalElem=XMLUtils.getFirstElementAtXPath(flightLegElem, "./ota:ArrivalAirport");
			
			if(arrivalElem!=null)
			{
				ssrJson.put(JSON_PROP_DESTLOC, arrivalElem.getAttribute("LocationCode"));
			}
			if(departureElem!=null)
			{
				ssrJson.put(JSON_PROP_ORIGLOC, departureElem.getAttribute("LocationCode"));
			}
			
			Element airLineElem=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:Airline");
			
			if(airLineElem!=null)
			{
				ssrJson.put(JSON_PROP_AIRLINECODE, airLineElem.getAttribute("Code"));
				ssrJson.put(JSON_PROP_COMPANYSHORTNAME, airLineElem.getAttribute("CompanyShortName"));
			}
			
			
			Element servicePrice=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:ServicePrice");
			Element taxesElem=XMLUtils.getFirstElementAtXPath(servicePrice, "./ota:Taxes");
			if (servicePrice != null) {
				JSONObject servicePriceJson=new JSONObject();
				JSONObject taxesJson=new JSONObject();
				Element basePrice=XMLUtils.getFirstElementAtXPath(servicePrice, "./ota:BasePrice");
				if(XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:Amount")!=null) {
					ssrJson.put(JSON_PROP_AMOUNT, XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:Amount").getTextContent());
				}
				else if(servicePrice.hasAttribute("Total")) {
					ssrJson.put(JSON_PROP_AMOUNT, servicePrice.getAttribute("Total").toString());
				}
				
				
				if(XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:currencyCode")!=null)
				ssrJson.put(JSON_PROP_CCYCODE, XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:currencyCode").getTextContent());
				
				if(basePrice!=null)
				servicePriceJson.put(JSON_PROP_BASEPRICE, basePrice.getAttribute(XML_ATTR_AMOUNT));
				
				if (taxesElem != null) {
					taxesJson.put(JSON_PROP_AMOUNT, taxesElem.getAttribute(XML_ATTR_AMOUNT));
					ssrJson.put(JSON_PROP_TAXES, taxesJson);
				}
				
				ssrJson.put(JSON_PROP_SVCPRC, servicePriceJson);
			}
			
			Element categoryCode=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:CategoryCode");
			if(categoryCode!=null)
			{
				ssrJson.put(JSON_PROP_CATCODE, categoryCode.getTextContent());
			}
			
			Element textElem=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:Text");
			if(textElem!=null)
			{
				ssrJson.put(JSON_PROP_DESC, textElem.getTextContent());
			}
		
			
			ssrJsonArr.put(ssrJson);
		}
		ssrJsonInfo.put(JSON_PROP_SPECIALREQUESTINFO, ssrJsonArr);
		paxDetailJson.put(JSON_PROP_SPECIALSVCREQS, ssrJsonInfo);
		
	}

	private static Map<String,ArrayList<Element>> getSsrElemMap(Element travelerInfoElem) {
		
		Map<String,ArrayList<Element>> paxSsrElemMap=new HashMap<String,ArrayList<Element>>();
		Element[] ssrElems=XMLUtils.getElementsAtXPath(travelerInfoElem, "./ota:SpecialReqDetails/ota:SpecialServiceRequests/ota:SpecialServiceRequest");
		for(Element ssrElem : ssrElems) {
			
			String travelerRph=XMLUtils.getValueAtXPath(ssrElem, "./@TravelerRefNumberRPHList");
			if(paxSsrElemMap.containsKey(travelerRph)) {
				ArrayList<Element> paxSsrElemList=paxSsrElemMap.get(travelerRph);
				paxSsrElemList.add(ssrElem);
			}
			else {
				ArrayList<Element> paxSsrElemList=new ArrayList<Element>();
				paxSsrElemList.add(ssrElem);
				paxSsrElemMap.put(travelerRph, paxSsrElemList);
			}
		}
		
		return paxSsrElemMap;
	}
	
	private static Map<String,ArrayList<Element>> getSeatMapElemMap(Element travelerInfoElem) {
		
		Map<String,ArrayList<Element>> paxSeatMapElemMap=new HashMap<String,ArrayList<Element>>();
		Element[] seatMapElems=XMLUtils.getElementsAtXPath(travelerInfoElem, "./ota:SpecialReqDetails/ota:SeatRequests/ota:SeatRequest");
		for(Element seatMapElem : seatMapElems) {
			
			String travelerRph=XMLUtils.getValueAtXPath(seatMapElem, "./@TravelerRefNumberRPHList");
			if(paxSeatMapElemMap.containsKey(travelerRph)) {
				ArrayList<Element> paxSsrElemList=paxSeatMapElemMap.get(travelerRph);
				paxSsrElemList.add(seatMapElem);
			}
			else {
				ArrayList<Element> paxSsrElemList=new ArrayList<Element>();
				paxSsrElemList.add(seatMapElem);
				paxSeatMapElemMap.put(travelerRph, paxSsrElemList);
			}
		}
		
		return paxSeatMapElemMap;
	}


	private static void createContactDetailsArr(JSONObject paxDetailJson, Element airTravelerElem,
			JSONArray contactDetailsJsonArr) {
		JSONObject contactDetailJson=new JSONObject();
		JSONObject contactInfoJson=new JSONObject();
		if(((XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Email"))!=null))
				{
			contactDetailJson.put(JSON_PROP_EMAIL,XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Email"));
				}
		if((XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Telephone"))!=null)
		{
			contactDetailJson.put("mobileNo",XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Telephone/@PhoneNumber"));
			contactDetailJson.put("locationCode",XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Telephone/@LocationCode"));
			contactDetailJson.put(JSON_PROP_COUNTRYCODE,XMLUtils.getValueAtXPath(airTravelerElem, "./ota:Telephone/@CountryAccessCode"));
		}
		contactInfoJson.put(JSON_PROP_CONTACTINFO, contactDetailJson);
		contactDetailsJsonArr.put(contactInfoJson);
		
		
	}

	public static void getSupplierResponsePricedItinerariesJSON(Element resBodyElem, JSONObject priceItinJson) throws Exception {
    	getSupplierResponsePricedItinerariesJSON(resBodyElem, priceItinJson, false, 0);
    }
	
	public static void getSupplierResponsePricedItinerariesJSON(Element resBodyElem, JSONObject priceItinJson, boolean generateBookRefIdx, int bookRefIdx) throws Exception {
    	
        Element airReservationElem=XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:AirReservation");
        
        Element airItinPricingElem = XMLUtils.getFirstElementAtXPath(airReservationElem, "./ota:PriceInfo");
        priceItinJson.put(JSON_PROP_AIRPRICEINFO, getAirItineraryPricingJSON(airItinPricingElem));
        
        Element airItinElem = XMLUtils.getFirstElementAtXPath(airReservationElem, "./ota:AirItinerary");
        priceItinJson.put(JSON_PROP_AIRITINERARY, getAirItineraryJSON(airItinElem));
        
    }
	  public static JSONObject getAirItineraryJSON(Element airItinElem)throws Exception {
	        JSONObject airItinJson = new JSONObject();

	        if (airItinElem != null) {
//	            // TODO: RPH is different for each flight segment. What to map here?
//	            airItinJson.put("rph", "");
	            JSONArray odOptJsonArr = new JSONArray();
	            Element[] odOptElems = XMLUtils.getElementsAtXPath(airItinElem, "./ota:OriginDestinationOptions/ota:OriginDestinationOption");
	            for (Element odOptElem : odOptElems) {
	                odOptJsonArr.put(getOriginDestinationOptionJSON(odOptElem));
	            }

	            airItinJson.put(JSON_PROP_ORIGDESTOPTS, odOptJsonArr);
	        }

	        return airItinJson;
	    }
	  
	  public static JSONObject getOriginDestinationOptionJSON(Element odOptElem)throws Exception {
	        JSONObject odOptJson = new JSONObject();
	        
	        
	        JSONArray flightSegsJsonArr = new JSONArray();
	        Element[] flightSegElems = XMLUtils.getElementsAtXPath(odOptElem, "./ota:FlightSegment");
	        for (Element flightSegElem : flightSegElems) {
	        	JSONObject flightSegJson = new JSONObject();
	            flightSegsJsonArr.put(AirSearchProcessor.getFlightSegmentJSON(flightSegElem,flightSegJson));
	        }
	        odOptJson.put(JSON_PROP_FLIGHTSEG, flightSegsJsonArr);
	        return odOptJson;
	    }
	
	
	 public static JSONObject getAirItineraryPricingJSON(Element airItinPricingElem)throws Exception {
	        JSONObject airItinPricingJson = new JSONObject();

	        JSONObject itinTotalFareJson = new JSONObject();
	        itinTotalFareJson.put(JSON_PROP_AMOUNT,XMLUtils.getValueAtXPath(airItinPricingElem, "./ota:ItinTotalFare/ota:TotalFare/@Amount"));
	        itinTotalFareJson.put(JSON_PROP_CCYCODE,XMLUtils.getValueAtXPath(airItinPricingElem, "./ota:ItinTotalFare/ota:TotalFare/@CurrencyCode"));
	        airItinPricingJson.put(JSON_PROP_ITINTOTALFARE, itinTotalFareJson);

	        JSONArray ptcFaresJsonArr = new JSONArray();
	        Element[] ptcFareBkElems = XMLUtils.getElementsAtXPath(airItinPricingElem, "./ota:PTC_FareBreakdowns/ota:PTC_FareBreakdown");
	        for (Element ptcFareBkElem : ptcFareBkElems) {
	            ptcFaresJsonArr.put(AirSearchProcessor.getPTCFareBreakdownJSON(ptcFareBkElem));
	        }
	        airItinPricingJson.put(JSON_PROP_PAXTYPEFARES, ptcFaresJsonArr);

	       
	        if((XMLUtils.getFirstElementAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR"))!=null) {
	        	
	       
	        JSONArray ssrJsonArr = new JSONArray();
	        Element[] ssrElems = XMLUtils.getElementsAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR/ota:SpecialServiceRequests/ota:SpecialServiceRequest");
	        
	        for (Element ssrElem : ssrElems) {
	        	ssrJsonArr.put(AirSearchProcessor.getSSRJson(ssrElem));
	        }
	        airItinPricingJson.put(JSON_PROP_SPECIALSVCREQS, ssrJsonArr);
	        }
	        
	        return airItinPricingJson;
	    }

    

	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
	 private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {

		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_ReadRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		
		JSONArray supplierBookRefArr = reqBodyJson.getJSONArray("supplierBookReferences");
		for(int j=0; j < supplierBookRefArr.length(); j++)
		{
			JSONObject suppBookRefJson = supplierBookRefArr.getJSONObject(j);
			
			String suppID = suppBookRefJson.getString(JSON_PROP_SUPPREF);
			String credsName = suppBookRefJson.optString(JSON_PROP_CREDSNAME);
			Element suppWrapperElem = null;

			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
			
			ProductSupplier prodSupplier = (credsName.isEmpty()) 
													? usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID) 
															: ProductSupplierUtils.getSupplierCredentialsForCredentialsName(suppID, credsName);
			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s %s not found for user/client", suppID, (credsName.isEmpty()) ? "" : String.format("and credential name %s", credsName)));
			}
			
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
			Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem, String.format("./com:SupplierCredentials[com:SupplierID = '%s']", suppID));
			if (suppCredsElem == null) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}
			Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:SupplierID");
			suppIDElem.setTextContent(suppID);
			
			
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:Sequence");
			sequenceElem.setTextContent(Integer.toString(j));
			
			Element readRqElem=XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_ReadRQ");
			
			Element uniqueIdElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_ReadRQ/ota:UniqueID");
			Element uniqueIdElem1= (Element) uniqueIdElem.cloneNode(true);
			
			uniqueIdElem.setAttribute("Type","14");
			uniqueIdElem.setAttribute("ID", suppBookRefJson.getString(JSON_PROP_BOOKREFID));
			
			uniqueIdElem1.setAttribute("Type","24");
			uniqueIdElem1.setAttribute("ID", suppBookRefJson.getString(JSON_PROP_GDSPNR));
			readRqElem.appendChild(uniqueIdElem1);
		
		}
	
		return reqElem;

	}
}
