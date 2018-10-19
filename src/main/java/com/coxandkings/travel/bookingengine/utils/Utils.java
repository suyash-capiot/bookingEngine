package com.coxandkings.travel.bookingengine.utils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;

public class Utils implements Constants{
	
	private static MathContext mathCtx = new MathContext(0, RoundingMode.UP);
	
	
	public static int convertToInt(String strVal, int dftVal) {
		try {
			return Integer.valueOf(strVal);
		}
		catch (NumberFormatException nfx) {
			return dftVal;
		}
	}

	public static BigDecimal convertToBigDecimal(String strVal, int precision, int dftVal) {
		if (isStringNullOrEmpty(strVal)) {
			return new BigDecimal(dftVal);
		}
		
		try {
			return new BigDecimal(strVal, new MathContext(precision));
		}
		catch (NumberFormatException nfx) {
			return new BigDecimal(dftVal);
		}
	}

	
	public static BigDecimal convertToBigDecimal(String strVal, int dftVal) {
		//return convertToBigDecimal(strVal, 6, dftVal);
		if (isStringNullOrEmpty(strVal)) {
			return new BigDecimal(dftVal);
		}
		try {
			return new BigDecimal(strVal);
		}
		catch (NumberFormatException nfx) {
			return new BigDecimal(dftVal);
		}
		
	}
	
	public static int convertAndRoundToInt(String strVal, int dftVal) {
		try {
			BigDecimal bigDec = new BigDecimal(strVal);
			return bigDec.round(mathCtx).intValue();
		}
		catch (NumberFormatException nfx) {
			return dftVal;
		}
	}
	
	public static String concatSequence(List<String> seq, char sepChar) {
		if (seq == null) {
			return "";
		}
		
		StringBuilder strBldr = new StringBuilder();
		for (int i=0; i < seq.size(); i++) {
			strBldr.append(seq.get(i));
			strBldr.append(sepChar);
		}
		
		strBldr.setLength((strBldr.length() > 0) ? strBldr.length() - 1 : 0);
		return strBldr.toString();
	}

	public static boolean isStringNotNullAndNotEmpty(String str) {
		return ! isStringNullOrEmpty(str);
	}

	public static boolean isStringNullOrEmpty(String str) {
		return (str == null || str.isEmpty());
	}
	
	public static boolean isSynchronousSearchRequest(JSONObject reqJson) {
		boolean dftResult = true;
		
		if (reqJson == null) {
			return dftResult;
		}
		
		JSONObject reqHdrJson = reqJson.optJSONObject(JSON_PROP_REQHEADER);
		if (reqHdrJson == null) {
			return dftResult;
		}
		
		JSONObject clCtxJson = reqHdrJson.optJSONObject(JSON_PROP_CLIENTCONTEXT);
		return (clCtxJson == null || clCtxJson.optString(JSON_PROP_CLIENTCALLBACK).isEmpty());
	}

	public static int calculateAge(String birthdate) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
		int age = 1;
		if(birthdate==null || birthdate.isEmpty())
			return age;
		if (birthdate.matches("([0-9]{4})-([0-9]{2})-([0-9]{2})")) {
			LocalDate dob = LocalDate.parse(birthdate);
			LocalDate curDate = LocalDate.now();
			age = Period.between(dob, curDate).getYears();
		} else if (birthdate.matches("([0-9]{2})-([0-9]{2})-([0-9]{4})")) {
			LocalDate dob = LocalDate.parse(birthdate, formatter);
			LocalDate curDate = LocalDate.now();
			age = Period.between(dob, curDate).getYears();
		}
		return age;
	}
	
	public static JSONArray convertProdSuppliersToCommercialCache(ProductSupplier... prodSupps) {
		JSONArray prodSuppsJsonArr = new JSONArray();
		if (prodSupps != null && prodSupps.length > 0) {
			for (ProductSupplier prodSupp : prodSupps) {
	        	JSONObject prodSuppJson = new JSONObject();
	        	prodSuppJson.put(JSON_PROP_SUPPID, prodSupp.getSupplierID());
	        	prodSuppJson.put(JSON_PROP_CREDSNAME, prodSupp.getCredentialsName());
	        	prodSuppJson.put(Constants.JSON_PROP_ISGDS, prodSupp.isGDS());
				prodSuppsJsonArr.put(prodSuppJson);
			}
		}
		return prodSuppsJsonArr;
	}
	
	/*public static void dedupProductSuppliers(List<ProductSupplier> productSuppliers) {
		if(productSuppliers == null)
			return;
		
		Map<String,Boolean> prodSuppMap = new HashMap<String,Boolean>();
		for(int i=productSuppliers.size()-1;i>=0;i--) {
			ProductSupplier productSupplier = productSuppliers.get(i);
			String prodSuppMapKey = String.format("%s|%s", productSupplier.getSupplierID(),productSupplier.getCredentialsName());
			if(prodSuppMap.containsKey(prodSuppMapKey)) {
				productSuppliers.remove(i);
				continue;
			}
			prodSuppMap.put(prodSuppMapKey, true);
		}
	}*/
}
