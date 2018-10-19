package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.activities.ActivitiesConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

@Service
public class ActivityBookProcessor implements ActivityConstants {

	private static final Logger logger = LogManager.getLogger(ActivityBookProcessor.class);

	public static String process(JSONObject reqJson)throws RequestProcessingException, InternalProcessingException, ValidationException {
		//OperationConfig opConfig;
		ServiceConfig opConfig;
		Element reqElem;
		UserContext usrCtx;
		KafkaBookProducer bookProducer = new KafkaBookProducer();
		
		try {


			//opConfig = ActivitiesConfig.getOperationConfig("book");
			opConfig = ActivitiesConfig.getOperationConfig("book");
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			TrackingContext.setTrackingContext(reqJson);
			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			usrCtx = UserContext.getUserContextForSession(reqHdrJson);

			setSupplierRequestElem(reqJson, reqElem, usrCtx);

			kafkaRequestJson(reqJson,usrCtx, bookProducer);
		}
		catch (ValidationException valx) {
			throw valx;
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}

		try{
			Element resElem = null;

			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);

			if (resElem == null) {
				throw new Exception(ERROR_MSG_NULL_RESPONSE_SI);
			}

			JSONObject resJson = getSupplierResponseJSON(reqJson, resElem, usrCtx);

			kafkaResponseJson(reqJson,resJson, bookProducer);

			return resJson.toString();

		}
		catch (Exception e) {
			e.printStackTrace();
			return STATUS_ERROR;
		}

	}

	private static void createHeaderElem(Element reqElem, String userID, String sessionID, String transactionID) {
		XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_USERID, userID);
		XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_SESSIONID, sessionID);
		XMLUtils.setValueAtXPath(reqElem, XPATH_REQUESTHEADER_TRANSACTIONID, transactionID);

	}


	private static void kafkaResponseJson(JSONObject reqJson, JSONObject resJson, KafkaBookProducer bookProducer) throws Exception {
		resJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PRODUCT, PRODUCT_CATEGORY);

		// TODO KafkaMsg after SI request
		
		bookProducer.runProducer(1, resJson);
		logger.info((String.format("%s_RS = %s", "BOOKKAFKA", resJson)));

	}


	private static void kafkaRequestJson(JSONObject reqJson, UserContext usrCtx, KafkaBookProducer bookProducer) throws Exception {

		// Kafka JSON Creation
		// JSONObject kafkaMsgJson = reqJson;
		JSONObject kafkaMsgJson = new JSONObject(new JSONTokener(reqJson.toString()));
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_TYPE, "request");
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PRODUCT, PRODUCT_CATEGORY);
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put("bookID", reqJson.getJSONObject(JSON_PROP_REQBODY).get("bookID"));

		// ----------copied from BusBookProcessor
		JSONObject reqHeaderJson = kafkaMsgJson.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		// ----------------------------------------------------------------

		// Adding clientIATANumber to kafka reqjson
		kafkaMsgJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).put("clientIATANumber",
				usrCtx.getClientIATANUmber());

		// TODO Kafka msg before SI request.
		bookProducer.runProducer(1, kafkaMsgJson);
		logger.info((String.format("%s_RQ = %s", "BOOKKAFKA", kafkaMsgJson)));

	}

	public static void setSupplierRequestElem(JSONObject reqJson,Element reqElem, UserContext usrCtx) throws Exception {
		Document ownerDoc = reqElem.getOwnerDocument();
		Element blankWrapperElem = XMLUtils.getFirstElementAtXPath(reqElem, "./sig:RequestBody/sig1:OTA_TourActivityBookRQWrapper");
		XMLUtils.removeNode(blankWrapperElem);
		JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null) ? reqJson.optJSONObject(JSON_PROP_REQHEADER) : new JSONObject();
		JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null) ? reqJson.optJSONObject(JSON_PROP_REQBODY) : new JSONObject();


		ActivityRequestValidator.validateBookRequest(reqHdrJson, reqBodyJson);


		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);

		createHeaderElem(reqElem, userID, sessionID, transactionID);

		JSONArray reqReservationsArr = reqBodyJson.getJSONArray(JSON_PROP_RESERVATIONS);

		String redisKey = null;
		Map<String, String> reprcSuppFaresMap = null;
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			redisKey = sessionID.concat("|").concat(PRODUCT);
			reprcSuppFaresMap = redisConn.hgetAll(redisKey);
		}

		//This loop will load all the prices from cache in our json request.
		for(int i = 0; i<reqReservationsArr.length(); i++) {

			JSONObject reqReservations = reqReservationsArr.getJSONObject(i);

			String redisPriceInfoKey = ActivityRepriceProcessor.getRedisKeyForReservationV2(reqReservations);
			String priceInfoInStr = reprcSuppFaresMap.get(redisPriceInfoKey);

			if (null == priceInfoInStr) {
				continue;
			}
			JSONObject priceInfoJSON = new JSONObject(priceInfoInStr);

			// TODO : Pending
			// TODO : Basic Info JSON needs to send unique key in request Json and this
			// needs to be
			// TODO : Mapped with BasicInfo request JSon and the similar needs to be put for
			// Kafka

			reqReservations.put("suppPriceInfo", priceInfoJSON.getJSONObject("suppPriceInfo"));
			reqReservations.put("activityTotalPricingInfo", priceInfoJSON.getJSONObject("activityTotalPricingInfo"));

		}

		//Since not that we have all the prices fetched from the cache, we fetch getpolicies.
		ActivityGetPoliciesProcessor.process(reqJson);
		//At this point our json req has polices as well.




		for (int j = 0; j < reqReservationsArr.length(); j++) {

			createBookRequest(reqElem, ownerDoc, blankWrapperElem, usrCtx, reqReservationsArr.getJSONObject(j), j);


			//			JSONObject kafkaReservationJson = kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY)
			//					.getJSONArray(JSON_PROP_RESERVATIONS).getJSONObject(j);
			//
			//			JSONObject suppPriceInfo = priceInfoJSON.getJSONObject(JSON_PROP_SUPPPRICEINFO);
			//			
			//			kafkaReservationJson.put(JSON_PROP_SUPPPRICEINFO, suppPriceInfo);
			//
			//			kafkaReservationJson.put("activityTotalPricingInfo",
			//					priceInfoJSON.getJSONObject("activityTotalPricingInfo"));
			//			

			//			kafkaReservationJson.remove(JSON_PROP_PRICING);
		}







	}

	/**
	 * @param reqHdrJson
	 * @param resElem
	 * @return
	 * @throws ValidationException
	 */
	private static JSONObject getSupplierResponseJSON(JSONObject reqJson, Element resElem, UserContext usrCtx)
			throws ValidationException {
		JSONObject resJson = new JSONObject();
		JSONObject resBodyJson = new JSONObject();

		resBodyJson.put("product", "Activities");
		JSONArray supplierBookRefrencesArr = new JSONArray();

		Element[] tourActivityBookRSWrapperElems = XMLUtils.getElementsAtXPath(resElem,
				"./sig:ResponseBody/sig1:OTA_TourActivityBookRSWrapper");

		for (Element tourActivityBookRSWrapperElem : tourActivityBookRSWrapperElems) {
			JSONObject supplierBookRefrence = new JSONObject();

			String supplierID = XMLUtils.getValueAtXPath(tourActivityBookRSWrapperElem, "./sig1:SupplierID");
			supplierBookRefrence.put(SUPPLIER_ID, supplierID);

			Element[] tourActivityBookElems = XMLUtils.getElementsAtXPath(tourActivityBookRSWrapperElem,
					"./ota:OTA_TourActivityBookRS");

			for (Element tourActivityBookElem : tourActivityBookElems) {

				if (XMLUtils.getValueAtXPath(tourActivityBookElem, "./ota:Success").equalsIgnoreCase("false")) {

					ActivitySearchProcessor.validateSupplierResponse(supplierID, tourActivityBookElem);				}

				// For now it is done as Single Concatenated String from multiple confirmation
				// tags

				StringBuilder confirmationID = new StringBuilder();
				Element[] confirmationElems = XMLUtils.getElementsAtXPath(tourActivityBookRSWrapperElem,
						"./ota:OTA_TourActivityBookRS/ota:ReservationDetails/ota:Confirmation");
				for (int confirmationCount = 0; confirmationCount < confirmationElems.length; confirmationCount++) {
					confirmationID.append(XMLUtils.getValueAtXPath(confirmationElems[confirmationCount], "./@ID"))
					.append(",");
					confirmationID.append(XMLUtils.getValueAtXPath(confirmationElems[confirmationCount], "./@Type"))
					.append(",");
					confirmationID
					.append(XMLUtils.getValueAtXPath(confirmationElems[confirmationCount], "./@Instance"));
					if (confirmationCount != (confirmationElems.length - 1)) {
						confirmationID.append("|");
					}
				}

				supplierBookRefrence.put(JSON_PROP_BOOKREFID, confirmationID);
				supplierBookRefrencesArr.put(supplierBookRefrence);
			}
			resBodyJson.put("supplierBookReferences", supplierBookRefrencesArr);
			resBodyJson.put("bookID", reqJson.getJSONObject(JSON_PROP_REQBODY).get("bookID"));

		}

		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);

		// Adding clientIATANumber to kafka resjson
		resJson.getJSONObject(JSON_PROP_RESHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT)
		.put("clientIATANumber", usrCtx.getClientIATANUmber());

		//----------copied from BusBookProcessor
		JSONObject resHeaderJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		resHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		resHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		resHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		resHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		resHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		//----------------------------------------------------------------



		return resJson;

	}

	/**
	 * @param reqElem
	 * @param ownerDoc
	 * @param blankWrapperElem
	 * @param usrCtx
	 * @param multiReqArr
	 * @param j
	 * @throws Exception
	 */
	private static void createBookRequest(Element reqElem, Document ownerDoc, Element blankWrapperElem, UserContext usrCtx,
			JSONObject reservationDetail, int j) throws Exception {
		String suppID;
		ProductSupplier prodSupplier;
		//		JSONObject reservationDetail = multiReqArr.getJSONObject(j);
		suppID = reservationDetail.getString(SUPPLIER_ID);
		prodSupplier = usrCtx.getSupplierForProduct(PRODUCT_CATEGORY, PRODUCT_SUBCATEGORY, suppID);

		if (prodSupplier == null) {
			throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
		}

		XMLUtils.insertChildNode(reqElem, XPATH_REQUESTHEADER_SUPPLIERCREDENTIALSLIST,
				prodSupplier.toElement(ownerDoc, j), false);
		Element wrapperElement = (Element) blankWrapperElem.cloneNode(true);
		Document wrapperOwner = wrapperElement.getOwnerDocument();

		XMLUtils.setValueAtXPath(wrapperElement, "./sig1:SupplierID", suppID);
		XMLUtils.setValueAtXPath(wrapperElement, "./sig1:Sequence", String.valueOf(j));

		Element sourceElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_TourActivityBookRQ/ota:POS/ota:Source");
		createSource(reservationDetail, sourceElem);

		Element contactDetailElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_TourActivityBookRQ/ota:ContactDetail");
		createContactDetails(reservationDetail, contactDetailElem);

		Element basicInfoElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:BasicInfo");
		createBasicInfo(reservationDetail, basicInfoElem, wrapperOwner);

		Element schedule = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:Schedule");

		Element bookingInfo = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_TourActivityBookRQ/ota:BookingInfo");
		createParticipantInfo(reservationDetail, bookingInfo, wrapperOwner, schedule);

		createSchedule(reservationDetail, schedule);

		Element locationElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:Location");
		createLocation(reservationDetail, locationElem);

		Element pickUpDropOffElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:PickupDropoff");
		createPickupDropOff(reservationDetail, pickUpDropOffElem);

		Element pricingElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:Pricing");
		createPricing(reservationDetail, pricingElem);

		XMLUtils.insertChildNode(reqElem, "./sig:RequestBody", wrapperElement, false);

	}


	private static void createSource(JSONObject reservationDetail, Element sourceElem) {
		sourceElem.setAttribute(JSON_PROP_ISO_CURRENCY, reservationDetail.getJSONObject(JSON_PROP_BASICINFO)
				.getJSONObject(JSON_PROP_POS).getString(JSON_PROP_ISO_CURRENCY));	
	}

	/**
	 * @param reservationDetail
	 * @param wrapperElement
	 * @param wrapperOwner
	 */
	private static void createParticipantInfo(JSONObject reservationDetail, Element bookingInfo, Document wrapperOwner,
			Element schedule) {
		JSONArray particiapntInfo = reservationDetail.getJSONArray(JSON_PROP_PARTICIPANT_INFO);



		for (int participantCount = 0; participantCount < particiapntInfo.length(); participantCount++) {
			Element participantInfo = wrapperOwner.createElementNS(NS_OTA, "n3:ParticipantInfo");

			Element category = wrapperOwner.createElementNS(NS_OTA, "n3:Category");

			String Age = calculateAge(particiapntInfo.getJSONObject(participantCount).getString(JSON_PROP_DOB));

			category.setAttribute("Age", Age);
			category.setAttribute("Quantity", "1");
			category.setAttribute("ParticipantCategoryID", String.valueOf(participantCount+1));

			Element qualifierInfo = createQualifierInfo(wrapperOwner, particiapntInfo, participantCount);

			Element contact = wrapperOwner.createElementNS(NS_OTA, "n3:Contact");

			Element personName = createPersonName(wrapperOwner, particiapntInfo, participantCount);

			Element telephone = createParticiapntInfoTelephone(wrapperOwner, particiapntInfo, participantCount);

			contact.appendChild(personName);
			contact.appendChild(telephone);

			category.appendChild(qualifierInfo);
			category.appendChild(contact);

			participantInfo.appendChild(category);

			bookingInfo.insertBefore(participantInfo, schedule);

		}
	}

	/**
	 * @param particiapntInfo
	 * @param participantCount
	 * @return
	 */
	public static String calculateAge(String DOB) {

		String[] dateOFBirthArray = DOB.split("-");
		int date = Integer.parseInt(dateOFBirthArray[2]);
		int month = Integer.parseInt(dateOFBirthArray[1]);
		int year = Integer.parseInt(dateOFBirthArray[0]);
		LocalDate birthDate = LocalDate.of(year, month, date);
		LocalDate currentDate = LocalDate.now();
		String Age = Integer.toString(Period.between(birthDate, currentDate).getYears());
		return Age;
	}

	/**
	 * @param wrapperOwner
	 * @param particiapntInfo
	 * @param participantCount
	 * @return
	 */
	private static Element createQualifierInfo(Document wrapperOwner, JSONArray particiapntInfo, int participantCount) {
		Element qualifierInfo = wrapperOwner.createElementNS(NS_OTA, "n3:QualifierInfo");
		qualifierInfo.setAttribute("Extension", "1");
		qualifierInfo.setTextContent(ActivityPassengerType.valueOf(particiapntInfo.getJSONObject(participantCount).getString(JSON_PROP_QUALIFIERINFO)).toString());
		return qualifierInfo;
	}

	/**
	 * @param wrapperOwner
	 * @param particiapntInfo
	 * @param participantCount
	 * @return
	 */
	private static Element createParticiapntInfoTelephone(Document wrapperOwner, JSONArray particiapntInfo,
			int participantCount) {
		Element telephone = wrapperOwner.createElementNS(NS_OTA, "n3:Telephone");
		telephone.setAttribute("CountryAccessCode", particiapntInfo.getJSONObject(participantCount)
				.getJSONObject(JSON_PROP_CONTACTDETAILS).getString(JSON_PROP_CTRYACESCODE));
		telephone.setAttribute("PhoneNumber", particiapntInfo.getJSONObject(participantCount)
				.getJSONObject(JSON_PROP_CONTACTDETAILS).getString(JSON_PROP_PHONE_NUMBER));
		return telephone;
	}

	/**
	 * @param wrapperOwner
	 * @param particiapntInfo
	 * @param participantCount
	 * @return
	 */
	private static Element createPersonName(Document wrapperOwner, JSONArray particiapntInfo, int participantCount) {
		Element personName = wrapperOwner.createElementNS(NS_OTA, "n3:PersonName");

		Element namePrefix = wrapperOwner.createElementNS(NS_OTA, "n3:NamePrefix");
		namePrefix.setTextContent(particiapntInfo.getJSONObject(participantCount).getJSONObject(JSON_PROP_PERSONNAME)
				.getString(JSON_PROP_NAME_PREFIX));

		Element givenName = wrapperOwner.createElementNS(NS_OTA, "n3:GivenName");
		givenName.setTextContent(particiapntInfo.getJSONObject(participantCount).getJSONObject(JSON_PROP_PERSONNAME)
				.getString(JSON_PROP_GIVEN_NAME));

		Element middleName = wrapperOwner.createElementNS(NS_OTA, "n3:MiddleName");
		middleName.setTextContent(particiapntInfo.getJSONObject(participantCount).getJSONObject(JSON_PROP_PERSONNAME)
				.getString(JSON_PROP_MIDDLE_NAME));

		Element surname = wrapperOwner.createElementNS(NS_OTA, "n3:Surname");
		surname.setTextContent(particiapntInfo.getJSONObject(participantCount).getJSONObject(JSON_PROP_PERSONNAME)
				.getString(JSON_PROP_SURNAME));

		Element nameTitle = wrapperOwner.createElementNS(NS_OTA, "n3:NameTitle");
		nameTitle.setTextContent(particiapntInfo.getJSONObject(participantCount).getJSONObject(JSON_PROP_PERSONNAME)
				.getString(JSON_PROP_NAME_TITLE));

		personName.appendChild(namePrefix);
		personName.appendChild(givenName);
		personName.appendChild(middleName);
		personName.appendChild(surname);
		personName.appendChild(nameTitle);
		return personName;
	}

	/**
	 * @param reservationsJson
	 * @param wrapperElement
	 * @param wrapperOwner
	 */
	private static void createPricing(JSONObject reservationsJson, Element pricingElem) {


		Document priceOwner = pricingElem.getOwnerDocument();

		// JSONArray pricingJSONElem =
		// priceInfoJSON.getJSONObject(JSON_PROP_SUPPPRICEINFO)
		// .getJSONArray(JSON_PROP_PRICING);
		//
		// for (int pricingCount = 0; pricingCount < pricingJSONElem.length();
		// pricingCount++) {
		// if (JSON_PROP_SUMMARY
		// .equals(pricingJSONElem.getJSONObject(pricingCount).getString(JSON_PROP_PARTICIPANTCATEGORY)))
		// {
		// XMLUtils.setValueAtXPath(wrapperElement,
		// "./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:Pricing/ota:Summary/@CurrencyCode",
		// pricingJSONElem.getJSONObject(pricingCount).getString(JSON_PROP_CCYCODE));
		//
		// XMLUtils.setValueAtXPath(wrapperElement,
		// "./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:Pricing/ota:Summary/@Amount",
		// pricingJSONElem.getJSONObject(pricingCount).getBigDecimal(JSON_PROP_TOTALPRICE).toString());
		//
		// } else {
		// Element particiapntCategoryPricing = priceOwner.createElementNS(NS_OTA,
		// "ota:ParticipantCategory");
		//
		// Element qualifierInfo = priceOwner.createElementNS(NS_OTA,
		// "ota:QualifierInfo");
		//
		// qualifierInfo.setTextContent(
		// pricingJSONElem.getJSONObject(pricingCount).getString(JSON_PROP_PARTICIPANTCATEGORY));
		//
		// Element price = priceOwner.createElementNS(NS_OTA, "ota:Price");
		// price.setAttribute("Amount",
		// pricingJSONElem.getJSONObject(pricingCount).getBigDecimal(JSON_PROP_TOTALPRICE).toString());
		//
		// particiapntCategoryPricing.appendChild(qualifierInfo);
		// particiapntCategoryPricing.appendChild(price);
		//
		// pricingElem.appendChild(particiapntCategoryPricing);
		// }
		// }

		JSONObject suppPriceInfo = reservationsJson.getJSONObject(JSON_PROP_SUPPPRICEINFO);

		JSONObject activitySupplierSummaryPrice = suppPriceInfo.getJSONObject("activitySupplierSummaryPrice");

		XMLUtils.setValueAtXPath(pricingElem, "./ota:Summary/@CurrencyCode", activitySupplierSummaryPrice.getString(JSON_PROP_CCYCODE));

		XMLUtils.setValueAtXPath(pricingElem, "./ota:Summary/@Amount", activitySupplierSummaryPrice.getBigDecimal("amount").toString());

		JSONArray paxPriceInfoArr = suppPriceInfo.getJSONArray("paxPriceInfo");

		for (int i = 0; i < paxPriceInfoArr.length(); i++) {
			JSONObject paxPriceInfo = paxPriceInfoArr.getJSONObject(i);
			Element particiapntCategoryPricing = priceOwner.createElementNS(NS_OTA, "ota:ParticipantCategory");

			Element qualifierInfo = priceOwner.createElementNS(NS_OTA, "ota:QualifierInfo");

			qualifierInfo.setTextContent(
					ActivityPassengerType.valueOf(paxPriceInfo.getString(JSON_PROP_PARTICIPANTCATEGORY)).toString());

			Element price = priceOwner.createElementNS(NS_OTA, "ota:Price");
			price.setAttribute("Amount", paxPriceInfo.getBigDecimal(JSON_PROP_TOTALPRICE).toString());

			particiapntCategoryPricing.appendChild(qualifierInfo);
			particiapntCategoryPricing.appendChild(price);

			pricingElem.appendChild(particiapntCategoryPricing);
		}

	}

	/**
	 * @param reservationDetail
	 * @param wrapperElement
	 */
	private static void createPickupDropOff(JSONObject reservationDetail, Element pickUpDropOffElem) {

		pickUpDropOffElem.setAttribute("DateTime", reservationDetail.getJSONObject("pickupDropoff").getString("dateTime"));
		pickUpDropOffElem.setAttribute("LocationName", reservationDetail.getJSONObject("pickupDropoff").getString("locationName"));



		//		XMLUtils.setValueAtXPath(wrapperElement,
		//				"./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:PickupDropoff/@DateTime",
		//				reservationDetail.getJSONObject("pickupDropoff").getString("dateTime"));
		//
		//		XMLUtils.setValueAtXPath(wrapperElement,
		//				"./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:PickupDropoff/@LocationName",
		//				reservationDetail.getJSONObject("pickupDropoff").getString("locationName"));

	}

	/**
	 * @param reservationDetail
	 * @param wrapperElement
	 */
	private static void createLocation(JSONObject reservationDetail, Element locationElem) {

		XMLUtils.setValueAtXPath(locationElem, "./ota:Address/ota:CountryName/@Code", reservationDetail.getString(JSON_PROP_COUNTRYCODE));

		XMLUtils.setValueAtXPath(locationElem, "./ota:Region/@RegionCode", reservationDetail.getString(JSON_PROP_CITYCODE));

		//		XMLUtils.setValueAtXPath(wrapperElement,
		//				"./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:Location/ota:Address/ota:CountryName/@Code",
		//				reservationDetail.getString(JSON_PROP_COUNTRYCODE));
		//
		//		XMLUtils.setValueAtXPath(wrapperElement,
		//				"./ota:OTA_TourActivityBookRQ/ota:BookingInfo/ota:Location/ota:Region/@RegionCode",
		//				reservationDetail.getString(JSON_PROP_CITYCODE));
	}

	/**
	 * @param reservationDetail
	 * @param schedule
	 */
	private static void createSchedule(JSONObject reservationDetail, Element schedule) {
		JSONObject scheduleJson = reservationDetail.getJSONObject(JSON_PROP_SCHEDULE);

		XMLUtils.setValueAtXPath(schedule, "./@Start", scheduleJson.getString(JSON_PROP_START));

		XMLUtils.setValueAtXPath(schedule, "./@End", scheduleJson.getString(JSON_PROP_END));
	}

	/**
	 * @param reservationDetail
	 * @param wrapperElement
	 * @param wrapperOwner
	 */
	private static void createBasicInfo(JSONObject reservationDetail, Element basicInfoElem, Document wrapperOwner) {

		createBasicInfoAttr(reservationDetail, basicInfoElem);

		Element activityTPA = XMLUtils.getFirstElementAtXPath(basicInfoElem, "./ota:TPA_Extensions/sig1:Activity_TPA");

		createCancellationPolicy(reservationDetail, activityTPA, wrapperOwner);

		createTourLanguages(reservationDetail, activityTPA, wrapperOwner);

		createTimeSlots(reservationDetail, activityTPA, wrapperOwner);

		createSupplierDetails(reservationDetail, activityTPA);

		createAnswers(reservationDetail, activityTPA, wrapperOwner);

		createShippingDetails(reservationDetail, activityTPA);

		createPOS(reservationDetail, activityTPA);
	}

	/**
	 * @param reservationDetail
	 * @param wrapperOwner
	 * @param activityTPA
	 */
	private static void createTourLanguages(JSONObject reservationDetail, Element activityTPA, Document wrapperOwner) {
		JSONArray tourLanguages = reservationDetail.getJSONObject(JSON_PROP_BASICINFO)
				.getJSONArray(JSON_PROP_TOURLANGUAGE);

		Element tourLanguageElement = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:TourLanguage");
		tourLanguageElement.setAttribute("Code", tourLanguages.getJSONObject(0).getString(JSON_PROP_CODE));
		tourLanguageElement.setAttribute("LanguageListCode",
				tourLanguages.getJSONObject(0).getString(JSON_PROP_LANGUAGELISTCODE));
		tourLanguageElement.setTextContent(tourLanguages.getJSONObject(0).getString(JSON_PROP_VALUE));
		activityTPA.appendChild(tourLanguageElement);
	}

	/**
	 * @param reservationDetail
	 * @param basicInfo
	 */
	private static void createBasicInfoAttr(JSONObject reservationDetail, Element basicInfo) {
		XMLUtils.setValueAtXPath(basicInfo, "./@SupplierProductCode",
				reservationDetail.getJSONObject(JSON_PROP_BASICINFO).getString(JSON_PROP_SUPPLIERPRODUCTCODE));

		XMLUtils.setValueAtXPath(basicInfo, "./@SupplierBrandCode",
				reservationDetail.getJSONObject(JSON_PROP_BASICINFO).getString("supplierBrandCode"));

		XMLUtils.setValueAtXPath(basicInfo, "./@Name",
				reservationDetail.getJSONObject(JSON_PROP_BASICINFO).getString(JSON_PROP_NAME));
	}

	/**
	 * @param reservationDetail
	 * @param wrapperOwner
	 * @param activityTPA
	 */
	private static void createCancellationPolicy(JSONObject reservationDetail, Element activityTPA, Document wrapperOwner) {
		JSONArray cancellationPolicyJson = reservationDetail.getJSONObject(JSON_PROP_BASICINFO)
				.getJSONArray("cancellationPolicy");

		for (int cancellationCount = 0; cancellationCount < cancellationPolicyJson.length(); cancellationCount++) {

			Element cancellationPolicyUnit = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:Unit");
			cancellationPolicyUnit
			.setTextContent(cancellationPolicyJson.getJSONObject(cancellationCount).getString("unit"));

			Element cancellationPolicyFromValue = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:FromValue");
			cancellationPolicyFromValue
			.setTextContent(cancellationPolicyJson.getJSONObject(cancellationCount).getString("fromValue"));

			Element cancellationPolicyChargeType = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:ChargeType");
			cancellationPolicyChargeType
			.setTextContent(cancellationPolicyJson.getJSONObject(cancellationCount).getString("chargeType"));

			Element cancellationPolicyRate = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:Rate");
			cancellationPolicyRate
			.setTextContent(cancellationPolicyJson.getJSONObject(cancellationCount).getString("rate"));

			Element cancellationPolicy = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:CancellationPolicy");

			cancellationPolicy.appendChild(cancellationPolicyUnit);
			cancellationPolicy.appendChild(cancellationPolicyFromValue);
			cancellationPolicy.appendChild(cancellationPolicyChargeType);
			cancellationPolicy.appendChild(cancellationPolicyRate);

			activityTPA.appendChild(cancellationPolicy);

		}
	}

	/**
	 * @param reservationDetail
	 * @param wrapperOwner
	 * @param activityTPA
	 */
	private static void createTimeSlots(JSONObject reservationDetail, Element activityTPA, Document wrapperOwner) {
		JSONArray timeslots = reservationDetail.getJSONObject(JSON_PROP_BASICINFO)
				.getJSONArray(JSON_PROP_TIME_SLOT_DETAILS);
		Element timeSlotElem = XMLUtils.getFirstElementAtXPath(activityTPA, "./sig1:TimeSlots");

		for (int timeSlotCount = 0; timeSlotCount < timeslots.length(); timeSlotCount++) {
			Element timeSlot = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:TimeSlot");

			timeSlot.setAttribute(JSON_PROP_CODE, timeslots.getJSONObject(timeSlotCount).getString(JSON_PROP_CODE));
			timeSlot.setAttribute(JSON_PROP_STARTTIME,
					timeslots.getJSONObject(timeSlotCount).getString(JSON_PROP_STARTTIME));
			timeSlot.setAttribute(JSON_PROP_ENDTIME,
					timeslots.getJSONObject(timeSlotCount).getString(JSON_PROP_ENDTIME));

			timeSlotElem.appendChild(timeSlot);
		}
	}

	/**
	 * @param reservationDetail
	 * @param activityTPA
	 */
	private static void createSupplierDetails(JSONObject reservationDetail, Element activityTPA) {
		JSONObject supplier_Details = reservationDetail.getJSONObject(JSON_PROP_BASICINFO)
				.getJSONObject(JSON_PROP_SUPPLIER_DETAILS);

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Supplier_Details/sig1:name",
				supplier_Details.getString(JSON_PROP_NAME));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Supplier_Details/sig1:ID",
				supplier_Details.getString(JSON_PROP_ID));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Supplier_Details/sig1:Reference",
				supplier_Details.getString(JSON_PROP_REFERENCE));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Supplier_Details/sig1:RateKey",
				supplier_Details.getString(JSON_PROP_RATEKEY));
	}

	/**
	 * @param reservationDetail
	 * @param wrapperOwner
	 * @param activityTPA
	 */
	private static void createAnswers(JSONObject reservationDetail, Element activityTPA, Document wrapperOwner) {
		JSONArray answersJsonArr = reservationDetail.getJSONObject(JSON_PROP_BASICINFO).getJSONArray("answers");

		Element answers = XMLUtils.getFirstElementAtXPath(activityTPA, "./sig1:Answers");

		for (int answersCount = 0; answersCount < answersJsonArr.length(); answersCount++) {

			Element questionID = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:QuestionID");
			questionID.setTextContent(answersJsonArr.getJSONObject(answersCount).getString("questionID"));

			Element questionText = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:QuestionText");
			questionText.setTextContent(answersJsonArr.getJSONObject(answersCount).getString("questionText"));

			Element answerType = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:AnswerType");
			answerType.setTextContent(answersJsonArr.getJSONObject(answersCount).getString("answerType"));

			Element answerExample = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:answerExample");
			answerExample.setTextContent(answersJsonArr.getJSONObject(answersCount).getString("answerExample"));

			Element requiredFlag = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:RequiredFlag");
			requiredFlag.setTextContent(answersJsonArr.getJSONObject(answersCount).getString("requiredFlag"));

			Element extraInfo = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:ExtraInfo");
			extraInfo.setTextContent(answersJsonArr.getJSONObject(answersCount).getString("extraInfo"));

			Element question = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:Question");
			question.appendChild(questionID);
			question.appendChild(questionText);
			question.appendChild(answerType);
			question.appendChild(answerExample);

			Element answer = null;
			if (!answersJsonArr.getJSONObject(answersCount).getString("answer").isEmpty()) {
				answer = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:Answer");
				answer.setTextContent(answersJsonArr.getJSONObject(answersCount).getString("answer"));
			}

			Element answerSet = wrapperOwner.createElementNS(NS_SIGHTSEEING, "n2:Answer");
			answerSet.appendChild(question);
			if (answer != null)
				answerSet.appendChild(answer);

			answers.appendChild(answerSet);
		}
	}

	/**
	 * @param reservationDetail
	 * @param activityTPA
	 */
	private static void createPOS(JSONObject reservationDetail, Element activityTPA) {
		JSONObject POSJSONElem = reservationDetail.getJSONObject(JSON_PROP_BASICINFO).getJSONObject(JSON_PROP_POS);

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:POS/sig1:Source", POSJSONElem.getString("source"));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:POS/sig1:Agent_Name", POSJSONElem.getString("agent_Name"));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:POS/sig1:Email", POSJSONElem.getString(JSON_PROP_EMAIL));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:POS/sig1:Phone", POSJSONElem.getString("phone"));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:POS/sig1:Country", POSJSONElem.getString("country"));
	}

	/**
	 * @param reservationDetail
	 * @param activityTPA
	 */
	private static void createShippingDetails(JSONObject reservationDetail, Element activityTPA) {
		JSONObject shipping_Details = reservationDetail.getJSONObject(JSON_PROP_BASICINFO)
				.getJSONObject(JSON_PROP_SHIPPINGDETAILS);

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Shipping_Details/sig1:ID",
				shipping_Details.getString(JSON_PROP_ID));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Shipping_Details/sig1:OptionName",
				shipping_Details.getString("optionName"));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Shipping_Details/sig1:Details",
				shipping_Details.getString(JSON_PROP_DETAILS));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Shipping_Details/sig1:ShippingAreas/sig1:RateList/sig1:AreaID",
				shipping_Details.getJSONObject(JSON_PROP_SHIPPINGAREAS).getString(JSON_PROP_AREAID));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Shipping_Details/sig1:ShippingAreas/sig1:RateList/sig1:Name",
				shipping_Details.getJSONObject(JSON_PROP_SHIPPINGAREAS).getString(JSON_PROP_NAME));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Shipping_Details/sig1:ShippingAreas/sig1:RateList/sig1:Cost",
				shipping_Details.getJSONObject(JSON_PROP_SHIPPINGAREAS).getString("cost"));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Shipping_Details/sig1:TotalCost",
				shipping_Details.getString(JSON_PROP_TOTALCOST));

		XMLUtils.setValueAtXPath(activityTPA, "./sig1:Shipping_Details/sig1:Currency",
				shipping_Details.getString("currency"));
	}

	/**
	 * @param reservationDetail
	 * @param contactDetail
	 */
	private static void createContactDetails(JSONObject reservationDetail, Element contactDetail) {
		JSONObject contactDetailJson = reservationDetail.getJSONObject("contactDetail");

		XMLUtils.setValueAtXPath(contactDetail, "./ota:PersonName/ota:NamePrefix",
				contactDetailJson.getJSONObject(JSON_PROP_PERSONNAME).getString(JSON_PROP_NAME_PREFIX));

		XMLUtils.setValueAtXPath(contactDetail, "./ota:PersonName/ota:GivenName",
				contactDetailJson.getJSONObject(JSON_PROP_PERSONNAME).getString(JSON_PROP_GIVEN_NAME));

		XMLUtils.setValueAtXPath(contactDetail, "./ota:PersonName/ota:MiddleName",
				contactDetailJson.getJSONObject(JSON_PROP_PERSONNAME).getString(JSON_PROP_MIDDLE_NAME));

		XMLUtils.setValueAtXPath(contactDetail, "./ota:PersonName/ota:Surname",
				contactDetailJson.getJSONObject(JSON_PROP_PERSONNAME).getString(JSON_PROP_SURNAME));

		XMLUtils.setValueAtXPath(contactDetail, "./ota:PersonName/ota:NameTitle",
				contactDetailJson.getJSONObject(JSON_PROP_PERSONNAME).getString(JSON_PROP_NAME_TITLE));

		XMLUtils.setValueAtXPath(contactDetail, "./ota:Telephone/@CountryAccessCode",
				contactDetailJson.getJSONObject(JSON_PROP_TELEPHONE).getString(JSON_PROP_CTRYACESCODE));

		XMLUtils.setValueAtXPath(contactDetail, "./ota:Telephone/@PhoneNumber",
				contactDetailJson.getJSONObject(JSON_PROP_TELEPHONE).getString(JSON_PROP_PHONE_NUMBER));

		XMLUtils.setValueAtXPath(contactDetail, "./ota:Address/ota:BldgRoom",
				contactDetailJson.getJSONObject(JSON_PROP_ADDRESS).getString(JSON_PROP_BLDG_ROOM));

		XMLUtils.setValueAtXPath(contactDetail, "./ota:Address/ota:AddressLine",
				contactDetailJson.getJSONObject(JSON_PROP_ADDRESS).getString(JSON_PROP_ADDRESSLINE));

		XMLUtils.setValueAtXPath(contactDetail, "./ota:Address/ota:CountryName",
				contactDetailJson.getJSONObject(JSON_PROP_ADDRESS).getString(JSON_PROP_COUNTRYCODE));

		XMLUtils.setValueAtXPath(contactDetail, "./@BirthDate", contactDetailJson.getString(JSON_PROP_DOB));

		XMLUtils.setValueAtXPath(contactDetail, "./ota:Email", contactDetailJson.getString(JSON_PROP_EMAIL));
	}

}
