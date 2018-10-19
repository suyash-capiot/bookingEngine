package com.coxandkings.travel.bookingengine.orchestrator.car.rental;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ThreadPoolConfig;
import com.coxandkings.travel.bookingengine.config.car.CarConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;

public class RentalSearchAsyncProcessor implements CarConstants, Runnable{

	private static final Logger logger = LogManager.getLogger(RentalSearchAsyncProcessor.class);
	private JSONObject mReqJson;
	
	public RentalSearchAsyncProcessor(JSONObject reqJson){
		mReqJson = reqJson;
	}
	
	public static String process(JSONObject reqJson) throws InternalProcessingException,RequestProcessingException{
		
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		UserContext usrCtx = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		try {
			opConfig = CarConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();
			RentalSearchProcessor.createHeader(reqHdrJson, reqElem);

			JSONObject carRentalReq = reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);
			RentalSearchProcessor.populateRequestBody(reqElem, ownerDoc, carRentalReq);
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			
		} catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}

		try {

			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_RENTAL);
			
		    if(prodSuppliers==null || prodSuppliers.size()==0)
            	 OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for Car subtype rental");	
           
			RentalSearchListenerResponder searchListener = new RentalSearchListenerResponder(prodSuppliers, reqJson);

			for (ProductSupplier prodSupplier : prodSuppliers) {
				SupplierSearchProcessor suppSrchPrc = new SupplierSearchProcessor(searchListener, prodSupplier, reqJson, reqElem);
				ThreadPoolConfig.execute(suppSrchPrc);
			}
    		if (Utils.isSynchronousSearchRequest(reqJson)) {
    			// This is a synchronous search request
                // Wait for all supplier threads to finish or till configured timeout
                synchronized(searchListener) {
                	searchListener.wait(CarConfig.getAsyncSearchWaitMillis());
                }

    			JSONObject resJson = searchListener.getSearchResponse();
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
	
	@Override
	public void run() {
		try {
			process(mReqJson);
		}
		catch (Exception x) {
			  logger.error("An exception was received during asynchornous search processing", x);
		}
		
	}
	

}
