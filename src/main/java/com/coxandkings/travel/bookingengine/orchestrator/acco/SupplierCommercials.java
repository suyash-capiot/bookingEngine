package com.coxandkings.travel.bookingengine.orchestrator.acco;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
//import com.coxandkings.travel.bookingengine.utils.RedisCityData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisCityData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisHotelData;

public class SupplierCommercials implements AccoConstants{

	private static final Logger logger = LogManager.getLogger(AccoSearchProcessor.class);

	public static JSONObject getSupplierCommercials(CommercialsOperation op,JSONObject req, JSONObject res, Map<Integer,String> SI2BRMSRoomMap) {
		
		//CommercialsConfig commConfig = AccoConfig.getCommercialsConfig();
		//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		//ServiceConfig commTypeConfig = AccoConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		ServiceConfig commTypeConfig = AccoConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		JSONObject breSuppReqJson = new JSONObject(commTypeConfig.getRequestJSONShell());

		JSONObject reqHeader = req.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);
		JSONObject clntCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);

		JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);

		UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
		JSONObject breHdrJson = new JSONObject();
		breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
		breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
		breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
		breHdrJson.put(JSON_PROP_OPERATIONNAME, op.toString());

		JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert")
				.getJSONObject("object")
				.getJSONObject("cnk.acco_commercialscalculationengine.suppliertransactionalrules.Root");
		
		JSONArray briJsonArr = new JSONArray();
		rootJson.put(JSON_PROP_HEADER, breHdrJson);
		rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
		
		int briIndex=0,hotelIndex = 0;
		String prevSuppId,suppId,cityName,hotelCode;
		JSONObject briJson = null,commonElemsJson,advDefnJson,hotelDetailsJson,roomDetailsJson,subReqBody,accoInfoJson;
		Map<String, Object> cityAttrs;
		Map<String, Object> hotelAttrs;
		Map<String, JSONObject> hotelMap = null;
		Map<String, Integer> hotelIndexMap = null;
		JSONArray multiResArr = resBody.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		int roomIdx=0;
		
		//Fetch clientGroup
		/*List<ClientInfo> clientCommHierarchyList = usrCtx.getClientHierarchy();
		String clientGroup = "";
		if (clientCommHierarchyList != null && clientCommHierarchyList.size() > 0) {
			ClientInfo clInfo = clientCommHierarchyList.get(clientCommHierarchyList.size() - 1);
			if (clInfo.getCommercialsEntityType() == ClientInfo.CommercialsEntityType.ClientGroup) {
				clientGroup = clInfo.getCommercialsEntityId();
			}
		}*/
		
		for(int i=0;i<multiResArr.length();i++) {
			
			accoInfoJson = (JSONObject) multiResArr.get(i);
			subReqBody = reqBody.has(JSON_PROP_ACCOMODATIONARR)?(JSONObject) reqBody.getJSONArray(JSON_PROP_ACCOMODATIONARR).get(i):reqBody;
			//briIndex = briJsonArr.length();
			prevSuppId = DEF_SUPPID;
			
			for(Object roomStayJson: accoInfoJson.getJSONArray(JSON_PROP_ROOMSTAYARR)) {
				
				suppId = ((JSONObject)roomStayJson).getString(JSON_PROP_SUPPREF);
				hotelCode = ((JSONObject) ((JSONObject) ((JSONObject)roomStayJson).get(JSON_PROP_ROOMINFO)).get(JSON_PROP_HOTELINFO)).getString(JSON_PROP_HOTELCODE);
				
				if(!prevSuppId.equals(suppId)) {
					
					prevSuppId = suppId;
					hotelMap = new HashMap<String,JSONObject>();
					hotelIndexMap = new HashMap<String,Integer>();
					hotelIndex = 0;
					briJson = new JSONObject();
					
					commonElemsJson = new JSONObject();
					commonElemsJson.put(JSON_PROP_SUPP, suppId);
	                // TODO: Supplier market is hard-coded below. Where will this come from?
					commonElemsJson.put(JSON_PROP_SUPPMARKET, clntCtx.getString(JSON_PROP_CLIENTMARKET));
					commonElemsJson.put("contractValidity", DATE_FORMAT.format(new Date()));
					//TODO:get from enum
					commonElemsJson.put(JSON_PROP_PRODCATEGSUBTYPE, ((JSONObject) roomStayJson).getString(JSON_PROP_ACCOSUBTYPE));
					// TODO: Hard-coded value. This should be set from client context.Check if
					// required in acco or no
					commonElemsJson.put(JSON_PROP_CLIENTTYPE, clntCtx.getString(JSON_PROP_CLIENTTYPE));
					commonElemsJson.put("companyId", usrCtx.getSupplierForProduct(PROD_CATEG_ACCO, ((JSONObject) roomStayJson).getString(JSON_PROP_ACCOSUBTYPE), suppId).getmCompanyId());
					// TODO: Properties for clientGroup, clientName, iatanumber are not yet set. Are
					// these required for B2C? What will be BRMS behavior if these properties are  not sent.
		
					String clGrpID="";
					if(ClientType.B2B == ClientType.valueOf(clntCtx.getString(JSON_PROP_CLIENTTYPE)))
						clGrpID=MDMUtils.getClientGroupProductSuppliers(clntCtx.getString(JSON_PROP_CLIENTID));
					
					commonElemsJson.put(JSON_PROP_CLIENTGROUP, clGrpID);
					
					advDefnJson = new JSONObject();
					advDefnJson.put("travelCheckInDate", subReqBody.getString(JSON_PROP_CHKIN).concat("T00:00:00"));
					advDefnJson.put("travelCheckOutDate", subReqBody.getString(JSON_PROP_CHKOUT).concat("T00:00:00"));
					// TODO: Significance of this data in acco.Is it same as the check in date or
					// date of search
					advDefnJson.put("salesDate", DATE_FORMAT.format(new Date()));
					// TODO: is this the default value for all.Which other values are supported and
					// where will it come from
					advDefnJson.put("bookingType", "Online");
					// TODO: Is this the same as client type.If yes, set from client context
					advDefnJson.put(JSON_PROP_CREDSNAME, usrCtx.getSupplierForProduct(PROD_CATEG_ACCO, ((JSONObject) roomStayJson).getString(JSON_PROP_ACCOSUBTYPE), suppId).getCredentialsName());
					// TODO: Is this expected from req? This data is also needed by some supplier?
					// Will it be provided by wem
					advDefnJson.put(JSON_PROP_NATIONALITY, "Indian");
					// TODO: city country continent and state mapping should be there iin adv def

					//TODO:is empty default requied?data should be present in redis
					cityName = RedisHotelData.getHotelInfo(hotelCode, JSON_PROP_CITY);
					cityAttrs = RedisCityData.getCityInfo(cityName);
					advDefnJson.put(JSON_PROP_CONTINENT, cityAttrs.getOrDefault(JSON_PROP_CONTINENT,""));
					advDefnJson.put(JSON_PROP_COUNTRY, cityAttrs.getOrDefault(JSON_PROP_COUNTRY,""));
					advDefnJson.put(JSON_PROP_CITY, cityName);
					advDefnJson.put(JSON_PROP_STATE, cityAttrs.getOrDefault(JSON_PROP_STATE,""));

					briJson.put(JSON_PROP_COMMONELEMS, commonElemsJson);
					briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);
					
					briJsonArr.put(briIndex++,briJson);
				}
				
				hotelDetailsJson = hotelMap.get(hotelCode);
				if (hotelDetailsJson == null) {
					
					hotelDetailsJson = new JSONObject();
					
					hotelAttrs = RedisHotelData.getHotelInfo(hotelCode);
					hotelDetailsJson.put("productName", hotelAttrs.getOrDefault("name", ""));
					hotelDetailsJson.put("productBrand", hotelAttrs.getOrDefault("brand", ""));
					hotelDetailsJson.put("productChain", hotelAttrs.getOrDefault("chain", ""));
					
	                briJson.append(JSON_PROP_HOTELDETAILS, hotelDetailsJson);
					hotelMap.put(hotelCode, hotelDetailsJson);
					hotelIndexMap.put(hotelCode, hotelIndex++);
				}
				
				roomDetailsJson = getBRMSRoomDetailsJSON((JSONObject) roomStayJson);
				hotelDetailsJson.append(JSON_PROP_ROOMDETAILS, roomDetailsJson);
				//this is done so that while calculating prices from client response finding a particular room becomes efficient
				SI2BRMSRoomMap.put(roomIdx++,String.format("%s%c%s%c%s",briIndex-1,KEYSEPARATOR,hotelIndexMap.get(hotelCode),KEYSEPARATOR,hotelDetailsJson.getJSONArray("roomDetails").length()-1));
			}
		}
		
		// Ops Amendments Block
		if (reqBody.has("opsAmendments") /*&& reqBody.getJSONObject("opsAmendments").getString("actionItem")
				.equalsIgnoreCase("amendSupplierCommercials")*/) {
			JSONObject opsAmendmentJson = reqBody.getJSONObject("opsAmendments");
			for (int briIdx = 0; briIdx < briJsonArr.length(); briIdx++) {
				JSONObject bri = briJsonArr.getJSONObject(briIdx);
				if (bri.getJSONObject(JSON_PROP_COMMONELEMS).getString(JSON_PROP_SUPP)
						.equals(opsAmendmentJson.getString("supplierId"))) {
					JSONArray hotelDetailsJsonArr = bri.getJSONArray(JSON_PROP_HOTELDETAILS);
					for (int hotelIdx = 0; hotelIdx < hotelDetailsJsonArr.length(); hotelIdx++) {
						JSONObject hotelDetails = hotelDetailsJsonArr.getJSONObject(hotelIdx);
						if (hotelDetails.getString("productName").equals(opsAmendmentJson.getString("hotelName"))) {
							JSONArray roomDetailsJsonArr = hotelDetails.getJSONArray(JSON_PROP_ROOMDETAILS);
							for (int Idx = 0; Idx < roomDetailsJsonArr.length(); Idx++) {
								JSONObject roomDetails = roomDetailsJsonArr.getJSONObject(Idx);
								if (roomDetails.get("roomType")
										.equals(opsAmendmentJson.getString(JSON_PROP_ROOMTYPENAME))
										&& roomDetails.get("roomCategory")
												.equals(opsAmendmentJson.getString(JSON_PROP_ROOMCATEGNAME))
										&& roomDetails.get("rateType")
												.equals(opsAmendmentJson.getString(JSON_PROP_RATEPLANNAME))
										&& roomDetails.get("rateCode")
												.equals(opsAmendmentJson.getString(JSON_PROP_RATEPLANCODE))) {
									roomDetails.put("bookingId", opsAmendmentJson.getString("bookingId"));
									roomDetails.put("ineligibleCommercials",opsAmendmentJson.getJSONArray("ineligibleCommercials"));
								}
							}
						}
					}

				}
			}
		}

		
		JSONObject breSuppResJson = null;
		try {
			//long start = System.currentTimeMillis();
			//breSuppResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(),breSuppReqJson);
			breSuppResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_SUPPCOMM, commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(),breSuppReqJson);
			//logger.info(String.format("Time taken to get supplier commercials response : %s ms", System.currentTimeMillis()-start));
		} catch (Exception x) {
			logger.warn("An exception occurred when calling supplier commercials", x);
		}
		
		return breSuppResJson;
	}

	public static JSONObject getBRMSRoomDetailsJSON(JSONObject roomStayJson) {
		
		JSONObject roomDetailsJson = new JSONObject();
		JSONObject roomInfoJson = roomStayJson.getJSONObject(JSON_PROP_ROOMINFO);
		
		JSONObject roomTypeJson = roomInfoJson.getJSONObject(JSON_PROP_ROOMTYPEINFO);
		//TODO:should codes come here
		roomDetailsJson.put("roomType",roomTypeJson.get(JSON_PROP_ROOMTYPENAME));
		//TODO:this data is taken from si as it is not found in mat's system.Ask mdm if room name has single mapping to room category
		//If yes take from mdm
		roomDetailsJson.put("roomCategory",roomTypeJson.get(JSON_PROP_ROOMCATEGNAME));
		
		JSONObject ratePlanJson = roomInfoJson.getJSONObject(JSON_PROP_RATEPLANINFO);
		//TODO:Will SI lookup this?If not,where this data will come from
		roomDetailsJson.put("rateType", ratePlanJson.get(JSON_PROP_RATEPLANNAME));
		roomDetailsJson.put("rateCode", ratePlanJson.get(JSON_PROP_RATEPLANCODE));
		//TODO:This hack is rght?
		//roomDetailsJson.put("bookingEngineKey", roomIndex);
		
		// TODO:Yet to add passenger type.If passenger type is taken from req, how to handle multiroom
		// case? ask SI to map in response

		JSONObject totalPriceJson = (JSONObject) roomStayJson.get(JSON_PROP_ROOMPRICE);
		BigDecimal totalPrice = totalPriceJson.getBigDecimal(JSON_PROP_AMOUNT);
		roomDetailsJson.put("totalFare", String.valueOf(totalPrice));

		JSONObject taxesJson = totalPriceJson.optJSONObject(JSON_PROP_TOTALTAX);
		//farebreakup will not be a part of req if null
		if(taxesJson!=null && taxesJson.has(JSON_PROP_AMOUNT)) {
			JSONObject fareBreakupJson = new JSONObject();
			fareBreakupJson.put("baseFare", totalPrice.subtract(taxesJson.getBigDecimal(JSON_PROP_AMOUNT)));
			JSONArray taxArr = taxesJson.optJSONArray(JSON_PROP_TAXBRKPARR);
			if(taxArr!=null) {
				JSONArray taxDetailsArr = new JSONArray();
				JSONObject taxDetailJson;
				for(Object tax:taxArr) {
					taxDetailJson = new JSONObject();
					//TODO:Will SI standardized these codes
					taxDetailJson.put("taxName", ((JSONObject) tax).getString(JSON_PROP_TAXCODE));
					taxDetailJson.put("taxValue", ((JSONObject) tax).getBigDecimal(JSON_PROP_AMOUNT));
					taxDetailsArr.put(taxDetailJson);
				}
				fareBreakupJson.put("taxDetails",taxDetailsArr);
			}
			roomDetailsJson.put("fareBreakUp", fareBreakupJson);
		}

		return roomDetailsJson;

	}
	
}
