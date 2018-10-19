package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisHotelData;


public class AccoAmClCompanyPolicy implements AccoConstants{

	private static final Logger logger = LogManager.getLogger(AccoAmClCompanyPolicy.class);
	
	public static JSONObject getAmClCompanyPolicy(CommercialsOperation op, JSONObject req, JSONObject res, JSONArray productsArr) throws Exception{
	//CommercialsConfig commConfig = AccoConfig.getCommercialsConfig();
    //CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_COMPANY_POLICIES);
	//ServiceConfig commTypeConfig = AccoConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_COMPANY_POLICIES);
	ServiceConfig commTypeConfig = AccoConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_COMPANY_POLICIES);
    JSONObject breCompPolicyReq = new JSONObject(new JSONTokener(commTypeConfig.getRequestJSONShell()));
    
    JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
    JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);

    JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
    JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);
    
    JSONObject breHdrJson = new JSONObject();
    breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
    breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
    breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
    breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());

    JSONObject rootJson = breCompPolicyReq.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.acco_companypolicy.Root");
    JSONArray briJsonArr = new JSONArray();
    rootJson.put(JSON_PROP_HEADER, breHdrJson);
    rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
    JSONArray resAccoInfoArr = resBody.getJSONArray(JSON_PROP_ACCOMODATIONARR);
    JSONArray accoInfoArr = reqBody.getJSONArray(JSON_PROP_ACCOMODATIONARR);
    JSONObject orderDetail = null;
   
    
    for (int i=0; i < accoInfoArr.length(); i++) {
    	 JSONObject briJson = new JSONObject();
    	 JSONObject reqAccoInfoObj = accoInfoArr.getJSONObject(i);
    	 JSONObject resAccoInfoObj = resAccoInfoArr.getJSONObject(i);
         if(resAccoInfoObj.has("errorMsg")){
        	 continue;
         }
         String orderId = reqAccoInfoObj.getString("orderID");
         for(Object order : productsArr) {
         	if(orderId.equals(((JSONObject) order).getString("orderID"))){
         		orderDetail = (JSONObject) order;
         	}
         }
         if(orderDetail == null) {
         	logger.debug(String.format("Order Details for orderID: %s not found", orderId));
         }
         briJson = createBusinessRuleIntakeForSupplier(reqHeader, reqAccoInfoObj, orderDetail,resAccoInfoObj);
         briJsonArr.put(briJson);
    }
    JSONObject companyPolicyRes = null;
    try {
        //companyPolicyRes = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_COMPANYPLCY, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breCompPolicyReq);
    	companyPolicyRes = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_COMPANYPLCY, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breCompPolicyReq);
    }
    catch (Exception x) {
        logger.warn("An exception occurred when calling supplier commercials", x);
    }
    return companyPolicyRes;
	}

   private static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqAccoInfoObj,JSONObject orderDetail,JSONObject resAccoInfoObj) {
		JSONObject briJson = new JSONObject();
        JSONObject commonElemsJson = new JSONObject();
        UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
        JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
        
        commonElemsJson.put(JSON_PROP_COMPANYMKT, clientCtx.optString(JSON_PROP_CLIENTMARKET, ""));
        commonElemsJson.put("contractValidity", DATE_FORMAT.format(new Date()));
        commonElemsJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_ACCO);
        commonElemsJson.put(JSON_PROP_PRODCATEGSUBTYPE, reqAccoInfoObj.getString(JSON_PROP_ACCOSUBTYPE));
        commonElemsJson.put(JSON_PROP_ENTITYTYPE, clientCtx.getString(JSON_PROP_CLIENTTYPE));
        commonElemsJson.put(JSON_PROP_ENTITYNAME, (usrCtx != null) ? usrCtx.getClientName() : "");
    	
        briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
        
        JSONObject advDefnJson = new JSONObject();
        
        advDefnJson.put(JSON_PROP_TRAVELDATE, getTravelDateFromResponse(reqAccoInfoObj));
        advDefnJson.put(JSON_PROP_BOOKINGDATE, DATE_FORMAT.format(new Date()));
        advDefnJson.put("transactionDate", DATE_FORMAT.format(new Date()));
        advDefnJson.put("ancillaryName","ancillaryName");
        advDefnJson.put("ancillaryType", "ancillaryType");
        
        String hotelCode = reqAccoInfoObj.getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_HOTELINFO).getString(JSON_PROP_HOTELCODE);
        String cityName = RedisHotelData.getHotelInfo(hotelCode, JSON_PROP_CITY);
		Map<String, Object> cityAttrs = RedisCityData.getCityInfo(cityName);
		advDefnJson.put(JSON_PROP_CONTINENT, cityAttrs.getOrDefault(JSON_PROP_CONTINENT,""));
		advDefnJson.put(JSON_PROP_COUNTRY, cityAttrs.getOrDefault(JSON_PROP_COUNTRY,""));
		advDefnJson.put(JSON_PROP_CITY, cityName);
        advDefnJson.put(JSON_PROP_NATIONALITY, clientCtx.getString(JSON_PROP_CLIENTMARKET));
        briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);
        
        JSONObject supplierPolicy = new JSONObject();
       
        //TODO : To check from where to get this value.
		supplierPolicy.put("supplierBufferPeriod", 2);
		//supplier charge amount is sent here
		supplierPolicy.put("policyAmount", resAccoInfoObj.getBigDecimal("supplierCharges"));
		briJson.put("supplierPolicyDetails", supplierPolicy);	
		 
		//TODD : To discuss how to set these values ?
		JSONObject policyDtls = new JSONObject();
		policyDtls.put("policyType", "Cancellation Terms");
		policyDtls.put("policyCategory", "Cancellation Terms");
		policyDtls.put("policyName", "AB");
		briJson.put("companyPolicyDetails", policyDtls);	
		
        
        JSONArray htlDtlsJsonArr = new JSONArray();
        briJson.put(JSON_PROP_HOTELDETAILS, htlDtlsJsonArr);
        briJson.getJSONArray(JSON_PROP_HOTELDETAILS).put(getBRMSHotelDetailJson(reqAccoInfoObj,orderDetail,hotelCode));
        return briJson;
	}

	private static JSONObject getBRMSHotelDetailJson(JSONObject reqAccoInfoObj, JSONObject orderDetail,String hotelCode) {
		
		//JSONObject roomInfo=reqAccoInfoObj.getJSONObject(JSON_PROP_ROOMINFO);
		String modType = reqAccoInfoObj.getString("modificationType");
		BigDecimal roomPrice;
		JSONArray dbRoomsArr = orderDetail.getJSONObject("orderDetails").getJSONObject("hotelDetails").getJSONArray("rooms");
		if(dbRoomsArr==null || dbRoomsArr.length()==0) {
			logger.warn(String.format("No rooms found in db for orderID %s",reqAccoInfoObj.getString("orderID")));
		}
		//add null check
		JSONObject dbroomDetailObj = new JSONObject();
		if(!("FULLCANCELLATION".equals(modType)) && !("CHANGEPERIODOFSTAY".equals(modType))) { 
		String roomID = reqAccoInfoObj.getString("roomID");
		//JSONObject dbroomDetailObj = null;
		for(Object room : dbRoomsArr) {
	         	if(roomID.equals(((JSONObject) room).getString("roomID"))){
	         		//dbroomDetailObj = (JSONObject) room;
	         		dbroomDetailObj.append("rooms", (JSONObject) room);
	         	}
	         }
		/*if(dbroomDetailObj==null) {
			logger.debug(String.format("Room Details for roomID: %s not found", roomID));
		}*/
		}
		else {
			dbroomDetailObj.put("rooms", dbRoomsArr);
		}
		
		
		JSONObject hotelDetailObj=new JSONObject();
		Map<String, Object> hotelAttrs = RedisHotelData.getHotelInfo(hotelCode);
		hotelDetailObj.put("productName", hotelAttrs.getOrDefault("name", ""));
		hotelDetailObj.put("productBrand", hotelAttrs.getOrDefault("brand", ""));
		hotelDetailObj.put("productChain", hotelAttrs.getOrDefault("chain", ""));
		
		if(dbroomDetailObj!=null) {
		for(int i=0;i<dbroomDetailObj.getJSONArray("rooms").length();i++){
		JSONObject dbRoom = dbroomDetailObj.getJSONArray("rooms").getJSONObject(i);
		JSONObject roomDetailObj=new JSONObject();
		roomDetailObj.put("roomCategory", dbRoom.getJSONObject("roomTypeInfo").getString("roomCategoryName"));
		roomDetailObj.put("roomType", dbRoom.getJSONObject("roomTypeInfo").getString("roomTypeName"));
		roomDetailObj.put("mealPlan", dbRoom.getJSONObject(JSON_PROP_MEALINFO).getString("mealID"));
		//what is the difference between rate Type and rate Code
		roomDetailObj.put("rateType", dbRoom.getJSONObject(JSON_PROP_RATEPLANINFO).getString(JSON_PROP_RATEPLANCODE));
		roomDetailObj.put("rateCode", dbRoom.getJSONObject(JSON_PROP_RATEPLANINFO).getString(JSON_PROP_RATEPLANCODE));
		roomDetailObj.put("rating", "");
		
		
		if(modType.endsWith("PASSENGER"))
		    roomDetailObj.put("applicableOn", "per passenger");
		else if(modType.endsWith("ROOM"))
			roomDetailObj.put("applicableOn", "per room");
		else
			roomDetailObj.put("applicableOn", "per night");
		
		//Todo:Offers not yet put in DB.So need to fetch this from database 
		roomDetailObj.put("companyOffers", new JSONArray());
		
		//TODO:no clarity about what to be sent in this.(Recievable's or payable's)
		JSONArray clientComm=new JSONArray();
		
		roomDetailObj.put("companyCommercialHead", clientComm);
		JSONArray dbClientComm = dbRoom.getJSONArray("clientCommercials");
		if(dbClientComm!=null && dbClientComm.length()!=0) {
		for(int t=0;t<dbClientComm.length();t++) {
			JSONObject dbClientCommObj = dbClientComm.getJSONObject(t);
			JSONArray dbClientCommInnrArr = dbClientCommObj.getJSONArray("clientCommercials");
			for(Object dbClientCommInnrObj:dbClientCommInnrArr) {
				JSONObject clientCommObj=new JSONObject();
				clientCommObj.put("commercialName", ((JSONObject) dbClientCommInnrObj).getString("commercialName"));
				clientCommObj.put("commercialValue", ((JSONObject) dbClientCommInnrObj).optBigDecimal("commercialAmount",new BigDecimal(0)));
				clientComm.put(clientCommObj);
			}
		  }
		}
		//Only one room comes in one request
		hotelDetailObj.append("roomDetails", roomDetailObj);
		
		//if(dbroomDetailObj!=null) {
		BigDecimal cmpnyTaxAmt=new BigDecimal(0);
		JSONObject totalPriceInfoObj = dbroomDetailObj.getJSONArray("rooms").getJSONObject(i).getJSONObject(JSON_PROP_ROOMPRICE);
		JSONObject taxesJson=totalPriceInfoObj.getJSONObject(JSON_PROP_TOTALTAX);
		JSONArray taxesJsonArr=taxesJson.optJSONArray(JSON_PROP_TAXBRKPARR);
		JSONObject cmpnyTaxesObj=totalPriceInfoObj.optJSONObject("companyTaxes");
		JSONObject rcvblsJson=totalPriceInfoObj.optJSONObject("receivables");
		String totalPrice=totalPriceInfoObj.getString("totalPrice");
		if(cmpnyTaxesObj!=null) {
		cmpnyTaxAmt=cmpnyTaxesObj.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO);
		}
		roomPrice=Utils.convertToBigDecimal(totalPrice, 0);
        roomDetailObj.put("totalFare",roomPrice);
		
        //add base fare
		JSONObject fareBrkpJson = new JSONObject();
		fareBrkpJson.put("sellingPriceComponentName", "Basic");
		fareBrkpJson.put("sellingPriceComponentValue", roomPrice.subtract(taxesJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO).subtract(cmpnyTaxAmt)));
		roomDetailObj.append("fareDetails", fareBrkpJson);

		//add taxes
		if(taxesJsonArr!=null) {
			for(Object taxJson:taxesJsonArr) {
				fareBrkpJson = new JSONObject();
				fareBrkpJson.put("sellingPriceComponentName", ((JSONObject) taxJson).getString(JSON_PROP_TAXCODE));
				fareBrkpJson.put("sellingPriceComponentValue",((JSONObject) taxJson).getBigDecimal(JSON_PROP_AMOUNT));
				roomDetailObj.append("fareDetails", fareBrkpJson);
			}
		}

		//add receivables
		if(rcvblsJson!=null) {
		for(Object rcvblJson:rcvblsJson.getJSONArray(JSON_PROP_RECEIVABLE)) {
			fareBrkpJson = new JSONObject();
			fareBrkpJson.put("sellingPriceComponentName", ((JSONObject) rcvblJson).getString(JSON_PROP_CODE));
			fareBrkpJson.put("sellingPriceComponentValue",((JSONObject) rcvblJson).getBigDecimal(JSON_PROP_AMOUNT));
			roomDetailObj.append("fareDetails", fareBrkpJson);
		}
		}
		}
		}
		 return hotelDetailObj;
	}

	private static String getTravelDateFromResponse(JSONObject reqAccoInfoObj) {
		String chckIn = reqAccoInfoObj.getString(JSON_PROP_CHKIN);
		if (chckIn == null || chckIn.isEmpty()) {
			return "";
		}
		return chckIn.concat("T00:00:00");
	}
}
