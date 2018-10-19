package com.coxandkings.travel.bookingengine.orchestrator.transfers;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.transfers.TransfersConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;


public class TransfersCancelProcessor implements TransfersConstants {

	private static final Logger logger = LogManager.getLogger(TransfersCancelProcessor.class);

	public static String process(JSONObject reqJson) throws Exception {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;


		try {
			
			KafkaBookProducer cancelProducer = new KafkaBookProducer();
			TrackingContext.setTrackingContext(reqJson);
			opConfig = TransfersConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			usrCtx = UserContext.getUserContextForSession(reqHdrJson);

			//OperationConfig opConfig = TransfersConfig.getOperationConfig("cancel");
			 JSONObject kafkaMsgJson;
			reqElem = createSIRequest(reqJson, reqHdrJson, reqBodyJson, opConfig, usrCtx, cancelProducer);
			
			
			//---------- End of Request Processing ---------------	
			
			Element resElem = null;
			// logger.trace(String.format("SI XML Request = %s",
			// XMLTransformer.toString(reqElem)));
			//resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), TransfersConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
		
			ToDoTaskSubType todoSubType=null;
	         if((reqBodyJson.optString("requestType")!=null)&&(reqBodyJson.getString("requestType").equals("cancel"))) {
	        	  String cancelType=reqBodyJson.getString(JSON_PROP_CANCELTYPE);
		            switch(cancelType) {
		            case "Cancel":todoSubType=ToDoTaskSubType.ORDER;break;
		            //case "PAX":todoSubType=ToDoTaskSubType.PASSENGER;break;
		            default:todoSubType=null;
		            }
	          }
		      OperationsToDoProcessor.callOperationTodo(ToDoTaskName.CANCEL, ToDoTaskPriority.MEDIUM,todoSubType, reqBodyJson.getString(JSON_PROP_ORDERID), reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			
		      
			/*
			 * JSONObject resBodyJson = new JSONObject(); 
			 * JSONArray groundWrapperJsonArray =
			 * new JSONArray(); 
			 * Element[] resWrapperElems =
			 * XMLUtils.getElementsAtXPath(resElem,
			 * "./tran:ResponseBody/tran1:OTA_GroundCancelRSWrapper");
			 *  for (Element resWrapperElem : resWrapperElems) {
			 *   Element resBodyElem =
			 * XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ns:OTA_GroundCancelRQ");
			 * getSupplierResponseGroundServiceJSON(resWrapperElem, groundWrapperJsonArray);
			 * } resBodyJson.put(JSON_PROP_GROUNDWRAPPER, groundWrapperJsonArray);
			 * 
			 * JSONObject resJson = new JSONObject(); resJson.put(JSON_PROP_RESHEADER,
			 * reqHdrJson); resJson.put(JSON_PROP_RESBODY, resBodyJson);
			 * logger.trace(String.format("SI Transformed JSON Response = %s",
			 * resJson.toString()));
			 * 
			 * // Call BRMS Supplier and Client Commercials JSONObject resSupplierJson =
			 * SupplierCommercials.getSupplierCommercialsV2(reqJson, resJson); JSONObject
			 * resClientJson = ClientCommercials.getClientCommercialsV2(resSupplierJson);
			 * //String tripInd = reqBodyJson.getString("tripIndicator");
			 * calculatePrices(reqJson, resJson,resSupplierJson,resClientJson, true ,
			 * usrCtx); pushSuppFaresToRedisAndRemove(resJson);
			 * 
			 * return resJson.toString(); } catch (Exception x) { x.printStackTrace();
			 * return "{\"status\": \"ERROR\"}"; } }
			 */
			JSONObject resBodyJson = new JSONObject();

			// Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem,
			// "./busi:ResponseBody/bus:OTA_BusCancelTicketRSWrapper");
		/*	Element[] resWrapperElems = SeatMapProcessor.sortWrapperElementsBySequence(
					XMLUtils.getElementsAtXPath(resElem, "./busi:ResponseBody/bus:OTA_BusCancelTicketRSWrapper"));
				*/	
			
			Element resWrapperElems = XMLUtils.getFirstElementAtXPath(resElem,
			  "./tran:ResponseBody/tran1:OTA_GroundCancelRSWrapper");
			
			Element ReservationElems = XMLUtils.getFirstElementAtXPath(resWrapperElems,
					"./ota:OTA_GroundCancelRS/ota:Reservation");

			Element ErrorElem = XMLUtils.getFirstElementAtXPath(resWrapperElems,
					"./ota:OTA_GroundCancelRS/ota:Errors");
			
			//TODO: find supplierRef
			String suppId = XMLUtils.getValueAtXPath(resWrapperElems, "./tran1:SupplierID");
			JSONObject cancelJson = new JSONObject();
			cancelJson.put(JSON_PROP_TRAN_CANCEL_STATUS, XMLUtils.getValueAtXPath(ReservationElems,"./@Status"));
			cancelJson.put("errorMsg", XMLUtils.getValueAtXPath(ErrorElem,"./ota:Error/@ShortText"));
		
				cancelJson.put(JSON_PROP_SUPPREF, suppId);
			
		
			
		/*	Element cancelResElem = XMLUtils.getFirstElementAtXPath(resWrapperElems, "./tran1:OTA_GroundCancelRS");
			
			JSONObject statusJSON = new JSONObject();

			for (Element wrapperElement : resWrapperElems) {
				Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_GroundCancelRS");
				getSupplierResponseCancelJSON(wrapperElement, getCancelTktJson, false, 0);
				
			}*/
			resBodyJson.put("cancelResponse",cancelJson );
			
			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
			kafkaMsgJson = new JSONObject(resJson.toString());
			  kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("product", reqBodyJson.get("product"));
		       // kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("type", reqBodyJson.get("type"));
		        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("requestType", reqBodyJson.get("requestType"));
		        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("cancelType", reqBodyJson.get("cancelType"));
		        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("entityName", reqBodyJson.get("entityName"));
		        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("orderID", reqBodyJson.get("orderID"));
		        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("type", reqBodyJson.get("type"));
		        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("bookID", reqBodyJson.get("bookID"));
		        JSONArray entityIdArr = reqBodyJson.optJSONArray("entityIds");
		        for(int i = 0 ;i < entityIdArr.length();i++) {
		        	JSONObject entityObj = entityIdArr.optJSONObject(i);
		        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("entityId", entityObj.optString("entityId"));
		        }
		       // System.out.println("KAfkaRQMsg->"+kafkaMsgJson);
				cancelProducer.runProducer(1, kafkaMsgJson);
			
			   int index=0;
	         
	            	if(XMLUtils.getFirstElementAtXPath(resWrapperElems, "./trans:ErrorList")!=null)
	            	{
	            		JSONObject cancelReq=reqBodyJson.getJSONArray(JSON_PROP_SERVICE).getJSONObject(index);
	            		Element errorMessage=XMLUtils.getFirstElementAtXPath(resWrapperElems, "./trans:ErrorList/com:Error/com:ErrorCode");
	            		
	            		String errMsgStr=errorMessage.getTextContent().toString();
	            		if(CANCEL_SI_ERR.equalsIgnoreCase(errMsgStr))
	            		{	
	            			logger.error("This service is not supported. Kindly contact our operations team for support.");
	            			return getSIErrorResponse(resJson).toString();
	            		
	            	}
	            }

			return resJson.toString();

		}
	          catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}
	//private static Element createSIRequest(JSONObject reqJson, JSONObject reqHdrJson, JSONObject reqBodyJson, OperationConfig opConfig, UserContext usrCtx, KafkaBookProducer cancelProducer) throws Exception {
	private static Element createSIRequest(JSONObject reqJson, JSONObject reqHdrJson, JSONObject reqBodyJson, ServiceConfig opConfig, UserContext usrCtx, KafkaBookProducer cancelProducer) throws Exception {
		Element reqElem;
		reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();

		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./tran:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./tran1:OTA_GroundCancelRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		// System.out.println(XMLTransformer.toString(reqBodyElem));
		TrackingContext.setTrackingContext(reqJson);
		//JSONObject kafkaMsgJson = reqJson;

		//TrackingContext.setTrackingContext(reqJson);
		//UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);

		List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT,
				PROD_CATEG_SUBTYPE_TRANSFER);
		TransfersSearchProcessor.createHeader(reqHdrJson, reqElem);

		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
				"./tran1:RequestHeader/com:SupplierCredentialsList");
		for (ProductSupplier prodSupplier : prodSuppliers) {
			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
		}
		/*
		 * int sequence = 1; Element suppCredsListElem =
		 * XMLUtils.getFirstElementAtXPath(reqElem,
		 * "./air:RequestHeader/com:SupplierCredentialsList"); for (ProductSupplier
		 * prodSupplier : prodSuppliers) {
		 * suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, sequence++));
		 * }
		 */

		// Element passengersElem = null;

		String suppID = reqBodyJson.getString(JSON_PROP_SUPPREF);
		Element suppWrapperElem = null;
		suppWrapperElem = (Element) wrapperElem.cloneNode(true);
		reqBodyElem.appendChild(suppWrapperElem);
		if (prodSuppliers == null) {
			throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
		}

		Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./tran1:SupplierID");
		suppIDElem.setTextContent(suppID);

		Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./tran1:Sequence");
		sequenceElem.setTextContent("1");

		Element posElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ns:OTA_GroundCancelRQ/ns:POS");
		Element ground = (Element) posElem.getParentNode();
		Element sourceElem = XMLUtils.getFirstElementAtXPath(posElem, "./ns:Source");
		// TODO: hardcode for ISOCurrency!get it from where?
		sourceElem.setAttribute("ISOCurrency", reqBodyJson.getString("currencyCode"));

		Element reservationElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Reservation");
		reservationElem.setAttribute("CancelType", reqBodyJson.getString("cancelType"));
		ground.appendChild(reservationElem);

		//JSONArray uniqueArr = reqBodyJson.getJSONArray("uniqueID");
		/*for (int i = 0; i < uniqueArr.length(); i++) {
			JSONObject uniqueJson = uniqueArr.getJSONObject(i);
			Element uniqueElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:UniqueID");
			uniqueElem.setAttribute("ID", uniqueJson.getString("id"));
			uniqueElem.setAttribute("ID_Context", uniqueJson.getString("id_Context"));
			uniqueElem.setAttribute("Type", uniqueJson.getString("type"));

			reservationElem.appendChild(uniqueElem);

		}*/
		JSONObject reservationIdObj = reqBodyJson.getJSONObject(JSON_PROP_RESERVATIONIDS);
		Element uniqueElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:UniqueID");
		uniqueElem.setAttribute("ID",reservationIdObj.optString(JSON_PROP_RESERVATIONID));
		//uniqueElem.setAttribute("ID",reservationIdObj.optString(JSON_PROP_RESERVATIONID));
		//uniqueElem.setAttribute("Type","14");
		uniqueElem.setAttribute("ID_Context",reservationIdObj.optString(JSON_PROP_ID_CONTEXT));
		JSONArray referenceArr = reservationIdObj.optJSONArray(JSON_PROP_REFERENCES);
		for(int i = 0 ; i < referenceArr.length() ; i++) {
			JSONObject referenceObj = referenceArr.optJSONObject(i);
			Element uniqueElem1 = ownerDoc.createElementNS(Constants.NS_OTA, "ns:UniqueID");
			uniqueElem1.setAttribute("ID", referenceObj.optString(JSON_PROP_REFERENCEID));
			//uniqueElem1.setAttribute("Type", "16");
			uniqueElem1.setAttribute("ID_Context",referenceObj.optString(JSON_PROP_ID_CONTEXT));
			//reservationElem.appendChild(uniqueElem);
			reservationElem.appendChild(uniqueElem1);
		}
		reservationElem.appendChild(uniqueElem);
		ground.appendChild(reservationElem);

		/*
		 * Element companyNameElem = ownerDoc.createElementNS(Constants.NS_OTA,
		 * "ns:CompanyName"); uniqueElem.appendChild(companyNameElem);
		 * 
		 * Element tpa_ExtensionsElem = ownerDoc.createElementNS(Constants.NS_OTA,
		 * "ns:TPA_Extensions"); Element commentsElem =
		 * ownerDoc.createElementNS(Constants.NS_OTA,"ns:Comments");
		 * tpa_ExtensionsElem.appendChild(commentsElem); Element commentElem =
		 * ownerDoc.createElementNS(Constants.NS_OTA, "ns:Comment");
		 * commentsElem.appendChild(commentElem);
		 * uniqueElem.appendChild(tpa_ExtensionsElem);
		 * 
		 * JSONObject verificationJson = reqBodyJson.getJSONObject("verification");
		 * Element verificationElem = ownerDoc.createElementNS(Constants.NS_OTA,
		 * "ns:Verification"); Element associatedQuantityElem =
		 * ownerDoc.createElementNS(Constants.NS_OTA,"ns:AssociatedQuantity");
		 * verificationElem.appendChild(associatedQuantityElem);
		 * associatedQuantityElem.setAttribute("Code",
		 * verificationJson.getString("associatedQuantityCode")); Element startLocation
		 * = ownerDoc.createElementNS(Constants.NS_OTA, "ns:StartLocation");
		 * startLocation.setAttribute("Latitude",
		 * verificationJson.getString("startLocationLatitude"));
		 * startLocation.setAttribute("Longitude",
		 * verificationJson.getString("startLocationLongitude"));
		 * verificationElem.appendChild(startLocation);
		 * ground.appendChild(verificationElem);
		 */
		
		JSONObject kafkaMsgJson = new JSONObject(reqJson.toString());
		//System.out.println("KAfkaRQMsg->"+kafkaMsgJson);
		cancelProducer.runProducer(1, kafkaMsgJson);
		return reqElem;
	}
	
	
	private static String getSubTypeIdType(String cancelType) {
		if(cancelType.equals(CANCEL_TYPE_PAX))
		{
			return "PASSENGER";
		}
		else
		return "";
	}
	
private static Object getSIErrorResponse(JSONObject resJson) {
		
		JSONObject errorMessage=new JSONObject();
		
		errorMessage.put("errorMessage", "This service is not supported. Kindly contact our operations team for support.");
		 
		resJson.put(JSON_PROP_RESBODY, errorMessage);
        
		return resJson;
	}

	/*public static void pushSuppFaresToRedisAndRemove(JSONObject resJson) {
		JSONObject resHeaderJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);

		JSONArray groundServiceJsonArr = resBodyJson.optJSONArray(JSON_PROP_GROUNDSERVICES);

		Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();
		for (int i = 0; i < groundServiceJsonArr.length(); i++) {
			JSONObject groundServiceJson = groundServiceJsonArr.getJSONObject(i);
			 JSONObject suppPriceInfoJson = new JSONObject(); 
			JSONObject suppPriceInfoJson = groundServiceJson.optJSONObject(JSON_PROP_SUPPPRICEINFO);
			groundServiceJson.remove(JSON_PROP_SUPPPRICEINFO);

			JSONObject totalPricingInfo = groundServiceJson.optJSONObject(JSON_PROP_CLIENTCOMMTOTAL);
			groundServiceJson.remove(JSON_PROP_CLIENTCOMMTOTAL);

			// supplier commercial
			
			 * groundServiceJson.optJSONArray(JSON_PROP_SUPPPRICEINFO);
			 * groundServiceJson.remove(JSON_PROP_SUPPPRICEINFO); //client Commercial
			 * groundServiceJson.optJSONArray(JSON_PROP_CLIENTCOMMTOTAL);
			 * groundServiceJson.remove(JSON_PROP_CLIENTCOMMTOTAL);
			 * 
			 * groundServiceJson.remove(JSON_PROP_BOOKREFIDX);
			 

			if (suppPriceInfoJson == null) {
				// TODO: This should never happen. Log a warning message here.
				continue;
			}

			
			 * //getting client commercial JSONArray clientCommercialItinInfoJsonArr =
			 * groundServiceJson.optJSONArray(JSON_PROP_CLIENTCOMMITININFO);
			 * groundServiceJson.remove(JSON_PROP_CLIENTCOMMITININFO);
			 * 
			 * JSONArray clientCommercialItinTotalJsonArr =
			 * groundServiceJson.optJSONArray(JSON_PROP_CLIENTCOMMTOTAL);
			 * groundServiceJson.remove(JSON_PROP_CLIENTCOMMTOTAL);
			 * 
			 * if ( suppPriceInfoJson == null) { // TODO: This should never happen. Log a
			 * warning message here. continue; }
			 
			
			 * JSONArray clientCommercialItinTotalJsonArr =
			 * groundServiceJson.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			 * groundServiceJson.remove(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			 * 
			 * JSONObject suppPriceBookInfoJson = new JSONObject();
			 * suppPriceBookInfoJson.put(JSON_PROP_SUPPPRICEINFO, suppPriceInfoJson);
			 * //suppPriceBookInfoJson.put(JSON_PROP_BOOKREFS, bookRefJson);
			 * suppPriceBookInfoJson.put(JSON_PROP_CLIENTCOMMITININFO,
			 * clientCommercialItinInfoJsonArr);
			 * suppPriceBookInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS,
			 * clientCommercialItinTotalJsonArr);
			 

			
			 * JSONObject JSON_PROP_SUPPPRICEINFO = new JSONObject();
			 * JSON_PROP_SUPPPRICEINFO.put(JSON_PROP_SUPPPRICEINFO,
			 * JSON_PROP_SUPPPRICEINFO);
			 
			
			 * suppPriceBookInfoJson.put(JSON_PROP_SUPPPRICEINFO, suppPriceInfoJson);
			 * //suppPriceBookInfoJson.put(JSON_PROP_BOOKREFS, bookRefJson);
			 * suppPriceBookInfoJson.put(JSON_PROP_CLIENTCOMMITININFO,
			 * clientCommercialItinInfoJsonArr);
			 * suppPriceBookInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS,
			 * clientCommercialItinTotalJsonArr);
			 
			JSONObject suppPriceBookInfoJson = new JSONObject();
			suppPriceBookInfoJson.put(JSON_PROP_SUPPPRICEINFO, suppPriceInfoJson);
			suppPriceBookInfoJson.put(JSON_PROP_CLIENTCOMMTOTAL, totalPricingInfo);
			reprcSuppFaresMap.put(getRedisKeyForGroundService(groundServiceJson), suppPriceBookInfoJson.toString());
		}

		String redisKey = resHeaderJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT_TRANSFERS);
		Jedis redisConn = RedisConfig.getRedisConnectionFromPool();
		redisConn.hmset(redisKey, reprcSuppFaresMap);
		redisConn.pexpire(redisKey, (long) (TransfersConfig.getRedisTTLMinutes() * 60 * 1000));
		RedisConfig.releaseRedisConnectionToPool(redisConn);
	}*/

	/*public static String getRedisKeyForGroundService(JSONObject groundServiceJson) {
		List<String> keys = new ArrayList<>();

		keys.add(groundServiceJson.optString(JSON_PROP_SUPPREF));
		JSONArray serviceJsonArr = groundServiceJson.getJSONArray(JSON_PROP_SERVICE);
		for (int i = 0; i < serviceJsonArr.length(); i++) {
			JSONObject serviceJson = serviceJsonArr.getJSONObject(i);
			keys.add(serviceJson.optString("maximumPassengers"));
			keys.add(serviceJson.optString("description"));
			keys.add(serviceJson.optString("uniqueID"));

		}

		String key = keys.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining("|"));
		return key;
	}*/


	
}
