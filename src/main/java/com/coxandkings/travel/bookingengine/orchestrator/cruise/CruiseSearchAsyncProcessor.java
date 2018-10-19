package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ThreadPoolConfig;
import com.coxandkings.travel.bookingengine.config.cruise.CruiseConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class CruiseSearchAsyncProcessor implements CruiseConstants, Runnable {

	private static final Logger logger = LogManager.getLogger(CruiseSearchAsyncProcessor.class);
	private JSONObject mReqJson;
	
	public CruiseSearchAsyncProcessor(JSONObject reqJson) {
		mReqJson = reqJson;
	}
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException, ValidationException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		UserContext usrCtx = null;
		
		try {
			/*JSONArray suppliers = new JSONArray("[{\"supplierID\": \"STAR\"}]");
			String commCacheRes =CommercialCacheProcessor.getFromCache(PRODUCT_CRUISE, "B2CIndiaEng", suppliers, reqJson);
            if(commCacheRes!=null && !(commCacheRes.equals("error")))
            return commCacheRes;*/
			
            //OperationConfig opConfig = CruiseConfig.getOperationConfig("search");
			ServiceConfig opConfig = CruiseConfig.getOperationConfig("search");
            
            reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
            reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
            TrackingContext.setTrackingContext(reqJson);
            
            validateRequestParameters(reqJson);
            usrCtx = UserContext.getUserContextForSession(reqHdrJson);
            reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson);
		}
        catch (ValidationException valx) {
        	throw valx;
        }
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
		try {
			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT,PRODUCT_CRUISE_PRODS);
		    if(prodSuppliers==null || prodSuppliers.size()==0)
            	 OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for Cruise");	
           
			CruiseSearchListenerResponder searchListener = new CruiseSearchListenerResponder(prodSuppliers, reqJson);
	        for (ProductSupplier prodSupplier : prodSuppliers) {
	        	SupplierSearchProcessor suppSrchPrc = new SupplierSearchProcessor(searchListener, prodSupplier, reqJson, reqElem);
	        	ThreadPoolConfig.execute(suppSrchPrc);
	        }
			
	        if (isSynchronousSearchRequest(reqJson)) {
    			// This is a synchronous search request
                // Wait for all supplier threads to finish or till configured timeout
                synchronized(searchListener) {
                	searchListener.wait(CruiseConfig.getAsyncSearchWaitMillis());
                }

    			JSONObject resJson = searchListener.getSearchResponse();
    			return resJson.toString();
    		}
	        
		} catch (Exception x) {
			// TODO: handle exception
			x.printStackTrace();
			logger.error("Exception received while processing", x);
			 throw new InternalProcessingException(x);
		}
		
		return null;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		try {
			process(mReqJson);
		}
		catch (Exception x) {
			  logger.error("An exception was received during asynchornous search processing", x);
		}
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
	private static void validateRequestParameters(JSONObject reqJson) throws ValidationException {
		
	}
	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
		
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		CruiseSearchProcessor.createHeader(reqElem, reqHdrJson);
		
		Element otaCruiseSailAvalElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru:RequestBody/ota:OTA_CruiseSailAvailRQ");
        
        Element Source = XMLUtils.getFirstElementAtXPath(reqElem, "./cru:RequestBody/ota:OTA_CruiseSailAvailRQ/ota:POS/ota:Source");
        Source.setAttribute("ISOCurrency", reqHdrJson.getJSONObject("clientContext").getString("clientCurrency"));
        
        Element RequesterID = ownerDoc.createElementNS(Constants.NS_OTA,"RequestorID");
        RequesterID.setAttribute("Type", "");
        RequesterID.setAttribute("ID", "US");
        
        Source.appendChild(RequesterID);
        
//---------------x-------------------x-------------------x----------------------x---------------------x----------------------------x--------------------x-------------------------x-----------------------x-
        
        JSONArray guestsReqJsonArr = reqBodyJson.getJSONArray("Guests");
        CruisePriceProcessor.createGuestsAndGuestCounts(ownerDoc, guestsReqJsonArr, otaCruiseSailAvalElem);
        
//---------------x------------------x-----------------x-------------------x----------------------x-------------------x-----------------x-------------------x--------------------x---------------------x----------------------x-
        JSONObject sailingDateRangeJson = reqBodyJson.getJSONObject("sailingDateRange");
        Element sailingDateRangeElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru:RequestBody/ota:OTA_CruiseSailAvailRQ/ota:SailingDateRange");
        Element otareqElem = (Element) sailingDateRangeElem.getParentNode();
        sailingDateRangeElem.setAttribute("End", sailingDateRangeJson.getString("endDate"));
        sailingDateRangeElem.setAttribute("Start", sailingDateRangeJson.getString("startDate"));
        
        Element cruiseLinePrefs = XMLUtils.getFirstElementAtXPath(reqElem,"./cru:RequestBody/ota:OTA_CruiseSailAvailRQ/ota:CruiseLinePrefs");
        JSONArray cruiseLinePrefArr =  reqBodyJson.getJSONArray("cruiseLinePref");
        for(int i=0;i<cruiseLinePrefArr.length();i++)
        {
        	JSONObject cruiseLinePrefJson =  (JSONObject) cruiseLinePrefArr.get(i);
        	Element cruiseLinePrefElem = CruiseSearchProcessor.getCruiseLinePref(ownerDoc, cruiseLinePrefJson);
        	cruiseLinePrefs.appendChild(cruiseLinePrefElem);
        }
        
        Element regionPref = XMLUtils.getFirstElementAtXPath(reqElem,"./cru:RequestBody/ota:OTA_CruiseSailAvailRQ/ota:RegionPref");
        regionPref.setAttribute("RegionCode", reqBodyJson.getJSONObject("regionPref").getString("regionCode"));
		
		return reqElem;
	}
}
