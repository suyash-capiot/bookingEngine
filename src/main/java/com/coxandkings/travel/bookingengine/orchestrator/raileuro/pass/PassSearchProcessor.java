package com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.raileuro.RailEuroConfig;
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.PriceComponentsGroup;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.RailEuropeConstants;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class PassSearchProcessor implements RailEuropeConstants{

	private static final Logger logger = LogManager.getLogger(PassSearchProcessor.class);
	private static String mIncvPriceCompFormat = JSON_PROP_INCENTIVE.concat(".%s@").concat(JSON_PROP_CODE);

	public static String process(JSONObject reqJson) {
		try {
//			 RailEuroConfig yet to define
			//OperationConfig opConfig = RailEuroConfig.getOperationConfig("search");
			ServiceConfig opConfig = RailEuroConfig.getOperationConfig("search");
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			TrackingContext.setTrackingContext(reqJson);
			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
//			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			UserContext usrContxt = UserContext.getUserContextForSession(reqHdrJson);
			

			createHeader(reqElem, reqHdrJson);

//			getSupplierCredentials(reqElem, ownerDoc, usrContxt);
			
			List<ProductSupplier> productSuppliers = usrContxt.getSuppliersForProduct(PROD_CATEG_TRANSPORT,PROD_CATEG_SUBTYPE_RAILEURO);
			if (productSuppliers == null || productSuppliers.size()==0) {
				OperationsToDoProcessor.callOperationTodo(ToDoTaskName.SEARCH, ToDoTaskPriority.HIGH, ToDoTaskSubType.ORDER, "", "", reqHdrJson,"Product supplier not found for Rail Euro Pass");	
				throw new Exception("Product supplier not found for user/client");
			}

			Element suppCredsList = XMLUtils.getFirstElementAtXPath(reqElem,"./wwr:RequestHeader/com:SupplierCredentialsList");
			for (ProductSupplier prodSupplier : productSuppliers) {
				suppCredsList.appendChild(prodSupplier.toElement(ownerDoc));
			}
			
			JSONArray prodSuppsJsonArr = Utils.convertProdSuppliersToCommercialCache(productSuppliers.get(0));
			
			try { 

				ClientInfo[] clInfo = usrContxt.getClientCommercialsHierarchyArray();
				String commCacheRes = CommercialCacheProcessor.getFromCache("EURORAIL", clInfo[clInfo.length - 1].getCommercialsEntityId(), prodSuppsJsonArr, reqJson);
				

				if (commCacheRes != null && (commCacheRes.equals("error")) == false) {
					pushSuppFaresToRedisAndRemove(new JSONObject(new JSONTokener(commCacheRes)));
					return commCacheRes;
				}
			}

			catch (Exception x) {
				logger.info("An exception was received while retrieving search results from commercial cache", x);
			}
			
			Element railShopRQ = XMLUtils.getFirstElementAtXPath(reqElem, "./wwr1:RequestBody/ota:OTA_WWRailShopRQ");
			railShopRQ.setAttribute(JSON_PROP_PRODTYPE, reqBodyJson.getJSONObject(JSON_PROP_RAILPASS).optString(JSON_PROP_PRODTYPE));
			String productType = reqBodyJson.getJSONObject(JSON_PROP_RAILPASS).optString(JSON_PROP_PRODTYPE);

			// -=-=--=-=-=-=-=-> check NonEuro or Euro

			if (productType.equals(JSON_PROP_PRODTYPE_PASS)) {
				Element passProd = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PassProducts");
				Element familyID = ownerDoc.createElementNS(Constants.NS_OTA, "ota:FamilyId");
				familyID.setTextContent(reqBodyJson.getJSONObject(JSON_PROP_RAILPASS).getString(JSON_PROP_FAMILYID));
				passProd.appendChild(familyID);
				railShopRQ.appendChild(passProd);
			} else {
				Element neroProd = ownerDoc.createElementNS(Constants.NS_OTA, "ota:NeroProducts");

				Element neroPassProd = ownerDoc.createElementNS(Constants.NS_OTA, "ota:NeroPassProducts");
				neroProd.appendChild(neroPassProd);
				Element itemID = ownerDoc.createElementNS(Constants.NS_OTA, "ota:ItemId");
				itemID.setTextContent(reqBodyJson.getJSONObject(JSON_PROP_RAILPASS).getString(JSON_PROP_ITEMID));
				neroPassProd.appendChild(itemID);
				Element retrieveDeposit = ownerDoc.createElementNS(Constants.NS_OTA, "ota:RetrieveDeposit");
				Boolean retDep = reqBodyJson.optBoolean(JSON_PROP_RETRIEVEDEPOSIT);
				retrieveDeposit.setTextContent(retDep.toString());
				neroPassProd.appendChild(retrieveDeposit);
				railShopRQ.appendChild(neroProd);

			}

			logger.info("Before opening HttpURLConnection to SI");
			Element resElem = null;
//			System.out.println(XMLTransformer.toString(reqElem));
//			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(),
//					RailEuroConfig.getHttpHeaders(), reqElem);
			//resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), RailEuroConfig.getHttpHeaders(), "POST", 120000, reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), "POST", 120000, reqElem);
			logger.info("RailEuroPASS SI RESPONSE"+resElem);
//			 System.out.println("SI Response" + XMLTransformer.toString(resElem));

			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			logger.info("HttpURLConnection to SI closed");
			JSONObject resJson;
			resJson = getSupplierResponseJSON(reqJson, resElem);
//			System.out.println(resJson);
//			logger.info("SI res: " + resJson);
//			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
			
//			 Call CCE SC

			JSONObject resSupplierJson = SupplierCommercials.getSupplierCommercials(CommercialsOperation.Search, reqJson, resJson);
//			System.out.println(resSupplierJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s",
						resSupplierJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}
			
			
			JSONObject resClientJson = ClientCommercials.getClientCommercials(reqJson, resJson, resSupplierJson);
//			System.out.println(resClientJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s",
								resClientJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}
			
			calculatePrices(reqJson, resJson, resSupplierJson, resClientJson,usrContxt);
			
			TaxEngine.getCompanyTaxes(reqJson, resJson);

			try {

				ClientInfo[] clInfo = usrContxt.getClientCommercialsHierarchyArray();

				CommercialCacheProcessor.putInCache("EURORAIL", clInfo[clInfo.length - 1].getCommercialsEntityId(), resJson, reqJson);

			}

			catch (Exception x) {

				logger.info("An exception was received while pushing search results in commercial cache", x);

			}

			pushSuppFaresToRedisAndRemove(resJson);
			//System.out.println(resJson);
			return resJson.toString();

		} catch (Exception x) {
			 logger.error("Exception received while processing", x);
			x.printStackTrace();
			return "{\"status\": \"ERROR\"}";
		}
	}



	public static void createHeader(Element reqElem, JSONObject reqHdrJson) {
		String sessionId = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionId = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userId = reqHdrJson.getString(JSON_PROP_USERID);
		XMLUtils.setValueAtXPath(reqElem, "./wwr:RequestHeader/com:SessionID", sessionId);
		XMLUtils.setValueAtXPath(reqElem, "./wwr:RequestHeader/com:TransactionID", transactionId);
		XMLUtils.setValueAtXPath(reqElem, "./wwr:RequestHeader/com:UserID", userId);
	}

	

	public static JSONObject getSupplierResponseJSON(JSONObject reqJson, Element resElem) {
		JSONObject resJson = new JSONObject();
		Element resBodyElem = XMLUtils.getFirstElementAtXPath(resElem,
				"./wwr1:ResponseBody/wwr:OTA_WWRailShopRSWrapper");
		JSONObject resBodyJson = new JSONObject();
		JSONArray passProduct = new JSONArray();
		resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		// resBodyJson.put("PassProduct", passProduct);
		// Element[] passProductElement = XMLUtils.getElementsAtXPath(resBodyElem,
		// "./ota:OTA_WWRailShopRS/ota:Success/ota:Content/ota:PassProductContent/ota:PassProducts/ota:PassProduct");
		getPassProduct(resBodyElem, passProduct);
		resBodyJson.put(JSON_PROP_PASSPROD, passProduct);
//		PrintWriter pw = new PrintWriter(new File("D:\\abc.json"));
//		pw.write(resJson.toString(4));
//		pw.close();
//		System.out.println(XMLTransformer.toString(resBodyElem));
		return resJson;
	}

	
	public static void getPassProduct(Element resBodyElem, JSONArray passProduct) {
		JSONObject resBodyJson;
		Element[] passProductElement = XMLUtils.getElementsAtXPath(resBodyElem,
				"./ota:OTA_WWRailShopRS/ota:Content/ota:PassProductContent/ota:PassProducts/ota:PassProduct");
		// System.out.println(XMLTransformer.toString(passProductElement));
		for (Element passProdElem : passProductElement) {

			resBodyJson = new JSONObject();
			resBodyJson.put(JSON_PROP_PRODUCTID, XMLUtils.getValueAtXPath(passProdElem, "./ota:ProductId"));
			
			// supplierRef
				//resBodyJson.put("supplierRef", XMLUtils.getValueAtXPath(resBodyElem, "./wwr:SupplierID"));
			//supplierRef
			
			resBodyJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(resBodyElem, "./wwr:SupplierID"));
			resBodyJson.put(JSON_PROP_FAMILYNAME, XMLUtils.getValueAtXPath(passProdElem, "./ota:FamilyName"));
			resBodyJson.put(JSON_PROP_QUALITYENABLED, XMLUtils.getValueAtXPath(passProdElem, "./ota:QuantityEnabled"));
			resBodyJson.put(JSON_PROP_PRODNAME, XMLUtils.getValueAtXPath(passProdElem, "./ota:ProductName"));
			resBodyJson.put(JSON_PROP_PASSTYPE, XMLUtils.getValueAtXPath(passProdElem, "./ota:PassType"));
			resBodyJson.put(JSON_PROP_TRAVELPERIOD, XMLUtils.getValueAtXPath(passProdElem, "./ota:Duration/ota:TravelPeriod"));
			resBodyJson.put(JSON_PROP_VALIDITYPERIOD,
					XMLUtils.getValueAtXPath(passProdElem, "./ota:Duration/ota:ValidityPeriod"));
			resBodyJson.put(JSON_PROP_SEATCLASS, XMLUtils.getValueAtXPath(passProdElem, "./ota:PhysicalClassOfService"));
			resBodyJson.put(JSON_PROP_FIRSTTRAVELDATECHECK, XMLUtils.getValueAtXPath(passProdElem, "./ota:NeedFirstTravelDate"));
			resBodyJson.put(JSON_PROP_TnC, XMLUtils.getValueAtXPath(passProdElem, "./ota:TermsAndConditions"));
			resBodyJson.put(JSON_PROP_INTERRAILPASS_CHECK, XMLUtils.getValueAtXPath(passProdElem, "./ota:IsInterrailPass"));
			resBodyJson.put("numberOfMandatorySelectedCountries",
					XMLUtils.getValueAtXPath(passProdElem, "./ota:NMandatorySelectedCountries"));
			

			String nFreeChildren = XMLUtils.getValueAtXPath(passProdElem, "./ota:NFreeChildren");
			Element[] passengerTypeDetails = XMLUtils.getElementsAtXPath(passProdElem, "./ota:PassengerTypeDetails");
			if(passengerTypeDetails!=null)
			{
				Element passengerTypeDetail = XMLUtils.getFirstElementAtXPath(passProdElem,
						"./ota:PassengerTypeDetails/ota:PassengerTypeDetail");
				if(passengerTypeDetail!=null)
				{
					getPaxInfo(resBodyJson, passProdElem, passengerTypeDetail);
				}

			}
			
			getPricingInfo(resBodyJson, passProdElem);
//			if (nFreeChildren.equals("0")) {
//				
//				// =-=-=-=-=-=-=-=-ADDITIONAL COUNTRIES CODE APPEND
//			}

//			getPricingInfo(resBodyJson, passProdElem, passengerTypeDetail);

			// =-=-=--= APPEND COMMISSION PERCENTAGE (OPTIONAL)

			JSONArray countriesCoveredArr = getCountriesCovered(passProdElem);
			resBodyJson.put(JSON_PROP_COUNTRIESCOVERED, countriesCoveredArr);

			passProduct.put(resBodyJson);
		}
	}

	
	public static JSONArray getCountriesCovered(Element passProdElem) {
		Element[] countriesCovered = XMLUtils.getElementsAtXPath(passProdElem,
				"./ota:CountriesCovered/ota:Country");
		JSONArray countriesCoveredArr = new JSONArray();
		for (Element country : countriesCovered) {
			JSONObject countriesCoveredObj = new JSONObject();
			countriesCoveredObj.put(JSON_PROP_COUNTRYNAME, XMLUtils.getValueAtXPath(country, "./ota:Name"));
			countriesCoveredObj.put(JSON_PROP_COUNTRYCODE, XMLUtils.getValueAtXPath(country, "./ota:Code"));
			countriesCoveredArr.put(countriesCoveredObj);
		}
		return countriesCoveredArr;
	}

	
	public static void getPaxInfo(JSONObject resBodyJson, Element passProdElem, Element passengerTypeDetail) {
		JSONObject paxInfo = new JSONObject();
		String paxType = XMLUtils.getValueAtXPath(passengerTypeDetail, "./ota:PassengerType");
		String countOfPassengers = countOfPax(passProdElem, paxType);
		paxInfo.put(JSON_PROP_PAXTYPE_DEF,
				XMLUtils.getValueAtXPath(passProdElem, "./ota:DefaultPassengerType"));
		paxInfo.put(JSON_PROP_PAXTYPE, XMLUtils.getValueAtXPath(passengerTypeDetail, "./ota:PassengerType"));
		paxInfo.put(JSON_PROP_PAX_MINAGE,
				XMLUtils.getValueAtXPath(passengerTypeDetail, "./ota:AgeRange/ota:Low"));
		paxInfo.put(JSON_PROP_PAX_MAXAGE,
				XMLUtils.getValueAtXPath(passengerTypeDetail, "./ota:AgeRange/ota:High"));
		paxInfo.put(JSON_PROP_PAXTYPE, paxType);
		resBodyJson.put(JSON_PROP_COUNT, countOfPassengers);
		resBodyJson.put(JSON_PROP_PAXINFO, paxInfo);
	}

	
	public static void getPricingInfo(JSONObject resBodyJson, Element passProdElem) {
		Element pricingInfo = XMLUtils.getFirstElementAtXPath(passProdElem, "./ota:Price");
		JSONObject priceInfo = new JSONObject();
		JSONObject totalFare = new JSONObject();
		totalFare.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(pricingInfo, "./ota:SellingPrice/ota:Amount"));
		
		//no need for paxType as there will always be only one passenger type per pass
//		passTypeFare.put("passengerType", XMLUtils.getValueAtXPath(passengerTypeDetail, "./ota:PassengerType"));
		
//		priceInfo.put("amount", XMLUtils.getValueAtXPath(pricingInfo, "./ota:SellingPrice/ota:Amount"));
		
		totalFare.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(pricingInfo, "./ota:SellingPrice/ota:CurrencyCode"));
//		passTypeFareDetails.put(passTypeFare);
//		priceInfo.put("paxTypeFareDetails", passTypeFare);
		priceInfo.put(JSON_PROP_TOTALFARE, totalFare);
		resBodyJson.put(JSON_PROP_PRICINGINFO, priceInfo);
	}


	public static String countOfPax(Element passProducts, String passengerType) {
		String count = null;
		switch (passengerType) {
		case JSON_PROP_PAXTYPE_YOUTH: {
			count = XMLUtils.getValueAtXPath(passProducts, "./ota:NYouths");
			break;
		}
		case JSON_PROP_PAXTYPE_ADULT: {
			count = XMLUtils.getValueAtXPath(passProducts, "./ota:NAdults");
			break;
		}
		case JSON_PROP_PAXTYPE_SENIOR: {
			count = XMLUtils.getValueAtXPath(passProducts, "./ota:NSenior");
			break;
		}
		case JSON_PROP_PAXTYPE_CHILD: {
			count = XMLUtils.getValueAtXPath(passProducts, "./ota:NChildren");
			break;
		}
		default:
			System.out.println("default of countOfPax" + passengerType);

		}
		return count;
	}
	
	 
	private static void appendSupplierCommercialsForRail(JSONObject suppPricingInfo, JSONObject suppTotalFareJson, JSONObject ccommTrainPsgrDtlJson,
				String suppID, String suppCcyCode, Map<String, String> suppCommToTypeMap) {
			
			JSONArray suppCommJsonArr = new JSONArray();
			JSONArray ccommSuppCommJsonArr = ccommTrainPsgrDtlJson.optJSONArray(JSON_PROP_COMMDETAILS);
			// If no supplier commercials have been defined in BRMS, the JSONArray for ccommSuppCommJsonArr will be null.
			// In this case, log a message and proceed with other calculations.
			if (ccommSuppCommJsonArr == null) {
				logger.warn(String.format("No supplier commercials found for supplier %s", suppID));
				return;
			} 
			
			for (int x = 0; x < ccommSuppCommJsonArr.length(); x++) {
				String temp;
				JSONObject ccommSuppCommJson = ccommSuppCommJsonArr.getJSONObject(x);
				JSONObject suppCommJson = new JSONObject();
				suppCommJson.put(JSON_PROP_COMMNAME, ccommSuppCommJson.getString(JSON_PROP_COMMNAME));
				suppCommJson.put(JSON_PROP_COMMTYPE,
						suppCommToTypeMap.get(ccommSuppCommJson.getString(JSON_PROP_COMMNAME)));
				suppCommJson.put(JSON_PROP_COMMAMOUNT, ccommSuppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
				suppCommJson.put(JSON_PROP_COMMCCY, (temp = ccommSuppCommJson.optString(JSON_PROP_COMMCCY)).equals("") ? suppCcyCode : temp);
				suppCommJsonArr.put(suppCommJson);
			}
			suppPricingInfo.put(JSON_PROP_SUPPCOMM, suppCommJsonArr);
		}
	 
	 
	private static JSONObject convertToClientEntityCommercialJson(JSONObject clientCommJson, Map<String,String> clntCommToTypeMap, String suppCcyCode) {
	    	JSONObject clientCommercial= new JSONObject();
	    	String commercialName = clientCommJson.getString(JSON_PROP_COMMNAME);
			clientCommercial.put(JSON_PROP_COMMTYPE, clntCommToTypeMap.getOrDefault(commercialName, "?"));
			clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
			clientCommercial.put(JSON_PROP_COMMNAME, clientCommJson.getString(JSON_PROP_COMMNAME));
			clientCommercial.put(JSON_PROP_COMMCCY, clientCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
	    	return clientCommercial;
	    }
	 
	
	public static void calculatePrices(JSONObject reqJson, JSONObject resJson, JSONObject suppCommResJson,
			 JSONObject clientCommResJson,
			 UserContext usrCtx) {

		 JSONArray passProductsArr = resJson.getJSONObject(JSON_PROP_RESBODY).optJSONArray(JSON_PROP_PASSPROD);
		 Map<String, String> suppCommToTypeMap = getSupplierCommercialsAndTheirType(suppCommResJson);
		 Map<String, String> clntCommToTypeMap = getClientCommercialsAndTheirType(clientCommResJson);

		 ClientType clientType = ClientType.valueOf(reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
		 String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		 String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);

		 for (int i = 0; i < passProductsArr.length(); i++) {
//			 System.out.println(passProductsArr.length());
//			 System.out.println(passProductsArr);
			 JSONObject passProducts = passProductsArr.getJSONObject(i);
//			 System.out.println(passProducts);


			 JSONObject pricingInfoJson = passProducts.getJSONObject(JSON_PROP_PRICINGINFO);
			 JSONObject totalFareJson = pricingInfoJson.getJSONObject(JSON_PROP_TOTALFARE);
			 String suppCcyCode = totalFareJson.getString(JSON_PROP_CCYCODE);
			 JSONObject suppTotalFareJson = new JSONObject(totalFareJson.toString());



			 String suppID = passProducts.getString(JSON_PROP_SUPPREF);

			 JSONArray ccommBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
			 for(int a=0;a<ccommBRIJsonArr.length();a++)
			 {
				 JSONObject ccommBRIJsonObj = ccommBRIJsonArr.getJSONObject(a);
				 JSONArray ccommTrainDetailsJson = ccommBRIJsonObj.optJSONArray(JSON_PROP_TRAINDETAILS);
//				 System.out.println(ccommTrainDetailsJson.toString());
				 if (ccommTrainDetailsJson == null) {
					 logger.info(String.format("BRMS/ClientComm: \"businessRuleIntake\" no train details found"));
				 }

			
					 
//					 System.out.println(ccommTrainDetailsJson.length());
					 JSONObject ccommTrnDtlsJsonObj = ccommTrainDetailsJson.getJSONObject(i);
					 //-=-=--==-=-=-=-=-=-=-=-=-=-=-
					 PriceComponentsGroup totalFareCompsGroup = new PriceComponentsGroup(JSON_PROP_TOTALFARE, clientCcyCode, new BigDecimal(0), true);
					 PriceComponentsGroup totalIncentivesGroup = new PriceComponentsGroup(JSON_PROP_INCENTIVES, clientCcyCode, BigDecimal.ZERO, true);
					 JSONObject ccommTrainPsgrDtlJson = new JSONObject();
					 //-=-=-=-=-=-=-=-=-=-=-=-=--=-
					 if(ccommTrnDtlsJsonObj.optJSONArray(JSON_PROP_PASSENGERDET)!=null)
					   ccommTrainPsgrDtlJson = ccommTrnDtlsJsonObj.optJSONArray(JSON_PROP_PASSENGERDET).optJSONObject(0);

					 JSONArray clientEntityCommJsonArr = null;
					 //if freeChild, paxDetails will be null
					 if (ccommTrainPsgrDtlJson == null) {
						 logger.info(String.format("Passenger details not found in client commercial trainDetails"));
					 }

					 else {
						 JSONObject suppPricingInfo = new JSONObject();
						 suppPricingInfo.put(JSON_PROP_TOTALFARE, suppTotalFareJson);
						 appendSupplierCommercialsForRail(suppPricingInfo, suppTotalFareJson, ccommTrainPsgrDtlJson, suppID, suppCcyCode, suppCommToTypeMap);
//						 System.out.println(passProducts.toString());
						 passProducts.put(JSON_PROP_SUPPPRICINFO, suppPricingInfo);
//						 System.out.println(passProducts.toString());

						 clientEntityCommJsonArr = ccommTrainPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
						 if (clientEntityCommJsonArr == null) {
							 logger.warn("Client commercials calculations not found");
						 }
					 }


					 JSONObject markupCalcJson = null;
					 JSONArray clientCommercials= new JSONArray();
					 PriceComponentsGroup totalReceivablesCompsGroup = null;
					 for (int k = 0; clientEntityCommJsonArr != null && k < clientEntityCommJsonArr.length(); k++)
					 {
						 JSONArray clientEntityCommercialsJsonArr=new JSONArray();
						 JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(k);

						 if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) {
							 markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);

							 clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(markupCalcJson, clntCommToTypeMap, suppCcyCode));
						 }

						 JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
						 JSONArray retentionCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_RETENTIONCOMMDETAILS);
						 JSONArray fixedCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_FIXEDCOMMDETAILS);


						 totalReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode, new BigDecimal(0), true);
						 if (additionalCommsJsonArr != null) {
							 for (int p = 0; p < additionalCommsJsonArr.length(); p++) {
								 JSONObject additionalCommsJson = additionalCommsJsonArr.getJSONObject(p);
								 String additionalCommName = additionalCommsJson.optString(JSON_PROP_COMMNAME);
								 String additionalCommType = clntCommToTypeMap.get(additionalCommName);
								 clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(additionalCommsJson, clntCommToTypeMap, suppCcyCode));

								 if (COMM_TYPE_RECEIVABLE.equals(additionalCommType)) {
									 String additionalCommCcy = additionalCommsJson.optString(JSON_PROP_COMMCCY);
									 BigDecimal additionalCommAmt = additionalCommsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket));
									 totalReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt);
								 }
							 }
						 }

						 //Retention
						 for (int p = 0; retentionCommsJsonArr!=null && p < retentionCommsJsonArr.length(); p++) {
							 JSONObject retentionCommsJson = retentionCommsJsonArr.getJSONObject(p);
							 clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(retentionCommsJson, clntCommToTypeMap, suppCcyCode));
						 }

						 //Fixed
						 for (int p = 0; fixedCommsJsonArr!=null && p < fixedCommsJsonArr.length(); p++) {
							 JSONObject fixedCommsJson = fixedCommsJsonArr.getJSONObject(p);
							 clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(fixedCommsJson, clntCommToTypeMap, suppCcyCode));
						 }


						 JSONObject clientEntityDetailsJson = new JSONObject();
						 ClientInfo[] clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchyArray();
						 clientEntityDetailsJson.put(JSON_PROP_CLIENTID, clientEntityDetailsArr[k].getClientId());
						 clientEntityDetailsJson.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsArr[k].getParentClienttId());
						 clientEntityDetailsJson.put(JSON_PROP_COMMENTITYTYPE, clientEntityDetailsArr[k].getCommercialsEntityType());
						 clientEntityDetailsJson.put(JSON_PROP_COMMENTITYID, clientEntityDetailsArr[k].getCommercialsEntityId());
						 clientEntityDetailsJson.put(JSON_PROP_CLIENTCOMMTOTAL, clientEntityCommercialsJsonArr);
						 clientCommercials.put(clientEntityDetailsJson);

						 if (k == (clientEntityCommJsonArr.length() - 1)) {
							 for (int x = 0; x < clientEntityCommercialsJsonArr.length(); x++) {
								 JSONObject clientEntityCommercialsJson = clientEntityCommercialsJsonArr.getJSONObject(x);
								 if (COMM_TYPE_PAYABLE.equals(clientEntityCommercialsJson.getString(JSON_PROP_COMMTYPE))) {
									 String commCcy = clientEntityCommercialsJson.getString(JSON_PROP_COMMCCY);
									 String commName = clientEntityCommercialsJson.getString(JSON_PROP_COMMNAME);
									 BigDecimal commAmt = clientEntityCommercialsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(commCcy, clientCcyCode, clientMarket));
									 //for B2B add incentives in totalIncentivesGroup
									 totalIncentivesGroup.add(String.format(mIncvPriceCompFormat, commName), clientCcyCode, commAmt);
								 }
							 }
						 }
					 }
					 
					 
					 BigDecimal totalFareAmt = suppTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
//					 System.out.println(suppTotalFareJson);
						
						if (markupCalcJson != null) {totalFareAmt = markupCalcJson.optBigDecimal(JSON_PROP_TOTALFARE, totalFareAmt);}
						
						JSONObject baseFareJson = new JSONObject();
						baseFareJson.put(JSON_PROP_AMOUNT, totalFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
						baseFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);
						
						totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode, baseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
//						System.out.println(totalFareCompsGroup.toJSON());

					 

					 if (totalReceivablesCompsGroup != null && totalReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
						 totalFareCompsGroup.addSubComponent(totalReceivablesCompsGroup, null);
					 }

					 pricingInfoJson.put(JSON_PROP_TOTALFARE, totalFareCompsGroup.toJSON());
				

					 if ( clientType == ClientType.B2B) {
						 pricingInfoJson.put(JSON_PROP_INCENTIVES, totalIncentivesGroup.toJSON());
					 }

					 pricingInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercials);

				 }
			 
		 }
//		 logger.trace(String.format("supplierResponse after supplierItinFare = %s", resJson.toString()));

	 }
	 
	 
	 private static void pushSuppFaresToRedisAndRemove(JSONObject resJson) {
			
			JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
			JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
			JSONArray passProductsArr = resBodyJson.optJSONArray(JSON_PROP_PASSPROD);
			
			Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();
				for (int i=0; i < passProductsArr.length(); i++) {
					JSONObject suppPriceBookInfoJson = new JSONObject();
					JSONObject passProduct = passProductsArr.getJSONObject(i);
					//take this passProduct for map
					JSONObject suppPriceInfoJson = passProduct.optJSONObject(JSON_PROP_SUPPPRICINFO);
					passProduct.remove(JSON_PROP_SUPPPRICINFO);
					
					//Getting ClientCommercials Info
					JSONObject totalPriceInfo = passProduct.getJSONObject(JSON_PROP_PRICINGINFO);
					JSONArray clientCommercialsTotalJsonArr = totalPriceInfo.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
					totalPriceInfo.remove(JSON_PROP_CLIENTENTITYTOTALCOMMS);
					
					if ( suppPriceInfoJson == null) {
						// TODO: This should never happen. Log a warning message here.
						continue;
					}
					suppPriceBookInfoJson.put(JSON_PROP_SUPPPRICINFO, suppPriceInfoJson);
					suppPriceBookInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercialsTotalJsonArr);
					reprcSuppFaresMap.put(getRedisKeyForPassProducts(passProduct), suppPriceBookInfoJson.toString());
				}
			
			try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
			String redisKey = String.format("%s%c%s", resHdrJson.optString(JSON_PROP_SESSIONID), '|', "EuropeanRail");
			redisConn.hmset(redisKey, reprcSuppFaresMap);
			redisConn.pexpire(redisKey, (long) (RailEuroConfig.getRedisTTLMinutes() * 60 * 1000));
			}
			//RedisConfig.releaseRedisConnectionToPool(redisConn);
		}
	 
	 
	 static String getRedisKeyForPassProducts(JSONObject passProduct) {
			
			List<String> keys = new ArrayList<>();
			String productID = passProduct.optString(JSON_PROP_PRODUCTID);
			keys.add(productID);
			String key = keys.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(Character.valueOf('|').toString()));
			return key;

		}
	
	
	 public static JSONObject getEmptyResponse(JSONObject reqHdrJson) {
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, new JSONObject());
		return resJson;
	}
	
	
	 private static Map<String, String> getSupplierCommercialsAndTheirType(JSONObject suppCommResJson) {
		JSONArray commHeadJsonArr = null;
		JSONObject commHeadJson = null;
		Map<String, String> commToTypeMap = new HashMap<String, String>();
		JSONArray scommBRIJsonArr = getSupplierCommercialsBusinessRuleIntakeJSONArray(suppCommResJson);
		for (int i = 0; i < scommBRIJsonArr.length(); i++) {
			if ((commHeadJsonArr = scommBRIJsonArr.getJSONObject(i).optJSONArray(JSON_PROP_COMMHEAD)) == null) {
				logger.warn("No commercial heads found in supplier commercials");
				continue;
			}

			for (int j = 0; j < commHeadJsonArr.length(); j++) {
				commHeadJson = commHeadJsonArr.getJSONObject(j);
				commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME),
						commHeadJson.getString(JSON_PROP_COMMTYPE));
			}
		}

		return commToTypeMap;
	}

	
	 private static Map<String, String> getClientCommercialsAndTheirType(JSONObject clientCommResJson) {
		JSONArray commHeadJsonArr = null;
		JSONObject commHeadJson = null;
		JSONArray entityDtlsJsonArr = null;
		Map<String, String> commToTypeMap = new HashMap<String, String>();
		JSONArray ccommBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
		for (int i = 0; i < ccommBRIJsonArr.length(); i++) {
			if ((entityDtlsJsonArr = ccommBRIJsonArr.getJSONObject(i).optJSONArray(JSON_PROP_ENTITYDETAILS)) == null) {
				continue;
			}
			for (int j = 0; j < entityDtlsJsonArr.length(); j++) {
				if ((commHeadJsonArr = entityDtlsJsonArr.getJSONObject(j).optJSONArray(JSON_PROP_COMMHEAD)) == null) {
					logger.warn("No commercial heads found in client commercials");
					continue;
				}

				for (int k = 0; k < commHeadJsonArr.length(); k++) {
					commHeadJson = commHeadJsonArr.getJSONObject(k);
					commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME),
							commHeadJson.getString(JSON_PROP_COMMTYPE));
				}
			}
		}
		return commToTypeMap;
	}
	 
	 public static void getSupplierCredentials(Element reqElem, Document ownerDoc, UserContext usrContxt) throws Exception {
			List<ProductSupplier> productSuppliers = usrContxt.getSuppliersForProduct(PROD_CATEG_TRANSPORT,
					PROD_CATEG_SUBTYPE_RAILEURO);
			Element suppCredsList = XMLUtils.getFirstElementAtXPath(reqElem,
					"./wwr:RequestHeader/com:SupplierCredentialsList");
			if (productSuppliers == null) {
				throw new Exception("Product supplier not found for user/client");
			}
			for (ProductSupplier prodSupplier : productSuppliers) {
				suppCredsList.appendChild(prodSupplier.toElement(ownerDoc));
			}
		}
	
	
	 static JSONArray getClientCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
		return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results")
				.getJSONObject(0).getJSONObject(JSON_PROP_VALUE)
				.getJSONObject("cnk.rail_commercialscalculationengine.clienttransactionalrules.Root")
				.getJSONArray(JSON_PROP_BUSSRULEINTAKE);
	}
	
	
	 static JSONArray getSupplierCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
		return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results")
				.getJSONObject(0).getJSONObject(JSON_PROP_VALUE)
				.getJSONObject("cnk.rail_commercialscalculationengine.suppliertransactionalrules.Root")
				.getJSONArray(JSON_PROP_BUSSRULEINTAKE);
	}
	
	

}
