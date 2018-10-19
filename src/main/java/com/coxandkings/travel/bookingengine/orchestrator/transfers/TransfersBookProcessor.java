package com.coxandkings.travel.bookingengine.orchestrator.transfers;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.transfers.TransfersConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class TransfersBookProcessor implements TransfersConstants {
	@Autowired
	private static final Logger logger = LogManager.getLogger(TransfersBookProcessor.class);
	private static final DateFormat mDtFmt = new SimpleDateFormat("yyyyMMddHHmmssSSS");

	public static void getSupplierResponseGroundWrapperJSON(Element resWrapperElem, JSONArray groundWrapperJsonArr ,JSONObject reqBodyJson)
			throws Exception {
		getSupplierResponseGroundBookWrapperJSON(reqBodyJson,resWrapperElem, groundWrapperJsonArr, false, 0);
	}
	public static void getSupplierResponseGroundBookWrapperJSON(JSONObject reqBodyJson,Element resWrapperElem, JSONArray groundWrapperJsonArr,
			boolean generateBookRefIdx, int bookRefIdx) throws Exception {
		// boolean isCombinedReturnJourney =
		// Boolean.valueOf(XMLUtils.getValueAtXPath(resBodyElem,
		// "./@CombinedReturnJourney"));
		String suppId = XMLUtils.getValueAtXPath(resWrapperElem, "./tran1:SupplierID");
		String sequence = XMLUtils.getValueAtXPath(resWrapperElem, "./tran1:Sequence");
		JSONObject bookwrapperJson = new JSONObject();
		
			bookwrapperJson.put(JSON_PROP_SUPPREF, suppId);
			bookwrapperJson.put("sequence", sequence);
			//bookwrapperJson.put("bookID",reqBodyJson.getString(JSON_PROP_BOOKID) );
		
		// groundServiceJson.put("isReturnJourneyCombined", isCombinedReturnJourney);
		/*
		 * if (generateBookRefIdx) { groundServiceJson.put(JSON_PROP_BOOKREFIDX,
		 * bookRefIdx); }
		 */
		JSONArray reservationIdsArr = new JSONArray();
		Element reservationElem = XMLUtils.getFirstElementAtXPath(resWrapperElem,
				"./ns:OTA_GroundBookRS/ns:Reservation");
		JSONObject reservationjson = new JSONObject();
		bookwrapperJson.put("status", XMLUtils.getValueAtXPath(reservationElem, "./@Status"));
        reservationjson.put(JSON_PROP_RESERVATIONID, XMLUtils.getValueAtXPath(reservationElem, "./ns:Confirmation[@Type='14']/@ID"));
        reservationjson.put("id_Context", XMLUtils.getValueAtXPath(reservationElem, "./ns:Confirmation[@Type='14']/@ID_Context"));
		Element confirmationid[] = XMLUtils.getElementsAtXPath(reservationElem, "./ns:Confirmation");
		//reservationJson.put(JSON_PROP_RESERVATIONID, XMLUtils.getValueAtXPath(vehSegCoreElem, "./ota:ConfID[@Type='14']/@ID"));
		for (Element confid : confirmationid) {
			JSONObject reservationIdJson = new JSONObject();
			// ID="TS" ID_Context="s_practica_sigla" Type="16"
			if("14".equals(XMLUtils.getValueAtXPath(confid, "./@Type"))==false) {
			//reservationIdJson.put("type", XMLUtils.getValueAtXPath(confid, "./@Type"));
				reservationIdJson.put("id_Context",XMLUtils.getValueAtXPath(confid, "./@ID_Context"));
				reservationIdJson.put("referenceID",XMLUtils.getValueAtXPath(confid, "./@ID"));
			reservationIdsArr.put(reservationIdJson);
		}
		reservationjson.put(JSON_PROP_REFERENCES, reservationIdsArr);
		//bookwrapperJson.put("status", reservationjson);
		bookwrapperJson.put(JSON_PROP_RESERVATIONIDS, reservationjson);
		
	}
		groundWrapperJsonArr.put(bookwrapperJson);
	}
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		KafkaBookProducer bookProducer = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		try {
			 bookProducer = new KafkaBookProducer();
			 opConfig = TransfersConfig.getOperationConfig("book");
			 reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./tran:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./tran1:OTA_GroundBookRQWrapper");
			reqBodyElem.removeChild(wrapperElem);
			// System.out.println(XMLTransformer.toString(reqBodyElem));
			TrackingContext.setTrackingContext(reqJson);
			JSONObject kafkaMsgJson = reqJson;
			 reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null)
					? reqJson.optJSONObject(JSON_PROP_REQHEADER)
					: new JSONObject();
			 reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null)
					? reqJson.optJSONObject(JSON_PROP_REQBODY)
					: new JSONObject();

			/*
			 * JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			 * JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			 */

			/*
			 * String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID); String
			 * transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID); String userID =
			 * reqHdrJson.getString(JSON_PROP_USERID);
			 * 
			 * UserContext usrCtx = UserContext.getUserContextForSession(sessionID);
			 * List<ProductSupplier> prodSuppliers =
			 * usrCtx.getSuppliersForProduct(TransfersConstants.PRODUCT_TRANSFERS);
			 * 
			 * 
			 * 
			 * XMLUtils.setValueAtXPath(reqElem, "./tran1:RequestHeader/com:SessionID",
			 * sessionID); XMLUtils.setValueAtXPath(reqElem,
			 * "./tran1:RequestHeader/com:TransactionID", transactionID);
			 * XMLUtils.setValueAtXPath(reqElem, "./tran1:RequestHeader/com:UserID",
			 * userID);
			 */

			 usrCtx = UserContext.getUserContextForSession(reqHdrJson);

			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT,
					PROD_CATEG_SUBTYPE_TRANSFER);

			TransfersSearchProcessor.createHeader(reqHdrJson, reqElem);

			// TODO : Redis Connection

			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./tran1:RequestHeader/com:SupplierCredentialsList");
			for (ProductSupplier prodSupplier : prodSuppliers) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}

			  String clientCcyCode = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
			  String redisKey = null;
			  Map<String, String> searchSuppFaresMap = null;
			  try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
				  redisKey = String.format("%s%c%s", reqHdrJson.optString(JSON_PROP_SESSIONID), KEYSEPARATOR,
						PRODUCT_TRANSFER);
					// System.out.println(redisKey);
				  searchSuppFaresMap = redisConn.hgetAll(redisKey);
			  }
			  if (searchSuppFaresMap == null) {   
				  throw new Exception(String.format("Search context not found,for %s", redisKey)); }
			 
			JSONArray transJSONArr = reqBodyJson.getJSONArray(JSON_PROP_TRANSFERSINFO);
			
			for (int t = 0; t < transJSONArr.length(); t++) {
				JSONObject bookReq = transJSONArr.getJSONObject(t);
				String suppID = bookReq.getString(JSON_PROP_SUPPREF);
				String bookID = reqBodyJson.getString(JSON_PROP_BOOKID);
				Element suppWrapperElem = null;
				
				JSONObject totalPricingInfo =  new JSONObject();
						/*bookReq.getJSONObject(JSON_PROP_TOTALFARE);*/
				String vehicleKey = getRedisKeyForGroundService(bookReq);
				//Appending Commercials and Fares in KafkaBookReq For Database Population
				JSONObject suppPriceBookInfoJson = new JSONObject(searchSuppFaresMap.get(vehicleKey));
				JSONObject suppPricingInfo = suppPriceBookInfoJson.getJSONObject(JSON_PROP_SUPPPRICEINFO);
				String clientMarket = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
				String suppCcy = suppPricingInfo.getJSONObject("suppTotalFare").getString(JSON_PROP_CCYCODE);
				bookReq.put(JSON_PROP_SUPPPRICEINFO, suppPricingInfo);
				JSONObject clientCommercialItinTotalInfoObj = suppPriceBookInfoJson.getJSONObject(JSON_PROP_CLIENTCOMMTOTAL);
				bookReq.put(JSON_PROP_CLIENTCOMMTOTAL, clientCommercialItinTotalInfoObj);
				
				suppWrapperElem = (Element) wrapperElem.cloneNode(true);
				reqBodyElem.appendChild(suppWrapperElem);
				/*
				 * ProductSupplier prodSupplier =
				 * usrCtx.getSupplierForProduct(TransfersConstants.PRODUCT_TRANSFERS, suppID);
				 */
				if (prodSuppliers == null) {
					throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
				}
		
				
				Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./tran1:SupplierID");
				suppIDElem.setTextContent(suppID);

				Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./tran1:Sequence");
				sequenceElem.setTextContent("1");

				Element posElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ns:OTA_GroundBookRQ/ns:POS");
				/* Element ground = (Element)posElem.getParentNode(); */
				Element sourceElem = XMLUtils.getFirstElementAtXPath(posElem, "./ns:Source");
				// TODO: hardcode for ISOCurrency!get it from where?
				sourceElem.setAttribute("ISOCurrency", bookReq.getString("currencyCode"));

				Element groundReservationElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem,
						"./ns:OTA_GroundBookRQ/ns:GroundReservation");

				// JSONObject groundReservationJson =
				// reqBodyJson.getJSONObject("groundReservation");

				if (null != bookReq.optJSONArray("references")) {
					JSONArray referencesArr = bookReq.getJSONArray("references");
					int referencesArrLen = referencesArr.length();
					// Element referenceElem = ownerDoc.createElementNS(Constants.NS_OTA,
					// "ns:Reference");
					Element groundAvailElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ns:OTA_GroundBookRQ");
					// groundAvailElem.removeChild(referenceElem);
					getJSONReference(ownerDoc, groundReservationElem, referencesArr, referencesArrLen, groundAvailElem);
					
					Element reference1 = XMLUtils.getFirstElementAtXPath(groundAvailElem, "./ns:Reference");
					reference1.setAttribute("ID", bookReq.getString(JSON_PROP_UNIQUEID));
					

				}

			 /* getJSONArray("paxDetails"); */
		
				getJSONPaxDetails(ownerDoc, groundReservationElem , bookReq);

				// TODO: for loop
				JSONArray serviceArr = bookReq.getJSONArray("service");
				int serviceArrLen = serviceArr.length();
				//Appending ROE in Kafka Req.
				bookReq.put("roe", RedisRoEData.getRateOfExchange(suppCcy, clientCcyCode, clientMarket));
				getJSONService(ownerDoc, groundReservationElem, serviceArr, serviceArrLen);

			}
			bookProducer.runProducer(1, kafkaMsgJson);
			sendPreBookingKafkaMessage(bookProducer, reqJson,usrCtx);
			// System.out.println(XMLTransformer.toString(reqElem));
			}
			catch (Exception x) {
			
			logger.error("Exception received while processing", x);
            throw new RequestProcessingException(x);
			}
			
			try {
			
			Element resElem = null;
			// logger.trace(String.format("SI XML Request = %s",
			// XMLTransformer.toString(reqElem)));
			//resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), TransfersConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
			
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			
		
			
			JSONObject resBodyJson = new JSONObject();
			JSONArray groundWrapperJsonArray = new JSONArray();
			Element[] resWrapperElems = XMLUtils.getElementsAtXPath(resElem,
					"./tran:ResponseBody/tran1:OTA_GroundBookRSWrapper");
			for (Element resWrapperElem : resWrapperElems) {
				Element resBodyElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ns:OTA_GroundBookRS");
				getSupplierResponseGroundWrapperJSON(resWrapperElem, groundWrapperJsonArray,reqBodyJson);
			}
			resBodyJson.put(JSON_PROP_GROUNDWRAPPER, groundWrapperJsonArray);

			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));

			sendPostBookingKafkaMessage(bookProducer, reqJson, resJson);
			return resJson.toString();	
		} catch (Exception x) {
			logger.error("Exception received while processing", x);
            throw new InternalProcessingException(x);
		}
	}

	private static String getRedisKeyForGroundService(JSONObject bookReq) {
		List<String> keys = new ArrayList<>();
		String suppId = bookReq.optString(JSON_PROP_SUPPREF);
		keys.add(suppId);
		
		//suppId.split("_")[1] != null ? suppId.split("_")[1] : suppId
		JSONArray serviceArr = bookReq.getJSONArray(JSON_PROP_SERVICES);
		for(int x = 0;x<serviceArr.length();x++) {
		JSONObject serviceJSON = serviceArr.getJSONObject(x);
	/*	JSONArray vehicleTypeArr = serviceJSON.getJSONArray(JSON_PROP_VEHICLETYPE);
		for(int k = 0 ;k < vehicleTypeArr.length() ; k++) {*/
			JSONObject vehicleTypeJSON = serviceJSON.optJSONObject(JSON_PROP_VEHICLETYPE);
			//need to change key
			//keys.add(vehicleTypeJSON.optString(JSON_PROP_MAXPASS));
			//keys.add(vehicleTypeJSON.optString(JSON_PROP_DESCRIPTION));
			keys.add(vehicleTypeJSON.optString(JSON_PROP_UNIQUEID));
			
			
		
		
		}
		
		JSONArray arrReference = bookReq.getJSONArray(JSON_PROP_REFERENCES);
		for(int r  = 0 ; r < arrReference.length() ; r++) {
			JSONObject objReference = arrReference.getJSONObject(r);
		/*	
			if(objReference.getString(JSON_PROP_TYPE).equals("16")) {
				keys.add(objReference.optString(JSON_PROP_SUPPREFNO));
				}
			*/
			keys.add(objReference.optString(JSON_PROP_SUPPREFNO));
		}
		
		String key = keys.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining("|"));
		return key;
	}

	private static void getJSONService(Document ownerDoc, Element groundReservationElem, JSONArray serviceArr,
			int serviceArrLen) {
		for (int j = 0; j < serviceArrLen; j++) {

			Element serviceElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Service");
			Element locationElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Location");
			serviceElem.appendChild(locationElem);
			JSONObject pickupJson = serviceArr.getJSONObject(j).getJSONObject("pickup");
			Element pickupElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Pickup");
			pickupElem.setAttribute(JSON_PROP_LOCATIONCODE, pickupJson.getString("locationCode"));
			pickupElem.setAttribute(JSON_PROP_LOCATIONTYPE, pickupJson.getString("locationType"));
			pickupElem.setAttribute(JSON_PROP_DATETIME, pickupJson.getString("dateTime"));
			locationElem.appendChild(pickupElem);
			JSONObject dropoffJson = serviceArr.getJSONObject(j).getJSONObject("dropoff");
			Element dropoffElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Dropoff");
			dropoffElem.setAttribute(JSON_PROP_LOCATIONCODE, dropoffJson.getString("locationCode"));
			dropoffElem.setAttribute(JSON_PROP_LOCATIONTYPE, dropoffJson.getString("locationType"));
			dropoffElem.setAttribute(JSON_PROP_DATETIME, dropoffJson.getString("dateTime"));
			locationElem.appendChild(dropoffElem);
			groundReservationElem.appendChild(serviceElem);

		}
	}

	private static void getJSONReference(Document ownerDoc, Element groundReservationElem, JSONArray referencesArr,
			int referencesArrLen, Element groundAvailElem) {
		for (int m = 0; m < referencesArrLen; m++) {

			
			Element reference = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Reference");
			// Element reference = (Element) referenceElem.cloneNode(true);
			if(null!= referencesArr.getJSONObject(m).optString(JSON_PROP_SUPPREFNO)) {
				reference.setAttribute("ID", referencesArr.getJSONObject(m).optString(JSON_PROP_SUPPREFNO));
			}
			/* if(null!= referencesArr.getJSONObject(m).optString(JSON_PROP_UNIQUEID)) {
				reference.setAttribute("ID", referencesArr.getJSONObject(m).optString(JSON_PROP_UNIQUEID));
			}*/
			reference.setAttribute("ID_Context", referencesArr.getJSONObject(m).getString("id_Context"));
			reference.setAttribute(JSON_PROP_REFTYPE, referencesArr.getJSONObject(m).optString("type"));
			

			groundAvailElem.insertBefore(reference, groundReservationElem);
		}
	}

	/*private static void getJSONPaxDetails(Document ownerDoc, Element groundReservationElem, JSONArray passengerArr,
			int passengerArrLen,JSONObject bookReq) {
		for (int p = 0; p < passengerArrLen; p++) {

			// Passenger
			Element passengerElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Passenger");
			groundReservationElem.appendChild(passengerElem);
			Element primaryElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Primary");
			passengerElem.appendChild(primaryElem);
			
			JSONObject primaryJson = passengerArr.getJSONObject(p).getJSONObject("primary");
			primaryElem.setAttribute("PaxType", primaryJson.optString(JSON_PROP_PAXTYPE));
			primaryElem.setAttribute("Age", primaryJson.optString(JSON_PROP_AGE));
			Element personNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PersonName");
			primaryElem.appendChild(personNameElem);
			Element givenNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:GivenName");
			givenNameElem.setTextContent(primaryJson.getJSONObject("personName").getString("givenName"));
			personNameElem.appendChild(givenNameElem);
			Element surNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Surname");
			surNameElem.setTextContent(primaryJson.getJSONObject("personName").getString("surname"));
			personNameElem.appendChild(surNameElem);
			JSONArray contactArr = primaryJson.getJSONArray("contactNumber");
			for(int t = 0 ; t < contactArr.length() ; t++) {
			JSONObject contactJson = contactArr.getJSONObject(t);	
			
			Element telephoneElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Telephone");
			telephoneElem.setAttribute("PhoneNumber", contactJson.getString("phoneNumber"));
			primaryElem.appendChild(telephoneElem);
			}
			Element emailElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Email");
			emailElem.setTextContent(primaryJson.getString("email"));
			primaryElem.appendChild(emailElem);

			//JSONObject additionalJson = passengerArr.getJSONObject(p).getJSONObject("additional");
			JSONArray additionalArr = passengerArr.getJSONObject(p).getJSONArray("additional");
			for(int a= 0 ; a< additionalArr.length() ; a++) {
			// Additional
				JSONObject additionalJson = additionalArr.getJSONObject(a);
			Element additionalElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Additional");
			additionalElem.setAttribute("PaxType", additionalJson.getString(JSON_PROP_PAXTYPE));
			additionalElem.setAttribute("Age", additionalJson.getString(JSON_PROP_AGE));
			personNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PersonName");
			additionalElem.appendChild(personNameElem);
			givenNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:GivenName");
			givenNameElem.setTextContent(additionalJson.getJSONObject("personName").getString("givenName"));
			personNameElem.appendChild(givenNameElem);
			surNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Surname");
			surNameElem.setTextContent(additionalJson.getJSONObject("personName").getString("surname"));
			personNameElem.appendChild(surNameElem);
			contactArr = additionalJson.getJSONArray("contactNumber");
			for(int t = 0 ; t < contactArr.length() ; t++) {
			JSONObject contactJson = contactArr.getJSONObject(t);	
			Element telephoneElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Telephone");
			telephoneElem.setAttribute("PhoneNumber", contactJson.getString("phoneNumber"));
			additionalElem.appendChild(telephoneElem);
			}
			emailElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Email");
			emailElem.setTextContent(additionalJson.getString("email"));
			additionalElem.appendChild(emailElem);
			passengerElem.appendChild(additionalElem);
			}
			//addition for passenger quantity according to paxType
	
			JSONArray paxQuantityArr = passengerArr.getJSONObject(p).getJSONArray("passengerQuantity");
			for(int q=0 ; q < paxQuantityArr.length() ; q++) {
				JSONObject paxQuantityJson = paxQuantityArr.getJSONObject(q);
				Element paxQuantityElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PassengerTypeQuantity");
			paxQuantityElem.setAttribute("Quantity", paxQuantityJson.optString("quantity"));
			paxQuantityElem.setAttribute("Code", paxQuantityJson.optString("code"));
			passengerElem.appendChild(paxQuantityElem);
		
		
			}

		}
		
	}*/
	
	private static void getJSONPaxDetails(Document ownerDoc, Element groundReservationElem,JSONObject bookReq) {
		
		   int intAdtCount = 0;
	        int intChdCount = 0;
	        int intInfCount = 0;
	    Element passengerElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Passenger");
	    JSONArray passengerArr = bookReq.getJSONArray("paxDetails");
		for (int p = 0; p < passengerArr.length(); p++) {
			
			
			JSONObject objPassenger = passengerArr.getJSONObject(p);
			// Passenger
			groundReservationElem.appendChild(passengerElem);
			
			if(objPassenger.getBoolean("isLeadPax") == true) {
			Element primaryElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Primary");
			primaryElem.setAttribute("PaxType", objPassenger.optString(JSON_PROP_PAXTYPE));	
			primaryElem.setAttribute("Age", objPassenger.optString(JSON_PROP_AGE));
			Element personNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PersonName");
			primaryElem.appendChild(personNameElem);
			Element namePrefixElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:NamePrefix");
			namePrefixElem.setTextContent(objPassenger.getString("title"));
			personNameElem.appendChild(namePrefixElem);
			Element givenNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:GivenName");
			givenNameElem.setTextContent(objPassenger.getString("firstName"));
			personNameElem.appendChild(givenNameElem);
			Element surNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Surname");
			surNameElem.setTextContent(objPassenger.getString("surname"));
			personNameElem.appendChild(surNameElem);
			JSONArray contactArr = objPassenger.getJSONArray("contactDetails");
			for(int t = 0 ; t < contactArr.length() ; t++) {
				JSONObject contactJson = contactArr.getJSONObject(t);	
				Element telephoneElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Telephone");
				telephoneElem.setAttribute("PhoneNumber", contactJson.getString("mobileNo"));
				Element emailElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Email");
				emailElem.setTextContent(contactJson.getString("email"));
				primaryElem.appendChild(telephoneElem);
				primaryElem.appendChild(emailElem);
			}
			
			passengerElem.appendChild(primaryElem);
				
			}
			
			else if(objPassenger.getBoolean("isLeadPax") == false) {
				Element additionalElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Additional");
				additionalElem.setAttribute("PaxType", objPassenger.optString(JSON_PROP_PAXTYPE));	
				additionalElem.setAttribute("Age", objPassenger.optString(JSON_PROP_AGE));
				Element personNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PersonName");
				additionalElem.appendChild(personNameElem);
				Element namePrefixElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:NamePrefix");
				namePrefixElem.setTextContent(objPassenger.getString("title"));
				personNameElem.appendChild(namePrefixElem);
				Element givenNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:GivenName");
				givenNameElem.setTextContent(objPassenger.getString("firstName"));
				personNameElem.appendChild(givenNameElem);
				Element surNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Surname");
				surNameElem.setTextContent(objPassenger.getString("surname"));
				personNameElem.appendChild(surNameElem);
				JSONArray contactArr = objPassenger.getJSONArray("contactDetails");
				for(int t = 0 ; t < contactArr.length() ; t++) {
					JSONObject contactJson = contactArr.getJSONObject(t);	
					Element telephoneElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Telephone");
					telephoneElem.setAttribute("PhoneNumber", contactJson.getString("mobileNo"));
					Element emailElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Email");
					emailElem.setTextContent(contactJson.getString("email"));
					additionalElem.appendChild(telephoneElem);
					additionalElem.appendChild(emailElem);
				
					
				}
				
				passengerElem.appendChild(additionalElem);
				
			}
			
			if ("Adult".equalsIgnoreCase(objPassenger.optString(JSON_PROP_PAXTYPE))) {
                intAdtCount = intAdtCount + 1;
            } else if ("Child".equalsIgnoreCase(objPassenger.optString(JSON_PROP_PAXTYPE))) {
                intChdCount = intChdCount + 1;
            }
//				Element paxQuantityElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PassengerTypeQuantity");
//				paxQuantityElem.setAttribute("Quantity", String.valueOf(intAdtCount));
//				paxQuantityElem.setAttribute("Code", "Adult");
//				passengerElem.appendChild(paxQuantityElem);
//				Element paxChildQuantityElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PassengerTypeQuantity");
//				paxChildQuantityElem.setAttribute("Quantity", String.valueOf(intAdtCount));
//				paxChildQuantityElem.setAttribute("Code", "Child");
//				passengerElem.appendChild(paxChildQuantityElem);
			
				/*Element paxInfQuantityElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PassengerTypeQuantity");
				paxInfQuantityElem.setAttribute("Quantity", String.valueOf(intAdtCount));
				paxInfQuantityElem.setAttribute("Code", "Adult");
				passengerElem.appendChild(paxInfQuantityElem);*/
			
		}		
		
		Element paxQuantityElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PassengerTypeQuantity");
		paxQuantityElem.setAttribute("Quantity", String.valueOf(intAdtCount));
		paxQuantityElem.setAttribute("Code", "Adult");
		passengerElem.appendChild(paxQuantityElem);
		Element paxChildQuantityElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PassengerTypeQuantity");
		paxChildQuantityElem.setAttribute("Quantity", String.valueOf(intChdCount));
		paxChildQuantityElem.setAttribute("Code", "Child");
		passengerElem.appendChild(paxChildQuantityElem);
		
	}
	private static Element createRentalPaymentPrefElement(Document ownerDoc, JSONObject vehicleAvailJson, JSONObject suppTotalFare) {
		 
//		 String temp;
		 JSONObject rentalPaymentPrefJson = vehicleAvailJson.getJSONObject("rentalPaymentPref");
        Element rentalPaymentPrefElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:RentalPaymentPref");
        rentalPaymentPrefElem.setAttribute("PaymentType", rentalPaymentPrefJson.optString("paymentType"));
        rentalPaymentPrefElem.setAttribute("Type", rentalPaymentPrefJson.optString("type"));
        
       /* Element paymentCardElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PaymentCard"); 
        if(!(temp = rentalPaymentPrefJson.optString("PaymentCardCode")).isEmpty())
       	 paymentCardElem.setAttribute("CardCode", temp);
        if(!(temp = rentalPaymentPrefJson.optString("PaymentCardExpiryDate")).isEmpty())
       	 paymentCardElem.setAttribute("ExpireDate", temp);
        
        Element cardType = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CardType");
        cardType.setTextContent(rentalPaymentPrefJson.optString("CardType"));
        paymentCardElem.appendChild(cardType);
        Element cardHolderName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CardHolderName");
        cardHolderName.setTextContent(rentalPaymentPrefJson.optString("CardHolderName"));
        paymentCardElem.appendChild(cardHolderName);
        Element cardNumber = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CardNumber");
        cardNumber.setTextContent(rentalPaymentPrefJson.optString("CardNumber"));
        paymentCardElem.appendChild(cardNumber);*/
        
        // Payment Amount taken from Redis which was saved in price operation 
        Element paymentAmountElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PaymentAmount");
		 paymentAmountElem.setAttribute(XML_ATTR_AMOUNT,  BigDecimaltoString(rentalPaymentPrefJson,JSON_PROP_AMOUNT));
        paymentAmountElem.setAttribute(XML_ATTR_CURRENCYCODE, rentalPaymentPrefJson.getString(JSON_PROP_CURRENCYCODE));
        
       /* Element paymentAmountElem =ownerDoc.createElementNS(Constants.NS_OTA, "ota:PaymentAmount");
        if(!(temp = rentalPaymentPrefJson.optString("Amount")).isEmpty())
        paymentAmountElem.setAttribute("Amount", temp);
        if(!(temp = rentalPaymentPrefJson.optString("CurrencyCode")).isEmpty())
        paymentAmountElem.setAttribute("CurrencyCode", temp);*/
        
//      rentalPaymentPrefElem.appendChild(paymentCardElem);
        rentalPaymentPrefElem.appendChild(paymentAmountElem);
		return rentalPaymentPrefElem;
	}
	
	private static void sendPreBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, UserContext usrCtx) throws Exception {
		//JSONObject kafkaMsgJson = reqJson;
		JSONObject kafkaMsg=new JSONObject(new JSONTokener(reqJson.toString()));
		JSONObject reqHeaderJson = kafkaMsg.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, PROD_CATEG_SUBTYPE_TRANSFER);
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PRODCATEG, PROD_CATEG_TRANSPORT);
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_TRANSFER);
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put("operation", "book");
		bookProducer.runProducer(1, kafkaMsg);
		logger.trace(String.format("Car Book Request Kafka Message: %s", kafkaMsg.toString()));
	}
	
	private static void sendPostBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, JSONObject resJson) throws Exception {
		JSONObject kafkaMsgJson = resJson;
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PROD, PROD_CATEG_SUBTYPE_TRANSFER);
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_TRANSFER);
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqBodyJson.getString(JSON_PROP_BOOKID));
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("operation", "book");
		bookProducer.runProducer(1, kafkaMsgJson);
	}
	
	public static String BigDecimaltoString(JSONObject json, String prop) {
		
		if(json==null)
			return "";
		try {
			if(json.getBigDecimal(prop).compareTo(new BigDecimal(0)) == 0)
				return "";
			else
				return json.getBigDecimal(prop).toString();
		}
		catch(JSONException e) {
			return "";
		}
	}
	
	public static String NumbertoString(JSONObject json, String prop) {
		
		if(json==null)
			return "";
		try {
			if(json.getNumber(prop).equals(0))
				return "";
			else
				return json.getNumber(prop).toString();
		}
		catch(JSONException e) {
			return "";
		}
	}
}
