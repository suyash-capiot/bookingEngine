package com.coxandkings.travel.bookingengine.orchestrator.bus;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.bus.ClientCommercials;
import com.coxandkings.travel.bookingengine.orchestrator.bus.SupplierCommercials;
import com.coxandkings.travel.bookingengine.userctx.UserContext;

public class OpsUtility implements BusConstants {

	public static String applyCommercials(JSONObject opsRqJson) throws Exception {

		JSONObject rqJson = new JSONObject();
		JSONObject rsJson = new JSONObject();
		CommercialsOperation op = null;
		UserContext usrCtx = null;
		JSONObject reqHdr= null;
		Map<String, Integer> BRMS2SIBusMap = new HashMap<String, Integer>();

		try {
			reqHdr = opsRqJson.getJSONObject(JSON_PROP_REQHEADER);
			rqJson.put(JSON_PROP_REQHEADER, reqHdr);
			rqJson.put(JSON_PROP_REQBODY, opsRqJson.getJSONObject(JSON_PROP_REQBODY));
			rsJson.put(JSON_PROP_RESHEADER, reqHdr);
			rsJson.put(JSON_PROP_RESBODY, opsRqJson.getJSONObject(JSON_PROP_RESBODY));
			op = CommercialsOperation.forString(opsRqJson.getString("commercialsOperation"));
			usrCtx = UserContext.getUserContextForSession(reqHdr);
		} catch (Exception e) {
			throw new RequestProcessingException(e.getCause());
		}

		try {

			JSONObject resSupplierComJson = SupplierCommercials.getSupplierCommercials(op, rqJson, rsJson,
					BRMS2SIBusMap);
			JSONObject backupSupplierJson = new JSONObject(resSupplierComJson.toString());
			JSONObject resClientComJson = ClientCommercials.getClientCommercials(rqJson, resSupplierComJson);
			JSONObject resBodyJson = rsJson.getJSONObject(JSON_PROP_RESBODY);
			JSONArray opsServiceArr = new JSONArray();
			JSONArray availJsonArr = resBodyJson.getJSONArray(JSON_PROP_AVAILABILITY);
			Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();
			for (int i = 0; i < availJsonArr.length(); i++) {
				JSONObject availJson = availJsonArr.getJSONObject(i);
				JSONArray serviceJsonArr = availJson.getJSONArray(JSON_PROP_SERVICE);
				for (int j = 0; j < serviceJsonArr.length(); j++) {
					JSONObject serviceJson = serviceJsonArr.getJSONObject(j);
					JSONArray faresJsonArr = serviceJson.getJSONArray(JSON_PROP_FARESARRAY);
//					JSONArray passJsonArr = serviceJson.getJSONArray("passengers");
					for (int k = 0; k < faresJsonArr.length(); k++) {
						JSONObject fareJson = faresJsonArr.getJSONObject(k);
						JSONObject mapJson = new JSONObject();
						mapJson.put("fare",fareJson.getBigDecimal("fare"));
						mapJson.put("currency",fareJson.getString("currency"));
						reprcSuppFaresMap.put(BusSeatMapProcessor.getseatMapKeyForSeatFare(serviceJson, fareJson), mapJson.toString());

					}
					BusSeatMapProcessor.putSupplierCommercialsInMap(backupSupplierJson, reprcSuppFaresMap,
							serviceJson, faresJsonArr);
					BusSeatMapProcessor.putClientCommercialsInMap(resClientComJson, reprcSuppFaresMap, serviceJson,
							faresJsonArr);
					BusBookProcessor.paxCalculation(usrCtx, rqJson, serviceJson, faresJsonArr, reprcSuppFaresMap);
				}
				opsServiceArr = availJson.getJSONArray(JSON_PROP_SERVICE);
			}
			resBodyJson.remove(JSON_PROP_AVAILABILITY);
			resBodyJson.put(JSON_PROP_SERVICE, opsServiceArr);
			reqHdr.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
			reqHdr.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
			reqHdr.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
			reqHdr.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
			reqHdr.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
			return rsJson.toString();
		} catch (Exception e) {
			throw new InternalProcessingException(e.getCause());
		}

	}

//	public static String calculate(JSONObject opsRqJson) throws Exception {
//
//		JSONObject rqJson = new JSONObject();
//		JSONObject rsJson = new JSONObject();
//		
//		UserContext usrCtx = null;
//		JSONObject reqHdr= null;
//		Map<String, Integer> BRMS2SIBusMap = new HashMap<String, Integer>();
//
//		try {
//			reqHdr = opsRqJson.getJSONObject(JSON_PROP_RESHEADER);
//			
//			usrCtx = UserContext.getUserContextForSession(reqHdr);
//			
//			JSONObject resBodyJson = rsJson.getJSONObject(JSON_PROP_RESBODY);
//			JSONArray opsServiceArr = new JSONArray();
//			JSONArray availJsonArr = resBodyJson.getJSONArray(JSON_PROP_AVAILABILITY);
//			Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();
//			for (int i = 0; i < availJsonArr.length(); i++) {
//				JSONObject availJson = availJsonArr.getJSONObject(i);
//				JSONArray serviceJsonArr = availJson.getJSONArray(JSON_PROP_SERVICE);
//				for (int j = 0; j < serviceJsonArr.length(); j++) {
//					JSONObject serviceJson = serviceJsonArr.getJSONObject(j);
//					JSONArray faresJsonArr = serviceJson.getJSONArray(JSON_PROP_FARESARRAY);
////					JSONArray passJsonArr = serviceJson.getJSONArray("passengers");
//					for (int k = 0; k < faresJsonArr.length(); k++) {
//						JSONObject fareJson = faresJsonArr.getJSONObject(k);
//						JSONObject mapJson = new JSONObject();
//						mapJson.put("fare",fareJson.getBigDecimal("fare"));
//						mapJson.put("currency",fareJson.getString("currency"));
//						reprcSuppFaresMap.put(BusSeatMapProcessor.getseatMapKeyForSeatFare(serviceJson, fareJson), mapJson.toString());
//
//					}
////					BusSeatMapProcessor.putSupplierCommercialsInMap(backupSupplierJson, reprcSuppFaresMap,
////							serviceJson, faresJsonArr);
////					BusSeatMapProcessor.putClientCommercialsInMap(resClientComJson, reprcSuppFaresMap, serviceJson,
////							faresJsonArr);
//					BusBookProcessor.paxCalculation(usrCtx, rqJson, serviceJson, faresJsonArr, reprcSuppFaresMap);
//				}
//				opsServiceArr = availJson.getJSONArray(JSON_PROP_SERVICE);
//			}
//			resBodyJson.remove(JSON_PROP_AVAILABILITY);
//			resBodyJson.put(JSON_PROP_SERVICE, opsServiceArr);
//			reqHdr.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
//			reqHdr.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
//			reqHdr.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
//			reqHdr.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
//			reqHdr.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
//			return rsJson.toString();
//		} catch (Exception e) {
//			throw new InternalProcessingException(e.getCause());
//		}
//
//	}


}
