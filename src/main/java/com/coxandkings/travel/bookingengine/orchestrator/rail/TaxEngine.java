package com.coxandkings.travel.bookingengine.orchestrator.rail;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.TaxEngineConfig;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRailData;


public class TaxEngine implements RailConstants {
private static final Logger logger = LogManager.getLogger(TaxEngine.class);
	
	public static void getCompanyTaxes(JSONObject reqJson, JSONObject resJson, String operationName) {
        JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
        JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

        JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
//        JSONArray pricedItinJsonArr = resBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
        OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();

        JSONObject taxEngineReqJson = new JSONObject(new JSONTokener(TaxEngineConfig.getRequestJSONShell()));
        JSONObject rootJson = taxEngineReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.taxcalculation.Root");
        JSONObject headerJsonObject=new JSONObject();
        headerJsonObject.put(JSON_PROP_TRANSACTID, reqHdrJson.getString(JSON_PROP_TRANSACTID));
        headerJsonObject.put(JSON_PROP_SESSIONID, reqHdrJson.getString(JSON_PROP_SESSIONID));
        if (operationName.equals("search")) {
        	headerJsonObject.put(JSON_PROP_OPERATIONNAME, "Search");
		}else if (operationName.equals("reprice")) {
			headerJsonObject.put(JSON_PROP_OPERATIONNAME, "Reprice");
		}
        headerJsonObject.put(JSON_PROP_USERID, reqHdrJson.getString(JSON_PROP_USERID));
        //add header in root element
        rootJson.put(JSON_PROP_HEADER, headerJsonObject);
        
        JSONObject gstCalculationIntakeJsonObject=new JSONObject();
        //TODO:hard coded value. should be dynamic.
        gstCalculationIntakeJsonObject.put(JSON_PROP_COMPANYNAME, "CnK");
        gstCalculationIntakeJsonObject.put(JSON_PROP_COMPANYMKT, "India");
        gstCalculationIntakeJsonObject.put(JSON_PROP_COUNTRY, "India");
        gstCalculationIntakeJsonObject.put(JSON_PROP_STATE, "Maharashtra");
        gstCalculationIntakeJsonObject.put(JSON_PROP_CLIENTTYPE, "B2B");
        gstCalculationIntakeJsonObject.put(JSON_PROP_CLIENTCAT, "CG");
        gstCalculationIntakeJsonObject.put(JSON_PROP_CLIENTSUBCAT, "CS1");
        gstCalculationIntakeJsonObject.put(JSON_PROP_VALIDITY, "2018-05-10T00:00:00");
        
        JSONArray supplierPricingDetailsArray=new JSONArray();
        JSONObject supplierPricingDetailsJson=new JSONObject();
        String supplierName="",travellingState="",travellingCity="";
        JSONArray originDestinationOptionsJsonArray=resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ORIGINDESTOPTS);
		for (Object object : originDestinationOptionsJsonArray) {
			JSONObject jsonObj=(JSONObject)object;
			supplierName=jsonObj.getString(JSON_PROP_SUPPREF);
			Map<String, Object> destinationLocationMap=new HashMap<String,Object>();
			destinationLocationMap=RedisRailData.getStationCodeInfo(jsonObj.getString(JSON_PROP_DESTLOC));
			travellingState=(String) destinationLocationMap.getOrDefault(JSON_PROP_STATE, "");
			travellingCity=(String) destinationLocationMap.getOrDefault(JSON_PROP_CITY, "");
			 supplierPricingDetailsJson.put(JSON_PROP_SUPPLIER_NAME, supplierName);
		        supplierPricingDetailsJson.put(JSON_PROP_CLIENTNAME, "");
		        supplierPricingDetailsJson.put(JSON_PROP_ISTHIRDPARTY, false);
		        supplierPricingDetailsJson.put(JSON_PROP_RATEFOR, "");
		        supplierPricingDetailsArray.put(supplierPricingDetailsJson);
		        JSONArray travelDetailsJsonArray=new JSONArray();
		        JSONObject travelDetailsAndFareDetailsJson=new JSONObject();
		        travelDetailsAndFareDetailsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, "India");
		        travelDetailsAndFareDetailsJson.put(JSON_PROP_TRAVELLINGSTATE, travellingState);
		        travelDetailsAndFareDetailsJson.put(JSON_PROP_TRAVELLINGCITY, travellingCity);
		        
		        JSONArray classAvailInfoJsonArray=jsonObj.getJSONArray(JSON_PROP_CLASSAVAIL);
		        for (Object object2 : classAvailInfoJsonArray) {
		        	JSONObject jsonObj2=(JSONObject)object2;
		        	JSONObject pricingJsonObject=jsonObj2.getJSONObject(JSON_PROP_PRICING);
		        	BigDecimal totalFare=jsonObj2.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_TOTALFARE).getBigDecimal(JSON_PROP_AMOUNT);
		        	BigDecimal totalReceivables=jsonObj2.getJSONObject(JSON_PROP_PRICING).getBigDecimal(JSON_PROP_TOTAL_RECEIVABLES);
		        	//get receivables name and value from response
		        	JSONArray clientEntityTotalCommercialsJsonArray=jsonObj2.getJSONObject(JSON_PROP_PRICING).getJSONArray(JSON_PROP_CLIENT_ENTITY_TOTAL_COMMERCIALS);
		        	JSONArray receivablesValueJsonArray=getReceivablesValueAndName(clientEntityTotalCommercialsJsonArray);
		        	System.out.println("receivablesValueJsonArray: "+receivablesValueJsonArray);
		        	//create fare break up structure
		        	JSONArray fareDetailsJsonArray=getFareDetailsJsonStructure(pricingJsonObject);
		        	System.out.println("fareDetailsJsonArray: "+fareDetailsJsonArray);
		        	
				}
		        travelDetailsJsonArray.put(travelDetailsAndFareDetailsJson);
		}   
	}

	private static JSONArray getFareDetailsJsonStructure(JSONObject pricingJsonObject) {
		BigDecimal baseFareAmount=pricingJsonObject.getJSONObject(JSON_PROP_BASEFARE).getBigDecimal(JSON_PROP_AMOUNT);
		JSONObject fareBreakUpJson=new JSONObject();
		JSONArray fareBreakUpJsonArray=new JSONArray();
		fareBreakUpJson.put(JSON_PROP_AMOUNT, baseFareAmount);
		fareBreakUpJson.put(JSON_PROP_NAME, JSON_PROP_BASE);
		JSONArray feesJsonArray=pricingJsonObject.getJSONObject(JSON_PROP_BASEFARE).getJSONArray(JSON_PROP_FEES);
		for (Object object : feesJsonArray) {
			JSONObject jsonObj=(JSONObject)object;
			JSONObject fareBrkUpjson=new JSONObject();
			fareBrkUpjson.put(JSON_PROP_NAME, jsonObj.get(JSON_PROP_CODECONTEXT));
			fareBrkUpjson.put(JSON_PROP_AMOUNT, jsonObj.get(JSON_PROP_AMOUNT));
			fareBreakUpJsonArray.put(fareBrkUpjson);
		}
		fareBreakUpJsonArray.put(fareBreakUpJson);
		return fareBreakUpJsonArray;
	}

	private static JSONArray getReceivablesValueAndName(JSONArray clientEntityTotalCommercialsJsonArray) {
		JSONArray clientCommercialTotalJsonArray = null;
		for (Object object : clientEntityTotalCommercialsJsonArray) {
			JSONObject jsonObj=(JSONObject)object;
			 clientCommercialTotalJsonArray=new JSONArray();
			JSONArray clientCommercialTotalArray=jsonObj.getJSONArray(JSON_PROP_CLIENTCOMMTOTAL);
			for (Object object2 : clientCommercialTotalArray) {
				JSONObject jsonObj2=(JSONObject)object2;
				BigDecimal commercialAmount=jsonObj2.getBigDecimal(JSON_PROP_COMMERCIAL_AMOUNT);
				String commercialName=jsonObj2.getString(JSON_PROP_COMMERCIAL_NAME);
				JSONObject commercialValuesJsonObject=new JSONObject();
				commercialValuesJsonObject.put(JSON_PROP_NAME, commercialName);
				commercialValuesJsonObject.put(JSON_PROP_VALUE, commercialAmount);
				clientCommercialTotalJsonArray.put(commercialValuesJsonObject);
			}
		}
		return clientCommercialTotalJsonArray;
	}
}
