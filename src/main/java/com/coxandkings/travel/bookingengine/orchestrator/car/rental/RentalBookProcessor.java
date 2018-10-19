package com.coxandkings.travel.bookingengine.orchestrator.car.rental;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.car.CarConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.orchestrator.car.rental.enums.TripIndicator;
import com.coxandkings.travel.bookingengine.orchestrator.car.rental.enums.Unit;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class RentalBookProcessor implements CarConstants{
	
	private static final Logger logger = LogManager.getLogger(RentalBookProcessor.class);

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException{
		
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		KafkaBookProducer bookProducer = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		
		try {
			opConfig = CarConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			bookProducer = new KafkaBookProducer();
			
			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
			
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			
			reqElem = createSIRequest(reqHdrJson, reqBodyJson, opConfig, usrCtx);
	        sendPreBookingKafkaMessage(bookProducer, reqJson, usrCtx);
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		try {
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), CarConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
		
	        
	        int sequence = 0;
			String sequence_str;
            JSONArray reservationJsonArr = new JSONArray();
            Element[] resWrapperElems = XMLUtils.getElementsAtXPath(resElem,"./cari:ResponseBody/car:OTA_VehResRSWrapper");
			for (Element resWrapperElem : resWrapperElems) {
				
				JSONObject reservationJson = new JSONObject();
				reservationJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(resWrapperElem, "./car:SupplierID"));
				Element vehResCoreElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_VehResRS/ota:VehResRSCore");
				getSupplierBookResponseJSON(vehResCoreElem, reservationJson);
				sequence = (sequence_str = XMLUtils.getValueAtXPath(resWrapperElem, "./car:Sequence")).isEmpty()? sequence: Integer.parseInt(sequence_str);
				reservationJsonArr.put(sequence, reservationJson);
			
			}
			JSONObject resBodyJson = new JSONObject();
			resBodyJson.put("reservation", reservationJsonArr);
			resBodyJson.put(JSON_PROP_PROD, PROD_CATEG_SUBTYPE_CAR);
			resBodyJson.put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_RENTAL);
			resBodyJson.put(JSON_PROP_BOOKID, reqBodyJson.getString(JSON_PROP_BOOKID));
			
			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			
			sendPostBookingKafkaMessage(bookProducer, reqJson, resJson);
			//Removing SupplierPrice Info from response
			JSONArray reservationArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray("reservation");
			for(Object reservation : reservationArr) 
				((JSONObject) reservation).remove(JSON_PROP_SUPPPRICEINFO);
			return resJson.toString();
			
		}catch(Exception x) {
			logger.warn(String.format("An exception occured when booking Car"), x);
			return "{\"status\": \"ERROR\"}";
		}
	}

	//private static Element createSIRequest(JSONObject reqHdrJson, JSONObject reqBodyJson, OperationConfig opConfig, UserContext usrCtx)
	private static Element createSIRequest(JSONObject reqHdrJson, JSONObject reqBodyJson, ServiceConfig opConfig, UserContext usrCtx)
			throws Exception {
		
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();

		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./car:OTA_VehResRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		
		RentalSearchProcessor.createHeader(reqHdrJson, reqElem);
		
		String clientMarket = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		String clientCcyCode = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);

		Map<String, String> reprcSuppFaresMap = null;
		String redisKey = null;
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool()) {
			redisKey = String.format("%s%c%s", reqHdrJson.optString(JSON_PROP_SESSIONID), KEYSEPARATOR,
					PROD_CATEG_SUBTYPE_RENTAL);
			reprcSuppFaresMap = redisConn.hgetAll(redisKey);
		}
		if (reprcSuppFaresMap == null) {
			throw new Exception(String.format("Reprice context not found,for %s", redisKey));
		}
		
		JSONArray carRentalInfoArr = reqBodyJson.getJSONArray(JSON_PROP_CARRENTALARR);
		for (int y = 0; y < carRentalInfoArr.length(); y++) {
			JSONObject bookReq = carRentalInfoArr.getJSONObject(y);
			Element suppWrapperElem = null;
			JSONObject totalPricingInfo = bookReq.getJSONObject(JSON_PROP_TOTALPRICEINFO);
			String vehicleKey = RentalSearchProcessor.getRedisKeyForVehicleAvail(bookReq);
			//Appending Commercials and Fares in KafkaBookReq For Database Population
			String suppPriceBookInfo = reprcSuppFaresMap.get(vehicleKey);
			if(suppPriceBookInfo==null) {
				throw new Exception(String.format("SupplierPriceInfo not found for Vehicle %s ", vehicleKey)); 
			}
			JSONObject suppPriceBookInfoJson = new JSONObject(suppPriceBookInfo);
			JSONObject suppPricingInfo = suppPriceBookInfoJson.getJSONObject(JSON_PROP_SUPPPRICEINFO);
			String suppCcy = suppPricingInfo.getJSONObject(JSON_PROP_TOTALFARE).getString(JSON_PROP_CCYCODE);
			bookReq.put(JSON_PROP_SUPPPRICEINFO, suppPricingInfo);
			JSONArray clientCommercialItinTotalInfoArr = suppPriceBookInfoJson.getJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			totalPricingInfo.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercialItinTotalInfoArr);
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			XMLUtils.setValueAtXPath(suppWrapperElem, "./car:Sequence", String.valueOf(y));
			reqBodyElem.appendChild(suppWrapperElem);
			//Appending ROE in Kafka Req.
			bookReq.put("roe", RedisRoEData.getRateOfExchange(suppCcy, clientCcyCode, clientMarket));
			populateWrapperElement(reqElem, ownerDoc, suppWrapperElem, usrCtx, bookReq, suppPricingInfo.getJSONObject(JSON_PROP_TOTALFARE));
		}
		return reqElem;
	}

	private static void getSupplierBookResponseJSON(Element vehResCoreElem, JSONObject reservationJson) {
		
		Element reservationElem = XMLUtils.getFirstElementAtXPath(vehResCoreElem, "ota:VehReservation");
		
		/*reservationJson.put(JSON_PROP_CUSTOMER, getCustomerJSONArray(XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:Customer")));
		Element rateDistElems[] = XMLUtils.getElementsAtXPath(reservationElem, "./ota:VehSegmentCore/ota:RentalRate/ota:RateDistance");
		reservationJson.put(JSON_PROP_RATEDISTANCE, CarSearchProcessor.getRateDistanceJSON(rateDistElems));
		Element rateQualifierElem = XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentCore/ota:RentalRate/ota:RateQualifier");
		reservationJson.put(JSON_PROP_RATEQUALIFIER, CarSearchProcessor.getRateQualifierJSON(rateQualifierElem));
		Element referenceElem = XMLUtils.getFirstElementAtXPath(reservationElem, "/ota:VehSegmentCore/ota:Reference");
		reservationJson.put(JSON_PROP_REFERENCE, CarSearchProcessor.getReferenceJSON(referenceElem));
		Element locationDetailsElem = XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentInfo/ota:LocationDetails");
		reservationJson.put(JSON_PROP_LOCATIONDETAIL, CarSearchProcessor.getLocationDetailsJSON(locationDetailsElem));
		Element rentalPaymentAmtElem = XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentInfo/ota:RentalPaymentAmount");
		reservationJson.put("RentalPaymentPref", getRentalPaymentJSON(rentalPaymentAmtElem));
		Element totalChrgElem = XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentInfo/ota:TotalCharge");
		JSONObject totalChrgJson = new JSONObject();
		totalChrgJson.put(JSON_PROP_ESTIMATEDTOTALAMOUNT, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(totalChrgElem, "./@EstimatedTotalAmount"),0));
		totalChrgJson.put(JSON_PROP_RATETOTALAMOUNT, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(totalChrgElem, "./@RateTotalAmount"),0));
		totalChrgJson.put(JSON_PROP_CURRENCYCODE, XMLUtils.getValueAtXPath(totalChrgElem, "./@CurrencyCode"));
		reservationJson.put(JSON_PROP_TOTALCHARGE, totalChrgJson);
		*/
//		reservationJson.put("VehSegment", getVehSegmentJSON(XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentCore")));
		
		Element vehSegCoreElem = XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentCore");
		Element vehSegInfoElem =  XMLUtils.getFirstElementAtXPath(reservationElem, "./ota:VehSegmentInfo");
		setSuppBookReferences(vehSegCoreElem, reservationJson);
		
		reservationJson.put(JSON_PROP_SUPPPRICEINFO, RentalSearchProcessor.getPricingInfoJSON(vehSegCoreElem, vehSegInfoElem));
	}
	
	private static void setSuppBookReferences(Element vehSegCoreElem, JSONObject reservationJson) {
		
		JSONArray referencesArr = new JSONArray();
		reservationJson.put(JSON_PROP_RESERVATIONID, XMLUtils.getValueAtXPath(vehSegCoreElem, "./ota:ConfID[@Type='14']/@ID"));
		Element confIdsElem[] = XMLUtils.getElementsAtXPath(vehSegCoreElem, "./ota:ConfID");
		for(Element confIdElem : confIdsElem) {
			if("14".equals(XMLUtils.getValueAtXPath(confIdElem, "./@Type"))==false) {
				referencesArr.put(XMLUtils.getValueAtXPath(confIdElem, "./@ID"));
			}
		}
		reservationJson.put(JSON_PROP_REFERENCES, referencesArr);
	}
	
	private static JSONObject getVehSegmentJSON(Element vehSegmentElem) {
		
		JSONObject vehSegmentJson= new JSONObject();
		JSONArray confIdJsonArr = new JSONArray();
		Element confIdsElem[] = XMLUtils.getElementsAtXPath(vehSegmentElem, "./ota:ConfID");
		for(Element confIdElem:confIdsElem) {
			JSONObject confIdJson = new JSONObject();
			confIdJson.put("Id", XMLUtils.getValueAtXPath(confIdElem, "./@ID"));
			confIdJson.put("Type", XMLUtils.getValueAtXPath(confIdElem, "./@Type"));
			confIdJson.put("Status", XMLUtils.getValueAtXPath(confIdElem, "./@Status"));
			confIdJson.put("Url", XMLUtils.getValueAtXPath(confIdElem, "./@URL"));
			confIdJson.put("ID_Context", XMLUtils.getValueAtXPath(confIdElem, "./@ID_Context"));
			confIdJsonArr.put(confIdJson);
		}
		
		vehSegmentJson.put("ConfID", confIdJsonArr);
		vehSegmentJson.put("VendorCode", XMLUtils.getValueAtXPath(vehSegmentElem, "./ota:Vendor/@Code"));
		vehSegmentJson.put("VendorCompanyShortName", XMLUtils.getValueAtXPath(vehSegmentElem, "./ota:Vendor/@CompanyShortName"));
		vehSegmentJson.put("VendorTravelSector", XMLUtils.getValueAtXPath(vehSegmentElem, "./ota:Vendor/@TravelSector"));
		vehSegmentJson.put("VendorCodeContext", XMLUtils.getValueAtXPath(vehSegmentElem, "./ota:Vendor/@CodeContext"));
		
		Element vehRentalCoreElem = XMLUtils.getFirstElementAtXPath(vehSegmentElem, "./ota:VehRentalCore");
		vehSegmentJson.put(JSON_PROP_PICKUPDATE, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./@PickUpDateTime"));
		vehSegmentJson.put(JSON_PROP_RETURNDATE, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./@ReturnDateTime"));
		//TODO : Put OneWayIndicator as a Boolean Value
		String oneWayIndc = XMLUtils.getValueAtXPath(vehRentalCoreElem, "./@OneWayIndicator");
		
		vehSegmentJson.put(JSON_PROP_TRIPTYPE, !oneWayIndc.isEmpty() ? (oneWayIndc.equals("true") ? "OneWay" : "Return") : "");
		vehSegmentJson.put(JSON_PROP_PICKUPLOCCODE, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:PickUpLocation/@LocationCode"));
		vehSegmentJson.put(JSON_PROP_RETURNLOCCODE, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:ReturnLocation/@LocationCode"));
		vehSegmentJson.put(JSON_PROP_PICKUPLOCCODECONTXT, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:PickUpLocation/@CodeContext"));
		vehSegmentJson.put(JSON_PROP_RETURNLOCCODECONTXT, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:ReturnLocation/@CodeContext"));
		vehSegmentJson.put(JSON_PROP_PICKUPLOCNAME, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:PickUpLocation/@Name"));
		vehSegmentJson.put(JSON_PROP_RETURNLOCNAME, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:ReturnLocation/@Name"));
		vehSegmentJson.put(JSON_PROP_PICKUPLOCEXTCODE, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:PickUpLocation/@ExtendedLocationCode"));
		vehSegmentJson.put(JSON_PROP_RETURNLOCEXTCODE, XMLUtils.getValueAtXPath(vehRentalCoreElem, "./ota:ReturnLocation/@ExtendedLocationCode"));
		
		return vehSegmentJson;
	}
	
	private static JSONObject getRentalPaymentJSON(Element rentalPaymentAmtElem) {
		
		JSONObject rentalPaymentJson = new JSONObject();
		rentalPaymentJson.put("paymentType", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./@PaymentType"));
		rentalPaymentJson.put("type", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./@Type"));
		rentalPaymentJson.put("paymentCardCode", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/@Code"));
		rentalPaymentJson.put("paymentCardExpiryDate", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/@ExpireDate"));
		rentalPaymentJson.put("cardType", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/ota:CardType"));
		rentalPaymentJson.put("cardHolderName", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/ota:CardHolderName"));
		rentalPaymentJson.put("cardNumber", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/ota:CardNumber"));
		rentalPaymentJson.put("amount", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentAmount/@Amount"));
		rentalPaymentJson.put("currencyCode", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentAmount/@CurrencyCode"));
		rentalPaymentJson.put("seriesCode", XMLUtils.getValueAtXPath(rentalPaymentAmtElem, "./ota:PaymentCard/ota:SeriesCode/ota:PlainText"));
		
		return rentalPaymentJson;
	}
	
	private static JSONArray getCustomerJSONArray(Element customerElem) {
		
		JSONArray customerJsonArr = new JSONArray();
		
		Element primaryElem = XMLUtils.getFirstElementAtXPath(customerElem, "./ota:Primary");	
		customerJsonArr.put(getCustomerJSON(primaryElem, true));
		Element additionalElems[] = XMLUtils.getElementsAtXPath(customerElem, "./ota:Additional");
		for(Element additionalElem : additionalElems) {
			customerJsonArr.put(getCustomerJSON(additionalElem, false));
		}
		return customerJsonArr;
	}
	
	private static JSONObject getCustomerJSON(Element elem, Boolean isLead) {
		
		JSONObject customerJson = new JSONObject();
		
		customerJson.put("customerId", XMLUtils.getValueAtXPath(elem, "./ota:CustomerID/@ID"));
		customerJson.put("custLoyaltyMembershipID", XMLUtils.getValueAtXPath(elem, "./ota:CustLoyalty/@MembershipID"));
		customerJson.put(JSON_PROP_TITLE, XMLUtils.getValueAtXPath(elem, "./ota:NamePrefix"));
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
	
	private static void populateWrapperElement(Element reqElem, Document ownerDoc, Element suppWrapperElem, UserContext usrCtx, 
			JSONObject vehicleAvailJson, JSONObject suppTotalFare) throws Exception {
		
		String suppID = vehicleAvailJson.getString(JSON_PROP_SUPPREF);
		
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
		
		 Element vehResCoreElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehResRQ/ota:VehResRQCore");
		 Element vehResInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehResRQ/ota:VehResRQInfo");
		 
		 Element vehRentalElem =  RentalSearchProcessor.getVehRentalCoreElement(ownerDoc,vehicleAvailJson);
		 vehResCoreElem.appendChild(vehRentalElem);
		 
		 JSONArray customerJsonArr = vehicleAvailJson.optJSONArray(JSON_PROP_PAXDETAILS);
		 if(customerJsonArr!=null && customerJsonArr.length()!=0) {
			 
			 Element customerElem =  RentalSearchProcessor.populateCustomerElement(ownerDoc, customerJsonArr);
			 vehResCoreElem.appendChild(customerElem);
		 }
		 
		 JSONObject vehPrefJson = vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO);
		 TripIndicator tripIndc = TripIndicator.forString(vehPrefJson.getString(JSON_PROP_CODECONTEXT));
		 
		 Element VehPrefElem = XMLUtils.getFirstElementAtXPath(vehResCoreElem, "./ota:VehPref");
		 String temp;
		 
		 VehPrefElem.setAttribute("CodeContext", tripIndc!=null ? tripIndc.toString() : "");
		 if(!(temp = vehPrefJson.optString("airConditionInd")).isEmpty())
			 VehPrefElem.setAttribute("AirConditionInd", temp);
		 if(!(temp = vehPrefJson.optString("transmissionType")).isEmpty())
			 VehPrefElem.setAttribute("TransmissionType", temp);
		 if(!(temp = vehPrefJson.optString("driveType")).isEmpty())
		 	VehPrefElem.setAttribute("DriveType", temp);
		 if(!(temp = vehPrefJson.optString("fuelType")).isEmpty())
			 VehPrefElem.setAttribute("FuelType", temp);
		 if(!(temp = vehPrefJson.optString("vehicleQty")).isEmpty())
			 VehPrefElem.setAttribute("VehicleQty", temp);
		 if(!(temp = vehPrefJson.optString("code")).isEmpty())
			 VehPrefElem.setAttribute("Code", temp);
		 
		 Element vehMakeModelElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehMakeModel");
		 if(!vehPrefJson.optString("vehMakeModelName").isEmpty())
		 vehMakeModelElem.setAttribute("Name", vehPrefJson.optString("vehMakeModelName"));
		 vehMakeModelElem.setAttribute("Code", vehPrefJson.optString("vehMakeModelCode"));
		 VehPrefElem.appendChild(vehMakeModelElem);
		 
		 Element vehClassElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehClass");
		 vehClassElem.setAttribute("Size", vehPrefJson.optString("vehicleClassSize"));
		 VehPrefElem.insertBefore(vehClassElem, vehMakeModelElem);
		 
         Element vehTypeElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehType");
         vehTypeElem.setAttribute("VehicleCategory", vehPrefJson.optString(JSON_PROP_VEHICLECATEGORY));
         VehPrefElem.insertBefore(vehTypeElem, vehClassElem);

		 vehResCoreElem.appendChild(VehPrefElem);
				
		 JSONArray feesJsonArr = suppTotalFare.getJSONObject(JSON_PROP_FEES).getJSONArray(JSON_PROP_FEE);
		 Element feeElem = null;
		 Element feesElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Fees");	
		 for(int i=0;i<feesJsonArr.length();i++) {
			 
			 JSONObject feesJson = feesJsonArr.getJSONObject(i);
			 feeElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Fee");
			 feeElem.setAttribute("Amount", BigDecimaltoString(feesJson, JSON_PROP_AMOUNT));
			 feeElem.setAttribute("CurrencyCode", feesJson.optString(JSON_PROP_CCYCODE));
			 if(!(temp = feesJson.optString("purpose")).isEmpty())
				 feeElem.setAttribute("Purpose", temp);
			 if(!(temp = feesJson.optString(JSON_PROP_ISINCLDINBASE)).isEmpty())
				 feeElem.setAttribute("IncludedInRate",temp);
			 if(!(temp = feesJson.optString(JSON_PROP_DESCRIPTION)).isEmpty())
				 feeElem.setAttribute("Description", temp);
			 
			 feesElem.appendChild(feeElem);
		 }
		 vehResCoreElem.appendChild(feesElem);
		
		 JSONArray rateDistJsonArr = vehicleAvailJson.optJSONArray(JSON_PROP_RATEDISTANCE);
		 if(rateDistJsonArr!=null) {
			 Element rateDistElem = null;
			 for(int i=0;i<rateDistJsonArr.length();i++) {
				 JSONObject rateDistJson = rateDistJsonArr.getJSONObject(i);
				 rateDistElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:RateDistance");
				 rateDistElem.setAttribute("Quantity", rateDistJson.optString(JSON_PROP_QTY));
				 rateDistElem.setAttribute("DistUnitName", rateDistJson.optString("distUnitName"));
				 rateDistElem.setAttribute("VehiclePeriodUnitName", rateDistJson.optString("vehiclePeriodUnitName"));
				 
				 vehResCoreElem.appendChild(rateDistElem);
			 }
		 }
		 
		 Element totalChargElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:TotalCharge");
		 totalChargElem.setAttribute("RateTotalAmount", BigDecimaltoString(suppTotalFare.getJSONObject(JSON_PROP_BASEFARE), JSON_PROP_AMOUNT));
	 	 totalChargElem.setAttribute("EstimatedTotalAmount", BigDecimaltoString(suppTotalFare, JSON_PROP_AMOUNT));
		 totalChargElem.setAttribute("CurrencyCode", suppTotalFare.optString(JSON_PROP_CCYCODE));
		
		 vehResCoreElem.appendChild(totalChargElem);
		 
		
		 
         Element Elem = getBookingDetailsElement(ownerDoc,vehicleAvailJson);
         vehResCoreElem.appendChild(Elem);
	      
		 
         JSONObject refJson = vehicleAvailJson.getJSONObject(JSON_PROP_REFERENCE);
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
         
         Element rentalPaymentPrefElem = createRentalPaymentPrefElement(ownerDoc, vehicleAvailJson, suppTotalFare);
         vehResInfoElem.insertBefore(rentalPaymentPrefElem, refElem);
	}

	private static Element createRentalPaymentPrefElement(Document ownerDoc, JSONObject vehicleAvailJson, JSONObject suppTotalFare) {
		 
         Element rentalPaymentPrefElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:RentalPaymentPref");
         // TODO : Check from where to get these values, Hard-coded for now.
         rentalPaymentPrefElem.setAttribute("PaymentType", "1");
         rentalPaymentPrefElem.setAttribute("Type", "");
         
        /* Element paymentCardElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PaymentCard"); 
         if(!(temp = rentalPaymentPrefJson.optString("PaymentCardCode")).isEmpty())
        	 paymentCardElem.setAttribute("CardCode", temp);
         if(!(temp = rentalPaymentPrefJson.optString("PaymentCardExpiryDate")).isEmpty())
        	 paymentCardElem.setAttribute("ExpireDate", temp);
         
         Element cardType = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CardType");
         cardType.setTextContent(rentalPaymentPrefJson.optString("CardType"));
         paymentCardElem.appendChild(cardType);
         Element cardHolderName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CardHolderName");
         cardHolderName.setTextContent(rentalPaymentPrefJson.optString("CardHolderName"));
         paymentCardElem.appendChild(cardHolderName);
         Element cardNumber = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CardNumber");
         cardNumber.setTextContent(rentalPaymentPrefJson.optString("CardNumber"));
         paymentCardElem.appendChild(cardNumber);
       	 rentalPaymentPrefElem.appendChild(paymentCardElem);*/
         
         // TODO : Payment Amount taken from Redis which was saved in search operation 
         //Check on where to get these values
         Element paymentAmountElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PaymentAmount");
		 paymentAmountElem.setAttribute("Amount",  suppTotalFare.getBigDecimal(JSON_PROP_AMOUNT).toString());
         paymentAmountElem.setAttribute("CurrencyCode", suppTotalFare.getString(JSON_PROP_CCYCODE));
         
         rentalPaymentPrefElem.appendChild(paymentAmountElem);
		return rentalPaymentPrefElem;
	}
	
	private static void sendPreBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, UserContext usrCtx) throws Exception {
		
		JSONObject kafkaMsgJson = new JSONObject(reqJson.toString());
		JSONObject reqHeaderJson = kafkaMsgJson.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		
	    kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, PROD_CATEG_SUBTYPE_CAR);
	    kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PRODCATEGSUBTYPE, PROD_CATEG_SUBTYPE_RENTAL);
        kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put("operation", "book");
		bookProducer.runProducer(1, kafkaMsgJson);
		logger.trace(String.format("Car Book Request Kafka Message: %s", kafkaMsgJson.toString()));
	}
	
	private static void sendPostBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, JSONObject resJson) throws Exception {
		
		JSONObject kafkaMsgJson = new JSONObject(resJson.toString());
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("operation", "book");
		logger.trace(String.format("Car Book Response Kafka Message: %s", kafkaMsgJson.toString()));
		bookProducer.runProducer(1, kafkaMsgJson);
	}
	
	private static Element getBookingDetailsElement(Document ownerDoc, JSONObject carRentalReq) {

		Element tpaExtensions = ownerDoc.createElementNS(Constants.NS_OTA, "ota:TPA_Extensions");
		Element bookingDetails = ownerDoc.createElementNS(NS_CAR, "car:BookingDetails");
		Element bookingDetail = ownerDoc.createElementNS(NS_CAR, "car:BookingDetail");
		
		TripIndicator tripIndc = TripIndicator.forString(carRentalReq.getString(JSON_PROP_TRIPINDC));
		boolean isOutstation = tripIndc.equals(TripIndicator.Outstation);
		
		//LOCAL - duration_unit is 'PACKAGE_STRING'
		//OUTSTATION - duration_unit is 'DAYS'
		Unit unit = isOutstation ? Unit.DAYS : Unit.PACKAGE_STRING;
		
		int days = RentalSearchProcessor.deduceBookingDays(carRentalReq);
		
		bookingDetail.setAttribute("duration_value", String.valueOf(days));
		bookingDetail.setAttribute("duration_unit", unit.toString());
		bookingDetail.setAttribute("package_selected", carRentalReq.optString(JSON_PROP_PACKAGE));
		
		bookingDetails.appendChild(bookingDetail);
		tpaExtensions.appendChild(bookingDetails);
		return tpaExtensions;
	}
	
	public static String BigDecimaltoString(JSONObject json, String prop) {
		
		try {
			if(json==null)
				return "";
			return json.getBigDecimal(prop).toString();
		}
		catch(JSONException e) {
			return "";
		}
	}
	
	public static String NumbertoString(JSONObject json, String prop) {
		
		try {
			if(json==null)
				return "";
			if(json.getNumber(prop).equals(0))
				return "";
			else
				return json.getNumber(prop).toString();
		}
		catch(JSONException e) {
			return "";
		}
	}
	
}
