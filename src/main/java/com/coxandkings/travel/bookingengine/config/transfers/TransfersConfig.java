package com.coxandkings.travel.bookingengine.config.transfers;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.ConfigType;
import com.coxandkings.travel.bookingengine.config.LoadConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ServicesGroupConfig;
import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.orchestrator.transfers.TransfersConstants;
import com.coxandkings.travel.bookingengine.utils.Constants;

public class TransfersConfig implements Constants{
	
	private static final int DEFAULT_REDIS_TTL_MINS = 15;
	private static final int DEFAULT_ASYNC_SEARCH_WAIT_SECS = 60;
	
	private static ServicesGroupConfig mOpConfig;
	private static ServicesGroupConfig mCommConfig;
	
	private static int mRedisTTLMins;
	private static boolean mEnableCacheRental;
	private static boolean mEnableCacheSelfDrive;
	private static int mAsyncSearchWaitSecs;

	@LoadConfig (configType = ConfigType.PRODUCT)
	public static void loadConfig() {
		
		Document configDoc = MongoProductConfig.getConfig(TransfersConstants.PRODUCT_TRANSFERS);
		mRedisTTLMins = configDoc.getInteger("redisTTLMinutes", DEFAULT_REDIS_TTL_MINS);
		mAsyncSearchWaitSecs = configDoc.getInteger("asyncSearchWaitSeconds", DEFAULT_ASYNC_SEARCH_WAIT_SECS);

		mOpConfig = new ServicesGroupConfig(CONFIG_PROP_SUPPINTEG, (Document) configDoc.get(CONFIG_PROP_SUPPINTEG));
		mCommConfig = new ServicesGroupConfig(CONFIG_PROP_COMMERCIALS, (Document) configDoc.get(CONFIG_PROP_COMMERCIALS));
	}
	
	public static ServiceConfig getOperationConfig(String opName) {
		return mOpConfig.getServiceConfig(opName);
	}
	
	public static ServiceConfig getCommercialTypeConfig(CommercialsType commType) {
		return mCommConfig.getServiceConfig(commType.toString());
	}
	
	public static int getRedisTTLMinutes() {
		return mRedisTTLMins;
	}
	
	public static int getAsyncSearchWaitSeconds() {
		return mAsyncSearchWaitSecs;
	}
	
	public static boolean isEnableCacheRental() {
		return mEnableCacheRental;
	}

	public static boolean isEnableCacheSelfDrive() {
		return mEnableCacheSelfDrive;
	}

	public static int getAsyncSearchWaitMillis() {
		return (mAsyncSearchWaitSecs * 1000);
	}
}
