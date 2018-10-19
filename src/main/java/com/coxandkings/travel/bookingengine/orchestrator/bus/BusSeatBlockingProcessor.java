package com.coxandkings.travel.bookingengine.orchestrator.bus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.bus.BusConfig;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class BusSeatBlockingProcessor implements BusConstants{

	private static final Logger logger = LogManager.getLogger(BusSeatBlockingProcessor.class);
	public static String process(JSONObject reqJson) {
		

		
		try
		{
			//OperationConfig opConfig = null;
			ServiceConfig opConfig = null;
			Element reqElem = null;String sessionID="";
			JSONObject reqHdrJson = null, reqBodyJson = null;
			try
			{
				 opConfig = BusConfig.getOperationConfig("SeatBlocking");
				 reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
				Document ownerDoc = reqElem.getOwnerDocument();
				
				Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./busi:RequestBody");
				Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./bus:OTA_BusBlockTicketRQWrapper");
				reqBodyElem.removeChild(wrapperElem);
				
				
				 reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
				 reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

				 sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
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
				
				//------------ redis--------------
//				Jedis redisConn = RedisConfig.getRedisConnectionFromPool();
//				String redisKey = sessionID.concat("|").concat(PRODUCT_BUS).concat("|").concat("seatmap");
//				Map<String, String> reprcSuppFaresMap = redisConn.hgetAll(redisKey);
//				if (reprcSuppFaresMap == null) {
//					logger.error("seatmap not found in redis");
//					throw new Exception(String.format("GetLayout context not found,for %s", redisKey));
//				}
			
				
				JSONArray busserviceJSONArr = reqBodyJson.getJSONArray(JSON_PROP_SERVICE);
				for (int y=0; y < busserviceJSONArr.length(); y++) 
				{
					
					JSONObject busServiceJson = busserviceJSONArr.getJSONObject(y);
					Element suppWrapperElem = null;
					suppWrapperElem = (Element) wrapperElem.cloneNode(true);
					reqBodyElem.appendChild(suppWrapperElem);
					
					XMLUtils.setValueAtXPath(suppWrapperElem, "./bus:SupplierID", busServiceJson.getString("supplierRef"));
					XMLUtils.setValueAtXPath(suppWrapperElem, "./bus:Sequence", String.valueOf(y));
					Element otaBlockTkt = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_BusBlockTicketRQ");
					Element tktinfo = ownerDoc.createElementNS(NS_OTA, "TicketInfo");
					otaBlockTkt.appendChild(tktinfo);
			  
			 
			  
//			  if(busServiceJson.get("RouteScheduleId").toString().isEmpty()==false)
//			  {
//				  Element newElem = ownerDoc.createElementNS(NS_OTA, "RouteScheduleId");
//				  newElem.setTextContent(busServiceJson.get("RouteScheduleId").toString());
//				  tktinfo.appendChild(newElem);
//			  }
				  if(busServiceJson.get(JSON_PROP_JOURNEYDATE).toString().isEmpty()==false)
				  {
					  Element newElem = ownerDoc.createElementNS(NS_OTA, "JourneyDate");
					  newElem.setTextContent(busServiceJson.get(JSON_PROP_JOURNEYDATE).toString());
					  tktinfo.appendChild(newElem);
				  }
				  if(busServiceJson.get(JSON_PROP_SERVICEID).toString().isEmpty()==false)
				  {
					  Element newElem = ownerDoc.createElementNS(NS_OTA, "serviceId");
					  newElem.setTextContent(busServiceJson.get("serviceId").toString());
					  tktinfo.appendChild(newElem);
				  }
				  if(busServiceJson.get(JSON_PROP_LAYOUTID).toString().isEmpty()==false)
				  {
					  Element newElem = ownerDoc.createElementNS(NS_OTA, "layoutId");
					  newElem.setTextContent(busServiceJson.get(JSON_PROP_LAYOUTID).toString());
					  tktinfo.appendChild(newElem);
				  }
				  if(busServiceJson.get(JSON_PROP_SOURCE).toString().isEmpty()==false)
				  {
					  Element newElem = ownerDoc.createElementNS(NS_OTA, "sourceStationId");
					  newElem.setTextContent(busServiceJson.get("sourceStationId").toString());
					  tktinfo.appendChild(newElem);
				  }
				  if(busServiceJson.get(JSON_PROP_DESTINATION).toString().isEmpty()==false)
				  {
					  Element newElem = ownerDoc.createElementNS(NS_OTA, "destinationStationId");
					  newElem.setTextContent(busServiceJson.get("destinationStationId").toString());
					  tktinfo.appendChild(newElem);
				  }
				  JSONObject boardingJson = busServiceJson.optJSONObject("boardingPointDetails");
				  if(boardingJson!=null)
				  {
					  Element newElem = ownerDoc.createElementNS(NS_OTA, "boardingPointID");
					  newElem.setTextContent(boardingJson.get("id").toString());
					  tktinfo.appendChild(newElem);
				  }
				  JSONObject droppingJson = busServiceJson.optJSONObject("droppingPointDetails");
				  if(droppingJson!=null)
				  {
					  Element newElem = ownerDoc.createElementNS(NS_OTA, "droppingPointID");
					  newElem.setTextContent(droppingJson.get("id").toString());
					  tktinfo.appendChild(newElem);
				  }
				  if(busServiceJson.get(JSON_PROP_OPERATORID).toString().isEmpty()==false)
				  {
					  Element newElem = ownerDoc.createElementNS(NS_OTA, "OperatorID");
					  newElem.setTextContent(busServiceJson.get(JSON_PROP_OPERATORID).toString());
					  tktinfo.appendChild(newElem);
				  }
			  
//			  if(busServiceJson.opt("PickUpID").toString().isEmpty()==false)
//			  {
//				  Element newElem = ownerDoc.createElementNS(NS_OTA, "PickUpID");
//				  newElem.setTextContent(busServiceJson.get("PickUpID").toString());
//				  tktinfo.appendChild(newElem);
//			  }
//			  
	//
//			  if(busServiceJson.get("address").toString().isEmpty()==false)
//			  {
//				  Element newElem = ownerDoc.createElementNS(NS_OTA, "address");
//				  newElem.setTextContent(busServiceJson.get("address").toString());
//				  tktinfo.appendChild(newElem);
//			  }
//			  if(busServiceJson.get("ladiesSeat").toString().isEmpty()==false)
//			  {
//				  Element newElem = ownerDoc.createElementNS(NS_OTA, "ladiesSeat");
//				  newElem.setTextContent(busServiceJson.get("ladiesSeat").toString());
//				  tktinfo.appendChild(newElem);
//			  }	  
//			  
				  
				  
				  getPassangers(ownerDoc, busServiceJson, otaBlockTkt,sessionID);
			  
				}
			}
			catch(Exception e)
			{
				logger.error("Exception during search request processing", e);
    			throw new RequestProcessingException(e);
			}
			
			System.out.println(XMLTransformer.toString(reqElem));
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), BusConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			System.out.println(XMLTransformer.toString(resElem));
			
			JSONObject resBodyJson = new JSONObject();
	       
	        JSONArray blockTktJsonArr = new JSONArray();
	        
	        Element[] wrapperElems=BusSeatMapProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem,
					"./busi:ResponseBody/bus:OTA_BusBlockTicketRSWrapper"));
	        for (Element wrapperElement : wrapperElems) {
	        	Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_BusBlockTicketRS");
	        	getBlockJSON(resBodyElem, blockTktJsonArr);
	        }
	        resBodyJson.put("blockTicket", blockTktJsonArr);
	        
	        JSONObject resJson = new JSONObject();
	        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
	        resJson.put(JSON_PROP_RESBODY, resBodyJson);
//	        System.out.println(resJson.toString());
	        
	        pushBlockIdToRedis(reqJson,resJson,sessionID);
	        return resJson.toString();
		}
		catch(Exception e)
		{
			logger.error("exception in BusSeatBlocking|Process method");
			e.printStackTrace();
			return null;
		}
		
	}

	public static void getPassangers(Document ownerDoc, JSONObject busServiceJson, Element otaBlockTkt,String sessionID) throws RequestProcessingException {
		Element passangers = ownerDoc.createElementNS(NS_OTA, "Passengers");
		  
		Element contactinfo = null;
		  JSONArray passArr = new JSONArray();
		  passArr = busServiceJson.getJSONArray("passengers");
		  Element newElem= null;
		  JSONObject mapJson=null;
		  for(int k=0; k<passArr.length(); k++)
		  {
			  JSONObject passJson = passArr.getJSONObject(k);
			  Element passElem = ownerDoc.createElementNS(NS_OTA, "Passenger");
			  if(Boolean.valueOf(passJson.get("isPrimary").toString()).equals(true))
			  {
				   contactinfo = ownerDoc.createElementNS(NS_OTA, "ContactInformation");
				  
				  if(passJson.get(JSON_PROP_FIRSTNAME).toString().isEmpty()==false)
				  {
					   newElem = ownerDoc.createElementNS(NS_OTA, "CustomerName");
					  newElem.setTextContent(passJson.get(JSON_PROP_FIRSTNAME).toString());
					  contactinfo.appendChild(newElem);
				  }
				  if(passJson.get(JSON_PROP_EMAIL).toString().isEmpty()==false)
				  {
					  newElem = ownerDoc.createElementNS(NS_OTA, "Email");
					  newElem.setTextContent(passJson.get(JSON_PROP_EMAIL).toString());
					  contactinfo.appendChild(newElem);
				  }
				  if(passJson.get(JSON_PROP_MOBILENBR).toString().isEmpty()==false)
				  {
					 newElem = ownerDoc.createElementNS(NS_OTA, "Phone");
					  newElem.setTextContent(passJson.get(JSON_PROP_MOBILENBR).toString());
					  contactinfo.appendChild(newElem);
				  }
				  if(passJson.get(JSON_PROP_MOBILENBR).toString().isEmpty()==false)
				  {
					  newElem = ownerDoc.createElementNS(NS_OTA, "Mobile");
					  newElem.setTextContent(passJson.get(JSON_PROP_MOBILENBR).toString());
					  contactinfo.appendChild(newElem);
				  }
				  if(contactinfo!=null)
						 otaBlockTkt.appendChild(contactinfo);
			  }
			  otaBlockTkt.appendChild(passangers);
			 
			  
			 
			  newElem = ownerDoc.createElementNS(NS_OTA, "Name");
			  newElem.setTextContent(passJson.opt(JSON_PROP_FIRSTNAME).toString());
			  passElem.appendChild(newElem);
			  try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();)
			  {
				String redisKey = sessionID.concat("|").concat(PRODUCT_BUS).concat("|").concat("seatmap");
				Map<String, String> reprcSuppFaresMap = redisConn.hgetAll(redisKey);
				if (reprcSuppFaresMap == null) {
					throw new Exception(String.format("SeatBlocking | price map not found ,for %s", redisKey));
				}
				String mapKey = BusSeatMapProcessor.getseatMapKeyForSeatFare(busServiceJson,passJson);
				mapJson = new JSONObject(reprcSuppFaresMap.get(mapKey));
			  }
			  catch(Exception e)
			  {
				  logger.error("Exception during seatBlocking request processing| not able to fetch price from redis", e);
	    			throw new RequestProcessingException(e);
			  }
			  
			  newElem = ownerDoc.createElementNS(NS_OTA, "Title");
			  newElem.setTextContent(passJson.opt(JSON_PROP_TITLE).toString());
			  passElem.appendChild(newElem);
			  try
			  {
				  if(mapJson.get("ladiesSeat").equals(true) && passJson.opt(JSON_PROP_TITLE).toString().equalsIgnoreCase("Mr"))
				  {
					  throw new Exception();
				  }
			  }
			  catch(Exception e)
			  {
				  logger.error(String.format("The requested seat(seatNumber->%s) is reserved for ladies.. Please try to book another seat.", mapJson.opt(JSON_PROP_SEATNO)), e);
	    			throw new RequestProcessingException(e);
			  }
			  newElem = ownerDoc.createElementNS(NS_OTA, "Age");
			  newElem.setTextContent(Integer.toString(Utils.calculateAge(passJson.getString("dob"))));
			  passElem.appendChild(newElem);
			  
			  
			  newElem = ownerDoc.createElementNS(NS_OTA, "Gender");
			  newElem.setTextContent(passJson.opt("gender").toString());
			  passElem.appendChild(newElem);
			  
			  newElem = ownerDoc.createElementNS(NS_OTA, "SeatNo");
			  newElem.setTextContent(passJson.get(JSON_PROP_SEATNO).toString());
			  passElem.appendChild(newElem);
			  

			  newElem = ownerDoc.createElementNS(NS_OTA, "ota:Fare");
			  newElem.setTextContent(mapJson.getBigDecimal("fare").toString());
			  passElem.appendChild(newElem);
			  
//			  newElem = ownerDoc.createElementNS(NS_OTA, "seatTypesList");
//			  newElem.setTextContent(passJson.get("seatTypesList").toString());
//			  passElem.appendChild(newElem);
//			  
//			  newElem = ownerDoc.createElementNS(NS_OTA, "seatTypeIds");
//			  newElem.setTextContent(passJson.get("seatTypeIds").toString());
//			  passElem.appendChild(newElem);
			  
			  //---------------
			  
//			  newElem = ownerDoc.createElementNS(NS_OTA, "IsPrimary");
//			  newElem.setTextContent(passJson.opt("IsPrimary").toString());
//			  passElem.appendChild(newElem);
//			  
//			  newElem = ownerDoc.createElementNS(NS_OTA, "IdNumber");
//			  newElem.setTextContent(passJson.opt("IdNumber").toString());
//			  passElem.appendChild(newElem);
//			  newElem = ownerDoc.createElementNS(NS_OTA, "IdType");
//			  newElem.setTextContent(passJson.opt("IdType").toString());
//			  passElem.appendChild(newElem);
			 
		  
			  //temporarily fare is hardcoded
			 
			  
//			  newElem = ownerDoc.createElementNS(NS_OTA, "isAcSeat");
//			  newElem.setTextContent(passJson.get("isAcSeat").toString());
//			  passElem.appendChild(newElem);
//			  
			  passangers.appendChild(passElem);
			  
		  }
	}

	private static void pushBlockIdToRedis(JSONObject reqJson,JSONObject resJson,String sessionId) {
		
		JSONObject resHeaderJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		
		try
		{
			
			JSONArray blockTktArr = resBodyJson.getJSONArray("blockTicket");
			
			Map<String,String> detailsMap= new  HashMap<String, String>();

		    JSONArray serviceArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_SERVICE);
		    for(int i=0;i<serviceArr.length();i++)
		    {
		    	JSONObject serviceJson = serviceArr.getJSONObject(i);
		    	String redisKey = null;
				Map<String, String> reprcSuppFaresMap = null;
		    	try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
		    	 redisKey = sessionId.concat("|").concat(PRODUCT_BUS).concat("|").concat("seatmap");
				 reprcSuppFaresMap = redisConn.hgetAll(redisKey);
		    	
					if (reprcSuppFaresMap == null) {
						logger.error(String.format("supplier Commercials not found,for %s", redisKey));
//						throw new Exception(String.format("GetLayout context not found,for %s", redisKey));
					}
					else
					{
						redisConn.hmset(redisKey, reprcSuppFaresMap);
						redisConn.pexpire(redisKey, (long) (BusConfig.getRedisTTLMinutes() * 60 * 1000));
					}
					
		    	
//				redisKey = resHeaderJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT_BUS).concat("|").concat("clientComm");	
//				Map<String, String> clientCommMap = redisConn.hgetAll(redisKey);
//				if (clientCommMap == null) {
//					logger.error(String.format("client Commercials not found,for %s", redisKey));
////					throw new Exception(String.format("GetLayout context not found,for %s", redisKey));
//				}
//				else
//				{
//					redisConn.hmset(redisKey, clientCommMap);
//					redisConn.pexpire(redisKey, (long) (BusConfig.getRedisTTLMinutes() * 60 * 1000));
//				}
				
		    	
		    	
		    	JSONArray passArr =serviceJson.getJSONArray("passengers");
		    	 String mapKey = getMapKey(serviceJson,passArr);
		    	if(blockTktArr.getJSONObject(i).has("errorMessage"))
		    	{
		    		 serviceJson = new JSONObject();
		    		 serviceJson.put("errorMessage",blockTktArr.getJSONObject(i).getString("errorMessage"));
		    	}
		    	else
		    	{
		    		
			    	 if((resJson.getJSONObject("responseBody").getJSONArray("blockTicket").getJSONObject(i).optString("blockingId"))!=null)
			    	 {
			    		 serviceJson.put("holdKey", resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray("blockTicket").getJSONObject(i).optString("blockingId"));
			    	 }
			    	 else
			    	 {
			    		 serviceJson = new JSONObject();
			    		 serviceJson.put("errorMessage","Requested seats are already booked or not present in seatLayout. Please try again with diffrent seat numbers.");
			    		 logger.error("Requested seats are already booked or not present in seatLayout. Please try diffrent seat numbers.");
			    	 }
			    	
			    	
		    	}
		    	reprcSuppFaresMap.put(mapKey, serviceJson.toString());
		    	 redisKey = sessionId.concat("|").concat(PRODUCT_BUS).concat("|").concat("seatmap");
		    	 redisConn.hmset(redisKey, reprcSuppFaresMap);
		    	 redisConn.pexpire(redisKey, (long) (BusConfig.getRedisTTLMinutes() * 60 * 1000));
		    	// RedisConfig.releaseRedisConnectionToPool(redisConn);
		    	}
	    	 
		}
		    	
		}
		catch(Exception e)
		{
			logger.error("exception in BusSeatBlocking|pushToRedis method ");;
			e.printStackTrace();
		}
	}

	public static String getMapKey(JSONObject serviceJson,JSONArray passJsonArr) {

        StringBuilder mapKey = new StringBuilder();
        mapKey.append("SeatBlock");
   	    mapKey.append("|");
        mapKey.append(serviceJson.get(JSON_PROP_SUPPREF));
   	    mapKey.append("|");
   	    mapKey.append(serviceJson.get(JSON_PROP_JOURNEYDATE));
   	    mapKey.append("|");
   	    mapKey.append(serviceJson.get(JSON_PROP_SERVICEID));
	    mapKey.append("|");
   	    for(int i=0;i<passJsonArr.length();i++)
   	    {
   	    	JSONObject passJson = passJsonArr.getJSONObject(i);
   	    	mapKey.append(passJson.get(JSON_PROP_SEATNO));
    	    mapKey.append("|");
   	    }
		return mapKey.toString();
	}

	private static void getBlockJSON(Element resBodyElem, JSONArray blockTktJsonArr) {
		 JSONObject blockTktJson = new JSONObject();
		if(XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:Errors")!=null)
    	{
    		Element errorMessage=XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:Errors/ota:Error");
    		String errMsgStr=XMLUtils.getValueAtXPath(errorMessage, "./@ShortText");
    		blockTktJson.put("errorMessage", errMsgStr);
    	}
		else
		{
			blockTktJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath((Element)resBodyElem.getParentNode(), "./bus:SupplierID"));
			blockTktJson.put("status", XMLUtils.getValueAtXPath(resBodyElem, "./ota:Status"));
			blockTktJson.put("blockingId", XMLUtils.getValueAtXPath(resBodyElem, "./ota:BlockingId"));
		}
		blockTktJsonArr.put(blockTktJson);
	}

}
