package com.coxandkings.travel.bookingengine.test_suite.config;

import java.io.FileNotFoundException;
import java.io.IOException;

public class TestConfigFactory {

	public static TestConfig getBEServiceConfig() throws FileNotFoundException, IOException {
		return new FileTestConfig(); 
	}
}
