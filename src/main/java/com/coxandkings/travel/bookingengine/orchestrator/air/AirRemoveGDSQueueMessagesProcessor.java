package com.coxandkings.travel.bookingengine.orchestrator.air;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplierUtils;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AirRemoveGDSQueueMessagesProcessor implements AirConstants  {

	private static final Logger logger = LogManager.getLogger(AirRemoveGDSQueueMessagesProcessor.class);

	public static String process(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException {
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
            resBodyJson.put(JSON_PROP_STATUS, Boolean.valueOf(XMLUtils.getValueAtXPath(resElem, "./airi:ResponseBody/air:OTA_RemoveMessageQueueRSWrapper/ota:OTA_RemoveMessageQueueRS/ota:Success")));
            JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);
            
			return resJson.toString();
		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}

	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
		String suppID = reqBodyJson.getString(JSON_PROP_SUPPREF);
		String credsName = reqBodyJson.getString(JSON_PROP_CREDSNAME);
		//String tlgxQueue = reqBodyJson.getString(JSON_PROP_TLGXQUEUE);
		ProductSupplier prodSupplier = ProductSupplierUtils.getSupplierCredentialsForCredentialsName(suppID, credsName);
		
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
		suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element itemsElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_RemoveMessageQueueRQWrapper/ota:OTA_RemoveMessageQueueRQ/ota:Items");

		JSONArray gdsQueuesJsonArr = reqBodyJson.getJSONArray(JSON_PROP_GDSQUEUES);
		for (int y=0; y < gdsQueuesJsonArr.length(); y++) {
			JSONObject gdsQueuesJson = gdsQueuesJsonArr.getJSONObject(y);
			String gdsQueue = gdsQueuesJson.getString(JSON_PROP_QUEUE);
			
			JSONArray gdsPNRsJsonArr = gdsQueuesJson.getJSONArray(JSON_PROP_GDSPNRS);
			for (int j=0; j < gdsPNRsJsonArr.length(); j++) {
				String gdsPNR = gdsPNRsJsonArr.getString(j);
				
				Element itemElem = ownerDoc.createElementNS(NS_OTA, "ota:Item");
				
				Element uniqueIdElem = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
				uniqueIdElem.setTextContent(gdsPNR);
				itemElem.appendChild(uniqueIdElem);

				Element categoryIdElem = ownerDoc.createElementNS(NS_OTA, "ota:CategoryID");
				categoryIdElem.setTextContent(gdsQueue);
				itemElem.appendChild(categoryIdElem);
				
				itemsElem.appendChild(itemElem);
			}
		}
			
		return reqElem;
	}
}
