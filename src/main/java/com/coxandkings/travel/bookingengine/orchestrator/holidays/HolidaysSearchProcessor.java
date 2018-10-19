package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.enums.PassengerType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class HolidaysSearchProcessor implements HolidayConstants {
    public static final Logger logger = LogManager.getLogger(HolidaysSearchProcessor.class);
    public static BigDecimal totalFare = new BigDecimal("0");
    public static String currencyCode = "" ;
    public static Boolean assignFlag = false;
    public static String mRcvsPriceCompQualifier = JSON_PROP_RECEIVABLES.concat(SIGMA).concat(".").concat(JSON_PROP_RECEIVABLE).concat(".");
	public static String mTaxesPriceCompQualifier = JSON_PROP_TAXES.concat(SIGMA).concat(".").concat(JSON_PROP_TAX).concat(".");
    public static String mIncvPriceCompFormat = JSON_PROP_INCENTIVE.concat(".%s@").concat(JSON_PROP_CODE);
    
    public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException, ValidationException {
    	Element reqElem = null;
        JSONObject reqHdrJson = null,reqBodyJson = null, resJson = null;
        //OperationConfig opConfig = null;
        ServiceConfig opConfig = null;
        UserContext usrCtx = null;
        JSONArray errorJsonArray = new JSONArray();
    	JSONObject resSupplierCommJson = new JSONObject();
    	JSONObject resClientCommJson = new JSONObject();
        
        try {
        	TrackingContext.setTrackingContext(reqJson);
        	opConfig = HolidaysConfig.getOperationConfig("search");
        	
        	reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null) ? reqJson.optJSONObject(JSON_PROP_REQHEADER) : new JSONObject();
            reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null) ? reqJson.optJSONObject(JSON_PROP_REQBODY) : new JSONObject();
        	usrCtx = UserContext.getUserContextForSession(reqHdrJson);
            
            validateRequestParameters(reqJson);
        	
            List<ProductSupplier> productSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_HOLIDAYS,PROD_CATEG_HOLIDAYS);
			if (productSuppliers == null) {
				throw new Exception("Product supplier not found for user/client");
			}
			ProductSupplier[] ProductsArr = new ProductSupplier[productSuppliers.size()];
			ProductsArr = productSuppliers.toArray(ProductsArr);
			
			JSONArray prodSuppsJsonArr = Utils.convertProdSuppliersToCommercialCache(ProductsArr);
            ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
        	String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT, clInfo[clInfo.length - 1].getCommercialsEntityId(), prodSuppsJsonArr, reqJson);
            if(commCacheRes!=null && !(commCacheRes.equals("error")))
            	return commCacheRes;
            
            reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson);
        }
        catch(ValidationException valx) {
        	throw valx;
        }
        catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
        
        try {
        	
        	Element resElem = hitSI(reqElem,opConfig);
        	resJson = convertSIResponse(resElem,reqJson,errorJsonArray);
            
            if(resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE).length()!= 0) {
            
            	resSupplierCommJson = applySupplierCommercials(reqJson,resJson,reqHdrJson,errorJsonArray);
            	resClientCommJson = applyClientCommercials(resSupplierCommJson, reqJson);
            	
	            calculatePricesV2(reqJson, resJson, resSupplierCommJson, resClientCommJson, UserContext.getUserContextForSession(reqHdrJson));
	            
	            HolidaysCompanyOffers.getCompanyOffers(reqJson, resJson, OffersType.COMPANY_SEARCH_TIME,CommercialsOperation.Search);
	            
	            ClientInfo[] clInfo = usrCtx.getClientCommercialsHierarchyArray();
	            CommercialCacheProcessor.putInCache(PRODUCT, clInfo[clInfo.length - 1].getCommercialsEntityId(), resJson, reqJson);
            }
            
            return resJson.toString();
        }
        catch (ValidationException valx) {
        	throw valx;
        }
        catch (Exception x) {
            logger.error("Exception received while processing", x);
            throw new InternalProcessingException(x);
        }
    }
    
    public static JSONObject applyClientCommercials(JSONObject resSupplierCommJson, JSONObject reqJson) throws Exception {
    	JSONObject resClientCommJson = HolidaysClientCommercials.getClientCommercials(reqJson, resSupplierCommJson);
        if (BRMS_STATUS_TYPE_FAILURE.equals(resClientCommJson.getString(JSON_PROP_TYPE))) {
            logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", resClientCommJson.toString()));
            throw new Exception("TRLERR500");
        }
        logger.info(String.format("Client Commercial Response = %s", resClientCommJson.toString()));
		return resClientCommJson;
	}

	public static JSONObject applySupplierCommercials(JSONObject reqJson, JSONObject resJson, JSONObject reqHdrJson, JSONArray errorJsonArray) throws Exception {
    	
        logger.info(String.format("Calling to Supplier Commercial"));
        JSONObject resSupplierCommJson = HolidaysSupplierCommercials.getSupplierCommercials(reqJson, resJson,CommercialsOperation.Search);
        if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierCommJson.getString(JSON_PROP_TYPE))) {
            logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", resSupplierCommJson.toString()));
            throw new Exception("TRLERR500");
        } if (resSupplierCommJson.has("error")){
			logger.error(String.format("Received error from SI and Empty BRI",errorJsonArray.toString()));
			throw new Exception("TRLERR500");
		}
        logger.info(String.format("Supplier Commercial Response = %s", resSupplierCommJson.toString()));
        return resSupplierCommJson;
	}

	public static JSONObject convertSIResponse(Element resElem, JSONObject reqJson, JSONArray errorJsonArray) {
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqJson.getJSONObject(JSON_PROP_REQHEADER));

        JSONObject resBodyJson = new JSONObject();
        JSONArray dynamicPackageArray = new JSONArray();

        Element[] oTA_wrapperElems = XMLUtils.getElementsAtXPath(resElem,"./pac1:ResponseBody/pac:OTA_DynamicPkgAvailRSWrapper");

        for (Element oTA_wrapperElem : oTA_wrapperElems) {

            String supplierIDStr = String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:SupplierID"));
            String sequenceStr = String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:Sequence"));

            Element[] dynamicPackageElem = XMLUtils.getElementsAtXPath(oTA_wrapperElem,"./ns:OTA_DynamicPkgAvailRS/ns:DynamicPackage");
            
            //Error Response from SI
            Element errorElem[] = XMLUtils.getElementsAtXPath(oTA_wrapperElem, "./ns:OTA_DynamicPkgAvailRS/ns:Errors/ns:Error");
            if(errorElem.length != 0) 
            	errorJsonArray = logTheError(errorElem, errorJsonArray);
            
            for (Element dynamicPackElem : dynamicPackageElem) {

                JSONObject dynamicPackJson = new JSONObject();
                dynamicPackJson = getSupplierResponseDynamicPackageJSON(dynamicPackElem, true);

                dynamicPackJson.put(JSON_PROP_SEQUENCE, sequenceStr);
                dynamicPackJson.put(JSON_PROP_SUPPLIERID, supplierIDStr);

                String tourCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/@ID"));
                String subTourCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:Components/ns:PackageOptionComponent/ns:PackageOptions/ns:PackageOption/@QuoteID"));
                String availabilityStatus = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:Components/ns:PackageOptionComponent/ns:PackageOptions/ns:PackageOption/@AvailabilityStatus"));
                String brandName = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/ns:CompanyName/@CompanyShortName"));

                dynamicPackJson.put(JSON_PROP_TOURCODE, tourCode);
                dynamicPackJson.put(JSON_PROP_SUBTOURCODE, subTourCode);
                dynamicPackJson.put(JSON_PROP_AVAILABILITYSTATUS, availabilityStatus);
                dynamicPackJson.put(JSON_PROP_BRANDNAME, brandName);
                dynamicPackJson.put("destinationLocation", reqJson.getJSONObject(JSON_PROP_REQBODY).opt("destinationLocation"));
                
                dynamicPackageArray.put(dynamicPackJson);

            }
        }
        
        resBodyJson.put(JSON_PROP_DYNAMICPACKAGE, dynamicPackageArray);
        resJson.put(JSON_PROP_RESBODY, resBodyJson);
        logger.info(String.format("SI Transformed JSON Response = %s", resJson.toString()));
        
		return resJson;
	}

	//public static Element hitSI(Element reqElem, OperationConfig opConfig) throws Exception {
	public static Element hitSI(Element reqElem, ServiceConfig opConfig) throws Exception {

		logger.info("Before opening HttpURLConnection, XML request:" + XMLTransformer.toString(reqElem));
    	//Element resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(),HolidaysConfig.getHttpHeaders(), reqElem);
		Element resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
        if (resElem == null)
            throw new Exception("Null response received from SI");
        logger.info(String.format("SI XML Response = %s", XMLTransformer.toString(resElem)));
        
        return resElem;
	}

	public static JSONArray logTheError(Element[] errorElem, JSONArray errorJsonArray) {
    	for(Element error : errorElem) {
	    	JSONObject errorJSON = new JSONObject();
	    	
	        String errorShortText = String.valueOf(XMLUtils.getValueAtXPath(error, "./@ShortText"));
	        errorJSON.put("errorShortText", errorShortText);
	        
	        String errorType = String.valueOf(XMLUtils.getValueAtXPath(error, "./@Type"));
	        errorJSON.put("errorType", errorType);
	        
	        String errorCode = String.valueOf(XMLUtils.getValueAtXPath(error, "./@Code"));
	        errorJSON.put("errorCode", errorCode);
	        
	        String errorStatus = String.valueOf(XMLUtils.getValueAtXPath(error, "./@status"));
	        errorJSON.put("errorStatus", errorStatus);
	        
	        errorJsonArray.put(errorJSON);
	        logger.info(String.format("Recieved Error from SI. Error Details:" + errorJSON.toString()));
    	}
    	return errorJsonArray;
	}

	//public static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) {
	public static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson, JSONObject reqBodyJson) {
    	Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
        Document ownerDoc = reqElem.getOwnerDocument();
        ProductSupplier prodSupplier;
        
        String suppID = reqBodyJson.getString("supplierID");
        String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
        String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
        String userID = reqHdrJson.getString(JSON_PROP_USERID);
         
        //List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_HOLIDAYS, PROD_CATEG_HOLIDAYS);
         
        XMLUtils.setValueAtXPath(reqElem, "./pac:RequestHeader/com:SessionID", sessionID);
        XMLUtils.setValueAtXPath(reqElem, "./pac:RequestHeader/com:TransactionID", transactionID);
        XMLUtils.setValueAtXPath(reqElem, "./pac:RequestHeader/com:UserID", userID);

        Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,"./pac:RequestHeader/com:SupplierCredentialsList");
        prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_HOLIDAYS,PROD_CATEG_HOLIDAYS, suppID);
        
        int sequence = 1;
        Element suppCredsElem = prodSupplier.toElement(ownerDoc);
        Element sequenceElem = ownerDoc.createElementNS(NS_COM, "com:Sequence");
        sequenceElem.setTextContent(Integer.toString(sequence++));
        suppCredsElem.appendChild(sequenceElem);
        suppCredsListElem.appendChild(suppCredsElem);

        String refPointCode = reqBodyJson.optString(JSON_PROP_BRANDNAME);
        String optionRefCode = reqBodyJson.optString(JSON_PROP_TOURCODE);
        String quoteID = reqBodyJson.optString(JSON_PROP_SUBTOURCODE);
        String destinationLocation = reqBodyJson.optString("destinationLocation");
        String requestedCurrency = reqBodyJson.optString("requestedCurrency");
        
        Element refPointElem = XMLUtils.getFirstElementAtXPath(reqElem,"./pac1:RequestBody/ns:OTA_DynamicPkgAvailRQ/ns:SearchCriteria/ns:PackageOptionSearch/ns:OptionSearchCriteria/ns:Criterion/ns:RefPoint");
        Element optionRefElem = XMLUtils.getFirstElementAtXPath(reqElem,"./pac1:RequestBody/ns:OTA_DynamicPkgAvailRQ/ns:SearchCriteria/ns:PackageOptionSearch/ns:OptionSearchCriteria/ns:Criterion/ns:OptionRef");
        Element requestedCurrencyElem = XMLUtils.getFirstElementAtXPath(reqElem,"./pac1:RequestBody/ns:OTA_DynamicPkgAvailRQ");
        
        Attr codeAttr = ownerDoc.createAttribute(JSON_PROP_CODE);
        codeAttr.setValue(refPointCode);
        refPointElem.setAttributeNode(codeAttr);

        Attr optionAttr = ownerDoc.createAttribute(JSON_PROP_CODE);
        optionAttr.setValue(optionRefCode);
        optionRefElem.setAttributeNode(optionAttr);
        
        Attr requestedCurrAttr = ownerDoc.createAttribute("RequestedCurrency");
        requestedCurrAttr.setValue(requestedCurrency);
        requestedCurrencyElem.setAttributeNode(requestedCurrAttr);
        
        Element packageOptionElem = XMLUtils.getFirstElementAtXPath(reqElem,"./pac1:RequestBody/ns:OTA_DynamicPkgAvailRQ/ns:DynamicPackage/ns:Components/ns:PackageOptionComponent");
        Attr quoteIdAttr = ownerDoc.createAttribute("QuoteID");
        quoteIdAttr.setValue(quoteID);
        packageOptionElem.setAttributeNode(quoteIdAttr);

        return reqElem;
    }

	public static void validateRequestParameters(JSONObject reqJson) throws ValidationException, ParseException {
    	HolidaysRequestValidator.validateTourCode(reqJson);
    	HolidaysRequestValidator.validateSupplierID(reqJson);
	}

	public static void calculatePricesV2(JSONObject reqJson, JSONObject resJson, JSONObject resSupplierCommJson,JSONObject resClientCommJson, UserContext usrCtx) {
    	
    	Map<String, String> scommToTypeMap = getSupplierCommercialsAndTheirType(resSupplierCommJson);
		Map<String, String> clntCommToTypeMap = getClientCommercialsAndTheirType(resClientCommJson);

		String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		ClientType clientType = ClientType.valueOf(reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
		 
		Map<String, JSONArray> ccommSuppBRIJsonMap = getSupplierWisePackageDetailsFromClientCommercials(resClientCommJson);
		Map<String, Integer> suppIndexMap = new HashMap<String, Integer>();
        //for search paxCount is not specified hence default paxCount is 1
		BigDecimal paxCount = new BigDecimal(1);
		Map<String, JSONObject> totalPkgFareMap = new HashMap<String, JSONObject>();
		JSONObject totalJson = new JSONObject(),roomStayJson = null,roomRateJson = null,categoryOptionJson =null;
		String roomTypeSI = "",paxType = "",ratePlanCategory = "";
		 
		JSONArray dynamicPkgArray = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		// For each dynamicPackage in search results
		for (int i = 0; i < dynamicPkgArray.length(); i++) {
		 
			JSONObject dynamicPackageJson = dynamicPkgArray.getJSONObject(i);
			String suppID = dynamicPackageJson.getString(JSON_PROP_SUPPLIERID);
			 assignFlag = false;
			// The BRMS client commercials response JSON contains one businessRuleIntake for each supplier. Inside businessRuleIntake, the 
    		// order of each supplier result is maintained within packageDetails child array. Therefore, keep track of index for each 
    		// businessRuleIntake.packageDetails for each supplier.
			
			int idx = (suppIndexMap.containsKey(suppID)) ? (suppIndexMap.get(suppID) + 1) : 0;
    		suppIndexMap.put(suppID, idx);
    		JSONObject ccommPkgDtlsJson = (ccommSuppBRIJsonMap.containsKey(suppID)) ? ccommSuppBRIJsonMap.get(suppID).getJSONObject(idx) : null;
    		if (ccommPkgDtlsJson == null) {
    			logger.info(String.format("BRMS/ClientComm: \"businessRuleIntake\" JSON element for supplier %s not found.", suppID));
    		}
    		
    		// The following PriceComponentsGroup accepts search subcomponents one-by-one and automatically calculates totals
    		PriceComponentsGroup totalFareCompsGroup = new PriceComponentsGroup(JSON_PROP_TOTAL, clientCcyCode, new BigDecimal(0), true);
    		PriceComponentsGroup totalIncentivesGroup = new PriceComponentsGroup(JSON_PROP_INCENTIVES, clientCcyCode, BigDecimal.ZERO, true);
    		JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
    		
    		JSONArray clientCommercialPkgInfoArr = new JSONArray();
    		JSONObject componentJson = dynamicPackageJson.getJSONObject(JSON_PROP_COMPONENTS);
    		totalPkgFareMap = new HashMap<String, JSONObject>(); 
    		//For Hotel Component
			if (componentJson.has(JSON_PROP_HOTEL_COMPONENT)) {

				JSONObject hotelCompJson = componentJson.getJSONObject(JSON_PROP_HOTEL_COMPONENT);
				if (hotelCompJson != null && hotelCompJson.length() > 0) {
				JSONArray roomStayArray = hotelCompJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
				for (int c = 0; c < roomStayArray.length(); c++) {
					roomStayJson = roomStayArray.getJSONObject(c);

					roomTypeSI = roomStayJson.getString(JSON_PROP_ROOMTYPE);
					
					JSONArray ccommRoomDtlsJsonArr = ccommPkgDtlsJson.getJSONArray(JSON_PROP_ROOMDETAILS);

					for (int k = 0; k < ccommRoomDtlsJsonArr.length(); k++) {

						JSONObject ccommRoomDtlsJson = ccommRoomDtlsJsonArr.getJSONObject(k);
					JSONArray roomRateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);

						for (int r = 0; r < roomRateArr.length(); r++) {
							roomRateJson = roomRateArr.getJSONObject(r);
							totalJson = roomRateJson.getJSONObject(JSON_PROP_TOTAL);
							paxType = totalJson.getString(JSON_PROP_TYPE);
							ratePlanCategory = roomRateJson.getString("ratePlanCategory");
							
							calculatePriceHotelOrCruise(usrCtx, scommToTypeMap, clntCommToTypeMap,
									clientMarket, clientCcyCode, paxCount, totalPkgFareMap, totalJson, roomTypeSI,
									paxType, ratePlanCategory, suppID, totalFareCompsGroup, totalIncentivesGroup,
									suppPaxTypeFaresJsonArr, clientCommercialPkgInfoArr, ccommRoomDtlsJson,clientType);

						}
					}
				}
			}
		}      
			//For Cruise Component
			else if (componentJson.has(JSON_PROP_CRUISE_COMPONENT)) {
				JSONObject cruiseComponentJson =componentJson.getJSONObject(JSON_PROP_CRUISE_COMPONENT);
				
				if (cruiseComponentJson != null && cruiseComponentJson.length() > 0) {
					JSONArray categoryOptionsArray = cruiseComponentJson.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
					for (int y = 0; y < categoryOptionsArray.length(); y++) {
						JSONObject categoryOptionsJson = categoryOptionsArray.getJSONObject(y);
						JSONArray categoryOptionArray = categoryOptionsJson.getJSONArray(JSON_PROP_CATEGORYOPTION);
						for (int z = 0; z < categoryOptionArray.length(); z++) {
							categoryOptionJson = categoryOptionArray.getJSONObject(z);
							roomTypeSI = categoryOptionJson.getString(JSON_PROP_CABINTYPE);
							JSONArray ccommRoomDtlsJsonArr = ccommPkgDtlsJson.getJSONArray(JSON_PROP_ROOMDETAILS);

							for (int k = 0; k < ccommRoomDtlsJsonArr.length(); k++) {

								JSONObject ccommRoomDtlsJson = ccommRoomDtlsJsonArr.getJSONObject(k);
								JSONArray totalArray = categoryOptionJson.getJSONArray(JSON_PROP_TOTAL);
								for (int t = 0; t < totalArray.length(); t++) {
									totalJson = totalArray.getJSONObject(t);
									paxType = totalJson.getString(JSON_PROP_TYPE);
									calculatePriceHotelOrCruise(usrCtx, scommToTypeMap,
											clntCommToTypeMap, clientMarket, clientCcyCode, paxCount, totalPkgFareMap,
											totalJson, roomTypeSI, paxType, ratePlanCategory, suppID,
											totalFareCompsGroup, totalIncentivesGroup, suppPaxTypeFaresJsonArr,
											clientCommercialPkgInfoArr, ccommRoomDtlsJson,clientType);
								}
							}
						}
					}
				}
			}
                	 
                 
    		//setting the total Package Fare in GlobalInfo in Search.It is the TWIN room BROCHURE CATEGORY LEAST PRICE
			
			 Iterator<Map.Entry<String, JSONObject>> priceIter = totalPkgFareMap.entrySet().iterator();
			 JSONObject totalPkgJson =new JSONObject();
    			while (priceIter.hasNext()) {
    				Map.Entry<String, JSONObject> priceEntry = priceIter.next();
    				
    				if(priceEntry.getKey().equalsIgnoreCase("TWIN") || priceEntry.getKey().equalsIgnoreCase("standard") || priceEntry.getKey().equalsIgnoreCase("Standard - Twin")) {
    				JSONObject totalPriceJson = priceEntry.getValue();
    				
    				totalPkgJson.put(JSON_PROP_AMOUNTBEFORETAX, totalPriceJson.get(JSON_PROP_AMOUNTBEFORETAX));
    				totalPkgJson.put(JSON_PROP_AMOUNTAFTERTAX, totalPriceJson.get(JSON_PROP_AMOUNTAFTERTAX));
    				totalPkgJson.put(JSON_PROP_TAXES, totalPriceJson.getJSONObject(JSON_PROP_TAXES));
    				if(totalPriceJson.has(JSON_PROP_RECEIVABLES)) {
    					totalPkgJson.put(JSON_PROP_RECEIVABLES, totalPriceJson.getJSONObject(JSON_PROP_RECEIVABLES));
    				}
    				totalPkgJson.put(JSON_PROP_CURRENCYCODE,  totalPriceJson.get(JSON_PROP_CURRENCYCODE));
    				}
    				if (clientType == ClientType.B2B) {
    					if(priceEntry.getKey().equalsIgnoreCase(JSON_PROP_INCENTIVES)) {
    						totalPkgJson.put(JSON_PROP_INCENTIVES, priceEntry.getValue());
    				}
    				}
    			    
    			}
    			dynamicPackageJson.getJSONObject(JSON_PROP_GLOBALINFO).put(JSON_PROP_TOTAL,totalPkgJson);
			
		
		}
		logger.info(String.format("SI response Json after calculate prices = %s", resJson.toString()));
	}

	public static void calculatePriceHotelOrCruise(UserContext usrCtx, Map<String, String> scommToTypeMap,
			Map<String, String> clntCommToTypeMap, String clientMarket, String clientCcyCode, BigDecimal paxCount,
			Map<String, JSONObject> totalPkgFareMap, JSONObject totalJson, String roomTypeSI, String paxType,
			String ratePlanCategory, String suppID, PriceComponentsGroup totalFareCompsGroup,
			PriceComponentsGroup totalIncentivesGroup, JSONArray suppPaxTypeFaresJsonArr,
			JSONArray clientCommercialPkgInfoArr, JSONObject ccommRoomDtlsJson, ClientType clientType) {

		String suppCcyCode = totalJson.getString(JSON_PROP_CCYCODE);
		// PassengerType psgrType = PassengerType.forString(paxType);
		JSONObject ccommPaxDtlsJson = getClientCommercialsPackageDetailsForPassengerType(ccommRoomDtlsJson, paxType);
		JSONArray clientEntityCommJsonArr = null;
		int amountAfterTaxvalue = 0, amountBeforeTaxvalue = 0;
		if (ccommPaxDtlsJson == null) {
			logger.info(String.format("Passenger type %s details not found client commercial packageDetails", paxType));
		} else {
			// From the passenger type client commercial JSON, retrieve calculated client entity commercials
			clientEntityCommJsonArr = ccommPaxDtlsJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
			if (clientEntityCommJsonArr == null) {
				logger.warn("Client commercials calculations not found");
			}
		}
		if (ccommPaxDtlsJson != null) {
			BigDecimal amountAfterTaxcc = ccommPaxDtlsJson.optBigDecimal(JSON_PROP_TOTALFARE, new BigDecimal("0"));
			BigDecimal amountBeforeTaxcc = ccommPaxDtlsJson.getJSONObject(JSON_PROP_FAREBREAKUP)
					.optBigDecimal(JSON_PROP_BASEFARE, new BigDecimal("0"));
			BigDecimal amountAfterTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTAFTERTAX,
					new BigDecimal("0")));
			BigDecimal amountBeforeTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTBEFORETAX,
					new BigDecimal("0")));
			amountAfterTaxvalue = (amountAfterTaxcc).compareTo(amountAfterTaxSI);
			amountBeforeTaxvalue = amountBeforeTaxcc.compareTo(amountBeforeTaxSI);
		}

		String roomTypeCC = ccommRoomDtlsJson.getString(JSON_PROP_ROOMTYPE);

		BigDecimal paxTypeTotalFare = new BigDecimal(0);
		JSONObject commPriceJson = new JSONObject();
		// Reference CKIL_323141 - There are three types of client commercials that are 
		// receivable from clients: Markup, Service Charge (Transaction Fee) & Look-to-Book.
		// Of these, Markup and Service Charge (Transaction Fee) are transactional and need 
		// to be considered for selling price.
		// Reference CKIL_231556 (1.1.2.3/BR04) The display price will be calculated as - 
		// (Total Supplier Price + Markup + Additional Company Receivable Commercials)

		JSONObject markupCalcJson = null;
		JSONArray clientCommercials = new JSONArray();
		PriceComponentsGroup paxReceivablesCompsGroup = null;
		PriceComponentsGroup paxIncentivesGroup = null;
		PriceComponentsGroup totalPaxReceivablesCompsGroup = null;
		// Since there are multiple rooms of same type eg.TWIN ,it is required to check whether
		// the initial basefare and totalfare matches of SI and CC to correctly replace the amounts
		
		if (roomTypeSI.equalsIgnoreCase(roomTypeCC) && amountAfterTaxvalue == 0 && amountBeforeTaxvalue == 0) {

			for (int n = 0; clientEntityCommJsonArr != null && n < clientEntityCommJsonArr.length(); n++) {
				JSONArray clientEntityCommercialsJsonArr = new JSONArray();
				JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(n);

				// TODO: In case of B2B, do we need to add markups for all client hierarchy
				// levels?
				if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) {
					markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);
					clientEntityCommercialsJsonArr
							.put(convertToClientEntityCommercialJson(markupCalcJson, clntCommToTypeMap, suppCcyCode));
				}

				// Additional commercialcalc clientCommercial
				// TODO: In case of B2B, do we need to add additional receivable commercials for all client hierarchy levels?
				JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
				// If totals of receivables at all levels is required, the following instance creation needs to move where
				// variable 'paxReceivablesCompsGroup' is declared
				paxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode,new BigDecimal(0), true);
				totalPaxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode,new BigDecimal(0), true);
				paxIncentivesGroup = new PriceComponentsGroup(JSON_PROP_INCENTIVES, clientCcyCode, BigDecimal.ZERO,true);

				if (additionalCommsJsonArr != null) {
					for (int p = 0; p < additionalCommsJsonArr.length(); p++) {
						JSONObject additionalCommJson = additionalCommsJsonArr.getJSONObject(p);
						String additionalCommName = additionalCommJson.optString(JSON_PROP_COMMNAME);
						String additionalCommType = clntCommToTypeMap.get(additionalCommName);
						clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(additionalCommJson,
								clntCommToTypeMap, suppCcyCode));

						if (COMM_TYPE_RECEIVABLE.equals(additionalCommType)) {
							String additionalCommCcy = additionalCommJson.getString(JSON_PROP_COMMCCY);
							BigDecimal additionalCommAmt = additionalCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT)
									.multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode,clientMarket));
							paxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName)
									.concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt);
							totalPaxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".")
									.concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode,
									additionalCommAmt.multiply(paxCount));
						}
					}

				}

				JSONObject clientEntityDetailsJson = new JSONObject();
				// JSONArray clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchy();
				ClientInfo[] clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchyArray();
				// clientEntityDetailsJson = clientEntityDetailsArr.getJSONObject(k);
				clientEntityDetailsJson.put(JSON_PROP_CLIENTID, clientEntityDetailsArr[n].getClientId());
				clientEntityDetailsJson.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsArr[n].getParentClienttId());
				clientEntityDetailsJson.put(JSON_PROP_COMMENTITYTYPE,
						clientEntityDetailsArr[n].getCommercialsEntityType());
				clientEntityDetailsJson.put(JSON_PROP_COMMENTITYID, clientEntityDetailsArr[n].getCommercialsEntityId());
				clientEntityDetailsJson.put(JSON_PROP_CLIENTCOMM, clientEntityCommercialsJsonArr);
				clientCommercials.put(clientEntityDetailsJson);

				// For B2B clients, the incentives of the last client hierarchy level should be accumulated and returned in the response.
				if (n == (clientEntityCommJsonArr.length() - 1)) {
					for (int x = 0; x < clientEntityCommercialsJsonArr.length(); x++) {
						JSONObject clientEntityCommercialsJson = clientEntityCommercialsJsonArr.getJSONObject(x);
						if (COMM_TYPE_PAYABLE.equals(clientEntityCommercialsJson.getString(JSON_PROP_COMMTYPE))) {
							String commCcy = clientEntityCommercialsJson.getString(JSON_PROP_COMMCCY);
							String commName = clientEntityCommercialsJson.getString(JSON_PROP_COMMNAME);
							BigDecimal commAmt = clientEntityCommercialsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(commCcy, clientCcyCode, clientMarket))
									.multiply(paxCount, MATH_CTX_2_HALF_UP);
							paxIncentivesGroup.add(String.format(mIncvPriceCompFormat, commName), clientCcyCode,commAmt);
							totalIncentivesGroup.add(String.format(mIncvPriceCompFormat, commName), clientCcyCode,commAmt);
						}
					}
				}
			}

			// ------------------------BEGIN----------------------------------
			BigDecimal baseFareAmt = totalJson.optBigDecimal(JSON_PROP_AMOUNTBEFORETAX, new BigDecimal("0"));
			JSONArray ccommTaxDetailsJsonArr = null;
			if (markupCalcJson != null) {
				JSONObject fareBreakupJson = markupCalcJson.optJSONObject(JSON_PROP_FAREBREAKUP);
				if (fareBreakupJson != null) {
					baseFareAmt = fareBreakupJson.optBigDecimal(JSON_PROP_BASEFARE, baseFareAmt);
					ccommTaxDetailsJsonArr = fareBreakupJson.optJSONArray(JSON_PROP_TAXDETAILS);
				}
			}

			totalJson.put(JSON_PROP_AMOUNTBEFORETAX,
					baseFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
			totalJson.put(JSON_PROP_CCYCODE, clientCcyCode);
			paxTypeTotalFare = paxTypeTotalFare.add(totalJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX));
			totalFareCompsGroup.add(JSON_PROP_AMOUNTBEFORETAX, clientCcyCode,
					totalJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX).multiply(paxCount));

			int offset = 0;
			JSONArray paxTypeTaxJsonArr = totalJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
			JSONObject taxesJson = getCommercialPricesTaxesJson(paxTypeTaxJsonArr, ccommTaxDetailsJsonArr, offset,
					totalFareCompsGroup, paxCount, clientCcyCode, clientMarket);
			totalJson.put(JSON_PROP_TAXES, taxesJson);
			paxTypeTotalFare = paxTypeTotalFare.add(taxesJson.getBigDecimal(JSON_PROP_AMOUNT));

			// If amount of receivables group is greater than zero, then append to
			// commercial prices
			if (paxReceivablesCompsGroup != null
					&& paxReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
				paxTypeTotalFare = paxTypeTotalFare.add(paxReceivablesCompsGroup.getComponentAmount());
				totalFareCompsGroup.addSubComponent(totalPaxReceivablesCompsGroup, null);
				totalJson.put(JSON_PROP_RECEIVABLES, paxReceivablesCompsGroup.toJSON());
			}
			// -------------------------END-----------------------------------

			JSONObject clientCommercialPkgInfoJson = new JSONObject();
			clientCommercialPkgInfoJson.put(JSON_PROP_PAXTYPE, paxType);
			clientCommercialPkgInfoJson.put(JSON_PROP_CLIENTENTITYCOMMS, clientCommercials);
			clientCommercialPkgInfoArr.put(clientCommercialPkgInfoJson);

			suppPaxTypeFaresJsonArr.put(totalJson);
			// JSONObject totalFareJson = new JSONObject();
			totalJson.put(JSON_PROP_AMOUNTAFTERTAX, paxTypeTotalFare);
			totalJson.put(JSON_PROP_AMOUNTBEFORETAX, baseFareAmt);
			totalJson.put(JSON_PROP_CCYCODE, clientCcyCode);

			PassengerType psgrType = PassengerType.forString(paxType);
			/*
			 * Below code to set Total package fare If the supplier provides two separate
			 * rates for adult and child , only Adult price will be taken into
			 * consideration, the child price for the search result page will be ignored If
			 * in case there are multiple TWIN BROCHURE rooms then the lowest ADULT price
			 * would be taken into consideration
			 */

			if (!ratePlanCategory.equals("")) {
				// For Hotel component
				if (roomTypeSI.equalsIgnoreCase("TWIN") || roomTypeSI.equalsIgnoreCase("standard") || roomTypeSI.equalsIgnoreCase("Standard - Twin")) {
					if (ratePlanCategory.equalsIgnoreCase("BROCHURE")) {
						if (psgrType.equals(PassengerType.ADULT)) {
							getTotalSearchPkgFare(paxTypeTotalFare, roomTypeSI, totalJson, totalPkgFareMap,
									paxIncentivesGroup);
						}
					}

				}
			} else {
				// Below code to set Total package fare for cruise where we do not get
				// ratePlanCategory as "BROCHURE" for Cruise component
				if (roomTypeSI.equalsIgnoreCase("TWIN") || roomTypeSI.equalsIgnoreCase("standard")) {

					if (psgrType.equals(PassengerType.ADULT)) {

						getTotalSearchPkgFare(paxTypeTotalFare, roomTypeSI, totalJson, totalPkgFareMap,
								paxIncentivesGroup);
					}
				}
			}
		}
	}

	// Retrieve array of businessRuleIntake.packageDetails for each supplier from Client Commercials response JSON
	public static Map<String, JSONArray> getSupplierWisePackageDetailsFromClientCommercials(
			JSONObject resClientCommJson) {
		JSONArray ccommSuppBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(resClientCommJson);

		Map<String, JSONArray> ccommSuppBRIJsonMap = new HashMap<String, JSONArray>();
		for (int i = 0; i < ccommSuppBRIJsonArr.length(); i++) {
			JSONObject ccommSuppBRIJson = ccommSuppBRIJsonArr.getJSONObject(i);
			String suppID = ccommSuppBRIJson.getJSONObject(JSON_PROP_COMMONELEMS).getString(JSON_PROP_SUPP);
			JSONArray ccommPkgDtlsJsonArr = ccommSuppBRIJson.getJSONArray(JSON_PROP_PACKAGEDETAILS);
			ccommSuppBRIJsonMap.put(suppID, ccommPkgDtlsJsonArr);
		}

		return ccommSuppBRIJsonMap;
	}

 	public static JSONObject getClientCommercialsPackageDetailsForPassengerType(JSONObject ccommRoomDtlsJson,
			String paxType) {
		if (ccommRoomDtlsJson == null || paxType == null) {
			return null;
		}
		// Search this paxType in client commercials packageDetails.roomDetails
		JSONArray ccommPaxDtlsJsonArr = ccommRoomDtlsJson.getJSONArray(JSON_PROP_PASSENGERDETAILS);

		for (int n = 0; n < ccommPaxDtlsJsonArr.length(); n++) {

			JSONObject ccommPaxDtlsJson = ccommPaxDtlsJsonArr.getJSONObject(n);

			String passengerType = ccommPaxDtlsJson.getString(JSON_PROP_PASSENGERTYPE);

			// Checks if SI PaxType matches with CC passengerType
			if (passengerType.equalsIgnoreCase(paxType)) {
				return ccommPaxDtlsJson;
			}}
		return null;
	}
 	
 	

   
	
	public static JSONObject convertToClientEntityCommercialJson(JSONObject clientCommJson, Map<String,String> clntCommToTypeMap, String suppCcyCode) {
    	JSONObject clientCommercial= new JSONObject();
    	String commercialName = clientCommJson.getString(JSON_PROP_COMMNAME);
		clientCommercial.put(JSON_PROP_COMMTYPE, clntCommToTypeMap.getOrDefault(commercialName, "?"));
		clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
		clientCommercial.put(JSON_PROP_COMMNAME,clientCommJson.getString(JSON_PROP_COMMNAME));
		clientCommercial.put(JSON_PROP_COMMCCY,clientCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
    	return clientCommercial;
    }
	
	 public static JSONObject getCommercialPricesTaxesJson(JSONArray paxTypeTaxJsonArr, JSONArray ccommTaxDetailsJsonArr, int offset, PriceComponentsGroup totalFareCompsGroup, BigDecimal paxCount, String clientCcyCode, String clientMarket) {
			BigDecimal taxesTotal = new BigDecimal(0);
			JSONObject taxesJson = new JSONObject();
			JSONArray taxJsonArr = new JSONArray();
			String suppCcyCode = null;
			String taxCode = null;
			BigDecimal amount = null;
			
			JSONObject ccommTaxDetailJson = null; 
			JSONObject paxTypeTaxJson = null;
			JSONObject taxJson = null;
			for (int l=0; l < paxTypeTaxJsonArr.length(); l++) {
				paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
				suppCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
				taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
				//amount=paxTypeTaxJson.getString(JSON_PROP_AMOUNT);
				amount= (BigDecimal) (paxTypeTaxJson.optBigDecimal(JSON_PROP_AMOUNT,
							new BigDecimal("0")));
				// Access the tax array returned by client commercials in a positional manner instead of 
				// searching in that array using taxcode/feecode.
				taxJson = paxTypeTaxJson;
				ccommTaxDetailJson = (ccommTaxDetailsJsonArr != null) ? ccommTaxDetailsJsonArr.optJSONObject(offset + l) : null;
				if (ccommTaxDetailJson != null) {
					// If tax JSON is found in commercials, replace existing tax details with one from commercials
					BigDecimal taxAmt = ccommTaxDetailJson.getBigDecimal(JSON_PROP_TAXVALUE);
					taxJson = new JSONObject();
					taxJson.put(JSON_PROP_TAXDESCRIPTION, taxCode);
					taxJson.put(JSON_PROP_AMOUNT, taxAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
					taxJson.put(JSON_PROP_CCYCODE, clientCcyCode);
				}

				taxJsonArr.put(taxJson);
				taxesTotal = taxesTotal.add(taxJson.getBigDecimal(JSON_PROP_AMOUNT));
				totalFareCompsGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXDESCRIPTION), clientCcyCode, taxJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
			}
			
			taxesJson.put(JSON_PROP_TAX, taxJsonArr);
		    //Total taxes amount
			taxesJson.put(JSON_PROP_AMOUNT, taxesTotal);
			taxesJson.put(JSON_PROP_CCYCODE, clientCcyCode);
			return taxesJson;
	    }
    
   @Deprecated
    public static void calculatePricesV1(JSONObject reqJson, JSONObject resJson, JSONObject resSupplierCommJson,
            JSONObject resClientCommJson) {

        JSONObject briJson, ccommPkgDtlsJson, ccommRoomDtlsJson, clientEntityCommJson, markupCalcJson, ccommPaxDtlsJson;
        JSONArray ccommPkgDtlsJsonArr, ccommRoomDtlsJsonArr, clientEntityCommJsonArr, ccommPaxDtlsJsonArr,supplierCommercialDetailsArr;
        
        Map<String,String> scommToTypeMap= getSupplierCommercialsAndTheirType(resSupplierCommJson);
        Map<String, String> commToTypeMap = getClientCommercialsAndTheirType(resClientCommJson);
		Map<String, JSONObject> totalPkgFareMap = new HashMap<String, JSONObject>(); 
        
        String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
        String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
        
        JSONArray briArr = resClientCommJson.getJSONObject(JSON_PROP_RESULT).getJSONObject(JSON_PROP_EXECUTIONRES).getJSONArray(JSON_PROP_RESULTS).getJSONObject(0).getJSONObject(JSON_PROP_VALUE).getJSONObject(JSON_PROP_CLIENTTRANRULES).getJSONArray(JSON_PROP_BUSSRULEINTAKE);

        String suppId = "";
        
        BigDecimal totalTaxPrice = new BigDecimal(0);
        Map<String, Integer> suppIndexMap = new HashMap<String, Integer>();

        for (int i = 0; i < briArr.length(); i++) {

            briJson = (JSONObject) briArr.get(i);
            ccommPkgDtlsJsonArr = briJson.getJSONArray(JSON_PROP_PACKAGEDETAILS);
            suppId = briJson.getJSONObject(JSON_PROP_COMMONELEMENTS).getString(JSON_PROP_SUPP);
            // getting roomstay from SI response
            JSONArray dynamicPkgArray = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE);

            for (int a = 0; a < dynamicPkgArray.length(); a++) {
                JSONObject dynamicPackageJson = dynamicPkgArray.getJSONObject(a);
                totalFare = new BigDecimal("0");
                assignFlag = false;
                String supplierIdSI = dynamicPackageJson.getString(JSON_PROP_SUPPLIERID);
                if (!(suppId.equalsIgnoreCase(supplierIdSI))) {
                    continue;
                }

                int idx = (suppIndexMap.containsKey(supplierIdSI)) ? (suppIndexMap.get(supplierIdSI) + 1) : 0;
                suppIndexMap.put(supplierIdSI, idx);
                ccommPkgDtlsJson = ccommPkgDtlsJsonArr.getJSONObject(idx);
                
                ccommRoomDtlsJsonArr = ccommPkgDtlsJson.getJSONArray(JSON_PROP_ROOMDETAILS);

                for (int k = 0; k < ccommRoomDtlsJsonArr.length(); k++) {
                    
                    ccommRoomDtlsJson = ccommRoomDtlsJsonArr.getJSONObject(k);

                    String roomType = ccommRoomDtlsJson.getString(JSON_PROP_ROOMTYPE);

                    ccommPaxDtlsJsonArr = ccommRoomDtlsJson.getJSONArray(JSON_PROP_PASSENGERDETAILS);

                    for (int p = 0; p < ccommPaxDtlsJsonArr.length(); p++) {

                        ccommPaxDtlsJson = ccommPaxDtlsJsonArr.getJSONObject(p);
                        BigDecimal totalPrice = new BigDecimal(0);
                        BigDecimal amountAfterTaxcc = ccommPaxDtlsJson.getBigDecimal(JSON_PROP_TOTALFARE);
                        BigDecimal amountBeforeTaxcc = ccommPaxDtlsJson.getJSONObject(JSON_PROP_FAREBREAKUP)
                                .getBigDecimal(JSON_PROP_BASEFARE);
                        clientEntityCommJsonArr = ccommPaxDtlsJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
                        if (clientEntityCommJsonArr == null) {
                            // TODO: Refine this warning message. Maybe log some context information also.
                            logger.warn("Client commercials calculations not found");
                            continue;
                        }
                         else//This is to capture the comm type field from commercial head in entity details
                            {
                                int len=clientEntityCommJsonArr.length();
                                for (int x=0; x < len; x++) {
                                    JSONObject ccommClientCommJson = clientEntityCommJsonArr.getJSONObject(x);
                                    ccommClientCommJson.put(JSON_PROP_COMMTYPE, commToTypeMap.get(ccommClientCommJson.optJSONObject(JSON_PROP_MARKUPCOMDTLS).getString(JSON_PROP_COMMNAME)));
                                }
                                
                            }
                         supplierCommercialDetailsArr = ccommPaxDtlsJson.optJSONArray(JSON_PROP_COMMDETAILS);

                            if (supplierCommercialDetailsArr == null) {
                                logger.warn(String.format("No supplier commercials found for supplier %s", suppId));
                            } 
                            
                            else//This is to capture the comm type field from commercial head in suppCommercialRes
                            {
                                int len=supplierCommercialDetailsArr.length();
                                for (int x=0; x < len; x++) {
                                    JSONObject scommClientCommJson = supplierCommercialDetailsArr.getJSONObject(x);
                                    scommClientCommJson.put(JSON_PROP_COMMTYPE, scommToTypeMap.get(scommClientCommJson.getString(JSON_PROP_COMMNAME)));
                                }
                                
                        // for multiple chain of entity take the latest commercials applied
                        for (int l = (clientEntityCommJsonArr.length() - 1); l >= 0; l--) {

                            clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(l);
                            
                            // TODO: In case of B2B, do we need to add additional receivable commercials for all client hierarchy levels?  
                            JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);//take the array additionalcommercialDetails
                            if (additionalCommsJsonArr != null) {
                                for (int x=0; x < additionalCommsJsonArr.length(); x++) {
                                    JSONObject additionalCommsJson = additionalCommsJsonArr.getJSONObject(x);//take object of additionalcommercialDetails array one by one
                                    String additionalCommName = additionalCommsJson.optString(JSON_PROP_COMMNAME);//fetch comm  Name from additionalcommercialDetails object
                                    if (COMM_TYPE_RECEIVABLE.equals(commToTypeMap.get(additionalCommName))) {//is the additionalCommName receivable?
                                        String additionalCommCcy = additionalCommsJson.getString(JSON_PROP_COMMCCY);//get comm currency from additionalcommercialDetails Object
                                        BigDecimal additionalCommAmt = additionalCommsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket));
                                        totalPrice=totalPrice.add(additionalCommAmt);
                                        
                                    }
                                }
                            }
                            markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMDTLS);
                            if (markupCalcJson == null) {
                                continue;
                            }
                            

                            if (dynamicPackageJson.getJSONObject(JSON_PROP_COMPONENTS).has(JSON_PROP_HOTEL_COMPONENT)) {
                                //JSONArray hotelComponentArray = dynamicPackageJson.getJSONObject(JSON_PROP_COMPONENTS)
                                //      .getJSONArray(JSON_PROP_HOTEL_COMPONENT);
                                
                                JSONObject hotelCompJson = dynamicPackageJson.getJSONObject(JSON_PROP_COMPONENTS)
                                        .getJSONObject(JSON_PROP_HOTEL_COMPONENT);
                                if (hotelCompJson!=null && hotelCompJson.length()>0) {
                                calculatePriceHotel(markupCalcJson, roomType, amountAfterTaxcc, amountBeforeTaxcc,
                                        hotelCompJson,totalPrice,totalPkgFareMap);

                            }
                        }

                            if (dynamicPackageJson.getJSONObject(JSON_PROP_COMPONENTS).has(JSON_PROP_CRUISE_COMPONENT)) {
                                //JSONArray cruiseComponentArray = dynamicPackageJson.getJSONObject(JSON_PROP_COMPONENTS)
                                //      .getJSONArray(JSON_PROP_CRUISE_COMPONENT);
                                
                                JSONObject cruiseComponentJson = dynamicPackageJson.getJSONObject(JSON_PROP_COMPONENTS)
                                        .getJSONObject(JSON_PROP_CRUISE_COMPONENT);
                                if (cruiseComponentJson!=null && cruiseComponentJson.length()>0) {
                                    calculatePriceCruise(markupCalcJson, roomType, amountAfterTaxcc, amountBeforeTaxcc,
                                        cruiseComponentJson,totalPrice,totalPkgFareMap);

                                }
                            }   
                        }
                    }
                }
            }
                //setting the totalFare in GlobalInfo
                
                JSONObject totalJson = new JSONObject();
                totalJson.put(JSON_PROP_AMOUNTAFTERTAX, totalFare);
                totalJson.put(JSON_PROP_CURRENCYCODE, currencyCode);
                dynamicPackageJson.getJSONObject(JSON_PROP_GLOBALINFO).put(JSON_PROP_TOTAL,totalJson);
                
                
         }
       }
    }
    
    public static Map<String, String> getSupplierCommercialsAndTheirType(JSONObject suppCommResJson) {
          JSONObject scommBRIJson,commHeadJson;
          JSONArray commHeadJsonArr = null;
         Map<String, String> suppCommToTypeMap = new HashMap<String, String>();
           JSONArray scommBRIJsonArr = getSupplierCommercialsBusinessRuleIntakeJSONArray(suppCommResJson);
           for (int i=0; i < scommBRIJsonArr.length(); i++) {
                scommBRIJson = scommBRIJsonArr.getJSONObject(i);
                commHeadJsonArr = scommBRIJson.optJSONArray(JSON_PROP_COMMHEAD);
                if (commHeadJsonArr == null) {
                    logger.warn("No commercial heads found in supplier commercials");
                    continue;
                }
                
                for (int j=0; j < commHeadJsonArr.length(); j++) {
                    commHeadJson = commHeadJsonArr.getJSONObject(j);
                    suppCommToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME), commHeadJson.getString(JSON_PROP_COMMTYPE));
                }
            }
            
            return suppCommToTypeMap;
    
    }

    public static JSONArray getSupplierCommercialsBusinessRuleIntakeJSONArray(JSONObject suppCommResJson) {
        return suppCommResJson.getJSONObject(JSON_PROP_RESULT).getJSONObject(JSON_PROP_EXECUTIONRES).getJSONArray(JSON_PROP_RESULTS).getJSONObject(0).getJSONObject(JSON_PROP_VALUE).getJSONObject(JSON_PROP_SUPPTRANRULES).getJSONArray(JSON_PROP_BUSSRULEINTAKE);
       }
    
    public static Map<String, String> getClientCommercialsAndTheirType(JSONObject clientCommResJson) {
         JSONArray commHeadJsonArr = null;
         JSONArray entDetaiJsonArray= null;
            JSONObject commHeadJson = null;
            JSONObject scommBRIJson = null;
            Map<String, String> commToTypeMap = new HashMap<String, String>();
            JSONArray scommBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
            
            for (int i=0; i < scommBRIJsonArr.length(); i++) {
                scommBRIJson = scommBRIJsonArr.getJSONObject(i);
             entDetaiJsonArray = scommBRIJson.getJSONArray(JSON_PROP_ENTITYDETAILS);
                for(int j=0;j<entDetaiJsonArray.length();j++)
                {
                    commHeadJsonArr=entDetaiJsonArray.getJSONObject(j).getJSONArray(JSON_PROP_COMMHEAD);
                if (commHeadJsonArr == null) {
                    logger.warn("No commercial heads found in supplier commercials");
                    continue;
                }
                
                for (int k=0; k < commHeadJsonArr.length(); k++) {
                    commHeadJson = commHeadJsonArr.getJSONObject(k);
                    commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME), commHeadJson.getString(JSON_PROP_COMMTYPE));
                }
            }
            }
            
            return commToTypeMap;
        
    }

    public static JSONArray getClientCommercialsBusinessRuleIntakeJSONArray(JSONObject clientCommResJson) {
        
        return clientCommResJson.getJSONObject(JSON_PROP_RESULT).getJSONObject(JSON_PROP_EXECUTIONRES).getJSONArray(JSON_PROP_RESULTS).getJSONObject(0).getJSONObject("value").getJSONObject(JSON_PROP_CLIENTTRANRULES).getJSONArray(JSON_PROP_BUSSRULEINTAKE);
    }

    public static void calculatePriceHotel(JSONObject markupCalcJson, String roomType, BigDecimal amountAfterTaxcc,
            BigDecimal amountBeforeTaxcc, JSONObject hotelCompJson,BigDecimal totalPrice, Map<String, JSONObject> totalPkgFareMap) {
        //for (int b = 0; b < hotelCompJson.length(); b++) {
        //  JSONObject hotelComponentJson = hotelCompJson.getJSONObject(b);
        
            JSONArray roomStayArray = hotelCompJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
            for (int c = 0; c < roomStayArray.length(); c++) {
                JSONObject roomStayJson = roomStayArray.getJSONObject(c);

                String roomTypeSI = roomStayJson.getString(JSON_PROP_ROOMTYPE);
                if (roomTypeSI.equalsIgnoreCase(roomType)) {

                    BigDecimal amountAfterTax = markupCalcJson.getBigDecimal(JSON_PROP_TOTALFARE);
                    BigDecimal amountBeforeTax = markupCalcJson.getJSONObject(JSON_PROP_FAREBREAKUP)
                            .getBigDecimal(JSON_PROP_BASEFARE);
                    String currencyCodeCC = markupCalcJson.getString(JSON_PROP_COMMCURRENCY);
                    //totalPrice = totalPrice.add(amountAfterTax);
                    JSONArray taxArr = markupCalcJson.getJSONObject(JSON_PROP_FAREBREAKUP)
                            .getJSONArray(JSON_PROP_TAXDETAILS);

                    JSONArray roomRateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);

                    for (int r = 0; r < roomRateArr.length(); r++) {
                    	JSONObject roomRateJson =roomRateArr.getJSONObject(r);
                        JSONObject totalJson = roomRateJson.getJSONObject(JSON_PROP_TOTAL);
                        BigDecimal amountAfterTaxSI;
                        BigDecimal amountBeforeTaxSI;
                        String ratePlanCategory = roomRateArr.getJSONObject(r).getString("ratePlanCategory");
                        currencyCode =totalJson.optString("currencyCode");
                        
                        amountAfterTaxSI = totalJson.optBigDecimal("amountAfterTax", new BigDecimal("0"));
                        amountBeforeTaxSI = totalJson.optBigDecimal("amountBeforeTax", new BigDecimal("0"));

                        int amountAfterTaxvalue = (amountAfterTaxcc).compareTo(amountAfterTaxSI);
                        int amountBeforeTaxvalue = amountBeforeTaxcc.compareTo(amountBeforeTaxSI);
                        if (roomTypeSI.equalsIgnoreCase(roomType) && amountAfterTaxvalue == 0
                                && amountBeforeTaxvalue == 0) {
                            JSONArray taxArraySI = totalJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
                            if (taxArraySI.length() > 0 && taxArr.length() > 0) {
                                for (int t = 0; t < taxArr.length(); t++) {
                                    JSONObject taxJson = taxArr.getJSONObject(t);
                                    BigDecimal taxValue = taxJson.getBigDecimal(JSON_PROP_TAXVALUE);
                                    String taxName = taxJson.getString(JSON_PROP_TAXNAME);

                                    JSONObject taxJsonSI = taxArraySI.getJSONObject(t);
                                    taxJsonSI.put(JSON_PROP_AMOUNT, taxValue);
                                    taxJsonSI.put(JSON_PROP_TAXDESCRIPTION, taxName);
                                    // TODO : check whether we need to replace SI currency
                                    // code with
                                    // markup commercials currencycode
                                    // taxJsonSI.put("currencyCode", currencyCode);

                                }
                            }
                            
                          String type = totalJson.getString(JSON_PROP_TYPE);
                          PassengerType psgrType = PassengerType.forString(type);
                         // RoomType roomTypeStr =RoomType.forString(roomTypeSI);
                         // RoomCategory roomCategory =RoomCategory.forString(ratePlanCategory);
                       
                          BigDecimal componentPrice = totalPrice.add(amountAfterTax);
                          totalJson.put(JSON_PROP_AMOUNTAFTERTAX, componentPrice);
                          totalJson.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTax);
                            // TODO : check whether we need to replace SI currency code with
                            // markup
                            // commercials currencycode
                            // totalJson.put("currencyCode", currencyCodeCC);
                            
                            //Below code to set Total package fare
                            if(roomTypeSI.equalsIgnoreCase("TWIN")) {
                                if(ratePlanCategory.equalsIgnoreCase("BROCHURE")) {
                                    //If the supplier provides two separate rates for adult and child , only Adult price will be taken into consideration,
                                    //the child price for the search result page will be ignored
                                	//If in case there are multiple TWIN BROCHURE rooms then the lowest price would be taken into consideration
                                   if(psgrType.equals(PassengerType.ADULT)) {

                                        getTotalSearchPkgFareV1(componentPrice,roomTypeSI,roomRateJson, totalPkgFareMap);
                                        }
                                }
                                
                            }
                        }
                    }

                }

            }
        }
    public static void getTotalSearchPkgFare(BigDecimal componentPrice, String roomTypeSI, JSONObject totalJson, Map<String, JSONObject> totalPkgFareMap, PriceComponentsGroup paxIncentivesGroup) {
    
        if(assignFlag) {
            int num = (totalFare).compareTo(componentPrice);
            if( num == 1) {
                totalFare = componentPrice;
                totalPkgFareMap.put(roomTypeSI, totalJson);
                totalPkgFareMap.put(JSON_PROP_INCENTIVES,  (JSONObject) paxIncentivesGroup.toJSON());
        		
            }
        }else {
            totalFare = componentPrice;
            assignFlag=true;
            totalPkgFareMap.put(roomTypeSI, totalJson);
            totalPkgFareMap.put(JSON_PROP_INCENTIVES,  (JSONObject) paxIncentivesGroup.toJSON());
    		
}
 
    }
    
    public static void getTotalSearchPkgFareV1(BigDecimal componentPrice, String roomTypeSI, JSONObject totalJson, Map<String, JSONObject> totalPkgFareMap) {
        
        if(assignFlag) {
            int num = (totalFare).compareTo(componentPrice);
            if( num == 1) {
                totalFare = componentPrice;
                totalPkgFareMap.put(roomTypeSI, totalJson);
            }
        }else {
            totalFare = componentPrice;
            assignFlag=true;
            totalPkgFareMap.put(roomTypeSI, totalJson);
        
}
 
    }
    

    public static void calculatePriceCruise(JSONObject markupCalcJson, String roomType, BigDecimal amountAfterTaxcc,
            BigDecimal amountBeforeTaxcc, JSONObject cruiseComponentJson,BigDecimal totalPrice, Map<String, JSONObject> totalPkgFareMap) {
        
        
        //for (int x = 0; x < cruiseComponentJson.length(); x++) {
        //  JSONObject cruiseCompJson = cruiseComponentJson.getJSONObject(x);
            JSONArray categoryOptionsArray = cruiseComponentJson.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
            for (int y = 0; y < categoryOptionsArray.length(); y++) {
                JSONObject categoryOptionsJson = categoryOptionsArray.getJSONObject(y);
                JSONArray categoryOptionArray = categoryOptionsJson.getJSONArray(JSON_PROP_CATEGORYOPTION);
                for (int z = 0; z < categoryOptionArray.length(); z++) {
                    JSONObject categoryOptionJson = categoryOptionArray.getJSONObject(z);
                    String roomTypeSI = categoryOptionJson.getString(JSON_PROP_CABINTYPE);
                    JSONArray totalArray = categoryOptionJson.getJSONArray(JSON_PROP_TOTAL);
                  
                    
                    for (int t = 0; t < totalArray.length(); t++) {
                        JSONObject totalJson = totalArray.getJSONObject(t);
                        currencyCode =totalJson.optString(JSON_PROP_CURRENCYCODE);
                        
                        BigDecimal amountAfterTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTAFTERTAX,
                                new BigDecimal("0")));
                        BigDecimal amountBeforeTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTBEFORETAX,
                                new BigDecimal("0")));
                        int amountAfterTaxvalue = (amountAfterTaxcc).compareTo(amountAfterTaxSI);
                        int amountBeforeTaxvalue = amountBeforeTaxcc.compareTo(amountBeforeTaxSI);
                        if (roomTypeSI.equalsIgnoreCase(roomType) && amountAfterTaxvalue == 0
                                && amountBeforeTaxvalue == 0) {
                            BigDecimal amountAfterTax = markupCalcJson.getBigDecimal(JSON_PROP_TOTALFARE);
                            BigDecimal amountBeforeTax = markupCalcJson.getJSONObject(JSON_PROP_FAREBREAKUP)
                                    .getBigDecimal(JSON_PROP_BASEFARE);

                            String currencyCodeCC = markupCalcJson.getString(JSON_PROP_COMMCURRENCY);
                            //totalPrice = totalPrice.add(amountAfterTax);
                            JSONArray taxArr = markupCalcJson.getJSONObject(JSON_PROP_FAREBREAKUP)
                                    .getJSONArray(JSON_PROP_TAXDETAILS);

                            JSONArray taxArraySI = totalJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
                            if (taxArraySI.length() > 0 && taxArr.length() > 0) {
                                for (int s = 0; s < taxArr.length(); s++) {
                                    JSONObject taxJson = taxArr.getJSONObject(s);
                                    BigDecimal taxValue = taxJson.getBigDecimal(JSON_PROP_TAXVALUE);
                                    String taxName = taxJson.getString(JSON_PROP_TAXNAME);

                                    JSONObject taxJsonSI = taxArraySI.getJSONObject(s);
                                    taxJsonSI.put(JSON_PROP_AMOUNT, taxValue);
                                    taxJsonSI.put(JSON_PROP_TAXDESCRIPTION, taxName);
                                    // TODO : check whether we need to replace SI currency code
                                    // with markup commercials currencycode
                                    // taxJsonSI.put("currencyCode", currencyCodeCC);

                                }
                            }
                            
                            String type = totalJson.getString(JSON_PROP_TYPE);
                            PassengerType psgrType = PassengerType.forString(type);
                          
                            BigDecimal componentPrice = totalPrice.add(amountAfterTax);
                            totalJson.put(JSON_PROP_AMOUNTAFTERTAX, componentPrice);
                            totalJson.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTax);
                            // TODO : check whether we need to replace SI currency code with
                            // markup commercials currencycode
                            // totalJson.put("currencyCode", currencyCode);

                            //Below code to set Total package fare for cruise where we do not get ratePlanCategory as "BROCHURE"
                            if(roomTypeSI.equalsIgnoreCase("TWIN")) {
                                //If the supplier provides two separate rates for adult and child , only Adult price will be taken into consideration,
                                //the child price for the search result page will be ignored.Also the lowest price for the TWIN paxType would be set.
                            if (psgrType.equals(PassengerType.ADULT)) {

                            	getTotalSearchPkgFareV1(componentPrice, roomTypeSI, totalJson,totalPkgFareMap);
                            }
                        }
                            
                        }

                    }

                }

            }

        //}
    }

    /**
     * 
     * @param dynamicPackElem
     * @return
     */
    public static JSONObject getSupplierResponseDynamicPackageJSON(Element dynamicPackElem, Boolean flag) {

        JSONObject dynamicPacJson = new JSONObject();
        boolean allowOverrideAirDates = Boolean.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem, "./@AllowOverrideAirDates"));
        dynamicPacJson.put(JSON_PROP_ALLOWOVERRIDEAIRDATES, allowOverrideAirDates);
        
        boolean fullPaymentRequiredToBook = Boolean.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem, "./@FullPaymentRequiredToBook"));
        dynamicPacJson.put("fullPaymentRequiredToBook", fullPaymentRequiredToBook);

        Element componentElem = XMLUtils.getFirstElementAtXPath(dynamicPackElem, "./ns:Components");

        JSONObject componentJson = new JSONObject();

        // For Hotel Component
        Element[] hotelComponentElementArray = XMLUtils.getElementsAtXPath(componentElem, "./ns:HotelComponent");

        if (hotelComponentElementArray != null && hotelComponentElementArray.length != 0) {
            for (Element hotelCompElem : hotelComponentElementArray) {
                JSONObject hotelComponentJson = getHotelComponentJSON(hotelCompElem, flag);
                if(hotelComponentJson.getString("dynamicPkgAction").toLowerCase().contains("pre"))
                    componentJson.put(JSON_PROP_PRENIGHT, hotelComponentJson);
                else if(hotelComponentJson.getString("dynamicPkgAction").toLowerCase().contains("post"))
                    componentJson.put(JSON_PROP_POSTNIGHT, hotelComponentJson);
                else 
                    componentJson.put(JSON_PROP_HOTEL_COMPONENT, hotelComponentJson);
            }
        }

        // Air Component

        Element airComponentELement = XMLUtils.getFirstElementAtXPath(componentElem, "./ns:AirComponent");
        if (airComponentELement != null && airComponentELement.hasChildNodes()) {
            Element[] airItineraryElemArray = XMLUtils.getElementsAtXPath(componentElem,"./ns:AirComponent/ns:AirItinerary");

            if (airItineraryElemArray != null && airItineraryElemArray.length != 0) {
                JSONObject airComponentJson = new JSONObject();
                JSONArray airItineraryJsonArr = new JSONArray();

                for (Element airItineryElem : airItineraryElemArray) {

                    JSONObject AirItineryJson = getAitItineryComponentJSON(airItineryElem);
                    airItineraryJsonArr.put(AirItineryJson);

                }

                airComponentJson.put(JSON_PROP_AIRITINERARY, airItineraryJsonArr);
                componentJson.put(JSON_PROP_AIR_COMPONENT, airComponentJson);
            }
        }

        // PackageOptionComponent

        Element packageOptionComponentElem = XMLUtils.getFirstElementAtXPath(componentElem,"./ns:PackageOptionComponent");
        
        Element uniqueIDElem = XMLUtils.getFirstElementAtXPath(packageOptionComponentElem,"./ns:UniqueID");
        if(uniqueIDElem!= null) {
        	JSONObject invoiceDetails = new JSONObject();
        	
        	String id_Context = String.valueOf(XMLUtils.getValueAtXPath(uniqueIDElem, "./@ID_Context"));
        	invoiceDetails.put(JSON_PROP_ID_CONTEXT, id_Context);
        	
        	String id = String.valueOf(XMLUtils.getValueAtXPath(uniqueIDElem, "./@ID"));
        	invoiceDetails.put(JSON_PROP_ID, id);
        	
        	String createdDate = String.valueOf(XMLUtils.getValueAtXPath(uniqueIDElem, "./@CreatedDate"));
        	invoiceDetails.put("createdDate", createdDate);
        	
        	dynamicPacJson.put("invoiceDetails", invoiceDetails);
        }
        
        if (packageOptionComponentElem != null && packageOptionComponentElem.hasChildNodes()) {
            
        	// For Transfer Element
            Element transferComponentsElement = XMLUtils.getFirstElementAtXPath(packageOptionComponentElem,"./ns:PackageOptions/ns:PackageOption/ns:TPA_Extensions/pac:TransferComponents");
            if (transferComponentsElement != null && (transferComponentsElement.hasChildNodes() || transferComponentsElement.hasAttributes())) {
                JSONArray transferComponent = new JSONArray();
                transferComponent = getTransferComponent(transferComponentsElement,flag);
                componentJson.put(JSON_PROP_TRANSFER_COMPONENT, transferComponent);
            }
            
            //For Activities Component
            Element activityComponentsElement = XMLUtils.getFirstElementAtXPath(packageOptionComponentElem,"./ns:PackageOptions/ns:PackageOption/ns:TPA_Extensions/pac:ActivityComponents");
            if (activityComponentsElement != null && (activityComponentsElement.hasChildNodes() || activityComponentsElement.hasAttributes())) {
                JSONArray activityArr = new JSONArray();
                activityArr = getActivityComponent(activityComponentsElement,flag);
                componentJson.put(JSON_PROP_ACTIVITY_COMPONENT, activityArr);
            }
            
            // For Cruise Element
            Element cruiseComponentsElement = XMLUtils.getFirstElementAtXPath(packageOptionComponentElem,"./ns:PackageOptions/ns:PackageOption/ns:TPA_Extensions/pac:CruiseComponents");
            if (cruiseComponentsElement != null && (cruiseComponentsElement.hasChildNodes() || cruiseComponentsElement.hasAttributes())) {
                Element[] cruiseComponentsElemArray = XMLUtils.getElementsAtXPath(cruiseComponentsElement,"./pac:CruiseComponent");
                
                for (Element cruiseCompElem : cruiseComponentsElemArray) {
                JSONObject cruiseComponent = new JSONObject();
                cruiseComponent = getCruiseComponent(cruiseCompElem, flag);
                
                if(cruiseComponent.getString("dynamicPkgAction").toLowerCase().contains("pre"))
                    componentJson.put(JSON_PROP_PRENIGHT, cruiseComponent);
                else if(cruiseComponent.getString("dynamicPkgAction").toLowerCase().contains("post"))
                    componentJson.put(JSON_PROP_POSTNIGHT, cruiseComponent);
                else 
                    componentJson.put(JSON_PROP_CRUISE_COMPONENT, cruiseComponent);
            }
                }
            
            //For Pre-Night and Post-Night
            JSONObject preNight = componentJson.optJSONObject(JSON_PROP_PRENIGHT);
            JSONObject postNight = componentJson.optJSONObject(JSON_PROP_POSTNIGHT);
            if(preNight != null && !(preNight.length() == 0)) {
            	if(componentJson.optJSONObject(JSON_PROP_CRUISE_COMPONENT)!= null && componentJson.optJSONObject(JSON_PROP_CRUISE_COMPONENT).length()!=0) {
            		preNight = transformExtensionNights(preNight, flag);
            		componentJson.put("preNight", preNight);
            	}
            }
            if(postNight != null && !(postNight.length() == 0)) {
            	if(componentJson.optJSONObject(JSON_PROP_CRUISE_COMPONENT)!= null && componentJson.optJSONObject(JSON_PROP_CRUISE_COMPONENT).length()!=0) {
            		postNight = transformExtensionNights(postNight, flag);
            		componentJson.put("postNight", postNight);
            	}
            }
            
            // For Insurance Element
            Element insuranceComponentsElement = XMLUtils.getFirstElementAtXPath(packageOptionComponentElem,"./ns:PackageOptions/ns:PackageOption/ns:TPA_Extensions/pac:InsuranceComponents");

            if (insuranceComponentsElement != null && (insuranceComponentsElement.hasChildNodes() || insuranceComponentsElement.hasAttributes())) {
                JSONArray insuranceComponent = new JSONArray();
                insuranceComponent = getInsuranceComponent(insuranceComponentsElement);
                componentJson.put(JSON_PROP_INSURANCE_COMPONENT, insuranceComponent);
            }

            // For Itinerary Element
            Element itineraryElement = XMLUtils.getFirstElementAtXPath(packageOptionComponentElem,"./ns:PackageOptions/ns:PackageOption/ns:TPA_Extensions/pac:itinerary");
            if (itineraryElement != null && (itineraryElement.hasChildNodes() || itineraryElement.hasAttributes())) {
                JSONObject itineraryComponent = new JSONObject();
                itineraryComponent = getItinerary(itineraryElement);
                componentJson.put("itinerary", itineraryComponent);
            }
        }
        dynamicPacJson.put(JSON_PROP_COMPONENTS, componentJson);

        // ResGuests
        Element[] resGuestElements = XMLUtils.getElementsAtXPath(dynamicPackElem, "./ns:ResGuests/ns:ResGuest");
        if (resGuestElements != null && resGuestElements.length > 0 && flag == true) {
            JSONObject resGuestJson = getResGuest(dynamicPackElem);
            dynamicPacJson.put(JSON_PROP_RESGUESTS, resGuestJson);
        }

        // GlobalInfo
        JSONObject globalInfoJson = new JSONObject();
        Element globalInfoElem = XMLUtils.getFirstElementAtXPath(dynamicPackElem, "./ns:GlobalInfo");

        String availabilityStatus = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:Components/ns:PackageOptionComponent/ns:PackageOptions/ns:PackageOption/@AvailabilityStatus"));
        if (availabilityStatus != null && availabilityStatus.length() > 0)
            globalInfoJson.put(JSON_PROP_AVAILABILITYSTATUS, availabilityStatus);

        String serviceRPH = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:Components/ns:PackageOptionComponent/ns:PackageOptions/ns:PackageOption/@ServiceRPH"));
        if (serviceRPH != null && serviceRPH.length() > 0)
            globalInfoJson.put("serviceRPH", serviceRPH);

        String serviceCategoryCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:Components/ns:PackageOptionComponent/ns:PackageOptions/ns:PackageOption/@ServiceCategoryCode"));
        if (serviceCategoryCode != null && serviceCategoryCode.length() > 0)
            globalInfoJson.put("serviceCategoryCode", serviceCategoryCode);

        globalInfoJson = getGlobalInfo(globalInfoElem,flag);
        dynamicPacJson.put(JSON_PROP_GLOBALINFO, globalInfoJson);

        return dynamicPacJson;

    }

   public static JSONArray getActivityComponent(Element activityComponentsElement, Boolean flag) {
        Element[] activityComponentElemArray = XMLUtils.getElementsAtXPath(activityComponentsElement,"./pac:ActivityComponent");

        JSONArray actArr = new JSONArray();

        for (Element activityCompElem : activityComponentElemArray) {
            JSONObject activityCompJson = new JSONObject();
            
            activityCompJson.put(JSON_PROP_DYNAMICPKGACTION, String.valueOf(XMLUtils.getValueAtXPath(activityCompElem, "./@DynamicPkgAction")));
            activityCompJson.put(JSON_PROP_ID, String.valueOf(XMLUtils.getValueAtXPath(activityCompElem, "./@ID")));
            activityCompJson.put(JSON_PROP_AVAILABILITYSTATUS, String.valueOf(XMLUtils.getValueAtXPath(activityCompElem, "./@AvailabilityStatus")));
            activityCompJson.put("createdDate", String.valueOf(XMLUtils.getValueAtXPath(activityCompElem, "./@CreatedDate")));

            Element activityServiceElem = XMLUtils.getFirstElementAtXPath(activityCompElem, "./pac:ActivityService");
            
            if(XMLUtils.getValueAtXPath(activityServiceElem, "./@Name")!= null)
            	activityCompJson.put("name", String.valueOf(XMLUtils.getValueAtXPath(activityServiceElem, "./@Name")));
            else
            	activityCompJson.put("name", "");
            
            activityCompJson.put("description", String.valueOf(XMLUtils.getValueAtXPath(activityServiceElem, "./@Description")));
            
            Element basicInfo = XMLUtils.getFirstElementAtXPath(activityServiceElem, "./pac:BasicInfo");
            JSONObject basicInfoJson = new JSONObject();
            basicInfoJson.put("name", String.valueOf(XMLUtils.getValueAtXPath(basicInfo, "./@Name")));
            basicInfoJson.put("tourActivityID", String.valueOf(XMLUtils.getValueAtXPath(basicInfo, "./@TourActivityID")));
            activityCompJson.put("basicInfo", basicInfoJson);
            
        	Element[] priceTotalElements = XMLUtils.getElementsAtXPath(activityServiceElem, "./pac:Price/ns:Total");
        	if (priceTotalElements != null && priceTotalElements.length > 0) {
                JSONArray totalArray = new JSONArray();
                JSONObject priceJson = new JSONObject();
                
                for (Element total : priceTotalElements) {
	                JSONObject totalObj = new JSONObject();
	
	                totalObj.put(JSON_PROP_AMOUNTBEFORETAX, String.valueOf(XMLUtils.getValueAtXPath(total, "./@AmountBeforeTax")));
	                totalObj.put(JSON_PROP_AMOUNTAFTERTAX, String.valueOf(XMLUtils.getValueAtXPath(total, "./@AmountAfterTax")));
	                totalObj.put(JSON_PROP_CURRENCYCODE, String.valueOf(XMLUtils.getValueAtXPath(total, "./@CurrencyCode")));
	                totalObj.put(JSON_PROP_TYPE, String.valueOf(XMLUtils.getValueAtXPath(total, "./@Type")));
	
	                JSONObject taxJson = getChargeTax(total);
	
	                totalObj.put(JSON_PROP_TAXES, taxJson);
	                totalArray.put(totalObj);               
                }
                priceJson.put(JSON_PROP_TOTAL, totalArray);
                activityCompJson.put(JSON_PROP_PRICE, priceJson);
            }
            
            Element depositElement = XMLUtils.getFirstElementAtXPath(activityServiceElem, "./pac:Deposits");
            if (depositElement != null) {
            	JSONObject depositjson = new JSONObject();
            	JSONArray depositArr = new JSONArray();
            	Element[] depositElements = XMLUtils.getElementsAtXPath(depositElement, "./pac:Deposit");
            	for (Element deposit : depositElements) {
                    JSONObject depositJson = new JSONObject();
                    depositJson.put("depositAmount", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@DepositAmount")));
                    depositJson.put("currencyCode", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@CurrencyCode")));
                    depositJson.put("type", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@Type")));
                    depositArr.put(depositJson);
            	}
            	depositjson.put("deposit", depositArr);
            	activityCompJson.put("deposits", depositjson);
            }
            
            //For Booking Rule
            Element bookingRulesElement = XMLUtils.getFirstElementAtXPath(activityServiceElem, "./pac:BookingRules");
            if (bookingRulesElement != null) {
            	JSONArray bookingRuleArr = new JSONArray();
            	Element[] bookingRule = XMLUtils.getElementsAtXPath(bookingRulesElement, "./ns:BookingRule");
            	for (Element br : bookingRule) {
                    JSONObject brJson = new JSONObject();
                    brJson.put("end", String.valueOf(XMLUtils.getValueAtXPath(br, "./@End")));
                    brJson.put("start", String.valueOf(XMLUtils.getValueAtXPath(br, "./@Start")));
                    
                    Element lengthsOfStayElem = XMLUtils.getFirstElementAtXPath(br, "./ns:LengthsOfStay");
                    if(lengthsOfStayElem!=null) {
                    	//JSONObject lengthOfStay = new JSONObject();
                    	Element lengthOfStayElem[] = XMLUtils.getElementsAtXPath(lengthsOfStayElem, "./ns:LengthOfStay");
                    	for(Element los : lengthOfStayElem) {
                    		String minOrMax = String.valueOf(XMLUtils.getValueAtXPath(los, "./@MinMaxMessageType"));
                    		String time = String.valueOf(XMLUtils.getValueAtXPath(los, "./@Time"));
                    		String timeUnit = String.valueOf(XMLUtils.getValueAtXPath(los, "./@TimeUnit"));
                    		brJson.put(minOrMax,time + " " + timeUnit);
                    		
                    		//brJson.put("lengthOfStay", lengthOfStay);
                    	}
                    }
                    
                    bookingRuleArr.put(brJson);
            	}
            	activityCompJson.put(JSON_PROP_BOOKINGRULE, bookingRuleArr);
            }
            
            Element[] passengers = XMLUtils.getElementsAtXPath(activityServiceElem, "./pac:Passenger");
            if (passengers != null && passengers.length > 0) {
	            JSONArray passArr = new JSONArray();
	            for (Element pass : passengers) {
	            	JSONObject passJson = new JSONObject();
	            	passJson.put("minimumAge", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MinimumAge")));
	            	passJson.put("maximumAge", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MaximumAge")));
	                passJson.put("maximumPassengers", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MaximumPassengers")));
	                passJson.put("minimumPassengers", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MinimumPassengers")));
	                passJson.put("passengerTypeCode", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@PassengerTypeCode")));
	                passArr.put(passJson);
	            }
	            activityCompJson.put("passenger", passArr);
            }
            
            Element referenceElem = XMLUtils.getFirstElementAtXPath(activityServiceElem, "./pac:Reference");
            if(referenceElem!=null) {
	            JSONObject refernceJson = new JSONObject();
	            refernceJson.put("id", String.valueOf(XMLUtils.getValueAtXPath(referenceElem, "./@ID")));
	            refernceJson.put("id_Context", String.valueOf(XMLUtils.getValueAtXPath(referenceElem, "./@ID_Context")));
	            refernceJson.put("url", String.valueOf(XMLUtils.getValueAtXPath(referenceElem, "./@URL")));
	            activityCompJson.put("reference", refernceJson);
            }
            
            Element timeSpanElem = XMLUtils.getFirstElementAtXPath(activityServiceElem, "./pac:TimeSpan");
            if(timeSpanElem!= null) {
	            JSONObject timeSpanJson = new JSONObject();
	            timeSpanJson.put("end", String.valueOf(XMLUtils.getValueAtXPath(timeSpanElem, "./@End")));
	            timeSpanJson.put("start", String.valueOf(XMLUtils.getValueAtXPath(timeSpanElem, "./@Start")));
	            activityCompJson.put("timeSpan", timeSpanJson);
            }
            
            Element[] guestCountElems = XMLUtils.getElementsAtXPath(activityServiceElem, "./pac:GuestCounts/ns:GuestCount");
            if(guestCountElems!=null) {
		        JSONArray guestCountarr = getGuests(guestCountElems);
		        activityCompJson.put("guestCount", guestCountarr);
            }
            
            actArr.put(activityCompJson);
        }
        return actArr;
    }

	public static JSONObject transformExtensionNights(JSONObject extensionNight, Boolean flag) {
		JSONArray categoryOptions = extensionNight.getJSONArray("categoryOptions");
		JSONArray roomStay = new JSONArray();
		JSONObject revisedExtensionNight = new JSONObject();
		revisedExtensionNight.put("dynamicPkgAction", extensionNight.getString("dynamicPkgAction"));
		
		for(Object categoryOptionsElement : categoryOptions) {
			JSONObject categoryOptionsJson = (JSONObject)categoryOptionsElement;
			JSONArray categoryOption = categoryOptionsJson.getJSONArray("categoryOption");
			
			for(Object categoryOptionElement : categoryOption) {
				JSONObject categoryOptionJson = (JSONObject)categoryOptionElement;
				JSONObject roomStayJson = new JSONObject();
				JSONArray roomRate = new JSONArray();
				
				roomStayJson.put("roomType", categoryOptionJson.optString("cabinType"));
				roomStayJson.put("roomCategory", categoryOptionJson.optString("cabinCategory"));
				roomStayJson.put("roomStayStatus", categoryOptionJson.optString("availabilityStatus"));
				roomStayJson.put(JSON_PROP_DESCRIPTION, categoryOptionJson.optString(JSON_PROP_DESCRIPTION));
				roomStayJson.put(JSON_PROP_ID, categoryOptionJson.optString(JSON_PROP_ID));
				roomStayJson.put(JSON_PROP_NAME, categoryOptionJson.optString(JSON_PROP_NAME));
				roomStayJson.put("timeSpan", categoryOptionJson.optJSONObject("timeSpan"));
				roomStayJson.put("deposits", categoryOptionJson.optJSONObject("deposits"));
				roomStayJson.put(JSON_PROP_BOOKINGRULE, categoryOptionJson.optJSONArray(JSON_PROP_BOOKINGRULE));
				roomStayJson.put("passenger", categoryOptionJson.optJSONArray("passenger"));
				roomStayJson.put("basicPropertyInfo", categoryOptionJson.optJSONArray("basicPropertyInfo"));
				if(flag == false)
					roomStayJson.put("guestCount", categoryOptionJson.optJSONArray("guestCount"));
				else
					roomStayJson.put("guestCount", categoryOptionJson.optJSONObject("guestCount"));
				JSONArray totalArr = categoryOptionJson.getJSONArray("total");
				for(int i = 0; i<totalArr.length(); i++){	
					JSONObject roomRateJson = new JSONObject();
					roomRateJson.put("total", totalArr.getJSONObject(i));					
					roomRate.put(roomRateJson);
				}
				roomStayJson.put("roomRate", roomRate);
				roomStay.put(roomStayJson);
			}
		}
		JSONObject roomStays = new JSONObject();
		roomStays.put("roomStay", roomStay);
		revisedExtensionNight.put("roomStays", roomStays);
		return revisedExtensionNight;
	}

	public static JSONObject getDynamicPkgID(Element dynamicPkgIDElem) {
        JSONObject dynamicPkgJson = new JSONObject();
        
        String id = String.valueOf(XMLUtils.getValueAtXPath(dynamicPkgIDElem, "./@ID"));
        String id_Context = String.valueOf(XMLUtils.getValueAtXPath(dynamicPkgIDElem, "./@ID_Context"));
        String createdDate = String.valueOf(XMLUtils.getValueAtXPath(dynamicPkgIDElem, "./@CreatedDate"));
        String url = String.valueOf(XMLUtils.getValueAtXPath(dynamicPkgIDElem, "./@URL"));
        
        dynamicPkgJson.put("createdDate", createdDate);
        dynamicPkgJson.put("url", url);
        dynamicPkgJson.put(JSON_PROP_ID, id);
        dynamicPkgJson.put(JSON_PROP_ID_CONTEXT, id_Context);

        Element companyNameElem = XMLUtils.getFirstElementAtXPath(dynamicPkgIDElem, "./ns:CompanyName");
        if (companyNameElem != null && (companyNameElem.hasChildNodes() || companyNameElem.hasAttributes())) {
            String companyShortName = String.valueOf(XMLUtils.getValueAtXPath(companyNameElem, "./@CompanyShortName"));
            dynamicPkgJson.put(JSON_PROP_COMPANYNAME, companyShortName);
        }

        Element tourDetailsElem = XMLUtils.getFirstElementAtXPath(dynamicPkgIDElem, "./ns:TPA_Extensions/pac:Pkgs_TPA/pac:TourDetails");
        if (tourDetailsElem != null && (tourDetailsElem.hasChildNodes() || tourDetailsElem.hasAttributes())) {
            JSONObject tourDetails = new JSONObject();

            String tourRefID = String.valueOf(XMLUtils.getValueAtXPath(tourDetailsElem, "./pac:TourRefID"));
            String tourName = String.valueOf(XMLUtils.getValueAtXPath(tourDetailsElem, "./pac:TourName"));
            String tourStartCity = String.valueOf(XMLUtils.getValueAtXPath(tourDetailsElem, "./pac:TourStartCity"));
            String tourEndCity = String.valueOf(XMLUtils.getValueAtXPath(tourDetailsElem, "./pac:TourEndCity"));
            String productCode = String.valueOf(XMLUtils.getValueAtXPath(tourDetailsElem, "./pac:ProductCode"));

            tourDetails.put(JSON_PROP_TOURREFID, tourRefID);
            tourDetails.put(JSON_PROP_TOURNAME, tourName);
            tourDetails.put(JSON_PROP_TOURSTARTCITY, tourStartCity);
            tourDetails.put(JSON_PROP_TOURENDCITY, tourEndCity);
            tourDetails.put("productCode", productCode);
            
            //For start and end address
            JSONObject tourStartAddress = new JSONObject();
            JSONObject tourEndAddress = new JSONObject();
            
            Element tourStartAddressElem = XMLUtils.getFirstElementAtXPath(tourDetailsElem,"./pac:TourStartAddress");
            Element countryElem = XMLUtils.getFirstElementAtXPath(tourStartAddressElem,"./pac:country");
            
            String street = String.valueOf(XMLUtils.getValueAtXPath(tourStartAddressElem, "./pac:street"));
            String city = String.valueOf(XMLUtils.getValueAtXPath(tourStartAddressElem, "./pac:city"));
            String countryCode = String.valueOf(XMLUtils.getValueAtXPath(countryElem, "./pac:Code"));
            String countryName = String.valueOf(XMLUtils.getValueAtXPath(countryElem, "./pac:Name"));
            String postal_zip = String.valueOf(XMLUtils.getValueAtXPath(tourStartAddressElem, "./pac:postal_zip"));
            
            tourStartAddress.put("street", street);
            tourStartAddress.put("city", city);
            tourStartAddress.put("countryCode", countryCode);
            tourStartAddress.put("countryName", countryName);
            tourStartAddress.put("postal_zip", postal_zip);
            
            Element tourEndAddressElem = XMLUtils.getFirstElementAtXPath(tourDetailsElem,"./pac:TourEndAddress");
            Element countryElem1 = XMLUtils.getFirstElementAtXPath(tourEndAddressElem,"./pac:country");
            
            String street1 = String.valueOf(XMLUtils.getValueAtXPath(tourEndAddressElem, "./pac:street"));
            String city1 = String.valueOf(XMLUtils.getValueAtXPath(tourEndAddressElem, "./pac:city"));
            String countryCode1 = String.valueOf(XMLUtils.getValueAtXPath(countryElem1, "./pac:Code"));
            String countryName1 = String.valueOf(XMLUtils.getValueAtXPath(countryElem1, "./pac:Name"));
            String postal_zip1 = String.valueOf(XMLUtils.getValueAtXPath(tourEndAddressElem, "./pac:postal_zip"));
            
            tourEndAddress.put("street", street1);
            tourEndAddress.put("city", city1);
            tourEndAddress.put("countryCode", countryCode1);
            tourEndAddress.put("countryName", countryName1);
            tourEndAddress.put("postal_zip", postal_zip1);
            
            tourDetails.put("tourStartAddress", tourStartAddress);
            tourDetails.put("tourEndAddress", tourEndAddress);
            
            // For IDInfo Elements
            Element[] idInfos = XMLUtils.getElementsAtXPath(dynamicPkgIDElem, "./ns:TPA_Extensions/pac:Pkgs_TPA/pac:TourDetails/pac:IDInfo");

            if (idInfos != null && idInfos.length > 0) {
                JSONArray idInfoArray = new JSONArray();

                for (Element idInfo : idInfos) {
                    JSONObject idInfoObj = new JSONObject();

                    String infoID = String.valueOf(XMLUtils.getValueAtXPath(idInfo, "./pac:ID"));
                    if (infoID != null && infoID.length() > 0)
                        idInfoObj.put(JSON_PROP_ID, infoID);

                    String type = String.valueOf(XMLUtils.getValueAtXPath(idInfo, "./pac:Type"));
                    if (type != null && type.length() > 0)
                        idInfoObj.put(JSON_PROP_TYPE, type);

                    String name = String.valueOf(XMLUtils.getValueAtXPath(idInfo, "./pac:Name"));
                    if (name != null && name.length() > 0)
                        idInfoObj.put(JSON_PROP_NAME, name);

                    String subType = String.valueOf(XMLUtils.getValueAtXPath(idInfo, "./pac:SubType"));
                    if (subType != null && subType.length() > 0)
                        idInfoObj.put(JSON_PROP_SUBTYPE, subType);
                    
                    String url1 = String.valueOf(XMLUtils.getValueAtXPath(idInfo, "./pac:URL"));
                    if (url1 != null && url1.length() > 0)
                        idInfoObj.put("URL", url1);
                    
                    String isAddon = String.valueOf(XMLUtils.getValueAtXPath(idInfo, "./pac:IsAddon"));
                    if (isAddon != null && isAddon.length() > 0)
                        idInfoObj.put("isAddon", isAddon);
                    
                    String startDate = String.valueOf(XMLUtils.getValueAtXPath(idInfo, "./pac:StartDate"));
                    if (startDate != null && startDate.length() > 0)
                        idInfoObj.put("startDate", startDate);
                    
                    String endDate = String.valueOf(XMLUtils.getValueAtXPath(idInfo, "./pac:EndDate"));
                    if (endDate != null && endDate.length() > 0)
                        idInfoObj.put("endDate", endDate);
                    idInfoArray.put(idInfoObj);
                }

                tourDetails.put("idInfo", idInfoArray);
            }

            // For GeoInfo Elements
            Element geoInfos = XMLUtils.getFirstElementAtXPath(dynamicPkgIDElem, "./ns:TPA_Extensions/pac:Pkgs_TPA/pac:TourDetails/pac:GeoInfo");
            if (geoInfos != null && (geoInfos.hasChildNodes() || geoInfos.hasAttributes())) {
                JSONObject geoInfo = new JSONObject();

                Element[] continentElements = XMLUtils.getElementsAtXPath(geoInfos, "./pac:Continent");

                if (continentElements != null && continentElements.length > 0) {
                    JSONArray continentArray = new JSONArray();

                    for (Element continentElement : continentElements) {
                        JSONObject continentObj = new JSONObject();

                        String code = String.valueOf(XMLUtils.getValueAtXPath(continentElement, "./pac:Code"));
                        if (code != null && code.length() > 0)
                            continentObj.put(JSON_PROP_CODE, code);

                        String name = String.valueOf(XMLUtils.getValueAtXPath(continentElement, "./pac:Name"));
                        if (name != null && name.length() > 0)
                            continentObj.put(JSON_PROP_NAME, name);

                        continentArray.put(continentObj);
                    }
                    geoInfo.put("continent", continentArray);
                }

                Element[] countryElements = XMLUtils.getElementsAtXPath(geoInfos, "./pac:Country");
                if (countryElements != null && countryElements.length > 0) {
                    JSONArray countryArray = new JSONArray();

                    for (Element countryElement : countryElements) {
                        JSONObject countryObj = new JSONObject();

                        String code = String.valueOf(XMLUtils.getValueAtXPath(countryElement, "./pac:Code"));
                        if (code != null && code.length() > 0)
                            countryObj.put(JSON_PROP_CODE, code);

                        String name = String.valueOf(XMLUtils.getValueAtXPath(countryElement, "./pac:Name"));
                        if (name != null && name.length() > 0)
                            countryObj.put(JSON_PROP_NAME, name);

                        String continentCode = String
                                .valueOf(XMLUtils.getValueAtXPath(countryElement, "./pac:ContinentCode"));
                        if (continentCode != null && continentCode.length() > 0)
                            countryObj.put("continentCode", continentCode);

                        countryArray.put(countryObj);
                    }
                    geoInfo.put("country", countryArray);
                }

                Element[] locationElements = XMLUtils.getElementsAtXPath(geoInfos, "./pac:Country");
                if (locationElements != null && locationElements.length > 0) {
                    JSONArray locationArray = new JSONArray();

                    for (Element locationElement : locationElements) {
                        JSONObject locationObj = new JSONObject();

                        String code = String.valueOf(XMLUtils.getValueAtXPath(locationElement, "./pac:CountryCode"));
                        if (code != null && code.length() > 0)
                            locationObj.put(JSON_PROP_CODE, code);

                        String name = String.valueOf(XMLUtils.getValueAtXPath(locationElement, "./pac:Name"));
                        if (name != null && name.length() > 0)
                            locationObj.put(JSON_PROP_NAME, name);

                        locationArray.put(locationObj);
                    }
                    geoInfo.put("location", locationArray);
                }
                tourDetails.put("geoInfo", geoInfo);
            }

            // For Section Elements
            Element[] sectionElements = XMLUtils.getElementsAtXPath(dynamicPkgIDElem, "./ns:TPA_Extensions/pac:Pkgs_TPA/pac:TourDetails/pac:Section");
            if (sectionElements != null && sectionElements.length > 0) {
                JSONArray sectionArray = new JSONArray();

                for (Element sectionElement : sectionElements) {
                    JSONObject sectionObj = new JSONObject();

                    String title = String.valueOf(XMLUtils.getValueAtXPath(sectionElement, "./pac:Title"));
                    if (title != null && title.length() > 0)
                        sectionObj.put("title", title);

                    String type = String.valueOf(XMLUtils.getValueAtXPath(sectionElement, "./pac:Type"));
                    if (type != null && type.length() > 0)
                        sectionObj.put(JSON_PROP_TYPE, type);

                    Element[] textElements = XMLUtils.getElementsAtXPath(sectionElement, "./pac:Text");
                    if (textElements != null && textElements.length > 0) {
                        JSONArray textArray = new JSONArray();

                        for (Element textElement : textElements) {
                            JSONObject textObj = new JSONObject();

                            String text = XMLUtils.getElementValue(textElement);
                            if (text != null && text.length() > 0)
                                textObj.put(JSON_PROP_TEXT, text);

                            textArray.put(textObj);
                        }
                        sectionObj.put(JSON_PROP_TEXT, textArray);
                    }
                    sectionArray.put(sectionObj);
                }
                tourDetails.put("section", sectionArray);
            }
            dynamicPkgJson.put(JSON_PROP_TOURDETAILS, tourDetails);
        }
        return dynamicPkgJson;
    }

    public static JSONObject getBookingRule(Element bookingRulesElem) {
        JSONObject bookingRuleJson = new JSONObject();

        String code = String.valueOf(XMLUtils.getValueAtXPath(bookingRulesElem, "./@Code"));
        String codeContext = String.valueOf(XMLUtils.getValueAtXPath(bookingRulesElem, "./@CodeContext"));
        
        Element descriptionElem = XMLUtils.getFirstElementAtXPath(bookingRulesElem, "./ns:Description");
        String name = String.valueOf(XMLUtils.getValueAtXPath(descriptionElem, "./@Name"));
        String text = String.valueOf(XMLUtils.getValueAtXPath(descriptionElem, "./ns:Text"));
        
        bookingRuleJson.put(JSON_PROP_CODE, code);
        bookingRuleJson.put(JSON_PROP_CODECONTEXT, codeContext);
        bookingRuleJson.put(JSON_PROP_NAME, name);
        bookingRuleJson.put(JSON_PROP_TEXT, text);

        return bookingRuleJson;
    }

    public static JSONObject getFee(Element feeElem) {
        JSONObject feeJson = new JSONObject();

        String type = String.valueOf(XMLUtils.getValueAtXPath(feeElem, "./@Type"));

        String amount = String.valueOf(XMLUtils.getValueAtXPath(feeElem, "./@Amount"));
        
        BigDecimal amountAfterTax = Utils.convertToBigDecimal(amount, 0);
        BigDecimal taxTotalAmount = Utils.convertToBigDecimal(String.valueOf(XMLUtils.getValueAtXPath(feeElem, "./ns:Taxes/@Amount")), 0);
        
        String code = String.valueOf(XMLUtils.getValueAtXPath(feeElem, "./@Code"));

        String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(feeElem, "./@CurrencyCode"));

        String maxChargeUnitApplies = String.valueOf(XMLUtils.getValueAtXPath(feeElem, "./@MaxChargeUnitApplies"));

        feeJson.put(JSON_PROP_TYPE, type);
        feeJson.put(JSON_PROP_AMOUNT, amount);
        feeJson.put(JSON_PROP_AMOUNTBEFORETAX, (amountAfterTax.subtract(taxTotalAmount)));
        feeJson.put("feeCode", code);
        feeJson.put(JSON_PROP_CURRENCYCODE, currencyCode);
        feeJson.put(JSON_PROP_MAXCHARGEUNITAPPLIES, maxChargeUnitApplies);

        JSONObject taxJSon = getChargeTax(feeElem);

        feeJson.put(JSON_PROP_TAXES, taxJSon);

        Element descriptionElem = XMLUtils.getFirstElementAtXPath(feeElem, "./ns:Description");
        String name = String.valueOf(XMLUtils.getValueAtXPath(descriptionElem, "./@Name"));
        feeJson.put("feeName", name);

        String text = String.valueOf(XMLUtils.getValueAtXPath(descriptionElem, "./ns:Text"));
        feeJson.put(JSON_PROP_TEXT, text);

        Element pkgs_TPAElem = XMLUtils.getFirstElementAtXPath(feeElem, "./ns:TPA_Extensions/pac:Pkgs_TPA");
        String isIncludedInTour = String.valueOf(XMLUtils.getValueAtXPath(pkgs_TPAElem, "./pac:isIncludedInTour"));
        String RequiredForBooking = String.valueOf(XMLUtils.getValueAtXPath(pkgs_TPAElem, "./pac:RequiredForBooking"));
        
        if (isIncludedInTour != null && isIncludedInTour.length() > 0)
        	feeJson.put(JSON_PROP_ISINCLUDEDINTOUR, isIncludedInTour);
        if (RequiredForBooking != null && RequiredForBooking.length() > 0)
        	feeJson.put(JSON_PROP_REQUIREDFORBOOKING, RequiredForBooking);

        return feeJson;
    }

    public static JSONObject getComment(Element commentElem) {
        JSONObject commentJson = new JSONObject();

        String name = String.valueOf(XMLUtils.getValueAtXPath(commentElem, "./@Name"));
        commentJson.put(JSON_PROP_NAME, name);
        
        String textStr = String.valueOf(XMLUtils.getValueAtXPath(commentElem, "./ns:Text"));
        commentJson.put(JSON_PROP_TEXT, textStr);
        
/*       Element[] texts = XMLUtils.getElementsAtXPath(commentElem, "./ns:Text");

        if (texts != null && texts.length > 0) {
            JSONArray textArray = new JSONArray();
            for (Element text : texts) {
                String textStr = XMLUtils.getElementValue(text);

                textArray.put(textStr);
            }

            commentJson.put(JSON_PROP_TEXT, textArray);
        }*/

        Element[] images = XMLUtils.getElementsAtXPath(commentElem, "./ns:Image");

        if (images != null && images.length > 0) {
            JSONArray imageArray = new JSONArray();
            for (Element image : images) {
                String imageStr = XMLUtils.getElementValue(image);

                imageArray.put(imageStr);
            }

            commentJson.put("images", imageArray);
        }

        Element[] urls = XMLUtils.getElementsAtXPath(commentElem, "./ns:URL");

        if (urls != null && urls.length > 0) {
            JSONArray urlArray = new JSONArray();
            for (Element url : urls) {
                String urlStr = XMLUtils.getElementValue(url);

                urlArray.put(urlStr);
            }

            commentJson.put("urls", urlArray);
        }

        return commentJson;
    }

    public static JSONObject getGlobalInfoTimespan(Element globalInfoElem) {

        Element timeSpanElem = XMLUtils.getFirstElementAtXPath(globalInfoElem, "./ns:TimeSpan");
        JSONObject timeSpanJson = new JSONObject();

        String end = String.valueOf(XMLUtils.getValueAtXPath(timeSpanElem, "./@End"));
        if(!(end.length() == 0))
        	timeSpanJson.put(JSON_PROP_END, end);
        String start = String.valueOf(XMLUtils.getValueAtXPath(timeSpanElem, "./@Start"));
        if(!(start.length() == 0))
        	timeSpanJson.put(JSON_PROP_START, start);
        String travelEndDate = String.valueOf(XMLUtils.getValueAtXPath(timeSpanElem, "./@TravelEndDate"));
        if(!(travelEndDate.length() == 0))
        	timeSpanJson.put(JSON_PROP_TRAVELENDDATE, travelEndDate);
        String travelStartDate = String.valueOf(XMLUtils.getValueAtXPath(timeSpanElem, "./@TravelStartDate"));
        if(!(travelStartDate.length() == 0))
        	timeSpanJson.put(JSON_PROP_TRAVELSTARTDATE, travelStartDate);
        
        Element startDateWindowElem = XMLUtils.getFirstElementAtXPath(timeSpanElem,"./ns:StartDateWindow");
        if(startDateWindowElem!=null) {
	        String latestDate = String.valueOf(XMLUtils.getValueAtXPath(startDateWindowElem, "./@LatestDate"));
	        JSONObject startDateWindowJson = new JSONObject();
	        startDateWindowJson.put("latestDate", latestDate);
	        timeSpanJson.put("startDateWindow", startDateWindowJson);
        }
        
        Element endDateWindowElem = XMLUtils.getFirstElementAtXPath(timeSpanElem,"./ns:EndDateWindow");
        if(endDateWindowElem!=null) {
        	String earliestDate = String.valueOf(XMLUtils.getValueAtXPath(endDateWindowElem, "./@EarliestDate"));
        	JSONObject endDateWindowJson = new JSONObject();
        	endDateWindowJson.put("earliestDate", earliestDate);
        	timeSpanJson.put("endDateWindow", endDateWindowJson);
        }
        return timeSpanJson;
    }

    public static JSONObject getGlobalInfo(Element globalInfoElem, Boolean flag) {

        JSONObject globalInfoJson = new JSONObject();

        // For Time Span
        JSONObject timeSpanJson = getGlobalInfoTimespan(globalInfoElem);
        if(!(timeSpanJson.length() == 0))
        	globalInfoJson.put(JSON_PROP_TIMESPAN, timeSpanJson);

        // Comments
        Element[] commentElems = XMLUtils.getElementsAtXPath(globalInfoElem, "./ns:Comments/ns:Comment");
        JSONArray CommentArray = new JSONArray();

        for (Element commentElem : commentElems) {
            JSONObject CommentJson = getComment(commentElem);
            CommentArray.put(CommentJson);
        }
        globalInfoJson.put(JSON_PROP_COMMENT, CommentArray);

        // CancelPenalties
        Element cancelPenaltiesElem = XMLUtils.getFirstElementAtXPath(globalInfoElem, "./ns:CancelPenalties");
        String cancelPolicyIndicator = String
                .valueOf(XMLUtils.getValueAtXPath(cancelPenaltiesElem, "./@CancelPolicyIndicator"));
        JSONObject cancelPenaltiesJson = new JSONObject();
        if(!(cancelPolicyIndicator.length() == 0))
        {
        	cancelPenaltiesJson.put(JSON_PROP_CANCELPOLICYINDICATOR, cancelPolicyIndicator);
        	globalInfoJson.put(JSON_PROP_CANCELPENALTIES, cancelPenaltiesJson);
        }

        // Fee
        Element[] feeElems = XMLUtils.getElementsAtXPath(globalInfoElem, "./ns:Fees/ns:Fee");
        JSONArray feeArray = new JSONArray();
        for (Element feeElem : feeElems) {
            JSONObject feeJson = getFee(feeElem);
            feeArray.put(feeJson);
        }
        globalInfoJson.put(JSON_PROP_FEE, feeArray);

        // BookingRules
        Element[] bookingRulesElems = XMLUtils.getElementsAtXPath(globalInfoElem, "./ns:BookingRules/ns:BookingRule");
        JSONArray bookingRulesArray = new JSONArray();

        for (Element bookingRulesElem : bookingRulesElems) {
            JSONObject bookingRuleJson = getBookingRule(bookingRulesElem);
            bookingRulesArray.put(bookingRuleJson);
        }
        globalInfoJson.put(JSON_PROP_BOOKINGRULE, bookingRulesArray);

        // DynamicPkgID
        Element dynamicPkgIDElement = XMLUtils.getFirstElementAtXPath(globalInfoElem,
                "./ns:DynamicPkgIDs/ns:DynamicPkgID");
        if (dynamicPkgIDElement != null
                && (dynamicPkgIDElement.hasChildNodes() || dynamicPkgIDElement.hasAttributes())) {
            JSONObject dynamicpacJson = getDynamicPkgID(dynamicPkgIDElement);

            globalInfoJson.put(JSON_PROP_DYNAMICPKGID, dynamicpacJson);
        }
	        
        //TotalCommissions
	    JSONObject totalCommissionsjson = getGlobalInfoTotalCommissions(globalInfoElem);
	    globalInfoJson.put("totalCommissions", totalCommissionsjson);
	        
	    //DepositPayments
	    JSONArray depositPaymentsJson = getGlobalInfoDepositPayments(globalInfoElem);
		globalInfoJson.put("depositPayments", depositPaymentsJson);
	
		//Total
		JSONObject totalObj = getGlobalInfoTotal(globalInfoElem);
		globalInfoJson.put("total", totalObj);
			
        // For Promotions Element
        Element promotionsElement = XMLUtils.getFirstElementAtXPath(globalInfoElem, "./ns:Promotions");

        if (promotionsElement != null && promotionsElement.hasChildNodes()) {
            JSONArray promotion = getPromotions(promotionsElement);
            globalInfoJson.put("promotion", promotion);
        }

        return globalInfoJson;
    }

    public static JSONArray getPromotions(Element promotionsElement) {
    
    Element[] promotionElements = XMLUtils.getElementsAtXPath(promotionsElement, "./ns:Promotion");
    	JSONArray promotion = new JSONArray();
    for (Element promotionElement : promotionElements) {
        JSONObject promotionObj = new JSONObject();

        String isIncludedInTour = String
                .valueOf(XMLUtils.getValueAtXPath(promotionElement, "./@isIncludedInTour"));
        if(isIncludedInTour.length() != 0)
        promotionObj.put(JSON_PROP_ISINCLUDEDINTOUR, isIncludedInTour);

        String amount = String.valueOf(XMLUtils.getValueAtXPath(promotionElement, "./@Amount"));
        promotionObj.put(JSON_PROP_AMOUNT, amount);

        String description = String.valueOf(XMLUtils.getValueAtXPath(promotionElement, "./@Description"));
        promotionObj.put(JSON_PROP_DESCRIPTION, description);

        String id = String.valueOf(XMLUtils.getValueAtXPath(promotionElement, "./@ID"));
        promotionObj.put(JSON_PROP_ID, id);

        String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(promotionElement, "./@CurrencyCode"));
        promotionObj.put(JSON_PROP_CURRENCYCODE, currencyCode);

        promotion.put(promotionObj);
    }
	return promotion;
	
    }

	public static JSONObject getGlobalInfoTotal(Element globalInfoElem) {

		JSONObject totalObj = new JSONObject();
		Element totalElement = XMLUtils.getFirstElementAtXPath(globalInfoElem, "./ns:Total");

		String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(totalElement, "./@CurrencyCode"));
		String amountAfterTax = String.valueOf(XMLUtils.getValueAtXPath(totalElement, "./@AmountAfterTax"));
		String type = String.valueOf(XMLUtils.getValueAtXPath(totalElement, "./@Type"));

		totalObj.put("currencyCode", currencyCode);
		totalObj.put("amountAfterTax", amountAfterTax);
		totalObj.put("type", type);

		return totalObj;
	}

	public static JSONArray getGlobalInfoDepositPayments(Element globalInfoElem) {
		Element[] guaranteePaymentElements = XMLUtils.getElementsAtXPath(globalInfoElem,
				"./ns:DepositPayments/ns:GuaranteePayment");
		JSONArray guaranteePaymentArray = new JSONArray();
		for (Element guaranteePaymentElement : guaranteePaymentElements) {
			JSONObject guaranteePaymentJson = new JSONObject();

			Element globalInfoDepositPaymentsElement = XMLUtils.getFirstElementAtXPath(guaranteePaymentElement,
					"./ns:AmountPercent");
			String currencyCode = String
					.valueOf(XMLUtils.getValueAtXPath(globalInfoDepositPaymentsElement, "./@CurrencyCode"));
			String amount = String.valueOf(XMLUtils.getValueAtXPath(globalInfoDepositPaymentsElement, "./@Amount"));
			guaranteePaymentJson.put("currencyCode", currencyCode);
			guaranteePaymentJson.put("amount", amount);

			guaranteePaymentArray.put(guaranteePaymentJson);
		}

		return guaranteePaymentArray;
	}

	public static JSONObject getGlobalInfoTotalCommissions(Element globalInfoElem) {
		JSONObject commissionCurrencyAmountAttributeJson = new JSONObject();

		Element globalInfoBookingRuleDescriptionElement = XMLUtils.getFirstElementAtXPath(globalInfoElem,
				"./ns:TotalCommissions/ns:CommissionPayableAmount");
		String currencyCode = String
				.valueOf(XMLUtils.getValueAtXPath(globalInfoBookingRuleDescriptionElement, "./@CurrencyCode"));
		String amount = String.valueOf(XMLUtils.getValueAtXPath(globalInfoBookingRuleDescriptionElement, "./@Amount"));
		commissionCurrencyAmountAttributeJson.put("currencyCode", currencyCode);
		commissionCurrencyAmountAttributeJson.put("amount", amount);

		return commissionCurrencyAmountAttributeJson;
	}

	public static JSONObject getResGuest(Element dynamicPackElem) {
        Element resGuestsElem = XMLUtils.getFirstElementAtXPath(dynamicPackElem,
                "./ns:ResGuests/ns:ResGuest/ns:TPA_Extensions/pac:Pkgs_TPA/pac:TourOccupancyRules");
        JSONObject resGuestJson = new JSONObject();
        JSONObject tourOccupancyRules = new JSONObject();

        JSONObject adultJson = new JSONObject();
        Element adultElem = XMLUtils.getFirstElementAtXPath(resGuestsElem, "./pac:Adult");
        String minimumAge = String.valueOf(XMLUtils.getValueAtXPath(adultElem, "./@MinimumAge"));
        adultJson.put(JSON_PROP_MINIMUMAGE, minimumAge);

        String maximumAge = String.valueOf(XMLUtils.getValueAtXPath(adultElem, "./@MaximumAge"));
        adultJson.put(JSON_PROP_MAXIMUMAGE, maximumAge);

        JSONObject childJson = new JSONObject();
        Element childElem = XMLUtils.getFirstElementAtXPath(dynamicPackElem,
                "./ns:ResGuests/ns:ResGuest/ns:TPA_Extensions/pac:Pkgs_TPA/pac:TourOccupancyRules/pac:Child");
        String minimumAge1 = String.valueOf(XMLUtils.getValueAtXPath(childElem, "./@MinimumAge"));
        childJson.put(JSON_PROP_MINIMUMAGE, minimumAge1);

        String maximumAge1 = String.valueOf(XMLUtils.getValueAtXPath(childElem, "./@MaximumAge"));
        childJson.put(JSON_PROP_MAXIMUMAGE, maximumAge1);

        tourOccupancyRules.put(JSON_PROP_ADULT, adultJson);
        tourOccupancyRules.put(JSON_PROP_CHILD, childJson);

        resGuestJson.put("tourOccupancyRules", tourOccupancyRules);

        return resGuestJson;
    }

    public static JSONObject getPackageOption(Element packageOptionsElem, Boolean flag) {
        JSONObject packageOption = new JSONObject();
        Element packageOptionElem = XMLUtils.getFirstElementAtXPath(packageOptionsElem, "./ns:PackageOption");

        String availabilityStatus = String
                .valueOf(XMLUtils.getValueAtXPath(packageOptionElem, "./@AvailabilityStatus"));
        packageOption.put(JSON_PROP_AVAILABILITYSTATUS, availabilityStatus);

        String iD_Context = String.valueOf(XMLUtils.getValueAtXPath(packageOptionElem, "./@ID_Context"));
        packageOption.put(JSON_PROP_ID_CONTEXT, iD_Context);

        String quoteID = String.valueOf(XMLUtils.getValueAtXPath(packageOptionElem, "./@QuoteID"));
        packageOption.put(JSON_PROP_QUOTEID, quoteID);

        // TPA_Extensions
        JSONObject tPA_Extensions = new JSONObject();
        Element tPA_ExtensionsElem = XMLUtils.getFirstElementAtXPath(packageOptionElem, "./ns:TPA_Extensions");

        Element transferComponentsElem = XMLUtils.getFirstElementAtXPath(tPA_ExtensionsElem, "./pac:TransferComponents");
        Element[] transferComponentElemArray = XMLUtils.getElementsAtXPath(transferComponentsElem, "./pac:TransferComponent");
        
        JSONArray transfercompArray = new JSONArray();
        for (Element transferCompElem : transferComponentElemArray) {
            JSONObject transferCompJson = new JSONObject();
            String dynamicPkgAction = String.valueOf(XMLUtils.getValueAtXPath(transferCompElem, "./@DynamicPkgAction"));
            transferCompJson.put(JSON_PROP_DYNAMICPKGACTION, dynamicPkgAction);

            JSONArray GroundServiceJsonArray = getGroundService(transferCompElem, dynamicPkgAction,false);
            transferCompJson.put(JSON_PROP_GROUNDSERVICE, GroundServiceJsonArray);
            transfercompArray.put(transferCompJson);
        }

        // CruiseComponents
        Element[] cruiseComponentsElemArray = XMLUtils.getElementsAtXPath(tPA_ExtensionsElem, "./pac:CruiseComponents/pac:CruiseComponent");

        JSONArray cruisecompArray = new JSONArray();
        for (Element cruiseCompElem : cruiseComponentsElemArray) {
            JSONObject cruiseCompJson = new JSONObject();
            String dynamicPkgAction = String.valueOf(XMLUtils.getValueAtXPath(cruiseCompElem, "./@DynamicPkgAction"));
            cruiseCompJson.put(JSON_PROP_DYNAMICPKGACTION, dynamicPkgAction);

            String name = String.valueOf(XMLUtils.getValueAtXPath(cruiseCompElem, "./@Name"));
            cruiseCompJson.put(JSON_PROP_NAME, name);
            cruisecompArray.put(cruiseCompJson);

            JSONArray categoryOptionJsonArray = getCategoryOption(cruiseCompElem, flag);
            cruiseCompJson.put(JSON_PROP_CATEGORYOPTION, categoryOptionJsonArray);

        }

        tPA_Extensions.put(JSON_PROP_TRANSFER_COMPONENT, transfercompArray);
        tPA_Extensions.put(JSON_PROP_CRUISE_COMPONENT, cruisecompArray);

        // InsuranceComponents
        Element insuranceComponentsElem = XMLUtils.getFirstElementAtXPath(tPA_ExtensionsElem,"./pac:InsuranceComponents/pac:InsuranceComponent");

        JSONObject insuranceCompJson = new JSONObject();
        String dynamicPkgAction = String.valueOf(XMLUtils.getValueAtXPath(insuranceComponentsElem, "./@DynamicPkgAction"));
        insuranceCompJson.put(JSON_PROP_DYNAMICPKGACTION, dynamicPkgAction);

        String isIncludedInTour = String.valueOf(XMLUtils.getValueAtXPath(insuranceComponentsElem, "./@isIncludedInTour"));
        insuranceCompJson.put(JSON_PROP_ISINCLUDEDINTOUR, isIncludedInTour);
        
        Element insCoverageDetailElem = XMLUtils.getFirstElementAtXPath(insuranceComponentsElem, "./pac:InsCoverageDetail");
        JSONObject insCoverageDetail = new JSONObject();
        String description = String.valueOf(XMLUtils.getValueAtXPath(insCoverageDetailElem, "./@Description"));
        String name = String.valueOf(XMLUtils.getValueAtXPath(insCoverageDetailElem, "./@Name"));
                
        insCoverageDetail.put(JSON_PROP_DESCRIPTION, description);
        insCoverageDetail.put(JSON_PROP_NAME, name);

        insuranceCompJson.put(JSON_PROP_INSCOVERAGEDETAIL, insCoverageDetail);

        // PlanCost
        Element[] planCostElems = XMLUtils.getElementsAtXPath(tPA_ExtensionsElem, "./pac:InsuranceComponents/pac:InsuranceComponent/pac:PlanCost");
        JSONArray planCostArray = new JSONArray();
        for (Element PlanCostElem : planCostElems) {
            JSONObject planCostJson = getPlanCost(PlanCostElem);
            planCostArray.put(planCostJson);
        }

        insuranceCompJson.put(JSON_PROP_PLANCOST, planCostArray);

        tPA_Extensions.put(JSON_PROP_INSURANCECOMPONENT, insuranceCompJson);

        packageOption.put(JSON_PROP_TPA_EXTENSIONS, tPA_Extensions);

        return packageOption;
    }

    public static JSONArray getCategoryOption(Element cruiseCompElem, Boolean flag) {

        Element[] categoryOptionsElements = XMLUtils.getElementsAtXPath(cruiseCompElem, "./pac:CategoryOptions");
        JSONArray categoryOptionsArray = new JSONArray();

        for (Element categoryOptionsElement : categoryOptionsElements) {
            JSONObject categoryOptionsJSON = new JSONObject();

            Element[] categoryOptionElements = XMLUtils.getElementsAtXPath(categoryOptionsElement,"./pac:CategoryOption");

            JSONArray categoryOptionArray = new JSONArray();

            for (Element categoryOptionElement : categoryOptionElements) {
                JSONObject categoryOptionJSON = new JSONObject();
                //12-4-18 Added CabinCategory and CabinType(Rahul)
                String cabinCategory = String.valueOf(XMLUtils.getValueAtXPath(categoryOptionElement, "./@CabinCategory"));
                String description = String.valueOf(XMLUtils.getValueAtXPath(categoryOptionElement, "./@Description"));
                String availabilityStatus = String.valueOf(XMLUtils.getValueAtXPath(categoryOptionElement, "./@AvailabilityStatus"));
                String cabinType = String.valueOf(XMLUtils.getValueAtXPath(categoryOptionElement, "./@CabinType"));
                String cabinId = String.valueOf(XMLUtils.getValueAtXPath(categoryOptionElement, "./@ID"));
                String cabinName = String.valueOf(XMLUtils.getValueAtXPath(categoryOptionElement, "./@Name"));

                categoryOptionJSON.put(JSON_PROP_CABINCATEGORY, cabinCategory);
                categoryOptionJSON.put(JSON_PROP_DESCRIPTION, description);
                categoryOptionJSON.put(JSON_PROP_AVAILABILITYSTATUS, availabilityStatus);
                categoryOptionJSON.put(JSON_PROP_CABINTYPE, cabinType);
                categoryOptionJSON.put(JSON_PROP_ID, cabinId);
                categoryOptionJSON.put(JSON_PROP_NAME, cabinName);

                String maximumLengthOfStay = String.valueOf(XMLUtils.getValueAtXPath(categoryOptionElement, "./@MaximumLengthOfStay"));
                if (maximumLengthOfStay != null && maximumLengthOfStay.length() > 0)
                    categoryOptionJSON.put("maximumLengthOfStay", maximumLengthOfStay);

                String minimumLengthOfStay = String.valueOf(XMLUtils.getValueAtXPath(categoryOptionElement, "./@MinimumLengthOfStay"));
                if (minimumLengthOfStay != null && minimumLengthOfStay.length() > 0)
                    categoryOptionJSON.put("minimumLengthOfStay", minimumLengthOfStay);

                String isIncludedInTour = String.valueOf(XMLUtils.getValueAtXPath(categoryOptionElement, "./@isIncludedInTour"));
                if (isIncludedInTour != null && isIncludedInTour.length() > 0)
                    categoryOptionJSON.put(JSON_PROP_ISINCLUDEDINTOUR, isIncludedInTour);

                // For Total Element
                Element priceElem = XMLUtils.getFirstElementAtXPath(categoryOptionElement, "./pac:Price");

                Element[] totalElems = XMLUtils.getElementsAtXPath(priceElem, "./ns:Total");
                JSONArray totalJson = new JSONArray();

                for (Element totalElem : totalElems) {
                    JSONObject totalObj = new JSONObject();

                    String amountBeforeTax = String.valueOf(XMLUtils.getValueAtXPath(totalElem, "./@AmountBeforeTax"));
                    String amountAfterTax = String.valueOf(XMLUtils.getValueAtXPath(totalElem, "./@AmountAfterTax"));
                    String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(totalElem, "./@CurrencyCode"));
                    String totalType = String.valueOf(XMLUtils.getValueAtXPath(totalElem, "./@Type"));

                    totalObj.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTax);
                    totalObj.put(JSON_PROP_CURRENCYCODE, currencyCode);
                    totalObj.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTax);
                    totalObj.put(JSON_PROP_TYPE, totalType);

                    JSONObject taxJson = getChargeTax(totalElem);

                    totalObj.put(JSON_PROP_TAXES, taxJson);
                    totalJson.put(totalObj);
                }
                categoryOptionJSON.put("total", totalJson);

                // For Guest Count Element
                if(flag == false) {
                	//ForGuestCount
                    Element[] guestCountElems = XMLUtils.getElementsAtXPath(categoryOptionElement, "./pac:GuestCounts/ns:GuestCount");
                    if(guestCountElems!= null) {
	        	        JSONArray guestCountarr = getGuests(guestCountElems);
	        	        categoryOptionJSON.put("guestCount", guestCountarr);
                    }
                }
                else {
	                Element guestCountElem = XMLUtils.getFirstElementAtXPath(categoryOptionElement, "./pac:GuestCounts/ns:GuestCount");
	                if(guestCountElem!= null) {
		                JSONObject guestCountJson = new JSONObject();
		                String count = String.valueOf(XMLUtils.getValueAtXPath(guestCountElem, "./@Count"));
		                guestCountJson.put(JSON_PROP_COUNT, count);
		                categoryOptionJSON.put(JSON_PROP_GUESTCOUNT, guestCountJson);
	                }
                }
                
                //For TimeSpan
                Element timeSpanElem = XMLUtils.getFirstElementAtXPath(categoryOptionElement,"./pac:TimeSpan");
                if (timeSpanElem != null && (timeSpanElem.hasChildNodes() || timeSpanElem.hasAttributes())) {
	                JSONObject timeSpanJson = getTimeSpan(timeSpanElem);
	                categoryOptionJSON.put("timeSpan", timeSpanJson);
	            }
                
                // For Address Element
                Element addressElement = XMLUtils.getFirstElementAtXPath(categoryOptionElement, "./pac:Address");

                if (addressElement != null && (addressElement.hasChildNodes() || addressElement.hasAttributes())) {
                    JSONObject address = new JSONObject();

                    Element[] addressLineElements = XMLUtils.getElementsAtXPath(addressElement, "./ns:AddressLine");
                    JSONArray addressLineArray = new JSONArray();

                    for (Element addressLineElement : addressLineElements) {
                        JSONObject addressLine = new JSONObject();

                        String addressLineValue = XMLUtils.getElementValue(addressLineElement);
                        addressLine.put("addressLine", addressLineValue);
                        addressLineArray.put(addressLine);
                    }
                    address.put("addressLines", addressLineArray);

                    Element cityNameElement = XMLUtils.getFirstElementAtXPath(addressElement, "./ns:CityName");
                    String cityName = XMLUtils.getElementValue(cityNameElement);
                    address.put("cityName", cityName);

                    Element postalCodeElement = XMLUtils.getFirstElementAtXPath(addressElement, "./ns:PostalCode");
                    String postalCode = XMLUtils.getElementValue(postalCodeElement);
                    address.put("postalCode", postalCode);

                    Element countyElement = XMLUtils.getFirstElementAtXPath(addressElement, "./ns:County");
                    String county = XMLUtils.getElementValue(countyElement);
                    address.put("county", county);

                    Element stateProvElement = XMLUtils.getFirstElementAtXPath(addressElement, "./ns:StateProv");
                    String stateProv = XMLUtils.getElementValue(stateProvElement);
                    address.put("stateProv", stateProv);

                    Element countryNameElement = XMLUtils.getFirstElementAtXPath(addressElement, "./ns:CountryName");
                    String countryName = XMLUtils.getElementValue(countryNameElement);
                    address.put("countryName", countryName);

                    categoryOptionJSON.put("address", address);
                }

                // Last MinuteDiscount Element
                Element[] lastMinuteDiscountsElement = XMLUtils.getElementsAtXPath(categoryOptionElement,"./pac:LastMinuteDiscount/pac:LastMinuteDiscounts");
                if (lastMinuteDiscountsElement != null && lastMinuteDiscountsElement.length > 0) {
                    JSONArray lastMinuteDiscounts = new JSONArray();
                    for (Element lastMinuteDiscountElement : lastMinuteDiscountsElement) {
                        JSONObject lastMinuteDiscount = new JSONObject();

                        String amountBeforeTaxLMD = String.valueOf(XMLUtils.getValueAtXPath(lastMinuteDiscountElement, "./@AmountBeforeTax"));
                        lastMinuteDiscount.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTaxLMD);

                        String amountAfterTaxLMD = String.valueOf(XMLUtils.getValueAtXPath(lastMinuteDiscountElement, "./@AmountAfterTax"));
                        lastMinuteDiscount.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTaxLMD);

                        String CurrencyCodeLMD = String.valueOf(XMLUtils.getValueAtXPath(lastMinuteDiscountElement, "./@CurrencyCode"));
                        lastMinuteDiscount.put(JSON_PROP_CURRENCYCODE, CurrencyCodeLMD);

                        String typeLMD = String.valueOf(XMLUtils.getValueAtXPath(lastMinuteDiscountElement, "./@Type"));
                        lastMinuteDiscount.put(JSON_PROP_TYPE, typeLMD);

                        lastMinuteDiscounts.put(lastMinuteDiscount);
                    }

                    categoryOptionJSON.put("lastMinuteDiscounts", lastMinuteDiscounts);
                }
                
                //For depositElements
                Element depositElement = XMLUtils.getFirstElementAtXPath(categoryOptionElement, "./pac:Deposits");
                if (depositElement != null) {
                	JSONObject depositjson = new JSONObject();
                	JSONArray depositArr = new JSONArray();
                	Element[] depositElements = XMLUtils.getElementsAtXPath(depositElement, "./pac:Deposit");
                	for (Element deposit : depositElements) {
                        JSONObject depositJson = new JSONObject();
                        depositJson.put("depositAmount", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@DepositAmount")));
                        depositJson.put("currencyCode", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@CurrencyCode")));
                        depositJson.put("type", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@Type")));
                        depositArr.put(depositJson);
                	}
                	depositjson.put("deposit", depositArr);
                	categoryOptionJSON.put("deposits", depositjson);
                }
                
                //For Promotion
                Element promotionElement = XMLUtils.getFirstElementAtXPath(categoryOptionElement, "./pac:Promotion");
                if (promotionElement != null) {
                	JSONObject promotionJson = new JSONObject();
                	promotionJson.put("amountAfterTax", String.valueOf(XMLUtils.getValueAtXPath(promotionElement, "./@AmountAfterTax")));
                	promotionJson.put("amountBeforeTax", String.valueOf(XMLUtils.getValueAtXPath(promotionElement, "./@AmountBeforeTax")));
                	promotionJson.put("currencyCode", String.valueOf(XMLUtils.getValueAtXPath(promotionElement, "./@CurrencyCode")));
                	promotionJson.put("discountCode", String.valueOf(XMLUtils.getValueAtXPath(promotionElement, "./@DiscountCode")));
                	promotionJson.put("type", String.valueOf(XMLUtils.getValueAtXPath(promotionElement, "./@Type")));
                	
                	JSONArray discountResonArr = new JSONArray();
                	Element[] discountElements = XMLUtils.getElementsAtXPath(promotionElement, "./ns:DiscountReason");
                	for (Element discount : discountElements) {
                        JSONObject reasonJson = new JSONObject();
                        reasonJson.put("url", String.valueOf(XMLUtils.getValueAtXPath(discount, "./ns:URL")));
                        discountResonArr.put(reasonJson);
                	}
                	promotionJson.put("discountReason", discountResonArr);
                	categoryOptionJSON.put("promotion", promotionJson);
                }
                
                // For Passenger Element
                Element[] passengerElements = XMLUtils.getElementsAtXPath(categoryOptionElement, "./pac:Passenger");
                if (passengerElements != null && passengerElements.length > 0) {
                    JSONArray passengers = new JSONArray();

                    for (Element passengerElement : passengerElements) {
                        JSONObject passenger = new JSONObject();
                        
                        String minimumAge = String.valueOf(XMLUtils.getValueAtXPath(passengerElement, "./@MinimumAge"));
                        if (minimumAge != null & minimumAge.length() > 0)
                            passenger.put("minimumAge", minimumAge);
                        
                        String maximumAge = String.valueOf(XMLUtils.getValueAtXPath(passengerElement, "./@MaximumAge"));
                        if (maximumAge != null & maximumAge.length() > 0)
                            passenger.put("maximumAge", maximumAge);
                        
                        String passengerTypeCode = String.valueOf(XMLUtils.getValueAtXPath(passengerElement, "./@PassengerTypeCode"));
                        if (passengerTypeCode != null & passengerTypeCode.length() > 0)
                            passenger.put("passengerTypeCode", passengerTypeCode);
                        
                        String maximumPassengers = String.valueOf(XMLUtils.getValueAtXPath(passengerElement, "./@MaximumPassengers"));
                        if (maximumPassengers != null & maximumPassengers.length() > 0)
                            passenger.put("maximumPassengers", maximumPassengers);

                        String minimumPassengers = String.valueOf(XMLUtils.getValueAtXPath(passengerElement, "./@MinimumPassengers"));
                        if (minimumPassengers != null & minimumPassengers.length() > 0)
                            passenger.put("minimumPassengers", minimumPassengers);

                        String minimumPayingPassengers = String.valueOf(XMLUtils.getValueAtXPath(passengerElement, "./@MinimumPayingPassengers"));
                        if (minimumPayingPassengers != null & minimumPayingPassengers.length() > 0)
                            passenger.put("minimumPayingPassengers", minimumPayingPassengers);

                        passengers.put(passenger);
                    }
                    categoryOptionJSON.put("passenger", passengers);
                }
                
                //For Booking Rules
                Element bookingRulesElement = XMLUtils.getFirstElementAtXPath(categoryOptionElement, "./pac:BookingRules");
                if (bookingRulesElement != null) {
                	JSONArray bookingRuleArr = new JSONArray();
                	Element[] bookingRule = XMLUtils.getElementsAtXPath(bookingRulesElement, "./ns:BookingRule");
                	for (Element br : bookingRule) {
                        JSONObject brJson = new JSONObject();
                        
                        Element lengthsOfStayElem = XMLUtils.getFirstElementAtXPath(br, "./ns:LengthsOfStay");
                        if(lengthsOfStayElem!=null) {
                        	JSONArray losArr = new JSONArray();
                        	Element lengthOfStayElem[] = XMLUtils.getElementsAtXPath(lengthsOfStayElem, "./ns:LengthOfStay");
                        	for(Element los : lengthOfStayElem) {
                        		JSONObject lengthOfStay = new JSONObject();
                        		String minOrMax = String.valueOf(XMLUtils.getValueAtXPath(los, "./@MinMaxMessageType"));
                        		String time = String.valueOf(XMLUtils.getValueAtXPath(los, "./@Time"));
                        		String timeUnit = String.valueOf(XMLUtils.getValueAtXPath(los, "./@TimeUnit"));
                        		if(minOrMax.contains("Min"))
                        			lengthOfStay.put("minLos",time + " " + timeUnit);
                        		else
                        			lengthOfStay.put("maxLos",time + " " + timeUnit);
                        		losArr.put(lengthOfStay);
                        	}
                        	brJson.put("lengthOfStay", losArr);
                        }
                        else {
                        	brJson.put("end", String.valueOf(XMLUtils.getValueAtXPath(br, "./@End")));
                            brJson.put("start", String.valueOf(XMLUtils.getValueAtXPath(br, "./@Start")));
                        }
                        bookingRuleArr.put(brJson);
                	}
                	categoryOptionJSON.put(JSON_PROP_BOOKINGRULE, bookingRuleArr);
                }
                
                // For Cabin Options Element
                Element cabinOptionsElem = XMLUtils.getFirstElementAtXPath(categoryOptionElement, "./pac:CabinOptions");

                Element[] cabinOptionElems = XMLUtils.getElementsAtXPath(cabinOptionsElem, "./pac:CabinOption");
                JSONArray cabinOptionArr = new JSONArray();
                for (Element cabinOptionElem : cabinOptionElems) {
                    JSONObject cabinOptionJson = new JSONObject();

                    String cabinNumber = String.valueOf(XMLUtils.getValueAtXPath(cabinOptionElem, "./@CabinNumber"));
                    String status = String.valueOf(XMLUtils.getValueAtXPath(cabinOptionElem, "./@Status"));
                    String maxOccupancy = String.valueOf(XMLUtils.getValueAtXPath(cabinOptionElem, "./@MaxOccupancy"));
                    String minOccupancy = String.valueOf(XMLUtils.getValueAtXPath(cabinOptionElem, "./@MinOccupancy"));

                    cabinOptionJson.put(JSON_PROP_CABINNUMBER, cabinNumber);
                    cabinOptionJson.put(JSON_PROP_STATUS, status);
                    cabinOptionJson.put("maxOccupancy", maxOccupancy);
                    cabinOptionJson.put("minOccupancy", minOccupancy);

                    cabinOptionArr.put(cabinOptionJson);

                }
                categoryOptionJSON.put(JSON_PROP_CABINOPTION, cabinOptionArr);
                
                //For BasicPropertyInfo
                Element basicPropElem[] = XMLUtils.getElementsAtXPath(categoryOptionElement, "./pac:BasicPropertyInfo");
                JSONArray basicInfoArr = new JSONArray();
                for (Element basicInfoElem : basicPropElem) {
                    JSONObject basicInfoJson = new JSONObject();
                	
                    String hotelCode = String.valueOf(XMLUtils.getValueAtXPath(basicInfoElem, "./@HotelCode"));
                    String hotelCodeContext = String.valueOf(XMLUtils.getValueAtXPath(basicInfoElem, "./@HotelCodeContext"));
                    String hotelName = String.valueOf(XMLUtils.getValueAtXPath(basicInfoElem, "./@HotelName"));

                    basicInfoJson.put("hotelName", hotelName);
                    basicInfoJson.put("hotelCode", hotelCode);
                    basicInfoJson.put("hotelCodeContext", hotelCodeContext);
                	
                    basicInfoArr.put(basicInfoJson);
                }
                categoryOptionJSON.put("basicPropertyInfo", basicInfoArr);
                
                categoryOptionArray.put(categoryOptionJSON);
            }

            categoryOptionsJSON.put(JSON_PROP_CATEGORYOPTION, categoryOptionArray);
            categoryOptionsArray.put(categoryOptionsJSON);
        }

        return categoryOptionsArray;
    }

    public static JSONArray getTransferComponent(Element transferComponentsElement,Boolean flag) {
        Element[] transferComponentElemArray = XMLUtils.getElementsAtXPath(transferComponentsElement,"./pac:TransferComponent");

        JSONArray transfercompArray = new JSONArray();

        for (Element transferCompElem : transferComponentElemArray) {
            JSONObject transferCompJson = new JSONObject();
            String dynamicPkgAction = String.valueOf(XMLUtils.getValueAtXPath(transferCompElem, "./@DynamicPkgAction"));
            
            transferCompJson.put(JSON_PROP_DYNAMICPKGACTION, dynamicPkgAction);
            transferCompJson.put("id", String.valueOf(XMLUtils.getValueAtXPath(transferCompElem, "./@ID")));

            JSONArray GroundServiceJsonArray = getGroundService(transferCompElem, dynamicPkgAction,flag);
            transferCompJson.put(JSON_PROP_GROUNDSERVICE, GroundServiceJsonArray);
            transfercompArray.put(transferCompJson);
        }

        return transfercompArray;
    }

    public static JSONObject getCruiseComponent(Element cruiseComponentsElement, Boolean flag) {
            JSONObject cruiseCompJson = new JSONObject();
            String dynamicPkgAction = String.valueOf(XMLUtils.getValueAtXPath(cruiseComponentsElement, "./@DynamicPkgAction"));
            cruiseCompJson.put(JSON_PROP_DYNAMICPKGACTION, dynamicPkgAction);

            String name = String.valueOf(XMLUtils.getValueAtXPath(cruiseComponentsElement, "./@Name"));
            cruiseCompJson.put(JSON_PROP_NAME, name);

            String id = String.valueOf(XMLUtils.getValueAtXPath(cruiseComponentsElement, "./@ID"));
            cruiseCompJson.put("id", id);

            JSONArray categoryOptionJsonArray = getCategoryOption(cruiseComponentsElement, flag);
            cruiseCompJson.put("categoryOptions", categoryOptionJsonArray);
        return cruiseCompJson;
    }

    public static JSONArray getInsuranceComponent(Element insuranceComponentsElement) {
        // InsuranceComponents
        Element[] insuranceComponentElements = XMLUtils.getElementsAtXPath(insuranceComponentsElement,"./pac:InsuranceComponent");
        JSONArray insuranceComponentArray = new JSONArray();

        for (Element insuranceComponentElement : insuranceComponentElements) {
            JSONObject insuranceComponent = new JSONObject();

            String dynamicPkgAction = String
                    .valueOf(XMLUtils.getValueAtXPath(insuranceComponentElement, "./@DynamicPkgAction"));
            insuranceComponent.put(JSON_PROP_DYNAMICPKGACTION, dynamicPkgAction);

            String isIncludedInTour = String
                    .valueOf(XMLUtils.getValueAtXPath(insuranceComponentElement, "./@isIncludedInTour"));
            insuranceComponent.put(JSON_PROP_ISINCLUDEDINTOUR, isIncludedInTour);
            
            //16-4-18 added by Rahul
            String icType = String
                    .valueOf(XMLUtils.getValueAtXPath(insuranceComponentElement, "./@Type"));
            insuranceComponent.put(JSON_PROP_TYPE, icType);

            Element insCoverageDetailElem = XMLUtils.getFirstElementAtXPath(insuranceComponentElement,
                    "./pac:InsCoverageDetail");

            JSONObject insCoverageDetail = new JSONObject();
            String description = String.valueOf(XMLUtils.getValueAtXPath(insCoverageDetailElem, "./@Description"));
            String name = String.valueOf(XMLUtils.getValueAtXPath(insCoverageDetailElem, "./@Name"));
            String type = String.valueOf(XMLUtils.getValueAtXPath(insCoverageDetailElem, "./@Type"));
            String planID = String.valueOf(XMLUtils.getValueAtXPath(insCoverageDetailElem, "./@PlanID"));
            
            insCoverageDetail.put(JSON_PROP_DESCRIPTION, description);
            insCoverageDetail.put(JSON_PROP_NAME, name);
            insCoverageDetail.put(JSON_PROP_TYPE, type);
            insCoverageDetail.put("planID", planID);

            insuranceComponent.put(JSON_PROP_INSCOVERAGEDETAIL, insCoverageDetail);
            
            //ForGuestCount
            Element[] guestCountElems = XMLUtils.getElementsAtXPath(insuranceComponentElement, "./pac:GuestCounts/ns:GuestCount");
	        JSONArray guestCountarr = getGuests(guestCountElems);
	        if(guestCountarr.length()!=0)
	        insuranceComponent.put("guestCount", guestCountarr);
            
            // PlanCost
            Element[] planCostElems = XMLUtils.getElementsAtXPath(insuranceComponentElement, "./pac:PlanCost");

            JSONArray planCostArray = new JSONArray();
            for (Element PlanCostElem : planCostElems) {

                JSONObject planCostJson = getPlanCost(PlanCostElem);
                planCostArray.put(planCostJson);
            }
            insuranceComponent.put(JSON_PROP_PLANCOST, planCostArray);
            insuranceComponentArray.put(insuranceComponent);
        }

        return insuranceComponentArray;
    }

    public static JSONObject getItinerary(Element itineraryElements) {
        Element[] days = XMLUtils.getElementsAtXPath(itineraryElements, "./pac:days/pac:day");

        JSONObject itinerary = new JSONObject();
        JSONArray daysArray = new JSONArray();

        for (Element dayElement : days) {
            JSONObject dayObj = new JSONObject();

            String day = String.valueOf(XMLUtils.getValueAtXPath(dayElement, "./pac:day"));
            if (day != null && day.length() > 0)
                dayObj.put("day", day);

            String label = String.valueOf(XMLUtils.getValueAtXPath(dayElement, "./pac:label"));
            if (label != null && label.length() > 0)
                dayObj.put("label", label);

            String accommodation = String.valueOf(XMLUtils.getValueAtXPath(dayElement, "./pac:Accommodation"));
            if (accommodation != null && accommodation.length() > 0)
                dayObj.put("accommodation", accommodation);

            String duration = String.valueOf(XMLUtils.getValueAtXPath(dayElement, "./pac:Duration"));
            if (duration != null && duration.length() > 0)
                dayObj.put("duration", duration);

            // For Description Element
            Element[] descriptionElements = XMLUtils.getElementsAtXPath(dayElement, "./pac:description");

            JSONArray descArray = new JSONArray();
            for (Element descriptionElement : descriptionElements) {
                String description = descriptionElement.getTextContent();
                if (description != null && description.length() > 0)
                    descArray.put(description);
            }
            dayObj.put("descriptions", descArray);

            // For Meal Element
            Element[] mealElements = XMLUtils.getElementsAtXPath(dayElement, "./pac:Meals/pac:Meal");

            JSONArray mealArray = new JSONArray();
            for (Element mealElement : mealElements) {
                JSONObject mealObj = new JSONObject();

                String type = String.valueOf(XMLUtils.getValueAtXPath(mealElement, "./pac:Type"));
                if (type != null && type.length() > 0)
                    mealObj.put(JSON_PROP_TYPE, type);

                String mealNumber = String.valueOf(XMLUtils.getValueAtXPath(mealElement, "./pac:MealNumber"));
                if (mealNumber != null && mealNumber.length() > 0)
                    mealObj.put("mealNumber", mealNumber);

                mealArray.put(mealObj);
            }
            dayObj.put("meals", mealArray);

            // For Meal Element
            Element[] locationElements = XMLUtils.getElementsAtXPath(dayElement, "./pac:Locations/pac:Location");

            JSONArray locationArray = new JSONArray();
            for (Element locationElement : locationElements) {
                JSONObject locationObj = new JSONObject();

                String type = String.valueOf(XMLUtils.getValueAtXPath(locationElement, "./pac:Type"));
                if (type != null && type.length() > 0)
                    locationObj.put(JSON_PROP_TYPE, type);

                String name = String.valueOf(XMLUtils.getValueAtXPath(locationElement, "./pac:Name"));
                if (name != null && name.length() > 0)
                    locationObj.put(JSON_PROP_NAME, name);

                String countryCode = String.valueOf(XMLUtils.getValueAtXPath(locationElement, "./pac:CountryCode"));
                if (countryCode != null && countryCode.length() > 0)
                    locationObj.put("countryCode", countryCode);

                locationArray.put(locationObj);
            }
            dayObj.put("location", locationArray);
            daysArray.put(dayObj);
        }
        itinerary.put("days", daysArray);

        return itinerary;
    }

    public static JSONObject getPlanCost(Element planCostElem) {
        JSONObject planCostJson = new JSONObject();

        String amount = String.valueOf(XMLUtils.getValueAtXPath(planCostElem, "./@Amount"));
        String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(planCostElem, "./@CurrencyCode"));
        //16-4-18 added by Rahul
        String paxType = String.valueOf(XMLUtils.getValueAtXPath(planCostElem, "./@paxType"));
        
        planCostJson.put(JSON_PROP_PAXTYPE, paxType);
        planCostJson.put(JSON_PROP_AMOUNT, amount);
        planCostJson.put(JSON_PROP_CURRENCYCODE, currencyCode);

        Element basePremiumElem = XMLUtils.getFirstElementAtXPath(planCostElem, "./ns:BasePremium");

        JSONObject basePremiumJson = new JSONObject();
        String amount1 = String.valueOf(XMLUtils.getValueAtXPath(basePremiumElem, "./@Amount"));
        String currencyCode1 = String.valueOf(XMLUtils.getValueAtXPath(basePremiumElem, "./@CurrencyCode"));

        basePremiumJson.put(JSON_PROP_AMOUNT, amount1);
        basePremiumJson.put(JSON_PROP_CURRENCYCODE, currencyCode1);

        planCostJson.put("basePremium", basePremiumJson);

        Element[] chargeElements = XMLUtils.getElementsAtXPath(planCostElem, "./ns:Charges/ns:Charge");

        JSONArray chargeArray = new JSONArray();
        for (Element chargeElement : chargeElements) {
            JSONObject chargeObj = new JSONObject();

            String type = String.valueOf(XMLUtils.getValueAtXPath(chargeElement, "./@Type"));
            String chargeAmount = String.valueOf(XMLUtils.getValueAtXPath(chargeElement, "./@Amount"));
            String curencyCode = String.valueOf(XMLUtils.getValueAtXPath(chargeElement, "./@CurrencyCode"));
            JSONObject taxJson = getChargeTax(chargeElement);

            if (type != null && type.length() > 0)
                chargeObj.put(JSON_PROP_TYPE, type);

            if (chargeAmount != null && chargeAmount.length() > 0)
                chargeObj.put(JSON_PROP_AMOUNT, chargeAmount);

            if (curencyCode != null && curencyCode.length() > 0)
                chargeObj.put(JSON_PROP_CURRENCYCODE, curencyCode);

            chargeObj.put(JSON_PROP_TAXES, taxJson);
            
            //12-4-18 added DescriptionJSON(Rahul)
            String desName = String.valueOf(XMLUtils.getValueAtXPath(chargeElement, "./ns:Description/@Name"));
            if(desName.length() > 0 && desName != null)
           	{
            	JSONObject desJson = new JSONObject();
            	desJson.put(JSON_PROP_NAME, desName);
            	chargeObj.put(JSON_PROP_DESCRIPTION,desJson);
           	}
            chargeArray.put(chargeObj);
        }
        planCostJson.put(JSON_PROP_CHARGE, chargeArray);

        return planCostJson;
    }

    public static JSONObject getChargeTax(Element chargeElem) {

        JSONObject taxes = new JSONObject();
        JSONArray taxArray = new JSONArray();
        //12-4-18 added amount and CurrencyCode(Rahul)
        Element taxTotals = XMLUtils.getFirstElementAtXPath(chargeElem, "./ns:Taxes");
        String taxTotalAmount = String.valueOf(XMLUtils.getValueAtXPath(taxTotals, "./@Amount"));
        String totalTaxCurrencyCode = String.valueOf(XMLUtils.getValueAtXPath(taxTotals, "./@CurrencyCode"));
        
        Element[] taxElems = XMLUtils.getElementsAtXPath(chargeElem, "./ns:Taxes/ns:Tax");
        for (Element taxElem : taxElems) {
            JSONObject taxJson = new JSONObject();

            String amount1 = String.valueOf(XMLUtils.getValueAtXPath(taxElem, "./@Amount"));
            String currencyCode1 = String.valueOf(XMLUtils.getValueAtXPath(taxElem, "./@CurrencyCode"));

            taxJson.put(JSON_PROP_AMOUNT, amount1);
            taxJson.put(JSON_PROP_CURRENCYCODE, currencyCode1);

            Element taxDescriptionElem = XMLUtils.getFirstElementAtXPath(taxElem, "./ns:TaxDescription");
            String name = String.valueOf(XMLUtils.getValueAtXPath(taxDescriptionElem, "./@Name"));
            
            taxJson.put(JSON_PROP_TAXDESCRIPTION, name);
            taxArray.put(taxJson);
        }
        
        //12-4-18 added amount and CurrencyCode(Rahul)
        taxes.put(JSON_PROP_AMOUNT, taxTotalAmount);
        taxes.put(JSON_PROP_CURRENCYCODE, totalTaxCurrencyCode);
        taxes.put(JSON_PROP_TAX, taxArray);
        
        return taxes;
    }

    public static JSONArray getGroundService(Element transferCompElem, String dynamicPkgAction,Boolean flag) {
        JSONArray groundServiceArray = new JSONArray();
        Element[] groundServiceElemArray = XMLUtils.getElementsAtXPath(transferCompElem, "./pac:GroundService");

        for (Element groundServiceElem : groundServiceElemArray) {
            JSONObject groundServiceJson = new JSONObject();

            String name = String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./@Name"));
            if (name != null && name.length() > 0)
                groundServiceJson.put(JSON_PROP_NAME, name);
            
            String id = String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./@ID"));
            if (id != null && id.length() > 0)
                groundServiceJson.put(JSON_PROP_ID, id);
            
            String availabilityStatus = String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./@AvailabilityStatus"));
            if (availabilityStatus != null && availabilityStatus.length() > 0)
                groundServiceJson.put(JSON_PROP_AVAILABILITYSTATUS, availabilityStatus);

            String description = String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./@Description"));
            if (description != null && description.length() > 0)
                groundServiceJson.put(JSON_PROP_DESCRIPTION, description);
            
            String departureCity = String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./@DepartureCity"));
            if (departureCity != null && departureCity.length() > 0)
                groundServiceJson.put("departureCity", departureCity);
            
            String arrivalCity = String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./@ArrivalCity"));
            if (arrivalCity != null && arrivalCity.length() > 0)
                groundServiceJson.put("arrivalCity", arrivalCity);
            
            String departureDate = String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./@DepartureDate"));
            if (departureDate != null && departureDate.length() > 0)
                groundServiceJson.put("departureDate", departureDate);
            
            String arrivalDate = String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./@ArrivalDate"));
            if (arrivalDate != null && arrivalDate.length() > 0)
                groundServiceJson.put("arrivalDate", arrivalDate);
            
            Element locationElem = XMLUtils.getFirstElementAtXPath(groundServiceElem, "./pac:Location");
            if (locationElem != null && (locationElem.hasChildNodes() || locationElem.hasAttributes())) {
                JSONObject locationJson = getLocation(locationElem, dynamicPkgAction,flag);
                groundServiceJson.put(JSON_PROP_LOCATION, locationJson);
            }
            
            Element depositElement = XMLUtils.getFirstElementAtXPath(groundServiceElem, "./pac:Deposits");
            if (depositElement != null) {
            	JSONArray depositArr = new JSONArray();
            	Element[] depositElements = XMLUtils.getElementsAtXPath(depositElement, "./pac:Deposit");
            	for (Element deposit : depositElements) {
                    JSONObject depositJson = new JSONObject();
                    depositJson.put("depositAmount", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@DepositAmount")));
                    depositJson.put("currencyCode", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@CurrencyCode")));
                    depositJson.put("type", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@Type")));
                    depositArr.put(depositJson);
            	}
            	groundServiceJson.put("deposits", depositArr);
            }
            
            Element bookingRulesElement = XMLUtils.getFirstElementAtXPath(groundServiceElem, "./pac:BookingRules");
            if (bookingRulesElement != null) {
            	JSONArray bookingRuleArr = new JSONArray();
            	Element[] bookingRule = XMLUtils.getElementsAtXPath(bookingRulesElement, "./ns:BookingRule");
            	for (Element br : bookingRule) {
                    JSONObject brJson = new JSONObject();
                    brJson.put("end", String.valueOf(XMLUtils.getValueAtXPath(br, "./@End")));
                    brJson.put("start", String.valueOf(XMLUtils.getValueAtXPath(br, "./@Start")));
                    
                    Element lengthsOfStayElem = XMLUtils.getFirstElementAtXPath(br, "./ns:LengthsOfStay");
                    if(lengthsOfStayElem!=null) {
                    	//JSONObject lengthOfStay = new JSONObject();
                    	Element lengthOfStayElem[] = XMLUtils.getElementsAtXPath(lengthsOfStayElem, "./ns:LengthOfStay");
                    	for(Element los : lengthOfStayElem) {
                    		String minOrMax = String.valueOf(XMLUtils.getValueAtXPath(los, "./@MinMaxMessageType"));
                    		String time = String.valueOf(XMLUtils.getValueAtXPath(los, "./@Time"));
                    		String timeUnit = String.valueOf(XMLUtils.getValueAtXPath(los, "./@TimeUnit"));
                    		brJson.put(minOrMax,time + " " + timeUnit);
                    		
                    		//brJson.put("lengthOfStay", lengthOfStay);
                    	}
                    }
                    
                    bookingRuleArr.put(brJson);
            	}
            	groundServiceJson.put(JSON_PROP_BOOKINGRULE, bookingRuleArr);
            }
            
            Element[] passengers = XMLUtils.getElementsAtXPath(groundServiceElem, "./pac:Passenger");
            if (passengers != null && passengers.length > 0) {
            JSONArray passArr = new JSONArray();
            for (Element pass : passengers) {
            	JSONObject passJson = new JSONObject();
            	passJson.put("minimumAge", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MinimumAge")));
            	passJson.put("maximumAge", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MaximumAge")));
                passJson.put("maximumPassengers", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MaximumPassengers")));
                passJson.put("minimumPassengers", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MinimumPassengers")));
                passJson.put("passengerTypeCode", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@PassengerTypeCode")));
                passArr.put(passJson);
            	}
            groundServiceJson.put("passenger", passArr);
            }
            
            Element[] totalChargeElements = XMLUtils.getElementsAtXPath(groundServiceElem, "./pac:TotalCharge");
            if (totalChargeElements != null && totalChargeElements.length > 0) {
                JSONArray totlChargeArray = new JSONArray();

                for (Element totalChargeElement : totalChargeElements) {
                    JSONObject totalChargeJson = getTotalCharge(totalChargeElement);
                    totlChargeArray.put(totalChargeJson);
                }
                groundServiceJson.put(JSON_PROP_TOTALCHARGE, totlChargeArray);
            }
            
            	Element[] priceTotalElements = XMLUtils.getElementsAtXPath(groundServiceElem, "./pac:Price/ns:Total");
            	if (priceTotalElements != null && priceTotalElements.length > 0) {
                    JSONArray totalArray = new JSONArray();

                    for (Element total : priceTotalElements) {
                    JSONObject totalObj = new JSONObject();

                    String amountBeforeTax = String.valueOf(XMLUtils.getValueAtXPath(total, "./@AmountBeforeTax"));
                    String amountAfterTax = String.valueOf(XMLUtils.getValueAtXPath(total, "./@AmountAfterTax"));
                    String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(total, "./@CurrencyCode"));
                    String totalType = String.valueOf(XMLUtils.getValueAtXPath(total, "./@Type"));

                    totalObj.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTax);
                    totalObj.put(JSON_PROP_CURRENCYCODE, currencyCode);
                    totalObj.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTax);
                    totalObj.put(JSON_PROP_TYPE, totalType);

                    JSONObject taxJson = getChargeTax(total);
                    totalObj.put(JSON_PROP_TAXES, taxJson);

                    totalArray.put(totalObj);
                    }
                    groundServiceJson.put(JSON_PROP_TOTAL, totalArray);
                }
            
            Element timeSpanElem = XMLUtils.getFirstElementAtXPath(groundServiceElem, "./pac:TimeSpan");
            if (timeSpanElem != null && (timeSpanElem.hasChildNodes() || timeSpanElem.hasAttributes())) {
	            JSONObject timeSpanJson = getTimeSpan(timeSpanElem);
	            groundServiceJson.put(JSON_PROP_TIMESPAN, timeSpanJson);
            }
            if(flag == false) {
	            Element[] guestCountElems = XMLUtils.getElementsAtXPath(groundServiceElem, "./pac:GuestCounts/ns:GuestCount");
		        JSONArray guestCountarr = getGuests(guestCountElems);
		        groundServiceJson.put("guestCount", guestCountarr);
            }
	        
            if(flag==true) {
	            Boolean airInclusiveBooking = Boolean.valueOf(String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./pac:AirInclusiveBooking")));
	            Boolean declineRequired = Boolean.valueOf(String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./pac:DeclineRequired")));
	            Boolean withExtraNights = Boolean.valueOf(String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./pac:WithExtraNights")));
	            Boolean purchasable = Boolean.valueOf(String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./pac:Purchasable")));
	            Boolean isIncludedInTour = Boolean.valueOf(String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./pac:isIncludedInTour")));
	            Boolean flightInfoRequired = Boolean.valueOf(String.valueOf(XMLUtils.getValueAtXPath(groundServiceElem, "./pac:FlightInfoRequired")));

	            // PerServicePricing
	
	            JSONArray perServicePricingArr = getPerServicePricing(groundServiceElem);
	
	            groundServiceJson.put(JSON_PROP_AIRINCLUSIVEBOOKING, airInclusiveBooking);
	            groundServiceJson.put("declineRequired", declineRequired);
	            groundServiceJson.put(JSON_PROP_WITHEXTRANIGHTS, withExtraNights);
	            groundServiceJson.put(JSON_PROP_PURCHASABLE, purchasable);
	            groundServiceJson.put(JSON_PROP_ISINCLUDEDINTOUR, isIncludedInTour);
	            groundServiceJson.put(JSON_PROP_FLIGHTINFOREQUIRED, flightInfoRequired);
	            if(perServicePricingArr.length()!=0)
	            	groundServiceJson.put(JSON_PROP_PERSERVICEPRICING, perServicePricingArr);
            }
            groundServiceArray.put(groundServiceJson);
        }
        return groundServiceArray;
    }

    public static JSONArray getPerServicePricing(Element groundServiceElem) {

        Element[] PerServicePricingElems = XMLUtils.getElementsAtXPath(groundServiceElem, "./pac:PerServicePricing");
        JSONArray perServicePricingArray = new JSONArray();

        for (Element PerServicePricingElem : PerServicePricingElems) {
            JSONObject perServicePricingJson = new JSONObject();
            String maxPassengersInParty = String.valueOf(XMLUtils.getValueAtXPath(PerServicePricingElem, "./@MaxPassengersInParty"));
            String minPassengersInParty = String.valueOf(XMLUtils.getValueAtXPath(PerServicePricingElem, "./@MinPassengersInParty"));
            String price = String.valueOf(XMLUtils.getValueAtXPath(PerServicePricingElem, "./@Price"));
            //12-4-18 added type and CurrencyCode(Rahul)
            String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(PerServicePricingElem, "./@CurrencyCode"));
            String type = String.valueOf(XMLUtils.getValueAtXPath(PerServicePricingElem, "./@Type"));
            perServicePricingJson.put(JSON_PROP_MAXPASSENGERSINPARTY, maxPassengersInParty);
            perServicePricingJson.put(JSON_PROP_MINPASSENGERSINPARTY, minPassengersInParty);
            perServicePricingJson.put(JSON_PROP_PRICE, price);
            perServicePricingJson.put(JSON_PROP_CURRENCYCODE, currencyCode);
            perServicePricingJson.put(JSON_PROP_TYPE, type);
            perServicePricingArray.put(perServicePricingJson);

        }

        return perServicePricingArray;
    }

    public static JSONObject getTimeSpan(Element timeSpanElem) {
        JSONObject timeSpanJson = new JSONObject();

        String end = String.valueOf(XMLUtils.getValueAtXPath(timeSpanElem, "./@End"));
        String start = String.valueOf(XMLUtils.getValueAtXPath(timeSpanElem, "./@Start"));
        String duration = String.valueOf(XMLUtils.getValueAtXPath(timeSpanElem, "./@Duration"));
        
        timeSpanJson.put(JSON_PROP_DURATION, duration);
        timeSpanJson.put(JSON_PROP_END, end);
        timeSpanJson.put(JSON_PROP_START, start);
        return timeSpanJson;

    }

    public static JSONObject getTotalCharge(Element totalChargeElement) {
        JSONObject totalChargeJson = new JSONObject();

        String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(totalChargeElement, "./@CurrencyCode"));
        String rateTotalAmount = String.valueOf(XMLUtils.getValueAtXPath(totalChargeElement, "./@RateTotalAmount"));
        String type = String.valueOf(XMLUtils.getValueAtXPath(totalChargeElement, "./@Type"));

        totalChargeJson.put(JSON_PROP_CURRENCYCODE, currencyCode);
        totalChargeJson.put(JSON_PROP_RATETOTALAMOUNT, rateTotalAmount);
        totalChargeJson.put(JSON_PROP_TYPE, type);

        return totalChargeJson;
    }

    public static JSONObject getLocation(Element locationElem, String dynamicPkgAction, Boolean flag) {

        JSONObject locationJson = new JSONObject();
        
        Element PickupElem = XMLUtils.getFirstElementAtXPath(locationElem, "./ns:Pickup");
        Element airportInfoElem = XMLUtils.getFirstElementAtXPath(PickupElem, "./ns:AirportInfo");
        Element departArrivElem = null;
        if (flag){
        if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_TRANSFER_DEPARTURE))
            departArrivElem = XMLUtils.getFirstElementAtXPath(airportInfoElem, "./ns:Departure");

        else if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_TRANSFER_IARRIVAL))
            departArrivElem = XMLUtils.getFirstElementAtXPath(airportInfoElem, "./ns:Arrival");
        }
        
        //Below condition for handling reprice
        if(flag==false)	
        	departArrivElem = XMLUtils.getFirstElementAtXPath(airportInfoElem, "./ns:Departure");

        String airportName = String.valueOf(XMLUtils.getValueAtXPath(departArrivElem, "./@AirportName"));
        locationJson.put(JSON_PROP_AIRPORTNAME, airportName);

        String codeContext = String.valueOf(XMLUtils.getValueAtXPath(departArrivElem, "./@CodeContext"));
        locationJson.put(JSON_PROP_CODECONTEXT, codeContext);

        String pickUpLocation = String.valueOf(XMLUtils.getValueAtXPath(departArrivElem, "./@LocationCode"));
        locationJson.put("pickUpLocation", pickUpLocation);
        
        //For Gadventures
        String pickUpLocationCode = String.valueOf(XMLUtils.getValueAtXPath(PickupElem, "./@LocationCode"));
        JSONObject pickUp = new JSONObject();
        pickUp.put("LocationCode", pickUpLocationCode);
        
        Element pickUpAddress = XMLUtils.getFirstElementAtXPath(PickupElem, "./ns:Address");
        JSONObject pAddress = new JSONObject();
        pAddress.put("cityName",String.valueOf(XMLUtils.getValueAtXPath(pickUpAddress, "./ns:CityName")));
        pAddress.put("countryName",String.valueOf(XMLUtils.getValueAtXPath(pickUpAddress, "./ns:CountryName")));
        
        pickUp.put("address", pAddress);
        
        Element dropOffElem = XMLUtils.getFirstElementAtXPath(locationElem, "./ns:Dropoff");
        String dropOffLocationCode = String.valueOf(XMLUtils.getValueAtXPath(dropOffElem, "./@LocationCode"));
        JSONObject dropOff = new JSONObject();
        dropOff.put("LocationCode", dropOffLocationCode);
        
        Element dropOffAddress = XMLUtils.getFirstElementAtXPath(dropOffElem, "./ns:Address");
        JSONObject dAddress = new JSONObject();
        dAddress.put("cityName",String.valueOf(XMLUtils.getValueAtXPath(dropOffAddress, "./ns:CityName")));
        dAddress.put("countryName",String.valueOf(XMLUtils.getValueAtXPath(dropOffAddress, "./ns:CountryName")));
        
        dropOff.put("address", dAddress);
        
        locationJson.put("pickup", pickUp);
        locationJson.put("dropOff", dropOff);
        
        return locationJson;
    }

    public static JSONObject getAitItineryComponentJSON(Element airItineryElem) {

        JSONObject airItineraryObj = new JSONObject();
        
        Element originDestOptionsElem = XMLUtils.getFirstElementAtXPath(airItineryElem,"./ns:OriginDestinationOptions");
        Element originDestOptionElem = XMLUtils.getFirstElementAtXPath(originDestOptionsElem, "./ns:OriginDestinationOption");

        airItineraryObj = getFlightSegment(originDestOptionElem);
        return airItineraryObj;
    }

    public static JSONObject getFlightSegment(Element originDestOptionElem) {
        JSONObject flightSegJson = new JSONObject();
        Element flightSegElem = XMLUtils.getFirstElementAtXPath(originDestOptionElem, "./ns:FlightSegment");

        JSONObject departureAirportJson = new JSONObject();
        Element departureAirportElem = XMLUtils.getFirstElementAtXPath(flightSegElem, "./ns:DepartureAirport");

        String codeContext = String.valueOf(XMLUtils.getValueAtXPath(departureAirportElem, "./@CodeContext"));
        departureAirportJson.put(JSON_PROP_CODECONTEXT, codeContext);

        String locationCode = String.valueOf(XMLUtils.getValueAtXPath(departureAirportElem, "./@LocationCode"));
        departureAirportJson.put(JSON_PROP_LOCATIONCODE, locationCode);

        JSONObject arrivalAirportJson = new JSONObject();
        Element arrivalAirportElem = XMLUtils.getFirstElementAtXPath(flightSegElem, "./ns:ArrivalAirport");

        String ArrivelocationCode = String.valueOf(XMLUtils.getValueAtXPath(arrivalAirportElem, "./@LocationCode"));
        arrivalAirportJson.put(JSON_PROP_LOCATIONCODE, ArrivelocationCode);

        JSONArray bookingArray = new JSONArray();
        Element[] bookingClassAvailsArray = XMLUtils.getElementsAtXPath(flightSegElem, "./ns:BookingClassAvails");

        for (Element bookingClassAvailsElem : bookingClassAvailsArray) {
            JSONObject bookingAvailJson = getBookingAvailsJson(bookingClassAvailsElem);
            bookingArray.put(bookingAvailJson);
        }

        flightSegJson.put(JSON_PROP_DEPARTUREAIRPORT, departureAirportJson);
        flightSegJson.put(JSON_PROP_ARRIVALAIRPORT, arrivalAirportJson);
        flightSegJson.put(JSON_PROP_BOOKINGCLASSAVAILS, bookingArray);
        return flightSegJson;
    }

    public static JSONObject getBookingAvailsJson(Element bookingClassAvailsElem) {

        JSONObject cabinTypeJson = new JSONObject();
        String cabinType = String.valueOf(XMLUtils.getValueAtXPath(bookingClassAvailsElem, "./@CabinType"));
        cabinTypeJson.put(JSON_PROP_CABINTYPE, cabinType);

        return cabinTypeJson;
    }

    public static JSONObject getHotelComponentJSON(Element hotelCompElem,Boolean flag) {
        JSONObject hotelComponentJson = new JSONObject();
        String dynamicPkgAction = String.valueOf(XMLUtils.getValueAtXPath(hotelCompElem, "./@DynamicPkgAction"));

        hotelComponentJson.put(JSON_PROP_DYNAMICPKGACTION, dynamicPkgAction);
        JSONArray roomStayArr = new JSONArray();

        Element[] roomStayArray = XMLUtils.getElementsAtXPath(hotelCompElem, "./ns:RoomStays/ns:RoomStay");
        for (Element roomStayElem : roomStayArray) {
            JSONObject roomStay = getRoomStayJSON(roomStayElem, flag);
            String ratePlanCategory = roomStay.optJSONArray(JSON_PROP_ROOMRATE).optJSONObject(0).optString(JSON_PROP_RATEPLANCATEGORY);
        	roomStay.put(JSON_PROP_RATEPLANCATEGORY, ratePlanCategory);
            roomStayArr.put(roomStay);
        }
        JSONObject roomStays = new JSONObject();
        roomStays.put(JSON_PROP_ROOMSTAY, roomStayArr);
        hotelComponentJson.put(JSON_PROP_ROOMSTAYS, roomStays);

        return hotelComponentJson;
    }

    public static JSONObject getRoomStayJSON(Element roomStayElem, Boolean flag) {
        JSONObject roomStayJson = new JSONObject();

        String roomStayStatus = String.valueOf(XMLUtils.getValueAtXPath(roomStayElem, "./@RoomStayStatus"));
        roomStayJson.put(JSON_PROP_ROOMSTAYSTATUS, roomStayStatus);

        Element roomTypeElem = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ns:RoomTypes/ns:RoomType");

        String roomTypeStr = String.valueOf(XMLUtils.getValueAtXPath(roomTypeElem, "./@RoomType"));
        String roomCategory = String.valueOf(XMLUtils.getValueAtXPath(roomTypeElem, "./@RoomCategory"));
        String sharedRoomInd = String.valueOf(XMLUtils.getValueAtXPath(roomTypeElem, "./@SharedRoomInd"));
        String roomID = String.valueOf(XMLUtils.getValueAtXPath(roomTypeElem, "./@RoomID"));

        roomStayJson.put(JSON_PROP_ROOMTYPE, roomTypeStr);
        roomStayJson.put(JSON_PROP_ROOMCATEGORY, roomCategory);
        roomStayJson.put("sharedRoomInd", sharedRoomInd);
        roomStayJson.put("roomID", roomID);
        
        // For Occupancy Element
        Element[] occupancyElements = XMLUtils.getElementsAtXPath(roomStayElem,"./ns:RoomTypes/ns:RoomType/ns:Occupancy");

        if (occupancyElements != null && occupancyElements.length != 0) {
            JSONArray occupancyObjects = new JSONArray();

            for (Element occupancyElement : occupancyElements) {
                JSONObject occupancyObject = new JSONObject();

                String minAge = String.valueOf(XMLUtils.getValueAtXPath(occupancyElement, "./@MinAge"));
                occupancyObject.put("minAge", minAge);

                String maxAge = String.valueOf(XMLUtils.getValueAtXPath(occupancyElement, "./@MaxAge"));
                occupancyObject.put("maxAge", maxAge);

                String maxOccupancy = String.valueOf(XMLUtils.getValueAtXPath(occupancyElement, "./@MaxOccupancy"));
                occupancyObject.put("maxOccupancy", maxOccupancy);

                String minOccupancy = String.valueOf(XMLUtils.getValueAtXPath(occupancyElement, "./@MinOccupancy"));
                occupancyObject.put("minOccupancy", minOccupancy);

                String ageQualifyingCode = String
                        .valueOf(XMLUtils.getValueAtXPath(occupancyElement, "./@AgeQualifyingCode"));
                occupancyObject.put("ageQualifyingCode", ageQualifyingCode);

                occupancyObjects.put(occupancyObject);
            }

            roomStayJson.put("occupancy", occupancyObjects);
        }

        // For Room Rules Element
        Element roomRulesElement = XMLUtils.getFirstElementAtXPath(roomTypeElem,"./ns:TPA_Extensions/pac:Pkgs_TPA/pac:RoomRules");
        if (roomRulesElement != null && (roomRulesElement.hasChildNodes() || roomRulesElement.hasAttributes())) {
            JSONObject roomRules = new JSONObject();

            String minimumPayingPassengers = String.valueOf(XMLUtils.getValueAtXPath(roomRulesElement, "./@MinimumPayingPassengers"));
            roomRules.put("minimumPayingPassengers", minimumPayingPassengers);
            roomStayJson.put("roomRules", roomRules);
        }

        Element[] roomRateElems = XMLUtils.getElementsAtXPath(roomStayElem, "./ns:RoomRates/ns:RoomRate");
        JSONArray roomRateArray = new JSONArray();

        for (Element roomRateElem : roomRateElems) {
            JSONObject roomRateJson = new JSONObject();
            JSONObject total = new JSONObject();
            //12-4-18 added rates(Rahul)
            JSONArray ratesarr = getRatesForRoomRates(roomRateElem);
            if(ratesarr.length()!=0) {
            	JSONObject rateJson = new JSONObject();
                rateJson.put("rate", ratesarr);
                roomRateJson.put("rates", rateJson);
            }
            JSONObject totalRoomRate = getTotalRoomRateJSON(roomRateElem, total, flag);
            String ratePlanCategory = String.valueOf(XMLUtils.getValueAtXPath(roomRateElem, "./@RatePlanCategory"));
            if(!ratePlanCategory.isEmpty())
            	roomRateJson.put("ratePlanCategory", ratePlanCategory);
            roomRateJson.put(JSON_PROP_TOTAL, totalRoomRate);
            roomRateArray.put(roomRateJson);
        }

        roomStayJson.put(JSON_PROP_ROOMRATE, roomRateArray);

        // for GuestCount started
        if(flag== true) {
	        Element guestCountsElem = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ns:GuestCounts");
	        
	        Element guestCountElem = XMLUtils.getFirstElementAtXPath(guestCountsElem, "./ns:GuestCount");
	        JSONObject guestCountJson = new JSONObject();
	
	        String count = String.valueOf(XMLUtils.getValueAtXPath(guestCountElem, "./@Count"));
	        guestCountJson.put(JSON_PROP_COUNT, count);
	
	        roomStayJson.put(JSON_PROP_GUESTCOUNT, guestCountJson);
        }
        else {
        	Element[] guestCountElems = XMLUtils.getElementsAtXPath(roomStayElem, "./ns:GuestCounts/ns:GuestCount");
	        JSONArray guestCountarr = getGuests(guestCountElems);
	        roomStayJson.put("guestCount", guestCountarr);
        }

        // For TimeSpan Element
        Element timeSpanElement = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ns:TimeSpan");
        if (timeSpanElement != null && (timeSpanElement.hasChildNodes() || timeSpanElement.hasAttributes())) {
            JSONObject timeSpan = getTimeSpan(timeSpanElement);
            roomStayJson.put(JSON_PROP_TIMESPAN, timeSpan);
        }

        // for BookingRules started
        Element bookingRulesElem = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ns:BookingRules");
        if (bookingRulesElem != null && (bookingRulesElem.hasChildNodes() || bookingRulesElem.hasAttributes())) {
            JSONArray bookingRulesJson = new JSONArray();
            Element[] bookingRuleElems = XMLUtils.getElementsAtXPath(bookingRulesElem, "./ns:BookingRule");

            for (Element bookingRuleElem : bookingRuleElems) {
                JSONObject bookingRuleJson = new JSONObject();

                Element[] lengthsOfStayArray = XMLUtils.getElementsAtXPath(bookingRuleElem,"./ns:LengthsOfStay/ns:LengthOfStay");
                JSONArray lengthStayArr = new JSONArray();

                for (Element lengthOfStayElem : lengthsOfStayArray) {
                    JSONObject lengthStayJson = getLengthOfStayElemJSON(lengthOfStayElem);
                    lengthStayArr.put(lengthStayJson);
                }
                bookingRuleJson.put(JSON_PROP_LENGTHSOFSTAY, lengthStayArr);
                bookingRulesJson.put(bookingRuleJson);
            }
            roomStayJson.put(JSON_PROP_BOOKINGRULE, bookingRulesJson);
        }

        // For BasicPropertyInfo Element
        JSONArray basicPropertyInfoJson = new JSONArray();
        Element[] basicPropertyInfoElements = XMLUtils.getElementsAtXPath(roomStayElem, "./ns:BasicPropertyInfo");
        for (Element basicPropertyInfoElement : basicPropertyInfoElements) {
            if (basicPropertyInfoElement != null && (basicPropertyInfoElement.hasChildNodes() || basicPropertyInfoElement.hasAttributes())) {
                JSONObject basicPropertyInfo = new JSONObject();

                String hotelCode = String.valueOf(XMLUtils.getValueAtXPath(basicPropertyInfoElement, "./@HotelCode"));
                basicPropertyInfo.put("hotelCode", hotelCode);
                
                String hotelSegmentCategoryCode = String.valueOf(XMLUtils.getValueAtXPath(basicPropertyInfoElement, "./@HotelSegmentCategoryCode"));
                basicPropertyInfo.put("hotelSegmentCategoryCode", hotelSegmentCategoryCode);

                String hotelName = String.valueOf(XMLUtils.getValueAtXPath(basicPropertyInfoElement, "./@HotelName"));
                basicPropertyInfo.put("hotelName", hotelName);

                Element addressElement = XMLUtils.getFirstElementAtXPath(basicPropertyInfoElement, "./ns:Address");
                if (addressElement != null && (addressElement.hasChildNodes() || addressElement.hasAttributes())) {
                    JSONObject address = new JSONObject();

                    Element[] addressLinesElement = XMLUtils.getElementsAtXPath(addressElement, "./AddressLine");
                    if (addressLinesElement != null && addressLinesElement.length > 0) {
                        JSONArray addressLines = new JSONArray();
                        for (Element addressLineElement : addressLinesElement) {
                            addressLineElement = XMLUtils.getFirstElementAtXPath(addressElement, "./ns:AddressLine");
                            String addressLineStr = XMLUtils.getElementValue(addressLineElement);
                            addressLines.put(addressLineStr);
                        }
                        address.put("addressLine", addressLines);
                    }

                    Element cityNameElement = XMLUtils.getFirstElementAtXPath(addressElement, "./ns:CityName");
                    if (cityNameElement != null) {
                        String cityName = XMLUtils.getElementValue(cityNameElement);
                        address.put("cityName", cityName);
                    }

                    Element postalCodeElement = XMLUtils.getFirstElementAtXPath(addressElement, "./ns:PostalCode");
                    if (postalCodeElement != null) {
                        String postalCode = XMLUtils.getElementValue(postalCodeElement);
                        address.put("postalCode", postalCode);
                    }

                    Element countyElement = XMLUtils.getFirstElementAtXPath(addressElement, "./ns:County");
                    if (countyElement != null) {
                        String county = XMLUtils.getElementValue(countyElement);
                        address.put("county", county);
                    }

                    Element stateProvElement = XMLUtils.getFirstElementAtXPath(addressElement, "./ns:StateProv");
                    if (stateProvElement != null) {
                        String stateProv = XMLUtils.getElementValue(stateProvElement);
                        address.put("stateProv", stateProv);
                    }

                    basicPropertyInfo.put("address", address);
                }
                basicPropertyInfoJson.put(basicPropertyInfo);
            }
        }
        roomStayJson.put("basicPropertyInfo", basicPropertyInfoJson);
        
        //For DepositPayments
        Element depositPaymentsElement = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ns:DepositPayments");
        if (depositPaymentsElement != null && (depositPaymentsElement.hasChildNodes() || depositPaymentsElement.hasAttributes())) {
        	Element[] guaranteePaymentElem = XMLUtils.getElementsAtXPath(depositPaymentsElement, "./ns:GuaranteePayment");
        	JSONArray gp = new JSONArray();
        	for(Element gpe: guaranteePaymentElem) {
        		JSONObject amountPercent = new JSONObject();
        		
        		String type = String.valueOf(XMLUtils.getValueAtXPath(gpe,"./@Type"));
        		String amount = String.valueOf(XMLUtils.getValueAtXPath(gpe,"./ns:AmountPercent/@Amount"));
        		String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(gpe,"./ns:AmountPercent/@CurrencyCode"));
        		
        		amountPercent.put("type", type);
        		amountPercent.put("amount", amount);
        		amountPercent.put("currencyCode", currencyCode);
        		gp.put(amountPercent);
        	}
        	JSONObject depositPaymentsJson = new JSONObject();
        	depositPaymentsJson.put("guaranteePayment", gp);
        	roomStayJson.put("depositPayments", depositPaymentsJson);
        }
        
        //For Discounts
        Element discountElem = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ns:Discount");
        if (discountElem != null && (discountElem.hasChildNodes() || discountElem.hasAttributes())) {
        	JSONObject discount = new JSONObject();
    		
    		String amountAfterTax = String.valueOf(XMLUtils.getValueAtXPath(discountElem,"./@AmountAfterTax"));
    		String amountBeforeTax = String.valueOf(XMLUtils.getValueAtXPath(discountElem,"./@AmountBeforeTax"));
    		String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(discountElem,"./@CurrencyCode"));
    		String discountCode = String.valueOf(XMLUtils.getValueAtXPath(discountElem,"./@DiscountCode"));
    		String type = String.valueOf(XMLUtils.getValueAtXPath(discountElem,"./@Type"));
    		
    		discount.put("amountAfterTax", amountAfterTax);
    		discount.put("amount", amountBeforeTax);
    		discount.put("currencyCode", currencyCode);
    		discount.put("discountCode", discountCode);
    		discount.put("type", type);
    		
    		roomStayJson.put("discount", discount);
        }

        //For Special Requests
        Element[] specialRequestArr = XMLUtils.getElementsAtXPath(roomStayElem, "./ns:SpecialRequests/ns:SpecialRequest");
        if (specialRequestArr != null && specialRequestArr.length > 0) {
        	JSONArray specialRequestJsonArr = new JSONArray();
        	for(Element specialRequest: specialRequestArr) {
        		JSONObject sp = new JSONObject();
        		
        		String name = String.valueOf(XMLUtils.getValueAtXPath(specialRequest,"./@Name"));
        		String requestCode = String.valueOf(XMLUtils.getValueAtXPath(specialRequest,"./@RequestCode"));
        		
        		Element textElem = XMLUtils.getFirstElementAtXPath(specialRequest, "./ns:Text");
                String text = XMLUtils.getElementValue(textElem);
        		
                Element urlElem = XMLUtils.getFirstElementAtXPath(specialRequest, "./ns:URL");
                String url = XMLUtils.getElementValue(urlElem);
        		
                sp.put("name", name);
                sp.put("requestCode", requestCode);
                sp.put("text", text);
                sp.put("url", url);
                specialRequestJsonArr.put(sp);
        	}
        	roomStayJson.put("specialRequests", specialRequestJsonArr);
        }
        
        //For Single supplement TPA_Extensions
        Element tpaElem = XMLUtils.getFirstElementAtXPath(roomStayElem, "./ns:TPA_Extensions");
        if (tpaElem != null && (tpaElem.hasChildNodes() || tpaElem.hasAttributes())) {
        	Element supplementElem = XMLUtils.getFirstElementAtXPath(tpaElem, "./pac:SingleSupplementService");
            if (supplementElem != null && (supplementElem.hasChildNodes() || supplementElem.hasAttributes())) {
	        	Element[] supplementArr = XMLUtils.getElementsAtXPath(supplementElem, "./pac:SingleSupplement");
	            if (supplementArr != null && supplementArr.length > 0) {
	            	JSONArray supplementJsonArr = new JSONArray();
	            	for(Element supplement: supplementArr) {
	            		JSONObject singleSupplement = new JSONObject();

	                    singleSupplement.put("createdDate", String.valueOf(XMLUtils.getValueAtXPath(supplement, "./@CreatedDate")));
	                    singleSupplement.put("description", String.valueOf(XMLUtils.getValueAtXPath(supplement, "./@Description")));
	                    singleSupplement.put("id", String.valueOf(XMLUtils.getValueAtXPath(supplement, "./@ID")));
	                    singleSupplement.put("name", String.valueOf(XMLUtils.getValueAtXPath(supplement, "./@Name")));
	                    singleSupplement.put("status", String.valueOf(XMLUtils.getValueAtXPath(supplement, "./@Status")));
	                    
	                    //SingleSupplement TimeSpan
	                    Element ssTimeSpanElement = XMLUtils.getFirstElementAtXPath(supplement, "./pac:TimeSpan");
	                    if (ssTimeSpanElement != null && (ssTimeSpanElement.hasChildNodes() || ssTimeSpanElement.hasAttributes())) {
	                        JSONObject timeSpan = getTimeSpan(ssTimeSpanElement);
	                        singleSupplement.put(JSON_PROP_TIMESPAN, timeSpan);
	                    }

	                    //SingleSupplement priceTotal
	                	Element[] priceTotalElements = XMLUtils.getElementsAtXPath(supplement, "./pac:Price/ns:Total");
	                	if (priceTotalElements != null && priceTotalElements.length > 0) {
	                        JSONArray totalArray = new JSONArray();
	                        JSONObject priceJson = new JSONObject();
	                        
	                        for (Element total : priceTotalElements) {
	        	                JSONObject totalObj = new JSONObject();
	        	
	        	                totalObj.put(JSON_PROP_AMOUNTBEFORETAX, String.valueOf(XMLUtils.getValueAtXPath(total, "./@AmountBeforeTax")));
	        	                totalObj.put(JSON_PROP_AMOUNTAFTERTAX, String.valueOf(XMLUtils.getValueAtXPath(total, "./@AmountAfterTax")));
	        	                totalObj.put(JSON_PROP_CURRENCYCODE, String.valueOf(XMLUtils.getValueAtXPath(total, "./@CurrencyCode")));
	        	                totalObj.put(JSON_PROP_TYPE, String.valueOf(XMLUtils.getValueAtXPath(total, "./@Type")));
	        	
	        	                JSONObject taxJson = getChargeTax(total);
	        	
	        	                totalObj.put(JSON_PROP_TAXES, taxJson);
	        	                totalArray.put(totalObj);               
	                        }
	                        priceJson.put(JSON_PROP_TOTAL, totalArray);
	                        singleSupplement.put(JSON_PROP_PRICE, priceJson);
	                    }
	                    
	                	//SingleSupplement deposits
	                	Element depositElement = XMLUtils.getFirstElementAtXPath(supplement, "./pac:Deposits");
	                    if (depositElement != null) {
	                    	JSONObject depositjson = new JSONObject();
	                    	JSONArray depositArr = new JSONArray();
	                    	Element[] depositElements = XMLUtils.getElementsAtXPath(depositElement, "./pac:Deposit");
	                    	for (Element deposit : depositElements) {
	                            JSONObject depositJson = new JSONObject();
	                            depositJson.put("depositAmount", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@DepositAmount")));
	                            depositJson.put("currencyCode", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@CurrencyCode")));
	                            depositJson.put("type", String.valueOf(XMLUtils.getValueAtXPath(deposit, "./@Type")));
	                            depositArr.put(depositJson);
	                    	}
	                    	depositjson.put("deposit", depositArr);
	                    	singleSupplement.put("deposits", depositjson);
	                    }
	                    
	                    //SingleSupplement passenger
	                    Element[] passengers = XMLUtils.getElementsAtXPath(supplement, "./pac:Passenger");
	                    if (passengers != null && passengers.length > 0) {
	        	            JSONArray passArr = new JSONArray();
	        	            for (Element pass : passengers) {
	        	            	JSONObject passJson = new JSONObject();
	        	            	passJson.put("minimumAge", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MinimumAge")));
	        	            	passJson.put("maximumAge", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MaximumAge")));
	        	                passJson.put("maximumPassengers", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MaximumPassengers")));
	        	                passJson.put("minimumPassengers", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@MinimumPassengers")));
	        	                passJson.put("passengerTypeCode", String.valueOf(XMLUtils.getValueAtXPath(pass, "./@PassengerTypeCode")));
	        	                passArr.put(passJson);
	        	            }
	        	            singleSupplement.put("passenger", passArr);
	                    }
	                    supplementJsonArr.put(singleSupplement);
	            	}
	            	roomStayJson.put("singleSupplement",supplementJsonArr);	
	            }
            }
        }
        
        return roomStayJson;
    }

	public static JSONArray getGuests(Element[] guestCountElems) {
		JSONArray guestCountarr = new JSONArray();
		for(Element guestCount : guestCountElems) {
        	String resGuestRPH = String.valueOf(XMLUtils.getValueAtXPath(guestCount, "./@ResGuestRPH"));
        	JSONObject guestCountJson = new JSONObject();
        	guestCountJson.put("resGuestRPH", resGuestRPH);
        	guestCountarr.put(guestCountJson);
        }
		return guestCountarr;
	}
	//12-4-18 added ratesJson(Rahul)    
	public static JSONArray getRatesForRoomRates(Element roomRateElem) {
    	Element rates[] = XMLUtils.getElementsAtXPath(roomRateElem, "./ns:Rates/ns:Rate");
    	JSONArray rateArray = new JSONArray();
    	if (rates != null && rates.length != 0) {
	    	for(Element rate: rates)
	    	{
	    		JSONObject rateJson = new JSONObject();
	    		String minAge = String.valueOf(XMLUtils.getValueAtXPath(rate, "./@MinAge"));
	    		String maxAge = String.valueOf(XMLUtils.getValueAtXPath(rate, "./@MaxAge"));
	    		String minGuestApplicable = String.valueOf(XMLUtils.getValueAtXPath(rate, "./@MinGuestApplicable"));
	    		String maxGuestApplicable = String.valueOf(XMLUtils.getValueAtXPath(rate, "./@MaxGuestApplicable"));
	    		
	    		rateJson.put("minAge", minAge);
	    		rateJson.put("maxAge", maxAge);
	    		rateJson.put("minGuestApplicable", minGuestApplicable);
	    		rateJson.put("maxGuestApplicable", maxGuestApplicable);
	    		
	    		rateArray.put(rateJson);
	    	}
    	}
		return rateArray;
	}
	public static JSONObject getLengthOfStayElemJSON(Element lengthOfStayElem) {
        JSONObject lengthOfStayJson = new JSONObject();
        // Element bookingRuleElem = XMLUtils.getFirstElementAtXPath(lengthOfStayElem,
        // "./ns:LengthOfStay");

        String minMaxMessageType = String.valueOf(XMLUtils.getValueAtXPath(lengthOfStayElem, "./@MinMaxMessageType"));
        lengthOfStayJson.put(JSON_PROP_MINMAXMESSAGETYPE, minMaxMessageType);

        String time = String.valueOf(XMLUtils.getValueAtXPath(lengthOfStayElem, "./@Time"));
        lengthOfStayJson.put(JSON_PROP_TIME, time);

        String timeUnit = String.valueOf(XMLUtils.getValueAtXPath(lengthOfStayElem, "./@TimeUnit"));
        lengthOfStayJson.put(JSON_PROP_TIMEUNIT, timeUnit);

        return lengthOfStayJson;
    }

    public static JSONObject getTotalRoomRateJSON(Element roomRateElem, JSONObject total, Boolean flag) {
        Element totalElem = XMLUtils.getFirstElementAtXPath(roomRateElem, "./ns:Total");

        String type = String.valueOf(XMLUtils.getValueAtXPath(totalElem, "./@Type"));
        total.put(JSON_PROP_TYPE, type);

        String amountAfterTax = String.valueOf(XMLUtils.getValueAtXPath(totalElem, "./@AmountAfterTax"));
        total.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTax);

        String amountBeforeTax = String.valueOf(XMLUtils.getValueAtXPath(totalElem, "./@AmountBeforeTax"));
        total.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTax);

        String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(totalElem, "./@CurrencyCode"));
        total.put(JSON_PROP_CURRENCYCODE, currencyCode);

        

        JSONArray taxArr = new JSONArray();

        Element taxesElem = XMLUtils.getFirstElementAtXPath(totalElem, "./ns:Taxes");
        //12-4-18 added amount and CurrencyCode(Rahul)
        String amount = String.valueOf(XMLUtils.getValueAtXPath(taxesElem, "./@Amount"));
        String taxesCurrencyCode = String.valueOf(XMLUtils.getValueAtXPath(taxesElem, "./@CurrencyCode"));
        
        JSONObject taxesJson = new JSONObject();

        Element[] taxArray = XMLUtils.getElementsAtXPath(taxesElem, "./ns:Tax");
        for (Element taxElem : taxArray) {

            JSONObject tax = getTaxJson(taxElem);
            taxArr.put(tax);

        }
        //12-4-18 added amount and CurrencyCode(Rahul)
        taxesJson.put(JSON_PROP_AMOUNT, amount);
        taxesJson.put(JSON_PROP_CURRENCYCODE, taxesCurrencyCode);
        taxesJson.put(JSON_PROP_TAX, taxArr);
        total.put(JSON_PROP_TAXES, taxesJson);

        Element tPA_ExtensionsElem = XMLUtils.getFirstElementAtXPath(roomRateElem, "./ns:TPA_Extensions");

        Element pkgs_TPAElem = XMLUtils.getFirstElementAtXPath(tPA_ExtensionsElem, "./pac:Pkgs_TPA");

        Boolean isIncludedInTour = Boolean.valueOf(XMLUtils.getValueAtXPath(pkgs_TPAElem, "./@isIncludedInTour"));
        
        if(flag == true)
        total.put(JSON_PROP_ISINCLUDEDINTOUR, isIncludedInTour);

        // Last MinuteDiscount Element
        Element[] lastMinuteDiscountsElement = XMLUtils.getElementsAtXPath(totalElem,
                "./ns:TPA_Extensions/pac:LastMinuteDiscount/pac:LastMinuteDiscounts");

        if (lastMinuteDiscountsElement != null && lastMinuteDiscountsElement.length > 0) {
            JSONArray lastMinuteDiscounts = new JSONArray();
            for (Element lastMinuteDiscountElement : lastMinuteDiscountsElement) {
                JSONObject lastMinuteDiscount = new JSONObject();

                String amountBeforeTaxLMD = String
                        .valueOf(XMLUtils.getValueAtXPath(lastMinuteDiscountElement, "./@AmountBeforeTax"));
                lastMinuteDiscount.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTaxLMD);

                String amountAfterTaxLMD = String
                        .valueOf(XMLUtils.getValueAtXPath(lastMinuteDiscountElement, "./@AmountAfterTax"));
                lastMinuteDiscount.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTaxLMD);

                String CurrencyCodeLMD = String
                        .valueOf(XMLUtils.getValueAtXPath(lastMinuteDiscountElement, "./@CurrencyCode"));
                lastMinuteDiscount.put(JSON_PROP_CURRENCYCODE, CurrencyCodeLMD);

                String typeLMD = String.valueOf(XMLUtils.getValueAtXPath(lastMinuteDiscountElement, "./@Type"));
                lastMinuteDiscount.put(JSON_PROP_TYPE, typeLMD);

                lastMinuteDiscounts.put(lastMinuteDiscount);
            }

            total.put("lastMinuteDiscounts", lastMinuteDiscounts);
        }

        return total;

    }

    public static JSONObject getTaxJson(Element taxElem) {

        JSONObject tax = new JSONObject();
        String amount = String.valueOf(XMLUtils.getValueAtXPath(taxElem, "./@Amount"));
        tax.put(JSON_PROP_AMOUNT, amount);

        String currencyCode = String.valueOf(XMLUtils.getValueAtXPath(taxElem, "./@CurrencyCode"));
        tax.put(JSON_PROP_CURRENCYCODE, currencyCode);

        Element taxDescriptionElem = XMLUtils.getFirstElementAtXPath(taxElem, "./ns:TaxDescription");

        String name = String.valueOf(XMLUtils.getValueAtXPath(taxDescriptionElem, "./@Name"));

        tax.put(JSON_PROP_TAXDESCRIPTION, name);

        return tax;
    }

}
