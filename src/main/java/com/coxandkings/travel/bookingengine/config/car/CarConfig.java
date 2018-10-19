package com.coxandkings.travel.bookingengine.config.car;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.ConfigType;
import com.coxandkings.travel.bookingengine.config.LoadConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ServicesGroupConfig;
import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.utils.Constants;

public class CarConfig implements Constants{
	
	private static final int DEFAULT_REDIS_TTL_MINS = 15;
	
	private static final int DEFAULT_ASYNC_SEARCH_WAIT_SECS = 60;
	
	private static ServicesGroupConfig mOpConfig;
	private static ServicesGroupConfig mCommConfig;
	private static ServicesGroupConfig mOffConfig;

	private static int mRedisTTLMins;
	private static boolean mEnableCacheRental;
	private static boolean mEnableCacheSelfDrive;
	private static int mAsyncSearchWaitSecs;

	@LoadConfig (configType = ConfigType.PRODUCT)
	public static void loadConfig() {
		
		Document configDoc = MongoProductConfig.getConfig(CarConstants.PRODUCT_CAR);
		mRedisTTLMins = configDoc.getInteger("redisTTLMinutes", DEFAULT_REDIS_TTL_MINS);
		mAsyncSearchWaitSecs = configDoc.getInteger("asyncSearchWaitSeconds", DEFAULT_ASYNC_SEARCH_WAIT_SECS);
		mEnableCacheRental = configDoc.getBoolean("enableCacheRental", true);
		mEnableCacheSelfDrive = configDoc.getBoolean("enableCacheSelfDrive", true);
		
		mOpConfig = new ServicesGroupConfig(CONFIG_PROP_SUPPINTEG, (Document) configDoc.get(CONFIG_PROP_SUPPINTEG));
		mCommConfig = new ServicesGroupConfig(CONFIG_PROP_COMMERCIALS, (Document) configDoc.get(CONFIG_PROP_COMMERCIALS));
		mOffConfig = new ServicesGroupConfig(CONFIG_PROP_OFFERS, (Document) configDoc.get(CONFIG_PROP_OFFERS));
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
	
	public static boolean ismEnableCacheRental() {
		return mEnableCacheRental;
	}

	public static boolean ismEnableCacheSelfDrive() {
		return mEnableCacheSelfDrive;
	}

	public static ServiceConfig getOffersTypeConfig(OffersType offType) {
		return mOffConfig.getServiceConfig(offType.toString());
	}
	
	public static int getAsyncSearchWaitMillis() {
		return (mAsyncSearchWaitSecs * 1000);
	}
}
