package com.coxandkings.travel.bookingengine.orchestrator.air;

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

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.TaxEngineConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirportData;

public class TaxEngine implements AirConstants {

	private static final Logger logger = LogManager.getLogger(TaxEngine.class);
	
	@SuppressWarnings("unused")
	public static void getCompanyTaxes(JSONObject reqJson, JSONObject resJson) {
        JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
        JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

        JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
        JSONArray pricedItinJsonArr = resBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
        OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();

        JSONObject taxEngineReqJson = new JSONObject(new JSONTokener(TaxEngineConfig.getRequestJSONShell()));
        JSONObject rootJson = taxEngineReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.taxcalculation.Root");
        
        JSONObject teHdrJson = new JSONObject();
        teHdrJson.put(JSON_PROP_SESSIONID, resHdrJson.getString(JSON_PROP_SESSIONID));
        teHdrJson.put(JSON_PROP_TRANSACTID, resHdrJson.getString(JSON_PROP_TRANSACTID));
        teHdrJson.put(JSON_PROP_USERID, resHdrJson.getString(JSON_PROP_USERID));
        teHdrJson.put(JSON_PROP_OPERATIONNAME, "Search");
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
        gciJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
        gciJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_TRANSPORT);
        gciJson.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_FLIGHT);
        gciJson.put(JSON_PROP_VALIDITY, DATE_FORMAT.format(new Date()));

        Map<String, JSONObject> suppHdrJsonMap = new LinkedHashMap<String, JSONObject>();
        Map<String, Map<String, JSONObject>> suppTaxReqJson = new LinkedHashMap<String, Map<String, JSONObject>>();
        createSupplierAndTravelDetails(usrCtx, pricedItinJsonArr, suppHdrJsonMap, suppTaxReqJson,reqBodyJson,reqHdrJson);
        
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
	
	private static void createSupplierAndTravelDetails(UserContext usrCtx, JSONArray pricedItinJsonArr, Map<String,JSONObject> suppHdrJsonMap,Map<String, Map<String, JSONObject>> suppTaxReqJson,JSONObject reqBodyJson,JSONObject reqHdrJson) {
		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();
        for (int i=0; i < pricedItinJsonArr.length(); i++) {
        	JSONObject pricedItinJson = pricedItinJsonArr.getJSONObject(i);
        	String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
        	JSONObject airItinPriceJson = pricedItinJson.getJSONObject(JSON_PROP_AIRPRICEINFO);
        	JSONObject itinTotalFareJson = airItinPriceJson.getJSONObject(JSON_PROP_ITINTOTALFARE);
        	JSONObject itinTotalBaseFareJson = itinTotalFareJson.getJSONObject(JSON_PROP_BASEFARE);
        	JSONObject itinTotalTaxesJson = itinTotalFareJson.optJSONObject(JSON_PROP_TAXES);
        	JSONArray itinTotalTaxesJsonArr = (itinTotalTaxesJson != null) ? itinTotalTaxesJson.getJSONArray(JSON_PROP_TAX) : null;
        	JSONObject itinTotalFeesJson = itinTotalFareJson.optJSONObject(JSON_PROP_FEES);
        	JSONArray itinTotalFeesJsonArr = (itinTotalFeesJson != null) ? itinTotalFeesJson.getJSONArray(JSON_PROP_FEE) : null;
        	JSONObject itinTotalRcvblsJson = itinTotalFareJson.optJSONObject(JSON_PROP_RECEIVABLES);
        	JSONArray itinTotalRcvblsJsonArr = (itinTotalRcvblsJson != null) ? itinTotalRcvblsJson.getJSONArray(JSON_PROP_RECEIVABLE) : null;
        	String destAirport = getPricedItineraryDestinationAirport(pricedItinJson);
        	
        	if (suppHdrJsonMap.containsKey(suppID) == false) {
        		JSONObject suppHdrJson = new JSONObject();
        		suppHdrJson.put(JSON_PROP_SUPPNAME, suppID);
//        		suppHdrJson.put(JSON_PROP_SUPPRATETYPE, "");
//        		suppHdrJson.put(JSON_PROP_SUPPRATECODE, "");
        		suppHdrJson.put(JSON_PROP_ISTHIRDPARTY, false);
        		suppHdrJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
        		suppHdrJson.put(JSON_PROP_RATEFOR, "");
//        		suppHdrJson.put(JSON_PROP_LOCOFSALE, orgHierarchy.getCompanyState());
        		suppHdrJson.put(JSON_PROP_LOCOFRECIEPT, orgHierarchy.getCompanyState());
        		suppHdrJson.put(JSON_PROP_PTOFSALE, usrCtx.getClientState());
        		suppHdrJsonMap.put(suppID,  suppHdrJson);
        	}
        	
        	Map<String, JSONObject> travelDtlsJsonMap = suppTaxReqJson.get(suppID);
        	if (travelDtlsJsonMap == null) {
        		travelDtlsJsonMap = new LinkedHashMap<String,JSONObject>();
        		suppTaxReqJson.put(suppID, travelDtlsJsonMap);
        	}
        	
        	JSONObject travelDtlsJson = travelDtlsJsonMap.get(destAirport);
        	if (travelDtlsJson == null) {
        		travelDtlsJson = new JSONObject();
        		Map<String,Object> airportInfo = RedisAirportData.getAirportInfo(destAirport);
        		if ( airportInfo != null) {
        			travelDtlsJson.put(JSON_PROP_TRAVELLINGCITY, airportInfo.getOrDefault(RedisAirportData.AIRPORT_CITY, ""));
        			travelDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, airportInfo.getOrDefault(RedisAirportData.AIRPORT_STATE, ""));
        			travelDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, airportInfo.getOrDefault(RedisAirportData.AIRPORT_COUNTRY, ""));
        		}
        		travelDtlsJsonMap.put(destAirport, travelDtlsJson);
        	}
        	
        	JSONObject fareDtlsJson = new JSONObject();
        	BigDecimal itinTotalAmt = itinTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
        	JSONArray fareBrkpJsonArr = new JSONArray(); 
        	
        	if (itinTotalBaseFareJson != null) {
        		JSONObject fareBrkpJson = new JSONObject();
        		fareBrkpJson.put(JSON_PROP_NAME, JSON_VAL_BASE);
        		fareBrkpJson.put(JSON_PROP_VALUE, itinTotalBaseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
        		fareBrkpJsonArr.put(fareBrkpJson);
        	}
        	
        	if (itinTotalTaxesJsonArr != null) {
        		for (int j=0; j < itinTotalTaxesJsonArr.length(); j++) {
        			JSONObject itinTotalTaxJson = itinTotalTaxesJsonArr.getJSONObject(j);
            		JSONObject fareBrkpJson = new JSONObject();
            		fareBrkpJson.put(JSON_PROP_NAME, itinTotalTaxJson.getString(JSON_PROP_TAXCODE));
            		fareBrkpJson.put(JSON_PROP_VALUE, itinTotalTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
            		fareBrkpJsonArr.put(fareBrkpJson);
        		}
        	}
        	
        	if (itinTotalFeesJsonArr != null) {
        		for (int j=0; j < itinTotalFeesJsonArr.length(); j++) {
        			JSONObject itinTotalFeeJson = itinTotalFeesJsonArr.getJSONObject(j);
            		JSONObject fareBrkpJson = new JSONObject();
            		fareBrkpJson.put(JSON_PROP_NAME, itinTotalFeeJson.getString(JSON_PROP_FEECODE));
            		fareBrkpJson.put(JSON_PROP_VALUE, itinTotalFeeJson.getBigDecimal(JSON_PROP_AMOUNT));
            		fareBrkpJsonArr.put(fareBrkpJson);
        		}
        	}
        	
        	if (itinTotalRcvblsJsonArr != null) {
        		for (int j=0; j < itinTotalRcvblsJsonArr.length(); j++) {
        			JSONObject itinTotalRcvblJson = itinTotalRcvblsJsonArr.getJSONObject(j);
            		JSONObject fareBrkpJson = new JSONObject();
            		fareBrkpJson.put(JSON_PROP_NAME, itinTotalRcvblJson.getString(JSON_PROP_CODE));
            		fareBrkpJson.put(JSON_PROP_VALUE, itinTotalRcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
            		fareBrkpJsonArr.put(fareBrkpJson);
            		
            		// The input to Tax Engine expects totalFare element to have totals that include  
            		// only BaseFare + Taxes + Fees
            		itinTotalAmt = itinTotalAmt.subtract(itinTotalRcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
        		}
        	}
        	
        	fareDtlsJson.put(JSON_PROP_FAREBREAKUP, fareBrkpJsonArr);
        	fareDtlsJson.put(JSON_PROP_TOTALFARE, itinTotalAmt);
        	//------------------------------------------------------
        	// TODO: Revisit following value assignments for following properties.This should be determined based on 
        	// whether the product is combined with any other product. For example, Flight and transfer from airport 
        	// to place-of-work etc.
        	fareDtlsJson.put(JSON_PROP_PROD, PROD_CATEG_TRANSPORT);
        	// TODO: Following assignment of JSON_PROP_SUBTYPE to valie 'AIR' is only temporary
        	fareDtlsJson.put(JSON_PROP_SUBTYPE, PROD_CATEG_SUBTYPE_FLIGHT);
        	
        	
        
//        	fareDtlsJson.put(JSON_PROP_SUBTYPE, PRODUCT_AIR);
        	// TODO: Hard-coded. This is ghastly! 
        	// How would booking engine know if this reprice is being performed 
        	// in conjunction with some other product's reprice?
        	fareDtlsJson.put(JSON_PROP_ISPKG, false);
        	fareDtlsJson.put(JSON_PROP_ISMARKEDUP, isMarkedUpPrice(airItinPriceJson));
        	fareDtlsJson.put(JSON_PROP_ADDCHARGES, BigDecimal.ZERO);
        	fareDtlsJson.put(JSON_PROP_SELLINGPRICE, itinTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT));
        	fareDtlsJson.put(JSON_PROP_MEALPLAN, "");
        	
        	//TODO:how to get these below categories?
        	fareDtlsJson.put("starCategory", "");
        	fareDtlsJson.put("cabinClass", getCabinType(pricedItinJson));
        	//This is done so that taxEngine can also be used during search 
        	ServiceConfig opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
    		String opName = opConfig.getOperationName();

        	fareDtlsJson.put("flighType","search".equals(opName)?AirSearchProcessor.deduceTripIndicator(reqHdrJson, reqBodyJson):AirSearchProcessor.deduceOtherOperationsTripIndicator(reqBodyJson));
        	fareDtlsJson.put("amendmentCharges", 0);
        	fareDtlsJson.put("cancellationCharges", 0);
        	fareDtlsJson.put("supplierRateType", "");
        	fareDtlsJson.put("supplierRateCode", "");
        	
        	//------------------------------------------------------
        	
        	JSONArray fareDtlsJsonArr = travelDtlsJson.optJSONArray(JSON_PROP_FAREDETAILS);
        	if (fareDtlsJsonArr == null) {
        		fareDtlsJsonArr = new JSONArray();
        	}
        	
        	fareDtlsJsonArr.put(fareDtlsJson);
        	travelDtlsJson.put(JSON_PROP_FAREDETAILS, fareDtlsJsonArr);
        }
	}
	
	private static String getCabinType(JSONObject pricedItinJson) {

		
		//TODO:  if origindestArr and flightSegArr repeats .. which cabintype should be taken 
		
		String cabinType=null;
		JSONObject airItinJson = pricedItinJson.getJSONObject("airItinerary");
		JSONArray originDestnArr = airItinJson.getJSONArray("originDestinationOptions");
		for(int i =0;i<originDestnArr.length();i++)
		{
			JSONObject originDestnJson = originDestnArr.getJSONObject(i);
			JSONArray flightSegArr = originDestnJson.getJSONArray("flightSegment");
			for(int j =0;j<flightSegArr.length();j++)
			{
				JSONObject flightJson = flightSegArr.getJSONObject(j);
				cabinType = flightJson.getString("cabinType");
			}
		}
		return cabinType;
	}

	private static void addTaxesToResponseItinerariesTotal(JSONObject reqJson, JSONObject resJson, JSONObject taxEngResJson) {
		// Retrieve taxes by supplier and destination from tax engine response JSON
		Map<String, Map<String, JSONArray>> taxBySuppDestMap = getTaxesBySupplierAndDestination(taxEngResJson);
		
		// The order of priced itineraries in service response JSON is not the same as it is in tax engine response JSON. 
		// Therefore, we need to keep track of each supplier/destination occurrence of priced itinerary. This may be an 
		// overkill in context of reprice service from where this class is called but does not hurt. 
		Map<String, Integer> suppDestIndexMap = new HashMap<String, Integer>();
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray pricedItinsJsonArr = resBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
		for (int i=0; i < pricedItinsJsonArr.length(); i++) {
			JSONObject pricedItinJson = pricedItinsJsonArr.getJSONObject(i);
			String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
			String destAirport = getPricedItineraryDestinationAirport(pricedItinJson);
			if (destAirport == null || destAirport.isEmpty()) {
				continue;
			}
			
			Map<String,Object> airportData = RedisAirportData.getAirportInfo(destAirport);
			Map<String, JSONArray> travelDtlsMap = taxBySuppDestMap.get(suppID);
			String travelDtlsMapKey = String.format("%s|%s|%s", airportData.getOrDefault(RedisAirportData.AIRPORT_CITY, ""), airportData.getOrDefault(RedisAirportData.AIRPORT_STATE, ""), airportData.getOrDefault(RedisAirportData.AIRPORT_COUNTRY, ""));
			JSONArray fareDtlsJsonArr = travelDtlsMap.get(travelDtlsMapKey);
			
			String suppDestIndexMapKey = String.format("%s|%s", suppID, travelDtlsMapKey);
			int idx = (suppDestIndexMap.containsKey(suppDestIndexMapKey)) ? (suppDestIndexMap.get(suppDestIndexMapKey) + 1) : 0;
			suppDestIndexMap.put(suppDestIndexMapKey, idx);
			
			if (idx < fareDtlsJsonArr.length()) {
				JSONObject airItinPricInfoJson = pricedItinJson.getJSONObject(JSON_PROP_AIRPRICEINFO);
				JSONObject itinTotalFareJson = airItinPricInfoJson.getJSONObject(JSON_PROP_ITINTOTALFARE);
				BigDecimal itinTotalFareAmt = itinTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
				String itinTotalFareCcy = itinTotalFareJson.getString(JSON_PROP_CCYCODE);
				
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
				itinTotalFareJson.put(JSON_PROP_COMPANYTAXES, companyTaxesJson);
				itinTotalFareJson.put(JSON_PROP_AMOUNT, itinTotalFareAmt);
			}
		}
	}
	
	// Get last airport from last flight segment of last origin-destination-option
	// TODO: It is not yet clear to tax engine folks about what should happen in multi-city
	private static String getPricedItineraryDestinationAirport(JSONObject pricedItinJson) {
		try {
			JSONObject airItinJson = pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY);
	    	JSONArray odoJsonArr = airItinJson.getJSONArray(JSON_PROP_ORIGDESTOPTS);
	    	JSONObject lastOdoJson = odoJsonArr.getJSONObject(odoJsonArr.length() - 1);
	    	JSONArray flSegsJsonArr = lastOdoJson.getJSONArray(JSON_PROP_FLIGHTSEG);
	    	JSONObject lastFlSegJson = flSegsJsonArr.getJSONObject(flSegsJsonArr.length() - 1);
	    	return lastFlSegJson.optString(JSON_PROP_DESTLOC, "");
		}
		catch (Exception x) {
			logger.trace("An error occurred while retrieving desination airport", x);
		}
		
		return "";
		
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
	
	private static boolean isMarkedUpPrice(JSONObject airItinPriceJson) {
		JSONArray clEntityTotalCommsJsonArr = airItinPriceJson.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
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
