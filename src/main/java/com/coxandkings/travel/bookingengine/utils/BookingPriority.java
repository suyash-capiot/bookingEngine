package com.coxandkings.travel.bookingengine.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.config.DBServiceConfig;
import com.coxandkings.travel.bookingengine.config.MDMConfigV2;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirlineData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisHotelData;

//@CrossOrigin(origins = "*")
//@RestController
//@RequestMapping("/TestService")
public class BookingPriority implements BookingPriorityConstants {

	private static final Logger logger = LogManager.getLogger(BookingPriority.class);
	
//	@PostMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
//	public ResponseEntity<String> pingService(JSONObject reqJson) throws Exception
//	{
////		JSONTokener jsonTokener = new JSONTokener(req);
////		JSONObject jsonObject = new JSONObject(jsonTokener.toString());
//		
//		JSONObject kafkaRQJson = new JSONObject();
//		
//		BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("D:\\Opeations\\Products Xml\\Air\\WEMBookRQ.json")));
////		BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("D:\\Opeations\\Products Xml\\Air\\AirKafkaRQJSON.json")));
////		BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("D:\\Opeations\\Products Xml\\Hotel\\KafkaHotelRQ.json")));
//		
////		BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("D:\\Opeations\\Products Xml\\Air\\AirGetBookingRS.json")));
////		BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("D:\\Opeations\\Products Xml\\Hotel\\AccoWEMBookRQ.json")));
//		
////		BufferedReader bufferedReader = new BufferedReader(new FileReader(new File("D:\\Opeations\\Products Xml\\Hotel\\gettBookingRS(11-6).json"))); 
//		
//		String txt="";
//		String st;
//		  while ((st = bufferedReader.readLine()) != null){
//			  txt +=st;
//	    }
//		bufferedReader.close();
//		
//		kafkaRQJson =  new JSONObject(txt);
////		String priority = getPriorityRQ("ACCO",kafkaRQJson,"On request");
////		String priority = getPriorityRS(kafkaRQJson,"Confirmed");
//		
////		BufferedReader reader = new BufferedReader(new FileReader(new File("D:\\Opeations\\Products Xml\\Hotel\\KafkaHotelRS.json")));
//		BufferedReader reader = new BufferedReader(new FileReader(new File("D:\\Opeations\\Products Xml\\Air\\AirKafkaRSJSON.json")));
//		
//		String txt1="";
//		String st1;
//		  while ((st1 = reader.readLine()) != null){
//			  txt1 +=st1;
//	    }
//		  reader.close();
//		
//		JSONObject kafkaMsg = new JSONObject();
//		kafkaMsg = new JSONObject(txt1);
//		  
//		JSONObject reJsson = new JSONObject(kafkaRQJson.toString());
//		
//		
////		setPriorityRQ("AIR",kafkaRQJson);
////		System.out.println(kafkaRQJson);
////		
////		setPriorityRS("AIR", reJsson, kafkaMsg);
////		System.out.println(kafkaMsg);
//		
//		return new ResponseEntity<String>(getPriorityForOps("12345677","8a9d932863b51a840163ce392505004c","Confirmed"),HttpStatus.OK);
//		
//	}
	
//	private static void setPriorityRQ(String product, JSONObject kafkaMsg)
//	{
//		BookingPriority bookingPriority = new BookingPriority();
//		String priorityStr = bookingPriority.getPriorityRQ(product, kafkaMsg, "On request");
//		
//		JSONArray priorityArr = new JSONArray(priorityStr.toString());
//		JSONArray pricedItinArr = kafkaMsg.getJSONObject("requestBody").getJSONArray("pricedItinerary");
//		
//		for(int i=0;i<pricedItinArr.length();i++)
//		{
//			JSONObject pricedItin = pricedItinArr.getJSONObject(i);
//			pricedItin.put("priority", priorityArr.getJSONObject(i));
//		}
//	}
	
//	private static void setPriorityRS(String product,JSONObject reqJson,JSONObject kafkaMsg)
//	{
//		BookingPriority bookingPriority = new BookingPriority();
//		String priorityStr = bookingPriority.getPriorityRQ(product, reqJson, "Confirmed");
//		
//		JSONArray priorityArr = new JSONArray(priorityStr.toString());
//		JSONArray suppBookArr = kafkaMsg.getJSONObject("responseBody").getJSONArray("supplierBookReferences");
//		
//		for(int i=0;i<suppBookArr.length();i++)
//		{
//			JSONObject suppBook = suppBookArr.getJSONObject(i);
//			suppBook.put("priority", priorityArr.getJSONObject(i));
//		}
//	}
	
	
	public static String getBookingFromDbServices(String bookID) throws MalformedURLException, Exception 
	{
		String url = String.format(DBServiceConfig.getDBServiceURL(),bookID);
		URI uri = new URI(url);
		
		JSONObject getBookingJson =  HTTPServiceConsumer.consumeJSONService("", uri.toURL(), DBServiceConfig.getHttpHeaders(), "GET", null);
		
		return getBookingJson.toString();
	}
	
	
	public static String getPriorityForBE(String product, JSONObject kafkaRQJson, String bookingStatus)
	{
		JSONObject requestHeader = kafkaRQJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject requestBody = kafkaRQJson.getJSONObject(JSON_PROP_REQBODY);
		
		UserContext userCtx = UserContext.getUserContextForSession(requestHeader);
		JSONArray entityDtlsArr = userCtx.getClientCommercialsHierarchy();
		
		String market = "";
		
		JSONObject filters = new JSONObject();
		
//--X----------X-------------X-------------Client Details Filter Formation-------X----------------X-----------------X-------------------X		
		
		String clientType =	requestHeader.getJSONObject("clientContext").getString("clientType");
		
		JSONArray andArr = new JSONArray();
		for(int i=0;i<entityDtlsArr.length();i++)
		{
			JSONObject entityDtlsJson =	entityDtlsArr.getJSONObject(i);
			JSONObject andJson = new JSONObject();
			
			market = entityDtlsJson.getString(JSON_PROP_ENTITYMARKET);
			
			andJson.put(BPRIORITY_ENTITYTYPE, clientType);//if the array length is more than one and client type filter gets created twice it doesnt make a difference.
														  //I am doing this because i want the enitity name and entity type to be in the same object if it is B2B
			
			if(clientType.equalsIgnoreCase("B2B"))
				andJson.put(BPRIORITY_ENTITYNAME, entityDtlsJson.getString(JSON_PROP_ENTITYNAME));
			
			andArr.put(andJson);
		}
		
//		makeElemMatchObj(andArr);
		
		filters.put(BPRIORITY_CLIENTDETAILS.concat(".").concat(BPRIORITY_ENTITIES), makeElemMatchObj(andArr));
		filters.put(BPRIORITY_CLIENTDETAILS.concat(".").concat(BPRIORITY_COMPANYMARKET), market);
		
//--------X-------------X----------------X-------------X-Booking Criteria Filter Formation-------------X----------------X----------------X---------------X----------------X-------------X-------------X
		
		JSONObject bookCritJson = new JSONObject();
		
//		bookCritJson.put(BPRIORITY_BOOKINGATTR, "Reconfirmation Rejected");//where would i get this?
		bookCritJson.put(BPRIORITY_BOOKINGSTATUS, bookingStatus);//It will always be "On request" since we are getting priority for kafkaRQ JSON
		
		JSONArray bookCritJsonArr = new JSONArray();
		bookCritJsonArr.put(bookCritJson);
		
		filters.put(BPRIORITY_BOOKINGCRITERIA, makeElemMatchObj(bookCritJsonArr));
		
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
		//TODO: Cut Off days? not querying with cut off days for now! We 'may' have to do query with it later 
		
		String bookingJson = "";
		
		if(product.equalsIgnoreCase(PRODUCT_AIR))
			bookingJson = getAirFilterRQ(requestBody, filters);
		else if(product.equalsIgnoreCase(PRODUCT_ACCO))
			bookingJson = getAccoFilterRQ(requestBody, filters);
		
//		System.out.println(bookingJson);
		
		return bookingJson.toString();
	}

	private static String getAccoFilterRQ(JSONObject requestBody, JSONObject filters) {
		String bookingJson;
		JSONObject accomInfoJson = requestBody.getJSONArray(JSON_PROP_ACCOMODATIONARR).getJSONObject(0);
		
		filters.put(BPRIORITY_PRODUCTDETAILS.concat(".").concat(BPRIORITY_PRODUCTCATEGORY), PROD_CATEG_ACCO);
		filters.put(BPRIORITY_PRODUCTDETAILS.concat(".").concat(BPRIORITY_PRODUCTSUBCATEGORY), accomInfoJson.getString(JSON_PROP_ACCOSUBTYPE));
		
		JSONArray accomInfoArr = requestBody.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		
		JSONArray finalPriorityArr = new JSONArray();
		
		String hotelCode = "";
		for(int i=0;i<accomInfoArr.length();i++)
		{
			JSONObject finalFilter = new JSONObject(filters.toString());
			
			JSONObject accomInfoJsonObj = accomInfoArr.getJSONObject(i);
			
			String suppRef = accomInfoJsonObj.getString("supplierRef");
			
			//Added to accommodate suppliers
			finalFilter.put("suppliers", suppRef);
			
			JSONArray roomConfigJsonArr = accomInfoJsonObj.getJSONArray(JSON_PROP_REQUESTEDROOMARR);
			
			for(int j=0;j<roomConfigJsonArr.length();j++)
			{
				JSONObject roomConfigJson = roomConfigJsonArr.getJSONObject(j);
				hotelCode = roomConfigJson.getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_HOTELINFO).getString(JSON_PROP_HOTELCODE);
			}
			
			JSONArray accoAndArr = new JSONArray();
			Map<String,Object> hotelInfoMap = new HashMap<String,Object>();
			
			hotelInfoMap = RedisHotelData.getHotelInfo(hotelCode);
			
			String name = (String) hotelInfoMap.getOrDefault(JSON_PROP_NAME, "");
//			accoAndArr.put(makeJsonObj(JSON_PROP_PROD, name));
			
			String chain = (String) hotelInfoMap.getOrDefault(JSON_PROP_CHAIN, "");
			accoAndArr.put(makeJsonObj(JSON_PROP_CHAIN,chain));
			
			String brand = (String) hotelInfoMap.getOrDefault(JSON_PROP_BRAND, "");
			accoAndArr.put(makeJsonObj(JSON_PROP_BRAND, brand));
			
			String city = (String) hotelInfoMap.getOrDefault(JSON_PROP_CITY, "");
			
			Map<String,Object> cityAttrs = new HashMap<String,Object>();
			cityAttrs =	RedisCityData.getCityInfo(city);
			
			String country = (String) cityAttrs.getOrDefault(JSON_PROP_COUNTRY,"");
			accoAndArr.put(makeJsonObj(JSON_PROP_COUNTRY, country));
			
			finalFilter.put(BPRIORITY_PRODUCTDETAILS.concat(".").concat(BPRIORITY_PRODUCTS).concat(".").concat(BPRIORITY_ACCOMMODATION), makeElemMatchObj(accoAndArr));
			
			String data = MDMUtils.getData(String.format(MDMConfigV2.getURL(BPRIORITY_NAME), finalFilter), true);
			
			if(data.length()<3)// If the string is empty it returns "{}" so i'm checking the length rather than converting it to JSON
			{
				logger.info("No Booking Priority Document Found");//not sure what to do if this case comes up
				finalPriorityArr.put(new JSONObject());
				continue;
			}
			
			JSONObject latestDoc = MDMUtils.getLatestUpdatedJson(new JSONObject(data));
			
			if(!latestDoc.has(BPRIORITY_PRIORITYSETTINGS))
			{
				logger.info("No Priority Settings Found in the MDM Document");//Not sure what to do if this case comes up
				finalPriorityArr.put(new JSONObject());
				continue;
			}
			
			JSONObject priorityObj = latestDoc.getJSONObject(BPRIORITY_PRIORITYSETTINGS);
			finalPriorityArr.put(priorityObj);
		}
		bookingJson = finalPriorityArr.toString();
		
//		System.out.println(bookingJson);
		
		return bookingJson;
	}

	private static String getAirFilterRQ(JSONObject requestBody, JSONObject filters) {
		String bookingJson;
		filters.put(BPRIORITY_PRODUCTDETAILS.concat(".").concat(BPRIORITY_PRODUCTCATEGORY), PROD_CATEG_TRANSPORT);
		filters.put(BPRIORITY_PRODUCTDETAILS.concat(".").concat(BPRIORITY_PRODUCTSUBCATEGORY), PROD_CATEG_SUBTYPE_FLIGHT);//How do i get the airline Name
		
		JSONArray pricedItinArr = requestBody.getJSONArray(JSON_PROP_PRICEDITIN);
		
		JSONArray finalPriorityArr = new JSONArray();
		
		for(int i=0;i<pricedItinArr.length();i++)
		{
			JSONArray priorityArr = new JSONArray();
			JSONObject pricedItin = pricedItinArr.getJSONObject(i);
			
			String suppRef = pricedItin.getString("supplierRef");
			
			JSONArray ODOArr = pricedItin.getJSONObject(JSON_PROP_AIRITINERARY).getJSONArray(JSON_PROP_ORIGDESTOPTS);
			
			for(int j=0;j<ODOArr.length();j++)
			{
				JSONObject ODOJson = ODOArr.getJSONObject(j);
				JSONArray flightSegArr = ODOJson.getJSONArray(JSON_PROP_FLIGHTSEG);
				
				for(int k=0;k<flightSegArr.length();k++)
				{
					JSONObject finalFilter = new JSONObject(filters.toString());
					JSONObject flightSegJson = flightSegArr.getJSONObject(k);
					
					JSONArray airlineArr = new JSONArray();
			    	
			    	if(flightSegJson.has(JSON_PROP_OPERAIRLINE)){
			    		String airlineCode = flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).getString(JSON_PROP_AIRLINECODE);
			    		
			    		Map<String,Object> origAirportInfo = RedisAirlineData.getAirlineDetails(airlineCode);
			        	String airlineName = (String) origAirportInfo.getOrDefault(AIRLINE_NAME, "");
			        	
			        	airlineArr.put(makeJsonObj(JSON_PROP_AIRLINENAME.concat(".").concat(JSON_PROP_NAME), airlineName));
			    	}
			    	
			    	finalFilter.put(BPRIORITY_PRODUCTDETAILS.concat(".").concat(BPRIORITY_PRODUCTS).concat(".").concat(JSON_PROP_FLIGHTS), makeElemMatchObj(airlineArr));
			    	
			    	//Added to accommodate suppliers 
			    	finalFilter.put("suppliers", suppRef);
			    	
			    	String data = MDMUtils.getData(String.format(MDMConfigV2.getURL(BPRIORITY_NAME), finalFilter), true);
			    	
			    	if(data.length()<3)// If the string is empty it returns "{}" so i'm checking the length rather than converting it to JSON
					{
						logger.info("Something went wrong on MDM side");//not sure what to do if this case comes up
//							priorityArr.put(new JSONObject());// Did this so if we dont get a priority for one ODO we send an empty json object
						continue;
					}
					
			    	JSONObject mdmRS = new JSONObject(data);
			    	
			    	if(mdmRS.getJSONArray("data").length()==0)
			    	{
						logger.info("No Booking Priority Document Found");//not sure what to do if this case comes up
//							priorityArr.put(new JSONObject());// Did this so if we dont get a priority for one ODO we send an empty json object
						continue;
					}
			    	
					JSONObject latestDoc = MDMUtils.getLatestUpdatedJson(mdmRS);
					
					if(!latestDoc.has(BPRIORITY_PRIORITYSETTINGS))
					{
						logger.info("No Priority Settings Found in the MDM Document");//Not sure what to do if this case comes up
//							priorityArr.put(new JSONObject()); // Did this so if we dont get a priority for one ODO we send an empty json object
						continue;
					}
			    	
			    	JSONObject priorityJson = new JSONObject(latestDoc.toString());
			    	System.out.println(priorityJson);
			    	priorityArr.put(new JSONObject(priorityJson.getJSONObject(BPRIORITY_PRIORITYSETTINGS).toString()));
				}
			}
			
			JSONObject refPriorityJson = new JSONObject();
			
			for(int z=0;z<priorityArr.length();z++)
			{
				JSONObject priorityJson = priorityArr.getJSONObject(z);
				
				if(refPriorityJson.length()==0)
					refPriorityJson = priorityJson;
				
				if(priorityJson.getString(BPRIORITY_PRIORITYSETTINGS).equalsIgnoreCase("high"))
				{
					refPriorityJson = priorityJson;
				}
				else if(priorityJson.getString(BPRIORITY_PRIORITYSETTINGS).equalsIgnoreCase("medium"))
				{
					if(refPriorityJson.getString(BPRIORITY_PRIORITYSETTINGS).equalsIgnoreCase("low") || refPriorityJson.getString(BPRIORITY_PRIORITYSETTINGS).equalsIgnoreCase("medium"))
						refPriorityJson = priorityJson;
				}
				else
				{
					if(refPriorityJson.getString(BPRIORITY_PRIORITYSETTINGS).equalsIgnoreCase("low"))
						refPriorityJson = priorityJson;
				}
			}
			
			finalPriorityArr.put(refPriorityJson);
		}
		
		bookingJson = finalPriorityArr.toString();
		return bookingJson;
	}
	
	public static String getPriorityForOps(String bookID, String orderID, String bookingStatus) 
	{
		String priorityString = "";
		
		JSONObject priorityJson = new JSONObject();
		JSONObject updateResponse = new JSONObject();
		
		try
		{
			String bookingString = getBookingFromDbServices(bookID);
			
			JSONObject bookingJson = new JSONObject(bookingString);
			
			
			JSONObject bookingRSBdy = bookingJson.getJSONObject("responseBody");
			JSONObject bookingRSHdr = bookingJson.getJSONObject("responseHeader");
			
			UserContext userCtx = UserContext.getUserContextForSession(bookingRSHdr);
			JSONArray entityDtlsArr = userCtx.getClientCommercialsHierarchy();
			
			String market = "";
			
			JSONObject filters = new JSONObject();
			
	//--X----------X-------------X-------------Client Details Filter Formation-------X----------------X-----------------X-------------------X---------X-------------X----------------X----------------X--------	
			
			JSONArray andArr = new JSONArray();
			for(int i=0;i<entityDtlsArr.length();i++)
			{
				JSONObject entityDtlsJson =	entityDtlsArr.getJSONObject(i);
				JSONObject andJson = new JSONObject();
				
				market = entityDtlsJson.getString(JSON_PROP_ENTITYMARKET);
	
				andJson.put(BPRIORITY_ENTITYTYPE, entityDtlsJson.getString(JSON_PROP_ENTITYTYPE));
				andJson.put(BPRIORITY_ENTITYNAME, entityDtlsJson.getString(JSON_PROP_ENTITYNAME));
				
				andArr.put(andJson);
			}
			
			JSONObject cliAndJsonObj = new JSONObject();
			cliAndJsonObj.put("$and", andArr);
			
			JSONObject cliElemMatchJson = new JSONObject();
			cliElemMatchJson.put("$elemMatch", cliAndJsonObj);
			
			filters.put(BPRIORITY_CLIENTDETAILS.concat(".").concat(BPRIORITY_ENTITIES), cliElemMatchJson);
			filters.put(BPRIORITY_CLIENTDETAILS.concat(".").concat(BPRIORITY_COMPANYMARKET), market);
			
			filters.put(BPRIORITY_BOOKINGCRITERIA.concat(".").concat(BPRIORITY_BOOKINGSTATUS), bookingStatus);
	
			JSONArray bookingProds = bookingRSBdy.getJSONArray("products");
			
			for(int i=0;i<bookingProds.length();i++){
				JSONObject bookingProd = bookingProds.getJSONObject(i);			
				
				if(!bookingProd.getString("orderID").equalsIgnoreCase(orderID))
					continue;
				
				priorityJson.put("orderID", orderID);
				
				JSONObject finalFilter = new JSONObject(filters.toString());
				
				String prodSubCat =	bookingProd.getString("productSubCategory");
				
				if(prodSubCat.equalsIgnoreCase("Flight"))
					priorityString = getAirFilterRS(bookingProd, finalFilter);
				else if(prodSubCat.equalsIgnoreCase("Hotel"))
					priorityString = getAccoFilterRS(bookingProd, finalFilter);
				
				priorityJson.put("product", prodSubCat);
				priorityJson.put("userID", "Test123");
				priorityJson.put("priority", new JSONObject(priorityString));
				
				break;
			}
			
		}
		catch(MalformedURLException e){
			logger.info("Exception While getting the data from MDM", e);
			updateResponse.put("ackMessage", "FAILED");//Change this later
			return updateResponse.toString();
		}
		catch(Exception e){
			logger.info("Exception while creating the filter", e);
			updateResponse.put("ackMessage", "FAILED");//Change this later
			return updateResponse.toString();
		}
		
		try
		{
			String updateUrl = String.format(DBServiceConfig.getDBUpdateURL(), "priority");
			URI uri= new URI(updateUrl);
			
			Map<String, String> mHttpHeaders = new HashMap<String, String>();
			mHttpHeaders.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
			
			updateResponse = HTTPServiceConsumer.consumeJSONService("", uri.toURL(), mHttpHeaders,"PUT", priorityJson);
		}
		catch (URISyntaxException e) {
			logger.info("URI Syntax Exception while putting the data in BE DB services", e);
			updateResponse.put("ackMessage", "FAILED");//Change this later
			return updateResponse.toString();
		}
		catch (MalformedURLException e) {
			logger.info("Malformed URL Exception while putting the data in BE DB services", e);
			updateResponse.put("ackMessage", "FAILED");//Change this later
			return updateResponse.toString();
		}
		catch (JSONException e) {
			logger.info("JSON Exception while putting the data in BE DB services", e);
			updateResponse.put("ackMessage", "FAILED");//Change this later
			return updateResponse.toString();
		}
		catch(Exception e)
		{
			logger.info("Exception while putting the data in DB Services", e);
			updateResponse.put("ackMessage", "FAILED");//Change this later
			return updateResponse.toString();
		}
		return updateResponse.toString();
	}

	private static String getAccoFilterRS(JSONObject bookingProd, JSONObject finalFilter) {
		try
		{
			finalFilter.put(BPRIORITY_PRODUCTDETAILS.concat(".").concat(BPRIORITY_PRODUCTCATEGORY), bookingProd.getString("productCategory"));
			finalFilter.put(BPRIORITY_PRODUCTDETAILS.concat(".").concat(BPRIORITY_PRODUCTSUBCATEGORY), bookingProd.getString("productSubCategory"));
			
			JSONArray accoAndArr = new JSONArray();
			
			JSONObject orderDtlsJson = bookingProd.getJSONObject("orderDetails");
			JSONObject hotelDtlsJson = orderDtlsJson.getJSONObject("hotelDetails");
			
			String hotelCode = hotelDtlsJson.getString("hotelCode");
			String cityCode = hotelDtlsJson.getString("cityCode");
			
			Map<String,Object> hotelInfoMap = new HashMap<String,Object>();
			
			hotelInfoMap = RedisHotelData.getHotelInfo(hotelCode);
			
			String name = (String) hotelInfoMap.getOrDefault("name", "");
	//		accoAndArr.put(makeJsonObj("product", name));
			
			String chain = (String) hotelInfoMap.getOrDefault("chain", "");
			accoAndArr.put(makeJsonObj("chain",chain));
			
			String brand = (String) hotelInfoMap.getOrDefault("brand", "");
			accoAndArr.put(makeJsonObj("brand", brand));
			
			String city = (String) hotelInfoMap.getOrDefault("city", "");
			
			Map<String,Object> cityAttrs = new HashMap<String,Object>();
			cityAttrs =	RedisCityData.getCityInfo(city);
			
			String country = (String) cityAttrs.getOrDefault(JSON_PROP_COUNTRY,"");
			accoAndArr.put(makeJsonObj("country", country));
			
			finalFilter.put("productDetails.products.accommodation", makeElemMatchObj(accoAndArr));
			
			String data = MDMUtils.getData(String.format(MDMConfigV2.getURL(BPRIORITY_NAME), finalFilter), true);
	    	
			if(data.length()<2)// If the string is empty it returns "{}" so i'm checking the length rather than converting it to JSON
			{
				logger.info("No Booking Priority Document Found");//not sure what to do if this case comes up
				return new JSONObject().toString();// Did this so if we dont get a priority for one ODO we send an empty json object
			}
			
			JSONObject latestDoc = MDMUtils.getLatestUpdatedJson(new JSONObject(data));
			
			if(!latestDoc.has("prioritySettings"))
			{
				logger.info("No Priority Settings Found in the MDM Document");//Not sure what to do if this case comes up
				return new JSONObject().toString();// Did this so if we dont get a priority for one ODO we send an empty json object
			}
			
	    	return latestDoc.getJSONObject("prioritySettings").toString();
		}
		catch(Exception e)
		{
			throw e;
		}
		
	}

	private static String getAirFilterRS(JSONObject bookingProd, JSONObject finalFilter) {
		try
		{
			finalFilter.put(BPRIORITY_PRODUCTDETAILS.concat(".").concat(BPRIORITY_PRODUCTCATEGORY), bookingProd.getString("productCategory"));
			finalFilter.put(BPRIORITY_PRODUCTDETAILS.concat(".").concat(BPRIORITY_PRODUCTSUBCATEGORY), bookingProd.getString("productSubCategory"));//How do i get the airline Name
			
			JSONArray priorityArr = new JSONArray();
			
			
			JSONObject orderDtlsJson =	bookingProd.getJSONObject("orderDetails");
			JSONObject flightDtlsJson = orderDtlsJson.getJSONObject("flightDetails");
			JSONArray odoJsonArr = flightDtlsJson.getJSONArray("originDestinationOptions");
			
			for(int j=0;j<odoJsonArr.length();j++)
			{
				JSONObject odoJson = odoJsonArr.getJSONObject(j);
				
				JSONArray flightSegJsonArr = odoJson.getJSONArray("flightSegment");
				for(int k=0;k<flightSegJsonArr.length();k++)
				{
					
					JSONObject odoJsonFilter = new JSONObject();
					odoJsonFilter = new JSONObject(finalFilter.toString());
					
					JSONObject flightSegJson =	flightSegJsonArr.getJSONObject(k);
					JSONArray airlineArr = new JSONArray();
					
			    	if(flightSegJson.has("operatingAirline"))
			    	{
			    		String airlineCode = flightSegJson.getJSONObject("operatingAirline").getString("airlineCode");
			    		
			    		Map<String,Object> origAirportInfo = RedisAirlineData.getAirlineDetails(airlineCode);
			        	String airlineName = (String) origAirportInfo.getOrDefault(AIRLINE_NAME, "");
			        	
			        	airlineArr.put(makeJsonObj("airlineName.name", airlineName));
			    	}
			    	
			    	JSONObject flSegAndArr = new JSONObject();
			    	flSegAndArr.put("$and", airlineArr);
			    	
			    	JSONObject flSegElemMatch = new JSONObject();
			    	flSegElemMatch.put("$elemMatch", flSegAndArr);
			    	
			    	odoJsonFilter.put("productDetails.products.flights", flSegElemMatch);
			    	
			    	String data = MDMUtils.getData(String.format(MDMConfigV2.getURL(BPRIORITY_NAME), odoJsonFilter), true);
			    	
			    	if(data.length()<2)// If the string is empty it returns "{}" so i'm checking the length rather than converting it to JSON
					{
						logger.info("No Booking Priority Document Found");//not sure what to do if this case comes up
	//					priorityArr.put(new JSONObject());// Did this so if we dont get a priority for one ODO we send an empty json object
						continue;
					}
					
					JSONObject latestDoc = MDMUtils.getLatestUpdatedJson(new JSONObject(data));
					
					if(!latestDoc.has("prioritySettings"))
					{
						logger.info("No Priority Settings Found in the MDM Document");//Not sure what to do if this case comes up
	//					priorityArr.put(new JSONObject()); // Did this so if we dont get a priority for one ODO we send an empty json object
						continue;
					}
			    	
			    	JSONObject priorityJson = new JSONObject(latestDoc.toString());
			    	priorityArr.put(new JSONObject(priorityJson.getJSONObject("prioritySettings").toString()));
				}
			}
			
			JSONObject refPriorityJson = new JSONObject();
			
			for(int i=0;i<priorityArr.length();i++)
			{
				JSONObject priorityJson = priorityArr.getJSONObject(i);
				
				if(refPriorityJson.length()==0)
					refPriorityJson = priorityJson;
				
				if(priorityJson.getString("prioritySettings").equalsIgnoreCase("high"))
				{
					refPriorityJson = priorityJson;
				}
				else if(priorityJson.getString("prioritySettings").equalsIgnoreCase("medium"))
				{
					if(refPriorityJson.getString("prioritySettings").equalsIgnoreCase("low") || refPriorityJson.getString("prioritySettings").equalsIgnoreCase("medium"))
						refPriorityJson = priorityJson;
				}
				else
				{
					if(refPriorityJson.getString("prioritySettings").equalsIgnoreCase("low"))
						refPriorityJson = priorityJson;
				}
				
			}
			
			return refPriorityJson.toString();
		}
		catch(Exception e)
		{
			throw e;
		}
		
	}
	
	private static JSONObject makeElemMatchObj(JSONArray andArr)
	{
		JSONObject andJson = new JSONObject();
		andJson.put("$and", new JSONArray(andArr.toString()));
			
		JSONObject elemMatchJson = new JSONObject();
		elemMatchJson.put("$elemMatch", andJson);
		
		return elemMatchJson;
	}
	
	private static JSONObject makeJsonObj(String key, String value)
	{
		JSONObject json = new JSONObject();
		json.put(key, value);
		
		return json;
	}
	
}
