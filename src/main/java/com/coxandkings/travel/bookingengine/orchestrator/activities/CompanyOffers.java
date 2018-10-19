package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.enums.PassengerType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class CompanyOffers implements ActivityConstants {

	private static final Logger logger = LogManager.getLogger(CompanyOffers.class);

	public static void getCompanyOffers(JSONObject req, JSONObject res, OffersType invocationType) {
		JSONObject reqHdrJson = req.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBodyJson = req.getJSONObject(JSON_PROP_REQBODY);
		JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

		JSONObject resBodyJson = res.getJSONObject(JSON_PROP_RESBODY);
		JSONArray resActivityInfoArr = resBodyJson.getJSONArray("activityInfo");
		JSONArray resTourActivityInfoArr = resActivityInfoArr.getJSONObject(0).getJSONArray("tourActivityInfo");
		JSONObject reqActivityInfoObj = reqBodyJson.getJSONArray("activityInfo").getJSONObject(0);

		//OffersConfig offConfig = ActivitiesConfig.getOffersConfig();
		//CommercialTypeConfig offTypeConfig = offConfig.getOfferTypeConfig(invocationType);
		ServiceConfig offTypeConfig = ActivitiesConfig.getOffersTypeConfig(invocationType);

		JSONObject breCpnyOffReqJson = new JSONObject(new JSONTokener(offTypeConfig.getRequestJSONShell()));
		// TODO: check the name of offers for activities
		JSONArray briJsonArr = breCpnyOffReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.activities_companyoffers.withoutredemption.Root").getJSONArray("businessRuleIntake");
		JSONObject briJson = new JSONObject();

		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		List<ClientInfo> clientCommHierarchyList = usrCtx.getClientHierarchy();
		String clientGroup = "";
		if (clientCommHierarchyList != null && clientCommHierarchyList.size() > 0) {
			ClientInfo clInfo = clientCommHierarchyList.get(clientCommHierarchyList.size() - 1);
			if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
				clientGroup = clInfo.getCommercialsEntityId();
			}
		}

		// Getting companyDetails
		OrgHierarchy orgHier = usrCtx.getOrganizationHierarchy();
		JSONObject cpnyDtlsJson = new JSONObject();

		// cpnyDtlsJson.put(JSON_PROP_SBU, "abc");
		// cpnyDtlsJson.put(JSON_PROP_BU, "Marketing");
		// cpnyDtlsJson.put(JSON_PROP_DIVISION, "A");
		// cpnyDtlsJson.put("salesOfficeLocation", "Mumbai");
		// cpnyDtlsJson.put(JSON_PROP_SALESOFFICE, "Ezeego");
		// cpnyDtlsJson.put(JSON_PROP_COMPANYMKT, "India");

		cpnyDtlsJson.put(JSON_PROP_SBU, orgHier.getSBU());
		cpnyDtlsJson.put(JSON_PROP_BU, orgHier.getBU());
		cpnyDtlsJson.put(JSON_PROP_DIVISION, orgHier.getDivision());
		cpnyDtlsJson.put("salesOfficeLocation", orgHier.getSalesOfficeLoc());
		cpnyDtlsJson.put(JSON_PROP_SALESOFFICE, orgHier.getSalesOfficeName());
		cpnyDtlsJson.put(JSON_PROP_COMPANYMKT, clientCtxJson.getString(JSON_PROP_CLIENTMARKET));
		briJson.put(JSON_PROP_COMPANYDETAILS, cpnyDtlsJson);

		//
		JSONObject clientDtlsJson = new JSONObject();
		// Hardcoded as the rules arent configured other than these ClientDetails
		// clientDtlsJson.put(JSON_PROP_CLIENTCAT, "abc");
		// clientDtlsJson.put(JSON_PROP_CLIENTSUBCAT, "Marketing");
		// clientDtlsJson.put(JSON_PROP_CLIENTGROUP, "Pune");
		// clientDtlsJson.put(JSON_PROP_CLIENTNAME, "Akbar Tavels");
		// clientDtlsJson.put(JSON_PROP_CLIENTTYPE, "B2B");

		// TODO: Check if this is correct
		// clientDtlsJson.put(JSON_PROP_NATIONALITY, "India");

		// clientDtlsJson.put("pointOfSale", clientCtxJson.optString(JSON_PROP_POS,
		// "Kolkata"));

		clientDtlsJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
		clientDtlsJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
		clientDtlsJson.put(JSON_PROP_CLIENTGROUP, clientGroup);
		clientDtlsJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
		clientDtlsJson.put(JSON_PROP_CLIENTTYPE, clientCtxJson.getString(JSON_PROP_CLIENTTYPE));

		// TODO: Check if this is correct
		clientDtlsJson.put(JSON_PROP_NATIONALITY, clientCtxJson.getString(JSON_PROP_CLIENTMARKET));

		clientDtlsJson.put("pointOfSale", clientCtxJson.optString(JSON_PROP_POS, ""));

		briJson.put(JSON_PROP_CLIENTDETAILS, clientDtlsJson);

		getCommonElements(reqActivityInfoObj, briJson);

		getPaymentDetails(resTourActivityInfoArr, reqActivityInfoObj, briJson);

		getProductDetails(resTourActivityInfoArr, reqActivityInfoObj, briJson);

		briJsonArr.put(briJson);


		JSONObject breOffResJson = null;
		try {
			//breOffResJson = HTTPServiceConsumer.consumeJSONService("BRMS/ComOffers", offTypeConfig.getServiceURL(), offConfig.getHttpHeaders(), breCpnyOffReqJson);
			breOffResJson = HTTPServiceConsumer.consumeJSONService("BRMS/ComOffers", offTypeConfig.getServiceURL(), offTypeConfig.getHttpHeaders(), breCpnyOffReqJson);

		}

		catch (Exception x) {
			logger.warn("An exception occurred when calling company offers", x);
		}

		if (BRMS_STATUS_TYPE_FAILURE.equals(breOffResJson.getString(JSON_PROP_TYPE))) {
			logger.warn(String.format("A failure response was received from Company Offers calculation engine: %s",
					breOffResJson.toString()));
			return;
		}

		// Check offers invocation type
		if (OffersType.COMPANY_SEARCH_TIME == invocationType) {
			appendOffersToResults(resBodyJson, breOffResJson);
		}
	}

	private static void appendOffersToResults(JSONObject resBodyJson, JSONObject offResJson) {
		// Search operation
		JSONArray activityInfoJsonArray = resBodyJson.getJSONArray("activityInfo").getJSONObject(0)
				.getJSONArray("tourActivityInfo");

		JSONArray prodDtlsJsonArr = null;
		try {
			prodDtlsJsonArr = offResJson.getJSONObject("result").getJSONObject("execution-results")
					.getJSONArray("results").getJSONObject(0).getJSONArray("value").getJSONObject(0)
					.getJSONObject("cnk.activities_companyoffers.withoutredemption.Root")
					.getJSONArray("businessRuleIntake").getJSONObject(0).getJSONArray("productDetails").getJSONObject(0)
					.getJSONArray("activityDetails");
		} catch (Exception x) {
			logger.warn("Unable to retrieve first businessRuleIntake item. Aborting Company Offers response processing",
					x);
			return;
		}

		if (activityInfoJsonArray.length() != prodDtlsJsonArr.length()) {
			logger.warn(
					"Number of elements in BE result and number of productDetails elements in company offers result is different. Aborting Company Offers response processing.");
			return;
		}

		for (int i = 0; i < activityInfoJsonArray.length(); i++) {
			JSONObject activityInfoJson = activityInfoJsonArray.getJSONObject(i);
			JSONObject activityOffers = prodDtlsJsonArr.getJSONObject(i);

			JSONArray offersJsonArr = activityOffers.optJSONArray(JSON_PROP_OFFERDETAILSSET);
			if (offersJsonArr != null) {
				activityInfoJson.put(JSON_PROP_OFFERS, offersJsonArr);
			}
			Map<PassengerType, Map<String, JSONObject>> psgrOffers = new LinkedHashMap<PassengerType, Map<String, JSONObject>>();
			JSONArray psgrDtlsJsonArr = activityOffers.getJSONArray(JSON_PROP_PSGRDETAILS);

			for (int j = 0; j < psgrDtlsJsonArr.length(); j++) {
				JSONObject psgrDtlsJson = psgrDtlsJsonArr.getJSONObject(j);

				PassengerType psgrType = PassengerType.forString(psgrDtlsJson.getString(JSON_PROP_PSGRTYPE));
				if (psgrType == null) {
					continue;
				}

				Map<String, JSONObject> psgrTypeOffers = (psgrOffers.containsKey(psgrType)) ? psgrOffers.get(psgrType)
						: new LinkedHashMap<String, JSONObject>();
				JSONArray psgrOffersJsonArr = psgrDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
				if (psgrOffersJsonArr == null) {
					continue;
				}
				for (int k = 0; k < psgrOffersJsonArr.length(); k++) {
					JSONObject psgrOfferJson = psgrOffersJsonArr.getJSONObject(k);
					String offerId = psgrOfferJson.getString(JSON_PROP_OFFERID);
					psgrTypeOffers.put(offerId, psgrOfferJson);
				}
				psgrOffers.put(psgrType, psgrTypeOffers);
			}

			JSONArray paxTypeFaresJsonArr = activityInfoJson.getJSONArray(JSON_PROP_PRICING);
			for (int j = 0; j < paxTypeFaresJsonArr.length(); j++) {
				JSONObject paxTypeFareJson = paxTypeFaresJsonArr.getJSONObject(j);

				/** Supplier output : Adult/Child addressed here */
				String participantCategory = null;
				participantCategory = getParticipantCategory(paxTypeFareJson, participantCategory);
				PassengerType psgrType = PassengerType.forString(participantCategory);

				// PassengerType psgrType =
				// PassengerType.forString(paxTypeFareJson.getString("participantCategory"));
				if (psgrType == null) {
					continue;
				}

				JSONArray psgrTypeOffsJsonArr = new JSONArray();
				Map<String, JSONObject> psgrTypeOffers = psgrOffers.get(psgrType);
				if (psgrTypeOffers == null) {
					continue;
				}

				Collection<JSONObject> psgrTypeOffersColl = psgrTypeOffers.values();
				for (JSONObject psgrTypeOffer : psgrTypeOffersColl) {
					psgrTypeOffsJsonArr.put(psgrTypeOffer);
				}
				paxTypeFareJson.put(JSON_PROP_OFFERS, psgrTypeOffsJsonArr);

			}

		}

	}

	/**
	 * @param paxTypeFareJson
	 * @param participantCategory
	 * @return
	 */
	private static String getParticipantCategory(JSONObject paxTypeFareJson, String participantCategory) {
		if ("ADT".equals(paxTypeFareJson.getString("participantCategory"))) {
			participantCategory = PassengerType.ADULT.toString();
		} else if ("CHD".equals(paxTypeFareJson.getString("participantCategory"))) {
			participantCategory = PassengerType.CHILD.toString();
		}
		return participantCategory;
	}

	private static void getPaymentDetails(JSONArray resTourActivityInfoArr, JSONObject reqActivityInfoObj,
			JSONObject briJson) {

		// TODO: how will we get payment details
		JSONObject paymentDetails = new JSONObject();

		paymentDetails.put("modeOfPayment", "");
		paymentDetails.put("paymentType", "");
		paymentDetails.put("bankName", "");

		JSONObject cardDetails = new JSONObject();
		cardDetails.put("cardType", "");
		cardDetails.put("cardNo", "");
		cardDetails.put("nthBooking", 0);

		paymentDetails.put("cardDetails", cardDetails);
	}

	private static void getProductDetails(JSONArray resTourActivityInfoArr, JSONObject reqActivityInfoObj,
			JSONObject briJson) {
		JSONArray productDetailsArr = new JSONArray();
		JSONObject productDetailsObj = new JSONObject();

		productDetailsObj.put("productCategory", PRODUCT_CATEGORY);
		// TODO: Subtype in ActivityService is PRODUCT_SUBCATEGORY = "Tours & Sightseeing" ('&' or 'and')
		productDetailsObj.put(JSON_PROP_PRODCATEGSUBTYPE, "Tours and SightSeeing");

		// TODO: Hardcoded for now; as Offers doesnt have any rules configured to
		// support Continent Country City
		productDetailsObj.put("destination", "Asia");
		productDetailsObj.put(JSON_PROP_COUNTRY, "Dubai");
		productDetailsObj.put(JSON_PROP_CITY, "");
		productDetailsObj.put("interest", JSONObject.NULL);

		productDetailsObj.put("activityDetails", getActivityDetailsArr(reqActivityInfoObj, resTourActivityInfoArr));

		productDetailsArr.put(productDetailsObj);
		briJson.put("productDetails", productDetailsArr);
	}

	private static void getCommonElements(JSONObject reqActivityInfoObj, JSONObject briJson) {
		JSONObject commonElementsJson = new JSONObject();
		if (reqActivityInfoObj.has(JSON_PROP_STARTDATE))
			commonElementsJson.put("travelDate", reqActivityInfoObj.getString(JSON_PROP_STARTDATE).concat("T00:00:00"));
		else
			commonElementsJson.put("travelDate",
					LocalDateTime.now().plusDays(1).format(ActivityConstants.mDateTimeFormat));

		commonElementsJson.put("bookingDate", DATE_FORMAT.format(new Date()));
		// TODO: targetSet to be inserted

		briJson.put("commonElements", commonElementsJson);
	}

	private static JSONArray getActivityDetailsArr(JSONObject reqActivityInfoObj, JSONArray resTourActivityInfoArr) {
		JSONArray activityDetailsArr = new JSONArray();
		for (int tourActivityInfoCount = 0; tourActivityInfoCount < resTourActivityInfoArr
				.length(); tourActivityInfoCount++) {
			JSONObject activityDetailsObj = new JSONObject();
			JSONObject resTourActivityInfoObj = resTourActivityInfoArr.getJSONObject(tourActivityInfoCount);
			activityDetailsObj.put(JSON_PROP_PRODNAME, "Safari");
			activityDetailsObj.put("productNameSubType",
					"Half-day Evening Desert Safari in Abu Dhabi via Land Cruiser with Dinner");
			activityDetailsObj.put("passengerDetails", getPassengerDetails(reqActivityInfoObj, resTourActivityInfoObj));
			activityDetailsArr.put(activityDetailsObj);

		}

		return activityDetailsArr;
	}

	private static JSONArray getPassengerDetails(JSONObject reqActivityInfoObj, JSONObject resTourActivityInfoObj) {
		JSONArray passengerDetailsArr = new JSONArray();

		JSONObject participantInfoObj = new JSONObject();
		int adultCount;

		JSONArray childAgesArr = new JSONArray();
		int childCount = 0;

		JSONObject pricingObjAdult = new JSONObject();
		JSONObject pricingObjChild = new JSONObject();
		JSONObject pricingObjSummary = new JSONObject();
		JSONArray pricingArr = resTourActivityInfoObj.getJSONArray("pricing");

		for (int i = 0; i < pricingArr.length(); i++) {
			JSONObject pricingObj = pricingArr.getJSONObject(i);

			if (pricingObj.getString("participantCategory").equalsIgnoreCase("ADT")) {
				pricingObjAdult = pricingObj;
			} else {
				if (pricingObj.getString("participantCategory").equalsIgnoreCase("CHD")) {
					pricingObjChild = pricingObj;
				}
			}
			if (pricingObj.getString("participantCategory").equalsIgnoreCase("SUMMARY")) {
				pricingObjSummary = pricingObj;
			}
		}

		if (reqActivityInfoObj.has("participantInfo")) {
			participantInfoObj = reqActivityInfoObj.getJSONObject("participantInfo");
			if (participantInfoObj.has("adultCount")) {
				adultCount = participantInfoObj.getInt("adultCount");
			} else {
				adultCount = 1;

			}
			if (participantInfoObj.has("childAges")) {
				childAgesArr = participantInfoObj.getJSONArray("childAges");
				childCount = childAgesArr.length();
			} else {
				childCount = 0;
			}

		} else {
			adultCount = 1;
			// childCount = 0;
		}
		// TODO: assuming gender to be as male

		for (int i = 0; i < adultCount; i++) {

			// For Adult
			JSONObject passengerDetailsAdult = new JSONObject();
			passengerDetailsAdult.put("passengerType", PassengerType.ADULT.toString());
			passengerDetailsAdult.put("age", 0);
			passengerDetailsAdult.put("gender", "Male");
			if (pricingObjAdult != null && pricingObjAdult.has("totalPrice"))
				passengerDetailsAdult.put("totalFare", pricingObjAdult.getBigDecimal("totalPrice"));
			else if (pricingObjSummary != null && pricingObjSummary.has("totalPrice"))
				passengerDetailsAdult.put("totalFare", pricingObjSummary.getBigDecimal("totalPrice"));

			passengerDetailsArr.put(passengerDetailsAdult);
		}

		for (int i = 0; i < childCount; i++) {
			JSONObject passengerDetailsChild = new JSONObject();
			passengerDetailsChild.put("passengerType", PassengerType.CHILD.toString());

			passengerDetailsChild.put("age", childAgesArr.getNumber(i));
			passengerDetailsChild.put("gender", "Male");
			if (pricingObjChild != null && pricingObjChild.has("totalPrice"))
				passengerDetailsChild.put("totalFare", pricingObjChild.getBigDecimal("totalPrice"));
			else if (pricingObjSummary != null && pricingObjSummary.has("totalPrice"))
				passengerDetailsChild.put("totalFare", pricingObjSummary.getBigDecimal("totalPrice"));
			passengerDetailsArr.put(passengerDetailsChild);
		}

		// TODO: fareDetails to be added

		return passengerDetailsArr;
	}
}
