package com.coxandkings.travel.bookingengine.orchestrator.transfers;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.transfers.TransfersConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.common.CommercialCacheProcessor;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class TransfersSearchProcessor implements TransfersConstants {

	private static final Logger logger = LogManager.getLogger(TransfersSearchProcessor.class);
	static final String OPERATION_NAME = "search";
	private static String mRcvsPriceCompQualifier = JSON_PROP_RECEIVABLES.concat(SIGMA).concat(".")
			.concat(JSON_PROP_RECEIVABLE).concat(".");

	public static void getSupplierResponseGroundServiceJSON(Element resBodyElem, JSONArray groundServiceJsonArr,
			JSONObject reqBodyJson) throws Exception {
		
		getSupplierResponseGroundServiceJSON(reqBodyJson, resBodyElem, groundServiceJsonArr, false, 0);
	}

	public static void getSupplierResponseGroundServiceJSON(JSONObject reqBodyJson, Element resBodyElem,
			JSONArray groundServicesJsonArr, boolean generateBookRefIdx, int bookRefIdx) throws Exception {
		// boolean isCombinedReturnJourney =
		// Boolean.valueOf(XMLUtils.getValueAtXPath(resBodyElem,
		// "./@CombinedReturnJourney"));
		
		//Map<Integer,Map<Integer,Integer>> vehicleMap = null;
		
	
		Element[] groundServiceElems = XMLUtils.getElementsAtXPath(resBodyElem,
				"./ota:GroundServices/ota:GroundService");
		
	
		/*Element groundServicesElem=XMLUtils.getFirstElementAtXPath(resBodyElem,
				"./ota:GroundServices");
		*/
		for (Element groundServiceElem : groundServiceElems) {

	/*	Element[] vehicleTypelem = XMLUtils.getElementsAtXPath(groundServiceElem, "./ota:Service/ota:VehicleType");
			
			 for(Element vehicleService : vehicleTypelem) {
				 
				 vehicleMap.get(vehicleService);*/
			/*Element cloneGroundServiceElem = (Element) groundServiceElem.cloneNode(true);
			
			Element[] cloneTotalAmtElem = XMLUtils.getElementsAtXPath(cloneGroundServiceElem, "./ota:TotalCharge");
			Element cloneServiceElem=XMLUtils.getFirstElementAtXPath(cloneGroundServiceElem, "./ota:Service");
			Element[] cloneVehicleTypeElem = XMLUtils.getElementsAtXPath(cloneGroundServiceElem, "./ota:Service/ota:VehicleType");
			int counter=0;
			for(Element totalAmtClone : cloneTotalAmtElem) {
				
				cloneGroundServiceElem.removeChild(totalAmtClone);
				cloneServiceElem.removeChild(cloneVehicleTypeElem[counter]);
				//Element cloneVehicleType=XMLUtils.getFirstElementAtXPath(cloneGroundServiceElem, String.format("./ota:Service/ota:VehicleType/@UniqueID=/s",totalAmtClone.getAttribute("Type") ));
				counter++;
			
			}
			System.out.println("cloneGroundServiceElem->"+XMLTransformer.toString(cloneGroundServiceElem));
			for(Element totalAmtClone : cloneTotalAmtElem) {
				cloneGroundServiceElem = (Element) groundServiceElem.cloneNode(true);
				cloneGroundServiceElem.appendChild(totalAmtClone);
			//System.out.println("cloneGroundServiceElem1->"+XMLTransformer.toString(cloneGroundServiceElem));
			cloneServiceElem.appendChild(XMLUtils.getFirstElementAtXPath(cloneGroundServiceElem, String.format("./ota:Service/ota:VehicleType/@UniqueID=",totalAmtClone.getAttribute("Type") )));
				groundServicesElem.appendChild(cloneGroundServiceElem);
			}*/
		
			Element[] TotalAmtElem = XMLUtils.getElementsAtXPath(groundServiceElem, "./ota:TotalCharge");
			int i=0;
			 for(Element totalAmt : TotalAmtElem) {
			//Map<Integer,Integer> vehicleAmtMap = new HashMap<>();
				 
				/* String uniqueId = XMLUtils.getValueAtXPath(vehicleService, "./@UniqueID");
				  
				 Element[] totalChargeElem = XMLUtils.getElementsAtXPath( groundServiceElem , "./ota:TotalCharge");
				 
				 for(Element totalCharge  : totalChargeElem) {
				 
					 String type = XMLUtils.getValueAtXPath(totalCharge, "./@Type");
					 if(uniqueId.equals(type)) {
						 
					 }*/
						 
			
			JSONObject groundServiceJson = getGroundServiceJSON(groundServiceElem, reqBodyJson ,totalAmt,  i++);
			String suppId = XMLUtils.getValueAtXPath((Element) resBodyElem.getParentNode(), "./tran1:SupplierID");
		/*	try {
				groundServiceJson.put(JSON_PROP_SUPPREF, suppId.split("_")[1] != null ? suppId.split("_")[1] : suppId);
			} catch (Exception e) {
				groundServiceJson.put(JSON_PROP_SUPPREF, suppId);
			}*/
			groundServiceJson.put(JSON_PROP_SUPPREF, suppId);
			groundServicesJsonArr.put(groundServiceJson);
			 }
			// groundServicesElem.removeChild(groundServiceElem);
		}
		//resBodyElem.removeChild(groundServicesElem);
		
	}

	private static JSONObject getGroundServiceJSON(Element groundServiceElem, JSONObject reqBodyJson, Element totalAmt ,  int i) {
		//Map<Integer,Integer> vehicleAmtMap = null; /*= new HashMap<>();*/
		JSONObject groundServiceJson = new JSONObject();

		JSONArray serviceArr = new JSONArray();
		Element[] serviceElems = XMLUtils.getElementsAtXPath(groundServiceElem, "./ota:Service");
		for (Element serviceElem : serviceElems) {
			
			serviceArr.put(getServiceJson(serviceElem,reqBodyJson,totalAmt,i));

		}
		groundServiceJson.put(JSON_PROP_SERVICE, serviceArr);

		Element transferInformationElems = XMLUtils.getFirstElementAtXPath(groundServiceElem,
				"./ota:TransferInformation");
		groundServiceJson.put(JSON_PROP_TRANSFERINFORMATION, getTransferInformation(transferInformationElems));

		Element restrictionsElem = XMLUtils.getFirstElementAtXPath(groundServiceElem, "./ota:Restrictions");
		groundServiceJson.put(JSON_PROP_RESTRICTIONS, getRestrictionsJSON(restrictionsElem));

		
		groundServiceJson.put(JSON_PROP_TOTALCHARGE, getTotalChargeJSON(totalAmt));
		
		//String uniqueId = XMLUtils.getValueAtXPath(vehicleService, "./@UniqueID");
		  
		 //Element[] totalChargeElem = XMLUtils.getElementsAtXPath( groundServiceElem , "./ota:TotalCharge");
		 
		// for(Element totalCharge  : totalAmt) {
		 
			// String type = XMLUtils.getValueAtXPath(totalCharge, "./@Type");
			/* if(uniqueId.equals(type)) {*/
		
		//Element totalChargeElem = XMLUtils.getFirstElementAtXPath(groundServiceElem, "./ota:TotalCharge");
	
		
			 //}
		 
	/*	Element[] vehicleTotalChargeElem = XMLUtils.getElementsAtXPath(groundServiceElem, "./ota:TotalCharge");
		for(Element totalAmount : vehicleTotalChargeElem) {
			
		
			vehicleAmtMap.get(totalAmount);
			groundServiceElem.put(JSON_PROP_TOTALCHARGE , getTotalChargeJSON(vehicleAmtMap));
			
			
		}
*/
		Element referenceElem[] = XMLUtils.getElementsAtXPath(groundServiceElem, "./ota:Reference");
		groundServiceJson.put(JSON_PROP_REFERENCE1, getReferenceJSON(referenceElem));

		Element timelinesElem = XMLUtils.getFirstElementAtXPath(groundServiceElem, "./ota:Timelines");
		groundServiceJson.put(JSON_PROP_TIMELINES, getTimelinesJSON(timelinesElem));

		Element feesElem = XMLUtils.getFirstElementAtXPath(groundServiceElem, "./ota:Fees");
		groundServiceJson.put(JSON_PROP_FEES, getFeesJSON(feesElem));
		return groundServiceJson;
	
	}

	private static JSONObject getFeesJSON(Element feesElem) {
		JSONObject feesJson = new JSONObject();
		feesJson.put(JSON_PROP_DESCRIPTION, XMLUtils.getValueAtXPath(feesElem, "./@Description"));
		return feesJson;
	}

	private static JSONObject getTransferInformation(Element transferInformationElem) {
		JSONObject transferInfoJson = new JSONObject();

		JSONArray imageListArr = new JSONArray();
		Element[] imageListElems = XMLUtils.getElementsAtXPath(transferInformationElem, "./ota:ImageList");
		for (Element imageListElem : imageListElems) {
			imageListArr.put(getImageListJson(imageListElem));
		}
		transferInfoJson.put(JSON_PROP_IMAGELIST, imageListArr);

		JSONArray descriptionArr = new JSONArray();
		Element[] descriptionElems = XMLUtils.getElementsAtXPath(transferInformationElem, "./ota:Description");
		for (Element descriptionElem : descriptionElems) {
			descriptionArr.put(getDescriptionJson(descriptionElem));
		}
		transferInfoJson.put(JSON_PROP_DESCRIPTION, descriptionArr);

		JSONArray guidelinesArr = new JSONArray();
		Element[] guidelinesElems = XMLUtils.getElementsAtXPath(transferInformationElem, "./ota:Guidelines");
		for (Element guidelinesElem : guidelinesElems) {
			guidelinesArr.put(getGuidelinesJson(guidelinesElem));
		}
		transferInfoJson.put(JSON_PROP_GUIDELINES, guidelinesArr);

		return transferInfoJson;
	}

	private static JSONObject getGuidelinesJson(Element guidelinesElem) {
		JSONObject guidelinesJson = new JSONObject();
		guidelinesJson.put(JSON_PROP_ID, XMLUtils.getValueAtXPath(guidelinesElem, "./@id"));
		guidelinesJson.put(JSON_PROP_DESCRIPTION, XMLUtils.getValueAtXPath(guidelinesElem, "./@description"));
		guidelinesJson.put(JSON_PROP_DETAILDESCRIPTION, XMLUtils.getValueAtXPath(guidelinesElem, "./@detailedDescription"));
		return guidelinesJson;
	}

	private static JSONObject getDescriptionJson(Element descriptionElem) {
		JSONObject descriptionJson = new JSONObject();
		descriptionJson.put(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(descriptionElem, "./@type"));
		descriptionJson.put(JSON_PROP_LANGCODE, XMLUtils.getValueAtXPath(descriptionElem, "./@languagecode"));
		descriptionJson.put(JSON_PROP_TEXT, XMLUtils.getValueAtXPath(descriptionElem, "./@text"));
		return descriptionJson;
	}

	private static JSONObject getImageListJson(Element imageListElem) {
		JSONObject imageListJson = new JSONObject();
		imageListJson.put(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(imageListElem, "./@type"));
		imageListJson.put(JSON_PROP_URL, XMLUtils.getValueAtXPath(imageListElem, "./@URL"));
		return imageListJson;
	}

	private static JSONObject getServiceJson(Element serviceElem, JSONObject reqBodyJson, Element totalAmt ,  int i) {
		
		//sJSONObject serviceObj = reqBodyJson.opt
		JSONObject serviceJson = new JSONObject();
		Element locationElem = XMLUtils.getFirstElementAtXPath(serviceElem, "./ota:Location");
		serviceJson.put(JSON_PROP_SERVICETYPE, XMLUtils.getValueAtXPath(locationElem, "./@ServiceType"));

		Element pickupElem = XMLUtils.getFirstElementAtXPath(locationElem, "./ota:Pickup");
		serviceJson.put(JSON_PROP_PICKLOCCODE, XMLUtils.getValueAtXPath(pickupElem, "./@LocationCode"));
		
		
		
		
		Element dropoffElem = XMLUtils.getFirstElementAtXPath(locationElem, "./ota:Dropoff");
		serviceJson.put(JSON_PROP_DROPLOCCODE, XMLUtils.getValueAtXPath(dropoffElem, "./@LocationCode"));
		
		//TODO:Also Add vehicle Type Name

		Element vehicleTypeElem = XMLUtils.getElementsAtXPath(serviceElem, "./ota:VehicleType")[i];
		serviceJson.put(JSON_PROP_DESCRIPTION, XMLUtils.getValueAtXPath(vehicleTypeElem, "./@Description"));
		serviceJson.put(JSON_PROP_MAXPASS, XMLUtils.getValueAtXPath(vehicleTypeElem, "./@MaximumPassengers"));
		serviceJson.put(JSON_PROP_MAXBAG, XMLUtils.getValueAtXPath(vehicleTypeElem, "./@MaximumBaggage"));
		serviceJson.put(JSON_PROP_UNIQUEID, XMLUtils.getValueAtXPath(vehicleTypeElem, "./@UniqueID"));
		

		Element transferInformationElem = XMLUtils.getFirstElementAtXPath(serviceElem, "./ota:TransferInformation");
		//need check for vehicle type and text type
		Element descriptionElem = XMLUtils.getFirstElementAtXPath(transferInformationElem, "./ota:Description");
	
			//String uniqueId = XMLUtils.getValueAtXPath(vehicleService, "./@UniqueID");
			//String descriptionType = XMLUtils.getValueAtXPath(descElem, "./@type");
			//if(uniqueId.equals(descriptionType)) {
		//Element descriptionElem = XMLUtils.getFirstElementAtXPath(transferInformationElem, "./ota:Description");
		serviceJson.put(JSON_PROP_TEXT, XMLUtils.getValueAtXPath(descriptionElem, "./@text"));
		serviceJson.put(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(descriptionElem, "./@type"));
		
		//}
		//ProductSpecifications
	/*	Element[] productSpecifications = XMLUtils.getElementsAtXPath(transferInformationElem, "./ota:ProductSpecifications");
		for(Element productSpec : productSpecifications) {
			String uniqueId = XMLUtils.getValueAtXPath(vehicleService, "./@UniqueID");
			String productSpecType = XMLUtils.getValueAtXPath(productSpec, "./@type");
			
			if(uniqueId.equals(productSpecType)) {
				serviceJson.put("AvailabilityStatus", XMLUtils.getValueAtXPath(productSpec, "./@AvailabilityStatus"));
			}
		}*/

		return serviceJson;
	}

	private static JSONObject getTimelinesJSON(Element timelinesElem) {
		JSONObject timelinesJson = new JSONObject();
		timelinesJson.put(JSON_PROP_TIME, XMLUtils.getValueAtXPath(timelinesElem, "./@Time"));
		timelinesJson.put(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(timelinesElem, "./@type"));
		return timelinesJson;
	}

	private static JSONArray getReferenceJSON(Element[] referenceElem) {
		JSONArray referenceArr = new JSONArray();

		for (Element refer : referenceElem) {
			JSONObject referenceJson = new JSONObject();
			if (XMLUtils.getValueAtXPath(refer, "./@Type").equals("16")) {
				referenceJson.put(JSON_PROP_SUPPREFNO, XMLUtils.getValueAtXPath(refer, "./@ID"));
				referenceJson.put(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(refer, "./@Type"));
				referenceJson.put(JSON_PROP_ID_CONTEXT, XMLUtils.getValueAtXPath(refer, "./@ID_Context"));
				referenceArr.put(referenceJson);
			}
				/*referenceJson.put("id_Context", XMLUtils.getValueAtXPath(refer, "./@ID_Context"));
				referenceJson.put("type", XMLUtils.getValueAtXPath(refer, "./@Type"));*/

		}
		return referenceArr;
	}

	private static JSONObject getTotalChargeJSON(Element totalAmt) {
		JSONObject totalChargeJson = new JSONObject();
		totalChargeJson.put(JSON_PROP_CURRENCYCODE, XMLUtils.getValueAtXPath(totalAmt, "./@CurrencyCode"));
		totalChargeJson.put(JSON_PROP_AMOUNT,
				Utils.convertToInt(XMLUtils.getValueAtXPath(totalAmt, "./@EstimatedTotalAmount"), 0));

		return totalChargeJson;
	}

	private static JSONObject getRestrictionsJSON(Element restrictionsElem) {
		JSONObject restrictionsJson = new JSONObject();
		restrictionsJson.put(JSON_PROP_CANCELPENTCHRG,
				XMLUtils.getValueAtXPath(restrictionsElem, "./@CancellationPenaltyInd"));
		return restrictionsJson;
	}

	public static String process(JSONObject reqJson) throws InternalProcessingException, RequestProcessingException {
		try {

			// Calling commercial cache
			// TODO: currently hardcoding. Discuss it with Sir and find out where to get it

			String commCacheRes = CommercialCacheProcessor.getFromCache(PRODUCT_TRANSFER, "B2CIndiaEng",
					new JSONArray("[{\"supplierID\": \"ACAMPORA\"},{\"credentialName\": \"\"}]"), reqJson);
			if (commCacheRes != null && !(commCacheRes.equals("error")))
				return commCacheRes;

			JSONObject reqHdrJson = (reqJson.optJSONObject(JSON_PROP_REQHEADER) != null)
					? reqJson.optJSONObject(JSON_PROP_REQHEADER)
					: new JSONObject();
			JSONObject reqBodyJson = (reqJson.optJSONObject(JSON_PROP_REQBODY) != null)
					? reqJson.optJSONObject(JSON_PROP_REQBODY)
					: new JSONObject();

			//OperationConfig opConfig = TransfersConfig.getOperationConfig("search");
			ServiceConfig opConfig = TransfersConfig.getOperationConfig("search");
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);

			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT,
					PROD_CATEG_SUBTYPE_TRANSFER);
			createHeader(reqHdrJson, reqElem);

			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./tran1:RequestHeader/com:SupplierCredentialsList");
			for (ProductSupplier prodSupplier : prodSuppliers) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}

			// Element passengersElem = null;
			setSuppReqOTAElem(reqBodyJson, reqElem, ownerDoc);

			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), TransfersConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			System.out.println(XMLTransformer.toString(resElem));
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
			JSONObject resBodyJson = new JSONObject();
			JSONArray groundServiceJsonArr = new JSONArray();
			Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem,
					"./tran:ResponseBody/tran1:OTA_GroundAvailRSWrapper");
			for (Element wrapperElem : wrapperElems) {
				Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_GroundAvailRS");
				getSupplierResponseGroundServiceJSON(resBodyElem, groundServiceJsonArr, reqBodyJson);
			}
			
			JSONArray groundServicesArr = resBodyJson.getJSONArray("groundServices");
			for(int i = 0 ; i < groundServiceJsonArr.length();i++) {
				JSONObject groundServicesObj = groundServiceJsonArr.optJSONObject(i);
				
				JSONArray resServiceArr = groundServicesObj.getJSONArray("service");
				
				for(int j = 0 ; j < resServiceArr.length() ; j++) {
					JSONObject resServiceObj = resServiceArr.optJSONObject(j);
					
					JSONObject reqServiceObj = reqBodyJson.optJSONArray("service").optJSONObject(j);
					
					resServiceObj.put(JSON_PROP_PICKCITY,reqServiceObj.optJSONObject("pickup").optJSONObject("address").optString("cityName"));
					resServiceObj.put(JSON_PROP_DROPCITY, reqServiceObj.optJSONObject("dropoff").optJSONObject("address").optString("cityName"));
				}
				
			}
			
			
			resBodyJson.put(JSON_PROP_GROUNDSERVICES, groundServiceJsonArr);
			;
			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));

			Map<String, Integer> suppResToBRIIndex = new HashMap<String, Integer>();
			JSONObject resSupplierJson = SupplierCommercials.getSupplierCommercialsV2(CommercialsOperation.Search, reqJson, resJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format(
						"A failure response was received from Supplier Commercials calculation engine: %s",
						resSupplierJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}
			JSONObject resClientJson = ClientCommercials.getClientCommercialsV2(resSupplierJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientJson.getString(JSON_PROP_TYPE))) {
				logger.error(
						String.format("A failure response was received from Client Commercials calculation engine: %s",
								resClientJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}
			// String tripInd = reqBodyJson.getString("tripIndicator");
			calculatePrices(reqJson, resJson, resSupplierJson, resClientJson, true, usrCtx);
			// Calculate company taxes
			TaxEngine.getCompanyTaxes(reqJson, resJson);
			pushSuppFaresToRedisAndRemove(resJson);
			// putting in cache
			CommercialCacheProcessor.putInCache(PRODUCT_TRANSFER, "B2CIndiaEng", resJson, reqJson);

			return resJson.toString();
		} catch (Exception x) {
			x.printStackTrace();
			return "{\"status\": \"ERROR\"}";
		}
	}

	public static void setSuppReqOTAElem(JSONObject reqBodyJson, Element reqElem, Document ownerDoc) {
		Element posElem = XMLUtils.getFirstElementAtXPath(reqElem, "./tran:RequestBody/ns:OTA_GroundAvailRQ/ns:POS");
		Element ground = (Element) posElem.getParentNode();
		Element sourceElem = XMLUtils.getFirstElementAtXPath(posElem, "./ns:Source");
		// TODO: hardcode for ISOCurrency!get it from where?
		sourceElem.setAttribute(JSON_PROP_ISOCURRENCY, "");

		// TODO: for loop
		JSONArray serviceArr = reqBodyJson.getJSONArray("service");
		int serviceArrLen = serviceArr.length();
		int i;
		for (i = 0; i < serviceArrLen; i++) {
			Element serviceElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Service");
			JSONObject serviceObj = serviceArr.getJSONObject(i);
			serviceElem.setAttribute(JSON_PROP_SERTYPE, serviceObj.getString(JSON_PROP_SERVICETYPE));
			Element pickupElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Pickup");
			JSONObject pickupObj = serviceObj.getJSONObject("pickup");
			pickupElem.setAttribute(JSON_PROP_DATETIME, pickupObj.getString("dateTime"));

			if (Utils.isStringNotNullAndNotEmpty(pickupObj.optString("locationType"))) {
				pickupElem.setAttribute(JSON_PROP_LOCATIONTYPE, pickupObj.getString("locationType"));
			}

			if (Utils.isStringNotNullAndNotEmpty(pickupObj.optString("locationCode"))) {
				pickupElem.setAttribute(JSON_PROP_LOCATIONCODE, pickupObj.getString("locationCode"));
			}

			if (Utils.isStringNotNullAndNotEmpty(pickupObj.optString("locationCode"))) {
				JSONObject addrObj = pickupObj.getJSONObject("address");
				Element addressElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Address");
				Element cityNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:CityName");
				cityNameElem.setTextContent(addrObj.getString("cityName"));
				addressElem.appendChild(cityNameElem);

				if (Utils.isStringNotNullAndNotEmpty(addrObj.optString("countryCode"))) {
					Element countryCodeElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:CountryCode");
					countryCodeElem.setTextContent(addrObj.getString("countryCode"));
					addressElem.appendChild(countryCodeElem);
				}

				if (Utils.isStringNotNullAndNotEmpty(addrObj.optString("locationName"))) {
					Element locationNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:LocationName");
					locationNameElem.setTextContent(addrObj.getString("locationName"));
					addressElem.appendChild(locationNameElem);
				}
				pickupElem.appendChild(addressElem);

			}
			serviceElem.appendChild(pickupElem);

			Element dropoffElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Dropoff");
			JSONObject dropoffObj = serviceObj.getJSONObject("dropoff");
			dropoffElem.setAttribute(JSON_PROP_DATETIME, dropoffObj.getString("dateTime"));

			if (Utils.isStringNotNullAndNotEmpty(dropoffObj.optString("locationType"))) {
				dropoffElem.setAttribute(JSON_PROP_LOCATIONTYPE, dropoffObj.getString("locationType"));
			}

			if (Utils.isStringNotNullAndNotEmpty(dropoffObj.optString("locationCode"))) {
				dropoffElem.setAttribute(JSON_PROP_LOCATIONCODE, dropoffObj.getString("locationCode"));
			}

			if (!dropoffObj.getJSONObject("address").equals("") && dropoffObj.getJSONObject("address") != null) {
				JSONObject addrObj = dropoffObj.getJSONObject("address");
				Element addressElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Address");
				Element cityNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:CityName");
				cityNameElem.setTextContent(addrObj.getString("cityName"));
				addressElem.appendChild(cityNameElem);

				if (Utils.isStringNotNullAndNotEmpty(addrObj.optString("countryCode"))) {
					Element countryCodeElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:CountryCode");
					countryCodeElem.setTextContent(addrObj.getString("countryCode"));
					addressElem.appendChild(countryCodeElem);
				}

				if (Utils.isStringNotNullAndNotEmpty(addrObj.optString("locationName"))) {
					Element locationNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:LocationName");
					locationNameElem.setTextContent(addrObj.getString("locationName"));
					addressElem.appendChild(locationNameElem);
				}
				
				dropoffElem.appendChild(addressElem);

			}
			serviceElem.appendChild(dropoffElem);
			// ground.insertBefore(serviceElem,passengersElem);
			ground.appendChild(serviceElem);

		}

		JSONArray paxInfoArr = reqBodyJson.getJSONArray("paxInfo");
		int paxInfoArrLen = paxInfoArr.length();
		int k;
		for (k = 0; k < paxInfoArrLen; k++) {
			JSONObject paxInfoObj = paxInfoArr.getJSONObject(k);
			Element passengersElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Passengers");
			passengersElem.setAttribute(XML_ATTR_QUANTITY, paxInfoObj.getString("quantity"));
			if (!paxInfoObj.getString("age").equals("") && paxInfoObj.getString("age") != null) {

				passengersElem.setAttribute(XML_PROP_AGE, paxInfoObj.getString("age"));
				ground.appendChild(passengersElem);
			}

		}

		if (null != reqBodyJson.optJSONArray("passengerPrefs")) {
			JSONArray passengerPrefsArr = reqBodyJson.getJSONArray("passengerPrefs");

			int passengerPrefsArrLen = passengerPrefsArr.length();

			int l;
			for (l = 0; l < passengerPrefsArrLen; l++) {
				JSONObject passengerPrefsObj = passengerPrefsArr.getJSONObject(l);
				if (null != passengerPrefsArr) {
					Element passengerPrefsElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:PassengerPrefs");
					passengerPrefsElem.setAttribute(XML_PROP_MAXBAG, passengerPrefsObj.getString("maximumBaggage"));

					Element languageElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Language");
					languageElem.setAttribute(JSON_PROP_LANG, passengerPrefsObj.getString("language"));

					passengerPrefsElem.appendChild(languageElem);
					ground.appendChild(passengerPrefsElem);
				}
			}
		}

		if (reqBodyJson.optString("tripIndicator").equalsIgnoreCase("share")) {
			Element vehiclePrefsElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:VehiclePrefs");

			Element typeElem = ownerDoc.createElementNS(Constants.NS_OTA, "ns:Type");
			typeElem.setTextContent(reqBodyJson.optString("vehicleType").toLowerCase());
			vehiclePrefsElem.appendChild(typeElem);
			ground.appendChild(vehiclePrefsElem);
		}
	}

	public static void pushSuppFaresToRedisAndRemove(JSONObject resJson) {
		JSONObject resHeaderJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);

		JSONArray groundServiceJsonArr = resBodyJson.optJSONArray(JSON_PROP_GROUNDSERVICES);

		Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();
		for (int i = 0; i < groundServiceJsonArr.length(); i++) {
			JSONObject groundServiceJson = groundServiceJsonArr.getJSONObject(i);
			JSONObject suppPriceInfoJson = groundServiceJson.optJSONObject(JSON_PROP_SUPPPRICEINFO);
			groundServiceJson.remove(JSON_PROP_SUPPPRICEINFO);

			JSONObject totalPricingInfo = groundServiceJson.optJSONObject(JSON_PROP_CLIENTCOMMTOTAL);
			groundServiceJson.remove(JSON_PROP_CLIENTCOMMTOTAL);

			JSONObject suppPriceBookInfoJson = new JSONObject();
			suppPriceBookInfoJson.put(JSON_PROP_SUPPPRICEINFO, suppPriceInfoJson);
			suppPriceBookInfoJson.put(JSON_PROP_CLIENTCOMMTOTAL, totalPricingInfo);
			reprcSuppFaresMap.put(getRedisKeyForGroundService(groundServiceJson), suppPriceBookInfoJson.toString());
		}

		String redisKey = resHeaderJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT_TRANSFER);
		try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
		redisConn.hmset(redisKey, reprcSuppFaresMap);
		redisConn.pexpire(redisKey, (long) (TransfersConfig.getRedisTTLMinutes() * 60 * 1000));
		}
		//RedisConfig.releaseRedisConnectionToPool(redisConn);
	}

	public static void pushCacheFaresToRedisAndRemove(JSONObject commCacheRes) {
		JSONObject resHeaderJson = commCacheRes.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = commCacheRes.getJSONObject(JSON_PROP_RESBODY);
		JSONObject supplierResponseJson = commCacheRes.getJSONObject("supplierResponse");

		JSONArray groundServiceJsonArr = supplierResponseJson.optJSONArray(JSON_PROP_GROUNDSERVICES);

		Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();
		for (int i = 0; i < groundServiceJsonArr.length(); i++) {
			JSONObject groundServiceJson = groundServiceJsonArr.getJSONObject(i);
			JSONObject suppPriceInfoJson = groundServiceJson.optJSONObject(JSON_PROP_SUPPPRICEINFO);
			groundServiceJson.remove(JSON_PROP_SUPPPRICEINFO);

			JSONObject totalPricingInfo = groundServiceJson.optJSONObject(JSON_PROP_CLIENTCOMMTOTAL);
			groundServiceJson.remove(JSON_PROP_CLIENTCOMMTOTAL);

			commCacheRes.remove("supplierResponse");

			JSONObject suppPriceBookInfoJson = new JSONObject();
			suppPriceBookInfoJson.put(JSON_PROP_SUPPPRICEINFO, suppPriceInfoJson);
			suppPriceBookInfoJson.put(JSON_PROP_CLIENTCOMMTOTAL, totalPricingInfo);
			JSONArray resGroundServiceJsonArr = resBodyJson.optJSONArray(JSON_PROP_GROUNDSERVICES);
			for (int j = 0; j < resGroundServiceJsonArr.length(); j++) {
				JSONObject resGroundServiceJson = resGroundServiceJsonArr.getJSONObject(i);
				reprcSuppFaresMap.put(getRedisKeyForGroundService(resGroundServiceJson),
						suppPriceBookInfoJson.toString());
			}
		}

		String redisKey = resHeaderJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT_TRANSFER);
		try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
		redisConn.hmset(redisKey, reprcSuppFaresMap);
		redisConn.pexpire(redisKey, (long) (TransfersConfig.getRedisTTLMinutes() * 60 * 1000));
		}
		//RedisConfig.releaseRedisConnectionToPool(redisConn);
	}

	public static String getRedisKeyForGroundService(JSONObject groundServiceJson) {
		List<String> keys = new ArrayList<>();

		keys.add(groundServiceJson.optString(JSON_PROP_SUPPREF));
		JSONArray serviceJsonArr = groundServiceJson.getJSONArray(JSON_PROP_SERVICE);
		for (int i = 0; i < serviceJsonArr.length(); i++) {
			//need to change key
			JSONObject serviceJson = serviceJsonArr.getJSONObject(i);
			//keys.add(serviceJson.optString(JSON_PROP_MAXPASS));
			//keys.add(serviceJson.optString(JSON_PROP_DESCRIPTION));
			if(serviceJson.optString(JSON_PROP_UNIQUEID).isEmpty()==false)
			{
			keys.add(serviceJson.optString(JSON_PROP_UNIQUEID));
			}
			JSONArray referenceArr = groundServiceJson.getJSONArray("reference");
			for(int j = 0 ; j < referenceArr.length() ; j++) {
				JSONObject referenceJson = referenceArr.getJSONObject(j);
			/*	if(referenceJson.getString(JSON_PROP_TYPE).equals("16")) {
				keys.add(referenceJson.optString(JSON_PROP_SUPPREFNO));
				}*/
				keys.add(referenceJson.optString(JSON_PROP_SUPPREFNO));
			}

		}

		String key = keys.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining("|"));
		return key;
	}

	public static void createHeader(JSONObject reqHdrJson, Element reqElem) {
		Element sessionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./tran1:RequestHeader/com:SessionID");
		sessionElem.setTextContent(reqHdrJson.getString(JSON_PROP_SESSIONID));

		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./tran1:RequestHeader/com:TransactionID");
		transactionElem.setTextContent(reqHdrJson.getString(JSON_PROP_TRANSACTID));

		Element userElem = XMLUtils.getFirstElementAtXPath(reqElem, "./tran1:RequestHeader/com:UserID");
		userElem.setTextContent(reqHdrJson.getString(JSON_PROP_USERID));
	}

	public static void calculatePrices(JSONObject reqJson, JSONObject resJson, JSONObject suppCommResJson,
			JSONObject clientCommResJson, boolean retainSuppFares, UserContext usrCtx) {

		Map<String, String> suppCommToTypeMap = getSupplierCommercialsAndTheirType(suppCommResJson);
		Map<String, String> clntCommToTypeMap = getClientCommercialsAndTheirType(clientCommResJson);

		String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT)
				.getString(JSON_PROP_CLIENTMARKET);
		String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT)
				.getString(JSON_PROP_CLIENTCCY);
		JSONArray resGroundServicesArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray("groundServices");

		JSONArray ccommSuppBRIJsonArr = clientCommResJson.getJSONObject("result").getJSONObject("execution-results")
				.getJSONArray("results").getJSONObject(0).getJSONObject("value")
				.getJSONObject("cnk.transfers_commercialscalculationengine.clienttransactionalrules.Root")
				.getJSONArray("businessRuleIntake");
		Map<String, JSONArray> ccommSuppBRIJsonMap = new HashMap<String, JSONArray>();
		for (int i = 0; i < ccommSuppBRIJsonArr.length(); i++) {
			JSONObject ccommSuppBRIJson = ccommSuppBRIJsonArr.getJSONObject(i);
			String suppID = ccommSuppBRIJson.getJSONObject("commonElements").getString("supplier");
			JSONArray ccommGroundServJsonArr = ccommSuppBRIJson.getJSONArray("transfersDetails");
			ccommSuppBRIJsonMap.put(suppID, ccommGroundServJsonArr);
		}
		Map<String, Integer> suppIndexMap = new HashMap<String, Integer>();
		JSONArray groundServiceArr = new JSONArray();
		for (int i = 0; i < resGroundServicesArr.length(); i++) {
			JSONObject resGroundServicesJson = resGroundServicesArr.getJSONObject(i);
			// Adding clientCommercialInfo
			JSONObject suppCommercialItinInfoJson = new JSONObject();
			JSONObject clientCommercialItinInfoJson = new JSONObject();
			JSONObject totalAmountSupp = resGroundServicesJson.getJSONObject(JSON_PROP_TOTALCHARGE);
			JSONObject totalChargeJson = new JSONObject();
			totalChargeJson.put(JSON_PROP_AMOUNT, totalAmountSupp.get(JSON_PROP_AMOUNT));
			totalChargeJson.put(JSON_PROP_CURRENCYCODE, totalAmountSupp.get(JSON_PROP_CURRENCYCODE));
			// clientCommercialItinInfoJson.put(JSON_PROP_SUPPTOTALFARE, totalChargeJson);
			// suppCommercialItinInfoArr.put(clientCommercialItinInfoJson);
			// JSONObject ccommJrnyDtlsJson = ccommJrnyDtlsJsonArr.getJSONObject(i);
/*
			String suppID = resGroundServicesJson.getString(JSON_PROP_SUPPREF).substring(0, 1).toUpperCase()
					+ resGroundServicesJson.getString(JSON_PROP_SUPPREF).substring(1).toLowerCase();*/
			// input.substring(0, 1).toUpperCase() + input.substring(1);
			String suppID = resGroundServicesJson.getString(JSON_PROP_SUPPREF);
			JSONArray ccommGroundServJsonArr = ccommSuppBRIJsonMap.get(suppID);

			if (ccommGroundServJsonArr == null) {
				// TODO: This should never happen. Log a information message here.
				logger.info(String.format(
						"BRMS/ClientComm: \"businessRuleIntake\" JSON element for supplier %s not found.", suppID));
				continue;
			}

			int idx = 0;
			if (suppIndexMap.containsKey(suppID)) {
				idx = suppIndexMap.get(suppID) + 1;
			}
			suppIndexMap.put(suppID, idx);
			JSONObject ccommGroundServJson = ccommGroundServJsonArr.getJSONObject(idx);

			BigDecimal totalFareAmt = new BigDecimal(0);

			// Search this paxType in client commercials
			// Even though the name is PassengerDetails, commericals are applied on vehicles
			PriceComponentsGroup totalFareCompsGroup = new PriceComponentsGroup(JSON_PROP_TOTALFARE, clientCcyCode,
					new BigDecimal(0), true);
			JSONObject ccommTransPsgrDtlJson = ccommGroundServJson.getJSONArray(JSON_PROP_PSGRDETAILS).getJSONObject(0);

			if (ccommTransPsgrDtlJson == null) {
				// TODO: Log a crying message here. Ideally this part of the code will never be
				// reached.
				continue;
			}

			// From the passenger type client commercial JSON, retrieve calculated client
			// commercial commercials
			JSONArray clientEntityCommJsonArr = ccommTransPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
			if (clientEntityCommJsonArr == null) {
				// TODO: Refine this warning message. Maybe log some context information also.
				logger.warn("Client commercials calculations not found");
				continue;
			}
			JSONObject markupCalcJson = null;
			JSONArray clientCommercials = new JSONArray();
			PriceComponentsGroup totalReceivablesCompsGroup = null;
			JSONObject totalCharge = resGroundServicesJson.getJSONObject(JSON_PROP_TOTALCHARGE);
			JSONObject suppTotalCharge = new JSONObject(totalCharge.toString());
			for (int k = (clientEntityCommJsonArr.length() - 1); k >= 0; k--) {
				JSONObject clientCommercial = new JSONObject();
				JSONArray clientEntityCommercialsJsonArr = new JSONArray();
				JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(k);
				// TODO: In case of B2B, do we need to add additional receivable commercials for
				// all client hierarchy levels?
				/* if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) { */
				// TODO: In case of B2B, do we need to add additional receivable commercials for
				// all client hierarchy levels?
				String suppCcyCode = suppTotalCharge.optString(JSON_PROP_CURRENCYCODE);
				if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) {
					markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);
					clientEntityCommercialsJsonArr
							.put(convertToClientEntityCommercialJson(markupCalcJson, clntCommToTypeMap, suppCcyCode));
				}

				JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
				JSONArray retentionCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_RETENTIONCOMMDETAILS);
				JSONArray fixedCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_FIXEDCOMMDETAILS);

				totalReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode,
						new BigDecimal(0), true);
				if (additionalCommsJsonArr != null) {
					for (int p = 0; p < additionalCommsJsonArr.length(); p++) {
						JSONObject additionalCommsJson = additionalCommsJsonArr.getJSONObject(p);
						String additionalCommName = additionalCommsJson.optString(JSON_PROP_COMMNAME);
						String additionalCommType = clntCommToTypeMap.get(additionalCommName);
						clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(additionalCommsJson,
								clntCommToTypeMap, suppCcyCode));

						if (COMM_TYPE_RECEIVABLE.equals(additionalCommType)) {
							String additionalCommCcy = additionalCommsJson.optString(JSON_PROP_COMMCCY);
							BigDecimal additionalCommAmt = additionalCommsJson.getBigDecimal(JSON_PROP_COMMAMOUNT)
									.multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode,
											clientMarket));
							totalFareCompsGroup.add(mRcvsPriceCompQualifier.concat(additionalCommName).concat("@")
									.concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt);
						}
					}
				}
				// Retention Commercials
				for (int p = 0; retentionCommsJsonArr != null && p < retentionCommsJsonArr.length(); p++) {
					JSONObject retentionCommsJson = retentionCommsJsonArr.getJSONObject(p);
					clientEntityCommercialsJsonArr.put(
							convertToClientEntityCommercialJson(retentionCommsJson, clntCommToTypeMap, suppCcyCode));
				}

				// Fixed Commercials
				for (int p = 0; fixedCommsJsonArr != null && p < fixedCommsJsonArr.length(); p++) {
					JSONObject fixedCommsJson = fixedCommsJsonArr.getJSONObject(p);
					clientEntityCommercialsJsonArr
							.put(convertToClientEntityCommercialJson(fixedCommsJson, clntCommToTypeMap, suppCcyCode));
				}
				BigDecimal totalFare = markupCalcJson.getBigDecimal(JSON_PROP_TOTALFARE);
				totalCharge.put(JSON_PROP_AMOUNT, totalFare);
				JSONObject totalFarecommJson = new JSONObject();
				totalFarecommJson.put(JSON_PROP_AMOUNT, totalFare);
				totalFarecommJson.put(JSON_PROP_CURRENCYCODE, totalCharge.get(JSON_PROP_CURRENCYCODE));
				// totalCharge.put(JSON_PROP_CURRENCYCODE, );

				// TODO: Should this be made conditional? Only in case when (retainSuppFares ==
				// true)
				// supplier commercial
				BigDecimal paxCount = new BigDecimal(1);
				JSONArray suppCommJsonArr = new JSONArray();
				JSONArray ccommSuppCommJsonArr = ccommTransPsgrDtlJson.optJSONArray(JSON_PROP_COMMDETAILS);
				if (ccommSuppCommJsonArr == null) {
					logger.warn(String.format("No supplier commercials found for supplier %s", suppID));
					return;
				}
				for (int x = 0; x < ccommSuppCommJsonArr.length(); x++) {
					JSONObject ccommSuppCommJson = ccommSuppCommJsonArr.getJSONObject(x);
					JSONObject suppCommJson = new JSONObject();
					suppCommJson.put(JSON_PROP_COMMNAME, ccommSuppCommJson.getString(JSON_PROP_COMMNAME));
					suppCommJson.put(JSON_PROP_COMMTYPE,
							suppCommToTypeMap.get(ccommSuppCommJson.getString(JSON_PROP_COMMNAME)));
					suppCommJson.put(JSON_PROP_COMMAMOUNT, ccommSuppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
					suppCommJson.put(JSON_PROP_COMMCCY, ccommSuppCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
					suppCommJsonArr.put(suppCommJson);
				}

				// TODO: ADD Calculation basefare + Receivable
				JSONObject suppTotalFareJson = new JSONObject(totalAmountSupp.toString());
				// JSONObject suppBaseFareJson =
				// suppTotalFareJson.getJSONObject(JSON_PROP_BASEFARE);
				BigDecimal baseFareAmt = suppTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT);
				JSONObject baseFareJson = new JSONObject();
				baseFareJson.put(JSON_PROP_AMOUNT,
						baseFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
				baseFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);

				// TODO : Need to find totalFareCompsGroup path.
				totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode,
						baseFareJson.getBigDecimal(JSON_PROP_AMOUNT));

				// If amount of receivables group is greater than zero, then append to
				// commercial prices
				if (totalReceivablesCompsGroup != null
						&& totalReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
					totalFareCompsGroup.addSubComponent(totalReceivablesCompsGroup, null);

				}
				totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode, totalFareAmt.multiply(paxCount));
				resGroundServicesJson.put(JSON_PROP_TOTALCHARGE, totalFareCompsGroup.toJSON());
				JSONObject newTotalFare = resGroundServicesJson.getJSONObject(JSON_PROP_TOTALCHARGE);

				suppCommercialItinInfoJson.put(JSON_PROP_SUPPCOMMTOTALS, suppCommJsonArr);
				// suppCommercialItinInfoArr.put(suppCommercialItinInfoJson);
				// resGroundServicesJson.put(JSON_PROP_SUPPCOMM, suppCommJsonArr);
				suppCommercialItinInfoJson.put(JSON_PROP_SUPPTOTALFARE, totalChargeJson);
				// client commercial
				JSONObject clientEntityDetailsJson = new JSONObject();
				JSONObject userCtxJson = usrCtx.toJSON();
				JSONArray clientEntityDetailsArr = userCtxJson.getJSONArray(JSON_PROP_CLIENTCOMMENTITYDTLS);
				for (int y = 0; y < clientEntityDetailsArr.length(); y++) {
					/*
					 * if(clientEntityDetailsArr.getJSONObject(y).opt(JSON_PROP_COMMENTITYID)!=null)
					 * { if(clientEntityDetailsArr.getJSONObject(y).opt(JSON_PROP_COMMENTITYID).
					 * toString().equalsIgnoreCase(clientEntityCommJson.get("entityName").toString()
					 * )) { clientEntityDetailsJson=clientEntityDetailsArr.getJSONObject(y); } }
					 */
					// TODO:Add a check later

					clientEntityDetailsJson = clientEntityDetailsArr.getJSONObject(y);

				}
				String commercialCurrency = "";
				// markup commercialcalc clientCommercial
				clientCommercial.put(JSON_PROP_COMMTYPE,
						clntCommToTypeMap.get(markupCalcJson.get(JSON_PROP_COMMNAME).toString()));
				clientCommercial.put(JSON_PROP_COMMAMOUNT, markupCalcJson.get(JSON_PROP_COMMAMOUNT).toString());
				clientCommercial.put(JSON_PROP_COMMNAME, markupCalcJson.get(JSON_PROP_COMMNAME));

				if ((markupCalcJson.get(JSON_PROP_COMMCALCPCT).toString()) == null) {
					clientCommercial.put(JSON_PROP_COMMCCY, markupCalcJson.get(JSON_PROP_COMMCCY).toString());
				} else {
					clientCommercial.put(JSON_PROP_COMMCCY, commercialCurrency);
				}
				clientCommercial.put(JSON_PROP_COMMCCY,
						markupCalcJson.optString(JSON_PROP_COMMCCY, commercialCurrency));
				clientEntityCommercialsJsonArr.put(clientCommercial);

				clientEntityDetailsJson.put(JSON_PROP_CLIENTCOMM, clientEntityCommercialsJsonArr);
				// clientEntityDetailsJson.put(JSON_PROP_TOTALFARE, totalFarecommJson);
				clientCommercials.put(clientEntityDetailsJson);

				clientCommercialItinInfoJson.put(JSON_PROP_CLIENTENTITYCOMMS, clientCommercials);
			/*	totalFarecommJson.remove(JSON_PROP_AMOUNT);
				totalFarecommJson.remove(JSON_PROP_CURRENCYCODE);*/
				JSONObject totalAmt = new JSONObject();
				totalAmt.put(JSON_PROP_AMOUNT, newTotalFare.get(JSON_PROP_AMOUNT));
				totalAmt.put(JSON_PROP_CURRENCYCODE, newTotalFare.getString(JSON_PROP_CURRENCYCODE) );
				clientCommercialItinInfoJson.put(JSON_PROP_TOTALFARE, totalAmt);

				break;
			}
			if (retainSuppFares) {

				resGroundServicesJson.put(JSON_PROP_CLIENTCOMMTOTAL, clientCommercialItinInfoJson);
				resGroundServicesJson.put(JSON_PROP_SUPPPRICEINFO, suppCommercialItinInfoJson);

				/*
				 * supplierResArr.put(JSON_PROP_CLIENTCOMMTOTAL);
				 * supplierResArr.put(JSON_PROP_SUPPPRICEINFO);
				 */
				/* resJson.put("supplierResponse", supplierResArr); */
				JSONObject supplierResJson = new JSONObject();

				JSONObject groundServiceJson = new JSONObject();
				groundServiceJson.put(JSON_PROP_CLIENTCOMMTOTAL, clientCommercialItinInfoJson);
				groundServiceJson.put(JSON_PROP_SUPPPRICEINFO, suppCommercialItinInfoJson);
				groundServiceArr.put(groundServiceJson);
				supplierResJson.put(JSON_PROP_GROUNDSERVICES, groundServiceArr);
				resJson.put("supplierResponse", supplierResJson);

			}
		}
		logger.trace(String.format("supplierResponse after supplierItinFare = %s", resJson.toString()));
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

	private static JSONArray getSupplierCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
		return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results")
				.getJSONObject(0).getJSONObject("value")
				.getJSONObject("cnk.transfers_commercialscalculationengine.suppliertransactionalrules.Root")
				.getJSONArray("businessRuleIntake");
	}

	// Retrieve commercials head array from client commercials and find type
	// (Receivable, Payable) for commercials
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

	private static JSONArray getClientCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
		return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results")
				.getJSONObject(0).getJSONObject("value")
				.getJSONObject("cnk.transfers_commercialscalculationengine.clienttransactionalrules.Root")
				.getJSONArray("businessRuleIntake");
	}

	public static JSONObject getEmptyResponse(JSONObject reqHdrJson) {
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, new JSONObject());
		return resJson;
	}

	private static JSONObject convertToClientEntityCommercialJson(JSONObject clientCommJson,
			Map<String, String> clntCommToTypeMap, String suppCcyCode) {
		JSONObject clientCommercial = new JSONObject();
		String commercialName = clientCommJson.getString(JSON_PROP_COMMNAME);
		clientCommercial.put(JSON_PROP_COMMTYPE, clntCommToTypeMap.getOrDefault(commercialName, "?"));
		clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
		clientCommercial.put(JSON_PROP_COMMNAME, clientCommJson.getString(JSON_PROP_COMMNAME));
		clientCommercial.put(JSON_PROP_COMMCCY, clientCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
		return clientCommercial;
	}
}
