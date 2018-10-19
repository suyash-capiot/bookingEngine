package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ThreadPoolConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.enums.AccoSubType;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;

public class AccoMRSearchAsyncProcessor implements AccoConstants{

	private static final Logger logger = LogManager.getLogger(AccoMRSearchAsyncProcessor.class);
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException, ValidationException{
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		UserContext usrCtx = null;
		
		try {
			
			ServiceConfig opConfig = AccoConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
	        reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
	        
	        AccoSearchAsyncProcessor.validateRequestParameters(reqBodyJson,reqHdrJson);
	        

	        usrCtx = UserContext.getUserContextForSession(reqHdrJson);
	        // The mock UI is changing very frequently and now Accommodation sub-types are removed from UI.
	        // Just to protect our code from all these frequent changes,
	        //if accommodation subtype is not provided in request, search for all configured subtypes
	        JSONArray accoSubTypeArr = reqBodyJson.optJSONArray(JSON_PROP_ACCOSUBTYPEARR);
	        if (accoSubTypeArr == null || accoSubTypeArr.length()==0) {
	        	accoSubTypeArr=usrCtx.getSubCatForProductCategory(PROD_CATEG_ACCO);
	        	reqBodyJson.put(JSON_PROP_ACCOSUBTYPEARR, accoSubTypeArr);
	        }
	        
	        if(accoSubTypeArr.length()==0)
	        	throw new Exception("Product supplier not found for user/client for subtypes ".concat(accoSubTypeArr.toString()));
	        
		}
		catch (ValidationException valx) {
			throw valx;
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		try {
			JSONArray prodCategSubtypeJsonArr = reqBodyJson.getJSONArray(JSON_PROP_ACCOSUBTYPEARR);
			JSONArray roomConfigJsonArr = reqBodyJson.getJSONArray(JSON_PROP_REQUESTEDROOMARR);
			
			//---------------GET LIST OF ALL PRODUCTSUPPLIERS AND ROOMWISE REQUEST-------------------------
			List<ProductSupplier> finalProdSuppLst = new ArrayList<ProductSupplier>();
			List<JSONObject> finalPerRoomReqLst = new ArrayList<JSONObject>();
			for(Object prodCategSubtype: prodCategSubtypeJsonArr) {
				List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_ACCO,(String) prodCategSubtype);
				if (prodSuppliers == null) {
					OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for Accommodation subtype ".concat((String) prodCategSubtype));
					logger.warn("Product supplier not found for user/client for subtype ".concat((String) prodCategSubtype));
					continue;
				}
				for(ProductSupplier productSupplier : prodSuppliers) {
					for(int i=0;i<roomConfigJsonArr.length();i++) {
						finalProdSuppLst.add(productSupplier);
						finalPerRoomReqLst.add(getSingleRoomReq(reqJson,(String) prodCategSubtype,i));
					}
				}
			}
			if(finalProdSuppLst.isEmpty())
				throw new Exception("Product supplier not found for user/client for subtypes ".concat(prodCategSubtypeJsonArr.toString()));
			
			//----------------INITIALIZE LISTENER WITH DEFAULT REQUEST-------------------
			AccoSearchListenerResponder searchListener = new AccoSearchListenerResponder(finalProdSuppLst, reqJson);
			
			//---------------CALL SI PER SUPPLIER PER ROOM-------------
				for(int i=0;i<finalPerRoomReqLst.size();i++) {
					Element newReqElem = (Element) ((Document)reqElem.getOwnerDocument().cloneNode(true)).importNode(reqElem, true);;
					AccoSearchAsyncProcessor.setSupplierRequestElem(reqHdrJson,finalPerRoomReqLst.get(i).getJSONObject(JSON_PROP_REQBODY), newReqElem);
					SupplierSearchProcessor suppSrchPrc = new SupplierSearchProcessor(searchListener, finalProdSuppLst.get(i), finalPerRoomReqLst.get(i), newReqElem);
					ThreadPoolConfig.execute(suppSrchPrc);
				}
				
			//----------------------------------------------------------------------------
			if (Utils.isSynchronousSearchRequest(reqJson)) {
    			// This is a synchronous search request
                // Wait for all supplier threads to finish or till configured timeout
                synchronized(searchListener) {
                	searchListener.wait(AirConfig.getAsyncSearchWaitMillis());
                }

                JSONObject resJson = searchListener.getMultiRoomSearchResponse();
                
                MRCompanyOffers.getCompanyOffers(CommercialsOperation.Search,reqJson, resJson,OffersType.COMPANY_SEARCH_TIME);
               
                MRSpliterCombiner.finalCombinedPrices(resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR));
	              
        		//this will return the response in a sorted order according to incentives or price
        		JSONArray sortedArr =AccoSearchSorterResponder.getMRSortedResponse(reqHdrJson,resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR));
        		resJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_ACCOMODATIONARR,sortedArr);
        		/*for(Object roomStayJson:sortedArr) {
        			resBodyJson= resJson.getJSONObject(JSON_PROP_RESBODY).append(JSON_PROP_ACCOMODATIONARR, roomStayJson);
        		}
        		resJson.put(JSON_PROP_RESBODY, resBodyJson);*/
                return resJson.toString();
    		}
		}
		catch (Exception x) {
			  logger.error("Exception received while processing", x);
			  throw new InternalProcessingException(x);
      }
		
		return null;
	}

	private static JSONObject getSingleRoomReq(JSONObject reqJson,String prodSubcateg,int roomIdx) {
		
		JSONObject newReqJson = new JSONObject(reqJson.toString());
		
		//make single room req
		JSONObject newReqBdyJson = newReqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONArray newRoomCfgJsonArr = (JSONArray) newReqBdyJson.remove(JSON_PROP_REQUESTEDROOMARR);
		newReqBdyJson.append(JSON_PROP_REQUESTEDROOMARR, newRoomCfgJsonArr.get(roomIdx));
		
		//add subtype
		newReqBdyJson.put(JSON_PROP_ACCOSUBTYPE,prodSubcateg );
		
		//add room index
		newReqBdyJson.put(JSON_PROP_ROOMINDEX, roomIdx);
		
		return newReqJson;
		
	}
	
}
