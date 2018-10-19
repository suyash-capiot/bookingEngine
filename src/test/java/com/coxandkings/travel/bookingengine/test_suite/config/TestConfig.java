package com.coxandkings.travel.bookingengine.test_suite.config;

import java.util.ArrayList;

public interface TestConfig {

	static final String BE_HTPPSFLAG_PROP = "isHttps";
	static final String BE_IP_PROP = "bookingEngineIP";
	static final String BE_PORT_PROP = "bookingEnginePort";
	static final String BE_OPERATIONS_JSON_PROP = "bookingEngineOperations";
	static final String BE_CONFIGFILE_PROP= "be.config.file";
	
	public String getBEServiceURI(String operationName);
	public ArrayList<String> getOperationList();
	
}
