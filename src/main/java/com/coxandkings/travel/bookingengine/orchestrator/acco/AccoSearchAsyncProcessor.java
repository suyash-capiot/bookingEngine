package com.coxandkings.travel.bookingengine.orchestrator.acco;

import java.text.ParseException;
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
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.acco.enums.AccoSubType;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AccoSearchAsyncProcessor implements AccoConstants{

	private static final Logger logger = LogManager.getLogger(AccoSearchAsyncProcessor.class);

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException, ValidationException{
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		UserContext usrCtx = null;
		
		try {
			
			//OperationConfig opConfig = AccoConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			ServiceConfig opConfig = AccoConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
	        reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
	        
	        validateRequestParameters(reqBodyJson,reqHdrJson);
	       
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
	        
	        setSupplierRequestElem(reqHdrJson,reqBodyJson, reqElem);
		}
		catch (ValidationException valx) {
			throw valx;
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		try {
			//Map<String,Boolean> prodSuppMap = new HashMap<String,Boolean>();
			//usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			JSONArray prodCategSubtypeJsonArr = reqBodyJson.getJSONArray(JSON_PROP_ACCOSUBTYPEARR);
			AccoSearchListenerResponder searchListener = null;
			
			//----------------------------------------------------------------------------
			boolean suppHit = false;
			for(Object prodCategSubtype: prodCategSubtypeJsonArr) {
				//AccoSubType tempSubtype = AccoSubType.forString((String) prodCategSubtype);
				List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_ACCO,(String) prodCategSubtype);
				if (prodSuppliers == null) {
					OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for user/client for Accommodation subtype ".concat((String) prodCategSubtype));
					logger.warn("Product supplier not found for user/client for subtype ".concat((String) prodCategSubtype));
					continue;
				}
				JSONObject newReqJson = new JSONObject(reqJson.toString());
				newReqJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_ACCOSUBTYPE, (String) prodCategSubtype);
				//Utils.dedupProductSuppliers(prodSuppliers);
				searchListener = new AccoSearchListenerResponder(prodSuppliers, reqJson);
				for (ProductSupplier prodSupplier : prodSuppliers) {
					/*String prodSuppMapKey = String.format("%s%c%s", prodSupplier.getSupplierID(),KEYSEPARATOR,prodSupplier.getCredentialsName());
					if(prodSuppMap.containsKey(prodSuppMapKey)) {
						//so that multiple request of the same creds are not hit
						continue;
					}
					prodSuppMap.put(prodSuppMapKey, true);*/
					SupplierSearchProcessor suppSrchPrc = new SupplierSearchProcessor(searchListener, prodSupplier, newReqJson, reqElem);
					ThreadPoolConfig.execute(suppSrchPrc);
					suppHit = true;
				}
			}
			if(!suppHit) {
				throw new Exception("Product supplier not found for user/client for subtypes ".concat(prodCategSubtypeJsonArr.toString()));
			}
			//----------------------------------------------------------------------------
			if (Utils.isSynchronousSearchRequest(reqJson)) {
    			// This is a synchronous search request
                // Wait for all supplier threads to finish or till configured timeout
                synchronized(searchListener) {
                	searchListener.wait(AirConfig.getAsyncSearchWaitMillis());
                }

    			JSONObject resJson = searchListener.getSearchResponse();
    			return resJson.toString();
    		}
		}
		catch (Exception x) {
			  logger.error("Exception received while processing", x);
			  throw new InternalProcessingException(x);
        }
		
		return null;
	}

	private static void validateRequestParameters(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException, ParseException {
		AccoRequestValidator.validateRequestBody(reqBodyJson,reqHdrJson);
		AccoRequestValidator.validateCountryCode(reqBodyJson,reqHdrJson);
		AccoRequestValidator.validateCityCode(reqBodyJson,reqHdrJson);
		AccoRequestValidator.validateRoomConfig(reqBodyJson,reqHdrJson);
		AccoRequestValidator.validateAccoSubTypeArr(reqBodyJson,reqHdrJson);
		AccoRequestValidator.validateDates(reqBodyJson,reqHdrJson);
	}
	
	public static void setSupplierRequestElem(JSONObject reqHdrJson, JSONObject reqBodyJson, Element reqElem) throws Exception {

		Document ownerDoc = reqElem.getOwnerDocument();

		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);

		JSONObject clientContext=reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientMrkt = clientContext.getString(JSON_PROP_CLIENTMARKET);
		String clientID = clientContext.getString(JSON_PROP_CLIENTID);
		
		AccoSearchProcessor.createSuppReqHdrElem(XMLUtils.getFirstElementAtXPath(reqElem, "./acco:RequestHeader"), sessionID, transactionID, userID,clientMrkt,clientID);
		AccoSearchProcessor.setSuppReqOTAElem(ownerDoc,XMLUtils.getFirstElementAtXPath(reqElem, "./accoi:RequestBody/ota:OTA_HotelAvailRQ"),
				reqBodyJson,reqHdrJson);
	}

}
