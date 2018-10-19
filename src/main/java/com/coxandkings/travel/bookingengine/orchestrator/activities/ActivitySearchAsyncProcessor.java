package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ThreadPoolConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;

public class ActivitySearchAsyncProcessor implements ActivityConstants {

	private static final Logger logger = LogManager.getLogger(ActivitySearchAsyncProcessor.class);

	public static String process(JSONObject reqJson)
			throws InternalProcessingException, RequestProcessingException, ValidationException {

		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		UserContext usrCtx = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		Document ownerDoc = null;
//		HashMap<Integer, HashMap<String, BigDecimal>> paxTypeCountMap = new HashMap<>();
		
		try {
			opConfig = ActivitiesConfig.getOperationConfig(
					TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			ownerDoc = reqElem.getOwnerDocument();
			ActivitySearchProcessor.createHeader(reqElem, reqHdrJson);

			usrCtx = UserContext.getUserContextForSession(reqHdrJson);

		}

		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}

		try {
			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(ActivityConstants.PRODUCT_CATEGORY,
					ActivityConstants.PRODUCT_SUBCATEGORY);
			ActivitySearchListenerResponder searchListener = new ActivitySearchListenerResponder(prodSuppliers,
					reqJson);

			JSONArray activityInfoArr = reqBodyJson.getJSONArray(JSON_PROP_ACTIVITYINFO);
			
			//Adding index of ActivityInfo and creating and sending new copy of request with only corresponding ActivityInfo and Header
			for(int i = 0; i<activityInfoArr.length(); i++){
				
				JSONObject activityInfoJson = activityInfoArr.getJSONObject(i);
				activityInfoJson.put("activityInfoIndex", i);
				ActivitySearchProcessor.populateRequestBody(reqElem, ownerDoc, activityInfoJson);

				JSONObject newReq = new JSONObject();
				newReq.put(JSON_PROP_REQHEADER, reqHdrJson);
				newReq.put(JSON_PROP_REQBODY, activityInfoJson);
				
				for (ProductSupplier prodSupplier : prodSuppliers) {
					SupplierSearchProcessor suppSrchPrc = new SupplierSearchProcessor(searchListener, prodSupplier, newReq, reqElem);
					ThreadPoolConfig.execute(suppSrchPrc);

				}
			}
			if (Utils.isSynchronousSearchRequest(reqJson)) {
    			// This is a synchronous search request
                // Wait for all supplier threads to finish or till configured timeout
                synchronized(searchListener) {
                	searchListener.wait(ActivitiesConfig.getAsyncSearchWaitMillis());
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

}
