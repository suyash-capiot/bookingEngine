package com.coxandkings.travel.bookingengine.utils.redis;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.coxandkings.travel.bookingengine.config.MDMConfigV2;
import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.Utils;

import redis.clients.jedis.Jedis;

@Deprecated
//@Component
public class RedisCityData_old extends AbstractRedisLoader implements Constants{

	private static final Logger logger = LogManager.getLogger(RedisCityData.class);
	private static final String REDIS_KEY_CITYCODEDATA = "be:common:citycode";
	private static final String REDIS_KEY_CITYNAMEDATA = "be:common:cityname";
	public static final String COLL_ANCILLARY_DATA = "ancillaryData";
	public static final String MDM_PROP_CITYCODE = "cityCode";
	
	@Override
	public void loadConfig() {
		JSONObject data = null;
		long startTime = System.currentTimeMillis();
		try ( Jedis jedis = RedisConfig.getRedisConnectionFromPool(); ) {
			
			JSONArray cityDataArr = getConfig();
			
			Map<String, String> cityCodeMap = new HashMap<String, String>();
			Map<String, String> cityNameMap = new HashMap<String, String>();
			for (int i=0; i<cityDataArr.length();i++) {
				JSONObject cityDataDoc = cityDataArr.getJSONObject(i);
				data = cityDataDoc.optJSONObject(MDM_PROP_DATA);
				
				if (data!=null && data.has(MDM_PROP_CITYCODE)) {
					cityCodeMap.put(data.getString(MDM_PROP_CITYCODE), data.toString());
				}

				if (data!=null && data.has(MDM_PROP_VALUE)) {
					cityNameMap.put(data.getString(MDM_PROP_VALUE), data.toString());
				}
			}
			
			if (cityCodeMap.size() > 0) {
				jedis.hmset(REDIS_KEY_CITYCODEDATA, cityCodeMap);
			}
			if (cityNameMap.size() > 0) {
				jedis.hmset(REDIS_KEY_CITYNAMEDATA, cityNameMap);
			}
		}
		catch (Exception x) {
			logger.warn("An exception occurred when loading city data", x);
		}
		
		logger.info("Redis City Data push time: " + (System.currentTimeMillis() - startTime));
	}
	 
	 
	public static JSONArray getConfig() {

		String apiUrl = MDMConfigV2.getURL(COLL_ANCILLARY_DATA);
		long startTime = System.currentTimeMillis();

		JSONObject props = new JSONObject();
		props.put(MDM_PROP_DELETED, false);
		
		String url = String.format(apiUrl, MDM_VAL_CITY, props.toString());
		String res = MDMUtils.getData(url, false);
		
		logger.debug(String.format("Mongo City Data fetch time = %dms", (System.currentTimeMillis() - startTime)));
		
		return res!=null ? new JSONArray(res) : new JSONArray();
	}	 
	 
	public static Map<String, Object> getCityInfo(String cityName) {
		Map<String,Object> cityAttrs = new HashMap<String,Object>();
		if (cityName == null) {
			return cityAttrs;
		}
		
		try ( Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
			String cityNameAttrs = redisConn.hget(REDIS_KEY_CITYNAMEDATA, cityName);
			JSONObject cityData = new JSONObject(cityNameAttrs!=null ? cityNameAttrs.toString() : JSON_OBJECT_EMPTY);
			
			return cityData.toMap();
		}
	}
	 
	public static String getCityInfo(String cityName, String key) {
		return (cityName == null || key == null) ? "" : getCityInfo(cityName).getOrDefault(key, "").toString();
	}
	
	public static Map<String, Object> getCityCodeInfo(String cityCode) {
		Map<String, Object> cityAttrs = new HashMap<String, Object>();
		if (cityCode == null) {
			return cityAttrs;
		}
		
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			String cityCodeAttrs = redisConn.hget(REDIS_KEY_CITYCODEDATA, cityCode);
			JSONObject cityData = new JSONObject(cityCodeAttrs!=null ? cityCodeAttrs.toString() : JSON_OBJECT_EMPTY);
			
			return cityData.toMap();
		}
	}
	 
	public static String getCityCodeInfo(String cityCode, String key) {
		return (cityCode == null || key == null) ? "" : getCityCodeInfo(cityCode).getOrDefault(key, "").toString();
	}
}
