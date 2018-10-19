package com.coxandkings.travel.bookingengine.orchestrator.insurance;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.insurance.InsuranceConfig;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class InsuranceBookProcessor implements InsuranceConstants {
	
	public static String process(JSONObject reqJson) throws Exception
	{
		KafkaBookProducer bookProducer = new KafkaBookProducer();
		JSONObject kafkaMsgJson = new JSONObject();
		JSONObject reqHdrJson=null;JSONObject reqBodyJson=null;UserContext userctx=null;ServiceConfig opConfig=null;Element reqElem=null;
		try
		{
			TrackingContext.setTrackingContext(reqJson);
			
			reqHdrJson = reqJson.getJSONObject("requestHeader");
			reqBodyJson = reqJson.getJSONObject("requestBody");
			
			userctx = UserContext.getUserContextForSession(reqHdrJson);
			opConfig = InsuranceConfig.getOperationConfig("book");
			//kafkaMsgJson = reqJson;
			kafkaMsgJson=new JSONObject(new JSONTokener(reqJson.toString()));
			reqElem = createSIRequest(reqHdrJson, reqBodyJson, userctx, opConfig);
		}
		catch(Exception e){
		}
		
		Element resElem = null;
		
		System.out.println(XMLTransformer.toString(reqElem));
		resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
		if (resElem == null) {
			throw new Exception("Null response received from SI");
		}
		
		System.out.println(XMLTransformer.toString(resElem));
		
		JSONObject resBodyJson = new JSONObject();
        JSONArray insuranceDetailsJsonArr = new JSONArray();
        Element[] otaInsuranceBookWrapperElems = XMLUtils.getElementsAtXPath(resElem, "./insui:ResponseBody/insu:OTA_InsuranceBookRSWrapper");
        for(Element otaInsuranceBookWrapperElem : otaInsuranceBookWrapperElems)
        {
        	//IF SI gives an error
        	Element[] errorListElems = XMLUtils.getElementsAtXPath(otaInsuranceBookWrapperElem, "./ota:OTA_InsuranceBookRS/ota:Errors/ota:Error");
        	if(errorListElems!=null && errorListElems.length!=0)
        	{
        		int errorInt=0;
        		JSONObject errorObj = new JSONObject();
        		for(Element errorListElem : errorListElems)
        		{
        			errorObj.put(String.format("%s %s", "Error",errorInt), XMLUtils.getValueAtXPath(errorListElem, "/ota:Error"));
        		}
        		return errorObj.toString();//Code will stop here if SI gives an error
        	}
//        	Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_CruiseSailAvailRS");
        	getSupplierResponseJSONV1(otaInsuranceBookWrapperElem,insuranceDetailsJsonArr);
        }
		
        JSONObject reqHeaderJson = kafkaMsgJson.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, userctx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, userctx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, userctx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, userctx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, userctx.getOrganizationHierarchy().getBU());
        kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put("product", "INSURANCE");
        kafkaMsgJson.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
		
        
        resBodyJson.put("insuranceDetails", insuranceDetailsJsonArr);
        
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, resBodyJson);
		
        bookProducer.runProducer(1, kafkaMsgJson);
        
        kafkaMsgJson = new JSONObject();
        kafkaMsgJson = resJson;
        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put("product", "INSURANCE");
        kafkaMsgJson.getJSONObject(JSON_PROP_RESBODY).put(JSON_PROP_BOOKID, reqBodyJson.get(JSON_PROP_BOOKID).toString());
//        System.out.println(kafkaMsgJson);
        bookProducer.runProducer(1, kafkaMsgJson);
        
		return resJson.toString();
	}
	
	public static void getSupplierResponseJSONV1(Element otaCategoryAvailWrapperElem,JSONArray insuranceBookDetailsJsonArr) {
		
		JSONObject insuranceDtlsJson = new JSONObject();
		
		String suppID =	XMLUtils.getValueAtXPath(otaCategoryAvailWrapperElem, "./insu:SupplierID");
		insuranceDtlsJson.put(JSON_PROP_SUPPREF, suppID);
		Element otaInsuranceElem =	XMLUtils.getFirstElementAtXPath(otaCategoryAvailWrapperElem, "./ota:OTA_InsuranceBookRS");
		
		Element planForBookElem = XMLUtils.getFirstElementAtXPath(otaInsuranceElem, "./ota:PlanForBookRS");
		
		Element policyDtlElem =	XMLUtils.getFirstElementAtXPath(planForBookElem, "./ota:PolicyDetail");
		if(policyDtlElem!=null){
			insuranceDtlsJson.put("policyDetail", getPoilicyDtlsJson(policyDtlElem));
		}
		
		Element[] coveredTravelerElems = XMLUtils.getElementsAtXPath(planForBookElem, "./ota:CoveredTravelers/ota:CoveredTraveler");
		if(coveredTravelerElems!=null && coveredTravelerElems.length!=0){
			insuranceDtlsJson.put("coveredTravelers", getCoveredTravelersJson(coveredTravelerElems));
		}
		
		Element planCostElem = XMLUtils.getFirstElementAtXPath(planForBookElem, "./ota:PlanCost");
		if(planCostElem!=null){
			insuranceDtlsJson.put("planCost", getPlanCostJson(planCostElem));
		}
		insuranceBookDetailsJsonArr.put(insuranceDtlsJson);
	}
	
	private static JSONObject getPlanCostJson(Element planCostElem)
	{
		JSONObject planCostJson = new JSONObject();
		
		planCostJson.put("amount", XMLUtils.getValueAtXPath(planCostElem, "./@Amount"));
		
		Element[] chargesElems = XMLUtils.getElementsAtXPath(planCostElem, "./ota:Charges");
		
		JSONArray chargesJsonArr = new JSONArray();
		for(Element chargesElem: chargesElems)
		{
			JSONObject chargeJson = new JSONObject();
			Element[] taxElems = XMLUtils.getElementsAtXPath(chargesElem, "./ota:Taxes/ota:Tax");
			
			JSONArray taxesJsonArr = new JSONArray();
			for(Element taxElem : taxElems)
			{
				JSONObject taxJson = new JSONObject();
				taxJson.put("amount", XMLUtils.getValueAtXPath(taxElem, "./@Amount"));
				taxJson.put("percent", XMLUtils.getValueAtXPath(taxElem, "./@Percent"));
				
				taxesJsonArr.put(taxJson);
			}
			chargeJson.put("taxes", taxesJsonArr);
			chargesJsonArr.put(chargeJson);
		}
		planCostJson.put("charges", chargesJsonArr);
		
		return planCostJson;
	}
	
	private static JSONArray getCoveredTravelersJson(Element[] coveredTravelerElems)
	{
		JSONArray coveredTravelersJsonArr = new JSONArray();
		
		for(Element coveredTravelerElem : coveredTravelerElems)
		{
			JSONObject coveredTravlrJson = new JSONObject();
			
			coveredTravlrJson.put("citizenCountryName", XMLUtils.getValueAtXPath(coveredTravelerElem, "./ota:CitizenCountryName/@Code"));
			coveredTravlrJson.put("docID", XMLUtils.getValueAtXPath(coveredTravelerElem, "./ota:Document/@DocID"));
			coveredTravlrJson.put("beneficiaryRelation", XMLUtils.getValueAtXPath(coveredTravelerElem, "./ota:Beneficiary/@Relation"));
			coveredTravlrJson.put("beneficiaryName", XMLUtils.getValueAtXPath(coveredTravelerElem, "./ota:Beneficiary/ota:Name/ota:GivenName"));
			
			coveredTravelersJsonArr.put(coveredTravlrJson);
		}
		
		return coveredTravelersJsonArr;
	}
	
	private static JSONObject getPoilicyDtlsJson(Element policyDtlElem)
	{
		JSONObject policyDtlsJson = new JSONObject();
		
		Element policyNmbrElem = XMLUtils.getFirstElementAtXPath(policyDtlElem, "./ota:PolicyNumber");
		if(policyNmbrElem!=null)
		{
			policyDtlsJson.put("policyNumber", getPolicyNmbrJson(policyNmbrElem));
		}
		
		Element refNumberElem =	XMLUtils.getFirstElementAtXPath(policyDtlElem, "./ota:RefNumber");
		if(refNumberElem!=null)
		{
			policyDtlsJson.put("refNumber", getRefNmbrJson(refNumberElem));
		}
		
		Element[] planRestrictionElems = XMLUtils.getElementsAtXPath(policyDtlElem, "./ota:PlanRestrictions/ota:PlanRestriction");
		if(planRestrictionElems!=null && planRestrictionElems.length!=0)
		{
			policyDtlsJson.put("planRestrictions", getPlanRestrictionsJsonArr(planRestrictionElems));
		}
		
		policyDtlsJson.put("poilicyDetailURL", XMLUtils.getValueAtXPath(policyDtlElem, "./ota:PolicyDetailURL"));
		
		return policyDtlsJson;
	}
	
	private static JSONArray getPlanRestrictionsJsonArr(Element[] planRestrictionElems)
	{
		JSONArray planRestrictionsJsonArr = new JSONArray();
		for(Element planRestrictionElem : planRestrictionElems )
		{
			JSONObject planRestrictionsJson = new JSONObject();
			planRestrictionsJson.put("code", XMLUtils.getValueAtXPath(planRestrictionElem, "./@Code"));
			planRestrictionsJsonArr.put(planRestrictionsJson);
		}
		
		return planRestrictionsJsonArr;
	}
	
	private static JSONObject getRefNmbrJson(Element refNumberElem)
	{
		JSONObject refNumberJson = new JSONObject();
		
		refNumberJson.put("type", XMLUtils.getValueAtXPath(refNumberElem, "./@Type"));
		refNumberJson.put("id", XMLUtils.getValueAtXPath(refNumberElem, "./@ID"));
		
		return refNumberJson;
	}
	
	private static JSONObject getPolicyNmbrJson(Element policyNmbrElem)
	{
		JSONObject policyNmbrJson = new JSONObject();
		
		policyNmbrJson.put("type", XMLUtils.getValueAtXPath(policyNmbrElem, "./@Type"));
		policyNmbrJson.put("id", XMLUtils.getValueAtXPath(policyNmbrElem, "./@ID"));
		
		return policyNmbrJson;
	}
	
	private static Element createSIRequest(JSONObject reqHdrJson, JSONObject reqBodyJson, UserContext userctx,
			ServiceConfig opConfig) throws Exception {
				try {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();	
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./insui:RequestBody");
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./insu:OTA_InsuranceBookRQWrapper");
		reqBodyElem.removeChild(wrapperElem);

		String sessionID = reqHdrJson.getString("sessionID");
		
		createHeader(reqElem,reqHdrJson);
		
		Element suppWrapperElem = null;
		int seqItr =0;
		
		JSONArray insuDtlsJsonArr = reqBodyJson.getJSONArray("insuranceDetails");
		
		for(int i=0;i<insuDtlsJsonArr.length();i++)
		{
				JSONObject insuDtlsJson = insuDtlsJsonArr.getJSONObject(i);
				
			String suppID =	insuDtlsJson.getString("supplierRef");
			
			suppWrapperElem = (Element) wrapperElem.cloneNode(true);
			reqBodyElem.appendChild(suppWrapperElem);
			
			//Request Header starts
			ProductSupplier prodSupplier = userctx.getSupplierForProduct(PROD_CATEG_OTHERPRODUCTS, "Insurance", suppID);
			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}
			seqItr++;
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./insu:RequestHeader/com:SupplierCredentialsList");
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
			Element supplierCredentials = prodSupplier.toElement(ownerDoc);
			
//			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			//Request Body starts
				
			Element sequence = ownerDoc.createElementNS(Constants.NS_COM, "Sequence");
			sequence.setTextContent(String.valueOf(seqItr));
			
			Element credentialsElem = XMLUtils.getFirstElementAtXPath(supplierCredentials, "./com:Credentials");
			supplierCredentials.insertBefore(sequence, credentialsElem);
			
			suppCredsListElem.appendChild(supplierCredentials);
//--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------	
			Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./insu:SupplierID");
			suppIDElem.setTextContent(suppID);
			
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./insu:Sequence");
			sequenceElem.setTextContent(String.valueOf(seqItr));
			
			Element otaInsuranceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_InsuranceBookRQ");
			
			createPOS(ownerDoc,otaInsuranceElem);
			
			JSONObject planForBookJson = insuDtlsJson.getJSONObject("planForBook");
			
			Element planForBookElem = XMLUtils.getFirstElementAtXPath(otaInsuranceElem, "./ota:PlanForBookRQ");
			planForBookElem.setAttribute("PlanID", planForBookJson.getString("planID"));
			planForBookElem.setAttribute("Name", planForBookJson.getString("name"));
			planForBookElem.setAttribute("Type", planForBookJson.getString("type"));
			planForBookElem.setAttribute("TypeID", planForBookJson.getString("typeID"));
//----------Creating Covered Travelers--------------------------------------------------------------------------------------------------------------------------------			
			JSONArray cvrdTrvlrsJsonArr = insuDtlsJson.getJSONArray("coveredTravellers");
			
			Element cvrdTrvlrsElem = XMLUtils.getFirstElementAtXPath(planForBookElem, "./ota:CoveredTravelers");
			
			for(int j=0;j<cvrdTrvlrsJsonArr.length();j++)
			{
				JSONObject cvrdTrvlrsJson =	cvrdTrvlrsJsonArr.getJSONObject(j);
				
				Element cvrdTrvlrElem = ownerDoc.createElementNS(NS_OTA, "CoveredTraveler");
				cvrdTrvlrElem.setAttribute("RPH", cvrdTrvlrsJson.getString("rph"));
				
				//Beneficiary Elem------------------------------
				JSONObject beneficiaryJson = cvrdTrvlrsJson.getJSONObject("beneficiary");
				
				Element beneficiaryElem = ownerDoc.createElementNS(NS_OTA, "Beneficiary");
				beneficiaryElem.setAttribute("Relation", beneficiaryJson.getString("relation"));
				beneficiaryElem.setAttribute("ID", beneficiaryJson.getString("id"));
				
				Element nameElem = ownerDoc.createElementNS(NS_OTA, "Name");
				
				Element givenNameBenElem = ownerDoc.createElementNS(NS_OTA, "GivenName");
				givenNameBenElem.setTextContent(beneficiaryJson.getString("givenName"));
				
				Element surNameBenElem = ownerDoc.createElementNS(NS_OTA, "Surname");
				surNameBenElem.setTextContent(beneficiaryJson.getString("surName"));
				
				nameElem.appendChild(surNameBenElem);
				nameElem.insertBefore(givenNameBenElem, surNameBenElem);
				
				beneficiaryElem.appendChild(nameElem);
				
				cvrdTrvlrElem.appendChild(beneficiaryElem);
				//Document Elem----------------------------------------------------------------
				JSONObject documentJson = cvrdTrvlrsJson.getJSONObject("document");
				
				Element documentElem = ownerDoc.createElementNS(NS_OTA, "Document");
				documentElem.setAttribute("DocID", documentJson.getString("docID"));
				documentElem.setAttribute("DocType", documentJson.getString("docType"));
				documentElem.setAttribute("Gender", documentJson.getString("gender"));
				
				cvrdTrvlrElem.insertBefore(documentElem, beneficiaryElem);
				
				//Telephone Elem-------------------------------------------------------------
				JSONArray telephoneJsonArr = cvrdTrvlrsJson.getJSONArray("telephone");
				Element telephoneElem = null;
				for(int k=0;k<telephoneJsonArr.length();k++)
				{
					JSONObject telephoneJson = telephoneJsonArr.getJSONObject(k);
					
					telephoneElem =	ownerDoc.createElementNS(NS_OTA,"Telephone");
					telephoneElem.setAttribute("PhoneLocationType", telephoneJson.getString("phoneLocationType"));
					telephoneElem.setAttribute("PhoneNumber", telephoneJson.getString("phoneNumber"));
					
					cvrdTrvlrElem.insertBefore(telephoneElem, documentElem);
				}
				
				//Address Elem--------------------------------------------------------------------
				JSONObject addressJson = cvrdTrvlrsJson.getJSONObject("address");
				
				Element addressElem = ownerDoc.createElementNS(NS_OTA, "Address");
				addressElem.setAttribute("UseType", addressJson.getString("useType"));
				
				Element bldgRoomElem = ownerDoc.createElementNS(NS_OTA, "BldgRoom");
				bldgRoomElem.setTextContent(addressJson.getString("bldgRoom"));
				
				Element addressLineElem = ownerDoc.createElementNS(NS_OTA, "AddressLine");
				addressLineElem.setTextContent(addressJson.getString("addressLine"));
				
				Element cityNameElem = ownerDoc.createElementNS(NS_OTA, "CityName");
				cityNameElem.setTextContent(addressJson.getString("cityName"));
				
				Element postalCodeElem = ownerDoc.createElementNS(NS_OTA, "PostalCode");
				postalCodeElem.setTextContent(addressJson.getString("postalCode"));
				
				Element countyElem = ownerDoc.createElementNS(NS_OTA, "County");
				countyElem.setTextContent(addressJson.getString("county"));
				
				Element stateProvElem = ownerDoc.createElementNS(NS_OTA, "StateProv");
				stateProvElem.setTextContent(addressJson.getString("stateCode"));
				
				Element countryNameElem = ownerDoc.createElementNS(NS_OTA, "CountryName");
				countryNameElem.setTextContent(addressJson.getString("countryName"));
				
				addressElem.appendChild(countryNameElem);
				addressElem.insertBefore(stateProvElem, countryNameElem);
				addressElem.insertBefore(countyElem, stateProvElem);
				addressElem.insertBefore(postalCodeElem, countyElem);
				addressElem.insertBefore(cityNameElem, postalCodeElem);
				addressElem.insertBefore(addressLineElem, cityNameElem);
				addressElem.insertBefore(bldgRoomElem, addressLineElem);
				
				cvrdTrvlrElem.insertBefore(addressElem, telephoneElem);
				//Email Elem--------------------------
				Element emailElem =	ownerDoc.createElementNS(NS_OTA, "Email");
				emailElem.setTextContent(cvrdTrvlrsJson.getString("email"));
				
				cvrdTrvlrElem.insertBefore(emailElem, addressElem);
				//Covered Person Elem----------------------
				JSONObject coveredPerson = cvrdTrvlrsJson.getJSONObject("coveredPerson");
				
				Element cvrdPersonElem = ownerDoc.createElementNS(NS_OTA, "CoveredPerson");
				cvrdPersonElem.setAttribute("Relation", coveredPerson.getString("relation"));
				cvrdPersonElem.setAttribute("Age", coveredPerson.getString("age"));
				cvrdPersonElem.setAttribute("BirthDate", coveredPerson.getString("birthDate"));
				
				Element surnameElem = ownerDoc.createElementNS(NS_OTA, "Surname");
				surnameElem.setTextContent(coveredPerson.getString("surName"));
				
				Element middleNameElem = ownerDoc.createElementNS(NS_OTA, "MiddleName");
				middleNameElem.setTextContent(coveredPerson.getString("middleName"));
				
				Element givenNameElem = ownerDoc.createElementNS(NS_OTA, "GivenName");
				givenNameElem.setTextContent(coveredPerson.getString("givenName"));
				
				Element namePrefixElem = ownerDoc.createElementNS(NS_OTA, "NamePrefix");
				namePrefixElem.setTextContent(coveredPerson.getString("namePrefix"));
				
				cvrdPersonElem.appendChild(surnameElem);
				cvrdPersonElem.insertBefore(middleNameElem, surnameElem);
				cvrdPersonElem.insertBefore(givenNameElem, middleNameElem);
				cvrdPersonElem.insertBefore(namePrefixElem, givenNameElem);
					
				cvrdTrvlrElem.insertBefore(cvrdPersonElem, emailElem);
				
				cvrdTrvlrsElem.appendChild(cvrdTrvlrElem);
			}
			
//----------Creating InsCoverageDetails-------------------------------------------------------------------------------------------------------------------------------
			JSONObject insCoverageDtlsJson = insuDtlsJson.getJSONObject("insCoverageDetail");
			
			Element insCovrgDtlsElem = XMLUtils.getFirstElementAtXPath(planForBookElem, "./ota:InsCoverageDetail");
			insCovrgDtlsElem.setAttribute("EffectiveDate", insCoverageDtlsJson.getString("effectiveDate"));
			insCovrgDtlsElem.setAttribute("ExpireDate", insCoverageDtlsJson.getString("expireDate"));
			
			Element totTrpQttyElem = XMLUtils.getFirstElementAtXPath(insCovrgDtlsElem, "./ota:TotalTripQuantity");
			totTrpQttyElem.setAttribute("Quantity", insCoverageDtlsJson.getString("totalTripQuantity"));
			
			JSONArray cvrdTripsJsonArr = insCoverageDtlsJson.getJSONArray("coveredTrips");
			Element cvrdTripsElem = XMLUtils.getFirstElementAtXPath(insCovrgDtlsElem, "./ota:CoveredTrips");
			
			for(int j=0;j<cvrdTripsJsonArr.length();j++)
			{
				JSONObject cvrdTripsJson = cvrdTripsJsonArr.getJSONObject(j);
				
				Element cvrdTripElem = ownerDoc.createElementNS(NS_OTA, "CoveredTrip");
				cvrdTripElem.setAttribute("Start", cvrdTripsJson.getString("start"));
				cvrdTripElem.setAttribute("Duration", cvrdTripsJson.getString("duration"));
				cvrdTripElem.setAttribute("End", cvrdTripsJson.getString("end"));
				cvrdTripElem.setAttribute("TravelStartDate", cvrdTripsJson.getString("travelStartDate"));
				cvrdTripElem.setAttribute("TravelEndDate", cvrdTripsJson.getString("travelEndDate"));
				
				JSONArray destinationsJsonArr = cvrdTripsJson.getJSONArray("destinations");
				Element destinationsElem = ownerDoc.createElementNS(NS_OTA, "Destinations");
				
				for(int k=0;k<destinationsJsonArr.length();k++)
				{
					JSONObject destinationJson = destinationsJsonArr.getJSONObject(k);
					
					Element destinationElem = ownerDoc.createElementNS(NS_OTA, "Destination");
					
					Element cityNameElem = ownerDoc.createElementNS(NS_OTA, "CityName");
					cityNameElem.setTextContent(destinationJson.getString("cityName"));
					
					Element stateProveElem = ownerDoc.createElementNS(NS_OTA, "StateProv");
					stateProveElem.setTextContent(destinationJson.getString("stateProv"));
					
					destinationElem.appendChild(stateProveElem);
					destinationElem.insertBefore(cityNameElem, stateProveElem);
					
					destinationsElem.appendChild(destinationElem);
				}
				cvrdTripElem.appendChild(destinationsElem);
				
				cvrdTripsElem.appendChild(cvrdTripElem);
			}
			
//----------Creating InsuranceCustomer-------------------------------------------------------------------------------------------------------------------------------
			
			JSONObject insuranceCustomerJson = insuDtlsJson.getJSONObject("insuranceCustomer");
			
			Element insuranceCustomerElem =	XMLUtils.getFirstElementAtXPath(planForBookElem, "./ota:InsuranceCustomer");
			insuranceCustomerElem.setAttribute("ID", insuranceCustomerJson.getString("id"));
			insuranceCustomerElem.setAttribute("Gender", insuranceCustomerJson.getString("gender"));
			insuranceCustomerElem.setAttribute("BirthDate", insuranceCustomerJson.getString("birthDate"));
			insuranceCustomerElem.setAttribute("MaritalStatus", insuranceCustomerJson.getString("maritalStatus"));
			
			//PersonName---------------------
			JSONObject personNameInsuJson =	insuranceCustomerJson.getJSONObject("personName");
			
			Element personNameInsuElem = XMLUtils.getFirstElementAtXPath(insuranceCustomerElem, "./ota:PersonName");
			
			Element namePrefixInsuElem = XMLUtils.getFirstElementAtXPath(personNameInsuElem, "./ota:NamePrefix");
			namePrefixInsuElem.setTextContent(personNameInsuJson.getString("namePrefix"));
			
			Element givenNameInsuElem = XMLUtils.getFirstElementAtXPath(personNameInsuElem, "./ota:GivenName");
			givenNameInsuElem.setTextContent(personNameInsuJson.getString("givenName"));
			
			Element middleNameInsuElem = XMLUtils.getFirstElementAtXPath(personNameInsuElem, "./ota:MiddleName");
			middleNameInsuElem.setTextContent(personNameInsuJson.getString("middleName"));
			
			Element surNameInsuElem = XMLUtils.getFirstElementAtXPath(personNameInsuElem, "./ota:Surname");
			surNameInsuElem.setTextContent(personNameInsuJson.getString("surName"));
			
			Element nameTitleInsuElem = XMLUtils.getFirstElementAtXPath(personNameInsuElem, "./ota:NameTitle");
			nameTitleInsuElem.setTextContent(personNameInsuJson.getString("nameTitle"));
			
			Element documentInsuElem = XMLUtils.getFirstElementAtXPath(personNameInsuElem, "./ota:Document");
			documentInsuElem.setAttribute("DocID", personNameInsuJson.getJSONObject("document").getString("docID"));
			
			Element addElemInsuElem = XMLUtils.getFirstElementAtXPath(personNameInsuElem, "./ota:TPA_Extensions/insu:Additionalelements");
			addElemInsuElem.setAttribute("ClientType", personNameInsuJson.getJSONObject("additionalElements").getString("clientType"));
			addElemInsuElem.setAttribute("OccupationID", personNameInsuJson.getJSONObject("additionalElements").getString("occupationID"));
			
			
			//Email----------------------
			Element emailElem =	XMLUtils.getFirstElementAtXPath(insuranceCustomerElem, "./ota:Email");
			emailElem.setTextContent(insuranceCustomerJson.getString("email"));
			
			//Address
//			JSONObject addressInsuJson = insuranceCustomerJson.getJSONObject("address");
//			
//			Element addressElem = XMLUtils.getFirstElementAtXPath(insuranceCustomerElem, "./ota:Address");
//			addressElem.setAttribute("UseType", addressInsuJson.getString("useType"));
//			
//			Element strtNmbrElem = XMLUtils.getFirstElementAtXPath(addressElem, "./ota:StreetNmbr");
//			strtNmbrElem.setTextContent(addressInsuJson.getString("streetNmbr"));
//			
//			Element bldgRoom = XMLUtils.getFirstElementAtXPath(addressElem, "./ota:BldgRoom");
//			bldgRoom.setTextContent(addressInsuJson.getString("bldgRoom"));
//			
//			Element addrssLineElem = XMLUtils.getFirstElementAtXPath(addressElem, "./ota:AddressLine");
//			addrssLineElem.setTextContent(addressInsuJson.getString("addressLine"));
//			
//			Element ctyNameElem = XMLUtils.getFirstElementAtXPath(addressElem, "./ota:CityName");
//			ctyNameElem.setTextContent(addressInsuJson.getString("cityName"));
//			
//			Element pstlCodElem = XMLUtils.getFirstElementAtXPath(addressElem, "./ota:PostalCode");
//			pstlCodElem.setTextContent(addressInsuJson.getString("postalCode"));
//			
//			Element stateProvElem = XMLUtils.getFirstElementAtXPath(addressElem, "./ota:StateProv");
//			stateProvElem.setAttribute("StateCode", addressInsuJson.getString("stateCode"));
//			
//			Element cntryNameElem = XMLUtils.getFirstElementAtXPath(addressElem, "./ota:CountryName");
//			cntryNameElem.setTextContent(addressInsuJson.getString("countryName"));
			
			//Telephone---------------------------------------------------------------------
//			JSONArray telephnsJsonArr =	insuranceCustomerJson.getJSONArray("telephone");
//			
//			for(int j=0;j<telephnsJsonArr.length();j++)
//			{
//				JSONObject telephnJson = telephnsJsonArr.getJSONObject(j);
//				
//				Element telephoneInsuElem =	ownerDoc.createElementNS(NS_OTA, "Telephone");
//				telephoneInsuElem.setAttribute("PhoneLocationType", telephnJson.getString("phoneLocationType"));
//				telephoneInsuElem.setAttribute("PhoneNumber", telephnJson.getString("phoneNumber"));
//				telephoneInsuElem.setAttribute("PhoneTechType", telephnJson.getString("phoneTechType"));
//				
//				insuranceCustomerElem.insertBefore(telephoneInsuElem, addressElem);
//			}
			
			//Citizen Country------------------------------------------------
			Element citznCntryNameElm =	XMLUtils.getFirstElementAtXPath(insuranceCustomerElem, "./ota:CitizenCountryName");
			citznCntryNameElm.setAttribute("Code",insuranceCustomerJson.getString("citizenCountryName"));
			
			//Document--------------------------------------------
			Element documentElem = XMLUtils.getFirstElementAtXPath(insuranceCustomerElem, "./ota:Document");
			documentElem.setAttribute("DocIssueCountry", insuranceCustomerJson.getString("docIssueCountry"));
			
//----------Creating PlanCost-----------------------------------------------------------------------------------------------------------------------------------------
			JSONObject planCostJson = insuDtlsJson.getJSONObject("planCost");
			
			Element planCostElem = XMLUtils.getFirstElementAtXPath(planForBookElem, "./ota:PlanCost");
			planCostElem.setAttribute("CurrencyCode", planCostJson.getString("currencyCode"));
			planCostElem.setAttribute("Amount", planCostJson.getString("amount"));
			
			JSONObject basePremJson = planCostJson.getJSONObject("basePremium");
			
			Element basePremiumElem = XMLUtils.getFirstElementAtXPath(planCostElem, "./ota:BasePremium");
			basePremiumElem.setAttribute("CurrencyCode", basePremJson.getString("currencyCode"));
			basePremiumElem.setAttribute("Amount", basePremJson.getString("amount"));
			
			JSONArray chargeJsonArr = planCostJson.getJSONArray("charges");
			
			Element chargesElem = XMLUtils.getFirstElementAtXPath(planCostElem, "./ota:Charges");
			
			for(int j=0;j<chargeJsonArr.length();j++)
			{
				JSONObject chargeJson =	chargeJsonArr.getJSONObject(j);
				
				Element chargeElem = ownerDoc.createElementNS(NS_OTA, "Charge");
				chargeElem.setAttribute("Amount", chargeJson.getString("amount"));
				chargeElem.setAttribute("CurrencyCode", chargeJson.getString("currencyCode"));
				
				JSONArray taxesJsonArr = chargeJson.getJSONArray("taxes");
				
				Element taxesElem =	ownerDoc.createElementNS(NS_OTA, "Taxes");
				
				for(int k=0;k<taxesJsonArr.length();k++)
				{
					JSONObject taxesJson =  taxesJsonArr.getJSONObject(k);
					
					Element taxElem = ownerDoc.createElementNS(NS_OTA, "Tax");
					taxElem.setAttribute("Amount", taxesJson.getString("amount"));
					
					taxesElem.appendChild(taxElem);
				}
				chargeElem.appendChild(taxesElem);
				
				chargesElem.appendChild(chargeElem);
			}
		}
		
		return reqElem;
	}
		catch(Exception e){
			e.printStackTrace();
			return null;
		}
	}
	
	public static void createPOS(Document ownerDoc, Element otaCategoryAvail) {
		Element Source = XMLUtils.getFirstElementAtXPath(otaCategoryAvail, "./ota:POS/ota:Source");
		Source.setAttribute("ISOCurrency", "AUD");
		
		Element RequesterID = ownerDoc.createElementNS(Constants.NS_OTA,"RequestorID");
//		RequesterID.setAttribute("ID", "US");
		RequesterID.setAttribute("Type", "A");
		
		Source.appendChild(RequesterID);
	}
	
	protected static void createHeader(Element reqElem,JSONObject reqHdrJson) {
		
		Element sessionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./insu:RequestHeader/com:SessionID");
		sessionElem.setTextContent(reqHdrJson.getString(JSON_PROP_SESSIONID));

		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./insu:RequestHeader/com:TransactionID");
		transactionElem.setTextContent(reqHdrJson.getString(JSON_PROP_TRANSACTID));

		Element userElem = XMLUtils.getFirstElementAtXPath(reqElem, "./insu:RequestHeader/com:UserID");
		userElem.setTextContent(reqHdrJson.getString(JSON_PROP_USERID));
	}
	
}
