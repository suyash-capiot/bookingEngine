package com.coxandkings.travel.bookingengine.orchestrator.rail;

import java.util.Date;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.rail.RailConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class SupplierCommercials implements RailConstants {

	private static final Logger logger = LogManager.getLogger(RailSearchProcessor.class);

	public static JSONObject getSupplierCommercials(JSONObject req, JSONObject res, Map<String,JSONObject> brmsToSIClassMap) {

		//CommercialsConfig commConfig = RailConfig.getCommercialsConfig();
		//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		ServiceConfig commTypeConfig = RailConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		JSONObject breSuppReqJson = new JSONObject(commTypeConfig.getRequestJSONShell());

		JSONObject reqBody = req.getJSONObject(JSON_PROP_REQBODY);

		JSONObject resHeader = res.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBody = res.getJSONObject(JSON_PROP_RESBODY);

		JSONObject breHdrJson = new JSONObject();
		breHdrJson.put(JSON_PROP_SESSIONID, resHeader.getString(JSON_PROP_SESSIONID));
		breHdrJson.put(JSON_PROP_TRANSACTID, resHeader.getString(JSON_PROP_TRANSACTID));
		breHdrJson.put(JSON_PROP_USERID, resHeader.getString(JSON_PROP_USERID));
		breHdrJson.put(JSON_PROP_OPERATIONNAME, "Search");

		JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert")
				.getJSONObject("object")
				.getJSONObject("cnk.rail_commercialscalculationengine.suppliertransactionalrules.Root");
		rootJson.put(JSON_PROP_BRMSHEADER, breHdrJson);

		JSONArray briJsonArr = new JSONArray();
		JSONArray trainDetailsArr = new JSONArray();
		JSONObject briJson = new JSONObject();
		JSONObject commonElemJson = getCommonElements(resBody);
		JSONObject advanceDefJson = getAdvanceDef(reqBody, resBody);
		briJson.put(JSON_PROP_COMMONELEMS, commonElemJson);
		briJson.put(JSON_PROP_ADVANCEDEF, advanceDefJson);
		briJson.put(JSON_PROP_TRAINDETAILS, trainDetailsArr);
		briJsonArr.put(briJson);
		rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
		
		String supp=null;

		JSONArray originDestArr = resBody.getJSONArray(JSON_PROP_ORIGINDESTOPTS);
		int orgDestLen = originDestArr.length();
		for (int i = 0; i < orgDestLen; i++) {
			JSONObject originDestObj = originDestArr.getJSONObject(i);
			supp=originDestObj.getString(JSON_PROP_SUPPREF);
			JSONObject trainInfo = originDestObj.getJSONObject(JSON_PROP_TRAINDETAILS);
			JSONObject trainDetailsObj = getTrainDetails(originDestObj, trainInfo);
			trainDetailsArr.put(trainDetailsObj);
			JSONArray paxDetails = new JSONArray();
			JSONArray availClassArr = originDestObj.getJSONArray(JSON_PROP_CLASSAVAIL);
			trainDetailsObj.put("passengerDetails", paxDetails);
			int availClassLen = availClassArr.length();
			
			for (int j = 0; j < availClassLen; j++) {
				JSONObject availClassObj = availClassArr.getJSONObject(j);
				JSONObject pricing = availClassObj.getJSONObject(JSON_PROP_PRICING);
				JSONObject passenger = new JSONObject();
				passenger.put("seatClass", availClassObj.getString(JSON_PROP_RESERVATIONCLASS));
				passenger.put(JSON_PROP_TOTAL, pricing.getJSONObject(JSON_PROP_TOTAL).getBigDecimal(JSON_PROP_AMOUNT));
				JSONObject fareBrkup = getFareBreakup(pricing.getJSONObject(JSON_PROP_FAREBREAKUP));
				passenger.put("fareBreakUp", fareBrkup);
				paxDetails.put(passenger);
				
				brmsToSIClassMap.put(String.format("%s%s%s", trainDetailsObj.get(JSON_PROP_TRAINNUM),"|",j), availClassObj);
			}
			
		}
		commonElemJson.put("supplier", supp);
		// TODO hard coded value=IRCTC. From where will this value come? What is it's significance?
		advanceDefJson.put("credentialsName",supp);

		JSONObject breSuppResJson = null;
		try {
			logger.info("Before opening HttpURLConnection to BRMS for Supplier Commercials");
			//breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS", commTypeConfig.getServiceURL(), commConfig.getHttpHeaders(), breSuppReqJson);
			breSuppResJson = HTTPServiceConsumer.consumeJSONService("BRMS", commTypeConfig.getServiceURL(), commTypeConfig.getHttpHeaders(), breSuppReqJson);
			logger.info("HttpURLConnection to BRMS closed");
		} catch (Exception x) {
			logger.warn("An exception occurred when calling supplier commercials", x);
		}
		//System.out.println("Supp Comm Req: " + breSuppReqJson.toString());
		//System.out.println("Supp Comm Res: " + breSuppResJson.toString());
		return breSuppResJson;

	}

	public static JSONObject getFareBreakup(JSONObject pricing) {
		JSONObject pricingInfo = new JSONObject();
		JSONArray taxDetails = new JSONArray();
		pricingInfo.put(JSON_PROP_BASE, pricing.getJSONObject(JSON_PROP_BASE).getBigDecimal(JSON_PROP_AMOUNT));

		for (Object ancillary : pricing.getJSONArray(JSON_PROP_ANCILLARY)) {
			JSONObject tax = getTaxes(ancillary);
			taxDetails.put(tax);
		}
		for (Object fee : pricing.getJSONArray(JSON_PROP_FEES)) {
			JSONObject tax = getTaxes(fee);
			taxDetails.put(tax);
		}
		for (Object tax : pricing.getJSONArray(JSON_PROP_TAXES)) {
			JSONObject taxJson = getTaxes(tax);
			taxDetails.put(taxJson);
		}
		pricingInfo.put("taxDetails", taxDetails);

		return pricingInfo;
	}

	public static JSONObject getTaxes(Object taxObj) {
		JSONObject tax = new JSONObject();
		tax.put("taxName", ((JSONObject) taxObj).getString(JSON_PROP_CODECONTEXT));
		tax.put("taxValue", ((JSONObject) taxObj).getBigDecimal(JSON_PROP_AMOUNT));
		return tax;
	}

	public static JSONObject getTrainDetails(JSONObject originDestObj, JSONObject trainInfo) {
		JSONObject trainDetailsObj = new JSONObject();
		//TODO hard coded value. Will this be retrieved from product set up?
		trainDetailsObj.put("productCategory", "Transportation");
		// TODO hard coded value. Will this be retrieved from product set up?
		trainDetailsObj.put("productCategorySubType", PRODUCT_RAIL);
		// TODO hard coded value. Since, from and to country won't change for IRCTC.
		trainDetailsObj.put("fromCountry", "India");
		trainDetailsObj.put("toCountry", "India");
		
		trainDetailsObj.put("fromState", "Rajasthan");
		trainDetailsObj.put("toState", "Maharashtra");
		// TODO hard coded value. From where to get city state mapping?
		trainDetailsObj.put("fromCity", originDestObj.getString(JSON_PROP_ORIGINLOC));
		trainDetailsObj.put("toCity", originDestObj.getString(JSON_PROP_DESTLOC));
		trainDetailsObj.put(JSON_PROP_TRAINNUM, trainInfo.getString(JSON_PROP_TRAINNUM));
		trainDetailsObj.put(JSON_PROP_TRAINNAME, trainInfo.getString(JSON_PROP_TRAINNAME));
		trainDetailsObj.put("trainCategory", trainInfo.getString(JSON_PROP_TRAINTYPE));
		
		return trainDetailsObj;
	}

	public static JSONObject getCommonElements(JSONObject resBody) {

		JSONObject commonElemJson = new JSONObject();
		// TODO hard coded value. From where will this value come?
		commonElemJson.put("supplierMarket", "INDIA");
		commonElemJson.put("contractValidity", dateFormat.format(new Date()));
		// TODO hard coded value. Will this be retrieved from MDM based on clientID in Search Request Header?
		commonElemJson.put("clientGroup", "CG1");
		// TODO hard coded value. Will this be retrieved from MDM based on clientID in Search Request Header?
		commonElemJson.put("clientName", "CN1");
		
		return commonElemJson;
	}

	public static JSONObject getAdvanceDef(JSONObject reqBody, JSONObject resBody) {
		JSONObject advanceDefJson = new JSONObject();
		advanceDefJson.put("ticketingDate", dateFormat.format(new Date()));
		advanceDefJson.put("travelDate", reqBody.get(JSON_PROP_TRAVELDATE));
		// TODO hard coded value. From where will this value come?
		advanceDefJson.put("bookingType", "Online");

		return advanceDefJson;
	}
}
