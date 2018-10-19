package com.coxandkings.travel.bookingengine.utils.redis;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.config.MDMConfigV2;
import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.Utils;

import redis.clients.jedis.Jedis;
import org.springframework.stereotype.Component;

//@Component
public class RedisHotelData extends AbstractRedisLoader implements Constants{

	private static final Logger logger = LogManager.getLogger(RedisHotelData.class);
	public static final String COLL_HOTEL_DATA = "productAccomodations";
	private static final String REDIS_KEY_HOTELCODEDATA = "be:common:hotelcode";

	
	@Override
	public void loadConfig() {
		insertHotelInfo();
	}
	 
	public static JSONArray getConfig(int page, int buffer) {
		
		//http://10.24.2.5:10021/product/v1/Accommodation?page=%d&count=%d
		String apiUrl = MDMConfigV2.getURL(COLL_HOTEL_DATA);
		long startTime = System.currentTimeMillis();

		JSONObject props = new JSONObject();
		props.put(MDM_PROP_DELETED, false);
		
		String url = String.format(apiUrl, page, buffer, props.toString());
		String res = MDMUtils.getData(url, true);
		
		JSONObject resJson = new JSONObject(res!=null ? res : JSON_OBJECT_EMPTY);
		JSONArray data = resJson.optJSONArray("data");
		logger.debug(String.format("Mongo Hotel Data fetch time = %dms", (System.currentTimeMillis() - startTime)));
		
		return data!=null ? data : new JSONArray();
	}	 
	 
	public static void insertHotelInfo() {
		long startTime = System.currentTimeMillis();
		//Jedis jedis = null;
		//Pipeline pipeline = null;
		
		// TODO: Change the try block to try-with-resources
		try ( Jedis jedis = RedisConfig.getRedisConnectionFromPool();) {
			
			Map<String, String> hotelCodeMap = new HashMap<String, String>();
			int page=1;
			do {
				JSONArray hotelDataArr = getConfig(page++, MDMConfigV2.getBufferSize());
				if(hotelDataArr.length()==0)
					break;
				
				JSONObject obj = null;
				JSONObject data = new JSONObject();
				for (int i=0;i<hotelDataArr.length();i++) {
					JSONObject hotelDataDoc = hotelDataArr.getJSONObject(i);
				
					obj = hotelDataDoc.optJSONObject("accomodationInfo");
					if (obj != null && obj.has("commonProductId")) {
						
						data.put("commonProductId", obj.optString("commonProductId"));
						data.put("name", obj.optString("name"));
						data.put("brand", obj.optString("brand"));
						data.put("chain",  obj.optString("chain"));
						if(obj.has("address")) {
							JSONObject address = obj.getJSONObject("address");
							data.put("city", address.optString("city"));
						}
						else {
							data.put("city", "");
						}
						hotelCodeMap.put(obj.getString("commonProductId"), data.toString());
					}
				}
				
			}while(true);
			
			if(!hotelCodeMap.isEmpty())
				jedis.hmset(REDIS_KEY_HOTELCODEDATA, hotelCodeMap);
			
		}
		catch (Exception x) {
			logger.warn("An exception occurred when loading hotel data", x);
		}
//		finally {
//			if (pipeline != null) {
//				try { 
//					pipeline.close(); 
//				}
//				catch (Exception x) { 
//					// TODO: Check if it is safe to eat this exception 
//				}
//			}
//			if (jedis != null) {
//				RedisConfig.releaseRedisConnectionToPool(jedis);
//			}
//		}
		logger.info("Redis Hotel Data push time: " + (System.currentTimeMillis() - startTime));
	}
	
	public static Map<String, Object> getHotelInfo(String hotelCode) {
		
		Map<String, Object> hotelAttr = new HashMap<String, Object>();
		if(hotelCode==null)
			return hotelAttr;
		
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool(); ) {
			String hotelAttrs = redisConn.hget(REDIS_KEY_HOTELCODEDATA, hotelCode);
			JSONObject hotelData = new JSONObject(hotelAttrs!=null ? hotelAttrs.toString() : JSON_OBJECT_EMPTY);
			
			return hotelData.toMap();
		}
	}
	 
	public static String getHotelInfo(String hotelCode, String key) {
		return (hotelCode == null || key == null) ? "" : getHotelInfo(hotelCode).getOrDefault(key, "").toString();
	}
}
