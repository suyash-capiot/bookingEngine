package com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive;

import java.math.BigDecimal;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.car.CarConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;


public class SupplierCommercials implements CarConstants {

    public static final String OPERATION = "LowFareSearch";
    public static final String HTTP_AUTH_BASIC_PREFIX = "Basic ";
    private static final Logger logger = LogManager.getLogger(CarSearchProcessor.class);
    private static final String DEF_SUPPID = "";

    public static JSONObject getSupplierCommercials(CommercialsOperation op, JSONObject req, JSONObject res, Map<String,Integer> suppResToBRIIndex) throws Exception{
    	
        //CommercialsConfig commConfig = CarConfig.getCommercialsConfig();
        //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
    	ServiceConfig commTypeConfig = CarConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
        JSONObject breSuppReqJson = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));

        JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
        JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);

        JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
        JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);

        JSONObject breHdrJson = new JSONObject();
        breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
        breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
        breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
        breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());

        JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.carrentals_commercialscalculationengine.suppliertransactionalrules.Root");
        rootJson.put("header", breHdrJson);

        JSONArray briJsonArr = new JSONArray();
        JSONArray vehicleAvailJsonArr = new JSONArray();
        JSONArray vehicleDetailsJsonArr = null;
        JSONArray multiResArr = resBody.optJSONArray(JSON_PROP_CARRENTALARR)!=null ? resBody.getJSONArray(JSON_PROP_CARRENTALARR): new JSONArray().put(resBody.getJSONObject(JSON_PROP_CARRENTALARR));
        String prevSuppId;
        JSONObject carRentalReq;
        JSONObject briJson = null;
        int briNo=0;
		for(int i=0;i<multiResArr.length();i++) {
			
			vehicleAvailJsonArr = multiResArr.getJSONObject(i).getJSONArray(JSON_PROP_VEHICLEAVAIL);
			carRentalReq = reqBody.optJSONArray(JSON_PROP_CARRENTALARR)!=null? reqBody.getJSONArray(JSON_PROP_CARRENTALARR).getJSONObject(i): reqBody.getJSONObject(JSON_PROP_CARRENTALARR);
			prevSuppId = DEF_SUPPID;
			
			for (int j=0; j < vehicleAvailJsonArr.length(); j++) {
	            JSONObject vehicleAvailJson = vehicleAvailJsonArr.getJSONObject(j);
	            String suppID = vehicleAvailJson.getString(JSON_PROP_SUPPREF);
	            if (!prevSuppId.equals(suppID)) {
	            	prevSuppId = suppID;
	            	briJson = createBusinessRuleIntakeForSupplier(reqHeader, carRentalReq, resHeader, resBody, vehicleAvailJson);
	            	briJsonArr.put(briJson);
	            	briNo++;
	            }
	            suppResToBRIIndex.put(String.format("%d%c%d", i, KEYSEPARATOR, j), briNo);
	            vehicleDetailsJsonArr = briJson.getJSONArray(JSON_PROP_VEHICLEDETAILS);
	            vehicleDetailsJsonArr.put(getBRMSCarDetailsJSON(vehicleAvailJson));
	        }
		}
	
        rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
        JSONObject breSuppResJson = null;
        try {
            //breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS/SuppComm", commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breSuppReqJson);
        	breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS/SuppComm", commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breSuppReqJson);
        }
        catch (Exception x) {
            logger.warn("An exception occurred when calling supplier commercials", x);
        }
        return breSuppResJson;
    }

    public static JSONObject getBRMSCarDetailsJSON(JSONObject vehicleAvailJson) {
    	
    	JSONObject vehDetails = new JSONObject();
    	JSONArray psgDetailsArr = new JSONArray();
    	
		/*JSONObject vehicleInfo = vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO);
		vehDetails.put("sipp_ACRISSCode", vehicleInfo.getString(JSON_PROP_VEHICLECODE));
		vehDetails.put("vehicleCategory", vehicleInfo.getString(JSON_PROP_VEHICLECATEGORY));
		vehDetails.put("vehicleName", vehicleInfo.getString(JSON_PROP_VEHICLENAME));*/
		
    	//TODO: Remove the Hard-coded Values and use the above method
    	vehDetails.put("sipp_ACRISSCode", "CDMR");
    	vehDetails.put("vehicleCategory", "Economy");
    	vehDetails.put("vehicleName", "Tata Indica");
    	
		//TODO: find out how to set these values
		vehDetails.put("rateTypeApplicableFor","Both");
		vehDetails.put("rateTypeApplicableForCategory","Rental");
		vehDetails.put("modeOfPayment","Prepaid");
//    	vehDetails.put("modeOfPayment", vehicleAvailJson.getString("isPrepaid"));
		
			/*String cityName = vehicleAvailJson.getString(JSON_PROP_CITY);
	    	Map<String,Object> cityInfo = RedisCityData.getCityInfo(cityName);
	    	vehDetails.put(JSON_PROP_TOCONTINENT, cityInfo.getOrDefault("continent", ""));
	    	vehDetails.put(JSON_PROP_TOCOUNTRY, cityInfo.getOrDefault("country", ""));
	    	vehDetails.put(JSON_PROP_TOSTATE, cityInfo.getOrDefault("state", ""));
	    	vehDetails.put(JSON_PROP_TOCITY, cityName);*/
		
		//TODO: Remove the hard-coded values and use the above code to fetch from Redis	
		vehDetails.put(JSON_PROP_TOCONTINENT,"Asia");
		vehDetails.put(JSON_PROP_TOCOUNTRY,"India");
		vehDetails.put(JSON_PROP_TOSTATE,"Goa");
		vehDetails.put(JSON_PROP_TOCITY,"Goa");
		
		JSONObject psgDetail = getPsgDetailsJson(vehicleAvailJson);
		psgDetailsArr.put(psgDetail);
		vehDetails.put(JSON_PROP_PSGRDETAILS, psgDetailsArr);
    	
    	return vehDetails;
    }
    
    private static JSONObject getPsgDetailsJson(JSONObject vehicleAvailJson) {
    	
    	JSONObject psgDetails = new JSONObject();
    	BigDecimal totalFare = new BigDecimal(0);
//		As Confirmed by the Commercial guy (Milan), the price of a car is to be sent in 1st Passenger Details.
//    	JSONObject totalFareJson = vehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO).getJSONObject(JSON_PROP_TOTALFARE);
//    	psgDetails.put("passengerType","ADT");
    	JSONObject fareBreakUp = getFareBreakUpJson(vehicleAvailJson);
    	totalFare = totalFare.add(fareBreakUp.getBigDecimal(JSON_PROP_BASEFARE_COMM));
    	for(Object taxDetail : fareBreakUp.optJSONArray(JSON_PROP_TAXDETAILS)) {
    		totalFare = totalFare.add(((JSONObject) taxDetail).getBigDecimal(JSON_PROP_TAXVALUE));
    	}
    	psgDetails.put(JSON_PROP_TOTALFARE_COMM, totalFare);
    	psgDetails.put(JSON_PROP_FAREBRKUP, fareBreakUp);
    	
    	return psgDetails;
    }
    
    private static JSONObject getFareBreakUpJson(JSONObject vehicleAvailJson) {
    	
    	JSONObject totalFareJson = vehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO).getJSONObject(JSON_PROP_TOTALFARE);
    	JSONArray taxJsonArr = totalFareJson.getJSONObject(JSON_PROP_TAXES).optJSONArray(JSON_PROP_TAX);
    	JSONArray pricedEquipsArr = vehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO).optJSONObject(JSON_PROP_TOTALFARE).getJSONObject(JSON_PROP_SPLEQUIPS).optJSONArray(JSON_PROP_SPLEQUIP);
    	JSONObject fareBreakUp = new JSONObject();
    	JSONArray taxDetailsArr = new JSONArray();
    	JSONObject taxDetailJson = new JSONObject();
    	
    	BigDecimal baseTotal = totalFareJson.getJSONObject(JSON_PROP_BASEFARE).getBigDecimal(JSON_PROP_AMOUNT);
    	
    	//TODO : May need to revisit later.
		for(int i=0;pricedEquipsArr!=null && i<pricedEquipsArr.length();i++){
			JSONObject pricedEquipsJson = pricedEquipsArr.getJSONObject(i);	
			//In case of price Operation
			//If baseFare includes equipment price, remove it and add back the price after applying commercials
			if(pricedEquipsJson.optString(JSON_PROP_ISINCLDINBASE).equals("true")) {
				BigDecimal equipAmount = pricedEquipsJson.optBigDecimal(JSON_PROP_AMOUNT, new BigDecimal(0));
				if(equipAmount.compareTo(BigDecimal.ZERO) == 1) 
					continue;
				baseTotal = baseTotal.subtract(equipAmount);
				pricedEquipsJson.put(JSON_PROP_ISINCLDINBASE, "false");
			}
		}
//    	totalFareJson.getJSONObject(JSON_PROP_BASEFARE).put(JSON_PROP_AMOUNT, baseTotal);
    	
    	JSONArray feesJsonArr = totalFareJson.getJSONObject(JSON_PROP_FEES).optJSONArray(JSON_PROP_FEE);
    	if((taxJsonArr==null || taxJsonArr.length()==0) && (feesJsonArr==null || taxJsonArr.length()==0)) {
    		// If Both Tax and Fees are absent, then calculating Tax by subtraction for now.
    		// TODO : Ideally Taxes should come in the response, if not then should not send it in tax details.
	    	BigDecimal tax = totalFareJson.getBigDecimal(JSON_PROP_AMOUNT).subtract(baseTotal);
	    	taxDetailJson.put(JSON_PROP_TAXNAME, "YQ");
	    	taxDetailJson.put(JSON_PROP_TAXVALUE, tax);
	    	taxDetailsArr.put(taxDetailJson);
    	}
    	else {
    		String code;
    		for(int i=0;taxJsonArr!=null && i<taxJsonArr.length();i++) {
    			JSONObject taxesJson = taxJsonArr.getJSONObject(i);
    			 //TODO : Some Supplier do not give TaxCode. Should be Handled by CCE, For Now
    			taxDetailJson = new JSONObject();
                taxDetailJson.put(JSON_PROP_TAXNAME, (code = taxesJson.optString(JSON_PROP_TAXCODE)).isEmpty() ? "YQ" : code);
    			taxDetailJson.put(JSON_PROP_TAXVALUE, taxesJson.getBigDecimal(JSON_PROP_AMOUNT));
    	    	taxDetailsArr.put(taxDetailJson);
    		}
    		for (int j=0; feesJsonArr!=null && j<feesJsonArr.length(); j++) {
                JSONObject feeJson = feesJsonArr.getJSONObject(j);
                JSONObject feeDetailJson = new JSONObject();
                //TODO : Some Supplier do not give FeeCode. Should be Handled by CCE, For Now
                if(!feeJson.optString(JSON_PROP_ISINCLDINBASE).equals("true")) {
	                feeDetailJson.put(JSON_PROP_TAXNAME, (code = feeJson.optString(JSON_PROP_FEECODE)).isEmpty()? "RT": code);
	                feeDetailJson.put(JSON_PROP_TAXVALUE, feeJson.getBigDecimal(JSON_PROP_AMOUNT));
	                taxDetailsArr.put(feeDetailJson);
                }
    		}
    	}
    	fareBreakUp.put(JSON_PROP_BASEFARE_COMM, baseTotal);
    	fareBreakUp.put(JSON_PROP_TAXDETAILS, taxDetailsArr);
    	return fareBreakUp;
    }
    
    private static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqBody, JSONObject resHeader, JSONObject resBody, JSONObject vehicleAvailJson) {
        JSONObject briJson = new JSONObject();
        JSONObject commonElemsJson = new JSONObject();
        String suppID = vehicleAvailJson.getString(JSON_PROP_SUPPREF);
        commonElemsJson.put(JSON_PROP_SUPP,  String.format("%s%s",suppID.substring(0, 1).toUpperCase(),suppID.substring(1).toLowerCase()));
        // TODO: Supplier market is hard-coded below. Where will this come from? This should be ideally come from supplier credentials.
        commonElemsJson.put(JSON_PROP_SUPPMARKET, "India");
        commonElemsJson.put("contractValidity","2017-12-30T00:00:00");
//        commonElemsJson.put("contractValidity", mDateFormat.format(new Date()));
        commonElemsJson.put(JSON_PROP_PRODNAME, PRODUCT_NAME_BRMS);
        commonElemsJson.put(JSON_PROP_CLIENTTYPE, "B2B");
        
       /* UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
        JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
        commonElemsJson.put(JSON_PROP_CLIENTTYPE, clientCtx.getString("clientType"));
        if (usrCtx != null) {
        	commonElemsJson.put(JSON_PROP_CLIENTNAME, (usrCtx != null) ? usrCtx.getClientName() : "");
        	List<ClientInfo> clientHierarchyList = usrCtx.getClientHierarchy();
        	if (clientHierarchyList != null && clientHierarchyList.size() > 0) {
        		ClientInfo clInfo = clientHierarchyList.get(clientHierarchyList.size() - 1);
        		if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
        			commonElemsJson.put(JSON_PROP_CLIENTGROUP, clInfo.getCommercialsEntityId());
        		}
        	}
        }*/
        
        // TODO: Properties for clientGroup, clientName are not yet set. Are these required for B2C? What will be BRMS behavior if these properties are not sent.
        commonElemsJson.put(JSON_PROP_CLIENTNAME, "Sitaram Travels");
        commonElemsJson.put(JSON_PROP_CLIENTGROUP, "Travel Agent");
        commonElemsJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_TRANSPORT);
        commonElemsJson.put(JSON_PROP_PRODCATEGSUBTYPE, "Cars");

        briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);

        JSONObject advDefnJson = new JSONObject();
        advDefnJson.put(JSON_PROP_SALESDATE,"2017-01-11T00:00:00");
        advDefnJson.put(JSON_PROP_TRAVELDATE, "2017-03-11T00:00:00");
        
//        advDefnJson.put("salesDate", mDateFormat.format(new Date()));
//        advDefnJson.put("travelDate", mDateFormat.format(new Date()));
        // TODO: How to set travelType?
        advDefnJson.put(JSON_PROP_TRAVELTYPE, "TT1");
        
//        advDefnJson.put("carType", "Direct");
        advDefnJson.put(JSON_PROP_CLIENTNATIONALITY, "Indian");
        // TODO: connectivitySupplierType hard-coded to 'CST1'. How should this value be assigned?
        advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "CST1");
        // TODO: What value to set for connectivitySupplier? For now, it is set to the same value as supplierID.
        //advDefnJson.put("connectivitySupplierName", resBody.getString("supplierRef"));
        advDefnJson.put(JSON_PROP_CONNECTSUPPNAME, "CSN1");
        // TODO: credentialsName hard-coded to 'Alamo'. This should come from product suppliers list in user context.
       
        advDefnJson.put(JSON_PROP_CREDSNAME, String.format("%s%s",suppID.substring(0, 1).toUpperCase(),suppID.substring(1).toLowerCase()));
        // TODO: bookingType hard-coded to 'Online'. How should this value be assigned?
        briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);
        
        JSONArray vehDtlsJsonArr = new JSONArray();
        briJson.put(JSON_PROP_VEHICLEDETAILS, vehDtlsJsonArr);
    
        return briJson;
    }

}
