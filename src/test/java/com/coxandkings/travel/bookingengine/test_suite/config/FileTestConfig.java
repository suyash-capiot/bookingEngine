package com.coxandkings.travel.bookingengine.test_suite.config;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.json.JSONObject;

public class FileTestConfig extends AbstractTestConfig{
	
	FileTestConfig() throws FileNotFoundException, IOException{
		String filePath_str = System.getProperty(BE_CONFIGFILE_PROP);
		if(filePath_str==null || filePath_str.isEmpty()) {
			throw new IllegalArgumentException(String.format("No configuration file found at system property %s", BE_CONFIGFILE_PROP));
		}
		System.out.println(String.format("File configuration found at path %s", filePath_str));
		Properties props = new Properties();
		props.load(new FileReader(filePath_str));
		mBEServiceHttpsFlg = Boolean.valueOf(props.getProperty(BE_HTPPSFLAG_PROP));
		mBEServiceIP = props.getProperty(BE_IP_PROP);
		mBEServicePort = props.getProperty(BE_PORT_PROP);
		mBEServiceOpsJson = new JSONObject(props.getProperty(BE_OPERATIONS_JSON_PROP));
		loadOperationsURI();
	}
}
