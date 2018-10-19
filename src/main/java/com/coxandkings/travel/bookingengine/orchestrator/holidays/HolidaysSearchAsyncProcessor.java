package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ThreadPoolConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.SupplierSearchProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;

public class HolidaysSearchAsyncProcessor implements HolidayConstants{
  
  private static final Logger logger = LogManager.getLogger(HolidaysSearchAsyncProcessor.class);
  private JSONObject mReqJson;
  private static JSONObject EMPTY_RESJSON = new JSONObject(String.format("{\"%s\": {\"%s\": []}}", JSON_PROP_RESBODY, JSON_PROP_DYNAMICPACKAGE));
  
  public HolidaysSearchAsyncProcessor(JSONObject reqJson) {
    mReqJson = reqJson;
  }
  
  public static String process(JSONObject requestJSON) throws InternalProcessingException, RequestProcessingException, ValidationException {
    Element requestElement = null;
    JSONObject requestHeaderJSON = null, requestBodyJSON = null;
    
    //OperationConfig opConfig = null;
    ServiceConfig opConfig = null;
    UserContext usrCtx = null;

    try {
        TrackingContext.setTrackingContext(requestJSON);
        // have to check why this statement is failing for holidays
        //opConfig = HolidaysConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
        opConfig = HolidaysConfig.getOperationConfig("search");
        
        requestHeaderJSON = requestJSON.getJSONObject(JSON_PROP_REQHEADER);
        requestBodyJSON = requestJSON.getJSONObject(JSON_PROP_REQBODY);
        
        //X----------------------------------------DELETE--------------------------------------------------X
        //TEMP FIX FOR HOLIDAYS OFFERS
        //HOLIDAY OFFERS NEEDS GIVING ERROR WITHOUT BRANDNAME
        JSONArray dynamicPackageArraytest = requestBodyJSON.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
        for(int i=0;i<dynamicPackageArraytest.length();i++) 
        {
          JSONObject dynamicPackageObj = dynamicPackageArraytest.getJSONObject(i);
          dynamicPackageObj.put("brandName", "");
        }
        //X----------------------------------------DELETE--------------------------------------------------X

        HolidaysSearchProcessor.validateRequestParameters(requestJSON);
        //usrCtx = UserContext.getUserContextForSession(requestHeaderJSON);
       // requestElement = HolidaysSearchProcessor.createSIRequest(opConfig, usrCtx, requestHeaderJSON, requestBodyJSON);
    }
    catch (ValidationException valx) {
        throw valx;
    }
    catch (Exception x) {
        logger.error("Exception during request processing", x);
        throw new RequestProcessingException(x);
    }
    
        
    try {
      
      usrCtx = UserContext.getUserContextForSession(requestHeaderJSON);
      
      JSONArray dynamicPackageArray = requestBodyJSON.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
      
      //Get List of all the Product Suppliers supported for that client
      List<ProductSupplier> productSuppliersList = new ArrayList<ProductSupplier>();
      List<JSONObject> NewRequestDynamicPkgList = new ArrayList<JSONObject>();
      
      List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_HOLIDAYS,PROD_CATEG_HOLIDAYS);
      if(prodSuppliers==null || prodSuppliers.size()==0)
     	 OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", requestHeaderJSON,"Product supplier not found for Holidays");	
    
      
      if (prodSuppliers == null) {
          logger.warn("Product supplier not found for user/client for subtype ".concat(PROD_CATEG_HOLIDAYS));
      }
      
      //----------------INITIALIZE LISTENER WITH DEFAULT REQUEST-------------------
      if(prodSuppliers!=null) {
        
      //removing redundent suppliers for that client 
        for(int i=0;i<dynamicPackageArray.length();i++) 
        {
          JSONObject dynamicPackageObj = dynamicPackageArray.getJSONObject(i);
            
          String supplierID = dynamicPackageObj.getString(JSON_PROP_SUPPID);
         
          for(ProductSupplier productSupplier : prodSuppliers) {
            if(productSupplier.getSupplierID().equals(supplierID))
            {
              productSuppliersList.add(productSupplier);
              NewRequestDynamicPkgList.add(getSingleRequest(requestJSON, dynamicPackageObj));
            }
          }
        }
        
      int check =0;
      HolidaysSearchListnerResponder searchListener = new HolidaysSearchListnerResponder(productSuppliersList, requestJSON);
            
      //---------------CALL SI PER SUPPLIER PER DYNAMIC PACKAGE OBJECT-------------
      for(int i=0;i<productSuppliersList.size();i++) 
      {
        requestElement = HolidaysSearchProcessor.createSIRequest(opConfig, usrCtx, requestHeaderJSON, NewRequestDynamicPkgList.get(i).getJSONObject(JSON_PROP_REQBODY));
            
        SupplierSearchProcessor supplierSearchProcessor = new SupplierSearchProcessor(searchListener, productSuppliersList.get(i) , NewRequestDynamicPkgList.get(i), requestElement);
        
        ThreadPoolConfig.execute(supplierSearchProcessor);
        
        check = 1;
      }
      
      if(check==0) {
          throw new Exception("Product suppliers not found for user/client ");
        }
      
       if (Utils.isSynchronousSearchRequest(requestJSON)) {
         // This is a synchronous search request
         // Wait for all supplier threads to finish or till configured timeout
         synchronized(searchListener) {
             searchListener.wait(AirConfig.getAsyncSearchWaitMillis());
         }
         
         JSONObject resJson = searchListener.getSearchResponse();
         
         return resJson.toString();
         }
        }
        else {
            JSONObject resJson=new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, requestHeaderJSON);
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
  
  private static JSONObject getSingleRequest(JSONObject requestJSON,JSONObject dynamicPackageObj) {
    
    JSONObject newRequestJSON = new JSONObject();
    
    JSONObject newRequestBody = new JSONObject();
    
    newRequestBody.put(JSON_PROP_TOURCODE, dynamicPackageObj.getString(JSON_PROP_TOURCODE));
    newRequestBody.put(JSON_PROP_SUBTOURCODE, dynamicPackageObj.optString(JSON_PROP_SUBTOURCODE));
    newRequestBody.put(JSON_PROP_SUPPID, dynamicPackageObj.getString(JSON_PROP_SUPPID));
    newRequestBody.put(JSON_PROP_BRANDNAME, "");
    
    newRequestJSON.put(JSON_PROP_REQBODY, newRequestBody);
    newRequestJSON.put(JSON_PROP_REQHEADER, requestJSON.getJSONObject(JSON_PROP_REQHEADER));
    
    return newRequestJSON;
    
}
 
}
