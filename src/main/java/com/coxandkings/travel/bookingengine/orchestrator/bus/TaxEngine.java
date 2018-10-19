package com.coxandkings.travel.bookingengine.orchestrator.bus;

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

import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;



public class TaxEngine implements BusConstants {
private static final Logger logger = LogManager.getLogger(TaxEngine.class);
	
	@SuppressWarnings("unused")
	public static void getCompanyTaxes(JSONObject reqJson, JSONObject resJson) {

		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
        JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

        JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
        JSONArray availJsonArr = resBodyJson.getJSONArray(JSON_PROP_AVAILABILITY);
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
        
        gciJson.put(JSON_PROP_COMPANYNAME, orgHierarchy.getCompanyName());
        // Company can have multiple markets associated with it. However, a client associated with that 
        // company can have only one market. Therefore, following assignment uses client market.
        gciJson.put(JSON_PROP_COMPANYMKT, clientCtxJson.optString(JSON_PROP_CLIENTMARKET, ""));
        gciJson.put(JSON_PROP_COUNTRY, orgHierarchy.getCompanyCountry());
        gciJson.put(JSON_PROP_STATE, orgHierarchy.getCompanyState());
        gciJson.put(JSON_PROP_CLIENTTYPE, clientCtxJson.optString(JSON_PROP_CLIENTTYPE, ""));
        gciJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
        gciJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
        gciJson.put(JSON_PROP_VALIDITY, DATE_FORMAT.format(new Date()));
        
        Map<String, JSONObject> suppHdrJsonMap = new LinkedHashMap<String, JSONObject>();
        Map<String, Map<String, JSONObject>> suppTaxReqJson = new LinkedHashMap<String, Map<String, JSONObject>>();
        createSupplierAndTravelDetails(usrCtx, availJsonArr, suppHdrJsonMap, suppTaxReqJson);
        
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
        	taxEngineResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_TAXENGINE, TaxEngineConfig.getServiceURL(), TaxEngineConfig.getHttpHeaders(), taxEngineReqJson);
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

	private static void addTaxesToResponseItinerariesTotal(JSONObject reqJson, JSONObject resJson,
			JSONObject taxEngineResJson) {

		Map<String, Map<String, JSONArray>> taxBySuppDestMap = getTaxesBySupplierAndDestination(taxEngineResJson);
		Map<String, Integer> suppDestIndexMap = new HashMap<String, Integer>();
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray availJsonArr = resBodyJson.getJSONArray(JSON_PROP_AVAILABILITY);
		for(int i=0;i<availJsonArr.length();i++)
		{
			JSONArray serviceJsonArr = availJsonArr.getJSONObject(i).getJSONArray(JSON_PROP_SERVICE);
			for(int j=0;j<serviceJsonArr.length();j++)
			{
				JSONObject serviceJson = serviceJsonArr.getJSONObject(j);
				String suppID = serviceJson.get(JSON_PROP_SUPPREF).toString();
				String dest = serviceJson.get(JSON_PROP_DESTINATION).toString();
				if (dest == null || dest.isEmpty()) {
					continue;
				}
				Map<String, Object> cityInfo = RedisCityData.getCityCodeInfo(dest);
				Map<String, JSONArray> travelDtlsMap = taxBySuppDestMap.get(suppID);

				//TODO: has to uncomment
//				String travelDtlsMapKey = String.format("%s|%s|%s", cityInfo.getOrDefault("value", ""), cityInfo.getOrDefault("state", ""), cityInfo.getOrDefault("country", ""));
				
				String travelDtlsMapKey = String.format("%s|%s|%s", "Mumbai", "Maharashtra", "India");

				JSONArray fareDtlsJsonArr = travelDtlsMap.get(travelDtlsMapKey);
				
				String suppDestIndexMapKey = String.format("%s|%s", suppID, travelDtlsMapKey);
				int idx = (suppDestIndexMap.containsKey(suppDestIndexMapKey)) ? (suppDestIndexMap.get(suppDestIndexMapKey) + 1) : 0;
				suppDestIndexMap.put(suppDestIndexMapKey, idx);
				
				JSONArray faresArr = serviceJson.getJSONArray("fares");
				for(int k=0;k<faresArr.length();k++)
				{
					JSONObject fareJson = faresArr.getJSONObject(k);
					if (idx < fareDtlsJsonArr.length()) {
						JSONObject serviceTotalFareJson = fareJson.getJSONObject(JSON_PROP_BUSSERVICETOTALFARE);
						BigDecimal itinTotalFareAmt = serviceTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
						String itinTotalFareCcy = serviceTotalFareJson.getString(JSON_PROP_CCYCODE);
						
						BigDecimal companyTaxTotalAmt = BigDecimal.ZERO;
						JSONObject fareDtlsJson = fareDtlsJsonArr.getJSONObject(idx);
						JSONArray companyTaxJsonArr = new JSONArray();
						JSONArray appliedTaxesJsonArr = fareDtlsJson.optJSONArray(JSON_PROP_APPLIEDTAXDTLS);
						
						if (appliedTaxesJsonArr == null) {
							logger.warn("No service taxes applied on itinerary");
							continue;
						}
						
						for (int m=0; m < appliedTaxesJsonArr.length(); m++) {
							JSONObject appliedTaxesJson = appliedTaxesJsonArr.getJSONObject(m);
							JSONObject companyTaxJson = new JSONObject();
							BigDecimal taxAmt = appliedTaxesJson.optBigDecimal(JSON_PROP_TAXVALUE, BigDecimal.ZERO);
							companyTaxJson.put(JSON_PROP_TAXCODE, appliedTaxesJson.optString(JSON_PROP_TAXNAME, ""));
							companyTaxJson.put(JSON_PROP_TAXPCT, appliedTaxesJson.optBigDecimal(JSON_PROP_TAXPERCENTAGE, BigDecimal.ZERO));
							companyTaxJson.put(JSON_PROP_AMOUNT, taxAmt);
							companyTaxJson.put(JSON_PROP_CCYCODE, itinTotalFareCcy);
							companyTaxJson.put(JSON_PROP_HSNCODE, appliedTaxesJson.optString(JSON_PROP_HSNCODE));
							companyTaxJson.put(JSON_PROP_SACCODE, appliedTaxesJson.optString(JSON_PROP_SACCODE));
							//taxComponent added on finance demand..
							companyTaxJson.put(JSON_PROP_TAXCOMP, appliedTaxesJson.optString(JSON_PROP_TAXCOMP));
							companyTaxJsonArr.put(companyTaxJson);
							companyTaxTotalAmt = companyTaxTotalAmt.add(taxAmt);
							itinTotalFareAmt = itinTotalFareAmt.add(taxAmt);
						}
						
						// Append the taxes retrieved from tax engine response in itineraryTotalFare element of pricedItinerary JSON
						JSONObject companyTaxesJson = new JSONObject();
						companyTaxesJson.put(JSON_PROP_AMOUNT, companyTaxTotalAmt);
						companyTaxesJson.put(JSON_PROP_CCYCODE, itinTotalFareCcy);
						companyTaxesJson.put(JSON_PROP_COMPANYTAX, companyTaxJsonArr);
						serviceTotalFareJson.put(JSON_PROP_COMPANYTAXES, companyTaxesJson);
						serviceTotalFareJson.put(JSON_PROP_AMOUNT, itinTotalFareAmt);
					}
				}
				
				
				
			}
	}
	}

	private static Map<String, Map<String, JSONArray>> getTaxesBySupplierAndDestination(JSONObject taxEngineResJson) {

		Map<String, Map<String, JSONArray>> taxBySuppDestMap = new LinkedHashMap<String, Map<String, JSONArray>>();
		JSONArray suppTaxesJsonArr = taxEngineResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.taxcalculation.Root").getJSONObject("gstCalculationIntake").getJSONArray(JSON_PROP_SUPPPRICINGDTLS);
		for (int i=0; i < suppTaxesJsonArr.length(); i++) {
			JSONObject suppTaxesJson = suppTaxesJsonArr.getJSONObject(i);
			String suppID = suppTaxesJson.getString(JSON_PROP_SUPPNAME);
			Map<String, JSONArray> travelDetailsMap = taxBySuppDestMap.get(suppID);
			if (travelDetailsMap == null) {
				travelDetailsMap = new LinkedHashMap<String, JSONArray>();
				taxBySuppDestMap.put(suppID, travelDetailsMap);
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
		}
		return taxBySuppDestMap;
	}

	private static void createSupplierAndTravelDetails(UserContext usrCtx, JSONArray availJsonArr,
			Map<String, JSONObject> suppHdrJsonMap, Map<String, Map<String, JSONObject>> suppTaxReqJson) {

		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();
        for (int i=0; i < availJsonArr.length(); i++)
        {
        	JSONArray serviceArr = availJsonArr.getJSONObject(i).getJSONArray(JSON_PROP_SERVICE);
        	for(int j=0;j<serviceArr.length();j++)
        	{
        		JSONObject serviceJson = serviceArr.getJSONObject(j);
        		String suppID = serviceJson.getString(JSON_PROP_SUPPREF);
        		JSONArray faresArr = serviceJson.getJSONArray("fares");
        		for(int k=0;k<faresArr.length();k++)
        		{
        			JSONObject fareJSon = faresArr.getJSONObject(k);
        			JSONObject serviceTotalFareJson = fareJSon.getJSONObject(JSON_PROP_BUSSERVICETOTALFARE);
        			JSONObject serviceBaseFareJson = serviceTotalFareJson.getJSONObject(JSON_PROP_BASEFARE);
        			JSONObject recvblsJson = serviceTotalFareJson.getJSONObject(JSON_PROP_RECEIVABLES);
        			JSONArray serviceTotalRcvblsJsonArr = recvblsJson.optJSONArray(JSON_PROP_RECEIVABLE);
        			
        			String dest = serviceJson.get(JSON_PROP_DESTINATION).toString();
        			
        			if (suppHdrJsonMap.containsKey(suppID) == false) {
                		JSONObject suppHdrJson = new JSONObject();
                		suppHdrJson.put(JSON_PROP_SUPPNAME, suppID);
//                		suppHdrJson.put(JSON_PROP_SUPPRATETYPE, "");
//                		suppHdrJson.put(JSON_PROP_SUPPRATECODE, "");
                		suppHdrJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
                		suppHdrJson.put(JSON_PROP_ISTHIRDPARTY, false);
                		suppHdrJson.put(JSON_PROP_RATEFOR, "");
                		suppHdrJson.put(JSON_PROP_LOCOFRECIEPT, orgHierarchy.getCompanyState());
                		suppHdrJson.put(JSON_PROP_PTOFSALE, usrCtx.getClientState());
                		suppHdrJsonMap.put(suppID,  suppHdrJson);
                	}
        			
        			Map<String, JSONObject> travelDtlsJsonMap = suppTaxReqJson.get(suppID);
                	if (travelDtlsJsonMap == null) {
                		travelDtlsJsonMap = new LinkedHashMap<String,JSONObject>();
                		suppTaxReqJson.put(suppID, travelDtlsJsonMap);
                	}
                	
                	JSONObject travelDtlsJson = travelDtlsJsonMap.get(dest);
                	if (travelDtlsJson == null) {
                		travelDtlsJson = new JSONObject();
                		Map<String, Object> cityInfo = RedisCityData.getCityCodeInfo(dest);
                		if ( cityInfo != null) {
                			
                			//TODO: has to uncomment 
                			
//                			travelDtlsJson.put(JSON_PROP_TRAVELLINGCITY, cityInfo.getOrDefault("value", ""));
//                			travelDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, cityInfo.getOrDefault("state", ""));
//                			travelDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, cityInfo.getOrDefault("country", ""));
                			
                			travelDtlsJson.put(JSON_PROP_TRAVELLINGCITY, "Mumbai");
                			travelDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, "Maharashtra");
                			travelDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, "India");
                		}
                		travelDtlsJsonMap.put(dest, travelDtlsJson);
                	}
                	
                	JSONObject fareDtlsJson = new JSONObject();
                	BigDecimal serviceTotalAmt = serviceTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
                	JSONArray fareBrkpJsonArr = new JSONArray(); 
                	
                	if (serviceBaseFareJson != null) {
                		JSONObject fareBrkpJson = new JSONObject();
                		fareBrkpJson.put(JSON_PROP_NAME, JSON_VAL_BASE);
                		fareBrkpJson.put(JSON_PROP_VALUE, serviceBaseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
                		fareBrkpJsonArr.put(fareBrkpJson);
                	}
                	
                	if (serviceTotalRcvblsJsonArr != null) {
                		for (int m=0; m < serviceTotalRcvblsJsonArr.length(); m++) {
                			JSONObject itinTotalRcvblJson = serviceTotalRcvblsJsonArr.getJSONObject(m);
                    		JSONObject fareBrkpJson = new JSONObject();
                    		fareBrkpJson.put(JSON_PROP_NAME, itinTotalRcvblJson.getString(JSON_PROP_CODE));
                    		fareBrkpJson.put(JSON_PROP_VALUE, itinTotalRcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
                    		fareBrkpJsonArr.put(fareBrkpJson);
                    		
                    		// The input to Tax Engine expects totalFare element to have totals that include  
                    		// only BaseFare + Taxes + Fees
                    		serviceTotalAmt = serviceTotalAmt.subtract(itinTotalRcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
                		}
                	}
                	
                	
                	fareDtlsJson.put(JSON_PROP_FAREBREAKUP, fareBrkpJsonArr);
                	fareDtlsJson.put(JSON_PROP_TOTALFARE, serviceTotalAmt);
                	
                	fareDtlsJson.put(JSON_PROP_PROD, PROD_CATEG_TRANSPORT);
                	fareDtlsJson.put(JSON_PROP_SUBTYPE, PRODUCT_BUS);
                	
                	
                	fareDtlsJson.put(JSON_PROP_ISPKG, false);
                	fareDtlsJson.put(JSON_PROP_ISMARKEDUP, isMarkedUpPrice(fareJSon));
                	fareDtlsJson.put(JSON_PROP_ADDCHARGES, BigDecimal.ZERO);
                	fareDtlsJson.put(JSON_PROP_SELLINGPRICE, serviceBaseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
                	fareDtlsJson.put(JSON_PROP_MEALPLAN, "");
                	
                	fareDtlsJson.put("starCategory", "");
                	fareDtlsJson.put("cabinClass", "");
                	fareDtlsJson.put("flighType","");
                	fareDtlsJson.put("amendmentCharges", 0);
                	fareDtlsJson.put("cancellationCharges", 0);
                	fareDtlsJson.put("supplierRateType", "");
                	fareDtlsJson.put("supplierRateCode", "");
                	JSONArray fareDtlsJsonArr = travelDtlsJson.optJSONArray(JSON_PROP_TRAVELDTLS);
                	if (fareDtlsJsonArr == null) {
                		fareDtlsJsonArr = new JSONArray();
                	}
                	
                	fareDtlsJsonArr.put(fareDtlsJson);
                	travelDtlsJson.put(JSON_PROP_FAREDETAILS, fareDtlsJsonArr);
        		}
        		
        	}
        	
        }
		
	}

	private static boolean isMarkedUpPrice(JSONObject fareJSon) {

		JSONArray clEntityTotalCommsJsonArr = fareJSon.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
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
