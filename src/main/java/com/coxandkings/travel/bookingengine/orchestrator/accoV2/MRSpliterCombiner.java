package com.coxandkings.travel.bookingengine.orchestrator.accoV2;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.acco.AccoConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;

public class MRSpliterCombiner implements AccoConstants{

	public static String process(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException, ValidationException {
		JSONObject reqHdr = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBdy=reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONArray accInfoArr = reqBdy.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONObject newReqJson=new JSONObject();
		JSONObject resJson = null;
		String resJsonStr = null;
		newReqJson.put(JSON_PROP_REQHEADER, reqHdr);
		newReqJson.put(JSON_PROP_REQBODY, new JSONObject());
		ServiceConfig opConfig = AccoConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
		String opName = opConfig.getOperationName();
		for(int i=0;i<accInfoArr.length();i++) {
			JSONObject accInfoObj = new JSONObject(accInfoArr.getJSONObject(i).toString());
			JSONArray roomConfig = (JSONArray) accInfoObj.remove(JSON_PROP_REQUESTEDROOMARR);
		
			for(int j=0;j<roomConfig.length();j++) {
				JSONObject newAccInfoObj=new JSONObject(accInfoObj.toString());
				JSONObject roomConfigObj = roomConfig.getJSONObject(j);
				newAccInfoObj.append(JSON_PROP_REQUESTEDROOMARR, roomConfigObj);
				newAccInfoObj.put(JSON_PROP_SUPPREF, roomConfigObj.remove(JSON_PROP_SUPPREF));
				newAccInfoObj.put(JSON_PROP_ROOMINDEX, j);
				newAccInfoObj.put("belongsToRequest", i);
				newReqJson.getJSONObject(JSON_PROP_REQBODY).append(JSON_PROP_ACCOMODATIONARR,newAccInfoObj);	
			}	
		}
		switch(opName){
		case "price":resJsonStr = AccoPriceProcessor.process(newReqJson);
		             resJson = getSupplierResponseJSON(reqJson,resJsonStr);
		             //This is done as offers is optional
		 			 try{
		 				MRCompanyOffers.getCompanyOffers(CommercialsOperation.Search,reqJson, resJson, OffersType.COMPANY_SEARCH_TIME);
		 			 }
		 			 catch(Exception e) {}
		             break;
		case "reprice":resJsonStr = AccoRepriceProcessor.process(newReqJson);
		             resJson = getSupplierResponseJSON(reqJson,resJsonStr);
		             //This is done as offers is optional
			 		 try{
			 				MRCompanyOffers.getCompanyOffers(CommercialsOperation.Reprice,reqJson, resJson, OffersType.COMPANY_SEARCH_TIME);
			 			}
			 		 catch(Exception e) {}
			 		 finalCombinedPrices(resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR));
		             break;
		case "book":newReqJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PAYINFO, reqBdy.getJSONArray(JSON_PROP_PAYINFO));
		        	newReqJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_BOOKID, reqBdy.getString(JSON_PROP_BOOKID));
			        resJsonStr=AccoBookProcessor.processV2(newReqJson);
		            resJson =getBookSupplierResponseJSON(reqJson,resJsonStr);
		            break;
		}
		
		return resJson.toString();
		
	}

	private static JSONObject getBookSupplierResponseJSON(JSONObject reqJson, String resJsonStr) {
		JSONObject resBodyJson = new JSONObject();
		JSONObject finalResJson = new JSONObject();
		finalResJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		finalResJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		JSONObject resJson = new JSONObject(resJsonStr);
		
		JSONArray multiResArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONArray multiReqArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		
		int sequence=0;
		JSONArray finalMultiResArr = new JSONArray();
		
		for(int i=0;i<multiReqArr.length();i++) {
			
			resBodyJson.put(JSON_PROP_ACCOMODATIONARR, finalMultiResArr);
			JSONArray roomConfig = multiReqArr.getJSONObject(i).getJSONArray(JSON_PROP_REQUESTEDROOMARR);
			JSONObject suppBookRef=new JSONObject();
			for(int j=0;j<roomConfig.length();j++) {
				JSONObject multiResObj = multiResArr.getJSONObject(sequence);
				modifyBookResponse(multiResObj);
				suppBookRef.append("supplierBookReferences",multiResObj);
				
				//Deprecation this because now BE has to set status as that in Db eg Confirmed,On request
				/*if(j==roomConfig.length()-1) {
					Boolean reservedFlag = false;
					JSONArray suppBookRefArr = suppBookRef.getJSONArray("supplierBookReferences");
					for(int k=0;k<suppBookRefArr.length();k++) {
						JSONObject suppRoomObj = suppBookRefArr.getJSONObject(i);	
						String roomStatus = suppRoomObj.optString("roomStatus");
						if("NOTCONF".equals(roomStatus) || "FAILED".equals(roomStatus)) {
							suppBookRef.put("status", roomStatus);
							break;
						}
						else if("RESERVED".equals(roomStatus)){
							suppBookRef.put("status", roomStatus);
							reservedFlag=true;
						}
						else {
							if(reservedFlag != true)
							{
								suppBookRef.put("status", roomStatus);
							}
	                     }
					}
					String status = suppBookRef.getString("status");
					if("RESERVED".equals(status) || "FAILED".equals(status))
						OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.ON_REQUEST, "", reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID),reqJson.getJSONObject(JSON_PROP_REQHEADER),"");
					else
						OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.BOOKING, "", reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID),reqJson.getJSONObject(JSON_PROP_REQHEADER),"");

				}*/
				if(j==roomConfig.length()-1) {
					JSONArray suppBookRefArr = suppBookRef.getJSONArray("supplierBookReferences");
					for(int k=0;k<suppBookRefArr.length();k++) {
						JSONObject suppRoomObj = suppBookRefArr.getJSONObject(k);	
						String roomStatus = suppRoomObj.optString("roomStatus");
						if("On Request".equalsIgnoreCase(roomStatus)) {
							suppBookRef.put("status", roomStatus);
							break;
						}
						else {
							suppBookRef.put("status", roomStatus);
	                     }
					}
					String status = suppBookRef.getString("status");
					if("On Request".equalsIgnoreCase(status))
						OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.ON_REQUEST, "", reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID),reqJson.getJSONObject(JSON_PROP_REQHEADER),"");
					//else
					//	OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.BOOKING, "", reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID),reqJson.getJSONObject(JSON_PROP_REQHEADER),"");

				}
				sequence++;
			}
			finalMultiResArr.put(suppBookRef);
		}
		resBodyJson.put(JSON_PROP_BOOKID, resJson.getJSONObject(JSON_PROP_RESBODY).getString(JSON_PROP_BOOKID));
		return finalResJson;
	}

	private static void modifyBookResponse(JSONObject multiResObj) {
		JSONArray suppRoomRef = multiResObj.getJSONArray("supplierRoomReferences");
		String supplierRmIdx = "";
		if(suppRoomRef!=null && suppRoomRef.length()>0) {
			JSONObject roomObj = multiResObj.getJSONArray("supplierRoomReferences").getJSONObject(0);
			multiResObj.put("roomStatus", roomObj.getString("roomStatus"));
			supplierRmIdx=roomObj.getString("supplierRoomIndex");
			multiResObj.put("supplierRoomIndex",supplierRmIdx);
		}
		multiResObj.remove("supplierRoomReferences");
		String status = (String) multiResObj.remove("status");
		if("On Request".equals(status)) {
			multiResObj.put("roomStatus", status);
			multiResObj.put("supplierRoomIndex", supplierRmIdx);
		}
	}

	private static JSONObject getSupplierResponseJSON(JSONObject reqJson,String resJsonStr) {
		JSONObject resBodyJson = new JSONObject();
		JSONObject finalResJson = new JSONObject();
		finalResJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		finalResJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		ServiceConfig opConfig = AccoConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
		String opName = opConfig.getOperationName();
		
		JSONObject resJson = new JSONObject(resJsonStr);
		
		JSONArray multiResArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONArray multiReqArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		
		int sequence=0;
		JSONArray finalMultiResArr = new JSONArray();
		for(int i=0;i<multiReqArr.length();i++) {
			//String roomInReqKey = null;
			resBodyJson.put(JSON_PROP_ACCOMODATIONARR, finalMultiResArr);
			JSONArray roomConfig = multiReqArr.getJSONObject(i).getJSONArray(JSON_PROP_REQUESTEDROOMARR);
			JSONObject roomStayArray=new JSONObject();
			for(int j=0;j<roomConfig.length();j++) {
				/*if("reprice".equals(opName))
					 roomInReqKey = AccoRepriceProcessor.getRedisKeyForRoomStay(roomConfig.getJSONObject(j).getJSONObject(JSON_PROP_ROOMINFO));*/
				 
				JSONObject multiResObj = multiResArr.getJSONObject(sequence);
				if(multiResObj.getJSONArray(JSON_PROP_ROOMSTAYARR).length()>0) {
					for(Object roomStayObj:multiResObj.getJSONArray(JSON_PROP_ROOMSTAYARR)) {
						//filtering of rooms is done in reprice class itself
						/*if("reprice".equals(opName)){
							String roomInResKey = AccoRepriceProcessor.getRedisKeyForRoomStay(((JSONObject) roomStayObj).getJSONObject(JSON_PROP_ROOMINFO));
							if(!roomInReqKey.equals(roomInResKey))
								continue;
						}*/
						roomStayArray.append(JSON_PROP_ROOMSTAYARR, (JSONObject)roomStayObj);
					}	
				}
				else {
					roomStayArray.put(JSON_PROP_ROOMSTAYARR, new JSONArray());
				}
				sequence++;
			}
			finalMultiResArr.put(roomStayArray);
			//AccoInfo Obj wise Combined prices
			//finalMultiResArr.getJSONObject(i).put("combinedTotalPriceInfo",combinedPrices(finalMultiResArr.getJSONObject(i).getJSONArray(JSON_PROP_ROOMSTAYARR)));
		}
		resBodyJson.put("timeLimit",resJson.getJSONObject(JSON_PROP_RESBODY).optJSONObject("timeLimit"));
		resBodyJson.put("isPartPayment", resJson.getJSONObject(JSON_PROP_RESBODY).optString("isPartPayment",null));
		resBodyJson.put("partPayment",resJson.getJSONObject(JSON_PROP_RESBODY).optJSONArray("partPayment"));
		return finalResJson;
	}

	private static JSONObject combinedPrices(JSONArray finalMultiResArr) {
		JSONObject totalpriceInfo=new JSONObject();
		BigDecimal totalroomPriceAmt= new BigDecimal(0);
		BigDecimal totalroomTaxAmt= new BigDecimal(0);
		Map<String, JSONObject> companyTaxTotalsMap = new HashMap<String, JSONObject>();
	    Map<String, JSONObject> discountTotalsMap = new HashMap<String, JSONObject>();
		Map<String, JSONObject> roomPrctaxBrkUpTotalsMap = new HashMap<String, JSONObject>();
		JSONObject companyTaxesTotal=new JSONObject();
		JSONObject discountsTotal=new JSONObject();
		for(int i=0;i<finalMultiResArr.length();i++) {
			JSONObject roompriceInfo=finalMultiResArr.getJSONObject(i).getJSONObject(JSON_PROP_ROOMPRICE);
			
			//Calculate total roomPriceInfo
			BigDecimal[] roomTotals=AccoBookProcessor.getTotalTaxesV2(roompriceInfo,totalpriceInfo,roomPrctaxBrkUpTotalsMap,totalroomPriceAmt,totalroomTaxAmt);
			totalroomPriceAmt=roomTotals[0];
			totalroomTaxAmt=roomTotals[1];
			
			//Add companyTax
			JSONObject companyTaxesObj = roompriceInfo.optJSONObject("companyTaxes");
			if(companyTaxesObj!=null) {
			AccoBookProcessor.getTotalCompanyTaxes(companyTaxesObj,totalpriceInfo,companyTaxesTotal,companyTaxTotalsMap);
			}
			
			//Add discounts
			JSONObject discountsObj = roompriceInfo.optJSONObject("discounts");
			if(discountsObj!=null) {
			AccoBookProcessor.getTotalDiscounts(discountsObj,totalpriceInfo,discountsTotal,discountTotalsMap);
			}
	    }
		return totalpriceInfo;
	}
	
	static void finalCombinedPrices(JSONArray finalMultiResArr) {
		//TODO:add combined incentives and receivables
		for(int i=0;i<finalMultiResArr.length();i++) {
			JSONObject totalpriceInfo=new JSONObject();
			BigDecimal totalroomPriceAmt= new BigDecimal(0);
			BigDecimal totalroomTaxAmt= new BigDecimal(0);
			Map<String, JSONObject> companyTaxTotalsMap = new HashMap<String, JSONObject>();
		    Map<String, JSONObject> discountTotalsMap = new HashMap<String, JSONObject>();
			Map<String, JSONObject> roomPrctaxBrkUpTotalsMap = new HashMap<String, JSONObject>();
			JSONObject companyTaxesTotal=new JSONObject();
			JSONObject receivablesTotal=new JSONObject();
			JSONObject incentivesTotal=new JSONObject();
			Map<String, JSONObject> receivablesTotalsMap = new HashMap<String, JSONObject>(); 
			Map<String, JSONObject> incentivesTotalsMap = new HashMap<String, JSONObject>();
			JSONObject discountsTotal=new JSONObject();
			JSONArray roomStayArr = finalMultiResArr.getJSONObject(i).getJSONArray(JSON_PROP_ROOMSTAYARR);
			for(int j=0;j<roomStayArr.length();j++) {
				JSONObject roompriceInfo=roomStayArr.getJSONObject(j).getJSONObject(JSON_PROP_ROOMPRICE);
				
				//Calculate total roomPriceInfo
				BigDecimal[] roomTotals=AccoBookProcessor.getTotalTaxesV2(roompriceInfo,totalpriceInfo,roomPrctaxBrkUpTotalsMap,totalroomPriceAmt,totalroomTaxAmt);
				totalroomPriceAmt=roomTotals[0];
				totalroomTaxAmt=roomTotals[1];
				
				//Add Receivables
				JSONObject receivablesObj = roompriceInfo.optJSONObject("receivables");
				if(receivablesObj!=null) {
					AccoBookProcessor.getTotalRecvblesIncentives(receivablesObj, totalpriceInfo,receivablesTotal,receivablesTotalsMap,"receivables.receivable");
				}
				
				//Add Incentives
				JSONObject incentivesObj = roompriceInfo.optJSONObject("incentives");
				if(incentivesObj!=null) {
					AccoBookProcessor.getTotalRecvblesIncentives(incentivesObj, totalpriceInfo,incentivesTotal,incentivesTotalsMap,"incentives.incentive");
				}
				
				//Add companyTax
				JSONObject companyTaxesObj = roompriceInfo.optJSONObject("companyTaxes");
				if(companyTaxesObj!=null) {
				AccoBookProcessor.getTotalCompanyTaxes(companyTaxesObj,totalpriceInfo,companyTaxesTotal,companyTaxTotalsMap);
				}
				
				//Add discounts
				JSONObject discountsObj = roompriceInfo.optJSONObject("discounts");
				if(discountsObj!=null) {
					AccoBookProcessor.getTotalDiscounts(discountsObj,totalpriceInfo,discountsTotal,discountTotalsMap);
				}
			}
			finalMultiResArr.getJSONObject(i).put("combinedTotalPriceInfo",totalpriceInfo);
		
	  }
		
   }

	public static String processModify(JSONObject reqJson) throws Exception {
		JSONObject reqHdr = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBdy=reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONArray accInfoArr = reqBdy.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		AccoRequestValidator.validateBookId(reqBdy, reqHdr);
		//<--------New Request for Full Cancellation--------->
		JSONObject fullCancelReqJson=new JSONObject();
		JSONObject fullCancelReqBdy=new JSONObject();
		fullCancelReqJson.put(JSON_PROP_REQHEADER, reqHdr);
		fullCancelReqJson.put(JSON_PROP_REQBODY,fullCancelReqBdy);
		fullCancelReqBdy.put("bookID", reqBdy.getString("bookID"));
		//<--------New Request for Full Cancellation--------->
		JSONObject modifyOpReqJson=new JSONObject();
		JSONObject modifyOpReqBdy=new JSONObject();
		modifyOpReqJson.put(JSON_PROP_REQHEADER, reqHdr);
		modifyOpReqJson.put(JSON_PROP_REQBODY,modifyOpReqBdy);
		modifyOpReqBdy.put("bookID", reqBdy.getString("bookID"));
		//<-----------Final Response--------->
		JSONObject resJson = new JSONObject();
		JSONObject resBdy=new JSONObject();
		JSONArray finalMultiResArr=new JSONArray();
		resJson.put(JSON_PROP_RESHEADER, reqHdr);
		resJson.put(JSON_PROP_RESBODY, resBdy);
		resBdy.put(JSON_PROP_ACCOMODATIONARR, finalMultiResArr);
		//<----------------------------------->
		int accoInfoLen = accInfoArr.length();
		for(int i=0;i<accoInfoLen;i++) {
			JSONObject accInfoObj = accInfoArr.getJSONObject(i);
			//to keep track of the sequence while combining
			accInfoObj.put("reqSequence",i);
			AccoRequestValidator.validateModType(accInfoObj, reqHdr);
			String modType = accInfoObj.getString("modificationType");
			if("FULLCANCELLATION".equals(modType)) {
				fullCancelReqBdy.append(JSON_PROP_ACCOMODATIONARR,accInfoArr.getJSONObject(i));
			}
			else {
				modifyOpReqBdy.append(JSON_PROP_ACCOMODATIONARR, accInfoArr.getJSONObject(i));
			}
		}	
		if(fullCancelReqBdy.optJSONArray(JSON_PROP_ACCOMODATIONARR)!=null && fullCancelReqBdy.getJSONArray(JSON_PROP_ACCOMODATIONARR).length()>0) {
			String fullCancelResponse = AccoMRCancelProcessor.process(fullCancelReqJson);
			CombineModificationResponse(finalMultiResArr,fullCancelResponse);
		}
		if(modifyOpReqBdy.optJSONArray(JSON_PROP_ACCOMODATIONARR)!=null && modifyOpReqBdy.getJSONArray(JSON_PROP_ACCOMODATIONARR).length()>0) {
			String modifyResJson = AccoModifyProcessor.process(reqJson);
			CombineModificationResponse(finalMultiResArr,modifyResJson);
		}
		return resJson.toString();
	}

	private static void CombineModificationResponse(JSONArray finalMultiResArr, String reponseStr) {
		JSONObject resJson=new JSONObject(reponseStr);
		JSONArray multiResArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		
		for(int i=0;i<multiResArr.length();i++) {
			JSONObject accoInfoObj = multiResArr.getJSONObject(i);
			 int reqSeq = (int) accoInfoObj.remove("reqSequence");
			 finalMultiResArr.put(reqSeq,accoInfoObj);
		}
		
	}
}
