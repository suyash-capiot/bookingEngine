package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class HolidaysUtil implements HolidayConstants {

	
	public static JSONArray holidaysErrorHandler(Element oTA_DynamicPkgAvailRS) {
		Element errorElem[] = XMLUtils.getElementsAtXPath(oTA_DynamicPkgAvailRS, "./ns:Errors/ns:Error");
		JSONArray errorArray = new JSONArray();
		if(errorElem.length != 0) {
			for(Element error : errorElem) {
				JSONObject errorJSON = new JSONObject();
			String shortText = String.valueOf(XMLUtils.getValueAtXPath(error, "./@ShortText"));
			String type = String.valueOf(XMLUtils.getValueAtXPath(error, "./@Type"));
			String code = String.valueOf(XMLUtils.getValueAtXPath(error, "./@Code"));
			String status = String.valueOf(XMLUtils.getValueAtXPath(error, "./@Status"));
			errorJSON.put("shortText", shortText);
			errorJSON.put("errorType", type);
			errorJSON.put("errorCode", code);
			errorJSON.put("status", status);
			errorArray.put(errorJSON);
			}
		}
		
		return errorArray;
	}
	
	//13-04-18 Added to handle the scenario when SI errors out (Rahul)
	public static void SIErrorHandler(Element oTA_wrapperElem, JSONObject errorJSON) {
		Element oTA_DynamicPkgAvailRS = XMLUtils.getFirstElementAtXPath(oTA_wrapperElem,"./ns:OTA_DynamicPkgAvailRS");
		if (oTA_DynamicPkgAvailRS == null)
			oTA_DynamicPkgAvailRS = XMLUtils.getFirstElementAtXPath(oTA_wrapperElem, "./ns:OTA_DynamicPkgBookRS");
		Element supplierError = XMLUtils.getFirstElementAtXPath(oTA_DynamicPkgAvailRS,"./ns:Errors");
		if(oTA_DynamicPkgAvailRS==null) {
			Element errorElem1 = XMLUtils.getFirstElementAtXPath(oTA_wrapperElem, "./com:Error");
			//JSONObject errorJSON = new JSONObject();
			
			if(errorElem1!=null) {
				String errorCode = String.valueOf(XMLUtils.getValueAtXPath(errorElem1, "./com:ErrorCode"));
				String errorMsg = String.valueOf(XMLUtils.getValueAtXPath(errorElem1, "./com:ErrorMsg"));
				String errorDetails = String.valueOf(XMLUtils.getValueAtXPath(errorElem1, "./com:ErrorDetails"));
				
				errorJSON.put("errorCode", errorCode);
				errorJSON.put("errorMsg", errorMsg);
				errorJSON.put("errorDetails", errorDetails);
			}
			else {
				errorJSON.put("error:", "Neither SI nor Supplier Error");
			}
		}
		else if (supplierError != null)
		{
			errorJSON.put("supplierErrors:",HolidaysUtil.holidaysErrorHandler(oTA_DynamicPkgAvailRS));
		}
		
		
	}
	
	public static JSONArray createNewPriceArr(JSONObject componentJson, Map<String, JSONObject> priceMap) {

		String rateDescriptionText;
		JSONArray newPriceArr = new JSONArray();
		Map<String, BigDecimal> taxMap = new HashMap<String, BigDecimal>();
		
		BigDecimal totalExtrasAmountAfterTax = new BigDecimal("0"), totalExtrasAmountBeforeTax = new BigDecimal("0"),
				totalTransferAmountAfterTax = new BigDecimal("0"), totalTransferAmountBeforeTax = new BigDecimal("0"),
				totalInsuranceAmountBeforeTax = new BigDecimal("0"), totalInsuranceAmountAfterTax = new BigDecimal("0"),
				totalTransferTaxes = new BigDecimal("0"),totalExtraTaxes = new BigDecimal("0"),totalInsuranceTaxes = new BigDecimal("0");

		// Map containing unique values of priceArray
		
			JSONArray priceArray = componentJson.getJSONArray(JSON_PROP_PRICE);
			for (int b = 0; b < priceArray.length(); b++) {
				JSONObject priceJson = priceArray.getJSONObject(b);
				rateDescriptionText = priceJson.getJSONObject(JSON_PROP_RATEDESC).getString(JSON_PROP_TEXT);

				BigDecimal amountAfterTax = priceJson.getJSONObject("total").optBigDecimal(JSON_PROP_AMOUNTAFTERTAX,
						new BigDecimal("0"));
				BigDecimal amountBeforeTax = priceJson.getJSONObject("total").optBigDecimal(JSON_PROP_AMOUNTBEFORETAX,
						new BigDecimal("0"));
				BigDecimal amountBeforeTaxBase = priceJson.getJSONObject("base")
						.optBigDecimal(JSON_PROP_AMOUNTBEFORETAX, new BigDecimal("0"));
				
				JSONObject totalJson = priceJson.getJSONObject("total");
				JSONObject baseJson = priceJson.getJSONObject("base");
				JSONObject taxes = totalJson.getJSONObject(JSON_PROP_TAXES);
				BigDecimal taxAmount = taxes.optBigDecimal(JSON_PROP_AMOUNT,new BigDecimal("0"));
				

				totalJson.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTax);
				totalJson.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTax);
				baseJson.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTaxBase);
				taxes.put(JSON_PROP_AMOUNT,taxAmount);
				
				if (rateDescriptionText.contains("Transfer")) {
					
					totalTransferAmountAfterTax = totalTransferAmountAfterTax.add(amountAfterTax);
					totalTransferAmountBeforeTax = totalTransferAmountBeforeTax.add(amountAfterTax);
					totalTransferTaxes = totalTransferTaxes.add(taxAmount);
					totalJson.put(JSON_PROP_AMOUNTAFTERTAX, totalTransferAmountAfterTax);
					totalJson.put(JSON_PROP_AMOUNTBEFORETAX, totalTransferAmountBeforeTax);
					baseJson.put(JSON_PROP_AMOUNTBEFORETAX, totalTransferAmountBeforeTax);
					taxes.put(JSON_PROP_AMOUNT,totalTransferTaxes);
					
					JSONArray taxJsonArr = getTotalTaxes(priceJson, taxMap);
					taxes.put(JSON_PROP_TAX, taxJsonArr);
					
					JSONObject rateDescJson = priceJson.getJSONObject(JSON_PROP_RATEDESC);
					rateDescJson.put(JSON_PROP_TEXT, "Transfers");
					priceMap.put("Transfers", priceJson);

				} else if (rateDescriptionText.contains("Extra")) {

					totalExtrasAmountAfterTax = totalExtrasAmountAfterTax.add(amountAfterTax);
					totalExtrasAmountBeforeTax = totalExtrasAmountBeforeTax.add(amountBeforeTax);
					totalExtraTaxes = totalExtraTaxes.add(taxAmount);
					
					totalJson.put(JSON_PROP_AMOUNTAFTERTAX, totalExtrasAmountAfterTax);
					totalJson.put(JSON_PROP_AMOUNTBEFORETAX, totalExtrasAmountBeforeTax);
					baseJson.put(JSON_PROP_AMOUNTBEFORETAX, totalExtrasAmountBeforeTax);
					taxes.put(JSON_PROP_AMOUNT,totalExtraTaxes);
					
					JSONArray taxJsonArr = getTotalTaxes(priceJson, taxMap);
					taxes.put(JSON_PROP_TAX, taxJsonArr);
					
					priceMap.put(rateDescriptionText, priceJson);

				} else if (rateDescriptionText.contains("Trip Protection")
						|| (rateDescriptionText.contains("Insurance"))) {

					totalInsuranceAmountAfterTax = totalInsuranceAmountAfterTax.add(amountAfterTax);
					totalInsuranceAmountBeforeTax = totalInsuranceAmountBeforeTax.add(amountBeforeTax);
					totalInsuranceTaxes = totalInsuranceTaxes.add(taxAmount);
					
					totalJson.put(JSON_PROP_AMOUNTAFTERTAX, totalInsuranceAmountAfterTax);
					totalJson.put(JSON_PROP_AMOUNTBEFORETAX, totalInsuranceAmountBeforeTax);
					baseJson.put(JSON_PROP_AMOUNTBEFORETAX, totalInsuranceAmountBeforeTax);
					taxes.put(JSON_PROP_AMOUNT,totalInsuranceTaxes);
					
					JSONArray taxJsonArr = getTotalTaxes(priceJson, taxMap);
					taxes.put(JSON_PROP_TAX, taxJsonArr);
					
					priceMap.put(rateDescriptionText, priceJson);

				} else {
					priceMap.put(rateDescriptionText, priceJson);
				}

			}
			componentJson.remove(JSON_PROP_PRICE);
	        
	// Populating new price array from priceMap
		Iterator<Map.Entry<String, JSONObject>> priceIter = priceMap.entrySet().iterator();
		while (priceIter.hasNext()) {
			Map.Entry<String, JSONObject> priceEntry = priceIter.next();
			newPriceArr.put(priceEntry.getValue());
			componentJson.put(JSON_PROP_PRICE, newPriceArr);
		}
	
		return newPriceArr;
	}

	
	public static JSONArray getTotalTaxes(JSONObject priceJson, Map<String, BigDecimal> taxMap) {
		
		JSONArray taxArr = priceJson.getJSONObject("total").getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
		JSONArray taxJsonArr = new JSONArray();

		// for adding all air taxes
		for (int k = 0; k < taxArr.length(); k++) {
			JSONObject tax = taxArr.getJSONObject(k);
			String taxName = tax.getString(JSON_PROP_TAXDESCRIPTION);
			if (taxName.equals("TaxesAndPortCharges")) {
				BigDecimal taxesAndPortChargesValue = tax.optBigDecimal(JSON_PROP_AMOUNT,new BigDecimal("0"));

				if (taxMap.containsKey("TaxesAndPortCharges")) {
					BigDecimal totalTaxesAndPortCharges = taxMap.get("TaxesAndPortCharges").add(taxesAndPortChargesValue);
					taxMap.put("TaxesAndPortCharges", totalTaxesAndPortCharges);
				} else {
					BigDecimal totalTaxesAndPortCharges = taxesAndPortChargesValue;
					taxMap.put("TaxesAndPortCharges", totalTaxesAndPortCharges);
				}
			}

			if (taxName.equals("Surcharge")) {
				BigDecimal surchargeValue = tax.optBigDecimal(JSON_PROP_AMOUNT,new BigDecimal("0"));
				
				if (taxMap.containsKey("Surcharge")) {
					BigDecimal totalSurcharge = taxMap.get("Surcharge").add(surchargeValue);
					taxMap.put("Surcharge", totalSurcharge);
				} else {
					BigDecimal totalSurcharge = surchargeValue;
					taxMap.put("Surcharge", totalSurcharge);
				}
			}

		}

		for (Entry<String, BigDecimal> entry : taxMap.entrySet()) {
			JSONObject taxJson = new JSONObject();
			taxJson.put(JSON_PROP_AMOUNT, String.valueOf(entry.getValue()));
			taxJson.put(JSON_PROP_TAXDESCRIPTION, String.valueOf(entry.getKey()));
			taxJsonArr.put(taxJson);

		}
		return taxJsonArr;
	}
	
	public static Map<String, JSONObject> retainSuppFaresMap(JSONObject componentJson, Map<String, JSONObject> priceMap) {

		String rateDescriptionText;
		JSONArray newPriceArr = new JSONArray();
		Map<String, BigDecimal> taxMap = new HashMap<String, BigDecimal>();
		
		
		// Map containing unique values of priceArray
		
			JSONArray priceArray = componentJson.getJSONArray(JSON_PROP_PRICE);
			for (int b = 0; b < priceArray.length(); b++) {
				JSONObject priceJson = priceArray.getJSONObject(b);
				rateDescriptionText = priceJson.getJSONObject(JSON_PROP_RATEDESC).getString(JSON_PROP_TEXT);

				BigDecimal amountAfterTax = priceJson.getJSONObject("total").optBigDecimal(JSON_PROP_AMOUNTAFTERTAX,
						new BigDecimal("0"));
				BigDecimal amountBeforeTax = priceJson.getJSONObject("total").optBigDecimal(JSON_PROP_AMOUNTBEFORETAX,
						new BigDecimal("0"));
				BigDecimal amountBeforeTaxBase = priceJson.getJSONObject("base")
						.optBigDecimal(JSON_PROP_AMOUNTBEFORETAX, new BigDecimal("0"));
				
				JSONObject totalJson = priceJson.getJSONObject("total");
				JSONObject baseJson = priceJson.getJSONObject("base");
				JSONObject taxes = totalJson.getJSONObject(JSON_PROP_TAXES);
				BigDecimal taxAmount = taxes.optBigDecimal(JSON_PROP_AMOUNT,new BigDecimal("0"));
				

				totalJson.put(JSON_PROP_AMOUNTAFTERTAX, amountAfterTax);
				totalJson.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTax);
				baseJson.put(JSON_PROP_AMOUNTBEFORETAX, amountBeforeTaxBase);
				taxes.put(JSON_PROP_AMOUNT,taxAmount);
				priceMap.put(rateDescriptionText, priceJson);
				
			}
	        
	// Populating new price array from priceMap
		Iterator<Map.Entry<String, JSONObject>> priceIter = priceMap.entrySet().iterator();
		while (priceIter.hasNext()) {
			Map.Entry<String, JSONObject> priceEntry = priceIter.next();
			newPriceArr.put(priceEntry.getValue());
			componentJson.put(JSON_PROP_PRICE, newPriceArr);
		}
	
		return priceMap;
	}
}
