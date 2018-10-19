package com.coxandkings.travel.bookingengine.orchestrator.activities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class ActivityCancelProcessor implements ActivityConstants {

	private static final Logger logger = LogManager.getLogger(ActivityCancelProcessor.class);

	public static String process(JSONObject reqJson) throws ValidationException {
		try {
			KafkaBookProducer bookProducer = new KafkaBookProducer();

			//OperationConfig opConfig = ActivitiesConfig.getOperationConfig(JSON_PROP_CANCEL);
			ServiceConfig opConfig = ActivitiesConfig.getOperationConfig(JSON_PROP_CANCEL);
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();
			Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./sig:RequestBody/sig1:OTA_TourActivityCancelRQWrapper");
			XMLUtils.removeNode(blankWrapperElem);

			TrackingContext.setTrackingContext(reqJson);

			JSONObject reqHdrJson = reqJson.getJSONObject(ActivityConstants.JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(ActivityConstants.JSON_PROP_REQBODY);

			JSONObject kafkaMsgJson = reqJson;
			kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PRODUCT, PRODUCT_CATEGORY);
			kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_REQUEST_TYPE, JSON_PROP_CANCEL);

			String sessionID = reqHdrJson.getString(ActivityConstants.JSON_PROP_SESSIONID);
			String transactionID = reqHdrJson.getString(ActivityConstants.JSON_PROP_TRANSACTID);
			String userID = reqHdrJson.getString(ActivityConstants.JSON_PROP_USERID);

			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);

			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_SESSIONID, sessionID);
			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_TRANSACTIONID, transactionID);
			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_USERID, userID);

			JSONArray multiReqArr = reqBodyJson.getJSONArray(JSON_PROP_ACTIVITYINFO);
			JSONObject activityInfo;
			for (int j = 0; j < multiReqArr.length(); j++) {

				ProductSupplier prodSupplier;
				JSONObject reservationDetail = multiReqArr.getJSONObject(j);
				String suppID = reservationDetail.getString(ActivityConstants.SUPPLIER_ID);
				prodSupplier = usrCtx.getSupplierForProduct(ActivityConstants.PRODUCT_CATEGORY,
						ActivityConstants.PRODUCT_SUBCATEGORY, suppID);

				if (prodSupplier == null) {
					throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
				}

				XMLUtils.insertChildNode(reqElem, XPATH_REQUESTHEADER_SUPPLIERCREDENTIALSLIST,
						prodSupplier.toElement(ownerDoc, j), false);

				Element wrapperElement = (Element) blankWrapperElem.cloneNode(true);
				Document wrapperOwner = wrapperElement.getOwnerDocument();

				XMLUtils.setValueAtXPath(wrapperElement, "./sig1:SupplierID",
						multiReqArr.getJSONObject(j).getString(SUPPLIER_ID));
				XMLUtils.setValueAtXPath(wrapperElement, "./sig1:Sequence", String.valueOf(j));

				XMLUtils.setValueAtXPath(wrapperElement, "./ns:OTA_TourActivityCancelRQ/ns:POS/ns:Source/@ISOCurrency",
						multiReqArr.getJSONObject(j).getString(JSON_PROP_ISO_CURRENCY));
				String con[] = multiReqArr.getJSONObject(j).getString(JSON_PROP_BOOKREFID).split("\\|");
				for (String str2 : con) {
					XMLUtils.insertChildNode(wrapperElement, "./ns:OTA_TourActivityCancelRQ",
							getConfirmations(wrapperOwner, str2), false);
				}
				XMLUtils.insertChildNode(reqElem, "./sig:RequestBody", wrapperElement, false);

				/** Kafka Msg JSON code */
				activityInfo = multiReqArr.getJSONObject(j);
				String type = activityInfo.getString(JSON_PROP_TYPE);

				kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(j)
						.put(JSON_PROP_ENTITY_NAME, "order");

				/**
				 * Book Ref id is sent from BookingEngine. In DbServices BookRefId is used to
				 * get orderID which is then feed into the table
				 */
				kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(j)
						.put(JSON_PROP_ENTITY_ID, multiReqArr.getJSONObject(j).getString(JSON_PROP_BOOKREFID));

			}
			Element resElem = null;


			// TODO Kafka msg before SI request.
			bookProducer.runProducer(1, kafkaMsgJson);

			logger.trace(String.format("SI XML Request = %s", XMLTransformer.toString(reqElem)));
			//resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), ActivitiesConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception(ERROR_MSG_NULL_RESPONSE_SI);
			}
			logger.trace(String.format("SI XML Response = %s", XMLTransformer.toString(resElem)));

			JSONObject resJson = getCancelResponseJSON(reqJson, resElem);

			kafkaMsgJson = resJson;
			kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PRODUCT, PRODUCT_CATEGORY);
			kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_REQUEST_TYPE, JSON_PROP_CANCEL);

			JSONObject responseBody = kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY);
			JSONArray supplierCancelReferences = responseBody.getJSONArray("supplierCancelReferences");
			String entityName = "order";
			for (int supplierCancelReferencesCount = 0; supplierCancelReferencesCount < supplierCancelReferences
					.length(); supplierCancelReferencesCount++) {
				JSONObject supplierCancelRefrence = supplierCancelReferences
						.getJSONObject(supplierCancelReferencesCount);

				// entityID will be orderID
				// supplierCancelRefrence.put("entityId",supplierCancelRefrence.getString("cancelRefId"));
				supplierCancelRefrence.put("entityName", entityName);

				// JSONObject activityInfoObj;
				for (int count = 0; count < multiReqArr.length(); count++) {
					// supplierCancelReferences.getJSONObject(count).put("bookRefId",
					// activityInfos.getJSONObject(count).getString("bookRefId"));
					supplierCancelReferences.getJSONObject(count).put("entityId",
							multiReqArr.getJSONObject(count).getString("bookRefId"));
					supplierCancelReferences.getJSONObject(count).put("type",
							multiReqArr.getJSONObject(count).getString(JSON_PROP_TYPE));
				}

			}


			// TODO Kafka msg before SI request.
			bookProducer.runProducer(1, kafkaMsgJson);


			return resJson.toString();

		} 
		
		catch(ValidationException valx) {
			throw valx;
		}
		
		catch (Exception e) {
			e.printStackTrace();
			return STATUS_ERROR;
		}

	}

	private static JSONObject getCancelResponseJSON(JSONObject reqJson, Element resElem) throws ValidationException {
		JSONObject resJson = new JSONObject();
		JSONObject resBodyJson = new JSONObject();

		JSONArray supplierCancelReferencesArr = new JSONArray();

		Element[] tourActivityCancelRSWrapperElems = XMLUtils.getElementsAtXPath(resElem,
				"./sig:ResponseBody/sig1:OTA_TourActivityCancelRSWrapper");

		for (Element tourActivityCancelRSWrapperElem : tourActivityCancelRSWrapperElems) {

			JSONObject supplierCancelReferences = new JSONObject();
			
			String supplierID = XMLUtils.getValueAtXPath(tourActivityCancelRSWrapperElem, "./sig1:SupplierID");
			supplierCancelReferences.put(SUPPLIER_ID, supplierID);
			
			Element[] tourActivityCancelRSElems = XMLUtils.getElementsAtXPath(tourActivityCancelRSWrapperElem, "./ota:OTA_TourActivityCancelRS");
			
			for(Element tourActivityCancelRSElem: tourActivityCancelRSElems) {
			
				if(XMLUtils.getValueAtXPath(tourActivityCancelRSElem, "./ota:Success").equalsIgnoreCase("false")) {
					
					ActivitySearchProcessor.validateSupplierResponse(supplierID, tourActivityCancelRSElem);
					
				}
				
			// For now it is done as Single Concatenated String from multiple confirmation
			// tags and Refrences Tags
			StringBuilder confirmationID = new StringBuilder();
			Element[] confirmations = XMLUtils.getElementsAtXPath(tourActivityCancelRSElem,
					"./ota:Reservation/ota:CancelConfirmation/ota:UniqueID");
			Element[] refrences = XMLUtils.getElementsAtXPath(tourActivityCancelRSElem,
					"./ota:Reservation/ota:ReferenceID");

//			for (int confirmationCount = 0; confirmationCount < confirmations.length; confirmationCount++) {
//				
//				confirmationID.append(XMLUtils.getValueAtXPath(confirmations[confirmationCount], "./@ID")).append(",");
//				confirmationID.append(XMLUtils.getValueAtXPath(confirmations[confirmationCount], "./@Type"))
//						.append(",");
//				confirmationID.append(XMLUtils.getValueAtXPath(confirmations[confirmationCount], "./@Instance"));
//				if (confirmationCount != (confirmations.length - 1) || (refrences != null && refrences.length > 0)) {
//					confirmationID.append("|");
//				}
//			}

			for (int refrencesCount = 0; refrencesCount < refrences.length; refrencesCount++) {
				confirmationID.append(XMLUtils.getValueAtXPath(refrences[refrencesCount], "./@ID")).append(",");
				confirmationID.append(XMLUtils.getValueAtXPath(refrences[refrencesCount], "./@Type")).append(",");
				confirmationID.append(XMLUtils.getValueAtXPath(refrences[refrencesCount], "./@Instance"));
				
				
				
				if (refrencesCount != (refrences.length - 1)) {
					confirmationID.append("|");
				}
			}

			
			supplierCancelReferences.put(JSON_PROP_STATUS, XMLUtils.getValueAtXPath(tourActivityCancelRSElem, "./ota:Reservation/ota:CancelConfirmation/ota:UniqueID/@ID_Context"));
			supplierCancelReferences.put("cancelRefId", confirmationID);
			

			supplierCancelReferencesArr.put(supplierCancelReferences);
		}}
		resBodyJson.put("supplierCancelReferences", supplierCancelReferencesArr);
		resBodyJson.put("bookID", reqJson.getJSONObject(JSON_PROP_REQBODY).get("bookID")); // BookID is cart ID
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		return resJson;
	}

	private static Element getConfirmations(Document ownerDoc, String str1) {
		Element confirmation = ownerDoc.createElementNS("http://www.opentravel.org/OTA/2003/05", "ns:Confirmation");
		String subcon[] = str1.split(",");
		if (subcon.length >= 1 && null != subcon[0] && !subcon[0].isEmpty())
			confirmation.setAttribute(JSON_PROP_ID, subcon[0]);
		if (subcon.length >= 2 && null != subcon[1] && !subcon[1].isEmpty())
			confirmation.setAttribute("Type", subcon[1]);
		if (subcon.length >= 3 && null != subcon[2] && !subcon[2].isEmpty())
			confirmation.setAttribute("Instance", subcon[2]);
		return confirmation;
	}

}
