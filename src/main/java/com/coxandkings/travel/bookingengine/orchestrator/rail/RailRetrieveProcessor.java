package com.coxandkings.travel.bookingengine.orchestrator.rail;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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

public class RailRetrieveProcessor implements RailConstants {

	private static final Logger logger = LogManager.getLogger(RailRetrieveProcessor.class);
	static final String OPERATION_NAME = "retrieve";

	public static String process(JSONObject reqJson)
			throws ValidationException, RequestProcessingException, InternalProcessingException {
		Element reqElem = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
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

			Element suppCredsList = XMLUtils.getFirstElementAtXPath(reqElem,
					"./rail:RequestHeader/com:SupplierCredentialsList");
			if (productSuppliers == null) {
				throw new Exception("Product supplier not found for user/client");
			}

			for (ProductSupplier prodSupplier : productSuppliers) {
				suppCredsList.appendChild(prodSupplier.toElement(ownerDoc));
			}
			// *********WEM JSON TO SI XML FOR REQUEST HEADER STARTS HERE*********//
			validateRequestParameters(reqHdrJson, reqBodyJson);
			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:SessionID", sessionId);
			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:TransactionID", transactionId);
			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:UserID", userId);

			XMLUtils.setValueAtXPath(reqElem, "./raili:RequestBody/rail:OTA_RailReadRQWrapper/rail:SupplierID",
					reqBodyJson.getString(JSON_PROP_SUPPREF));
			// TODO hard coded sequence value. ask if wrapper can repeat in case of IRCTC
			XMLUtils.setValueAtXPath(reqElem, "./raili:RequestBody/rail:OTA_RailReadRQWrapper/rail:Sequence", "1");

			// *******WEM JSON TO SI XML FOR REQUEST HEADER ENDS HERE**********//

			// *******WEM JSON TO SI XML FOR REQUEST BODY STARTS HERE******//

			if (reqBodyJson.has("pnr") && reqBodyJson.getString("pnr") != null
					&& !reqBodyJson.getString("pnr").isEmpty()) {
				XMLUtils.setValueAtXPath(reqElem,
						"./raili:RequestBody/rail:OTA_RailReadRQWrapper/ota:OTA_RailReadRQ/ota:UniqueID/@ID",
						reqBodyJson.getString("pnr"));
				XMLUtils.setValueAtXPath(reqElem,
						"./raili:RequestBody/rail:OTA_RailReadRQWrapper/ota:OTA_RailReadRQ/ota:UniqueID/@ID_Context",
						"pnr");
			} else if (reqBodyJson.has("clienttransactionid") && reqBodyJson.getString("clienttransactionid") != null
					&& !reqBodyJson.getString("clienttransactionid").isEmpty()) {
				XMLUtils.setValueAtXPath(reqElem,
						"./raili:RequestBody/rail:OTA_RailReadRQWrapper/ota:OTA_RailReadRQ/ota:UniqueID/@ID",
						reqBodyJson.getString("clienttransactionid"));
				XMLUtils.setValueAtXPath(reqElem,
						"./raili:RequestBody/rail:OTA_RailReadRQWrapper/ota:OTA_RailReadRQ/ota:UniqueID/@ID_Context",
						"clienttransactionid");
			}

			// hard coded value. since, it is a constant and won't change
			XMLUtils.setValueAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_RailReadRQWrapper/ota:OTA_RailReadRQ/ota:UniqueID/@Type", "16");
			System.out.println("Reprice xml req: " + XMLTransformer.toString(reqElem));
			logger.info("Before opening HttpURLConnection to SI");
			logger.info("Rail retireve service before SI call (Req element): " + XMLTransformer.toString(reqElem));
		} catch (ValidationException validationException) {
			validationException.printStackTrace();
			logger.info("Validation Exception in Rail Retrieve Process: " + validationException);
			throw validationException;
		} catch (Exception x) {
			x.printStackTrace();
			logger.info("Exception in Rail Retrieve Process: " + x);
			throw new RequestProcessingException(x);
		}
		try {
			Element resElem = null;
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(),
					opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			System.out.println(XMLTransformer.toString(resElem));
			logger.info("HttpURLConnection to SI closed");
			logger.info("Rail retireve service before SI Response XML: " + XMLTransformer.toString(resElem));
			JSONObject resJson = getSupplierResponseJSON(reqJson, resElem);
			logger.info("Rail retireve service before SI Response JSON: " + resJson);
			System.out.println("SI response: " + resJson);

			return resJson.toString();
		} catch (Exception x) {
			logger.error("Exception in Rail Retrieve Process: ", x);
			throw new InternalProcessingException(x);
		}

	}

	private static void validateRequestParameters(JSONObject reqHdrJson, JSONObject reqBodyJson)
			throws ValidationException {
		RailRequestValidator.validatePnr(reqBodyJson, reqHdrJson);
		RailRequestValidator.validateSupplierRef(reqBodyJson, reqHdrJson);
	}

	public static JSONObject getSupplierResponseJSON(JSONObject reqJson, Element resElem) {
		JSONObject resJson = new JSONObject();
		Element retrieveDetail = XMLUtils.getFirstElementAtXPath(resElem,
				"./raili:ResponseBody/rail:OTA_RailResRetrieveDetailRSWrapper");
		Element reservationDetail = XMLUtils.getFirstElementAtXPath(resElem,
				"./raili:ResponseBody/rail:OTA_RailResRetrieveDetailRSWrapper/ota:OTA_RailResRetrieveDetailRS/ota:RailReservation");
		Element itinElem = XMLUtils.getFirstElementAtXPath(reservationDetail, "./ota:Itinerary");
		Element railChargesElem = XMLUtils.getFirstElementAtXPath(itinElem, "./ota:RailCharges");
		Element tpa = XMLUtils.getFirstElementAtXPath(reservationDetail, "./ota:TPA_Extensions/rail:Rail_TPA");
		Element[] paxInfo = XMLUtils.getElementsAtXPath(reservationDetail, "./ota:PassengerInfo");
		JSONObject resBodyJson = new JSONObject();
		// Read unique ID from xml and set PNR number in response body
		String pnrNo = "";
		Element[] uniqueIDElementArray = XMLUtils.getElementsAtXPath(reservationDetail, "./ota:UniqueID");
		for (Element uniqueIDElem : uniqueIDElementArray) {
			String pnr = XMLUtils.getValueAtXPath(uniqueIDElem, "./@ID_Context");

			if (pnr.equalsIgnoreCase("PNR")) {
				pnrNo = XMLUtils.getValueAtXPath(uniqueIDElem, "./@ID");
			}
		}

		resBodyJson.put(JSON_PROP_PNR, pnrNo);
		resBodyJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(retrieveDetail, "./rail:SupplierID"));
		resBodyJson.put(JSON_PROP_SEQUENCE, XMLUtils.getValueAtXPath(retrieveDetail, "./rail:Sequence"));
		JSONObject itinerary = getItinerary(itinElem);
		JSONObject pricing = getPricingInfo(railChargesElem);
		resBodyJson.put(JSON_PROP_ITINERARY, itinerary);
		resBodyJson.put(JSON_PROP_PRICING, pricing);
		resBodyJson.put(JSON_PROP_PASSENGER_DETAILS, getPaxInfo(paxInfo));
		resBodyJson.put(JSON_PROP_RESERVATION_DETAILS, getReservationDetails(tpa));
		resJson.put(JSON_PROP_RESHEADER, reqJson.get(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);

		return resJson;
	}

	public static JSONObject getItinerary(Element itin) {
		JSONObject itinJson = new JSONObject();

		itinJson.put(JSON_PROP_ORIGINLOC, XMLUtils.getValueAtXPath(itin,
				"./ota:OriginAndDestination/ota:TrainSegment/ota:DepartureStation/ota:Details/@LocationCode"));
		itinJson.put(JSON_PROP_DESTLOC, XMLUtils.getValueAtXPath(itin,
				"./ota:OriginAndDestination/ota:TrainSegment/ota:ArrivalStation/ota:Details/@LocationCode"));
		itinJson.put(JSON_PROP_DEPARTTIME,
				XMLUtils.getValueAtXPath(itin, "./ota:OriginAndDestination/ota:TrainSegment/@DepartureDateTime"));
		itinJson.put(JSON_PROP_ARRIVALTIME,
				XMLUtils.getValueAtXPath(itin, "./ota:OriginAndDestination/ota:TrainSegment/@ArrivalDateTime"));
		itinJson.put(JSON_PROP_TRAINNUM, XMLUtils.getValueAtXPath(itin,
				"./ota:OriginAndDestination/ota:TrainSegment/ota:TrainInfo/ota:Train/ota:TrainNumber"));
		return itinJson;
	}

	public static JSONObject getPricingInfo(Element priceElem) {
		JSONObject priceJson = new JSONObject();
		JSONObject total = new JSONObject();
		JSONArray taxArr = new JSONArray();
		priceJson.put(JSON_PROP_TOTAL, total);
		total.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(priceElem, "./ota:Total/@AmountAfterTax"));
		total.put(JSON_PROP_CURRENCYCODE, XMLUtils.getValueAtXPath(priceElem, "./ota:Total/@CurrencyCode"));
		total.put(JSON_PROP_TAXES, taxArr);
		Element[] taxes = XMLUtils.getElementsAtXPath(priceElem, "./ota:Total/ota:Taxes/ota:Tax");
		for (Element t : taxes) {
			JSONObject taxObj = new JSONObject();
			taxObj.put(JSON_PROP_CODECONTEXT, XMLUtils.getValueAtXPath(t, "./ota:TaxDescription/@Name"));
			taxObj.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(t, "./@Amount"));
			taxObj.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(t, "./@CurrencyCode"));
			taxArr.put(taxObj);
		}
		priceJson.put("charges", getCharges(priceElem));
		return priceJson;
	}

	public static JSONArray getCharges(Element priceElem) {
		JSONArray chargesArr = new JSONArray();
		Element[] chargesElem = XMLUtils.getElementsAtXPath(priceElem, "./ota:Charges/ota:Charge");
		for (Element charge : chargesElem) {
			JSONObject taxObj = new JSONObject();
			taxObj.put(JSON_PROP_CODECONTEXT, XMLUtils.getValueAtXPath(charge, "./ota:Description/ota:Text"));
			taxObj.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(charge, "./@Amount"));
			taxObj.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(charge, "./@CurrencyCode"));
			chargesArr.put(taxObj);
		}
		return chargesArr;
	}

	public static JSONObject getReservationDetails(Element tpa) {
		JSONObject resDet = new JSONObject();
		JSONObject insuranceJson = new JSONObject();
		Element insuranceElem = XMLUtils.getFirstElementAtXPath(tpa, "./rail:InsuranceDetails");
		resDet.put("insuranceDetails", insuranceJson);
		resDet.put(JSON_PROP_RESERVATIONCLASS,
				XMLUtils.getValueAtXPath(tpa, "./rail:ReservationDetails/rail:ReservationClass"));
		resDet.put(JSON_PROP_RESERVATIONTYPE,
				XMLUtils.getValueAtXPath(tpa, "./rail:ReservationDetails/rail:ReservationType"));
		resDet.put("reservationDate", XMLUtils.getValueAtXPath(tpa, "./rail:ReservationDetails/rail:ReservationDate"));

		insuranceJson.put("insuredPax", XMLUtils.getValueAtXPath(insuranceElem, "./rail:insuredPassengers"));
		insuranceJson.put("issueDate", XMLUtils.getValueAtXPath(insuranceElem, "./rail:PolicyIssueDate"));
		insuranceJson.put("company", XMLUtils.getValueAtXPath(insuranceElem, "./rail:InsuranceCompany/@name"));
		insuranceJson.put("url", XMLUtils.getValueAtXPath(insuranceElem, "./rail:InsuranceCompany/@URL"));
		insuranceJson.put("status", XMLUtils.getValueAtXPath(insuranceElem, "./rail:PolicyStatus"));

		return resDet;
	}

	public static JSONArray getPaxInfo(Element[] paxInfoArr) {
		JSONArray passengerArr = new JSONArray();
		// JSONObject paxObj = new JSONObject();

		for (Element pax : paxInfoArr) {
			JSONObject paxObj = new JSONObject();
			// Booking berth details start
			paxObj.put("berthNo", XMLUtils.getValueAtXPath(pax,
					"./ota:PassengerDetail/ota:Identification/ota:TPA_Extensions/rail:Rail_TPA/rail:PassengerDetails/rail:BookingBerthDetails/rail:bookingBerthNo"));

			paxObj.put("coachId", XMLUtils.getValueAtXPath(pax,
					"./ota:PassengerDetail/ota:Identification/ota:TPA_Extensions/rail:Rail_TPA/rail:PassengerDetails/rail:BookingBerthDetails/rail:bookingCoachId"));

			paxObj.put("bookingStatus", XMLUtils.getValueAtXPath(pax,
					"./ota:PassengerDetail/ota:Identification/ota:TPA_Extensions/rail:Rail_TPA/rail:PassengerDetails/rail:BookingBerthDetails/rail:bookingStatus"));
			// Booking berth details end

			// current berth details start
			paxObj.put("currentBerthNo", XMLUtils.getValueAtXPath(pax,
					"./ota:PassengerDetail/ota:Identification/ota:TPA_Extensions/rail:Rail_TPA/rail:PassengerDetails/rail:CurrentBerthDetails/rail:currentBerthNo"));

			paxObj.put("currentCoachId", XMLUtils.getValueAtXPath(pax,
					"./ota:PassengerDetail/ota:Identification/ota:TPA_Extensions/rail:Rail_TPA/rail:PassengerDetails/rail:CurrentBerthDetails/rail:currentCoachId"));

			paxObj.put("currentBookingStatus", XMLUtils.getValueAtXPath(pax,
					"./ota:PassengerDetail/ota:Identification/ota:TPA_Extensions/rail:Rail_TPA/rail:PassengerDetails/rail:CurrentBerthDetails/rail:currentStatus"));
			// end

			paxObj.put("age", XMLUtils.getValueAtXPath(pax,
					"./ota:PassengerDetail/ota:Identification/ota:TPA_Extensions/rail:Rail_TPA/rail:PassengerDetails/@Age"));
			paxObj.put("berthChoice", XMLUtils.getValueAtXPath(pax,
					"./ota:PassengerDetail/ota:Identification/ota:TPA_Extensions/rail:Rail_TPA/rail:PassengerDetails/@BerthChoice"));
			paxObj.put("policyNumber", XMLUtils.getValueAtXPath(pax,
					"./ota:PassengerDetail/ota:Identification/ota:TPA_Extensions/rail:Rail_TPA/rail:PassengerDetails/@Policynumber"));
			paxObj.put("fare", XMLUtils.getValueAtXPath(pax,
					"./ota:PassengerDetail/ota:Identification/ota:TPA_Extensions/rail:Rail_TPA/rail:PassengerDetails/rail:PassengerFare/@Amount"));
			paxObj.put("gender", XMLUtils.getValueAtXPath(pax, "./@Gender"));
			paxObj.put("index", XMLUtils.getValueAtXPath(pax, "./@RPH"));
			paxObj.put("docId",
					XMLUtils.getValueAtXPath(pax, "./ota:PassengerDetail/ota:Identification/ota:Document/@DocID"));
			paxObj.put("name", XMLUtils.getValueAtXPath(pax, "./ota:PassengerDetail/ota:Identification/ota:GivenName"));
			passengerArr.put(paxObj);
		}
		return passengerArr;
	}

}
