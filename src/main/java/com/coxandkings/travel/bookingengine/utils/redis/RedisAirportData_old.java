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
public class RedisAirportData_old extends AbstractRedisLoader implements Constants{

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
	 
	public static JSONArray getConfig() {

		//MDM Api link
		//http://10.24.2.5:10002/ancillary/v1/%s?filter=%s
		String apiUrl = MDMConfigV2.getURL(MDM_COLL_ANCILLARYDATA);
		long startTime = System.currentTimeMillis();

		JSONObject props = new JSONObject();
		props.put(MDM_PROP_DELETED, false);
		
		String url = String.format(apiUrl, MDM_VAL_AIRPORT, props.toString());
		String res = MDMUtils.getData(url, false);
		logger.debug(String.format("Mongo Airport Data fetch time = %dms", (System.currentTimeMillis() - startTime)));
		
		return res!=null ? new JSONArray(res) : new JSONArray();
		
	}	 
	 
	public static void insertAirportInfo() {
		long startTime = System.currentTimeMillis();
		
		try ( Jedis jedis = RedisConfig.getRedisConnectionFromPool(); ) {
			JSONArray airportDataArr = getConfig();
			
			Map<String, String> airportMap = new HashMap<String, String>();
			JSONObject data = null;
			for (int i=0;i< airportDataArr.length();i++){
				JSONObject airlineData = airportDataArr.getJSONObject(i);
				data = airlineData.optJSONObject(MDM_PROP_DATA);
				if (data != null && data.has(MDM_PROP_IATACODE)) {
					airportMap.put(data.getString(MDM_PROP_IATACODE), data.toString());
				}
			}
			if(airportMap!=null && !airportMap.isEmpty())
				jedis.hmset(REDIS_KEY_AIRPORTDATA, airportMap);
		}
		catch (Exception x) {
			logger.warn("An exception occurred when loading airport data", x);
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
