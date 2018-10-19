package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class HolidaysRetrieveProcessor implements HolidayConstants {

private static final Logger logger = LogManager.getLogger(HolidaysRetrieveProcessor.class);
  
  public static String process(JSONObject requestJson) throws RequestProcessingException, ValidationException, InternalProcessingException
  {
	  Element requestElement = null;
		JSONObject requestHeader = null, requestBody = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
    
	  try {
		  validateRequestParameters(requestJson);
		  TrackingContext.setTrackingContext(requestJson);
		  
		  requestHeader = requestJson.getJSONObject(JSON_PROP_REQHEADER);
		  requestBody = requestJson.getJSONObject(JSON_PROP_REQBODY);
		  
		  opConfig = HolidaysConfig.getOperationConfig("retrieve");
		  usrCtx = UserContext.getUserContextForSession(requestHeader);
		  
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
      logger.info(String.format("SI XML Request for Retrieve = %s", XMLTransformer.toString(requestElement)));
      responseElement = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), requestElement);
      logger.info(String.format("SI XML Response for Retrieve = %s",XMLTransformer.toString(responseElement)));
      if (responseElement == null) {
          throw new Exception("Null response received from SI");
      }
      
      JSONObject resBodyJson = convertSIResponse(responseElement);
      
      JSONObject resJson = new JSONObject();
      resJson.put("responseHeader", requestHeader);
      resJson.put("responseBody", resBodyJson);
      logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
      
      return resJson.toString();
    }
    catch (Exception x) {
		logger.error("Exception received while processing", x);
		throw new InternalProcessingException(x);
	}
  }

private static JSONObject convertSIResponse(Element responseElement) {
	
	JSONObject resBodyJson = new JSONObject();
	JSONArray dynamicPackageArray = new JSONArray();

	Element[] oTA_wrapperElems = XMLUtils.getElementsAtXPath(responseElement,"./pac1:ResponseBody/pac:OTA_DynamicPkgBookRSWrapper");
	for (Element oTA_wrapperElem : oTA_wrapperElems) {
		String supplierIDStr = String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:SupplierID"));
		String sequenceStr = String.valueOf(XMLUtils.getValueAtXPath(oTA_wrapperElem, "./pac:Sequence"));

		// -----Error Handling Started-----
		JSONObject errorJson = new JSONObject();
		HolidaysUtil.SIErrorHandler(oTA_wrapperElem, errorJson);
		if (errorJson.length() != 0) 
			logTheError(errorJson, supplierIDStr, sequenceStr);

		else {
			Element[] dynamicPackageElemArray = XMLUtils.getElementsAtXPath(oTA_wrapperElem,"./ns:OTA_DynamicPkgBookRS/ns:DynamicPackage");
			for (Element dynamicPackElem : dynamicPackageElemArray) {
					
				JSONObject dynamicPackJson = HolidaysSearchProcessor.getSupplierResponseDynamicPackageJSON(dynamicPackElem, false);
				
				String tourCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/@ID"));
				String subTourCode = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:Components/ns:PackageOptionComponent/ns:PackageOptions/ns:PackageOption/@QuoteID"));
				String brandName = String.valueOf(XMLUtils.getValueAtXPath(dynamicPackElem,"./ns:GlobalInfo/ns:DynamicPkgIDs/ns:DynamicPkgID/ns:CompanyName/@CompanyShortName"));
				
				dynamicPackJson.put("tourCode", tourCode);
				dynamicPackJson.put("subTourCode", subTourCode);
				dynamicPackJson.put("brandName", brandName);
				
				Element[] resGuestElems = XMLUtils.getElementsAtXPath(dynamicPackElem,"./ns:ResGuests/ns:ResGuest");
				JSONArray resGuestArr = new JSONArray();
				for (Element resGuest : resGuestElems) {
					JSONObject resGuestsJson = HolidaysBookProcessor.getResGuests(resGuest);
					resGuestArr.put(resGuestsJson);
				}

				dynamicPackJson.put("resGuests", resGuestArr);
				dynamicPackageArray.put(dynamicPackJson);
			}
			resBodyJson.put("dynamicPackage", dynamicPackageArray);
		}
	}
	return resBodyJson;
}

private static void logTheError(JSONObject errorJson, String supplierIDStr, String sequenceStr) {
	errorJson.put("sequence", sequenceStr);
	errorJson.put("supplierID", supplierIDStr);
	errorJson.put("errorSource", "Error from SI or Supplier");
	logger.trace(String.format("Error received from SI = %s", errorJson.toString()));
}

//private static Element createSIRequest(JSONObject requestHeader, JSONObject requestBody, OperationConfig opConfig, UserContext usrCtx) throws Exception {
private static Element createSIRequest(JSONObject requestHeader, JSONObject requestBody, ServiceConfig opConfig, UserContext usrCtx) throws Exception {
	
	Element requestElement = (Element) opConfig.getRequestXMLShell().cloneNode(true);
    
    //create Document object associated with request node, this Document object is also used to create new nodes.
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
    
    Element wrapperElement = XMLUtils.getFirstElementAtXPath(requestBodyElement, "./pac:OTA_ReadRQWrapper");
    requestBodyElement.removeChild(wrapperElement);
    
    int sequence = 0;
    JSONArray dynamicPackageArray = requestBody.getJSONArray(JSON_PROP_DYNAMICPACKAGE);
    for (int i=0; i < dynamicPackageArray.length(); i++) 
    {
        JSONObject dynamicPackageObj = dynamicPackageArray.getJSONObject(i);
        sequence++;
    
        String supplierID = dynamicPackageObj.getString(JSON_PROP_SUPPREF);
        Element supWrapperElement = null;
        
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
        
        //getting UniqueID Element
        Element uniqueID = XMLUtils.getFirstElementAtXPath(supWrapperElement, "./ns:OTA_ReadRQ/ns:UniqueID");

        String invoiceNumber = dynamicPackageObj.getString("invoiceNumber");
        if(invoiceNumber != null && invoiceNumber.length() > 0)
        {
          uniqueID.setAttribute("ID", invoiceNumber);
        }
        
        String idContext = dynamicPackageObj.getString("idContext");
        if(idContext != null && idContext.length() > 0)
        {
          uniqueID.setAttribute("ID_Context", idContext);
        }
        
        String type = dynamicPackageObj.getString("type");
        if(type != null && type.length() > 0)
        {
          uniqueID.setAttribute("Type", type);
        }
      }
    return requestElement;
}

private static void validateRequestParameters(JSONObject reqJson) throws ValidationException {
	HolidaysRequestValidator.validateDynamicPkg(reqJson);
	HolidaysRequestValidator.validateTourCode(reqJson);
	HolidaysRequestValidator.validateSubTourCode(reqJson);
	HolidaysRequestValidator.validateBrandName(reqJson);
	HolidaysRequestValidator.validateInvoiceNumberAndIDContext(reqJson);
}
  
  
}
