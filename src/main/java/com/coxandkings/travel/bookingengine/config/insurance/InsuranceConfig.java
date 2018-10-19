package com.coxandkings.travel.bookingengine.config.insurance;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.ConfigType;
import com.coxandkings.travel.bookingengine.config.LoadConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ServicesGroupConfig;
import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.utils.Constants;

public class InsuranceConfig implements Constants {
	
	private static final int DEFAULT_REDIS_TTL_MINS = 15;
	private static final int DEFAULT_ASYNC_SEARCH_WAIT_SECS = 60;
	
	private static ServicesGroupConfig mOpConfig;
	private static ServicesGroupConfig mOffConfig;
	
	private static int mRedisTTLMins;
	private static int mAsyncSearchWaitSecs;

	
	@LoadConfig (configType = ConfigType.PRODUCT)
	public static void loadConfig() {

		Document configDoc = MongoProductConfig.getConfig("INSURANCE");
		mRedisTTLMins = configDoc.getInteger("redisTTLMinutes", DEFAULT_REDIS_TTL_MINS);
		mAsyncSearchWaitSecs = configDoc.getInteger("asyncSearchWaitSeconds", DEFAULT_ASYNC_SEARCH_WAIT_SECS);

		mOpConfig = new ServicesGroupConfig(CONFIG_PROP_SUPPINTEG, (Document) configDoc.get(CONFIG_PROP_SUPPINTEG));
		mOffConfig = new ServicesGroupConfig(CONFIG_PROP_OFFERS, (Document) configDoc.get(CONFIG_PROP_OFFERS));
	}
	
	public static ServiceConfig getOperationConfig(String opName) {
		return  mOpConfig.getServiceConfig(opName);
	}
	
	public static int getRedisTTLMinutes() {
		return mRedisTTLMins;
	}
	
	public static int getAsyncSearchWaitSeconds() {
		return mAsyncSearchWaitSecs;
	}
	
	public static int getAsyncSearchWaitMillis() {
		return (mAsyncSearchWaitSecs * 1000);
	}
	
	public static ServiceConfig getOffersTypeConfig(OffersType offType) {
		return mOffConfig.getServiceConfig(offType.toString());
	}
}
