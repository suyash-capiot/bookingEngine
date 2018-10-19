package com.coxandkings.travel.bookingengine.orchestrator.acco;

import java.math.BigDecimal;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.DBServiceConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.acco.enums.AccoSubType;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AccoModifyProcessor implements AccoConstants {

	private static final Logger logger = LogManager.getLogger(AccoModifyProcessor.class);
	static final String OPERATION_NAME = "modify";

	// ***********************************SI JSON TO XML FOR REQUEST BODY STARTS HERE**************************************//
	public static void setSupplierRequestElem(JSONObject reqJson, Element reqElem) throws Exception,ValidationException{

		Document ownerDoc = reqElem.getOwnerDocument();
		Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem,"./accoi:RequestBody/acco:OTA_HotelResModifyRQWrapper");
		XMLUtils.removeNode(blankWrapperElem);
		
		JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null)? reqJson.optJSONObject(JSON_PROP_REQHEADER):new JSONObject();
		JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null)? reqJson.optJSONObject(JSON_PROP_REQBODY): new JSONObject();

		AccoRequestValidator.validateBookId(reqBodyJson, reqHdrJson);
		
		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);

		JSONObject clientContext=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientMrkt = clientContext.getString(JSON_PROP_CLIENTMARKET);
		String clientID = clientContext.getString(JSON_PROP_CLIENTID);
		
		AccoSearchProcessor.createSuppReqHdrElem(XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader"),sessionID, transactionID, userID,clientMrkt,clientID);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		String suppID;
		ProductSupplier prodSupplier;
		JSONArray multiReqArr = reqBodyJson.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONObject roomObjectJson;
		Element wrapperElement, suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,"./acco:RequestHeader/com:SupplierCredentialsList");
		AccoSubType prodCategSubtype;
		
		for (int j = 0; j < multiReqArr.length(); j++) {
		    roomObjectJson = multiReqArr.getJSONObject(j);
            
		    validateRequestParameters(roomObjectJson,reqHdrJson);
            
		    String modType = roomObjectJson.getString("modificationType");
           
		    //Call opsTodo
            ToDoTaskSubType todoSubType;
            ToDoTaskName todoTaskName=ToDoTaskName.AMEND;
            if("FULLCANCELLATION".equals(modType))
            	todoSubType=ToDoTaskSubType.ORDER;
                todoTaskName=ToDoTaskName.CANCEL;
            if(modType.endsWith("PASSENGER"))
            	todoSubType=ToDoTaskSubType.PASSENGER;
            else
            	todoSubType=ToDoTaskSubType.ORDER;
            	
            OperationsToDoProcessor.callOperationTodo(todoTaskName, ToDoTaskPriority.MEDIUM, todoSubType,roomObjectJson.getString("orderID"), reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID),reqHdrJson,"");
            
			suppID = roomObjectJson.getString(JSON_PROP_SUPPREF);
			prodCategSubtype = AccoSubType.forString(roomObjectJson.optString(JSON_PROP_ACCOSUBTYPE));
			prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_ACCO,prodCategSubtype != null ? prodCategSubtype.toString() : "", suppID);

			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}

			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, j));
			wrapperElement = (Element) blankWrapperElem.cloneNode(true);

			XMLUtils.setValueAtXPath(wrapperElement, "./acco:SupplierID", suppID);
			XMLUtils.setValueAtXPath(wrapperElement, "./acco:Sequence", String.valueOf(j));
			setSuppReqOTAElem(ownerDoc, XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_HotelResModifyRQ"),roomObjectJson);

			XMLUtils.insertChildNode(reqElem, "./accoi:RequestBody", wrapperElement, false);
		}
	}

	private static void validateRequestParameters(JSONObject roomObjectJson, JSONObject reqHdrJson) throws ValidationException {
		AccoRequestValidator.validateSuppResId(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateAccoSubType(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateSuppRef(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateSuppIds(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateModType(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validatePaxInfo(roomObjectJson, reqHdrJson);
		AccoRequestValidator.validateOrderAndRoomandPaxId(roomObjectJson, reqHdrJson);
	}

	public static void setSuppReqOTAElem(Document ownerDoc, Element reqOTAElem, JSONObject roomObjectJson) {

		Element hotelResModifyElem = XMLUtils.getFirstElementAtXPath(reqOTAElem,"./ota:HotelResModifies/ota:HotelResModify");
		Element roomStaysElem = (Element) XMLUtils.getFirstElementAtXPath(hotelResModifyElem, "./ota:RoomStays");
		Element resGuestsElem = XMLUtils.getFirstElementAtXPath(hotelResModifyElem, "./ota:ResGuests");
		Element roomStayElem = ownerDoc.createElementNS(NS_OTA, "ota:RoomStay");
		Element guestCountsElem = ownerDoc.createElementNS(NS_OTA, "ota:GuestCounts");
		JSONObject roomInfoJson = roomObjectJson.getJSONObject(JSON_PROP_ROOMINFO);
		String cityCode = roomObjectJson.getString(JSON_PROP_CITYCODE);
		String countryCode = roomObjectJson.getString(JSON_PROP_COUNTRYCODE);
		String chkIn = roomObjectJson.getString(JSON_PROP_CHKIN);
		String chkOut = roomObjectJson.getString(JSON_PROP_CHKOUT);
		JSONArray paxArr = roomObjectJson.getJSONArray(JSON_PROP_PAXINFOARR);

		roomStayElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:RoomTypes")).appendChild(AccoBookProcessor.getRoomTypeElem(ownerDoc, roomInfoJson));
		roomStayElem.appendChild(ownerDoc.createElementNS(NS_OTA, "ota:RatePlans")).appendChild(AccoBookProcessor.getRateElem(ownerDoc, roomInfoJson));
		for (int k = 0; k < paxArr.length(); k++) {
			AccoBookProcessor.addGuestDetails(ownerDoc, (JSONObject) paxArr.get(k), resGuestsElem, guestCountsElem, k);
		}
		roomStayElem.appendChild(guestCountsElem);
		roomStayElem.appendChild(AccoBookProcessor.getTimeSpanElem(ownerDoc, chkIn, chkOut));
		// roomStayElem.appendChild(totalElem);
		roomStayElem.appendChild(AccoBookProcessor.getHotelElem(ownerDoc, roomInfoJson, cityCode, countryCode));
		for (Object reference : roomInfoJson.getJSONArray(JSON_PROP_REFERENCESARR)) {
			roomStayElem.appendChild(AccoBookProcessor.getReferenceElem(ownerDoc, (JSONObject) reference));
		}
		roomStayElem.setAttribute("RPH", String.valueOf(roomInfoJson.optInt(JSON_PROP_ROOMINDEX)));
		roomStayElem.setAttribute("RoomStayStatus", (roomInfoJson.getString(JSON_PROP_AVAILSTATUS)));
		roomStayElem.setAttribute("IndexNumber", (roomObjectJson.getString("supplierRoomIndex")));

		roomStaysElem.appendChild(roomStayElem);
		hotelResModifyElem.appendChild(roomStaysElem);
		hotelResModifyElem.appendChild(resGuestsElem);

		Element uniqueIdElem = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
		uniqueIdElem.setAttribute("ID", roomObjectJson.getString("supplierReservationId"));
		uniqueIdElem.setAttribute("Type", "14");
		XMLUtils.insertChildNode(hotelResModifyElem, ".", uniqueIdElem, true);

		uniqueIdElem.setAttribute("ID", roomObjectJson.getString("supplierReferenceId"));
		uniqueIdElem.setAttribute("Type", "16");
		XMLUtils.insertChildNode(hotelResModifyElem, ".", uniqueIdElem, true);

		uniqueIdElem.setAttribute("ID", roomObjectJson.getString("clientReferenceId"));
		uniqueIdElem.setAttribute("Type", "38");
		XMLUtils.insertChildNode(hotelResModifyElem, ".", uniqueIdElem, true);

		uniqueIdElem.setAttribute("ID", roomObjectJson.getString("supplierCancellationId"));
		uniqueIdElem.setAttribute("Type", "15");
		XMLUtils.insertChildNode(hotelResModifyElem, ".", uniqueIdElem, true);

		/*
		 * for(Object bookRefJson:roomObjectJson.getJSONArray("supplierBookReferences"))
		 * { XMLUtils.insertChildNode(hotelResModifyElem, ".", getUniqueIdElem(ownerDoc,
		 * (JSONObject) bookRefJson), true); }
		 */
		// TODO:set some enum
		reqOTAElem.setAttribute("Target", roomObjectJson.getString("modificationType"));
	}

	public static Element getUniqueIdElem(Document ownerDoc, JSONObject bookRefJson) {

		Element uniqueIdElem = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");

		uniqueIdElem.setAttribute("ID", bookRefJson.getString(JSON_PROP_REFVALUE));
		uniqueIdElem.setAttribute("Type", bookRefJson.getString(JSON_PROP_REFCODE));

		return uniqueIdElem;
	}
	// ***********************************SI JSON TO XML FOR REQUEST BODY ENDS HERE**************************************//

	public static String process(JSONObject reqJson) throws ValidationException, RequestProcessingException, InternalProcessingException {
		Element reqElem;
		//OperationConfig opConfig;
		ServiceConfig opConfig;
		JSONObject retrieveResJson;
		try {
			opConfig = AccoConfig.getOperationConfig(OPERATION_NAME);
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			TrackingContext.setTrackingContext(reqJson);
			String retrieveResStr = AccoRetrieveProcessor.process(reqJson);
			retrieveResJson = new JSONObject(retrieveResStr);
			
			setSupplierRequestElem(reqJson, reqElem);
			// This part is to take Information from Database.will WEM provide us with BookId?
						
           /* String uri ="http://10.24.2.248:8080/BookingEngineDBSource/BookingService/getBooking/123456";
			RestTemplate restTemplate = new RestTemplate(); String result =
			restTemplate.getForObject(uri, String.class); System.out.println(result);*/
		}
		catch (ValidationException valx) {
			throw valx;
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
			//return "{\"status\": \"ERROR\"}";
		}
			
        try {
			JSONObject kafkaReqJson = getKakfaAmendRequest(reqJson);

			//System.out.println("amend RQ" + XMLTransformer.toString(reqElem));
			//Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AccoConfig.getHttpHeaders(), reqElem);
			Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			//System.out.println("amend RS" + XMLTransformer.toString(resElem));
			JSONObject resJson = getSupplierResponseJSON(retrieveResJson, reqJson, resElem);
			kafkaResponse(resJson, kafkaReqJson,reqJson);
			calculateTotalCharges(reqJson,resJson);
			
			return resJson.toString();
		} 
		
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new InternalProcessingException(x);
			//return "{\"status\": \"ERROR\"}";
		}
	}

	private static void calculateTotalCharges(JSONObject reqJson,JSONObject resJson) {
		String clientCcy = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		String mrkt =  reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		if(resJson.getJSONObject(JSON_PROP_RESBODY).optString("errorMsg").isEmpty()) {
		JSONArray multiResJsonArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		
		for(int i=0;i<multiResJsonArr.length();i++) {
			JSONObject roomChrgsJson = multiResJsonArr.getJSONObject(i);
			String cmpnyCcyCode = (String) roomChrgsJson.remove("companyChargesCurrencyCode");
			String suppCcyCode = (String) roomChrgsJson.remove("supplierChargesCurrencyCode");
			String cmpnyChrgeType = (String) roomChrgsJson.remove("companyChargeType");
			String suppChrgeType = (String) roomChrgsJson.remove("supplierChargeType");
			BigDecimal cmpnyChrges = (BigDecimal) roomChrgsJson.remove("companyCharges");
			BigDecimal suppChrges =  (BigDecimal) roomChrgsJson.remove("supplierCharges");
			
			//will brms send the type of company chrge or signed amount
			//assuming it will be a signed amount
			BigDecimal modifChrgs=new BigDecimal(0);
			if("Refund".equals(suppChrgeType)) {
			modifChrgs = cmpnyChrges.subtract(suppChrges.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcy, mrkt)));
			}
			else {
			modifChrgs = cmpnyChrges.add(suppChrges.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcy, mrkt)));	
			}
			
			roomChrgsJson.put("companyCharges",modifChrgs);
			roomChrgsJson.put("companyChargesCurrencyCode",clientCcy);
			roomChrgsJson.put(JSON_PROP_SUPPREF,reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR).getJSONObject(i).getString(JSON_PROP_SUPPREF));
		}
		}
	}

	private static void kafkaResponse(JSONObject resJson, JSONObject kafkareqJson,JSONObject reqJson) throws Exception {
		if (resJson.getJSONObject(JSON_PROP_RESBODY).optString("errorMsg").isEmpty() ) {
		    JSONObject amdResBdy = resJson.getJSONObject(JSON_PROP_RESBODY);
			JSONArray amdResAccInfoArr = amdResBdy.getJSONArray(JSON_PROP_ACCOMODATIONARR);
			
			for(int i=0;i<amdResAccInfoArr.length();i++) {
				
			JSONObject accInfoObj = amdResAccInfoArr.getJSONObject(i);
			JSONObject kafkaresJson = new JSONObject();
			JSONObject kafkaresBody = new JSONObject();
			
			kafkaresJson.put(JSON_PROP_RESHEADER, kafkareqJson.getJSONObject(JSON_PROP_REQHEADER));
			kafkaresJson.put(JSON_PROP_RESBODY, kafkaresBody);
			kafkaresBody.put(JSON_PROP_PROD, "ACCO");
			kafkaresBody.put("operation", "amend");
			kafkaresBody.put("companyCharges", accInfoObj.getInt("companyCharges"));
			kafkaresBody.put("companyChargesCurrencyCode", accInfoObj.getString("companyChargesCurrencyCode"));
			kafkaresBody.put("supplierChargesCurrencyCode", accInfoObj.getString("supplierChargesCurrencyCode"));
			kafkaresBody.put("type", kafkareqJson.getJSONObject(JSON_PROP_REQBODY).getString("type"));
			kafkaresBody.put("orderID", kafkareqJson.getJSONObject(JSON_PROP_REQBODY).getString("orderID"));
			kafkaresBody.put("entityIDs", kafkareqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray("entityIDs"));
			kafkaresBody.put("requestType", kafkareqJson.getJSONObject(JSON_PROP_REQBODY).getString("requestType"));
			kafkaresBody.put("entityName", kafkareqJson.getJSONObject(JSON_PROP_REQBODY).getString("entityName"));
			
			//if charge type is refund then sign should be -ve
			if ("Refund".equals(accInfoObj.getString("supplierChargeType"))) {
				kafkaresBody.put("supplierCharges", accInfoObj.getBigDecimal("supplierCharges").negate());
			} 
			else {
				kafkaresBody.put("supplierCharges", accInfoObj.getBigDecimal("supplierCharges"));
			}

			//System.out.println("Amend Kafka RS " + kafkaresJson);
			
			KafkaBookProducer bookProducer = new KafkaBookProducer();
			bookProducer.runProducer(1, kafkaresJson);
			
			logger.info((String.format("%s_RQ = %s", "AMENDKAFKA",kafkaresJson)));
			}
		}

	}

	private static JSONObject getSupplierResponseJSON(JSONObject retrieveResJson, JSONObject reqJson, Element resElem) throws Exception {
		BigDecimal bookingAmt = null, amndAmt = null;
		JSONObject resJson = new JSONObject();
		JSONObject resBody = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBody);

		// If res global is there in amend response and if there is type in case of
		// tourico
		outerloop:for (Element ReadRSwrapper : XMLUtils.getElementsAtXPath(resElem,"./accoi:ResponseBody/acco:OTA_HotelResModifyRSWrapper")) {

			validateSuppRes(resBody, ReadRSwrapper);
			if (resBody.has("errorMsg")) {
				continue;
			}
			
			String cancelRoomStatus = XMLUtils.getValueAtXPath(ReadRSwrapper,
					"./ota:OTA_HotelResModifyRS/ota:HotelResModifies/ota:HotelResModify/@ResStatus");
			if (Utils.isStringNotNullAndNotEmpty(cancelRoomStatus)) {
				if (("CANCELCONF").equals(cancelRoomStatus)) {
					setNoChange(resBody);
					continue;
				}
			}

			for (Element RoomStayElem : XMLUtils.getElementsAtXPath(ReadRSwrapper,
					"./ota:OTA_HotelResModifyRS/ota:HotelResModifies/ota:HotelResModify/ota:RoomStays")) {
				String roomStayStatus = XMLUtils.getValueAtXPath(RoomStayElem, "./ota:RoomStay/@RoomStayStatus");
				if (("CANCELCONF").equals(roomStayStatus)) {
					setNoChange(resBody);
					continue outerloop;
				}
				
			}
			Element totalAmtElem = XMLUtils.getFirstElementAtXPath(ReadRSwrapper,"./ota:OTA_HotelResModifyRS/ota:HotelResModifies/ota:HotelResModify/ota:ResGlobalInfo/ota:Total");
			JSONObject chargesObj=new JSONObject();
			chargesObj.put("companyChargesCurrencyCode", "");
			chargesObj.put("companyCharges", new BigDecimal(0));
			chargesObj.put("companyChargeType", "NoChange");
			chargesObj.put("status", "SUCCESS");
			String totalAmt = XMLUtils.getValueAtXPath(totalAmtElem, "./@AmountAfterTax");
			
			if (Utils.isStringNotNullAndNotEmpty(totalAmt)) {
				chargesObj.put("supplierChargesCurrencyCode", XMLUtils.getValueAtXPath(totalAmtElem, "./@CurrencyCode"));
				amndAmt = Utils.convertToBigDecimal(totalAmt, 0);
				String type = XMLUtils.getValueAtXPath(totalAmtElem, "./@Type");
				if (!type.isEmpty()) {
					chargesObj.put("supplierChargeType", type);
					int signOfAmt = amndAmt.signum();
					if (signOfAmt > 0 || signOfAmt == 0) {
						chargesObj.put("supplierCharges", amndAmt);
					} else if (signOfAmt < 0) {
						chargesObj.put("supplierCharges", amndAmt.negate());
					}
					resBody.append(JSON_PROP_ACCOMODATIONARR, chargesObj);
					continue;
				} 
				else {
					// if resglobal is there but the modification amount is already added in the amount
					JSONArray retAccInfoArr = retrieveResJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
					for (int i = 0; i < retAccInfoArr.length(); i++) {
						bookingAmt = retAccInfoArr.getJSONObject(i).getBigDecimal("totalPrice");
					}
                    calculateSupplierCharges(resBody, amndAmt, bookingAmt,chargesObj);
					continue;
				}
			}
			// if there is no res global so we have to compare the room prices
			getAmendFromRetrieve(retrieveResJson, reqJson, resElem, resBody);
		}
		if (!resBody.has("errorMsg")) {
		String bookId = reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID);
		
		JSONObject companyPolicyRes = null;
		//JSONObject orderDetailsDB = HTTPServiceConsumer.produceJSONObjectResponse(TARGET_SYSTEM_DBSERIVCE,String.format(DBServiceConfig.getDBServiceURL(),bookId), DBServiceConfig.getHttpHeaders());
		JSONObject orderDetailsDB = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_DBSERIVCE, new URL(String.format(DBServiceConfig.getDBServiceURL(), bookId)), DBServiceConfig.getHttpHeaders(), "GET", null);  
			
		if(!orderDetailsDB.has("ErrorMsg")) {
		   JSONArray productsArr = orderDetailsDB.optJSONObject(JSON_PROP_RESBODY).optJSONArray(JSON_PROP_PRODS);
		   //Applying Company Policy
		   companyPolicyRes = AccoAmClCompanyPolicy.getAmClCompanyPolicy(CommercialsOperation.Ammend, reqJson, resJson, productsArr);
		   appendingCompanyPolicyChargesInRes(resBody, companyPolicyRes);
		}
		
		else {
			logger.debug(String.format("Unable to create CompanyPolicy Request as no Bookings found for bookID: %s ", bookId));
		}
	}
		
   	    return resJson;
    }
	
	
	private static void appendingCompanyPolicyChargesInRes(JSONObject resBody, JSONObject companyPolicyRes) {
		
		if(companyPolicyRes==null)	
			return;
		
    	if (BRMS_STATUS_TYPE_FAILURE.equals(companyPolicyRes.getString(JSON_PROP_TYPE))) {
    		logger.error(String.format("A failure response was received from Company Policy calculation engine: %s", companyPolicyRes.toString()));
    		return;
    	}
    	
    	JSONArray briArr = getCompanyPoliciesBusinessRuleIntakeJSONArray(companyPolicyRes);
    	
		JSONArray resAccInfoArr = resBody.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		for(int j=0;j<resAccInfoArr.length();j++) {
			JSONObject accInfoObj = resAccInfoArr.getJSONObject(j);
			JSONObject companyPolicyDtls = briArr.getJSONObject(j).optJSONObject("companyPolicyDetails");
			BigDecimal companyChrg = companyPolicyDtls!=null ? companyPolicyDtls.getBigDecimal("policyCharges") : BigDecimal.ZERO;
			
			accInfoObj.put("companyCharges", companyChrg);
		
		}
	}

	private static JSONArray getCompanyPoliciesBusinessRuleIntakeJSONArray(JSONObject companyPolicyRes) {
		return companyPolicyRes.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.acco_companypolicy.Root").getJSONArray("businessRuleIntake");
	}

	private static void calculateSupplierCharges(JSONObject resBody, BigDecimal amndAmt, BigDecimal bookingAmt,JSONObject chargesObj) {
		BigDecimal supplierChrge = amndAmt.subtract(bookingAmt);
		int signOfAmt = supplierChrge.signum();
		if (signOfAmt > 0) {
			chargesObj.put("supplierCharges", supplierChrge);
			chargesObj.put("supplierChargeType", "Charge");
		} else if (signOfAmt < 0) {
			chargesObj.put("supplierCharges", supplierChrge.negate());
			chargesObj.put("supplierChargeType", "Refund");
		} else {
			chargesObj.put("supplierCharges", supplierChrge);
			chargesObj.put("supplierChargeType", "NoChange");
		}
		resBody.append(JSON_PROP_ACCOMODATIONARR, chargesObj);
	}

	private static void setNoChange(JSONObject resBody) {
		JSONObject chargesObj=new JSONObject();
		chargesObj.put("companyChargesCurrencyCode", "");
		chargesObj.put("companyCharges", new BigDecimal(0));
		chargesObj.put("companyChargeType", "");
		chargesObj.put("supplierChargesCurrencyCode", "");
		chargesObj.put("supplierChargeType", "NoChange");
		chargesObj.put("supplierCharges", new BigDecimal(0));
		chargesObj.put("status", "SUCCESS");
		resBody.append(JSON_PROP_ACCOMODATIONARR, chargesObj);

	}

	private static void validateSuppRes(JSONObject resBody, Element ReadRSwrapper) {
		String ErrorMsg = XMLUtils.getValueAtXPath(ReadRSwrapper, "./acco:ErrorList/com:Error/com:ErrorMsg");
		if (Utils.isStringNotNullAndNotEmpty(ErrorMsg)) {
			resBody.put("status", "FAILURE");
			resBody.put("errorMsg", ErrorMsg);
			resBody.put("errorCode",
					XMLUtils.getValueAtXPath(ReadRSwrapper, "./acco:ErrorList/com:Error/com:ErrorCode"));
		}
		String ErrorMsg1 = XMLUtils.getValueAtXPath(ReadRSwrapper,
				"./ota:OTA_HotelResModifyRS/ota:Errors/ota:Error/@ShortText");
		if (Utils.isStringNotNullAndNotEmpty(ErrorMsg1)) {
			resBody.put("status", "FAILURE");
			resBody.put("errorMsg", ErrorMsg1);

		}
		String ErrorMsg2 = XMLUtils.getValueAtXPath(ReadRSwrapper,
				"./ota:OTA_HotelResModifyRS/ota:Errors/ota:Error/@Status");
		if (Utils.isStringNotNullAndNotEmpty(ErrorMsg2)) {
			resBody.put("status", "FAILURE");
			resBody.put("errorMsg", ErrorMsg2);

		}

	}

	private static JSONObject getAmendFromRetrieve(JSONObject retrieveResJson, JSONObject reqJson, Element resElem,
			JSONObject resBody) {
		String amndRqSuppRoomIdx = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR).getJSONObject(0).getString(JSON_PROP_SUPPROOMINDEX);// roomindex specified in amend req
		BigDecimal bookingAmt = null, amendAmt = null;
		JSONArray retAccInfoArr = retrieveResJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		
		for (int i = 0; i < retAccInfoArr.length(); i++) {
			JSONArray roomStay = retAccInfoArr.getJSONObject(i).getJSONArray(JSON_PROP_ROOMSTAYARR);
			for (int j = 0; j < roomStay.length(); j++) {
				JSONObject roomObj = roomStay.getJSONObject(j);
				if (!amndRqSuppRoomIdx.equals(roomObj.getString(JSON_PROP_SUPPROOMINDEX))) {
					continue;
				}
				// the old amount fetched during retrieve
				bookingAmt = roomObj.getJSONObject(JSON_PROP_ROOMPRICE).getBigDecimal(JSON_PROP_AMOUNT);
			}
		}

		for (Element ReadRSwrapper : XMLUtils.getElementsAtXPath(resElem,"./accoi:ResponseBody/acco:OTA_HotelResModifyRSWrapper")) {

			for (Element roomStayElem : XMLUtils.getElementsAtXPath(ReadRSwrapper,"./ota:OTA_HotelResModifyRS/ota:HotelResModifies/ota:HotelResModify/ota:RoomStays/ota:RoomStay")) {

				if (!amndRqSuppRoomIdx.equals(roomStayElem.getAttribute("IndexNumber"))) {
					continue;
				}
				// new amount in amend response
				amendAmt = Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(roomStayElem, "./ota:Total/@AmountAfterTax"), 0);
			}

		}
		
		if (amendAmt != null) {
			JSONObject chargesObj=new JSONObject();
			calculateSupplierCharges(resBody, amendAmt, bookingAmt,chargesObj);
		}
		return resBody;

	}

	private static JSONObject getKakfaAmendRequest(JSONObject reqJson) throws Exception {
		JSONObject reqBody = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject reqHdr = reqJson.getJSONObject(JSON_PROP_REQHEADER);

		JSONArray accoInfoArr = reqBody.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONObject kafkaRqJson = null,reqBdy;
		
		for (int i = 0; i < accoInfoArr.length(); i++) {
			JSONObject accoInfoObj = accoInfoArr.getJSONObject(i);
			kafkaRqJson = new JSONObject();
			reqBdy = new JSONObject();
			
			kafkaRqJson.put(JSON_PROP_REQHEADER, reqHdr);
			kafkaRqJson.put(JSON_PROP_REQBODY, reqBdy);
			
			reqBdy.put("operation", "amend");
			String modType = accoInfoObj.getString("modificationType");
			
			if (modType != null) {
				reqBdy.put(JSON_PROP_TYPE, modType);
				reqBdy.put(JSON_PROP_PROD, "ACCO");
				reqBdy.put("entityName", "FULLCANCELLATION".equals(modType) || "CHANGEPERIODOFSTAY".equals(modType) ? "order" : "room");
				reqBdy.put("orderID", accoInfoObj.getString("orderID"));

				if (modType.endsWith("PASSENGER"))
					setPaxKafka(reqBdy, modType, accoInfoObj);

				else 
					setKafka(reqBdy, modType, accoInfoObj);
					
				//System.out.println("kafka RQ" + kafkaRqJson);
				KafkaBookProducer bookProducer = new KafkaBookProducer();
				bookProducer.runProducer(1, kafkaRqJson);
				logger.info((String.format("%s_RQ = %s", "AMENDKAFKA", kafkaRqJson)));	
			}
		}
		return kafkaRqJson;

	}

	private static void setKafka(JSONObject newReqBody, String modType, JSONObject accoInfoObj) {
		JSONArray entityIdsArr = new JSONArray();
		JSONObject entityid = new JSONObject();
		entityIdsArr.put(entityid);
		newReqBody.put("entityIDs", entityIdsArr);
		newReqBody.put("requestType", "cancel");
		entityid.put("entityID", "FULLCANCELLATION".equals(modType) || "CHANGEPERIODOFSTAY".equals(modType) ? accoInfoObj.getString("orderID"): accoInfoObj.getString("roomID"));
	
		if ("UPDATEROOM".equals(modType)) {
			newReqBody.put("requestType", "amend");
			JSONObject roomInfoObj = accoInfoObj.getJSONObject("roomInfo");
			newReqBody.put(JSON_PROP_ROOMTYPECODE,roomInfoObj.getJSONObject(JSON_PROP_ROOMTYPEINFO).getString(JSON_PROP_ROOMTYPECODE));
			newReqBody.put(JSON_PROP_RATEPLANCODE,roomInfoObj.getJSONObject(JSON_PROP_RATEPLANINFO).getString(JSON_PROP_RATEPLANCODE));
		}
		
		if("CHANGEPERIODOFSTAY".equals(modType)) {
			newReqBody.put("requestType", "amend");
			newReqBody.put(JSON_PROP_CHKIN,accoInfoObj.getString(JSON_PROP_CHKIN));
			newReqBody.put(JSON_PROP_CHKOUT,accoInfoObj.getString(JSON_PROP_CHKOUT));
        }
		
	}

	private static void setPaxKafka(JSONObject newReqBody, String modType, JSONObject accoInfoObj) {
		JSONArray entityIdsArr = new JSONArray();
		JSONObject entityid = new JSONObject();
		entityIdsArr.put(entityid);
		newReqBody.put("requestType", "amend");
		entityid.put("entityID", accoInfoObj.getString("roomID"));
		newReqBody.put("entityIDs", entityIdsArr);
		JSONArray paxArr = accoInfoObj.getJSONArray(JSON_PROP_PAXINFOARR);
		JSONArray paxInfoArr = new JSONArray();
		
		for (int i = 0; i < paxArr.length(); i++) {
			String paxID = paxArr.getJSONObject(i).optString("paxID");
			
			if ("CANCELPASSENGER".equals(modType) || "UPDATEPASSENGER".equals(modType)) {
				if (!paxID.isEmpty()) {
					paxInfoArr.put(paxArr.getJSONObject(i));
				}
			} else if ("ADDPASSENGER".equals(modType)) {
				if (paxID.isEmpty()) {
					paxInfoArr.put(paxArr.getJSONObject(i));
				}
			}

		}
		newReqBody.put("paxInfo", paxInfoArr);
	}
}
