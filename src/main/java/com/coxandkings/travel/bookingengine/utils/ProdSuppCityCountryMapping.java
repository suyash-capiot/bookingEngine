package com.coxandkings.travel.bookingengine.utils;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ProdSuppCityMappConfig;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class ProdSuppCityCountryMapping implements Constants{

	private static final Logger logger = LogManager.getLogger(ProdSuppCityCountryMapping.class);
	
	public static Boolean checkProductSupplierCityMapping(String countryName, String cityName, String supplierCode) {
			
		try {
			//To get the Travelogixx Supplier Code from SI supplier Code
			String tlgxCode = getTravelogixxSupplierCode(supplierCode);
			if(tlgxCode.isEmpty()) {
				logger.warn(String.format("No Travelogixx SupplierID found for %s", supplierCode));
				return false;
			}
			
			//To get the SupplierName from SupplierMaster API.
			String supplierName = getSupplierName(String.format(ProdSuppCityMappConfig.getGetSupplierNameURL(), tlgxCode));
			if(supplierName.isEmpty()) {
				logger.warn(String.format("No SupplierName found from SupplierMaster for %s", tlgxCode));
				return false;
			}
			
			//After getting supplierName, Fetch the supplier-city Mapping
			URL url = new URL(String.format(ProdSuppCityMappConfig.getSuppCityMappingFromNameURL(), countryName, cityName, supplierName));
			URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
			url = uri.toURL();
			
			JSONArray prodSuppCityMapping = HTTPServiceConsumer.consumeJSONReturnJSONArray(TARGET_SYSTEM_SUPPCITYMAPPING, url, ProdSuppCityMappConfig.getHttpHeaders(), "GET", null);
			if(prodSuppCityMapping!=null && prodSuppCityMapping.length()!=0){
				return true;
			}
			return false;
		}
		catch(Exception x) {
			logger.warn(String.format("An exception occured while fetching ProdSupp/CityMapping for Supplier <%s>", supplierCode), x);
			return false;
		}
	}

	private static boolean checkProductSupplierCityCodeMapping(String supplierID, String cityCode) {
		
		try {
			//To get the Travelogixx Supplier Code from SI supplier Code
			String tlgxCode = getTravelogixxSupplierCode(supplierID);
			if(tlgxCode.isEmpty()) {
				logger.warn(String.format("No Travelogixx SupplierID found for %s", supplierID));
				return false;
			}
			
			URL url = new URL(String.format(ProdSuppCityMappConfig.getSuppCityMappingFromCodeURL(), cityCode, tlgxCode));
			URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
			url = uri.toURL();
			
			//Fetch the supplier-city Mapping
			JSONArray prodSuppCityMapping = HTTPServiceConsumer.consumeJSONReturnJSONArray(TARGET_SYSTEM_SUPPCITYMAPPING, url, ProdSuppCityMappConfig.getHttpHeaders(), "GET", null);
			if(prodSuppCityMapping!=null && prodSuppCityMapping.length()!=0){
				return true;
			}
			return false;
		}
		catch(Exception x) {
			logger.warn(String.format("An exception occured while fetching ProdSupp/CityMapping for Supplier <%s>", supplierID), x);
			return false;
		}
	}

	private static String getSupplierName(String supplierNameurl) throws Exception{
		
		//No need to Encode it, as the tlgx SupplierCode already has '%20' for space.
		URL supplierNameUrl = new URL(supplierNameurl);
		String supplierName = "";
		JSONArray supplierInfoRes = HTTPServiceConsumer.consumeJSONReturnJSONArray(TARGET_SYSTEM_SUPPCITYMAPPING, supplierNameUrl, ProdSuppCityMappConfig.getHttpHeaders(), "GET", null);
		if(supplierInfoRes!=null && supplierInfoRes.length()!=0)
			supplierName = supplierInfoRes.getJSONObject(0).getString("supplierName");
			
		return supplierName;
	}

	private static String getTravelogixxSupplierCode(String supplierCode)throws Exception {
		
		Map<String, String> httpHdrs  = new HashMap<String, String>();
		httpHdrs.put(HTTP_HEADER_CONTENT_TYPE, HTTP_CONTENT_TYPE_APP_XML);
		
		String tlgxCode;
		Element reqElem = ProdSuppCityMappConfig.getmReqXMLShell();
		XMLUtils.setValueAtXPath(reqElem, "./SupplierID", supplierCode);
		Element resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPCITYMAPPING, ProdSuppCityMappConfig.getTlgxSupplierCrossRefURL(), httpHdrs, reqElem);
		tlgxCode = XMLUtils.getValueAtXPath(resElem, "./SupplierID");
		
		return tlgxCode;
	}
	
	public static Boolean checkProductSupplierCityMapping(String cityName, String supplierCode) throws Exception {
			
		Map<String,Object> cityInfo = RedisCityData.getCityInfo(cityName);
		String countryName = cityInfo.getOrDefault(JSON_PROP_COUNTRY, "").toString();
		return checkProductSupplierCityMapping(countryName, cityName, supplierCode);
	}
	
	public static void getProductSuppliersForCityMapping(List<ProductSupplier> productSuppliers, String cityName) throws Exception {
		
		if(productSuppliers == null) 
			return;
 
        Iterator<ProductSupplier> itr = productSuppliers.iterator();
        while (itr.hasNext())
        {
        	ProductSupplier productSupplier = (ProductSupplier) itr.next();
        	if(!checkProductSupplierCityMapping(cityName, productSupplier.getSupplierID())) {
        		 itr.remove();
			}
        }
	}
	
	public static void getProductSuppliersForCityMapping(String cityCode, List<ProductSupplier> productSuppliers) throws Exception {
		
		if(productSuppliers == null) 
			return;
 
        Iterator<ProductSupplier> itr = productSuppliers.iterator();
        while (itr.hasNext())
        {
        	ProductSupplier productSupplier = (ProductSupplier) itr.next();
        	if(!checkProductSupplierCityCodeMapping(productSupplier.getSupplierID(), cityCode)) {
        		 itr.remove();
			}
        }
	}

}
