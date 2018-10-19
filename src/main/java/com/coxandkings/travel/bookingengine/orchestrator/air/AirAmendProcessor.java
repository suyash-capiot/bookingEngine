package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.math.BigDecimal;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.DBServiceConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;


public class AirAmendProcessor implements AirConstants {
	private static final Logger logger = LogManager.getLogger(AirCancelProcessor.class);
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		KafkaBookProducer bookProducer = null;
		JSONObject currencyCodeJson=null;
		Map<String,JSONObject> requestSuppRefMap=new HashMap<String,JSONObject>();
		//Map<Integer,String> sequenceToOrderIdMap=null;
		Map<Integer,JSONObject> sequenceToOrderIdMap=null;
		 JSONObject orderDetailsDB =null;
		try {
			TrackingContext.setTrackingContext(reqJson);
			opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			
			orderDetailsDB = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_DBSERIVCE, new URL(String.format(DBServiceConfig.getDBServiceURL(), reqBodyJson.getString(JSON_PROP_BOOKID))), DBServiceConfig.getHttpHeaders(), "GET", reqJson);
			 if(orderDetailsDB.has("ErrorMsg")) {
	            	throw new ValidationException(reqHdrJson,"TRLERR037");
	            }
			 
			validateRequestParameters(reqJson);
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			 currencyCodeJson=new JSONObject();
			 sequenceToOrderIdMap=new HashMap<Integer,JSONObject>();
			reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson,requestSuppRefMap,currencyCodeJson,sequenceToOrderIdMap,orderDetailsDB);
			bookProducer = new KafkaBookProducer();
			sendPreAmendKafkaMessage(bookProducer,reqJson);
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
		
		try {
			
			Element resElem = null;
			resElem = HTTPServiceConsumer.consumeXMLService("SI/Amend", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
	         if (resElem == null) {
	         	throw new Exception("Null response received from SI");
	         }
	         JSONArray supplierRefArr=reqBodyJson.getJSONArray(JSON_PROP_SUPPBOOKREFS);
	         for(int i=0;i<supplierRefArr.length();i++) {
	        	 ToDoTaskSubType todoSubType;
		            String cancelType=reqBodyJson.getString(JSON_PROP_AMENDTYPE);
		            switch(cancelType) {
		            case JSON_PROP_AMENDTYPE_PIS:todoSubType=ToDoTaskSubType.PASSENGER;break;
		            case CANCEL_TYPE_SSR:todoSubType=ToDoTaskSubType.PASSENGER;break;
		            case JSON_PROP_AMENDTYPE_REM:todoSubType=ToDoTaskSubType.ORDER;break;
		            default:todoSubType=null;
		            }
		         OperationsToDoProcessor.callOperationTodo(ToDoTaskName.AMEND, ToDoTaskPriority.MEDIUM,todoSubType, supplierRefArr.getJSONObject(i).getString("orderID"), reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
	         }
	         
	         JSONObject resJson = new JSONObject();
	         resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
         
	         JSONObject resBodyJson = new JSONObject();
	  
	         JSONArray supplierBookReferencesResJsonArr = new JSONArray();
         
	         Element[] resWrapperElems = AirPriceProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper"));
	       
	         for (Element resWrapperElem : resWrapperElems) {
	        	// String orderID=sequenceToOrderIdMap.get(Integer.parseInt(XMLUtils.getValueAtXPath(resWrapperElem, "/air:Sequence")));
	        	 JSONObject seqBkRefJson=sequenceToOrderIdMap.get(Integer.parseInt(XMLUtils.getValueAtXPath(resWrapperElem, "./air:Sequence")));
	        	 String orderID=seqBkRefJson.getString(JSON_PROP_ORDERID);
	        	 JSONObject supplierBookReferencesResJson= new JSONObject();
	        	 if(XMLUtils.getFirstElementAtXPath(resWrapperElem, "./air:ErrorList")!=null)
	        	 {	
	        		 
	        		 Element errorMessage=XMLUtils.getFirstElementAtXPath(resWrapperElem, "./air:ErrorList/com:Error/com:ErrorCode");
	        		 String errMsgStr=errorMessage.getTextContent().toString();
	        		 if(CANCEL_SI_ERR.equalsIgnoreCase(errMsgStr))
	        		 {	
	        			 logger.error("Recieved Error from SI for Amend Operation as it is not supported by suppplier");
	        			 AirCancelProcessor.putSINotSupportedErrorMessage(supplierBookReferencesResJson,orderID);
	        			 supplierBookReferencesResJsonArr.put(supplierBookReferencesResJson);
	        			 continue;
	        		 }
	        		 else {
	        			 logger.error("Recieved Error from SI for Amend Operation");
	        			 AirCancelProcessor.putErrorMessage(supplierBookReferencesResJson,orderID);
	        			 supplierBookReferencesResJsonArr.put(supplierBookReferencesResJson);
	        			 continue;
	        		 }
         		
	        	 }
	        	 else {
	        		 
	        		 supplierBookReferencesResJson.put(JSON_PROP_SUPPREF, XMLUtils.getFirstElementAtXPath(resWrapperElem, "./air:SupplierID").getTextContent());
	        		 supplierBookReferencesResJson.put(JSON_PROP_BOOKREFID, seqBkRefJson.getString(JSON_PROP_BOOKREFID));
	     
	        		 supplierBookReferencesResJson.put("status", reqBodyJson.getString(JSON_PROP_AMENDTYPE)+" "+"type amend"+" "+"succesfully amended");
	        		
	        			if(reqBodyJson.getString(JSON_PROP_AMENDTYPE).equals(CANCEL_TYPE_SSR)) {
	       
	        				supplierBookReferencesResJson.put(JSON_PROP_SUPPLIERCHARGES, getSsrTotalCharge(requestSuppRefMap.get(supplierBookReferencesResJson.getString(JSON_PROP_BOOKREFID))));
	        			}
	        			else {
	        				supplierBookReferencesResJson.put(JSON_PROP_SUPPLIERCHARGES, "0");
	        			}
	        		 supplierBookReferencesResJsonArr.put(supplierBookReferencesResJson);
	        	 }
	         }
	         resBodyJson.put(JSON_PROP_SUPPBOOKREFS, supplierBookReferencesResJsonArr);
	         resJson.put(JSON_PROP_RESBODY, resBodyJson);
	         
	         
	       //TODO:company policy is not required to call; there will be another commercial service that will be used for only ssr amend
	         
	      //   JSONObject companyPolicyRes = null;
            /* JSONObject orderDetailsDB = HTTPServiceConsumer.produceJSONObjectResponse(TARGET_SYSTEM_DBSERIVCE, DBServiceConfig.getDBServiceURL(), DBServiceConfig.getHttpHeaders());
             if(orderDetailsDB.has("ErrorMsg")) {
             	throw new ValidationException(reqHdrJson,"TRLERR037");
             }*/
             
             //JSONArray productsArr = orderDetailsDB.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRODS);
            
	      /*   if(productsArr == null) 
	        	 logger.debug(String.format("Unable to create CompanyPolicy Request as no Bookings found for bookID: %s ", reqBodyJson.getString(JSON_PROP_BOOKID)));
	         else 
	        	 //Applying Company Policy
	        	 companyPolicyRes = AmClCompanyPolicy.getAmClCompanyPolicy(CommercialsOperation.Ammend, reqJson, resJson, productsArr);
         
        	 appendingCompanyPolicyInRes(supplierBookReferencesResJsonArr, companyPolicyRes);*/
            
	         bookProducer = new KafkaBookProducer();
	         sendPostAmendKafkaMessage(bookProducer,resJson,reqJson,currencyCodeJson);
	         return resJson.toString();
		}
		catch (Exception x) {
			// TODO: Is this the right thing to do? Or should this be pushed to operations Todo queue?
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}
	
	 private static void validateRequestParameters(JSONObject reqJson) throws JSONException, ValidationException {
		 AirRequestValidator.validatebookID(reqJson);
		 AirRequestValidator.validateSupplierBookRefs(reqJson);
		// AirRequestValidator.validatePaymentInfo(reqJson);
		
	}

	private static JSONArray getCompanyPoliciesBusinessRuleIntakeJSONArray(JSONObject commResJson) {
    	return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.air_companypolicies.Root").getJSONArray("businessRuleIntake");
	}
 
	private static void appendingCompanyPolicyInRes(JSONArray supplierBookRefResJsonArr, JSONObject companypolicyRes) {
		
		if(companypolicyRes==null)	
			return;
		
    	if (BRMS_STATUS_TYPE_FAILURE.equals(companypolicyRes.getString(JSON_PROP_TYPE))) {
    		logger.error(String.format("A failure response was received from Company Policy calculation engine: %s", companypolicyRes.toString()));
    		return;
    	}
		
		JSONArray briArr = getCompanyPoliciesBusinessRuleIntakeJSONArray(companypolicyRes);
		
		for(int i=0;i<supplierBookRefResJsonArr.length();i++) {
			
			JSONObject supplierBookRef = supplierBookRefResJsonArr.getJSONObject(i);
			JSONObject companyPolicyDtls = briArr.getJSONObject(i).optJSONObject("companyPolicyDetails");
			BigDecimal companyChrg = companyPolicyDtls!=null ? companyPolicyDtls.getBigDecimal("policyCharges") : BigDecimal.ZERO;
			BigDecimal totalCharge=companyChrg.add(supplierBookRef.getBigDecimal(JSON_PROP_SUPPLIERCHARGES));
			supplierBookRef.put(JSON_PROP_COMPANYCHARGES, totalCharge);
		}
	}
	
	private static void sendPreAmendKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson) throws Exception {
		JSONObject kafkaMsgJson = reqJson;
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		AirCancelProcessor.getEntityIdJsonArray(reqBodyJson);
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, PRODUCT_AIR);
		AmendDbEnum amendEnum=AmendDbEnum.valueOf(reqBodyJson.getString(JSON_PROP_AMENDTYPE));
		String dbType=amendEnum.getcancelDbEnum();
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_TYPE, dbType);
		
		if(reqBodyJson.getString(JSON_PROP_AMENDTYPE).equals(JSON_PROP_AMENDTYPE_REM)) {
			kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_ENTITYNAME, "order");
		}
		else
		{
			kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_ENTITYNAME, "pax");
		}
	
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_REQTYPE, "amend");
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put("operation", "amend");
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
		bookProducer.runProducer(1, kafkaMsgJson);
		logger.trace(String.format("Air Amend Request Kafka Message: %s", kafkaMsgJson.toString()));
	}
	
	private static void sendPostAmendKafkaMessage(KafkaBookProducer bookProducer,JSONObject resJson ,JSONObject reqJson, JSONObject currencyCodeJson) throws Exception {
		JSONObject kafkaMsgJson = new JSONObject();
		kafkaMsgJson.put(JSON_PROP_RESHEADER,new JSONObject( resJson.getJSONObject(JSON_PROP_RESHEADER).toString()));
		kafkaMsgJson.put(JSON_PROP_RESBODY,new JSONObject(resJson.getJSONObject(JSON_PROP_RESBODY).toString()));
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		
		Map<String,JSONObject> cancelRqSuppJsonArr=AirCancelProcessor.getEntityIdJsonArray(reqBodyJson);
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PROD, PRODUCT_AIR);
		AmendDbEnum amendEnum=AmendDbEnum.valueOf(reqBodyJson.getString(JSON_PROP_AMENDTYPE));
		String dbType=amendEnum.getcancelDbEnum();
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_TYPE, dbType);
		
		if(reqBodyJson.getString(JSON_PROP_AMENDTYPE).equals(JSON_PROP_AMENDTYPE_REM)) {
			kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_ENTITYNAME, "order");
		}
		else
		{
			kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_ENTITYNAME, "pax");
		}
		
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_REQTYPE, "amend");
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("operation", "amend");
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
		JSONArray suppBookRefsJsonArr=resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_SUPPBOOKREFS);
		JSONArray kafkaSuppBookRefsJsonArr=kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_SUPPBOOKREFS);
		for(int i=0;i<suppBookRefsJsonArr.length();i++) {
			JSONObject suppBookRefJson=suppBookRefsJsonArr.getJSONObject(i);
			
			if(AirCancelProcessor.checkIsOperationFailed(suppBookRefJson)) {
				continue;
			}
			
			JSONObject cancelRqSuppJson=cancelRqSuppJsonArr.get(suppBookRefJson.getString(JSON_PROP_SUPPREF)+"|"+suppBookRefJson.getString(JSON_PROP_BOOKREFID));
			
			
			JSONObject kafkaSuppBookRefJson=kafkaSuppBookRefsJsonArr.getJSONObject(i);
			kafkaSuppBookRefJson.put("orderID", cancelRqSuppJson.getString("orderID"));
			kafkaSuppBookRefJson.put("entityIDs", cancelRqSuppJson.getJSONArray("entityIDs"));
			kafkaSuppBookRefJson.put(JSON_PROP_SUPPLIERCHARGESCCYCODE, currencyCodeJson.optString(JSON_PROP_SUPPLIERCHARGESCCYCODE));
			
			kafkaSuppBookRefJson.put(JSON_PROP_SUPPLIERCHARGES,(suppBookRefJson.getString(JSON_PROP_SUPPLIERCHARGES)));
			
			
		kafkaSuppBookRefJson.put(JSON_PROP_COMPANYCHARGESCCYCODE,resJson.getJSONObject(JSON_PROP_RESHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).optString(JSON_PROP_CLIENTCCY));
		suppBookRefJson.put(JSON_PROP_COMPANYCHARGESCCYCODE, resJson.getJSONObject(JSON_PROP_RESHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).optString(JSON_PROP_CLIENTCCY));
		BigDecimal roe=RedisRoEData.getRateOfExchange(kafkaSuppBookRefJson.getString(JSON_PROP_SUPPLIERCHARGESCCYCODE), kafkaSuppBookRefJson.getString(JSON_PROP_COMPANYCHARGESCCYCODE), resJson.getJSONObject(JSON_PROP_RESHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).optString(JSON_PROP_CLIENTMARKET));
		//suppBookRefJson.put(JSON_PROP_COMPANYCHARGES, suppBookRefJson.getBigDecimal(JSON_PROP_COMPANYCHARGES).multiply(roe));
		suppBookRefJson.put(JSON_PROP_COMPANYCHARGES, suppBookRefJson.getBigDecimal(JSON_PROP_SUPPLIERCHARGES).multiply(roe));
		suppBookRefJson.remove(JSON_PROP_SUPPLIERCHARGES);
		kafkaSuppBookRefJson.put(JSON_PROP_COMPANYCHARGES, suppBookRefJson.getBigDecimal(JSON_PROP_COMPANYCHARGES));
		}
		bookProducer.runProducer(1, kafkaMsgJson);
		logger.trace(String.format("Air Amend Response Kafka Message: %s", kafkaMsgJson.toString()));
	}
	
	
	private static String getSsrTotalCharge(JSONObject suppBookRefJson) {
		
		BigDecimal totalSsrCharge=new BigDecimal(0);
		JSONArray paxDetailsJsonArr=suppBookRefJson.getJSONArray(JSON_PROP_PAXDETAILS);
		for(int j=0;j<paxDetailsJsonArr.length();j++)
		{
			JSONObject paxDetailJson=paxDetailsJsonArr.getJSONObject(j);
		
		JSONArray ssrArray=paxDetailJson.getJSONArray(JSON_PROP_SPECIALREQUESTINFO);
		
		for(int i=0;i<ssrArray.length();i++) {
			JSONObject ssrJson=ssrArray.getJSONObject(i);
			totalSsrCharge=totalSsrCharge.add(ssrJson.optBigDecimal(JSON_PROP_AMOUNT,new BigDecimal(0)));
		}
		}
		return totalSsrCharge.toString();
	}

	
	private static Map<String, String> getTravellRefMap(Element retrieveResElem) {
		
		Map<String,String> traverllerRefMap=new HashMap<String,String>();
		Element[] travellerInfoElems = XMLUtils.getElementsAtXPath(retrieveResElem,"./ota:OTA_AirBookRS/ota:AirReservation/ota:TravelerInfo/ota:AirTraveler");
		
		for (Element travellerInfoElem : travellerInfoElems) {
			traverllerRefMap.put((XMLUtils.getFirstElementAtXPath(travellerInfoElem, "./ota:PersonName/ota:GivenName").getTextContent().toString().toUpperCase()).concat(XMLUtils.getFirstElementAtXPath(travellerInfoElem, "./ota:PersonName/ota:Surname").getTextContent().toString().toUpperCase()), XMLUtils.getValueAtXPath(travellerInfoElem, "./ota:TravelerRefNumber/@RPH"));
		}
		
		return traverllerRefMap;
	}


	private static Element getRetrieveWrapperByBookRefId(String bookRefId, Element[] retreieveResWrapperElems) {

		Element reqWrapperElem = null;
		for (Element wrapperElem : retreieveResWrapperElems) {
			// TODO:Does this require more bookRefChecks?
			Element reqBookRsElem = XMLUtils.getFirstElementAtXPath(wrapperElem, String.format("./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@ID='%s']", bookRefId));
			if (reqBookRsElem != null) {
				reqWrapperElem = (Element) reqBookRsElem.getParentNode().getParentNode().getParentNode();
				break;
			}

		}
		return reqWrapperElem;
	}
	

	//public static Element getRetrieveResponse(JSONObject reqJson) {
	public static Element getRetrieveResponse(JSONObject reqHdrJson, JSONObject reqBodyJson,Map<String,String> orderGDSmap) {
		//JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null) ? reqJson.optJSONObject(JSON_PROP_REQHEADER) : new JSONObject();
		//JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null) ? reqJson.optJSONObject(JSON_PROP_REQBODY) : new JSONObject();
      
		try {
			//OperationConfig opConfig = AirConfig.getOperationConfig("retrievepnr");
			ServiceConfig opConfig = AirConfig.getOperationConfig("retrievepnr");
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();
			
			//TrackingContext.setTrackingContext(reqJson);
			
			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_ReadRQWrapper");
			reqBodyElem.removeChild(wrapperElem);
			
			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			
			AirSearchProcessor.createHeader(reqHdrJson, reqElem);
			
			int seqNo = 1;
			Map<String,Integer> supplierSequenceMap=new HashMap<String,Integer>();
			
				
			JSONArray supplierBookRefArr=reqBodyJson.getJSONArray("supplierBookReferences");
			for(int j=0;j<supplierBookRefArr.length();j++)
			{
				JSONObject suppBookRefJson = supplierBookRefArr.getJSONObject(j);
				
				String suppID = suppBookRefJson.getString(JSON_PROP_SUPPREF);
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
				uniqueIdElem.setAttribute("ID", suppBookRefJson.getString(JSON_PROP_BOOKREFID));
				
				String orderID = suppBookRefJson.getString("orderID");
				if(orderGDSmap.containsKey(orderID)) {
					String gdsPnr = orderGDSmap.get(orderID);
					if(!gdsPnr.isEmpty()) {
						Element uniqueIdElem1 = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
						uniqueIdElem1.setAttribute("Type","24");
						uniqueIdElem1.setAttribute("ID", gdsPnr);
						readRqElem.appendChild(uniqueIdElem1);
					}
				}
			}
		
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService("SI/Amend", opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService("SI/Amend", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
      
   
			return resElem;
		}
		catch(Exception E) {
			return null;
		}
	}

	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson, Map<String, JSONObject> requestSuppRefObject, JSONObject currencyCodeJson, Map<Integer, JSONObject> sequenceToOrderIdMap,JSONObject orderDetailsDB) throws Exception {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_AirBookModifyRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		
		//Map to store the GDS pnr of each order       
				Map<String,String> orderGDSmap=new HashMap<>();
				JSONArray productsArr = orderDetailsDB.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRODS);
				if(productsArr != null) {
					for(int i=0;i<productsArr.length();i++) {
			        	JSONObject productObj = productsArr.getJSONObject(i);
			        	if(PROD_CATEG_FLIGHT.equalsIgnoreCase(productObj.getString("productSubCategory"))) {
			        		JSONObject orderDetailsObj = productObj.getJSONObject("orderDetails");
			        		orderGDSmap.put(productObj.getString("orderID"), orderDetailsObj.optString("GDSPNR"));
			        	}
			        }	
				}
		
		//Element retrieveResElem=getRetrieveResponse(reqJson);
		Element retrieveResElem=getRetrieveResponse(reqHdrJson, reqBodyJson,orderGDSmap);
		logger.trace("Retrieve Res XML",XMLTransformer.toString(retrieveResElem));
		
		
		currencyCodeJson.put(JSON_PROP_SUPPLIERCHARGESCCYCODE, XMLUtils.getValueAtXPath(retrieveResElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper/ota:OTA_AirBookRS/ota:AirReservation/ota:PriceInfo/ota:PTC_FareBreakdowns/ota:PTC_FareBreakdown/ota:PassengerFare/ota:BaseFare/@CurrencyCode"));
		
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		
	
	
		Element[] retreieveResWrapperElems=AirPriceProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(retrieveResElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper"));
		
		JSONArray suppBookRefJsonArr=reqBodyJson.getJSONArray(JSON_PROP_SUPPBOOKREFS);
		
		for(int j=0;j<suppBookRefJsonArr.length();j++)
		{
			JSONObject suppBookRefJson = suppBookRefJsonArr.getJSONObject(j);
			String suppID = suppBookRefJson.getString(JSON_PROP_SUPPREF);
			
			
			
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
			
			
			
			Element supplierIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:SupplierID");
			supplierIDElem.setTextContent(suppID);
			
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:Sequence");
			
			sequenceElem.setTextContent(String.valueOf(j+1));
			sequenceToOrderIdMap.put(j+1, suppBookRefJson);
			
			if(!(suppBookRefJson.has(JSON_PROP_BOOKREFID)) && (suppBookRefJson.getString(JSON_PROP_BOOKREFID).equals(""))) {
				throw new ValidationException(reqHdrJson,"TRLERR039");
			}
			requestSuppRefObject.put(suppBookRefJson.getString(JSON_PROP_BOOKREFID), suppBookRefJson);
			Element retrieveResWrapperElem=getRetrieveWrapperByBookRefId(suppBookRefJson.getString(JSON_PROP_BOOKREFID),retreieveResWrapperElems);
			
			Element amendRQElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirBookModifyRQ/ota:AirBookModifyRQ");
			
			Element amendOtaRQElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirBookModifyRQ");
			amendOtaRQElem.setAttribute("Version",XMLUtils.getValueAtXPath(retrieveResWrapperElem, "./ota:OTA_AirBookRS/@Version"));
			
			Map<String,String> travellRefMap=getTravellRefMap(retrieveResWrapperElem);
			
			
			
			//AirItenerary
			if(reqBodyJson.getString(JSON_PROP_AMENDTYPE).equals(CANCEL_TYPE_SSR))
			{
				Element airItinElem=ownerDoc.createElementNS(NS_OTA, "ota:AirItinerary");
				
				Element originDestinationOptionsElem=ownerDoc.createElementNS(NS_OTA, "ota:OriginDestinationOptions");
				JSONArray odoJsonArr=suppBookRefJson.getJSONArray(JSON_PROP_ORIGDESTOPTS);
				if(odoJsonArr==null) {
					throw new ValidationException("TRLERR025");
				}
				for(int k=0;k<odoJsonArr.length();k++)
				{
					JSONObject odoJson=	odoJsonArr.getJSONObject(k);
					JSONArray flightSegJsonArr=odoJson.getJSONArray(JSON_PROP_FLIGHTSEG);
					Element originDestinationOptionElem=ownerDoc.createElementNS(NS_OTA, "ota:OriginDestinationOption");
					
				
					 for(int l=0;l<flightSegJsonArr.length();l++)
					 {
						JSONObject flightSegJson=flightSegJsonArr.getJSONObject(l);
						Element flightSegElem=ownerDoc.createElementNS(NS_OTA, "ota:FlightSegment");
						flightSegElem.setAttribute("DepartureDateTime", flightSegJson.getString(JSON_PROP_DEPARTDATE));
						flightSegElem.setAttribute("ArrivalDateTime",  flightSegJson.getString(JSON_PROP_ARRIVEDATE));
						
						flightSegElem.setAttribute("ConnectionType", flightSegJson.optString(JSON_PROP_CONNTYPE));
						flightSegElem.setAttribute("FlightNumber",  flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).getString(JSON_PROP_FLIGHTNBR));
						flightSegElem.setAttribute("RPH", Integer.toString(l));
						
						Element depAirportElem=ownerDoc.createElementNS(NS_OTA, "ota:DepartureAirport");
						depAirportElem.setAttribute("LocationCode", flightSegJson.getString(JSON_PROP_ORIGLOC));
						flightSegElem.appendChild(depAirportElem);
						
						Element arrAirportElem=ownerDoc.createElementNS(NS_OTA, "ota:ArrivalAirport");
						arrAirportElem.setAttribute("LocationCode", flightSegJson.getString(JSON_PROP_DESTLOC));
						flightSegElem.appendChild(arrAirportElem);
						
						Element operatingAirlineElem=ownerDoc.createElementNS(NS_OTA, "ota:OperatingAirline");
						operatingAirlineElem.setAttribute("FlightNumber", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).getString(JSON_PROP_FLIGHTNBR));
						operatingAirlineElem.setAttribute("Code", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).getString(JSON_PROP_AIRLINECODE));
						flightSegElem.appendChild(operatingAirlineElem);
						
						Element tpaEtxnElem=ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
						
						Element quoteElem=ownerDoc.createElementNS(NS_AIR, "air:QuoteID");
						quoteElem.setTextContent(flightSegJson.getString(JSON_PROP_QUOTEID).toString());
						tpaEtxnElem.appendChild(quoteElem);
						
						Element exntendedRphElem=ownerDoc.createElementNS(NS_AIR, "air:ExtendedRPH");
						Element tpaExtensions = XMLUtils.getFirstElementAtXPath(retrieveResWrapperElem, "./ota:OTA_AirBookRS/ota:AirReservation/ota:AirItinerary/ota:OriginDestinationOptions/ota:OriginDestinationOption/ota:FlightSegment/ota:TPA_Extensions");
						exntendedRphElem.setTextContent( XMLUtils.getValueAtXPath(tpaExtensions, "./air:ExtendedRPH"));
						XMLTransformer.toString(exntendedRphElem);
						tpaEtxnElem.appendChild(exntendedRphElem);
						flightSegElem.appendChild(tpaEtxnElem);
						
						Element marketingAirlineElem=ownerDoc.createElementNS(NS_OTA, "ota:MarketingAirline");
						marketingAirlineElem.setAttribute("Code",flightSegJson.getJSONObject(JSON_PROP_MARKAIRLINE).getString(JSON_PROP_AIRLINECODE));
						flightSegElem.appendChild(marketingAirlineElem);
						
						Element bookingClassAvailsElem=ownerDoc.createElementNS(NS_OTA, "ota:BookingClassAvails");
						if(flightSegJson.optString(JSON_PROP_CABINTYPE)==null) {
							throw new ValidationException("TRLERR035");
						}
						bookingClassAvailsElem.setAttribute("CabinType", flightSegJson.getString(JSON_PROP_CABINTYPE));
						
						Element bookingClassAvailElem=ownerDoc.createElementNS(NS_OTA, "ota:BookingClassAvail");
						bookingClassAvailElem.setAttribute("ResBookDesigCode", flightSegJson.getString(JSON_PROP_RESBOOKDESIG));
						
						bookingClassAvailsElem.appendChild(bookingClassAvailElem);
						flightSegElem.appendChild(bookingClassAvailsElem);
						
						originDestinationOptionElem.appendChild(flightSegElem);
					 }
					originDestinationOptionsElem.appendChild(originDestinationOptionElem);
				}
				airItinElem.appendChild(originDestinationOptionsElem);
				amendRQElem.appendChild(airItinElem);
			}
			
			
			
			/*//priceInfo starts
			Element priceInfoElem=ownerDoc.createElementNS(NS_OTA, "ota:PriceInfo");
			
			Element ptcBreakDownsElem=ownerDoc.createElementNS(NS_OTA, "ota:PTC_FareBreakdowns");
			
			Element ptcBreakDownElem=ownerDoc.createElementNS(NS_OTA, "ota:PTC_FareBreakdown");
			//TODO:Get value from retrieve
			ptcBreakDownElem.setAttribute("FlightRefNumberRPHList", "");
			
			Element passengerTypeQuantity=ownerDoc.createElementNS(NS_OTA, "ota:PassengerTypeQuantity");
			//TODO:Get value from retrieve
			passengerTypeQuantity.setAttribute("Age", "");
			passengerTypeQuantity.setAttribute("Code", "");

			ptcBreakDownElem.appendChild(passengerTypeQuantity);
			
			Element fareInfoElem=ownerDoc.createElementNS(NS_OTA, "ota:FareInfo");
			
			Element deptAirportElem=ownerDoc.createElementNS(NS_OTA, "ota:DepartureAirport");
			//TODO:Get value from retrieve
			deptAirportElem.setAttribute("LocationCode", "");
			fareInfoElem.appendChild(deptAirportElem);
			
			Element arrivalAirportElem=ownerDoc.createElementNS(NS_OTA, "ota:ArrivalAirport");
			//TODO:Get value from retrieve
			arrivalAirportElem.setAttribute("LocationCode", "");
			fareInfoElem.appendChild(arrivalAirportElem);
			
			Element dateElem=ownerDoc.createElementNS(NS_OTA, "ota:Date");
			//TODO:Get value from retrieve
			dateElem.setAttribute("Date", "");
			fareInfoElem.appendChild(dateElem);
			
			Element innerFareInfoElem=ownerDoc.createElementNS(NS_OTA, "ota:FareInfo");
			//TODO:Get value from retrieve
			innerFareInfoElem.setAttribute("FareBasisCode", "");
			
			Element tpEtxInfoElem=ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
				
			Element quoteIdElem=ownerDoc.createElementNS(NS_AIR, "ota:QuoteID");
			quoteIdElem.setTextContent("");
			
			tpEtxInfoElem.appendChild(quoteIdElem);
			
			innerFareInfoElem.appendChild(tpEtxInfoElem);
			
			fareInfoElem.appendChild(innerFareInfoElem);
			ptcBreakDownElem.appendChild(fareInfoElem);
			
			ptcBreakDownsElem.appendChild(ptcBreakDownElem);
			priceInfoElem.appendChild(ptcBreakDownsElem);
			amendRQElem.appendChild(priceInfoElem);
			*/
			
			//travelerInfo starts
			Element travelerInfoElem=ownerDoc.createElementNS(NS_OTA, "ota:TravelerInfo");
			//put rem amend here
			
			
			
			
			if((reqBodyJson.getString(JSON_PROP_AMENDTYPE)).equals(JSON_PROP_AMENDTYPE_REM)) {
				Element specialReqDetailsElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialReqDetails");
				Element specialRemarksElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialRemarks");
				JSONArray paxRemarkArrJson=suppBookRefJson.getJSONArray("paxRemarkInfo");
				for(int r=0;r<paxRemarkArrJson.length();r++)
				{
					JSONObject paxRemarkJson=paxRemarkArrJson.getJSONObject(r);
					Element specialRemarkElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialRemark");
					specialRemarkElem.setAttribute("RemarkType", paxRemarkJson.getString(JSON_PROP_PAXREMARKTYPE));
			
			
			
					Element textElem=ownerDoc.createElementNS(NS_OTA, "ota:Text");
					textElem.setTextContent(paxRemarkJson.getString(JSON_PROP_PAXREMARKTEXT));
			
					specialRemarkElem.appendChild(textElem);
					specialRemarksElem.appendChild(specialRemarkElem);
				}
			
			
			
				specialReqDetailsElem.appendChild(specialRemarksElem);
				travelerInfoElem.appendChild(specialReqDetailsElem);
			}
			
			if(reqBodyJson.getString(JSON_PROP_AMENDTYPE).equals(JSON_PROP_AMENDTYPE_PIS) || reqBodyJson.getString(JSON_PROP_AMENDTYPE).equals(CANCEL_TYPE_SSR))
			{
			JSONArray paxDetailsJsonArr=suppBookRefJson.optJSONArray(JSON_PROP_PAXDETAILS);
			if(paxDetailsJsonArr==null) {
				throw new ValidationException("TRLERR030");
			}
			
			for(int z=0;z<paxDetailsJsonArr.length();z++)
			{
				JSONObject paxDetailJson=paxDetailsJsonArr.getJSONObject(z);
				
				
				
				
					
				
						
					Element airTravelerElem=ownerDoc.createElementNS(NS_OTA, "ota:AirTraveler");
					
					if(reqBodyJson.getString(JSON_PROP_AMENDTYPE).equals(CANCEL_TYPE_SSR))
					{
						Element personNameElem=ownerDoc.createElementNS(NS_OTA, "ota:PersonName");
						
						Element namePrefixElem=ownerDoc.createElementNS(NS_OTA, "ota:NamePrefix");
						
						namePrefixElem.setTextContent(paxDetailJson.getString(JSON_PROP_NAMEPREFIX));
						personNameElem.appendChild(namePrefixElem);
						
						Element givenNameElem=ownerDoc.createElementNS(NS_OTA, "ota:GivenName");
						
						givenNameElem.setTextContent(paxDetailJson.getString(JSON_PROP_FIRSTNAME));
						personNameElem.appendChild(givenNameElem);
						
						Element surNameElem=ownerDoc.createElementNS(NS_OTA, "ota:Surname");
						
						surNameElem.setTextContent(paxDetailJson.getString(JSON_PROP_SURNAME));
						personNameElem.appendChild(surNameElem);
						
						Element tpaEtxElem=ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
						
						personNameElem.appendChild(tpaEtxElem);
						
						airTravelerElem.appendChild(personNameElem);
						
						Element paxTypeQuantity=ownerDoc.createElementNS(NS_OTA, "ota:PassengerTypeQuantity");
						paxTypeQuantity.setAttribute("Age", paxDetailJson.getString(JSON_PROP_AGE));
						paxTypeQuantity.setAttribute("Code", paxDetailJson.getString(JSON_PROP_PAXTYPE));
						
						airTravelerElem.appendChild(paxTypeQuantity);
						
						Element travelerRefElem=ownerDoc.createElementNS(NS_OTA, "ota:TravelerRefNumber");
						travelerRefElem.setAttribute("RPH", (travellRefMap.get(paxDetailJson.getString(JSON_PROP_FIRSTNAME).toUpperCase().concat(paxDetailJson.getString(JSON_PROP_SURNAME).toUpperCase()))).toString());
						
						airTravelerElem.appendChild(travelerRefElem);
						travelerInfoElem.appendChild(airTravelerElem);	
						
					}
					
					
					
					
					
					
					
					if(reqBodyJson.getString(JSON_PROP_AMENDTYPE).equals(JSON_PROP_AMENDTYPE_PIS))
					{
						JSONObject paxDataJson=paxDetailJson.optJSONObject(JSON_PROP_PAXDATA_AMEND);
						if(paxDataJson==null) {
							throw new ValidationException("TRLERR033");
						}
						JSONObject telephoneInfoJson=paxDataJson.optJSONObject(JSON_PROP_TELEPHONEINFO);
						if(telephoneInfoJson!=null)
						{
							Element telephoneElem=ownerDoc.createElementNS(NS_OTA, "ota:Telephone");
							telephoneElem.setAttribute("PhoneNumber", telephoneInfoJson.getString(JSON_PROP_PHNO));
							telephoneElem.setAttribute("PhoneUseType", telephoneInfoJson.getString(JSON_PROP_PHUSETYPE));
							
							telephoneElem.setAttribute("RPH", (travellRefMap.get(paxDetailJson.getString(JSON_PROP_FIRSTNAME).toUpperCase().concat(paxDetailJson.getString(JSON_PROP_SURNAME).toUpperCase()))).toString());
							
							airTravelerElem.appendChild(telephoneElem);
						}
						JSONObject emailInfoJson=paxDataJson.optJSONObject(JSON_PROP_EMAILINFO);
						if(emailInfoJson!=null)
						{
							Element emailElem=ownerDoc.createElementNS(NS_OTA, "ota:Email");
							emailElem.setAttribute("EmailType", emailInfoJson.getString(JSON_PROP_EMAILTYPE));
							
							emailElem.setAttribute("RPH", (travellRefMap.get(paxDetailJson.getString(JSON_PROP_FIRSTNAME).toUpperCase().concat(paxDetailJson.getString(JSON_PROP_SURNAME).toUpperCase()))).toString());
							//TODO:Determine value
							emailElem.setAttribute("Remark", "Test");
							emailElem.setTextContent(emailInfoJson.getString(JSON_PROP_EMAILID));
							airTravelerElem.appendChild(emailElem);
						
						}
						
						Element travelerRefNumber = ownerDoc.createElementNS(NS_OTA, "ota:TravelerRefNumber");
						travelerRefNumber.setAttribute("RPH", (travellRefMap.get(paxDetailJson.getString(JSON_PROP_FIRSTNAME).toUpperCase().concat(paxDetailJson.getString(JSON_PROP_SURNAME).toUpperCase()))).toString());
						airTravelerElem.appendChild(travelerRefNumber);
						travelerInfoElem.appendChild(airTravelerElem);	
					}

				
				
				//remove this if loop
				if((reqBodyJson.getString(JSON_PROP_AMENDTYPE)).equals(CANCEL_TYPE_SSR))
				{	
					Element specialReqDetailsElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialReqDetails");
					
					/*if((reqBodyJson.getString(JSON_PROP_AMENDTYPE)).equals(JSON_PROP_AMENDTYPE_REM)) {
						
						Element specialRemarksElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialRemarks");
						JSONArray paxRemarkArrJson=suppBookRefJson.getJSONArray("paxRemarkInfo");
						for(int r=0;r<paxRemarkArrJson.length();r++)
						{
							JSONObject paxRemarkJson=paxRemarkArrJson.getJSONObject(r);
							Element specialRemarkElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialRemark");
							specialRemarkElem.setAttribute("RemarkType", paxRemarkJson.getString(JSON_PROP_PAXREMARKTYPE));
					
					
					
							Element textElem=ownerDoc.createElementNS(NS_OTA, "ota:Text");
							textElem.setTextContent(paxRemarkJson.getString(JSON_PROP_PAXREMARKTEXT));
					
							specialRemarkElem.appendChild(textElem);
							specialRemarksElem.appendChild(specialRemarkElem);
						}
					
					
					
						specialReqDetailsElem.appendChild(specialRemarksElem);
					}*/
				
					
					
						JSONArray ssrJsonArr=paxDetailJson.getJSONArray(JSON_PROP_SPECIALREQUESTINFO);
						Element specialServiceRequestsElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequests");
						for(int p=0;p<ssrJsonArr.length();p++)
						{
							JSONObject ssrJson=ssrJsonArr.getJSONObject(p);
							
							
							
							
							
							Element specialServiceRequestElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequest");
							specialServiceRequestElem.setAttribute("SSRCode", ssrJson.get(JSON_PROP_SSRCODE).toString());
							specialServiceRequestElem.setAttribute("TravelerRefNumberRPHList", travellRefMap.get(paxDetailJson.getString(JSON_PROP_FIRSTNAME).toUpperCase().concat(paxDetailJson.getString(JSON_PROP_SURNAME).toUpperCase())).toString());
							specialServiceRequestElem.setAttribute("FlightRefNumberRPHList",ssrJson.get(JSON_PROP_FLIGHTNBR).toString());
							specialServiceRequestElem.setAttribute("Number", ssrJson.opt(JSON_PROP_NUMBER).toString());
							specialServiceRequestElem.setAttribute("Status",  ssrJson.optString(JSON_PROP_STATUS,"NN"));
							specialServiceRequestElem.setAttribute("ServiceQuantity", ssrJson.opt(JSON_PROP_SVCQTY).toString());
							specialServiceRequestElem.setAttribute("Type", ssrJson.opt(JSON_PROP_TYPE).toString());
							
							Element catCodeElem=ownerDoc.createElementNS(NS_OTA, "ota:CategoryCode");
							catCodeElem.setTextContent(ssrJson.opt(JSON_PROP_CATCODE).toString());
							specialServiceRequestElem.appendChild(catCodeElem);
							
							Element travelerRefElem=ownerDoc.createElementNS(NS_OTA, "ota:TravelerRef");
							travelerRefElem.setTextContent(paxDetailJson.opt(JSON_PROP_PAXTYPE).toString());
							specialServiceRequestElem.appendChild(travelerRefElem);
						
							if(ssrJson.has(JSON_PROP_SVCPRC))
							{
								Element servicePriceElem=ownerDoc.createElementNS(NS_OTA, "ota:ServicePrice");
								servicePriceElem.setAttribute("CurrencyCode", ssrJson.getString(JSON_PROP_CCYCODE));
								servicePriceElem.setAttribute("Total",  ssrJson.getString(JSON_PROP_AMOUNT));
								
								Element basePriceElem=ownerDoc.createElementNS(NS_OTA, "ota:BasePrice");
								basePriceElem.setAttribute("Amount",ssrJson.getJSONObject(JSON_PROP_SVCPRC).getString(JSON_PROP_BASEPRICE));
								servicePriceElem.appendChild(basePriceElem);
								
								Element taxesElem=ownerDoc.createElementNS(NS_OTA, "ota:Taxes");
								taxesElem.setAttribute("Amount", ssrJson.getJSONObject(JSON_PROP_TAXES).optString((JSON_PROP_AMOUNT),""));
								servicePriceElem.appendChild(taxesElem);
								
								specialServiceRequestElem.appendChild(servicePriceElem);
							}
						
							specialServiceRequestsElem.appendChild(specialServiceRequestElem);
						}
						specialReqDetailsElem.appendChild(specialServiceRequestsElem);
					
					travelerInfoElem.appendChild(specialReqDetailsElem);
				}
			
			}
			
			}
			amendRQElem.appendChild(travelerInfoElem);
			
			
			
			Element[] bookRefElemArr=XMLUtils.getElementsAtXPath(retrieveResWrapperElem, "./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID");
			for (Element bookRefElem : bookRefElemArr)
			{
				
				Element bookReferenceId=ownerDoc.createElementNS(NS_OTA, "ota:BookingReferenceID");
				bookReferenceId.setAttribute("Type", bookRefElem.getAttribute("Type"));
				bookReferenceId.setAttribute("ID", bookRefElem.getAttribute("ID"));
				bookReferenceId.setAttribute("ID_Context", reqBodyJson.getString(JSON_PROP_AMENDTYPE));
				
				
				Element companyNameRetreiveElem=XMLUtils.getFirstElementAtXPath(bookRefElem, "./ota:CompanyName");
			
				if(companyNameRetreiveElem!=null)
				{
					Element companyNameElem=ownerDoc.createElementNS(NS_OTA, "ota:CompanyName");
					companyNameElem.setAttribute("CompanyShortName", XMLUtils.getValueAtXPath(companyNameRetreiveElem, "./@CompanyShortName"));	
					companyNameElem.setAttribute("Code", XMLUtils.getValueAtXPath(companyNameRetreiveElem, "./@Code"));	
					bookReferenceId.appendChild(companyNameElem);
				
				}
			
			
				Element tpaEtxRetreiveElem=XMLUtils.getFirstElementAtXPath(bookRefElem, "./ota:TPA_Extensions");
			
				if(tpaEtxRetreiveElem!=null)
				{
					Element tpaEtxElem=ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
					Element extendedRphElem=ownerDoc.createElementNS(NS_OTA, "ota:ExtendedRPH");
					extendedRphElem.setAttribute("ExtendedRPH", XMLUtils.getValueAtXPath(tpaEtxRetreiveElem, "./air:ExtendedRPH"));
					System.out.println(XMLTransformer.toString(extendedRphElem));
					tpaEtxElem.appendChild(extendedRphElem);
					bookReferenceId.appendChild(tpaEtxElem);
				}
			
			
				amendRQElem.appendChild(bookReferenceId);
			}
			  
		}

		return reqElem;
	}

}
