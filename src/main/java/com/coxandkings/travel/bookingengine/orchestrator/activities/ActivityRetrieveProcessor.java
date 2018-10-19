package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class ActivityRetrieveProcessor implements ActivityConstants{
	private static final Logger logger = LogManager.getLogger(ActivityRetrieveProcessor.class);

	public static String process(JSONObject reqJson) {
		try {
			//OperationConfig opConfig = ActivitiesConfig.getOperationConfig("retrieve");
			ServiceConfig opConfig = ActivitiesConfig.getOperationConfig("retrieve");
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();
			Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./sig:RequestBody/sig1:OTA_ReadRQWrapper");
			XMLUtils.removeNode(blankWrapperElem);

			TrackingContext.setTrackingContext(reqJson);

			JSONObject reqHdrJson = reqJson.getJSONObject(ActivityConstants.JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(ActivityConstants.JSON_PROP_REQBODY);

			String sessionID = reqHdrJson.getString(ActivityConstants.JSON_PROP_SESSIONID);
			String transactionID = reqHdrJson.getString(ActivityConstants.JSON_PROP_TRANSACTID);
			String userID = reqHdrJson.getString(ActivityConstants.JSON_PROP_USERID);

			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			
			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_SESSIONID, sessionID);
			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_TRANSACTIONID, transactionID);
			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_USERID, userID);


			JSONArray multiReqArr = reqBodyJson.getJSONArray(JSON_PROP_RESERVATIONS);

			for (int j = 0; j < multiReqArr.length(); j++) {
				ProductSupplier prodSupplier;
				JSONObject reservationDetail = multiReqArr.getJSONObject(j);
				String suppID = reservationDetail.getString(ActivityConstants.SUPPLIER_ID);
				prodSupplier = usrCtx.getSupplierForProduct(ActivityConstants.PRODUCT_CATEGORY,ActivityConstants.PRODUCT_SUBCATEGORY, suppID);

				if (prodSupplier == null) {
					throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
				}
				XMLUtils.insertChildNode(reqElem, XPATH_REQUESTHEADER_SUPPLIERCREDENTIALSLIST,
						prodSupplier.toElement(ownerDoc, j), false);
				
				

				Element wrapperElement = (Element) blankWrapperElem.cloneNode(true);
				Element readRQElement = XMLUtils.getFirstElementAtXPath(wrapperElement,"./ns:OTA_ReadRQ");
				Document readRQOwner = readRQElement.getOwnerDocument();
				
				XMLUtils.setValueAtXPath(wrapperElement, "./sig1:SupplierID",
						reservationDetail.getString(SUPPLIER_ID));
				XMLUtils.setValueAtXPath(wrapperElement, "./sig1:Sequence", String.valueOf(j));
				
				XMLUtils.setValueAtXPath(wrapperElement, "./ns:OTA_ReadRQ/ns:POS/ns:Source/@ISOCurrency",
						reservationDetail.getString(JSON_PROP_ISO_CURRENCY));
				String con[] = reservationDetail.getString(JSON_PROP_CONFIRMATION).split("\\|");
				for (String str2 : con) {
					XMLUtils.insertChildNode(wrapperElement, "./ns:OTA_ReadRQ", getUniqueElement(readRQOwner, str2),
							false);
				}
				XMLUtils.insertChildNode(reqElem, "./sig:RequestBody", wrapperElement, false);

			}

			Element resElem = null;
			logger.trace(String.format("SI XML Request = %s", XMLTransformer.toString(reqElem)));
			//resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), ActivitiesConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception(ERROR_MSG_NULL_RESPONSE_SI);
			}
			logger.trace(String.format("SI XML Response = %s", XMLTransformer.toString(resElem)));
			
			Element[] resBodyElem = XMLUtils.getElementsAtXPath(resElem,
					"./sig:ResponseBody/sig1:OTA_TourActivityResRetrieveRSIntWrapper");
			
			JSONObject resBodyJson = getSupplierResponseJSON(resBodyElem);
			
			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			
			return resJson.toString();

		} catch (Exception e) {
			e.printStackTrace();
			return STATUS_ERROR;
		}

	}
	
	private static JSONObject getSupplierResponseJSON(Element[] resBodyElems) {
		JSONObject resJson = new JSONObject();
		Element[] retrieveRSElems;
		JSONArray activityInfoJsonArr = new JSONArray();
		resJson.put(JSON_PROP_ACTIVITYINFO, activityInfoJsonArr);
		for (Element resBodyElem : resBodyElems) {
			JSONObject activityInfoJson = new JSONObject();
			JSONArray tourActivityJsonArr = new JSONArray();
			activityInfoJson.put(JSON_PROP_TOURACTIVITYINFO, tourActivityJsonArr);
			String supplierID = XMLUtils.getValueAtXPath(resBodyElem, "./sig1:SupplierID");
			String sequence=XMLUtils.getValueAtXPath(resBodyElem, "./sig1:Sequence");
			retrieveRSElems = XMLUtils.getElementsAtXPath(resBodyElem,
					"./ns:OTA_TourActivityResRetrieveRS/ns:Detail");
			for (Element retrieveRSElem : retrieveRSElems) {
				JSONObject tourActivityJson = getTourActivityJSON(retrieveRSElem, supplierID);
				tourActivityJson.put(SUPPLIER_ID, supplierID);
				tourActivityJsonArr.put(tourActivityJson);
			}
			activityInfoJsonArr.put(Integer.parseInt(sequence),activityInfoJson);
		}

		return resJson;
	}

	private static JSONObject getTourActivityJSON(Element retrieveRSElem, String supplierID) {
		JSONObject tourActivityJson = new JSONObject();
		tourActivityJson.put(JSON_PROP_BASICINFO,
				getBasicInfoJson(XMLUtils.getFirstElementAtXPath(retrieveRSElem, "./ns:BasicInfo")));
		tourActivityJson.put(JSON_PROP_SCHEDULE,
				getScheduleJson(XMLUtils.getFirstElementAtXPath(retrieveRSElem, "./ns:Schedule")));
		tourActivityJson.put(JSON_PROP_COMMISIONINFO,
				getCommisionInfoJson(XMLUtils.getFirstElementAtXPath(retrieveRSElem, "./ns:CommissionInfo")));
		tourActivityJson.put(JSON_PROP_CATEGORYANDTYPE,
				getCategoryTypeJson(XMLUtils.getFirstElementAtXPath(retrieveRSElem, "./ns:CategoryAndType")));
		tourActivityJson.put(JSON_PROP_DESCRIPTION,
				getDescriptionJson(XMLUtils.getFirstElementAtXPath(retrieveRSElem, "./ns:Description")));
		tourActivityJson.put(JSON_PROP_EXTRA, getExtraJson(XMLUtils.getElementsAtXPath(retrieveRSElem, "./ns:Extra")));
		tourActivityJson.put(JSON_PROP_LOCATION,
				getLocationJson(XMLUtils.getFirstElementAtXPath(retrieveRSElem, "./ns:Location")));
		tourActivityJson.put(JSON_PROP_SUPPLIEROPERATOR,
				getSupplieroperatorJson(XMLUtils.getFirstElementAtXPath(retrieveRSElem, "./ns:SupplierOperator")));
		tourActivityJson.put(JSON_PROP_PRICING,
				getPricingJson(XMLUtils.getFirstElementAtXPath(retrieveRSElem, "./ns:Pricing"), supplierID));
		tourActivityJson.put(JSON_PROP_PICKUPDROPOFF,
				getPickUpdropOffJson(XMLUtils.getFirstElementAtXPath(retrieveRSElem, "./ns:PickupDropoff")));

		return tourActivityJson;
	}

	private static JSONObject getPickUpdropOffJson(Element pickUpdropOffJsonElem) {
		JSONObject pickupDropOffJson = new JSONObject();
		pickupDropOffJson.put(JSON_PROP_MEETINGLOCATION, XMLUtils.getValueAtXPath(pickUpdropOffJsonElem, "./@MeetingLocation"));
		pickupDropOffJson.put(JSON_PROP_PICKUPIND, XMLUtils.getValueAtXPath(pickUpdropOffJsonElem, "./@PickupInd"));
		pickupDropOffJson.put(JSON_PROP_OTHERINFO, XMLUtils.getValueAtXPath(pickUpdropOffJsonElem, "./@OtherInfo"));

		return pickupDropOffJson;
	}

	private static JSONArray getPricingJson(Element pricingElem, String supplierID) {
		JSONArray pricingJsonArr = new JSONArray();
		pricingJsonArr = ActivityPricing.suppRepricePricing.get(supplierID).getPricingJson(pricingJsonArr, pricingElem);
		return pricingJsonArr;
	}

	private static JSONObject getSupplieroperatorJson(Element supplierOperatorElem) {
		JSONObject supplierOpeatorJson = new JSONObject();
		supplierOpeatorJson.put(JSON_PROP_PHONE_NUMBER,
				XMLUtils.getValueAtXPath(supplierOperatorElem, "./ns:Contact/ns:Telephone/@PhoneNumber"));
		return supplierOpeatorJson;
	}

	private static JSONObject getLocationJson(Element locationElem) {

		JSONObject addressJson = new JSONObject();
		addressJson.put(JSON_PROP_STATEPROV, XMLUtils.getValueAtXPath(locationElem, "./ns:Address/ns:StateProv/@StateCode"));
		addressJson.put(JSON_PROP_COUNTRYNAME, XMLUtils.getValueAtXPath(locationElem, "./ns:Address/ns:CountryName"));
		addressJson.put(JSON_PROP_ADDRESSLINE, XMLUtils.getValueAtXPath(locationElem, "./ns:Address/ns:AddressLine"));

		JSONObject positionJson = new JSONObject();
		positionJson.put(JSON_PROP_LONGITUDE, XMLUtils.getValueAtXPath(locationElem, "./ns:Position/@Latitude"));
		positionJson.put(JSON_PROP_LATITUDE, XMLUtils.getValueAtXPath(locationElem, "./ns:Position/@Longitude"));

		addressJson.put(JSON_PROP_POSITION, positionJson);
		return addressJson;
	
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

	private static JSONObject getDescriptionJson(Element descElem) {
		JSONObject descJson = new JSONObject();
		descJson.put(JSON_PROP_SHORTDESCRIPTION, XMLUtils.getValueAtXPath(descElem, "./ns:ShortDescription"));
		descJson.put(JSON_PROP_LONGDESCRIPTION, XMLUtils.getValueAtXPath(descElem, "./ns:LongDescription"));
		return descJson;
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

	private static JSONObject getCommisionInfoJson(Element commisionInfoElem) {
		JSONObject commisionInfo = new JSONObject();

		commisionInfo.put(JSON_PROP_CCYCODE,
				XMLUtils.getValueAtXPath(commisionInfoElem, "./ns:CommissionPayableAmount/@CurrencyCode"));
		commisionInfo.put(JSON_PROP_AMOUNT,
				XMLUtils.getValueAtXPath(commisionInfoElem, "./ns:CommissionPayableAmount/@Amount"));

		return commisionInfo;
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

		basicInfoJson.put(JSON_PROP_SUPPLIERPRODUCTCODE, XMLUtils.getValueAtXPath(basicInfoElem, "./@SupplierProductCode"));
		basicInfoJson.put("supplierBrandCode", XMLUtils.getValueAtXPath(basicInfoElem, "./@SupplierBrandCode"));
		basicInfoJson.put(JSON_PROP_NAME, XMLUtils.getValueAtXPath(basicInfoElem, "./@Name"));

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
		basicInfoJson.put(JSON_PROP_SUPPLIER_DETAILS, supplierDetailsJson);
		
	}

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

		//TO-DO if tourLanguages not coming in reprice response map it using request data
		basicInfoJson.put(JSON_PROP_TOURLANGUAGE, tourlanguages);
		
	}

	private static Element getUniqueElement(Document ownerDoc, String str1) {
		Element uniqueTag = ownerDoc.createElementNS("http://www.opentravel.org/OTA/2003/05", "n3:UniqueID");
		String subcon[] = str1.split(",");
		if(subcon.length >=1 && null != subcon[0] && !subcon[0].isEmpty())
		uniqueTag.setAttribute(JSON_PROP_ID, subcon[0]);
		if(subcon.length >=2 && null != subcon[1] && !subcon[1].isEmpty())
		uniqueTag.setAttribute("Type", subcon[1]);
		if(subcon.length >=3 && null != subcon[2] && !subcon[2].isEmpty())
		uniqueTag.setAttribute("Instance", subcon[2]);
		return uniqueTag;
	}

}
