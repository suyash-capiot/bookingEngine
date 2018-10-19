package com.coxandkings.travel.bookingengine.config.mongo;

import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MongoConnect {
	
	private MongoClient mongoClient;
	private MongoDatabase mongoDB;
	
//	private static class SingletonHolder {
//		private static MongoConnect INSTANCE = null;
//		static {
//			try {
//				INSTANCE = new MongoConnect(); 
//			}
//			catch (Exception x) {
//				x.printStackTrace();
//				throw new RuntimeException(x);
//			}
//		}
//	}
	
	private MongoConnect() throws Exception {
		this(null);
	}
	
	private MongoConnect(Document configDoc) throws Exception {
		MongoConfig mongoCfg = MongoConfigFactory.getMongoConfig(configDoc);
		mongoClient = new MongoClient(new MongoClientURI(mongoCfg.getConnectionURI()));
		mongoDB = mongoClient.getDatabase(mongoCfg.getDatabaseName());
	}
	
	public MongoCollection<Document> getCollection(String collName) {
		return mongoDB.getCollection(collName);
	}
	
	public static MongoConnect newInstance(Document configDoc) {
		try { 
			return (new MongoConnect(configDoc));
		}
		catch (Exception x) {
			return null;
		}
	}
	
	public void close() {
		mongoClient.close();
	}
}
