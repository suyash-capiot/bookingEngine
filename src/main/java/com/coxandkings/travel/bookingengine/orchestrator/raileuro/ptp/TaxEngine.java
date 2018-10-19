package com.coxandkings.travel.bookingengine.orchestrator.raileuro.ptp;

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

import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.config.TaxEngineConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.RailEuropeConstants;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;

public class TaxEngine implements RailEuropeConstants {

	private static final Logger logger = LogManager.getLogger(TaxEngine.class);

	public static void getCompanyTaxes(JSONObject reqJson, JSONObject resJson) {
		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

		JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray wwRailJsonArr = resBodyJson.getJSONArray(JSON_PROP_WWRAILARR);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);	
		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();

		JSONObject taxEngineReqJson = new JSONObject(new JSONTokener(TaxEngineConfig.getRequestJSONShell()));
		JSONObject rootJson = taxEngineReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.taxcalculation.Root");

		JSONObject teHdrJson = new JSONObject();
		teHdrJson.put(JSON_PROP_SESSIONID, resHdrJson.getString(JSON_PROP_SESSIONID));
		teHdrJson.put(JSON_PROP_TRANSACTID, resHdrJson.getString(JSON_PROP_TRANSACTID));
		teHdrJson.put(JSON_PROP_USERID, resHdrJson.getString(JSON_PROP_USERID));
		teHdrJson.put(JSON_PROP_OPERATIONNAME, CommercialsOperation.Search);
		rootJson.put(JSON_PROP_HEADER, teHdrJson);

		JSONObject gciJson = new JSONObject();

//		gciJson.put(JSON_PROP_COMPANYNAME, orgHierarchy.getCompanyName());
//		// Company can have multiple markets associated with it. However, a client associated with that 
//		// company can have only one market. Therefore, following assignment uses client market.
//		gciJson.put(JSON_PROP_COMPANYMKT, clientCtxJson.optString(JSON_PROP_CLIENTMARKET, ""));
//		gciJson.put(JSON_PROP_COUNTRY, orgHierarchy.getCompanyCountry());
//		gciJson.put(JSON_PROP_STATE, orgHierarchy.getCompanyState());
//		gciJson.put(JSON_PROP_CLIENTTYPE, clientCtxJson.optString(JSON_PROP_CLIENTTYPE, ""));
//		gciJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
//		gciJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
//		gciJson.put(JSON_PROP_VALIDITY, DATE_FORMAT.format(new Date()));

		gciJson.put(JSON_PROP_COMPANYNAME, "CnK");
        gciJson.put(JSON_PROP_COMPANYMKT, "India");
        gciJson.put(JSON_PROP_COUNTRY, "India");
        gciJson.put(JSON_PROP_STATE, "Maharashtra");
        gciJson.put(JSON_PROP_CLIENTTYPE, "B2B");
        gciJson.put(JSON_PROP_CLIENTCAT, "CG");
        gciJson.put(JSON_PROP_CLIENTSUBCAT, "CS1");
        gciJson.put(JSON_PROP_VALIDITY, "2018-05-10T00:00:00");
		 
//		createSupplierAndTravelDetails(usrCtx, wwRailJsonArr, suppHdrJsonMap, suppTaxReqJson);
		JSONArray travelDetailsArr = new JSONArray();
		Map<String, JSONObject> travelDtlsJsonMap = new LinkedHashMap<String, JSONObject>();
		
		for (int i=0; i < wwRailJsonArr.length(); i++) {

			JSONArray solutionArr = wwRailJsonArr.getJSONObject(i).getJSONArray(JSON_PROP_SOLUTIONS);
			for(int j=0; j < solutionArr.length() ;j++) {
				JSONObject solutionJson = solutionArr.getJSONObject(j);
				String cityCode = solutionJson.getString("toCityCode");
				JSONArray packageDetailsArr = solutionJson.getJSONArray(JSON_PROP_PACKAGEDETAILS);
				JSONObject travelDtlsJson = travelDtlsJsonMap.get(cityCode);
				if (travelDtlsJson == null) {
					travelDtlsJson = new JSONObject();
					Map<String,Object> cityInfo = RedisCityData.getCityCodeInfo(cityCode);
					if (cityInfo != null) {
						//TODO : Uncommment Later.
						travelDtlsJson.put(JSON_PROP_TRAVELLINGCITY, cityCode);
	        			travelDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, cityInfo.getOrDefault(JSON_PROP_STATE, ""));
	        			travelDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, cityInfo.getOrDefault(JSON_PROP_COUNTRY, ""));

						/*	travelDtlsJson.put(JSON_PROP_TRAVELLINGCITY, "Mumbai");
        			travelDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, "Maharashtra");
        			travelDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, "India");*/
					}
					travelDetailsArr.put(travelDtlsJson);
					travelDtlsJsonMap.put(cityCode, travelDtlsJson);
				}
				for(int k = 0; k<packageDetailsArr.length();k++) {
					
					JSONObject packageDetailsJson = packageDetailsArr.getJSONObject(k);
					JSONObject totalPricingInfoJson = packageDetailsJson.getJSONObject(JSON_PROP_TOTALPRICEINFO);
					JSONObject totalFareJson = totalPricingInfoJson.getJSONObject(JSON_PROP_TOTALFARE);
					JSONObject totalBaseFareJson = totalFareJson.getJSONObject(JSON_PROP_BASEFARE);
					JSONObject totalRcvblsJson = totalFareJson.optJSONObject(JSON_PROP_RECEIVABLES);
					JSONArray totalRcvblsJsonArr = (totalRcvblsJson != null) ? totalRcvblsJson.getJSONArray(JSON_PROP_RECEIVABLE) : null;

					JSONObject fareDtlsJson = new JSONObject();
					BigDecimal itinTotalAmt = totalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
					JSONArray fareBrkpJsonArr = new JSONArray(); 

					if (totalBaseFareJson != null) {
						JSONObject fareBrkpJson = new JSONObject();
						fareBrkpJson.put(JSON_PROP_NAME, JSON_VAL_BASE);
						fareBrkpJson.put(JSON_PROP_VALUE, totalBaseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
						fareBrkpJsonArr.put(fareBrkpJson);
					}

					if (totalRcvblsJsonArr != null) {
						for (int l=0; l < totalRcvblsJsonArr.length(); l++) {
							JSONObject totalRcvblJson = totalRcvblsJsonArr.getJSONObject(l);
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
					//------------------------------------------------------
					// TODO: Revisit following value assignments for following properties.This should be determined based on 
					// whether the product is combined with any other product. For example, Flight and transfer from airport 
					// to place-of-work etc.
					fareDtlsJson.put(JSON_PROP_PROD, PROD_CATEG_TRANSPORT);
//					fareDtlsJson.put(JSON_PROP_SUBTYPE, PROD_CATEG_SUBTYPE_RAILEUROPE);
					fareDtlsJson.put(JSON_PROP_SUBTYPE, "Rail");
					// TODO: Hard-coded. This is ghastly! 
					// How would booking engine know if this reprice is being performed 
					// in conjunction with some other product's reprice?
					fareDtlsJson.put(JSON_PROP_ISPKG, false);
					fareDtlsJson.put(JSON_PROP_ISMARKEDUP, isMarkedUpPrice(totalPricingInfoJson));
					fareDtlsJson.put(JSON_PROP_ADDCHARGES, BigDecimal.ZERO);
					fareDtlsJson.put(JSON_PROP_SUPPRATETYPE, "");
					fareDtlsJson.put(JSON_PROP_SUPPRATECODE, "");

					fareDtlsJson.put(JSON_PROP_SELLINGPRICE, totalFareJson.getBigDecimal(JSON_PROP_AMOUNT));
					fareDtlsJson.put(JSON_PROP_MEALPLAN, "");

					fareDtlsJson.put("starCategory", "");
					fareDtlsJson.put("cabinClass", "");
					fareDtlsJson.put("flighType","");
					fareDtlsJson.put("amendmentCharges", BigDecimal.ZERO);
					fareDtlsJson.put("cancellationCharges", BigDecimal.ZERO);

					//------------------------------------------------------

					JSONArray fareDtlsJsonArr = travelDtlsJson.optJSONArray(JSON_PROP_FAREDETAILS);
					if (fareDtlsJsonArr == null) {
						fareDtlsJsonArr = new JSONArray();
					}

					fareDtlsJsonArr.put(fareDtlsJson);
					travelDtlsJson.put(JSON_PROP_FAREDETAILS, fareDtlsJsonArr);
				}
			}
		}
		String suppID = "RAILEUROPE";
		JSONObject suppHdrJson = new JSONObject();
		suppHdrJson.put(JSON_PROP_SUPPNAME, suppID);
		suppHdrJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
		suppHdrJson.put(JSON_PROP_ISTHIRDPARTY, false);
		suppHdrJson.put(JSON_PROP_RATEFOR, "");
		//TODO : is Value of LoR Correct ? 
		suppHdrJson.put(JSON_PROP_LOCOFSALE, orgHierarchy.getCompanyState());
		suppHdrJson.put(JSON_PROP_PTOFSALE, usrCtx.getClientState());
		
		suppHdrJson.put(JSON_PROP_TRAVELDTLS, travelDetailsArr);
		
		JSONArray suppPrcDtlsJsonArr = new JSONArray();
        suppPrcDtlsJsonArr.put(suppHdrJson);
		
		gciJson.put(JSON_PROP_SUPPPRICINGDTLS, suppPrcDtlsJsonArr);
		rootJson.put(JSON_PROP_GSTCALCINTAKE, gciJson);

		JSONObject taxEngineResJson = null;
		try {
//			System.out.println(taxEngineReqJson);
			taxEngineResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_TAXENGINE, TaxEngineConfig.getServiceURL(), TaxEngineConfig.getHttpHeaders(), taxEngineReqJson);
//			System.out.println(taxEngineResJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(taxEngineResJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from tax calculation engine: %s", taxEngineResJson.toString()));
				return;
			}
			addTaxesToResponseItinerariesTotal(reqJson, resJson, taxEngineResJson);
		}
		catch (Exception x) {
			logger.warn("An exception occurred when calling tax calculation engine", x);
		}
	}

//	private static void createSupplierAndTravelDetails(UserContext usrCtx, JSONArray wwRailJsonArr, Map<String,JSONObject> suppHdrJsonMap,Map<String, Map<String, JSONObject>> suppTaxReqJson) {
//	
//	}

	private static void addTaxesToResponseItinerariesTotal(JSONObject reqJson, JSONObject resJson, JSONObject taxEngResJson) {
		// Retrieve taxes by supplier and destination from tax engine response JSON
		Map<String, Map<String, JSONArray>> taxBySuppDestMap = getTaxesBySupplierAndDestination(taxEngResJson);

		// The order of vehicleAvail in service response JSON is not the same as it is in tax engine response JSON. 
		// Therefore, we need to keep track of each supplier/destination occurrence of vehicleAvail. This may be an 
		// overkill in context of reprice service from where this class is called but does not hurt. 
		Map<String, Integer> suppDestIndexMap = new HashMap<String, Integer>();
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray wwRailJsonArr = resBodyJson.getJSONArray(JSON_PROP_WWRAILARR);
		for (int i=0; i < wwRailJsonArr.length(); i++) {

			JSONArray solutionArr = wwRailJsonArr.getJSONObject(i).getJSONArray(JSON_PROP_SOLUTIONS);
//			System.out.println(solutionArr);
			for(int j =0; j< solutionArr.length(); j++) {
				JSONObject solutionJson = solutionArr.getJSONObject(j);

				String suppID = solutionJson.getString(JSON_PROP_SUPPREF);
				String cityCode = solutionJson.getString("toCityCode");
				//TODO: hardcode city name   
//				String cityCode = "Mumbai";
				JSONArray packageDetailsArr = solutionJson.getJSONArray(JSON_PROP_PACKAGEDETAILS);
				for(int z = 0; z<packageDetailsArr.length();z++)

				{
					JSONObject packageDetailsJson = packageDetailsArr.getJSONObject(z);
					Map<String,Object> cityInfo = RedisCityData.getCityCodeInfo(cityCode);
//					System.out.println(cityInfo.toString());
					Map<String, JSONArray> travelDtlsMap = taxBySuppDestMap.get(suppID);
					//TODO: Uncomment later.
					String travelDtlsMapKey = String.format("%s|%s|%s", cityInfo.getOrDefault("cityCode", ""), cityInfo.getOrDefault(JSON_PROP_STATE, ""), cityInfo.getOrDefault(JSON_PROP_COUNTRY, ""));
					//				String travelDtlsMapKey = String.format("%s|%s|%s", "Mumbai", "Maharashtra", "India");
					JSONArray fareDtlsJsonArr = travelDtlsMap.get(travelDtlsMapKey);

					String suppDestIndexMapKey = String.format("%s|%s", suppID, travelDtlsMapKey);
					int idx = (suppDestIndexMap.containsKey(suppDestIndexMapKey)) ? (suppDestIndexMap.get(suppDestIndexMapKey) + 1) : 0;
					suppDestIndexMap.put(suppDestIndexMapKey, idx);

					if (idx < fareDtlsJsonArr.length()) {
						JSONObject totalPriceInfoJson = packageDetailsJson.getJSONObject(JSON_PROP_TOTALPRICEINFO);
						JSONObject totalFareJson = totalPriceInfoJson.getJSONObject(JSON_PROP_TOTALFARE);
						BigDecimal totalFareAmt = totalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
						String totalFareCcy = totalFareJson.getString(JSON_PROP_CCYCODE);
						

						BigDecimal companyTaxTotalAmt = BigDecimal.ZERO;
						JSONObject fareDtlsJson = fareDtlsJsonArr.getJSONObject(idx);
						JSONArray companyTaxJsonArr = new JSONArray();
						JSONArray appliedTaxesJsonArr = fareDtlsJson.optJSONArray(JSON_PROP_APPLIEDTAXDTLS);
						if (appliedTaxesJsonArr == null) {
							logger.warn("No service taxes applied on car-rental");
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
							//						companyTaxJson.put(JSON_PROP_TAXCOMP, appliedTaxesJson.optString(JSON_PROP_TAXCOMP));
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

	private static boolean isMarkedUpPrice(JSONObject carPriceInfoJson) {
		JSONArray clEntityTotalCommsJsonArr = carPriceInfoJson.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
		if (clEntityTotalCommsJsonArr == null || clEntityTotalCommsJsonArr.length() == 0) {
			return false;
		}

		for (int i=0; i < clEntityTotalCommsJsonArr.length(); i++) {
			JSONObject clEntityTotalCommsJson = clEntityTotalCommsJsonArr.getJSONObject(i);
			JSONArray clCommsTotalJsonArr = clEntityTotalCommsJson.optJSONArray(JSON_PROP_CLIENTCOMMTOTAL);
			if (clCommsTotalJsonArr == null || clCommsTotalJsonArr.length() == 0) {
				continue;
			}

			for (int j=0; j < clCommsTotalJsonArr.length(); j++) {
				JSONObject clCommsTotalJson = clCommsTotalJsonArr.getJSONObject(j);
				if (JSON_VAL_COMMTYPEMARKUP.equals(clCommsTotalJson.getString(JSON_PROP_COMMNAME))) {
					return true;
				}
			}
		}

		return false;
	}
}
