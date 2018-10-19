package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.coxandkings.travel.bookingengine.config.DBServiceConfig;
import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.enums.TripIndicator;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class AirRepriceProcessor implements AirConstants {

	private static final Logger logger = LogManager.getLogger(AirRepriceProcessor.class);

	public static Element getAirTravelerAvailElement(Document ownerDoc, JSONObject traveller, int travellerIdx, UserContext usrCtx, JSONObject leadPaxJsonObj, List<Integer> adtPaxRphList, int adtPaxListIdx) throws ValidationException,RequestProcessingException {
		Element travellerElem = ownerDoc.createElementNS(NS_OTA, "ota:AirTravelerAvail");
		Element psgrElem = ownerDoc.createElementNS(NS_OTA, "ota:PassengerTypeQuantity");
		psgrElem.setAttribute("Code", traveller.getString(JSON_PROP_PAXTYPE));
		//psgrElem.setAttribute("Quantity", String.valueOf(traveller.getInt("quantity")));
		psgrElem.setAttribute("Quantity", "1");
		
		JSONObject leadAddressDetailJson=leadPaxJsonObj.optJSONObject(JSON_PROP_ADDRDTLS); 
		JSONArray leadContactDetailJson=leadPaxJsonObj.optJSONArray(JSON_PROP_CONTACTDTLS); 
		
		travellerElem.appendChild(psgrElem);

		Element airTravlerElem = ownerDoc.createElementNS(NS_OTA, "ota:AirTraveler");
		
		if(traveller.has(JSON_PROP_DATEOFBIRTH) && !(traveller.getString(JSON_PROP_DATEOFBIRTH)).equalsIgnoreCase("")) {
			LocalDate dobDate=LocalDate.parse(traveller.getString(JSON_PROP_DATEOFBIRTH));
			
			if(dobDate.isAfter(LocalDate.now())) {
				throw new ValidationException("TRLERR020");
			}
		}
		
		String dob=traveller.getString(JSON_PROP_DATEOFBIRTH);
		
		LocalDate today = LocalDate.now();
		LocalDate birthday = LocalDate.parse(dob);
		
		int age=Period.between(birthday, today).getYears();
		
		airTravlerElem.setAttribute("BirthDate", traveller.optString(JSON_PROP_DATEOFBIRTH, ""));
		if(age==0) {
			age=1;
		}
		
		airTravlerElem.setAttribute("Age",Integer.toString(age));
		airTravlerElem.setAttribute("PassengerTypeCode", traveller.optString(JSON_PROP_PAXTYPE,""));
		airTravlerElem.setAttribute("Gender", traveller.optString(JSON_PROP_GENDER,""));

		travellerElem.appendChild(airTravlerElem);
		
		
		if((traveller.getString(JSON_PROP_PAXTYPE)).equalsIgnoreCase(JSON_PROP_INF)) {
			Element profileRefElem = ownerDoc.createElementNS(NS_OTA, "ota:ProfileRef");
			airTravlerElem.appendChild(profileRefElem);
			Element uniqueIDElem = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
			uniqueIDElem.setAttribute("Type", "36");
			uniqueIDElem.setAttribute("ID", adtPaxRphList.get(adtPaxListIdx).toString());
			profileRefElem.appendChild(uniqueIDElem);
		}
		

		Element personNameElem = ownerDoc.createElementNS(NS_OTA, "ota:PersonName");
		airTravlerElem.appendChild(personNameElem);

		Element namePrefixElem = ownerDoc.createElementNS(NS_OTA, "ota:NamePrefix");
		namePrefixElem.setTextContent(traveller.optString(JSON_PROP_TITLE,""));
		
		personNameElem.appendChild(namePrefixElem);

		Element givenNameElem = ownerDoc.createElementNS(NS_OTA, "ota:GivenName");
		givenNameElem.setTextContent(traveller.optString(JSON_PROP_FIRSTNAME,""));
		
		personNameElem.appendChild(givenNameElem);

		Element surnameElem = ownerDoc.createElementNS(NS_OTA, "ota:Surname");
		surnameElem.setTextContent(traveller.optString(JSON_PROP_SURNAME,""));
		
		personNameElem.appendChild(surnameElem);

		if(traveller.has(JSON_PROP_CONTACTDTLS) && traveller.getJSONArray(JSON_PROP_CONTACTDTLS).length()>0) {
			int contactLength = traveller.getJSONArray(JSON_PROP_CONTACTDTLS).length();
			for (int l = 0; l < contactLength; l++) {
				JSONObject contactInfo = traveller.getJSONArray(JSON_PROP_CONTACTDTLS).getJSONObject(l).getJSONObject(JSON_PROP_CONTACTINFO);
				AirBookProcessor.createTelephoneEmailDetails(ownerDoc, airTravlerElem, contactInfo);
			}
		}
		else if(leadPaxJsonObj.has(JSON_PROP_CONTACTDTLS) && leadPaxJsonObj.getJSONArray(JSON_PROP_CONTACTDTLS).length()>0) {
			int contactLength = leadPaxJsonObj.getJSONArray(JSON_PROP_CONTACTDTLS).length();
			for (int l = 0; l < contactLength; l++) {
				JSONObject contactInfo = leadPaxJsonObj.getJSONArray(JSON_PROP_CONTACTDTLS).getJSONObject(l).getJSONObject(JSON_PROP_CONTACTINFO);
				AirBookProcessor.createTelephoneEmailDetails(ownerDoc, airTravlerElem, contactInfo);
			}
			
		}
		
		
		
		if(traveller.has(JSON_PROP_ADDRDTLS) && traveller.getJSONObject(JSON_PROP_ADDRDTLS).length()>0) {
			Element Address = ownerDoc.createElementNS(NS_OTA, "ota:Address");
			airTravlerElem.appendChild(Address);
			AirBookProcessor.createAddress(ownerDoc, airTravlerElem, Address, traveller);
		}
		else if(!leadPaxJsonObj.has(JSON_PROP_ADDRDTLS) || leadPaxJsonObj.getJSONObject(JSON_PROP_ADDRDTLS).length()==0) {
			Element Address = ownerDoc.createElementNS(NS_OTA, "ota:Address");
			airTravlerElem.appendChild(Address);
			//JSONObject orgEntityJson =MDMUtils.getOrgHierarchyDocumentByIdv2(MDM_VAL_TYPECOMPANY, usrCtx.getOrganizationHierarchy().getCompanyId());
			org.bson.Document orgEntityDoc =MDMUtils.getOrgHierarchyDocumentById(MDM_VAL_TYPECOMPANY, usrCtx.getOrganizationHierarchy().getCompanyId());
			JSONObject orgEntityJson=new JSONObject(new JSONTokener(orgEntityDoc.toJson()));
			AirBookProcessor.createCompanyAddress(ownerDoc,Address, orgEntityJson);
		}
		else {
			Element Address = ownerDoc.createElementNS(NS_OTA, "ota:Address");
			airTravlerElem.appendChild(Address);
			AirBookProcessor.createAddress(ownerDoc, airTravlerElem, Address, leadPaxJsonObj);
		}
		
		

		// For minors documentDetails information might not be available
		JSONObject documentDetails = traveller.optJSONObject(JSON_PROP_DOCDTLS);
		if (documentDetails != null && documentDetails.length()>0) {
			JSONArray documentInfoJsonArr = documentDetails.getJSONArray(JSON_PROP_DOCINFO);
			for (int l = 0; l < documentInfoJsonArr.length(); l++) {
				JSONObject documentInfoJson = documentInfoJsonArr.getJSONObject(l);
				if (documentInfoJson != null && documentInfoJson.toString().trim().length() > 0)
					AirBookProcessor.createDocuments(ownerDoc, airTravlerElem, documentInfoJson, traveller);
			}
		}
		Element travellerRefNoElem = ownerDoc.createElementNS(NS_OTA, "ota:TravelerRefNumber");
		travellerRefNoElem.setAttribute("RPH", String.valueOf(travellerIdx));
		airTravlerElem.appendChild(travellerRefNoElem);

		return travellerElem;
	}

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException, ValidationException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		TripIndicator tripInd=null;
		
		try {
			TrackingContext.setTrackingContext(reqJson);
			opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			
			validateRequestParameters(reqJson);
			
			tripInd = AirSearchProcessor.deduceTripIndicator(reqHdrJson, reqBodyJson);
            reqBodyJson.put(JSON_PROP_TRIPIND, tripInd.toString());
			
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson);
				}
		catch (ValidationException x) {
			throw x;
			}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
			}

		try {	
            Element resElem = null;
            //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AirConfig.getHttpHeaders(), reqElem);
            //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
            resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
            if (resElem == null) {
            	throw new Exception("Null response received from SI");
            }
       
            int idx = 0;
            JSONObject resBodyJson = new JSONObject();
            JSONArray bookRefsJsonArr = new JSONArray();
            JSONArray pricedItinsJsonArr = new JSONArray();
            Element[] resWrapperElems = AirPriceProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirPriceRSWrapper"));
            Map<String,String> redisPtcPriceItinMap=new HashMap<String,String>();
            for (Element resWrapperElem : resWrapperElems) {
            	Element resBodyElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_AirPriceRS"); 
            	// BigDecimal totalFarePtcBrkDwn=new BigDecimal(0);
            	 JSONObject suppBookPriceJson = new JSONObject();
            	getSupplierResponsePricedItinerariesJSON(resBodyElem, pricedItinsJsonArr, true, idx++,redisPtcPriceItinMap,reqBodyJson.getJSONArray(JSON_PROP_PAXINFO),suppBookPriceJson);  
            	JSONObject bookRefJson = new JSONObject();
                
                //suppBookPriceJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(resBodyElem.getAttribute("ItinTotalPrice"), 0));
                if(resBodyElem.hasAttribute("ItinTotalPrice") ) {
                	suppBookPriceJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(resBodyElem.getAttribute("ItinTotalPrice"), 0));
                }
                
                bookRefJson.put(JSON_PROP_SUPPBOOKFARE, suppBookPriceJson);
                bookRefJson.put(JSON_PROP_SUPPBOOKID, resBodyElem.getAttribute("TransactionIdentifier"));
                bookRefsJsonArr.put(bookRefJson);
            }
            resBodyJson.put(JSON_PROP_TRIPIND, tripInd.toString());
            resBodyJson.put(JSON_PROP_PRICEDITIN, pricedItinsJsonArr);
            resBodyJson.put(JSON_PROP_BOOKREFS, bookRefsJsonArr);

            JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);
            logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));

            List<Integer> sourceSupplierCommIndexes=new ArrayList<Integer>();
            
			JSONObject resSupplierJson =  SupplierCommercials.getSupplierCommercialsV3(CommercialsOperation.Booking, reqJson, resJson,sourceSupplierCommIndexes);
			JSONObject resClientJson = ClientCommercials.getClientCommercialsV2(reqJson, resJson, resSupplierJson);
			AirSearchProcessor.calculatePricesV5(reqJson, resJson, resSupplierJson, resClientJson, true, usrCtx,sourceSupplierCommIndexes);

			// Calculate company taxes
			TaxEngine.getCompanyTaxes(reqJson, resJson);

		/*	pushSuppFaresToRedisAndRemove(resJson);
			pushPtcFareBrkDwntoRedis(redisPtcPriceItinMap,reqHdrJson);*/
			
			//CompanyOffers.getCompanyOffers(reqJson, resJson, OffersConfig.Type.COMPANY_SEARCH_TIME,CommercialsOperation.Reprice);
			try {
				CompanyOffers.getCompanyOffers(reqJson, resJson, OffersType.COMPANY_SEARCH_TIME,CommercialsOperation.Reprice);
			}
			catch(Exception ex) {
				logger.warn("There was an error in processing company offers");
			}
			
			
			pushSuppFaresToRedisAndRemove(resJson);
			pushPtcFareBrkDwntoRedis(redisPtcPriceItinMap,reqHdrJson);
			
		//Commenting out as discussed with Ratan that when api doscuiment was created on Aprril 1st this was not added. So that is why we are removing it from time being. We will add it back with the cabin class changes and update the document and then share.

			/*TimeLimitProcessor.validateTimeLimit(reqJson, resJson,usrCtx);
			if(!resJson.getJSONObject(JSON_PROP_RESBODY).getJSONObject(JSON_PROP_TIMELIMIT).optBoolean(JSON_PROP_TIMLIMAPPL)) {		
				PartPaymentProcessor.validateTheData(reqJson, resJson,usrCtx);
			}*/
			
			
			matchRequestAndResponseData(resJson,reqJson);
			return resJson.toString();
		}
		catch (Exception x) {
			  logger.error("Exception received while processing", x);
			  throw new InternalProcessingException(x);
		}
	}
	
	public static void matchRequestAndResponseData(JSONObject resJson, JSONObject reqJson) {
		
		JSONArray reqPricedItinJsonArr=reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_PRICEDITIN);
		JSONArray resPricedItinJsonArr=resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRICEDITIN);
		
		for(int i=0;i<resPricedItinJsonArr.length();i++) {
			JSONArray reqOdoJsonArray=reqPricedItinJsonArr.getJSONObject(i).getJSONObject(JSON_PROP_AIRITINERARY).getJSONArray(JSON_PROP_ORIGDESTOPTS);
			JSONArray resOdoJsonArray=resPricedItinJsonArr.getJSONObject(i).getJSONObject(JSON_PROP_AIRITINERARY).getJSONArray(JSON_PROP_ORIGDESTOPTS);
			for(int j=0;j<resOdoJsonArray.length();j++) {
				JSONArray reqflightSegJsonArr=reqOdoJsonArray.getJSONObject(j).getJSONArray(JSON_PROP_FLIGHTSEG);
				JSONArray resflightSegJsonArr=resOdoJsonArray.getJSONObject(j).getJSONArray(JSON_PROP_FLIGHTSEG);
				for(int k=0;k<resflightSegJsonArr.length();k++) {
					JSONObject reqflightSegJson=reqflightSegJsonArr.getJSONObject(k);
					JSONObject resflightSegJson=resflightSegJsonArr.getJSONObject(k);
					
					resflightSegJson.put(JSON_PROP_AVAILCOUNT, reqflightSegJson.optInt(JSON_PROP_AVAILCOUNT));
					resflightSegJson.put(JSON_PROP_DEPARTTERMINAL, reqflightSegJson.optString(JSON_PROP_DEPARTTERMINAL));
					resflightSegJson.put(JSON_PROP_JOURNEYDUR, reqflightSegJson.optInt(JSON_PROP_JOURNEYDUR));
					resflightSegJson.put(JSON_PROP_ARRIVETERMINAL, reqflightSegJson.optString(JSON_PROP_ARRIVETERMINAL));
					resflightSegJson.put(JSON_PROP_STATUS, reqflightSegJson.optString(JSON_PROP_STATUS));
					
				}
				
			}
			
			
			
		}
		
	}

	static Map<String, String> getBookRefIdsFromRetrieveresponse(JSONObject reqHdrJson, JSONObject pricedItinJson,Map<String, String> pnrValueMap) {

		try {
			ServiceConfig opConfig = AirConfig.getOperationConfig("retrievepnr");
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();
			
			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_ReadRQWrapper");
			reqBodyElem.removeChild(wrapperElem);
			
			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			
			AirSearchProcessor.createHeader(reqHdrJson, reqElem);
			
			int seqNo = 1;
			Map<String,Integer> supplierSequenceMap=new HashMap<String,Integer>();
				
			String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
			Element suppWrapperElem = null;

			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
				
			ProductSupplier prodSupplier=usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT,suppID);
			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}
				
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
			Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem, String.format("./com:SupplierCredentials[com:SupplierID = '%s']", suppID));
			if (suppCredsElem == null) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}
			Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:SupplierID");
			suppIDElem.setTextContent(suppID);
				
			if(supplierSequenceMap.containsKey(suppID)) {
				int mapSeqVal=supplierSequenceMap.get(suppID)+1;
				supplierSequenceMap.replace(suppID, mapSeqVal);
			}
			else {
				supplierSequenceMap.put(suppID, seqNo);
			}
				
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:Sequence");
			sequenceElem.setTextContent(String.valueOf(supplierSequenceMap.get(suppID)));
				
			Element readRqElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_ReadRQ");
				
			Element uniqueIdElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_ReadRQ/ota:UniqueID");
				
			uniqueIdElem.setAttribute("Type","14");
			uniqueIdElem.setAttribute("ID", pnrValueMap.get(JSON_PROP_AIRLINEPNR));
				
			Element uniqueIdElem1 = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
			uniqueIdElem1.setAttribute("Type","24");
			uniqueIdElem1.setAttribute("ID", pnrValueMap.get(JSON_PROP_GDSPNR));
			readRqElem.appendChild(uniqueIdElem1);
						
			Element resElem = null;
			resElem = HTTPServiceConsumer.consumeXMLService("SI/Cancel", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
		    Map<String,String> typeIdMap=new HashMap<>();
			for(Element bookRefId:XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper/ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID")) {
				typeIdMap.put(bookRefId.getAttribute("Type"), bookRefId.getAttribute("ID"));
			}
   
			return typeIdMap;
		}
		catch(Exception E) {
			return null;
		}
	}

	static Map<String,String> getPNRsFromDb(String bookId, JSONObject reqHdrJson, String orderId) throws MalformedURLException, Exception {
		JSONObject orderDetailsDB =HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_DBSERIVCE, new URL(String.format(DBServiceConfig.getDBServiceURL(), bookId)), DBServiceConfig.getHttpHeaders(), "GET", null);
		if(orderDetailsDB.has("ErrorMsg")) {
           	throw new ValidationException(reqHdrJson,"TRLERR037");
        }
		Map<String,String> PnrValueMap = null;
		JSONArray productsArr = orderDetailsDB.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRODS);
		if(productsArr != null) {
			PnrValueMap=new HashMap<>();
			for(int i=0;i<productsArr.length();i++) {
	        	JSONObject productObj = productsArr.getJSONObject(i);
	        	if(orderId.equals(productObj.getString(JSON_PROP_ORDERID))) {
	        		JSONObject orderDetailsObj = productObj.getJSONObject(JSON_PROP_ORDERDETAILS);
	        		PnrValueMap.put(JSON_PROP_AIRLINEPNR, orderDetailsObj.getString(JSON_PROP_AIRLINEPNR));
	        		PnrValueMap.put(JSON_PROP_GDSPNR,orderDetailsObj.getString("GDSPNR"));
	        		break;
	        	}
	        }	
		}
		else {
			throw new Exception("Product Array not present for given BookID"+bookId);
		}
		if(PnrValueMap.isEmpty())
			throw new Exception("order id "+orderId+"for bookID "+bookId+" is invalid");
		
		return PnrValueMap;
		
	}

	private static void validateRequestParameters(JSONObject reqJson) throws ValidationException {
		AirRequestValidator.validateTripType(reqJson);
		AirRequestValidator.validatePassengerCounts(reqJson);
		AirRequestValidator.validatePaxDetails(reqJson);
	}

	public static String opsProcess(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		JSONObject reqHdrJson = null, reqBodyJson = null;
		UserContext usrCtx = null;
		TripIndicator tripInd=null;
		
		try {
			//TrackingContext.setTrackingContext(reqJson);
			//opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			
			//validateRequestParameters(reqJson);
			 tripInd = AirSearchProcessor.deduceTripIndicator(reqHdrJson, reqBodyJson);
            reqBodyJson.put(JSON_PROP_TRIPIND, tripInd.toString());
			
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			//reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson);
				}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
					}

		try {
			JSONObject opsAmendmentJson=reqBodyJson.getJSONObject("opsAmendments");
			String actionItem=opsAmendmentJson.getString("actionItem");
            Element resElem = null;
            resElem =  XMLTransformer.toXMLElement(opsAmendmentJson.getString("siRepriceResponse"));
            if (resElem == null) {
            	throw new Exception("Null response received from SI");
            }
       
            int idx = 0;
            JSONObject resBodyJson = new JSONObject();
            JSONArray bookRefsJsonArr = new JSONArray();
            JSONArray pricedItinsJsonArr = new JSONArray();
            Element[] resWrapperElems = AirPriceProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirPriceRSWrapper"));
            Map<String,String> redisPtcPriceItinMap=new HashMap<String,String>();
            for (Element resWrapperElem : resWrapperElems) {
            	Element resBodyElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_AirPriceRS");
            	JSONObject suppBookPriceJson = new JSONObject();
            	getSupplierResponsePricedItinerariesJSON(resBodyElem, pricedItinsJsonArr, true, idx++,redisPtcPriceItinMap,reqBodyJson.getJSONArray(JSON_PROP_PAXINFO),suppBookPriceJson);  
            	
            	JSONObject bookRefJson = new JSONObject();
            	
            	if(resBodyElem.hasAttribute("ItinTotalPrice") ) {
                	suppBookPriceJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(resBodyElem.getAttribute("ItinTotalPrice"), 0));
                }
                
                //suppBookPriceJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(resBodyElem.getAttribute("ItinTotalPrice"), 0));
              
            	
            	suppBookPriceJson.put(JSON_PROP_CCYCODE, XMLUtils.getFirstElementAtXPath(resWrapperElem,"./ota:PricedItineraries/ota:PricedItinerary/ota:AirItineraryPricingInfo/ota:PTC_FareBreakdowns/ota:PTC_FareBreakdown/ota:PassengerFare/ota:BaseFare/@CurrencyCode"));
            	//suppBookPriceJson.put(JSON_PROP_CCYCODE, "INR");
                bookRefJson.put(JSON_PROP_SUPPBOOKFARE, suppBookPriceJson);
                bookRefJson.put(JSON_PROP_SUPPBOOKID, resBodyElem.getAttribute("TransactionIdentifier"));
                bookRefsJsonArr.put(bookRefJson);
            }
            resBodyJson.put(JSON_PROP_TRIPIND, tripInd.toString());
            resBodyJson.put(JSON_PROP_PRICEDITIN, pricedItinsJsonArr);
            resBodyJson.put(JSON_PROP_BOOKREFS, bookRefsJsonArr);

            JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);
            logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));

            List<Integer> sourceSupplierCommIndexes=new ArrayList<Integer>();
            
			JSONObject resSupplierJson =  SupplierCommercials.getSupplierCommercialsV3(CommercialsOperation.Booking, reqJson, resJson,sourceSupplierCommIndexes);
			/*if(actionItem.equalsIgnoreCase("amendEntityCommercials")){
				resSupplierJson=new JSONObject(opsAmendmentJson.getString("suppCommRs"));
			}*/
			JSONObject resClientJson = ClientCommercials.getClientCommercialsV2(reqJson, resJson, resSupplierJson);
			AirSearchProcessor.calculatePricesV5(reqJson, resJson, resSupplierJson, resClientJson, true, usrCtx,sourceSupplierCommIndexes);

			// Calculate company taxes
			TaxEngine.getCompanyTaxes(reqJson, resJson);
			
			CompanyOffers.getCompanyOffers(reqJson, resJson, OffersType.COMPANY_SEARCH_TIME,CommercialsOperation.Reprice);

			//pushSuppFaresToRedisAndRemove(resJson);
			//pushPtcFareBrkDwntoRedis(redisPtcPriceItinMap,reqHdrJson);
			
			return resJson.toString();
		}
		catch (Exception x) {
			  logger.error("Exception received while processing", x);
			  throw new InternalProcessingException(x);
		}
	}
	
	
	public static void  getSupplierResponsePricedItinerariesJSON(Element resBodyElem, JSONArray pricedItinsJsonArr, boolean generateBookRefIdx, int bookRefIdx, Map<String, String> redisPtcPriceItinMap, JSONArray paxInfoArray, JSONObject suppBookPriceJson) throws Exception {
	    	boolean isCombinedReturnJourney = Boolean.valueOf(XMLUtils.getValueAtXPath(resBodyElem, "./@CombinedReturnJourney"));
	    	
	    	
	    	 
	    	
	        Element[] pricedItinElems = XMLUtils.getElementsAtXPath(resBodyElem, "./ota:PricedItineraries/ota:PricedItinerary");
	        for (Element pricedItinElem : pricedItinElems) {
	            JSONObject pricedItinJson =getPricedItineraryJSON(pricedItinElem,paxInfoArray,suppBookPriceJson);
	           
	            pricedItinJson.put(JSON_PROP_TRANSACTID, XMLUtils.getValueAtXPath(resBodyElem, "./@TransactionIdentifier"));
	            pricedItinJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath((Element)resBodyElem.getParentNode(), "./air:SupplierID"));
	            pricedItinJson.put(JSON_PROP_ISRETURNJRNYCOMBINED, isCombinedReturnJourney);
	            if (generateBookRefIdx) {
	            	pricedItinJson.put(JSON_PROP_BOOKREFIDX, bookRefIdx);
	            }
	            
	            StringBuilder strBldr= new StringBuilder();
	            strBldr.append(getRedisKeyForPricedItinerary(pricedItinJson)+JSON_PROP_PTCXML);
	            redisPtcPriceItinMap.put(strBldr.toString(), XMLTransformer.toString((getRedisPtcFareBreakDown(pricedItinElem))));
	            
	            pricedItinsJsonArr.put(pricedItinJson);
	        }
	        
	        
	    }
	public static JSONObject getPricedItineraryJSON(Element pricedItinElem, JSONArray paxInfoArray, JSONObject suppBookPriceJson) throws Exception{
        JSONObject pricedItinJson = new JSONObject();
        Map<String, Element> ptcPaxFareInfoMap=new HashMap<String,Element>();
        Element airItinElem = XMLUtils.getFirstElementAtXPath(pricedItinElem, "./ota:AirItinerary");
        Element airItinPricingElem = XMLUtils.getFirstElementAtXPath(pricedItinElem, "./ota:AirItineraryPricingInfo");
        pricedItinJson.put(JSON_PROP_AIRPRICEINFO, getAirItineraryPricingJSON(airItinPricingElem,ptcPaxFareInfoMap,suppBookPriceJson,paxInfoArray));
        pricedItinJson.put(JSON_PROP_AIRITINERARY, AirSearchProcessor.getAirItineraryJSON(airItinElem,airItinPricingElem,ptcPaxFareInfoMap,paxInfoArray));
        
       

        return pricedItinJson;
    }
	 public static JSONObject getAirItineraryPricingJSON(Element airItinPricingElem, Map<String, Element> ptcPaxFareInfoMap, JSONObject suppBookPriceJson, JSONArray paxInfoArray)throws Exception {
	        JSONObject airItinPricingJson = new JSONObject();

	        JSONObject itinTotalFareJson = new JSONObject();
	        airItinPricingJson.put(JSON_PROP_ITINTOTALFARE, itinTotalFareJson);

	        JSONArray ptcFaresJsonArr = new JSONArray();
	        Element[] ptcFareBkElems = XMLUtils.getElementsAtXPath(airItinPricingElem, "./ota:PTC_FareBreakdowns/ota:PTC_FareBreakdown");
	        for (Element ptcFareBkElem : ptcFareBkElems) {
	            ptcFaresJsonArr.put(getPTCFareBreakdownJSON(ptcFareBkElem,suppBookPriceJson,paxInfoArray));
	            String flightRefNumberRPHList=(XMLUtils.getValueAtXPath(ptcFareBkElem, "./@FlightRefNumberRPHList")).toString();
	       
	           Element[] ptcFareInfo = XMLUtils.getElementsAtXPath(ptcFareBkElem, "./ota:FareInfo");
	           
	        		   /*getValueAtXPath(ptcFareBkElems, "./FareInfo");*/
	            
	           // ptcPaxFareInfoMap.put(ptcFareBkElem,((XMLUtils.getValueAtXPath "./ota:PassengerTypeQuantity/@Code")), getFareInfoMap(ptcFareBkElem));
	            String ptcPaxTypeCode = (XMLUtils.getValueAtXPath(ptcFareBkElem, "./ota:PassengerTypeQuantity/@Code")).toString();
	            String splitParm = "|" ;
	          // String[] flightRPHRef=null;
	          String[] flightRPHRef;
	          int rphCountLen = 0;
	           if(flightRefNumberRPHList.indexOf(splitParm)>0) {
	        	  flightRPHRef = flightRefNumberRPHList.split("|");
	        	  rphCountLen = flightRPHRef.length;
	           }
	           else {
	        	   flightRPHRef=new String[1];
	        	   //TODO :  Need to check?????
	        	   flightRPHRef[0]=flightRefNumberRPHList;
	        	   rphCountLen = flightRPHRef.length;
	           }
	         if(null!=(XMLUtils.getFirstElementAtXPath(ptcFareBkElem,  "./ota:FareInfo/ota:DepartureAirport"))) {
	        	 
	        	 for(int i=0;i<ptcFareInfo.length;i++) {
	        	      if(ptcFareInfo.length==1) {
	        	    	  Element ptcFareInfoElem=ptcFareInfo[0];
	        	    	  String fareInfoKey = XMLUtils.getValueAtXPath(ptcFareInfoElem, "./ota:DepartureAirport/@LocationCode").concat("|").concat(XMLUtils.getValueAtXPath(ptcFareInfoElem, "./ota:ArrivalAirport/@LocationCode").concat("|").concat(ptcPaxTypeCode));
	              		ptcPaxFareInfoMap.put(fareInfoKey, ptcFareInfoElem);
	        	      }
	        	      else
	        	      {
	        	    	  Element ptcFareInfoElem=ptcFareInfo[i];
	        	    	  String fareInfoKey = XMLUtils.getValueAtXPath(ptcFareInfoElem, "./ota:DepartureAirport/@LocationCode").concat("|").concat(XMLUtils.getValueAtXPath(ptcFareInfoElem, "./ota:ArrivalAirport/@LocationCode").concat("|").concat(ptcPaxTypeCode));
	              		ptcPaxFareInfoMap.put(fareInfoKey, ptcFareInfoElem);
	        	      }
	        	 }
	        	 
	         }
	         else {	       
	           //int rphCountLen = flightRPHRef.length;
	           // ptcPaxFareInfoMap.put(flightRPHRef[0], ptcFareInfoElem);
	        	 if(!flightRPHRef[0].isEmpty()) {
	        		 
	        	 
	            for(int i = 1 ; i <= rphCountLen ; i++) {
	            if(ptcFareInfo.length==1) {
	        
	            	Element ptcFareInfoElem=ptcFareInfo[0];      
	            		ptcPaxFareInfoMap.put(flightRPHRef[i-1].concat("|").concat(ptcPaxTypeCode), ptcFareInfoElem);
	            	
	            }
	            else {
	            	Element ptcFareInfoElem=ptcFareInfo[i];
	            	ptcPaxFareInfoMap.put(flightRPHRef[i-1].concat("|").concat(ptcPaxTypeCode), ptcFareInfoElem);

	            }
	        }
	         }
	        }
	        }
	        airItinPricingJson.put(JSON_PROP_PAXTYPEFARES, ptcFaresJsonArr);

	       
	        if((XMLUtils.getFirstElementAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR"))!=null) {
	        	
	       if((XMLUtils.getFirstElementAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR/ota:SpecialServiceRequests"))!=null) {
	        JSONArray ssrJsonArr = new JSONArray();
	        Element[] ssrElems = XMLUtils.getElementsAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR/ota:SpecialServiceRequests/ota:SpecialServiceRequest");
	        
	        for (Element ssrElem : ssrElems) {
	        	ssrJsonArr.put(getSSRJson(ssrElem,suppBookPriceJson));
	        }
	        airItinPricingJson.put(JSON_PROP_SPECIALSVCREQS, ssrJsonArr);
	        }
	       if((XMLUtils.getFirstElementAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR/ota:SeatRequests"))!=null) {
	    	   JSONArray seatMapJsonArr = new JSONArray();
	           Element[] seatMapElems = XMLUtils.getElementsAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR/ota:SeatRequests/ota:SeatRequest");
	          
	           for (Element seatMapElem : seatMapElems) {
	        	   seatMapJsonArr.put(AirSearchProcessor.getSeatMap(seatMapElem));
	           }
	           airItinPricingJson.put(JSON_PROP_SEATMAP, seatMapJsonArr);
	           }
	       }
	        
	        
	        return airItinPricingJson;
	    }
	 
	 public static JSONObject getSSRJson(Element ssrElem, JSONObject suppBookPriceJson) {
			
	    	JSONObject ssrJson= new JSONObject();
	    	
	    	ssrJson.put(JSON_PROP_SSRCODE, ssrElem.getAttribute("SSRCode"));
	    	ssrJson.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(ssrElem, "./ota:ServicePrice/@Total"));
	    	
	    	if(ssrJson.has(JSON_PROP_AMOUNT) && !(ssrJson.getString(JSON_PROP_AMOUNT).equalsIgnoreCase(""))) {
	    		BigDecimal suppBookPrice=suppBookPriceJson.getBigDecimal(JSON_PROP_AMOUNT);
	    		suppBookPrice=suppBookPrice.add(ssrJson.getBigDecimal(JSON_PROP_AMOUNT));
	    		suppBookPriceJson.put(JSON_PROP_AMOUNT, suppBookPrice);
	    	}
	    	
	    	JSONObject servicePriceJson= new JSONObject();
	    	servicePriceJson.put(JSON_PROP_CCYCODE,XMLUtils.getValueAtXPath(ssrElem, "./ota:ServicePrice/@CurrencyCode"));
	    	servicePriceJson.put(JSON_PROP_BASEFARE, XMLUtils.getValueAtXPath(ssrElem, "./ota:ServicePrice/ota:BasePrice/@Amount"));
	    	servicePriceJson.put(JSON_PROP_TAXES, XMLUtils.getValueAtXPath(ssrElem, "./ota:ServicePrice/ota:Taxes/@Amount"));
	    	
	    	ssrJson.put(JSON_PROP_SVCPRC,servicePriceJson );
	    	
			return ssrJson;
		}
	
	 public static JSONObject getPTCFareBreakdownJSON(Element ptcFareBkElem, JSONObject suppBookPriceJson, JSONArray paxInfoArray) throws Exception{
	        JSONObject paxFareJson = new JSONObject();
	        Map<String,Integer> paxInfoMap=new HashMap<String,Integer>();
	        for(int i=0;i<paxInfoArray.length();i++){
	        	JSONObject paxInfoJson=paxInfoArray.getJSONObject(i);
	        	paxInfoMap.put(paxInfoJson.getString(JSON_PROP_PAXTYPE), paxInfoJson.getInt(JSON_PROP_QTY));
	        }
	        
	        String currencyCode="";
	        paxFareJson.put(JSON_PROP_PAXTYPE, XMLUtils.getValueAtXPath(ptcFareBkElem, "./ota:PassengerTypeQuantity/@Code"));
	        Element baseFareElem = XMLUtils.getFirstElementAtXPath(ptcFareBkElem, "./ota:PassengerFare/ota:BaseFare");
	        JSONObject baseFareJson = new JSONObject();
	        BigDecimal baseFareAmt = new BigDecimal(0);
	        BigDecimal totalPtcAmt = new BigDecimal(0);
	        String baseFareCurrency = "";
	        if (baseFareElem != null) {
	            baseFareAmt = Utils.convertToBigDecimal(baseFareElem.getAttribute(XML_ATTR_AMOUNT), 0);
	            baseFareCurrency = baseFareElem.getAttribute(XML_ATTR_CURRENCYCODE);
	            currencyCode=baseFareCurrency;
	            baseFareJson.put(JSON_PROP_AMOUNT, baseFareAmt);
	            baseFareJson.put(JSON_PROP_CCYCODE, baseFareCurrency);
	        }
	        paxFareJson.put(JSON_PROP_BASEFARE, baseFareJson);

	        //----------------------------------------------------------------
	        // Taxes
	        // This code always calculates taxes/fees totals and retrieve currencycode. In SI standardization 3.0, the top level elements for
	        // ota:Taxes and ota:Fees parent elements will never have Amount and CurrencyCode attributes. Total amount and currency code for
	        // taxes and fees will need to be calculated from child tax/fee elements.

	        Element taxesElem = XMLUtils.getFirstElementAtXPath(ptcFareBkElem, "./ota:PassengerFare/ota:Taxes");
	        JSONObject taxesJson = new JSONObject();
	        BigDecimal taxesAmount = new BigDecimal(0);
	        String taxesCurrency = "";
	        JSONArray taxJsonArr = new JSONArray();
	        Element[] taxElems = XMLUtils.getElementsAtXPath(taxesElem, "./ota:Tax");
	        for (Element taxElem : taxElems) {
	            JSONObject taxJson = new JSONObject();
	            BigDecimal taxAmt = Utils.convertToBigDecimal(taxElem.getAttribute(XML_ATTR_AMOUNT), 0);
	            String taxCurrency = taxElem.getAttribute(XML_ATTR_CURRENCYCODE);
	            taxJson.put(JSON_PROP_TAXCODE, taxElem.getAttribute("TaxCode"));
	            taxJson.put(JSON_PROP_AMOUNT, taxAmt);
	            taxJson.put(JSON_PROP_CCYCODE, taxCurrency);
	            taxJsonArr.put(taxJson);
	            taxesAmount = taxesAmount.add(taxAmt);
	            taxesCurrency = taxCurrency;
	        }

	        taxesJson.put(JSON_PROP_AMOUNT, taxesAmount);
	        taxesJson.put(JSON_PROP_CCYCODE, taxesCurrency);
	        taxesJson.put(JSON_PROP_TAX, taxJsonArr);
	        paxFareJson.put(JSON_PROP_TAXES, taxesJson);

	        //----------------------------------------------------------------
	        // Fees
	        // This code always calculates taxes/fees totals and retrieve currencycode. In SI standardization 3.0, the top level elements for
	        // ota:Taxes and ota:Fees parent elements will never have Amount and CurrencyCode attributes. Total amount and currency code for
	        // taxes and fees will need to be calculated from child tax/fee elements.

	        Element feesElem = XMLUtils.getFirstElementAtXPath(ptcFareBkElem, "./ota:PassengerFare/ota:Fees");
	        JSONObject feesJson = new JSONObject();
	        BigDecimal feesAmount = new BigDecimal(0);
	        String feesCurrency = "";

	        JSONArray feeJsonArr = new JSONArray();
	        Element[] feeElems = XMLUtils.getElementsAtXPath(feesElem, "./ota:Fee");
	        for (Element feeElem : feeElems) {
	            JSONObject feeJson = new JSONObject();
	            BigDecimal feeAmt = Utils.convertToBigDecimal(feeElem.getAttribute(XML_ATTR_AMOUNT), 0);
	            String feeCurrency = feeElem.getAttribute(XML_ATTR_CURRENCYCODE);
	            feeJson.put(JSON_PROP_FEECODE, feeElem.getAttribute("FeeCode"));
	            feeJson.put(JSON_PROP_AMOUNT, feeAmt);
	            feeJson.put(JSON_PROP_CCYCODE, feeCurrency);
	            feeJsonArr.put(feeJson);
	            feesAmount = feesAmount.add(feeAmt);
	            feesCurrency = feeCurrency;
	        }

	        feesJson.put(JSON_PROP_AMOUNT, feesAmount);
	        feesJson.put(JSON_PROP_CCYCODE, feesCurrency);
	        feesJson.put(JSON_PROP_FEE, feeJsonArr);
	        paxFareJson.put(JSON_PROP_FEES, feesJson);
	        //----------------------------------------------------------------

	        JSONObject totalFareJson = new JSONObject();
	        totalFareJson.put(JSON_PROP_AMOUNT, baseFareAmt.add(taxesAmount).add(feesAmount));
	        totalFareJson.put(JSON_PROP_CCYCODE, baseFareCurrency);
	        paxFareJson.put(JSON_PROP_TOTALFARE, totalFareJson);
	        
	        if(suppBookPriceJson.has(JSON_PROP_AMOUNT)) {
	        	//totalPtcAmt=suppBookPriceJson.getBigDecimal(JSON_PROP_AMOUNT);
	        	BigDecimal quantity=new BigDecimal(paxInfoMap.get(paxFareJson.getString(JSON_PROP_PAXTYPE)));
	        	BigDecimal prevAmt=suppBookPriceJson.getBigDecimal(JSON_PROP_AMOUNT);
	        	
		        totalPtcAmt= totalPtcAmt.add(totalFareJson.getBigDecimal(JSON_PROP_AMOUNT));
	        	totalPtcAmt=totalPtcAmt.multiply(quantity);
	        	totalPtcAmt=prevAmt.add(totalPtcAmt);
	        	
		        suppBookPriceJson.put(JSON_PROP_AMOUNT, totalPtcAmt);
		        suppBookPriceJson.put(JSON_PROP_CCYCODE, currencyCode);
	        }
	        else {
	        	totalPtcAmt= totalPtcAmt.add(totalFareJson.getBigDecimal(JSON_PROP_AMOUNT));
	     
	        	BigDecimal quantity=new BigDecimal(paxInfoMap.get(paxFareJson.getString(JSON_PROP_PAXTYPE)));
	        	totalPtcAmt=totalPtcAmt.multiply(quantity);
	        	suppBookPriceJson.put(JSON_PROP_AMOUNT, totalPtcAmt);
	        	suppBookPriceJson.put(JSON_PROP_CCYCODE, currencyCode);
	        }
	        

	        return paxFareJson;
	    }
	
	private static Element getRedisPtcFareBreakDown(Element priceItinElem) {
		
		Element redisPtcFrBrkDwnsElem=XMLUtils.getFirstElementAtXPath(priceItinElem, "./ota:AirItineraryPricingInfo/ota:PTC_FareBreakdowns");
		Element[] ptcBrkDwnElems=XMLUtils.getElementsAtXPath(priceItinElem, "./ota:AirItineraryPricingInfo/ota:PTC_FareBreakdowns/ota:PTC_FareBreakdown");
		for (Element ptcBrkDwnElem : ptcBrkDwnElems) {
    		
			
		
			//Element redisPtcFrBrkDwnElem=ownerDoc.createElementNS(NS_OTA,"ota:PTC_FareBreakdown");
			
			Element paxTypeElem=XMLUtils.getFirstElementAtXPath(ptcBrkDwnElem, "./ota:PassengerTypeQuantity");
			Node[] paxTypeNodes=XMLUtils.getNodesAtXPath(ptcBrkDwnElem, "./ota:PassengerTypeQuantity");
			
			Element tpaEtxElem=XMLUtils.getFirstElementAtXPath(ptcBrkDwnElem, "./ota:FareInfo/ota:FareInfo/ota:TPA_Extensions");
			if(tpaEtxElem!=null) {
				Element offersElem=XMLUtils.getFirstElementAtXPath(tpaEtxElem, "./air:Offers");
				if(offersElem!=null) {
					tpaEtxElem.removeChild(offersElem);
				}
			}
			
			
			
			
			
			XMLUtils.removeNodes(paxTypeNodes);
			
			//redisPtcFrBrkDwnElem.appendChild(paxTypeElem);
			
			Element[] fareInfoElems=XMLUtils.getElementsAtXPath(ptcBrkDwnElem, "./ota:FareInfo");
			Element[] ptcChildElems=XMLUtils.getAllChildElements(ptcBrkDwnElem);
			XMLUtils.removeNodes(ptcChildElems);
			
			ptcBrkDwnElem.appendChild(paxTypeElem);
			
			
			for (Element fareInfoElem : fareInfoElems) {
				ptcBrkDwnElem.appendChild(fareInfoElem);
			}
			
			//redisPtcFrBrkDwnsElem.appendChild(redisPtcFrBrkDwnElem);
    		
    	}
		
		return redisPtcFrBrkDwnsElem;
    	
		
		
	}

	protected static Map<String, String> createOriginDestinationOptions(Document ownerDoc, JSONObject pricedItinJson,
			Element odosElem, Map<String, JSONObject> flightSegJsonMap) throws ValidationException {
		JSONObject airItinJson = pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY);
		JSONArray odoJsonArr = airItinJson.optJSONArray(JSON_PROP_ORIGDESTOPTS);
		if(odoJsonArr==null) {
			throw new ValidationException("TRLERR025");
		}
		Map<String,String> flightMap=new HashMap<String,String>();
		
		for (int i=0; i < odoJsonArr.length(); i++) {
			JSONObject odoJson = odoJsonArr.getJSONObject(i);
			Element odoElem = ownerDoc.createElementNS(NS_OTA, "ota:OriginDestinationOption");
			JSONArray flSegJsonArr = odoJson.getJSONArray(JSON_PROP_FLIGHTSEG);
			for (int j=0; j < flSegJsonArr.length(); j++) {
				JSONObject flSegJson = flSegJsonArr.getJSONObject(j);
				Element flSegElem = ownerDoc.createElementNS(NS_OTA, "ota:FlightSegment");

				Element depAirportElem = ownerDoc.createElementNS(NS_OTA, "ota:DepartureAirport");
				depAirportElem.setAttribute("LocationCode", flSegJson.getString("originLocation"));
				flSegElem.appendChild(depAirportElem);

				Element arrAirportElem = ownerDoc.createElementNS(NS_OTA, "ota:ArrivalAirport");
				arrAirportElem.setAttribute("LocationCode", flSegJson.getString("destinationLocation"));
				flSegElem.appendChild(arrAirportElem);
				
				JSONObject mkAirlineJson = flSegJson.getJSONObject(JSON_PROP_MARKAIRLINE);
				Element opAirlineElem = ownerDoc.createElementNS(NS_OTA,"ota:OperatingAirline");
				JSONObject opAirlineJson = flSegJson.getJSONObject(JSON_PROP_OPERAIRLINE);
				flSegElem.setAttribute("FlightNumber", mkAirlineJson.optString((JSON_PROP_FLIGHTNBR),""));
				//flightSet.add(Integer.parseInt(opAirlineJson.getString(JSON_PROP_FLIGHTNBR).replaceAll("\\s","")));
				flightMap.put((opAirlineJson.getString(JSON_PROP_FLIGHTNBR).replaceAll("\\s","")).toString(), flSegJson.getString(JSON_PROP_RESBOOKDESIG));
				flightSegJsonMap.put(opAirlineJson.getString(JSON_PROP_FLIGHTNBR), flSegJson);
				flSegElem.setAttribute("DepartureDateTime", flSegJson.getString(JSON_PROP_DEPARTDATE));
				flSegElem.setAttribute("ArrivalDateTime", flSegJson.getString(JSON_PROP_ARRIVEDATE));
				flSegElem.setAttribute("ConnectionType", flSegJson.optString(JSON_PROP_CONNTYPE));
				flSegElem.setAttribute("Status", flSegJson.optString(JSON_PROP_STATUS));
				opAirlineElem.setAttribute("Code", opAirlineJson.getString(JSON_PROP_AIRLINECODE));
				opAirlineElem.setAttribute("FlightNumber", opAirlineJson.getString(JSON_PROP_FLIGHTNBR));
				String companyShortName = opAirlineJson.optString(JSON_PROP_COMPANYSHORTNAME, "");
				if (companyShortName.isEmpty() == false) {
					opAirlineElem.setAttribute("CompanyShortName", companyShortName);
				}
				flSegElem.appendChild(opAirlineElem);

				Element tpaExtsElem = ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
				Element extRPHElem = ownerDoc.createElementNS(NS_AIR, "air:ExtendedRPH");
				extRPHElem.setTextContent(flSegJson.getString(JSON_PROP_EXTENDEDRPH));
				tpaExtsElem.appendChild(extRPHElem);

				Element quoteElem = ownerDoc.createElementNS(NS_AIR, "air:QuoteID");
				quoteElem.setTextContent(flSegJson.getString(JSON_PROP_QUOTEID));
				tpaExtsElem.appendChild(quoteElem);

				flSegElem.appendChild(tpaExtsElem);

				Element mkAirlineElem = ownerDoc.createElementNS(NS_OTA, "ota:MarketingAirline");
				
				mkAirlineElem.setAttribute("Code", mkAirlineJson.getString(JSON_PROP_AIRLINECODE));
				/*String mkAirlineFlNbr = mkAirlineJson.optString(JSON_PROP_FLIGHTNBR, "");
				if (mkAirlineFlNbr.isEmpty() == false) {
					mkAirlineElem.setAttribute("FlightNumber", mkAirlineFlNbr);
				}*/
				String mkAirlineShortName = mkAirlineJson.optString(JSON_PROP_COMPANYSHORTNAME, "");
				if (mkAirlineShortName.isEmpty() == false) {
					mkAirlineElem.setAttribute(JSON_PROP_COMPANYSHORTNAME, mkAirlineShortName);
				}
				flSegElem.appendChild(mkAirlineElem);

				Element bookClsAvailsElem = ownerDoc.createElementNS(NS_OTA, "BookingClassAvails");
				if(flSegJson.optString(JSON_PROP_CABINTYPE)==null) {
					throw new ValidationException("TRLERR035");
				}
				bookClsAvailsElem.setAttribute("CabinType", flSegJson.getString(JSON_PROP_CABINTYPE));
				Element bookClsAvailElem = ownerDoc.createElementNS(NS_OTA, "ota:BookingClassAvail");
				bookClsAvailElem.setAttribute("ResBookDesigCode", flSegJson.getString(JSON_PROP_RESBOOKDESIG));
				bookClsAvailElem.setAttribute("RPH", flSegJson.getString(JSON_PROP_RPH));
				bookClsAvailsElem.appendChild(bookClsAvailElem);
				flSegElem.appendChild(bookClsAvailsElem);

				odoElem.appendChild(flSegElem);
			}

			odosElem.appendChild(odoElem);
		}
		return flightMap;
	}
		

	static void createSSRElem(Document ownerDoc, Element tpExElem, int travellerRPH, JSONObject traveller, Map<String, String> flightMap, Element sellBySSRElem) {
	
		
		Element ssrElem=XMLUtils.getFirstElementAtXPath(tpExElem, "./air:SSR");
		
		if(ssrElem==null) {
			 ssrElem=ownerDoc.createElementNS(NS_AIR, "air:SSR");
		}
		
		
		//Element ssrElem=ownerDoc.createElementNS(NS_AIR, "air:SSR");
		
		Element specialServiceRequestsElem=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:SpecialServiceRequests");
		
		
		if(specialServiceRequestsElem==null) {
			specialServiceRequestsElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequests");
		}
		
		
		

//Element specialServiceRequestsElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequests");


JSONArray specialServiceRequests=traveller.getJSONObject("specialRequests").getJSONArray("specialRequestInfo");





for(int s=0;s<specialServiceRequests.length();s++)
{


JSONObject specialServiceRequestJson=specialServiceRequests.getJSONObject(s);



if(!flightMap.containsKey(specialServiceRequestJson.getString((JSON_PROP_FLIGHTNBR)).replaceAll("\\s","")))
{
	continue;
}
sellBySSRElem.setTextContent("true");

	Element specialServiceRequestElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequest");
	
specialServiceRequestElem.setAttribute("TravelerRefNumberRPHList", Integer.toString(travellerRPH));

if((specialServiceRequestJson.getString(JSON_PROP_FLIGHREFNBR).equals("")))
{
	specialServiceRequestElem.setAttribute("FlightRefNumberRPHList", specialServiceRequestJson.getString(JSON_PROP_FLIGHTNBR));
}
else {
	specialServiceRequestElem.setAttribute("FlightRefNumberRPHList", specialServiceRequestJson.getString(JSON_PROP_FLIGHREFNBR));
}


specialServiceRequestElem.setAttribute("SSRCode", specialServiceRequestJson.optString(JSON_PROP_SSRCODE, ""));

specialServiceRequestElem.setAttribute("ServiceQuantity", specialServiceRequestJson.optString(JSON_PROP_SVCQTY,""));

specialServiceRequestElem.setAttribute("Type", specialServiceRequestJson.optString(JSON_PROP_TYPE,""));

specialServiceRequestElem.setAttribute("Status", specialServiceRequestJson.optString(JSON_PROP_STATUS,""));


if(specialServiceRequestJson.has(JSON_PROP_NUMBER) && !specialServiceRequestJson.get(JSON_PROP_NUMBER).equals("")){
	
	specialServiceRequestElem.setAttribute("Number", specialServiceRequestJson.optString(JSON_PROP_NUMBER,"0"));
}

Element airlineElem=ownerDoc.createElementNS(NS_OTA, "ota:Airline");

airlineElem.setAttribute("CompanyShortName", specialServiceRequestJson.optString((JSON_PROP_COMPANYSHORTNAME),""));

airlineElem.setAttribute("Code", specialServiceRequestJson.optString((JSON_PROP_AIRLINECODE),""));

specialServiceRequestElem.appendChild(airlineElem);


Element textElem=ownerDoc.createElementNS(NS_OTA, "ota:Text");

textElem.setTextContent(specialServiceRequestJson.optString((JSON_PROP_DESC),""));

specialServiceRequestElem.appendChild(textElem);


Element flightLegElem=ownerDoc.createElementNS(NS_OTA, "ota:FlightLeg");

flightLegElem.setAttribute("FlightNumber", specialServiceRequestJson.getString((JSON_PROP_FLIGHTNBR)));

flightLegElem.setAttribute("ResBookDesigCode",flightMap.get((specialServiceRequestJson.getString((JSON_PROP_FLIGHTNBR))).replaceAll("\\s","")).toString());

specialServiceRequestElem.appendChild(flightLegElem);


Element categoryCodeElem=ownerDoc.createElementNS(NS_OTA, "ota:CategoryCode");

categoryCodeElem.setTextContent(specialServiceRequestJson.optString((JSON_PROP_CATCODE),""));

specialServiceRequestElem.appendChild(categoryCodeElem);

Element travelerRefElem=ownerDoc.createElementNS(NS_OTA,"ota:TravelerRef");

travelerRefElem.setTextContent(traveller.getString(JSON_PROP_PAXTYPE));

specialServiceRequestElem.appendChild(travelerRefElem);


if(specialServiceRequestJson.has(JSON_PROP_AMOUNT) && !specialServiceRequestJson.get(JSON_PROP_AMOUNT).equals(""))
{
Element servicePriceElem=ownerDoc.createElementNS(NS_OTA,"ota:ServicePrice");


servicePriceElem.setAttribute("Total", specialServiceRequestJson.optString(JSON_PROP_AMOUNT));
servicePriceElem.setAttribute("CurrencyCode", specialServiceRequestJson.optString(JSON_PROP_CCYCODE));

if(specialServiceRequestJson.has(JSON_PROP_SVCPRC) && !specialServiceRequestJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_BASEPRICE).equals(""))
{
	Element basePriceElem=ownerDoc.createElementNS(NS_OTA,"ota:BasePrice");

	basePriceElem.setAttribute("Amount",specialServiceRequestJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_BASEPRICE));

	servicePriceElem.appendChild(basePriceElem);
}

if(specialServiceRequestJson.has(JSON_PROP_TAXES))
{
	Element taxesElem=ownerDoc.createElementNS(NS_OTA,"ota:Taxes");

	taxesElem.setAttribute("Amount",specialServiceRequestJson.getJSONObject(JSON_PROP_TAXES).optString(JSON_PROP_AMOUNT));

	servicePriceElem.appendChild(taxesElem);
}



specialServiceRequestElem.appendChild(servicePriceElem);
}

specialServiceRequestsElem.appendChild(specialServiceRequestElem);

ssrElem.appendChild(specialServiceRequestsElem);

tpExElem.appendChild(ssrElem);


}

	}

	static String getRedisKeyForPricedItinerary(JSONObject pricedItinJson) {
		StringBuilder strBldr = new StringBuilder(pricedItinJson.optString(JSON_PROP_SUPPREF));
		
		JSONObject airItinJson = pricedItinJson.optJSONObject(JSON_PROP_AIRITINERARY);
		if (airItinJson != null) {
			JSONArray origDestOptsJsonArr = airItinJson.getJSONArray(JSON_PROP_ORIGDESTOPTS);
			for (int j = 0; j < origDestOptsJsonArr.length(); j++) {
				JSONObject origDestOptJson = origDestOptsJsonArr.getJSONObject(j);
				strBldr.append('[');
				JSONArray flSegsJsonArr = origDestOptJson.optJSONArray(JSON_PROP_FLIGHTSEG);
				if (flSegsJsonArr == null) {
					break;
				}

				for (int k = 0; k < flSegsJsonArr.length(); k++) {
					JSONObject flSegJson = flSegsJsonArr.getJSONObject(k);
					JSONObject opAirlineJson = flSegJson.getJSONObject(JSON_PROP_OPERAIRLINE);
					strBldr.append(opAirlineJson.getString(JSON_PROP_AIRLINECODE).concat(opAirlineJson.getString(JSON_PROP_FLIGHTNBR)).concat("|"));
				}
				strBldr.setLength(strBldr.length() - 1);
				strBldr.append(']');
			}
		}
		return strBldr.toString();
	}
	
	 private static void pushPtcFareBrkDwntoRedis(Map<String, String> redisPtcPriceItinMap, JSONObject reqHdrJson) {
			Map<String,String> redisPtcMap=new HashMap<String,String>();
			redisPtcMap=redisPtcPriceItinMap;
		 	StringBuilder redisKeyBldr=new StringBuilder();
		 	//JSON_PROP_PTCXML
		 	
		 	redisKeyBldr.append(reqHdrJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT_AIR));
		 	redisKeyBldr.append(JSON_PROP_PTCXML);
			
			try ( Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
			redisConn.hmset(redisKeyBldr.toString(), redisPtcMap);
			redisConn.pexpire(redisKeyBldr.toString(), (long) (AirConfig.getRedisTTLMinutes() * 60 * 1000));
			}
		}

	
	private static void pushSuppFaresToRedisAndRemove(JSONObject resJson) {
		JSONObject resHeaderJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray pricedItinsJsonArr = resBodyJson.optJSONArray(JSON_PROP_PRICEDITIN);
		JSONArray bookRefsJsonArr = resBodyJson.optJSONArray(JSON_PROP_BOOKREFS);
		resBodyJson.remove(JSON_PROP_BOOKREFS);
		
		if (pricedItinsJsonArr == null || bookRefsJsonArr == null) {
			logger.warn("PricedItinerary or BookRefJson Found null");
			return;
		}
		
		Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();
		for (int i=0; i < pricedItinsJsonArr.length(); i++) {
			JSONObject pricedItinJson = pricedItinsJsonArr.getJSONObject(i);
			JSONObject suppPriceInfoJson = pricedItinJson.optJSONObject(JSON_PROP_SUPPPRICEINFO);
			pricedItinJson.remove(JSON_PROP_SUPPPRICEINFO);

			int bookRefIdx = pricedItinJson.getInt(JSON_PROP_BOOKREFIDX);
			JSONObject bookRefJson = bookRefsJsonArr.optJSONObject(bookRefIdx);
			pricedItinJson.remove(JSON_PROP_BOOKREFIDX);
			
			//Getting ClientCommercial Info
			JSONArray clientCommercialItinInfoJsonArr = pricedItinJson.optJSONArray(JSON_PROP_CLIENTCOMMITININFO);
			pricedItinJson.remove(JSON_PROP_CLIENTCOMMITININFO);
			
			JSONArray clientCommercialItinTotalJsonArr = pricedItinJson.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			pricedItinJson.remove(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			
			// Retrieve and save the 'airItineraryPricingInfo'. Do not remove from pricedItinJson.
			// The saved 'airItineraryPricingInfo' can be used in booking operation
			JSONObject airPriceInfoJson = pricedItinJson.getJSONObject(JSON_PROP_AIRPRICEINFO);
			
			if ( suppPriceInfoJson == null || bookRefJson == null) {
				continue;
			}
			
			JSONObject suppPriceBookInfoJson = new JSONObject();
			suppPriceBookInfoJson.put(JSON_PROP_SUPPPRICEINFO, suppPriceInfoJson);
			suppPriceBookInfoJson.put(JSON_PROP_BOOKREFS, bookRefJson);
			suppPriceBookInfoJson.put(JSON_PROP_CLIENTCOMMITININFO, clientCommercialItinInfoJsonArr);
			suppPriceBookInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercialItinTotalJsonArr);
			suppPriceBookInfoJson.put(JSON_PROP_AIRPRICEINFO, new JSONObject(new JSONTokener(airPriceInfoJson.toString())));
			suppPriceBookInfoJson.put(JSON_PROP_ENABLERSUPPID, pricedItinJson.optString(JSON_PROP_ENABLERSUPPID));
			pricedItinJson.remove(JSON_PROP_ENABLERSUPPID);
			suppPriceBookInfoJson.put(JSON_PROP_SOURCESUPPID, pricedItinJson.optString(JSON_PROP_SOURCESUPPID));
			pricedItinJson.remove(JSON_PROP_SOURCESUPPID);
			
			removeCompanyOffers(airPriceInfoJson);
			reprcSuppFaresMap.put(getRedisKeyForPricedItinerary(pricedItinJson), suppPriceBookInfoJson.toString());
		}
		
		String redisKey = resHeaderJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT_AIR);
		try ( Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
		redisConn.hmset(redisKey, reprcSuppFaresMap);
		redisConn.pexpire(redisKey, (long) (AirConfig.getRedisTTLMinutes() * 60 * 1000));
		}
	}

	private static void removeCompanyOffers(JSONObject airPriceInfoJson) {
		JSONArray paxTypeFareArr=airPriceInfoJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
		if(airPriceInfoJson.has(JSON_PROP_OFFERS))
		{
			airPriceInfoJson.remove(JSON_PROP_OFFERS);
		}
		
		for(int i=0;i<paxTypeFareArr.length();i++) {
			JSONObject paxTypeFare=paxTypeFareArr.getJSONObject(i);
			if(paxTypeFare.has(JSON_PROP_OFFERS))
			{
				paxTypeFare.remove(JSON_PROP_OFFERS);
			}
		}
		
	}

	private static boolean hasGetSSRBeenInvokedForPricedItin(JSONObject reqHdrJson, JSONObject pricedItinJson) {
		String pricedItinRedisKey = getRedisKeyForPricedItinerary(pricedItinJson);
		String sessionID = reqHdrJson.optString(JSON_PROP_SESSIONID);
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool()) {
			String pricedItinGetSSR = redisConn.hget(String.format("%s|AIR|GetSSR", sessionID), "AIR|GetSSR");
			return (pricedItinGetSSR != null);
		}
		catch (Exception x) {
			logger.warn(String.format("An error occurred while retrieving GetSSR state for %s in session %s", pricedItinRedisKey, sessionID), x);
			return false;
		}
			}
	
	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_AirPriceRQWrapper");
		reqBodyElem.removeChild(wrapperElem);

		String bookId = reqBodyJson.optString(JSON_PROP_BOOKID);
		
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		String prevSuppID = "";
		JSONArray pricedItinsJSONArr = reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
		for (int y=0; y < pricedItinsJSONArr.length(); y++) {
			Map<String,String> flightMap=new HashMap<String,String>();
			JSONObject pricedItinJson = pricedItinsJSONArr.getJSONObject(y);
		 
		
			String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
			Element suppWrapperElem = null;
			Element otaReqElem = null;
			Element travelerInfoElem = null;
			Element priceInfoElem = null;
			//create adtRphListHere
			JSONArray travellerJsonArr = reqBodyJson.getJSONArray(JSON_PROP_PAXDETAILS);
			
			List<Integer> adtPaxRphList=new ArrayList<Integer>();
			for(int i=0;i< travellerJsonArr.length();i++) {
				JSONObject paxInfo=travellerJsonArr.getJSONObject(i);
				
				if((paxInfo.getString(JSON_PROP_PAXTYPE)).equalsIgnoreCase(JSON_PROP_ADT)) {
					adtPaxRphList.add(i);
				}
			
			}
			
			
			if (suppID.equals(prevSuppID)) {
				suppWrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, String.format("./air:OTA_AirPriceRQWrapper[air:SupplierID = '%s']", suppID));
				travelerInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirPriceRQ/ota:TravelerInfoSummary");
				otaReqElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirPriceRQ");
				priceInfoElem = XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:PriceRequestInformation");
			}
			else {
				suppWrapperElem = (Element) wrapperElem.cloneNode(true);
				reqBodyElem.appendChild(suppWrapperElem);
				
				ProductSupplier prodSupplier=usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT,suppID);
				if (prodSupplier == null) {
					throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
				}

				Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
				Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem, String.format("./com:SupplierCredentials[com:SupplierID = '%s']", suppID));
				if (suppCredsElem == null) {
					suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
				}

				Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:SupplierID");
				suppIDElem.setTextContent(suppID);

				Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:Sequence");
				sequenceElem.setTextContent(String.valueOf(y));
				
				travelerInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirPriceRQ/ota:TravelerInfoSummary");
				otaReqElem = (Element) travelerInfoElem.getParentNode();
				
				//This case is implemented for Ops
				if(Utils.isStringNotNullAndNotEmpty(bookId)) {
					String orderId = pricedItinJson.optString(JSON_PROP_ORDERID);
					if(Utils.isStringNullOrEmpty(orderId)) {
					    logger.warn("orderId not found or is invalid");
						throw new Exception("orderId not found");
					}
					Map<String,String> pnrValueMap=getPNRsFromDb(bookId,reqHdrJson,orderId);
					Map<String,String> typeIdMap=getBookRefIdsFromRetrieveresponse(reqHdrJson,pricedItinJson,pnrValueMap);
					if(!typeIdMap.isEmpty()) {
						 Element airPriceRqElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirPriceRQ");
						 Iterator<Entry<String, String>> typeIdItr = typeIdMap.entrySet().iterator();
						 while(typeIdItr.hasNext()) {
							 Entry<String, String> Itr = typeIdItr.next();
							 Element bookRefId = ownerDoc.createElementNS(NS_OTA, "ota:BookingReferenceID"); 
							 bookRefId.setAttribute("ID",Itr.getValue());
							 bookRefId.setAttribute("Type",Itr.getKey());
							 airPriceRqElem.appendChild(bookRefId);
						 }
					}						
				}
				
				//Element priceRqElem= XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirPriceRQ");
				otaReqElem.setAttribute("TransactionIdentifier", pricedItinJson.optString(JSON_PROP_TRANSACTID));

				priceInfoElem = XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:PriceRequestInformation");
				Element tpExElem = ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
				
				
				Element repriceFlowIndElem = ownerDoc.createElementNS(NS_AIR, "air:RepriceFlowIndicators");
				//Keep this sellIndicatorElem false if getSSR called before repice.
				Element sellIndicatorElem = ownerDoc.createElementNS(NS_AIR, "air:SellIndicator");
				//sellIndicatorElem.setTextContent("false");
				String hasSsrBeenInvoked=Boolean.toString( ! hasGetSSRBeenInvokedForPricedItin(reqHdrJson, pricedItinJson));
				sellIndicatorElem.setTextContent(hasSsrBeenInvoked);
				repriceFlowIndElem.appendChild(sellIndicatorElem);
				
				
				Element updateContactsElem = ownerDoc.createElementNS(NS_AIR, "air:UpdateContacts");
				//updateContactsElem.setTextContent("true");
				updateContactsElem.setTextContent(hasSsrBeenInvoked);
				repriceFlowIndElem.appendChild(updateContactsElem);
				//Keep this sellIndicatorElem false is no SSR are passed in the Request(to be done after standardization).
				Element sellBySSRIndicatorElem = ownerDoc.createElementNS(NS_AIR, "air:SellBySSRIndicator");
				sellBySSRIndicatorElem.setTextContent("false");
				repriceFlowIndElem.appendChild(sellBySSRIndicatorElem);
				
				Element assignSeatsElem = ownerDoc.createElementNS(NS_AIR, "air:AssignSeats");
				assignSeatsElem.setTextContent("false");
				repriceFlowIndElem.appendChild(assignSeatsElem);
				
				Element updatePassengerElem = ownerDoc.createElementNS(NS_AIR, "air:UpdatePassenger");
				updatePassengerElem.setTextContent("true");
				
				repriceFlowIndElem.appendChild(updatePassengerElem);
				
				tpExElem.appendChild(repriceFlowIndElem);
				priceInfoElem.appendChild(tpExElem);
				
				JSONArray travellerArr = reqBodyJson.getJSONArray(JSON_PROP_PAXDETAILS);
				JSONObject leadPaxJsonObj=null;
				
				/*List<Integer> adtPaxRphList=new ArrayList<Integer>();
				for(int i=0;i< travellerArr.length();i++) {
					JSONObject paxInfo=travellerArr.getJSONObject(i);
					
					if((paxInfo.getString(JSON_PROP_PAXTYPE)).equalsIgnoreCase(JSON_PROP_ADT)) {
						adtPaxRphList.add(i);
					}
				
				}*/
				
				
				for (int i=0,adtPaxListIdx=0; i < travellerArr.length(); i++) {
					
					if(i==0) {
						leadPaxJsonObj=	travellerArr.getJSONObject(0);
					}
					
					JSONObject traveller = (JSONObject) travellerArr.get(i);
					Element travellerElem = getAirTravelerAvailElement(ownerDoc, traveller, i,usrCtx,leadPaxJsonObj,adtPaxRphList,adtPaxListIdx);
					if((traveller.getString(JSON_PROP_PAXTYPE)).equalsIgnoreCase(JSON_PROP_INF)) {
						adtPaxListIdx++;
					}
					travelerInfoElem.insertBefore(travellerElem, priceInfoElem);
				}	
				
			}
			Element odosElem = null;
			if (suppID.equals(prevSuppID)) {
				odosElem = XMLUtils.getFirstElementAtXPath(otaReqElem, "./ota:AirItinerary/ota:OriginDestinationOptions");
				if (odosElem == null) {
					logger.warn(String.format("XML element for ota:OriginDestinationOptions not found for supplier %s", suppID));
				}
			}
			else {
				Element airItinElem = ownerDoc.createElementNS(NS_OTA, "ota:AirItinerary");
				airItinElem.setAttribute("DirectionInd", reqBodyJson.getString(JSON_PROP_TRIPTYPE));
				odosElem = ownerDoc.createElementNS(NS_OTA, "ota:OriginDestinationOptions");
				airItinElem.appendChild(odosElem);
				otaReqElem.insertBefore(airItinElem, travelerInfoElem);
			}				
			Map<String,JSONObject> flightSegJsonMap=new HashMap<String,JSONObject>();
			flightMap=createOriginDestinationOptions(ownerDoc, pricedItinJson, odosElem,flightSegJsonMap);
			
		
			Element tpExtensionElem=XMLUtils.getFirstElementAtXPath(travelerInfoElem,"./ota:PriceRequestInformation/ota:TPA_Extensions");
			JSONArray paxDetailsJsonArr = reqBodyJson.getJSONArray(JSON_PROP_PAXDETAILS);
			Element sellBySSRElem = XMLUtils.getFirstElementAtXPath(tpExtensionElem, "./air:RepriceFlowIndicators/air:SellBySSRIndicator");
			Element assignSeatsElem = XMLUtils.getFirstElementAtXPath(tpExtensionElem, "./air:RepriceFlowIndicators/air:AssignSeats");
			int adtPaxListIdx=0;
			for (int i=0; i < paxDetailsJsonArr.length(); i++) {
				JSONObject traveller = (JSONObject) paxDetailsJsonArr.get(i);
				
				String paxType=traveller.getString(JSON_PROP_PAXTYPE);
				
				if(paxType.equalsIgnoreCase(JSON_PROP_INF)) {
					
					createINFSSRElem(ownerDoc, tpExtensionElem, i, traveller,sellBySSRElem,flightSegJsonMap,adtPaxRphList,adtPaxListIdx);
					adtPaxListIdx++;
				}
				
				//getSSR for passengers
				if(!traveller.isNull(JSON_PROP_SPECIALREQS))
				{
					//sellBySSRElem.setTextContent("true");
					createSSRElem(ownerDoc, tpExtensionElem, i, traveller,flightMap,sellBySSRElem);
				}
				if(!traveller.isNull(JSON_PROP_SEATMAP)) {
					createSeatMapElem(ownerDoc, tpExtensionElem, i, traveller,flightMap,assignSeatsElem,flightSegJsonMap);
				}
				
			}
	
			AirPriceProcessor.createTPA_Extensions(reqBodyJson, travelerInfoElem);
			prevSuppID = suppID;
		}

		return reqElem;
	}

	private static void createINFSSRElem(Document ownerDoc, Element tpExtensionElem, int i, JSONObject traveller,
			Element sellBySSRElem, Map<String, JSONObject> flightSegJsonMap, List<Integer> adtPaxRphList, int adtPaxListIdx) {
	
		
		
		sellBySSRElem.setTextContent("true");
		
		
		for(Map.Entry<String, JSONObject> entry :flightSegJsonMap.entrySet())
		{
			JSONObject flightSegJson=entry.getValue();
			
			Element ssrElem=XMLUtils.getFirstElementAtXPath(tpExtensionElem, "./air:SSR");
			
			if(ssrElem==null) {
				 ssrElem=ownerDoc.createElementNS(NS_AIR, "air:SSR");
			}
			
			
			//Element ssrElem=ownerDoc.createElementNS(NS_AIR, "air:SSR");
			
			Element specialServiceRequestsElem=XMLUtils.getFirstElementAtXPath(ssrElem, "./ota:SpecialServiceRequests");
			
			
			if(specialServiceRequestsElem==null) {
				specialServiceRequestsElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequests");
			}
			
			
			

	//Element specialServiceRequestsElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequests");


	//JSONArray specialServiceRequests=traveller.getJSONObject("specialRequests").getJSONArray("specialRequestInfo");



	//JSONObject specialServiceRequestJson=specialServiceRequests.getJSONObject(s);

	

		Element specialServiceRequestElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequest");
		
	specialServiceRequestElem.setAttribute("TravelerRefNumberRPHList",Integer.toString(adtPaxRphList.get(adtPaxListIdx)));

	//put flightRefHere?
	specialServiceRequestElem.setAttribute("FlightRefNumberRPHList", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).optString(JSON_PROP_FLIGHTNBR));



	specialServiceRequestElem.setAttribute("SSRCode", "INFT");

	specialServiceRequestElem.setAttribute("ServiceQuantity", "1");

	specialServiceRequestElem.setAttribute("Type", "");

	specialServiceRequestElem.setAttribute("Status","");

	specialServiceRequestElem.setAttribute("Number","");

	
	Element airlineElem=ownerDoc.createElementNS(NS_OTA, "ota:Airline");

	airlineElem.setAttribute("CompanyShortName", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).optString((JSON_PROP_AIRLINECODE),""));

	airlineElem.setAttribute("Code", "");

	specialServiceRequestElem.appendChild(airlineElem);


	Element textElem=ownerDoc.createElementNS(NS_OTA, "ota:Text");

	textElem.setTextContent("");

	specialServiceRequestElem.appendChild(textElem);


	Element flightLegElem=ownerDoc.createElementNS(NS_OTA, "ota:FlightLeg");

	flightLegElem.setAttribute("FlightNumber", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).optString(JSON_PROP_FLIGHTNBR));

	flightLegElem.setAttribute("ResBookDesigCode",flightSegJson.optString(JSON_PROP_RESBOOKDESIG));

	specialServiceRequestElem.appendChild(flightLegElem);


	Element categoryCodeElem=ownerDoc.createElementNS(NS_OTA, "ota:CategoryCode");

	categoryCodeElem.setTextContent("");

	specialServiceRequestElem.appendChild(categoryCodeElem);

	Element travelerRefElem=ownerDoc.createElementNS(NS_OTA,"ota:TravelerRef");

	travelerRefElem.setTextContent(traveller.getString(JSON_PROP_PAXTYPE));

	specialServiceRequestElem.appendChild(travelerRefElem);

/*
	if(specialServiceRequestJson.has(JSON_PROP_AMOUNT) && !specialServiceRequestJson.get(JSON_PROP_AMOUNT).equals(""))
	{
	Element servicePriceElem=ownerDoc.createElementNS(NS_OTA,"ota:ServicePrice");


	servicePriceElem.setAttribute("Total", specialServiceRequestJson.optString(JSON_PROP_AMOUNT));
	servicePriceElem.setAttribute("CurrencyCode", specialServiceRequestJson.optString(JSON_PROP_CCYCODE));

	if(specialServiceRequestJson.has(JSON_PROP_SVCPRC) && !specialServiceRequestJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_BASEPRICE).equals(""))
	{
		Element basePriceElem=ownerDoc.createElementNS(NS_OTA,"ota:BasePrice");

		basePriceElem.setAttribute("Amount",specialServiceRequestJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_BASEPRICE));

		servicePriceElem.appendChild(basePriceElem);
	}

	if(specialServiceRequestJson.has(JSON_PROP_TAXES))
	{
		Element taxesElem=ownerDoc.createElementNS(NS_OTA,"ota:Taxes");

		taxesElem.setAttribute("Amount",specialServiceRequestJson.getJSONObject(JSON_PROP_TAXES).optString(JSON_PROP_AMOUNT));

		servicePriceElem.appendChild(taxesElem);
	}



	specialServiceRequestElem.appendChild(servicePriceElem);
	}*/

	specialServiceRequestsElem.appendChild(specialServiceRequestElem);

	ssrElem.appendChild(specialServiceRequestsElem);

	tpExtensionElem.appendChild(ssrElem);
	
		}
	}

	private static void createSeatMapElem(Document ownerDoc, Element tpExtensionElem, int i, JSONObject traveller,
			Map<String, String> flightMap, Element sellBySSRElem, Map<String, JSONObject> flightSegJsonMap) {
	
		Element ssrElem=XMLUtils.getFirstElementAtXPath(tpExtensionElem, "./air:SSR");
		
		if(ssrElem==null) {
			 ssrElem=ownerDoc.createElementNS(NS_AIR, "air:SSR");
		}
		
		Element seatMapReqsElem=XMLUtils.getFirstElementAtXPath(tpExtensionElem, "./ota:SeatRequests");
		
		if(seatMapReqsElem==null) {
			seatMapReqsElem=ownerDoc.createElementNS(NS_OTA, "ota:SeatRequests");
		}
		JSONArray seatMapJsonArr=traveller.getJSONArray(JSON_PROP_SEATMAP);
		JSONObject seatMapObj=seatMapJsonArr.getJSONObject(0);
		if(flightSegJsonMap.containsKey(seatMapObj.optString(JSON_PROP_FLIGHTNBR))) {
			
			sellBySSRElem.setTextContent("true");
		JSONObject flightSegJson = flightSegJsonMap.get(seatMapObj.optString(JSON_PROP_FLIGHTNBR));
		
		
		JSONArray seatMapArr = traveller.getJSONArray(JSON_PROP_SEATMAP);
		
		for(int j = 0 ; j < seatMapArr.length() ; j++) {
			
		JSONObject seatMapJson = seatMapArr.getJSONObject(j);
		
		//cabin class Array
		
		JSONArray cabinClassArr = seatMapJson.getJSONArray(JSON_PROP_CABINCLASS);
		for(int y = 0 ; y < cabinClassArr.length() ; y++) {
			
			JSONObject cabinClassJSon = cabinClassArr.getJSONObject(y);
			
			
			JSONArray rowInfoArr = cabinClassJSon.getJSONArray(JSON_PROP_ROWINFO);
			for(int z=0;z<rowInfoArr.length();z++) {
				JSONObject rowInfoJson = rowInfoArr.getJSONObject(z);
				
				JSONArray seatInfoArr = rowInfoJson.getJSONArray(JSON_PROP_SEATINFO);
				
				for(int t = 0 ; t < seatInfoArr.length() ; t++) {
					JSONObject seatInfoJson = seatInfoArr.getJSONObject(t);
		
		//for(int i = 0 ;i < ; i++)
		Element seatMapRequestElem=ownerDoc.createElementNS(NS_OTA, "ota:SeatRequest");
		
		/*seatMapRequestElem.setAttribute("PartialSeatingInd", "");
		seatMapRequestElem.setAttribute("DepartureDate", flightSegJson.optString("departureDate"));*/
		
		//seatMapRequestElem.setAttribute("FlightNumber", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).optString(JSON_PROP_FLIGHTNBR));
		seatMapRequestElem.setAttribute("RowNumber", rowInfoJson.optString(JSON_PROP_ROWNBR));
		seatMapRequestElem.setAttribute("SeatNumber", seatInfoJson.optString(JSON_PROP_SEATNBR) );
		seatMapRequestElem.setAttribute("TravelerRefNumberRPHList", Integer.toString(i));
		if((!seatMapJson.has(JSON_PROP_FLIGHTREFRPH)) || (seatMapJson.optString((JSON_PROP_FLIGHTREFRPH)).equalsIgnoreCase(""))) {
			seatMapRequestElem.setAttribute("FlightRefNumberRPHList", seatMapJson.optString(JSON_PROP_FLIGHTNBR));
		}
		else {
			seatMapRequestElem.setAttribute("FlightRefNumberRPHList", seatMapJson.optString(JSON_PROP_FLIGHTREFRPH));
		}
		//seatMapRequestElem.setAttribute("FlightRefNumberRPHList", seatMapJson.optString((JSON_PROP_FLIGHTREFRPH),seatMapJson.optString(JSON_PROP_FLIGHTNBR)));
		seatMapRequestElem.setAttribute("SeatPreference", "");
		seatMapRequestElem.setAttribute("SeatSequenceNumber", seatInfoJson.optString(JSON_PROP_SEATSEQNBR));
		
	/*	seatMapRequestElem.setAttribute("Status", "");
		seatMapRequestElem.setAttribute("SeatPreference", "");
		seatMapRequestElem.setAttribute("DeckLevel", "");
		seatMapRequestElem.setAttribute("SeatInRow", "");
		seatMapRequestElem.setAttribute("SmokingAllowed", "");*/
		
		
		Element deptElem = ownerDoc.createElementNS(NS_OTA, "ota:DepartureAirport");
		deptElem.setAttribute("LocationCode", flightSegJson.getString(JSON_PROP_ORIGLOC));
		//deptElem.setAttribute("CodeContext", "");
		seatMapRequestElem.appendChild(deptElem);
		
		Element arrivalElem = ownerDoc.createElementNS(NS_OTA, "ota:ArrivalAirport");
		arrivalElem.setAttribute("LocationCode", flightSegJson.getString(JSON_PROP_DESTLOC));
		//arrivalElem.setAttribute("CodeContext", "");
		seatMapRequestElem.appendChild(arrivalElem);
		
		
		Element airlineElem = ownerDoc.createElementNS(NS_OTA, "ota:Airline");
	/*	airlineElem.setAttribute("CompanyShortName", "");
		airlineElem.setAttribute("TravelSector", "");*/
		airlineElem.setAttribute("Code", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).optString(JSON_PROP_FLIGHTNBR));
		/*airlineElem.setAttribute("CodeContext", "");
		airlineElem.setAttribute("CountryCode", "");
		airlineElem.setAttribute("Division", "");
		airlineElem.setAttribute("Department", "");*/
		
		seatMapRequestElem.appendChild(airlineElem);
		
		
		seatMapReqsElem.appendChild(seatMapRequestElem);
				}
			}
		}
		}
	}
		ssrElem.appendChild(seatMapReqsElem);
		tpExtensionElem.appendChild(ssrElem);
		
		}
	}

