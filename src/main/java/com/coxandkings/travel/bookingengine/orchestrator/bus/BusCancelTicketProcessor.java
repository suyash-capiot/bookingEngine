package com.coxandkings.travel.bookingengine.orchestrator.bus;




import java.net.URL;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.DBServiceConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.bus.BusConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
//import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class BusCancelTicketProcessor implements BusConstants{

	private static final Logger logger = LogManager.getLogger(BusCancelTicketProcessor.class);
	public static String process(JSONObject reqJson) throws InternalProcessingException 
	{
		try
		{
			//OperationConfig opConfig = null;
			ServiceConfig opConfig = null;
			Element reqElem = null;
			JSONObject reqHdrJson = null, reqBodyJson = null;
			JSONObject kafkaMsgJson=null;
			KafkaBookProducer cancelProducer=null;
			JSONObject productJson = null;
			try
			{
				cancelProducer = new KafkaBookProducer();
				
				String retriveStr = BusRetrieveBookingProcessor.process(reqJson);
				JSONObject retriveJson = new JSONObject(retriveStr);
				 reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
				 reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
				 JSONArray seatsToCancelArr=null;
				if(retriveJson.has("errorMessage"))
				{
					logger.info(retriveJson.get("errorMessage"));
				}
				else
				{
					JSONArray passengerArr = retriveJson.getJSONObject(JSON_PROP_RESBODY).getJSONObject("bookTicket").getJSONArray("passengers");
					seatsToCancelArr= reqBodyJson.getJSONObject(JSON_PROP_SERVICE).getJSONArray("seatsToCancel");
					if(passengerArr.length()==seatsToCancelArr.length())
					{
					JSONObject errObj = new JSONObject();
						errObj.put("errorMessage", "you cannot cancel all the seats you have booked..");
						return errObj.toString();
					}
				}
				 
				
	            opConfig = BusConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

				 reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true); // xmshell
				Document ownerDoc = reqElem.getOwnerDocument();
				
				Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./busi:RequestBody");
				Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./bus:OTA_BusCancelTicketRQWrapper");
				reqBodyElem.removeChild(wrapperElem);
				
	           
				
				
				String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
				String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
				String userID = reqHdrJson.getString(JSON_PROP_USERID);
				
				UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
				List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT,PROD_CATEG_SUBTYPE_BUS);
				
				
				BusSearchProcessor.createHeader(reqElem, sessionID, transactionID, userID);
				

				
				Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
						"./bus:RequestHeader/com:SupplierCredentialsList");
				for (ProductSupplier prodSupplier : prodSuppliers) {
					suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
				}
				

				
					JSONObject busServiceJson = reqBodyJson.getJSONObject(JSON_PROP_SERVICE);
					
					if(reqBodyJson.getString("type").equalsIgnoreCase(JSON_PROP_BUS_CANNCELTYPE_CANCELPAX))
					{
						JSONObject getBookingJson = HTTPServiceConsumer.consumeJSONService("DB_SERVICE", new URL(String.format(DBServiceConfig.getDBServiceURL(), reqBodyJson.getString("bookID"))), DBServiceConfig.getHttpHeaders(),"GET",null);
						 if(getBookingJson!=null)
						 {
							 JSONObject resBodyBookingJson = getBookingJson.getJSONObject(JSON_PROP_RESBODY);
								 JSONArray productsJsonArr = resBodyBookingJson.getJSONArray("products");

									 productJson = getProductJson(productsJsonArr,retriveJson.getJSONObject(JSON_PROP_RESBODY).getJSONObject("bookTicket"));

									  if(reqBodyJson.getString("requestType").equalsIgnoreCase("amend") )
									 {
										 logger.error("The request forwarded to our operations team.");
										  OperationsToDoProcessor.callOperationTodo(ToDoTaskName.AMEND, ToDoTaskPriority.MEDIUM,ToDoTaskSubType.ORDER, productJson.getString("orderID"), reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
										  JSONObject errObj = new JSONObject();
											errObj.put("errorMessage", "you cannot cancel all the seats you have booked..");
											return errObj.toString();
									 }
										JSONArray paxJsonArr = productJson.getJSONObject("orderDetails").getJSONArray("paxInfo");
										int cancelSeatsCount=0;
										for(int m=0;m<paxJsonArr.length();m++)
										{
											JSONObject paxJSon = paxJsonArr.getJSONObject(m);
											if(paxJSon.getString("status").equalsIgnoreCase("Cancelled"))
											{
												cancelSeatsCount++;
												
											}
										}
										
											if(paxJsonArr.length()-cancelSeatsCount==1 && seatsToCancelArr.length()>0)
											{
								
												JSONObject errObj = new JSONObject();
												errObj.put("errorMessage","you cannot cancel all the seats you have booked..");
												return errObj.toString();
											}
										

						 }
						
						
					}
					
					Element suppWrapperElem = null;
					suppWrapperElem = (Element) wrapperElem.cloneNode(true);
					reqBodyElem.appendChild(suppWrapperElem);
					
					
			        XMLUtils.setValueAtXPath(suppWrapperElem, "./bus:SupplierID", busServiceJson.getString(JSON_PROP_SUPPREF));
			        XMLUtils.setValueAtXPath(suppWrapperElem, "./bus:Sequence", String.valueOf(1));
			        
			        Element otacancelTkt = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_BusCancelTicketRQ");
			        Element newElem;
			        
			        if(busServiceJson.get(JSON_PROP_OPERATORID).toString().isEmpty()==false)
					  {
						   newElem = ownerDoc.createElementNS(NS_OTA, "operatorId");
						  newElem.setTextContent(busServiceJson.get(JSON_PROP_OPERATORID).toString());
						  otacancelTkt.appendChild(newElem);
					  }
			        
			        if(busServiceJson.get("phone").toString().isEmpty()==false)
					  {
						   newElem = ownerDoc.createElementNS(NS_OTA, "phoneNum");
					
						  newElem.setTextContent(busServiceJson.get("phone").toString());
						  otacancelTkt.appendChild(newElem);
					  }
			        if(busServiceJson.get(JSON_PROP_TICKETNO).toString().isEmpty()==false)
					  {
						   newElem = ownerDoc.createElementNS(NS_OTA, "ticketNo");
						  newElem.setTextContent(busServiceJson.get(JSON_PROP_TICKETNO).toString());
						  otacancelTkt.appendChild(newElem);
					  }
			        if(busServiceJson.get("partialCancellation").toString().isEmpty()==false)
					  {
						   newElem = ownerDoc.createElementNS(NS_OTA, "partialCancellation");
						  newElem.setTextContent(busServiceJson.get("partialCancellation").toString());
						  otacancelTkt.appendChild(newElem);
					  }

			        
			        JSONArray seatToCancelArr = busServiceJson.getJSONArray("seatsToCancel");

			        
			        for(int k=0;k<seatToCancelArr.length();k++)
			        {
			        	JSONObject seatCancelJson = seatToCancelArr.getJSONObject(k);
			        	if(seatCancelJson.get(JSON_PROP_SEATNO).toString().isEmpty()==false)
						  {
							  newElem = ownerDoc.createElementNS(NS_OTA, "seatToCancel");
							  newElem.setTextContent(seatCancelJson.get(JSON_PROP_SEATNO).toString());
							  otacancelTkt.appendChild(newElem);
						  }

			        }

			        if(busServiceJson.get("PNRNo").toString().isEmpty()==false)
					  {
						  newElem = ownerDoc.createElementNS(NS_OTA, "PNRNo");
						  newElem.setTextContent(busServiceJson.get("PNRNo").toString());
						  otacancelTkt.appendChild(newElem);
					  }

				
				reqBodyJson.put("operation", "cancel");
				reqJson.put(JSON_PROP_REQBODY, reqBodyJson);
				 kafkaMsgJson = new JSONObject(reqJson.toString());
				cancelProducer.runProducer(1, kafkaMsgJson);
			}
			catch(Exception e)
			{
				logger.error("Exception during cancel request processing ", e);
    			throw new RequestProcessingException(e);
			}
			
			 Element resElem = null;
	          //resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), BusConfig.getHttpHeaders(), reqElem);
			 resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			  ToDoTaskSubType todoSubType=null;
	          if((reqBodyJson.optString("requestType")!=null)&&(reqBodyJson.getString("requestType").equals("cancel"))) {
	        	  String cancelType=reqBodyJson.getString(JSON_PROP_CANCELTYPE);
		            switch(cancelType) {
		            case "ALL":todoSubType=ToDoTaskSubType.ORDER;break;
		            case "PAX":todoSubType=ToDoTaskSubType.PASSENGER;break;
		            default:todoSubType=null;
		       
	          }
	          }
	          if(productJson!=null)
	             OperationsToDoProcessor.callOperationTodo(ToDoTaskName.CANCEL, ToDoTaskPriority.MEDIUM,todoSubType, productJson.getString("orderID"), reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
	          else
	          {
	        	  logger.info("orderID not found ");
	          }
			 
			 if (resElem == null) {
	          	throw new Exception("Null response received from SI");
	          }
	        
	            
	            
	        JSONObject resBodyJson = new JSONObject();
		    JSONObject getCancelTktJson = new JSONObject();
		    
//		    Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem, "./busi:ResponseBody/bus:OTA_BusCancelTicketRSWrapper");
	        Element[] resWrapperElems=BusSeatMapProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./busi:ResponseBody/bus:OTA_BusCancelTicketRSWrapper"));

		    for (Element wrapperElement : resWrapperElems) {
	        	Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_BusCancelTicketRS");
	        	getCancelTktJson(resBodyElem, getCancelTktJson);
	        }
		    resBodyJson.put(JSON_PROP_SERVICE, getCancelTktJson);
	        JSONObject resJson = new JSONObject();
	        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
	        resJson.put(JSON_PROP_RESBODY, resBodyJson);
		    if(getCancelTktJson.has("errorMessage"))
		    {
		    	return resJson.toString();
		    }
		    else
		    {
		    	 JSONObject service = reqBodyJson.getJSONObject(JSON_PROP_SERVICE);
			        JSONArray seatsTocancelArr = service.getJSONArray("seatsToCancel");
			        kafkaMsgJson = new JSONObject(resJson.toString());
			        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).getJSONObject(JSON_PROP_SERVICE).put("seatsToCancel", seatsTocancelArr);
			        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID));
			        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("product", reqBodyJson.get("product"));
			        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("type", reqBodyJson.get("type"));
			        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("operation", "cancel");
			        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("requestType", reqBodyJson.get("requestType"));
			     
			        
			        System.out.println("KAfkaRQMsg->"+kafkaMsgJson);
					cancelProducer.runProducer(1, kafkaMsgJson);
		    }
	        
	       
	        

	        
			return resJson.toString();

		}
		catch(Exception e)
		{
			 logger.error("Exception received while processing cancel operation ", e);
			  throw new InternalProcessingException(e);
		}
		
		
	}

	private static JSONObject getProductJson(JSONArray productsJsonArr, JSONObject bookTicketJson) {
		JSONObject prodJson = null;
		for(int i=0;i<productsJsonArr.length();i++)
		{
			prodJson = productsJsonArr.getJSONObject(i);
			if(prodJson.getString("supplierID").equalsIgnoreCase(bookTicketJson.getString(JSON_PROP_SUPPREF)) &&
					prodJson.getString("productSubCategory").equalsIgnoreCase("Bus") && 
					prodJson.getString("ticketNo").equalsIgnoreCase(bookTicketJson.getString(JSON_PROP_TICKETNO))&&
					prodJson.getString("PNRNo").equalsIgnoreCase(bookTicketJson.getString(JSON_PROP_PNRNO)))
			{
				break;
			}
			else
				continue;
		}
		return prodJson;
	}




	private static void getCancelTktJson(Element resBodyElem, JSONObject getCancelTktJson) {

		if(XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:Errors")!=null)
    	{
    		Element errorMessage=XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:Errors/ota:Error");
    		String errMsgStr=XMLUtils.getValueAtXPath(errorMessage, "./@ShortText");
    		getCancelTktJson.put("errorMessage", errMsgStr);
    	}
		else
		{
			getCancelTktJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath((Element)resBodyElem.getParentNode(), "./bus:SupplierID"));
	          getCancelTktJson.put(JSON_PROP_STATUS, XMLUtils.getValueAtXPath(resBodyElem, "./ota:Status"));
	          getCancelTktJson.put("message", XMLUtils.getValueAtXPath(resBodyElem, "./ota:Message"));
	          getCancelTktJson.put(JSON_PROP_TOTALFARE, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(resBodyElem, "./ota:Status"), 0));
	          getCancelTktJson.put("refundAmount", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(resBodyElem, "./ota:RefundAmount"), 0));
	          getCancelTktJson.put("chargePct", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(resBodyElem, "./ota:ChargePct"), 0));
	          getCancelTktJson.put("cancellationCharge", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(resBodyElem, "./ota:cancellationCharge"), 0));
	          getCancelTktJson.put(JSON_PROP_CURRENCY, XMLUtils.getValueAtXPath(resBodyElem, "./ota:Currency"));
		}
		

	}
}
