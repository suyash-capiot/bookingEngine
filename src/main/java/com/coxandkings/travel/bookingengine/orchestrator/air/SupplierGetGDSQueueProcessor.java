package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.userctx.Credential;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackableExecutor;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class SupplierGetGDSQueueProcessor extends TrackableExecutor implements AirConstants {
	
	private static final Logger logger = LogManager.getLogger(SupplierGetGDSQueueProcessor.class);
	private static Pattern mDatePatternddMMM = Pattern.compile("([0][1-9]|[1-2][0-9]|[3][0-1])(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)");
	private static DateFormat mDateFormatddMMyyyy = new SimpleDateFormat("ddMMMyyyy");
	
	private JSONArray mSuppQueuesJsonArr;
	private AirGetGDSQueueResponseListener mRespListener;
	private String mTLGXQueue;
	
	
	SupplierGetGDSQueueProcessor(AirGetGDSQueueResponseListener respListener, String tlgxQueue, ProductSupplier prodSupp, JSONObject reqJson, JSONArray suppQueuesJsonArr) {
		mRespListener = respListener;
		mTLGXQueue = tlgxQueue;
		mProdSupplier = prodSupp;
		mReqJson = reqJson;
		mSuppQueuesJsonArr = suppQueuesJsonArr;
	}
	
	@SuppressWarnings("unused")
	public void process(ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		//Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		
		JSONObject resJson = createResponseJsonShell();

		try {
			//----------------------------------------------------------------------------
			// Request Creation
			
            opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

            reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
            reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
            
            reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson);

			//----------------------------------------------------------------------------
			// SI Service Invocation

            Element resElem = null;
            //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AirConfig.getHttpHeaders(), reqElem);
            //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
            resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
            if (resElem == null) {
            	throw new Exception("Null response received from SI");
            }

			//----------------------------------------------------------------------------
			// Response Creation

            JSONArray suppGDSQueuesJsonArr = new JSONArray();
            Element[] resWrapperElems = AirSearchProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_MessageQueueRSWrapper"));
            for (Element resWrapperElem : resWrapperElems) {
            	Element queueMsgsResElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_MessageQueueRS");
            	JSONObject suppGDSQueueJson = getSupplierGDSQueueMessagesJSON(queueMsgsResElem);
            	suppGDSQueuesJsonArr.put(suppGDSQueueJson);
            }

            JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
            resBodyJson.put(JSON_PROP_GDSQUEUES, suppGDSQueuesJsonArr);

		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
		}
		finally {
			mRespListener.receiveSupplierResponse(resJson);
		}
	}

	//private Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson) throws Exception {
	private Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson) throws Exception {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_MessageQueueRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		
		int seqIdx = 0;
		String suppID = mProdSupplier.getSupplierID();
		
		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
		suppCredsListElem.appendChild(mProdSupplier.toElement(ownerDoc));
		
		for (int j=0; j < mSuppQueuesJsonArr.length(); j++) {
			String queueName = mSuppQueuesJsonArr.getString(j);
			Element queueWrapperElem = (Element) wrapperElem.cloneNode(true);

			Element suppElem = XMLUtils.getFirstElementAtXPath(queueWrapperElem, "./air:SupplierID");
			suppElem.setTextContent(suppID);
			
			Element seqElem = XMLUtils.getFirstElementAtXPath(queueWrapperElem, "./air:Sequence");
			seqElem.setTextContent(String.valueOf(++seqIdx));
			
			Element categElem = XMLUtils.getFirstElementAtXPath(queueWrapperElem, "./ota:OTA_MessageQueueRQ/ota:CategoryID");
			categElem.setTextContent(queueName);

			// TODO: This is only temporary till the meaning of this element and its value is clarified. This element is required in Mystifly.
			Element targetElem = XMLUtils.getFirstElementAtXPath(queueWrapperElem, "./ota:OTA_MessageQueueRQ/ota:Target");
			targetElem.setTextContent("Test");

			reqBodyElem.appendChild(queueWrapperElem);
		}
		
		return reqElem;
	}
	
	private JSONObject getSupplierGDSQueueMessagesJSON(Element queueMsgsResElem) {
		Element resWrapperElem = (Element) queueMsgsResElem.getParentNode();
		
		String queueID = "??";
		int sequence = Utils.convertToInt(XMLUtils.getValueAtXPath(resWrapperElem, "./air:Sequence"), -1);
		if (sequence > -1) {
			queueID = mSuppQueuesJsonArr.getString(sequence - 1);
		}
		
		JSONObject suppGDSQueueJson = new JSONObject();
		suppGDSQueueJson.put(JSON_PROP_QUEUE, queueID);
		
		JSONObject pnrMsgsJson = null;
		Element[] msgItemElems = XMLUtils.getElementsAtXPath(queueMsgsResElem, "./ota:MessageItems/ota:MessageItem");
		for (Element msgItemElem : msgItemElems) {
			String pnrNumber = XMLUtils.getValueAtXPath(msgItemElem, "./ota:UniqueID");
			if (pnrNumber.isEmpty()) {
				continue;
			}

			JSONArray messagesJsonArr = new JSONArray();
			Element[] messageElems = XMLUtils.getElementsAtXPath(msgItemElem, "./ota:Messages/ota:Message");
			for (Element messageElem : messageElems) {
				messagesJsonArr.put(messageElem.getTextContent());
			}

			JSONObject pnrMsgJson = new JSONObject();
			pnrMsgJson.put(JSON_PROP_QUEUEDATE, formatQueueDate(XMLUtils.getValueAtXPath(msgItemElem, "./ota:QueueDate")));
			pnrMsgJson.put(JSON_PROP_QUEUETIME, XMLUtils.getValueAtXPath(msgItemElem, "./ota:QueueTime"));
			pnrMsgJson.put(JSON_PROP_MESSAGES, messagesJsonArr);
			
			pnrMsgsJson = getOrCreatePNRMessagesJson(suppGDSQueueJson, pnrNumber);
			JSONArray pnrMsgsJsonArr = pnrMsgsJson.getJSONArray(JSON_PROP_PNRMESSAGES);
			pnrMsgsJsonArr.put(pnrMsgJson);
		}
		
		return suppGDSQueueJson;
	}
	
	private JSONObject getOrCreatePNRMessagesJson(JSONObject suppGDSQueueJson, String pnrNumber) {
		JSONArray queueMsgsJsonArr = suppGDSQueueJson.optJSONArray(JSON_PROP_QUEUEMESSAGES);
		if (queueMsgsJsonArr == null) {
			queueMsgsJsonArr = new JSONArray();
			suppGDSQueueJson.put(JSON_PROP_QUEUEMESSAGES, queueMsgsJsonArr);
		}
		
		for (int i=0; i < queueMsgsJsonArr.length(); i++) {
			JSONObject queueMsgsJson = queueMsgsJsonArr.getJSONObject(i);
			String gdsPNR = queueMsgsJson.optString(JSON_PROP_GDSPNR);
			if (gdsPNR.isEmpty() == false && gdsPNR.equals(pnrNumber)) {
				return queueMsgsJson;
			}
		}
		
		JSONObject pnrMsgsJson = new JSONObject();
		pnrMsgsJson.put(JSON_PROP_GDSPNR, pnrNumber);
		pnrMsgsJson.put(JSON_PROP_PNRMESSAGES, new JSONArray());
		queueMsgsJsonArr.put(pnrMsgsJson);
		return pnrMsgsJson;
	}

	private JSONObject createResponseJsonShell() {
		JSONObject resJson = new JSONObject();
		
		resJson.put(JSON_PROP_RESHEADER, mReqJson.optJSONObject(JSON_PROP_REQHEADER));
		
		JSONObject resBodyJson = new JSONObject();
		resBodyJson.put(JSON_PROP_SUPPREF, mProdSupplier.getSupplierID());
		resBodyJson.put(JSON_PROP_CREDSNAME, mProdSupplier.getCredentialsName());
		Credential pccCredential = mProdSupplier.getCredentialForKey(MDM_VAL_PCC);
		if (pccCredential != null) {
			resBodyJson.put(JSON_PROP_PSEUDOCC, pccCredential.getValue());
		}
		resBodyJson.put(JSON_PROP_TLGXQUEUE, mTLGXQueue);
		
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		return resJson;
	}
	
	private static String formatQueueDate(String queueDate) {
		if (mDatePatternddMMM.matcher(queueDate).matches() == false) {
			return queueDate;
		}
		
		try {
			// Retrieve today's date
			Instant nowInstant = Instant.now();
			ZonedDateTime nowZonedDateTime = nowInstant.atZone(ZoneId.systemDefault());
			String yearStr = String.valueOf(nowZonedDateTime.getYear());
			int mthInt = nowZonedDateTime.getMonthValue();
			int dayInt = nowZonedDateTime.getDayOfMonth();
	
			Date dt = mDateFormatddMMyyyy.parse(String.format("%s%s", queueDate, yearStr));
			Instant dtInstant = dt.toInstant();
			ZonedDateTime queueZonedDateTime = dtInstant.atZone(ZoneId.systemDefault());
			int queueDateMthInt = queueZonedDateTime.getMonthValue();
			int queueDateDayInt = queueZonedDateTime.getDayOfMonth();
			if (queueDateMthInt > mthInt || (queueDateMthInt == mthInt && queueDateDayInt > dayInt)) {
				queueZonedDateTime = queueZonedDateTime.minusYears(1);
			}

			return queueZonedDateTime.toString().substring(0, 10);
		}
		catch (Exception x) {
			logger.debug(String.format("Exception when formatting queue date {0}", queueDate), x);
			return queueDate;
		}
		
	}
}
