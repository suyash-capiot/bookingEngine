package com.coxandkings.travel.bookingengine.utils.redis;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.utils.Constants;

import redis.clients.jedis.Jedis;

@Component
public class RedisAirlineData extends AbstractRedisLoader implements Constants{
	
	private static final Logger logger = LogManager.getLogger(RedisAirlineData.class);
	private static final String REDIS_KEY_AIRLINEDATA = "be:air:airline";
	public static final String AIRLINE_CODE = "code";
	public static final String AIRLINE_NAME = "name";
	public static final String AIRLINE_TYPE = "value";

	@Override
	public void loadConfig() {
		long startTime = System.currentTimeMillis();

		try ( Jedis jedis = RedisConfig.getRedisConnectionFromPool(); ) {
			mDataItr = getAirlineData();

			Map<String, String> airlineMap = new HashMap<String, String>();
			Document data = null;
			while (mDataItr.hasNext()) {
				data = (Document) mDataItr.next().get(MDM_PROP_DATA);
				if (data != null && data.containsKey(MDM_PROP_CODE)) {
					airlineMap.put(data.getString(MDM_PROP_CODE), data.toJson());
				}
			}
			
			if(!airlineMap.isEmpty())
				jedis.hmset(REDIS_KEY_AIRLINEDATA, airlineMap);
		}

		logger.info("Redis Airline Details push time: " + (System.currentTimeMillis() - startTime));
	}

	public static Iterator<Document> getAirlineData() {
		long startTime = System.currentTimeMillis();

		Map<String, Object> props = new HashMap<String, Object>();
		props.put(MDM_PROP_DELETED, false);
		props.put(MDM_PROP_ANCILLARYTYPE, MDM_VAL_AIRLINEDETAILS);
		
		//For api call we need to send uri paramas i.e. ancillaryType in this case.This is defined in mdm.
		Map<String,String> uriParams = new HashMap<String,String>();
		uriParams.put(MDM_PROP_ANCILLARYTYPE, MDM_VAL_AIRLINEDETAILS);
		
		Iterator<Document> dataItr = MDMUtils.getDocumentIterator(MDM_COLL_ANCILLARYDATA, props,uriParams);
		logger.debug(String.format("Mongo Airline Details fetch time = %dms", (System.currentTimeMillis() - startTime)));
		
		return dataItr;
	}

	public static Map<String, Object> getAirlineDetails(String airlineCode) {
		
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
			String airlineAttrs = redisConn.hget(REDIS_KEY_AIRLINEDATA, airlineCode);
			
			JSONObject airlineData = new JSONObject(airlineAttrs!=null ? airlineAttrs.toString() : JSON_OBJECT_EMPTY);
			return airlineData.toMap();
		}
	}

	public static String getAirlineDetails(String airlineCode, String key) {
		return getAirlineDetails(airlineCode).getOrDefault(key, "").toString();
	}
}
