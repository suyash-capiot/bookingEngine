package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.enums.TripIndicator;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AirPriceProcessor implements AirConstants  {

	private static final Logger logger = LogManager.getLogger(AirPriceProcessor.class);

	public static Element getAirTravelerAvailElement(Document ownerDoc, JSONObject traveller) {
		Element travellerElem = ownerDoc.createElementNS(NS_OTA, "ota:AirTravelerAvail");
		Element psgrElem = ownerDoc.createElementNS(NS_OTA, "ota:PassengerTypeQuantity");
		psgrElem.setAttribute("Code", traveller.getString(JSON_PROP_PAXTYPE));
		psgrElem.setAttribute("Quantity", String.valueOf(traveller.getInt(JSON_PROP_QTY)));
		travellerElem.appendChild(psgrElem);
		return travellerElem;
	}

	public static String process(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		 TripIndicator tripInd =null;
		try {
			TrackingContext.setTrackingContext(reqJson);
			opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			
			validateRequestParameters(reqJson);
             tripInd = AirSearchProcessor.deduceTripIndicator(reqHdrJson, reqBodyJson);
            reqBodyJson.put(JSON_PROP_TRIPIND, tripInd.toString());

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

            JSONObject resBodyJson = new JSONObject();
            JSONArray pricedItinsJsonArr = new JSONArray();
            Element[] resWrapperElems = sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirPriceRSWrapper"));
            for (Element resWrapperElem : resWrapperElems) {
            	Element resBodyElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_AirPriceRS");
            	AirSearchProcessor.getSupplierResponsePricedItinerariesJSON(resBodyElem, pricedItinsJsonArr,reqBodyJson.getJSONArray(JSON_PROP_PAXINFO));
            }
            resBodyJson.put(JSON_PROP_PRICEDITIN, pricedItinsJsonArr);
            resBodyJson.put(JSON_PROP_TRIPIND, tripInd.toString());
            JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);
            
            List<Integer> sourceSupplierCommIndexes=new ArrayList<Integer>();
            
			JSONObject resSupplierJson =  SupplierCommercials.getSupplierCommercialsV3(CommercialsOperation.Search, reqJson, resJson,sourceSupplierCommIndexes);
			JSONObject resClientJson = ClientCommercials.getClientCommercialsV2(reqJson, resJson, resSupplierJson);
			
			
			AirSearchProcessor.calculatePricesV5(reqJson, resJson, resSupplierJson, resClientJson, false, usrCtx,sourceSupplierCommIndexes);
			
			// Apply company offers
			//CompanyOffers.getCompanyOffers(reqJson, resJson, OffersConfig.Type.COMPANY_SEARCH_TIME,CommercialsOperation.Search);
			try {
				CompanyOffers.getCompanyOffers(reqJson, resJson, OffersType.COMPANY_SEARCH_TIME,CommercialsOperation.Search);
			}
			catch(Exception ex) {
				logger.warn("There was an error in processing company offers");
			}
			
			try {
				TaxEngine.getCompanyTaxes(reqJson, resJson);
			}catch(Exception x) {
				logger.warn("An exception was received during Tax Engine call",x);
			}
			
			  try {
	        	   ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
	        	   CommercialCacheProcessor.updateInCache(PRODUCT_AIR, clInfo[clInfo.length - 1].getCommercialsEntityId(), resJson, reqJson);
	           }
	           catch (Exception x) {
	        	   logger.info("An exception was received while updating price results in commercial cache", x);
	           }
			  
			  AirRepriceProcessor.matchRequestAndResponseData(resJson,reqJson);
			return resJson.toString();
		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}

	protected static void createTPA_Extensions(JSONObject reqBodyJson, Element travelerInfoElem) {
		Element tpaExtsElem = XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:TPA_Extensions");
		Element tripTypeElem = XMLUtils.getFirstElementAtXPath(tpaExtsElem, "./air:TripType");
		tripTypeElem.setTextContent(reqBodyJson.getString(JSON_PROP_TRIPTYPE));
		Element tripIndElem = XMLUtils.getFirstElementAtXPath(tpaExtsElem, "./air:TripIndicator");
		tripIndElem.setTextContent(reqBodyJson.getString(JSON_PROP_TRIPIND));
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
				flSegElem.setAttribute("Status", flSegJson.optString(JSON_PROP_STATUS));
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
	
	public static Element[] sortWrapperElementsBySequence(Element[] wrapperElems) {
		Map<Integer, Element> wrapperElemsMap = new TreeMap<Integer, Element>();
		for (Element wrapperElem : wrapperElems) {
			wrapperElemsMap.put(Utils.convertToInt(XMLUtils.getValueAtXPath(wrapperElem, "./air:Sequence"), 0), wrapperElem);
		}
		
		int  idx = 0;
		Element[] seqSortedWrapperElems = new Element[wrapperElems.length];
		Iterator<Map.Entry<Integer, Element>> wrapperElemsIter = wrapperElemsMap.entrySet().iterator();
		while (wrapperElemsIter.hasNext()) {
			seqSortedWrapperElems[idx++] = wrapperElemsIter.next().getValue();
		}
		
		return seqSortedWrapperElems;
	}

	private static void validateRequestParameters(JSONObject reqJson) throws ValidationException {
		AirRequestValidator.validateTripType(reqJson);
		AirRequestValidator.validatePassengerCounts(reqJson);
	}
	
	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_AirPriceRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		
		String prevSuppID = "";
		JSONArray pricedItinsJSONArr = reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
		for (int y=0; y < pricedItinsJSONArr.length(); y++) {
			JSONObject pricedItinJson = pricedItinsJSONArr.getJSONObject(y);
			
			//------- Loop Begin --------
			
			String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
			Element travelerInfoElem = null;
			Element odosElem = null;
			if (suppID.equals(prevSuppID)) {
				Element suppWrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, String.format("./air:OTA_AirPriceRQWrapper[air:SupplierID = '%s']", suppID));
				travelerInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirPriceRQ/ota:TravelerInfoSummary");
				Element otaReqElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirPriceRQ");
				odosElem = XMLUtils.getFirstElementAtXPath(otaReqElem, "./ota:AirItinerary/ota:OriginDestinationOptions");
				if (odosElem == null) {
					logger.warn(String.format("XML element for ota:OriginDestinationOptions not found for supplier %s", suppID));
				}
			}
			else {
				Element suppWrapperElem = (Element) wrapperElem.cloneNode(true);
				reqBodyElem.appendChild(suppWrapperElem);
				ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID);
				if (prodSupplier == null) {
					throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
				}
	
				Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
				Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem, String.format("./com:SupplierCredentials[com:SupplierID = '%s']", suppID));
				if (suppCredsElem == null) {
					suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
				}
	
				Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:SupplierID");
				suppIDElem.setTextContent(suppID);
				
				Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:Sequence");
				sequenceElem.setTextContent(String.valueOf(y));
	
				travelerInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirPriceRQ/ota:TravelerInfoSummary");
				Element otaReqElem = (Element) travelerInfoElem.getParentNode();
				
				//Element priceRqElem= XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirPriceRQ");
				otaReqElem.setAttribute("TransactionIdentifier", pricedItinJson.optString(JSON_PROP_TRANSACTID));
	
				Element priceInfoElem = XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:PriceRequestInformation");
				JSONArray travellerArr = reqBodyJson.getJSONArray(JSON_PROP_PAXINFO);
				for (int i=0; i < travellerArr.length(); i++) {
					JSONObject traveller = (JSONObject) travellerArr.get(i);
					Element travellerElem = getAirTravelerAvailElement(ownerDoc, traveller);
					travelerInfoElem.insertBefore(travellerElem, priceInfoElem);
				}
				
				Element airItinElem = ownerDoc.createElementNS(NS_OTA, "ota:AirItinerary");
				airItinElem.setAttribute("DirectionInd", reqBodyJson.getString(JSON_PROP_TRIPTYPE));
				odosElem = ownerDoc.createElementNS(NS_OTA, "ota:OriginDestinationOptions");
				airItinElem.appendChild(odosElem);
				otaReqElem.insertBefore(airItinElem, travelerInfoElem);
			}
			
			createOriginDestinationOptions(ownerDoc, pricedItinJson, odosElem);
			createTPA_Extensions(reqBodyJson, travelerInfoElem);

			//------- Loop End --------
			prevSuppID = suppID;
		}

		return reqElem;
	}
}
