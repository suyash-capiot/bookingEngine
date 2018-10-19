package com.coxandkings.travel.bookingengine.orchestrator.activities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class ActivityGetPoliciesProcessor implements ActivityConstants{
	
	private static final Logger logger = LogManager.getLogger(ActivityGetPoliciesProcessor.class);
	static final String OPERATION_NAME = "getPolicies";
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		Element reqElem = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		try {
		    opConfig = ActivitiesConfig.getOperationConfig(OPERATION_NAME);
		    reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		    
		    setSupplierRequestElem(reqJson, reqElem);
		    
		    
		} catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		try {
			//Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AccoConfig.getHttpHeaders(), reqElem);
			Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			JSONObject resJson = getSupplierResponseJSON(reqJson, resElem);
			return resJson.toString() ;

		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}

		
	}
	
	private static void setSupplierRequestElem(JSONObject reqJson, Element reqElem) throws Exception {
		
		Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem, "./sig:RequestBody/sig1:OTA_TourActivityCancellationPoliciesRQWrapper");
	    XMLUtils.removeNode(blankWrapperElem);
	    
	    Document ownerDoc = reqElem.getOwnerDocument();
		JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null) ? reqJson.optJSONObject(JSON_PROP_REQHEADER) : new JSONObject();
		JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null) ? reqJson.optJSONObject(JSON_PROP_REQBODY) : new JSONObject();

		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);
		
		JSONObject clientContext=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientMrkt = clientContext.getString(JSON_PROP_CLIENTMARKET);
		String clientID = clientContext.getString(JSON_PROP_CLIENTID);

		createSuppReqHdrElem(XMLUtils.getFirstElementAtXPath(reqElem, ".sig1:RequestHeader"),sessionID,transactionID,userID,clientMrkt,clientID);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		
		JSONArray multiReqArr = reqBodyJson.getJSONArray("reservations");
		Element wrapperElement,suppCredsListElem= XMLUtils.getFirstElementAtXPath(reqElem, "./sig1:RequestHeader/com:SupplierCredentialsList");
		JSONObject reservationObjectJson;
		String suppID;
		ProductSupplier prodSupplier;
		
		for (int j=0; j < multiReqArr.length(); j++) {
			reservationObjectJson = multiReqArr.getJSONObject(j);
			suppID = reservationObjectJson.getString("supplierID");
			prodSupplier = usrCtx.getSupplierForProduct(PRODUCT_CATEGORY, PRODUCT_SUBCATEGORY, suppID);
			
			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}
			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc,j));
			wrapperElement= (Element) blankWrapperElem.cloneNode(true);
			
			XMLUtils.setValueAtXPath(wrapperElement, "./sig1:SupplierID",suppID);
			XMLUtils.setValueAtXPath(wrapperElement, "./sig1:Sequence", String.valueOf(j));
			setSuppReqOTAElem(ownerDoc, XMLUtils.getFirstElementAtXPath(wrapperElement,"./ns:OTA_TourActivityCancellationPoliciesRQ/ns:CancellationPolicies"), reservationObjectJson,reqHdrJson);
			XMLUtils.insertChildNode(reqElem, "./sig:RequestBody", wrapperElement, false);
		}
		
	}
	
	private static JSONObject getSupplierResponseJSON(JSONObject reqJson, Element resElem) {
		JSONObject resJson = new JSONObject();
		JSONObject resBodyJson = new JSONObject();
		
		JSONArray reservationsArr = new JSONArray();
		JSONArray cancellationPolicyArr = new JSONArray();
		
		Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem, "./sig:ResponseBody/sig1:OTA_TourActivityCancellationPoliciesRSWrapper");
		
		for(int i = 0; i<wrapperElems.length; i++) {
			Element wrapperElem = wrapperElems[i];
			
			
			JSONObject reservationsJson = new JSONObject();
			reservationsJson.put("supplierID", XMLUtils.getValueAtXPath(wrapperElem, "./sig1:SupplierID"));
			
			Element[] cancellationPoliciesElemArr = XMLUtils.getElementsAtXPath(wrapperElem, "./ns:OTA_TourActivityCancellationPoliciesRS/ns:CancellationPolicies");
			
			for(int j = 0; j<cancellationPoliciesElemArr.length; j++) {
				Element cancellationPoliciesElem = cancellationPoliciesElemArr[j];
				
				JSONObject cancellationPolicyJson = new JSONObject();
				
				cancellationPolicyJson.put("fromValue", XMLUtils.getValueAtXPath(cancellationPoliciesElem, "./ns:FromValue"));
				cancellationPolicyJson.put("toValue", XMLUtils.getValueAtXPath(cancellationPoliciesElem, "./ns:ToValue"));
				cancellationPolicyJson.put("currencyCode", XMLUtils.getValueAtXPath(cancellationPoliciesElem, "./ns:CurrencyCode"));
				cancellationPolicyJson.put("supplierProductCode", XMLUtils.getValueAtXPath(cancellationPoliciesElem, "./ns:SupplierProductCode"));
				cancellationPolicyJson.put("referenceCode", XMLUtils.getValueAtXPath(cancellationPoliciesElem, "./ns:ReferenceCode"));
				cancellationPolicyJson.put("rate", XMLUtils.getValueAtXPath(cancellationPoliciesElem, "./ns:Rate"));
				cancellationPolicyJson.put("text", XMLUtils.getValueAtXPath(cancellationPoliciesElem, "./ns:Text"));
				cancellationPolicyJson.put("unit", XMLUtils.getValueAtXPath(cancellationPoliciesElem, "./ns:Unit"));
				cancellationPolicyJson.put("chargeType", XMLUtils.getValueAtXPath(cancellationPoliciesElem, "./ns:ChargeType"));
				
				cancellationPolicyArr.put(cancellationPolicyJson);
			}
			
			//Appending Policies in Req here for now 18-07-18
			reqJson.getJSONObject("requestBody").getJSONArray("reservations").getJSONObject(i).getJSONObject("basicInfo").put("cancellationPolicy", cancellationPolicyArr);
			
			reservationsJson.put("policies", cancellationPolicyArr);
			reservationsArr.put(reservationsJson);
		}
		resBodyJson.put("reservations", reservationsArr);
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
        return resJson;
		
	}
	
	private static void createSuppReqHdrElem(Element reqHdrElem, String sessionId, String transactId, String userId,String clientMrkt,String clientID) {
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:SessionID",sessionId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:TransactionID",transactId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:UserID",userId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:ClientID",clientID);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:MarketID",clientMrkt);
	}
	
	private static void setSuppReqOTAElem(Document ownerDoc, Element reqOTAElem, JSONObject reservationObjectJson,
			JSONObject reqHdrJson) {
		Element countryCode = ownerDoc.createElementNS(NS_OTA, "ns:CountryCode");
		countryCode.setTextContent(reservationObjectJson.getString("countryCode"));
		reqOTAElem.appendChild(countryCode);
		
		Element  cityCode = ownerDoc.createElementNS(NS_OTA, "ns:CityCode");
		cityCode.setTextContent(reservationObjectJson.getString("cityCode"));
		reqOTAElem.appendChild(cityCode);
		
		setParticipantCount(ownerDoc, reqOTAElem, reservationObjectJson.getJSONArray("participantInfo"), reqHdrJson);
		
		Element fromDate = ownerDoc.createElementNS(NS_OTA, "ns:FromDate");
		fromDate.setTextContent(reservationObjectJson.getJSONObject("schedule").getString("start").substring(0,10));
		reqOTAElem.appendChild(fromDate);
		
		Element  endDate = ownerDoc.createElementNS(NS_OTA, "ns:EndDate");
		endDate.setTextContent(reservationObjectJson.getJSONObject("schedule").getString("end").substring(0,10));
		reqOTAElem.appendChild(endDate);
		
		Element  currencyCode = ownerDoc.createElementNS(NS_OTA, "ns:CurrencyCode");
		currencyCode.setTextContent(reservationObjectJson.getJSONObject("activityTotalPricingInfo").getJSONObject("activitySummaryPrice").getString("currencyCode"));
		reqOTAElem.appendChild(currencyCode);
		
		Element  supplierProductCode = ownerDoc.createElementNS(NS_OTA, "ns:SupplierProductCode");
		supplierProductCode.setTextContent(reservationObjectJson.getJSONObject("basicInfo").getString("supplierProductCode"));
		reqOTAElem.appendChild(supplierProductCode);
		
		Element  referenceCode = ownerDoc.createElementNS(NS_OTA, "ns:ReferenceCode");
		referenceCode.setTextContent(reservationObjectJson.getJSONObject("basicInfo").getJSONObject("supplier_Details").getString("reference"));
		reqOTAElem.appendChild(referenceCode);
	}
	
	private static void setParticipantCount(Document ownerDoc, Element reqOTAElem, JSONArray participantInfoArr,
			JSONObject reqHdrJson) {
		
		
		for(int i=0; i<participantInfoArr.length(); i++) {
			
			JSONObject particiapntInfo = participantInfoArr.getJSONObject(i);
			Element participantCount = ownerDoc.createElementNS(NS_OTA, "ns:ParticipantCount");
			participantCount.setAttribute("Quantity", "1");
			participantCount.setAttribute("Age", ActivityBookProcessor.calculateAge(particiapntInfo.getString("DOB")));
			
			Element qualifierInfo = ownerDoc.createElementNS(NS_OTA, "ns:QualifierInfo");
			qualifierInfo.setTextContent(ActivityPassengerType.valueOf(particiapntInfo.getString("qualifierInfo")).toString());
			
			participantCount.appendChild(qualifierInfo);
			reqOTAElem.appendChild(participantCount);
		}
		
	}
}
































