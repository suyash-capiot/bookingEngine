package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.enums.AccoSubType;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AccoGetPoliciesProcessor implements AccoConstants{

	private static final Logger logger = LogManager.getLogger(AccoGetPoliciesProcessor.class);
	static final String OPERATION_NAME = "getPolicies";
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		Element reqElem = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		try {
		    opConfig = AccoConfig.getOperationConfig(OPERATION_NAME);
		    reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		    
		    setSupplierRequestElem(reqJson, reqElem);
		    
		} catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		try {
			//Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AccoConfig.getHttpHeaders(), reqElem);
			Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			JSONObject resJson = getSupplierResponseJSON(reqJson, resElem);
			return resJson.toString() ;

		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}

		
	}

	private static void setSupplierRequestElem(JSONObject reqJson, Element reqElem) throws Exception {
		
		Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem, "./accoi:RequestBody/acco:OTA_HotelGetCancellationPolicyRQWrapper");
	    XMLUtils.removeNode(blankWrapperElem);
	    
	    Document ownerDoc = reqElem.getOwnerDocument();
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
		
		JSONArray multiReqArr = reqBodyJson.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		Element wrapperElement,suppCredsListElem= XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader/com:SupplierCredentialsList");
		JSONObject roomObjectJson;
		String suppID;
		ProductSupplier prodSupplier;
		AccoSubType prodCategSubtype;
		for (int j=0; j < multiReqArr.length(); j++) {
			roomObjectJson =   multiReqArr.getJSONObject(j);
			suppID = roomObjectJson.getString(JSON_PROP_SUPPREF);
			prodCategSubtype = AccoSubType.forString(roomObjectJson.optString(JSON_PROP_ACCOSUBTYPE));
			prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_ACCO,prodCategSubtype!=null?prodCategSubtype.toString():"", suppID);

			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}
			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc,j));
			wrapperElement= (Element) blankWrapperElem.cloneNode(true);

			XMLUtils.setValueAtXPath(wrapperElement, "./acco:SupplierID",suppID);
			XMLUtils.setValueAtXPath(wrapperElement, "./acco:Sequence", String.valueOf(j));
			setSuppReqOTAElem(ownerDoc, XMLUtils.getFirstElementAtXPath(wrapperElement,"./ota:OTA_HotelGetCancellationPolicyRQ"), roomObjectJson,reqHdrJson);
			XMLUtils.insertChildNode(reqElem, "./accoi:RequestBody", wrapperElement, false);
		}
		
	}

	private static JSONObject getSupplierResponseJSON(JSONObject reqJson, Element resElem) {
		JSONObject policiesJsonObj,policyApplicability,policyCharges;
		JSONObject resBodyJson = new JSONObject();
		JSONArray accArr=new JSONArray();
		
		int sequence = 0;String sequence_str="";
		for(Element wrapperElem:XMLUtils.getElementsAtXPath(resElem, "./accoi:ResponseBody/acco:OTA_HotelGetCancellationPolicyRSWrapper")) {
			sequence_str = XMLUtils.getValueAtXPath(wrapperElem, "./acco:Sequence");
			sequence = sequence_str.isEmpty()?sequence:Integer.parseInt(sequence_str);
			JSONObject accInfoJson=new JSONObject();
			JSONArray policiesArr=new JSONArray();
			accInfoJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(wrapperElem,"./acco:SupplierID"));
			for(Element policiesElem:XMLUtils.getElementsAtXPath(wrapperElem, "./ota:OTA_HotelGetCancellationPolicyRS/ota:Policies/ota:Policy")) {
				policiesJsonObj=new JSONObject();
			    policyApplicability=new JSONObject();
				policyCharges=new JSONObject();
				
				policyApplicability.put("timeUnit", XMLUtils.getValueAtXPath(XMLUtils.getFirstElementAtXPath(policiesElem, "./ota:Timelines"), "./@Unit"));
				policyApplicability.put("from",XMLUtils.getValueAtXPath(XMLUtils.getFirstElementAtXPath(policiesElem, "./ota:Timelines"), "./@FromValue"));
				policyApplicability.put("to",XMLUtils.getValueAtXPath(XMLUtils.getFirstElementAtXPath(policiesElem, "./ota:Timelines"), "./@ToValue"));
				
				policyCharges.put("chargeType", XMLUtils.getValueAtXPath(policiesElem, "./ota:ChargeType"));
				policyCharges.put("chargeValue", XMLUtils.getValueAtXPath(policiesElem, "./ota:ChargeRate"));
				policyCharges.put("currencyCode", XMLUtils.getValueAtXPath(policiesElem, "./ota:Currency"));
				
				policiesJsonObj.put("policyType", policiesElem.getAttribute("PolicyType"));
				policiesJsonObj.put("policyApplicability", policyApplicability);
				policiesJsonObj.put("policyCharges", policyCharges);
				policiesJsonObj.put("policyDescription", XMLUtils.getValueAtXPath(policiesElem, "./ota:Text"));
				policiesArr.put(policiesJsonObj);
			}
			
			accInfoJson.put("policies", policiesArr);
			accArr.put(sequence,accInfoJson);
			sequence++;
		}
		resBodyJson.put(JSON_PROP_ACCOMODATIONARR, accArr);
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
        return resJson;
	}

	private static void setSuppReqOTAElem(Document ownerDoc, Element reqOTAElem, JSONObject roomObjectJson,
			JSONObject reqHdrJson) {
		JSONObject roomInfoJsonObj = roomObjectJson.getJSONObject(JSON_PROP_ROOMINFO);
		XMLUtils.setValueAtXPath(reqOTAElem,"./ota:HotelCode",roomInfoJsonObj.getJSONObject(JSON_PROP_HOTELINFO).getString(JSON_PROP_HOTELCODE));
		XMLUtils.setValueAtXPath(reqOTAElem,"./ota:HotelCodeContext",roomInfoJsonObj.getJSONObject(JSON_PROP_HOTELINFO).getString(JSON_PROP_HOTELREF));
		XMLUtils.setValueAtXPath(reqOTAElem,"./ota:CheckInDate",roomObjectJson.getString(JSON_PROP_CHKIN));
		XMLUtils.setValueAtXPath(reqOTAElem,"./ota:CheckOutDate",roomObjectJson.getString(JSON_PROP_CHKOUT));
		XMLUtils.setValueAtXPath(reqOTAElem,"./ota:CountryCode",roomObjectJson.getString(JSON_PROP_COUNTRYCODE));
		XMLUtils.setValueAtXPath(reqOTAElem,"./ota:HotelCityCode",roomObjectJson.getString(JSON_PROP_CITYCODE));
		XMLUtils.setValueAtXPath(reqOTAElem,"./ota:NationalityID",roomObjectJson.getString(JSON_PROP_PAXNATIONALITY));
		getRoomInfoElem(ownerDoc,XMLUtils.getFirstElementAtXPath(reqOTAElem,"./ota:RoomInfo"),roomInfoJsonObj,roomObjectJson);
		
	}


	private static void getRoomInfoElem(Document ownerDoc,Element roomInfoElem, JSONObject roomInfoJsonObj,
			JSONObject roomObjectJson) {
		roomInfoElem.setAttribute("RoomTypeCode", roomInfoJsonObj.getJSONObject(JSON_PROP_ROOMTYPEINFO).getString(JSON_PROP_ROOMTYPECODE));
		roomInfoElem.setAttribute("RoomID", roomInfoJsonObj.getJSONObject(JSON_PROP_ROOMTYPEINFO).getString(JSON_PROP_ROOMREF));
		roomInfoElem.setAttribute("RoomCategoryID", roomInfoJsonObj.getJSONObject(JSON_PROP_ROOMTYPEINFO).getString(JSON_PROP_ROOMCATEGCODE));
		roomInfoElem.setAttribute("RoomCategoryName", roomInfoJsonObj.getJSONObject(JSON_PROP_ROOMTYPEINFO).getString(JSON_PROP_ROOMCATEGNAME));
		roomInfoElem.setAttribute("MealId", roomInfoJsonObj.getJSONObject(JSON_PROP_MEALINFO).getString(JSON_PROP_MEALCODE));
		roomInfoElem.setAttribute("RatePlanCode", roomInfoJsonObj.getJSONObject(JSON_PROP_RATEPLANINFO).getString(JSON_PROP_RATEPLANCODE));
		XMLUtils.setValueAtXPath(roomInfoElem,  "./ota:AdultNum", String.valueOf(roomObjectJson.getInt(JSON_PROP_ADTCNT)));
	    Element chldAges = XMLUtils.getFirstElementAtXPath(roomInfoElem, "./ota:ChildAges");
	    
	    for(int i=0;i<roomObjectJson.getJSONArray(JSON_PROP_CHDAGESARR).length();i++) {
		Element chldAge = ownerDoc.createElementNS(NS_OTA, "ota:ChildAge");
		XMLUtils.insertChildNode(roomInfoElem, "./ota:ChildAges", chldAge,true);
		XMLUtils.setValueAtXPath(chldAges,  "./ota:ChildAge", String.valueOf(roomObjectJson.getJSONArray(JSON_PROP_CHDAGESARR).getInt(i)));
	    }
	    
	    for(int i=0;i<roomObjectJson.getJSONObject(JSON_PROP_ROOMINFO).getJSONArray(JSON_PROP_REFERENCESARR).length();i++) {
	    	Element ref = ownerDoc.createElementNS(NS_OTA, "ota:Reference");
	    	ref.setAttribute("Type", roomObjectJson.getJSONObject(JSON_PROP_ROOMINFO).getJSONArray(JSON_PROP_REFERENCESARR).getJSONObject(i).getString(JSON_PROP_REFCODE));
	    	ref.setAttribute("ID", roomObjectJson.getJSONObject(JSON_PROP_ROOMINFO).getJSONArray(JSON_PROP_REFERENCESARR).getJSONObject(i).getString(JSON_PROP_REFVALUE));
	    	XMLUtils.insertChildNode(roomInfoElem, "./ota:References", ref,true);
	    	
	    }
	 }

	
}
