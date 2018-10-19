package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.OperationsShellConfig;
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

public class CruiseCancelProcessor implements CruiseConstants {

	
private static final Logger logger = LogManager.getLogger(CruiseSearchProcessor.class);
	
	public static String process(JSONObject reqJson) throws Exception
	{
		KafkaBookProducer cancelProducer = new KafkaBookProducer();
		
		//OperationConfig opConfig = CruiseConfig.getOperationConfig("cancel");
		ServiceConfig opConfig = CruiseConfig.getOperationConfig("cancel");
		//Element reqElem = (Element) mXMLPriceShellElem.cloneNode(true);
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		//logger.info(String.format("Read Reprice Verify XML request template: %s\n", XMLTransformer.toEscapedString(reqElem)));
		Document ownerDoc = reqElem.getOwnerDocument();
		
		JSONObject kafkaCancelJson = new JSONObject();
		kafkaCancelJson = reqJson;
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./cru1:OTA_CancelRQWrapper");
		reqBodyElem.removeChild(wrapperElem);

		TrackingContext.setTrackingContext(reqJson);
		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		
		CruiseSearchProcessor.createHeader(reqElem, reqHdrJson);
		
		Element suppWrapperElem = null;
		int seqItr =0;
//		JSONArray cruisePriceDetailsArr = reqBodyJson.getJSONArray("cancelRequests");
		
//		for(int i=0;i<cruisePriceDetailsArr.length();i++)
		
			JSONObject supplierBody = reqBodyJson.getJSONObject("cancelRequests");
			
			String suppID =	supplierBody.getString("supplierRef");
			
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
			
			//Request Header starts
			ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, "Cruise", suppID);
			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}
			seqItr++;
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru1:RequestHeader/com:SupplierCredentialsList");
//------------------------------------------------------------------------------------------------------------------------------------------------
			Element supplierCredentials = prodSupplier.toElement(ownerDoc);
			
//			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
//			Request Body starts
				
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
			
			Element otaCategoryAvail = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_CancelRQ");
			CruisePriceProcessor.createPOS(ownerDoc, otaCategoryAvail);
			
			Element reqVerificationElem =	XMLUtils.getFirstElementAtXPath(otaCategoryAvail, "./ota:Verification");
			
//			JSONArray reqUniqueIdJsonArr = supplierBody.getJSONArray("uniqueID");
//			for(int j=0;j<reqUniqueIdJsonArr.length();j++)
//			{
			JSONObject uniqueIdJson = supplierBody.getJSONObject("reservationID");
			
			Element itineraryIDElem = ownerDoc.createElementNS(JSON_PROP_SUPPLCRUISE, "ItineraryID");
			itineraryIDElem.setTextContent(uniqueIdJson.getString("itineraryID"));
			
			Element tpa_ExtensionsElem = ownerDoc.createElementNS(NS_OTA, "TPA_Extensions");
			tpa_ExtensionsElem.appendChild(itineraryIDElem);
			
			Element uniqueIdElem =	ownerDoc.createElementNS(NS_OTA, "UniqueID");
			uniqueIdElem.setAttribute("ID", uniqueIdJson.getString("id"));
			uniqueIdElem.setAttribute("Type", uniqueIdJson.getString("type"));
			uniqueIdElem.appendChild(tpa_ExtensionsElem);
			
			otaCategoryAvail.insertBefore(uniqueIdElem, reqVerificationElem);
//			}
			
			JSONObject reqPersonJson = supplierBody.getJSONObject("verification").getJSONObject("personName");
			Element personNameElem = XMLUtils.getFirstElementAtXPath(reqVerificationElem, "./ota:PersonName");
			
			Element firstNameElem =	XMLUtils.getFirstElementAtXPath(personNameElem, "./ota:GivenName");
			firstNameElem.setTextContent(reqPersonJson.getString("givenName"));
			
			Element middleNameElem = XMLUtils.getFirstElementAtXPath(personNameElem, "./ota:MiddleName");
			middleNameElem.setTextContent(reqPersonJson.getString("middleName"));
			
			Element surNameElem = XMLUtils.getFirstElementAtXPath(personNameElem, "./ota:Surname");
			surNameElem.setTextContent(reqPersonJson.getString("surname"));
			
			JSONArray reqCancellationOverridesArr = supplierBody.getJSONArray("cancellationOverrides");
			Element cancellationOverridesElem =	XMLUtils.getFirstElementAtXPath(otaCategoryAvail, "./ota:CancellationOverrides");
			
			for(int j=0;j<reqCancellationOverridesArr.length();j++)
			{
				JSONObject reqCancellationOverridesJson = reqCancellationOverridesArr.getJSONObject(j);
				
				Element cancellationOverrideElem = ownerDoc.createElementNS(NS_OTA, "CancellationOverride");
				cancellationOverrideElem.setAttribute("CancelByDate", reqCancellationOverridesJson.getString("cancelByDate"));
				cancellationOverridesElem.appendChild(cancellationOverrideElem);
			}
		
		
		//System.out.println(XMLTransformer.toString(reqElem));
		
		Element resElem = null;
        //resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), CruiseConfig.getHttpHeaders(), reqElem);
		resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
		 OperationsToDoProcessor.callOperationTodo(ToDoTaskName.CANCEL, ToDoTaskPriority.MEDIUM,ToDoTaskSubType.ORDER,supplierBody.optString("entityId"), reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
        if (resElem == null) {
        	throw new Exception("Null response received from SI");
        }
       
        
        cancelProducer.runProducer(1, kafkaCancelJson);
        //System.out.println(XMLTransformer.toString(resElem));
        
        //Cancel DB Population
        //System.out.println(kafkaCancelJson.toString());
        
        JSONObject resBodyJson = new JSONObject();
        JSONArray cruiseRepriceDetailsJsonArr = new JSONArray();
        Element[] otaCategoryAvailWrapperElems = XMLUtils.getElementsAtXPath(resElem, "./cru:ResponseBody/cru1:OTA_CancelRSWrapper");
        for(Element otaCategoryAvailWrapperElem : otaCategoryAvailWrapperElems)
        {
        	
        	if(XMLUtils.getFirstElementAtXPath(otaCategoryAvailWrapperElem, "./cru1:ErrorList")!=null)
        	{	
        		
        		Element errorMessage=XMLUtils.getFirstElementAtXPath(otaCategoryAvailWrapperElem, "./cru1:ErrorList/com:Error/com:ErrorCode");
        		String errMsgStr=errorMessage.getTextContent().toString();
        		if(CANCEL_SI_ERR.equalsIgnoreCase(errMsgStr))
        		{
        			logger.error("This service is not supported. Kindly contact our operations team for support.");
        			return getSIErrorResponse(reqHdrJson).toString();
        		}
        	}
        	getSupplierResponseJSON(otaCategoryAvailWrapperElem,cruiseRepriceDetailsJsonArr);
        }
        
        //System.out.println(cruiseRepriceDetailsJsonArr.toString());
        
        resBodyJson.put("cancel", cruiseRepriceDetailsJsonArr);
        
        kafkaCancelJson = new JSONObject();
        JSONObject kafkaRsBdyJson = cruiseRepriceDetailsJsonArr.getJSONObject(0);
        
        kafkaRsBdyJson.put("operation", "cancel");
        kafkaRsBdyJson.put(JSON_PROP_PROD, PRODUCT_CRUISE);
        kafkaRsBdyJson.put("entityName", "");
        kafkaRsBdyJson.put("entityId", reqJson.getJSONObject("requestBody").getJSONObject("cancelRequests").getString("entityId"));
        kafkaRsBdyJson.put("requestType", reqJson.getJSONObject("requestBody").getJSONObject("cancelRequests").getString("requestType"));
        kafkaRsBdyJson.put("type", reqJson.getJSONObject("requestBody").getJSONObject("cancelRequests").getString("type"));
        
        kafkaCancelJson.put("responseHeader", reqJson.getJSONObject("requestHeader"));
        kafkaCancelJson.put("responseBody", kafkaRsBdyJson);
        
        cancelProducer.runProducer(1, kafkaCancelJson);
        System.out.println(kafkaCancelJson);
        JSONObject resJson = new JSONObject();
//        cancelProducer.runProducer(1, kafkaCancelJson);
//        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, resBodyJson);
        
		return resJson.toString();
	}
	
	public static void getSupplierResponseJSON(Element otaCategoryAvailWrapperElem,JSONArray cruiseRepriceDetailsJsonArr) {
		
		JSONObject cancelRsJson = new JSONObject();
		
		String suppID =	XMLUtils.getValueAtXPath(otaCategoryAvailWrapperElem, "./cru1:SupplierID");
		cancelRsJson.put("supplierRef", suppID);
		
		Element otaCancelRsElem = XMLUtils.getFirstElementAtXPath(otaCategoryAvailWrapperElem, "./ota:OTA_CancelRS");
		
		Element[] uniqueIDElems = XMLUtils.getElementsAtXPath(otaCancelRsElem, "./ota:UniqueID");
		if(uniqueIDElems!=null)
		{
			JSONArray uniqueIDJson = getUniqueIDJsonArr(uniqueIDElems);
			cancelRsJson.put("uniqueID", uniqueIDJson);
		}
		
		Element cancelInfoRsElem =	XMLUtils.getFirstElementAtXPath(otaCancelRsElem, "./ota:CancelInfoRS");
		if(cancelInfoRsElem!=null)
		{
			JSONObject cancelInfoRsJson = getcancelInfoRsJson(cancelInfoRsElem);
			cancelRsJson.put("cancelInfo", cancelInfoRsJson);
		}
		
		Element errorsRsElem = XMLUtils.getFirstElementAtXPath(otaCancelRsElem, "./ota:Errors");
		if(errorsRsElem!=null)
		{
			JSONArray errorsJsonArr = getErrorJsonArr(errorsRsElem);
			cancelRsJson.put("errorMsg", errorsJsonArr);
		}
		
		cruiseRepriceDetailsJsonArr.put(cancelRsJson);
	}
	
	private static JSONArray getErrorJsonArr(Element errorsRsElem)
 	{
		JSONArray errorsJsonArr = new JSONArray();
		Element[] errorElems =	XMLUtils.getElementsAtXPath(errorsRsElem, "./ota:Error");
		
		for(Element errorElem : errorElems)
		{
			JSONObject errorJson = new JSONObject();
			errorJson.put("language", XMLUtils.getValueAtXPath(errorElem, "./@Language"));
			errorJson.put("type", XMLUtils.getValueAtXPath(errorElem, "./@Type"));
			errorJson.put("shortText", XMLUtils.getValueAtXPath(errorElem, "./@ShortText"));
			errorJson.put("code", XMLUtils.getValueAtXPath(errorElem, "./@Code"));
			errorJson.put("docURL", XMLUtils.getValueAtXPath(errorElem, "./@DocURL"));
			errorJson.put("status", XMLUtils.getValueAtXPath(errorElem, "./@Status"));
			errorJson.put("tag", XMLUtils.getValueAtXPath(errorElem, "./@Tag"));
			errorJson.put("recordID", XMLUtils.getValueAtXPath(errorElem, "./@RecordID"));
			errorJson.put("nodeList", XMLUtils.getValueAtXPath(errorElem, "./@NodeList"));
			
			errorsJsonArr.put(errorJson);
		}
		return errorsJsonArr;
 	}
	
	private static JSONObject getcancelInfoRsJson(Element cancelInfoRsElem)
 	{
		JSONObject cancelInfoRsJson = new JSONObject();
		
		Element[] cancelRuleElems =	XMLUtils.getElementsAtXPath(cancelInfoRsElem, "./ota:CancelRules/ota:CancelRule");
		if(cancelRuleElems!=null)
		{
			JSONArray cancelRuleJsonArr = getCancelRulesJsonArr(cancelRuleElems);
			cancelInfoRsJson.put("cancelRules", cancelRuleJsonArr);
		}
		
		Element[] cancelUniqueIDElems =	XMLUtils.getElementsAtXPath(cancelInfoRsElem, "./ota:UniqueID");
		if(cancelUniqueIDElems!=null)
		{
			JSONArray cancelUniqueIDJsonArr = getCancelUniqueIDJsonArr(cancelUniqueIDElems);
			cancelInfoRsJson.put("uniqueID", cancelUniqueIDJsonArr);
		}
		
		return cancelInfoRsJson;
 	}
	
	private static JSONArray getCancelUniqueIDJsonArr(Element[] cancelUniqueIDElems)
 	{
		JSONArray cancelUniqueIDJsonArr = new JSONArray();
		
		for(Element cancelUniqueIDElem : cancelUniqueIDElems)
		{
			JSONObject cancelUniqueIDJson = new JSONObject();
			
			cancelUniqueIDJson.put("instance", XMLUtils.getValueAtXPath(cancelUniqueIDElem, "./@Instance"));
			cancelUniqueIDJson.put("id", XMLUtils.getValueAtXPath(cancelUniqueIDElem, "./@ID"));
			
			cancelUniqueIDJsonArr.put(cancelUniqueIDJson);
		}
		return cancelUniqueIDJsonArr;
 	}
	
	private static JSONArray getCancelRulesJsonArr(Element[] cancelRuleElems)
 	{
		JSONArray cancelRuleJsonArr = new JSONArray();
		
		for(Element cancelRuleElem : cancelRuleElems)
		{
			JSONObject cancelRuleJson = new JSONObject();
			
			cancelRuleJson.put("type", XMLUtils.getValueAtXPath(cancelRuleElem, "./@Type"));
			cancelRuleJson.put("amount", XMLUtils.getValueAtXPath(cancelRuleElem, "./@Amount"));
			cancelRuleJson.put("currencyCode", XMLUtils.getValueAtXPath(cancelRuleElem, "./@CurrencyCode"));
			
			cancelRuleJsonArr.put(cancelRuleJson);
		}
		return cancelRuleJsonArr;
 	}
	
	private static JSONArray getUniqueIDJsonArr(Element[] uniqueIDElems)
 	{
		JSONArray uniqueIDJsonArr = new JSONArray();
		for(Element uniqueIDElem : uniqueIDElems)
		{
			JSONObject uniqueIDJson = new JSONObject();
			
			uniqueIDJson.put("id", XMLUtils.getValueAtXPath(uniqueIDElem, "./@ID"));
			uniqueIDJson.put("type", XMLUtils.getValueAtXPath(uniqueIDElem, "./@Type"));
			uniqueIDJson.put("instance", XMLUtils.getValueAtXPath(uniqueIDElem, "./@Instance"));
			
			uniqueIDJsonArr.put(uniqueIDJson);
		}
		return uniqueIDJsonArr;
 	}
	
	
	private static JSONObject getSIErrorResponse(JSONObject resJson) {
		
		JSONObject errorMessage=new JSONObject();
		
		errorMessage.put("errorMessage", "This service is not supported. Kindly contact our operations team for support.");
		 
		resJson.put(JSON_PROP_RESBODY, errorMessage);
        
		return resJson;
		
	}
	
}
