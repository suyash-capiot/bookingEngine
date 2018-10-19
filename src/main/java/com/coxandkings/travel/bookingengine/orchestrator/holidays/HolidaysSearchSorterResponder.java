package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class HolidaysSearchSorterResponder implements HolidayConstants {

  private static final DecimalFormat decFmt = new DecimalFormat("000000000.000000"); 
  private static final Logger logger = LogManager.getLogger(HolidaysSearchSorterResponder.class);

  public static void respondToCallback(URL callbackURL, JSONObject reqHdrJson, List<JSONArray> itinsList) {
    JSONObject resJson = getSortedResponse(reqHdrJson, itinsList);
    Map<String, String> httpHdrs = new HashMap<String, String>();
    httpHdrs.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_JSON);
    try {
        HTTPServiceConsumer.consumeJSONService("CallBack", callbackURL, httpHdrs, resJson);
    }
    catch (Exception x) {
        logger.warn(String.format("An exception occurred while responding to callback address %s", callbackURL.toString()), x);
    }
}
  
  static JSONObject getSortedResponse(JSONObject reqHdrJson, List<JSONArray> dynamicPackageArrayList) {
    JSONObject resBodyJson = new JSONObject();
    //JSONArray itinsJsonArr = sortItinsByPrice(itinsList);
    JSONArray dynamicPackageArray = sortDynamicPackage(reqHdrJson, dynamicPackageArrayList);
    resBodyJson.put(JSON_PROP_DYNAMICPACKAGE, dynamicPackageArray);
    
    JSONObject resJson = new JSONObject();
    resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
    resJson.put(JSON_PROP_RESBODY, resBodyJson);

    return resJson;
}
  
  private static JSONArray sortDynamicPackage(JSONObject reqHdrJson, List<JSONArray> dynamicPackageFirstArray) {
    UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
    Map<String, JSONObject> dynamicPackageSorterMap = new TreeMap<String, JSONObject>();
    StringBuilder strBldr = new StringBuilder();
    JSONArray dynamicPackageSortedArray = new JSONArray();
    
    for (JSONArray dynamicPkgSecondArray : dynamicPackageFirstArray) {
        for (int i=0; i < dynamicPkgSecondArray.length(); i++) {
            JSONObject dynamicPackageObject = dynamicPkgSecondArray.getJSONObject(i);
            JSONObject totalJSON = dynamicPackageObject.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TOTAL);
            
            strBldr.setLength(0);
            //strBldr.append(decFmt.format(itinTotalFareJson.getDouble(JSON_PROP_AMOUNT)));
            
            //Dynamic Package receieved from supplier in the dynamicPkgSecondArray will be only one
            //Thus no sortig is required
            /*strBldr.append(decFmt.format(totalJSON.getDouble(JSON_PROP_AMOUNTAFTERTAX)));
            strBldr.append(String.format("%-15s", dynamicPackageObject.getString(JSON_PROP_SUPPREF)));
            //strBldr.append(HolidaysRepriceProcessorV2.getRedisKeyForPricedItinerary(dynamicPackageObject));
            dynamicPackageSorterMap.put(strBldr.toString(), dynamicPackageObject);*/
            
            dynamicPackageSortedArray.put(dynamicPackageObject);
        }
    }
    
    //Dynamic Package receieved from supplier in the dynamicPkgSecondArray will be only one
    //Thus no sortig is required
    /*Iterator<Entry<String, JSONObject>> dynamicPackageIterator = dynamicPackageSorterMap.entrySet().iterator();
    while (dynamicPackageIterator.hasNext()) {
        Entry<String, JSONObject> dynamicPackageEntry = dynamicPackageIterator.next();
        dynamicPackageSortedArray.put(dynamicPackageEntry.getValue());
    }*/
    
    return dynamicPackageSortedArray;
}
  
}