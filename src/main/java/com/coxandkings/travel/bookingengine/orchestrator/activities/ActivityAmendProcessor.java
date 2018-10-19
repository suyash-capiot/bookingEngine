package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class ActivityAmendProcessor implements ActivityConstants {

	private static final String XPATH_NS_NAME_TITLE = "./ns:Category/ns:Contact/ns:PersonName/ns:NameTitle";
	private static final String XPATH_NS_SURNAME = "./ns:Category/ns:Contact/ns:PersonName/ns:Surname";
	private static final String XPATH_NS_MIDDLE_NAME = "./ns:Category/ns:Contact/ns:PersonName/ns:MiddleName";
	private static final String XPATH_NS_GIVEN_NAME = "./ns:Category/ns:Contact/ns:PersonName/ns:GivenName";
	private static final String XPATH_NAME_PREFIX = "./ns:Category/ns:Contact/ns:PersonName/ns:NamePrefix";
	private static final String XPATH_NS_QUALIFIER_INFO = "./ns:Category/ns:QualifierInfo";
	private static final String XPATH_PARTICIPANT_CATEGORY_ID = "./ns:Category/@ParticipantCategoryID";
	private static final Logger logger = LogManager.getLogger(ActivityAmendProcessor.class);

	public static String process(JSONObject reqJson) throws ValidationException {

		try {

			KafkaBookProducer bookProducer = new KafkaBookProducer();

			/*
			 * 
			 * Amend is only suppoerted in GTA. Operations supported are adding a passenger,
			 * deleting a passenger. both of these are essentially the same as the take the
			 * final list of passengers irrespective of what the earlier list was. Another
			 * operation that is supported is changing period of stay which changes the
			 * dates
			 * 
			 */

			String entityName = "pax";

			//OperationConfig opConfig = ActivitiesConfig.getOperationConfig(JSON_PROP_AMEND);
			ServiceConfig opConfig = ActivitiesConfig.getOperationConfig(JSON_PROP_AMEND);
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();
			Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem, "./sig:RequestBody/sig1:OTA_TourActivityModifyRQWrapper");
			XMLUtils.removeNode(blankWrapperElem);

			TrackingContext.setTrackingContext(reqJson);
			JSONObject reqHdrJson = reqJson.getJSONObject(ActivityConstants.JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(ActivityConstants.JSON_PROP_REQBODY);

			JSONObject kafkaMsgJson = reqJson;
			kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PRODUCT, PRODUCT_CATEGORY);
			kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_REQUEST_TYPE, JSON_PROP_AMEND);

			String sessionID = reqHdrJson.getString(ActivityConstants.JSON_PROP_SESSIONID);
			String transactionID = reqHdrJson.getString(ActivityConstants.JSON_PROP_TRANSACTID);
			String userID = reqHdrJson.getString(ActivityConstants.JSON_PROP_USERID);

			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);

			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_SESSIONID, sessionID);
			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_TRANSACTIONID, transactionID);
			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_USERID, userID);

			JSONArray actReqArr = reqBodyJson.getJSONArray(JSON_PROP_ACTIVITYINFO);
			Element wrapperElement;
			JSONObject activityInfo;
			HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap = new HashMap<>();

			for (int i = 0; i < actReqArr.length(); i++) {
				activityInfo = actReqArr.getJSONObject(i);

				/**
				 * TODO : type will : ADDPASSENGER, CANCELPASSENGER, <for change period of stay>
				 * UPDATEPASSENGER<- there wont be UpdatePassenger, there will be change period
				 * of stay FULLCANCELLATION FULLCANCELLATION WILL BE HANDLED BY
				 * ACTIVITYCANCELPROCESSOR
				 */
				String type = activityInfo.getString(JSON_PROP_TYPE);

				switch (type) {
				case JSON_PROP_TYPE_ADDPAX: {
					kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(i).put(JSON_PROP_ENTITY_NAME, entityName);
				}
				case JSON_PROP_TYPE_DELETEPAX: {
					kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(i).put(JSON_PROP_ENTITY_NAME, entityName);
				}
				case JSON_PROP_TYPE_CHANGEPOS: {
					kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(i).put(JSON_PROP_ENTITY_NAME, entityName);
				}

				}

				StringBuilder entityId = new StringBuilder();
				JSONArray participantInfoJsonArray = activityInfo.getJSONArray(JSON_PROP_PARTICIPANT_INFO);
				for (int j = 0; j < participantInfoJsonArray.length(); j++) {
					entityId.append(participantInfoJsonArray.getJSONObject(j).getString("participantCategoryID")).append("|");
				}
				entityId.setLength(entityId.length() - 1);
				kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(i).put(JSON_PROP_ENTITY_ID, entityId);

				wrapperElement = (Element) blankWrapperElem.cloneNode(true);
				String suppID = activityInfo.getString(SUPPLIER_ID);
				Element bookingInfoElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ns:OTA_TourActivityModifyRQ/ns:BookingInfo");
				ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(ActivityConstants.PRODUCT_CATEGORY, ActivityConstants.PRODUCT_SUBCATEGORY, suppID);
				if (prodSupplier == null) {
					throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
				}
				Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, XPATH_REQUESTHEADER_SUPPLIERCREDENTIALSLIST);
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, i));
				Element suppIDElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./sig1:SupplierID");
				suppIDElem.setTextContent(suppID);
				Element sequenceElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./sig1:Sequence");
				sequenceElem.setTextContent(String.valueOf(i));
				XMLUtils.setValueAtXPath(bookingInfoElem, "./ns:BasicInfo/@SupplierProductCode", activityInfo.getString(JSON_PROP_SUPPLIERPRODUCTCODE));
				JSONObject confirmation = getConfirmation(activityInfo.getString("bookRefId"));

				XMLUtils.setValueAtXPath(bookingInfoElem, "./ns:Confirmation/@Type", confirmation.getString(JSON_PROP_TYPE));
				XMLUtils.setValueAtXPath(bookingInfoElem, "./ns:Confirmation/@ID", confirmation.getString(JSON_PROP_ID));

				JSONArray participantInfoJsonArr = activityInfo.getJSONArray(JSON_PROP_PARTICIPANT_INFO);
				Element participantInfoElemBlank = XMLUtils.getFirstElementAtXPath(wrapperElement,
						"./ns:OTA_TourActivityModifyRQ/ns:BookingInfo/ns:ParticipantInfo");
				XMLUtils.removeNode(participantInfoElemBlank);
				Element scheduleElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ns:OTA_TourActivityModifyRQ/ns:BookingInfo/ns:Schedule");

				getPaxTypeCountMap(activityInfo, paxTypeCountMap, i);

				for (int j = 0; j < participantInfoJsonArr.length(); j++) {
					JSONObject participantInfoJson = participantInfoJsonArr.getJSONObject(j);
					Element participantInfoElem = (Element) participantInfoElemBlank.cloneNode(true);
					XMLUtils.setValueAtXPath(participantInfoElem, XPATH_PARTICIPANT_CATEGORY_ID, participantInfoJson.getString(JSON_PROP_PARTICIPANT_CATEGORY_ID));
					XMLUtils.setValueAtXPath(participantInfoElem, XPATH_NS_QUALIFIER_INFO, ActivityPassengerType.valueOf(participantInfoJson.getString(JSON_PROP_QUALIFIERINFO)).toString());
					XMLUtils.setValueAtXPath(participantInfoElem, XPATH_NAME_PREFIX, participantInfoJson.getString(JSON_PROP_NAME_PREFIX));
					XMLUtils.setValueAtXPath(participantInfoElem, XPATH_NS_GIVEN_NAME, participantInfoJson.getString(JSON_PROP_GIVEN_NAME));
					XMLUtils.setValueAtXPath(participantInfoElem, XPATH_NS_MIDDLE_NAME, participantInfoJson.getString(JSON_PROP_MIDDLE_NAME));
					XMLUtils.setValueAtXPath(participantInfoElem, XPATH_NS_SURNAME, participantInfoJson.getString(JSON_PROP_SURNAME));
					XMLUtils.setValueAtXPath(participantInfoElem, XPATH_NS_NAME_TITLE, participantInfoJson.getString(JSON_PROP_NAME_TITLE));
					bookingInfoElem.insertBefore(participantInfoElem, scheduleElem);
				}

				XMLUtils.setValueAtXPath(scheduleElem, "./@Start", activityInfo.getString(JSON_PROP_START));
				XMLUtils.insertChildNode(reqElem, "./sig:RequestBody", wrapperElement, false);

			}

			// TODO Kafka msg before SI request.
			bookProducer.runProducer(1, kafkaMsgJson);


			logger.info("Before opening HttpURLConnection");
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), ActivitiesConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);

			if (resElem == null) {
				throw new Exception(ERROR_MSG_NULL_RESPONSE_SI);
			}


			Element[] tourActivityBookRSWrapperElems = XMLUtils.getElementsAtXPath(resElem,
					"./sig:ResponseBody/sig1:OTA_TourActivityBookRSWrapper");
			JSONObject resBodyJson = getSupplierResponseJSON(tourActivityBookRSWrapperElems, paxTypeCountMap);

			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
			Map<String, JSONObject> briActTourActMap = new HashMap<String, JSONObject>();

			JSONObject resSupplierComJson = SupplierCommercials.getSupplierCommercialsV2(CommercialsOperation.Book,
					reqJson, resJson);

			JSONObject resClientComJson = ClientCommercials.getClientCommercials(reqJson, resJson, resSupplierComJson);

			// ActivitySearchProcessor.calculatePrices(reqJson, resJson, resSupplierComJson,
			// resClientComJson,
			// briActTourActMap, usrCtx, true);

			ActivitySearchProcessor.calculatePricesV2(reqJson, resJson, resSupplierComJson, resClientComJson, true);


			TaxEngine.getCompanyTaxesV2(reqJson, resJson);


			kafkaMsgJson = resJson;
			createKafkaResponseJSON(entityName, reqBodyJson, kafkaMsgJson);


			ActivityRepriceProcessor.pushSuppFaresToRedisAndRemoveV2(reqJson, resJson);

			// TODO Kafka msg before SI request.
			bookProducer.runProducer(1, kafkaMsgJson);

			// resJson.remove(JSON_PROP_COMPANY_CHARGES);
			// resJson.remove(JSON_PROP_SUPPLIER_CHARGES);
			// resJson.remove(JSON_PROP_COMPANY_CHARGES_CURRENCY_CODE);
			// resJson.remove(JSON_PROP_SUPPLIER_CHARGES_CURRENCY_CODE);
			return resJson.toString();

		}

		catch (ValidationException valx) {
			throw valx;
		}

		// TODO: need to show the error message on wem rather than printing on console

		catch (Exception x) {
			x.printStackTrace();
			return STATUS_ERROR;
		}

	}

	private static void getPaxTypeCountMap(JSONObject activityInfo,
			HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap, int i) {

		HashMap<String, BigDecimal> paxCountMap = new HashMap<>();
		JSONArray paxInfoArr = activityInfo.getJSONArray("paxInfo");

		for (int m = 0; m < paxInfoArr.length(); m++) {
			JSONObject paxInfoJson = paxInfoArr.getJSONObject(m);
			paxCountMap.put(paxInfoJson.getString("paxType"), BigDecimal.valueOf(paxInfoJson.getInt("quantity")));
		}

		paxTypeCountMap.put(i, paxCountMap);
	}

	/**
	 * @param entityName
	 * @param reqBodyJson
	 * @param kafkaMsgJson
	 */
	private static void createKafkaResponseJSON(String entityName, JSONObject reqBodyJson, JSONObject kafkaMsgJson) {
		JSONObject resBodyJSON = kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY);

		resBodyJSON.put(JSON_PROP_PRODUCT, PRODUCT_CATEGORY);
		resBodyJSON.put(JSON_PROP_REQUEST_TYPE, JSON_PROP_AMEND);

		JSONArray actResArr = resBodyJSON.getJSONArray(JSON_PROP_ACTIVITYINFO);

		// for (int resActivityInfoCount = 0; resActivityInfoCount < actResArr.length();
		// resActivityInfoCount++) {
		// JSONObject kafkaDetailsTourActivityInfo =
		// resBodyJSON.getJSONArray(JSON_PROP_ACTIVITYINFO)
		// .getJSONObject(resActivityInfoCount).getJSONArray("tourActivityInfo").getJSONObject(0);
		//// JSONArray pricingArray =
		// kafkaDetailsTourActivityInfo.getJSONArray("pricing");
		//// String companyCharges = null;
		//// String companyChargesCurrencyCode = null;
		//// for (int pricingArrayCount = 0; pricingArrayCount < pricingArray.length();
		// pricingArrayCount++) {
		//// String particiapntCategory = pricingArray.getJSONObject(pricingArrayCount)
		//// .getString(JSON_PROP_PARTICIPANT_CATEGORY);
		//// if (JSON_PROP_SUMMARY.equals(particiapntCategory))
		//// companyCharges =
		// pricingArray.getJSONObject(pricingArrayCount).getBigDecimal(JSON_PROP_TOTAL_PRICE)
		//// .toString();
		//// companyChargesCurrencyCode = pricingArray.getJSONObject(pricingArrayCount)
		//// .getString(JSON_PROP_CURRENCY_CODE);
		//// }
		//
		//// JSONArray supplierPricingArray =
		// kafkaDetailsTourActivityInfo.getJSONObject(JSON_PROP_SUPP_PRICE_INFO)
		//// .getJSONArray(JSON_PROP_PRICING);
		//// String supplierCharges = null;
		//// String supplierChargesCurrencyCode = null;
		//// for (int suppPricingArrayCount = 0; suppPricingArrayCount <
		// supplierPricingArray
		//// .length(); suppPricingArrayCount++) {
		//// String particiapntCategory =
		// supplierPricingArray.getJSONObject(suppPricingArrayCount)
		//// .getString(JSON_PROP_PARTICIPANT_CATEGORY);
		//// if (JSON_PROP_SUMMARY.equals(particiapntCategory))
		//// supplierCharges = supplierPricingArray.getJSONObject(suppPricingArrayCount)
		//// .getBigDecimal(JSON_PROP_TOTAL_PRICE).toString();
		//// supplierChargesCurrencyCode =
		// supplierPricingArray.getJSONObject(suppPricingArrayCount)
		//// .getString(JSON_PROP_CURRENCY_CODE);
		//// }
		////
		//// kafkaDetailsTourActivityInfo.put(JSON_PROP_COMPANY_CHARGES,
		// companyCharges);
		//// kafkaDetailsTourActivityInfo.put(JSON_PROP_COMPANY_CHARGES_CURRENCY_CODE,
		// companyChargesCurrencyCode);
		//// kafkaDetailsTourActivityInfo.put(JSON_PROP_SUPPLIER_CHARGES,
		// supplierCharges);
		//// kafkaDetailsTourActivityInfo.put(JSON_PROP_SUPPLIER_CHARGES_CURRENCY_CODE,
		// supplierChargesCurrencyCode);
		//
		// kafkaDetailsTourActivityInfo.put(JSON_PROP_ENTITY_NAME, entityName);
		// StringBuilder entityId = new StringBuilder();
		// JSONArray participantInfoJsonArray =
		// reqBodyJson.getJSONArray(JSON_PROP_ACTIVITYINFO)
		// .getJSONObject(resActivityInfoCount).getJSONArray(JSON_PROP_PARTICIPANT_INFO);
		// for (int j = 0; j < participantInfoJsonArray.length(); j++) {
		// entityId.append(participantInfoJsonArray.getJSONObject(j).getString(JSON_PROP_PARTICIPANT_CATEGORY_ID))
		// .append("|");
		// }
		// entityId.setLength(entityId.length() - 1);
		//
		// kafkaDetailsTourActivityInfo.put(JSON_PROP_ENTITY_ID, entityId);
		//
		// String type =
		// reqBodyJson.getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(resActivityInfoCount)
		// .getString(JSON_PROP_TYPE);
		// kafkaDetailsTourActivityInfo.put(JSON_PROP_TYPE, type);
		//
		// }
	}

	private static JSONObject getConfirmation(String confirmationString) {

		JSONArray confirmationArray = new JSONArray();

		String[] split1Array = confirmationString.split("\\|");

		for (String split1String : split1Array) {
			JSONObject confirmationObject = new JSONObject();
			String[] split2Array = split1String.split(",");
			for (int split2Count = 0; split2Count < split2Array.length; split2Count++) {

				switch (split2Count) {
				case 0:
					confirmationObject.put(JSON_PROP_ID, split2Array[0]);
					break;
				case 1:
					confirmationObject.put(JSON_PROP_TYPE, split2Array[1]);
					break;
				case 2:
					confirmationObject.put(JSON_PROP_INSTANCE, split2Array[2]);
					break;
				}
			}
			confirmationArray.put(confirmationObject);
		}

		for (int confirmationCount = 0; confirmationCount < confirmationArray.length(); confirmationCount++) {
			if (confirmationArray.getJSONObject(confirmationCount).getString(JSON_PROP_TYPE).equalsIgnoreCase("14")) {
				return confirmationArray.getJSONObject(confirmationCount);
			}
		}
		return null;

	}

	private static JSONObject getSupplierResponseJSON(Element[] tourActivityBookRSWrapperElems,
			HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap) throws ValidationException {

		JSONObject resJson = new JSONObject();
		Element[] tourActivityElems;
		JSONArray activityInfoJsonArr = new JSONArray();
		resJson.put(JSON_PROP_ACTIVITYINFO, activityInfoJsonArr);
		for (int i = 0; i < tourActivityBookRSWrapperElems.length; i++) {
			Element tourActivityBookRSWrapperElement = tourActivityBookRSWrapperElems[i];
			JSONObject activityInfoJson = new JSONObject();
			JSONArray tourActivityJsonArr = new JSONArray();
			activityInfoJson.put(JSON_PROP_TOURACTIVITYINFO, tourActivityJsonArr);
			String supplierID = XMLUtils.getValueAtXPath(tourActivityBookRSWrapperElement, "./sig1:SupplierID");
			String sequence = XMLUtils.getValueAtXPath(tourActivityBookRSWrapperElement, "./sig1:Sequence");

			Element[] tourActivityBookRSElems = XMLUtils.getElementsAtXPath(tourActivityBookRSWrapperElement,
					"./ns:OTA_TourActivityBookRS");

			for (Element tourActivityBookRSElem : tourActivityBookRSElems) {
				if (XMLUtils.getValueAtXPath(tourActivityBookRSElem, "./ns:Success").equalsIgnoreCase("false")) {

					throw new ValidationException("ACTIVITYTEST_101");

					// TODO: when we get clarity on ErrorCodes and Messages we can pick Error codes
					// and messaged from the below tags from SI Response
					// Element[] errorElemArr = XMLUtils.getElementsAtXPath(resBodyElem,
					// "./ns:Errors");
					// for (Element errorElem : errorElemArr) {
					// System.out.println("Error wala Element: " +
					// XMLTransformer.toString(errorElem));
					// }

				}

				tourActivityElems = XMLUtils.getElementsAtXPath(tourActivityBookRSWrapperElement,
						"./ns:OTA_TourActivityBookRS/ns:ReservationDetails");
				for (Element tourActivityElem : tourActivityElems) {
					JSONObject tourActivityJson = getTourActivityJSON(tourActivityElem, supplierID,
							paxTypeCountMap.get(i));
					tourActivityJsonArr.put(tourActivityJson);
				}

			}

			activityInfoJsonArr.put(Integer.parseInt(sequence), activityInfoJson);

		}

		return resJson;
	}

	private static JSONObject getTourActivityJSON(Element tourActivityElem, String supplierID,
			HashMap<String, BigDecimal> paxCountMap) {
		JSONObject tourActivityJson = new JSONObject();
		tourActivityJson.put(SUPPLIER_ID, supplierID);
		tourActivityJson.put(JSON_PROP_BASICINFO,
				getBasicInfoJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:BasicInfo")));
		//		tourActivityJson.put(JSON_PROP_PRICING,
		//				getPricingJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Pricing"), supplierID));
		tourActivityJson.put("activityTotalPricingInfo", getPricingJsonV2(
				XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Pricing"), supplierID, paxCountMap));
		tourActivityJson.put(JSON_PROP_LOCATION, ActivitySearchProcessor
				.getLocationJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Location")));
		tourActivityJson.put(JSON_PROP_START, XMLUtils.getValueAtXPath(tourActivityElem, "./ns:Schedule/@Start"));
		tourActivityJson.put(JSON_PROP_PARTICIPANT_INFO,
				getParticipantInfoJson(XMLUtils.getElementsAtXPath(tourActivityElem, "./ns:ParticipantInfo")));
		return tourActivityJson;
	}

	private static JSONObject getBasicInfoJson(Element basicInfoElem) {
		JSONObject basicInfoJson = new JSONObject();
		basicInfoJson.put(JSON_PROP_SUPPLIERPRODUCTCODE,
				XMLUtils.getValueAtXPath(basicInfoElem, "./@SupplierProductCode"));
		basicInfoJson.put(JSON_PROP_NAME, XMLUtils.getValueAtXPath(basicInfoElem, "./@Name"));
		return basicInfoJson;

	}

	private static JSONArray getPricingJson(Element pricingElem, String supplierID) {
		JSONArray pricingJsonArr = new JSONArray();
		if (XMLUtils.getValueAtXPath(pricingElem, "./@PerPaxPriceInd").equalsIgnoreCase("true"))
			pricingJsonArr = ActivityPricing.paxPricing.getPricingJson(pricingJsonArr, pricingElem);
		else
			pricingJsonArr = ActivityPricing.summaryPricing.getPricingJson(pricingJsonArr, pricingElem);
		return pricingJsonArr;
	}

	private static JSONArray getParticipantInfoJson(Element[] participantInfoElems) {
		JSONArray participantInfoJsonArr = new JSONArray();
		for (Element participantInfoElem : participantInfoElems) {
			JSONObject participantInfoJson = new JSONObject();
			participantInfoJson.put(JSON_PROP_PARTICIPANT_CATEGORY_ID,
					XMLUtils.getValueAtXPath(participantInfoElem, XPATH_PARTICIPANT_CATEGORY_ID));
			participantInfoJson.put(JSON_PROP_QUALIFIERINFO, ActivityPassengerType
					.forString(XMLUtils.getValueAtXPath(participantInfoElem, XPATH_NS_QUALIFIER_INFO)));
			participantInfoJson.put(JSON_PROP_NAME_PREFIX,
					XMLUtils.getValueAtXPath(participantInfoElem, XPATH_NAME_PREFIX));
			participantInfoJson.put(JSON_PROP_GIVEN_NAME,
					XMLUtils.getValueAtXPath(participantInfoElem, XPATH_NS_GIVEN_NAME));
			participantInfoJson.put(JSON_PROP_MIDDLE_NAME,
					XMLUtils.getValueAtXPath(participantInfoElem, XPATH_NS_MIDDLE_NAME));
			participantInfoJson.put(JSON_PROP_SURNAME, XMLUtils.getValueAtXPath(participantInfoElem, XPATH_NS_SURNAME));
			participantInfoJson.put(JSON_PROP_NAME_TITLE,
					XMLUtils.getValueAtXPath(participantInfoElem, XPATH_NS_NAME_TITLE));
			participantInfoJson.put(JSON_PROP_STATUS, XMLUtils.getValueAtXPath(participantInfoElem,
					"./ns:Category/ns:TPA_Extensions/sig1:Activity_TPA/sig1:Status"));
			participantInfoJsonArr.put(participantInfoJson);
		}
		return participantInfoJsonArr;
	}

	public static JSONObject getPricingJsonV2(Element pricingElem, String supplierID,
			HashMap<String, BigDecimal> paxCountMap) {

		JSONObject activityTotalPricingInfo = new JSONObject();

		activityTotalPricingInfo.put("activitySummaryPrice",
				Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(pricingElem, "./ns:Summary/@Amount"), 0));
		activityTotalPricingInfo.put("currencyCode",
				XMLUtils.getValueAtXPath(pricingElem, "./ns:Summary/@CurrencyCode"));

		JSONArray paxPriceInfoArr = new JSONArray();

		activityTotalPricingInfo.put("activityWisePricing", true);

		BigDecimal summaryPrice = activityTotalPricingInfo.getBigDecimal("activitySummaryPrice").setScale(4);
		BigDecimal paxCount = new BigDecimal(0);
		BigDecimal paxPrice = new BigDecimal(0);

		for (Map.Entry<String, BigDecimal> entry : paxCountMap.entrySet()) {
			paxCount = paxCount.add(entry.getValue());
			// System.out.println(entry.getKey() + "/" + entry.getValue());
			JSONObject participantPricingJson = new JSONObject();
			participantPricingJson.put("participantCategory", entry.getKey());
			// participantPricingJson.put("totalPrice", paxPrice);
			participantPricingJson.put("currencyCode", activityTotalPricingInfo.getString("currencyCode"));
			participantPricingJson.put("age", "");

			participantPricingJson.put("quantity", entry.getValue());
			paxPriceInfoArr.put(participantPricingJson);

		}

		paxPrice = summaryPrice.divide(paxCount, 4);

		for (int i = 0; i < paxPriceInfoArr.length(); i++) {
			paxPriceInfoArr.getJSONObject(i).put("totalPrice", paxPrice);
		}

		activityTotalPricingInfo.put("paxPriceInfo", paxPriceInfoArr);

		// TODO: If PerPaxPriceInd comes as false? we calculate the prices by dividing
		// the activityTotalPrice by paxCount?

		return activityTotalPricingInfo;
	}

	
	public static String processV2(JSONObject reqJson) {
		
		try {
			
			TrackingContext.setTrackingContext(reqJson);
			JSONObject reqHdrJson = reqJson.getJSONObject(ActivityConstants.JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(ActivityConstants.JSON_PROP_REQBODY);
			
			
			
			//TODO: push into ops todo queue. As discussed with Shivam and Pritish on 18-10-10, pass requestBody as it is
			 //Call opsTodo
            
			String modType = reqBodyJson.getString("operation");
			
			ToDoTaskSubType todoSubType;
            ToDoTaskName todoTaskName=ToDoTaskName.AMEND;
            
                
            if(modType.endsWith("Pax"))
            	todoSubType=ToDoTaskSubType.PASSENGER;
            else
            	todoSubType=ToDoTaskSubType.ORDER;
            	
            OperationsToDoProcessor.callOperationTodo(todoTaskName, ToDoTaskPriority.MEDIUM, todoSubType,reqBodyJson.getString(JSON_PROP_ORDERID), reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID),reqHdrJson, reqBodyJson, "");
            
			
			
			JSONObject resJson = new JSONObject();
			
			
			
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			JSONObject resBodyJson = new JSONObject();
			
			resBodyJson.put(JSON_PROP_BOOKID, reqBodyJson.getString(JSON_PROP_BOOKID));
			resBodyJson.put(JSON_PROP_ORDERID, reqBodyJson.getString(JSON_PROP_ORDERID));
			resBodyJson.put("orderBookingDate", reqBodyJson.getString("orderBookingDate"));
			resBodyJson.put("enamblerSupplierName", reqBodyJson.getString("enamblerSupplierName"));
			resBodyJson.put("productCategory", reqBodyJson.getString("productCategory"));
			resBodyJson.put("productSubCategory", reqBodyJson.getString("productSubCategory"));
			resBodyJson.put("cityCode", reqBodyJson.getString("cityCode"));
			resBodyJson.put("operation", reqBodyJson.getString("operation"));
			resBodyJson.put("status", "On Request");
			
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			
			return resJson.toString();
		}
		
		
		
		catch (Exception x) {
			x.printStackTrace();
			return STATUS_ERROR;
		}
		
		
	}
}
