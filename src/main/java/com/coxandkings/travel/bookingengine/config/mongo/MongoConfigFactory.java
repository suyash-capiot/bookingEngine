package com.coxandkings.travel.bookingengine.config.mongo;

import org.bson.Document;

public class MongoConfigFactory {

	static String MONGO_URL_PROP = "mongoURL";
	static String MONGO_USER_PROP = "mongoUser";
	static String MONGO_PASSWORD_PROP = "mongoPassword";
	static String MONGO_DATABASE_PROP = "mongoDatabase";

	public static MongoConfig getMongoConfig() throws Exception {
		return getMongoConfig(null);
	}

	public static MongoConfig getMongoConfig(Document configDoc) throws Exception {
		return (configDoc != null) ? (new BsonDocMongoConfig(configDoc)) : (new FileMongoConfig());
	}
}
