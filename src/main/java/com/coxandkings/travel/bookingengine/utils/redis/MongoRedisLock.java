package com.coxandkings.travel.bookingengine.utils.redis;

import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.mongo.MongoConfig;
import com.coxandkings.travel.bookingengine.config.mongo.MongoConnect;
import com.mongodb.DBCollection;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.IndexOptions;

public class MongoRedisLock {

	private static MongoConnect mMongoConn = MongoConnect.newInstance(null);
	private static MongoCollection<Document> mCacheLckCllctn = mMongoConn.getCollection(MongoConfig.COLL_CACHE_LOCK);
	
	private static long TTL_SEC = 300;
	private static final String CREATED_AT = "createdAt";
	private static final String EXPIRE_AFTER_SECS = "expireAfterSeconds";
	private static final String TTL_INDEX_NAME = "createdAt_TTL";
	
	static {
		boolean doCreateIdx = true;
		ListIndexesIterable<Document> idxDocList = mCacheLckCllctn.listIndexes();
		MongoCursor<Document> id = idxDocList.iterator();
		while (id.hasNext()) {
			Document idxDoc = id.next();
			if (TTL_INDEX_NAME.equals(idxDoc.getString("name")) == false) {
				continue;
			}
			
			// Check if the expiration time defined in the index is same as the one 
			// configured in this class. If not, delete existing index. New index 
			// will be created in next step
			if (TTL_SEC != (long) idxDoc.getOrDefault(EXPIRE_AFTER_SECS, 0)) {
				// Drop old index 
				mCacheLckCllctn.dropIndex(TTL_INDEX_NAME);
				break;
			}

			doCreateIdx = false;
			break;
		}

		// Create a new TTL index
		if (doCreateIdx) {
			IndexOptions idxOpts = new IndexOptions();
			idxOpts.name(TTL_INDEX_NAME);
			idxOpts.expireAfter(TTL_SEC, TimeUnit.SECONDS);
			mCacheLckCllctn.createIndex(new Document(CREATED_AT, 1), idxOpts);
		}
	}
	
	public static boolean getLock(String key) {
		HashMap<String, Object> props = new HashMap<String, Object>();
		
		props.put(DBCollection.ID_FIELD_NAME, key);
		props.put(CREATED_AT, new Date());
		
		try {
			mCacheLckCllctn.insertOne(new Document(props));
			return true;
		}
		catch(Exception e) {
			return false;
		}
	}
	
	public static boolean releaseLock(String key) {
		HashMap<String, Object> props = new HashMap<String, Object>();
		
		props.put(DBCollection.ID_FIELD_NAME, key);
		try {
			mCacheLckCllctn.deleteOne(new Document(props));
			return true;
		}
		catch(Exception e){
			return false;
		}
	}
	
	public static boolean releaseAllLocks() {
		try {
			mCacheLckCllctn.deleteMany(new Document());
			return true;
		}
		catch(Exception e){
			return false;
		}
	}
	
}
