package com.coxandkings.travel.bookingengine.config.holidays;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.ConfigType;
import com.coxandkings.travel.bookingengine.config.LoadConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ServicesGroupConfig;
import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.HolidayConstants;
import com.coxandkings.travel.bookingengine.utils.Constants;

public class HolidaysConfig implements Constants {
  
	private static final int DEFAULT_REDIS_TTL_MINS = 15;
  
	private static ServicesGroupConfig mOpConfig;
	private static ServicesGroupConfig mCommConfig;
	private static ServicesGroupConfig mOffConfig;
  
	private static int mRedisTTLMins;
  
	@LoadConfig (configType = ConfigType.PRODUCT)
	public static void loadConfig() {
		
		org.bson.Document configDoc = MongoProductConfig.getConfig(HolidayConstants.PRODUCT);
		mRedisTTLMins = configDoc.getInteger("redisTTLMinutes", DEFAULT_REDIS_TTL_MINS);
		
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
	
	public static ServiceConfig getOffersTypeConfig(OffersType offType) {
		return mOffConfig.getServiceConfig(offType.toString());
	}
	
	public static int getRedisTTLMinutes() {
		return mRedisTTLMins;
	}
}
