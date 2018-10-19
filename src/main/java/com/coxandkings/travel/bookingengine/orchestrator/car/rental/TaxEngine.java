package com.coxandkings.travel.bookingengine.orchestrator.car.rental;

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
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;

public class TaxEngine implements CarConstants {

	private static final Logger logger = LogManager.getLogger(TaxEngine.class);
	
	public static void getCompanyTaxes(JSONObject reqJson, JSONObject resJson) {
        JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

        JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
        JSONArray carRentalJsonArr = resBodyJson.getJSONArray(JSON_PROP_CARRENTALARR);
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
        
        /*gciJson.put(JSON_PROP_COMPANYNAME, "CnK");
        gciJson.put(JSON_PROP_COMPANYMKT, "India");
        gciJson.put(JSON_PROP_COUNTRY, "India");
        gciJson.put(JSON_PROP_STATE, "Maharashtra");
        gciJson.put(JSON_PROP_CLIENTTYPE, "B2B");
        gciJson.put(JSON_PROP_CLIENTCAT, "CG");
        gciJson.put(JSON_PROP_CLIENTSUBCAT, "CS1");
        gciJson.put(JSON_PROP_VALIDITY, "2018-05-10T00:00:00");
*/
        Map<String, JSONObject> suppHdrJsonMap = new LinkedHashMap<String, JSONObject>();
        Map<String, Map<String, JSONObject>> suppTaxReqJson = new LinkedHashMap<String, Map<String, JSONObject>>();
        createSupplierAndTravelDetails(usrCtx, carRentalJsonArr, suppHdrJsonMap, suppTaxReqJson);
        
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
	
	private static void createSupplierAndTravelDetails(UserContext usrCtx, JSONArray carRentalInfoArr, Map<String,JSONObject> suppHdrJsonMap,Map<String, Map<String, JSONObject>> suppTaxReqJson) {
		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();
        for (int i=0; i < carRentalInfoArr.length(); i++) {
        	
        	JSONArray vehicleAvailArr = carRentalInfoArr.getJSONObject(i).getJSONArray(JSON_PROP_VEHICLEAVAIL);
        	for(int j=0; j < vehicleAvailArr.length() ;j++) {
	        	JSONObject vehicleAvailJson = vehicleAvailArr.getJSONObject(j);
	        	
	        	String suppID = vehicleAvailJson.getString(JSON_PROP_SUPPREF);
	        	JSONObject totalPricingInfoJson = vehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO);
	        	JSONObject totalFareJson = totalPricingInfoJson.getJSONObject(JSON_PROP_TOTALFARE);
	        	JSONObject totalBaseFareJson = totalFareJson.getJSONObject(JSON_PROP_BASEFARE);
	        	JSONObject totalTaxesJson = totalFareJson.optJSONObject(JSON_PROP_TAXES);
	        	JSONArray totalTaxesJsonArr = (totalTaxesJson != null) ? totalTaxesJson.getJSONArray(JSON_PROP_TAX) : null;
	        	JSONObject totalFeesJson = totalFareJson.optJSONObject(JSON_PROP_FEES);
	        	JSONArray totalFeesJsonArr = (totalFeesJson != null) ? totalFeesJson.getJSONArray(JSON_PROP_FEE) : null;
	        	JSONObject totalRcvblsJson = totalFareJson.optJSONObject(JSON_PROP_RECEIVABLES);
	        	JSONArray totalRcvblsJsonArr = (totalRcvblsJson != null) ? totalRcvblsJson.getJSONArray(JSON_PROP_RECEIVABLE) : null;
	        	
	        	String cityCode = RentalSearchProcessor.deduceDropOffCity(vehicleAvailJson);
	        	
	        	if (suppHdrJsonMap.containsKey(suppID) == false) {
	        		JSONObject suppHdrJson = new JSONObject();
	        		suppHdrJson.put(JSON_PROP_SUPPNAME, suppID);
	        		suppHdrJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
	        		suppHdrJson.put(JSON_PROP_ISTHIRDPARTY, false);
	        		suppHdrJson.put(JSON_PROP_RATEFOR, "");
	        		//TODO : is Value of LoR Correct ? 
	        		suppHdrJson.put(JSON_PROP_LOCOFRECIEPT, orgHierarchy.getCompanyState());
	        		suppHdrJson.put(JSON_PROP_PTOFSALE, usrCtx.getClientState());
	        		suppHdrJsonMap.put(suppID,  suppHdrJson);
	        	}
	        	
	        	Map<String, JSONObject> travelDtlsJsonMap = suppTaxReqJson.get(suppID);
	        	if (travelDtlsJsonMap == null) {
	        		travelDtlsJsonMap = new LinkedHashMap<String,JSONObject>();
	        		suppTaxReqJson.put(suppID, travelDtlsJsonMap);
	        	}
	        	
	        	JSONObject travelDtlsJson = travelDtlsJsonMap.get(cityCode);
	        	if (travelDtlsJson == null) {
	        		travelDtlsJson = new JSONObject();
	        		Map<String,Object> cityInfo = RedisCityData.getCityCodeInfo(cityCode);
	        		if (cityInfo != null) {
	        			//TODO : Uncommment Later.
	        			travelDtlsJson.put(JSON_PROP_TRAVELLINGCITY, cityInfo.getOrDefault(JSON_PROP_VALUE, ""));
	        			travelDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, cityInfo.getOrDefault(JSON_PROP_STATE, ""));
	        			travelDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, cityInfo.getOrDefault(JSON_PROP_COUNTRY, ""));
	        			
	        		/*	travelDtlsJson.put(JSON_PROP_TRAVELLINGCITY, "Mumbai");
	        			travelDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, "Maharashtra");
	        			travelDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, "India");*/
	        		}
	        		travelDtlsJsonMap.put(cityCode, travelDtlsJson);
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
	        		for (int k=0; k < totalTaxesJsonArr.length(); k++) {
	        			JSONObject totalTaxJson = totalTaxesJsonArr.getJSONObject(k);
	            		JSONObject fareBrkpJson = new JSONObject();
	            		fareBrkpJson.put(JSON_PROP_NAME, totalTaxJson.getString(JSON_PROP_TAXCODE));
	            		fareBrkpJson.put(JSON_PROP_VALUE, totalTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
	            		fareBrkpJsonArr.put(fareBrkpJson);
	        		}
	        	}
	        	
	        	if (totalFeesJsonArr != null) {
	        		for (int k=0; k < totalFeesJsonArr.length(); k++) {
	        			JSONObject totalFeeJson = totalFeesJsonArr.getJSONObject(k);
	            		JSONObject fareBrkpJson = new JSONObject();
	            		fareBrkpJson.put(JSON_PROP_NAME, totalFeeJson.getString(JSON_PROP_FEECODE));
	            		fareBrkpJson.put(JSON_PROP_VALUE, totalFeeJson.getBigDecimal(JSON_PROP_AMOUNT));
	            		fareBrkpJsonArr.put(fareBrkpJson);
	        		}
	        	}
	        	
	        	if (totalRcvblsJsonArr != null) {
	        		for (int k=0; k < totalRcvblsJsonArr.length(); k++) {
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
	        	//------------------------------------------------------
	        	// TODO: Revisit following value assignments for following properties.This should be determined based on 
	        	// whether the product is combined with any other product. For example, Flight and transfer from airport 
	        	// to place-of-work etc.
	        	fareDtlsJson.put(JSON_PROP_PROD, PROD_CATEG_TRANSPORT);
	    		fareDtlsJson.put(JSON_PROP_SUBTYPE, PROD_CATEG_SUBTYPE_RENTAL);
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
	
	private static void addTaxesToResponseItinerariesTotal(JSONObject reqJson, JSONObject resJson, JSONObject taxEngResJson) {
		// Retrieve taxes by supplier and destination from tax engine response JSON
		Map<String, Map<String, JSONArray>> taxBySuppDestMap = getTaxesBySupplierAndDestination(taxEngResJson);
		
		// The order of vehicleAvail in service response JSON is not the same as it is in tax engine response JSON. 
		// Therefore, we need to keep track of each supplier/destination occurrence of vehicleAvail. This may be an 
		// overkill in context of reprice service from where this class is called but does not hurt. 
		Map<String, Integer> suppDestIndexMap = new HashMap<String, Integer>();
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray carRentalJsonArr = resBodyJson.getJSONArray(JSON_PROP_CARRENTALARR);
		for (int i=0; i < carRentalJsonArr.length(); i++) {
			
			JSONArray vehicleAvailArr = carRentalJsonArr.getJSONObject(i).getJSONArray(JSON_PROP_VEHICLEAVAIL);
			for(int j =0; j< vehicleAvailArr.length(); j++) {
				JSONObject vehicleAvailJson = vehicleAvailArr.getJSONObject(j);
				
				String suppID = vehicleAvailJson.getString(JSON_PROP_SUPPREF);
				String cityCode = RentalSearchProcessor.deduceDropOffCity(vehicleAvailJson);
				if (cityCode == null || cityCode.isEmpty()) {
					continue;
				}
				
				Map<String,Object> cityInfo = RedisCityData.getCityCodeInfo(cityCode);
				Map<String, JSONArray> travelDtlsMap = taxBySuppDestMap.get(suppID);
				//TODO: Uncomment later.
				String travelDtlsMapKey = String.format("%s|%s|%s", cityInfo.getOrDefault(JSON_PROP_VALUE, ""), cityInfo.getOrDefault(JSON_PROP_STATE, ""), cityInfo.getOrDefault(JSON_PROP_COUNTRY, ""));
//				String travelDtlsMapKey = String.format("%s|%s|%s", "Mumbai", "Maharashtra", "India");
				JSONArray fareDtlsJsonArr = travelDtlsMap.get(travelDtlsMapKey);
				
				String suppDestIndexMapKey = String.format("%s|%s", suppID, travelDtlsMapKey);
				int idx = (suppDestIndexMap.containsKey(suppDestIndexMapKey)) ? (suppDestIndexMap.get(suppDestIndexMapKey) + 1) : 0;
				suppDestIndexMap.put(suppDestIndexMapKey, idx);
				
				if (idx < fareDtlsJsonArr.length()) {
					JSONObject totalPriceInfoJson = vehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO);
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
