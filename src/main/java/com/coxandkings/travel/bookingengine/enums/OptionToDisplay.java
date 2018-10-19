package com.coxandkings.travel.bookingengine.enums;

import com.coxandkings.travel.bookingengine.utils.Constants;

public enum OptionToDisplay implements Constants{
All("All"), LHR("LHR");
	
	private String mOptionToDisplay;
	
	private OptionToDisplay(String val) {
		mOptionToDisplay = val;
	}
	
	public String toString() {
		return mOptionToDisplay;
	}
	
	public static OptionToDisplay forString(String showRateOfStr) {
		OptionToDisplay[] sros = OptionToDisplay.values();
		for (OptionToDisplay sro : sros) {
			if (sro.toString().equals(showRateOfStr)) {
				return sro;
			}
		}
		return null;
	}
	
	public static OptionToDisplay valueOfOrDefault(String valStr, OptionToDisplay dft) {
		OptionToDisplay sro = forString(valStr);
		return (sro != null) ? sro : dft;
	}

}
