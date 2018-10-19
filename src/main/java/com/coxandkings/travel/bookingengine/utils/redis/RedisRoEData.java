package com.coxandkings.travel.bookingengine.utils.redis;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.mongodb.DBCollection;
import com.mongodb.client.MongoCursor;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

@Component
public class RedisRoEData extends AbstractRedisLoader implements Constants {

	private static final Logger logger = LogManager.getLogger(RedisRoEData.class);
	private static final String COLL_ROE = "roe";
	private static final String REDIS_KEY_ROEDATA = "be:common:roe";

	@Override
	public void loadConfig() {
		insertDailyROEData();
	}

	private boolean insertDailyROEData() {

		mDataItr = getROEData("Daily ROE");
		Map<String, HashMap<String, String>> roeMap = getDateWiseROEMap(mDataItr);

		try (Jedis jedis = RedisConfig.getRedisConnectionFromPool(); Pipeline pipeline = jedis.pipelined();) {

			for (Entry<String, HashMap<String, String>> roeEntry : roeMap.entrySet()) {
				pipeline.hmset(roeEntry.getKey(), roeEntry.getValue());
			}
			pipeline.sync();
			pipeline.close();
			return true;
		} catch (Exception x) {
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, HashMap<String, String>> getDateWiseROEMap(Iterator<Document> roeDocItr) {

		// Note:If Roe type other than daily needs to be loaded expire date and
		// roeValueType(sellinROE,ROE) depending on type needs to be incorporated
		HashMap<String, HashMap<String, String>> roeMap = new HashMap<String, HashMap<String, String>>();
		ArrayList<Document> roeDataLst, mrktDataLst;
		Document roeData;
		String fromCcy, toCcy, roeVal, mrktName, roeKey;
		Object effDate;
		Calendar cal;

		while (roeDocItr.hasNext()) {

			Document roeDoc = roeDocItr.next();
			roeDataLst = (ArrayList<Document>) roeDoc.getOrDefault("ROE", new ArrayList<Document>());

			effDate = roeDoc.get("effectiveFrom");
			// Ideally this should not be done as date in ui seems to be in ISO date format
			// But prev documents saved have date defined as year,month and day in a
			// document.
			if (effDate instanceof Date) {
				try {
					effDate = DATE_FORMAT.format(DATE_FORMAT.parse((String) effDate));
				} catch (ParseException e) {
					logger.warn(String.format("Effective date parse error for ROE document with id: %s",
							roeDoc.getString(DBCollection.ID_FIELD_NAME)));
					continue;
				}
			} else if (effDate instanceof Document) {
				cal = Calendar.getInstance();
				cal.set(((JSONObject) effDate).getInt("year"), ((JSONObject) effDate).getInt("month") - 1,
						((JSONObject) effDate).getInt("day"));
				effDate = DATE_FORMAT.format(cal.getTime());
			} else {
				// As per BRD eff Date is mandatory but not in UI.This should not reach ideally
				logger.warn(String.format("Effective date not found for ROE document with id: %s",
						roeDoc.getString(DBCollection.ID_FIELD_NAME)));
				continue;
			}

			for (int i = 0; i < roeDataLst.size(); i++) {

				roeData = roeDataLst.get(i);
				fromCcy = roeData.getString("fromCurrency");
				toCcy = roeData.getString("toCurrency");
				if (!roeData.containsKey("sellingROE")) {
					logger.warn(String.format(
							"No selling ROE value found for document with id: %s, fromCurrency: %s, toCurrency: %s",
							roeDoc.get(DBCollection.ID_FIELD_NAME), fromCcy, toCcy));
					// This should never happen as selling roe is mandatory when daily roe is
					// defined.This check needs to be added in UI!!!
					continue;
				}
				roeVal = roeData.get("sellingROE").toString();
				mrktDataLst = (ArrayList<Document>) roeDoc.getOrDefault("companyMarkets", new ArrayList<Document>());
				int j = 0;
				do {
					// TODO:As per BRD only one market should be present for this type of roe.But it
					// is disabled in UI.Need to change!!!BR-05,CKIL-105730
					mrktName = mrktDataLst.isEmpty() ? "" : (String) mrktDataLst.get(j).getOrDefault("name", "");
					roeKey = getROEKey(fromCcy, toCcy, mrktName);
					if (!roeMap.containsKey(roeKey))
						roeMap.put(roeKey, new HashMap<String, String>());
					roeMap.get(roeKey).put(effDate.toString(), roeVal);
					j++;
				} while (j < mrktDataLst.size());

			}
		}
		return roeMap;
	}

	private static Iterator<Document> getROEData(String roeType) {
		long startTime = System.currentTimeMillis();

		Map<String, Object> props = new HashMap<String, Object>();
		props.put("roeType", roeType);
		props.put(MDM_PROP_DELETED, false);

		Iterator<Document> dataItr = MDMUtils.getDocumentIterator(COLL_ROE, props);
		logger.debug(String.format("Mongo ROE fetch time = %dms", (System.currentTimeMillis() - startTime)));

		return dataItr;
	}

	public static BigDecimal getRateOfExchange(String fromCcy, String toCcy, String market, Date cutOffDate) {
		// TODO:This code can be optimized by sorting and storing data in json during
		// insertion time
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			// Retrieve map and sort
			Map<String, String> roeDateMap = redisConn.hgetAll(getROEKey(fromCcy, toCcy, market));
			if (roeDateMap == null || roeDateMap.isEmpty())
				return Utils.isStringNullOrEmpty(market) ? new BigDecimal(1) : getRateOfExchange(fromCcy, toCcy);
			TreeMap<String, String> sortedRoeDateMap = new TreeMap<String, String>(roeDateMap);
			Entry<String, String> roeEntry = sortedRoeDateMap.floorEntry(DATE_FORMAT.format(cutOffDate==null?new Date():cutOffDate));
			return (roeEntry != null) ? Utils.convertToBigDecimal(roeEntry.getValue(), 1) : new BigDecimal(1);
		}
	}

	public static BigDecimal getRateOfExchange(String fromCcy, String toCcy, Date cutOffDate) {
		return getRateOfExchange(fromCcy, toCcy, "",cutOffDate==null?new Date():cutOffDate);
	}
	
	public static BigDecimal getRateOfExchange(String fromCcy, String toCcy) {
		return getRateOfExchange(fromCcy, toCcy, "",new Date());
	}
	
	public static BigDecimal getRateOfExchange(String fromCcy, String toCcy,String market) {
		return getRateOfExchange(fromCcy, toCcy, market,new Date());
	}

	private static String getROEKey(String fromCcy, String toCcy, String market) {
		return String.format("%s:%s|%s|%s", REDIS_KEY_ROEDATA, fromCcy, toCcy, market);
	}

}
