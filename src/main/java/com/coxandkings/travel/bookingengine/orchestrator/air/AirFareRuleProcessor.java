
package com.coxandkings.travel.bookingengine.orchestrator.air;


import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.enums.TripIndicator;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;


public class AirFareRuleProcessor implements AirConstants  {
	
	private static final Logger logger = LogManager.getLogger(AirFareRuleProcessor.class);
	static final String OPERATION_NAME = "fareRules";
	
	public static String process(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		Map<Integer,JSONArray> flightSegSeqMap=null; 
		 Map<Integer,Integer> fareInfoToPricedItinMap=null;
		try {
			TrackingContext.setTrackingContext(reqJson);
			//opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			opConfig=AirConfig.getOperationConfig(OPERATION_NAME);
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

            TripIndicator tripInd = AirSearchProcessor.deduceTripIndicator(reqHdrJson, reqBodyJson);
            reqBodyJson.put(JSON_PROP_TRIPIND, tripInd.toString());

			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		 flightSegSeqMap=new HashMap<Integer,JSONArray>(); 
		 fareInfoToPricedItinMap=new HashMap<Integer,Integer>();
            reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson,flightSegSeqMap,fareInfoToPricedItinMap);
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
		try {
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(),  opConfig.getHttpHeaders(), "POST", opConfig.getServiceTimeoutMillis(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			
			 JSONObject resJson = new JSONObject();
	         resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
	         JSONObject resBodyJson = new JSONObject();
	         Map<Integer,JSONObject> pricedItinMap=new HashMap<Integer,JSONObject>();
			Element[] wrapprElems=AirPriceProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirRulesRSWrapper"));
			//JSONArray fareInfoJsonArr=new JSONArray();
			JSONArray fareInfoJsonArr=null;
			for (Element wrapprElem : wrapprElems) {
				
				int sequenceNo=Integer.parseInt(XMLUtils.getValueAtXPath(wrapprElem, "./air:Sequence"));
				int priceItinNo=fareInfoToPricedItinMap.get(sequenceNo);
				JSONObject pricedItinJson=null;
				if(pricedItinMap.containsKey(priceItinNo)) {
					pricedItinJson=pricedItinMap.get(priceItinNo);
				
			        fareInfoJsonArr=pricedItinJson.getJSONArray(JSON_PROP_FAREINFO);
					
						
				}
				else {
					pricedItinJson=new JSONObject();
					fareInfoJsonArr=new JSONArray();
					pricedItinMap.put(priceItinNo, pricedItinJson);
					pricedItinJson.put(JSON_PROP_FAREINFO, fareInfoJsonArr);
					pricedItinJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(wrapprElem, "./air:SupplierID"));
					
				}
				
				JSONObject fareInfoJson=new JSONObject();
				getFareRules(reqHdrJson,fareInfoJson,wrapprElem,flightSegSeqMap);
				
				fareInfoJsonArr.put(fareInfoJson);
				pricedItinJson.put(JSON_PROP_FAREINFO, fareInfoJsonArr);
				
			}
			JSONArray pricedItinJsonArr=new JSONArray();
			for(Map.Entry<Integer,JSONObject> entry:pricedItinMap.entrySet()) {
				pricedItinJsonArr.put(entry.getValue());
				
			}
			resBodyJson.put(JSON_PROP_PRICEDITIN, pricedItinJsonArr);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			
		return resJson.toString();
	}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
	}

	private static void getFareRules(JSONObject reqHdrJson,JSONObject fareInfoJson, Element wrapprElem, Map<Integer, JSONArray> flightSegSeqMap) throws ValidationException {
		
		JSONObject fareRuleInfoJson=new JSONObject();
		
		JSONArray flightSegJsonArrMap=flightSegSeqMap.get(Integer.valueOf(XMLUtils.getFirstElementAtXPath(wrapprElem, "./air:Sequence").getTextContent()));
		fareRuleInfoJson.put(JSON_PROP_FLIGHTSEG, flightSegJsonArrMap);
		fareInfoJson.put("fareRuleInfo", fareRuleInfoJson);
		JSONObject fareRuleJson=new JSONObject();
		JSONArray  subSectionJsonArr=new JSONArray();
		
		Element[] subSectionElems=XMLUtils.getElementsAtXPath(wrapprElem,"./ota:OTA_AirRulesRS/ota:FareRuleResponseInfo/ota:FareRules/ota:SubSection");
		for(Element subSectionElem : subSectionElems)
		{	
			JSONObject subSectionJson=new JSONObject();
			JSONObject paragraphJson=new JSONObject();
		
			subSectionJson.put(JSON_PROP_SUBTITLE, (XMLUtils.getValueAtXPath(subSectionElem, "./@SubTitle")));
			paragraphJson.put(JSON_PROP_TEXT, (XMLUtils.getFirstElementAtXPath(subSectionElem, "./ota:Paragraph/ota:Text")).getTextContent());
			subSectionJson.put(JSON_PROP_PARAGRAPH, paragraphJson);
			subSectionJsonArr.put(subSectionJson);
			
			
		}
		if(subSectionJsonArr.length()==0)
		{
			throw new ValidationException(reqHdrJson,"TRLERR007");
		}
		else
		{
			fareRuleJson.put(JSON_PROP_SUBSECTION, subSectionJsonArr);
			fareInfoJson.put(JSON_PROP_FARERULE, fareRuleJson);
		}
		
		
	}

	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson, Map<Integer, JSONArray> flightSegSeqMap) throws ValidationException {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson, Map<Integer, JSONArray> flightSegSeqMap, Map<Integer, Integer> fareInfoToPricedItinMap) throws ValidationException {
		
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_AirRulesRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		int count=1;
		JSONArray pricedItinsJSONArr = reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
		for (int y=0; y < pricedItinsJSONArr.length(); y++) {
			JSONObject pricedItinJson = pricedItinsJSONArr.getJSONObject(y);
			String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
			
			JSONArray originDestOptJsonArr=pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY).optJSONArray(JSON_PROP_ORIGDESTOPTS);
			if(originDestOptJsonArr==null) {
				throw new ValidationException("TRLERR025");
			}
			for(int x=0;x<originDestOptJsonArr.length();x++)
			{
				JSONObject originDestOptJson=originDestOptJsonArr.getJSONObject(x);
				JSONArray flightSegJsonArr=originDestOptJson.getJSONArray(JSON_PROP_FLIGHTSEG);
				for(int z=0;z<flightSegJsonArr.length();z++)
				{
				//break loop here if fareinfo of flight segments remain the same
			
			JSONObject flightSegJson=flightSegJsonArr.getJSONObject(z);		
			
			JSONArray fareInfoJsonArr=flightSegJson.getJSONArray(JSON_PROP_FAREINFO);
			for(int i=0;i<fareInfoJsonArr.length();i++) {
				
			JSONObject fareInfoJson=fareInfoJsonArr.getJSONObject(i);
			
			removeAndAddFareInfo(flightSegJson,fareInfoJson,count,flightSegSeqMap);
			
			Element suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
			
			Element supplierIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:SupplierID");
			supplierIDElem.setTextContent(suppID);
			
			ProductSupplier prodSupplier=usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT,suppID);
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
			Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem, String.format("./com:SupplierCredentials[com:SupplierID = '%s']", suppID));
			if (suppCredsElem == null) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}
			fareInfoToPricedItinMap.put(count, y);
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:Sequence");
			sequenceElem.setTextContent(String.valueOf(count++));
			
			Element otaReqElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirRulesRQ");
			otaReqElem.setAttribute("TransactionIdentifier",pricedItinJson.optString(JSON_PROP_TRANSACTID));
			
			Element ruleReqInfoElem = ownerDoc.createElementNS(NS_OTA, "ota:RuleReqInfo");
			
			Element fareReferenceElem = ownerDoc.createElementNS(NS_OTA, "ota:FareReference");
			fareReferenceElem.setTextContent(fareInfoJson.getString(JSON_PROP_FAREREFERENCE));
			
			ruleReqInfoElem.appendChild(fareReferenceElem);
			
			Element marketingAirLineElem = ownerDoc.createElementNS(NS_OTA, "ota:MarketingAirline");
			marketingAirLineElem.setAttribute("Code", flightSegJson.getJSONObject(JSON_PROP_MARKAIRLINE).getString(JSON_PROP_AIRLINECODE));
			
			ruleReqInfoElem.appendChild(marketingAirLineElem);
			
			Element departureAirportElem = ownerDoc.createElementNS(NS_OTA, "ota:DepartureAirport");
			departureAirportElem.setAttribute("LocationCode", flightSegJson.getString(JSON_PROP_ORIGLOC));
			
			ruleReqInfoElem.appendChild(departureAirportElem);
			
			Element arrivalAirportElem = ownerDoc.createElementNS(NS_OTA, "ota:ArrivalAirport");
			arrivalAirportElem.setAttribute("LocationCode", flightSegJson.getString(JSON_PROP_DESTLOC));
			
			ruleReqInfoElem.appendChild(arrivalAirportElem);
			
			Element fareInfoElem = ownerDoc.createElementNS(NS_OTA, "ota:FareInfo");
			fareInfoElem.setAttribute("FareBasisCode", fareInfoJson.getString(JSON_PROP_FAREBASISCODE));
			
			if(Utils.isStringNotNullAndNotEmpty(flightSegJson.optString(JSON_PROP_RPH,"1"))) {
				fareInfoElem.setAttribute("RPH", flightSegJson.optString(JSON_PROP_RPH,"1"));
			}
			else{
				fareInfoElem.setAttribute("RPH", "1");
			}
			
			if(Utils.isStringNotNullAndNotEmpty(fareInfoJson.optString(JSON_PROP_FARETYPE))) {
				fareInfoElem.setAttribute("FareType", fareInfoJson.getString(JSON_PROP_FARETYPE));
			}
			fareInfoElem.setAttribute("FareBasisCode", fareInfoJson.getString(JSON_PROP_FAREBASISCODE));
			
			Element tpaEtxElem = ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
			
			Element quoteIDElem=ownerDoc.createElementNS(NS_AIR, "air:QuoteID");
			quoteIDElem.setTextContent(flightSegJson.getString(JSON_PROP_QUOTEID));
			tpaEtxElem.appendChild(quoteIDElem);
			
			Element extendedRphElem=ownerDoc.createElementNS(NS_AIR, "air:ExtendedRPH");
			extendedRphElem.setTextContent(flightSegJson.getString(JSON_PROP_EXTENDEDRPH));
			tpaEtxElem.appendChild(extendedRphElem);
			
			Element tripIndElem=ownerDoc.createElementNS(NS_AIR, "air:TripIndicator");
			tripIndElem.setTextContent(reqBodyJson.getString(JSON_PROP_TRIPIND));
			tpaEtxElem.appendChild(tripIndElem);
			
			fareInfoElem.appendChild(tpaEtxElem);
			
			ruleReqInfoElem.appendChild(fareInfoElem);	
			otaReqElem.appendChild(ruleReqInfoElem);
			
			
			}
				}
			}
			
		}
		
		
		return reqElem;
	}

	private static void removeAndAddFareInfo(JSONObject flightSegJson, JSONObject fareInfoJson, int sequenceNumber, Map<Integer, JSONArray> flightSegSeqMap) {
		
		JSONArray flightSegJsonArr=new JSONArray();
		//JSONTokener tokener=new JSONTokener(flightSegJson.toString());
		JSONObject flightSegMapJson=new JSONObject(new JSONTokener(flightSegJson.toString()));
		flightSegMapJson.remove(JSON_PROP_FAREINFO);
		
		JSONObject fareInfoMapJson=new JSONObject(new JSONTokener(fareInfoJson.toString()));
		JSONArray fareInfoMapJsonArr=new JSONArray();
		fareInfoMapJsonArr.put(fareInfoMapJson);
		flightSegMapJson.put(JSON_PROP_FAREINFO, fareInfoMapJsonArr);
		flightSegJsonArr.put(flightSegMapJson);
		flightSegSeqMap.put(sequenceNumber, flightSegJsonArr);
		
	}
	

}
