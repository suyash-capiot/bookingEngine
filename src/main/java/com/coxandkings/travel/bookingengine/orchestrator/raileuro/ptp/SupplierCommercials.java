package com.coxandkings.travel.bookingengine.orchestrator.raileuro.ptp;

import java.util.Date;
import java.util.List;
import java.util.Map;

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
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer; 

public class SupplierCommercials implements RailEuropeConstants  {

	public static final String OPERATION = "LowFareSearch";
	public static final String HTTP_AUTH_BASIC_PREFIX = "Basic ";
	private static final Logger logger = LogManager.getLogger(PTPSearchProcessor.class);

	public static JSONObject getSupplierCommercials(CommercialsOperation op, JSONObject req, JSONObject res, Map<String,Integer> suppResToBRIIndex) throws Exception{

		//CommercialsConfig commConfig = RailEuroConfig.getCommercialsConfig();
		//CommercialTypeConfig commTypeConfig = commConfig.getCommercialTypeConfig(CommercialsConfig.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
		ServiceConfig commTypeConfig = RailEuroConfig.getCommercialTypeConfig(CommercialsType.COMMERCIAL_SUPPLIER_TRANSACTIONAL);
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

		JSONObject rootJson = breSuppReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.rail_commercialscalculationengine.suppliertransactionalrules.Root");
		rootJson.put("header", breHdrJson);

		JSONArray briJsonArr = new JSONArray();
		JSONArray solutionsJsonArr = null;
		JSONArray multiResArr = resBody.optJSONArray(JSON_PROP_WWRAILARR)!=null ? resBody.getJSONArray(JSON_PROP_WWRAILARR): new JSONArray().put(resBody.getJSONObject(JSON_PROP_WWRAILARR));
		JSONObject briJson = null;

		for(int i=0;i<multiResArr.length();i++) {

			solutionsJsonArr = multiResArr.getJSONObject(i).getJSONArray(JSON_PROP_SOLUTIONS);
			String supplierID = multiResArr.getJSONObject(i).getString(JSON_PROP_SUPPLIERID);
			String isRoundTrip = multiResArr.getJSONObject(i).getString(JSON_PROP_ISROUNDTRIP);
			JSONArray trainDetailsJsonArr = null;
			for(int j = 0;j<solutionsJsonArr.length(); j++) {
				JSONObject solutionObj = solutionsJsonArr.getJSONObject(j);
				briJson = createBusinessRuleIntakeForSupplier(reqHeader, reqBody, resHeader, resBody, solutionObj, supplierID);
				briJsonArr.put(briJson);
			}
		}	
		rootJson.put(JSON_PROP_BUSSRULEINTAKE, briJsonArr);
//		System.out.println("reqSupplier->"+breSuppReqJson.toString());
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

	private static JSONArray getBRMSTrainDetailsJSON(JSONObject solutionObj) {
		JSONArray trainDetailsArr = solutionObj.getJSONArray(JSON_PROP_TRAINDETAILS);
		JSONArray trnDtlsArr = new JSONArray();
		for(int i = 0; i<trainDetailsArr.length(); i++) {
			JSONObject trainDetails = trainDetailsArr.getJSONObject(i);
			JSONObject trnDtls = new JSONObject();
			String segmentId = trainDetails.getString(JSON_PROP_SEGMENTID);
			trnDtls.put("productCategory", PROD_CATEG_TRANSPORT);
			//trnDtls.put("productCategorySubType", PROD_CATEG_SUBTYPE_RAILEUROPE);
			trnDtls.put("productCategorySubType", "RAILEUROPE");
			trnDtls.put("trainNumber",trainDetails.getString(JSON_PROP_TRAINNUMBER));
			trnDtls.put("fromContinent", "");
			trnDtls.put("fromCountry", "");
			trnDtls.put("fromState", "");
			trnDtls.put("fromCity", trainDetails.getString(JSON_PROP_ORIGINCITYNAME));
			trnDtls.put("toContinent", "");
			trnDtls.put("toCountry", "");
			trnDtls.put("toState", "");
			trnDtls.put("toCity", trainDetails.getString(JSON_PROP_DESTINATIONCITYNAME));
			//trnDtls.put("numberOfPassengers", "");
			trnDtls.put("trainName", "");
			trnDtls.put("ticketType", "");
			trnDtls.put("passengerDetails", getPassengerDetails(solutionObj,segmentId));
			trnDtlsArr.put(trnDtls);
		}	
		return trnDtlsArr;
	}

	private static JSONArray getPassengerDetails(JSONObject solutionObj, String segmentId) {
		JSONArray packageDetailsArr = solutionObj.getJSONArray("packageDetails");
		JSONArray paxDetailsArr = new JSONArray();
		for(int i=0; i<packageDetailsArr.length(); i++) {
			JSONObject packageDetails = packageDetailsArr.getJSONObject(i);	
			JSONArray productFareArr = packageDetails.getJSONArray("productFare");			
			for(int j=0; j<productFareArr.length(); j++) {
				JSONObject productFare = productFareArr.getJSONObject(j);
				JSONObject paxDetail = new JSONObject();
				if(productFare.getString(JSON_PROP_SEGMENTID).equalsIgnoreCase(segmentId)) {
					int nPax = Integer.parseInt(productFare.getString("nPassengers"));
					paxDetail.put("nPassenger", nPax);
					paxDetail.put("seatClass", packageDetails.getString("classType"));
					paxDetail.put("packageType",packageDetails.getString("packageType"));
					paxDetail.put("accommodationCode",packageDetails.getString("accommodationCode"));
					paxDetail.put("passengerType", productFare.getString("basePassengerType"));
					paxDetail.put("fareType", productFare.getString("fareType"));
					paxDetail.put("totalFare", productFare.getJSONObject(JSON_PROP_PRODUCTPRICEINFO).getJSONObject("totalFare").getBigDecimal("amount"));									
				}
				paxDetailsArr.put(paxDetail);	
			}			
		}	
		return paxDetailsArr;
	}

	private static JSONObject createBusinessRuleIntakeForSupplier(JSONObject reqHeader, JSONObject reqBody,
			JSONObject resHeader, JSONObject resBody, JSONObject solutionObj, String supplierID) {

		JSONObject briJson = new JSONObject();

		JSONObject commonElemsJson = new JSONObject();        
		commonElemsJson.put(JSON_PROP_SUPP, supplierID);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHeader);
		// TODO: Supplier market is hard-coded below. Where will this come from? This should be ideally come from supplier credentials.
		commonElemsJson.put(JSON_PROP_SUPPMARKET, "EUROPE");
		commonElemsJson.put("contractValidity", DATE_FORMAT.format(new Date()));
		commonElemsJson.put(JSON_PROP_PRODNAME, PRODUCT_NAME_BRMS);
		// TODO: Check how the value for segment should be set?
		commonElemsJson.put(JSON_PROP_SEGMENT, "Active");
		JSONObject clientCtx = reqHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		commonElemsJson.put(JSON_PROP_CLIENTTYPE, clientCtx.getString(JSON_PROP_CLIENTTYPE));
		if (usrCtx != null) {
			commonElemsJson.put(JSON_PROP_CLIENTNAME, (usrCtx != null) ? usrCtx.getClientName() : "");
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
		advDefnJson.put(JSON_PROP_SALESDATE,"2017-01-11T00:00:00");
		advDefnJson.put(JSON_PROP_TRAVELDATE, "2017-03-11T00:00:00");
		advDefnJson.put(JSON_PROP_TRAVELTYPE, "SITI");
		// advDefnJson.put(JSON_PROP_JOURNEYTYPE, reqBody.getString(JSON_PROP_JOURNEYTYPE));
		advDefnJson.put(JSON_PROP_TRAINCATEGORY, "Exp");
		advDefnJson.put(JSON_PROP_CONNECTSUPPTYPE, "");
		advDefnJson.put(JSON_PROP_CONNECTSUPP, "");

		briJson.put(JSON_PROP_ADVANCEDDEF, advDefnJson);

		briJson.put(JSON_PROP_TRAINDETAILS, getBRMSTrainDetailsJSON(solutionObj));

		return briJson;
	}
}
