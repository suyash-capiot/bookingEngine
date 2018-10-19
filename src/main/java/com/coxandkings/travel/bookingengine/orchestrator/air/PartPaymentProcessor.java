package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.math.BigDecimal;
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
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirlineData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirportData;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;

public class PartPaymentProcessor implements PartPaymentConstants{

	@SuppressWarnings("unchecked")
	public static String validateTheData(JSONObject jsonRequest, JSONObject jsonResponse, UserContext usrCtx) throws Exception  {
		String countryFrom = "", countryTo = "", cityFrom = "", cityTo = "", airlineName = "";
		String MDMcountryFrom = "", MDMcountryTo = "", MDMcityFrom = "", MDMcityTo = "", MDMmode = "",MDMbillingAmountAsPer = "";
		String entityid = "", entityType = "", entityMarket = "";
		List<String> MDMairlineName = new ArrayList<String>();
		List<Double> paxTypeFareList = new ArrayList<Double>();
		int adults = 0, children = 0, passengers = 0, index = 0,noOfFlights = 0;
		double totalFare = 0.0, perPaxFare = 0.0, perTransactionFare = 0.0, toBillingAmount = 0.0,fromBillingAmount = 0.0;
		boolean isPartPaymentValidated = false;
		List<String> FlagList = new ArrayList<String>();
		JSONArray scheduleArray = new JSONArray();
		JSONArray flightSegmentArray = new JSONArray();
		ArrayList <Date> deptDates= new ArrayList<Date>();
		Document partPayment = null;
		Date bookDate = new Date();
		SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
		// processing response header
		JSONObject requestHeader = (JSONObject) jsonRequest.opt(JSON_PROP_REQUESTHEADER);
		JSONObject clientContext = (JSONObject) requestHeader.opt(JSON_PROP_CLIENTCONTEXT);
		ClientInfo[] clientInfoArr=usrCtx.getClientCommercialsHierarchyArray();
		entityType =  usrCtx.getClientCommercialsHierarchyArray()[0].getCommercialsEntityType().toString(); 
		if(clientContext.optString(JSON_PROP_CLIENTTYPE).equalsIgnoreCase(Constants.CLIENT_TYPE_B2C))  {
			entityid = usrCtx.getClientCommercialsHierarchyArray()[0].getCommercialsEntityId().toString();
			entityType =  usrCtx.getClientCommercialsHierarchyArray()[0].getCommercialsEntityType().toString(); 
			
		}
		else {
			entityid = clientContext.optString(JSON_PROP_CLIENTID); 
			for(int d=0;d<clientInfoArr.length;d++) {
				if((usrCtx.getClientCommercialsHierarchyArray()[d].getCommercialsEntityId().toString()).equalsIgnoreCase(entityid)) {
					entityType =  usrCtx.getClientCommercialsHierarchyArray()[d].getCommercialsEntityType().toString(); 
				}
			}
			
		}
		//entityid = clientContext.optString(JSON_PROP_CLIENTID); // clientId - entityId
		//entityType = clientContext.optString(JSON_PROP_CLIENTTYPE); // clientType - entityType
		entityMarket = clientContext.optString(JSON_PROP_CLIENTMARKET); // clientMarket - entityMarket - companyMarket

		//System.out.println("\nentityid : " + entityid + " entityType : " + entityType + " entityMarket : " + entityMarket);

		// Mongo
		//FindIterable<Document> MDMPartPayment = MDMUtils.getPartPaymentDetails(AIR_PRODUCTCAT, AIR_PRODUCTCATSUBTYPE, entityid,entityType, entityMarket);
		List<Document> MDMPartPayment = MDMUtils.getPartPaymentDetailsV2(AIR_PRODUCTCAT, AIR_PRODUCTCATSUBTYPE, entityid,entityType, entityMarket);
		
		//take fare from response body
		JSONObject response = (JSONObject) jsonResponse.opt(JSON_PROP_RESPONSEBODY);
		JSONArray pricedIteneraryArray = (JSONArray) response.opt(JSON_PROP_AIR_PRICEDITINERARY);
		for (Object iteneraries : pricedIteneraryArray) {
			JSONObject pricedItenerary = (JSONObject) iteneraries;
			JSONObject airItineraryPricingInfo = (JSONObject) pricedItenerary.opt(JSON_PROP_AIR_AIRITINERARYPRICINGINFO);
			JSONObject itinTotalFare = (JSONObject) airItineraryPricingInfo.opt(JSON_PROP_AIR_ITINTOTALFARE);
			totalFare += itinTotalFare.optDouble(JSON_PROP_AIR_ITINAMOUNT);
			JSONArray paxTypeFaresArray = (JSONArray) airItineraryPricingInfo.opt(JSON_PROP_AIR_PAXTYPEFARES);
			for (Object fares : paxTypeFaresArray) {
				JSONObject paxTypeFares = (JSONObject) fares;
				JSONObject totalFareObject = (JSONObject) paxTypeFares.opt(JSON_PROP_AIR_PAXTYPETOTALFARE);
				paxTypeFareList.add(totalFareObject.optDouble(JSON_PROP_AIR_PAXTYPEAMOUNT));
				//System.out.println("paxTypeFare : " + paxTypeFareList); // per passenger fare
			}
		}
		
		//get earliest date from request
				for (Object iteneraries : pricedIteneraryArray) {
					JSONObject pricedItenerary = (JSONObject) iteneraries;
					JSONObject airItenerary = (JSONObject) pricedItenerary.opt(JSON_PROP_AIR_AIRITINERARY);
					JSONArray originDestinationOptionsArray = (JSONArray) airItenerary.opt(JSON_PROP_AIR_ORIGINDESTINATIONOPTIONS);
					for (Object options : originDestinationOptionsArray) {
						JSONObject originDestinationOption = (JSONObject) options;
						flightSegmentArray = (JSONArray) originDestinationOption.opt(JSON_PROP_AIR_FLIGHTSEGMENT);
						for (Object flights : flightSegmentArray) {
							JSONObject flightSegment = (JSONObject) flights;
							String flightDate= flightSegment.getString(JSON_PROP_AIR_DEPARTUREDATE);
							Date travelDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:SS").parse(flightDate);
							deptDates.add(travelDate);
						}
					}
				}
				//System.out.println("deptDates before : "+deptDates);
				Collections.sort(deptDates);
				//System.out.println("deptDates after : "+deptDates);
				Date travellingDate = deptDates.get(0);
				//System.out.println("travellingDate from reprice : "+travellingDate);

		// calculating the total fare
		//System.out.println("totalFare :-- " + totalFare);

		// processing the responseBody

		for (Object iteneraries : pricedIteneraryArray) {
			JSONObject pricedItenerary = (JSONObject) iteneraries;
			JSONObject airItenerary = (JSONObject) pricedItenerary.opt(JSON_PROP_AIR_AIRITINERARY);
			JSONObject airItineraryPricingInfo = (JSONObject) pricedItenerary.opt(JSON_PROP_AIR_AIRITINERARYPRICINGINFO);
			JSONObject itinTotalFare = (JSONObject) airItineraryPricingInfo.opt(JSON_PROP_AIR_ITINTOTALFARE);
			String supplierRef = pricedItenerary.optString(JSON_PROP_AIR_SUPPLIERREF);
			JSONArray originDestinationOptionsArray = (JSONArray) airItenerary.opt(JSON_PROP_AIR_ORIGINDESTINATIONOPTIONS);
			for (Object options : originDestinationOptionsArray) {
				JSONObject originDestinationOption = (JSONObject) options;
				flightSegmentArray = (JSONArray) originDestinationOption.opt(JSON_PROP_AIR_FLIGHTSEGMENT);
				index += flightSegmentArray.length();
				//System.out.println("index :-- " + index);
				for (Object flightSegments : flightSegmentArray) {
					noOfFlights += 1;
					JSONObject flightSegment = (JSONObject) flightSegments;
					String originLocation = flightSegment.optString(JSON_PROP_AIR_ORIGINLOCATION);
					String destinationLocation = flightSegment.optString(JSON_PROP_AIR_DESTINATIONLOCATION);

					countryFrom = RedisAirportData.getAirportInfo(originLocation, AIR_COUNTRY); // countryFrom
					cityFrom = RedisAirportData.getAirportInfo(originLocation, AIR_CITY); // cityFrom
					countryTo = RedisAirportData.getAirportInfo(destinationLocation, AIR_COUNTRY); // countryTo
					cityTo = RedisAirportData.getAirportInfo(destinationLocation, AIR_CITY); // cityTo
					//System.out.println("\noriginLocation:" + originLocation + " countryFrom:" + countryFrom+ " cityFrom:" + cityFrom + " destinationLocation:" + destinationLocation + " countryTo:"+ countryTo + " cityTo:" + cityTo);

					JSONObject operatingAirline = (JSONObject) flightSegment.opt(JSON_PROP_AIR_OPERATINGAIRLINE);
					airlineName = RedisAirlineData.getAirlineDetails(operatingAirline.optString(JSON_PROP_AIR_AIRLINECODE), AIR_AIRLINENAME); // airlineName
					//System.out.println("airlineName : " + airlineName);

					//MongoCursor<Document> MDMpartIter = MDMPartPayment.iterator();
					//while (MDMpartIter.hasNext()) {
					for(Document mdmPartPayment:MDMPartPayment) {
						//Document mdmPartPayment = MDMpartIter.next();
						MDMmode = mdmPartPayment.getString(MDM_PART_MODE);
						List<Document> productsFlightArray = (List<Document>) mdmPartPayment.get(MDM_PART_PRODUCTFLIGHT);
						for (Document productFlight : productsFlightArray) {
							MDMcountryTo = productFlight.getString(MDM_PART_COUNTRYTO);
							MDMcountryFrom = productFlight.getString(MDM_PART_COUNTRYFROM);
							MDMcityFrom = productFlight.getString(MDM_PART_CITYFROM);
							MDMcityTo = productFlight.getString(MDM_PART_CITYTO);
							MDMairlineName = (List<String>) productFlight.get(MDM_PART_AIRLINENAME);
							//System.out.println("\nMDMcountryFrom=" + MDMcountryFrom + " MDMcityFrom=" + MDMcityFrom+ " MDMcountryTo=" + MDMcountryTo + " MDMcityTo=" + MDMcityTo + " MDMairName="+ MDMairlineName);
							//System.out.println("mode = " + MDMmode);
							List<Document> paymentScheduleArray = (List<Document>) mdmPartPayment.get(MDM_PART_PAYMENTSCHEDULE);
							for (Document paymentSchedule : paymentScheduleArray) {
								MDMbillingAmountAsPer = paymentSchedule.getString(MDM_PART_BILLINGAMTASPER);
								fromBillingAmount = Double.parseDouble(paymentSchedule.getString(MDM_PART_FROMBILLINGAMOUNT));
								toBillingAmount = Double.parseDouble(paymentSchedule.getString(MDM_PART_TOBILLINGAMOUNT));
							}
								if (MDMmode.equalsIgnoreCase(MDM_PART_MODE_INCLUDE)) {
									if (CommonUtil.checkStringParams(countryFrom, MDMcountryFrom, "countryFrom")
											&& CommonUtil.checkStringParams(cityFrom, MDMcityFrom, "cityFrom")
											&& CommonUtil.checkStringParams(countryTo, MDMcountryTo, "countryTo")
											&& CommonUtil.checkStringParams(cityTo, MDMcityTo, "cityTo")
											&& !MDMairlineName.isEmpty() && MDMairlineName.contains(airlineName)) {
										if (MDMbillingAmountAsPer.equalsIgnoreCase(MDM_PART_BILLINGAMTASPER_PERPAX)) {
											if (CommonUtil.comparePerPaxFare(fromBillingAmount,toBillingAmount,paxTypeFareList)) {
												//System.out.println("Inclusion true , Billing amount matched true, flag true");
												FlagList.add("true");
												isPartPaymentValidated = true;
												//scheduleArray.put(generatePaymentSchedule(totalFare,mdmPartPayment,supplierRef,travellingDate));
											} else {
												//System.out.println("Inclusion true ,Billing amount matched false, flag false");
												// FlagList.add("false");
												isPartPaymentValidated = false;
											}
										} else if (MDMbillingAmountAsPer.equalsIgnoreCase(MDM_PART_BILLINGAMTASPER_PERTRANS)) {
											if (totalFare <= toBillingAmount && totalFare >= fromBillingAmount) {
												//System.out.println("Inclusion true............. ,Billing amount matched true, flag true");
												FlagList.add("true");
												isPartPaymentValidated = true;
												//scheduleArray.put(generatePaymentSchedule(totalFare,mdmPartPayment,supplierRef,travellingDate));
											} else {
												//System.out.println("Inclusion true ,Billing amount matched false, flag false");
												// FlagList.add("false");
												isPartPaymentValidated = false;
											}
										}
									} 
									else if (CommonUtil.checkStringParams(countryFrom, MDMcountryFrom, "countryFrom")
											&& CommonUtil.checkStringParams(cityFrom, MDMcityFrom, "cityFrom")
											&& CommonUtil.checkStringParams(countryTo, MDMcountryTo, "countryTo")
											&& CommonUtil.checkStringParams(cityTo, MDMcityTo, "cityTo")
											&& MDMairlineName.isEmpty()) {
										if (MDMbillingAmountAsPer.equalsIgnoreCase(MDM_PART_BILLINGAMTASPER_PERPAX)) {
											if (CommonUtil.comparePerPaxFare(fromBillingAmount,toBillingAmount,paxTypeFareList)) {
												//System.out.println("Inclusion true , Billing amount matched true, flag true");
												FlagList.add("true");
												isPartPaymentValidated = true;
												//scheduleArray.put(generatePaymentSchedule(totalFare,mdmPartPayment,supplierRef,travellingDate));
											} else {
												//System.out.println("Inclusion true ,Billing amount matched false, flag false");
												// FlagList.add("false");
												isPartPaymentValidated = false;
											}
										} else if (MDMbillingAmountAsPer.equalsIgnoreCase(MDM_PART_BILLINGAMTASPER_PERTRANS)) {
											if (totalFare <= toBillingAmount && totalFare >= fromBillingAmount) {
												//System.out.println("Inclusion true............. ,Billing amount matched true, flag true");
												FlagList.add("true");
												isPartPaymentValidated = true;
												//scheduleArray.put(generatePaymentSchedule(totalFare,mdmPartPayment,supplierRef,travellingDate));
											} else {
												//System.out.println("Inclusion true ,Billing amount matched false, flag false");
												// FlagList.add("false");
												isPartPaymentValidated = false;
											}
										}
									}
									
									else {
										//System.out.println("Inclusion false");
										// FlagList.add("false");
										isPartPaymentValidated = false;
									}
								}
								

								if (MDMmode.equalsIgnoreCase(MDM_PART_MODE_EXCLUDE)) {
									if (CommonUtil.checkStringParams(countryFrom, MDMcountryFrom, countryFrom)
											&& CommonUtil.checkStringParams(cityFrom, MDMcityFrom, cityFrom)
											&& CommonUtil.checkStringParams(countryTo, MDMcountryTo, countryTo)
											&& CommonUtil.checkStringParams(cityTo, MDMcityTo, cityTo)
											&& !MDMairlineName.isEmpty() && MDMairlineName.contains(airlineName)) {
										//System.out.println("Exclusion checked true ..found the exclusion.. false");
										//										FlagList.add("false");
										isPartPaymentValidated = false;
									} 
									else if (CommonUtil.checkStringParams(countryFrom, MDMcountryFrom, countryFrom)
											&& CommonUtil.checkStringParams(cityFrom, MDMcityFrom, cityFrom)
											&& CommonUtil.checkStringParams(countryTo, MDMcountryTo, countryTo)
											&& CommonUtil.checkStringParams(cityTo, MDMcityTo, cityTo)
											&& MDMairlineName.isEmpty()) {
										//System.out.println("Exclusion checked true ..found the exclusion.. false");
										//										FlagList.add("false");
										isPartPaymentValidated = false;
									} 
									else {
										if (MDMbillingAmountAsPer.equalsIgnoreCase(MDM_PART_BILLINGAMTASPER_PERPAX)) {
											if (CommonUtil.comparePerPaxFare(fromBillingAmount,toBillingAmount,paxTypeFareList)) {
												//System.out.println("Exclusion true , Billing amount matched true, flag true");
												FlagList.add("true");
												isPartPaymentValidated = true;	//send mdmPartpayment for generation
												//scheduleArray.put(generatePaymentSchedule(totalFare,mdmPartPayment,supplierRef,travellingDate));
											} else {
												//System.out.println("Exclusion true ,Billing amount matched false, flag false");
												//												FlagList.add("false");
												isPartPaymentValidated = false;
											}
										} else if (MDMbillingAmountAsPer.equalsIgnoreCase(MDM_PART_BILLINGAMTASPER_PERTRANS)) {
											if (totalFare <= toBillingAmount && totalFare >= fromBillingAmount) {
												//System.out.println("Exclusion true ,Billing amount matched true, flag true");
												FlagList.add("true");
												isPartPaymentValidated = true;
												//scheduleArray.put(generatePaymentSchedule(totalFare,mdmPartPayment,supplierRef,travellingDate));
											} else {
												//System.out.println("Exclusion true ,Billing amount matched false, flag false");
												//												FlagList.add("false");
												isPartPaymentValidated = false;
											}
										}
									}
								}
						}
					partPayment = mdmPartPayment;
					}
				}
			}
//			System.out.println("noOfFlights : "+noOfFlights);
//			System.out.println("FlagList.size() : "+FlagList.size());
			if(noOfFlights==FlagList.size()) {
				scheduleArray.put(generatePaymentSchedule(totalFare,partPayment,supplierRef,travellingDate,bookDate));
			}
		}
//		System.out.println("scheduleGenerated :"+scheduleArray);
		String dueDate = "";
		JSONArray finalArray = new JSONArray();
		ArrayList <String> dates = new ArrayList<>();
		ArrayList <Boolean> depositRefundables = new ArrayList<>();
		ArrayList <Boolean> balanceRefundables = new ArrayList<>();
		if(scheduleArray.length() > 0 &&  scheduleArray.length() == pricedIteneraryArray.length()) {
			BigDecimal deposits = new BigDecimal(0);
			BigDecimal balance = new BigDecimal(0);
//			System.out.println("scheduleArray.size : "+scheduleArray.length());
			for(Object schedules : scheduleArray) {
				JSONObject schedule = (JSONObject) schedules;
				JSONArray paymentScheduleArray = schedule.optJSONArray("paymentSchedule");
				for(Object paymentSchedules : paymentScheduleArray) {
					JSONObject paymentSchedule = (JSONObject) paymentSchedules;
					if(paymentSchedule.optString("paymentScheduleType").contains("deposit")) {
						deposits = deposits.add(new BigDecimal(paymentSchedule.optDouble("dueAmount")));
						dueDate = paymentSchedule.optString("dueDate");
						depositRefundables.add(paymentSchedule.optBoolean("isRefundable"));
					}
					else {
						balance = balance.add(new BigDecimal(paymentSchedule.optDouble("dueAmount")));
						dates.add(paymentSchedule.optString("dueDate"));		//adding balances
						balanceRefundables.add(paymentSchedule.optBoolean("isRefundable"));
					}
				}
			}
			//add json object
			JSONObject finalSchedule = new JSONObject();
			Collections.sort(dates);
			
			String date1 = DATE_FORMAT.format(DATE_FORMAT.parse(dates.get(0)));
			String date2 = DATE_FORMAT.format(DATE_FORMAT.parse(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(bookDate)));
			if(date1.equalsIgnoreCase(date2)) {
				response.put("isPartPayment", false);
			}
			else {
				finalSchedule.put("paymentScheduleType","Initial Deposit");
				finalSchedule.put("dueAmount", deposits.divide(new BigDecimal(scheduleArray.length())));
				finalSchedule.put("dueDate", dueDate);
				if(depositRefundables.contains(false))
					finalSchedule.put("isRefundable", false);
				else
					finalSchedule.put("isRefundable", true);
				finalArray.put(finalSchedule);

				finalSchedule = new JSONObject();
				finalSchedule.put("paymentScheduleType","Balance Payment");
				finalSchedule.put("dueAmount", balance.divide(new BigDecimal(scheduleArray.length())));
				finalSchedule.put("dueDate", dates.get(0));
				if(balanceRefundables.contains(false))
					finalSchedule.put("isRefundable", false);
				else
					finalSchedule.put("isRefundable", true);
				finalArray.put(finalSchedule);

//				System.out.println("finalArray : "+finalArray);
				response.put("isPartPayment", true);
				response.put("partPayment", finalArray);
			}
		}
		else{
			response.put("isPartPayment", false);
		}
		
		/*
		//*******************if not to add balances********************
		if(scheduleArray.length() > 0 &&  scheduleArray.length() == pricedIteneraryArray.length()) {
			BigDecimal deposits = new BigDecimal(0);
			BigDecimal balance = new BigDecimal(0);
			JSONObject finalSchedule = new JSONObject();
			System.out.println("scheduleArray.size : "+scheduleArray.length());
			for(Object schedules : scheduleArray) {
				JSONObject schedule = (JSONObject) schedules;
				JSONArray paymentScheduleArray = schedule.optJSONArray("paymentSchedule");
				for(Object paymentSchedules : paymentScheduleArray) {
					JSONObject paymentSchedule = (JSONObject) paymentSchedules;
					if(paymentSchedule.optString("paymentScheduleType").contains("deposit")) {
						deposits = deposits.add(new BigDecimal(paymentSchedule.optDouble("dueAmount")));
						depositRefundables.add(paymentSchedule.optBoolean("isRefundable"));
					}
					else {
						if(paymentSchedule.optString("dueDate").equalsIgnoreCase(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(bookDate))) {
							deposits = deposits.add(new BigDecimal(paymentSchedule.optDouble("dueAmount")));
							dueDate = paymentSchedule.optString("dueDate");
							depositRefundables.add(paymentSchedule.optBoolean("isRefundable"));
						}
						else{
							finalSchedule.put("paymentScheduleType","Balance Payment");
							finalSchedule.put("dueAmount", paymentSchedule.optDouble("dueAmount")/(scheduleArray.length()));
							finalSchedule.put("dueDate", paymentSchedule.optString("dueDate"));
							finalSchedule.put("isRefundable", paymentSchedule.optBoolean("isRefundable"));
							finalArray.put(finalSchedule);
							finalSchedule = new JSONObject();
						}
					}
				}
			}
			finalSchedule.put("paymentScheduleType","Initial Deposit");
			finalSchedule.put("dueAmount", deposits.divide(new BigDecimal(scheduleArray.length())));
			finalSchedule.put("dueDate", dueDate);
			if(depositRefundables.contains(false))
				finalSchedule.put("isRefundable", false);
			else
				finalSchedule.put("isRefundable", true);
			finalArray.put(finalSchedule);
			ArrayList <String> values = new ArrayList<>();
			for(Object elements : finalArray) {
				JSONObject element = (JSONObject) elements;
				values.add(element.optString("paymentScheduleType"));
			}

			if(values.contains("Balance Payment")) {
				response.put("isPartPayment", true);
				response.put("partPayment", finalArray);
			}
			else {
				response.put("isPartPayment", false);
			}
		}
		else{
			response.put("isPartPayment", false);
		}
		//**********************************************
		 */
		return response.toString();
	}

	public static JSONObject generatePaymentSchedule(Double totalFareReprice, Document partPayment,String supplierRef,Date travelDate,Date bookDate) throws Exception {
		
		String priceComponent="";
		Document periodicity = null;
		BigDecimal percentage=new BigDecimal(0);
		BigDecimal RemainingPercentage=new BigDecimal(0);
		BigDecimal amount=new BigDecimal(0);
		BigDecimal RemainingAmount=new BigDecimal(0);
		BigDecimal dueAmount=new BigDecimal(0);
		SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		JSONArray myArray = new JSONArray();
		JSONObject finalObject = new JSONObject();
		BigDecimal totalFare = new BigDecimal(totalFareReprice);
		BigDecimal duplicatedTotalFare = new BigDecimal(totalFareReprice);
		
//		System.out.println("totalFromReprice : "+totalFareReprice);
		periodicity = CommonUtil.fetchSupplierSettlement(supplierRef);
		List<Document> listOfSchedules = (List<Document>)partPayment.get(MDM_PART_PAYMENTSCHEDULE);
		for( Document schedule : listOfSchedules) {
			String paymentSchdType = schedule.getString(MDM_PART_PAYMENTSCHDTYPE);
			String paymentDueDate = schedule.getString(MDM_PART_PAYMENTDUEDATE);
			Document paymentDue = (Document) schedule.get(MDM_PART_PAYMENTDUE);
			String durationType = paymentDue.getString(MDM_PART_DURATIONTYPE);
			boolean isRefundable = schedule.getBoolean(MDM_PART_REFUNDABLE);
			int count = (int) paymentDue.get(MDM_PART_COUNT);
//			System.out.println("schedule : "+schedule);
//			System.out.println(paymentSchdType+" : "+paymentDueDate+" : "+durationType+" : "+count);
			Document value = (Document)schedule.get(MDM_PART_VALUE);
			String mode = value.getString(MDM_PART_VALUE_MODE);
			if(value.containsKey(MDM_PART_VALUE_MODE))
			{
				if(mode.equalsIgnoreCase(MDM_PART_PERCENTAGE))
				{
					percentage = new BigDecimal(value.getInteger(MDM_PART_PERCENTAGE));
				}
				else {
					if(value.containsKey(MDM_PART_CURRENCYAMT))
					{
						amount = new BigDecimal(value.getInteger(MDM_PART_CURRENCYAMT));
						duplicatedTotalFare = (duplicatedTotalFare).subtract(amount);
//						System.out.println("duplicatedTotalFare :"+duplicatedTotalFare);
					}
				}
			}
			priceComponent = value.getString(MDM_PART_PRICECOMPONENT);
//			System.out.println(mode+" : "+priceComponent+" : "+percentage+" : "+amount);
//			dueAmount = (double) Math.round(calculateValue(mode,percentage,amount,totalFare));
			dueAmount = CommonUtil.calculateValue(mode,percentage,amount,totalFare);
//			System.out.println("dueAmount : "+dueAmount);
			JSONObject jObject = new JSONObject();
			Date final_date = null;
			//Calendar paymentDate = Calendar.getInstance();
			if(!paymentSchdType.contains(MDM_PART_DEPOSIT)) {
				if(!paymentDueDate.equalsIgnoreCase(MDM_PART_PAYMENTDUEDATE_SUPPLIERSETTLEMENT)) {
					Calendar paymentDate = CommonUtil.calculatePaymentDate(paymentDueDate, bookDate, travelDate, durationType, count);
					//System.out.println("DATE_FORMAT.parse(travelDate) : "+DATE_FORMAT.parse(travelDate));
//					System.out.println("Before settlement terms : "+paymentDate.getTime());
					final_date = CommonUtil.checkSettlementTerms(periodicity,paymentDate.getTime(),travelDate);
//					System.out.println("After settlement terms : "+final_date);
				}
				else {
//					System.out.println("Before settlement terms : "+travelDate);
					final_date = CommonUtil.checkSettlementTerms(periodicity,travelDate,travelDate);
//					System.out.println("After settlement terms : "+final_date);
				}
				
				if(value.containsKey(MDM_PART_VALUE_MODE))
				{
					if(mode.equalsIgnoreCase(MDM_PART_AMOUNT))
					{
						dueAmount = duplicatedTotalFare;
//						System.out.println("---------------"+dueAmount);
					}
				}
				//balanceamt = balanceamt.subtract(dueAmount); 
				//jObject.put("bookID", bookId);
				jObject.put("paymentScheduleType", paymentSchdType);
				jObject.put("dueDate", DATE_FORMAT.format(final_date));
				jObject.put("dueAmount", dueAmount);
				jObject.put("isRefundable", isRefundable);
//				jObject.put("balanceAmt", (double) Math.round(balanceamt));
				//jObject.put("balanceAmt", (balanceamt));
				myArray.put(jObject);
			}
			else {
				//balanceamt = balanceamt.subtract(dueAmount);
				//jObject.put("bookID", bookId);
				jObject.put("paymentScheduleType", paymentSchdType);
				jObject.put("dueDate", DATE_FORMAT.format(bookDate));
				jObject.put("dueAmount", dueAmount);
				jObject.put("isRefundable", isRefundable);
//				jObject.put("balanceAmt", (double) Math.round(balanceamt));
				//jObject.put("balanceAmt", (balanceamt));
				myArray.put(jObject);
			}
		}
		finalObject.put("paymentSchedule", myArray);
//		System.out.println("schedule : "+finalObject);
		return finalObject;
	}
	

}
