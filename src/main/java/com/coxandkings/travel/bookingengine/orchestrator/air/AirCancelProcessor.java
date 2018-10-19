package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.math.BigDecimal;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AirCancelProcessor implements AirConstants  {

	private static final Logger logger = LogManager.getLogger(AirCancelProcessor.class);

	public static Element getAirTravelerAvailElement(Document ownerDoc, JSONObject traveller) {
		Element travellerElem = ownerDoc.createElementNS(NS_OTA, "ota:AirTravelerAvail");
		Element psgrElem = ownerDoc.createElementNS(NS_OTA, "ota:PassengerTypeQuantity");
		
		psgrElem.setAttribute("Code", traveller.getString(JSON_PROP_PAXTYPE));
		psgrElem.setAttribute("Quantity", String.valueOf(traveller.getInt(JSON_PROP_QTY)));
		travellerElem.appendChild(psgrElem);
		return travellerElem;
	}

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		KafkaBookProducer bookProducer = null;
		JSONObject currencyCodeJson=null;
		//Map<Integer,String> sequenceToOrderIdMap=null;
		Map<Integer,JSONObject> sequenceToOrderIdMap=null;
		Map<String,JSONObject> orderIdToProdMap=null;
		 JSONObject orderDetailsDB =null;
		 Boolean isPre=null;

		try {
			TrackingContext.setTrackingContext(reqJson);
			opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			/*orderDetailsDB = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_DBSERIVCE, new URL(String.format(DBServiceConfig.getDBServiceURL(), reqBodyJson.getString(JSON_PROP_BOOKID))), DBServiceConfig.getHttpHeaders(), "GET", reqJson);
			if(orderDetailsDB.has("ErrorMsg")) {
            	throw new ValidationException(reqHdrJson,"TRLERR037");
            }
			JSONArray productsArr = orderDetailsDB.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRODS);
	            
		    if(productsArr == null) 
		       logger.debug(String.format("Unable to create CompanyPolicy Request as no Bookings found for bookID: %s ", reqBodyJson.getString(JSON_PROP_BOOKID)));
		    orderIdToProdMap=new HashMap<String,JSONObject>();
		    createOrderIdToSuppBookRefMap(reqJson,productsArr,orderIdToProdMap);*/
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			
			orderDetailsDB = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_DBSERIVCE, new URL(String.format(DBServiceConfig.getDBServiceURL(), reqBodyJson.getString(JSON_PROP_BOOKID))), DBServiceConfig.getHttpHeaders(), "GET", reqJson);
			if(orderDetailsDB.has("ErrorMsg")) {
	           	throw new ValidationException(reqHdrJson,"TRLERR037");
	        }
			validateRequestParameters(reqJson);
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			currencyCodeJson=new JSONObject();
		   // sequenceToOrderIdMap=new HashMap<Integer,String>();
		    sequenceToOrderIdMap=new HashMap<Integer,JSONObject>();
			reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson,currencyCodeJson,sequenceToOrderIdMap,orderDetailsDB);
			isPre=reqBodyJson.getString(JSON_PROP_CANCELTYPE).substring(0, 3).equals(CANCEL_TYPE_PRE);
			bookProducer = new KafkaBookProducer();
			if(!isPre) {
				sendPreCancelKafkaMessage(bookProducer,reqJson);
			}
			
				
			//---------- End of Request Processing ---------------	
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
			
		try {	
			// JSONObject orderDetailsDB = HTTPServiceConsumer.produceJSONObjectResponse(TARGET_SYSTEM_DBSERIVCE, String.format(DBServiceConfig.getDBServiceURL(), reqBodyJson.getString(JSON_PROP_BOOKID)), DBServiceConfig.getHttpHeaders());   
			
	          
            Element resElem = null;
            //resElem = HTTPServiceConsumer.consumeXMLService("SI/Cancel", opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
            resElem = HTTPServiceConsumer.consumeXMLService("SI/Cancel", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
            if (resElem == null) {
            	throw new Exception("Null response received from SI");
            }
          /*  JSONArray supplierRefArr=reqBodyJson.getJSONArray(JSON_PROP_SUPPBOOKREFS);
            for(int i=0;i<supplierRefArr.length();i++) {
            	ToDoTaskSubType todoSubType;
                String cancelType=reqBodyJson.getString(JSON_PROP_CANCELTYPE);
                switch(cancelType) {
                case CANCEL_TYPE_ALL:todoSubType=ToDoTaskSubType.ORDER;break;
                case CANCEL_TYPE_SSR:todoSubType=ToDoTaskSubType.PASSENGER;break;
                case CANCEL_TYPE_JOU:todoSubType=ToDoTaskSubType.ORDER;break;
                case CANCEL_TYPE_PAX:todoSubType=ToDoTaskSubType.PASSENGER;break;
                default:todoSubType=null;                
                }
                if(!cancelType.equalsIgnoreCase(CANCEL_TYPE_PRE)) {
                	 OperationsToDoProcessor.callOperationTodo(ToDoTaskName.CANCEL, ToDoTaskPriority.MEDIUM,todoSubType,supplierRefArr.getJSONObject(i).getString("orderID"), reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
                }
               
            }*/
            
           
            JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
           
            JSONObject resBodyJson = new JSONObject();
            
            JSONArray supplierBookReferencesResJsonArr = new JSONArray();
            Element[] resWrapperElems = AirPriceProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_CancelRSWrapper"));
         
            for (Element resWrapperElem : resWrapperElems) {
            	JSONObject supplierBookReferencesResJson= new JSONObject();
            	//String orderID=sequenceToOrderIdMap.get(Integer.parseInt(XMLUtils.getValueAtXPath(resWrapperElem, "./air:Sequence")));
            	JSONObject seqSuppBkRefJson=sequenceToOrderIdMap.get(Integer.parseInt(XMLUtils.getValueAtXPath(resWrapperElem, "./air:Sequence")));
            	String orderID=seqSuppBkRefJson.getString(JSON_PROP_ORDERID);
            	if(XMLUtils.getFirstElementAtXPath(resWrapperElem, "./air:ErrorList")!=null)
            	{	
            		
            		Element errorMessage=XMLUtils.getFirstElementAtXPath(resWrapperElem, "./air:ErrorList/com:Error/com:ErrorCode");
            		String errMsgStr=errorMessage.getTextContent().toString();
            		if(CANCEL_SI_ERR.equalsIgnoreCase(errMsgStr))
            		{	
            			//logger.error("This service is not supported. Kindly contact our operations team for support.");
            			supplierBookReferencesResJson.put(JSON_PROP_ISTODOENABLED, true);
            			logger.error("Recieved Error from SI for Cancel Operation as it is not supported by suppplier");
            			putSINotSupportedErrorMessage(supplierBookReferencesResJson,orderID);
            			supplierBookReferencesResJsonArr.put(supplierBookReferencesResJson);
            			continue;
            			//throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR102");
            			//callOperationTodo(reqJson,"CANCEL");
            			// resBodyJson.put("status", "This service is not supported. Kindly contact our operations team for support.");
            			//return getSIErrorResponse(resJson).toString();
            		}
            		else
            		{   
            			putErrorMessage(supplierBookReferencesResJson,orderID);
            			supplierBookReferencesResJsonArr.put(supplierBookReferencesResJson);
            			logger.error("Recieved Error from SI for Cancel Operation");
            			continue;
        				//throw new ValidationException(reqHdrJson,"TRLERR400");

            		}
            		
            	}
            	Element resBodyElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_CancelRS");
            	
            	if(reqBodyJson.getString(JSON_PROP_CANCELTYPE).substring(0, 3).equals(CANCEL_TYPE_PRE))
            	supplierBookReferencesResJson.put(JSON_PROP_ISTODOENABLED, false);
            	else
            	supplierBookReferencesResJson.put(JSON_PROP_ISTODOENABLED, true);
            		
            	supplierBookReferencesResJson.put(JSON_PROP_SUPPREF, XMLUtils.getFirstElementAtXPath(resWrapperElem, "./air:SupplierID").getTextContent());
            	supplierBookReferencesResJson.put(JSON_PROP_BOOKREFID, seqSuppBkRefJson.getString(JSON_PROP_BOOKREFID));
            	supplierBookReferencesResJson.put("status", reqBodyJson.getString(JSON_PROP_CANCELTYPE)+" "+"type cancel"+" "+"succesfully canceled");
            
            	
            	Element[] cancelRulesElemArr=XMLUtils.getElementsAtXPath(resBodyElem, "./ota:CancelInfoRS/ota:CancelRules/ota:CancelRule");
            	//JSONArray cancelRulesJsonArr=new JSONArray();
            	String status=resBodyElem.getAttribute("Status");
            	if(cancelRulesElemArr.length>0) {
            		for(Element cancelRuleElem :cancelRulesElemArr) {
                		//TODO:should this be BalanceDue or TotalCost
                		if(cancelRuleElem.getAttribute("Type")!=null && (cancelRuleElem.getAttribute("Type").toString()).equalsIgnoreCase("BalanceDue"))
                		{
                			supplierBookReferencesResJson.put(JSON_PROP_SUPPLIERCHARGES, (cancelRuleElem.getAttribute("Amount").toString()));
                			supplierBookReferencesResJson.put(JSON_PROP_CCYCODE, (cancelRuleElem.getAttribute("CurrencyCode").toString()));
                			break;
                			
                		}else {
                			supplierBookReferencesResJson.put(JSON_PROP_SUPPLIERCHARGES, "0");
                			supplierBookReferencesResJson.put(JSON_PROP_CCYCODE, (cancelRuleElem.getAttribute("CurrencyCode").toString()));
                		}
                		
                		
                	}
            	}
            	else if(status.equalsIgnoreCase("Cancelled")) {
            		supplierBookReferencesResJson.put(JSON_PROP_SUPPLIERCHARGES, "0");
            		supplierBookReferencesResJson.put(JSON_PROP_CCYCODE, currencyCodeJson.get(JSON_PROP_SUPPLIERCHARGESCCYCODE));
            	}
            	
            	//supplierBookReferencesResJson.put(JSON_PROP_CANCELRULES, cancelRulesJsonArr);
            	supplierBookReferencesResJsonArr.put(supplierBookReferencesResJson); 
            	
            }
            
            //JSONArray supplierRefArr=reqBodyJson.getJSONArray(JSON_PROP_SUPPBOOKREFS);
            for(int i=0;i<supplierBookReferencesResJsonArr.length();i++) {
            	JSONObject supplierRefJson=supplierBookReferencesResJsonArr.getJSONObject(i);
            	if((supplierRefJson.optBoolean(JSON_PROP_ISTODOENABLED)))
            	{
            		ToDoTaskSubType todoSubType;
                    String cancelType=reqBodyJson.getString(JSON_PROP_CANCELTYPE);
                    switch(cancelType) {
                    case CANCEL_TYPE_ALL:todoSubType=ToDoTaskSubType.ORDER;break;
                    case CANCEL_TYPE_PRE:todoSubType=ToDoTaskSubType.ORDER;break;
                    case CANCEL_TYPE_SSR:todoSubType=ToDoTaskSubType.PASSENGER;break;
                    case CANCEL_TYPE_PRESSR:todoSubType=ToDoTaskSubType.PASSENGER;break;
                    case CANCEL_TYPE_JOU:todoSubType=ToDoTaskSubType.ORDER;break;
                    case CANCEL_TYPE_PREJOU:todoSubType=ToDoTaskSubType.ORDER;break;
                    case CANCEL_TYPE_PAX:todoSubType=ToDoTaskSubType.PASSENGER;break;
                    case CANCEL_TYPE_PREPAX:todoSubType=ToDoTaskSubType.PASSENGER;break;
                    default:todoSubType=null;                
                    }
                   OperationsToDoProcessor.callOperationTodo(ToDoTaskName.CANCEL, ToDoTaskPriority.MEDIUM,todoSubType,supplierBookReferencesResJsonArr.getJSONObject(i).getString("orderID"), reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
            	}
            	supplierRefJson.remove(JSON_PROP_ISTODOENABLED);
            	
               
            }
            
            resBodyJson.put(JSON_PROP_SUPPBOOKREFS, supplierBookReferencesResJsonArr);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);
            
            JSONObject companyPolicyRes = null;
            
            //Getting Booking Details from DBService.
           
            JSONArray productsArr = orderDetailsDB.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRODS);
            
	        if(productsArr == null) 
	        	logger.debug(String.format("Unable to create CompanyPolicy Request as no Bookings found for bookID: %s ", reqBodyJson.getString(JSON_PROP_BOOKID)));
	        else 
	        	//Applying Company Policy
	        	companyPolicyRes = AmClCompanyPolicy.getAmClCompanyPolicy(CommercialsOperation.Cancel, reqJson, resJson, productsArr);
	        
    		appendingCompanyPolicyInRes(supplierBookReferencesResJsonArr, companyPolicyRes);
	        
    			bookProducer = new KafkaBookProducer();
                sendPostCancelKafkaMessage(bookProducer,resJson,reqJson,currencyCodeJson,isPre);
    		
            
            
           /* for(int i=0;i<supplierBookReferencesResJsonArr.length();i++) {
    			
    			JSONObject supplierBookRef = supplierBookReferencesResJsonArr.getJSONObject(i);
    			supplierBookRef.remove("cancelRules");
    		}*/
            
			return resJson.toString();
		}
		catch (Exception x) {
			// TODO: Is this the right thing to do? Or should this be pushed to operations Todo queue?
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}
	


	private static void createOrderIdToSuppBookRefMap(JSONObject reqJson, JSONArray productsArr, Map<String, JSONObject> orderIdToProdMap) {
		
		JSONArray suppBookRefJsonArr=reqJson.getJSONArray(JSON_PROP_SUPPBOOKREFS);
		int suppBookRefArrLength=suppBookRefJsonArr.length();
		for(int i=0;i<productsArr.length();i++) {
			JSONObject productJson=productsArr.getJSONObject(i);
			String airlinePNR=productJson.getJSONObject(JSON_PROP_ORDERDETAILS).optString(JSON_PROP_AIRLINEPNR);
			String orderID=productJson.optString(JSON_PROP_ORDERID);
			if(!Utils.isStringNotNullAndNotEmpty(airlinePNR) && !Utils.isStringNotNullAndNotEmpty(orderID)) {
				continue;
			}
			Boolean breakFlag=false;
			for(int j=0;j<suppBookRefJsonArr.length();j++) {
				JSONObject suppBookRefJson=suppBookRefJsonArr.getJSONObject(j);
				//Should there be a check here
				String suppAirlinePNR=suppBookRefJson.getString(JSON_PROP_BOOKREFID);
				String suppOrderId=suppBookRefJson.getString(JSON_PROP_ORDERID);
				if(airlinePNR.equals(suppAirlinePNR) && orderID.equals(suppOrderId)) {
					orderIdToProdMap.put(suppOrderId, productJson);
					if(suppBookRefArrLength==0) {
						breakFlag=true;
					}
					else {
						suppBookRefArrLength--;
					}
					
					
				}
			}
			if(breakFlag==true) {
				break;
			}
		}
		
	}

	public static void putSINotSupportedErrorMessage(JSONObject suppBookJson, String orderID) {
		suppBookJson.put(JSON_PROP_ORDERID,orderID);
		suppBookJson.put("errorCode", "TRLERR102");
		suppBookJson.put("errorMessage", "Internal Processing Error");
		suppBookJson.put(JSON_PROP_STATUS, "OnRequest");
		
	}

	public static void putErrorMessage(JSONObject suppBookJson, String orderID) {
		suppBookJson.put(JSON_PROP_ORDERID,orderID);
		suppBookJson.put("errorCode", "TRLERR500");
		suppBookJson.put("errorMessage", "Internal Processing Error");
		suppBookJson.put(JSON_PROP_STATUS, "Failure");
		
	}

	 private static void validateRequestParameters(JSONObject reqJson) throws JSONException, ValidationException {
		 AirRequestValidator.validatebookID(reqJson);
		 AirRequestValidator.validateSupplierBookRefs(reqJson);
		// AirRequestValidator.validatePaymentInfo(reqJson);
		
	}

	private static JSONArray getCompanyPoliciesBusinessRuleIntakeJSONArray(JSONObject commResJson) {
	    	return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.air_companypolicies.Root").getJSONArray("businessRuleIntake");
	 }
	 
	private static void appendingCompanyPolicyInRes(JSONArray supplierBookRefResJsonArr, JSONObject cancelPolicyRes) {
		
		if(cancelPolicyRes==null)	
		{
			for(int i=0;i<supplierBookRefResJsonArr.length();i++) {
				
				JSONObject supplierBookRef = supplierBookRefResJsonArr.getJSONObject(i);
				//JSONObject companyPolicyDtls = briArr.getJSONObject(i).optJSONObject("companyPolicyDetails");
				//BigDecimal companyChrg = companyPolicyDtls!=null ? companyPolicyDtls.getBigDecimal("policyCharges") : BigDecimal.ZERO;
				supplierBookRef.put(JSON_PROP_COMPANYCHARGES, supplierBookRef.getBigDecimal(JSON_PROP_SUPPLIERCHARGES).abs());
				
			}
			return;
		}
			
		
    	if (BRMS_STATUS_TYPE_FAILURE.equals(cancelPolicyRes.getString(JSON_PROP_TYPE))) {
    		logger.error(String.format("A failure response was received from Company Policy calculation engine: %s", cancelPolicyRes.toString()));
    		return;
    	}
    	
		JSONArray briArr = getCompanyPoliciesBusinessRuleIntakeJSONArray(cancelPolicyRes);
		
		for(int i=0;i<supplierBookRefResJsonArr.length();i++) {
			
			JSONObject supplierBookRef = supplierBookRefResJsonArr.getJSONObject(i);
			 if(AirCancelProcessor.checkIsOperationFailed(supplierBookRef)) {
	            	continue;
	            	
	            }
			JSONObject companyPolicyDtls = briArr.getJSONObject(i).optJSONObject("companyPolicyDetails");
			BigDecimal companyChrg = companyPolicyDtls!=null ? companyPolicyDtls.getBigDecimal("policyCharges") : BigDecimal.ZERO;
			supplierBookRef.put(JSON_PROP_COMPANYCHARGES, companyChrg.add(supplierBookRef.getBigDecimal(JSON_PROP_SUPPLIERCHARGES).abs()));
		}
	}

	private static void sendPreCancelKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson) throws Exception {
		JSONObject kafkaMsgJson = reqJson;
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		getEntityIdJsonArray(reqBodyJson);	
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, PRODUCT_AIR);
		
		//change enums here
		CancelDbEnum cancelEnum=CancelDbEnum.valueOf(reqBodyJson.getString(JSON_PROP_CANCELTYPE));
		//TODO:Determine value
		String dbType=cancelEnum.getcancelDbEnum();
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_TYPE, dbType);
		if(reqBodyJson.getString(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_JOU) || reqBodyJson.getString(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_ALL)) {
			kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_ENTITYNAME, "order");
		}
		else
		{
			kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_ENTITYNAME, "pax");
		}
	
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_REQTYPE, "cancel");
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put("operation", "cancel");
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
		bookProducer.runProducer(1, kafkaMsgJson);
		logger.trace(String.format("Air Cancel Request Kafka Message: %s", kafkaMsgJson.toString()));
	}

	private static void sendPostCancelKafkaMessage(KafkaBookProducer bookProducer, JSONObject resJson,JSONObject reqJson, JSONObject currencyCodeJson, Boolean isPre) throws Exception
	{
		JSONObject kafkaMsgJson = new JSONObject();
		kafkaMsgJson.put(JSON_PROP_RESHEADER,new JSONObject( resJson.getJSONObject(JSON_PROP_RESHEADER).toString()));
		kafkaMsgJson.put(JSON_PROP_RESBODY,new JSONObject(resJson.getJSONObject(JSON_PROP_RESBODY).toString()));
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		
		Map<String,JSONObject> cancelRqSuppJsonArr=getEntityIdJsonArray(reqBodyJson);
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PROD, PRODUCT_AIR);
		String dbType="";
		
		
		if(!isPre) {
			CancelDbEnum cancelEnum=CancelDbEnum.valueOf(reqBodyJson.getString(JSON_PROP_CANCELTYPE));
			 dbType=cancelEnum.getcancelDbEnum();
		}
	
		
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_TYPE, dbType);
		
		if(reqBodyJson.getString(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_JOU) || reqBodyJson.getString(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_ALL) ) 
			kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_ENTITYNAME, "order");
		else
			kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_ENTITYNAME, "pax");
		
		
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_REQTYPE, "cancel");
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("operation", "cancel");
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
		JSONArray suppBookRefsJsonArr=resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_SUPPBOOKREFS);
		JSONArray kafkaSuppBookRefsJsonArr=kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_SUPPBOOKREFS);
		
		for(int i=0;i<suppBookRefsJsonArr.length();i++) {
			
			JSONObject suppBookRefJson=suppBookRefsJsonArr.getJSONObject(i);
			if(checkIsOperationFailed(suppBookRefJson)) {
				continue;
			}
			JSONObject cancelRqSuppJson=cancelRqSuppJsonArr.get(suppBookRefJson.getString(JSON_PROP_SUPPREF)+"|"+suppBookRefJson.getString(JSON_PROP_BOOKREFID));
			
			JSONObject kafkaSuppBookRefJson=kafkaSuppBookRefsJsonArr.getJSONObject(i);
			kafkaSuppBookRefJson.put("orderID", cancelRqSuppJson.getString("orderID"));
			kafkaSuppBookRefJson.put("entityIDs", cancelRqSuppJson.getJSONArray("entityIDs"));
				
			/*JSONArray cancelRulesJsonArr=suppBookRefJson.optJSONArray(JSON_PROP_CANCELRULES);
			
			if(cancelRulesJsonArr!=null)
			{
				String cancelCharge="0";
				for(int j=0;j<cancelRulesJsonArr.length();j++) {
					JSONObject cancelRulesJson=cancelRulesJsonArr.getJSONObject(j);
					if(cancelRulesJson.has("TotalCost"))
						cancelCharge = cancelRulesJson.getString("TotalCost");
					
				}
				kafkaSuppBookRefJson.put(JSON_PROP_SUPPLIERCHARGES, cancelCharge);
			}*/
			
			kafkaSuppBookRefJson.put(JSON_PROP_SUPPLIERCHARGES, suppBookRefJson.getBigDecimal(JSON_PROP_SUPPLIERCHARGES));
			 suppBookRefJson.remove(JSON_PROP_SUPPLIERCHARGES);
			
			//kafkaSuppBookRefJson.put(JSON_PROP_SUPPLIERCHARGESCCYCODE, suppBookRefJson.optString((JSON_PROP_CCYCODE),currencyCodeJson.optString(JSON_PROP_COMPANYCHARGESCCYCODE));
			 kafkaSuppBookRefJson.put(JSON_PROP_SUPPLIERCHARGESCCYCODE, currencyCodeJson.optString(JSON_PROP_COMPANYCHARGESCCYCODE));
			 suppBookRefJson.remove(JSON_PROP_CCYCODE);
			kafkaSuppBookRefJson.put(JSON_PROP_COMPANYCHARGESCCYCODE, resJson.getJSONObject(JSON_PROP_RESHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).optString(JSON_PROP_CLIENTCCY));
			suppBookRefJson.put(JSON_PROP_COMPANYCHARGESCCYCODE, resJson.getJSONObject(JSON_PROP_RESHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).optString(JSON_PROP_CLIENTCCY));
			BigDecimal roe=RedisRoEData.getRateOfExchange(kafkaSuppBookRefJson.getString(JSON_PROP_SUPPLIERCHARGESCCYCODE), kafkaSuppBookRefJson.getString(JSON_PROP_COMPANYCHARGESCCYCODE), resJson.getJSONObject(JSON_PROP_RESHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).optString(JSON_PROP_CLIENTMARKET));
			suppBookRefJson.put(JSON_PROP_COMPANYCHARGES, suppBookRefJson.getBigDecimal(JSON_PROP_COMPANYCHARGES).multiply(roe));
			kafkaSuppBookRefJson.put(JSON_PROP_COMPANYCHARGES, suppBookRefJson.getBigDecimal(JSON_PROP_COMPANYCHARGES));
			
		}
		if(!isPre) {
			bookProducer.runProducer(1, kafkaMsgJson);
			logger.trace(String.format("Air Cancel Response Kafka Message: %s", kafkaMsgJson.toString()));
		}
		
	}
	
	public static boolean checkIsOperationFailed(JSONObject suppBookRef) {
		if((suppBookRef.optString(JSON_PROP_STATUS)).equalsIgnoreCase("Failure") || (suppBookRef.optString(JSON_PROP_STATUS)).equalsIgnoreCase("OnRequest"))
		{
			return true;
		}
		return false;
		
	}
	
	public static Map<String,JSONObject> getEntityIdJsonArray(JSONObject reqBodyJson) {
		
		JSONArray suppRefJsonArr=reqBodyJson.getJSONArray(JSON_PROP_SUPPBOOKREFS);
		JSONArray entityIdJsonArr=new JSONArray();
		Set<String> entityIdSet=new HashSet<String>();
		Map<String,JSONObject> cancelRqSuppRefMap=new HashMap<String,JSONObject>();
		for(int j=0;j<suppRefJsonArr.length();j++) {
			JSONObject suppRefJson=suppRefJsonArr.getJSONObject(j);
			
			cancelRqSuppRefMap.put(suppRefJson.getString(JSON_PROP_SUPPREF)+"|"+suppRefJson.getString(JSON_PROP_BOOKREFID), suppRefJson);
		JSONArray paxDetails=suppRefJson.optJSONArray(JSON_PROP_PAXDETAILS);
		
		if(paxDetails!=null && paxDetails.length()>0)
		{
			
			for(int i=0;i<paxDetails.length();i++) {
				JSONObject paxDetail=paxDetails.getJSONObject(i);
				if(!entityIdSet.contains(paxDetail.getString("paxID")))
				{
				JSONObject entityIdJson=new JSONObject();
				entityIdJson.put("entityID", paxDetail.getString("paxID"));
				entityIdJsonArr.put(entityIdJson);
				}
			}
			
		}
		else {
			
				JSONObject entityIdJson=new JSONObject();
				entityIdJson.put("entityID", suppRefJson.getString("orderID"));
				entityIdJsonArr.put(entityIdJson);
			
			
		}
		suppRefJson.put("entityIDs", entityIdJsonArr);
		}
		return cancelRqSuppRefMap;
		
	}
	

	public static Element getRetrieveWrapperByBookRefId(String bookRefId, Element[] retreieveResWrapperElems) {
		
		Element reqWrapperElem=null;
	    for (Element wrapperElem : retreieveResWrapperElems) {
	    	Element reqBookRsElem = XMLUtils.getFirstElementAtXPath(wrapperElem, String.format("./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID[@ID='%s']",bookRefId));
	    	if(reqBookRsElem!=null)
	    	{
	    		reqWrapperElem=(Element) reqBookRsElem.getParentNode().getParentNode().getParentNode();
	    		break;
	    	}
	    }
		return reqWrapperElem;
	}


	
	private static Element getSsrElemForTraveller(Element[] retrieveSsrElems, String ssrCode) {
		Element ssrElem=null;
		
		for (Element retrieveSsrElem : retrieveSsrElems)
		{	
			
			if(retrieveSsrElem.getAttribute("SSRCode").equals(ssrCode))
			{
				ssrElem=retrieveSsrElem;
				break;
			}
			
		}
		
		return ssrElem;
	}

	private static Map<String, Element> getFlightSegMap(Element retrieveResElem) {
		Map<String,Element> flightSegMap=new HashMap<String,Element>();
		Element[] odosElems = XMLUtils.getElementsAtXPath(retrieveResElem,"./ota:OTA_AirBookRS/ota:AirReservation/ota:AirItinerary/ota:OriginDestinationOptions/ota:OriginDestinationOption");
		for (Element odoElem : odosElems) {
			Element[] flightSegElems=XMLUtils.getElementsAtXPath(odoElem,"./ota:FlightSegment");
			for (Element flightSegElem : flightSegElems) {
				flightSegMap.put(XMLUtils.getFirstElementAtXPath(flightSegElem, "./ota:OperatingAirline").getAttribute("FlightNumber").toString().replaceAll("\\s+",""), flightSegElem);
			}
		}
		return flightSegMap;
	}

	private static Map<String, String> getTravellRefMap(Element retrieveResElem) {
		Map<String,String> traverllerRefMap=new HashMap<String,String>();
		Element[] travellerInfoElems = XMLUtils.getElementsAtXPath(retrieveResElem,"./ota:OTA_AirBookRS/ota:AirReservation/ota:TravelerInfo/ota:AirTraveler");
		
		for (Element travellerInfoElem : travellerInfoElems) {
			 
			traverllerRefMap.put((XMLUtils.getFirstElementAtXPath(travellerInfoElem, "./ota:PersonName/ota:GivenName").getTextContent().toString().toUpperCase()).concat(XMLUtils.getFirstElementAtXPath(travellerInfoElem, "./ota:PersonName/ota:Surname").getTextContent().toString().toUpperCase()), XMLUtils.getValueAtXPath(travellerInfoElem, "./ota:TravelerRefNumber/@RPH"));
		}
		
		return traverllerRefMap;
	}


	protected static void createTPA_Extensions(JSONObject reqBodyJson, Element travelerInfoElem) {
		Element tpaExtsElem = XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:TPA_Extensions");
		Element tripTypeElem = XMLUtils.getFirstElementAtXPath(tpaExtsElem, "./air:TripType");
		tripTypeElem.setTextContent(reqBodyJson.getString(JSON_PROP_TRIPTYPE));
		Element tripIndElem = XMLUtils.getFirstElementAtXPath(tpaExtsElem, "./air:TripIndicator");
		tripIndElem.setTextContent(reqBodyJson.getString(JSON_PROP_TRIPIND));
	}

	protected static void createOriginDestinationOptions(Document ownerDoc, JSONObject pricedItinJson, Element odosElem) {
		JSONObject airItinJson = pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY);
		JSONArray odoJsonArr = airItinJson.getJSONArray("originDestinationOptions");
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

				Element opAirlineElem = ownerDoc.createElementNS(NS_OTA,"ota:OperatingAirline");
				JSONObject opAirlineJson = flSegJson.getJSONObject("operatingAirline");
				flSegElem.setAttribute("FlightNumber", opAirlineJson.getString(JSON_PROP_FLIGHTNBR));
				flSegElem.setAttribute("DepartureDateTime", flSegJson.getString("departureDate"));
				flSegElem.setAttribute("ArrivalDateTime", flSegJson.getString("arrivalDate"));
				opAirlineElem.setAttribute("Code", opAirlineJson.getString("airlineCode"));
				opAirlineElem.setAttribute("FlightNumber", opAirlineJson.getString(JSON_PROP_FLIGHTNBR));
				String companyShortName = opAirlineJson.optString("companyShortName", "");
				if (companyShortName.isEmpty() == false) {
					opAirlineElem.setAttribute("CompanyShortName", companyShortName);
				}
				flSegElem.appendChild(opAirlineElem);

				Element tpaExtsElem = ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
				Element extRPHElem = ownerDoc.createElementNS(NS_AIR, "air:ExtendedRPH");
				extRPHElem.setTextContent(flSegJson.getString("extendedRPH"));
				tpaExtsElem.appendChild(extRPHElem);

				Element quoteElem = ownerDoc.createElementNS(NS_AIR, "air:QuoteID");
				quoteElem.setTextContent(flSegJson.getString("quoteID"));
				tpaExtsElem.appendChild(quoteElem);

				flSegElem.appendChild(tpaExtsElem);

				Element mkAirlineElem = ownerDoc.createElementNS(NS_OTA, "ota:MarketingAirline");
				JSONObject mkAirlineJson = flSegJson.getJSONObject("marketingAirline");
				mkAirlineElem.setAttribute("Code", mkAirlineJson.getString("airlineCode"));
				String mkAirlineFlNbr = mkAirlineJson.optString(JSON_PROP_FLIGHTNBR, "");
				if (mkAirlineFlNbr.isEmpty() == false) {
					mkAirlineElem.setAttribute("FlightNumber", mkAirlineFlNbr);
				}
				String mkAirlineShortName = mkAirlineJson.optString("companyShortName", "");
				if (mkAirlineShortName.isEmpty() == false) {
					mkAirlineElem.setAttribute("CompanyShortName", mkAirlineShortName);
				}
				flSegElem.appendChild(mkAirlineElem);

				Element bookClsAvailsElem = ownerDoc.createElementNS(NS_OTA, "BookingClassAvails");
				bookClsAvailsElem.setAttribute("CabinType", flSegJson.getString("CabinType"));
				Element bookClsAvailElem = ownerDoc.createElementNS(NS_OTA, "ota:BookingClassAvail");
				bookClsAvailElem.setAttribute("ResBookDesigCode", flSegJson.getString("ResBookDesigCode"));
				bookClsAvailElem.setAttribute("RPH", flSegJson.getString("RPH"));
				bookClsAvailsElem.appendChild(bookClsAvailElem);
				flSegElem.appendChild(bookClsAvailsElem);

				odoElem.appendChild(flSegElem);
			}

			odosElem.appendChild(odoElem);
		}
	}

	//public static Element getRetrieveResponse(JSONObject reqJson) {
	public static Element getRetrieveResponse(JSONObject reqHdrJson, JSONObject reqBodyJson,Map<String,String> orderGDSmap) {
		  //JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null) ? reqJson.optJSONObject(JSON_PROP_REQHEADER) : new JSONObject();
	      //  JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null) ? reqJson.optJSONObject(JSON_PROP_REQBODY) : new JSONObject();
	        
	        try {
	        	//OperationConfig opConfig = AirConfig.getOperationConfig("retrievepnr");
	        	ServiceConfig opConfig = AirConfig.getOperationConfig("retrievepnr");
				Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
				Document ownerDoc = reqElem.getOwnerDocument();
				
				Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
				Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_ReadRQWrapper");
				reqBodyElem.removeChild(wrapperElem);
				
				UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
				
				AirSearchProcessor.createHeader(reqHdrJson, reqElem);
				
				/*int seqNo = 1;
				Map<String,Integer> supplierSequenceMap=new HashMap<String,Integer>();*/
			/*	JSONArray retrieveReqsJSONArr = reqBodyJson.getJSONArray(JSON_PROP_CANCELREQUESTS);
				for(int i=0;i<retrieveReqsJSONArr.length();i++) {
					JSONObject retrieveJson=retrieveReqsJSONArr.getJSONObject(i);*/
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
					/*	
						if(supplierSequenceMap.containsKey(suppID)) {
							int mapSeqVal=supplierSequenceMap.get(suppID)+1;
							supplierSequenceMap.replace(suppID, mapSeqVal);
						}
						else {
							supplierSequenceMap.put(suppID, seqNo);
						}*/
						
						Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:Sequence");
						sequenceElem.setTextContent(String.valueOf(j));
						
						
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
	            //resElem = HTTPServiceConsumer.consumeXMLService("SI/Cancel", opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
				resElem = HTTPServiceConsumer.consumeXMLService("SI/Cancel", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
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
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson, JSONObject currencyCodeJson, Map<Integer, JSONObject> sequenceToOrderIdMap,JSONObject orderDetailsDB) throws Exception {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_CancelRQWrapper");
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
		
		
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		
		currencyCodeJson.put(JSON_PROP_SUPPLIERCHARGESCCYCODE, XMLUtils.getValueAtXPath(retrieveResElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper/ota:OTA_AirBookRS/ota:AirReservation/ota:PriceInfo/ota:PTC_FareBreakdowns/ota:PTC_FareBreakdown/ota:PassengerFare/ota:BaseFare/@CurrencyCode"));
	
		//Element[] retreieveResWrapperElems=AirPriceProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(retrieveResElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper"));
		Element[] retreieveResWrapperElems=XMLUtils.getElementsAtXPath(retrieveResElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper");
		
		/*Map<String,Integer> supplierSequenceMap=new HashMap<String,Integer>();

		int seqNo = 1;*/
		/*JSONArray cancelReqsJSONArr = reqBodyJson.getJSONArray(JSON_PROP_CANCELREQS);
		for (int y=0; y < cancelReqsJSONArr.length(); y++) {
			JSONObject cancelReqJson = cancelReqsJSONArr.getJSONObject(y);*/
			
			JSONArray suppBookRefsJsonArr = reqBodyJson.getJSONArray(JSON_PROP_SUPPBOOKREFS);
			for (int z=0; z < suppBookRefsJsonArr.length(); z++) {
			
				
				
				
				JSONObject suppBookRefJson = suppBookRefsJsonArr.getJSONObject(z);
			
				
				
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
				/*if(supplierSequenceMap.containsKey(suppID)) {
					int mapSeqVal=supplierSequenceMap.get(suppID)+1;
					supplierSequenceMap.replace(suppID, mapSeqVal);
				}
				else {
					supplierSequenceMap.put(suppID, seqNo);
				}*/
				Element supplierIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:SupplierID");
				supplierIDElem.setTextContent(suppID);
				
				Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:Sequence");
				sequenceElem.setTextContent(String.valueOf(z));
				sequenceToOrderIdMap.put(z, suppBookRefJson);
			
				
				Element cancelRQElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_CancelRQ");
				
				if(!(suppBookRefJson.has(JSON_PROP_BOOKREFID)) && (suppBookRefJson.getString(JSON_PROP_BOOKREFID).equals(""))) {
					throw new ValidationException(reqHdrJson,"TRLERR039");
				}
				Element retrieveResWrapperElem=getRetrieveWrapperByBookRefId(suppBookRefJson.getString(JSON_PROP_BOOKREFID),retreieveResWrapperElems);
				if(XMLUtils.getValueAtXPath(retrieveResWrapperElem, "./ota:OTA_AirBookRS/@Version").isEmpty()) {
					cancelRQElem.setAttribute("Version",Integer.toString(1));
				}
				else {
					cancelRQElem.setAttribute("Version",XMLUtils.getValueAtXPath(retrieveResWrapperElem, "./ota:OTA_AirBookRS/@Version"));
				}
				
				
				Element[] bookRefElemArr=XMLUtils.getElementsAtXPath(retrieveResWrapperElem, "./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID");
			
				for (Element bookRefElem : bookRefElemArr)
				{
					Element uniqueIdElem = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
						
					
					uniqueIdElem.setAttribute("Type", bookRefElem.getAttribute("Type"));
					uniqueIdElem.setAttribute("ID", bookRefElem.getAttribute("ID"));
					//getSICancelType(reqBodyJson.getString(JSON_PROP_CANCELTYPE));
					uniqueIdElem.setAttribute("ID_Context", reqBodyJson.getString(JSON_PROP_CANCELTYPE));
				
					
					Element companyNameRetreiveElem=XMLUtils.getFirstElementAtXPath(bookRefElem, "./ota:CompanyName");
					
					if(companyNameRetreiveElem!=null)
					{
						Element companyNameElem=ownerDoc.createElementNS(NS_OTA, "ota:CompanyName");
						companyNameElem.setAttribute("CompanyShortName", XMLUtils.getValueAtXPath(companyNameRetreiveElem, "./@CompanyShortName"));	
						companyNameElem.setAttribute("Code", XMLUtils.getValueAtXPath(companyNameRetreiveElem, "./@Code"));	
						uniqueIdElem.appendChild(companyNameElem);
						
					}
					
					
					Element tpaEtxRetreiveElem=XMLUtils.getFirstElementAtXPath(bookRefElem, "./ota:TPA_Extensions");
					
					if(tpaEtxRetreiveElem!=null)
					{
						Element tpaEtxElem=ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
						Element extendedRphElem=ownerDoc.createElementNS(NS_OTA, "ota:ExtendedRPH");
						extendedRphElem.setAttribute("ExtendedRPH", XMLUtils.getValueAtXPath(tpaEtxRetreiveElem, "./ota:ExtendedRPH"));
						//companyNameElem.setAttribute("CompanyShortName", XMLUtils.getValueAtXPath(companyNameRetreiveElem, "./@CompanyShortName"));
						tpaEtxElem.appendChild(extendedRphElem);
						uniqueIdElem.appendChild(tpaEtxElem);
					}
					
					
					
					
					cancelRQElem.appendChild(uniqueIdElem);
				}
				
			
				if(reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_JOU) || reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_PREJOU) || 
						reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_SSR) ||reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_PRESSR) || 
						reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_JOU) || reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_PREPAX) ) {
					
					
					Map<String,Element> flightSegElemMap=getFlightSegMap(retrieveResWrapperElem);
					Map<String,String> travellRefMap=getTravellRefMap(retrieveResWrapperElem);
				
					
					//JSONArray paxDetailsJsonArr=suppBookRefJson.getJSONArray(JSON_PROP_PAXDETAILS);
					if(reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_PAX) || reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_PREPAX))
					{
						
						JSONArray paxDetailsJsonArr=suppBookRefJson.optJSONArray(JSON_PROP_PAXDETAILS);	
						if(paxDetailsJsonArr==null) {
							throw new ValidationException(reqHdrJson,"TRLERR030");
						}
						
						Element originDestSegElem=ownerDoc.createElementNS(NS_OTA, "ota:OriginAndDestinationSegment");
						
					/*	Element originLocationElem=ownerDoc.createElementNS(NS_OTA, "ota:OriginLocation");
						
						originLocationElem.setAttribute("LocationCode", "");
						originDestSegElem.appendChild(originLocationElem);
						
						Element destLocationElem=ownerDoc.createElementNS(NS_OTA, "ota:DestinationLocation");
					
						destLocationElem.setAttribute("LocationCode", "");
						originDestSegElem.appendChild(destLocationElem);*/
					
					
						for(int n=0;n<paxDetailsJsonArr.length();n++)
						{
							JSONObject paxDetJson=	paxDetailsJsonArr.getJSONObject(n);
							
							Element travellerElem=ownerDoc.createElementNS(NS_OTA, "ota:Traveler");
							
							Element givenNameElem=ownerDoc.createElementNS(NS_OTA, "ota:GivenName");						
							givenNameElem.setTextContent(paxDetJson.getString(JSON_PROP_FIRSTNAME));
							travellerElem.appendChild(givenNameElem);
							
							Element surNameElem=ownerDoc.createElementNS(NS_OTA, "ota:Surname");
							surNameElem.setTextContent(paxDetJson.getString(JSON_PROP_SURNAME));
							travellerElem.appendChild(surNameElem);
							
							Element tpaExtentionsElem=ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
							
							Element travellerRefNoElem=ownerDoc.createElementNS(NS_AIR, "air:TravelerRefNumber");
							
							travellerRefNoElem.setAttribute("RPH", travellRefMap.get(paxDetJson.getString(JSON_PROP_FIRSTNAME).toUpperCase().concat(paxDetJson.getString(JSON_PROP_SURNAME).toUpperCase())).toString());
							tpaExtentionsElem.appendChild(travellerRefNoElem);
							travellerElem.appendChild(tpaExtentionsElem);
							
							originDestSegElem.appendChild(travellerElem);
						}
						cancelRQElem.appendChild(originDestSegElem);
					}
					
					if(reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_JOU) || reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_SSR) 
							|| reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_PREJOU) || reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_PRESSR) 
							)
					{
						Element tpaEtxElem=ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
						
						//Airitenary for tpaExtension
				
						Element airIternaryElem=ownerDoc.createElementNS(NS_AIR, "air:AirItinerary");
						
						Element odosElem=ownerDoc.createElementNS(NS_AIR, "air:OriginDestinationOptions");
						
						JSONArray odosJsonArr=suppBookRefJson.optJSONArray(JSON_PROP_ORIGDESTOPTS);
						if(odosJsonArr==null) {
							throw new ValidationException("TRLERR025");
						}
						
						for(int j=0;j<odosJsonArr.length();j++)
						{
							Element odoElem=ownerDoc.createElementNS(NS_AIR, "air:OriginDestinationOption");
							JSONObject odoJson=odosJsonArr.getJSONObject(j);
							JSONArray flightSegJsonArr=odoJson.getJSONArray(JSON_PROP_FLIGHTSEG);
							for(int k=0;k<flightSegJsonArr.length();k++)
							{
								JSONObject flightSegJson=flightSegJsonArr.getJSONObject(k);
								Element retrieveFlightSeg=flightSegElemMap.get(flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).get(JSON_PROP_FLIGHTNBR).toString().replaceAll("\\s+",""));
								
								Element flightSegElem=ownerDoc.createElementNS(NS_AIR, "air:FlightSegment");
								flightSegElem.setAttribute("RPH",Integer.toString(j));
								
								
								if(retrieveFlightSeg.getAttribute("ConnectionType").isEmpty())
								{
									flightSegElem.setAttribute("ConnectionType","");
								}
								else {
									flightSegElem.setAttribute("ConnectionType",retrieveFlightSeg.getAttribute("ConnectionType").toString());
								}
									
								flightSegElem.setAttribute("FlightNumber", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).get(JSON_PROP_FLIGHTNBR).toString().replaceAll("\\s+",""));
								flightSegElem.setAttribute("DepartureDateTime",  flightSegJson.get(JSON_PROP_DEPARTDATE).toString().replaceAll("\\s+",""));
								flightSegElem.setAttribute("ArrivalDateTime", flightSegJson.get(JSON_PROP_ARRIVEDATE).toString().replaceAll("\\s+",""));
								
								flightSegElem.setAttribute("Status", retrieveFlightSeg.getAttribute("Status"));
								
								Element departureAirportElem=ownerDoc.createElementNS(NS_AIR, "air:DepartureAirport");
								departureAirportElem.setAttribute("LocationCode", flightSegJson.get(JSON_PROP_ORIGLOC).toString().replaceAll("\\s+",""));
								departureAirportElem.setAttribute("Terminal", flightSegJson.optString(((JSON_PROP_DEPARTTERMINAL).toString().replaceAll("\\s+","")),""));
								flightSegElem.appendChild(departureAirportElem);
								
								Element arrivalAirportElem=ownerDoc.createElementNS(NS_AIR, "air:ArrivalAirport");
								arrivalAirportElem.setAttribute("LocationCode", flightSegJson.get(JSON_PROP_DESTLOC).toString().replaceAll("\\s+",""));
								arrivalAirportElem.setAttribute("Terminal",  flightSegJson.optString(((JSON_PROP_ARRIVETERMINAL).toString().replaceAll("\\s+","")),""));
								flightSegElem.appendChild(arrivalAirportElem);
								
								
								Element operatingAirlineElem=ownerDoc.createElementNS(NS_AIR, "air:OperatingAirline");
								operatingAirlineElem.setAttribute("Code", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).get(JSON_PROP_AIRLINECODE).toString().replaceAll("\\s+",""));
								operatingAirlineElem.setAttribute("FlightNumber", flightSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).get(JSON_PROP_FLIGHTNBR).toString().replaceAll("\\s+",""));
								flightSegElem.appendChild(operatingAirlineElem);
								
								Element tpaExtnElem=ownerDoc.createElementNS(NS_AIR, "air:TPA_Extensions");
								
								Element extendedRphElem=ownerDoc.createElementNS(NS_AIR, "air:ExtendedRPH");
								extendedRphElem.setTextContent(flightSegJson.get(JSON_PROP_EXTENDEDRPH).toString());
								tpaExtnElem.appendChild(extendedRphElem);
								
								flightSegElem.appendChild(tpaExtnElem);
								
								odoElem.appendChild(flightSegElem);
							}
						
							odosElem.appendChild(odoElem);
						}
						
						airIternaryElem.appendChild(odosElem);
						
						tpaEtxElem.appendChild(airIternaryElem);
						if(reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_SSR) || reqBodyJson.get(JSON_PROP_CANCELTYPE).equals(CANCEL_TYPE_PRESSR)) 
						{
							Element ssrElem=ownerDoc.createElementNS(NS_AIR, "air:SSR");
							
							Element specSSRElems=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequests");
							
							
							
							JSONArray paxDetailsJsonArr=suppBookRefJson.optJSONArray(JSON_PROP_PAXDETAILS);	
							if(paxDetailsJsonArr==null) {
								throw new ValidationException(reqHdrJson,"TRLERR030");
							}
							Element specialServiceRequestsElem=XMLUtils.getFirstElementAtXPath(retrieveResElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper/ota:OTA_AirBookRS/ota:AirReservation/ota:TravelerInfo/ota:SpecialReqDetails/ota:SpecialServiceRequests");
							for(int k=0;k<paxDetailsJsonArr.length();k++)
							{
								JSONObject paxDetailJson=paxDetailsJsonArr.getJSONObject(k);
								String travellerRPHList=travellRefMap.get(paxDetailJson.get(JSON_PROP_FIRSTNAME).toString().toUpperCase().concat(paxDetailJson.get(JSON_PROP_SURNAME).toString().toUpperCase()));
								
								JSONArray ssrJsonArr=paxDetailJson.optJSONArray(JSON_PROP_SPECIALREQUESTINFO);
								if(ssrJsonArr==null) {
									throw new ValidationException(reqHdrJson,"TRLERR030");
								}
								
								Element[] retrieveSsrElems=XMLUtils.getElementsAtXPath(specialServiceRequestsElem,String.format("./ota:SpecialServiceRequest[@TravelerRefNumberRPHList='%s']",travellerRPHList));
								if(retrieveSsrElems==null || retrieveSsrElems[0]==null) {
									throw new ValidationException(reqHdrJson,"TRLERR022");
								}
								
								for(int l=0;l<ssrJsonArr.length();l++)
								{
									JSONObject ssrJson=ssrJsonArr.getJSONObject(l);
									Element retrieveSsrElem=getSsrElemForTraveller(retrieveSsrElems, ssrJson.getString(JSON_PROP_SSRCODE));
									Element flightSegElem=flightSegElemMap.get(ssrJson.get(JSON_PROP_FLIGHTNBR).toString().replaceAll("\\s+",""));
									
									Element specSSRElem=ownerDoc.createElementNS(NS_OTA, "ota:SpecialServiceRequest");
									specSSRElem.setAttribute("FlightRefNumberRPHList",   ssrJson.get(JSON_PROP_FLIGHTNBR).toString().replaceAll("\\s+",""));
									specSSRElem.setAttribute("TravelerRefNumberRPHList", travellerRPHList);
									specSSRElem.setAttribute("SSRCode", ssrJson.get(JSON_PROP_SSRCODE).toString());
									specSSRElem.setAttribute("Number",retrieveSsrElem.getAttribute("Number").toString() );
									specSSRElem.setAttribute("ServiceQuantity", retrieveSsrElem.getAttribute("ServiceQuantity").toString());
									specSSRElem.setAttribute("Status", retrieveSsrElem.getAttribute("Status").toString());
									
									Element airLineElem=ownerDoc.createElementNS(NS_OTA, "ota:Airline");
									airLineElem.setAttribute("Code", XMLUtils.getValueAtXPath(flightSegElem, "./ota:OperatingAirline/@Code"));
									specSSRElem.appendChild(airLineElem);
									
									Element flightLegElem=ownerDoc.createElementNS(NS_OTA, "ota:FlightLeg");
									flightLegElem.setAttribute("FlightNumber",  ssrJson.get(JSON_PROP_FLIGHTNBR).toString().replaceAll("\\s+",""));
									flightLegElem.setAttribute("Date", "");
									
									Element departureAirportElem=ownerDoc.createElementNS(NS_OTA, "ota:DepartureAirport");
									departureAirportElem.setAttribute("LocationCode", XMLUtils.getValueAtXPath(flightSegElem, "./ota:DepartureAirport/@LocationCode"));
									
									flightLegElem.appendChild(departureAirportElem);
									
									Element arrivalAirportElem=ownerDoc.createElementNS(NS_OTA, "ota:ArrivalAirport");
									arrivalAirportElem.setAttribute("LocationCode",  XMLUtils.getValueAtXPath(flightSegElem, "./ota:ArrivalAirport/@LocationCode"));
									
									flightLegElem.appendChild(arrivalAirportElem);
									
									
									specSSRElem.appendChild(flightLegElem);
									
									specSSRElems.appendChild(specSSRElem);
									
								}
							
							}
	
							ssrElem.appendChild(specSSRElems);
						
							tpaEtxElem.appendChild(ssrElem);
						}
						cancelRQElem.appendChild(tpaEtxElem);
					}
					
				}
			}
		
		return reqElem;
	}

	/*private static String getSICancelType(String cancelType) {
		String cancelSIType=null;
		switch(cancelType)
		{
		case "CANCELPASSENGER":  cancelSIType=CANCEL_TYPE_PAX.toString();  break;
		case "CANCELJOU": cancelSIType=CANCEL_TYPE_PAX.toString(); break;
		case "CANCELSSR": cancelSIType=CANCEL_TYPE_PAX.toString(); break;
		case "FULLCANCEL": cancelSIType=CANCEL_TYPE_PAX.toString(); break;
		default:cancelSIType="";
		
		}
		
		return cancelType;
		
	}*/
	
	
}
