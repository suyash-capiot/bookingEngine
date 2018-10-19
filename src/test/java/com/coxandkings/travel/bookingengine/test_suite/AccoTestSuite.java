package com.coxandkings.travel.bookingengine.test_suite;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoConstants;

public class AccoTestSuite extends AbstractTestSuite implements AccoConstants{

	//this is ugly hack
	private static String mpaxShell = "{\"documentDetails\":{\"documentInfo\":[{\"expiryDate\":\"2020-12-12\",\"nationality\":\"Indian\",\"docNumber\":\"AEEE11113\",\"docType\":\"PAN Card\",\"issueCountry\":\"India\",\"issueAuthority\":\"Govt. of India\",\"effectiveDate\":\"2010-12-12\",\"issueLocation\":\"Mumbai\",}]},\"paxType\": \"\",\"isLeadPax\": false,\"title\": \"Mr\",\"firstName\": \"\",\"middleName\": \"mathew\", \"surname\": \"doe\",\"dob\": \"1980-01-01\",\"contactDetails\":[{\"contactInfo\" :{\"countryCode\": \"+91\",\"contactType\":\"WORK\",\"mobileNo\": \"9800000000\",\"email\": \"xyz@gmail.com\"}}],\"addressDetails\": {\"city\": \"Mumbai\",\"state\": \"Maharashtra\",\"country\": \"India\",\"zip\": \"400063\",\"addrLine2\": \"addrLine2\",\"addrLine1\": \"addrLine1\"}}";
	boolean mMultiSupp = false;
	List<Object> mMultiSuppLst;
	int mBrkAftr = mBEConfig.getOperationList().size();

	public AccoTestSuite(JSONObject reqJson) {
		JSONObject testConfig = reqJson.optJSONObject("testConfig");
		if (testConfig != null) {
			mBrkAftr = Math.min(testConfig.optInt("breakAfterCall", mBrkAftr),mBrkAftr);
			mMultiSupp = testConfig.optBoolean("multiSupp");
			mMultiSuppLst = testConfig.optJSONArray("multiSuppList")!=null
					? testConfig.optJSONArray("multiSuppList").toList()
					: new ArrayList<Object>();
		}
	}

	//*****************VALIDATE RESPONSES*****************//
	boolean validateSrchRes(JSONObject reqJSon,JSONObject srchRes) {

		JSONArray validationErrorsJsonArr = new JSONArray();
		srchRes.put("validationErrors", validationErrorsJsonArr);
		JSONObject reqBdyJson = reqJSon.getJSONObject(JSON_PROP_REQBODY);
		HashMap<String,List<Integer>> roomCountMap = new HashMap<String, List<Integer>>();
		List<Integer> roomIndLst;

		JSONArray accInfoJsonArr = srchRes.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		if (accInfoJsonArr.length() != 1) {
			validationErrorsJsonArr.put("AccomodationInfo array length should be one !!");
			return false;
		}
		JSONArray roomStayArr = ((JSONObject) accInfoJsonArr.get(0)).getJSONArray(JSON_PROP_ROOMSTAYARR);
		if (roomStayArr.length() < 1) {
			validationErrorsJsonArr.put("No rooms received from SI !!");
		}
		JSONObject roomJson;String suppId;
		for (int i = 0; i < roomStayArr.length(); i++) {
			roomJson = (JSONObject) roomStayArr.get(i);
			if (!validateRoomStay(reqBdyJson, roomJson, validationErrorsJsonArr)) {
				validationErrorsJsonArr.put(String.format("RoomStay with index %s has the above validation errors", i));
				break;
			}
			suppId = roomJson.getString(JSON_PROP_SUPPREF);
			if(!roomCountMap.containsKey(suppId)) {
				roomCountMap.put(suppId, new ArrayList<Integer>());
			}
			roomIndLst = roomCountMap.get(suppId);
			if(!roomIndLst.contains(roomJson.getJSONObject(JSON_PROP_ROOMINFO).getInt(JSON_PROP_ROOMINDEX))) {
				roomIndLst.add(roomJson.getJSONObject(JSON_PROP_ROOMINFO).getInt(JSON_PROP_ROOMINDEX));
			}
		}

		int reqRoomCnt = reqBdyJson.getJSONArray(JSON_PROP_REQUESTEDROOMARR).length();
		for(Entry<String, List<Integer>> entry : roomCountMap.entrySet()) {
			if(reqRoomCnt != entry.getValue().size()) {
				validationErrorsJsonArr.put(String
						.format("Room count for supplier %s does not match requested room count", entry.getKey()));
			}
		}
		return validationErrorsJsonArr.length() > 0 ? false : true;
	}

	boolean validatePrcRes(JSONObject reqJSon,JSONObject prcRes) {
		JSONArray validationErrorsJsonArr = new JSONArray();
		prcRes.put("validationErrors", validationErrorsJsonArr);
		JSONObject reqBdyJson;
		List<Integer> roomIndLst;

		JSONArray accInfoJsonArr = prcRes.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		for (int j = 0; j < accInfoJsonArr.length(); j++) {
			JSONArray roomStayArr = ((JSONObject) accInfoJsonArr.get(j)).getJSONArray(JSON_PROP_ROOMSTAYARR);
			if (roomStayArr.length() < 1) {
				validationErrorsJsonArr
						.put(String.format("No rooms received from SI for request body at index %s!!", j));
			}
			JSONObject roomJson;
			roomIndLst = new ArrayList<Integer>();
			reqBdyJson = (JSONObject) reqJSon.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR)
					.get(j);
			for (int i = 0; i < roomStayArr.length(); i++) {
				roomJson = (JSONObject) roomStayArr.get(i);
				if (!validateRoomStay(reqBdyJson, roomJson, validationErrorsJsonArr)) {
					validationErrorsJsonArr.put(String
							.format("RoomStay for response %s with index %s has the above validation errors", j, i));
					break;
				}

				if (!roomIndLst.contains(roomJson.getJSONObject(JSON_PROP_ROOMINFO).getInt(JSON_PROP_ROOMINDEX))) {
					roomIndLst.add(roomJson.getJSONObject(JSON_PROP_ROOMINFO).getInt(JSON_PROP_ROOMINDEX));
				}
			}

			int reqRoomCnt = reqBdyJson.getJSONArray(JSON_PROP_REQUESTEDROOMARR).length();
			if (reqRoomCnt != roomIndLst.size()) {
				validationErrorsJsonArr.put(String
						.format("Room count does not match requested room count for request body at index %s", j));
			}

		}

		return validationErrorsJsonArr.length() > 0 ? false : true;
	}

	boolean validateReprcRes(JSONObject reqJSon, JSONObject reprcRes) {
		return validatePrcRes(reqJSon, reprcRes);
	}

	boolean validateRoomStay(JSONObject reqBdyJson, JSONObject roomJson, JSONArray validationErrorsJsonArr) {

		if (roomJson.getString(JSON_PROP_SUPPREF).isEmpty())
			validationErrorsJsonArr.put("Supplier ref is empty");
		String availStatus = roomJson.getJSONObject("roomInfo").getString(JSON_PROP_AVAILSTATUS);
		if (availStatus.isEmpty())
			validationErrorsJsonArr.put("Availability Status is Empty OR not as defined");
		//availStatus.equals(AVAIL_STATUS.AVAILABLEFORSALE);
		JSONArray tempOccupJsonArr = roomJson.getJSONArray(JSON_PROP_OCCUPANCYARR);

		if (tempOccupJsonArr.length() > 2) {
			validationErrorsJsonArr.put("Occupancy Info Array Size Invalid");
		}
		JSONObject tempJson = roomJson.getJSONObject(JSON_PROP_ROOMPRICE);
		if (tempJson.getBigDecimal(JSON_PROP_AMOUNT) == new BigDecimal(0))
			validationErrorsJsonArr.put("Total amount cannot be zero");
		if (tempJson.getString(JSON_PROP_CCYCODE).isEmpty())
			validationErrorsJsonArr.put("Currency for total fare cannot be empty");

		tempJson = tempJson.optJSONObject(JSON_PROP_TOTALTAX);
		if (tempJson != null && tempJson.has(JSON_PROP_AMOUNT)) {
			if (tempJson.getString(JSON_PROP_CCYCODE).isEmpty())
				validationErrorsJsonArr.put("Currency for total tax cannot be empty");
			if (tempJson.has(JSON_PROP_TAXBRKPARR)) {
				for (Object tax : tempJson.getJSONArray(JSON_PROP_TAXBRKPARR)) {
					if (!((JSONObject) tax).has(JSON_PROP_AMOUNT))
						validationErrorsJsonArr.put("Amount for tax breakup not present");
					if (((JSONObject) tax).getString(JSON_PROP_CCYCODE).isEmpty())
						validationErrorsJsonArr.put("Currency for tax breakup cannot be empty");
				}
			}

		}


		JSONArray tempJsonArr = roomJson.getJSONArray(JSON_PROP_NIGHTLYPRICEARR);
		if (tempJsonArr.length() != getDateRange(reqBdyJson.getString(JSON_PROP_CHKIN),
				reqBdyJson.getString(JSON_PROP_CHKOUT), true, false).size()) {
			validationErrorsJsonArr.put("Number of nights mismatch in nightly price array");
		}
		// String effecDate="";
		for (int i = 0; i < tempJsonArr.length(); i++) {
			tempJson = (JSONObject) tempJsonArr.get(i);
			if (!tempJson.has("effectiveDate"))
				validationErrorsJsonArr
						.put(String.format("Effective date not present in nightly price array for index %s", i));
			/*
			 * else effecDate = tempJson.getString("effectiveDate");
			 */

			if (!tempJson.has(JSON_PROP_AMOUNT))
				validationErrorsJsonArr
						.put(String.format("Nightly amount not present in nightly price array for index %s", i));
			if (tempJson.getString(JSON_PROP_CCYCODE).isEmpty())
				validationErrorsJsonArr.put(String
						.format("Currency for nightly fare cannot be empty in nightly price array for index %s", i));

			tempJson = tempJson.optJSONObject(JSON_PROP_TOTALTAX);
			if (tempJson != null && tempJson.has(JSON_PROP_AMOUNT)) {
				if (tempJson.getString(JSON_PROP_CCYCODE).isEmpty())
					validationErrorsJsonArr.put(String.format(
							"Currency for nightly total tax cannot be empty in nightly price array for index %s", i));
				if (tempJson.has(JSON_PROP_TAXBRKPARR)) {
					for (Object tax : tempJson.getJSONArray(JSON_PROP_TAXBRKPARR)) {
						if (!((JSONObject) tax).has(JSON_PROP_AMOUNT))
							validationErrorsJsonArr.put(String.format(
									"Amount for tax breakup not present in nightly price array for index %s", i));
						if (((JSONObject) tax).getString(JSON_PROP_CCYCODE).isEmpty())
							validationErrorsJsonArr.put(String.format(
									"Currency for nightly tax breakup cannot be empty in nightly price array for index %s",
									i));
					}
				}
			}

		}

		tempJson = roomJson.getJSONObject(JSON_PROP_ROOMINFO);
		if (tempJson.getInt(JSON_PROP_ROOMINDEX) < 0)
			validationErrorsJsonArr
					.put("Room index should be a positive integer which indicates which room in request it belongs to");
		if (tempJson.getJSONObject(JSON_PROP_HOTELINFO).getString(JSON_PROP_HOTELCODE).isEmpty())
			validationErrorsJsonArr.put("Hotel code cannot be empty");

		// TODO:hotelCode validation.Check if hotels are as per req in all operations
		return validationErrorsJsonArr.length() > 0 ? false : true;
	}

	// *****************VALIDATION CODE ENDS*****************//

	// *****************MAP RESPONSES************************//

	void mapSrch2Price(JSONObject reqJson, JSONObject srchRes) {
		JSONArray roomJsonArr = ((JSONObject) srchRes.getJSONObject(JSON_PROP_RESBODY)
				.getJSONArray(JSON_PROP_ACCOMODATIONARR).get(0)).getJSONArray(JSON_PROP_ROOMSTAYARR);
		HashMap<String, List<JSONObject>> suppResMap = new HashMap<String, List<JSONObject>>();
		String suppId;
		for (int i = 0; i < roomJsonArr.length(); i++) {// A map for supplier and rooms is made.
			JSONObject roomJson = (JSONObject) roomJsonArr.get(i);
			suppId = roomJson.getString(JSON_PROP_SUPPREF);
			if (!suppResMap.containsKey(suppId))
				suppResMap.put(suppId, new ArrayList<JSONObject>());
			suppResMap.get(suppId).add(roomJson);
		}
		JSONArray multiReqArr = new JSONArray();
		JSONObject reqBdyJson = (JSONObject) reqJson.remove(JSON_PROP_REQBODY);// Request body of search is removed so
																				// that price request could be inserted.
		reqJson.put(JSON_PROP_REQBODY, (new JSONObject()).put(JSON_PROP_ACCOMODATIONARR, multiReqArr));// Accomodation
																										// Info and a
																										// new request
																										// body is
																										// inserted,
		reqBdyJson.remove(JSON_PROP_HOTELCODE);
		JSONArray accoSubTypeArr = (JSONArray) reqBdyJson.remove(JSON_PROP_ACCOSUBTYPEARR);
		reqBdyJson.put(JSON_PROP_ACCOSUBTYPE, accoSubTypeArr.get(0));
		boolean testMultiSupp = suppResMap.size() > 1 && mMultiSupp ? true : false;
		String supp = "";
		if (testMultiSupp) {
			for (Entry<String, List<JSONObject>> entry : suppResMap.entrySet()) {
				supp = entry.getKey();
				if (mMultiSuppLst.size() != 0 && !mMultiSuppLst.contains(entry.getKey())) {
					continue;
				}
				JSONObject tempBdy = new JSONObject(reqBdyJson.toString());
				mapRoomInfo(tempBdy.getJSONArray(JSON_PROP_REQUESTEDROOMARR), new JSONArray(entry.getValue()), supp);
				reqBdyJson.put(JSON_PROP_SUPPREF, supp);
				multiReqArr.put(reqBdyJson);
			}
		} else {
			int randomNo = getRandomNumber(suppResMap.size());
			int cnt = 0;
			List<JSONObject> roomStayLst = null;
			for (Entry<String, List<JSONObject>> entry : suppResMap.entrySet()) {
				roomStayLst = entry.getValue();
				if (cnt == randomNo) {
					supp = entry.getKey();
					break;
				}
			}
			mapRoomInfo(reqBdyJson.getJSONArray(JSON_PROP_REQUESTEDROOMARR), new JSONArray(roomStayLst), supp);
			reqBdyJson.put(JSON_PROP_SUPPREF, supp);
			multiReqArr.put(reqBdyJson);
		}

	}

	void mapPrc2Reprc(JSONObject reqJson, JSONObject prcRes) {

		JSONArray multiResJsonArr = prcRes.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONArray multiReqJsonArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		for (int i = 0; i < multiResJsonArr.length(); i++) {
			JSONArray roomJsonArr = ((JSONObject) multiResJsonArr.get(i)).getJSONArray(JSON_PROP_ROOMSTAYARR);
			JSONArray reqBdyRoomJsonArr = ((JSONObject) multiReqJsonArr.get(i))
					.getJSONArray(JSON_PROP_REQUESTEDROOMARR);
			mapRoomInfo(reqBdyRoomJsonArr, roomJsonArr, ((JSONObject) roomJsonArr.get(0)).getString(JSON_PROP_SUPPREF));
		}

	}

	void mapReprc2Book(JSONObject reqJson, JSONObject rprcRes) {
        reqJson.getJSONObject(JSON_PROP_REQBODY).put("bookID", "12345");
		JSONArray multiResJsonArr = rprcRes.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONArray multiReqJsonArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_ACCOMODATIONARR);
		for (int i = 0; i < multiResJsonArr.length(); i++) {
			JSONArray roomJsonArr = ((JSONObject) multiResJsonArr.get(i)).getJSONArray(JSON_PROP_ROOMSTAYARR);
			JSONArray reqBdyRoomJsonArr = ((JSONObject) multiReqJsonArr.get(i))
					.getJSONArray(JSON_PROP_REQUESTEDROOMARR);
			mapRoomInfo(reqBdyRoomJsonArr, roomJsonArr, ((JSONObject) roomJsonArr.get(0)).getString(JSON_PROP_SUPPREF));
			mapPaxInfo(reqBdyRoomJsonArr);
		}

	}

	void mapRoomInfo(JSONArray roomConfigArr, JSONArray roomStayArr, String supp) {// Whatever changes it does in
																					// roomstayarry are added in reqbody
																					// of price
		List<ArrayList<JSONObject>> roomsLst = new ArrayList<ArrayList<JSONObject>>();
		for (Object roomJson : roomStayArr) {
			if (!supp.equals(((JSONObject) roomJson).getString(JSON_PROP_SUPPREF)))
				continue;
			// SI rph starts from 1
			int ind = ((JSONObject) roomJson).getJSONObject(JSON_PROP_ROOMINFO).getInt(JSON_PROP_ROOMINDEX)-1 ;
			if (roomsLst.isEmpty() || roomsLst.size() <= ind)
				roomsLst.add(new ArrayList<JSONObject>());
			roomsLst.get(ind).add((JSONObject) roomJson);
		}
		JSONObject reqRoomJson;
		List<JSONObject> roomLst;
		String currHotelCode = null;
		for (int i = 0; i < roomConfigArr.length(); i++) {
			reqRoomJson = (JSONObject) roomConfigArr.get(i);
			roomLst = roomsLst.get(i);

			JSONObject room = roomLst.get(getRandomNumber(roomLst.size())).getJSONObject(JSON_PROP_ROOMINFO);
	  
				String hotelCode = room.getJSONObject("hotelInfo").getString("hotelCode");
				if (i == 0) {
					reqRoomJson.put(JSON_PROP_ROOMINFO, room);
					currHotelCode = room.getJSONObject("hotelInfo").getString("hotelCode");
				} else {
					if (hotelCode.equals(currHotelCode)) {
						reqRoomJson.put(JSON_PROP_ROOMINFO, room);
					} else {
						i--;
					}
				}

			} 
		}
	

	void mapPaxInfo(JSONArray roomConfigArr) {
		JSONObject reqRoomJson;
		int adCnt;
		JSONArray chdAges;
		char name = 'a';
		for (int i = 0; i < roomConfigArr.length(); i++) {
			reqRoomJson = (JSONObject) roomConfigArr.get(i);
			adCnt = (int) reqRoomJson.remove("adultCount");
			chdAges = (JSONArray) reqRoomJson.remove("childAges");
			boolean lead = true;
			while (adCnt > 0) {
				reqRoomJson.append("paxInfo", getPaxJson(true, lead, String.valueOf(name), 0));
				lead = false;
				name++;
				adCnt--;
			}
			for (Object age : chdAges) {
				reqRoomJson.append("paxInfo", getPaxJson(false, false, String.valueOf(name), (int) age));
				name++;
			}

		}

	}

	JSONObject getPaxJson(boolean isAdult, boolean isLead, String paxName, int chdAge) {

		JSONObject paxJson = new JSONObject(mpaxShell);
		paxJson.put("paxType", isAdult ? Pax_ADT : Pax_CHD);
		paxJson.put("isLeadPax", isLead ? true : false);
		int chdYr = Calendar.getInstance().get(Calendar.YEAR) - chdAge;
		paxJson.put("dob",
				isAdult ? "1990-01-01" : String.format("%s-01-01", chdYr));
		paxJson.put("firstName", paxName);
		return paxJson;
	}

	// *****************MAPPING ENDS************************//
	public JSONObject process(JSONObject reqJson) {

		JSONObject finalResJson = new JSONObject();
		JSONArray finalResJsonArr = new JSONArray();
		finalResJson.put("testSuiteOP", finalResJsonArr);
		
		

		/*
		 * JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		 * JSONObject reqBdyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		 */
		JSONObject currRes;
		reqJson.remove("testConfig");

		currRes = addResForOperation(OPERATION_NAME_SEARCH, reqJson, finalResJsonArr);
		if (!validateSrchRes(reqJson, currRes) || --mBrkAftr < 1)
			return finalResJson;
		mapSrch2Price(reqJson, currRes);
		currRes = addResForOperation(OPERATION_NAME_PRICE, reqJson, finalResJsonArr);
		if (!validatePrcRes(reqJson, currRes) || --mBrkAftr < 1)
			return finalResJson;
		mapPrc2Reprc(reqJson, currRes);
		currRes = addResForOperation(OPERATION_NAME_REPRICE, reqJson, finalResJsonArr);
		if (!validateReprcRes(reqJson, currRes) || --mBrkAftr < 1)
			return finalResJson;
		mapReprc2Book(reqJson, currRes);
		currRes = addResForOperation(OPERATION_NAME_BOOK, reqJson, finalResJsonArr);
		if (!validateBookRes(reqJson, currRes) || --mBrkAftr < 1)
			return finalResJson;
		mapBook2Cancel(reqJson, currRes);
		currRes = addResForOperation(OPERATION_NAME_CANCEL, reqJson, finalResJsonArr);
		
		return finalResJson;
	}

	private void mapBook2Cancel(JSONObject reqJson, JSONObject bookRes) {
		JSONArray CancelReservation=new JSONArray();
		JSONObject cancelObj=new JSONObject();
		JSONObject roomObj=new JSONObject();
		JSONArray roomStayArr=new JSONArray();
		JSONArray cancelRoomStayArr=new JSONArray();
		JSONObject roomStayObj=new JSONObject();
		JSONObject reqBdyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONArray multiResJsonArr = bookRes.getJSONArray(JSON_PROP_ACCOMODATIONARR);
		JSONArray roomJsonArr = ((JSONObject) multiResJsonArr.get(0)).getJSONArray("supplierBookReferences");
		cancelObj.put(JSON_PROP_SUPPREF, ((JSONObject) multiResJsonArr.get(0)).getString(JSON_PROP_SUPPREF));
		//cancelObj.put(JSON_PROP_UNIQUEID, roomJsonArr);
		/*roomStayArr=multiResJsonArr.getJSONObject(0).getJSONArray(JSON_PROP_ROOMSTAYARR);
	
		for(int k=0;k<roomStayArr.length();k++)
		{
			roomObj=(JSONObject) roomStayArr.get(k);
			roomStayObj=new JSONObject();
			roomStayObj.put(JSON_PROP_HOTELCODE,roomObj.getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_HOTELINFO).getString(JSON_PROP_HOTELCODE) );
			roomStayObj.put(JSON_PROP_ROOMTYPECODE,roomObj.getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_ROOMTYPEINFO).getString(JSON_PROP_ROOMTYPECODE) );
			roomStayObj.put(JSON_PROP_MEALCODE,roomObj.getJSONObject(JSON_PROP_ROOMINFO).getJSONObject(JSON_PROP_MEALINFO).getString(JSON_PROP_MEALCODE) );
			cancelRoomStayArr.put(roomStayObj);
		}
		cancelObj.put(JSON_PROP_ROOMSTAYARR, cancelRoomStayArr);*/
		CancelReservation.put(cancelObj);
	        reqBdyJson.remove(JSON_PROP_ACCOMODATIONARR);
		//reqBdyJson.put(JSON_PROP_CANCELRESERVATION, CancelReservation);// Accomodation
		
		
	}

	private boolean validateBookRes(JSONObject reqJson, JSONObject currRes) {
		//todo
		return true;
	}

	protected JSONObject addResForOperation(String operationName, JSONObject reqJson, JSONArray finalResJsonArr) {

		reqJson.getJSONObject(JSON_PROP_REQHEADER).put("userID", String.format("WEM-%s", operationName));
		JSONObject resJson = consumeBEService(operationName, reqJson);

		JSONObject addedResJson = new JSONObject();
		addedResJson.put("operation", operationName);
		addedResJson.put("input", new JSONObject(reqJson.toString()));
		addedResJson.put("output", resJson);

		finalResJsonArr.put(addedResJson);

		return resJson;
	}
}
