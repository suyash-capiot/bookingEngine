package com.coxandkings.travel.bookingengine.orchestrator.car.rental;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.car.CarConfig;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.orchestrator.car.SubType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class CompanyOffers implements CarConstants{
	
	private static final Logger logger = LogManager.getLogger(CompanyOffers.class);
	
	public static void getCompanyOffers(JSONObject req, JSONObject res, OffersType invocationType) {
        JSONObject reqHdrJson = req.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = req.getJSONObject(JSON_PROP_REQBODY);
        JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

        JSONObject resBodyJson = res.getJSONObject(JSON_PROP_RESBODY);
        String subTypeStr = reqBodyJson.optString(JSON_PROP_PRODCATEGSUBTYPE);
		SubType subType = SubType.forString(subTypeStr);
		
        //OffersConfig offConfig = CarConfig.getOffersConfig();
		//CommercialTypeConfig offTypeConfig = offConfig.getOfferTypeConfig(invocationType);
		ServiceConfig offTypeConfig = CarConfig.getOffersTypeConfig(invocationType);

		JSONObject breCpnyOffReqJson = new JSONObject(new JSONTokener(offTypeConfig.getRequestJSONShell()));
		JSONObject root = breCpnyOffReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.transportation_companyoffers.transportation_companyoffers_wredemption.Root");
		JSONObject briJson = new JSONObject();
		
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
		
		Map<String,Integer> resToBRIIndex = new HashMap<String,Integer>();
		int briNo=0;
		JSONObject carRentalReq;
		JSONArray briJsonArr = new JSONArray(); 
		root.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
		JSONArray vehicleAvailJsonArr= null;
		JSONArray carRentalJsonArr = resBodyJson.getJSONArray(JSON_PROP_CARRENTALARR);
		for(int i=0;i<carRentalJsonArr.length();i++) {
				
			JSONArray prodDtlsJsonArr = new JSONArray();
			vehicleAvailJsonArr = carRentalJsonArr.getJSONObject(i).getJSONArray(JSON_PROP_VEHICLEAVAIL);
			carRentalReq = reqBodyJson.optJSONArray(JSON_PROP_CARRENTALARR)!=null? reqBodyJson.getJSONArray(JSON_PROP_CARRENTALARR).getJSONObject(i): reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);
			briJson = createBRI(carRentalReq, resBodyJson, cpnyDtlsJson, clientDtlsJson);
			
			for (int j=0; j < vehicleAvailJsonArr.length(); j++) {
	            JSONObject vehicleAvailJson = vehicleAvailJsonArr.getJSONObject(j);
	            JSONObject totalPriceInfoJson = vehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO);
	            JSONObject totalFareJson = totalPriceInfoJson.getJSONObject(JSON_PROP_TOTALFARE);
	            JSONObject vehicleInfo = vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO);
				JSONObject prodDtlsJson = new JSONObject();
				prodDtlsJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_TRANSPORT);
				prodDtlsJson.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_RENTAL);
				//TODO : May need to revisit this and check how to set these values.
				prodDtlsJson.put(JSON_PROP_VEHICLENAME, vehicleInfo.optString(JSON_PROP_VEHMAKEMODELNAME));
				prodDtlsJson.put(JSON_PROP_VEHICLEDETAILS, getVehicleDetailsJsonArray(reqBodyJson, vehicleAvailJson));
				prodDtlsJson.put("vehicleBrandName", "Brand13");
				prodDtlsJson.put(JSON_PROP_TOTALFARE, totalFareJson.getBigDecimal(JSON_PROP_AMOUNT));
				prodDtlsJson.put(JSON_PROP_FAREDETAILS, getFareDetailsJsonArray(totalFareJson));
				prodDtlsJson.put("destination", vehicleAvailJson.optString(JSON_PROP_RETURNLOCCODE));
	            prodDtlsJsonArr.put(prodDtlsJson);
	            resToBRIIndex.put(String.format("%d%c%d", i, KEYSEPARATOR, j), briNo);
	        }
			briJson.put(JSON_PROP_PRODDETAILS, prodDtlsJsonArr);
			briJsonArr.put(briJson);
			briNo++;
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

	private static JSONArray getVehicleDetailsJsonArray(JSONObject reqBodyJson, JSONObject vehicleAvailJson) {
		
		JSONArray vehicleDetailsArr = new JSONArray();
		JSONObject vehicleDetails = new JSONObject();
		
		JSONObject totalFareJson = vehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO).getJSONObject(JSON_PROP_TOTALFARE);
		vehicleDetails.put(JSON_PROP_VEHICLECATEGORY, vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).getString(JSON_PROP_VEHICLECATEGORY));
		vehicleDetails.put(JSON_PROP_TOTALFARE, totalFareJson.getBigDecimal(JSON_PROP_AMOUNT));
		vehicleDetails.put(JSON_PROP_FAREDETAILS, getFareDetailsJsonArray(totalFareJson));
		
		vehicleDetailsArr.put(vehicleDetails);
		return vehicleDetailsArr;
	}

	private static JSONObject createBRI(JSONObject carRentalReq, JSONObject resBodyJson, JSONObject cpnyDtlsJson, JSONObject clientDtlsJson) {
		
		JSONObject briJson = new JSONObject();
		JSONObject commonElemsJson = new JSONObject();
		
		JSONObject paymentDetails = new JSONObject();
		JSONObject cardDetails = new JSONObject();
		paymentDetails.put("ModeOfPayment", "Mobile");
		paymentDetails.put("paymentType", "Debit Card");
		paymentDetails.put("bankName", "HDFC");
		paymentDetails.put("cardDetails", cardDetails);
		cardDetails.put("cardNo", "123456789");
		cardDetails.put("cardType", "Visa");
		cardDetails.put("nthBooking", "10");
		
		commonElemsJson.put(JSON_PROP_BOOKINGDATE, DATE_FORMAT.format(new Date()));
		// Following is discussed and confirmed with Offers team. The travelDate is the 
		commonElemsJson.put(JSON_PROP_TRAVELDATE, carRentalReq.getString(JSON_PROP_PICKUPDATE));
		
		//TODO : Check if we need to send paymentDetails in BRI
		briJson.put("paymentDetails", paymentDetails);
		briJson.put(JSON_PROP_COMPANYDETAILS, cpnyDtlsJson);
    	briJson.put(JSON_PROP_CLIENTDETAILS, clientDtlsJson);
    	briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
		return briJson;
	}
	

	private static JSONArray getFareDetailsJsonArray(JSONObject fareJson) {
		JSONArray fareDtlsJsonArr = new JSONArray();
		JSONObject fareDtlsJson = new JSONObject();
		JSONObject baseFareJson = fareJson.getJSONObject(JSON_PROP_BASEFARE);
		fareDtlsJson.put(JSON_PROP_FARENAME, JSON_VAL_BASE);
		fareDtlsJson.put(JSON_PROP_FAREVAL, baseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
		fareDtlsJsonArr.put(fareDtlsJson);
		
		JSONObject taxesJson = fareJson.optJSONObject(JSON_PROP_TAXES);
		if (taxesJson != null) {
			JSONArray taxJsonArr = taxesJson.optJSONArray(JSON_PROP_TAX);
			for (int j=0; taxJsonArr != null && j < taxJsonArr.length(); j++) {
				JSONObject taxJson = taxJsonArr.getJSONObject(j);
				fareDtlsJson = new JSONObject();
				fareDtlsJson.put(JSON_PROP_FARENAME, taxJson.getString(JSON_PROP_TAXCODE));
				fareDtlsJson.put(JSON_PROP_FAREVAL, taxJson.getBigDecimal(JSON_PROP_AMOUNT));
				fareDtlsJsonArr.put(fareDtlsJson);
			}
		}
		
		JSONObject feesJson = fareJson.optJSONObject(JSON_PROP_FEES);
		if (feesJson != null) {
			JSONArray feeJsonArr = feesJson.optJSONArray(JSON_PROP_FEE);
			for (int j=0; feeJsonArr != null && j < feeJsonArr.length(); j++) {
				JSONObject feeJson = feeJsonArr.getJSONObject(j);
				fareDtlsJson = new JSONObject();
				fareDtlsJson.put(JSON_PROP_FARENAME, feeJson.getString(JSON_PROP_FEECODE));
				fareDtlsJson.put(JSON_PROP_FAREVAL, feeJson.getBigDecimal(JSON_PROP_AMOUNT));
				fareDtlsJsonArr.put(fareDtlsJson);
			}
		}
		
		JSONObject rcvblsJson = fareJson.optJSONObject(JSON_PROP_RECEIVABLES);
		if (rcvblsJson != null) {
			JSONArray rcvblJsonArr = rcvblsJson.optJSONArray(JSON_PROP_RECEIVABLE);
			for (int j=0; rcvblJsonArr != null && j < rcvblJsonArr.length(); j++) {
				JSONObject rcvblJson = rcvblJsonArr.getJSONObject(j);
				fareDtlsJson = new JSONObject();
				fareDtlsJson.put(JSON_PROP_FARENAME, rcvblJson.getString(JSON_PROP_CODE));
				fareDtlsJson.put(JSON_PROP_FAREVAL, rcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
				fareDtlsJsonArr.put(fareDtlsJson);
			}
		}
		return fareDtlsJsonArr;
	}
	
	public static void appendOffersToResults(JSONObject resBodyJson, JSONObject offResJson) {
		
        JSONArray carRentalArr = resBodyJson.getJSONArray(JSON_PROP_CARRENTALARR);
        JSONArray briArr = null;
        JSONArray prodDtlsJsonArr = null;
        try {
        	briArr = offResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONArray("value").getJSONObject(0).getJSONObject("cnk.transportation_companyoffers.transportation_companyoffers_wredemption.Root").getJSONArray("businessRuleIntake");
        }
        catch (Exception x) {
        	logger.warn("Unable to retrieve businessRuleIntake. Aborting Company Offers response processing", x);
        	return;
        }
        for(int i=0;i<carRentalArr.length();i++) {
        	
        	JSONArray vehicleAvailArr = carRentalArr.getJSONObject(i).getJSONArray(JSON_PROP_VEHICLEAVAIL);
        	 try {
        		 prodDtlsJsonArr = briArr.getJSONObject(i).getJSONArray("productDetails");
             }
             catch (Exception x) {
             	logger.warn("Unable to retrieve productDetails. Aborting Company Offers response processing", x);
             	return;
             }
        	prodDtlsJsonArr = briArr.getJSONObject(i).getJSONArray("productDetails");
        	
	        if (vehicleAvailArr.length() != prodDtlsJsonArr.length()) {
	        	logger.warn("Number of vehicleAvail elements in BE result and number of productDetails elements in company offers result is different. Aborting Company Offers response processing.");
	        	return;
	        }
	        
	        for (int j=0; j < vehicleAvailArr.length(); j++) {
	        	JSONObject vehicleAvailJson = vehicleAvailArr.getJSONObject(j);
	        	JSONObject prodDtlsJson = prodDtlsJsonArr.getJSONObject(j);
	        	
	        	// Append search result level offers to search result
	        	JSONArray offersJsonArr = prodDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
	        	if (offersJsonArr != null) {
	        		vehicleAvailJson.put(JSON_PROP_OFFERS, offersJsonArr);
	        	}
	        	
	        }
        }
   }

}
