package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;

public class SupplierCommercials implements ActivityConstants {

	private static final Logger logger = LogManager.getLogger(ActivityPriceProcessor.class);

//	public static JSONObject getSupplierCommercials(JSONObject req, Element reqElem, JSONObject res,
//			Map<String, JSONObject> briActTourActMap, UserContext usrCtx) throws Exception {
//		CommercialsConfig commConfig = ActivitiesConfig.getCommercialsConfig();
//		CommercialTypeConfig commTypeConfig = commConfig
//				.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
//		JSONObject breSuppReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
//
//		JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
//		JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);
//		JSONArray reqActivityInfoJsonArr = reqBody.getJSONArray(JSON_PROP_ACTIVITYINFO);
//		JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
//		JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);
//		JSONObject breHdrJson = new JSONObject();
//		breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
//		breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
//		breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
//		// TODO take operation name as input
//		breHdrJson.put(JSON_PROP_OPERATION_NAME, "Search");
//
//		JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert")
//				.getJSONObject("object")
//				.getJSONObject("cnk.activities_commercialscalculationengine.suppliertransactionalrules.Root");
//		rootJson.put("header", breHdrJson);
//
//		JSONArray briJsonArr = new JSONArray();
//		Map<String, Integer> suppBRIIndexMap = new HashMap<String, Integer>();
//		JSONArray activityInfoJsonArr = resBody.getJSONArray(JSON_PROP_ACTIVITYINFO);
//		for (int i = 0; i < activityInfoJsonArr.length(); i++) {
//			Map<String, JSONObject> suppBRIMap = new LinkedHashMap<String, JSONObject>();
//			JSONObject activityInfoJson = activityInfoJsonArr.getJSONObject(i);
//			JSONObject reqActivityInfoJson = reqActivityInfoJsonArr.getJSONObject(i);
//			JSONArray tourActivityJsonArr = activityInfoJson.getJSONArray(JSON_PROP_TOURACTIVITYINFO);
//			int actIndex = 0;
//			for (int j = 0; j < tourActivityJsonArr.length(); j++) {
//				JSONObject tourActivityJson = tourActivityJsonArr.getJSONObject(j);
//				String suppID = tourActivityJson.getString(SUPPLIER_ID);
//				JSONObject briJson = null;
//				if (suppBRIMap.containsKey(suppID)) {
//					briJson = suppBRIMap.get(suppID);
//				}
//
//				else {
//					briJson = new JSONObject();
//					suppBRIMap.put(suppID, briJson);
//					suppBRIIndexMap.put(i + "_" + suppID, briJsonArr.length());
//					JSONObject clientCtx = reqHeader.getJSONObject("clientContext");
//					JSONObject commonElemsJson = new JSONObject();
//					commonElemsJson.put(JSON_PROP_SUPPLIER_NAME, suppID);
//					// TODO: Supplier market is hard-coded below. Where will this come from? This
//					// should be ideally come from supplier credentials.
//					// TODO : CONFIRM THIS CHANGE
//					commonElemsJson.put(JSON_PROP_SUPPLIER_MARKET, clientCtx.getString("clientMarket"));
//					commonElemsJson.put(JSON_PROP_CONTRACT_VALIDITY,
//							LocalDateTime.now().format(ActivityService.mDateTimeFormat));
//					// TODO: Check how the value for segment should be set?
//					commonElemsJson.put("segment", "Active");
//					commonElemsJson.put("clientType", clientCtx.getString("clientType"));
//					// TODO: Properties for clientGroup, clientName,
//					// iatanumber,supplierRateType,supplierRateCode are not yet set. Are these
//					// required for B2C? What will be BRMS behavior if these properties are not
//					// sent.
//					commonElemsJson.put("iataNumber", usrCtx.getClientIATANUmber());
//
//					// TODO : As per discussion only the last element needs to be checked,
//					// if it is ClientGroup get the CommercialEntityID. Confirm This Later
//					// Once More
//					if (usrCtx.getClientHierarchy() != null && !usrCtx.getClientHierarchy().isEmpty()
//							&& usrCtx.getClientHierarchy().get(usrCtx.getClientHierarchy().size() - 1)
//									.getCommercialsEntityType().equals(ClientInfo.CommercialsEntityType.ClientGroup))
//						commonElemsJson.put("clientGroup", usrCtx.getClientHierarchy()
//								.get(usrCtx.getClientHierarchy().size() - 1).getCommercialsEntityId());
//
//					// TODO : Now UserContext is containing clientName
//					commonElemsJson.put("clientName", usrCtx.getClientName());
//
//					commonElemsJson.put("supplierRateType", "Contracted");
//					commonElemsJson.put("supplierRateCode", "CNKGTA0001");
//					briJson.put("commonElements", commonElemsJson);
//					JSONObject advDefJson = new JSONObject();
//
//					// TODO : As per discussion, Sales Date is modified to Current Date.
//					advDefJson.put("salesDate", Instant.now().toString());
//
//					// TODO : As per discussion , Date of Activity/ Start Date
//					advDefJson.put("travelDate", "2017-04-10T00:00:00");
//					advDefJson.put("bookingType", "Online");
//					advDefJson.put("connectivitySupplierType", "Direct Connection");
//					advDefJson.put("connectivitySupplierName", "GTA");
//
//					// TODO : Credentials name now present in formed request Xml
//					advDefJson.put("credentialsName",
//							XMLUtils.getValueAtXPath(reqElem, XPATH_SUPPLIERCREDENTIALS_CREDENTIALSNAME));
//
//					// TODO : Clarification needed about from where it will come. Nationality
//					// is about lead passenger's nationality. No such field in UI.
//					advDefJson.put("nationality", clientCtx.getString(JSON_PROP_CLIENTMARKET));
//
//					briJson.put("advancedDefinition", advDefJson);
//					briJsonArr.put(briJson);
//				}
//
//				briJson.append(JSON_PROP_CCE_ACTIVITY_DETAILS,
//						getActivityDetailsJson(tourActivityJson, reqActivityInfoJson));
//				actIndex = briJson.getJSONArray(JSON_PROP_CCE_ACTIVITY_DETAILS).length();
//				briActTourActMap.put(suppBRIIndexMap.get(i + "_" + suppID) + "_" + (actIndex - 1), tourActivityJson);
//
//			}
//		}
//		rootJson.put(JSON_PROP_BUSINESS_RULE_INTAKE, briJsonArr);
//		JSONObject breSuppResJson = null;
//		try {
//
//			System.out.println("Supplier Commercial Req: " + breSuppReqJson);
//
//			// logger.trace(String.format("BRMS Supplier Commercials Request = %s",
//			// breSuppReqJson.toString()));
//			breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS Supplier Commercials",
//					commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breSuppReqJson);
//
//			System.out.println("Supplier Commercial Res: " + breSuppResJson);
//
//			// logger.trace(String.format("BRMS Supplier Commercials Response = %s",
//			// breSuppResJson.toString()));
//		} catch (Exception x) {
//			logger.warn("An exception occurred when calling supplier commercials", x);
//		}
//
//		return breSuppResJson;
//	}

	// ---------Changes by Mohit 25-05-18

	public static JSONObject getSupplierCommercialsV2(CommercialsOperation op, JSONObject req, JSONObject res)
			throws Exception {

		Map<String, JSONObject> bussRuleIntakeBySupp = new HashMap<String, JSONObject>();

		//CommercialsConfig commConfig = ActivitiesConfig.getCommercialsConfig();
		//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		ServiceConfig commTypeConfig = ActivitiesConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		JSONObject breSuppReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));

		JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);

		JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);

		JSONObject breHdrJson = new JSONObject();
		breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
		breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
		breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
		breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());

		JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert")
				.getJSONObject("object")
				.getJSONObject("cnk.activities_commercialscalculationengine.suppliertransactionalrules.Root");
		rootJson.put(JSON_PROP_HEADER, breHdrJson);

		JSONArray briJsonArr = new JSONArray();
		JSONArray activityInfoArr = resBody.getJSONArray("activityInfo");
		JSONArray activityDetailsArr = null;

		for (int i = 0; i < activityInfoArr.length(); i++) {

			JSONObject activityInfoJson = activityInfoArr.getJSONObject(i);
			JSONArray tourActivityInfoArr = activityInfoJson.getJSONArray("tourActivityInfo");

			for (int j = 0; j < tourActivityInfoArr.length(); j++) {

				JSONObject tourActivityInfoJson = tourActivityInfoArr.getJSONObject(j);

				String supplierID = tourActivityInfoJson.getString("supplierID");
				JSONObject briJson = null;

				if (bussRuleIntakeBySupp.containsKey(supplierID)) {
					briJson = bussRuleIntakeBySupp.get(supplierID);
				} else {
					briJson = createBusinessRuleIntakeForSupplier(reqHeader, reqBody, resHeader, resBody,
							tourActivityInfoJson);
					bussRuleIntakeBySupp.put(supplierID, briJson);
				}

				activityDetailsArr = briJson.getJSONArray("activityDetails");
				activityDetailsArr.put(getActivityDetailsJson(tourActivityInfoJson));

			}

		}
		Iterator<Map.Entry<String, JSONObject>> briEntryIter = bussRuleIntakeBySupp.entrySet().iterator();
		while (briEntryIter.hasNext()) {
			Map.Entry<String, JSONObject> briEntry = briEntryIter.next();
			briJsonArr.put(briEntry.getValue());
		}
		rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);

		JSONObject breSuppResJson = null;


		try {
			//long start = System.currentTimeMillis();
			//breSuppResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(),breSuppReqJson);
			breSuppResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(),breSuppReqJson);
			//logger.info(String.format("Time taken to get supplier commercials response : %s ms", System.currentTimeMillis()-start));
		} catch (Exception x) {
			logger.warn("An exception occurred when calling supplier commercials", x);
		}


		return breSuppResJson;
	}

	private static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqBody,
			JSONObject resHeader, JSONObject resBody, JSONObject tourActivityInfoJson) {

		JSONObject briJson = new JSONObject();
		JSONObject commonElemsJson = new JSONObject();
		String supplierID = tourActivityInfoJson.getString("supplierID");

		// TODO: hard coding values so that we get the commercial applied
		commonElemsJson.put("supplierName", supplierID);
		// commonElemsJson.put("supplierRateCode", "CNKGTA0001");
		// commonElemsJson.put("supplierRateType", "Contracted");

		JSONObject clientCtxJson = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);

		// TODO: Supplier market is hard-coded below. Where will this come from? This
		// should be ideally come from supplier credentials.
		// commonElemsJson.put(JSON_PROP_SUPPMARKET, "India");
		commonElemsJson.put(JSON_PROP_SUPPMARKET, clientCtxJson.getString(JSON_PROP_CLIENTMARKET));
		commonElemsJson.put("contractValidity", DATE_FORMAT.format(new Date()));

		// TODO: not there in previous SupCom req
		commonElemsJson.put(JSON_PROP_PRODNAME, "Excursion");

		// TODO: Check how the value for segment should be set?
		commonElemsJson.put(JSON_PROP_SEGMENT, "Active");
		JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		commonElemsJson.put(JSON_PROP_CLIENTTYPE, clientCtx.getString(JSON_PROP_CLIENTTYPE));
		if (usrCtx != null) {
			commonElemsJson.put(JSON_PROP_CLIENTNAME, (usrCtx != null) ? usrCtx.getClientName() : "");
			commonElemsJson.put(JSON_PROP_IATANBR, (usrCtx != null) ? usrCtx.getClientIATANUmber() : "");
			List<ClientInfo> clientHierarchyList = usrCtx.getClientHierarchy();
			if (clientHierarchyList != null && clientHierarchyList.size() > 0) {
				ClientInfo clInfo = clientHierarchyList.get(clientHierarchyList.size() - 1);
				if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
					commonElemsJson.put(JSON_PROP_CLIENTGROUP, clInfo.getCommercialsEntityId());
				}
			}
		}
		briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);

		JSONObject advDefJson = new JSONObject();

		// TODO : As per discussion, Sales Date is modified to Current Date.
		advDefJson.put("salesDate", Instant.now().toString());

		// TODO : As per discussion , Date of Activity/ Start Date
		// advDefJson.put("travelDate", "2017-04-10T00:00:00");

		// TODO: connectivitySupplierType hard-coded to 'LCC'. How should this value be
		// assigned?
		// Retrieve this from productAir mongo collection by matching product name/id
		// from clientConfigEnableDisableSupplier
		ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PRODUCT_CATEGORY, PRODUCT_SUBCATEGORY, supplierID);
		advDefJson.put("connectivitySupplierType", "LCC");
		/*
		 * if(prodSupplier.isGDS()) { advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "GDS");
		 * } else { advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "LCC"); }
		 */

		// TODO: What value to set for connectivitySupplier? For now, it is set to the
		// same value as supplierID.
		advDefJson.put("connectivitySupplierName", supplierID);

		// TODO : Hard coded for now. This should come from product suppliers list in
		// user context.
		advDefJson.put("credentialsName", prodSupplier.getCredentialsName());

		// TODO: bookingType hard-coded to 'Online'. How should this value be assigned?
		advDefJson.put("bookingType", "Online");

		// TODO : Clarification needed about from where it will come. Nationality
		// is about lead passenger's nationality. No such field in UI.
		// advDefJson.put("nationality", clientCtx.getString(JSON_PROP_CLIENTMARKET));

		briJson.put(JSON_PROP_ADVANCEDDEF, advDefJson);

		JSONArray actDtlsJsonArr = new JSONArray();
		briJson.put("activityDetails", actDtlsJsonArr);

		return briJson;

	}

	// ---------Changes by Mohit 25-05-18

	private static JSONObject getActivityDetailsJson(JSONObject tourActivityJson) {
		JSONObject activityDetJson = new JSONObject();
		activityDetJson.put("supplierProductCode",
				tourActivityJson.getJSONObject("basicInfo").getString("supplierProductCode"));
		activityDetJson.put("productCategorySubType", "Excursion");
		activityDetJson.put("productType", "");
		// activityDetJson.put("productName", "Cycling");
		activityDetJson.put("productName", tourActivityJson.getJSONObject("basicInfo").getString("name"));
		activityDetJson.put("productNameSubType", "");

		// Since we have removed reqJson as input from this function, from where will we
		// get cityCode
		Map<String, Object> cityInfo = RedisCityData.getCityCodeInfo("");

		activityDetJson.put("continent", cityInfo.getOrDefault("continent", ""));
		activityDetJson.put("country", cityInfo.getOrDefault("country", ""));
		activityDetJson.put("state", cityInfo.getOrDefault("state", ""));
		activityDetJson.put("city", cityInfo.getOrDefault("value", ""));
		activityDetJson.put(JSON_PROP_CCE_PRICING, getPricingDetailsJson(tourActivityJson.getJSONObject("activityTotalPricingInfo")));
		return activityDetJson;
	}

	private static JSONArray getPricingDetailsJson(JSONObject activityTotalPricingInfo) {

		JSONArray paxPriceInfoArr = activityTotalPricingInfo.getJSONArray("paxPriceInfo");
		JSONArray pricingDetailsJsonArr = new JSONArray();

		for (int i = 0; i < paxPriceInfoArr.length(); i++) {

			JSONObject paxPriceInfoJson = paxPriceInfoArr.getJSONObject(i);
			JSONObject pricingDetailsJson = new JSONObject();

			pricingDetailsJson.put(JSON_PROP_PARTICIPANTCATEGORY, ActivityPassengerType.valueOf(paxPriceInfoJson.getString(JSON_PROP_PARTICIPANTCATEGORY)).toString());

			pricingDetailsJson.put(JSON_PROP_TOTAL_FARE, paxPriceInfoJson.get(JSON_PROP_TOTALPRICE));
			if (paxPriceInfoJson.has(JSON_PROP_PRICE_BREAKUP))
				pricingDetailsJson.put(JSON_PROP_CCE_FAREBREAKUP, paxPriceInfoJson.get(JSON_PROP_PRICE_BREAKUP));

			pricingDetailsJsonArr.put(pricingDetailsJson);
		}
		return pricingDetailsJsonArr;
	}

}
