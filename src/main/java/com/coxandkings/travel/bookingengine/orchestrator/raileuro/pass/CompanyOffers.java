package com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.raileuro.RailEuroConfig;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;

public class CompanyOffers {
	
	
private static final Logger logger = LogManager.getLogger(CompanyOffers.class);
public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T00:00:00'");
	
	public static void getCompanyOffers(JSONObject req, JSONObject res, OffersType invocationType)
	{
		
		JSONObject reqHdrJson = req.getJSONObject("requestHeader");
	    JSONObject reqBodyJson = req.getJSONObject("requestBody");
	    JSONObject clientCtxJson = reqHdrJson.getJSONObject("clientContext");
	    
	    JSONObject resBodyJson = res.getJSONObject("responseBody");
        String subType = req.getJSONObject("requestBody").optString("productCategorySubType");
        //OffersConfig offConfig = RailEuroConfig.getOffersConfig();
		//CommercialTypeConfig offTypeConfig = offConfig.getOfferTypeConfig(invocationType);
        ServiceConfig offTypeConfig = RailEuroConfig.getOffersTypeConfig(invocationType);
		
		JSONObject breCpnyOffReqJson = new JSONObject(new JSONTokener(offTypeConfig.getRequestJSONShell()));
		JSONObject root = breCpnyOffReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.rail_companyoffers.rail_companyoffers_wredemption.Root");
		
		
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		
		List<ClientInfo> clientCommHierarchyList = usrCtx.getClientHierarchy();
		String clientGroup = "";
		if (clientCommHierarchyList != null && clientCommHierarchyList.size() > 0) {
			ClientInfo clInfo = clientCommHierarchyList.get(clientCommHierarchyList.size() - 1);
			if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
				clientGroup = clInfo.getCommercialsEntityId();
			}
		}
		
		JSONObject clientDtlsJson = new JSONObject();
		clientDtlsJson.put("clientName", usrCtx.getClientName());
		clientDtlsJson.put("clientCategory", usrCtx.getClientCategory());
		clientDtlsJson.put("clientSubCategory", usrCtx.getClientSubCategory());
		clientDtlsJson.put("clientType", clientCtxJson.getString("clientType"));
		clientDtlsJson.put("pointOfSale", clientCtxJson.optString("pointOfSale", ""));
		clientDtlsJson.put("clientGroup", clientGroup);
		clientDtlsJson.put("nationality", clientCtxJson.getString("nationality"));
		
		OrgHierarchy orgHier = usrCtx.getOrganizationHierarchy();
		JSONObject cpnyDtlsJson = new JSONObject();
		cpnyDtlsJson.put("sbu", orgHier.getSBU());
		cpnyDtlsJson.put("bu", orgHier.getBU());
		cpnyDtlsJson.put("division", orgHier.getDivision());
		cpnyDtlsJson.put("salesOfficeLocation", orgHier.getSalesOfficeLoc());
		cpnyDtlsJson.put("salesOffice", orgHier.getSalesOfficeName());
		cpnyDtlsJson.put("companyMarket", clientCtxJson.getString("clientMarket"));
		
		JSONArray briJsonArr = new JSONArray();
	    JSONObject briJson = new JSONObject();
	    
	    //insert paymentDetails?
		
//		JSONObject paymentDetails = new JSONObject();
//		JSONObject cardDetails = new JSONObject();
//		paymentDetails.put("ModeOfPayment", "");
//		paymentDetails.put("paymentType", "Debit Card");
//		paymentDetails.put("bankName", "HDFC");
//		paymentDetails.put("cardDetails", cardDetails);
//		cardDetails.put("cardNo", "123456789");
//		cardDetails.put("cardType", "Visa");
//		cardDetails.put("nthBooking", "10");
	    JSONObject commonElemsJson = new JSONObject();
	    
	    commonElemsJson.put("bookingDate", DATE_FORMAT.format(new Date()));
	    commonElemsJson.put("journeyType", "return");
	    commonElemsJson.put("travelDate", DATE_FORMAT.format(new Date()));
	    commonElemsJson.put("supplierName", resBodyJson.optString("supplierRef"));
	    commonElemsJson.put("supplierName", "EUROPE");
	    
//	    briJson.put("paymentDetails", paymentDetails);
		briJson.put("companyDetails", cpnyDtlsJson);
    	briJson.put("clientDetails", clientDtlsJson);
    	briJson.put("commonElements", commonElemsJson);
	    
	    //-=-=-=-=-=-=-=--=-=-=--=--=-=-=-
	    // INSERT TARGET SET
	    //=-=-=-=-=-=-=-=-=-=-=-=-=--=-=--
	    
	    JSONArray passProductsArr = resBodyJson.getJSONArray("passProducts");
	    for (int j=0; j < passProductsArr.length(); j++) {
            JSONObject passProducts = passProductsArr.getJSONObject(j);
	    

	     

		
	}
	
	
	

}}
