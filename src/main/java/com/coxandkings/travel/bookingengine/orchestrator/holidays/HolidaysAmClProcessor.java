package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.holidays.HolidaysConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class HolidaysAmClProcessor implements HolidayConstants {
	
	private static final Logger logger = LogManager.getLogger(HolidaysAmClProcessor.class);
	
	public static String process(JSONObject requestJson) throws RequestProcessingException, InternalProcessingException {
		Element requestElement = null;
		JSONObject requestHeader = null, requestBody = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		KafkaBookProducer bookProducer = null;
		
		try {
			TrackingContext.setTrackingContext(requestJson);
			opConfig = HolidaysConfig.getOperationConfig("book");
			
			JSONObject transReqJSON = HolidaysAmClCompanyPolicy.requestJSONTransformation(requestJson);
			logger.trace("Transformed req json: "+transReqJSON);
			
			requestHeader = transReqJSON.getJSONObject(JSON_PROP_REQHEADER);
			requestBody = transReqJSON.getJSONObject(JSON_PROP_REQBODY);
			
			usrCtx = UserContext.getUserContextForSession(requestHeader);
			bookProducer = new KafkaBookProducer();
			sendPreAmClKafkaMessage(bookProducer, requestJson,usrCtx);
			
			//TODO: Bypassing this flow till we have concrete sample of SI XML request
			//requestElement = createSIRequest(opConfig, usrCtx, requestHeader, requestBody);
		}
		catch (Exception x) {
			x.printStackTrace();
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
		try {			
			
			//TODO: Bypassing this flow till we have concrete sample of SI XML request
			/*Element resElem = null;
            resElem = HTTPServiceConsumer.consumeXMLService("SI/Cancel", opConfig.getSIServiceURL(), HolidaysConfig.getHttpHeaders(), requestElement);
            if (resElem == null) {
            	throw new Exception("Null response received from SI");
            }
            System.out.println("XML Request for SI: " + XMLTransformer.toString(requestElement));*/
			
            JSONObject resJson = new JSONObject();
            resJson.put(JSON_PROP_RESHEADER, requestHeader);
            
            //JSONObject resBodyJson = new JSONObject();
            resJson.put(JSON_PROP_RESBODY, requestBody);
            
            JSONObject companyPolicyRes = null;
            
            //Applying Company Policy
       	 	companyPolicyRes = HolidaysAmClCompanyPolicy.getAmClCompanyPolicy(CommercialsOperation.Ammend, requestJson, resJson);
            
            //TODO:Call BRMS here, hardcoding for now
            
            requestBody.put("supplierCharges", 200);
            requestBody.put("companyCharges", 100);
            requestBody.put("companyChargesCurrencyCode", "INR");
            requestBody.put("supplierChargesCurrencyCode", "INR");
            
            
            
            sendPostAmClKafkaMessage(bookProducer, requestJson, resJson);
            
            //JSONArray supplierBookReferencesReqJsonArr = new JSONArray();
			
          //TODO: Bypassing this flow till we have concrete sample of SI XML response
            /*Element[] resWrapperElems = AirPriceProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_CancelRSWrapper"));
             for (Element resWrapperElem : resWrapperElems) {
             	
             	if(XMLUtils.getFirstElementAtXPath(resWrapperElem, "./air:ErrorList")!=null)
             		{	
             		//JSONObject cancelReq=reqBodyJson.getJSONArray(JSON_PROP_CANCELREQS).getJSONObject(index);
             		
             		Element errorMessage=XMLUtils.getFirstElementAtXPath(resWrapperElem, "./air:ErrorList/com:Error/com:ErrorCode");
             		String errMsgStr=errorMessage.getTextContent().toString();
             		if(CANCEL_SI_ERR.equalsIgnoreCase(errMsgStr))
             			{	
             			logger.error("This service is not supported. Kindly contact our operations team for support.");
             			callOperationTodo(resJson,requestBody);
             			return getSIErrorResponse(resJson).toString();
             		
             			
             			}
             		
             		}
             	
             	Element resBodyElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_CancelRS");
            	
            	
            	JSONObject supplierBookReferencesReqJson= new JSONObject();
            	
            	
            	supplierBookReferencesReqJson.put(JSON_PROP_SUPPREF, XMLUtils.getFirstElementAtXPath(resWrapperElem, "./air:SupplierID").getTextContent());
            	supplierBookReferencesReqJson.put(JSON_PROP_BOOKREFID, XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:UniqueID").getAttribute("ID"));
            	
            
            	Element[] cancelRulesElemArr=XMLUtils.getElementsAtXPath(resBodyElem, "./ota:CancelInfoRS/ota:CancelRules/ota:CancelRule");
            	JSONArray cancelRulesJsonArr=new JSONArray();
            	for(Element cancelRuleElem :cancelRulesElemArr) {
            		JSONObject cancelRuleJson=new JSONObject();
            		cancelRuleJson.put(cancelRuleElem.getAttribute("Type"), cancelRuleElem.getAttribute("Amount"));
            		cancelRulesJsonArr.put(cancelRuleJson);
            		}
            	supplierBookReferencesReqJson.put(JSON_PROP_CANCELRULES, cancelRulesJsonArr);
            
            	supplierBookReferencesReqJsonArr.put(supplierBookReferencesReqJson); 
             	}
             
             resBodyJson.put(JSON_PROP_SUPPBOOKREFS, supplierBookReferencesReqJsonArr);
             resJson.put(JSON_PROP_RESBODY, resBodyJson);*/
             
            return resJson.toString();
         	}
			catch (Exception x) {
				x.printStackTrace();
			// TODO: Is this the right thing to do? Or should this be pushed to operations Todo queue?
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
			}
	}

	private static void sendPostAmClKafkaMessage(KafkaBookProducer bookProducer, JSONObject requestJson,
			JSONObject resJson) throws Exception {
		
		JSONObject reqBodyJson = requestJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject kafkaMsgJson=resJson;
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_PROD, JSON_PROP_HOLIDAYS);
		
		//TODO: write a logic to send Failed as status in case AmCL is unsuccessful or check With SI if their response has error(Only after we get Schema)
		kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
		bookProducer.runProducer(1, kafkaMsgJson);
		logger.trace(String.format("Holidays Amend/Cancel Response Kafka Message: %s", kafkaMsgJson.toString()));
	}


	private static void sendPreAmClKafkaMessage(KafkaBookProducer bookProducer, JSONObject requestJson,
			UserContext usrCtx) throws Exception {

		JSONObject kafkaMsg=new JSONObject(new JSONTokener(requestJson.toString()));
		JSONObject reqBodyJson = requestJson.getJSONObject(JSON_PROP_REQBODY);
		JSONObject reqHeaderJson = kafkaMsg.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, JSON_PROP_HOLIDAYS);
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
		bookProducer.runProducer(1, kafkaMsg);
		logger.trace(String.format("Holidays Amend/Cancel Request Kafka Message: %s", kafkaMsg.toString()));
		
	}

	private static Object getSIErrorResponse(JSONObject resJson) {
		JSONObject errorMessage=new JSONObject();
		
		errorMessage.put("errorMessage", "This service is not supported. Kindly contact our operations team for support.");
		 
		resJson.put(JSON_PROP_RESBODY, errorMessage);
        
		return resJson;
		
	}

	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject requestHeader, JSONObject requestBody) throws Exception {
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject requestHeader, JSONObject requestBody) throws Exception {
	
					Element requestElement = (Element) opConfig.getRequestXMLShell().cloneNode(true);
					Document ownerDoc = requestElement.getOwnerDocument();

					// CREATE SI REQUEST HEADER
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

					Element supplierCredentialsList = XMLUtils.getFirstElementAtXPath(requestHeaderElement,
							"./com:SupplierCredentialsList");

					// CREATE SI REQUEST BODY
					Element requestBodyElement = XMLUtils.getFirstElementAtXPath(requestElement, "./pac1:RequestBody");
					
					Element wrapperElement = XMLUtils.getFirstElementAtXPath(requestBodyElement,
							"./pac:OTA_DynamicPkgAvailRQWrapper");
					
					//Create cancel body
					Element cancelElement = XMLUtils.getFirstElementAtXPath(wrapperElement,
							"./ns:OTA_DynamicPkgAvailRQ");
					
					//Create SupplierBookReferences  body
					/*Element supplierBookReferencesElmnt = XMLUtils.getFirstElementAtXPath(cancelElement,
							"./ns:CancelRequest/ns:SupplierBookReferences");
					
					requestBodyElement.removeChild(wrapperElement);*/
					
					
						
					int sequence = 0;
					//TODO:Create constant for cancelRequests
					JSONArray cancelRequestsArr = requestBody.getJSONArray("cancelRequests");
					for (int k = 0; k < cancelRequestsArr.length(); k++) {
						
						// Making cancel body 
						Element cancelWrapperElement = (Element) cancelElement.cloneNode(true);
						
						// Making supplierBookReference body
//						Element supplierBookReferencesWrapperElement = (Element) supplierBookReferencesElmnt.cloneNode(true);
//						requestBodyElement.appendChild(supplierBookReferencesWrapperElement);
						
						wrapperElement.appendChild(cancelWrapperElement);
						//create cancelrequest element
						Element cancelRequestsElement = XMLUtils.getFirstElementAtXPath(cancelElement, "./ns:CancelRequest");
						
						//create canceltype element
						Attr cancelType = ownerDoc.createAttribute("CancelType");
						cancelType.setValue(cancelRequestsArr.getJSONObject(k).getString("cancelType"));
						cancelRequestsElement.setAttributeNode(cancelType);
						
						//create bookId element
						Attr bookId = ownerDoc.createAttribute("BookId");
						bookId.setValue(cancelRequestsArr.getJSONObject(k).getString("bookID"));
						cancelRequestsElement.setAttributeNode(bookId);
						
						wrapperElement.appendChild(cancelRequestsElement);
						//TODO:Create constant for supplierBookReferences
						JSONArray supplierBookReferencesArr = cancelRequestsArr.getJSONObject(k).getJSONArray("supplierBookReferences");
						for (int l = 0; l < supplierBookReferencesArr.length(); l++) {
						JSONArray dynamicPackageArr = supplierBookReferencesArr.getJSONObject(l).getJSONArray(JSON_PROP_DYNAMICPACKAGE);
						
						//create supplierBookReferences
						Element supplierBookReferencesElement = XMLUtils.getFirstElementAtXPath(cancelRequestsElement,"./ns:SupplierBookReferences");
						
						//create supplierRef element
						Attr supplierRefAttr = ownerDoc.createAttribute("SupplierRef");
						supplierRefAttr.setValue(supplierBookReferencesArr.getJSONObject(l).getString("supplierRef"));
						supplierBookReferencesElement.setAttributeNode(supplierRefAttr);
						
						//create supplierRef element
						Attr bookRefIdAttr = ownerDoc.createAttribute("BookRefId");
						bookRefIdAttr.setValue(supplierBookReferencesArr.getJSONObject(l).getString("bookRefId"));
						supplierBookReferencesElement.setAttributeNode(bookRefIdAttr);
						
						
						wrapperElement.appendChild(supplierBookReferencesElement);
						for (int i = 0; i < dynamicPackageArr.length(); i++) {
						JSONObject dynamicPackageObj = dynamicPackageArr.getJSONObject(i);
						sequence++;

						String supplierID = dynamicPackageObj.getString("supplierID");
						Element supWrapperElement = null;
						Element otaAvailRQ = null;
						Element searchCriteria = null;
						Element dynamicPackage = null;

						supplierCredentialsList = HolidaysRepriceProcessorV2.getSupplierCredentialsList(ownerDoc, usrCtx, supplierID, sequence,supplierCredentialsList);

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
						searchCriteria = XMLUtils.getFirstElementAtXPath(supWrapperElement,
								"./ns:OTA_DynamicPkgAvailRQ/ns:SearchCriteria");

						// getting parent node OTA_DynamicPkgAvailRQ from SearchCriteria
						otaAvailRQ = (Element) searchCriteria.getParentNode();

						String tourCode = dynamicPackageObj.getString(JSON_PROP_TOURCODE);
						String brandName = dynamicPackageObj.getString(JSON_PROP_BRANDNAME);
						String subTourCode = dynamicPackageObj.getString(JSON_PROP_SUBTOURCODE);

						Element refPoint = XMLUtils.getFirstElementAtXPath(searchCriteria,
								"./ns:PackageOptionSearch/ns:OptionSearchCriteria/ns:Criterion/ns:RefPoint");
						Attr attributeBrandCode = ownerDoc.createAttribute(JSON_PROP_CODE);
						attributeBrandCode.setValue(brandName);
						refPoint.setAttributeNode(attributeBrandCode);

						Element optionRef = XMLUtils.getFirstElementAtXPath(searchCriteria,
								"./ns:PackageOptionSearch/ns:OptionSearchCriteria/ns:Criterion/ns:OptionRef");
						Attr attributeTourCode = ownerDoc.createAttribute(JSON_PROP_CODE);
						attributeTourCode.setValue(tourCode);
						optionRef.setAttributeNode(attributeTourCode);

						// creating element dynamic package
						dynamicPackage = XMLUtils.getFirstElementAtXPath(otaAvailRQ, "./ns:CancelRequest/ns:SupplierBookReferences/ns:DynamicPackage");
						

						// Creating Components element
						JSONObject components = dynamicPackageObj.getJSONObject(JSON_PROP_COMPONENTS);

						if (components == null || components.length() == 0) {
							throw new Exception(String.format("Object components must be set for supplier %s", supplierID));
						}

						Element componentsElement = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:Components");
						logger.trace("SI XML request: "+ requestElement);

						// Check whether hotel component is empty or not. If not empty then add pre and
						// postnight in hotel component
						if (components.has(JSON_PROP_HOTEL_COMPONENT)) {
							JSONObject hotelComponentJson = components.getJSONObject(JSON_PROP_HOTEL_COMPONENT);
							if (hotelComponentJson != null && hotelComponentJson.length() != 0) {
								JSONArray hotelComponentsJsonArray = new JSONArray();
								hotelComponentsJsonArray.put(hotelComponentJson);
								components.remove(JSON_PROP_HOTEL_COMPONENT);
								components.put(JSON_PROP_HOTEL_COMPONENT, hotelComponentsJsonArray);

								// Creating Hotel Component
								JSONArray hotelComponents = components.getJSONArray(JSON_PROP_HOTEL_COMPONENT);
								if (hotelComponents != null && hotelComponents.length() != 0) {
									// read post and pre Night and put into hotel component
									putPreAndPostNightInHotelOrCruiseComponent(components, hotelComponents,
											JSON_PROP_HOTEL_COMPONENT);
									// end
									componentsElement = HolidaysRepriceProcessorV2.getHotelComponentElement(ownerDoc, hotelComponents, componentsElement);
								}
							} else {
								JSONArray hotelComponentsJsonArray = new JSONArray();
								components.put(JSON_PROP_HOTEL_COMPONENT, hotelComponentsJsonArray);
							}

						}

						// Creating Air Component
						JSONArray airComponents = components.getJSONArray(JSON_PROP_AIR_COMPONENT);

						if (airComponents != null && airComponents.length() != 0) {
							componentsElement = HolidaysRepriceProcessorV2.getAirComponentElement(ownerDoc, dynamicPackageObj, airComponents,
									componentsElement, supplierID);
						}

						// Creating PackageOptionComponent Element
						Element packageOptionComponentElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOptionComponent");

						Attr attributeQuoteID = ownerDoc.createAttribute("QuoteID");
						attributeQuoteID.setValue(subTourCode);
						packageOptionComponentElement.setAttributeNode(attributeQuoteID);

						Element packageOptionsElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOptions");

						Element packageOptionElement = ownerDoc.createElementNS(NS_OTA, "ns:PackageOption");

						Element tpaElement = ownerDoc.createElementNS(NS_OTA, "ns:TPA_Extensions");

						// Check whether cruise component is empty or not. If not empty then add pre and
						// postnight in cruise component
						// Note- either cruise or hotel will be empty
						if (components.has(JSON_PROP_CRUISE_COMPONENT)) {
							JSONObject cruiseComponentJson = components.getJSONObject(JSON_PROP_CRUISE_COMPONENT);
							if (cruiseComponentJson != null && cruiseComponentJson.length() != 0) {
								JSONArray cruiseComponentsJsonArray = new JSONArray();
								cruiseComponentsJsonArray.put(cruiseComponentJson);
								components.remove(JSON_PROP_CRUISE_COMPONENT);
								components.put(JSON_PROP_CRUISE_COMPONENT, cruiseComponentsJsonArray);

								// Creating Cruise Component
								JSONArray cruiseComponents = components.getJSONArray(JSON_PROP_CRUISE_COMPONENT);
								if (cruiseComponents != null && cruiseComponents.length() != 0) {
									// read post and pre Night and put into cruise component
									putPreAndPostNightInHotelOrCruiseComponent(components, cruiseComponents,
											JSON_PROP_CRUISE_COMPONENT);
									// end
									tpaElement = HolidaysRepriceProcessorV2.getCruiseComponentElement(ownerDoc, cruiseComponents, tpaElement);
								}

							} else {
								JSONArray cruiseComponentsJsonArray = new JSONArray();
								components.put(JSON_PROP_CRUISE_COMPONENT, cruiseComponentsJsonArray);
							}
						}

						// Creating Transfers Component
						JSONArray transfersComponents = components.getJSONArray(JSON_PROP_TRANSFER_COMPONENT);

						if (transfersComponents != null && transfersComponents.length() != 0) {
							 tpaElement = HolidaysRepriceProcessorV2.getTransferComponentElement(ownerDoc, transfersComponents,
							 tpaElement);
							// passed components to read globalinfo component
							//HolidaysRepriceProcessor.putDynamicActionForTranfers(transfersComponents, dynamicPackageObj);

							//tpaElement = getTransferComponentElement(ownerDoc, transfersComponents, tpaElement);
						}

						// Creating Insurance Component
						JSONArray insuranceComponents = components.getJSONArray(JSON_PROP_INSURANCE_COMPONENT);

						if (insuranceComponents != null && insuranceComponents.length() != 0) {
							tpaElement = HolidaysRepriceProcessorV2.getInsuranceComponentElement(ownerDoc, insuranceComponents, tpaElement);
						}

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

								Element resGuestElement = HolidaysRepriceProcessorV2.getResGuestElement(ownerDoc, resGuest);

								resGuestsElement.appendChild(resGuestElement);
							}

							// dynamicPackage.appendChild(resGuestsElement);
						}

						// Create GlobalInfo xml element
						JSONObject globalInfo = dynamicPackageObj.getJSONObject(JSON_PROP_GLOBALINFO);

						if (globalInfo != null && globalInfo.length() != 0) {
							Element globalInfoElement = XMLUtils.getFirstElementAtXPath(dynamicPackage, "./ns:GlobalInfo");
							globalInfoElement = HolidaysRepriceProcessorV2.getGlobalInfoElement(ownerDoc, globalInfo, globalInfoElement);

							// dynamicPackage.appendChild(globalInfoElement);
						}
					}
					}
				}
		return requestElement;
	}

	private static void putPreAndPostNightInHotelOrCruiseComponent(JSONObject components, JSONArray hotelComponents,
			String componentType) {
		JSONObject postNightJson = new JSONObject();
		JSONObject preNightJson = new JSONObject();
		if (components.has(JSON_PROP_POSTNIGHT)) {
			if (componentType.equals(JSON_PROP_CRUISE_COMPONENT)) {
				components.getJSONObject(JSON_PROP_POSTNIGHT).put(JSON_PROP_DYNAMICPKGACTION,
						DYNAMICPKGACTION_CRUISE_POST);
				postNightJson = components.getJSONObject(JSON_PROP_POSTNIGHT);
				components.remove(JSON_PROP_POSTNIGHT);
				components.getJSONArray(JSON_PROP_CRUISE_COMPONENT).put(postNightJson);
			} else if (componentType.equals(JSON_PROP_HOTEL_COMPONENT)) {
				components.getJSONObject(JSON_PROP_POSTNIGHT).put(JSON_PROP_DYNAMICPKGACTION,
						DYNAMICPKGACTION_HOTEL_POST);
				postNightJson = components.getJSONObject(JSON_PROP_POSTNIGHT);
				components.remove(JSON_PROP_POSTNIGHT);
				components.getJSONArray(JSON_PROP_HOTEL_COMPONENT).put(postNightJson);
			}
		}
		if (components.has(JSON_PROP_PRENIGHT)) {
			if (componentType.equals(JSON_PROP_CRUISE_COMPONENT)) {
				components.getJSONObject(JSON_PROP_PRENIGHT).put(JSON_PROP_DYNAMICPKGACTION,
						DYNAMICPKGACTION_CRUISE_PRE);
				preNightJson = components.getJSONObject(JSON_PROP_PRENIGHT);
				components.remove(JSON_PROP_PRENIGHT);
				components.getJSONArray(JSON_PROP_CRUISE_COMPONENT).put(preNightJson);
			} else if (componentType.equals(JSON_PROP_HOTEL_COMPONENT)) {
				components.getJSONObject(JSON_PROP_PRENIGHT).put(JSON_PROP_DYNAMICPKGACTION,
						DYNAMICPKGACTION_HOTEL_PRE);
				preNightJson = components.getJSONObject(JSON_PROP_PRENIGHT);
				components.remove(JSON_PROP_PRENIGHT);
				components.getJSONArray(JSON_PROP_HOTEL_COMPONENT).put(preNightJson);
			}
		}

	}
}
