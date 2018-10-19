package com.coxandkings.travel.bookingengine.orchestrator.acco;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.TaxEngine;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AccoPriceProcessor implements AccoConstants {

	private static final Logger logger = LogManager.getLogger(AccoPriceProcessor.class);
	//static final String OPERATION_NAME = "price";

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
			
			validateRequestParameters(roomObjectJson,reqHdrJson);
			
			suppID = roomObjectJson.getString(JSON_PROP_SUPPREF);
			//prodCategSubtype = AccoSubType.forString(roomObjectJson.optString(JSON_PROP_ACCOSUBTYPE));
			//prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_ACCO,prodCategSubtype!=null?prodCategSubtype.toString():"", suppID);
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

	static void validateRequestParameters(JSONObject roomObjectJson, JSONObject reqHdrJson) throws ValidationException, ParseException {
		AccoRequestValidator.validateAccoSubType(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateSuppRef(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateCityCode(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateCountryCode(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateDates(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validatePaxNationality(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateRoomConfig(roomObjectJson, reqHdrJson);
		
	}

	public static void setSuppReqOTAElem(Document ownerDoc,Element reqOTAElem,JSONObject reqParamJson,JSONObject reqHdrJSON) {

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
			XMLUtils.insertChildNode(baseElem,"./ota:RoomStayCandidates",AccoSearchProcessor.getRoomStayCandidateElem(ownerDoc,(JSONObject) roomJson, i+1),false);
		}

	}

	//***********************************SI JSON TO XML FOR REQUEST BODY ENDS HERE**************************************//

	//***********************************SI XML TO JSON FOR RESPONSE STARTS HERE**************************************//

	public static JSONObject getSupplierResponseJSON(JSONObject reqJson,Element resElem){

		JSONObject resBodyJson = new JSONObject();
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		JSONArray multiResArr = new JSONArray();
		JSONArray multiReqArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		resBodyJson.put(JSON_PROP_ACCOMODATIONARR, multiResArr);
		int sequence = 0;String sequence_str="";
		JSONArray roomStayJsonArr;
		JSONObject roomStayJson;

		for(Element wrapperElem:XMLUtils.getElementsAtXPath(resElem, "./accoi:ResponseBody/acco:OTA_HotelAvailRSWrapper")) {
			sequence_str = XMLUtils.getValueAtXPath(wrapperElem, "./acco:Sequence");
			sequence = sequence_str.isEmpty()?sequence:Integer.parseInt(sequence_str);
			
			validateSuppRes(wrapperElem,resBodyJson,multiResArr,sequence);
		
			if (resBodyJson.optJSONArray(JSON_PROP_ACCOMODATIONARR).optJSONObject(sequence)!=null) {
				if(resBodyJson.optJSONArray(JSON_PROP_ACCOMODATIONARR).optJSONObject(sequence).has("errorMsg")) {
					resBodyJson.optJSONArray(JSON_PROP_ACCOMODATIONARR).optJSONObject(sequence).put(JSON_PROP_ROOMSTAYARR, new JSONArray());
					continue;	
				}	
			}
			roomStayJsonArr = new JSONArray();
			for (Element roomStayElem : XMLUtils.getElementsAtXPath(wrapperElem, "./ota:OTA_HotelAvailRS/ota:RoomStays/ota:RoomStay")) {
				roomStayJson = AccoSearchProcessor.getRoomStayJSON(roomStayElem);
				roomStayJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(wrapperElem, "./acco:SupplierID"));
				roomStayJson.put(JSON_PROP_ACCOSUBTYPE, multiReqArr.getJSONObject(sequence).getString(JSON_PROP_ACCOSUBTYPE));
				if(AccoSearchProcessor.filterRoomStay(roomStayJson)) {
					logger.warn("RoomStay has been filtered for key %s",AccoRepriceProcessor.getRedisKeyForRoomStay(roomStayJson.getJSONObject(JSON_PROP_ROOMINFO)));
					continue;
				}
				roomStayJsonArr.put(roomStayJson);
			}
			multiResArr.put(sequence, (new JSONObject()).put(JSON_PROP_ROOMSTAYARR, roomStayJsonArr));
			sequence++;
		}

		return resJson;

	}

	//***********************************SI XML TO JSON FOR RESPONSE ENDS HERE**************************************//

	private static void validateSuppRes(Element wrapperElem, JSONObject resBodyJson,JSONArray multiResArr,int sequence) {
		
		for (Element errorElem : XMLUtils.getElementsAtXPath(wrapperElem, "./ota:OTA_HotelAvailRS/ota:Errors/ota:Error")) {
			JSONObject accoInfoJson = new JSONObject();
			accoInfoJson.put("errorMsg", XMLUtils.getValueAtXPath(errorElem, "./@ShortText"));
			multiResArr.put(sequence,accoInfoJson);
		}
		for (Element errorElem1 : XMLUtils.getElementsAtXPath(wrapperElem, "./acco:ErrorList/com:Error")) {
			JSONObject accoInfoJson = new JSONObject();
			accoInfoJson.put("errorMsg", XMLUtils.getValueAtXPath(errorElem1, "./com:ErrorMsg"));
			multiResArr.put(sequence,accoInfoJson);
		}
	}

	public static String process(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException, ValidationException {
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		Element reqElem = null;
		UserContext usrCtx = UserContext.getUserContextForSession(reqJson.getJSONObject(JSON_PROP_REQHEADER));
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
			Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(),opConfig.getHttpHeaders(),opConfig.getHttpMethod(),opConfig.getServiceTimeoutMillis(), reqElem);
		//	Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AccoConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			JSONObject resJson = getSupplierResponseJSON(reqJson, resElem);

			Map<Integer,String> SI2BRMSRoomMap= new HashMap<Integer,String>();
			JSONObject resSupplierComJson =  SupplierCommercials.getSupplierCommercials(CommercialsOperation.Search,reqJson, resJson, SI2BRMSRoomMap);
			JSONObject resClientComJson = ClientCommercials.getClientCommercials(reqJson,resSupplierComJson);

			AccoSearchProcessor.calculatePrices(reqJson,resJson,resClientComJson,resSupplierComJson,SI2BRMSRoomMap,false);
			
			// Apply company offers
			try{
				CompanyOffers.getCompanyOffers(CommercialsOperation.Search,reqJson, resJson, OffersType.COMPANY_SEARCH_TIME);
			}
			catch(Exception e) {}

			TaxEngine.getCompanyTaxes(reqJson, resJson);
			
			try {
	        	   ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
	        	   CommercialCacheProcessor.updateInCache(PRODUCT_ACCO, clInfo[clInfo.length - 1].getCommercialsEntityId(),resJson,reqJson);
             }
             catch (Exception x) {
     	           logger.info("An exception was received while pushing price results in commercial cache", x);
             }
			
			return resJson.toString();

		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}

	}


}
