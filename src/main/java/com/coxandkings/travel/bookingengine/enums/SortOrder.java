package com.coxandkings.travel.bookingengine.enums;

public enum SortOrder {
	Incentives, Price;
	
	public static SortOrder valueOfOrDefault(String valStr, SortOrder dftSortOrder) {
		
		if(valStr == null || valStr.isEmpty())
		{
			return dftSortOrder;
		}
		else {
			SortOrder srtOrdr = valueOf(valStr);
			return srtOrdr;
		}
		/*SortOrder srtOrdr = valueOf(valStr);
		return (srtOrdr != null) ? srtOrdr : dftSortOrder;*/
	}
}
