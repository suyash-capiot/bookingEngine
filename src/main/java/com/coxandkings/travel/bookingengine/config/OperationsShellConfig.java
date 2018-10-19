package com.coxandkings.travel.bookingengine.config;

import java.net.URL;
import java.util.Map;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;

public class OperationsShellConfig {
	public static final String PROP_MONGO_DOC = "OPERATIONS";
//	public static final String PROP_REQ_JSON_SHELL = "todoJsonShell";
//	public static final String PROP_REQ_JSON_URL = "operationsUrl";
//	private static String errorJsonString;
//	private static String operationsUrl;
//	private static URL mToDoSvcURL;
//	private static final Logger logger = LogManager.getLogger(OperationsShellConfig.class); 
	private static ServiceConfig mOpsTodoServiceConfig;
	
	@LoadConfig (configType = ConfigType.COMMON)
	public static void loadConfig() {
		Document configDoc = MongoProductConfig.getConfig(PROP_MONGO_DOC);
		Document todoTaskListInfo = (Document) configDoc.get("todoTaskListInfo");
//		 errorJsonString = todoTaskListInfo.getString(PROP_REQ_JSON_SHELL);
//		 operationsUrl = todoTaskListInfo.getString(PROP_REQ_JSON_URL);
//		try {
//			mToDoSvcURL = new URL(operationsUrl);
//		} 
//		catch (MalformedURLException mux) {
//			logger.warn(String.format("Error occurred while initializing ToDo service URL in %s configuration", PROP_MONGO_DOC), mux);
//		}
		
		mOpsTodoServiceConfig = new ServiceConfig(null, todoTaskListInfo);
 	}

	public static String getRequestJSONShell() {
		//return errorJsonString;
		return mOpsTodoServiceConfig.getRequestJSONShell();
	}
	 
	public static Map<String, String> getHttpHeaders() {
		return mOpsTodoServiceConfig.getHttpHeaders();
	}
//	@Deprecated
//	public static String getOperationsUrl() {
//		//return operationsUrl;
//		return mOpsTodoServiceConfig.getServiceURL().toString();
//	}	
	
	public static URL getServiceURL() {
		//return mToDoSvcURL;
		return mOpsTodoServiceConfig.getServiceURL();
	}
}
