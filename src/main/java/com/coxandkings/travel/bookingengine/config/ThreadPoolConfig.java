package com.coxandkings.travel.bookingengine.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.TrackableExecutor;


public class ThreadPoolConfig implements Constants {
	
	private static final int THREAD_POOL_CORE_SIZE = 10;
	private static final int THREAD_POOL_MAX_SIZE = 20;
	private static final int THREAD_POOL_KEEP_ALIVE_SECONDS = 60;
	private static final int THREAD_POOL_QUEUE_SIZE = 10;
	
	private static ThreadPoolExecutor mThreadPool;
	
	@LoadConfig (configType = ConfigType.COMMON)
	public static void loadConfig() {
		//------------------------------------------------------------
		// Create thread pool to process product search requests

		int coreSize = THREAD_POOL_CORE_SIZE;
		int maxSize = THREAD_POOL_MAX_SIZE;
		long keepAliveSeconds =  THREAD_POOL_KEEP_ALIVE_SECONDS;
		int queueSize = THREAD_POOL_QUEUE_SIZE;

		Document configDoc = MongoProductConfig.getConfig(CONFIG_ID_THREADPOOL);
		if (configDoc != null) {
			coreSize = configDoc.getInteger(CONFIG_PROP_CORETHREADS, THREAD_POOL_CORE_SIZE);
			maxSize = configDoc.getInteger(CONFIG_PROP_MAXTHREADS, THREAD_POOL_MAX_SIZE);
			keepAliveSeconds = configDoc.getInteger(CONFIG_PROP_KEEPALIVESECS, THREAD_POOL_KEEP_ALIVE_SECONDS);
			queueSize = configDoc.getInteger(CONFIG_PROP_QUEUESIZE, THREAD_POOL_QUEUE_SIZE);
		}
		mThreadPool = new ThreadPoolExecutor(coreSize, maxSize, keepAliveSeconds, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize));
	}

//	@Deprecated
//	public static ThreadPoolExecutor getThreadPool() {
//		return mThreadPool;
//	}
	
	public static void execute(TrackableExecutor executor) {
		mThreadPool.execute(executor);
	}
	
    public static void unloadConfig() {
    	mThreadPool.shutdownNow();
    }

	
}
