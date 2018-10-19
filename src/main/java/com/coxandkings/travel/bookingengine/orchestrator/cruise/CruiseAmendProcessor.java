package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.cruise.CruiseConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;


public class CruiseAmendProcessor implements CruiseConstants {
	private static final Logger logger = LogManager.getLogger(CruiseAmendProcessor.class);
	
	public static String process(JSONObject reqJson) throws Exception{
		
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		
		KafkaBookProducer amendProducer = new KafkaBookProducer();
		JSONObject kafkaMsgJson = new JSONObject();
		
		kafkaMsgJson = reqJson;
		try {
			TrackingContext.setTrackingContext(reqJson);
			opConfig = CruiseConfig.getOperationConfig("amend");
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();
			
			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./cru1:OTA_CruiseBookRQWrapper");
			reqBodyElem.removeChild(wrapperElem);

			CruiseSearchProcessor.createHeader(reqElem,reqHdrJson);
			int seqItr =0;
			Element suppWrapperElem = null;
			
			String suppID =	reqBodyJson.getString(JSON_PROP_SUPPREF);
			
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
			
			ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, "Cruise", suppID);
			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cru1:RequestHeader/com:SupplierCredentialsList");
//------------------------------------------------------------------------------------------------------------------------------------------------
			Element supplierCredentials = prodSupplier.toElement(ownerDoc);
			
//			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			//Request Body starts
				
			Element sequence = ownerDoc.createElementNS(Constants.NS_COM, "Sequence");
			sequence.setTextContent(String.valueOf(seqItr));
			
			Element credentialsElem = XMLUtils.getFirstElementAtXPath(supplierCredentials, "./com:Credentials");
			supplierCredentials.insertBefore(sequence, credentialsElem);
			
			suppCredsListElem.appendChild(supplierCredentials);
//------------------------------------------------------------------------------------------------------------------------------------------------
			Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./cru1:SupplierID");
			suppIDElem.setTextContent(suppID);
			
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./cru1:Sequence");
			sequenceElem.setTextContent(String.valueOf(seqItr));
			
			Element otaAmendElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_CruiseBookRQ");
			CruisePriceProcessor.createPOS(ownerDoc, otaAmendElem);
			
			Element itineraryElem =	XMLUtils.getFirstElementAtXPath(otaAmendElem, "./ota:SailingInfo/ota:SelectedCategory/ota:TPA_Extensions/cru1:ItineraryID");
			itineraryElem.setTextContent(reqBodyJson.getString("itineraryID"));
			
			JSONObject reservationJson = reqBodyJson.getJSONObject("reservationID");
			Element reservationIDElem =	XMLUtils.getFirstElementAtXPath(otaAmendElem, "./ota:ReservationInfo/ota:ReservationID");
			reservationIDElem.setAttribute("ID", reservationJson.getString("id"));
			reservationIDElem.setAttribute("ID_Context", reservationJson.getString("id_context"));
			
			Element guestDtlsElem =	XMLUtils.getFirstElementAtXPath(otaAmendElem, "./ota:ReservationInfo/ota:GuestDetails");
			
//			JSONArray guestDtlsJsonArr = reqBodyJson.getJSONArray("guestDetails");
//			
//			for(int i=0;i<guestDtlsJsonArr.length();i++)
//			{
				JSONObject guestDtlsJson = reqBodyJson.getJSONObject("guestDetails");
				
				Element guestDtlElem = ownerDoc.createElementNS(NS_OTA, "GuestDetail");
				
				Element contactInfoElem = ownerDoc.createElementNS(NS_OTA, "ContactInfo");
				
				Element personNameElem = ownerDoc.createElementNS(NS_OTA, "PersonName");
				
				Element givenNameElem =	ownerDoc.createElementNS(NS_OTA, "GivenName");
				givenNameElem.setTextContent(guestDtlsJson.getString("firstName"));
				
				Element middleNameElem = ownerDoc.createElementNS(NS_OTA, "MiddleName");
				middleNameElem.setTextContent(guestDtlsJson.getString("middleName"));
				
				Element surNameElem = ownerDoc.createElementNS(NS_OTA, "Surname");
				surNameElem.setTextContent(guestDtlsJson.getString("lastName"));
				
				personNameElem.appendChild(surNameElem);
				personNameElem.insertBefore(middleNameElem, surNameElem);
				personNameElem.insertBefore(givenNameElem, middleNameElem);
//------------------------------------------------------------------------------------------------------------------------------------------------------------------------------				
				Element telephoneElem =	ownerDoc.createElementNS(NS_OTA, "Telephone");
				telephoneElem.setAttribute("PhoneNumber", guestDtlsJson.getString("telephoneNo"));
				telephoneElem.setAttribute("CountryAccessCode", guestDtlsJson.getString("countryAccessCode"));
//------------------------------------------------------------------------------------------------------------------------------------------------------------------------------				
				JSONObject addressJson = guestDtlsJson.getJSONObject("address");
				Element addressElem = ownerDoc.createElementNS(NS_OTA, "Address");
				
				Element addressLineElem = ownerDoc.createElementNS(NS_OTA, "AddressLine");
				addressLineElem.setTextContent(addressJson.getString("addressLine"));
				
				Element cityNameElem = ownerDoc.createElementNS(NS_OTA, "CityName");
				cityNameElem.setTextContent(addressJson.getString("cityName"));
				
				Element postalCodeElem = ownerDoc.createElementNS(NS_OTA, "PostalCode");
				postalCodeElem.setTextContent(addressJson.getString("postalCode"));
				
				Element stateProvElem = ownerDoc.createElementNS(NS_OTA,"StateProv");
				stateProvElem.setTextContent(addressJson.getString("stateProv"));
				
				JSONObject countryJson = addressJson.getJSONObject("country");
				Element countryNameElem = ownerDoc.createElementNS(NS_OTA, "CountryName");
				countryNameElem.setTextContent(countryJson.getString("name"));
				countryNameElem.setAttribute("Code", countryJson.getString("code"));
				
				addressElem.appendChild(countryNameElem);
				addressElem.insertBefore(stateProvElem, countryNameElem);
				addressElem.insertBefore(postalCodeElem, stateProvElem);
				addressElem.insertBefore(cityNameElem, postalCodeElem);
				addressElem.insertBefore(addressLineElem, cityNameElem);
				
				contactInfoElem.appendChild(addressElem);
				contactInfoElem.insertBefore(telephoneElem, addressElem);
				contactInfoElem.insertBefore(personNameElem, telephoneElem);
				
				guestDtlElem.appendChild(contactInfoElem);
				
				guestDtlsElem.appendChild(guestDtlElem);
//			}
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
		
		
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		try
		{
			//System.out.println(XMLTransformer.toString(reqElem));
			
			Element resElem = null;
	        //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), CruiseConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			 OperationsToDoProcessor.callOperationTodo(ToDoTaskName.CANCEL, ToDoTaskPriority.MEDIUM,ToDoTaskSubType.ORDER,reqBodyJson.optString("entityId"), reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
			if (resElem == null) {
	        	throw new Exception("Null response received from SI");
	        }
	        
	       
	        amendProducer.runProducer(1, kafkaMsgJson);
	        
	       // System.out.println(XMLTransformer.toString(resElem));
	        
	        kafkaMsgJson = new JSONObject();
	        
	        JSONObject resBodyJson = new JSONObject();
	        JSONObject cruiseAmendOptsJson = new JSONObject();
	        System.out.println(XMLTransformer.toString(resElem));
	        Element[] otaAmendAvailWrapperElems = XMLUtils.getElementsAtXPath(resElem, "./cru:ResponseBody/cru1:OTA_CruiseBookRSWrapper");
	        
	        for(Element otaAmendAvailWrapperElem : otaAmendAvailWrapperElems)
	        {
	        	//IF SI gives an error
	        	Element[] errorListElems = XMLUtils.getElementsAtXPath(otaAmendAvailWrapperElem, "./ota:OTA_CruiseBookRS/ota:Errors/ota:Error");
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
	        	
	        	getSupplierResponseJSON(otaAmendAvailWrapperElem, cruiseAmendOptsJson);
	        }
	        
	        resJson.put(JSON_PROP_RESBODY, cruiseAmendOptsJson);
	        kafkaMsgJson = resJson;
	        
	        amendProducer.runProducer(1, kafkaMsgJson);
		}
		catch(Exception e)
		{
			
		}
        
		return resJson.toString();
	}
	
	
	public static void getSupplierResponseJSON(Element otaAmendAvailWrapperElem,JSONObject cruiseAmendOptsJson)
	{
		String suppID = XMLUtils.getValueAtXPath(otaAmendAvailWrapperElem, "./cru1:SupplierID");
		cruiseAmendOptsJson.put(JSON_PROP_SUPPREF, suppID);
		
		Element otaBookElem = XMLUtils.getFirstElementAtXPath(otaAmendAvailWrapperElem, "./ota:OTA_CruiseBookRS");
		
		Element reservationElem = XMLUtils.getFirstElementAtXPath(otaBookElem, "./ota:ReservationID");
		if(reservationElem!=null)
		{
			cruiseAmendOptsJson.put("reservationID", getReservationJson(reservationElem));
		}
		
		String dueDate = XMLUtils.getValueAtXPath(otaBookElem, "./ota:BookingPayment/ota:PaymentSchedule/ota:Payment/@DueDate");
		cruiseAmendOptsJson.put("paymentDueDate", dueDate);
		
		Element[] guestPriceElems =	XMLUtils.getElementsAtXPath(otaBookElem, "./ota:BookingPayment/ota:GuestPrices/ota:GuestPrice");
		if(guestPriceElems!=null && guestPriceElems.length!=0)
		{
			cruiseAmendOptsJson.put("guestAddress", getGuestPricesJsonArr(guestPriceElems));
		}
		
		//for kafka msg
		cruiseAmendOptsJson.put(JSON_PROP_PROD, PRODUCT_CRUISE);
		cruiseAmendOptsJson.put("requestType", "amend");
		cruiseAmendOptsJson.put("type","updatePaxAddress");
		cruiseAmendOptsJson.put("entityName", "");
	}
	
	public static JSONArray getGuestPricesJsonArr(Element[] guestPriceElems)
	{
		JSONArray guestPriceJsonArr = new JSONArray();
		
		for(Element guestPriceElem : guestPriceElems)
		{
			JSONObject guestPriceJson = new JSONObject();
			
			Element addressElem = XMLUtils.getFirstElementAtXPath(guestPriceElem, "./ota:GuestName/ota:Address");
			
			String addressLine = XMLUtils.getValueAtXPath(addressElem, "./ota:AddressLine");
			guestPriceJson.put("addressLine", addressLine);
			
			String cityName = XMLUtils.getValueAtXPath(addressElem, "./ota:CityName");
			guestPriceJson.put("cityName", cityName);
			
			String postalCode = XMLUtils.getValueAtXPath(addressElem, "./ota:PostalCode");
			guestPriceJson.put("postalCode", postalCode);
			
			String stateProv = XMLUtils.getValueAtXPath(addressElem, "./ota:StateProv");
			guestPriceJson.put("stateProv", stateProv);
			
			guestPriceJsonArr.put(guestPriceJson);
		}
		return guestPriceJsonArr;
	}
	
	public static JSONObject getReservationJson(Element reservationElem)
	{
		JSONObject reservationJson = new JSONObject();
		
		String id = XMLUtils.getValueAtXPath(reservationElem, "./@ID");
		reservationJson.put("id", id);
		
		String bookedDate = XMLUtils.getValueAtXPath(reservationElem, "./@BookedDate");
		reservationJson.put("bookedDate", bookedDate);

		String offerDate = XMLUtils.getValueAtXPath(reservationElem, "./@OfferDate");
		reservationJson.put("offerDate", offerDate);
		
		return reservationJson;
	}
	
}
