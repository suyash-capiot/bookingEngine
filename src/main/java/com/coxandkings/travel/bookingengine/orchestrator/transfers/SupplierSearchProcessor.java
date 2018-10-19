package com.coxandkings.travel.bookingengine.orchestrator.transfers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.transfers.TransfersConfig;
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

public class SupplierSearchProcessor extends TrackableExecutor implements TransfersConstants {

	private static final Logger logger = LogManager.getLogger(SupplierSearchProcessor.class);
	private TransfersSearchListenerResponder mSearchListener;
	private JSONArray mProdSuppsJsonArr;
	private static JSONObject EMPTY_RESJSON = new JSONObject(String.format("{\"%s\": {\"%s\": []}}", JSON_PROP_RESBODY, JSON_PROP_GROUNDSERVICES));
	
	
	
	SupplierSearchProcessor(TransfersSearchListenerResponder searchListener, ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		mSearchListener = searchListener;
		mProdSupplier = prodSupplier;
		mReqJson = reqJson;
		mReqElem = (Element) reqElem.cloneNode(true);
		mProdSuppsJsonArr = Utils.convertProdSuppliersToCommercialCache(mProdSupplier);
	}
	
	public void process(ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		//OperationConfig opConfig = TransfersConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
		ServiceConfig opConfig = TransfersConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
        JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		
		Document ownerDoc = reqElem.getOwnerDocument();
        Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./tran1:RequestHeader/com:SupplierCredentialsList");
        suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, 1));
        
        Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./tran1:RequestHeader/com:TransactionID");
        transactionElem.setTextContent(transactionElem.getTextContent().concat(String.format("-%s", prodSupplier.getSupplierID())));

        Element resElem = null;
        
        // Search in commercial cache first. If found, send response to async search listener
        // for further processing and then return.
       try { 
        	ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
		   	String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT_TRANSFERS, clInfo[clInfo.length - 1].getCommercialsEntityId(), mProdSuppsJsonArr, reqJson);
		    if (commCacheRes != null && (commCacheRes.equals("error")) == false) {
		    	JSONObject cachedResJson = new JSONObject(new JSONTokener(commCacheRes));
		    	TransfersSearchProcessor.pushCacheFaresToRedisAndRemove(cachedResJson);
		    	mSearchListener.receiveSupplierResponse(cachedResJson);
		    	//JSONObject cachedPriceTotal = cachedResJson.getJSONObject("responseBody").getJSONObject("supplierResponse");
		    	//TransfersSearchProcessor.pushCacheFaresToRedisAndRemove(cachedResJson);
		    	return;
		    	
		    }
        }
        catch (Exception x) {
        	logger.info("An exception was received while retrieving search results from commercial cache", x);
        }
        try {
    		//TrackingContext.setTrackingContext(reqJson);
    		//JSONObject carRentalReq = reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);
    		
    		//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), TransfersConfig.getHttpHeaders(), reqElem);
        	resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			
			JSONObject resBodyJson = new JSONObject();
			JSONArray groundServiceJsonArr = new JSONArray();
			Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem,
					"./tran:ResponseBody/tran1:OTA_GroundAvailRSWrapper");
			for (Element wrapperElem : wrapperElems) {
				Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_GroundAvailRS");
				TransfersSearchProcessor.getSupplierResponseGroundServiceJSON(resBodyElem, groundServiceJsonArr,reqBodyJson);
			}
			
			resBodyJson.put(JSON_PROP_GROUNDSERVICES, groundServiceJsonArr);
			
			JSONArray groundServicesArr = resBodyJson.getJSONArray("groundServices");
			for(int i = 0 ; i < groundServiceJsonArr.length();i++) {
				JSONObject groundServicesObj = groundServiceJsonArr.optJSONObject(i);
				
				JSONArray resServiceArr = groundServicesObj.getJSONArray("service");
				
				for(int j = 0 ; j < resServiceArr.length() ; j++) {
					JSONObject resServiceObj = resServiceArr.optJSONObject(j);
					
					JSONObject reqServiceObj = reqBodyJson.optJSONArray("service").optJSONObject(j);
					
					resServiceObj.put(JSON_PROP_PICKCITY,reqServiceObj.optJSONObject("pickup").optJSONObject("address").optString("cityName"));
					resServiceObj.put(JSON_PROP_DROPCITY, reqServiceObj.optJSONObject("dropoff").optJSONObject("address").optString("cityName"));
				}
				
			}

            JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);
        //	Map<String, Integer> suppResToBRIIndex = new HashMap<String, Integer>();
        	
        	JSONObject resSupplierJson = SupplierCommercials.getSupplierCommercialsV2(CommercialsOperation.Search,reqJson, resJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s",
						resSupplierJson.toString()));
				
			}
			JSONObject resClientJson = ClientCommercials.getClientCommercialsV2(resSupplierJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s",
								resClientJson.toString()));
				
			}
           
			TransfersSearchProcessor.calculatePrices(reqJson, resJson, resSupplierJson, resClientJson, true, UserContext.getUserContextForSession(reqHdrJson));
				// Apply company offers
	           //CompanyOffers.getCompanyOffers(reqJson, resJson, OffersConfig.Type.COMPANY_SEARCH_TIME);
			

			TaxEngine.getCompanyTaxes(reqJson, resJson);
			TransfersSearchProcessor.pushSuppFaresToRedisAndRemove(resJson);	
			 try {
	         	   ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
	         	   CommercialCacheProcessor.putInCache(PRODUCT_TRANSFERS, clInfo[clInfo.length - 1].getCommercialsEntityId(), resJson , reqJson);
	            }
	            catch (Exception x) {
	         	
	            	logger.info("An exception was received while pushing search results in commercial cache", x);
	            	mSearchListener.receiveSupplierResponse(EMPTY_RESJSON);
	            }
				 
			
			
			mSearchListener.receiveSupplierResponse(resJson);
        }
        catch (Exception x) {
        	x.printStackTrace();
        }
	}
	
}
