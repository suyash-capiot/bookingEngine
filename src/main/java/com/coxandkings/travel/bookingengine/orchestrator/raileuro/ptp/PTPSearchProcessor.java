package com.coxandkings.travel.bookingengine.orchestrator.raileuro.ptp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.raileuro.RailEuroConfig;
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.PriceComponentsGroup;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.RailEuropeConstants;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;



public class PTPSearchProcessor implements RailEuropeConstants{

	private static final Logger logger = LogManager.getLogger(PTPSearchProcessor.class);
	private static String mIncvPriceCompFormat = JSON_PROP_INCENTIVE.concat(".%s@").concat(JSON_PROP_CODE);

	protected static void createHeader(JSONObject reqHdrJson ,Element reqElem) {
		Element sessionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr:RequestHeader/com:SessionID");
		sessionElem.setTextContent(reqHdrJson.getString(JSON_PROP_SESSIONID));

		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr:RequestHeader/com:TransactionID");
		transactionElem.setTextContent(reqHdrJson.getString(JSON_PROP_TRANSACTID));

		Element userElem = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr:RequestHeader/com:UserID");
		userElem.setTextContent(reqHdrJson.getString(JSON_PROP_USERID));
	}

	public static String process(JSONObject reqJson) throws Exception {

		try {
			TrackingContext.setTrackingContext(reqJson);
			//OperationConfig opConfig = RailEuroConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			ServiceConfig opConfig = RailEuroConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_RAILEUROPE);
			if(prodSuppliers==null || prodSuppliers.size()==0)
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for Rail Euro subType ptp");		
			
			createHeader(reqHdrJson, reqElem);

			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./wwr:RequestHeader/com:SupplierCredentialsList");

			for (ProductSupplier prodSupplier : prodSuppliers) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}

			JSONObject railEuropeReq = reqBodyJson.getJSONObject(JSON_PROP_RAILEUROPEOBJ);

			if(railEuropeReq.getString(JSON_PROP_PRODUCTTYPE).equalsIgnoreCase(JSON_PROP_PRODUCTTYPE_PTP)) {
				populateRequestBodyPTP(reqElem, ownerDoc, railEuropeReq);
			}

			else if(railEuropeReq.getString(JSON_PROP_PRODUCTTYPE).equalsIgnoreCase(JSON_PROP_PRODUCTTYPE_NERO_PTP)) {
				populateRequestBodyNPTP(reqElem, ownerDoc, railEuropeReq);
			}
//			System.out.println(XMLTransformer.toString(reqElem));
			Element resElem = null;
//			logger.trace(String.format("SI XML Request = %s", XMLTransformer.toString(reqElem)));
//			System.out.println("reqElem->"+XMLTransformer.toString(reqElem));
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), RailEuroConfig.getHttpHeaders(), "POST", 900000, reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), "POST", 900000, reqElem);
			logger.info("RailEuroPTP SI RESPONSE"+resElem);
//			HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), RailEuroConfig.getHttpHeaders(), "POST", 120000, reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

//			System.out.println(XMLTransformer.toString(resElem));
			JSONObject resBodyJson = new JSONObject();
			JSONArray wwRailJsonArr = new JSONArray();

			Element wrapperElem = XMLUtils.getFirstElementAtXPath(resElem,
					"./wwr1:ResponseBody/wwr:OTA_WWRailShopRSWrapper");

			JSONArray solutionArr = new JSONArray();
				String supplierID = XMLUtils.getValueAtXPath(wrapperElem,"./wwr:SupplierID");
				String isRoundTrip = XMLUtils.getValueAtXPath(wrapperElem,"./ota:OTA_WWRailShopRS/ota:Content/ota:BuildPackageContent/ota:IsRoundtrip");

				JSONObject wwrailJsonObj = new JSONObject();
				wwrailJsonObj.put(JSON_PROP_SUPPLIERID,supplierID);
				wwrailJsonObj.put(JSON_PROP_ISROUNDTRIP ,isRoundTrip);

				Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElem,
						"./ota:OTA_WWRailShopRS/ota:Content/ota:BuildPackageContent/ota:ForwardItinerary");
				getSupplierResponseJSON(resBodyElem, solutionArr, "forward", supplierID);

				if(isRoundTrip.equalsIgnoreCase("true")) {
					resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElem,
							"./ota:OTA_WWRailShopRS/ota:Content/ota:BuildPackageContent/ota:ReturnItinerary");
					getSupplierResponseJSON(resBodyElem, solutionArr, "return", supplierID);

				}
				wwrailJsonObj.put(JSON_PROP_SOLUTIONS,solutionArr);	
				wwRailJsonArr.put(wwrailJsonObj);

			resBodyJson.put(JSON_PROP_WWRAILARR, wwRailJsonArr);

			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
//			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));


			Map<String, Integer> suppResToBRIIndex = new HashMap<String, Integer>();
			// Call BRMS Supplier and Client Commercials
			JSONObject resSupplierJson = SupplierCommercials.getSupplierCommercials(CommercialsOperation.Search, reqJson, resJson, suppResToBRIIndex);
//			System.out.println("resSupplier->"+resSupplierJson.toString());
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s",
						resSupplierJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}

			JSONObject resClientJson = ClientCommercials.getClientCommercials(reqJson, resJson, resSupplierJson);
//			System.out.println("resClient->"+resClientJson.toString());
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s",
						resClientJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}

//			System.out.println("BeforeCalculatePrices"+resJson.toString());
			calculatePrices(reqJson, resJson, resSupplierJson, resClientJson, suppResToBRIIndex, true, usrCtx);
//			System.out.println("AfterCalculatePrices"+resJson.toString());
			// Calculate company taxes
			TaxEngine.getCompanyTaxes(reqJson, resJson);

//			System.out.println(resJson);
			pushSuppFaresToRedisAndRemove(resJson);
			return resJson.toString();
		}

		catch(Exception x) {
			logger.error("(PTP)Exception received while processing", x);
			x.printStackTrace();
			return "{\"status\": \"ERROR\"}";
		}
	}
	
	private static void pushSuppFaresToRedisAndRemove(JSONObject resJson) {

		JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray wwRailJsonArr = resBodyJson.getJSONArray(JSON_PROP_WWRAILARR);
		Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();

		for (int i=0; i < wwRailJsonArr.length(); i++)
		{
			JSONArray solutionArr = wwRailJsonArr.getJSONObject(i).getJSONArray(JSON_PROP_SOLUTIONS);
			for(int j=0; j < solutionArr.length() ;j++)
			{
				JSONObject solutionJson = solutionArr.getJSONObject(j);
				JSONArray packageDetailsArr = solutionJson.getJSONArray(JSON_PROP_PACKAGEDETAILS);
				for(int k = 0; k<packageDetailsArr.length();k++)
				{
					JSONObject packageDetailsJson = packageDetailsArr.getJSONObject(k);
					JSONObject suppPriceBookInfoJson = new JSONObject();
					JSONObject suppPriceInfoJson = packageDetailsJson.optJSONObject(JSON_PROP_SUPPPRICEINFO);
					packageDetailsJson.remove(JSON_PROP_SUPPPRICEINFO);

					//Getting ClientCommercials Info
					JSONObject totalPriceInfo = packageDetailsJson.getJSONObject(JSON_PROP_TOTALPRICEINFO);
					JSONArray clientCommercialsTotalJsonArr = totalPriceInfo.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
					totalPriceInfo.remove(JSON_PROP_CLIENTENTITYTOTALCOMMS);

					if ( suppPriceInfoJson == null) {
						// TODO: This should never happen. Log a warning message here.
						continue;
					}
					suppPriceBookInfoJson.put(JSON_PROP_SUPPPRICEINFO, suppPriceInfoJson);
					suppPriceBookInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercialsTotalJsonArr);
					reprcSuppFaresMap.put(getRedisKeyForPackageDetails(packageDetailsJson), suppPriceBookInfoJson.toString());
				}
			}
		}

		try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
		String redisKey = String.format("%s%c%s", resHdrJson.optString(JSON_PROP_SESSIONID), '|', "EuropeanRailPTP");
		redisConn.hmset(redisKey, reprcSuppFaresMap);
		redisConn.pexpire(redisKey, (long) (RailEuroConfig.getRedisTTLMinutes() * 60 * 1000));
		}
		//RedisConfig.releaseRedisConnectionToPool(redisConn);
	}
	
	static String getRedisKeyForPackageDetails(JSONObject packageDetailsJson) {
		
		List<String> keys = new ArrayList<>();
		String pckgFareID = packageDetailsJson.optString(JSON_PROP_PCKGFAREID);
		keys.add(pckgFareID);
		String key = keys.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(Character.valueOf('|').toString()));
		return key;

	}
	
	public static void populateRequestBodyPTP(Element reqElem, Document ownerDoc, JSONObject railEuropeReq) throws Exception {

		int nPax = 0, nAdult = 0, nChild = 0, nYouth = 0, nSenior = 0;
		Element wwRailShopElem = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody/ota:OTA_WWRailShopRQ");

		wwRailShopElem.setAttribute(JSON_PROP_PRODUCTTYPE, PRODUCTTYPE_PTP);
		Element product = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PTPProducts");

		if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_DESTINATIONCITYNAME))) {
			Element destinationCityName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DestinationCityName");
			destinationCityName.setTextContent(railEuropeReq.getString(JSON_PROP_DESTINATIONCITYNAME));
			product.appendChild(destinationCityName);

			if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_ORIGINCITYNAME))) {
				Element originCityName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:OriginCityName");
				originCityName.setTextContent(railEuropeReq.getString(JSON_PROP_ORIGINCITYNAME));
				product.insertBefore(originCityName, destinationCityName);
			}
		}	

		if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_DESTINATIONCITYCODE))) {
			Element destinationCityCode = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DestinationCityCode");
			destinationCityCode.setTextContent(railEuropeReq.getString(JSON_PROP_DESTINATIONCITYCODE));
			product.appendChild(destinationCityCode);

			if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_ORIGINCITYCODE))) {
				Element originCityCode = ownerDoc.createElementNS(Constants.NS_OTA, "ota:OriginCityCode");
				originCityCode.setTextContent(railEuropeReq.getString(JSON_PROP_ORIGINCITYCODE));
				product.insertBefore(originCityCode,destinationCityCode);
			}
		}

		if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_DESTINATIONSTATIONCODE))) {
			Element destinationStationCode = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DestinationStationCode");
			destinationStationCode.setTextContent(railEuropeReq.getString(JSON_PROP_DESTINATIONSTATIONCODE));
			product.appendChild(destinationStationCode);

			if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_ORIGINSTATIONCODE))) {
				Element originStationCode = ownerDoc.createElementNS(Constants.NS_OTA, "ota:OriginStationCode");
				originStationCode.setTextContent(railEuropeReq.getString(JSON_PROP_ORIGINSTATIONCODE));
				product.insertBefore(originStationCode,destinationStationCode);
			}
		}

		if(Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_JOURNEYTYPE))) {	
			Element directTrainsOnly = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DirectTrainsOnly");
			if(railEuropeReq.getString(JSON_PROP_JOURNEYTYPE).equalsIgnoreCase("MultiCity")) {
				directTrainsOnly.setTextContent("false");	
			}
			else {
				directTrainsOnly.setTextContent("true");	
			}
			product.appendChild(directTrainsOnly);
		}

		if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_ORIGINSTATIONCODE))) {
			Element departureDate = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DepartureDate");
			departureDate.setTextContent(railEuropeReq.getString(JSON_PROP_ORIGINSTATIONCODE));
			product.appendChild(departureDate);
		}

		if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_DEPARTUREDATE))) {
			Element departureDate = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DepartureDate");
			departureDate.setTextContent(railEuropeReq.getString(JSON_PROP_DEPARTUREDATE));
			product.appendChild(departureDate);
		}

		if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_DEPARTURETIME))) {
			Element departureTime = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DepartureTime");
			Element departureTimeHigh = ownerDoc.createElementNS(Constants.NS_OTA, "ota:High");
			departureTimeHigh.setTextContent(railEuropeReq.optString("departureTimeHigh"));
			departureTime.appendChild(departureTimeHigh);
			Element departureTimeLow = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Low");
			departureTimeLow.setTextContent(railEuropeReq.getString("departureTimeLow"));
			departureTime.insertBefore(departureTimeLow, departureTimeHigh);
			product.appendChild(departureTime);
		}

		if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_RETURNDATE))) {
			Element returnDate = ownerDoc.createElementNS(Constants.NS_OTA, "ota:ReturnDate");
			returnDate.setTextContent(railEuropeReq.getString(JSON_PROP_RETURNDATE));
			product.appendChild(returnDate);
		}

		if (Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_RETURNTIME))) {
			Element returnTime = ownerDoc.createElementNS(Constants.NS_OTA, "ota:ReturnTime");
			Element returnTimeHigh = ownerDoc.createElementNS(Constants.NS_OTA, "ota:High");
			returnTimeHigh.setTextContent(railEuropeReq.optString("returnTimeHigh"));
			returnTime.appendChild(returnTimeHigh);
			Element returnTimeLow = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Low");
			returnTimeLow.setTextContent(railEuropeReq.optString("returnTimeLow"));
			returnTime.insertBefore(returnTimeLow,returnTimeHigh);
			product.appendChild(returnTime);
		}

		//TODO: Values are hard-coded as of now. Need changes.
		nPax = Integer.parseInt(railEuropeReq.getString(JSON_PROP_NPASSENGERS));
		JSONArray paxAge = railEuropeReq.getJSONArray(JSON_PROP_PAXAGE);

		ArrayList<Integer> cAge = new ArrayList<>();
		ArrayList<Integer> yAge = new ArrayList<>();

		for(int i = 0; i < paxAge.length(); i++) {
			int age = paxAge.getInt(i);
			if(age>=18)
				nAdult++;
			if(age>0 && age<12) {
				nChild++;	
				cAge.add(age);
			}		
			if(age>=12 && age<27) {
				nYouth++;	
				yAge.add(age);
			}
		}

		Element NAdults = ownerDoc.createElementNS(Constants.NS_OTA, "ota:NAdults");	
		NAdults.setTextContent(Integer.toString(nAdult));
		product.appendChild(NAdults);

		Element NChildren = ownerDoc.createElementNS(Constants.NS_OTA, "ota:NChildren");
		NChildren.setTextContent(Integer.toString(nChild));
		product.appendChild(NChildren);

		Element childrenAge= ownerDoc.createElementNS(Constants.NS_OTA, "ota:ChildrenAge");
		for(Integer age : cAge) {
			Element childrenAgeInt= ownerDoc.createElementNS(Constants.NS_OTA, "ota:Int");
			childrenAgeInt.setTextContent(Integer.toString(age));
			childrenAge.appendChild(childrenAgeInt);
		}
		product.appendChild(childrenAge);

		Element NYouth = ownerDoc.createElementNS(Constants.NS_OTA, "ota:NYouth");
		NYouth.setTextContent(Integer.toString(nYouth));
		product.appendChild(NYouth);

		Element youthAge= ownerDoc.createElementNS(Constants.NS_OTA, "ota:YouthAge");
		for(Integer age : yAge) {
			Element youthAgeInt= ownerDoc.createElementNS(Constants.NS_OTA, "Int");
			youthAgeInt.setTextContent(Integer.toString(age));
			youthAge.appendChild(youthAgeInt);
		}
		product.appendChild(youthAge);

		Element NSeniors = ownerDoc.createElementNS(Constants.NS_OTA, "ota:NSeniors");
		NSeniors.setTextContent(Integer.toString(nSenior));
		product.appendChild(NSeniors);

		Element nPassHolders = ownerDoc.createElementNS(Constants.NS_OTA, "ota:NPassHolders");
		nPassHolders.setTextContent("0");
		product.appendChild(nPassHolders);

//		Element travellingPartyDetails = ownerDoc.createElementNS(Constants.NS_OTA, "ota:TravellingPartyDetails");
//		Element travellingPartyDetail = ownerDoc.createElementNS(Constants.NS_OTA, "ota:TravellingPartyDetail");
//		Element passengerType = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PassengerType");
//		passengerType.setTextContent("Adult");
//		travellingPartyDetail.appendChild(passengerType);
//		travellingPartyDetails.appendChild(travellingPartyDetail);

		Element packageType = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PackageType");
		packageType.setTextContent(PACKAGETYPE);
		product.appendChild(packageType);

		Element fareTypes = ownerDoc.createElementNS(Constants.NS_OTA, "ota:FareTypes");
		fareTypes.setTextContent(FARETYPES);
		product.appendChild(fareTypes);

		if(Utils.isStringNotNullAndNotEmpty(railEuropeReq.optString(JSON_PROP_JOURNEYTYPE))) {	
			Element roundtripMode = ownerDoc.createElementNS(Constants.NS_OTA, "ota:RoundtripMode");
			if(railEuropeReq.getString(JSON_PROP_JOURNEYTYPE).equalsIgnoreCase("Oneway")) {
				roundtripMode.setTextContent("FORWARD_ONLY");	
			}
			else if(railEuropeReq.getString(JSON_PROP_JOURNEYTYPE).equalsIgnoreCase("Return")){
				roundtripMode.setTextContent("FORWARD_AND_RETURN");	
			}
			product.appendChild(roundtripMode);
		}

		Element groupMode = ownerDoc.createElementNS(Constants.NS_OTA, "ota:GroupMode");
		groupMode.setTextContent(GROUPMODE);
		product.appendChild(groupMode);

		wwRailShopElem.appendChild(product);

	}

	public static void populateRequestBodyNPTP(Element reqElem, Document ownerDoc, JSONObject railEuropeReq) throws Exception {

		Element wwRailShopElem = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody/ota:OTA_WWRailShopRQ");
		wwRailShopElem.setAttribute(JSON_PROP_PRODUCTTYPE, PRODUCTTYPE_NERO_PTP);
	}

	public static void getSupplierResponseJSON(Element resBodyElem, JSONArray solutionArr, String itineraryType, String supplierID) {

		String fromCityCode = XMLUtils.getValueAtXPath(resBodyElem, "./ota:FromCityCode");
		String toCityCode = XMLUtils.getValueAtXPath(resBodyElem, "./ota:ToCityCode");

		Element[] solutions =  XMLUtils.getElementsAtXPath(resBodyElem,"./ota:Solutions/ota:Solution");

		for(Element solution : solutions) {

			String classType = null;
			JSONArray packageDetails = new JSONArray();
			JSONObject solutionObj = new JSONObject();	
			solutionObj.put(JSON_PROP_SUPPREF, supplierID);
			solutionObj.put("itineraryType", itineraryType);
			solutionObj.put("fromCityCode", fromCityCode);
			solutionObj.put("toCityCode", toCityCode);
			solutionObj.put("solutionID", XMLUtils.getValueAtXPath(solution, "./ota:SolutionId"));
			solutionObj.put("tripDurationMinutes", XMLUtils.getValueAtXPath(solution, "./ota:TripDurationMinutes"));
			//if(firstClass){
			Element firstClass = XMLUtils.getFirstElementAtXPath(solution, "./ota:PackagePrices/ota:FirstClassPrices");
			classType = "First Class";
			getPackageDetails(firstClass, classType, packageDetails);
			//}
			//if() {
			Element secondClass = XMLUtils.getFirstElementAtXPath(solution, "./ota:PackagePrices/ota:secondClassPrices");
			classType = "Second Class";
			getPackageDetails(secondClass, classType, packageDetails);
			//}
			solutionObj.put("packageDetails", packageDetails);
			solutionObj.put("trainDetails", getTrainDetails(solution));
			solutionArr.put(solutionObj);
		}	
	}

	public static void getPackageDetails(Element classPrice, String classType, JSONArray packageArr) {

		Element[] accoPrices =  XMLUtils.getElementsAtXPath(classPrice,"./ota:AccommodationPrice");

		for(Element accoPrice : accoPrices) {
			String accoCode = XMLUtils.getValueAtXPath(accoPrice, "./ota:AccommodationCode");
			Element[] packages =  XMLUtils.getElementsAtXPath(accoPrice,"./ota:Packages/ota:Package");
			for(Element packageDetails: packages) {
				JSONObject packageObj = new JSONObject();
				packageObj.put("classType", classType);
				packageObj.put("accommodationCode", accoCode);
				packageObj.put("packageType", XMLUtils.getValueAtXPath(packageDetails,"./ota:PackageType"));
				packageObj.put("packageFareId", XMLUtils.getValueAtXPath(packageDetails,"./ota:PackageFareId"));
				packageObj.put("reservationStatus", XMLUtils.getValueAtXPath(packageDetails,"./ota:ReservationStatus"));
				packageObj.put("reservationStatus", XMLUtils.getValueAtXPath(packageDetails,"./ota:ReservationStatus"));
				//remove during SIT retPckgId
				packageObj.put("returnPackageFareID", XMLUtils.getValueAtXPath(packageDetails,"./ota:CompatiblePackagesOnOtherLeg/ota:CompatiblePackage/ota:ReturnPackageFareID"));
				packageObj.put(JSON_PROP_TOTALPRICEINFO, getTotalPriceJson(XMLUtils.getFirstElementAtXPath(packageDetails, "./ota:Price")));
				packageObj.put("productFare", getProductFare(packageDetails));	
				//packageObj.put("compatiblePackage", getCompatiblePackage(packageDetails));
				packageArr.put(packageObj);
			}			
		}
	}

	public static JSONObject getTotalPriceJson(Element priceElem){

		JSONObject priceInfo = new JSONObject();
		JSONObject totalFareJson = new JSONObject();

		totalFareJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(priceElem, "./ota:CurrencyCode"));
		totalFareJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(priceElem, "./ota:Amount"), 0));

		priceInfo.put("totalFare", totalFareJson);
		return priceInfo;
	}

	public static JSONArray getProductFare(Element packageDetails) {
		Element [] productFares =  XMLUtils.getElementsAtXPath(packageDetails,"./ota:FareDetails/ota:ProductFare");
		JSONArray prodFare = new JSONArray();
		for(Element productFare : productFares) {
			JSONObject productFareObj = new JSONObject();
			productFareObj.put("segmentId", XMLUtils.getValueAtXPath(productFare,"./ota:ApplicableSegments/ota:SegmentId/ota:SegmentId"));
			productFareObj.put("familyId", XMLUtils.getValueAtXPath(productFare,"./ota:FamilyId"));
			productFareObj.put("familyName", XMLUtils.getValueAtXPath(productFare,"./ota:FamilyName"));
			productFareObj.put("availableSeats", XMLUtils.getValueAtXPath(productFare,"./ota:SeatAvailabilityLevel"));
			productFareObj.put("nPassengers", XMLUtils.getValueAtXPath(productFare,"./ota:NumberOfPassengers"));
			productFareObj.put("basePassengerType", XMLUtils.getValueAtXPath(productFare,"./ota:BasePassengerType"));
			productFareObj.put("fareName", XMLUtils.getValueAtXPath(productFare,"./ota:FareName"));
			productFareObj.put("fareType", XMLUtils.getValueAtXPath(productFare,"./ota:FareType"));
			productFareObj.put("accommodationId", XMLUtils.getValueAtXPath(productFare,"./ota:AccommodationId"));
			productFareObj.put("accommodationName", XMLUtils.getValueAtXPath(productFare,"./ota:AccommodationName"));
			productFareObj.put(JSON_PROP_PRODUCTPRICEINFO, getTotalPriceJson(XMLUtils.getFirstElementAtXPath(productFare, "./ota:ProductPrice")));
			productFareObj.put("reservationStatus", XMLUtils.getValueAtXPath(productFare,"./ota:ReservationStatus"));
			productFareObj.put("preference", getPeferences(productFare));

			prodFare.put(productFareObj);
		}
		return prodFare;
	}

	public static JSONArray  getPeferences(Element productFare) {

		Element[] peferences = XMLUtils.getElementsAtXPath(productFare,"./ota:Preferences/ota:Preference");
		JSONArray peferenceArr = new JSONArray();
		for(Element peference : peferences) {
			JSONObject peferenceObj = new JSONObject();
			peferenceObj.put("type", XMLUtils.getValueAtXPath(peference,"./ota:Type"));
			peferenceObj.put("description", XMLUtils.getValueAtXPath(peference,"./ota:Description"));
			peferenceObj.put("options", XMLUtils.getValueAtXPath(peference,"./ota:Options"));
			peferenceObj.put("isMandatory", XMLUtils.getValueAtXPath(peference,"./ota:IsMandatory"));

			peferenceArr.put(peferenceObj);
		}
		return peferenceArr;
	}

	public static JSONArray  getCompatiblePackage(Element packageDetails) {

		Element[] compatiblePackages = XMLUtils.getElementsAtXPath(packageDetails,"./ota:CompatiblePackagesOnOtherLeg/ota:CompatiblePackage");
		JSONArray cpArr = new JSONArray();
		for(Element cp : compatiblePackages) {
			JSONObject cpObj = new JSONObject();
			cpObj.put("returnPackageFareID", XMLUtils.getValueAtXPath(cp,"./ota:ReturnPackageFareID"));
			cpObj.put("amount", XMLUtils.getValueAtXPath(cp,"./ota:Price/ota:Amount"));
			cpObj.put("currencyCode", XMLUtils.getValueAtXPath(cp,"./ota:Price/ota:CurrencyCode"));

			cpArr.put(cpObj);
		}
		return cpArr;
	}

	public static JSONArray getTrainDetails(Element solution) {

		Element[] segments =  XMLUtils.getElementsAtXPath(solution,"./ota:Segments/ota:Segment");

		JSONArray segmentArr = new JSONArray();
		for(Element segment: segments) {
			JSONObject segmentObj = new JSONObject();
			segmentObj.put("segmentId", XMLUtils.getValueAtXPath(segment,"./ota:SegmentId"));
			segmentObj.put("departureDateTime", XMLUtils.getValueAtXPath(segment,"./ota:DepartureDateTime"));
			segmentObj.put("arrivalDateTime", XMLUtils.getValueAtXPath(segment,"./ota:ArrivalDateTime"));
			segmentObj.put("originCityCode", XMLUtils.getValueAtXPath(segment,"./ota:OriginCityCode"));
			segmentObj.put("originCityName", XMLUtils.getValueAtXPath(segment,"./ota:OriginCityName"));
			segmentObj.put("destinationCityCode", XMLUtils.getValueAtXPath(segment,"./ota:DestinationCityCode"));
			segmentObj.put("destinationCityName", XMLUtils.getValueAtXPath(segment,"./ota:DestinationCityName"));
			segmentObj.put("originStationCode", XMLUtils.getValueAtXPath(segment,"./ota:OriginStationCode"));
			segmentObj.put("originStationName", XMLUtils.getValueAtXPath(segment,"./ota:OriginStationName"));
			segmentObj.put("destinationStationCode", XMLUtils.getValueAtXPath(segment,"./ota:DestinationStationCode"));
			segmentObj.put("destinationStationName", XMLUtils.getValueAtXPath(segment,"./ota:DestinationStationName"));
			segmentObj.put("carrierCode", XMLUtils.getValueAtXPath(segment,"./ota:Carrier/ota:Code"));
			segmentObj.put("trainNumber", XMLUtils.getValueAtXPath(segment,"./ota:Train/ota:TrainNumber"));
			segmentObj.put("trainTypeId", XMLUtils.getValueAtXPath(segment,"./ota:Train/ota:TrainTypeId"));
			segmentObj.put("nStops", XMLUtils.getValueAtXPath(segment,"./ota:NStops"));
			segmentObj.put("durationMins", XMLUtils.getValueAtXPath(segment,"./ota:DurationMins"));

			segmentArr.put(segmentObj);
		}
		return segmentArr;
	}

	public static JSONObject getEmptyResponse(JSONObject reqHdrJson) {
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, new JSONObject());
		return resJson;
	}

	public static void calculatePrices(JSONObject reqJson, JSONObject resJson, JSONObject suppCommResJson, JSONObject clientCommResJson, Map<String, Integer> suppResToBRIIndex, boolean retainSuppFares, UserContext usrCtx) {
		JSONArray resWWRailInfoArr = resJson.getJSONObject(JSON_PROP_RESBODY).optJSONArray(JSON_PROP_WWRAILARR);
		Map<String, String> suppCommToTypeMap = getSupplierCommercialsAndTheirType(suppCommResJson);
		Map<String, String> clntCommToTypeMap = getClientCommercialsAndTheirType(clientCommResJson);
		
		ClientType clientType = ClientType.valueOf(reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
		String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);

		Map<String,BigDecimal> paxCountsMap = new HashMap<String, BigDecimal>();
		Map<Integer, JSONArray> ccommSuppBRIJsonMap = getBRIWiseTrainDetailsFromClientCommercials(clientCommResJson);
		//Map<String, Integer> suppIndexMap = new HashMap<String, Integer>();

		for (int i=0; i < resWWRailInfoArr.length(); i++) {
			JSONObject resWWRailJson = resWWRailInfoArr.getJSONObject(i);
			String suppID = resWWRailJson.getString(JSON_PROP_SUPPLIERID);

			JSONArray resSolutionJsonArr = resWWRailJson.getJSONArray(JSON_PROP_SOLUTIONS);
			for (int j = 0; j < resSolutionJsonArr.length(); j++) {
				JSONObject resSolutionJson = resSolutionJsonArr.getJSONObject(j);

				JSONArray resPackageDetailsJsonArr = resSolutionJson.getJSONArray(JSON_PROP_PACKAGEDETAILS);
				JSONArray resTrainDetailsJsonArr = resSolutionJson.getJSONArray(JSON_PROP_TRAINDETAILS);

				int count=0;
				for (int k = 0; k < resTrainDetailsJsonArr.length(); k++) {
					JSONObject resTrainDetailsJson = resTrainDetailsJsonArr.getJSONObject(k);

					String segmentID = resTrainDetailsJson.getString(JSON_PROP_SEGMENTID);

					for (int l= 0; l< resPackageDetailsJsonArr.length(); l++) {
						JSONObject resPackageDetailsJson = resPackageDetailsJsonArr.getJSONObject(l);
						JSONObject resPackageDetailsInfoJson = resPackageDetailsJson.getJSONObject(JSON_PROP_TOTALPRICEINFO); 
						
						PriceComponentsGroup totalFareCompsGroup = new PriceComponentsGroup(JSON_PROP_TOTALFARE, clientCcyCode, new BigDecimal(0), true);
						PriceComponentsGroup totalIncentivesGroup = new PriceComponentsGroup(JSON_PROP_INCENTIVES, clientCcyCode, BigDecimal.ZERO, true);
						JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
						
						JSONArray resProductFareJsonArr = resPackageDetailsJson.getJSONArray(JSON_PROP_PRODUCTFARE);
						JSONArray paxTypeFaresJsonArr = new JSONArray();
						JSONArray clientCommercialInfoArr= new JSONArray();
						for (int m = 0; m < resProductFareJsonArr.length(); m++) {
							JSONObject resProductFareJson = resProductFareJsonArr.getJSONObject(m);
							if(resProductFareJson.getString(JSON_PROP_SEGMENTID).equalsIgnoreCase(segmentID)) {
								JSONObject pricingInfoJson = resProductFareJson.getJSONObject(JSON_PROP_PRODUCTPRICEINFO);
								JSONObject totalPaxFare = pricingInfoJson.getJSONObject("totalFare");							
								String paxType = resProductFareJson.getString(JSON_PROP_BASEPAXTYPE);
								pricingInfoJson.put(JSON_PROP_PAXTYPE,paxType);
								String suppCcyCode = totalPaxFare.getString(JSON_PROP_CCYCODE);
								JSONObject suppTotalFareJson = new JSONObject(totalPaxFare.toString());
								BigDecimal paxCount = Utils.convertToBigDecimal(resProductFareJson.getString("nPassengers"),0);
								paxCountsMap.put(paxType, paxCount);

								/*Integer briNo = suppResToBRIIndex.get(String.format("%d%c%d", i, KEYSEPARATOR, j));
								int idx = (suppIndexMap.containsKey(briNo)) ? (suppIndexMap.get(briNo) + 1) : 0;
								suppIndexMap.put(briNo, idx);*/

								JSONArray ccommTrainDtlsJsonArr = ccommSuppBRIJsonMap.get(j);
								if (ccommTrainDtlsJsonArr == null) {
									logger.info(String.format(
											"BRMS/ClientComm: \"businessRuleIntake\" JSON element for supplier %s not found.", suppID));
									continue;
								}

								//Adding clientCommercialInfo
								JSONObject ccommTrainDtlsJson = ccommTrainDtlsJsonArr.getJSONObject(k);	
								JSONArray ccommPsgrDtlsJsonArr = ccommTrainDtlsJson.getJSONArray(JSON_PROP_PASSENGERDETAILS);
								JSONObject ccommPsgrDtlJson = ccommPsgrDtlsJsonArr.getJSONObject(count);
								
								JSONArray clientEntityCommJsonArr = null;
								if (ccommPsgrDtlJson == null) {
									logger.info(String.format("Passenger details not found in client commercial Train Details"));
								}
								else {
									// Only in case of reprice when (retainSuppFares == true), retain supplier commercial
									if (retainSuppFares) {
										appendSupplierCommercialsForRail(pricingInfoJson, ccommPsgrDtlJson, suppID, suppCcyCode, suppCommToTypeMap);
									}
									// From the passenger type client commercial JSON, retrieve calculated client entity commercials
									clientEntityCommJsonArr = ccommPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
									if (clientEntityCommJsonArr == null) {
										logger.warn("Client commercials calculations not found");
									}
								}	

								// Reference CKIL_231556 (1.1.2.3/BR04) The display price will be calculated as - 
								// (Total Supplier Price + Markup + Additional Company Receivable Commercials)

								BigDecimal paxTypeTotalFare = new BigDecimal(0);
				    			JSONObject commPriceJson = new JSONObject();
				    			
								JSONObject markupCalcJson = null;
								JSONArray clientCommercials= new JSONArray();
								PriceComponentsGroup paxReceivablesCompsGroup = null;
								PriceComponentsGroup totalPaxReceivablesCompsGroup = null;
								for (int y = 0; clientEntityCommJsonArr != null && y < clientEntityCommJsonArr.length(); y++) {
									JSONArray clientEntityCommercialsJsonArr = new JSONArray();
									JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(y);

									// TODO: In case of B2B, do we need to add markups for all client hierarchy levels?
									if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) {
										markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);
										clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(markupCalcJson, clntCommToTypeMap, suppCcyCode));
									}

									//Additional commercialcalc clientCommercial
									// TODO: In case of B2B, do we need to add additional receivable commercials for all client hierarchy levels?  
									JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
									JSONArray retentionCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_RETENTIONCOMMDETAILS);
									JSONArray fixedCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_FIXEDCOMMDETAILS);
									// If totals of receivables at all levels is required, the following instance creation needs to move where
									// variable 'paxReceivablesCompsGroup' is declared
									paxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode, new BigDecimal(0), true);
									totalPaxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode, new BigDecimal(0), true);
									if (additionalCommsJsonArr != null) {
										for(int p=0; p < additionalCommsJsonArr.length(); p++) {
											JSONObject additionalCommJson = additionalCommsJsonArr.getJSONObject(p);
											String additionalCommName = additionalCommJson.optString(JSON_PROP_COMMNAME);
											String additionalCommType = clntCommToTypeMap.get(additionalCommName);
											clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(additionalCommJson, clntCommToTypeMap, suppCcyCode));

											if (COMM_TYPE_RECEIVABLE.equals(additionalCommType)) {
												String additionalCommCcy = additionalCommJson.optString(JSON_PROP_COMMCCY);													
												BigDecimal additionalCommAmt = additionalCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket));
												paxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt);
												totalPaxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt.multiply(paxCount));
											}
										}
									}

									//Retention
									for (int p = 0; retentionCommsJsonArr!=null && p < retentionCommsJsonArr.length(); p++) {
										JSONObject retentionCommsJson = retentionCommsJsonArr.getJSONObject(p);
										clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(retentionCommsJson, clntCommToTypeMap, suppCcyCode));
									}

									//Fixed
									for (int p = 0; fixedCommsJsonArr!=null && p < fixedCommsJsonArr.length(); p++) {
										JSONObject fixedCommsJson = fixedCommsJsonArr.getJSONObject(p);
										clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(fixedCommsJson, clntCommToTypeMap, suppCcyCode));
									}

									JSONObject clientEntityDetailsJson = new JSONObject();
									//JSONArray clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchy();
									ClientInfo[] clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchyArray();
									//clientEntityDetailsJson = clientEntityDetailsArr.getJSONObject(k);
									clientEntityDetailsJson.put(JSON_PROP_CLIENTID, clientEntityDetailsArr[y].getClientId());
									clientEntityDetailsJson.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsArr[y].getParentClienttId());
									clientEntityDetailsJson.put(JSON_PROP_COMMENTITYTYPE, clientEntityDetailsArr[y].getCommercialsEntityType());
									clientEntityDetailsJson.put(JSON_PROP_COMMENTITYID, clientEntityDetailsArr[y].getCommercialsEntityId());
									clientEntityDetailsJson.put(JSON_PROP_CLIENTCOMM, clientEntityCommercialsJsonArr);
									clientCommercials.put(clientEntityDetailsJson);

									// For B2B clients, the incentives of the last client hierarchy level should be accumulated and returned in the response.
									if (y == (clientEntityCommJsonArr.length() - 1)) {
										for (int z = 0; z < clientEntityCommercialsJsonArr.length(); z++) {
											JSONObject clientEntityCommercialsJson = clientEntityCommercialsJsonArr.getJSONObject(z);
											if (COMM_TYPE_PAYABLE.equals(clientEntityCommercialsJson.getString(JSON_PROP_COMMTYPE))) {
												String commCcy = clientEntityCommercialsJson.getString(JSON_PROP_COMMCCY);
												String commName = clientEntityCommercialsJson.getString(JSON_PROP_COMMNAME);
												BigDecimal commAmt = clientEntityCommercialsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(commCcy, clientCcyCode, clientMarket)).multiply(paxCount, MATH_CTX_2_HALF_UP);
												totalIncentivesGroup.add(String.format(mIncvPriceCompFormat, commName), clientCcyCode, commAmt);
											}
										}
									}
								}

								BigDecimal totalFareAmt = suppTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
								//System.out.println(suppTotalFareJson);

								if (markupCalcJson != null) {
									totalFareAmt = markupCalcJson.optBigDecimal("totalFare", totalFareAmt);
								}

								commPriceJson.put(JSON_PROP_PAXTYPE, paxType);
								
								JSONObject baseFareJson = new JSONObject();
								baseFareJson.put(JSON_PROP_AMOUNT, totalFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
								baseFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);
								commPriceJson.put(JSON_PROP_BASEFARE, baseFareJson);
								paxTypeTotalFare = paxTypeTotalFare.add(baseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
								totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode, baseFareJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
								

								if (paxReceivablesCompsGroup != null && paxReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
									paxTypeTotalFare = paxTypeTotalFare.add(paxReceivablesCompsGroup.getComponentAmount());
									totalFareCompsGroup.addSubComponent(totalPaxReceivablesCompsGroup, null);
									commPriceJson.put(JSON_PROP_RECEIVABLES, paxReceivablesCompsGroup.toJSON());
								}
								
								JSONObject clientCommercialInfoJson= new JSONObject();
				    			clientCommercialInfoJson.put(JSON_PROP_PAXTYPE, paxType);
				    			clientCommercialInfoJson.put(JSON_PROP_CLIENTENTITYCOMMS, clientCommercials);
				    			clientCommercialInfoArr.put(clientCommercialInfoJson);
				    			commPriceJson.put("clientEntityCommercials", clientCommercialInfoJson.getJSONArray("clientEntityCommercials"));
				    			
				    			suppPaxTypeFaresJsonArr.put(pricingInfoJson);
				    			JSONObject totalFareJson = new JSONObject();
				    			totalFareJson.put(JSON_PROP_AMOUNT, paxTypeTotalFare);
				    			totalFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);
				    			commPriceJson.put(JSON_PROP_TOTALFARE, totalFareJson);	
				    						    			
				    			paxTypeFaresJsonArr.put(m, commPriceJson);
				    						    		
							}
							count++;						
						}			
						resPackageDetailsInfoJson.put("totalFare", totalFareCompsGroup.toJSON());
		        		if ( clientType == ClientType.B2B) {
		        			resPackageDetailsInfoJson.put(JSON_PROP_INCENTIVES, totalIncentivesGroup.toJSON());
		        		}

		        		//resPackageDetailsInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercials);
		        		resPackageDetailsInfoJson.put(JSON_PROP_PAXTYPEFARES, paxTypeFaresJsonArr);	
		        		
		        		if (retainSuppFares) {
		    	    		JSONObject suppPricingInfoJson  = new JSONObject();
		    	    		JSONObject suppTotalFareJson = new JSONObject();
		    	    		suppPricingInfoJson.put("totalFare", suppTotalFareJson);
		    	    		suppPricingInfoJson.put(JSON_PROP_PAXTYPEFARES, suppPaxTypeFaresJsonArr);
		    	    		resPackageDetailsJson.put(JSON_PROP_CLIENTCOMMINFO, clientCommercialInfoArr);
		    	    		resPackageDetailsJson.put(JSON_PROP_SUPPPRICEINFO, suppPricingInfoJson);
		    	    		addSupplierTotalFare(resPackageDetailsJson, paxCountsMap);
		        		} 
					}
				}
				//System.out.println(count);
			}
		}
	}    		 

	private static void appendSupplierCommercialsForRail(JSONObject pricingInfoJson, JSONObject ccommTrainPsgrDtlJson, String suppID, String suppCcyCode, Map<String, String> suppCommToTypeMap) {

		JSONArray suppCommJsonArr = new JSONArray();
		JSONArray ccommSuppCommJsonArr = ccommTrainPsgrDtlJson.optJSONArray(JSON_PROP_COMMDETAILS);
		// If no supplier commercials have been defined in BRMS, the JSONArray for ccommSuppCommJsonArr will be null.
		// In this case, log a message and proceed with other calculations.
		if (ccommSuppCommJsonArr == null) {
			logger.warn(String.format("No supplier commercials found for supplier %s", suppID));
			return;
		} 

		for (int x=0; x < ccommSuppCommJsonArr.length(); x++) {
			JSONObject ccommSuppCommJson = ccommSuppCommJsonArr.getJSONObject(x);
			JSONObject suppCommJson = new JSONObject();
			suppCommJson.put(JSON_PROP_COMMNAME, ccommSuppCommJson.getString(JSON_PROP_COMMNAME));
			suppCommJson.put(JSON_PROP_COMMTYPE, suppCommToTypeMap.get(ccommSuppCommJson.getString(JSON_PROP_COMMNAME)));
			suppCommJson.put(JSON_PROP_COMMAMOUNT, ccommSuppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
			suppCommJson.put(JSON_PROP_COMMCCY, ccommSuppCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
//			suppCommJson.put(JSON_PROP_MDMRULEID,ccommSuppCommJson.optString("mdmruleId", ""));
			suppCommJsonArr.put(suppCommJson);
		}
		pricingInfoJson.put(JSON_PROP_SUPPCOMM, suppCommJsonArr);
	}

	private static Map<String, String> getSupplierCommercialsAndTheirType(JSONObject suppCommResJson) {
		JSONArray commHeadJsonArr = null;
		JSONObject commHeadJson = null;
		Map<String, String> commToTypeMap = new HashMap<String, String>();
		JSONArray scommBRIJsonArr = getSupplierCommercialsBusinessRuleIntakeJSONArray(suppCommResJson);
		for (int i = 0; i < scommBRIJsonArr.length(); i++) {
			if ((commHeadJsonArr = scommBRIJsonArr.getJSONObject(i).optJSONArray(JSON_PROP_COMMHEAD)) == null) {
				logger.warn("No commercial heads found in supplier commercials");
				continue;
			}

			for (int j = 0; j < commHeadJsonArr.length(); j++) {
				commHeadJson = commHeadJsonArr.getJSONObject(j);
				commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME),
						commHeadJson.getString(JSON_PROP_COMMTYPE));
			}
		}

		return commToTypeMap;
	}

	private static Map<String, String> getClientCommercialsAndTheirType(JSONObject clientCommResJson) {
		JSONArray commHeadJsonArr = null;
		JSONObject commHeadJson = null;
		JSONArray entityDtlsJsonArr = null;
		Map<String, String> commToTypeMap = new HashMap<String, String>();
		JSONArray ccommBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
		for (int i = 0; i < ccommBRIJsonArr.length(); i++) {
			if ((entityDtlsJsonArr = ccommBRIJsonArr.getJSONObject(i).optJSONArray(JSON_PROP_ENTITYDETAILS)) == null) {
				continue;
			}
			for (int j = 0; j < entityDtlsJsonArr.length(); j++) {
				if ((commHeadJsonArr = entityDtlsJsonArr.getJSONObject(j).optJSONArray(JSON_PROP_COMMHEAD)) == null) {
					logger.warn("No commercial heads found in client commercials");
					continue;
				}
				for (int k = 0; k < commHeadJsonArr.length(); k++) {
					commHeadJson = commHeadJsonArr.getJSONObject(k);
					commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME),commHeadJson.getString(JSON_PROP_COMMTYPE));
				}
			}
		}
		return commToTypeMap;
	}

	static JSONArray getSupplierCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
		return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results")
				.getJSONObject(0).getJSONObject("value")
				.getJSONObject("cnk.rail_commercialscalculationengine.suppliertransactionalrules.Root")
				.getJSONArray("businessRuleIntake");
	}

	static JSONArray getClientCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
		return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results")
				.getJSONObject(0).getJSONObject("value")
				.getJSONObject("cnk.rail_commercialscalculationengine.clienttransactionalrules.Root")
				.getJSONArray("businessRuleIntake");
	}

	private static Map<Integer, JSONArray> getBRIWiseTrainDetailsFromClientCommercials(JSONObject clientCommResJson) {

		JSONArray ccommSuppBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
		Map<Integer, JSONArray> ccommSuppBRIJsonMap = new HashMap<Integer, JSONArray>();
		Integer briNo = 0;
		for (int i = 0; i < ccommSuppBRIJsonArr.length(); i++) {
			JSONObject ccommSuppBRIJson = ccommSuppBRIJsonArr.getJSONObject(i);
			// Getting SupplierCommericals BRI Since order is preserved
			JSONArray ccommTrainDtlsJsonArr = ccommSuppBRIJson.getJSONArray(JSON_PROP_TRAINDETAILS);
			ccommSuppBRIJsonMap.put(briNo, ccommTrainDtlsJsonArr);
			briNo++;
		}
		return ccommSuppBRIJsonMap;
	}

	private static JSONObject convertToClientEntityCommercialJson(JSONObject clientCommJson, Map<String,String> clntCommToTypeMap, String suppCcyCode) {
		JSONObject clientCommercial= new JSONObject();
		String commercialName = clientCommJson.getString(JSON_PROP_COMMNAME);
		clientCommercial.put(JSON_PROP_COMMTYPE, clntCommToTypeMap.getOrDefault(commercialName, "?"));
		clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
		clientCommercial.put(JSON_PROP_COMMNAME, clientCommJson.getString(JSON_PROP_COMMNAME));
		clientCommercial.put(JSON_PROP_COMMCCY, clientCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
		return clientCommercial;
	}

    private static void addSupplierTotalFare(JSONObject resPackageDetailsJson, Map<String, BigDecimal> paxInfoMap) {
		JSONObject suppPricingInfoJson = resPackageDetailsJson.getJSONObject(JSON_PROP_SUPPPRICEINFO);
		JSONArray paxTypeFaresArr = suppPricingInfoJson.getJSONArray(JSON_PROP_PAXTYPEFARES);

		Map<String, JSONObject> suppCommTotalsMap = new HashMap<String, JSONObject>();
		Map<String, JSONObject> clientCommTotalsMap = new HashMap<String, JSONObject>();
		BigDecimal totalFareAmt = new BigDecimal(0);
	
		String ccyCode = null;
		JSONObject clientEntityTotalCommercials=null;
		JSONArray totalClientArr= new JSONArray();
		for (int i = 0; i < paxTypeFaresArr.length(); i++) {
			JSONObject paxTypeFare = paxTypeFaresArr.getJSONObject(i);
			JSONObject paxTypeTotalFareJson = paxTypeFare.getJSONObject(JSON_PROP_TOTALFARE);
			totalFareAmt = totalFareAmt.add(paxTypeTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE))));
			ccyCode = (ccyCode == null) ? paxTypeTotalFareJson.getString(JSON_PROP_CCYCODE) : ccyCode;
			
			JSONArray suppCommJsonArr = paxTypeFare.optJSONArray(JSON_PROP_SUPPCOMM);
			//the order of clientCommercialItinInfo will same as that of normal paxTypeFares
			JSONObject clientInfoJson=resPackageDetailsJson.getJSONArray(JSON_PROP_CLIENTCOMMINFO).getJSONObject(i);
			JSONArray clientCommJsonArr=clientInfoJson.optJSONArray(JSON_PROP_CLIENTENTITYCOMMS);
			
			// If no supplier commercials have been defined in BRMS, the JSONArray for suppCommJsonArr will be null.
			// In this case, log a message and proceed with other calculations.
			if (suppCommJsonArr == null) {
				logger.warn("No supplier commercials found");
			}
			else {
				for (int j=0; j < suppCommJsonArr.length(); j++) {
					JSONObject suppCommJson = suppCommJsonArr.getJSONObject(j);
					String suppCommName = suppCommJson.getString(JSON_PROP_COMMNAME);
					JSONObject suppCommTotalsJson = null;
					if (suppCommTotalsMap.containsKey(suppCommName)) {
						suppCommTotalsJson = suppCommTotalsMap.get(suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE)))));
					}
					else {
						suppCommTotalsJson = new JSONObject();
						suppCommTotalsJson.put(JSON_PROP_COMMNAME, suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMTYPE, suppCommJson.getString(JSON_PROP_COMMTYPE));
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE))));
						suppCommTotalsJson.put(JSON_PROP_COMMCCY,suppCommJson.get(JSON_PROP_COMMCCY).toString());
						suppCommTotalsMap.put(suppCommName, suppCommTotalsJson);
					}
				}
			}
			
			if (clientCommJsonArr == null) {
				logger.warn("No client commercials found");
			}
			else {
				for (int l=0; l < clientCommJsonArr.length(); l++) {
					//TODO:Add jsonElements from clientEntity once they can be fetched.
					JSONObject clientCommJson = clientCommJsonArr.getJSONObject(l);
					JSONObject clientCommEntJson= new JSONObject();
					
					//JSONObject clientCommJson =clientEntityCommJson.getJSONObject("clientCommercial");
					//clientCommEntJson
					JSONArray clientEntityCommJsonArr=clientCommJson.getJSONArray(JSON_PROP_CLIENTCOMM);				
					
					JSONObject clientCommTotalsJson = null;
					JSONArray clientTotalEntityArray=new JSONArray();
					
					for(int m=0;m<clientEntityCommJsonArr.length();m++) {
						
						JSONObject clientCommEntityJson=clientEntityCommJsonArr.getJSONObject(m);
						String clientCommName = clientCommEntityJson.getString(JSON_PROP_COMMNAME);						
						
						if (clientCommTotalsMap.containsKey(clientCommName)) {
							clientEntityTotalCommercials= clientCommTotalsMap.get(clientCommName);
							//clientCommTotalsJson=clientEntityTotalCommercials.getJSONObject("clientCommercialsTotal");
							//clientCommTotalsJson = clientCommTotalsMap.get(clientCommName);
							clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT).add(clientCommEntityJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE)))));
						}
						else {
							clientEntityTotalCommercials= new JSONObject();
							clientCommTotalsJson = new JSONObject();
							clientCommTotalsJson.put(JSON_PROP_COMMNAME, clientCommName);
							clientCommTotalsJson.put(JSON_PROP_COMMTYPE, clientCommEntityJson.getString(JSON_PROP_COMMTYPE));
							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, clientCommEntityJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE))));
							clientCommTotalsJson.put(JSON_PROP_COMMCCY,clientCommEntityJson.get(JSON_PROP_COMMCCY).toString());
							clientTotalEntityArray.put(clientCommTotalsJson);						
						
							clientCommTotalsMap.put(clientCommName, clientCommTotalsJson);
						}
					}
					
					if((clientTotalEntityArray!=null) && (clientTotalEntityArray.length()>0) ) {
						clientCommEntJson.put("clientCommercialsTotal", clientTotalEntityArray);
						clientCommEntJson.put(JSON_PROP_CLIENTID, clientCommJson.optString(JSON_PROP_CLIENTID,""));
						clientCommEntJson.put(JSON_PROP_PARENTCLIENTID, clientCommJson.optString(JSON_PROP_PARENTCLIENTID,""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYTYPE, clientCommJson.optString(JSON_PROP_COMMENTITYTYPE,""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYID, clientCommJson.optString(JSON_PROP_COMMENTITYID,""));
						
						totalClientArr.put(clientCommEntJson);
					}
				}
			}
		}

		// Convert map of Commercial Head to Commercial Amount to JSONArray and append in suppItinPricingInfoJson
		JSONArray suppCommTotalsJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet().iterator();
		while (suppCommTotalsIter.hasNext()) {
			suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
		}
		suppPricingInfoJson.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr);
		
	/*	JSONArray clientCommTotalsJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> clientCommTotalsIter = clientCommTotalsMap.entrySet().iterator();
		while (clientCommTotalsIter.hasNext()) {
			clientCommTotalsJsonArr.put(clientCommTotalsIter.next().getValue());
		}*/
		resPackageDetailsJson.getJSONObject(JSON_PROP_TOTALPRICEINFO).put(JSON_PROP_CLIENTENTITYTOTALCOMMS, totalClientArr);
		
		JSONObject totalFare = suppPricingInfoJson.getJSONObject(JSON_PROP_TOTALFARE);
		totalFare.put(JSON_PROP_CCYCODE, ccyCode);
		totalFare.put(JSON_PROP_AMOUNT, totalFareAmt);
		
		resPackageDetailsJson.remove(JSON_PROP_CLIENTCOMMINFO);
	}

}
