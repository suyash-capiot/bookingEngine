package com.coxandkings.travel.bookingengine.utils.redis;

import java.util.Iterator;

import javax.annotation.PostConstruct;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import com.coxandkings.travel.bookingengine.utils.redis.MongoRedisLock;
import com.mongodb.client.MongoCursor;

abstract class AbstractRedisLoader {

	private static final Logger logger = LogManager.getLogger(AbstractRedisLoader.class);
	protected Iterator<Document> mDataItr;
	
	@PostConstruct
	public void lockAndload() {
		String className = this.getClass().getName();
		try{
			if(MongoRedisLock.getLock(className)) {
				loadConfig();
			}
		}
		catch(Exception e) {
			logger.warn("An exception occurred while loading ",this.getClass().getName());
			MongoRedisLock.releaseLock(className);
		}
		finally {
			if (mDataItr instanceof MongoCursor<?>)
				((MongoCursor<?>) mDataItr).close();
		}
	}

	abstract void loadConfig();
}
