package com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.car.CarConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class CarGetPoliciesProcessor implements CarConstants{
	
	private static final Logger logger = LogManager.getLogger(CarGetPoliciesProcessor.class);
	
	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException{
		
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		
		try {
			opConfig = CarConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./car:OTA_VehResRQWrapper");

			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			CarSearchProcessor.createHeader(reqHdrJson, reqElem);
			
			JSONObject bookReq = reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);
			populateWrapperElement(reqElem, ownerDoc, wrapperElem, usrCtx, bookReq);
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		try {
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), CarConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
            Element resWrapperElem = XMLUtils.getFirstElementAtXPath(resElem,"./cari:ResponseBody/car:OTA_VehResRSWrapper");
				
			JSONObject reservationJson = new JSONObject();
			reservationJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(resWrapperElem, "./car:SupplierID"));
			Element vehResCoreElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_VehResRS/ota:VehResRSCore");
			getSupplierBookResponseJSON(vehResCoreElem, reservationJson);
			
			JSONObject resBodyJson = new JSONObject();
			resBodyJson.put(JSON_PROP_CARRENTALARR, reservationJson);

			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			return resJson.toString();
			
		}catch(Exception x) {
			logger.warn(String.format("An exception occured when getting policies for Car"), x);
			return "{\"status\": \"ERROR\"}";
		}
	}

	private static void getSupplierBookResponseJSON(Element vehResCoreElem, JSONObject reservationJson) {
		
		Element[] vendorMsgElems = XMLUtils.getElementsAtXPath(vehResCoreElem, "./ota:Reservation/ota:VehSegmentInfo/ota:VendorMessages/ota:VendorMessage");
		JSONArray policiesArr = new JSONArray();
		for(Element vendorMsgElem : vendorMsgElems) {
			
			JSONObject policyJson = new JSONObject();
			policyJson.put("title", XMLUtils.getValueAtXPath(vendorMsgElem, "./@Title"));
			JSONArray subPoliciesArr = new JSONArray();
			for(Element subPolicyElem : XMLUtils.getElementsAtXPath(vendorMsgElem, "./ota:SubSection")) {
				JSONObject subPolicyJson = new JSONObject();
				subPolicyJson.put("title", XMLUtils.getValueAtXPath(subPolicyElem, "./@SubTitle"));
				subPolicyJson.put("code", XMLUtils.getValueAtXPath(subPolicyElem, "./@SubCode"));
				JSONArray paragraphArr = new JSONArray();
				for(Element paragraphElem :  XMLUtils.getElementsAtXPath(subPolicyElem, "./ota:SubSection/ota:Paragraph")) {
					JSONObject paragraphjson = new JSONObject();
					paragraphjson.put("text", XMLUtils.getValueAtXPath(paragraphElem, "./ota:Text"));
					paragraphjson.put("url", XMLUtils.getValueAtXPath(paragraphElem, "./ota:URL"));
					paragraphArr.put(paragraphjson);
				}
				subPolicyJson.put("paragraph", paragraphArr);
				subPoliciesArr.put(subPolicyJson);
			}
			policyJson.put("policy", subPoliciesArr);
		}
		reservationJson.put("policies", policiesArr);
	}

	private static void populateWrapperElement(Element reqElem, Document ownerDoc, Element suppWrapperElem,
			UserContext usrCtx, JSONObject getPolicyReq) throws Exception{
		
		// TODO : Need To Change later
		Element sourceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehResRQ/ota:POS/ota:Source");
		sourceElem.setAttribute("ISOCurrency", "EUR");
		sourceElem.setAttribute("ISOCountry", "IE");
		
		String suppID = getPolicyReq.getString(JSON_PROP_SUPPREF);
		
		ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_CAR, suppID);
		if (prodSupplier == null) {
			throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
		}

		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestHeader/com:SupplierCredentialsList");
		Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem, String.format("./com:SupplierCredentials[com:SupplierID = '%s']", suppID));
		if (suppCredsElem == null) {
			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
		}

		Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./car:SupplierID");
		suppIDElem.setTextContent(suppID);
		
		 Element vehResCoreElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehResRQ/ota:VehResRQCore");
		 Element vehResInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehResRQ/ota:VehResRQInfo");
		 
		 Element vehRentalElem =  CarSearchProcessor.getVehRentalCoreElement(ownerDoc, getPolicyReq);
		 vehResCoreElem.appendChild(vehRentalElem);
		 
		 String temp;
         JSONObject refJson = getPolicyReq.getJSONObject(JSON_PROP_REFERENCE);
         Element refElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Reference");
         if(!(temp = refJson.optString("type")).isEmpty())
        	 refElem.setAttribute("Type", temp);
         if(!(temp = refJson.optString("id")).isEmpty())
        	 refElem.setAttribute("ID", temp);
         if(!(temp = refJson.optString("url")).isEmpty())
        	 refElem.setAttribute("URL", temp);
         if(!(temp = refJson.optString("id_Context")).isEmpty())
        	 refElem.setAttribute("ID_Context", temp);
         
         vehResInfoElem.appendChild(refElem);
         
	}
}
