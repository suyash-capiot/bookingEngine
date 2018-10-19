package com.coxandkings.travel.bookingengine.orchestrator.visa;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.visa.VisaConfig;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class VisaSearchProcessor implements VisaConstants{

	private static final Logger logger = LogManager.getLogger(VisaSearchProcessor.class);
	
	public static String process(JSONObject reqJson) throws Exception
	{
		
		//OperationConfig opConfig = VisaConfig.getOperationConfig("search");
		ServiceConfig opConfig = VisaConfig.getOperationConfig("search");
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		TrackingContext.setTrackingContext(reqJson);
		JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
		JSONObject reqBdyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
		
		UserContext userctx = UserContext.getUserContextForSession(reqHdrJson);
		List<ProductSupplier> prodSuppliers = userctx.getSuppliersForProduct(PROD_CATEG_OTHERPRODUCTS,PRODUCT_VISA_PRODS);
		int seqItr =0;
		try {
			createHeader(reqElem,reqHdrJson);
			
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./visai:RequestHeader/com:SupplierCredentialsList");
			
			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./visai:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./visa:OTA_SearchVisaRQWrapper");
			reqBodyElem.removeChild(wrapperElem);
			
			Element subwrapperElem =null;
	        for (ProductSupplier prodSupplier : prodSuppliers) 
	        {
	            suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
	        
		        JSONArray countryCodesArr =	reqBdyJson.getJSONArray(JSON_PROP_COUNTRYCODES);
		        for(int i=0;i<countryCodesArr.length();i++)
		        {
		        	JSONObject countryCodeJson = countryCodesArr.getJSONObject(i);
		        	
		        	subwrapperElem = (Element) wrapperElem.cloneNode(true);
		        	reqBodyElem.appendChild(subwrapperElem);
		        	
		        	Element suppIDElem = XMLUtils.getFirstElementAtXPath(subwrapperElem, "./visa:SupplierID");
		        	suppIDElem.setTextContent(prodSupplier.getSupplierID());
		        	
		        	Element sequenceElem =	XMLUtils.getFirstElementAtXPath(subwrapperElem, "./visa:Sequence");
		        	sequenceElem.setTextContent(String.valueOf(seqItr));
		        	seqItr++;
		        	
		        	Element otaSearchVisaElem = XMLUtils.getFirstElementAtXPath(subwrapperElem, "./ota:OTA_SearchVisaRQ");
		        	otaSearchVisaElem.setAttribute("RequestedCurrency", reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCURR));
		        	
		        	createPOS(ownerDoc,otaSearchVisaElem);
		        	
		        	Element countryElem = XMLUtils.getFirstElementAtXPath(otaSearchVisaElem, "./ota:VisaSearchRequest/ota:CountryDetails/ota:Country");
		        	countryElem.setAttribute("Code", countryCodeJson.getString(JSON_PROP_CODE));
		        }
	        }
		}
		catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
		
		Element resElem = null;
        //resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), VisaConfig.getHttpHeaders(), reqElem);
		resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
        if (resElem == null) {
        	throw new Exception("Null response received from SI");
        }
		
        JSONObject resBodyJson = new JSONObject();
        JSONArray visaOptionsJsonArr = new JSONArray();
        Element[] otaSearchVisaWrapperElems = XMLUtils.getElementsAtXPath(resElem, "./visai:ResponseBody/visa:OTA_SearchVisaRSWrapper");
        for(Element otaSearchVisaWrapperElem : otaSearchVisaWrapperElems)
        {
        	//IF SI gives an error
        	Element[] errorListElems = XMLUtils.getElementsAtXPath(otaSearchVisaWrapperElem, "./ota:OTA_SearchVisaRS/ota:Errors/ota:Error");
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
        	getSupplierResponseJSON(otaSearchVisaWrapperElem,visaOptionsJsonArr);
        }
        resBodyJson.put(JSON_PROP_VISAOPTIONS, visaOptionsJsonArr);
        
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, resBodyJson);
        
		return resJson.toString();
	}
	
	public static void getSupplierResponseJSON(Element otaSearchVisaWrapperElem,JSONArray visaOptionsJsonArr) {
		
		JSONObject visaDetailsJson = new JSONObject();
		
		String suppID = XMLUtils.getValueAtXPath(otaSearchVisaWrapperElem, "./visa:SupplierID");
		visaDetailsJson.put(JSON_PROP_SUPPREF, suppID);
		
		Element visaSearchRSElem = XMLUtils.getFirstElementAtXPath(otaSearchVisaWrapperElem, "./ota:OTA_SearchVisaRS/ivans:VisaSearchResponse/ivans:VisaDetails");
		String countryCode = XMLUtils.getValueAtXPath(visaSearchRSElem, "./@CountryCode");
		visaDetailsJson.put(JSON_PROP_COUNTRYCODE, countryCode);
		
		Element cntryDtlsElem = XMLUtils.getFirstElementAtXPath(visaSearchRSElem, "./ivans:CountryDetails");
		if(cntryDtlsElem!=null)
		{
			visaDetailsJson.put(JSON_PROP_COUNTRYDETAILS, getCntryDtlsJson(cntryDtlsElem));
		}
		
		Element[] dipRprOfficeElems = XMLUtils.getElementsAtXPath(visaSearchRSElem, "./ivans:DiplomaticRepresentation/ivans:Offices/ivans:Office");
		if(dipRprOfficeElems!=null && dipRprOfficeElems.length!=0)
		{
			visaDetailsJson.put(JSON_PROP_DIPLOMATICREPRESENTATIONOFFICES, getOfficeDtls(dipRprOfficeElems));
		}
		
		Element[] indEmbOfficeElems = XMLUtils.getElementsAtXPath(visaSearchRSElem, "./ivans:IndianEmbassy/ivans:Office");
		if(indEmbOfficeElems!=null && indEmbOfficeElems.length!=0)
		{
			visaDetailsJson.put(JSON_PROP_INDIANEMBASSYOFFICES, getOfficeDtls(indEmbOfficeElems));
		}
		
		Element[] helpAddressElems = XMLUtils.getElementsAtXPath(visaSearchRSElem, "./ivans:IntlHelpAddress/ivans:HelpAddress");
		if(helpAddressElems!=null && helpAddressElems.length!=0)
		{
			visaDetailsJson.put(JSON_PROP_INTLHELPADDRESSES, getHlpAddressJsonArr(indEmbOfficeElems));
		}
		
		//Better believe it!
		visaDetailsJson.put(JSON_PROP_IVSADVISORYDESC, XMLTransformer.toString(XMLUtils.getFirstElementAtXPath(visaSearchRSElem, "./ivans:IVSAdvisory/ivans:Description/ivans:Heading")));
		visaDetailsJson.put(JSON_PROP_RECIPROCALVISAINFODESC, XMLTransformer.toString(XMLUtils.getFirstElementAtXPath(visaSearchRSElem, "./ivans:ReciprocalVisaInfo/ivans:Description/ivans:ReciprocalVisaInfo")));
		visaDetailsJson.put(JSON_PROP_INTERNATIONALADVISORYDESC, XMLTransformer.toString(XMLUtils.getFirstElementAtXPath(visaSearchRSElem, "./ivans:InternationalAdvisory/ivans:Description/ivans:InternationalAdvisory")));
		
		Element[] saarcofficeElems = XMLUtils.getElementsAtXPath(visaSearchRSElem, "./ivans:SAARCInfo/ivans:CountryOffices/ivans:CountryOffic");
		if(saarcofficeElems!=null && saarcofficeElems.length!=0)
		{
			visaDetailsJson.put(JSON_PROP_SAARCINFOOFFICES, getOfficeDtls(saarcofficeElems));
		}
		
		Element[] visaInfoElems = XMLUtils.getElementsAtXPath(visaSearchRSElem, "./ivans:VisaInformation");
		if(visaInfoElems!=null && visaInfoElems.length!=0)
		{
			visaDetailsJson.put(JSON_PROP_VISAINFORMATION, getVisaInfoJsonArr(visaInfoElems));
		}
		
		visaOptionsJsonArr.put(visaDetailsJson);
	}
	
	public static JSONArray getVisaInfoJsonArr(Element[] visaInfoElems)
	{
		JSONArray visaInfoJsonArr = new JSONArray();
		
		for(Element visaInfoElem : visaInfoElems)
		{
			JSONObject visaInfoJson = new JSONObject();
			
			visaInfoJson.put("territoryCity", XMLUtils.getValueAtXPath(visaInfoElem, "./ivans:TerritoryCity"));
			
			Element[] categoryElems = XMLUtils.getElementsAtXPath(visaInfoElem, "./ivans:Categories/ivans:Category");
			if(categoryElems!=null && categoryElems.length!=0)
			{
				visaInfoJson.put("categories", getVisaCategories(categoryElems));
			}
			
			Element[] categoryFeesElems = XMLUtils.getElementsAtXPath(visaInfoElem, "./ivans:CategoryFees");
			if(categoryFeesElems!=null && categoryFeesElems.length!=0)
			{
				visaInfoJson.put("categoryFees", getCategoryFeesJsonArr(categoryFeesElems));
			}
			
			Element[] categoryFormElems = XMLUtils.getElementsAtXPath(visaInfoElem, "./ivans:CategoryForms/ivans:CategoryForm");
			if(categoryFormElems!=null && categoryFormElems.length!=0)
			{
				visaInfoJson.put("categoryForms", getCategoryFormsJsonArr(categoryFormElems));
			}
			
			visaInfoJsonArr.put(visaInfoJson);
		}
		return visaInfoJsonArr;
	}
	
	public static JSONArray getCategoryFormsJsonArr(Element[] categoryFeesElems)
	{
		JSONArray catFormJsonArr = new JSONArray();
		
		for(Element categoryFeesElem : categoryFeesElems)
		{
			JSONObject catFormJson = new JSONObject();
			
			catFormJson.put("formName", XMLUtils.getValueAtXPath(categoryFeesElem, "./ivans:Form"));
			catFormJson.put("formPath", XMLUtils.getValueAtXPath(categoryFeesElem, "./ivans:FormPath"));
			catFormJson.put("formCode", XMLUtils.getValueAtXPath(categoryFeesElem, "./ivans:CategoryCode"));
			
			catFormJsonArr.put(catFormJson);
		}
		
		return catFormJsonArr;
	}
	
	public static JSONArray getCategoryFeesJsonArr(Element[] categoryFeesElems)
	{
		JSONArray catFeesJsonArr = new JSONArray();
		
		for(Element categoryFeesElem : categoryFeesElems)//inside this there is categoryfee elements that repeat
		{
			Element[] catfeeElems =	XMLUtils.getElementsAtXPath(categoryFeesElem, "./ivans:CategoryFee");
			
			for(Element catfeeElem : catfeeElems)
			{
				JSONObject catfeeJson = new JSONObject();
				
				catfeeJson.put("feeCode", XMLUtils.getValueAtXPath(catfeeElem, "./ivans:CategoryCode"));
				catfeeJson.put("feeName", XMLUtils.getValueAtXPath(catfeeElem, "./ivans:Category"));
				catfeeJson.put("fees", XMLUtils.getValueAtXPath(catfeeElem, "./ivans:CategoryFeeAmountINR"));
				
				catFeesJsonArr.put(catfeeJson);
			}
		}
		
		return catFeesJsonArr;
	}
	
	public static JSONArray getVisaCategories(Element[] categoryElems)
	{
		JSONArray visaCategJsonArr = new JSONArray();
		
		for(Element categoryElem : categoryElems)
		{
			JSONObject visaCategJson = new JSONObject();
			
			visaCategJson.put("categoryName", XMLUtils.getValueAtXPath(categoryElem, "./ivans:Category"));
			visaCategJson.put("categoryCode", XMLUtils.getValueAtXPath(categoryElem, "./ivans:CategoryCode"));
			visaCategJson.put("categoryInfo", XMLTransformer.toString(XMLUtils.getFirstElementAtXPath(categoryElem, "./ivans:CategoryInfo")));
			visaCategJson.put("CategoryNotes", XMLTransformer.toString(XMLUtils.getFirstElementAtXPath(categoryElem, "./ivans:CategoryNotes")));
			
			visaCategJsonArr.put(visaCategJson);
		}
		
		return visaCategJsonArr;
	}
	
	public static JSONArray getHlpAddressJsonArr(Element[] indEmbOfficeElems)
	{
		JSONArray hlpAddrsJsonArr = new JSONArray();
		
		for(Element indEmbOfficeElem : indEmbOfficeElems)
		{
			JSONObject hlpAddrsJson = new JSONObject();
			
			hlpAddrsJson.put("name",XMLUtils.getValueAtXPath(indEmbOfficeElem, "./ivans:Name"));
			hlpAddrsJson.put("website",XMLUtils.getValueAtXPath(indEmbOfficeElem, "./ivans:Website"));
		}
		return hlpAddrsJsonArr;
	}
	
	public static JSONArray getOfficeDtls(Element[] dipRprOfficeElems)
	{
		JSONArray officeJsonArr = new JSONArray();
		
		for(Element dipRprOfficeElem : dipRprOfficeElems)//make the code such that it will handle all the three office tags appropriately eg.check for null
		{
			JSONObject officeJson = new JSONObject();
			
			String address = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:Address");
			if(address!=null && !address.isEmpty())
				officeJson.put("address", XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:Address"));
			
			String city = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:City");
			if(city!=null && !city.isEmpty())
				officeJson.put("city", city);
			
			String collectionTimings = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:CollectionTimings");
			if(collectionTimings!=null && !collectionTimings.isEmpty())
				officeJson.put("collectionTimings", collectionTimings);
			
			String country = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:Country");
			if(country!=null && !country.isEmpty())
				officeJson.put("country", country);
			
			String email = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:Email");
			if(email!=null && !email.isEmpty())
				officeJson.put("email", email);
			
			String fax = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:Fax");
			if(fax!=null && !fax.isEmpty())
				officeJson.put("fax", fax);
			
			String name = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:Name");
			if(name!=null && !name.isEmpty())
				officeJson.put("name", name);
			
			String phone = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:Phone");
			if(phone!=null && !phone.isEmpty())
				officeJson.put("phone", phone);
			
			String pincode = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:PinCode");
			if(pincode!=null && !pincode.isEmpty())
				officeJson.put("pincode", pincode);
			
			String publicTimings = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:PublicTimings");
			if(publicTimings!=null && !publicTimings.isEmpty())
				officeJson.put("publicTimings", publicTimings);
			
			String telephone = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:Telephone");
			if(telephone!=null && !telephone.isEmpty())
				officeJson.put("telephone", telephone);
			
			String timings = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:Timings");
			if(timings!=null && !timings.isEmpty())
				officeJson.put("timings", timings);
			
			String visaTimings = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:VisaTimings");
			if(visaTimings!=null && !visaTimings.isEmpty())
				officeJson.put("visaTimings", visaTimings);
			
			String website = XMLUtils.getValueAtXPath(dipRprOfficeElem, "./ivans:Website");
			if(website!=null && !website.isEmpty())
				officeJson.put("website", website);
			
			officeJsonArr.put(officeJson);
		}
		return officeJsonArr;
	}
	
	public static JSONObject getCntryDtlsJson(Element cntryDtlsElem)
	{
		JSONObject cntryDtlsJson = new JSONObject();
		
		cntryDtlsJson.put("countryName", XMLUtils.getValueAtXPath(cntryDtlsElem, "./ivans:CountryName/ivans:Name"));
		
		Element gnrlInfoElem = XMLUtils.getFirstElementAtXPath(cntryDtlsElem, "./ivans:GeneralInfo");
		if(gnrlInfoElem!=null)
		{
			cntryDtlsJson.put("generalInfo", getGnrlInfoJson(gnrlInfoElem));
		}
		
		Element[] holidayElems = XMLUtils.getElementsAtXPath(cntryDtlsElem, "./ivans:Holidays/ivans:Holiday");
		if(holidayElems!=null && holidayElems.length!=0)
		{
			cntryDtlsJson.put("holidays", getHolidaysJsonArr(holidayElems));
		}
		
		Element[] airlineElems = XMLUtils.getElementsAtXPath(cntryDtlsElem, "./ivans:Airlines/ivans:Airline");
		if(airlineElems!=null && airlineElems.length!=0)
		{
			cntryDtlsJson.put("airlines", getAirlinesJsonArr(airlineElems));
		}
		
		Element[] airportElems = XMLUtils.getElementsAtXPath(cntryDtlsElem, "./ivans:Airports/ivans:Airport");
		if(airportElems!=null && airportElems.length!=0)
		{
			cntryDtlsJson.put("airports", getAirportsJsonArr(airportElems));
		}
		
		return cntryDtlsJson;
	}
	
	public static JSONArray getAirportsJsonArr(Element[] airportElems)
	{
		JSONArray airportJsonArr = new JSONArray(); 
				
		for(Element airportElem : airportElems)
		{
			JSONObject airportJson = new JSONObject();
			
			airportJson.put("name", XMLUtils.getValueAtXPath(airportElem, "./ivans:Name"));
			airportJson.put("code", XMLUtils.getValueAtXPath(airportElem, "./ivans:Code"));
			airportJson.put("type", XMLUtils.getValueAtXPath(airportElem, "./ivans:Type"));
			
			airportJsonArr.put(airportJson);
		}
		return airportJsonArr;
	}
	
	public static JSONArray getAirlinesJsonArr(Element[] airlineElems)
	{
		JSONArray airlinesJsonArr = new JSONArray();
		
		for(Element airlineElem : airlineElems)
		{
			JSONObject airlineJson = new JSONObject();
			
			airlineJson.put("code", XMLUtils.getValueAtXPath(airlineElem, "./ivans:Code"));
			airlineJson.put("name", XMLUtils.getValueAtXPath(airlineElem, "./ivans:Name"));
			
			airlinesJsonArr.put(airlineJson);
		}
		
		return airlinesJsonArr;
	}
	
	public static JSONArray getHolidaysJsonArr(Element[] holidayElems)
	{
		JSONArray holidayElemJsonArr = new JSONArray();
		
		for(Element holidayElem : holidayElems)
		{
			JSONObject holidayJson = new JSONObject(); 
			
			holidayJson.put("holidayName", XMLUtils.getValueAtXPath(holidayElem, "./ivans:HolidayName/ivans:p"));
			holidayJson.put("year", XMLUtils.getValueAtXPath(holidayElem, "./ivans:Year"));
			
			holidayElemJsonArr.put(holidayJson);
		}
		return holidayElemJsonArr;
	}
	
	public static JSONObject getGnrlInfoJson(Element gnrlInfoElem)
	{
		JSONObject gnrlInfoJson = new JSONObject();
		
		gnrlInfoJson.put("area",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:Area"));
		gnrlInfoJson.put("capital",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:Capital"));
		gnrlInfoJson.put("climate",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:Climate"));
		gnrlInfoJson.put("currency",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:Currency"));
		gnrlInfoJson.put("flag",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:Flag"));
		gnrlInfoJson.put("code",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:Code"));
		gnrlInfoJson.put("languages",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:Languages"));
		gnrlInfoJson.put("largeMap",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:LargeMap"));
		gnrlInfoJson.put("location",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:Location"));
		gnrlInfoJson.put("nationalDay",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:NationalDay"));
		gnrlInfoJson.put("population",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:Population"));
		gnrlInfoJson.put("smallMap",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:SmallMap"));
		gnrlInfoJson.put("time",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:Time"));
		gnrlInfoJson.put("worldFactBook",XMLUtils.getValueAtXPath(gnrlInfoElem, "./ivans:WorldFactBook"));
		
		return gnrlInfoJson;
	}
	
	public static void createPOS(Document ownerDoc, Element otaCategoryAvail) {
		Element Source = XMLUtils.getFirstElementAtXPath(otaCategoryAvail, "./ota:POS/ota:Source");
		Source.setTextContent("Test");
		
	}
	
	protected static void createHeader(Element reqElem,JSONObject reqHdrJson) {
		
		Element sessionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./visai:RequestHeader/com:SessionID");
		sessionElem.setTextContent(reqHdrJson.getString(JSON_PROP_SESSIONID));

		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./visai:RequestHeader/com:TransactionID");
		transactionElem.setTextContent(reqHdrJson.getString(JSON_PROP_TRANSACTID));

		Element userElem = XMLUtils.getFirstElementAtXPath(reqElem, "./visai:RequestHeader/com:UserID");
		userElem.setTextContent(reqHdrJson.getString(JSON_PROP_USERID));
	}
	
}
