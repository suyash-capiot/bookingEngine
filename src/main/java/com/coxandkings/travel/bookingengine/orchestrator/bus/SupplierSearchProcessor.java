package com.coxandkings.travel.bookingengine.orchestrator.bus;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.bus.BusConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackableExecutor;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class SupplierSearchProcessor extends TrackableExecutor implements BusConstants, Runnable{

	private static final Logger logger = LogManager.getLogger(SupplierSearchProcessor.class);
	private BusSearchListenerResponder mSearchListener;
	private JSONArray mProdSuppsJsonArr;
	
	SupplierSearchProcessor(BusSearchListenerResponder searchListener, ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		mSearchListener = searchListener;
		mProdSupplier = prodSupplier;
		mReqElem = (Element) reqElem.cloneNode(true);
		mReqJson = reqJson;
		mProdSuppsJsonArr = Utils.convertProdSuppliersToCommercialCache(mProdSupplier);
	}
	
	public void process(ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {

    	//OperationConfig opConfig = BusConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
		ServiceConfig opConfig = BusConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
        JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
        
		Document ownerDoc = reqElem.getOwnerDocument();
        Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./bus:RequestHeader/com:SupplierCredentialsList");
        suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, 1));
        
        Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./bus:RequestHeader/com:TransactionID");
        transactionElem.setTextContent(transactionElem.getTextContent().concat(String.format("-%s", prodSupplier.getSupplierID())));

        Element resElem = null;
        
      //-----------------------------------------------------------------------------------
        // Search in commercial cache first. If found, send response to async search listener
        // for further processing and then return.
        try { 
        	ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
		   	String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT_BUS, clInfo[clInfo.length - 1].getCommercialsEntityId(), mProdSuppsJsonArr, reqJson);
		    if (commCacheRes != null && (commCacheRes.equals("error")) == false) {
		    	JSONObject cachedResJson = new JSONObject(new JSONTokener(commCacheRes));
		    	mSearchListener.receiveSupplierResponse(cachedResJson);
		    	return;
		    }
        }
        catch (Exception x) {
        	logger.info("An exception was received while retrieving search results from commercial cache", x);
        }
        try
        {
        	
	        //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), BusConfig.getHttpHeaders(), reqElem);
        	resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
	        // TODO: Revisit this. Should the code really throw an exception? Or just log the problem and send an empty response back? 
	        if (resElem == null) {
	        	throw new Exception("Null response received from SI");
	        }
	        
	        JSONObject resBodyJson = new JSONObject();
			JSONObject availJson = new JSONObject();
			JSONArray availabilityJsonArr = new JSONArray();
			JSONArray serviceArr = new JSONArray();
	        Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem,
					"./busi:ResponseBody/bus:OTA_VehAvailRateRS2Wrapper");
			for (Element wrapperElem : wrapperElems) {
				Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_VehAvailRateRS2");
				BusSearchProcessor.getSupplierResponseAvailableTripsJSON(resBodyElem, serviceArr,reqHdrJson,reqBodyJson);
			}
			availJson.put(JSON_PROP_SERVICE, serviceArr);
			availabilityJsonArr.put(availJson);
			resBodyJson.put(JSON_PROP_AVAILABILITY, availabilityJsonArr);
			
			JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);
           
            Map<String,Integer> BRMS2SIBusMap = new HashMap<String,Integer>();
            // Call BRMS Supplier Commercials
            JSONObject resSupplierJson =  SupplierCommercials.getSupplierCommercials(CommercialsOperation.Search,reqJson, resJson,BRMS2SIBusMap);
 			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierJson.getString(JSON_PROP_TYPE))) {
 				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", resSupplierJson.toString()));
 				//AirSearchProcessor.getEmptyResponse(reqHdrJson).toString();
 			}
 			
 			JSONObject resClientJson = ClientCommercials.getClientCommercials(reqJson,resSupplierJson);
 			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientJson.getString(JSON_PROP_TYPE))) {
 				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", resClientJson.toString()));
 				//return AirSearchProcessor.getEmptyResponse(reqHdrJson).toString();
 			}
 			
 			BusSearchProcessor.calculatePrices(reqJson, resJson, resClientJson, BRMS2SIBusMap,false);
 			
 			 // Put the search results in commercial cache
            try {
         	   ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
         	   CommercialCacheProcessor.putInCache(PRODUCT_BUS, clInfo[clInfo.length - 1].getCommercialsEntityId(), resJson, reqJson);
            }
            catch (Exception x) {
         	   logger.info("An exception was received while pushing search results in commercial cache", x);
            }
			 
 			mSearchListener.receiveSupplierResponse(resJson);
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        }
        
        
	}

}
