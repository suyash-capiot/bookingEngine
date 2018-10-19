package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.enums.PassengerType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class HolidaysCompanyOffers implements HolidayConstants {

	private static final Logger logger = LogManager.getLogger(HolidaysCompanyOffers.class);
	private static String travelDateFrom = "";
	private static String travelDateTo = "";
	private static Boolean childWithBed = false;

	public static void getCompanyOffers(JSONObject reqJson, JSONObject resJson, OffersType invocationType,CommercialsOperation op) {

		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject resHeader = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBody = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		
		BigDecimal totalFare = new BigDecimal(0);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray dynamicPackageArr = resBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE);

		//OffersConfig offConfig = HolidaysConfig.getOffersConfig();
		//CommercialTypeConfig offTypeConfig = offConfig.getOfferTypeConfig(invocationType);
		ServiceConfig offTypeConfig = HolidaysConfig.getOffersTypeConfig(invocationType);
        JSONObject breCpnyOffReqJson = new JSONObject(new JSONTokener(offTypeConfig.getRequestJSONShell()));
        
        JSONObject rootJson = breCpnyOffReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.holidays_companyoffers.withoutredemption.Root");
		
        JSONObject breHdrJson = new JSONObject();
		breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
		breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
		breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
		breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());
		rootJson.put("messageHeader", breHdrJson);
        
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
		//commonElemsJson.put(JSON_PROP_BOOKINGDATE, DATE_FORMAT.format(new Date()));
		commonElemsJson.put(JSON_PROP_BOOKINGDATE,"2017-09-22T00:00:00+05:30");
		

		//TODO: Uncomment below code when we need to pass actual tour value dates
	/*	for (int i = 0; i < dynamicPackageArr.length(); i++) {

			if (!dynamicPackageArr.getJSONObject(i).has("error")) {
				JSONObject dynamicPkgJson = dynamicPackageArr.getJSONObject(i);
				if (dynamicPkgJson.optJSONObject(JSON_PROP_GLOBALINFO).has(JSON_PROP_TIMESPAN)) {
					travelDateFrom = dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO)
							.getJSONObject(JSON_PROP_TIMESPAN).optString(JSON_PROP_TRAVELSTARTDATE);
					travelDateTo = dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TIMESPAN)
							.optString(JSON_PROP_TRAVELENDDATE);

				}
			}
		}
		convertDate(commonElemsJson, travelDateFrom, JSON_PROP_TRAVELDATEFROM);

		convertDate(commonElemsJson, travelDateTo, JSON_PROP_TRAVELDATETO);*/
		
		//TODO: Hardcoded the below values for now so that offers get applied
		commonElemsJson.put(JSON_PROP_TRAVELDATEFROM, "2017-09-25T00:00:00+05:30");
		commonElemsJson.put(JSON_PROP_TRAVELDATETO, "2017-09-29T00:00:00+05:30");
		 

		// TODO: Populate Target Set (Slabs) .Need to find out from where values will
		// come
		JSONArray targetSetArray = new JSONArray();
		JSONObject targetSetJsonPass = new JSONObject();
		JSONObject targetSetJsonRoom = new JSONObject();

		targetSetJsonPass.put(JSON_PROP_TYPE, "Number of Passengers");
		targetSetJsonPass.put(JSON_PROP_VALUE, 496);
		targetSetJsonRoom.put(JSON_PROP_TYPE, "no of rooms");
		targetSetJsonRoom.put(JSON_PROP_VALUE, 8);

		targetSetArray.put(targetSetJsonPass);
		targetSetArray.put(targetSetJsonRoom);
		commonElemsJson.put(JSON_PROP_TARGETSET, targetSetArray);

		briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);

		JSONArray prodDtlsJsonArr = new JSONArray();
		for (int i = 0; i < dynamicPackageArr.length(); i++) {

			JSONObject dynamicPkgJson = dynamicPackageArr.getJSONObject(i);
			JSONObject prodDtlsJson = new JSONObject();
			prodDtlsJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_HOLIDAYS);
			prodDtlsJson.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_HOLIDAYS);

			// TODO : packageType,flavourType and productFlavourName is hardcoded below.Need
			// to find out from where values will come.
			// WEM is supposed to pass the flag to identify online or offline(custom)
			// package for packageType.
			prodDtlsJson.put("packageType", "custom");
			prodDtlsJson.put("flavourType", "");
			prodDtlsJson.put("productFlavourName", "");
			String reqBrandName="";
			String reqDestination ="";
			
			if (reqBodyJson.has("brandName") && reqBodyJson.has("destinationLocation")) {
				reqBrandName = reqBodyJson.getString("brandName");
				reqDestination = reqBodyJson.getString("destinationLocation");
			}
			else if (reqBodyJson.has(JSON_PROP_DYNAMICPACKAGE)){
				JSONArray dynamicPkgArrReq = reqBodyJson.optJSONArray(JSON_PROP_DYNAMICPACKAGE);
				for(int a=0;a<dynamicPkgArrReq.length();a++) {
					if(dynamicPkgArrReq.optJSONObject(i).has("brandName")) {
						reqBrandName = dynamicPkgArrReq.optJSONObject(i).optString("brandName");
						JSONArray selectedOfferListJsonArray= dynamicPkgArrReq.getJSONObject(i).optJSONArray(JSON_PROP_SELECTEDOFFERLIST);
						if(selectedOfferListJsonArray!=null && op.toString().equalsIgnoreCase("Reprice") ) {
							prodDtlsJson.put(JSON_PROP_SELECTEDOFFERLIST, selectedOfferListJsonArray);
						}
					}
				}
			}

			/*
			 * if (dynamicPkgJson.has(JSON_PROP_GLOBALINFO)) { JSONObject dynamicpackageId =
			 * dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO)
			 * .getJSONObject(JSON_PROP_DYNAMICPKGID); String brandName =
			 * dynamicpackageId.get(JSON_PROP_COMPANYNAME).toString(); }
			 */

			prodDtlsJson.put("brand", reqBrandName);
			prodDtlsJson.put("destination", reqDestination);
			// TODO : country is hardcoded to India.Need to check on this.
			prodDtlsJson.put(JSON_PROP_COUNTRY, "India");

			String productName = "Amazing India";
			// TODO: ProductName is set to "Amazing India".Below code was for setting
			// tourName as productName from SI response

			/*
			 * if (dynamicpackageId.has(JSON_PROP_TPA_EXTENSIONS)) { productName =
			 * dynamicpackageId.getJSONObject(JSON_PROP_TPA_EXTENSIONS).getJSONObject(
			 * JSON_PROP_PKGS_TPA)
			 * .getJSONObject(JSON_PROP_TOURDETAILS).get(JSON_PROP_TOURNAME).toString(); }
			 */

			prodDtlsJson.put(JSON_PROP_PRODUCTNAME, productName);
			JSONObject totalJson = new JSONObject();
			// TODO : Search calculate price total fare to be set below
			if(dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TOTAL).has(JSON_PROP_AMOUNTAFTERTAX)) {
				totalJson = dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TOTAL);
			 }
			else {
				totalJson = dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TOTAL).getJSONObject("totalPackageFare");
						
			}
			totalFare = totalJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
			prodDtlsJson.put(JSON_PROP_FAREDETAILS, getFareDetailsJsonArray(totalJson));
			prodDtlsJson.put(JSON_PROP_TOTALFARE, totalFare);

			JSONArray hotelDetailsArray = new JSONArray();
			JSONObject hotelDetailsJson = new JSONObject();
			JSONArray roomDetailsArr = new JSONArray();

			if (dynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS).has(JSON_PROP_HOTEL_COMPONENT)) {

				getHotelRoomDetails(dynamicPkgJson, roomDetailsArr);
			}

			if (dynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS).has(JSON_PROP_CRUISE_COMPONENT)) {
				getCruiseRoomDetails(dynamicPkgJson, roomDetailsArr);
			}
			hotelDetailsJson.put(JSON_PROP_ROOMDETAILS, roomDetailsArr);
			hotelDetailsArray.put(hotelDetailsJson);
			prodDtlsJson.put(JSON_PROP_HOTELDETAILS, hotelDetailsArray);
			
			
			JSONArray selectedOfferListJsonArray=prodDtlsJson.optJSONArray(JSON_PROP_SELECTEDOFFERLIST);
			if(selectedOfferListJsonArray!=null && op.toString().equalsIgnoreCase("Reprice") ) {
				prodDtlsJson.put(JSON_PROP_SELECTEDOFFERLIST, selectedOfferListJsonArray);
			}
			prodDtlsJsonArr.put(prodDtlsJson);
		}

		briJson.put(JSON_PROP_PRODUCTDETAILS, prodDtlsJsonArr);

		JSONObject paymentDetailsJson = new JSONObject();
		paymentDetailsJson.put("bankName", "");
		paymentDetailsJson.put("modeOfPayment", "");
		paymentDetailsJson.put("paymentType", "");

		JSONObject cardDetails = new JSONObject();
		cardDetails.put("cardNo", "");
		cardDetails.put("cardType", "");
		cardDetails.put("nthBooking", "");
		paymentDetailsJson.put("cardDetails", cardDetails);

		briJson.put("paymentDetails", paymentDetailsJson);

		briJsonArr.put(briJson);

		JSONObject breOffResJson = null;
		try {
			//breOffResJson = HTTPServiceConsumer.consumeJSONService("BRMS/ComOffers", offTypeConfig.getServiceURL(), offConfig.getHttpHeaders(), breCpnyOffReqJson);
			breOffResJson = HTTPServiceConsumer.consumeJSONService("BRMS/ComOffers", offTypeConfig.getServiceURL(), offTypeConfig.getHttpHeaders(), breCpnyOffReqJson);
			
		} catch (Exception x) {
			logger.warn("An exception occurred when calling company offers", x);
		}

		if (BRMS_STATUS_TYPE_FAILURE.equals(breOffResJson.getString(JSON_PROP_TYPE))) {
			logger.warn(String.format("A failure response was received from Company Offers calculation engine: %s",
					breOffResJson.toString()));
			return;
		}
		
		logger.trace(String.format("Offers request: %s", breCpnyOffReqJson.toString()));
		logger.trace(String.format("Offers response: %s", breOffResJson.toString()));

		// Check offers invocation type
        if (OffersType.COMPANY_SEARCH_TIME == invocationType) {
        	if(op.toString().equalsIgnoreCase("Reprice")) {
        		appendDiscountsToResults(resBodyJson, breOffResJson);
        	}
        	else {
        		appendOffersToResults(resBodyJson, breOffResJson);
        	}       	
        }
	}

	private static void appendDiscountsToResults(JSONObject resBodyJson, JSONObject breOffResJson) {
		
		JSONArray dynamicPackageArr = resBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		// When invoking offers engine, only one businessRuleIntake is being sent. Therefore, here retrieve the first 
        // businessRuleIntake item and process results from that.
		
		 JSONArray prodDtlsJsonArr = null;
	        try {
	        	prodDtlsJsonArr = breOffResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONArray("value").getJSONObject(0).getJSONObject("cnk.holidays_companyoffers.withoutredemption.Root").getJSONArray("businessRuleIntake").getJSONObject(0).getJSONArray("productDetails");
	        }
	        catch (Exception x) {
	        	logger.warn("Unable to retrieve first businessRuleIntake item. Aborting Company Offers response processing", x);
	        	return;
	        }
	        
	        if (dynamicPackageArr.length() != prodDtlsJsonArr.length()) {
				logger.warn(
						"Number of dynamicPackage elements in BE result and number of productDetails elements in company offers result is different. Aborting Company Offers response processing.");
				return;
			}
	        
	        for (int i = 0; i < dynamicPackageArr.length(); i++) {
				JSONObject dynamicPkgJson = dynamicPackageArr.getJSONObject(i);
				JSONObject prodDtlsJson = prodDtlsJsonArr.getJSONObject(i);
				 BigDecimal totalDiscountAmount=new BigDecimal(0);
	        	 String currencyCode="";
				// Append search result level offers to search result
				JSONArray offerDetailSetArr = prodDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
				
				JSONArray discountJsonArr=new JSONArray();
	        	JSONObject discountTotalJson=new JSONObject();
	        	if(offerDetailSetArr!=null) {
	        		for(int j=0;j<offerDetailSetArr.length();j++) {
		        		JSONObject discountJson=new JSONObject();
		        		JSONObject offerDetailJson=offerDetailSetArr.getJSONObject(j);
		        		totalDiscountAmount=totalDiscountAmount.add(offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
		        		discountJson.put(JSON_PROP_DISCOUNTCODE, offerDetailJson.optString(JSON_PROP_OFFERID));
		        		discountJson.put(JSON_PROP_DISCOUNTTYPE, offerDetailJson.optString(JSON_PROP_OFFERTYPE));
		        		discountJson.put(JSON_PROP_AMOUNT, offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
		        		currencyCode=offerDetailJson.optString(JSON_PROP_CCYCODE);
		        		discountJson.put(JSON_PROP_CCYCODE, offerDetailJson.optString(JSON_PROP_CCYCODE));
		        		discountJsonArr.put(discountJson);
		        	}
	        	}
	        
	        	
	        	// Retrieve and de-duplicate offers that are at passenger level
		
			    Map<String,JSONObject> discountOfferMap=new HashMap<String,JSONObject>(); 
				JSONArray hotelDetailsArr = prodDtlsJson.optJSONArray(JSON_PROP_HOTELDETAILS);

				for (int j = 0; j < hotelDetailsArr.length(); j++) {
					
					JSONArray roomDetailsArr = hotelDetailsArr.getJSONObject(j).optJSONArray(JSON_PROP_ROOMDETAILS);
					PassengerType psgrType = null;
					for (int k = 0; k < roomDetailsArr.length(); k++) {
						JSONObject roomDetailsJson = roomDetailsArr.getJSONObject(k);
						JSONArray passengerDetailsArr = roomDetailsJson.optJSONArray(JSON_PROP_PASSENGERDETAILS);
						
						String roomType = roomDetailsJson.get(JSON_PROP_ROOMTYPE).toString();
						String roomCategory =  roomDetailsJson.get(JSON_PROP_ROOMCATEGORY).toString();
						//RoomType roomType = RoomType.forString(roomTypeStr);
						//RoomCategory roomCategory = RoomCategory.forString(roomCategoryStr);
						if (roomType == null) {
							continue;
						}
						//Offers at roomType level
						
						JSONArray roomOffersJsonArr = roomDetailsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
						
						if(roomOffersJsonArr!=null) {
	        				for(int l=0;l<roomOffersJsonArr.length();l++) {
	        					JSONObject offerDetailJson=roomOffersJsonArr.getJSONObject(l);
	        					JSONObject discountJson=new JSONObject();
	        					if(!(offerDetailJson.getString(JSON_PROP_OFFERTYPE)).equalsIgnoreCase("cashback")) {
	        						totalDiscountAmount=totalDiscountAmount.add(offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
	    	        			}
	        					if(discountOfferMap.containsKey(offerDetailJson.optString(JSON_PROP_OFFERTYPE)+"|"+offerDetailJson.optString(JSON_PROP_OFFERID))) {
	    	        				JSONObject discountMapJson=discountOfferMap.get(offerDetailJson.optString(JSON_PROP_OFFERTYPE)+"|"+offerDetailJson.optString(JSON_PROP_OFFERID));
	    	        				BigDecimal addedDiscountJson=(discountMapJson.getBigDecimal(JSON_PROP_AMOUNT)).add(offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
	    	        				discountMapJson.put(JSON_PROP_AMOUNT, addedDiscountJson);
	    	        				continue;
	    	        			}
	        					
	        					discountJson.put(JSON_PROP_DISCOUNTCODE, offerDetailJson.optString(JSON_PROP_OFFERID));
	    		        		discountJson.put(JSON_PROP_DISCOUNTTYPE, offerDetailJson.optString(JSON_PROP_OFFERTYPE));
	    		        		discountJson.put(JSON_PROP_AMOUNT, offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
	    		        		currencyCode=offerDetailJson.optString(JSON_PROP_CCYCODE);
	    		        		discountJson.put(JSON_PROP_CCYCODE, offerDetailJson.optString(JSON_PROP_CCYCODE));
	    		        		discountOfferMap.put(offerDetailJson.optString(JSON_PROP_OFFERTYPE)+"|"+offerDetailJson.optString(JSON_PROP_OFFERID),discountJson);
	        				}
					
					}
						for (int l = 0; l < passengerDetailsArr.length(); l++) {
							JSONObject passengerDetailsJson = passengerDetailsArr.getJSONObject(l);
							String passengerType = passengerDetailsJson.getString(JSON_PROP_PASSENGERTYPE);
							psgrType =  PassengerType.forString(passengerType);
							
						
							if (psgrType == null) {
								continue;
							}
							//Offers at passenger level
						
							JSONArray psgrOffersJsonArr = passengerDetailsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
							
							if(psgrOffersJsonArr!=null) {
		        				for(int p=0;p<psgrOffersJsonArr.length();p++) {
		        					JSONObject offerDetailJson=psgrOffersJsonArr.getJSONObject(p);
		        					JSONObject discountJson=new JSONObject();
		        					if(!(offerDetailJson.getString(JSON_PROP_OFFERTYPE)).equalsIgnoreCase("cashback")) {
		        						totalDiscountAmount=totalDiscountAmount.add(offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
		    	        			}
		        					if(discountOfferMap.containsKey(offerDetailJson.optString(JSON_PROP_OFFERTYPE)+"|"+offerDetailJson.optString(JSON_PROP_OFFERID))) {
		    	        				JSONObject discountMapJson=discountOfferMap.get(offerDetailJson.optString(JSON_PROP_OFFERTYPE)+"|"+offerDetailJson.optString(JSON_PROP_OFFERID));
		    	        				BigDecimal addedDiscountJson=(discountMapJson.getBigDecimal(JSON_PROP_AMOUNT)).add(offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
		    	        				discountMapJson.put(JSON_PROP_AMOUNT, addedDiscountJson);
		    	        				continue;
		    	        			}
		        					
		        					discountJson.put(JSON_PROP_DISCOUNTCODE, offerDetailJson.optString(JSON_PROP_OFFERID));
		    		        		discountJson.put(JSON_PROP_DISCOUNTTYPE, offerDetailJson.optString(JSON_PROP_OFFERTYPE));
		    		        		discountJson.put(JSON_PROP_AMOUNT, offerDetailJson.getBigDecimal(JSON_PROP_OFFERAMOUNT));
		    		        		currencyCode=offerDetailJson.optString(JSON_PROP_CCYCODE);
		    		        		discountJson.put(JSON_PROP_CCYCODE, offerDetailJson.optString(JSON_PROP_CCYCODE));
		    		        		discountOfferMap.put(offerDetailJson.optString(JSON_PROP_OFFERTYPE)+"|"+offerDetailJson.optString(JSON_PROP_OFFERID),discountJson);
		        				}
						
						}
						}
					}}
				discountOfferMap.forEach((key,discountJson) ->discountJsonArr.put(discountJson));
				discountTotalJson.put(JSON_PROP_AMOUNT, totalDiscountAmount);
	        	discountTotalJson.put(JSON_PROP_CCYCODE, currencyCode);
	        	discountTotalJson.put(JSON_PROP_DISCOUNT, discountJsonArr);
	        	JSONObject totalFareJson = dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TOTAL).getJSONObject("totalPackageFare");
	        	totalFareJson.put(JSON_PROP_DISCOUNTS, discountTotalJson);
	        	
	        	BigDecimal packageTotalPrice=totalFareJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
	        	packageTotalPrice=packageTotalPrice.subtract(totalDiscountAmount);
	        	totalFareJson.put(JSON_PROP_AMOUNTAFTERTAX, packageTotalPrice);
	        }
	}

	private static void getCruiseRoomDetails(JSONObject dynamicPkgJson, JSONArray roomDetailsArr) {
		JSONObject cruiseCompJson = dynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS)
				.getJSONObject(JSON_PROP_CRUISE_COMPONENT);

		if (cruiseCompJson != null && cruiseCompJson.length() > 0) {
			String dynamicPkgAction = cruiseCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
			if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_CRUISE_TOUR)) {
				JSONArray categoryOptionsArr = cruiseCompJson.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
				for (int k = 0; k < categoryOptionsArr.length(); k++) {
					JSONObject categoryOptionsJson = categoryOptionsArr.getJSONObject(k);
					JSONArray categoryOptionArr = categoryOptionsJson.getJSONArray(JSON_PROP_CATEGORYOPTION);

					for (int j = 0; j < categoryOptionArr.length(); j++) {
						JSONObject categoryOptionJson = categoryOptionArr.getJSONObject(j);
						JSONObject roomDetails = new JSONObject();
						String cabinType = categoryOptionJson.getString(JSON_PROP_CABINTYPE);
						String cabinCategory = categoryOptionJson.getString(JSON_PROP_CABINCATEGORY);
						roomDetails.put(JSON_PROP_ROOMTYPE, cabinType);
						roomDetails.put(JSON_PROP_ROOMCATEGORY, cabinCategory);
						roomDetails.put(JSON_PROP_PASSENGERDETAILS,
								getOffersCruisePassengerDetailsJSON(categoryOptionJson));
						roomDetailsArr.put(roomDetails);
					}
				}
			}
		}
	}

	private static void getHotelRoomDetails(JSONObject dynamicPkgJson, JSONArray roomDetailsArr) {
		JSONObject hotelCompJson = dynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS)
				.getJSONObject(JSON_PROP_HOTEL_COMPONENT);

		if (hotelCompJson != null && hotelCompJson.length() > 0) {
			String dynamicPkgAction = hotelCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
			if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_HOTEL_TOUR)) {
				JSONArray roomStayArr = hotelCompJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);

				for (int j = 0; j < roomStayArr.length(); j++) {
					JSONObject roomDetails = new JSONObject();
					JSONObject roomStayJson = roomStayArr.getJSONObject(j);
					JSONArray roomrateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);
					String roomType = roomStayJson.get(JSON_PROP_ROOMTYPE).toString();
					roomDetails.put(JSON_PROP_ROOMTYPE, roomType);
					for (int k = 0; k < roomrateArr.length(); k++) {
						JSONObject roomRateJson = roomrateArr.getJSONObject(k);
						String roomCategory = roomRateJson.optString("ratePlanCategory");
						roomDetails.put(JSON_PROP_ROOMCATEGORY, roomCategory);
					}

					roomDetails.put(JSON_PROP_PASSENGERDETAILS, getOffersPassengerDetailsJSON(roomStayJson));

					roomDetailsArr.put(roomDetails);
				}
			}
		}
	}

	private static void appendOffersToResults(JSONObject resBodyJson, JSONObject offResJson) {

		JSONArray dynamicPackageArr = resBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		PassengerType psgrType = null;
		// When invoking offers engine, only one businessRuleIntake is being sent.
		// Therefore, here retrieve the first businessRuleIntake item and process results from that.
		JSONArray prodDtlsJsonArr = null;
		try {
			prodDtlsJsonArr = offResJson.getJSONObject("result").getJSONObject("execution-results")
					.getJSONArray("results").getJSONObject(0).getJSONArray("value").getJSONObject(0)
					.getJSONObject("cnk.holidays_companyoffers.withoutredemption.Root")
					.getJSONArray("businessRuleIntake").getJSONObject(0).getJSONArray("productDetails");
		} catch (Exception x) {
			logger.warn("Unable to retrieve first businessRuleIntake item. Aborting Company Offers response processing",
					x);
			return;
		}

		if (dynamicPackageArr.length() != prodDtlsJsonArr.length()) {
			logger.warn(
					"Number of dynamicPackage elements in BE result and number of productDetails elements in company offers result is different. Aborting Company Offers response processing.");
			return;
		}

		for (int i = 0; i < dynamicPackageArr.length(); i++) {
			JSONObject dynamicPkgJson = dynamicPackageArr.getJSONObject(i);
			JSONObject prodDtlsJson = prodDtlsJsonArr.getJSONObject(i);

			// Append search result level offers to search result
			JSONArray offersJsonArr = prodDtlsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
			if (offersJsonArr != null) {
				dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).put(JSON_PROP_OFFERS, offersJsonArr);
			}
			// Retrieve and de-duplicate offers that are at passenger level
			Map<PassengerType, Map<String, JSONObject>> psgrOffers = new LinkedHashMap<PassengerType, Map<String, JSONObject>>();
			
			Map<String,Map<String,JSONObject>> roomTypeOffers = new LinkedHashMap<String,Map<String,JSONObject>>();
			Map<String,Map<String,JSONObject>> roomCatOffers = new LinkedHashMap<String,Map<String,JSONObject>>();
			
			JSONArray hotelDetailsArr = prodDtlsJson.optJSONArray(JSON_PROP_HOTELDETAILS);

			for (int j = 0; j < hotelDetailsArr.length(); j++) {
				
				JSONArray roomDetailsArr = hotelDetailsArr.getJSONObject(j).optJSONArray(JSON_PROP_ROOMDETAILS);
				
				for (int k = 0; k < roomDetailsArr.length(); k++) {
					JSONObject roomDetailsJson = roomDetailsArr.getJSONObject(k);
					JSONArray passengerDetailsArr = roomDetailsJson.optJSONArray(JSON_PROP_PASSENGERDETAILS);
					
					String roomType = roomDetailsJson.get(JSON_PROP_ROOMTYPE).toString();
					String roomCategory =  roomDetailsJson.get(JSON_PROP_ROOMCATEGORY).toString();
					//RoomType roomType = RoomType.forString(roomTypeStr);
					//RoomCategory roomCategory = RoomCategory.forString(roomCategoryStr);
					if (roomType == null) {
						continue;
					}
					//Offers at roomType level
					Map<String, JSONObject> roomTypeCtgOffers = (roomTypeOffers.containsKey(roomType))? roomTypeOffers.get(roomType): new LinkedHashMap<String, JSONObject>();
					JSONArray roomOffersJsonArr = roomDetailsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
					if (roomOffersJsonArr!= null) {
						for (int m = 0; m < roomOffersJsonArr.length(); m++) {
						JSONObject roomOfferJson = roomOffersJsonArr.getJSONObject(m);
						String offerId = roomOfferJson.getString(JSON_PROP_OFFERID);
						roomTypeCtgOffers.put(offerId, roomOfferJson);
					}
						roomTypeOffers.put(roomType, roomTypeCtgOffers);
						//Offers at roomCategory
						if(!roomTypeOffers.containsKey(roomType)) {
							roomCatOffers.put(roomCategory, roomTypeCtgOffers);
						}
					}
				
						
					for (int l = 0; l < passengerDetailsArr.length(); l++) {
						JSONObject passengerDetailsJson = passengerDetailsArr.getJSONObject(l);
						String passengerType = passengerDetailsJson.getString(JSON_PROP_PASSENGERTYPE);
						psgrType =  PassengerType.forString(passengerType);
						
					
						if (psgrType == null) {
							continue;
						}
						//Offers at passenger level
						Map<String, JSONObject> psgrTypeOffers = (psgrOffers.containsKey(psgrType))
								? psgrOffers.get(psgrType)
								: new LinkedHashMap<String, JSONObject>();
						JSONArray psgrOffersJsonArr = passengerDetailsJson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
						if (psgrOffersJsonArr == null) {
							continue;
						}
						for (int m = 0; m < psgrOffersJsonArr.length(); m++) {
							JSONObject psgrOfferJson = psgrOffersJsonArr.getJSONObject(m);
							String offerId = psgrOfferJson.getString(JSON_PROP_OFFERID);
							psgrTypeOffers.put(offerId, psgrOfferJson);
						}
						psgrOffers.put(psgrType, psgrTypeOffers);
					}
				}
				
				
					JSONObject componentJson = dynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS);
					//for Hotel component
					if (componentJson.has(JSON_PROP_HOTEL_COMPONENT)) {

						appendOffersHotelComp(psgrOffers, roomTypeOffers, roomCatOffers, componentJson);
					}
					// for Cruise component
					if (componentJson.has(JSON_PROP_CRUISE_COMPONENT)) {
						
						appendOffersCruiseComp(psgrOffers, roomTypeOffers, roomCatOffers, componentJson);}

			}

		}

	}

	private static void appendOffersHotelComp(Map<PassengerType, Map<String, JSONObject>> psgrOffers,
			Map<String, Map<String, JSONObject>> roomTypeOffers, Map<String, Map<String, JSONObject>> roomCatOffers,
			JSONObject componentJson) {
		PassengerType psgrType;
		JSONObject hotelCompJson = componentJson.getJSONObject(JSON_PROP_HOTEL_COMPONENT);

		if (hotelCompJson != null && hotelCompJson.length() > 0) {
			String dynamicPkgAction = hotelCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
			if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_HOTEL_TOUR)) {
				JSONArray roomStayArr = hotelCompJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);

				for (int a = 0; a < roomStayArr.length(); a++) {
					JSONObject roomStayJson = roomStayArr.getJSONObject(a);
					//Appending room level offers for roomType --start
					String roomTypeStr = roomStayJson.get(JSON_PROP_ROOMTYPE).toString();
					appendRoomTypeOffers(roomTypeOffers, roomStayJson,roomTypeStr);
					
					//Appending room level offers for roomType --end
					
					JSONArray roomrateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);
					for (int b = 0; b < roomrateArr.length(); b++) {
						JSONObject roomRateJson = roomrateArr.getJSONObject(b);
						
						//Appending room level offers for roomCategory --start
						appendRoomCategoryOffers(roomCatOffers, roomStayJson,
								roomRateJson);
						//Appending room level offers for roomCategory --end
						
						String passengerType = roomRateJson.getJSONObject(JSON_PROP_TOTAL)
								.getString(JSON_PROP_TYPE);

						// if passengerType is not specified then consider Adult by default for offers
						
						psgrType = PassengerType.forString(passengerType);
					
						if (psgrType == null) {
							continue;
						}

						JSONArray psgrTypeOffsJsonArr = new JSONArray();
						Map<String, JSONObject> psgrTypeOffers = psgrOffers.get(psgrType);
						if (psgrTypeOffers == null) {
							continue;
						}

						Collection<JSONObject> psgrTypeOffersColl = psgrTypeOffers.values();
						for (JSONObject psgrTypeOffer : psgrTypeOffersColl) {
							psgrTypeOffsJsonArr.put(psgrTypeOffer);
						}
						roomRateJson.getJSONObject(JSON_PROP_TOTAL).put(JSON_PROP_OFFERS, psgrTypeOffsJsonArr);

					}
				}
			}
		}
	}

	private static void appendOffersCruiseComp(Map<PassengerType, Map<String, JSONObject>> psgrOffers,
			Map<String, Map<String, JSONObject>> roomTypeOffers, Map<String, Map<String, JSONObject>> roomCatOffers,
			JSONObject componentJson) {
		PassengerType psgrType;
		JSONObject cruiseCompJson = componentJson.getJSONObject(JSON_PROP_CRUISE_COMPONENT);

		if (cruiseCompJson != null && cruiseCompJson.length() > 0) {
			String dynamicPkgAction = cruiseCompJson.getString(JSON_PROP_DYNAMICPKGACTION);

			if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_CRUISE_TOUR)) {

				JSONArray categoryOptionsArr = cruiseCompJson.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
				for (int c = 0; c < categoryOptionsArr.length(); c++) {
					JSONObject categoryOptionsJson = categoryOptionsArr.getJSONObject(c);
					JSONArray categoryOptionArr = categoryOptionsJson.getJSONArray(JSON_PROP_CATEGORYOPTION);

					for (int d = 0; d < categoryOptionArr.length(); d++) {
						JSONObject categoryOptionJson = categoryOptionArr.getJSONObject(d);

						// For applying room level offers in Cruise --changed Type to CabinType
						String roomTypeStr = categoryOptionJson.get(JSON_PROP_CABINTYPE).toString();
						String cabinCategory = categoryOptionJson.getString(JSON_PROP_CABINCATEGORY);
						appendRoomTypeOffers(roomTypeOffers, categoryOptionJson, roomTypeStr);
						// Appending room level offers for roomCategory --start
						appendCruiseCategoryOffers(roomCatOffers, categoryOptionJson, cabinCategory);
						// Appending room level offers for roomCategory --end
						JSONArray totalArr = categoryOptionJson.getJSONArray(JSON_PROP_TOTAL);
						for (int e = 0; e < totalArr.length(); e++) {
							JSONObject totalJson = totalArr.getJSONObject(e);
							String passengerType = totalJson.getString(JSON_PROP_TYPE);

							psgrType = PassengerType.forString(passengerType);

							if (psgrType == null) {
								continue;
							}

							JSONArray psgrTypeOffsJsonArr = new JSONArray();
							Map<String, JSONObject> psgrTypeOffers = psgrOffers.get(psgrType);
							if (psgrTypeOffers == null) {
								continue;
							}

							Collection<JSONObject> psgrTypeOffersColl = psgrTypeOffers.values();
							for (JSONObject psgrTypeOffer : psgrTypeOffersColl) {
								psgrTypeOffsJsonArr.put(psgrTypeOffer);
							}
							totalJson.put(JSON_PROP_OFFERS, psgrTypeOffsJsonArr);

						}

					}
				}
			}
		}
	}

	private static void appendRoomTypeOffers(Map<String, Map<String, JSONObject>> roomTypeOffers,
			JSONObject roomStayOrCabinOptionJson, String roomType) {
		
		
		//RoomType roomType = RoomType.forString(roomTypeStr);
		
		if (roomType!= null) {
			JSONArray roomTypeOffsJsonArr = new JSONArray();
			Map<String, JSONObject> roomTypeCtgOffers = roomTypeOffers.get(roomType);
			if (roomTypeCtgOffers!= null) {
				Collection<JSONObject> roomTypeOffersColl = roomTypeCtgOffers.values();
				for (JSONObject roomTypeOffer : roomTypeOffersColl) {
					roomTypeOffsJsonArr.put(roomTypeOffer);
				}
				roomStayOrCabinOptionJson.put(JSON_PROP_OFFERS, roomTypeOffsJsonArr);
			}
		}
	}

	private static void appendRoomCategoryOffers(Map<String, Map<String, JSONObject>> roomCatOffers,
			JSONObject roomStayJson, JSONObject roomRateJson) {
		String roomCategory = roomRateJson.optString("ratePlanCategory");
		
		//RoomCategory roomCategory = RoomCategory.forString(roomCategoryStr);
		
		if (roomCategory!=null) {
			JSONArray roomCtgOffsJsonArr = new JSONArray();
			
			Map<String, JSONObject> roomCatCtgOffers = roomCatOffers.get(roomCategory);
			if (roomCatCtgOffers!=null) {
				Collection<JSONObject> roomTypeOffersColl = roomCatCtgOffers.values();
				for (JSONObject roomTypeOffer : roomTypeOffersColl) {
					roomCtgOffsJsonArr.put(roomTypeOffer);
				}
				roomStayJson.put(JSON_PROP_OFFERS, roomCtgOffsJsonArr);
			}
			
		}
	}
	
	private static void appendCruiseCategoryOffers(Map<String, Map<String, JSONObject>> roomCatOffers,
			JSONObject categoryOptionJson, String cabinCategory) {
		String cabinCategoryStr = categoryOptionJson.optString(JSON_PROP_CABINCATEGORY);
		
		if (cabinCategoryStr!=null) {
			JSONArray roomCtgOffsJsonArr = new JSONArray();
			
			Map<String, JSONObject> roomCatCtgOffers = roomCatOffers.get(cabinCategoryStr);
			if (roomCatCtgOffers!=null) {
				Collection<JSONObject> roomTypeOffersColl = roomCatCtgOffers.values();
				for (JSONObject roomTypeOffer : roomTypeOffersColl) {
					roomCtgOffsJsonArr.put(roomTypeOffer);
				}
				categoryOptionJson.put(JSON_PROP_OFFERS, roomCtgOffsJsonArr);
			}
			
		}
	}

	private static JSONArray getOffersCruisePassengerDetailsJSON(JSONObject categoryOptionJson) {
		JSONArray passengerDetailsArr = new JSONArray();
		JSONObject paxDetail = new JSONObject();

		JSONArray totalArr = categoryOptionJson.getJSONArray(JSON_PROP_TOTAL);

		for (int i = 0; i < totalArr.length(); i++) {
			JSONObject totalJson = totalArr.getJSONObject(i);

			String type = totalJson.get(JSON_PROP_TYPE).toString();
			 
		    paxDetail.put(JSON_PROP_PASSENGERTYPE, type);
			
			// TODO : We do not get "childWithBed" indicator from supplier.Need to check
			// when will this be true.
			paxDetail.put("childWithBed", childWithBed);
			// TODO : age and gender is blank as we do not get these details in Holidays
			// Search
			//paxDetail.put("age", 0);
			paxDetail.put("gender", "");
			BigDecimal totalFare = totalJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
			paxDetail.put(JSON_PROP_TOTALFARE, totalFare);
			paxDetail.put(JSON_PROP_FAREDETAILS, getFareDetailsJsonArray(totalJson));
			passengerDetailsArr.put(paxDetail);
		}

		return passengerDetailsArr;
	}

	private static JSONArray getOffersPassengerDetailsJSON(JSONObject roomStayJson) {
		JSONArray passengerDetailsArr = new JSONArray();
		JSONArray roomrateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);

		for (int i = 0; i < roomrateArr.length(); i++) {
			JSONObject paxDetail = new JSONObject();
			JSONObject roomRateJson = roomrateArr.getJSONObject(i);
			JSONObject totalJson = roomRateJson.getJSONObject(JSON_PROP_TOTAL);
			String type = roomRateJson.getJSONObject(JSON_PROP_TOTAL).get(JSON_PROP_TYPE).toString();

			paxDetail.put(JSON_PROP_PASSENGERTYPE, type);
			
			// TODO : We do not get "childWithBed" indicator from supplier.Need to check
			// when will this be true.
			paxDetail.put("childWithBed", childWithBed);
			// TODO : age and gender is blank as we do not get these details in Holidays
			// Search
			//paxDetail.put("age", 0);
			paxDetail.put("gender", "");
			BigDecimal totalFare = roomRateJson.getJSONObject(JSON_PROP_TOTAL).getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
			paxDetail.put(JSON_PROP_TOTALFARE, totalFare);

			paxDetail.put(JSON_PROP_FAREDETAILS, getFareDetailsJsonArray(totalJson));

			passengerDetailsArr.put(paxDetail);

		}
		return passengerDetailsArr;
	}

	private static JSONArray getFareDetailsJsonArray(JSONObject totalJson) {
		JSONArray fareDtlsJsonArr = new JSONArray();
		JSONObject fareDtlsJson = new JSONObject();
		BigDecimal baseFare = totalJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX);
		fareDtlsJson.put(JSON_PROP_FARENAME, JSON_VAL_BASE);
		fareDtlsJson.put(JSON_PROP_FAREVAL, baseFare);
		fareDtlsJsonArr.put(fareDtlsJson);

		JSONObject taxesJson = totalJson.optJSONObject(JSON_PROP_TAXES);
		if(taxesJson!=null) {
			JSONArray taxJsonArr = taxesJson.optJSONArray(JSON_PROP_TAX);
			for (int j = 0; j < taxJsonArr.length(); j++) {
				JSONObject taxJson = taxJsonArr.getJSONObject(j);
				fareDtlsJson = new JSONObject();
				fareDtlsJson.put(JSON_PROP_FARENAME, taxJson.getString(JSON_PROP_TAXDESCRIPTION));
				fareDtlsJson.put(JSON_PROP_FAREVAL, taxJson.getBigDecimal(JSON_PROP_AMOUNT));
				fareDtlsJsonArr.put(fareDtlsJson);
			}
		}

		return fareDtlsJsonArr;
	}

	private static void convertDate(JSONObject commonElemsJson, String travelDate, String travelDateStr) {
		if ((!travelDate.equals("") || !travelDate.isEmpty())) {

			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
			try {
				Date date = formatter.parse(travelDate);

				commonElemsJson.put(travelDateStr, mDateFormat.format(date));

			} catch (ParseException e) {

				e.printStackTrace();
			}
		} else {
			commonElemsJson.put(travelDateStr, "");
		}
	}

}
