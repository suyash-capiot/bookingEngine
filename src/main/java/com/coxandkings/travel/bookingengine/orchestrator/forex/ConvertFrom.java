package com.coxandkings.travel.bookingengine.orchestrator.forex;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

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



public class ConvertFrom implements ForexConstants{
	
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
	       
        JSONArray convertFromJsonArr = new JSONArray();
        
        Element[] wrapperElems=sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem,
				"./for1:ResponseBody/for:OTA_ForeignExchangeConvertFromRSWrapper"));
        for (Element wrapperElement : wrapperElems) {
        	Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_ForeignExchangeConvertFromRS");
        	getConvertFromJSONArr(resBodyElem, convertFromJsonArr);
        }
        resBodyJson.put("convertFrom", convertFromJsonArr);
        
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		return resJson.toString();
		
	}

	private static void getConvertFromJSONArr(Element resBodyElem, JSONArray convertFromJsonArr) {
		
		JSONObject convertFromJson = new JSONObject();
		convertFromJson.put("terms", XMLUtils.getValueAtXPath(resBodyElem, "./ota:ConvertFrom/ota:Terms"));
		convertFromJson.put("privacy", XMLUtils.getValueAtXPath(resBodyElem, "./ota:ConvertFrom/ota:Privacy"));
		convertFromJson.put("from", XMLUtils.getValueAtXPath(resBodyElem, "./ota:ConvertFrom/ota:From"));
		convertFromJson.put("amount", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(resBodyElem, "./ota:ConvertFrom/ota:Amount"),0));
		convertFromJson.put("timeStamp", XMLUtils.getValueAtXPath(resBodyElem, "./ota:ConvertFrom/ota:Timestamp"));
		
		JSONArray rateJsonArr = new JSONArray();
		Element[] rateElems = XMLUtils.getElementsAtXPath(resBodyElem, "./ota:ConvertFrom/ota:To/ota:Rate");
		for (Element rate : rateElems) {
			getRateJSON(rate, rateJsonArr);
		}
		convertFromJson.put("rates", rateJsonArr);
		convertFromJsonArr.put(convertFromJson);
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
		
//		UserContextAPI usrCtx = UserContextAPI.getUserContextForSession(reqHdrJson);
//		List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PRODUCT_FOREIGN_EXCHG,PRODUCT_FOREIGN_EXCHG);

		createHeader(reqElem, sessionID, transactionID, userID);
		
		int sequence = 1;
//		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
//				"./bus:RequestHeader/com:SupplierCredentialsList");
//		for (ProductSupplier prodSupplier : prodSuppliers) {
//			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc,sequence++));
//		}
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./for1:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./for:OTA_ForeignExchangeConvertFromRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		
		
		
		JSONArray convertFromJSONArr = reqBodyJson.getJSONArray(JSON_PROP_CONVERT_FROM);
		for(int i=0;i<convertFromJSONArr.length();i++)
		{
			JSONObject convertFromJson = convertFromJSONArr.getJSONObject(i);
			Element suppWrapperElem = null;
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
			Element otaConvertFrom = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_ForeignExchangeConvertFromRQ");
			XMLUtils.setValueAtXPath(suppWrapperElem, "./for:SupplierID", convertFromJson.getString("supplierRef"));
			XMLUtils.setValueAtXPath(suppWrapperElem, "./for:Sequence", String.valueOf(i));
			
			if(convertFromJson.get(JSON_PROP_FROM).toString().isEmpty()==false)
			{
				  Element newElem = ownerDoc.createElementNS(NS_OTA, "From");
				  newElem.setTextContent(convertFromJson.getString(JSON_PROP_FROM));
				  otaConvertFrom.appendChild(newElem);
			}
			JSONArray toJsonArr = convertFromJson.getJSONArray(JSON_PROP_TO);
			for(int j=0;j<toJsonArr.length();j++)
			{
				if(Utils.isStringNotNullAndNotEmpty(toJsonArr.getString(j)))
				{
					 Element newElem = ownerDoc.createElementNS(NS_OTA, "To");
					  newElem.setTextContent(toJsonArr.getString(j));
					  otaConvertFrom.appendChild(newElem);
				}
			}
			if(convertFromJson.get(JSON_PROP_AMT).toString().isEmpty()==false)
			{
				  Element newElem = ownerDoc.createElementNS(NS_OTA, "Amount");
				  newElem.setTextContent(convertFromJson.optBigDecimal(JSON_PROP_AMT,BigDecimal.ZERO).toString());
				  otaConvertFrom.appendChild(newElem);
			}
			if(convertFromJson.get(JSON_PROP_OBS).toString().isEmpty()==false)
			{
				  Element newElem = ownerDoc.createElementNS(NS_OTA, "Obsolete");
				  newElem.setTextContent(convertFromJson.getString(JSON_PROP_OBS));
				  otaConvertFrom.appendChild(newElem);
			}
			if(convertFromJson.get(JSON_PROP_INV).toString().isEmpty()==false)
			{
				  Element newElem = ownerDoc.createElementNS(NS_OTA, "Inverse");
				  newElem.setTextContent(convertFromJson.getString(JSON_PROP_INV));
				  otaConvertFrom.appendChild(newElem);
			} 
			if(convertFromJson.get(JSON_PROP_DEC_PLACES).toString().isEmpty()==false)
			{
				  Element newElem = ownerDoc.createElementNS(NS_OTA, "DecimalPlaces");
				  newElem.setTextContent(convertFromJson.get(JSON_PROP_DEC_PLACES).toString());
				  otaConvertFrom.appendChild(newElem);
			}
		}
		
	}

	public static void createHeader(Element reqElem, String sessionID, String transactionID, String userID) {

		Element sessionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./for:RequestHeader/com:SessionID");
		sessionElem.setTextContent(sessionID);

		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./for:RequestHeader/com:TransactionID");
		transactionElem.setTextContent(transactionID);

		Element userElem = XMLUtils.getFirstElementAtXPath(reqElem, "./for:RequestHeader/com:UserID");
		userElem.setTextContent(userID);
		
	}
	
	public static Element[] sortWrapperElementsBySequence(Element[] wrapperElems) {
		Map<Integer, Element> wrapperElemsMap = new TreeMap<Integer, Element>();
		for (Element wrapperElem : wrapperElems) {
			wrapperElemsMap.put(Utils.convertToInt(XMLUtils.getValueAtXPath(wrapperElem, "./for:Sequence"), 0), wrapperElem);
		}
		
		int  idx = 0;
		Element[] seqSortedWrapperElems = new Element[wrapperElems.length];
		Iterator<Map.Entry<Integer, Element>> wrapperElemsIter = wrapperElemsMap.entrySet().iterator();
		while (wrapperElemsIter.hasNext()) {
			seqSortedWrapperElems[idx++] = wrapperElemsIter.next().getValue();
		}
		
		return seqSortedWrapperElems;
	}
}
