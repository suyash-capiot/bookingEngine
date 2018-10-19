package com.coxandkings.travel.bookingengine.orchestrator.forex;



import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.forex.ForexConfig;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class Currencies implements ForexConstants{

	private static final Logger logger = LogManager.getLogger(ConvertFrom.class);
	
	public static String process(JSONObject reqJson) throws RequestProcessingException
	{
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		try
		{
			opConfig = ForexConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true); // xmshell
			Document ownerDoc = reqElem.getOwnerDocument();
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			 
			createSIRequest(reqElem, reqHdrJson, reqBodyJson, ownerDoc);
		}
		catch(Exception e)
		{
			logger.error("Exception during convertFrom request processing", e);
			throw new RequestProcessingException(e);
		}
		Element resElem = null;
		
		try {
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), ForexConfig.getmHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (resElem == null) {
			logger.info("Null response received from SI ");
			
		}
		JSONObject resBodyJson = new JSONObject();
	       
        JSONArray currenciesJsonArr = new JSONArray();
        
        Element[] wrapperElems=ConvertFrom.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem,
				"./for1:ResponseBody/for:OTA_ForeignExchangeCurrenciesRSWrapper"));
        for (Element wrapperElement : wrapperElems) {
        	Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_ForeignExchangeCurrenciesRS");
        	getCurrenciesJSONArr(resBodyElem, currenciesJsonArr);
        }
        resBodyJson.put(JSON_PROP_CCYS, currenciesJsonArr);
        
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		return resJson.toString();
		
	}

	private static void getCurrenciesJSONArr(Element resBodyElem, JSONArray currenciesJsonArr) {

		JSONObject currencyJson = new JSONObject();
		currencyJson.put("success", XMLUtils.getValueAtXPath(resBodyElem, "./ota:Success"));
		currencyJson.put("terms", XMLUtils.getValueAtXPath(resBodyElem, "./ota:Currencies/ota:Terms"));
		currencyJson.put("privacy", XMLUtils.getValueAtXPath(resBodyElem, "./ota:Currencies/ota:Privacy"));
		Element[] currencyElems = XMLUtils.getElementsAtXPath(resBodyElem, "./ota:Currencies/ota:Currencies/ota:Currency");
		JSONArray currencyJsonArr = new JSONArray();
		for (Element ccyElem : currencyElems) {
			getCcyDetailsJSON(ccyElem, currencyJsonArr);
		}
		currencyJson.put(JSON_PROP_CCY_DTLS, currencyJsonArr);
		currenciesJsonArr.put(currencyJson);
		
	}

	private static void getCcyDetailsJSON(Element ccyElem, JSONArray currencyJsonArr) {
		
		JSONObject ccyDtlsJson = new JSONObject();
		ccyDtlsJson.put(JSON_PROP_CCY_CODE, XMLUtils.getValueAtXPath(ccyElem, "./ota:CrrencyCode"));
		ccyDtlsJson.put(JSON_PROP_CCY_NAME, XMLUtils.getValueAtXPath(ccyElem, "./ota:CurrencyName"));
		ccyDtlsJson.put(JSON_PROP_OBS, XMLUtils.getValueAtXPath(ccyElem, "./ota:Obsolete"));
		currencyJsonArr.put(ccyDtlsJson);
		
	}

	private static void createSIRequest(Element reqElem, JSONObject reqHdrJson, JSONObject reqBodyJson,
			Document ownerDoc) {

		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);
		
//		UserContextV2 usrCtx = UserContextV2.getUserContextForSession(reqHdrJson);
//		List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PRODUCT_FOREIGN_EXCHG,PRODUCT_FOREIGN_EXCHG);
		
		ConvertFrom.createHeader(reqElem, sessionID, transactionID, userID);
		
		int sequence = 1;
//		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
//				"./bus:RequestHeader/com:SupplierCredentialsList");
//		for (ProductSupplier prodSupplier : prodSuppliers) {
//			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc,sequence++));
//		}
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./for1:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./for:OTA_ForeignExchangeCurrenciesRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		
		
		
		
		JSONArray currenciesJSONArr = reqBodyJson.getJSONArray(JSON_PROP_CCYS);
		
		for(int i=0;i<currenciesJSONArr.length();i++)
		{
			JSONObject currencyJson = currenciesJSONArr.getJSONObject(i);
			Element suppWrapperElem = null;
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
			
			Element otaConvertCcy = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_ForeignExchangeCurrenciesRQ");
			XMLUtils.setValueAtXPath(suppWrapperElem, "./for:SupplierID", currencyJson.getString("supplierRef"));
			XMLUtils.setValueAtXPath(suppWrapperElem, "./for:Sequence", String.valueOf(i));
			
			
			Element newElem = ownerDoc.createElementNS(NS_OTA, "Language");
			newElem.setTextContent(currencyJson.getString(JSON_PROP_LANG));
			otaConvertCcy.appendChild(newElem);
		
		
			newElem = ownerDoc.createElementNS(NS_OTA, "ISO");
			newElem.setTextContent(currencyJson.optString(JSON_PROP_ISO));
			otaConvertCcy.appendChild(newElem);
			
//			newElem = ownerDoc.createElementNS(NS_OTA, "Obsolete");
//			newElem.setTextContent(currencyJson.optString(JSON_PROP_OBS));
//			otaConvertFrom.appendChild(newElem);
		
		
			
			
		}
		
		
	}
}
