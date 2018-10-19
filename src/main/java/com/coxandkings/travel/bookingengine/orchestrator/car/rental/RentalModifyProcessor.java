package com.coxandkings.travel.bookingengine.orchestrator.car.rental;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.OperationsShellConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.car.CarConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class RentalModifyProcessor implements CarConstants {

	private static final Logger logger = LogManager.getLogger(RentalModifyProcessor.class);

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException{
		
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null, modifyReq= null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		KafkaBookProducer bookProducer = null;
		
		try {
			opConfig = CarConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			
			bookProducer = new KafkaBookProducer();
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./car:OTA_VehModifyRQWrapper");
			reqBodyElem.removeChild(wrapperElem);

			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			RentalSearchProcessor.createHeader(reqHdrJson, reqElem);

			modifyReq = reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);

			JSONObject errorMsg = new JSONObject();
			String modifyType = modifyReq.optString(JSON_PROP_TYPE);
			Element suppWrapperElem = null;
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			XMLUtils.setValueAtXPath(suppWrapperElem, "./car:Sequence", "1");
			
			if (!isValidModifyType(modifyType)) {
				errorMsg.put("errorMessage", String.format("No operation available for modify type: '%s'", modifyType));
				return errorMsg.toString();
			}
			reqBodyElem.appendChild(suppWrapperElem);
			populateModifyWrapperElement(reqElem, ownerDoc, suppWrapperElem, usrCtx, reqBodyJson);
			
			sendAmendRQKafkaMessage(bookProducer, reqJson, usrCtx);
			
		}catch (Exception x) {
			
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
		try {
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), CarConfig.getHttpHeaders(), reqElem);
		
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			ToDoTaskSubType todoSubType=null;
			OperationsToDoProcessor.callOperationTodo(ToDoTaskName.CANCEL, ToDoTaskPriority.MEDIUM,todoSubType.ORDER, "", reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			
			JSONObject resBodyJson = new JSONObject();
			JSONObject carRentalInfo = new JSONObject();
			Element resWrapperElem = XMLUtils.getFirstElementAtXPath(resElem, "./cari:ResponseBody/car:OTA_VehModifyRSWrapper");

			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			
			if (XMLUtils.getFirstElementAtXPath(resWrapperElem, "./car:ErrorList") != null) {
				
				Element errorMessage = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./car:ErrorList/com:Error/com:ErrorCode");
				String errMsgStr = errorMessage.getTextContent().toString();
				if (CANCEL_SI_ERR.equalsIgnoreCase(errMsgStr)) {

					logger.error("This service is not supported. Kindly contact our operations team for support.");
					return getSIErrorResponse(resJson).toString();
				}
			}
			if (XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_VehModifyRS/ota:Errors") != null) {
				
				if(XMLUtils.getValueAtXPath(resWrapperElem, "./ota:OTA_VehModifyRS/ota:Errors/ota:Error").equals(ADDANCILLARY_SI_ERR)) 
					logger.error("The supplier rejected the operation. No Further Equipments can be added.Kindly contact our operations team for support.");
				else
					logger.error("The supplier rejected the operation. Kindly contact our operations team for support.");
				
				return getSIErrorResponse(resJson).toString();
			}
			carRentalInfo.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(resWrapperElem, "./car:SupplierID"));
			Element resBodyElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_VehModifyRS/ota:VehModifyRSCore");
			getSupplierBookResponseJSON(modifyReq, resBodyElem, carRentalInfo);
			
			resBodyJson.put(JSON_PROP_PROD, PROD_CATEG_SUBTYPE_CAR);
			resBodyJson.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_CAR);
			resBodyJson.put(JSON_PROP_BOOKID, reqBodyJson.getString(JSON_PROP_BOOKID));
			resBodyJson.put("requestType", reqBodyJson.getString("requestType"));
			resBodyJson.put("type", reqBodyJson.getString("type"));
			resBodyJson.put("entityId", reqBodyJson.getString("entityId"));
			resBodyJson.put("entityName", reqBodyJson.getString("entityName").isEmpty() ? "order" : reqBodyJson.getString("entityName"));
			resBodyJson.put(JSON_PROP_CARRENTALARR, carRentalInfo);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
			
			//TODO : Decide whether to apply commercials and charges here.
			sendAmendRSKafkaMessage(bookProducer, reqJson, resJson);
			
			return resJson.toString();
		} catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}

	private static void sendAmendRQKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, UserContext usrCtx) throws Exception {
		
		JSONObject kafkaMsgJson = new JSONObject(reqJson.toString());
		JSONObject reqHeaderJson = kafkaMsgJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBodyJson = kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY);
		
		reqBodyJson.put(JSON_PROP_PROD, PROD_CATEG_SUBTYPE_CAR);
		reqBodyJson.getJSONObject(JSON_PROP_REQBODY).put("operation", "amend");
		
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		bookProducer.runProducer(1, kafkaMsgJson);
		logger.trace(String.format("Car Amend Request Kafka Message: %s", kafkaMsgJson.toString()));
	}
	
	private static void sendAmendRSKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson,
			JSONObject resJson) throws Exception{
		
		JSONObject kafkaMsgJson = new JSONObject(resJson.toString());
		
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("operation", "amend");
		bookProducer.runProducer(1, kafkaMsgJson);
	}

	private static boolean isValidModifyType(String modifyTypeStr) {

		ModifyEnum modifyType = ModifyEnum.forString(modifyTypeStr);
		if(modifyType==null)
			return false;
		
		return true;
	}

	private static void getSupplierBookResponseJSON(JSONObject modifyReq, Element vehResCoreElem,
			JSONObject reservationJson) {

		Element reservationElem = XMLUtils.getFirstElementAtXPath(vehResCoreElem, "ota:VehReservation");

		Element vehSegCoreElem = XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentCore");
		Element vehSegInfoElem = XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentInfo");
		
		reservationJson.put("status", XMLUtils.getValueAtXPath(vehResCoreElem, "./@ModifyStatus"));
		reservationJson.put(JSON_PROP_TOTALPRICEINFO, RentalSearchProcessor.getPricingInfoJSON(vehSegCoreElem, vehSegInfoElem));
		// Element vehSegmentElem = XMLUtils.getFirstElementAtXPath(reservationElem,
		// "./ota:VehSegmentCore");
		// reservationJson.put(JSON_PROP_SUPPPRICEINFO,
		// CarSearchProcessor.getPricingInfoJSON(vehSegmentElem));

	}
	
	private static JSONArray getCustomerJSONArray(Element customerElem) {

		JSONArray customerJsonArr = new JSONArray();

		Element primaryElem = XMLUtils.getFirstElementAtXPath(customerElem, "./ota:Primary");
		customerJsonArr.put(getCustomerJSON(primaryElem, true));
		Element additionalElems[] = XMLUtils.getElementsAtXPath(customerElem, "./ota:Additional");
		for (Element additionalElem : additionalElems) {
			customerJsonArr.put(getCustomerJSON(additionalElem, false));
		}
		return customerJsonArr;
	}

	private static JSONObject getCustomerJSON(Element elem, Boolean isLead) {

		JSONObject customerJson = new JSONObject();

		customerJson.put("customerId", XMLUtils.getValueAtXPath(elem, "./ota:CustomerID/@ID"));
		customerJson.put("custLoyaltyMembershipID", XMLUtils.getValueAtXPath(elem, "./ota:CustLoyalty/@MembershipID"));
		customerJson.put("namePrefix", XMLUtils.getValueAtXPath(elem, "./ota:NamePrefix"));
		customerJson.put(JSON_PROP_TITLE, XMLUtils.getValueAtXPath(elem, "./ota:NameTitle"));
		customerJson.put(JSON_PROP_GENDER, XMLUtils.getValueAtXPath(elem, "./@Gender"));
		customerJson.put(JSON_PROP_ISLEAD, isLead);
		customerJson.put(JSON_PROP_FIRSTNAME, XMLUtils.getValueAtXPath(elem, "./ota:GivenName"));
		customerJson.put(JSON_PROP_SURNAME, XMLUtils.getValueAtXPath(elem, "./ota:Surname"));
		customerJson.put(JSON_PROP_AREACITYCODE, XMLUtils.getValueAtXPath(elem, "./ota:Address/ota:StateProv/@StateCode"));
		customerJson.put(JSON_PROP_MOBILENBR, XMLUtils.getValueAtXPath(elem, "./ota:Telephone/@PhoneNumber"));
		customerJson.put(JSON_PROP_EMAIL, XMLUtils.getValueAtXPath(elem, "./ota:Email"));
		String addressLine = XMLUtils.getValueAtXPath(elem, "./ota:Address/ota:AddressLine[1]");
		customerJson.put(JSON_PROP_ADDRLINE1, addressLine);
		addressLine = XMLUtils.getValueAtXPath(elem, "./ota:Address/ota:AddressLine[2]");
		customerJson.put(JSON_PROP_ADDRLINE2, addressLine);
		customerJson.put(JSON_PROP_CITY, XMLUtils.getValueAtXPath(elem, "./ota:Address/ota:CityName"));
		customerJson.put(JSON_PROP_ZIP, XMLUtils.getValueAtXPath(elem, "./ota:Address/ota:PostalCode"));
		customerJson.put(JSON_PROP_COUNTRY, XMLUtils.getValueAtXPath(elem, "./ota:Address/ota:CountryName"));
		customerJson.put(JSON_PROP_COUNTRYCODE, XMLUtils.getValueAtXPath(elem, "./ota:Address/ota:CountryName/@Code"));

		return customerJson;

	}

	private static void populateModifyWrapperElement(Element reqElem, Document ownerDoc, Element suppWrapperElem,
			UserContext usrCtx, JSONObject reqBodyJson) throws Exception {

		JSONObject modifyReq = reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);
		// TODO : Need To Change later
		Element sourceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehModifyRQ/ota:POS/ota:Source");
		sourceElem.setAttribute("ISOCurrency", "EUR");
		sourceElem.setAttribute("ISOCountry", "IE");

		String suppID = modifyReq.getString(JSON_PROP_SUPPREF);
		ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_RENTAL, suppID);
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

		Element vehModifyCoreElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehModifyRQ/ota:VehModifyRQCore");

		vehModifyCoreElem.setAttribute("Status", "Available");
		//TODO : Need to Check on How to set this value?
		vehModifyCoreElem.setAttribute("ModifyType", reqBodyJson.getString("modifyType"));

		String bookRefid = modifyReq.getString(JSON_PROP_RESERVATIONID);
		Element uniqIdElem = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
		uniqIdElem.setAttribute("ID", bookRefid);
		uniqIdElem.setAttribute("Type", "14");
		vehModifyCoreElem.appendChild(uniqIdElem);

	}


	protected static Element populateCustomerElementFromReqJSON(Document ownerDoc, JSONArray customerJsonArr, ModifyEnum amendType) {

		Element customerElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Customer");
		for (int i = 0; i < customerJsonArr.length(); i++) {

			JSONObject customerJson = customerJsonArr.getJSONObject(i);
			Element customerType = ownerDoc.createElementNS(Constants.NS_OTA,
					customerJson.optBoolean(JSON_PROP_ISLEAD) == true ? "ota:Primary" : "ota:Additional");

			customerElem.appendChild(customerType);
			String temp;
			if (!(temp = customerJson.optString(JSON_PROP_GENDER)).isEmpty())
				customerType.setAttribute("Gender", temp);
			Element personNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PersonName");

			Element surname = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Surname");
			surname.setTextContent(customerJson.optString(JSON_PROP_SURNAME));
			personNameElem.appendChild(surname);

			Element givenName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:GivenName");
			givenName.setTextContent(customerJson.optString(JSON_PROP_FIRSTNAME));
			personNameElem.insertBefore(givenName, surname);

			Element namePrefix = ownerDoc.createElementNS(Constants.NS_OTA, "ota:NamePrefix");
			namePrefix.setTextContent(customerJson.optString(JSON_PROP_TITLE));
			personNameElem.insertBefore(namePrefix, givenName);

			customerType.appendChild(personNameElem);
			if (amendType.equals(ModifyEnum.PIU)) {

				JSONObject contactDetails = customerJson.getJSONArray(JSON_PROP_CONTACTDTLS).getJSONObject(0);
				JSONObject addressDetails = customerJson.getJSONObject(JSON_PROP_ADDRDTLS);
				Element telephoneElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Telephone");
				Element addressElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Address");
				Element emailElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Email");
				customerType.appendChild(telephoneElem);
				customerType.appendChild(emailElem);
				customerType.appendChild(addressElem);

				emailElem.setTextContent(contactDetails.getString(JSON_PROP_EMAIL));

				if (!contactDetails.optString(JSON_PROP_AREACITYCODE).isEmpty())
					telephoneElem.setAttribute("AreaCityCode", contactDetails.getString(JSON_PROP_AREACITYCODE));
				if (!contactDetails.optString(JSON_PROP_MOBILENBR).isEmpty())
					telephoneElem.setAttribute("PhoneNumber", contactDetails.getString(JSON_PROP_MOBILENBR));

				Element cityNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CityName");
				cityNameElem.setTextContent(addressDetails.optString(JSON_PROP_CITY));
				addressElem.appendChild(cityNameElem);

				Element postalCodeElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PostalCode");
				postalCodeElem.setTextContent(addressDetails.optString(JSON_PROP_ZIP));
				addressElem.appendChild(postalCodeElem);

				Element stateProv = ownerDoc.createElementNS(NS_OTA, "ota:StateProv");
				// stateProv.setTextContent(addressDetails.getString("state"));
				stateProv.setAttribute("StateCode", addressDetails.getString(JSON_PROP_STATE));
				addressElem.appendChild(stateProv);

				Element countryNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CountryName");
				if (!addressDetails.optString(JSON_PROP_COUNTRY).isEmpty())
					countryNameElem.setAttribute("Code", addressDetails.getString(JSON_PROP_COUNTRY));
				addressElem.appendChild(countryNameElem);

				Element addressLineElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:AddressLine");
				addressLineElem.setTextContent(addressDetails.optString(JSON_PROP_ADDRLINE1));
				addressElem.insertBefore(addressLineElem, cityNameElem);
				if (!addressDetails.optString(JSON_PROP_ADDRLINE2).equals("")) {
					addressLineElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:AddressLine");
					addressLineElem.setTextContent(addressDetails.optString(JSON_PROP_ADDRLINE2));
					addressElem.insertBefore(addressLineElem, cityNameElem);
				}
			}
		}
		return customerElem;
	}
	
	public static JSONObject getEmptyResponse(JSONObject reqHdrJson) {
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, new JSONObject());
		return resJson;
	}

	private static JSONObject getSIErrorResponse(JSONObject resJson) {

		JSONObject errorMessage = new JSONObject();
		errorMessage.put("errorMessage", "This service is not supported. Kindly contact our operations team for support.");
		resJson.put(JSON_PROP_RESBODY, errorMessage);

		return resJson;
	}

}
