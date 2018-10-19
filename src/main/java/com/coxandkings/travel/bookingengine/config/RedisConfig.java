package com.coxandkings.travel.bookingengine.config;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisConfig {

	public static final int DFT_REDIS_PORT = 6379;
	private static String mRedisHost;
	private static int mRedisPort;
	private static JedisPool mRedisConnPool;
	private static JedisPoolConfig mRedisConnPoolConfig;
	
	@LoadConfig (configType = ConfigType.COMMON)
	public static void loadConfig() {
		Document redisConfig = MongoProductConfig.getConfig("REDIS");
		Document connConfig = (Document) redisConfig.get("connection");
		mRedisHost = connConfig.getString("host");
		mRedisPort = connConfig.getInteger("port", DFT_REDIS_PORT);
		
		//------------------------------------------------------------
		// Connection pool parameters
		Integer minIdle = connConfig.getInteger("minIdle");
		if (minIdle != null) {
			getRedisConnectionPoolConfig().setMinIdle(minIdle);
		}
		
		Integer maxIdle = connConfig.getInteger("maxIdle");
		if (maxIdle != null) {
			getRedisConnectionPoolConfig().setMaxIdle(maxIdle);
		}
		
		Integer maxTotal = connConfig.getInteger("maxTotal");
		if (maxTotal != null) {
			getRedisConnectionPoolConfig().setMaxTotal(maxTotal);
		}
		
		Integer maxWaitSecs = connConfig.getInteger("maxWaitSecs");
		if (maxWaitSecs != null) {
			Long maxWaitMillis = (long) (maxWaitSecs*1000);
			getRedisConnectionPoolConfig().setMaxWaitMillis(maxWaitMillis);
		}
		//------------------------------------------------------------
		
		mRedisConnPool = (mRedisConnPoolConfig != null) ? new JedisPool(mRedisConnPoolConfig, mRedisHost, mRedisPort) : new JedisPool(mRedisHost, mRedisPort);
	}
	
	private static JedisPoolConfig getRedisConnectionPoolConfig() {
		if (mRedisConnPoolConfig == null) {
			mRedisConnPoolConfig = new JedisPoolConfig();
		}
		return mRedisConnPoolConfig;
	}
	
	public static Jedis getRedisConnectionFromPool() {
		return mRedisConnPool.getResource();
	}
	
	public static void releaseRedisConnectionToPool(Jedis conn) {
		conn.close();
	}
	
	public static void unloadConfig() {
		mRedisConnPool.close();
	}
}
