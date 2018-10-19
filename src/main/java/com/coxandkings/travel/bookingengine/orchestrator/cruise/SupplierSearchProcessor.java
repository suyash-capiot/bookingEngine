package com.coxandkings.travel.bookingengine.orchestrator.cruise;

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
import com.coxandkings.travel.bookingengine.config.cruise.CruiseConfig;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackableExecutor;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class SupplierSearchProcessor extends TrackableExecutor implements CruiseConstants, Runnable {

	private static final Logger logger = LogManager.getLogger(SupplierSearchProcessor.class);
	private CruiseSearchListenerResponder mSearchListener;
	private JSONArray mProdSuppsJsonArr;

	SupplierSearchProcessor(CruiseSearchListenerResponder searchListener, ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		mSearchListener = searchListener;
		mProdSupplier = prodSupplier;
		mReqJson = reqJson;
		mReqElem = (Element) reqElem.cloneNode(true);
		mProdSuppsJsonArr = Utils.convertProdSuppliersToCommercialCache(mProdSupplier);
	}
	
	public void process(ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		logger.trace("Inside process of supplierSearch");
		ServiceConfig opConfig=null; JSONObject reqHdrJson=null;
		//OperationConfig opConfig = CruiseConfig.getOperationConfig("search");
		try{
		 opConfig = CruiseConfig.getOperationConfig("search");
		 reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        
        Document ownerDoc = reqElem.getOwnerDocument();
        Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru1:RequestHeader/com:SupplierCredentialsList");
        suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, 1));
     
        Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru1:RequestHeader/com:TransactionID");
        transactionElem.setTextContent(transactionElem.getTextContent().concat(String.format("-%s", prodSupplier.getSupplierID())));
        
        JSONArray suppliers = new JSONArray("[{\"supplierID\": \"STAR\"}]");
		String commCacheRes =CommercialCacheProcessor.getFromCache(PRODUCT_CRUISE, "B2CIndiaEng", suppliers, reqJson);
        if(commCacheRes!=null && !(commCacheRes.equals("error"))){
        	JSONObject cachedResJson = new JSONObject(new JSONTokener(commCacheRes));
        	mSearchListener.receiveSupplierResponse(cachedResJson);
        	return;
        }
        
        /*try { 
        	ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
		   	String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT_CRUISE, clInfo[clInfo.length - 1].getCommercialsEntityId(), mProdSuppsJsonArr, reqJson);
		    if (commCacheRes != null && (commCacheRes.equals("error")) == false) {
		    	JSONObject cachedResJson = new JSONObject(new JSONTokener(commCacheRes));
		    	mSearchListener.receiveSupplierResponse(cachedResJson);
		    	return;
		    }
        }
        catch (Exception x) {
        	logger.info("An exception was received while retrieving search results from commercial cache", x);
        }*/
		}
		catch(Exception e) {
			
			logger.error("error inside supplierSearch" +e);
		}
        Element resElem = null;
        try {
        	TrackingContext.setTrackingContext(reqJson);
	        //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), CruiseConfig.getHttpHeaders(), reqElem);
        	resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
	        if (resElem == null) {
	        	throw new Exception("Null response received from SI");
	        }
	        JSONObject resBodyJson = new JSONObject();
	        JSONArray sailingOptionJsonArr = new JSONArray();
//	        System.out.println(XMLTransformer.toString(resElem));
	        Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem, "./cru:ResponseBody/cru1:OTA_CruiseSailAvailRSWrapper");
	        
	        for(Element wrapperElem : wrapperElems)
	        {
	        	//IF SI gives an error
	        	Element[] errorListElems = XMLUtils.getElementsAtXPath(wrapperElem, "./ota:OTA_CruiseSailAvailRS/ota:Errors/ota:Error");
	        	if(errorListElems!=null && errorListElems.length!=0)
	        	{
	        		int errorInt=1;
	        		for(Element errorListElem : errorListElems)
	        		{
	        			logger.error(String.format("An error response was received from SI for supplier %s :Error %s : %s",XMLUtils.getValueAtXPath(wrapperElem, "/cru1:SupplierID"), errorInt,XMLUtils.getValueAtXPath(errorListElem, "/ota:Error")));
	        			errorInt++;
	        		}
	        		continue;
	        	}
	        	CruiseSearchProcessor.getSupplierResponseSailingOptionsJSON(wrapperElem,sailingOptionJsonArr);
	        }
	        
	        resBodyJson.put("cruiseOptions", sailingOptionJsonArr);
	        
	        System.out.println(resBodyJson.toString());
	        
	        JSONObject resJson = new JSONObject();
	        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
	        resJson.put(JSON_PROP_RESBODY, resBodyJson);
	        
	        System.out.println(resJson.toString());
	        
	        Map<String,String> SI2BRMSSailingOptionMap = new HashMap<String,String>();
	        JSONObject parentSupplTransJson = CruiseSupplierCommercials.getSupplierCommercialsV2(reqJson,resJson,SI2BRMSSailingOptionMap);
	        if (BRMS_STATUS_TYPE_FAILURE.equals(parentSupplTransJson.getJSONObject(PRODUCT_CRUISE_SUPPLTRANSRS).getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", parentSupplTransJson.toString()));
			}
	        	
	        
	        JSONObject breResClientJson = CruiseClientCommercials.getClientCommercialsV1(parentSupplTransJson);
	        if (BRMS_STATUS_TYPE_FAILURE.equals(breResClientJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", breResClientJson.toString()));
			}
	        
	        CruiseSearchProcessor.calculatePricesV6(reqJson, resJson, parentSupplTransJson, breResClientJson, SI2BRMSSailingOptionMap, false, UserContext.getUserContextForSession(reqHdrJson));
	        
	        try {
	        	CommercialCacheProcessor.putInCache(PRODUCT_CRUISE, "B2CIndiaEng", resJson, reqJson);
        	}
        	catch(Exception e)
        	{
        		e.printStackTrace();
        	}
	        /*try {
        	   ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
        	   CommercialCacheProcessor.putInCache(PRODUCT_CRUISE, clInfo[clInfo.length - 1].getCommercialsEntityId(), reqJson, resJson);
            }
            catch (Exception x) {
        	   logger.info("An exception was received while pushing search results in commercial cache", x);
            }*/
	        mSearchListener.receiveSupplierResponse(resJson);
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        }
	}
}
