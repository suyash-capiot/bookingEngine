package com.coxandkings.travel.bookingengine.config.mongo;

import org.bson.Document;

public class BsonDocMongoConfig extends AbstractMongoConfig {

	BsonDocMongoConfig(Document configDoc) {
		mMongoURL = configDoc.getString(MONGO_URL_PROP);
		mMongoUser = configDoc.getString(MONGO_USER_PROP);
		mMongoPwd = configDoc.getString(MONGO_PASSWORD_PROP);
		mMongoDB = configDoc.getString(MONGO_DATABASE_PROP);
		formatMongoURI();
		System.out.println(String.format("BsonDocMongoConfig: mMongoURI=<%s>", mMongoURI));
	}
	
}
