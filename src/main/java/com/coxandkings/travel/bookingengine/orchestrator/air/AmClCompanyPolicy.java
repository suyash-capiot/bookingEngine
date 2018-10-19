package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirlineData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirportData;

public class AmClCompanyPolicy implements AirConstants{

	private static final Logger logger = LogManager.getLogger(AmClCompanyPolicy.class);
	 
	private static final String REFUNDABLE = "Refundable";
	private static final String NON_REFUNDABLE = "NonRefundable";
	
	public static JSONObject getAmClCompanyPolicy(CommercialsOperation op, JSONObject req, JSONObject res, JSONArray productsArr) 
			throws Exception{
		
			//CommercialsConfig commConfig = AirConfig.getCommercialsConfig();
	        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_COMPANY_POLICIES);
			//ServiceConfig commTypeConfig = AirConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_COMPANY_POLICIES);
			ServiceConfig commTypeConfig = AirConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_COMPANY_POLICIES);
	        JSONObject breCompPolicyReq = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));

	        JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
	        JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);

	        JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
	        JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);
	        
	        String type;
	        if(op.equals(CommercialsOperation.Cancel))
	        	type = reqBody.getString(JSON_PROP_CANCELTYPE);
        	else
	        	type = reqBody.getString(JSON_PROP_AMENDTYPE);	
	        	
	        JSONObject breHdrJson = new JSONObject();
	        breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
	        breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
	        breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
	        breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());

	        JSONObject rootJson = breCompPolicyReq.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.air_companypolicies.Root");
	        rootJson.put(JSON_PROP_HEADER, breHdrJson);

	        JSONObject briJson = null;
	        JSONArray briJsonArr = new JSONArray();
	        JSONObject orderDetail = null;
	        JSONArray suppBookRefs = resBody.getJSONArray(JSON_PROP_SUPPBOOKREFS);
	        JSONArray journeyDetailsJsonArr = null;
	        for (int i=0; i < suppBookRefs.length(); i++) {
	        		
	            JSONObject suppBookRef = suppBookRefs.getJSONObject(i);
	            if(AirCancelProcessor.checkIsOperationFailed(suppBookRef)) {
	            	logger.warn(String.format("Due to SI Failure Amend/Cancel policy not requested for orderID:%s",suppBookRef.optString(JSON_PROP_ORDERID)));
	            	continue;
	            	
	            }
	           // JSONObject reqSuppBookRef = reqBody.getJSONArray(JSON_PROP_SUPPBOOKREFS).getJSONObject(i);
	            JSONObject reqSuppBookRef = reqBody.getJSONArray(JSON_PROP_SUPPBOOKREFS).getJSONObject(i);
	            String orderId = reqSuppBookRef.getString("orderID");
	            for(Object order : productsArr) {
	            	if(orderId.equals(((JSONObject) order).getString("orderID"))){
	            		orderDetail = (JSONObject) order;
	            	}
	            }
	            if(orderDetail == null) {
	            	logger.debug(String.format("Order Details for orderID: %s not found", orderId));
	            }
            	briJson = createBusinessRuleIntakeForSupplier(reqHeader, reqBody, orderDetail, resBody, suppBookRef);
	            
	            journeyDetailsJsonArr = briJson.getJSONArray(JSON_PROP_JOURNEYDETAILS);
	            journeyDetailsJsonArr.put(getBRMSFlightDetailsJSON(reqSuppBookRef, orderDetail, type, op));
	            
	            JSONObject supplierPolicy = new JSONObject();
	            //TODO : To check from where to get this value.
				supplierPolicy.put("supplierBufferPeriod", "2");
				supplierPolicy.put("supplierPolicyDefinedBy", "Days");
				//TODO : As discussed with Venky, to take 'totalCost' for CancellationCharges.
				//Supplier gives total policyAmount and not on per pax level.
				
				/*String totalCost= "";
				JSONArray cancelRulesArr = suppBookRef.getJSONArray(JSON_PROP_CANCELRULES);
				for(int k=0; cancelRulesArr!=null && k<cancelRulesArr.length();k++) {
					JSONObject cancelRule = cancelRulesArr.getJSONObject(k);
					if(cancelRule.has("TotalCost"))
						totalCost = cancelRule.getString("TotalCost");
				}*/
				supplierPolicy.put("policyAmount", suppBookRef.getBigDecimal(JSON_PROP_SUPPLIERCHARGES).negate());
				briJson.put("supplierPolicy", supplierPolicy);
	            briJsonArr.put(briJson);
	        }
	        rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);

	        JSONObject companyPolicyRes = null;
	        try {
	            companyPolicyRes = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_COMPANYPLCY, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breCompPolicyReq);
	        }
	        catch (Exception x) {
	            logger.warn("An exception occurred when calling supplier commercials", x);
	        }

	        return companyPolicyRes;
		
	}
	
	private static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqSuppBookRef, JSONObject orderDetail, JSONObject resBody, JSONObject suppBookRef) {
        
		JSONObject briJson = new JSONObject();
        JSONObject commonElemsJson = new JSONObject();
        UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
        JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
       
        // Company can have multiple markets associated with it. However, a client associated with that 
 		// company can have only one market. Therefore, following assignment uses client market.
        
        commonElemsJson.put(JSON_PROP_COMPANYMKT, clientCtx.optString(JSON_PROP_CLIENTMARKET, ""));
//      commonElemsJson.put(JSON_PROP_COMPANYMKT, "India");
        commonElemsJson.put("contractValidity", DATE_FORMAT.format(new Date()));
        
        commonElemsJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_TRANSPORT);
        commonElemsJson.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_FLIGHT);
        // TODO: Check how the value for segment should be set?
        commonElemsJson.put(JSON_PROP_SEGMENT, "Active");
        
        commonElemsJson.put(JSON_PROP_ENTITYTYPE, clientCtx.getString(JSON_PROP_CLIENTTYPE));
    	commonElemsJson.put(JSON_PROP_ENTITYNAME, (usrCtx != null) ? usrCtx.getClientName() : "");
    	//TODO : Remove Hardcoding and set this value from offers fetched from DbService.
    	commonElemsJson.put("supplierOfferName", "INDOFFER");
    	ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppBookRef.getString(JSON_PROP_SUPPREF));
    	commonElemsJson.put(JSON_PROP_CREDSNAME, prodSupplier.getCredentialsName());
        briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
        
        JSONObject advDefnJson = new JSONObject();
        
        advDefnJson.put(JSON_PROP_TRAVELDATE, getTravelDateFromResponse(orderDetail));
        advDefnJson.put(JSON_PROP_JOURNEYTYPE, orderDetail!=null ? orderDetail.getJSONObject(JSON_PROP_ORDERDETAILS).getString(JSON_PROP_TRIPTYPE) : "");
        advDefnJson.put(JSON_PROP_BOOKINGDATE, DATE_FORMAT.format(new Date()));
        advDefnJson.put("transactionDate", "2019-04-24T06:00:00");
        //advDefnJson.put(JSON_PROP_NATIONALITY, clientCtx.getString(JSON_PROP_CLIENTMARKET));
        advDefnJson.put(JSON_PROP_NATIONALITY, clientCtx.getString(JSON_PROP_CLIENTMARKET));
        briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);
        
        JSONArray jrnyDtlsJsonArr = new JSONArray();
        briJson.put(JSON_PROP_JOURNEYDETAILS, jrnyDtlsJsonArr);
    
        return briJson;
    }

	public static JSONObject getBRMSFlightDetailsJSON(JSONObject reqSuppBookRef, JSONObject orderDetail, String type, CommercialsOperation op) throws Exception {
		
		//JSONArray origDestJsonArr = reqSuppBookRef.getJSONArray(JSON_PROP_ORIGDESTOPTS);
		JSONArray orderDetailOrigDestJsonArr = orderDetail.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONObject(JSON_PROP_FLIGHTDETAILS).getJSONArray(JSON_PROP_ORIGDESTOPTS);
		JSONArray fltDtlsJsonArr = new JSONArray();
		JSONArray tvlDtlsJsonArr = new JSONArray();
		
		Map<String, Set<String>> paxToFareBasisCodeSetMap = new HashMap<String, Set<String>>();
		Boolean refundIndc = false;
		for (int i = 0; i < orderDetailOrigDestJsonArr.length(); i++) {
			JSONObject origDestJson = orderDetailOrigDestJsonArr.getJSONObject(i);
			JSONArray fltSegsJsonArr = origDestJson.getJSONArray(JSON_PROP_FLIGHTSEG);
			JSONObject tvlDtlsJson = new JSONObject();
			Set<String> fareBasisSet=null;
			for (int j = 0; j < fltSegsJsonArr.length(); j++) {
				JSONObject fltSegJson = fltSegsJsonArr.getJSONObject(j);
				JSONObject fltDtlJson = new JSONObject();
				refundIndc = fltSegJson.optBoolean("refundableIndicator");
				
				JSONObject opAirlineJson = fltSegJson.getJSONObject(JSON_PROP_OPERAIRLINE);
				fltDtlJson.put(JSON_PROP_FLIGHTNBR, opAirlineJson.getString(JSON_PROP_FLIGHTNBR));
				fltDtlJson.put(JSON_PROP_FLIGHTTIMIMNG, fltSegJson.getString(JSON_PROP_DEPARTDATE));
				fltDtlJson.put(JSON_PROP_CABINCLASS, fltSegJson.getString(JSON_PROP_CABINTYPE));
				fltDtlJson.put(JSON_PROP_RBD, fltSegJson.getString(JSON_PROP_RESBOOKDESIG));
				String airlineCode = fltSegJson.getJSONObject(JSON_PROP_OPERAIRLINE).getString(JSON_PROP_AIRLINECODE);
				
				Map<String,Object> airlineData = RedisAirlineData.getAirlineDetails(airlineCode);
				fltDtlJson.put(JSON_PROP_AIRLINENAME, airlineData.getOrDefault(RedisAirlineData.AIRLINE_NAME, ""));
				fltDtlsJsonArr.put(fltDtlJson);
				
				JSONArray fareInfoArr=fltSegJson.optJSONArray(JSON_PROP_FAREINFO);
				if(fareInfoArr!=null) {
					for(int k=0;k<fareInfoArr.length();k++) {
						JSONObject fareInfoJson=fareInfoArr.getJSONObject(k);
						if(Utils.isStringNotNullAndNotEmpty(fareInfoJson.optString(JSON_PROP_FAREBASISCODE))) {
							if(paxToFareBasisCodeSetMap.containsKey(fareInfoJson.getString(JSON_PROP_PAXTYPE))) {
								fareBasisSet=paxToFareBasisCodeSetMap.get(fareInfoJson.getString(JSON_PROP_PAXTYPE));
								fareBasisSet.add(fareInfoJson.optString(JSON_PROP_FAREBASISCODE));
								paxToFareBasisCodeSetMap.put(fareInfoJson.getString(JSON_PROP_PAXTYPE), fareBasisSet);
							}
							else {
								fareBasisSet=new HashSet<String>();
								fareBasisSet.add(fareInfoJson.optString(JSON_PROP_FAREBASISCODE));
								paxToFareBasisCodeSetMap.put(fareInfoJson.getString(JSON_PROP_PAXTYPE), fareBasisSet);
							}
						}
					}
				}
			
				
				if (i == 0) {
					String origLoc = fltSegJson.getString(JSON_PROP_ORIGLOC);
					Map<String, Object> airportInfo = RedisAirportData.getAirportInfo(origLoc);
					tvlDtlsJson.put("cityFrom", airportInfo.getOrDefault(RedisAirportData.AIRPORT_CITY, ""));
					tvlDtlsJson.put("countryFrom", airportInfo.getOrDefault(RedisAirportData.AIRPORT_COUNTRY, ""));
				}

				if (i == (fltSegsJsonArr.length() - 1)) {
					String destLoc = fltSegJson.getString(JSON_PROP_DESTLOC);
					Map<String, Object> airportInfo = RedisAirportData.getAirportInfo(destLoc);
					tvlDtlsJson.put("cityTo", airportInfo.getOrDefault(RedisAirportData.AIRPORT_CITY, ""));
					tvlDtlsJson.put("countryTo", airportInfo.getOrDefault(RedisAirportData.AIRPORT_COUNTRY, ""));
				}
			}
			//paxToFareBasisCode = getPaxToFareBasis(fltSegsJsonArr);
			tvlDtlsJsonArr.put(tvlDtlsJson);
		}

		JSONObject jrnyDtlsJson = new JSONObject();
		jrnyDtlsJson.put(JSON_PROP_FLIGHTDETAILS, fltDtlsJsonArr);
		jrnyDtlsJson.put(JSON_PROP_TRAVELDTLS, tvlDtlsJsonArr);
		
		JSONArray psgrDtlsJsonArr = new JSONArray();
		JSONArray paxPricingJsonArr = orderDetail!=null ? orderDetail.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONObject("orderTotalPriceInfo").getJSONArray("paxTypeFares")
				: null;
		JSONObject orderTotalPriceInfo= orderDetail.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONObject("orderTotalPriceInfo");
		//creatclientComm map
		Map<String,Boolean> orderClientCommToCompanyFlagMap = new HashMap<String,Boolean>();
		JSONArray orderClientCommJsonArr=orderDetail!=null ? orderDetail.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONArray("orderClientCommercials"): null;
		for(int z=0;z<orderClientCommJsonArr.length();z++) {
			JSONObject orderClientCommJson=orderClientCommJsonArr.getJSONObject(z);
			orderClientCommToCompanyFlagMap.put(orderClientCommJson.getString(JSON_PROP_COMMNAME), orderClientCommJson.getBoolean(JSON_PROP_COMPANYFLAG));
			
		}
		
		Map<String,String> paxIdToType = getPaxIdToTypeMap(orderDetail);
		JSONArray paxDetailsArr = getPaxDetailsArr(type, op, orderDetail, reqSuppBookRef);
		
		for (int i = 0; paxDetailsArr!=null && i < paxDetailsArr.length(); i++) {
			JSONObject paxDetail = paxDetailsArr.getJSONObject(i);
			//TODO : This is a hack, Ideally The key should be same.
			String paxId = paxDetail.has("paxID") ? paxDetail.getString("paxID") : paxDetail.getString("passengerID");
			String paxType = paxIdToType.get(paxId);
			
			for(int j=0;paxPricingJsonArr!=null && j<paxPricingJsonArr.length();j++) {
				
				JSONObject paxPricingJson = paxPricingJsonArr.getJSONObject(j);
				if(paxType.equals(paxPricingJson.getString(JSON_PROP_PAXTYPE))) {
					JSONObject psgrDtlsJson = new JSONObject();
					JSONArray paxClientCommArr = paxPricingJson.getJSONArray("clientEntityCommercials");
					
					psgrDtlsJson.put(JSON_PROP_PSGRTYPE, paxPricingJson.getString(JSON_PROP_PAXTYPE));
					//psgrDtlsJson.put("applicableOn", "Per Passenger");
					
					// TODO: Figure out how to set dealCode, Hardcoded for now.
						psgrDtlsJson.put("dealCode", "DC01");
						
						Set<String> faseBasisCodeSet=paxToFareBasisCodeSetMap.get(paxType);
						JSONArray fareBasisJsonArr=new JSONArray();
						if(faseBasisCodeSet!=null) {
							for (String fareBasisCode : faseBasisCodeSet) {
								fareBasisJsonArr.put(fareBasisCode);
							}
						}
						
						
						
						psgrDtlsJson.put("fareBasisValue", fareBasisJsonArr);
					
					
					String fareType = refundIndc == true ? REFUNDABLE : NON_REFUNDABLE;
					
					psgrDtlsJson.put(JSON_PROP_TOTALFARE, paxPricingJson.getJSONObject(JSON_PROP_TOTALFARE).getBigDecimal(JSON_PROP_AMOUNT));
					psgrDtlsJson.put(JSON_PROP_FARETYPE, fareType);
					
					//TODD : To discuss how to set these values ?
					/*JSONObject policyDtls = new JSONObject();
					policyDtls.put("policyType", "Cancellation Terms");
					policyDtls.put("policyCategory", "Cancellation Terms");
					policyDtls.put("policyName", "AB");
					
					psgrDtlsJson.put(JSON_PROP_POLICYDTLS, policyDtls);*/
					
					//TODO : Should eventually come from DBService. To set these values from getBooking Json obtained from DBService.
					JSONArray orderTotalOffer=orderTotalPriceInfo.optJSONArray(JSON_PROP_OFFERS);
					JSONArray paxTypeOffer=paxPricingJson.optJSONArray(JSON_PROP_OFFERS);
					
					JSONArray companyOffrsArr = new JSONArray();
					
					
					if(orderTotalOffer!=null) {
						
						for(int k=0;k<orderTotalOffer.length();k++) {
							JSONObject companyoffr = new JSONObject();
							JSONObject orderTotalOfferJson=orderTotalOffer.getJSONObject(k);
							companyoffr.put(JSON_PROP_OFFERTYPE, orderTotalOfferJson.optString(JSON_PROP_OFFERTYPE));
							companyoffr.put("offerSubType", orderTotalOfferJson.optString("offerSubType"));
							companyoffr.put(JSON_PROP_OFFERNAME, orderTotalOfferJson.optString(JSON_PROP_OFFERNAME));
							companyOffrsArr.put(companyoffr);
							
						}
						
					}
					
					if(paxTypeOffer!=null) {
						for(int k=0;k<paxTypeOffer.length();k++) {
							JSONObject companyoffr = new JSONObject();
							JSONObject paxTypeOfferJson=orderTotalOffer.getJSONObject(k);
							companyoffr.put(JSON_PROP_OFFERTYPE, paxTypeOfferJson.optString(JSON_PROP_OFFERTYPE));
							companyoffr.put("offerSubType", paxTypeOfferJson.optString("offerSubType"));
							companyoffr.put(JSON_PROP_OFFERNAME, paxTypeOfferJson.optString(JSON_PROP_OFFERNAME));
							companyOffrsArr.put(companyoffr);
						}
						
					}
					
					
				/*	companyoffr.put("offerType", "Discount");
					companyoffr.put("offerSubType", "Discount On Passenger");
					companyoffr.put("offerName", "10 % Off on tickets from Mumbai to Delhi");
					companyOffrsArr.put(companyoffr);*/
				
					psgrDtlsJson.put("companyOffers", companyOffrsArr);
					
					JSONArray fareDetailsArr = new JSONArray();
					JSONObject fareDetail = new JSONObject();
					fareDetail.put(JSON_PROP_SELLPRICECOMPNAME, "Basic");
					fareDetail.put(JSON_PROP_SELLPRICECOMPVAL, paxPricingJson.getJSONObject(JSON_PROP_BASEFARE).getBigDecimal(JSON_PROP_AMOUNT));
					fareDetailsArr.put(fareDetail);
					
					JSONObject taxesObj = paxPricingJson.getJSONObject(JSON_PROP_TAXES);
					if(taxesObj!=null) {
						JSONArray taxesArr = taxesObj.getJSONArray(JSON_PROP_TAX);
						for(Object tax : taxesArr) {
							fareDetail = new JSONObject();
							fareDetail.put(JSON_PROP_SELLPRICECOMPNAME, ((JSONObject) tax).getString(JSON_PROP_TAXCODE));
							fareDetail.put(JSON_PROP_SELLPRICECOMPVAL, ((JSONObject) tax).getBigDecimal(JSON_PROP_AMOUNT));
							fareDetailsArr.put(fareDetail);
						}
					}
					
					JSONObject feesObj = paxPricingJson.getJSONObject(JSON_PROP_FEES);
					if(feesObj!=null) {
						JSONArray feesArr = feesObj.getJSONArray(JSON_PROP_FEE);
						for(Object fee : feesArr) {
							fareDetail = new JSONObject();
							fareDetail.put(JSON_PROP_SELLPRICECOMPNAME, ((JSONObject) fee).getString(JSON_PROP_FEECODE));
							fareDetail.put(JSON_PROP_SELLPRICECOMPVAL, ((JSONObject) fee).getBigDecimal(JSON_PROP_AMOUNT));
							fareDetailsArr.put(fareDetail);
						}
					}
					
					psgrDtlsJson.put(JSON_PROP_FAREDETAILS, fareDetailsArr);
					
					JSONArray companyCommHeadArr = new JSONArray();
					for(int k=0;k<paxClientCommArr.length();k++) {
						JSONObject paxClientComm = paxClientCommArr.getJSONObject(k);
						JSONArray clientCommJsonArr=paxClientComm.getJSONArray(JSON_PROP_CLIENTCOMM);
						for(int l=0;l<clientCommJsonArr.length();l++) 
						{
						JSONObject clientCommJson=clientCommJsonArr.getJSONObject(l);
						//To get only CompanyCommercials (i.e Companyflag=true)
						if(orderClientCommToCompanyFlagMap.get(clientCommJson.getString(JSON_PROP_COMMNAME))) {
							JSONObject companyCommHead = new JSONObject();
							companyCommHead.put(JSON_PROP_COMMNAME, clientCommJson.getString(JSON_PROP_COMMNAME));
							companyCommHead.put(JSON_PROP_COMMVALUE, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
							companyCommHeadArr.put(companyCommHead);
						}
					}
				}
					psgrDtlsJson.put("companyCommercialHead", companyCommHeadArr);
					psgrDtlsJsonArr.put(psgrDtlsJson);
				}
			}
		}
		jrnyDtlsJson.put(JSON_PROP_PSGRDETAILS, psgrDtlsJsonArr);
		return jrnyDtlsJson;
	}
	
	private static Map<String, String> getPaxToFareBasis(JSONArray fltSegsJsonArr) {
		
		Map<String, String> paxToFareBasis = new HashMap<String, String>();
		
		for (int i = 0; fltSegsJsonArr!=null && i < fltSegsJsonArr.length(); i++) {
			JSONObject fltSegJson = fltSegsJsonArr.getJSONObject(i);
			JSONArray fareInfoArr = fltSegJson.optJSONArray("fareInfo");
			for(int j=0; fareInfoArr!=null && j < fareInfoArr.length();j++) {
				JSONObject fareInfo = fareInfoArr.getJSONObject(j);
				paxToFareBasis.put(fareInfo.optString("paxType"), fareInfo.optString("fareBasisCode"));
			}
		}
		return paxToFareBasis;
	}

	private static Map<String,String> getPaxIdToTypeMap(JSONObject orderDetail) {
		
		Map<String,String> paxIdToType = new HashMap<String,String>();
		if(orderDetail==null) 
			return paxIdToType;
		
		JSONArray paxInfoArr = orderDetail.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONArray(JSON_PROP_PAXINFO);
		
		for(int i=0;i<paxInfoArr.length();i++) {
			JSONObject paxInfo = paxInfoArr.getJSONObject(i);
			paxIdToType.put(paxInfo.getString("passengerID"), paxInfo.getString(JSON_PROP_PAXTYPE));
		}
		return paxIdToType;
	}

	private static JSONArray getPaxDetailsArr(String type, CommercialsOperation op, JSONObject orderDetail, JSONObject reqSuppBookRef) {
		
		JSONArray paxTypeFares = null;
		
		if(op.equals(CommercialsOperation.Cancel)) 
			if(type.equals(CancelDbEnum.PAX.name()) || type.equals(CancelDbEnum.SSR.name())) 
				//For cancel on a specific pax accepting the pax Arr from WEM.
				paxTypeFares = reqSuppBookRef.getJSONArray(JSON_PROP_PAXDETAILS);
			else
				//For Other Cancellations taking all the Pax Details from OrderDetails during Booking.
				paxTypeFares = orderDetail!=null ? orderDetail.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONArray(JSON_PROP_PAXINFO) : null;
		
		else {
			if(type.equals(AmendDbEnum.PIS.name()) || type.equals(AmendDbEnum.SSR.name())) 
				//For Amend on a specific pax accepting the pax Arr from WEM.
				paxTypeFares = reqSuppBookRef.getJSONArray(JSON_PROP_PAXDETAILS);
			else
				//For Other Amendments taking all the Pax Details from OrderDetails during Booking.
				paxTypeFares = orderDetail!=null ? orderDetail.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONArray(JSON_PROP_PAXINFO) : null;
		}
		
		return paxTypeFares;
	}

	private static String getTravelDateFromResponse(JSONObject orderDetail) {
		
		JSONObject flightDetailsJson=orderDetail.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONObject(JSON_PROP_FLIGHTDETAILS);
		
		JSONArray odosJsonArr = flightDetailsJson.getJSONArray(JSON_PROP_ORIGDESTOPTS);
		if (odosJsonArr == null || odosJsonArr.length() == 0) {
			return "";
		}
		
		JSONArray flSegsJsonArr = odosJsonArr.getJSONObject(0).getJSONArray(JSON_PROP_FLIGHTSEG);
		if (flSegsJsonArr == null || flSegsJsonArr.length() == 0) {
			return "";
		}
		
		String departDate = flSegsJsonArr.getJSONObject(0).optString(JSON_PROP_DEPARTDATE);
		//return (departDate.length() >= 10) ? departDate.substring(0, 10) : "";
		return departDate;
	}
	
	
}
