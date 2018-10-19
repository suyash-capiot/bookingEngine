package com.coxandkings.travel.bookingengine.orchestrator.car;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.Utils;

public class PriceComponentsGroup extends PriceComponent {

	private boolean mDoSummation;
	private Map<String, PriceComponent> mComps;

//	PriceComponentsGroup(String compCode) {
//		this(compCode, false);
//	}
//
//	PriceComponentsGroup(String compCode, boolean doSummation) {
//		this(compCode,"", new BigDecimal(0), doSummation);
//	}
//	
	public PriceComponentsGroup(String compCode, String compCcy, BigDecimal compAmt, boolean doSummation) {
		super(compCode, compCcy, compAmt);
		mDoSummation = doSummation;
		mComps = new LinkedHashMap<String, PriceComponent>();
	}

	public void add(String compCode, String compCcy, BigDecimal compAmt) {
		if (Utils.isStringNullOrEmpty(compCode)) {
			return;
		}
		
		boolean isSummation = false;
		int idx = compCode.indexOf('.');
		String currCompCode = compCode.substring(0, (idx > -1) ? idx : compCode.length());
		if (currCompCode.endsWith(SIGMA)) {
			isSummation = true;
			currCompCode = currCompCode.substring(0, currCompCode.length() - SIGMA.length());
		}
		
		PriceComponent prcComp = mComps.get(currCompCode);
		
		if (prcComp == null) {
			if (idx == -1) {
				mComps.put(currCompCode, new PriceComponent(compCode, compCcy, compAmt));
			}
			else if (idx < (compCode.length() - 1)) {
				PriceComponentsGroup prcCompsList = new PriceComponentsGroup(currCompCode, getComponentCurrency(), new BigDecimal(0), isSummation);
				mComps.put(currCompCode, prcCompsList);
				prcCompsList.add(compCode.substring(idx+1), compCcy, compAmt);
				
			}
		}
		else {	
			if (prcComp instanceof PriceComponentsGroup) {
				((PriceComponentsGroup) prcComp).add(compCode.substring(idx+1), compCcy, compAmt);
			}
			else if (prcComp instanceof PriceComponent) {
				if (prcComp.isMatching(compCode.substring(idx+1))) {
					prcComp.add(compCode.substring(idx+1), compCcy, compAmt);
				}
				else {
					PriceComponentsGroup prcCompsList = new PriceComponentsGroup(currCompCode, getComponentCurrency(), new BigDecimal(0), isSummation);
					prcCompsList.addSubComponent(prcComp.getComponentCode(), prcComp);
					prcCompsList.add(compCode.substring(idx+1), compCcy, compAmt);
					mComps.put(currCompCode, prcCompsList);
				}
			}
		}
		
		if (mDoSummation) {
			super.add(this.getComponentCode(), compCcy, compAmt);
		}
	}
	
	private void addSubComponent(String compCode, PriceComponent prcComp) {
		mComps.put(compCode, prcComp);
	}
	
	public Object toJSON() {
		Object returnObj = null;
		if (mDoSummation) {
			JSONObject prcCompGrpJson = new JSONObject();
			prcCompGrpJson.put(JSON_PROP_AMOUNT, getComponentAmount());
			prcCompGrpJson.put(JSON_PROP_CCYCODE, getComponentCurrency());

			Iterator<Entry<String,PriceComponent>> compsIter = mComps.entrySet().iterator();
			while (compsIter.hasNext()) {
				Entry<String,PriceComponent> comp = compsIter.next();
				prcCompGrpJson.put(comp.getKey(), comp.getValue().toJSON());
			}
			returnObj = prcCompGrpJson; 
		}
		else {
			JSONArray prcCompGrpJsonArr = new JSONArray();
			Iterator<Entry<String,PriceComponent>> compsIter = mComps.entrySet().iterator();
			while (compsIter.hasNext()) {
				Entry<String,PriceComponent> comp = compsIter.next();
				prcCompGrpJsonArr.put(comp.getValue().toJSON());
			}
			returnObj = prcCompGrpJsonArr;
		}
		
		return returnObj;
	}
	
	
	private String getPriceComponentsGroupCode(PriceComponent prcComp, String parentCodePrefix) {
		StringBuilder strBldr = new StringBuilder((parentCodePrefix == null) ? "" : parentCodePrefix);
		if (strBldr.length() > 0) {
			strBldr.append(".");
		}
		
		strBldr.append(prcComp.getQualifiedComponentCode());
		
		if (prcComp instanceof PriceComponentsGroup && ((PriceComponentsGroup) prcComp).mDoSummation) {
			strBldr.append(Constants.SIGMA);
		}

		return strBldr.toString();
	}
	
	public void addSubComponent(PriceComponent prcComp, String compCodePrefix) {
		String newCompCodePrefix = getPriceComponentsGroupCode(prcComp, compCodePrefix);
		
		if (prcComp instanceof PriceComponentsGroup) {
			PriceComponentsGroup prcCompsGrp = (PriceComponentsGroup) prcComp;
			
			Collection<PriceComponent> subPrcComps = prcCompsGrp.mComps.values();
			for (PriceComponent subPrcComp : subPrcComps) {
				addSubComponent(subPrcComp, newCompCodePrefix);
			}
		}
		else {
			this.add(newCompCodePrefix, prcComp.getComponentCurrency(), prcComp.getComponentAmount());
		}
	}
}
