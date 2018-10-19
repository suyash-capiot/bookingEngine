package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirportData;

public class SupplierCommercials implements AirConstants {

    private static final Logger logger = LogManager.getLogger(AirSearchProcessor.class);

    public static JSONObject getSupplierCommercialsV2(CommercialsOperation op, JSONObject req, JSONObject res) throws Exception{
    	Map<String, JSONObject> bussRuleIntakeBySupp = new HashMap<String, JSONObject>();
    	
        //CommercialsConfig commConfig = AirConfig.getCommercialsConfig();
        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
    	//ServiceConfig commTypeConfig = AirConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
    	ServiceConfig commTypeConfig = AirConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
        JSONObject breSuppReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));

        JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);

        JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);

        
        UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
        
        JSONObject breHdrJson = new JSONObject();
        breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
        breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
        breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
        breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());

        JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.air_commercialscalculationengine.suppliertransactionalrules.Root");
        rootJson.put(JSON_PROP_HEADER, breHdrJson);

        JSONArray briJsonArr = new JSONArray();
        JSONArray pricedItinJsonArr = resBody.getJSONArray(JSON_PROP_PRICEDITIN);
        JSONArray journeyDetailsJsonArr = null;
        for (int i=0; i < pricedItinJsonArr.length(); i++) {
            JSONObject pricedItinJson = pricedItinJsonArr.getJSONObject(i);
            String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
            JSONObject briJson = null;
            if (bussRuleIntakeBySupp.containsKey(suppID)) {
            	briJson = bussRuleIntakeBySupp.get(suppID);
            }
            else {
            	
            	briJson = createBusinessRuleIntakeForSupplierV3(reqHeader, reqBody, resHeader, resBody, pricedItinJson,suppID,usrCtx,"","");
            	bussRuleIntakeBySupp.put(suppID, briJson);
            }
            
            journeyDetailsJsonArr = briJson.getJSONArray(JSON_PROP_JOURNEYDETAILS);
            journeyDetailsJsonArr.put(getBRMSFlightDetailsJSON(pricedItinJson));
           
        }

        Iterator<Map.Entry<String, JSONObject>> briEntryIter = bussRuleIntakeBySupp.entrySet().iterator();
        while (briEntryIter.hasNext()) {
        	Map.Entry<String, JSONObject> briEntry = briEntryIter.next();
        	briJsonArr.put(briEntry.getValue());
        }
        rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
		
		// Ops Amendments Block
		if (reqBody.has("opsAmendments") /*&& reqBody.getJSONObject("opsAmendments").getString("actionItem")
				.equalsIgnoreCase("amendSupplierCommercials")*/) {
			JSONObject opsAmendmentJson = reqBody.getJSONObject("opsAmendments");
			for (int briIdx = 0; briIdx < briJsonArr.length(); briIdx++) {
				JSONObject briJson = briJsonArr.getJSONObject(briIdx);
				if (briJson.getJSONObject(JSON_PROP_COMMONELEMS).getString(JSON_PROP_SUPP)
						.equals(opsAmendmentJson.getString("supplierId"))) {
					JSONObject journeyDetailsJson = briJson.getJSONArray(JSON_PROP_JOURNEYDETAILS)
							.getJSONObject(opsAmendmentJson.getInt("journeyDetailsIdx"));
					JSONArray psgrDtlsJsonArr = journeyDetailsJson.getJSONArray(JSON_PROP_PSGRDETAILS);
					for (int idx = 0; idx < psgrDtlsJsonArr.length(); idx++) {
						JSONObject psgrDtlsJson = psgrDtlsJsonArr.getJSONObject(idx);
						if (psgrDtlsJson.getString(JSON_PROP_PSGRTYPE)
								.equals(opsAmendmentJson.getString(JSON_PROP_PAXTYPE))) {
							psgrDtlsJson.put("bookingId", opsAmendmentJson.getString("bookingId"));
							psgrDtlsJson.put("ineligibleCommercials",
									opsAmendmentJson.getJSONArray("ineligibleCommercials"));
						}

					}
				}
			}
		}

        JSONObject breSuppResJson = null;
        try {
            breSuppResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breSuppReqJson);
        }
        catch (Exception x) {
            logger.warn("An exception occurred when calling supplier commercials", x);
        }

        return breSuppResJson;
    }
    
    public static JSONObject getSupplierCommercialsV3(CommercialsOperation op, JSONObject req, JSONObject res, List<Integer> sourceSupplierCommIndexes) throws Exception{
    	Map<String, JSONObject> bussRuleIntakeBySupp = new HashMap<String, JSONObject>();
    	
        //CommercialsConfig commConfig = AirConfig.getCommercialsConfig();
        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
    	//ServiceConfig commTypeConfig = AirConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
    	ServiceConfig commTypeConfig = AirConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
        JSONObject breSuppReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));

        JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);

        JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);

        
        UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
        
        JSONObject breHdrJson = new JSONObject();
        breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
        breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
        breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
        breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());

        JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.air_commercialscalculationengine.suppliertransactionalrules.Root");
        rootJson.put(JSON_PROP_HEADER, breHdrJson);

        String iataNumber=null;
        String prodName=null;
    	String iataCode=null;
		Document productName=null;
        
        JSONArray briJsonArr = new JSONArray();
        JSONArray pricedItinJsonArr = resBody.getJSONArray(JSON_PROP_PRICEDITIN);
        JSONArray journeyDetailsJsonArr = null;
        for (int i=0; i < pricedItinJsonArr.length(); i++) {
            JSONObject pricedItinJson = pricedItinJsonArr.getJSONObject(i);
            String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
            JSONObject briJson = null;
            if (bussRuleIntakeBySupp.containsKey(suppID)) {
            	briJson = bussRuleIntakeBySupp.get(suppID);
            }
            else {
            	
            	//call suppCommercials for enabler and lcc
            	
            	JSONObject operatingAirlineJson=pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY).getJSONArray(JSON_PROP_ORIGDESTOPTS).getJSONObject(0).getJSONArray(JSON_PROP_FLIGHTSEG).
     	        		getJSONObject(0).getJSONObject(JSON_PROP_OPERAIRLINE);
     			String iataFlightCode=operatingAirlineJson.getString(JSON_PROP_AIRLINECODE);
            	
            	org.bson.Document suppCommDoc = MDMUtils.getSupplierCommercials(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID);
        		if (suppCommDoc == null) {
        			logger.trace(String.format("Supplier commercials definition for productCategory=%s, productCategorySubType=%s and SupplierId=%s not found", PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID));
        			//should error be thrown here?
        		}
            	
        		ArrayList<org.bson.Document> stdCommercialArray=(ArrayList<Document>) MDMUtils.getValueObjectAtPathFromDocument(suppCommDoc, MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_STANDARDCOMM));
        		if(stdCommercialArray==null || stdCommercialArray.size()==0) {
        			logger.trace(String.format("Supplier settlement definition for productCategory=%s, productCategorySubType=%s, SupplierId=%s ", PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID));
        		}
        		
        		//boolean isCommissionable = (Boolean) MDMUtils.getValueObjectAtPathFromDocument(suppCommDoc, MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_STANDARDCOMM).concat(".").concat(MDM_PROP_ISCOMMISSIONABLE));
        		org.bson.Document stdComm =stdCommercialArray.get(0);
        		org.bson.Document flight =(Document) MDMUtils.getValueObjectAtPathFromDocument(stdComm, MDM_PROP_PROD.concat(".").concat(MDM_PROP_TRANSPORTATION).concat(".").concat(MDM_PROP_PROD).concat(".").concat(MDM_PROP_FLIGHT));
        	
        		ArrayList<org.bson.Document> iataNumbers=(ArrayList<Document>) flight.get(MDM_PROP_IATANUMBERS);
        		ArrayList<org.bson.Document> productIds=(ArrayList<Document>) flight.get(MDM_PROP_PRODID);
        		
        		/*if(iataNumbers!=null && iataNumbers.size()>0) {
        			
        			Iterator<org.bson.Document> iataArrayIterator=iataNumbers.iterator();
        			Iterator<org.bson.Document> productIdIterator=productIds.iterator();
        			
        			while(iataArrayIterator.hasNext() && productIdIterator.hasNext()) {
        				// iataCode=iataArrayIterator.next();
        				iataCode=iataArrayIterator.toString();
        				 productName=productIdIterator.next();
        				iataNumber=iataCode.toString();
        				if(iataNumber.equalsIgnoreCase(iataFlightCode)) {
        					prodName=productName.toString();
        				}
        			}
        		}*/

	        		if(iataNumbers!=null && iataNumbers.size()>0) {
	        			
	        			//Iterator<org.bson.Document> iataArrayIterator=iataNumbers.iterator();
	        			JSONArray iataJsonArr=new JSONArray(new JSONTokener(iataNumbers.toString()));
	        			JSONArray prodIDJsonArr=new JSONArray(new JSONTokener(productIds.toString()));
	        		//	Iterator<org.bson.Document> productIdIterator=productIds.iterator();
	        			
	        			/*while(iataArrayIterator.hasNext() && productIdIterator.hasNext()) {
	        				// iataCode=iataArrayIterator.next();
	        				 
	        				productName=productIdIterator.next();
	        				iataNumber=iataCode.toString();
	        				if(iataNumber.equalsIgnoreCase(iataFlightCode)) {
	        					prodName=productName.toString();
	        				}
	        			}*/
	        			if(iataJsonArr!=null) {
	        				for(int z=0;z<iataJsonArr.length();z++) {
	        					iataNumber=iataJsonArr.getString(z);
	        					if(iataNumber.equalsIgnoreCase(iataFlightCode))
	        					{
	        						iataNumber=iataJsonArr.getString(z);
	        						prodName=prodIDJsonArr.getString(z);
	        						break;
	        					}
		        			}
	        			}
	        			
	        		}
        		else {
        			iataNumber="";
        			prodName="All";
        		}
            
            	briJson = createBusinessRuleIntakeForSupplierV3(reqHeader, reqBody, resHeader, resBody, pricedItinJson,suppID,usrCtx,iataNumber,prodName);
            	bussRuleIntakeBySupp.put(suppID, briJson);
            }
            
            journeyDetailsJsonArr = briJson.getJSONArray(JSON_PROP_JOURNEYDETAILS);
            journeyDetailsJsonArr.put(getBRMSFlightDetailsJSON(pricedItinJson));
            
        	
        	ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID);
     		if (prodSupplier == null) {
     			throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
     		}
     		if(prodSupplier.isGDS()){
     			JSONObject operatingAirlineJson=pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY).getJSONArray(JSON_PROP_ORIGDESTOPTS).getJSONObject(0).getJSONArray(JSON_PROP_FLIGHTSEG).
     	        		getJSONObject(0).getJSONObject(JSON_PROP_OPERAIRLINE);
     			String iataFlightCode=operatingAirlineJson.getString(JSON_PROP_AIRLINECODE);
     			if(Utils.isStringNullOrEmpty(iataFlightCode)) {
     				logger.warn(String.format("No Airline Code found for GDS airline=%s for flightNumber=%s", suppID,operatingAirlineJson.opt(JSON_PROP_FLIGHTNBR)));
     			}
     	       /*String sourceSuppId=usrCtx.getSuppIdFromIATACode(iataCode);
     	      org.bson.Document suppCommDoc = MDMUtils.getSupplierCommercials(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, sourceSuppId);*/
     			//String sourceSuppId=usrCtx.getSuppIdFromIATACode(iataCode);
       	      org.bson.Document suppCommDoc = MDMUtils.getSupplierCommercialsFromIATACode(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, iataFlightCode,usrCtx.getClientCompanyId());
       	      if (suppCommDoc == null) {
     				logger.trace(String.format("Supplier commercials definition for productCategory=%s, productCategorySubType=%s and SupplierId=%s not found during setting SuppComm RQ for GDS",PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID));
     			}
     			else {
     				
     				org.bson.Document commDefData= (Document) MDMUtils.getValueObjectAtPathFromDocument(suppCommDoc, MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_COMMERCIALDEFN));
     				if(commDefData==null || commDefData.size()==0) {
     					logger.trace(String.format("Supplier standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s", PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID));
     				}
     				String sourceSuppId =MDMUtils.getValueAtPathFromDocument(commDefData, MDM_PROP_SUPPID);
     				if(Utils.isStringNotNullAndNotEmpty(sourceSuppId)) {
     					 if (bussRuleIntakeBySupp.containsKey(sourceSuppId)) {
     		            	briJson = bussRuleIntakeBySupp.get(sourceSuppId);
     		            }
     		            else {
     		            	
     		           	
     		            	org.bson.Document stdComm= (Document) MDMUtils.getValueObjectAtPathFromDocument(suppCommDoc, MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_STANDARDCOMM));
     		        		if(stdComm==null || stdComm.size()==0) {
     		        			logger.trace(String.format("Supplier settlement definition for productCategory=%s, productCategorySubType=%s, SupplierId=%s ", PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID));
     		        		}
     		        		
     		        		//boolean isCommissionable = (Boolean) MDMUtils.getValueObjectAtPathFromDocument(suppCommDoc, MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_STANDARDCOMM).concat(".").concat(MDM_PROP_ISCOMMISSIONABLE));
     		        		//org.bson.Document stdComm =stdCommercialArray.get(0);
     		        		org.bson.Document flight =(Document) MDMUtils.getValueObjectAtPathFromDocument(stdComm, MDM_PROP_PROD.concat(".").concat(MDM_PROP_TRANSPORTATION).concat(".").concat(MDM_PROP_PROD).concat(".").concat(MDM_PROP_FLIGHT));
     		        	
     		        		ArrayList<org.bson.Document> iataNumbers=(ArrayList<Document>) flight.get(MDM_PROP_IATANUMBERS);
     		        		ArrayList<org.bson.Document> productIds=(ArrayList<Document>) flight.get(MDM_PROP_PRODID);
     		        		
     		        		if(iataNumbers!=null && iataNumbers.size()>0) {
     		        			
     		        			//Iterator<org.bson.Document> iataArrayIterator=iataNumbers.iterator();
     		        			JSONArray iataJsonArr=new JSONArray(new JSONTokener(iataNumbers.toString()));
     		        			JSONArray prodIDJsonArr=new JSONArray(new JSONTokener(productIds.toString()));
     		        		//	Iterator<org.bson.Document> productIdIterator=productIds.iterator();
     		        			
     		        			/*while(iataArrayIterator.hasNext() && productIdIterator.hasNext()) {
     		        				// iataCode=iataArrayIterator.next();
     		        				 
     		        				productName=productIdIterator.next();
     		        				iataNumber=iataCode.toString();
     		        				if(iataNumber.equalsIgnoreCase(iataFlightCode)) {
     		        					prodName=productName.toString();
     		        				}
     		        			}*/
     		        			if(iataJsonArr!=null) {
     		        				for(int z=0;z<iataJsonArr.length();z++) {
     		        					iataNumber=iataJsonArr.getString(z);
     		        					if(iataNumber.equalsIgnoreCase(iataFlightCode))
     		        					{
     		        						iataNumber=iataJsonArr.getString(z);
     		        						prodName=prodIDJsonArr.getString(z);
     		        						break;
     		        					}
         		        			}
     		        			}
     		        			
     		        		}
     		        		else {
     		        			iataNumber="";
     		        			prodName="All";
     		        		}
     		            	
     		            	
     		            	  briJson = createBusinessRuleIntakeForSupplierV3(reqHeader, reqBody, resHeader, resBody, pricedItinJson,sourceSuppId,usrCtx,iataNumber,prodName);
     		            	bussRuleIntakeBySupp.put(sourceSuppId, briJson);
     		            }
     				   sourceSupplierCommIndexes.add(i);
     				  // briJson = createBusinessRuleIntakeForSupplierV3(reqHeader, reqBody, resHeader, resBody, pricedItinJson,sourceSuppId,usrCtx);
       	     	       //bussRuleIntakeBySupp.put(sourceSuppId, briJson);
       	     	       journeyDetailsJsonArr = briJson.getJSONArray(JSON_PROP_JOURNEYDETAILS);
       	     	       journeyDetailsJsonArr.put(getBRMSFlightDetailsJSON(pricedItinJson));
     				}
     				
     	     	        
     			}
     	      
     		}
        }

        Iterator<Map.Entry<String, JSONObject>> briEntryIter = bussRuleIntakeBySupp.entrySet().iterator();
        while (briEntryIter.hasNext()) {
        	Map.Entry<String, JSONObject> briEntry = briEntryIter.next();
        	briJsonArr.put(briEntry.getValue());
        }
        rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
		
		// Ops Amendments Block
		if (reqBody.has("opsAmendments") /*&& reqBody.getJSONObject("opsAmendments").getString("actionItem")
				.equalsIgnoreCase("amendSupplierCommercials")*/) {
			JSONObject opsAmendmentJson = reqBody.getJSONObject("opsAmendments");
			for (int briIdx = 0; briIdx < briJsonArr.length(); briIdx++) {
				JSONObject briJson = briJsonArr.getJSONObject(briIdx);
				if (briJson.getJSONObject(JSON_PROP_COMMONELEMS).getString(JSON_PROP_SUPP)
						.equals(opsAmendmentJson.getString("supplierId"))) {
					JSONObject journeyDetailsJson = briJson.getJSONArray(JSON_PROP_JOURNEYDETAILS)
							.getJSONObject(opsAmendmentJson.getInt("journeyDetailsIdx"));
					JSONArray psgrDtlsJsonArr = journeyDetailsJson.getJSONArray(JSON_PROP_PSGRDETAILS);
					for (int idx = 0; idx < psgrDtlsJsonArr.length(); idx++) {
						JSONObject psgrDtlsJson = psgrDtlsJsonArr.getJSONObject(idx);
						if (psgrDtlsJson.getString(JSON_PROP_PSGRTYPE)
								.equals(opsAmendmentJson.getString(JSON_PROP_PAXTYPE))) {
							psgrDtlsJson.put("bookingId", opsAmendmentJson.getString("bookingId"));
							psgrDtlsJson.put("ineligibleCommercials",
									opsAmendmentJson.getJSONArray("ineligibleCommercials"));
						}

					}
				}
			}
		}

        JSONObject breSuppResJson = null;
        try {
            breSuppResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breSuppReqJson);
        }
        catch (Exception x) {
            logger.warn("An exception occurred when calling supplier commercials", x);
        }

        return breSuppResJson;
    }
    

	public static JSONObject getBRMSFlightDetailsJSON(JSONObject pricedItinJson)throws Exception {
        boolean isVia = false;
        JSONArray origDestJsonArr =  pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY).getJSONArray(JSON_PROP_ORIGDESTOPTS);
        JSONArray fltDtlsJsonArr = new JSONArray();
        JSONArray tvlDtlsJsonArr = new JSONArray();
        for (int i=0; i < origDestJsonArr.length(); i++) {
            JSONObject origDestJson = origDestJsonArr.getJSONObject(i);
            JSONArray fltSegsJsonArr = origDestJson.getJSONArray(JSON_PROP_FLIGHTSEG);
            JSONObject tvlDtlsJson = new JSONObject();
            
            isVia = (fltSegsJsonArr.length() > 1);
            for (int j=0; j < fltSegsJsonArr.length(); j++) {
                JSONObject fltSegJson = fltSegsJsonArr.getJSONObject(j);
                JSONObject fltDtlJson = new JSONObject();
                
                JSONObject opAirlineJson = fltSegJson.getJSONObject(JSON_PROP_OPERAIRLINE);
                fltDtlJson.put(JSON_PROP_FLIGHTNBR, opAirlineJson.getString(JSON_PROP_FLIGHTNBR));
                fltDtlJson.put(JSON_PROP_FLIGHTTIMIMNG, fltSegJson.getString(JSON_PROP_DEPARTDATE));
                fltDtlJson.put(JSON_PROP_CABINCLASS, fltSegJson.getString(JSON_PROP_CABINTYPE));
                fltDtlJson.put(JSON_PROP_RBD, fltSegJson.getString(JSON_PROP_RESBOOKDESIG));
                fltDtlsJsonArr.put(fltDtlJson);
                
                if (i == 0) {
                	String origLoc = fltSegJson.getString(JSON_PROP_ORIGLOC);
                	Map<String,Object> airportInfo = RedisAirportData.getAirportInfo(origLoc);
                	tvlDtlsJson.put(JSON_PROP_FROMCITY, airportInfo.getOrDefault(RedisAirportData.AIRPORT_CITY, ""));
                	tvlDtlsJson.put(JSON_PROP_FROMCOUNTRY, airportInfo.getOrDefault(RedisAirportData.AIRPORT_COUNTRY, ""));
                	tvlDtlsJson.put(JSON_PROP_FROMCONTINENT, airportInfo.getOrDefault(RedisAirportData.AIRPORT_CONTINENT, ""));
                }
                
                if (i == (fltSegsJsonArr.length() - 1)) {
                	String destLoc = fltSegJson.getString(JSON_PROP_DESTLOC);
                	Map<String,Object> airportInfo = RedisAirportData.getAirportInfo(destLoc);
                	tvlDtlsJson.put(JSON_PROP_TOCITY, airportInfo.getOrDefault(RedisAirportData.AIRPORT_CITY, ""));
                	tvlDtlsJson.put(JSON_PROP_TOCOUNTRY, airportInfo.getOrDefault(RedisAirportData.AIRPORT_COUNTRY, ""));
                	tvlDtlsJson.put(JSON_PROP_TOCONTINENT, airportInfo.getOrDefault(RedisAirportData.AIRPORT_CONTINENT, ""));
                }
                
                if (isVia && (i > 0 && i < (fltSegsJsonArr.length() - 1))) {
                    String destLoc = fltSegJson.getString(JSON_PROP_DESTLOC);
                    Map<String,Object> airportInfo = RedisAirportData.getAirportInfo(destLoc);

                    String city = airportInfo.getOrDefault(RedisAirportData.AIRPORT_CITY, "").toString();
                    String viaCity = tvlDtlsJson.optString(JSON_PROP_VIACITY, "");
                    tvlDtlsJson.put(JSON_PROP_VIACITY, viaCity.concat((viaCity.length() > 0) ? "|" : "").concat(city));
                    String country = airportInfo.getOrDefault(RedisAirportData.AIRPORT_COUNTRY, "").toString();
                    String viaCountry = tvlDtlsJson.optString(JSON_PROP_VIACOUNTRY, "");
                    tvlDtlsJson.put(JSON_PROP_VIACOUNTRY, viaCountry.concat((viaCountry.length() > 0) ? "|" : "").concat(country));
                    String continent = airportInfo.getOrDefault(RedisAirportData.AIRPORT_CONTINENT, "").toString();
                    String viaContinent = tvlDtlsJson.optString(JSON_PROP_VIACONTINENT, "");
                    tvlDtlsJson.put(JSON_PROP_VIACONTINENT, viaContinent.concat((viaContinent.length() > 0) ? "|" : "").concat(continent));
                }

            }
            
            tvlDtlsJsonArr.put(tvlDtlsJson);
        }

        JSONObject jrnyDtlsJson = new JSONObject();
        jrnyDtlsJson.put(JSON_PROP_FLIGHTTYPE, ((isVia) ? "Via" : "Direct"));
        // TODO: Check if this hard-coding is alright...
        jrnyDtlsJson.put(JSON_PROP_FLIGHTLINETYPE, "Online");
        // TODO: Check if this hard-coding is alright...
        jrnyDtlsJson.put(JSON_PROP_CODESHAREFLIGHTINC, Boolean.TRUE.booleanValue());
        // TODO: Check if this hard-coding is alright...
        jrnyDtlsJson.put(JSON_PROP_TRAVELPRODNAME, "Flights");
        jrnyDtlsJson.put(JSON_PROP_FLIGHTDETAILS, fltDtlsJsonArr);
        jrnyDtlsJson.put(JSON_PROP_TRAVELDTLS, tvlDtlsJsonArr);

        JSONArray psgrDtlsJsonArr = new JSONArray();
        JSONArray paxPricingJsonArr = pricedItinJson.getJSONObject(JSON_PROP_AIRPRICEINFO).getJSONArray(JSON_PROP_PAXTYPEFARES);
        for (int i=0; i < paxPricingJsonArr.length(); i++) {
            JSONObject psgrDtlsJson = new JSONObject();
            JSONObject paxPricingJson = paxPricingJsonArr.getJSONObject(i);
            psgrDtlsJson.put(JSON_PROP_PSGRTYPE, paxPricingJson.getString(JSON_PROP_PAXTYPE));
            // TODO: Map fareBasisValue
            // TODO: Map dealCode
            psgrDtlsJson.put(JSON_PROP_TOTALFARE, paxPricingJson.getJSONObject(JSON_PROP_TOTALFARE).getBigDecimal(JSON_PROP_AMOUNT));

            JSONObject fareBreakupJson = new JSONObject();
            fareBreakupJson.put(JSON_PROP_BASEFARE, paxPricingJson.getJSONObject(JSON_PROP_BASEFARE).getBigDecimal(JSON_PROP_AMOUNT));

            JSONArray taxDetailsJsonArr = new JSONArray();
            JSONArray taxesJsonArr = paxPricingJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
            for (int j=0; j < taxesJsonArr.length(); j++) {
                JSONObject taxJson = taxesJsonArr.getJSONObject(j);
                JSONObject taxDetailJson = new JSONObject();
                taxDetailJson.put(JSON_PROP_TAXNAME, taxJson.getString(JSON_PROP_TAXCODE));
                taxDetailJson.put(JSON_PROP_TAXVALUE, taxJson.getBigDecimal(JSON_PROP_AMOUNT));
                taxDetailsJsonArr.put(taxDetailJson);
            }

            JSONArray feesJsonArr = paxPricingJson.getJSONObject(JSON_PROP_FEES).getJSONArray(JSON_PROP_FEE);
            for (int j=0; j < feesJsonArr.length(); j++) {
                JSONObject feeJson = feesJsonArr.getJSONObject(j);
                JSONObject feeDetailJson = new JSONObject();
                feeDetailJson.put(JSON_PROP_TAXNAME, feeJson.getString(JSON_PROP_FEECODE));
                feeDetailJson.put(JSON_PROP_TAXVALUE, feeJson.getBigDecimal(JSON_PROP_AMOUNT));
                taxDetailsJsonArr.put(feeDetailJson);
            }

            fareBreakupJson.put(JSON_PROP_TAXDETAILS, taxDetailsJsonArr);
            psgrDtlsJson.put(JSON_PROP_FAREBREAKUP, fareBreakupJson);
            psgrDtlsJsonArr.put(psgrDtlsJson);
        }

        jrnyDtlsJson.put(JSON_PROP_PSGRDETAILS, psgrDtlsJsonArr);
        return jrnyDtlsJson;
    }
    
    private static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqBody, JSONObject resHeader, JSONObject resBody, JSONObject pricedItinJson) {
        JSONObject briJson = new JSONObject();
        JSONObject commonElemsJson = new JSONObject();
        String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
        commonElemsJson.put(JSON_PROP_SUPP, suppID);
        JSONObject clientCtxJson=reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
        UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
        
        // TODO: Supplier market is hard-coded below. Where will this come from? This should be ideally come from supplier credentials.
        //commonElemsJson.put(JSON_PROP_SUPPMARKET, "India");
        commonElemsJson.put(JSON_PROP_SUPPMARKET, clientCtxJson.getString(JSON_PROP_CLIENTMARKET));
        commonElemsJson.put("contractValidity", DATE_FORMAT.format(new Date()));
        commonElemsJson.put(JSON_PROP_PRODNAME, PRODUCT_NAME_BRMS);
        // TODO: Check how the value for segment should be set?
        commonElemsJson.put(JSON_PROP_SEGMENT, "Active");
        JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
        commonElemsJson.put(JSON_PROP_CLIENTTYPE, clientCtx.getString(JSON_PROP_CLIENTTYPE));
        commonElemsJson.put(MDM_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
        
        if (usrCtx != null) {
        	commonElemsJson.put(JSON_PROP_CLIENTNAME, (usrCtx != null) ? usrCtx.getClientName() : "");
        	commonElemsJson.put(JSON_PROP_IATANBR, (usrCtx != null) ? usrCtx.getClientIATANUmber() : "");
        	List<ClientInfo> clientHierarchyList = usrCtx.getClientHierarchy();
        	if (clientHierarchyList != null && clientHierarchyList.size() > 0) {
        		ClientInfo clInfo = clientHierarchyList.get(clientHierarchyList.size() - 1);
        		if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
        			commonElemsJson.put(JSON_PROP_CLIENTGROUP, clInfo.getCommercialsEntityId());
        		}
        	}
        }
        briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
        

        JSONObject advDefnJson = new JSONObject();
        advDefnJson.put(JSON_PROP_TICKETINGDATE, DATE_FORMAT.format(new Date()));
        // TODO: How to set travelType?
        advDefnJson.put(JSON_PROP_TRAVELTYPE, "SITI");
        advDefnJson.put(JSON_PROP_JOURNEYTYPE, reqBody.getString(JSON_PROP_TRIPTYPE));
        // TODO: connectivitySupplierType hard-coded to 'LCC'. How should this value be assigned?
        //Retrieve this from productAir mongo collection by matching product name/id from clientConfigEnableDisableSupplier
        ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID);
        advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "LCC");
        /*   if(prodSupplier.isGDS()) {
 	   		advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "GDS");
 		}
 		else {
 	   		advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "LCC");
 		}*/
        // TODO: What value to set for connectivitySupplier? For now, it is set to the same value as supplierID.
        //advDefnJson.put("connectivitySupplier", resBody.getString("supplierRef"));
        advDefnJson.put(JSON_PROP_CONNECTSUPP, suppID);
        // TODO: credentialsName hard-coded to 'Indigo'. This should come from product suppliers list in user context.
        
    
        advDefnJson.put(JSON_PROP_CREDSNAME, prodSupplier.getCredentialsName());
      //  advDefnJson.put(JSON_PROP_CREDSNAME, "Indigo");
        // TODO: bookingType hard-coded to 'Online'. How should this value be assigned?
        advDefnJson.put(JSON_PROP_BOOKINGTYPE, "Online");
        briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);
        
        JSONArray jrnyDtlsJsonArr = new JSONArray();
        briJson.put(JSON_PROP_JOURNEYDETAILS, jrnyDtlsJsonArr);
    
        return briJson;
    }

    private static JSONObject createBusinessRuleIntakeForSupplierV3(JSONObject reqHeader, JSONObject reqBody, JSONObject resHeader, JSONObject resBody, JSONObject pricedItinJson, String suppID, UserContext usrCtx, String iataNumber, String prodName) throws Exception {
        JSONObject briJson = new JSONObject();
        JSONObject commonElemsJson = new JSONObject();
       // String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
        commonElemsJson.put(JSON_PROP_SUPP, suppID);
        //JSONObject clientCtxJson=reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
        
        ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID);
        
        //TODO: FOR NOW COMMENTING OUT BECAUSE FFOR SOURCE SUPPLIER DOES NOT HAVE IT
 		if (prodSupplier != null) {
 			  commonElemsJson.put(JSON_PROP_SUPPMARKET, prodSupplier.getSupplierMrkt());
 		}
 		else {
 			org.bson.Document suppCompanyMarketDoc =MDMUtils.getSupplierCompanyMarketMapping(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID);
 			if(suppCompanyMarketDoc == null) {
 				//throw error
 				commonElemsJson.put(JSON_PROP_SUPPMARKET,"");
 				//throw new Exception(String.format("Supplier Market not found for supplier %", suppID));
 			}
 			else {
 				ArrayList<org.bson.Document> mappingDocs=(ArrayList<Document>) MDMUtils.getValueObjectAtPathFromDocument(suppCompanyMarketDoc,("mappings"));
 	 			
 	 			Iterator<Document> mappingItr=mappingDocs.iterator();
 	 			while(mappingItr.hasNext()) {
 	 				org.bson.Document mappingDoc=mappingItr.next();
 	 				if((mappingDoc.getString(JSON_PROP_COMPANYMKT)).equals(usrCtx.getOrganizationHierarchy().getCompanyMarketName()))
 	 					commonElemsJson.put(JSON_PROP_SUPPMARKET,mappingDoc.getString(JSON_PROP_SUPPMARKET));
 	 			}
 			}
 			
 		}

 		
        // TODO: Supplier market is hard-coded below. Where will this come from? This should be ideally come from supplier credentials.
        //commonElemsJson.put(JSON_PROP_SUPPMARKET, "India");		
        //commonElemsJson.put(JSON_PROP_SUPPMARKET, prodSupplier.getSupplierMrkt());
        commonElemsJson.put("contractValidity", DATE_FORMAT.format(new Date()));
        //commonElemsJson.put(JSON_PROP_PRODNAME, PRODUCT_NAME_BRMS);
        commonElemsJson.put(JSON_PROP_PRODNAME, prodName);
        // TODO: Check how the value for segment should be set?
        commonElemsJson.put(JSON_PROP_SEGMENT, "Active");
        JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
        commonElemsJson.put(JSON_PROP_CLIENTTYPE, clientCtx.getString(JSON_PROP_CLIENTTYPE));
        commonElemsJson.put(MDM_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
        commonElemsJson.put(JSON_PROP_IATANBR, iataNumber);
        //get iata number from pricedItin if lcc;if GDS
			/* if(prodSupplier.isGDS()) {
        	  commonElemsJson.put(JSON_PROP_IATANBR, "");
        }
        else {
        	 commonElemsJson.put(JSON_PROP_IATANBR, iataCode);
        }*/
       
        
        if (usrCtx != null) {
        	commonElemsJson.put(JSON_PROP_CLIENTNAME, (usrCtx != null) ? usrCtx.getClientName() : "");
        	//commonElemsJson.put(JSON_PROP_IATANBR, (usrCtx != null) ? usrCtx.getClientIATANUmber() : "");
        	List<ClientInfo> clientHierarchyList = usrCtx.getClientHierarchy();
        	if (clientHierarchyList != null && clientHierarchyList.size() > 0) {
        		ClientInfo clInfo = clientHierarchyList.get(clientHierarchyList.size() - 1);
        		if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
        			commonElemsJson.put(JSON_PROP_CLIENTGROUP, clInfo.getCommercialsEntityId());
        		}
        	}
        }
        briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
        

        JSONObject advDefnJson = new JSONObject();
        advDefnJson.put(JSON_PROP_TICKETINGDATE, DATE_FORMAT.format(new Date()));
        // TODO: How to set travelType?
        advDefnJson.put(JSON_PROP_TRAVELTYPE, "SITI");
        advDefnJson.put(JSON_PROP_JOURNEYTYPE, reqBody.getString(JSON_PROP_TRIPTYPE));
        // TODO: connectivitySupplierType hard-coded to 'LCC'. How should this value be assigned?
        //Retrieve this from productAir mongo collection by matching product name/id from clientConfigEnableDisableSupplier
        //ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID);
        //advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "LCC");
        // TODO: What value to set for connectivitySupplier? For now, it is set to the same value as supplierID.
           if(prodSupplier!=null && prodSupplier.isGDS()) {
 	   		advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "GDS");
 		}
 		else {
 	   		advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "LCC");
 		}
       
        //advDefnJson.put("connectivitySupplier", resBody.getString("supplierRef"));
        advDefnJson.put(JSON_PROP_CONNECTSUPP, suppID);
        // TODO: credentialsName hard-coded to 'Indigo'. This should come from product suppliers list in user context.
        
    
        if(prodSupplier!=null)
        advDefnJson.put(JSON_PROP_CREDSNAME, prodSupplier.getCredentialsName());
      //  advDefnJson.put(JSON_PROP_CREDSNAME, "Indigo");
        // TODO: bookingType hard-coded to 'Online'. How should this value be assigned?
        advDefnJson.put(JSON_PROP_BOOKINGTYPE, "Online");
        briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);
        
        JSONArray jrnyDtlsJsonArr = new JSONArray();
        briJson.put(JSON_PROP_JOURNEYDETAILS, jrnyDtlsJsonArr);
    
        return briJson;
    }


}
