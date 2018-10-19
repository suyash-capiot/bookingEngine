package com.coxandkings.travel.bookingengine.orchestrator.transfers;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ThreadPoolConfig;
import com.coxandkings.travel.bookingengine.config.transfers.TransfersConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;

public class TransfersSearchAsyncProcessor implements TransfersConstants,Runnable {

	private JSONObject mReqJson;
	
	public TransfersSearchAsyncProcessor(JSONObject reqJson) {
		mReqJson = reqJson;
	}
	private static final Logger logger = LogManager.getLogger(TransfersSearchAsyncProcessor.class);

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		UserContext usrCtx = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		try {
			
		/*	String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT_TRANSFERS, "B2CIndiaEng", new JSONArray("[{\"supplierID\": \"ACAMPORA\"}]"), reqJson);
	        if(commCacheRes!=null && !(commCacheRes.equals("error")))
	        	return commCacheRes;*/
		    //TrackingContext.setTrackingContext(reqJson);
			opConfig = TransfersConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();
			
			validateRequestParameters(reqJson);
			
			TransfersSearchProcessor.createHeader(reqHdrJson, reqElem);

			//JSONObject transfersReq = reqBodyJson.getJSONObject(JSON_PROP_GROUNDSERVICES);
			TransfersSearchProcessor.setSuppReqOTAElem(reqBodyJson, reqElem, ownerDoc);
			
			//System.out.println(XMLTransformer.toString(reqElem));
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			
		} catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		try {

			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_TRANSFER);
			if(prodSuppliers==null || prodSuppliers.size()==0)
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for Transfer");	
			
			TransfersSearchListenerResponder searchListener = new TransfersSearchListenerResponder(prodSuppliers, reqJson);

			for (ProductSupplier prodSupplier : prodSuppliers) {
				SupplierSearchProcessor suppSrchPrc = new SupplierSearchProcessor(searchListener, prodSupplier, reqJson, reqElem);
				ThreadPoolConfig.execute(suppSrchPrc);
			}
    		if (Utils.isSynchronousSearchRequest(reqJson)) {
    			// This is a synchronous search request
                // Wait for all supplier threads to finish or till configured timeout
                synchronized(searchListener) {
                	searchListener.wait(TransfersConfig.getAsyncSearchWaitMillis());
                }

    			JSONObject resJson = searchListener.getSearchResponse();
    			//CommercialCacheProcessor.putInCache(PRODUCT_TRANSFERS, "B2CIndiaEng", resJson, reqJson);
    			return resJson.toString();
    		}
        }
        catch (Exception x) {
			  logger.error("Exception received while processing", x);
			  throw new InternalProcessingException(x);
        }
		// Code will reach here only in case of asynchronous search.
        // It does not matter what is returned here for asynchronous search.
        return null;
	}
	
	
	private static void validateRequestParameters(JSONObject reqJson) throws ValidationException {
		
		TransfersRequestValidator.validateTravelDates(reqJson);
		TransfersRequestValidator.validateTravelLocation(reqJson);
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
}
