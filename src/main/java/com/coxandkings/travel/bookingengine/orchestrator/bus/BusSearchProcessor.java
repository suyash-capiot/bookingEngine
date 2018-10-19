package com.coxandkings.travel.bookingengine.orchestrator.bus;


import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.bus.BusConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class BusSearchProcessor implements BusConstants {

	private static final Logger logger = LogManager.getLogger(BusSearchProcessor.class);
	private static String mRcvsPriceCompQualifier = JSON_PROP_RECEIVABLES.concat(SIGMA).concat(".").concat(JSON_PROP_RECEIVABLE).concat(".");

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		try {
			
		 	String commCacheRes =CommercialCacheProcessor.getFromCache(PRODUCT_BUS, "B2CIndiaEng", new JSONArray("[{\"supplierID\": \"ABHIBUS\"},{\"supplierID\": \"MANTIS\"}]"), reqJson);
            if(commCacheRes!=null && !(commCacheRes.equals("error")))
            return commCacheRes;
			//OperationConfig opConfig = null;
            ServiceConfig opConfig = null;
			Element reqElem = null;
			JSONObject reqHdrJson = null, reqBodyJson = null;
	            try
	            {
//	            	 opConfig = BusConfig.getOperationConfig("search");
	            	opConfig = BusConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
	    			 reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true); // xmshell
	    			Document ownerDoc = reqElem.getOwnerDocument();

//	    			TrackingContext.setTrackingContext(reqJson);

	    			 reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
	    			 reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
	    			 
	    			createSIRequest(reqElem, reqHdrJson, reqBodyJson, ownerDoc);
	            }
	            catch(Exception e)
	            {
	            	logger.error("Exception during search request processing", e);
	    			throw new RequestProcessingException(e);
	            }
			
	        Element resElem = null;
		
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), BusConfig.getHttpHeaders(), reqElem);
	        resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			
			if (resElem == null) {
				logger.info("Null response received from SI ");
				throw new Exception("Null response received from SI");
			}
			
			JSONObject resBodyJson = new JSONObject();
			JSONObject availJson = new JSONObject();
			JSONObject resJson = createSIJsonResponse(reqHdrJson, reqBodyJson, resElem, resBodyJson, availJson);
			
			Map<String,Integer> BRMS2SIBusMap = new HashMap<String,Integer>();
			
			
			JSONObject resSupplierJson = SupplierCommercials.getSupplierCommercials(CommercialsOperation.Search,reqJson, resJson,BRMS2SIBusMap);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierJson.getString(JSON_PROP_TYPE))) {
            	logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", resSupplierJson.toString()));
            	return getEmptyResponse(reqHdrJson).toString();
            }
		
			JSONObject resClientJson = ClientCommercials.getClientCommercials(reqJson,resSupplierJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientJson.getString(JSON_PROP_TYPE))) {
            	logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", resClientJson.toString()));
            	return getEmptyResponse(reqHdrJson).toString();
            }
			
			calculatePrices(reqJson, resJson, resClientJson, BRMS2SIBusMap,false);
		
			//putting in cache
			 CommercialCacheProcessor.putInCache(PRODUCT_BUS, "B2CIndiaEng", resJson, reqJson);
			 
			 //CompanyOffers
			 CompanyOffers.getCompanyOffers(reqJson, resJson,  OffersType.COMPANY_SEARCH_TIME);
			return resJson.toString();

		} catch (Exception e) {
			  logger.error("Exception received while processing", e);
			  throw new InternalProcessingException(e);
		}
	}

	private static void createSIRequest(Element reqElem, JSONObject reqHdrJson, JSONObject reqBodyJson,
			Document ownerDoc) {
		String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
		String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
		String userID = reqHdrJson.getString(JSON_PROP_USERID);

		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT,PROD_CATEG_SUBTYPE_BUS);

		createHeader(reqElem, sessionID, transactionID, userID);

		
		int sequence = 1;
		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
				"./bus:RequestHeader/com:SupplierCredentialsList");
		for (ProductSupplier prodSupplier : prodSuppliers) {
			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc,sequence++));
		}

		
		XMLUtils.setValueAtXPath(reqElem, "./busi:RequestBody/ota:OTA_VehAvailRateRQ2/ota:sourceStationId",
				reqBodyJson.getString(JSON_PROP_SOURCE));
		XMLUtils.setValueAtXPath(reqElem, "./busi:RequestBody/ota:OTA_VehAvailRateRQ2/ota:destinationStationId",
				reqBodyJson.getString(JSON_PROP_DESTINATION));
		XMLUtils.setValueAtXPath(reqElem, "./busi:RequestBody/ota:OTA_VehAvailRateRQ2/ota:journeyDate",
				reqBodyJson.getString(JSON_PROP_JOURNEYDATE));
	}

	private static JSONObject createSIJsonResponse(JSONObject reqHdrJson, JSONObject reqBodyJson, Element resElem,
			JSONObject resBodyJson, JSONObject availJson) throws ValidationException {
		
		JSONArray availabilityJsonArr = new JSONArray();
		JSONArray serviceArr = new JSONArray();

		Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem,
				"./busi:ResponseBody/bus:OTA_VehAvailRateRS2Wrapper");
		for (Element wrapperElem : wrapperElems) {
			Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_VehAvailRateRS2");
				getSupplierResponseAvailableTripsJSON(resBodyElem, serviceArr,reqHdrJson,reqBodyJson);
		}
		if(serviceArr.length()>0)
		{
			availJson.put(JSON_PROP_SERVICE, serviceArr);
			availabilityJsonArr.put(availJson);
		}
		if(availabilityJsonArr.length()>0)
		{
			resBodyJson.put(JSON_PROP_AVAILABILITY, availabilityJsonArr);
		}
		else
		{
			resBodyJson.put("SI-ERR", "Empty response received from SI ..");
		}

		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		
		resJson.put(JSON_PROP_RESBODY, resBodyJson);
		
		
		return resJson;
	}

	public static Object getEmptyResponse(JSONObject reqHdrJson) {
		JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, new JSONObject());
        return resJson;
	}

	public static void calculatePrices(JSONObject reqJson, JSONObject resJson, JSONObject resClientJson,
			Map<String,Integer> BRMS2SIBusMap,boolean retainSuppFares) {

		
		
		JSONArray entityCommJsonArr = new JSONArray();
		JSONArray passDetailsArr = new JSONArray();
		JSONArray busServiceArr = new JSONArray();
		JSONArray serviceArr = new JSONArray();
		JSONArray availArr = new JSONArray();
		JSONArray fareArr = new JSONArray();

		Map<String, String> commToTypeMap = getClientCommercialsAndTheirType(resClientJson);
		
		Map<String,Integer> suppIndexMap = new HashMap<String,Integer>();
		
		 String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
	     String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
	     JSONArray ccommSuppBRIJsonArr=null;
	     try
	    {
	    	ccommSuppBRIJsonArr = resClientJson.getJSONObject(JSON_PROP_RESULT)
	    			.getJSONObject(JSON_PROP_EXECUTIONRES).getJSONArray(JSON_PROP_RESULTS).getJSONObject(0)
	    			.getJSONObject("value")
	    			.getJSONObject(JSON_PROP_CLIENTTRANRULES)
	    			.getJSONArray(JSON_PROP_BUSSRULEINTAKE);
	    	
	    }
	    catch(Exception e)
	    {
	    	 logger.info("client commercials not found");
	    	 return;
	    }
	     
	     Map<Integer, JSONArray> ccommSuppBRIJsonMap = new HashMap<Integer, JSONArray>();
	     Integer briNo = 1;
	     for (int m = 0; m < ccommSuppBRIJsonArr.length(); m++)
			{
				busServiceArr = ccommSuppBRIJsonArr.getJSONObject(m).getJSONArray(JSON_PROP_BUSSERVICEDETAILS);
				 ccommSuppBRIJsonMap.put(briNo, busServiceArr);
             briNo++;
			}
		availArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_AVAILABILITY);
        
		for (int p = 0; p < availArr.length(); p++) {
		
			serviceArr = availArr.getJSONObject(p).optJSONArray(JSON_PROP_SERVICE);
			if(serviceArr!=null) {
			for (int q = 0; q < serviceArr.length(); q++) {
				
				JSONArray suppFareArr = new JSONArray();
				fareArr = serviceArr.getJSONObject(q).getJSONArray(JSON_PROP_FARESARRAY);
				BigDecimal suppFare = new BigDecimal(0);
				JSONObject serviceJson = serviceArr.getJSONObject(q);
				String suppId = serviceJson.getString(JSON_PROP_SUPPREF);
				
				for (int r = 0; r < fareArr.length(); r++) {
				     PriceComponentsGroup totalFareCompsGroup = new PriceComponentsGroup(JSON_PROP_BUSSERVICETOTALFARE, clientCcyCode, new BigDecimal(0), true);

					JSONObject fareJson = fareArr.getJSONObject(r);
					BigDecimal totalFareAmt = new BigDecimal(0);
					briNo = BRMS2SIBusMap.get(String.format("%d%c%d%c%d", p, '|', q,'|', r));
					JSONArray ccommBusDtlsJsonArr = ccommSuppBRIJsonMap.get(briNo);
					if (ccommBusDtlsJsonArr == null) {
						logger.info(String.format(
                                "BRMS/ClientComm: \"businessRuleIntake\" JSON element for supplier %s not found.", suppId));
						continue;
					}
					// Required for search response,busdetails Objects mapped to Different BRI's
                    // as per supplier
					int idx = 0;
                    if (suppIndexMap.containsKey(briNo)) {
                                    idx = suppIndexMap.get(briNo) + 1;
                    }
                    suppIndexMap.put(suppId, idx);
                    JSONObject busDtlsJSon = ccommBusDtlsJsonArr.getJSONObject(idx);
                    passDetailsArr = busDtlsJSon.getJSONArray(JSON_PROP_PASSANGERDETAILS);

					// Assumptions - only one fare present in SI response. not able to handle
							// multiple fares because identification of these fares is not possible.
							
					//TODO: supplierfare added?? or replaced by commercial amts from addition and markup
//							totalFareAmt = serviceArr.getJSONObject(q).getJSONArray(JSON_PROP_FARESARRAY).getJSONObject(r)
//									.getBigDecimal("fare");
					BigDecimal paxCount  = new BigDecimal(1);
					for (int j = 0; j < passDetailsArr.length(); j++) {
						entityCommJsonArr = passDetailsArr.getJSONObject(j).optJSONArray(JSON_PROP_ENTITYCOMMS);
						if (entityCommJsonArr == null) {
	        				logger.warn("Client commercials calculations not found");
	        			}
						for (int k = 0; entityCommJsonArr != null && k < entityCommJsonArr.length(); k++) {
							JSONObject clientEntityCommJson = entityCommJsonArr.getJSONObject(k);
							JSONArray additionalCommArr = clientEntityCommJson.getJSONArray(JSON_PROP_ADDITIONCOMMDETAILS);
							for(int x=0;x<additionalCommArr.length();x++)
							{
								JSONObject additionalCommsJson = additionalCommArr.getJSONObject(x);
	    						String additionalCommName = additionalCommsJson.optString(JSON_PROP_COMMNAME);//fetch comm	Name from additionalcommercialDetails object
	    						if(commToTypeMap!=null)
	    						{
	    							if (COMM_TYPE_RECEIVABLE.equals(commToTypeMap.get(additionalCommName))) {//is the additionalCommName receivable?
		    							String additionalCommCcy = additionalCommsJson.getString(JSON_PROP_COMMCCY);//get comm currency from additionalcommercialDetails Object
		    							BigDecimal additionalCommAmt = additionalCommsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket));
		    							totalFareAmt = totalFareAmt.add(additionalCommAmt);
		    							totalFareCompsGroup.add(mRcvsPriceCompQualifier.concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt.multiply(paxCount));

		    						}
	    						}
	    						else
	    						{
	    							logger.info("receivables not found for passenger..");
	    						}
								
							}
							JSONObject markupCalcJson = clientEntityCommJson
									.optJSONObject(JSON_PROP_MARKUPCOMMERCIALS);
							if (markupCalcJson == null) {
								totalFareAmt = totalFareAmt.add(fareJson.getBigDecimal("fare"));
							}
							else
							{
								totalFareAmt = totalFareAmt.add(markupCalcJson.getBigDecimal("totalFare"));
							}
							
						}
						totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode, totalFareAmt.multiply(paxCount));
						
						suppFare = fareArr.getJSONObject(r).getBigDecimal("fare");
						 fareJson.put(JSON_PROP_BUSSERVICETOTALFARE, totalFareCompsGroup.toJSON());
						fareArr.getJSONObject(r).put("fare", totalFareAmt);
						
						fareArr.getJSONObject(r).remove("fare");
						fareArr.getJSONObject(r).remove("currency");
					}

					
					
					if (retainSuppFares) {
						
						JSONObject suppFaresJson = new JSONObject();
						JSONObject seatFare = fareArr.getJSONObject(r);
						JSONObject servicetotalFare = fareJson.getJSONObject("serviceTotalFare");
						JSONObject baseFare = servicetotalFare.getJSONObject("baseFare");
						suppFaresJson.put(JSON_PROP_SEATNO, seatFare.get(JSON_PROP_SEATNO));
						suppFaresJson.put("fare", baseFare.getBigDecimal("amount"));
						suppFaresJson.put(JSON_PROP_CURRENCY, baseFare.getString("currencyCode"));
						suppFaresJson.put("ladiesSeat", seatFare.opt("ladiesSeat"));
						suppFareArr.put(suppFaresJson);
						serviceArr.getJSONObject(q).put(JSON_PROP_SUPPLIERFARES, suppFareArr);
						
						
						fareJson.put(JSON_PROP_CLIENTCOMMITININFO, entityCommJsonArr);
						}
				}			}
		}
		}


	}
	
	
	private static Map<String, String> getClientCommercialsAndTheirType(JSONObject resClientJson) {
		JSONArray commHeadJsonArr = null;
		 JSONArray entDetaiJsonArray= null;
	        JSONObject commHeadJson = null;
	        JSONObject scommBRIJson = null;
	        Map<String, String> commToTypeMap = new HashMap<String, String>();
	        try
	        {
	        	JSONArray scommBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(resClientJson);
		        
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
	        catch(Exception e)
	        {
	        	logger.info("client commercials");
	        	return null;
	        }
			
	        
	}

	private static JSONArray getClientCommercialsBusinessRuleIntakeJSONArray(JSONObject resClientJson) {
		return resClientJson.getJSONObject(JSON_PROP_RESULT).getJSONObject(JSON_PROP_EXECUTIONRES).getJSONArray(JSON_PROP_RESULTS).getJSONObject(0).getJSONObject("value").getJSONObject(JSON_PROP_CLIENTTRANRULES).getJSONArray(JSON_PROP_BUSSRULEINTAKE);

	}

	public static void getSupplierResponseAvailableTripsJSON(Element resBodyElem, JSONArray serviceArr,JSONObject reqHdrJson,JSONObject reqBodyJson) throws ValidationException {

		getSupplierResponseAvailableTripsJSON(reqHdrJson,reqBodyJson,resBodyElem, serviceArr, false, 0);

	}

	private static void getSupplierResponseAvailableTripsJSON(JSONObject reqHdrJson,JSONObject reqBodyJson,Element resBodyElem, JSONArray serviceArr,
			boolean generateBookRefIdx, int bookRefIdx) throws ValidationException {
		
		
		Element[] serviceElems = XMLUtils.getElementsAtXPath(resBodyElem, "./ota:availableTrips/ota:Service");
			if(serviceElems.length==0)
			{
//				throw new ValidationException(reqHdrJson,"SI-ERR","Empty response received from SI.");
				logger.info("Empty response received from SI.");
			}
			else
			{
				for (Element serviceElem : serviceElems) {
					JSONObject serviceJson = getServiceJSON(resBodyElem, serviceElem);
					serviceJson.put(JSON_PROP_SUPPREF,XMLUtils.getValueAtXPath((Element) resBodyElem.getParentNode(), "./bus:SupplierID"));
					serviceJson.put(JSON_PROP_SOURCE, reqBodyJson.get(JSON_PROP_SOURCE));
					serviceJson.put(JSON_PROP_DESTINATION, reqBodyJson.get(JSON_PROP_DESTINATION));
					serviceJson.put(JSON_PROP_JOURNEYDATE, reqBodyJson.get(JSON_PROP_JOURNEYDATE));
					serviceArr.put(serviceJson);

				}
			}
	}

	private static JSONObject getServiceJSON(Element resBodyElem, Element serviceElem) {
		
		JSONObject serviceJson = new JSONObject();
		serviceJson.put(JSON_PROP_SUPPREF,XMLUtils.getValueAtXPath((Element) resBodyElem.getParentNode(), "./bus:SupplierID"));
		serviceJson.put("AC", XMLUtils.getValueAtXPath(serviceElem, "./AC"));

		serviceJson.put("arrivalTime", XMLUtils.getValueAtXPath(serviceElem, "./ota:arrivalTime"));

		serviceJson.put("availableSeats", Utils.convertToInt(XMLUtils.getValueAtXPath(serviceElem, "./ota:availableSeats"),0));
		Element boardingTimeElems[] = XMLUtils.getElementsAtXPath(serviceElem, "./ota:boardingTimes");
		JSONArray boardingDetailsArr = new JSONArray();
		for (Element boardingElem : boardingTimeElems) {
			JSONObject getBoardingJson = new JSONObject();

			getBoardingJson = getDetailsJson(boardingElem);
			if (getBoardingJson == null)
				serviceJson.put("boardingTimes", "");
			else {
				boardingDetailsArr.put(getBoardingJson);

			}
		}
		serviceJson.put("boardingTimes", boardingDetailsArr);
		Element droppingTimesElems[] = XMLUtils.getElementsAtXPath(serviceElem, "./ota:droppingTimes");
		JSONArray droppingDetailsArr = new JSONArray();
		for (Element droppingTimeElem : droppingTimesElems) {
			JSONObject getdroppingJson = new JSONObject();

			getdroppingJson = getDetailsJson(droppingTimeElem);
			if (getdroppingJson == null)
				serviceJson.put("droppingTimes", "");
			else {
				droppingDetailsArr.put(getdroppingJson);
			}
		}
		serviceJson.put("droppingTimes", droppingDetailsArr);
		
		//TODO: how to handle this cancellation policy 

		serviceJson.put("cancellationPolicy", XMLUtils.getValueAtXPath(serviceElem, "./ota:cancellationPolicy"));

		serviceJson.put("departureTime", XMLUtils.getValueAtXPath(serviceElem, "./ota:departureTime"));

		serviceJson.put(JSON_PROP_JOURNEYDATE, XMLUtils.getValueAtXPath(serviceElem, "./ota:doj"));

		serviceJson.put(JSON_PROP_OPERATORID, XMLUtils.getValueAtXPath(serviceElem, "./ota:operatorId"));
		
		serviceJson.put(JSON_PROP_SERVICEID, XMLUtils.getValueAtXPath(serviceElem, "./ota:ServiceId"));
		// serviceJson.put("fares", getFaresArray(serviceElem));

		Element faressElems[] = XMLUtils.getElementsAtXPath(serviceElem, "./ota:fares/ota:fare");
		for (Element faresElem : faressElems) {
			JSONArray fareArr = new JSONArray();
			getFaresArray(resBodyElem, serviceElem, faresElem,fareArr);
			serviceJson.put(JSON_PROP_FARESARRAY, fareArr);

		}
		
//		serviceJson.put("serviceTaxAbsolute", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(serviceElem, "./ota:fareDetails/ota:serviceTaxAbsolute"), 0));
//		serviceJson.put("baseFare", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(serviceElem, "./ota:fareDetails/ota:baseFare"), 0));
//		serviceJson.put("markupFareAbsolute", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(serviceElem, "./ota:fareDetails/ota:markupFareAbsolute"), 0));
//		serviceJson.put("operatorServiceChargePercentage", XMLUtils.getValueAtXPath(serviceElem, "./ota:fareDetails/ota:operatorServiceChargePercentage"));
//		serviceJson.put("totalFare", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(serviceElem, "./ota:fareDetails/ota:totalFare"), 0));
//		serviceJson.put("markupFarePercentage", XMLUtils.getValueAtXPath(serviceElem, "./ota:fareDetails/ota:markupFarePercentage"));
//		serviceJson.put("operatorServiceChargeAbsolute", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(serviceElem, "./ota:fareDetails/ota:operatorServiceChargeAbsolute"), 0));
//		serviceJson.put("serviceTaxPercentage", XMLUtils.getValueAtXPath(serviceElem, "./ota:fareDetails/ota:serviceTaxPercentage"));
//		serviceJson.put("currency", XMLUtils.getValueAtXPath(serviceElem, "./ota:fareDetails/ota:currency"));

		serviceJson.put("serviceId", XMLUtils.getValueAtXPath(serviceElem, "./ota:ServiceId"));

		
		return serviceJson;
	}

	private static void getFaresArray(Element resBodyElem, Element serviceElem, Element faresElem,JSONArray fareArr) {


		JSONObject fare = new JSONObject();
		
		fare.put("fare", new BigDecimal(faresElem.getTextContent()));
		fare.put(JSON_PROP_SERVICEID, XMLUtils.getValueAtXPath(serviceElem, "./ota:ServiceId"));
//		fare.put("currencyCode", XMLUtils.getValueAtXPath(faresElem, "./@currency"));
//		fare.put("routeId", XMLUtils.getValueAtXPath(serviceElem, "./ota:id"));
		fare.put("mTicketEnabled",XMLUtils.getValueAtXPath(serviceElem, "./ota:mTicketEnabled"));
		fare.put("nonAC", XMLUtils.getValueAtXPath(serviceElem, "./ota:nonAC"));
//		fare.put("tatkalTime", XMLUtils.getValueAtXPath(serviceElem, "./ota:tatkalTime"));
		fare.put(JSON_PROP_OPERATORID, XMLUtils.getValueAtXPath(serviceElem, "./ota:operatorId"));
		fare.put("operatorName", XMLUtils.getValueAtXPath(serviceElem, "./ota:operatorName"));
		fare.put("busLabel", XMLUtils.getValueAtXPath(serviceElem, "./ota:busLabel"));
		fare.put("busType", XMLUtils.getValueAtXPath(serviceElem, "./ota:busType"));
		fare.put("busTypeId", XMLUtils.getValueAtXPath(serviceElem, "./ota:busTypeId"));
		fare.put("busNumber", XMLUtils.getValueAtXPath(serviceElem, "./ota:busNumber"));
		fare.put("serviceNumber", XMLUtils.getValueAtXPath(serviceElem, "./ota:ServiceNumber"));
		fare.put("travelTime", XMLUtils.getValueAtXPath(serviceElem, "./ota:TravelTime"));
		fare.put("amenities", XMLUtils.getValueAtXPath(serviceElem, "./ota:Amenities"));
//		fare.put("isRtc", XMLUtils.getValueAtXPath(serviceElem, "./ota:isRtc"));
//		fare.put("routeId", XMLUtils.getValueAtXPath(serviceElem, "./ota:routeId"));
		fare.put("seater", XMLUtils.getValueAtXPath(serviceElem, "./ota:Seater"));
//		fare.put("seaterFareNAC", XMLUtils.getValueAtXPath(serviceElem, "./ota:SeaterFareNAC"));
//		fare.put("seaterFareAC", XMLUtils.getValueAtXPath(serviceElem, "./ota:SeaterFareAC"));
		fare.put("partialCancellationAllowed",
				Boolean.valueOf(XMLUtils.getValueAtXPath(serviceElem, "./ota:partialCancellationAllowed")));
		fare.put("sleeper", XMLUtils.getValueAtXPath(serviceElem, "./ota:sleeper"));
//		fare.put("sleeperFareNAC", XMLUtils.getValueAtXPath(serviceElem, "./ota:SleeperFareNAC"));
//		fare.put("sleeperFareAC", XMLUtils.getValueAtXPath(serviceElem, "./ota:SleeperFareAC"));
		fare.put(JSON_PROP_LAYOUTID, XMLUtils.getValueAtXPath(serviceElem, "./ota:layOutId"));
//		fare.put("commPCT", XMLUtils.getValueAtXPath(serviceElem, "./ota:CommPCT"));
//		fare.put("commAmount", XMLUtils.getValueAtXPath(serviceElem, "./ota:CommAmount"));
//		fare.put("routeCode", XMLUtils.getValueAtXPath(serviceElem, "./ota:RouteCode"));
		fare.put("vehicleType", XMLUtils.getValueAtXPath(serviceElem, "./ota:vehicleType"));
		
		fareArr.put(fare);

	}

	

	private static JSONObject getDetailsJson(Element detailElem) {
		JSONObject getDetailsJson = new JSONObject();

		getDetailsJson.put("Id", XMLUtils.getValueAtXPath(detailElem, "./ota:Details/ota:bpId"));

		getDetailsJson.put("name", XMLUtils.getValueAtXPath(detailElem, "./ota:Details/ota:bpName"));

		getDetailsJson.put("location", XMLUtils.getValueAtXPath(detailElem, "./ota:Details/ota:location"));

		getDetailsJson.put("prime", XMLUtils.getValueAtXPath(detailElem, "./ota:Details/ota:prime"));

		getDetailsJson.put("time", XMLUtils.getValueAtXPath(detailElem, "./ota:Details/ota:time"));

		getDetailsJson.put("address", XMLUtils.getValueAtXPath(detailElem, "./ota:Details/ota:address"));

		getDetailsJson.put("landMark", XMLUtils.getValueAtXPath(detailElem, "./ota:Details/ota:landMark"));

		getDetailsJson.put("telephone", XMLUtils.getValueAtXPath(detailElem, "./ota:Details/ota:Telephone"));
		return getDetailsJson;
	}

	public static void createHeader(Element reqElem, String sessionID, String transactionID, String userID) {
		

		Element sessionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./bus:RequestHeader/com:SessionID");
		sessionElem.setTextContent(sessionID);

		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./bus:RequestHeader/com:TransactionID");
		transactionElem.setTextContent(transactionID);

		Element userElem = XMLUtils.getFirstElementAtXPath(reqElem, "./bus:RequestHeader/com:UserID");
		userElem.setTextContent(userID);

	}
	

	
	public static String getRedisKeyForBusService(JSONObject serviceJson)
	{
		StringBuilder strBldr = new StringBuilder(serviceJson.optString(JSON_PROP_SUPPREF));
		strBldr.append(serviceJson.getString(JSON_PROP_SERVICEID));
		strBldr.append(serviceJson.getString(JSON_PROP_OPERATORID));
		return strBldr.toString();
		
	}
}
