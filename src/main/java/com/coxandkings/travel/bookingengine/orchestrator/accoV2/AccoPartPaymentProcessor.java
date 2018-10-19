package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.CommonUtil;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.PartPaymentConstants;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisHotelData;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;

public class AccoPartPaymentProcessor implements PartPaymentConstants{

	@SuppressWarnings("unchecked")
	public static String validatePartPayment(JSONObject reqJson, JSONObject resJson) throws ParseException {

		Double totalPrice = 0.0;
		JSONObject jTlObject = new JSONObject();
		JSONArray objectArray = new JSONArray();
		Date bookDate = new Date();
		String dueDate = "";
		JSONArray finalArray = new JSONArray();
		ArrayList <String> dates = new ArrayList<>();
		ArrayList <Boolean> depositRefundables = new ArrayList<>();
		ArrayList <Boolean> balanceRefundables = new ArrayList<>();
		SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

		// request parameters
		JSONObject roomJobj = new JSONObject();
		JSONArray roomJarray = new JSONArray();
		Date reqTravelDate = null;
		ArrayList<Date> reqTravel = new ArrayList<>();

		// response parameters
		String hotelCode = "",supplierRef = "";
		JSONObject roomStayObject = new JSONObject();
		JSONObject roomInfo = new JSONObject();
		JSONObject hotelInfo = new JSONObject();
		List<Boolean> partPaymentFlag = new ArrayList<Boolean>();
		Boolean isPartPayment = null;

		// Retrieve Part Payment details from MDM
		String mdmMode = "", mdmChain = "", mdmBrand = "", mdmCity = "", mdmContinent = "", mdmCountry = "", billingAmtAsPer = "";
		Double fromBillingAmt = 0.0,toBillingAmt = 0.0;
		List<Document> productAccomodation = new ArrayList<Document>();
		List<Document> paymentSchedule =  new ArrayList<Document>();
		List<String> mdmProductName = new ArrayList<String>();

		//request function
		JSONObject requestBody = reqJson.optJSONObject(JSON_PROP_REQUESTBODY);
		JSONArray requestAccommodationInfoJarray = requestBody.optJSONArray(JSON_PROP_ACCO_ACCOINFO);
		for (Object requestAccommodationInfo : requestAccommodationInfoJarray) {
			JSONObject requestAccommodationInfoObject = (JSONObject) requestAccommodationInfo;
			String requestCheckIn = requestAccommodationInfoObject.optString(JSON_PROP_ACCO_CHECKIN)+"T00:00:00";
			reqTravelDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(requestCheckIn);
			reqTravel.add(reqTravelDate);
			JSONArray requestRoomConfigJarray = requestAccommodationInfoObject.optJSONArray(JSON_PROP_ACCO_ROOMCONFIG);
			for (Object requestRoomConfig : requestRoomConfigJarray) {
				JSONObject requestRoomConfigObject = (JSONObject) requestRoomConfig;
				JSONObject requestRoomInfo = requestRoomConfigObject.optJSONObject(JSON_PROP_ACCO_ROOMINFO);
				JSONObject requestHotelInfo = requestRoomInfo.optJSONObject(JSON_PROP_ACCO_HOTELINFO);
				String requestHotelCode = requestHotelInfo.optString(JSON_PROP_ACCO_HOTELCODE);
				roomJobj.put(JSON_PROP_ACCO_HOTELCODE, requestHotelCode);
				JSONObject requestRoomTypeInfo = requestRoomInfo.optJSONObject(JSON_PROP_ACCO_ROOMTYPEINFO);
				String requestRoomTypeName = requestRoomTypeInfo.optString(JSON_PROP_ACCO_ROOMTYPENAME);
				roomJobj.put(JSON_PROP_ACCO_ROOMTYPENAME, requestRoomTypeName);
				String requestRoomCategoryName = requestRoomTypeInfo.optString(JSON_PROP_ACCO_ROOMCATEGORYNAME);
				roomJobj.put(JSON_PROP_ACCO_ROOMCATEGORYNAME, requestRoomCategoryName);
				JSONObject tempjObj = new JSONObject(roomJobj.toString());
				roomJarray.put(tempjObj);
			}
		}
		Collections.sort(reqTravel);
		reqTravelDate = reqTravel.get(0);

		JSONObject requestHeader = reqJson.optJSONObject(JSON_PROP_REQUESTHEADER);
		JSONObject clientContext = requestHeader.optJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientID = clientContext.optString(JSON_PROP_CLIENTID);
		String clientType = clientContext.optString(JSON_PROP_CLIENTTYPE);
		String clientMarket = clientContext.optString(JSON_PROP_CLIENTMARKET);

		//response function
		JSONObject responseBody = resJson.optJSONObject(JSON_PROP_RESPONSEBODY);
		JSONArray accommodationInfoJarray = responseBody.optJSONArray(JSON_PROP_ACCO_ACCOINFO);
		for (Object accommodationInfo : accommodationInfoJarray) {
			JSONObject accomodationInfoObject = (JSONObject) accommodationInfo;
			JSONArray roomStayJarray = accomodationInfoObject.optJSONArray(JSON_PROP_ACCO_ROOMSTAY);
			for (Object roomStay : roomStayJarray) {
				roomStayObject = (JSONObject) roomStay;
				JSONObject totalPriceInfo = roomStayObject.optJSONObject(JSON_PROP_ACCO_TOTALPRICEINFO);
				roomInfo = roomStayObject.optJSONObject(JSON_PROP_ACCO_ROOMINFO);
				hotelInfo = roomInfo.optJSONObject(JSON_PROP_ACCO_HOTELINFO);
				hotelCode = hotelInfo.optString(JSON_PROP_ACCO_HOTELCODE);
				JSONObject roomTypeInfo = roomInfo.optJSONObject(JSON_PROP_ACCO_ROOMTYPEINFO);
				String roomTypeName = roomTypeInfo.optString(JSON_PROP_ACCO_ROOMTYPENAME);
				String roomCategoryName = roomTypeInfo.optString(JSON_PROP_ACCO_ROOMCATEGORYNAME);
				for(Object roomArray : roomJarray) {
					if (hotelCode.equalsIgnoreCase(((JSONObject) roomArray).optString(JSON_PROP_ACCO_HOTELCODE)) && 
							roomTypeName.equalsIgnoreCase(((JSONObject) roomArray).optString(JSON_PROP_ACCO_ROOMTYPENAME)) &&
							roomCategoryName.equalsIgnoreCase(((JSONObject) roomArray).optString(JSON_PROP_ACCO_ROOMCATEGORYNAME))) {
						Double amount = totalPriceInfo.optDouble(JSON_PROP_ACCO_AMOUNT);
						totalPrice += amount;
					}
				}
			}
		}
		
		responseBody = (JSONObject) resJson.get(JSON_PROP_RESPONSEBODY);
		accommodationInfoJarray = responseBody.optJSONArray(JSON_PROP_ACCO_ACCOINFO);
		for (Object accommodationInfo : accommodationInfoJarray) {
			if(((JSONObject) accommodationInfo).has("errorMsg")) {
				continue;
			}
			JSONObject accomodationInfoObject = (JSONObject) accommodationInfo;
			JSONArray roomStayJarray = accomodationInfoObject.optJSONArray(JSON_PROP_ACCO_ROOMSTAY);
			roomStayObject = roomStayJarray.getJSONObject(0);
			String accommodationSubType = roomStayObject.optString(JSON_PROP_ACCO_ACCOSUBTYPE);
			roomInfo = roomStayObject.optJSONObject(JSON_PROP_ACCO_ROOMINFO);
			hotelInfo = roomInfo.optJSONObject(JSON_PROP_ACCO_HOTELINFO);
			hotelCode = hotelInfo.optString(JSON_PROP_ACCO_HOTELCODE);

			String name = RedisHotelData.getHotelInfo(hotelCode, NAME);
			String city = RedisHotelData.getHotelInfo(hotelCode, CITY);
			String brand = RedisHotelData.getHotelInfo(hotelCode, BRAND);
			String chain = RedisHotelData.getHotelInfo(hotelCode, CHAIN);

			String continent = RedisCityData.getCityInfo(city, CONTINENT);
			String country = RedisCityData.getCityInfo(city, COUNTRY);

			////
			UserContext usrCtx=UserContext.getUserContextForSession(requestHeader);
			ClientInfo[] clientInfoArr=usrCtx.getClientCommercialsHierarchyArray();
			String entityType = null,entityid;
			if(clientType.equalsIgnoreCase(Constants.CLIENT_TYPE_B2C))  {
				entityid = usrCtx.getClientCommercialsHierarchyArray()[0].getCommercialsEntityId().toString();
				entityType =  usrCtx.getClientCommercialsHierarchyArray()[0].getCommercialsEntityType().toString(); 
				
			}
			else {
				entityid =clientID; 
				for(int d=0;d<clientInfoArr.length;d++) {
					if((usrCtx.getClientCommercialsHierarchyArray()[d].getClientId().toString()).equalsIgnoreCase(entityid)) {
						entityType =  usrCtx.getClientCommercialsHierarchyArray()[d].getCommercialsEntityType().toString(); 
					}
				}
				
			}
			
			//FindIterable<Document> MDMPartPayment = MDMUtils.getPartPaymentDetails(ACCOMMODATION, accommodationSubType,entityid, entityType, clientMarket);
			
			List<Document> MDMPartPayment = MDMUtils.getPartPaymentDetailsV2(ACCOMMODATION, accommodationSubType,entityid, entityType, clientMarket);
			//MongoCursor<Document> MDMpartIter = MDMPartPayment.iterator();
			//while (MDMpartIter.hasNext()) {
			for(Document mdmPartPayment:MDMPartPayment) {
				//Document mdmPartPayment = MDMpartIter.next();
				mdmMode = mdmPartPayment.getString(MDM_PART_MODE);

				paymentSchedule = (List<Document>) mdmPartPayment.get(MDM_PART_PAYMENTSCHEDULE);
				for(Document paymentScheduleArray : paymentSchedule) {
					fromBillingAmt = Double.parseDouble(paymentScheduleArray.getString(MDM_PART_FROMBILLINGAMOUNT));
					toBillingAmt = Double.parseDouble(paymentScheduleArray.getString(MDM_PART_TOBILLINGAMOUNT));
					billingAmtAsPer = paymentScheduleArray.getString(MDM_PART_BILLINGAMTASPER);
				}

				productAccomodation = (List<Document>) mdmPartPayment.get(MDM_PART_PRODUCTACCO);
				for (Document productAccomodationArray : productAccomodation) {
					mdmChain = productAccomodationArray.getString(MDM_PART_CHAIN);
					mdmBrand = productAccomodationArray.getString(MDM_PART_BRAND);
					mdmCity = productAccomodationArray.getString(MDM_PART_CITY);
					mdmContinent = productAccomodationArray.getString(MDM_PART_CONTINENT);
					mdmCountry = productAccomodationArray.getString(MDM_PART_COUNTRY);
					mdmProductName = (List<String>) productAccomodationArray.get(MDM_PART_PRODUCTNAME);

					if (mdmMode!=null && mdmMode.equalsIgnoreCase(MDM_PART_MODE_INCLUDE)) {
						if (CommonUtil.checkStringParams(continent, mdmContinent, MDM_PART_CONTINENT) && 
								CommonUtil.checkStringParams(country, mdmCountry, MDM_PART_COUNTRY) && 
								CommonUtil.checkStringParams(city, mdmCity, MDM_PART_CITY) && CommonUtil.checkStringParams(brand, mdmBrand, MDM_PART_BRAND) && 
								CommonUtil.checkStringParams(chain, mdmChain, MDM_PART_CHAIN) && !mdmProductName.isEmpty() && 
								mdmProductName.contains(name)) {

							if(fromBillingAmt < totalPrice && totalPrice < toBillingAmt) {
								partPaymentFlag.add(true);
								jTlObject = generateSchedule(mdmPartPayment, reqTravelDate, supplierRef, totalPrice,bookDate);
								if(jTlObject.length() > 0)
									objectArray.put(jTlObject);
							}
							else
								continue;
						}
						else if(CommonUtil.checkStringParams(continent, mdmContinent, MDM_PART_CONTINENT) && 
								CommonUtil.checkStringParams(country, mdmCountry, MDM_PART_COUNTRY) && 
								CommonUtil.checkStringParams(city, mdmCity, MDM_PART_CITY) && CommonUtil.checkStringParams(brand, mdmBrand, MDM_PART_BRAND) && 
								CommonUtil.checkStringParams(chain, mdmChain, MDM_PART_CHAIN) && mdmProductName.isEmpty()) {

							if(fromBillingAmt < totalPrice && totalPrice < toBillingAmt) {
								partPaymentFlag.add(true);
								jTlObject = generateSchedule(mdmPartPayment, reqTravelDate, supplierRef, totalPrice,bookDate);
								if(jTlObject.length() > 0)
									objectArray.put(jTlObject);
							}
							else
								continue;
						}	
						else
							continue;
					}

					else if (mdmMode!=null && mdmMode.equalsIgnoreCase(MDM_PART_MODE_EXCLUDE)) {
						if (CommonUtil.checkStringParams(continent, mdmContinent, MDM_PART_CONTINENT) && 
								CommonUtil.checkStringParams(country, mdmCountry,MDM_PART_COUNTRY) && 
								CommonUtil.checkStringParams(city, mdmCity, MDM_PART_CITY) && CommonUtil.checkStringParams(brand, mdmBrand, MDM_PART_BRAND) &&
								CommonUtil.checkStringParams(chain, mdmChain, MDM_PART_CHAIN) && !mdmProductName.isEmpty() && 
								mdmProductName.contains(name)) {

							partPaymentFlag.add(false);
						}	
						else if(CommonUtil.checkStringParams(continent, mdmContinent, MDM_PART_CONTINENT) && 
								CommonUtil.checkStringParams(country, mdmCountry, MDM_PART_COUNTRY) && 
								CommonUtil.checkStringParams(city, mdmCity, MDM_PART_CITY) && CommonUtil.checkStringParams(brand, mdmBrand, MDM_PART_BRAND) &&
								CommonUtil.checkStringParams(chain, mdmChain, MDM_PART_CHAIN) && mdmProductName.isEmpty()) {

							partPaymentFlag.add(false);
						}
						else {
							if(fromBillingAmt < totalPrice && totalPrice < toBillingAmt) {
								partPaymentFlag.add(true);
								jTlObject = generateSchedule(mdmPartPayment, reqTravelDate, supplierRef, totalPrice,bookDate);
								if(jTlObject.length() > 0)
									objectArray.put(jTlObject);
							}
							else
								partPaymentFlag.add(false);
						}
					}
				}
			}
		}


		if(objectArray.length() > 0 &&  objectArray.length() == accommodationInfoJarray.length()) {
			BigDecimal deposits = new BigDecimal(0);
			BigDecimal balance = new BigDecimal(0);
			for(Object schedules : objectArray) {
				JSONObject schedule = (JSONObject) schedules;
				JSONArray paymentScheduleArray = schedule.optJSONArray("paymentSchedule");
				for(Object paymentSchedules : paymentScheduleArray) {
					JSONObject paymentScheduled = (JSONObject) paymentSchedules;
					if(paymentScheduled.optString("paymentScheduleType").contains("deposit")) {
						deposits = deposits.add(new BigDecimal(paymentScheduled.optDouble("dueAmount")));
						dueDate = paymentScheduled.optString("dueDate");
						depositRefundables.add(paymentScheduled.optBoolean("isRefundable"));
					}
					else {
						balance = balance.add(new BigDecimal(paymentScheduled.optDouble("dueAmount")));
						dates.add(paymentScheduled.optString("dueDate"));		//adding balances
						balanceRefundables.add(paymentScheduled.optBoolean("isRefundable"));
					}
				}
			}
			//add json object
			JSONObject finalSchedule = new JSONObject();
			Collections.sort(dates);

			String date1 = DATE_FORMAT.format(DATE_FORMAT.parse(dates.get(0)));
			String date2 = DATE_FORMAT.format(DATE_FORMAT.parse(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(bookDate)));
			if(date1.equalsIgnoreCase(date2)) {
				responseBody.put("isPartPayment", false);
			}
			else {
				finalSchedule.put("paymentScheduleType","Initial Deposit");
				finalSchedule.put("dueAmount", deposits.divide(new BigDecimal(objectArray.length())));
				finalSchedule.put("dueDate", dueDate);
				if(depositRefundables.contains(false))
					finalSchedule.put("isRefundable", false);
				else
					finalSchedule.put("isRefundable", true);
				finalArray.put(finalSchedule);

				finalSchedule = new JSONObject();
				finalSchedule.put("paymentScheduleType","Balance Payment");
				finalSchedule.put("dueAmount", balance.divide(new BigDecimal(objectArray.length())));
				finalSchedule.put("dueDate", dates.get(0));
				if(balanceRefundables.contains(false))
					finalSchedule.put("isRefundable", false);
				else
					finalSchedule.put("isRefundable", true);
				finalArray.put(finalSchedule);

				responseBody.put("isPartPayment", true);
				responseBody.put("partPayment", finalArray);
			}
		}
		else{
			responseBody.put("isPartPayment", false);
		}
		return responseBody.toString();
	}

	public static JSONObject generateSchedule(Document mdmPartPayment, Date travelDateReq, String supplierRef, Double totalPrice, Date bookDate) throws ParseException {

		BigDecimal percentage = new BigDecimal(0);
		BigDecimal amount = new BigDecimal(0);
		Document periodicity = new Document();
		List<Document> paymentScheduleArray = new ArrayList<Document>();
		JSONObject finalObject = new JSONObject();
		JSONArray myArray = new JSONArray();
		BigDecimal totalAmount = new BigDecimal(totalPrice);
		BigDecimal duplicatedTotalFare = new BigDecimal(totalPrice);
		SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

		periodicity = CommonUtil.fetchSupplierSettlement(supplierRef);

		paymentScheduleArray = (List<Document>) mdmPartPayment.get(MDM_PART_PAYMENTSCHEDULE);
		for(Document paymentSchedule : paymentScheduleArray) {
			String paymentSchdType = paymentSchedule.getString(MDM_PART_PAYMENTSCHDTYPE);
			String paymentDueDate = paymentSchedule.getString(MDM_PART_PAYMENTDUEDATE);
			Document paymentDue = (Document) paymentSchedule.get(MDM_PART_PAYMENTDUE);
			String durationType = paymentDue.getString(MDM_PART_DURATIONTYPE);
			boolean isRefundable = paymentSchedule.getBoolean(MDM_PART_REFUNDABLE);
			int count = paymentDue.getInteger(MDM_PART_COUNT);
			Document value = (Document)paymentSchedule.get(MDM_PART_VALUE);
			String mode = value.getString(MDM_PART_VALUE_MODE);
			if(value.containsKey(MDM_PART_VALUE_MODE)) {
				if(mode.equalsIgnoreCase(MDM_PART_PERCENTAGE))
					percentage = new BigDecimal(value.getInteger(MDM_PART_PERCENTAGE));
				else if(value.containsKey(MDM_PART_CURRENCYAMT)) {
					amount = new BigDecimal(value.getInteger(MDM_PART_CURRENCYAMT));
					duplicatedTotalFare = (duplicatedTotalFare).subtract(amount);
				}
			}
			String priceComponent = value.getString(MDM_PART_PRICECOMPONENT);
			BigDecimal dueAmount = CommonUtil.calculateValue(mode,percentage,amount,totalAmount);
			JSONObject jObject = new JSONObject();
			Date final_date = null;
			if(!paymentSchdType.contains(MDM_PART_DEPOSIT)) {
				if(!paymentDueDate.equalsIgnoreCase(MDM_PART_PAYMENTDUEDATE_SUPPLIERSETTLEMENT)) {
					Calendar paymentDate = CommonUtil.calculatePaymentDate(paymentDueDate, bookDate, travelDateReq, durationType, count);
					final_date = CommonUtil.checkSettlementTerms(periodicity,paymentDate.getTime(),travelDateReq);
				}
				else {
					final_date = CommonUtil.checkSettlementTerms(periodicity,travelDateReq,travelDateReq);
				}
				if(value.containsKey(MDM_PART_VALUE_MODE))
				{
					if(mode.equalsIgnoreCase(MDM_PART_AMOUNT))
						dueAmount = duplicatedTotalFare;
				}
				jObject.put("paymentScheduleType", paymentSchdType);
				jObject.put("dueDate", DATE_FORMAT.format(final_date));
				jObject.put("dueAmount", dueAmount);
				jObject.put("isRefundable", isRefundable);
				myArray.put(jObject);
			}
			else {
				jObject.put("paymentScheduleType", paymentSchdType);
				jObject.put("dueDate", DATE_FORMAT.format(bookDate));
				jObject.put("dueAmount", dueAmount);
				jObject.put("isRefundable", isRefundable);
				myArray.put(jObject);
			}
		}
		finalObject.put("paymentSchedule",myArray);
		return finalObject;
	}
}


