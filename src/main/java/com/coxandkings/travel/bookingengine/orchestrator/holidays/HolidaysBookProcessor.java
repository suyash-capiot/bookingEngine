package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class HolidaysBookProcessor implements HolidayConstants {

  private static final Logger logger = LogManager.getLogger(HolidaysBookProcessor.class);
  
  public static String process(JSONObject requestJson) throws RequestProcessingException, InternalProcessingException, ValidationException{
	  JSONObject responseJSON = new JSONObject();
	  Element requestElement = null;
	  //OperationConfig opConfig = null;
	  ServiceConfig opConfig = null;
	  KafkaBookProducer bookProducer = null;
	  JSONObject requestHeader = null;
	  
    try {
    	logger.info(String.format("Holidaybookprocessor start"));
	    
    	TrackingContext.setTrackingContext(requestJson); 
	    opConfig = HolidaysConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
	    
	    validateRequestParameters(requestJson);
	    
	    requestHeader = requestJson.getJSONObject(JSON_PROP_REQHEADER);
	    JSONObject requestBody = requestJson.getJSONObject(JSON_PROP_REQBODY);
	    
	    UserContext usrCtx = UserContext.getUserContextForSession(requestHeader);
	    enrichRequestBodyJson(requestHeader, requestBody);
	    bookProducer = new KafkaBookProducer();
	   	sendPreBookingKafkaMessage(bookProducer, requestJson,usrCtx);
	       	
	    JSONObject transformedRequestJSON = requestJSONTransformation(requestJson);
	    requestHeader = transformedRequestJSON.getJSONObject(JSON_PROP_REQHEADER);
	    requestBody = transformedRequestJSON.getJSONObject(JSON_PROP_REQBODY);
	   	    
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
		Element responseElement = callSI(requestElement,opConfig);
		
		OperationsToDoProcessor.callOperationTodo(ToDoTaskName.BOOK, ToDoTaskPriority.HIGH, ToDoTaskSubType.BOOKING, "", requestJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID),requestHeader,"");
        
		responseJSON = convertSIResponse(responseElement, requestHeader);
		
		sendPostBookingKafkaMessage(bookProducer, requestJson, responseJSON);
		logger.info(String.format("Booking process end"));
		
        return responseJSON.toString();
      }
      catch (Exception x) {
			logger.error("Exception received while processing", x);
            throw new InternalProcessingException(x);
		} 
  }
  
  private static JSONObject convertSIResponse(Element responseElement, JSONObject requestHeader) {
	  
	  	JSONObject responseJSON = new JSONObject();
	  	JSONObject resBodyJson = new JSONObject();
      //--------------------------OTA_DynamicPkgBookRSWrapper Start -----------------------------------
		Element oTA_wrapperElems [] = XMLUtils.getElementsAtXPath(responseElement, "./pac1:ResponseBody/pac:OTA_DynamicPkgBookRSWrapper");
		
		//For Loop
		for(Element oTA_wrapperElem : oTA_wrapperElems) {
		
			//OTA_DynamicPkgBookRS
			Element ota_DynamicPkgBookRSElement = XMLUtils.getFirstElementAtXPath(oTA_wrapperElem, "./ns:OTA_DynamicPkgBookRS");
			
			//Error Response from SI
			Element errorElem[] = XMLUtils.getElementsAtXPath(ota_DynamicPkgBookRSElement, "./ns:Errors/ns:Error");
			if(errorElem.length != 0)
				logTheError(errorElem);
			
			//To access <n1:DynamicPackage> start
			Element dynamicPkgElems [] = XMLUtils.getElementsAtXPath(ota_DynamicPkgBookRSElement, "./ns:DynamicPackage");
			JSONArray dynamicPkgArray = new JSONArray();
		
			for(Element dynamicPackageElement : dynamicPkgElems) {
				
				JSONObject dynamicPkgJSON=getDynamicPkgComponent(dynamicPackageElement);
				
				dynamicPkgJSON.put(JSON_PROP_SUPPLIERID, String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:SupplierID")));
				dynamicPkgJSON.put(JSON_PROP_SEQUENCE, String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:Sequence")));
				dynamicPkgJSON.put(JSON_PROP_SUBTOURCODE, String.valueOf(XMLUtils.getValueAtXPath(dynamicPackageElement, "./ns:Components/ns:PackageOptionComponent/ns:PackageOptions/ns:PackageOption/@QuoteID")));
				dynamicPkgJSON.put(JSON_PROP_BRANDNAME, String.valueOf(XMLUtils.getValueAtXPath(dynamicPackageElement, "./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/ns:CompanyName/@CompanyShortName")));
				dynamicPkgJSON.put(JSON_PROP_TOURCODE, String.valueOf(XMLUtils.getValueAtXPath(dynamicPackageElement, "./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/@ID")));
				
				JSONObject suppBookJson = new JSONObject();
				suppBookJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:SupplierID"));
				suppBookJson.put(JSON_PROP_BOOKINGID, XMLUtils.getValueAtXPath(dynamicPackageElement,"./ns:Components/ns:PackageOptionComponent/ns:UniqueID/@ID"));
				dynamicPkgJSON.put(JSON_PROP_SUPPBOOKREFS,suppBookJson);
				dynamicPkgArray.put(dynamicPkgJSON);
			}
			//To access <n1:DynamicPackage> end
			resBodyJson.put(JSON_PROP_DYNAMICPACKAGE, dynamicPkgArray);
			//OTA_DynamicPkgBookRS Ends
		}
		//--------------------------OTA_DynamicPkgBookRSWrapper end -----------------------------------
		
		responseJSON.put(JSON_PROP_RESHEADER, requestHeader);
		responseJSON.put(JSON_PROP_RESBODY, resBodyJson);
		
		logger.info(String.format("SI JSON Response for Holidaybookprocessor = %s",responseJSON));
		
	return responseJSON;
}

//private static Element callSI(Element requestElement, OperationConfig opConfig) throws Exception {
  private static Element callSI(Element requestElement, ServiceConfig opConfig) throws Exception {
	  
	  logger.info(String.format("SI XML Request for Holidaybookprocessor = %s", XMLTransformer.toString(requestElement)));
      //Element responseElement = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), HolidaysConfig.getHttpHeaders(), requestElement);
	  Element responseElement = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), requestElement);
      logger.info(String.format("SI XML Response for Holidaybookprocessor = %s",XMLTransformer.toString(responseElement)));
      
      if (responseElement == null)
          throw new Exception("Null response received from SI");
      
	return responseElement;
}

private static void validateRequestParameters(JSONObject reqJson) throws ValidationException {
	  	HolidaysRequestValidator.validateDynamicPkg(reqJson);
		HolidaysRequestValidator.validateTourCode(reqJson);
		HolidaysRequestValidator.validateSubTourCode(reqJson);
		HolidaysRequestValidator.validateBrandName(reqJson);
		HolidaysRequestValidator.validatebookID(reqJson);
		HolidaysRequestValidator.validatePaymentInfo(reqJson);
		HolidaysRequestValidator.validatePassengerCounts(reqJson);
		HolidaysRequestValidator.validateResGuestsInfo(reqJson);
		HolidaysRequestValidator.validateComponents(reqJson);
		HolidaysRequestValidator.validateGlobalInfo(reqJson);
}

private static void logTheError(Element[] errorElem) {
	  	for(Element error : errorElem) {
		String errorShortText = String.valueOf(XMLUtils.getValueAtXPath(error, "./@ShortText"));
		String errorType = String.valueOf(XMLUtils.getValueAtXPath(error, "./@Type"));
		String errorCode = String.valueOf(XMLUtils.getValueAtXPath(error, "./@Code"));
		String errorStatus = String.valueOf(XMLUtils.getValueAtXPath(error, "./@status"));
		logger.info(String.format("Recieved Error from SI. Error Details: ErrorCode:" + errorCode + ", Type:" + errorType + ", Status:" + errorStatus + ", ShortText:"+ errorShortText));
		}}

//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject requestHeader, JSONObject requestBody) throws Exception {
private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject requestHeader, JSONObject requestBody) throws Exception {
	  	
	  	Element requestElement = (Element) opConfig.getRequestXMLShell().cloneNode(true);
	    Document ownerDoc = requestElement.getOwnerDocument();	    
	    
	    //CREATE SI REQUEST HEADER
	    Element requestHeaderElement = XMLUtils.getFirstElementAtXPath(requestElement, "./pac:RequestHeader");
	    
	    Element userIDElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:UserID");
	    userIDElement.setTextContent(requestHeader.getString(JSON_PROP_USERID));
	    
	    Element sessionIDElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:SessionID");
	    sessionIDElement.setTextContent(requestHeader.getString(JSON_PROP_SESSIONID));
	    
	    Element transactionIDElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:TransactionID");
	    transactionIDElement.setTextContent(JSON_PROP_TRANSACTID);	    
	    
	    Element supplierCredentialsListElement = XMLUtils.getFirstElementAtXPath(requestHeaderElement, "./com:SupplierCredentialsList");
	    
	    //CREATE SI REQUEST BODY
	    Element requestBodyElement = XMLUtils.getFirstElementAtXPath(requestElement, "./pac1:RequestBody");
	    
	    Element wrapperElement = XMLUtils.getFirstElementAtXPath(requestBodyElement, "./pac:OTA_DynamicPkgBookRQWrapper");
	    requestBodyElement.removeChild(wrapperElement);
	    
	    int sequence = 0;
	    JSONArray dynamicPackageArray = requestBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
	    for (int i=0; i < dynamicPackageArray.length(); i++) 
	    {
	        JSONObject dynamicPackageObj = dynamicPackageArray.getJSONObject(i);
	        sequence++;
	    
	        String supplierID = dynamicPackageObj.getString(JSON_PROP_SUPPLIERID);
	        Element supWrapperElement = null;
	        Element otaBookRQ = null;
	        Element dynamicPackage = null;
	        
	        //Making supplierCredentialsList for Each SupplierID
	        supplierCredentialsListElement = HolidaysRepriceProcessorV2.getSupplierCredentialsList(ownerDoc, usrCtx, supplierID, sequence, supplierCredentialsListElement);
	          
	        //Making request body for particular supplierID
	        supWrapperElement = (Element) wrapperElement.cloneNode(true);
	        requestBodyElement.appendChild(supWrapperElement);
	            
	        //Setting supplier id in request body
	        Element supplierIDElement = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./pac:SupplierID");
	        supplierIDElement.setTextContent(supplierID);
	            
	        //Setting sequence in request body
	        Element sequenceElement = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./pac:Sequence");
	        sequenceElement.setTextContent(Integer.toString(sequence));

	        //getting parent node OTA_DynamicPkgAvailRQ from SearchCriteria
	        otaBookRQ = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./ns:OTA_DynamicPkgBookRQ");

	        //creating element dynamic package
	        dynamicPackage = XMLUtils.getFirstElementAtXPath(otaBookRQ, "./ns:DynamicPackage");
	        
	        String allowOverrideAirDates = dynamicPackageObj.getString("allowOverrideAirDates");
	        
	        if(allowOverrideAirDates != null && allowOverrideAirDates.length() != 0)
	        dynamicPackage.setAttribute("AllowOverrideAirDates", allowOverrideAirDates);
	            
	        //Creating Components element
	        JSONObject components = dynamicPackageObj.getJSONObject(JSON_PROP_COMPONENTS); 
	            
	        if (components == null || components.length() == 0)
	          throw new Exception(String.format("Object components must be set for supplier %s", supplierID));
	            
	        Element componentsElement = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:Components");
	            
	        //Creating Hotel Component
	        JSONArray hotelComponents = components.getJSONArray(JSON_PROP_HOTEL_COMPONENT);
	        if(hotelComponents != null && hotelComponents.length() != 0)
	        	componentsElement = HolidaysRepriceProcessorV2.getHotelComponentElement(ownerDoc, hotelComponents, componentsElement);
	          
	        //Creating Air Component
	        JSONArray airComponents = components.optJSONArray(JSON_PROP_AIR_COMPONENT);
	        if(airComponents != null && airComponents.length() != 0)
	        	componentsElement = HolidaysRepriceProcessorV2.getAirComponentElement(ownerDoc, dynamicPackageObj, airComponents, componentsElement, supplierID);
	            
	        //Creating PackageOptionComponent Element
	        Element packageOptionComponentElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOptionComponent");
	            
	        String subTourCode = dynamicPackageObj.getString(JSON_PROP_SUBTOURCODE);
	        if(subTourCode != null && subTourCode.length() > 0)
	        	packageOptionComponentElement.setAttribute("QuoteID", subTourCode);
	            
	        Element packageOptionsElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOptions");
	        Element packageOptionElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOption");
	        
	        String brandName = dynamicPackageObj.getString(JSON_PROP_BRANDNAME);
	        if(brandName != null && brandName.length() > 0)
	        	packageOptionElement.setAttribute("CompanyShortName", brandName);
	        
	        String tourCode = dynamicPackageObj.getString(JSON_PROP_TOURCODE);
	        if(tourCode != null && tourCode.length() > 0)
	        	packageOptionElement.setAttribute("ID", tourCode);

	            
	        Element tpaElement = ownerDoc.createElementNS(NS_OTA, "ns:TPA_Extensions");
	            
	        //Creating Cruise Component
	        JSONArray cruiseComponents = components.optJSONArray(JSON_PROP_CRUISE_COMPONENT);
	            
	        if(cruiseComponents != null && cruiseComponents.length() != 0)
	        	tpaElement = HolidaysRepriceProcessorV2.getCruiseComponentElement(ownerDoc, cruiseComponents, tpaElement);
	            
	        //Creating Transfers Component
	        JSONArray transfersComponents = components.optJSONArray(JSON_PROP_TRANSFER_COMPONENT);
	        if(transfersComponents != null && transfersComponents.length() != 0)
	           tpaElement = HolidaysRepriceProcessorV2.getTransferComponentElement(ownerDoc, transfersComponents, tpaElement);
	            
	        //Creating Insurance Component
	        JSONArray insuranceComponents = components.getJSONArray(JSON_PROP_INSURANCE_COMPONENT);
	        if(insuranceComponents != null && insuranceComponents.length() != 0)
	           tpaElement = HolidaysRepriceProcessorV2.getInsuranceComponentElement(ownerDoc, insuranceComponents, tpaElement);
	            
	        //Appending TPA element to package Option Element
	        packageOptionElement.appendChild(tpaElement);
	            
	        //Appending package Option Element to package Options Element
	        packageOptionsElement.appendChild(packageOptionElement);
	            
	        //Appending package Options Element to PackageOptionComponent Element
	        packageOptionComponentElement.appendChild(packageOptionsElement);
	            
	        //Appending PackageOptionComponent Element to Components Element
	        componentsElement.appendChild(packageOptionComponentElement);
	            
	        //create RestGuests xml elements
	        JSONArray resGuests =  dynamicPackageObj.getJSONArray(JSON_PROP_RESGUESTS); 	            
	        Element resGuestsElement = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:ResGuests");
	        if(resGuests != null && resGuests.length() != 0)
	           for(int j=0;j<resGuests.length();j++)
	           {
	        	   JSONObject resGuest = resGuests.getJSONObject(j);
	               Element resGuestElement = HolidaysRepriceProcessorV2.getResGuestElement(ownerDoc, resGuest);
	               resGuestsElement.appendChild(resGuestElement);
	           }
	            
	        //Create GlobalInfo xml element
	        JSONObject globalInfo = dynamicPackageObj.getJSONObject(JSON_PROP_GLOBALINFO);
	        if(globalInfo != null && globalInfo.length() != 0)
	        {
	          Element globalInfoElement = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:GlobalInfo");
	          globalInfoElement = HolidaysRepriceProcessorV2.getGlobalInfoElement(ownerDoc, globalInfo, globalInfoElement);
	         }
	    }
		return requestElement;
	    }

private static void sendPostBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject requestJson, JSONObject responseJSON) throws Exception {
	JSONObject reqBodyJson = requestJson.getJSONObject(JSON_PROP_REQBODY);
	JSONObject kafkaMsgJson=responseJSON;
	kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PROD, JSON_PROP_HOLIDAYS);
	
	//TODO: write a logic to send Failed as status in case Booking is unsuccessful 
	kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
	bookProducer.runProducer(1, kafkaMsgJson);
	logger.trace(String.format("Holidays Book Response Kafka Message: %s", kafkaMsgJson.toString()));
	
	  Map<String, String> reprcSuppFaresMap = null;
		try ( Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
			String redisKey = requestJson.getJSONObject(JSON_PROP_REQHEADER).optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT).concat("|").concat("reprice");
			reprcSuppFaresMap = redisConn.hgetAll(redisKey);
			if (reprcSuppFaresMap.isEmpty()) {
				throw new Exception(String.format("Reprice context not found for %s", redisKey));
			}
		
		redisConn.del(redisKey,redisKey+"|ptcXML");
		logger.trace("Reprice context deleted from redis.");
	}
}

private static void sendPreBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson ,  UserContext usrCtx) throws Exception {
		//JSONObject kafkaMsgJson = reqJson;
	    JSONObject kafkaMsg=new JSONObject(new JSONTokener(reqJson.toString()));
		JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject reqHeaderJson = kafkaMsg.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, JSON_PROP_HOLIDAYS);
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
		bookProducer.runProducer(1, kafkaMsg);
		logger.trace(String.format("Holidays Book Request Kafka Message: %s", kafkaMsg.toString()));
	}

private static void enrichRequestBodyJson(JSONObject requestHeader, JSONObject requestBody) throws Exception {     

	  Map<String, String> reprcSuppFaresMap = null;
		try ( Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
			String redisKey = requestHeader.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT).concat("|").concat("reprice");
			reprcSuppFaresMap = redisConn.hgetAll(redisKey);
			if (reprcSuppFaresMap.isEmpty()) {
				throw new Exception(String.format("Reprice context not found for %s", redisKey));
			}
		}
		
		JSONArray dynamicPkgJsonArr = requestBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
		for (int k = 0; k < dynamicPkgJsonArr.length(); k++) {
			JSONObject dynPkgJson = dynamicPkgJsonArr.getJSONObject(k);
			JSONObject reqComponents = dynPkgJson.getJSONObject(JSON_PROP_COMPONENTS);
			
			String dynPkgRedisKey = HolidaysRepriceProcessorV2.getRedisKeyForDynamicPackage(dynPkgJson);
			
			JSONObject redisJson = new JSONObject(reprcSuppFaresMap.get(dynPkgRedisKey));
			
			//JSONObject componentsForRedis = redisJson.optJSONObject("componentsForRedis");
			/*File file = new File("D:\\AVAMAR BACKUP\\Rahul Shewani\\Desktop\\Reprice restructure\\4-5-18\\RedisHolidaysStructure.json");
			JSONObject redisJson = new JSONObject(FileUtils.readFileToString(file));*/
			
			JSONObject componentsForRedis = redisJson.getJSONObject("componentForRedis");
			//JSONObject componentsInfo = redisJson.getJSONObject(JSON_PROP_COMPONENTS);
			if (componentsForRedis == null) {
				throw new Exception(String.format("Pricing details for %s were not found in booking context", dynPkgRedisKey));
			}
			//dynPkgJson.put("redisComponents", componentsForRedis);
			
			JSONArray transferComponent = componentsForRedis.optJSONArray(JSON_PROP_TRANSFER_COMPONENT);
			JSONArray insuranceComponent = componentsForRedis.optJSONArray(JSON_PROP_INSURANCE_COMPONENT);
			JSONArray activityComponent = componentsForRedis.optJSONArray(JSON_PROP_ACTIVITY_COMPONENT);
			JSONArray extrasComponent = componentsForRedis.optJSONArray(JSON_PROP_EXTRA_COMPONENT);
			/*if(extrasComponent != null && extrasComponent.length() != 0)
			reqComponents.put(JSON_PROP_EXTRA_COMPONENT, extrasComponent);*/
			
			JSONObject hotelComponent = componentsForRedis.optJSONObject(JSON_PROP_HOTEL_COMPONENT);
			JSONObject cruiseComponent = componentsForRedis.optJSONObject(JSON_PROP_CRUISE_COMPONENT);
			JSONObject preNight = componentsForRedis.optJSONObject(JSON_PROP_PRENIGHT);
			JSONObject postNight = componentsForRedis.optJSONObject(JSON_PROP_POSTNIGHT);
			
			if(transferComponent!=null && transferComponent.length()!=0)
				for(int t=0;t<transferComponent.length();t++) {
					JSONArray groundService = transferComponent.getJSONObject(t).getJSONArray(JSON_PROP_GROUNDSERVICE);
					for(int u =0;u<groundService.length();u++) {
						//enrichComponent(componentsInfo.getJSONArray(JSON_PROP_TRANSFER_COMPONENT).getJSONObject(t).getJSONArray(JSON_PROP_GROUNDSERVICE).getJSONObject(u),groundService.getJSONObject(u),reqComponents.getJSONArray(JSON_PROP_TRANSFER_COMPONENT).getJSONObject(t).getJSONArray(JSON_PROP_GROUNDSERVICE).getJSONObject(u));}}
						enrichComponent(groundService.getJSONObject(u),reqComponents.getJSONArray(JSON_PROP_TRANSFER_COMPONENT).getJSONObject(t).getJSONArray(JSON_PROP_GROUNDSERVICE).getJSONObject(u));}}
						
			if(insuranceComponent!=null && insuranceComponent.length()!=0)
				for(int t=0;t<insuranceComponent.length();t++) {
					//enrichComponent(componentsInfo.getJSONArray(JSON_PROP_INSURANCE_COMPONENT).getJSONObject(t), insuranceComponent.getJSONObject(t),reqComponents.getJSONArray(JSON_PROP_INSURANCE_COMPONENT).getJSONObject(t));}
					enrichComponent(insuranceComponent.getJSONObject(t),reqComponents.getJSONArray(JSON_PROP_INSURANCE_COMPONENT).getJSONObject(t));}
					
			if(activityComponent!=null && activityComponent.length()!=0)
				for(int t=0;t<activityComponent.length();t++) {
					//enrichComponent(componentsInfo.getJSONArray(JSON_PROP_ACTIVITY_COMPONENT).getJSONObject(t),activityComponent.getJSONObject(t),reqComponents.getJSONArray(JSON_PROP_ACTIVITY_COMPONENT).getJSONObject(t));}
					enrichComponent(activityComponent.getJSONObject(t),reqComponents.getJSONArray(JSON_PROP_ACTIVITY_COMPONENT).getJSONObject(t));}
					
			if(extrasComponent!=null && extrasComponent.length()!=0)
				for(int t=0;t<extrasComponent.length();t++) {
					enrichExtraComponent(extrasComponent.getJSONObject(t));
					reqComponents.put(JSON_PROP_EXTRA_COMPONENT, extrasComponent);
					//enrichComponent(componentsInfo.getJSONArray(JSON_PROP_EXTRA_COMPONENT).getJSONObject(t),extrasComponent.getJSONObject(t),reqComponents.getJSONArray(JSON_PROP_EXTRA_COMPONENT).getJSONObject(t));
					}
			
			if(hotelComponent!=null && hotelComponent.length()!=0) {
				enrichComponentLevel(hotelComponent,reqComponents.getJSONObject(JSON_PROP_HOTEL_COMPONENT));
				//JSONArray roomStayInfo = componentsInfo.getJSONObject(JSON_PROP_HOTEL_COMPONENT).getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
				JSONArray roomStay = hotelComponent.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
				JSONArray reqRoomStay = reqComponents.getJSONObject(JSON_PROP_HOTEL_COMPONENT).getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
				for(int t=0;t<reqRoomStay.length();t++) {
					//enrichComponent(roomStayInfo.getJSONObject(t),roomStay.getJSONObject(t),reqRoomStay.getJSONObject(t));
					enrichComponent(roomStay.getJSONObject(t),reqRoomStay.getJSONObject(t));
				}}
			
			if(cruiseComponent!=null && cruiseComponent.length()!=0) {
				enrichComponentLevel(cruiseComponent,reqComponents.getJSONObject(JSON_PROP_CRUISE_COMPONENT));
				JSONArray categoryOptions = reqComponents.getJSONObject(JSON_PROP_CRUISE_COMPONENT).getJSONArray(JSON_PROP_CATEGORYOPTIONS);
				for(int i = 0; i< categoryOptions.length(); i++) {
					//JSONArray roomStayInfo = componentsInfo.getJSONObject(JSON_PROP_CRUISE_COMPONENT).getJSONArray(JSON_PROP_CATEGORYOPTIONS).getJSONObject(i).getJSONArray(JSON_PROP_CATEGORYOPTION);
					JSONArray roomStay = cruiseComponent.getJSONArray(JSON_PROP_CATEGORYOPTIONS).getJSONObject(i).getJSONArray(JSON_PROP_CATEGORYOPTION);
					JSONArray reqRoomStay = categoryOptions.getJSONObject(i).getJSONArray(JSON_PROP_CATEGORYOPTION);
					for(int t=0;t<reqRoomStay.length();t++) {
						//enrichComponent(roomStayInfo.getJSONObject(t), roomStay.getJSONObject(t), reqRoomStay.getJSONObject(t));
						enrichComponent(roomStay.getJSONObject(t), reqRoomStay.getJSONObject(t));
				}}}
			
			if(preNight!=null && preNight.length()!=0){
				enrichComponentLevel(preNight,reqComponents.getJSONObject(JSON_PROP_PRENIGHT));
					//JSONArray roomStayInfo = componentsInfo.getJSONObject(JSON_PROP_PRENIGHT).getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);	
					JSONArray roomStay = preNight.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
					JSONArray reqRoomStay = reqComponents.getJSONObject(JSON_PROP_PRENIGHT).getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
					for(int t=0;t<reqRoomStay.length();t++) {
						//enrichComponent(roomStayInfo.getJSONObject(t),roomStay.getJSONObject(t),reqRoomStay.getJSONObject(t));
						enrichComponent(roomStay.getJSONObject(t),reqRoomStay.getJSONObject(t));
					}
				}
			
			if(postNight!=null && postNight.length()!=0){
				enrichComponentLevel(postNight,reqComponents.getJSONObject(JSON_PROP_POSTNIGHT));
				//JSONArray roomStayInfo = componentsInfo.getJSONObject(JSON_PROP_POSTNIGHT).getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
				JSONArray roomStay = postNight.getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
				JSONArray reqRoomStay = reqComponents.getJSONObject(JSON_PROP_POSTNIGHT).getJSONObject(JSON_PROP_ROOMSTAYS).getJSONArray(JSON_PROP_ROOMSTAY);
				for(int t=0;t<reqRoomStay.length();t++) {
					//enrichComponent(roomStayInfo.getJSONObject(t),roomStay.getJSONObject(t),reqRoomStay.getJSONObject(t));
					enrichComponent(roomStay.getJSONObject(t),reqRoomStay.getJSONObject(t));
					}
				}
			redisJson.remove("components");
			redisJson.remove("componentForRedis");
			dynPkgJson.put(JSON_PROP_SUPPINFO, redisJson);
			dynPkgJson.getJSONObject(JSON_PROP_SUPPINFO).put(JSON_PROP_TIMESPAN,dynPkgJson.getJSONObject(JSON_PROP_GLOBALINFO).getJSONObject(JSON_PROP_TIMESPAN));
			String suppCurCode = redisJson.getJSONObject(JSON_PROP_SUPPLIERPRICINGINFO).getJSONObject(JSON_PROP_TOTALFARE).getString(JSON_PROP_CCYCODE);
			JSONObject clientContxt = requestHeader.getJSONObject(JSON_PROP_CLIENTCONTEXT);
			dynPkgJson.getJSONObject(JSON_PROP_SUPPINFO).put(JSON_PROP_ROE, RedisRoEData.getRateOfExchange(suppCurCode, clientContxt.getString(JSON_PROP_CLIENTCCY),clientContxt.getString(JSON_PROP_CLIENTMARKET)));
		}
}

  private static void enrichExtraComponent(JSONObject extraComp) {
	  
	  JSONArray clientCommercialInfo = extraComp.getJSONArray(JSON_PROP_CLIENTCOMMERCIALINFO);
	  JSONArray paxTypeFaresJsonArr = extraComp.getJSONObject(JSON_PROP_TOTALPRICINGINFO).getJSONArray("paxTypeFares");
	  
	  Map<String,JSONArray> clientCommPaxMap=new HashMap<String,JSONArray>();
		for(int t=0; t < clientCommercialInfo.length(); t++) {
			JSONObject paxTypeCC=new JSONObject();
			paxTypeCC=clientCommercialInfo.getJSONObject(t);
			clientCommPaxMap.put(paxTypeCC.get(JSON_PROP_TYPE).toString(),paxTypeCC.getJSONArray(JSON_PROP_CLIENTENTITYCOMMS));
		}
		
		for(int z=0;z<paxTypeFaresJsonArr.length();z++) {
			JSONObject paxTypeFareJson=paxTypeFaresJsonArr.getJSONObject(z);
			clientCommPaxMap.get(paxTypeFareJson.get(JSON_PROP_TYPE).toString());
			paxTypeFareJson.put(JSON_PROP_CLIENTENTITYCOMMS, clientCommPaxMap.get(paxTypeFareJson.get(JSON_PROP_TYPE).toString()));
		}
	  
  }

private static void enrichComponentLevel(JSONObject redisComponent, JSONObject reqComponent) {
	  reqComponent.put(JSON_PROP_SUPPLIERPRICINGINFO, redisComponent.getJSONObject(JSON_PROP_SUPPLIERPRICINGINFO));
	  reqComponent.put(JSON_PROP_COMPTOTALFARE, redisComponent.getJSONObject(JSON_PROP_COMPTOTALFARE));
	  reqComponent.put(JSON_PROP_CLIENTCOMMERCIALSTOTALCOMM, redisComponent.getJSONArray(JSON_PROP_CLIENTCOMMERCIALSTOTALCOMM));
  }

//private static void enrichComponent(JSONObject InfoComponent, JSONObject redisComponent, JSONObject reqComponent) {
private static void enrichComponent(JSONObject redisComponent, JSONObject reqComponent) {

		JSONArray clientCommercialPackageInfo = redisComponent.getJSONArray(JSON_PROP_CLIENTCOMMERCIALINFO);
		redisComponent.remove(JSON_PROP_CLIENTCOMMERCIALINFO);
		
		JSONArray clientEntityTotalCommercials = redisComponent.getJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
		redisComponent.remove(JSON_PROP_CLIENTENTITYTOTALCOMMS);
	
		JSONObject totalPricingInfo=redisComponent.getJSONObject(JSON_PROP_TOTALPRICINGINFO);
		reqComponent.put(JSON_PROP_TOTALPRICINGINFO, totalPricingInfo);
		redisComponent.remove(JSON_PROP_TOTALPRICINGINFO);
		
		JSONObject supplierPricingInfo=redisComponent.getJSONObject(JSON_PROP_SUPPLIERPRICINGINFO);
		reqComponent.put(JSON_PROP_SUPPLIERPRICINGINFO, supplierPricingInfo);
		redisComponent.remove(JSON_PROP_SUPPLIERPRICINGINFO);
		
		//reqComponent.put(JSON_PROP_ADDITIONALINFO, InfoComponent);
		
		JSONObject reqTotalPricingInfo = reqComponent.getJSONObject(JSON_PROP_TOTALPRICINGINFO);
		reqTotalPricingInfo.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientEntityTotalCommercials);
		JSONArray paxTypeFaresJsonArr = reqTotalPricingInfo.getJSONArray("paxTypeFares");
		
		Map<String,JSONArray> clientCommPaxMap=new HashMap<String,JSONArray>();
		for(int t=0; t < clientCommercialPackageInfo.length(); t++) {
			JSONObject clientCommercialItin=new JSONObject();
			clientCommercialItin=clientCommercialPackageInfo.getJSONObject(t);
			clientCommPaxMap.put(clientCommercialItin.get(JSON_PROP_TYPE).toString(),clientCommercialItin.getJSONArray(JSON_PROP_CLIENTENTITYCOMMS));
		}
		
		for(int z=0;z<paxTypeFaresJsonArr.length();z++) {
			JSONObject paxTypeFareJson=paxTypeFaresJsonArr.getJSONObject(z);
			clientCommPaxMap.get(paxTypeFareJson.get(JSON_PROP_TYPE).toString());
			paxTypeFareJson.put(JSON_PROP_CLIENTENTITYCOMMS, clientCommPaxMap.get(paxTypeFareJson.get(JSON_PROP_TYPE).toString()));
		}
	
}

public static JSONObject requestJSONTransformation(JSONObject requestJson) throws Exception
  {
    JSONObject requestBody = requestJson.getJSONObject(JSON_PROP_REQBODY);
    JSONArray dynamicPackageArray = requestBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
    for (int i = 0; i < dynamicPackageArray.length(); i++){

    	JSONObject dynamicPackageObj = dynamicPackageArray.getJSONObject(i);
        JSONObject components = dynamicPackageObj.getJSONObject(JSON_PROP_COMPONENTS);
        String supplierID = dynamicPackageObj.getString(JSON_PROP_SUPPLIERID);

        if (components == null || components.length() == 0)
            throw new Exception(String.format("Object components must be set for supplier %s", supplierID));

        JSONArray hotelComponentsJsonArray = new JSONArray();
        JSONArray cruiseComponentsJsonArray = new JSONArray();
        
        //ForAccommodation Component
        if (components.has(JSON_PROP_HOTEL_COMPONENT)) {
          JSONObject hotelComponentJson = components.getJSONObject(JSON_PROP_HOTEL_COMPONENT);
          
          if (hotelComponentJson != null && hotelComponentJson.length() != 0){
             hotelComponentsJsonArray.put(hotelComponentJson);
              
              if (components.has(JSON_PROP_PRENIGHT)){
                 JSONObject perNightObject = components.getJSONObject(JSON_PROP_PRENIGHT);
                 
                 if (perNightObject != null && perNightObject.length() != 0){
                   perNightObject.put(JSON_PROP_DYNAMICPKGACTION, DYNAMICPKGACTION_HOTEL_PRE);
                   hotelComponentsJsonArray.put(perNightObject);
                   components.remove(JSON_PROP_PRENIGHT);
                  } 
              }
              
              if (components.has(JSON_PROP_POSTNIGHT)){
                 JSONObject postNightNightObject = components.getJSONObject(JSON_PROP_POSTNIGHT);
                 
                 if (postNightNightObject != null && postNightNightObject.length() != 0) {
                   postNightNightObject.put(JSON_PROP_DYNAMICPKGACTION, DYNAMICPKGACTION_HOTEL_POST);
                   hotelComponentsJsonArray.put(postNightNightObject);
                   components.remove(JSON_PROP_POSTNIGHT);
                  } 
              }
              components.remove(JSON_PROP_HOTEL_COMPONENT);
          }
         }
        components.put(JSON_PROP_HOTEL_COMPONENT, hotelComponentsJsonArray);
        
        //For Cruise Component
        if (components.has(JSON_PROP_CRUISE_COMPONENT)) {
          JSONObject cruiseComponentJson = components.getJSONObject(JSON_PROP_CRUISE_COMPONENT);
          
          if (cruiseComponentJson != null && cruiseComponentJson.length() != 0) {
              cruiseComponentsJsonArray.put(cruiseComponentJson);
              
              if (components.has(JSON_PROP_PRENIGHT)) {
                 JSONObject perNightObject = components.getJSONObject(JSON_PROP_PRENIGHT);
                 
                 if (perNightObject != null && perNightObject.length() != 0){
                   perNightObject.put(JSON_PROP_DYNAMICPKGACTION, DYNAMICPKGACTION_CRUISE_PRE);
                   cruiseComponentsJsonArray.put(perNightObject);
                   components.remove(JSON_PROP_PRENIGHT);
                  } 
              }
              
              if (components.has(JSON_PROP_POSTNIGHT)){
                 JSONObject postNightNightObject = components.getJSONObject(JSON_PROP_POSTNIGHT);
                 
                 if (postNightNightObject != null && postNightNightObject.length() != 0){
                   postNightNightObject.put(JSON_PROP_DYNAMICPKGACTION, DYNAMICPKGACTION_CRUISE_POST);
                   cruiseComponentsJsonArray.put(postNightNightObject);
                   components.remove(JSON_PROP_POSTNIGHT);
                  }
              }
              components.remove(JSON_PROP_CRUISE_COMPONENT);
          }
         }
        components.put(JSON_PROP_CRUISE_COMPONENT, cruiseComponentsJsonArray);
    }

    logger.trace("Transformed req json: " + requestJson);
    return requestJson;
  }
  
  
  protected static JSONObject getDynamicPkgComponent(Element dynamicPackageElem) {
		JSONObject dynamicPkgComponentJSON=new JSONObject();
		
		JSONArray uniqIDJSONArray = getUniqueID(dynamicPackageElem);
		dynamicPkgComponentJSON.put("uniqueID", uniqIDJSONArray);
		
		//resGuest
		JSONArray resGuestArray = new JSONArray();
		Element resGuestElem [] = XMLUtils.getElementsAtXPath(dynamicPackageElem, "./ns:ResGuests/ns:ResGuest");
		for(Element resGuest : resGuestElem) {
		
		JSONObject resGuestsJSON=getResGuests(resGuest);
		resGuestArray.put(resGuestsJSON);
		}
		JSONObject guestsJSON =new JSONObject();
		guestsJSON.put("resGuest", resGuestArray);
		dynamicPkgComponentJSON.put(JSON_PROP_RESGUESTS, guestsJSON);
		//resGuest ends
		
		JSONObject globalInfoJSON=getglobalinfo(dynamicPackageElem);
		dynamicPkgComponentJSON.put(JSON_PROP_GLOBALINFO, globalInfoJSON);
		
				
		return dynamicPkgComponentJSON;
	}

	private static JSONObject getglobalinfo(Element dynamicPkgElement) {
		Element globalInfoElement = XMLUtils.getFirstElementAtXPath(dynamicPkgElement, "./ns:GlobalInfo");
		JSONObject globalInfoJSON=new JSONObject();
		
		//For TimeSpan
		Element timeSpanElement = XMLUtils.getFirstElementAtXPath(globalInfoElement, "./ns:TimeSpan");
		JSONObject timeSpanJSON = new JSONObject();
		String End = String.valueOf(XMLUtils.getValueAtXPath(timeSpanElement, "./@End"));
		String TravelStartDate = String.valueOf(XMLUtils.getValueAtXPath(timeSpanElement, "./@TravelStartDate"));
		timeSpanJSON.put(JSON_PROP_END, End);
		timeSpanJSON.put(JSON_PROP_TRAVELSTARTDATE, TravelStartDate);
		globalInfoJSON.put(JSON_PROP_TIMESPAN, timeSpanJSON);
		//Timespan End
		
		//special request start
		JSONArray specialRequestJSONArray = new JSONArray();
		JSONObject specialRequestJson=new JSONObject();
			Element[] specialRequestElementArray = XMLUtils.getElementsAtXPath(globalInfoElement, "./ns:SpecialRequests/ns:SpecialRequest");
			
			//For Text array start <ns:text>
			for(Element specialRequestElem : specialRequestElementArray ) {
				
				String name = String.valueOf(XMLUtils.getValueAtXPath(specialRequestElem, "./@Name"));
				JSONObject specialRequestJSON1=new JSONObject();
				specialRequestJSON1.put("name", name);
				JSONArray textJSONArray = new JSONArray();
				
				Element[] specialRequestTextElementArray = XMLUtils.getElementsAtXPath(specialRequestElem, "./ns:Text");
				for(Element specialRequestTextElement : specialRequestTextElementArray ) {
					String text = String.valueOf(XMLUtils.getElementValue(specialRequestTextElement));
					textJSONArray.put(text);
				
				}
				specialRequestJSON1.put("text",textJSONArray);
				specialRequestJSONArray.put(specialRequestJSON1);
			}
			specialRequestJson.put("specialRequest", specialRequestJSONArray);
			globalInfoJSON.put("specialRequests", specialRequestJson);
			//end
		//special request end
		
		//Guarantee
		JSONArray guranteePaymentsJSONArray = new JSONArray();
		Element guranteePaymentsElement[] = XMLUtils.getElementsAtXPath(globalInfoElement, "./ns:DepositPayments/ns:GuaranteePayment");
		
		for(Element guranteePaymentElement : guranteePaymentsElement ) {
		JSONObject guranteePaymentsJSONObject=getDepositPayments(guranteePaymentElement);
		guranteePaymentsJSONArray.put(guranteePaymentsJSONObject);
		}
		JSONObject depositPaymentsJSON = new JSONObject();
		depositPaymentsJSON.put("guaranteePayment", guranteePaymentsJSONArray);
		globalInfoJSON.put("depositPayments", depositPaymentsJSON);
		//Guarantee ends
		
		//Total
		JSONArray totalJsonArray = new JSONArray();
		Element totalElementPath[] = XMLUtils.getElementsAtXPath(globalInfoElement, "./ns:Total");
		
		for(Element totalElement : totalElementPath ) {
		
		JSONObject totalJSON=getTotal(totalElement);
		totalJsonArray.put(totalJSON);
		}
		globalInfoJSON.put(JSON_PROP_TOTAL, totalJsonArray);
		//Total End
		
		
		//BookingRules
		Element bookingRuleElementPath[] = XMLUtils.getElementsAtXPath(globalInfoElement, "./ns:BookingRules/ns:BookingRule");
		JSONArray bookingRuleArray = new JSONArray();
		JSONObject bookingRule = new JSONObject();
		
		for(Element bookingRuleElem : bookingRuleElementPath) {
			
		JSONObject descriptionJson = getBookingRules(bookingRuleElem);
		bookingRuleArray.put(descriptionJson);
	
		}
		bookingRule.put("bookingRule",bookingRuleArray);
		globalInfoJSON.put("bookingRules",bookingRule);
		//BookingRules End
		
		//For TotalCommissions
		Element CommissionPayableAmountElement = XMLUtils.getFirstElementAtXPath(globalInfoElement, "./ns:TotalCommissions/ns:CommissionPayableAmount");
		String Amount = String.valueOf(XMLUtils.getValueAtXPath(CommissionPayableAmountElement, "./@Amount"));
		
		JSONObject CommissionPayableAmountJSON = new JSONObject();
		CommissionPayableAmountJSON.put(JSON_PROP_AMOUNT, Amount);
		
		JSONObject totalCommisionsjson = new JSONObject();
		totalCommisionsjson.put("commissionPayableAmount", CommissionPayableAmountJSON);
		globalInfoJSON.put("totalCommissions", totalCommisionsjson);
		//TotalCommissions end
		
		JSONObject dynamicPkgIDJSON = getDynamicPkgIDs(globalInfoElement);
		globalInfoJSON.put("dynamicPkgID", dynamicPkgIDJSON);
		
		return globalInfoJSON;
	}

	private static JSONObject getDynamicPkgIDs(Element globalInfoElement) {
		Element dynamicPkgIDElement = XMLUtils.getFirstElementAtXPath(globalInfoElement, "./ns:DynamicPkgIDs/ns:DynamicPkgID");
		String id_context = String.valueOf(XMLUtils.getValueAtXPath(dynamicPkgIDElement,"./@ID_Context"));
		String id = String.valueOf(XMLUtils.getValueAtXPath(dynamicPkgIDElement,"./@ID"));
		
		JSONObject dynamicPkgIDJSON = new JSONObject();
		dynamicPkgIDJSON.put(JSON_PROP_ID_CONTEXT, id_context);
		dynamicPkgIDJSON.put(JSON_PROP_ID, id);
		
		String companyShortName = String.valueOf(XMLUtils.getValueAtXPath(dynamicPkgIDElement,"./ns:CompanyName/@CompanyShortName"));
		JSONObject companyNameJson = new JSONObject();
		companyNameJson.put(JSON_PROP_COMPANYSHORTNAME, companyShortName);
		dynamicPkgIDJSON.put(JSON_PROP_COMPANYNAME, companyNameJson);
		
		String tourName = String.valueOf(XMLUtils.getValueAtXPath(dynamicPkgIDElement,"./ns:TPA_Extensions/pac:Pkgs_TPA/pac:TourDetails/pac:TourName"));
		JSONObject tourDetailsJson = new JSONObject();
		tourDetailsJson.put(JSON_PROP_TOURNAME, tourName);
		
		dynamicPkgIDJSON.put("tourDetails",tourDetailsJson);
		
		return dynamicPkgIDJSON;
	}

	private static JSONObject getBookingRules(Element bookingRule) {
		
		Element brDescription[] = XMLUtils.getElementsAtXPath(bookingRule, "./ns:Description");
		JSONArray bookingRDescriptionArray = new JSONArray();
		for(Element description :brDescription) {
		String Name = String.valueOf(XMLUtils.getValueAtXPath(description,"./@Name"));
		String Text = String.valueOf(XMLUtils.getValueAtXPath(description,"./ns:Text"));

		JSONObject descriptionJson = new JSONObject();
		descriptionJson.put(JSON_PROP_NAME, Name);
		descriptionJson.put(JSON_PROP_TEXT, Text);
		
		bookingRDescriptionArray.put(descriptionJson);
		}
		
		JSONObject descriptionJson = new JSONObject();
		descriptionJson.put(JSON_PROP_DESCRIPTION, bookingRDescriptionArray);
	
		return descriptionJson;
	}

	private static JSONObject getTotal(Element totalElement) {
		String amountAfterTax = String.valueOf(XMLUtils.getValueAtXPath(totalElement, "./@AmountAfterTax"));
		String amountBeforeTax = String.valueOf(XMLUtils.getValueAtXPath(totalElement, "./@AmountBeforeTax"));
		JSONObject totalJSON = new JSONObject();
		totalJSON.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTax);
		totalJSON.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTax);
		
		
		Element tpa_ExtensionsElement = XMLUtils.getFirstElementAtXPath(totalElement, "./ns:TPA_Extensions");
		
		Element PaymentElement = XMLUtils.getFirstElementAtXPath(tpa_ExtensionsElement, "./pac:Payment");
		String BalanceDueGross = String.valueOf(XMLUtils.getValueAtXPath(PaymentElement, "./pac:BalanceDueGross"));
		String Payments = String.valueOf(XMLUtils.getValueAtXPath(PaymentElement, "./pac:Payments"));
		JSONObject paymentJSON = new JSONObject();
		paymentJSON.put("balanceDueGross", BalanceDueGross);
		paymentJSON.put("payments", Payments);
		totalJSON.put("payment", paymentJSON);
		
		
		Element excursionsElement = XMLUtils.getFirstElementAtXPath(tpa_ExtensionsElement, "./pac:Excursions/pac:ExcursionsTotal");
		String AmountAfterTax = String.valueOf(XMLUtils.getValueAtXPath(excursionsElement, "./@AmountAfterTax"));
		JSONObject ExcursionsTotalJSON = new JSONObject();
		JSONObject ExcursionsJSON = new JSONObject();
		ExcursionsTotalJSON.put(JSON_PROP_AMOUNTAFTERTAX, AmountAfterTax);
		
		Element taxElement = XMLUtils.getFirstElementAtXPath(excursionsElement, "./ns:Taxes/ns:Tax");
		String Amount = String.valueOf(XMLUtils.getValueAtXPath(taxElement, "./@Amount"));
		
		String Name = String.valueOf(XMLUtils.getValueAtXPath(taxElement, "./ns:TaxDescription/@Name"));
		JSONObject taxDescriptionJSON = new JSONObject();
		taxDescriptionJSON.put(JSON_PROP_NAME, Name);
		
		JSONObject taxJSON = new JSONObject();
		taxJSON.put(JSON_PROP_AMOUNT, Amount);
		taxJSON.put(JSON_PROP_TAXDESCRIPTION, taxDescriptionJSON);
		
		JSONObject taxesJSON = new JSONObject();
		taxesJSON.put(JSON_PROP_TAX, taxJSON);
		
		ExcursionsTotalJSON.put(JSON_PROP_TAXES, taxesJSON);
		
		ExcursionsJSON.put("excursionsTotal", ExcursionsTotalJSON);
				
		String ExcursionsBalanceDue = String.valueOf(XMLUtils.getValueAtXPath(tpa_ExtensionsElement, "./pac:Excursions/pac:ExcursionsBalanceDue"));
		ExcursionsJSON.put("excursionsBalanceDue", ExcursionsBalanceDue);
		
		String cAmount = String.valueOf(XMLUtils.getValueAtXPath(tpa_ExtensionsElement, "./pac:Excursions/pac:ExcursionsCommission/ns:CommissionPayableAmount/@Amount"));
		JSONObject commissionPayableAmountJSON = new JSONObject();
		commissionPayableAmountJSON.put(JSON_PROP_AMOUNT, cAmount);
		
		JSONObject excursionsCommissionJSON = new JSONObject();
		excursionsCommissionJSON.put("commissionPayableAmount", commissionPayableAmountJSON);
		
		ExcursionsJSON.put("excursionsCommission", excursionsCommissionJSON);
		totalJSON.put("excursions", ExcursionsJSON);

		return totalJSON;
	}

	private static JSONObject getDepositPayments(Element guranteePayment) {
		
		
		String Amount = String.valueOf(XMLUtils.getValueAtXPath(guranteePayment, "./ns:AmountPercent/@Amount"));
		
		Element deadlineElement[] = XMLUtils.getElementsAtXPath(guranteePayment, "./ns:Deadline");
		JSONArray deadLineArray = new JSONArray();
		for(Element deadline : deadlineElement) {
		JSONObject absoluteDeadlineJSON = new JSONObject();
		String AbsoluteDeadline = String.valueOf(XMLUtils.getValueAtXPath(deadline, "./@AbsoluteDeadline"));
		
		absoluteDeadlineJSON.put("absoluteDeadline", AbsoluteDeadline);
		deadLineArray.put(absoluteDeadlineJSON);
		
		}
		
		JSONObject guaranteePaymentJSON = new JSONObject();
		JSONObject amountPercentJSON = new JSONObject();
		
		
		guaranteePaymentJSON.put("deadline", deadLineArray);
		amountPercentJSON.put(JSON_PROP_AMOUNT, Amount);
		guaranteePaymentJSON.put("amountPercent", amountPercentJSON);
		
		return guaranteePaymentJSON;
	}

	static JSONObject getResGuests(Element resGuest) {
		JSONObject resGuestJSON=new JSONObject();
		
		String primaryIndicator = String.valueOf(XMLUtils.getValueAtXPath(resGuest, "./@PrimaryIndicator"));
		String age = String.valueOf(XMLUtils.getValueAtXPath(resGuest, "./@Age"));
		String resGuestRPH = String.valueOf(XMLUtils.getValueAtXPath(resGuest, "./@ResGuestRPH"));
		
		resGuestJSON.put("primaryIndicator", primaryIndicator);
		resGuestJSON.put("age", age);
		resGuestJSON.put("resGuestRPH", resGuestRPH);
		
		String profileType = String.valueOf(XMLUtils.getValueAtXPath(resGuest, "./ns:Profiles/ns:ProfileInfo/ns:Profile/@ProfileType"));
		resGuestJSON.put("profileType", profileType);
		
		Element customerElement = XMLUtils.getFirstElementAtXPath(resGuest, "./ns:Profiles/ns:ProfileInfo/ns:Profile/ns:Customer");
		String birthDate = String.valueOf(XMLUtils.getValueAtXPath(customerElement, "./@BirthDate"));
		String gender = String.valueOf(XMLUtils.getValueAtXPath(customerElement, "./@Gender"));
		JSONObject customerJSON =new JSONObject();
		customerJSON.put("birthDate", birthDate);
		customerJSON.put("gender", gender);
		
		//<n1:Telephone PhoneNumber/>
		Element phoneNumberElement[] = XMLUtils.getElementsAtXPath(customerElement, "./ns:Telephone");
		JSONArray telephoneJSONArray = new JSONArray();
		
		for(Element phoneNumber : phoneNumberElement) {
		
		JSONObject telephoneJSON =new JSONObject();
		String phoneNumberString = String.valueOf(XMLUtils.getValueAtXPath(phoneNumber, "./@PhoneNumber"));
		String phoneUseType = String.valueOf(XMLUtils.getValueAtXPath(phoneNumber, "./@PhoneUseType"));
		String countryAccessCode = String.valueOf(XMLUtils.getValueAtXPath(phoneNumber, "./@CountryAccessCode"));
		String areaCityCode = String.valueOf(XMLUtils.getValueAtXPath(phoneNumber, "./@AreaCityCode"));
		
		telephoneJSON.put("phoneNumber", phoneNumberString);
		telephoneJSON.put("phoneUseType", phoneUseType);
		telephoneJSON.put("countryAccessCode", countryAccessCode);
		telephoneJSON.put("areaCityCode", areaCityCode);
		
		telephoneJSONArray.put(telephoneJSON);
		}
		customerJSON.put("telephone", telephoneJSONArray);
		//<n1:Telephone PhoneNumber/>
		
		
		//<ns:Email>
		Element emailElement[] = XMLUtils.getElementsAtXPath(customerElement, "./ns:Email");
		JSONArray emailArray = new JSONArray();
				
		for(Element email : emailElement) {	
		String emailString = String.valueOf(XMLUtils.getElementValue(email));
		emailArray.put(emailString);
		}
		customerJSON.put("email", emailArray);
		//</ns:Email>
				
		
		//<ns:Document DocID="" DocType="" />
		Element documentElement[] = XMLUtils.getElementsAtXPath(customerElement, "./ns:Document");
		JSONArray documentArray = new JSONArray();
		
		for(Element document : documentElement) {
		
		JSONObject documentJSON =new JSONObject();
		String docIDString = String.valueOf(XMLUtils.getValueAtXPath(document, "./@DocID"));
		String docTypeString = String.valueOf(XMLUtils.getValueAtXPath(document, "./@DocType"));
		String docNumber = String.valueOf(XMLUtils.getValueAtXPath(document, "./@DocNumber"));
		String nationality = String.valueOf(XMLUtils.getValueAtXPath(document, "./@Nationality"));
		String effectiveDate = String.valueOf(XMLUtils.getValueAtXPath(document, "./@EffectiveDate"));
		String expiryDate = String.valueOf(XMLUtils.getValueAtXPath(document, "./@ExpiryDate"));
		String issueAuthority = String.valueOf(XMLUtils.getValueAtXPath(document, "./@IssueAuthority"));
		String issueLocation = String.valueOf(XMLUtils.getValueAtXPath(document, "./@IssueLocation"));
		String issueCountry = String.valueOf(XMLUtils.getValueAtXPath(document, "./@IssueCountry"));
		
		documentJSON.put("docID", docIDString);
		documentJSON.put("docType", docTypeString);
		documentJSON.put("docNumber", docNumber);
		documentJSON.put("nationality", nationality);
		documentJSON.put("effectiveDate", effectiveDate);
		documentJSON.put("expiryDate", expiryDate);
		documentJSON.put("issueAuthority", issueAuthority);
		documentJSON.put("issueLocation", issueLocation);
		documentJSON.put("issueCountry", issueCountry);
		
		documentArray.put(documentJSON);
		}
		customerJSON.put("document", documentArray);
		//<ns:Document DocID="" DocType="" />
		
		Element personNameElement = XMLUtils.getFirstElementAtXPath(customerElement, "./ns:PersonName");
		
		String givenName = String.valueOf(XMLUtils.getValueAtXPath(personNameElement, "./ns:GivenName"));
		String surname = String.valueOf(XMLUtils.getValueAtXPath(personNameElement, "./ns:Surname"));
		String nameTitle = String.valueOf(XMLUtils.getValueAtXPath(personNameElement, "./ns:NameTitle"));
		String middleName = String.valueOf(XMLUtils.getValueAtXPath(personNameElement, "./ns:MiddleName"));
		
		JSONObject personNameJSON =new JSONObject();
		personNameJSON.put("givenName", givenName);
		personNameJSON.put("surname", surname);
		personNameJSON.put("nameTitle", nameTitle);
		personNameJSON.put("middleName", middleName);
		
		customerJSON.put("personName", personNameJSON);
		
		//Address Start
				JSONObject addressJSON =new JSONObject();
				
				Element addressElement = XMLUtils.getFirstElementAtXPath(customerElement, "./ns:Address");
				
				Element addressLineElement[] = XMLUtils.getElementsAtXPath(addressElement, "./ns:AddressLine");
				JSONArray addressLineArray = new JSONArray();
				
				for(Element addressLine : addressLineElement) {
					String addressLineString = String.valueOf(XMLUtils.getElementValue(addressLine));
					addressLineArray.put(addressLineString);			
				}
				String cityName = String.valueOf(XMLUtils.getValueAtXPath(addressElement, "./ns:CityName"));
				String postalCode = String.valueOf(XMLUtils.getValueAtXPath(addressElement, "./ns:PostalCode"));
				String countryName = String.valueOf(XMLUtils.getValueAtXPath(addressElement, "./ns:CountryName"));
				String stateProv = String.valueOf(XMLUtils.getValueAtXPath(addressElement, "./ns:StateProv"));
				
				addressJSON.put("addressLine", addressLineArray);
				addressJSON.put("cityName", cityName);
				addressJSON.put("postalCode", postalCode);
				addressJSON.put("countryName", countryName);
				addressJSON.put("stateProv", stateProv);
				
				customerJSON.put("address", addressJSON);
				
				//Address Ends
		
		JSONObject profileJSON =new JSONObject();
		JSONObject profileInfoJSON =new JSONObject();
		JSONObject profilesJSON =new JSONObject();
				
		profileJSON.put("customer", customerJSON);
		profileInfoJSON.put("profile", profileJSON);
		profilesJSON.put("profileInfo", profileInfoJSON);
		resGuestJSON.put("profiles", profilesJSON);
		
		return resGuestJSON;
	}

	private static JSONArray getUniqueID(Element dynamicPkgElement) {
		Element packageOptionComponentElement = XMLUtils.getFirstElementAtXPath(dynamicPkgElement, "./ns:Components/ns:PackageOptionComponent");
		JSONArray unqIdArray = new JSONArray();

		if (packageOptionComponentElement!=null) {
		Element unqIDElem [] = XMLUtils.getElementsAtXPath(packageOptionComponentElement, "./ns:UniqueID");
		
		
		//Unique ID Array
		for(Element UnqID : unqIDElem) {
		
		String id_context = String.valueOf(XMLUtils.getValueAtXPath(UnqID, "./@ID_Context"));
		String id = String.valueOf(XMLUtils.getValueAtXPath(UnqID, "./@ID"));
		String createdDate = String.valueOf(XMLUtils.getValueAtXPath(UnqID, "./@CreatedDate"));
		
		JSONObject uniqueIDJSON = new JSONObject();
		
		uniqueIDJSON.put(JSON_PROP_ID_CONTEXT, id_context);
		uniqueIDJSON.put(JSON_PROP_ID, id);
		uniqueIDJSON.put("createdDate", createdDate);
		unqIdArray.put(uniqueIDJSON);
		//Unique ID Array End
		}
		
		}
		
		return unqIdArray;
	}

}


