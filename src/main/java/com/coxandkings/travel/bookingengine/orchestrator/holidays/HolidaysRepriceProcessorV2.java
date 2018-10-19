package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class HolidaysRepriceProcessorV2 implements HolidayConstants {

	private static final Logger logger = LogManager.getLogger(HolidaysRepriceProcessorV2.class);
	private static BigDecimal totalFare = new BigDecimal("0");
	private static String paxType;
	private static BigDecimal totalPrice = new BigDecimal(0);
	private static String mRcvsPriceCompQualifier = JSON_PROP_RECEIVABLES.concat(SIGMA).concat(".").concat(JSON_PROP_RECEIVABLE).concat(".");
	private static String mTaxesPriceCompQualifier = JSON_PROP_TAXES.concat(SIGMA).concat(".").concat(JSON_PROP_TAX).concat(".");
	private static String mIncvPriceCompFormat = JSON_PROP_INCENTIVE.concat(".%s@").concat(JSON_PROP_CODE);
	private static Map<String, BigDecimal> paxInfoMap = new LinkedHashMap<String, BigDecimal>(); 
	private static PriceComponentsGroup pkgsuppFareComGroup;
	private static PriceComponentsGroup pkgtotalFareComGroup;
	private static Map<String,JSONObject>  pkgTotalFareMap = new HashMap<String, JSONObject>();
	
	private static BigDecimal componentTotalFare = new BigDecimal(0);
	private static BigDecimal componentBaseFare = new BigDecimal(0);
	private static BigDecimal supplierCompTotalFare = new BigDecimal(0);
	private static BigDecimal supplierCompBaseFare = new BigDecimal(0);
	

	public static String process(JSONObject requestJson) throws InternalProcessingException, RequestProcessingException, ValidationException {
		Element requestElement = null;
		JSONObject requestHeader = null, requestBody = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		
		try {
			validateRequestParameters(requestJson);
			
			//No flag JSON request to JSON request with flags transformation
	        JSONObject transformedRequestJSON = HolidaysBookProcessor.requestJSONTransformation(requestJson);
			TrackingContext.setTrackingContext(requestJson);
			opConfig = HolidaysConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			
			requestHeader = transformedRequestJSON.getJSONObject(JSON_PROP_REQHEADER);
			requestBody = transformedRequestJSON.getJSONObject(JSON_PROP_REQBODY);
			
			usrCtx = UserContext.getUserContextForSession(requestHeader);
			
			// CREATE SI REQUEST 
			requestElement = createSIRequest(requestHeader, requestBody, opConfig, usrCtx);
		}
		catch(ValidationException valx) {
			throw valx;
		}
	   	catch (Exception x) {
	   		logger.error("Exception during request processing", x);
	   		throw new RequestProcessingException(x);
	   	}
		
		try {
			Element responseElement = null;
			logger.info(String.format("SI XML Request = %s", XMLTransformer.toString(requestElement)));
			responseElement = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), requestElement);
			logger.info(String.format("SI XML Response = %s", XMLTransformer.toString(responseElement)));
			if (responseElement == null)
				throw new Exception("Null response received from SI");
			
			int idx =0;
			
			//Converting SI XML response to SI JSON Response
			JSONObject resBodyJson = ConvertSIResponseToJson(responseElement, idx);

			JSONObject resJson = new JSONObject();
			resJson.put("responseHeader", requestHeader);
			resJson.put("responseBody", resBodyJson);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
			
			// Call BRMS Supplier and Client Commercials		
			JSONObject resSupplierCommJson = HolidaysSupplierCommercials.getSupplierCommercials(requestJson, resJson,CommercialsOperation.Book);
			logger.info(String.format("Supplier Commercial Response = %s", resSupplierCommJson.toString()));
			if (resSupplierCommJson.has("error")){
				logger.error(String.format("Received error from SI or an Empty BRI", resJson.toString()));
				throw new Exception("Received error from SI or an Empty BRI");
			}
			else if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierCommJson.getString(JSON_PROP_TYPE))) {
	                logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", resSupplierCommJson.toString()));
	                throw new Exception("A failure response was received from Supplier Commercials calculation engine");
			}

			JSONObject resClientCommJson = HolidaysClientCommercials.getClientCommercials(requestJson,resSupplierCommJson);
			 if (BRMS_STATUS_TYPE_FAILURE.equals(resClientCommJson.getString(JSON_PROP_TYPE))) {
	                logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", resClientCommJson.toString()));
	                throw new Exception("A failure response was received from Client Commercials calculation engine");
	            }
			logger.info(String.format("Client Commercial Response = %s", resClientCommJson.toString()));
			 
			calculatePricesV2(requestJson, resJson, resSupplierCommJson, resClientCommJson, true, usrCtx);

			// Calculate company taxes
			TaxEngine.getCompanyTaxes(requestJson, resJson);
			
			// Apply company offers
	        HolidaysCompanyOffers.getCompanyOffers(requestJson, resJson, OffersType.COMPANY_SEARCH_TIME,CommercialsOperation.Reprice);
			// Call to Redis Cache
			pushSuppFaresToRedisAndRemove(resJson);
			
			return resJson.toString();

		}
		catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}
	
	private static void validateRequestParameters(JSONObject reqJson) throws ValidationException {
		HolidaysRequestValidator.validateDynamicPkg(reqJson);
		HolidaysRequestValidator.validateTourCode(reqJson);
		HolidaysRequestValidator.validateSubTourCode(reqJson);
		HolidaysRequestValidator.validateBrandName(reqJson);
		HolidaysRequestValidator.validatePassengerCounts(reqJson);
		HolidaysRequestValidator.validateResGuestsInfo(reqJson);
		HolidaysRequestValidator.validateComponents(reqJson);
		HolidaysRequestValidator.validateGlobalInfo(reqJson);
	}

	private static JSONObject ConvertSIResponseToJson(Element responseElement, int idx) {
		JSONObject resBodyJson = new JSONObject();
		JSONArray dynamicPackageArray = new JSONArray();

		Element[] oTA_wrapperElems = XMLUtils.getElementsAtXPath(responseElement,"./pac1:ResponseBody/pac:OTA_DynamicPkgAvailRSWrapper");
		for (Element oTA_wrapperElem : oTA_wrapperElems) {
			JSONArray bookReferencesArray = new JSONArray();

			String supplierIDStr = String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:SupplierID"));
			String sequenceStr = String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:Sequence"));

			// -----Error Handling Started-----
			// 13-04-18 Added to handle the scenario when SI errors out (Rahul)
			JSONObject errorJson = new JSONObject();
			HolidaysUtil.SIErrorHandler(oTA_wrapperElem, errorJson);
			if (errorJson.length() != 0) 
				logTheError(errorJson, supplierIDStr, sequenceStr);
			// -----Error Handling ended-----

			else {
				Element[] dynamicPackageElemArray = XMLUtils.getElementsAtXPath(oTA_wrapperElem,"./ns:OTA_DynamicPkgAvailRS/ns:DynamicPackage");
				for (Element dynamicPackageElem : dynamicPackageElemArray) {
					
					String tourCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackageElem,"./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/@ID"));
					String subTourCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackageElem,"./ns:Components/ns:PackageOptionComponent/ns:PackageOptions/ns:PackageOption/@QuoteID"));
					String brandName = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackageElem,"./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/ns:CompanyName/@CompanyShortName"));

					JSONObject dynamicPackJson = HolidaysSearchProcessor.getSupplierResponseDynamicPackageJSON(dynamicPackageElem, false);
					dynamicPackJson.put("bookRefIdx", idx);

					dynamicPackJson.put(JSON_PROP_SUPPLIERID, supplierIDStr);
					dynamicPackJson.put(JSON_PROP_SEQUENCE, sequenceStr);

					dynamicPackJson.put("tourCode", tourCode);
					dynamicPackJson.put("subTourCode", subTourCode);
					dynamicPackJson.put("brandName", brandName);

					Element[] resGuestElems = XMLUtils.getElementsAtXPath(dynamicPackageElem,"./ns:ResGuests/ns:ResGuest");
					JSONArray resGuestArr = new JSONArray();
					for (Element resGuest : resGuestElems) {
						JSONObject resGuestsJson = HolidaysBookProcessor.getResGuests(resGuest);
						resGuestArr.put(resGuestsJson);
					}

					dynamicPackJson.put("resGuests", resGuestArr);
					dynamicPackageArray.put(dynamicPackJson);

					// Creating bookReferences Object and supplierBookingFare Object
					JSONObject bookReferencesJson = getBookReference(dynamicPackageElem);
					bookReferencesArray.put(bookReferencesJson);
					idx++;
				}
			}
			if (errorJson.length() > 0) {
				dynamicPackageArray.put(errorJson);
				logger.trace(String.format("Error received from supplier or SI = %s", errorJson.toString()));
			}
			resBodyJson.put("dynamicPackage", dynamicPackageArray);
			if (bookReferencesArray.length() != 0)
				resBodyJson.put("bookReferences", bookReferencesArray);

		}
		return resBodyJson;
	}

	private static void logTheError(JSONObject errorJson, String supplierIDStr, String sequenceStr) {
		errorJson.put("sequence", sequenceStr);
		errorJson.put("supplierID", supplierIDStr);
		errorJson.put("errorSource", "Error from SI or Supplier");
		logger.trace(String.format("Error received from SI = %s", errorJson.toString()));
	}

	public static void calculatePricesV2(JSONObject requestJson, JSONObject resJson, JSONObject resSupplierCommJson,
			JSONObject resClientCommJson, boolean retainSuppFares, UserContext usrCtx) {
		
		Map<String, String> scommToTypeMap = getSupplierCommercialsAndTheirType(resSupplierCommJson);
		Map<String, String> clntCommToTypeMap = getClientCommercialsAndTheirType(resClientCommJson);

		String clientMarket = requestJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		String clientCcyCode = requestJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		ClientType clientType = ClientType.valueOf(requestJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
		 
		Map<String, JSONArray> ccommSuppBRIJsonMap = HolidaysSearchProcessor.getSupplierWisePackageDetailsFromClientCommercials(resClientCommJson);
		Map<String, Integer> suppIndexMap = new HashMap<String, Integer>();
		Map<String,BigDecimal> compPaxInfoMap = new HashMap<String,BigDecimal>() ;
		
		String paxType = "",ratePlanCategory = "";

		BigDecimal paxCount = new BigDecimal(0);
		BigDecimal paxQty = new BigDecimal(0);
		
		// retrieve passenger Qty from requestJson
				JSONObject reqCurrentDynamicPkg = new JSONObject();
				JSONObject resdynamicPkgJson = new JSONObject();
				JSONArray dynamicPkgArray = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE);
				for (int a = 0; a < dynamicPkgArray.length(); a++) {
					resdynamicPkgJson = dynamicPkgArray.getJSONObject(a);
					String brandNameRes = resdynamicPkgJson.getString("brandName");
					String tourCodeRes = resdynamicPkgJson.getString("tourCode");
					String subTourCodeRes = resdynamicPkgJson.getString("subTourCode");
					String resUniqueKey = brandNameRes + tourCodeRes + subTourCodeRes;
					JSONArray reqDynamicPkgArray = requestJson.getJSONObject(JSON_PROP_REQBODY)
							.getJSONArray(JSON_PROP_DYNAMICPACKAGE);

					for (int i = 0; i < reqDynamicPkgArray.length(); i++) {

						JSONObject dynamicPkg = reqDynamicPkgArray.getJSONObject(i);
						String reqBrandName = dynamicPkg.getString("brandName");
						String reqTourCode = dynamicPkg.getString("tourCode");
						String reqSubTourCode = dynamicPkg.getString("subTourCode");

						String reqUniqueKey = reqBrandName + reqTourCode + reqSubTourCode;

						if (resUniqueKey.equals(reqUniqueKey)) {
							reqCurrentDynamicPkg = dynamicPkg;
							break;
						}

					}
				}

				// create Map of componentWise passenger Type quantity
				Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty = retrievePassengerQty(reqCurrentDynamicPkg,resdynamicPkgJson);
				// For each dynamicPackage in reprice results
				for (int j = 0; j < dynamicPkgArray.length(); j++) {
					JSONObject dynamicPackageJson = dynamicPkgArray.getJSONObject(j);
					String suppID = dynamicPackageJson.getString(JSON_PROP_SUPPLIERID);
					componentTotalFare = new BigDecimal(0);
					componentBaseFare = new BigDecimal(0);
					supplierCompTotalFare = new BigDecimal(0);
					supplierCompBaseFare = new BigDecimal(0);
					pkgsuppFareComGroup = new PriceComponentsGroup(JSON_PROP_TOTAL, clientCcyCode, new BigDecimal(0), true);
					pkgtotalFareComGroup = new PriceComponentsGroup(JSON_PROP_TOTAL, clientCcyCode, new BigDecimal(0), true);
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
		    		
		    		JSONObject componentJson = dynamicPackageJson.getJSONObject(JSON_PROP_COMPONENTS);
		    		JSONObject redisComponentJson =new JSONObject(componentJson.toString());
		    		String roomTypeSI ="";
					// For Hotel Component
					if (componentJson.has(JSON_PROP_HOTEL_COMPONENT)) {
		
						JSONObject hotelCompJson = componentJson.getJSONObject(JSON_PROP_HOTEL_COMPONENT);
						JSONObject redisHotelCompJson = redisComponentJson.getJSONObject(JSON_PROP_HOTEL_COMPONENT);
						String dynamicPkgAction = hotelCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
						String redisdynamicPkgAction = redisHotelCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
						compPaxInfoMap = new HashMap<String,BigDecimal>() ;
						paxCount = new BigDecimal(0);
						paxQty = new BigDecimal(0);
						
						if(dynamicPkgAction.equals(DYNAMICPKGACTION_HOTEL_TOUR)&& redisdynamicPkgAction.equals(DYNAMICPKGACTION_HOTEL_TOUR)) {
						if (hotelCompJson != null && hotelCompJson.length() > 0) {
							JSONArray roomStayArray = hotelCompJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
							JSONArray redisRoomStayArray = redisHotelCompJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
							
				    		
				    		for (int c = 0; c < roomStayArray.length(); c++) {
								JSONObject roomStayJson = roomStayArray.getJSONObject(c);
								JSONObject redisRoomStayJson = redisRoomStayArray.getJSONObject(c);
								JSONArray roomRateArr = new JSONArray();
								JSONArray redisRoomRateArr = new JSONArray();
								JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
					    		JSONArray clientCommercialPkgInfoArr = new JSONArray();
					    		JSONArray roomPaxTypeFaresJsonArr = new JSONArray();
							    roomTypeSI = roomStayJson.getString(JSON_PROP_ROOMTYPE);
								JSONArray ccommRoomDtlsJsonArr = ccommPkgDtlsJson.getJSONArray(JSON_PROP_ROOMDETAILS);
								paxInfoMap = new LinkedHashMap<String, BigDecimal>();
								
								for (int k = 0; k < ccommRoomDtlsJsonArr.length(); k++) {
		
									JSONObject ccommRoomDtlsJson = ccommRoomDtlsJsonArr.getJSONObject(k);
		
									roomRateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);
									redisRoomRateArr =redisRoomStayJson.getJSONArray(JSON_PROP_ROOMRATE);
									for (int r = 0; r < roomRateArr.length(); r++) {
										JSONObject roomRateJson = roomRateArr.getJSONObject(r);
										JSONObject redisRoomRateJson = redisRoomRateArr.getJSONObject(r);
									    JSONObject totalJson = roomRateJson.getJSONObject(JSON_PROP_TOTAL);
										paxType = totalJson.getString(JSON_PROP_TYPE);
										// ratePlanCategory = roomRateJson.getString("ratePlanCategory");
										
										
										// to get the paxCount rateDescriptionName is the dynamicPkgAction+RoomType to match
										//with the request key and index is saved to handle multiple same type of rooms
											String rateDescriptionName = (dynamicPkgAction + roomTypeSI+c).toLowerCase();
											paxCount = getPaxCountV2(componentWisePaxTypeQty, dynamicPkgAction,
													rateDescriptionName, paxCount, paxType);
		
											totalJson = calculatePriceHotelOrCruise(usrCtx, scommToTypeMap, clntCommToTypeMap, clientMarket,
													clientCcyCode, paxCount, totalJson, roomTypeSI, paxType,
													 suppID, totalFareCompsGroup, totalIncentivesGroup,
													suppPaxTypeFaresJsonArr, clientCommercialPkgInfoArr, ccommRoomDtlsJson,
													clientType, retainSuppFares, paxInfoMap, paxQty,dynamicPkgAction,
													roomRateArr,r,roomRateJson,redisRoomRateJson,roomPaxTypeFaresJsonArr,roomRateArr);
											
		
										
									}
								}
							
								// The supplier fares will be retained only in reprice operation. In reprice, after calculations, supplier
								// prices are saved in Redis cache to be used in book operation.
								//Below redis structure elements at roomStaylevel
							//	if (retainSuppFares) {
									createFareRedisStructure(paxInfoMap, redisRoomStayJson, suppPaxTypeFaresJsonArr,
											clientCommercialPkgInfoArr, roomPaxTypeFaresJsonArr,clientCcyCode,"");
								
							
							//	}
							}
							//Below redis structure elements at complevel
							//if (retainSuppFares) {
								dynamicPkgAction = redisHotelCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
								createFareRedisStructureCompLevel(redisHotelCompJson,dynamicPkgAction,clientCcyCode);
							//}
						}}
					}
					
					//For Cruise Component
					else if (componentJson.has(JSON_PROP_CRUISE_COMPONENT)) {
						JSONObject cruiseComponentJson =componentJson.getJSONObject(JSON_PROP_CRUISE_COMPONENT);
						JSONObject redisCruiseCompJson = redisComponentJson.getJSONObject(JSON_PROP_CRUISE_COMPONENT);
						String dynamicPkgAction = cruiseComponentJson.getString(JSON_PROP_DYNAMICPKGACTION);
						String redisdynamicPkgAction = redisCruiseCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
						paxCount = new BigDecimal(0);
						paxQty = new BigDecimal(0);
						compPaxInfoMap = new HashMap<String, BigDecimal>();
						
			    		if(dynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_TOUR)&& redisdynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_TOUR)) {
						if (cruiseComponentJson != null && cruiseComponentJson.length() > 0) {
							
							JSONArray categoryOptionsArray = cruiseComponentJson.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
							JSONArray redisCategoryOptionsArray = redisCruiseCompJson.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
							
							for (int y = 0; y < categoryOptionsArray.length(); y++) {
								JSONObject categoryOptionsJson = categoryOptionsArray.getJSONObject(y);
								JSONObject redisCategoryOptionsJson = redisCategoryOptionsArray.getJSONObject(y);
								JSONArray categoryOptionArray = categoryOptionsJson.getJSONArray(JSON_PROP_CATEGORYOPTION);
								JSONArray redisCategoryOptionArray = redisCategoryOptionsJson.getJSONArray(JSON_PROP_CATEGORYOPTION);
								
					    		
								for (int z = 0; z < categoryOptionArray.length(); z++) {
									JSONObject categoryOptionJson = categoryOptionArray.getJSONObject(z);
									JSONObject redisCategoryOptionJson = redisCategoryOptionArray.getJSONObject(z);
									roomTypeSI = categoryOptionJson.getString(JSON_PROP_CABINTYPE);
									JSONArray ccommRoomDtlsJsonArr = ccommPkgDtlsJson.getJSONArray(JSON_PROP_ROOMDETAILS);
									JSONObject totalJson = new JSONObject();
									JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
						    		JSONArray clientCommercialPkgInfoArr = new JSONArray();
						    		//to store the after commercial price
						    		JSONArray roomPaxTypeFaresJsonArr =new JSONArray();
						    		
									paxInfoMap = new LinkedHashMap<String, BigDecimal>();
									
									for (int k = 0; k < ccommRoomDtlsJsonArr.length(); k++) {

									 JSONObject ccommRoomDtlsJson = ccommRoomDtlsJsonArr.getJSONObject(k);
									
									//JSONObject ccommRoomDtlsJson = getClientCommercialsPackageDetailsForRoomType(ccommPkgDtlsJson, roomTypeSI);

										JSONArray totalArray = categoryOptionJson.getJSONArray(JSON_PROP_TOTAL);
										JSONArray redistotalArr = redisCategoryOptionJson.getJSONArray(JSON_PROP_TOTAL);
										for (int t = 0; t < totalArray.length(); t++) {
											totalJson = totalArray.getJSONObject(t);
											paxType = totalJson.getString(JSON_PROP_TYPE);
											if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_CRUISE_TOUR)) {
												
												// rateDescriptionName is the dynamicPkgAction+RoomType to match with the
												// request key.
												String rateDescriptionName = (dynamicPkgAction + roomTypeSI+y+z).toLowerCase();
												paxCount = getPaxCountV2(componentWisePaxTypeQty, dynamicPkgAction,
														rateDescriptionName, paxCount, paxType);
											    totalJson= calculatePriceHotelOrCruise(usrCtx, scommToTypeMap,clntCommToTypeMap, clientMarket, 
													clientCcyCode, paxCount,totalJson, roomTypeSI, paxType, suppID,
													totalFareCompsGroup, totalIncentivesGroup, suppPaxTypeFaresJsonArr,
													clientCommercialPkgInfoArr, ccommRoomDtlsJson,clientType, retainSuppFares, paxInfoMap, paxQty,dynamicPkgAction,
													totalArray,t,categoryOptionJson,redisCategoryOptionJson,roomPaxTypeFaresJsonArr,redistotalArr);
											  //  totalArray.put(t,totalJson);
											   // categoryOptionJson.put("total", totalArray);
										}}}
									
									
									// The supplier fares will be retained only in reprice operation. In reprice, after calculations, supplier
									// prices are saved in Redis cache to be used in book operation.
									//Below redis structure elements at categoryOption level for roomType
									//if (retainSuppFares) {
										createFareRedisStructure(paxInfoMap, redisCategoryOptionJson,suppPaxTypeFaresJsonArr,
												clientCommercialPkgInfoArr,roomPaxTypeFaresJsonArr,clientCcyCode,"");
									//}
								}
							}
							//Below redis structure elements at complevel
							//if (retainSuppFares) {
								dynamicPkgAction = redisCruiseCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
								createFareRedisStructureCompLevel(redisCruiseCompJson,dynamicPkgAction,clientCcyCode);
							//}
						}
						}
					}
					
					//For applicable On products 
					
					//For prenight/postnight - Hotel  -START
					String dynamicPkgHotelStr ="";
					
					
						if (componentJson.has(JSON_PROP_PRENIGHT)) {
				
							JSONObject extNightJson = componentJson.getJSONObject(JSON_PROP_PRENIGHT);
							JSONObject redisExtNightJson = redisComponentJson.getJSONObject(JSON_PROP_PRENIGHT);
							if (extNightJson != null && extNightJson.length() > 0) {
								String dynamicPkgAction = extNightJson.getString(JSON_PROP_DYNAMICPKGACTION);
								String redisDynamicPkgAction = redisExtNightJson.getString(JSON_PROP_DYNAMICPKGACTION);
								if(dynamicPkgAction.toLowerCase().contains("prenight")&& redisDynamicPkgAction.toLowerCase().contains("prenight")) {
									if(componentJson.has(JSON_PROP_HOTEL_COMPONENT)) {
									dynamicPkgHotelStr = DYNAMICPKGACTION_HOTEL_PRE;
									}
									else if(componentJson.has(JSON_PROP_CRUISE_COMPONENT)) {
										dynamicPkgHotelStr = DYNAMICPKGACTION_CRUISE_PRE;
									}
									String extNightStr = JSON_PROP_PRENIGHT;
									getExtNightsHotelPrice(retainSuppFares, usrCtx, scommToTypeMap, clntCommToTypeMap,
											clientMarket, clientCcyCode, componentWisePaxTypeQty, suppID,
											ccommPkgDtlsJson, totalFareCompsGroup, totalIncentivesGroup, extNightJson,
											redisExtNightJson, dynamicPkgAction, dynamicPkgHotelStr, extNightStr);			
										
							}
								}}
						
					
						if (componentJson.has(JSON_PROP_POSTNIGHT)) {
							
							JSONObject extNightJson = componentJson.getJSONObject(JSON_PROP_POSTNIGHT);
							JSONObject redisExtNightJson = redisComponentJson.getJSONObject(JSON_PROP_POSTNIGHT);
							if (extNightJson != null && extNightJson.length() > 0) {
								String dynamicPkgAction = extNightJson.getString(JSON_PROP_DYNAMICPKGACTION);
								String redisDynamicPkgAction = redisExtNightJson.getString(JSON_PROP_DYNAMICPKGACTION);
								if(dynamicPkgAction.toLowerCase().contains("postnight")&& redisDynamicPkgAction.toLowerCase().contains("postnight")) {
									if(componentJson.has(JSON_PROP_HOTEL_COMPONENT)) {
									dynamicPkgHotelStr = DYNAMICPKGACTION_HOTEL_POST;
									}
									else if(componentJson.has(JSON_PROP_CRUISE_COMPONENT)) {
									dynamicPkgHotelStr = DYNAMICPKGACTION_CRUISE_POST;
								    }
									String extNightStr = JSON_PROP_POSTNIGHT;
									
									getExtNightsHotelPrice(retainSuppFares, usrCtx, scommToTypeMap, clntCommToTypeMap,
										clientMarket, clientCcyCode, componentWisePaxTypeQty, suppID,
										ccommPkgDtlsJson, totalFareCompsGroup, totalIncentivesGroup, extNightJson,
										redisExtNightJson, dynamicPkgAction, dynamicPkgHotelStr, extNightStr);}
								}}
						roomTypeSI = "";
					
					//For prenight/postnight - Hotel  --END
					
					//For prenight/postnight - Cruise  --START
					//cruise comp old structure
					
					/*else if (componentJson.has(JSON_PROP_CRUISE_COMPONENT)) {
						//cruise comp old structure
						if (componentJson.has(JSON_PROP_PRENIGHT)) {
						JSONObject extNightJson = componentJson.getJSONObject(JSON_PROP_PRENIGHT);
						JSONObject redisExtNightJson = redisComponentJson.getJSONObject(JSON_PROP_PRENIGHT);
						if (extNightJson != null && extNightJson.length() > 0) {
							String dynamicPkgAction = extNightJson.getString(JSON_PROP_DYNAMICPKGACTION);
							String redisDynamicPkgAction = redisExtNightJson.getString(JSON_PROP_DYNAMICPKGACTION);
							if(dynamicPkgAction.toLowerCase().contains("prenight")&& redisDynamicPkgAction.toLowerCase().contains("prenight")) {
								String dynamicPkgCruiseStr = DYNAMICPKGACTION_CRUISE_PRE;
								String extNightStr = JSON_PROP_PRENIGHT;
								getExtNightCruisePrice(retainSuppFares, usrCtx, scommToTypeMap, clntCommToTypeMap,
										clientMarket, clientCcyCode, componentWisePaxTypeQty, suppID, ccommPkgDtlsJson,
										totalFareCompsGroup, totalIncentivesGroup, extNightJson, redisExtNightJson,
										dynamicPkgAction,dynamicPkgCruiseStr,extNightStr);
							}}
					}
						if (componentJson.has(JSON_PROP_POSTNIGHT)) {
							JSONObject extNightJson = componentJson.getJSONObject(JSON_PROP_POSTNIGHT);
							JSONObject redisExtNightJson = redisComponentJson.getJSONObject(JSON_PROP_POSTNIGHT);
							if (extNightJson != null && extNightJson.length() > 0) {
								String dynamicPkgAction = extNightJson.getString(JSON_PROP_DYNAMICPKGACTION);
								String redisDynamicPkgAction = redisExtNightJson.getString(JSON_PROP_DYNAMICPKGACTION);
								if(dynamicPkgAction.toLowerCase().contains("postnight")&& redisDynamicPkgAction.toLowerCase().contains("postnight")) {
									String dynamicPkgCruiseStr = DYNAMICPKGACTION_CRUISE_POST;
									String extNightStr = JSON_PROP_POSTNIGHT;
									getExtNightCruisePrice(retainSuppFares, usrCtx, scommToTypeMap, clntCommToTypeMap,
											clientMarket, clientCcyCode, componentWisePaxTypeQty, suppID, ccommPkgDtlsJson,
											totalFareCompsGroup, totalIncentivesGroup, extNightJson, redisExtNightJson,
											dynamicPkgAction,dynamicPkgCruiseStr,extNightStr);
								}}
						}*/
						
						
						//For prenight/postnight - Cruise  --END
					
					//}
					// prenight -postnight end	
						
					
					// for Insurance -start
					if (componentJson.has(JSON_PROP_INSURANCE_COMPONENT)) {
						JSONArray insuranceCompArr = componentJson.getJSONArray(JSON_PROP_INSURANCE_COMPONENT);
						JSONArray redisInsuranceCompArr = redisComponentJson.getJSONArray(JSON_PROP_INSURANCE_COMPONENT);
		
						for (int a = 0; a < insuranceCompArr.length(); a++) {
							JSONObject insuranceCompJson = insuranceCompArr.getJSONObject(a);
							JSONObject redisInsuranceCompJson = redisInsuranceCompArr.getJSONObject(a);
							String dynamicPkgAction = insuranceCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
							JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
							JSONArray clientCommercialPkgInfoArr = new JSONArray();
							// to store after commercial price
							JSONArray insurancePaxTypeFaresJsonArr = new JSONArray();
							paxCount = new BigDecimal(0);
							paxQty = new BigDecimal(0);
							
							compPaxInfoMap = new HashMap<String,BigDecimal>() ;
							paxInfoMap = new LinkedHashMap<String, BigDecimal>();
							JSONArray planCostArr = insuranceCompJson.getJSONArray(JSON_PROP_PLANCOST);
							JSONArray redisPlanCost = redisInsuranceCompJson.getJSONArray(JSON_PROP_PLANCOST);
							for (int b = 0; b < planCostArr.length(); b++) {
								JSONObject planCostJson = planCostArr.getJSONObject(b);
								JSONObject redisplanCostJson = redisPlanCost.getJSONObject(b);
								BigDecimal amountAfterTax = planCostJson.getBigDecimal(JSON_PROP_AMOUNT);
								BigDecimal amountBeforeTax = planCostJson.getJSONObject(JSON_PROP_BASEPREMIUM)
										.getBigDecimal(JSON_PROP_AMOUNT);
								roomTypeSI = "";
								paxType = planCostJson.getString(JSON_PROP_PAXTYPE);
								JSONArray charge = planCostJson.getJSONArray(JSON_PROP_CHARGE);
								JSONObject applicableOnProductsJson = new JSONObject();
		
								if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_INSURANCE)) {
		
									// rateDescriptionName to match with the request key in componentWisePaxTypeQty
									// map
									String rateDescriptionName = (dynamicPkgAction
											+ insuranceCompJson.getJSONObject("insCoverageDetail").getString("description"))
													.toLowerCase();
									paxCount = getPaxCountV2(componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName,
											paxCount, paxType);
									applicableOnProductsJson = getClientCommercialsApplicableOnForPassengerType(
											ccommPkgDtlsJson, paxType, "", DYNAMICPKGACTION_INSURANCE,dynamicPkgAction);
									getApplicableOnProductsPriceV2(retainSuppFares, usrCtx, scommToTypeMap, clntCommToTypeMap,
											clientMarket, clientCcyCode, paxInfoMap, paxCount,
											suppID, ccommPkgDtlsJson, totalFareCompsGroup, totalIncentivesGroup,
											dynamicPkgAction, planCostJson, applicableOnProductsJson, roomTypeSI,
											suppPaxTypeFaresJsonArr, clientCommercialPkgInfoArr, insuranceCompJson,
											redisInsuranceCompJson, insurancePaxTypeFaresJsonArr, paxType, planCostArr,
											b, redisPlanCost);
								}
								
							}
		
							// Below redis structure elements at Complevel
							//if (retainSuppFares) {
								createFareRedisStructure(paxInfoMap, redisInsuranceCompJson, suppPaxTypeFaresJsonArr,
										clientCommercialPkgInfoArr, insurancePaxTypeFaresJsonArr,clientCcyCode,"");
							//}
		
						}
					}
					// insurance comp end		
					//Transfer components  ----start
					JSONArray totalArray = null,redisTotalArray= null;
					if (componentJson.has(JSON_PROP_TRANSFER_COMPONENT)) {
						JSONArray transferCompArr = componentJson.getJSONArray(JSON_PROP_TRANSFER_COMPONENT);
						JSONArray redisTransferCompArr = redisComponentJson.getJSONArray(JSON_PROP_TRANSFER_COMPONENT);
						for (int i = 0; i < transferCompArr.length(); i++) {
							JSONObject transferJson = transferCompArr.getJSONObject(i);
							JSONObject redisTransferJson = redisTransferCompArr.getJSONObject(i);
							String dynamicPkgAction = transferJson.getString(JSON_PROP_DYNAMICPKGACTION);
							JSONObject applicableOnProductsJson = new JSONObject();
					
							compPaxInfoMap = new HashMap<String, BigDecimal>();
		
							JSONArray groundServiceArr = transferJson.getJSONArray(JSON_PROP_GROUNDSERVICE);
							JSONArray redisGroundServiceArr = redisTransferJson.getJSONArray(JSON_PROP_GROUNDSERVICE);
							for (int g = 0; g < groundServiceArr.length(); g++) {
								JSONObject groundServiceJson = groundServiceArr.getJSONObject(g);
								JSONObject redisGroundServiceJson = redisGroundServiceArr.getJSONObject(g);
								//to handle getDetails
								if(retainSuppFares == false) {
									totalArray = groundServiceJson.getJSONArray(JSON_PROP_TOTALCHARGE);
									redisTotalArray = redisGroundServiceJson.getJSONArray(JSON_PROP_TOTALCHARGE);	
								}else {
								totalArray = groundServiceJson.getJSONArray(JSON_PROP_TOTAL);
								redisTotalArray = redisGroundServiceJson.getJSONArray(JSON_PROP_TOTAL);}
								JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
								JSONArray clientCommercialPkgInfoArr = new JSONArray();
								JSONArray groundPaxTypeFaresJsonArr = new JSONArray();
								paxInfoMap = new LinkedHashMap<String, BigDecimal>();
		
								for (int k = 0; k < totalArray.length(); k++) {
									JSONObject totalJson = totalArray.getJSONObject(k);
									paxType = totalJson.getString(JSON_PROP_TYPE);
									if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_TRANSFER_DEPARTURE)) {
										String rateDescriptionName = dynamicPkgAction.toLowerCase();
										paxCount = getPaxCountV2(componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName,
												paxCount, paxType);
										applicableOnProductsJson = getClientCommercialsApplicableOnForPassengerType(
												ccommPkgDtlsJson, paxType, "", DYNAMICPKGACTION_TRANSFER, dynamicPkgAction);
										getApplicableOnProductsPriceV2(retainSuppFares, usrCtx, scommToTypeMap,
												clntCommToTypeMap, clientMarket, clientCcyCode, paxInfoMap, paxCount, suppID, ccommPkgDtlsJson,
												totalFareCompsGroup,totalIncentivesGroup, dynamicPkgAction, totalJson, applicableOnProductsJson,
												roomTypeSI, suppPaxTypeFaresJsonArr, clientCommercialPkgInfoArr,
												groundServiceJson, redisGroundServiceJson, groundPaxTypeFaresJsonArr,
											    paxType, totalArray, k, redisTotalArray);
		
									}
									if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_TRANSFER_IARRIVAL)) {
										String rateDescriptionName = dynamicPkgAction.toLowerCase();
										paxCount = getPaxCountV2(componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName,
												paxCount, paxType);
										applicableOnProductsJson = getClientCommercialsApplicableOnForPassengerType(
												ccommPkgDtlsJson, paxType, "", DYNAMICPKGACTION_TRANSFER, dynamicPkgAction);
										getApplicableOnProductsPriceV2(retainSuppFares, usrCtx, scommToTypeMap,
												clntCommToTypeMap, clientMarket, clientCcyCode, paxInfoMap, paxCount, suppID, ccommPkgDtlsJson, totalFareCompsGroup,
												totalIncentivesGroup, dynamicPkgAction, totalJson, applicableOnProductsJson,
												roomTypeSI, suppPaxTypeFaresJsonArr, clientCommercialPkgInfoArr,
												groundServiceJson, redisGroundServiceJson, groundPaxTypeFaresJsonArr,
												paxType, totalArray, k, redisTotalArray);
									}
		
								}
		
							//	if (retainSuppFares) {
									createFareRedisStructure(paxInfoMap, redisGroundServiceJson, suppPaxTypeFaresJsonArr,
											clientCommercialPkgInfoArr, groundPaxTypeFaresJsonArr,clientCcyCode,"");
		
							//	}
		
							}
						}
		
					}
						//Transfer components -----end	
						//For extras and Surcharge component 	
					//Creating extraComponent in componentForRedis JSONObject
					
					PriceComponentsGroup totalFareFeesGroup = new PriceComponentsGroup(JSON_PROP_TOTAL, clientCcyCode, new BigDecimal(0), true);
					//Map<String,JSONObject> feeComponentMap = new HashMap<String,JSONObject>();
					JSONObject globalInfoJson = dynamicPackageJson.getJSONObject(JSON_PROP_GLOBALINFO);
					JSONArray feeArr = globalInfoJson.getJSONArray(JSON_PROP_FEE);
					JSONArray extrasArr = new JSONArray();
					JSONArray surchargeArr = new JSONArray();
					BigDecimal baseFareAmtFee= new BigDecimal(0);
					BigDecimal totalFareAmtFee= new BigDecimal(0);
					JSONArray extraJsonFeeArr = new JSONArray();
					for(int f=0;f< feeArr.length();f++) {
						JSONObject feeJson = feeArr.getJSONObject(f);
						String feeCode = feeJson.getString("feeCode");
						String feeName = feeJson.getString("feeName");
						JSONArray remainingFeeArr = new JSONArray();
						JSONObject extraJson =new JSONObject();
						JSONObject surchargeJson =new JSONObject();
						JSONObject remainingFeeJson = new JSONObject();
						if (feeName.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)){
							extrasArr = new JSONArray();
							extrasArr.put(feeJson);
							extraJson.put("extras", extrasArr);
							extraJsonFeeArr.put(extraJson);
						}
						if (feeCode.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)) {
							surchargeArr.put(feeJson);
							surchargeJson.put("surcharges", surchargeArr);
							extraJsonFeeArr.put(surchargeJson);
						}
						
						else if (!feeName.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)
								&& !feeCode.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)) {
							remainingFeeArr.put(feeJson);
							remainingFeeJson.put("remFee", remainingFeeArr);
							extraJsonFeeArr.put(remainingFeeJson);
						}
						redisComponentJson.put("extraComponent", extraJsonFeeArr);
						
						//Below code for adding fees to final package price apart from extras and surcharge
						//Since we consider all fee array in extraComponent now below code is commented -31 May 2018
						/*if (!feeName.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)
								&& !feeCode.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)) {
							// for extrasComponent
							if (feeJson.get(JSON_PROP_TYPE).equals("")) {
								paxCount = getPaxCountV2(componentWisePaxTypeQty, DYNAMICPKGACTION_EXTRAS,
										DYNAMICPKGACTION_EXTRAS, paxCount, paxType);
		
								paxCount = new BigDecimal(0);
								for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
									paxCount = paxCount.add(entry.getValue());
		
								}
								if (feeJson.getString("maxChargeUnitApplies").equalsIgnoreCase(paxCount.toString())) {
									paxCount = new BigDecimal(1);
								}
							}
							baseFareAmtFee = baseFareAmtFee
									.add(feeJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX).multiply(paxCount));
							totalFareAmtFee = totalFareAmtFee.add(feeJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
		
							JSONArray paxTypeTaxJsonArr = feeJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
		
							for (int l = 0; l < paxTypeTaxJsonArr.length(); l++) {
								JSONObject paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
								clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
								String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
		
								totalFareFeesGroup.add(
										mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXDESCRIPTION),
										clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
							}
		
							JSONObject totalPriceJson = new JSONObject();
							totalPriceJson = (JSONObject) totalFareFeesGroup.toJSON();
							totalPriceJson.put(JSON_PROP_AMOUNTAFTERTAX, totalFareAmtFee);
							totalPriceJson.put(JSON_PROP_AMOUNTBEFORETAX, baseFareAmtFee);
							totalPriceJson.remove("amount");
							pkgTotalFareMap.put("feesTotal", totalPriceJson);
						}*/
						
					}
					
					// extras start
						if (redisComponentJson.has("extraComponent")) {
							JSONArray extraJsonArr = redisComponentJson.getJSONArray("extraComponent");
							JSONArray redisExtrasArr = new JSONArray();
							JSONArray redisSurchargeArr = new JSONArray();
							JSONArray redisRemFeeArr = new JSONArray();
							for(int e=0;e<extraJsonArr.length();e++) {
								
							JSONObject newExtraJson = extraJsonArr.getJSONObject(e);
							if(newExtraJson.has("extras")) {
							redisExtrasArr = newExtraJson.optJSONArray("extras");
							}
							if(newExtraJson.has("surcharges")) {
							redisSurchargeArr = newExtraJson.optJSONArray("surcharges");
							}
							
							if(newExtraJson.has("remFee")) {
								redisRemFeeArr = newExtraJson.optJSONArray("remFee");
								}
							
							 JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
							 JSONArray clientCommercialPkgInfoArr = new JSONArray();
							 JSONArray extraPaxTypeFaresJsonArr = new JSONArray();
							 
								if(newExtraJson.has("extras")) {
							for (int i = 0; i < redisExtrasArr.length(); i++) {
								JSONObject redisExtraJson = redisExtrasArr.getJSONObject(i);
								String extraFeeName = redisExtraJson.getString("feeName");
								String extraFeeCode = redisExtraJson.getString("feeCode");
								
								for (int f = 0; f < feeArr.length(); f++) {
									JSONObject feeJson = feeArr.getJSONObject(f);
									JSONObject applicableOnProductsJson = new JSONObject();
									String feeCode = feeJson.getString("feeCode");
									String feeName = feeJson.getString("feeName");
									
									paxType = feeJson.getString(JSON_PROP_TYPE);
			
									if (feeName.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)
											&& extraFeeName.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)&& extraFeeCode.equalsIgnoreCase(feeCode) ) {
										//extraJson = new JSONObject();
										String rateDescriptionName = DYNAMICPKGACTION_EXTRAS;
										
			
										//paxCount = getPaxCountV2(componentWisePaxTypeQty, DYNAMICPKGACTION_EXTRAS,
										//		rateDescriptionName, paxCount, paxType);
										paxCount = new BigDecimal(0);
										//if (paxType.equals("") || paxType.equals(null)) {
											for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
												paxCount = paxCount.add(entry.getValue());
										//	}
										}
									/*	if (redisExtraJson.getString("maxChargeUnitApplies")
												.equalsIgnoreCase(paxCount.toString())) {
											paxCount = new BigDecimal(1);
										}*/
										
										newExtraJson.put("feeCode", feeCode);
										newExtraJson.put("feeName", feeName);
										newExtraJson.put("text", feeJson.getString("text"));
										newExtraJson.put("type", feeJson.getString("type"));
										
										JSONArray guestCountArr= new JSONArray();
										if(retainSuppFares) {
										JSONArray resGuestArr = dynamicPackageJson.getJSONArray("resGuests");
										for (int r=0; r<resGuestArr.length();r++) {
											JSONObject resGuestJson = new JSONObject();
											String resGuestRPH = resGuestArr.getJSONObject(r).getString("resGuestRPH");
											resGuestJson.put("resGuestRPH", resGuestRPH);
											guestCountArr.put(resGuestJson);
										}
										newExtraJson.put("guestCount", guestCountArr);
										}
										
										applicableOnProductsJson = getClientCommercialsApplicableOnForPassengerType(
												ccommPkgDtlsJson, paxType, "", DYNAMICPKGACTION_EXTRAS, feeCode);
										getApplicableOnProductsPriceV2(retainSuppFares, usrCtx, scommToTypeMap, clntCommToTypeMap,
												clientMarket, clientCcyCode, paxInfoMap, paxCount, suppID, ccommPkgDtlsJson,
												totalFareCompsGroup, totalIncentivesGroup,DYNAMICPKGACTION_EXTRAS, redisExtraJson,
												applicableOnProductsJson, roomTypeSI,suppPaxTypeFaresJsonArr, clientCommercialPkgInfoArr,
												feeJson, redisExtraJson,extraPaxTypeFaresJsonArr, paxType, feeArr, f, redisExtrasArr);
			
										
								}
	
								}
						//	if (retainSuppFares) {
								if (extraFeeName.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)
										|| extraFeeCode.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)) {
									createFareRedisStructure(paxInfoMap, newExtraJson, suppPaxTypeFaresJsonArr,
											clientCommercialPkgInfoArr, extraPaxTypeFaresJsonArr,clientCcyCode,DYNAMICPKGACTION_EXTRAS);
									newExtraJson.remove("extras");
									//to show paxTypeFares in extra comp
									if (paxType.equals("") || paxType.equals(null)) {
									JSONArray paxTypeFares = newExtraJson.getJSONObject("totalPricingInfo").getJSONArray("paxTypeFares");
								
									
									JSONArray paxTypeFaresArr = createExtraCompPaxTypeFare(paxType, paxCount,
											newExtraJson,paxTypeFares,"totalPricingInfo");
									newExtraJson.getJSONObject("totalPricingInfo").put("paxTypeFares",paxTypeFaresArr);		
									
									JSONArray suppPaxTypeFares = newExtraJson.getJSONObject("supplierPricingInfo").getJSONArray("paxTypeFares");
									//to show paxTypeFares in extra comp
									JSONArray suppPaxTypeFaresArr = createExtraCompPaxTypeFare(paxType, paxCount,
											newExtraJson,suppPaxTypeFares,"supplierPricingInfo");
									
									newExtraJson.getJSONObject("supplierPricingInfo").put("paxTypeFares",suppPaxTypeFaresArr);
									
									JSONArray ccpaxTypeFaresArr = getCCExtraPaxTypeFares(paxType, paxCount,
											newExtraJson, redisExtraJson);
									newExtraJson.put("clientCommercialInfo", ccpaxTypeFaresArr);
								}
							}}}
							if (newExtraJson.has("surcharges")) {
								for (int i = 0; i < redisSurchargeArr.length(); i++) {
									JSONObject redisSurchargeJson = redisSurchargeArr.getJSONObject(i);
									String extraFeeName = redisSurchargeJson.getString("feeName");
									String extraFeeCode = redisSurchargeJson.getString("feeCode");
		
									for (int f = 0; f < feeArr.length(); f++) {
										JSONObject feeJson = feeArr.getJSONObject(f);
										JSONObject applicableOnProductsJson = new JSONObject();
										String feeCode = feeJson.getString("feeCode");
										String feeName = feeJson.getString("feeName");
		
										paxType = feeJson.getString(JSON_PROP_TYPE);
		
										if (feeCode.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)
												&& extraFeeCode.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)) {
											// extraJson = new JSONObject();
											String rateDescriptionName = "Surcharge";
		
											//paxCount = getPaxCountV2(componentWisePaxTypeQty, DYNAMICPKGACTION_SURCHARGES,
											//		rateDescriptionName, paxCount, paxType);
											paxCount = new BigDecimal(0);
											//if (paxType.equals("") || paxType.equals(null)) {
												for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
													paxCount = paxCount.add(entry.getValue());
												//}
											}
											/*if (redisSurchargeJson.getString("maxChargeUnitApplies")
													.equalsIgnoreCase(paxCount.toString())) {
												paxCount = new BigDecimal(1);
											}*/
											newExtraJson.put("feeCode", feeCode);
											newExtraJson.put("feeName", feeName);
											newExtraJson.put("text", feeJson.getString("text"));
											newExtraJson.put("type", feeJson.getString("type"));
											
											if(retainSuppFares) {
											JSONArray guestCountArr= new JSONArray();
											JSONArray resGuestArr = dynamicPackageJson.getJSONArray("resGuests");
											for (int r=0; r<resGuestArr.length();r++) {
												JSONObject resGuestJson = new JSONObject();
												String resGuestRPH = resGuestArr.getJSONObject(r).getString("resGuestRPH");
												resGuestJson.put("resGuestRPH", resGuestRPH);
												guestCountArr.put(resGuestJson);
											}
											newExtraJson.put("guestCount", guestCountArr);
											}
											applicableOnProductsJson = getClientCommercialsApplicableOnForPassengerType(
													ccommPkgDtlsJson, paxType, "", rateDescriptionName, feeCode);
											getApplicableOnProductsPriceV2(retainSuppFares, usrCtx, scommToTypeMap,
													clntCommToTypeMap, clientMarket, clientCcyCode, paxInfoMap, paxCount,
													suppID, ccommPkgDtlsJson, totalFareCompsGroup, totalIncentivesGroup,
													DYNAMICPKGACTION_SURCHARGES, redisSurchargeJson, applicableOnProductsJson,
													roomTypeSI, suppPaxTypeFaresJsonArr, clientCommercialPkgInfoArr, feeJson,
													redisSurchargeJson, extraPaxTypeFaresJsonArr, paxType, feeArr, f,
													redisExtrasArr);
		
										}
		
									}
								//	if (retainSuppFares) {
										if (extraFeeName.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)
												|| extraFeeCode.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)) {
											createFareRedisStructure(paxInfoMap, newExtraJson, suppPaxTypeFaresJsonArr,
													clientCommercialPkgInfoArr, extraPaxTypeFaresJsonArr,clientCcyCode,DYNAMICPKGACTION_SURCHARGES);
										}
										newExtraJson.remove("surcharges");
										if (paxType.equals("") || paxType.equals(null)) {
										JSONArray paxTypeFares = newExtraJson.getJSONObject("totalPricingInfo").getJSONArray("paxTypeFares");
										//to show paxTypeFares in extra comp
									
										JSONArray paxTypeFaresArr = createExtraCompPaxTypeFare(paxType, paxCount,
												newExtraJson,paxTypeFares,"totalPricingInfo");
										newExtraJson.getJSONObject("totalPricingInfo").put("paxTypeFares",paxTypeFaresArr);		
										
										JSONArray suppPaxTypeFares = newExtraJson.getJSONObject("supplierPricingInfo").getJSONArray("paxTypeFares");
										//to show paxTypeFares in extra comp
										JSONArray suppPaxTypeFaresArr = createExtraCompPaxTypeFare(paxType, paxCount,
												newExtraJson,suppPaxTypeFares,"supplierPricingInfo");
										
										newExtraJson.getJSONObject("supplierPricingInfo").put("paxTypeFares",suppPaxTypeFaresArr);
										
										JSONArray ccpaxTypeFaresArr = getCCExtraPaxTypeFares(paxType, paxCount,
												newExtraJson, redisSurchargeJson);
										newExtraJson.put("clientCommercialInfo", ccpaxTypeFaresArr);
																		}
								}
							}
							
							if(newExtraJson.has("remFee")) {
								for (int i = 0; i < redisRemFeeArr.length(); i++) {
									JSONObject redisRemFeeJson = redisRemFeeArr.getJSONObject(i);
									String extraFeeName = redisRemFeeJson.getString("feeName");
									String extraFeeCode = redisRemFeeJson.getString("feeCode");
									
									for (int f = 0; f < feeArr.length(); f++) {
										JSONObject feeJson = feeArr.getJSONObject(f);
										JSONObject applicableOnProductsJson = new JSONObject();
										String feeCode = feeJson.getString("feeCode");
										String feeName = feeJson.getString("feeName");
										
										paxType = feeJson.getString(JSON_PROP_TYPE);
										if (!feeName.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)
												&& !feeCode.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)) {
										if (feeName.equalsIgnoreCase(extraFeeName)) {
											//extraJson = new JSONObject();
											String rateDescriptionName = DYNAMICPKGACTION_EXTRAS;
											//paxCount = getPaxCountV2(componentWisePaxTypeQty, DYNAMICPKGACTION_EXTRAS,
											//		rateDescriptionName, paxCount, paxType);
											paxCount = new BigDecimal(0);
											//if (paxType.equals("") || paxType.equals(null)) {
												for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
													paxCount = paxCount.add(entry.getValue());
												}
											//}
										/*	if (redisExtraJson.getString("maxChargeUnitApplies")
													.equalsIgnoreCase(paxCount.toString())) {
												paxCount = new BigDecimal(1);
											}*/
											
											newExtraJson.put("feeCode", feeCode);
											newExtraJson.put("feeName", feeName);
											newExtraJson.put("text", feeJson.getString("text"));
											newExtraJson.put("type", feeJson.getString("type"));
											if(retainSuppFares) {
											JSONArray guestCountArr= new JSONArray();
											JSONArray resGuestArr = dynamicPackageJson.getJSONArray("resGuests");
											for (int r=0; r<resGuestArr.length();r++) {
												JSONObject resGuestJson = new JSONObject();
												String resGuestRPH = resGuestArr.getJSONObject(r).getString("resGuestRPH");
												resGuestJson.put("resGuestRPH", resGuestRPH);
												guestCountArr.put(resGuestJson);
											}
											newExtraJson.put("guestCount", guestCountArr);
											}
											applicableOnProductsJson = getClientCommercialsApplicableOnForPassengerType(
													ccommPkgDtlsJson, paxType, "", feeName, feeCode);
											getApplicableOnProductsPriceV2(retainSuppFares, usrCtx, scommToTypeMap, clntCommToTypeMap,
													clientMarket, clientCcyCode, paxInfoMap, paxCount, suppID, ccommPkgDtlsJson,
													totalFareCompsGroup, totalIncentivesGroup,"remFee", redisRemFeeJson,
													applicableOnProductsJson, roomTypeSI,suppPaxTypeFaresJsonArr, clientCommercialPkgInfoArr,
													feeJson, redisRemFeeJson,extraPaxTypeFaresJsonArr, paxType, feeArr, f, redisExtrasArr);
				
											
									}
										}
									}
								//if (retainSuppFares) {
									if (!extraFeeName.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)
											&& !extraFeeCode.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)) {
										createFareRedisStructure(paxInfoMap, newExtraJson, suppPaxTypeFaresJsonArr,
												clientCommercialPkgInfoArr, extraPaxTypeFaresJsonArr,clientCcyCode,DYNAMICPKGACTION_EXTRAS);
										newExtraJson.remove("remFee");
										if (paxType.equals("") || paxType.equals(null)) {
										JSONArray paxTypeFares = newExtraJson.getJSONObject("totalPricingInfo").getJSONArray("paxTypeFares");
										//to show paxTypeFares in extra comp
										JSONArray paxTypeFaresArr = createExtraCompPaxTypeFare(paxType, paxCount,
												newExtraJson,paxTypeFares,"totalPricingInfo");
										newExtraJson.getJSONObject("totalPricingInfo").put("paxTypeFares",paxTypeFaresArr);		
										
										JSONArray suppPaxTypeFares = newExtraJson.getJSONObject("supplierPricingInfo").getJSONArray("paxTypeFares");
										//to show paxTypeFares in extra comp
										JSONArray suppPaxTypeFaresArr = createExtraCompPaxTypeFare(paxType, paxCount,
												newExtraJson,suppPaxTypeFares,"supplierPricingInfo");
										
										newExtraJson.getJSONObject("supplierPricingInfo").put("paxTypeFares",suppPaxTypeFaresArr);
										
										JSONArray ccpaxTypeFaresArr = getCCExtraPaxTypeFares(paxType, paxCount,
												newExtraJson, redisRemFeeJson);
										newExtraJson.put("clientCommercialInfo", ccpaxTypeFaresArr);
									}}
								
								}}
		
						}
					}
						
						
					
		               
					dynamicPackageJson.put("componentForRedis", redisComponentJson);
					//setting the total Package Fare
					JSONObject globalInfo = dynamicPackageJson.getJSONObject(JSON_PROP_GLOBALINFO);
					JSONObject totalPkgJson = new JSONObject();
					
					JSONObject componentForRedis = dynamicPackageJson.getJSONObject("componentForRedis");
					Map<String, JSONObject> suppCommTotalsMap = new HashMap<String, JSONObject>();
					Map<String, JSONObject> clientCommTotalsMap = new HashMap<String, JSONObject>();
					
					JSONObject clientEntityTotalCommercials=null;
					JSONArray totalClientArr= new JSONArray();
					
					if (componentForRedis.has(JSON_PROP_HOTEL_COMPONENT)){
						
						JSONObject compJson = componentForRedis.optJSONObject(JSON_PROP_HOTEL_COMPONENT);
						calculateFinalPrice(compJson,totalPkgJson,suppCommTotalsMap,clientCommTotalsMap,clientEntityTotalCommercials,totalClientArr);
					}
					else if (componentForRedis.has(JSON_PROP_CRUISE_COMPONENT)){
						JSONObject compJson = componentForRedis.getJSONObject(JSON_PROP_CRUISE_COMPONENT);
						calculateFinalPrice(compJson,totalPkgJson,suppCommTotalsMap,clientCommTotalsMap,clientEntityTotalCommercials,totalClientArr);
					}
					if (componentForRedis.has(JSON_PROP_PRENIGHT)) {
						JSONObject compJson = componentForRedis.getJSONObject(JSON_PROP_PRENIGHT);
						calculateFinalPrice(compJson,totalPkgJson,suppCommTotalsMap,clientCommTotalsMap,clientEntityTotalCommercials,totalClientArr);
					}
					if (componentForRedis.has(JSON_PROP_POSTNIGHT)){
						JSONObject compJson = componentForRedis.getJSONObject(JSON_PROP_POSTNIGHT);
						calculateFinalPrice(compJson,totalPkgJson,suppCommTotalsMap,clientCommTotalsMap,clientEntityTotalCommercials,totalClientArr);
					}
					if (componentForRedis.has(JSON_PROP_TRANSFER_COMPONENT)) {
						JSONArray compJsonArr = componentForRedis.getJSONArray(JSON_PROP_TRANSFER_COMPONENT);
						for(int c=0;c<compJsonArr.length();c++) {
							JSONObject compJson = compJsonArr.getJSONObject(c);
						JSONArray groundService = compJson.getJSONArray(JSON_PROP_GROUNDSERVICE);
						 
						for(int g=0;g<groundService.length();g++) {
							JSONObject groundServiceJson = groundService.getJSONObject(g);
							calculateFinalPrice(groundServiceJson,totalPkgJson,suppCommTotalsMap,clientCommTotalsMap,clientEntityTotalCommercials,totalClientArr);
						}}
					}
					if (componentForRedis.has(JSON_PROP_INSURANCE_COMPONENT)) {
						JSONArray compJsonArr = componentForRedis.getJSONArray(JSON_PROP_INSURANCE_COMPONENT);
						for(int c=0;c<compJsonArr.length();c++) {
							JSONObject compJson = compJsonArr.getJSONObject(c);
							calculateFinalPrice(compJson,totalPkgJson,suppCommTotalsMap,clientCommTotalsMap,clientEntityTotalCommercials,totalClientArr);
						}
					}
					if (componentForRedis.has("extraComponent")) {
						JSONArray compJsonArr = componentForRedis.getJSONArray("extraComponent");
						
						for(int c=0;c<compJsonArr.length();c++) {
							JSONObject compJson = compJsonArr.getJSONObject(c);
							calculateFinalPrice(compJson,totalPkgJson,suppCommTotalsMap,clientCommTotalsMap,clientEntityTotalCommercials,totalClientArr);
						}
					}
					//Below code for adding fees to final package price apart from extras and surcharge
					//Since we consider all fees array in extraComponent now below code is commented -31 May 2018
					/*for (Entry<String, JSONObject> entry : pkgTotalFareMap.entrySet()) {
						JSONObject compJson = new JSONObject();
						
						compJson.put("componentTotalFare",entry.getValue());
					    JSONObject suppPricingInfo = new JSONObject();
					    suppPricingInfo.put(JSON_PROP_TOTALFARE, entry.getValue());
						JSONArray supplierCommercialTotal = new JSONArray();
						suppPricingInfo.put(JSON_PROP_SUPPCOMMTOTALS,supplierCommercialTotal);
						compJson.put(JSON_PROP_SUPPLIERPRICINGINFO, suppPricingInfo);
						calculateFinalPrice(compJson,totalPkgJson,suppCommTotalsMap,clientCommTotalsMap,clientEntityTotalCommercials,totalClientArr);
					}*/
					
					if ( clientType == ClientType.B2B) {
						totalPkgJson.put(JSON_PROP_INCENTIVES, totalIncentivesGroup.toJSON());
		    		}
					
					globalInfo.put(JSON_PROP_TOTAL,totalPkgJson);
					JSONObject totalFareJson = (JSONObject) totalFareCompsGroup.toJSON();
					if(totalFareJson.has(JSON_PROP_RECEIVABLES)) {
					JSONObject receivablesJson = totalFareJson.getJSONObject(JSON_PROP_RECEIVABLES);
					globalInfo.getJSONObject("total").getJSONObject("totalPackageFare").put(JSON_PROP_RECEIVABLES, receivablesJson);
					
					}
					
				}
							
					
					}

	private static JSONArray getCCExtraPaxTypeFares(String paxType, BigDecimal paxCount, JSONObject newExtraJson,
			JSONObject redisExtraJson) {
		JSONArray clientCommercialInfoArr = newExtraJson.getJSONArray("clientCommercialInfo");
		Map<String,JSONObject> compMap = new HashMap<String,JSONObject>();
		for (int c=0; c < clientCommercialInfoArr.length();c++) {
			String type = clientCommercialInfoArr.getJSONObject(c).getString("type");
			if (type.equals("")){
				JSONArray clientEntityCommercialsArr = clientCommercialInfoArr.getJSONObject(c).getJSONArray("clientEntityCommercials");
				
				for (int d=0; d < clientEntityCommercialsArr.length();d++) {
					JSONArray clientCommercialsArr = clientEntityCommercialsArr.getJSONObject(d).getJSONArray("clientCommercials");
					
					for (int f=0; f < clientCommercialsArr.length();f++) {
						//Changes as per confirmed with supplier paxTypeFares are per person and doesnt depend on maxChargeUnitApplies 
						/*if (redisExtraJson.getString("maxChargeUnitApplies").equalsIgnoreCase(paxCount.toString())) {
					        BigDecimal commercialAmount =clientCommercialsArr.getJSONObject(f).getBigDecimal("commercialAmount").divide(paxCount);
					        clientCommercialsArr.getJSONObject(f).put("commercialAmount", commercialAmount);
					        compMap.put("clientCommercials"+c, clientCommercialInfoArr.getJSONObject(c));
						}
						else {*/
							 compMap.put("clientCommercials"+c, clientCommercialInfoArr.getJSONObject(c));
						//	}
					
					
					}
				}
			}
		}
		
		newExtraJson.remove("clientCommercialInfo");
		JSONArray ccpaxTypeFaresArr = new JSONArray();
		if (paxType.equals("") || paxType.equals(null)) {
			for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
				
				BigDecimal count = entry.getValue();
				BigDecimal zero = new BigDecimal(0);
				int value = count.compareTo(zero);
				if (value==1){
					
					for (Entry<String, JSONObject> compentry : compMap.entrySet()) {
						 
					    JSONObject paxTypeJson = new JSONObject(compentry.getValue().toString());
						paxTypeJson.put("type", entry.getKey());
						ccpaxTypeFaresArr.put(paxTypeJson);
					}
					}
				}	

		}
		return ccpaxTypeFaresArr;
	}

	private static JSONArray createExtraCompPaxTypeFare(String paxType, BigDecimal paxCount, JSONObject newExtraJson, JSONArray paxTypeFares, String pricingInfo) {
		Map<String,JSONObject> compMap = new HashMap<String,JSONObject>();
		
		
					for(int p=0;p< paxTypeFares.length();p++) {
						JSONObject paxTypeFaresJson = paxTypeFares.getJSONObject(p);
						String type = paxTypeFaresJson.getString(JSON_PROP_TYPE);
						//String maxChargeUnitApplies = paxTypeFaresJson.getString("maxChargeUnitApplies");
						if(type.equals("")){
						
						     compMap.put("extra"+p, paxTypeFaresJson);
													 
							
						}
						
					}
					newExtraJson.getJSONObject(pricingInfo).remove("paxTypeFares");
					JSONArray paxTypeFaresArr = new JSONArray();
					if (paxType.equals("") || paxType.equals(null)) {
						for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
							
							BigDecimal count = entry.getValue();
							BigDecimal zero = new BigDecimal(0);
							int value = count.compareTo(zero);
							if (value==1){
								
								for (Entry<String, JSONObject> compentry : compMap.entrySet()) {
									 
								    JSONObject paxTypeJson = new JSONObject(compentry.getValue().toString());
									paxTypeJson.put("type", entry.getKey());
									paxTypeFaresArr.put(paxTypeJson);
								}
								
								
							}
							}	
			
		}
		return paxTypeFaresArr;
	}

	private static void calculateFinalPrice(JSONObject compJson, JSONObject totalPkgJson, Map<String, JSONObject> suppCommTotalsMap, Map<String, JSONObject> clientCommTotalsMap, JSONObject clientEntityTotalCommercials, JSONArray totalClientArr) {
		
		JSONObject totalFareJson =new JSONObject();
			//For Component Total Fare
		if(compJson.has(JSON_PROP_TOTALPRICINGINFO)) {
			totalFareJson = compJson.getJSONObject(JSON_PROP_TOTALPRICINGINFO).getJSONObject(JSON_PROP_TOTALFARE);
		}else {
			totalFareJson =compJson.getJSONObject("componentTotalFare");
		}
			componentBaseFare = componentBaseFare.add(totalFareJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX));
			componentTotalFare = componentTotalFare.add(totalFareJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX));
			String clientCcyCode = totalFareJson.getString(JSON_PROP_CCYCODE);
			
			if(totalFareJson.has(JSON_PROP_TAXES)) {
			JSONArray paxTypeTaxJsonArr = totalFareJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
			
			for (int l=0; l < paxTypeTaxJsonArr.length(); l++) {
				JSONObject paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
			    clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
				String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
	
				pkgtotalFareComGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXDESCRIPTION), clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
				}
			}
			JSONObject totalPriceJson = new JSONObject();
		    totalPriceJson = (JSONObject) pkgtotalFareComGroup.toJSON();
		    totalPriceJson.put(JSON_PROP_AMOUNTAFTERTAX, componentTotalFare);
		    totalPriceJson.put(JSON_PROP_AMOUNTBEFORETAX, componentBaseFare);
		    totalPriceJson.remove("amount");
		    totalPkgJson.put("totalPackageFare", totalPriceJson);
		   // pkgTotalFareMap.put("componentTotalFare", totalPriceJson);
		    
		   //For Supplier Total Fare
		    JSONObject suppTotalFareJson = compJson.getJSONObject(JSON_PROP_SUPPLIERPRICINGINFO).getJSONObject(JSON_PROP_TOTALFARE);
		    supplierCompBaseFare = supplierCompBaseFare.add(suppTotalFareJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX));
			supplierCompTotalFare = supplierCompTotalFare.add(suppTotalFareJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX));
			clientCcyCode = totalFareJson.getString(JSON_PROP_CCYCODE);
	
			if(totalFareJson.has(JSON_PROP_TAXES)) {
			JSONArray suppPaxTypeTaxJsonArr = totalFareJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
			
			for (int l=0; l < suppPaxTypeTaxJsonArr.length(); l++) {
				JSONObject paxTypeTaxJson = suppPaxTypeTaxJsonArr.getJSONObject(l);
			    clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
				String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
	
				pkgsuppFareComGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXDESCRIPTION), clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
				}
			}
			JSONObject totalSuppJson = new JSONObject();
			totalSuppJson = (JSONObject) pkgsuppFareComGroup.toJSON();
			totalSuppJson.put(JSON_PROP_AMOUNTAFTERTAX, supplierCompTotalFare);
			totalSuppJson.put(JSON_PROP_AMOUNTBEFORETAX, supplierCompBaseFare);
			totalSuppJson.remove("amount");
		    //pkgTotalFareMap.put("supplierTotalFare", totalSuppJson);
		    JSONObject supplierPricingInfo = new JSONObject();
		    supplierPricingInfo.put(JSON_PROP_TOTALFARE, totalSuppJson);
		   
			
		    //For supplierCommercialsTotal Array
		    
		    JSONArray suppCommJsonArr = compJson.getJSONObject(JSON_PROP_SUPPLIERPRICINGINFO).optJSONArray(JSON_PROP_SUPPCOMMTOTALS);
		    
			// If no supplier commercials have been defined in BRMS, the JSONArray for suppCommJsonArr will be null.
			// In this case, log a message and proceed with other calculations.
			if (suppCommJsonArr == null) {
				logger.warn("No supplier commercials found");
			}else {
				for (int j=0; j < suppCommJsonArr.length(); j++) {
					JSONObject suppCommJson = suppCommJsonArr.getJSONObject(j);
					String suppCommName = suppCommJson.getString(JSON_PROP_COMMNAME);
					JSONObject suppCommTotalsJson = null;
					if (suppCommTotalsMap.containsKey(suppCommName)) {
						suppCommTotalsJson = suppCommTotalsMap.get(suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT)));
					}
					else {
						suppCommTotalsJson = new JSONObject();
						suppCommTotalsJson.put(JSON_PROP_COMMNAME, suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMTYPE, suppCommJson.getString(JSON_PROP_COMMTYPE));
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
						suppCommTotalsJson.put(JSON_PROP_COMMCCY,suppCommJson.get(JSON_PROP_COMMCCY).toString());
						suppCommTotalsMap.put(suppCommName, suppCommTotalsJson);
						
					}
				}
			}
			
			JSONArray suppCommTotalsJsonArr = new JSONArray();
			Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet().iterator();
			while (suppCommTotalsIter.hasNext()) {
				suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
			}
			supplierPricingInfo.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr); 
			totalPkgJson.put(JSON_PROP_SUPPLIERPRICINGINFO, supplierPricingInfo);
			
			
			
			 //For clientEntityTotalCommercials Array
					
					JSONArray clientEntityCommJsonArr=compJson.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
					
					if (clientEntityCommJsonArr == null) {
						logger.warn("No client commercials found");
					}
					else  {
					
				    JSONObject clientCommTotalsJson = null;
					JSONArray clientTotalEntityArray=new JSONArray();
					JSONObject clientCommEntJson= new JSONObject();
					
					for(int m=0;m<clientEntityCommJsonArr.length();m++) {
						
						JSONObject clientCommEntityJson=clientEntityCommJsonArr.getJSONObject(m);
						JSONArray clientCommTotalsArr=clientCommEntityJson.getJSONArray("clientCommercials");
						for(int n=0;n<clientCommTotalsArr.length();n++) {
							
							JSONObject clientCommJson=clientCommTotalsArr.getJSONObject(n);
							String clientCommName = clientCommJson.getString(JSON_PROP_COMMNAME);
							
						if (clientCommTotalsMap.containsKey(clientCommName)) {
							clientEntityTotalCommercials= clientCommTotalsMap.get(clientCommName);
							
							clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT).add(clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT)));
						}
						else {
							clientEntityTotalCommercials= new JSONObject();
							clientCommTotalsJson = new JSONObject();
							clientCommTotalsJson.put(JSON_PROP_COMMNAME, clientCommName);
							clientCommTotalsJson.put(JSON_PROP_COMMTYPE, clientCommJson.getString(JSON_PROP_COMMTYPE));
							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
							clientCommTotalsJson.put(JSON_PROP_COMMCCY,clientCommJson.get(JSON_PROP_COMMCCY).toString());
							clientTotalEntityArray.put(clientCommTotalsJson);
						
						
							clientCommTotalsMap.put(clientCommName, clientCommTotalsJson);
						}
					}
					
					if((clientTotalEntityArray!=null) && (clientTotalEntityArray.length()>0) ) {
						clientCommEntJson.put("clientCommercials", clientTotalEntityArray);
						clientCommEntJson.put(JSON_PROP_CLIENTID, clientCommTotalsJson.optString(JSON_PROP_CLIENTID,""));
						clientCommEntJson.put(JSON_PROP_PARENTCLIENTID, clientCommTotalsJson.optString(JSON_PROP_PARENTCLIENTID,""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYTYPE, clientCommTotalsJson.optString(JSON_PROP_COMMENTITYTYPE,""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYID, clientCommTotalsJson.optString(JSON_PROP_COMMENTITYID,""));
						
						totalClientArr.put(clientCommEntJson);
					}
					totalPkgJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, totalClientArr);
			
					
		}}
		
		
	}

	private static void createFareRedisStructureCompLevel(JSONObject redisHotelCompJson, String dynamicPkgAction, String clientCcyCode) {
		BigDecimal componentTotalFare = new BigDecimal(0);
		BigDecimal componentBaseFare = new BigDecimal(0);
		BigDecimal supplierCompTotalFare = new BigDecimal(0);
		BigDecimal supplierCompBaseFare = new BigDecimal(0);
		PriceComponentsGroup totalFareComGroup = new PriceComponentsGroup(JSON_PROP_TOTAL, clientCcyCode, new BigDecimal(0), true);
		PriceComponentsGroup suppFareComGroup = new PriceComponentsGroup(JSON_PROP_TOTAL, clientCcyCode, new BigDecimal(0), true);
		Map<String, JSONObject> suppCommTotalsMap = new HashMap<String, JSONObject>();
		Map<String, JSONObject> clientCommTotalsMap = new HashMap<String, JSONObject>();
		JSONArray redisRoomStayArray =new JSONArray();
		JSONArray categoryOptionsArr = new JSONArray();
		JSONObject clientEntityTotalCommercials=null;
		JSONArray totalClientArr= new JSONArray();
        //cruise prent/postnt comp change---commented lines is old code
        //if(dynamicPkgAction.toLowerCase().contains("acco")) {
		
		if(dynamicPkgAction.toLowerCase().contains("acco")||(dynamicPkgAction.toLowerCase().contains("prenight")||(dynamicPkgAction.toLowerCase().contains("postnight")))) {
		   redisRoomStayArray = redisHotelCompJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
		   
		   
		   for(int i=0; i<redisRoomStayArray.length();i++) {
			
			JSONObject redisRoomStayJson = redisRoomStayArray.getJSONObject(i);
			//For Component Total Fare
			JSONObject totalFareJson = redisRoomStayJson.getJSONObject(JSON_PROP_TOTALPRICINGINFO).getJSONObject(JSON_PROP_TOTALFARE);
			componentBaseFare = componentBaseFare.add(totalFareJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX));
			componentTotalFare = componentTotalFare.add(totalFareJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX));
			clientCcyCode = totalFareJson.getString(JSON_PROP_CCYCODE);
			
			if  (totalFareJson.has("taxes")) {
			JSONArray paxTypeTaxJsonArr = totalFareJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
			
			for (int l=0; l < paxTypeTaxJsonArr.length(); l++) {
				JSONObject paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
			    clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
				String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
	
				totalFareComGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXDESCRIPTION), clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
				}
                         }
			if(totalFareJson.has(JSON_PROP_RECEIVABLES)) {
			JSONArray paxTypeReceivablesJsonArr = totalFareJson.optJSONObject(JSON_PROP_RECEIVABLES).optJSONArray(JSON_PROP_RECEIVABLE);
			for (int l=0; l < paxTypeReceivablesJsonArr.length(); l++) {
				JSONObject paxTypeReceivableJson = paxTypeReceivablesJsonArr.getJSONObject(l);
			    clientCcyCode = paxTypeReceivableJson.getString(JSON_PROP_CCYCODE);
				String code = paxTypeReceivableJson.getString(JSON_PROP_CODE);
	
				totalFareComGroup.add(mRcvsPriceCompQualifier.concat(code).concat("@").concat(JSON_PROP_CODE), clientCcyCode, paxTypeReceivableJson.getBigDecimal(JSON_PROP_AMOUNT));
				}}
			
			
			JSONObject totalPriceJson = new JSONObject();
		    totalPriceJson = (JSONObject) totalFareComGroup.toJSON();
		    totalPriceJson.put(JSON_PROP_AMOUNTAFTERTAX, componentTotalFare);
		    totalPriceJson.put(JSON_PROP_AMOUNTBEFORETAX, componentBaseFare);
		    totalPriceJson.remove("amount");
		    redisHotelCompJson.put("componentTotalFare", totalPriceJson);
		   // pkgTotalFareMap.put("componentTotalFare", totalPriceJson);
		    
		   //For Supplier Total Fare
		    JSONObject suppTotalFareJson = redisRoomStayJson.getJSONObject(JSON_PROP_SUPPLIERPRICINGINFO).getJSONObject(JSON_PROP_TOTALFARE);
		    supplierCompBaseFare = supplierCompBaseFare.add(suppTotalFareJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX));
			supplierCompTotalFare = supplierCompTotalFare.add(suppTotalFareJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX));
			clientCcyCode = totalFareJson.getString(JSON_PROP_CCYCODE);
			
			if  (totalFareJson.has("taxes")) {
			JSONArray suppPaxTypeTaxJsonArr = totalFareJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
			
			for (int l=0; l < suppPaxTypeTaxJsonArr.length(); l++) {
				JSONObject paxTypeTaxJson = suppPaxTypeTaxJsonArr.getJSONObject(l);
			    clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
				String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
	
				suppFareComGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXDESCRIPTION), clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
				}
			}
			JSONObject totalSuppJson = new JSONObject();
			totalSuppJson = (JSONObject) suppFareComGroup.toJSON();
			totalSuppJson.put(JSON_PROP_AMOUNTAFTERTAX, supplierCompTotalFare);
			totalSuppJson.put(JSON_PROP_AMOUNTBEFORETAX, supplierCompBaseFare);
			totalSuppJson.remove("amount");
		    //pkgTotalFareMap.put("supplierTotalFare", totalSuppJson);
		    JSONObject supplierPricingInfo = new JSONObject();
		    supplierPricingInfo.put(JSON_PROP_TOTALFARE, totalSuppJson);
		   
			
		    //For supplierCommercialsTotal Array
		    
		    JSONArray suppCommJsonArr = redisRoomStayJson.getJSONObject(JSON_PROP_SUPPLIERPRICINGINFO).getJSONArray(JSON_PROP_SUPPCOMMTOTALS);
		    
			// If no supplier commercials have been defined in BRMS, the JSONArray for suppCommJsonArr will be null.
			// In this case, log a message and proceed with other calculations.
			if (suppCommJsonArr == null) {
				logger.warn("No supplier commercials found");
			}else {
				for (int j=0; j < suppCommJsonArr.length(); j++) {
					JSONObject suppCommJson = suppCommJsonArr.getJSONObject(j);
					String suppCommName = suppCommJson.getString(JSON_PROP_COMMNAME);
					JSONObject suppCommTotalsJson = null;
					if (suppCommTotalsMap.containsKey(suppCommName)) {
						suppCommTotalsJson = suppCommTotalsMap.get(suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT)));
					}
					else {
						suppCommTotalsJson = new JSONObject();
						suppCommTotalsJson.put(JSON_PROP_COMMNAME, suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMTYPE, suppCommJson.getString(JSON_PROP_COMMTYPE));
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
						suppCommTotalsJson.put(JSON_PROP_COMMCCY,suppCommJson.get(JSON_PROP_COMMCCY).toString());
						suppCommTotalsMap.put(suppCommName, suppCommTotalsJson);
						
					}
				}
			}
			
			JSONArray suppCommTotalsJsonArr = new JSONArray();
			Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet().iterator();
			while (suppCommTotalsIter.hasNext()) {
				suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
			}
			supplierPricingInfo.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr); 
			redisHotelCompJson.put(JSON_PROP_SUPPLIERPRICINGINFO, supplierPricingInfo);
			
			//JSONArray clientCommJsonArr= redisRoomStayJson.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			/*if (clientCommJsonArr == null) {
				logger.warn("No client commercials found");
			}
			else  {*/
				//JSONObject clientCommJson =clientEntityCommJson.getJSONObject("clientCommercial");
					//clientCommEntJson
			
			 //For clientEntityTotalCommercials Array
					
					JSONArray clientEntityCommJsonArr=redisRoomStayJson.getJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
					JSONObject clientCommTotalsJson = null;
					JSONArray clientTotalEntityArray=new JSONArray();
					JSONObject clientCommEntJson= new JSONObject();
					
					for(int m=0;m<clientEntityCommJsonArr.length();m++) {
						
						JSONObject clientCommEntityJson=clientEntityCommJsonArr.getJSONObject(m);
						JSONArray clientCommTotalsArr=clientCommEntityJson.getJSONArray("clientCommercials");
						for(int n=0;n<clientCommTotalsArr.length();n++) {
							
							JSONObject clientCommJson=clientCommTotalsArr.getJSONObject(n);
							String clientCommName = clientCommJson.getString(JSON_PROP_COMMNAME);
							
						if (clientCommTotalsMap.containsKey(clientCommName)) {
							clientEntityTotalCommercials= clientCommTotalsMap.get(clientCommName);
							
							clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT).add(clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT)));
						}
						else {
							clientEntityTotalCommercials= new JSONObject();
							clientCommTotalsJson = new JSONObject();
							clientCommTotalsJson.put(JSON_PROP_COMMNAME, clientCommName);
							clientCommTotalsJson.put(JSON_PROP_COMMTYPE, clientCommJson.getString(JSON_PROP_COMMTYPE));
							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
							clientCommTotalsJson.put(JSON_PROP_COMMCCY,clientCommJson.get(JSON_PROP_COMMCCY).toString());

							clientTotalEntityArray.put(clientCommTotalsJson);
						
						
							clientCommTotalsMap.put(clientCommName, clientCommTotalsJson);
						}
					}
					
					if((clientTotalEntityArray!=null) && (clientTotalEntityArray.length()>0) ) {
						clientCommEntJson.put("clientCommercials", clientTotalEntityArray);
						clientCommEntJson.put(JSON_PROP_CLIENTID, clientCommTotalsJson.optString(JSON_PROP_CLIENTID,""));
						clientCommEntJson.put(JSON_PROP_PARENTCLIENTID, clientCommTotalsJson.optString(JSON_PROP_PARENTCLIENTID,""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYTYPE, clientCommTotalsJson.optString(JSON_PROP_COMMENTITYTYPE,""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYID, clientCommTotalsJson.optString(JSON_PROP_COMMENTITYID,""));
						
						totalClientArr.put(clientCommEntJson);
					}
			redisHotelCompJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, totalClientArr);
			
			
		}
        }}
       // if(dynamicPkgAction.toLowerCase().contains("cruise")) {
		  if(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_CRUISE_TOUR)) {
			  categoryOptionsArr = redisHotelCompJson.getJSONArray("categoryOptions");
        	for( int i= 0 ;i< categoryOptionsArr.length(); i++) {
        		redisRoomStayArray=categoryOptionsArr.getJSONObject(i).getJSONArray("categoryOption");
        		for(int r=0; r<redisRoomStayArray.length();r++) {
        			
        			JSONObject redisRoomStayJson = redisRoomStayArray.getJSONObject(r);
        			//For Component Total Fare
        			JSONObject totalFareJson = redisRoomStayJson.getJSONObject(JSON_PROP_TOTALPRICINGINFO).getJSONObject(JSON_PROP_TOTALFARE);
        			componentBaseFare = componentBaseFare.add(totalFareJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX));
        			componentTotalFare = componentTotalFare.add(totalFareJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX));
        			clientCcyCode = totalFareJson.getString(JSON_PROP_CCYCODE);
        			
        			
        			JSONArray paxTypeTaxJsonArr = totalFareJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
        			
        			for (int l=0; l < paxTypeTaxJsonArr.length(); l++) {
        				JSONObject paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
        			    clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
        				String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
        	
        				totalFareComGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXDESCRIPTION), clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
        				}
        			
        			if(totalFareJson.has(JSON_PROP_RECEIVABLES)) {
        				JSONArray paxTypeReceivablesJsonArr = totalFareJson.optJSONObject(JSON_PROP_RECEIVABLES).optJSONArray(JSON_PROP_RECEIVABLE);
        				for (int l=0; l < paxTypeReceivablesJsonArr.length(); l++) {
        					JSONObject paxTypeReceivableJson = paxTypeReceivablesJsonArr.getJSONObject(l);
        				    clientCcyCode = paxTypeReceivableJson.getString(JSON_PROP_CCYCODE);
        					String code = paxTypeReceivableJson.getString(JSON_PROP_CODE);
        		
        					totalFareComGroup.add(mRcvsPriceCompQualifier.concat(code).concat("@").concat(JSON_PROP_CODE), clientCcyCode, paxTypeReceivableJson.getBigDecimal(JSON_PROP_AMOUNT));
        					}}
        			
        			JSONObject totalPriceJson = new JSONObject();
        		    totalPriceJson = (JSONObject) totalFareComGroup.toJSON();
        		    totalPriceJson.put(JSON_PROP_AMOUNTAFTERTAX, componentTotalFare);
        		    totalPriceJson.put(JSON_PROP_AMOUNTBEFORETAX, componentBaseFare);
        		    totalPriceJson.remove("amount");
        		    redisHotelCompJson.put("componentTotalFare", totalPriceJson);
        		   // pkgTotalFareMap.put("componentTotalFare", totalPriceJson);
        		    
        		   //For Supplier Total Fare
        		    JSONObject suppTotalFareJson = redisRoomStayJson.getJSONObject(JSON_PROP_SUPPLIERPRICINGINFO).getJSONObject(JSON_PROP_TOTALFARE);
        		    supplierCompBaseFare = supplierCompBaseFare.add(suppTotalFareJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX));
        			supplierCompTotalFare = supplierCompTotalFare.add(suppTotalFareJson.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX));
        			clientCcyCode = totalFareJson.getString(JSON_PROP_CCYCODE);
        			
        			
        			JSONArray suppPaxTypeTaxJsonArr = totalFareJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
        			
        			for (int l=0; l < suppPaxTypeTaxJsonArr.length(); l++) {
        				JSONObject paxTypeTaxJson = suppPaxTypeTaxJsonArr.getJSONObject(l);
        			    clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
        				String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
        	
        				suppFareComGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXDESCRIPTION), clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
        				}
        			
        			JSONObject totalSuppJson = new JSONObject();
        			totalSuppJson = (JSONObject) suppFareComGroup.toJSON();
        			totalSuppJson.put(JSON_PROP_AMOUNTAFTERTAX, supplierCompTotalFare);
        			totalSuppJson.put(JSON_PROP_AMOUNTBEFORETAX, supplierCompBaseFare);
        			totalSuppJson.remove("amount");
        		    //pkgTotalFareMap.put("supplierTotalFare", totalSuppJson);
        		    JSONObject supplierPricingInfo = new JSONObject();
        		    supplierPricingInfo.put(JSON_PROP_TOTALFARE, totalSuppJson);
        		   
        			
        		    //For supplierCommercialsTotal Array
        		    
        		    JSONArray suppCommJsonArr = redisRoomStayJson.getJSONObject(JSON_PROP_SUPPLIERPRICINGINFO).getJSONArray(JSON_PROP_SUPPCOMMTOTALS);
        		    
        			// If no supplier commercials have been defined in BRMS, the JSONArray for suppCommJsonArr will be null.
        			// In this case, log a message and proceed with other calculations.
        			if (suppCommJsonArr == null) {
        				logger.warn("No supplier commercials found");
        			}else {
        				for (int j=0; j < suppCommJsonArr.length(); j++) {
        					JSONObject suppCommJson = suppCommJsonArr.getJSONObject(j);
        					String suppCommName = suppCommJson.getString(JSON_PROP_COMMNAME);
        					JSONObject suppCommTotalsJson = null;
        					if (suppCommTotalsMap.containsKey(suppCommName)) {
        						suppCommTotalsJson = suppCommTotalsMap.get(suppCommName);
        						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT)));
        					}
        					else {
        						suppCommTotalsJson = new JSONObject();
        						suppCommTotalsJson.put(JSON_PROP_COMMNAME, suppCommName);
        						suppCommTotalsJson.put(JSON_PROP_COMMTYPE, suppCommJson.getString(JSON_PROP_COMMTYPE));
        						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
        						suppCommTotalsJson.put(JSON_PROP_COMMCCY,suppCommJson.get(JSON_PROP_COMMCCY).toString());
        						suppCommTotalsMap.put(suppCommName, suppCommTotalsJson);
        						
        					}
        				}
        			}
        			
        			JSONArray suppCommTotalsJsonArr = new JSONArray();
        			Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet().iterator();
        			while (suppCommTotalsIter.hasNext()) {
        				suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
        			}
        			supplierPricingInfo.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr); 
        			redisHotelCompJson.put(JSON_PROP_SUPPLIERPRICINGINFO, supplierPricingInfo);
        			
        			//JSONArray clientCommJsonArr= redisRoomStayJson.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
        			/*if (clientCommJsonArr == null) {
        				logger.warn("No client commercials found");
        			}
        			else  {*/
        				//JSONObject clientCommJson =clientEntityCommJson.getJSONObject("clientCommercial");
        					//clientCommEntJson
        			
        			 //For clientEntityTotalCommercials Array
        					
        					JSONArray clientEntityCommJsonArr=redisRoomStayJson.getJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
        					JSONObject clientCommTotalsJson = null;
        					JSONArray clientTotalEntityArray=new JSONArray();
        					JSONObject clientCommEntJson= new JSONObject();
        					
        					for(int m=0;m<clientEntityCommJsonArr.length();m++) {
        						
        						JSONObject clientCommEntityJson=clientEntityCommJsonArr.getJSONObject(m);
        						JSONArray clientCommTotalsArr=clientCommEntityJson.getJSONArray("clientCommercials");
        						for(int n=0;n<clientCommTotalsArr.length();n++) {
        							
        							JSONObject clientCommJson=clientCommTotalsArr.getJSONObject(n);
        							String clientCommName = clientCommJson.getString(JSON_PROP_COMMNAME);
        							
        						if (clientCommTotalsMap.containsKey(clientCommName)) {
        							clientEntityTotalCommercials= clientCommTotalsMap.get(clientCommName);
        							
        							clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT).add(clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT)));
        						}
        						else {
        							clientEntityTotalCommercials= new JSONObject();
        							clientCommTotalsJson = new JSONObject();
        							clientCommTotalsJson.put(JSON_PROP_COMMNAME, clientCommName);
        							clientCommTotalsJson.put(JSON_PROP_COMMTYPE, clientCommJson.getString(JSON_PROP_COMMTYPE));
        							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
        							clientCommTotalsJson.put(JSON_PROP_COMMCCY,clientCommJson.get(JSON_PROP_COMMCCY).toString());
        				
        							clientTotalEntityArray.put(clientCommTotalsJson);
        						
        						
        							clientCommTotalsMap.put(clientCommName, clientCommTotalsJson);
        						}
        					}
        					
        					if((clientTotalEntityArray!=null) && (clientTotalEntityArray.length()>0) ) {
        						clientCommEntJson.put("clientCommercials", clientTotalEntityArray);
        						clientCommEntJson.put(JSON_PROP_CLIENTID, clientCommTotalsJson.optString(JSON_PROP_CLIENTID,""));
        						clientCommEntJson.put(JSON_PROP_PARENTCLIENTID, clientCommTotalsJson.optString(JSON_PROP_PARENTCLIENTID,""));
        						clientCommEntJson.put(JSON_PROP_COMMENTITYTYPE, clientCommTotalsJson.optString(JSON_PROP_COMMENTITYTYPE,""));
        						clientCommEntJson.put(JSON_PROP_COMMENTITYID, clientCommTotalsJson.optString(JSON_PROP_COMMENTITYID,""));
        						
        						totalClientArr.put(clientCommEntJson);
        					}
        			redisHotelCompJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, totalClientArr);
        			
        			
        		}}
        	}
        }
		
	
		
		
	}

	private static void getExtNightsHotelPrice(boolean retainSuppFares, UserContext usrCtx,
			Map<String, String> scommToTypeMap, Map<String, String> clntCommToTypeMap, String clientMarket,
			String clientCcyCode, Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty, String suppID,
			JSONObject ccommPkgDtlsJson, PriceComponentsGroup totalFareCompsGroup,
			PriceComponentsGroup totalIncentivesGroup, JSONObject extNightJson, JSONObject redisExtNightJson,
			String dynamicPkgAction, String dynamicPkgHotelStr, String extNightStr) {
		String paxType ="";
		
		BigDecimal paxCount = new BigDecimal(0);
		
		JSONObject applicableOnProductsJson = new JSONObject();
		JSONArray roomStayArr = extNightJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
		JSONArray redisRoomStayArr = redisExtNightJson.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
		for (int i = 0; i < roomStayArr.length(); i++) {

			JSONObject roomStayJson = roomStayArr.getJSONObject(i);
			JSONObject redisNtRoomStayJson = redisRoomStayArr.getJSONObject(i);
			JSONArray roomrateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);
			JSONArray redisRoomRateArr = redisNtRoomStayJson.getJSONArray(JSON_PROP_ROOMRATE);
			JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
			JSONArray roomPaxTypeFaresJsonArr = new JSONArray();
			JSONArray clientCommercialPkgInfoArr = new JSONArray();
			paxInfoMap = new LinkedHashMap<String, BigDecimal>();
			
			 JSONArray applicableOnProductsArr =ccommPkgDtlsJson.getJSONArray("applicableOnProducts");
			 for (int a = 0; a < applicableOnProductsArr.length(); a++) {
			 applicableOnProductsJson = applicableOnProductsArr.getJSONObject(a);
			 
			String roomTypeSI = roomStayJson.get(JSON_PROP_ROOMTYPE).toString();
			for (int k = 0; k < roomrateArr.length(); k++) {
				JSONObject roomRateJson = roomrateArr.getJSONObject(k);
				JSONObject redisNtRoomRateJson = redisRoomRateArr.getJSONObject(k);
				String roomCategoryNights = roomRateJson.optString(JSON_PROP_RATEPLANCATEGORY);
				JSONObject totalJson = roomRateJson.getJSONObject(JSON_PROP_TOTAL);
				paxType = totalJson.getString(JSON_PROP_TYPE);
				
				
				// to get the paxCount
				if (dynamicPkgAction.equalsIgnoreCase(dynamicPkgHotelStr)) {

					// rateDescriptionName is the preNight+RoomType to match with the request key.
					String rateDescriptionName = (extNightStr + roomTypeSI + i).toLowerCase();
					paxCount = getPaxCountV2(componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName, paxCount,
							paxType);
					/*applicableOnProductsJson = getClientCommercialsApplicableOnForPassengerType(ccommPkgDtlsJson,
							paxType, roomTypeSI, extNightStr.toLowerCase(),dynamicPkgAction);*/
				}

				getApplicableOnProductsPriceV2(retainSuppFares, usrCtx, scommToTypeMap, clntCommToTypeMap, clientMarket,
						clientCcyCode, paxInfoMap, paxCount, suppID, ccommPkgDtlsJson,
						totalFareCompsGroup, totalIncentivesGroup, dynamicPkgAction, totalJson,
						applicableOnProductsJson, roomTypeSI, suppPaxTypeFaresJsonArr, clientCommercialPkgInfoArr,
						roomRateJson, redisNtRoomRateJson, roomPaxTypeFaresJsonArr, paxType, roomrateArr, k,
						roomrateArr);
			}}

			// Below redis structure elements at roomStaylevel
			//if (retainSuppFares) {
				createFareRedisStructure(paxInfoMap, redisNtRoomStayJson, suppPaxTypeFaresJsonArr,
						clientCommercialPkgInfoArr, roomPaxTypeFaresJsonArr,clientCcyCode,"");
				
				/*if(compPaxInfoMap.containsKey(paxType)) {
					paxQty = paxQty.add(paxCount);
					compPaxInfoMap.put(paxType, paxQty);}
				else {
					paxQty = paxCount;
					compPaxInfoMap=paxInfoMap;
				}*/
		//	}
			}
		// Below redis structure elements at componentlevel
		//if (retainSuppFares) {
		
				createFareRedisStructureCompLevel(redisExtNightJson,dynamicPkgAction,clientCcyCode);
			
		//}
	}

	private static void getExtNightCruisePrice(boolean retainSuppFares, UserContext usrCtx,
			Map<String, String> scommToTypeMap, Map<String, String> clntCommToTypeMap, String clientMarket,
			String clientCcyCode, Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty, String suppID,
			JSONObject ccommPkgDtlsJson, PriceComponentsGroup totalFareCompsGroup,
			PriceComponentsGroup totalIncentivesGroup, JSONObject extNightJson, JSONObject redisExtNightJson,
			String dynamicPkgAction, String dynamicPkgCruiseStr, String extNightStr) {
		String paxType="";
	
		BigDecimal paxCount;
		
		paxCount = new BigDecimal(0);
		
		JSONObject applicableOnProductsJson=new JSONObject();
		JSONArray categoryOptionsArray = extNightJson.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
		JSONArray redisCategoryOptionsArray = redisExtNightJson.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
		
		for (int y = 0; y < categoryOptionsArray.length(); y++) {
			JSONObject categoryOptionsJson = categoryOptionsArray.getJSONObject(y);
			JSONObject redisCategoryOptionsJson = redisCategoryOptionsArray.getJSONObject(y);
			JSONArray categoryOptionArray = categoryOptionsJson.getJSONArray(JSON_PROP_CATEGORYOPTION);
			JSONArray redisCategoryOptionArray = redisCategoryOptionsJson.getJSONArray(JSON_PROP_CATEGORYOPTION);
			JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
			JSONArray clientCommercialPkgInfoArr = new JSONArray();
			JSONArray roomPaxTypeFaresJsonArr =new JSONArray();
			
			for (int z = 0; z < categoryOptionArray.length(); z++) {
				JSONObject categoryOptionJson = categoryOptionArray.getJSONObject(z);
				JSONObject redisCategoryOptionJson = redisCategoryOptionArray.getJSONObject(z);
				String roomTypeSI = categoryOptionJson.getString(JSON_PROP_CABINTYPE);
				JSONObject totalJson = new JSONObject();
				JSONArray totalArray = categoryOptionJson.getJSONArray(JSON_PROP_TOTAL);
				JSONArray redisTotalArr = redisCategoryOptionJson.getJSONArray(JSON_PROP_TOTAL);
				paxInfoMap = new LinkedHashMap<String, BigDecimal>();
				
				for (int t = 0; t < totalArray.length(); t++) {
					totalJson = totalArray.getJSONObject(t);
					paxType = totalJson.getString(JSON_PROP_TYPE);
					if (dynamicPkgAction.equalsIgnoreCase(dynamicPkgCruiseStr)) {

						//for eg. rateDescriptionName is the preNight+RoomType to match with the request key.
						String rateDescriptionName = (extNightStr + roomTypeSI+z).toLowerCase();
						paxCount = getPaxCountV2(componentWisePaxTypeQty, dynamicPkgAction,
								rateDescriptionName, paxCount, paxType);
						applicableOnProductsJson = getClientCommercialsApplicableOnForPassengerType(ccommPkgDtlsJson, paxType,roomTypeSI,extNightStr.toLowerCase(),dynamicPkgAction);
					}
					getApplicableOnProductsPriceV2(retainSuppFares, usrCtx, scommToTypeMap,
							clntCommToTypeMap, clientMarket, clientCcyCode, paxInfoMap, paxCount, suppID, ccommPkgDtlsJson, totalFareCompsGroup,
							totalIncentivesGroup, dynamicPkgAction,totalJson,applicableOnProductsJson,
							roomTypeSI,suppPaxTypeFaresJsonArr,clientCommercialPkgInfoArr,categoryOptionJson,redisCategoryOptionJson,roomPaxTypeFaresJsonArr,paxType,totalArray,t,redisTotalArr);
			}
				if (retainSuppFares) {
					createFareRedisStructure(paxInfoMap, redisCategoryOptionJson,suppPaxTypeFaresJsonArr,
							clientCommercialPkgInfoArr,roomPaxTypeFaresJsonArr,clientCcyCode,"");
					
				}
			
			}}
		//Below redis structure elements at complevel
		if (retainSuppFares) {
			dynamicPkgAction = redisExtNightJson.getString(JSON_PROP_DYNAMICPKGACTION);
			createFareRedisStructureCompLevel(redisExtNightJson,dynamicPkgAction,clientCcyCode);
		}
	}

	private static void createFareRedisStructure(Map<String, BigDecimal> paxInfoMap, JSONObject redisRoomStayJson,
			JSONArray suppPaxTypeFaresJsonArr, JSONArray clientCommercialPkgInfoArr,
			JSONArray roomPaxTypeFaresJsonArr, String clientCcyCode,String dynamicPkgAction) {
		JSONObject suppCompPricingInfoJson = new JSONObject();
		JSONObject suppCompTotalFareJson = new JSONObject();
		JSONObject totalCompPricingInfoJson = new JSONObject();
		JSONObject totalCompFareJson = new JSONObject();
		suppCompPricingInfoJson.put(JSON_PROP_TOTALFARE, suppCompTotalFareJson);
	
		suppCompPricingInfoJson.put(JSON_PROP_PAXTYPEFARES, suppPaxTypeFaresJsonArr);
		totalCompPricingInfoJson.put(JSON_PROP_TOTALFARE, totalCompFareJson);
		
		totalCompPricingInfoJson.put(JSON_PROP_PAXTYPEFARES, roomPaxTypeFaresJsonArr);
		
		redisRoomStayJson.put(JSON_PROP_CLIENTCOMMERCIALINFO, clientCommercialPkgInfoArr);
		redisRoomStayJson.put(JSON_PROP_SUPPLIERPRICINGINFO, suppCompPricingInfoJson);
		redisRoomStayJson.put(JSON_PROP_TOTALPRICINGINFO, totalCompPricingInfoJson);
		addSupplierTotalFare(redisRoomStayJson, paxInfoMap,clientCcyCode,dynamicPkgAction);
	}
		
		
		
	private static JSONObject getClientCommercialsPackageDetailsForRoomType(JSONObject ccommPkgDtlsJson,
			String roomTypeSI) {
		if (ccommPkgDtlsJson == null || roomTypeSI == null) {
			return null;
		}
		
		// Search this paxType in client commercials journeyDetails 
		JSONArray ccommRoomDtlsJsonArr = ccommPkgDtlsJson.getJSONArray(JSON_PROP_ROOMDETAILS);
		for (int k=0; k < ccommRoomDtlsJsonArr.length(); k++) {
			JSONObject ccommRoomDtlsJson = ccommRoomDtlsJsonArr.getJSONObject(k);
			if (roomTypeSI.equals(ccommRoomDtlsJson.getString(JSON_PROP_ROOMTYPE))) {
				return ccommRoomDtlsJson;
			}
		}
		
		return null;
	}



	private static void getApplicableOnProductsPriceV2(boolean retainSuppFares, UserContext usrCtx,
			Map<String, String> scommToTypeMap, Map<String, String> clntCommToTypeMap, String clientMarket,
			String clientCcyCode, Map<String, BigDecimal> paxInfoMap, BigDecimal paxCount,String suppID, JSONObject ccommPkgDtlsJson,
			PriceComponentsGroup totalFareCompsGroup, PriceComponentsGroup totalIncentivesGroup,
			String dynamicPkgAction, JSONObject totalJson, JSONObject applicableOnProductsJson, 
			String roomTypeSI, JSONArray suppPaxTypeFaresJsonArr, JSONArray clientCommercialPkgInfoArr, JSONObject roomRateJson, JSONObject redisRoomRateJson, JSONArray roomPaxTypeFaresJsonArr,
			 String paxType, JSONArray totalArray, int t,JSONArray redisTotalArr) {
		
					    String suppCcyCode = totalJson.getString(JSON_PROP_CCYCODE);
						BigDecimal amountAfterTaxSI = new BigDecimal(0),amountBeforeTaxSI=new BigDecimal(0) ;
						BigDecimal amountAfterTaxcc = new BigDecimal(0);
						BigDecimal amountBeforeTaxcc = new BigDecimal(0);
						String insuranceCC ="";String roomTypeCC="";
						JSONArray clientEntityCommJsonArr = null;
						int amountAfterTaxvalue = 0, amountBeforeTaxvalue = 0;
						if (applicableOnProductsJson == null) {
		    				logger.info(String.format("Passenger type %s details not found client commercial applicableOnProducts", paxType));
		    			}
		    			else {
		        			/*// Only in case of reprice when (retainSuppFares == true), retain supplier commercial
		        			if (retainSuppFares) {
		        				appendSupplierCommercialsToPaxTypeFares(paxTypeFareJson, ccommJrnyPsgrDtlJson, suppID, suppCcyCode, suppCommToTypeMap);
		        			}*/
		        			// From the passenger type client commercial JSON, retrieve calculated client entity commercials
		        			clientEntityCommJsonArr = applicableOnProductsJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
		    				
		        			if (clientEntityCommJsonArr == null) {
		        				logger.warn("Client commercials calculations not found");
		        			}
		    			}
						
						if (applicableOnProductsJson != null) {
							    amountAfterTaxcc = applicableOnProductsJson.optBigDecimal(JSON_PROP_TOTALFARE, new BigDecimal("0"));
							    amountBeforeTaxcc = applicableOnProductsJson.getJSONObject(JSON_PROP_FAREBREAKUP)
									.optBigDecimal(JSON_PROP_BASEFARE, new BigDecimal("0"));
							amountAfterTaxcc = amountAfterTaxcc.setScale(2, BigDecimal.ROUND_HALF_UP);
							amountBeforeTaxcc = amountBeforeTaxcc.setScale(2, BigDecimal.ROUND_HALF_UP);
						}
							//to handle transfers in getDetails
								if (retainSuppFares == false && dynamicPkgAction.toLowerCase().contains("transfer")) {
									
									amountAfterTaxSI = (BigDecimal) (totalJson.optBigDecimal("rateTotalAmount", new BigDecimal("0")));
									amountBeforeTaxSI = (BigDecimal) (totalJson.optBigDecimal("rateTotalAmount", new BigDecimal("0")));
									totalJson.remove("rateTotalAmount");
									totalJson.put(JSON_PROP_AMOUNTAFTERTAX,amountAfterTaxSI);
									totalJson.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTaxSI);
								} 
								else if (retainSuppFares) {
									if (dynamicPkgAction.toLowerCase().contains("transfer")) {
										amountAfterTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTAFTERTAX,
												new BigDecimal("0")));
										amountBeforeTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTBEFORETAX,
												new BigDecimal("0")));
									}
								}
								
								if (dynamicPkgAction.toLowerCase().contains("night")){
									amountAfterTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTAFTERTAX,
											new BigDecimal("0")));
									amountBeforeTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTBEFORETAX,
											new BigDecimal("0")));
								}
							 if(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)||(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)||dynamicPkgAction.equals("remFee"))) {
								amountAfterTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNT,
										new BigDecimal("0")));
								amountBeforeTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTBEFORETAX,
										new BigDecimal("0")));
								totalJson.remove(JSON_PROP_AMOUNT);
								totalJson.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTaxSI);
							}
							else if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_INSURANCE)) {
								amountAfterTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNT,
										new BigDecimal("0")));
								amountBeforeTaxSI = (BigDecimal) (totalJson.getJSONObject(JSON_PROP_BASEPREMIUM).optBigDecimal(JSON_PROP_AMOUNT,
										new BigDecimal("0")));
								insuranceCC = totalJson.getJSONObject(JSON_PROP_BASEPREMIUM).getString(JSON_PROP_CCYCODE);
								totalJson.remove(JSON_PROP_BASEPREMIUM);
								totalJson.remove(JSON_PROP_AMOUNT);
								totalJson.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTaxSI);
								totalJson.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTaxSI);
							}
							
							amountAfterTaxSI = amountAfterTaxSI.setScale(2, BigDecimal.ROUND_HALF_UP);
							amountBeforeTaxSI = amountBeforeTaxSI.setScale(2, BigDecimal.ROUND_HALF_UP);
							amountAfterTaxvalue = (amountAfterTaxcc).compareTo(amountAfterTaxSI);
							amountBeforeTaxvalue = amountBeforeTaxcc.compareTo(amountBeforeTaxSI);
						
						if (applicableOnProductsJson!= null) {
						 roomTypeCC = applicableOnProductsJson.optString(JSON_PROP_ROOMTYPE);
						}
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
						
						if ((roomTypeSI.equalsIgnoreCase(roomTypeCC) && amountAfterTaxvalue == 0 && amountBeforeTaxvalue == 0)||dynamicPkgAction.equals("remFee")) {
							// Only in case of reprice when (retainSuppFares == true), retain supplier commercial
						//	if (retainSuppFares) {
									appendSupplierCommercialsToPrice(totalJson, applicableOnProductsJson, suppID, suppCcyCode, scommToTypeMap);
						//	}
							
							/*//Creating a new map of paxType and paxCount to get per roomType
							if(paxInfoMap.containsKey(paxType)) {
								paxQty = paxQty.add(paxCount);
								paxInfoMap.put(paxType, paxQty);}
							else {
								paxQty = paxCount;
								paxInfoMap.put(paxType, paxCount);
							}*/
							for (int n = 0; clientEntityCommJsonArr != null && n < clientEntityCommJsonArr.length(); n++) {
								JSONArray clientEntityCommercialsJsonArr = new JSONArray();
								JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(n);

								// TODO: In case of B2B, do we need to add markups for all client hierarchy
								// levels?
								if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) {
									markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);
									clientEntityCommercialsJsonArr
											.put(HolidaysSearchProcessor.convertToClientEntityCommercialJson(markupCalcJson, clntCommToTypeMap, suppCcyCode));
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
										clientEntityCommercialsJsonArr.put(HolidaysSearchProcessor.convertToClientEntityCommercialJson(additionalCommJson,
												clntCommToTypeMap, suppCcyCode));

										if (COMM_TYPE_RECEIVABLE.equals(additionalCommType)) {
											String additionalCommCcy = additionalCommJson.optString(JSON_PROP_COMMCCY);
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
							BigDecimal baseFareAmt= new BigDecimal(0);
							/*if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_INSURANCE)) {
							baseFareAmt = totalJson.getJSONObject(JSON_PROP_BASEPREMIUM).optBigDecimal(JSON_PROP_AMOUNT, new BigDecimal("0"));
							}else {*/
								baseFareAmt = totalJson.optBigDecimal(JSON_PROP_AMOUNTBEFORETAX, new BigDecimal("0"));
							//}
							JSONArray ccommTaxDetailsJsonArr = null;
							if (markupCalcJson != null) {
								JSONObject fareBreakupJson = markupCalcJson.optJSONObject(JSON_PROP_FAREBREAKUP);
								if (fareBreakupJson != null) {
									baseFareAmt = fareBreakupJson.optBigDecimal(JSON_PROP_BASEFARE, baseFareAmt);
									ccommTaxDetailsJsonArr = fareBreakupJson.optJSONArray(JSON_PROP_TAXDETAILS);
								}
							}
							
							 if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_INSURANCE)) {
								commPriceJson.put(JSON_PROP_TYPE, totalJson.getString(JSON_PROP_PAXTYPE));
								totalJson.put(JSON_PROP_TYPE, totalJson.getString(JSON_PROP_PAXTYPE));
								totalJson.remove(JSON_PROP_PAXTYPE);
								JSONObject basePremium = new JSONObject();
								basePremium.put(JSON_PROP_AMOUNT, baseFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
								commPriceJson.put(JSON_PROP_BASEPREMIUM,basePremium);
								commPriceJson.put(JSON_PROP_CURRENCYCODE, insuranceCC);
								totalJson.put(JSON_PROP_CURRENCYCODE, insuranceCC);
							}else {
								commPriceJson.put(JSON_PROP_TYPE, totalJson.getString(JSON_PROP_TYPE));
								commPriceJson.put(JSON_PROP_AMOUNTBEFORETAX,
										baseFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
							}
							
							commPriceJson.put(JSON_PROP_CCYCODE, clientCcyCode);
							
							
							if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_INSURANCE)) {
								paxTypeTotalFare = paxTypeTotalFare.add(commPriceJson.getJSONObject(JSON_PROP_BASEPREMIUM).getBigDecimal(JSON_PROP_AMOUNT));
								totalFareCompsGroup.add(JSON_PROP_AMOUNTBEFORETAX, clientCcyCode,
										totalJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX).multiply(paxCount));
							}else {
								//to handle transfers in getDetails
							/*	if (retainSuppFares == false && dynamicPkgAction.toLowerCase().contains("transfer")) {
									paxTypeTotalFare = paxTypeTotalFare.add(commPriceJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX));
									totalFareCompsGroup.add(JSON_PROP_AMOUNTBEFORETAX, clientCcyCode,
											totalJson.getBigDecimal("rateTotalAmount").multiply(paxCount));
									
								}else {*/
								paxTypeTotalFare = paxTypeTotalFare.add(commPriceJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX));
								totalFareCompsGroup.add(JSON_PROP_AMOUNTBEFORETAX, clientCcyCode,
										totalJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX).multiply(paxCount));
								
							//}
							}
							
							
							int offset = 0;
							if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_INSURANCE)) {
								//charge array..sent in parameter totalArray
								JSONArray charge = totalJson.getJSONArray(JSON_PROP_CHARGE);
								JSONArray chargeArrCC = new JSONArray();
							for (int c=0 ; c< charge.length();c++) {
								   JSONObject chargeJson = charge.getJSONObject(c);
								   JSONArray paxTypeTaxJsonArr = chargeJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
								   JSONObject taxesJson = HolidaysSearchProcessor.getCommercialPricesTaxesJson(paxTypeTaxJsonArr, ccommTaxDetailsJsonArr, offset,
											totalFareCompsGroup, paxCount, clientCcyCode, clientMarket);
								   JSONObject chargeJsonCC = new JSONObject();
								 
								  chargeJsonCC.put(JSON_PROP_TAXES, taxesJson);
								  chargeArrCC.put(chargeJsonCC);
								   paxTypeTotalFare = paxTypeTotalFare.add(taxesJson.getBigDecimal(JSON_PROP_AMOUNT));
								}  commPriceJson.put(JSON_PROP_CHARGE, chargeArrCC);
							
							}else {
								//to handle transfers in getDetails
								if (retainSuppFares){
									JSONArray paxTypeTaxJsonArr = totalJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
									JSONObject taxesJson = HolidaysSearchProcessor.getCommercialPricesTaxesJson(paxTypeTaxJsonArr, ccommTaxDetailsJsonArr, offset,
											totalFareCompsGroup, paxCount, clientCcyCode, clientMarket);
									commPriceJson.put(JSON_PROP_TAXES, taxesJson);
									paxTypeTotalFare = paxTypeTotalFare.add(taxesJson.getBigDecimal(JSON_PROP_AMOUNT));
								}
								if (retainSuppFares==false && !(dynamicPkgAction.toLowerCase().contains("transfer"))) {
								JSONArray paxTypeTaxJsonArr = totalJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
								JSONObject taxesJson = HolidaysSearchProcessor.getCommercialPricesTaxesJson(paxTypeTaxJsonArr, ccommTaxDetailsJsonArr, offset,
										totalFareCompsGroup, paxCount, clientCcyCode, clientMarket);
								commPriceJson.put(JSON_PROP_TAXES, taxesJson);
								paxTypeTotalFare = paxTypeTotalFare.add(taxesJson.getBigDecimal(JSON_PROP_AMOUNT));
							}}
							// If amount of receivables group is greater than zero, then append to
							// commercial prices
							if (paxReceivablesCompsGroup != null
									&& paxReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
								paxTypeTotalFare = paxTypeTotalFare.add(paxReceivablesCompsGroup.getComponentAmount());
								totalFareCompsGroup.addSubComponent(totalPaxReceivablesCompsGroup, null);
								commPriceJson.put(JSON_PROP_RECEIVABLES, paxReceivablesCompsGroup.toJSON());
							}
							
							if(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)||(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)||dynamicPkgAction.equals("remFee"))){
								commPriceJson.put("feeName", totalJson.getString("feeName"));
								commPriceJson.put("feeCode", totalJson.getString("feeCode"));
								commPriceJson.put("text", totalJson.getString("text"));
								commPriceJson.put("type", totalJson.getString("type"));
								commPriceJson.put("maxChargeUnitApplies", totalJson.getString("maxChargeUnitApplies"));
							}
							// -------------------------END-----------------------------------

							JSONObject clientCommercialPkgInfoJson = new JSONObject();
							clientCommercialPkgInfoJson.put(JSON_PROP_TYPE, paxType);
							clientCommercialPkgInfoJson.put(JSON_PROP_CLIENTENTITYCOMMS, clientCommercials);
							clientCommercialPkgInfoArr.put(clientCommercialPkgInfoJson);
							
							if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_INSURANCE)) {
								JSONObject taxesJson = totalJson.getJSONArray(JSON_PROP_CHARGE).getJSONObject(0).getJSONObject(JSON_PROP_TAXES);
								totalJson.remove(JSON_PROP_CHARGE);
								totalJson.put(JSON_PROP_TAXES, taxesJson);
								
							}
							suppPaxTypeFaresJsonArr.put(totalJson);
							if (retainSuppFares == false && dynamicPkgAction.toLowerCase().contains("transfer")) {
								commPriceJson.put("rateTotalAmount", paxTypeTotalFare);
							}
							if (retainSuppFares){
							if (dynamicPkgAction.toLowerCase().contains("transfer")){
							commPriceJson.put(JSON_PROP_AMOUNTAFTERTAX, paxTypeTotalFare);
							}}
							
						    if(dynamicPkgAction.toLowerCase().contains("night")){
						    	commPriceJson.put(JSON_PROP_AMOUNTAFTERTAX, paxTypeTotalFare);
						    }
							if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_INSURANCE)||(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)||(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)||dynamicPkgAction.equals("remFee")))){
							commPriceJson.put(JSON_PROP_AMOUNT,paxTypeTotalFare);
							}
							//commPriceJson.put(JSON_PROP_AMOUNTBEFORETAX, baseFareAmt);
							commPriceJson.put(JSON_PROP_CCYCODE, clientCcyCode);
							
							
							//if(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_HOTEL_PRE)||dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_HOTEL_POST)) {
							if(dynamicPkgAction.toLowerCase().contains("prenight")||dynamicPkgAction.toLowerCase().contains("postnight")) {
								roomRateJson.put(JSON_PROP_TOTAL,commPriceJson);
								redisRoomRateJson.put(JSON_PROP_TOTAL, commPriceJson);
								roomPaxTypeFaresJsonArr.put(commPriceJson);
								
								
							}/*else if(dynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_PRE)||(dynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_POST))){
							
								totalArray.put(t,commPriceJson);
								redisTotalArr.put(t,commPriceJson);
								//cabinOptionJson for cruise
								roomRateJson.put(JSON_PROP_TOTAL, totalArray);
								redisRoomRateJson.put(JSON_PROP_TOTAL, redisTotalArr);
								roomPaxTypeFaresJsonArr.put(commPriceJson);
								
							}*/
							else if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_INSURANCE)) {
								//planCostArr sent in below parameter
								totalArray.put(t,commPriceJson);
								//remove charge array from redis structure
								JSONObject newPriceJson = new JSONObject(commPriceJson.toString());
								JSONObject taxes = newPriceJson.getJSONArray(JSON_PROP_CHARGE).getJSONObject(0).getJSONObject(JSON_PROP_TAXES);
								newPriceJson.remove(JSON_PROP_CHARGE);
								newPriceJson.put(JSON_PROP_TAXES, taxes);
								newPriceJson.remove(JSON_PROP_BASEPREMIUM);
								newPriceJson.remove(JSON_PROP_AMOUNT);
								newPriceJson.put(JSON_PROP_AMOUNTBEFORETAX, commPriceJson.getJSONObject(JSON_PROP_BASEPREMIUM).getBigDecimal(JSON_PROP_AMOUNT));
								newPriceJson.put(JSON_PROP_AMOUNTAFTERTAX, commPriceJson.getBigDecimal(JSON_PROP_AMOUNT));
								redisTotalArr.put(t,newPriceJson);
								//at Insurance comp level
								roomPaxTypeFaresJsonArr.put(newPriceJson);
								
							}else if(dynamicPkgAction.toLowerCase().contains("transfer")){
								totalArray.put(t,commPriceJson);
								// handle transfer in getDetails
								if (retainSuppFares == false && dynamicPkgAction.toLowerCase().contains("transfer")) {
									JSONObject newPriceJson = new JSONObject(commPriceJson.toString());
									newPriceJson.put(JSON_PROP_AMOUNTAFTERTAX, commPriceJson.getBigDecimal("rateTotalAmount"));
									newPriceJson.remove("rateTotalAmount");
									redisTotalArr.put(t,newPriceJson);
									roomRateJson.put(JSON_PROP_TOTALCHARGE, totalArray);
									redisRoomRateJson.put(JSON_PROP_TOTAL, redisTotalArr);
									//at ground service level
									roomPaxTypeFaresJsonArr.put(newPriceJson);
								}
								
								//groundServiceJson for transfer -reprice
								if (retainSuppFares) {
									redisTotalArr.put(t,commPriceJson);
									roomRateJson.put(JSON_PROP_TOTAL, totalArray);
									redisRoomRateJson.put(JSON_PROP_TOTAL, redisTotalArr);
									//at ground service level
									roomPaxTypeFaresJsonArr.put(commPriceJson);
								} 
								//at comp level
								//CCPaxTypeFaresJsonArr.put(commPriceJson);
								
							}else if(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_EXTRAS)||(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_SURCHARGES)||dynamicPkgAction.equals("remFee"))){
								totalArray.put(t,commPriceJson);
								JSONObject newPriceJson = new JSONObject(commPriceJson.toString());
								newPriceJson.remove(JSON_PROP_AMOUNT);
								newPriceJson.put(JSON_PROP_AMOUNTAFTERTAX, commPriceJson.getBigDecimal(JSON_PROP_AMOUNT));
								//To handle multiple extra we need to set to index 
								redisTotalArr.put(0,newPriceJson);
								roomPaxTypeFaresJsonArr.put(newPriceJson);
								
							}
								
							}
						
						
			
		
	}
	
	private static JSONObject getClientCommercialsApplicableOnForPassengerType(JSONObject ccommPkgDtlsJson,
			String paxType,String roomType, String productNameSI, String dynamicPkgAction) {
		String componentName ="";
		if (ccommPkgDtlsJson == null || paxType == null) {
			return null;
		}
		
		// Search this paxType in client commercials journeyDetails 
		JSONArray applicableOnProductsArr = ccommPkgDtlsJson.getJSONArray("applicableOnProducts");
		for (int a = 0; a < applicableOnProductsArr.length(); a++) {
			JSONObject applicableOnProductsJson = applicableOnProductsArr.getJSONObject(a);
			String productNameCC = applicableOnProductsJson.getString(JSON_PROP_PRODUCTNAME).toLowerCase();
			String passengerTypeCC = applicableOnProductsJson.getString(JSON_PROP_PASSENGERTYPE);
			String roomTypeCC = applicableOnProductsJson.getString(JSON_PROP_ROOMTYPE);
			if (productNameCC.toLowerCase().contains("extra")||productNameCC.toLowerCase().contains("surcharge")) {
			    componentName = applicableOnProductsJson.getString("componentType");}
			else {
				componentName = applicableOnProductsJson.getString("componentName");
			}
		   if (paxType.equals(passengerTypeCC) && (roomType.equalsIgnoreCase(roomTypeCC)&& (productNameSI.equalsIgnoreCase(productNameCC) && (dynamicPkgAction.equalsIgnoreCase(componentName))))) {
				return applicableOnProductsJson;
			}
		}
		
		return null;
    }

	private static void addSupplierTotalFare(JSONObject compJson, Map<String, BigDecimal> paxInfoMap, String clientCcyCode,String dynamicPkgAction) {
		JSONObject suppCompTotalFareJson = compJson.getJSONObject(JSON_PROP_SUPPLIERPRICINGINFO);
		
		JSONArray paxTypeFaresArr,roomPaxTypeFaresArr;
	
		paxTypeFaresArr = suppCompTotalFareJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
		
		JSONObject totalCompFareJson = compJson.getJSONObject(JSON_PROP_TOTALPRICINGINFO);
		
		roomPaxTypeFaresArr = totalCompFareJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
		
		PriceComponentsGroup totalFareComGroup = new PriceComponentsGroup(JSON_PROP_TOTAL, clientCcyCode, new BigDecimal(0), true);
		PriceComponentsGroup suppFareComGroup = new PriceComponentsGroup(JSON_PROP_TOTAL, clientCcyCode, new BigDecimal(0), true);
		
		
		Map<String, JSONObject> suppCommTotalsMap = new HashMap<String, JSONObject>();
		Map<String, JSONObject> clientCommTotalsMap = new HashMap<String, JSONObject>();
		BigDecimal totalFareAmt = new BigDecimal(0);
		BigDecimal baseFareAmt = new BigDecimal(0);
		BigDecimal totalCCFareAmt = new  BigDecimal(0);
		BigDecimal baseCCFareAmt = new  BigDecimal(0);
		BigDecimal paxCount = new BigDecimal(0);
		
		String ccyCode = null;
		JSONObject clientEntityTotalCommercials=null;
		JSONArray totalClientArr= new JSONArray();
		for (int i = 0; i < paxTypeFaresArr.length(); i++) {
			JSONObject paxTypeFare = paxTypeFaresArr.getJSONObject(i);
			if (paxTypeFare.has(JSON_PROP_TYPE)){
				//for extrasComponent
				if(paxTypeFare.get(JSON_PROP_TYPE).equals("")) {
					paxCount =new BigDecimal(0);
					for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
						paxCount= paxCount.add(entry.getValue());
						
				}
				/*if (paxTypeFare.getString("maxChargeUnitApplies").equalsIgnoreCase(paxCount.toString())){
					paxCount = new BigDecimal(1);
				}*/}
				
				else {
			paxCount = paxInfoMap.get(paxTypeFare.get(JSON_PROP_TYPE));
			}
			}
			if(paxTypeFare.has(JSON_PROP_AMOUNTBEFORETAX)) {
				baseFareAmt = baseFareAmt.add(paxTypeFare.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX).multiply(paxCount));
				}
			/*else if (paxTypeFare.has(JSON_PROP_PAXTYPE)){
			paxCount = paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE));
			}*/
			
			if(paxTypeFare.has(JSON_PROP_AMOUNTAFTERTAX)) {
				totalFareAmt = totalFareAmt.add(paxTypeFare.optBigDecimal(JSON_PROP_AMOUNTAFTERTAX,new BigDecimal(0)).multiply(paxCount));
			}
			if(paxTypeFare.has("rateTotalAmount")) {
				totalFareAmt = totalFareAmt.add(paxTypeFare.getBigDecimal("rateTotalAmount").multiply(paxCount));
			}
			//for insurance comp and fees -surcharge /extras
			else if (paxTypeFare.has(JSON_PROP_AMOUNT)) {
				totalFareAmt = totalFareAmt.add(paxTypeFare.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
				//for insurance comp
			}
			if(paxTypeFare.has(JSON_PROP_BASEPREMIUM)) {
				baseFareAmt = baseFareAmt.add(paxTypeFare.getJSONObject(JSON_PROP_BASEPREMIUM).getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
			}
			ccyCode = (ccyCode == null) ? paxTypeFare.getString(JSON_PROP_CCYCODE) : ccyCode;
	
			//for insurance comp
			JSONArray paxTypeTaxJsonArr =new JSONArray();
			/* if (paxTypeFare.has(JSON_PROP_CHARGE)){
			JSONArray charge = paxTypeFare.getJSONArray(JSON_PROP_CHARGE);
			
				for (int c = 0; c < charge.length(); c++) {
					JSONObject chargeJson = charge.getJSONObject(c);
					paxTypeTaxJsonArr = chargeJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
					for (int l=0; l < paxTypeTaxJsonArr.length(); l++) {
						JSONObject paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
						clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
						String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
			
						suppFareComGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXCODE), clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
						}
				}
			}
			 else {*/
			if  (paxTypeFare.has("taxes")) {
				paxTypeTaxJsonArr = paxTypeFare.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
				for (int l=0; l < paxTypeTaxJsonArr.length(); l++) {
					JSONObject paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
					clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
					String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
		
					suppFareComGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXDESCRIPTION), clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
					}
			}
			
			JSONArray suppCommJsonArr = paxTypeFare.optJSONArray(JSON_PROP_SUPPCOMM);
			//the order of clientCommercialItinInfo will same as that of normal paxTypeFares
			JSONObject clientItinInfoJson=compJson.getJSONArray(JSON_PROP_CLIENTCOMMERCIALINFO).getJSONObject(i);
			JSONArray clientCommJsonArr=clientItinInfoJson.optJSONArray(JSON_PROP_CLIENTENTITYCOMMS);
			
			// If no supplier commercials have been defined in BRMS, the JSONArray for suppCommJsonArr will be null.
			// In this case, log a message and proceed with other calculations.
			if (suppCommJsonArr == null) {
				logger.warn("No supplier commercials found");
			}else {
				for (int j=0; j < suppCommJsonArr.length(); j++) {
					JSONObject suppCommJson = suppCommJsonArr.getJSONObject(j);
					String suppCommName = suppCommJson.getString(JSON_PROP_COMMNAME);
					JSONObject suppCommTotalsJson = null;
					if (suppCommTotalsMap.containsKey(suppCommName)) {
						suppCommTotalsJson = suppCommTotalsMap.get(suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxCount)));
					}
					else {
						suppCommTotalsJson = new JSONObject();
						suppCommTotalsJson.put(JSON_PROP_COMMNAME, suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMTYPE, suppCommJson.getString(JSON_PROP_COMMTYPE));
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxCount));
						suppCommTotalsJson.put(JSON_PROP_COMMCCY,suppCommJson.get(JSON_PROP_COMMCCY).toString());
						suppCommTotalsMap.put(suppCommName, suppCommTotalsJson);
					}
				}
			}
			
			if (clientCommJsonArr == null) {
				logger.warn("No client commercials found");
			}
			else  {
				for (int l=0; l < clientCommJsonArr.length(); l++) {
					//TODO:Add jsonElements from clientEntity once they can be fetched.
					JSONObject clientCommJson = clientCommJsonArr.getJSONObject(l);
					JSONObject clientCommEntJson= new JSONObject();
					
					//JSONObject clientCommJson =clientEntityCommJson.getJSONObject("clientCommercial");
					//clientCommEntJson
					JSONArray clientEntityCommJsonArr=clientCommJson.getJSONArray(JSON_PROP_CLIENTCOMM);
					
					
					
					JSONObject clientCommTotalsJson = null;
					JSONArray clientTotalEntityArray=new JSONArray();
					
					for(int m=0;m<clientEntityCommJsonArr.length();m++) {
						
						JSONObject clientCommEntityJson=clientEntityCommJsonArr.getJSONObject(m);
						String clientCommName = clientCommEntityJson.getString(JSON_PROP_COMMNAME);
						
						
						if (clientCommTotalsMap.containsKey(clientCommName)) {
							clientEntityTotalCommercials= clientCommTotalsMap.get(clientCommName);
							//clientCommTotalsJson=clientEntityTotalCommercials.getJSONObject("clientCommercialsTotal");
							//clientCommTotalsJson = clientCommTotalsMap.get(clientCommName);
							clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT).add(clientCommEntityJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxCount)));
						}
						else {
							clientEntityTotalCommercials= new JSONObject();
							clientCommTotalsJson = new JSONObject();
							clientCommTotalsJson.put(JSON_PROP_COMMNAME, clientCommName);
							clientCommTotalsJson.put(JSON_PROP_COMMTYPE, clientCommEntityJson.getString(JSON_PROP_COMMTYPE));
							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, clientCommEntityJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxCount));
							clientCommTotalsJson.put(JSON_PROP_COMMCCY,clientCommEntityJson.get(JSON_PROP_COMMCCY).toString());

							clientTotalEntityArray.put(clientCommTotalsJson);
						
						
							clientCommTotalsMap.put(clientCommName, clientCommTotalsJson);
						}
					}
					
					if((clientTotalEntityArray!=null) && (clientTotalEntityArray.length()>0) ) {
						clientCommEntJson.put("clientCommercials", clientTotalEntityArray);
						clientCommEntJson.put(JSON_PROP_CLIENTID, clientCommJson.optString(JSON_PROP_CLIENTID,""));
						clientCommEntJson.put(JSON_PROP_PARENTCLIENTID, clientCommJson.optString(JSON_PROP_PARENTCLIENTID,""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYTYPE, clientCommJson.optString(JSON_PROP_COMMENTITYTYPE,""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYID, clientCommJson.optString(JSON_PROP_COMMENTITYID,""));
						
						totalClientArr.put(clientCommEntJson);
					}
				}
			
			}}
		
		for (int i = 0; i < roomPaxTypeFaresArr.length(); i++) {
			
			JSONObject roomPaxTypeFare = roomPaxTypeFaresArr.getJSONObject(i);
			if (roomPaxTypeFare.has(JSON_PROP_TYPE)){
				//for extrasComponent
				if(roomPaxTypeFare.get(JSON_PROP_TYPE).equals("")) {
					paxCount =new BigDecimal(0);
					for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
						paxCount= paxCount.add(entry.getValue());
						
				}
				/*if (roomPaxTypeFare.getString("maxChargeUnitApplies").equalsIgnoreCase(paxCount.toString())){
					paxCount = new BigDecimal(1);
				}*/}else {
					paxCount = paxInfoMap.get(roomPaxTypeFare.get(JSON_PROP_TYPE));
				}
				}
			if(roomPaxTypeFare.has(JSON_PROP_AMOUNTBEFORETAX)) {
				baseCCFareAmt = baseCCFareAmt.add(roomPaxTypeFare.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX).multiply(paxCount));
				}
				/*else if (roomPaxTypeFare.has(JSON_PROP_PAXTYPE)){
				paxCount = paxInfoMap.get(roomPaxTypeFare.get(JSON_PROP_PAXTYPE));
				}*/
			if(roomPaxTypeFare.has(JSON_PROP_AMOUNTAFTERTAX)) {
			totalCCFareAmt = totalCCFareAmt.add(roomPaxTypeFare.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX).multiply(paxCount));
			
			}
			if(roomPaxTypeFare.has("rateTotalAmount")) {
				totalFareAmt = totalFareAmt.add(roomPaxTypeFare.getBigDecimal("rateTotalAmount").multiply(paxCount));
			}
			//for insurance comp
			else if (roomPaxTypeFare.has(JSON_PROP_AMOUNT)) {
				totalCCFareAmt = totalCCFareAmt.add(roomPaxTypeFare.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));	
				
			}
			if(roomPaxTypeFare.has(JSON_PROP_BASEPREMIUM)) {
				baseCCFareAmt = baseCCFareAmt.add(roomPaxTypeFare.getJSONObject(JSON_PROP_BASEPREMIUM).getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
			}
			//for insurance comp
			JSONArray paxTypeTaxJsonArr =new JSONArray();
			/* if (roomPaxTypeFare.has(JSON_PROP_CHARGE)){
			JSONArray charge = roomPaxTypeFare.getJSONArray(JSON_PROP_CHARGE);
			
				for (int c = 0; c < charge.length(); c++) {
					JSONObject chargeJson = charge.getJSONObject(c);
					paxTypeTaxJsonArr = chargeJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
					for (int l=0; l < paxTypeTaxJsonArr.length(); l++) {
						JSONObject paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
						clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
						String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
			
						totalFareComGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXCODE), clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
						}
				}
			}
			 else {*/
			if  (roomPaxTypeFare.has("taxes")) {
				paxTypeTaxJsonArr = roomPaxTypeFare.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
				 for (int l=0; l < paxTypeTaxJsonArr.length(); l++) {
						JSONObject paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
						clientCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
						String taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXDESCRIPTION);
			
						totalFareComGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXDESCRIPTION), clientCcyCode, paxTypeTaxJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
						}
			}
			if (roomPaxTypeFare.has(JSON_PROP_RECEIVABLES)){
			 JSONArray paxTypeReceivablesJsonArr = roomPaxTypeFare.optJSONObject(JSON_PROP_RECEIVABLES).optJSONArray(JSON_PROP_RECEIVABLE);
				for (int l=0; l < paxTypeReceivablesJsonArr.length(); l++) {
					JSONObject paxTypeReceivableJson = paxTypeReceivablesJsonArr.getJSONObject(l);
				    clientCcyCode = paxTypeReceivableJson.getString(JSON_PROP_CCYCODE);
					String code = paxTypeReceivableJson.getString(JSON_PROP_CODE);
		
					totalFareComGroup.add(mRcvsPriceCompQualifier.concat(code).concat("@").concat(JSON_PROP_CODE), clientCcyCode, paxTypeReceivableJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
					}
			}
		}
		// Convert map of Commercial Head to Commercial Amount to JSONArray and append in suppItinPricingInfoJson
		JSONArray suppCommTotalsJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet().iterator();
		while (suppCommTotalsIter.hasNext()) {
			suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
		}
		suppCompTotalFareJson.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr); 
		
		compJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, totalClientArr);
		
		//to set the supplierTotalFare
		JSONObject compTotalFare = suppCompTotalFareJson.getJSONObject(JSON_PROP_TOTALFARE);
		compTotalFare = (JSONObject) suppFareComGroup.toJSON();
		compTotalFare.put(JSON_PROP_AMOUNTAFTERTAX, totalFareAmt);
		compTotalFare.put(JSON_PROP_AMOUNTBEFORETAX, baseFareAmt);
		suppCompTotalFareJson.put(JSON_PROP_TOTALFARE,compTotalFare);
		suppCompTotalFareJson.getJSONObject(JSON_PROP_TOTALFARE).remove("amount");
		
		//to set the componentTotalFare
		JSONObject totalPrice = totalCompFareJson.getJSONObject(JSON_PROP_TOTALFARE);
		totalPrice = (JSONObject) totalFareComGroup.toJSON();
		totalPrice.put(JSON_PROP_AMOUNTAFTERTAX, totalCCFareAmt);
		totalPrice.put(JSON_PROP_AMOUNTBEFORETAX, baseCCFareAmt);
		totalCompFareJson.put(JSON_PROP_TOTALFARE,totalPrice);
		totalCompFareJson.getJSONObject(JSON_PROP_TOTALFARE).remove("amount");
	}
	
	private static JSONObject calculatePriceHotelOrCruise(UserContext usrCtx, Map<String, String> scommToTypeMap,
			Map<String, String> clntCommToTypeMap, String clientMarket, String clientCcyCode, BigDecimal paxCount,
			 JSONObject totalJson, String roomTypeSI, String paxType,
			 String suppID, PriceComponentsGroup totalFareCompsGroup,
			PriceComponentsGroup totalIncentivesGroup, JSONArray suppPaxTypeFaresJsonArr,
			JSONArray clientCommercialPkgInfoArr, JSONObject ccommRoomDtlsJson, ClientType clientType,Boolean retainSuppFares, 
			Map<String, BigDecimal> paxInfoMap, BigDecimal paxQty, String dynamicPkgAction,
			JSONArray totalArray, int t, JSONObject roomRateorcategoryOptionJson,JSONObject redisRoomRateJson, JSONArray roomPaxTypeFaresJsonArr, JSONArray redistotalArr) {
		
		//JSONObject totalJson= roomRateJson.getJSONObject(JSON_PROP_TOTAL);
		String suppCcyCode = totalJson.getString(JSON_PROP_CCYCODE);
		// PassengerType psgrType = PassengerType.forString(paxType);
		
		JSONObject ccommPaxDtlsJson = HolidaysSearchProcessor.getClientCommercialsPackageDetailsForPassengerType(ccommRoomDtlsJson, paxType);
		JSONArray clientEntityCommJsonArr = null;
		int amountAfterTaxvalue = 1, amountBeforeTaxvalue = 1;
		if (ccommPaxDtlsJson == null) {
			logger.info(String.format("Passenger type %s details not found client commercial packageDetails", paxType));
		}
		else {
			
			// From the passenger type client commercial JSON, retrieve calculated client entity commercials
			clientEntityCommJsonArr = ccommPaxDtlsJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
			if (clientEntityCommJsonArr == null) {
				logger.warn("Client commercials calculations not found");
			}
		}
		if (ccommPaxDtlsJson != null) {
			BigDecimal amountAfterTaxcc = ccommPaxDtlsJson.optBigDecimal(JSON_PROP_TOTALFARE, new BigDecimal("0"));
			BigDecimal amountBeforeTaxcc = ccommPaxDtlsJson.getJSONObject(JSON_PROP_FAREBREAKUP).optBigDecimal(JSON_PROP_BASEFARE, new BigDecimal("0"));
			BigDecimal amountAfterTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTAFTERTAX, new BigDecimal("0")));
			BigDecimal amountBeforeTaxSI = (BigDecimal) (totalJson.optBigDecimal(JSON_PROP_AMOUNTBEFORETAX, new BigDecimal("0")));
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
			// Only in case of reprice when (retainSuppFares == true), retain supplier commercial
			//if (retainSuppFares) {
					appendSupplierCommercialsToPrice(totalJson, ccommPaxDtlsJson, suppID, suppCcyCode, scommToTypeMap);
		//	}
			
			
			for (int n = 0; clientEntityCommJsonArr != null && n < clientEntityCommJsonArr.length(); n++) {
				JSONArray clientEntityCommercialsJsonArr = new JSONArray();
				JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(n);

				// TODO: In case of B2B, do we need to add markups for all client hierarchy
				// levels?
				if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) {
					markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);
					clientEntityCommercialsJsonArr.put(HolidaysSearchProcessor.convertToClientEntityCommercialJson(markupCalcJson, clntCommToTypeMap, suppCcyCode));
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
						clientEntityCommercialsJsonArr.put(HolidaysSearchProcessor.convertToClientEntityCommercialJson(additionalCommJson,clntCommToTypeMap, suppCcyCode));

						if (COMM_TYPE_RECEIVABLE.equals(additionalCommType)) {
							String additionalCommCcy = additionalCommJson.getString(JSON_PROP_COMMCCY);
							BigDecimal additionalCommAmt = additionalCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket));
							paxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt);
							totalPaxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode,additionalCommAmt.multiply(paxCount));
						}
					}
				}

				JSONObject clientEntityDetailsJson = new JSONObject();
				// JSONArray clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchy();
				ClientInfo[] clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchyArray();
				// clientEntityDetailsJson = clientEntityDetailsArr.getJSONObject(k);
				clientEntityDetailsJson.put(JSON_PROP_CLIENTID, clientEntityDetailsArr[n].getClientId());
				clientEntityDetailsJson.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsArr[n].getParentClienttId());
				clientEntityDetailsJson.put(JSON_PROP_COMMENTITYTYPE, clientEntityDetailsArr[n].getCommercialsEntityType());
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
							BigDecimal commAmt = clientEntityCommercialsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(commCcy, clientCcyCode, clientMarket)).multiply(paxCount, MATH_CTX_2_HALF_UP);
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
			commPriceJson.put(JSON_PROP_TYPE, totalJson.getString(JSON_PROP_TYPE));
			commPriceJson.put(JSON_PROP_AMOUNTBEFORETAX,baseFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
			commPriceJson.put(JSON_PROP_CCYCODE, clientCcyCode);
			//currencyCode = clientCcyCode;
			paxTypeTotalFare = paxTypeTotalFare.add(commPriceJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX));
			totalFareCompsGroup.add(JSON_PROP_AMOUNTBEFORETAX, clientCcyCode,
					totalJson.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX).multiply(paxCount));

			int offset = 0;
			JSONArray paxTypeTaxJsonArr = totalJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
			JSONObject taxesJson = HolidaysSearchProcessor.getCommercialPricesTaxesJson(paxTypeTaxJsonArr, ccommTaxDetailsJsonArr, offset,
					totalFareCompsGroup, paxCount, clientCcyCode, clientMarket);
			commPriceJson.put(JSON_PROP_TAXES, taxesJson);
			paxTypeTotalFare = paxTypeTotalFare.add(taxesJson.getBigDecimal(JSON_PROP_AMOUNT));

			// If amount of receivables group is greater than zero, then append to
			// commercial prices
			if (paxReceivablesCompsGroup != null
					&& paxReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
				paxTypeTotalFare = paxTypeTotalFare.add(paxReceivablesCompsGroup.getComponentAmount());
				totalFareCompsGroup.addSubComponent(totalPaxReceivablesCompsGroup, null);
				commPriceJson.put(JSON_PROP_RECEIVABLES, paxReceivablesCompsGroup.toJSON());
			}
			// -------------------------END-----------------------------------

			JSONObject clientCommercialPkgInfoJson = new JSONObject();
			clientCommercialPkgInfoJson.put(JSON_PROP_TYPE, paxType);
			clientCommercialPkgInfoJson.put(JSON_PROP_CLIENTENTITYCOMMS, clientCommercials);
			clientCommercialPkgInfoArr.put(clientCommercialPkgInfoJson);

			suppPaxTypeFaresJsonArr.put(totalJson);
			
			commPriceJson.put(JSON_PROP_AMOUNTAFTERTAX, paxTypeTotalFare);
			//commPriceJson.put(JSON_PROP_AMOUNTBEFORETAX, baseFareAmt);
			commPriceJson.put(JSON_PROP_CCYCODE, clientCcyCode);
	
			if(dynamicPkgAction.equals(DYNAMICPKGACTION_HOTEL_TOUR)) {
				
				roomRateorcategoryOptionJson.put("total",commPriceJson);
				redisRoomRateJson.put("total", commPriceJson);
				roomPaxTypeFaresJsonArr.put(commPriceJson);			
			}
			else if(dynamicPkgAction.equals(DYNAMICPKGACTION_CRUISE_TOUR)){
				
				totalArray.put(t,commPriceJson);
				redistotalArr.put(t,commPriceJson);
				roomRateorcategoryOptionJson.put(JSON_PROP_TOTAL, totalArray);
				redisRoomRateJson.put(JSON_PROP_TOTAL, redistotalArr);
				roomPaxTypeFaresJsonArr.put(commPriceJson);
			}
			
		}
		return totalJson;
		
		}
	
	// Append the supplier commercials returned by commercials engine in supplier pax type fares. 
    // This is required only at reprice/book time for financial consumption and supplier settlement purpose.
	private static void appendSupplierCommercialsToPrice(JSONObject totalJson,
			JSONObject ccommPaxDtlsJson, String suppID, String suppCcyCode, Map<String, String> scommToTypeMap) {
		JSONArray suppCommJsonArr = new JSONArray();
		JSONArray ccommSuppCommJsonArr = null;
		
		if (ccommPaxDtlsJson!= null) {
		     ccommSuppCommJsonArr = ccommPaxDtlsJson.optJSONArray(JSON_PROP_COMMDETAILS);
		}
		// If no supplier commercials have been defined in BRMS, the JSONArray for ccommSuppCommJsonArr will be null.
		// In this case, log a message and proceed with other calculations.
		if (ccommSuppCommJsonArr == null) {
			logger.warn(String.format("No supplier commercials found for supplier %s", suppID));
			return;
		}

		for (int x=0; x < ccommSuppCommJsonArr.length(); x++) {
			JSONObject ccommSuppCommJson = ccommSuppCommJsonArr.getJSONObject(x);
			JSONObject suppCommJson = new JSONObject();
			suppCommJson.put(JSON_PROP_COMMNAME, ccommSuppCommJson.getString(JSON_PROP_COMMNAME));
			suppCommJson.put(JSON_PROP_COMMTYPE, scommToTypeMap.get(ccommSuppCommJson.getString(JSON_PROP_COMMNAME)));
			suppCommJson.put(JSON_PROP_COMMAMOUNT, ccommSuppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
			suppCommJson.put(JSON_PROP_COMMCCY, ccommSuppCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
			suppCommJson.put(JSON_PROP_MDMRULEID,ccommSuppCommJson.optString("mdmruleId"));
			suppCommJsonArr.put(suppCommJson);
		}
		totalJson.put(JSON_PROP_SUPPCOMM, suppCommJsonArr);
		
	}

	//private static Element createSIRequest(JSONObject requestHeader, JSONObject requestBody, OperationConfig opConfig, UserContext usrCtx) throws Exception {
	private static Element createSIRequest(JSONObject requestHeader, JSONObject requestBody, ServiceConfig opConfig, UserContext usrCtx) throws Exception {
		
		// clone shell si request from ProductConfig collection HOLIDAYS document
		Element requestElement = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		
		// create Document object associated with request node, this Document object is also used to create new nodes.
		Document ownerDoc = requestElement.getOwnerDocument();
		String sessionID = requestHeader.getString(JSON_PROP_SESSIONID);
		String transactionID = requestHeader.getString(JSON_PROP_TRANSACTID);
		String userID = requestHeader.getString(JSON_PROP_USERID);

		Element requestHeaderElement = XMLUtils.getFirstElementAtXPath(requestElement, "./pac:RequestHeader");

		Element userElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:UserID");
		userElement.setTextContent(userID);

		Element sessionElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:SessionID");
		sessionElement.setTextContent(sessionID);

		Element transactionElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:TransactionID");
		transactionElement.setTextContent(transactionID);

		Element supplierCredentialsList = XMLUtils.getFirstElementAtXPath(requestHeaderElement,"./com:SupplierCredentialsList");

		// CREATE SI REQUEST BODY
		Element requestBodyElement = XMLUtils.getFirstElementAtXPath(requestElement, "./pac1:RequestBody");

		Element wrapperElement = XMLUtils.getFirstElementAtXPath(requestBodyElement, "./pac:OTA_DynamicPkgAvailRQWrapper");
		requestBodyElement.removeChild(wrapperElement);

		int sequence = 0;
		JSONArray dynamicPackageArr = requestBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		for (int i = 0; i < dynamicPackageArr.length(); i++) {
			JSONObject dynamicPackageObj = dynamicPackageArr.getJSONObject(i);
			sequence++;

			String supplierID = dynamicPackageObj.getString("supplierID");
			Element supWrapperElement = null;
			Element otaAvailRQ = null;
			Element searchCriteria = null;
			Element dynamicPackage = null;

			supplierCredentialsList = getSupplierCredentialsList(ownerDoc, usrCtx, supplierID, sequence,supplierCredentialsList);

			// Making request body for particular supplierID
			supWrapperElement = (Element) wrapperElement.cloneNode(true);
			requestBodyElement.appendChild(supWrapperElement);

			// Setting supplier id in request body
			Element supplierIDElement = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./pac:SupplierID");
			supplierIDElement.setTextContent(supplierID);

			// Setting sequence in request body
			Element sequenceElement = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./pac:Sequence");
			sequenceElement.setTextContent(Integer.toString(sequence));

			// creating element search criteria
			searchCriteria = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./ns:OTA_DynamicPkgAvailRQ/ns:SearchCriteria");

			// getting parent node OTA_DynamicPkgAvailRQ from SearchCriteria
			otaAvailRQ = (Element) searchCriteria.getParentNode();

			String tourCode = dynamicPackageObj.getString("tourCode");
			String brandName = dynamicPackageObj.getString("brandName");
			String subTourCode = dynamicPackageObj.getString("subTourCode");
			String requestedCurrency = dynamicPackageObj.optString("requestedCurrency");

			Element refPoint = XMLUtils.getFirstElementAtXPath(searchCriteria,"./ns:PackageOptionSearch/ns:OptionSearchCriteria/ns:Criterion/ns:RefPoint");
			Attr attributeBrandCode = ownerDoc.createAttribute("Code");
			attributeBrandCode.setValue(brandName);
			refPoint.setAttributeNode(attributeBrandCode);

			Element optionRef = XMLUtils.getFirstElementAtXPath(searchCriteria,"./ns:PackageOptionSearch/ns:OptionSearchCriteria/ns:Criterion/ns:OptionRef");
			Attr attributeTourCode = ownerDoc.createAttribute("Code");
			attributeTourCode.setValue(tourCode);
			optionRef.setAttributeNode(attributeTourCode);

			Element requestedCurrencyElem = XMLUtils.getFirstElementAtXPath(supWrapperElement,"./ns:OTA_DynamicPkgAvailRQ");
			Attr requestedCurrAttr = ownerDoc.createAttribute("RequestedCurrency");
	        	requestedCurrAttr.setValue(requestedCurrency);
	       		requestedCurrencyElem.setAttributeNode(requestedCurrAttr);
			
			// creating element dynamic package
			dynamicPackage = XMLUtils.getFirstElementAtXPath(otaAvailRQ, "./ns:DynamicPackage");

			// Creating Components element
			JSONObject components = dynamicPackageObj.getJSONObject(JSON_PROP_COMPONENTS);
			if (components == null || components.length() == 0)
				throw new Exception(String.format("Object components must be set for supplier %s", supplierID));
			
			 Element componentsElement = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:Components");
	         
			 //Creating Hotel Component
	         JSONArray hotelComponents = components.optJSONArray(JSON_PROP_HOTEL_COMPONENT);
	         if(hotelComponents != null && hotelComponents.length() != 0)
	              componentsElement = getHotelComponentElement(ownerDoc, hotelComponents, componentsElement);

			// Creating Air Component
			JSONArray airComponents = components.optJSONArray(JSON_PROP_AIR_COMPONENT);
			if (airComponents != null && airComponents.length() != 0)
				componentsElement = getAirComponentElement(ownerDoc, dynamicPackageObj, airComponents,componentsElement, supplierID);

			// Creating PackageOptionComponent Element
			Element packageOptionComponentElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOptionComponent");

			Attr attributeQuoteID = ownerDoc.createAttribute("QuoteID");
			attributeQuoteID.setValue(subTourCode);
			packageOptionComponentElement.setAttributeNode(attributeQuoteID);

			Element packageOptionsElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOptions");
			Element packageOptionElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOption");
			Element tpaElement = ownerDoc.createElementNS(NS_OTA, "ns:TPA_Extensions");

			//Creating Cruise Component
            JSONArray cruiseComponents = components.optJSONArray(JSON_PROP_CRUISE_COMPONENT);
            if(cruiseComponents != null && cruiseComponents.length() != 0)
            	tpaElement = getCruiseComponentElement(ownerDoc, cruiseComponents, tpaElement);

			// Creating Transfers Component
			JSONArray transfersComponents = components.optJSONArray(JSON_PROP_TRANSFER_COMPONENT);
			if (transfersComponents != null && transfersComponents.length() != 0)
				tpaElement = getTransferComponentElement(ownerDoc, transfersComponents, tpaElement);
			
			// Creating Activity Component
			JSONArray activityComponent = components.optJSONArray(JSON_PROP_ACTIVITY_COMPONENT);
			if (activityComponent != null && activityComponent.length() != 0)
				tpaElement = getActivityComponentElement(ownerDoc, activityComponent, tpaElement);

			// Creating Insurance Component
			JSONArray insuranceComponents = components.optJSONArray(JSON_PROP_INSURANCE_COMPONENT);
			if (insuranceComponents != null && insuranceComponents.length() != 0)
				tpaElement = getInsuranceComponentElement(ownerDoc, insuranceComponents, tpaElement);

			// Appending TPA element to package Option Element
			packageOptionElement.appendChild(tpaElement);

			// Appending package Option Element to package Options Element
			packageOptionsElement.appendChild(packageOptionElement);

			// Appending package Options Element to PackageOptionComponent Element
			packageOptionComponentElement.appendChild(packageOptionsElement);

			// Appending PackageOptionComponent Element to Components Element
			componentsElement.appendChild(packageOptionComponentElement);

			// create RestGuests xml elements
			JSONArray resGuests = dynamicPackageObj.getJSONArray(JSON_PROP_RESGUESTS);
			Element resGuestsElement = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:ResGuests");

			if (resGuests != null && resGuests.length() != 0) {
				for (int j = 0; j < resGuests.length(); j++) {
					JSONObject resGuest = resGuests.getJSONObject(j);
					Element resGuestElement = getResGuestElement(ownerDoc, resGuest);
					resGuestsElement.appendChild(resGuestElement);
				}
			}

			// Create GlobalInfo xml element
			JSONObject globalInfo = dynamicPackageObj.getJSONObject(JSON_PROP_GLOBALINFO);
			if (globalInfo != null && globalInfo.length() != 0) {
				Element globalInfoElement = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:GlobalInfo");
				globalInfoElement = getGlobalInfoElement(ownerDoc, globalInfo, globalInfoElement);
			}
		}

		return requestElement;
	}
	
	private static Element getActivityComponentElement(Document ownerDoc, JSONArray activityComponents,
			Element tpaElement) {

		Element activityComponentsElement = ownerDoc.createElementNS(NS_PAC, "tns:ActivityComponents");

		for (int i = 0; i < activityComponents.length(); i++) {
			JSONObject activityComponent = activityComponents.getJSONObject(i);

			Element activityComponentElement = ownerDoc.createElementNS(NS_PAC, "tns:ActivityComponent");
			
			// Setting DynamicPkgActon
			Attr attributeDynamicPkgAction = ownerDoc.createAttribute(DYNAMICPKGACTION);
			attributeDynamicPkgAction.setValue(activityComponent.getString(JSON_PROP_DYNAMICPKGACTION));
			activityComponentElement.setAttributeNode(attributeDynamicPkgAction);

			Attr attributeID = ownerDoc.createAttribute("ID");
			attributeID.setValue(activityComponent.optString("id"));
			activityComponentElement.setAttributeNode(attributeID);
			
			Attr attributeCreatedDate = ownerDoc.createAttribute("CreatedDate");
			attributeCreatedDate.setValue(activityComponent.optString("createdDate"));
			activityComponentElement.setAttributeNode(attributeCreatedDate);
			
			Attr attributeStatus = ownerDoc.createAttribute("AvailabilityStatus");
			attributeStatus.setValue(activityComponent.optString("availabilityStatus"));
			activityComponentElement.setAttributeNode(attributeStatus);

				// Setting ActivityService Element
				Element activityService = ownerDoc.createElementNS(NS_PAC, "tns:ActivityService");

				Attr attributeName = ownerDoc.createAttribute("Name");
				attributeName.setValue(activityComponent.optString("name"));
				activityService.setAttributeNode(attributeName);

				Attr attributeDesc = ownerDoc.createAttribute("Description");
				attributeDesc.setValue(activityComponent.optString("description"));
				activityService.setAttributeNode(attributeDesc);

				// Setting BasicInfo Element
				JSONObject basicInfo = activityComponent.getJSONObject("basicInfo");
				if (basicInfo != null && basicInfo.length() != 0) {
					Element basicInfoElem = ownerDoc.createElementNS(NS_PAC, "tns:BasicInfo");

					Attr tourActivityID = ownerDoc.createAttribute("TourActivityID");
					tourActivityID.setValue(basicInfo.getString("tourActivityID"));
					basicInfoElem.setAttributeNode(tourActivityID);

					Attr name = ownerDoc.createAttribute("Name");
					name.setValue(basicInfo.getString("name"));
					basicInfoElem.setAttributeNode(name);

					activityService.appendChild(basicInfoElem);
				}
				
				// Setting Reference Element
				JSONObject reference = activityComponent.getJSONObject("reference");
				if (reference != null && reference.length() != 0) {
					Element referenceElem = ownerDoc.createElementNS(NS_PAC, "tns:Reference");

					Attr id_Context = ownerDoc.createAttribute("ID_Context");
					id_Context.setValue(reference.getString("id_Context"));
					referenceElem.setAttributeNode(id_Context);

					Attr id = ownerDoc.createAttribute("ID");
					id.setValue(reference.getString("id"));
					referenceElem.setAttributeNode(id);
					
					Attr url = ownerDoc.createAttribute("URL");
					url.setValue(reference.getString("url"));
					referenceElem.setAttributeNode(url);

					activityService.appendChild(referenceElem);
				}
				
				// Setting Time Span
				JSONObject timeSpan = activityComponent.getJSONObject("timeSpan");
				if (timeSpan != null && timeSpan.length() != 0) {
					Element timeSpanElement = ownerDoc.createElementNS(NS_PAC, "tns:TimeSpan");

					Attr attributeStart = ownerDoc.createAttribute("Start");
					attributeStart.setValue(timeSpan.optString("start"));
					timeSpanElement.setAttributeNode(attributeStart);

					Attr attributeDuration = ownerDoc.createAttribute("Duration");
					attributeDuration.setValue(timeSpan.optString("duration"));
					timeSpanElement.setAttributeNode(attributeDuration);

					Attr attributeEnd = ownerDoc.createAttribute("End");
					attributeEnd.setValue(timeSpan.optString("end"));
					timeSpanElement.setAttributeNode(attributeEnd);

					activityService.appendChild(timeSpanElement);
				}
				
				// Setting Price Element
				JSONObject price = activityComponent.optJSONObject("price");
				if(price!= null && price.length()!=0) {
					Element priceElem = ownerDoc.createElementNS(NS_PAC, "tns:Price");
					JSONArray totalArr = price.optJSONArray("total");
					if (totalArr != null && totalArr.length() != 0) {
						for (int z = 0; z < totalArr.length(); z++) {

							JSONObject totalJson = totalArr.getJSONObject(z);
							Element totalElem = ownerDoc.createElementNS(NS_PAC, "tns:Total");
							
							Attr amountBeforeTax = ownerDoc.createAttribute("AmountBeforeTax");
							amountBeforeTax.setValue(Integer.toString(totalJson.optInt("amountBeforeTax")));
							totalElem.setAttributeNode(amountBeforeTax);
							
							Attr amountAfterTax = ownerDoc.createAttribute("AmountAfterTax");
							amountAfterTax.setValue(Integer.toString(totalJson.optInt("amountAfterTax")));
							totalElem.setAttributeNode(amountAfterTax);
	
							Attr attributeCurrency = ownerDoc.createAttribute("CurrencyCode");
							attributeCurrency.setValue(totalJson.getString("currencyCode"));
							totalElem.setAttributeNode(attributeCurrency);
	
							Attr attributeType = ownerDoc.createAttribute("Type");
							attributeType.setValue(totalJson.getString("type"));
							totalElem.setAttributeNode(attributeType);
							
							priceElem.appendChild(totalElem);
						}
						activityService.appendChild(priceElem);
					}
				}
				
				// Setting Deposits Element
				JSONObject depositsJson = activityComponent.optJSONObject("deposits");
				if(depositsJson!= null && depositsJson.length()!=0) {
					Element depositsElem = ownerDoc.createElementNS(NS_PAC, "tns:Deposits");
					JSONArray depositArr = depositsJson.optJSONArray("deposit");
					if (depositArr != null && depositArr.length() != 0) {
						for (int z = 0; z < depositArr.length(); z++) {
							
							JSONObject depositJson = depositArr.getJSONObject(z);
							Element depositElem = ownerDoc.createElementNS(NS_PAC, "tns:Deposit");
		
							Attr amountAfterTax = ownerDoc.createAttribute("DepositAmount");
							amountAfterTax.setValue(Integer.toString(depositJson.optInt("depositAmount")));
							depositElem.setAttributeNode(amountAfterTax);
	
							Attr attributeCurrency = ownerDoc.createAttribute("CurrencyCode");
							attributeCurrency.setValue(depositJson.optString("currencyCode"));
							depositElem.setAttributeNode(attributeCurrency);
	
							Attr attributeType = ownerDoc.createAttribute("Type");
							attributeType.setValue(depositJson.optString("type"));
							depositElem.setAttributeNode(attributeType);
		
							depositsElem.appendChild(depositElem);
						}
						activityService.appendChild(depositsElem);
					}
				}
				
				// Setting Promotions element
				JSONArray promotions = activityComponent.optJSONArray(JSON_PROP_PROMOTION);
				if (promotions != null && promotions.length() != 0) {
					Element promotionsElement = ownerDoc.createElementNS(NS_OTA, "ns:Promotions");
					for (int y = 0; y < promotions.length(); y++) {
						
						JSONObject promotion = promotions.getJSONObject(y);
						Element promotionELement = ownerDoc.createElementNS(NS_OTA, "ns:Promotion");

						Attr attributeDiscountCode = ownerDoc.createAttribute("DiscountCode");
						attributeDiscountCode.setValue(promotion.getString("discountCode"));
						promotionELement.setAttributeNode(attributeDiscountCode);

						Attr attributeAmountBT = ownerDoc.createAttribute("AmountBeforeTax");
						attributeAmountBT.setValue(promotion.getString("amountBeforeTax"));
						promotionELement.setAttributeNode(attributeAmountBT);
						
						Attr attributeAmountAT = ownerDoc.createAttribute("AmountAfterTax");
						attributeAmountAT.setValue(promotion.getString("amountAfterTax"));
						promotionELement.setAttributeNode(attributeAmountAT);

						Attr attributeCurrencyCode = ownerDoc.createAttribute("CurrencyCode");
						attributeCurrencyCode.setValue(promotion.getString("currencyCode"));
						promotionELement.setAttributeNode(attributeCurrencyCode);

						Attr attributeURL = ownerDoc.createAttribute("URL");
						attributeURL.setValue(promotion.getString("url"));
						promotionELement.setAttributeNode(attributeURL);

						Attr attributeType = ownerDoc.createAttribute("Type");
						attributeType.setValue(promotion.getString("type"));
						promotionELement.setAttributeNode(attributeType);

						promotionsElement.appendChild(promotionELement);
					}

					activityService.appendChild(promotionsElement);
				}
				
				// Setting Passenger ELement
                JSONArray passengerArr = activityComponent.getJSONArray("passenger");
                if (passengerArr != null && passengerArr.length() != 0) {
                    for (int k = 0; k < passengerArr.length(); k++) {
                        JSONObject guestCount = passengerArr.getJSONObject(k);
                        Element passengerElem = ownerDoc.createElementNS(NS_PAC, "tns:Passenger");
                        
                        passengerElem.setAttribute("MinimumAge",(guestCount.optString("minimumAge")));
                        passengerElem.setAttribute("MaximumAge",(guestCount.optString("maximumAge")));
                        passengerElem.setAttribute("MinimumPassengers",(guestCount.optString("minimumPassengers")));
                        passengerElem.setAttribute("MaximumPassengers",(guestCount.optString("maximumPassengers")));
                        passengerElem.setAttribute("PassengerTypeCode",(guestCount.optString("passengerTypeCode")));
                        
                        activityService.appendChild(passengerElem);
                    }
                }
				
				// Setting Guest Count ELement
                JSONArray guestCounts = activityComponent.getJSONArray("guestCount");
                if (guestCounts != null && guestCounts.length() != 0) {
                    Element guestCountsElement = ownerDoc.createElementNS(NS_PAC, "ns:GuestCounts");

                    for (int k = 0; k < guestCounts.length(); k++) {
                        JSONObject guestCount = guestCounts.getJSONObject(k);

                        Element guestCountElement = ownerDoc.createElementNS(NS_OTA, "ns:GuestCount");
                        guestCountElement.setAttribute("ResGuestRPH",Integer.toString(guestCount.optInt("resGuestRPH")));
                        guestCountsElement.appendChild(guestCountElement);
                    }
                    activityService.appendChild(guestCountsElement);
                }
                
                //TODO: Ground Service remaining
                
				// Appending activityService Element to Activity Component
				activityComponentElement.appendChild(activityService);
				
				// Appending Activity Component Elements to Activity Components
				activityComponentsElement.appendChild(activityComponentElement);
		}

			// Appending Transfer Components Element to TPA Element
			tpaElement.appendChild(activityComponentsElement);

			return tpaElement;
	}

	public static JSONObject getEmptyResponse(JSONObject reqHdrJson) {
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, new JSONObject());
        return resJson;
    }

	@Deprecated
	private static void calculatePrices(JSONObject requestJson, JSONObject resJson, JSONObject resSupplierCommJson,
			JSONObject resClientCommJson) {
		JSONObject briJson, ccommPkgDtlsJson;
		JSONArray ccommPkgDtlsJsonArr, ccommRoomDtlsJsonArr,applicableOnProductsArr, supplierCommercialDetailsArr = null;

		Map<String, String> scommToTypeMap = getSupplierCommercialsAndTheirType(resSupplierCommJson);
		Map<String, String> commToTypeMap = getClientCommercialsAndTheirType(resClientCommJson);

		String clientMarket = requestJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		String clientCcyCode = requestJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		// creating priceArray of unique elements to create Wem response
		Map<String, JSONObject> priceMap = new ConcurrentHashMap<String, JSONObject>();
		
		// retrieve passenger Qty from requestJson
		JSONObject reqCurrentDynamicPkg = new JSONObject();
		JSONObject resdynamicPkgJson = new JSONObject();
		JSONArray dynamicPkgArray = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		for (int a = 0; a < dynamicPkgArray.length(); a++) {
			resdynamicPkgJson = dynamicPkgArray.getJSONObject(a);
			String brandNameRes = resdynamicPkgJson.getString("brandName");
			String tourCodeRes = resdynamicPkgJson.getString("tourCode");
			String subTourCodeRes = resdynamicPkgJson.getString("subTourCode");

			String resUniqueKey = brandNameRes + tourCodeRes + subTourCodeRes;
			JSONArray reqDynamicPkgArray = requestJson.getJSONObject(JSON_PROP_REQBODY)
					.getJSONArray(JSON_PROP_DYNAMICPACKAGE);

			for (int i = 0; i < reqDynamicPkgArray.length(); i++) {

				JSONObject dynamicPkg = reqDynamicPkgArray.getJSONObject(i);
				String reqBrandName = dynamicPkg.getString("brandName");
				String reqTourCode = dynamicPkg.getString("tourCode");
				String reqSubTourCode = dynamicPkg.getString("subTourCode");

				String reqUniqueKey = reqBrandName + reqTourCode + reqSubTourCode;

				if (resUniqueKey.equals(reqUniqueKey)) {
					reqCurrentDynamicPkg = dynamicPkg;
					break;
				}

			}
		}

		// create Map of componentWise passenger Type quantity
		Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty = retrievePassengerQty(reqCurrentDynamicPkg,resdynamicPkgJson);
		// ------populate values from client commercials to generate wem response(i.e.
		// replacing the values of SI json response)

		JSONArray briArr = resClientCommJson.getJSONObject(JSON_PROP_RESULT).getJSONObject(JSON_PROP_EXECUTIONRES).getJSONArray(JSON_PROP_RESULTS).getJSONObject(0).getJSONObject(JSON_PROP_VALUE)
				.getJSONObject(JSON_PROP_CLIENTTRANRULES).getJSONArray(JSON_PROP_BUSSRULEINTAKE);
		Map<String, Integer> suppIndexMap = new HashMap<String, Integer>();
		
		BigDecimal totalTaxPrice = new BigDecimal(0);
		String suppId = "";
		for (int i = 0; i < briArr.length(); i++) {

			briJson = (JSONObject) briArr.get(i);
			ccommPkgDtlsJsonArr = briJson.getJSONArray(JSON_PROP_PACKAGEDETAILS);
			suppId = briJson.getJSONObject("commonElements").getString("supplier");

			for (int j = 0; j < dynamicPkgArray.length(); j++) {
				JSONObject dynamicPackageJson = dynamicPkgArray.getJSONObject(j);
				totalFare = new BigDecimal("0");
				String supplierIdSI = dynamicPackageJson.getString(JSON_PROP_SUPPLIERID);
				if (!(suppId.equalsIgnoreCase(supplierIdSI))) {
					continue;
				}
				int idx = (suppIndexMap.containsKey(supplierIdSI)) ? (suppIndexMap.get(supplierIdSI) + 1) : 0;
				suppIndexMap.put(supplierIdSI, idx);
				ccommPkgDtlsJson = ccommPkgDtlsJsonArr.getJSONObject(idx);

				JSONArray componentArr = dynamicPackageJson.getJSONArray(JSON_PROP_COMPONENTS);
				for (int k = 0; k < componentArr.length(); k++) {
					JSONObject componentJson = componentArr.getJSONObject(k);
					paxType = componentJson.getString("paxType");

					JSONArray priceArr = componentJson.getJSONArray(JSON_PROP_PRICE);
					priceMap = HolidaysUtil.retainSuppFaresMap(componentJson, priceMap);
					for (int l = 0; l < priceArr.length(); l++) {
						JSONObject priceJson = priceArr.getJSONObject(l);
						
						String rateDescriptionText = priceJson.getJSONObject(JSON_PROP_RATEDESC)
								.getString(JSON_PROP_TEXT);
						if(priceJson.getJSONObject(JSON_PROP_TOTAL).has(JSON_PROP_RATEDESC)) {
						
						
						// for roomDetails ---start
						ccommRoomDtlsJsonArr = ccommPkgDtlsJson.getJSONArray(JSON_PROP_ROOMDETAILS);

						if (rateDescriptionText.contains("Room")) {
							getRoomDetailsPrice(ccommRoomDtlsJsonArr,
									supplierCommercialDetailsArr, scommToTypeMap, commToTypeMap, clientMarket,clientCcyCode, priceMap, 
									componentWisePaxTypeQty, totalTaxPrice, suppId, priceJson,rateDescriptionText);

						}
						// roomDetails -- end
						// for applicable On products --start
						applicableOnProductsArr = ccommPkgDtlsJson.getJSONArray("applicableOnProducts");

						if (!(rateDescriptionText.contains("Room"))) {
							 getApplicableOnProductsPrice(applicableOnProductsArr,supplierCommercialDetailsArr,scommToTypeMap,commToTypeMap,clientMarket,
									clientCcyCode, priceMap, componentWisePaxTypeQty, totalTaxPrice, suppId, priceJson,rateDescriptionText);
						}
						
						// for applicable On products --end
					}}
					//Added SI prices from priceArray for whom commercials are not applied to totalFare 
					if (priceMap != null && !priceMap.isEmpty()) {
						
					totalFare = addSupplierFarestoTotalPrice(priceMap, componentWisePaxTypeQty);}} // Setting the Final price for the package
					JSONObject totalJson = dynamicPackageJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TOTAL);

				totalJson.put(JSON_PROP_AMOUNTAFTERTAX, totalFare);

			}

		}
	}

	private static void getApplicableOnProductsPrice(JSONArray applicableOnProductsArr,
			JSONArray supplierCommercialDetailsArr, Map<String, String> scommToTypeMap,
			Map<String, String> commToTypeMap, String clientMarket, String clientCcyCode,
			Map<String, JSONObject> priceMap, Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty,
			BigDecimal totalTaxPrice, String suppId, JSONObject priceJson, String rateDescriptionText) {

		JSONObject clientEntityCommJson, markupCalcJson, applicableOnProductsJson;
		JSONArray clientEntityCommJsonArr;

		BigDecimal amountAfterTaxSI = priceJson.getJSONObject(JSON_PROP_TOTAL).getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
		BigDecimal amountBeforeTaxSI = priceJson.getJSONObject(JSON_PROP_TOTAL).getBigDecimal(JSON_PROP_AMOUNTBEFORETAX);
		String dynamicPkgAction = priceJson.getJSONObject(JSON_PROP_TOTAL).getJSONObject(JSON_PROP_RATEDESC).getString(JSON_PROP_DYNAMICPKGACTION);
		String rateDescriptionName = priceJson.getJSONObject(JSON_PROP_TOTAL).getJSONObject(JSON_PROP_RATEDESC).getString(JSON_PROP_NAME);

		for (int a = 0; a < applicableOnProductsArr.length(); a++) {
			applicableOnProductsJson = applicableOnProductsArr.getJSONObject(a);
			String productName = applicableOnProductsJson.getString("productName");
			String passengerType = applicableOnProductsJson.getString("passengerType");

			// Checks if SI PaxType matches with CC passengerType

			if (passengerType.equalsIgnoreCase(paxType)) {
				clientEntityCommJsonArr = applicableOnProductsJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
				if (clientEntityCommJsonArr == null) { // TODO: Refine this warning message.
					// Maybe log some context information also.
					logger.warn("Client commercials calculations not found");
					continue;
				} else// This is to capture the comm type field from commercial head in entity
						// details
				{
					int len = clientEntityCommJsonArr.length();
					for (int x = 0; x < len; x++) {
						JSONObject ccommClientCommJson = clientEntityCommJsonArr.getJSONObject(x);
						ccommClientCommJson.put(JSON_PROP_COMMTYPE, commToTypeMap.get(ccommClientCommJson
								.optJSONObject(JSON_PROP_MARKUPCOMDTLS).getString(JSON_PROP_COMMNAME)));
					}

				}
				supplierCommercialDetailsArr = applicableOnProductsJson.optJSONArray(JSON_PROP_COMMDETAILS);

				if (supplierCommercialDetailsArr == null) {
					logger.warn(String.format("No supplier commercials found for supplier %s", suppId));
				}

				else// This is to capture the comm type field from commercial head in
					// suppCommercialRes
				{
					int len = supplierCommercialDetailsArr.length();
					for (int x = 0; x < len; x++) {
						JSONObject scommClientCommJson = supplierCommercialDetailsArr.getJSONObject(x);
						scommClientCommJson.put(JSON_PROP_COMMTYPE,scommToTypeMap.get(scommClientCommJson.getString(JSON_PROP_COMMNAME)));
					}
					// for multiple chain of entity take the latest commercials applied
					for (int y = (clientEntityCommJsonArr.length() - 1); y >= 0; y--) {

						clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(y);
						// TODO: In case of B2B, do we need to add additional receivable commercials
						// for all client hierarchy levels?
						JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);// take the array additionalcommercialDetails
						if (additionalCommsJsonArr != null) {
							for (int x = 0; x < additionalCommsJsonArr.length(); x++) {
								JSONObject additionalCommsJson = additionalCommsJsonArr.getJSONObject(x);// take object of additionalcommercialDetails array one by one
								String additionalCommName = additionalCommsJson.optString(JSON_PROP_COMMNAME);// fetch comm Name from additionalcommercialDetails object
								if (COMM_TYPE_RECEIVABLE.equals(commToTypeMap.get(additionalCommName))) {// is the additionalCommName receivable?
									String additionalCommCcy = additionalCommsJson.getString(JSON_PROP_COMMCCY);// get comm currency from additionalcommercialDetails Object
									BigDecimal additionalCommAmt = additionalCommsJson
											.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData
													.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket));
									totalPrice = totalPrice.add(additionalCommAmt);

								}
							}
						}
						markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMDTLS);
						if (markupCalcJson == null) {
							continue;
						}

						if (rateDescriptionText.contains("Night")) {
							String applicableOnStr = rateDescriptionText
									.substring(0, rateDescriptionText.indexOf("Night") + 5).replaceAll("[^a-zA-Z]", "");
							BigDecimal amountAfterTaxcc = applicableOnProductsJson.getBigDecimal(JSON_PROP_TOTALFARE);
							BigDecimal amountBeforeTaxcc = applicableOnProductsJson.getJSONObject(JSON_PROP_FAREBREAKUP)
									.getBigDecimal(JSON_PROP_BASEFARE);
							int amountAfterTaxvalue = (amountAfterTaxcc).compareTo(amountAfterTaxSI);
							int amountBeforeTaxvalue = amountBeforeTaxcc.compareTo(amountBeforeTaxSI);
							if (applicableOnStr.equalsIgnoreCase(productName) && amountAfterTaxvalue == 0
									&& amountBeforeTaxvalue == 0) {
								populateFinalPrice(markupCalcJson, priceJson, totalPrice, totalTaxPrice,
										componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName);
								priceMap.remove(rateDescriptionText);
							}
						}

						else if (rateDescriptionText.contains("Transfer") && (productName.contains("Transfer"))) {
							if (rateDescriptionText.contains("Arrival") && rateDescriptionText.contains("Transfer")) {

								populateFinalPrice(markupCalcJson, priceJson, totalPrice, totalTaxPrice,
										componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName);
								priceMap.remove(rateDescriptionText);
							} else if (rateDescriptionText.contains("Departure")
									&& rateDescriptionText.contains("Transfer")) {

								populateFinalPrice(markupCalcJson, priceJson, totalPrice, totalTaxPrice,
										componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName);
								priceMap.remove(rateDescriptionText);
							}
						}

						else if ((rateDescriptionText.contains("Extra") || rateDescriptionText.contains("Upgrade"))
								&& (productName.contains("Extra"))) {

							populateFinalPrice(markupCalcJson, priceJson, totalPrice, totalTaxPrice,
									componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName);
							priceMap.remove(rateDescriptionText);
						}

						else if ((rateDescriptionText.contains("Trip Protection")
								|| rateDescriptionText.contains("Insurance")) && (productName.contains("Insurance"))) {

							populateFinalPrice(markupCalcJson, priceJson, totalPrice, totalTaxPrice,
									componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName);
							priceMap.remove(rateDescriptionText);

						} else if (rateDescriptionText.contains("Surcharge") && (productName.contains("Surcharge"))) {

							populateFinalPrice(markupCalcJson, priceJson, totalPrice, totalTaxPrice,
									componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName);
							priceMap.remove(rateDescriptionText);
						}

					}

				}
			}
		}

	}

	private  static void getRoomDetailsPrice(JSONArray ccommRoomDtlsJsonArr, JSONArray supplierCommercialDetailsArr,
			Map<String, String> scommToTypeMap, Map<String, String> commToTypeMap, String clientMarket,
			String clientCcyCode, Map<String, JSONObject> priceMap,
			Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty, BigDecimal totalTaxPrice, String suppId,
			JSONObject priceJson, String rateDescriptionText) {
		
		JSONObject ccommRoomDtlsJson,clientEntityCommJson,markupCalcJson,ccommPaxDtlsJson;
		JSONArray clientEntityCommJsonArr,ccommPaxDtlsJsonArr ;
		
		BigDecimal amountAfterTaxSI = priceJson.getJSONObject(JSON_PROP_TOTAL)
				.getBigDecimal(JSON_PROP_AMOUNTAFTERTAX);
		BigDecimal amountBeforeTaxSI = priceJson.getJSONObject(JSON_PROP_TOTAL)
				.getBigDecimal(JSON_PROP_AMOUNTBEFORETAX);
		String dynamicPkgAction = priceJson.getJSONObject(JSON_PROP_TOTAL).getJSONObject(JSON_PROP_RATEDESC).getString(JSON_PROP_DYNAMICPKGACTION);
		String rateDescriptionName = priceJson.getJSONObject(JSON_PROP_TOTAL).getJSONObject(JSON_PROP_RATEDESC).getString(JSON_PROP_NAME);
		
		for (int m = 0; m < ccommRoomDtlsJsonArr.length(); m++) {
			ccommRoomDtlsJson = ccommRoomDtlsJsonArr.getJSONObject(m);

			String roomType = ccommRoomDtlsJson.getString(JSON_PROP_ROOMTYPE);

			ccommPaxDtlsJsonArr = ccommRoomDtlsJson.getJSONArray(JSON_PROP_PASSENGERDETAILS);

			for (int n = 0; n < ccommPaxDtlsJsonArr.length(); n++) {

				ccommPaxDtlsJson = ccommPaxDtlsJsonArr.getJSONObject(n);

				String passengerType = ccommPaxDtlsJson.getString("passengerType");

				// Checks if SI PaxType matches with CC passengerType
				if (passengerType.equalsIgnoreCase(paxType)) {

					clientEntityCommJsonArr = ccommPaxDtlsJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
					if (clientEntityCommJsonArr == null) {
						// TODO: Refine this warning message. Maybe log some context information
						// also.
						logger.warn("Client commercials calculations not found");
						continue;
					} else// This is to capture the comm type field from commercial head in entity
							// details
					{
						int len = clientEntityCommJsonArr.length();
						for (int x = 0; x < len; x++) {
							JSONObject ccommClientCommJson = clientEntityCommJsonArr.getJSONObject(x);
							ccommClientCommJson.put(JSON_PROP_COMMTYPE,commToTypeMap.get(ccommClientCommJson.optJSONObject(JSON_PROP_MARKUPCOMDTLS).getString(JSON_PROP_COMMNAME)));
						}

					}
					supplierCommercialDetailsArr = ccommPaxDtlsJson.optJSONArray(JSON_PROP_COMMDETAILS);
					if (supplierCommercialDetailsArr == null) {
						logger.warn(String.format("No supplier commercials found for supplier %s",
								suppId));
					}
					else// This is to capture the comm type field from commercial head in suppCommercialRes
					{   int len = supplierCommercialDetailsArr.length();
						for (int x = 0; x < len; x++) {
							JSONObject scommClientCommJson = supplierCommercialDetailsArr.getJSONObject(x);
							scommClientCommJson.put(JSON_PROP_COMMTYPE, scommToTypeMap.get(scommClientCommJson.getString(JSON_PROP_COMMNAME)));
						}
						// for multiple chain of entity take the latest commercials applied
						for (int o = (clientEntityCommJsonArr.length() - 1); o >= 0; o--) {

							clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(o);
							// TODO: In case of B2B, do we need to add additional receivable
							// commercials for all client hierarchy levels?
							JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);// take the array additionalcommercialDetails
							if (additionalCommsJsonArr != null) {
								for (int x = 0; x < additionalCommsJsonArr.length(); x++) {
									JSONObject additionalCommsJson = additionalCommsJsonArr.getJSONObject(x);// take object of additionalcommercialDetails array one by one
									String additionalCommName = additionalCommsJson.optString(JSON_PROP_COMMNAME);// fetch comm Name from additionalcommercialDetails object
									if (COMM_TYPE_RECEIVABLE.equals(commToTypeMap.get(additionalCommName))) {// is the additionalCommName receivable?
										String additionalCommCcy = additionalCommsJson.getString(JSON_PROP_COMMCCY);// get comm currency from additionalcommercialDetails Object
										BigDecimal additionalCommAmt = additionalCommsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(
														additionalCommCcy, clientCcyCode,clientMarket));
										totalPrice = totalPrice.add(additionalCommAmt);

									}
								}
							}
							markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMDTLS);
							if (markupCalcJson == null) {

								continue;
							}

							BigDecimal amountAfterTaxcc = ccommPaxDtlsJson.getBigDecimal(JSON_PROP_TOTALFARE);
							BigDecimal amountBeforeTaxcc = ccommPaxDtlsJson.getJSONObject(JSON_PROP_FAREBREAKUP).getBigDecimal(JSON_PROP_BASEFARE);
							int amountAfterTaxvalue = (amountAfterTaxcc).compareTo(amountAfterTaxSI);
							int amountBeforeTaxvalue = amountBeforeTaxcc.compareTo(amountBeforeTaxSI);
							if (rateDescriptionText.contains(roomType) && amountAfterTaxvalue == 0
									&& amountBeforeTaxvalue == 0) {

								populateFinalPrice(markupCalcJson, priceJson, totalPrice,
										totalTaxPrice, componentWisePaxTypeQty,dynamicPkgAction,rateDescriptionName);
								priceMap.remove(rateDescriptionText);
							}
						}
					}

				}

			}
		}
		
		
	}

	private static BigDecimal addSupplierFarestoTotalPrice(Map<String, JSONObject> priceMap,
			Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty) {
		for (Entry<String, JSONObject> entry : priceMap.entrySet()) 
		{ JSONObject priceMapJson = entry.getValue();
		BigDecimal paxCount = new BigDecimal(0);
		if(priceMapJson.getJSONObject(JSON_PROP_TOTAL).has(JSON_PROP_RATEDESC)) {
		String dynamicPkgAction = priceMapJson.getJSONObject(JSON_PROP_TOTAL).getJSONObject(JSON_PROP_RATEDESC).getString(JSON_PROP_DYNAMICPKGACTION);
		String rateDescriptionName = priceMapJson.getJSONObject(JSON_PROP_TOTAL).getJSONObject(JSON_PROP_RATEDESC).getString(JSON_PROP_NAME);
		
		if(dynamicPkgAction.contains(DYNAMICPKGACTION_HOTEL_TOUR)) {

			String[] arr= rateDescriptionName.split("\\s+");
			
			rateDescriptionName = (dynamicPkgAction+arr[0]).toLowerCase();
			
		}else if (dynamicPkgAction.contains("Night")) {
			if(dynamicPkgAction.contains(JSON_PROP_PRENIGHT)){
				rateDescriptionName = JSON_PROP_PRENIGHT.toLowerCase();
				}else if (dynamicPkgAction.contains(JSON_PROP_POSTNIGHT)){
					rateDescriptionName = JSON_PROP_POSTNIGHT.toLowerCase();
				}
			//rateDescriptionName = (dynamicPkgAction+rateDescriptionName.substring(rateDescriptionName.indexOf("(")+1,rateDescriptionName.indexOf(")"))).toLowerCase();
			//TODO :Check how to handle when supplier doesn't send roomType
			
		}else if (dynamicPkgAction.contains("Insurance")) {
			
			rateDescriptionName = (dynamicPkgAction+rateDescriptionName).toLowerCase();
		}else if (dynamicPkgAction.contains("Transfer")) {
			rateDescriptionName = dynamicPkgAction.toLowerCase();
		}
		
		
		BigDecimal amountAfterTax = priceMapJson.getJSONObject("total").optBigDecimal(JSON_PROP_AMOUNTAFTERTAX, new BigDecimal("0"));
		paxCount = getPaxCount(componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName, paxCount);
		totalFare = totalFare.add((amountAfterTax).multiply(paxCount));
		}
}		 return totalFare;
	}

	private static void populateFinalPrice(JSONObject markupCalcJson, JSONObject priceJson, BigDecimal totalPrice,
			BigDecimal totalTaxPrice, Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty,
			String dynamicPkgAction, String rateDescriptionName) {
		String roomType = null;
		if(dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_HOTEL_TOUR)||dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_CRUISE_TOUR)) {

			String[] arr= rateDescriptionName.split("\\s+");
			roomType=arr[0];
			rateDescriptionName = (dynamicPkgAction+arr[0]).toLowerCase();
			
		}else if (dynamicPkgAction.contains("Night")) {
			/*if(rateDescriptionName.contains("(")) {
			rateDescriptionName = (dynamicPkgAction+rateDescriptionName.substring(rateDescriptionName.indexOf("(")+1,rateDescriptionName.indexOf(")"))).toLowerCase();
		}else {*/ 
			//TODO :Check how to handle when supplier doesnt send roomType//}
			if(dynamicPkgAction.contains(JSON_PROP_PRENIGHT)){
			rateDescriptionName = JSON_PROP_PRENIGHT.toLowerCase();
			}else if (dynamicPkgAction.contains(JSON_PROP_POSTNIGHT)){
				rateDescriptionName = JSON_PROP_POSTNIGHT.toLowerCase();
			}
			}
			else if (dynamicPkgAction.contains("Insurance")) {
			
			rateDescriptionName = (dynamicPkgAction+rateDescriptionName).toLowerCase();
		}else if (dynamicPkgAction.contains("Transfer")) {
			rateDescriptionName = dynamicPkgAction.toLowerCase();
		}
		BigDecimal totalFareCC = markupCalcJson.optBigDecimal(JSON_PROP_TOTALFARE, new BigDecimal("0"));

		BigDecimal baseFareCC = markupCalcJson.getJSONObject(JSON_PROP_FAREBREAKUP).optBigDecimal(JSON_PROP_BASEFARE,
				new BigDecimal("0"));
		BigDecimal paxCount = new BigDecimal(0);

		JSONArray taxArr = markupCalcJson.getJSONObject(JSON_PROP_FAREBREAKUP).getJSONArray(JSON_PROP_TAXDETAILS);
		JSONObject totalJson = priceJson.getJSONObject(JSON_PROP_TOTAL);
		JSONObject baseJson = priceJson.getJSONObject("base");
		totalPrice = totalPrice.add(totalFareCC);

		JSONArray taxArraySI = totalJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
		totalTaxPrice = totalPrice.subtract(baseFareCC);
		totalJson.getJSONObject(JSON_PROP_TAXES).put(JSON_PROP_AMOUNT, totalTaxPrice);

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

		totalJson.put(JSON_PROP_AMOUNTAFTERTAX, totalPrice);
		totalJson.put(JSON_PROP_AMOUNTBEFORETAX, baseFareCC);
		baseJson.put(JSON_PROP_AMOUNTBEFORETAX, baseFareCC);
		// TODO : check whether we need to replace SI currency code with
		// markup
		// commercials currencycode
		// totalJson.put("currencyCode", currencyCode);
		paxCount = getPaxCount(componentWisePaxTypeQty, dynamicPkgAction, rateDescriptionName, paxCount);
		totalFare = totalFare.add((totalPrice).multiply(paxCount));

	}

	private static BigDecimal getPaxCount(Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty,
			String dynamicPkgAction, String rateDescriptionName, BigDecimal paxCount) {
		Iterator<Map.Entry<String, Map<String, BigDecimal>>> componentPaxTypeQty = componentWisePaxTypeQty.entrySet()
				.iterator();
		while (componentPaxTypeQty.hasNext()) {

			Map.Entry<String, Map<String, BigDecimal>> compPaxEntry = componentPaxTypeQty.next();
			if (!(compPaxEntry.getKey().contains("Transfer"))){
			if (compPaxEntry.getKey().equalsIgnoreCase(rateDescriptionName)) {
				Iterator<Map.Entry<String, BigDecimal>> paxTypeQty = compPaxEntry.getValue().entrySet().iterator();
				while (paxTypeQty.hasNext()) {
					Map.Entry<String, BigDecimal> paxTypeEntry = paxTypeQty.next();
					if (paxType.equalsIgnoreCase(paxTypeEntry.getKey())) {
						paxCount = paxTypeEntry.getValue();
					}

				}
			} }else if ((compPaxEntry.getKey().contains("Transfer"))){
				if ((compPaxEntry.getKey().contains(dynamicPkgAction.toLowerCase()))) {
					Iterator<Map.Entry<String, BigDecimal>> paxTypeQty = compPaxEntry.getValue().entrySet().iterator();
					while (paxTypeQty.hasNext()) {
						Map.Entry<String, BigDecimal> paxTypeEntry = paxTypeQty.next();
						if (paxType.equalsIgnoreCase(paxTypeEntry.getKey())) {
							paxCount = paxTypeEntry.getValue();
						}

					}
				}
			}
			
			if(!(componentWisePaxTypeQty.containsKey(rateDescriptionName))) {
				if ((compPaxEntry.getKey().equalsIgnoreCase("default"))) {
					Iterator<Map.Entry<String, BigDecimal>> paxTypeQty = compPaxEntry.getValue().entrySet().iterator();
					while (paxTypeQty.hasNext()) {
						Map.Entry<String, BigDecimal> paxTypeEntry = paxTypeQty.next();
						if (paxType.equalsIgnoreCase(paxTypeEntry.getKey())) {
							paxCount = paxTypeEntry.getValue();
						}

					}
				}
		}
		}
		return paxCount;
	}

	public static BigDecimal getPaxCountV2(Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty,
			String dynamicPkgAction, String rateDescriptionName, BigDecimal paxCount,String paxType) {
		Iterator<Map.Entry<String, Map<String, BigDecimal>>> componentPaxTypeQty = componentWisePaxTypeQty.entrySet()
				.iterator();
		while (componentPaxTypeQty.hasNext()) {

			Map.Entry<String, Map<String, BigDecimal>> compPaxEntry = componentPaxTypeQty.next();
			if (!(compPaxEntry.getKey().contains("Transfer"))){
			if (compPaxEntry.getKey().equalsIgnoreCase(rateDescriptionName)) {
			    paxInfoMap = compPaxEntry.getValue();
				Iterator<Map.Entry<String, BigDecimal>> paxTypeQty = compPaxEntry.getValue().entrySet().iterator();
				while (paxTypeQty.hasNext()) {
					Map.Entry<String, BigDecimal> paxTypeEntry = paxTypeQty.next();
					if (paxType.equalsIgnoreCase(paxTypeEntry.getKey())) {
						paxCount = paxTypeEntry.getValue();
					}

				}
			} }else if ((compPaxEntry.getKey().contains("Transfer"))){
				if ((compPaxEntry.getKey().contains(dynamicPkgAction.toLowerCase()))) {
					paxInfoMap = compPaxEntry.getValue();
					Iterator<Map.Entry<String, BigDecimal>> paxTypeQty = compPaxEntry.getValue().entrySet().iterator();
					while (paxTypeQty.hasNext()) {
						Map.Entry<String, BigDecimal> paxTypeEntry = paxTypeQty.next();
						if (paxType.equalsIgnoreCase(paxTypeEntry.getKey())) {
							paxCount = paxTypeEntry.getValue();
						}

					}
				}
			}
			
			if(!(componentWisePaxTypeQty.containsKey(rateDescriptionName))) {
				if ((compPaxEntry.getKey().equalsIgnoreCase("default"))) {
					paxInfoMap = compPaxEntry.getValue();
					Iterator<Map.Entry<String, BigDecimal>> paxTypeQty = compPaxEntry.getValue().entrySet().iterator();
					while (paxTypeQty.hasNext()) {
						Map.Entry<String, BigDecimal> paxTypeEntry = paxTypeQty.next();
						if (paxType.equalsIgnoreCase(paxTypeEntry.getKey())) {
							paxCount = paxTypeEntry.getValue();
						}

					}
				}
		}
		}
		return paxCount;
	}

	public static Map<String, String> getClientCommercialsAndTheirType(JSONObject clientCommResJson) {
		JSONArray commHeadJsonArr = null;
		JSONArray entDetaiJsonArray = null;
		JSONObject commHeadJson = null;
		JSONObject scommBRIJson = null;
		Map<String, String> commToTypeMap = new HashMap<String, String>();
		JSONArray scommBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);

		for (int i = 0; i < scommBRIJsonArr.length(); i++) {
			scommBRIJson = scommBRIJsonArr.getJSONObject(i);
			entDetaiJsonArray = scommBRIJson.getJSONArray(JSON_PROP_ENTITYDETAILS);
			for (int j = 0; j < entDetaiJsonArray.length(); j++) {
				commHeadJsonArr = entDetaiJsonArray.getJSONObject(j).getJSONArray(JSON_PROP_COMMHEAD);
				if (commHeadJsonArr == null) {
					logger.warn("No commercial heads found in supplier commercials");
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

	private static JSONArray getClientCommercialsBusinessRuleIntakeJSONArray(JSONObject clientCommResJson) {

		return clientCommResJson.getJSONObject(JSON_PROP_RESULT).getJSONObject(JSON_PROP_EXECUTIONRES)
				.getJSONArray(JSON_PROP_RESULTS).getJSONObject(0).getJSONObject("value")
				.getJSONObject(JSON_PROP_CLIENTTRANRULES).getJSONArray(JSON_PROP_BUSSRULEINTAKE);
	}

	public static Map<String, String> getSupplierCommercialsAndTheirType(JSONObject suppCommResJson) {
		JSONObject scommBRIJson, commHeadJson;
		JSONArray commHeadJsonArr = null;
		Map<String, String> suppCommToTypeMap = new HashMap<String, String>();
		JSONArray scommBRIJsonArr = getSupplierCommercialsBusinessRuleIntakeJSONArray(suppCommResJson);
		for (int i = 0; i < scommBRIJsonArr.length(); i++) {
			scommBRIJson = scommBRIJsonArr.getJSONObject(i);
			commHeadJsonArr = scommBRIJson.optJSONArray(JSON_PROP_COMMHEAD);
			if (commHeadJsonArr == null) {
				logger.warn("No commercial heads found in supplier commercials");
				continue;
			}

			for (int j = 0; j < commHeadJsonArr.length(); j++) {
				commHeadJson = commHeadJsonArr.getJSONObject(j);
				suppCommToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME),
						commHeadJson.getString(JSON_PROP_COMMTYPE));
			}
		}

		return suppCommToTypeMap;

	}

	private static JSONArray getSupplierCommercialsBusinessRuleIntakeJSONArray(JSONObject suppCommResJson) {
		return suppCommResJson.getJSONObject(JSON_PROP_RESULT).getJSONObject(JSON_PROP_EXECUTIONRES).getJSONArray(JSON_PROP_RESULTS).getJSONObject(0).getJSONObject(JSON_PROP_VALUE).getJSONObject(JSON_PROP_SUPPTRANRULES).getJSONArray(JSON_PROP_BUSSRULEINTAKE);
	}

	public static Map<String, Map<String, BigDecimal>> retrievePassengerQty(JSONObject reqCurrentDynamicPkg,
			JSONObject resdynamicPkgJson) {

		Map<String, String> paxTypeMap = new HashMap<String, String>();
		Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty = new HashMap<String, Map<String, BigDecimal>>();
		String productType = null;

		// to find which resGuestNumber corresponds to which paxType
		JSONArray resGuestArr = reqCurrentDynamicPkg.getJSONArray("resGuests");
		for (int j = 0; j < resGuestArr.length(); j++) {

			JSONObject resGuestJson = resGuestArr.getJSONObject(j);

			String resGuestNumber = resGuestJson.getString("resGuestRPH");
			String paxType = resGuestJson.getString("paxType");
			paxTypeMap.put(resGuestNumber, paxType);
		}
		JSONObject componentJson = reqCurrentDynamicPkg.getJSONObject(JSON_PROP_COMPONENTS);
		
		// For Hotel Component
		JSONArray hotelCompArr = componentJson.optJSONArray(JSON_PROP_HOTEL_COMPONENT);
		for (int k = 0; k < hotelCompArr.length(); k++) {

			String dynamicPkgAction = hotelCompArr.getJSONObject(k).getString(JSON_PROP_DYNAMICPKGACTION);
			JSONArray roomStayArr = hotelCompArr.getJSONObject(k).getJSONObject("roomStays").getJSONArray("roomStay");
			for (int l = 0; l < roomStayArr.length(); l++) {
				JSONObject roomStayJSon = roomStayArr.getJSONObject(l);
				
				if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_HOTEL_TOUR))
					productType =  (dynamicPkgAction+roomStayJSon.getString("roomType")+l).toLowerCase();
				else if (dynamicPkgAction.equalsIgnoreCase(DYNAMICPKGACTION_HOTEL_PRE))
					productType =  (JSON_PROP_PRENIGHT+roomStayJSon.getString("roomType")+l).toLowerCase();
				else if (dynamicPkgAction.contains(DYNAMICPKGACTION_HOTEL_POST))
					productType = (JSON_PROP_POSTNIGHT+roomStayJSon.getString("roomType")+l).toLowerCase();
				
				getcomponentWisePaxTypeQty(paxTypeMap, componentWisePaxTypeQty, productType, roomStayJSon);
			}
		}

		// for cruise component
		JSONArray cruiseCompRqArr = componentJson.optJSONArray(JSON_PROP_CRUISE_COMPONENT);
		
			if (cruiseCompRqArr.length() > 0) {
				for (int l = 0; l < cruiseCompRqArr.length(); l++) {
					String dynamicPkgActionCruise = cruiseCompRqArr.getJSONObject(l).getString(JSON_PROP_DYNAMICPKGACTION);
					JSONArray categoryOptionsArr=new JSONArray(),categoryOptionArr=new JSONArray();
					if (dynamicPkgActionCruise.equals(DYNAMICPKGACTION_CRUISE_PRE) || dynamicPkgActionCruise.equals(DYNAMICPKGACTION_CRUISE_POST)) {
						JSONObject roomStays = cruiseCompRqArr.getJSONObject(l).getJSONObject("roomStays");
						categoryOptionsArr.put(roomStays);
					}
					else
						categoryOptionsArr = cruiseCompRqArr.getJSONObject(l).getJSONArray(JSON_PROP_CATEGORYOPTIONS);
					
					for (int m = 0; m < categoryOptionsArr.length(); m++) {

						//To handle Prenight /Postnight from WEM request to form proper cruise prent /postnt xml req --Priti
						if(dynamicPkgActionCruise.equals(DYNAMICPKGACTION_CRUISE_PRE) || dynamicPkgActionCruise.equals(DYNAMICPKGACTION_CRUISE_POST))
							categoryOptionArr = categoryOptionsArr.getJSONObject(m).getJSONArray("roomStay");
						else
							categoryOptionArr = categoryOptionsArr.getJSONObject(m).getJSONArray(JSON_PROP_CATEGORYOPTION);
						
						for (int n = 0; n < categoryOptionArr.length(); n++) {
							JSONObject categoryOptionJson = categoryOptionArr.getJSONObject(n);

							if (dynamicPkgActionCruise.equalsIgnoreCase(DYNAMICPKGACTION_CRUISE_TOUR)) {
								//name is stored with index so that it can handle 2 twin rooms for eg and key is unique
								if(categoryOptionJson.has(JSON_PROP_TYPE))
									productType =  (dynamicPkgActionCruise+categoryOptionJson.getString(JSON_PROP_TYPE)+m+n).toLowerCase();
								else
									productType =  (dynamicPkgActionCruise+categoryOptionJson.getString(JSON_PROP_CABINTYPE)+m+n).toLowerCase();
							}
							else if (dynamicPkgActionCruise.equalsIgnoreCase(DYNAMICPKGACTION_CRUISE_PRE))
								productType =  (JSON_PROP_PRENIGHT+categoryOptionJson.getString(JSON_PROP_ROOMTYPE)+n).toLowerCase();
							else if (dynamicPkgActionCruise.contains(DYNAMICPKGACTION_CRUISE_POST))
								productType =  (JSON_PROP_POSTNIGHT+categoryOptionJson.getString(JSON_PROP_ROOMTYPE)+n).toLowerCase();

							getcomponentWisePaxTypeQty(paxTypeMap, componentWisePaxTypeQty, productType,
									categoryOptionJson);

						}
					}
			}
		}
			
		

		// for Insurance component
		JSONArray insuranceCompArr = componentJson.optJSONArray("insuranceComponent");
		if(insuranceCompArr != null && insuranceCompArr.length()!=0)
		for (int n = 0; n < insuranceCompArr.length(); n++) {
			String dynamicPkgAction = insuranceCompArr.getJSONObject(n).getString("dynamicPkgAction");
			JSONObject insuranceJson = insuranceCompArr.getJSONObject(n);
			productType = (dynamicPkgAction+insuranceJson.getJSONObject("insCoverageDetail").getString("description")).toLowerCase();
			getcomponentWisePaxTypeQty(paxTypeMap, componentWisePaxTypeQty, productType, insuranceJson);
		}
		
		// for Transfer component
		JSONArray transferComponentArr = componentJson.optJSONArray("transferComponent");
		if(transferComponentArr != null && transferComponentArr.length()!=0)
		for (int p = 0; p < transferComponentArr.length(); p++) {

			String dynamicPkgActionTransfer = transferComponentArr.getJSONObject(p).getString("dynamicPkgAction");

			if (dynamicPkgActionTransfer.equalsIgnoreCase("PackageDepartureTransfer"))
				productType = dynamicPkgActionTransfer.toLowerCase();
			else if (dynamicPkgActionTransfer.equalsIgnoreCase("PackageArrivalTransfer"))
				productType = dynamicPkgActionTransfer.toLowerCase();

			JSONArray groundServiceArr = transferComponentArr.getJSONObject(p).getJSONArray("groundService");
			for (int q = 0; q < groundServiceArr.length(); q++) {
				JSONObject groundServiceJson = groundServiceArr.getJSONObject(q);
				getcomponentWisePaxTypeQty(paxTypeMap, componentWisePaxTypeQty, productType, groundServiceJson);
			}
		}
		// for other applicableOnProducts which are not in request
				JSONArray paxInfoArr = reqCurrentDynamicPkg.getJSONArray("paxInfo");
				Map<String, BigDecimal> defaultPaxTypeMap = new HashMap<String, BigDecimal>();
				for (int k = 0; k < paxInfoArr.length(); k++) {
					JSONObject paxInfoJson = paxInfoArr.getJSONObject(k);
					String paxType = paxInfoJson.getString("paxType");
					BigDecimal paxQty = new BigDecimal(paxInfoJson.optInt("quantity"));
					defaultPaxTypeMap.put(paxType, paxQty);
					productType = "default";
				}
				componentWisePaxTypeQty.put(productType, defaultPaxTypeMap);
		return componentWisePaxTypeQty;

	}

	private static void getcomponentWisePaxTypeQty(Map<String, String> paxTypeMap,
			Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty, String productType,
			JSONObject compGuestJSon) {
		JSONArray guestCountArr = compGuestJSon.getJSONArray("guestCount");
		Map<String, BigDecimal> paxTypeQtyMap = new HashMap<String, BigDecimal>();
		BigDecimal paxQty = new BigDecimal(0);
		BigDecimal paxCnt = new BigDecimal(1);

		for (int m = 0; m < guestCountArr.length(); m++) {
			JSONObject guestCountJson = guestCountArr.getJSONObject(m);
			String resGuestNumber = String.valueOf(guestCountJson.get("resGuestRPH"));

			Iterator<Map.Entry<String, String>> paxTypeIter = paxTypeMap.entrySet().iterator();
			while (paxTypeIter.hasNext()) {
				Map.Entry<String, String> priceEntry = paxTypeIter.next();
				if (resGuestNumber.equalsIgnoreCase(priceEntry.getKey())) {
					if (paxTypeQtyMap.containsKey(priceEntry.getValue())) {
						paxCnt = paxCnt.add(paxQty);
						paxTypeQtyMap.put(priceEntry.getValue(), paxCnt);
					}
					else {
						paxQty = new BigDecimal(1);
						paxTypeQtyMap.put(priceEntry.getValue(), paxQty);
					}

				}
			}
		}
		componentWisePaxTypeQty.put(productType, paxTypeQtyMap);
	}

	public static Element getSupplierCredentialsList(Document ownerDoc, UserContext usrCtx, String supplierID,
			int sequence, Element supplierCredentialsList) throws Exception {
		ProductSupplier productSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_HOLIDAYS, PROD_CATEG_HOLIDAYS,supplierID);

		if (productSupplier == null)
			throw new Exception(String.format("Product supplier %s not found for user/client", supplierID));

		// Setting the sequence, supplier credentials and urls for the header
		Element supplierCredentials = productSupplier.toElement(ownerDoc, sequence);
		supplierCredentialsList.appendChild(supplierCredentials);

		return supplierCredentialsList;
	}

	public static Element getResGuestElement(Document ownerDoc, JSONObject resGuest) {
		Element resGuestElement = ownerDoc.createElementNS(NS_OTA, "ns:ResGuest");

		Attr attributeResGuestRPH = ownerDoc.createAttribute("ResGuestRPH");
		attributeResGuestRPH.setValue(resGuest.getString("resGuestRPH"));
		resGuestElement.setAttributeNode(attributeResGuestRPH);

		Attr attributePrimaryIndicator = ownerDoc.createAttribute("PrimaryIndicator");
		attributePrimaryIndicator.setValue(Boolean.toString(resGuest.getBoolean("primaryIndicator")));
		resGuestElement.setAttributeNode(attributePrimaryIndicator);
	
		Attr attributeAge = ownerDoc.createAttribute("Age");
		attributeAge.setValue(resGuest.getString("age"));
		resGuestElement.setAttributeNode(attributeAge);
		
		Element profilesElement = ownerDoc.createElementNS(NS_OTA, "ns:Profiles");
		Element profileInfoElement = ownerDoc.createElementNS(NS_OTA, "ns:ProfileInfo");
		Element profileElement = ownerDoc.createElementNS(NS_OTA, "ns:Profile");
		Element customerElement = ownerDoc.createElementNS(NS_OTA, "ns:Customer");
		
		//Added ProfileType to profileElement 
		Attr profileType = ownerDoc.createAttribute("ProfileType");
		profileType.setValue(resGuest.getString("paxType"));
		profileElement.setAttributeNode(profileType);
						
		Attr attributeBirthDate = ownerDoc.createAttribute("BirthDate");
		attributeBirthDate.setValue(resGuest.getString("dob"));
		customerElement.setAttributeNode(attributeBirthDate);

		Attr attributeGender = ownerDoc.createAttribute("Gender");
		attributeGender.setValue(resGuest.getString("gender"));
		customerElement.setAttributeNode(attributeGender);

		// Setting person name element
		String personName = resGuest.getString("firstName");

		if (personName != null && personName.length() != 0) {
			Element personNameElement = ownerDoc.createElementNS(NS_OTA, "ns:PersonName");

			Element givenNameElement = ownerDoc.createElementNS(NS_OTA, "ns:GivenName");
			givenNameElement.setTextContent(resGuest.getString("firstName"));
			personNameElement.appendChild(givenNameElement);

			Element middleNameElement = ownerDoc.createElementNS(NS_OTA, "ns:MiddleName");
			middleNameElement.setTextContent(resGuest.getString("middleName"));
			personNameElement.appendChild(middleNameElement);

			Element surnameElement = ownerDoc.createElementNS(NS_OTA, "ns:Surname");
			surnameElement.setTextContent(resGuest.getString("surname"));
			personNameElement.appendChild(surnameElement);

			Element nameTitleElement = ownerDoc.createElementNS(NS_OTA, "ns:NameTitle");
			nameTitleElement.setTextContent(resGuest.getString("title"));
			personNameElement.appendChild(nameTitleElement);

			customerElement.appendChild(personNameElement);
		}

		// Setting telephone element
		JSONArray contactInfoArray = resGuest.getJSONArray("contactDetails");
		if (contactInfoArray != null && contactInfoArray.length() != 0) {
			for(int g=0;g<contactInfoArray.length();g++)
			{
			    JSONObject contactInfo = contactInfoArray.getJSONObject(g);
			    //Setting Telephone number
			    Element telephoneElement = ownerDoc.createElementNS(NS_OTA, "ns:Telephone");
	        	Attr attributePhoneNumber = ownerDoc.createAttribute("PhoneNumber");
	        	attributePhoneNumber.setValue(contactInfo.getJSONObject("contactInfo").getString("phoneNumber"));
	        	telephoneElement.setAttributeNode(attributePhoneNumber);
	        	telephoneElement.setAttribute("PhoneUseType", contactInfo.getJSONObject("contactInfo").getString("contactType"));
	        	telephoneElement.setAttribute("AreaCityCode", contactInfo.getJSONObject("contactInfo").getString("areaCityCode"));
	        	telephoneElement.setAttribute("CountryAccessCode", contactInfo.getJSONObject("contactInfo").getString("countryCode"));
	        	customerElement.appendChild(telephoneElement);
	        
	    		// Setting Email element
	    		Element emailElement = ownerDoc.createElementNS(NS_OTA, "ns:Email");
	    		emailElement.setTextContent(contactInfo.getJSONObject("contactInfo").getString("email"));
	    		customerElement.appendChild(emailElement);
			}
		}

		// Setting Address element
		JSONObject addressDetails = resGuest.getJSONObject("addressDetails");
		if (addressDetails != null && addressDetails.length() != 0) {
			Element addressElement = ownerDoc.createElementNS(NS_OTA, "ns:Address");

			Element addrLine1Element = ownerDoc.createElementNS(NS_OTA, "ns:AddressLine");
			addrLine1Element.setTextContent(addressDetails.getString("addrLine1"));
			addressElement.appendChild(addrLine1Element);

			Element addrLine2Element = ownerDoc.createElementNS(NS_OTA, "ns:AddressLine");
			addrLine2Element.setTextContent(addressDetails.getString("addrLine2"));
			addressElement.appendChild(addrLine2Element);

			Element cityElement = ownerDoc.createElementNS(NS_OTA, "ns:CityName");
			cityElement.setTextContent(addressDetails.getString("city"));
			addressElement.appendChild(cityElement);

			Element zipElement = ownerDoc.createElementNS(NS_OTA, "ns:PostalCode");
			zipElement.setTextContent(addressDetails.getString("zip"));
			addressElement.appendChild(zipElement);

			Element stateElement = ownerDoc.createElementNS(NS_OTA, "ns:StateProv");
			stateElement.setTextContent(addressDetails.getString("state"));
			addressElement.appendChild(stateElement);
			
			Element countryElement = ownerDoc.createElementNS(NS_OTA, "ns:CountryName");
			countryElement.setTextContent(addressDetails.getString("country"));
			addressElement.appendChild(countryElement);

			customerElement.appendChild(addressElement);
		}

		// Setting Document Element and appending it to customer element
		JSONArray documentInfo = resGuest.getJSONArray("documentInfo");
		if (documentInfo != null && documentInfo.length() != 0) {
			for (int i = 0; i < documentInfo.length(); i++) {
				JSONObject document = documentInfo.getJSONObject(i);

				Element documentElement = ownerDoc.createElementNS(NS_OTA, "ns:Document");

				Attr attributeDocType = ownerDoc.createAttribute("DocType");
				attributeDocType.setValue(document.getString("docType"));
				documentElement.setAttributeNode(attributeDocType);

				Attr attributeDocID = ownerDoc.createAttribute("DocID");
				attributeDocID.setValue(document.getString("docNumber"));
				documentElement.setAttributeNode(attributeDocID);

				customerElement.appendChild(documentElement);
			}
		}

		// Appending elements in backward sequencial order
		profileElement.appendChild(customerElement);
		profileInfoElement.appendChild(profileElement);
		profilesElement.appendChild(profileInfoElement);
		resGuestElement.appendChild(profilesElement);

		// Setting Document Element and appending it to customer element
		JSONObject specialRequests = resGuest.getJSONObject("specialRequests");

		if (specialRequests != null && specialRequests.length() != 0) {
			JSONArray specialRequestInfo = specialRequests.getJSONArray("specialRequestInfo");

			if (specialRequestInfo != null && specialRequestInfo.length() != 0) {
				Element specialRequestsElement = ownerDoc.createElementNS(NS_OTA, "ns:SpecialRequests");

				for (int i = 0; i < specialRequestInfo.length(); i++) {
					JSONObject specialRequest = specialRequestInfo.getJSONObject(i);

					Element specialRequestElement = ownerDoc.createElementNS(NS_OTA, "ns:SpecialRequest");

					Attr attributeCode = ownerDoc.createAttribute("RequestCode");
					attributeCode.setValue(specialRequest.getString("code"));
					specialRequestElement.setAttributeNode(attributeCode);

					Attr attributeName = ownerDoc.createAttribute("Name");
					attributeName.setValue(specialRequest.getString("name"));
					specialRequestElement.setAttributeNode(attributeName);

					specialRequestsElement.appendChild(specialRequestElement);
				}
				resGuestElement.appendChild(specialRequestsElement);
			}
		}
		return resGuestElement;
	}

	public static Element getGlobalInfoElement(Document ownerDoc, JSONObject globalInfo, Element globalInfoElement) {
		
		//Setting Tour Details
		JSONObject dynamicPkgID = globalInfo.getJSONObject("dynamicPkgID");
		JSONObject tourDetails = dynamicPkgID.getJSONObject("tourDetails");
		
		Element dynamicPkgIDs = ownerDoc.createElementNS(NS_OTA, "ns:DynamicPkgIDs");
		Element dynamicPkgIDElement = ownerDoc.createElementNS(NS_OTA, "ns:DynamicPkgID");
		Element companyName = ownerDoc.createElementNS(NS_OTA, "ns:CompanyName");
		Element tpa_Extensions = ownerDoc.createElementNS(NS_PAC, "pac:TPA_Extensions");
		Element pkgs_TPA = ownerDoc.createElementNS(NS_PAC, "pac:Pkgs_TPA");
		Element tourDetailsElement = ownerDoc.createElementNS(NS_PAC, "pac:TourDetails");
		
		Element tourName = ownerDoc.createElementNS(NS_PAC, "pac:TourName");
		tourName.setTextContent(tourDetails.optString("tourName"));
		tourDetailsElement.appendChild(tourName);
		
		Element tourStartCity = ownerDoc.createElementNS(NS_PAC, "pac:TourStartCity");
		tourStartCity.setTextContent(tourDetails.optString("tourStartCity"));
		tourDetailsElement.appendChild(tourStartCity);
		
		Element tourEndCity = ownerDoc.createElementNS(NS_PAC, "pac:TourEndCity");
		tourEndCity.setTextContent(tourDetails.optString("tourEndCity"));
		tourDetailsElement.appendChild(tourEndCity);
		
		Element productCodeElem = ownerDoc.createElementNS(NS_PAC, "pac:ProductCode");
		productCodeElem.setTextContent(tourDetails.optString("productCode"));
		tourDetailsElement.appendChild(productCodeElem);
		
		Element tourRefID = ownerDoc.createElementNS(NS_PAC, "pac:TourRefID");
		tourRefID.setTextContent(tourDetails.optString("tourRefID"));
		tourDetailsElement.appendChild(tourRefID);
		
		//TourStart
		JSONObject tourStart = tourDetails.optJSONObject("tourStartAddress");
		if(tourStart!=null && tourStart.length()>0) {
			Element tourStartAddress = ownerDoc.createElementNS(NS_PAC, "tns:TourStartAddress");
			
			Element sStreet = ownerDoc.createElementNS(NS_PAC, "tns:Street");
			sStreet.setTextContent(tourStart.optString("street"));
			tourStartAddress.appendChild(sStreet);
	
			Element sCity = ownerDoc.createElementNS(NS_PAC, "tns:City");
			sCity.setTextContent(tourStart.optString("city"));
			tourStartAddress.appendChild(sCity);
			
			Element sCountry = ownerDoc.createElementNS(NS_PAC, "tns:Country");
			Element sCCode = ownerDoc.createElementNS(NS_PAC, "tns:Code");
			Element sCName = ownerDoc.createElementNS(NS_PAC, "tns:Name");
			
			sCCode.setTextContent(tourStart.optString("code"));
			sCName.setTextContent(tourStart.optString("name"));
			sCountry.appendChild(sCCode);
			sCountry.appendChild(sCName);
			tourStartAddress.appendChild(sCountry);
			
			Element sPostal_zip = ownerDoc.createElementNS(NS_PAC, "tns:postal_zip");
			sPostal_zip.setTextContent(tourStart.optString("postal_zip"));
			tourStartAddress.appendChild(sPostal_zip);
			
			tourDetailsElement.appendChild(tourStartAddress);
		}
		
		//TourEnd
		JSONObject tourEnd = tourDetails.optJSONObject("tourEndAddress");
		if(tourEnd!=null && tourEnd.length()>0) {
			Element tourEndAddress = ownerDoc.createElementNS(NS_PAC, "tns:TourStartAddress");
			
			Element eStreet = ownerDoc.createElementNS(NS_PAC, "tns:Street");
			eStreet.setTextContent(tourEnd.optString("street"));
			tourEndAddress.appendChild(eStreet);
	
			Element eCity = ownerDoc.createElementNS(NS_PAC, "tns:City");
			eCity.setTextContent(tourEnd.optString("city"));
			tourEndAddress.appendChild(eCity);
			
			Element eCountry = ownerDoc.createElementNS(NS_PAC, "tns:Country");
			Element eCCode = ownerDoc.createElementNS(NS_PAC, "tns:Code");
			Element eCName = ownerDoc.createElementNS(NS_PAC, "tns:Name");
			
			eCCode.setTextContent(tourEnd.optString("code"));
			eCName.setTextContent(tourEnd.optString("name"));
			eCountry.appendChild(eCCode);
			eCountry.appendChild(eCName);
			tourEndAddress.appendChild(eCountry);
			
			Element ePostal_zip = ownerDoc.createElementNS(NS_PAC, "tns:postal_zip");
			ePostal_zip.setTextContent(tourEnd.optString("postal_zip"));
			tourEndAddress.appendChild(ePostal_zip);
			
			tourDetailsElement.appendChild(tourEndAddress);
		}
		
		//Setting IDInfo
		JSONArray id_Infos = tourDetails.optJSONArray("idInfo");
		if(id_Infos!=null && id_Infos.length()>0) {
			for(int i=0;i<id_Infos.length();i++) {
				JSONObject id_Info = id_Infos.getJSONObject(i);
				Element idInfoElem = ownerDoc.createElementNS(NS_PAC, "tns:IDInfo");
				
				Element id = ownerDoc.createElementNS(NS_PAC, "tns:ID");
				id.setTextContent(id_Info.optString("id"));
				idInfoElem.appendChild(id);
		
				Element type = ownerDoc.createElementNS(NS_PAC, "tns:Type");
				type.setTextContent(id_Info.optString("type"));
				idInfoElem.appendChild(type);
				
				Element name = ownerDoc.createElementNS(NS_PAC, "tns:Name");
				name.setTextContent(id_Info.optString("name"));
				idInfoElem.appendChild(name);
				
				Element subType = ownerDoc.createElementNS(NS_PAC, "tns:SubType");
				subType.setTextContent(id_Info.optString("subType"));
				idInfoElem.appendChild(subType);
				
				Element url = ownerDoc.createElementNS(NS_PAC, "tns:URL");
				url.setTextContent(id_Info.optString("url"));
				idInfoElem.appendChild(url);
				
				Element isAddon = ownerDoc.createElementNS(NS_PAC, "tns:IsAddon");
				isAddon.setTextContent(id_Info.optString("isAddon"));
				idInfoElem.appendChild(isAddon);
				
				Element startDate = ownerDoc.createElementNS(NS_PAC, "tns:StartDate");
				startDate.setTextContent(id_Info.optString("startDate"));
				idInfoElem.appendChild(startDate);
				
				Element endDate = ownerDoc.createElementNS(NS_PAC, "tns:EndDate");
				endDate.setTextContent(id_Info.optString("endDate"));
				idInfoElem.appendChild(endDate);
				
				tourDetailsElement.appendChild(idInfoElem);
			}
		}
		
		pkgs_TPA.appendChild(tourDetailsElement);
		tpa_Extensions.appendChild(pkgs_TPA);
		
		Attr companyShortName = ownerDoc.createAttribute("CompanyShortName");
		companyShortName.setValue(dynamicPkgID.getString("companyName"));
		companyName.setAttributeNode(companyShortName);
		
		dynamicPkgIDElement.appendChild(companyName);
		dynamicPkgIDElement.appendChild(tpa_Extensions);
		
		Attr id = ownerDoc.createAttribute("ID");
		id.setValue(dynamicPkgID.getString("id"));
		dynamicPkgIDElement.setAttributeNode(id);
		
		Attr id_Context = ownerDoc.createAttribute("ID_Context");
		id_Context.setValue(dynamicPkgID.getString("id_Context"));
		dynamicPkgIDElement.setAttributeNode(id_Context);
		
		dynamicPkgIDs.appendChild(dynamicPkgIDElement);
		globalInfoElement.appendChild(dynamicPkgIDs);
		
		// Setting Comments Element
		JSONArray comment = globalInfo.getJSONArray(JSON_PROP_COMMENT);

		if (comment != null && comment.length() != 0) {
			Element commentsElement = ownerDoc.createElementNS(NS_OTA, "ns:Comments");
			
			for (int i = 0; i < comment.length(); i++) {
				JSONObject comm = comment.getJSONObject(i);

				Element commentElement = ownerDoc.createElementNS(NS_OTA, "ns:Comment");

				Attr attributeName = ownerDoc.createAttribute("Name");
				attributeName.setValue(comm.getString("name"));
				commentElement.setAttributeNode(attributeName);

				Element textElement = ownerDoc.createElementNS(NS_OTA, "ns:Text");
				textElement.setTextContent(comm.getString("text"));
				commentElement.appendChild(textElement);

				commentsElement.appendChild(commentElement);
			}
			globalInfoElement.appendChild(commentsElement);
		}

		// Setting Time Span
        JSONObject timeSpan = globalInfo.getJSONObject("timeSpan");

        if (timeSpan != null && timeSpan.length() != 0) {
            Element timeSpanElement = ownerDoc.createElementNS(NS_PAC, "tns:TimeSpan");

            Attr attributeStart = ownerDoc.createAttribute("Start");
            attributeStart.setValue(timeSpan.optString("start"));
            timeSpanElement.setAttributeNode(attributeStart);

            Attr attributeEnd = ownerDoc.createAttribute("End");
            attributeEnd.setValue(timeSpan.optString("end"));
            timeSpanElement.setAttributeNode(attributeEnd);
            
            Attr attributeTravelStartDate = ownerDoc.createAttribute("TravelStartDate");
            attributeTravelStartDate.setValue(timeSpan.optString("travelStartDate"));
            timeSpanElement.setAttributeNode(attributeTravelStartDate);
            
            Attr attributeTravelEndDate = ownerDoc.createAttribute("TravelEndDate");
            attributeTravelEndDate.setValue(timeSpan.optString("travelEndDate"));
            timeSpanElement.setAttributeNode(attributeTravelEndDate);

            globalInfoElement.appendChild(timeSpanElement);
        }
		
		// Setting fees element
		JSONArray fees = globalInfo.getJSONArray(JSON_PROP_FEES);

		if (fees != null && fees.length() != 0) {
			Element feesElement = ownerDoc.createElementNS(NS_OTA, "ns:Fees");

			for (int j = 0; j < fees.length(); j++) {
				JSONObject fee = fees.getJSONObject(j);

				Element feeElement = ownerDoc.createElementNS(NS_OTA, "ns:Fee");

				Attr attributeCode = ownerDoc.createAttribute("Code");
				attributeCode.setValue(fee.getString("feeCode"));
				feeElement.setAttributeNode(attributeCode);

				Attr attributeQuantity = ownerDoc.createAttribute("MaxChargeUnitApplies");
				attributeQuantity.setValue(fee.getString("quantity"));
				feeElement.setAttributeNode(attributeQuantity);

				Element descriptionELement = ownerDoc.createElementNS(NS_OTA, "ns:Description");

				Attr attributeName = ownerDoc.createAttribute("Name");
				attributeName.setValue(fee.getString("feeName"));
				descriptionELement.setAttributeNode(attributeName);

				Element textElement = ownerDoc.createElementNS(NS_OTA, "ns:Text");
				textElement.setTextContent(fee.getString("text"));

				descriptionELement.appendChild(textElement);
				feeElement.appendChild(descriptionELement);
				feesElement.appendChild(feeElement);
			}
			globalInfoElement.appendChild(feesElement);
		}

		// Setting booking rules element
		JSONArray bookingRules = globalInfo.getJSONArray(JSON_PROP_BOOKINGRULE);
		Element bookingRulesElement = ownerDoc.createElementNS(NS_OTA, "ns:BookingRules");
		if (bookingRules != null && bookingRules.length() != 0) {
			for (int k = 0; k < bookingRules.length(); k++) {
				JSONObject bookingRule = bookingRules.getJSONObject(k);

				Element bookingRuleElement = ownerDoc.createElementNS(NS_OTA, "ns:BookingRule");
				Element descriptionElement = ownerDoc.createElementNS(NS_OTA, "ns:Description");

				Attr attributeName = ownerDoc.createAttribute("Name");
				attributeName.setValue(bookingRule.getString("name"));
				descriptionElement.setAttributeNode(attributeName);

				Element textElement = ownerDoc.createElementNS(NS_OTA, "ns:Text");
				textElement.setTextContent(bookingRule.getString("text"));
				descriptionElement.appendChild(textElement);

				bookingRuleElement.appendChild(descriptionElement);
				bookingRulesElement.appendChild(bookingRuleElement);
			}

			globalInfoElement.appendChild(bookingRulesElement);
		}

		// Setting Promotions element
		JSONArray promotions = globalInfo.getJSONArray(JSON_PROP_PROMOTION);

		Element promotionsElement = ownerDoc.createElementNS(NS_OTA, "ns:Promotions");

		if (promotions != null && promotions.length() != 0) {
			for (int y = 0; y < promotions.length(); y++) {
				JSONObject promotion = promotions.getJSONObject(y);

				Element promotionELement = ownerDoc.createElementNS(NS_OTA, "ns:Promotion");

				Attr attributeIsIncludedInTour = ownerDoc.createAttribute("isIncludedInTour");
				attributeIsIncludedInTour.setValue(Boolean.toString(promotion.getBoolean("isIncludedInTour")));
				promotionELement.setAttributeNode(attributeIsIncludedInTour);

				Attr attributeAmount = ownerDoc.createAttribute("Amount");
				attributeAmount.setValue(promotion.getString("amount"));
				promotionELement.setAttributeNode(attributeAmount);

				Attr attributeCurrencyCode = ownerDoc.createAttribute("CurrencyCode");
				attributeCurrencyCode.setValue(promotion.getString("currencyCode"));
				promotionELement.setAttributeNode(attributeCurrencyCode);

				Attr attributeID = ownerDoc.createAttribute("ID");
				attributeID.setValue(promotion.getString("id"));
				promotionELement.setAttributeNode(attributeID);

				Attr attributeDescription = ownerDoc.createAttribute("Description");
				attributeDescription.setValue(promotion.getString("description"));
				promotionELement.setAttributeNode(attributeDescription);

				promotionsElement.appendChild(promotionELement);
			}

			globalInfoElement.appendChild(promotionsElement);
		}

		return globalInfoElement;
	}

	public static Element getHotelComponentElement(Document ownerDoc, JSONArray hotelComponents,
			Element componentsElement) {
		for (int i = 0; i < hotelComponents.length(); i++) {
			
			JSONObject hotelComponent = hotelComponents.getJSONObject(i);
			Element hotelComponentElement = ownerDoc.createElementNS(NS_OTA, "ns:HotelComponent");

			//code to add dynamicPkgAction in Hotel Component
			if (!hotelComponent.has(JSON_PROP_DYNAMICPKGACTION))
				hotelComponent.put(JSON_PROP_DYNAMICPKGACTION, "PackageAccomodation");
			
			// Setting DynamicPkgActon
			Attr attributeDynamicPkgAction = ownerDoc.createAttribute(DYNAMICPKGACTION);
			attributeDynamicPkgAction.setValue(hotelComponent.getString(JSON_PROP_DYNAMICPKGACTION));
			hotelComponentElement.setAttributeNode(attributeDynamicPkgAction);

			// Creating Room Stays ELement
			Element roomStaysElement = ownerDoc.createElementNS(NS_OTA, "ns:RoomStays");

			JSONObject roomStays = hotelComponent.getJSONObject("roomStays");
			JSONArray roomStay = roomStays.getJSONArray("roomStay");

			for (int j = 0; j < roomStay.length(); j++) {
				JSONObject roomSty = roomStay.getJSONObject(j);

				// Creating Room Stay element
				Element roomStayElement = ownerDoc.createElementNS(NS_OTA, "ns:RoomStay");

				// Setting Room Type Element
				String roomType = roomSty.getString("roomType");

				if (roomType != null && !roomType.isEmpty()) {
					Element roomTypesElement = ownerDoc.createElementNS(NS_OTA, "ns:RoomTypes");
					Element roomTypeElement = ownerDoc.createElementNS(NS_OTA, "ns:RoomType");

					Attr attributeRoomType = ownerDoc.createAttribute("RoomType");
					attributeRoomType.setValue(roomSty.getString("roomType"));
					roomTypeElement.setAttributeNode(attributeRoomType);

					roomTypesElement.appendChild(roomTypeElement);
					roomStayElement.appendChild(roomTypesElement);
				}

				// Setting Guest Count ELement
				JSONArray guestCounts = roomSty.getJSONArray("guestCount");

				if (guestCounts != null && guestCounts.length() != 0) {
					Element guestCountsElement = ownerDoc.createElementNS(NS_OTA, "ns:GuestCounts");

					for (int k = 0; k < guestCounts.length(); k++) {
						JSONObject guestCount = guestCounts.getJSONObject(k);
						Element guestCountElement = ownerDoc.createElementNS(NS_OTA, "ns:GuestCount");

						guestCountElement.setAttribute("ResGuestRPH", Integer.toString(guestCount.optInt("resGuestRPH")));
						guestCountsElement.appendChild(guestCountElement);
					}
					roomStayElement.appendChild(guestCountsElement);
				}

				// Setting TimeSpan Element
				JSONObject timeSpan = roomSty.optJSONObject("timeSpan");

				if (timeSpan != null && timeSpan.length() != 0) {
					Element timeSpanElement = ownerDoc.createElementNS(NS_OTA, "ns:TimeSpan");

					Attr attributeStart = ownerDoc.createAttribute("Start");
					attributeStart.setValue(timeSpan.getString("start"));
					timeSpanElement.setAttributeNode(attributeStart);

					Attr attributeDuration = ownerDoc.createAttribute("Duration");
						String duration = Integer.toString(timeSpan.optInt("duration"));
						if (duration != null && duration.length() != 0)
							attributeDuration.setValue(Integer.toString(timeSpan.optInt("duration")));
						else
							attributeDuration.setValue("0");
						
					timeSpanElement.setAttributeNode(attributeDuration);
					roomStayElement.appendChild(timeSpanElement);
				}

				// Setting Basic Property Info Element
				JSONArray basicPropertyInfoArr = roomSty.getJSONArray("basicPropertyInfo");
				for (int l = 0; l < basicPropertyInfoArr.length(); l++) {
					JSONObject basicPropertyInfo = basicPropertyInfoArr.getJSONObject(l);
					if (basicPropertyInfo != null && basicPropertyInfo.length() != 0) {
						Element basicPropertyInfoElement = ownerDoc.createElementNS(NS_OTA, "ns:BasicPropertyInfo");

						Attr attributeHotelCode = ownerDoc.createAttribute("HotelCode");
						attributeHotelCode.setValue(basicPropertyInfo.getString("hotelCode"));
						basicPropertyInfoElement.setAttributeNode(attributeHotelCode);

						roomStayElement.appendChild(basicPropertyInfoElement);
					}
				}
				
				// Setting singleSupplement Service
				JSONArray singleSuppArr = roomSty.optJSONArray("SingleSupplement");
				if(singleSuppArr!= null && singleSuppArr.length()!=0) {
					Element tpa_ExtensionsElem = ownerDoc.createElementNS(NS_OTA, "ns:TPA_Extensions");
					Element singleSupplementServiceElem = ownerDoc.createElementNS(NS_PAC, "tns:SingleSupplementService");
					
					for (int l = 0; l < singleSuppArr.length(); l++) {
						JSONObject singleSuppJson = singleSuppArr.getJSONObject(l);
						if (singleSuppJson != null && singleSuppJson.length() != 0) {
							Element singleSupplement = ownerDoc.createElementNS(NS_PAC, "tns:SingleSupplement");
	
							Attr id = ownerDoc.createAttribute("ID");
							id.setValue(singleSuppJson.optString("id"));
							singleSupplement.setAttributeNode(id);
							
							Attr name = ownerDoc.createAttribute("Name");
							name.setValue(singleSuppJson.optString("name"));
							singleSupplement.setAttributeNode(name);
							
							Attr createdDate = ownerDoc.createAttribute("CreatedDate");
							createdDate.setValue(singleSuppJson.optString("createdDate"));
							singleSupplement.setAttributeNode(createdDate);
							
							Attr description = ownerDoc.createAttribute("Description");
							description.setValue(singleSuppJson.optString("description"));
							singleSupplement.setAttributeNode(description);
							
							//Time Span
							JSONObject singleSuppTimeSpan = singleSuppJson.optJSONObject("timeSpan");
							if (singleSuppTimeSpan != null && singleSuppTimeSpan.length() != 0) {
								Element timeSpanElement = ownerDoc.createElementNS(NS_PAC, "tns:TimeSpan");

								Attr attributeStart = ownerDoc.createAttribute("Start");
								attributeStart.setValue(singleSuppTimeSpan.optString("start"));
								timeSpanElement.setAttributeNode(attributeStart);

								Attr attributeDuration = ownerDoc.createAttribute("Duration");
								attributeDuration.setValue(singleSuppTimeSpan.optString("duration"));
								timeSpanElement.setAttributeNode(attributeDuration);

								Attr attributeEnd = ownerDoc.createAttribute("End");
								attributeEnd.setValue(singleSuppTimeSpan.optString("end"));
								timeSpanElement.setAttributeNode(attributeEnd);

								singleSupplement.appendChild(timeSpanElement);
							}
							
							// Setting Price Element
							JSONObject price = singleSuppJson.optJSONObject("price");
							if(price!= null && price.length()!=0) {
								Element priceElem = ownerDoc.createElementNS(NS_PAC, "tns:Price");
								JSONArray totalArr = price.optJSONArray("total");
								if (totalArr != null && totalArr.length() != 0) {
									for (int z = 0; z < totalArr.length(); z++) {

										JSONObject totalJson = totalArr.getJSONObject(z);
										Element totalElem = ownerDoc.createElementNS(NS_OTA, "ns:Total");
										
										Attr amountBeforeTax = ownerDoc.createAttribute("AmountBeforeTax");
										amountBeforeTax.setValue(Integer.toString(totalJson.optInt("amountBeforeTax")));
										totalElem.setAttributeNode(amountBeforeTax);
										
										Attr amountAfterTax = ownerDoc.createAttribute("AmountAfterTax");
										amountAfterTax.setValue(Integer.toString(totalJson.optInt("amountAfterTax")));
										totalElem.setAttributeNode(amountAfterTax);
				
										Attr attributeCurrency = ownerDoc.createAttribute("CurrencyCode");
										attributeCurrency.setValue(totalJson.getString("currencyCode"));
										totalElem.setAttributeNode(attributeCurrency);
				
										Attr attributeType = ownerDoc.createAttribute("Type");
										attributeType.setValue(totalJson.getString("type"));
										totalElem.setAttributeNode(attributeType);
										
										priceElem.appendChild(totalElem);
									}
									singleSupplement.appendChild(priceElem);
								}
							}
							
							// Setting Deposits Element
							JSONObject depositsJson = singleSuppJson.optJSONObject("deposits");
							if(depositsJson!= null && depositsJson.length()!=0) {
								Element depositsElem = ownerDoc.createElementNS(NS_PAC, "tns:Deposits");
								JSONArray depositArr = depositsJson.optJSONArray("total");
								if (depositArr != null && depositArr.length() != 0) {
									for (int z = 0; z < depositArr.length(); z++) {
										
										JSONObject depositJson = depositArr.getJSONObject(z);
										Element depositElem = ownerDoc.createElementNS(NS_PAC, "tns:Deposit");
					
										Attr amountAfterTax = ownerDoc.createAttribute("DepositAmount");
										amountAfterTax.setValue(Integer.toString(depositJson.optInt("depositAmount")));
										depositElem.setAttributeNode(amountAfterTax);
				
										Attr attributeCurrency = ownerDoc.createAttribute("CurrencyCode");
										attributeCurrency.setValue(depositJson.optString("currencyCode"));
										depositElem.setAttributeNode(attributeCurrency);
				
										Attr attributeType = ownerDoc.createAttribute("Type");
										attributeType.setValue(depositJson.optString("type"));
										depositElem.setAttributeNode(attributeType);
					
										depositsElem.appendChild(depositElem);
									}
									singleSupplement.appendChild(depositsElem);
								}
							}
							
							// Setting Passenger ELement
			                JSONArray passengerArr = singleSuppJson.getJSONArray("passenger");
			                if (passengerArr != null && passengerArr.length() != 0) {
			                    for (int k = 0; k < passengerArr.length(); k++) {
			                        JSONObject guestCount = passengerArr.getJSONObject(k);
			                        Element passengerElem = ownerDoc.createElementNS(NS_PAC, "tns:Passenger");
			                        
			                        passengerElem.setAttribute("MinimumAge",(guestCount.optString("minimumAge")));
			                        passengerElem.setAttribute("MaximumAge",(guestCount.optString("maximumAge")));
			                        passengerElem.setAttribute("MinimumPassengers",(guestCount.optString("minimumPassengers")));
			                        passengerElem.setAttribute("MaximumPassengers",(guestCount.optString("maximumPassengers")));
			                        passengerElem.setAttribute("PassengerTypeCode",(guestCount.optString("passengerTypeCode")));
			                        
			                        singleSupplement.appendChild(passengerElem);
			                    }
			                }
							
							singleSupplementServiceElem.appendChild(singleSupplement);
						}
					}
					tpa_ExtensionsElem.appendChild(singleSupplementServiceElem);
					roomStayElement.appendChild(tpa_ExtensionsElem);
				}
				roomStaysElement.appendChild(roomStayElement);
			}
			hotelComponentElement.appendChild(roomStaysElement);
			componentsElement.appendChild(hotelComponentElement);
		}
		return componentsElement;
	}

	public static Element getAirComponentElement(Document ownerDoc, JSONObject dynamicPackageObj,
			JSONArray airComponents, Element componentsElement, String supplierID) throws Exception {

		for (int i = 0; i < airComponents.length(); i++) {
			JSONObject airComponent = airComponents.getJSONObject(i);

			Element airComponentElement = ownerDoc.createElementNS(NS_OTA, "ns:AirComponent");

			// Setting DynamicPkgActon
			Attr attributeDynamicPkgAction = ownerDoc.createAttribute(DYNAMICPKGACTION);
			attributeDynamicPkgAction.setValue(airComponent.getString(JSON_PROP_DYNAMICPKGACTION));
			airComponentElement.setAttributeNode(attributeDynamicPkgAction);

			// Setting AirItinerary Element
			JSONObject airItinerary = airComponent.getJSONObject("airItinerary");

			Element airItineraryElement = ownerDoc.createElementNS(NS_OTA, "ns:AirItinerary");

			Attr attributeAirItineraryRPH = ownerDoc.createAttribute("AirItineraryRPH");
			attributeAirItineraryRPH.setValue(airItinerary.getString("airItineraryRPH"));
			airItineraryElement.setAttributeNode(attributeAirItineraryRPH);

			JSONArray ODOs = airItinerary.getJSONArray("originDestinationOptions");

			if (ODOs == null || ODOs.length() != 0)
				throw new Exception(String.format("Object originDestinationOptions must be set for supplier %s if flights are to be availed",supplierID));

			// Creating ODOs Element
			Element ODOsElement = ownerDoc.createElementNS(NS_OTA, "ns:OriginDestinationOptions");
			for (int j = 0; j < ODOs.length(); j++) {
				JSONObject ODO = ODOs.getJSONObject(j);

				// Creating ODO Element
				Element ODOElement = ownerDoc.createElementNS(NS_OTA, "ns:OriginDestinationOption");
				JSONArray flightSegments = ODO.getJSONArray("flightSegment");

				for (int k = 0; k < flightSegments.length(); k++) {
					JSONObject flightSegment = flightSegments.getJSONObject(k);

					// Creating Flight Segment Element
					Element flightSegmentElement = ownerDoc.createElementNS(NS_OTA, "ns:FlightSegment");

					Attr attributeFlightNumber = ownerDoc.createAttribute("FlightNumber");
					attributeFlightNumber.setValue(flightSegment.getString("flightNumber"));
					flightSegmentElement.setAttributeNode(attributeFlightNumber);

					Attr attributeDepartureDate = ownerDoc.createAttribute("DepartureDateTime");
					attributeDepartureDate.setValue(flightSegment.getString("departureDate"));
					flightSegmentElement.setAttributeNode(attributeDepartureDate);

					Attr attributeArrivalDate = ownerDoc.createAttribute("ArrivalDateTime");
					attributeArrivalDate.setValue(flightSegment.getString("arrivalDate"));
					flightSegmentElement.setAttributeNode(attributeArrivalDate);

					Attr attributeStopQuantity = ownerDoc.createAttribute("StopQuantity");
					attributeStopQuantity.setValue(flightSegment.getString("stopQuantity"));
					flightSegmentElement.setAttributeNode(attributeStopQuantity);

					Attr attributeNumberInParty = ownerDoc.createAttribute("NumberInParty");
					attributeNumberInParty.setValue(flightSegment.getString("numberInParty"));
					flightSegmentElement.setAttributeNode(attributeNumberInParty);

					// Setting departure Airport element
					Element departureAirportElement = ownerDoc.createElementNS(NS_OTA, "ns:DepartureAirport");

					Attr attributeLocationCode = ownerDoc.createAttribute("LocationCode");
					attributeLocationCode.setValue(flightSegment.getString("originLocation"));
					departureAirportElement.setAttributeNode(attributeLocationCode);

					Attr attributeTerminal = ownerDoc.createAttribute("Terminal");
					attributeTerminal.setValue(flightSegment.getString("departureTerminal"));
					departureAirportElement.setAttributeNode(attributeTerminal);

					flightSegmentElement.appendChild(departureAirportElement);

					// Setting Arrival Airport Element
					Element arrivalAirportElement = ownerDoc.createElementNS(NS_OTA, "ns:ArrivalAirport");

					Attr attributeArrLocationCode = ownerDoc.createAttribute("LocationCode");
					attributeArrLocationCode.setValue(flightSegment.getString("destinationLocation"));
					arrivalAirportElement.setAttributeNode(attributeArrLocationCode);

					Attr attributeArrTerminal = ownerDoc.createAttribute("Terminal");
					attributeArrTerminal.setValue(flightSegment.getString("arrivalTerminal"));
					arrivalAirportElement.setAttributeNode(attributeArrTerminal);

					flightSegmentElement.appendChild(arrivalAirportElement);

					// Setting Operating Airline Element
					JSONObject operatingAirline = flightSegment.getJSONObject("operatingAirline");
					if (operatingAirline != null && operatingAirline.length() != 0) {
						Element operatingAirlinrElement = ownerDoc.createElementNS(NS_OTA, "ns:OperatingAirline");

						Attr attributeFlightNum = ownerDoc.createAttribute("FlightNumber");
						attributeFlightNum.setValue(operatingAirline.getString("flightNumber"));
						operatingAirlinrElement.setAttributeNode(attributeFlightNum);

						Attr attributeCompanyShortName = ownerDoc.createAttribute("CompanyShortName");
						attributeCompanyShortName.setValue(operatingAirline.getString("companyShortName"));
						operatingAirlinrElement.setAttributeNode(attributeCompanyShortName);

						Attr attributeCode = ownerDoc.createAttribute("Code");
						attributeCode.setValue(operatingAirline.getString("airlineCode"));
						operatingAirlinrElement.setAttributeNode(attributeCode);

						flightSegmentElement.appendChild(operatingAirlinrElement);
					}

					// Setting BookingClassAvails element
					Element bookingClassAvailsElement = ownerDoc.createElementNS(NS_OTA, "ns:BookingClassAvails");

					Attr attributeCabinType = ownerDoc.createAttribute("CabinType");
					attributeCabinType.setValue(flightSegment.getString("cabinType"));
					bookingClassAvailsElement.setAttributeNode(attributeCabinType);

					Element bookingClassAvailElement = ownerDoc.createElementNS(NS_OTA, "ns:BookingClassAvail");

					Attr attributeRBD = ownerDoc.createAttribute("ResBookDesigCode");
					attributeRBD.setValue(flightSegment.getString("resBookDesigCode"));
					bookingClassAvailElement.setAttributeNode(attributeRBD);

					bookingClassAvailsElement.appendChild(bookingClassAvailElement);
					flightSegmentElement.appendChild(bookingClassAvailsElement);
					ODOElement.appendChild(flightSegmentElement);
				}

				ODOsElement.appendChild(ODOElement);
			}

			airItineraryElement.appendChild(ODOsElement);

			airComponentElement.appendChild(airItineraryElement);

			// Creating Air Itinerary Pricing Info Element
			JSONObject airItineraryPricingInfo = airComponent.getJSONObject("airItineraryPricingInfo");

			if (airItineraryPricingInfo != null && airItineraryPricingInfo.length() != 0) {
				Element airItineraryPricingInfoElement = ownerDoc.createElementNS(NS_OTA, "ns:AirItineraryPricingInfo");

				// Creating ItinTotalFare Element
				JSONObject itinTotalFare = airItineraryPricingInfo.getJSONObject("itinTotalFare");
				Element ItinElement = ownerDoc.createElementNS(NS_OTA, "ns:ItinTotalFare");

				// Setting Base Fare Element
				JSONObject baseFare = itinTotalFare.getJSONObject("baseFare");
				if (baseFare != null && baseFare.length() != 0) {
					Element baseFareElement = ownerDoc.createElementNS(NS_OTA, "ns:BaseFare");

					Attr attributeBaseCurrency = ownerDoc.createAttribute("CurrencyCode");
					attributeBaseCurrency.setValue(baseFare.getString("currencyCode"));
					baseFareElement.setAttributeNode(attributeBaseCurrency);

					Attr attributeBaseDecmlPt = ownerDoc.createAttribute("DecimalPlaces");
					attributeBaseDecmlPt.setValue(Integer.toString(baseFare.optInt("decimalPlaces")));
					baseFareElement.setAttributeNode(attributeBaseDecmlPt);

					Attr attributeBaseAmount = ownerDoc.createAttribute("Amount");
					attributeBaseAmount.setValue(Integer.toString(baseFare.optInt("amount")));
					baseFareElement.setAttributeNode(attributeBaseAmount);

					ItinElement.appendChild(baseFareElement);
				}

				// Setting Taxes Element
				JSONObject taxes = itinTotalFare.getJSONObject("taxes");

				if (taxes != null && taxes.length() != 0) {
					Element taxesElement = ownerDoc.createElementNS(NS_OTA, "ns:Taxes");

					Attr attributeTaxesAmount = ownerDoc.createAttribute("Amount");
					attributeTaxesAmount.setValue(Integer.toString(taxes.optInt("amount")));
					taxesElement.setAttributeNode(attributeTaxesAmount);

					JSONArray tax = taxes.getJSONArray("tax");

					if (tax != null && tax.length() != 0) {
						for (int x = 0; x < tax.length(); x++) {
							
							JSONObject taxx = tax.getJSONObject(x);
							Element taxElement = ownerDoc.createElementNS(NS_OTA, "ns:Tax");

							Attr attributeTaxCode = ownerDoc.createAttribute("TaxCode");
							attributeTaxCode.setValue(taxx.getString("taxCode"));
							taxElement.setAttributeNode(attributeTaxCode);

							Attr attributeTaxName = ownerDoc.createAttribute("TaxName");
							attributeTaxName.setValue(taxx.getString("taxName"));
							taxElement.setAttributeNode(attributeTaxName);

							Attr attributeTaxCurrency = ownerDoc.createAttribute("CurrencyCode");
							attributeTaxCurrency.setValue(taxx.getString("currencyCode"));
							taxElement.setAttributeNode(attributeTaxCurrency);

							Attr attributeTaxDecmlPt = ownerDoc.createAttribute("DecimalPlaces");
							attributeTaxDecmlPt.setValue(Integer.toString(taxx.optInt("decimalPlaces")));
							taxElement.setAttributeNode(attributeTaxDecmlPt);

							Attr attributeTaxAmount = ownerDoc.createAttribute("Amount");
							attributeTaxAmount.setValue(Integer.toString(taxx.optInt("amount")));
							taxElement.setAttributeNode(attributeTaxAmount);

							taxesElement.appendChild(taxElement);
						}
					}
					ItinElement.appendChild(taxesElement);
				}

				// Setting Total Fare Element
				JSONObject totalFare = itinTotalFare.getJSONObject("totalFare");

				if (totalFare != null && totalFare.length() != 0) {
					Element totalFareElement = ownerDoc.createElementNS(NS_OTA, "ns:TotalFare");

					Attr attributeTotalPQ = ownerDoc.createAttribute("PassengerQuantity");
					attributeTotalPQ.setValue(Integer.toString(totalFare.optInt("passengerQuantity")));
					totalFareElement.setAttributeNode(attributeTotalPQ);

					Attr attributeTotalCurrency = ownerDoc.createAttribute("CurrencyCode");
					attributeTotalCurrency.setValue(totalFare.getString("currencyCode"));
					totalFareElement.setAttributeNode(attributeTotalCurrency);

					Attr attributeTotalAmount = ownerDoc.createAttribute("Amount");
					attributeTotalAmount.setValue(Integer.toString(totalFare.optInt("amount")));
					totalFareElement.setAttributeNode(attributeTotalAmount);

					ItinElement.appendChild(totalFareElement);
				}
				airItineraryPricingInfoElement.appendChild(ItinElement);

				// Setting Fare Info Element
				JSONArray fareInfos = airItineraryPricingInfo.getJSONArray("fareInfo");

				if (fareInfos != null && fareInfos.length() != 0) {
					Element fareInfosElement = ownerDoc.createElementNS(NS_OTA, "ns:FareInfos");

					for (int z = 0; z < fareInfos.length(); z++) {
						JSONObject fareInfo = fareInfos.getJSONObject(z);

						Element fareInfoElement = ownerDoc.createElementNS(NS_OTA, "ns:FareInfo");

						JSONObject discountPricing = fareInfo.getJSONObject("discountPricing");

						Element discountPricingElement = ownerDoc.createElementNS(NS_OTA, "ns:DiscountPricing");

						Attr attributePurpose = ownerDoc.createAttribute("Purpose");
						attributePurpose.setValue(discountPricing.getString("purpose"));
						discountPricingElement.setAttributeNode(attributePurpose);

						Attr attributeType = ownerDoc.createAttribute("Type");
						attributeType.setValue(discountPricing.getString("type"));
						discountPricingElement.setAttributeNode(attributeType);

						Attr attributeDiscount = ownerDoc.createAttribute("Discount");
						attributeDiscount.setValue(discountPricing.getString("discount"));
						discountPricingElement.setAttributeNode(attributeDiscount);

						fareInfoElement.appendChild(discountPricingElement);
						fareInfosElement.appendChild(fareInfoElement);
					}
					airItineraryPricingInfoElement.appendChild(fareInfosElement);
				}
				airComponentElement.appendChild(airItineraryPricingInfoElement);
			}

			// Creating Traveler Info Summary Element
			JSONArray travelerInfoSummary = dynamicPackageObj.getJSONArray("paxInfo");

			if (travelerInfoSummary != null && travelerInfoSummary.length() != 0) {
				
				Element tpaElement = ownerDoc.createElementNS(NS_OTA, "ns:TPA_Extensions");
				Element travelRefSummaryElement = ownerDoc.createElementNS(NS_PAC, "tns:TravelRefSummary");
				Element passengerTypeQuantitiesElement = ownerDoc.createElementNS(NS_OTA, "ns:PassengerTypeQuantities");

				for (int g = 0; g < travelerInfoSummary.length(); g++) {
					JSONObject travelerInfoSmry = travelerInfoSummary.getJSONObject(g);

					Element passengerTypeQuantityElement = ownerDoc.createElementNS(NS_OTA, "ns:PassengerTypeQuantity");

					String code = travelerInfoSmry.getString("paxType");
					if (code != null && code.length() != 0)
						passengerTypeQuantityElement.setAttribute("Code", code);

					String quantity = Integer.toString(travelerInfoSmry.optInt("quantity"));
					if (quantity != null && quantity.length() != 0)
						passengerTypeQuantityElement.setAttribute("Quantity", quantity);

					passengerTypeQuantitiesElement.appendChild(passengerTypeQuantityElement);
				}
				travelRefSummaryElement.appendChild(passengerTypeQuantitiesElement);
				tpaElement.appendChild(travelRefSummaryElement);
				airComponentElement.appendChild(tpaElement);
			}
			componentsElement.appendChild(airComponentElement);
		}

		return componentsElement;
	}

	public static Element getCruiseComponentElement(Document ownerDoc, JSONArray cruiseComponents, Element tpaElement) {
		Element cruiseComponentsElement = ownerDoc.createElementNS(NS_PAC, "tns:CruiseComponents");

		for (int i = 0; i < cruiseComponents.length(); i++) {
			JSONObject cruiseComponent = cruiseComponents.getJSONObject(i);

			Element cruiseComponentElement = ownerDoc.createElementNS(NS_PAC, "tns:CruiseComponent");
			
			if (!cruiseComponent.has(JSON_PROP_DYNAMICPKGACTION))
				cruiseComponent.put(JSON_PROP_DYNAMICPKGACTION, DYNAMICPKGACTION_CRUISE_TOUR);
			
			// Setting DynamicPkgActon
			Attr attributeDynamicPkgAction = ownerDoc.createAttribute(DYNAMICPKGACTION);
			attributeDynamicPkgAction.setValue(cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION));
			cruiseComponentElement.setAttributeNode(attributeDynamicPkgAction);

			//check if dynamicpkgaction not pre or post night then only this attribute can set.
			if(!cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION).equals(DYNAMICPKGACTION_HOTEL_PRE) && !cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION).equals(DYNAMICPKGACTION_HOTEL_POST)) {
				Attr attributeID = ownerDoc.createAttribute("ID");
				attributeID.setValue(cruiseComponent.optString("id"));
				cruiseComponentElement.setAttributeNode(attributeID);
					
				Attr attributeName = ownerDoc.createAttribute("Name");
				attributeName.setValue(cruiseComponent.optString("name"));
				cruiseComponentElement.setAttributeNode(attributeName);
				
				Attr attributeDescription = ownerDoc.createAttribute("Description");
				attributeDescription.setValue(cruiseComponent.optString("description"));
				cruiseComponentElement.setAttributeNode(attributeDescription);
			}
			
			//check if dynamicpkgaction not pre or post night then only this attribute can set.
			JSONArray categoryOption =new JSONArray();
			JSONArray categoryOptions =new JSONArray();
			JSONObject roomStays = new JSONObject();
			if (!cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION).equals(DYNAMICPKGACTION_HOTEL_PRE) && !cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION).equals(DYNAMICPKGACTION_HOTEL_POST)) {
				// To handle Prenight /Postnight from WEM request to form proper cruise prent/postnt xml req 
				if (cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION).equals(DYNAMICPKGACTION_CRUISE_PRE) || cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION).equals(DYNAMICPKGACTION_CRUISE_POST)) {
					roomStays = cruiseComponent.getJSONObject("roomStays");
					categoryOptions.put(roomStays);
				}
				else
					categoryOptions = cruiseComponent.optJSONArray("categoryOptions");
				
				for (int j = 0; j < categoryOptions.length(); j++) {
					JSONObject catgryOptions = categoryOptions.getJSONObject(j);

					Element categoryOptionsElement = ownerDoc.createElementNS(NS_PAC, "tns:CategoryOptions");
					// To handle Prenight /Postnight from WEM request to form proper cruise prent /postnt xml req
					if (cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION).equals(DYNAMICPKGACTION_CRUISE_PRE) || cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION).equals(DYNAMICPKGACTION_CRUISE_POST))
						categoryOption = catgryOptions.getJSONArray("roomStay");
					else
						categoryOption = catgryOptions.getJSONArray("categoryOption");

					for (int k = 0; k < categoryOption.length(); k++) {
						JSONObject catgryOption = categoryOption.getJSONObject(k);

						Element categoryOptionElement = ownerDoc.createElementNS(NS_PAC, "tns:CategoryOption");

						Attr attributeStatus = ownerDoc.createAttribute("AvailabilityStatus");
						attributeStatus.setValue(catgryOption.optString("availabilityStatus"));
						categoryOptionElement.setAttributeNode(attributeStatus);
						
						Attr attributeName = ownerDoc.createAttribute("Name");
						attributeName.setValue(catgryOption.optString("name"));
						categoryOptionElement.setAttributeNode(attributeName);
						
						Attr attributeID = ownerDoc.createAttribute("ID");
						attributeID.setValue(catgryOption.optString("id"));
						categoryOptionElement.setAttributeNode(attributeID);
						
						//Added CabinCategory 
						Attr attributeCatgryName = ownerDoc.createAttribute("CabinCategory");
						attributeCatgryName.setValue(catgryOption.optString("cabinCategory"));
						categoryOptionElement.setAttributeNode(attributeCatgryName);

						Attr attributeDescription = ownerDoc.createAttribute("Description");
						attributeDescription.setValue(catgryOption.optString("description"));
						categoryOptionElement.setAttributeNode(attributeDescription);
						// To handle Prenight /Postnight from WEM request to form proper cruise prent /postnt xml req
						if (cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION).equals(DYNAMICPKGACTION_CRUISE_PRE) || cruiseComponent.getString(JSON_PROP_DYNAMICPKGACTION).equals(DYNAMICPKGACTION_CRUISE_POST)) {
							// Setting Room Type Element
							String roomType = catgryOption.getString("roomType");

							if (roomType != null && !roomType.isEmpty()) {
								Attr attributeType = ownerDoc.createAttribute("Type");
								attributeType.setValue(catgryOption.getString("roomType"));
								categoryOptionElement.setAttributeNode(attributeType);
								//Added CabinType
								Attr attributeCabinType = ownerDoc.createAttribute("CabinType");
								attributeCabinType.setValue(catgryOption.getString("roomType"));
								categoryOptionElement.setAttributeNode(attributeCabinType);
							}
						}
						else {
							Attr attributeCabinType = ownerDoc.createAttribute("CabinType");
							attributeCabinType.setValue(catgryOption.optString("cabinType"));
							categoryOptionElement.setAttributeNode(attributeCabinType);
							//Added CabinCategory
							Attr attributeType = ownerDoc.createAttribute("Type");
							attributeType.setValue(catgryOption.optString("type"));
							categoryOptionElement.setAttributeNode(attributeType);
						}

						// Setting Guest Count Element
						JSONArray guestCounts = catgryOption.getJSONArray("guestCount");

						if (guestCounts != null && guestCounts.length() != 0) {
							Element guestCountsElement = ownerDoc.createElementNS(NS_PAC, "tns:GuestCounts");

							for (int y = 0; y < guestCounts.length(); y++) {
								JSONObject guestCount = guestCounts.getJSONObject(y);

								Element guestCountElement = ownerDoc.createElementNS(NS_OTA, "ns:GuestCount");

								Attr attributeResGuestRPH = ownerDoc.createAttribute("ResGuestRPH");
								attributeResGuestRPH.setValue(Integer.toString(guestCount.optInt("resGuestRPH")));
								guestCountElement.setAttributeNode(attributeResGuestRPH);

								guestCountsElement.appendChild(guestCountElement);
							}
							categoryOptionElement.appendChild(guestCountsElement);
						}

						// Setting TimeSpan Element
						JSONObject timeSpan = catgryOption.optJSONObject("timeSpan");

						if (timeSpan != null && timeSpan.length() != 0) {
							Element timeSpanElement = ownerDoc.createElementNS(NS_PAC, "tns:TimeSpan");

							Attr attributeStart = ownerDoc.createAttribute("Start");
							attributeStart.setValue(timeSpan.optString("start"));
							timeSpanElement.setAttributeNode(attributeStart);

							Attr attributeDuration = ownerDoc.createAttribute("Duration");

							String duration = Integer.toString(timeSpan.optInt("duration"));
							if (duration != null && duration.length() != 0)
								attributeDuration.setValue(Integer.toString(timeSpan.optInt("duration")));
							else
								attributeDuration.setValue("0");

							timeSpanElement.setAttributeNode(attributeDuration);

							Attr attributeEnd = ownerDoc.createAttribute("End");
							attributeEnd.setValue(timeSpan.optString("end"));
							timeSpanElement.setAttributeNode(attributeEnd);

							categoryOptionElement.appendChild(timeSpanElement);
						}

						// Setting Cabin Options Element
						JSONArray cabinOptions = catgryOption.optJSONArray("cabinOption");

						if (cabinOptions != null && cabinOptions.length() != 0) {
							Element cabinOptionsElement = ownerDoc.createElementNS(NS_PAC, "tns:CabinOptions");

							for (int x = 0; x < cabinOptions.length(); x++) {
								JSONObject cabinOption = cabinOptions.getJSONObject(x);

								Element cabinOptionElement = ownerDoc.createElementNS(NS_PAC, "tns:CabinOption");

								Attr attributeCabinStatus = ownerDoc.createAttribute("Status");
								attributeCabinStatus.setValue(cabinOption.optString("status"));
								cabinOptionElement.setAttributeNode(attributeCabinStatus);

								Attr attributeCabinNumber = ownerDoc.createAttribute("CabinNumber");
								attributeCabinNumber.setValue(cabinOption.optString("cabinNumber"));
								// attributeCabinNumber.setValue(cabinOption.optInt("cabinNumber")+"");
								cabinOptionElement.setAttributeNode(attributeCabinNumber);

								Attr attributeMaxOccupancy = ownerDoc.createAttribute("MaxOccupancy");
								attributeMaxOccupancy.setValue(Integer.toString(cabinOption.optInt("maxOccupancy")));
								cabinOptionElement.setAttributeNode(attributeMaxOccupancy);

								Attr attributeMinOccupancy = ownerDoc.createAttribute("MinOccupancy");
								attributeMinOccupancy.setValue(Integer.toString(cabinOption.optInt("minOccupancy")));
								cabinOptionElement.setAttributeNode(attributeMinOccupancy);

								cabinOptionsElement.appendChild(cabinOptionElement);
							}
							categoryOptionElement.appendChild(cabinOptionsElement);
						}
						categoryOptionsElement.appendChild(categoryOptionElement);
					}
					cruiseComponentElement.appendChild(categoryOptionsElement);
				}
			}
				cruiseComponentsElement.appendChild(cruiseComponentElement);
		}
		tpaElement.appendChild(cruiseComponentsElement);

		return tpaElement;
		
	}

	public static Element getTransferComponentElement(Document ownerDoc, JSONArray transfersComponents,
			Element tpaElement) {
		Element transferComponentsElement = ownerDoc.createElementNS(NS_PAC, "tns:TransferComponents");

		for (int i = 0; i < transfersComponents.length(); i++) {
			JSONObject transfersComponent = transfersComponents.getJSONObject(i);

			Element transferComponentElement = ownerDoc.createElementNS(NS_PAC, "tns:TransferComponent");
			
			// Setting DynamicPkgActon
			Attr attributeDynamicPkgAction = ownerDoc.createAttribute(DYNAMICPKGACTION);
			attributeDynamicPkgAction.setValue(transfersComponent.getString(JSON_PROP_DYNAMICPKGACTION));
			transferComponentElement.setAttributeNode(attributeDynamicPkgAction);

			Attr attributeID = ownerDoc.createAttribute("ID");
			attributeID.setValue(transfersComponent.optString("id"));
			transferComponentElement.setAttributeNode(attributeID);
			
			Attr dynamicPkgStatus = ownerDoc.createAttribute("DynamicPkgStatus");
			dynamicPkgStatus.setValue(transfersComponent.optString("dynamicPkgStatus"));
			transferComponentElement.setAttributeNode(dynamicPkgStatus);
			
			Attr attributeCreatedDate = ownerDoc.createAttribute("CreatedDate");
			attributeCreatedDate.setValue(transfersComponent.optString("createdDate"));
			transferComponentElement.setAttributeNode(attributeCreatedDate);

			Attr attributeExpDate = ownerDoc.createAttribute("option_expiry_date");
			attributeExpDate.setValue(transfersComponent.optString("option_expiry_date"));
			transferComponentElement.setAttributeNode(attributeExpDate);

			Attr attributeStatus = ownerDoc.createAttribute("AvailabilityStatus");
			attributeStatus.setValue(transfersComponent.optString("availabilityStatus"));
			transferComponentElement.setAttributeNode(attributeStatus);

			JSONArray groundServices = transfersComponent.getJSONArray("groundService");

			for (int j = 0; j < groundServices.length(); j++) {
				JSONObject groundService = groundServices.getJSONObject(j);

				// Setting Ground Service Element
				Element groundServiceElement = ownerDoc.createElementNS(NS_PAC, "tns:GroundService");

				Attr attributeName = ownerDoc.createAttribute("Name");
				attributeName.setValue(groundService.getString("name"));
				groundServiceElement.setAttributeNode(attributeName);

				Attr attributeDesc = ownerDoc.createAttribute("Description");
				attributeDesc.setValue(groundService.getString("description"));
				groundServiceElement.setAttributeNode(attributeDesc);

				Attr attributeDep = ownerDoc.createAttribute("DepartureCity");
				attributeDep.setValue(groundService.getString("departureCity"));
				groundServiceElement.setAttributeNode(attributeDep);

				Attr attributeArr = ownerDoc.createAttribute("ArrivalCity");
				attributeArr.setValue(groundService.getString("arrivalCity"));
				groundServiceElement.setAttributeNode(attributeArr);

				Attr attributeDepDate = ownerDoc.createAttribute("DepartureDate");
				attributeDepDate.setValue(groundService.getString("departureDate"));
				groundServiceElement.setAttributeNode(attributeDepDate);

				Attr attributeArrDate = ownerDoc.createAttribute("ArrivalDate");
				attributeArrDate.setValue(groundService.getString("arrivalDate"));
				groundServiceElement.setAttributeNode(attributeArrDate);

				// Setting Location Element
				JSONObject location = groundService.getJSONObject("location");

				if (location != null && location.length() != 0) {
					Element locationElement = ownerDoc.createElementNS(NS_PAC, "tns:Location");
					Element pickUpElement = ownerDoc.createElementNS(NS_OTA, "ns:Pickup");
					Element airportInfoElement = ownerDoc.createElementNS(NS_OTA, "ns:AirportInfo");
					Element departureElement = ownerDoc.createElementNS(NS_OTA, "ns:Departure");

					Attr attributeLocation = ownerDoc.createAttribute("LocationCode");
					attributeLocation.setValue(location.getString("pickUpLocation"));
					departureElement.setAttributeNode(attributeLocation);

					Attr attributeAirport = ownerDoc.createAttribute("AirportName");
					attributeAirport.setValue(location.getString("airportName"));
					departureElement.setAttributeNode(attributeAirport);

					Attr attributeCodeContext = ownerDoc.createAttribute("CodeContext");
					attributeCodeContext.setValue(location.getString("codeContext"));
					departureElement.setAttributeNode(attributeCodeContext);

					airportInfoElement.appendChild(departureElement);
					pickUpElement.appendChild(airportInfoElement);
					locationElement.appendChild(pickUpElement);

					groundServiceElement.appendChild(locationElement);
				}
				
				// Setting Price Element
				JSONObject price = groundService.optJSONObject("price");
				if(price!= null && price.length()!=0) {
					Element priceElem = ownerDoc.createElementNS(NS_PAC, "tns:Price");
					JSONArray totalArr = price.optJSONArray("total");
					if (totalArr != null && totalArr.length() != 0) {
						for (int z = 0; z < totalArr.length(); z++) {

							JSONObject totalJson = totalArr.getJSONObject(z);
							Element totalElem = ownerDoc.createElementNS(NS_PAC, "tns:Total");
							
							Attr amountBeforeTax = ownerDoc.createAttribute("AmountBeforeTax");
							amountBeforeTax.setValue(Integer.toString(totalJson.optInt("amountBeforeTax")));
							totalElem.setAttributeNode(amountBeforeTax);
							
							Attr amountAfterTax = ownerDoc.createAttribute("AmountAfterTax");
							amountAfterTax.setValue(Integer.toString(totalJson.optInt("amountAfterTax")));
							totalElem.setAttributeNode(amountAfterTax);
	
							Attr attributeCurrency = ownerDoc.createAttribute("CurrencyCode");
							attributeCurrency.setValue(totalJson.getString("currencyCode"));
							totalElem.setAttributeNode(attributeCurrency);
	
							Attr attributeType = ownerDoc.createAttribute("Type");
							attributeType.setValue(totalJson.getString("type"));
							totalElem.setAttributeNode(attributeType);
							
							priceElem.appendChild(totalElem);
						}
						groundServiceElement.appendChild(priceElem);
					}
				}
				
				// Setting Deposits Element
				JSONObject depositsJson = groundService.optJSONObject("deposits");
				if(depositsJson!= null && depositsJson.length()!=0) {
					Element depositsElem = ownerDoc.createElementNS(NS_PAC, "tns:Deposits");
					JSONArray depositArr = depositsJson.optJSONArray("total");
					if (depositArr != null && depositArr.length() != 0) {
						for (int z = 0; z < depositArr.length(); z++) {
							
							JSONObject depositJson = depositArr.getJSONObject(z);
							Element depositElem = ownerDoc.createElementNS(NS_PAC, "tns:Deposit");
		
							Attr amountAfterTax = ownerDoc.createAttribute("DepositAmount");
							amountAfterTax.setValue(Integer.toString(depositJson.optInt("depositAmount")));
							depositElem.setAttributeNode(amountAfterTax);
	
							Attr attributeCurrency = ownerDoc.createAttribute("CurrencyCode");
							attributeCurrency.setValue(depositJson.optString("currencyCode"));
							depositElem.setAttributeNode(attributeCurrency);
	
							Attr attributeType = ownerDoc.createAttribute("Type");
							attributeType.setValue(depositJson.optString("type"));
							depositElem.setAttributeNode(attributeType);
		
							depositsElem.appendChild(depositElem);
						}
						groundServiceElement.appendChild(depositsElem);
					}
				}
				
				// Setting booking rules element
				JSONArray bookingRules = groundService.optJSONArray(JSON_PROP_BOOKINGRULE);
				Element bookingRulesElement = ownerDoc.createElementNS(NS_OTA, "ns:BookingRules");
				if (bookingRules != null && bookingRules.length() != 0) {
					for (int k = 0; k < bookingRules.length(); k++) {
						
						JSONObject bookingRule = bookingRules.getJSONObject(k);
						Element bookingRuleElement = ownerDoc.createElementNS(NS_OTA, "ns:BookingRule");

						Attr attributeStart = ownerDoc.createAttribute("Start");
						attributeStart.setValue(bookingRule.getString("start"));
						bookingRuleElement.setAttributeNode(attributeStart);

						Attr attributeEnd = ownerDoc.createAttribute("End");
						attributeEnd.setValue(bookingRule.getString("end"));
						bookingRuleElement.setAttributeNode(attributeEnd);

						bookingRulesElement.appendChild(bookingRuleElement);
					}
					groundServiceElement.appendChild(bookingRulesElement);
				}
				
				// Setting Guest Count ELement
                JSONArray guestCounts = groundService.getJSONArray("guestCount");

                if (guestCounts != null && guestCounts.length() != 0) {
                    Element guestCountsElement = ownerDoc.createElementNS(NS_OTA, "ns:GuestCounts");

                    for (int k = 0; k < guestCounts.length(); k++) {
                    	
                        JSONObject guestCount = guestCounts.getJSONObject(k);
                        Element guestCountElement = ownerDoc.createElementNS(NS_OTA, "ns:GuestCount");

                        guestCountElement.setAttribute("ResGuestRPH", Integer.toString(guestCount.optInt("resGuestRPH")));
                        guestCountsElement.appendChild(guestCountElement);
                    }

                    groundServiceElement.appendChild(guestCountsElement);
                }

				// Setting Total Charge Element
				JSONArray totalCharges = groundService.optJSONArray("totalCharge");

				if (totalCharges != null && totalCharges.length() != 0) {
					for (int z = 0; z < totalCharges.length(); z++) {
						
						JSONObject totalCharge = totalCharges.getJSONObject(z);
						Element totalChargeElement = ownerDoc.createElementNS(NS_PAC, "tns:TotalCharge");

						Attr attributeAmount = ownerDoc.createAttribute("RateTotalAmount");
						attributeAmount.setValue(Integer.toString(totalCharge.optInt("rateTotalAmount")));
						totalChargeElement.setAttributeNode(attributeAmount);

						Attr attributeCurrency = ownerDoc.createAttribute("CurrencyCode");
						attributeCurrency.setValue(totalCharge.getString("currencyCode"));
						totalChargeElement.setAttributeNode(attributeCurrency);

						Attr attributeType = ownerDoc.createAttribute("Type");
						attributeType.setValue(totalCharge.getString("type"));
						totalChargeElement.setAttributeNode(attributeType);

						groundServiceElement.appendChild(totalChargeElement);
					}
				}
				
				// Setting Passenger ELement
                JSONArray passengerArr = groundService.optJSONArray("passenger");
                if (passengerArr != null && passengerArr.length() != 0) {
                    for (int k = 0; k < passengerArr.length(); k++) {
                        JSONObject guestCount = passengerArr.getJSONObject(k);
                        Element passengerElem = ownerDoc.createElementNS(NS_PAC, "tns:Passenger");
                        
                        passengerElem.setAttribute("MinimumAge",(guestCount.optString("minimumAge")));
                        passengerElem.setAttribute("MaximumAge",(guestCount.optString("maximumAge")));
                        passengerElem.setAttribute("MinimumPassengers",(guestCount.optString("minimumPassengers")));
                        passengerElem.setAttribute("MaximumPassengers",(guestCount.optString("maximumPassengers")));
                        passengerElem.setAttribute("PassengerTypeCode",(guestCount.optString("passengerTypeCode")));
                        
                        groundServiceElement.appendChild(passengerElem);
                    }
                }
				
				// Setting Time Span
				JSONObject timeSpan = groundService.getJSONObject("timeSpan");

				if (timeSpan != null && timeSpan.length() != 0) {
					Element timeSpanElement = ownerDoc.createElementNS(NS_PAC, "tns:TimeSpan");

					Attr attributeStart = ownerDoc.createAttribute("Start");
					attributeStart.setValue(timeSpan.getString("start"));
					timeSpanElement.setAttributeNode(attributeStart);

					Attr attributeDuration = ownerDoc.createAttribute("Duration");
					attributeDuration.setValue(timeSpan.getString("duration"));
					timeSpanElement.setAttributeNode(attributeDuration);

					Attr attributeEnd = ownerDoc.createAttribute("End");
					attributeEnd.setValue(timeSpan.getString("end"));
					timeSpanElement.setAttributeNode(attributeEnd);

					groundServiceElement.appendChild(timeSpanElement);
				}

				// Setting remaining Elements
				Element airInclusiveBookingElement = ownerDoc.createElementNS(NS_PAC, "tns:AirInclusiveBooking");
				airInclusiveBookingElement.setTextContent(Boolean.toString(groundService.getBoolean("airInclusiveBooking")));
				groundServiceElement.appendChild(airInclusiveBookingElement);

				Element withExtraNightsElement = ownerDoc.createElementNS(NS_PAC, "tns:WithExtraNights");
				withExtraNightsElement.setTextContent(Boolean.toString(groundService.getBoolean("withExtraNights")));
				groundServiceElement.appendChild(withExtraNightsElement);

				Element declineRequiredElement = ownerDoc.createElementNS(NS_PAC, "tns:DeclineRequired");
				declineRequiredElement.setTextContent(Boolean.toString(groundService.getBoolean("declineRequired")));
				groundServiceElement.appendChild(declineRequiredElement);

				Element purchasableElement = ownerDoc.createElementNS(NS_PAC, "tns:Purchasable");
				purchasableElement.setTextContent(Boolean.toString(groundService.getBoolean("purchasable")));
				groundServiceElement.appendChild(purchasableElement);

				Element flightInfoRequiredElement = ownerDoc.createElementNS(NS_PAC, "tns:FlightInfoRequired");
				flightInfoRequiredElement.setTextContent(Boolean.toString(groundService.getBoolean("flightInfoRequired")));
				groundServiceElement.appendChild(flightInfoRequiredElement);

				Element isIncludedInTourElement = ownerDoc.createElementNS(NS_PAC, "tns:isIncludedInTour");
				isIncludedInTourElement.setTextContent(Boolean.toString(groundService.getBoolean("isIncludedInTour")));
				groundServiceElement.appendChild(isIncludedInTourElement);

				// Appending Ground Service Element to Transfer Component
				transferComponentElement.appendChild(groundServiceElement);
			}
			// Appending Transfer Component Elements to Transfer Components
			transferComponentsElement.appendChild(transferComponentElement);
		}
		// Appending Transfer Components Element to TPA Element
		tpaElement.appendChild(transferComponentsElement);

		return tpaElement;
	}

	public static Element getInsuranceComponentElement(Document ownerDoc, JSONArray insuranceComponents,
			Element tpaElement) {
		Element insuranceComponentsElement = ownerDoc.createElementNS(NS_PAC, "tns:InsuranceComponents");

		for (int i = 0; i < insuranceComponents.length(); i++) {
			
			JSONObject insuranceComponent = insuranceComponents.getJSONObject(i);
			Element insuranceComponentElement = ownerDoc.createElementNS(NS_PAC, "tns:InsuranceComponent");

			//check if dynamicPkgAction is present if not then set 
			if (!insuranceComponent.has(JSON_PROP_DYNAMICPKGACTION))
				insuranceComponent.put(JSON_PROP_DYNAMICPKGACTION, "PackageInsurance");
			
			// Setting DynamicPkgActon
			Attr attributeDynamicPkgAction = ownerDoc.createAttribute(DYNAMICPKGACTION);
			attributeDynamicPkgAction.setValue(insuranceComponent.getString(JSON_PROP_DYNAMICPKGACTION));
			insuranceComponentElement.setAttributeNode(attributeDynamicPkgAction);

			Attr attributeIsIncludedInTour = ownerDoc.createAttribute("isIncludedInTour");
			attributeIsIncludedInTour.setValue(insuranceComponent.getString("isIncludedInTour"));
			insuranceComponentElement.setAttributeNode(attributeIsIncludedInTour);

			// Setting insCoverageDetail Element
			JSONObject insCoverageDetail = insuranceComponent.getJSONObject("insCoverageDetail");
			if (insCoverageDetail != null && insCoverageDetail.length() != 0) {
				Element insCoverageDetailElement = ownerDoc.createElementNS(NS_PAC, "tns:InsCoverageDetail");

				Attr attributeName = ownerDoc.createAttribute("Name");
				attributeName.setValue(insCoverageDetail.getString("name"));
				insCoverageDetailElement.setAttributeNode(attributeName);

				Attr attributeDesc = ownerDoc.createAttribute("Description");
				attributeDesc.setValue(insCoverageDetail.getString("description"));
				insCoverageDetailElement.setAttributeNode(attributeDesc);

				// Appending InsCoverageDetail Element to Insurance Element
				insuranceComponentElement.appendChild(insCoverageDetailElement);
			}
			// Setting Guest Count Element  --priti
			JSONArray guestCounts = insuranceComponent.getJSONArray("guestCount");

			if (guestCounts != null && guestCounts.length() != 0) {
				Element guestCountsElement = ownerDoc.createElementNS(NS_PAC, "tns:GuestCounts");
				for (int y = 0; y < guestCounts.length(); y++) {
					
					JSONObject guestCount = guestCounts.getJSONObject(y);
					Element guestCountElement = ownerDoc.createElementNS(NS_OTA, "ns:GuestCount");

					Attr attributeResGuestRPH = ownerDoc.createAttribute("ResGuestRPH");
					attributeResGuestRPH.setValue(Integer.toString(guestCount.optInt("resGuestRPH")));
					guestCountElement.setAttributeNode(attributeResGuestRPH);

					guestCountsElement.appendChild(guestCountElement);
				}
				insuranceComponentElement.appendChild(guestCountsElement);
			}
			
			// Setting planCost Element
			JSONArray planCosts = insuranceComponent.getJSONArray("planCost");
			if (planCosts != null && planCosts.length() != 0) {
				for (int j = 0; j < planCosts.length(); j++) {
					
					JSONObject planCost = planCosts.getJSONObject(j);
					Element planCostElement = ownerDoc.createElementNS(NS_PAC, "tns:PlanCost");

					Attr attributeCurrency = ownerDoc.createAttribute("CurrencyCode");
					attributeCurrency.setValue(planCost.getString("currencyCode"));
					planCostElement.setAttributeNode(attributeCurrency);

					Attr attributeAmount = ownerDoc.createAttribute("Amount");
					attributeAmount.setValue(Integer.toString(planCost.optInt("amount")));
					planCostElement.setAttributeNode(attributeAmount);

					// Setting Base Premium Element
					JSONObject basePremium = planCost.getJSONObject("basePremium");
					if (basePremium != null && basePremium.length() != 0) {
						Element basePremiumElement = ownerDoc.createElementNS(NS_OTA, "ns:BasePremium");

						Attr attributeBaseCurrency = ownerDoc.createAttribute("CurrencyCode");
						attributeBaseCurrency.setValue(basePremium.optString("currencyCode"));
						basePremiumElement.setAttributeNode(attributeBaseCurrency);

						Attr attributeBaseAmount = ownerDoc.createAttribute("Amount");
						attributeBaseAmount.setValue(Integer.toString(basePremium.optInt("amount")));
						basePremiumElement.setAttributeNode(attributeBaseAmount);

						// Appending Base Premium Element to Plan Cost Element
						planCostElement.appendChild(basePremiumElement);
					}

					// Setting Charge Element
					JSONArray charges = planCost.getJSONArray("charge");
					if (charges != null && charges.length() != 0) {
						Element chargesElement = ownerDoc.createElementNS(NS_OTA, "ns:Charges");
						for (int k = 0; k < charges.length(); k++) {
							
							JSONObject charge = charges.getJSONObject(k);
							Element chargeElement = ownerDoc.createElementNS(NS_OTA, "ns:Charge");

							Attr attributeType = ownerDoc.createAttribute("Type");
							attributeType.setValue(charge.optString("type"));
							chargeElement.setAttributeNode(attributeType);

							// Setting Taxes Element
							JSONObject taxes = charge.getJSONObject("taxes");
							if (taxes != null && taxes.length() != 0) {
								Element taxesElement = ownerDoc.createElementNS(NS_OTA, "ns:Taxes");

								Attr attributeTaxesCurrency = ownerDoc.createAttribute("CurrencyCode");
								attributeTaxesCurrency.setValue(taxes.getString("currencyCode"));
								taxesElement.setAttributeNode(attributeTaxesCurrency);

								Attr attributeTaxesAmount = ownerDoc.createAttribute("Amount");
								attributeTaxesAmount.setValue(Integer.toString(taxes.optInt("amount")));
								taxesElement.setAttributeNode(attributeTaxesAmount);

								// Setting Tax Element
								JSONArray tax = taxes.getJSONArray("tax");

								if (tax != null && tax.length() != 0) {
									for (int y = 0; y < tax.length(); y++) {
										
										JSONObject taxx = tax.getJSONObject(y);
										Element taxElement = ownerDoc.createElementNS(NS_OTA, "ns:Tax");

										Attr attributeTaxCurrency = ownerDoc.createAttribute("CurrencyCode");
										attributeTaxCurrency.setValue(taxx.getString("currencyCode"));
										taxElement.setAttributeNode(attributeTaxCurrency);

										Attr attributeTaxAmount = ownerDoc.createAttribute("Amount");
										attributeTaxAmount.setValue(Integer.toString(taxx.optInt("amount")));
										taxElement.setAttributeNode(attributeTaxAmount);

/*										Attr attributeDP = ownerDoc.createAttribute("DecimalPlaces");
										attributeDP.setValue(Integer.toString(taxx.optInt("decimalPlaces")));
										taxElement.setAttributeNode(attributeDP);*/

										// Setting Description Element
										Element descElement = ownerDoc.createElementNS(NS_OTA, "ns:TaxDescription");

										Attr attributeName = ownerDoc.createAttribute("Name");
										attributeName.setValue(taxx.getString("taxDescription"));
										descElement.setAttributeNode(attributeName);

										// Appending Tax Description to tax element
										taxElement.appendChild(descElement);

										// Appending tax element to taxes ELement
										taxesElement.appendChild(taxElement);
									}
								}
								// Appending Taxes element to charge element
								chargeElement.appendChild(taxesElement);
							}
							// Appending Charge Element to Charges Element
							chargesElement.appendChild(chargeElement);
						}
						// Appending Charges Element to Plan Cost Element
						planCostElement.appendChild(chargesElement);
					}
					// Appending Plan Cost Elements to Insurance Element
					insuranceComponentElement.appendChild(planCostElement);
				}
			}
			// Appending Insurance Component Elements to Insurance Components Element
			insuranceComponentsElement.appendChild(insuranceComponentElement);
		}
		// Appending Insurance Components Element to TPA Element
		tpaElement.appendChild(insuranceComponentsElement);

		return tpaElement;
	}

	static String getRedisKeyForDynamicPackage(JSONObject dynamicPackageObj) {
		StringBuilder strBldr = new StringBuilder(dynamicPackageObj.optString(JSON_PROP_SUPPLIERID));

		strBldr.append('[');
		strBldr.append(dynamicPackageObj.getString("brandName").concat(dynamicPackageObj.getString("tourCode")).concat(dynamicPackageObj.getString("subTourCode")));
		strBldr.append(']');

		return strBldr.toString();
	}

	private static void pushSuppFaresToRedisAndRemove(JSONObject resJson) {

		JSONObject responseHeaderJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject responseBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);

		JSONArray dynamicPackageArray = responseBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		JSONArray bookReferencesArray = responseBodyJson.getJSONArray(JSON_PROP_BOOKREFERENCES);
		responseBodyJson.remove(JSON_PROP_BOOKREFERENCES);

		if (dynamicPackageArray == null || bookReferencesArray == null) {
			logger.warn("dynamicPackageArray or bookReferencesArray is null");
			return;
		}

		Map<String, String> repriceSupplierPricesMap = new HashMap<String, String>();
		for (int i = 0; i < dynamicPackageArray.length(); i++) {

			JSONObject dynamicPackageObj = dynamicPackageArray.getJSONObject(i);
			JSONObject globalInfoJson = dynamicPackageObj.getJSONObject("globalInfo");
			JSONObject componentForRedis = dynamicPackageObj.optJSONObject("componentForRedis");
			dynamicPackageObj.remove("componentForRedis");
			
			int bookRefIdx = dynamicPackageObj.optInt("bookRefIdx");
			JSONObject bookRefJson = bookReferencesArray.getJSONObject(bookRefIdx);
			//JSONObject bookRefJson = dynamicPackageObj.optJSONObject("bookReferences");
			dynamicPackageObj.remove("bookRefIdx");
			
			//Getting ClientCommercial Info
			JSONArray clientCommercialItinTotalJsonArr = globalInfoJson.getJSONObject("total").optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			dynamicPackageObj.getJSONObject("globalInfo").getJSONObject("total").remove(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			
			JSONObject packageTotalFare = globalInfoJson.getJSONObject("total").getJSONObject("totalPackageFare");
			
			JSONObject supplierPricingInfo = globalInfoJson.getJSONObject("total").getJSONObject("supplierPricingInfo");
			dynamicPackageObj.getJSONObject("globalInfo").getJSONObject("total").remove("supplierPricingInfo");
			
			//JSONArray feeArray = getFeeArray(globalInfoJson);
			
			if ( componentForRedis == null || bookRefJson == null) {
				logger.warn("supplierItineraryPricingInfo and bookRefIdx both are null");
				continue;
			}

			JSONObject suppPriceBookInfoJson = new JSONObject();
			suppPriceBookInfoJson.put("componentForRedis", componentForRedis);
			suppPriceBookInfoJson.put("components", dynamicPackageObj.optJSONObject("components"));
			suppPriceBookInfoJson.put("tourDetails", globalInfoJson.getJSONObject("dynamicPkgID").optJSONObject("tourDetails"));
			suppPriceBookInfoJson.put("supplierPricingInfo", supplierPricingInfo);
			suppPriceBookInfoJson.put("packageTotalFare", packageTotalFare);
			suppPriceBookInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercialItinTotalJsonArr);
			suppPriceBookInfoJson.put("bookReferences", bookRefJson);
			
			repriceSupplierPricesMap.put(getRedisKeyForDynamicPackage(dynamicPackageObj), suppPriceBookInfoJson.toString());
		}
		
		responseBodyJson.remove("JSON_PROP_BOOKREFERENCES");
		String redisKey = responseHeaderJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT).concat("|").concat("reprice");
		try ( Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
			redisConn.hmset(redisKey, repriceSupplierPricesMap);
			redisConn.pexpire(redisKey, (long) (HolidaysConfig.getRedisTTLMinutes() * 60 * 1000));
		}
	}

	public static JSONObject getBookReference(Element dynamicPackageElem) {
		// Creating bookReferences Object and supplierBookingFare Object
		JSONObject bookReferencesJson = new JSONObject();
		JSONObject supplierBookingFareJson = new JSONObject();

		supplierBookingFareJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(String.valueOf(XMLUtils.getValueAtXPath(dynamicPackageElem, "./ns:GlobalInfo/ns:Total/@AmountAfterTax")), 0));
		supplierBookingFareJson.put("currencyCode", XMLUtils.getValueAtXPath(dynamicPackageElem, "./ns:GlobalInfo/ns:Total/@CurrencyCode"));
		bookReferencesJson.put("supplierBookingFare", supplierBookingFareJson);

		String tourCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackageElem, "./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/@ID"));
		String subTourCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackageElem, "./ns:Components/ns:PackageOptionComponent/ns:PackageOptions/ns:PackageOption/@QuoteID"));
		String brandName = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackageElem, "./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/ns:CompanyName/@CompanyShortName"));

		String supplierBookingId = brandName + tourCode + subTourCode;
		bookReferencesJson.put("supplierBookingId", supplierBookingId);

		return bookReferencesJson;
	}
}
 
