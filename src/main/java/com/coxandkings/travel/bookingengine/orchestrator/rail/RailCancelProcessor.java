package com.coxandkings.travel.bookingengine.orchestrator.rail;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.rail.RailConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class RailCancelProcessor implements RailConstants {
	private static final Logger logger = LogManager.getLogger(RailCancelProcessor.class);
	static final String OPERATION_NAME = "cancel";
	
	public static String process(JSONObject reqJson) {
		try {
			KafkaBookProducer bookProducer = null;
			// opConfig: contains all details of search operation-SI URL, Req XML Shell
			//OperationConfig opConfig = RailConfig.getOperationConfig(OPERATION_NAME);
			ServiceConfig opConfig = RailConfig.getOperationConfig(OPERATION_NAME);
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			TrackingContext.setTrackingContext(reqJson);
			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			String sessionId = reqHdrJson.getString(JSON_PROP_SESSIONID);
			String transactionId = reqHdrJson.getString(JSON_PROP_TRANSACTID);
			String userId = reqHdrJson.getString(JSON_PROP_USERID);

			UserContext usrContxt = UserContext.getUserContextForSession(reqHdrJson);
			List<ProductSupplier> productSuppliers = usrContxt.getSuppliersForProduct(PROD_CATEG_TRANSPORT,
					PROD_CAT_SUBTYPE);

			// **********WEM JSON TO SI XML FOR REQUEST HEADER STARTS HERE**********//

			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:SessionID", sessionId);
			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:TransactionID", transactionId);
			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:UserID", userId);

			Element suppCredsList = XMLUtils.getFirstElementAtXPath(reqElem,
					"./rail:RequestHeader/com:SupplierCredentialsList");

			if (productSuppliers == null) {
				throw new Exception("Product supplier not found for user/client");
			}

			for (ProductSupplier prodSupplier : productSuppliers) {
				suppCredsList.appendChild(prodSupplier.toElement(ownerDoc));
			}

			// ************WEM JSON TO SI XML FOR REQUEST HEADER ENDS HERE************//

			// *******WEM JSON TO SI XML FOR REQUEST BODY STARTS HERE*********//

			Element railUniqueId = XMLUtils.getFirstElementAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_CancelRQWrapper/ota:OTA_CancelRQ/ota:UniqueID");
			railUniqueId.setAttribute("ID", reqBodyJson.getString(JSON_PROP_UNIQUE_ID));
			railUniqueId.setAttribute("Type", reqBodyJson.getString(JSON_PROP_UNIQUE_TYPE_ID));
			
			//set supplier id and sequence start
			Element supplierIdElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_CancelRQWrapper/rail:SupplierID");
			supplierIdElem.setTextContent(reqBodyJson.getString(MDM_PROP_SUPPID));
			
			Element sequenceIdElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_CancelRQWrapper/rail:Sequence");
			sequenceIdElem.setTextContent(reqBodyJson.getInt(JSON_PROP_SEQUENCE)+"");
		
			//end
			
//			XMLUtils.setValueAtXPath(reqElem,
//					"./raili:RequestBody/rail:OTA_CancelRQWrapper/ota:OTA_CancelRQ/ota:UniqueID",
//					reqBodyJson.getString(JSON_PROP_UNIQUE_ID));
			JSONArray travelerJsonArray=reqBodyJson.getJSONArray("traveler");
			for (int i = 0; i < travelerJsonArray.length(); i++) {
				Element railDocIdElem = XMLUtils.getFirstElementAtXPath(reqElem,
						"./raili:RequestBody/rail:OTA_CancelRQWrapper/ota:OTA_CancelRQ/ota:OriginAndDestinationSegment/ota:Traveler");
				railDocIdElem.setAttribute("DocID", travelerJsonArray.getJSONObject(i).getString("docID"));
//			XMLUtils.setValueAtXPath(reqElem,
//					"./raili:RequestBody/rail:OTA_CancelRQWrapper/ota:OTA_CancelRQ/ota:OriginAndDestinationSegment/ota:Traveler",
//					travelerJsonArray.getJSONObject(i).getString("docID"));
			}
			XMLUtils.setValueAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_CancelRQWrapper/ota:OTA_CancelRQ/ota:TPA_Extensions/rail:Rail_TPA/rail:ReferenceID",
					reqBodyJson.getString("referenceID"));

			// ********WEM JSON TO SI XML FOR REQUEST BODY ENDS HERE***********//

			// ******CONSUME SI SEARCH SERVICE***********************//

			System.out.println(XMLTransformer.toString(reqElem));
			logger.info("Before opening HttpURLConnection to SI");
			//Temporary read xml file for SI response
			Element resElem = null;
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(),
					opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			
			OperationsToDoProcessor.callOperationTodo(ToDoTaskName.CANCEL, ToDoTaskPriority.MEDIUM, ToDoTaskSubType.ORDER, "", reqBodyJson.getString(JSON_PROP_UNIQUE_ID),reqHdrJson,"");
			
			System.out.println("SI ResponseXMLTransformer: "+XMLTransformer.toString(resElem));
			logger.info("HttpURLConnection to SI closed");
			JSONObject resJson;
			resJson = getSupplierResponseJSON(reqJson, resElem);
			bookProducer = new KafkaBookProducer();
			JSONObject copyOfRequestJson=reqJson;
			sendKafkaMessage(bookProducer, copyOfRequestJson , usrContxt);
//			logger.info("SI res: " + resJson.toString());
//			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
//
//			logger.info("After creating response from SI");
//
//			// ***********SI SEARCH RESPONSE CONVERTED TO JSON***********//
//
//			Map<String, JSONObject> brmsToSIClassMap = new LinkedHashMap<String, JSONObject>();
//			JSONObject resSupplierComJson = SupplierCommercials.getSupplierCommercials(reqJson, resJson,
//					brmsToSIClassMap);
//
//			JSONObject resClientComJson = ClientCommercials.getClientCommercials(resSupplierComJson);
//			return resJson.toString(); 
			return resJson.toString();

		} catch (Exception e) {
			e.printStackTrace();
			return "{\"status\": \"ERROR\"}";
		}
	}

	
	
	private static void sendKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, UserContext usrContxt) throws Exception {
		//JSONObject kafkaMsgJson = reqJson;
		reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).remove(JSON_PROP_CLIENTCALLBACK);
		reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).remove(JSON_PROP_COMPANY);
		
		JSONObject kafkaMsg=new JSONObject(new JSONTokener(reqJson.toString()));
		JSONObject reqBodyJson = kafkaMsg.getJSONObject(JSON_PROP_REQBODY);
		
		JSONObject kafkaRequestBody=new JSONObject();
		kafkaRequestBody.put(JSON_PROP_PROD, reqBodyJson.getString(JSON_PROP_PRODUCT));
		kafkaRequestBody.put(JSON_PROP_TYPE, reqBodyJson.getString(JSON_PROP_CANCELTYPE));
		kafkaRequestBody.put(JSON_PROP_ORDERID, reqBodyJson.getString(JSON_PROP_ORDERID));
		kafkaRequestBody.put(JSON_PROP_REQUEST_TYPE, reqBodyJson.getString(JSON_PROP_REQUEST_TYPE));
		kafkaRequestBody.put(JSON_PROP_BOOKID, reqBodyJson.getString(JSON_PROP_BOOKID));
		//TODO:hardcoaded... should be dynamic
		kafkaRequestBody.put(JSON_PROP_ENTITYNAME, "passenger");
		
		//read values from traveler array and create entity ids json array
		JSONArray entityIdsJsonArray=new JSONArray();
		
		JSONArray tranvelerJsonArray=reqBodyJson.getJSONArray(JSON_PROP_TRAVELER);
		for (Object object : tranvelerJsonArray) {
			JSONObject jsonObj=(JSONObject)object;
			JSONObject ids=new JSONObject();
			ids.put(JSON_PROP_ENTITY_ID, jsonObj.getString(JSON_PROP_DOCID));
			entityIdsJsonArray.put(ids);
		}
		kafkaRequestBody.put(JSON_PROP_ENTITY_IDS, entityIdsJsonArray);
		
		kafkaMsg.remove(JSON_PROP_REQBODY);
		kafkaMsg.put(JSON_PROP_REQBODY, kafkaRequestBody);
		bookProducer.runProducer(1, kafkaMsg);
		logger.trace(String.format("Rail cancel Request Kafka Message: %s", kafkaMsg.toString()));
	}



	private static JSONObject getSupplierResponseJSON(JSONObject reqJson, Element resElem) {
		JSONObject resJson = new JSONObject();
		Element resBodyElem = XMLUtils.getFirstElementAtXPath(resElem,
				"./raili:ResponseBody");
		JSONObject resBodyJson = new JSONObject();
		JSONObject ota_CancelRSWrapperJson = new JSONObject();
		JSONObject ota_CancelRSJson=new JSONObject();
		Element cancelWrapperElement = XMLUtils.getFirstElementAtXPath(resBodyElem,
				"./rail:OTA_CancelRSWrapper");
		if (cancelWrapperElement!=null) {
			ota_CancelRSWrapperJson.put(JSON_PROP_SUPPID, XMLUtils.getValueAtXPath(cancelWrapperElement, "./rail:SupplierID"));
			ota_CancelRSWrapperJson.put(JSON_PROP_SEQUENCE, XMLUtils.getValueAtXPath(cancelWrapperElement, "./rail:Sequence"));
			//read n1:OTA_CancelRS and create json object "OTA_CancelRS"
			getOTACancelRSJson(cancelWrapperElement,ota_CancelRSWrapperJson);
		}
		resBodyJson.put(JSON_PROP_OTA_CancelRSWrapper, ota_CancelRSWrapperJson);
		resJson.put(JSON_PROP_RESHEADER, reqJson.get(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);

		return resJson;
	}

	private static void getOTACancelRSJson(Element cancelWrapperElement, JSONObject ota_CancelRSWrapperJson) {
		System.out.println(XMLTransformer.toString(cancelWrapperElement));
		Element[] otaCancelRsWrapperElementArray = XMLUtils.getElementsAtXPath(cancelWrapperElement,
				"./ota:OTA_CancelRS/ota:UniqueID");
		JSONObject ota_CancelRSJson=new JSONObject();
		JSONArray uniqueIdJsonArray = new JSONArray();
		for (Element otaCancelRsWrapperElement : otaCancelRsWrapperElementArray) {
			JSONObject otaCancelRSIDJson=new JSONObject();
			otaCancelRSIDJson.put(JSON_PROP_ID, XMLUtils.getValueAtXPath(otaCancelRsWrapperElement, "./@ID"));
			String id_context=XMLUtils.getValueAtXPath(otaCancelRsWrapperElement, "./@ID_Context");
			if (id_context!=null && id_context!="") {
				otaCancelRSIDJson.put(JSON_PROP_ID_CONTEXT,id_context);
			}
			uniqueIdJsonArray.put(otaCancelRSIDJson);
		}
		ota_CancelRSJson.put(JSON_PROP_UNIQUE_ID, uniqueIdJsonArray);
		
		//create json for TPA_Extensions element
		getTPAExtensions(cancelWrapperElement,ota_CancelRSJson);
		
		ota_CancelRSWrapperJson.put(JSON_PROP_OTA_CANCELRS, ota_CancelRSJson);
		
	}

	//To access "<CancelDetails>"
	private static void getTPAExtensions(Element cancelWrapperElement, JSONObject ota_CancelRSJson) {
		Element tpaExtensionsElement = XMLUtils.getFirstElementAtXPath(cancelWrapperElement,
				"./ota:OTA_CancelRS/ota:TPA_Extensions/rail:Rail_TPA/rail:CancelDetails");
		
		JSONObject tpa_ExtensionsJson=new JSONObject();
		JSONObject cancelDetailsJson=new JSONObject();
		JSONObject railTpaJson=new JSONObject();

		cancelDetailsJson.put(JSON_PROP_AMOUNT_COLLECTED, XMLUtils.getValueAtXPath(tpaExtensionsElement, "./rail:amountCollected"));
		cancelDetailsJson.put(JSON_PROP_CASH_DEDUCTED, XMLUtils.getValueAtXPath(tpaExtensionsElement, "./rail:cashDeducted"));
		cancelDetailsJson.put(JSON_PROP_REFUND_AMOUNT, XMLUtils.getValueAtXPath(tpaExtensionsElement, "./rail:refundAmount"));
		cancelDetailsJson.put(JSON_PROP_REFUND_CURRENCY, XMLUtils.getValueAtXPath(tpaExtensionsElement, "./rail:currency"));

		//For passengerstatus element
		JSONObject passengerStatusJson=new JSONObject();
		Element passengerStatusElement = XMLUtils.getFirstElementAtXPath(tpaExtensionsElement,
				"./rail:passengerStatus");
		passengerStatusJson.put(JSON_PROP_NAME, XMLUtils.getValueAtXPath(passengerStatusElement, "./@name"));
		passengerStatusJson.put(JSON_PROP_STATUS, XMLUtils.getValueAtXPath(passengerStatusElement, "./@status"));
		cancelDetailsJson.put(JSON_PROP_PASSENGER_STATUS, passengerStatusJson);
		railTpaJson.put(JSON_PROP_CANCEL_DETAILS, cancelDetailsJson);
		tpa_ExtensionsJson.put(JSON_PROP_RAIL_TPA, railTpaJson);
		ota_CancelRSJson.put(JSON_PROP_TPA_EXTENSIONS, tpa_ExtensionsJson);
	}
	
}
