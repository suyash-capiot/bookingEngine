package com.coxandkings.travel.bookingengine.test_suite;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.test_suite.config.TestConfig;
import com.coxandkings.travel.bookingengine.test_suite.config.TestConfigFactory;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;

public abstract class AbstractTestSuite implements TestSuite {

	protected static TestConfig mBEConfig;
	static Map<String,String> mhttpHdrs = new HashMap<String, String>();

	static {
		try {
			mBEConfig = TestConfigFactory.getBEServiceConfig();
		} catch (IOException e) {
			e.printStackTrace();
		}
		mhttpHdrs.put("Content-Type", "application/json");
	}

	protected JSONObject consumeBEService(String operationName,JSONObject reqJson){
		try {
			return  HTTPServiceConsumer.consumeJSONService("BE",new URL(mBEConfig.getBEServiceURI(operationName)) , mhttpHdrs, reqJson);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new JSONObject();
	}

	public static ArrayList<String> getDateRange(String startDate,String endDate, boolean strtInclusive,boolean endInclusive)	{
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		ArrayList<String> dates = new ArrayList<>();

		if(startDate==null || startDate.isEmpty()){
			//System.out.println("No start date is provided");
			return dates;
		}
		if(endDate==null || endDate.isEmpty()){
			//System.out.println("No end date is provided");
			if(strtInclusive)
				dates.add(startDate);
			return dates;
		}
		try
		{
			Calendar start = Calendar.getInstance();
			start.setTime(dateFormat.parse(startDate));

			Calendar end = Calendar.getInstance();
			end.setTime(dateFormat.parse(endDate));

			while(!start.after(end))
			{
				Date targetDay = start.getTime();
				dates.add(dateFormat.format(targetDay));
				start.add(Calendar.DATE, 1);
			}
			if(!dates.isEmpty() && !strtInclusive)
				dates.remove(0);
			if(!dates.isEmpty() && !endInclusive)
				dates.remove(dates.size()-1);   
			return dates;

		}
		catch(Exception e)
		{
			e.printStackTrace();
			return new ArrayList<String>();
		}


	}
	
	protected static int getRandomNumber(int length) {
		return (int) Math.floor(Math.random()*length);
	}
}
