package com.coxandkings.travel.bookingengine.test_suite.config;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.test_suite.TestSuite;



public abstract class AbstractTestConfig implements TestConfig,TestSuite{

	private static final Logger logger = LogManager.getLogger(AbstractTestConfig.class);
	
	protected boolean mBEServiceHttpsFlg = false;
	protected String mBEServiceIP = "";
	protected String mBEServicePort = "";
	protected JSONObject mBEServiceOpsJson;
	protected HashMap<String, String> mOperationURIMap = new HashMap<String,String>();
	protected ArrayList<String> mOperationList = new ArrayList<String>();
	protected static final String OPERATION_CONFIG_PROP = "operationConfig";
	
	protected void loadOperationsURI(){
		JSONArray opsJsonArr = mBEServiceOpsJson.optJSONArray(OPERATION_CONFIG_PROP);
		if(opsJsonArr==null || opsJsonArr.length()==0) {
			logger.error(String.format("No configuration array found for operation name and URL for key %s",OPERATION_CONFIG_PROP));
			return;
		}
		String opName;
		for(Object operationJson : opsJsonArr) {
				opName = ((JSONObject) operationJson).getString("operationName").toUpperCase();
				if(!OPERATION_NAMES_LIST.contains(opName)){
					logger.warn(String.format("Operation %s not supported.Supported operations List: %s",opName, OPERATION_NAMES_LIST.toString()));
					continue;
				}
				mOperationURIMap.put(opName,getFormattedURI(((JSONObject) operationJson).getString("relativeURI")));
				mOperationList.add(opName);
		}
	}
	
	protected String getFormattedURI(String relURI) {
		StringBuilder uriBldr = new StringBuilder();
		if(relURI==null || relURI.isEmpty()) {
			relURI = "";
		}
		else {
			relURI = relURI.startsWith("/")?relURI:"/".concat(relURI);
		}
		uriBldr.append(mBEServiceHttpsFlg?"https://":"http://");
		uriBldr.append(mBEServiceIP);
		uriBldr.append(':');
		uriBldr.append(mBEServicePort);
		uriBldr.append(relURI);
		return uriBldr.toString();
	}
	
	public String getBEServiceURI(String operationName) {
		return mOperationURIMap.get(operationName);
	}
	
	public ArrayList<String> getOperationList() {
		return mOperationList;
	}
}
