package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class HolidaysAddServiceProcessor implements HolidayConstants {

private static final Logger logger = LogManager.getLogger(HolidaysAddServiceProcessor.class);
  
  public static String process(JSONObject requestJson) {
    try {
      
    //HolidaysConfig.loadConfig();
    //OperationConfig opConfig = HolidaysConfig.getOperationConfig("addservice");
    ServiceConfig opConfig = HolidaysConfig.getOperationConfig("addservice");
    
    //clone shell si request from ProductConfig collection HOLIDAYS document
    Element requestElement = (Element) opConfig.getRequestXMLShell().cloneNode(true);
    
    //create Document object associated with request node, this Document object is also used to create new nodes. 
    Document ownerDoc = requestElement.getOwnerDocument();
    
    TrackingContext.setTrackingContext(requestJson);
      
    JSONObject requestHeader = requestJson.getJSONObject(JSON_PROP_REQHEADER);
    JSONObject requestBody = requestJson.getJSONObject(JSON_PROP_REQBODY);
    JSONObject resBodyJson = new JSONObject();
    
    //CREATE SI REQUEST HEADER
    String sessionID = requestHeader.getString(JSON_PROP_SESSIONID);
    String transactionID = requestHeader.getString(JSON_PROP_TRANSACTID);
    String userID = requestHeader.getString(JSON_PROP_USERID);
    
    Element requestHeaderElement = XMLUtils.getFirstElementAtXPath(requestElement, "./pac:RequestHeader");
    
    Element userElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:UserID");
    userElement.setTextContent(userID);
    
    Element sessionElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:SessionID");
    sessionElement.setTextContent(sessionID);

    Element transactionElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:TransactionID");
    transactionElement.setTextContent(transactionID);

    //get the list of all supplier in packages
   // UserContext usrCtx = UserContext.getUserContextForSession(sessionID);
    UserContext usrCtx = UserContext.getUserContextForSession(requestHeader);
    
    Element supplierCredentialsListElement  = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:SupplierCredentialsList");

    //CREATE SI REQUEST BODY
    Element requestBodyElement = XMLUtils.getFirstElementAtXPath(requestElement, "./pac1:RequestBody");
    
    Element wrapperElement = XMLUtils.getFirstElementAtXPath(requestBodyElement, "./pac:OTA_DynamicPkgBookRQWrapper");
    requestBodyElement.removeChild(wrapperElement);
        
    int sequence = 0;
    JSONArray dynamicPackageArray = requestBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
    for (int i=0; i < dynamicPackageArray.length(); i++) 
    {
        JSONObject dynamicPackageObj = dynamicPackageArray.getJSONObject(i);
        sequence++;
    
        String supplierID = dynamicPackageObj.getString(JSON_PROP_SUPPREF);
        Element supWrapperElement = null;
        Element otaBookRQ = null;
        Element searchCriteria = null;
        Element dynamicPackage = null;
        
        //Making supplierCredentialsList for Each SupplierID
        supplierCredentialsListElement = HolidaysRepriceProcessorV2.getSupplierCredentialsList(ownerDoc, usrCtx, supplierID, sequence, supplierCredentialsListElement);
          
        //Making request body for particular supplierID
        supWrapperElement = (Element) wrapperElement.cloneNode(true);
        requestBodyElement.appendChild(supWrapperElement);
            
        //Setting supplier id in request body
        Element supplierIDElement = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./pac:SupplierID");
        supplierIDElement.setTextContent(supplierID);
            
        //Setting sequence in request body
        Element sequenceElement = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./pac:Sequence");
        sequenceElement.setTextContent(Integer.toString(sequence));

        //getting parent node OTA_DynamicPkgAvailRQ from SearchCriteria
        otaBookRQ = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./ns:OTA_DynamicPkgBookRQ");

        //creating element dynamic package
        dynamicPackage = XMLUtils.getFirstElementAtXPath(otaBookRQ, "./ns:DynamicPackage");  
        
        String allowOverrideAirDates = dynamicPackageObj.getString("allowOverrideAirDates");
        
        if(allowOverrideAirDates != null && allowOverrideAirDates.length() != 0)
        dynamicPackage.setAttribute("AllowOverrideAirDates", allowOverrideAirDates);
            
        //Creating Components element
        JSONObject components = dynamicPackageObj.getJSONObject(JSON_PROP_COMPONENTS); 
            
        if (components == null || components.length() == 0) {
          throw new Exception(String.format("Object components must be set for supplier %s", supplierID));
        }
            
        Element componentsElement = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:Components");
            
        //Creating Hotel Component
        JSONArray hotelComponents = components.getJSONArray(JSON_PROP_HOTEL_COMPONENT);
            
        if(hotelComponents != null && hotelComponents.length() != 0)
           componentsElement = HolidaysRepriceProcessorV2.getHotelComponentElement(ownerDoc, hotelComponents, componentsElement);
          
        //Creating Air Component
        JSONArray airComponents = components.getJSONArray(JSON_PROP_AIR_COMPONENT);
            
        if(airComponents != null && airComponents.length() != 0)
          componentsElement = HolidaysRepriceProcessorV2.getAirComponentElement(ownerDoc, dynamicPackageObj, airComponents, componentsElement, supplierID);
            
        //Creating PackageOptionComponent Element
        Element packageOptionComponentElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOptionComponent");
            
        String subTourCode = dynamicPackageObj.getString("subTourCode");
        if(subTourCode != null && subTourCode.length() > 0)
          packageOptionComponentElement.setAttribute("QuoteID", subTourCode);
            
        Element packageOptionsElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOptions");
            
        Element packageOptionElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOption");
        
        String refPoint = dynamicPackageObj.getString("refPoint");
        if(refPoint != null && refPoint.length() > 0)
          packageOptionElement.setAttribute("CompanyShortName", refPoint);
        
        String optionRef = dynamicPackageObj.getString("optionRef");
        if(optionRef != null && optionRef.length() > 0)
          packageOptionElement.setAttribute("ID", optionRef);
            
        Element tpaElement = ownerDoc.createElementNS(NS_OTA, "ns:TPA_Extensions");
            
        //Creating Cruise Component
        JSONArray cruiseComponents = components.getJSONArray(JSON_PROP_CRUISE_COMPONENT);
            
        if(cruiseComponents != null && cruiseComponents.length() != 0)
          tpaElement = HolidaysRepriceProcessorV2.getCruiseComponentElement(ownerDoc, cruiseComponents, tpaElement);
        
        //Appending TPA element to package Option Element
        packageOptionElement.appendChild(tpaElement);
        
        //Appending package Option Element to package Options Element
        packageOptionsElement.appendChild(packageOptionElement);
        
        //Appending package Options Element to PackageOptionComponent Element
        packageOptionComponentElement.appendChild(packageOptionsElement);
        
        //Appending PackageOptionComponent Element to Components Element
        componentsElement.appendChild(packageOptionComponentElement);
      }
    
      Element responseElement = null;
      //responseElement = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), HolidaysConfig.getHttpHeaders(), requestElement);
      logger.trace(String.format("AddService SI request = %s", XMLTransformer.toString(requestElement)));
      responseElement = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), requestElement);
      logger.trace(String.format("AddService SI response = %s", XMLTransformer.toString(responseElement)));
      if (responseElement == null)
          throw new Exception("Null response received from SI");
      
   // Added code for converting SI XML response to SI JSON Response

   		JSONArray dynamicPackageArr = new JSONArray();

   		
   		Element[] oTA_wrapperElems = XMLUtils.getElementsAtXPath(responseElement,
   				"./pac1:ResponseBody/pac:OTA_DynamicPkgBookRSWrapper");

   		for (Element oTA_wrapperElem : oTA_wrapperElems) {

   			String supplierIDStr = String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:SupplierID"));
   			String sequenceStr = String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:Sequence"));

   			Element[] dynamicPackageElemArray = XMLUtils.getElementsAtXPath(oTA_wrapperElem,
   					"./ns:OTA_DynamicPkgBookRS/ns:DynamicPackage");

   			for (Element dynamicPackageElem : dynamicPackageElemArray) {
   				JSONObject dynamicPackJson = getSupplierResponseDynamicPackageJSON(dynamicPackageElem);

   				dynamicPackJson.put(JSON_PROP_SUPPLIERID, supplierIDStr);
				dynamicPackJson.put(JSON_PROP_SEQUENCE, sequenceStr);
   				dynamicPackageArr.put(dynamicPackJson);
   			}
   			resBodyJson.put(JSON_PROP_DYNAMICPACKAGE, dynamicPackageArr);

   		}

   		JSONObject resJson = new JSONObject();
   		resJson.put(JSON_PROP_RESHEADER, requestHeader);
   		resJson.put(JSON_PROP_RESBODY, resBodyJson);
   		logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));

   		// Call BRMS Supplier and Client Commercials
   		JSONObject resSupplierCommJson = HolidaysSupplierCommercials.getSupplierCommercials(requestJson, resJson,CommercialsOperation.Search);
   		logger.info(String.format("Supplier Commercial Response = %s", resSupplierCommJson.toString()));
   		
   		JSONObject resClientCommJson = HolidaysClientCommercials.getClientCommercials(requestJson, resSupplierCommJson);
   		logger.info(String.format("Client Commercial Response = %s", resClientCommJson.toString()));
      
      return XMLTransformer.toString(responseElement);
    }
    catch (Exception x) {
        x.printStackTrace();
        return "{\"status\": \"ERROR\"}";
      }
    }
  
	private static JSONObject getSupplierResponseDynamicPackageJSON(Element dynamicPackageElem) {

		JSONObject component = new JSONObject();
		Element componentElement = XMLUtils.getFirstElementAtXPath(dynamicPackageElem, "./ns:Components");
		JSONObject componentJson = new JSONObject();

		JSONObject airComponentJson = getAirComponentJSON(componentElement);

		componentJson.put("airComponent", airComponentJson);
		component.put("components", componentJson);
		return component;
	}

	private static JSONObject getAirComponentJSON(Element componentElement) {
		Element airComponentElement = XMLUtils.getFirstElementAtXPath(componentElement, "./ns:AirComponent");
		JSONObject airComponentJson = new JSONObject();
		String dynamicPkgAction = String.valueOf(XMLUtils.getValueAtXPath(airComponentElement, "./@DynamicPkgAction"));
		airComponentJson.put("dynamicPkgAction", dynamicPkgAction);

		Element[] airItineraryElems = XMLUtils.getElementsAtXPath(airComponentElement, "./ns:AirItinerary");
		JSONArray airItineraryJsonArr = new JSONArray();
		for (Element airItineraryElem : airItineraryElems) {

			JSONObject airItineraryJson = getAirItineraryJSON(airItineraryElem);
			airItineraryJsonArr.put(airItineraryJson);

		}

		airComponentJson.put("airItinerary", airItineraryJsonArr);

		Element[] airItineraryPricingInfoElems = XMLUtils.getElementsAtXPath(airComponentElement,
				"./ns:AirItineraryPricingInfo");
		JSONArray airItineraryPricingInfoArr = new JSONArray();
		for (Element airItineraryPricingInfoElem : airItineraryPricingInfoElems) {

			JSONObject airItineraryPricingInfoJson = getAirItineraryPricingInfoJson(airItineraryPricingInfoElem);
			airItineraryPricingInfoArr.put(airItineraryPricingInfoJson);
		}

		airComponentJson.put("airItineraryPricingInfo", airItineraryPricingInfoArr);

		Element[] ticketingInfoElems = XMLUtils.getElementsAtXPath(airComponentElement, "./ns:TicketingInfo");
		JSONArray ticketingInfoArr = new JSONArray();
		for (Element ticketingInfoElem : ticketingInfoElems) {

			JSONObject ticketingInfoJson = getTicketingInfoJson(ticketingInfoElem);
			ticketingInfoArr.put(ticketingInfoJson);
		}

		airComponentJson.put("ticketingInfo", ticketingInfoArr);

		return airComponentJson;
	}

	private static JSONObject getTicketingInfoJson(Element ticketingInfoElem) {
		JSONObject ticketingInfoJson = new JSONObject();

		JSONObject tPA_ExtensionsJson = getTpaAirItinRPH(ticketingInfoElem);

		Element earlyTicketingElement = XMLUtils.getFirstElementAtXPath(ticketingInfoElem,
				"./ns:TPA_Extensions/pac:EarlyTicketingDetails");
		JSONObject earlyTicketingJson = new JSONObject();
		String earlyTicketingDate = String
				.valueOf(XMLUtils.getValueAtXPath(earlyTicketingElement, "./pac:EarlyTicketingDate"));
		String earlyTicketingDepositAmount = String
				.valueOf(XMLUtils.getValueAtXPath(earlyTicketingElement, "./pac:EarlyTicketingDepositAmount"));
		String ticketingDate = String.valueOf(XMLUtils.getValueAtXPath(earlyTicketingElement, "./pac:TicketingDate"));

		earlyTicketingJson.put("earlyTicketingDate", earlyTicketingDate);
		earlyTicketingJson.put("earlyTicketingDepositAmount", earlyTicketingDepositAmount);
		earlyTicketingJson.put("ticketingDate", ticketingDate);

		tPA_ExtensionsJson.put("earlyTicketingDetails", earlyTicketingJson);
		ticketingInfoJson.put("tPA_Extensions", tPA_ExtensionsJson);
		return ticketingInfoJson;
	}

	private static JSONObject getAirItineraryPricingInfoJson(Element airItineraryPricingInfoElem) {
		String amount;
		String currencyCode;
		String taxName;
		String passengerQuantity;
		JSONObject airItineraryPriceInfoJson = new JSONObject();

		Element itinTotalFareElement = XMLUtils.getFirstElementAtXPath(airItineraryPricingInfoElem,
				"./ns:ItinTotalFare");
		JSONObject itinTotalFareJson = new JSONObject();

		Element baseFareElement = XMLUtils.getFirstElementAtXPath(itinTotalFareElement, "./ns:BaseFare");
		JSONObject baseFareJson = new JSONObject();

		amount = String.valueOf(XMLUtils.getValueAtXPath(baseFareElement, "./@Amount"));
		currencyCode = String.valueOf(XMLUtils.getValueAtXPath(baseFareElement, "./@CurrencyCode"));

		baseFareJson.put("amount", amount);
		baseFareJson.put("currencyCode", currencyCode);

		itinTotalFareJson.put("baseFare", baseFareJson);

		Element taxesElement = XMLUtils.getFirstElementAtXPath(itinTotalFareElement, "./ns:Taxes");
		JSONObject taxesJson = new JSONObject();
		amount = String.valueOf(XMLUtils.getValueAtXPath(taxesElement, "./@Amount"));
		taxesJson.put("amount", amount);

		Element[] taxElems = XMLUtils.getElementsAtXPath(taxesElement, "./ns:Tax");
		JSONArray taxArr = new JSONArray();
		for (Element taxElem : taxElems) {

			JSONObject taxJson = new JSONObject();

			taxName = String.valueOf(XMLUtils.getValueAtXPath(taxElem, "./@TaxName"));
			amount = String.valueOf(XMLUtils.getValueAtXPath(taxElem, "./@Amount"));
			currencyCode = String.valueOf(XMLUtils.getValueAtXPath(taxElem, "./@CurrencyCode"));

			taxJson.put("taxName", taxName);
			taxJson.put("amount", amount);
			taxJson.put("currencyCode", currencyCode);
			taxArr.put(taxJson);

		}

		taxesJson.put("tax", taxArr);
		itinTotalFareJson.put("taxes", taxesJson);

		Element totalFareElement = XMLUtils.getFirstElementAtXPath(itinTotalFareElement, "./ns:TotalFare");
		JSONObject totalFareJson = new JSONObject();

		passengerQuantity = String.valueOf(XMLUtils.getValueAtXPath(totalFareElement, "./@PassengerQuantity"));
		amount = String.valueOf(XMLUtils.getValueAtXPath(totalFareElement, "./@Amount"));
		currencyCode = String.valueOf(XMLUtils.getValueAtXPath(totalFareElement, "./@CurrencyCode"));

		totalFareJson.put("passengerQuantity", passengerQuantity);
		totalFareJson.put("amount", amount);
		totalFareJson.put("currencyCode", currencyCode);

		itinTotalFareJson.put("totalFare", totalFareJson);

		airItineraryPriceInfoJson.put("itinTotalFare", itinTotalFareJson);

		JSONObject tPA_ExtensionsJson = getTpaAirItinRPH(airItineraryPricingInfoElem);
		airItineraryPriceInfoJson.put("tPA_Extensions", tPA_ExtensionsJson);

		return airItineraryPriceInfoJson;
	}

	private static JSONObject getTpaAirItinRPH(Element airItineraryPricingInfoElem) {
		Element pkgs_TPAElement = XMLUtils.getFirstElementAtXPath(airItineraryPricingInfoElem,
				"./ns:TPA_Extensions/pac:Pkgs_TPA");
		JSONObject tPA_ExtensionsJson = new JSONObject();
		JSONObject pkgs_TPAJson = new JSONObject();

		String airItineraryRPH = String.valueOf(XMLUtils.getValueAtXPath(pkgs_TPAElement, "./pac:AirItineraryRPH"));

		pkgs_TPAJson.put("airItineraryRPH", airItineraryRPH);

		tPA_ExtensionsJson.put("pkgs_TPA", pkgs_TPAJson);
		return tPA_ExtensionsJson;
	}

	private static JSONObject getAirItineraryJSON(Element airItineraryElem) {

		JSONObject airItineraryJson = new JSONObject();
		String airItineraryRPH = String.valueOf(XMLUtils.getValueAtXPath(airItineraryElem, "./@AirItineraryRPH"));

		airItineraryJson.put("airItineraryRPH", airItineraryRPH);
		Element originDestinationOptionsElement = XMLUtils.getFirstElementAtXPath(airItineraryElem,
				"./ns:OriginDestinationOptions");
		JSONObject originDestinationOptionsJson = new JSONObject();

		Element[] originDestinationOptionElemArray = XMLUtils.getElementsAtXPath(originDestinationOptionsElement,
				"./ns:OriginDestinationOption");
		JSONArray originDestinationOptionJsonArr = new JSONArray();

		for (Element originDestinationOptionElem : originDestinationOptionElemArray) {

			JSONObject originDestinationOptionJson = getOriginDestinationOptionJSON(originDestinationOptionElem);
			originDestinationOptionJsonArr.put(originDestinationOptionJson);

		}

		originDestinationOptionsJson.put(JSON_PROP_ORIGINDESTINATIONOPTION, originDestinationOptionJsonArr);
		airItineraryJson.put(JSON_PROP_ORIGINDESTOPTIONS, originDestinationOptionsJson);

		return airItineraryJson;
	}

	private static JSONObject getOriginDestinationOptionJSON(Element originDestinationOptionElem) {
		JSONObject originDestOptionJson = new JSONObject();

		Element[] flightSegmentElemArray = XMLUtils.getElementsAtXPath(originDestinationOptionElem,
				"./ns:FlightSegment");
		JSONArray flightSegmentArr = new JSONArray();

		for (Element flightSegmentElem : flightSegmentElemArray) {
			JSONObject flightSegmentJson = getFlightSegment(flightSegmentElem);
			flightSegmentArr.put(flightSegmentJson);
		}

		originDestOptionJson.put(JSON_PROP_FLIGHTSEGMENT, flightSegmentArr);

		return originDestOptionJson;
	}

	private static JSONObject getFlightSegment(Element flightSegmentElem) {
		JSONObject flightSegmentJson = new JSONObject();

		String flightNumber = String.valueOf(XMLUtils.getValueAtXPath(flightSegmentElem, "./@FlightNumber"));
		String stopQuantity = String.valueOf(XMLUtils.getValueAtXPath(flightSegmentElem, "./@StopQuantity"));
		String departureDateTime = String.valueOf(XMLUtils.getValueAtXPath(flightSegmentElem, "./@DepartureDateTime"));
		String arrivalDateTime = String.valueOf(XMLUtils.getValueAtXPath(flightSegmentElem, "./@ArrivalDateTime"));
		String numberInParty = String.valueOf(XMLUtils.getValueAtXPath(flightSegmentElem, "./@NumberInParty"));

		flightSegmentJson.put("flightNumber", flightNumber);
		flightSegmentJson.put("stopQuantity", stopQuantity);
		flightSegmentJson.put("departureDateTime", departureDateTime);
		flightSegmentJson.put("arrivalDateTime", arrivalDateTime);
		flightSegmentJson.put("numberInParty", numberInParty);

		String locationCode;
		Element departureAirportElement = XMLUtils.getFirstElementAtXPath(flightSegmentElem, "./ns:DepartureAirport");
		JSONObject departureAirportJson = new JSONObject();
		locationCode = String.valueOf(XMLUtils.getValueAtXPath(departureAirportElement, "./@LocationCode"));

		departureAirportJson.put("locationCode", locationCode);

		flightSegmentJson.put("departureAirport", departureAirportJson);

		Element arrivalAirportJsonElement = XMLUtils.getFirstElementAtXPath(flightSegmentElem, "./ns:ArrivalAirport");
		JSONObject arrivalAirportJson = new JSONObject();
		locationCode = String.valueOf(XMLUtils.getValueAtXPath(arrivalAirportJsonElement, "./@LocationCode"));

		arrivalAirportJson.put("locationCode", locationCode);

		flightSegmentJson.put("arrivalAirport", arrivalAirportJson);

		Element operatingAirlineJsonElement = XMLUtils.getFirstElementAtXPath(flightSegmentElem,
				"./ns:OperatingAirline");
		JSONObject operatingAirlineJson = new JSONObject();
		String companyShortName = String
				.valueOf(XMLUtils.getValueAtXPath(operatingAirlineJsonElement, "./@CompanyShortName"));
		String code = String.valueOf(XMLUtils.getValueAtXPath(operatingAirlineJsonElement, "./@Code"));

		operatingAirlineJson.put("flightNumber", flightNumber);
		operatingAirlineJson.put("companyShortName", companyShortName);
		operatingAirlineJson.put("code", code);

		flightSegmentJson.put("operatingAirline", operatingAirlineJson);

		Element[] bookingClassAvailsElems = XMLUtils.getElementsAtXPath(flightSegmentElem, "./ns:BookingClassAvails");
		JSONArray bookingClassAvailsArr = new JSONArray();

		for (Element bookingClassAvailsElem : bookingClassAvailsElems) {

			JSONObject bookingClassAvailsJson = new JSONObject();

			String cabinType = String.valueOf(XMLUtils.getValueAtXPath(bookingClassAvailsElem, "./@CabinType"));
			bookingClassAvailsJson.put("cabinType", cabinType);

			JSONObject bookingClassAvailJson = new JSONObject();
			Element bookingClassAvailElement = XMLUtils.getFirstElementAtXPath(bookingClassAvailsElem,
					"./ns:BookingClassAvail");
			String resBookDesigCode = String
					.valueOf(XMLUtils.getValueAtXPath(bookingClassAvailElement, "./@ResBookDesigCode"));
			bookingClassAvailJson.put("resBookDesigCode", resBookDesigCode);
			bookingClassAvailsJson.put("bookingClassAvail", bookingClassAvailJson);

			bookingClassAvailsArr.put(bookingClassAvailsJson);

		}

		flightSegmentJson.put("bookingClassAvails", bookingClassAvailsArr);

		return flightSegmentJson;
	}
}
