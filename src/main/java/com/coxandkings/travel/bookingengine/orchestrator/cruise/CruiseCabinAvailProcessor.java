package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.cruise.CruiseConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class CruiseCabinAvailProcessor implements CruiseConstants {
	
	private static final Logger logger = LogManager.getLogger(CruiseCabinAvailProcessor.class);
	
	public static String process(JSONObject reqJson) throws Exception {
		
		//OperationConfig opConfig = null;Element reqElem =null;UserContext userctx =null;JSONObject reqHdrJson = null;JSONObject reqBodyJson = null;
		ServiceConfig opConfig = null;Element reqElem =null;UserContext userctx =null;JSONObject reqHdrJson = null;JSONObject reqBodyJson = null;
		try{
			TrackingContext.setTrackingContext(reqJson);
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			userctx = UserContext.getUserContextForSession(reqHdrJson);
			
			opConfig = CruiseConfig.getOperationConfig("cabinavail");
			reqElem = createSIRequest(opConfig, userctx, reqHdrJson, reqBodyJson);
		}
		catch(Exception e){
			logger.error("Exception during request processing", e);
			throw new RequestProcessingException(e);
		}
		try
		{
			Element resElem = null;
	        //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), CruiseConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
	        if (resElem == null) {
	        	throw new Exception("Null response received from SI");
	        }
		
	        JSONObject resBodyJson = new JSONObject();
	        JSONArray cabinOptionJsonArr = new JSONArray();
	        System.out.println(XMLTransformer.toString(resElem));
	        Element[] RswrapperElems = XMLUtils.getElementsAtXPath(resElem, "./cru:ResponseBody/cru1:OTA_CruiseCabinAvailRSWrapper");
	        
	        for(Element RswrapperElem : RswrapperElems)
	        {
	        	//IF SI gives an error
	        	Element[] errorListElems = XMLUtils.getElementsAtXPath(RswrapperElem, "./ota:OTA_CruiseCabinAvailRS/ota:Errors/ota:Error");
	        	if(errorListElems!=null && errorListElems.length!=0)
	        	{
	        		int errorInt=0;
	        		JSONObject errorObj = new JSONObject();
	        		for(Element errorListElem : errorListElems)
	        		{
	        			errorObj.put(String.format("%s %s", "Error",errorInt), XMLUtils.getValueAtXPath(errorListElem, "/ota:Error"));
	        		}
	        		return errorObj.toString();//Code will stop here if SI gives an error
	        	}
	        	getCabinOptionsJSON(RswrapperElem,cabinOptionJsonArr);
	        }
	        
	        resBodyJson.put("cabinOptions", cabinOptionJsonArr);
	        
	        JSONObject resJson = new JSONObject();
	        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
	        resJson.put(JSON_PROP_RESBODY, resBodyJson);
	        
			return resJson.toString();
		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}

	//private static Element createSIRequest(OperationConfig opConfig, UserContext userctx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext userctx, JSONObject reqHdrJson, JSONObject reqBodyJson) throws Exception {
		Element reqElem;
		reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./cru1:OTA_CruiseCabinAvailRQWrapper");
		reqBodyElem.removeChild(wrapperElem);

		
		
		CruiseSearchProcessor.createHeader(reqElem,reqHdrJson);
		
		Element suppWrapperElem = null;
		int seqItr =0;
		
		JSONArray categoryOptionsReqArr = reqBodyJson.getJSONArray("cruiseOptions");
		
		for(int i=0;i<categoryOptionsReqArr.length();i++)
		{
			JSONObject cabinOptionsJson = categoryOptionsReqArr.getJSONObject(i);
			
			String suppID = cabinOptionsJson.getString("supplierRef");
			
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
			
			ProductSupplier prodSupplier = userctx.getSupplierForProduct(PROD_CATEG_TRANSPORT, "Cruise", suppID);
			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}
			seqItr++;
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru1:RequestHeader/com:SupplierCredentialsList");
//--------------------------------------------------------------Adding sequence and suppliercredentialslist------------------------------------------------------
			Element supplierCredentials = prodSupplier.toElement(ownerDoc);
			
			Element sequence = ownerDoc.createElementNS(Constants.NS_COM, "Sequence");
			sequence.setTextContent(String.valueOf(seqItr));
			
			Element credentialsElem = XMLUtils.getFirstElementAtXPath(supplierCredentials, "./com:Credentials");
			supplierCredentials.insertBefore(sequence, credentialsElem);
			
			suppCredsListElem.appendChild(supplierCredentials);
			
//-------------------------------------------------Response Body--------------------------------------------------------------------------------
			
			Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./cru1:SupplierID");
			suppIDElem.setTextContent(suppID);
			
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./cru1:Sequence");
			sequenceElem.setTextContent(String.valueOf(seqItr));
			
			Element otaCabinAvail = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_CruiseCabinAvailRQ");
			
			CruisePriceProcessor.createPOS(ownerDoc, otaCabinAvail);
			
			JSONArray guestsReqJsonArr = cabinOptionsJson.getJSONArray("Guests");
			CruisePriceProcessor.createGuestsAndGuestCounts(ownerDoc,guestsReqJsonArr,otaCabinAvail);
			
			Element selectedSailingElem = XMLUtils.getFirstElementAtXPath(otaCabinAvail, "./ota:SailingInfo/ota:SelectedSailing");
			selectedSailingElem.setAttribute("VoyageID",cabinOptionsJson.getJSONObject("SailingInfo").getJSONObject("selectedSailing").getString("voyageId"));
			selectedSailingElem.setAttribute("Start",cabinOptionsJson.getJSONObject("SailingInfo").getJSONObject("selectedSailing").getString("start"));
			
			Element cruiseLineElem = XMLUtils.getFirstElementAtXPath(selectedSailingElem, "./ota:CruiseLine");
			cruiseLineElem.setAttribute("ShipCode", cabinOptionsJson.getJSONObject("SailingInfo").getJSONObject("selectedSailing").getJSONObject("cruiseLine").getString("shipCode"));
			cruiseLineElem.setAttribute("VendorCodeContext", cabinOptionsJson.getJSONObject("SailingInfo").getJSONObject("selectedSailing").getJSONObject("cruiseLine").getString("vendorCodeCotext"));
			
			Element selectedCategoryElem = XMLUtils.getFirstElementAtXPath(otaCabinAvail, "./ota:SailingInfo/ota:SelectedCategory");
			selectedCategoryElem.setAttribute("PricedCategoryCode", cabinOptionsJson.getJSONObject("SailingInfo").getJSONObject("selectedSailing").getJSONObject("selectedCategory").getString("PricedCategoryCode"));
			
			Element selectedCabinElem = XMLUtils.getFirstElementAtXPath(selectedCategoryElem, "./ota:SelectedCabin");
			selectedCabinElem.setAttribute("CabinCategoryStatusCode", cabinOptionsJson.getJSONObject("SailingInfo").getJSONObject("selectedSailing").getJSONObject("selectedCategory").getJSONObject("SelectedCabin").getString("CabinCategoryStatusCode"));
			selectedCabinElem.setAttribute("MaxOccupancy", cabinOptionsJson.getJSONObject("SailingInfo").getJSONObject("selectedSailing").getJSONObject("selectedCategory").getJSONObject("SelectedCabin").getString("MaxOccupancy"));
			
			Element ItineraryIDElem = XMLUtils.getFirstElementAtXPath(selectedCategoryElem, "./ota:TPA_Extensions/cru1:ItineraryID");
			ItineraryIDElem.setTextContent(cabinOptionsJson.getJSONObject("SailingInfo").getJSONObject("selectedSailing").getJSONObject("selectedCategory").getJSONObject("SelectedCabin").getJSONObject("TPA_Extensions").getString("ItineraryID"));;
			
			Element selectedFareElem = XMLUtils.getFirstElementAtXPath(otaCabinAvail, "./ota:SelectedFare");
			selectedFareElem.setAttribute("FareCode", cabinOptionsJson.getJSONObject("SelectedFare").getString("FareCode"));
			
			Element sailingElem = XMLUtils.getFirstElementAtXPath(otaCabinAvail, "./ota:TPA_Extensions/cru1:Cruise/cru1:SailingDates/cru1:Sailing");
			sailingElem.setAttribute("SailingID", cabinOptionsJson.getJSONObject("TPA_Extensions").getJSONObject("Cruise").getJSONObject("SailingDates").getJSONObject("Sailing").getString("SailingID"));
			
		}
		return reqElem;
	}
	
	public static void getCabinOptionsJSON(Element wrapperElem, JSONArray cabinOptionJsonArr) throws Exception {
		 
//		 getSupplierResponseSailingOptionsJSONV2(wrapperElem, sailingOptionJsonArr,false);
		getCabinOptionsJSON(wrapperElem, cabinOptionJsonArr,false);
   }

	public static void getCabinOptionsJSON(Element resBodyElem, JSONArray cabinOptionJsonArr,Boolean value) throws Exception {
    	
		 Element[] cabinOptionsElems = XMLUtils.getElementsAtXPath(resBodyElem, "./ota:OTA_CruiseCabinAvailRS/ota:CabinOptions/ota:CabinOption");
		 for(Element cabinOptionElem : cabinOptionsElems)
		 {
			 JSONObject cabinOptionJson = new JSONObject();
			 
			 cabinOptionJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath((Element)resBodyElem.getParentNode(), "./cru1:SupplierID"));
			 cabinOptionJson.put("cabinNumber", XMLUtils.getValueAtXPath(cabinOptionElem, "./@CabinNumber"));
			 cabinOptionJson.put("deckName", XMLUtils.getValueAtXPath(cabinOptionElem, "./@DeckName"));
			 cabinOptionJson.put("deckNumber", XMLUtils.getValueAtXPath(cabinOptionElem, "./@DeckNumber"));
			 cabinOptionJson.put("status", XMLUtils.getValueAtXPath(cabinOptionElem, "./@Status"));
			 
			 cabinOptionJson.put("remark", XMLUtils.getValueAtXPath(cabinOptionElem, "./ota:Remark"));
			 cabinOptionJson.put("isGuaranteed", XMLUtils.getValueAtXPath(cabinOptionElem, "./ota:TPA_Extensions/cru1:IsGuaranteed"));
			 
			 cabinOptionJsonArr.put(cabinOptionJson);
		 }
   }
	
	 private static JSONObject getRemarkJSON(Element sailingOptionElem)
	 {
		 
		 return null;
	 }
	
}
