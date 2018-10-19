package com.coxandkings.travel.bookingengine.orchestrator.bus;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.bus.BusConfig;
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.bus.PriceComponentsGroup;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class BusBookProcessor implements BusConstants {

	private static final Logger logger = LogManager.getLogger(BusBookProcessor.class);
	private static String mRcvsPriceCompQualifier = JSON_PROP_RECEIVABLES.concat(SIGMA).concat(".")
			.concat(JSON_PROP_RECEIVABLE).concat(".");
	private static String mIncvPriceCompFormat = JSON_PROP_INCENTIVE.concat(".%s@").concat(JSON_PROP_CODE);

	public static String process(JSONObject reqJson) throws InternalProcessingException, ValidationException {

		// OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null, kafkaMsgJson = null, resJson = null;
		UserContext usrCtx = null;
		try {
			BusSeatBlockingProcessor.process(reqJson);

			opConfig = BusConfig.getOperationConfig(
					TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			createSIRequest(reqJson, reqElem, ownerDoc, usrCtx);

			Element resElem = null;
			// resElem = HTTPServiceConsumer.consumeXMLService("SI",
			// opConfig.getSIServiceURL(), BusConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(),
					reqElem);
			if (resElem == null) {
				logger.error("Null response received from SI");
			}
			if (reqBodyJson.optBoolean(JSON_PROP_ISTIMELIMIT)) {
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH,
						ToDoTaskSubType.TIME_LIMIT_BOOKING, "", reqBodyJson.getString("bookId"),reqHdrJson,"");
			} else {
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH,
						ToDoTaskSubType.BOOKING, "", reqBodyJson.getString("bookId"),reqHdrJson,"");
			}
			OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.BOOKING,
					"", reqBodyJson.getString("bookId"),reqHdrJson,"");

			JSONObject resBodyJson = new JSONObject();
			JSONArray bookTktJsonArr = new JSONArray();
			KafkaBookProducer bookProducer = new KafkaBookProducer();
			String bookId = reqJson.getJSONObject(JSON_PROP_REQBODY).getString("bookId");
			kafkaMsgJson = createPreKafkaJson(reqJson, usrCtx);
			// System.out.println("KAfkaRQMsg->"+kafkaMsgJson);
			bookProducer.runProducer(1, kafkaMsgJson);
			resJson = createJSONResponse(reqJson, reqHdrJson, resElem, resBodyJson, bookTktJsonArr, bookProducer,
					bookId);

		} catch (Exception e) {
			logger.info("Exception in creating RQ");

		}

		return resJson.toString();
	}

	private static JSONObject createPreKafkaJson(JSONObject reqJson, UserContext usrCtx) throws ValidationException {
		JSONObject reqBodyJson;
		JSONObject kafkaMsgJson = new JSONObject(reqJson.toString());
		// ClientType clientType =
		// ClientType.valueOf(reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONArray service = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_SERVICE);
		// BigDecimal ROE = new BigDecimal(0);
		String bookId = reqJson.getJSONObject(JSON_PROP_REQBODY).getString("bookId");
		JSONArray paymentInfoArr = kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray("paymentInfo");
		JSONArray kafkaServiceArr = new JSONArray();
		for (int z = 0; z < service.length(); z++) {
			JSONObject serviceJSon = service.getJSONObject(z);
			JSONArray paxArr = serviceJSon.getJSONArray("passengers");

			JSONObject busServiceJson = service.getJSONObject(z);
			JSONArray passArr = busServiceJson.getJSONArray("passengers");
//			JSONArray paxDetailsArr = new JSONArray();
//
//			getPassengerDetails(busServiceJson, passArr, paxDetailsArr);
			String sessionID = reqJson.getJSONObject(JSON_PROP_REQHEADER).getString(JSON_PROP_SESSIONID);
			Map<String, String> reprcSuppFaresMap = null;
			try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
			String redisKey = sessionID.concat("|").concat(PRODUCT_BUS).concat("|").concat("seatmap");
			 reprcSuppFaresMap = redisConn.hgetAll(redisKey); // map gives passanger information and
			}															// blockid
			if (reprcSuppFaresMap == null) {
				throw new ValidationException("BUS-BOOK-ERR", "not able to retrive seatblocking information from redis");
			}
			
			paxCalculation(usrCtx, reqJson, busServiceJson, passArr,reprcSuppFaresMap);
			busServiceJson.put("sequenceNumber", z + 1);

			kafkaServiceArr.put(busServiceJson);

			// }
		}

		kafkaMsgJson.put(JSON_PROP_REQBODY, new JSONObject());
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_SERVICE, kafkaServiceArr);
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PAYINFO, paymentInfoArr);
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put("product", "Bus");
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put("operation", "book");
		kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_BOOKID, bookId);
		JSONObject reqHeaderJson = kafkaMsgJson.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());

		return kafkaMsgJson;
	}

	public static void paxCalculation(UserContext usrCtx, JSONObject reqJson, JSONObject busServiceJson,
			JSONArray passArr,Map<String, String> reprcSuppFaresMap) throws ValidationException {

		ClientType clientType = ClientType.valueOf(reqJson.getJSONObject(JSON_PROP_REQHEADER)
				.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
		String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT)
				.getString(JSON_PROP_CLIENTMARKET);
		String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT)
				.getString(JSON_PROP_CLIENTCCY);
		PriceComponentsGroup totalFareCompsGroup = new PriceComponentsGroup(JSON_PROP_BUSSERVICETOTALFARE,
				clientCcyCode, new BigDecimal(0), true);
		PriceComponentsGroup totalIncentivesGroup = new PriceComponentsGroup(JSON_PROP_INCENTIVES, clientCcyCode,
				BigDecimal.ZERO, true);
		String SupplierTotalFareCurrency = "";
		JSONArray orderlevelCommArr = new JSONArray();
		BigDecimal clientcommercialAmount = new BigDecimal(0);
		BigDecimal calcSuppTotalFare = new BigDecimal(0);
		JSONObject orderLevelJson = new JSONObject();
		JSONObject orderLevelcommercialJson = new JSONObject();
		JSONArray clientCommTotalArr = new JSONArray();
		JSONArray entityCommArr = new JSONArray();

		JSONObject busTotalPriceInfoJson = new JSONObject();
		JSONObject suppInfoJson = new JSONObject();
		JSONObject supplierPricingInfoJson = new JSONObject();
		JSONObject totalFareJson = new JSONObject();
		JSONObject supplierFareJson = new JSONObject();
		JSONObject supplierTotalFareJson = new JSONObject();

		JSONArray paxClientFaresArr = new JSONArray();
		JSONArray paxSupplierFaresArr = new JSONArray();
		Map<String, JSONObject> suppCommTotalsMap = new HashMap<String, JSONObject>();
		Map<String, JSONObject> clientCommTotalsMap = new HashMap<String, JSONObject>();
		JSONObject clientEntityTotalCommercials = new JSONObject();
		Map<String, JSONArray> entityMap = new HashMap<String, JSONArray>();
		JSONObject clientEntityDetailsTotalJson = new JSONObject();
		JSONObject taxJson = null;
//		String sessionID = reqJson.getJSONObject(JSON_PROP_REQHEADER).getString(JSON_PROP_SESSIONID);
//		Jedis redisConn = RedisConfig.getRedisConnectionFromPool();
//		String redisKey = sessionID.concat("|").concat(PRODUCT_BUS).concat("|").concat("seatmap");
//		Map<String, String> reprcSuppFaresMap = redisConn.hgetAll(redisKey); // map gives passanger information and
//																				// blockid
//		if (reprcSuppFaresMap == null) {
//			throw new ValidationException("BUS-BOOK-ERR", "not able to retrive seatblocking information from redis");
//		}
//		RedisConfig.releaseRedisConnectionToPool(redisConn);
		for (int i = 0; i < passArr.length(); i++) {
			JSONObject passJson = passArr.getJSONObject(i);
			BigDecimal totalFare = new BigDecimal(0);
			try {
				if (reprcSuppFaresMap != null) {
					String redisKeyForFare = BusSeatMapProcessor.getseatMapKeyForSeatFare(busServiceJson, passJson);
					supplierFareJson = new JSONObject(reprcSuppFaresMap.get(redisKeyForFare));
					calcSuppTotalFare = calcSuppTotalFare.add(supplierFareJson.getBigDecimal("fare"));
					SupplierTotalFareCurrency = supplierFareJson.getString(JSON_PROP_CURRENCY);
				}
			} catch (Exception e) {
				logger.info("supplier price information not found for seat number :- "
						+ passJson.getString(JSON_PROP_SEATNO));
			}

			JSONObject suppCommJson = null;
			try {
				suppCommJson = new JSONObject(reprcSuppFaresMap
						.get(BusSeatMapProcessor.getMapKeyForSuppliercommercials(busServiceJson, passJson)));
				supplierFareJson = new JSONObject(
						reprcSuppFaresMap.get(BusSeatMapProcessor.getseatMapKeyForSeatFare(busServiceJson, passJson)));
				if (suppCommJson != null) {
					JSONArray commdetailsArr = suppCommJson.getJSONArray(JSON_PROP_PASSANGERDETAILS).getJSONObject(0)
							.optJSONArray(JSON_PROP_COMMDETAILS);
					JSONArray paxLevelSupplierCommArr = new JSONArray();
					JSONObject paxSupplierFareJson = new JSONObject();
					calculateSuppCommercials(orderlevelCommArr, clientcommercialAmount, orderLevelJson,
							suppCommTotalsMap, commdetailsArr, paxLevelSupplierCommArr, paxSupplierFaresArr,
							paxSupplierFareJson, passJson, supplierFareJson);
				}

			} catch (Exception e) {
				logger.info(
						"supplier commercials not found for seat number :-  " + passJson.getString(JSON_PROP_SEATNO));
			}
			JSONObject clientCommJson = null;
			totalFare = supplierFareJson.getBigDecimal("fare");
			try {
				clientCommJson = new JSONObject(reprcSuppFaresMap
						.get(BusSeatMapProcessor.getMapKeyForClientCommercials(busServiceJson, passJson)));
				entityCommArr = clientCommJson.getJSONArray(JSON_PROP_PASSANGERDETAILS).getJSONObject(0)
						.getJSONArray(JSON_PROP_ENTITYCOMMS);
				Map<String, String> commToTypeMap = new HashMap<String, String>();

				for (int m = 0; m < entityCommArr.length(); m++) {
					JSONArray additionArr = entityCommArr.getJSONObject(m).optJSONArray("additionalCommercialDetails");
					for (int n = 0; n < additionArr.length(); n++) {
						JSONObject addJson = additionArr.getJSONObject(n);
						commToTypeMap.put(addJson.getString("commercialName"), addJson.getString("commercialType"));
					}
					JSONArray retensionArr = entityCommArr.getJSONObject(m).optJSONArray("retentionCommercialDetails");
					for (int n = 0; n < retensionArr.length(); n++) {
						JSONObject retenionJson = additionArr.getJSONObject(n);
						commToTypeMap.put(retenionJson.getString("commercialName"),
								retenionJson.getString("commercialType"));
					}
					JSONArray fixedArr = entityCommArr.getJSONObject(m).optJSONArray("fixedCommercialDetails");
					for (int n = 0; n < fixedArr.length(); n++) {
						JSONObject fixedJson = additionArr.getJSONObject(n);
						commToTypeMap.put(fixedJson.getString("commercialName"), fixedJson.getString("commercialType"));
					}
					JSONObject markUpCommercialDetails = entityCommArr.getJSONObject(m)
							.optJSONObject("markUpCommercialDetails");
					commToTypeMap.put(markUpCommercialDetails.getString("commercialName"),
							markUpCommercialDetails.getString("commercialType"));

				}

				JSONObject paxClientFareJson = new JSONObject();
				JSONArray paxLevelClientCommArr = new JSONArray();
				BigDecimal paxCount = new BigDecimal(passArr.length());
				calculateClientCommercials(clientMarket, clientCcyCode, totalFare, orderLevelcommercialJson,
						clientCommTotalArr, totalFareJson, paxClientFaresArr, totalFareCompsGroup, clientCommTotalsMap,
						passJson, entityCommArr, paxClientFareJson, paxCount, paxLevelClientCommArr, commToTypeMap,
						usrCtx, clientEntityTotalCommercials, totalIncentivesGroup, entityMap);
			} catch (Exception e) {
				logger.info("client commercials not found for seat number :- " + passJson.getString(JSON_PROP_SEATNO));
			}

			// try
			// {
			// if(reprcSuppFaresMap!=null)
			// {
			// String redisKeyForFare =
			// BusSeatMapProcessor.getseatMapKeyForSeatFare(busServiceJson,passJson);
			// supplierFareJson = new JSONObject(reprcSuppFaresMap.get(redisKeyForFare));
			// calcSuppTotalFare =
			// calcSuppTotalFare.add(supplierFareJson.getBigDecimal("fare"));
			// SupplierTotalFareCurrency = supplierFareJson.getString(JSON_PROP_CURRENCY);
			// }
			// }
			// catch(Exception e)
			// {
			// logger.info("supplier price information not found for seat number :-
			// "+passJson.getString(JSON_PROP_SEATNO));
			// }

			BigDecimal ROE = RedisRoEData.getRateOfExchange(supplierFareJson.getString("currency"), clientCcyCode,
					clientMarket);
			busServiceJson.put("rateOfExchange", ROE);

			// taxes added.
			if (reprcSuppFaresMap.get(BusSeatMapProcessor.getMapKeyForTaxEngine(busServiceJson, passJson)) != null)
				taxJson = new JSONObject(
						reprcSuppFaresMap.get(BusSeatMapProcessor.getMapKeyForTaxEngine(busServiceJson, passJson)));

		}

		for (Entry<String, JSONObject> entry : suppCommTotalsMap.entrySet()) {
			orderlevelCommArr.put(entry.getValue());
		}

		// suppinfo

		supplierTotalFareJson.put(JSON_PROP_AMOUNT, calcSuppTotalFare);
		supplierTotalFareJson.put(JSON_PROP_CURRENCY, SupplierTotalFareCurrency);
		supplierPricingInfoJson.put(JSON_PROP_BUSSERVICETOTALFARE, supplierTotalFareJson);// supplier itintotal
		supplierPricingInfoJson.put(JSON_PROP_SUPPCOMMTOTALS, orderlevelCommArr);
		supplierPricingInfoJson.put(JSON_PROP_PAXSEATFARES, paxSupplierFaresArr);
		suppInfoJson.put(JSON_PROP_SUPPPRICEINFO, supplierPricingInfoJson);
		busServiceJson.put("suppInfo", suppInfoJson);

		JSONArray entityTotalArr = new JSONArray();
		// bustotalpriceinfo
		for (int k = 0; entityCommArr != null && k < entityCommArr.length(); k++) {

			ClientInfo[] clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchyArray();
			clientEntityDetailsTotalJson.put(JSON_PROP_CLIENTID, clientEntityDetailsArr[k].getClientId());
			clientEntityDetailsTotalJson.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsArr[k].getParentClienttId());
			clientEntityDetailsTotalJson.put(JSON_PROP_COMMENTITYTYPE,
					clientEntityDetailsArr[k].getCommercialsEntityType());
			clientEntityDetailsTotalJson.put(JSON_PROP_COMMENTITYID,
					clientEntityDetailsArr[k].getCommercialsEntityId());
			StringBuilder key = new StringBuilder();
			key.append(clientEntityDetailsArr[k].getClientId());
			key.append(clientEntityDetailsArr[k].getParentClienttId());
			key.append(clientEntityDetailsArr[k].getCommercialsEntityType());
			key.append(clientEntityDetailsArr[k].getCommercialsEntityId());
			clientEntityDetailsTotalJson.put("clientCommercialsTotal", entityMap.get(key.toString()));
			entityTotalArr.put(clientEntityDetailsTotalJson);
		}

		busTotalPriceInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, entityTotalArr);
		busTotalPriceInfoJson.put(JSON_PROP_BUSSERVICETOTALFARE, totalFareCompsGroup.toJSON());
		if (clientType == ClientType.B2B) {
			busTotalPriceInfoJson.put(JSON_PROP_INCENTIVES, totalIncentivesGroup.toJSON());
		}
		busTotalPriceInfoJson.put(JSON_PROP_PAXSEATFARES, paxClientFaresArr);

		JSONObject serviceTotalFareJson = busTotalPriceInfoJson.getJSONObject(JSON_PROP_BUSSERVICETOTALFARE);
		BigDecimal TotalAmtAftrTax = serviceTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
		if (taxJson != null) {
			serviceTotalFareJson.put(JSON_PROP_COMPANYTAXES, taxJson);
			TotalAmtAftrTax = TotalAmtAftrTax.add(taxJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO));
		}
		serviceTotalFareJson.put(JSON_PROP_AMOUNT, TotalAmtAftrTax);
		busTotalPriceInfoJson.put(JSON_PROP_BUSSERVICETOTALFARE, serviceTotalFareJson);
		busServiceJson.put(JSON_PROP_BUSTOTALPRICEINFO, busTotalPriceInfoJson);
		
		JSONArray paxDetailsArr = new JSONArray();
		getPassengerDetails(busServiceJson, passArr, paxDetailsArr);
		
		
		
	}

	private static JSONObject createJSONResponse(JSONObject reqJson, JSONObject reqHdrJson, Element resElem,
			JSONObject resBodyJson, JSONArray bookTktJsonArr, KafkaBookProducer bookProducer, String bookId)
			throws Exception {
		JSONObject kafkaMsgJson;
		Element[] wrapperElems = BusSeatMapProcessor.sortWrapperElementsBySequence(
				XMLUtils.getElementsAtXPath(resElem, "./busi:ResponseBody/bus:OTA_BusBookTicketRSWrapper"));

		for (Element wrapperElement : wrapperElems) {
			Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_BusBookTicketRS");
			getBookJSON(resBodyElem, bookTktJsonArr);
		}
		resBodyJson.put("bookTicket", bookTktJsonArr);
		resBodyJson.put("product", "Bus");
		resBodyJson.put("operation", "book");
		resBodyJson.put(JSON_PROP_BOOKID, bookId);

		JSONObject resJson = new JSONObject();

		createCancellationPloicyRQ(reqJson, resBodyJson);
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		kafkaMsgJson = new JSONObject(resJson.toString());
		System.out.println("KAfkaRQMsg->" + kafkaMsgJson);
		bookProducer.runProducer(1, kafkaMsgJson);
		for (int x = 0; x < bookTktJsonArr.length(); x++) {
			bookTktJsonArr.getJSONObject(x).remove("cancellationPolicy");
		}
		return resJson;
	}

	private static void createSIRequest(JSONObject reqJson, Element reqElem, Document ownerDoc, UserContext usrCtx)
			throws ValidationException {
		JSONObject reqHdrJson;
		JSONObject reqBodyJson;
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./busi:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./bus:OTA_BusBookTicketRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);

		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);

		List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT,
				PROD_CATEG_SUBTYPE_BUS);

		BusSearchProcessor.createHeader(reqElem, sessionID, transactionID, userID);

		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
				"./bus:RequestHeader/com:SupplierCredentialsList");
		for (ProductSupplier prodSupplier : prodSuppliers) {
			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
		}

		JSONArray service = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_SERVICE);
		for (int z = 0; z < service.length(); z++) {
			JSONObject serviceJSon = service.getJSONObject(z);
			JSONArray paxArr = serviceJSon.getJSONArray("passengers");
			Map<String, String> reprcSuppFaresMap = null;
			try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool()) {
				String redisKey = sessionID.concat("|").concat(PRODUCT_BUS).concat("|").concat("seatmap");
				reprcSuppFaresMap = redisConn.hgetAll(redisKey); // map gives passanger information and
			}																// blockid
			if (reprcSuppFaresMap == null) {
				throw new ValidationException("BUS-BOOK-ERR",
						"not able to retrive seatblocking information from redis");
			}
			//RedisConfig.releaseRedisConnectionToPool(redisConn);

			if ((reprcSuppFaresMap.get(BusSeatBlockingProcessor.getMapKey(serviceJSon, paxArr))) != null) {
				reqBodyJson = new JSONObject(
						reprcSuppFaresMap.get(BusSeatBlockingProcessor.getMapKey(serviceJSon, paxArr)));
				JSONObject busServiceJson = reqBodyJson;
				if (busServiceJson.has("errorMessage")) {

					throw new ValidationException("BUS-BOOK_ERR",
							"Requested seats are already booked so Please Try again with different seat numbers..");
				}

				Element suppWrapperElem = null;
				suppWrapperElem = (Element) wrapperElem.cloneNode(true);
				reqBodyElem.appendChild(suppWrapperElem);

				XMLUtils.setValueAtXPath(suppWrapperElem, "./bus:SupplierID",
						busServiceJson.getString(JSON_PROP_SUPPREF));
				XMLUtils.setValueAtXPath(suppWrapperElem, "./bus:Sequence", String.valueOf(z + 1));

				Element otaBookTkt = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_BusBookTicketRQ");
				Element newElem;

				newElem = ownerDoc.createElementNS(NS_OTA, "HoldKey");
				newElem.setTextContent(busServiceJson.get("holdKey").toString());
				otaBookTkt.appendChild(newElem);

				if (busServiceJson.get(JSON_PROP_JOURNEYDATE).toString().isEmpty() == false) {
					newElem = ownerDoc.createElementNS(NS_OTA, "JourneyDate");
					newElem.setTextContent(busServiceJson.get(JSON_PROP_JOURNEYDATE).toString());
					otaBookTkt.appendChild(newElem);
				}
				if (busServiceJson.get(JSON_PROP_OPERATORID).toString().isEmpty() == false) {
					newElem = ownerDoc.createElementNS(NS_OTA, "operatorId");
					newElem.setTextContent(busServiceJson.get(JSON_PROP_OPERATORID).toString());
					otaBookTkt.appendChild(newElem);
				}

			}
		}
	}

	private static void createCancellationPloicyRQ(JSONObject reqJson, JSONObject resBodyJson) {

		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		String getPolicyJsonStr = "";

		JSONArray serviceArr = reqBodyJson.getJSONArray(JSON_PROP_SERVICE);
		JSONArray bookTktArr = resBodyJson.getJSONArray("bookTicket");
		for (int i = 0; i < serviceArr.length(); i++) {
			JSONObject serviceJson = serviceArr.getJSONObject(i);
			JSONObject bookTktJson = bookTktArr.getJSONObject(i);
			if (bookTktJson.has("errorMessage")) {

				continue;
			}
			serviceJson.put("ticketNo", bookTktJson.get("ticketNo"));
			serviceJson.put("PNRNo", bookTktArr.getJSONObject(i).get("PNRNo"));
			getPolicyJsonStr = BusGetPolicyProcessor.process(reqJson);
			JSONObject getPolicyres = new JSONObject(getPolicyJsonStr);
			JSONObject getpolicyBodyJson = getPolicyres.getJSONObject(JSON_PROP_RESBODY);

			resBodyJson.getJSONArray("bookTicket").getJSONObject(i).put("cancellationPolicy",
					getpolicyBodyJson.getJSONArray("policyResponse"));

		}

	}

	private static void getPassengerDetails(JSONObject busServiceJson, JSONArray passArr, JSONArray paxDetailsArr) {
		for (int i = 0; i < passArr.length(); i++) {
			JSONObject paxJson = new JSONObject();
			JSONObject passJson = passArr.getJSONObject(i);
			JSONObject documentDetails = new JSONObject();
			JSONArray documentInfoArr = new JSONArray();
			JSONArray contactDetailsArr = new JSONArray();

			JSONObject docInfoJson = new JSONObject();
			docInfoJson.put("IdNumber", passJson.get("IdNumber"));
			docInfoJson.put("IdType", passJson.get("IdType"));
			documentInfoArr.put(docInfoJson);
			documentDetails.put("documentInfo", documentInfoArr);
			paxJson.put("documentDetails", documentDetails);

			paxJson.put(JSON_PROP_FIRSTNAME, passJson.get(JSON_PROP_FIRSTNAME));
			paxJson.put(JSON_PROP_MIDDLENAME, passJson.opt(JSON_PROP_MIDDLENAME));
			paxJson.put(JSON_PROP_SURNAME, passJson.opt(JSON_PROP_SURNAME));
			paxJson.put(JSON_PROP_SEATNO, passJson.get(JSON_PROP_SEATNO));
			paxJson.put(JSON_PROP_TITLE, passJson.get(JSON_PROP_TITLE));
			paxJson.put(JSON_PROP_GENDER, passJson.get(JSON_PROP_GENDER));
			paxJson.put(JSON_PROP_DATEOFBIRTH, passJson.get(JSON_PROP_DATEOFBIRTH));
//			paxJson.put("seatTypesList", passJson.get("seatTypesList"));
//			paxJson.put("seatTypeIds", passJson.get("seatTypeIds"));
			paxJson.put("isPrimary", passJson.get("isPrimary"));

			// JSONObject contactJson = new JSONObject();
			JSONObject contactInfoJson = new JSONObject();
			contactInfoJson.put(JSON_PROP_EMAIL, passJson.get(JSON_PROP_EMAIL));
			contactInfoJson.put(JSON_PROP_MOBILENBR, passJson.get(JSON_PROP_MOBILENBR));
			// contactInfoJson.put("phone", passJson.get("phone"));
			// contactInfoJson.put(JSON_PROP_FIRSTNAME, passJson.get(JSON_PROP_FIRSTNAME));
			// contactJson.put("contactInfo", contactInfoJson);
			contactDetailsArr.put(contactInfoJson);
			paxJson.put(JSON_PROP_CONTACTDTLS, contactDetailsArr);

			paxDetailsArr.put(paxJson);
		}
		busServiceJson.remove("passangers");
		busServiceJson.put("paxDetails", paxDetailsArr);
	}

	private static void calculateClientCommercials(String clientMarket, String clientCcyCode, BigDecimal totalFare,
			JSONObject orderLevelcommercialJson, JSONArray clientCommTotalArr, JSONObject totalFareJson,
			JSONArray paxClientFaresArr, PriceComponentsGroup totalFareCompsGroup,
			Map<String, JSONObject> clientCommTotalsMap, JSONObject passJson, JSONArray entityCommArr,
			JSONObject paxClientFareJson, BigDecimal paxCount, JSONArray paxLevelClientCommArr,
			Map<String, String> commToTypeMap, UserContext usrCtx, JSONObject clientEntityTotalCommercials,
			PriceComponentsGroup totalIncentivesGroup, Map<String, JSONArray> entityMap) {

		String commercialName;
		String commercialType;
		String commercialCurrency;
		BigDecimal clientcommercialAmount;
		String totalFareCurrency;

		PriceComponentsGroup paxReceivablesCompsGroup = null;
		PriceComponentsGroup totalPaxReceivablesCompsGroup = null;

		JSONArray clientTotalEntityArray = null;

		for (int k = 0; k < entityCommArr.length(); k++) {
			clientTotalEntityArray = new JSONArray();
			JSONObject mapObj = null;

			JSONArray additinaldetailsArr = entityCommArr.getJSONObject(k).getJSONArray(JSON_PROP_ADDITIONCOMMDETAILS);
			JSONArray tempadditionJsonArr = new JSONArray();
			paxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode, new BigDecimal(0),
					true);
			totalPaxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode,
					new BigDecimal(0), true);
			for (int p = 0; p < additinaldetailsArr.length(); p++) {
				JSONObject additionJson = additinaldetailsArr.getJSONObject(p);
				BigDecimal calculatedAmt = new BigDecimal(0);

				JSONObject paxTempJson = new JSONObject();

				if (clientCommTotalsMap.containsKey(additionJson.getString(JSON_PROP_COMMNAME))) {
					clientcommercialAmount = additionJson.getBigDecimal(JSON_PROP_COMMAMOUNT);
					mapObj = clientCommTotalsMap.get(additionJson.getString(JSON_PROP_COMMNAME));
					clientEntityTotalCommercials = new JSONObject(mapObj.toString());
					calculatedAmt = clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT)
							.add(clientcommercialAmount);
					clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, calculatedAmt);

					clientTotalEntityArray.put(clientEntityTotalCommercials);
					paxTempJson = new JSONObject(clientEntityTotalCommercials.toString());
					paxTempJson.put(JSON_PROP_COMMAMOUNT, clientcommercialAmount);
					paxLevelClientCommArr.put(paxTempJson);
				} else {
					clientEntityTotalCommercials = new JSONObject();
					JSONObject tempJson = new JSONObject();
					tempJson.put(JSON_PROP_COMMNAME, additionJson.getString(JSON_PROP_COMMNAME));
					tempJson.put(JSON_PROP_COMMAMOUNT, additionJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
					tempJson.put(JSON_PROP_COMMCURRENCY, additionJson.getString(JSON_PROP_COMMCURRENCY));
					tempJson.put(JSON_PROP_COMMTYPE, additionJson.getString(JSON_PROP_COMMTYPE));
					clientCommTotalsMap.put(additionJson.getString(JSON_PROP_COMMNAME), tempJson);
					clientTotalEntityArray.put(tempJson);
					paxLevelClientCommArr.put(tempJson);
				}
				orderLevelcommercialJson.put(JSON_PROP_ADDITIONCOMMDETAILS, tempadditionJsonArr);

				String additionalCommName = additionJson.optString(JSON_PROP_COMMNAME);
				if (COMM_TYPE_RECEIVABLE.equals(commToTypeMap.get(additionalCommName))) {
					String additionalCommCcy = additionJson.getString(JSON_PROP_COMMCCY);
					BigDecimal additionalCommAmt = additionJson.getBigDecimal(JSON_PROP_COMMAMOUNT)
							.multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket));
					// calculatedAmt = calculatedAmt.add(additionalCommAmt);
					// totalFareCompsGroup.add(mRcvsPriceCompQualifier.concat(additionalCommName).concat("@").concat(JSON_PROP_CODE),
					// clientCcyCode, additionalCommAmt.multiply(paxCount));
					paxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@")
							.concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt);
					totalPaxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName)
							.concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt.multiply(paxCount));
				}

				if (paxReceivablesCompsGroup != null
						&& paxReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
					calculatedAmt = calculatedAmt.add(paxReceivablesCompsGroup.getComponentAmount());
					totalFareCompsGroup.addSubComponent(totalPaxReceivablesCompsGroup, null);
					paxTempJson.put(JSON_PROP_RECEIVABLES, paxReceivablesCompsGroup.toJSON());
				}
			}

			JSONArray retensionCommDetailsArr = entityCommArr.getJSONObject(k)
					.getJSONArray(JSON_PROP_RETENSIONCOMMDETAILS);
			JSONArray tempRetensionJsonArr = new JSONArray();
			for (int p = 0; p < retensionCommDetailsArr.length(); p++) {
				JSONObject retensionJson = retensionCommDetailsArr.getJSONObject(p);
				JSONObject paxTempJson = new JSONObject();

				BigDecimal calculatedAmt = new BigDecimal(0);
				if (clientCommTotalsMap.containsKey(retensionJson.getString(JSON_PROP_COMMNAME))) {
					clientcommercialAmount = retensionJson.getBigDecimal(JSON_PROP_COMMAMOUNT);
					mapObj = clientCommTotalsMap.get(retensionJson.getString(JSON_PROP_COMMNAME));
					clientEntityTotalCommercials = new JSONObject(mapObj.toString());
					calculatedAmt = clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT)
							.add(clientcommercialAmount);
					clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, calculatedAmt);

					commercialCurrency = retensionJson.getString(JSON_PROP_COMMCURRENCY);
					commercialName = retensionJson.getString(JSON_PROP_COMMNAME);
					commercialType = retensionJson.getString(JSON_PROP_COMMTYPE);
					JSONObject tempJson = new JSONObject();
					tempJson.put(JSON_PROP_COMMAMOUNT, calculatedAmt);
					tempJson.put(JSON_PROP_COMMCURRENCY, commercialCurrency);
					tempJson.put(JSON_PROP_COMMNAME, commercialName);
					tempJson.put(JSON_PROP_COMMTYPE, commercialType);

					clientTotalEntityArray.put(tempJson);
					paxTempJson = new JSONObject(tempJson.toString());
					paxTempJson.put(JSON_PROP_COMMAMOUNT, clientcommercialAmount);
					paxLevelClientCommArr.put(paxTempJson);

				} else {

					clientEntityTotalCommercials = new JSONObject();
					JSONObject tempJson = new JSONObject();
					tempJson.put(JSON_PROP_COMMNAME, retensionJson.getString(JSON_PROP_COMMNAME));
					tempJson.put(JSON_PROP_COMMAMOUNT, retensionJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
					tempJson.put(JSON_PROP_COMMCURRENCY, retensionJson.getString(JSON_PROP_COMMCURRENCY));
					tempJson.put(JSON_PROP_COMMTYPE, retensionJson.getString(JSON_PROP_COMMTYPE));

					clientCommTotalsMap.put(retensionJson.getString(JSON_PROP_COMMNAME), tempJson);

					clientTotalEntityArray.put(tempJson);
					paxLevelClientCommArr.put(tempJson);
				}
				orderLevelcommercialJson.put(JSON_PROP_RETENSIONCOMMDETAILS, tempRetensionJsonArr);
			}
			JSONArray fixedCommDetailsArr = entityCommArr.getJSONObject(k).getJSONArray(JSON_PROP_FIXEDCOMMDETAILS);
			JSONArray tempFixedJsonArr = new JSONArray();
			for (int p = 0; p < fixedCommDetailsArr.length(); p++) {
				JSONObject fixedJson = fixedCommDetailsArr.getJSONObject(p);
				JSONObject paxTempJson = new JSONObject();

				BigDecimal calculatedAmt = new BigDecimal(0);
				if (clientCommTotalsMap.containsKey(fixedJson.getString(JSON_PROP_COMMNAME))) {
					clientcommercialAmount = fixedJson.getBigDecimal(JSON_PROP_COMMAMOUNT);
					mapObj = clientCommTotalsMap.get(fixedJson.getString(JSON_PROP_COMMNAME));
					clientEntityTotalCommercials = new JSONObject(mapObj.toString());
					calculatedAmt = clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT)
							.add(clientcommercialAmount);
					clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, calculatedAmt);

					commercialCurrency = fixedJson.getString(JSON_PROP_COMMCURRENCY);
					commercialName = fixedJson.getString(JSON_PROP_COMMNAME);
					commercialType = fixedJson.getString(JSON_PROP_COMMTYPE);
					JSONObject tempJson = new JSONObject();
					tempJson.put(JSON_PROP_COMMAMOUNT, calculatedAmt);
					tempJson.put(JSON_PROP_COMMCURRENCY, commercialCurrency);
					tempJson.put(JSON_PROP_COMMNAME, commercialName);
					tempJson.put(JSON_PROP_COMMTYPE, commercialType);

					clientTotalEntityArray.put(tempJson);
					paxTempJson = new JSONObject(tempJson.toString());
					paxTempJson.put(JSON_PROP_COMMAMOUNT, clientcommercialAmount);
					paxLevelClientCommArr.put(paxTempJson);

				} else {

					JSONObject tempJson = new JSONObject();
					clientEntityTotalCommercials = new JSONObject();
					tempJson.put(JSON_PROP_COMMNAME, fixedJson.getString(JSON_PROP_COMMNAME));
					tempJson.put(JSON_PROP_COMMAMOUNT, fixedJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
					tempJson.put(JSON_PROP_COMMCURRENCY, fixedJson.getString(JSON_PROP_COMMCURRENCY));
					tempJson.put(JSON_PROP_COMMTYPE, fixedJson.getString(JSON_PROP_COMMTYPE));

					clientCommTotalsMap.put(fixedJson.getString(JSON_PROP_COMMNAME), tempJson);

					clientTotalEntityArray.put(tempJson);
					paxLevelClientCommArr.put(tempJson);
				}
				orderLevelcommercialJson.put(JSON_PROP_FIXEDCOMMDETAILS, tempFixedJsonArr);
			}
			JSONObject markupJson = entityCommArr.getJSONObject(k).getJSONObject(JSON_PROP_MARKUPCOMDTLS);

			totalFare = totalFare.add(markupJson.getBigDecimal(JSON_PROP_TOTALFARE));
			totalFareCurrency = markupJson.getString(JSON_PROP_COMMCURRENCY);

			JSONObject paxTempJson = new JSONObject();

			JSONObject tempJson = new JSONObject();
			BigDecimal calculatedAmt = new BigDecimal(0);
			if (clientCommTotalsMap.containsKey(markupJson.getString(JSON_PROP_COMMNAME))) {
				clientcommercialAmount = markupJson.getBigDecimal(JSON_PROP_COMMAMOUNT);
				mapObj = clientCommTotalsMap.get(markupJson.getString(JSON_PROP_COMMNAME));
				clientEntityTotalCommercials = new JSONObject(mapObj.toString());
				calculatedAmt = clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT)
						.add(clientcommercialAmount);
				clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, calculatedAmt);

				commercialCurrency = markupJson.getString(JSON_PROP_COMMCURRENCY);
				commercialName = markupJson.getString(JSON_PROP_COMMNAME);
				commercialType = markupJson.getString(JSON_PROP_COMMTYPE);
				tempJson.put(JSON_PROP_COMMAMOUNT, calculatedAmt);
				tempJson.put(JSON_PROP_COMMCURRENCY, commercialCurrency);
				tempJson.put(JSON_PROP_COMMNAME, commercialName);
				tempJson.put(JSON_PROP_COMMTYPE, commercialType);

				clientTotalEntityArray.put(tempJson);
				paxTempJson = new JSONObject(tempJson.toString());
				paxTempJson.put(JSON_PROP_COMMAMOUNT, clientcommercialAmount);
				paxLevelClientCommArr.put(paxTempJson);
			} else {
				tempJson = new JSONObject();
				clientEntityTotalCommercials = new JSONObject();
				tempJson.put(JSON_PROP_COMMNAME, markupJson.getString(JSON_PROP_COMMNAME));
				tempJson.put(JSON_PROP_COMMAMOUNT, markupJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
				tempJson.put(JSON_PROP_COMMCURRENCY, markupJson.getString(JSON_PROP_COMMCURRENCY));
				tempJson.put(JSON_PROP_COMMTYPE, markupJson.getString(JSON_PROP_COMMTYPE));
				clientCommTotalsMap.put(markupJson.getString(JSON_PROP_COMMNAME), tempJson);

				clientTotalEntityArray.put(tempJson);
				paxLevelClientCommArr.put(tempJson);

			}

			// totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode,
			// calculatedAmt.multiply(paxCount));
			totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode, totalFare.multiply(new BigDecimal(1)));

			// If amount of receivables group is greater than zero, then append to
			// commercial prices
			if (paxReceivablesCompsGroup != null
					&& paxReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
				calculatedAmt = calculatedAmt.add(paxReceivablesCompsGroup.getComponentAmount());
				totalFareCompsGroup.addSubComponent(totalPaxReceivablesCompsGroup, null);
				paxClientFareJson.put(JSON_PROP_RECEIVABLES, paxReceivablesCompsGroup.toJSON());
			}
			totalFareJson.put(JSON_PROP_AMOUNT, totalFare);
			totalFareJson.put(JSON_PROP_CCYCODE, totalFareCurrency);
			paxClientFareJson.put(JSON_PROP_TOTALFARE, totalFareJson);// total added at pax level
			paxClientFareJson.put(JSON_PROP_SEATNO, passJson.getString(JSON_PROP_SEATNO));// calculation as per seat

			// clientcomm at pax level clientEntityDetailsJson
			JSONArray clientEntityCommArr = new JSONArray();
			JSONObject paxcliententitydtlsJson = new JSONObject();
			ClientInfo[] clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchyArray();
			paxcliententitydtlsJson.put(JSON_PROP_CLIENTID, clientEntityDetailsArr[k].getClientId());
			paxcliententitydtlsJson.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsArr[k].getParentClienttId());
			paxcliententitydtlsJson.put(JSON_PROP_COMMENTITYTYPE, clientEntityDetailsArr[k].getCommercialsEntityType());
			paxcliententitydtlsJson.put(JSON_PROP_COMMENTITYID, clientEntityDetailsArr[k].getCommercialsEntityId());
			paxcliententitydtlsJson.put(JSON_PROP_CLIENTCOMM, paxLevelClientCommArr);
			clientEntityCommArr.put(paxcliententitydtlsJson);
			paxClientFareJson.put(JSON_PROP_CLIENTENTITYCOMMS, clientEntityCommArr);
			paxClientFareJson.put(JSON_PROP_BASEFARE, totalFareJson);
			paxClientFaresArr.put(paxClientFareJson);
			StringBuilder key = new StringBuilder();
			key.append(clientEntityDetailsArr[k].getClientId());
			key.append(clientEntityDetailsArr[k].getParentClienttId());
			key.append(clientEntityDetailsArr[k].getCommercialsEntityType());
			key.append(clientEntityDetailsArr[k].getCommercialsEntityId());
			entityMap.put(key.toString(), clientTotalEntityArray);

			if (k == (entityCommArr.length() - 1)) {
				for (int x = 0; x < clientTotalEntityArray.length(); x++) {
					JSONObject clientEntityCommercialsJson = clientTotalEntityArray.getJSONObject(x);
					if (COMM_TYPE_PAYABLE.equals(clientEntityCommercialsJson.getString(JSON_PROP_COMMTYPE))) {
						String commCcy = clientEntityCommercialsJson.getString(JSON_PROP_COMMCCY);
						String commName = clientEntityCommercialsJson.getString(JSON_PROP_COMMNAME);
						BigDecimal commAmt = clientEntityCommercialsJson.getBigDecimal(JSON_PROP_COMMAMOUNT)
								.multiply(RedisRoEData.getRateOfExchange(commCcy, clientCcyCode, clientMarket))
								.multiply(paxCount, MATH_CTX_2_HALF_UP);
						totalIncentivesGroup.add(String.format(mIncvPriceCompFormat, commName), clientCcyCode, commAmt);
					}

				}
			}

		}

	}

	private static void calculateSuppCommercials(JSONArray orderlevelCommArr, BigDecimal clientcommercialAmount,
			JSONObject orderLevelJson, Map<String, JSONObject> suppCommTotalsMap, JSONArray commdetailsArr,
			JSONArray paxLevelSupplierCommArr, JSONArray paxSupplierFaresArr, JSONObject paxSupplierFareJson,
			JSONObject passJson, JSONObject supplierFareJson) {

		for (int j = 0; j < commdetailsArr.length(); j++) {
			JSONObject suppCommTotalsJson = null;
			JSONObject mapObj = null;
			JSONObject paxTempJson = new JSONObject();
			JSONObject commJson = commdetailsArr.getJSONObject(j);
			BigDecimal calculatedAmt = new BigDecimal(0);
			BigDecimal commercialAmount = new BigDecimal(0);
			if (suppCommTotalsMap.containsKey(commJson.getString(JSON_PROP_COMMNAME))) {
				orderLevelJson = new JSONObject();
				mapObj = suppCommTotalsMap.get(commJson.getString(JSON_PROP_COMMNAME));
				suppCommTotalsJson = new JSONObject(mapObj.toString());
				commercialAmount = commJson.getBigDecimal(JSON_PROP_COMMAMOUNT);
				calculatedAmt = commercialAmount.add(suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
				suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, calculatedAmt);
				suppCommTotalsMap.put(commJson.getString(JSON_PROP_COMMNAME), suppCommTotalsJson);

				paxTempJson.put(JSON_PROP_COMMNAME, commJson.getString(JSON_PROP_COMMNAME));
				paxTempJson.put(JSON_PROP_COMMCURRENCY, commJson.getString(JSON_PROP_COMMCURRENCY));
				paxTempJson.put(JSON_PROP_COMMTYPE, commJson.getString(JSON_PROP_COMMTYPE));

				paxTempJson.put(JSON_PROP_COMMAMOUNT, commercialAmount);
				paxLevelSupplierCommArr.put(paxTempJson);

			} else {
				paxTempJson = new JSONObject();
				paxTempJson.put(JSON_PROP_COMMAMOUNT, commJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
				paxTempJson.put(JSON_PROP_COMMNAME, commJson.getString(JSON_PROP_COMMNAME));
				paxTempJson.put(JSON_PROP_COMMCURRENCY, commJson.getString(JSON_PROP_COMMCURRENCY));
				paxTempJson.put(JSON_PROP_COMMTYPE, commJson.getString(JSON_PROP_COMMTYPE));
				paxLevelSupplierCommArr.put(paxTempJson);
				suppCommTotalsMap.put(commJson.getString(JSON_PROP_COMMNAME), paxTempJson);
			}

		}
		JSONObject paxLevelFareJson = new JSONObject();
		JSONObject paxLevelTotalFareJson = new JSONObject();
		paxLevelFareJson.put(JSON_PROP_AMOUNT, supplierFareJson.getBigDecimal("fare"));
		paxLevelFareJson.put(JSON_PROP_CCYCODE, supplierFareJson.getString(JSON_PROP_CURRENCY));
		paxLevelTotalFareJson.put(JSON_PROP_AMOUNT, paxLevelFareJson.getBigDecimal(JSON_PROP_AMOUNT));
		paxLevelTotalFareJson.put(JSON_PROP_CCYCODE, paxLevelFareJson.getString(JSON_PROP_CCYCODE));
		paxLevelTotalFareJson.put(JSON_PROP_BASEFARE, paxLevelFareJson);

		paxSupplierFareJson.put(JSON_PROP_TOTALFARE, paxLevelTotalFareJson);// total added at pax level
		paxSupplierFareJson.put(JSON_PROP_SEATNO, passJson.getString(JSON_PROP_SEATNO));// calculation as per seat
		paxSupplierFareJson.put(JSON_PROP_SUPPCOMM, paxLevelSupplierCommArr);
		paxSupplierFaresArr.put(paxSupplierFareJson);

	}

	private static void getBookJSON(Element resBodyElem, JSONArray bookTktJsonArr) {

		JSONObject bookTktJson = new JSONObject();
		if (XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:Errors") != null) {
			Element errorMessage = XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:Errors/ota:Error");
			String errMsgStr = XMLUtils.getValueAtXPath(errorMessage, "./@ShortText");
			bookTktJson.put("errorMessage", errMsgStr);
		} else {
			bookTktJson.put(JSON_PROP_SUPPREF,
					XMLUtils.getValueAtXPath((Element) resBodyElem.getParentNode(), "./bus:SupplierID"));
			bookTktJson.put(JSON_PROP_STATUS, XMLUtils.getValueAtXPath(resBodyElem, "./ota:Response/ota:IsSuccess"));
			bookTktJson.put("message", XMLUtils.getValueAtXPath(resBodyElem, "./ota:Response/ota:Message"));
			bookTktJson.put(JSON_PROP_PNRNO, XMLUtils.getValueAtXPath(resBodyElem, "./ota:BookSeatRS/ota:PNRNo"));
			bookTktJson.put(JSON_PROP_TICKETNO, XMLUtils.getValueAtXPath(resBodyElem, "./ota:BookSeatRS/ota:TicketNo"));
			bookTktJson.put("transactionId",
					XMLUtils.getValueAtXPath(resBodyElem, "./ota:BookSeatRS/ota:TransactionId"));
			bookTktJson.put(JSON_PROP_JOURNEYDATE,
					XMLUtils.getValueAtXPath(resBodyElem, "./ota:BookSeatRS/ota:DateOfJourney"));
			bookTktJson.put(JSON_PROP_TOTALFARE,
					XMLUtils.getValueAtXPath(resBodyElem, "./ota:BookSeatRS/ota:TotalFare"));
			bookTktJson.put(JSON_PROP_CURRENCY, XMLUtils.getValueAtXPath(resBodyElem, "./ota:BookSeatRS/ota:Currency"));
			JSONArray passArr = new JSONArray();
			Element[] passangerElems = XMLUtils.getElementsAtXPath(resBodyElem,
					"./ota:BookSeatRS/ota:Passengers/ota:Passenger");
			for (Element passElem : passangerElems) {
				getPassangers(passElem, passArr);
			}

			bookTktJson.put("passengers", passArr);
		}

		bookTktJsonArr.put(bookTktJson);
	}

	public static void getPassangers(Element passElem, JSONArray passArr) {

		JSONObject passJson = new JSONObject();
		passJson.put(JSON_PROP_FIRSTNAME, XMLUtils.getValueAtXPath(passElem, "./ota:Name"));
		passJson.put("age", Utils.convertToInt(XMLUtils.getValueAtXPath(passElem, "./ota:Age").trim(), 0));
		passJson.put("gender", XMLUtils.getValueAtXPath(passElem, "./ota:Gender"));
		passJson.put(JSON_PROP_SEATNO, XMLUtils.getValueAtXPath(passElem, "./ota:SeatNo"));
		passJson.put("fare", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(passElem, "./ota:Fare").trim(), 0));
		passJson.put(JSON_PROP_CURRENCY, XMLUtils.getValueAtXPath(passElem, "./ota:Currency"));
		passJson.put("seatType", XMLUtils.getValueAtXPath(passElem, "./ota:SeatType"));
		passJson.put("isAcSeat", Boolean.valueOf(XMLUtils.getValueAtXPath(passElem, "./ota:IsAcSeat")));
		passArr.put(passJson);

	}

}
