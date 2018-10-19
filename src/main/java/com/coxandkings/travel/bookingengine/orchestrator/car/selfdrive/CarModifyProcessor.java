package com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive;

import java.util.HashMap;
import java.util.Map;

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
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
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

public class CarModifyProcessor implements CarConstants {

	private static final Logger logger = LogManager.getLogger(CarModifyProcessor.class);

	public static String processV2(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException{
		
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
			CarSearchProcessor.createHeader(reqHdrJson, reqElem);

			JSONObject retrieveReq = new JSONObject(reqJson.toString());
			modifyReq = reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);

			JSONObject retrieveRes = new JSONObject(CarRetrieveProcessor.process(retrieveReq, usrCtx));
			JSONArray retrieveResArr = retrieveRes.getJSONObject(JSON_PROP_RESBODY).optJSONArray(JSON_PROP_CARRENTALARR);

			JSONObject errorMsg = new JSONObject();
			String modifyType = reqBodyJson.optString(JSON_PROP_TYPE);
			Element suppWrapperElem = null;
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			XMLUtils.setValueAtXPath(suppWrapperElem, "./car:Sequence", "1");
			
			if (!isValidModifyType(modifyType)) {
				errorMsg.put("errorMessage", String.format("No operation available for modify type: '%s'", modifyType));
				return errorMsg.toString();
			}
			reqBodyElem.appendChild(suppWrapperElem);

			populateModifyWrapperElement(reqElem, ownerDoc, suppWrapperElem, usrCtx, reqBodyJson, retrieveResArr.getJSONObject(0));
			
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
	          if((reqBodyJson.optString("requestType")!=null)&&(reqBodyJson.getString("requestType").equals("cancel"))) {
	        	  String type=reqBodyJson.getString(JSON_PROP_TYPE);
		            switch(type) {
		            case "ALL":todoSubType=ToDoTaskSubType.ORDER;break;
		            case "CANANC":todoSubType=ToDoTaskSubType.ORDER;break;
		            default:todoSubType=null;
		       
	          }
	          }
	          else if((reqBodyJson.optString("requestType")!=null)&&(reqBodyJson.getString("requestType").equals("amend"))){
	        	  String type=reqBodyJson.getString(JSON_PROP_TYPE);
		            switch(type){
		            
		            case "ADDANC":todoSubType=ToDoTaskSubType.ORDER;break;
		            case "PIU":todoSubType=ToDoTaskSubType.PASSENGER;break;
		            case "PNC":todoSubType=ToDoTaskSubType.PASSENGER;break;
		            default:todoSubType=null;
		            }  
	          }
	          OperationsToDoProcessor.callOperationTodo(ToDoTaskName.CANCEL, ToDoTaskPriority.MEDIUM,todoSubType, "", reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
	            
			
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			JSONObject resBodyJson = new JSONObject();
			JSONObject carRentalInfo = new JSONObject();
			Element resWrapperElem = XMLUtils.getFirstElementAtXPath(resElem, "./cari:ResponseBody/car:OTA_VehModifyRSWrapper");

			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			
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
			
			JSONArray vehicleAvailArr = new JSONArray();
			vehicleAvailArr.put(carRentalInfo);
			JSONArray carRentalArr = new JSONArray();
			carRentalArr.put(new JSONObject().put(JSON_PROP_VEHICLEAVAIL, vehicleAvailArr));
			
			resBodyJson.put(JSON_PROP_CARRENTALARR, carRentalArr);
			resBodyJson.put("requestType", reqBodyJson.getString("requestType"));
			resBodyJson.put("type", reqBodyJson.getString("type"));
			
			resBodyJson.put("entityId", reqBodyJson.optString("entityId"));
			resBodyJson.put("entityName", reqBodyJson.getString("entityName").isEmpty() ? "order" : reqBodyJson.getString("entityName"));
			resJson.put(JSON_PROP_RESBODY, resBodyJson);

			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
			Map<String,Integer> suppResToBRIIndex= new HashMap<String,Integer>();
			
			//Applying Commercials
			JSONObject resSupplierComJson =  SupplierCommercials.getSupplierCommercials(CommercialsOperation.Ammend, reqJson, resJson, suppResToBRIIndex);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierComJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s",
						resSupplierComJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}
			JSONObject resClientComJson = ClientCommercials.getClientCommercialsV2(reqJson, resJson, resSupplierComJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientComJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s",
						resClientComJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}
			CarSearchProcessor.calculatePricesV2(reqJson, resJson, resSupplierComJson, resClientComJson, suppResToBRIIndex, true, usrCtx, true);
			
			JSONObject vehicleAvail = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_CARRENTALARR).getJSONObject(0).getJSONArray(JSON_PROP_VEHICLEAVAIL).getJSONObject(0);
			resBodyJson.put(JSON_PROP_CARRENTALARR, vehicleAvail);
			resBodyJson.put(JSON_PROP_PROD, PROD_CATEG_SUBTYPE_CAR);
			resBodyJson.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_CAR);
			resBodyJson.put(JSON_PROP_BOOKID, reqBodyJson.getString(JSON_PROP_BOOKID));
			
			sendAmendRSKafkaMessage(bookProducer, reqJson, resJson);
			
			resBodyJson.remove("entityName");
			resBodyJson.remove("entityId");
			vehicleAvail.optJSONObject(JSON_PROP_TOTALPRICEINFO).remove(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			vehicleAvail.remove(JSON_PROP_SUPPPRICEINFO);
			
			return resJson.toString();
		} catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}

	private static void sendAmendRQKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, UserContext usrCtx) throws Exception {
		
		String modifyType = reqJson.getJSONObject(JSON_PROP_REQBODY).getString("type");
		String modifyTypeDesc = ModifyEnum.forString(modifyType).getDescription();
		
		JSONObject kafkaMsgJson = new JSONObject(reqJson.toString());
		JSONObject reqHeaderJson = kafkaMsgJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBodyJson = kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY);
		
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		
		reqBodyJson.put(JSON_PROP_PROD, PROD_CATEG_SUBTYPE_CAR);
		reqBodyJson.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_CAR);
		reqBodyJson.put("operation", "amend");
		reqBodyJson.put("type", modifyTypeDesc);
		
		bookProducer.runProducer(1, kafkaMsgJson);
		logger.trace(String.format("Car Amend Request Kafka Message: %s", kafkaMsgJson.toString()));
		
	}
	
	private static void sendAmendRSKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson,
			JSONObject resJson) throws Exception{
		
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject modifyReq = reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);
		
		String modifyType = reqBodyJson.getString("type");
		String modifyTypeDesc = ModifyEnum.forString(modifyType).getDescription();
		
		JSONObject kafkaMsgJson = new JSONObject(resJson.toString());
		JSONObject resBodyJson = kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY);
		
		resBodyJson.getJSONObject(JSON_PROP_CARRENTALARR).put(JSON_PROP_REFERENCES, modifyReq.optJSONArray(JSON_PROP_REFERENCES));
		resBodyJson.getJSONObject(JSON_PROP_CARRENTALARR).put(JSON_PROP_RESERVATIONID, modifyReq.getString(JSON_PROP_RESERVATIONID));
		resBodyJson.put("type", modifyTypeDesc);
		resBodyJson.put("operation", "amend");
		
		bookProducer.runProducer(1, kafkaMsgJson);
		logger.trace(String.format("Car Amend Response Kafka Message: %s", kafkaMsgJson.toString()));
	}

	private static boolean isValidModifyType(String modifyTypeStr) {
		
		ModifyEnum modifyType = ModifyEnum.forString(modifyTypeStr);
		if(modifyType == null) 
			return false;
		
		return true;
	}

	private static void getSupplierBookResponseJSON(JSONObject modifyReq, Element vehResCoreElem,
			JSONObject reservationJson) {

		Element reservationElem = XMLUtils.getFirstElementAtXPath(vehResCoreElem, "ota:VehReservation");

		Element vehSegCoreElem = XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentCore");
		Element vehSegInfoElem = XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentInfo");

		reservationJson.put("status", XMLUtils.getValueAtXPath(vehResCoreElem, "./@ModifyStatus"));
		reservationJson.put(JSON_PROP_TOTALPRICEINFO, CarSearchProcessor.getPricingInfoJSON(vehSegCoreElem, vehSegInfoElem));
		// Element vehSegmentElem = XMLUtils.getFirstElementAtXPath(reservationElem,
		// "./ota:VehSegmentCore");
		// reservationJson.put(JSON_PROP_SUPPPRICEINFO,
		// CarSearchProcessor.getPricingInfoJSON(vehSegmentElem));

	}
	
	private static JSONObject getVehSegmentJSON(Element vehSegmentElem) {

		JSONObject vehSegmentJson = new JSONObject();
		JSONArray confIdJsonArr = new JSONArray();
		Element confIdsElem[] = XMLUtils.getElementsAtXPath(vehSegmentElem, "./ota:ConfID");
		for (Element confIdElem : confIdsElem) {
			JSONObject confIdJson = new JSONObject();
			confIdJson.put("id", XMLUtils.getValueAtXPath(confIdElem, "./@ID"));
			confIdJson.put("type", XMLUtils.getValueAtXPath(confIdElem, "./@Type"));
			confIdJson.put("status", XMLUtils.getValueAtXPath(confIdElem, "./@Status"));
			confIdJson.put("url", XMLUtils.getValueAtXPath(confIdElem, "./@URL"));
			confIdJson.put("id_Context", XMLUtils.getValueAtXPath(confIdElem, "./@ID_Context"));
			confIdJsonArr.put(confIdJson);
		}

		vehSegmentJson.put("confID", confIdJsonArr);
		vehSegmentJson.put("vendorCode", XMLUtils.getValueAtXPath(vehSegmentElem, "./ota:Vendor/@Code"));
		vehSegmentJson.put("vendorCompanyShortName",
				XMLUtils.getValueAtXPath(vehSegmentElem, "./ota:Vendor/@CompanyShortName"));
		vehSegmentJson.put("vendorTravelSector",
				XMLUtils.getValueAtXPath(vehSegmentElem, "./ota:Vendor/@TravelSector"));
		vehSegmentJson.put("vendorCodeContext", XMLUtils.getValueAtXPath(vehSegmentElem, "./ota:Vendor/@CodeContext"));

		Element vehRentalCoreElem = XMLUtils.getFirstElementAtXPath(vehSegmentElem, "./ota:VehRentalCore");
		vehSegmentJson.put(JSON_PROP_PICKUPDATE, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./@PickUpDateTime"));
		vehSegmentJson.put(JSON_PROP_RETURNDATE, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./@ReturnDateTime"));

		String oneWayIndc = XMLUtils.getValueAtXPath(vehRentalCoreElem, "./@OneWayIndicator");

		vehSegmentJson.put(JSON_PROP_TRIPTYPE,
				!oneWayIndc.isEmpty() ? (oneWayIndc.equals("true") ? "OneWay" : "Return") : "");
		vehSegmentJson.put(JSON_PROP_PICKUPLOCCODE,
				XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:PickUpLocation/@LocationCode"));
		vehSegmentJson.put(JSON_PROP_RETURNLOCCODE,
				XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:ReturnLocation/@LocationCode"));
		vehSegmentJson.put(JSON_PROP_PICKUPLOCCODECONTXT,
				XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:PickUpLocation/@CodeContext"));
		vehSegmentJson.put(JSON_PROP_RETURNLOCCODECONTXT,
				XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:ReturnLocation/@CodeContext"));
		vehSegmentJson.put(JSON_PROP_PICKUPLOCNAME,
				XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:PickUpLocation/@Name"));
		vehSegmentJson.put(JSON_PROP_RETURNLOCNAME,
				XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:ReturnLocation/@Name"));
		vehSegmentJson.put(JSON_PROP_PICKUPLOCEXTCODE,
				XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:PickUpLocation/@ExtendedLocationCode"));
		vehSegmentJson.put(JSON_PROP_RETURNLOCEXTCODE,
				XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:ReturnLocation/@ExtendedLocationCode"));

		return vehSegmentJson;
	}

	private static JSONObject getRentalPaymentJSON(Element rentalPaymentAmtElem) {

		JSONObject rentalPaymentJson = new JSONObject();
		rentalPaymentJson.put("paymentType", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./@PaymentType"));
		rentalPaymentJson.put("type", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./@Type"));
		rentalPaymentJson.put("paymentCardCode",
				XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/@Code"));
		rentalPaymentJson.put("paymentCardExpiryDate",
				XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/@ExpireDate"));
		rentalPaymentJson.put("cardType",
				XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/ota:CardType"));
		rentalPaymentJson.put("cardHolderName",
				XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/ota:CardHolderName"));
		rentalPaymentJson.put("cardNumber",
				XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/ota:CardNumber"));
		rentalPaymentJson.put("amount", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentAmount/@Amount"));
		rentalPaymentJson.put("currencyCode",
				XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentAmount/@CurrencyCode"));
		rentalPaymentJson.put("seriesCode",
				XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/ota:SeriesCode/ota:PlainText"));

		return rentalPaymentJson;
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
		customerJson.put(JSON_PROP_AREACITYCODE,
				XMLUtils.getValueAtXPath(elem, "./ota:Address/ota:StateProv/@StateCode"));
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

	private static Element createRentalPaymentPrefElement(Document ownerDoc, JSONObject vehicleAvailJson,
			JSONObject suppTotalFare) {

		// String temp;
		JSONObject rentalPaymentPrefJson = vehicleAvailJson.getJSONObject("rentalPaymentPref");
		Element rentalPaymentPrefElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:RentalPaymentPref");
		rentalPaymentPrefElem.setAttribute("PaymentType", rentalPaymentPrefJson.optString("paymentType"));
		rentalPaymentPrefElem.setAttribute("Type", rentalPaymentPrefJson.optString("type"));

		/*
		 * Element paymentCardElem = ownerDoc.createElementNS(Constants.NS_OTA,
		 * "ota:PaymentCard"); if(!(temp =
		 * rentalPaymentPrefJson.optString("PaymentCardCode")).isEmpty())
		 * paymentCardElem.setAttribute("CardCode", temp); if(!(temp =
		 * rentalPaymentPrefJson.optString("PaymentCardExpiryDate")).isEmpty())
		 * paymentCardElem.setAttribute("ExpireDate", temp);
		 * 
		 * Element cardType = ownerDoc.createElementNS(Constants.NS_OTA,
		 * "ota:CardType");
		 * cardType.setTextContent(rentalPaymentPrefJson.optString("CardType"));
		 * paymentCardElem.appendChild(cardType); Element cardHolderName =
		 * ownerDoc.createElementNS(Constants.NS_OTA, "ota:CardHolderName");
		 * cardHolderName.setTextContent(rentalPaymentPrefJson.optString(
		 * "CardHolderName")); paymentCardElem.appendChild(cardHolderName); Element
		 * cardNumber = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CardNumber");
		 * cardNumber.setTextContent(rentalPaymentPrefJson.optString("CardNumber"));
		 * paymentCardElem.appendChild(cardNumber);
		 */

		// Payment Amount taken from Redis which was saved in price operation
		Element paymentAmountElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PaymentAmount");
		paymentAmountElem.setAttribute("Amount", CarBookProcessor.BigDecimaltoString(rentalPaymentPrefJson, JSON_PROP_AMOUNT));
		paymentAmountElem.setAttribute("CurrencyCode", rentalPaymentPrefJson.getString(JSON_PROP_CCYCODE));

		/*
		 * Element paymentAmountElem =ownerDoc.createElementNS(Constants.NS_OTA,
		 * "ota:PaymentAmount"); if(!(temp =
		 * rentalPaymentPrefJson.optString("Amount")).isEmpty())
		 * paymentAmountElem.setAttribute("Amount", temp); if(!(temp =
		 * rentalPaymentPrefJson.optString("CurrencyCode")).isEmpty())
		 * paymentAmountElem.setAttribute("CurrencyCode", temp);
		 */

		// rentalPaymentPrefElem.appendChild(paymentCardElem);
		rentalPaymentPrefElem.appendChild(paymentAmountElem);
		return rentalPaymentPrefElem;
	}

	private static void populateModifyWrapperElement(Element reqElem, Document ownerDoc, Element suppWrapperElem,
			UserContext usrCtx, JSONObject reqBodyJson, JSONObject retrieveRes) throws Exception {

		String modifyTypeStr = reqBodyJson.optString(JSON_PROP_TYPE);
		ModifyEnum modifyType = ModifyEnum.forString(modifyTypeStr);
		
		JSONObject modifyReq = reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);
		// TODO : Need To Change later
		Element sourceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehModifyRQ/ota:POS/ota:Source");
		sourceElem.setAttribute("ISOCurrency", "EUR");
		sourceElem.setAttribute("ISOCountry", "IE");

		String suppID = modifyReq.getString(JSON_PROP_SUPPREF);
		ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_CAR,
				suppID);
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
		Element vehModifyInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehModifyRQ/ota:VehModifyRQInfo");

		vehModifyCoreElem.setAttribute("Status", "Available");
		//TODO : Need to Check on How to set this value?
		vehModifyCoreElem.setAttribute("ModifyType", reqBodyJson.optString("modifyType").isEmpty() ? "Modify" : reqBodyJson.getString("modifyType"));

		String bookRefid = modifyReq.getString(JSON_PROP_RESERVATIONID);

		Element uniqIdElem = ownerDoc.createElementNS(NS_OTA, "ota:UniqueID");
		uniqIdElem.setAttribute("ID", bookRefid);
		uniqIdElem.setAttribute("Type", "14");
		vehModifyCoreElem.appendChild(uniqIdElem);

		Element vehRentalElem = CarSearchProcessor.getVehRentalCoreElement(ownerDoc, retrieveRes);
		vehModifyCoreElem.appendChild(vehRentalElem);
		
		Element customerElem = null;
		JSONArray customerJsonArr = null;
		
		//if paxAmend, populate from ModifyReq Json
		customerJsonArr = modifyReq.optJSONArray(JSON_PROP_PAXDETAILS);
		
		//First Name and Last Name of Primary Driver is Mandatory for Modify operation.
		if (customerJsonArr != null && customerJsonArr.length() != 0) {
			customerElem = populateCustomerElementFromReqJSON(ownerDoc, customerJsonArr, modifyType);
			vehModifyCoreElem.appendChild(customerElem);
		}
		
		Element vendorPrefElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:VendorPref");
		vendorPrefElem.setAttribute("Code", retrieveRes.getString("vendorPrefCode"));
		vehModifyCoreElem.appendChild(vendorPrefElem);

		String temp;
		//Needs to be Removed. This operation will not be supported
/*		if (modifyType.equals(UPGRADECAR)) {
			JSONObject vehPrefJson = modifyReq.getJSONObject(JSON_PROP_VEHICLEINFO);

			Element VehPrefElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehPref");
			if (!(temp = vehPrefJson.optString(JSON_PROP_CODECONTEXT)).isEmpty())
				VehPrefElem.setAttribute("CodeContext", temp);
			if (!(temp = vehPrefJson.optString("airConditionInd")).isEmpty())
				VehPrefElem.setAttribute("AirConditionInd", temp);
			if (!(temp = vehPrefJson.optString("transmissionType")).isEmpty())
				VehPrefElem.setAttribute("TransmissionType", temp);
			if (!(temp = vehPrefJson.optString("driveType")).isEmpty())
				VehPrefElem.setAttribute("DriveType", temp);
			if (!(temp = vehPrefJson.optString("fuelType")).isEmpty())
				VehPrefElem.setAttribute("FuelType", temp);
			if (!(temp = vehPrefJson.optString("vehicleQty")).isEmpty())
				VehPrefElem.setAttribute("VehicleQty", temp);
			if (!(temp = vehPrefJson.optString("code")).isEmpty())
				VehPrefElem.setAttribute("Code", temp);

			Element vehTypeElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehType");
			vehTypeElem.setAttribute("VehicleCategory", vehPrefJson.optString(JSON_PROP_VEHICLECATEGORY));
			VehPrefElem.appendChild(vehTypeElem);

			Element vehClassElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehClass");
			vehClassElem.setAttribute("Size", vehPrefJson.optString("vehicleClassSize"));
			VehPrefElem.appendChild(vehClassElem);

			Element vehMakeModelElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehMakeModel");
			if (!vehPrefJson.optString(JSON_PROP_VEHMAKEMODELNAME).isEmpty())
				vehMakeModelElem.setAttribute("Name", vehPrefJson.optString(JSON_PROP_VEHMAKEMODELNAME));
			vehMakeModelElem.setAttribute("Code", vehPrefJson.optString(JSON_PROP_VEHMAKEMODELCODE));
			VehPrefElem.appendChild(vehMakeModelElem);

			vehModifyCoreElem.appendChild(VehPrefElem);
		}*/
		
		//Mandatory for Modify Request
		Element driverAge = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DriverType");
		driverAge.setAttribute("Age", CARRENTAL_DRIVER_AGE);
		vehModifyCoreElem.appendChild(driverAge);

		JSONArray rateDistJsonArr = modifyReq.optJSONArray(JSON_PROP_RATEDISTANCE);
		if (rateDistJsonArr != null) {
			Element rateDistElem = null;
			for (int i = 0; i < rateDistJsonArr.length(); i++) {
				JSONObject rateDistJson = rateDistJsonArr.getJSONObject(i);
				rateDistElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:RateDistance");
				rateDistElem.setAttribute("Quantity", rateDistJson.optString(JSON_PROP_QTY));
				rateDistElem.setAttribute("DistUnitName", rateDistJson.optString("distUnitName"));
				rateDistElem.setAttribute("VehiclePeriodUnitName", rateDistJson.optString("vehiclePeriodUnitName"));

				vehModifyCoreElem.appendChild(rateDistElem);
			}
		}

		JSONObject rateQualifier = modifyReq.optJSONObject(JSON_PROP_RATEQUALIFIER);
		if (rateQualifier != null) {
			Element rateQualifierElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:RateQualifier");
			rateQualifierElem.setAttribute("RateQualifier", rateQualifier.getString(JSON_PROP_RATEQUALIFIER));
			vehModifyCoreElem.appendChild(rateQualifierElem);
		}

		// Ancillary includes Equipments, Additional Drivers and Coverages.
		if (modifyType.equals(ModifyEnum.ADDANC)) {
			// Additinal Driver to be sent in <SpecialEquipPref> tag
			JSONArray splEquipsArr = modifyReq.optJSONArray(JSON_PROP_SPLEQUIPS);
			if (splEquipsArr != null && splEquipsArr.length() != 0) {

				Element specialEquipPrefs = ownerDoc.createElementNS(Constants.NS_OTA, "ota:SpecialEquipPrefs");
				Element specialEquipPref = null;
				for (int i = 0; i < splEquipsArr.length(); i++) {
					JSONObject splEquips = splEquipsArr.getJSONObject(i);
					specialEquipPref = ownerDoc.createElementNS(Constants.NS_OTA, "ota:SpecialEquipPref");
					specialEquipPref.setAttribute("EquipType", splEquips.optString(JSON_PROP_EQUIPTYPE));
					specialEquipPref.setAttribute("Quantity", String.valueOf(splEquips.optInt(JSON_PROP_QTY, 1)));
					specialEquipPref.setAttribute("Action", "Add");
					specialEquipPrefs.appendChild(specialEquipPref);
				}
				vehModifyCoreElem.appendChild(specialEquipPrefs);
			}

			// TODO : When adding Coverages Element, SI Not giving response. Check with SI.
			/*
			 * JSONArray pricedCovrgsArr = modifyReq.optJSONArray(JSON_PROP_PRICEDCOVRGS);
			 * if(pricedCovrgsArr!=null && pricedCovrgsArr.length()!=0) { Element
			 * pricedCovrgsElem = ownerDoc.createElementNS(Constants.NS_OTA,
			 * "ota:CoveragePrefs"); for(int i=0;i<pricedCovrgsArr.length();i++) {
			 * JSONObject pricedCovrgJson = pricedCovrgsArr.optJSONObject(i); Element
			 * pricedCovrgElem = ownerDoc.createElementNS(Constants.NS_OTA,
			 * "ota:CoveragePref"); //TODO: Better Way to handle this
			 * pricedCovrgElem.setAttribute("CoverageType",
			 * pricedCovrgJson.getString(JSON_PROP_COVERAGETYPE));
			 * pricedCovrgsElem.appendChild(pricedCovrgElem); }
			 * vehModifyInfoElem.appendChild(pricedCovrgsElem); }
			 */
		}

		if (modifyType.equals(ModifyEnum.CANANC)) {

			JSONArray retsplEquipArr = retrieveRes.getJSONObject(JSON_PROP_TOTALPRICEINFO)
					.getJSONObject(JSON_PROP_TOTALFARE).getJSONObject(JSON_PROP_SPLEQUIPS)
					.optJSONArray(JSON_PROP_SPLEQUIP);
			JSONArray splEquipsArr = modifyReq.optJSONArray(JSON_PROP_SPLEQUIPS);
			if (splEquipsArr != null && splEquipsArr.length() != 0) {

				Element specialEquipPrefs = ownerDoc.createElementNS(Constants.NS_OTA, "ota:SpecialEquipPrefs");
				Element specialEquipPref = null;
				for (int i = 0; i < splEquipsArr.length(); i++) {
					JSONObject splEquips = splEquipsArr.getJSONObject(i);
					specialEquipPref = ownerDoc.createElementNS(Constants.NS_OTA, "ota:SpecialEquipPref");
					specialEquipPref.setAttribute("EquipType", splEquips.optString(JSON_PROP_EQUIPTYPE));
					Boolean isPresent = false;
					String isRequired = "";
					Integer quantity = splEquips.optInt(JSON_PROP_QTY);
					// if quantity not provided by WEM ,Cancel the equipment.
					if (quantity == 0) {
						specialEquipPref.setAttribute("Action", "Delete");
					}
					for (int j = 0; j < retsplEquipArr.length(); j++) {
						JSONObject retsplEquip = retsplEquipArr.getJSONObject(j);
						String equipType = retsplEquip.getString(JSON_PROP_EQUIPTYPE);
						if (equipType.equals(splEquips.optString(JSON_PROP_EQUIPTYPE))) {
							isPresent = true;
							isRequired = retsplEquip.optString(JSON_PROP_ISREQUIRED);
							if ("true".equals(isRequired)) {
								logger.info(
										String.format("Cannot remove EquipmentType %s as Required is true", equipType));
								break;
							}
							quantity = retsplEquip.getInt(JSON_PROP_QTY) - quantity;
						}
					}
					if (isPresent == false || "true".equals(isRequired)) {
						logger.info(String.format("Cannot remove EquipmentType %s as its not already added",
								splEquips.optString(JSON_PROP_EQUIPTYPE)));
						continue;
					}
					if (quantity <= 0) {
						specialEquipPref.setAttribute("Action", "Delete");
					} else {
						specialEquipPref.setAttribute("Quantity", quantity.toString());
					}
					specialEquipPrefs.appendChild(specialEquipPref);
				}
				if (XMLUtils.getElementsAtXPath(specialEquipPrefs, "./ota:SpecialEquipPref").length != 0)
					vehModifyCoreElem.appendChild(specialEquipPrefs);
			}

			// TODO : When adding Coverages Element, SI Not giving response.
			/*
			 * JSONArray pricedCovrgsArr = modifyReq.optJSONArray(JSON_PROP_PRICEDCOVRGS);
			 * if(pricedCovrgsArr!=null && pricedCovrgsArr.length()!=0) { Element
			 * pricedCovrgsElem = ownerDoc.createElementNS(Constants.NS_OTA,
			 * "ota:CoveragePrefs"); for(int i=0;i<pricedCovrgsArr.length();i++) {
			 * JSONObject pricedCovrgJson = pricedCovrgsArr.optJSONObject(i); Element
			 * pricedCovrgElem = ownerDoc.createElementNS(Constants.NS_OTA,
			 * "ota:CoveragePref"); //TODO: Better Way to handle this
			 * pricedCovrgElem.setAttribute("CoverageType",
			 * pricedCovrgJson.getString(JSON_PROP_COVERAGETYPE));
			 * pricedCovrgsElem.appendChild(pricedCovrgElem); }
			 * vehModifyInfoElem.appendChild(pricedCovrgsElem); }
			 */
		}
	}

	protected static Element populateCustomerElementFromReqJSON(Document ownerDoc, JSONArray customerJsonArr, ModifyEnum modifyType) {

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
			if (modifyType.equals(ModifyEnum.PIU)) {

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
		errorMessage.put("errorMessage",
				"This service is not supported. Kindly contact our operations team for support.");

		resJson.put(JSON_PROP_RESBODY, errorMessage);

		return resJson;
	}

}
