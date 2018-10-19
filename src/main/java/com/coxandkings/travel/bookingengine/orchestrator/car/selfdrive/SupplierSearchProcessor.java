package com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive;

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
import com.coxandkings.travel.bookingengine.config.car.CarConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
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

public class SupplierSearchProcessor extends TrackableExecutor implements CarConstants {

	private static final Logger logger = LogManager.getLogger(SupplierSearchProcessor.class);
	private CarSearchListenerResponder mSearchListener;
	private JSONArray mProdSuppsJsonArr;
	
	SupplierSearchProcessor(CarSearchListenerResponder searchListener, ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		mSearchListener = searchListener;
		mProdSupplier = prodSupplier;
		mReqJson = reqJson;
		mReqElem = (Element) reqElem.cloneNode(true);
		mProdSuppsJsonArr =	Utils.convertProdSuppliersToCommercialCache(mProdSupplier);
	}
	
	public void process(ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		//OperationConfig opConfig = CarConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
		ServiceConfig opConfig = CarConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
        JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
        
		Document ownerDoc = reqElem.getOwnerDocument();
        Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestHeader/com:SupplierCredentialsList");
        suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, 1));
        
        Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestHeader/com:TransactionID");
        transactionElem.setTextContent(transactionElem.getTextContent().concat(String.format("-%s", prodSupplier.getSupplierID())));
        
        //-----------------------------------------------------------------------------------
        // Search in commercial cache first. If found, send response to async search listener
        // for further processing and then return.
        if(CarConfig.ismEnableCacheSelfDrive()) {
			try { 
	        	ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
			   	String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT_CAR, clInfo[clInfo.length - 1].getCommercialsEntityId(), mProdSuppsJsonArr, reqJson);
			    if (commCacheRes != null && (commCacheRes.equals("error")) == false) {
			    	JSONObject cachedResJson = new JSONObject(new JSONTokener(commCacheRes));
			    	mSearchListener.receiveSupplierResponse(cachedResJson);
			    	return;
			    }
	        }
	        catch (Exception x) {
	        	logger.info("An exception was received while retrieving search results from commercial cache", x);
	        }
        }
		
        Element resElem = null;
        try {
    		JSONObject carRentalReq = reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);
    		
    		//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), CarConfig.getHttpHeaders(), reqElem);
    		resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			JSONObject resBodyJson = new JSONObject();
			JSONArray vehicleAvailJsonArr = new JSONArray();
			Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem,
					"./cari:ResponseBody/car:OTA_VehAvailRateRSWrapper");

			JSONArray resCarRentalsArr = new JSONArray();
			for (Element wrapperElem : wrapperElems) {
				Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_VehAvailRateRS/ota:VehAvailRSCore");
				CarSearchProcessor.getSupplierResponseVehicleAvailJSON(resBodyElem, vehicleAvailJsonArr);

			}
			for (int i = 0; i < vehicleAvailJsonArr.length(); i++) {
				JSONObject vehicleAvail = vehicleAvailJsonArr.getJSONObject(i);
				vehicleAvail.put(JSON_PROP_CITY, carRentalReq.getString(JSON_PROP_CITY));
			}
			resCarRentalsArr.put(new JSONObject().put(JSON_PROP_VEHICLEAVAIL, vehicleAvailJsonArr));
			resBodyJson.put(JSON_PROP_CARRENTALARR, resCarRentalsArr);

            JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);
        	logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
        	Map<String, Integer> suppResToBRIIndex = new HashMap<String, Integer>();
        	
           JSONObject resSupplierJson =  SupplierCommercials.getSupplierCommercials(CommercialsOperation.Search, reqJson, resJson, suppResToBRIIndex);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", resSupplierJson.toString()));
			}
           
           JSONObject resClientJson = ClientCommercials.getClientCommercialsV2(reqJson, resJson, resSupplierJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", resClientJson.toString()));
			}
			
			
			CarSearchProcessor.calculatePricesV2(reqJson, resJson, resSupplierJson, resClientJson, suppResToBRIIndex, false, UserContext.getUserContextForSession(reqHdrJson),false);
            
			// Apply company offers
	        CompanyOffers.getCompanyOffers(reqJson, resJson, OffersType.COMPANY_SEARCH_TIME);
	        
	        if(CarConfig.ismEnableCacheSelfDrive()) {
				try {
	        	   ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
	        	   CommercialCacheProcessor.putInCache(PRODUCT_CAR, clInfo[clInfo.length - 1].getCommercialsEntityId(), resJson, reqJson);
				}
	            catch (Exception x) {
	        	   logger.info("An exception was received while pushing search results in commercial cache", x);
	            }
	        }
			mSearchListener.receiveSupplierResponse(resJson);
        }
        catch (Exception x) {
        	x.printStackTrace();
        }
	}
	
}
