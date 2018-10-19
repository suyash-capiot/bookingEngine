package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
//import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
//import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class HolidaysGetPackageDetailsProcessorV2 implements HolidayConstants {
	private static final Logger logger = LogManager.getLogger(HolidaysGetPackageDetailsProcessor.class);
	private static Map<String, BigDecimal> paxInfoMap = new LinkedHashMap<String, BigDecimal>(); 
	
	public static String processV2(JSONObject requestJson) throws RequestProcessingException, InternalProcessingException, ValidationException{
		 Element requestElement =null;
		 ServiceConfig opConfig = null;
         JSONObject requestHeader = null, requestBody = null, resJson = null;
         UserContext usrCtx = null;
         JSONObject originalReq=null;
      try {
    	  validateRequestParameters(requestJson);
    	  originalReq = new JSONObject(requestJson.toString());
    	  
          //No flag JSON request to JSON request with flags transformation
          JSONObject transformedRequestJSON = HolidaysBookProcessor.requestJSONTransformation(requestJson);
          opConfig = HolidaysConfig.getOperationConfig("getDetails");
          
          TrackingContext.setTrackingContext(requestJson);
          
          requestHeader = transformedRequestJSON.getJSONObject(JSON_PROP_REQHEADER);
          requestBody = transformedRequestJSON.getJSONObject(JSON_PROP_REQBODY);
          
          usrCtx = UserContext.getUserContextForSession(requestHeader);       
          requestElement = createSIRequest(opConfig, usrCtx, requestHeader, requestBody);
          }
      catch(ValidationException valx) {
			throw valx;
		}
      catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
      
      try {
    	  Element resElem = hitSI(requestElement, opConfig);
    	  
    	  resJson = convertSIResponse(resElem, requestHeader);
            
          filterGetDetailsResponse(requestJson,resJson,originalReq);

          JSONObject resSupplierCommJson = HolidaysSupplierCommercials.getSupplierCommercials(requestJson, resJson,CommercialsOperation.Reprice);
          if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierCommJson.getString(JSON_PROP_TYPE))) {
        	  logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s", resSupplierCommJson.toString()));
        	  return getEmptyResponse(requestBody).toString();
          }
          logger.info(String.format("Supplier Commercial Response = %s", resSupplierCommJson.toString()));
          logger.info(String.format("Client Commercial Request = %s", resSupplierCommJson.toString()));
          
          JSONObject resClientCommJson = HolidaysClientCommercials.getClientCommercials(requestJson, resSupplierCommJson);
          if (BRMS_STATUS_TYPE_FAILURE.equals(resClientCommJson.getString(JSON_PROP_TYPE))) {
        	  logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s", resClientCommJson.toString()));
        	  return getEmptyResponse(requestBody).toString();
          }
          logger.info(String.format("Client Commercial Response = %s", resClientCommJson.toString()));
         
          HolidaysGetPackageCalc.calculatePricesV2(requestJson, resJson, resSupplierCommJson, resClientCommJson, false, usrCtx);
          removeRedisStructure(resJson);
          
          //Apply company offers
          HolidaysCompanyOffers.getCompanyOffers(requestJson, resJson, OffersType.COMPANY_SEARCH_TIME,CommercialsOperation.Search);
          
          return resJson.toString();
      }

      catch (Exception x) {
          logger.error("Exception received while processing", x);
		  throw new InternalProcessingException(x);
      }
  }
	
	private static JSONObject convertSIResponse(Element resElem , JSONObject requestHeader) {
		
		JSONArray errorJsonArray = new JSONArray();
        JSONObject resBodyJson = new JSONObject();
        JSONArray dynamicPackageArray = new JSONArray();
          
        Element[] oTA_wrapperElems = XMLUtils.getElementsAtXPath(resElem,"./pac1:ResponseBody/pac:OTA_DynamicPkgAvailRSWrapper");
        for (Element oTA_wrapperElem : oTA_wrapperElems) {
         
            String supplierIDStr = String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:SupplierID"));
            String sequenceStr = String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:Sequence"));
            
          //Error Response from SI
            Element errorElem[] = XMLUtils.getElementsAtXPath(oTA_wrapperElem, "./ns:OTA_DynamicPkgAvailRS/ns:Errors/ns:Error");
            if(errorElem.length != 0)
          	  errorJsonArray = logTheError(errorElem, errorJsonArray);
            
            Element[] dynamicPackageElem = XMLUtils.getElementsAtXPath(oTA_wrapperElem,"./ns:OTA_DynamicPkgAvailRS/ns:DynamicPackage");
            
            for (Element dynamicPackElem : dynamicPackageElem)
            {
              JSONObject dynamicPackJson = HolidaysSearchProcessor.getSupplierResponseDynamicPackageJSON(dynamicPackElem, true);
              dynamicPackJson.put(JSON_PROP_SUPPLIERID, supplierIDStr);
              dynamicPackJson.put(JSON_PROP_SEQUENCE, sequenceStr);
              
              String tourCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/@ID"));
              String subTourCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:Components/ns:PackageOptionComponent/ns:PackageOptions/ns:PackageOption/@QuoteID"));
              String brandName = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/ns:CompanyName/@CompanyShortName"));

              dynamicPackJson.put(JSON_PROP_TOURCODE, tourCode);
              dynamicPackJson.put(JSON_PROP_SUBTOURCODE, subTourCode);
              dynamicPackJson.put(JSON_PROP_BRANDNAME, brandName);

              dynamicPackageArray.put(dynamicPackJson);
            }
          }
          
          resBodyJson.put(JSON_PROP_DYNAMICPACKAGE, dynamicPackageArray);
          
          JSONObject resJson = new JSONObject();
          resJson.put(JSON_PROP_RESHEADER, requestHeader);
          resJson.put(JSON_PROP_RESBODY, resBodyJson);
          logger.info(String.format("SI Transformed JSON Response = %s", resJson.toString()));
          
          return resJson;
	}

	private static JSONArray logTheError(Element[] errorElem, JSONArray errorJsonArray) {
		errorJsonArray = new JSONArray();
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

	private static Element hitSI(Element requestElement, ServiceConfig opConfig) throws Exception {
		
		logger.info(String.format("SI XML Request = %s", XMLTransformer.toString(requestElement)));
		Element resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), requestElement);
		logger.info(String.format("SI XML Response = %s", XMLTransformer.toString(resElem)));
		
		if(resElem == null)
			throw new Exception("Null response received from SI");
          
		return resElem;
      }

	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject requestHeader,
			JSONObject requestBody) throws Exception {
		
	   Element requestElement = (Element) opConfig.getRequestXMLShell().cloneNode(true);
       Document ownerDoc = requestElement.getOwnerDocument();
       Element requestHeaderElement = XMLUtils.getFirstElementAtXPath(requestElement, "./pac:RequestHeader");
       
       Element userIDElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:UserID");
       userIDElement.setTextContent(requestHeader.getString(JSON_PROP_USERID));
       
       Element sessionIDElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:SessionID");
       sessionIDElement.setTextContent(requestHeader.getString(JSON_PROP_SESSIONID));
       
       Element transactionIDElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:TransactionID");
       transactionIDElement.setTextContent(JSON_PROP_TRANSACTID);
       
       Element supplierCredentialsListElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:SupplierCredentialsList");
       Element requestBodyElement = XMLUtils.getFirstElementAtXPath(requestElement, "./pac1:RequestBody");
       Element wrapperElement = XMLUtils.getFirstElementAtXPath(requestBodyElement, "./pac:OTA_DynamicPkgAvailRQWrapper");
       requestBodyElement.removeChild(wrapperElement);
       
       int sequence = 0;
       JSONArray dynamicPackageRequestArray = requestBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
       for (int i=0; i < dynamicPackageRequestArray.length(); i++) 
       {
	        JSONObject dynamicPackageObj = dynamicPackageRequestArray.getJSONObject(i);
	        sequence++;
	        
	        String supplierID = dynamicPackageObj.getString("supplierID");
	        Element supWrapperElement = null;
	        Element otaAvailRQ = null;
	        Element searchCriteria = null;
	        Element dynamicPackage = null;

	        supplierCredentialsListElement = HolidaysRepriceProcessorV2.getSupplierCredentialsList(ownerDoc, usrCtx, supplierID, sequence, supplierCredentialsListElement);
	        
	        supWrapperElement = (Element) wrapperElement.cloneNode(true);
	        requestBodyElement.appendChild(supWrapperElement);
	            
	        Element supplierIDElement = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./pac:SupplierID");
	        supplierIDElement.setTextContent(supplierID);

	        Element sequenceElement = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./pac:Sequence");
	        sequenceElement.setTextContent(Integer.toString(sequence));

	        searchCriteria = XMLUtils.getFirstElementAtXPath(supWrapperElement,"./ns:OTA_DynamicPkgAvailRQ/ns:SearchCriteria");
	        otaAvailRQ = (Element) searchCriteria.getParentNode();
	
	        String tourCode = dynamicPackageObj.getString("tourCode");
	        String brandName = dynamicPackageObj.getString("brandName");
	        String subTourCode = dynamicPackageObj.getString("subTourCode");
	
	        Element refPoint = XMLUtils.getFirstElementAtXPath(searchCriteria,"./ns:PackageOptionSearch/ns:OptionSearchCriteria/ns:Criterion/ns:RefPoint");
	        Attr attributeBrandCode = ownerDoc.createAttribute("Code");
	        attributeBrandCode.setValue(brandName);
	        refPoint.setAttributeNode(attributeBrandCode);
	
	        Element optionRef = XMLUtils.getFirstElementAtXPath(searchCriteria,"./ns:PackageOptionSearch/ns:OptionSearchCriteria/ns:Criterion/ns:OptionRef");
	        Attr attributeTourCode = ownerDoc.createAttribute("Code");
	        attributeTourCode.setValue(tourCode);
	        optionRef.setAttributeNode(attributeTourCode);
	        
	        dynamicPackage = XMLUtils.getFirstElementAtXPath(otaAvailRQ, "./ns:DynamicPackage");
	        Element packageOptionComponentElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOptionComponent");
           
           if(subTourCode != null && subTourCode.length() > 0)
             packageOptionComponentElement.setAttribute("QuoteID", subTourCode);
           
           Element packageOptionsElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOptions");
           Element packageOptionElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOption");

           packageOptionsElement.appendChild(packageOptionElement);
           packageOptionComponentElement.appendChild(packageOptionsElement);
           
           Element componentsElement = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:Components");
           Element resGuest = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:ResGuests");
           XMLUtils.removeNode(resGuest);
           componentsElement.appendChild(packageOptionComponentElement);
       }
	        return requestElement;
   }

	private static void validateRequestParameters(JSONObject reqJson) throws JSONException, ValidationException {
		HolidaysRequestValidator.validateDynamicPkg(reqJson);
		HolidaysRequestValidator.validateTourCode(reqJson);
		HolidaysRequestValidator.validateSubTourCode(reqJson);
		HolidaysRequestValidator.validateBrandName(reqJson);
		HolidaysRequestValidator.validatePassengerCounts(reqJson);
		//HolidaysRequestValidator.validateResGuestsInfo(reqJson);
		HolidaysRequestValidator.validateComponents(reqJson);
		HolidaysRequestValidator.validateGlobalInfo(reqJson);
	}

	private static void removeRedisStructure(JSONObject resJson) {
		JSONObject responseBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);

		JSONArray dynamicPackageArray = responseBodyJson.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		for (int i = 0; i < dynamicPackageArray.length(); i++) {
			JSONObject dynamicPackageObj = dynamicPackageArray.getJSONObject(i);
			
			JSONObject globalInfoJson = dynamicPackageObj.getJSONObject("globalInfo");
			//JSONObject componentForRedis = dynamicPackageObj.optJSONObject("componentForRedis");
			dynamicPackageObj.remove("componentForRedis");
			dynamicPackageObj.getJSONObject("globalInfo").getJSONObject("total").remove(JSON_PROP_CLIENTENTITYTOTALCOMMS);
			dynamicPackageObj.getJSONObject("globalInfo").getJSONObject("total").remove("supplierPricingInfo");
			
			JSONArray feeArray = globalInfoJson.getJSONArray("fee");
			for (Object fee :feeArray) {
				 JSONObject feeJson = (JSONObject) fee;
				 feeJson.put("type", "ADT");
			}
		}
	}

	private static void filterGetDetailsResponse(JSONObject requestJson,JSONObject resJson, JSONObject originalReq) {
		
		JSONObject reqCurrentDynamicPkg = new JSONObject();
		JSONObject origreqCurrentDynamicPkg = new JSONObject();
		JSONObject resdynamicPkgJson = new JSONObject();
		JSONArray dynamicPkgArray = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		for (int a = 0; a < dynamicPkgArray.length(); a++) {
			resdynamicPkgJson = dynamicPkgArray.getJSONObject(a);
			String brandNameRes = resdynamicPkgJson.getString("brandName");
			String tourCodeRes = resdynamicPkgJson.getString("tourCode");
			String subTourCodeRes = resdynamicPkgJson.getString("subTourCode");
			String resUniqueKey = brandNameRes + tourCodeRes + subTourCodeRes;
			JSONArray reqDynamicPkgArray = requestJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE);
			JSONArray origreqDynamicPkgArray = originalReq.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_DYNAMICPACKAGE);
			 
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
			
			for (int i = 0; i < origreqDynamicPkgArray.length(); i++) {

				JSONObject dynamicPkg = origreqDynamicPkgArray.getJSONObject(i);
				String reqBrandName = dynamicPkg.getString("brandName");
				String reqTourCode = dynamicPkg.getString("tourCode");
				String reqSubTourCode = dynamicPkg.getString("subTourCode");

				String reqUniqueKey = reqBrandName + reqTourCode + reqSubTourCode;

				if (resUniqueKey.equals(reqUniqueKey)) {
					origreqCurrentDynamicPkg = dynamicPkg;
					break;
				}
			}
		}

		
		// create Map of componentWise passenger Type quantity
		Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty = HolidaysGetPackageCalc.retrievePassengerQtyGetDetails(reqCurrentDynamicPkg,resdynamicPkgJson);
		Map<String,JSONObject> compMap = new HashMap<String,JSONObject>();
		
		JSONObject componentJsonRequest = origreqCurrentDynamicPkg.getJSONObject(JSON_PROP_COMPONENTS);
		JSONObject componentJsonResponse = 	resdynamicPkgJson.getJSONObject(JSON_PROP_COMPONENTS);
		
		if(componentJsonRequest.has(JSON_PROP_HOTEL_COMPONENT)&& componentJsonResponse.has(JSON_PROP_HOTEL_COMPONENT)) {
		JSONObject hotelCompRes = componentJsonResponse.getJSONObject(JSON_PROP_HOTEL_COMPONENT);
		JSONObject hotelCompReq = componentJsonRequest.getJSONObject(JSON_PROP_HOTEL_COMPONENT);
		String dynamicPkgAction = hotelCompRes.getString(JSON_PROP_DYNAMICPKGACTION);
		
		if(hotelCompReq!=null && hotelCompReq.length()>1) {
		recreateRoomStayRespfromReq(compMap, componentJsonRequest, hotelCompRes,hotelCompReq,dynamicPkgAction,componentWisePaxTypeQty);
		}else {
			componentJsonResponse.remove(JSON_PROP_HOTEL_COMPONENT);
		}}
		
		else if(componentJsonRequest.has(JSON_PROP_CRUISE_COMPONENT) && componentJsonResponse.has(JSON_PROP_CRUISE_COMPONENT)) {
			JSONObject cruiseCompJson = componentJsonResponse.getJSONObject(JSON_PROP_CRUISE_COMPONENT);
			JSONObject cruiseCompReq = componentJsonRequest.getJSONObject(JSON_PROP_CRUISE_COMPONENT);
			String dynamicPkgAction = cruiseCompJson.getString(JSON_PROP_DYNAMICPKGACTION);
			
			compMap = new HashMap<String,JSONObject>();
			if(cruiseCompReq.length()>0 ) {
			recreateCruiseCompRespfromReq(compMap, cruiseCompJson, dynamicPkgAction, cruiseCompReq,componentWisePaxTypeQty);
			}else {
				componentJsonResponse.remove(JSON_PROP_CRUISE_COMPONENT);
			}}
		
		if(componentJsonResponse.has(JSON_PROP_PRENIGHT)) {
			compMap = new HashMap<String,JSONObject>();
			JSONObject preNightRes = componentJsonResponse.getJSONObject(JSON_PROP_PRENIGHT);
			JSONObject preNightCompReq = componentJsonRequest.getJSONObject(JSON_PROP_PRENIGHT);
			
			String dynamicPkgAction = "prenight";
			if(preNightCompReq.length()>0 ) {
			recreateRoomStayRespfromReq(compMap, componentJsonRequest, preNightRes,preNightCompReq,dynamicPkgAction,componentWisePaxTypeQty);
			}else {
				componentJsonResponse.remove(JSON_PROP_PRENIGHT);
			}
		}
		
		if(componentJsonResponse.has(JSON_PROP_POSTNIGHT)) {
			compMap = new HashMap<String,JSONObject>();
			JSONObject postNightRes = componentJsonResponse.getJSONObject(JSON_PROP_POSTNIGHT);
			JSONObject postNightCompReq = componentJsonRequest.getJSONObject(JSON_PROP_POSTNIGHT);
			String dynamicPkgAction = "postnight";
			if(postNightCompReq.length()>0 ) {
			recreateRoomStayRespfromReq(compMap, componentJsonRequest, postNightRes,postNightCompReq,dynamicPkgAction,componentWisePaxTypeQty);
			}else {
				componentJsonResponse.remove(JSON_PROP_POSTNIGHT);
			}
		}
		//For insurance Component
		JSONArray newInsuranceArray = new JSONArray();
		if (componentJsonRequest.has(JSON_PROP_INSURANCE_COMPONENT)&& componentJsonResponse.has(JSON_PROP_INSURANCE_COMPONENT)) {
			JSONArray insuranceCompReqArr = componentJsonRequest.getJSONArray(JSON_PROP_INSURANCE_COMPONENT);
			JSONArray insuranceCompArr = componentJsonResponse.getJSONArray(JSON_PROP_INSURANCE_COMPONENT);
			compMap = new HashMap<String,JSONObject>();
			BigDecimal paxCount = new BigDecimal(0);
			//BigDecimal amountRes,amount;
			for (int n = 0; n < insuranceCompArr.length(); n++) {
				String dynamicPkgAction = insuranceCompArr.getJSONObject(n).getString("dynamicPkgAction");
				JSONObject insuranceJson = insuranceCompArr.getJSONObject(n);
				String insCoverageDetailResName = (insuranceJson.getJSONObject("insCoverageDetail").getString("name")).toLowerCase();
				String description =(insuranceJson.getJSONObject("insCoverageDetail").getString("description")).toLowerCase();
				String type = (insuranceJson.getJSONObject("insCoverageDetail").getString("type")).toLowerCase();
				String planId = (insuranceJson.getJSONObject("insCoverageDetail").getString("planID")).toLowerCase();
				//JSONArray planCostArr = insuranceJson.getJSONArray("planCost");
				
				for (int m = 0; m < insuranceCompReqArr.length(); m++) {
					//String dynamicPkgActionReq = insuranceCompReqArr.getJSONObject(m).getString("dynamicPkgAction");
					JSONObject insuranceJsonReq = insuranceCompReqArr.getJSONObject(m);
					String insCoverageDetailReqName = (insuranceJsonReq.getJSONObject("insCoverageDetail").getString("name")).toLowerCase();
					String descriptionRq =(insuranceJsonReq.getJSONObject("insCoverageDetail").getString("description")).toLowerCase();
					String typeRq = (insuranceJsonReq.getJSONObject("insCoverageDetail").getString("type")).toLowerCase();
					String planIdRq = (insuranceJsonReq.getJSONObject("insCoverageDetail").getString("planID")).toLowerCase();

					String insuranceKeyReq = insCoverageDetailReqName+descriptionRq+typeRq+planIdRq;
					String insuranceKeyRes= insCoverageDetailResName+description+type+planId;
					if (insuranceKeyReq.equalsIgnoreCase(insuranceKeyRes)) {
					JSONArray planCostArr = insuranceJson.getJSONArray(JSON_PROP_PLANCOST);
					for (int p =0 ; p< planCostArr.length();p++){
						JSONObject planCostJson = planCostArr.getJSONObject(p);
						String paxType = planCostJson.getString(JSON_PROP_PAXTYPE);
						
						String rateDescriptionName = (dynamicPkgAction+insuranceJson.getJSONObject("insCoverageDetail").getString("description")).toLowerCase();
						paxInfoMap = getPaxInfo(componentWisePaxTypeQty, dynamicPkgAction,rateDescriptionName, paxCount, paxType,paxInfoMap);
						compMap.put("plancost"+p,planCostJson);
					}
					
					insuranceJson.remove(JSON_PROP_PLANCOST);
					JSONArray newPlanCostArr= new JSONArray();
					for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
						BigDecimal count = entry.getValue();
						BigDecimal zero = new BigDecimal(0);
						int value = count.compareTo(zero);
						if (value == 1) {
							
							Iterator<Map.Entry<String, JSONObject>> roomIter = compMap.entrySet().iterator();
							while (roomIter.hasNext()) {
								Map.Entry<String, JSONObject> roomEntry = roomIter.next();
								if ((roomEntry.getKey()).contains("plancost")) {
									JSONObject newPlanCostJson = new JSONObject(roomEntry.getValue().toString());
									
									if (!(entry.getKey()).equals("ADT")) {
			
										newPlanCostJson.put("paxType", entry.getKey());
										newPlanCostArr.put( newPlanCostJson);
									
									} else if ((entry.getKey()).equals("ADT")) {
										newPlanCostArr.put(newPlanCostJson);
									}
									insuranceJson.put(JSON_PROP_PLANCOST, newPlanCostArr);
								}
								
							}
				
						}
										
					}compMap.put("ins" + n, insuranceJson);
				}}
			}
			componentJsonResponse.remove(JSON_PROP_INSURANCE_COMPONENT);
			Iterator<Map.Entry<String, JSONObject>> insuranceIter = compMap.entrySet().iterator();
			while (insuranceIter.hasNext()) {
				Map.Entry<String, JSONObject> entry = insuranceIter.next();
				if (entry.getKey().contains("ins")) {
				
				newInsuranceArray.put(entry.getValue());
				componentJsonResponse.put(JSON_PROP_INSURANCE_COMPONENT, newInsuranceArray);
			}
			}
		}
		
		//for transfer component
		JSONArray newTransferArray = new JSONArray();
		if (componentJsonRequest.has(JSON_PROP_TRANSFER_COMPONENT)&& componentJsonResponse.has(JSON_PROP_TRANSFER_COMPONENT)) {
			JSONArray transferCompReqArr = componentJsonRequest.getJSONArray(JSON_PROP_TRANSFER_COMPONENT);
			JSONArray transferCompArr = componentJsonResponse.getJSONArray(JSON_PROP_TRANSFER_COMPONENT);
			BigDecimal paxCount = new BigDecimal(0);
			BigDecimal amountReq =  new BigDecimal(0),amountRes = new BigDecimal(0);
			int amtValue = 1;
			//Map<String,JSONObject> arrivalTrnsfrMap =  new HashMap<String,JSONObject>();
			if(transferCompReqArr.length()>1) {
			for (int i=0;i<transferCompArr.length();i++) {
				JSONObject transferJson = transferCompArr.getJSONObject(i);
				String dynamicPkgActionRes = transferJson.getString(JSON_PROP_DYNAMICPKGACTION);
				String transferKeyReq="",transferKeyRes="";
				for (int t=0;t<transferCompReqArr.length();t++) {
					JSONObject transferReqJson = transferCompReqArr.getJSONObject(t);
					String dynamicPkgActionReq = transferReqJson.getString(JSON_PROP_DYNAMICPKGACTION);
				
				
					compMap = new HashMap<String,JSONObject>();
					newTransferArray = new JSONArray();
				JSONArray groundServiceArr = transferJson.getJSONArray("groundService");
				for (int q = 0; q < groundServiceArr.length(); q++) {
					
					JSONArray groundServiceReqArr = transferReqJson.getJSONArray("groundService");
					JSONObject groundServiceJson = groundServiceArr.getJSONObject(q);
					String pickUpLocation = groundServiceJson.getJSONObject("location").getString("pickUpLocation");
					String starttimeSpan = groundServiceJson.getJSONObject("timeSpan").getString("start");
					String endTimeSpan =  groundServiceJson.getJSONObject("timeSpan").getString("end");
					transferKeyRes = pickUpLocation+starttimeSpan+endTimeSpan;
					
					for (int m = 0; m < groundServiceReqArr.length(); m++) {
						
					if(dynamicPkgActionRes.equalsIgnoreCase(dynamicPkgActionReq) ) {
						
						JSONObject groundServiceReqJson = groundServiceReqArr.getJSONObject(m);
						
						String pickUpLocationReq = groundServiceReqJson.getJSONObject("location").getString("pickUpLocation");
						String starttimeSpanReq = groundServiceReqJson.getJSONObject("timeSpan").getString("start");
						String endTimeSpanReq =  groundServiceReqJson.getJSONObject("timeSpan").getString("end");
						
						transferKeyReq = pickUpLocationReq+starttimeSpanReq+endTimeSpanReq;

						if(transferKeyReq.equalsIgnoreCase(transferKeyRes)) {
						JSONArray totalChargeArray = groundServiceJson.getJSONArray("totalCharge");
						for(t =0 ;t<totalChargeArray.length(); t++) {
							amountRes = totalChargeArray.getJSONObject(t).getBigDecimal("rateTotalAmount"); 
							
							JSONArray totalChargeReqArray = groundServiceReqJson.getJSONArray("totalCharge");
							for(int n =0 ;n<totalChargeReqArray.length(); n++) {
								amountReq = totalChargeReqArray.getJSONObject(n).getBigDecimal("rateTotalAmount"); 
								String paxType = totalChargeReqArray.getJSONObject(n).getString("type");
								amtValue =amountReq.compareTo(amountRes);
							if((transferKeyReq).equalsIgnoreCase(transferKeyRes)&& amtValue==0) {
								String rateDescriptionName =dynamicPkgActionRes.toLowerCase();
								paxInfoMap = getPaxInfo(componentWisePaxTypeQty, dynamicPkgActionRes,
										rateDescriptionName, paxCount, paxType,paxInfoMap);
								
								compMap.put("totalCharge"+t, totalChargeArray.getJSONObject(t));
							}
							}
						}
					
				}}}if((transferKeyReq).equalsIgnoreCase(transferKeyRes)&& amtValue==0) {
					groundServiceJson.remove("totalCharge");
					JSONArray newPlanCostArr= new JSONArray();
					for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
						BigDecimal count = entry.getValue();
						BigDecimal zero = new BigDecimal(0);
						int value = count.compareTo(zero);
						if (value == 1) {
							
							Iterator<Map.Entry<String, JSONObject>> roomIter = compMap.entrySet().iterator();
							while (roomIter.hasNext()) {
								Map.Entry<String, JSONObject> roomEntry = roomIter.next();
								if ((roomEntry.getKey()).contains("totalCharge")) {
									JSONObject newTotalJson = new JSONObject(roomEntry.getValue().toString());
									
									if (!(entry.getKey()).equals("ADT")) {
			
										newTotalJson.put("type", entry.getKey());
										newPlanCostArr.put(newTotalJson);
									
									} else if ((entry.getKey()).equals("ADT")) {
										newPlanCostArr.put(newTotalJson);
									}
									groundServiceJson.put("totalCharge", newPlanCostArr);
								}
							}
						}
										
					}compMap.put("groundService"+q, groundServiceJson);
				}
				}
				}
				transferJson.remove(JSON_PROP_GROUNDSERVICE);
				Iterator<Map.Entry<String, JSONObject>> transferIter = compMap.entrySet().iterator();
				while (transferIter.hasNext()) {
					Map.Entry<String, JSONObject> entry = transferIter.next();
					if(entry.getKey().contains("groundService")) {
					newTransferArray.put(entry.getValue());
					transferJson.put(JSON_PROP_GROUNDSERVICE, newTransferArray);
				}}
			}
			}
			else {
				componentJsonResponse.remove(JSON_PROP_TRANSFER_COMPONENT);
			}
		}
		
		JSONArray feeArray = resdynamicPkgJson.getJSONObject("globalInfo").getJSONArray("fee");
		compMap = new HashMap<String,JSONObject>();
		 for (Object fee :feeArray) {
			 
			 JSONObject feeJson = (JSONObject) fee;
			 feeJson.put("type", "");
			 //compMap.put("fee", feeJson);
		 }
	}

	private static void recreateCruiseCompRespfromReq(Map<String, JSONObject> compMap, JSONObject cruiseCompJson,
		String dynamicPkgAction, JSONObject cruiseCompReq, Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty) {
		JSONArray newCategoryOptionsArr = new JSONArray();
		JSONArray newCategoryOptionArr = new JSONArray();
		JSONArray newtotalArr = new JSONArray();
		JSONArray categoryOptionsArr = cruiseCompJson.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
		String paxType ="";
		JSONObject totalJson= new JSONObject();
		BigDecimal paxCount = new BigDecimal(0);	
		Map<String, JSONObject> categoryMap = new HashMap<String,JSONObject>();
			//for (int i = 0; i < cruiseCompReqArr.length(); i++) {

				String dynamicPkgActionReq = cruiseCompReq.getString(JSON_PROP_DYNAMICPKGACTION);
				if (dynamicPkgActionReq.equalsIgnoreCase(dynamicPkgAction)) {
					JSONArray categoryOptionsReqArr = cruiseCompReq.getJSONArray(JSON_PROP_CATEGORYOPTIONS);
					for (int r = 0; r < categoryOptionsReqArr.length();r++) {
						JSONObject categoryOptionsReqJson = categoryOptionsReqArr.getJSONObject(r);
						JSONArray categoryOptionReqArr = categoryOptionsReqJson.getJSONArray(JSON_PROP_CATEGORYOPTION);
					for (int l = 0; l < categoryOptionReqArr.length(); l++) {
						JSONObject roomStayJsonReq = categoryOptionReqArr.getJSONObject(l);
						String cabinTypeReq = roomStayJsonReq.getString("cabinType");
						String cabinCategoryReq =roomStayJsonReq.getString("cabinCategory");
						for (int k = 0; k < categoryOptionsArr.length(); k++) {
							JSONObject categoryOptionsJson = categoryOptionsArr.getJSONObject(k);
							JSONArray categoryOptionArr = categoryOptionsJson.getJSONArray(JSON_PROP_CATEGORYOPTION);
						
							for (int j = 0; j < categoryOptionArr.length(); j++) {
								JSONObject categoryOptionJson = categoryOptionArr.getJSONObject(j);
								//JSONObject roomDetails = new JSONObject();
								String cabinType = categoryOptionJson.getString(JSON_PROP_CABINTYPE);
								String cabinCategory = categoryOptionJson.getString(JSON_PROP_CABINCATEGORY);
								JSONArray totalArr = categoryOptionJson.getJSONArray("total");
								compMap = new HashMap<String,JSONObject>();
							if ((cabinType.equalsIgnoreCase(cabinTypeReq))&& cabinCategory.equalsIgnoreCase(cabinCategoryReq) ) {
								
									for (Object total : totalArr) {
										
										totalJson = (JSONObject) total;
										paxType = totalJson.getString(JSON_PROP_TYPE);
										
										// to get the paxCount rateDescriptionName is the dynamicPkgAction+RoomType to match
										//with the request key and index is saved to handle multiple same type of rooms
											String rateDescriptionName = (dynamicPkgAction + cabinType+k+j).toLowerCase();
											paxInfoMap = getPaxInfo(componentWisePaxTypeQty, dynamicPkgAction,rateDescriptionName, paxCount, paxType,paxInfoMap);
											if(categoryOptionsReqArr.length()>1) {
											compMap.put("total"+r, totalJson);
									}else if(categoryOptionReqArr.length()>1) {
										compMap.put("total"+l, totalJson);
										}
								}
								
								categoryOptionJson.remove(JSON_PROP_TOTAL);
								newtotalArr = new JSONArray();
							for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
								BigDecimal count = entry.getValue();
								BigDecimal zero = new BigDecimal(0);
								int value = count.compareTo(zero);
								if (value == 1) {
									
									Iterator<Map.Entry<String, JSONObject>> roomIter = compMap.entrySet().iterator();
									while (roomIter.hasNext()) {
										Map.Entry<String, JSONObject> roomEntry = roomIter.next();
										if ((roomEntry.getKey()).contains("total")) {
											JSONObject newtotalJson = new JSONObject(roomEntry.getValue().toString());
											if (!(entry.getKey()).equals("ADT")) {
												newtotalJson.put("type", entry.getKey());
												newtotalArr.put(newtotalJson);
												categoryOptionJson.put("total", newtotalArr);
											} 
											else if ((entry.getKey()).equals("ADT")) {
												newtotalArr.put(newtotalJson);
												categoryOptionJson.put("total", newtotalArr);
											}
										}
									}
								}

								if(categoryOptionsReqArr.length()>1) {
									categoryMap.put("options"+r, categoryOptionJson);
									}
								else if(categoryOptionReqArr.length()>1) {
									categoryMap.put("option"+l, categoryOptionJson);
								}
							}
							}
							}
						}
					}
					}
				}
			
			
			cruiseCompJson.remove(JSON_PROP_CATEGORYOPTIONS);
		
					Iterator<Map.Entry<String, JSONObject>> roomIter = categoryMap.entrySet().iterator();
					while (roomIter.hasNext()) {
						Map.Entry<String, JSONObject> roomEntry = roomIter.next();
						if ((roomEntry.getKey()).contains("options")) {
							newCategoryOptionArr = new JSONArray();
							// newCategoryOptionsArr = new JSONArray();
							JSONObject categoryOptionsJson = new JSONObject();
							JSONObject categoryOptionJson = new JSONObject(roomEntry.getValue().toString());
					
							newCategoryOptionArr.put(categoryOptionJson);
							categoryOptionsJson.put(JSON_PROP_CATEGORYOPTION, newCategoryOptionArr);
							newCategoryOptionsArr.put(categoryOptionsJson);
							cruiseCompJson.put(JSON_PROP_CATEGORYOPTIONS, newCategoryOptionsArr);
							
							}
						else if ((roomEntry.getKey()).contains("option")) {
							JSONObject categoryOptionJson = new JSONObject(roomEntry.getValue().toString());
							
								newCategoryOptionArr.put(categoryOptionJson);
								JSONObject categoryOptionsJson = new JSONObject();
	
								categoryOptionsJson.put(JSON_PROP_CATEGORYOPTION, newCategoryOptionArr);
								categoryOptionsArr.put(categoryOptionsJson);
								cruiseCompJson.put(JSON_PROP_CATEGORYOPTIONS, categoryOptionsArr);
						}
					}		
	}

	private static Map<String, BigDecimal> getPaxInfo(Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty,
			String dynamicPkgAction, String rateDescriptionName, BigDecimal paxCount, String paxType,
			Map<String, BigDecimal> paxInfoMap) {
		Iterator<Map.Entry<String, Map<String, BigDecimal>>> componentPaxTypeQty = componentWisePaxTypeQty.entrySet().iterator();
		while (componentPaxTypeQty.hasNext()) {

			Map.Entry<String, Map<String, BigDecimal>> compPaxEntry = componentPaxTypeQty.next();
			if (!(compPaxEntry.getKey().contains("Transfer"))){
			if (compPaxEntry.getKey().equalsIgnoreCase(rateDescriptionName)) {
			    paxInfoMap = compPaxEntry.getValue();
				Iterator<Map.Entry<String, BigDecimal>> paxTypeQty = compPaxEntry.getValue().entrySet().iterator();
				while (paxTypeQty.hasNext()) {
					Map.Entry<String, BigDecimal> paxTypeEntry = paxTypeQty.next();
					if (paxType.equalsIgnoreCase(paxTypeEntry.getKey()))
						paxCount = paxTypeEntry.getValue();
				}
			} 
			}
			else if ((compPaxEntry.getKey().contains("Transfer"))){
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
		return paxInfoMap;
	}

	private static void recreateRoomStayRespfromReq(Map<String, JSONObject> compMap, JSONObject componentJsonRequest,
			JSONObject hotelCompRes, JSONObject hotelCompReq, String dynamicPkgAction, Map<String, Map<String, BigDecimal>> componentWisePaxTypeQty) {
		BigDecimal paxCount = new BigDecimal(0);
		JSONArray newRoomStayArr = new JSONArray();
		String paxType ="";
		String hotelCodeReq="",hotelCodeRes="";
		Map<String, JSONObject> roomStayMap = new HashMap<String,JSONObject>();
				String dynamicPkgActionReq = hotelCompReq.getString(JSON_PROP_DYNAMICPKGACTION);
				if (dynamicPkgActionReq.toLowerCase().contains(dynamicPkgAction.toLowerCase())) {
					JSONArray roomStayReqArr = hotelCompReq.getJSONObject("roomStays")
							.getJSONArray(JSON_PROP_ROOMSTAY);
					for (int l = 0; l < roomStayReqArr.length(); l++) {
						JSONObject roomStayJsonReq = roomStayReqArr.getJSONObject(l);
						String roomTypeReq = roomStayJsonReq.getString(JSON_PROP_ROOMTYPE);
						String ratePlanCategoryReq =roomStayJsonReq.getString(JSON_PROP_RATEPLANCATEGORY);
						
						JSONArray basicPropertyInfo = roomStayJsonReq.getJSONArray("basicPropertyInfo");
						
						 if (basicPropertyInfo.length()>0)  {
							for(Object basicPropInfo: basicPropertyInfo) {
								JSONObject basicPropInfoJson = (JSONObject)basicPropInfo;
								hotelCodeReq = basicPropInfoJson.getString("hotelCode");
							}
						 }
					    String ratePlanCategorySI ="";
					    JSONArray roomStayResArr = hotelCompRes.getJSONObject("roomStays").getJSONArray(JSON_PROP_ROOMSTAY);
						for (int j = 0; j < roomStayResArr.length(); j++) {
							JSONObject roomStayJson = roomStayResArr.getJSONObject(j);
							String roomTypeSI = roomStayJson.getString(JSON_PROP_ROOMTYPE);
							
							JSONArray basicPropertyInfoRes = roomStayJson.getJSONArray("basicPropertyInfo");
							
							 if (basicPropertyInfoRes.length()>0)  {
								for(Object basicPropInfo: basicPropertyInfoRes) {
									JSONObject basicPropInfoJson = (JSONObject)basicPropInfo;
									hotelCodeRes = basicPropInfoJson.getString("hotelCode");
								}
							 }
							if ((roomTypeSI.equalsIgnoreCase(roomTypeReq)&& hotelCodeReq.equalsIgnoreCase(hotelCodeRes))){
							JSONArray roomRateArr = roomStayJson.getJSONArray(JSON_PROP_ROOMRATE);
							for(int k=0; k<roomRateArr.length(); k++ ) {
								
							JSONObject roomRateJson = roomRateArr.getJSONObject(k);
								ratePlanCategorySI = roomRateArr.getJSONObject(k).optString(JSON_PROP_RATEPLANCATEGORY);
								if ((roomTypeSI.equalsIgnoreCase(roomTypeReq))&& ratePlanCategoryReq.equalsIgnoreCase(ratePlanCategorySI) ) {
								 JSONObject totalJson = roomRateArr.getJSONObject(k).getJSONObject(JSON_PROP_TOTAL);
									paxType = totalJson.getString(JSON_PROP_TYPE);
									String amountAfterTax = totalJson.getString(JSON_PROP_AMOUNTAFTERTAX);
									//Below handled when Monograms suppliers doesnt send amountAfterTax for prenight/postnights
									if(amountAfterTax.equals("")) {
										amountAfterTax= totalJson.getString(JSON_PROP_AMOUNTBEFORETAX);
										totalJson.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTax);
									}
									// to get the paxCount rateDescriptionName is the dynamicPkgAction+RoomType to match
									//with the request key and index is saved to handle multiple same type of rooms
									if (!(paxType.equals("CHD"))||!(paxType.equals("INF"))) {
										String rateDescriptionName = (dynamicPkgAction + roomTypeSI+l).toLowerCase();
										paxInfoMap = getPaxInfo(componentWisePaxTypeQty, dynamicPkgAction,
												rateDescriptionName, paxCount, paxType,paxInfoMap);
										
										compMap.put("roomRate"+k, roomRateJson);
								}}
							}
							if (!(paxType.equals("CHD"))||!(paxType.equals("INF"))) {
							roomStayJson.remove(JSON_PROP_ROOMRATE);
							JSONArray newRoomRateArr= new JSONArray();
						for (Entry<String, BigDecimal> entry : paxInfoMap.entrySet()) {
							BigDecimal count = entry.getValue();
							BigDecimal zero = new BigDecimal(0);
							int value = count.compareTo(zero);
							if (value == 1) {
								
								Iterator<Map.Entry<String, JSONObject>> roomIter = compMap.entrySet().iterator();
								while (roomIter.hasNext()) {
									Map.Entry<String, JSONObject> roomEntry = roomIter.next();
									if ((roomEntry.getKey()).contains("roomRate")) {
										
										JSONObject newRoomRateJson = new JSONObject(roomEntry.getValue().toString());
										JSONObject newTotalJson = newRoomRateJson.getJSONObject(JSON_PROP_TOTAL);
										if (!(entry.getKey()).equals("ADT")) {
				
											newTotalJson.put("type", entry.getKey());
											newRoomRateArr.put(newRoomRateJson);
										
										} else if ((entry.getKey()).equals("ADT")) {
											
											newRoomRateArr.put(newRoomRateJson);
										}
									}
								}
					
							}
											
						}
						roomStayJson.put(JSON_PROP_ROOMRATE, newRoomRateArr);
						roomStayMap.put("room"+j, roomStayJson);						
							}
						}
						}
					}
			
						hotelCompRes.getJSONObject("roomStays").remove("roomStay");
			
						for (Entry<String, JSONObject> compentry : roomStayMap.entrySet()) {
							if ((compentry.getKey()).contains("room")) {
							JSONObject roomStayJson = new JSONObject(compentry.getValue().toString());
							newRoomStayArr.put(roomStayJson);
							hotelCompRes.getJSONObject("roomStays").put(JSON_PROP_ROOMSTAY, newRoomStayArr);
						}}}
	}
	
	public static JSONObject getEmptyResponse(JSONObject reqHdrJson) {
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, new JSONObject());
        return resJson;
    }

	
}

