package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.orchestrator.air.CompanyOffers;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackableExecutor;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class SupplierSearchProcessor extends TrackableExecutor implements HolidayConstants {

  private static final Logger logger = LogManager.getLogger(SupplierSearchProcessor.class);
  private HolidaysSearchListnerResponder mSearchListener;
  private JSONArray mProdSuppsJsonArr;
  private static JSONObject EMPTY_RESJSON = new JSONObject(String.format("{\"%s\": {\"%s\": []}}", JSON_PROP_RESBODY, JSON_PROP_DYNAMICPACKAGE));
  
  SupplierSearchProcessor(HolidaysSearchListnerResponder searchListener, ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
    mSearchListener = searchListener;
    mProdSupplier = prodSupplier;
    mReqJson = reqJson;
    mReqElem = (Element) ((Document)reqElem.getOwnerDocument().cloneNode(true)).importNode(reqElem, true);
    //mReqElem = (Element) reqElem.cloneNode(true);
    mProdSuppsJsonArr = Utils.convertProdSuppliersToCommercialCache(mProdSupplier);
}
  
  protected void process(ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
    //OperationConfig opConfig = HolidaysConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
    //ServiceConfig opConfig = HolidaysConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
    ServiceConfig opConfig = HolidaysConfig.getOperationConfig("search");
    
    JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
    UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
    
    JSONObject resJson = null,resSupplierCommJson = null,resClientCommJson = null;
    
    JSONArray errorJsonArray = new JSONArray();
    
    Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./pac:RequestHeader/com:TransactionID");
    transactionElem.setTextContent(transactionElem.getTextContent().concat(String.format("-%s", prodSupplier.getSupplierID())));

    //-----------------------------------------------------------------------------------
    // Search in commercial cache first. If found, send response to async search listener
    // for further processing and then return.
    /*try { 
        ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
        String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT, clInfo[clInfo.length - 1].getCommercialsEntityId(), mProdSuppsJsonArr, reqJson);
        if (commCacheRes != null && (commCacheRes.equals("error")) == false) {
            JSONObject cachedResJson = new JSONObject(new JSONTokener(commCacheRes));
            mSearchListener.receiveSupplierResponse(cachedResJson);
            return;
        }
    }
    catch (Exception x) {
        logger.info("An exception was received while retrieving search results from commercial cache", x);
    }*/

    //-----------------------------------------------------------------------------------
    // Send request to Supplier Integration (SI)
    Element resElem = null;
    try {
      
        //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AirConfig.getHttpHeaders(), reqElem);
        //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
        resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
        if (resElem == null) {
            logger.error("A null response received from SI for supplier {} and credential {}", mProdSupplier.getSupplierID(), mProdSupplier.getCredentialsName());
            mSearchListener.receiveSupplierResponse(EMPTY_RESJSON);
            return;
        }
        
        //Handle SI Response
        resJson = HolidaysSearchProcessor.convertSIResponse(resElem,reqJson,errorJsonArray);
        
        // Call BRMS Supplier Commercials
        resSupplierCommJson = HolidaysSearchProcessor.applySupplierCommercials(reqJson,resJson,reqHdrJson,errorJsonArray);
        if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierCommJson.getString(JSON_PROP_TYPE))) {
            logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", resSupplierCommJson.toString()));
        }
          
        resClientCommJson = HolidaysSearchProcessor.applyClientCommercials(resSupplierCommJson, reqJson);
        if (BRMS_STATUS_TYPE_FAILURE.equals(resClientCommJson.getString(JSON_PROP_TYPE))) {
            logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", resClientCommJson.toString()));
        }
        
        // Call Calculate Prices
        HolidaysSearchProcessor.calculatePricesV2(reqJson, resJson, resSupplierCommJson, resClientCommJson, UserContext.getUserContextForSession(reqHdrJson));

        // Apply company offers
        HolidaysCompanyOffers.getCompanyOffers(reqJson, resJson, OffersType.COMPANY_SEARCH_TIME,CommercialsOperation.Search);
   
        // Put the search results in commercial cache
        try {
            ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
            CommercialCacheProcessor.putInCache(PRODUCT, clInfo[clInfo.length - 1].getCommercialsEntityId(),resJson,reqJson);
        }
        catch (Exception x) {
            logger.info("An exception was received while pushing search results in commercial cache", x);
        }

        mSearchListener.receiveSupplierResponse(resJson);
     }
     catch (Exception x) {
         logger.error(String.format("An exception was received processing search request for supplier %s and credentials %s", mProdSupplier.getSupplierID(), mProdSupplier.getCredentialsName()), x);
         mSearchListener.receiveSupplierResponse(EMPTY_RESJSON);
     }
}
  
}
