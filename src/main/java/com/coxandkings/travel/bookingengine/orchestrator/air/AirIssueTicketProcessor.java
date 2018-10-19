
package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.DBServiceConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.Credential;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AirIssueTicketProcessor implements AirConstants  {
	
	private static final Logger logger = LogManager.getLogger(AirPriceProcessor.class);
	
	public static String process(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		List<String> bookingPnrList=null;
		KafkaBookProducer bookProducer = null;
	    JSONObject orderDetailsDB=null;
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
			bookingPnrList=new ArrayList<String>();
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		
            reqElem = createSIRequest(opConfig, usrCtx,reqJson,bookingPnrList,orderDetailsDB);
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
		try {    
            Element resElem = null;
            //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
            resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
            if (resElem == null) {
            	throw new Exception("Null response received from SI");
            }
            JSONObject resJson = new JSONObject();
            JSONObject resBodyJson = new JSONObject();
            JSONArray supplierBookRefJsonArr = new JSONArray();
            
        	
            JSONArray productsArr = orderDetailsDB.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRODS);
            if(productsArr == null) 
	        	logger.debug(String.format("Unable to create issueTicket response as no Bookings found for bookID: %s ", reqBodyJson.getString(JSON_PROP_BOOKID)));
            
            Map<String,JSONObject> airlinePNRtoProdMap=new HashMap<String,JSONObject>();
           for(int i=0;i<productsArr.length();i++) {
        	   JSONObject productJson=productsArr.getJSONObject(i);
        	
        	   String prodPnr=productJson.getJSONObject(JSON_PROP_ORDERDETAILS).optString(JSON_PROP_AIRLINEPNR);
        	   if(bookingPnrList.contains(prodPnr))
        	   {
        		   airlinePNRtoProdMap.put(prodPnr, productJson);
        	   }
        	   
           }
            Element[] resWrapperElems = AirPriceProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirDemandTicketRSWrapper"));
            for (Element resWrapperElem : resWrapperElems) {
            	//int sequenceNo=Integer.parseInt(XMLUtils.getValueAtXPath(resWrapperElem, "./air:SupplierID"));
            	
            	JSONObject supplierBookRefJson=new JSONObject();
            	Element resBodyElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_AirDemandTicketRS");
            	/*if((XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:Success"))!=null && ((XMLUtils.getValueAtXPath(resBodyElem, "./ota:Success")).toString()).equalsIgnoreCase("true")) {
            		supplierBookRefJson.put(JSON_PROP_STATUS, String.format("Ticket issued succesfully for pnr:%s", (XMLUtils.getValueAtXPath(resBodyElem, "./ota:BookingReferenceID/@ID"))));
            		supplierBookRefJsonArr.put(supplierBookRefJson);
            	}
            	else {
            		supplierBookRefJson.put(JSON_PROP_STATUS, String.format("Ticket issue failure for pnr:%s", (XMLUtils.getValueAtXPath(resBodyElem, "./ota:BookingReferenceID/@ID"))));
            		supplierBookRefJsonArr.put(supplierBookRefJson);
            	}*/
            	  Element[] ticketItemInfoElems =XMLUtils.getElementsAtXPath(resBodyElem, "ota:TicketItemInfo");
            	  JSONArray ticketInfoArray=new JSONArray();
            	  for (Element ticketItemInfoElem : ticketItemInfoElems) {
            		  JSONObject ticketInfoJson=new JSONObject();
            		  ticketInfoJson.put(JSON_PROP_TICKETNBR,  ticketItemInfoElem.getAttribute("TicketNumber"));
            		  ticketInfoJson.put(JSON_PROP_TICKETTYPE,  ticketItemInfoElem.getAttribute("Type"));
            		  ticketInfoArray.put(ticketInfoJson);
            		  
            	  }
            	  supplierBookRefJson.put((JSON_PROP_SUPPREF), (XMLUtils.getValueAtXPath(resWrapperElem, "./air:SupplierID")));  
            	supplierBookRefJson.put(JSON_PROP_TICKETINGINFO,ticketInfoArray);
            	//supplierBookRefJson.put(JSON_PROP_STATUS, String.format("Ticket issued succesfully for pnr:%s", (XMLUtils.getValueAtXPath(resBodyElem, "./ota:BookingReferenceID/@ID"))));
            	supplierBookRefJson.put(JSON_PROP_STATUS, "Success");
            	supplierBookRefJsonArr.put(supplierBookRefJson);
            }
            resBodyJson.put(JSON_PROP_SUPPBOOKREFS, supplierBookRefJsonArr);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
            bookProducer = new KafkaBookProducer();
            sendPostCancelKafkaMessage(bookProducer,resJson,reqJson,airlinePNRtoProdMap,usrCtx);
            return resJson.toString();
            
		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}

	private static void sendPostCancelKafkaMessage(KafkaBookProducer bookProducer, JSONObject resJson,
			JSONObject reqJson, Map<String, JSONObject> airlinePNRtoProdMap, UserContext usrCtx) throws Exception {
		
		JSONObject kafkaMessageJson=new JSONObject(new JSONTokener(resJson.toString()));
		kafkaMessageJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PROD, PRODUCT_AIR);
		kafkaMessageJson.getJSONObject(JSON_PROP_RESBODY).put("operation", "issueTicket");
		kafkaMessageJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqJson.getJSONObject(JSON_PROP_REQBODY).get(JSON_PROP_BOOKID).toString());
		JSONArray suppBookRefJsonArr=kafkaMessageJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_SUPPBOOKREFS);
		for(int i=0;i<suppBookRefJsonArr.length();i++)
		{
			
			JSONObject suppBookRefJson=suppBookRefJsonArr.getJSONObject(i);

			String suppID=suppBookRefJson.getString(JSON_PROP_SUPPREF);
			ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT,
					PROD_CATEG_SUBTYPE_FLIGHT, suppID);
			
			
			if (prodSupplier == null) {
				logger.warn(String.format("Product supplier %s not found for user/client", suppID));
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR400");
			}
			 Credential pccCredential = prodSupplier.getCredentialForKey(MDM_VAL_PCC);
			 if(pccCredential==null)
				 pccCredential = prodSupplier.getCredentialForKey("Pseudo City Code");
			 
			 if (pccCredential != null) {
				 suppBookRefJson.put(JSON_PROP_TICKETINGPCC, pccCredential.getValue());
				}
				else {
					suppBookRefJson.put(JSON_PROP_TICKETINGPCC,"");
				}
			
			JSONObject reqSuppBookRefJson=reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_SUPPBOOKREFS).getJSONObject(i);
			JSONObject productJson=airlinePNRtoProdMap.get(reqSuppBookRefJson.getString(JSON_PROP_BOOKREFID));
			suppBookRefJson.put(JSON_PROP_ORDERID, productJson.getString(JSON_PROP_ORDERID));
			JSONArray paxInfoArr=productJson.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONArray(JSON_PROP_PAXINFO);
			JSONArray ticketingInfoJsonArr=suppBookRefJson.getJSONArray(JSON_PROP_TICKETINGINFO);
			if(ticketingInfoJsonArr.length()==0 || ticketingInfoJsonArr==null) {
				logger.warn(String.format("No tickets found"));
				throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR400");
			}
			for(int j=0,k=0;j<ticketingInfoJsonArr.length();j++) {
				JSONObject ticketingInfoJson=ticketingInfoJsonArr.getJSONObject(j);
				JSONObject paxInfoJson=paxInfoArr.getJSONObject(k);
				if(paxInfoJson.getString(JSON_PROP_STATUS).equalsIgnoreCase("cancelled") ||paxInfoJson.getString(JSON_PROP_STATUS).equalsIgnoreCase("canceled")) {
					while(paxInfoJson.getString(JSON_PROP_STATUS).equalsIgnoreCase("cancelled") ||paxInfoJson.getString(JSON_PROP_STATUS).equalsIgnoreCase("canceled")) {
						k++;
					}
					paxInfoJson=paxInfoArr.getJSONObject(k);
			}else {
				ticketingInfoJson.put("paxID",paxInfoJson.getString("passengerID"));
				k++;
			}
				
				
			}
		}
		bookProducer.runProducer(1, kafkaMessageJson);
		logger.trace(String.format("Air IssueTicket Response Kafka Message: %s", kafkaMessageJson.toString()));
		
		
	}


	private static void validateRequestParameters(JSONObject reqJson) throws JSONException, ValidationException {
		 AirRequestValidator.validatebookID(reqJson);
		 AirRequestValidator.validateSupplierBookRefs(reqJson);
		
	}

	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqJson) throws ValidationException {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqJson, List<String> bookingPnrList,JSONObject orderDetailsDB) throws ValidationException {
		 JSONObject reqHdrJson=reqJson.getJSONObject(JSON_PROP_REQHEADER);
		 JSONObject reqBodyJson=reqJson.getJSONObject(JSON_PROP_REQBODY);

		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_AirDemandTicketRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		 
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
			
		Element retrieveResElem=AirCancelProcessor.getRetrieveResponse(reqHdrJson, reqBodyJson,orderGDSmap);
		
		Element[] retreieveResWrapperElems=XMLUtils.getElementsAtXPath(retrieveResElem, "./airi:ResponseBody/air:OTA_AirBookRSWrapper");
		JSONArray supplierBookRefJsonArr=reqBodyJson.getJSONArray(JSON_PROP_SUPPBOOKREFS);
		
		
		JSONArray productDetailsJsonArr=orderDetailsDB.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRODS);
		Map<String,JSONObject> productDetailsPnrMap=new HashMap<String,JSONObject>();
		for(int p=0;p<productDetailsJsonArr.length();p++) {
			JSONObject productDetailJson=productDetailsJsonArr.getJSONObject(p);
			if(productDetailJson.getJSONObject(JSON_PROP_ORDERDETAILS).has(JSON_PROP_AIRLINEPNR))
			{
				productDetailsPnrMap.put(productDetailJson.getJSONObject(JSON_PROP_ORDERDETAILS).getString(JSON_PROP_AIRLINEPNR), productDetailJson);
			}
			
		}
		
		 
		for(int i=0;i<supplierBookRefJsonArr.length();i++) {
			JSONObject supplierBookRefJson=supplierBookRefJsonArr.getJSONObject(i);
			JSONObject prodDetailJson=productDetailsPnrMap.get(supplierBookRefJson.getString(JSON_PROP_BOOKREFID));
			reqBodyElem.appendChild(wrapperElem);
			
			/*supplierBookRefJson.getString(JSON_PROP_SUPPREF);
			supplierBookRefJson.getString(JSON_PROP_BOOKREFID);*/
			supplierBookRefJson.getString(JSON_PROP_ORDERID);
			bookingPnrList.add(supplierBookRefJson.getString(JSON_PROP_BOOKREFID));
			
			Element retrieveResWrapperElem=AirCancelProcessor.getRetrieveWrapperByBookRefId(supplierBookRefJson.getString(JSON_PROP_BOOKREFID),retreieveResWrapperElems);
			
			
			
			String suppID=supplierBookRefJson.getString(JSON_PROP_SUPPREF);
			
			ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID);
			
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
			Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem, String.format("./com:SupplierCredentials[com:SupplierID = '%s']", suppID));
			if (suppCredsElem == null) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}
			
			Element suppIDElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./air:SupplierID");
			suppIDElem.setTextContent(suppID);
			
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./air:Sequence");
			sequenceElem.setTextContent(String.valueOf(i));
			
			
			Element airDemandTicketRQElem=XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_AirDemandTicketRQ");
			//airDemandTicketRQElem.setAttribute("TransactionIdentifier", supplierBookRefJson.getString(JSON_PROP_TRANSACTID));
			airDemandTicketRQElem.setAttribute("TransactionIdentifier", prodDetailJson.getString(JSON_PROP_TRANSACTID));
			
			if(XMLUtils.getValueAtXPath(retrieveResWrapperElem, "./ota:OTA_AirBookRS/@Version").isEmpty()) {
				airDemandTicketRQElem.setAttribute("Version",Integer.toString(1));
			}
			else {
				airDemandTicketRQElem.setAttribute("Version",XMLUtils.getValueAtXPath(retrieveResWrapperElem, "./ota:OTA_AirBookRS/@Version"));
			}
			
			Element[] bookRefElemArr=XMLUtils.getElementsAtXPath(retrieveResWrapperElem, "./ota:OTA_AirBookRS/ota:AirReservation/ota:BookingReferenceID");
			
			Element demandTicketDetailElem=XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_AirDemandTicketRQ/ota:DemandTicketDetail");
			if(!(supplierBookRefJson.has(JSON_PROP_BOOKREFID)) && (supplierBookRefJson.getString(JSON_PROP_BOOKREFID).equals(""))) {
				throw new ValidationException(reqHdrJson,"TRLERR039");
			}

			for (Element bookRefElem : bookRefElemArr)
			{
				Element bookRefIdElem=ownerDoc.createElementNS(NS_OTA, "ota:BookingReferenceID");
				demandTicketDetailElem.appendChild(bookRefIdElem);
				bookRefIdElem.setAttribute("Type", bookRefElem.getAttribute("Type"));
				bookRefIdElem.setAttribute("ID", bookRefElem.getAttribute("ID"));
			}
			
			
			
			/*Element bookRefIdElem=ownerDoc.createElementNS(NS_OTA, "ota:BookingReferenceID");
			
			bookRefIdElem.setAttribute("ID", supplierBookRefJson.getString(JSON_PROP_BOOKREFID));
			bookRefIdElem.setAttribute("Type", "14");*/
			
			
			
			Element paymentInfoElem=ownerDoc.createElementNS(NS_OTA, "ota:PaymentInfo");
			demandTicketDetailElem.appendChild(paymentInfoElem);
			
			paymentInfoElem.setAttribute("PaymentType", "32");
			
			Element docInstrsElem=ownerDoc.createElementNS(NS_OTA, "ota:DocumentInstructions");
			demandTicketDetailElem.appendChild(docInstrsElem);
			
			Element docInstructElem=ownerDoc.createElementNS(NS_OTA, "ota:DocumentInstruction");
			docInstrsElem.appendChild(docInstructElem);
			
			Random rand = new Random();
			int num = rand.nextInt(9000000) + 1000000;
			
			docInstructElem.setAttribute("Number", Integer.toString(num));
			
			
			
			Element tpaExtensionsElem=ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
			demandTicketDetailElem.appendChild(tpaExtensionsElem);
			
			//TODO:retrieve from db
			Element carrierCodeElem=ownerDoc.createElementNS(NS_OTA, "ota:CarrierCode");
			//carrierCodeElem.setTextContent(supplierBookRefJson.getString(JSON_PROP_AIRLINECODE));
			
			JSONObject odoOptsJson=prodDetailJson.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONObject(JSON_PROP_FLIGHTDETAILS).getJSONArray(JSON_PROP_ORIGDESTOPTS).getJSONObject(0);
			carrierCodeElem.setTextContent(odoOptsJson.getJSONArray(JSON_PROP_FLIGHTSEG).getJSONObject(0).getJSONObject(JSON_PROP_MARKAIRLINE).getString(JSON_PROP_AIRLINECODE));
			tpaExtensionsElem.appendChild(carrierCodeElem);
			
			Element numResponsesElem=ownerDoc.createElementNS(NS_OTA, "ota:NumResponses");
			numResponsesElem.setTextContent("1");
			tpaExtensionsElem.appendChild(numResponsesElem);
			
		}
	
		
		
		return reqElem;
	}
	

}
