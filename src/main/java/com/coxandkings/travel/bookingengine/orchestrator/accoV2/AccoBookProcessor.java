package com.coxandkings.travel.bookingengine.orchestrator.accoV2;


import java.math.BigDecimal;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.enums.Status;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.enums.AccoSubType;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.BookingPriority;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class AccoBookProcessor  implements AccoConstants {

	private static final Logger logger = LogManager.getLogger(AccoBookProcessor.class);
	static final String OPERATION_NAME = "book";

	public static void setSupplierRequestElem(JSONObject reqJson,Element reqElem) throws Exception {

		Document ownerDoc = reqElem.getOwnerDocument();
		Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem, "./accoi:RequestBody/acco:OTA_HotelResRQWrapper");
		XMLUtils.removeNode(blankWrapperElem);
		JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null) ? reqJson.optJSONObject(JSON_PROP_REQHEADER) : new JSONObject();
		JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null) ? reqJson.optJSONObject(JSON_PROP_REQBODY) : new JSONObject();
		Map<String, String> reprcSuppFaresMap = null;
        
		AccoRequestValidator.validateBookId(reqBodyJson, reqHdrJson);
		AccoRequestValidator.validatePaymentInfo(reqBodyJson, reqHdrJson);
		
		String bookId = reqBodyJson.getString(JSON_PROP_BOOKID);
		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);

        JSONObject clientContext=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientMrkt = clientContext.getString(JSON_PROP_CLIENTMARKET);
		String clientID = clientContext.getString(JSON_PROP_CLIENTID);
		
		try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool()){
			String redisKey = String.format("%s%c%s", sessionID,KEYSEPARATOR,PRODUCT_ACCO);
			reprcSuppFaresMap = redisConn.hgetAll(redisKey);
			if (reprcSuppFaresMap == null || reprcSuppFaresMap.isEmpty()) {
				logger.warn(String.format("Reprice context not found for %s", redisKey));
				throw new Exception(String.format("Reprice context not found for %s", redisKey));
			}
		}

		AccoSearchProcessor.createSuppReqHdrElem(XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader"),sessionID,transactionID,userID,clientMrkt,clientID);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		String suppID;
		ProductSupplier prodSupplier;
		JSONArray multiReqArr = reqBodyJson.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONObject roomObjectJson;
		Element wrapperElement,suppCredsListElem= XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader/com:SupplierCredentialsList");
		AccoSubType prodCategSubtype;
		for (int j=0; j < multiReqArr.length(); j++) {
			
			validateRequestParam(reqHdrJson,multiReqArr.getJSONObject(j));
			
			roomObjectJson =   multiReqArr.getJSONObject(j);
			suppID = roomObjectJson.getString(JSON_PROP_SUPPREF);
			prodCategSubtype = AccoSubType.forString(roomObjectJson.optString(JSON_PROP_ACCOSUBTYPE));
			prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_ACCO,prodCategSubtype!=null?prodCategSubtype.toString():"", suppID);
            
			if (prodSupplier == null) {
				logger.info(String.format("Product supplier %s not found for user/client", suppID));
				throw new Exception();
			}
			roomObjectJson.put(JSON_PROP_CREDSNAME, prodSupplier.getCredentialsName());
			roomObjectJson.put(JSON_PROP_SUPPNAME, prodSupplier.getSuppName());
			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc,j));
			wrapperElement= (Element) blankWrapperElem.cloneNode(true);

			XMLUtils.setValueAtXPath(wrapperElement, "./acco:SupplierID",suppID);
			XMLUtils.setValueAtXPath(wrapperElement, "./acco:Sequence", String.valueOf(j));
			setSuppReqOTAElem(ownerDoc, XMLUtils.getFirstElementAtXPath(wrapperElement,"./ota:OTA_HotelResRQ"), roomObjectJson, reprcSuppFaresMap,reqHdrJson,bookId);

			XMLUtils.insertChildNode(reqElem, "./accoi:RequestBody", wrapperElement, false);
		}
	}

	public static void setSuppReqOTAElem(Document ownerDoc,Element reqOTAElem,JSONObject roomObjectJson, Map<String, String> reprcSuppFaresMap,JSONObject reqHdrJson, String bookId) throws Exception {

		Element hotelResElem = XMLUtils.getFirstElementAtXPath(reqOTAElem, "./ota:HotelReservations/ota:HotelReservation");
		Element roomStaysElem = (Element) XMLUtils.getFirstElementAtXPath(hotelResElem, "./ota:RoomStays");
		Element resGuestsElem =  XMLUtils.getFirstElementAtXPath(hotelResElem, "./ota:ResGuests");
		Element resGlobalInfoElem = XMLUtils.getFirstElementAtXPath(hotelResElem, "./ota:ResGlobalInfo");

		String cityCode = roomObjectJson.getString(JSON_PROP_CITYCODE);
		String countryCode = roomObjectJson.getString(JSON_PROP_COUNTRYCODE);
		String chkIn = roomObjectJson.getString(JSON_PROP_CHKIN);
		String chkOut = roomObjectJson.getString(JSON_PROP_CHKOUT);
		String redisReqKey = AccoRepriceProcessor.getRedisKeyForReq(roomObjectJson);
		String suppCcyCode="",roomKey="";
		JSONArray paxArr;
		JSONObject roomInfoJson,totalPriceInfoJson,price_commInfojson;
		Element roomStayElem,guestCountsElem,bookingTotalElem,bookingTaxElem,totalElem;
		int rph=0;
		BigDecimal bookingTotalPrice = new BigDecimal(0);
		BigDecimal bookingTaxPrice = new BigDecimal(0);
		BigDecimal roomTotalPrice,roomTaxPrice;

		for(Object roomConfig:roomObjectJson.getJSONArray(JSON_PROP_REQUESTEDROOMARR)) {
			
			AccoRequestValidator.validatePaxInfo((JSONObject)roomConfig, reqHdrJson);
			
			getPolicies(roomObjectJson,roomConfig,reqHdrJson);
			
			roomStayElem = ownerDoc.createElementNS(NS_OTA, "ota:RoomStay");
			guestCountsElem =  ownerDoc.createElementNS(NS_OTA, "ota:GuestCounts");

			paxArr = ((JSONObject) roomConfig).getJSONArray(JSON_PROP_PAXINFOARR);
			roomInfoJson = ((JSONObject) roomConfig).getJSONObject(JSON_PROP_ROOMINFO);
			roomKey = redisReqKey.concat(AccoRepriceProcessor.getRedisKeyForRoomStay(((JSONObject) roomConfig).getJSONObject(JSON_PROP_ROOMINFO)));
			if(reprcSuppFaresMap.get(roomKey)==null) {
				logger.info(String.format("Reprice context not found for %s. The request sent during book differs from that sent during reprice",roomKey));
				throw new ValidationException(reqHdrJson, "TRLERR1005");
			}
			price_commInfojson = new JSONObject(reprcSuppFaresMap.get(roomKey));
			
			//delete room Detail from redis
			String redisKey = String.format("%s%c%s", reqHdrJson.getString(JSON_PROP_SESSIONID),KEYSEPARATOR,PRODUCT_ACCO);
			deleteRoomFromRedis(redisKey,roomKey);
			
			totalPriceInfoJson = price_commInfojson.getJSONObject(JSON_PROP_SUPPROOMPRICE);
			((JSONObject) roomConfig).put(JSON_PROP_SUPPROOMPRICE,totalPriceInfoJson);
			((JSONObject) roomConfig).put(JSON_PROP_ROOMPRICE,price_commInfojson.getJSONObject(JSON_PROP_ROOMPRICE));
			((JSONObject) roomConfig).put(JSON_PROP_SUPPCOMM,price_commInfojson.optJSONArray(JSON_PROP_SUPPCOMM));
			((JSONObject) roomConfig).put(JSON_PROP_CLIENTCOMMENTITYDTLS,price_commInfojson.optJSONArray(JSON_PROP_CLIENTCOMMENTITYDTLS));
			((JSONObject) roomConfig).put(JSON_PROP_OCCUPANCYARR,price_commInfojson.getJSONArray(JSON_PROP_OCCUPANCYARR));
			((JSONObject) roomConfig).put("offerDetailsSet",price_commInfojson.optJSONArray("offerDetailsSet"));
			((JSONObject) roomConfig).put(JSON_PROP_PAYATHOTEL,price_commInfojson.optBoolean(JSON_PROP_PAYATHOTEL));
			((JSONObject) roomConfig).put("supplierOffers",price_commInfojson.optJSONArray("supplierOffers"));
			
			//This part is to get with Redemption OfferCodes from BRMS.
			JSONArray offerDetailsSetArr = price_commInfojson.optJSONArray(JSON_PROP_OFFERDETAILSSET);
			if(!(offerDetailsSetArr==null || offerDetailsSetArr.length()==0)) {
				JSONArray offerCodesArr=new JSONArray();
				for(int i=0;i<offerDetailsSetArr.length();i++) {
					JSONObject offrDetailSetObj = offerDetailsSetArr.getJSONObject(i);
					String withOrWithoutRedemption = offrDetailSetObj.getString("offerApplicability");
					if("WithRedemption".equalsIgnoreCase(withOrWithoutRedemption)) {
						offerCodesArr.put(offrDetailSetObj.getString(JSON_PROP_OFFERID));
					}
				}
				if(offerCodesArr.length()!=0) {
					JSONObject offerRedeemptionReq=new JSONObject();
					offerRedeemptionReq.put(JSON_PROP_USERID, reqHdrJson.getString(JSON_PROP_USERID));
					offerRedeemptionReq.put(JSON_PROP_BOOKID, bookId);
					offerRedeemptionReq.put("offerIDs", offerCodesArr);
					offerRedeemptionReq.put("operation", "booking");
					
					ServiceConfig commTypeConfig = AccoConfig.getOffersTypeConfig(OffersType.REDEEM_OFFER_CODE_GENERATION);
					JSONObject offerRedeemptionRes = null;
					try
					{
						logger.info("Before opening HttpURLConnection to BRMS for code generation");
						offerRedeemptionRes = HTTPServiceConsumer.consumeJSONService("BRMS_COMPANY_OFFER_CODE_GENERATION", commTypeConfig.getServiceURL(),commTypeConfig.getHttpHeaders(), offerRedeemptionReq);
						logger.info("HttpURLConnection to BRMS closed");
					}
				    catch(Exception x)
					{
						logger.warn("An exception occurred when calling code generation of Offer codes", x);
					}
					if(offerRedeemptionRes==null) {
						logger.info("Codes not generated for request"+offerRedeemptionReq.toString());
					}	
					else {
						((JSONObject) roomConfig).put("offerCodes",offerRedeemptionRes.getJSONArray("redemptionOfferCodes"));
					}
				}
			}
			
			
			roomStayElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:RoomTypes")).appendChild(getRoomTypeElem(ownerDoc, roomInfoJson));
			roomStayElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:RatePlans")).appendChild(getRateElem(ownerDoc, roomInfoJson));
			for(int k=0;k<paxArr.length();k++) {
				addGuestDetails(ownerDoc, (JSONObject) paxArr.get(k), resGuestsElem, guestCountsElem, rph++);
			}
			roomStayElem.appendChild(guestCountsElem);
			roomStayElem.appendChild(getTimeSpanElem(ownerDoc, chkIn, chkOut));
			//roomStayElem.appendChild(getTotalElem(ownerDoc, totalPriceInfoJson));
			totalElem = ownerDoc.createElementNS(NS_OTA, "ota:Total");
			suppCcyCode = totalPriceInfoJson.getString(JSON_PROP_CCYCODE);
			totalElem.setAttribute("CurrencyCode", suppCcyCode);
			roomTotalPrice = totalPriceInfoJson.getBigDecimal(JSON_PROP_AMOUNT);
			totalElem.setAttribute("AmountAfterTax", String.valueOf(roomTotalPrice));
			bookingTotalPrice = bookingTotalPrice.add(roomTotalPrice);
			JSONObject taxesJson = totalPriceInfoJson.optJSONObject(JSON_PROP_TOTALTAX);
			if(taxesJson!=null && taxesJson.has(JSON_PROP_AMOUNT)) {
				Element taxesElem = (Element) totalElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:Taxes"));
				taxesElem.setAttribute("CurrencyCode", taxesJson.getString(JSON_PROP_CCYCODE));
				roomTaxPrice = taxesJson.getBigDecimal(JSON_PROP_AMOUNT);
				taxesElem.setAttribute("Amount", String.valueOf(roomTaxPrice));
				bookingTaxPrice = bookingTaxPrice.add(roomTaxPrice);
			}

			roomStayElem.appendChild(totalElem);
			
			roomStayElem.appendChild(getHotelElem(ownerDoc, roomInfoJson, cityCode, countryCode));
			for(Object reference:roomInfoJson.getJSONArray(JSON_PROP_REFERENCESARR)) {
				roomStayElem.appendChild(getReferenceElem(ownerDoc, (JSONObject) reference));
			}
			roomStayElem.setAttribute("RPH", String.valueOf(roomInfoJson.get(JSON_PROP_ROOMRPH)));
			roomStayElem.setAttribute("RoomStayStatus", (roomInfoJson.getString(JSON_PROP_AVAILSTATUS)));

			roomStaysElem.appendChild(roomStayElem);
		}

		bookingTotalElem = XMLUtils.getFirstElementAtXPath(resGlobalInfoElem, "./ota:Total");
		bookingTaxElem = XMLUtils.getFirstElementAtXPath(bookingTotalElem, "./ota:Taxes");
		bookingTotalElem.setAttribute("AmountAfterTax", String.valueOf(bookingTotalPrice));
		bookingTotalElem.setAttribute("CurrencyCode", suppCcyCode);
		bookingTaxElem.setAttribute("Amount",  String.valueOf(bookingTaxPrice));
		bookingTaxElem.setAttribute("CurrencyCode", suppCcyCode);
		//TODO:payment info is hard coded in shell. It will be cnk payment details.Where will it come from?
		
	}

	private static void deleteRoomFromRedis(String redisKey, String roomKey) {
		try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool()){
			redisConn.hdel(redisKey,roomKey);
		}	
	}

	private static void validateRequestParam(JSONObject reqHdrJson, JSONObject roomObjectJson) throws ValidationException, ParseException {
		AccoRequestValidator.validateAccoSubType(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateCityCode(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateCountryCode(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateDates(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateSuppRef(roomObjectJson, reqHdrJson);
		
	}

	private static void getPolicies(JSONObject roomObjectJson, Object roomConfig,JSONObject reqHdrJson) throws InternalProcessingException, RequestProcessingException {
		JSONObject reqJson=new JSONObject();
		JSONObject reqBdy=new JSONObject();
		JSONObject accoInfoObj=new JSONObject();
		JSONArray accInfoArr=new JSONArray();
		reqJson.put(JSON_PROP_REQHEADER, reqHdrJson);
		reqJson.put(JSON_PROP_REQBODY, reqBdy);
		reqBdy.put(JSON_PROP_ACCOMODATIONARR, accInfoArr);
		accoInfoObj.put(JSON_PROP_SUPPREF, roomObjectJson.getString(JSON_PROP_SUPPREF));
		accoInfoObj.put(JSON_PROP_COUNTRYCODE, roomObjectJson.getString(JSON_PROP_COUNTRYCODE));
		accoInfoObj.put(JSON_PROP_CITYCODE, roomObjectJson.getString(JSON_PROP_CITYCODE));
		accoInfoObj.put(JSON_PROP_HOTELCODE, ((JSONObject) roomConfig).getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_HOTELINFO).getString(JSON_PROP_HOTELCODE));
		accoInfoObj.put(JSON_PROP_CHKIN, roomObjectJson.getString(JSON_PROP_CHKIN));
		accoInfoObj.put(JSON_PROP_CHKOUT, roomObjectJson.getString(JSON_PROP_CHKOUT));
		accoInfoObj.put(JSON_PROP_PAXNATIONALITY, "IN");//There is no paxNationality in Book RQ
		accoInfoObj.put(JSON_PROP_ACCOSUBTYPE, roomObjectJson.optString(JSON_PROP_ACCOSUBTYPE,"Hotel"));
		accoInfoObj.put(JSON_PROP_ROOMINFO, ((JSONObject) roomConfig).getJSONObject(JSON_PROP_ROOMINFO));
		
		JSONArray paxArr = ((JSONObject) roomConfig).getJSONArray(JSON_PROP_PAXINFOARR);
		int adltCount = 0;
		JSONArray chldAges=new JSONArray();
		for(int i=0;i<paxArr.length();i++) {
			String paxType = paxArr.getJSONObject(i).getString(JSON_PROP_PAXTYPE);
			if(Pax_ADT.equals(paxType)) {
				adltCount++;
			}
			if(Pax_CHD.equals(paxType)) {
				int Age = Utils.calculateAge(paxArr.getJSONObject(i).getString(JSON_PROP_DATEOFBIRTH));
				chldAges.put(Age);
			}
		}
		accoInfoObj.put(JSON_PROP_ADTCNT,adltCount );
		accoInfoObj.put(JSON_PROP_CHDAGESARR,chldAges);
		accInfoArr.put(accoInfoObj);
		String getPoliciesResStr = AccoGetPoliciesProcessor.process(reqJson);
		JSONObject getPolicy=new JSONObject(getPoliciesResStr);
		JSONObject accInfoObj = getPolicy.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR).getJSONObject(0);
		((JSONObject) roomConfig).put("policies",accInfoObj.getJSONArray("policies"));
		
		
	}

	public static Element getRoomTypeElem(Document ownerDoc, JSONObject roomInfoJson) {

		Element roomTypeElem = ownerDoc.createElementNS(NS_OTA, "ota:RoomType");
		Element tpaElem = ownerDoc.createElementNS(NS_OTA,"ota:TPA_Extensions");
		JSONObject roomTypeInfo = roomInfoJson.getJSONObject(JSON_PROP_ROOMTYPEINFO);

		roomTypeElem.setAttribute("RoomTypeCode",roomTypeInfo.getString(JSON_PROP_ROOMTYPECODE));
		roomTypeElem.setAttribute("RoomType",roomTypeInfo.getString(JSON_PROP_ROOMTYPENAME));
		roomTypeElem.setAttribute("RoomID",roomTypeInfo.getString(JSON_PROP_ROOMREF));
		tpaElem.appendChild(ownerDoc.createElementNS(NS_ACCO,"acco:RoomCategoryID")).setTextContent(roomTypeInfo.getString(JSON_PROP_ROOMCATEGCODE));
		tpaElem.appendChild(ownerDoc.createElementNS(NS_ACCO,"acco:RoomCategoryName")).setTextContent(roomTypeInfo.getString(JSON_PROP_ROOMCATEGNAME));
		roomTypeElem.setAttribute("RoomID",roomTypeInfo.getString(JSON_PROP_ROOMREF));
		roomTypeElem.appendChild(tpaElem);

		return roomTypeElem;
	}

	public static Element getRateElem(Document ownerDoc, JSONObject roomInfoJson) {

		Element ratePlanElem = ownerDoc.createElementNS(NS_OTA, "ota:RatePlan");
		JSONObject ratePlanInfo = roomInfoJson.getJSONObject(JSON_PROP_RATEPLANINFO);
		Element tpaElem = ownerDoc.createElementNS(NS_OTA,"ota:TPA_Extensions");

		ratePlanElem.setAttribute("RatePlanCode",ratePlanInfo.getString(JSON_PROP_RATEPLANCODE));
		ratePlanElem.setAttribute("RatePlanName",ratePlanInfo.getString(JSON_PROP_RATEPLANNAME));
		ratePlanElem.setAttribute("BookingCode",ratePlanInfo.getString(JSON_PROP_RATEBOOKINGREF));
		ratePlanElem.setAttribute("RatePlanID",ratePlanInfo.getString(JSON_PROP_RATEPLANREF));
		Element mealsElem = (Element) tpaElem.appendChild(ownerDoc.createElementNS(NS_ACCO,"acco:Meals"));
		Element mealElem = (Element) mealsElem.appendChild(ownerDoc.createElementNS(NS_ACCO,"acco:Meal"));
		mealElem.setAttribute("MealId", roomInfoJson.getJSONObject(JSON_PROP_MEALINFO).getString(JSON_PROP_MEALCODE));
		ratePlanElem.appendChild(tpaElem);

		return ratePlanElem;
	}

	public static Element getHotelElem(Document ownerDoc, JSONObject roomInfoJson,String cityCode,String countryCode) {

		Element hotelElem = ownerDoc.createElementNS(NS_OTA, "ota:BasicPropertyInfo");
		JSONObject hotelInfo = roomInfoJson.getJSONObject(JSON_PROP_HOTELINFO);

		hotelElem.setAttribute("HotelCode",hotelInfo.getString(JSON_PROP_HOTELCODE));
		hotelElem.setAttribute("HotelCodeContext",hotelInfo.getString(JSON_PROP_HOTELREF));
		hotelElem.setAttribute("HotelCityCode",cityCode);
		((Element) hotelElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:Address"))
				.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:CountryName")))
		.setAttribute("Code",countryCode);

		return hotelElem;
	}

	public static Element getReferenceElem(Document ownerDoc, JSONObject referenceJson) {

		Element referenceElem = ownerDoc.createElementNS(NS_OTA, "ota:Reference");

		referenceElem.setAttribute("ID",referenceJson.getString(JSON_PROP_REFVALUE));
		referenceElem.setAttribute("ID_Context", referenceJson.getString(JSON_PROP_REFNAME));
		referenceElem.setAttribute("Type",referenceJson.getString(JSON_PROP_REFCODE));

		return referenceElem;
	}

	public static Element getTimeSpanElem(Document ownerDoc, String chkIn,String chkOut) {

		Element timeSpanElem = ownerDoc.createElementNS(NS_OTA, "ota:TimeSpan");

		timeSpanElem.setAttribute("Start",chkIn);
		timeSpanElem.setAttribute("End",chkOut);

		return timeSpanElem;
	}

	public static Element getTotalElem(Document ownerDoc,JSONObject priceJson) {

		Element totalElem = ownerDoc.createElementNS(NS_OTA, "ota:Total");

		totalElem.setAttribute("CurrencyCode", priceJson.getString(JSON_PROP_CCYCODE));
		totalElem.setAttribute("AmountAfterTax", String.valueOf(priceJson.getBigDecimal(JSON_PROP_AMOUNT)));
		//bookingTotalPrice = bookingTotalPrice.add(roomTotalPrice);
		JSONObject taxesJson = priceJson.optJSONObject(JSON_PROP_TOTALTAX);
		if(taxesJson!=null && taxesJson.has(JSON_PROP_AMOUNT)) {
			Element taxesElem = (Element) totalElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:Taxes"));
			taxesElem.setAttribute("CurrencyCode", taxesJson.getString(JSON_PROP_CCYCODE));
			taxesElem.setAttribute("Amount", String.valueOf(taxesJson.getBigDecimal(JSON_PROP_AMOUNT)));
			//bookingTaxPrice = bookingTaxPrice.add(roomTaxPrice);
		}

		return totalElem;

	}

	public static Element getResGuestElement(Document ownerDoc,JSONObject paxInfo,String rph) {
		
		JSONObject contactDetails = (JSONObject) paxInfo.getJSONArray(JSON_PROP_CONTACTDTLS).getJSONObject(0).getJSONObject(JSON_PROP_CONTACTINFO);
		JSONObject addressDetails = (JSONObject) paxInfo.get(JSON_PROP_ADDRDTLS);

		Element resGuest = ownerDoc.createElementNS(NS_OTA, "ota:ResGuest");

		resGuest.setAttribute("ResGuestRPH",rph!=null?rph:"");
		resGuest.setAttribute("AgeQualifyingCode",Pax_CHD.equals(paxInfo.get(JSON_PROP_PAXTYPE))?Pax_CHD_ID:Pax_ADT_ID);
		resGuest.setAttribute("PrimaryIndicator",paxInfo.optBoolean(JSON_PROP_LEADPAX_IND,false)?"true":"false");
		resGuest.setAttribute("Age", Integer.toString(Utils.calculateAge(paxInfo.getString(JSON_PROP_DATEOFBIRTH))));

		Element customerElem = (Element) resGuest.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:Profiles"))
				.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:ProfileInfo"))
				.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:Profile"))
				.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:Customer"));
		customerElem.setAttribute("BirthDate",paxInfo.getString(JSON_PROP_DATEOFBIRTH));

		Element personNameElem = (Element) customerElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:PersonName"));
		personNameElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:NamePrefix")).setTextContent(paxInfo.getString(JSON_PROP_TITLE));
		personNameElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:GivenName")).setTextContent(paxInfo.getString(JSON_PROP_FIRSTNAME));
		personNameElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:MiddleName")).setTextContent(paxInfo.getString(JSON_PROP_MIDDLENAME));
		personNameElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:Surname")).setTextContent(paxInfo.getString(JSON_PROP_SURNAME));
		//TODO:citizenCountryName and code to be added.Is this same as address countryCodes and names?
		Element contactElem = (Element) customerElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:Telephone"));
		contactElem.setAttribute("CountryAccessCode",contactDetails.getString(JSON_PROP_COUNTRYCODE));
		contactElem.setAttribute("PhoneNumber",contactDetails.getString(JSON_PROP_MOBILENBR));

		customerElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:Email")).setTextContent(contactDetails.getString(JSON_PROP_EMAIL));

		Element addressElem = (Element) customerElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:Address"));
		addressElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:AddressLine")).setTextContent(paxInfo.getJSONObject(JSON_PROP_ADDRDTLS).getString(JSON_PROP_ADDRLINE2));
		addressElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:AddressLine")).setTextContent(paxInfo.getJSONObject(JSON_PROP_ADDRDTLS).getString(JSON_PROP_ADDRLINE1));
        addressElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:CityName")).setTextContent(addressDetails.getString(JSON_PROP_CITY));
		addressElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:PostalCode")).setTextContent(addressDetails.getString(JSON_PROP_ZIP));
		addressElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:CountryName")).setTextContent(addressDetails.getString(JSON_PROP_COUNTRY));
		XMLUtils.getFirstElementAtXPath(addressElem, "ota:CountryName").setAttribute("Code",addressDetails.getString(JSON_PROP_COUNTRY));
		//TODO:state and country code need to be added.Will wem provide codes?
		Element citizenCountryNameElem = (Element) customerElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:CitizenCountryName"));
		citizenCountryNameElem.setAttribute("Code",addressDetails.getString(JSON_PROP_COUNTRY));
		return resGuest;

	}

	public static void addGuestDetails(Document ownerDoc, JSONObject paxInfoJson, Element resGuestsElem,Element guestCountsElem, int rph) {

		String birthdate = paxInfoJson.getString(JSON_PROP_DATEOFBIRTH);
		Element guestElem = AccoSearchProcessor.getGuestCountElem(ownerDoc, 1, Utils.calculateAge(birthdate), paxInfoJson.getString(JSON_PROP_PAXTYPE));
		guestElem.setAttribute("ResGuestRPH",String.valueOf(rph));
		guestCountsElem.appendChild(guestElem);
		resGuestsElem.appendChild(getResGuestElement(ownerDoc, paxInfoJson, String.valueOf(rph)));
	}

	public static String processV2(JSONObject reqJson) throws ValidationException, InternalProcessingException, RequestProcessingException {
		//OperationConfig opConfig;
		ServiceConfig opConfig;
		Element reqElem;
		try{
		    opConfig = AccoConfig.getOperationConfig(OPERATION_NAME);
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			TrackingContext.setTrackingContext(reqJson);
			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			
			setSupplierRequestElem(reqJson, reqElem);
			
			kafkaRequestJson(reqJson,usrCtx);
		   
		}
		catch (ValidationException valx) {
			throw valx;
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		try{
			//Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(),AccoConfig.getHttpHeaders(),"POST",opConfig.getServiceTimeoutMillis(), reqElem);
			Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(),opConfig.getHttpHeaders(),"POST",opConfig.getServiceTimeoutMillis(), reqElem);
			//Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AccoConfig.getHttpHeaders(), reqElem);
			
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
            JSONObject resJson = getSupplierResponseJSON(reqJson, resElem);
			
            try {
				kafkaResponseJson(reqJson,resJson);
			}
			catch(Exception x){
				logger.warn("Exception occured while sending Kafka response message for book");
			}
			
			
			return resJson.toString();
		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}

	private static void kafkaResponseJson(JSONObject reqJson, JSONObject resJson) throws Exception {
		//kafka response
		/*JSONObject resBodyJson = new JSONObject();
		resBodyJson.put(JSON_PROP_BOOKID, reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID));
		JSONArray suppBooksJsonArr = new JSONArray();
		JSONArray accInfoArr=resJson.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		for(Object accInfoObj:accInfoArr) {
			JSONObject suppBookJson = new JSONObject();
			suppBookJson.put(JSON_PROP_SUPPREF,((JSONObject) accInfoObj).getString(JSON_PROP_SUPPREF));
			JSONArray suppBookRefArr=((JSONObject) accInfoObj).getJSONArray(JSON_PROP_SUPPBOOKREFERENCES);
			for(int i=0;i<suppBookRefArr.length();i++) {
				if("14".equals(suppBookRefArr.getJSONObject(i).getString(JSON_PROP_REFCODE))) {
					suppBookJson.put(JSON_PROP_REFVALUE,suppBookRefArr.getJSONObject(i).getString(JSON_PROP_REFVALUE));
					break;
				}
			}

			suppBooksJsonArr.put(suppBookJson);
		}
		resBodyJson.put(JSON_PROP_SUPPBOOKREFERENCES, suppBooksJsonArr);
		JSONObject kafkaMsgJson = new JSONObject();
		kafkaMsgJson.put(JSON_PROP_RESBODY, resBodyJson);
		kafkaMsgJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PROD, PRODUCT_ACCO);
		System.out.println("kafka response"+kafkaMsgJson);*/
		
		//JSONObject kafkaMsgJson = new JSONObject();
		//kafkaMsgJson.put(JSON_PROP_RESHEADER, resJson.getJSONObject(JSON_PROP_RESHEADER));
		JSONObject resBdy = resJson.getJSONObject(JSON_PROP_RESBODY);
		resBdy.put(JSON_PROP_BOOKID, reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID));
		resBdy.put(JSON_PROP_PROD,PRODUCT_ACCO);
		
		setPriorityRS("ACCO", reqJson, resJson);
		
		logger.info((String.format("%s_RS = %s", "BOOKKAFKA",resJson)));
		KafkaBookProducer bookProducer = new KafkaBookProducer();
		bookProducer.runProducer(1, resJson);
		
		for(int i=0;i<resBdy.getJSONArray(JSON_PROP_ACCOMODATIONARR).length();i++) {
			JSONObject accInfoObj = resBdy.getJSONArray(JSON_PROP_ACCOMODATIONARR).getJSONObject(i);
			if(accInfoObj.has("errorMessage")) {
				accInfoObj.remove("errorMessage");
				accInfoObj.remove("errorCode");
			}
		}
		
		//delete stored info from redis
		/*try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool()){
			String redisKey = String.format("%s%c%s", reqJson.getJSONObject(JSON_PROP_REQHEADER).getString(JSON_PROP_SESSIONID),KEYSEPARATOR,PRODUCT_ACCO);
			redisConn.del(redisKey);
		}*/
		
		removePriorityObj(resJson);
    }

	private static void removePriorityObj(JSONObject resJson)
	{
		JSONArray accommInfoArr = resJson.getJSONObject("responseBody").getJSONArray("accommodationInfo");
		
		for(int i=0;i<accommInfoArr.length();i++)
		{
			JSONObject accommInfo = accommInfoArr.getJSONObject(i);
			accommInfo.remove("priority");
		}
	}
	
	private static void kafkaRequestJson(JSONObject reqJson, UserContext usrCtx) throws Exception {
		
		JSONObject kafkaMsg=new JSONObject(new JSONTokener(reqJson.toString()));
		JSONObject reqBodyJson = kafkaMsg.getJSONObject(JSON_PROP_REQBODY);
		JSONObject reqHeaderJson = kafkaMsg.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		reqHeaderJson.put(JSON_PROP_COMPANYMKT, usrCtx.getOrganizationHierarchy().getCompanyMarket());
		reqHeaderJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
		reqHeaderJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
		reqHeaderJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, PRODUCT_ACCO);
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
		addSupplierClientCommTotalComm(kafkaMsg, usrCtx);
		
		setPriorityRQ("ACCO", kafkaMsg);
		
		logger.info((String.format("%s_RQ = %s", "BOOKKAFKA",kafkaMsg)));
		KafkaBookProducer bookProducer = new KafkaBookProducer();
		bookProducer.runProducer(1, kafkaMsg);
		
	}
    
	private static void setPriorityRQ(String product, JSONObject reqJson)
	{
		String priorityStr = BookingPriority.getPriorityForBE(product, reqJson, "On request");
		
		JSONArray priorityArr = new JSONArray(priorityStr.toString());
		JSONArray accomArr = reqJson.getJSONObject("requestBody").getJSONArray("accommodationInfo");
		
		for(int i=0;i<accomArr.length();i++)
		{
			JSONObject pricedItin = accomArr.getJSONObject(i);
			pricedItin.put("priority", priorityArr.getJSONObject(i));
		}
	}
	
	private static void setPriorityRS(String product,JSONObject reqJson,JSONObject kafkaMsg)
	{
		String priorityStr = BookingPriority.getPriorityForBE(product, reqJson, "Confirmed");
		
		JSONArray priorityArr = new JSONArray(priorityStr.toString());
		JSONArray suppBookArr = kafkaMsg.getJSONObject("responseBody").getJSONArray("accommodationInfo");
		
		for(int i=0;i<suppBookArr.length();i++)
		{
			JSONObject suppBook = suppBookArr.getJSONObject(i);
			suppBook.put("priority", priorityArr.getJSONObject(i));
		}
	}
	
	private static void addSupplierClientCommTotalComm(JSONObject kafkamsgjson, UserContext usrCtx) {
        JSONObject reqBody = kafkamsgjson.getJSONObject(JSON_PROP_REQBODY);
		JSONArray multiReqArr = reqBody.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		for(int i=0;i<multiReqArr.length();i++) {//accoinfo arr
			Map<String, JSONObject> suppCommTotalsMap = new HashMap<String, JSONObject>();
			Map<String, JSONObject> supptaxBrkUpTotalsMap = new HashMap<String, JSONObject>();
			Map<String, JSONObject> roomPrctaxBrkUpTotalsMap = new HashMap<String, JSONObject>();
			Map<String, JSONObject> clientCommTotalsMap = new HashMap<String, JSONObject>();
		    Map<String, JSONObject> companyTaxTotalsMap = new HashMap<String, JSONObject>();
		    Map<String, JSONObject> discountTotalsMap = new HashMap<String, JSONObject>();
		    Map<String, JSONObject> receivablesTotalsMap = new HashMap<String, JSONObject>(); 
		    Map<String, JSONObject> incentivesTotalsMap = new HashMap<String, JSONObject>();
			JSONArray suppCommTotalsJsonArr = new JSONArray();
			JSONArray clientEntityTotalCommArr=new JSONArray();
			JSONObject roomObjectJson =   multiReqArr.getJSONObject(i);	
			BigDecimal totalroomSuppInfoAmt= new BigDecimal(0);
			BigDecimal totalroomPriceAmt= new BigDecimal(0);
			BigDecimal totalroomTaxAmt= new BigDecimal(0);
			BigDecimal totalroomSuppTaxAmt= new BigDecimal(0);
			JSONObject totalRoomSuppPriceInfo=new JSONObject();
			JSONObject totalpriceInfo=new JSONObject();
			JSONObject companyTaxesTotal=new JSONObject();
			JSONObject discountsTotal=new JSONObject();
			JSONObject receivablesTotal=new JSONObject();
			JSONObject incentivesTotal=new JSONObject();
			for(Object roomConfig:roomObjectJson.getJSONArray(JSON_PROP_REQUESTEDROOMARR)) {//roomConfig length

				//Calculate total roomSuppPriceInfo
				JSONObject roomSuppInfo=((JSONObject) roomConfig).getJSONObject(JSON_PROP_SUPPROOMPRICE);
				BigDecimal[] suppRoomTotals=getTotalTaxesV2(roomSuppInfo,totalRoomSuppPriceInfo,supptaxBrkUpTotalsMap,totalroomSuppInfoAmt,totalroomSuppTaxAmt);
				totalroomSuppInfoAmt=suppRoomTotals[0];
				totalroomSuppTaxAmt=suppRoomTotals[1];

                //Calculate total roomPriceInfo
				JSONObject roompriceInfo=((JSONObject) roomConfig).getJSONObject(JSON_PROP_ROOMPRICE);
				BigDecimal[] roomTotals=getTotalTaxesV2(roompriceInfo,totalpriceInfo,roomPrctaxBrkUpTotalsMap,totalroomPriceAmt,totalroomTaxAmt);
				totalroomPriceAmt=roomTotals[0];
				totalroomTaxAmt=roomTotals[1];
				
				//Add Receivables
				JSONObject receivablesObj = roompriceInfo.optJSONObject("receivables");
				if(receivablesObj!=null) {
				getTotalRecvblesIncentives(receivablesObj, totalpriceInfo,receivablesTotal,receivablesTotalsMap,"receivables.receivable");
				}
				
				//Add Incentives
				JSONObject incentivesObj = roompriceInfo.optJSONObject("incentives");
				if(incentivesObj!=null) {
					AccoBookProcessor.getTotalRecvblesIncentives(incentivesObj, totalpriceInfo,incentivesTotal,incentivesTotalsMap,"incentives.incentive");
				}
				
				//Add companyTax
				JSONObject companyTaxesObj = roompriceInfo.optJSONObject("companyTaxes");
				if(companyTaxesObj!=null) {
				getTotalCompanyTaxes(companyTaxesObj,totalpriceInfo,companyTaxesTotal,companyTaxTotalsMap);
				}
				
				//orderOfferDetailSet
				JSONArray offDtlSetArr = ((JSONObject) roomConfig).optJSONArray("offerDetailsSet");
				if(offDtlSetArr!=null) {
					for(int l=0;l<offDtlSetArr.length();l++) {
						roomObjectJson.append("offerDetailsSet",offDtlSetArr.getJSONObject(l));
					}
				}	
				
				//orderOfferCodes
				JSONArray offCodesArr = ((JSONObject) roomConfig).optJSONArray("offerCodes");
				if(offCodesArr!=null) {
					for(int l=0;l<offCodesArr.length();l++) {
						roomObjectJson.append("offerCodes",offCodesArr.getJSONObject(l));
					}
				}	
				
				//Add discounts
				JSONObject discountsObj = roompriceInfo.optJSONObject("discounts");
				if(discountsObj!=null) {
				getTotalDiscounts(discountsObj,totalpriceInfo,discountsTotal,discountTotalsMap);
				}
				
				//ADD SUPPLIER COMMERCIALS
				JSONArray suppCommJsonArr=((JSONObject) roomConfig).optJSONArray(JSON_PROP_SUPPCOMM);

				// If no supplier commercials have been defined in BRMS, the JSONArray for suppCommJsonArr will be null.
				// In this case, log a message and proceed with other calculations.
				if (suppCommJsonArr == null) {
					logger.warn("No supplier commercials found");
				}
				else {
					for (int j=0; j < suppCommJsonArr.length(); j++) {
						JSONObject suppCommJson = suppCommJsonArr.getJSONObject(j);
						String suppCommName = suppCommJson.getString(JSON_PROP_COMMNAME);
						JSONObject suppCommTotalsJson = null;
						if (suppCommTotalsMap.containsKey(suppCommName)) {
							suppCommTotalsJson = suppCommTotalsMap.get(suppCommName);
							suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT)));
						}
						else {
							suppCommTotalsJson = new JSONObject();
							suppCommTotalsJson.put(JSON_PROP_COMMNAME, suppCommName);
							suppCommTotalsJson.put(JSON_PROP_COMMCCY, suppCommJson.optString(JSON_PROP_COMMCCY));
							suppCommTotalsJson.put(JSON_PROP_COMMTYPE, suppCommJson.getString(JSON_PROP_COMMTYPE));
							suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
							suppCommTotalsMap.put(suppCommName, suppCommTotalsJson);
						}
					}

				}
				
				/*Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet().iterator();
				while (suppCommTotalsIter.hasNext()) {
					suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
				}*/
				
	            
				//ADDING CLIENTCOMMTOTAL
				JSONArray clientCommJsonArr=((JSONObject) roomConfig).optJSONArray(JSON_PROP_CLIENTCOMMENTITYDTLS);
				if (clientCommJsonArr == null) {
					logger.warn("No supplier commercials found");
				}
				else {
					//for each entity in clientcomm add a new object in clientEntityTotalCommercials Array
					for (int j=0; j < clientCommJsonArr.length(); j++) {
						JSONObject clientCommJson = clientCommJsonArr.getJSONObject(j);
						String entityName = clientCommJson.getString(JSON_PROP_ENTITYNAME);
						JSONArray clntCommArr = clientCommJson.getJSONArray(JSON_PROP_CLIENTCOMM);
			            JSONObject clientCommTotalsJson = null;
						

						//Add the commercial type inside the markUpCommercialDetails obj into clientCommTotalsMap and calculate total of each entity
						for(int k=0;k<clntCommArr.length();k++) {
						JSONObject clntCommJsonObj= clntCommArr.getJSONObject(k);
						String clientCommName = clntCommArr.getJSONObject(k).getString(JSON_PROP_COMMNAME);

						if (clientCommTotalsMap.containsKey(clientCommName.concat(entityName))) {
							clientCommTotalsJson = clientCommTotalsMap.get(clientCommName.concat(entityName));
							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, clientCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(clntCommJsonObj.getBigDecimal(JSON_PROP_COMMAMOUNT)));
						}
						else {
							clientCommTotalsJson = new JSONObject();
							//clientCommTotalsJson.put("mdmruleId", clntCommJsonObj.getString("mdmruleId"));
							clientCommTotalsJson.put(JSON_PROP_COMMNAME, clientCommName);
							clientCommTotalsJson.put(JSON_PROP_COMMTYPE, clntCommJsonObj.getString(JSON_PROP_COMMTYPE));
							clientCommTotalsJson.put(JSON_PROP_COMMCCY, clntCommJsonObj.optString(JSON_PROP_COMMCCY));
							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, clntCommJsonObj.getBigDecimal(JSON_PROP_COMMAMOUNT));
							clientCommTotalsMap.put(clientCommName.concat(entityName), clientCommTotalsJson);
						}

						//
						
						}
						JSONArray clientCommTotalsJsonArr = new JSONArray();
						Iterator<Entry<String, JSONObject>> clientCommTotalsIter = clientCommTotalsMap.entrySet().iterator();
						while (clientCommTotalsIter.hasNext()) {
							clientCommTotalsJsonArr.put(clientCommTotalsIter.next().getValue());
						}
						if(!clientCommTotalsMap.containsKey(entityName)) {
							JSONObject clientEntityTotalJson=new JSONObject();
							clientEntityTotalJson.put(JSON_PROP_CLIENTID, clientCommJson.getString(JSON_PROP_CLIENTID));
							clientEntityTotalJson.put(JSON_PROP_PARENTCLIENTID, clientCommJson.getString(JSON_PROP_PARENTCLIENTID));
							clientEntityTotalJson.put(JSON_PROP_COMMENTITYTYPE, clientCommJson.getString(JSON_PROP_COMMENTITYTYPE));
							clientEntityTotalJson.put(JSON_PROP_COMMENTITYID,clientCommJson.getString(JSON_PROP_COMMENTITYID));
							clientCommTotalsMap.put(entityName,clientEntityTotalJson);
							clientEntityTotalJson.put(JSON_PROP_CLIENTCOMMTOTAL, clientCommTotalsJsonArr);
							clientEntityTotalCommArr.put(clientEntityTotalJson);
					}
						

				}
				}
				//final total calculation at order level
				/*roomObjectJson.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr);
				roomObjectJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientEntityTotalCommArr);
				roomObjectJson.put(JSON_PROP_SUPPBOOKPRICE, totalRoomSuppPriceInfo);
				roomObjectJson.put(JSON_PROP_BOOKPRICE, totalpriceInfo);*/
				
				
			}
			Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet().iterator();
			while (suppCommTotalsIter.hasNext()) {
				suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
			}
			
			//Added FinanceControl ID
			ProductSupplier productSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_ACCO, roomObjectJson.getString(JSON_PROP_ACCOSUBTYPE), roomObjectJson.getString(JSON_PROP_SUPPREF));
			String financeControlId = productSupplier.getFinanceControlId()!=null? productSupplier.getFinanceControlId() : "";
	
			roomObjectJson.put("financeControlId", financeControlId);
			
			roomObjectJson.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr);
			roomObjectJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientEntityTotalCommArr);
			roomObjectJson.put(JSON_PROP_SUPPBOOKPRICE, totalRoomSuppPriceInfo);
			roomObjectJson.put(JSON_PROP_BOOKPRICE, totalpriceInfo);
			String suppCurCode = roomObjectJson.getJSONObject(JSON_PROP_SUPPBOOKPRICE).getString(JSON_PROP_CCYCODE);
			JSONObject clientContxt = kafkamsgjson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT);
			roomObjectJson.put("roe", RedisRoEData.getRateOfExchange(suppCurCode, clientContxt.getString(JSON_PROP_CLIENTCCY),clientContxt.getString(JSON_PROP_CLIENTMARKET)));
		}
		
		
	}
	
	/*private static void addSupplierClientCommTotalComm(JSONObject kafkamsgjson) {
		Map<String, JSONObject> suppCommTotalsMap = new HashMap<String, JSONObject>();
		Map<String, JSONObject> supptaxBrkUpTotalsMap = new HashMap<String, JSONObject>();
		Map<String, JSONObject> roomPrctaxBrkUpTotalsMap = new HashMap<String, JSONObject>();


		JSONObject reqBody = kafkamsgjson.getJSONObject(JSON_PROP_REQBODY);
		JSONArray multiReqArr = reqBody.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		for(int i=0;i<multiReqArr.length();i++) {
			JSONObject roomObjectJson =   multiReqArr.getJSONObject(i);	
			Map<String, JSONObject> clientCommTotalsMap = new HashMap<String, JSONObject>();
			BigDecimal totalroomSuppInfoAmt= new BigDecimal(0);
			BigDecimal totalroomPriceAmt= new BigDecimal(0);
			BigDecimal totalroomTaxAmt= new BigDecimal(0);
			BigDecimal totalroomSuppTaxAmt= new BigDecimal(0);
			JSONObject totalRoomSuppPriceInfo=new JSONObject();
			JSONObject totalpriceInfo=new JSONObject();
			for(Object roomConfig:roomObjectJson.getJSONArray(JSON_PROP_REQUESTEDROOMARR)) {//roomConfig length

				//Calculate total roomSuppPriceInfo
				JSONObject roomSuppInfo=((JSONObject) roomConfig).getJSONObject(JSON_PROP_SUPPROOMPRICE);
				BigDecimal[] suppRoomTotals=getTotalTaxesV2(roomSuppInfo,totalRoomSuppPriceInfo,supptaxBrkUpTotalsMap,totalroomSuppInfoAmt,totalroomSuppTaxAmt);
				totalroomSuppInfoAmt=suppRoomTotals[0];
				totalroomSuppTaxAmt=suppRoomTotals[1];

				JSONObject totalRoomSuppPriceInfo=new JSONObject();
				totalroomSuppInfoAmt=totalroomSuppInfoAmt.add(roomSuppInfo.getBigDecimal(JSON_PROP_AMOUNT));
				totalRoomSuppPriceInfo.put("amount", totalroomSuppInfoAmt) ;
				totalRoomSuppPriceInfo.put("currencyCode", roomSuppInfo.getString("currencyCode"));
				JSONObject totaltaxes=new JSONObject();
				JSONObject taxes=roomSuppInfo.getJSONObject("taxes");
				totalroomSuppTaxAmt=totalroomSuppTaxAmt.add(taxes.optBigDecimal("amount",new BigDecimal(0)));
				totaltaxes.put(JSON_PROP_AMOUNT,totalroomSuppTaxAmt );
				totaltaxes.put(JSON_PROP_CCYCODE, taxes.getString("currencyCode"));
				JSONArray taxBreakUpArr = taxes.getJSONArray("taxBreakup");
				for(int t=0;t<taxBreakUpArr.length();t++) {
					JSONObject taxBrkUpJson = taxBreakUpArr.getJSONObject(t);
					String taxCode= taxBrkUpJson.getString("taxCode");
					JSONObject taxBrkUpTotalsJson = null;
					if (supptaxBrkUpTotalsMap.containsKey(taxCode)) {
						taxBrkUpTotalsJson = supptaxBrkUpTotalsMap.get(taxCode);
						taxBrkUpTotalsJson.put(JSON_PROP_AMOUNT, taxBrkUpTotalsJson.getBigDecimal(JSON_PROP_AMOUNT).add(taxBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT)));
					}
					else {
						taxBrkUpTotalsJson = new JSONObject();
						taxBrkUpTotalsJson.put(JSON_PROP_AMOUNT,taxBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT));
						taxBrkUpTotalsJson.put("taxCode", taxBrkUpJson.getString("taxCode"));
						taxBrkUpTotalsJson.put(JSON_PROP_CCYCODE,taxBrkUpJson.getString(JSON_PROP_CCYCODE));
						supptaxBrkUpTotalsMap.put(taxCode, taxBrkUpTotalsJson);
					}

				}
				JSONArray TotaltaxBrkUpArr = new JSONArray();
				Iterator<Entry<String, JSONObject>> supptaxIter = supptaxBrkUpTotalsMap.entrySet().iterator();
				while (supptaxIter.hasNext()) {
					TotaltaxBrkUpArr.put(supptaxIter.next().getValue());
				}
				totaltaxes.put("totalTaxBreakUp", TotaltaxBrkUpArr);
				totalRoomSuppPriceInfo.put("totalTaxes", totaltaxes);


				//Calculate total roomPriceInfo
				JSONObject roompriceInfo=((JSONObject) roomConfig).getJSONObject(JSON_PROP_ROOMPRICE);
				BigDecimal[] roomTotals=getTotalTaxesV2(roompriceInfo,totalpriceInfo,roomPrctaxBrkUpTotalsMap,totalroomPriceAmt,totalroomTaxAmt);
				totalroomPriceAmt=roomTotals[0];
				totalroomTaxAmt=roomTotals[1];

				//JSONObject totalpriceInfo=new JSONObject();
				totalroomPriceAmt=totalroomPriceAmt.add(roompriceInfo.getBigDecimal(JSON_PROP_AMOUNT));
				totalpriceInfo.put("amount", totalroomPriceAmt) ;
				totalpriceInfo.put("currencyCode", roompriceInfo.getString("currencyCode"));
				JSONObject roomPrctotaltaxes=new JSONObject();
				JSONObject roomPrctaxes=roompriceInfo.getJSONObject("taxes");
				totalroomTaxAmt=totalroomTaxAmt.add(roomPrctaxes.getBigDecimal("amount"));
				roomPrctotaltaxes.put(JSON_PROP_AMOUNT,totalroomTaxAmt );
				roomPrctotaltaxes.put(JSON_PROP_CCYCODE, roomPrctaxes.getString("currencyCode"));
				JSONArray roomPrctaxBreakUpArr = roomPrctaxes.getJSONArray("taxBreakup");
				for(int t=0;t<roomPrctaxBreakUpArr.length();t++) {
					JSONObject taxBrkUpJson = roomPrctaxBreakUpArr.getJSONObject(t);
					String taxCode= taxBrkUpJson.getString("taxCode");
					JSONObject taxBrkUpTotalsJson = null;
					if (roomPrctaxBrkUpTotalsMap.containsKey(taxCode)) {
						taxBrkUpTotalsJson = roomPrctaxBrkUpTotalsMap.get(taxCode);
						taxBrkUpTotalsJson.put(JSON_PROP_AMOUNT, taxBrkUpTotalsJson.getBigDecimal(JSON_PROP_AMOUNT).add(taxBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT)));
					}
					else {
						taxBrkUpTotalsJson = new JSONObject();
						taxBrkUpTotalsJson.put(JSON_PROP_AMOUNT,taxBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT));
						taxBrkUpTotalsJson.put("taxCode", taxBrkUpJson.getString("taxCode"));
						taxBrkUpTotalsJson.put(JSON_PROP_CCYCODE,taxBrkUpJson.getString(JSON_PROP_CCYCODE));
						roomPrctaxBrkUpTotalsMap.put(taxCode, taxBrkUpTotalsJson);
					}

				}
				JSONArray roomPrcTotaltaxBrkUpArr = new JSONArray();
				Iterator<Entry<String, JSONObject>> roomPrctaxIter = supptaxBrkUpTotalsMap.entrySet().iterator();
				while (roomPrctaxIter.hasNext()) {
					roomPrcTotaltaxBrkUpArr.put(roomPrctaxIter.next().getValue());
				}
				roomPrctotaltaxes.put("totalTaxBreakUp", roomPrcTotaltaxBrkUpArr);
				totalpriceInfo.put("totalTaxes", roomPrctotaltaxes);
				 

				//ADD SUPPLIER COMMERCIALS
				JSONArray suppCommJsonArr=((JSONObject) roomConfig).optJSONArray(JSON_PROP_SUPPCOMM);

				// If no supplier commercials have been defined in BRMS, the JSONArray for suppCommJsonArr will be null.
				// In this case, log a message and proceed with other calculations.
				if (suppCommJsonArr == null) {
					logger.warn("No supplier commercials found");
				}
				else {
					for (int j=0; j < suppCommJsonArr.length(); j++) {
						JSONObject suppCommJson = suppCommJsonArr.getJSONObject(j);
						String suppCommName = suppCommJson.getString(JSON_PROP_COMMNAME);
						JSONObject suppCommTotalsJson = null;
						if (suppCommTotalsMap.containsKey(suppCommName)) {
							suppCommTotalsJson = suppCommTotalsMap.get(suppCommName);
							suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT)));
						}
						else {
							suppCommTotalsJson = new JSONObject();
							suppCommTotalsJson.put(JSON_PROP_COMMNAME, suppCommName);
							suppCommTotalsJson.put(JSON_PROP_COMMCCY, suppCommJson.optString(JSON_PROP_COMMCCY));
							suppCommTotalsJson.put(JSON_PROP_COMMTYPE, suppCommJson.getString(JSON_PROP_COMMTYPE));
							suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
							suppCommTotalsMap.put(suppCommName, suppCommTotalsJson);
						}
					}

				}
				JSONArray suppCommTotalsJsonArr = new JSONArray();
				Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet().iterator();
				while (suppCommTotalsIter.hasNext()) {
					suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
				}
				JSONObject reqHeader = kafkamsgjson.getJSONObject(JSON_PROP_REQHEADER);
				UserContextV2 usrCtx = UserContextV2.getUserContextV2ForSession(reqHeader);
				List<ClientInfo> clientCommHierarchyList = usrCtx.getClientHierarchy();

				//ADDING CLIENTCOMMTOTAL
				JSONArray clientEntityTotalCommArr=new JSONArray();
				JSONArray clientCommJsonArr=((JSONObject) roomConfig).optJSONArray(JSON_PROP_CLIENTCOMM);
				if (clientCommJsonArr == null) {
					logger.warn("No supplier commercials found");
				}
				else {
					//for each entity in clientcomm add a new object in clientEntityTotalCommercials Array
					for (int j=0; j < clientCommJsonArr.length(); j++) {
						JSONObject clientCommJson = clientCommJsonArr.getJSONObject(j);
						JSONArray additionalCommsJsonArr = clientCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
						String clientID="";
						String ParentClienttId="";
						String CommercialEntityId="";
						CommercialsEntityType commEntityType=null;
						JSONObject clientEntityTotalJson=new JSONObject();
						if (clientCommHierarchyList != null && clientCommHierarchyList.size() > 0) {
							ClientInfo clInfo = clientCommHierarchyList.get(clientCommHierarchyList.size() - 1);
							if (clInfo.getCommercialsEntityId()==clientCommJson.get(JSON_PROP_ENTITYNAME)) {
								clientID = clInfo.getClientId();
								ParentClienttId=clInfo.getParentClienttId();
								CommercialEntityId=clInfo.getCommercialsEntityId();
								commEntityType=clInfo.getCommercialsEntityType();
							}
						}
						clientEntityTotalJson.put(JSON_PROP_CLIENTID, clientID);
						clientEntityTotalJson.put(JSON_PROP_PARENTCLIENTID, ParentClienttId);
						clientEntityTotalJson.put(JSON_PROP_COMMENTITYTYPE, commEntityType);
						clientEntityTotalJson.put(JSON_PROP_COMMENTITYID,CommercialEntityId);

						//Add the commercial types inside the additionalCommdetails array into clientCommTotalsMap and calculate total of each entity
						JSONObject clientCommTotalsJson = null;
						if((additionalCommsJsonArr!=null)&& (!(additionalCommsJsonArr.length()<0))){
							for(int p=0;p<additionalCommsJsonArr.length();p++) {
								JSONObject additionalCommJson=additionalCommsJsonArr.getJSONObject(p);
								String addcommName=additionalCommJson.getString(JSON_PROP_COMMNAME);

								if (clientCommTotalsMap.containsKey(addcommName.concat(clientCommJson.getString(JSON_PROP_ENTITYNAME)))) {
									clientCommTotalsJson = clientCommTotalsMap.get(addcommName.concat(clientCommJson.getString(JSON_PROP_ENTITYNAME)));
									clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, additionalCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(additionalCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT)));
								}
								else {
									clientCommTotalsJson = new JSONObject();
									clientCommTotalsJson.put(JSON_PROP_COMMTYPE, additionalCommJson.getString(JSON_PROP_COMMNAME));
									clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, additionalCommJson.get(JSON_PROP_COMMAMOUNT).toString());
									clientCommTotalsJson.put(JSON_PROP_COMMNAME,additionalCommJson.get(JSON_PROP_COMMNAME));
									clientCommTotalsJson.put(JSON_PROP_COMMCCY,additionalCommJson.get(JSON_PROP_COMMCCY).toString());
									clientCommTotalsMap.put(addcommName.concat(clientCommJson.getString(JSON_PROP_ENTITYNAME)), clientCommTotalsJson);
								}
							}
						}

						//Add the commercial type inside the markUpCommercialDetails obj into clientCommTotalsMap and calculate total of each entity
						JSONObject markupJson = clientCommJson.getJSONObject(JSON_PROP_MARKUPCOMDTLS);
						String clientCommName = markupJson.getString(JSON_PROP_COMMNAME);

						if (clientCommTotalsMap.containsKey(clientCommName.concat(clientCommJson.getString(JSON_PROP_ENTITYNAME)))) {
							clientCommTotalsJson = clientCommTotalsMap.get(clientCommName.concat(clientCommJson.getString(JSON_PROP_ENTITYNAME)));
							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, clientCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(markupJson.getBigDecimal(JSON_PROP_COMMAMOUNT)));
						}
						else {
							clientCommTotalsJson = new JSONObject();
							clientCommTotalsJson.put(JSON_PROP_COMMNAME, clientCommName);
							clientCommTotalsJson.put(JSON_PROP_COMMTYPE, clientCommJson.getString(JSON_PROP_COMMTYPE));
							clientCommTotalsJson.put(JSON_PROP_COMMCCY, markupJson.optString(JSON_PROP_COMMCCY));
							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, markupJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
							clientCommTotalsMap.put(clientCommName.concat(clientCommJson.getString("entityName")), clientCommTotalsJson);
						}

						//
						JSONArray clientCommTotalsJsonArr = new JSONArray();
						Iterator<Entry<String, JSONObject>> clientCommTotalsIter = clientCommTotalsMap.entrySet().iterator();
						while (clientCommTotalsIter.hasNext()) {
							clientCommTotalsJsonArr.put(clientCommTotalsIter.next().getValue());
						}
						clientEntityTotalJson.put(JSON_PROP_CLIENTCOMMTOTAL, clientCommTotalsJsonArr);
						clientEntityTotalCommArr.put(clientEntityTotalJson);
					}

				}

				//final total calculation at order level
				roomObjectJson.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr);
				roomObjectJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientEntityTotalCommArr);
				roomObjectJson.put(JSON_PROP_SUPPBOOKPRCINFO, totalRoomSuppPriceInfo);
				roomObjectJson.put(JSON_PROP_BOOKPRCINFO, totalpriceInfo);

			}
		}
	}*/

	static void getTotalDiscounts(JSONObject roomdiscountsObj, JSONObject totalPriceInfoObj, JSONObject discountsTotalObj,	Map<String, JSONObject> discountTotalsMap) {
		totalPriceInfoObj.put("discounts", discountsTotalObj) ;
		discountsTotalObj.put(JSON_PROP_AMOUNT, discountsTotalObj.optBigDecimal(JSON_PROP_AMOUNT, new BigDecimal(0)).add(roomdiscountsObj.getBigDecimal(JSON_PROP_AMOUNT))) ;
		//discountsTotalObj.put(JSON_PROP_CCYCODE, roomdiscountsObj.getString(JSON_PROP_CCYCODE));
		JSONArray discountArr = roomdiscountsObj.optJSONArray("discount");
		if(discountArr!=null) {
			for(int t=0;t<discountArr.length();t++) {
				JSONObject discountBrkUpJson = discountArr.getJSONObject(t);
				String discountType= discountBrkUpJson.getString("discountType");
				JSONObject discountBrkUpTotalsJson = null;
				if (discountTotalsMap.containsKey(discountType)) {
					discountBrkUpTotalsJson = discountTotalsMap.get(discountType);
					discountBrkUpTotalsJson.put(JSON_PROP_AMOUNT, discountBrkUpTotalsJson.getBigDecimal(JSON_PROP_AMOUNT).add(discountBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT)));
				}
				else {
					discountBrkUpTotalsJson = new JSONObject();
					discountBrkUpTotalsJson.put(JSON_PROP_AMOUNT,discountBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT));
					discountBrkUpTotalsJson.put("discountType", discountBrkUpJson.getString("discountType"));
					discountBrkUpTotalsJson.put(JSON_PROP_CCYCODE,discountBrkUpJson.getString(JSON_PROP_CCYCODE));
					discountTotalsMap.put(discountType, discountBrkUpTotalsJson);
				}

			}
			
			JSONArray discountTotalArr = new JSONArray();
			Iterator<Entry<String, JSONObject>> discountIter = discountTotalsMap.entrySet().iterator();
			while (discountIter.hasNext()) {
				discountTotalArr.put(discountIter.next().getValue());
			}
			discountsTotalObj.put("discount", discountTotalArr);
			}
		
		
	}
	
	static void getTotalRecvblesIncentives(JSONObject obj, JSONObject totalPriceInfoObj, JSONObject totalObj,Map<String, JSONObject> totalsMap,String component) {
		int indx = component.indexOf(".");
		totalPriceInfoObj.put(component.substring(0, indx), totalObj) ;
		totalObj.put(JSON_PROP_AMOUNT, totalObj.optBigDecimal(JSON_PROP_AMOUNT, new BigDecimal(0)).add(obj.getBigDecimal(JSON_PROP_AMOUNT))) ;
		totalObj.put(JSON_PROP_CCYCODE, obj.getString(JSON_PROP_CCYCODE));
		JSONArray recvbleIncntiveArr = obj.optJSONArray(component.substring(indx+1));
		if(recvbleIncntiveArr!=null) {
			for(int t=0;t<recvbleIncntiveArr.length();t++) {
				JSONObject recvbleIncntiveBrkUpJson = recvbleIncntiveArr.getJSONObject(t);
				String code= recvbleIncntiveBrkUpJson.getString("code");
				JSONObject recvbleIncntiveBrkUpTotalsJson = null;
				if (totalsMap.containsKey(code)) {
					recvbleIncntiveBrkUpTotalsJson = totalsMap.get(code);
					recvbleIncntiveBrkUpTotalsJson.put(JSON_PROP_AMOUNT, recvbleIncntiveBrkUpTotalsJson.getBigDecimal(JSON_PROP_AMOUNT).add(recvbleIncntiveBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT)));
				}
				else {
					recvbleIncntiveBrkUpTotalsJson = new JSONObject();
					recvbleIncntiveBrkUpTotalsJson.put(JSON_PROP_AMOUNT,recvbleIncntiveBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT));
					recvbleIncntiveBrkUpTotalsJson.put("code", recvbleIncntiveBrkUpJson.getString("code"));
					recvbleIncntiveBrkUpTotalsJson.put(JSON_PROP_CCYCODE,recvbleIncntiveBrkUpJson.getString(JSON_PROP_CCYCODE));
					totalsMap.put(code, recvbleIncntiveBrkUpTotalsJson);
				}

			}
			
			JSONArray recvbleIncentiveTotalArr = new JSONArray();
			Iterator<Entry<String, JSONObject>> recvbleIncntiveIter = totalsMap.entrySet().iterator();
			while (recvbleIncntiveIter.hasNext()) {
				recvbleIncentiveTotalArr.put(recvbleIncntiveIter.next().getValue());
			}
			totalObj.put(component.substring(indx+1), recvbleIncentiveTotalArr);
			}
		
		
	}

	static  BigDecimal[] getTotalTaxesV2(JSONObject roomSuppInfo, JSONObject totalPriceInfo, Map<String, JSONObject> taxBrkUpTotalsMap, BigDecimal totalInfoAmt, BigDecimal totalTaxAmt) {
		totalInfoAmt=totalInfoAmt.add(roomSuppInfo.getBigDecimal(JSON_PROP_AMOUNT));
		totalPriceInfo.put(JSON_PROP_AMOUNT, totalInfoAmt) ;
		totalPriceInfo.put(JSON_PROP_CCYCODE, roomSuppInfo.getString(JSON_PROP_CCYCODE));
		JSONObject totaltaxes=new JSONObject();
		JSONObject taxes=roomSuppInfo.getJSONObject(JSON_PROP_TOTALTAX);
		if(!(taxes.length()==0)) {
			totalTaxAmt=totalTaxAmt.add(taxes.optBigDecimal(JSON_PROP_AMOUNT,new BigDecimal(0)));
			totaltaxes.put(JSON_PROP_AMOUNT,totalTaxAmt );
			totaltaxes.put(JSON_PROP_CCYCODE, taxes.optString(JSON_PROP_CCYCODE));
			JSONArray taxBreakUpArr = taxes.optJSONArray(JSON_PROP_TAXBRKPARR);
			if(taxBreakUpArr!=null) {
			for(int t=0;t<taxBreakUpArr.length();t++) {
				JSONObject taxBrkUpJson = taxBreakUpArr.getJSONObject(t);
				String taxCode= taxBrkUpJson.getString(JSON_PROP_TAXCODE);
				JSONObject taxBrkUpTotalsJson = null;
				if (taxBrkUpTotalsMap.containsKey(taxCode)) {
					taxBrkUpTotalsJson = taxBrkUpTotalsMap.get(taxCode);
					taxBrkUpTotalsJson.put(JSON_PROP_AMOUNT, taxBrkUpTotalsJson.getBigDecimal(JSON_PROP_AMOUNT).add(taxBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT)));
				}
				else {
					taxBrkUpTotalsJson = new JSONObject();
					taxBrkUpTotalsJson.put(JSON_PROP_AMOUNT,taxBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT));
					taxBrkUpTotalsJson.put(JSON_PROP_TAXCODE, taxBrkUpJson.getString(JSON_PROP_TAXCODE));
					taxBrkUpTotalsJson.put(JSON_PROP_CCYCODE,taxBrkUpJson.getString(JSON_PROP_CCYCODE));
					taxBrkUpTotalsMap.put(taxCode, taxBrkUpTotalsJson);
				}

			}
			
			JSONArray TotaltaxBrkUpArr = new JSONArray();
			Iterator<Entry<String, JSONObject>> taxIter = taxBrkUpTotalsMap.entrySet().iterator();
			while (taxIter.hasNext()) {
				TotaltaxBrkUpArr.put(taxIter.next().getValue());
			}
			totaltaxes.put(JSON_PROP_TAXBRKPARR, TotaltaxBrkUpArr);
			}	
		}
		totalPriceInfo.put(JSON_PROP_TOTALTAX, totaltaxes);

		return new BigDecimal[] {totalInfoAmt, totalTaxAmt};
	}

	/*private JSONObject getSupplierResponseJSON(JSONObject reqJson, Element resElem) {
		JSONArray accomodationInfoArr = new JSONArray();
		JSONObject accomodationInfoObj,roomStayJson,uniqueIdObj;
		JSONArray roomStayJsonArr=new JSONArray();
		JSONArray uniqueIdArray=new JSONArray();

		for(Element wrapperElem:XMLUtils.getElementsAtXPath(resElem, "./accoi:ResponseBody/acco:OTA_HotelResRSWrapper")) {
			accomodationInfoObj=new JSONObject();
			accomodationInfoObj.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(wrapperElem, "./acco:SupplierID"));
			for(Element uniqueId : XMLUtils.getElementsAtXPath(wrapperElem, "./ota:OTA_HotelResRS/ota:HotelReservations/ota:HotelReservation/ota:UniqueID"))
			{
				uniqueIdObj=new JSONObject();
				uniqueIdObj.put(JSON_PROP_BOOKREFID, uniqueId.getAttribute("ID"));
				uniqueIdObj.put(JSON_PROP_BOOKREFTYPE, uniqueId.getAttribute("Type"));
				uniqueIdArray.put(uniqueIdObj);
			}
			accomodationInfoObj.put(JSON_PROP_UNIQUEID, uniqueIdArray);
			for (Element roomStayElem : XMLUtils.getElementsAtXPath(wrapperElem, "./ota:OTA_HotelResRS/ota:HotelReservations/ota:HotelReservation/ota:RoomStays/ota:RoomStay")) {
				roomStayJson = AccoSearchProcessor.getRoomStayJSON(roomStayElem);
				roomStayJson.remove(JSON_PROP_ROOMPRICE);
				roomStayJson.remove(JSON_PROP_NIGHTLYPRICEARR);
				roomStayJson.remove(JSON_PROP_OCCUPANCYARR);
				roomStayJson.getJSONObject(JSON_PROP_ROOMINFO).remove(JSON_PROP_AVAILSTATUS);
				roomStayJsonArr.put(roomStayJson);


			}
			accomodationInfoObj.put(JSON_PROP_ROOMSTAYARR,roomStayJsonArr);
			accomodationInfoArr.put(accomodationInfoObj);
		}
		JSONObject resJson = new JSONObject();
		JSONObject resBodyJson = new JSONObject();
		resBodyJson.put(JSON_PROP_ACCOMODATIONARR, accomodationInfoArr);
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		return resJson;
	}*/

	static void getTotalCompanyTaxes(JSONObject roomCompanytTaxObj, JSONObject totalPriceInfoObj, JSONObject companyTaxesTotalObj, Map<String, JSONObject> companyTaxTotalsMap) {
		
		totalPriceInfoObj.put("companyTaxes", companyTaxesTotalObj) ;
		companyTaxesTotalObj.put(JSON_PROP_AMOUNT, companyTaxesTotalObj.optBigDecimal(JSON_PROP_AMOUNT, new BigDecimal(0)).add(roomCompanytTaxObj.getBigDecimal(JSON_PROP_AMOUNT))) ;
		companyTaxesTotalObj.put(JSON_PROP_CCYCODE, roomCompanytTaxObj.getString(JSON_PROP_CCYCODE));
		JSONArray companyTaxBreakUpArr = roomCompanytTaxObj.optJSONArray("companyTax");
		if(companyTaxBreakUpArr!=null) {
			for(int t=0;t<companyTaxBreakUpArr.length();t++) {
				JSONObject taxBrkUpJson = companyTaxBreakUpArr.getJSONObject(t);
				String taxCode= taxBrkUpJson.getString(JSON_PROP_TAXCODE);
				JSONObject taxBrkUpTotalsJson = null;
				if (companyTaxTotalsMap.containsKey(taxCode)) {
					taxBrkUpTotalsJson = companyTaxTotalsMap.get(taxCode);
					taxBrkUpTotalsJson.put(JSON_PROP_AMOUNT, taxBrkUpTotalsJson.getBigDecimal(JSON_PROP_AMOUNT).add(taxBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT)));
				}
				else {
					taxBrkUpTotalsJson = new JSONObject();
					taxBrkUpTotalsJson.put(JSON_PROP_AMOUNT,taxBrkUpJson.getBigDecimal(JSON_PROP_AMOUNT));
					taxBrkUpTotalsJson.put(JSON_PROP_TAXCODE, taxBrkUpJson.getString(JSON_PROP_TAXCODE));
					taxBrkUpTotalsJson.put(JSON_PROP_CCYCODE,taxBrkUpJson.getString(JSON_PROP_CCYCODE));
					taxBrkUpTotalsJson.put("taxPercent",taxBrkUpJson.getBigDecimal("taxPercent"));
					taxBrkUpTotalsJson.put("hsnCode",taxBrkUpJson.getString("hsnCode"));
					taxBrkUpTotalsJson.put("sacCode",taxBrkUpJson.getString("sacCode"));
					companyTaxTotalsMap.put(taxCode, taxBrkUpTotalsJson);
				}

			}
			
			JSONArray companyTotaltaxBrkUpArr = new JSONArray();
			Iterator<Entry<String, JSONObject>> taxIter = companyTaxTotalsMap.entrySet().iterator();
			while (taxIter.hasNext()) {
				companyTotaltaxBrkUpArr.put(taxIter.next().getValue());
			}
			companyTaxesTotalObj.put("companyTax", companyTotaltaxBrkUpArr);
			}
		
	}

	private static JSONObject getSupplierResponseJSON(JSONObject reqJson, Element resElem) {

		JSONObject resBodyJson = new JSONObject();
		JSONArray multiResArr = new JSONArray();
		resBodyJson.put(JSON_PROP_ACCOMODATIONARR, multiResArr);
		int sequence = 0;String sequence_str="";
		JSONObject accoInfoJson,roomJson;
		JSONArray roomRefJsonArr;

		for(Element wrapperElem:XMLUtils.getElementsAtXPath(resElem, "./accoi:ResponseBody/acco:OTA_HotelResRSWrapper")) {
			accoInfoJson = new JSONObject();
           validateSuppRes(wrapperElem, accoInfoJson);
            /*if(accoInfoJson.has("errorMessage")) {
				break;
			}*/
			accoInfoJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(wrapperElem, "./acco:SupplierID"));
			
			accoInfoJson.put("supplierReservationId", XMLUtils.getValueAtXPath(wrapperElem,"./ota:OTA_HotelResRS/ota:HotelReservations/ota:HotelReservation/ota:UniqueID[@Type='14']/@ID").toString());
			accoInfoJson.put("supplierReferenceId", XMLUtils.getValueAtXPath(wrapperElem,"./ota:OTA_HotelResRS/ota:HotelReservations/ota:HotelReservation/ota:UniqueID[@Type='16']/@ID").toString());
			accoInfoJson.put("clientReferenceId", XMLUtils.getValueAtXPath(wrapperElem,"./ota:OTA_HotelResRS/ota:HotelReservations/ota:HotelReservation/ota:UniqueID[@Type='38']/@ID").toString());
			accoInfoJson.put("supplierCancellationId", XMLUtils.getValueAtXPath(wrapperElem,"./ota:OTA_HotelResRS/ota:HotelReservations/ota:HotelReservation/ota:UniqueID[@Type='15']/@ID").toString());
			String orderStatus = XMLUtils.getValueAtXPath(wrapperElem,"./ota:OTA_HotelResRS/ota:HotelReservations/ota:HotelReservation/@ResStatus");
			if(Utils.isStringNotNullAndNotEmpty(orderStatus)) {
				accoInfoJson.put("status",Status.getValue(Status.valueOf(orderStatus)));
				//This is done in order to differentiate between NOTCONF,RESERVED and FAILED at db side as we send status as On Request for all 3 
				if("NOTCONF".equalsIgnoreCase(orderStatus) || "NOTCON".equalsIgnoreCase(orderStatus)) {
					accoInfoJson.put("errorMessage", "Booking was not Confirmed from the supplier");
				    accoInfoJson.put("errorCode", "SIERR01");
				}				
			}
			  	
			/*if(accoInfoJson.getString("supplierReservationId").isEmpty()) {
				accoInfoJson.put("status", "FAILED");
			}*/
			
			//In case of Failed response we send On Request status to WEM
			if(accoInfoJson.getString("supplierReservationId").isEmpty()) {
				accoInfoJson.put("status", "On Request");
			}
			
			roomRefJsonArr = new JSONArray();
			for (Element roomStayElem : XMLUtils.getElementsAtXPath(wrapperElem, "./ota:OTA_HotelResRS/ota:HotelReservations/ota:HotelReservation/ota:RoomStays/ota:RoomStay")) {
				roomJson = new JSONObject();
				String roomIdx_str=XMLUtils.getValueAtXPath(roomStayElem, "./@RPH");
				int roomIdx = roomIdx_str.isEmpty()?-1:Integer.valueOf(roomIdx_str);
				roomJson.put(JSON_PROP_ROOMINDEX,roomIdx);
				roomJson.put(JSON_PROP_SUPPROOMINDEX, XMLUtils.getValueAtXPath(roomStayElem, "./@IndexNumber"));
				String roomStatus = XMLUtils.getValueAtXPath(roomStayElem,"./@RoomStayStatus");
				if(Utils.isStringNotNullAndNotEmpty(roomStatus)) {
					roomJson.put("roomStatus", Status.getValue(Status.valueOf(XMLUtils.getValueAtXPath(roomStayElem,"./@RoomStayStatus"))));
					//This is done in order to differentiate between NOTCONF,RESERVED and FAILED at db side as we send status as On Request for all 3 
					if("NOTCONF".equalsIgnoreCase(roomStatus) || "NOTCON".equalsIgnoreCase(roomStatus)) {
						accoInfoJson.put("errorMessage", "Booking was not Confirmed from the supplier");
					    accoInfoJson.put("errorCode", "SIERR01");
					}	
				}
								
			    if(roomStatus.isEmpty() && !orderStatus.isEmpty()) {
			    	roomJson.put("roomStatus", accoInfoJson.getString("status"));
			    }
				roomRefJsonArr.put(roomJson);
			}
			
			accoInfoJson.put(JSON_PROP_SUPPROOMREFERENCES, roomRefJsonArr);

         /*   Boolean reservedFlag = false;
			if(accoInfoJson.getString("status").isEmpty() || "RoomLevel".equals(accoInfoJson.getString("status"))) {
				JSONArray suppRoomRefArr = accoInfoJson.getJSONArray(JSON_PROP_SUPPROOMREFERENCES);
				for(int i=0;i<suppRoomRefArr.length();i++) {
					JSONObject suppRoomObj = suppRoomRefArr.getJSONObject(i);	
					String roomStatus = Status.getValue(Status.valueOf(suppRoomObj.getString("roomStatus")));
					if("NOTCONF".equals(roomStatus)) {
						accoInfoJson.put("status", roomStatus);
						break;
					}
					else if("RESERVED".equals(roomStatus)){
						accoInfoJson.put("status", roomStatus);
						reservedFlag=true;
					}
					else {
						if(reservedFlag != true)
						{
							accoInfoJson.put("status", roomStatus);
						}
                     }
				}
			}*/
			if(orderStatus.isEmpty() || "RoomLevel".equals(accoInfoJson.getString("status"))) {
				JSONArray suppRoomRefArr = accoInfoJson.getJSONArray(JSON_PROP_SUPPROOMREFERENCES);
				for(int i=0;i<suppRoomRefArr.length();i++) {
					JSONObject suppRoomObj = suppRoomRefArr.getJSONObject(i);	
					String roomStatus = suppRoomObj.getString("roomStatus");
					if("On Request".equalsIgnoreCase(roomStatus)) {
						accoInfoJson.put("status", roomStatus);
						break;
					}
					else {
						accoInfoJson.put("status", roomStatus);
				    	}
                   }
			}
			//Shifted todo to MRSplitterCombiner
			/*if("RESERVED".equals(accoInfoJson.getString("status")))
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.ON_REQUEST, "", reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID),reqJson.getJSONObject(JSON_PROP_REQHEADER));
			else
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.BOOKING, "", reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID),reqJson.getJSONObject(JSON_PROP_REQHEADER));*/

			sequence_str = XMLUtils.getValueAtXPath(wrapperElem, "./acco:Sequence");
			sequence = sequence_str.isEmpty()?sequence:Integer.parseInt(sequence_str);
			multiResArr.put(sequence++, accoInfoJson);
		}
		
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		return resJson;
	}

	private static void validateSuppRes(Element wrapperElem, JSONObject accoInfoJson) {
			
			for (Element errorElem : XMLUtils.getElementsAtXPath(wrapperElem, "./ota:OTA_HotelResRS/ota:Errors/ota:Error")) {
				accoInfoJson.put("errorMessage", XMLUtils.getValueAtXPath(errorElem, "./@ShortText"));
				accoInfoJson.put("errorCode", XMLUtils.getValueAtXPath(errorElem, "./@Code"));
			}
			for (Element errorElem1 : XMLUtils.getElementsAtXPath(wrapperElem, "./acco:ErrorList/com:Error")) {
				accoInfoJson.put("errorMessage", XMLUtils.getValueAtXPath(errorElem1, "./com:ErrorMsg"));
				accoInfoJson.put("errorCode", XMLUtils.getValueAtXPath(errorElem1, "./com:ErrorCode"));
			}
		}
		
	

}
