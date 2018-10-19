package com.coxandkings.travel.bookingengine.orchestrator.holidays;

import java.math.BigDecimal;

import org.json.JSONObject;

public class PriceComponent implements HolidayConstants {
	private String mCompName;
	private String mCompCode;
	private String mCompCcy;
	private BigDecimal mCompAmt;
	
	PriceComponent(String compCode, String compCcy, BigDecimal compAmt) {
		String[] compCodeParts = compCode.split("@");
		mCompCode = compCodeParts[0];
		mCompName = (compCodeParts.length > 1) ? compCodeParts[1] : null;
		mCompCcy = compCcy;
		mCompAmt = compAmt;
	}
	
	void add(String compCode, String compCcy, BigDecimal compAmt) {
		// TODO: What if currency is different? Should RoE be called here?
		if (mCompCcy.equals(compCcy)) {
			mCompAmt = mCompAmt.add(compAmt);
		}
	}
	
	void add(String compCcy, BigDecimal compAmt) {
		// TODO: What if currency is different? Should RoE be called here?
		if (mCompCcy.equals(compCcy)) {
			mCompAmt = mCompAmt.add(compAmt);
		}
	}

	public BigDecimal getComponentAmount() {
		return mCompAmt;
	}
	
	public String getComponentCode() {
		return mCompCode;
	}

	public String getComponentCurrency() {
		return mCompCcy;
	}
	
	public String getComponentName() {
		return mCompName;
	}

	public String getQualifiedComponentCode() {
		return (mCompName != null) ? String.format("%s@%s", mCompCode, mCompName) : mCompCode;
	}

	public boolean isMatching(String compCode) {
		if (compCode == null || compCode.isEmpty()) { 
			return false;
		}
		
		String[] compCodeParts = compCode.split("@");
		return (mCompCode.equals(compCodeParts[0]) && ((mCompName == null && compCodeParts.length <= 1) ||  mCompName.equals(compCodeParts[1])));
	}
	
	public Object toJSON() {
		JSONObject priceCompJson = new JSONObject();
		if (mCompName != null) {
			priceCompJson.put(mCompName, mCompCode);
		}
		priceCompJson.put(JSON_PROP_AMOUNT, mCompAmt);
		priceCompJson.put(JSON_PROP_CCYCODE, mCompCcy);
		return priceCompJson;
	}
	
}
