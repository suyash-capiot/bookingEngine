package com.coxandkings.travel.bookingengine.config.rail;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.ConfigType;
import com.coxandkings.travel.bookingengine.config.LoadConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.ServicesGroupConfig;
import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.enums.CommercialsType;
import com.coxandkings.travel.bookingengine.enums.OffersType;
import com.coxandkings.travel.bookingengine.orchestrator.rail.RailConstants;
import com.coxandkings.travel.bookingengine.utils.Constants;

public class RailConfig implements Constants {

	private static ServicesGroupConfig mOpConfig;
	private static ServicesGroupConfig mCommConfig;
	private static ServicesGroupConfig mOffConfig;
	
	@LoadConfig (configType = ConfigType.PRODUCT)
	public static void loadConfig() {
		
		org.bson.Document configDoc = MongoProductConfig.getConfig(RailConstants.PRODUCT_RAIL);

		mOpConfig = new ServicesGroupConfig(CONFIG_PROP_SUPPINTEG, (Document) configDoc.get(CONFIG_PROP_SUPPINTEG));
		mCommConfig = new ServicesGroupConfig(CONFIG_PROP_COMMERCIALS, (Document) configDoc.get(CONFIG_PROP_COMMERCIALS));
		mOffConfig = new ServicesGroupConfig(CONFIG_PROP_OFFERS, (Document) configDoc.get(CONFIG_PROP_OFFERS));
	}
	
	public static ServiceConfig getOperationConfig(String opName) {
		return mOpConfig.getServiceConfig(opName);
	}
	
	public static ServiceConfig getOffersTypeConfig(OffersType offType) {
		return mOffConfig.getServiceConfig(offType.toString());
	}
	
	public static ServiceConfig getCommercialTypeConfig(CommercialsType commType) {
		return mCommConfig.getServiceConfig(commType.toString());
	}

}
