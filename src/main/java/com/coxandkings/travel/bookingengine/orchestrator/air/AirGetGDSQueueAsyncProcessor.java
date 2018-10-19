package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
//import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ThreadPoolConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplierUtils;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
//import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AirGetGDSQueueAsyncProcessor implements AirConstants {
	
	private static final Logger logger = LogManager.getLogger(AirGetGDSQueueAsyncProcessor.class);
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException  {
		//Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		AirGetGDSQueueResponseListener suppRespListener = new AirGetGDSQueueResponseListener();

		try {
            TrackingContext.setTrackingContext(reqJson);
            opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

            reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
            reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
            
            
            JSONArray suppGDSQueuesJsonArr = reqBodyJson.getJSONArray(JSON_PROP_GDSQUEUES);
            for (int i=0; i < suppGDSQueuesJsonArr.length(); i++) {
            	JSONObject suppGDSQueuesJson = suppGDSQueuesJsonArr.getJSONObject(i);
            	String tlgxQueue = suppGDSQueuesJson.getString(JSON_PROP_TLGXQUEUE);
            	String suppID = suppGDSQueuesJson.getString(JSON_PROP_SUPPREF);
            	List<ProductSupplier> prodSupps = ProductSupplierUtils.getSupplierCredentialsForGDSQueueOperations(suppID);
            	JSONArray suppQueuesJsonArr = suppGDSQueuesJson.getJSONArray(JSON_PROP_QUEUES);
            	for (ProductSupplier prodSupp : prodSupps) {
            		SupplierGetGDSQueueProcessor suppGDSQProc = new SupplierGetGDSQueueProcessor(suppRespListener, tlgxQueue, prodSupp, reqJson, suppQueuesJsonArr);
            		ThreadPoolConfig.execute(suppGDSQProc);
            		suppRespListener.incrementStartedThreadsCount();
            	}
            }
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
		
        try {    
        	synchronized(suppRespListener) {
        		//suppRespListener.wait(opConfig.getSITimeoutMillis());
        		suppRespListener.wait(opConfig.getServiceTimeoutMillis());
        	}
    		
    		// process response here
    		JSONArray aggregatedResJsonArr = suppRespListener.getAggregatedResponse();
    		JSONObject resBodyJson = new JSONObject();
    		resBodyJson.put(JSON_PROP_SUPPQUEUEMSGS, aggregatedResJsonArr);
    		
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

//	private static void getSupplierGDSQueueMessagesJSON(JSONArray suppQueueMsgsJsonArr, Element queueMsgsResElem) {
//		if (queueMsgsResElem == null) {
//			return;
//		}
//		
//		Element resWrapperElem = (Element) queueMsgsResElem.getParentNode();
//		String suppID = XMLUtils.getValueAtXPath(resWrapperElem, "./air:SupplierID");
//		String queueID = XMLUtils.getValueAtXPath(resWrapperElem, "./air:CategoryID");
//		// TODO: Add check to see suppIDElem == null
//		JSONObject suppQueueMsgsJson = getOrCreateSupplierQMessagesJson(suppQueueMsgsJsonArr, suppID);
//		JSONObject msgsInQueueJson = getOrCreateMessagesInQueueJson(suppQueueMsgsJson, queueID);
//		
//		JSONObject pnrMsgsJson = null;
//		Element[] msgItemElems = XMLUtils.getElementsAtXPath(queueMsgsResElem, "./ota:MessageItems/ota:MessageItem");
//		for (Element msgItemElem : msgItemElems) {
//			String pnrNumber = XMLUtils.getValueAtXPath(msgItemElem, "./ota:UniqueID");
//			if (pnrNumber.isEmpty() && pnrMsgsJson == null) {
//				continue;
//			}
//			
//			if (pnrNumber.isEmpty() == false) {
//				pnrMsgsJson = getOrCreatePNRMessagesJson(msgsInQueueJson, pnrNumber);
//			}
//			
//			JSONArray commentsJsonArr = new JSONArray();
//			Element[] commentElems = XMLUtils.getElementsAtXPath(msgItemElem, "./ota:Messages/ota:Comment");
//			for (Element commentElem : commentElems) {
//				commentsJsonArr.put(commentElem.getTextContent());
//			}
//			JSONArray msgsJsonArr = pnrMsgsJson.getJSONArray(JSON_PROP_MESSAGES);
//			msgsJsonArr.put(commentsJsonArr);
//		}
//	}
	
//	private static JSONObject getOrCreateSupplierQMessagesJson(JSONArray suppQueueMsgsJsonArr, String suppID) {
//		for (int i=0; i < suppQueueMsgsJsonArr.length(); i++) {
//			JSONObject suppQueueMsgsJson = suppQueueMsgsJsonArr.getJSONObject(i);
//			String queueMsgsSuppID = suppQueueMsgsJson.optString(JSON_PROP_SUPPREF);
//			if (queueMsgsSuppID.isEmpty() == false && queueMsgsSuppID.equals(suppID)) {
//				return suppQueueMsgsJson;
//			}
//		}
//		
//		JSONObject suppQueueMsgsJson = new JSONObject();
//		suppQueueMsgsJson.put(JSON_PROP_SUPPREF, suppID);
//		suppQueueMsgsJsonArr.put(suppQueueMsgsJson);
//		return suppQueueMsgsJson;
//	}
	
//	private static JSONObject getOrCreateMessagesInQueueJson(JSONObject suppQueueMsgsJson, String queueID) {
//		JSONArray queueMsgsJsonArr = suppQueueMsgsJson.optJSONArray(JSON_PROP_QUEUEMESSAGES);
//		if (queueMsgsJsonArr == null) {
//			queueMsgsJsonArr = new JSONArray();
//			suppQueueMsgsJson.put(JSON_PROP_QUEUEMESSAGES, queueMsgsJsonArr);
//		}
//		
//		for (int i=0; i < queueMsgsJsonArr.length(); i++) {
//			JSONObject queueMsgsJson = queueMsgsJsonArr.getJSONObject(i);
//			String queueMsgsQID = queueMsgsJson.optString(JSON_PROP_QUEUE);
//			if (queueMsgsQID.isEmpty() == false && queueMsgsQID.equals(queueID)) {
//				return queueMsgsJson;
//			}
//		}
//		
//		JSONObject queueMsgsJson = new JSONObject();
//		queueMsgsJson.put(JSON_PROP_QUEUE, queueID);
//		queueMsgsJsonArr.put(queueMsgsJson);
//		return queueMsgsJson;
//	}

//	private static JSONObject getOrCreatePNRMessagesJson(JSONObject msgsInQueueJson, String pnrNumber) {
//		JSONArray pnrMsgsJsonArr = msgsInQueueJson.optJSONArray(JSON_PROP_PNRMESSAGES);
//		if (pnrMsgsJsonArr == null) {
//			pnrMsgsJsonArr = new JSONArray();
//			msgsInQueueJson.put(JSON_PROP_PNRMESSAGES, pnrMsgsJsonArr);
//		}
//		
//		for (int i=0; i < pnrMsgsJsonArr.length(); i++) {
//			JSONObject pnrMsgsJson = pnrMsgsJsonArr.getJSONObject(i);
//			String gdsPNR = pnrMsgsJson.optString(JSON_PROP_GDSPNR);
//			if (gdsPNR.isEmpty() == false && gdsPNR.equals(pnrNumber)) {
//				return pnrMsgsJson;
//			}
//		}
//		
//		JSONObject pnrMsgsJson = new JSONObject();
//		pnrMsgsJson.put(JSON_PROP_QUEUE, pnrNumber);
//		pnrMsgsJsonArr.put(pnrMsgsJson);
//		return pnrMsgsJson;
//	}

}
