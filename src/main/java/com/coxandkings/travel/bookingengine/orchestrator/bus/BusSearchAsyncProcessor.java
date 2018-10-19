package com.coxandkings.travel.bookingengine.orchestrator.bus;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ThreadPoolConfig;
import com.coxandkings.travel.bookingengine.config.bus.BusConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;



public class BusSearchAsyncProcessor implements BusConstants, Runnable {

	private static final Logger logger = LogManager.getLogger(BusSearchAsyncProcessor.class);
	private JSONObject mReqJson;
	
	public BusSearchAsyncProcessor(JSONObject reqJson) {
		mReqJson = reqJson;
	}
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException, ValidationException
	{
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		UserContext usrCtx = null;
		try
		{

            
			//OperationConfig opConfig = BusConfig.getOperationConfig("search");
			ServiceConfig opConfig = BusConfig.getOperationConfig("search");
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
            reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
            TrackingContext.setTrackingContext(reqJson);
            
            validateRequestParameters(reqJson);
            usrCtx = UserContext.getUserContextForSession(reqHdrJson);
            reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			logger.error("Exception during request processing", e);
			throw new RequestProcessingException(e);
		}
		
		try
		{
			  List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_BUS);
			  
			  if(prodSuppliers==null || prodSuppliers.size()==0)
	            	OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for Bus");	
	           
			  BusSearchListenerResponder searchListener = new BusSearchListenerResponder(prodSuppliers, reqJson);
			  for (ProductSupplier prodSupplier : prodSuppliers) {
	                SupplierSearchProcessor suppSrchPrc = new SupplierSearchProcessor(searchListener, prodSupplier, reqJson, reqElem);
	            	ThreadPoolConfig.execute(suppSrchPrc);
	            }
			  
			  if (isSynchronousSearchRequest(reqJson)) {
	    			// This is a synchronous search request
	                // Wait for all supplier threads to finish or till configured timeout
	                synchronized(searchListener) {
	                	searchListener.wait(BusConfig.getmAsyncSearchWaitSecs());
	                }

	    			JSONObject resJson = searchListener.getSearchResponse();
	    			return resJson.toString();
			  }
		}
		catch(Exception e)
		{
			logger.error("Exception received while processing", e);
			  throw new InternalProcessingException(e);
		}
		return null;
		
	}
	private static void validateRequestParameters(JSONObject reqJson) throws ValidationException{

		BusRequestValidator.validateSource(reqJson);
		BusRequestValidator.validateDestination(reqJson);
		BusRequestValidator.validateJourneyDate(reqJson);
		
		
	}

	public static boolean isSynchronousSearchRequest(JSONObject reqJson) {
		boolean dftResult = true;
		
		if (reqJson == null) {
			return dftResult;
		}
		
		JSONObject reqHdrJson = reqJson.optJSONObject(JSON_PROP_REQHEADER);
		if (reqHdrJson == null) {
			return dftResult;
		}
		
		JSONObject clCtxJson = reqHdrJson.optJSONObject(JSON_PROP_CLIENTCONTEXT);
		return (clCtxJson == null || clCtxJson.optString(JSON_PROP_CLIENTCALLBACK).isEmpty());
	}

	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) {

		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true); // xmshell
		Document ownerDoc = reqElem.getOwnerDocument();
		
		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);
		
		
		BusSearchProcessor.createHeader(reqElem, sessionID, transactionID, userID);
		
		XMLUtils.setValueAtXPath(reqElem, "./busi:RequestBody/ota:OTA_VehAvailRateRQ2/ota:sourceStationId",
				reqBodyJson.getString(JSON_PROP_SOURCE));
		XMLUtils.setValueAtXPath(reqElem, "./busi:RequestBody/ota:OTA_VehAvailRateRQ2/ota:destinationStationId",
				reqBodyJson.getString(JSON_PROP_DESTINATION));
		XMLUtils.setValueAtXPath(reqElem, "./busi:RequestBody/ota:OTA_VehAvailRateRQ2/ota:journeyDate",
				reqBodyJson.getString(JSON_PROP_JOURNEYDATE));
		
		return reqElem;
	}

	@Override
	public void run() {
		try {
			process(mReqJson);
		}
		catch (Exception x) {
			  logger.error("An exception was received during asynchornous search processing", x);
		}
		
	}

}
