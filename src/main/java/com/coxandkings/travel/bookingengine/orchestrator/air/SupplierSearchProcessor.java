package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackableExecutor;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
//import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class SupplierSearchProcessor extends TrackableExecutor implements AirConstants {

	private static final Logger logger = LogManager.getLogger(SupplierSearchProcessor.class);
	private AirSearchListenerResponder mSearchListener;
	private JSONArray mProdSuppsJsonArr;
	private static JSONObject EMPTY_RESJSON = new JSONObject(String.format("{\"%s\": {\"%s\": []}}", JSON_PROP_RESBODY, JSON_PROP_PRICEDITIN));
	private Integer cabinTypeJsonArrLength=0;
	
	SupplierSearchProcessor(AirSearchListenerResponder searchListener, ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem, Integer cabinTypeStrJsonArrLength) {
		mSearchListener = searchListener;
		mProdSupplier = prodSupplier;
		mReqJson = reqJson;
		mReqElem = (Element) ((Document)reqElem.getOwnerDocument().cloneNode(true)).importNode(reqElem, true);
		//mReqElem = (Element) reqElem.cloneNode(true);
		mProdSuppsJsonArr = convertProdSuppliersToCommercialCache(mProdSupplier);
		cabinTypeJsonArrLength=cabinTypeStrJsonArrLength;
	}
	
	protected void process(ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		//OperationConfig opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
		ServiceConfig opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
        JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		
		Document ownerDoc = reqElem.getOwnerDocument();
        Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
        suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, 1));
        
        Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:TransactionID");
        transactionElem.setTextContent(transactionElem.getTextContent().concat(String.format("-%s", prodSupplier.getSupplierID())));

        //-----------------------------------------------------------------------------------
        // Search in commercial cache first. If found, send response to async search listener
        // for further processing and then return.
        try { 
        	ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
		   	String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT_AIR, clInfo[clInfo.length - 1].getCommercialsEntityId(), mProdSuppsJsonArr, reqJson);
		    if (commCacheRes != null && (commCacheRes.equals("error")) == false) {
		    	JSONObject cachedResJson = new JSONObject(new JSONTokener(commCacheRes));
		    	cachedResJson=changeCurrencyOfCachedResJson(cachedResJson,reqHdrJson);
		    	//mSearchListener.receiveSupplierResponse(cachedResJson);
		    	mSearchListener.receiveSupplierResponseV2(cachedResJson,cabinTypeJsonArrLength);
		    	return;
		    }
        }
        catch (Exception x) {
        	logger.info("An exception was received while retrieving search results from commercial cache", x);
        }

        //-----------------------------------------------------------------------------------
        // Send request to Supplier Integration (SI)
        Element resElem = null;
        try {
	       
        	
        	//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
        	resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(),  opConfig.getHttpHeaders(), "POST", opConfig.getServiceTimeoutMillis(), reqElem);
	        if (resElem == null) {
	        	logger.error("A null response received from SI for supplier {} and credential {}", mProdSupplier.getSupplierID(), mProdSupplier.getCredentialsName());
	        	mSearchListener.receiveSupplierResponse(EMPTY_RESJSON);
	        	return;
	        }
	        
            JSONObject resBodyJson = new JSONObject();
            JSONArray pricedItinsJsonArr = new JSONArray();
            Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirLowFareSearchRSWrapper");
            for (Element wrapperElem : wrapperElems) {
            	Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_AirLowFareSearchRS");
            	AirSearchProcessor.getSupplierResponsePricedItinerariesJSON(resBodyElem, pricedItinsJsonArr,reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_PAXINFO));
            }
            resBodyJson.put(JSON_PROP_PRICEDITIN, pricedItinsJsonArr);

            JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
            resJson.put(JSON_PROP_RESBODY, resBodyJson);

            //create a List for indexes for which suppComm is created for source supplier
            List<Integer> sourceSupplierCommIndexes=new ArrayList<Integer>();
            
            // Call BRMS Supplier Commercials
           JSONObject resSupplierJson =  SupplierCommercials.getSupplierCommercialsV3(CommercialsOperation.Search, reqJson, resJson,sourceSupplierCommIndexes);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", resSupplierJson.toString()));
				//AirSearchProcessor.getEmptyResponse(reqHdrJson).toString();
			}
           
            //**********************************************************************
            // There are no supplier offers for Air. As communicated by Offers team.
			
           JSONObject resClientJson = ClientCommercials.getClientCommercialsV2(reqJson, resJson, resSupplierJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientJson.getString(JSON_PROP_TYPE))) {
				logger.error("A failure response was received from Client Commercials calculation engine: {}", resClientJson.toString());
				//return AirSearchProcessor.getEmptyResponse(reqHdrJson).toString();
			}
           
			AirSearchProcessor.calculatePricesV5(reqJson, resJson, resSupplierJson, resClientJson, false, UserContext.getUserContextForSession(reqHdrJson),sourceSupplierCommIndexes);
           
           // Apply company offers
			try {
				 CompanyOffers.getCompanyOffers(reqJson, resJson, OffersType.COMPANY_SEARCH_TIME,CommercialsOperation.Search);
			}
			catch(Exception ex){
				logger.warn("There was an error in processing company offers");
			}
          

			try {
				TaxEngine.getCompanyTaxes(reqJson, resJson);
			}catch(Exception x) {
				logger.warn("An exception was received during Tax Engine call",x);
			}
			
           // Put the search results in commercial cache
           try {
        	   ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
        	   CommercialCacheProcessor.putInCache(PRODUCT_AIR, clInfo[clInfo.length - 1].getCommercialsEntityId(),resJson,reqJson);
           }
           catch (Exception x) {
        	   logger.info("An exception was received while pushing search results in commercial cache", x);
           }

           
           // Send search response to async search listener for further processing
          // mSearchListener.receiveSupplierResponse(resJson);
           mSearchListener.receiveSupplierResponseV2(resJson,cabinTypeJsonArrLength);
        }
        catch (Exception x) {
        	logger.error(String.format("An exception was received processing search request for supplier %s and credentials %s", mProdSupplier.getSupplierID(), mProdSupplier.getCredentialsName()), x);
        	mSearchListener.receiveSupplierResponse(EMPTY_RESJSON);
        }
	}
	
	private JSONObject changeCurrencyOfCachedResJson(JSONObject cachedResJson, JSONObject reqHdrJson) {
		
		JSONObject resHdrJson=cachedResJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson=cachedResJson.getJSONObject(JSON_PROP_RESBODY);
		//String cacheResCcy=resHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		
		String cacheResCcy=resBodyJson.getJSONArray(JSON_PROP_PRICEDITIN).getJSONObject(0).getJSONObject(JSON_PROP_AIRPRICEINFO).getJSONObject(JSON_PROP_ITINTOTALFARE).getString(JSON_PROP_CCYCODE);
		String reqCcy=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		String clientMarket=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		if(reqCcy.equals(cacheResCcy)) {
			return cachedResJson;
		}
		JSONArray pricedItinJsonArr=cachedResJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRICEDITIN);
		Date cutOffDate=null;
		BigDecimal roe=RedisRoEData.getRateOfExchange(cacheResCcy, reqCcy, clientMarket,cutOffDate);
		
		for(int i=0;i<pricedItinJsonArr.length();i++) {
			JSONObject airItinPricingJson=pricedItinJsonArr.getJSONObject(i).getJSONObject(JSON_PROP_AIRPRICEINFO);
			JSONObject itinTotalFareJson=airItinPricingJson.getJSONObject(JSON_PROP_ITINTOTALFARE);
			//Itin total fare
			BigDecimal amount=itinTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(roe);
			itinTotalFareJson.put(JSON_PROP_AMOUNT, amount);
			itinTotalFareJson.put(JSON_PROP_CCYCODE, reqCcy);
			
			//Base Fare
			JSONObject baseFareJson=itinTotalFareJson.getJSONObject(JSON_PROP_BASEFARE);
			amount=baseFareJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(roe);
			baseFareJson.put(JSON_PROP_AMOUNT, amount);
			baseFareJson.put(JSON_PROP_CCYCODE, reqCcy);
			
			//Fees 
			JSONObject feesJson=itinTotalFareJson.optJSONObject(JSON_PROP_FEES);
			applyRoeOnComponent(reqCcy, roe, feesJson,JSON_PROP_FEE);
			
			
			//Taxes
			JSONObject taxesJson=itinTotalFareJson.optJSONObject(JSON_PROP_TAXES);
			applyRoeOnComponent(reqCcy, roe, taxesJson,JSON_PROP_TAX);
			
			//Recievables
			JSONObject recievablesJson=itinTotalFareJson.optJSONObject(JSON_PROP_RECEIVABLES);
			applyRoeOnComponent(reqCcy, roe, recievablesJson,JSON_PROP_RECEIVABLE);
			
			//Incentives
			JSONObject incentivesJson=airItinPricingJson.optJSONObject(JSON_PROP_INCENTIVES);
			applyRoeOnComponent(reqCcy, roe, incentivesJson,JSON_PROP_INCENTIVE);
			
			//CompanyOffers
			JSONArray offersJsonArr=itinTotalFareJson.optJSONArray(JSON_PROP_OFFERS);
			if(offersJsonArr!=null && offersJsonArr.length()!=0) {
			applyRoeOnOffers(reqCcy, roe, offersJsonArr);	
			}
			
			JSONArray paxTypeFaresJsonArr=airItinPricingJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
			
			for(int j=0;j<paxTypeFaresJsonArr.length();j++) {
				
				JSONObject paxTypeFareJson=paxTypeFaresJsonArr.getJSONObject(j);
				JSONObject totalFareJson=paxTypeFareJson.getJSONObject(JSON_PROP_TOTALFARE);
				//Itin total fare
			     amount=totalFareJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(roe);
			     totalFareJson.put(JSON_PROP_AMOUNT, amount);
			     totalFareJson.put(JSON_PROP_CCYCODE, reqCcy);
				
				//Base Fare
			    baseFareJson=paxTypeFareJson.getJSONObject(JSON_PROP_BASEFARE);
				amount=baseFareJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(roe);
				baseFareJson.put(JSON_PROP_AMOUNT, amount);
				baseFareJson.put(JSON_PROP_CCYCODE, reqCcy);
				
				//Fees 
				 feesJson=paxTypeFareJson.optJSONObject(JSON_PROP_FEES);
				applyRoeOnComponent(reqCcy, roe, feesJson,JSON_PROP_FEE);
				
				
				//Taxes
				 taxesJson=paxTypeFareJson.optJSONObject(JSON_PROP_TAXES);
				applyRoeOnComponent(reqCcy, roe, taxesJson,JSON_PROP_TAX);
				
				//Recievables
				 recievablesJson=paxTypeFareJson.optJSONObject(JSON_PROP_RECEIVABLES);
				applyRoeOnComponent(reqCcy, roe, recievablesJson,JSON_PROP_RECEIVABLE);
				
				//Incentives
				 incentivesJson=paxTypeFareJson.optJSONObject(JSON_PROP_INCENTIVES);
				applyRoeOnComponent(reqCcy, roe, incentivesJson,JSON_PROP_INCENTIVE);
				
				//CompanyOffers
				 offersJsonArr=paxTypeFareJson.optJSONArray(JSON_PROP_OFFERS);
				if(offersJsonArr!=null && offersJsonArr.length()!=0) {
				applyRoeOnOffers(reqCcy, roe, offersJsonArr);	
				}
				
				
				
			}
			
		}
		
		return cachedResJson;
	}

	private void applyRoeOnOffers(String reqCcy, BigDecimal roe, JSONArray offersJsonArr) {
		
		
		for(int i=0;i<offersJsonArr.length();i++) {
			JSONObject offerJson=offersJsonArr.getJSONObject(i);
			BigDecimal amount=offerJson.getBigDecimal(JSON_PROP_OFFERAMOUNT);
			offerJson.put(JSON_PROP_OFFERAMOUNT, amount.multiply(roe));
			offerJson.put(JSON_PROP_CURRENCY,reqCcy);
			
		}
		
	}

	private void applyRoeOnComponent(String reqCcy, BigDecimal roe, JSONObject componentJson, String subComponent) {
		BigDecimal amount=new BigDecimal(0);
		if(componentJson!=null) {
			if(componentJson.has(JSON_PROP_AMOUNT)) {
				amount=componentJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(roe);
			}
			else {
				amount=componentJson.getBigDecimal(JSON_PROP_TOTAL).multiply(roe);
			}
			componentJson.put(JSON_PROP_AMOUNT, amount);
			componentJson.put(JSON_PROP_CCYCODE, reqCcy);
			
			JSONArray componentJsonArr=componentJson.optJSONArray(subComponent);
			if(componentJsonArr!=null && componentJsonArr.length()!=0) {
				for(int j=0;j<componentJsonArr.length();j++) {
					JSONObject compJson=componentJsonArr.getJSONObject(j);
					amount=compJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(roe);
					compJson.put(JSON_PROP_CCYCODE, reqCcy);
					compJson.put(JSON_PROP_AMOUNT, amount);
					
				}
			}
			
			
		}
	}

	private static JSONArray convertProdSuppliersToCommercialCache(ProductSupplier... prodSupps) {
		JSONArray prodSuppsJsonArr = new JSONArray();
		if (prodSupps != null && prodSupps.length > 0) {
			for (ProductSupplier prodSupp : prodSupps) {
	        	JSONObject prodSuppJson = new JSONObject();
	        	prodSuppJson.put(JSON_PROP_SUPPID, prodSupp.getSupplierID());
	        	prodSuppJson.put(JSON_PROP_CREDSNAME, prodSupp.getCredentialsName());
	        	prodSuppJson.put(Constants.JSON_PROP_ISGDS, prodSupp.isGDS());
				prodSuppsJsonArr.put(prodSuppJson);
			}
		}
		
		return prodSuppsJsonArr;
	}

}
