package com.coxandkings.travel.bookingengine.orchestrator.accoV3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.accoV3.MRCompanyOffers;
import com.coxandkings.travel.bookingengine.orchestrator.accoV3.enums.AccoSubType;
import com.coxandkings.travel.bookingengine.orchestrator.accoV3.enums.AvailabilityStatus;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AccoSearchProcessor implements AccoConstants {

	private static final Logger logger = LogManager.getLogger(AccoSearchProcessor.class);

	//***********************************SI JSON TO XML FOR REQUEST BODY STARTS HERE**************************************//
	public static Map<String,String> setSupplierRequestElem(JSONObject reqJson,Element reqElem) throws Exception {
        Map<String,String> credSubTypeMap=new HashMap<>();
		Document ownerDoc = reqElem.getOwnerDocument();
		JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null) ? reqJson.optJSONObject(JSON_PROP_REQHEADER) : new JSONObject();
		JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null) ? reqJson.optJSONObject(JSON_PROP_REQBODY) : new JSONObject();

		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);

		JSONObject clientContext=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		
		
		createSuppReqHdrElem(XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader"),sessionID,transactionID,userID,clientContext,usrCtx);
		
		// The mock UI is changing very frequently and now Accommodation sub-types are removed from UI.
        // Just to protect our code from all these frequent changes,
        //if accommodation subtype is not provided in request, search for all configured subtypes
        JSONArray prodCategSubtypeJsonArr = reqBodyJson.optJSONArray(JSON_PROP_ACCOSUBTYPEARR);
        if (prodCategSubtypeJsonArr == null || prodCategSubtypeJsonArr.length()==0) {
        	prodCategSubtypeJsonArr=usrCtx.getSubCatForProductCategory(PROD_CATEG_ACCO);
        	reqBodyJson.put(JSON_PROP_ACCOSUBTYPEARR, prodCategSubtypeJsonArr);
        }
        
        if(prodCategSubtypeJsonArr.length()==0)
        	throw new Exception("Product supplier not found for user/client for subtypes ".concat(prodCategSubtypeJsonArr.toString()));
        
		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader/com:SupplierCredentialsList");
		for(Object prodCategSubtype: prodCategSubtypeJsonArr) {
			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_ACCO,(String) prodCategSubtype);
			//List<ProductSupplier> prodSuppliers = usrCtx.getPccHapSuppliersForProduct(PROD_CATEG_ACCO, (String) prodCategSubtype, "search");
			if (prodSuppliers == null) {
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for Accommodation subtype ".concat((String) prodCategSubtype));
				logger.warn("Product supplier not found for user/client for subtype ".concat((String) prodCategSubtype));
				continue;
			}
			for (ProductSupplier prodSupplier : prodSuppliers) {
				/*String advDefnId = prodSupplier.getAdvanceDefinitionId();
				if(Utils.isStringNotNullAndNotEmpty(advDefnId)) {
					ValidateAdvancedDefinition.processOtherProducts(MDMUtils.getPccAdvancedDefinitionsDoc(advDefnId));
				}*/
				//This Map is maintained to map in response which room belongs to which subType based on the CredName
				credSubTypeMap.put(prodSupplier.getCredentialsName(), (String) prodCategSubtype);
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}
		}
		
		setSuppReqOTAElem(ownerDoc, XMLUtils.getFirstElementAtXPath(reqElem,"./accoi:RequestBody/ota:OTA_HotelAvailRQ"), reqBodyJson,reqHdrJson);
		
		return credSubTypeMap;

	}

	public static void createSuppReqHdrElem(Element reqHdrElem, String sessionId, String transactId, String userId,JSONObject clientContext,UserContext usrCtx) {

		XMLUtils.setValueAtXPath(reqHdrElem, "./com:SessionID",sessionId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:TransactionID",transactId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:UserID",userId);
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:MarketID",clientContext.getString(JSON_PROP_CLIENTMARKET));
		ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:EntityID", clInfo[clInfo.length - 1].getCommercialsEntityId());	
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:EntityType",clientContext.getString(JSON_PROP_CLIENTTYPE));
		XMLUtils.setValueAtXPath(reqHdrElem, "./com:FlagLHRAll",usrCtx.getClientOptionToDisplay().toString());
		
	}

	public static void setSuppReqOTAElem(Document ownerDoc,Element reqOTAElem,JSONObject reqParamJson,JSONObject reqHdrJSON) {

		reqOTAElem.setAttribute("RequestedCurrency", reqHdrJSON.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY));
		
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
			//send rph for requested room(will not be 0)
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
	public static JSONObject getSupplierResponseJSONV3(JSONObject reqJson,Element resElem, Map<String, String> credSubTypeMap){
		JSONObject resJson = new JSONObject();
		JSONArray accInfoArr=new JSONArray();
		JSONObject resBodyJson=new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		resBodyJson.put(JSON_PROP_ACCOMODATIONARR, accInfoArr);
		JSONObject roomStayJson,roomStayJsonArr;
		
		//Each roomstay signifies a hotel and roomrate signifies the rooms
		for (Element roomStayElem : XMLUtils.getElementsAtXPath(resElem, "./accoi:ResponseBody/ota:OTA_HotelAvailRS/ota:RoomStays/ota:RoomStay")) {
			roomStayJsonArr = new JSONObject();
			for (Element roomRateElem : XMLUtils.getElementsAtXPath(roomStayElem, "./ota:RoomRates/ota:RoomRate")) {
				//If commercials are not Applied not to consider this room
				boolean commercialsAppliedFlag = Boolean.parseBoolean(XMLUtils.getValueAtXPath(roomRateElem,"./ota:Total/ota:TPA_Extensions/ota:CommercialApplied"));
				if(commercialsAppliedFlag) {
					roomStayJson = getRoomStayJSONV3(roomRateElem,roomStayElem,credSubTypeMap);
					roomStayJsonArr.append(JSON_PROP_ROOMSTAYARR, roomStayJson);
				}
			}
			accInfoArr.put(roomStayJsonArr);
			}
		return resJson;

	}
	
	private static JSONObject getRoomStayJSONV3(Element roomRateElem, Element roomStayElem, Map<String, String> credSubTypeMap) {
		JSONObject roomStayJson = new JSONObject();
		
		Element TPA_Ext = XMLUtils.getFirstElementAtXPath(roomRateElem,"./ota:Total/ota:TPA_Extensions");
		
		//*************TotalPriceInfo*************//
	    roomStayJson.put(JSON_PROP_ROOMPRICE, getTotalPriceJson(XMLUtils.getFirstElementAtXPath(roomRateElem, "./ota:Total"),TPA_Ext));
				
		//**********nightly prices*************//
		JSONArray nightRateArr = new JSONArray();
		roomStayJson.put(JSON_PROP_NIGHTLYPRICEARR,nightRateArr);
		for(Element nightRateElem:XMLUtils.getElementsAtXPath(roomRateElem, "./ota:Rates/ota:Rate")){
			nightRateArr.put(getNightPriceJson(nightRateElem));
		}
		
		Element rateElem = XMLUtils.getFirstElementAtXPath(roomRateElem, "./ota:Rates/ota:Rate");
		String rateMode = rateElem.getAttribute("RateMode");
		roomStayJson.put("isRecommended","R".equalsIgnoreCase(rateMode));
		
		//***********available Units***************//
		String availableRooms =roomRateElem.getAttribute("NumberOfUnits");
		if(Utils.isStringNotNullAndNotEmpty(availableRooms))
			roomStayJson.put("availableUnits",Integer.valueOf(availableRooms));
		
		//***********Occupancy Info*************//
		roomStayJson.put(JSON_PROP_OCCUPANCYARR, getOccupancyJson(TPA_Ext));
		
		
		String prepayInd = XMLUtils.getValueAtXPath(TPA_Ext, "./ota:PrepaidIndicator");
		roomStayJson.put(JSON_PROP_PAYATHOTEL,prepayInd.isEmpty()?false:!Boolean.valueOf(prepayInd));
		
		String absDeadline= XMLUtils.getValueAtXPath(TPA_Ext, "./ota:CancelPenalties/ota:CancelPenalty/ota:Deadline/@AbsoluteDeadline");
        if(Utils.isStringNotNullAndNotEmpty(absDeadline))
        	roomStayJson.put("freeCancellationTill",absDeadline);
		
		//************RoomInfo**************///
		roomStayJson.put(JSON_PROP_ROOMINFO, getRoomInfoJsonV3(roomRateElem,roomStayElem,TPA_Ext));
		
		//********SupplierID********//
		//first check channelManagerID ,if present consider it,if not consider supplierID
		String suppRef,credName;	
		String channelManagerId = XMLUtils.getValueAtXPath(TPA_Ext, "./ota:ChannelManagerID");
		if(Utils.isStringNullOrEmpty(channelManagerId) || ("0").equals(channelManagerId)) {
			suppRef = XMLUtils.getValueAtXPath(TPA_Ext, "./ota:SupplierID");
			credName=XMLUtils.getValueAtXPath(TPA_Ext, "./ota:SupplierCredentialName");
		}else {
			suppRef = XMLUtils.getValueAtXPath(TPA_Ext, "./ota:ChannelManagerID");
			credName=XMLUtils.getValueAtXPath(TPA_Ext, "./ota:ChannelManagerCredName");
		}
		
		//Unique suppRef (suppRef|credName|channelManagerId|channelManagerCredName)
		roomStayJson.put(JSON_PROP_SUPPREF,getSuppRefKey(TPA_Ext));
		
		//************Acco SubType**********//
		roomStayJson.put(JSON_PROP_ACCOSUBTYPE,credSubTypeMap.getOrDefault(credName,AccoSubType.HOTEL.toString()));
		
		//*********SupplierOffers**********//
		Element supplierOffers = XMLUtils.getFirstElementAtXPath(TPA_Ext, "./ota:RoomRateAdditionalDetails/ota:RoomRateAdditionalDetail");
		if(supplierOffers!=null) {
			roomStayJson.put("supplierOffers", getSupplierOfferJson(supplierOffers));
		}
		
		//*****HotelFee which are exclusive******//
		Element TPAExtHotlFee = XMLUtils.getFirstElementAtXPath(TPA_Ext, "./ota:HotelFees");
		if(TPAExtHotlFee!=null) {
			roomStayJson.put("feeInfo", getHotelFeeJson(TPAExtHotlFee));
		}
		
		return roomStayJson;
	}
	
	private static String getSuppRefKey(Element tPA_Ext) {
		String suppRef,credName,channelManagerId,channelManagerCredName;	
		suppRef = XMLUtils.getValueAtXPath(tPA_Ext, "./ota:SupplierID");
		credName=XMLUtils.getValueAtXPath(tPA_Ext, "./ota:SupplierCredentialName");
		channelManagerId = XMLUtils.getValueAtXPath(tPA_Ext, "./ota:ChannelManagerID");
		channelManagerCredName=XMLUtils.getValueAtXPath(tPA_Ext, "./ota:ChannelManagerCredName");	
		
		return String.format("%s%c%s%c%s%c%s", suppRef,KEYSEPARATOR,credName,KEYSEPARATOR,channelManagerId,KEYSEPARATOR,channelManagerCredName);	
	}

	private static JSONObject getRoomInfoJsonV3(Element roomRateElem, Element roomStayElem, Element tPA_Ext) {
		JSONObject roomInfoJson = new JSONObject();
		
		//This flag is used to indicate which requested room in the array it belongs to.It should always come in SI
		String roomIdx_str=XMLUtils.getValueAtXPath(tPA_Ext, "./ota:RPH");
		int roomIdx = roomIdx_str.isEmpty()?0:Integer.valueOf(roomIdx_str);
		roomInfoJson.put(JSON_PROP_ROOMINDEX, roomIdx);
		
		roomInfoJson.put(JSON_PROP_ROOMTYPEINFO, getRoomTypeInfoJsonV3(roomRateElem,tPA_Ext));
		roomInfoJson.put(JSON_PROP_RATEPLANINFO, getRatePlanInfoJsonV3(roomRateElem));
		
		roomInfoJson.put(JSON_PROP_HOTELINFO, getHotelInfoJson(XMLUtils.getFirstElementAtXPath(roomStayElem, "./ota:BasicPropertyInfo")));
		
		roomInfoJson.put(JSON_PROP_MEALINFO, getMealInfoJsonV3(tPA_Ext));
		
		JSONArray referenceArr = new JSONArray();
		roomInfoJson.put(JSON_PROP_REFERENCESARR,referenceArr);
		for(Element referenceElem:XMLUtils.getElementsAtXPath(tPA_Ext, "./ota:Reference")){
			referenceArr.put(getReferencesInfoJson(referenceElem));
		}
		
		AvailabilityStatus availStatus= AvailabilityStatus.forString(XMLUtils.getValueAtXPath(roomRateElem, "./@AvailabilityStatus"));
		roomInfoJson.put(JSON_PROP_AVAILSTATUS, availStatus!=null?availStatus.toString():"");
		
		return roomInfoJson;
	}

	private static JSONObject getMealInfoJsonV3(Element tPA_ExtElem) {
		JSONObject mealJson = new JSONObject();
		Element mealElem = XMLUtils.getFirstElementAtXPath(tPA_ExtElem, "./ota:Meals/ota:Meal");

		mealJson.put(JSON_PROP_MEALCODE,XMLUtils.getValueAtXPath(mealElem, "./@MealId"));
		mealJson.put(JSON_PROP_MEALNAME,XMLUtils.getValueAtXPath(mealElem, "./@Name"));

		return mealJson;
	}

	private static JSONObject getRatePlanInfoJsonV3(Element roomRateElem) {

		JSONObject ratePlanJson = new JSONObject();

		ratePlanJson.put(JSON_PROP_RATEPLANCODE, XMLUtils.getValueAtXPath(roomRateElem, "./@RatePlanCode"));
		ratePlanJson.put(JSON_PROP_RATEPLANNAME, XMLUtils.getValueAtXPath(roomRateElem, "./@RatePlanName"));//to be mapped
		ratePlanJson.put(JSON_PROP_RATEPLANREF, XMLUtils.getValueAtXPath(roomRateElem, "./@RatePlanID"));
		ratePlanJson.put(JSON_PROP_RATEBOOKINGREF, XMLUtils.getValueAtXPath(roomRateElem, "./@BookingCode"));//to be mapped

		return ratePlanJson;
	}

	private static JSONObject getRoomTypeInfoJsonV3(Element roomRateElem, Element tPA_ExtElem) {
		JSONObject roomTypeJson = new JSONObject();

		roomTypeJson.put("roomBookCode", XMLUtils.getValueAtXPath(tPA_ExtElem, "./ota:BookingCode"));
		roomTypeJson.put(JSON_PROP_ROOMTYPECODE, XMLUtils.getValueAtXPath(roomRateElem, "./@RoomTypeCode"));
		roomTypeJson.put(JSON_PROP_ROOMTYPENAME, XMLUtils.getValueAtXPath(roomRateElem, "./@RoomType"));
		roomTypeJson.put(JSON_PROP_ROOMCATEGCODE, XMLUtils.getValueAtXPath(tPA_ExtElem, "./ota:RoomCategoryID"));
		roomTypeJson.put(JSON_PROP_ROOMCATEGNAME, XMLUtils.getValueAtXPath(tPA_ExtElem, "./ota:RoomCategoryName"));
		//ask to map this
		roomTypeJson.put(JSON_PROP_ROOMREF, XMLUtils.getValueAtXPath(roomRateElem, "./@RoomID"));

		return roomTypeJson;

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
			//TODO:Need to add OfferType:Online/Offline
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
		hotelInfoJson.put(JSON_PROP_HOTELREF, XMLUtils.getValueAtXPath(hotelElem, "./@HotelCodeContext"));//to be mapped

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

	public static JSONObject getTotalPriceJson(Element totalPriceElem, Element tPA_Ext){

		JSONObject totalPriceJson = new JSONObject();

		totalPriceJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(totalPriceElem, "./@CurrencyCode"));
		totalPriceJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(totalPriceElem, "./@AmountAfterTax"), 0));
		totalPriceJson.put(JSON_PROP_TOTALTAX, getTotalTaxJson(totalPriceElem, true));

		//Should Receivables Object be shown always ?Even if not present
		String receivableAmt = XMLUtils.getValueAtXPath(tPA_Ext,"./ota:receivables/amount");
		if(Utils.isStringNotNullAndNotEmpty(receivableAmt)) {
			getReceivablesJson(XMLUtils.getFirstElementAtXPath(tPA_Ext,"./ota:receivables"),totalPriceJson);
		}
		
		//Should Incentives Object be shown always ?Even if not present
		String incentiveAmt = XMLUtils.getValueAtXPath(tPA_Ext,"./ota:incentives/amount");
		if(Utils.isStringNotNullAndNotEmpty(incentiveAmt)) {
			getIncentivesJson(XMLUtils.getFirstElementAtXPath(tPA_Ext,"./ota:incentives"),totalPriceJson);
		}
		
		//Should CompanyTaxes Object be shown always ?Even if not present
		String companyTaxAmt = XMLUtils.getValueAtXPath(tPA_Ext,"./ota:Taxes/companyTaxes/amount");
		if(Utils.isStringNotNullAndNotEmpty(companyTaxAmt)) {
			getCompanyTaxesJson(XMLUtils.getFirstElementAtXPath(tPA_Ext,"./ota:Taxes/companyTaxes"),totalPriceJson);
		}
		return totalPriceJson;

	}

	private static void getCompanyTaxesJson(Element companyTaxesElem, JSONObject totalPriceJson) {
		JSONObject companyTaxesJson = new JSONObject();
		/*companyTaxesJson.put(JSON_PROP_CCYCODE,companyTaxesElem.getAttribute("CurrencyCode"));
		companyTaxesJson.put(JSON_PROP_AMOUNT,Utils.convertToBigDecimal(companyTaxesElem.getAttribute("Amount"),0));
		companyTaxesJson.put(JSON_PROP_COMPANYTAX,new JSONArray());*/
		companyTaxesJson.put(JSON_PROP_CCYCODE,XMLUtils.getValueAtXPath(companyTaxesElem, "./ota:currencyCode"));
		companyTaxesJson.put(JSON_PROP_AMOUNT,Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(companyTaxesElem, "./ota:amount"),0));
		companyTaxesJson.put(JSON_PROP_COMPANYTAX,new JSONArray());
		
		for(Element companyTax:XMLUtils.getElementsAtXPath(companyTaxesElem,"./ota:companyTaxe")) {
			JSONObject companyJson = new JSONObject();
			companyJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(companyTax, "./ota:amount"),0));
			companyJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(companyTax, "./ota:currencyCode"));
			companyJson.put(JSON_PROP_TAXCODE, XMLUtils.getValueAtXPath(companyTax, "./ota:taxCode"));
			companyJson.put(JSON_PROP_TAXPCT, XMLUtils.getValueAtXPath(companyTax, "./ota:taxPercent"));
			companyJson.put(JSON_PROP_HSNCODE, XMLUtils.getValueAtXPath(companyTax, "./ota:hsnCode"));
			companyJson.put(JSON_PROP_SACCODE, XMLUtils.getValueAtXPath(companyTax, "./ota:sacCode"));
			companyTaxesJson.append(JSON_PROP_COMPANYTAX, companyJson);
		}
		
		totalPriceJson.put(JSON_PROP_COMPANYTAXES, companyTaxesJson);
	}

	private static void getReceivablesJson(Element receivablesElem, JSONObject totalPriceJson) {
		JSONObject recevblsJson = new JSONObject();
		/*recevblsJson.put(JSON_PROP_CCYCODE,receivablesElem.getAttribute("CurrencyCode"));
		recevblsJson.put(JSON_PROP_AMOUNT,Utils.convertToBigDecimal(receivablesElem.getAttribute("Amount"),0));
		recevblsJson.put(JSON_PROP_RECEIVABLE,new JSONArray());*/
		recevblsJson.put(JSON_PROP_CCYCODE,XMLUtils.getValueAtXPath(receivablesElem, "./currencyCode"));
		recevblsJson.put(JSON_PROP_AMOUNT,Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(receivablesElem, "./amount"),0));
		recevblsJson.put(JSON_PROP_RECEIVABLE,new JSONArray());
		
		
		for(Element receivable:XMLUtils.getElementsAtXPath(receivablesElem,"./receivable")) {
			JSONObject recevblJson = new JSONObject();
			recevblJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(receivable, "./amount"),0));
			recevblJson.put(JSON_PROP_CCYCODE,XMLUtils.getValueAtXPath(receivable, "./currency"));
			recevblJson.put(JSON_PROP_CODE,XMLUtils.getValueAtXPath(receivable, "./code"));
			recevblsJson.append(JSON_PROP_RECEIVABLE, recevblJson);
		}
		
		totalPriceJson.put(JSON_PROP_RECEIVABLES, recevblsJson);
	}

	private static void getIncentivesJson(Element incentivesElem, JSONObject totalPriceJson) {
		JSONObject incentivesJson = new JSONObject();
		/*incentivesJson.put(JSON_PROP_CCYCODE,incentivesElem.getAttribute("CurrencyCode"));
		incentivesJson.put(JSON_PROP_AMOUNT,Utils.convertToBigDecimal(incentivesElem.getAttribute("Amount"),0));
		incentivesJson.put(JSON_PROP_INCENTIVE,new JSONArray());*/
		incentivesJson.put(JSON_PROP_CCYCODE,XMLUtils.getValueAtXPath(incentivesElem, "./currencyCode"));
		incentivesJson.put(JSON_PROP_AMOUNT,Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(incentivesElem, "./amount"),0));
		incentivesJson.put(JSON_PROP_INCENTIVE,new JSONArray());
		
		for(Element incentive:XMLUtils.getElementsAtXPath(incentivesElem,"./ota:incentive")) {
			JSONObject incentiveJson = new JSONObject();
			incentiveJson.put(JSON_PROP_AMOUNT,Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(incentive, "./amount"),0));
			incentiveJson.put(JSON_PROP_CCYCODE,XMLUtils.getValueAtXPath(incentive, "./currency") );
			incentiveJson.put(JSON_PROP_CODE, XMLUtils.getValueAtXPath(incentive, "./code"));
			incentivesJson.append(JSON_PROP_INCENTIVE, incentiveJson);
		}
		
		totalPriceJson.put(JSON_PROP_INCENTIVES, incentivesJson);
	}
	
	
	public static JSONObject getNightPriceJson(Element nightPriceElem){

		JSONObject nightPriceJson = new JSONObject();

		nightPriceJson.put(JSON_PROP_EFFECTIVEDATE, XMLUtils.getValueAtXPath(nightPriceElem, "./@EffectiveDate"));
		nightPriceJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(nightPriceElem, "./ota:Base/@CurrencyCode"));
		nightPriceJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(nightPriceElem, "./ota:Base/@AmountAfterTax"),0));
		nightPriceJson.put(JSON_PROP_TOTALTAX, getTotalTaxJson(nightPriceElem, false));

		return nightPriceJson;

	}

	private static JSONArray getOccupancyJson(Element TPA_Ext) {

		JSONArray occupancyArr=new JSONArray();
		JSONObject occupancy;

		for(Element occupancyElem:XMLUtils.getElementsAtXPath(TPA_Ext, "./ota:Occupancy"))
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

	public static String processV3(JSONObject reqJson) throws RequestProcessingException, ValidationException, InternalProcessingException {
		ServiceConfig opConfig;
		Element reqElem;
		Map<String, String> credSubTypeMap;
		try{
            opConfig = AccoConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			TrackingContext.setTrackingContext(reqJson);

			credSubTypeMap= setSupplierRequestElem(reqJson, reqElem);
		}
		catch(ValidationException x) {
			throw x;
		}
		catch(Exception x) {
			throw new RequestProcessingException(x);
		}
		try {
			Element resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			JSONObject resJson = getSupplierResponseJSONV3(reqJson, resElem,credSubTypeMap);

			 try{
				MRCompanyOffers.getCompanyOffers(CommercialsOperation.Search,reqJson, resJson, OffersType.COMPANY_SEARCH_TIME);
			 }
			 catch(Exception e) {}
			 
			return resJson.toString();
		}
		catch (Exception x) {
			throw new InternalProcessingException(x);
		}
	}
}
