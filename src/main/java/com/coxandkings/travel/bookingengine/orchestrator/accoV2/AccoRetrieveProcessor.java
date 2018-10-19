package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.math.BigDecimal;

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
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AccoRetrieveProcessor implements AccoConstants {

	private static final Logger logger = LogManager.getLogger(AccoRetrieveProcessor.class);
	static final String OPERATION_NAME = "retrieve";

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException, ValidationException {
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		Element reqElem = null;
		try {
			opConfig = AccoConfig.getOperationConfig(OPERATION_NAME);
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			
			Document ownerDoc = reqElem.getOwnerDocument();
			Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem, "./accoi:RequestBody/acco:OTA_ReadRQWrapper");
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
			JSONArray multiReqArr = reqBodyJson.getJSONArray(JSON_PROP_ACCOMODATIONARR);
			Element wrapperElement, suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,"./acco:RequestHeader/com:SupplierCredentialsList");;

			for(int j=0; j < multiReqArr.length(); j++)
			{
			JSONObject roomObjectJson = multiReqArr.getJSONObject(j);
			
			validateRequestParameters(roomObjectJson,reqHdrJson);
			
			String suppID = roomObjectJson.getString(JSON_PROP_SUPPREF);
		    String prodCategSubtype = roomObjectJson.optString(JSON_PROP_ACCOSUBTYPE,"");
			ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_ACCO,prodCategSubtype, suppID);
            
			if (prodSupplier == null) {
				throw new Exception("Product supplier not found for user/client");
			}
			
            suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc,j));
			wrapperElement= (Element) blankWrapperElem.cloneNode(true);

			XMLUtils.setValueAtXPath(wrapperElement, "./acco:SupplierID", suppID);
			XMLUtils.setValueAtXPath(wrapperElement, "./acco:Sequence", String.valueOf(j));
			
			setSuppReqOTAElem(ownerDoc,XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_ReadRQ"), roomObjectJson,reqHdrJson);
			XMLUtils.insertChildNode(reqElem, "./accoi:RequestBody", wrapperElement, false);
			}
			
		}
		catch (ValidationException valx) {
			throw valx;
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
	    	try {
                System.out.println("retrieve req" + XMLTransformer.toString(reqElem));
				Element resElem = null;
				//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(),AccoConfig.getHttpHeaders(), reqElem);
				resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(),opConfig.getHttpHeaders(), reqElem);
				if (resElem == null) {
					throw new Exception("Null response received from SI");
				}
				System.out.println("retrieve res" + XMLTransformer.toString(resElem));
                JSONObject resJson = getSupplierResponseJSON(reqJson, resElem);

				return resJson.toString();
	    	}
			catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}

	private static void validateRequestParameters(JSONObject roomObjectJson, JSONObject reqHdrJson) throws ValidationException {
		AccoRequestValidator.validateSuppResId(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateAccoSubType(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateSuppRef(roomObjectJson, reqHdrJson);
	}

	private static JSONObject getSupplierResponseJSON(JSONObject reqJson, Element resElem) {
		JSONArray roomStayJsonArr;
		JSONObject accomodationInfoObj, roomStayJson;
		JSONArray accomodationInfoArr = new JSONArray();
		int sequence = 0;String sequence_str="";
		for (Element ReadRSwrapper : XMLUtils.getElementsAtXPath(resElem,"./accoi:ResponseBody/acco:OTA_ReadRSWrapper")) {
			sequence_str = XMLUtils.getValueAtXPath(ReadRSwrapper, "./acco:Sequence");
			sequence = sequence_str.isEmpty()?sequence:Integer.parseInt(sequence_str);
			roomStayJsonArr= new JSONArray();
			String startDate = null,endDate = null;
			accomodationInfoObj = new JSONObject();
			accomodationInfoObj.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(ReadRSwrapper, "./acco:SupplierID"));
        	if( XMLUtils.getValueAtXPath(ReadRSwrapper,"./ota:OTA_ResRetrieveRS/ota:ReservationsList/ota:HotelReservation/ota:UniqueID[@Type='14']/@ID").toString().isEmpty()) {
        		accomodationInfoArr.put(sequence,new JSONObject());
        		continue;
	          }
				accomodationInfoObj.put("suppierReservationId", XMLUtils.getValueAtXPath(ReadRSwrapper,"./ota:OTA_ResRetrieveRS/ota:ReservationsList/ota:HotelReservation/ota:UniqueID[@Type='14']/@ID").toString());
				accomodationInfoObj.put("supplierReferenceId", XMLUtils.getValueAtXPath(ReadRSwrapper,"./ota:OTA_ResRetrieveRS/ota:ReservationsList/ota:HotelReservation/ota:UniqueID[@Type='16']/@ID").toString());
				accomodationInfoObj.put("clientReferenceId", XMLUtils.getValueAtXPath(ReadRSwrapper,"./ota:OTA_ResRetrieveRS/ota:ReservationsList/ota:HotelReservation/ota:UniqueID[@Type='38']/@ID").toString());
				accomodationInfoObj.put("supplierCancellationId", XMLUtils.getValueAtXPath(ReadRSwrapper,"./ota:OTA_ResRetrieveRS/ota:ReservationsList/ota:HotelReservation/ota:UniqueID[@Type='15']/@ID").toString());
				accomodationInfoObj.put("status", XMLUtils.getValueAtXPath(ReadRSwrapper,"./ota:OTA_ResRetrieveRS/ota:ReservationsList/ota:HotelReservation/@ResStatus"));	
				
				int i=1;
				for (Element roomStayElem : XMLUtils.getElementsAtXPath(ReadRSwrapper,"./ota:OTA_ResRetrieveRS/ota:ReservationsList/ota:HotelReservation/ota:RoomStays/ota:RoomStay")) {
				roomStayJson = AccoSearchProcessor.getRoomStayJSON(roomStayElem);
				roomStayJson.put("roomStatus", roomStayElem.getAttribute("RoomStayStatus"));
				//roomStayJson.remove(JSON_PROP_ROOMPRICE);
				roomStayJson.remove(JSON_PROP_NIGHTLYPRICEARR);
				roomStayJson.remove(JSON_PROP_OCCUPANCYARR);
				roomStayJson.getJSONObject(JSON_PROP_ROOMINFO).remove(JSON_PROP_AVAILSTATUS);
				roomStayJson.put(JSON_PROP_SUPPROOMINDEX, roomStayElem.getAttribute("IndexNumber"));
				getPaxInfo(ReadRSwrapper,roomStayJson,i++);
				startDate=XMLUtils.getValueAtXPath(roomStayElem, "./ota:TimeSpan/@Start");
				endDate=XMLUtils.getValueAtXPath(roomStayElem, "./ota:TimeSpan/@End");
				roomStayJsonArr.put(roomStayJson);
            }
		   
								
			BigDecimal totalAmt = Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(ReadRSwrapper,"./ota:OTA_ResRetrieveRS/ota:ReservationsList/ota:HotelReservation/ota:ResGlobalInfo/ota:Total/@AmountAfterTax"), 0) ;
	    	accomodationInfoObj.put("totalPrice", totalAmt);
	    	accomodationInfoObj.put(JSON_PROP_CHKIN, startDate);
	    	accomodationInfoObj.put(JSON_PROP_CHKOUT, endDate);
			accomodationInfoObj.put(JSON_PROP_ROOMSTAYARR, roomStayJsonArr);
			

			Boolean reservedFlag = false;
			if("RoomLevel".equals(accomodationInfoObj.getString("status")) || accomodationInfoObj.getString("status").isEmpty()){
					JSONArray roomStayArr = accomodationInfoObj.getJSONArray("roomStay");
					for(int j=0;j<roomStayArr.length();j++) {
						JSONObject suppRoomObj = roomStayArr.getJSONObject(j);	
						String roomStatus = suppRoomObj.getString("roomStatus");
						if("NOTCONF".equals(roomStatus)) {
							accomodationInfoObj.put("status", roomStatus);
							break;
						}
						else if("RESERVED".equals(roomStatus)){
							accomodationInfoObj.put("status", roomStatus);
							reservedFlag=true;
						}
						else {
							if(reservedFlag != true)
							{
								accomodationInfoObj.put("status", roomStatus);
							}
	                     }
					}
			}	
			accomodationInfoArr.put(sequence,accomodationInfoObj);
			sequence++;
		}
		JSONObject resJson = new JSONObject();
		JSONObject resBodyJson = new JSONObject();
		resBodyJson.put(JSON_PROP_ACCOMODATIONARR, accomodationInfoArr);
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		return resJson;
	}
	
	private static void getPaxInfo(Element readRSwrapper, JSONObject roomStayJson,int i) {
		for (Element ResGuestElem : XMLUtils.getElementsAtXPath(readRSwrapper,"./ota:OTA_ResRetrieveRS/ota:ReservationsList/ota:HotelReservation/ota:ResGuests/ota:ResGuest")) {
				if(ResGuestElem.getAttribute("ResGuestRPH").startsWith(String.valueOf(i))) {
				    Element personNameElem = XMLUtils.getFirstElementAtXPath(ResGuestElem, "./ota:Profiles/ota:ProfileInfo/ota:Profile/ota:Customer/ota:PersonName");
			        JSONObject paxInfoObj=new JSONObject();
					paxInfoObj.put("firstName", XMLUtils.getValueAtXPath(personNameElem, "./ota:GivenName"));
					paxInfoObj.put("title", XMLUtils.getValueAtXPath(personNameElem, "./ota:NamePrefix"));
					paxInfoObj.put("surname", XMLUtils.getValueAtXPath(personNameElem, "./ota:Surname"));
					roomStayJson.append(JSON_PROP_PAXINFOARR, paxInfoObj);
				}
		}
		
	}

	private static void setSuppReqOTAElem(Document ownerDoc, Element readElem, JSONObject roomObjectJson,JSONObject reqHdrJson) {
		        Element uniqueId = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
				uniqueId.setAttribute("ID", roomObjectJson.getString("supplierReservationId"));
				uniqueId.setAttribute("Type", "14");
				readElem.appendChild(uniqueId);
         }

	

}
