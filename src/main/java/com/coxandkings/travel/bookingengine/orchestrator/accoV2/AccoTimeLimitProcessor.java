package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

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

import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.CommonUtil;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.TimeLimitConstants;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisHotelData;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;


public class AccoTimeLimitProcessor implements TimeLimitConstants{

	@SuppressWarnings("unchecked")
	public static String validateTimeLimit(JSONObject reqJson, JSONObject resJson) throws ParseException {
		
		Map<Integer,String> tldateMap = new HashMap<>();
		JSONObject roomInfo = new JSONObject();
		JSONObject hotelInfo = new JSONObject();
		JSONObject roomStayObject = new JSONObject();
		String accommodationSubType = "",mdmProductCat = "",mdmProductCatSubtype = "",mdmSupplierId = "",mdmDestination = "",mdmCountry = "",supplierRef = "",
				hotelCode = "",hotelName="",name="";
		JSONObject jTlObject = new JSONObject();
		Map<Integer, JSONObject> tlMap = new HashMap<>();
		int i=1,j=1;
		JSONArray objectArray = new JSONArray();
		JSONObject tlObj = new JSONObject();

		JSONObject requestHeader = reqJson.optJSONObject(JSON_PROP_REQUESTHEADER);
		
		UserContext usrCtx = UserContext.getUserContextForSession(requestHeader);
		
		JSONObject clientContext = requestHeader.optJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientID = clientContext.optString(JSON_PROP_CLIENTID);
		String clientType = clientContext.optString(JSON_PROP_CLIENTTYPE);
		String clientMarket = clientContext.optString(JSON_PROP_CLIENTMARKET);
		
		Date reqTravelDate = null;
		ArrayList<Date> reqTravel = new ArrayList<>();
		JSONObject requestBody = reqJson.optJSONObject(JSON_PROP_REQUESTBODY);
		JSONArray requestAccommodationInfoJarray = requestBody.optJSONArray(JSON_PROP_ACCO_ACCOINFO);
		for (Object requestAccommodationInfo : requestAccommodationInfoJarray) {
			JSONObject requestAccommodationInfoObj = (JSONObject) requestAccommodationInfo;
			String requestCheckIn = requestAccommodationInfoObj.optString(JSON_PROP_ACCO_CHECKIN)+"T00:00:00";
			reqTravelDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(requestCheckIn);
			reqTravel.add(reqTravelDate);
		}
		Collections.sort(reqTravel);
		reqTravelDate = reqTravel.get(0);
		
		
//		String requestCheckIn = ((JSONObject) requestAccommodationInfoJarray.opt(0)).optString(JSON_PROP_ACCO_CHECKIN)+"T00:00:00";
//		Date reqTravelDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(requestCheckIn);
		
		JSONObject responseBody = resJson.optJSONObject(JSON_PROP_RESPONSEBODY);
		JSONArray accommodationInfoJarray = responseBody.optJSONArray(JSON_PROP_ACCO_ACCOINFO);
		for (Object accommodationInfo : accommodationInfoJarray) {
			if(((JSONObject) accommodationInfo).has("errorMsg")) {
				continue;
			}
			int c1 = 0,c2 = 0;
			JSONObject accomodationInfoObject = (JSONObject) accommodationInfo;
			JSONArray roomStayJarray = accomodationInfoObject.optJSONArray(JSON_PROP_ACCO_ROOMSTAY);
			roomStayObject = roomStayJarray.optJSONObject(0);
			accommodationSubType = roomStayObject.optString(JSON_PROP_ACCO_ACCOSUBTYPE);
			roomInfo = roomStayObject.optJSONObject(JSON_PROP_ACCO_ROOMINFO);
			supplierRef = roomStayObject.optString(JSON_PROP_ACCO_SUPPLIERREF);
			hotelInfo = roomInfo.optJSONObject(JSON_PROP_ACCO_HOTELINFO);
			hotelCode = hotelInfo.optString(JSON_PROP_ACCO_HOTELCODE);
			name = hotelInfo.optString("hotelName");
            if(name==null || name.isEmpty()) 
            	 name = RedisHotelData.getHotelInfo(hotelCode, ACCO_NAME);
           
			String city = RedisHotelData.getHotelInfo(hotelCode, ACCO_CITY);
			String country = RedisCityData.getCityInfo(city, ACCO_COUNTRY);
			
			ClientInfo[] clientInfoArr=usrCtx.getClientCommercialsHierarchyArray();
			String entityType = null,entityID;
			
			if(clientType.equalsIgnoreCase(Constants.CLIENT_TYPE_B2C)) {
				entityID = usrCtx.getClientCommercialsHierarchyArray()[0].getCommercialsEntityId().toString();
				entityType =  usrCtx.getClientCommercialsHierarchyArray()[0].getCommercialsEntityType().toString(); 
			}
			else {
				entityID = clientID; 
				for(int d=0;d<clientInfoArr.length;d++) {
					if((usrCtx.getClientCommercialsHierarchyArray()[d].getClientId().toString()).equalsIgnoreCase(entityID)) {
						entityType =  usrCtx.getClientCommercialsHierarchyArray()[d].getCommercialsEntityType().toString(); 
					}
				}
				
			}
			
			//FindIterable<Document> TimeLimitMaster = MDMUtils.getTimeLimitMasterDetails(entityID,entityType,clientMarket);
			
			List<Document> TimeLimitMaster = MDMUtils.getTimeLimitMasterDetailsV2(entityID,entityType,clientMarket);
			
			/*MongoCursor<Document> timeLimitIterator = TimeLimitMaster.iterator();
			while(timeLimitIterator.hasNext()) {*/
			for(Document timeLimit:TimeLimitMaster) {
				//Document timeLimit = timeLimitIterator.next();
				List<Document> productsArray = (List<Document>) timeLimit.get(MDM_TL_PRODUCTS);
				int include[] = new int[productsArray.size()];
				int exclude[] = new int[productsArray.size()];
				List<String> mdmMode = new ArrayList<String>();
				for(Document products : productsArray) {
					mdmProductCat = products.getString(MDM_TL_PRODUCTCAT);
					mdmProductCatSubtype = products.getString(MDM_TL_PRODUCTCATSUBTYPE);
					mdmSupplierId = products.getString(MDM_TL_SUPPLIERID);
					mdmMode.add(products.getString(MDM_TL_MODE));
					List<Document> mdmProductFlavArray = (List<Document>) products.get(MDM_TL_PRODUCTFLAV);
					List<String> mdmProductName = new ArrayList<String>();
					for(Document mdmProductFlav : mdmProductFlavArray) {
						mdmProductName.add(mdmProductFlav.getString(MDM_TL_PRODUCTNAME));
					}
					mdmDestination = products.getString(MDM_TL_DESTINATION);
					mdmCountry = products.getString(MDM_TL_COUNTRY);

					 if(!mdmMode.isEmpty() && mdmMode.contains(MDM_TL_MODE_INCLUDE)) {
						if(CommonUtil.checkStringParams(ACCOMMODATION, mdmProductCat, MDM_TL_PRODUCTCAT) && 
								CommonUtil.checkStringParams(accommodationSubType, mdmProductCatSubtype, MDM_TL_PRODUCTCATSUBTYPE) && 
								CommonUtil.checkStringParams(supplierRef, mdmSupplierId, MDM_TL_SUPPLIERID) && mdmProductName.contains(name)) {
							include[c1]=1;
						}
						c1++;
					}
					if(!mdmMode.isEmpty() && mdmMode.contains(MDM_TL_MODE_EXCLUDE)) {
						if(CommonUtil.checkStringParams(ACCOMMODATION, mdmProductCat, MDM_TL_PRODUCTCAT) && 
								CommonUtil.checkStringParams(accommodationSubType, mdmProductCatSubtype, MDM_TL_PRODUCTCATSUBTYPE) && 
								CommonUtil.checkStringParams(supplierRef, mdmSupplierId, MDM_TL_SUPPLIERID) && mdmProductName.contains(name) && 
								CommonUtil.checkStringParams(city, mdmDestination, MDM_TL_DESTINATION) && 
								CommonUtil.checkStringParams(country, mdmCountry, MDM_TL_COUNTRY)) {
							exclude[c1]=1;
						}
						c1++;
					}
					c1=0;
				}
				for(int k1=0;k1<productsArray.size();k1++)
				{
					if(include[k1]==1)
						c1++;
					if(exclude[k1]==1)
						c2++;
				}
				if(mdmMode.contains(MDM_TL_MODE_INCLUDE) && mdmMode.contains(MDM_TL_MODE_EXCLUDE)) {
					if(c1>0 && c2==0) {
						jTlObject = generateTimeLimit(timeLimit, reqTravelDate, supplierRef);
						if(jTlObject.length() > 0)
							objectArray.put(jTlObject);
					}
				}
				else if(mdmMode.contains(MDM_TL_MODE_INCLUDE)) {
					if(c1>0) {
						jTlObject = generateTimeLimit(timeLimit, reqTravelDate, supplierRef);
						if(jTlObject.length() > 0)
							objectArray.put(jTlObject);
					}
				}
				else if(mdmMode.contains(MDM_TL_MODE_EXCLUDE)) {
					if(c2==0) {
						jTlObject = generateTimeLimit(timeLimit, reqTravelDate, supplierRef);
						if(jTlObject.length() > 0)
							objectArray.put(jTlObject);
					}
				}
			}
		}
		
		if(objectArray.length() > 0 && objectArray.length() >= accommodationInfoJarray.length()) {
			for(Object obj : objectArray) {
				JSONObject jobj = (JSONObject) obj;
				if(jobj.has(MAP_TIMELIMITDATE)) {
					tldateMap.put(j++,jobj.optString(MAP_TIMELIMITDATE));
					tlMap.put(i++, jobj);
				}
			}
		    Collection<String> myCol = tldateMap.values(); 	
			TreeSet<String> ts2 = new TreeSet<>(myCol);
			
			tlObj = tlMap.get(CommonUtil.getKeyFromValue(tldateMap, ts2.first()));
			
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			
			Calendar c = Calendar.getInstance();
			String str = sdf.format(c.getTime());
			String str1 = sdf.format(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(tlObj.getString("timeLimitDate")));
			
			if(str.equalsIgnoreCase(str1)) {
				JSONObject newtlObj = new JSONObject();
				newtlObj.put(JSON_PROP_TIMELIMAPP,false);
				responseBody.put("timeLimit", newtlObj);
			}
			else {
				responseBody.put("timeLimit", tlObj);
			}
		}
		else {
			tlObj.put(JSON_PROP_TIMELIMAPP,false);
			responseBody.put("timeLimit", tlObj);
		}
		return resJson.toString();
	}

	public static JSONObject generateTimeLimit(Document timeLimit, Date travelDateReq, String supplierRef) throws ParseException {
		
		Map<String,Date> datesMap = new HashMap<String,Date>();
		Date bookDateReq = new Date();
		Date tldate = new Date();
		Document periodicity = new Document();
		Calendar c = Calendar.getInstance();
		JSONObject jObject = new JSONObject();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		if(timeLimit.containsKey(MDM_TL_TLMEXPIRY)){
			Document tlmExpiry = (Document) timeLimit.get(MDM_TL_TLMEXPIRY);
			if(tlmExpiry.getBoolean(MDM_TL_SHOULDEXPIRE)==true && tlmExpiry.getString(MDM_TL_EXPIRYTYPE).equalsIgnoreCase(MDM_TL_EXPIRYTYPE_BOOKDATE)) {
				Document bookingDateOptions = (Document) timeLimit.get(MDM_TL_BOOKINGDATEOPTIONS);
				Document bookingDate = (Document) bookingDateOptions.get(MDM_TL_BOOKINGDATE);
				Document tlSetFor = (Document) bookingDateOptions.get(MDM_TL_BD_TLSETFOR);
				Document reconfirmationSetFor = (Document) bookingDateOptions.get(MDM_TL_BD_RECONFIRMATIONSETFOR);
				datesMap = CommonUtil.checkandReturnBookingDates(bookingDate,tlSetFor,reconfirmationSetFor,bookDateReq,travelDateReq);
			}
			else if(tlmExpiry.getBoolean(MDM_TL_SHOULDEXPIRE)==true && tlmExpiry.getString(MDM_TL_EXPIRYTYPE).equalsIgnoreCase(MDM_TL_EXPIRYTYPE_TRAVELDATE)) {
				Document travelDateOptions = (Document) timeLimit.get(MDM_TL_TRAVELDATEOPTIONS);
				Document travelDate = (Document) travelDateOptions.get(MDM_TL_TRAVELDATE);
				Document tlSetFor = (Document) travelDateOptions.get(MDM_TL_TD_TLSETFOR);
				Document reconfirmationSetFor = (Document) travelDateOptions.get(MDM_TL_TD_RECONFIRMATIONSETFOR);
				datesMap = CommonUtil.checkandReturnTravelDates(travelDate,tlSetFor,reconfirmationSetFor,bookDateReq,travelDateReq);
			}
			if(datesMap != null)
			{
				tldate = datesMap.get(MAP_TIMELIMITSETFOR);
				periodicity = CommonUtil.fetchSupplierSettlement(supplierRef);
				tldate = CommonUtil.checkSettlementTerms(periodicity,tldate,travelDateReq);
				Date dateBeforeBuffer = tldate;
				c.setTime(tldate);
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
					tldate = c.getTime();
				}
				if(bookDateReq.before(tldate) || bookDateReq.equals(tldate)) 
					datesMap.put(MAP_TIMELIMITSETFOR, tldate);
				else
					datesMap.put(MAP_TIMELIMITSETFOR, dateBeforeBuffer);
				
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
			}
		}
		return jObject;
	}
}
