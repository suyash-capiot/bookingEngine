package com.coxandkings.travel.bookingengine.orchestrator.rail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.rail.RailConfig;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class RailGetDetailsProcessor implements RailConstants {

	private static final Logger logger = LogManager.getLogger(RailGetDetailsProcessor.class);
	static final String OPERATION_NAME = "getDetails";

	public static String process(JSONObject reqJson) {
		try {

			// opConfig: contains all details of search operation-SI URL, Req XML Shell
			//OperationConfig opConfig = RailConfig.getOperationConfig(OPERATION_NAME);
			ServiceConfig opConfig = RailConfig.getOperationConfig(OPERATION_NAME);
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
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


			// **********WEM JSON TO SI XML FOR REQUEST HEADER STARTS HERE**********//

			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:SessionID", sessionId);
			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:TransactionID", transactionId);
			XMLUtils.setValueAtXPath(reqElem, "./rail:RequestHeader/com:UserID", userId);

			Element suppCredsList = XMLUtils.getFirstElementAtXPath(reqElem,
					"./rail:RequestHeader/com:SupplierCredentialsList");
			if (productSuppliers == null) {
				throw new Exception("Product supplier not found for user/client");
			}

			for (ProductSupplier prodSupplier : productSuppliers) {
				suppCredsList.appendChild(prodSupplier.toElement(ownerDoc));
			}

			// ************WEM JSON TO SI XML FOR REQUEST HEADER ENDS HERE************//

			// *******WEM JSON TO SI XML FOR REQUEST BODY STARTS HERE*********//

			XMLUtils.setValueAtXPath(reqElem, "./raili:RequestBody/rail:OTA_RailShopRQWrapper/rail:SupplierID",
					reqBodyJson.getString(JSON_PROP_SUPPREF));
			// TODO hard coded sequence value. ask if wrapper can repeat in case of IRCTC
			XMLUtils.setValueAtXPath(reqElem, "./raili:RequestBody/rail:OTA_RailShopRQWrapper/rail:Sequence", "1");

			XMLUtils.setValueAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_RailShopRQWrapper/ota:OTA_RailShopRQ/ota:OriginDestination/ota:DepartureDateTime",
					reqBodyJson.getString(JSON_PROP_TRAVELDATE));
			XMLUtils.setValueAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_RailShopRQWrapper/ota:OTA_RailShopRQ/ota:OriginDestination/ota:OriginLocation",
					reqBodyJson.getString(JSON_PROP_ORIGINLOC));
			XMLUtils.setValueAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_RailShopRQWrapper/ota:OTA_RailShopRQ/ota:OriginDestination/ota:DestinationLocation",
					reqBodyJson.getString(JSON_PROP_DESTLOC));
			
			Element railShopElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./raili:RequestBody/rail:OTA_RailShopRQWrapper/ota:OTA_RailShopRQ");

			XMLUtils.setValueAtXPath(railShopElem,
					"./ota:TPA_Extensions/rail:Rail_TPA/rail:ReservationDetails/rail:ReservationClass",
					reqBodyJson.getString(JSON_PROP_RESERVATIONCLASS));
			XMLUtils.setValueAtXPath(railShopElem,
					"./ota:TPA_Extensions/rail:Rail_TPA/rail:ReservationDetails/rail:ReservationType",
					reqBodyJson.getString(JSON_PROP_RESERVATIONTYPE));
	
			XMLUtils.setValueAtXPath(railShopElem, "./ota:RailSearchCriteria/ota:Train/ota:TrainNumber",
					reqBodyJson.getString(JSON_PROP_TRAINNUM));

			// ********WEM JSON TO SI XML FOR REQUEST BODY ENDS HERE***********//

			// ***********CONSUME SI GET DETAILS SERVICE*************//

			logger.info("Before opening HttpURLConnection to SI");
			Element resElem = null;
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(),
					opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			logger.info("HttpURLConnection to SI closed");
			JSONObject resJson;
			resJson = RailSearchProcessor.getSupplierResponseJSON(reqJson, resElem);
			logger.info("SI res: " + resJson.toString());
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));

			logger.info("After creating response from SI");

			// ***********SI SEARCH RESPONSE CONVERTED TO JSON***********//
			//System.out.println("GD Res: "+resJson.toString());
			Map<String, JSONObject> brmsToSIClassMap = new LinkedHashMap<String, JSONObject>();
			JSONObject resSupplierComJson = SupplierCommercials.getSupplierCommercials(reqJson, resJson,
					brmsToSIClassMap);
			
			JSONObject resClientComJson = ClientCommercials.getClientCommercials(resSupplierComJson);
			//calculatePrices(reqJson, resJson, resClientComJson, brmsToSIClassMap);
		//	 System.out.println("Client comm res: " + resClientComJson.toString());
			//logger.info("Client comm res: " + resClientComJson.toString());
			return resJson.toString();

		} catch (Exception x) {
			x.printStackTrace();
			return "{\"status\": \"ERROR\"}";
		}
	}


}
