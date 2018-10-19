package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

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
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.enums.AccoSubType;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AccoMRCancelProcessor implements AccoConstants {

	private static final Logger logger = LogManager.getLogger(AccoMRCancelProcessor.class);
	static final String OPERATION_NAME = "cancel";
	
	public static String process(JSONObject reqJson) throws Exception {
		Element reqElem;
		ServiceConfig opConfig;
		JSONObject newReqJson;
		JSONArray productsArr;
		try {
			productsArr = getProductsArray(reqJson);
			
			newReqJson = fetchBookIdDetails(reqJson,productsArr);
			
			opConfig = AccoConfig.getOperationConfig("modify");
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			
			setSupplierRequestElem(newReqJson, reqElem);
		}
		catch (ValidationException valx) {
			throw valx;
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
			
        try {
			JSONObject allKafkaReqJson = AccoModifyProcessor.getKakfaAmendRequest(newReqJson);
			
			Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			JSONObject resJson = getSupplierResponseJSON(newReqJson, resElem,productsArr);
			kafkaResponse(resJson, allKafkaReqJson,newReqJson);
			AccoModifyProcessor.calculateTotalCharges(newReqJson,resJson);
			
			JSONObject newResJson=combineResponse(reqJson,resJson);
			
			return newResJson.toString();
		} 
        catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new InternalProcessingException(x);
		}
	}

	private static JSONObject combineResponse(JSONObject reqJson, JSONObject resJson) {
		JSONObject resBodyJson = new JSONObject();
		JSONObject finalResJson = new JSONObject();
		finalResJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		finalResJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		JSONArray multiResArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONArray multiReqArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		
		int sequence=0;
		JSONArray finalMultiResArr = new JSONArray();
		
		for(int i=0;i<multiReqArr.length();i++) {
			resBodyJson.put(JSON_PROP_ACCOMODATIONARR, finalMultiResArr);
			JSONArray orderIdArr = multiReqArr.getJSONObject(i).getJSONArray("orderID");
			JSONObject suppCancelRef=new JSONObject();
			for(int j=0;j<orderIdArr.length();j++) {
				JSONObject multiResObj = multiResArr.getJSONObject(sequence);
				int reqSeq = (int) multiResObj.remove("reqSequence");
				suppCancelRef.append("supplierCancellationInfo",multiResObj);
				if(j==orderIdArr.length()-1) {
					JSONArray suppCancelInfoArr = suppCancelRef.getJSONArray("supplierCancellationInfo");
					BigDecimal totalCmpnyCharges=new BigDecimal(0);
					String compnyCcyCode = "";
					for(int k=0;k<suppCancelInfoArr.length();k++) {
						JSONObject suppRoomObj = suppCancelInfoArr.getJSONObject(k);
						totalCmpnyCharges=totalCmpnyCharges.add(suppRoomObj.optBigDecimal("companyCharges",new BigDecimal(0)));
						compnyCcyCode=suppRoomObj.optString("companyChargesCurrencyCode","");
						String roomStatus = suppRoomObj.optString("status","");
						if("FAILURE".equals(roomStatus)) {
							suppCancelRef.put("status", roomStatus);
							break;
						}
						else {
							suppCancelRef.put("status", roomStatus);
	                     }
					}
					suppCancelRef.put("companyChargesCurrencyCode",compnyCcyCode);
					suppCancelRef.put("reqSequence",reqSeq);
					suppCancelRef.put("totalCompanyCharges", totalCmpnyCharges);
				}
				sequence++;
			}
			finalMultiResArr.put(suppCancelRef);
		}
		resBodyJson.put(JSON_PROP_BOOKID, reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID));
		return finalResJson;
	}

	private static JSONArray getProductsArray(JSONObject reqJson) throws Exception {
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		AccoRequestValidator.validateBookId(reqBodyJson,reqJson.getJSONObject(JSON_PROP_REQHEADER));
		
		JSONArray productsArr;
		String bookId = reqBodyJson.getString("bookID");
		JSONObject orderDetailsDB = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_DBSERIVCE, new URL(String.format(DBServiceConfig.getDBServiceURL(), bookId)), DBServiceConfig.getHttpHeaders(), "GET", null);  
		
		if(!orderDetailsDB.has("ErrorMsg")) {
			productsArr = orderDetailsDB.optJSONObject(JSON_PROP_RESBODY).optJSONArray(JSON_PROP_PRODS);
		}
		else {
			logger.info(String.format("BookId %s is not valid",bookId));
			throw new Exception();
		}
		return productsArr;
	}

	private static JSONObject getSupplierResponseJSON(JSONObject reqJson, Element resElem,JSONArray productsArr) throws  Exception {
		JSONObject resJson = new JSONObject();
		JSONObject resBody = new JSONObject();
		JSONArray multiResArr = new JSONArray();
		
		JSONArray reqAccInfoArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBody);
		resBody.put(JSON_PROP_ACCOMODATIONARR, multiResArr);
		int sequence = 0;String sequence_str="";
		outerloop:for (Element ReadRSwrapper : XMLUtils.getElementsAtXPath(resElem,"./accoi:ResponseBody/acco:OTA_HotelResModifyRSWrapper")) {
			sequence_str = XMLUtils.getValueAtXPath(ReadRSwrapper, "./acco:Sequence");
			sequence = sequence_str.isEmpty()?sequence:Integer.parseInt(sequence_str);
			
			AccoModifyProcessor.validateSuppRes(ReadRSwrapper,multiResArr,sequence,reqAccInfoArr.getJSONObject(sequence).getInt("reqSequence"));
			
			if (resBody.optJSONArray(JSON_PROP_ACCOMODATIONARR).optJSONObject(sequence)!=null) {
				if(resBody.optJSONArray(JSON_PROP_ACCOMODATIONARR).optJSONObject(sequence).has("errorMsg")) {
					continue;	
				}	
			}
			
			//as discussed with Kanif the Cancellation status may be defined at booking level or roomLevel so i check both levels and in cancellations.the charges will be shown in cancelRuleInfo  
			String cancelRoomStatus = XMLUtils.getValueAtXPath(ReadRSwrapper,"./ota:OTA_HotelResModifyRS/ota:HotelResModifies/ota:HotelResModify/@ResStatus");
			if (Utils.isStringNotNullAndNotEmpty(cancelRoomStatus)) {
				if (("CANCELCONF").equals(cancelRoomStatus)) {
					int reqSeq = reqAccInfoArr.getJSONObject(sequence).getInt("reqSequence");
					AccoModifyProcessor.setNoChange(multiResArr,sequence,reqSeq);
					checkForSupplierCharges(ReadRSwrapper,multiResArr,sequence);
					continue;
				}
			}
			
			for (Element RoomStayElem : XMLUtils.getElementsAtXPath(ReadRSwrapper,"./ota:OTA_HotelResModifyRS/ota:HotelResModifies/ota:HotelResModify/ota:RoomStays")) {
				String roomStayStatus = XMLUtils.getValueAtXPath(RoomStayElem, "./ota:RoomStay/@RoomStayStatus");
				if (("CANCELCONF").equals(roomStayStatus)) {
					int reqSeq = reqAccInfoArr.getJSONObject(sequence).getInt("reqSequence");
					AccoModifyProcessor.setNoChange(multiResArr,sequence,reqSeq);
					checkForSupplierCharges(RoomStayElem,multiResArr,sequence);
					continue outerloop;
				}
			}
		}
		
		JSONObject companyPolicyRes = null;
	    
		//Applying Company Policy
	    companyPolicyRes = AccoMRAmClCompanyPolicy.getAmClCompanyPolicy(CommercialsOperation.Cancel, reqJson, resJson, productsArr);
	    AccoModifyProcessor.appendingCompanyPolicyChargesInRes(resBody, companyPolicyRes);
		
   	    return resJson;
	}

	private static void checkForSupplierCharges(Element wrapper, JSONArray multiResArr, int sequence) {
		Element cancelRuleElem = XMLUtils.getFirstElementAtXPath(wrapper,"./ota:OTA_HotelResModifyRS/ota:HotelResModifies/ota:HotelResModify/ota:TPA_Extensions/ota:CancelInfoRS/ota:CancelRules/ota:CancelRule");
		if(cancelRuleElem!=null) {
			String supplierAmt=cancelRuleElem.getAttribute("Amount");
			String currencyCode = cancelRuleElem.getAttribute("CurrencyCode");
			if(Utils.isStringNotNullAndNotEmpty(supplierAmt)) {
				multiResArr.getJSONObject(sequence).put("supplierCharges", supplierAmt);
				multiResArr.getJSONObject(sequence).put("supplierChargesCurrencyCode", currencyCode);
			}	
		}		
	}

	private static void setSupplierRequestElem(JSONObject reqJson, Element reqElem) throws Exception {
		Document ownerDoc = reqElem.getOwnerDocument();
		Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem,"./accoi:RequestBody/acco:OTA_HotelResModifyRQWrapper");
		XMLUtils.removeNode(blankWrapperElem);
		
		JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null)? reqJson.optJSONObject(JSON_PROP_REQHEADER):new JSONObject();
		JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null)? reqJson.optJSONObject(JSON_PROP_REQBODY): new JSONObject();
		
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
		    
		    //Call opsTodo
	        ToDoTaskSubType todoSubType;
	        todoSubType=ToDoTaskSubType.ORDER;
	        ToDoTaskName todoTaskName = ToDoTaskName.CANCEL;
	            	
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

	private static void setSuppReqOTAElem(Document ownerDoc, Element reqOTAElem, JSONObject roomObjectJson) {
		Element hotelResModifyElem = XMLUtils.getFirstElementAtXPath(reqOTAElem,"./ota:HotelResModifies/ota:HotelResModify");
		
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
		
		reqOTAElem.setAttribute("Target", "FULLCANCELLATION");
	}

	private static JSONObject fetchBookIdDetails(JSONObject reqJson,JSONArray productsArr) throws Exception {
		JSONObject roomObjectJson;
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject reqHeaderJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONArray multiReqArr = reqBodyJson.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONObject newReqJson=new JSONObject();
		JSONObject newReqBdyJson=new JSONObject();
		newReqJson.put(JSON_PROP_REQHEADER, reqHeaderJson);
		newReqJson.put(JSON_PROP_REQBODY,newReqBdyJson);
		newReqBdyJson.put("bookID", reqBodyJson.getString("bookID"));
		for (int i = 0; i < multiReqArr.length(); i++) {
			roomObjectJson = multiReqArr.getJSONObject(i);
			JSONArray orderIdArr = roomObjectJson.getJSONArray("orderID");
			
			for(int j=0;j<orderIdArr.length();j++) {
				JSONObject suppBookRefObj=new JSONObject();
				newReqBdyJson.append(JSON_PROP_ACCOMODATIONARR,suppBookRefObj);
				//order id from request
				String orderId = orderIdArr.getString(j);
				for(Object productObj:productsArr) {
					//orderID in DB
					String dbOrderID = ((JSONObject) productObj).getString("orderID");
					if(dbOrderID.equals(orderId)) {
						JSONObject roomObject = ((JSONObject) productObj).getJSONObject("orderDetails").getJSONObject("hotelDetails").getJSONArray("rooms").getJSONObject(0);
						JSONObject hotelDetailObj = ((JSONObject) productObj).getJSONObject("orderDetails").getJSONObject("hotelDetails");
						JSONObject roomInfoJson=new JSONObject();
						JSONObject hotelInfoJson=new JSONObject();
						suppBookRefObj.put("orderID",dbOrderID);
						suppBookRefObj.put("reqSequence", roomObjectJson.getInt("reqSequence"));
						suppBookRefObj.put("modificationType", "FULLCANCELLATION");
						suppBookRefObj.put(JSON_PROP_ACCOSUBTYPE, ((JSONObject) productObj).getString("productSubCategory"));
						suppBookRefObj.put("supplierReservationId", ((JSONObject) productObj).getString("supplierReservationId"));
						suppBookRefObj.put("clientReferenceId", ((JSONObject) productObj).getString("clientReferenceId"));
						suppBookRefObj.put("supplierCancellationId", ((JSONObject) productObj).getString("supplierCancellationId"));
						suppBookRefObj.put("supplierReferenceId", ((JSONObject) productObj).getString("supplierReferenceId"));
						suppBookRefObj.put(JSON_PROP_SUPPREF, ((JSONObject) productObj).getString("supplierID"));
						suppBookRefObj.put(JSON_PROP_PAXINFOARR, roomObject.getJSONArray(JSON_PROP_PAXINFOARR));
						suppBookRefObj.put(JSON_PROP_ROOMPRICE, roomObject.getJSONObject(JSON_PROP_ROOMPRICE));
						suppBookRefObj.put(JSON_PROP_CHKIN, roomObject.getString(JSON_PROP_CHKIN));
						suppBookRefObj.put(JSON_PROP_CHKOUT, roomObject.getString(JSON_PROP_CHKOUT));
						suppBookRefObj.put("clientCommercials", roomObject.getJSONArray("clientCommercials"));
						
						//RoomInfo
						suppBookRefObj.put(JSON_PROP_ROOMINFO, roomInfoJson);
						roomInfoJson.put(JSON_PROP_ROOMTYPEINFO, roomObject.getJSONObject(JSON_PROP_ROOMTYPEINFO));
						roomInfoJson.put(JSON_PROP_RATEPLANINFO, roomObject.getJSONObject(JSON_PROP_RATEPLANINFO));
						roomInfoJson.put(JSON_PROP_MEALINFO, roomObject.getJSONObject(JSON_PROP_MEALINFO));
						roomInfoJson.put(JSON_PROP_HOTELINFO, hotelInfoJson);
						hotelInfoJson.put(JSON_PROP_HOTELCODE, hotelDetailObj.getString(JSON_PROP_HOTELCODE));
						hotelInfoJson.put(JSON_PROP_HOTELNAME, hotelDetailObj.getString(JSON_PROP_HOTELNAME));
					}
				}
				if(suppBookRefObj.length()==0) {
					throw new Exception();
				}
			}
		  }
		return newReqJson;
	}
	
	static void kafkaResponse(JSONObject resJson, JSONObject allKafkaReqJson,JSONObject reqJson) throws Exception {
	    JSONObject amdResBdy = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray amdResAccInfoArr = amdResBdy.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONArray kafkaReqArr = allKafkaReqJson.getJSONArray("kafkaRequests");
		KafkaBookProducer bookProducer = new KafkaBookProducer();
		
		for(int i=0;i<amdResAccInfoArr.length();i++) {
		JSONObject kafkaresJson = new JSONObject();
		JSONObject kafkaresBody = new JSONObject();	
		JSONObject kafkaReqObj = kafkaReqArr.getJSONObject(i);
		kafkaresJson.put(JSON_PROP_RESHEADER, kafkaReqObj.getJSONObject(JSON_PROP_REQHEADER));
		kafkaresJson.put(JSON_PROP_RESBODY, kafkaresBody);
		kafkaresBody.put(JSON_PROP_PROD, "ACCO");
		kafkaresBody.put("operation", "amend");
		
		JSONObject accInfoObj = amdResAccInfoArr.getJSONObject(i);
		if (accInfoObj.has("errorMsg")) {
			kafkaresBody.put("errorMsg", accInfoObj.getString("errorMsg"));
			bookProducer.runProducer(1, kafkaresJson);
			continue;
		}
		
		JSONObject kafkaReqBdy = kafkaReqObj.getJSONObject(JSON_PROP_REQBODY);
		
		kafkaresBody.put("companyCharges", accInfoObj.getInt("companyCharges"));
		kafkaresBody.put("companyChargesCurrencyCode", accInfoObj.getString("companyChargesCurrencyCode"));
		kafkaresBody.put("supplierChargesCurrencyCode", accInfoObj.getString("supplierChargesCurrencyCode"));
		kafkaresBody.put("type", kafkaReqBdy.getString("type"));
		kafkaresBody.put("orderID", kafkaReqBdy.getString("orderID"));
		kafkaresBody.put("entityIDs", kafkaReqBdy.getJSONArray("entityIDs"));
		kafkaresBody.put("requestType", kafkaReqBdy.getString("requestType"));
		kafkaresBody.put("entityName", kafkaReqBdy.getString("entityName"));
		kafkaresBody.put("supplierCharges", accInfoObj.getBigDecimal("supplierCharges"));

		System.out.println("Amend Kafka RS " + kafkaresJson);
		
		
		bookProducer.runProducer(1, kafkaresJson);
		
		logger.info((String.format("%s_RS = %s", "AMENDKAFKA",kafkaresJson)));
		}
    }
	
	static void calculateTotalCharges(JSONObject reqJson,JSONObject resJson) {
		String clientCcy = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		String mrkt =  reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		JSONArray multiResJsonArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		
		for(int i=0;i<multiResJsonArr.length();i++) {
			JSONObject roomChrgsJson = multiResJsonArr.getJSONObject(i);
			if(roomChrgsJson.has("errorMsg")) {
				continue;
			}
			String cmpnyCcyCode = (String) roomChrgsJson.remove("companyChargesCurrencyCode");
			String suppCcyCode = (String) roomChrgsJson.remove("supplierChargesCurrencyCode");
			BigDecimal cmpnyChrges = (BigDecimal) roomChrgsJson.remove("companyCharges");
			BigDecimal suppChrges =  (BigDecimal) roomChrgsJson.remove("supplierCharges");
			
			//will brms send the type of company chrge or signed amount
			//assuming it will be a signed amount
			BigDecimal modifChrgs=new BigDecimal(0);
			modifChrgs = cmpnyChrges.add(suppChrges.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcy, mrkt)));
			
			roomChrgsJson.put("companyCharges",modifChrgs);
			roomChrgsJson.put("companyChargesCurrencyCode",clientCcy);
			roomChrgsJson.put(JSON_PROP_SUPPREF,reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR).getJSONObject(i).getString(JSON_PROP_SUPPREF));
		}
	}
}
