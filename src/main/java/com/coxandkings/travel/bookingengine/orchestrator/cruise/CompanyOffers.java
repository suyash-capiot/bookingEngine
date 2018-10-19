package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.cruise.CruiseConfig;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class CompanyOffers implements CruiseConstants{

	private static final Logger logger = LogManager.getLogger(CompanyOffers.class);
	
	public static void getCompanyOffers(JSONObject req, JSONObject res, OffersType invocationType) {
		
		JSONObject reqHdrJson = req.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = req.getJSONObject(JSON_PROP_REQBODY);
        JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

        JSONObject resBodyJson = res.getJSONObject(JSON_PROP_RESBODY);
        //JSONArray origDestJsonArr = reqBodyJson.getJSONArray(JSON_PROP_ORIGDESTINFO);
        
        //OffersConfig offConfig = CruiseConfig.getOffersConfig();
		//CommercialTypeConfig offTypeConfig = offConfig.getOfferTypeConfig(invocationType);
        ServiceConfig offTypeConfig = CruiseConfig.getOffersTypeConfig(invocationType);

		JSONObject breCpnyOffReqJson = new JSONObject(new JSONTokener(offTypeConfig.getRequestJSONShell()));
		JSONArray briJsonArr = breCpnyOffReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.cruise_companyoffers.cruise_companyoffers_wredemption.Root").getJSONArray("businessRuleIntake");
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
		briJson.put(JSON_PROP_CLIENTDETAILS, clientDtlsJson);
		
		OrgHierarchy orgHier = usrCtx.getOrganizationHierarchy();
		JSONObject cpnyDtlsJson = new JSONObject();
		cpnyDtlsJson.put(JSON_PROP_SBU, orgHier.getSBU());
		cpnyDtlsJson.put(JSON_PROP_BU, orgHier.getBU());
		cpnyDtlsJson.put(JSON_PROP_DIVISION, orgHier.getDivision());
		cpnyDtlsJson.put("salesOfficeLocation", orgHier.getSalesOfficeLoc());
		cpnyDtlsJson.put(JSON_PROP_SALESOFFICE, orgHier.getSalesOfficeName());
		cpnyDtlsJson.put(JSON_PROP_COMPANYMKT, clientCtxJson.getString(JSON_PROP_CLIENTMARKET));
		briJson.put(JSON_PROP_COMPANYDETAILS, cpnyDtlsJson);
		briJson.put("fareDetails", new JSONArray());
		briJson.put("totalFare", 10);
		
		
		briJson.put("paymentDetails", new JSONObject("{\r\n" + 
				"                           \"bankName\":\"HDFC\",\r\n" + 
				"                           \"ModeOfPayment\":\"Mobile\",\r\n" + 
				"                           \"paymentType\":\"Debit Card\",\r\n" + 
				"                           \"cardDetails\":{\r\n" + 
				"                              \"cardNo\":\"123456789\",\r\n" + 
				"                              \"cardType\":\"Visa\",\r\n" + 
				"                              \"nthBooking\":10\r\n" + 
				"                           }\r\n" + 
				"                        }"));
		
		
		
		JSONObject commonElemsJson = new JSONObject();
		commonElemsJson.put(JSON_PROP_BOOKINGDATE, DATE_FORMAT.format(new Date()));
		commonElemsJson.put("productCategorySubType", PRODUCT_CRUISE_PRODS);
		commonElemsJson.put("targetSet",new JSONArray("[\r\n" + 
				"                              {\r\n" + 
				"                                 \"type\":\"Number of Passengers\",\r\n" + 
				"                                 \"value\":25\r\n" + 
				"                              },\r\n" + 
				"                              {\r\n" + 
				"                                 \"type\":\"Number of Cabins\",\r\n" + 
				"                                 \"value\":25\r\n" + 
				"                              },\r\n" + 
				"                              {\r\n" + 
				"                                 \"type\":\"Number of Bookings\",\r\n" + 
				"                                 \"value\":500\r\n" + 
				"                              }\r\n" + 
				"                           ]"));
		// Following is discussed and confirmed with Offers team. The travelDate is the 
		//commonElemsJson.put(JSON_PROP_TRAVELDATE, origDestJsonArr.getJSONObject(0).getString(JSON_PROP_DEPARTDATE).concat(TIME_ZERO_SUFFIX));
		
		//Every Cruise has a different Travel Date. It cannot come in common elements
		commonElemsJson.put(JSON_PROP_TRAVELDATE, DATE_FORMAT.format(new Date()));
		
		// TODO: Populate Target Set (Slabs)
		briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
		JSONArray prodDtlsJsonArr = new JSONArray();
		JSONArray cruiseOptsJsonArr = resBodyJson.getJSONArray(JSON_PROP_CRUISEOPTIONS);
		for (int i=0; i < cruiseOptsJsonArr.length(); i++) {
			
			JSONObject cruiseOptsJson = cruiseOptsJsonArr.getJSONObject(i);
			JSONObject prodDtlsJson = new JSONObject();
			
			JSONObject selectedSailingJson = cruiseOptsJson.getJSONObject(JSON_PROP_SELECTEDSAILING);
			
			prodDtlsJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_TRANSPORT);
			prodDtlsJson.put(JSON_PROP_PRODCATEGSUBTYPE, PRODUCT_CRUISE_PRODS);
			prodDtlsJson.put("cruiseBrandName", cruiseOptsJson.getString("supplierRef"));
			prodDtlsJson.put("shipName", selectedSailingJson.getJSONObject("cruiseLine").getString("shipName"));
			prodDtlsJson.put("destination", "");
			prodDtlsJson.put("noOfNights",String.valueOf(GetDateRange.getDateRange(selectedSailingJson.getString("start"), selectedSailingJson.getString("end"), true, false).size()));
			prodDtlsJson.put("cruisePackageName", "abc");//TODO
			//Cruise Doesn't get Ancillary type or Name
			prodDtlsJson.put("ancillaryType", "abc");
			prodDtlsJson.put("ancillaryName","abc");
			
			JSONObject lowestItinTotalJson = cruiseOptsJson.getJSONObject("lowestFare").getJSONObject("totalInfo");
			
			prodDtlsJson.put(JSON_PROP_TOTALFARE,lowestItinTotalJson.getBigDecimal(JSON_PROP_AMOUNT));
			prodDtlsJson.put("fareDetailsSet", getFareDetailsJsonArray(lowestItinTotalJson));
			
			JSONArray categoryJsonArr =	cruiseOptsJson.getJSONArray(JSON_PROP_CATEGORY);
			JSONArray offCabinDetailsJsonArr = new JSONArray();
			for(int j=0;j<categoryJsonArr.length();j++)
			{
				JSONObject categoryJson = categoryJsonArr.getJSONObject(j);
				JSONObject offCategoryJson = new JSONObject();
				
				offCategoryJson.put("cabinCategory",categoryJson.getString(JSON_PROP_PRICEDCATEGORYCODE));
				offCategoryJson.put("cabinType", categoryJson.getString("categoryLocation"));
				
				JSONObject itinTotalJson =	categoryJson.getJSONObject("pricingInfo").getJSONObject("totalInfo");
				
				offCategoryJson.put(JSON_PROP_TOTALFARE,lowestItinTotalJson.getBigDecimal(JSON_PROP_AMOUNT));
				offCategoryJson.put(JSON_PROP_FAREDETAILS, getFareDetailsJsonArray(itinTotalJson));
				
				JSONArray psgrDtlsJsonArr = new JSONArray();
				JSONArray paxTypeFareJsonArr = categoryJson.getJSONObject("pricingInfo").getJSONArray("paxTypeFare");
				for(int k=0;k<paxTypeFareJsonArr.length();k++)
				{
					JSONObject paxTypeJson = paxTypeFareJsonArr.getJSONObject(k);
					JSONObject offPsgrDtlsJson = new JSONObject();
					
					offPsgrDtlsJson.put(JSON_PROP_PSGRTYPE, paxTypeJson.getString(JSON_PROP_PSGRTYPE));
					offPsgrDtlsJson.put(JSON_PROP_FAREDETAILS, getFareDetailsJsonArray1(paxTypeJson));//TODO Structure is slightly different
					offPsgrDtlsJson.put("age", 49);//Search Response doesn't have age
					offPsgrDtlsJson.put("childWithBed", false);
					offPsgrDtlsJson.put("gender", "Male");//Search Response doesn't have gender
					offPsgrDtlsJson.put("totalFare", paxTypeJson.getBigDecimal("totalFare"));
					
					JSONArray ancillaryArr = new JSONArray();
					offPsgrDtlsJson.put("ancillaryDetails", ancillaryArr);
					
					psgrDtlsJsonArr.put(offPsgrDtlsJson);
				}
				
				offCategoryJson.put("passengerDetails", psgrDtlsJsonArr);
				offCabinDetailsJsonArr.put(offCategoryJson);
			}
			prodDtlsJson.put("cabinDetails", offCabinDetailsJsonArr);
			prodDtlsJsonArr.put(prodDtlsJson);
		}
		briJson.put(JSON_PROP_PRODDETAILS, prodDtlsJsonArr);
		briJsonArr.put(briJson);
		
		System.out.println("Company Offers RQ");
		System.out.println(breCpnyOffReqJson.toString());
		
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
        System.out.println("Company Offers RS");
        System.out.println(breOffResJson.toString());
        // Check offers invocation type
        if (OffersType.COMPANY_SEARCH_TIME == invocationType) {
        	//TODO Put offers in response
        	appendOffersToResults(resBodyJson,breOffResJson);
        }
	}
	
	public static void appendOffersToResults(JSONObject resBodyJson, JSONObject offResJson) {
		JSONArray cruiseOptsJsonArr = resBodyJson.getJSONArray(JSON_PROP_CRUISEOPTIONS);
		
		JSONArray prodDtlsJsonArr = null;
        try {
        	prodDtlsJsonArr = offResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONArray("value").getJSONObject(0).getJSONObject("cnk.air_companyoffers.withoutredemption.Root").getJSONArray("businessRuleIntake").getJSONObject(0).getJSONArray("productDetails");
        }
        catch (Exception x) {
        	logger.warn("Unable to retrieve first businessRuleIntake item. Aborting Company Offers response processing", x);
        	return;
        }

        if (cruiseOptsJsonArr.length() != prodDtlsJsonArr.length()) {
        	logger.warn("Number of pricedItinerary elements in BE result and number of productDetails elements in company offers result is different. Aborting Company Offers response processing.");
        	return;
        }
        
        for(int i=0; i<cruiseOptsJsonArr.length();i++)
        {
        	JSONObject cruiseOptsJson = cruiseOptsJsonArr.getJSONObject(i);
        	JSONObject prodDtlsJson = prodDtlsJsonArr.getJSONObject(i);
        	
        	JSONArray offersJsonArr = prodDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
        	if (offersJsonArr != null) {
        		cruiseOptsJson.put(JSON_PROP_OFFERS, offersJsonArr);
        	}
        	JSONArray cabDtlsJsonArr = prodDtlsJson.getJSONArray("cabinDetails");
        	JSONArray categoryJsonArr =	cruiseOptsJson.getJSONArray(JSON_PROP_CATEGORY);
        	for(int j=0;j<categoryJsonArr.length();j++)
        	{
        		JSONObject categoryJson = categoryJsonArr.getJSONObject(j);
        		JSONObject cabDtlsJson = cabDtlsJsonArr.getJSONObject(j);
        		
        		JSONArray cabOffersJsonArr = cabDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
            	if (cabOffersJsonArr != null) {
            		categoryJson.put(JSON_PROP_OFFERS, cabOffersJsonArr);
            	}
            	
            	JSONArray paxTypeJsonArr = categoryJson.getJSONObject(JSON_PROP_PRICINGINFO).getJSONArray("paxTypeFare");
            	JSONArray passDtlsJsonArr = cabDtlsJson.getJSONArray("passengerDetails");
            	
            	for(int k=0;k<paxTypeJsonArr.length();k++)
            	{
            		JSONObject paxTypeJson = paxTypeJsonArr.getJSONObject(k);
            		JSONObject passDtlsJson = passDtlsJsonArr.getJSONObject(k);
            		
            		JSONArray paxOffersJsonArr = passDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
                	if (paxOffersJsonArr != null) {
                		paxTypeJson.put(JSON_PROP_OFFERS, paxOffersJsonArr);
                	}
            	}
        	}
        }
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
	
	private static JSONArray getFareDetailsJsonArray1(JSONObject fareJson) {
		JSONArray fareDtlsJsonArr = new JSONArray();
		JSONObject fareDtlsJson = new JSONObject();
		if(!fareJson.has("fareBreakUp"))
		{
			System.out.println("Error");
		}
		JSONObject baseFareJson = fareJson.getJSONObject("fareBreakUp");
		fareDtlsJson.put(JSON_PROP_FARENAME, JSON_VAL_BASE);
		fareDtlsJson.put(JSON_PROP_FAREVAL, baseFareJson.getBigDecimal(JSON_PROP_BASEFARE));
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
	
}
