package com.coxandkings.travel.bookingengine.orchestrator.cruise;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class GetDateRange {

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
	
}
