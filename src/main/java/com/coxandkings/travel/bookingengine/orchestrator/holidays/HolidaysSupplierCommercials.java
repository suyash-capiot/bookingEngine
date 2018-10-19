package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class HolidaysSupplierCommercials implements HolidayConstants {

	private static final Logger logger = LogManager.getLogger(HolidaysSupplierCommercials.class);
	private static SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");
	private static String brandname = null;
	private static double totalAirAmountAfterTax = 0;
	private static double totalAirAmountBeforeTax = 0;

	private static Map<String, ArrayList<String>> roomMap = new HashMap<String, ArrayList<String>>();
	private static Map<String, Double> airTaxMap = new HashMap<String, Double>();

	public static JSONObject getSupplierCommercials(JSONObject req, JSONObject res, CommercialsOperation operationName) {
		Map<String, JSONObject> bussRuleIntakeBySupp = new HashMap<String, JSONObject>();
		//CommercialsConfig commConfig = HolidaysConfig.getCommercialsConfig();
		//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		ServiceConfig commTypeConfig = HolidaysConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		JSONObject breSuppHolidayReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));

		JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);

		JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);

		JSONObject breHdrJson = new JSONObject();
		breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
		breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
		breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));

		//String opName = convertOperationName(operationName);
		breHdrJson.put(JSON_PROP_OPERATIONNAME, operationName);

		JSONObject rootJson = breSuppHolidayReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object")
				.getJSONObject("cnk.holidays_commercialscalculationengine.suppliertransactionalrules.Root");
		rootJson.put(JSON_PROP_HEADER, breHdrJson);

		JSONArray dynamicPackageArr = resBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);

		JSONArray briJsonArr = new JSONArray();
		JSONArray packageDetailsArr = null;
		for (int i = 0; i < dynamicPackageArr.length(); i++) {

			if (!dynamicPackageArr.getJSONObject(i).has("errorSource")) { //13-04-18 Added to handle the scenario when SI errors out (Rahul)
				JSONObject dynamicPkgJson = dynamicPackageArr.getJSONObject(i);
				String suppID = dynamicPkgJson.getString(JSON_PROP_SUPPLIERID);
				JSONObject briJson = null;
				if (bussRuleIntakeBySupp.containsKey(suppID)) {
					briJson = bussRuleIntakeBySupp.get(suppID);
				} else {
					briJson = createBusinessRuleIntakeForSupplier(reqHeader, reqBody, resHeader, resBody,
							dynamicPkgJson, operationName);
					bussRuleIntakeBySupp.put(suppID, briJson);
				}

				packageDetailsArr = briJson.getJSONArray(JSON_PROP_PACKAGEDETAILS);
				packageDetailsArr.put(getBRMSpackageDetailsJSON(dynamicPkgJson, operationName, reqBody));
			}
		}
		Iterator<Map.Entry<String, JSONObject>> briEntryIter = bussRuleIntakeBySupp.entrySet().iterator();
		while (briEntryIter.hasNext()) {
			Map.Entry<String, JSONObject> briEntry = briEntryIter.next();
			briJsonArr.put(briEntry.getValue());
		}

		rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);

		JSONObject breSuppHolidayResJson = new JSONObject();
		if(rootJson.getJSONArray(JSON_PROP_BUSSRULEINTAKE).length()>0) {
		try {
			logger.info(String.format("Supplier Commercial Request = %s", breSuppHolidayReqJson.toString()));
			breSuppHolidayResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breSuppHolidayReqJson);
			logger.info(String.format("Supplier Commercial Response = %s", breSuppHolidayResJson.toString()));
		} catch (Exception x) {
			logger.warn("An exception occurred when calling supplier commercials", x);
		}
		return breSuppHolidayResJson;
	}else {
		return breSuppHolidayResJson.put("error", "Empty BRI");}
	}

	private static String convertOperationName(String operationName) {

		if (operationName.equalsIgnoreCase("addservice")) {
			operationName = "AddService";
		} else {
			if (operationName.equalsIgnoreCase("getDetails")) {
				operationName = "Reprice";
			} else {
				operationName = operationName.substring(0, 1).toUpperCase() + operationName.substring(1).toLowerCase();
			}

		}

		return operationName;
	}

	private static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqBody,
			JSONObject resHeader, JSONObject resBody, JSONObject dynamicPkgJson, CommercialsOperation operationName) {

		JSONObject briJson = new JSONObject();

		JSONObject advancedDefinition = new JSONObject();

		// TODO :advancedDefinition object is hardcoded value below.Where will we get it
		// from?
		advancedDefinition.put(JSON_PROP_CLIENTNATIONALITY, "Indian");
		advancedDefinition.put("connectivitySupplierType", "LCC");
		advancedDefinition.put("connectivitySupplier", "CSN1");
		// TODO: credentialsName set as SupplierID value
		String suppID = dynamicPkgJson.getString(JSON_PROP_SUPPLIERID);
		advancedDefinition.put("credentialsName", suppID);

		JSONObject commonElemsJson = new JSONObject();
		commonElemsJson.put("supplier", suppID);
		// TODO: Supplier market is hard-coded below. Where will this come from? This
		// should be ideally come from supplier credentials.
		commonElemsJson.put("supplierMarket", "India");
		commonElemsJson.put("productCategory", "Holidays");
		// TODO: Contract validity and productCategorySubType is hard-coded below. Where
		// will this come from?
		commonElemsJson.put("contractValidity", "2016-11-11T00:00:00");
		commonElemsJson.put("productCategorySubType", "FIT");
		JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		commonElemsJson.put("clientType", clientCtx.getString("clientType"));
		// TODO: Properties for clientGroup, clientName are not yet set. Are these
		// required for B2C? What will be BRMS behavior if these properties are not
		// sent.

		// for slabDetails
		// TODO :slabType values need to confirm from where it will come. Now hard-coded
		// to greater than 200
		JSONArray slabDetailsJsonArray = new JSONArray();
		JSONObject slabDetailsJson = new JSONObject();

		slabDetailsJson.put("slabType", "NumberOfBookings");
		slabDetailsJson.put("slabTypeValue", 350);
		slabDetailsJsonArray.put(slabDetailsJson);

		briJson.put("slabDetails", slabDetailsJsonArray);
		briJson.put("advancedDefinition", advancedDefinition);
		briJson.put("commonElements", commonElemsJson);

		JSONArray packageDetailsArr = new JSONArray();

		// JSONObject packageDetailJson = dynamicPkgJson;
		// packageDetailsArr.put(getBRMSpackageDetailsJSON(packageDetailJson,
		// operationName, reqBody));

		briJson.put("packageDetails", packageDetailsArr);

		return briJson;
	}

	private static JSONObject getBRMSpackageDetailsJSON(JSONObject dynamicPkgJson, CommercialsOperation operationName,
			JSONObject reqBody) {

		JSONArray applicableOnProductArr = new JSONArray();
		String productName = "Amazing India";
		String travelDate = "";

		if (dynamicPkgJson.has(JSON_PROP_GLOBALINFO)) {
			JSONObject dynamicpackageId = dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO)
					.getJSONObject(JSON_PROP_DYNAMICPKGID);
			// TODO: ProductName is set to "Amazing India".Below code was for setting
			// tourName as productName from SI response

			/*
			 * if (dynamicpackageId.has(JSON_PROP_TPA_EXTENSIONS)) { productName =
			 * dynamicpackageId.getJSONObject(JSON_PROP_TPA_EXTENSIONS).getJSONObject(
			 * JSON_PROP_PKGS_TPA)
			 * .getJSONObject(JSON_PROP_TOURDETAILS).get(JSON_PROP_TOURNAME).toString(); }
			 */

			brandname = dynamicpackageId.get(JSON_PROP_COMPANYNAME).toString();

			if (dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).has(JSON_PROP_TIMESPAN)) {
				travelDate = dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TIMESPAN)
						.optString(JSON_PROP_TRAVELSTARTDATE);
			}
		}
		JSONObject packageDetail = new JSONObject();

		packageDetail.put(JSON_PROP_PRODUCTNAME, productName);
		// TODO
		// :productFlavorName,flavorType,productType,salesDate,tourType
		// HARDCODED set as "".Need to find from where values will come
		// below four values not in updated request
		packageDetail.put("productFlavorName", "");
		packageDetail.put("flavorType", "");
		packageDetail.put("productType", "");
		packageDetail.put("brandName", brandname);

		// Reprice Started---
		
		if (operationName.toString().equalsIgnoreCase("Reprice")||(operationName.toString().equalsIgnoreCase("Book"))){

			//getBRMSpkgDetailsRepriceV1(dynamicPkgJson, reqBody, applicableOnProductArr);
			
			//Uncomment below method for the new SI reprice response changes
			
			getBRMSpkgDetailsRepriceV2(dynamicPkgJson, reqBody, applicableOnProductArr);
		}

		// For AddService --start
		if (operationName.toString().equalsIgnoreCase("AddService")) {

			JSONArray airItineraryPricingInfoArray = dynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS)
					.getJSONObject(JSON_PROP_AIR_COMPONENT).getJSONArray(JSON_PROP_AIRITINERARYPRICINGINFO);

			for (int i = 0; i < airItineraryPricingInfoArray.length(); i++) {
				JSONObject airItineraryPricingInfoJson = airItineraryPricingInfoArray.getJSONObject(i);

				JSONObject applicableOnProductJson = getAirItineraryPricingInfo(airItineraryPricingInfoJson);

				applicableOnProductArr.put(applicableOnProductJson);

			}

		}
		// For AddService --end
		packageDetail.put("applicableOnProducts", applicableOnProductArr);
		if (!travelDate.equals("") || !travelDate.isEmpty()) {

			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
			try {
				Date date = formatter.parse(travelDate);
				packageDetail.put("travelDate", mDateFormat.format(date));

			} catch (ParseException e) {

				e.printStackTrace();
			}
		} else {
			packageDetail.put("travelDate", "");
		}
		packageDetail.put("salesDate", "");
		packageDetail.put("tourType", "");
		packageDetail.put("bookingType", "Online");

		packageDetail.put("roomDetails", getBRMSroomDetailsJSON(dynamicPkgJson, operationName));

		return packageDetail;
	}

	private static void getBRMSpkgDetailsRepriceV2(JSONObject dynamicPkgJson, JSONObject reqBody,
			JSONArray applicableOnProductArr) {
		
		
		JSONObject componentJson = dynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS);
		String productName ="";
		String roomType = "";
		String roomCategory= "";
		JSONObject extNightJson = new JSONObject();
		//String dynamicPkgAction ="";
		//For PostNight and Prenight   ----start
		
		//For Hotel component  ---start
		if (componentJson.has(JSON_PROP_HOTEL_COMPONENT)) {
			if (componentJson.has(JSON_PROP_PRENIGHT)) {
				productName = "PreNight";
				extNightJson = componentJson.getJSONObject(JSON_PROP_PRENIGHT);
				if (extNightJson != null && extNightJson.length() > 0) {
				getApplicableOnHotelExtensionNights(applicableOnProductArr, extNightJson,productName);
			     } 
			}
			if (componentJson.has(JSON_PROP_POSTNIGHT)) {
				
				productName = "PostNight";
				extNightJson = componentJson.getJSONObject(JSON_PROP_POSTNIGHT);
				if (extNightJson != null && extNightJson.length() > 0) {
				getApplicableOnHotelExtensionNights(applicableOnProductArr, extNightJson,productName);
				}
				
			}
			
		}
			
         //For Hotel component  --- end
         
         //For Cruise component --postnight and prenight  -- start
         
		if (componentJson.has(JSON_PROP_CRUISE_COMPONENT)) {
			if (componentJson.has(JSON_PROP_PRENIGHT)) {
				productName = "PreNight";
				extNightJson = componentJson.getJSONObject(JSON_PROP_PRENIGHT);
				if (extNightJson != null && extNightJson.length() > 0) {
				//getApplicableOnCruiseExtNights(applicableOnProductArr, productName, extNightJson);
				getApplicableOnHotelExtensionNights(applicableOnProductArr, extNightJson,productName);
				}
			}
			
			if (componentJson.has(JSON_PROP_POSTNIGHT)) {
				productName = "PostNight";
				extNightJson = componentJson.getJSONObject(JSON_PROP_POSTNIGHT);
				if (extNightJson != null && extNightJson.length() > 0) {
				//getApplicableOnCruiseExtNights(applicableOnProductArr, productName, extNightJson);
				getApplicableOnHotelExtensionNights(applicableOnProductArr, extNightJson,productName);
				}
			}
		}
		
		  //For Cruise component --postnight and prenight  -- end
		
		//For PostNight and Prenight   ---- end
         
		//For Transfer component---start
		if (componentJson.has(JSON_PROP_TRANSFER_COMPONENT)) {
		JSONArray transferCompArr = componentJson.getJSONArray(JSON_PROP_TRANSFER_COMPONENT);
		
		for (int i=0;i<transferCompArr.length();i++) {
			JSONObject transferJson = transferCompArr.getJSONObject(i);
			
			productName = DYNAMICPKGACTION_TRANSFER;
			String dynamicPkgAction = transferJson.getString(JSON_PROP_DYNAMICPKGACTION);
			
			JSONArray groundServiceArr = transferJson.getJSONArray(JSON_PROP_GROUNDSERVICE);
			for (int j=0;j<groundServiceArr.length();j++) {
				JSONObject groundServiceJson = groundServiceArr.getJSONObject(j);
				if (groundServiceJson.has(JSON_PROP_TOTAL)){
				JSONArray totalArray = groundServiceJson.getJSONArray(JSON_PROP_TOTAL);
				for(int k=0; k<totalArray.length();k++) {
					JSONObject totalJson = totalArray.getJSONObject(k);
					
					JSONObject applicableOnProductJson = createApplicableOnProductsV2(productName, dynamicPkgAction,
							roomType, totalJson, roomCategory);
					applicableOnProductArr.put(applicableOnProductJson);
				}
			}else if (groundServiceJson.has(JSON_PROP_TOTALCHARGE)){
				JSONArray totalChargeArr =  groundServiceJson.getJSONArray(JSON_PROP_TOTALCHARGE);
					for(int k=0; k<totalChargeArr.length();k++) {
						JSONObject totalJson = totalChargeArr.getJSONObject(k);
						
						JSONObject applicableOnProductJson = createApplicableOnTransferGetDetails(productName, dynamicPkgAction,
								roomType, totalJson, roomCategory);
						applicableOnProductArr.put(applicableOnProductJson);
					}
				}}
				
				
				
			}
		}
		
		//For Transfer component --- end
		
		//For Insurance component --- start
		if (componentJson.has(JSON_PROP_INSURANCE_COMPONENT)) {
			createApplicableOnInsurance(applicableOnProductArr, componentJson, roomType, roomCategory);
		}
		//For Insurance component --- end
		
		//For Extras & Surcharges
		JSONArray feeArr = dynamicPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONArray(JSON_PROP_FEE);
		
		for(int f=0;f< feeArr.length();f++) {
			JSONObject feeJson = feeArr.getJSONObject(f);
			String feeCode = feeJson.getString("feeCode");
			String feeName = feeJson.getString("feeName");
				if (feeName.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)){
				
				productName = DYNAMICPKGACTION_EXTRAS;
				createApplicableOnExtrasSurcharges(applicableOnProductArr, productName, roomType, roomCategory, feeJson);
			}
			
			if (feeCode.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)){
				
				productName = "Surcharge";
				createApplicableOnExtrasSurcharges(applicableOnProductArr, productName, roomType, roomCategory, feeJson);
				
			}
			
		}
	}

	private static JSONObject createApplicableOnTransferGetDetails(String productName, String dynamicPkgAction,
			String roomType, JSONObject totalJson, String roomCategory) {
		
		JSONObject applicableOnProductJson = new JSONObject();
		BigDecimal amountAfterTax = totalJson.getBigDecimal("rateTotalAmount");
		BigDecimal amountBeforeTax = totalJson.getBigDecimal("rateTotalAmount");
		String psgrType = totalJson.getString(JSON_PROP_TYPE);
		
		applicableOnProductJson.put(JSON_PROP_TOTALFARE, amountAfterTax);
		applicableOnProductJson.put(JSON_PROP_PASSENGERTYPE, psgrType);
		applicableOnProductJson.put(JSON_PROP_ROOMTYPE, roomType);
		applicableOnProductJson.put(JSON_PROP_ROOMCATEGORY, roomCategory);
		applicableOnProductJson.put(JSON_PROP_PRODUCTNAME, productName);
		applicableOnProductJson.put("componentName", dynamicPkgAction);
		applicableOnProductJson.put("componentType", "");
		
		JSONObject fareBreakUpJson = new JSONObject();
		fareBreakUpJson.put(JSON_PROP_BASEFARE, amountBeforeTax);
		JSONArray taxDetailsArray = new JSONArray();

		JSONObject taxDetailsJson = new JSONObject();

		taxDetailsJson.put("taxValue", "");
		taxDetailsJson.put("taxName", "");

		taxDetailsArray.put(taxDetailsJson);
		fareBreakUpJson.put(JSON_PROP_TAXDETAILS, taxDetailsArray);
		applicableOnProductJson.put(JSON_PROP_FAREBREAKUP, fareBreakUpJson);
		return applicableOnProductJson;
	}

	private static void createApplicableOnInsurance(JSONArray applicableOnProductArr, JSONObject componentJson,
			String roomType, String roomCategory) {
		String productName;
		JSONArray insuranceCompArr = componentJson.getJSONArray(JSON_PROP_INSURANCE_COMPONENT);
		
		for(int a = 0; a <insuranceCompArr.length();a++) {
			JSONObject insuranceCompJson = insuranceCompArr.getJSONObject(a);
			String dynamicPkgAction = insuranceCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
			
			productName = DYNAMICPKGACTION_INSURANCE;
			
			JSONArray planCost = insuranceCompJson.getJSONArray(JSON_PROP_PLANCOST);
			for(int b=0; b<planCost.length();b++) {
				JSONObject planCostJson = planCost.getJSONObject(b);
				BigDecimal amountAfterTax = planCostJson.getBigDecimal(JSON_PROP_AMOUNT);
				BigDecimal amountBeforeTax = planCostJson.getJSONObject(JSON_PROP_BASEPREMIUM).getBigDecimal(JSON_PROP_AMOUNT);
				String psgrType = planCostJson.getString(JSON_PROP_PAXTYPE);
				JSONArray charge = planCostJson.getJSONArray(JSON_PROP_CHARGE);
				JSONObject applicableOnProductJson= new JSONObject();
				
				applicableOnProductJson.put(JSON_PROP_TOTALFARE, amountAfterTax);
				applicableOnProductJson.put(JSON_PROP_PASSENGERTYPE, psgrType);
				applicableOnProductJson.put(JSON_PROP_ROOMTYPE, roomType);
				applicableOnProductJson.put(JSON_PROP_ROOMCATEGORY, roomCategory);
				applicableOnProductJson.put(JSON_PROP_PRODUCTNAME, productName);
				applicableOnProductJson.put("componentName", dynamicPkgAction);
				applicableOnProductJson.put("componentType", "");
				JSONObject fareBreakUpJson = new JSONObject();
				fareBreakUpJson.put(JSON_PROP_BASEFARE, amountBeforeTax);
				
				for (int c=0 ; c< charge.length();c++) {
				   JSONObject chargeJson = charge.getJSONObject(c);
				   fareBreakUpJson.put(JSON_PROP_TAXDETAILS, getBRMStaxDetailsJSON(chargeJson));
				}
				applicableOnProductJson.put(JSON_PROP_FAREBREAKUP, fareBreakUpJson);
				applicableOnProductArr.put(applicableOnProductJson);
			}
		}
	}

	private static void createApplicableOnExtrasSurcharges(JSONArray applicableOnProductArr, String productName,
			String roomType, String roomCategory, JSONObject feeJson) {
		BigDecimal amountAfterTax = feeJson.getBigDecimal(JSON_PROP_AMOUNT);
		BigDecimal amountBeforeTax = feeJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX);
		String psgrType = feeJson.getString(JSON_PROP_TYPE);
		String componentType = feeJson.getString("feeCode");
		
		JSONObject applicableOnProductJson= new JSONObject();
		applicableOnProductJson.put(JSON_PROP_TOTALFARE, amountAfterTax);
		applicableOnProductJson.put(JSON_PROP_PASSENGERTYPE, psgrType);
		applicableOnProductJson.put(JSON_PROP_ROOMTYPE, roomType);
		applicableOnProductJson.put(JSON_PROP_ROOMCATEGORY, roomCategory);
		applicableOnProductJson.put(JSON_PROP_PRODUCTNAME, productName);
		applicableOnProductJson.put("componentName", productName);
		applicableOnProductJson.put("componentType", componentType);
		JSONObject fareBreakUpJson = new JSONObject();
		fareBreakUpJson.put(JSON_PROP_BASEFARE, amountBeforeTax);
		fareBreakUpJson.put(JSON_PROP_TAXDETAILS, getBRMStaxDetailsJSON(feeJson));
		applicableOnProductJson.put(JSON_PROP_FAREBREAKUP, fareBreakUpJson);
		applicableOnProductArr.put(applicableOnProductJson);
	}

	private static void getApplicableOnCruiseExtNights(JSONArray applicableOnProductArr, String productName,
			JSONObject cruiseCompJson) {
		String dynamicPkgAction = cruiseCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
		JSONArray categoryOptionsArr = cruiseCompJson.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
			for (int k = 0; k < categoryOptionsArr.length(); k++) {
				JSONObject categoryOptionsJson = categoryOptionsArr.getJSONObject(k);
				JSONArray categoryOptionArr = categoryOptionsJson.getJSONArray(JSON_PROP_CATEGORYOPTION);

				for (int j = 0; j < categoryOptionArr.length(); j++) {
					JSONObject categoryOptionJson = categoryOptionArr.getJSONObject(j);

					String cabinType = categoryOptionJson.getString(JSON_PROP_CABINTYPE);
					String cabinCategory = categoryOptionJson.getString(JSON_PROP_CABINCATEGORY);
					
					JSONArray totalArray = categoryOptionJson.getJSONArray(JSON_PROP_TOTAL);
					for(int l=0; l<totalArray.length();l++) {
						JSONObject totalJson = totalArray.getJSONObject(l);
					JSONObject applicableOnProductJson = createApplicableOnProductsV2(productName, dynamicPkgAction,
							cabinType, totalJson, cabinCategory);
					applicableOnProductArr.put(applicableOnProductJson);
					}
				}
			}
	}

	private static void getApplicableOnHotelExtensionNights(JSONArray applicableOnProductArr, JSONObject extNightAccoJson,String productName) {
		
		 String dynamicPkgAction = extNightAccoJson.getString(JSON_PROP_DYNAMICPKGACTION);

			JSONArray roomStayArr = extNightAccoJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);

			for (int j = 0; j < roomStayArr.length(); j++) {

				JSONObject roomStayJson = roomStayArr.getJSONObject(j);
				JSONArray roomrateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);

				String roomTypeNights = roomStayJson.get(JSON_PROP_ROOMTYPE).toString();

				JSONObject roomRateJson = new JSONObject();
				for (int k = 0; k < roomrateArr.length(); k++) {
					roomRateJson = roomrateArr.getJSONObject(k);
					String roomCategoryNights = roomRateJson.optString(JSON_PROP_RATEPLANCATEGORY);
					JSONObject totalJson = roomRateJson.getJSONObject(JSON_PROP_TOTAL);
					JSONObject applicableOnProductJson = createApplicableOnProductsV2(productName, dynamicPkgAction,
							roomTypeNights, totalJson, roomCategoryNights);
					applicableOnProductArr.put(applicableOnProductJson);
				}

			}
		
	}

	private static JSONObject createApplicableOnProductsV2(String productName,
			String dynamicPkgAction, String roomType, JSONObject totalJson, String roomCategory) {
		JSONObject applicableOnProductJson = new JSONObject();
		BigDecimal amountAfterTax = new BigDecimal(0);
		//to handle when there is no amountAfterTax in Monograms -prenight /postnight
		
		   amountAfterTax = totalJson.optBigDecimal(JSON_PROP_AMOUNTAFTERTAX,new BigDecimal(0));
		
		BigDecimal amountBeforeTax = totalJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX);
		String psgrType = totalJson.getString(JSON_PROP_TYPE);
		
		applicableOnProductJson.put(JSON_PROP_TOTALFARE, amountAfterTax);
		applicableOnProductJson.put(JSON_PROP_PASSENGERTYPE, psgrType);
		applicableOnProductJson.put(JSON_PROP_ROOMTYPE, roomType);
		applicableOnProductJson.put(JSON_PROP_ROOMCATEGORY, roomCategory);
		applicableOnProductJson.put(JSON_PROP_PRODUCTNAME, productName);
		applicableOnProductJson.put("componentName", dynamicPkgAction);
		applicableOnProductJson.put("componentType", "");
		
		JSONObject fareBreakUpJson = new JSONObject();
		fareBreakUpJson.put(JSON_PROP_BASEFARE, amountBeforeTax);
		fareBreakUpJson.put(JSON_PROP_TAXDETAILS, getBRMStaxDetailsJSON(totalJson));
		applicableOnProductJson.put(JSON_PROP_FAREBREAKUP, fareBreakUpJson);
		return applicableOnProductJson;
	}

	
	private static void getBRMSpkgDetailsRepriceV1(JSONObject dynamicPkgJson, JSONObject reqBody,
			JSONArray applicableOnProductArr) {
		String brandNameRes = dynamicPkgJson.getString("brandName");
		String tourCodeRes = dynamicPkgJson.getString("tourCode");
		String subTourCodeRes = dynamicPkgJson.getString("subTourCode");

		JSONArray flightDetailsArr = new JSONArray();
		JSONObject applicableOnProductFlightJson = new JSONObject();

		// get Airline component from request
		JSONArray dynamicPackageArr = reqBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		
		String dynamicPkgAction = null;

		for (int i = 0; i < dynamicPackageArr.length(); i++) {
			JSONObject dynamicPkgJsonReq = dynamicPackageArr.getJSONObject(i);

			String brandNameReq = dynamicPkgJsonReq.getString("brandName");
			String tourCodeReq = dynamicPkgJsonReq.getString("tourCode");
			String subTourCodeReq = dynamicPkgJsonReq.getString("subTourCode");

			if (brandNameRes.equalsIgnoreCase(brandNameReq) && tourCodeRes.equalsIgnoreCase(tourCodeReq)
					&& subTourCodeRes.equalsIgnoreCase(subTourCodeReq)) {
				JSONArray airComponentArr = dynamicPkgJsonReq.getJSONObject(JSON_PROP_COMPONENTS)
						.getJSONArray(JSON_PROP_AIR_COMPONENT);
				for (int j = 0; j < airComponentArr.length(); j++) {
					JSONObject airComponentJson = airComponentArr.getJSONObject(j);

					JSONObject airItinJson = airComponentJson.getJSONObject(JSON_PROP_AIRITINERARY);
					JSONArray originDestinationOptionsArr = airItinJson.getJSONArray(JSON_PROP_ORIGINDESTOPTIONS);
					for (int k = 0; k < originDestinationOptionsArr.length(); k++) {
						JSONObject originDestinationOptionJson = originDestinationOptionsArr.getJSONObject(k);

						JSONArray flightSegmentArr = originDestinationOptionJson
								.getJSONArray(JSON_PROP_FLIGHTSEGMENT);
						for (int l = 0; l < flightSegmentArr.length(); l++) {
							JSONObject flightSegmentJson = flightSegmentArr.getJSONObject(l);
							JSONObject operatingAirlineJSON = flightSegmentJson.getJSONObject("operatingAirline");
							String flightDetails = operatingAirlineJSON.getString("airlineCode")
									+ operatingAirlineJSON.getString("flightNumber") + " "
									+ flightSegmentJson.getString("originLocation") + "/"
									+ flightSegmentJson.getString("destinationLocation");
							flightDetailsArr.put(flightDetails);
						}
					}
				}
				if(dynamicPkgJsonReq.getJSONObject(JSON_PROP_COMPONENTS).has(JSON_PROP_HOTEL_COMPONENT)) {
				JSONArray hotelCompRqArr = dynamicPkgJsonReq.getJSONObject(JSON_PROP_COMPONENTS)
						.optJSONArray(JSON_PROP_HOTEL_COMPONENT);
				if (hotelCompRqArr!= null && hotelCompRqArr.length() > 0) {
					roomMap= new HashMap<String, ArrayList<String>>();
					for (int k = 0; k < hotelCompRqArr.length(); k++) {

						JSONArray roomStayArr = hotelCompRqArr.getJSONObject(k).getJSONObject("roomStays")
								.getJSONArray("roomStay");
						dynamicPkgAction = hotelCompRqArr.getJSONObject(k).getString(JSON_PROP_DYNAMICPKGACTION);

						for (int l = 0; l < roomStayArr.length(); l++) {
							JSONObject roomStayJson = roomStayArr.getJSONObject(l);
							String roomType = roomStayJson.optString(JSON_PROP_ROOMTYPE);
							String roomCategory = roomStayJson.optString("roomCategory");

							ArrayList<String> list = new ArrayList<String>();
							list.add(roomType);
							list.add(roomCategory);
							roomMap.put(dynamicPkgAction, list);

						}
					}

				}
			}
				
				if (dynamicPkgJsonReq.getJSONObject(JSON_PROP_COMPONENTS).has(JSON_PROP_CRUISE_COMPONENT)) {
					JSONArray cruiseCompRqArr = dynamicPkgJsonReq.getJSONObject(JSON_PROP_COMPONENTS)
							.optJSONArray(JSON_PROP_CRUISE_COMPONENT);
					String roomType,roomCategory="";
					if (cruiseCompRqArr != null && cruiseCompRqArr.length() > 0) {
						roomMap= new HashMap<String, ArrayList<String>>();
						for (int k = 0; k < cruiseCompRqArr.length(); k++) {
							dynamicPkgAction = cruiseCompRqArr.getJSONObject(k)
									.getString(JSON_PROP_DYNAMICPKGACTION);
							JSONArray categoryOptionsArr =new JSONArray();
							JSONArray categoryOptionArr =new JSONArray();
							//To handle Prenight /Postnight from WEM request -Priti
							if(dynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_PRE) || 
									dynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_POST)) {
								JSONObject roomStays = cruiseCompRqArr.getJSONObject(k).getJSONObject("roomStays");
								categoryOptionsArr.put(roomStays);
							}
							else{
								categoryOptionsArr = cruiseCompRqArr.getJSONObject(k).getJSONArray("categoryOptions");
							}
							for (int l = 0; l < categoryOptionsArr.length(); l++) {
								JSONObject catgryOptions= categoryOptionsArr.getJSONObject(l);
								//To handle Prenight /Postnight from WEM request to form proper cruise prent /postnt xml req --Priti
								if(dynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_PRE) || 
										dynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_POST)) {
									
									
									categoryOptionArr = catgryOptions.getJSONArray("roomStay");
									
								}else {
								     categoryOptionArr = catgryOptions.getJSONArray("categoryOption");
								}
								for (int m = 0; m < categoryOptionArr.length(); m++) {
									JSONObject categoryOptionJson = categoryOptionArr.getJSONObject(m);
									if(dynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_PRE) || 
											dynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_POST)) {
										roomType = categoryOptionJson.optString(JSON_PROP_ROOMTYPE);
										roomCategory = categoryOptionJson.optString("roomCategory");
									}else {
									     roomType = categoryOptionJson.optString(JSON_PROP_TYPE);
									     roomCategory = categoryOptionJson.optString("ratePlanCategory");
									}
									

									ArrayList<String> list = new ArrayList<String>();
									list.add(roomType);
									list.add(roomCategory);
									roomMap.put(dynamicPkgAction, list);

								}
							}
						}
					}
				}

			}
		}

		// creating priceArray of unique elements to create Wem response
		Map<String, JSONObject> priceMap = new ConcurrentHashMap<String, JSONObject>();

		JSONArray componentArr = dynamicPkgJson.getJSONArray(JSON_PROP_COMPONENTS);
		for (int c = 0; c < componentArr.length(); c++) {
			JSONObject componentJson = componentArr.getJSONObject(c);
			String paxType = componentJson.getString("paxType");
			PassengerType psgrType = PassengerType.forString(paxType);

			JSONArray priceArray = componentJson.getJSONArray(JSON_PROP_PRICE);
				
				createApplicableOnForPax(applicableOnProductArr, priceArray, flightDetailsArr,
						applicableOnProductFlightJson, psgrType);
			
		}
	}

	private static void createApplicableOnForPax(JSONArray applicableOnProductArr, JSONArray newPriceArr,
			JSONArray flightDetailsArr, JSONObject applicableOnProductFlightJson, PassengerType psgrType) {
		String key;
		String roomTypeStr = "", roomCategoryStr = "";
		for (int i = 0; i < newPriceArr.length(); i++) {
			JSONObject priceJson = newPriceArr.getJSONObject(i);

			String rateDescriptionText = priceJson.getJSONObject("rateDescription").getString(JSON_PROP_TEXT);
			if (!(rateDescriptionText.contains("Room"))) {
				if (rateDescriptionText.contains("Night")) {
					String applicableOnStr = rateDescriptionText.substring(0, rateDescriptionText.indexOf("Night") + 5)
							.replaceAll("[^a-zA-Z]", "");
					for (Map.Entry<String, ArrayList<String>> entry : roomMap.entrySet()) {
						key = entry.getKey();
						if (key.contains(applicableOnStr)) {
							String roomType = entry.getValue().get(0);
							String roomCategory = entry.getValue().get(1);

							JSONObject applicableOnProductJson = createApplicableOnProduct(applicableOnStr, priceJson,
									psgrType, roomType, roomCategory);
							applicableOnProductArr.put(applicableOnProductJson);

						}
					}
				}

				else if (rateDescriptionText.contains("Trip Protection") || rateDescriptionText.contains("Insurance")) {
					String applicableOnStr = DYNAMICPKGACTION_INSURANCE;
					JSONObject applicableOnProductJson = createApplicableOnProduct(applicableOnStr, priceJson, psgrType,
							roomTypeStr, roomCategoryStr);
					applicableOnProductArr.put(applicableOnProductJson);

				}

				// Code for Extras/ Upgrades
				else if (rateDescriptionText.equalsIgnoreCase("Extras") || rateDescriptionText.contains("Upgrade")) {
					String applicableOnStr = DYNAMICPKGACTION_EXTRAS;
					JSONObject applicableOnProductJson = createApplicableOnProduct(applicableOnStr, priceJson, psgrType,
							roomTypeStr, roomCategoryStr);
					applicableOnProductArr.put(applicableOnProductJson);

				}

				// For Arrival and Departure Transfers
				else if (rateDescriptionText.contains("Transfer")) {

					String applicableOnStr = DYNAMICPKGACTION_TRANSFER;
					JSONObject applicableOnProductJson = createApplicableOnProduct(applicableOnStr, priceJson, psgrType,
							roomTypeStr, roomCategoryStr);
					applicableOnProductArr.put(applicableOnProductJson);
				}

				else if (rateDescriptionText.contains("Surcharge")) {
					String applicableOnStr = "Surcharge";
					JSONObject applicableOnProductJson = createApplicableOnProduct(applicableOnStr, priceJson, psgrType,
							roomTypeStr, roomCategoryStr);
					applicableOnProductArr.put(applicableOnProductJson);

				}
				// for adding all airline fares
				for (int j = 0; j < flightDetailsArr.length(); j++) {

					if (rateDescriptionText.contains(flightDetailsArr.getString(j))) {

						String amountAfterTax = priceJson.getJSONObject("total").getString(JSON_PROP_AMOUNTAFTERTAX);
						String amountBeforeTax = priceJson.getJSONObject("total").getString(JSON_PROP_AMOUNTBEFORETAX);
						totalAirAmountAfterTax = totalAirAmountAfterTax + Double.parseDouble(amountAfterTax);
						totalAirAmountBeforeTax = totalAirAmountBeforeTax + Double.parseDouble(amountBeforeTax);

						JSONArray taxJsonArr = getTotalTaxes(priceJson, airTaxMap);

						if (j != flightDetailsArr.length() - 1) {
							continue;
						}

						applicableOnProductFlightJson.put(JSON_PROP_PRODUCTNAME, "PackageDepartureAndArrivalFlights");
						applicableOnProductFlightJson.put(JSON_PROP_TOTALFARE, String.valueOf(totalAirAmountAfterTax));
						JSONObject fareBreakUpJson = new JSONObject();
						fareBreakUpJson.put(JSON_PROP_BASEFARE, String.valueOf(totalAirAmountBeforeTax));

						fareBreakUpJson.put(JSON_PROP_TAXDETAILS, taxJsonArr);
						applicableOnProductFlightJson.put(JSON_PROP_FAREBREAKUP, fareBreakUpJson);
						applicableOnProductArr.put(applicableOnProductFlightJson);

					}

				}

			}
		}
	}

	private static JSONObject getAirItineraryPricingInfo(JSONObject airItineraryPricingInfoJson) {
		JSONObject applicableOnProductJson = new JSONObject();

		String baseFareAmount = airItineraryPricingInfoJson.getJSONObject(JSON_PROP_ITINTOTALFARE)
				.getJSONObject(JSON_PROP_BASEFARE).getString(JSON_PROP_AMOUNT);
		String totalFareAmount = airItineraryPricingInfoJson.getJSONObject(JSON_PROP_ITINTOTALFARE)
				.getJSONObject(JSON_PROP_TOTALFARE).getString(JSON_PROP_AMOUNT);

		applicableOnProductJson.put(JSON_PROP_PRODUCTNAME, DYNAMICPKGACTION_AIR_DEPARR);
		applicableOnProductJson.put(JSON_PROP_TOTALFARE, totalFareAmount);

		JSONObject fareBreakUpJson = new JSONObject();
		fareBreakUpJson.put(JSON_PROP_BASEFARE, baseFareAmount);

		fareBreakUpJson.put(JSON_PROP_TAXDETAILS, getBRMSAddServiceTaxDetails(airItineraryPricingInfoJson));
		applicableOnProductJson.put(JSON_PROP_FAREBREAKUP, fareBreakUpJson);

		return applicableOnProductJson;
	}

	private static JSONArray getBRMSAddServiceTaxDetails(JSONObject airItineraryPricingInfoJson) {
		JSONArray taxJsonArr = new JSONArray();
		JSONArray taxArr = airItineraryPricingInfoJson.getJSONObject(JSON_PROP_ITINTOTALFARE)
				.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);

		for (int k = 0; k < taxArr.length(); k++) {
			JSONObject taxJson = taxArr.getJSONObject(k);
			String taxName = taxJson.getString(JSON_PROP_TAXNAME);
			String taxValue = taxJson.getString(JSON_PROP_AMOUNT);

			taxJson.put(JSON_PROP_TAXNAME, taxName);
			taxJson.put(JSON_PROP_TAXVALUE, taxValue);

			taxJsonArr.put(taxJson);

		}

		return taxJsonArr;
	}

	public static JSONArray getTotalTaxes(JSONObject priceJson, Map<String, Double> taxMap) {
		JSONArray taxArr = priceJson.getJSONObject("total").getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
		JSONArray taxJsonArr = new JSONArray();

		// for adding all air taxes
		for (int k = 0; k < taxArr.length(); k++) {
			JSONObject tax = taxArr.getJSONObject(k);
			String taxName = tax.getString(JSON_PROP_TAXDESCRIPTION);
			if (taxName.equals("TaxesAndPortCharges")) {
				String taxesAndPortChargesValue = tax.getString(JSON_PROP_AMOUNT);

				if (taxMap.containsKey("TaxesAndPortCharges")) {
					double totalTaxesAndPortCharges = taxMap.get("TaxesAndPortCharges")
							+ Double.parseDouble(taxesAndPortChargesValue);
					taxMap.put("TaxesAndPortCharges", totalTaxesAndPortCharges);
				} else {
					double totalTaxesAndPortCharges = Double.parseDouble(taxesAndPortChargesValue);
					taxMap.put("TaxesAndPortCharges", totalTaxesAndPortCharges);
				}
			}

			if (taxName.equals("Surcharge")) {
				String surchargeValue = tax.getString(JSON_PROP_AMOUNT);
				if (taxMap.containsKey("Surcharge")) {
					double totalSurcharge = taxMap.get("Surcharge") + Double.parseDouble(surchargeValue);
					taxMap.put("Surcharge", totalSurcharge);
				} else {
					double totalSurcharge = Double.parseDouble(surchargeValue);
					taxMap.put("Surcharge", totalSurcharge);
				}
			}

		}

		for (Entry<String, Double> entry : taxMap.entrySet()) {
			JSONObject taxJson = new JSONObject();
			taxJson.put(JSON_PROP_TAXVALUE, String.valueOf(entry.getValue()));
			taxJson.put(JSON_PROP_TAXNAME, String.valueOf(entry.getKey()));
			taxJsonArr.put(taxJson);

		}
		return taxJsonArr;
	}

	private static JSONObject createApplicableOnProduct(String applicableOnStr, JSONObject priceJson, PassengerType psgrType,
			String roomTypeStr, String roomCategoryStr) {
		JSONObject applicableOnProductJson = new JSONObject();
		
		String rateDescriptionName = priceJson.getJSONObject("rateDescription").getString("name");
		String rateDescriptionType = priceJson.getJSONObject("rateDescription").getString("text");
		
		applicableOnProductJson.put(JSON_PROP_PRODUCTNAME, applicableOnStr);
		applicableOnProductJson.put("componentName", rateDescriptionName);
		applicableOnProductJson.put("componentType", rateDescriptionType);
		
		BigDecimal amountAfterTax = priceJson.getJSONObject("total").getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
		applicableOnProductJson.put(JSON_PROP_TOTALFARE, amountAfterTax);

		applicableOnProductJson.put("passengerType", psgrType);
		applicableOnProductJson.put("roomType", roomTypeStr);
		applicableOnProductJson.put("roomCategory", roomCategoryStr);

		BigDecimal amountBeforeTax = priceJson.getJSONObject("total").getBigDecimal(JSON_PROP_AMOUNTBEFORETAX);
		JSONObject fareBreakUpJson = new JSONObject();
		fareBreakUpJson.put(JSON_PROP_BASEFARE, amountBeforeTax);
		JSONObject totalJson =priceJson.getJSONObject("total");
		fareBreakUpJson.put(JSON_PROP_TAXDETAILS, getBRMStaxDetailsJSON(totalJson));
		applicableOnProductJson.put(JSON_PROP_FAREBREAKUP, fareBreakUpJson);
		return applicableOnProductJson;
	}

	private static JSONArray getBRMSroomDetailsJSON(JSONObject packageDetailJson, CommercialsOperation operationName) {

		String tourEndCity = "";
		JSONArray roomDetailsArr = new JSONArray();

		if (packageDetailJson.has(JSON_PROP_GLOBALINFO)) {
			JSONObject dynamicpackageId = packageDetailJson.getJSONObject(JSON_PROP_GLOBALINFO)
					.getJSONObject(JSON_PROP_DYNAMICPKGID);

			if (dynamicpackageId.has(JSON_PROP_TPA_EXTENSIONS)) {
				tourEndCity = dynamicpackageId.getJSONObject(JSON_PROP_TPA_EXTENSIONS).getJSONObject(JSON_PROP_PKGS_TPA)
						.getJSONObject(JSON_PROP_TOURDETAILS).get(JSON_PROP_TOURENDCITY).toString();
			}
		}

		// add service passenger details
		if (operationName.toString().equalsIgnoreCase("AddService")) {
			JSONObject roomDetails = new JSONObject();

			getCommonRoomDetails("", roomDetails);
			roomDetails.put("roomCategory", "");
			roomDetails.put("roomType", "");

			JSONArray passengerDetailsArr = new JSONArray();

			JSONObject passengerDetailsJson = new JSONObject();
			passengerDetailsJson.put("totalFare", "");
			passengerDetailsJson.put("passengerType", "");

			JSONObject fareBreakUpJson = new JSONObject();
			fareBreakUpJson.put("baseFare", "");

			JSONArray taxDetailsArray = new JSONArray();

			JSONObject taxDetailsJson = new JSONObject();

			taxDetailsJson.put("taxValue", "");
			taxDetailsJson.put("taxName", "");

			taxDetailsArray.put(taxDetailsJson);

			fareBreakUpJson.put("taxDetails", taxDetailsArray);
			passengerDetailsJson.put("fareBreakUp", fareBreakUpJson);
			passengerDetailsArr.put(passengerDetailsJson);
			roomDetails.put("passengerDetails", passengerDetailsArr);
			roomDetailsArr.put(roomDetails);

		}
	//	if (operationName.toString().equalsIgnoreCase("Search") || operationName.toString().equalsIgnoreCase("getDetails")|| operationName.toString().equalsIgnoreCase("Reprice")) {

			getBRMSroomDetailsSearch(packageDetailJson, tourEndCity, roomDetailsArr);
			
			
		//}//comment below code to run new repriceprocessorV2
		 /*else if (operationName.equalsIgnoreCase("reprice")) {
			getBRMSroomDetailsRepriceV1(packageDetailJson, tourEndCity, roomDetailsArr);

		}
*/
		return roomDetailsArr;

	}

	private static void getBRMSroomDetailsSearch(JSONObject packageDetailJson, String tourEndCity,
			JSONArray roomDetailsArr) {
		if (packageDetailJson.getJSONObject(JSON_PROP_COMPONENTS).has(JSON_PROP_HOTEL_COMPONENT)) {
			
			JSONObject hotelCompJson = packageDetailJson.getJSONObject(JSON_PROP_COMPONENTS)
							.getJSONObject(JSON_PROP_HOTEL_COMPONENT);

			if(hotelCompJson!=null && hotelCompJson.length()>0) {
				String dynamicPkgAction = hotelCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
				if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_HOTEL_TOUR)) {
					JSONArray roomStayArr = hotelCompJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);

					for (int j = 0; j < roomStayArr.length(); j++) {
						JSONObject roomDetails = new JSONObject();
						JSONObject roomStayJson = roomStayArr.getJSONObject(j);
						JSONArray roomrateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);
						String roomType = roomStayJson.get(JSON_PROP_ROOMTYPE).toString();
						roomDetails.put(JSON_PROP_ROOMTYPE, roomType);
						// TODO :supplierRateType
						getCommonRoomDetails(tourEndCity, roomDetails);
						// getting Type="PREMIUM" in case of Monograms
						JSONObject roomRateJson = null;
						for (int k = 0; k < roomrateArr.length(); k++) {
							roomRateJson = roomrateArr.getJSONObject(k);
							String roomCategory = roomRateJson.optString("ratePlanCategory");
							roomDetails.put("roomCategory", roomCategory);

						}
						roomDetails.put("passengerDetails", getBRMSpassengerDetailsJSON(roomStayJson));

						roomDetailsArr.put(roomDetails);

					}
				}
				
			}}
		

		if (packageDetailJson.getJSONObject(JSON_PROP_COMPONENTS).has(JSON_PROP_CRUISE_COMPONENT)) {
			JSONObject cruiseCompJson = packageDetailJson.getJSONObject(JSON_PROP_COMPONENTS)
					.getJSONObject(JSON_PROP_CRUISE_COMPONENT);

			if(cruiseCompJson!=null && cruiseCompJson.length()>0) {
				String dynamicPkgAction = cruiseCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
				if (dynamicPkgAction.equalsIgnoreCase("PackageCruise")) {
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
							getCommonRoomDetails(tourEndCity, roomDetails);
							roomDetails.put("roomCategory", cabinCategory);
							roomDetails.put("passengerDetails", getCruisePassengerDetailsJSON(categoryOptionJson));
							roomDetailsArr.put(roomDetails);

						}
					}

				}
				
		}}
	}

	private static void getBRMSroomDetailsRepriceV1(JSONObject packageDetailJson, String tourEndCity,
			JSONArray roomDetailsArr) {
		Map<String, JSONObject> roomPaxTypeMap = new HashMap<String, JSONObject>();
		Map<String, JSONObject> roomTypeMap = new HashMap<String, JSONObject>();
		JSONArray componentsArr = packageDetailJson.getJSONArray(JSON_PROP_COMPONENTS);
		String key, roomCategoryStr = "";

		for (int c = 0; c < componentsArr.length(); c++) {
			JSONObject componentJson = componentsArr.getJSONObject(c);
			String paxType = componentJson.getString("paxType");
			JSONArray priceArray = componentJson.getJSONArray(JSON_PROP_PRICE);
			for (int i = 0; i < priceArray.length(); i++) {
				JSONObject priceJson = priceArray.getJSONObject(i);
				String rateDescriptionText = priceJson.getJSONObject("rateDescription").getString(JSON_PROP_TEXT);

				if (rateDescriptionText.contains("Room")) {

					String roomType = rateDescriptionText.substring(0, rateDescriptionText.lastIndexOf(" "));
					// Creating roomTypeMap and roomPaxTypeMap
					roomPaxTypeMap.put(paxType + "-" + roomType, priceJson);
					roomTypeMap.put(roomType, priceJson);
				}
			}
		}
		// Creating roomDetails array from roomTypeMap to create separate object room wise
		for (Map.Entry<String, JSONObject> roomTypeEntry : roomTypeMap.entrySet()) {
			JSONObject roomDetails = new JSONObject();
			String roomTypekey = roomTypeEntry.getKey();

			roomDetails.put(JSON_PROP_ROOMTYPE, roomTypekey);
			getCommonRoomDetails(tourEndCity, roomDetails);
			for (Map.Entry<String, ArrayList<String>> entry : roomMap.entrySet()) {
				key = entry.getKey();
				if (key.equals("PackageAccomodation") || key.equals("PackageCruise")) {
					roomCategoryStr = entry.getValue().get(1);

				}
			}

			roomDetails.put("roomCategory", roomCategoryStr);
			// Creating passengerDetails from roomPaxTypeMap which repeats the array based on passengerTypeObjects
			roomDetails.put("passengerDetails", getRepricePassengerDetailsJSON(roomPaxTypeMap, roomTypekey));
			roomDetailsArr.put(roomDetails);

		}
	}

	public static void getCommonRoomDetails(String tourEndCity, JSONObject roomDetails) {
		roomDetails.put("supplierRateType", "");
		roomDetails.put("supplierRateCode", "");
		roomDetails.put("toContinent", "Asia");
		roomDetails.put("toCountry", "India");
		roomDetails.put("toCity", tourEndCity);
		roomDetails.put("toState", "");
	}

	private static JSONArray getRepricePassengerDetailsJSON( Map<String, JSONObject> roomPaxTypeMap, String roomType) {
		JSONArray passengerDetailsArr = new JSONArray();
		
		for (Map.Entry<String, JSONObject> entry : roomPaxTypeMap.entrySet()) {
			JSONObject paxDetail = new JSONObject();
			String key = entry.getKey();
			String[] arr= key.split("-");
			String paxType= arr[0];
			
			if (key.contains(paxType) && key.contains(roomType)) {
				JSONObject priceJson = entry.getValue();
				getPassengerDetailsforReprice(passengerDetailsArr, paxDetail, priceJson, paxType);
				passengerDetailsArr.put(paxDetail);
			}
			
		}
		
		return passengerDetailsArr;

	}

	private static JSONObject getPassengerDetailsforReprice(JSONArray passengerDetailsArr, JSONObject paxDetail,
			JSONObject priceJson, String paxType) {
	
			BigDecimal totalFare = priceJson.getJSONObject(JSON_PROP_TOTAL).getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
			paxDetail.put(JSON_PROP_TOTALFARE, totalFare);
			paxDetail.put(JSON_PROP_PASSENGERTYPE,paxType );

			JSONObject fareBreakUp = new JSONObject();
			BigDecimal baseFare = priceJson.getJSONObject(JSON_PROP_TOTAL).getBigDecimal(JSON_PROP_AMOUNTBEFORETAX);
			fareBreakUp.put(JSON_PROP_BASEFARE, baseFare);
			JSONObject totalJson =priceJson.getJSONObject(JSON_PROP_TOTAL);
			fareBreakUp.put(JSON_PROP_TAXDETAILS, getBRMStaxDetailsJSON(totalJson));
			paxDetail.put(JSON_PROP_FAREBREAKUP, fareBreakUp);
			
		
		return paxDetail;
	}

	private static JSONArray getCruisePassengerDetailsJSON(JSONObject categoryOptionJson) {
		JSONArray passengerDetailsArr = new JSONArray();
		

		JSONArray totalArr = categoryOptionJson.getJSONArray(JSON_PROP_TOTAL);

		for (int i = 0; i < totalArr.length(); i++) {
			JSONObject paxDetail = new JSONObject();
			JSONObject totalJson = totalArr.getJSONObject(i);
			String totalFare = totalJson.getString(JSON_PROP_AMOUNTAFTERTAX);
			paxDetail.put(JSON_PROP_TOTALFARE, totalFare);
			String type = totalJson.get(JSON_PROP_TYPE).toString();
			
			paxDetail.put(JSON_PROP_PASSENGERTYPE, type);
			
			JSONObject fareBreakUp = new JSONObject();
			String baseFare = totalJson.getString(JSON_PROP_AMOUNTBEFORETAX);
			fareBreakUp.put(JSON_PROP_BASEFARE, baseFare);
			fareBreakUp.put(JSON_PROP_TAXDETAILS, getBRMSCruisetaxDetailsJSON(totalJson));
			paxDetail.put(JSON_PROP_FAREBREAKUP, fareBreakUp);
			passengerDetailsArr.put(paxDetail);

		}

		return passengerDetailsArr;
	}

	private static JSONArray getBRMSCruisetaxDetailsJSON(JSONObject totalJson) {
		JSONArray taxArr = totalJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
		JSONArray taxJsonArr = new JSONArray();

		for (int i = 0; i < taxArr.length(); i++) {
			JSONObject taxJson = new JSONObject();
			JSONObject tax = taxArr.getJSONObject(i);
			String taxName = tax.getString(JSON_PROP_TAXDESCRIPTION);
			taxJson.put(JSON_PROP_TAXNAME, taxName);
			String taxValue = tax.getString(JSON_PROP_AMOUNT);
			taxJson.put(JSON_PROP_TAXVALUE, taxValue);

			taxJsonArr.put(taxJson);
		}
		return taxJsonArr;
	}

	private static JSONArray getBRMSpassengerDetailsJSON(JSONObject roomStayJson) {
		JSONArray passengerDetailsArr = new JSONArray();
		JSONArray roomrateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);

		for (int i = 0; i < roomrateArr.length(); i++) {
			JSONObject paxDetail = new JSONObject();
			JSONObject roomRateJson = roomrateArr.getJSONObject(i);
			
			String type = roomRateJson.getJSONObject(JSON_PROP_TOTAL).get(JSON_PROP_TYPE).toString();
			
			paxDetail.put(JSON_PROP_PASSENGERTYPE, type);
			
			String totalFare = roomRateJson.getJSONObject(JSON_PROP_TOTAL).getString(JSON_PROP_AMOUNTAFTERTAX);
			paxDetail.put(JSON_PROP_TOTALFARE, totalFare);

			JSONObject fareBreakUp = new JSONObject();
			String baseFare = roomRateJson.getJSONObject(JSON_PROP_TOTAL).getString(JSON_PROP_AMOUNTBEFORETAX);
			fareBreakUp.put(JSON_PROP_BASEFARE, baseFare);
			JSONObject totalJson =roomRateJson.getJSONObject(JSON_PROP_TOTAL);
			fareBreakUp.put(JSON_PROP_TAXDETAILS, getBRMStaxDetailsJSON(totalJson));
			paxDetail.put(JSON_PROP_FAREBREAKUP, fareBreakUp);
			passengerDetailsArr.put(paxDetail);

		}

		return passengerDetailsArr;
	}

	private static JSONArray getBRMStaxDetailsJSON(JSONObject totalJson) {
		JSONArray taxArr = totalJson.optJSONObject(JSON_PROP_TAXES).optJSONArray(JSON_PROP_TAX);
		JSONArray taxJsonArr = new JSONArray();

		for (int i = 0; i < taxArr.length(); i++) {
			JSONObject taxJson = new JSONObject();
			JSONObject tax = taxArr.getJSONObject(i);
			String taxName = tax.getString(JSON_PROP_TAXDESCRIPTION);
			taxJson.put(JSON_PROP_TAXNAME, taxName);
			String taxValue = tax.getString(JSON_PROP_AMOUNT);
			taxJson.put(JSON_PROP_TAXVALUE, taxValue);

			taxJsonArr.put(taxJson);
		}
		return taxJsonArr;
	}

}
