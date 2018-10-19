package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.text.ParseException;
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
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.enums.AccoSubType;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AccoSearchAsyncProcessor implements AccoConstants{

	private static final Logger logger = LogManager.getLogger(AccoSearchAsyncProcessor.class);

	@Deprecated
	/**
	 * 
	 * @author Sahil.Dhakad
	 *This implementation is done in class AccoMRSearchAsyncProcessor to handle multiroom request
	 */
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
	        // The mock UI is changing very frequently and now Accommodation sub-types are removed from UI.
	        // Just to protect our code from all these frequent changes, if acco sub-types are not passed,
	        // set it to default Hotel sub-type.
	        JSONArray accoSubTypeArr = reqBodyJson.optJSONArray(JSON_PROP_ACCOSUBTYPEARR);
	        if (accoSubTypeArr == null) {
	        	accoSubTypeArr = new JSONArray();
	        	accoSubTypeArr.put(AccoSubType.HOTEL.toString());
	        	reqBodyJson.put(JSON_PROP_ACCOSUBTYPEARR, accoSubTypeArr);
	        }
	        
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
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			JSONArray prodCategSubtypeJsonArr = reqBodyJson.getJSONArray(JSON_PROP_ACCOSUBTYPEARR);
			
			//---------------GET LIST OF ALL PRODUCTSUPPLIERS AND ROOMWISE REQUEST-------------------------
			List<ProductSupplier> finalProdSuppLst = new ArrayList<ProductSupplier>();
			List<JSONObject> finalRoomReqLst = new ArrayList<JSONObject>();
			for(Object prodCategSubtype: prodCategSubtypeJsonArr) {
				List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_ACCO,(String) prodCategSubtype);
				if (prodSuppliers == null) {
					logger.warn("Product supplier not found for user/client for subtype ".concat((String) prodCategSubtype));
					continue;
				}
				for(ProductSupplier productSupplier : prodSuppliers) {
						finalProdSuppLst.add(productSupplier);
						JSONObject newReqJson = new JSONObject(reqJson.toString());
						newReqJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_ACCOSUBTYPE, (String) prodCategSubtype);
						finalRoomReqLst.add(newReqJson);
				}
			}
			if(finalProdSuppLst.isEmpty())
				throw new Exception("Product supplier not found for user/client for subtypes ".concat(prodCategSubtypeJsonArr.toString()));
			
			AccoSearchListenerResponder searchListener = new AccoSearchListenerResponder(finalProdSuppLst, reqJson);
			
			//-------make calls for all request--------//
			for(int i=0;i<finalRoomReqLst.size();i++) {
					SupplierSearchProcessor suppSrchPrc = new SupplierSearchProcessor(searchListener, finalProdSuppLst.get(i), finalRoomReqLst.get(i), reqElem);
					ThreadPoolConfig.execute(suppSrchPrc);
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

	protected static void validateRequestParameters(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException, ParseException {
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
