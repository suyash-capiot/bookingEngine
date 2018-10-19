package com.coxandkings.travel.bookingengine.test_suite;

import java.util.Arrays;
import java.util.List;

public interface TestSuite {

	public static final String OPERATION_NAME_SEARCH = "SEARCH";
	public static final String OPERATION_NAME_PRICE = "PRICE";
	public static final String OPERATION_NAME_REPRICE = "REPRICE";
	public static final String OPERATION_NAME_BOOK = "BOOK";
	public static final String OPERATION_NAME_AMEND = "AMEND";
	public static final String OPERATION_NAME_CANCEL = "CANCEL";
	
	public static final List<String> OPERATION_NAMES_LIST = Arrays.asList(new String[]{OPERATION_NAME_SEARCH,OPERATION_NAME_PRICE,OPERATION_NAME_REPRICE,OPERATION_NAME_BOOK,OPERATION_NAME_AMEND,OPERATION_NAME_CANCEL});
	
}
