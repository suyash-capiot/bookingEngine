package com.coxandkings.travel.bookingengine.orchestrator.accoV3;

import java.math.BigDecimal;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.TaxEngineConfig;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.enums.AccoSubType;
import com.coxandkings.travel.bookingengine.userctx.OrgHierarchy;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public class TaxEngine implements AccoConstants{

	private static final Logger logger = LogManager.getLogger(TaxEngine.class);

	@SuppressWarnings("unused")
	public static void getCompanyTaxes(JSONObject reqJson, JSONObject resJson) {
		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject clientCtxJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);

		JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray accoInfoJsonArr = resBodyJson.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();

		JSONObject taxEngineReqJson = new JSONObject(new JSONTokener(TaxEngineConfig.getRequestJSONShell()));
		JSONObject rootJson = taxEngineReqJson.getJSONArray("commands").getJSONObject(0).getJSONObject("insert").getJSONObject("object").getJSONObject("cnk.taxcalculation.Root");

		JSONObject teHdrJson = new JSONObject();
		teHdrJson.put(JSON_PROP_SESSIONID, resHdrJson.getString(JSON_PROP_SESSIONID));
		teHdrJson.put(JSON_PROP_TRANSACTID, resHdrJson.getString(JSON_PROP_TRANSACTID));
		teHdrJson.put(JSON_PROP_USERID, resHdrJson.getString(JSON_PROP_USERID));
		teHdrJson.put(JSON_PROP_OPERATIONNAME, "Search");
		rootJson.put(JSON_PROP_HEADER, teHdrJson);

		JSONObject gciJson = new JSONObject();

		gciJson.put(JSON_PROP_COMPANYNAME, orgHierarchy.getCompanyName());
		//gciJson.put(JSON_PROP_COMPANYNAME, "CnK");
		
		// Company can have multiple markets associated with it. However, a client associated with that 
		// company can have only one market. Therefore, following assignment uses client market.
		gciJson.put(JSON_PROP_COMPANYMKT, clientCtxJson.optString(JSON_PROP_CLIENTMARKET, ""));
		gciJson.put(JSON_PROP_COUNTRY, orgHierarchy.getCompanyCountry());
		//gciJson.put(JSON_PROP_COUNTRY, "India");
		
		gciJson.put(JSON_PROP_STATE, orgHierarchy.getCompanyState()!=null?orgHierarchy.getCompanyState():"");
		gciJson.put(JSON_PROP_CLIENTTYPE, clientCtxJson.optString(JSON_PROP_CLIENTTYPE, ""));
		gciJson.put(JSON_PROP_CLIENTCAT, usrCtx.getClientCategory());
		gciJson.put(JSON_PROP_CLIENTSUBCAT, usrCtx.getClientSubCategory());
		//gciJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
		//gciJson.put(JSON_PROP_PRODCATEG, PROD_CATEG_TRANSPORT);
		//gciJson.put(JSON_PROP_PRODCATEGSUBTYPE, AccoSubType.HOTEL.toString());
		gciJson.put(JSON_PROP_VALIDITY, DATE_FORMAT.format(new Date()));

		JSONArray suppPrcDtlsJsonArr = new JSONArray();
		for(Object accoInfoJson:accoInfoJsonArr) {
			suppPrcDtlsJsonArr.put(getSupplierAndTravelDetails(usrCtx,(JSONObject) accoInfoJson));
		}
		/*while (suppHdrEntries.hasNext()) {
			Entry<String, JSONObject> suppHdrEntry = suppHdrEntries.next();
			String suppID = suppHdrEntry.getKey();
			JSONObject suppHdrJson = suppHdrEntry.getValue();

			Map<String, JSONObject> destTravelDtlsJsonMap = suppTaxReqJson.get(suppID);
			if (destTravelDtlsJsonMap != null) {
				Collection<JSONObject> travelDtlsJsonColl = destTravelDtlsJsonMap.values();
				JSONArray travelDtlsJsonArr = new JSONArray();
				for (JSONObject travelDtlsJson : travelDtlsJsonColl) {
					travelDtlsJsonArr.put(travelDtlsJson);
				}

				suppHdrJson.put(JSON_PROP_TRAVELDTLS, travelDtlsJsonArr);
			}

			suppPrcDtlsJsonArr.put(suppHdrJson);
		}*/
		gciJson.put(JSON_PROP_SUPPPRICINGDTLS, suppPrcDtlsJsonArr);
		rootJson.put(JSON_PROP_GSTCALCINTAKE, gciJson);

		JSONObject taxEngineResJson = null;
		try {
			taxEngineResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_TAXENGINE, TaxEngineConfig.getServiceURL(), TaxEngineConfig.getHttpHeaders(), taxEngineReqJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(taxEngineResJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from tax calculation engine: %s", taxEngineResJson.toString()));
				return;
			}
			addTaxesToResponseRoomTotal(reqJson, resJson, taxEngineResJson);
		}
		catch (Exception x) {
			logger.warn("An exception occurred when calling tax calculation engine", x);
		}
	}

	private static JSONObject getSupplierAndTravelDetails(UserContext usrCtx,JSONObject accoInfoJson) {
		OrgHierarchy orgHierarchy = usrCtx.getOrganizationHierarchy();
		JSONObject suppHdrJson = new JSONObject();
		JSONArray roomStayJsonArr = accoInfoJson.getJSONArray(JSON_PROP_ROOMSTAYARR);
		if(roomStayJsonArr.length()<=0) {
			//TODO:log some message, will roomStay arr be present always
			return suppHdrJson;
		}
		String suppID = roomStayJsonArr.getJSONObject(0).getString(JSON_PROP_SUPPREF);

		suppHdrJson.put(JSON_PROP_SUPPNAME, suppID);
		//TODO:rate type and code are room level params.Ask what value should go here. It should be inside travel json
		/*suppHdrJson.put(JSON_PROP_SUPPRATETYPE, "");
		suppHdrJson.put(JSON_PROP_SUPPRATECODE, "");*/
		suppHdrJson.put(JSON_PROP_CLIENTNAME, usrCtx.getClientName());
		suppHdrJson.put(JSON_PROP_ISTHIRDPARTY, false);
		suppHdrJson.put(JSON_PROP_RATEFOR, "Hotel");//TODO:ask for this field.Is this same as subType
		suppHdrJson.put(JSON_PROP_LOCOFRECIEPT, orgHierarchy.getCompanyState());//TODO:get state code of hotel
		suppHdrJson.put(JSON_PROP_PTOFSALE, usrCtx.getClientState());

		JSONObject trvlDtlsJson = new JSONObject();

		trvlDtlsJson.put(JSON_PROP_TRAVELLINGCITY, "");
		trvlDtlsJson.put(JSON_PROP_TRAVELLINGSTATE, "");
		trvlDtlsJson.put(JSON_PROP_TRAVELLINGCOUNTRY, "");
		for(int i=0;i<roomStayJsonArr.length();i++) {
			JSONObject roomStayJson = roomStayJsonArr.getJSONObject(i);
			JSONObject totalPriceInfoJson = roomStayJson.getJSONObject(JSON_PROP_ROOMPRICE);
			JSONObject taxesJson = totalPriceInfoJson.getJSONObject(JSON_PROP_TOTALTAX);
			JSONArray taxesJsonArr = taxesJson.optJSONArray(JSON_PROP_TAXBRKPARR);
			JSONObject rcvblsJson = totalPriceInfoJson.getJSONObject(JSON_PROP_RECEIVABLES);
			// The input to Tax Engine expects totalFare element to have totals that include  
			// only BaseFare(include MarkUp) + Taxes(include MarkUp) - Discount
			BigDecimal roomPrice = totalPriceInfoJson.getBigDecimal(JSON_PROP_AMOUNT).subtract(rcvblsJson.getBigDecimal(JSON_PROP_AMOUNT));

			JSONObject fareDtlsJson = new JSONObject();

			fareDtlsJson.put(JSON_PROP_TOTALFARE, roomPrice);
			fareDtlsJson.put(JSON_PROP_SELLINGPRICE, totalPriceInfoJson.getBigDecimal(JSON_PROP_AMOUNT));
			fareDtlsJson.put(JSON_PROP_PROD, PROD_CATEG_ACCO);
			fareDtlsJson.put(JSON_PROP_SUBTYPE, roomStayJson.getString(JSON_PROP_ACCOSUBTYPE));
			fareDtlsJson.put(JSON_PROP_MEALPLAN, roomStayJson.getJSONObject(JSON_PROP_ROOMINFO).
					getJSONObject(JSON_PROP_MEALINFO).getString(JSON_PROP_MEALCODE));//TODO:ask if meal name should come
			fareDtlsJson.put(JSON_PROP_ISPKG, false);//TODO:as of now hard coded. chk for ancillary in rq
			fareDtlsJson.put(JSON_PROP_ADDCHARGES, BigDecimal.ZERO);//TODO:what shld be present here?
			fareDtlsJson.put(JSON_PROP_ISMARKEDUP, isMarkedUpPrice(roomStayJson));
			fareDtlsJson.put(JSON_PROP_SUPPRATETYPE, "");
			fareDtlsJson.put(JSON_PROP_SUPPRATECODE, "");
			fareDtlsJson.put("starCategory", 0);
			fareDtlsJson.put("cabinClass", "");
			fareDtlsJson.put("flighType", "");
			fareDtlsJson.put("amendmentCharges", 0);
			fareDtlsJson.put("cancellationCharges", 0);

			//add base fare
			JSONObject fareBrkpJson = new JSONObject();
			fareBrkpJson.put(JSON_PROP_NAME, JSON_VAL_BASE);
			fareBrkpJson.put(JSON_PROP_VALUE, roomPrice.subtract(taxesJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO)));
			fareDtlsJson.append(JSON_PROP_FAREBRKUP, fareBrkpJson);

			//add taxes
			if(taxesJsonArr!=null) {
				for(Object taxJson:taxesJsonArr) {
					fareBrkpJson = new JSONObject();
					fareBrkpJson.put(JSON_PROP_NAME, ((JSONObject) taxJson).getString(JSON_PROP_TAXCODE));
					fareBrkpJson.put(JSON_PROP_VALUE,((JSONObject) taxJson).getBigDecimal(JSON_PROP_AMOUNT));
					fareDtlsJson.append(JSON_PROP_FAREBRKUP, fareBrkpJson);
				}
			}

			//add receivables
			for(Object rcvblJson:rcvblsJson.getJSONArray(JSON_PROP_RECEIVABLE)) {
				fareBrkpJson = new JSONObject();
				fareBrkpJson.put(JSON_PROP_NAME, ((JSONObject) rcvblJson).getString(JSON_PROP_CODE));
				fareBrkpJson.put(JSON_PROP_VALUE,((JSONObject) rcvblJson).getBigDecimal(JSON_PROP_AMOUNT));
				fareDtlsJson.append(JSON_PROP_FAREBRKUP, fareBrkpJson);
			}

			trvlDtlsJson.append(JSON_PROP_FAREDETAILS, fareDtlsJson);

		}

		suppHdrJson.append(JSON_PROP_TRAVELDTLS, trvlDtlsJson);

		return suppHdrJson;
	}

	private static void addTaxesToResponseRoomTotal(JSONObject reqJson, JSONObject resJson, JSONObject taxEngResJson) {
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray accoInfoJsonArr = resBodyJson.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONArray suppPrcngDtlJsonArr = taxEngResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.taxcalculation.Root").getJSONObject("gstCalculationIntake").getJSONArray(JSON_PROP_SUPPPRICINGDTLS);

		for (int i=0; i < accoInfoJsonArr.length(); i++) {
			JSONObject accoInfoJson = accoInfoJsonArr.getJSONObject(i);
			JSONObject suppPrcngDtlJson = suppPrcngDtlJsonArr.optJSONObject(i);
			if (suppPrcngDtlJson == null) {
				logger.warn(String.format("No service taxes applied on rooms in accomodationInfo at index %s", i));
				continue;
			}

			JSONArray roomStayJsonArr = accoInfoJson.getJSONArray(JSON_PROP_ROOMSTAYARR);
			JSONArray trvlDtlsJsonArr = suppPrcngDtlJson.optJSONArray(JSON_PROP_TRAVELDTLS);
			if (trvlDtlsJsonArr == null || trvlDtlsJsonArr.length()<=0) {
				logger.warn(String.format("No service taxes applied on rooms in accomodationInfo at index %s", i));
				continue;
			}
			JSONArray fareDtlsJsonArr = trvlDtlsJsonArr.getJSONObject(0).getJSONArray(JSON_PROP_FAREDETAILS);
			if (fareDtlsJsonArr == null || fareDtlsJsonArr.length()<=0) {
				logger.warn(String.format("No service taxes applied on rooms in accomodationInfo at index %s", i));
				continue;
			}

			for(int j=0;j<roomStayJsonArr.length();j++) {
				JSONObject roomStayJson = (JSONObject) roomStayJsonArr.get(j);
				JSONObject fareDtlsJson = fareDtlsJsonArr.optJSONObject(j);
				if (fareDtlsJson == null) {
					logger.warn(String.format("No service taxes applied on room at index %s,%s", i,j));
					continue;
				}
				JSONArray appliedTaxesJsonArr = fareDtlsJson.optJSONArray(JSON_PROP_APPLIEDTAXDTLS);
				if (appliedTaxesJsonArr == null) {
					logger.warn(String.format("No service taxes applied on room at index %s,%s", i,j));
					continue;
				}

				JSONObject totalPriceInfojson =roomStayJson.getJSONObject(JSON_PROP_ROOMPRICE); 
				BigDecimal roomPrice = totalPriceInfojson.getBigDecimal(JSON_PROP_AMOUNT);
				String roomCcy = totalPriceInfojson.getString(JSON_PROP_CCYCODE);
				BigDecimal companyTaxTotalAmt = BigDecimal.ZERO;
				JSONArray companyTaxJsonArr = new JSONArray();
				for (int k=0; k < appliedTaxesJsonArr.length(); k++) {
					JSONObject appliedTaxesJson = appliedTaxesJsonArr.getJSONObject(k);
					JSONObject companyTaxJson = new JSONObject();
					BigDecimal taxAmt = appliedTaxesJson.optBigDecimal(JSON_PROP_TAXVALUE, BigDecimal.ZERO);
					companyTaxJson.put(JSON_PROP_TAXCODE, appliedTaxesJson.optString(JSON_PROP_TAXNAME, ""));
					companyTaxJson.put(JSON_PROP_TAXPCT, appliedTaxesJson.optBigDecimal(JSON_PROP_TAXPERCENTAGE, BigDecimal.ZERO));
					companyTaxJson.put(JSON_PROP_AMOUNT, taxAmt);
					companyTaxJson.put(JSON_PROP_CCYCODE, roomCcy);
					companyTaxJson.put(JSON_PROP_HSNCODE, appliedTaxesJson.optString(JSON_PROP_HSNCODE));
					companyTaxJson.put(JSON_PROP_SACCODE, appliedTaxesJson.optString(JSON_PROP_SACCODE));
					//taxComponent added on finance demand..
					companyTaxJson.put(JSON_PROP_TAXCOMP, appliedTaxesJson.optString(JSON_PROP_TAXCOMP));
					companyTaxJsonArr.put(companyTaxJson);
					companyTaxTotalAmt = companyTaxTotalAmt.add(taxAmt);
					roomPrice = roomPrice.add(taxAmt);
				}

				// Append the taxes retrieved from tax engine response in itineraryTotalFare element of pricedItinerary JSON
				JSONObject companyTaxesJson = new JSONObject();
				companyTaxesJson.put(JSON_PROP_AMOUNT, companyTaxTotalAmt);
				companyTaxesJson.put(JSON_PROP_CCYCODE, roomCcy);
				companyTaxesJson.put(JSON_PROP_COMPANYTAX, companyTaxJsonArr);
				totalPriceInfojson.put(JSON_PROP_COMPANYTAXES, companyTaxesJson);
				totalPriceInfojson.put(JSON_PROP_AMOUNT, roomPrice);
			}
		}
	}

	private static boolean isMarkedUpPrice(JSONObject roomStayJson) {
		JSONArray clEntityCommsJsonArr = roomStayJson.optJSONArray(JSON_PROP_CLIENTENTITYCOMMS);
		if (clEntityCommsJsonArr == null || clEntityCommsJsonArr.length() == 0) {
			return false;
		}

		for (int i=0; i < clEntityCommsJsonArr.length(); i++) {
			JSONObject clEntityCommsJson = clEntityCommsJsonArr.getJSONObject(i);
			JSONArray clCommsJsonArr = clEntityCommsJson.optJSONArray(JSON_PROP_CLIENTCOMM);
			if (clCommsJsonArr == null || clCommsJsonArr.length() == 0) {
				continue;
			}

			for (int j=0; j < clCommsJsonArr.length(); j++) {
				JSONObject clCommsTotalJson = clCommsJsonArr.getJSONObject(j);
				if (JSON_VAL_COMMTYPEMARKUP.equals(clCommsTotalJson.getString(JSON_PROP_COMMNAME))) {
					return true;
				}
			}
		}

		return false;
	}
}
