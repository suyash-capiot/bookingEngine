package com.coxandkings.travel.bookingengine.orchestrator.login;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.config.MDMConfig;
import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

import redis.clients.jedis.Jedis;

public class LoginProcessor implements LoginConstants {

	public static String process(JSONObject reqJson) {
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool()) {
			
			JSONObject reqHeader = reqJson.optJSONObject(JSON_PROP_REQHEADER);
			JSONObject reqBody = reqJson.getJSONObject(JSON_PROP_REQBODY);
			JSONObject loginRes =new JSONObject();
		
			
			String redisKey = getRedisKey(reqBody);			
			//Jedis redisConn = RedisConfig.getRedisConnectionFromPool();
			Map<String, String> loginResMap = redisConn.hgetAll(redisKey);
		    
		   if(loginResMap!=null &&loginResMap.size()!=0) {
			   
			   	loginRes.put(VERSION, loginResMap.get(VERSION));
			   	loginRes.put(USER, loginResMap.get(USER));
			   	loginRes.put(TOKEN, loginResMap.get(TOKEN));
			   	loginRes.put(LAST_UPDATED, loginResMap.get(LAST_UPDATED));
			   	loginRes.put(LOGIN_TIME, loginResMap.get(LOGIN_TIME));
			   	loginRes.put(EXPIRE_IN, loginResMap.get(EXPIRE_IN));
			   	loginRes.put(STATUS, loginResMap.get(STATUS));
			   	loginRes.put(ID, loginResMap.get(ID));
			   
		   }
		   
		   else {
			ServiceConfig clntLgnCfg = MDMConfig.getApiConfig("clientLoginURL");
			if(clntLgnCfg!=null) {
				loginRes = HTTPServiceConsumer.consumeJSONService("MDM/Login",clntLgnCfg.getServiceURL(),
						clntLgnCfg.getHttpHeaders(), reqBody);
			}
			//String res  = MDMUtils.getData(String.format(MDMConfigV2.getURL("clientLoginURL")), true);
			
			//TODO: also handle for http code 204 response as we dont have user in the cache and MDM is saying user is logged in.
			if(loginRes==null)
				{
				
				JSONObject errorRes = new JSONObject();
				errorRes.put("errorCode", "TRLERR400");
				errorRes.put("errorMessage", "Request processing error");
				return errorRes.toString();
				
				}
		
			loginResMap =   new HashMap<String,String>();
			
			//TODO: check what all details we need to put in redis for login response
			loginResMap.put(VERSION, loginRes.get(VERSION).toString());
			loginResMap.put(USER, loginRes.get(USER).toString());
			loginResMap.put(TOKEN, loginRes.get(TOKEN).toString());
			loginResMap.put(LAST_UPDATED, loginRes.get(LAST_UPDATED).toString());
			loginResMap.put(LOGIN_TIME, loginRes.get(LOGIN_TIME).toString());
			loginResMap.put(EXPIRE_IN, loginRes.get(EXPIRE_IN).toString());
			loginResMap.put(STATUS, loginRes.get(STATUS).toString());
			loginResMap.put(ID, loginRes.get(ID).toString());

			
			redisConn.hmset(redisKey, loginResMap);
			redisConn.pexpire(redisKey, (long) (MDMConfig.getClientLoginRedisTTLMins() * 60 * 1000));
		   
		   }
			//RedisConfig.releaseRedisConnectionToPool(redisConn);

			//UserContextV2 usrCtx = UserContextV2.getUserContextForSession(reqHeader);

			//TODO: we will confirm from WEM that what all fields they need from us. Also on what condition we will add thode details in the login response
			//loginRes.put(USER_CONTEXT, new JSONObject(usrCtx.toString()));
			
			JSONObject resJson = new JSONObject();
			resJson.put(JSON_PROP_RESHEADER, reqHeader);
			resJson.put(JSON_PROP_RESBODY, loginRes);
			
			return resJson.toString();
		} 
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// TODO: change it later :)
		return "Failure";

	}
	
	
	private static String getRedisKey(JSONObject reqJson) {
		
		String redisKey = String.format("%s%c%s%c%s",reqJson.optString("userID"),KEYSEPARATOR,reqJson.optString("password"),KEYSEPARATOR,reqJson.optString("clientID"));
		
		return redisKey;
		
	}


	public static JSONObject processGetLogin(String userID, String clientID, String password) {
		
		JSONObject reqBodyJson =  new JSONObject();
		reqBodyJson.put("userID", userID);
		reqBodyJson.put("clientID", clientID);
		reqBodyJson.put("password", password);
		
		JSONObject reqJson = new JSONObject();
		reqJson.put(JSON_PROP_REQBODY, reqBodyJson);
		return new JSONObject(process(reqJson));
	}

}
