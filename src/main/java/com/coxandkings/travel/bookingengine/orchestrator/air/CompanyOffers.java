package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.enums.PassengerType;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirlineData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirportData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;

public class CompanyOffers implements AirConstants {
	
	private static final Logger logger = LogManager.getLogger(CompanyOffers.class);
	
	public static void getCompanyOffers(JSONObject req, JSONObject res, OffersType invocationType, CommercialsOperation op) throws ValidationException {
        
		JSONObject reqHdrJson = req.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = req.getJSONObject(JSON_PROP_REQBODY);
        JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

        JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBodyJson = res.getJSONObject(JSON_PROP_RESBODY);
        //JSONArray origDestJsonArr = reqBodyJson.getJSONArray(JSON_PROP_ORIGDESTINFO);
        
      

        
        
        //OffersConfig offConfig = AirConfig.getOffersConfig();
		//CommercialTypeConfig offTypeConfig = offConfig.getOfferTypeConfig(invocationType);
        ServiceConfig offTypeConfig = AirConfig.getOffersTypeConfig(invocationType);

		JSONObject breCpnyOffReqJson = new JSONObject(new JSONTokener(offTypeConfig.getRequestJSONShell()));
		
		JSONObject rootJson = breCpnyOffReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.air_companyoffers.withoutredemption.Root");
		 
		JSONObject breHdrJson = new JSONObject();
        breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
        breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
        breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
        breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());
		rootJson.put(JSON_PROP_HEADER, breHdrJson);
		//JSONObject briJson = new JSONObject();
		JSONArray briJsonArr = rootJson.getJSONArray("businessRuleIntake");
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
		cpnyDtlsJson.put(JSON_PROP_SALESOFFICELOC, orgHier.getSalesOfficeLoc());
		cpnyDtlsJson.put(JSON_PROP_SALESOFFICE, orgHier.getSalesOfficeName());
		cpnyDtlsJson.put(JSON_PROP_COMPANYMKT, clientCtxJson.getString(JSON_PROP_CLIENTMARKET));
		briJson.put(JSON_PROP_COMPANYDETAILS, cpnyDtlsJson);
		
		JSONObject commonElemsJson = new JSONObject();
		commonElemsJson.put(JSON_PROP_BOOKINGDATE, DATE_FORMAT.format(new Date()));
		// Following is discussed and confirmed with Offers team. The travelDate is the 
		//commonElemsJson.put(JSON_PROP_TRAVELDATE, origDestJsonArr.getJSONObject(0).getString(JSON_PROP_DEPARTDATE).concat(TIME_ZERO_SUFFIX));
		commonElemsJson.put(JSON_PROP_TRAVELDATE, getTravelDateFromResponse(resBodyJson));
		//TODO:Determine value;Temp Fix
		commonElemsJson.put(JSON_PROP_TARGETSET,new JSONArray());
		
		// TODO: Populate Target Set (Slabs)
		briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
		
		JSONArray prodDtlsJsonArr = new JSONArray();
		JSONArray pricedItinsJsonArr = resBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
		for (int i=0; i < pricedItinsJsonArr.length(); i++) {
			JSONObject pricedItinJson = pricedItinsJsonArr.getJSONObject(i);
			
			JSONObject prodDtlsJson = new JSONObject();
			JSONArray reqPricedItinJsonArr = reqBodyJson.optJSONArray(JSON_PROP_PRICEDITIN);
			if((reqPricedItinJsonArr)!=null) {
				JSONObject reqPricedItinJson = reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN).getJSONObject(i);
				JSONArray selectedOfferListJsonArray=reqPricedItinJson.optJSONArray(JSON_PROP_SELECTEDOFFERLIST);
				if(selectedOfferListJsonArray!=null && op.toString().equalsIgnoreCase("Reprice") ) {
					prodDtlsJson.put(JSON_PROP_SELECTEDOFFERLIST, selectedOfferListJsonArray);
				}
				
			}
			
			
			
			prodDtlsJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_TRANSPORT);
			prodDtlsJson.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_FLIGHT);
			prodDtlsJson.put(JSON_PROP_JOURNEYTYPE, reqBodyJson.getString(JSON_PROP_TRIPTYPE));
			prodDtlsJson.put(JSON_PROP_FLIGHTDETAILS, getFlightDetailsJsonArray(reqBodyJson, pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY)));
			
			JSONObject airPriceInfoJson = pricedItinJson.getJSONObject(JSON_PROP_AIRPRICEINFO);
			JSONObject itinTotalJson = airPriceInfoJson.getJSONObject(JSON_PROP_ITINTOTALFARE);
			prodDtlsJson.put(JSON_PROP_TOTALFARE, itinTotalJson.getBigDecimal(JSON_PROP_AMOUNT));
			prodDtlsJson.put(JSON_PROP_FAREDETAILS, getFareDetailsJsonArray(itinTotalJson));
			
			JSONArray psgrDtlsJsonArr = new JSONArray();
			Map<String,BigDecimal> reqPaxCounts = AirSearchProcessor.getPaxCountsFromRequest(req);
			Map<String, List<JSONObject>> paxDtlsbyType = getPaxDetailsByPaxTye(reqBodyJson);
			JSONArray paxTypeFaresJsonArr = airPriceInfoJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
			for (int j=0; j < paxTypeFaresJsonArr.length(); j++) {
				JSONObject paxTypeFareJson = paxTypeFaresJsonArr.getJSONObject(j);
				String paxType = paxTypeFareJson.getString(JSON_PROP_PAXTYPE);
				List<JSONObject> paxDtlsList = paxDtlsbyType.get(paxType);
				int paxCount = (reqPaxCounts.containsKey(paxType)) ? reqPaxCounts.get(paxType).intValue() : 0;
				for (int k=0; k < paxCount; k++) {
					JSONObject psgrDtlsJson = new JSONObject();
					psgrDtlsJson.put(JSON_PROP_PSGRTYPE, paxType);
					// When called from Reprice operation, JSON values for age and gender need to be set here.
					if (paxDtlsList != null && k < paxDtlsList.size()) {
						JSONObject paxDtlJson = paxDtlsList.get(k);
						psgrDtlsJson.put(JSON_PROP_GENDER, paxDtlJson.optString(JSON_PROP_GENDER));
						psgrDtlsJson.put(JSON_PROP_AGE, getAge(paxDtlJson));
					}
					JSONObject totalFarePaxJson=new JSONObject();
					psgrDtlsJson.put(JSON_PROP_FAREDETAILS, getPaxFareDetailsJsonArray(paxTypeFareJson,totalFarePaxJson));
					psgrDtlsJson.put(JSON_PROP_TOTALFARE, totalFarePaxJson.getBigDecimal(JSON_PROP_AMOUNT));
					//psgrDtlsJson.put(JSON_PROP_FAREDETAILS, getFareDetailsJsonArray(paxTypeFareJson));
					psgrDtlsJsonArr.put(psgrDtlsJson);
				}
			}
			prodDtlsJson.put(JSON_PROP_PSGRDETAILS, psgrDtlsJsonArr);
			
			prodDtlsJsonArr.put(prodDtlsJson);
		}
		briJson.put(JSON_PROP_PRODDETAILS, prodDtlsJsonArr);
		briJsonArr.put(briJson);
		
        JSONObject breOffResJson = null;
        try {
        	
            //breOffResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_COMPANYOFFS, offTypeConfig.getServiceURL(), offConfig.getHttpHeaders(), breCpnyOffReqJson);
        	breOffResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_COMPANYOFFS, offTypeConfig.getServiceURL(), offTypeConfig.getHttpHeaders(), "POST", 60000, breCpnyOffReqJson);
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
        	if(op.toString().equalsIgnoreCase("Reprice")) {
        		appendDiscountsToResults(resBodyJson, breOffResJson,reqHdrJson);
        	}
        	else {
        		appendOffersToResults(resBodyJson, breOffResJson,reqHdrJson);
        	}
        	
        	
        	
        }
	}

	private static void appendDiscountsToResults(JSONObject resBodyJson, JSONObject offResJson, JSONObject reqHdrJson) {
		// TODO Auto-generated method stub
		 JSONArray pricedItinsJsonArr = resBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
	    
	        // When invoking offers engine, only one businessRuleIntake is being sent. Therefore, here retrieve the first 
	        // businessRuleIntake item and process results from that.
	        JSONArray prodDtlsJsonArr = null;
	        try {
	        	prodDtlsJsonArr = offResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONArray("value").getJSONObject(0).getJSONObject("cnk.air_companyoffers.withoutredemption.Root").getJSONArray("businessRuleIntake").getJSONObject(0).getJSONArray("productDetails");
	        }
	        catch (Exception x) {
	        	logger.warn("Unable to retrieve first businessRuleIntake item. Aborting Company Offers response processing", x);
	        	return;
	        }

	        if (pricedItinsJsonArr.length() != prodDtlsJsonArr.length()) {
	        	logger.warn("Number of pricedItinerary elements in BE result and number of productDetails elements in company offers result is different. Aborting Company Offers response processing.");
	        	return;
	        }
	        
	        /*if(Utils.isStringNullOrEmpty(offerJson.optString(JSON_PROP_CURRENCY))) {
				BigDecimal offerAmt=offerJson.getBigDecimal(JSON_PROP_OFFERAMOUNT);
				offerAmt=offerAmt.multiply(RedisRoEData.getRateOfExchange(offerJson.getString(JSON_PROP_CURRENCY), reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY)));
				offerJson.put(JSON_PROP_OFFERAMOUNT, offerAmt);
				offerJson.put(JSON_PROP_CURRENCY, reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY));
				offersJsonArr.put(offerJson);
			}
			else {
				offersJsonArr.put(offerJson);
			}*/
	        
	        for(int i=0;i<pricedItinsJsonArr.length();i++) {
	        	JSONObject pricedItinJson=pricedItinsJsonArr.getJSONObject(i);
	        	 BigDecimal totalDiscountAmount=new BigDecimal(0);
	        	 String currencyCode="";
	        	//TODO:Add recognization b/w pricedItin and prodDetail check here 
	        	JSONObject prodDtlsJson = prodDtlsJsonArr.getJSONObject(i);
	        	JSONArray offerDetailSetArr=prodDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
	        	JSONArray offerJsonArr=new JSONArray();
	        	
	        	JSONArray discountJsonArr=new JSONArray();
	        	JSONObject discountTotalJson=new JSONObject();
	        	if(offerDetailSetArr!=null) {
	        		for(int j=0;j<offerDetailSetArr.length();j++) {
		        		JSONObject discountJson=new JSONObject();
		        		JSONObject offerDetailJson=offerDetailSetArr.getJSONObject(j);
		        		totalDiscountAmount=totalDiscountAmount.add(offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
		        		if(!Utils.isStringNullOrEmpty(offerDetailJson.optString(JSON_PROP_CURRENCY))) {
		        			BigDecimal offerAmt=offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT);
		        			offerAmt=offerAmt.multiply(RedisRoEData.getRateOfExchange(offerDetailJson.getString(JSON_PROP_CURRENCY), reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY)));
		        			discountJson.put(JSON_PROP_AMOUNT, offerAmt);
		        		
		        			BigDecimal amount=offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT).multiply(RedisRoEData.getRateOfExchange(offerDetailJson.getString(JSON_PROP_CURRENCY), reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY)));
        					 BigDecimal amt=offerDetailJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO);
        					 if(!amt.equals(BigDecimal.ZERO)) {
        						 offerDetailJson.put(JSON_PROP_AMOUNT, amt.multiply(RedisRoEData.getRateOfExchange(offerDetailJson.getString(JSON_PROP_CURRENCY), reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY))));
        					 }
        					 offerDetailJson.put(JSON_PROP_CURRENCY, reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY));
        					 offerDetailJson.put(JSON_PROP_OFFERAMOUNT, amount);
		        			
		        		}
		        		else {
		        			discountJson.put(JSON_PROP_AMOUNT, offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
		   
		        		}
		        		offerJsonArr.put(offerDetailJson);
		        		currencyCode=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		        		discountJson.put(JSON_PROP_CCYCODE, reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY));
		        		discountJson.put(JSON_PROP_DISCOUNTCODE, offerDetailJson.optString(JSON_PROP_OFFERID));
		        		discountJson.put(JSON_PROP_DISCOUNTTYPE, offerDetailJson.optString(JSON_PROP_OFFERTYPE));
		        		discountJsonArr.put(discountJson);
		        	}
	        	}
	        
	        	
	        	JSONObject airPriceItinJson=pricedItinJson.getJSONObject(JSON_PROP_AIRPRICEINFO);        	
	        	//Adding offerTag
	        	airPriceItinJson.put(JSON_PROP_OFFERS, offerJsonArr);
	        	
	        	Map<String,JSONObject> paxTypeFareMap=new HashMap<String,JSONObject>();
	        	createPaxTypeFareMap(paxTypeFareMap,airPriceItinJson);
	        	 
	        	 
	        	Map<String,JSONObject> discountOfferMap=new HashMap<String,JSONObject>(); 
	        		JSONArray offerPaxDetails=prodDtlsJson.optJSONArray(JSON_PROP_PSGRDETAILS);
	        		for(int k=0;k<offerPaxDetails.length();k++) {
	        			offerJsonArr=new JSONArray();
	        			JSONObject offerPaxDetailJson=offerPaxDetails.getJSONObject(k);
	        			JSONObject paxTypeFareJson=paxTypeFareMap.get(offerPaxDetailJson.getString("passengerType"));
	        			JSONArray offerDetailJsonArr=offerPaxDetailJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
	        			if(offerDetailJsonArr!=null) {
	        				for(int l=0;l<offerDetailJsonArr.length();l++) {
	        					JSONObject offerDetailJson=offerDetailJsonArr.getJSONObject(l);
	        					JSONObject discountJson=new JSONObject();
	        					if(!(offerDetailJson.getString(JSON_PROP_OFFERTYPE)).equalsIgnoreCase("cashback")) {
	        						totalDiscountAmount=totalDiscountAmount.add(offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
	    	        			}
	    	        			if(discountOfferMap.containsKey(offerDetailJson.optString(JSON_PROP_OFFERTYPE)+"|"+offerDetailJson.optString(JSON_PROP_OFFERID))) {
	    	        				JSONObject discountMapJson=discountOfferMap.get(offerDetailJson.optString(JSON_PROP_OFFERTYPE)+"|"+offerDetailJson.optString(JSON_PROP_OFFERID));
	    	        				BigDecimal amount=null;
	    	        				if(!Utils.isStringNullOrEmpty(offerDetailJson.optString(JSON_PROP_CURRENCY))) {
	    	        					 amount=offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT).multiply(RedisRoEData.getRateOfExchange(offerDetailJson.getString(JSON_PROP_CURRENCY), reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY)));
	    	        					 BigDecimal amt=offerDetailJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO);
	    	        					 if(!amt.equals(BigDecimal.ZERO)) {
	    	        						 offerDetailJson.put(JSON_PROP_AMOUNT, amt.multiply(RedisRoEData.getRateOfExchange(offerDetailJson.getString(JSON_PROP_CURRENCY), reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY))));
	    	        					 }
	    	        					 offerDetailJson.put(JSON_PROP_CURRENCY, reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY));
	    	        					 offerDetailJson.put(JSON_PROP_OFFERAMOUNT, amount);
	    	        				}
	    	        				else {
	    	        					amount=offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT);
	    	        				}
	    	        				
	    	        				
	    	        				BigDecimal addedDiscountJson=(discountMapJson.getBigDecimal(JSON_PROP_AMOUNT)).add(amount);
	    	        				discountMapJson.put(JSON_PROP_AMOUNT, addedDiscountJson);
	    	        				offerJsonArr.put(offerDetailJson);
	    	        				continue;
	    	        			}
	    	        			discountJson.put(JSON_PROP_DISCOUNTCODE, offerDetailJson.optString(JSON_PROP_OFFERID));
	    		        		discountJson.put(JSON_PROP_DISCOUNTTYPE, offerDetailJson.optString(JSON_PROP_OFFERTYPE));
	    		        		
	    		        		discountJson.put(JSON_PROP_AMOUNT, offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
	    		        		offerJsonArr.put(offerDetailJson);
	    		        		currencyCode=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
	    		        		
	    		        		discountJson.put(JSON_PROP_CCYCODE, reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY));
	    		        		
	    		        		
	    		        		
	    		        		discountOfferMap.put(offerDetailJson.optString(JSON_PROP_OFFERTYPE)+"|"+offerDetailJson.optString(JSON_PROP_OFFERID),discountJson);
	        					
	        					
	        				}
	        				paxTypeFareJson.put(JSON_PROP_OFFERS, offerJsonArr);
	        			}
	        			
	        		}	
	        		discountOfferMap.forEach((key,discountJson) ->discountJsonArr.put(discountJson));

	        	discountTotalJson.put(JSON_PROP_AMOUNT, totalDiscountAmount);
	        	discountTotalJson.put(JSON_PROP_CCYCODE, currencyCode);
	        	discountTotalJson.put(JSON_PROP_DISCOUNT, discountJsonArr);
	        	airPriceItinJson.getJSONObject(JSON_PROP_ITINTOTALFARE).put(JSON_PROP_DISCOUNTS, discountTotalJson);
	        	BigDecimal itinTotalPrice=airPriceItinJson.getJSONObject(JSON_PROP_ITINTOTALFARE).getBigDecimal(JSON_PROP_AMOUNT);
	        	itinTotalPrice=itinTotalPrice.subtract(totalDiscountAmount);
	        	airPriceItinJson.getJSONObject(JSON_PROP_ITINTOTALFARE).put(JSON_PROP_AMOUNT, itinTotalPrice);
	        }
	}
	private static void createPaxTypeFareMap(Map<String, JSONObject> paxTypeFareMap, JSONObject airPriceItinJson) {
		JSONArray paxTypeFareJsonArr=airPriceItinJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
		for(int i=0;i<paxTypeFareJsonArr.length();i++) {
			JSONObject paxTypeFareJson=paxTypeFareJsonArr.getJSONObject(i);
			paxTypeFareMap.put(paxTypeFareJson.getString(JSON_PROP_PAXTYPE), paxTypeFareJson);
		}
		
		
	}

	private static JSONArray getPaxFareDetailsJsonArray(JSONObject fareJson, JSONObject totalFarePaxJson) {
		JSONArray fareDtlsJsonArr = new JSONArray();
		JSONObject fareDtlsJson = new JSONObject();
		
		BigDecimal totalPaxFare=new BigDecimal(0);
		JSONObject baseFareJson = fareJson.getJSONObject(JSON_PROP_BASEFARE);
		fareDtlsJson.put(JSON_PROP_FARENAME, JSON_VAL_BASE);
		totalPaxFare=totalPaxFare.add(baseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
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
				totalPaxFare=totalPaxFare.add(taxJson.getBigDecimal(JSON_PROP_AMOUNT));
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
				totalPaxFare=totalPaxFare.add(feeJson.getBigDecimal(JSON_PROP_AMOUNT));
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
				totalPaxFare=totalPaxFare.add(rcvblJson.getBigDecimal(JSON_PROP_AMOUNT));
				fareDtlsJsonArr.put(fareDtlsJson);
			}
		}
		totalFarePaxJson.put(JSON_PROP_AMOUNT, totalPaxFare);
		return fareDtlsJsonArr;
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
	
	private static JSONArray getFlightDetailsJsonArray(JSONObject reqBodyJson, JSONObject airItinJson) {
		JSONArray flDtlsJsonArr = new JSONArray();
		JSONArray odosJsonArr = airItinJson.getJSONArray(JSON_PROP_ORIGDESTOPTS);
		
		for (int j=0; j < odosJsonArr.length(); j++) {
			JSONObject odosJson = odosJsonArr.getJSONObject(j);
			JSONArray flSegsJsonArr = odosJson.getJSONArray(JSON_PROP_FLIGHTSEG);
			for (int k=0; k < flSegsJsonArr.length(); k++) {
				Map<String,Object> airportInfo = null;
				JSONObject flDtlJson = new JSONObject();
				JSONObject fltSegJson = flSegsJsonArr.getJSONObject(k);
				
            	String origLoc = fltSegJson.getString(JSON_PROP_ORIGLOC);
            	airportInfo = RedisAirportData.getAirportInfo(origLoc);
				flDtlJson.put(JSON_PROP_CITYFROM, airportInfo.getOrDefault(RedisAirportData.AIRPORT_CITY, ""));
				flDtlJson.put(JSON_PROP_COUNTRYFROM, airportInfo.getOrDefault(RedisAirportData.AIRPORT_COUNTRY, ""));
				
            	String destLoc = fltSegJson.getString(JSON_PROP_DESTLOC);
            	airportInfo = RedisAirportData.getAirportInfo(destLoc);
				flDtlJson.put(JSON_PROP_CITYTO, airportInfo.getOrDefault(RedisAirportData.AIRPORT_CITY, ""));
				flDtlJson.put(JSON_PROP_COUNTRYTO, airportInfo.getOrDefault(RedisAirportData.AIRPORT_COUNTRY, ""));
				
				flDtlJson.put(JSON_PROP_CABINCLASS, fltSegJson.getString(JSON_PROP_CABINTYPE));
				JSONObject mrktAirlineJson = fltSegJson.getJSONObject(JSON_PROP_MARKAIRLINE);
				String mrktAirlineCode = mrktAirlineJson.optString(JSON_PROP_AIRLINECODE);
				JSONObject operAirlineJson = fltSegJson.getJSONObject(JSON_PROP_OPERAIRLINE);
				String operAirlineCode = operAirlineJson.getString(JSON_PROP_AIRLINECODE);
				
				Map<String,Object> airlineData = RedisAirlineData.getAirlineDetails(operAirlineCode);
				flDtlJson.put(JSON_PROP_AIRLINENAME, airlineData.getOrDefault(RedisAirlineData.AIRLINE_NAME, ""));
				flDtlJson.put(JSON_PROP_AIRLINETYPE, airlineData.getOrDefault(RedisAirlineData.AIRLINE_TYPE, ""));
				// Comparing only marketing and operating airline code. If need be, flight number also can be compared.
				flDtlJson.put(JSON_PROP_ISCODESHARE, (operAirlineCode.equals(mrktAirlineCode) == false));
				flDtlJson.put(JSON_PROP_FLIGHTNBR, operAirlineJson.getString(JSON_PROP_FLIGHTNBR));
				
				flDtlsJsonArr.put(flDtlJson);
			}
		}
		
		return flDtlsJsonArr;
	}
	
	public static void appendOffersToResults(JSONObject resBodyJson, JSONObject offResJson, JSONObject reqHdrJson) {
        JSONArray pricedItinsJsonArr = resBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
        
        // When invoking offers engine, only one businessRuleIntake is being sent. Therefore, here retrieve the first 
        // businessRuleIntake item and process results from that.
        JSONArray prodDtlsJsonArr = null;
        try {
        	prodDtlsJsonArr = offResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONArray("value").getJSONObject(0).getJSONObject("cnk.air_companyoffers.withoutredemption.Root").getJSONArray("businessRuleIntake").getJSONObject(0).getJSONArray("productDetails");
        }
        catch (Exception x) {
        	logger.warn("Unable to retrieve first businessRuleIntake item. Aborting Company Offers response processing", x);
        	return;
        }

        if (pricedItinsJsonArr.length() != prodDtlsJsonArr.length()) {
        	logger.warn("Number of pricedItinerary elements in BE result and number of productDetails elements in company offers result is different. Aborting Company Offers response processing.");
        	return;
        }
        
        for (int i=0; i < pricedItinsJsonArr.length(); i++) {
        	JSONObject pricedItinJson = pricedItinsJsonArr.getJSONObject(i);
        	JSONObject prodDtlsJson = prodDtlsJsonArr.getJSONObject(i);
        	
        	// Append search result level offers to search result
        	//JSONArray offersJsonArr = prodDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
        	JSONArray offersJsonArr =new JSONArray();
        	//JSONArray offersJsonsArr=
        	if (pricedItinJson != null) {
        		
        		//TODO:what to do in case of calcType is amount and percentage
        		if(offersJsonArr!=null) {
        			pricedItinJson.getJSONObject(JSON_PROP_AIRPRICEINFO).put(JSON_PROP_OFFERS, offersJsonArr);
        		for(int l=0;l<offersJsonArr.length();l++) {
        			JSONObject offerJson=offersJsonArr.getJSONObject(l);
        			if(!Utils.isStringNullOrEmpty(offerJson.optString(JSON_PROP_CURRENCY))) {
        				BigDecimal offerAmt=offerJson.getBigDecimal(JSON_PROP_OFFERAMOUNT);
        				offerAmt=offerAmt.multiply(RedisRoEData.getRateOfExchange(offerJson.getString(JSON_PROP_CURRENCY), reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY)));
        				BigDecimal amount=offerJson.optBigDecimal(JSON_PROP_AMOUNT,BigDecimal.ZERO);
        				if(!amount.equals(BigDecimal.ZERO)) {
        					offerJson.put(JSON_PROP_AMOUNT, amount.multiply( RedisRoEData.getRateOfExchange(offerJson.getString(JSON_PROP_CURRENCY), reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY))));
        				}
        						
        				offerJson.put(JSON_PROP_OFFERAMOUNT, offerAmt);
        				offerJson.put(JSON_PROP_CURRENCY, reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY));
        				offersJsonArr.put(offerJson);
        			}
        			else {
        				offersJsonArr.put(offerJson);
        			}
        		}
        	}
        	}
        	
        	// Retrieve and de-duplicate offers that are at passenger level
        	Map<PassengerType, Map<String, JSONObject>> psgrOffers = new LinkedHashMap<PassengerType, Map<String, JSONObject>>();
        	JSONArray psgrDtlsJsonArr = prodDtlsJson.getJSONArray(JSON_PROP_PSGRDETAILS);
        	for (int j=0; j < psgrDtlsJsonArr.length(); j++) {
        		JSONObject psgrDtlsJson = psgrDtlsJsonArr.getJSONObject(j);
        		
        		PassengerType psgrType = PassengerType.forString(psgrDtlsJson.getString(JSON_PROP_PSGRTYPE));
        		if (psgrType == null) {
        			continue;
        		}
        		
        		Map<String,JSONObject> psgrTypeOffers = (psgrOffers.containsKey(psgrType)) ? psgrOffers.get(psgrType) : new LinkedHashMap<String,JSONObject>();
        		JSONArray psgrOffersJsonArr = psgrDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
        		if (psgrOffersJsonArr == null) {
        			continue;
        		}
        		for (int k=0; k < psgrOffersJsonArr.length(); k++) {
        			JSONObject psgrOfferJson = psgrOffersJsonArr.getJSONObject(k);
        			String offerId = psgrOfferJson.getString(JSON_PROP_OFFERID);
        			if(!Utils.isStringNullOrEmpty(psgrOfferJson.optString(JSON_PROP_CURRENCY))) {
        				BigDecimal offerAmt=psgrOfferJson.getBigDecimal(JSON_PROP_OFFERAMOUNT);
        				offerAmt=offerAmt.multiply(RedisRoEData.getRateOfExchange(psgrOfferJson.getString(JSON_PROP_CURRENCY), reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY)));
        				//psgrOfferJson.put(JSON_PROP_CURRENCY, reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY));
        				BigDecimal amount=psgrOfferJson.optBigDecimal(JSON_PROP_AMOUNT,BigDecimal.ZERO);
        				if(!amount.equals(BigDecimal.ZERO)) {
        					psgrOfferJson.put(JSON_PROP_AMOUNT,amount.multiply( RedisRoEData.getRateOfExchange(psgrOfferJson.getString(JSON_PROP_CURRENCY), reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY))));
        				}
        						
        				psgrOfferJson.put(JSON_PROP_OFFERAMOUNT, offerAmt);
        				psgrOfferJson.put(JSON_PROP_CURRENCY, reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY));
        				psgrTypeOffers.put(offerId, psgrOfferJson);
        			}
        			else
        			{
        				psgrTypeOffers.put(offerId, psgrOfferJson);
        			}
        		}
        		psgrOffers.put(psgrType, psgrTypeOffers);
        	}
        	
        	
        	JSONObject airPriceInfoJson = pricedItinJson.getJSONObject(JSON_PROP_AIRPRICEINFO);
        	JSONArray paxTypeFaresJsonArr = airPriceInfoJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
        	for (int j=0; j <paxTypeFaresJsonArr.length(); j++) {
        		JSONObject paxTypeFareJson = paxTypeFaresJsonArr.getJSONObject(j);
        		PassengerType psgrType = PassengerType.forString(paxTypeFareJson.getString(JSON_PROP_PAXTYPE));
        		if (psgrType == null) {
        			continue;
        		}
        		
        		JSONArray psgrTypeOffsJsonArr = new JSONArray();
        		Map<String,JSONObject> psgrTypeOffers = psgrOffers.get(psgrType);
        		if (psgrTypeOffers == null) {
        			continue;
        		}
        		
        		Collection<JSONObject> psgrTypeOffersColl = psgrTypeOffers.values();
        		for (JSONObject psgrTypeOffer : psgrTypeOffersColl) {
        			psgrTypeOffsJsonArr.put(psgrTypeOffer);
        		}
        		paxTypeFareJson.put(JSON_PROP_OFFERS, psgrTypeOffsJsonArr);
        	}
        }
	}

	private static String getTravelDateFromResponse(JSONObject resBodyJson) {
		JSONArray pricedItinsJsonArr = resBodyJson.optJSONArray(JSON_PROP_PRICEDITIN);
		if (pricedItinsJsonArr == null || pricedItinsJsonArr.length() == 0) {
			return "";
		}
		
		JSONObject airItinJson = pricedItinsJsonArr.getJSONObject(0).getJSONObject(JSON_PROP_AIRITINERARY);
		JSONArray odosJsonArr = airItinJson.getJSONArray(JSON_PROP_ORIGDESTOPTS);
		if (odosJsonArr == null || odosJsonArr.length() == 0) {
			return "";
		}
		
		JSONArray flSegsJsonArr = odosJsonArr.getJSONObject(0).getJSONArray(JSON_PROP_FLIGHTSEG);
		if (flSegsJsonArr == null || flSegsJsonArr.length() == 0) {
			return "";
		}
		
		String departDate = flSegsJsonArr.getJSONObject(0).optString(JSON_PROP_DEPARTDATE);
		//return (departDate.length() >= 10) ? departDate.substring(0, 10) : "";
		return departDate;
	}
	
	private static Map<String, List<JSONObject>> getPaxDetailsByPaxTye(JSONObject reqBodyJson) {
		Map<String, List<JSONObject>> paxDtlsByType = new HashMap<String, List<JSONObject>>();
		JSONArray paxDtlsJsonArr = reqBodyJson.optJSONArray(JSON_PROP_PAXDETAILS);
		if (paxDtlsJsonArr != null) {
			for (int i = 0; i < paxDtlsJsonArr.length(); i++) {
				JSONObject paxDtlJson = paxDtlsJsonArr.getJSONObject(i);
				String paxType = paxDtlJson.optString(JSON_PROP_PAXTYPE);
				if (paxDtlsByType.containsKey(paxType)) {
					paxDtlsByType.get(paxType).add(paxDtlJson);
				}
				else {
					List<JSONObject> paxDtlsList = new ArrayList<JSONObject>();
					paxDtlsList.add(paxDtlJson);
					 paxDtlsByType.put(paxType, paxDtlsList);
				}
			}
		}
		
		return paxDtlsByType;
	}
	
	private static int getAge(JSONObject paxDtlJson) {
		int age = 0;
		String dateOfBirthStr = paxDtlJson.optString(JSON_PROP_DATEOFBIRTH);
		if (dateOfBirthStr.isEmpty()) {
			return age;
		}
		
		DateFormat dtFmt = new SimpleDateFormat("yyyy-MM-dd");
		try {
			Calendar dateOfBirthCal = Calendar.getInstance();
			dateOfBirthCal.setTime(dtFmt.parse(dateOfBirthStr));
			Calendar todayDateCal = Calendar.getInstance();
			age = todayDateCal.get(Calendar.YEAR) - dateOfBirthCal.get(Calendar.YEAR);
			if ((todayDateCal.get(Calendar.MONTH) < dateOfBirthCal.get(Calendar.MONTH)) 
					|| (todayDateCal.get(Calendar.MONTH) == dateOfBirthCal.get(Calendar.MONTH) && todayDateCal.get(Calendar.DATE) < dateOfBirthCal.get(Calendar.DATE))){
				age--;
			}
			
			return age;
		}
		catch (Exception x) {
			return age;
		}
		
		
	}
}
