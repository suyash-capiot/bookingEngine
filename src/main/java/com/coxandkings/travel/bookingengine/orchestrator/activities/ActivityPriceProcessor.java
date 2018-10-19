package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class ActivityPriceProcessor implements ActivityConstants {
	//private static final String JSON_PROP_CACHE_ACTIVITIES = "ACTIVITIES";
	private static final Logger logger = LogManager.getLogger(ActivityPriceProcessor.class);

	public static String process(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException, ValidationException {

		// OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		Element reqElem = null;
		UserContext usrCtx = UserContext.getUserContextForSession(reqJson.getJSONObject(JSON_PROP_REQHEADER));

		HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap = new HashMap<>();

		try {

			
			opConfig = ActivitiesConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			// opConfig = ActivitiesConfig.getOperationConfig("price");
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);

			setSupplierRequestElem(reqJson, reqElem, usrCtx, paxTypeCountMap);

		} 

		catch (ValidationException valx) {
			throw valx;
		} 

		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		try {
			Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), "POST", opConfig.getServiceTimeoutMillis(), reqElem);

			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			JSONObject resJson = getSupplierResponse(resElem, reqJson, paxTypeCountMap);

			//			Map<String, JSONObject> briActTourActMap = new HashMap<String, JSONObject>();

			JSONObject resSupplierComJson = SupplierCommercials.getSupplierCommercialsV2(CommercialsOperation.Search,
					reqJson, resJson);

			JSONObject resClientComJson = ClientCommercials.getClientCommercials(reqJson, resJson, resSupplierComJson);

			ActivitySearchProcessor.calculatePricesV2(reqJson, resJson, resSupplierComJson, resClientComJson, false);

			// TODO: Apply company offers

			try {
				ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
				CommercialCacheProcessor.updateInCache(PRODUCT, clInfo[clInfo.length - 1].getCommercialsEntityId(),
						resJson, reqJson);
			} catch (Exception x) {
				logger.info("An exception was received while pushing price results in commercial cache", x);
			}

			return resJson.toString();

		}

		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}

	}

	public static void setSupplierRequestElem(JSONObject reqJson, Element reqElem, UserContext usrCtx,
			HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap) throws Exception {
		Document ownerDoc = reqElem.getOwnerDocument();
		XMLTransformer.toString(reqElem);
		Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem, "./sig:RequestBody/sig1:OTA_TourActivityAvailRQWrapper");
		XMLUtils.removeNode(blankWrapperElem);

		XMLTransformer.toString(reqElem);
		XMLTransformer.toString(blankWrapperElem);
		// TrackingContext.setTrackingContext(reqJson);
		JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null)? reqJson.optJSONObject(JSON_PROP_REQHEADER): new JSONObject();
		JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null)? reqJson.optJSONObject(JSON_PROP_REQBODY): new JSONObject();

		// ActivityRequestValidator.validatePriceRequest(reqHdrJson, reqBodyJson);

		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);

		JSONObject clientContext = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientMrkt = clientContext.getString(JSON_PROP_CLIENTMARKET);
		String clientID = clientContext.getString(JSON_PROP_CLIENTID);

		ActivitySearchProcessor.createSuppReqHdrElem(XMLUtils.getFirstElementAtXPath(reqElem, "./sig1:RequestHeader"), sessionID, transactionID, userID, clientMrkt, clientID);

		//setting pricing currency
		XMLUtils.setValueAtXPath(blankWrapperElem, "./ns:OTA_TourActivityAvailRQ/ns:ProcessingInformation/@PricingCurrency", clientContext.optString(JSON_PROP_CLIENTCURRENCY));

		String suppID;
		ProductSupplier prodSupplier;
		JSONArray actReqArr = reqBodyJson.getJSONArray(JSON_PROP_ACTIVITYINFO);
		Element wrapperElement, suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, XPATH_REQUESTHEADER_SUPPLIERCREDENTIALSLIST);

		for (int i = 0; i < actReqArr.length(); i++) {

			JSONObject activityInfo = actReqArr.getJSONObject(i);


			suppID = activityInfo.getString(SUPPLIER_ID);
			prodSupplier = usrCtx.getSupplierForProduct(PRODUCT_CATEGORY, PRODUCT_SUBCATEGORY, suppID);

			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}

			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, i));
			wrapperElement = (Element) blankWrapperElem.cloneNode(true);

			XMLUtils.setValueAtXPath(wrapperElement, "./sig1:SupplierID", suppID);
			XMLUtils.setValueAtXPath(wrapperElement, "./sig1:Sequence", String.valueOf(i));

			Element tourActivityElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ns:OTA_TourActivityAvailRQ/ns:TourActivity");

			setBasicInfoElem(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:BasicInfo"), activityInfo);

			setLocationElem(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Location"), activityInfo);

			//Setting Schedule
			XMLUtils.setValueAtXPath(tourActivityElem, "./ns:Schedule/@StartPeriod", activityInfo.getString(JSON_PROP_STARTDATE));
			XMLUtils.setValueAtXPath(tourActivityElem, "./ns:Schedule/@EndPeriod", activityInfo.getString(JSON_PROP_ENDDATE));

			getAdultChildParticipant(tourActivityElem, ownerDoc, activityInfo, paxTypeCountMap, i);

			XMLUtils.insertChildNode(reqElem, "./sig:RequestBody", wrapperElement, false);

		}
	}

	private static void setLocationElem(Element locationElem, JSONObject activityInfo) {
		XMLUtils.setValueAtXPath(locationElem, "./ns:Address/ns:CountryName/@Code", activityInfo.getString(JSON_PROP_COUNTRYCODE));
		XMLUtils.setValueAtXPath(locationElem, "./ns:Region/@RegionCode", activityInfo.getString(JSON_PROP_CITYCODE));
	}

	private static void setBasicInfoElem(Element basicInfoElem, JSONObject activityInfo) {

		basicInfoElem.setAttribute("SupplierProductCode", activityInfo.getString(JSON_PROP_SUPPLIERPRODUCTCODE));
		basicInfoElem.setAttribute("Name", activityInfo.optString(JSON_PROP_NAME));

		Element ativityTPAElem = XMLUtils.getFirstElementAtXPath(basicInfoElem, "./ns:TPA_Extensions/sig1:Activity_TPA");


		JSONObject tourLang = activityInfo.getJSONObject(JSON_PROP_TOURLANGUAGE);
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:TourLanguage/@Code", tourLang.optString(JSON_PROP_CODE));
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:TourLanguage/@LanguageListCode", tourLang.optString(JSON_PROP_LANGUAGELISTCODE));

		setShippingDetailsElem(ativityTPAElem, activityInfo.getJSONObject(JSON_PROP_SHIPPINGDETAILS));

		setSupplierDetailsElem(ativityTPAElem, activityInfo.getJSONObject(JSON_PROP_SUPPLIER_DETAILS));

		//setting TimeSlots | it will contain only one time slot
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:TimeSlots/sig:TimeSlot/@Code", activityInfo.optJSONObject(JSON_PROP_TIMESLOT).getString(JSON_PROP_CODE));

		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Nationality", activityInfo.optString(JSON_PROP_NATIONALITY));


	}

	private static void setSupplierDetailsElem(Element ativityTPAElem, JSONObject supplierDetails) {
		XMLTransformer.toString(ativityTPAElem);
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Supplier_Details/sig1:Name", supplierDetails.optString(JSON_PROP_NAME));
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Supplier_Details/sig1:ID", supplierDetails.optString(JSON_PROP_ID));
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Supplier_Details/sig1:Reference", supplierDetails.optString(JSON_PROP_REFERENCE));
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Supplier_Details/sig1:RateKey", supplierDetails.optString(JSON_PROP_RATEKEY));
	}

	private static void setShippingDetailsElem(Element ativityTPAElem, JSONObject shipping_Details) {
		XMLTransformer.toString(ativityTPAElem);
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Shipping_Details/sig1:ID", shipping_Details.optString(JSON_PROP_ID));
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Shipping_Details/sig1:OptionName", shipping_Details.optString(JSON_PROP_OPTION_NAME));
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Shipping_Details/sig1:Details", shipping_Details.optString(JSON_PROP_DETAILS));
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Shipping_Details/sig1:ServiceFee", shipping_Details.optString(JSON_PROP_SERVICEFEE));

		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Shipping_Details/sig1:ShippingAreas/sig1:RateList/sig1:AreaID", shipping_Details.getJSONObject(JSON_PROP_SHIPPINGAREAS).optString(JSON_PROP_AREAID));
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Shipping_Details/sig1:ShippingAreas/sig1:RateList/sig1:Name", shipping_Details.getJSONObject(JSON_PROP_SHIPPINGAREAS).optString(JSON_PROP_NAME));
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Shipping_Details/sig1:ShippingAreas/sig1:RateList/sig1:Cost", shipping_Details.getJSONObject(JSON_PROP_SHIPPINGAREAS).optString(JSON_PROP_COST));

		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Shipping_Details/sig1:TotalCost", shipping_Details.optString(JSON_PROP_TOTAL_COST));
		XMLUtils.setValueAtXPath(ativityTPAElem, "./sig1:Shipping_Details/sig1:Currency", shipping_Details.optString(JSON_PROP_CURRENCY));
	}

	private static void getAdultChildParticipant(Element tourActivityElem, Document ownerDoc, JSONObject activityInfo, HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap, int index) {

		HashMap<String, BigDecimal> paxCountMap = new HashMap<>();
		paxCountMap.put("ADT", BigDecimal.valueOf(0));
		paxCountMap.put("CHD", BigDecimal.valueOf(0));
		paxCountMap.put("INF", BigDecimal.valueOf(0));


		JSONArray paxInfoArr = activityInfo.getJSONArray(JSON_PROP_PAXINFO);

		Element participantCountDef = XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:ParticipantCount");
		XMLTransformer.toString(participantCountDef);
		XMLUtils.removeNode(participantCountDef);

		for(int i = 0; i<paxInfoArr.length(); i++) {
			String paxType = paxInfoArr.getJSONObject(i).getString(JSON_PROP_PAXTYPE);
			int quantity = paxInfoArr.getJSONObject(i).getInt(JSON_PROP_QUANTITY);
			
			paxCountMap.put(paxType, paxCountMap.get(paxType).add(new BigDecimal(quantity)));

			for(int j = 0; j<quantity; j++) {
				Element participantCount = ownerDoc.createElementNS(NS_OTA, "ns:ParticipantCount");
				participantCount.setAttribute("Quantity", "1");
				Element qualifierInfo = ownerDoc.createElementNS(NS_OTA, "ns:QualifierInfo");
				// qualifierInfo.setAttribute("Extension", "1");
				qualifierInfo.setTextContent(ActivityPassengerType.valueOf(paxType).toString());
				participantCount.appendChild(qualifierInfo);
				tourActivityElem.appendChild(participantCount);
			}
		}

		paxTypeCountMap.put(index, paxCountMap);


		//		for (int m = 0; m < adultCount; m++) {
		//
		//			Element participantCount = ownerDoc.createElementNS(NS_OTA, "ns:ParticipantCount");
		//			participantCount.setAttribute("Quantity", "1");
		//			Element qualifierInfo = ownerDoc.createElementNS(NS_OTA, "ns:QualifierInfo");
		//			// qualifierInfo.setAttribute("Extension", "1");
		//			qualifierInfo.setTextContent(JSON_PROP_ADULT);
		//			participantCount.appendChild(qualifierInfo);
		//			tourActivityElem.appendChild(participantCount);
		//
		//		}
		//
		//		for (int m = 0; m < childCount; m++) {
		//
		//			Element participantCount = ownerDoc.createElementNS(NS_OTA, "ns:ParticipantCount");
		//			participantCount.setAttribute("Quantity", "1");
		//			Element qualifierInfo = ownerDoc.createElementNS(NS_OTA, "ns:QualifierInfo");
		//			// qualifierInfo.setAttribute("Extension", "1");
		//			qualifierInfo.setTextContent(JSON_PROP_CHILD);
		//			participantCount.appendChild(qualifierInfo);
		//			tourActivityElem.appendChild(participantCount);
		//
		//		}
	}

	private static JSONObject getSupplierResponse(Element resElem, JSONObject reqJson,
			HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap) throws ValidationException {

		JSONObject resBodyJson = new JSONObject();
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);

		JSONArray activityInfoJsonArr = new JSONArray();
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

		Element[] tourActivityAvailRSWrapperElems = XMLUtils.getElementsAtXPath(resElem, "./sig:ResponseBody/sig1:OTA_TourActivityAvailRSWrapper");

		Element[] tourActivityElems;

		resBodyJson.put(JSON_PROP_ACTIVITYINFO, activityInfoJsonArr);
		for (int i = 0; i < tourActivityAvailRSWrapperElems.length; i++) {
			Element resBodyElem = tourActivityAvailRSWrapperElems[i];
			JSONObject activityInfoJson = new JSONObject();
			JSONArray tourActivityJsonArr = new JSONArray();
			activityInfoJson.put(JSON_PROP_TOURACTIVITYINFO, tourActivityJsonArr);
			String supplierID = XMLUtils.getValueAtXPath(resBodyElem, "./sig1:SupplierID");
			String sequence = XMLUtils.getValueAtXPath(resBodyElem, "./sig1:Sequence");
			Element[] tourActivityAvailRSElems = XMLUtils.getElementsAtXPath(resBodyElem,
					"./ns:OTA_TourActivityAvailRS");

			for (Element tourActivityAvailRSElem : tourActivityAvailRSElems) {

				JSONObject tourActivityJson = new JSONObject();

				if (XMLUtils.getValueAtXPath(tourActivityAvailRSElem, "./ns:Success").equalsIgnoreCase("false")) {

					ActivitySearchProcessor.validateSupplierResponse(supplierID, tourActivityAvailRSElem);

				} else {
					tourActivityElems = XMLUtils.getElementsAtXPath(tourActivityAvailRSElem, "./ns:TourActivityInfo");
					for (Element tourActivityElem : tourActivityElems) {
						tourActivityJson = getTourActivityJSON(tourActivityElem, supplierID, reqBodyJson.getJSONArray("activityInfo").getJSONObject(i), paxTypeCountMap.get(i));
						tourActivityJson.put(SUPPLIER_ID, supplierID);
						tourActivityJsonArr.put(tourActivityJson);
					}
				}
				activityInfoJsonArr.put(Integer.parseInt(sequence), activityInfoJson);
			}
		}

		return resJson;

	}


	private static JSONObject getTourActivityJSON(Element tourActivityElem, String supplierID, JSONObject reqTourActivity, HashMap<String, BigDecimal> paxCountMap) {
		
		JSONObject tourActivityJson = new JSONObject();
		tourActivityJson.put(JSON_PROP_BASICINFO, getBasicInfoJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:BasicInfo")));
		tourActivityJson.put(JSON_PROP_SCHEDULE, getScheduleJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Schedule")));
		tourActivityJson.put(JSON_PROP_COMMISIONINFO, getCommisionInfoJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:CommissionInfo")));
		tourActivityJson.put(JSON_PROP_CATEGORYANDTYPE, getCategoryTypeJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:CategoryAndType")));
		tourActivityJson.put(JSON_PROP_DESCRIPTION, getDescriptionJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Description")));
		tourActivityJson.put(JSON_PROP_EXTRA, getExtraJson(XMLUtils.getElementsAtXPath(tourActivityElem, "./ns:Extra")));
		tourActivityJson.put(JSON_PROP_LOCATION, ActivitySearchProcessor .getLocationJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Location")));
		tourActivityJson.put(JSON_PROP_SUPPLIEROPERATOR, getSupplieroperatorJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:SupplierOperator")));

		tourActivityJson.put("activityTotalPricingInfo", ActivitySearchProcessor.getPricingJsonV2(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Pricing"), supplierID, paxCountMap));
		tourActivityJson.put(JSON_PROP_PICKUPDROPOFF, getPickUpdropOffJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:PickupDropoff")));

		return tourActivityJson;
	}


	private static JSONObject getPickUpdropOffJson(Element pickUpdropOffJsonElem) {
		JSONObject pickupDropOffJson = new JSONObject();
		pickupDropOffJson.put(JSON_PROP_MEETINGLOCATION,
				XMLUtils.getValueAtXPath(pickUpdropOffJsonElem, "./@MeetingLocation"));
		pickupDropOffJson.put(JSON_PROP_PICKUPIND, XMLUtils.getValueAtXPath(pickUpdropOffJsonElem, "./@PickupInd"));
		pickupDropOffJson.put(JSON_PROP_OTHERINFO, XMLUtils.getValueAtXPath(pickUpdropOffJsonElem, "./@OtherInfo"));

		return pickupDropOffJson;
	}

	private static JSONObject getSupplieroperatorJson(Element supplierOperatorElem) {
		JSONObject supplierOpeatorJson = new JSONObject();
		supplierOpeatorJson.put(JSON_PROP_PHONE_NUMBER,
				XMLUtils.getValueAtXPath(supplierOperatorElem, "./ns:Contact/ns:Telephone/@PhoneNumber"));
		return supplierOpeatorJson;
	}


	private static JSONArray getExtraJson(Element[] extraElem) {
		JSONArray extras = new JSONArray();

		for (Element extra : extraElem) {
			JSONObject extraJson = new JSONObject();
			extraJson.put(JSON_PROP_NAME, XMLUtils.getValueAtXPath(extra, "./@Name"));
			extraJson.put(JSON_PROP_DESCRIPTION, XMLUtils.getValueAtXPath(extra, "./@Description"));

			extras.put(extraJson);
		}
		return extras;
	}

	private static JSONObject getCommisionInfoJson(Element commisionInfoElem) {
		JSONObject commisionInfo = new JSONObject();

		commisionInfo.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(commisionInfoElem, "./ns:CommissionPayableAmount/@CurrencyCode"));
		commisionInfo.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(commisionInfoElem, "./ns:CommissionPayableAmount/@Amount"));

		return commisionInfo;
	}

	private static JSONObject getCategoryTypeJson(Element categoryTypeElem) {
		JSONObject categoryTypeJson = new JSONObject();
		Element[] categoryElems = XMLUtils.getElementsAtXPath(categoryTypeElem, "./ns:Category");
		Element[] typeElems = XMLUtils.getElementsAtXPath(categoryTypeElem, "./ns:Type");

		for (Element categoryElem : categoryElems) {
			categoryTypeJson.append(JSON_PROP_CATEGORY, XMLUtils.getValueAtXPath(categoryElem, "./@Code"));
		}
		for (Element typeElem : typeElems) {
			categoryTypeJson.append(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(typeElem, "./@Code"));
		}
		return categoryTypeJson;
	}

	private static JSONObject getDescriptionJson(Element descElem) {
		JSONObject descJson = new JSONObject();
		descJson.put(JSON_PROP_SHORTDESCRIPTION, XMLUtils.getValueAtXPath(descElem, "./ns:ShortDescription"));
		descJson.put(JSON_PROP_LONGDESCRIPTION, XMLUtils.getValueAtXPath(descElem, "./ns:LongDescription"));
		return descJson;
	}

	private static JSONObject getScheduleJson(Element scheduleElem) {
		JSONObject scheduleJson = new JSONObject();

		Element[] detailElems = XMLUtils.getElementsAtXPath(scheduleElem, "./ns:Detail");

		for (Element detail : detailElems) {

			JSONObject detailJson = new JSONObject();

			detailJson.put(JSON_PROP_STARTDATE, XMLUtils.getValueAtXPath(detail, "./@Start"));
			detailJson.put(JSON_PROP_ENDDATE, XMLUtils.getValueAtXPath(detail, "./@End"));
			detailJson.put(JSON_PROP_DURATION, XMLUtils.getValueAtXPath(detail, "./@Duration"));

			Element[] operationTimeElems = XMLUtils.getElementsAtXPath(detail, "./ns:OperationTimes/ns:OperationTime");
			for (Element opTime : operationTimeElems) {
				JSONObject opTimeJson = new JSONObject();
				String[] days = { "Mon", "Tue", "Weds", "Thur", "Fri", "Sat", "Sun" };
				getDays(opTime, opTimeJson, days);
				opTimeJson.put(JSON_PROP_STARTTIME, XMLUtils.getValueAtXPath(opTime, "./@Start"));
				detailJson.append(JSON_PROP_OPERATIONTIMES, opTimeJson);
			}

			scheduleJson.append(JSON_PROP_DETAILS, detailJson);
		}

		return scheduleJson;
	}

	/**
	 * @param opTime
	 * @param opTimeJson
	 *            The method is used to get Days Array for the activity
	 */
	private static void getDays(Element opTime, JSONObject opTimeJson, String[] daysArray) {
		List<String> days = new ArrayList<>();

		for (String day : daysArray) {
			if ("true".equals(XMLUtils.getValueAtXPath(opTime, "./@" + day))) {
				days.add(day);
			}
		}
		opTimeJson.put(JSON_PROP_DAYS, days.toArray());
	}

	private static JSONObject getBasicInfoJson(Element basicInfoElem) {
		JSONObject basicInfoJson = new JSONObject();

		basicInfoJson.put(JSON_PROP_SUPPLIERPRODUCTCODE,
				XMLUtils.getValueAtXPath(basicInfoElem, "./@SupplierProductCode"));
		basicInfoJson.put(JSON_PROP_NAME, XMLUtils.getValueAtXPath(basicInfoElem, "./@Name"));
		basicInfoJson.put(JSON_PROP_UNIQUEKEY,
				XMLUtils.getValueAtXPath(basicInfoElem, "./ns:TPA_Extensions/sig1:Activity_TPA/sig1:UniqueKey"));

		getTourLanguages(basicInfoElem, basicInfoJson);
		getSupplierDetails(basicInfoElem, basicInfoJson);
		basicInfoJson.put(JSON_PROP_AVAILABILITYSTATUS, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Availability_Status"));
		getShippingDetails(basicInfoElem, basicInfoJson);
		getParticipant(basicInfoElem, basicInfoJson);

		JSONArray timeSlots = new JSONArray();
		getTimeSlot(basicInfoElem, basicInfoJson, timeSlots);

		return basicInfoJson;

	}

	private static void getTimeSlot(Element basicInfoElem, JSONObject basicInfoJson, JSONArray timeSlots) {
		Element[] timeSlotElems = XMLUtils.getElementsAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:TimeSlots/sig1:TimeSlot");

		for (Element timeSlotElem : timeSlotElems) {
			JSONObject timeSlotObj = new JSONObject();
			timeSlotObj.put(JSON_PROP_CODE, XMLUtils.getValueAtXPath(timeSlotElem, "./@code"));
			timeSlotObj.put(JSON_PROP_STARTTIME, XMLUtils.getValueAtXPath(timeSlotElem, "./@startTime"));
			timeSlotObj.put(JSON_PROP_ENDTIME, XMLUtils.getValueAtXPath(timeSlotElem, "./@endTime"));
			timeSlots.put(timeSlotObj);
		}

		basicInfoJson.put("timeSlots", timeSlots);
	}

	private static void getParticipant(Element basicInfoElem, JSONObject basicInfoJson) {
		JSONObject participant = new JSONObject();
		participant.put(JSON_PROP_MININDIVIDUALS, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Participant/@minIndividuals"));
		participant.put(JSON_PROP_MAXINDIVIDUALS, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Participant/@maxIndividuals"));
		participant.put(JSON_PROP_MINADULTAGE, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Participant/@minAdultAge"));
		participant.put(JSON_PROP_MAXADULTAGE, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Participant/@maxAdultAge"));
		participant.put(JSON_PROP_MINCHILDAGE, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Participant/@minChildAge"));
		participant.put(JSON_PROP_MAXCHILDAGE, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Participant/@maxChildAge"));
		participant.put(JSON_PROP_MININFANTAGE, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Participant/@minInfantAge"));
		participant.put(JSON_PROP_MAXINFANTAGE, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Participant/@maxInfantAge"));
		participant.put(JSON_PROP_ALLOWCHILDREN, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Participant/@allowChildren"));
		participant.put(JSON_PROP_ALLOWINFANTS, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Participant/@allowInfants"));

		basicInfoJson.put(JSON_PROP_PARTICIPANT, participant);
	}

	private static void getShippingDetails(Element basicInfoElem, JSONObject basicInfoJson) {
		JSONObject shippingDetails = new JSONObject();
		shippingDetails.put(JSON_PROP_ID, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Shipping_Details/sig1:ID"));
		shippingDetails.put("optionName", XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Shipping_Details/sig1:OptionName"));
		shippingDetails.put(JSON_PROP_DETAILS, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Shipping_Details/sig1:Details"));

		getShippingAreas(basicInfoElem, shippingDetails);

		shippingDetails.put(JSON_PROP_TOTALCOST, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Shipping_Details/sig1:TotalCost"));
		shippingDetails.put("currency", XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Shipping_Details/sig1:Currency"));

		basicInfoJson.put(JSON_PROP_SHIPPINGDETAILS, shippingDetails);
	}

	private static void getShippingAreas(Element basicInfoElem, JSONObject shippingDetails) {
		JSONObject shippingAreas = new JSONObject();
		shippingAreas.put(JSON_PROP_AREAID, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Shipping_Details/sig1:RateList/sig1:AreaID"));
		shippingAreas.put(JSON_PROP_NAME, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Shipping_Details/sig1:RateList/sig1:Name"));
		shippingAreas.put("cost", XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Shipping_Details/sig1:RateList/sig1:Cost"));

		shippingDetails.append(JSON_PROP_SHIPPINGAREAS, shippingAreas);
	}

	/**
	 * @param basicInfoElem
	 * @param basicInfoJson
	 *            The method is used to get Json array of TourLanguages
	 */
	private static void getTourLanguages(Element basicInfoElem, JSONObject basicInfoJson) {
		Element[] tourLanguageElems = XMLUtils.getElementsAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:TourLanguage");
		JSONArray tourlanguages = new JSONArray();
		for (Element tourLanguageElem : tourLanguageElems) {
			JSONObject tourlanguage = new JSONObject();
			tourlanguage.put(JSON_PROP_CODE, tourLanguageElem.getAttribute("Code"));
			tourlanguage.put(JSON_PROP_LANGUAGELISTCODE, tourLanguageElem.getAttribute("LanguageListCode"));
			tourlanguage.put(JSON_PROP_VALUE, tourLanguageElem.getTextContent());
			tourlanguages.put(tourlanguage);
		}

		basicInfoJson.put(JSON_PROP_TOURLANGUAGE, tourlanguages);
	}

	private static void getSupplierDetails(Element basicInfoElem, JSONObject basicInfoJson) {
		JSONObject supplierDetailsJson = new JSONObject();
		supplierDetailsJson.put(JSON_PROP_RATEKEY, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Supplier_Details/sig1:RateKey"));
		supplierDetailsJson.put(JSON_PROP_REFERENCE, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Supplier_Details/sig1:Reference"));
		supplierDetailsJson.put(JSON_PROP_NAME, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Supplier_Details/sig1:name"));
		supplierDetailsJson.put(JSON_PROP_ID, XMLUtils.getValueAtXPath(basicInfoElem,
				"./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Supplier_Details/sig1:ID"));
		basicInfoJson.append(JSON_PROP_SUPPLIER_DETAILS, supplierDetailsJson);
	}


	@SuppressWarnings("unused")
	private static void getEndDate(Element reqElem, JSONObject reqBodyJson) {
		if (reqBodyJson.getString(JSON_PROP_ENDDATE) != null && !reqBodyJson.getString(JSON_PROP_ENDDATE).isEmpty()) {
			XMLUtils.setValueAtXPath(reqElem,
					"./sig:RequestBody/sig1:OTA_TourActivityAvailRQWrapper/ns:OTA_TourActivityAvailRQ/ns:TourActivity/ns:Schedule/@EndPeriod",
					reqBodyJson.getString(JSON_PROP_ENDDATE));
		} else {
			XMLUtils.setValueAtXPath(reqElem,
					"./sig:RequestBody/sig1:OTA_TourActivityAvailRQWrapper/ns:OTA_TourActivityAvailRQ/ns:TourActivity/ns:Schedule/@EndPeriod",
					LocalDate.now().plusMonths(1).format(ActivityConstants.mDateFormat));
		}
	}

	@SuppressWarnings("unused")
	private static void getStartDate(Element reqElem, JSONObject reqBodyJson) {
		if (reqBodyJson.getString(JSON_PROP_STARTDATE) != null
				&& !reqBodyJson.getString(JSON_PROP_STARTDATE).isEmpty()) {
			XMLUtils.setValueAtXPath(reqElem,
					"./sig:RequestBody/sig1:OTA_TourActivityAvailRQWrapper/ns:OTA_TourActivityAvailRQ/ns:TourActivity/ns:Schedule/@StartPeriod",
					reqBodyJson.getString(JSON_PROP_STARTDATE));
		} else {
			XMLUtils.setValueAtXPath(reqElem,
					"./sig:RequestBody/sig1:OTA_TourActivityAvailRQWrapper/ns:OTA_TourActivityAvailRQ/ns:TourActivity/ns:Schedule/@StartPeriod",
					LocalDate.now().format(ActivityConstants.mDateFormat));
		}
	}

	@SuppressWarnings("unused")
	private static void getParticipant(Document ownerDoc, JSONArray participantDetails, Element tourActivity,
			String participantType) {
		for (int i = 0; i < participantDetails.length(); i++) {
			JSONObject adultSpecification = (JSONObject) participantDetails.get(i);

			Element participantCount = ownerDoc.createElementNS(NS_OTA, "ns:ParticipantCount");
			participantCount.setAttribute("Quantity", "1");

			String DOB = (String) adultSpecification.get(JSON_PROP_DOB);
			String[] dateOFBirthArray = DOB.split("-");
			int date = Integer.parseInt(dateOFBirthArray[2]);
			int month = Integer.parseInt(dateOFBirthArray[1]);
			int year = Integer.parseInt(dateOFBirthArray[0]);
			LocalDate birthDate = LocalDate.of(year, month, date);
			LocalDate currentDate = LocalDate.now();

			participantCount.setAttribute("Age", Integer.toString(Period.between(birthDate, currentDate).getYears()));

			Element qualifierInfo = ownerDoc.createElementNS(NS_OTA, "ns:QualifierInfo");

			qualifierInfo.setTextContent(participantType);

			participantCount.appendChild(qualifierInfo);

			tourActivity.appendChild(participantCount);

		}
	}

}
