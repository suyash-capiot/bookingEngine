package com.coxandkings.travel.bookingengine.orchestrator.acco;

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
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.orchestrator.acco.TaxEngine;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackableExecutor;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class SupplierSearchProcessor extends TrackableExecutor implements AccoConstants {

	private static final Logger logger = LogManager.getLogger(SupplierSearchProcessor.class);
	private AccoSearchListenerResponder mSearchListener;
	private JSONArray mProdSuppsJsonArr;

	SupplierSearchProcessor(AccoSearchListenerResponder searchListener, ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		mSearchListener = searchListener;
		mProdSupplier = prodSupplier;
		mReqJson = reqJson;
		//mReqElem = (Element) reqElem.cloneNode(true);
		mReqElem = (Element) ((Document)reqElem.getOwnerDocument().cloneNode(true)).importNode(reqElem, true);
		mProdSuppsJsonArr =	Utils.convertProdSuppliersToCommercialCache(mProdSupplier);
	}

	public void process(ProductSupplier prodSupplier, JSONObject reqJson, Element reqElem) {
		//OperationConfig opConfig = AccoConfig.getOperationConfig(AccoSearchProcessor.OPERATION_NAME);
		UserContext usrCtx=null; ServiceConfig opConfig=null;
		JSONObject resJson = null,resSupplierComJson = null,resClientComJson = null;
		Map<Integer,String> SI2BRMSRoomMap=null;
		try {
		 opConfig = AccoConfig.getOperationConfig(AccoSearchProcessor.OPERATION_NAME);
		 usrCtx = UserContext.getUserContextForSession(reqJson.getJSONObject(JSON_PROP_REQHEADER));
		
		 SI2BRMSRoomMap= new HashMap<Integer,String>();
		
		Document ownerDoc = reqElem.getOwnerDocument();
		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,"./acco:RequestHeader/com:SupplierCredentialsList");
		suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc, 1));

		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader/com:TransactionID");
		transactionElem.setTextContent(String.format("%s-%s-%s", transactionElem.getTextContent(),prodSupplier.getSupplierID(),
				reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_ACCOSUBTYPE)));
		}
		catch(Exception e){
			logger.error("An exception was received while running the thread for supplier {} ",mProdSupplier.getSupplierID());
			logger.info("An exception was received  while running the thread: ", e);
			mSearchListener.receiveSupplierResponse(resJson);
	    	return;
			}
		
		try { 
        	ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
		   	String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT_ACCO, clInfo[clInfo.length - 1].getCommercialsEntityId(), mProdSuppsJsonArr, reqJson);
		    if (commCacheRes != null && (commCacheRes.equals("error")) == false) {
		    	JSONObject cachedResJson = new JSONObject(new JSONTokener(commCacheRes));
		    	checkForCurrency(cachedResJson,reqJson.getJSONObject(JSON_PROP_REQHEADER));
		    	mSearchListener.receiveSupplierResponse(cachedResJson);
		    	return;
		    }
        }
        catch (Exception x) {
        	logger.info("An exception was received while retrieving search results from commercial cache", x);
        }

		Element resElem = null;
		try {
			
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(),AccoConfig.getHttpHeaders(),"POST",opConfig.getServiceTimeoutMillis(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(),opConfig.getHttpHeaders(),"POST",opConfig.getServiceTimeoutMillis(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			resJson = AccoSearchProcessor.getSupplierResponseJSON(reqJson, resElem);
			
			if(resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR).getJSONObject(0).getJSONArray(JSON_PROP_ROOMSTAYARR).length()!=0) {

			resSupplierComJson = SupplierCommercials.getSupplierCommercials(CommercialsOperation.Search,reqJson, resJson,SI2BRMSRoomMap);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierComJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", resSupplierComJson.toString()));
			}
			
			resClientComJson = ClientCommercials.getClientCommercials(reqJson,resSupplierComJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientComJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", resClientComJson.toString()));
			}
			
			AccoSearchProcessor.calculatePrices(reqJson, resJson, resClientComJson, resSupplierComJson, SI2BRMSRoomMap, false);
			
			try {
				TaxEngine.getCompanyTaxes(reqJson, resJson);
			}catch(Exception x) {
				logger.warn("An exception was received during Tax Engine call",x);
			}
			
			try {
	        	   ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
	        	   CommercialCacheProcessor.putInCache(PRODUCT_ACCO, clInfo[clInfo.length - 1].getCommercialsEntityId(),resJson,reqJson);
            }
            catch (Exception x) {
        	   logger.info("An exception was received while pushing search results in commercial cache", x);
            }
			
			//moved this call to multi room async processor
			try {
				CompanyOffers.getCompanyOffers(CommercialsOperation.Search,reqJson, resJson,OffersType.COMPANY_SEARCH_TIME);
			}
			catch(Exception x) {
				 logger.info("An exception was received while processing Company offers", x);
			}
			}
		}
		catch (Exception x) {
			//AccoSearchProcessor.calculatePrices(reqJson, resJson, resClientComJson, resSupplierComJson, SI2BRMSRoomMap, false);
			x.printStackTrace();
		}
		finally {
			mSearchListener.receiveSupplierResponse(resJson);
		}
	}
	private void checkForCurrency(JSONObject cachedResJson,JSONObject reqHeadr) {
		String clientCcyCode = reqHeadr.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		String clientMarket = reqHeadr.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		
		JSONObject cacheResBdy = cachedResJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray accoInfoArr = cacheResBdy.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		for(int i=0;i<accoInfoArr.length();i++) {
			JSONObject accInfoObj = accoInfoArr.getJSONObject(i);
			String cacheResCurncyCode=accInfoObj.getJSONArray(JSON_PROP_ROOMSTAYARR).getJSONObject(0).getJSONObject(JSON_PROP_ROOMPRICE).getString(JSON_PROP_CCYCODE);
			if(!clientCcyCode.equals(cacheResCurncyCode)) {
				//Apply ROE on each room Object
				JSONArray roomStayArr = accInfoObj.getJSONArray(JSON_PROP_ROOMSTAYARR);
				for(int j=0;j<roomStayArr.length();j++) {
					JSONObject resRoomJson = roomStayArr.getJSONObject(j);
					JSONObject totalPriceInfo = resRoomJson.getJSONObject(JSON_PROP_ROOMPRICE);
					AccoSearchProcessor.applyRoe(totalPriceInfo, clientCcyCode, clientMarket);
					
					//Apply ROE on taxes Object
					JSONObject totalTaxJson = totalPriceInfo.getJSONObject(JSON_PROP_TOTALTAX);
					AccoSearchProcessor.applyRoe(totalTaxJson, clientCcyCode, clientMarket);
					if(totalTaxJson.has(JSON_PROP_TAXBRKPARR)) {
						JSONArray taxJsonArr = totalTaxJson.getJSONArray(JSON_PROP_TAXBRKPARR);
						for(Object taxJson:taxJsonArr) {
							AccoSearchProcessor.applyRoe((JSONObject) taxJson, clientCcyCode, clientMarket);
						}
					}
					
					//Apply ROE on Nightly Prices
					JSONArray nightlyPriceArr =resRoomJson.getJSONArray(JSON_PROP_NIGHTLYPRICEARR);
					for(Object nghtPriceJson:nightlyPriceArr) {
						AccoSearchProcessor.applyRoe((JSONObject) nghtPriceJson, clientCcyCode, clientMarket);
						AccoSearchProcessor.applyRoe(((JSONObject) nghtPriceJson).getJSONObject(JSON_PROP_TOTALTAX), clientCcyCode, clientMarket);
					}
				}
			}
		}	
	}

}
