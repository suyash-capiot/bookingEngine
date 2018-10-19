package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;


import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.CommonUtil;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.TimeLimitConstants;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirlineData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirportData;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
public class TimeLimitProcessor implements TimeLimitConstants {


	public static JSONObject generateTimeLimit(Date travelDateReq,Document timeLimit,String supplierRef) throws ParseException {
		String entityid = "", entityType = "", entityMarket = "",bookID="";
		Map<String,Date> datesMap = new HashMap<String,Date>();
		Date bookDateReq = new Date();
		Date date=null;
		boolean enablePaymentTlb =  false;
		Calendar c = Calendar.getInstance();
		Document travelDateOptions,bookingDateOptions,travelDate,bookingDate,tlSetFor,reconfirmationSetFor=null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		Document periodicity = null;
		JSONObject jObject = new JSONObject();
		enablePaymentTlb = timeLimit.getBoolean(MDM_TL_ENABLEPAYMENTTLB);
		if(timeLimit.containsKey(MDM_TL_TLMEXPIRY)){
			Document tlmExpiry = (Document) timeLimit.get(MDM_TL_TLMEXPIRY);
			if(tlmExpiry.getBoolean(MDM_TL_SHOULDEXPIRE)==true && tlmExpiry.getString(MDM_TL_EXPIRYTYPE).equalsIgnoreCase(MDM_TL_EXPIRYTYPE_BOOKDATE)) {
				bookingDateOptions = (Document) timeLimit.get(MDM_TL_BOOKINGDATEOPTIONS);
				//System.out.println("bookingDateOptions : "+bookingDateOptions);
				bookingDate = (Document) bookingDateOptions.get(MDM_TL_BOOKINGDATE);
				tlSetFor = (Document) bookingDateOptions.get(MDM_TL_BD_TLSETFOR);
				reconfirmationSetFor = (Document) bookingDateOptions.get(MDM_TL_BD_RECONFIRMATIONSETFOR);
				//System.out.println("bookingDate :"+bookingDate+" tlSetFor :"+tlSetFor+" reconfirmationSetFor:"+reconfirmationSetFor);
				datesMap = CommonUtil.checkandReturnBookingDates(bookingDate,tlSetFor,reconfirmationSetFor,bookDateReq,travelDateReq);
			}
			else if(tlmExpiry.getBoolean(MDM_TL_SHOULDEXPIRE)==true && tlmExpiry.getString(MDM_TL_EXPIRYTYPE).equalsIgnoreCase(MDM_TL_EXPIRYTYPE_TRAVELDATE)) {
				travelDateOptions = (Document) timeLimit.get(MDM_TL_TRAVELDATEOPTIONS);
				//System.out.println("travelDateOptions : "+travelDateOptions);
				travelDate = (Document) travelDateOptions.get(MDM_TL_TRAVELDATE);
				tlSetFor = (Document) travelDateOptions.get(MDM_TL_TD_TLSETFOR);
				reconfirmationSetFor = (Document) travelDateOptions.get(MDM_TL_TD_RECONFIRMATIONSETFOR);
				//System.out.println("travelDate :"+travelDate+" tlSetFor :"+tlSetFor+" reconfirmationSetFor:"+reconfirmationSetFor);
				datesMap = CommonUtil.checkandReturnTravelDates(travelDate,tlSetFor,reconfirmationSetFor,bookDateReq,travelDateReq);
			}
			if(datesMap != null)
			{
				date = datesMap.get(MAP_TIMELIMITSETFOR);
				periodicity = CommonUtil.fetchSupplierSettlement(supplierRef);
				//System.out.println("Before settlement terms : "+date);
				date = CommonUtil.checkSettlementTerms(periodicity,date,travelDateReq);
				//System.out.println("After settlement terms : "+date);
				//System.out.println("tlDate :"+date);
				c.setTime(date);
				Date dateBeforeBuffer = c.getTime();
				if(timeLimit.containsKey(MDM_TL_BUFFER)) {
					Document buffer = (Document) timeLimit.get(MDM_TL_BUFFER);
					if(buffer.containsKey(MDM_TL_BUFFERDAYS)) {
						int bufferDays = (int) buffer.get(MDM_TL_BUFFERDAYS);
						c.add(Calendar.DAY_OF_MONTH,-bufferDays);	
					}
					if(buffer.containsKey(MDM_TL_BUFFERHRS)) {
						int bufferHrs = (int) buffer.get(MDM_TL_BUFFERHRS);
						c.add(Calendar.HOUR_OF_DAY,-bufferHrs);
					}
					date = c.getTime();
					//System.out.println("date after buffer :"+date);
					
				}
				if(bookDateReq.before(date) || bookDateReq.equals(date)) {
					datesMap.put(MAP_TIMELIMITSETFOR, date);
				}
				else {
					datesMap.put(MAP_TIMELIMITSETFOR, dateBeforeBuffer);
				}
				
				jObject.put("timeLimitDate",sdf.format(datesMap.get(MAP_TIMELIMITSETFOR)));
				
				if(datesMap.get(MAP_RECONFIRMATIONSETFOR) != null) {
					Date reconform = datesMap.get(MAP_RECONFIRMATIONSETFOR);
					Date tl = datesMap.get(MAP_TIMELIMITSETFOR);
					if(tl.before(reconform) || tl.equals(reconform)) {
						c.setTime(tl);
						c.add(Calendar.DATE, -1);
						reconform = c.getTime();
						if(reconform.before(bookDateReq))
							reconform = bookDateReq;
					}
					datesMap.put(MAP_RECONFIRMATIONSETFOR, reconform);
					jObject.put("reconfirmationDate",sdf.format(datesMap.get(MAP_RECONFIRMATIONSETFOR)));
				}
				else
					jObject.put("reconfirmationDate", "");
				
				jObject.put(JSON_PROP_TIMELIMAPP,true);
				
				
				//System.out.println("Final datesMap = "+datesMap);
				
			}
		}
	
		return jObject;
	}

	@SuppressWarnings("unchecked")
	public static String validateTimeLimit(JSONObject jsonRequest, JSONObject jsonResponse, UserContext usrCtx) throws Exception{

		String entityid="",airlineType="",entityType="",entityMarket="",supplierRef="",airlineName="";
		String MDMcategory="",MDMcategorySubType="",MDMmode="",MDMsupplierName="",MDMairlineType="",MDMcity="",MDMcountry="";
		//HashMap<Integer,Date> tldateMap = new HashMap();
		HashMap<Integer,String> tldateMap = new HashMap();
		Map<Integer, JSONObject> tlMap = new HashMap<>();
		JSONObject returnedObject = null;
		JSONArray myArray = new JSONArray();
		JSONObject finalObject = new JSONObject();
		
		JSONArray flightSegmentArray = new JSONArray();
		int c1=0,c2=0,i=1,j=1;
		boolean timeLimitFlag = false;
		// processing response header
		JSONObject responseHeader = (JSONObject) jsonResponse.opt(JSON_PROP_RESPONSEHEADER);
		JSONObject clientContext = (JSONObject) responseHeader.opt(JSON_PROP_CLIENTCONTEXT);
		
		
		//entityid = clientContext.optString(JSON_PROP_CLIENTID); // clientId - entityId
		//entityType = clientContext.optString(JSON_PROP_CLIENTTYPE); // clientType - entityType
		ClientInfo[] clientInfoArr=usrCtx.getClientCommercialsHierarchyArray();
		entityType =  usrCtx.getClientCommercialsHierarchyArray()[0].getCommercialsEntityType().toString(); 
		
		if(clientContext.optString(JSON_PROP_CLIENTTYPE).equalsIgnoreCase(Constants.CLIENT_TYPE_B2C)) {
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
		entityMarket = clientContext.optString(JSON_PROP_CLIENTMARKET); // clientMarket - entityMarket - companyMarket
		//System.out.println("\nentityid : " + entityid + " entityType : " + entityType + " entityMarket : " + entityMarket);

		//processing response body
		JSONObject response = (JSONObject) jsonResponse.opt(JSON_PROP_RESPONSEBODY);
		JSONArray pricedIteneraryArray = (JSONArray) response.opt(JSON_PROP_AIR_PRICEDITINERARY);
		ArrayList <Date> deptDates= new ArrayList<Date>();
		
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
		
		for (Object iteneraries : pricedIteneraryArray) {
			JSONObject pricedItenerary = (JSONObject) iteneraries;
			JSONObject airItenerary = (JSONObject) pricedItenerary.opt(JSON_PROP_AIR_AIRITINERARY);
			supplierRef = pricedItenerary.optString(JSON_PROP_AIR_SUPPLIERREF);
			JSONArray originDestinationOptionsArray = (JSONArray) airItenerary.opt(JSON_PROP_AIR_ORIGINDESTINATIONOPTIONS);
			for (Object options : originDestinationOptionsArray) {
				JSONObject originDestinationOption = (JSONObject) options;
				flightSegmentArray = (JSONArray) originDestinationOption.opt(JSON_PROP_AIR_FLIGHTSEGMENT);
				
				for (Object flightSegments : flightSegmentArray) {
					c1=0;c2=0;
					JSONObject flightSegment = (JSONObject) flightSegments;
					String originLocation = flightSegment.optString(JSON_PROP_AIR_ORIGINLOCATION);
					String destinationLocation = flightSegment.optString(JSON_PROP_AIR_DESTINATIONLOCATION);
					JSONObject operatingAirline = (JSONObject) flightSegment.opt(JSON_PROP_AIR_OPERATINGAIRLINE);
					airlineType = RedisAirlineData.getAirlineDetails(operatingAirline.optString(JSON_PROP_AIR_AIRLINECODE), "value");
					airlineName = RedisAirlineData.getAirlineDetails(operatingAirline.optString(JSON_PROP_AIR_AIRLINECODE), "name"); // airlineName
					String destinationCountry = RedisAirportData.getAirportInfo(destinationLocation, "country");
					String destinationCity = RedisAirportData.getAirportInfo(destinationLocation, "cityName");
					//System.out.println("airlineName : " + airlineName);
					//System.out.println(destinationCity +"--"+ destinationCountry);

					//FindIterable<Document> MDMTimeLimit = MDMUtils.getTimeLimitMasterDetails(entityid, entityType, entityMarket);
					List<Document> MDMTimeLimit = MDMUtils.getTimeLimitMasterDetailsV2(entityid, entityType, entityMarket);
					//MongoCursor<Document> MDMTimeLimitIter = MDMTimeLimit.iterator();
					//while (MDMTimeLimitIter.hasNext()) {
					for(Document timeLimit:MDMTimeLimit) {
						//Document timeLimit = MDMTimeLimitIter.next();
						List<Document> listOfProducts = (List<Document>) timeLimit.get(MDM_TL_PRODUCTS);
						int include[]=new int[listOfProducts.size()];
						int exclude[]=new int[listOfProducts.size()];
						List<String> mode = new ArrayList<String>();
						for(Document product : listOfProducts) {
							MDMsupplierName = product.getString(MDM_TL_SUPPLIERID);
							MDMcategory = product.getString(MDM_TL_PRODUCTCAT);
							MDMcategorySubType = product.getString(MDM_TL_PRODUCTCATSUBTYPE);
							mode.add(product.getString(MDM_TL_MODE));
							List<String> MDMproductFlav = new ArrayList<String>();
							MDMairlineType = product.getString(MDM_TL_AIRTYPE);
							//System.out.println("MDMairlineType : "+MDMairlineType);
							List<Document> productFlav = (List<Document>)product.get(MDM_TL_PRODUCTFLAV);
							for(Document flavour : productFlav) {
								MDMproductFlav.add(flavour.getString(MDM_TL_PRODUCTNAME));
							}
							if(product.containsKey(MDM_TL_DESTINATION))
								MDMcity = product.getString(MDM_TL_DESTINATION);
							if(product.containsKey(MDM_TL_COUNTRY))
								MDMcountry = product.getString(MDM_TL_COUNTRY);
							//System.out.println(timeLimit);
							//************
							//************
							if(!mode.isEmpty() && mode.contains(MDM_TL_MODE_INCLUDE)) 
							{
								if(CommonUtil.checkStringParams(TRANSPORTATION, MDMcategory, "productCategory") && CommonUtil.checkStringParams(FLIGHT, MDMcategorySubType, "productCategorySubType") && CommonUtil.checkStringParams(supplierRef, MDMsupplierName, "supplierID") && CommonUtil.checkStringParams(airlineType, MDMairlineType, "airlineType") && MDMproductFlav.contains(airlineName) )
								{
									include[c1]=1;
								}
								c1++;
							}
							if(!mode.isEmpty() && mode.contains(MDM_TL_MODE_EXCLUDE)) 
							{
								if(CommonUtil.checkStringParams(TRANSPORTATION, MDMcategory, "productCategory") && CommonUtil.checkStringParams(FLIGHT, MDMcategorySubType, "productCategorySubType") && CommonUtil.checkStringParams(supplierRef, MDMsupplierName, "supplierID") && MDMproductFlav.contains(airlineName) && CommonUtil.checkStringParams(airlineType, MDMairlineType, "airlineType") && CommonUtil.checkStringParams(destinationCity, MDMcity, "destination") && CommonUtil.checkStringParams(destinationCountry, MDMcountry, "country"))
								{
									exclude[c1]=1;
								}
								c1++;
							}
							c1=0;
						}

						for(int k1=0;k1<listOfProducts.size();k1++)
						{
							if(include[k1]==1)
								c1++;
							if(exclude[k1]==1)
								c2++;
						}
						if( mode.contains(MDM_TL_MODE_INCLUDE) && mode.contains(MDM_TL_MODE_EXCLUDE) )
						{
							if(c1>0 && c2==0)
							{timeLimitFlag=true;
							//System.out.println("Time Limit Flag (in/ex)= "+ timeLimitFlag);
							returnedObject = generateTimeLimit(travellingDate,timeLimit,supplierRef);
							if(returnedObject.length() > 0)
							myArray.put(returnedObject);
							}
							else
								timeLimitFlag=false;
						}
						else if(mode.contains(MDM_TL_MODE_INCLUDE))
						{
							if(c1>0)
							{timeLimitFlag=true;
							//System.out.println("Time Limit Flag (in)= "+ timeLimitFlag);
							returnedObject = generateTimeLimit(travellingDate,timeLimit,supplierRef);
							if(returnedObject.length() > 0)
							myArray.put(returnedObject);
							}
							else
								timeLimitFlag=false;

						}
						else if( mode.contains(MDM_TL_MODE_EXCLUDE))
						{
							if(c2==0)
							{timeLimitFlag=true;
							//System.out.println("Time Limit Flag (ex)= "+ timeLimitFlag);
							returnedObject = generateTimeLimit(travellingDate,timeLimit,supplierRef);
							if(returnedObject.length() > 0)
							myArray.put(returnedObject);
							}
							else
								timeLimitFlag=false;
						}
						else 
						{
							timeLimitFlag=false;
						}
					}
				}
			}
		}
		//System.out.println(myArray.length());
		//System.out.println(flightSegmentArray.length());
		JSONObject tlObj = new JSONObject();
		if(myArray.length() > 0 && myArray.length() >= flightSegmentArray.length()) {
			for(Object obj : myArray) {
				JSONObject jobj = (JSONObject) obj;
				if(jobj.has(MAP_TIMELIMITDATE)) {
//					tldateMap.put(j++,(Date) jobj.opt(MAP_TIMELIMITDATE));
					tldateMap.put(j++, (String) jobj.opt(MAP_TIMELIMITDATE));
					tlMap.put(i++, jobj);
				}
			}
		    Collection myCol = tldateMap.values();
			//System.out.println("\nhm1 values: " + myCol);  	
			TreeSet ts2 = new TreeSet(myCol);
			//System.out.println("hm1 sorted values: " + ts2);
			
			tlObj = tlMap.get(CommonUtil.getKeyFromValue(tldateMap, ts2.first()));
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			
			Calendar c = Calendar.getInstance();
			String str = sdf.format(c.getTime());
			String str1 = sdf.format(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(tlObj.getString("timeLimitDate")));
			
			//System.out.println("str = "+str+" str1 = "+str1);
			
			if(str.equalsIgnoreCase(str1))
			{
				JSONObject newtlObj = new JSONObject();
				newtlObj.put(JSON_PROP_TIMELIMAPP,false);
				response.put("timeLimit", newtlObj);
				//System.out.println("Do nothing!!!!");
			}
			else {
				response.put("timeLimit", tlObj);
				}
			//System.out.println("tlObj = "+tlObj);
		}
		else {
			tlObj.put(JSON_PROP_TIMELIMAPP, false);
			response.put("timeLimit", tlObj);
			//System.out.println("Do nothing!!!!");
		}
		//System.out.println("myarray : "+myArray.toString());
		
		return response.toString();
	}
	
	


}
