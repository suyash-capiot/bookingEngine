package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ThreadPoolConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.enums.TripIndicator;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AirSearchAsyncProcessor implements AirConstants, Runnable {

	private static final Logger logger = LogManager.getLogger(AirSearchAsyncProcessor.class);
	private JSONObject mReqJson;
	private static JSONObject EMPTY_RESJSON = new JSONObject(String.format("{\"%s\": {\"%s\": []}}", JSON_PROP_RESBODY, JSON_PROP_PRICEDITIN));
	
	public AirSearchAsyncProcessor(JSONObject reqJson) {
		mReqJson = reqJson;
	}
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException, ValidationException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		TripIndicator tripInd =null;
		//JSONArray cabinTypeJsonArr=null;
		 JSONArray cabinTypeStrJsonArr=null;
		Map<String,Element> cabinTypeToSIReqMap=null;
        try {
            TrackingContext.setTrackingContext(reqJson);
            opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

            reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
            reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

            tripInd = AirSearchProcessor.deduceTripIndicator(reqHdrJson, reqBodyJson);
            reqBodyJson.put(JSON_PROP_TRIPIND, tripInd.toString());
            
            validateRequestParameters(reqJson);
            usrCtx = UserContext.getUserContextForSession(reqHdrJson);
        //    reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson);
            
             cabinTypeToSIReqMap=new HashMap<String,Element>();
              cabinTypeStrJsonArr=new JSONArray();
             String reqCabinTypeStr=reqBodyJson.getString(JSON_PROP_CABINTYPE);
             if(Utils.isStringNotNullAndNotEmpty(reqCabinTypeStr)) {
            	 int strFirstIdx=0;
            	 if(reqCabinTypeStr.contains(","))
            	 {
            		 Boolean hasCabinClass=true;
            		 while(hasCabinClass==true)
            		 {
            			 int strLstIdx=reqCabinTypeStr.indexOf(",", strFirstIdx);
                		 if(strLstIdx==-1)
                		 {
                			 cabinTypeStrJsonArr.put(reqCabinTypeStr.substring(strFirstIdx));
                			 hasCabinClass=false;
                		 }
                		 else {
                			 cabinTypeStrJsonArr.put(reqCabinTypeStr.substring(strFirstIdx, strLstIdx));
                		 }
                		
                		strFirstIdx=strLstIdx+1;
            			 
            		 }
            		
            	 }
            	 else {
            		 cabinTypeStrJsonArr.put(reqCabinTypeStr);
            	 }
             }
             reqBodyJson.put(JSON_PROP_CABINTYPE, cabinTypeStrJsonArr);
             //cabinTypeJsonArr=reqBodyJson.getJSONArray(JSON_PROP_CABINTYPE);
            for(int i=0;i<cabinTypeStrJsonArr.length();i++) {
            	String cabinType=cabinTypeStrJsonArr.getString(i);
            	cabinTypeToSIReqMap.put(cabinType, createSIRequestV2(opConfig, usrCtx, reqHdrJson, reqBodyJson,cabinType));
            }
		}
        catch (ValidationException valx) {
        	throw valx;
        }
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
            
        try {
        	//------------------------------------------------------------------
        	// Retrieve list of suppliers for performing this search
        	
        	// Retrieve PCC-HAP supplier credentials for GDS
        	List<ProductSupplier> pccHAPProdSupps = PccHAPCredentials.getProductSuppliersV2(usrCtx, reqJson);
        	logger.trace("List of PCC-HAP suppliers: {}", pccHAPProdSupps);
        	// Retrieve enabled/disabled product suppliers from user context
            List<ProductSupplier> enableDisableProdSupps = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT);
        	logger.trace("List of enabled-disabled suppliers: {}", enableDisableProdSupps);            
            // Arrive at final supplier list after deduplicating above two retrieved supplier lists
            List<ProductSupplier> prodSuppliers = deduplicateProductSupplierLists(pccHAPProdSupps, enableDisableProdSupps);
            logger.trace("List of suppliers to call: {}", prodSuppliers);
           
            if(prodSuppliers==null || prodSuppliers.size()==0)
            	 OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for Flights");	
            
        	//------------------------------------------------------------------
        	// For each supplier, start a new thread to submit search request to 
            // SI. The response of each supplier will be accummulated in instance 
            // of class AirSearchListenerResponder.
            
            if(prodSuppliers!=null) {
            AirSearchListenerResponder searchListener = new AirSearchListenerResponder(prodSuppliers, reqJson);
          /*  for (ProductSupplier prodSupplier : prodSuppliers) {
            	    SupplierSearchProcessor suppSrchPrc = new SupplierSearchProcessor(searchListener, prodSupplier, reqJson, reqElem);
                  	ThreadPoolConfig.execute(suppSrchPrc);
            }*/
            Integer cabinTypeStrJsonArrLength=cabinTypeStrJsonArr.length();
            for (ProductSupplier prodSupplier : prodSuppliers) {
          	  for(int i=0;i<cabinTypeStrJsonArr.length();i++) {
          		  reqElem=cabinTypeToSIReqMap.get(cabinTypeStrJsonArr.getString(i));
          		  JSONObject suppSearchRqJson=new JSONObject(new JSONTokener(reqJson.toString()));
          		suppSearchRqJson.put(JSON_PROP_CABINTYPE, cabinTypeStrJsonArr.getString(i));
          	    SupplierSearchProcessor suppSrchPrc = new SupplierSearchProcessor(searchListener, prodSupplier, suppSearchRqJson, reqElem,cabinTypeStrJsonArrLength);
                	ThreadPoolConfig.execute(suppSrchPrc);
          	  }
             
          }
            
    		if (isSynchronousSearchRequest(reqJson)) {
    			// This is a synchronous search request
                // Wait for all supplier threads to finish or till configured timeout
                synchronized(searchListener) {
                	searchListener.wait(AirConfig.getAsyncSearchWaitMillis());
                }
                
    			JSONObject resJson = searchListener.getSearchResponse();
    			resJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_TRIPIND, tripInd.toString());
    			
    			return resJson.toString();
    		}
            }
            else {
            	JSONObject resJson=new JSONObject();
            	resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
            	resJson.put(JSON_PROP_RESBODY, EMPTY_RESJSON.getJSONObject(JSON_PROP_RESBODY));
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

	private static Element createSIRequestV2(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson,
			JSONObject reqBodyJson, String cabinType) throws Exception {
		 Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
	        Document ownerDoc = reqElem.getOwnerDocument();

	        AirSearchProcessor.createHeader(reqHdrJson, reqElem);
	        Element travelPrefsElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody/ota:OTA_AirLowFareSearchRQ/ota:TravelPreferences");
	        Element otaReqElem = (Element) travelPrefsElem.getParentNode();
	        JSONArray origDestArr = reqBodyJson.getJSONArray(JSON_PROP_ORIGDESTINFO);
	        for (int i=0; i < origDestArr.length(); i++) {
	            JSONObject origDest = (JSONObject) origDestArr.get(i);
	            Element origDestElem = AirSearchProcessor.getOriginDestinationElement(ownerDoc, origDest);
	            otaReqElem.insertBefore(origDestElem, travelPrefsElem);
	        }

	        Element cabinPrefElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CabinPref");
	        cabinPrefElem.setAttribute("Cabin", cabinType);
	        travelPrefsElem.appendChild(cabinPrefElem);

	        Element priceInfoElem = XMLUtils.getFirstElementAtXPath(otaReqElem, "./ota:TravelerInfoSummary/ota:PriceRequestInformation");
	        Element travelerInfoElem = (Element) priceInfoElem.getParentNode();
	        JSONArray travellerArr = reqBodyJson.getJSONArray(JSON_PROP_PAXINFO);
	        for (int i=0; i < travellerArr.length(); i++) {
	            JSONObject traveller = (JSONObject) travellerArr.get(i);
	            Element travellerElem = AirSearchProcessor.getAirTravelerAvailElement(ownerDoc, traveller);
	            travelerInfoElem.insertBefore(travellerElem, priceInfoElem);
	        }

	        Element nbyDepsElem = XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:TPA_Extensions/air:NearbyDepartures");
	        Element tpaExtnsElem = (Element) nbyDepsElem.getParentNode();

	        Element tripTypeElem = ownerDoc.createElementNS(Constants.NS_AIR, "air:TripType");
	        tripTypeElem.setTextContent(reqBodyJson.getString(JSON_PROP_TRIPTYPE));
	        tpaExtnsElem.insertBefore(tripTypeElem, nbyDepsElem);

	        Element tripIndElem = ownerDoc.createElementNS(Constants.NS_AIR, "air:TripIndicator");
	        tripIndElem.setTextContent(reqBodyJson.getString(JSON_PROP_TRIPIND));
	        tpaExtnsElem.insertBefore(tripIndElem, nbyDepsElem);
	        
	        // Code for setting Nearby Airports options
	        boolean searchNearbyAirports = reqBodyJson.optBoolean(JSON_PROP_SEARCHNEARBYAIRPORTS, false);
	        if (searchNearbyAirports) {
	        	Element nbyOriginElem = XMLUtils.getFirstElementAtXPath(tpaExtnsElem, "./air:NearbyDepartures");
	        	if (nbyOriginElem != null) {
	        		nbyOriginElem.setTextContent(Boolean.toString(searchNearbyAirports));
	        	}
	        	Element nbyDestinElem = XMLUtils.getFirstElementAtXPath(tpaExtnsElem, "./air:NearbyDestinations");
	        	if (nbyDestinElem != null) {
	        		nbyDestinElem.setTextContent(Boolean.toString(searchNearbyAirports));
	        	}
	        }
			
	        return reqElem;
		
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
	
	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
        Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
        Document ownerDoc = reqElem.getOwnerDocument();

        AirSearchProcessor.createHeader(reqHdrJson, reqElem);
        Element travelPrefsElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody/ota:OTA_AirLowFareSearchRQ/ota:TravelPreferences");
        Element otaReqElem = (Element) travelPrefsElem.getParentNode();
        JSONArray origDestArr = reqBodyJson.getJSONArray(JSON_PROP_ORIGDESTINFO);
        for (int i=0; i < origDestArr.length(); i++) {
            JSONObject origDest = (JSONObject) origDestArr.get(i);
            Element origDestElem = AirSearchProcessor.getOriginDestinationElement(ownerDoc, origDest);
            otaReqElem.insertBefore(origDestElem, travelPrefsElem);
        }

        Element cabinPrefElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CabinPref");
        cabinPrefElem.setAttribute("Cabin", reqBodyJson.getString(JSON_PROP_CABINTYPE));
        travelPrefsElem.appendChild(cabinPrefElem);

        Element priceInfoElem = XMLUtils.getFirstElementAtXPath(otaReqElem, "./ota:TravelerInfoSummary/ota:PriceRequestInformation");
        Element travelerInfoElem = (Element) priceInfoElem.getParentNode();
        JSONArray travellerArr = reqBodyJson.getJSONArray(JSON_PROP_PAXINFO);
        for (int i=0; i < travellerArr.length(); i++) {
            JSONObject traveller = (JSONObject) travellerArr.get(i);
            Element travellerElem = AirSearchProcessor.getAirTravelerAvailElement(ownerDoc, traveller);
            travelerInfoElem.insertBefore(travellerElem, priceInfoElem);
        }

        Element nbyDepsElem = XMLUtils.getFirstElementAtXPath(travelerInfoElem, "./ota:TPA_Extensions/air:NearbyDepartures");
        Element tpaExtnsElem = (Element) nbyDepsElem.getParentNode();

        Element tripTypeElem = ownerDoc.createElementNS(Constants.NS_AIR, "air:TripType");
        tripTypeElem.setTextContent(reqBodyJson.getString(JSON_PROP_TRIPTYPE));
        tpaExtnsElem.insertBefore(tripTypeElem, nbyDepsElem);

        Element tripIndElem = ownerDoc.createElementNS(Constants.NS_AIR, "air:TripIndicator");
        tripIndElem.setTextContent(reqBodyJson.getString(JSON_PROP_TRIPIND));
        tpaExtnsElem.insertBefore(tripIndElem, nbyDepsElem);
        
        // Code for setting Nearby Airports options
        boolean searchNearbyAirports = reqBodyJson.optBoolean(JSON_PROP_SEARCHNEARBYAIRPORTS, false);
        if (searchNearbyAirports) {
        	Element nbyOriginElem = XMLUtils.getFirstElementAtXPath(tpaExtnsElem, "./air:NearbyDepartures");
        	if (nbyOriginElem != null) {
        		nbyOriginElem.setTextContent(Boolean.toString(searchNearbyAirports));
        	}
        	Element nbyDestinElem = XMLUtils.getFirstElementAtXPath(tpaExtnsElem, "./air:NearbyDestinations");
        	if (nbyDestinElem != null) {
        		nbyDestinElem.setTextContent(Boolean.toString(searchNearbyAirports));
        	}
        }
		
        return reqElem;
	}
	
	public static boolean isSynchronousSearchRequest(JSONObject reqJson) {
		boolean dftResult = true;
		
		if (reqJson == null) {
			return dftResult;
		}
		
		JSONObject reqHdrJson = reqJson.optJSONObject(JSON_PROP_REQHEADER);
		if (reqHdrJson == null) {
			return dftResult;
		}
		
		JSONObject clCtxJson = reqHdrJson.optJSONObject(JSON_PROP_CLIENTCONTEXT);
		return (clCtxJson == null || clCtxJson.optString(JSON_PROP_CLIENTCALLBACK).isEmpty());
	}
	
	private static void validateRequestParameters(JSONObject reqJson) throws ValidationException {
		AirRequestValidator.validateCabinTypeV2(reqJson);
		AirRequestValidator.validateTripType(reqJson);
		AirRequestValidator.validateOriginDestInfo(reqJson);
		AirRequestValidator.validateSectors(reqJson);
		AirRequestValidator.validateTravelDates(reqJson);
		AirRequestValidator.validatePassengerCounts(reqJson);
	}
	
	private static List<ProductSupplier> deduplicateProductSupplierLists(List<ProductSupplier> pccHAPProdSupps, List<ProductSupplier> enableDisableProdSupps) {
		List<ProductSupplier> deduplicatedList = new ArrayList<ProductSupplier>();
		List<String> addedSuppCredsList = new ArrayList<String>();
		
		appendUniqueSupplierCredentialsFromList(pccHAPProdSupps, deduplicatedList, addedSuppCredsList);
		appendUniqueSupplierCredentialsFromList(enableDisableProdSupps, deduplicatedList, addedSuppCredsList);
		
		return deduplicatedList;
	}
	
	private static void appendUniqueSupplierCredentialsFromList(List<ProductSupplier> suppCredsList, List<ProductSupplier> deduplicatedList, List<String> addedSuppCredsList) {
		for (ProductSupplier suppCreds : suppCredsList) {
			String suppCredsKey = String.format("%s|%s", suppCreds.getSupplierID(), suppCreds.getCredentialsName());
			if (addedSuppCredsList.contains(suppCredsKey)) {
				continue;
			}
			
			deduplicatedList.add(suppCreds);
			addedSuppCredsList.add(suppCredsKey);
		}
	}
}
