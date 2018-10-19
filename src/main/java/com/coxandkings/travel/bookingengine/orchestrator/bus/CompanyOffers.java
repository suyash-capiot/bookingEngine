package com.coxandkings.travel.bookingengine.orchestrator.bus;


import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.bus.BusConfig;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;




public class CompanyOffers implements BusConstants{

	private static final Logger logger = LogManager.getLogger(CompanyOffers.class);
	
	public static void getCompanyOffers(JSONObject req, JSONObject res, OffersType invocationType)
	{
		JSONObject reqHdrJson = req.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = req.getJSONObject(JSON_PROP_REQBODY);
        JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
        
        JSONObject resBodyJson = res.getJSONObject(JSON_PROP_RESBODY);
        
        //OffersConfig offConfig = BusConfig.getOffersConfig();
		//CommercialTypeConfig offTypeConfig = offConfig.getOfferTypeConfig(invocationType);
        ServiceConfig offTypeConfig = BusConfig.getOffersTypeConfig(invocationType);
		
		JSONObject breCpnyOffReqJson = new JSONObject(new JSONTokener(offTypeConfig.getRequestJSONShell()));
//		JSONArray briJsonArr = breCpnyOffReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.transportation_companyoffers.transportation_companyoffers_wredemption.Root").getJSONArray("businessRuleIntake");
//		JSONObject briJson = new JSONObject();
		
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		List<ClientInfo> clientCommHierarchyList = usrCtx.getClientHierarchy();
		String clientGroup = "";
		if (clientCommHierarchyList != null && clientCommHierarchyList.size() > 0) {
			ClientInfo clInfo = clientCommHierarchyList.get(clientCommHierarchyList.size() - 1);
			if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
				clientGroup = clInfo.getCommercialsEntityId();
			}
		}
		
		JSONObject clientDtlsJson = new JSONObject();
		clientDtlsJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
		clientDtlsJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
		clientDtlsJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
		clientDtlsJson.put(JSON_PROP_CLIENTTYPE, clientCtxJson.getString(JSON_PROP_CLIENTTYPE));
		clientDtlsJson.put(JSON_PROP_POS, clientCtxJson.optString(JSON_PROP_POS, ""));
		clientDtlsJson.put(JSON_PROP_CLIENTGROUP, clientGroup);
		// TODO: Check if this is correct
		clientDtlsJson.put(JSON_PROP_NATIONALITY, clientCtxJson.getString(JSON_PROP_CLIENTMARKET));
		
		
		OrgHierarchy orgHier = usrCtx.getOrganizationHierarchy();
		JSONObject cpnyDtlsJson = new JSONObject();
		cpnyDtlsJson.put(JSON_PROP_SBU, orgHier.getSBU());
		cpnyDtlsJson.put(JSON_PROP_BU, orgHier.getBU());
		cpnyDtlsJson.put(JSON_PROP_DIVISION, orgHier.getDivision());
		cpnyDtlsJson.put(JSON_PROP_SALESOFFICELOC, orgHier.getSalesOfficeLoc());
		cpnyDtlsJson.put(JSON_PROP_SALESOFFICE, orgHier.getSalesOfficeName());
		cpnyDtlsJson.put(JSON_PROP_COMPANYMKT, clientCtxJson.getString(JSON_PROP_CLIENTMARKET));
		
		
		JSONObject commonElemsJson = new JSONObject();
		commonElemsJson.put(JSON_PROP_BOOKINGDATE, "2017-09-01T01:00:00");
		
		commonElemsJson.put(JSON_PROP_TRAVELDATE, "2017-10-01T01:00:00");

		JSONObject paymntJson = new JSONObject();
		paymntJson.put("ModeOfPayment", "Mobile");
		paymntJson.put("bankName", "HDFC");
		paymntJson.put("paymentType", "Debit Card");
		
		JSONObject cardJson = new JSONObject();
		cardJson.put("cardNo", "123456789");
		cardJson.put("cardType", "Visa");
		cardJson.put("nthBooking", 10);
		paymntJson.put("cardDetails", cardJson);
		
		JSONArray prodDtlsJsonArr = new JSONArray();
		
		JSONArray availArr =  resBodyJson.getJSONArray(JSON_PROP_AVAILABILITY);
		JSONArray briJsonArr = breCpnyOffReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.transportation_companyoffers.transportation_companyoffers_wredemption.Root").getJSONArray("businessRuleIntake");
		for(int i=0;i<availArr.length();i++)
		{
			
			JSONObject briJson = new JSONObject();
			briJson.put("paymentDetails", paymntJson);
			briJson.put(JSON_PROP_COMPANYDETAILS, cpnyDtlsJson);
			briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
			briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
			JSONArray serviceArr = availArr.getJSONObject(i).getJSONArray(JSON_PROP_SERVICE);
			for(int j=0;j<serviceArr.length();j++)
			{
				JSONObject serviceJson = serviceArr.getJSONObject(j);
				JSONArray faresArr = serviceJson.getJSONArray(JSON_PROP_FARESARRAY);
				for(int k=0;k<faresArr.length();k++)
				{
					JSONObject fareJson = faresArr.getJSONObject(k);
					JSONObject prodDtlsJson = new JSONObject();
					
					prodDtlsJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_TRANSPORT);
					prodDtlsJson.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_BUS);
					prodDtlsJson.put("vehicleBrandName", "Brand13");
					prodDtlsJson.put("vehicleName", "Ship14");
					prodDtlsJson.put("destination", "Goa");
					prodDtlsJson.put("noOfNights", "10");
					prodDtlsJson.put("vehiclePackageName", "Package3");
					prodDtlsJson.put("ancillaryType", "abc");
					prodDtlsJson.put("ancillaryName", "abc");
					
					prodDtlsJson.put(JSON_PROP_VEHICLEDETAILS, getVehicleDetailsJsonArray(prodDtlsJson, fareJson));

					prodDtlsJsonArr.put(prodDtlsJson);
					
					
				}
			}
			briJson.put(JSON_PROP_PRODDETAILS, prodDtlsJsonArr);
			briJsonArr.put(briJson);
		}
		
		
		JSONObject breOffResJson = null;
        try {
            //breOffResJson = HTTPServiceConsumer.consumeJSONService("BRMS/ComOffers", offTypeConfig.getServiceURL(), offConfig.getHttpHeaders(), breCpnyOffReqJson);
        	breOffResJson = HTTPServiceConsumer.consumeJSONService("BRMS/ComOffers", offTypeConfig.getServiceURL(), offTypeConfig.getHttpHeaders(), breCpnyOffReqJson);
        }
        catch (Exception x) {
            logger.warn("An exception occurred when calling company offers", x);
        }
        
        if (BRMS_STATUS_TYPE_FAILURE.equals(breOffResJson.getString(JSON_PROP_TYPE))) {
        	logger.warn(String.format("A failure response was received from Company Offers calculation engine: %s", breOffResJson.toString()));
        	return;
        }
     // Check offers invocation type
        if (OffersType.COMPANY_SEARCH_TIME == invocationType) {
        	appendOffersToResults(resBodyJson, breOffResJson);
        }
	}

	private static void appendOffersToResults(JSONObject resBodyJson, JSONObject offResJson) {

		 // When invoking offers engine, only one businessRuleIntake is being sent. Therefore, here retrieve the first 
        // businessRuleIntake item and process results from that.
		
		JSONArray availArr = resBodyJson.getJSONArray(JSON_PROP_AVAILABILITY);
		for(int i=0;i<availArr.length();i++)
		{
			JSONArray serviceJsonArr = availArr.getJSONObject(i).getJSONArray(JSON_PROP_SERVICE);
			for(int j=0;j<serviceJsonArr.length();j++)
			{
				JSONObject serviceJson = serviceJsonArr.getJSONObject(j);
				JSONArray fareJsonArr = serviceJson.getJSONArray(JSON_PROP_FARESARRAY);
				for(int k=0;k<fareJsonArr.length();k++)
				{
					JSONObject fareJSon = fareJsonArr.getJSONObject(k);
					
					JSONArray prodDtlsJsonArr = null;
			        try {
			        	prodDtlsJsonArr = offResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONArray("value").getJSONObject(0).getJSONObject("cnk.air_companyoffers.withoutredemption.Root").getJSONArray("businessRuleIntake").getJSONObject(0).getJSONArray("productDetails");
			        }
			        catch (Exception x) {
			        	logger.warn("Unable to retrieve first businessRuleIntake item. Aborting Company Offers response processing", x);
			        	return;
			        }

			        if (serviceJsonArr.length() != prodDtlsJsonArr.length()) {
			        	logger.warn("Number of pricedItinerary elements in BE result and number of productDetails elements in company offers result is different. Aborting Company Offers response processing.");
			        	return;
			        }
			        
			        JSONObject prodDtlsJson = prodDtlsJsonArr.getJSONObject(i);
			        
			     // Append search result level offers to search result
		        	JSONArray offersJsonArr = prodDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
		        	if (offersJsonArr != null) {
		        		serviceJson.put(JSON_PROP_OFFERS, offersJsonArr);
		        	}
		        	
		        	
				}
			}
		}
        
		
	}

	private static JSONArray getVehicleDetailsJsonArray(JSONObject prodDtlsJson, JSONObject fareJson) {
		
		JSONArray vehDtlsJsonArr = new JSONArray();
		JSONObject vehDtlJson = new JSONObject();
		JSONArray fareDtlsArr = new JSONArray();
		
		vehDtlJson.put("vehicleCategory", "Deluxe");
		vehDtlJson.put("vehicleType", "Luxury");
		
		JSONObject serviceTotalFareJson = fareJson.getJSONObject(JSON_PROP_BUSSERVICETOTALFARE);
		vehDtlJson.put(JSON_PROP_TOTALFARE, serviceTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT));
		vehDtlJson.put(JSON_PROP_FAREDETAILS, getFareDetailsJsonArray(serviceTotalFareJson,fareDtlsArr));
		vehDtlsJsonArr.put(vehDtlJson);
		
		prodDtlsJson.put(JSON_PROP_TOTALFARE, serviceTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT));
		
		return vehDtlsJsonArr;
	}

	private static JSONArray getFareDetailsJsonArray(JSONObject serviceTotalFareJson,JSONArray fareDtlsArr) {

		JSONObject fareJson = new JSONObject();
		JSONObject baseFareJson = serviceTotalFareJson.getJSONObject(JSON_PROP_BASEFARE);
		fareJson.put(JSON_PROP_FARENAME, "BASE");
		fareJson.put(JSON_PROP_FAREVALUE, baseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
		fareDtlsArr.put(fareJson);
		
		JSONObject rcvblsJson = serviceTotalFareJson.optJSONObject(JSON_PROP_RECEIVABLES);
		if (rcvblsJson != null) {
			JSONArray rcvblJsonArr = rcvblsJson.optJSONArray(JSON_PROP_RECEIVABLE);
			for (int j=0; rcvblJsonArr != null && j < rcvblJsonArr.length(); j++) {
				JSONObject rcvblJson = rcvblJsonArr.getJSONObject(j);
				fareJson = new JSONObject();
				fareJson.put(JSON_PROP_FARENAME, rcvblJson.getString(JSON_PROP_CODE));
				fareJson.put(JSON_PROP_FAREVALUE, rcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
				fareDtlsArr.put(fareJson);
			}
		}
		return fareDtlsArr;
		
	}

	
}
