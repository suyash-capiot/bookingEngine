package com.coxandkings.travel.bookingengine.orchestrator.acco;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class AccoRepriceProcessor implements AccoConstants {

	private static final Logger logger = LogManager.getLogger(AccoSearchProcessor.class);
	//static final String OPERATION_NAME = "reprice";

	//***********************************SI JSON TO XML FOR REQUEST BODY STARTS HERE**************************************//

	public static void setSupplierRequestElem(JSONObject reqJson,Element reqElem) throws Exception {

		Document ownerDoc = reqElem.getOwnerDocument();
		Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem, "./accoi:RequestBody/acco:OTA_HotelAvailRQWrapper");
		XMLUtils.removeNode(blankWrapperElem);
		JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null) ? reqJson.optJSONObject(JSON_PROP_REQHEADER) : new JSONObject();
		JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null) ? reqJson.optJSONObject(JSON_PROP_REQBODY) : new JSONObject();

		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);
        
		JSONObject clientContext=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientMrkt = clientContext.getString(JSON_PROP_CLIENTMARKET);
		String clientID = clientContext.getString(JSON_PROP_CLIENTID);
		
		AccoSearchProcessor.createSuppReqHdrElem(XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader"),sessionID,transactionID,userID,clientMrkt,clientID);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		String suppID;
		ProductSupplier prodSupplier;
		JSONArray multiReqArr = reqBodyJson.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONObject roomObjectJson;
		Element wrapperElement,suppCredsListElem= XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader/com:SupplierCredentialsList");
		//AccoSubType prodCategSubtype;
		for (int j=0; j < multiReqArr.length(); j++) {

			roomObjectJson =   multiReqArr.getJSONObject(j);
			
			AccoPriceProcessor.validateRequestParameters(roomObjectJson,reqHdrJson);
			
			suppID = roomObjectJson.getString(JSON_PROP_SUPPREF);
			//prodCategSubtype = AccoSubType.forString(roomObjectJson.optString(JSON_PROP_ACCOSUBTYPE));
			String prodCategSubtype = roomObjectJson.optString(JSON_PROP_ACCOSUBTYPE,""); 
			prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_ACCO,prodCategSubtype, suppID);

			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client for subtype %s", suppID, prodCategSubtype));
			}

			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc,j));
			wrapperElement= (Element) blankWrapperElem.cloneNode(true);

			XMLUtils.setValueAtXPath(wrapperElement, "./acco:SupplierID",suppID);
			XMLUtils.setValueAtXPath(wrapperElement, "./acco:Sequence", String.valueOf(j));
			setSuppReqOTAElem(ownerDoc, XMLUtils.getFirstElementAtXPath(wrapperElement,"./ota:OTA_HotelAvailRQ"), roomObjectJson,reqHdrJson);

			XMLUtils.insertChildNode(reqElem, "./accoi:RequestBody", wrapperElement, false);
		}
	}

	public static void setSuppReqOTAElem(Document ownerDoc,Element reqOTAElem,JSONObject reqParamJson,JSONObject reqHdrJson) {

		Element baseElem = XMLUtils.getFirstElementAtXPath(reqOTAElem, "./ota:AvailRequestSegments/ota:AvailRequestSegment/ota:HotelSearchCriteria/ota:Criterion");
		JSONArray roomConfigArr = reqParamJson.getJSONArray(JSON_PROP_REQUESTEDROOMARR);
		JSONObject roomJson;

		XMLUtils.setValueAtXPath(baseElem,"./ota:RefPoint/@CountryCode",reqParamJson.getString(JSON_PROP_COUNTRYCODE));
		XMLUtils.setValueAtXPath(baseElem,"./ota:HotelRef/@HotelCityCode",reqParamJson.getString(JSON_PROP_CITYCODE));
		XMLUtils.setValueAtXPath(baseElem,"./ota:HotelRef/@HotelCode",((JSONObject)roomConfigArr.get(0)).getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_HOTELINFO).getString(JSON_PROP_HOTELCODE));
		XMLUtils.setValueAtXPath(baseElem,"./ota:StayDateRange/@Start",reqParamJson.getString(JSON_PROP_CHKIN));
		XMLUtils.setValueAtXPath(baseElem,"./ota:StayDateRange/@End",reqParamJson.getString(JSON_PROP_CHKOUT));
		XMLUtils.setValueAtXPath(baseElem,"./ota:Profiles/ota:ProfileInfo/ota:Profile/ota:Customer/ota:CitizenCountryName/@Code",
				reqParamJson.getString(JSON_PROP_PAXNATIONALITY));

		for(int i=0;i<roomConfigArr.length();i++){
			roomJson = (JSONObject) roomConfigArr.get(i);
			XMLUtils.insertChildNode(baseElem,"./ota:RatePlanCandidates",getRatePlanCandidateElem(ownerDoc,roomJson,i+1),false);
			XMLUtils.insertChildNode(baseElem,"./ota:RoomStayCandidates",getRoomStayCandidateElem(ownerDoc,roomJson,i+1),false);
		}

	}

	public static Element getRoomStayCandidateElem(Document ownerDoc, JSONObject roomJson, int rph) {

		Element roomElem = AccoSearchProcessor.getRoomStayCandidateElem(ownerDoc, roomJson, rph);
		// extra params to be set in reprice
		JSONObject roomTypeJson = roomJson.getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_ROOMTYPEINFO);
		Element tpaElem = ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");

		tpaElem.appendChild(getMealInfo(ownerDoc, roomJson));
		roomElem.setAttribute("BookingCode", roomJson.getJSONObject(JSON_PROP_ROOMINFO)
				.getJSONObject(JSON_PROP_RATEPLANINFO).getString(JSON_PROP_RATEBOOKINGREF));
		roomElem.setAttribute("RoomTypeCode", roomTypeJson.getString(JSON_PROP_ROOMTYPECODE));
		roomElem.setAttribute("RoomType", roomTypeJson.getString(JSON_PROP_ROOMTYPENAME));
		roomElem.setAttribute("RoomID", roomTypeJson.getString(JSON_PROP_ROOMREF));
		roomElem.appendChild(tpaElem);

		return roomElem;
	}

	private static Element getMealInfo(Document ownerDoc, JSONObject roomJson) {

		Element mealsElem =  ownerDoc.createElementNS(NS_ACCO, "acco:Meals");
		Element mealElem = ownerDoc.createElementNS(NS_ACCO, "acco:Meal");
		JSONObject mealInfoJson = roomJson.getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_MEALINFO);

		mealElem.setAttribute("MealId", mealInfoJson.getString(JSON_PROP_MEALCODE));
		mealElem.setAttribute("Name", mealInfoJson.getString(JSON_PROP_MEALNAME));

		mealsElem.appendChild(mealElem);

		return mealsElem;

	}

	private static Element getRatePlanCandidateElem(Document ownerDoc, JSONObject roomJson, int rph) {

		Element ratePlanElem = ownerDoc.createElementNS(NS_OTA, "ota:RatePlanCandidate");
		JSONObject ratePlanJson = roomJson.getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_RATEPLANINFO);

		ratePlanElem.setAttribute("RatePlanID",ratePlanJson.getString(JSON_PROP_RATEPLANREF));
		ratePlanElem.setAttribute("RatePlanCode",ratePlanJson.getString(JSON_PROP_RATEPLANCODE));
		ratePlanElem.setAttribute("RPH",Integer.toString(rph));

		return ratePlanElem;
	}

	//***********************************SI JSON TO XML FOR REQUEST BODY ENDS HERE**************************************//

	public static String process(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException, ValidationException  {
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		Element reqElem = null;

		try{
			opConfig = AccoConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			//TrackingContext.setTrackingContext(reqJson);

			setSupplierRequestElem(reqJson, reqElem);
		}
		catch (ValidationException valx) {
			throw valx;
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		try {
			//Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(),AccoConfig.getHttpHeaders(),"POST",opConfig.getServiceTimeoutMillis(), reqElem);
			Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(),opConfig.getHttpHeaders(),"POST",opConfig.getServiceTimeoutMillis(), reqElem);
			//Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AccoConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			JSONObject resJson = AccoPriceProcessor.getSupplierResponseJSON(reqJson, resElem);

			//Commenting out because SI should always send filtered response
			//filterRooms(resJson,reqJson);
			
			Map<Integer,String> SI2BRMSRoomMap= new HashMap<Integer,String>();
			JSONObject resSupplierComJson =  SupplierCommercials.getSupplierCommercials(CommercialsOperation.Booking,reqJson, resJson, SI2BRMSRoomMap);
			JSONObject resClientComJson = ClientCommercials.getClientCommercials(reqJson,resSupplierComJson);

			AccoSearchProcessor.calculatePrices(reqJson,resJson,resClientComJson,resSupplierComJson,SI2BRMSRoomMap,true);
			
			try {
				TaxEngine.getCompanyTaxes(reqJson, resJson);
			}catch(Exception e) {}
			
			
			//This is done as offers is optional
			try{
				CompanyOffers.getCompanyOffers(CommercialsOperation.Reprice,reqJson, resJson, OffersType.COMPANY_SEARCH_TIME);
			}
			catch(Exception e) {}

			pushSuppFaresToRedisAndRemove(reqJson,resJson);

			AccoTimeLimitProcessor.validateTimeLimit(reqJson, resJson);
			
			if(!resJson.getJSONObject(JSON_PROP_RESBODY).getJSONObject(JSON_PROP_TIMELIMIT).optBoolean(JSON_PROP_TIMLIMAPPL)) {		
				AccoPartPaymentProcessor.validatePartPayment(reqJson, resJson);
			}
			
			return resJson.toString();

		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}

	}
	
	private static void filterRooms(JSONObject resJson,JSONObject reqJson) {
		String roomInReqKey = "";
		JSONArray reqAccoInfoArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONArray resAccoInfoArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		for(int i=0;i<reqAccoInfoArr.length();i++) {
			JSONObject reqAccoInfoObj = reqAccoInfoArr.getJSONObject(i);
			JSONObject resAccoInfoObj = resAccoInfoArr.getJSONObject(i);
			JSONArray roomStayArr = resAccoInfoObj.getJSONArray(JSON_PROP_ROOMSTAYARR);
			JSONArray roomConfig = reqAccoInfoObj.getJSONArray(JSON_PROP_REQUESTEDROOMARR);
			for(int j=0;j<roomConfig.length();j++) {
				roomInReqKey = AccoRepriceProcessor.getRedisKeyForRoomStay(roomConfig.getJSONObject(j).getJSONObject(JSON_PROP_ROOMINFO));
				for(int k=0;k<roomStayArr.length();k++) {
					String roomInResKey = AccoRepriceProcessor.getRedisKeyForRoomStay(roomStayArr.getJSONObject(k).getJSONObject(JSON_PROP_ROOMINFO));
					if(!roomInReqKey.equals(roomInResKey))
						roomStayArr.remove(k);
				}
			}
		}		
	}
	
	public static String opsProcess(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException  {
		JSONObject opsAmendmentJson=reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONObject("opsAmendments");
		String actionItem=opsAmendmentJson.getString("actionItem");
		try {
			Element resElem = XMLTransformer.toXMLElement(opsAmendmentJson.getString("siRepriceResponse"));
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			
			

			JSONObject resJson = AccoPriceProcessor.getSupplierResponseJSON(reqJson, resElem);
			Date bookingDate=DATE_FORMAT.parse(opsAmendmentJson.optString("bookingDate"));
			Map<Integer,String> SI2BRMSRoomMap= new HashMap<Integer,String>();
			JSONObject resSupplierComJson =  SupplierCommercials.getSupplierCommercials(CommercialsOperation.Booking,reqJson, resJson, SI2BRMSRoomMap);;
			/*if(actionItem.equalsIgnoreCase("amendEntityCommercials")){
				resSupplierComJson=new JSONObject(opsAmendmentJson.getString("suppCommRs"));
			}*/
			
			JSONObject resClientComJson = ClientCommercials.getClientCommercials(reqJson,resSupplierComJson);

			AccoSearchProcessor.calculatePrices(reqJson,resJson,resClientComJson,resSupplierComJson,SI2BRMSRoomMap,true,bookingDate);
			//TODO:Integrate with tax engine
			TaxEngine.getCompanyTaxes(reqJson, resJson);
			//pushSuppFaresToRedisAndRemove(reqJson,resJson);

			return resJson.toString();

		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}

	}		

	private static void pushSuppFaresToRedisAndRemove(JSONObject reqJson,JSONObject resJson) throws Exception {

		JSONObject resHeaderJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray multiResJsonArr = resBodyJson.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONArray multiReqJsonArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();
		String redisReqKey="",redisRoomKey="";
		for(int j=0; j < multiResJsonArr.length(); j++) {

			JSONArray roomStayJsonArr = ((JSONObject) multiResJsonArr.get(j)).optJSONArray(JSON_PROP_ROOMSTAYARR);
			if (roomStayJsonArr == null) {
				// TODO: This should never happen. Log a warning message here.
				return;
			}
			redisReqKey =getRedisKeyForReq((JSONObject) multiReqJsonArr.get(j));
			for (int i=0; i < roomStayJsonArr.length(); i++) {
				JSONObject roomStayJson = roomStayJsonArr.getJSONObject(i);
				Object suppTotalPriceInfoJson = roomStayJson.remove(JSON_PROP_SUPPROOMPRICE);
				Object suppNghtlyPriceInfoJson = roomStayJson.remove(JSON_PROP_SUPPNIGHTLYPRICEARR);
				Object TotalPriceInfoJson = roomStayJson.getJSONObject(JSON_PROP_ROOMPRICE);//ADDED FOR TOTAL PRICE
				Object clientCommercialsInfoJson = roomStayJson.remove(JSON_PROP_CLIENTENTITYCOMMS);
				Object supplierCommercialsInfoJson = roomStayJson.remove(JSON_PROP_SUPPCOMM);


				if ( suppTotalPriceInfoJson == null || suppNghtlyPriceInfoJson == null) {
					// TODO: This should never happen. Log a warning message here.
					//continue;
					suppTotalPriceInfoJson = roomStayJson.getJSONObject(JSON_PROP_ROOMPRICE);
					suppNghtlyPriceInfoJson = roomStayJson.getJSONArray(JSON_PROP_NIGHTLYPRICEARR);
				}

				JSONObject suppPriceBookInfoJson = new JSONObject();
				suppPriceBookInfoJson.put(JSON_PROP_SUPPROOMPRICE, suppTotalPriceInfoJson);
				suppPriceBookInfoJson.put(JSON_PROP_SUPPNIGHTLYPRICEARR, suppNghtlyPriceInfoJson);
				suppPriceBookInfoJson.put(JSON_PROP_SUPPCOMM, supplierCommercialsInfoJson==null?new JSONArray():supplierCommercialsInfoJson);
				suppPriceBookInfoJson.put(JSON_PROP_CLIENTCOMMENTITYDTLS, clientCommercialsInfoJson==null?new JSONArray():clientCommercialsInfoJson);
				suppPriceBookInfoJson.put(JSON_PROP_ROOMPRICE, TotalPriceInfoJson);
				suppPriceBookInfoJson.put(JSON_PROP_OCCUPANCYARR, roomStayJson.getJSONArray(JSON_PROP_OCCUPANCYARR));
				suppPriceBookInfoJson.put("offerDetailsSet", roomStayJson.optJSONArray("offerDetailsSet"));
				suppPriceBookInfoJson.put(JSON_PROP_PAYATHOTEL, roomStayJson.optBoolean(JSON_PROP_PAYATHOTEL,false));
				suppPriceBookInfoJson.put("supplierOffers", roomStayJson.optJSONArray("supplierOffers")==null?new JSONArray():roomStayJson.optJSONArray("supplierOffers"));
				
                redisRoomKey = redisReqKey.concat(getRedisKeyForRoomStay(roomStayJson.getJSONObject(JSON_PROP_ROOMINFO)));
				if(reprcSuppFaresMap.containsKey(redisRoomKey)) {
					//If this happens ask SI to make response unique on this key
					logger.error(String.format("[Overriding Key:%s,SubResponseIndex:%d,RoomIndex:%d]Prices cannot be cached in Redis as keys formed are not unique",redisRoomKey,j,i));
					//TODO:add a return instead.This is done for testing
					throw new Exception(String.format("[Overriding Key:%s,SubResponseIndex:%d,RoomIndex:%d]Prices cannot be cached in Redis as keys formed are not unique",redisRoomKey,j,i));
				}
				reprcSuppFaresMap.put(redisRoomKey, suppPriceBookInfoJson.toString());

			}
		}

		String redisKey = String.format("%s%c%s",resHeaderJson.optString(JSON_PROP_SESSIONID),KEYSEPARATOR,PRODUCT_ACCO);
		try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
		redisConn.hmset(redisKey, reprcSuppFaresMap);
		redisConn.pexpire(redisKey, (long) (AccoConfig.getRedisTTLMins() * 60 * 1000));
		//RedisConfig.releaseRedisConnectionToPool(redisConn);
		}

	}

	static String getRedisKeyForReq( JSONObject subReqJson) {

		return String.format("%s%c%s%c%s%c%s%c%s",subReqJson.getString(JSON_PROP_SUPPREF),KEYSEPARATOR,
				subReqJson.getString(JSON_PROP_COUNTRYCODE),KEYSEPARATOR,subReqJson.getString(JSON_PROP_CITYCODE),KEYSEPARATOR,
				subReqJson.getString(JSON_PROP_CHKIN),KEYSEPARATOR,subReqJson.getString(JSON_PROP_CHKOUT));
	}

	static String getRedisKeyForRoomStay(JSONObject roomInfoJson) {
		//TODO:should supplier ref be present or indexes/uuid should be used?
		//TODO:add req params here
		return String.format("%c%s%c%s%c%s%c%s%c%d%c%s",KEYSEPARATOR,roomInfoJson.getJSONObject(JSON_PROP_HOTELINFO).getString(JSON_PROP_HOTELCODE),KEYSEPARATOR,
				roomInfoJson.getJSONObject(JSON_PROP_ROOMTYPEINFO).getString(JSON_PROP_ROOMTYPECODE),KEYSEPARATOR,roomInfoJson.getJSONObject(JSON_PROP_ROOMTYPEINFO).getString(JSON_PROP_ROOMCATEGCODE),
				KEYSEPARATOR,roomInfoJson.getJSONObject(JSON_PROP_RATEPLANINFO).getString(JSON_PROP_RATEPLANCODE),KEYSEPARATOR,roomInfoJson.getInt(JSON_PROP_ROOMINDEX),KEYSEPARATOR,roomInfoJson.getJSONObject(JSON_PROP_ROOMTYPEINFO).getString(JSON_PROP_ROOMREF));
	}

	static String getRedisKeyForRoomPrice(JSONObject subReqJson,JSONObject roomInfoJson) {
		return String.format("%s%s", getRedisKeyForReq(subReqJson),getRedisKeyForRoomStay(roomInfoJson));
	}


}
