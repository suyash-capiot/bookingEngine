package com.coxandkings.travel.bookingengine.config.forex;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.ConfigType;
import com.coxandkings.travel.bookingengine.config.LoadConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ServicesGroupConfig;
import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.utils.Constants;


public class ForexConfig implements Constants {

	private static final int DEFAULT_REDIS_TTL_MINS = 15;
	private static final int DEFAULT_ASYNC_SEARCH_WAIT_SECS = 60;
	private static int mRedisTTLMins;
	private static int mAsyncSearchWaitSecs;
	
	private static ServicesGroupConfig mOpConfig;
	
	
	@LoadConfig (configType = ConfigType.PRODUCT)
	public static void loadConfig()
	{
        
		Document configDoc = MongoProductConfig.getConfig("FOREIGN_EXCHANGE");
		mRedisTTLMins = configDoc.getInteger("redisTTLMinutes", DEFAULT_REDIS_TTL_MINS);
		mAsyncSearchWaitSecs = configDoc.getInteger("asyncSearchWaitSeconds", DEFAULT_ASYNC_SEARCH_WAIT_SECS);

		mOpConfig = new ServicesGroupConfig(CONFIG_PROP_SUPPINTEG, (Document) configDoc.get(CONFIG_PROP_SUPPINTEG));
	}
	
	public static ServiceConfig getOperationConfig(String opName) {
		return mOpConfig.getServiceConfig(opName);
	}
	
}
