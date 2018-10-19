package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackableExecutor;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class SupplierSearchProcessor extends TrackableExecutor implements ActivityConstants {

	private static final Logger logger = LogManager.getLogger(SupplierSearchProcessor.class);
	private ActivitySearchListenerResponder mSearchListener;
	private JSONArray mProdSuppsJsonArr;

	SupplierSearchProcessor(ActivitySearchListenerResponder searchListener, ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		mSearchListener = searchListener;
		mProdSupplier = prodSupplier;
		mReqJson = reqJson;
		mReqElem = (Element) reqElem.cloneNode(true);
		mProdSuppsJsonArr = Utils.convertProdSuppliersToCommercialCache(mProdSupplier);
	}

	@Override
	protected void process(ProductSupplier prodSupplier, JSONObject newReqJson, Element reqElem) {

		//OperationConfig opConfig = ActivitiesConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
		ServiceConfig opConfig = ActivitiesConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
		JSONObject reqHdrJson = newReqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBodyJson = newReqJson.getJSONObject(JSON_PROP_REQBODY);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);

		Document ownerDoc = reqElem.getOwnerDocument();
		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, XPATH_REQUESTHEADER_SUPPLIERCREDENTIALSLIST);
		suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, 1));
		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, XPATH_REQUESTHEADER_TRANSACTIONID);
		transactionElem.setTextContent(transactionElem.getTextContent().concat(String.format("-%s", prodSupplier.getSupplierID())));

		//For storing pax count resActivityInfo wise
		HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap = new HashMap<>();

		getPaxTypeCountMap(reqBodyJson, paxTypeCountMap);

		//-----------------------------------------------------------------------------------
		//Search in commercial cache first. If found, send response to async search listener for further processing and then return.

		try {
			ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
			String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT,
					clInfo[clInfo.length - 1].getCommercialsEntityId(), mProdSuppsJsonArr, newReqJson);
			if (commCacheRes != null && (commCacheRes.equals("error")) == false) {
				JSONObject cachedResJson = new JSONObject(new JSONTokener(commCacheRes));
				mSearchListener.receiveSupplierResponse(newReqJson, cachedResJson);
				return;
			}
		}

		catch (Exception x) {
			logger.info("An exception was received while retrieving search results from commercial cache", x);
		}

		Element resElem = null;
		try {

			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), ActivitiesConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), "POST", opConfig.getServiceTimeoutMillis(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}



			Element[] resBodyElem = XMLUtils.getElementsAtXPath(resElem,
					"./sig:ResponseBody/sig1:OTA_TourActivityAvailRSWrapper");
			JSONObject resBodyJson = ActivitySearchProcessor.getSupplierResponseJSON(resBodyElem, reqBodyJson,
					paxTypeCountMap);

			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);


			//Map<String, JSONObject> briActTourActMap = new HashMap<String, JSONObject>();
			//JSONObject resSupplierCommJson = SupplierCommercials.getSupplierCommercials(reqJson, reqElem, resJson, briActTourActMap, usrCtx);

			JSONObject resSupplierComJson = SupplierCommercials.getSupplierCommercialsV2(CommercialsOperation.Search, newReqJson, resJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierComJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", resSupplierComJson.toString()));

				//Since not in Cars Code, code has been commented out (to display empty json if error.)
				//return getEmptyResponse(reqHdrJson).toString();
			}

			JSONObject resClientComJson = ClientCommercials.getClientCommercials(newReqJson, resJson, resSupplierComJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientComJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", resClientComJson.toString()));

				//Since not in Cars Code, code has been commented out (to display empty json if error.)
				//return getEmptyResponse(reqHdrJson).toString();
			}

			//ActivitySearchProcessor.calculatePrices(reqJson, resJson, resSupplierCommJson, resClientCommJson, briActTourActMap, usrCtx, false);

			ActivitySearchProcessor.calculatePricesV2(newReqJson, resJson, resSupplierComJson, resClientComJson, false);

			//resBodyJson.put(JSON_PROP_TOURACTIVITYINFO, resBodyJson.getJSONArray(JSON_PROP_ACTIVITYINFO).getJSONObject(0).getJSONArray(JSON_PROP_TOURACTIVITYINFO));
			//CompanyOffers.getCompanyOffers(reqJson, resJson, OffersConfig.Type.COMPANY_SEARCH_TIME);

			try {
				ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
				CommercialCacheProcessor.putInCache("ACTIVITIES", clInfo[clInfo.length - 1].getCommercialsEntityId(), resJson, newReqJson);
			}

			catch (Exception x) {
				logger.info("An exception was received while pushing search results in commercial cache", x);
			}
			mSearchListener.receiveSupplierResponse(newReqJson, resJson);

		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	private void getPaxTypeCountMap(JSONObject reqBodyJson,
			HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap) {

		int adultCount = 0;
		int childCount = 0;

		if (reqBodyJson.has("participantInfo")) {

			JSONObject participantInfo = reqBodyJson.getJSONObject("participantInfo");

			if (participantInfo.has("adultCount")) {
				adultCount = participantInfo.getInt("adultCount");
			}

			if (participantInfo.optJSONArray("childAges") != null) {
				childCount = participantInfo.getJSONArray("childAges").length();
			}

			if (adultCount == 0 && childCount == 0) {
				adultCount = 1;
				childCount = 0;
			}
		}

		else {

			adultCount = 1;
			childCount = 0;

		}

		HashMap<String, BigDecimal> paxCountMap = new HashMap<>();

		//index of paxTypeCountMap is kept 0 for now as we are assuming only one ActivityInfo
		paxCountMap.put("ADT", BigDecimal.valueOf(adultCount));
		paxCountMap.put("CHD", BigDecimal.valueOf(childCount));
		paxCountMap.put("INF", BigDecimal.ZERO);
		paxTypeCountMap.put(0, paxCountMap);

	}

}
