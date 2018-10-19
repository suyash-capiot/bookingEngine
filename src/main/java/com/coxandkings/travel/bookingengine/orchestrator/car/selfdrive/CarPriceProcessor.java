package com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive;

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
import com.coxandkings.travel.bookingengine.config.car.CarConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class CarPriceProcessor implements CarConstants{
	
	private static final Logger logger = LogManager.getLogger(CarPriceProcessor.class);
	
	public static String process(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException{
		
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		JSONArray multiReqArr = null; 
		try {
			opConfig = CarConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./car:OTA_VehAvailRateRQWrapper");
			reqBodyElem.removeChild(wrapperElem);

			reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			CarSearchProcessor.createHeader(reqHdrJson, reqElem);
			validateRequestParameters(reqJson);
			
			multiReqArr = reqBodyJson.getJSONArray(JSON_PROP_CARRENTALARR);
			for (int y = 0; y < multiReqArr.length(); y++) {
				
				JSONObject carRentalReq = multiReqArr.getJSONObject(y);
				Element suppWrapperElem = null;
				suppWrapperElem = (Element) wrapperElem.cloneNode(true);
				XMLUtils.setValueAtXPath(suppWrapperElem, "./car:Sequence", String.valueOf(y));
				reqBodyElem.appendChild(suppWrapperElem);
				populateWrapperElement(reqElem, ownerDoc, suppWrapperElem, usrCtx, carRentalReq);
			}
			
		}catch (Exception x) {
			logger.error("Exception during request processing", x);
			throw new RequestProcessingException(x);
		}
			
		try {
			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), CarConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}
	        
	        int idx=0;
			JSONObject resBodyJson = new JSONObject();
			JSONArray vehicleAvailJsonArray = null;
			Element[] resWrapperElems = XMLUtils.getElementsAtXPath(resElem, "./cari:ResponseBody/car:OTA_VehAvailRateRSWrapper");
			int sequence = 0;
			String sequence_str;
			JSONArray carRentalArr = new JSONArray();
			for (Element resWrapperElem : resWrapperElems) {
				//TODO : SI sends multiple cars in reprice response for a single request.
				//Eventually needs to be Handled by SI.
				vehicleAvailJsonArray = new JSONArray();
				Element resBodyElem = XMLUtils.getFirstElementAtXPath(resWrapperElem, "./ota:OTA_VehAvailRateRS/ota:VehAvailRSCore");
				CarSearchProcessor.getSupplierResponseVehicleAvailJSON(resBodyElem, vehicleAvailJsonArray, true, idx++);
				sequence = (sequence_str = XMLUtils.getValueAtXPath(resWrapperElem, "./car:Sequence")).isEmpty()? sequence: Integer.parseInt(sequence_str);
				carRentalArr.put(sequence, (new JSONObject()).put(JSON_PROP_VEHICLEAVAIL, vehicleAvailJsonArray));
			}			
		
			for (int y = 0; y < multiReqArr.length(); y++) {
				String city = multiReqArr.getJSONObject(y).optString(JSON_PROP_CITY);
				JSONArray vehicleAvailArr = carRentalArr.getJSONObject(y).getJSONArray(JSON_PROP_VEHICLEAVAIL);
				for(int i=0;i < vehicleAvailArr.length();i++) {
					
					//This is just a hack.
					//TODO : Eventually will be handled by SI, SI PriceRS should give the reference id.
					JSONObject resvehicleAvail = vehicleAvailArr.getJSONObject(i);
					JSONObject reference = resvehicleAvail.optJSONObject(JSON_PROP_REFERENCE);
					if(reference==null) 
						resvehicleAvail.put(JSON_PROP_REFERENCE, multiReqArr.getJSONObject(y).optJSONObject(JSON_PROP_REFERENCE));
					
					resvehicleAvail.put(JSON_PROP_CITY, city);
				}	
			}

			resBodyJson.put(JSON_PROP_CARRENTALARR, carRentalArr);
			
			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));
	        
	        Map<String,Integer> suppResToBRIIndex = new HashMap<String, Integer>();
			JSONObject resSuppCommJson = SupplierCommercials.getSupplierCommercials(CommercialsOperation.Reprice, reqJson, resJson, suppResToBRIIndex);
			JSONObject resClientCommJson = ClientCommercials.getClientCommercialsV2(reqJson, resJson, resSuppCommJson);
			CarSearchProcessor.calculatePricesV2(reqJson, resJson, resSuppCommJson, resClientCommJson, suppResToBRIIndex, true, usrCtx, true);
			
			TaxEngine.getCompanyTaxes(reqJson, resJson);
			pushSuppFaresToRedisAndRemove(resJson);
			
			return resJson.toString();
			
		} catch (Exception x) {
			logger.error("Exception received while processing", x);
			throw new InternalProcessingException(x);
		}
	}


	private static void validateRequestParameters(JSONObject reqJson) throws ValidationException{
		
		CarRequestValidator.validatePaxDetails(reqJson);
	}


	private static void pushSuppFaresToRedisAndRemove(JSONObject resJson) {
		
		JSONObject resHdrJson = resJson.getJSONObject(JSON_PROP_RESHEADER);
		JSONObject resBodyJson = resJson.getJSONObject(JSON_PROP_RESBODY);
		JSONArray carRentalInfoArr = resBodyJson.optJSONArray(JSON_PROP_CARRENTALARR);
		
		Map<String, String> reprcSuppFaresMap = new HashMap<String, String>();
		for(Object carRentalInfo : carRentalInfoArr) {
			JSONArray vehicleAvailJsonArr = ((JSONObject) carRentalInfo).getJSONArray(JSON_PROP_VEHICLEAVAIL);
			for (int i=0; i < vehicleAvailJsonArr.length(); i++) {
				JSONObject suppPriceBookInfoJson = new JSONObject();
				JSONObject vehicleAvailJson = vehicleAvailJsonArr.getJSONObject(i);
				JSONObject suppPriceInfoJson = vehicleAvailJson.optJSONObject(JSON_PROP_SUPPPRICEINFO);
				vehicleAvailJson.remove(JSON_PROP_SUPPPRICEINFO);
				vehicleAvailJson.remove(JSON_PROP_BOOKREFIDX);
				
				//Getting ClientCommercials Info
				JSONObject totalPriceInfo = vehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO);
				JSONArray clientCommercialsTotalJsonArr = totalPriceInfo.optJSONArray(JSON_PROP_CLIENTENTITYTOTALCOMMS);
				totalPriceInfo.remove(JSON_PROP_CLIENTENTITYTOTALCOMMS);
				
				if ( suppPriceInfoJson == null) {
					// TODO: This should never happen. Log a warning message here.
					continue;
				}
				suppPriceBookInfoJson.put(JSON_PROP_SUPPPRICEINFO, suppPriceInfoJson);
				suppPriceBookInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercialsTotalJsonArr);
				reprcSuppFaresMap.put(getRedisKeyForVehicleAvail(vehicleAvailJson), suppPriceBookInfoJson.toString());
			}
		}
		
		if(!reprcSuppFaresMap.isEmpty()) {
			try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
			String redisKey = String.format("%s%c%s", resHdrJson.optString(JSON_PROP_SESSIONID), KEYSEPARATOR, PROD_CATEG_SUBTYPE_CAR);
			redisConn.hmset(redisKey, reprcSuppFaresMap);
			redisConn.pexpire(redisKey, (long) (CarConfig.getRedisTTLMinutes() * 60 * 1000));
			}
			//RedisConfig.releaseRedisConnectionToPool(redisConn);
		}
	}
	
	
	static String getRedisKeyForVehicleAvail(JSONObject vehicleAvailJson) {
		
		List<String> keys = new ArrayList<>();
		String suppId = vehicleAvailJson.optString(JSON_PROP_SUPPREF);
		keys.add(String.format("%s%s", suppId.substring(0,1).toUpperCase(), suppId.substring(1).toLowerCase()));
		keys.add(vehicleAvailJson.optString(JSON_PROP_PICKUPDATE));
		keys.add(vehicleAvailJson.optString(JSON_PROP_PICKUPLOCCODE));
		keys.add(vehicleAvailJson.optString(JSON_PROP_RETURNDATE));
		keys.add(vehicleAvailJson.optString(JSON_PROP_RETURNLOCCODE));
		keys.add(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString(JSON_PROP_VEHMAKEMODELNAME));
		keys.add(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString(JSON_PROP_VEHMAKEMODELCODE));
		keys.add(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString(JSON_PROP_VEHICLECATEGORY));
		keys.add(vehicleAvailJson.optString(JSON_PROP_ISPREPAID));
		keys.add(vehicleAvailJson.optString(JSON_PROP_VENDORDIVISION));
		if(vehicleAvailJson.optJSONObject(JSON_PROP_REFERENCE)!=null)
			keys.add(vehicleAvailJson.getJSONObject(JSON_PROP_REFERENCE).optString(JSON_PROP_ID));
		
		// TODO : Find if we should add equipments/Coverages in key as same Car with equipments/Coverages will have different prices
		JSONObject equipObj = vehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO).getJSONObject(JSON_PROP_TOTALFARE).optJSONObject(JSON_PROP_SPLEQUIPS);
		if(equipObj!=null) {
			JSONArray equipsArr = equipObj.optJSONArray(JSON_PROP_SPLEQUIP);
			for(int i=0;equipsArr!=null && i<equipsArr.length();i++) {
				JSONObject equipJson = equipsArr.getJSONObject(i);
				keys.add(String.format("%s:%s","equipType", equipJson.optString(JSON_PROP_EQUIPTYPE)));
				keys.add(String.format("%s:%s","quantity", String.valueOf(equipJson.optInt(JSON_PROP_QTY, 1))));
			}
		}
		
		JSONObject covrgObj = vehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO).getJSONObject(JSON_PROP_TOTALFARE).optJSONObject(JSON_PROP_PRICEDCOVRGS);
		if(covrgObj!=null) {
			JSONArray covrgsArr = covrgObj.optJSONArray(JSON_PROP_PRICEDCOVRG);
			for(int i=0;covrgsArr!=null && i<covrgsArr.length();i++) {
				JSONObject covrgsJson = covrgsArr.getJSONObject(i);
				keys.add(String.format("%s:%s","coverageType", covrgsJson.optString(JSON_PROP_COVERAGETYPE)));
			}
		}
		
		String key = keys.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(Character.valueOf(KEYSEPARATOR).toString()));
		return key;
		
		/*StringBuilder strBldr = new StringBuilder(vehicleAvailJson.optString(JSON_PROP_SUPPREF));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_PICKUPDATE));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_PICKUPLOCCODE));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_RETURNDATE));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_RETURNLOCCODE));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_ONEWAYINDC));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_PICKUPDATE));
		strBldr.append(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString("VehMakeModelName"));
		strBldr.append(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString("VehMakeModelCode"));
		strBldr.append(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString("VehicleCategory"));
		strBldr.append(vehicleAvailJson.getJSONObject(JSON_PROP_REFERENCE).optString("Id"));*/
		
/*		strBldr.append(vehicleAvailJson.optString("IsPrepaid"));
		strBldr.append(vehicleAvailJson.optString("VendorDivision"));*/
		
//		return strBldr.toString();
	}
	
	private static void populateWrapperElement(Element reqElem, Document ownerDoc, Element suppWrapperElem,
			UserContext usrCtx, JSONObject carRentalReq) throws Exception {
	
		
		//TODO : Need To Change later
		Element sourceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehAvailRateRQ/ota:POS/ota:Source");
		sourceElem.setAttribute("ISOCurrency", "EUR");
		sourceElem.setAttribute("ISOCountry", "IE");
		
		String suppID = carRentalReq.getString(JSON_PROP_SUPPREF);
		ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_CAR, suppID);
		if (prodSupplier == null) {
			throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
		}

		Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestHeader/com:SupplierCredentialsList");
		Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem, String.format("./com:SupplierCredentials[com:SupplierID = '%s']", suppID));
		if (suppCredsElem == null) {
			suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
		}

		Element suppIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./car:SupplierID");
		suppIDElem.setTextContent(suppID);
		
		 Element vehAvailRQCoreElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehAvailRateRQ/ota:VehAvailRQCore");
		 Element vehAvailRQInfoElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_VehAvailRateRQ/ota:VehAvailRQInfo");
//		         JSONObject vehRentalObj = vehAvailRQCore.getJSONObject(JSON_PROP_VEHRENTAL);
		
		 Element vehRentalElem =  CarSearchProcessor.getVehRentalCoreElement(ownerDoc, carRentalReq);
		 vehAvailRQCoreElem.appendChild(vehRentalElem);
		 
		 if(carRentalReq.optString("vendorPrefCode")!=null && !carRentalReq.optString("vendorPrefCode").equals("")) {
			 Element vendorPrefsElem = CarSearchProcessor.getVendorPrefsElement(ownerDoc, carRentalReq);
			 vehAvailRQCoreElem.appendChild(vendorPrefsElem);
		 }
		  
		 Element VehPrefsElem =  ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehPrefs");
		 Element VehPrefElem =  ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehPref");
		 VehPrefElem.setAttribute("CodeContext", carRentalReq.optString(JSON_PROP_CODECONTEXT).isEmpty() ? "DEFAULT" : carRentalReq.getString(JSON_PROP_CODECONTEXT));
		 VehPrefsElem.appendChild(VehPrefElem);
		 vehAvailRQCoreElem.appendChild(VehPrefsElem);
		 
		 Element driverAge = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DriverType");
		 driverAge.setAttribute("Age", CARRENTAL_DRIVER_AGE);
		 vehAvailRQCoreElem.appendChild(driverAge);
		 
		 Element specialEquipsElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:SpecialEquipPrefs");
		 JSONArray specialEquipsArr = carRentalReq.optJSONArray(JSON_PROP_SPLEQUIPS);
		 if(specialEquipsArr!=null) {
			 for(int i=0;i<specialEquipsArr.length();i++) {
				 JSONObject specialEquipJson = specialEquipsArr.optJSONObject(i);
				 Element specialEquipElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:SpecialEquipPref");
				 //TODO: Better Way to handle this
				 specialEquipElem.setAttribute("Quantity", String.valueOf(specialEquipJson.optInt(JSON_PROP_QTY, 1)));
				 specialEquipElem.setAttribute("EquipType", specialEquipJson.getString("equipType"));
				 specialEquipsElem.appendChild(specialEquipElem);
			 }
		 }
		 vehAvailRQCoreElem.appendChild(specialEquipsElem);
		 
		 if(carRentalReq.optJSONObject(JSON_PROP_REFERENCE)!=null) {
			 Element tpaExtensions = ownerDoc.createElementNS(Constants.NS_OTA, "ota:TPA_Extensions");
			 Element extendedRPH = ownerDoc.createElementNS(CarConstants.NS_CAR, "car:ExtendedRPH");
			 JSONObject reference = carRentalReq.getJSONObject(JSON_PROP_REFERENCE);
			 extendedRPH.setTextContent(reference.getString(JSON_PROP_ID));
			 tpaExtensions.appendChild(extendedRPH);
			 vehAvailRQCoreElem.appendChild(tpaExtensions);
		}
		 
         JSONArray customerJsonArr = carRentalReq.optJSONArray(JSON_PROP_PAXDETAILS);
         if(customerJsonArr!=null && customerJsonArr.length()!=0) {
       	  	Element customerElem = CarSearchProcessor.populateCustomerElement(ownerDoc, customerJsonArr);
       	  	vehAvailRQInfoElem.appendChild(customerElem);
       	 }
         
         JSONArray pricedCovrgsArr = carRentalReq.optJSONArray(JSON_PROP_PRICEDCOVRGS);
         if(pricedCovrgsArr!=null && pricedCovrgsArr.length()!=0) {
        	 Element pricedCovrgsElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CoveragePrefs");
        	 for(int i=0;i<pricedCovrgsArr.length();i++) {
				 JSONObject pricedCovrgJson = pricedCovrgsArr.optJSONObject(i);
				 Element pricedCovrgElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CoveragePref");
				 //TODO: Better Way to handle this
				 pricedCovrgElem.setAttribute("CoverageType", pricedCovrgJson.getString(JSON_PROP_COVERAGETYPE));
				 pricedCovrgsElem.appendChild(pricedCovrgElem);
			 }
        	 vehAvailRQInfoElem.appendChild(pricedCovrgsElem);
         }
	} 	  
}
	

