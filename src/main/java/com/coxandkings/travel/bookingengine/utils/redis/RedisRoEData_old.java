package com.coxandkings.travel.bookingengine.utils.redis;

import java.math.BigDecimal;
import java.text.ParseException;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

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
import com.mongodb.DBCollection;


import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

@Deprecated
//@Component
public class RedisRoEData_old extends AbstractRedisLoader implements Constants{

	private static final Logger logger = LogManager.getLogger(RedisRoEData_old.class);
	private static final String COLL_ROE = "roe";
	private static final String REDIS_KEY_ROEDATA = "be:common:roe";

	@Override
	public void loadConfig() {
		insertDailyROEData();
	}
	
	private static boolean insertDailyROEData() {
		
		JSONArray roeArr = getROEData("Daily ROE");
		Map<String,HashMap<String,String>> roeMap = getDateWiseROEMap(roeArr);
		
		try ( Jedis jedis = RedisConfig.getRedisConnectionFromPool();
			  Pipeline pipeline = jedis.pipelined(); ) {
			
			for(Entry<String, HashMap<String, String>> roeEntry:roeMap.entrySet()) {
				pipeline.hmset(roeEntry.getKey(), roeEntry.getValue());
			}
			pipeline.sync(); 
			pipeline.close();
			return true;
		}
		catch (Exception x) {
			return false;
		}
	}

	private static Map<String,HashMap<String,String>> getDateWiseROEMap(JSONArray roeArr){
		
		//Note:If Roe type other than daily needs to be loaded expire date and roeValueType(sellinROE,ROE) depending on type needs to be incorporated
		HashMap<String,HashMap<String,String>> roeMap = new HashMap<String,HashMap<String,String>>();
		JSONArray roeDataArr, mrktDataArr;
		JSONObject roeData;
		String fromCcy,toCcy,roeVal,mrktName,roeKey;
		Object effDate;
		Calendar cal;
		
		for(int i=0;i<roeArr.length();i++) {
			
			JSONObject roeDoc = roeArr.getJSONObject(i);
			roeDataArr = roeDoc.optJSONArray("ROE")!=null ?  roeDoc.getJSONArray("ROE") : new JSONArray();
			
			effDate = roeDoc.opt("effectiveFrom");
			//Ideally this should not be done as date in ui seems to be in ISO date format
			//But prev documents saved have date defined as year,month and day in a document.
			if(effDate instanceof String) {
				try {
					effDate = DATE_FORMAT.format(DATE_FORMAT.parse((String) effDate));
				} catch (ParseException e) {
					logger.warn(String.format("Effective date parse error for ROE document with id: %s", roeDoc.getString(DBCollection.ID_FIELD_NAME)));
					continue;
				}
			}
			else if(effDate instanceof JSONObject) {
				cal = Calendar.getInstance();
				cal.set(((JSONObject) effDate).getInt("year"),((JSONObject) effDate).getInt("month")-1,((JSONObject) effDate).getInt("day"));
				effDate =  DATE_FORMAT.format(cal.getTime());
			}
			else {
				//As per BRD eff Date is mandatory but not in UI.This should not reach ideally
				logger.warn(String.format("Effective date not found for ROE document with id: %s", roeDoc.getString(DBCollection.ID_FIELD_NAME)));
				continue;
			}
			
			for(int j=0;j<roeDataArr.length();j++) {
				
				roeData = roeDataArr.getJSONObject(j);
				fromCcy = roeData.optString("fromCurrency");
				toCcy = roeData.optString("toCurrency");
				if(Utils.isStringNotNullAndNotEmpty(fromCcy) || Utils.isStringNotNullAndNotEmpty(toCcy))
				{
					logger.warn(String.format("One of the currency is evaluated to be null or empty for document  with id: %s, fromCurrency: %s, toCurrency: %s", roeDoc.getString(DBCollection.ID_FIELD_NAME), fromCcy, toCcy));
					continue;
				}
				if(!roeData.has("sellingROE")) {
					logger.warn(String.format("No selling ROE value found for document with id: %s, fromCurrency: %s, toCurrency: %s", roeDoc.getString(DBCollection.ID_FIELD_NAME), fromCcy, toCcy));
					//This should never happen as selling roe is mandatory when daily roe is defined.This check needs to be added in UI!!!
					continue;
				}
				roeVal = roeData.get("sellingROE").toString();
				mrktDataArr = roeDoc.optJSONArray("companyMarkets")!=null ? roeDoc.getJSONArray("companyMarkets") : new JSONArray();
				int k=0;
				do {
					//TODO:As per BRD only one market should be present for this type of roe.But it is disabled in UI.Need to change!!!BR-05,CKIL-105730
					mrktName = mrktDataArr.length()==0 ? "" : mrktDataArr.getJSONObject(j).optString("name");
					roeKey = getROEKey(fromCcy, toCcy, mrktName);
					if(!roeMap.containsKey(roeKey))
						roeMap.put(roeKey, new HashMap<String,String>());
					roeMap.get(roeKey).put(effDate.toString(), roeVal);
					k++;
				}while(k<mrktDataArr.length());
				
				
			}
		}
		return roeMap;
	}
	
	private static JSONArray getROEData(String roeType) {

		String apiUrl = MDMConfigV2.getURL(COLL_ROE);
		long startTime = System.currentTimeMillis();

		JSONObject props = new JSONObject();
		props.put("roeType", roeType);
		props.put(MDM_PROP_DELETED, false);
		
		String url = String.format(apiUrl, props.toString());
		String res = MDMUtils.getData(url, true);
		
		JSONObject resJson = new JSONObject(res!=null ? res : JSON_OBJECT_EMPTY);
		JSONArray data = resJson.optJSONArray("data");
		logger.debug(String.format("Mongo ROE fetch time = %dms", (System.currentTimeMillis() - startTime)));
		
		return data!=null ? data : new JSONArray();
		
	}


	public static BigDecimal getRateOfExchange(String fromCcy, String toCcy, String market) {
		//TODO:This code can be optimized by sorting and storing data in json during insertion time
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			//Retrieve map and sort
			Map<String,String> roeDateMap = redisConn.hgetAll(getROEKey(fromCcy, toCcy, market));
			if(roeDateMap == null || roeDateMap.isEmpty())
				return Utils.isStringNullOrEmpty(market)?new BigDecimal(1):getRateOfExchange(fromCcy, toCcy);
			TreeMap<String,String> sortedRoeDateMap = new TreeMap<String,String>(roeDateMap);
			Entry<String, String> roeEntry = sortedRoeDateMap.floorEntry(DATE_FORMAT.format(new Date()));
			return (roeEntry != null) ? Utils.convertToBigDecimal(roeEntry.getValue(), 1) : new BigDecimal(1);
		}
	}
	
	public static BigDecimal getRateOfExchange(String fromCcy, String toCcy) {
		return getRateOfExchange(fromCcy, toCcy, "");
	}

	private static String getROEKey(String fromCcy, String toCcy, String market) {
		return String.format("%s:%s|%s|%s", REDIS_KEY_ROEDATA, fromCcy, toCcy, market);
	}

}
