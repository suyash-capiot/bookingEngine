package com.coxandkings.travel.bookingengine.orchestrator.rail;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.userctx.UserContext;

import redis.clients.jedis.Jedis;

public class RailBookProcessor  implements RailConstants {
	private static final Logger logger = LogManager.getLogger(RailBookProcessor.class);
	static final String OPERATION_NAME = "Book";

	public static String process(JSONObject reqJson) {
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();){
			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			String redisKey = reqHdrJson.optString(JSON_PROP_SESSIONID).concat("|").concat(PRODUCT).concat("|")
					.concat("reprice");
			Map<String, String> suppFaresMap = redisConn.hgetAll(redisKey);
//			String suppFaresMap = redisConn.hget(redisKey,"railArray");
			
			KafkaBookProducer bookProducer = new KafkaBookProducer();
			UserContext usrContxt = UserContext.getUserContextForSession(reqHdrJson);
			sendBookingKafkaMessage(bookProducer,reqJson,suppFaresMap,usrContxt);
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"status\": \"ERROR\"}";
		}
		return "SUCCESS";
	}

	private static void sendBookingKafkaMessage(KafkaBookProducer bookProducer, JSONObject reqJson, Map<String, String> suppFaresMap, UserContext usrCtx) throws Exception {
		JSONObject obj=new JSONObject(suppFaresMap.get("railArray"));
		JSONObject requestBodyJson=obj.getJSONObject(JSON_PROP_RESBODY);
		requestBodyJson.put(JSON_PROP_PNR, reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_PNR));
		requestBodyJson.put(JSON_PROP_BOOKID, reqJson.getJSONObject(JSON_PROP_REQBODY).getString(JSON_PROP_BOOKID));
		
		JSONObject kafkaMsg=new JSONObject(new JSONTokener(reqJson.toString()));
		JSONObject reqHeaderJson = kafkaMsg.getJSONObject(JSON_PROP_REQHEADER);
		reqHeaderJson.put(JSON_PROP_GROUPOFCOMPANIESID, usrCtx.getOrganizationHierarchy().getGroupCompaniesId());
		reqHeaderJson.put(JSON_PROP_GROUPCOMPANYID, usrCtx.getOrganizationHierarchy().getGroupCompanyId());
		reqHeaderJson.put(JSON_PROP_COMPANYID, usrCtx.getOrganizationHierarchy().getCompanyId());
		reqHeaderJson.put(JSON_PROP_SBU, usrCtx.getOrganizationHierarchy().getSBU());
		reqHeaderJson.put(JSON_PROP_BU, usrCtx.getOrganizationHierarchy().getBU());
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put(JSON_PROP_PROD, JSON_PROP_PROD);
		kafkaMsg.getJSONObject(JSON_PROP_REQBODY).put("operation", "book");
		bookProducer.runProducer(1, kafkaMsg);
		//System.out.println(kafkaMsg);
		logger.trace(String.format("Rail Book Request Kafka Message: %s", kafkaMsg.toString()));
		
	}
}
