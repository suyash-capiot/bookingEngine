package com.coxandkings.travel.bookingengine.config.mongo;

import java.util.Map;
import java.util.HashMap;

import org.bson.Document;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;

public class MongoProductConfig {
	private static MongoConnect mMongoConn = MongoConnect.newInstance(null);
	private static MongoCollection<Document> mProductConfig = mMongoConn.getCollection(MongoConfig.COLL_PRODUCT_CONFIG);
	
	public static Document getConfig(String product) {
		Map<String, Object> props = new HashMap<String, Object>();
		props.put("_id", product);
		
		Document findDoc = new Document(props);
		FindIterable<Document> res = mProductConfig.find(findDoc);
		return res.first();
	}
	
}
