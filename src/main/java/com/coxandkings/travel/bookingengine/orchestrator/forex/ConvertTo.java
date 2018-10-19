package com.coxandkings.travel.bookingengine.orchestrator.forex;

import java.math.BigDecimal;

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
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class ConvertTo implements ForexConstants{

	private static final Logger logger = LogManager.getLogger(ConvertTo.class);
	
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
	       
        JSONArray convertToJsonArr = new JSONArray();
        
        Element[] wrapperElems=ConvertFrom.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem,
				"./for1:ResponseBody/for:OTA_ForeignExchangeConvertToRSWrapper"));
        for (Element wrapperElement : wrapperElems) {
        	Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_ForeignExchangeConvertToRS");
        	getConvertToJSONArr(resBodyElem, convertToJsonArr);
        }
        resBodyJson.put("convertTo", convertToJsonArr);
        
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		return resJson.toString();
	}

	private static void getConvertToJSONArr(Element resBodyElem, JSONArray convertToJsonArr) {

		JSONObject convertToJson = new JSONObject();
		convertToJson.put("terms", XMLUtils.getValueAtXPath(resBodyElem, "./ota:ConvertTo/ota:Terms"));
		convertToJson.put("privacy", XMLUtils.getValueAtXPath(resBodyElem, "./ota:ConvertTo/ota:Privacy"));
		convertToJson.put("to", XMLUtils.getValueAtXPath(resBodyElem, "./ota:ConvertTo/ota:To"));
		convertToJson.put("amount", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(resBodyElem, "./ota:ConvertTo/ota:Amount"),0));
		convertToJson.put("timeStamp", XMLUtils.getValueAtXPath(resBodyElem, "./ota:ConvertTo/ota:Timestamp"));
		
		JSONArray rateJsonArr = new JSONArray();
		Element[] rateElems = XMLUtils.getElementsAtXPath(resBodyElem, "./ota:ConvertTo//ota:From/ota:Rate");
		for (Element rate : rateElems) {
			getRateJSON(rate, rateJsonArr);
		}
		convertToJson.put("rates", rateJsonArr);
		convertToJsonArr.put(convertToJson);
		
		
	}

	private static void getRateJSON(Element rate, JSONArray rateJsonArr) {
		
		JSONObject rateJson = new JSONObject();
		rateJson.put("currency", XMLUtils.getValueAtXPath(rate, "./ota:Currency"));
		rateJson.put("mid", XMLUtils.getValueAtXPath(rate, "./ota:Mid"));
		rateJsonArr.put(rateJson);
	}
	private static void createSIRequest(Element reqElem, JSONObject reqHdrJson, JSONObject reqBodyJson,
			Document ownerDoc) {

		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);
		
//		UserContextV2 usrCtx = UserContextV2.getUserContextForSession(reqHdrJson);
//		List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PRODUCT_FOREIGN_EXCHG,PRODUCT_FOREIGN_EXCHG);

		ConvertFrom.createHeader(reqElem, sessionID, transactionID, userID);
		
//		int sequence = 1;
//		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
//				"./for:RequestHeader/com:SupplierCredentialsList");
//		for (ProductSupplier prodSupplier : prodSuppliers) {
//			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc,sequence++));
//		}
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./for1:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./for:OTA_ForeignExchangeConvertToRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		
		
		
		JSONArray convertToJSONArr = reqBodyJson.getJSONArray(JSON_PROP_CONVERT_TO);
		
		for(int i=0;i<convertToJSONArr.length();i++)
		{
			JSONObject convertToJson = convertToJSONArr.getJSONObject(i);
			
			Element suppWrapperElem = null;
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
			
			Element otaConvertTo = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_ForeignExchangeConvertToRQ");
			XMLUtils.setValueAtXPath(suppWrapperElem, "./for:SupplierID", convertToJson.getString("supplierRef"));
			XMLUtils.setValueAtXPath(suppWrapperElem, "./for:Sequence", String.valueOf(i));
			
			JSONArray fromJsonArr = convertToJson.getJSONArray(JSON_PROP_FROM);
			for(int j=0;j<fromJsonArr.length();j++)
			{
				if(Utils.isStringNotNullAndNotEmpty(fromJsonArr.getString(j)))
				{
					 Element newElem = ownerDoc.createElementNS(NS_OTA, "From");
					  newElem.setTextContent(fromJsonArr.getString(j));
					  otaConvertTo.appendChild(newElem);
				}
			}
			if(convertToJson.get(JSON_PROP_TO).toString().isEmpty()==false)
			{
				  Element newElem = ownerDoc.createElementNS(NS_OTA, "To");
				  newElem.setTextContent(convertToJson.getString(JSON_PROP_TO));
				  otaConvertTo.appendChild(newElem);
			}
			
			if(convertToJson.get(JSON_PROP_AMT).toString().isEmpty()==false)
			{
				  Element newElem = ownerDoc.createElementNS(NS_OTA, "Amount");
				  newElem.setTextContent(convertToJson.optBigDecimal(JSON_PROP_AMT,BigDecimal.ZERO).toString());
				  otaConvertTo.appendChild(newElem);
			}
			if(convertToJson.get(JSON_PROP_OBS).toString().isEmpty()==false)
			{
				  Element newElem = ownerDoc.createElementNS(NS_OTA, "Obsolete");
				  newElem.setTextContent(convertToJson.getString(JSON_PROP_OBS));
				  otaConvertTo.appendChild(newElem);
			}
			if(convertToJson.get(JSON_PROP_INV).toString().isEmpty()==false)
			{
				  Element newElem = ownerDoc.createElementNS(NS_OTA, "Inverse");
				  newElem.setTextContent(convertToJson.getString(JSON_PROP_INV));
				  otaConvertTo.appendChild(newElem);
			} 
			if(convertToJson.get(JSON_PROP_DEC_PLACES).toString().isEmpty()==false)
			{
				  Element newElem = ownerDoc.createElementNS(NS_OTA, "DecimalPlaces");
				  newElem.setTextContent(convertToJson.get(JSON_PROP_DEC_PLACES).toString());
				  otaConvertTo.appendChild(newElem);
			}
			
		}
	}
}
