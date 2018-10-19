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
public class RedisAirlineData_old extends AbstractRedisLoader implements Constants{
	
	private static final Logger logger = LogManager.getLogger(RedisAirlineData.class);
	private static final String REDIS_KEY_AIRLINEDATA = "be:air:airline";
	public static final String AIRLINE_CODE = "code";
	public static final String AIRLINE_NAME = "name";
	public static final String AIRLINE_TYPE = "value";

	@Override
	public void loadConfig() {
		long startTime = System.currentTimeMillis();

		try ( Jedis jedis = RedisConfig.getRedisConnectionFromPool(); ) {
			JSONArray airlineDtlsArr = getConfig();

			Map<String, String> airlineMap = new HashMap<String, String>();
			JSONObject data = null;
			for (int i=0;i< airlineDtlsArr.length();i++){
				JSONObject airlineData = airlineDtlsArr.getJSONObject(i);
				data =  airlineData.optJSONObject(MDM_PROP_DATA);
				if (data != null && data.has(MDM_PROP_CODE)) {
					airlineMap.put(data.getString(MDM_PROP_CODE), data.toString());
				}
			}
			if(!airlineMap.isEmpty())
				jedis.hmset(REDIS_KEY_AIRLINEDATA, airlineMap);
		}
		catch (Exception x) {
			logger.warn("An exception occurred when loading airline details", x);
		}

		logger.info("Redis Airline Details push time: " + (System.currentTimeMillis() - startTime));
	}

	public static JSONArray getConfig() {
		
		//MDM Api link
		//http://10.24.2.5:10002/ancillary/v1/%s?filter=%s
		String apiUrl = MDMConfigV2.getURL(MDM_COLL_ANCILLARYDATA);
		long startTime = System.currentTimeMillis();

		JSONObject props = new JSONObject();
		props.put(MDM_PROP_DELETED, false);
		
		String url = String.format(apiUrl, MDM_VAL_AIRLINEDETAILS, props.toString());
		String res = MDMUtils.getData(url, false);
		logger.debug(String.format("Mongo Airline Details fetch time = %dms", (System.currentTimeMillis() - startTime)));
		
		return res!=null ? new JSONArray(res) : new JSONArray();
		
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
