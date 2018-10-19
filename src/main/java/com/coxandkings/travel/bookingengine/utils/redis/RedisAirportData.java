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
public class RedisAirportData extends AbstractRedisLoader implements Constants{

	private static final Logger logger = LogManager.getLogger(RedisAirportData.class);
	private static final String REDIS_KEY_AIRPORTDATA = "be:air:airport";
	public static final String AIRPORT_CITY = "city";
	public static final String AIRPORT_COUNTRY = "country";
	public static final String AIRPORT_CONTINENT = "continent";
	public static final String AIRPORT_STATE = "state";

	@Override
	public void loadConfig() {
		insertAirportInfo();
	}
	 
	public static Iterator<Document> getAirportData() {
		long startTime = System.currentTimeMillis();

		Map<String, Object> props = new HashMap<String, Object>();
		props.put(MDM_PROP_DELETED, false);
		props.put(MDM_PROP_ANCILLARYTYPE, MDM_VAL_AIRPORT);
		
		//For api call we need to send uri paramas i.e. ancillaryType in this case.This is defined in mdm.
		Map<String,String> uriParams = new HashMap<String,String>();
		uriParams.put(MDM_PROP_ANCILLARYTYPE, MDM_VAL_AIRPORT);
		
		Iterator<Document> dataItr = MDMUtils.getDocumentIterator(MDM_COLL_ANCILLARYDATA, props,uriParams);
		logger.debug(String.format("Mongo Airport Details fetch time = %dms", (System.currentTimeMillis() - startTime)));
		
		return dataItr;
	}
	 
	public void insertAirportInfo() {
		long startTime = System.currentTimeMillis();
		
		try ( Jedis jedis = RedisConfig.getRedisConnectionFromPool(); ) {
			mDataItr = getAirportData();
			
			Map<String, String> airportMap = new HashMap<String, String>();
			Document data = null;
			while (mDataItr.hasNext()) {
				data = (Document) mDataItr.next().get(MDM_PROP_DATA);
				if (data != null && data.containsKey(MDM_PROP_IATACODE)) {
					airportMap.put(data.getString(MDM_PROP_IATACODE), data.toJson());
				}
			}
			if(airportMap!=null && !airportMap.isEmpty())
				jedis.hmset(REDIS_KEY_AIRPORTDATA, airportMap);
		}
		logger.info("Redis Airport Data push time: " + (System.currentTimeMillis() - startTime));
	}
	
	public static Map<String, Object> getAirportInfo(String iataCode) {
		if (iataCode == null || iataCode.isEmpty()) {
			return null;
		}
		
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
			String airportAttrs = redisConn.hget(REDIS_KEY_AIRPORTDATA, iataCode);
			JSONObject airportData = new JSONObject(airportAttrs!=null ? airportAttrs.toString() : JSON_OBJECT_EMPTY);
			
			return airportData.toMap();
		}
	}
	 
	public static String getAirportInfo(String iataCode, String key) {
		Map<String, Object> airportInfo = getAirportInfo(iataCode);
		return (airportInfo != null) ? airportInfo.getOrDefault(key, "").toString() : "";
	}

}
