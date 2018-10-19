package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.TaxEngineConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.orchestrator.car.SubType;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;

public class TaxEngine implements ActivityConstants {

	private static final Logger logger = LogManager.getLogger(TaxEngine.class);

	@SuppressWarnings("unused")
	public static void getCompanyTaxes(JSONObject reqJson, JSONObject resJson) {


		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

		JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);

		JSONArray activityInfoReq = reqBodyJson.getJSONArray("activityInfo");
		JSONArray tourActivityInfoArr = resBodyJson.getJSONArray("activityInfo").getJSONObject(0)
				.getJSONArray("tourActivityInfo");
		if (activityInfoReq.length() > 1) {
			for (int activityInfoReqCount = 1; activityInfoReqCount < activityInfoReq.length(); activityInfoReqCount++) {
				tourActivityInfoArr.put(resBodyJson.getJSONArray("activityInfo").getJSONObject(activityInfoReqCount).getJSONArray("tourActivityInfo").getJSONObject(0));
			}
		}
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();

		JSONObject taxEngineReqJson = new JSONObject(new JSONTokener(TaxEngineConfig.getRequestJSONShell()));
		JSONObject rootJson = taxEngineReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert")
				.getJSONObject("object").getJSONObject("cnk.taxcalculation.Root");

		JSONObject teHdrJson = new JSONObject();
		teHdrJson.put(JSON_PROP_SESSIONID, resHdrJson.getString(JSON_PROP_SESSIONID));
		teHdrJson.put(JSON_PROP_TRANSACTID, resHdrJson.getString(JSON_PROP_TRANSACTID));
		teHdrJson.put(JSON_PROP_USERID, resHdrJson.getString(JSON_PROP_USERID));
		teHdrJson.put(JSON_PROP_OPERATIONNAME, "Search");
		rootJson.put(JSON_PROP_HEADER, teHdrJson);

		JSONObject gciJson = new JSONObject();

		gciJson.put(JSON_PROP_COMPANYNAME, "CnK");

		// gciJson.put(JSON_PROP_COMPANYNAME, orgHierarchy.getCompanyName());
		// Company can have multiple markets associated with it. However, a client
		// associated with that
		// company can have only one market. Therefore, following assignment uses client
		// market.
		gciJson.put(JSON_PROP_COMPANYMKT, clientCtxJson.optString(JSON_PROP_CLIENTMARKET, ""));
		gciJson.put(JSON_PROP_COUNTRY, orgHierarchy.getCompanyCountry());
		gciJson.put(JSON_PROP_STATE, orgHierarchy.getCompanyState());
		gciJson.put(JSON_PROP_CLIENTTYPE, clientCtxJson.optString(JSON_PROP_CLIENTTYPE, ""));
		gciJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
		gciJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
		gciJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
		gciJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_ACTIVITY);
		gciJson.put(JSON_PROP_PRODCATEGSUBTYPE, "SIGHTSEEING PACKAGES");
		gciJson.put(JSON_PROP_VALIDITY, DATE_FORMAT.format(new Date()));

		JSONArray suppPrcDtlsJsonArr = new JSONArray();
		for (Object tourActivityInfoJson : tourActivityInfoArr) {
			suppPrcDtlsJsonArr.put(getSupplierPricingDetails(usrCtx, (JSONObject) tourActivityInfoJson));

		}

		gciJson.put(JSON_PROP_SUPPPRICINGDTLS, suppPrcDtlsJsonArr);

		rootJson.put(JSON_PROP_GSTCALCINTAKE, gciJson);


		JSONObject taxEngineResJson = null;
		try {
			taxEngineResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_TAXENGINE,
					TaxEngineConfig.getServiceURL(), TaxEngineConfig.getHttpHeaders(), taxEngineReqJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(taxEngineResJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from tax calculation engine: %s",
						taxEngineResJson.toString()));

				return;
			}


			addTaxesToResponseActivitiesTotal(reqJson, resJson, taxEngineResJson);

		} catch (Exception x) {
			logger.warn("An exception occurred when calling tax calculation engine", x);
		}

		// }
	}

	// Changes by Mohit 30-05-18-----------

	public static void getCompanyTaxesV2(JSONObject reqJson, JSONObject resJson) {

		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

		JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);

		JSONArray resActivityInfoArr = resBodyJson.getJSONArray("activityInfo");

		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);

		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();
		String subTypeStr = reqBodyJson.optString(JSON_PROP_PRODCATEGSUBTYPE);
		SubType subType = SubType.forString(subTypeStr);

		JSONObject taxEngineReqJson = new JSONObject(new JSONTokener(TaxEngineConfig.getRequestJSONShell()));
		JSONObject rootJson = taxEngineReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert")
				.getJSONObject("object").getJSONObject("cnk.taxcalculation.Root");

		JSONObject teHdrJson = new JSONObject();
		teHdrJson.put(JSON_PROP_SESSIONID, resHdrJson.getString(JSON_PROP_SESSIONID));
		teHdrJson.put(JSON_PROP_TRANSACTID, resHdrJson.getString(JSON_PROP_TRANSACTID));
		teHdrJson.put(JSON_PROP_USERID, resHdrJson.getString(JSON_PROP_USERID));
		teHdrJson.put(JSON_PROP_OPERATIONNAME, CommercialsOperation.Search);
		rootJson.put(JSON_PROP_HEADER, teHdrJson);

		JSONObject gciJson = new JSONObject();

		
		gciJson.put(JSON_PROP_COMPANYNAME, orgHierarchy.getCompanyName()); 
//		Company can have multiple markets associated with it. However, a client associated with that company can have only one market. Therefore, following assignment uses client market. 
		gciJson.put(JSON_PROP_COMPANYMKT,clientCtxJson.optString(JSON_PROP_CLIENTMARKET, ""));
		gciJson.put(JSON_PROP_COUNTRY, orgHierarchy.getCompanyCountry());
		gciJson.put(JSON_PROP_STATE, orgHierarchy.getCompanyState());
		gciJson.put(JSON_PROP_CLIENTTYPE,clientCtxJson.optString(JSON_PROP_CLIENTTYPE, ""));
		gciJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
		gciJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
		gciJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
	    	gciJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_ACTIVITY);
	    	gciJson.put(JSON_PROP_PRODCATEGSUBTYPE, "SIGHTSEEING PACKAGES");
		gciJson.put(JSON_PROP_VALIDITY, DATE_FORMAT.format(new Date()));

		
		Map<String, JSONObject> suppHdrJsonMap = new LinkedHashMap<String, JSONObject>();
		Map<String, Map<String, JSONObject>> suppTaxReqJson = new LinkedHashMap<String, Map<String, JSONObject>>();
		createSupplierAndTravelDetails(subType, usrCtx, resActivityInfoArr, suppHdrJsonMap, suppTaxReqJson);

		JSONArray suppPrcDtlsJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> suppHdrEntries = suppHdrJsonMap.entrySet().iterator();
		while (suppHdrEntries.hasNext()) {
			Entry<String, JSONObject> suppHdrEntry = suppHdrEntries.next();
			String suppID = suppHdrEntry.getKey();
			JSONObject suppHdrJson = suppHdrEntry.getValue();

			Map<String, JSONObject> destTravelDtlsJsonMap = suppTaxReqJson.get(suppID);
			if (destTravelDtlsJsonMap != null) {
				Collection<JSONObject> travelDtlsJsonColl = destTravelDtlsJsonMap.values();
				JSONArray travelDtlsJsonArr = new JSONArray();
				for (JSONObject travelDtlsJson : travelDtlsJsonColl) {
					travelDtlsJsonArr.put(travelDtlsJson);
				}

				suppHdrJson.put(JSON_PROP_TRAVELDTLS, travelDtlsJsonArr);
			}

			suppPrcDtlsJsonArr.put(suppHdrJson);
		}
		gciJson.put(JSON_PROP_SUPPPRICINGDTLS, suppPrcDtlsJsonArr);
		rootJson.put(JSON_PROP_GSTCALCINTAKE, gciJson);

		JSONObject taxEngineResJson = null;
		try {
			taxEngineResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_TAXENGINE,
					TaxEngineConfig.getServiceURL(), TaxEngineConfig.getHttpHeaders(), taxEngineReqJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(taxEngineResJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from tax calculation engine: %s",
						taxEngineResJson.toString()));
				return;
			}
			addTaxesToResponseActivitiesTotalV2(reqJson, resJson, taxEngineResJson);
		} catch (Exception x) {
			logger.warn("An exception occurred when calling tax calculation engine", x);
		}

	}

	private static void createSupplierAndTravelDetails(SubType subType, UserContext usrCtx,
			JSONArray resActivityInfoArr, Map<String, JSONObject> suppHdrJsonMap,
			Map<String, Map<String, JSONObject>> suppTaxReqJson) {

		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();

		for (int i = 0; i < resActivityInfoArr.length(); i++) {
			JSONObject resActivityInfoJson = resActivityInfoArr.getJSONObject(i);
			JSONArray tourActivityInfoArr = resActivityInfoJson.getJSONArray("tourActivityInfo");

			for (int j = 0; j < tourActivityInfoArr.length(); j++) {

				JSONObject tourActivityInfoJson = tourActivityInfoArr.getJSONObject(j);

				String suppID = tourActivityInfoJson.getString("supplierID");
				JSONObject activityTotalPricingInfoJson = tourActivityInfoJson
						.getJSONObject("activityTotalPricingInfo");
				JSONObject totalFareJson = activityTotalPricingInfoJson.getJSONObject("activitySummaryPrice");
				JSONObject totalBaseFareJson = totalFareJson.getJSONObject(JSON_PROP_BASEFARE);
				JSONObject totalTaxesJson = totalFareJson.optJSONObject(JSON_PROP_TAXES);
				JSONArray totalTaxesJsonArr = (totalTaxesJson != null) ? totalTaxesJson.getJSONArray(JSON_PROP_TAX)
						: null;
				JSONObject totalFeesJson = totalFareJson.optJSONObject(JSON_PROP_FEES);
				JSONArray totalFeesJsonArr = (totalFeesJson != null) ? totalFeesJson.getJSONArray(JSON_PROP_FEE) : null;
				JSONObject totalRcvblsJson = totalFareJson.optJSONObject(JSON_PROP_RECEIVABLES);
				JSONArray totalRcvblsJsonArr = (totalRcvblsJson != null)
						? totalRcvblsJson.getJSONArray(JSON_PROP_RECEIVABLE)
						: null;
				// JSONObject totalsplEquipJson =
				// totalFareJson.optJSONObject(JSON_PROP_SPLEQUIPS);
				// JSONArray splEquipJsonArr = (totalsplEquipJson != null) ?
				// totalsplEquipJson.getJSONArray(JSON_PROP_SPLEQUIP) : null;

				String cityName = tourActivityInfoJson.getJSONObject("location").getJSONObject("region").getString("regionName");

				if (suppHdrJsonMap.containsKey(suppID) == false) {
					JSONObject suppHdrJson = new JSONObject();
					suppHdrJson.put(JSON_PROP_SUPPNAME, suppID);
					suppHdrJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
					suppHdrJson.put(JSON_PROP_ISTHIRDPARTY, false);
					suppHdrJson.put(JSON_PROP_RATEFOR, "");
					
					suppHdrJson.put(JSON_PROP_LOCOFRECIEPT, orgHierarchy.getCompanyState());
					suppHdrJson.put(JSON_PROP_PTOFSALE, usrCtx.getClientState());
					suppHdrJsonMap.put(suppID, suppHdrJson);
				}

				Map<String, JSONObject> travelDtlsJsonMap = suppTaxReqJson.get(suppID);
				if (travelDtlsJsonMap == null) {
					travelDtlsJsonMap = new LinkedHashMap<String, JSONObject>();
					suppTaxReqJson.put(suppID, travelDtlsJsonMap);
				}

				JSONObject travelDtlsJson = travelDtlsJsonMap.get(cityName);
				if (travelDtlsJson == null) {
					travelDtlsJson = new JSONObject();
					Map<String, Object> cityInfo = RedisCityData.getCityInfo(cityName);
					if (cityInfo != null) {
						travelDtlsJson.put(JSON_PROP_TRAVELLINGCITY, cityName);
						travelDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, cityInfo.getOrDefault(JSON_PROP_STATE, ""));
						travelDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, cityInfo.getOrDefault(JSON_PROP_COUNTRY, ""));
					}
					travelDtlsJsonMap.put(cityName, travelDtlsJson);
				}

				JSONObject fareDtlsJson = new JSONObject();
				BigDecimal itinTotalAmt = totalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
				JSONArray fareBrkpJsonArr = new JSONArray();

				if (totalBaseFareJson != null) {
					JSONObject fareBrkpJson = new JSONObject();
					fareBrkpJson.put(JSON_PROP_NAME, JSON_VAL_BASE);
					fareBrkpJson.put(JSON_PROP_VALUE, totalBaseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
					fareBrkpJsonArr.put(fareBrkpJson);
				}

				if (totalTaxesJsonArr != null) {
					for (int k = 0; k < totalTaxesJsonArr.length(); k++) {
						JSONObject totalTaxJson = totalTaxesJsonArr.getJSONObject(k);
						JSONObject fareBrkpJson = new JSONObject();
						fareBrkpJson.put(JSON_PROP_NAME, totalTaxJson.getString(JSON_PROP_TAXCODE));
						fareBrkpJson.put(JSON_PROP_VALUE, totalTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
						fareBrkpJsonArr.put(fareBrkpJson);
					}
				}

				if (totalFeesJsonArr != null) {
					for (int k = 0; k < totalFeesJsonArr.length(); k++) {
						JSONObject totalFeeJson = totalFeesJsonArr.getJSONObject(k);
						JSONObject fareBrkpJson = new JSONObject();
						fareBrkpJson.put(JSON_PROP_NAME, totalFeeJson.getString("feeCode"));
						fareBrkpJson.put(JSON_PROP_VALUE, totalFeeJson.getBigDecimal(JSON_PROP_AMOUNT));
						fareBrkpJsonArr.put(fareBrkpJson);
					}
				}

				// if (splEquipJsonArr != null) {
				// for (int k=0; k < splEquipJsonArr.length(); k++) {
				// JSONObject splEquipJson = splEquipJsonArr.getJSONObject(k);
				// JSONObject fareBrkpJson = new JSONObject();
				// fareBrkpJson.put(JSON_PROP_NAME,
				// splEquipJson.getString(JSON_PROP_EQUIPTYPE));
				// fareBrkpJson.put(JSON_PROP_VALUE,
				// splEquipJson.getBigDecimal(JSON_PROP_AMOUNT));
				// fareBrkpJsonArr.put(fareBrkpJson);
				// }
				// }

				if (totalRcvblsJsonArr != null) {
					for (int k = 0; k < totalRcvblsJsonArr.length(); k++) {
						JSONObject totalRcvblJson = totalRcvblsJsonArr.getJSONObject(k);
						JSONObject fareBrkpJson = new JSONObject();
						fareBrkpJson.put(JSON_PROP_NAME, totalRcvblJson.getString(JSON_PROP_CODE));
						fareBrkpJson.put(JSON_PROP_VALUE, totalRcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
						fareBrkpJsonArr.put(fareBrkpJson);

						// The input to Tax Engine expects totalFare element to have totals that include
						// only BaseFare + Taxes + Fees
						itinTotalAmt = itinTotalAmt.subtract(totalRcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
					}
				}

				fareDtlsJson.put(JSON_PROP_FAREBRKUP, fareBrkpJsonArr);
				fareDtlsJson.put(JSON_PROP_TOTALFARE, itinTotalAmt);

				// ------------------------------------------------------
				// TODO: Revisit following value assignments for following properties.This
				// should be determined based on
				// whether the product is combined with any other product. For example, Flight
				// and transfer from airport
				// to place-of-work etc.
				fareDtlsJson.put(JSON_PROP_PROD, "Activity");
				fareDtlsJson.put(JSON_PROP_SUBTYPE, "Sightseeingpackages");
				// TODO: Hard-coded. This is ghastly!
				// How would booking engine know if this reprice is being performed
				// in conjunction with some other product's reprice?
				fareDtlsJson.put(JSON_PROP_ISPKG, false);
				fareDtlsJson.put(JSON_PROP_ISMARKEDUP, isMarkedUpPrice(activityTotalPricingInfoJson));
				fareDtlsJson.put(JSON_PROP_ADDCHARGES, BigDecimal.ZERO);
				fareDtlsJson.put(JSON_PROP_SUPPRATETYPE, "");
				fareDtlsJson.put(JSON_PROP_SUPPRATECODE, "");

				fareDtlsJson.put(JSON_PROP_SELLINGPRICE, totalFareJson.getBigDecimal(JSON_PROP_AMOUNT));
				fareDtlsJson.put(JSON_PROP_MEALPLAN, "");

				fareDtlsJson.put("starCategory", "");
				fareDtlsJson.put("cabinClass", "");
				fareDtlsJson.put("flighType", "");
				fareDtlsJson.put("amendmentCharges", BigDecimal.ZERO);
				fareDtlsJson.put("cancellationCharges", BigDecimal.ZERO);
				// ------------------------------------------------------

				JSONArray fareDtlsJsonArr = travelDtlsJson.optJSONArray(JSON_PROP_FAREDETAILS);
				if (fareDtlsJsonArr == null) {
					fareDtlsJsonArr = new JSONArray();
				}

				fareDtlsJsonArr.put(fareDtlsJson);
				travelDtlsJson.put(JSON_PROP_FAREDETAILS, fareDtlsJsonArr);
				
			}

		}

	}
	
	
	private static void addTaxesToResponseActivitiesTotalV2(JSONObject reqJson, JSONObject resJson, JSONObject taxEngResJson) {
		// Retrieve taxes by supplier and destination from tax engine response JSON
		Map<String, Map<String, JSONArray>> taxBySuppDestMap = getTaxesBySupplierAndDestination(taxEngResJson);
		
		// The order of priced itineraries in service response JSON is not the same as it is in tax engine response JSON. 
		// Therefore, we need to keep track of each supplier/destination occurrence of priced itinerary. This may be an 
		// overkill in context of reprice service from where this class is called but does not hurt. 
		Map<String, Integer> suppDestIndexMap = new HashMap<String, Integer>();
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray activityInfoArr = resBodyJson.getJSONArray("activityInfo");
		
		for(int i=0; i< activityInfoArr.length(); i++) {
			JSONObject activityInfo = activityInfoArr.getJSONObject(i);
			JSONArray tourActivityInfoArr = activityInfo.getJSONArray("tourActivityInfo");
			for(int j = 0; j< tourActivityInfoArr.length(); j++) {
				JSONObject tourActivityInfo = tourActivityInfoArr.getJSONObject(j);
				String suppID = tourActivityInfo.getString("supplierID");
				String cityName = tourActivityInfo.getJSONObject("location").getJSONObject("region").getString("regionName");
				
				if (cityName == null || cityName.isEmpty()) {
					continue;
				}
				
				Map<String,Object> cityInfo = RedisCityData.getCityInfo(cityName);
				Map<String, JSONArray> travelDtlsMap = taxBySuppDestMap.get(suppID);
				//TODO: Uncomment later.
				String travelDtlsMapKey = String.format("%s|%s|%s", cityName, cityInfo.getOrDefault(JSON_PROP_STATE, ""), cityInfo.getOrDefault(JSON_PROP_COUNTRY, ""));
//				String travelDtlsMapKey = String.format("%s|%s|%s", "Mumbai", "Maharashtra", "India");
				JSONArray fareDtlsJsonArr = travelDtlsMap.get(travelDtlsMapKey);
				
				String suppDestIndexMapKey = String.format("%s|%s", suppID, travelDtlsMapKey);
				int idx = (suppDestIndexMap.containsKey(suppDestIndexMapKey)) ? (suppDestIndexMap.get(suppDestIndexMapKey) + 1) : 0;
				suppDestIndexMap.put(suppDestIndexMapKey, idx);
				
				if (idx < fareDtlsJsonArr.length()) {
					JSONObject totalPriceInfoJson = tourActivityInfo.getJSONObject("activityTotalPricingInfo");
					JSONObject totalFareJson = totalPriceInfoJson.getJSONObject("activitySummaryPrice");
					BigDecimal totalFareAmt = totalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
					String totalFareCcy = totalFareJson.getString(JSON_PROP_CCYCODE);
					
					BigDecimal companyTaxTotalAmt = BigDecimal.ZERO;
					JSONObject fareDtlsJson = fareDtlsJsonArr.getJSONObject(idx);
					JSONArray companyTaxJsonArr = new JSONArray();
					JSONArray appliedTaxesJsonArr = fareDtlsJson.optJSONArray(JSON_PROP_APPLIEDTAXDTLS);
					if (appliedTaxesJsonArr == null) {
						logger.warn("No service taxes applied on car-selfDrive");
						continue;
					}
					
					for (int k=0; k < appliedTaxesJsonArr.length(); k++) {
						JSONObject appliedTaxesJson = appliedTaxesJsonArr.getJSONObject(k);
						JSONObject companyTaxJson = new JSONObject();
						BigDecimal taxAmt = appliedTaxesJson.optBigDecimal(JSON_PROP_TAXVALUE, BigDecimal.ZERO);
						companyTaxJson.put(JSON_PROP_TAXCODE, appliedTaxesJson.optString(JSON_PROP_TAXNAME, ""));
						companyTaxJson.put(JSON_PROP_TAXPCT, appliedTaxesJson.optBigDecimal(JSON_PROP_TAXPERCENTAGE, BigDecimal.ZERO));
						companyTaxJson.put(JSON_PROP_AMOUNT, taxAmt);
						//TaxComponent added on finance demand
						companyTaxJson.put(JSON_PROP_TAXCOMP, appliedTaxesJson.optString(JSON_PROP_TAXCOMP));
						companyTaxJson.put(JSON_PROP_CCYCODE, totalFareCcy);
						companyTaxJson.put(JSON_PROP_HSNCODE, appliedTaxesJson.optString(JSON_PROP_HSNCODE));
						companyTaxJson.put(JSON_PROP_SACCODE, appliedTaxesJson.optString(JSON_PROP_SACCODE));
						companyTaxJsonArr.put(companyTaxJson);
						companyTaxTotalAmt = companyTaxTotalAmt.add(taxAmt);
						totalFareAmt = totalFareAmt.add(taxAmt);
					}
					
					// Append the taxes retrieved from tax engine response in itineraryTotalFare element of pricedItinerary JSON
					JSONObject companyTaxesJson = new JSONObject();
					companyTaxesJson.put(JSON_PROP_AMOUNT, companyTaxTotalAmt);
					companyTaxesJson.put(JSON_PROP_CCYCODE, totalFareCcy);
					companyTaxesJson.put(JSON_PROP_COMPANYTAX, companyTaxJsonArr);
					totalFareJson.put(JSON_PROP_COMPANYTAXES, companyTaxesJson);
					totalFareJson.put(JSON_PROP_AMOUNT, totalFareAmt);
				}
			}
		}
		
				
	}
	
	
	private static Map<String, Map<String, JSONArray>> getTaxesBySupplierAndDestination(JSONObject taxEngResJson) {
		Map<String, Map<String, JSONArray>> taxBySuppDestMap = new LinkedHashMap<String, Map<String, JSONArray>>();
		JSONArray suppTaxesJsonArr = taxEngResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.taxcalculation.Root").getJSONObject("gstCalculationIntake").getJSONArray(JSON_PROP_SUPPPRICINGDTLS);
		for (int i=0; i < suppTaxesJsonArr.length(); i++) {
			JSONObject suppTaxesJson = suppTaxesJsonArr.getJSONObject(i);
			String suppID = suppTaxesJson.getString(JSON_PROP_SUPPNAME);
			Map<String, JSONArray> travelDetailsMap = taxBySuppDestMap.get(suppID);
			if (travelDetailsMap == null) {
				travelDetailsMap = new LinkedHashMap<String, JSONArray>();
				taxBySuppDestMap.put(suppID, travelDetailsMap);
			}
			
			JSONArray travelDtlsJsonArr = suppTaxesJson.optJSONArray(JSON_PROP_TRAVELDTLS);
			if (travelDtlsJsonArr == null) {
				continue;
			}
			
			for (int j = 0; j < travelDtlsJsonArr.length(); j++) {
				JSONObject travelDtlsJson = travelDtlsJsonArr.getJSONObject(j);
				String travelDetailsKey = String.format("%s|%s|%s", travelDtlsJson.getString(JSON_PROP_TRAVELLINGCITY), travelDtlsJson.getString(JSON_PROP_TRAVELLINGSTATE), travelDtlsJson.getString(JSON_PROP_TRAVELLINGCOUNTRY));
				JSONArray fareDtlsJsonArr = travelDtlsJson.getJSONArray(JSON_PROP_FAREDETAILS);
				travelDetailsMap.put(travelDetailsKey, fareDtlsJsonArr);
			}
		}
		
		return taxBySuppDestMap;
	}

	// Changes by Mohit 30-05-18-----------

	private static void addTaxesToResponseActivitiesTotal(JSONObject reqJson, JSONObject resJson,
			JSONObject taxEngResJson) {
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray activityInfoJsonArray = resBodyJson.getJSONArray("activityInfo");
		JSONArray suppPrcngDtlJsonArr = taxEngResJson.getJSONObject("result").getJSONObject("execution-results")
				.getJSONArray("results").getJSONObject(0).getJSONObject("value")
				.getJSONObject("cnk.taxcalculation.Root").getJSONObject("gstCalculationIntake")
				.getJSONArray(JSON_PROP_SUPPPRICINGDTLS);

		for (int activityInfoResCount = 0; activityInfoResCount < activityInfoJsonArray
				.length(); activityInfoResCount++) {
			JSONObject tourActivityInfoJsonObj = resBodyJson.getJSONArray("activityInfo")
					.getJSONObject(activityInfoResCount).getJSONArray("tourActivityInfo").getJSONObject(0);
			JSONObject suppPrcngDtlJson = suppPrcngDtlJsonArr.optJSONObject(activityInfoResCount);
			if (suppPrcngDtlJson == null) {
				logger.warn(String.format("No service taxes applied on rooms in accomodationInfo at index %s",
						activityInfoResCount));
				continue;
			}

			JSONArray trvlDtlsJsonArr = suppPrcngDtlJson.optJSONArray(JSON_PROP_TRAVELDTLS);
			if (trvlDtlsJsonArr == null || trvlDtlsJsonArr.length() <= 0) {
				logger.warn(String.format("No service taxes applied on rooms in accomodationInfo at index %s",
						activityInfoResCount));
				continue;
			}
			JSONArray fareDtlsJsonArr = trvlDtlsJsonArr.getJSONObject(0).getJSONArray(JSON_PROP_FAREDETAILS);
			if (fareDtlsJsonArr == null || fareDtlsJsonArr.length() <= 0) {
				logger.warn(String.format("No service taxes applied on rooms in accomodationInfo at index %s",
						activityInfoResCount));
				continue;
			}
			JSONObject fareDtlsJson = fareDtlsJsonArr.optJSONObject(0);
			if (fareDtlsJson == null) {
				logger.warn(String.format("No service taxes applied on room at index %s,%s", activityInfoResCount, 0));
				continue;
			}
			JSONArray appliedTaxesJsonArr = fareDtlsJson.optJSONArray(JSON_PROP_APPLIEDTAXDTLS);
			if (appliedTaxesJsonArr == null) {
				logger.warn(String.format("No service taxes applied on room at index %s,%s", activityInfoResCount, 0));
				continue;
			}

			JSONArray totalPriceInfojson = tourActivityInfoJsonObj.getJSONArray(JSON_PROP_PRICING);

			BigDecimal totalPrice = null;
			String activitiesCcy = null;
			for (int count = 0; count < totalPriceInfojson.length(); count++) {
				JSONObject pricingDetails = totalPriceInfojson.getJSONObject(count);
				if (JSON_PROP_SUMMARY.equals(pricingDetails.getString(JSON_PROP_PARTICIPANT_CATEGORY))) {
					totalPrice = pricingDetails.getBigDecimal("totalPrice");
					activitiesCcy = pricingDetails.getString("currencyCode");
				}
			}

			BigDecimal companyTaxTotalAmt = BigDecimal.ZERO;
			JSONArray companyTaxJsonArr = new JSONArray();
			for (int k = 0; k < appliedTaxesJsonArr.length(); k++) {
				JSONObject appliedTaxesJson = appliedTaxesJsonArr.getJSONObject(k);
				JSONObject companyTaxJson = new JSONObject();
				BigDecimal taxAmt = appliedTaxesJson.optBigDecimal(JSON_PROP_TAXVALUE, BigDecimal.ZERO);
				companyTaxJson.put(JSON_PROP_TAXCODE, appliedTaxesJson.optString(JSON_PROP_TAXNAME, ""));
				companyTaxJson.put(JSON_PROP_TAXPCT,
						appliedTaxesJson.optBigDecimal(JSON_PROP_TAXPERCENTAGE, BigDecimal.ZERO));
				companyTaxJson.put(JSON_PROP_AMOUNT, taxAmt);
				companyTaxJson.put(JSON_PROP_CCYCODE, activitiesCcy);
				companyTaxJson.put(JSON_PROP_HSNCODE, appliedTaxesJson.optString(JSON_PROP_HSNCODE));
				companyTaxJson.put(JSON_PROP_SACCODE, appliedTaxesJson.optString(JSON_PROP_SACCODE));
				companyTaxJsonArr.put(companyTaxJson);
				companyTaxTotalAmt = companyTaxTotalAmt.add(taxAmt);
				totalPrice = totalPrice.add(taxAmt);
			}

			JSONObject companyTaxesJson = new JSONObject();
			companyTaxesJson.put(JSON_PROP_AMOUNT, companyTaxTotalAmt);
			companyTaxesJson.put(JSON_PROP_CCYCODE, activitiesCcy);
			companyTaxesJson.put(JSON_PROP_COMPANYTAX, companyTaxJsonArr);
			JSONObject taxesJson = new JSONObject();
			taxesJson.put(JSON_PROP_COMPANYTAXES, companyTaxesJson);
			taxesJson.put(JSON_PROP_AMOUNT, totalPrice);

			totalPriceInfojson.put(taxesJson);

			for (int count = 0; count < totalPriceInfojson.length(); count++) {
				JSONObject pricingDetails = totalPriceInfojson.getJSONObject(count);
				if (pricingDetails.has(JSON_PROP_PARTICIPANT_CATEGORY)
						&& JSON_PROP_SUMMARY.equals(pricingDetails.getString(JSON_PROP_PARTICIPANT_CATEGORY))) {
					pricingDetails.put("totalPrice", totalPrice);
				}
			}
		}

	}

	private static JSONObject getSupplierPricingDetails(UserContext usrCtx, JSONObject tourActivityInfoJson) {

		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();

		JSONObject suppPrcDtlsJsonObj = new JSONObject();

		suppPrcDtlsJsonObj.put(JSON_PROP_SUPPLIER_NAME, tourActivityInfoJson.getString("supplierID"));
		suppPrcDtlsJsonObj.put(JSON_PROP_SUPPRATETYPE, "");
		suppPrcDtlsJsonObj.put(JSON_PROP_SUPPRATECODE, "");
		suppPrcDtlsJsonObj.put(JSON_PROP_ISTHIRDPARTY, false);
		suppPrcDtlsJsonObj.put(JSON_PROP_RATEFOR, "Activities"); // TODO: Ask for this field, is this same as sub type

		JSONArray travelDetailsArr = new JSONArray();

		travelDetailsArr.put(getTravelDetails(tourActivityInfoJson));
		suppPrcDtlsJsonObj.put(JSON_PROP_TRAVELDTLS, travelDetailsArr);

		suppPrcDtlsJsonObj.put(JSON_PROP_LOCOFSALE, orgHierarchy.getCompanyState());
		suppPrcDtlsJsonObj.put(JSON_PROP_PTOFSALE, usrCtx.getClientState());

		return suppPrcDtlsJsonObj;
	}

	private static JSONObject getTravelDetails(JSONObject tourActivityInfoJson) {
		JSONObject travelDetailsObj = new JSONObject();

		travelDetailsObj.put(JSON_PROP_TRAVELLINGCOUNTRY, "");
		travelDetailsObj.put(JSON_PROP_TRAVELLINGSTATE, "");
		travelDetailsObj.put(JSON_PROP_TRAVELLINGCITY, "");

		JSONArray fareDetailsArr = new JSONArray();

		JSONArray pricingArr = tourActivityInfoJson.getJSONArray("pricing");

		for (int pricingArrCount = 0; pricingArrCount < pricingArr.length(); pricingArrCount++) {
			JSONObject pricingJson = pricingArr.getJSONObject(pricingArrCount);
			// For now we are applying tax on Adult or Child and NOT ON SUMMARY
			if (pricingJson.getString("participantCategory").equalsIgnoreCase("Summary")) {
				JSONObject fareDetailsJson = new JSONObject();
				fareDetailsJson.put(JSON_PROP_TOTAL_FARE, pricingJson.getBigDecimal("totalPrice"));

				JSONArray fareBreakUpArr = new JSONArray();
				JSONObject fareBreakUpJson = new JSONObject();
				// TODO: we dont get Service Charge, However, for tax the rule is still not
				// configured to accept TotalFare
				fareBreakUpJson.put(JSON_PROP_NAME, "ServiceCharge");
				fareBreakUpJson.put(JSON_PROP_VALUE, pricingJson.getBigDecimal("totalPrice"));
				fareBreakUpArr.put(fareBreakUpJson);

				fareDetailsJson.put(JSON_PROP_FAREBRKUP, fareBreakUpArr);
				fareDetailsJson.put(JSON_PROP_ISMARKEDUP, false);
				fareDetailsJson.put(JSON_PROP_ADDCHARGES, 0);
				fareDetailsJson.put(JSON_PROP_SELLINGPRICE, pricingJson.getBigDecimal("totalPrice"));
				fareDetailsJson.put(JSON_PROP_PROD, PROD_CATEG_ACTIVITY);
				// We have SubType generally as "Tours & Sightseeing" , but this takes in as
				// "SIGHTSEEING PACKAGES"
				fareDetailsJson.put("subType", "SIGHTSEEING PACKAGES");
				fareDetailsJson.put(JSON_PROP_ISPKG, false);

				fareDetailsArr.put(fareDetailsJson);
			}

		}
		travelDetailsObj.put(JSON_PROP_FAREDETAILS, fareDetailsArr);

		return travelDetailsObj;
	}

	private static boolean isMarkedUpPrice(JSONObject activityTotalPricingInfoJson) {
		JSONArray clEntityTotalCommsJsonArr = activityTotalPricingInfoJson
				.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
		if (clEntityTotalCommsJsonArr == null || clEntityTotalCommsJsonArr.length() == 0) {
			return false;
		}

		for (int i = 0; i < clEntityTotalCommsJsonArr.length(); i++) {
			JSONObject clEntityTotalCommsJson = clEntityTotalCommsJsonArr.getJSONObject(i);
			JSONArray clCommsTotalJsonArr = clEntityTotalCommsJson.optJSONArray(JSON_PROP_CLIENTCOMMTOTAL);
			if (clCommsTotalJsonArr == null || clCommsTotalJsonArr.length() == 0) {
				continue;
			}

			for (int j = 0; j < clCommsTotalJsonArr.length(); j++) {
				JSONObject clCommsTotalJson = clCommsTotalJsonArr.getJSONObject(j);
				if (JSON_VAL_COMMTYPEMARKUP.equals(clCommsTotalJson.getString(JSON_PROP_COMMNAME))) {
					return true;
				}
			}
		}

		return false;
	}

}
