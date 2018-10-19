package com.coxandkings.travel.bookingengine.orchestrator.holidays;

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
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class TaxEngine implements HolidayConstants {
	private static final Logger logger = LogManager.getLogger(TaxEngine.class);

	public static void getCompanyTaxes(JSONObject reqJson, JSONObject resJson) {
		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
        JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
        
        JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
        JSONArray dynamicPkgArray = resBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
        OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();
        
        JSONObject taxEngineReqJson = new JSONObject(new JSONTokener(TaxEngineConfig.getRequestJSONShell()));
        JSONObject rootJson = taxEngineReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.taxcalculation.Root");
		
        JSONObject teHdrJson = new JSONObject();
        teHdrJson.put(JSON_PROP_SESSIONID, resHdrJson.getString(JSON_PROP_SESSIONID));
        teHdrJson.put(JSON_PROP_TRANSACTID, resHdrJson.getString(JSON_PROP_TRANSACTID));
        teHdrJson.put(JSON_PROP_USERID, resHdrJson.getString(JSON_PROP_USERID));
        teHdrJson.put(JSON_PROP_OPERATIONNAME, "Reprice");
        rootJson.put(JSON_PROP_HEADER, teHdrJson);
        
        JSONObject gstCalIntJson = new JSONObject();
        
        gstCalIntJson.put(JSON_PROP_COMPANYNAME, orgHierarchy.getCompanyName());
        //gstCalIntJson.put(JSON_PROP_COMPANYNAME, "CnK");
        // Company can have multiple markets associated with it. However, a client associated with that 
        // company can have only one market. Therefore, following assignment uses client market.
        gstCalIntJson.put(JSON_PROP_COMPANYMKT, clientCtxJson.optString(JSON_PROP_CLIENTMARKET, ""));
        gstCalIntJson.put(JSON_PROP_COUNTRY, orgHierarchy.getCompanyCountry());
        gstCalIntJson.put(JSON_PROP_STATE, orgHierarchy.getCompanyState());
        gstCalIntJson.put(JSON_PROP_CLIENTTYPE, clientCtxJson.optString(JSON_PROP_CLIENTTYPE, ""));
        gstCalIntJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
        gstCalIntJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
       // gstCalIntJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
       // gstCalIntJson.put(JSON_PROP_PRODCATEG, "PACKAGES");
       // gstCalIntJson.put(JSON_PROP_PRODCATEGSUBTYPE, "HOLIDAYS");
        gstCalIntJson.put(JSON_PROP_VALIDITY, DATE_FORMAT.format(new Date()));
        
        Map<String, JSONObject> suppHdrJsonMap = new LinkedHashMap<String, JSONObject>();
        Map<String, Map<String, JSONObject>> suppTaxReqJson = new LinkedHashMap<String, Map<String, JSONObject>>();
       createSupplierAndTravelDetails(usrCtx, dynamicPkgArray, reqBodyJson, suppHdrJsonMap, suppTaxReqJson);
        
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
       
        gstCalIntJson.put(JSON_PROP_SUPPPRICINGDTLS, suppPrcDtlsJsonArr);
        rootJson.put(JSON_PROP_GSTCALCINTAKE, gstCalIntJson);
        
        JSONObject taxEngineResJson = null;
        try {
        	logger.trace("Tax engine request: "+taxEngineReqJson);
        	taxEngineResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_TAXENGINE, TaxEngineConfig.getServiceURL(), TaxEngineConfig.getHttpHeaders(), taxEngineReqJson);
        	logger.trace("Tax engine response: "+taxEngineResJson );
        	if (BRMS_STATUS_TYPE_FAILURE.equals(taxEngineResJson.getString(JSON_PROP_TYPE))) {
            	logger.error(String.format("A failure response was received from tax calculation engine: %s", taxEngineResJson.toString()));
            	return;
            }
        	addTaxesToResponseJSONTotal(reqJson, resJson, taxEngineResJson);
        }
        catch (Exception x) {
            logger.warn("An exception occurred when calling tax calculation engine", x);
        }
	}

	private static void addTaxesToResponseJSONTotal(JSONObject reqJson, JSONObject resJson, JSONObject taxEngineResJson) {
		// Retrieve taxes by supplier and destination from tax engine response JSON
				Map<String, Map<String, JSONArray>> taxBySuppDestMap = getTaxesBySupplierAndDestination(taxEngineResJson);
				
				// The order of priced itineraries in service response JSON is not the same as it is in tax engine response JSON. 
				// Therefore, we need to keep track of each supplier/destination occurrence of priced itinerary. This may be an 
				// overkill in context of reprice service from where this class is called but does not hurt. 
				Map<String, Integer> suppDestIndexMap = new HashMap<String, Integer>();
				JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
				 JSONArray dynamicPkgArray = resBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
				 
				for (int i=0; i < dynamicPkgArray.length(); i++) {
					JSONObject dynamicPkgJson = dynamicPkgArray.getJSONObject(i);
					String suppID = dynamicPkgJson.getString(JSON_PROP_SUPPID);
		        	String subTourCode = dynamicPkgJson.getString("subTourCode");
					
					//Put a check for dynamicPkgSync Key
					//String destAirport = getPricedItineraryDestinationAirport(pricedItinJson);
					/*if (destAirport == null || destAirport.isEmpty()) {
						continue;
					}*/
					
					
					Map<String, JSONArray> travelDtlsMap = taxBySuppDestMap.get(suppID);
					//String travelDtlsMapKey = subTourCode;
					String travelDtlsMapKey = String.format("%s|%s|%s","", "", "");
					JSONArray fareDtlsJsonArr = travelDtlsMap.get(travelDtlsMapKey);
					
					String suppDestIndexMapKey = String.format("%s|%s", suppID, travelDtlsMapKey);
					int idx = (suppDestIndexMap.containsKey(suppDestIndexMapKey)) ? (suppDestIndexMap.get(suppDestIndexMapKey) + 1) : 0;
					suppDestIndexMap.put(suppDestIndexMapKey, idx);
					
					if (idx < fareDtlsJsonArr.length()) {
						JSONObject packageTotalJson = dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TOTAL);
			        	JSONObject packageTotalFareJson = packageTotalJson.getJSONObject("totalPackageFare");
			        	BigDecimal pkgtotalFareAmount = packageTotalFareJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
						String totalFareCcy = packageTotalFareJson.getString(JSON_PROP_CCYCODE);
						
						BigDecimal companyTaxTotalAmt = BigDecimal.ZERO;
						JSONObject fareDtlsJson = fareDtlsJsonArr.getJSONObject(idx);
						JSONArray companyTaxJsonArr = new JSONArray();
						JSONArray appliedTaxesJsonArr = fareDtlsJson.optJSONArray(JSON_PROP_APPLIEDTAXDTLS);
						if (appliedTaxesJsonArr == null) {
							logger.warn("No service taxes applied on itinerary");
							continue;
						}
						for (int j=0; j < appliedTaxesJsonArr.length(); j++) {
							JSONObject appliedTaxesJson = appliedTaxesJsonArr.getJSONObject(j);
							JSONObject companyTaxJson = new JSONObject();
							BigDecimal taxAmt = appliedTaxesJson.optBigDecimal(JSON_PROP_TAXVALUE, BigDecimal.ZERO);
							companyTaxJson.put(JSON_PROP_TAXCODE, appliedTaxesJson.optString(JSON_PROP_TAXNAME, ""));
							companyTaxJson.put(JSON_PROP_TAXPCT, appliedTaxesJson.optBigDecimal(JSON_PROP_TAXPERCENTAGE, BigDecimal.ZERO));
							companyTaxJson.put(JSON_PROP_AMOUNT, taxAmt);
							companyTaxJson.put(JSON_PROP_CCYCODE, totalFareCcy);
							companyTaxJson.put(JSON_PROP_HSNCODE, appliedTaxesJson.optString(JSON_PROP_HSNCODE));
							companyTaxJson.put(JSON_PROP_SACCODE, appliedTaxesJson.optString(JSON_PROP_SACCODE));
							companyTaxJsonArr.put(companyTaxJson);
							companyTaxTotalAmt = companyTaxTotalAmt.add(taxAmt);
							pkgtotalFareAmount = pkgtotalFareAmount.add(taxAmt);
						}
						
						// Append the taxes retrieved from tax engine response in itineraryTotalFare element of pricedItinerary JSON
						JSONObject companyTaxesJson = new JSONObject();
						companyTaxesJson.put(JSON_PROP_AMOUNT, companyTaxTotalAmt);
						companyTaxesJson.put(JSON_PROP_CCYCODE, totalFareCcy);
						companyTaxesJson.put(JSON_PROP_COMPANYTAX, companyTaxJsonArr);
						packageTotalFareJson.put(JSON_PROP_COMPANYTAXES, companyTaxesJson);
						packageTotalFareJson.put(JSON_PROP_AMOUNTAFTERTAX, pkgtotalFareAmount);
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

	private static void createSupplierAndTravelDetails(UserContext usrCtx, JSONArray dynamicPkgArray,JSONObject reqBodyJson, Map<String, JSONObject> suppHdrJsonMap, Map<String, Map<String, JSONObject>> suppTaxReqJson) {
		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();
		JSONObject suppHdrJson = new JSONObject();
		JSONObject travelDtlsJson = new JSONObject();
        for (int i=0; i < dynamicPkgArray.length(); i++) {
        	JSONObject dynamicPkgJson = dynamicPkgArray.getJSONObject(i);
        	String suppID = dynamicPkgJson.getString(JSON_PROP_SUPPID);
        	String subTourCode = dynamicPkgJson.getString("subTourCode");
        	/*String brandName = dynamicPkgJson.getString("brandName");
        	String tourCode = dynamicPkgJson.getString("tourCode");
        	String subTourCode = dynamicPkgJson.getString("subTourCode");
        	String uniqueID = brandName+tourCode+subTourCode;*/
        	
        	JSONObject packageTotalJson = dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TOTAL);
        	JSONObject packageTotalFareJson = packageTotalJson.getJSONObject("totalPackageFare");
        	BigDecimal totalBaseFare = packageTotalFareJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX);
        	JSONObject pkgTotalTaxesJson = packageTotalFareJson.optJSONObject(JSON_PROP_TAXES);
        	JSONArray  totalTaxesJsonArr = (pkgTotalTaxesJson != null) ? pkgTotalTaxesJson.getJSONArray(JSON_PROP_TAX) : null;
        	JSONObject pkgTotalRcvblsJson = packageTotalFareJson.optJSONObject(JSON_PROP_RECEIVABLES);
        	JSONArray pkgTotalRcvblsJsonArr = (pkgTotalRcvblsJson != null) ? pkgTotalRcvblsJson.getJSONArray(JSON_PROP_RECEIVABLE) : null;
        	
        	//BigDecimal sellingPrice = packageTotalJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
        	//JSONArray  pkgTotalFeesArr = packageTotalJson.optJSONArray(JSON_PROP_FEES);
        	
        	
        	
        	BigDecimal totalPrice = packageTotalFareJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
        
       
        	//JSONObject travelDtlsJson = reqBodyJson.getJSONArray("dynamicPackage").getJSONObject(i).getJSONObject("globalInfo").getJSONObject("dynamicPkgID").getJSONObject("tourDetails");
        
        	if (suppHdrJsonMap.containsKey(suppID) == false) {
        		
        		suppHdrJson.put(JSON_PROP_SUPPNAME, suppID);
        		//suppHdrJson.put(JSON_PROP_SUPPRATETYPE, "");
        	    //suppHdrJson.put(JSON_PROP_SUPPRATECODE, "");
        		suppHdrJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
        		suppHdrJson.put(JSON_PROP_ISTHIRDPARTY, false);
        		suppHdrJson.put(JSON_PROP_RATEFOR, "");
        		//suppHdrJson.put(JSON_PROP_LOCOFSALE, orgHierarchy.getCompanyState());
        		suppHdrJson.put(JSON_PROP_LOCOFRECIEPT, orgHierarchy.getCompanyState());
        		suppHdrJson.put(JSON_PROP_PTOFSALE, usrCtx.getClientState());
        		suppHdrJsonMap.put(suppID,  suppHdrJson);
        	}
        	
        	Map<String, JSONObject> travelDtlsJsonMap = suppTaxReqJson.get(suppID);
        	if (travelDtlsJsonMap == null) {
        		travelDtlsJsonMap = new LinkedHashMap<String,JSONObject>();
        		suppTaxReqJson.put(suppID, travelDtlsJsonMap);
        	}
        	
        	
        	//TODO : From where the below values would come for reprice?
        	travelDtlsJson.put(JSON_PROP_TRAVELLINGCITY, "");
        	travelDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, "");
        	travelDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, "");
        	travelDtlsJsonMap.put(subTourCode, travelDtlsJson);
    		
        	JSONObject fareDtlsJson = new JSONObject();
        	JSONArray fareBrkpJsonArr = new JSONArray(); 
        	
        	if (totalBaseFare != null) {
        		JSONObject fareBrkpJson = new JSONObject();
        		fareBrkpJson.put(JSON_PROP_NAME, JSON_VAL_BASE);
        		fareBrkpJson.put(JSON_PROP_VALUE, totalBaseFare);
        		fareBrkpJsonArr.put(fareBrkpJson);
        	}
        	
        	if (totalTaxesJsonArr != null) {
        		for (int j=0; j < totalTaxesJsonArr.length(); j++) {
        			JSONObject totalTaxJson = totalTaxesJsonArr.getJSONObject(j);
            		JSONObject fareBrkpJson = new JSONObject();
            		fareBrkpJson.put(JSON_PROP_NAME, totalTaxJson.getString("taxDescription"));
            		fareBrkpJson.put(JSON_PROP_VALUE, totalTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
            		fareBrkpJsonArr.put(fareBrkpJson);
        		}
        	}
        	
        	if (pkgTotalRcvblsJsonArr != null) {
        		for (int j=0; j < pkgTotalRcvblsJsonArr.length(); j++) {
        			JSONObject totalRcvblJson = pkgTotalRcvblsJsonArr.getJSONObject(j);
            		JSONObject fareBrkpJson = new JSONObject();
            		fareBrkpJson.put(JSON_PROP_NAME, totalRcvblJson.getString(JSON_PROP_CODE));
            		fareBrkpJson.put(JSON_PROP_VALUE, totalRcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
            		fareBrkpJsonArr.put(fareBrkpJson);
            		
            		// The input to Tax Engine expects totalFare element to have totals that include  
            		// only BaseFare + Taxes + Fees
            		totalPrice = totalPrice.subtract(totalRcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
        		}
        	}
        	
        	fareDtlsJson.put(JSON_PROP_FAREBREAKUP, fareBrkpJsonArr);
        	fareDtlsJson.put(JSON_PROP_TOTALFARE, totalPrice);
        	//------------------------------------------------------
        	// TODO: Revisit following value assignments for following properties.This should be determined based on 
        	// whether the product is combined with any other product. For example, Package and Flight from home airport
        	// to package start location airport etc.
        	fareDtlsJson.put(JSON_PROP_PROD, "Packages");
        	//fareDtlsJson.put(JSON_PROP_PROD, PROD_CATEG_HOLIDAYS);
        	// TODO: Following assignment of JSON_PROP_SUBTYPE to validate 'HOLIDAYS' is only temporary
        	//fareDtlsJson.put(JSON_PROP_SUBTYPE, PROD_CATEG_SUBTYPE_FLIGHT);
        	fareDtlsJson.put(JSON_PROP_SUBTYPE, PROD_CATEG_HOLIDAYS);
        	// TODO: Hard-coded. This is ghastly! 
        	// How would booking engine know if this reprice is being performed 
        	// in conjunction with some other product's reprice?
        	fareDtlsJson.put(JSON_PROP_ISPKG, false);
        	fareDtlsJson.put(JSON_PROP_ISMARKEDUP, isMarkedUpPrice(packageTotalJson));
        	fareDtlsJson.put(JSON_PROP_ADDCHARGES, BigDecimal.ZERO);
        	fareDtlsJson.put(JSON_PROP_SELLINGPRICE, packageTotalFareJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX));
        	fareDtlsJson.put(JSON_PROP_MEALPLAN, "");
        	//------------------------------------------------------
        	fareDtlsJson.put("starCategory", "");
			fareDtlsJson.put("cabinClass", "");
			fareDtlsJson.put("flightType", "");
			fareDtlsJson.put("amendmentCharges", 0);
			fareDtlsJson.put("cancellationCharges", 0);
			fareDtlsJson.put("supplierRateType", "");
        	fareDtlsJson.put("supplierRateCode", "");
        	
        	//travelDtlsJson.append(JSON_PROP_FAREDETAILS, fareDtlsJson);
        	
        	JSONArray fareDtlsJsonArr = travelDtlsJson.optJSONArray(JSON_PROP_TRAVELDTLS);
        	if (fareDtlsJsonArr == null) {
        		fareDtlsJsonArr = new JSONArray();
        	}
        	
        	fareDtlsJsonArr.put(fareDtlsJson);
        	travelDtlsJson.put(JSON_PROP_FAREDETAILS, fareDtlsJsonArr);
        	
        	
        }
        
        
		
	}
	private static boolean isMarkedUpPrice(JSONObject packageTotalJson) {
		JSONArray clEntityTotalCommsJsonArr = packageTotalJson.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
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
