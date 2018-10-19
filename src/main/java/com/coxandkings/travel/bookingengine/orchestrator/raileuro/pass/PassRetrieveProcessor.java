package com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.raileuro.RailEuroConfig;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.RailEuropeConstants;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class PassRetrieveProcessor implements RailEuropeConstants{
	
	private static final Logger logger = LogManager.getLogger(PassRetrieveProcessor.class);
	
	public static String process(JSONObject reqJson)
	{
		
			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			Element reqElem = null;
			//OperationConfig opConfig = null;
			ServiceConfig opConfig = null;
			JSONObject resJson = new JSONObject();
			JSONObject reqBodyJson = null;
			try {
				TrackingContext.setTrackingContext(reqJson);
				opConfig =RailEuroConfig.getOperationConfig("retrieve");
				reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
				Document ownerDoc = reqElem.getOwnerDocument(); 
				Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody");
				Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./wwr:OTA_WWRailReadRQWrapper");
				reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
				reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
				UserContext usrContxt = UserContext.getUserContextForSession(reqHdrJson);
				
				PassSearchProcessor.createHeader(reqElem, reqHdrJson);
				
				PassSearchProcessor.getSupplierCredentials(reqElem, ownerDoc, usrContxt);
				
				XMLUtils.setValueAtXPath(wrapperElem, "./wwr:Sequence", "0");
				Element suppIDElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./wwr:SupplierID");
				suppIDElem.setTextContent(JSON_PROP_SUPPID);
				
				Element railReadRQ = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody/wwr:OTA_WWRailReadRQWrapper/ota:OTA_WWRailReadRQ");
				
				Element bookingId = ownerDoc.createElementNS(Constants.NS_OTA, "ota:BookingId");
				bookingId.setTextContent(reqBodyJson.optString(JSON_PROP_SUPPBOOKINGID));
				
				railReadRQ.appendChild(bookingId);
				wrapperElem.appendChild(railReadRQ);
				
//				System.out.println(XMLTransformer.toString(reqElem));
				
				Element resElem = null;
				//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), RailEuroConfig.getHttpHeaders(), reqElem);
				resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
				
//				System.out.println(XMLTransformer.toString(resElem));
				
				if (resElem == null) {
					throw new Exception("Null response received from SI");
				}
				
				//populate retrieve response json
				
				JSONObject resBodyJson = new JSONObject();
				
				Element resBodyElem = XMLUtils.getFirstElementAtXPath(resElem,
						"./wwr1:ResponseBody/wwr:OTA_WWRailResRetrieveDetailRSWrapper");
				
				Element bookingDetails = XMLUtils.getFirstElementAtXPath(resBodyElem,
						"./ota:OTA_WWRailResRetrieveDetailRS/ota:Content/ota:BookingDetailsSearchContentList/ota:BookingDetails");
				
				resBodyJson.put(JSON_PROP_DOCTYPE_EURONETUSERID, XMLUtils.getValueAtXPath(bookingDetails, "./ota:EuronetUserId"));
				resBodyJson.put(JSON_PROP_DOCTYPE_EURONETUSERNAME, XMLUtils.getValueAtXPath(bookingDetails, "./ota:EuronetUserName"));
				resBodyJson.put(JSON_PROP_SUPPBOOKINGID, XMLUtils.getValueAtXPath(bookingDetails, "./ota:BookingId"));
				resBodyJson.put(JSON_PROP_PRINTINGOPTION, XMLUtils.getValueAtXPath(bookingDetails, "./ota:SelectedPrintingOption"));
				resBodyJson.put(JSON_PROP_AGENTNAME, XMLUtils.getValueAtXPath(bookingDetails, "./ota:AgentName"));
				resBodyJson.put(JSON_PROP_BOOKINGSTATUS, XMLUtils.getValueAtXPath(bookingDetails, "./ota:BookingStatus"));
				resBodyJson.put(JSON_PROP_PAYMENTREADY_CHECK, XMLUtils.getValueAtXPath(bookingDetails, "./ota:IsReadyForPayment"));
				resBodyJson.put(JSON_PROP_BOOKINGPRINTOPTION, XMLUtils.getValueAtXPath(bookingDetails, "./ota:BookingPrintingOptionList"));
				
				JSONObject contactInformation = new JSONObject();
				JSONObject address = PassBookProcessor.getContactInfo(resBodyJson, bookingDetails, contactInformation);
				
				resBodyJson.put(JSON_PROP_BOOKINGEXPIRYDATE, XMLUtils.getValueAtXPath(bookingDetails, "./ota:BookingExpirationDate"));
				
				PassBookProcessor.getLeadPassengerName(resBodyJson, bookingDetails);
				
				resBodyJson.put(JSON_PROP_DEPARTUREDATETOEUR, XMLUtils.getValueAtXPath(bookingDetails, "./ota:DepartureDateToEurope"));
				
				PassBookProcessor.getAgentInfo(resBodyJson, bookingDetails, contactInformation);
				
				PassBookProcessor.getTrackingInfo(resBodyJson, bookingDetails);
				
				PassBookProcessor.getShippingDetails(resBodyJson, bookingDetails, address);
				
				getBillingDetails(resBodyJson, bookingDetails);
				
				resBodyJson.put(JSON_PROP_NUMBEROFCOUPONS, XMLUtils.getValueAtXPath(bookingDetails, "./ota:NCouponsPrinted"));
				resBodyJson.put(JSON_PROP_INVOICENUMBER, XMLUtils.getValueAtXPath(bookingDetails, "./ota:InvoiceNumber"));
				
				//loop passProducts
				Element[] bookedPassProductElement = XMLUtils.getElementsAtXPath(bookingDetails,
						"./ota:PassProducts/ota:BookedPassProduct");
				JSONArray passProducts = new JSONArray();
				
				PassBookProcessor.getBookedPassProducts(bookedPassProductElement, passProducts);
				
				resBodyJson.put(JSON_PROP_PASSPROD, passProducts);
				
				resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
				resJson.put(JSON_PROP_RESBODY, resBodyJson);
				
				logger.info("SI res: " + resJson);
				logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
				
				

			} catch (Exception x) {
				x.printStackTrace();
				return "{\"status\": \"ERROR\"}";
			}
			
			return resJson.toString();
			
			
	}

	public static void getBillingDetails(JSONObject resBodyJson, Element bookingDetails) {
		JSONObject billingDet = new JSONObject();
		Element billingDetails = XMLUtils.getFirstElementAtXPath(bookingDetails,"./ota:BillingDetails");
		Element appliedAmount = XMLUtils.getFirstElementAtXPath(billingDetails,"./ota:AppliedAmount");
		JSONObject appliedAmountObj = new JSONObject();
		PassBookProcessor.insertCurrencyCodeAndAmt(appliedAmount, appliedAmountObj);
		billingDet.put(JSON_PROP_DOCTYPE_APPLIEDAMOUNT, appliedAmountObj);
		Element paidAmount = XMLUtils.getFirstElementAtXPath(billingDetails,"./ota:PaidAmount");
		JSONObject paidAmountObj = new JSONObject();
		PassBookProcessor.insertCurrencyCodeAndAmt(paidAmount, paidAmountObj);
		billingDet.put(JSON_PROP_DOCTYPE_PAIDAMOUNT, paidAmountObj);
		billingDet.put(JSON_PROP_DOCTYPE_PAYMENTDATE, XMLUtils.getValueAtXPath(billingDetails, "./ota:PaymentDate"));
		billingDet.put(JSON_PROP_DOCTYPE_PAYMENTTYPE, XMLUtils.getValueAtXPath(billingDetails, "./ota:PaymentType"));
		resBodyJson.put(JSON_PROP_BILLINGDET, billingDet);
	}
	
	
}
