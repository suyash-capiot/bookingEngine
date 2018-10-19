package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class HolidaysAmClCompanyPolicy implements HolidayConstants{

	private static final Logger logger = LogManager.getLogger(HolidaysAmClCompanyPolicy.class);
	private static double totalFare = 0;
	
	public static JSONObject getAmClCompanyPolicy(CommercialsOperation op, JSONObject req, JSONObject res) throws Exception{
		
		//CommercialsConfig commConfig = HolidaysConfig.getCommercialsConfig();
		//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_COMPANY_POLICIES);
		ServiceConfig commTypeConfig = HolidaysConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_COMPANY_POLICIES);
		JSONObject breCompPolicyReq = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
		
		//JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
		//JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);
		
		JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);
		
		/*String type;
        if(op.equals(CommercialsOperation.Cancel))
        	type = reqBody.getString(JSON_PROP_CANCELTYPE);
    	else
        	type = reqBody.getString(JSON_PROP_AMENDTYPE);*/
		
		JSONObject rootJson = breCompPolicyReq.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.holidays_companypolicy.Root");
		
		JSONObject breHdrJson = new JSONObject();
        breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
        breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
        breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
        //breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());
        breHdrJson.put(JSON_PROP_OPERATIONNAME, "Cancellation");
        
        rootJson.put(JSON_PROP_HEADER, breHdrJson);
		
        JSONObject briJson = null;
        JSONArray briJsonArr = new JSONArray();
        
        briJson = createBusinessRuleIntakeForSupplier(resHeader, resBody, resBody);
        
        briJsonArr.put(briJson);
        rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
        
		JSONObject companyPolicyRes = null;
		try {
			//companyPolicyRes = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_COMPANYPLCY, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breCompPolicyReq);
			logger.trace("Company Policy Req: "+breCompPolicyReq);
			companyPolicyRes = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_COMPANYPLCY, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breCompPolicyReq);
			logger.trace("Company Policy Res: "+companyPolicyRes);
		}
		catch (Exception x) {
			logger.warn("An exception occurred when calling supplier commercials", x);
		}
		// Add Policy to Result
        appendPolicyToResults(resBody, companyPolicyRes);
        	
		return companyPolicyRes;
	}
	
	private static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqSuppBookRef, JSONObject resBodyJson) {
		
		JSONObject briJson = new JSONObject();
        JSONObject commonElemsJson = new JSONObject();
        UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
        JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
        
        // Common Elements----
        //commonElemsJson.put(JSON_PROP_COMPANYMKT, clientCtx.optString(JSON_PROP_CLIENTMARKET, ""));
        commonElemsJson.put(JSON_PROP_COMPANYMKT, "India");
        //commonElemsJson.put("contractValidity", DATE_FORMAT.format(new Date()));
        commonElemsJson.put(JSON_PROP_CONTRACTVAL, "2019-12-15T00:00:00");
        
        commonElemsJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_HOLIDAYS);
        commonElemsJson.put(JSON_PROP_PRODCATEGSUBTYPE, "");
        // TODO: Check how the value for segment should be set?
        
        //commonElemsJson.put(JSON_PROP_ENTITYTYPE, clientCtx.getString(JSON_PROP_CLIENTTYPE));
    	//commonElemsJson.put(JSON_PROP_ENTITYNAME, (usrCtx != null) ? usrCtx.getClientName() : "");

    	commonElemsJson.put(JSON_PROP_ENTITYTYPE, "Client Type");
    	commonElemsJson.put(JSON_PROP_ENTITYNAME, "ENTITY221");
    	
    	commonElemsJson.put(JSON_PROP_BOOKINGDATE, "2019-03-18T06:00:00");
    	commonElemsJson.put(JSON_PROP_TRAVELDATE, "2019-04-26T06:00:00");
    	commonElemsJson.put(JSON_PROP_TRANSACTDATE, "2019-04-12T00:00:00");
    	
        briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
        
        // Advance Definition --------
        JSONObject advDefnJson = new JSONObject();
        
        advDefnJson.put(JSON_PROP_BOOKINGDATE, "2019-03-26T00:00:00");
        advDefnJson.put(JSON_PROP_TRAVELDATE, "2019-04-26T06:00:00");
        advDefnJson.put(JSON_PROP_TRANSACTDATE, "2019-04-24T06:00:00");
        
        //TODO : To check if this is correct?
        //advDefnJson.put(JSON_PROP_NATIONALITY, clientCtx.getString(JSON_PROP_CLIENTMARKET));
        advDefnJson.put(JSON_PROP_NATIONALITY, "African");
        advDefnJson.put("ancillaryType", "ANCILLARYTYPE1");
        advDefnJson.put("ancillaryName", "ANCILLARYNAME1");

        briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);
        
        // SupplierPolicy Details -----
        JSONObject suppPolicyDetails = new JSONObject();
        suppPolicyDetails.put("supplierBufferPeriod", 4);
        suppPolicyDetails.put("policyAmount", 100);
        
        briJson.put("supplierPolicyDetails", suppPolicyDetails);
        
        JSONArray dynamicPackageArr = resBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
        
        JSONArray hotelDetailsArr = new JSONArray();
        for (int i = 0; i < dynamicPackageArr.length(); i++) {
        	JSONObject dynamicPkgJson = dynamicPackageArr.getJSONObject(i);
        	
        	if (dynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS).has(JSON_PROP_HOTEL_COMPONENT)) {
        		getHotelDetails(dynamicPkgJson, hotelDetailsArr);
			}
        }
        briJson.put(JSON_PROP_PACKAGEDETAILS, hotelDetailsArr);
        
        return briJson;	
	}

	private static void getHotelDetails(JSONObject dynamicPkgJson, JSONArray hotelDetailsArr) {
		
		JSONArray hotelCompArr = dynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS).getJSONArray(JSON_PROP_HOTEL_COMPONENT);
		for(int i=0; i<hotelCompArr.length(); i++) {
			JSONObject hotelCompJson = hotelCompArr.getJSONObject(i);
			JSONObject hotelDetails = new JSONObject();
			JSONArray roomDetailsArr = new JSONArray();
			
			String dynamicPkgAction = hotelCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
			if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_HOTEL_TOUR)) {
				hotelDetails.put(JSON_PROP_DYNAMICPKGACTION, dynamicPkgAction);
				hotelDetails.put(JSON_PROP_PRODUCTNAME, "PRODUCTNAME");
				hotelDetails.put(JSON_PROP_PRODUCTBRAND, "PRODUCTBRAND");
				
				getHotelRoomDetails(hotelCompJson, roomDetailsArr);
			}
			else if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_HOTEL_POST)) {
				hotelDetails.put(JSON_PROP_DYNAMICPKGACTION, dynamicPkgAction);
				hotelDetails.put(JSON_PROP_PRODUCTNAME, "PRODUCTNAME");
				hotelDetails.put(JSON_PROP_PRODUCTBRAND, "PRODUCTBRAND");
				
				getHotelRoomDetails(hotelCompJson, roomDetailsArr);
			}
			hotelDetails.put("roomDetails", roomDetailsArr);
			hotelDetailsArr.put(hotelDetails);
		}
	}

	private static void getHotelRoomDetails(JSONObject hotelCompJson, JSONArray roomDetailsArr) {
		
		JSONArray roomStayArr = hotelCompJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
		for (int j = 0; j < roomStayArr.length(); j++) {
			JSONObject roomDetails = new JSONObject();
			JSONObject roomStayJson = roomStayArr.getJSONObject(j);
			JSONArray roomrateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);
			String roomType = roomStayJson.get(JSON_PROP_ROOMTYPE).toString();
			roomDetails.put(JSON_PROP_ROOMTYPE, roomType);
			String roomCategory = roomStayJson.getString(JSON_PROP_ROOMCATEGORY);
			roomDetails.put(JSON_PROP_ROOMCATEGORY, roomCategory);
			roomDetails.put("fareType", "NonRefundable");
			roomDetails.put("applicableOn", "Per Room");
			roomDetails.put("mealPlan", "");
			roomDetails.put("rateType", 123456);
			roomDetails.put("rateCode", 987654);
			roomDetails.put("rating", "");
			for (int k = 0; k < roomrateArr.length(); k++) {
				JSONObject roomRateJson = roomrateArr.getJSONObject(k);
				totalFare = roomRateJson.getJSONObject(JSON_PROP_TOTAL).getDouble(JSON_PROP_AMOUNTAFTERTAX);
				roomDetails.put(JSON_PROP_TOTALFARE, totalFare);
				
				JSONArray fareDetailsArr = new JSONArray();
				JSONObject fareDetails = new JSONObject();
				
				fareDetails.put("sellingPriceComponentName", "Basic");
				fareDetails.put("sellingPriceComponentValue", totalFare);
				
				fareDetailsArr.put(fareDetails);
				roomDetails.put(JSON_PROP_FAREDETAILS, fareDetailsArr);
			}
			//roomDetails.put(JSON_PROP_PASSENGERDETAILS, getOffersPassengerDetailsJSON(roomStayJson));
			roomDetailsArr.put(roomDetails);
		}
	}
	
	private static void appendPolicyToResults(JSONObject resBodyJson, JSONObject companyPolicyRes) {
		
		JSONArray dynamicPackageArr = resBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		
		JSONObject prodDtlsJson = null;
        try {
        	prodDtlsJson = companyPolicyRes.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.holidays_companypolicy.Root").getJSONArray("businessRuleIntake").getJSONObject(0).getJSONObject("companyPolicyDetails");
        }
        catch (Exception x) {
        	logger.warn("Unable to retrieve first businessRuleIntake item. Aborting Company Offers response processing", x);
        	return;
        }
        
        //if (dynamicPackageArr.length() != prodDtlsJsonArr.length()) {
	//		logger.warn(
		//			"Number of dynamicPackage elements in BE result and number of productDetails elements in company offers result is different. Aborting Company Offers response processing.");
		//	return;
		//}
        
        for (int i = 0; i < dynamicPackageArr.length(); i++) {
        	JSONObject dynamicPkgJson = dynamicPackageArr.getJSONObject(i);
        	JSONArray hotelCompArr = dynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS).getJSONArray(JSON_PROP_HOTEL_COMPONENT);
        	JSONObject hotelComp = new JSONObject();
        	
        	double totalDiscountAmount = 0;
        	
        	for(int j=0; j<hotelCompArr.length(); j++) {
        		JSONObject hoteCompObj = hotelCompArr.getJSONObject(j);
        		JSONArray roomStayArr = hoteCompObj.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
        		
        		for (int k = 0; k < roomStayArr.length(); k++) {
        			JSONObject roomStayJson = roomStayArr.getJSONObject(k);
        			JSONArray roomrateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);
    
        			for (int l = 0; l < roomrateArr.length(); l++) {
        				JSONObject roomRateJson = roomrateArr.getJSONObject(l);
        				totalDiscountAmount = totalDiscountAmount + roomRateJson.getJSONObject(JSON_PROP_TOTAL).getDouble(JSON_PROP_AMOUNTAFTERTAX);
        			}
        		}
        		
        		double remainingAmount = prodDtlsJson.getDouble("policyCharges");
        		prodDtlsJson.put("RemainingAmount", totalDiscountAmount - remainingAmount);
        		hotelComp.put("companyPolicyDetails", prodDtlsJson);
        	}
        	dynamicPkgJson.put("cancellationAmount", hotelComp);
        }
        
	}
	
	public static JSONObject requestJSONTransformation(JSONObject requestJson) throws Exception {

		JSONObject requestBody = requestJson.getJSONObject(JSON_PROP_REQBODY);
		JSONArray dynamicPackageArray = requestBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);

		for (int i = 0; i < dynamicPackageArray.length(); i++) {
			JSONObject dynamicPackageObj = dynamicPackageArray.getJSONObject(i);
			JSONObject components = dynamicPackageObj.getJSONObject(JSON_PROP_COMPONENTS);
			String supplierID = dynamicPackageObj.getString(JSON_PROP_SUPPLIERID);

			if (components == null || components.length() == 0) {
				throw new Exception(String.format("Object components must be set for supplier %s", supplierID));
			}

			JSONArray hotelComponentsJsonArray = new JSONArray();
			JSONArray cruiseComponentsJsonArray = new JSONArray();

			// ForAccommodation Component
			if (components.has(JSON_PROP_HOTEL_COMPONENT)) {
				JSONObject hotelComponentJson = components.getJSONObject(JSON_PROP_HOTEL_COMPONENT);

				if (hotelComponentJson != null && hotelComponentJson.length() != 0) {
					hotelComponentsJsonArray.put(hotelComponentJson);

					if (components.has(JSON_PROP_PRENIGHT)) {
						JSONObject perNightObject = components.getJSONObject(JSON_PROP_PRENIGHT);

						if (perNightObject != null && perNightObject.length() != 0) {
							perNightObject.put(JSON_PROP_DYNAMICPKGACTION, DYNAMICPKGACTION_HOTEL_PRE);
							hotelComponentsJsonArray.put(perNightObject);
							components.remove(JSON_PROP_PRENIGHT);
						}
					}

					if (components.has(JSON_PROP_POSTNIGHT)) {
						JSONObject postNightNightObject = components.getJSONObject(JSON_PROP_POSTNIGHT);

						if (postNightNightObject != null && postNightNightObject.length() != 0) {
							postNightNightObject.put(JSON_PROP_DYNAMICPKGACTION, DYNAMICPKGACTION_HOTEL_POST);
							hotelComponentsJsonArray.put(postNightNightObject);
							components.remove(JSON_PROP_POSTNIGHT);
						}
					}

					components.remove(JSON_PROP_HOTEL_COMPONENT);
				}
			}
			components.put(JSON_PROP_HOTEL_COMPONENT, hotelComponentsJsonArray);

			// For Cruise Component
			if (components.has(JSON_PROP_CRUISE_COMPONENT)) {

				JSONObject cruiseComponentJson = components.getJSONObject(JSON_PROP_CRUISE_COMPONENT);

				if (cruiseComponentJson != null && cruiseComponentJson.length() != 0) {
					cruiseComponentsJsonArray.put(cruiseComponentJson);

					if (components.has(JSON_PROP_PRENIGHT)) {
						JSONObject perNightObject = components.getJSONObject(JSON_PROP_PRENIGHT);
						if (perNightObject != null && perNightObject.length() != 0) {
							perNightObject.put(JSON_PROP_DYNAMICPKGACTION, DYNAMICPKGACTION_CRUISE_PRE);
							cruiseComponentsJsonArray.put(perNightObject);
							components.remove(JSON_PROP_PRENIGHT);
						}
					}

					if (components.has(JSON_PROP_POSTNIGHT)) {
						JSONObject postNightNightObject = components.getJSONObject(JSON_PROP_POSTNIGHT);

						if (postNightNightObject != null && postNightNightObject.length() != 0) {
							postNightNightObject.put(JSON_PROP_DYNAMICPKGACTION, DYNAMICPKGACTION_CRUISE_POST);
							cruiseComponentsJsonArray.put(postNightNightObject);
							components.remove(JSON_PROP_POSTNIGHT);
						}
					}

					components.remove(JSON_PROP_CRUISE_COMPONENT);
				}
			}
			components.put(JSON_PROP_CRUISE_COMPONENT, cruiseComponentsJsonArray);
		}
		return requestJson;
	}
}
