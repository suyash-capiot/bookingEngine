package com.coxandkings.travel.bookingengine.enums;

import com.coxandkings.travel.bookingengine.utils.Constants;

public enum ShowRateOf implements Constants {
	MultipleSuppliers(MDM_VAL_MULTISUPPS), SingleSupplier(MDM_VAL_SINGLESUPP);
	
	private String mShowRateOf;
	private ShowRateOf(String val) {
		mShowRateOf = val;
	}
	
	public String toString() {
		return mShowRateOf;
	}
	
	public static ShowRateOf forString(String showRateOfStr) {
		ShowRateOf[] sros = ShowRateOf.values();
		for (ShowRateOf sro : sros) {
			if (sro.toString().equals(showRateOfStr)) {
				return sro;
			}
		}
		
		return null;
	}
	
	public static ShowRateOf valueOfOrDefault(String valStr, ShowRateOf dft) {
		ShowRateOf sro = forString(valStr);
		return (sro != null) ? sro : dft;
	}

}
