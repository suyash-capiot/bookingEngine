package com.coxandkings.travel.bookingengine.orchestrator.acco;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.orchestrator.acco.enums.AvailabilityStatus;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

//import com.coxandkings.mapper.Xml2Json;


public class AccoSearchProcessor implements AccoConstants {

	private static final Logger logger = LogManager.getLogger(AccoSearchProcessor.class);
	static final String OPERATION_NAME = "search";

	//***********************************SI JSON TO XML FOR REQUEST BODY STARTS HERE**************************************//
	public static void setSupplierRequestElem(JSONObject reqJson,Element reqElem) throws Exception {

		Document ownerDoc = reqElem.getOwnerDocument();
		JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null) ? reqJson.optJSONObject(JSON_PROP_REQHEADER) : new JSONObject();
		JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null) ? reqJson.optJSONObject(JSON_PROP_REQBODY) : new JSONObject();

		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);

		 
		JSONObject clientContext=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientMrkt = clientContext.getString(JSON_PROP_CLIENTMARKET);
		String clientID = clientContext.getString(JSON_PROP_CLIENTID);
		
		createSuppReqHdrElem(XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader"),sessionID,transactionID,userID,clientMrkt,clientID);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		JSONArray prodCategSubtypeJsonArr = reqBodyJson.getJSONArray(JSON_PROP_ACCOSUBTYPEARR);
		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader/com:SupplierCredentialsList");
		for(Object prodCategSubtype: prodCategSubtypeJsonArr) {
			//AccoSubType tempSubtype = AccoSubType.forString((String) prodCategSubtype);
			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_ACCO,(String) prodCategSubtype);
			if (prodSuppliers == null) {
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for Accommodation subtype ".concat((String) prodCategSubtype));
				logger.warn("Product supplier not found for user/client for subtype ".concat((String) prodCategSubtype));
				continue;
			}
			for (ProductSupplier prodSupplier : prodSuppliers) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}
		}
		
		setSuppReqOTAElem(ownerDoc, XMLUtils.getFirstElementAtXPath(reqElem,"./accoi:RequestBody/ota:OTA_HotelAvailRQ"), reqBodyJson,reqHdrJson);

	}

	public static void createSuppReqHdrElem(Element reqHdrElem, String sessionId, String transactId, String userId,String clientMrkt,String clientID) {

		XMLUtils.setValueAtXPath(reqHdrElem, "./com:SessionID",sessionId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:TransactionID",transactId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:UserID",userId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:ClientID",clientID);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:MarketID",clientMrkt);

	}

	public static void setSuppReqOTAElem(Document ownerDoc,Element reqOTAElem,JSONObject reqParamJson,JSONObject reqHdrJSON) {

		Element baseElem = XMLUtils.getFirstElementAtXPath(reqOTAElem, "./ota:AvailRequestSegments/ota:AvailRequestSegment/ota:HotelSearchCriteria/ota:Criterion");
		JSONArray roomConfigArr = reqParamJson.getJSONArray(JSON_PROP_REQUESTEDROOMARR);
		JSONObject roomJson;

		XMLUtils.setValueAtXPath(baseElem,"./ota:RefPoint/@CountryCode",reqParamJson.getString(JSON_PROP_COUNTRYCODE));
		XMLUtils.setValueAtXPath(baseElem,"./ota:HotelRef/@HotelCityCode",reqParamJson.getString(JSON_PROP_CITYCODE));
		XMLUtils.setValueAtXPath(baseElem,"./ota:HotelRef/@HotelCode",reqParamJson.getString(JSON_PROP_HOTELCODE));
		XMLUtils.setValueAtXPath(baseElem,"./ota:StayDateRange/@Start",reqParamJson.getString(JSON_PROP_CHKIN));
		XMLUtils.setValueAtXPath(baseElem,"./ota:StayDateRange/@End",reqParamJson.getString(JSON_PROP_CHKOUT));
		//As per discussion in a meeting this flag can be expected from BE consumer
		//This would be derived from the flag symbol chosen at the search page
		XMLUtils.setValueAtXPath(baseElem,"./ota:Profiles/ota:ProfileInfo/ota:Profile/ota:Customer/ota:CitizenCountryName/@Code",
				reqParamJson.getString(JSON_PROP_PAXNATIONALITY));

		for(int i=0;i<roomConfigArr.length();i++){
			roomJson = (JSONObject) roomConfigArr.get(i);
			XMLUtils.insertChildNode(baseElem,"./ota:RoomStayCandidates",getRoomStayCandidateElem(ownerDoc,(JSONObject) roomJson, i+1),false);
		}

	}

	public static Element getRoomStayCandidateElem(Document ownerDoc, JSONObject roomJson, int rph){

		Element roomElem = ownerDoc.createElementNS(NS_OTA, "ota:RoomStayCandidate");
		Element guestCntParent = ownerDoc.createElementNS(NS_OTA, "ota:GuestCounts");

		roomElem.setAttribute("RPH", Integer.toString(rph));
		XMLUtils.insertChildNode(guestCntParent, ".", getGuestCountElem(ownerDoc,roomJson.getInt(JSON_PROP_ADTCNT),Integer.MIN_VALUE,Pax_ADT), false);
		JSONArray childAges = roomJson.getJSONArray(JSON_PROP_CHDAGESARR);
		for(int i=0;i<childAges.length();i++){
			XMLUtils.insertChildNode(guestCntParent, ".", getGuestCountElem(ownerDoc,1,childAges.getInt(i),Pax_CHD), false);
		}
		XMLUtils.insertChildNode(roomElem, ".", guestCntParent, false);

		return roomElem;
	}

	public static Element getGuestCountElem(Document ownerDoc, int count,int age,String paxType){

		Element guestElem = ownerDoc.createElementNS(NS_OTA, "ota:GuestCount");
		Attr ageQualifyingCode =  ownerDoc.createAttribute("AgeQualifyingCode");

		if(Pax_ADT.equals(paxType)){
			ageQualifyingCode.setValue(Pax_ADT_ID);
		}
		if(Pax_CHD.equals(paxType)){
			ageQualifyingCode.setValue(Pax_CHD_ID);
		}
		Attr guestCnt =  ownerDoc.createAttribute("Count");
		guestCnt.setValue(String.valueOf(count));
		Attr guestAge = null;
		if(age>0){
			guestAge = ownerDoc.createAttribute("Age");
			guestAge.setValue(String.valueOf(age));
		}
		XMLUtils.insertChildNodes(guestElem, ".", new Node[]{ageQualifyingCode,guestCnt,guestAge} , false);

		return guestElem;
	}

	//***********************************SI JSON TO XML FOR REQUEST BODY ENDS HERE**************************************//

	//***********************************SI XML TO JSON FOR RESPONSE STARTS HERE**************************************//

	public static JSONObject getSupplierResponseJSON(JSONObject reqJson,Element resElem){

		JSONArray roomStayJsonArr = new JSONArray();
		JSONObject roomStayJson;

		for(Element wrapperElem:XMLUtils.getElementsAtXPath(resElem, "./accoi:ResponseBody/acco:OTA_HotelAvailRSWrapper")) {
			for (Element roomStayElem : XMLUtils.getElementsAtXPath(wrapperElem, "./ota:OTA_HotelAvailRS/ota:RoomStays/ota:RoomStay")) {
				roomStayJson = getRoomStayJSON(roomStayElem);
				roomStayJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(wrapperElem, "./acco:SupplierID"));
				//This is done assuming that accosubtype will b polpulated during async search
				roomStayJson.put(JSON_PROP_ACCOSUBTYPE, reqJson.getJSONObject(JSON_PROP_REQBODY).optString(JSON_PROP_ACCOSUBTYPE,""));
				if(filterRoomStay(roomStayJson)) {
					logger.warn("RoomStay has been filtered for key %s",AccoRepriceProcessor.getRedisKeyForRoomStay(roomStayJson.getJSONObject(JSON_PROP_ROOMINFO)));
					continue;
				}
				roomStayJsonArr.put(roomStayJson);
			}
		}
		JSONObject resBodyJson = (new JSONObject()).append(JSON_PROP_ACCOMODATIONARR, (new JSONObject()).put(JSON_PROP_ROOMSTAYARR, roomStayJsonArr));
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);

		return resJson;

	}
	
	static boolean filterRoomStay(JSONObject roomStayJson) {
		String hotelCode = roomStayJson.getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_HOTELINFO).optString(JSON_PROP_HOTELCODE);
		//This has been agreed with SI and client
		if(Utils.isStringNullOrEmpty(hotelCode) || hotelCode.startsWith("0_"))
			return true;
		return false;
	}

	@Deprecated
	public static void getSupplierResponseJSON(Element wrapperElem,JSONArray roomStayJsonArr){

		JSONObject roomStayJson;

		for (Element roomStayElem : XMLUtils.getElementsAtXPath(wrapperElem, "./ota:OTA_HotelAvailRS/ota:RoomStays/ota:RoomStay")) {
			roomStayJson = getRoomStayJSON(roomStayElem);
			roomStayJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(wrapperElem, "./acco:SupplierID"));
			roomStayJsonArr.put(roomStayJson);
		}

	}

	public static JSONObject getRoomStayJSON(Element roomStayElem) {

		JSONObject roomStayJson = new JSONObject();

		roomStayJson.put(JSON_PROP_ROOMINFO, getRoomInfoJson(roomStayElem));
		roomStayJson.put(JSON_PROP_ROOMPRICE, getTotalPriceJson(XMLUtils.getFirstElementAtXPath(roomStayElem, "./ota:Total")));
		Element TPAExtHotlFee = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ota:Total/ota:TPA_Extensions/acco:HotelFees");
		if(TPAExtHotlFee!=null) {
			roomStayJson.put("feeInfo", getHotelFeeJson(TPAExtHotlFee));
		}
		Element TPAExtRoomRate = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ota:TPA_Extensions/acco:RoomRateAdditionalDetails/acco:RoomRateAdditionalDetail");
		if(TPAExtRoomRate!=null) {
			roomStayJson.put("supplierOffers", getSupplierOfferJson(TPAExtRoomRate));
		}
		JSONArray nightRateArr = new JSONArray();
		roomStayJson.put(JSON_PROP_NIGHTLYPRICEARR,nightRateArr);
		for(Element nightRateElem:XMLUtils.getElementsAtXPath(roomStayElem, "./ota:RoomRates/ota:RoomRate/ota:Rates/ota:Rate")){
			nightRateArr.put(getNightPriceJson(nightRateElem));
		}
		roomStayJson.put(JSON_PROP_OCCUPANCYARR, getOccupancyJson(roomStayElem));
		String prepayInd = XMLUtils.getValueAtXPath(roomStayElem, "./ota:RatePlans/ota:RatePlan/@PrepaidIndicator");
		roomStayJson.put(JSON_PROP_PAYATHOTEL,prepayInd.isEmpty()?false:!Boolean.valueOf(prepayInd));
		String availRooms = XMLUtils.getValueAtXPath(roomStayElem, "./ota:RoomTypes/ota:RoomType/@NumberOfUnits");
		if(Utils.isStringNotNullAndNotEmpty(availRooms))
			roomStayJson.put("availableUnits",Integer.valueOf(availRooms));
        String absDeadline= XMLUtils.getValueAtXPath(roomStayElem, "./ota:CancelPenalties/ota:CancelPenalty/ota:Deadline/@AbsoluteDeadline");
        if(Utils.isStringNotNullAndNotEmpty(absDeadline))
        	roomStayJson.put("freeCancellationTill",absDeadline);
       
		return roomStayJson;

	}

	private static JSONArray getSupplierOfferJson(Element tPAExtHotlFee) {
		JSONArray supplierOffers=new JSONArray();
		JSONObject supplierOffersObj;
		for(Element offerElem:XMLUtils.getElementsAtXPath(tPAExtHotlFee, "./acco:offers/acco:offer")) {
			supplierOffersObj=new JSONObject();
			supplierOffersObj.put("offerCode",offerElem.getAttribute("Code"));
			supplierOffersObj.put("offerName",offerElem.getAttribute("Name"));
			supplierOffersObj.put(JSON_PROP_AMOUNT,offerElem.getAttribute("Amount"));
			supplierOffersObj.put("description", offerElem.getAttribute("Description"));
			supplierOffers.put(supplierOffersObj);
		}
		return supplierOffers;
	}

	private static JSONArray getHotelFeeJson(Element TPAExtHotlFee) {
		JSONArray hoteFeeArr=new JSONArray();
		for(Element feeElem:XMLUtils.getElementsAtXPath(TPAExtHotlFee, "./acco:Fee")){
			JSONObject fee=new JSONObject();
			fee.put("condition", feeElem.getAttribute("Conditions"));
			fee.put("totalFee", feeElem.getAttribute("FeeTotal"));
			fee.put(JSON_PROP_CCYCODE, feeElem.getAttribute("CurrencyCode"));
			fee.put("feeMethod", feeElem.getAttribute("FeeMethod"));
			hoteFeeArr.put(fee);
		}
		return hoteFeeArr;
	}

	public static JSONObject getRoomInfoJson(Element roomStayElem) {

		JSONObject roomInfoJson = new JSONObject();
		Element roomTypeElem  = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ota:RoomTypes/ota:RoomType");
		Element ratePlanElem  = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ota:RatePlans/ota:RatePlan");

		//This flag is used to indicate which requested room in the array it belongs to.It should always come in SI
		String roomIdx_str=XMLUtils.getValueAtXPath(roomStayElem, "./@RPH");
		int roomIdx = roomIdx_str.isEmpty()?-1:Integer.valueOf(roomIdx_str);
		roomInfoJson.put(JSON_PROP_ROOMINDEX,roomIdx);
		roomInfoJson.put(JSON_PROP_ROOMTYPEINFO, getRoomTypeInfoJson(roomTypeElem));
		roomInfoJson.put(JSON_PROP_RATEPLANINFO, getRatePlanInfoJson(ratePlanElem));
		roomInfoJson.put(JSON_PROP_HOTELINFO, getHotelInfoJson(XMLUtils.getFirstElementAtXPath(roomStayElem, "./ota:BasicPropertyInfo")));
		//TODO:is meal info needed?Doesn't rate plan give the same info
		roomInfoJson.put(JSON_PROP_MEALINFO, getMealInfoJson(ratePlanElem));
		//TODO:add extra bed details
		JSONArray referenceArr = new JSONArray();
		roomInfoJson.put(JSON_PROP_REFERENCESARR,referenceArr);
		for(Element referenceElem:XMLUtils.getElementsAtXPath(roomStayElem, "./ota:Reference")){
			referenceArr.put(getReferencesInfoJson(referenceElem));
		}
		AvailabilityStatus availStatus= AvailabilityStatus.forString(XMLUtils.getValueAtXPath(roomStayElem, "./@AvailabilityStatus"));
		roomInfoJson.put(JSON_PROP_AVAILSTATUS, availStatus!=null?availStatus.toString():"");

		return roomInfoJson;

	}

	public static JSONObject getRoomTypeInfoJson(Element roomTypeElem) {

		JSONObject roomTypeJson = new JSONObject();

		roomTypeJson.put(JSON_PROP_ROOMTYPECODE, XMLUtils.getValueAtXPath(roomTypeElem, "./@RoomTypeCode"));
		roomTypeJson.put(JSON_PROP_ROOMTYPENAME, XMLUtils.getValueAtXPath(roomTypeElem, "./@RoomType"));
		roomTypeJson.put(JSON_PROP_ROOMCATEGCODE, XMLUtils.getValueAtXPath(roomTypeElem, "./ota:TPA_Extensions/acco:RoomCategoryID"));
		roomTypeJson.put(JSON_PROP_ROOMCATEGNAME, XMLUtils.getValueAtXPath(roomTypeElem, "./ota:TPA_Extensions/acco:RoomCategoryName"));
		roomTypeJson.put(JSON_PROP_ROOMREF, XMLUtils.getValueAtXPath(roomTypeElem, "./@RoomID"));

		return roomTypeJson;

	}

	public static JSONObject getRatePlanInfoJson(Element ratePlanElem) {

		JSONObject ratePlanJson = new JSONObject();

		ratePlanJson.put(JSON_PROP_RATEPLANCODE, XMLUtils.getValueAtXPath(ratePlanElem, "./@RatePlanCode"));
		ratePlanJson.put(JSON_PROP_RATEPLANNAME, XMLUtils.getValueAtXPath(ratePlanElem, "./@RatePlanName"));
		ratePlanJson.put(JSON_PROP_RATEPLANREF, XMLUtils.getValueAtXPath(ratePlanElem, "./@RatePlanID"));
		ratePlanJson.put(JSON_PROP_RATEBOOKINGREF, XMLUtils.getValueAtXPath(ratePlanElem, "./@BookingCode"));

		return ratePlanJson;

	}

	public static JSONObject getHotelInfoJson(Element hotelElem) {

		JSONObject hotelInfoJson = new JSONObject();

		hotelInfoJson.put(JSON_PROP_HOTELCODE, XMLUtils.getValueAtXPath(hotelElem, "./@HotelCode"));
		hotelInfoJson.put(JSON_PROP_HOTELNAME, XMLUtils.getValueAtXPath(hotelElem, "./@HotelName"));
		hotelInfoJson.put(JSON_PROP_HOTELREF, XMLUtils.getValueAtXPath(hotelElem, "./@HotelCodeContext"));

		return hotelInfoJson;

	}

	public static JSONObject getMealInfoJson(Element ratePlanElem) {

		JSONObject mealJson = new JSONObject();
		Element mealElem = XMLUtils.getFirstElementAtXPath(ratePlanElem, "./ota:TPA_Extensions/acco:Meals/acco:Meal");

		mealJson.put(JSON_PROP_MEALCODE,XMLUtils.getValueAtXPath(mealElem, "./@MealId"));
		mealJson.put(JSON_PROP_MEALNAME,XMLUtils.getValueAtXPath(mealElem, "./@Name"));

		return mealJson;

	}

	public static JSONObject getReferencesInfoJson(Element referenceElem) {

		JSONObject referenceJson = new JSONObject();

		referenceJson.put(JSON_PROP_REFNAME, XMLUtils.getValueAtXPath(referenceElem, "./@ID_Context"));
		referenceJson.put(JSON_PROP_REFVALUE, XMLUtils.getValueAtXPath(referenceElem, "./@ID"));
		referenceJson.put(JSON_PROP_REFCODE, XMLUtils.getValueAtXPath(referenceElem, "./@Type"));

		return referenceJson;

	}

	public static JSONObject getTotalPriceJson(Element totalPriceElem){

		JSONObject totalPriceJson = new JSONObject();

		totalPriceJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(totalPriceElem, "./@CurrencyCode"));
		totalPriceJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(totalPriceElem, "./@AmountAfterTax"), 0));
		totalPriceJson.put(JSON_PROP_TOTALTAX, getTotalTaxJson(totalPriceElem, true));

		return totalPriceJson;

	}

	public static JSONObject getNightPriceJson(Element nightPriceElem){

		JSONObject nightPriceJson = new JSONObject();

		nightPriceJson.put(JSON_PROP_EFFECTIVEDATE, XMLUtils.getValueAtXPath(nightPriceElem, "./@EffectiveDate"));
		nightPriceJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(nightPriceElem, "./ota:Base/@CurrencyCode"));
		nightPriceJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(nightPriceElem, "./ota:Base/@AmountAfterTax"),0));
		nightPriceJson.put(JSON_PROP_TOTALTAX, getTotalTaxJson(nightPriceElem, false));
		//TODO:check if tax breakup is present

		return nightPriceJson;

	}

	private static JSONArray getOccupancyJson(Element roomStayElem) {

		JSONArray occupancyArr=new JSONArray();
		JSONObject occupancy;

		for(Element occupancyElem:XMLUtils.getElementsAtXPath(roomStayElem, "./ota:RoomTypes/ota:RoomType/ota:Occupancy"))
		{
			occupancy=new JSONObject();
			occupancy.put(JSON_PROP_MAXOCCUPANCY, XMLUtils.getValueAtXPath(occupancyElem, "./@MaxOccupancy"));
			occupancy.put(JSON_PROP_MINOCCUPANCY, XMLUtils.getValueAtXPath(occupancyElem, "./@MinOccupancy"));
			occupancy.put(JSON_PROP_MAXAGE, XMLUtils.getValueAtXPath(occupancyElem, "./@MaxAge"));
			occupancy.put(JSON_PROP_MINAGE, XMLUtils.getValueAtXPath(occupancyElem, "./@MinAge"));
			String ageQC= XMLUtils.getValueAtXPath(occupancyElem, "./@AgeQualifyingCode");
			occupancy.put(JSON_PROP_PAXTYPE, Pax_ADT_ID.equals(ageQC)?Pax_ADT:(Pax_CHD_ID.equals(ageQC)?Pax_CHD:""));
			occupancyArr.put(occupancy);
		}

		return occupancyArr;

	}

	public static JSONObject getTotalTaxJson(Element priceElem, boolean includeBreakup) {

		JSONObject taxesJson = new JSONObject();

		Element totalTaxElem = XMLUtils.getFirstElementAtXPath(priceElem, "./ota:Taxes");
		Attr totalTaxAmt = (Attr) XMLUtils.getFirstNodeAtXPath(totalTaxElem, "./@Amount");
		Attr taxAmt;

		if(totalTaxAmt!=null && !(totalTaxAmt.getTextContent().isEmpty())) {

			taxesJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(totalTaxElem, "./@CurrencyCode"));
			taxesJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(totalTaxAmt.getTextContent(),0));

			if(includeBreakup) {
				JSONArray taxArr = new JSONArray();
				taxesJson.put(JSON_PROP_TAXBRKPARR, taxArr);
				for(Element taxElem:XMLUtils.getElementsAtXPath(totalTaxElem, "./ota:Tax")){
					taxAmt = (Attr) XMLUtils.getFirstNodeAtXPath(taxElem, "./@Amount");
					if(taxAmt!=null && !(taxAmt.getTextContent().isEmpty())) {
						taxArr.put(getTaxJson(taxElem));
					}
				}
			}

		}

		return taxesJson;

	}

	public static JSONObject getTaxJson(Element taxElem) {

		JSONObject taxJson = new JSONObject();

		taxJson.put(JSON_PROP_TAXCODE, XMLUtils.getValueAtXPath(taxElem, "./@Type"));
		taxJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(taxElem, "./@CurrencyCode"));
		taxJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(taxElem, "./@Amount"),0));

		return taxJson;

	}

	//***********************************SI XML TO JSON FOR RESPONSE ENDS HERE**************************************//

	public static String process(JSONObject reqJson) {
		try{
			long start = System.currentTimeMillis();
			//OperationConfig opConfig = AccoConfig.getOperationConfig(OPERATION_NAME);
			ServiceConfig opConfig = AccoConfig.getOperationConfig(OPERATION_NAME);
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			TrackingContext.setTrackingContext(reqJson);
			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
			String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
			String userID = reqHdrJson.getString(JSON_PROP_USERID);

			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct("Accommodation","Hotel");
			if (prodSuppliers == null) {
				throw new Exception("Product supplier not found for user/client");
			}

			XMLUtils.setValueAtXPath(reqElem, "./acco:RequestHeader/com:SessionID",sessionID);
			XMLUtils.setValueAtXPath(reqElem, "./acco:RequestHeader/com:TransactionID",transactionID);
			XMLUtils.setValueAtXPath(reqElem, "./acco:RequestHeader/com:UserID",userID);

			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader/com:SupplierCredentialsList");
			for (ProductSupplier prodSupplier : prodSuppliers) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}
			setSuppReqOTAElem(ownerDoc, XMLUtils.getFirstElementAtXPath(reqElem,"./accoi:RequestBody/ota:OTA_HotelAvailRQ"), reqBodyJson,reqHdrJson);
			//SI req made
			//return XMLTransformer.toString(reqElem);

			logger.info("Before opening HttpURLConnection to SI");
			long SI_start = System.currentTimeMillis();
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), AccoConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			long SI_elapsed = System.currentTimeMillis() - SI_start;
			logger.info("HttpURLConnection to SI closed");
			logger.info(String.format("Time taken to get SI response : %s ms", SI_elapsed));
			JSONObject resJson = getSupplierResponseJSON(reqJson, resElem); 
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
			//SI res in json
			//return resJson.toString();

			Map<Integer,String> SI2BRMSRoomMap= new HashMap<Integer,String>();
			JSONObject resSupplierComJson =  SupplierCommercials.getSupplierCommercials(CommercialsOperation.Search,reqJson, resJson, SI2BRMSRoomMap);
			JSONObject resClientComJson = ClientCommercials.getClientCommercials(reqJson,resSupplierComJson);
			calculatePrices(reqJson,resJson,resClientComJson,resSupplierComJson,SI2BRMSRoomMap,false);
                        //CompanyOffers.getCompanyOffers(reqJson, resJson,OffersConfig.Type.COMPANY_SEARCH_TIME);
			logger.info(String.format("Total time elapsed : %s ms", System.currentTimeMillis()-start));
			logger.info(String.format("Total time elapsed excluding SI layer time: %s ms", System.currentTimeMillis()-start-SI_elapsed));

			return resJson.toString();

		}
		catch (Exception x) {
			x.printStackTrace();
			return "{\"status\": \"ERROR\"}";
		}

	}

	public static String processV2(JSONObject reqJson) {
		try{
			
			  //Calling commercial cache
            //TODO: currently hardcoding. Discuss it with Sir and find out where to get it from 
            String commCacheRes =CommercialCacheProcessor.getFromCache(PRODUCT_ACCO, "B2CIndiaEng", new JSONArray(), reqJson);
            if(commCacheRes!=null && !(commCacheRes.equals("error")))
            return commCacheRes;
			
			//OperationConfig opConfig = AccoConfig.getOperationConfig(OPERATION_NAME);
            ServiceConfig opConfig = AccoConfig.getOperationConfig(OPERATION_NAME);
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			TrackingContext.setTrackingContext(reqJson);

			setSupplierRequestElem(reqJson, reqElem);

			//Element resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), AccoConfig.getHttpHeaders(), reqElem);
			//Element resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			Element resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			JSONObject resJson = getSupplierResponseJSON(reqJson, resElem);

			Map<Integer,String> SI2BRMSRoomMap= new HashMap<Integer,String>();
			JSONObject resSupplierComJson =  SupplierCommercials.getSupplierCommercials(CommercialsOperation.Search,reqJson, resJson, SI2BRMSRoomMap);
			JSONObject resClientComJson = ClientCommercials.getClientCommercials(reqJson,resSupplierComJson);
			
			calculatePrices(reqJson,resJson,resClientComJson,resSupplierComJson,SI2BRMSRoomMap,false);
			//CompanyOffers.getCompanyOffers(reqJson, resJson,OffersConfig.Type.COMPANY_SEARCH_TIME);
			//putting in cache
			 CommercialCacheProcessor.putInCache(PRODUCT_ACCO, "B2CIndiaEng", resJson, reqJson);

			return resJson.toString();
		}
		catch (Exception x) {
			x.printStackTrace();
			return "{\"status\": \"ERROR\"}";
		}
	}


	public static void calculatePrices(JSONObject reqJson, JSONObject resJson, JSONObject clientCommResJson,JSONObject suppCommResJson, Map<Integer,String> SI2BRMSRoomMap,boolean retainSuppFaresAndComm) {
		calculatePrices(reqJson, resJson, clientCommResJson, suppCommResJson, SI2BRMSRoomMap, retainSuppFaresAndComm,null);
	}
	
	public static void calculatePrices(JSONObject reqJson, JSONObject resJson, JSONObject clientCommResJson,JSONObject suppCommResJson, Map<Integer,String> SI2BRMSRoomMap,boolean retainSuppFaresAndComm,Date cutOffDate) {

		JSONArray resRoomJsonArr,entityCommJsonArr,additionalCommsJsonArr;
		JSONArray newTaxJsonArr,newNghtlyPriceJsonArr;
		JSONObject resRoomJson,ccommRoomJson,entityCommJson,markupCalcJson,fareBreakupJson;
		JSONObject newTotalPriceJson,newTotalTaxJson;

		JSONArray briArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
		ClientType clientType = ClientType.valueOf(reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
		Map<String, String> ccommToTypeMap = getClientCommercialsAndTheirType(clientCommResJson);
		Map<String,String> scommToTypeMap= getSupplierCommercialsAndTheirType(suppCommResJson);
		JSONArray resAccoInfoJsonArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);

		int roomIdx=0;
		JSONObject reqHdr= reqJson.getJSONObject(JSON_PROP_REQHEADER);
		String clientMarket = reqHdr.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		String clientCcyCode =reqHdr.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		String suppId=DEF_SUPPID,supplierCurrency;
		boolean isMrkpAdded,isRecvblsAdded;
		BigDecimal totalPrice,newTotalPrice,totalTaxPrice,totalRecvbls,totalIncentives;

		for(int i=0;i<resAccoInfoJsonArr.length();i++) {
			resRoomJsonArr = resAccoInfoJsonArr.getJSONObject(i).getJSONArray(JSON_PROP_ROOMSTAYARR);
			for(int j=0;j<resRoomJsonArr.length();j++) {
				/**
				 * Get supplier roomStay json and apply ROE to all the prices 
				 * TODO:This ROE can also be applied while creating the response.
				 * Supplier prices are retained in case of reprice operation where flag retainFaresAndComm comes as true
				 * Commercials and supplier prices breakup might be needed after calculating prices. So pushing to redis is not done here
				 * due to which new price json's have been made 
				 */
				//---------------Create new price structs--------------------
				resRoomJson = resRoomJsonArr.getJSONObject(j);
				supplierCurrency = resRoomJson.getJSONObject(JSON_PROP_ROOMPRICE).getString(JSON_PROP_CCYCODE);
				newTotalPriceJson = new JSONObject(resRoomJson.getJSONObject(JSON_PROP_ROOMPRICE).toString());
				newTotalTaxJson = newTotalPriceJson.getJSONObject(JSON_PROP_TOTALTAX);
				newTaxJsonArr = new JSONArray();
				newNghtlyPriceJsonArr = new JSONArray(resRoomJson.getJSONArray(JSON_PROP_NIGHTLYPRICEARR).toString());
				//-------------Apply ROE-------------
				applyRoe(newTotalPriceJson, clientCcyCode, clientMarket,cutOffDate);
				applyRoe(newTotalTaxJson, clientCcyCode, clientMarket,cutOffDate);
				if(newTotalTaxJson.has(JSON_PROP_TAXBRKPARR)) {
					newTaxJsonArr = newTotalTaxJson.getJSONArray(JSON_PROP_TAXBRKPARR);
					for(Object taxJson:newTaxJsonArr) {
						applyRoe((JSONObject) taxJson, clientCcyCode, clientMarket,cutOffDate);
					}
				}
				for(Object nghtPriceJson:newNghtlyPriceJsonArr) {
					applyRoe((JSONObject) nghtPriceJson, clientCcyCode, clientMarket,cutOffDate);
					applyRoe(((JSONObject) nghtPriceJson).getJSONObject(JSON_PROP_TOTALTAX), clientCcyCode, clientMarket,cutOffDate);
				}
				//----------------Retain fares-------------
				if(retainSuppFaresAndComm) {
					resRoomJson.put(JSON_PROP_SUPPROOMPRICE,  resRoomJson.getJSONObject(JSON_PROP_ROOMPRICE));
					resRoomJson.put(JSON_PROP_SUPPNIGHTLYPRICEARR, resRoomJson.getJSONArray(JSON_PROP_NIGHTLYPRICEARR));
				}
				//--------------Initialize prices-----------
				totalPrice = newTotalPrice = newTotalPriceJson.getBigDecimal(JSON_PROP_AMOUNT);
				totalTaxPrice = newTotalTaxJson.optBigDecimal(JSON_PROP_AMOUNT,new BigDecimal(0));
				totalRecvbls = new BigDecimal(0);
				totalIncentives = new BigDecimal(0);
				//---------------Inititalize receivables-----------
				JSONObject recevblsJson = new JSONObject();
				recevblsJson.put(JSON_PROP_CCYCODE, clientCcyCode);
				recevblsJson.put(JSON_PROP_AMOUNT, totalRecvbls);
				recevblsJson.put(JSON_PROP_RECEIVABLE,new JSONArray());
				//---------------Inititalize incentives-----------
				JSONObject incentivesJson = new JSONObject();
				incentivesJson.put(JSON_PROP_CCYCODE, clientCcyCode);
				incentivesJson.put(JSON_PROP_AMOUNT, totalIncentives);
				incentivesJson.put(JSON_PROP_INCENTIVE,new JSONArray());
				//-------------replace current prices---------
				newTotalPriceJson.put(JSON_PROP_RECEIVABLES, recevblsJson);
				resRoomJson.put(JSON_PROP_ROOMPRICE, newTotalPriceJson);
				resRoomJson.put(JSON_PROP_NIGHTLYPRICEARR, newNghtlyPriceJsonArr);
				//---------------------------------------------------------------------------

				/**
				 * SI2BRMS map has been populated during supplier commercials.
				 * rooms in SI response are indexed according to the natural ordering of their occurence
				 * rooms in Comm response are indexed depending on the position of BRi,hotel and the room itself and forming a composite briKey
				 */
				ccommRoomJson = getClientCommercialsRoomDetailJson(SI2BRMSRoomMap.get(roomIdx++),briArr);//decompose key to find indexes and get matching room
				if(ccommRoomJson==null) {
					logger.warn(String.format("Client commercials room detail not found at briKey %s for room number %s",SI2BRMSRoomMap.get(roomIdx-1),roomIdx-1));
					continue;
				}
				entityCommJsonArr = ccommRoomJson.optJSONArray(JSON_PROP_ENTITYCOMMS);//retieve client commercials
				JSONArray clntEntityCommJsonArray = getClntEntityCommJsonArray(entityCommJsonArr, ccommToTypeMap,reqHdr,supplierCurrency);
				
				if(retainSuppFaresAndComm) {
					//JSONArray clntEntityCommJsonArray = getClntEntityCommJsonArray(entityCommJsonArr, ccommToTypeMap,reqHdr,supplierCurrency);
					JSONArray suppCommJsonArray = getSuppCommJsonArray(ccommRoomJson.optJSONArray(JSON_PROP_COMMDETAILS), scommToTypeMap,supplierCurrency);

					if (suppCommJsonArray.length()==0) 
						logger.warn(String.format("No supplier commercials found for supplier %s at briKey %s for room number %s",suppId,SI2BRMSRoomMap.get(roomIdx-1),roomIdx-1));
					//if (clntEntityCommJsonArray.length()==0) :- done below
					//logger.warn(String.format("No client commercials found for supplier %s at briKey %s for room number %s",suppId,SI2BRMSRoomMap.get(roomIdx-1),roomIdx-1)); 

					resRoomJson.put(JSON_PROP_CLIENTENTITYCOMMS,clntEntityCommJsonArray);
					resRoomJson.put(JSON_PROP_SUPPCOMM,suppCommJsonArray );

				}

				suppId= resRoomJson.getString(JSON_PROP_SUPPREF);
				isMrkpAdded=isRecvblsAdded=false; 
				if (entityCommJsonArr == null || entityCommJsonArr.length()==0) {
					logger.info(String.format("No client commercials found for supplier %s at briKey %s for room number %s",suppId,SI2BRMSRoomMap.get(roomIdx-1),roomIdx-1));
					continue;
				}
				//backtrack entity commercials and apply the latest one
				for (int l = (entityCommJsonArr.length() - 1); l >= 0 && !(isMrkpAdded && isRecvblsAdded); l--) {
					entityCommJson = entityCommJsonArr.getJSONObject(l);

					markupCalcJson = entityCommJson.optJSONObject(JSON_PROP_MARKUPCOMDTLS);
					if (markupCalcJson != null && !isMrkpAdded) {
						BigDecimal markupCalcRoe = RedisRoEData.getRateOfExchange(markupCalcJson.optString(JSON_PROP_COMMCCY,supplierCurrency), clientCcyCode, clientMarket,cutOffDate);
						newTotalPrice = newTotalPrice.add(markupCalcJson.optBigDecimal(JSON_PROP_COMMAMOUNT,new BigDecimal(0)).multiply(markupCalcRoe));

						fareBreakupJson = markupCalcJson.optJSONObject(JSON_PROP_FAREBRKUP);
						if(fareBreakupJson!=null) {
							//BigDecimal mrkdUpBasePrice = fareBreakupJson.optBigDecimal(JSON_PROP_BASEFARE,new BigDecimal(0)).multiply(markupCalcRoe);
							//newTotalTaxJson.put(JSON_PROP_AMOUNT, totalPrice.subtract(mrkdUpBasePrice));
							BigDecimal totalTaxDiff = new BigDecimal(0);
							for(Object taxComp:newTaxJsonArr) {
								//TODO:chek for optional taxDetails
								String taxCode = ((JSONObject) taxComp).getString(JSON_PROP_TAXCODE);
								JSONObject ccommTaxDetailJson =getTaxDetailForTaxCode(fareBreakupJson.optJSONArray(JSON_PROP_TAXDETAILS),taxCode);
								if (ccommTaxDetailJson != null && ccommTaxDetailJson.has(JSON_PROP_TAXVALUE)) {
									BigDecimal mrkdUpTaxPrice = ccommTaxDetailJson.getBigDecimal(JSON_PROP_TAXVALUE);
									if(mrkdUpTaxPrice!=null) {
										mrkdUpTaxPrice = mrkdUpTaxPrice.multiply(markupCalcRoe);
										((JSONObject) taxComp).put(JSON_PROP_AMOUNT, mrkdUpTaxPrice);
										totalTaxDiff = totalTaxDiff.add(mrkdUpTaxPrice.subtract(((JSONObject) taxComp).getBigDecimal(JSON_PROP_AMOUNT)));
									}
								}
							}
							if(newTotalTaxJson.has(JSON_PROP_AMOUNT)) {
								newTotalTaxJson.put(JSON_PROP_AMOUNT, newTotalTaxJson.getBigDecimal(JSON_PROP_AMOUNT).add(totalTaxDiff));
							}
						}
						isMrkpAdded = true;
					}

					/** TODO: In case of B2B, do we need to add additional receivable commercials for all client hierarchy levels? 
					 * As of now the latest one is considered 
					 */
					additionalCommsJsonArr = entityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);//take the array additionalcommercialDetails
					if (additionalCommsJsonArr != null && !isRecvblsAdded) {
						for (int x=0; x < additionalCommsJsonArr.length(); x++) {
							JSONObject additionalCommsJson = additionalCommsJsonArr.getJSONObject(x);//take object of additionalcommercialDetails array one by one
							if (COMM_TYPE_RECEIVABLE.equals(ccommToTypeMap.get(additionalCommsJson.optString(JSON_PROP_COMMNAME)))) {//is the additionalCommName receivable?
								String additionalCommCcy = additionalCommsJson.optString(JSON_PROP_COMMCCY,supplierCurrency);
								BigDecimal additionalCommAmt = additionalCommsJson.getBigDecimal(JSON_PROP_COMMAMOUNT);
								
								if(additionalCommAmt!=null) {
									additionalCommAmt = additionalCommAmt.multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket,cutOffDate));
									newTotalPrice = newTotalPrice.add(additionalCommAmt);
									//add receivable
									JSONObject recevblJson = new JSONObject();
									recevblJson.put(JSON_PROP_AMOUNT, additionalCommAmt);
									recevblJson.put(JSON_PROP_CCYCODE, clientCcyCode);
									recevblJson.put(JSON_PROP_CODE, additionalCommsJson.optString(JSON_PROP_COMMNAME));
									
									recevblsJson.append(JSON_PROP_RECEIVABLE,recevblJson);
									totalRecvbls = totalRecvbls.add(additionalCommAmt);
									isRecvblsAdded = true;
								}
							}
						}

					}
				
					
					// For B2B clients, the incentives of the last client hierarchy level should be accumulated and returned in the response.
					if ( clientType == ClientType.B2B) {
        			if (l == (clntEntityCommJsonArray.length() - 1)) {
        				JSONObject clientEntityCommercialsJson = clntEntityCommJsonArray.getJSONObject(l);
    					JSONArray clntCommArr=clientEntityCommercialsJson.getJSONArray("clientCommercials");
        				for (int x = 0; x < clntCommArr.length(); x++) {
        					JSONObject clntCommObj=clntCommArr.getJSONObject(x);
        					if (COMM_TYPE_PAYABLE.equals(clntCommObj.getString(JSON_PROP_COMMTYPE))) {
        						String commCcy = clntCommObj.getString(JSON_PROP_COMMCCY);
        						String commName = clntCommObj.getString(JSON_PROP_COMMNAME);
        						BigDecimal commAmt = clntCommObj.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(commCcy, clientCcyCode, clientMarket,cutOffDate));
        						
        					   //add incentives
        						if(commAmt!=null) {
								JSONObject incentiveJson = new JSONObject();
								incentiveJson.put(JSON_PROP_AMOUNT, commAmt);
								incentiveJson.put(JSON_PROP_CCYCODE, clientCcyCode);
								incentiveJson.put(JSON_PROP_CODE, commName);
								
								incentivesJson.append(JSON_PROP_INCENTIVE,incentiveJson);
								totalIncentives = totalIncentives.add(commAmt);
        						}
        					}
        				}
        			}
        			newTotalPriceJson.put(JSON_PROP_INCENTIVES, incentivesJson);
					}
				
				}

				newTotalPriceJson.put(JSON_PROP_AMOUNT, newTotalPrice);
				recevblsJson.put(JSON_PROP_AMOUNT, totalRecvbls);
				incentivesJson.put(JSON_PROP_AMOUNT, totalIncentives);
				BigDecimal increasedTotalPrice = newTotalPrice.subtract(totalPrice);
				BigDecimal increasedTotalTaxPrice = newTotalTaxJson.optBigDecimal(JSON_PROP_AMOUNT,new BigDecimal(0)).subtract(totalTaxPrice);

				//calculate night price by weighted avg
				for(Object nghtPriceJson:newNghtlyPriceJsonArr) {
					BigDecimal nightlyPrice = ((JSONObject) nghtPriceJson).getBigDecimal(JSON_PROP_AMOUNT);
					((JSONObject) nghtPriceJson).put(JSON_PROP_AMOUNT,nightlyPrice.add(nightlyPrice.divide(totalPrice,new MathContext(6,RoundingMode.HALF_UP)).multiply(increasedTotalPrice)));

					JSONObject nghtTotalTaxJson = ((JSONObject) nghtPriceJson).getJSONObject(JSON_PROP_TOTALTAX);
					if(nghtTotalTaxJson.has(JSON_PROP_AMOUNT)) {
						//if nght tax is present total tax should be present.Else raise it to si
						nightlyPrice = ((JSONObject) nghtTotalTaxJson).getBigDecimal(JSON_PROP_AMOUNT);
						nghtTotalTaxJson.put(JSON_PROP_AMOUNT,nightlyPrice.add(nightlyPrice.divide(totalTaxPrice,new MathContext(6,RoundingMode.HALF_UP)).multiply(increasedTotalTaxPrice)));
					}
				}

			}
		}
	}

	public static Map<String, String> getSupplierCommercialsAndTheirType(JSONObject suppCommResJson) {
		JSONObject scommBRIJson,commHeadJson;
		JSONArray commHeadJsonArr = null;
		Map<String, String> suppCommToTypeMap = new HashMap<String, String>();
		JSONArray scommBRIJsonArr = getSupplierCommercialsBusinessRuleIntakeJSONArray(suppCommResJson);
		for (int i=0; i < scommBRIJsonArr.length(); i++) {
			scommBRIJson = scommBRIJsonArr.getJSONObject(i);
			commHeadJsonArr = scommBRIJson.optJSONArray(JSON_PROP_COMMHEAD);
			if (commHeadJsonArr == null) {
				logger.warn("No commercial heads found in supplier commercials");
				continue;
			}

			for (int j=0; j < commHeadJsonArr.length(); j++) {
				commHeadJson = commHeadJsonArr.getJSONObject(j);
				suppCommToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME), commHeadJson.getString(JSON_PROP_COMMTYPE));
			}
		}

		return suppCommToTypeMap;

	}

	private static JSONArray getSupplierCommercialsBusinessRuleIntakeJSONArray(JSONObject suppCommResJson) {
		JSONArray briJsonArray = new JSONArray();

		try{
			briJsonArray = suppCommResJson.getJSONObject(JSON_PROP_RESULT).getJSONObject(JSON_PROP_EXECUTIONRES).getJSONArray(JSON_PROP_RESULTS).getJSONObject(0).getJSONObject(JSON_PROP_VALUE).getJSONObject(JSON_PROP_SUPPTRANRULES).getJSONArray(JSON_PROP_BUSSRULEINTAKE);
		}
		catch(Exception e){
			logger.warn("Supplier Commercials \"businessRuleIntake\" evaluated to be null");
		}
		if(briJsonArray==null) {
			logger.warn("Supplier Commercials \"businessRuleIntake\" evaluated to be null");
			briJsonArray = new JSONArray();
		}

		return briJsonArray;

	}

	public static Map<String, String> getClientCommercialsAndTheirType(JSONObject clientCommResJson) {
		JSONArray commHeadJsonArr = null;
		JSONArray entDetaiJsonArray = null;
		JSONObject commHeadJson = null;
		Map<String, String> commToTypeMap = new HashMap<String, String>();
		JSONArray ccommBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);

		for (int i=0; i < ccommBRIJsonArr.length(); i++) {
			if ((entDetaiJsonArray = ccommBRIJsonArr.getJSONObject(i).optJSONArray(JSON_PROP_ENTITYDETAILS)) == null) {
				continue;
			}
			for(int j=0;j<entDetaiJsonArray.length();j++){
				if((commHeadJsonArr=entDetaiJsonArray.getJSONObject(j).optJSONArray(JSON_PROP_COMMHEAD)) == null) {
					logger.warn("No commercial heads found in client commercials");
					continue;
				}

				for (int k=0; k < commHeadJsonArr.length(); k++) {
					commHeadJson = commHeadJsonArr.getJSONObject(k);
					commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME), commHeadJson.getString(JSON_PROP_COMMTYPE));
				}
			}
		}

		return commToTypeMap;

	}

	private static JSONArray getClientCommercialsBusinessRuleIntakeJSONArray(JSONObject clientCommResJson) {

		JSONArray briJsonArray = new JSONArray();

		try{
			briJsonArray = clientCommResJson.getJSONObject(JSON_PROP_RESULT).getJSONObject(JSON_PROP_EXECUTIONRES).getJSONArray(JSON_PROP_RESULTS).getJSONObject(0).getJSONObject(JSON_PROP_VALUE).getJSONObject(JSON_PROP_CLIENTTRANRULES).getJSONArray(JSON_PROP_BUSSRULEINTAKE);
		}
		catch(Exception e){
			logger.warn("Client Commercials \"businessRuleIntake\" evaluated to be null");
		}
		if(briJsonArray==null) {
			logger.warn("Client Commercials \"businessRuleIntake\" evaluated to be null");
			briJsonArray = new JSONArray();
		}

		return briJsonArray;
	}

	private static JSONObject getClientCommercialsRoomDetailJson(String briKey,JSONArray briArr){
		JSONObject roomJson = null;
		if(briKey==null)
			return roomJson;
		String[] briIdxs = briKey.split(Pattern.quote(String.valueOf(KEYSEPARATOR)));//to escape this pipe
		//Key has been made using bri index,hotel index and room index in supplier commercials. Therefore this below restriction.
		if(briIdxs.length!=3)
			return roomJson;
		try {
			roomJson =(JSONObject) new JSONPointer(String.format("/%s/%s/%s/%s/%s",briIdxs[0],JSON_PROP_HOTELDETAILS,briIdxs[1],JSON_PROP_ROOMDETAILS,briIdxs[2])).queryFrom(briArr);
		}
		catch(Exception e) {}

		return roomJson;
	}

	private static JSONArray getSuppCommJsonArray(JSONArray suppCommDtlsJsonArr,Map<String,String> scommTypeMap,String supplierCurrency) {

		JSONArray suppCommJsonArr = new JSONArray(); 

		if(suppCommDtlsJsonArr == null)
			return suppCommJsonArr;

		suppCommDtlsJsonArr.forEach(suppCommDtlsJson -> addCommercialJson(suppCommJsonArr, (JSONObject) suppCommDtlsJson, scommTypeMap,supplierCurrency));

		return suppCommJsonArr;
	}

	private static JSONArray getClntEntityCommJsonArray(JSONArray entityCommJsonArr,Map<String,String> ccommTypeMap,JSONObject reqHdr,String supplierCurrency) {

		JSONArray clntEntityCommJsonArr = new JSONArray();

		if(entityCommJsonArr == null)
			return clntEntityCommJsonArr;

		entityCommJsonArr.forEach(clntentityJson -> addClientEntityJson(clntEntityCommJsonArr,(JSONObject) clntentityJson, ccommTypeMap,reqHdr,supplierCurrency));

		return clntEntityCommJsonArr;
	}

	private static void addClientEntityJson(JSONArray clntEntityJsonArr, JSONObject clntentityJson,
			Map<String,String> ccommTypeMap,JSONObject reqHdr,String supplierCurrency) {

		JSONObject entityJson = new JSONObject();
		JSONArray ccommJsonArr = new JSONArray();
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdr);

		// TODO:get data from usr ctx
		JSONObject userCtxJson=usrCtx.toJSON();
		JSONArray clientEntityDetailsArr = userCtxJson.getJSONArray(JSON_PROP_CLIENTCOMMENTITYDTLS);
		for(int y=0;y<clientEntityDetailsArr.length();y++) {
			/*if(clientEntityDetailsArr.getJSONObject(y).opt(JSON_PROP_COMMENTITYID)!=null)
			{
			if(clientEntityDetailsArr.getJSONObject(y).opt(JSON_PROP_COMMENTITYID).toString().equalsIgnoreCase(clientEntityCommJson.get("entityName").toString()))
			{
				clientEntityDetailsJson=clientEntityDetailsArr.getJSONObject(y);
			}
			}*/
			//TODO:Add a check later
                        entityJson=clientEntityDetailsArr.getJSONObject(y);
                        entityJson.put(JSON_PROP_ENTITYNAME, clntentityJson.getString(JSON_PROP_ENTITYNAME));
			entityJson.put(JSON_PROP_CLIENTCOMM, ccommJsonArr);
			// insert markup
			addCommercialJson(ccommJsonArr, clntentityJson.optJSONObject(JSON_PROP_MARKUPCOMDTLS), ccommTypeMap,supplierCurrency);
			// insert additional comm details
			JSONArray addCommDtlsJsonArr = clntentityJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
			if(addCommDtlsJsonArr!=null) {
				addCommDtlsJsonArr.forEach(addCommDtlsJson -> addCommercialJson(ccommJsonArr, (JSONObject) addCommDtlsJson, ccommTypeMap,supplierCurrency));
			}
			// insert fixed comm details
			JSONArray fixedCommDtlsJsonArr = clntentityJson.optJSONArray("fixedCommercialDetails");
			if(fixedCommDtlsJsonArr!=null) {
				fixedCommDtlsJsonArr.forEach(fixedCommDtlsJson -> addCommercialJson(ccommJsonArr, (JSONObject) fixedCommDtlsJson, ccommTypeMap,supplierCurrency));
			}
			// insert retention comm details
			JSONArray retentionCommDtlsJsonArr = clntentityJson.optJSONArray("retentionCommercialDetails");
			if(retentionCommDtlsJsonArr!=null) {
			retentionCommDtlsJsonArr.forEach(retentionCommDtlsJson -> addCommercialJson(ccommJsonArr, (JSONObject) retentionCommDtlsJson, ccommTypeMap,supplierCurrency));
						}
            clntEntityJsonArr.put(entityJson);
		}
	}

	private static void addCommercialJson(JSONArray commJsonArr,JSONObject commJson,Map<String,String> commTypeMap,String supplierCurrency) {

		if(commJson == null)
			return;
		JSONObject newCommJson = new JSONObject();

		newCommJson.put("commercialCalculationPercentage", commJson.optString("commercialCalculationPercentage",null));
		newCommJson.put("commercialCalculationAmount", commJson.optString("commercialCalculationAmount",null));
		newCommJson.put("commercialFareComponent", commJson.optString("commercialFareComponent",null));
		newCommJson.put("retentionPercentage", commJson.optString("retentionPercentage",null));
		newCommJson.put("retentionAmountPercentage", commJson.optString("retentionAmountPercentage",null));
		newCommJson.put("remainingPercentageAmount", commJson.optString("remainingPercentageAmount",null));
		newCommJson.put("remainingAmount", commJson.optString("remainingAmount",null));
		newCommJson.put(JSON_PROP_COMMCCY, commJson.optString(JSON_PROP_COMMCCY, supplierCurrency));
		newCommJson.put(JSON_PROP_COMMAMOUNT, commJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
		newCommJson.put(JSON_PROP_COMMNAME, commJson.optString(JSON_PROP_COMMNAME, ""));
		newCommJson.put(JSON_PROP_COMMTYPE, commTypeMap.get(commJson.get(JSON_PROP_COMMNAME)));
		newCommJson.put(JSON_PROP_MDMRULEID, commJson.optString("mdmruleId"));

		commJsonArr.put(newCommJson);
	}

	static void applyRoe(JSONObject priceJson,String toCcy, String market) {
		applyRoe(priceJson, toCcy, market,null);
	}
	
	private static void applyRoe(JSONObject priceJson,String toCcy, String market,Date cutOffDate) {
		if(priceJson == null)
			return;
		if(priceJson.has(JSON_PROP_AMOUNT)) {
			BigDecimal currAmt = priceJson.getBigDecimal(JSON_PROP_AMOUNT);
			priceJson.put(JSON_PROP_AMOUNT, currAmt.multiply(RedisRoEData.getRateOfExchange(priceJson.optString(JSON_PROP_CCYCODE, ""), toCcy, market,cutOffDate)));
			priceJson.put(JSON_PROP_CCYCODE,toCcy);
		}

	}

	private static JSONObject getTaxDetailForTaxCode(JSONArray ccommTaxDetailsJsonArr, String taxCode) {
		if (taxCode == null || taxCode.isEmpty()) {
			return null;
		}

		for (int i=0; i < ccommTaxDetailsJsonArr.length(); i++) {
			JSONObject ccommTaxDetailJson = ccommTaxDetailsJsonArr.getJSONObject(i);
			if (taxCode.equals(ccommTaxDetailJson.getString(JSON_PROP_TAXNAME))) {
				return ccommTaxDetailJson;
			}
		}

		return null;
	}

}
