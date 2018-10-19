package com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.TaxEngineConfig;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.RailEuropeConstants;
//import com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass.TaxEngine;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class TaxEngine implements RailEuropeConstants{
	
	
	private static final Logger logger = LogManager.getLogger(TaxEngine.class);
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");

	
	public static void getCompanyTaxes(JSONObject reqJson, JSONObject resJson)
	{
		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);

        JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
        
        JSONArray passProductsArr = resBodyJson.getJSONArray(JSON_PROP_PASSPROD);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
        OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();
//        String subType = reqJson.getJSONObject(JSON_PROP_REQBODY).optString(JSON_PROP_PRODCATEGSUBTYPE);
        
        JSONObject taxEngineReqJson = new JSONObject(new JSONTokener(TaxEngineConfig.getRequestJSONShell()));
        JSONObject rootJson = taxEngineReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.taxcalculation.Root");
        
        JSONObject teHdrJson = new JSONObject();
        teHdrJson.put(JSON_PROP_SESSIONID, resHdrJson.getString(JSON_PROP_SESSIONID));
        teHdrJson.put(JSON_PROP_TRANSACTID, resHdrJson.getString(JSON_PROP_TRANSACTID));
        teHdrJson.put(JSON_PROP_USERID, resHdrJson.getString(JSON_PROP_USERID));
        teHdrJson.put(JSON_PROP_OPERATIONNAME, "Search");
        rootJson.put(JSON_PROP_HEADER, teHdrJson);
        
        JSONObject gciJson = new JSONObject();
        
//        gciJson.put("companyName", orgHierarchy.getCompanyName());
        gciJson.put(JSON_PROP_COMPANYNAME, "CnK");
//        gciJson.put("companyMarket", clientCtxJson.optString("clientMarket", ""));
        gciJson.put(JSON_PROP_COMPANYMKT, "India");
        gciJson.put(JSON_PROP_COUNTRY, orgHierarchy.getCompanyCountry());
        gciJson.put(JSON_PROP_STATE, orgHierarchy.getCompanyState());
//        gciJson.put("clientType", clientCtxJson.optString("clientType", ""));
//        gciJson.put("clientCategory", usrCtx.getClientCategory());
//        gciJson.put("clientSubCategory", usrCtx.getClientSubCategory());
//        gciJson.put("clientName", usrCtx.getClientName());
        gciJson.put(JSON_PROP_CLIENTTYPE, "B2B");
        gciJson.put(JSON_PROP_CLIENTCAT, "CG");
        gciJson.put(JSON_PROP_CLIENTSUBCAT, "CS1");
        
        gciJson.put(JSON_PROP_VALIDITY, DATE_FORMAT.format(new Date()));
        
        JSONObject travelDtlsJson = new JSONObject();
        
        JSONArray fareDtlsJsonArr = travelDtlsJson.optJSONArray(JSON_PROP_FAREDETAILS);
    	if (fareDtlsJsonArr == null) {
    		fareDtlsJsonArr = new JSONArray();
    	}
    	
        
        for (int i=0; i < passProductsArr.length(); i++)
        {
        	JSONObject passProducts = passProductsArr.getJSONObject(i);
        	
//        	String suppID = passProducts.getString(JSON_PROP_SUPPREF);
        	JSONObject totalPricingInfoJson = passProducts.getJSONObject(JSON_PROP_PRICINGINFO);
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
        		for (int j=0; j < totalRcvblsJsonArr.length(); j++) {
        			JSONObject totalRcvblJson = totalRcvblsJsonArr.getJSONObject(j);
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
        	
        	fareDtlsJson.put(JSON_PROP_ISPKG, false);
        	fareDtlsJson.put(JSON_PROP_PROD, "Transportation");
        	fareDtlsJson.put(JSON_PROP_SUBTYPE, "Rail");
        	fareDtlsJson.put(JSON_PROP_ISMARKEDUP, isMarkedUpPrice(totalPricingInfoJson));
        	fareDtlsJson.put(JSON_PROP_ADDCHARGES, BigDecimal.ZERO);
        	fareDtlsJson.put(JSON_PROP_SELLINGPRICE, totalFareJson.getBigDecimal(JSON_PROP_AMOUNT));
        	fareDtlsJson.put(JSON_PROP_MEALPLAN, "");
        	
        	
        	
        	fareDtlsJsonArr.put(fareDtlsJson);
//        	
        }
        
        travelDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, "India");
    	travelDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, "Maharashtra");
    	travelDtlsJson.put(JSON_PROP_TRAVELLINGCITY, "Mumbai");
    	travelDtlsJson.put(JSON_PROP_FAREDETAILS, fareDtlsJsonArr);
    	JSONArray travelDetailsArr = new JSONArray();
    	travelDetailsArr.put(travelDtlsJson);
    	
    	
    	JSONObject suppHdrJson = new JSONObject();
		suppHdrJson.put(JSON_PROP_SUPPNAME, resBodyJson.optString(JSON_PROP_SUPPREF));
		suppHdrJson.put(JSON_PROP_SUPPRATETYPE, "");
		suppHdrJson.put(JSON_PROP_SUPPRATECODE, "");
		suppHdrJson.put(MDM_PROP_CLIENTNAME, "AkbarTravels");
		suppHdrJson.put(JSON_PROP_ISTHIRDPARTY, false);
		suppHdrJson.put(JSON_PROP_RATEFOR, "");
		suppHdrJson.put(JSON_PROP_LOCOFSALE, orgHierarchy.getCompanyState());
		suppHdrJson.put(JSON_PROP_PTOFSALE, usrCtx.getClientState());
		suppHdrJson.put(JSON_PROP_TRAVELDTLS, travelDetailsArr);
        JSONArray suppPrcDtlsJsonArr = new JSONArray();
        suppPrcDtlsJsonArr.put(suppHdrJson);
        
        gciJson.put(JSON_PROP_SUPPPRICINGDTLS, suppPrcDtlsJsonArr);
        
        rootJson.put(JSON_PROP_GSTCALCINTAKE, gciJson);
        
        JSONObject taxEngineResJson = null;
        
        try {
//        	System.out.println(taxEngineReqJson);
//        	taxEngineResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_TAXENGINE, TaxEngineConfig.getServiceURL(), TaxEngineConfig.getHttpHeaders(), taxEngineReqJson);
//        	taxEngineResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_TAXENGINE, TaxEngineConfig.getServiceURL(), TaxEngineConfig.getHttpHeaders(), taxEngineReqJson);
        	taxEngineResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_TAXENGINE, TaxEngineConfig.getServiceURL(), TaxEngineConfig.getHttpHeaders(), "POST", 120000, taxEngineReqJson);

        	
//        	System.out.println(taxEngineResJson);
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
				if ("MarkUp".equals(clCommsTotalJson.getString(JSON_PROP_COMMNAME))) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	
	private static void addTaxesToResponseItinerariesTotal(JSONObject reqJson, JSONObject resJson, JSONObject taxEngResJson)
	{
		
		//SINCE supplierPricingDetails and travelDetails wont repeat so consider 1st object of both and take fareDetailsArray
		JSONArray fareDtlsJsonArr = taxEngResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.taxcalculation.Root").getJSONObject("gstCalculationIntake").getJSONArray("supplierPricingDetails").getJSONObject(0).getJSONArray("travelDetails").getJSONObject(0).getJSONArray("fareDetails");
		
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		 JSONArray passProductsArr = resBodyJson.getJSONArray(JSON_PROP_PASSPROD);
		 for (int i=0; i < passProductsArr.length(); i++)
		 {
			 JSONObject passProducts = passProductsArr.getJSONObject(i);
			 
			 JSONObject totalPriceInfoJson = passProducts.getJSONObject(JSON_PROP_PRICINGINFO);
				JSONObject totalFareJson = totalPriceInfoJson.getJSONObject(JSON_PROP_TOTALFARE);
				BigDecimal totalFareAmt = totalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
				String totalFareCcy = totalFareJson.getString(JSON_PROP_CCYCODE);
				
				BigDecimal companyTaxTotalAmt = BigDecimal.ZERO;
				JSONObject fareDtlsJson = fareDtlsJsonArr.getJSONObject(i);
				JSONArray companyTaxJsonArr = new JSONArray();
				JSONArray appliedTaxesJsonArr = fareDtlsJson.optJSONArray(JSON_PROP_APPLIEDTAXDTLS);
				if (appliedTaxesJsonArr == null) {
					logger.warn("No service taxes applied on car");
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
					totalFareAmt = totalFareAmt.add(taxAmt);
				}
				
				JSONObject companyTaxesJson = new JSONObject();
				companyTaxesJson.put(JSON_PROP_AMOUNT, companyTaxTotalAmt);
				companyTaxesJson.put(JSON_PROP_CCYCODE, totalFareCcy);
				companyTaxesJson.put(JSON_PROP_COMPANYTAX, companyTaxJsonArr);
				totalFareJson.put(JSON_PROP_COMPANYTAXES, companyTaxesJson);
				totalFareJson.put(JSON_PROP_AMOUNT, totalFareAmt);
		 }
	}
	
	

}
