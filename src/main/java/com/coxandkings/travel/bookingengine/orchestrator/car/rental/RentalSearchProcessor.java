package com.coxandkings.travel.bookingengine.orchestrator.car.rental;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.enums.CommercialsOperation;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.orchestrator.car.PriceComponentsGroup;
import com.coxandkings.travel.bookingengine.orchestrator.car.rental.enums.TripIndicator;
import com.coxandkings.travel.bookingengine.orchestrator.car.rental.enums.TripType;
import com.coxandkings.travel.bookingengine.orchestrator.car.rental.enums.Unit;
import com.coxandkings.travel.bookingengine.orchestrator.cruise.GetDateRange;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

import redis.clients.jedis.Jedis;

public class RentalSearchProcessor implements CarConstants {

	private static final Logger logger = LogManager.getLogger(RentalSearchProcessor.class);
//  private static String mRcvsPriceCompQualifier = JSON_PROP_RECEIVABLES.concat(SIGMA).concat(".").concat(JSON_PROP_RECEIVABLE).concat(".");
    private static String mTaxesPriceCompQualifier = JSON_PROP_TAXES.concat(SIGMA).concat(".").concat(JSON_PROP_TAX).concat(".");
	private static String mIncvPriceCompFormat = JSON_PROP_INCENTIVE.concat(".%s@").concat(JSON_PROP_CODE);
	
	protected static void createHeader(JSONObject reqHdrJson ,Element reqElem) {
		Element sessionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestHeader/com:SessionID");
		sessionElem.setTextContent(reqHdrJson.getString(JSON_PROP_SESSIONID));

		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestHeader/com:TransactionID");
		transactionElem.setTextContent(reqHdrJson.getString(JSON_PROP_TRANSACTID));

		Element userElem = XMLUtils.getFirstElementAtXPath(reqElem, "./cari:RequestHeader/com:UserID");
		userElem.setTextContent(reqHdrJson.getString(JSON_PROP_USERID));
	}

	public static void getSupplierResponseVehicleAvailJSON(Element resBodyElem, JSONArray vehicleAvailJsonArr)
			throws Exception {
		getSupplierResponseVehicleAvailJSON(resBodyElem, vehicleAvailJsonArr, true, 0);
	}

	public static void getSupplierResponseVehicleAvailJSON(Element resBodyElem, JSONArray vehicleAvailJsonArr,
			boolean generateBookRefIdx, int bookRefIdx) throws Exception {

		Element[] vehVendorElems = XMLUtils.getElementsAtXPath(resBodyElem, "./ota:VehVendorAvails/ota:VehVendorAvail");
		Element vehRentalElem = XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:VehRentalCore");

		for (Element vehVendorElem : vehVendorElems) {
			
			Element[] vehAvailElems = XMLUtils.getElementsAtXPath(vehVendorElem, "./ota:VehAvails/ota:VehAvail");
			
			JSONObject vehicleAvailJson = new JSONObject();
			vehicleAvailJson.put("rateCategory", XMLUtils.getValueAtXPath(vehVendorElem, "./ota:VehAvails/@RateCategory"));
			vehicleAvailJson.put("ratePeriod", XMLUtils.getValueAtXPath(vehVendorElem, "./ota:VehAvails/@RatePeriod"));
			Element vendorInfoElem = XMLUtils.getFirstElementAtXPath(vehVendorElem, "./ota:Vendor");
			vehicleAvailJson.put(JSON_PROP_VENDORDIVISION, XMLUtils.getValueAtXPath(vendorInfoElem, "./@Division"));
			vehicleAvailJson.put("vendorPrefCode", XMLUtils.getValueAtXPath(vendorInfoElem, "./@Code"));
			vehicleAvailJson.put("vendorCodeContext", XMLUtils.getValueAtXPath(vendorInfoElem, "./@CodeContext"));
			vehicleAvailJson.put("vendorCompanyName", XMLUtils.getValueAtXPath(vendorInfoElem, "./@CompanyShortName"));
			
			Element locationDetails = XMLUtils.getFirstElementAtXPath(vehVendorElem, "./ota:Info/ota:LocationDetails");
			vehicleAvailJson.put(JSON_PROP_LOCATIONDETAIL, getLocationDetailsJSON(locationDetails));
			
			for(Element vehAvailElem : vehAvailElems) {
				
				JSONObject vehAvailJson = new JSONObject(vehicleAvailJson.toString());
				getVehicleAvailJSON(vehAvailElem, vehAvailJson);
				String suppId = XMLUtils.getValueAtXPath((Element) resBodyElem.getParentNode().getParentNode(), "./car:SupplierID");
				if (generateBookRefIdx) {
					vehAvailJson.put(JSON_PROP_BOOKREFIDX, bookRefIdx);
				}
				
				vehAvailJson.put(JSON_PROP_SUPPREF, suppId);
				vehAvailJson.put(JSON_PROP_PICKUPDATE, XMLUtils.getValueAtXPath(vehRentalElem, "./@PickUpDateTime"));
				vehAvailJson.put(JSON_PROP_RETURNDATE, XMLUtils.getValueAtXPath(vehRentalElem, "./@ReturnDateTime"));
				
				String oneWayIndc = XMLUtils.getValueAtXPath(vehRentalElem, "./@OneWayIndicator");
				String tripType = oneWayIndc.isEmpty() ? "" : (oneWayIndc.equals("true") ? TripType.ONEWAY.toString() : TripType.RETURN.toString());
				
				vehAvailJson.put(JSON_PROP_TRIPTYPE, tripType);
				vehAvailJson.put(JSON_PROP_PICKUPLOCCODE,
						XMLUtils.getValueAtXPath(vehRentalElem, "./ota:PickUpLocation/@LocationCode"));
				vehAvailJson.put(JSON_PROP_RETURNLOCCODE,
						XMLUtils.getValueAtXPath(vehRentalElem, "./ota:ReturnLocation/@LocationCode"));
				vehAvailJson.put(JSON_PROP_PICKUPLOCEXTCODE,
						XMLUtils.getValueAtXPath(vehRentalElem, "./ota:PickUpLocation/@ExtendedLocationCode"));
				vehAvailJson.put(JSON_PROP_RETURNLOCEXTCODE,
						XMLUtils.getValueAtXPath(vehRentalElem, "./ota:ReturnLocation/@ExtendedLocationCode"));
				vehAvailJson.put(JSON_PROP_PICKUPLOCCODECONTXT,
						XMLUtils.getValueAtXPath(vehRentalElem, "./ota:PickUpLocation/@CodeContext"));
				vehAvailJson.put(JSON_PROP_RETURNLOCCODECONTXT,
						XMLUtils.getValueAtXPath(vehRentalElem, "./ota:ReturnLocation/@CodeContext"));
				vehAvailJson.put(JSON_PROP_PICKUPLOCNAME,
						XMLUtils.getValueAtXPath(vehRentalElem, "./ota:PickUpLocation/@Name"));
				vehAvailJson.put(JSON_PROP_RETURNLOCNAME,
						XMLUtils.getValueAtXPath(vehRentalElem, "./ota:ReturnLocation/@Name"));
	
				vehicleAvailJsonArr.put(vehAvailJson);
			}
		}
	}

	static String getRedisKeyForVehicleAvail(JSONObject vehicleAvailJson) {
		
		//TODO : Some request keys are not required to make this unique.
		// Will be removed later.
		List<String> keys = new ArrayList<>();
		String suppId = vehicleAvailJson.optString(JSON_PROP_SUPPREF);
		keys.add(String.format("%s%s", suppId.substring(0,1).toUpperCase(), suppId.substring(1).toLowerCase()));
//		keys.add(vehicleAvailJson.optString(JSON_PROP_PICKUPDATE));
//		keys.add(vehicleAvailJson.optString(JSON_PROP_PICKUPLOCCODE));
//		keys.add(vehicleAvailJson.optString(JSON_PROP_RETURNDATE));
//		keys.add(vehicleAvailJson.optString(JSON_PROP_RETURNLOCCODE));
		
		String tripTypeStr = vehicleAvailJson.optString(JSON_PROP_TRIPTYPE);
		if(Utils.isStringNotNullAndNotEmpty(tripTypeStr))
			keys.add(TripType.forString(tripTypeStr).toString());
		
		keys.add(vehicleAvailJson.optString(JSON_PROP_TRIPINDC));
		keys.add(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString(JSON_PROP_VEHMAKEMODELNAME));
		keys.add(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString(JSON_PROP_VEHMAKEMODELCODE));
		keys.add(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString(JSON_PROP_VEHICLECATEGORY));
		keys.add(vehicleAvailJson.getJSONObject(JSON_PROP_REFERENCE).optString(JSON_PROP_ID));
		
		String key = keys.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(Character.valueOf(KEYSEPARATOR).toString()));
		return key;
				
		/*StringBuilder strBldr = new StringBuilder(vehicleAvailJson.optString(JSON_PROP_SUPPREF));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_PICKUPDATE));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_PICKUPLOCCODE));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_RETURNDATE));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_RETURNLOCCODE));
		strBldr.append(vehicleAvailJson.optString(JSON_PROP_TRIPTYPE));
		strBldr.append(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString(JSON_PROP_CODECONTEXT));
		strBldr.append(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString(JSON_PROP_VEHMAKEMODELNAME));
		strBldr.append(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString(JSON_PROP_VEHMAKEMODELCODE));
		strBldr.append(vehicleAvailJson.getJSONObject(JSON_PROP_VEHICLEINFO).optString(JSON_PROP_VEHICLECATEGORY));
		strBldr.append(vehicleAvailJson.getJSONObject(JSON_PROP_REFERENCE).optString("Id"));*/
		
//		return strBldr.toString();
	}
	
	private static JSONObject getVehicleAvailJSON(Element vehAvailElem, JSONObject vehVehicleJson) throws Exception {

		Element vehicleElem = XMLUtils.getFirstElementAtXPath(vehAvailElem, "./ota:VehAvailCore/ota:Vehicle");
		vehVehicleJson.put(JSON_PROP_VEHICLEINFO, getVehicleInfo(vehicleElem));
		
		Element vehAvailCoreElem = XMLUtils.getFirstElementAtXPath(vehAvailElem, "./ota:VehAvailCore");
		Element vehAvailInfoElem = XMLUtils.getFirstElementAtXPath(vehAvailElem, "./ota:VehAvailInfo");
		JSONObject pricingInfo = getPricingInfoJSON(vehAvailCoreElem, vehAvailInfoElem);
		vehVehicleJson.put(JSON_PROP_TOTALPRICEINFO, pricingInfo);
		
		vehVehicleJson.put(JSON_PROP_STATUS, XMLUtils.getValueAtXPath(vehAvailCoreElem, "./@Status"));
		vehVehicleJson.put("isAlternateInd", XMLUtils.getValueAtXPath(vehAvailCoreElem, "./@IsAlternateInd"));
//		vehVehicleJson.put("rateCategory", XMLUtils.getValueAtXPath(vehVendorElem, "./ota:VehAvails/@RateCategory"));
//		vehVehicleJson.put("ratePeriod", XMLUtils.getValueAtXPath(vehVendorElem, "./ota:VehAvails/@RatePeriod"));

		Element paymentRulesElem = XMLUtils.getFirstElementAtXPath(vehAvailInfoElem, "./ota:PaymentRules");
		vehVehicleJson.put(JSON_PROP_PAYMENTRULES, getPaymentRulesJSON(paymentRulesElem));

/*		Element vendorInfoElem = XMLUtils.getFirstElementAtXPath(vehVendorElem, "./ota:Vendor");
		vehVehicleJson.put(JSON_PROP_VENDORDIVISION, XMLUtils.getValueAtXPath(vendorInfoElem, "./@Division"));
		vehVehicleJson.put("vendorPrefCode", XMLUtils.getValueAtXPath(vendorInfoElem, "./@Code"));
		vehVehicleJson.put("vendorCodeContext", XMLUtils.getValueAtXPath(vendorInfoElem, "./@CodeContext"));
		vehVehicleJson.put("vendorCompanyName", XMLUtils.getValueAtXPath(vendorInfoElem, "./@CompanyShortName"));*/

		Element rateDistElems[] = XMLUtils.getElementsAtXPath(vehAvailElem, "./ota:VehAvailCore/ota:RentalRate/ota:RateDistance");
		vehVehicleJson.put(JSON_PROP_RATEDISTANCE, getRateDistanceJSON(rateDistElems));

		Element rateQualifierElem = XMLUtils.getFirstElementAtXPath(vehAvailElem, "./ota:VehAvailCore/ota:RentalRate/ota:RateQualifier");
		vehVehicleJson.put(JSON_PROP_RATEQUALIFIER, getRateQualifierJSON(rateQualifierElem));

		Element referenceElem = XMLUtils.getFirstElementAtXPath(vehAvailElem, "./ota:VehAvailCore/ota:Reference");
		vehVehicleJson.put(JSON_PROP_REFERENCE, getReferenceJSON(referenceElem));
		vehVehicleJson.put(JSON_PROP_ISPREPAID, XMLUtils.getValueAtXPath(referenceElem, "./ota:TPA_Extensions/car:VehAvailDetails/@IsPrepaid"));

		Element cancellationPolicy = XMLUtils.getFirstElementAtXPath(vehAvailInfoElem, "./ota:TPA_Extensions/car:CarRentals_TPA/car:CancellationPolicy");
		vehVehicleJson.put(JSON_PROP_CANCELPOLICY, XMLUtils.getValueAtXPath(cancellationPolicy, "./car:Description"));

//		Element locationDetails = XMLUtils.getFirstElementAtXPath(vehVendorElem, "./ota:Info/ota:LocationDetails");
//		vehVehicleJson.put(JSON_PROP_LOCATIONDETAIL, getLocationDetailsJSON(locationDetails));

		return vehVehicleJson;
	}

	protected static JSONArray getRateDistanceJSON(Element[] rateDistElems) {

		JSONArray rateDistJsonArr = new JSONArray();
		for (Element rateDistElem : rateDistElems) {
			JSONObject rateDistJson = new JSONObject();
			rateDistJson.put("distUnitName", XMLUtils.getValueAtXPath(rateDistElem, "./@DistUnitName"));
			rateDistJson.put("vehiclePeriodUnitName", XMLUtils.getValueAtXPath(rateDistElem, "./@VehiclePeriodUnitName"));
			rateDistJson.put("quantity", XMLUtils.getValueAtXPath(rateDistElem, "./@Quantity"));

			rateDistJsonArr.put(rateDistJson);
		}
		return rateDistJsonArr;
	}

	protected static JSONObject getRateQualifierJSON(Element rateQualifierElem) {

		JSONObject rateQualifierJson = new JSONObject();
		rateQualifierJson.put("corpDiscountNmbr", XMLUtils.getValueAtXPath(rateQualifierElem, "./@CorpDiscountNmbr"));
		rateQualifierJson.put("rateQualifier", XMLUtils.getValueAtXPath(rateQualifierElem, "./@RateQualifier"));
		rateQualifierJson.put("ratePeriod", XMLUtils.getValueAtXPath(rateQualifierElem, "./@RatePeriod"));

		return rateQualifierJson;
	}

	protected static JSONObject getLocationDetailsJSON(Element locationDetailsElem) {

		JSONObject locationDetailsJson = new JSONObject();
		locationDetailsJson.put("atAirport", XMLUtils.getValueAtXPath(locationDetailsElem, "./@AtAirport"));
		locationDetailsJson.put("name", XMLUtils.getValueAtXPath(locationDetailsElem, "./@Name"));
		locationDetailsJson.put("code", XMLUtils.getValueAtXPath(locationDetailsElem, "./@Code"));
		locationDetailsJson.put("extendedLocationCode",
				XMLUtils.getValueAtXPath(locationDetailsElem, "./@ExtendedLocationCode"));
		Element addressElem[] = XMLUtils.getElementsAtXPath(locationDetailsElem, "./ota:Address/ota:AddressLine");
		if (addressElem.length != 0)
			locationDetailsJson.put(JSON_PROP_ADDRLINE1, addressElem[0].getTextContent());
		if (addressElem.length == 2)
			locationDetailsJson.put(JSON_PROP_ADDRLINE2, addressElem[1].getTextContent());

		locationDetailsJson.put(JSON_PROP_CITY, XMLUtils.getValueAtXPath(locationDetailsElem, "./ota:Address/ota:CityName"));
		locationDetailsJson.put(JSON_PROP_COUNTRY,
				XMLUtils.getValueAtXPath(locationDetailsElem, "./ota:Address/ota:CountryName"));
		locationDetailsJson.put(JSON_PROP_ZIP, XMLUtils.getValueAtXPath(locationDetailsElem, "./ota:Address/ota:PostalCode"));

		locationDetailsJson.put("parkLocation",
				XMLUtils.getValueAtXPath(locationDetailsElem, "./ota:AdditionalInfo/ota:ParkLocation/@Location"));
		locationDetailsJson.put("counterLocation",
				XMLUtils.getValueAtXPath(locationDetailsElem, "./ota:AdditionalInfo/ota:CounterLocation/@Location"));

		return locationDetailsJson;
	}

	protected static JSONObject getReferenceJSON(Element referenceElem) {

		JSONObject referenceJson = new JSONObject();
		referenceJson.put("id", XMLUtils.getValueAtXPath(referenceElem, "./@ID"));
		referenceJson.put("type", XMLUtils.getValueAtXPath(referenceElem, "./@Type"));
		referenceJson.put("url", XMLUtils.getValueAtXPath(referenceElem, "./@URL"));
		referenceJson.put("dateTime", XMLUtils.getValueAtXPath(referenceElem, "./@DateTime"));
		referenceJson.put("id_Context", XMLUtils.getValueAtXPath(referenceElem, "./@ID_Context"));
		return referenceJson;

	}

	private static JSONObject getVendorInfoJSON(Element vendorInfoElem) {

		JSONObject vendorInfo = new JSONObject();

		vendorInfo.put("division", XMLUtils.getValueAtXPath(vendorInfoElem, "./@Division"));
		vendorInfo.put("vendorPrefCode", XMLUtils.getValueAtXPath(vendorInfoElem, "./@Code"));
		vendorInfo.put("vendorCodeContext", XMLUtils.getValueAtXPath(vendorInfoElem, "./@CodeContext"));
		vendorInfo.put("companyName", XMLUtils.getValueAtXPath(vendorInfoElem, "./@CompanyShortName"));
		return vendorInfo;
	}

	protected static JSONObject getVehicleInfo(Element vehicleElem) {

		JSONObject vehicleJson = new JSONObject();
		vehicleJson.put("transmissionType", XMLUtils.getValueAtXPath(vehicleElem, "./@TransmissionType"));
		vehicleJson.put("airConditionInd", XMLUtils.getValueAtXPath(vehicleElem, "./@AirConditionInd"));
		vehicleJson.put("baggageQuantity",
				Utils.convertToInt(XMLUtils.getValueAtXPath(vehicleElem, "./@BaggageQuantity"), 0));
		vehicleJson.put("passengerQuantity",
				Utils.convertToInt(XMLUtils.getValueAtXPath(vehicleElem, "./@PassengerQuantity"), 0));
		vehicleJson.put("driveType", XMLUtils.getValueAtXPath(vehicleElem, "./@DriveType"));
		vehicleJson.put("fuelType", XMLUtils.getValueAtXPath(vehicleElem, "./@FuelType"));
		
		String codeContext = TripIndicator.getEnumForValue(XMLUtils.getValueAtXPath(vehicleElem, "./@CodeContext"));
		vehicleJson.put(JSON_PROP_CODECONTEXT, codeContext);
		vehicleJson.put("code", XMLUtils.getValueAtXPath(vehicleElem, "./@Code"));

		// VehType
		vehicleJson.put(JSON_PROP_VEHICLECATEGORY, XMLUtils.getValueAtXPath(vehicleElem, "./ota:VehType/@VehicleCategory"));
		vehicleJson.put("doorType", XMLUtils.getValueAtXPath(vehicleElem, "./ota:VehType/@DoorType"));
		
		// VehClass
		vehicleJson.put("vehicleClassSize", XMLUtils.getValueAtXPath(vehicleElem, "./ota:VehClass/@Size"));
		// VehMakeModel
		vehicleJson.put(JSON_PROP_VEHMAKEMODELNAME, XMLUtils.getValueAtXPath(vehicleElem, "./ota:VehMakeModel/@Name"));
		vehicleJson.put(JSON_PROP_VEHMAKEMODELCODE, XMLUtils.getValueAtXPath(vehicleElem, "./ota:VehMakeModel/@Code"));
		// PictureURL
		vehicleJson.put("pictureUrl", XMLUtils.getValueAtXPath(vehicleElem, "./ota:PictureURL"));
		// VehicleAssetNumber
		vehicleJson.put("vehicleAssetNumber",
				XMLUtils.getValueAtXPath(vehicleElem, "./ota:VehIdentity/@VehicleAssetNumber"));

		return vehicleJson;

	}

	protected static JSONObject getPricingInfoJSON(Element vehAvailCoreElem, Element vehAvailInfoElem) {

		Element rentalRateElem = XMLUtils.getFirstElementAtXPath(vehAvailCoreElem, "./ota:RentalRate");
		
		JSONObject totalPriceJson = new JSONObject();
		JSONObject totalFareJson = new JSONObject();
		JSONArray taxArr = new JSONArray();
		String cCyCode = "";
		Element vehChargeElems[] = XMLUtils.getElementsAtXPath(rentalRateElem, "./ota:VehicleCharges/ota:VehicleCharge");
		BigDecimal totalTax = new BigDecimal(0);
		BigDecimal totalFees = new BigDecimal(0);
		for (Element vehChargeElem : vehChargeElems) {

			Element taxAmtsElems[] = XMLUtils.getElementsAtXPath(vehChargeElem, "./ota:TaxAmounts/ota:TaxAmount");
			JSONObject taxAmounts = null;
			for (Element taxAmntElem : taxAmtsElems) {

				BigDecimal taxAmt = Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(taxAmntElem, "./@Total"), 0);
				taxAmounts = new JSONObject();
				cCyCode = XMLUtils.getValueAtXPath(taxAmntElem, "./@CurrencyCode");
				taxAmounts.put(JSON_PROP_DESCRIPTION, XMLUtils.getValueAtXPath(taxAmntElem, "./@Description"));
				taxAmounts.put(JSON_PROP_CCYCODE, cCyCode);
				taxAmounts.put(JSON_PROP_TAXCODE, XMLUtils.getValueAtXPath(taxAmntElem, "./@TaxCode"));
				taxAmounts.put(JSON_PROP_AMOUNT, taxAmt);
				taxAmounts.put("percentage", Utils.convertToInt(XMLUtils.getValueAtXPath(taxAmntElem, "./@Percentage"), 0));
				totalTax = totalTax.add(taxAmt);
				taxArr.put(taxAmounts);
			}
		}
		JSONObject baseFareJson = new JSONObject();
		baseFareJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(
				XMLUtils.getValueAtXPath(vehAvailCoreElem, "./ota:TotalCharge/@RateTotalAmount"), 0));
		baseFareJson.put(JSON_PROP_CCYCODE,
				XMLUtils.getValueAtXPath(vehAvailCoreElem, "./ota:TotalCharge/@CurrencyCode"));
		totalFareJson.put(JSON_PROP_BASEFARE, baseFareJson);
		totalFareJson.put(JSON_PROP_AMOUNT, Utils.convertToBigDecimal(
				XMLUtils.getValueAtXPath(vehAvailCoreElem, "./ota:TotalCharge/@EstimatedTotalAmount"), 0));
		totalFareJson.put(JSON_PROP_CCYCODE,
				XMLUtils.getValueAtXPath(vehAvailCoreElem, "./ota:TotalCharge/@CurrencyCode"));
		totalFareJson.put("type", XMLUtils.getValueAtXPath(vehAvailCoreElem, "./ota:TotalCharge/@Type"));

		JSONObject taxes = new JSONObject();
		taxes.put(JSON_PROP_AMOUNT, totalTax);
		taxes.put(JSON_PROP_CCYCODE, cCyCode);
		taxes.put(JSON_PROP_TAX, taxArr);
		totalFareJson.put(JSON_PROP_TAXES, taxes);

		Element feesElems[] = XMLUtils.getElementsAtXPath(vehAvailCoreElem, "./ota:Fees/ota:Fee");
		JSONArray feeArr = new JSONArray();
		for (Element feesElem : feesElems) {

			BigDecimal feeAmt = Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(feesElem, "./@Amount"), 0);
			cCyCode = XMLUtils.getValueAtXPath(feesElem, "./@CurrencyCode");
			JSONObject feeJson = new JSONObject();
			feeJson.put(JSON_PROP_ISINCLDINBASE, XMLUtils.getValueAtXPath(feesElem, "./@IncludedInRate"));
			feeJson.put(JSON_PROP_CCYCODE, cCyCode);
			feeJson.put(JSON_PROP_FEECODE, XMLUtils.getValueAtXPath(feesElem, "./@FeeCode"));
			feeJson.put(JSON_PROP_AMOUNT, feeAmt);
			feeJson.put("purpose", XMLUtils.getValueAtXPath(feesElem, "./@Purpose"));
			feeJson.put(JSON_PROP_DESCRIPTION, XMLUtils.getValueAtXPath(feesElem, "./@Description"));
			totalFees = totalFees.add(feeAmt);
			feeArr.put(feeJson);
		}
		JSONObject feesJson = new JSONObject();
		feesJson.put(JSON_PROP_AMOUNT, totalFees);
		feesJson.put(JSON_PROP_CCYCODE, cCyCode);
		feesJson.put(JSON_PROP_FEE, feeArr);
		totalFareJson.put(JSON_PROP_FEES, feesJson);
		totalPriceJson.put(JSON_PROP_TOTALFARE, totalFareJson);
		
		return totalPriceJson;
		
	}

	private static JSONArray getPaymentRulesJSON(Element paymentRulesElem) {
		JSONArray paymentRules = new JSONArray();
		for (Element paymentrule : XMLUtils.getElementsAtXPath(paymentRulesElem, "./ota:PaymentRule")) {
			JSONObject paymentRule = new JSONObject();
			paymentRule.put("ruleType", XMLUtils.getValueAtXPath(paymentrule, "./@RuleType"));
			paymentRule.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(paymentrule, "./@CurrencyCode"));
			paymentRules.put(paymentRule);
		}
		return paymentRules;
	}

	public static String process(JSONObject reqJson) throws Exception {
		
		try {
			TrackingContext.setTrackingContext(reqJson);
			//OperationConfig opConfig = CarConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			ServiceConfig opConfig = CarConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
			Document ownerDoc = reqElem.getOwnerDocument();

			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_RENTAL);
			createHeader(reqHdrJson, reqElem);

			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
					"./cari:RequestHeader/com:SupplierCredentialsList");
			for (ProductSupplier prodSupplier : prodSuppliers) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}
			JSONObject carRentalReq = reqBodyJson.getJSONObject(JSON_PROP_CARRENTALARR);

			populateRequestBody(reqElem, ownerDoc, carRentalReq);

			Element resElem = null;
			//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), CarConfig.getHttpHeaders(), reqElem);
			resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
			if (resElem == null) {
				throw new Exception("Null response received from SI");
			}

			JSONObject resBodyJson = new JSONObject();
			JSONArray vehicleAvailJsonArr = new JSONArray();
			Element[] wrapperElems = XMLUtils.getElementsAtXPath(resElem, "./cari:ResponseBody/car:OTA_VehAvailRateRSWrapper");

			JSONArray resCarRentalsArr = new JSONArray();
			for (Element wrapperElem : wrapperElems) {
				Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_VehAvailRateRS/ota:VehAvailRSCore");
				getSupplierResponseVehicleAvailJSON(resBodyElem, vehicleAvailJsonArr);
			}

			for (int i = 0; i < vehicleAvailJsonArr.length(); i++) {
				JSONObject vehicleAvail = vehicleAvailJsonArr.getJSONObject(i);
				vehicleAvail.put(JSON_PROP_PACKAGE, carRentalReq.optString(JSON_PROP_PACKAGE));
				vehicleAvail.put(JSON_PROP_BOOKINGDURATION, deduceBookingDays(carRentalReq));
				vehicleAvail.put(JSON_PROP_TRIPTYPE, carRentalReq.optString(JSON_PROP_TRIPTYPE));
				vehicleAvail.put(JSON_PROP_TRIPINDC, carRentalReq.optString(JSON_PROP_TRIPINDC));
			}
			
			resCarRentalsArr.put(new JSONObject().put(JSON_PROP_VEHICLEAVAIL, vehicleAvailJsonArr));
			resBodyJson.put(JSON_PROP_CARRENTALARR, resCarRentalsArr);

			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
			resJson.put(JSON_PROP_RESBODY, resBodyJson);
			logger.trace(String.format("SI Transformed JSON Response = %s", resJson.toString()));

			Map<String, Integer> suppResToBRIIndex = new HashMap<String, Integer>();
			// Call BRMS Supplier and Client Commercials
			JSONObject resSupplierJson = SupplierCommercials.getSupplierCommercials(CommercialsOperation.Search, reqJson, resJson, suppResToBRIIndex);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resSupplierJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Supplier Commercials calculation engine: %s",
						resSupplierJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}
			JSONObject resClientJson = ClientCommercials.getClientCommercialsV2(reqJson, resJson, resSupplierJson);
			if (BRMS_STATUS_TYPE_FAILURE.equals(resClientJson.getString(JSON_PROP_TYPE))) {
				logger.error(String.format("A failure response was received from Client Commercials calculation engine: %s",
								resClientJson.toString()));
				return getEmptyResponse(reqHdrJson).toString();
			}

			calculatePrices(reqJson, resJson, resSupplierJson, resClientJson, suppResToBRIIndex, true, usrCtx);
			
			TaxEngine.getCompanyTaxes(reqJson, resJson);
			pushSuppFaresToRedisAndRemove(resJson);
			// Apply company offers
	        CompanyOffers.getCompanyOffers(reqJson, resJson, OffersType.COMPANY_SEARCH_TIME);
			
			return resJson.toString();
		} catch (Exception x) {
			x.printStackTrace();
			return "{\"status\": \"ERROR\"}";
		}
	}

	static void populateRequestBody(Element reqElem, Document ownerDoc, JSONObject carRentalReq) throws Exception {
		
		TripIndicator tripIndc = TripIndicator.forString(carRentalReq.getString(JSON_PROP_TRIPINDC));
		// TODO : Need To Change later
		Element sourceElem = XMLUtils.getFirstElementAtXPath(reqElem,
				"./cari:RequestBody/ota:OTA_VehAvailRateRQ/ota:POS/ota:Source");
		sourceElem.setAttribute("ISOCurrency", "INR");
		sourceElem.setAttribute("ISOCountry", "IN");

		Element vehAvailCoreElem = XMLUtils.getFirstElementAtXPath(reqElem,
				"./cari:RequestBody/ota:OTA_VehAvailRateRQ/ota:VehAvailRQCore");
		// JSONObject vehRentalObj = reqBodyJson.getJSONObject(JSON_PROP_VEHRENTAL);
		Element vehRentalElem = getVehRentalCoreElement(ownerDoc, carRentalReq);

		Element VehPrefElem = XMLUtils.getFirstElementAtXPath(reqElem,
				"./cari:RequestBody/ota:OTA_VehAvailRateRQ/ota:VehAvailRQCore/ota:VehPrefs/ota:VehPref");
		if (Utils.isStringNotNullAndNotEmpty(carRentalReq.optString(JSON_PROP_VEHICLECATEGORY))) {
			Element vehType = ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehType");
			vehType.setAttribute("VehicleCategory", carRentalReq.getString(JSON_PROP_VEHICLECATEGORY));
			VehPrefElem.appendChild(vehType);
		}
		
		VehPrefElem.setAttribute("CodeContext", tripIndc.toString());
		vehAvailCoreElem.insertBefore(vehRentalElem, VehPrefElem.getParentNode());

		Element driverage = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DriverType");
		driverage.setAttribute("Age", CARRENTAL_DRIVER_AGE);
		vehAvailCoreElem.appendChild(driverage);

		Element Elem = RentalSearchProcessor.getBookingDetailsElement(ownerDoc, carRentalReq);
		vehAvailCoreElem.appendChild(Elem);
	

		JSONArray customerJsonArr = carRentalReq.optJSONArray(JSON_PROP_PAXDETAILS);
		Element vehAvailRQInfoElem = XMLUtils.getFirstElementAtXPath(reqElem,
				"./cari:RequestBody/ota:OTA_VehAvailRateRQ/ota:VehAvailRQInfo");
		if (customerJsonArr != null && customerJsonArr.length() != 0) {

			Element customerElem = populateCustomerElement(ownerDoc, customerJsonArr);
			vehAvailRQInfoElem.appendChild(customerElem);
		}

	}

	static void pushSuppFaresToRedisAndRemove(JSONObject resJson) {
		
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
			String redisKey = String.format("%s%c%s", resHdrJson.optString(JSON_PROP_SESSIONID), KEYSEPARATOR, PROD_CATEG_SUBTYPE_RENTAL);
			redisConn.hmset(redisKey, reprcSuppFaresMap);
			redisConn.pexpire(redisKey, (long) (CarConfig.getRedisTTLMinutes() * 60 * 1000));
			}
			//RedisConfig.releaseRedisConnectionToPool(redisConn);
		}
	}
	
	protected static Element populateCustomerElement(Document ownerDoc, JSONArray customerJsonArr) {
		Element customerElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Customer");
		for (int i = 0; i < customerJsonArr.length(); i++) {
			JSONObject customerJson = customerJsonArr.getJSONObject(i);
			JSONObject contactDetails = customerJson.getJSONArray(JSON_PROP_CONTACTDTLS).getJSONObject(0);
			JSONObject addressDetails = customerJson.getJSONObject(JSON_PROP_ADDRDTLS);
			Element customerType = ownerDoc.createElementNS(Constants.NS_OTA,
					customerJson.optBoolean(JSON_PROP_ISLEAD)==true ? "ota:Primary" : "ota:Additional");

			customerElem.appendChild(customerType);
			String temp;
			if (!(temp = customerJson.optString(JSON_PROP_GENDER)).isEmpty())
				customerType.setAttribute("Gender", temp);
			Element personNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PersonName");
			Element telephoneElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Telephone");
			Element addressElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Address");
			Element emailElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Email");
			customerType.appendChild(addressElem);
			customerType.insertBefore(emailElem, addressElem);
			customerType.insertBefore(telephoneElem, emailElem);
			customerType.insertBefore(personNameElem, telephoneElem);

			Element surname = ownerDoc.createElementNS(Constants.NS_OTA, "ota:Surname");
			surname.setTextContent(customerJson.optString(JSON_PROP_SURNAME));
			personNameElem.appendChild(surname);

			Element givenName = ownerDoc.createElementNS(Constants.NS_OTA, "ota:GivenName");
			givenName.setTextContent(customerJson.optString(JSON_PROP_FIRSTNAME));
			personNameElem.insertBefore(givenName, surname);

			Element namePrefix = ownerDoc.createElementNS(Constants.NS_OTA, "ota:NamePrefix");
			namePrefix.setTextContent(customerJson.optString(JSON_PROP_TITLE));
			personNameElem.insertBefore(namePrefix, givenName);

			emailElem.setTextContent(contactDetails.getString(JSON_PROP_EMAIL));

			if (!contactDetails.optString(JSON_PROP_AREACITYCODE).isEmpty())
				telephoneElem.setAttribute("AreaCityCode", contactDetails.getString(JSON_PROP_AREACITYCODE));
			if (!contactDetails.optString(JSON_PROP_MOBILENBR).isEmpty())
				telephoneElem.setAttribute("PhoneNumber", contactDetails.getString(JSON_PROP_MOBILENBR));

			Element cityNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CityName");
			cityNameElem.setTextContent(addressDetails.optString(JSON_PROP_CITY));
			addressElem.appendChild(cityNameElem);

			Element postalCodeElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PostalCode");
			postalCodeElem.setTextContent(addressDetails.optString(JSON_PROP_ZIP));
			addressElem.appendChild(postalCodeElem);

			Element stateProv = ownerDoc.createElementNS(NS_OTA, "ota:StateProv");
			stateProv.setAttribute("StateCode", addressDetails.getString(JSON_PROP_STATE));
			addressElem.appendChild(stateProv);

			Element countryNameElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:CountryName");
			if (!addressDetails.optString(JSON_PROP_COUNTRY).isEmpty())
				countryNameElem.setAttribute("Code", addressDetails.getString(JSON_PROP_COUNTRY));
			addressElem.appendChild(countryNameElem);

			Element addressLineElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:AddressLine");
			addressLineElem.setTextContent(addressDetails.optString(JSON_PROP_ADDRLINE1));
			addressElem.insertBefore(addressLineElem, cityNameElem);
			if (!addressDetails.optString(JSON_PROP_ADDRLINE2).equals("")) {
				addressLineElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:AddressLine");
				addressLineElem.setTextContent(addressDetails.optString(JSON_PROP_ADDRLINE2));
				addressElem.insertBefore(addressLineElem, cityNameElem);
			}
		}
		return customerElem;
	}

	protected static Element getVehRentalCoreElement(Document ownerDoc, JSONObject carRentalReq) throws Exception {

		TripType tripType = TripType.forString(carRentalReq.optString(JSON_PROP_TRIPTYPE));
		Element vehRentalElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:VehRentalCore");
		vehRentalElem.setAttribute("PickUpDateTime", carRentalReq.getString(JSON_PROP_PICKUPDATE));

		Element pickUpLocElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PickUpLocation");
		pickUpLocElem.setAttribute("LocationCode", carRentalReq.getString(JSON_PROP_PICKUPLOCCODE));
		vehRentalElem.appendChild(pickUpLocElem);

		if (Utils.isStringNotNullAndNotEmpty(carRentalReq.optString(JSON_PROP_RETURNLOCCODE))) {
			Element returnLocElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:ReturnLocation");
			returnLocElem.setAttribute("LocationCode", carRentalReq.getString(JSON_PROP_RETURNLOCCODE));
			vehRentalElem.appendChild(returnLocElem);
		}
		
		if(TripType.RETURN.equals(tripType))
			vehRentalElem.setAttribute("ReturnDateTime", deduceReturnDateTime(carRentalReq));
			

		if (Utils.isStringNotNullAndNotEmpty(carRentalReq.optString(JSON_PROP_TRIPTYPE))) {
			
			//TODO : May need to change this later
			//In case of Local Rental, tripType does not matter. OneWayIndicator should be set empty.
			//Setting it to true and false in case of local changes the response.
			vehRentalElem.setAttribute("OneWayIndicator", tripType.equals(TripType.ONEWAY) ? "true" : "false");
			
		}
		return vehRentalElem;
	}

	private static String deduceReturnDateTime(JSONObject carRentalReq) throws Exception{
		
		if (Utils.isStringNotNullAndNotEmpty(carRentalReq.optString(JSON_PROP_RETURNDATE))) {
			return carRentalReq.getString(JSON_PROP_RETURNDATE);
		}
		
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		Date pickUpdate = formatter.parse(carRentalReq.getString(JSON_PROP_PICKUPDATE));
		Calendar cal = Calendar.getInstance();
		cal.setTime(pickUpdate);
		cal.add(Calendar.DAY_OF_MONTH, carRentalReq.optInt(JSON_PROP_BOOKINGDURATION)-1);
		return formatter.format(cal.getTime());
	}

	protected static Element getBookingDetailsElement(Document ownerDoc, JSONObject carRentalReq) {

		Element tpaExtensions = ownerDoc.createElementNS(Constants.NS_OTA, "ota:TPA_Extensions");
		Element bookingDetails = ownerDoc.createElementNS(NS_CAR, "car:BookingDetails");
		Element bookingDetail = ownerDoc.createElementNS(NS_CAR, "car:BookingDetail");
		
		TripIndicator tripIndc = TripIndicator.forString(carRentalReq.getString(JSON_PROP_TRIPINDC));
		boolean isOutstation = tripIndc.equals(TripIndicator.Outstation);
		
		//LOCAL - duration_unit is 'PACKAGE_STRING'
		//OUTSTATION - duration_unit is 'DAYS'
		Unit unit = isOutstation ? Unit.DAYS : Unit.PACKAGE_STRING;
		int days = deduceBookingDays(carRentalReq);
		bookingDetail.setAttribute("duration_unit", unit.toString());
		bookingDetail.setAttribute("duration_value", isOutstation ? String.valueOf(days) : carRentalReq.optString(JSON_PROP_PACKAGE));
		
		bookingDetails.appendChild(bookingDetail);
		tpaExtensions.appendChild(bookingDetails);
		return tpaExtensions;
	}

	 static int deduceBookingDays(JSONObject carRentalReq) {
		
		int daysCount = carRentalReq.optInt(JSON_PROP_BOOKINGDURATION);
		if(daysCount!=0)
			return daysCount;
		
		String pickUpDate = carRentalReq.optString(JSON_PROP_PICKUPDATE);
		String returnDate =  carRentalReq.optString(JSON_PROP_RETURNDATE);
		
		daysCount = GetDateRange.getDateRange(pickUpDate, returnDate, true, true).size();
		return daysCount!=0 ? daysCount : 1;
	}
	 
	static String deduceDropOffCity(JSONObject vehicleAvailJson) {
	    	
    	String codeContext = vehicleAvailJson.optString(JSON_PROP_TRIPINDC);
    	TripIndicator tripIndc = TripIndicator.forString(codeContext);
    	
    	if(tripIndc.equals(TripIndicator.Local))
    		return vehicleAvailJson.getString(JSON_PROP_PICKUPLOCCODE);
    	else
    		return vehicleAvailJson.getString(JSON_PROP_RETURNLOCCODE);
    	
	}

	public static void calculatePrices(JSONObject reqJson, JSONObject resJson, JSONObject suppCommResJson,
			JSONObject clientCommResJson, Map<String, Integer> suppResToBRIIndex, boolean retainSuppFares,
			UserContext usrCtx) {

		JSONArray resCarRentalInfoArr = resJson.getJSONObject(JSON_PROP_RESBODY).optJSONArray(JSON_PROP_CARRENTALARR);
		Map<String, String> suppCommToTypeMap = getSupplierCommercialsAndTheirType(suppCommResJson);
		Map<String, String> clntCommToTypeMap = getClientCommercialsAndTheirType(clientCommResJson);
		
		ClientType clientType = ClientType.valueOf(reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
		String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
		String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
		Map<Integer, JSONArray> ccommSuppBRIJsonMap = getBRIWiseVehicleDetailsFromClientCommercials(clientCommResJson);

		for (int y = 0; y < resCarRentalInfoArr.length(); y++) {
			Map<Integer, Integer> suppIndexMap = new HashMap<Integer, Integer>();

			JSONArray resVehicleAvailJsonArr = resCarRentalInfoArr.getJSONObject(y).getJSONArray(JSON_PROP_VEHICLEAVAIL);
			for (int i = 0; i < resVehicleAvailJsonArr.length(); i++) {
				JSONObject resVehicleAvailJson = resVehicleAvailJsonArr.getJSONObject(i);
				
				JSONObject totalPriceInfoJson = resVehicleAvailJson.getJSONObject(JSON_PROP_TOTALPRICEINFO);
				JSONObject totalFareJson = totalPriceInfoJson.getJSONObject(JSON_PROP_TOTALFARE);
				String suppCcyCode = totalFareJson.getString(JSON_PROP_CCYCODE);
				JSONObject suppTotalFareJson = new JSONObject(totalFareJson.toString());
				JSONObject suppBaseFareJson = suppTotalFareJson.getJSONObject(JSON_PROP_BASEFARE);
				
				String suppID = resVehicleAvailJson.getString(JSON_PROP_SUPPREF);
				Integer briNo = suppResToBRIIndex.get(String.format("%d%c%d", y, KEYSEPARATOR, i));
				
				// Required for search response,VehicleAvail Objects mapped to Different BRI's
				int idx = (suppIndexMap.containsKey(briNo)) ? (suppIndexMap.get(briNo) + 1) : 0;
				suppIndexMap.put(briNo, idx);
				
				JSONObject ccommVehDtlsJson = (ccommSuppBRIJsonMap.containsKey(briNo)) ? ccommSuppBRIJsonMap.get(briNo).getJSONObject(idx) : null;
	    		if (ccommVehDtlsJson == null) {
	    			logger.info(String.format("BRMS/ClientComm: \"businessRuleIntake\" JSON element for supplier %s not found.", suppID));
	    		}
	    		
				// Even though the name is PassengerDetails, commericals are applied on vehicles
				PriceComponentsGroup totalFareCompsGroup = new PriceComponentsGroup(JSON_PROP_TOTALFARE, clientCcyCode, new BigDecimal(0), true);
				PriceComponentsGroup totalIncentivesGroup = new PriceComponentsGroup(JSON_PROP_INCENTIVES, clientCcyCode, BigDecimal.ZERO, true);
				// First passengerDetails Object is assumed to have a car Price breakup.
				// It won't repeat
				JSONObject ccommVehPsgrDtlJson = ccommVehDtlsJson.getJSONArray(JSON_PROP_PSGRDETAILS).optJSONObject(0);
				JSONArray clientEntityCommJsonArr = null;
				if (ccommVehPsgrDtlJson == null) {
					logger.info(String.format("Passenger details not found in client commercial vehicleDetails"));
				}
				else {
        			if (retainSuppFares) {
        				JSONObject suppPricingInfo = new JSONObject();
    					suppPricingInfo.put(JSON_PROP_TOTALFARE, suppTotalFareJson);
        				appendSupplierCommercialsForCar(suppPricingInfo, suppTotalFareJson, ccommVehPsgrDtlJson, suppID, suppCcyCode, suppCommToTypeMap);
        				resVehicleAvailJson.put(JSON_PROP_SUPPPRICEINFO, suppPricingInfo);
        			}
        			// From the passenger type client commercial JSON, retrieve calculated client entity commercials
        			clientEntityCommJsonArr = ccommVehPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
        			if (clientEntityCommJsonArr == null) {
        				logger.warn("Client commercials calculations not found");
        			}
    			}

				// Reference CKIL_231556 The display price will be calculated as -
				// (Total Supplier Price + Markup + Additional Company Receivable Commercials)
				
				JSONObject markupCalcJson = null;
				JSONArray clientCommercials= new JSONArray();
				PriceComponentsGroup totalReceivablesCompsGroup = null;
				for (int k = 0; clientEntityCommJsonArr != null && k < clientEntityCommJsonArr.length(); k++) {
					
					JSONArray clientEntityCommercialsJsonArr=new JSONArray();
					JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(k);

					// TODO: In case of B2B, do we need to add markups for all client hierarchy levels?
					if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) {
    					markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);
    					clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(markupCalcJson, clntCommToTypeMap, suppCcyCode));
    				}
					//Additional commercialcalc clientCommercial
    				// TODO: In case of B2B, do we need to add additional receivable commercials for all client hierarchy levels?
					JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
					JSONArray retentionCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_RETENTIONCOMMDETAILS);
					JSONArray fixedCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_FIXEDCOMMDETAILS);
					
					// If totals of receivables at all levels is required, the following instance creation needs to move where
    				// variable 'totalReceivablesCompsGroup' is declared i.e outside loop
					totalReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode, new BigDecimal(0), true);
					if (additionalCommsJsonArr != null) {
					for (int p = 0; p < additionalCommsJsonArr.length(); p++) {
							JSONObject additionalCommsJson = additionalCommsJsonArr.getJSONObject(p);
							String additionalCommName = additionalCommsJson.optString(JSON_PROP_COMMNAME);
							String additionalCommType = clntCommToTypeMap.get(additionalCommName);
							clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(additionalCommsJson, clntCommToTypeMap, suppCcyCode));
							
							if (COMM_TYPE_RECEIVABLE.equals(additionalCommType)) {
								String additionalCommCcy = additionalCommsJson.optString(JSON_PROP_COMMCCY);
								BigDecimal additionalCommAmt = additionalCommsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket));
								totalReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt);
							}
						}
					}
					//Retention Commercials
					for (int p = 0; retentionCommsJsonArr!=null && p < retentionCommsJsonArr.length(); p++) {
						JSONObject retentionCommsJson = retentionCommsJsonArr.getJSONObject(p);
						clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(retentionCommsJson, clntCommToTypeMap, suppCcyCode));
					}
					
					//Fixed Commercials
					for (int p = 0; fixedCommsJsonArr!=null && p < fixedCommsJsonArr.length(); p++) {
						JSONObject fixedCommsJson = fixedCommsJsonArr.getJSONObject(p);
						clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(fixedCommsJson, clntCommToTypeMap, suppCcyCode));
					}
					
					JSONObject clientEntityDetailsJson = new JSONObject();
    				ClientInfo[] clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchyArray();
    				clientEntityDetailsJson.put(JSON_PROP_CLIENTID, clientEntityDetailsArr[k].getClientId());
    				clientEntityDetailsJson.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsArr[k].getParentClienttId());
    				clientEntityDetailsJson.put(JSON_PROP_COMMENTITYTYPE, clientEntityDetailsArr[k].getCommercialsEntityType());
    				clientEntityDetailsJson.put(JSON_PROP_COMMENTITYID, clientEntityDetailsArr[k].getCommercialsEntityId());
    				clientEntityDetailsJson.put(JSON_PROP_CLIENTCOMMTOTAL, clientEntityCommercialsJsonArr);
    				clientCommercials.put(clientEntityDetailsJson);
    				
    				// For B2B clients, the incentives of the last client hierarchy level should be accumulated and returned in the response.
        			if (k == (clientEntityCommJsonArr.length() - 1)) {
        				for (int x = 0; x < clientEntityCommercialsJsonArr.length(); x++) {
        					JSONObject clientEntityCommercialsJson = clientEntityCommercialsJsonArr.getJSONObject(x);
        					if (COMM_TYPE_PAYABLE.equals(clientEntityCommercialsJson.getString(JSON_PROP_COMMTYPE))) {
        						String commCcy = clientEntityCommercialsJson.getString(JSON_PROP_COMMCCY);
        						String commName = clientEntityCommercialsJson.getString(JSON_PROP_COMMNAME);
        						BigDecimal commAmt = clientEntityCommercialsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(commCcy, clientCcyCode, clientMarket));
        						totalIncentivesGroup.add(String.format(mIncvPriceCompFormat, commName), clientCcyCode, commAmt);
        					}
        				}
        			}
				}
				
				BigDecimal baseFareAmt = suppBaseFareJson.getBigDecimal(JSON_PROP_AMOUNT);
				JSONArray ccommTaxDetailsJsonArr = null; 
				if (markupCalcJson != null) {
					JSONObject fareBreakupJson = markupCalcJson.optJSONObject(JSON_PROP_FAREBRKUP);
					if (fareBreakupJson != null) {
						baseFareAmt = fareBreakupJson.optBigDecimal(JSON_PROP_BASEFARE_COMM, baseFareAmt);
						ccommTaxDetailsJsonArr = fareBreakupJson.optJSONArray(JSON_PROP_TAXDETAILS);
					}
				}
				
				JSONObject baseFareJson = new JSONObject();
				baseFareJson.put(JSON_PROP_AMOUNT, baseFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
				baseFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);
				totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode, baseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
				int offset = 0;
				getCommercialPricesTaxesJson(totalFareJson, ccommTaxDetailsJsonArr, offset, totalFareCompsGroup, clientCcyCode, clientMarket);
				// If amount of receivables group is greater than zero, then append to commercial prices
				if (totalReceivablesCompsGroup != null && totalReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
					totalFareCompsGroup.addSubComponent(totalReceivablesCompsGroup, null);
				}
				//TODO : Whether to keep Fees in the final WEM response.
				// Fees from SI is actually a breakUp of BaseFare, so ideally no need.
				totalPriceInfoJson.put(JSON_PROP_TOTALFARE, totalFareCompsGroup.toJSON());
		
				if ( clientType == ClientType.B2B) {
					totalPriceInfoJson.put(JSON_PROP_INCENTIVES, totalIncentivesGroup.toJSON());
	    		}
				
				if (retainSuppFares) {
					totalPriceInfoJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, clientCommercials);
				}
			}
		}
		logger.trace(String.format("supplierResponse after supplierItinFare = %s", resJson.toString()));
	}


	private static void appendSupplierCommercialsForCar(JSONObject suppPricingInfo, JSONObject suppTotalFareJson, JSONObject ccommVehPsgrDtlJson,
			String suppID, String suppCcyCode, Map<String, String> suppCommToTypeMap) {
		
		JSONArray suppCommJsonArr = new JSONArray();
		JSONArray ccommSuppCommJsonArr = ccommVehPsgrDtlJson.optJSONArray(JSON_PROP_COMMDETAILS);
		// If no supplier commercials have been defined in BRMS, the JSONArray for ccommSuppCommJsonArr will be null.
		// In this case, log a message and proceed with other calculations.
		if (ccommSuppCommJsonArr == null) {
			logger.warn(String.format("No supplier commercials found for supplier %s", suppID));
			return;
		} 
		
		for (int x = 0; x < ccommSuppCommJsonArr.length(); x++) {
			String temp;
			JSONObject ccommSuppCommJson = ccommSuppCommJsonArr.getJSONObject(x);
			JSONObject suppCommJson = new JSONObject();
			suppCommJson.put(JSON_PROP_COMMNAME, ccommSuppCommJson.getString(JSON_PROP_COMMNAME));
			suppCommJson.put(JSON_PROP_COMMTYPE,
					suppCommToTypeMap.get(ccommSuppCommJson.getString(JSON_PROP_COMMNAME)));
			suppCommJson.put(JSON_PROP_COMMAMOUNT, ccommSuppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
			suppCommJson.put(JSON_PROP_COMMCCY, (temp = ccommSuppCommJson.optString(JSON_PROP_COMMCCY)).equals("") ? suppCcyCode : temp);
			suppCommJsonArr.put(suppCommJson);
		}
		suppPricingInfo.put(JSON_PROP_SUPPCOMM, suppCommJsonArr);
	}

	private static void getCommercialPricesTaxesJson(JSONObject totalFareJson, JSONArray ccommTaxDetailsJsonArr,
			int offset, PriceComponentsGroup totalFareCompsGroup, String clientCcyCode, String clientMarket) {

		String suppCcyCode = totalFareJson.getString(JSON_PROP_CCYCODE);
		JSONArray taxesJsonArr = totalFareJson.getJSONObject(JSON_PROP_TAXES).optJSONArray(JSON_PROP_TAX);
		JSONObject taxJson = null;
		String taxCode = null;
		JSONObject taxesJson = new JSONObject();
		for (int i = 0; taxesJsonArr!=null && i < taxesJsonArr.length(); i++) {
			taxesJson = taxesJsonArr.getJSONObject(i);
			suppCcyCode = taxesJson.getString(JSON_PROP_CCYCODE);
			taxCode =  taxesJson.getString(JSON_PROP_TAXCODE);
			taxJson = taxesJson;
			JSONObject ccommTaxDetailJson = (ccommTaxDetailsJsonArr != null) ? ccommTaxDetailsJsonArr.optJSONObject(offset + i) : null;
			if (ccommTaxDetailJson != null) {
				// If tax JSON is found in commercials, replace existing tax details with one from commercials
				taxJson = new JSONObject();
				BigDecimal taxAmt = ccommTaxDetailJson.getBigDecimal(JSON_PROP_TAXVALUE);
				taxJson.put(JSON_PROP_TAXCODE, taxCode);
				taxJson.put(JSON_PROP_AMOUNT, taxAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket)));
				taxJson.put(JSON_PROP_CCYCODE, clientCcyCode);
			}
			totalFareCompsGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXCODE), clientCcyCode, taxJson.getBigDecimal(JSON_PROP_AMOUNT));
		}

	}

	static JSONArray getSupplierCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
		return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results")
				.getJSONObject(0).getJSONObject("value")
				.getJSONObject("cnk.carrentals_commercialscalculationengine.suppliertransactionalrules.Root")
				.getJSONArray("businessRuleIntake");
	}

	static JSONArray getClientCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
		return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results")
				.getJSONObject(0).getJSONObject("value")
				.getJSONObject("cnk.carrentals_commercialscalculationengine.clienttransactionalrules.Root")
				.getJSONArray("businessRuleIntake");
	}

	/*
	 * static Map<String, String> getSupplierCommercialsAndTheirType(JSONObject
	 * scommBRIJson) { //
	 * ---------------------------------------------------------------------- //
	 * Retrieve commercials head array from supplier commercials and find type //
	 * (Receivable, Payable) for commercials JSONArray commHeadJsonArr = null;
	 * JSONObject commHeadJson = null; Map<String, String> commToTypeMap = new
	 * HashMap<String, String>(); commHeadJsonArr =
	 * scommBRIJson.optJSONArray(JSON_PROP_COMMHEAD); if (commHeadJsonArr == null) {
	 * logger.warn("No commercial heads found in supplier commercials"); return new
	 * HashMap<String, String>(); }
	 * 
	 * for (int j = 0; j < commHeadJsonArr.length(); j++) { commHeadJson =
	 * commHeadJsonArr.getJSONObject(j);
	 * commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME),
	 * commHeadJson.getString(JSON_PROP_COMMTYPE)); } return commToTypeMap; }
	 */

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

	static Map<String, Map<String, String>> getClientCommercialsAndTheirTypeFor(JSONObject ccommBRIJson) {
		// ----------------------------------------------------------------------
		// Retrieve commercials head array from client commercials and find type
		// (Receivable, Payable) for each commercials Entity
		JSONArray commHeadJsonArr = null;
		Map<String, Map<String, String>> entityToCommHeadMap = new HashMap<String, Map<String, String>>();
		JSONArray cCommEntityDetails = ccommBRIJson.optJSONArray("entityDetails");
		if (cCommEntityDetails == null) {
			logger.warn("No EntityDetails found in client commercials");
			return new HashMap<String, Map<String, String>>();
		}
		for (int i = 0; i < cCommEntityDetails.length(); i++) {
			Map<String, String> commToTypeMap = new HashMap<String, String>();
			JSONObject cCommEntityJson = cCommEntityDetails.getJSONObject(i);
			commHeadJsonArr = cCommEntityJson.optJSONArray(JSON_PROP_COMMHEAD);
			if (commHeadJsonArr == null) {
				logger.warn("No commercial heads found in client commercials");
				continue;
			}
			JSONObject commHeadJson = null;
			for (int j = 0; j < commHeadJsonArr.length(); j++) {
				commHeadJson = commHeadJsonArr.getJSONObject(j);
				commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME),
						commHeadJson.getString(JSON_PROP_COMMTYPE));
			}
			entityToCommHeadMap.put(cCommEntityJson.optString("entityName"), commToTypeMap);
		}
		return entityToCommHeadMap;
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
	
	 private static Map<Integer, JSONArray> getBRIWiseVehicleDetailsFromClientCommercials(JSONObject clientCommResJson) {
		 
    	JSONArray ccommSuppBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
    	Map<Integer, JSONArray> ccommSuppBRIJsonMap = new HashMap<Integer, JSONArray>();
		Integer briNo = 1;
		for (int i = 0; i < ccommSuppBRIJsonArr.length(); i++) {
			JSONObject ccommSuppBRIJson = ccommSuppBRIJsonArr.getJSONObject(i);
			// Getting SupplierCommericals BRI Since order is preserved
			JSONArray ccommVehDtlsJsonArr = ccommSuppBRIJson.getJSONArray(JSON_PROP_VEHICLEDETAILS);
			ccommSuppBRIJsonMap.put(briNo, ccommVehDtlsJsonArr);
			briNo++;
		}
    	return ccommSuppBRIJsonMap;
    }
 
	private static JSONObject convertToClientEntityCommercialJson(JSONObject clientCommJson, Map<String,String> clntCommToTypeMap, String suppCcyCode) {
    	JSONObject clientCommercial= new JSONObject();
    	String commercialName = clientCommJson.getString(JSON_PROP_COMMNAME);
		clientCommercial.put(JSON_PROP_COMMTYPE, clntCommToTypeMap.getOrDefault(commercialName, "?"));
		clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
		clientCommercial.put(JSON_PROP_COMMNAME, clientCommJson.getString(JSON_PROP_COMMNAME));
		clientCommercial.put(JSON_PROP_COMMCCY, clientCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
    	return clientCommercial;
    }
	
	public static JSONObject getEmptyResponse(JSONObject reqHdrJson) {
		JSONObject resJson = new JSONObject();
		resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		resJson.put(JSON_PROP_RESBODY, new JSONObject());
		return resJson;
	}
}
