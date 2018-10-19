package com.coxandkings.travel.bookingengine.config.mongo;

public interface MongoConfig {
	static final String BPEL_CALLING_CLASS_PROP = "oracle.bpel.calling.class";
	static final String BPEL_DVM_LOC = "oramds:/apps/common/Configurations.dvm";
	static String BPEL_CALLING_CLASS = "com.collaxa.cube.engine.ext.bpel.common.wmp.BPELxExecWMP";
	
	static final String MONGO_URL_PROP = "mongoURL";
	static final String MONGO_USER_PROP = "mongoUser";
	static final String MONGO_PASSWORD_PROP = "mongoPassword";
	static final String MONGO_DATABASE_PROP = "mongoDatabase";
	static final String MONGO_CONFIG_FILE_PROP = "mongo.config.file";
	
	static final String COLL_CACHE_LOCK = "RedisLock";
	static final String COLL_PRODUCT_CONFIG = "ProductConfig";
	
	public String getConnectionURI();
	
	public String getDatabaseName();
}
