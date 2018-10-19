package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class ActivitySearchProcessor implements ActivityConstants {

	//private static final String JSON_PROP_CACHE_ACTIVITIES = "ACTIVITIES";
	private static final String XPATH_RESPONSEBODY_OTA_TOURACTIVITY = "./sig:ResponseBody/sig1:OTA_TourActivityAvailRSWrapper";
	private static final String DEFAULT_PARTICIPANTCOUNT = "1";
	private static final String XML_ATTR_ENDPERIOD = "EndPeriod";
	private static final String XML_ATTR_STARTPERIOD = "StartPeriod";
	private static final String XPATH_REQUESTBODY_NS_QUALIFIERINFO = "./sig:RequestBody/ns:OTA_TourActivityAvailRQ/ns:TourActivity/ns:ParticipantCount/ns:QualifierInfo";
	private static final String XPATH_REQUESTBODY_NS_PARTICIPANTCOUNT = "./sig:RequestBody/ns:OTA_TourActivityAvailRQ/ns:TourActivity/ns:ParticipantCount/@Quantity";
	private static final String XPATH_REQUESTBODY_NS_SCHEDULE = "./sig:RequestBody/ns:OTA_TourActivityAvailRQ/ns:TourActivity/ns:Schedule";
	private static final String XPATH_REQUESTBODY_NS_REGIONCODE = "./sig:RequestBody/ns:OTA_TourActivityAvailRQ/ns:TourActivity/ns:Location/ns:Region/@RegionCode";
	private static final String XPATH_REQUESTBODY_NS_COUNTRYCODE = "./sig:RequestBody/ns:OTA_TourActivityAvailRQ/ns:TourActivity/ns:Location/ns:Address/ns:CountryName/@Code";

	private static String mIncvPriceCompFormat = JSON_PROP_INCENTIVE.concat(".%s@").concat(JSON_PROP_CODE);
	//private static String mTaxesPriceCompQualifier = JSON_PROP_TAXES.concat(SIGMA).concat(".").concat(JSON_PROP_TAX).concat(".");

	private static final Logger logger = LogManager.getLogger(ActivitySearchProcessor.class);

	public static String process(JSONObject reqJson)
			throws InternalProcessingException, RequestProcessingException, ValidationException {
		try {
			//OperationConfig opConfig = ActivitiesConfig.getOperationConfig("search");
			ServiceConfig opConfig = ActivitiesConfig.getOperationConfig("search");
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();
			TrackingContext.setTrackingContext(reqJson);
			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap = new HashMap<>();

			/** Commercial Cache integration code */
			//			JSONArray suppliers = new JSONArray(
			//					"[{\"supplierID\": \"GTA\"},{\"supplierID\": \"WHL\"},{\"supplierID\": \"VIATOR\"},{\"supplierID\": \"BEMYGUEST\"},{\"supplierID\": \"THETRAVELLER\"},{\"supplierID\": \"TOURICO\"},{\"supplierID\": \"ACAMPORA\"},{\"supplierID\": \"HOTELBEDS\"},{\"supplierID\": \"SPORTSEVENTS365\"}]");
			//
			//			ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
			//			String commCacheRes = CommercialCacheProcessor.getFromCache(JSON_PROP_CACHE_ACTIVITIES,
			//					clInfo[clInfo.length - 1].getCommercialsEntityId(), suppliers, reqJson);
			//			if (commCacheRes != null && !(commCacheRes.equals("error")))
			//				return commCacheRes;

			ActivityRequestValidator.validateSearchRequest(reqHdrJson, reqBodyJson);

			createHeader(reqElem, reqHdrJson);

			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(ActivityConstants.PRODUCT_CATEGORY,
					ActivityConstants.PRODUCT_SUBCATEGORY);

			// for(ProductSupplier tempProdSupplier: prodSuppliers)
			// System.out.println("prodSuppliers: "+tempProdSupplier.toJSON());

			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
					XPATH_REQUESTHEADER_SUPPLIERCREDENTIALSLIST);
			for (ProductSupplier prodSupplier : prodSuppliers) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, prodSuppliers.indexOf(prodSupplier)));
			}

			JSONArray activityInfoArr = reqBodyJson.getJSONArray(JSON_PROP_ACTIVITYINFO);

			for (int i = 0; i<activityInfoArr.length(); i++) {

				populateRequestBody(reqElem, ownerDoc, activityInfoArr.getJSONObject(i));

			}



			logger.info("Before opening HttpURLConnection");

			Element resElem = null;
			logger.trace(String.format("SI XML Request = %s", XMLTransformer.toString(reqElem)));
			//resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), ActivitiesConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception(ERROR_MSG_NULL_RESPONSE_SI);
			}
			logger.trace(String.format("SI XML Response = %s", XMLTransformer.toString(resElem)));


			Element[] tourActivityAvailRSWrapperElems = XMLUtils.getElementsAtXPath(resElem,
					XPATH_RESPONSEBODY_OTA_TOURACTIVITY);
			JSONObject resBodyJson = getSupplierResponseJSON(tourActivityAvailRSWrapperElems, reqBodyJson,
					paxTypeCountMap);
			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));



			JSONObject resSupplierComJson = SupplierCommercials.getSupplierCommercialsV2(CommercialsOperation.Search,
					reqJson, resJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierComJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format(
						"A failure response was received from Supplier Commercials calculation engine: %s",
						resSupplierComJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}

			JSONObject resClientComJson = ClientCommercials.getClientCommercials(reqJson, resJson, resSupplierComJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientComJson.getString(JSON_PROP_TYPE))) {
				logger.error(
						String.format("A failure response was received from Client Commercials calculation engine: %s",
								resClientComJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}

			calculatePricesV2(reqJson, resJson, resSupplierComJson, resClientComJson, false);


			CompanyOffers.getCompanyOffers(reqJson, resJson, OffersType.COMPANY_SEARCH_TIME);

			try {
				ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
				CommercialCacheProcessor.updateInCache(PRODUCT, clInfo[clInfo.length - 1].getCommercialsEntityId(),resJson,reqJson);
			}
			catch (Exception x) {
				logger.info("An exception was received while pushing price results in commercial cache", x);
			}

			return resJson.toString();
		}

		catch (ValidationException valx) {
			throw valx;
		}

		catch (Exception x) {
			x.printStackTrace();
			return STATUS_ERROR;
		}

	}

	protected static void createHeader(Element reqElem, JSONObject reqHdrJson) {

		XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_SESSIONID, reqHdrJson.getString(JSON_PROP_SESSIONID));
		XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_TRANSACTIONID,
				reqHdrJson.getString(JSON_PROP_TRANSACTID));
		XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_USERID, reqHdrJson.getString(JSON_PROP_USERID));
	}

	protected static void populateRequestBody(Element reqElem, Document ownerDoc, JSONObject activityInfoJson) {

		if (activityInfoJson.has(JSON_PROP_COUNTRYCODE)) {
			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTBODY_NS_COUNTRYCODE,
					activityInfoJson.getString(JSON_PROP_COUNTRYCODE));
		}

		XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTBODY_NS_REGIONCODE,
				activityInfoJson.getString(JSON_PROP_CITYCODE));
		Element scheduleElem = XMLUtils.getFirstElementAtXPath(reqElem, XPATH_REQUESTBODY_NS_SCHEDULE);

		if (activityInfoJson.has(JSON_PROP_STARTDATE) && activityInfoJson.has(JSON_PROP_ENDDATE) && !activityInfoJson.optString(JSON_PROP_STARTDATE).isEmpty() && !activityInfoJson.optString(JSON_PROP_ENDDATE).isEmpty()) {

			// Date startDate = new
			// SimpleDateFormat("yyyy-mm-dd").parse(activityInfoJson.optString(JSON_PROP_STARTDATE));
			// Date endDate = new
			// SimpleDateFormat("yyyy-mm-dd").parse(activityInfoJson.optString(JSON_PROP_ENDDATE));

			scheduleElem.setAttribute(XML_ATTR_STARTPERIOD, activityInfoJson.getString(JSON_PROP_STARTDATE));
			scheduleElem.setAttribute(XML_ATTR_ENDPERIOD, activityInfoJson.getString(JSON_PROP_ENDDATE));
		} else {
			scheduleElem.setAttribute(XML_ATTR_STARTPERIOD,
					LocalDate.now().plusDays(1).format(ActivityConstants.mDateFormat));
			scheduleElem.setAttribute(XML_ATTR_ENDPERIOD,
					LocalDate.now().plusMonths(1).format(ActivityConstants.mDateFormat));
		}



		getAdultChildParticipant(reqElem, ownerDoc, activityInfoJson);



		// Changes by Mohit End here

	}

	private static void getAdultChildParticipant(Element reqElem, Document ownerDoc, JSONObject activityInfoJson) {
		int adultCount = 0;
		int childCount = 0;

		if (activityInfoJson.has("participantInfo")) {

			JSONObject participantInfo = activityInfoJson.getJSONObject("participantInfo");

			if (participantInfo.has("adultCount")) {
				adultCount = participantInfo.getInt("adultCount");
			}

			if (participantInfo.optJSONArray("childAges") != null) {
				childCount = participantInfo.getJSONArray("childAges").length();
			}

			if (adultCount == 0 && childCount == 0) {
				XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTBODY_NS_PARTICIPANTCOUNT, DEFAULT_PARTICIPANTCOUNT);
				XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTBODY_NS_QUALIFIERINFO, JSON_PROP_ADULT);
			} else {

				Element tourActivityElem = XMLUtils.getFirstElementAtXPath(reqElem,
						"./sig:RequestBody/ns:OTA_TourActivityAvailRQ/ns:TourActivity");
				Element participantCountElem = XMLUtils.getFirstElementAtXPath(tourActivityElem,
						"./ns:ParticipantCount");
				tourActivityElem.removeChild(participantCountElem);

				for (int i = 0; i < adultCount; i++) {

					Element participantCount = ownerDoc.createElementNS(NS_OTA, "ns:ParticipantCount");
					participantCount.setAttribute("Quantity", DEFAULT_PARTICIPANTCOUNT);
					Element qualifierInfo = ownerDoc.createElementNS(NS_OTA, "ns:QualifierInfo");
					// qualifierInfo.setAttribute("Extension", "1");
					qualifierInfo.setTextContent(JSON_PROP_ADULT);
					participantCount.appendChild(qualifierInfo);
					Element tourActivity = XMLUtils.getFirstElementAtXPath(reqElem,
							"./sig:RequestBody/ns:OTA_TourActivityAvailRQ/ns:TourActivity");
					tourActivity.appendChild(participantCount);

				}

				for (int i = 0; i < childCount; i++) {
					Element participantCount = ownerDoc.createElementNS(NS_OTA, "ns:ParticipantCount");
					participantCount.setAttribute("Quantity", DEFAULT_PARTICIPANTCOUNT);
					if (activityInfoJson.optJSONObject("participantInfo").getJSONArray("childAges").getBigDecimal(i)
							.intValue() <= 18) {
						participantCount.setAttribute("Age", activityInfoJson.optJSONObject("participantInfo")
								.getJSONArray("childAges").get(i).toString());
						Element qualifierInfo = ownerDoc.createElementNS(NS_OTA, "ns:QualifierInfo");
						// qualifierInfo.setAttribute("Extension", "1");
						qualifierInfo.setTextContent(JSON_PROP_CHILD);
						participantCount.appendChild(qualifierInfo);
						Element tourActivity = XMLUtils.getFirstElementAtXPath(reqElem,
								"./sig:RequestBody/ns:OTA_TourActivityAvailRQ/ns:TourActivity");
						tourActivity.appendChild(participantCount);
					}
				}

			}
		} else {

			adultCount = 1;
			childCount = 0;

			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTBODY_NS_PARTICIPANTCOUNT, DEFAULT_PARTICIPANTCOUNT);
			XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTBODY_NS_QUALIFIERINFO, JSON_PROP_ADULT);

		}

		//		HashMap<String, BigDecimal> paxCountMap = new HashMap<>();
		//
		//		// index of paxTypeCountMap is kwpt 0 for now as we are assuming only one
		//		// ActivityInfo
		//		paxCountMap.put("ADT", BigDecimal.valueOf(adultCount));
		//		paxCountMap.put("CHD", BigDecimal.valueOf(childCount));
		//		paxTypeCountMap.put(index, paxCountMap);
	}



	// public static void calculatePrices(JSONObject reqJson, JSONObject resJson,
	// JSONObject suppCommResJson,
	// JSONObject clientCommResJson, Map<String, JSONObject> briActTourActMap,
	// UserContext userContext,
	// boolean retainSuppFares) {
	//
	// JSONArray clientBRIJsonArr =
	// getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
	// JSONArray suppBRIJsonArr =
	// getSupplierCommercialsBusinessRuleIntakeJSONArray(suppCommResJson);
	// String clientMarket =
	// reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT)
	// .getString(JSON_PROP_CLIENTMARKET);
	// String clientCcyCode =
	// reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT)
	// .getString(JSON_PROP_CLIENTCCY);
	// Map<String, BigDecimal> paxInfoMap = null;
	// Map<String, String> suppCommToTypeMap = null;
	// Map<String, HashMap<String, String>> clientEntityToCommToTypeMap = null;
	// JSONObject userCtxJson = userContext.toJSON();
	// JSONArray clientEntityDetailsArr =
	// userCtxJson.getJSONArray(JSON_PROP_CLIENTCOMMENTITYDTLS);
	//
	// for (int i = 0; i < clientBRIJsonArr.length(); i++) {
	// JSONObject clientBRI = clientBRIJsonArr.getJSONObject(i);
	// if (retainSuppFares) {
	// JSONObject tourActivityRequestJson = reqJson.getJSONObject(JSON_PROP_REQBODY)
	// .getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(i);
	// paxInfoMap = getPaxCountsFromRequest(tourActivityRequestJson);
	// suppCommToTypeMap =
	// getSupplierCommercialsAndTheirType(suppBRIJsonArr.getJSONObject(i));
	// clientEntityToCommToTypeMap = getClientCommercialsAndTheirType(clientBRI);
	// } else {
	// paxInfoMap = new LinkedHashMap<String, BigDecimal>();
	// paxInfoMap.put("ADT", new BigDecimal(1));
	// }
	//
	// JSONArray clientActivityDetJsonArr =
	// clientBRI.getJSONArray(JSON_PROP_CCE_ACTIVITY_DETAILS);
	// for (int j = 0; j < clientActivityDetJsonArr.length(); j++) {
	//
	// iterateClientCommercialActivityDetails(briActTourActMap, retainSuppFares,
	// clientMarket, clientCcyCode,
	// paxInfoMap, suppCommToTypeMap, clientEntityToCommToTypeMap,
	// clientEntityDetailsArr, i,
	// clientActivityDetJsonArr, j);
	// }
	//
	// }
	// }

	/**
	 * @param briActTourActMap
	 * @param retainSuppFares
	 * @param clientMarket
	 * @param clientCcyCode
	 * @param paxInfoMap
	 * @param suppCommToTypeMap
	 * @param clientEntityToCommToTypeMap
	 * @param clientEntityDetailsArr
	 * @param i
	 * @param clientActivityDetJsonArr
	 * @param j
	 */
	//	private static void iterateClientCommercialActivityDetails(Map<String, JSONObject> briActTourActMap, boolean retainSuppFares, String clientMarket, String clientCcyCode, Map<String, BigDecimal> paxInfoMap, Map<String, String> suppCommToTypeMap, Map<String, HashMap<String, String>> clientEntityToCommToTypeMap, JSONArray clientEntityDetailsArr, int i, JSONArray clientActivityDetJsonArr, int j) {
	//		String suppCcyCode;
	//		JSONObject clientActivityDetJson = clientActivityDetJsonArr.getJSONObject(j);
	//		JSONObject tourActivityResponseJson = briActTourActMap.get(i + "_" + j);
	//
	//		JSONArray respPricingArr = tourActivityResponseJson.getJSONArray(JSON_PROP_PRICING);
	//		JSONArray clientCommPricingArr = clientActivityDetJson.getJSONArray(JSON_PROP_CCE_PRICING);
	//
	//		if (retainSuppFares) {
	//			JSONObject suppPriceJSON = new JSONObject();
	//			suppPriceJSON.put(JSON_PROP_PRICING, new JSONArray());
	//			tourActivityResponseJson.put(JSON_PROP_SUPPPRICEINFO, suppPriceJSON);
	//		}
	//		for (int k = 0; k < clientCommPricingArr.length(); k++) {
	//			iterateClientCommercialActivityPricingDetails(retainSuppFares, clientMarket, clientCcyCode,
	//					suppCommToTypeMap, clientEntityToCommToTypeMap, clientEntityDetailsArr, tourActivityResponseJson,
	//					respPricingArr, clientCommPricingArr, k);
	//
	//		}
	//
	//		calculateSummary(paxInfoMap, tourActivityResponseJson, retainSuppFares);
	//	}

	/**
	 * @param retainSuppFares
	 * @param clientMarket
	 * @param clientCcyCode
	 * @param suppCommToTypeMap
	 * @param clientEntityToCommToTypeMap
	 * @param clientEntityDetailsArr
	 * @param tourActivityResponseJson
	 * @param respPricingArr
	 * @param clientCommPricingArr
	 * @param k
	 */
	//	private static void iterateClientCommercialActivityPricingDetails(boolean retainSuppFares, String clientMarket, String clientCcyCode, Map<String, String> suppCommToTypeMap, Map<String, HashMap<String, String>> clientEntityToCommToTypeMap, JSONArray clientEntityDetailsArr, JSONObject tourActivityResponseJson, JSONArray respPricingArr, JSONArray clientCommPricingArr, int k) {
	//		String suppCcyCode;
	//		JSONObject clientCommPricing = clientCommPricingArr.getJSONObject(k);
	//		JSONObject respPricing = respPricingArr.getJSONObject(k);
	//		JSONObject suppPricing = null;
	//		String participantCategory = respPricing.getString(JSON_PROP_PARTICIPANTCATEGORY);
	//		suppCcyCode = respPricing.getString(JSON_PROP_CCYCODE);
	//		JSONArray clientEntityCommJsonArr = clientCommPricing.optJSONArray(JSON_PROP_ENTITYCOMMERCIALS);
	//
	//		if (retainSuppFares) {
	//			suppPricing = createSupplierCommercial(suppCommToTypeMap, tourActivityResponseJson, clientCommPricing,
	//					respPricing, participantCategory);
	//		}
	//
	//		if (clientEntityCommJsonArr == null) {
	//			//Refine this warning message. Maybe log some context information also.
	//			logger.warn(String.format("No Client commercials found for supplier %s and participant category %s",
	//					tourActivityResponseJson.getString(SUPPLIER_ID), participantCategory));
	//			return;
	//		}
	//
	//		for (int l = (clientEntityCommJsonArr.length() - 1); l >= 0; l--) {
	//
	//			iterateClientEntityCommercials(retainSuppFares, clientEntityToCommToTypeMap, clientEntityDetailsArr,
	//					respPricing, suppPricing, clientEntityCommJsonArr, l);
	//
	//		}
	//
	//		respPricing.put(JSON_PROP_TOTALPRICE,
	//				respPricing.getBigDecimal(JSON_PROP_TOTALPRICE)
	//				.multiply(com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData
	//						.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
	//	}

	/**
	 * @param retainSuppFares
	 * @param clientEntityToCommToTypeMap
	 * @param clientEntityDetailsArr
	 * @param respPricing
	 * @param suppPricing
	 * @param clientEntityCommJsonArr
	 * @param l
	 */
	//	private static void iterateClientEntityCommercials(boolean retainSuppFares, Map<String, HashMap<String, String>> clientEntityToCommToTypeMap, JSONArray clientEntityDetailsArr, JSONObject respPricing, JSONObject suppPricing, JSONArray clientEntityCommJsonArr, int l) {
	//		JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(l);
	//		String entityName = clientEntityCommJson.getString(JSON_PROP_ENTITYNAME);
	//		JSONObject clientEntityDetailsJson = new JSONObject();
	//		for (int y = 0; y < clientEntityDetailsArr.length(); y++) {
	//
	//			//This Condition will be later uncommented, when we will receive the
	//			// real time data
	//
	//			// if(clientEntityDetailsArr.getJSONObject(y).opt(JSON_PROP_COMMENTITYID)!=null)
	//			// {
	//			// if(clientEntityDetailsArr.getJSONObject(y).opt(JSON_PROP_COMMENTITYID).toString().equalsIgnoreCase(clientEntityCommJson.get(JSON_PROP_ENTITYNAME).toString()))
	//			// {
	//			// clientEntityDetailsJson=clientEntityDetailsArr.getJSONObject(y);
	//			// }
	//			// }
	//
	//			//This line will be removed, When the upper changes will be uncommented
	//			clientEntityDetailsJson = clientEntityDetailsArr.getJSONObject(y);
	//
	//		}
	//
	//
	//		if (retainSuppFares) {
	//			createClientCommercials(clientEntityToCommToTypeMap, suppPricing, clientEntityCommJson, entityName,
	//					clientEntityDetailsJson, respPricing);
	//		}
	//
	//		JSONObject markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMERCIALDETAILS);
	//		if (markupCalcJson == null) {
	//			return;
	//		}
	//
	//		respPricing.put(JSON_PROP_TOTALPRICE, respPricing.getBigDecimal(JSON_PROP_TOTALPRICE)
	//				.add(markupCalcJson.getBigDecimal(JSON_PROP_COMMERCIALAMOUNT)));
	//	}

	/**
	 * @param suppCommToTypeMap
	 * @param tourActivityResponseJson
	 * @param clientCommPricing
	 * @param respPricing
	 * @param participantCategory
	 * @return
	 */
	//	private static JSONObject createSupplierCommercial(Map<String, String> suppCommToTypeMap, JSONObject tourActivityResponseJson, JSONObject clientCommPricing, JSONObject respPricing, String participantCategory) {
	//		JSONObject suppPricing;
	//		suppPricing = new JSONObject();
	//		suppPricing.put(JSON_PROP_TOTALPRICE, respPricing.getBigDecimal(JSON_PROP_TOTALPRICE));
	//		suppPricing.put(JSON_PROP_CCYCODE, respPricing.getString(JSON_PROP_CCYCODE));
	//		suppPricing.put(JSON_PROP_PARTICIPANTCATEGORY, participantCategory);
	//		// Append calculated supplier commercials in pax type fares
	//		JSONArray suppCommJsonArr = new JSONArray();
	//		JSONArray ccommSuppCommJsonArr = clientCommPricing.optJSONArray(JSON_PROP_COMMERCIAL_DETAILS);
	//		// If no supplier commercials have been defined in BRMS, the JSONArray for
	//		// ccommSuppCommJsonArr will be null.
	//		// In this case, log a message and proceed with other calculations.
	//		if (ccommSuppCommJsonArr == null) {
	//			logger.warn(String.format("No supplier commercials found for supplier %s and participant category %s",
	//					tourActivityResponseJson.getString(SUPPLIER_ID), participantCategory));
	//		} else {
	//			//SupplierCommercial Changes here. Added Commmercial currency here
	//			for (int x = 0; x < ccommSuppCommJsonArr.length(); x++) {
	//				JSONObject ccommSuppCommJson = ccommSuppCommJsonArr.getJSONObject(x);
	//				JSONObject suppCommJson = new JSONObject();
	//				suppCommJson.put(JSON_PROP_COMMERCIALNAME, ccommSuppCommJson.getString(JSON_PROP_COMMERCIALNAME));
	//				suppCommJson.put(JSON_PROP_COMMERCIALTYPE,
	//						suppCommToTypeMap.get(ccommSuppCommJson.getString(JSON_PROP_COMMERCIALNAME)));
	//				suppCommJson.put(JSON_PROP_COMMERCIALAMOUNT,
	//						ccommSuppCommJson.getBigDecimal(JSON_PROP_COMMERCIALAMOUNT));
	//				suppCommJson.put(JSON_PROP_COMMERCIALCURRENCY,
	//						ccommSuppCommJson.optString(JSON_PROP_COMMERCIALCURRENCY));
	//				suppCommJsonArr.put(suppCommJson);
	//			}
	//			suppPricing.put(JSON_PROP_SUPPLIERCOMMERCIALS, suppCommJsonArr);
	//		}
	//		tourActivityResponseJson.getJSONObject(JSON_PROP_SUPPPRICEINFO).getJSONArray(JSON_PROP_PRICING)
	//		.put(suppPricing);
	//		return suppPricing;
	//	}

	/**
	 * @param clientEntityToCommToTypeMap
	 * @param suppPricing
	 * @param clientEntityCommJson
	 * @param entityName
	 * @param clientEntityDetailsJson
	 */
	//	private static void createClientCommercials(Map<String, HashMap<String, String>> clientEntityToCommToTypeMap, JSONObject suppPricing, JSONObject clientEntityCommJson, String entityName, JSONObject clientEntityDetailsJson, JSONObject respPricing) {
	//
	//		/** Additional Commercial needs to be added along with Markup in totalPrice */
	//		BigDecimal additionalCommercialPrice = new BigDecimal(0);
	//		JSONObject clientComm = new JSONObject();
	//		JSONArray entityCommJSONArr = new JSONArray();
	//		clientComm.put(JSON_PROP_ENTITYCOMMERCIALS, entityCommJSONArr);
	//		clientComm.put(JSON_PROP_ENTITYNAME, entityName);
	//		HashMap<String, String> clientCommToTypeMap = clientEntityToCommToTypeMap.get(entityName);
	//		JSONArray retentionCommJSONArr = clientEntityCommJson.optJSONArray("retentionCommercialDetails");
	//		JSONArray additionalCommJSONArr = clientEntityCommJson.optJSONArray("additionalCommercialDetails");
	//		JSONArray fixedCommJSONArr = clientEntityCommJson.optJSONArray("fixedCommercialDetails");
	//		JSONObject markupCommJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMERCIALDETAILS);
	//
	//		//Client Commercial Changes
	//		if (retentionCommJSONArr != null) {
	//			for (int commIdx = 0; commIdx < retentionCommJSONArr.length(); commIdx++) {
	//				JSONObject retentionCommJSON = retentionCommJSONArr.getJSONObject(commIdx);
	//				JSONObject commJSON = new JSONObject();
	//				getClientCommecialElements(clientCommToTypeMap, retentionCommJSON, commJSON);
	//				putUserContextDetailsintoClientCommercials(clientEntityDetailsJson, commJSON);
	//
	//				entityCommJSONArr.put(commJSON);
	//			}
	//		}
	//
	//		if (additionalCommJSONArr != null) {
	//			for (int commIdx = 0; commIdx < additionalCommJSONArr.length(); commIdx++) {
	//				JSONObject additionalCommJSON = additionalCommJSONArr.getJSONObject(commIdx);
	//				JSONObject commJSON = new JSONObject();
	//				getClientCommecialElements(clientCommToTypeMap, additionalCommJSON, commJSON);
	//				putUserContextDetailsintoClientCommercials(clientEntityDetailsJson, commJSON);
	//				if (COMM_TYPE_RECEIVABLE.equals(commJSON.get(JSON_PROP_COMMTYPE))) {
	//					additionalCommercialPrice = additionalCommercialPrice
	//							.add(commJSON.getBigDecimal(JSON_PROP_COMMERCIALAMOUNT));
	//				}
	//				entityCommJSONArr.put(commJSON);
	//			}
	//		}
	//
	//		if (fixedCommJSONArr != null) {
	//			for (int commIdx = 0; commIdx < fixedCommJSONArr.length(); commIdx++) {
	//				JSONObject fixedCommJSON = fixedCommJSONArr.getJSONObject(commIdx);
	//				JSONObject commJSON = new JSONObject();
	//				getClientCommecialElements(clientCommToTypeMap, fixedCommJSON, commJSON);
	//				putUserContextDetailsintoClientCommercials(clientEntityDetailsJson, commJSON);
	//
	//				entityCommJSONArr.put(commJSON);
	//			}
	//		}
	//		if (markupCommJson != null) {
	//			JSONObject commJSON = new JSONObject();
	//			getClientCommecialElements(clientCommToTypeMap, markupCommJson, commJSON);
	//			putUserContextDetailsintoClientCommercials(clientEntityDetailsJson, commJSON);
	//
	//			entityCommJSONArr.put(commJSON);
	//		}
	//
	//		suppPricing.append(JSON_PROP_CLIENTCOMMERCIALS, clientComm);
	//		respPricing.put(JSON_PROP_TOTALPRICE,
	//				respPricing.getBigDecimal(JSON_PROP_TOTALPRICE).add(additionalCommercialPrice));
	//	}

	/**
	 * @param clientCommToTypeMap
	 * @param retentionCommJSON
	 * @param commJSON
	 */
	//	private static void getClientCommecialElements(HashMap<String, String> clientCommToTypeMap, JSONObject retentionCommJSON, JSONObject commJSON) {
	//		commJSON.put(JSON_PROP_COMMERCIALNAME, retentionCommJSON.getString(JSON_PROP_COMMERCIALNAME));
	//		commJSON.put(JSON_PROP_COMMERCIALTYPE, clientCommToTypeMap.get(retentionCommJSON.getString(JSON_PROP_COMMERCIALNAME)));
	//		commJSON.put(JSON_PROP_COMMERCIALAMOUNT, retentionCommJSON.getBigDecimal(JSON_PROP_COMMERCIALAMOUNT));
	//		commJSON.put(JSON_PROP_COMMERCIALCURRENCY, retentionCommJSON.optString(JSON_PROP_COMMERCIALCURRENCY));
	//	}

	/**
	 * @param clientEntityDetailsJson
	 * @param commJSON
	 */
	//	private static void putUserContextDetailsintoClientCommercials(JSONObject clientEntityDetailsJson, JSONObject commJSON) {
	//		commJSON.put(JSON_PROP_COMMENTITYTYPE, clientEntityDetailsJson.get(JSON_PROP_COMMENTITYTYPE));
	//		commJSON.put(JSON_PROP_COMMENTITYID, clientEntityDetailsJson.getString(JSON_PROP_COMMENTITYID));
	//		commJSON.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsJson.getString(JSON_PROP_PARENTCLIENTID));
	//		commJSON.put(JSON_PROP_CLIENTID, clientEntityDetailsJson.getString(JSON_PROP_CLIENTID));
	//	}

	//	private static Map<String, HashMap<String, String>> getClientCommercialsAndTheirType(JSONObject clientCommBRIJson) {
	//		JSONArray entityDetailsJsonArr = null;
	//		JSONObject entityDetailsJson = null;
	//		entityDetailsJsonArr = clientCommBRIJson.optJSONArray(JSON_PROP_ENTITY_DETAILS);
	//		if (entityDetailsJsonArr == null) {
	//			logger.warn("No Entity found in client commercials");
	//			return null;
	//		}
	//		// String
	//		// supplierName=clientCommBRIJson.getJSONObject("commonElements").getString("supplierName");
	//		Map<String, HashMap<String, String>> clientEntityToCommToTypeMap = new HashMap<String, HashMap<String, String>>();
	//		for (int i = 0; i < entityDetailsJsonArr.length(); i++) {
	//			entityDetailsJson = entityDetailsJsonArr.getJSONObject(i);
	//			String entityName = entityDetailsJson.getString(JSON_PROP_ENTITYNAME);
	//			// String entityMarket=entityDetailsJson.getString("entityMarket");
	//			JSONArray commHeadJsonArr = null;
	//			commHeadJsonArr = entityDetailsJson.optJSONArray(JSON_PROP_COMMERCIAL_HEAD);
	//			if (commHeadJsonArr == null) {
	//				logger.warn("No commercial heads found in entity " + entityName);
	//				continue;
	//			}
	//			clientEntityToCommToTypeMap.put(entityName, getEntityCommercialsHeadsAndTheirType(commHeadJsonArr));
	//		}
	//		return clientEntityToCommToTypeMap;
	//	}

	//	private static HashMap<String, String> getEntityCommercialsHeadsAndTheirType(JSONArray entityCommHeadJsonArr) {
	//
	//		HashMap<String, String> commToTypeMap = new HashMap<String, String>();
	//		JSONObject commHeadJson;
	//		for (int i = 0; i < entityCommHeadJsonArr.length(); i++) {
	//			commHeadJson = entityCommHeadJsonArr.getJSONObject(i);
	//			commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMERCIAL_HEAD_NAME), commHeadJson.getString(JSON_PROP_COMMERCIALTYPE));
	//		}
	//		return commToTypeMap;
	//	}

	// private static Map<String, BigDecimal> getPaxCountsFromRequest(JSONObject
	// tourActivityRequestJson) {
	// Map<String, BigDecimal> paxInfoMap = new LinkedHashMap<String, BigDecimal>();
	// JSONObject paxInfo = null;
	//
	// JSONArray reqPaxInfoJsonArr =
	// tourActivityRequestJson.getJSONArray("paxInfo");
	// for (int i = 0; i < reqPaxInfoJsonArr.length(); i++) {
	// paxInfo = reqPaxInfoJsonArr.getJSONObject(i);
	// paxInfoMap.put(paxInfo.getString(JSON_PROP_PAXTYPE), new
	// BigDecimal(paxInfo.getInt("quantity")));
	// }
	//
	// return paxInfoMap;
	// }

	private static JSONArray getClientCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
		return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results")
				.getJSONObject(0).getJSONObject(JSON_PROP_VALUE)
				.getJSONObject("cnk.activities_commercialscalculationengine.clienttransactionalrules.Root")
				.getJSONArray("businessRuleIntake");
	}

	private static JSONArray getSupplierCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
		return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results")
				.getJSONObject(0).getJSONObject(JSON_PROP_VALUE)
				.getJSONObject("cnk.activities_commercialscalculationengine.suppliertransactionalrules.Root")
				.getJSONArray("businessRuleIntake");
	}

	//	private static Map<String, String> getSupplierCommercialsAndTheirType(JSONObject suppCommBRIJson) {
	//		// ----------------------------------------------------------------------
	//		// Retrieve commercials head array from supplier commercials and find type
	//		// (Receivable, Payable) for commercials
	//		JSONArray commHeadJsonArr = null;
	//		JSONObject commHeadJson = null;
	//		Map<String, String> commToTypeMap = new HashMap<String, String>();
	//		commHeadJsonArr = suppCommBRIJson.optJSONArray(JSON_PROP_COMMERCIAL_HEAD);
	//		if (commHeadJsonArr == null) {
	//			logger.warn("No commercial heads found in supplier commercials");
	//			return null;
	//		}
	//
	//		for (int j = 0; j < commHeadJsonArr.length(); j++) {
	//			commHeadJson = commHeadJsonArr.getJSONObject(j);
	//			commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMERCIAL_HEAD_NAME), commHeadJson.getString(JSON_PROP_COMMERCIALTYPE));
	//		}
	//
	//		return commToTypeMap;
	//	}

	//	private static void calculateSummary(Map<String, BigDecimal> paxInfoMap, JSONObject tourActivityResponseJson, boolean retainSuppFares) {
	//		JSONArray respPricingArr = tourActivityResponseJson.optJSONArray(JSON_PROP_PRICING);
	//		String currencyCode = null;
	//		if (null != respPricingArr && respPricingArr.length() > 0) {
	//			currencyCode = respPricingArr.getJSONObject(0).optString(JSON_PROP_CCYCODE);
	//		}
	//
	//		/**
	//		 * IMPORTANT NOTE : LENGTH > 1 IS CHECKED FOR THE PURPOSE THAT IT IS NOT AMONG
	//		 * THE SUPPLIERS WHERE SUMMARY IS ONLY COMING AND HENCE ALREADY HANDLED PRIOR TO
	//		 * IT. IF ADULT AND CHILD IS COMING LENGTH OF THE ARRAY WILL BE GREATER THEN ONE
	//		 * AND THE SUMMARY IS CALCULATED BASED ON COUNT OF PAX AND COMMERCIALS APPLIED
	//		 * ON EACH ONE OF THEM.
	//		 */
	//		if (respPricingArr.length() > 1) {
	//			JSONObject respSummaryPricing = new JSONObject();
	//			respSummaryPricing.put(JSON_PROP_PARTICIPANTCATEGORY, JSON_PROP_SUMMARY);
	//			respSummaryPricing.put(JSON_PROP_TOTALPRICE, new BigDecimal(0));
	//			respSummaryPricing.put(JSON_PROP_CCYCODE, currencyCode);
	//
	//			JSONObject suppSummaryPricing = null;
	//			Map<String, JSONObject> suppCommTotalsMap = null;
	//			Map<String, HashMap<String, JSONObject>> clientCommTotalsMap = null;
	//			if (retainSuppFares) {
	//				suppSummaryPricing = new JSONObject();
	//				suppSummaryPricing.put(JSON_PROP_PARTICIPANTCATEGORY, JSON_PROP_SUMMARY);
	//				suppSummaryPricing.put(JSON_PROP_TOTALPRICE, new BigDecimal(0));
	//				suppSummaryPricing.put(JSON_PROP_CCYCODE, currencyCode);
	//				suppCommTotalsMap = new HashMap<String, JSONObject>();
	//				clientCommTotalsMap = new HashMap<String, HashMap<String, JSONObject>>();
	//			}
	//			for (int i = 0; i < respPricingArr.length(); i++) {
	//				JSONObject respPricing = respPricingArr.getJSONObject(i);
	//				String participantCategory = respPricing.getString(JSON_PROP_PARTICIPANTCATEGORY);
	//
	//				if (JSON_PROP_SUMMARY.equals(participantCategory)) {
	//					respPricingArr.put(i, respSummaryPricing);
	//					if (retainSuppFares) {
	//						tourActivityResponseJson.getJSONObject(JSON_PROP_SUPPPRICEINFO).getJSONArray(JSON_PROP_PRICING)
	//						.put(i, suppSummaryPricing);
	//					}
	//					continue;
	//				}
	//
	//				if (paxInfoMap.containsKey(participantCategory)) {
	//					BigDecimal participantCategoryCount = paxInfoMap.get(participantCategory);
	//					respSummaryPricing.put(JSON_PROP_TOTALPRICE, respSummaryPricing.getBigDecimal(JSON_PROP_TOTALPRICE)
	//							.add(respPricing.getBigDecimal(JSON_PROP_TOTALPRICE).multiply(participantCategoryCount)));
	//					if (retainSuppFares) {
	//						JSONObject suppPricing = tourActivityResponseJson.getJSONObject(JSON_PROP_SUPPPRICEINFO)
	//								.getJSONArray(JSON_PROP_PRICING).getJSONObject(i);
	//						suppSummaryPricing.put(JSON_PROP_TOTALPRICE,
	//								suppSummaryPricing.getBigDecimal(JSON_PROP_TOTALPRICE).add(suppPricing
	//										.getBigDecimal(JSON_PROP_TOTALPRICE).multiply(participantCategoryCount)));
	//
	//						JSONArray suppCommJsonArr = suppPricing.optJSONArray(JSON_PROP_SUPPLIERCOMMERCIALS);
	//						JSONArray clientCommJsonArr = suppPricing.optJSONArray(JSON_PROP_CLIENTCOMMERCIALS);
	//						// If no supplier commercials have been defined in BRMS, the JSONArray for
	//						// suppCommJsonArr will be null.
	//						// In this case, log a message and proceed with other calculations.
	//						if (suppCommJsonArr == null) {
	//							logger.warn("No supplier commercials found");
	//						} else {
	//							for (int j = 0; j < suppCommJsonArr.length(); j++) {
	//								JSONObject suppCommJson = suppCommJsonArr.getJSONObject(j);
	//								String suppCommName = suppCommJson.getString(JSON_PROP_COMMERCIALNAME);
	//								JSONObject suppCommTotalsJson = null;
	//								if (suppCommTotalsMap.containsKey(suppCommName)) {
	//									suppCommTotalsJson = suppCommTotalsMap.get(suppCommName);
	//									suppCommTotalsJson.put(JSON_PROP_COMMERCIALAMOUNT,
	//											suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMERCIALAMOUNT)
	//											.add(suppCommJson.getBigDecimal(JSON_PROP_COMMERCIALAMOUNT)
	//													.multiply(participantCategoryCount)));
	//								} else {
	//									suppCommTotalsJson = new JSONObject();
	//									suppCommTotalsJson.put(JSON_PROP_COMMERCIALNAME, suppCommName);
	//									suppCommTotalsJson.put(JSON_PROP_COMMERCIALTYPE,
	//											suppCommJson.getString(JSON_PROP_COMMERCIALTYPE));
	//									suppCommTotalsJson.put(JSON_PROP_COMMERCIALAMOUNT,
	//											suppCommJson.getBigDecimal(JSON_PROP_COMMERCIALAMOUNT)
	//											.multiply(participantCategoryCount));
	//									suppCommTotalsJson.put(JSON_PROP_COMMERCIALCURRENCY,
	//											suppCommJson.optString(JSON_PROP_COMMERCIALCURRENCY));
	//									suppCommTotalsMap.put(suppCommName, suppCommTotalsJson);
	//								}
	//
	//							}
	//							JSONArray suppCommTotalsJsonArr = new JSONArray();
	//							Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet()
	//									.iterator();
	//							while (suppCommTotalsIter.hasNext()) {
	//								suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
	//							}
	//							suppSummaryPricing.put(JSON_PROP_SUPPLIERCOMMERCIALS, suppCommTotalsJsonArr);
	//						}
	//
	//						if (clientCommJsonArr == null) {
	//							logger.warn("No Client commercials found");
	//						} else {
	//
	//							for (int j = 0; j < clientCommJsonArr.length(); j++) {
	//
	//								JSONObject clientCommJson = clientCommJsonArr.getJSONObject(j);
	//								String entityName = clientCommJson.getString(JSON_PROP_ENTITYNAME);
	//								JSONArray entityCommercialsJSONArr = clientCommJson
	//										.getJSONArray(JSON_PROP_ENTITYCOMMERCIALS);
	//								JSONObject entityCommTotalsJson = null;
	//								HashMap<String, JSONObject> entityCommTotalsMap = null;
	//
	//								if (clientCommTotalsMap.containsKey(entityName)) {
	//
	//									entityCommTotalsMap = clientCommTotalsMap.get(entityName);
	//									for (int k = 0; k < entityCommercialsJSONArr.length(); k++) {
	//										JSONObject entityComm = entityCommercialsJSONArr.getJSONObject(k);
	//										String entityCommName = entityComm.getString(JSON_PROP_COMMERCIALNAME);
	//										entityCommTotalsJson = entityCommTotalsMap.get(entityCommName);
	//										entityCommTotalsJson.put(JSON_PROP_COMMERCIALAMOUNT,
	//												entityCommTotalsJson.getBigDecimal(JSON_PROP_COMMERCIALAMOUNT)
	//												.add(entityComm.getBigDecimal(JSON_PROP_COMMERCIALAMOUNT)
	//														.multiply(participantCategoryCount)));
	//
	//									}
	//								}
	//
	//								else {
	//									entityCommTotalsMap = new HashMap<String, JSONObject>();
	//									clientCommTotalsMap.put(entityName, entityCommTotalsMap);
	//									for (int k = 0; k < entityCommercialsJSONArr.length(); k++) {
	//										JSONObject entityComm = entityCommercialsJSONArr.getJSONObject(k);
	//										String entityCommName = entityComm.getString(JSON_PROP_COMMERCIALNAME);
	//										entityCommTotalsJson = new JSONObject();
	//										entityCommTotalsJson.put(JSON_PROP_COMMERCIALNAME, entityCommName);
	//										entityCommTotalsJson.put(JSON_PROP_COMMERCIALTYPE,
	//												entityComm.getString(JSON_PROP_COMMERCIALTYPE));
	//										entityCommTotalsJson.put(JSON_PROP_COMMERCIALAMOUNT,
	//												entityComm.getBigDecimal(JSON_PROP_COMMERCIALAMOUNT)
	//												.multiply(participantCategoryCount));
	//										entityCommTotalsJson.put(JSON_PROP_COMMERCIALCURRENCY,
	//												entityComm.optString(JSON_PROP_COMMERCIALCURRENCY));
	//										entityCommTotalsJson.put(JSON_PROP_COMMENTITYTYPE,
	//												entityComm.opt(JSON_PROP_COMMENTITYTYPE));
	//										entityCommTotalsJson.put(JSON_PROP_COMMENTITYID,
	//												entityComm.optString(JSON_PROP_COMMENTITYID));
	//										entityCommTotalsJson.put(JSON_PROP_PARENTCLIENTID,
	//												entityComm.optString(JSON_PROP_PARENTCLIENTID));
	//										entityCommTotalsJson.put(JSON_PROP_CLIENTID,
	//												entityComm.optString(JSON_PROP_CLIENTID));
	//										entityCommTotalsMap.put(entityCommName, entityCommTotalsJson);
	//									}
	//
	//								}
	//
	//							}
	//
	//							JSONArray clientCommTotalsJsonArr = new JSONArray();
	//							for (String entityName : clientCommTotalsMap.keySet()) {
	//								JSONObject entityCommercials = new JSONObject();
	//								entityCommercials.put(JSON_PROP_ENTITYNAME, entityName);
	//								Map<String, JSONObject> entityCommercialsMap = clientCommTotalsMap.get(entityName);
	//								for (Map.Entry<String, JSONObject> entityCommercial : entityCommercialsMap.entrySet()) {
	//									entityCommercials.append(JSON_PROP_ENTITYCOMMERCIALS, entityCommercial.getValue());
	//								}
	//								clientCommTotalsJsonArr.put(entityCommercials);
	//							}
	//							suppSummaryPricing.put(JSON_PROP_CLIENTCOMMERCIALS, clientCommTotalsJsonArr);
	//						}
	//
	//					}
	//				}
	//			}
	//		}
	//	}

	protected static JSONObject getSupplierResponseJSON(Element[] tourActivityAvailRSWrapperElems,
			JSONObject reqBodyJson, HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap)
					throws ValidationException {

		JSONObject resJson = new JSONObject();
		Element[] tourActivityElems;
		JSONArray activityInfoJsonArr = new JSONArray();
		resJson.put(JSON_PROP_ACTIVITYINFO, activityInfoJsonArr);
		JSONObject activityInfoJson = new JSONObject();
		JSONArray tourActivityJsonArr = new JSONArray();
		activityInfoJson.put(JSON_PROP_TOURACTIVITYINFO, tourActivityJsonArr);
		activityInfoJsonArr.put(activityInfoJson);
		for (int i = 0; i < tourActivityAvailRSWrapperElems.length; i++) {

			Element tourActivityAvailRSWrapperElement = tourActivityAvailRSWrapperElems[i];
			String supplierID = XMLUtils.getValueAtXPath(tourActivityAvailRSWrapperElement, "./sig1:SupplierID");

			Element[] tourActivityAvailRSElems = XMLUtils.getElementsAtXPath(tourActivityAvailRSWrapperElement, "./ns:OTA_TourActivityAvailRS");

			for (Element tourActivityAvailRSElem : tourActivityAvailRSElems) {


				if (XMLUtils.getValueAtXPath(tourActivityAvailRSElem, "./ns:Success").equalsIgnoreCase("false")) {

					validateSupplierResponse(supplierID, tourActivityAvailRSElem);

				}
				else{


					tourActivityElems = XMLUtils.getElementsAtXPath(tourActivityAvailRSElem, "./ns:TourActivityInfo");


					for (Element tourActivityElem : tourActivityElems) {
						JSONObject tourActivityJson = getTourActivityJSON(tourActivityElem, supplierID, paxTypeCountMap.get(i));
						tourActivityJsonArr.put(tourActivityJson);
					}
				}
			}

		}
		return resJson;
	}

	private static JSONObject getTourActivityJSON(Element tourActivityElem, String supplierID, HashMap<String, BigDecimal> paxCountMap) {
		JSONObject tourActivityJson = new JSONObject();
		tourActivityJson.put(SUPPLIER_ID, supplierID);
		tourActivityJson.put(JSON_PROP_BASICINFO, getBasicInfoJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:BasicInfo")));
		tourActivityJson.put(JSON_PROP_SCHEDULE, getScheduleJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Schedule")));
		tourActivityJson.put(JSON_PROP_DESCRIPTION, getDescriptionJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Description")));
		tourActivityJson.put(JSON_PROP_CATEGORYANDTYPE, getCategoryTypeJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:CategoryAndType")));
		tourActivityJson.put(JSON_PROP_LOCATION, getLocationJson(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Location")));
		tourActivityJson.put("activityTotalPricingInfo", getPricingJsonV2(XMLUtils.getFirstElementAtXPath(tourActivityElem, "./ns:Pricing"), supplierID, paxCountMap));

		return tourActivityJson;
	}

	public static JSONObject getLocationJson(Element locationElem) {
		JSONObject location = new JSONObject();
		JSONObject country = new JSONObject();
		JSONObject region = new JSONObject();

		country.put("countryName", XMLUtils.getValueAtXPath(locationElem, "./ns:Address/ns:CountryName"));
		country.put("countryCode", XMLUtils.getValueAtXPath(locationElem, "./ns:Address/ns:CountryName/@Code"));
		location.put("country", country);

		region.put("regionName", XMLUtils.getValueAtXPath(locationElem, "./ns:Region/@RegionName"));
		region.put("regionCode", XMLUtils.getValueAtXPath(locationElem, "./ns:Region/@RegionCode"));
		location.put("region", region);

		// location.put(JSON_PROP_STATEPROV,
		// XMLUtils.getValueAtXPath(locationElem,
		// "./ns:Address/ns:StateProv/@StateCode"));
		// location.put(JSON_PROP_COUNTRYNAME, XMLUtils.getValueAtXPath(locationElem,
		// "./ns:Address/ns:CountryName"));
		// location.put(JSON_PROP_ADDRESSLINE, XMLUtils.getValueAtXPath(locationElem,
		// "./ns:Address/ns:AddressLine"));
		//
		// JSONObject positionJson = new JSONObject();
		// positionJson.put(JSON_PROP_LONGITUDE, XMLUtils.getValueAtXPath(locationElem,
		// "./ns:Position/@Latitude"));
		// positionJson.put(JSON_PROP_LATITUDE, XMLUtils.getValueAtXPath(locationElem,
		// "./ns:Position/@Longitude"));
		//
		// location.put(JSON_PROP_POSITION, positionJson);

		return location;
	}

	public static JSONObject getPricingJsonV2(Element pricingElem, String supplierID,
			HashMap<String, BigDecimal> paxCountMap) {

		JSONObject activityTotalPricingInfo = new JSONObject();

		String totalPricingCurrencyCode = XMLUtils.getValueAtXPath(pricingElem, "./ns:Summary/@CurrencyCode");

		getActivityTotalPricingInfo(pricingElem, activityTotalPricingInfo, totalPricingCurrencyCode);

		getTaxesJSON(pricingElem, activityTotalPricingInfo, totalPricingCurrencyCode);

		getPaxPriceInfoArr(pricingElem, paxCountMap, activityTotalPricingInfo);



		return activityTotalPricingInfo;
	}


	//	private static JSONArray getPricingJson(Element pricingElem, String supplierID) {
	//		JSONArray pricingJsonArr = new JSONArray();
	//		if (XMLUtils.getValueAtXPath(pricingElem, "./@PerPaxPriceInd").equalsIgnoreCase("true"))
	//			pricingJsonArr = ActivityPricing.paxPricing.getPricingJson(pricingJsonArr, pricingElem);
	//		else
	//			pricingJsonArr = ActivityPricing.summaryPricing.getPricingJson(pricingJsonArr, pricingElem);
	//		return pricingJsonArr;
	//	}

	private static JSONObject getCategoryTypeJson(Element categoryTypeElem) {
		JSONObject categoryTypeJson = new JSONObject();
		Element[] categoryElems = XMLUtils.getElementsAtXPath(categoryTypeElem, "./ns:Category");
		Element[] typeElems = XMLUtils.getElementsAtXPath(categoryTypeElem, "./ns:Type");

		JSONArray categoryArr = new JSONArray();
		JSONArray typeArr = new JSONArray();

		for (Element categoryElem : categoryElems) {
			// categoryTypeJson.append(JSON_PROP_CATEGORY,
			// XMLUtils.getValueAtXPath(categoryElem, "./@Code"));
			JSONObject categoryJson = new JSONObject();
			categoryJson.put("code", XMLUtils.getValueAtXPath(categoryElem, "./@Code"));
			categoryJson.put("value", categoryElem.getTextContent());

			categoryArr.put(categoryJson);
		}
		categoryTypeJson.put(JSON_PROP_CATEGORY, categoryArr);

		for (Element typeElem : typeElems) {
			// categoryTypeJson.append(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(typeElem,
			// "./@Code"));
			JSONObject typeJson = new JSONObject();
			typeJson.put("code", XMLUtils.getValueAtXPath(typeElem, "./@Code"));
			typeJson.put("value", typeElem.getTextContent());
			typeArr.put(typeJson);
		}
		categoryTypeJson.put(JSON_PROP_TYPE, typeArr);

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
				opTimeJson.put(JSON_PROP_STARTTIME, XMLUtils.getValueAtXPath(opTime, "./@Start"));
				String[] days = { "Mon", "Tue", "Weds", "Thur", "Fri", "Sat", "Sun" };
				for (String day : days) {
					if ("true".equals(XMLUtils.getValueAtXPath(opTime, "./@" + day))) {
						opTimeJson.append(JSON_PROP_DAYS, day);
					}
				}
				detailJson.append(JSON_PROP_OPERATIONTIMES, opTimeJson);
			}
			scheduleJson.append(JSON_PROP_DETAILS, detailJson);
		}
		return scheduleJson;
	}

	private static JSONObject getBasicInfoJson(Element basicInfoElem) {
		JSONObject basicInfoJson = new JSONObject();
		basicInfoJson.put(JSON_PROP_NAME, XMLUtils.getValueAtXPath(basicInfoElem, "./@Name"));
		basicInfoJson.put(JSON_PROP_SUPPLIERPRODUCTCODE, XMLUtils.getValueAtXPath(basicInfoElem, "./@SupplierProductCode"));
		basicInfoJson.put(JSON_PROP_AVAILABILITYSTATUS, XMLUtils.getValueAtXPath(basicInfoElem, "./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Availability_Status"));
		basicInfoJson.put(JSON_PROP_REFERENCE, XMLUtils.getValueAtXPath(basicInfoElem, "./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Supplier_Details/sig1:Reference"));
		basicInfoJson.put(JSON_PROP_RATEKEY, XMLUtils.getValueAtXPath(basicInfoElem, "./ns:TPA_Extensions/sig1:Activity_TPA/sig1:Supplier_Details/sig1:RateKey"));
		basicInfoJson.put(JSON_PROP_UNIQUEKEY, XMLUtils.getValueAtXPath(basicInfoElem, "./ns:TPA_Extensions/sig1:Activity_TPA/sig1:UniqueKey"));
		return basicInfoJson;

	}

	public static JSONObject getEmptyResponse(JSONObject reqHdrJson) {
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, new JSONObject());
		return resJson;
	}

	private static void getActivityTotalPricingInfo(Element pricingElem, JSONObject activityTotalPricingInfo,
			String totalPricingCurrencyCode) {
		activityTotalPricingInfo.put("activitySummaryPrice", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(pricingElem, "./ns:Summary/@Amount"), 0));
		activityTotalPricingInfo.put("currencyCode", totalPricingCurrencyCode);
	}

	private static void getTaxesJSON(Element pricingElem, JSONObject activityTotalPricingInfo, String totalPricingCurrencyCode) {
		JSONObject taxesJson = new JSONObject();

		BigDecimal taxTotal = new BigDecimal(0);
		JSONArray taxArr = new JSONArray();
		Element[] taxAmountElems = XMLUtils.getElementsAtXPath(pricingElem, "./ns:Summary/ns:TaxAmounts");

		if(taxAmountElems!=null){

			for(int i = 0; i<taxAmountElems.length; i++) {
				JSONObject tax = new JSONObject();
				Element taxAmount = taxAmountElems[i];


				tax.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(taxAmount, "./ns:TaxAmount/@Total"));
				taxTotal = taxTotal.add(new BigDecimal(XMLUtils.getValueAtXPath(taxAmount, "./ns:TaxAmount/@Total")));
				tax.put(JSON_PROP_CURRENCY_CODE, totalPricingCurrencyCode);
				tax.put("taxCode", XMLUtils.getValueAtXPath(taxAmount, "./ns:TaxAmount/@Code"));	//Kept for future use when they send taxCode as well
				taxArr.put(tax);

			}

			taxesJson.put(JSON_PROP_AMOUNT, taxTotal);
			taxesJson.put(JSON_PROP_CURRENCY_CODE, totalPricingCurrencyCode);
			taxesJson.put(JSON_PROP_TAX, taxArr);
		}

		activityTotalPricingInfo.put(JSON_PROP_TAXES, taxesJson);
	}

	private static void getPaxPriceInfoArr(Element pricingElem, HashMap<String, BigDecimal> paxCountMap,
			JSONObject activityTotalPricingInfo) {
		JSONArray paxPriceInfoArr = new JSONArray();
		if (XMLUtils.getValueAtXPath(pricingElem, "./@PerPaxPriceInd").equalsIgnoreCase("true")) {

			activityTotalPricingInfo.put("activityWisePricing", false);

			Element[] participantCategoryElems = XMLUtils.getElementsAtXPath(pricingElem, "./ns:ParticipantCategory");

			for (Element participantCategoryElem : participantCategoryElems) {
				JSONObject participantPricingJson = new JSONObject();

				//2018-09-05 Since the participantCategoryElem is not uniform, below code will check if it has qualifierInfo to add it into pricing, else it wont add.


				if(XMLUtils.getValueAtXPath(participantCategoryElem, "./ns:QualifierInfo")!=""){
					participantPricingJson.put("participantCategory", ActivityPassengerType.valueOf(XMLUtils.getValueAtXPath(participantCategoryElem, "./ns:QualifierInfo")).toString());
					participantPricingJson.put("totalPrice", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(participantCategoryElem, "./ns:Price/@Amount"), 0));
					participantPricingJson.put("currencyCode", XMLUtils.getValueAtXPath(participantCategoryElem, "./ns:Price/@CurrencyCode"));
					participantPricingJson.put("age", XMLUtils.getValueAtXPath(participantCategoryElem, "./@Age"));

					participantPricingJson.put("quantity", paxCountMap.get(participantPricingJson.get("participantCategory")));
					paxPriceInfoArr.put(participantPricingJson);
				}

			}

			activityTotalPricingInfo.put("paxPriceInfo", paxPriceInfoArr);

		}

		else {
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

			// TODO: If PerPaxPriceInd comes as false? we calculate the prices by dividing the activityTotalPrice by paxCount?
		}
	}


	public static void calculatePricesV2(JSONObject reqJson, JSONObject resJson, JSONObject suppCommResJson, JSONObject clientCommResJson, boolean retainSuppFares) {

		JSONObject reqHdrJson = reqJson.getJSONObject("requestHeader");
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);

		Map<String, String> suppCommToTypeMap = getSupplierCommercialsAndTheirTypeV2(suppCommResJson);
		Map<String, String> clntCommToTypeMap = getClientCommercialsAndTheirTypeV2(clientCommResJson);

		ClientType clientType = ClientType.valueOf(reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
		String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);

		// ----------------------------------------------------------------------
		// Retrieve array of ActivityInfo from SI XML converted response JSON
		JSONArray activityInfoArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray("activityInfo");
		Map<String, JSONArray> ccommSuppBRIJsonMap = getSupplierWiseJourneyDetailsFromClientCommercials(clientCommResJson);
		Map<String, Integer> suppIndexMap = new HashMap<String, Integer>();

		for (int i = 0; i < activityInfoArr.length(); i++) {

			JSONObject activityInfoJson = activityInfoArr.getJSONObject(i);
			JSONArray tourActivityInfoArr = activityInfoJson.getJSONArray("tourActivityInfo");

			//			JSONObject reqActivityInfo = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACTIVITYINFO)
			//					.getJSONObject(i);

			//			Map<String, BigDecimal> paxCountMap = paxTypeCountMap.get(i);

			// paxCountsMap = getPaxCountsFromRequest(tourActivityRequestJson);

			for (int j = 0; j < tourActivityInfoArr.length(); j++) {

				JSONObject tourActivityInfoJson = tourActivityInfoArr.getJSONObject(j);
				String suppID = tourActivityInfoJson.getString("supplierID");

				// The BRMS client commercials response JSON contains one businessRuleIntake for
				// each supplier. Inside businessRuleIntake, the
				// order of each supplier search result is maintained within ActivityDetails
				// child array. Therefore, keep track of index for each
				// businessRuleIntake.ActivityDetails for each supplier.
				int idx = (suppIndexMap.containsKey(suppID)) ? (suppIndexMap.get(suppID) + 1) : 0;
				suppIndexMap.put(suppID, idx);
				JSONObject ccommActDtlsJson = (ccommSuppBRIJsonMap.containsKey(suppID)) ? ccommSuppBRIJsonMap.get(suppID).getJSONObject(idx) : null;
				if (ccommActDtlsJson == null) {
					logger.info(String.format("BRMS/ClientComm: \"businessRuleIntake\" JSON element for supplier %s not found.", suppID));
				}

				// The following PriceComponentsGroup accepts price subcomponents one-by-one and
				// automatically calculates totals
				PriceComponentsGroup totalFareCompsGroup = new PriceComponentsGroup("activityTotalPrice", clientCcyCode, new BigDecimal(0), true);
				PriceComponentsGroup totalIncentivesGroup = new PriceComponentsGroup("incentives", clientCcyCode, BigDecimal.ZERO, true);
				JSONArray suppPaxTypeFaresJsonArr = new JSONArray();

				// Adding clientCommercialInfo
				JSONArray clientCommercialActInfoArr = new JSONArray();
				JSONObject activityTotalPricingInfo = tourActivityInfoJson.getJSONObject("activityTotalPricingInfo");
				JSONArray paxTypeFaresJsonArr = activityTotalPricingInfo.getJSONArray("paxPriceInfo");
				String suppCcyCode = activityTotalPricingInfo.getString("currencyCode");


				for (int k = 0; k < paxTypeFaresJsonArr.length(); k++) {
					JSONObject paxTypeFareJson = paxTypeFaresJsonArr.getJSONObject(k);
					// if
					// (!paxTypeFareJson.getString("participantCategory").equalsIgnoreCase("SUMMARY")){

					// paxType from RepriceRS will be ADT and Commercials will have Adult
					String paxType = ActivityPassengerType.valueOf(paxTypeFareJson.getString("participantCategory")).toString();
					BigDecimal paxCount = paxTypeFareJson.getBigDecimal(JSON_PROP_QUANTITY);
					// String suppCcyCode = paxTypeFareJson.getString(JSON_PROP_CCYCODE);

					JSONObject ccommActPsgrDtlJson = getClientCommercialsActivityDetailsForPassengerType(ccommActDtlsJson, paxType);
					JSONArray clientEntityCommJsonArr = null;
					if (ccommActPsgrDtlJson == null) {
						logger.info(String.format(
								"Passenger type %s details not found client commercial journeyDetails", paxType));
					} else {
						// Only in case of reprice when (retainSuppFares == true), retain supplier
						// commercial
						if (retainSuppFares) {
							appendSupplierCommercialsToPaxTypeFares(paxTypeFareJson, ccommActPsgrDtlJson, suppID, suppCcyCode, suppCommToTypeMap);
						}
						// From the passenger type client commercial JSON, retrieve calculated client
						// entity commercials
						// JSONArray clientEntityCommJsonArr =
						// ccommActPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
						clientEntityCommJsonArr = ccommActPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
						if (clientEntityCommJsonArr == null) {
							logger.warn("Client commercials calculations not found");
						}
					}

					BigDecimal paxTypeTotalFare = new BigDecimal(0);
					JSONObject commPriceJson = new JSONObject();
					// suppPaxTypeFaresJsonArr.put(paxTypeFareJson);

					// Reference CKIL_323141 - There are three types of client commercials that are
					// receivable from clients: Markup, Service Charge (Transaction Fee) &
					// Look-to-Book.
					// Of these, Markup and Service Charge (Transaction Fee) are transactional and
					// need
					// to be considered for selling price.
					// Reference CKIL_231556 (1.1.2.3/BR04) The display price will be calculated as
					// -
					// (Total Supplier Price + Markup + Additional Company Receivable Commercials)

					JSONObject markupCalcJson = null;
					JSONArray clientCommercials = new JSONArray();
					PriceComponentsGroup paxReceivablesCompsGroup = null;
					PriceComponentsGroup totalPaxReceivablesCompsGroup = null;
					for (int l = 0; clientEntityCommJsonArr != null && l < clientEntityCommJsonArr.length(); l++) {
						JSONArray clientEntityCommercialsJsonArr = new JSONArray();
						JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(l);

						// TODO: In case of B2B, do we need to add markups for all client hierarchy
						// levels?
						if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) {
							markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);
							clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(markupCalcJson, clntCommToTypeMap, suppCcyCode));
						}

						// Additional commercialcalc clientCommercial
						// TODO: In case of B2B, do we need to add additional receivable commercials for
						// all client hierarchy levels?
						JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
						// If totals of receivables at all levels is required, the following instance
						// creation needs to move where
						// variable 'paxReceivablesCompsGroup' is declared
						paxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode,
								new BigDecimal(0), true);
						totalPaxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode,
								new BigDecimal(0), true);
						if (additionalCommsJsonArr != null) {
							for (int p = 0; p < additionalCommsJsonArr.length(); p++) {
								JSONObject additionalCommJson = additionalCommsJsonArr.getJSONObject(p);
								String additionalCommName = additionalCommJson.optString(JSON_PROP_COMMNAME);
								String additionalCommType = clntCommToTypeMap.get(additionalCommName);
								clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(additionalCommJson, clntCommToTypeMap, suppCcyCode));

								if (COMM_TYPE_RECEIVABLE.equals(additionalCommType)) {
									String additionalCommCcy = additionalCommJson.getString(JSON_PROP_COMMCCY);
									BigDecimal additionalCommAmt = additionalCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket));
									paxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt);
									totalPaxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt.multiply(paxCount));
								}
							}
						}

						JSONObject clientEntityDetailsJson = new JSONObject();
						// JSONArray clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchy();
						ClientInfo[] clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchyArray();
						// clientEntityDetailsJson = clientEntityDetailsArr.getJSONObject(k);
						clientEntityDetailsJson.put(JSON_PROP_CLIENTID, clientEntityDetailsArr[l].getClientId());
						clientEntityDetailsJson.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsArr[l].getParentClienttId());
						clientEntityDetailsJson.put(JSON_PROP_COMMENTITYTYPE, clientEntityDetailsArr[l].getCommercialsEntityType());
						clientEntityDetailsJson.put(JSON_PROP_COMMENTITYID, clientEntityDetailsArr[l].getCommercialsEntityId());
						clientEntityDetailsJson.put(JSON_PROP_CLIENTCOMM, clientEntityCommercialsJsonArr);
						clientCommercials.put(clientEntityDetailsJson);

						// For B2B clients, the incentives of the last client hierarchy level should be
						// accumulated and returned in the response.
						if (l == (clientEntityCommJsonArr.length() - 1)) {
							for (int x = 0; x < clientEntityCommercialsJsonArr.length(); x++) {
								JSONObject clientEntityCommercialsJson = clientEntityCommercialsJsonArr.getJSONObject(x);
								if (COMM_TYPE_PAYABLE.equals(clientEntityCommercialsJson.getString(JSON_PROP_COMMTYPE))) {
									String commCcy = clientEntityCommercialsJson.getString(JSON_PROP_COMMCCY);
									String commName = clientEntityCommercialsJson.getString(JSON_PROP_COMMNAME);
									BigDecimal commAmt = clientEntityCommercialsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(commCcy, clientCcyCode, clientMarket)).multiply(paxCount, MATH_CTX_2_HALF_UP);
									totalIncentivesGroup.add(String.format(mIncvPriceCompFormat, commName), clientCcyCode, commAmt);
								}
							}
						}

					}

					// ------------------------BEGIN----------------------------------
					BigDecimal baseFareAmt = paxTypeFareJson.getBigDecimal("totalPrice");
					// JSONArray ccommTaxDetailsJsonArr = null;
					if (markupCalcJson != null) {
						BigDecimal markup = markupCalcJson.optBigDecimal("commercialAmount", BigDecimal.ZERO);

						// baseFareAmt = fareBreakupJson.optBigDecimal(JSON_PROP_BASEFARE, baseFareAmt);
						// ccommTaxDetailsJsonArr = fareBreakupJson.optJSONArray(JSON_PROP_TAXDETAILS);
						baseFareAmt = baseFareAmt.add(markup);

					}

					commPriceJson.put(JSON_PROP_PAXTYPE, paxTypeFareJson.getString("participantCategory"));
					commPriceJson.put("quantity", paxTypeFareJson.optBigDecimal("quantity",new BigDecimal(1)));

					JSONObject baseFareJson = new JSONObject();
					baseFareJson.put(JSON_PROP_AMOUNT, baseFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
					baseFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);
					commPriceJson.put(JSON_PROP_BASEFARE, baseFareJson);
					paxTypeTotalFare = paxTypeTotalFare.add(baseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
					totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode, baseFareJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));

					// SINCE WE DO NOT RECIEVE TAXES FROM SUPPLIER IN ACTIVITIES
					// int offset = 0;
					// JSONArray paxTypeTaxJsonArr = paxTypeFareJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
					// JSONObject taxesJson = getCommercialPricesTaxesJson(paxTypeTaxJsonArr, ccommTaxDetailsJsonArr, offset, totalFareCompsGroup, paxCount, clientCcyCode, clientMarket);
					// commPriceJson.put(JSON_PROP_TAXES, taxesJson);
					// paxTypeTotalFare = paxTypeTotalFare.add(taxesJson.getBigDecimal(JSON_PROP_TOTAL));
					// offset = paxTypeTaxJsonArr.length();
					// JSONArray paxTypeFeeJsonArr = paxTypeFareJson.getJSONObject(JSON_PROP_FEES).getJSONArray(JSON_PROP_FEE);
					// JSONObject feesJson = getCommercialPricesFeesJson(paxTypeFeeJsonArr, ccommTaxDetailsJsonArr, offset, totalFareCompsGroup, paxCount, clientCcyCode, clientMarket);
					// commPriceJson.put(JSON_PROP_FEES, feesJson);
					// paxTypeTotalFare = paxTypeTotalFare.add(feesJson.getBigDecimal(JSON_PROP_TOTAL));

					// If amount of receivables group is greater than zero, then append to commercial prices
					if (paxReceivablesCompsGroup != null && paxReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
						paxTypeTotalFare = paxTypeTotalFare.add(paxReceivablesCompsGroup.getComponentAmount());
						totalFareCompsGroup.addSubComponent(totalPaxReceivablesCompsGroup, null);
						commPriceJson.put(JSON_PROP_RECEIVABLES, paxReceivablesCompsGroup.toJSON());
					}
					// -------------------------END-----------------------------------

					clientCommercialActInfoArr.put(clientCommercials);
					suppPaxTypeFaresJsonArr.put(paxTypeFareJson);

					JSONObject totalFareJson = new JSONObject();
					totalFareJson.put(JSON_PROP_AMOUNT, paxTypeTotalFare);
					totalFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);
					commPriceJson.put(JSON_PROP_TOTALFARE, totalFareJson);
					if (retainSuppFares)
						commPriceJson.put("clientEntityCommercials", clientCommercials);

					paxTypeFaresJsonArr.put(k, commPriceJson);

					// }
				}



				// Calculate ActTotalFare. This fare will be the one used for sorting. Till 23-05-18 we were using ADT pricing for sorting
				// Adding total fares thing in tourActivityInfoJson rather than pricing array

				activityTotalPricingInfo.put("activitySummaryPrice", totalFareCompsGroup.toJSON());
				activityTotalPricingInfo.remove("currencyCode");

				if (clientType == ClientType.B2B) {
					tourActivityInfoJson.put(JSON_PROP_INCENTIVES, totalIncentivesGroup.toJSON());
				}

				// The supplier fares will be retained only in reprice operation. In reprice, after calculations, supplier prices are saved in Redis cache to be used im book operation.
				if (retainSuppFares) {
					JSONObject suppActPricingInfoJson = new JSONObject();
					JSONObject suppActTotalPriceJson = new JSONObject();
					suppActPricingInfoJson.put("activitySupplierSummaryPrice", suppActTotalPriceJson);
					suppActPricingInfoJson.put("paxPriceInfo", suppPaxTypeFaresJsonArr);

					// Adding taxes json object in supplier fares for stroring it in redis
					suppActPricingInfoJson.put("taxes", new JSONObject(activityTotalPricingInfo.getJSONObject("taxes").toString()));

					// tourActivityInfoJson.put("clientCommercials", clientCommercialActInfoArr);
					tourActivityInfoJson.put(JSON_PROP_SUPPPRICEINFO, suppActPricingInfoJson);
					addSupplierActivityTotalFare(tourActivityInfoJson);
					// tourActivityInfoJson.remove("clientCommercials");
				}

				// Converting tax amount from supplier currency to client currency
				JSONObject taxes = activityTotalPricingInfo.getJSONObject("taxes");
				JSONArray taxesArr = taxes.getJSONArray("tax");

				taxes.put("amount", taxes.getBigDecimal("amount").multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
				taxes.put("currencyCode", clientCcyCode);

				for(int k = 0; k<taxesArr.length(); k++) {
					JSONObject taxesArrJson = taxesArr.getJSONObject(k);
					taxesArrJson.put("amount", taxes.getBigDecimal("amount").multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
					taxesArrJson.put("currencyCode", clientCcyCode);

				}

			}
		}

	}

	private static void addSupplierActivityTotalFare(JSONObject tourActivityInfoJson) {
		JSONObject suppActPricingInfoJson = tourActivityInfoJson.getJSONObject(JSON_PROP_SUPPPRICEINFO);
		JSONObject activityTotalPricingInfo = tourActivityInfoJson.getJSONObject("activityTotalPricingInfo");
		JSONArray paxTypeFaresArr = suppActPricingInfoJson.getJSONArray("paxPriceInfo");

		Map<String, JSONObject> suppCommTotalsMap = new HashMap<String, JSONObject>();
		Map<String, JSONObject> clientCommTotalsMap = new HashMap<String, JSONObject>();
		BigDecimal totalFareAmt = new BigDecimal(0);

		String ccyCode = null;
		JSONObject clientEntityTotalCommercials = null;
		JSONArray totalClientArr = new JSONArray();
		for (int i = 0; i < paxTypeFaresArr.length(); i++) {
			JSONObject paxTypeFare = paxTypeFaresArr.getJSONObject(i);
			JSONObject paxTypeTotalFareJson = paxTypeFare;
			totalFareAmt = totalFareAmt.add(paxTypeTotalFareJson.getBigDecimal("totalPrice").multiply(paxTypeFare.getBigDecimal(JSON_PROP_QUANTITY)));
			ccyCode = (ccyCode == null) ? paxTypeTotalFareJson.getString(JSON_PROP_CCYCODE) : ccyCode;

			JSONArray suppCommJsonArr = paxTypeFare.optJSONArray("supplierCommercials");
			// the order of clientCommercialActInfo will same as that of normal
			// paxTypeFares
			JSONArray clientCommJsonArr = tourActivityInfoJson.getJSONObject("activityTotalPricingInfo").getJSONArray("paxPriceInfo").getJSONObject(i).optJSONArray(JSON_PROP_CLIENTENTITYCOMMS);
			// If no supplier commercials have been defined in BRMS, the JSONArray for
			// suppCommJsonArr will be null.
			// In this case, log a message and proceed with other calculations.
			if (suppCommJsonArr == null) {
				logger.warn("No supplier commercials found");
			} else {
				for (int j = 0; j < suppCommJsonArr.length(); j++) {
					JSONObject suppCommJson = suppCommJsonArr.getJSONObject(j);
					String suppCommName = suppCommJson.getString(JSON_PROP_COMMNAME);
					JSONObject suppCommTotalsJson = null;
					if (suppCommTotalsMap.containsKey(suppCommName)) {
						suppCommTotalsJson = suppCommTotalsMap.get(suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxTypeFare.getBigDecimal(JSON_PROP_QUANTITY))));
					} else {
						suppCommTotalsJson = new JSONObject();
						suppCommTotalsJson.put(JSON_PROP_COMMNAME, suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMTYPE, suppCommJson.getString(JSON_PROP_COMMTYPE));
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxTypeFare.getBigDecimal(JSON_PROP_QUANTITY)));
						suppCommTotalsJson.put(JSON_PROP_COMMCCY, suppCommJson.get(JSON_PROP_COMMCCY).toString());
						suppCommTotalsMap.put(suppCommName, suppCommTotalsJson);
					}
				}
			}

			if (clientCommJsonArr == null) {
				logger.warn("No client commercials found");
			} else {
				for (int l = 0; l < clientCommJsonArr.length(); l++) {
					// TODO:Add jsonElements from clientEntity once they can be fetched.
					JSONObject clientCommJson = clientCommJsonArr.getJSONObject(l);
					JSONObject clientCommEntJson = new JSONObject();

					// JSONObject clientCommJson
					// =clientEntityCommJson.getJSONObject("clientCommercial");
					// clientCommEntJson
					JSONArray clientEntityCommJsonArr = clientCommJson.getJSONArray(JSON_PROP_CLIENTCOMM);

					JSONObject clientCommTotalsJson = null;
					JSONArray clientTotalEntityArray = new JSONArray();

					for (int m = 0; m < clientEntityCommJsonArr.length(); m++) {

						JSONObject clientCommEntityJson = clientEntityCommJsonArr.getJSONObject(m);
						String clientCommName = clientCommEntityJson.getString(JSON_PROP_COMMNAME);

						if (clientCommTotalsMap.containsKey(clientCommName)) {
							clientEntityTotalCommercials = clientCommTotalsMap.get(clientCommName);
							// clientCommTotalsJson=clientEntityTotalCommercials.getJSONObject("clientCommercialsTotal");
							// clientCommTotalsJson = clientCommTotalsMap.get(clientCommName);
							clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT).add(clientCommEntityJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxTypeFare.getBigDecimal(JSON_PROP_QUANTITY))));
						} else {
							clientEntityTotalCommercials = new JSONObject();
							clientCommTotalsJson = new JSONObject();
							clientCommTotalsJson.put(JSON_PROP_COMMNAME, clientCommName);
							clientCommTotalsJson.put(JSON_PROP_COMMTYPE, clientCommEntityJson.getString(JSON_PROP_COMMTYPE));
							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, clientCommEntityJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxTypeFare.getBigDecimal(JSON_PROP_QUANTITY)));
							clientCommTotalsJson.put(JSON_PROP_COMMCCY, clientCommEntityJson.get(JSON_PROP_COMMCCY).toString());
							clientTotalEntityArray.put(clientCommTotalsJson);

							clientCommTotalsMap.put(clientCommName, clientCommTotalsJson);
						}
					}

					if ((clientTotalEntityArray != null) && (clientTotalEntityArray.length() > 0)) {
						clientCommEntJson.put("clientCommercialsTotal", clientTotalEntityArray);
						clientCommEntJson.put(JSON_PROP_CLIENTID, clientCommJson.optString(JSON_PROP_CLIENTID, ""));
						clientCommEntJson.put(JSON_PROP_PARENTCLIENTID, clientCommJson.optString(JSON_PROP_PARENTCLIENTID, ""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYTYPE, clientCommJson.optString(JSON_PROP_COMMENTITYTYPE, ""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYID, clientCommJson.optString(JSON_PROP_COMMENTITYID, ""));

						totalClientArr.put(clientCommEntJson);
					}
				}
			}
		}

		// Convert map of Commercial Head to Commercial Amount to JSONArray and append
		// in suppActPricingInfoJson
		JSONArray suppCommTotalsJsonArr = new JSONArray();
		suppActPricingInfoJson.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr);
		Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet().iterator();
		while (suppCommTotalsIter.hasNext()) {
			suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
		}
		//		suppActPricingInfoJson.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr);

		/*
		 * JSONArray clientCommTotalsJsonArr = new JSONArray(); Iterator<Entry<String,
		 * JSONObject>> clientCommTotalsIter =
		 * clientCommTotalsMap.entrySet().iterator(); while
		 * (clientCommTotalsIter.hasNext()) {
		 * clientCommTotalsJsonArr.put(clientCommTotalsIter.next().getValue()); }
		 */
		activityTotalPricingInfo.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, totalClientArr);

		JSONObject actSummaryPrice = suppActPricingInfoJson.getJSONObject("activitySupplierSummaryPrice");
		actSummaryPrice.put(JSON_PROP_CCYCODE, ccyCode);
		actSummaryPrice.put(JSON_PROP_AMOUNT, totalFareAmt);
	}

	//	private static JSONObject getCommercialPricesTaxesJson(JSONArray paxTypeTaxJsonArr, JSONArray ccommTaxDetailsJsonArr, int offset, PriceComponentsGroup totalFareCompsGroup, BigDecimal paxCount, String clientCcyCode, String clientMarket) {
	//		BigDecimal taxesTotal = new BigDecimal(0);
	//		JSONObject taxesJson = new JSONObject();
	//		JSONArray taxJsonArr = new JSONArray();
	//		String suppCcyCode = null;
	//		String taxCode = null;
	//
	//		JSONObject ccommTaxDetailJson = null;
	//		JSONObject paxTypeTaxJson = null;
	//		JSONObject taxJson = null;
	//		for (int l = 0; l < paxTypeTaxJsonArr.length(); l++) {
	//			paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
	//			suppCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
	//			taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXCODE);
	//
	//			// Access the tax array returned by client commercials in a positional manner
	//			// instead of
	//			// searching in that array using taxcode/feecode.
	//			taxJson = paxTypeTaxJson;
	//			ccommTaxDetailJson = (ccommTaxDetailsJsonArr != null) ? ccommTaxDetailsJsonArr.optJSONObject(offset + l)
	//					: null;
	//			if (ccommTaxDetailJson != null) {
	//				// If tax JSON is found in commercials, replace existing tax details with one
	//				// from commercials
	//				BigDecimal taxAmt = ccommTaxDetailJson.getBigDecimal(JSON_PROP_TAXVALUE);
	//				taxJson = new JSONObject();
	//				taxJson.put(JSON_PROP_TAXCODE, taxCode);
	//				taxJson.put(JSON_PROP_AMOUNT,
	//						taxAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
	//				taxJson.put(JSON_PROP_CCYCODE, clientCcyCode);
	//			}
	//
	//			taxJsonArr.put(taxJson);
	//			taxesTotal = taxesTotal.add(taxJson.getBigDecimal(JSON_PROP_AMOUNT));
	//			totalFareCompsGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXCODE),
	//					clientCcyCode, taxJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
	//		}
	//
	//		taxesJson.put(JSON_PROP_TAX, taxJsonArr);
	//		taxesJson.put(JSON_PROP_TOTAL, taxesTotal);
	//		taxesJson.put(JSON_PROP_CCYCODE, clientCcyCode);
	//		return taxesJson;
	//	}

	private static JSONObject convertToClientEntityCommercialJson(JSONObject clientCommJson,
			Map<String, String> clntCommToTypeMap, String suppCcyCode) {
		JSONObject clientCommercial = new JSONObject();
		String commercialName = clientCommJson.getString(JSON_PROP_COMMNAME);
		clientCommercial.put(JSON_PROP_COMMTYPE, clntCommToTypeMap.getOrDefault(commercialName, "?"));
		clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
		clientCommercial.put(JSON_PROP_COMMNAME, clientCommJson.getString(JSON_PROP_COMMNAME));
		clientCommercial.put(JSON_PROP_COMMCCY, clientCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
		clientCommercial.put(JSON_PROP_MDMRULEID, clientCommJson.optString("mdmruleId", ""));
		return clientCommercial;
	}

	private static void appendSupplierCommercialsToPaxTypeFares(JSONObject paxTypeFareJson,
			JSONObject ccommActPsgrDtlJson, String suppID, String suppCcyCode, Map<String, String> suppCommToTypeMap) {
		JSONArray suppCommJsonArr = new JSONArray();
		paxTypeFareJson.put("supplierCommercials", suppCommJsonArr);
		JSONArray ccommSuppCommJsonArr = ccommActPsgrDtlJson.optJSONArray(JSON_PROP_COMMDETAILS);
		// If no supplier commercials have been defined in BRMS, the JSONArray for
		// ccommSuppCommJsonArr will be null.
		// In this case, log a message and proceed with other calculations.
		if (ccommSuppCommJsonArr == null) {
			logger.warn(String.format("No supplier commercials found for supplier %s", suppID));
			return;
		}

		for (int x = 0; x < ccommSuppCommJsonArr.length(); x++) {
			JSONObject ccommSuppCommJson = ccommSuppCommJsonArr.getJSONObject(x);
			JSONObject suppCommJson = new JSONObject();
			suppCommJson.put(JSON_PROP_COMMNAME, ccommSuppCommJson.getString(JSON_PROP_COMMNAME));
			suppCommJson.put(JSON_PROP_COMMTYPE,
					suppCommToTypeMap.get(ccommSuppCommJson.getString(JSON_PROP_COMMNAME)));
			suppCommJson.put(JSON_PROP_COMMAMOUNT, ccommSuppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
			suppCommJson.put(JSON_PROP_COMMCCY, ccommSuppCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
			suppCommJson.put(JSON_PROP_MDMRULEID, ccommSuppCommJson.optString("mdmruleId", ""));
			suppCommJsonArr.put(suppCommJson);
		}
		//		paxTypeFareJson.put("supplierCommercials", suppCommJsonArr);
	}

	private static JSONObject getClientCommercialsActivityDetailsForPassengerType(JSONObject ccommActDtlsJson,
			String paxType) {
		if (ccommActDtlsJson == null || paxType == null) {
			return null;
		}

		// Search this paxType in client commercials journeyDetails
		JSONArray ccommActPsgrDtlsJsonArr = ccommActDtlsJson.getJSONArray("pricingDetails");
		for (int k = 0; k < ccommActPsgrDtlsJsonArr.length(); k++) {
			JSONObject ccommActPsgrDtlsJson = ccommActPsgrDtlsJsonArr.getJSONObject(k);
			if (paxType.equals(ccommActPsgrDtlsJson.getString("participantCategory"))) {
				return ccommActPsgrDtlsJson;
			}
		}

		return null;
	}

	private static Map<String, String> getSupplierCommercialsAndTheirTypeV2(JSONObject suppCommResJson) {
		JSONArray commHeadJsonArr = null;
		JSONObject commHeadJson = null;
		Map<String, String> commToTypeMap = new HashMap<String, String>();
		JSONArray scommBRIJsonArr = getSupplierCommercialsBusinessRuleIntakeJSONArray(suppCommResJson);
		for (int i = 0; i < scommBRIJsonArr.length(); i++) {
			if ((commHeadJsonArr = scommBRIJsonArr.getJSONObject(i).optJSONArray(JSON_PROP_COMMHEAD)) == null) {
				logger.warn("No commercial heads found in supplier commercials");
				continue;
			}

			for (int j = 0; j < commHeadJsonArr.length(); j++) {
				commHeadJson = commHeadJsonArr.getJSONObject(j);
				commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME),
						commHeadJson.getString(JSON_PROP_COMMTYPE));
			}
		}

		return commToTypeMap;
	}

	private static Map<String, String> getClientCommercialsAndTheirTypeV2(JSONObject clientCommResJson) {
		JSONArray commHeadJsonArr = null;
		JSONObject commHeadJson = null;
		JSONArray entityDtlsJsonArr = null;
		Map<String, String> commToTypeMap = new HashMap<String, String>();
		JSONArray ccommBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
		for (int i = 0; i < ccommBRIJsonArr.length(); i++) {
			if ((entityDtlsJsonArr = ccommBRIJsonArr.getJSONObject(i).optJSONArray(JSON_PROP_ENTITYDETAILS)) == null) {
				continue;
			}
			for (int j = 0; j < entityDtlsJsonArr.length(); j++) {
				if ((commHeadJsonArr = entityDtlsJsonArr.getJSONObject(j).optJSONArray(JSON_PROP_COMMHEAD)) == null) {
					logger.warn("No commercial heads found in client commercials");
					continue;
				}

				for (int k = 0; k < commHeadJsonArr.length(); k++) {
					commHeadJson = commHeadJsonArr.getJSONObject(k);
					commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME),
							commHeadJson.getString(JSON_PROP_COMMTYPE));
				}
			}
		}

		return commToTypeMap;
	}

	private static Map<String, JSONArray> getSupplierWiseJourneyDetailsFromClientCommercials(
			JSONObject clientCommResJson) {
		JSONArray ccommSuppBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
		Map<String, JSONArray> ccommSuppBRIJsonMap = new HashMap<String, JSONArray>();
		for (int i = 0; i < ccommSuppBRIJsonArr.length(); i++) {
			JSONObject ccommSuppBRIJson = ccommSuppBRIJsonArr.getJSONObject(i);
			String suppID = ccommSuppBRIJson.getJSONObject(JSON_PROP_COMMONELEMS).getString("supplierName");
			JSONArray ccommActDtlsJsonArr = ccommSuppBRIJson.getJSONArray("activityDetails");
			ccommSuppBRIJsonMap.put(suppID, ccommActDtlsJsonArr);
		}

		return ccommSuppBRIJsonMap;
	}

	public static void createSuppReqHdrElem(Element reqHdrElem, String sessionId, String transactId, String userId,String clientMrkt,String clientID) {

		XMLUtils.setValueAtXPath(reqHdrElem, "./com:SessionID",sessionId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:TransactionID",transactId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:UserID",userId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:ClientID",clientID);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:MarketID",clientMrkt);

	}

	static void validateSupplierResponse(String supplierID, Element tourActivityAvailRSElem) {
		JSONArray errorArr = new JSONArray();
		for (Element errorElem : XMLUtils.getElementsAtXPath(tourActivityAvailRSElem, "./ns:Errors")) {

			JSONObject errorObj1 = new JSONObject();
			JSONObject errorObj2 = new JSONObject();

			errorObj2.put("language", XMLUtils.getValueAtXPath(errorElem, "./ns:Error/@Language"));
			errorObj2.put("type", XMLUtils.getValueAtXPath(errorElem, "./ns:Error/@Type"));
			errorObj2.put("shortText", XMLUtils.getValueAtXPath(errorElem, "./ns:Error/@ShortText"));
			errorObj2.put("code", XMLUtils.getValueAtXPath(errorElem, "./ns:Error/@Code"));
			errorObj2.put("recordID", XMLUtils.getValueAtXPath(errorElem, "./ns:Error/@RecordID"));
			errorObj1.put("error", errorObj2);
			errorObj1.put(SUPPLIER_ID, supplierID);

			errorArr.put(errorObj1);
		}

		logger.info("SI status error: "+ errorArr);
	}

}
