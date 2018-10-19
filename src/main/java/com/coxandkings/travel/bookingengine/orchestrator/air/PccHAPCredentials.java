package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplierUtils;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirportData;
import com.mongodb.DBCollection;

public class PccHAPCredentials implements AirConstants {
	private static Logger logger = LogManager.getLogger(PccHAPCredentials.class);
	
	public static List<ProductSupplier> getProductSuppliers(UserContext usrCtx, JSONObject reqJson) {
		List<ProductSupplier> suppCredsList = new ArrayList<ProductSupplier>();
		
		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqClientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		
		String cpnyName = usrCtx.getOrganizationHierarchy().getCompanyName();
		String mkt = reqClientCtxJson.getString(JSON_PROP_CLIENTMARKET);
		String clType = usrCtx.getClientType().toString();
		// TODO: What should be client group? What group is this?
		String clGrp = "";
		String clName = usrCtx.getClientName();
		String cabinClass = reqBodyJson.getString(JSON_PROP_CABINTYPE);
		String pos = reqClientCtxJson.getString(JSON_PROP_POS);
		
		List<String[][]> origDestList = new ArrayList<String[][]>();
        JSONArray origDestArr = reqBodyJson.getJSONArray(JSON_PROP_ORIGDESTINFO);
        for (int i=0; i < origDestArr.length(); i++) {
            JSONObject origDest = (JSONObject) origDestArr.get(i);
            String origAirport = origDest.getString(JSON_PROP_ORIGLOC);
            String destAirport = origDest.getString(JSON_PROP_DESTLOC);
            
            origDestList.add(new String[][] {
            		{ RedisAirportData.getAirportInfo(origAirport, RedisAirportData.AIRPORT_COUNTRY), RedisAirportData.getAirportInfo(origAirport, RedisAirportData.AIRPORT_CITY) },
            		{ RedisAirportData.getAirportInfo(destAirport, RedisAirportData.AIRPORT_COUNTRY), RedisAirportData.getAirportInfo(destAirport, RedisAirportData.AIRPORT_CITY) } 
            });
        }

		List<Document> pccHAPSuppCredsList = MDMUtils.getPccHAPCredentials(MDM_VAL_SEARCH);
		for (Document pccHAPSuppCred : pccHAPSuppCredsList) {
			if ( elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_COMPANYNAME), cpnyName) == false
				|| elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_MARKET), mkt) == false
				|| elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_CLIENTTYPE), clType) == false
				// TODO: Compare client group for Product Suppliers 
				|| elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_CLIENTNAME), clName) == false
				|| elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_BOOKCLASS), cabinClass) == false
				|| elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_POS), pos) == false ) {
				continue;
			}

			// Check origin/destination countries/cities in PCC-HAP credential's origin/destination country/city filters 
			if ( isPccHAPCredentialApplicableForOriginDestinationCountryCity(pccHAPSuppCred, origDestList) == false ) {
				continue;
			}
			
			String suppName = pccHAPSuppCred.getString(MDM_PROP_SUPPNAME);
			String credName = pccHAPSuppCred.getString(MDM_PROP_CREDNAME);
			
			Document suppDoc = MDMUtils.getSupplierByName(suppName);
			if (suppDoc == null) {
				logger.debug("Supplier was not found for supplier name {0}", suppName);
				continue;
			}
			
			String suppID = suppDoc.getString(DBCollection.ID_FIELD_NAME);
			ProductSupplier prodSupp = ProductSupplierUtils.getSupplierCredentialsForCredentialsName(suppID, credName);
			if (prodSupp != null) {
				suppCredsList.add(prodSupp);
			}
		}
		
		return suppCredsList;
	}
	
	public static List<ProductSupplier> getProductSuppliersV2(UserContext usrCtx, JSONObject reqJson) {
		List<ProductSupplier> suppCredsList = new ArrayList<ProductSupplier>();
		
		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqClientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		
		String cpnyName = usrCtx.getOrganizationHierarchy().getCompanyName();
		String mkt = reqClientCtxJson.getString(JSON_PROP_CLIENTMARKET);
		String clType = usrCtx.getClientType().toString();
		// TODO: What should be client group? What group is this?
		String clGrp = "";
		String clName = usrCtx.getClientName();
		//String cabinClass = reqBodyJson.getString(JSON_PROP_CABINTYPE);
		JSONArray cabinClassJsonArr=reqBodyJson.getJSONArray(JSON_PROP_CABINTYPE);
		String pos = reqClientCtxJson.getString(JSON_PROP_POS);
		
		List<String[][]> origDestList = new ArrayList<String[][]>();
        JSONArray origDestArr = reqBodyJson.getJSONArray(JSON_PROP_ORIGDESTINFO);
        for (int i=0; i < origDestArr.length(); i++) {
            JSONObject origDest = (JSONObject) origDestArr.get(i);
            String origAirport = origDest.getString(JSON_PROP_ORIGLOC);
            String destAirport = origDest.getString(JSON_PROP_DESTLOC);
            
            origDestList.add(new String[][] {
            		{ RedisAirportData.getAirportInfo(origAirport, RedisAirportData.AIRPORT_COUNTRY), RedisAirportData.getAirportInfo(origAirport, RedisAirportData.AIRPORT_CITY) },
            		{ RedisAirportData.getAirportInfo(destAirport, RedisAirportData.AIRPORT_COUNTRY), RedisAirportData.getAirportInfo(destAirport, RedisAirportData.AIRPORT_CITY) } 
            });
        }

		List<Document> pccHAPSuppCredsList = MDMUtils.getPccHAPCredentials(MDM_VAL_SEARCH);
		for(int i=0; i < cabinClassJsonArr.length(); i++) {
			String cabinClass = cabinClassJsonArr.getString(i);
		
		for (Document pccHAPSuppCred : pccHAPSuppCredsList) {
			if ( elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_COMPANYNAME), cpnyName) == false
				|| elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_MARKET), mkt) == false
				|| elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_CLIENTTYPE), clType) == false
				// TODO: Compare client group for Product Suppliers 
				|| elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_CLIENTNAME), clName) == false
				|| elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_BOOKCLASS), cabinClass) == false
				|| elementMatchesValue(pccHAPSuppCred.getString(MDM_PROP_POS), pos) == false ) {
				continue;
			}

			// Check origin/destination countries/cities in PCC-HAP credential's origin/destination country/city filters 
			if ( isPccHAPCredentialApplicableForOriginDestinationCountryCity(pccHAPSuppCred, origDestList) == false ) {
				continue;
			}
			
			String suppName = pccHAPSuppCred.getString(MDM_PROP_SUPPNAME);
			String credName = pccHAPSuppCred.getString(MDM_PROP_CREDNAME);
			
			Document suppDoc = MDMUtils.getSupplierByName(suppName);
			if (suppDoc == null) {
				logger.debug("Supplier was not found for supplier name {0}", suppName);
				continue;
			}
			
			String suppID = suppDoc.getString(DBCollection.ID_FIELD_NAME);
			ProductSupplier prodSupp = ProductSupplierUtils.getSupplierCredentialsForCredentialsName(suppID, credName);
			if (prodSupp != null) {
				suppCredsList.add(prodSupp);
			}
		}
		}
		
		return suppCredsList;
	}
	
	private static boolean elementMatchesValue(String elemValue, String value) {
		return (elemValue == null || elemValue.isEmpty() || elemValue.equals(value));
	}
	
	private static boolean excludeCity(List<String> includeCities, String cityToBeChecked) {
		for (String includeCity : includeCities) {
			if ( ( Utils.isStringNullOrEmpty(includeCity) && Utils.isStringNullOrEmpty(cityToBeChecked) )
					|| ( Utils.isStringNotNullAndNotEmpty(includeCity) && includeCity.equals(cityToBeChecked) ) ) {
				return false;
			}
		}
		
		return true;
	}

	private static boolean excludeCountry(List<Document> includeExcludeCountries, String countryToBeChecked) {
		for (Document includeExcludeCountry : includeExcludeCountries) {
			boolean excludeFlag = ( includeExcludeCountry.getBoolean(MDM_PROP_EXCLUDE, false) || includeExcludeCountry.getBoolean(MDM_PROP_INCLUDE, true) == false);
			String includeExcludeCountryStr = includeExcludeCountry.getString(MDM_PROP_COUNTRY);
			if (excludeFlag 
					&& (
							( Utils.isStringNullOrEmpty(includeExcludeCountryStr) && Utils.isStringNullOrEmpty(countryToBeChecked) )
							|| ( Utils.isStringNotNullAndNotEmpty(includeExcludeCountryStr) && includeExcludeCountryStr.equals(countryToBeChecked))
						)
				) {
				
				return true;
			}
		}
		
		return false;
	}
	
	@SuppressWarnings("unchecked")
	private static boolean isPccHAPCredentialApplicableForOriginDestinationCountryCity(Document pccHAPSuppCred, List<String[][]> origDestList) {
		boolean isApplicableForOrigDestCountryCity = true;
		List<Document> pccHAPOrigCountries = (List<Document>) pccHAPSuppCred.get(MDM_PROP_ORIGCOUNTRY);
		List<String> pccHAPOrigCities = (List<String>) pccHAPSuppCred.get(MDM_PROP_ORIGCITY);
		List<Document> pccHAPDestCountries = (List<Document>) pccHAPSuppCred.get(MDM_PROP_DESTCOUNTRY);
		List<String> pccHAPDestCities = (List<String>) pccHAPSuppCred.get(MDM_PROP_DESTCITY);
		for (String[][] origDestPair : origDestList) {
			String[] origCountryCity = origDestPair[0];
			String[] destCountryCity = origDestPair[1];
			
			if ( excludeCountry(pccHAPOrigCountries, origCountryCity[0]) || excludeCity(pccHAPOrigCities, origCountryCity[1])
					|| excludeCountry(pccHAPDestCountries, destCountryCity[0]) || excludeCity(pccHAPDestCities, destCountryCity[1]) ) {
				isApplicableForOrigDestCountryCity = false;
				break;
			}
			
		}

		return isApplicableForOrigDestCountryCity;
	}
}
