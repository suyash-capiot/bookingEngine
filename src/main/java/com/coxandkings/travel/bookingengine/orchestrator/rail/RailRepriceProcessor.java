package com.coxandkings.travel.bookingengine.orchestrator.rail;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.rail.RailConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class RailRepriceProcessor implements RailConstants {
	private static final Logger logger = LogManager.getLogger(RailRepriceProcessor.class);
	static final String OPERATION_NAME = "reprice";

	public static String process(JSONObject reqJson)
			throws ValidationException, RequestProcessingException, InternalProcessingException {
		Element reqElem = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		String refId;
		try {
			opConfig = RailConfig.getOperationConfig(OPERATION_NAME);
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			TrackingContext.setTrackingContext(reqJson);
			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			String sessionId = reqHdrJson.getString(JSON_PROP_SESSIONID);
			String transactionId = reqHdrJson.getString(JSON_PROP_TRANSACTID);
			String userId = reqHdrJson.getString(JSON_PROP_USERID);

			UserContext usrContxt = UserContext.getUserContextForSession(reqHdrJson);
			List<ProductSupplier> productSuppliers = usrContxt.getSuppliersForProduct(PROD_CATEG_TRANSPORT,
					PROD_CAT_SUBTYPE);

			validateRequestParameters(reqHdrJson, reqBodyJson);
			Element suppCredsList = XMLUtils.getFirstElementAtXPath(reqElem,
					"./rail:RequestHeader/com:SupplierCredentialsList");
			if (productSuppliers == null) {
				throw new Exception("Product supplier not found for user/client");
			}

			for (ProductSupplier prodSupplier : productSuppliers) {
				suppCredsList.appendChild(prodSupplier.toElement(ownerDoc));
			}
			// *********WEM JSON TO SI XML FOR REQUEST HEADER STARTS HERE***********//

			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:SessionID", sessionId);
			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:TransactionID", transactionId);
			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:UserID", userId);

			// *******WEM JSON TO SI XML FOR REQUEST HEADER ENDS HERE**********//

			// *******WEM JSON TO SI XML FOR REQUEST BODY STARTS HERE******//

			// String railShopElemXPath =
			// "./raili:RequestBody/rail:OTA_RailShopRQWrapper/ota:OTA_RailShopRQ";
			Element railShopElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_RailShopRQWrapper/ota:OTA_RailShopRQ");
			Element preference = XMLUtils.getFirstElementAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_RailShopRQWrapper/ota:OTA_RailShopRQ/ota:Preferences");
			Element passengerType = XMLUtils.getFirstElementAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_RailShopRQWrapper/ota:OTA_RailShopRQ/ota:PassengerType");
			XMLUtils.removeNode(passengerType);

			JSONArray paxArr = reqBodyJson.getJSONArray("passengerDetails");
			int numOfPax = paxArr.length();

			Element passenger;
			for (int i = 0; i < numOfPax; i++) {
				JSONObject paxJson = paxArr.getJSONObject(i);
				passenger = (Element) passengerType.cloneNode(true);
				setPassengerType(ownerDoc, passenger, paxJson, reqBodyJson, i + 1);
				railShopElem.insertBefore(passenger, preference);

			}

			XMLUtils.setValueAtXPath(railShopElem, "./ota:OriginDestination/ota:DepartureDateTime",
					reqBodyJson.getString(JSON_PROP_TRAVELDATE));
			XMLUtils.setValueAtXPath(railShopElem, "./ota:OriginDestination/ota:OriginLocation",
					reqBodyJson.getString(JSON_PROP_ORIGINLOC));
			XMLUtils.setValueAtXPath(railShopElem, "./ota:OriginDestination/ota:DestinationLocation",
					reqBodyJson.getString(JSON_PROP_DESTLOC));
			XMLUtils.setValueAtXPath(reqElem, "./raili:RequestBody/rail:OTA_RailShopRQWrapper/rail:SupplierID",
					reqBodyJson.getString(JSON_PROP_SUPPREF));
			// TODO hard coded sequence value. ask if wrapper can repeat in case of IRCTC
			XMLUtils.setValueAtXPath(reqElem, "./raili:RequestBody/rail:OTA_RailShopRQWrapper/rail:Sequence", "1");

			Element amenities, railAmenity, accoCategory, accommodation, compartment, journeyDet, boardingStn, railTpa;
			amenities = XMLUtils.getFirstElementAtXPath(railShopElem, "./ota:Preferences/ota:RailAmenities");
			JSONArray amenitiesJson = reqBodyJson.getJSONArray("railAmenities");
			int amenitiesLen = amenitiesJson.length();
			JSONObject pref;
			railTpa = XMLUtils.getFirstElementAtXPath(railShopElem, "./ota:TPA_Extensions/rail:Rail_TPA");
			for (int i = 0; i < amenitiesLen; i++) {
				pref = amenitiesJson.getJSONObject(i);
				railAmenity = setRailAmenity(ownerDoc, pref);
				amenities.appendChild(railAmenity);
			}

			// preferred coach is optional.
			if (reqBodyJson.has("preferredCoach") && reqBodyJson.getString("preferredCoach") != null) {
				accoCategory = ownerDoc.createElementNS(NS_OTA, "ota:AccommodationCategory");
				accommodation = ownerDoc.createElementNS(NS_OTA, "ota:Accommodation");
				compartment = ownerDoc.createElementNS(NS_OTA, "ota:Compartment");
				compartment.setTextContent(reqBodyJson.getString("preferredCoach"));
				preference.appendChild(accoCategory);
				accoCategory.appendChild(accommodation);
				accommodation.appendChild(compartment);
			}

			// boarding station is optional.
			if (reqBodyJson.has("boardingStation") && reqBodyJson.getString("boardingStation") != null) {
				journeyDet = ownerDoc.createElementNS(NS_RAIL, "ota:JourneyDetails");
				boardingStn = ownerDoc.createElementNS(NS_RAIL, "ota:BoardingStation");
				journeyDet.appendChild(boardingStn);
				railTpa.appendChild(journeyDet);
			}

			XMLUtils.setValueAtXPath(railShopElem,
					"./ota:TPA_Extensions/rail:Rail_TPA/rail:ReservationDetails/rail:ReservationClass",
					reqBodyJson.getString(JSON_PROP_RESERVATIONCLASS));
			XMLUtils.setValueAtXPath(railShopElem,
					"./ota:TPA_Extensions/rail:Rail_TPA/rail:ReservationDetails/rail:ReservationType",
					reqBodyJson.getString(JSON_PROP_RESERVATIONTYPE));

			// Reference ID won't be provided by wem. Hence, a random ID is generated and
			// sent in response
			// String refId=UUID.randomUUID().toString();
			refId = createRandomString(8);
			// System.out.println("generated uuid: "+ refId);
			XMLUtils.setValueAtXPath(railShopElem, "./ota:TPA_Extensions/rail:Rail_TPA/rail:ReferenceID", refId);
			XMLUtils.setValueAtXPath(railShopElem, "./ota:RailSearchCriteria/ota:Train/ota:TrainNumber",
					reqBodyJson.getString(JSON_PROP_TRAINNUM));

			logger.info("Before opening HttpURLConnection to SI");
			System.out.println("SI Request Element: " + XMLTransformer.toString(reqElem));
		} catch (ValidationException validationException) {
			validationException.printStackTrace();
			logger.info("Validation Exception in Rail Reprice Process: " + validationException);
			throw validationException;
		} catch (Exception x) {
			x.printStackTrace();
			logger.info("Exception in Rail Reprice Process: " + x);
			throw new RequestProcessingException(x);
		}
		try {
			Element resElem = null;
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(),
					opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			logger.info("HttpURLConnection to SI closed");
			System.out.println("SI Response: " + XMLTransformer.toString(resElem));
			logger.info("Rail reprice SI response XML: " + XMLTransformer.toString(resElem));
			JSONObject resJson = RailSearchProcessor.getSupplierResponseJSON(reqJson, resElem);
			logger.info("Rail reprice SI response json: " + resJson.toString());
			resJson.getJSONObject(JSON_PROP_RESBODY).put("referenceId", refId);
			// remove the extra fields not returned in reprice response of SI
			for (Object orgDestOpt : resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_ORIGINDESTOPTS)) {
				JSONObject orgDestOptJson = (JSONObject) orgDestOpt;
				JSONObject train = orgDestOptJson.getJSONObject(JSON_PROP_TRAINDETAILS);
				train.remove(JSON_PROP_OPERATIONSCHEDULE);
				train.remove(JSON_PROP_DEPARTTIME);
				train.remove(JSON_PROP_ARRIVALTIME);
				train.remove(JSON_PROP_JOURNEYDURATION);
				train.remove(JSON_PROP_TRAINTYPE);
			}

			// Create kafka message and push to redis
			// when we receive pnr from WEM we will get kafka msg from redis and will insert
			// in db with pnr number.
			createKafkaMessageForRedis(reqJson, resJson);
			// Map<String, JSONObject> brmsToSIClassMap = new LinkedHashMap<String,
			// JSONObject>();
			// JSONObject resSupplierComJson =
			// SupplierCommercials.getSupplierCommercials(reqJson, resJson,
			// brmsToSIClassMap);

			// JSONObject resClientComJson =
			// ClientCommercials.getClientCommercials(resSupplierComJson);
			// Call to Redis Cache
			// pushRepriceDataToRedis(resJson, reqBodyJson);

			/*
			 * JSONObject jsonForClientCommercialRequest=ClientCommercialStructure.
			 * getClientCommercialsStructure(reqJson, resJson,OPERATION_NAME,resElem);
			 * System.out.println("structure for client commercial: "
			 * +jsonForClientCommercialRequest); JSONObject resClientComJson =
			 * ClientCommercials.getClientCommercials(jsonForClientCommercialRequest);
			 * System.out.println("Client commercial resp: "+resClientComJson);
			 */
			// Offers.getOffers(reqJson, resJson, OffersConfig.Type.COMPANY_SEARCH_TIME,
			// OPERATION_NAME);
			// TODO:Check whether SI and client commercial fair structure is stored in redis
			// pushRepriceDataToRedis(resJson, reqBodyJson);
			// TODO:Create structure for kafca request
			// JSONObject kafcaRequestJson=getKafcaRequestStructure(reqJson,resJson);
			System.out.println("FInal Json Response: " + resJson);
			return resJson.toString();

		} catch (ValidationException vlex) {
			vlex.printStackTrace();
			logger.info("Validation Exception in Rail Reprice Process: " + vlex);
			throw vlex;
		} catch (Exception x) {
			logger.error("Exception in Rail Reprice Process: ", x);
			throw new InternalProcessingException(x);
		}

	}

	private static void validateRequestParameters(JSONObject reqHdrJson, JSONObject reqBodyJson)
			throws ValidationException {
		RailRequestValidator.validateTravelDate(reqBodyJson, reqHdrJson);
		RailRequestValidator.validateOriginalLocation(reqBodyJson, reqHdrJson);
		RailRequestValidator.validateDestinationLocation(reqBodyJson, reqHdrJson);
		RailRequestValidator.validateSupplierRef(reqBodyJson, reqHdrJson);
		RailRequestValidator.validateTrainNumber(reqBodyJson, reqHdrJson);
		RailRequestValidator.validateReservationClass(reqBodyJson, reqHdrJson);
		RailRequestValidator.validateReservationType(reqBodyJson, reqHdrJson);

	}

	// PNR number and insurance is applicable or not will be received from WEM
	// response after booking
	private static void createKafkaMessageForRedis(JSONObject reqJson, JSONObject resJson) {
		// TODO:Expecting bookid from WEM
		JSONObject railJson = new JSONObject();
		JSONObject originAndDestinationDetailsInnerStructureJson = new JSONObject();
		railJson.put(JSON_PROP_PROD, JSON_PROP_DB_PRODUCT);
		railJson.put(JSON_PROP_TYPE, JSON_PROP_REQUEST);
		railJson.put(JSON_PROP_ROE, 1);
		JSONArray railJsonArrayInnerStructureJsonArray = new JSONArray();

		JSONArray originDestinationOptionsJsonArray = resJson.getJSONObject(JSON_PROP_RESBODY)
				.getJSONArray(JSON_PROP_ORIGINDESTOPTS);
		for (int i = 0; i < originDestinationOptionsJsonArray.length(); i++) {

			JSONObject obj = originDestinationOptionsJsonArray.getJSONObject(i);
			originAndDestinationDetailsInnerStructureJson.put(JSON_PROP_SUPPLIER_REF,
					obj.getString(JSON_PROP_SUPPLIER_REF));

			JSONObject originAndDestinationDetails = new JSONObject();

			originAndDestinationDetailsInnerStructureJson.put(JSON_PROP_ORIGINLOC, obj.getString(JSON_PROP_ORIGINLOC));
			originAndDestinationDetailsInnerStructureJson.put(JSON_PROP_DESTLOC, obj.getString(JSON_PROP_DESTLOC));

			JSONObject trainDetailsInnerStructureJson = new JSONObject();
			trainDetailsInnerStructureJson.put(JSON_PROP_TRAINNUM,
					obj.getJSONObject(JSON_PROP_TRAINDETAILS).getString(JSON_PROP_TRAINNUM));
			trainDetailsInnerStructureJson.put(JSON_PROP_ARRIVALSTATION_CODE, obj.getString(JSON_PROP_DESTLOC));
			trainDetailsInnerStructureJson.put(JSON_PROP_DEPARTURESTATION_CODE, obj.getString(JSON_PROP_ORIGINLOC));
			// TODO:From where departure and arrival date time we can get
			trainDetailsInnerStructureJson.put(JSON_PROP_DEPARTUREDATETIME, "");
			trainDetailsInnerStructureJson.put(JSON_PROP_ARRIVALDATETIME, "");
			originAndDestinationDetailsInnerStructureJson.put(JSON_PROP_TRAINDETAILS, trainDetailsInnerStructureJson);

			JSONObject reservationDetailsJsonObj = new JSONObject();
			JSONObject reservationDetailsInnerStructureJsonObj = new JSONObject();
			JSONObject supTotalPriceInfo = new JSONObject();
			double totalAmount = 0;
			JSONArray classAvailInfoJsonArray = obj.getJSONArray(JSON_PROP_CLASSAVAIL);
			for (int j = 0; j < classAvailInfoJsonArray.length(); j++) {
				JSONObject obj1 = classAvailInfoJsonArray.getJSONObject(j);
				reservationDetailsInnerStructureJsonObj.put(JSON_PROP_RESERVATIONCLASS,
						obj1.getString(JSON_PROP_RESERVATIONCLASS));
				reservationDetailsInnerStructureJsonObj.put(JSON_PROP_RESERVATIONTYPE,
						obj1.getString(JSON_PROP_RESERVATIONTYPE));
				supTotalPriceInfo = obj1.getJSONObject(JSON_PROP_PRICING);
				totalAmount = obj1.getJSONObject(JSON_PROP_PRICING).getJSONObject(JSON_PROP_TOTALFARE)
						.getDouble(JSON_PROP_AMOUNT);
			}
			JSONObject copyOfReqJson = reqJson;
			JSONArray passJsonArray = copyOfReqJson.getJSONObject(JSON_PROP_REQBODY)
					.getJSONArray(JSON_PROP_PASSENGER_DETAILS);
			double perPassAmount = 0;
			if (totalAmount != 0) {
				perPassAmount = totalAmount / passJsonArray.length();
			}

			for (int j = 0; j < passJsonArray.length(); j++) {
				JSONObject obj2 = passJsonArray.getJSONObject(j);
				obj2.put(JSON_PROP_ADDRESS_DETAILS, obj2.getJSONObject(JSON_PROP_ADDRESS));
				obj2.remove(JSON_PROP_ADDRESS);

				obj2.put(JSON_PROP_CONTACTDTLS, obj2.getJSONObject(JSON_PROP_TELEPHONE));
				obj2.remove(JSON_PROP_TELEPHONE);

				// TODO:expecting from WEM response
				obj2.put(JSON_PROP_BOOKINGBERTHDETAILS, "");
				obj2.put(JSON_PROP_CURRENTBERTHDETAILS, "");
				JSONObject passengerFareInnerStructureJsonObj = new JSONObject();
				passengerFareInnerStructureJsonObj.put(JSON_PROP_KFKA_AMOUNT, perPassAmount);
				passengerFareInnerStructureJsonObj.put(JSON_PROP_CURRENCY,
						copyOfReqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT)
								.getString(JSON_PROP_CLIENTCCY));
				obj2.put(JSON_PROP_PASSENGER_FARE, passengerFareInnerStructureJsonObj);
			}
			reservationDetailsJsonObj.put(JSON_PROP_RESERVATION_DETAILS, reservationDetailsInnerStructureJsonObj);
			reservationDetailsJsonObj.put(JSON_PROP_SUPPLIERTOTALPRICEINFO, supTotalPriceInfo);
			reservationDetailsJsonObj.put(JSON_PROP_PASSENGER_INFO, passJsonArray);
			reservationDetailsJsonObj.put(JSON_PROP_ORIGIN_DESTINATION_DETAILS,
					originAndDestinationDetailsInnerStructureJson);
			// TODO:Expecting from WEM response
			reservationDetailsJsonObj.put(JSON_PROP_INSURANCE_DETAILS, "");
			railJsonArrayInnerStructureJsonArray.put(reservationDetailsJsonObj);
		}
		JSONObject responseHeaderJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		railJson.put(JSON_PROP_RAIL_ARRAY, railJsonArrayInnerStructureJsonArray);
		JSONObject resBody = new JSONObject();
		resBody.put(JSON_PROP_RESBODY, railJson);
		resBody.put(JSON_PROP_REQHEADER, responseHeaderJson);
		// railJson.put(JSON_PROP_RAIL_ARRAY, railJsonArrayInnerStructureJsonArray);
		String redisKey = responseHeaderJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT).concat("|")
				.concat(OPERATION_NAME);
		HashMap<String, String> railPriceMap = new HashMap<String, String>();
		railPriceMap.put(JSON_PROP_RAIL_ARRAY, resBody.toString());
		try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool()){
			redisConn.hmset(redisKey, railPriceMap);
			// redisConn.pexpire(redisKey, (long) (RailConfig.getRedisTTLMinutes() * 60 *
			// 1000));
		}

	}

	// For random number
	public static String createRandomString(int length) {
		Random random = new Random();
		StringBuilder sb = new StringBuilder();
		while (sb.length() < length) {
			sb.append(Integer.toHexString(random.nextInt()));
		}
		return sb.toString();
	}

	private static JSONObject getKafcaRequestStructure(JSONObject reqJson, JSONObject resJson) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * private static void pushRepriceDataToRedis(JSONObject resJson, JSONObject
	 * reqBodyJson) { System.out.println("Response Json: "+resJson);
	 * System.out.println("Request Json: "+reqBodyJson); JSONObject
	 * responseHeaderJson = resJson.getJSONObject(JSON_PROP_RESHEADER); JSONArray
	 * originDestinationOptionsJsonArray=resJson.getJSONObject(JSON_PROP_RESBODY).
	 * getJSONArray(JSON_PROP_ORIGINDESTOPTS); JSONArray
	 * originDestinationOptionsJsonArrayClone= originDestinationOptionsJsonArray;
	 * HashMap<String, String> railPriceMap=new HashMap<String,String>(); //create
	 * redis structure for (int h = 0; h
	 * <originDestinationOptionsJsonArrayClone.length(); h++) { //remove
	 * trainDetails,destinationLocation,originLocation //TODO:Use constants instead
	 * of string
	 * originDestinationOptionsJsonArrayClone.getJSONObject(h).remove("trainDetails"
	 * ); originDestinationOptionsJsonArrayClone.getJSONObject(h).remove(
	 * "destinationLocation");
	 * originDestinationOptionsJsonArrayClone.getJSONObject(h).remove(
	 * "originLocation");
	 * 
	 * JSONArray
	 * classAvailInfoJsonArray=originDestinationOptionsJsonArrayClone.getJSONObject(
	 * h).getJSONArray("classAvailInfo");
	 * 
	 * for (int i = 0; i < classAvailInfoJsonArray.length(); i++) { //remove
	 * availabilityDetail array
	 * classAvailInfoJsonArray.getJSONObject(i).remove("availabilityDetail");
	 * 
	 * //TODO:create constant double
	 * totalFareAmount=classAvailInfoJsonArray.getJSONObject(i).getJSONObject(
	 * "pricing").getJSONObject("totalFare").getInt("amount"); String
	 * totalFareCurrencyCode=classAvailInfoJsonArray.getJSONObject(i).getJSONObject(
	 * "pricing").getJSONObject("totalFare").getString("currencyCode");
	 * classAvailInfoJsonArray.getJSONObject(i).getJSONObject("pricing").remove(
	 * "totalFare");
	 * classAvailInfoJsonArray.getJSONObject(i).getJSONObject("pricing").put(
	 * "totalFare", totalFareAmount+"");
	 * classAvailInfoJsonArray.getJSONObject(i).getJSONObject("pricing").put(
	 * "currencyCode", totalFareCurrencyCode);
	 * 
	 * //Total fees calculation int feesAmount=0; JSONArray
	 * feesJsonArray=classAvailInfoJsonArray.getJSONObject(i).getJSONObject(
	 * "pricing").getJSONObject("fareBreakup").getJSONArray("fees"); JSONArray
	 * feesJsonArrayClone=feesJsonArray; JSONObject totalFeesAmountJsonObj=new
	 * JSONObject(); for (int j = 0; j < feesJsonArrayClone.length(); j++) {
	 * feesAmount=feesAmount+feesJsonArrayClone.getJSONObject(j).getInt("amount"); }
	 * totalFeesAmountJsonObj.put("totalFees", feesAmount);
	 * feesJsonArrayClone.put(totalFeesAmountJsonObj);
	 * 
	 * //For total ancillary charges int totalAncillaryCharges=0; JSONArray
	 * ancillaryChargesJsonArray=classAvailInfoJsonArray.getJSONObject(i).
	 * getJSONObject("pricing").getJSONObject("fareBreakup").getJSONArray(
	 * "ancillaryCharges"); JSONArray
	 * ancillaryChargesJsonArrayClone=ancillaryChargesJsonArray; for (int k = 0; k <
	 * ancillaryChargesJsonArrayClone.length(); k++) {
	 * totalAncillaryCharges=totalAncillaryCharges+ancillaryChargesJsonArrayClone.
	 * getJSONObject(k).getInt("amount"); } JSONObject
	 * totalAncillaryChargesAmountJsonObj=new JSONObject();
	 * totalAncillaryChargesAmountJsonObj.put("totalPriceAncillary",
	 * totalAncillaryCharges);
	 * ancillaryChargesJsonArrayClone.put(totalAncillaryChargesAmountJsonObj);
	 * 
	 * //For total tax charges int totalTaxCharges=0; JSONArray
	 * taxChargesJsonArray=classAvailInfoJsonArray.getJSONObject(i).getJSONObject(
	 * "pricing").getJSONObject("fareBreakup").getJSONArray("taxes"); JSONArray
	 * taxChargesJsonArrayClone=taxChargesJsonArray; for (int l = 0; l <
	 * taxChargesJsonArrayClone.length(); l++) {
	 * totalTaxCharges=totalTaxCharges+taxChargesJsonArrayClone.getJSONObject(l).
	 * getInt("amount"); } JSONObject totalTaxChargesAmountJsonObj=new JSONObject();
	 * totalTaxChargesAmountJsonObj.put("totalPriceTax", totalTaxCharges);
	 * taxChargesJsonArrayClone.put(totalTaxChargesAmountJsonObj);
	 * 
	 * //remove
	 * passengerPreferences,applicableBerth,reservationClass,reservationType from
	 * clone
	 * classAvailInfoJsonArray.getJSONObject(i).remove("passengerPreferences");
	 * classAvailInfoJsonArray.getJSONObject(i).remove("applicableBerth");
	 * classAvailInfoJsonArray.getJSONObject(i).remove("reservationClass");
	 * classAvailInfoJsonArray.getJSONObject(i).remove("reservationType"); }
	 * railPriceMap.put("classAvailInfo", classAvailInfoJsonArray.toString()); }
	 * 
	 * 
	 * String redisKey =
	 * responseHeaderJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT)
	 * .concat("|") .concat(OPERATION_NAME);
	 * 
	 * Jedis redisConn = RedisConfig.getRedisConnectionFromPool();
	 * redisConn.hmset(redisKey, railPriceMap); // redisConn.pexpire(redisKey,
	 * (long) (RailConfig.getRedisTTLMinutes() * 60 * 1000));
	 * RedisConfig.releaseRedisConnectionToPool(redisConn);
	 * 
	 * }
	 */

	public static Element setRailAmenity(Document ownerDoc, JSONObject prefJson) {
		Element railAmenity = ownerDoc.createElementNS(NS_OTA, "ota:RailAmenity");
		railAmenity.setAttribute("CodeContext", prefJson.getString("codeContext"));
		if (prefJson.has("code")) {
			railAmenity.setAttribute("Code", prefJson.getString("code"));
		}
		return railAmenity;
	}

	public static void setPassengerType(Document ownerDoc, Element passengerElem, JSONObject passengerJson,
			JSONObject reqBody, int rph) {

		Element paxTypeElem, passengerDetail, identification, givenName, surname, tpaExt, tpa, paxDetails, foodChoice,
				profileRef, uniqueId, address, country, phone;

		passengerElem.setAttribute("Gender", passengerJson.getString("gender"));
		passengerElem.setAttribute("RPH", Integer.toString(rph));

		paxTypeElem = ownerDoc.createElementNS(NS_OTA, "ota:PassengerQualifyingInfo");
		String passengerType = passengerJson.getString("passengerType");
		paxTypeElem.setAttribute("Code", passengerType);

		passengerElem.appendChild(paxTypeElem);

		passengerDetail = ownerDoc.createElementNS(NS_OTA, "ota:PassengerDetail");
		passengerElem.appendChild(passengerDetail);
		identification = ownerDoc.createElementNS(NS_OTA, "ota:Identification");
		passengerDetail.appendChild(identification);
		givenName = ownerDoc.createElementNS(NS_OTA, "ota:GivenName");
		givenName.setTextContent(passengerJson.getString("firstName"));
		surname = ownerDoc.createElementNS(NS_OTA, "ota:Surname");
		surname.setTextContent(passengerJson.getString("lastName"));
		identification.appendChild(givenName);
		identification.appendChild(surname);
		// if (rph == 1 && passengerType.equals("Adult")) {
		if (passengerType.equals("Adult")) {
			phone = ownerDoc.createElementNS(NS_OTA, "ota:Telephone");
			phone.setAttribute("PhoneNumber", reqBody.getString("phoneNumber"));
			passengerDetail.appendChild(phone);
		}
		tpaExt = ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
		passengerDetail.appendChild(tpaExt);
		identification.appendChild(tpaExt);
		System.out.println("Passenger details elem: " + XMLTransformer.toString(passengerDetail));
		tpa = ownerDoc.createElementNS(NS_RAIL, "rail:Rail_TPA");
		tpaExt.appendChild(tpa);
		paxDetails = ownerDoc.createElementNS(NS_RAIL, "rail:PassengerDetails");
		paxDetails.setAttribute("Age", String.valueOf(passengerJson.getInt("age")));

		tpa.appendChild(paxDetails);
		if (passengerJson.has("berthChoice") && passengerJson.getString("berthChoice") != null) {
			paxDetails.setAttribute("BerthChoice", passengerJson.getString("berthChoice"));
		}
		if (passengerJson.has("bedrollChoice")) {
			paxDetails.setAttribute("BedrollChoice", String.valueOf(passengerJson.getBoolean("bedrollChoice")));
		}
		if (passengerJson.has("meal") && passengerJson.getString("meal") != null) {
			foodChoice = ownerDoc.createElementNS(NS_RAIL, "rail:FoodChoice");
			foodChoice.setTextContent(passengerJson.getString("meal"));
			paxDetails.appendChild(foodChoice);
		}
		JSONObject idProof;
		if (passengerType.equals("Senior") && passengerJson.getBoolean("concession") == true) {
			profileRef = ownerDoc.createElementNS(NS_OTA, "ota:ProfileRef");
			uniqueId = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
			idProof = passengerJson.getJSONObject("identityProof");
			uniqueId.setAttribute("Type", idProof.getString("docType"));
			uniqueId.setAttribute("ID", idProof.getString("number"));
			passengerDetail.appendChild(profileRef);
			profileRef.appendChild(uniqueId);
			paxDetails.setAttribute("Concession", String.valueOf(passengerJson.getBoolean("concession")));
		}
		if (passengerType.equals("Child") && passengerJson.has("childBerthNeeded")) {
			paxDetails.setAttribute("ChildBerthNeeded", String.valueOf(passengerJson.has("childBerthNeeded")));
		}
		address = ownerDoc.createElementNS(NS_OTA, "ota:Address");
		country = ownerDoc.createElementNS(NS_OTA, "ota:CountryName");
		country.setAttribute("Code", passengerJson.getString("nationality"));
		passengerDetail.appendChild(address);
		address.appendChild(country);

	}

}
