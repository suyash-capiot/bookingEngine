package com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.raileuro.RailEuroConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.RailEuropeConstants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class SupplierCommercials implements RailEuropeConstants{
	
	private static final Logger logger = LogManager.getLogger(PassSearchProcessor.class);
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");
	
	public static JSONObject getSupplierCommercials(CommercialsOperation op, JSONObject req, JSONObject res)
	{
		 //CommercialsConfig commConfig = RailEuroConfig.getCommercialsConfig();
		 //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		 ServiceConfig commTypeConfig = RailEuroConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		 JSONObject breSupplierReq = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
		 
//		 JSONObject reqHeader = req.getJSONObject("requestHeader");
//	     JSONObject reqBody = req.getJSONObject("requestBody");
	     
	     JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
	     JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);
	     
	     JSONObject breHdrJson = new JSONObject();
	     breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
	     breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
	     breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
	     breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());
	     
	     JSONObject rootJson = breSupplierReq.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.rail_commercialscalculationengine.suppliertransactionalrules.Root");
	     rootJson.put(JSON_PROP_HEADER, breHdrJson);
	     
	     // BUSINESS RULE INTAKE
	     
	     JSONArray briJsonArr = new JSONArray();
	     
	     JSONObject briJson = new JSONObject();
	     
	     JSONObject commonElemsJson = new JSONObject();
	        String suppID = "RAILEUROPE";
	        commonElemsJson.put(JSON_PROP_SUPP, suppID);
	        // TODO: Supplier market is hard-coded below. Where will this come from? This should be ideally come from supplier credentials.
	        commonElemsJson.put(JSON_PROP_SUPPMARKET, "EUROPE");
	        commonElemsJson.put(JSON_PROP_CONTRACTVAL,DATE_FORMAT.format(new Date()));
	        commonElemsJson.put(JSON_PROP_GRPMODE, ""); //change
	        
	        
	        
//	        commonElemsJson.put("contractValidity", mDateFormat.format(new Date()));
	        
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
	        
	     // TODO: clientName and clientGroup
	        
	        briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
	        
	        JSONObject advDefnJson = new JSONObject();
	        
	        advDefnJson.put(JSON_PROP_SALESDATE,DATE_FORMAT.format(new Date()));
	        advDefnJson.put(JSON_PROP_TRAVELDATE, DATE_FORMAT.format(new Date()));
	        
//	        advDefnJson.put("salesDate", mDateFormat.format(new Date()));
//	       advDefnJson.put("travelDate", mDateFormat.format(new Date()));
	        // TODO: no travel type, connectivitySupplierType, connectivitySupplier, credentialsName, bookingType for PASS (CHECK!!)
	        advDefnJson.put(JSON_PROP_TRAVELTYPE, "");
	        advDefnJson.put(JSON_PROP_CLIENTNATIONALITY, "");
	        advDefnJson.put(JSON_PROP_BOOKINGTYPE, "");
	        advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "CST1");
	        advDefnJson.put(JSON_PROP_CONNECTSUPPNAME, "CSN1");
	       
//	        advDefnJson.put("credentialsName", String.format("%s%s",suppID.substring(0, 1).toUpperCase(),suppID.substring(1).toLowerCase()));
	        
	        advDefnJson.put(JSON_PROP_CREDSNAME, suppID);  //change
	        briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);
	        
	        getTrainDetails(resBody, suppID, briJson);
	        
	        briJsonArr.put(briJson);
	        
	        rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
	        JSONObject breSuppResJson = null;
//        	System.out.println("SupplierCommercialRequestCCE" + breSupplierReq);

	        try {
//	            breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS/SuppComm", commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breSupplierReq);
//	            breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS/SuppComm", commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), "POST", 120000, breSupplierReq);
	        	breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS/SuppComm", commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), "POST", 120000, breSupplierReq);
	        }
	        catch (Exception x) {
	        	logger.warn("An exception occurred when calling supplier commercials", x);
	        }
//        	System.out.println("SupplierCommercialResponseCCE" + breSuppResJson);

	        return breSuppResJson;
	        
	        
	     
	}


	public static void getTrainDetails(JSONObject resBody, String suppID, JSONObject briJson) {
		JSONArray passProductsArr = new JSONArray();
		passProductsArr = resBody.getJSONArray(JSON_PROP_PASSPROD);
//		System.out.println(resBody);

		JSONArray trainDetailsArr = new JSONArray();
		
		for (int i=0; i < passProductsArr.length(); i++)
		{
			JSONObject passProduct = passProductsArr.getJSONObject(i);
			JSONObject trainDetails = new JSONObject();
			JSONObject paxInfo = passProduct.optJSONObject(JSON_PROP_PAXINFO);
			JSONObject pricingInfo = passProduct.getJSONObject(JSON_PROP_PRICINGINFO);
			trainDetails.put(JSON_PROP_PRODCATEG, PROD_CATEG_TRANSPORT);
			trainDetails.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_RAILEURO);
//	        	trainDetails.put("trainNumber", "320");
//	        	trainDetails.put("trainCategory", "Exp");
			trainDetails.put(JSON_PROP_PRODUCTID, passProduct.optString(JSON_PROP_PRODUCTID));
			trainDetails.put(JSON_PROP_PASSNAME, passProduct.optString(JSON_PROP_FAMILYNAME));
			trainDetails.put(JSON_PROP_PRODNAME, passProduct.optString(JSON_PROP_PRODNAME));
			trainDetails.put(JSON_PROP_NUMBEROFPAX, passProduct.optString("JSON_PROP_COUNT"));
			JSONArray countriesCoveredArr = passProduct.getJSONArray(JSON_PROP_COUNTRIESCOVERED);
			Set<String> countries = new HashSet<String>();
			for(int j=0;j<countriesCoveredArr.length();j++){
				JSONObject temp = countriesCoveredArr.getJSONObject(j);
				countries.add(temp.getString(JSON_PROP_COUNTRYNAME));
				trainDetails.put(JSON_PROP_COUNTRY, countries);
			}
			//populate PAXDETAILS
			if(paxInfo!=null)
				getPassengerDetails(trainDetails, paxInfo, pricingInfo);
			trainDetailsArr.put(trainDetails);
			briJson.put(JSON_PROP_TRAINDETAILS, trainDetailsArr);
		}
	}


	public static void getPassengerDetails(JSONObject trainDetails, JSONObject paxInfo, JSONObject pricingInfo) {
		JSONArray passengerDetailsArr = new JSONArray();
		JSONObject passengerDetails = new JSONObject();
		passengerDetails.put(JSON_PROP_PAXTYPE, paxInfo.optString(JSON_PROP_PAXTYPE));
		passengerDetails.put(JSON_PROP_SEATCLASS, "3A");
		passengerDetails.put(JSON_PROP_TOTALFARE, pricingInfo.getJSONObject(JSON_PROP_TOTALFARE).optString(JSON_PROP_AMOUNT));
		passengerDetailsArr.put(passengerDetails);
		trainDetails.put(JSON_PROP_PASSENGERDET, passengerDetailsArr);
	}
	
	
	public static String CurrentDateTime()  
		{
		   DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:ss");  
		   LocalDateTime now = LocalDateTime.now();  
		return dtf.format(now);  
		}    
	

}
