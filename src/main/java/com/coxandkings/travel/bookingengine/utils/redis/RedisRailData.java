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
public class RedisRailData extends AbstractRedisLoader implements Constants {

	private static final Logger logger = LogManager.getLogger(RedisRailData.class);
	private static final String REDIS_KEY_STATIONDATA = "be:common:railcitycode";
	private static final String REDIS_KEY_STATIONCODEDATA = "be:common:stationCode";
	public static final String COLL_ANCILLARY_DATA = "ancillaryData";
	public static final String STATION_CODE = "stationCode";
	public static final String MDM_VAL_RAILWAYSTATION = "railwayStation";
	
	@Override
	void loadConfig() {
		Document data = null;
		long startTime = System.currentTimeMillis();
		try ( Jedis jedis = RedisConfig.getRedisConnectionFromPool(); ) {
			
			mDataItr = getStationData();
			
			Map<String, String> stationNameMap = new HashMap<String, String>();
			Map<String, String> stationCodeMap = new HashMap<String, String>();
			 
			while (mDataItr.hasNext()) {
				Document stationData = mDataItr.next();
				data = (Document) stationData.get(MDM_PROP_DATA);
				if (data == null) {
					continue;
				}
				
				String dataJSONStr = data.toJson();
				if (data.containsKey(MDM_PROP_VALUE)) {
					stationNameMap.put(data.getString(MDM_PROP_VALUE), dataJSONStr);
				}

				if (data.containsKey(STATION_CODE)) {
					stationCodeMap.put(data.getString(STATION_CODE), dataJSONStr);
				}
			}
			
			if (stationNameMap.size() > 0) {
				jedis.hmset(REDIS_KEY_STATIONDATA, stationNameMap);
			}
			if (stationCodeMap.size() > 0) {
				jedis.hmset(REDIS_KEY_STATIONCODEDATA, stationCodeMap);
			}
		}
			logger.info("Redis Railway Data push time: " ,(System.currentTimeMillis() - startTime));
	}
	
	public static Iterator<Document> getStationData() {
		long startTime = System.currentTimeMillis();

		Map<String, Object> props = new HashMap<String, Object>();
		props.put(MDM_PROP_DELETED, false);
		props.put(MDM_PROP_ANCILLARYTYPE, MDM_VAL_RAILWAYSTATION);
		
		//For api call we need to send uri paramas i.e. ancillaryType in this case.This is defined in mdm.
		Map<String,String> uriParams = new HashMap<String,String>();
		uriParams.put(MDM_PROP_ANCILLARYTYPE, MDM_VAL_RAILWAYSTATION);
		
		Iterator<Document> dataItr = MDMUtils.getDocumentIterator(MDM_COLL_ANCILLARYDATA, props,uriParams);
		logger.debug(String.format("Mongo station details fetch time = %dms", (System.currentTimeMillis() - startTime)));
		
		return dataItr;
	}
	
	
	public static Map<String, Object> getStationCodeInfo(String stationCode) {
		Map<String, Object> cityAttrs = new HashMap<String, Object>();
		if (stationCode == null) {
			return cityAttrs;
		}
		try ( Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
			String stationAttrs = redisConn.hget(REDIS_KEY_STATIONCODEDATA, stationCode);
			JSONObject stationData = new JSONObject(stationAttrs!=null ? stationAttrs.toString() : JSON_OBJECT_EMPTY);
			
			return stationData.toMap();
		}
	}
	
	 
	public static String getStationCodeInfo(String stationCode, String key) {
		return (stationCode == null || key == null) ? "" : getStationCodeInfo(stationCode).getOrDefault(key, "").toString();
	}
	
	public static Map<String, Object> getStationInfo(String stationName) {
		Map<String, Object> cityAttrs = new HashMap<String, Object>();
		if (stationName == null) {
			return cityAttrs;
		}
		
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			String stationAttrs = redisConn.hget(REDIS_KEY_STATIONDATA, stationName);
			
			JSONObject stationData = new JSONObject(stationAttrs!=null ? stationAttrs.toString() : JSON_OBJECT_EMPTY);
			return stationData.toMap();
		}
	}
	 
	public static String getStationInfo(String stationName, String key) {
		return (stationName == null || key == null) ? "" : getStationInfo(stationName).getOrDefault(key, "").toString();
	}

}
