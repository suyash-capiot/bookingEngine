package com.coxandkings.travel.bookingengine.utils;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;

public class CommonUtil implements TimeLimitConstants{
	
	public static Object getKeyFromValue(Map hm, Object value) {
	    for (Object o : hm.keySet()) {
	      if (hm.get(o).equals(value)) {
	        return o;
	      }
	    }
	    return null;
	  }
	
	public static BigDecimal calculateValue( String mode, BigDecimal percent, BigDecimal amount,BigDecimal totalAmount) {
		BigDecimal price = null,div;
		BigDecimal bg = new BigDecimal(100);

		if (mode.equalsIgnoreCase("percentage")) {
			div = percent.divide(bg);
			price = div.multiply(totalAmount);
		} 
		else if (mode.equalsIgnoreCase("amount")) {
			price = amount;
		}
		//System.out.println("price :---- "+price);
		return price;
	}
	
	public static boolean comparePerPaxFare(double fromBillingAmount, double toBillingAmount,List<Double> paxTypeFareList) {
		for( Double fares : paxTypeFareList) {
			if(fares<fromBillingAmount || fares>toBillingAmount) {
				return false;
			}
		}
		//System.out.println("Amount perPax matching*******");
		return true;
	}
	
	public static Map<String, Date> checkandReturnTravelDates(Document travelDate, Document tlSetFor,Document reconfirmationSetFor, Date bookDateReq, Date travelDateReq) {
		
		String DurationType = "";
		int count = 0;
		Map<String, Date> datesMap = new HashMap<>();
		
		DurationType = travelDate.getString(MDM_TL_TD_DAYSORMONTHS);
		count = travelDate.getInteger(MDM_TL_TD_COUNT);
		Date date = getTimeDate(calculatePaymentDate("From Booking Date", bookDateReq, travelDateReq, DurationType, count).getTime());
		if((date.toString()).equals(getTimeDate(travelDateReq).toString())) {
			if(tlSetFor != null) {
				DurationType = tlSetFor.getString(MDM_TL_TD_TLS_DAYSORMONTHS);
				count = tlSetFor.getInteger(MDM_TL_TD_TLS_COUNT);
				datesMap.put(MAP_TIMELIMITSETFOR, getTimeDate(calculatePaymentDate("Prior To Travel Date", bookDateReq, travelDateReq, DurationType, count).getTime()));
			}
			else
				datesMap.put(MAP_TIMELIMITSETFOR, null);

			if(reconfirmationSetFor != null) {
			DurationType = reconfirmationSetFor.getString(MDM_TL_TD_RS_DAYSORMONTHS);
			count = reconfirmationSetFor.getInteger(MDM_TL_TD_RS_COUNT);
			datesMap.put(MAP_RECONFIRMATIONSETFOR, getTimeDate(calculatePaymentDate("Prior To Travel Date", bookDateReq, travelDateReq, DurationType, count).getTime()));
			}
			else
				datesMap.put(MAP_RECONFIRMATIONSETFOR, null);
			return datesMap;
		}
		else
			return null;
	}

	public static Map<String, Date> checkandReturnBookingDates(Document bookingDate, Document tlSetFor,Document reconfirmationSetFor, Date bookDateReq, Date travelDateReq) {
		
		String DurationType = "";
		int count = 0;
		Map<String, Date> datesMap = new HashMap<>();
		
		DurationType = bookingDate.getString(MDM_TL_BD_DAYSORMONTHS);
		count = bookingDate.getInteger(MDM_TL_BD_COUNT);
		Date date = getTimeDate(calculatePaymentDate("Prior To Travel Date", bookDateReq, travelDateReq, DurationType, count).getTime());
		if((date.toString()).equals(getTimeDate(bookDateReq).toString())) {
			if(tlSetFor != null) {
				DurationType = tlSetFor.getString(MDM_TL_BD_TLS_DAYSORMONTHS);
				count = tlSetFor.getInteger(MDM_TL_BD_TLS_COUNT);
				datesMap.put(MAP_TIMELIMITSETFOR, getTimeDate(calculatePaymentDate("From Booking Date", bookDateReq, travelDateReq, DurationType, count).getTime()));
			}
			else
				datesMap.put(MAP_TIMELIMITSETFOR, null);
			
			if(reconfirmationSetFor != null) {
			DurationType = reconfirmationSetFor.getString(MDM_TL_BD_RS_DAYSORMONTHS);
			count = reconfirmationSetFor.getInteger(MDM_TL_BD_RS_COUNT);
			datesMap.put(MAP_RECONFIRMATIONSETFOR, getTimeDate(calculatePaymentDate("From Booking Date", bookDateReq, travelDateReq, DurationType, count).getTime()));
			}
			else
				datesMap.put(MAP_RECONFIRMATIONSETFOR, null);
			return datesMap;
		}
		else
			return null;
	}
	
	public static Calendar calculatePaymentDate(String condition, Date bookDate, Date travelDate, String DurationType, int count) {
		Calendar date = Calendar.getInstance();
		if (condition.trim().equalsIgnoreCase("From Booking Date")) {
			date.setTime(bookDate);
			//System.out.println("DATE=" + bookDate);
			if (DurationType.equalsIgnoreCase("Days")) {
				//System.out.println("DAYS=" + count);
				date.add(Calendar.DATE, count); // Adding 5 days
			} 
			else if (DurationType.equalsIgnoreCase("Hours")) {
				date.add(Calendar.HOUR_OF_DAY, count); // adds one hour
			}else if(DurationType.equalsIgnoreCase("Months")) {
				date.add(Calendar.MONTH, count);
			}
		}
		else if (condition.trim().equalsIgnoreCase("Prior To Travel Date")) {
			date.setTime(travelDate);
			//System.out.println("DATE=" + travelDate);
			count -= (2 * count);
			if (DurationType.equalsIgnoreCase("Days")) {
				////System.out.println("DAYS=" + count);
				date.add(Calendar.DATE, count); // Adding 5 days
			}
			else if (DurationType.equalsIgnoreCase("Hours")) {
				date.add(Calendar.HOUR_OF_DAY, count); // adds one hour
			}else if(DurationType.equalsIgnoreCase("Months")) {
				date.add(Calendar.MONTH, count);
			}
		}
		return date;
	}
	
	public static Date getTimeDate(Date date) {
	    Date res = date;
	    Calendar calendar = Calendar.getInstance();
	    calendar.setTime(date);
	    calendar.set(Calendar.HOUR_OF_DAY, 23);
	    calendar.set(Calendar.MINUTE, 59);
	    calendar.set(Calendar.SECOND, 59);
	    res = calendar.getTime();
	    return res;
	}
	
	public static boolean checkStringParams(String stringFromRequest, String stringFromMDM, String paramToCheck) {
		/*if (stringFromRequest == null || stringFromRequest.equalsIgnoreCase("")) {
			return true;
		}*/
		if (stringFromMDM == null || stringFromMDM.equalsIgnoreCase("")) {
			return true;
		}
		if (stringFromRequest != null) {
			if (stringFromRequest.equalsIgnoreCase(stringFromMDM)) 
				return true;
			else 
				return false;
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public static Document fetchSupplierSettlement(String supplierRef) {
		Document periodicity = new Document();
		List<Document> commercials = new ArrayList<Document>();
		//FindIterable<Document> MDMSupplierSettlement = MDMUtils.getSupplierSettlement(supplierRef);
		List<Document> MDMSupplierSettlement = MDMUtils.getSupplierSettlementV2(supplierRef);
		//MongoCursor<Document> MDMSupplierIter = MDMSupplierSettlement.iterator();
		
		//while (MDMSupplierIter.hasNext()) {
		for(Document mdmSupplierSettlement:MDMSupplierSettlement) {
			//Document mdmSupplierSettlement = MDMSupplierIter.next();
			commercials.add((Document) mdmSupplierSettlement.get(MDM_SS_NONCOMMISSIONABLECOMM));
			commercials.add((Document) mdmSupplierSettlement.get(MDM_SS_COMMISSIONABLECOMM));
			commercials.add((Document) mdmSupplierSettlement.get(MDM_SS_RECEIVABLECOMM));
			commercials.add((Document) mdmSupplierSettlement.get(MDM_SS_PAYABLECOMM));
			
			for(Document commercial : commercials) {
				if(commercial.containsKey(MDM_SS_COMMERCIALHEADS)) {
					List<Document> commercialHeads = (List<Document>) commercial.get(MDM_SS_COMMERCIALHEADS);
					if(commercialHeads != null && !commercialHeads.isEmpty()) {

						if(commercialHeads.get(0).containsKey(MDM_SS_PERIODICITY))
							periodicity = (Document) commercialHeads.get(0).get(MDM_SS_PERIODICITY);

						else {
							if(commercialHeads.get(0).containsKey(MDM_SS_CREDITSETTLE)) {
								List<Document> creditSettlement = (List<Document>) commercialHeads.get(0).get(MDM_SS_CREDITSETTLE);
								if(!creditSettlement.isEmpty() && creditSettlement.get(0).containsKey(MDM_SS_DEFINEPERIODICTY)) {
									Document definePeriodicity = (Document) creditSettlement.get(0).get(MDM_SS_DEFINEPERIODICTY);
									if(definePeriodicity.containsKey(MDM_SS_DP_PERIODICITY))
										periodicity = (Document) definePeriodicity.get(MDM_SS_DP_PERIODICITY);
								}
							}
							if(commercialHeads.get(0).containsKey(MDM_SS_COMMISSION)) {
								Document commission = (Document) commercialHeads.get(0).get(MDM_SS_COMMISSION);
								if(commission.containsKey(MDM_SS_COMM_PERIODICITY))
									periodicity = (Document) commission.get(MDM_SS_COMM_PERIODICITY);
							}
						}
					}
				}
			}
		}
		return periodicity;
	}

	public static Date checkSettlementTerms(Document periodicity, Date paymentDate, Date travelDate) throws ParseException {
		Date dueDate = null;
		if(!periodicity.isEmpty()){
			String repeatFreq = periodicity.getString(MDM_SS_P_REPEATFREQ);
			switch(repeatFreq)
			{
			case ENDVALIDITY : if(paymentDate.compareTo(new Date())>0 && paymentDate.compareTo(travelDate)<0) 
				dueDate = paymentDate;
			else 
				dueDate = new Date();
			break;
			case MONTHLY  : Document monthly = (Document) periodicity.get(MDM_SS_P_MONTHLY);
			dueDate = checkMonthlySettlement(monthly,paymentDate, travelDate);
			break;
			case QUARTERLY : List <Document> quarterly= (List<Document>) periodicity.get(MDM_SS_P_QUARTERLY);  
			dueDate = checkHalfYearlyOrQuarterlySettlement(quarterly,paymentDate,travelDate);
			break;
			case FORTNIGHTLY : List<Document> fortnightly = (List<Document>) periodicity.get(MDM_SS_P_FORTNIGHTLY);
			dueDate = checkFortnightlySettlement(fortnightly,paymentDate,travelDate);	
			break;
			case HALFYEARLY : List<Document> halfYearly = (List<Document>) periodicity.get(MDM_SS_P_HALFYEARLY);
			dueDate = checkHalfYearlyOrQuarterlySettlement(halfYearly,paymentDate, travelDate);
			break;
			case WEEKLY : Document weekly = (Document) periodicity.get(MDM_SS_P_WEEKLY);
			String weeklySchedule = weekly.getString(MDM_SS_P_W_WEEKLYSCHD);
			if(weeklySchedule.equalsIgnoreCase(MDM_SS_P_W_WEEKLYSCHD_DAY)) {
				Document day = (Document) weekly.get(MDM_SS_P_W_DAY);
				dueDate = checkWeeklyDaysSettlement(day,paymentDate,travelDate);
			}
			else if(weeklySchedule.equalsIgnoreCase(MDM_SS_P_W_WEEKLYSCHD_DATES)) {
				List<Document> dates = (List<Document>) weekly.get(MDM_SS_P_W_DATES);
				dueDate = checkWeeklyDatesSettlement(dates, paymentDate,travelDate);
			}
			break;
			case YEARLY : Document yearly = (Document) periodicity.get(MDM_SS_P_YEARLY);
			dueDate = checkYearlySettlement(yearly,paymentDate,travelDate);
			break;
			default : dueDate = paymentDate;
			break;
			}
		}
		else 
			dueDate = paymentDate;
		return dueDate;
	}
	
	public static Date checkWeeklyDatesSettlement(List<Document> dates, Date paymentDate, Date travelDate) throws ParseException {
		int fromYear = 0, toYear = 0, settlementDueYear = 0;
		Calendar cal = Calendar.getInstance();
		for(Document date : dates) {
			
			int fromDate = date.getInteger(MDM_SS_P_W_DATES_FROMDATE);
			String mdmFromMonth = date.getString(MDM_SS_P_W_DATES_FROMMONTH);
			Date date1 = new SimpleDateFormat("MMMM").parse(mdmFromMonth);
			cal.setTime(date1);
			int fromMonth = cal.get(Calendar.MONTH)+1;
			if(date.containsKey(MDM_SS_P_W_DATES_FROMYEAR))
				fromYear = date.getInteger(MDM_SS_P_W_DATES_FROMYEAR);
			else
				fromYear = cal.get(Calendar.YEAR);
			String FromDate = fromDate+"-"+fromMonth+"-"+fromYear+"T00:00:00";
			Date settlementFromDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(FromDate);
			////System.out.println("settlementFromDate = "+settlementFromDate);

			int toDate = date.getInteger(MDM_SS_P_W_DATES_TODATE);
			String mdmToMonth = date.getString(MDM_SS_P_W_DATES_TOMONTH);
			Date date2 = new SimpleDateFormat("MMMM").parse(mdmToMonth);
			cal.setTime(date2);
			int toMonth = cal.get(Calendar.MONTH)+1;
			if(date.containsKey(MDM_SS_P_W_DATES_TOYEAR))
				toYear = date.getInteger(MDM_SS_P_W_DATES_TOYEAR);
			else if(fromMonth > toMonth)
				toYear = cal.get(Calendar.YEAR)+1;
			else if(fromMonth < toMonth)
				toYear = cal.get(Calendar.YEAR);
			String ToDate = toDate+"-"+toMonth+"-"+toYear+"T23:59:59";
			Date settlementToDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(ToDate);
			////System.out.println("settlementToDate = "+settlementToDate);

			if((settlementFromDate.before(paymentDate) || settlementFromDate.equals(paymentDate)) 
					&& (settlementToDate.after(paymentDate) || settlementToDate.equals(paymentDate))) {

				int settlementDay = date.getInteger(MDM_SS_P_W_DATES_SETTLEDUEDATE);
				String settlementMonth = date.getString(MDM_SS_P_W_DATES_SETTLEDUEMONTH);
				Date date3 = new SimpleDateFormat("MMMM").parse(settlementMonth);
				cal.setTime(date3);
				int settlementDueMonth = cal.get(Calendar.MONTH)+1;
				if(date.containsKey(MDM_SS_P_W_DATES_SETTLEDUEYEAR))
					settlementDueYear = date.getInteger(MDM_SS_P_W_DATES_SETTLEDUEYEAR);
				else
					settlementDueYear = cal.get(Calendar.YEAR);
				String settlementDate = settlementDay+"-"+settlementDueMonth+"-"+settlementDueYear+"T23:59:59";
				Date settlementDueDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(settlementDate);
				if(paymentDate.compareTo(settlementDueDate)>0) {
					if(settlementDueDate.compareTo(new Date())>0 && settlementDueDate.compareTo(travelDate)<0) {
						return settlementDueDate;
					}
					else 
						return paymentDate;
				}
				else {
					if(paymentDate.compareTo(new Date())>0 && paymentDate.compareTo(travelDate)<0) 
						return paymentDate;
					else 
						return new Date();
				}
			}
		}
		return new Date();
	}

	public static Date checkWeeklyDaysSettlement(Document day, Date paymentDate, Date travelDate) {
		
		String fromDay = day.getString(MDM_SS_P_W_DAY_FROMDAY);
		String toDay = day.getString(MDM_SS_P_W_DAY_TODAY);
		////System.out.println("fromDay = "+fromDay+"--------toDay = "+toDay);
		String dueDay = new SimpleDateFormat("EEEE").format(paymentDate);
		//System.out.println("dueDay = "+dueDay);
		Date settlementDueDate = null;
		String settlementDueDay = day.getString(MDM_SS_P_W_DAY_SETTLEDUEDAY);
		
		Map<String , Integer> map = new LinkedHashMap<>(7);
	        // values of the Calendar field
		 map.put("Monday",Calendar.MONDAY);               // 2
		 map.put("Tuesday",Calendar.TUESDAY);             // 3
		 map.put("Wednesday",Calendar.WEDNESDAY);         // 4
		 map.put("Thursday",Calendar.THURSDAY);           // 5
		 map.put("Friday",Calendar.FRIDAY);               // 6
		 map.put("Saturday",Calendar.SATURDAY);           // 7
		 map.put("Sunday" ,Calendar.SUNDAY);			  // 1
		 int diff = 0;
		 int from = map.get(fromDay);
		 int to = map.get(toDay);
		 int due = map.get(dueDay);
		 int set = map.get(settlementDueDay);
		 Calendar c = Calendar.getInstance();
		 
		 if(from < to){
			 if(due <= set){
				 //return due
				 c.setTime(paymentDate);
				 diff = set - due;
				 c.add(Calendar.DATE, (diff));
				 //System.out.println("diff : "+diff);
				 //System.out.println("settle : "+c.getTime());
				 settlementDueDate = c.getTime();
				 //System.out.println("due : "+paymentDate);
				 //System.out.println("paymentDate : "+paymentDate);
				 //System.out.println("1 returned due");
			 }
			 else if(set < due){
				 //return settle
				 c.setTime(paymentDate);
				 diff = (due-set);
				 c.add(Calendar.DATE, (-diff));
				 //System.out.println("diff : "+diff);
				 //System.out.println("settle : "+c.getTime());
				 settlementDueDate = c.getTime();
				 //System.out.println("due : "+paymentDate);
				 //System.out.println("settlementDueDate : "+settlementDueDate);
				 //System.out.println("2 returned settle");
			 }
		 }
		 else if(from > to){
			 if(due <= set){
				 if(set <= to){
					 //return due
					 c.setTime(paymentDate);
					 diff = (set-due);
					 c.add(Calendar.DATE, (diff));
					 //System.out.println("diff : "+diff);
					 //System.out.println("settle : "+c.getTime());
					 settlementDueDate = c.getTime();
					 //System.out.println("due : "+paymentDate);
					 //System.out.println("paymentDate : "+paymentDate);
					 //System.out.println("3.1 returned due");

				 }
				 else{
					 //return settle
					 c.setTime(paymentDate);
					 diff = (set-due)-7;
					 c.add(Calendar.DATE, (diff));
					 //System.out.println("diff : "+diff);
					 //System.out.println("settle : "+c.getTime());
					 settlementDueDate = c.getTime();
					 //System.out.println("due : "+paymentDate);
					 //System.out.println("settlementDueDate : "+settlementDueDate);
					 //System.out.println("3.2 returned settle");
				 }
			 }
			 else if(set < due){
				 //return settle
				 c.setTime(paymentDate);
				 diff =(due-set);
				 c.add(Calendar.DATE, (-diff));
				 //System.out.println("diff : "+diff);
				 //System.out.println("settle : "+c.getTime());
				 settlementDueDate = c.getTime();
				 //System.out.println("due : "+paymentDate);
				 settlementDueDate = c.getTime();
				 //System.out.println("settlementDueDate : "+settlementDueDate);
				 //System.out.println("4 returned settle");
			 }
		 }
		
		 if(paymentDate.compareTo(settlementDueDate)>0) {
			 if(settlementDueDate.compareTo(new Date())>0 && settlementDueDate.compareTo(travelDate)<0) 
				 return settlementDueDate;
			 else 
				 return paymentDate;
		 }
		 else {
			 if(paymentDate.compareTo(new Date())>0 && paymentDate.compareTo(travelDate)<0) 
				 return paymentDate;
			 else 
				 return new Date();
		 } 
	}

	public static Date checkYearlySettlement(Document yearly, Date paymentDate, Date travelDate) throws ParseException {
		int fromYear = 0, toYear = 0, settlementDueYear = 0;
		Calendar cal = Calendar.getInstance();
		
		int fromDate = yearly.getInteger(MDM_SS_P_YEARLY_FROMDATE);
		String mdmFromMonth = yearly.getString(MDM_SS_P_YEARLY_FROMMONTH);
		Date date1 = new SimpleDateFormat("MMMM").parse(mdmFromMonth);
		cal.setTime(date1);
		int fromMonth = cal.get(Calendar.MONTH)+1;
		if(yearly.containsKey(MDM_SS_P_YEARLY_FROMYEAR))
			fromYear = yearly.getInteger(MDM_SS_P_YEARLY_FROMYEAR);
		else
			fromYear = cal.get(Calendar.YEAR);
		String FromDate = fromDate+"-"+fromMonth+"-"+fromYear+"T00:00:00";
		Date settlementFromDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(FromDate);
		//System.out.println("settlementFromDate = "+settlementFromDate);

		int toDate = yearly.getInteger(MDM_SS_P_YEARLY_TODATE);
		String mdmToMonth = yearly.getString(MDM_SS_P_YEARLY_TOMONTH);
		Date date2 = new SimpleDateFormat("MMMM").parse(mdmToMonth);
		cal.setTime(date2);
		int toMonth = cal.get(Calendar.MONTH)+1;
		if(yearly.containsKey(MDM_SS_P_YEARLY_TOYEAR))
			toYear = yearly.getInteger(MDM_SS_P_YEARLY_TOYEAR);
		else if(fromMonth > toMonth)
			toYear = cal.get(Calendar.YEAR)+1;
		else if(fromMonth < toMonth)
			toYear = cal.get(Calendar.YEAR);
		String ToDate = toDate+"-"+toMonth+"-"+toYear+"T23:59:59";
		Date settlementToDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(ToDate);
		//System.out.println("settlementToDate = "+settlementToDate);

		if((settlementFromDate.before(paymentDate) || settlementFromDate.equals(paymentDate)) 
				&& (settlementToDate.after(paymentDate) || settlementToDate.equals(paymentDate))) {

			int settlementDay = yearly.getInteger(MDM_SS_P_YEARLY_SETTLEDUEDATE);
			String settlementMonth = yearly.getString(MDM_SS_P_YEARLY_SETTLEDUEMONTH);
			Date date3 = new SimpleDateFormat("MMMM").parse(settlementMonth);
			cal.setTime(date3);
			int settlementDueMonth = cal.get(Calendar.MONTH)+1;
			if(yearly.containsKey(MDM_SS_P_YEARLY_SETTLEDUEYEAR))
				settlementDueYear = yearly.getInteger(MDM_SS_P_YEARLY_SETTLEDUEYEAR);
			else
				settlementDueYear = cal.get(Calendar.YEAR);
			String settlementDate = settlementDay+"-"+settlementDueMonth+"-"+settlementDueYear+"T23:59:59";
			Date settlementDueDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(settlementDate);
			if(paymentDate.compareTo(settlementDueDate)>0) {
				if(settlementDueDate.compareTo(new Date())>0 && settlementDueDate.compareTo(travelDate)<0) 
					return settlementDueDate;
				else 
					return paymentDate;
			}
			else {
				if(paymentDate.compareTo(new Date())>0 && paymentDate.compareTo(travelDate)<0) 
					return paymentDate;
				else 
					return new Date();
			}
		}
		return new Date();
	}

	public static Date checkFortnightlySettlement(List<Document> fortNightlyArray, Date paymentDate, Date travelDate) throws ParseException {
		
		DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");
		String d = formatter.format(paymentDate);
		paymentDate = (Date)formatter.parse(d);
		Calendar c = Calendar.getInstance();
		c.setTime(paymentDate);
		int date = c.get(Calendar.DATE); 

		for(Document fortNightly : fortNightlyArray) {
			if(((String) fortNightly.get(MDM_SS_P_FORTNIGHT)).equalsIgnoreCase("First Fortnight (1-15)") && 1<=date && date<=15) {
				int settlementDay = fortNightly.getInteger(MDM_SS_P_FORTNIGHTLY_SETTLEDUEDAY);
				String settlementDueMonth = fortNightly.getString(MDM_SS_P_FORTNIGHTLY_SETTLEDUEMONTH);
				paymentDate = compareFortNightlyDate(settlementDueMonth, settlementDay, paymentDate, travelDate);
				//System.out.println(paymentDate);
			}
			else if(((String) fortNightly.get(MDM_SS_P_FORTNIGHT)).equalsIgnoreCase("Second Fortnight (16- End of Month)") && 16<=date && date<=31) {
				int settlementDay = fortNightly.getInteger(MDM_SS_P_FORTNIGHTLY_SETTLEDUEDAY);
				String settlementDueMonth = fortNightly.getString(MDM_SS_P_FORTNIGHTLY_SETTLEDUEMONTH);
				paymentDate = compareFortNightlyDate(settlementDueMonth, settlementDay, paymentDate, travelDate);
				//System.out.println(paymentDate);
			}
		}

		return paymentDate;
	}

	public static Date compareFortNightlyDate(String settlementDueMonth, int settlementDay, Date paymentDate, Date travelDate) throws ParseException {
		int year;
		Calendar cal = Calendar.getInstance();
		if(settlementDueMonth.equalsIgnoreCase("Current")) {
			int month = cal.get(Calendar.MONTH)+1;
			year = cal.get(Calendar.YEAR);
			String settlementDate = settlementDay+"-"+month+"-"+year+"T23:59:59";
			Date settlementDueDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(settlementDate);
			if(paymentDate.compareTo(settlementDueDate)>0) {
				if(settlementDueDate.compareTo(new Date())>0 && settlementDueDate.compareTo(travelDate)<0) 
					return settlementDueDate;
				else 
					return paymentDate;
			}
			else {
				if(paymentDate.compareTo(new Date())>0 && paymentDate.compareTo(travelDate)<0) 
					return paymentDate;
				else 
					return new Date();
			}
		}
		else if(settlementDueMonth.equalsIgnoreCase("Next")) {
			int month = cal.get(Calendar.MONTH)+2;
			if(month > 12) {
				month -= 12;
				year = cal.get(Calendar.YEAR)+1;
			}
			else
				year = cal.get(Calendar.YEAR);
			String settlementDate = settlementDay+"-"+month+"-"+year+"T23:59:59";
			Date settlementDueDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(settlementDate);
			if(paymentDate.compareTo(settlementDueDate)>0) {
				if(settlementDueDate.compareTo(new Date())>0 && settlementDueDate.compareTo(travelDate)<0) 
					return settlementDueDate;
				else 
					return paymentDate;
			}
			else {
				if(paymentDate.compareTo(new Date())>0 && paymentDate.compareTo(travelDate)<0) 
					return paymentDate;
				else 
					return new Date();
			}
		}
		return new Date();
	}
	
	public static Date checkMonthlySettlement(Document monthly, Date paymentDate, Date travelDate) throws ParseException {
		int year;
		int settlementDueDay =  monthly.getInteger(MDM_SS_P_MONTHLY_SETTLEDUEDAY);
		Calendar cal = Calendar.getInstance();
		int month = cal.get(Calendar.MONTH)+2;	
		if(month > 12) {
			month = month - 12;
			year = cal.get(Calendar.YEAR) + 1;
			//System.out.println("Inside if month =" + month);
		}
		else 
			year= cal.get(Calendar.YEAR);
		//System.out.println(year);
		String dueDate = settlementDueDay + "-" + month + "-" + year + "T23:59:59";
		SimpleDateFormat output = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss");
		Date settlementDueDate = output.parse(dueDate);
		if(paymentDate.compareTo(settlementDueDate)>0) {
			if(settlementDueDate.compareTo(new Date())>0 && settlementDueDate.compareTo(travelDate)<0) 
				return settlementDueDate;
			else 
				return paymentDate;
		}
		else {
			if(paymentDate.compareTo(new Date())>0 && paymentDate.compareTo(travelDate)<0) 
				return paymentDate;
			else 
				return new Date();
		}
	}	

	public static Date checkHalfYearlyOrQuarterlySettlement(List<Document> halfYearly, Date paymentDate, Date travelDate) throws ParseException {
		int fromYear = 0, toYear = 0 ;
		int  settlementDueYear= 0; 
		Calendar cal = Calendar.getInstance();
		for(Document halfYearlyDoc : halfYearly) {
			String mdmFromMonth = halfYearlyDoc.getString(MDM_SS_P_HALFYEARLY_FROMMONTH);
			Date date1 = new SimpleDateFormat("MMMM").parse(mdmFromMonth);
			cal.setTime(date1);
			int fromMonth = cal.get(Calendar.MONTH)+1;
			cal.setTime(paymentDate);
			if(halfYearlyDoc.containsKey(MDM_SS_P_HALFYEARLY_FROMYEAR))
				fromYear = halfYearlyDoc.getInteger(MDM_SS_P_HALFYEARLY_FROMYEAR);
			else
				fromYear = cal.get(Calendar.YEAR);
			String FromDate = "01"+"-"+fromMonth+"-"+fromYear+"T00:00:00";
			Date settlementFromDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(FromDate);
			//System.out.println("settlementFromDate = "+settlementFromDate);

			String mdmToMonth = halfYearlyDoc.getString(MDM_SS_P_HALFYEARLY_TOMONTH);
			Date date2 = new SimpleDateFormat("MMMM").parse(mdmToMonth);
			cal.setTime(date2);
			int toMonth = cal.get(Calendar.MONTH)+1;
			int maxMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
			cal.setTime(paymentDate);
			if(halfYearlyDoc.containsKey(MDM_SS_P_HALFYEARLY_TOYEAR))
				toYear = halfYearlyDoc.getInteger(MDM_SS_P_HALFYEARLY_TOYEAR);
			else if(fromMonth > toMonth)
				toYear = cal.get(Calendar.YEAR)+1;
			else if(fromMonth < toMonth)
				toYear = cal.get(Calendar.YEAR);
			String ToDate = maxMonth +"-"+toMonth+"-"+toYear+"T00:00:00";
			Date settlementToDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(ToDate);
			//System.out.println("settlementToDate = "+settlementToDate);

			if((settlementFromDate.before(paymentDate) || settlementFromDate.equals(paymentDate)) 
					&& (settlementToDate.after(paymentDate) || settlementToDate.equals(paymentDate))) {

				int settlementDay = halfYearlyDoc.getInteger(MDM_SS_P_HALFYEARLY_SETTLEDUEDATE);
				String settlementMonth = halfYearlyDoc.getString(MDM_SS_P_HALFYEARLY_SETTLEDUEMONTH);
				Date date3 = new SimpleDateFormat("MMMM").parse(settlementMonth);
				cal.setTime(date3);
				int settlementDueMonth = cal.get(Calendar.MONTH)+1;
				cal.setTime(paymentDate);
				if(halfYearlyDoc.containsKey(MDM_SS_P_HALFYEARLY_SETTLEDUEYEAR) && halfYearlyDoc.get(MDM_SS_P_HALFYEARLY_SETTLEDUEYEAR) != null) {
					settlementDueYear = halfYearlyDoc.getInteger(MDM_SS_P_HALFYEARLY_SETTLEDUEYEAR);
				}
				else
					settlementDueYear = cal.get(Calendar.YEAR);

				String settlementDate = settlementDay+"-"+settlementDueMonth+"-"+settlementDueYear+"T00:00:00";
				//System.out.println("settlementDate of next = "+settlementDate);
				Date settlementDueDate = new SimpleDateFormat("dd-MM-yyyy'T'HH:mm:ss").parse(settlementDate);
				if(paymentDate.compareTo(settlementDueDate)>0) {
					if(settlementDueDate.compareTo(new Date())>0 && settlementDueDate.compareTo(travelDate)<0) 
						return settlementDueDate;
					else 
						return paymentDate;
				}
				else {
					if(paymentDate.compareTo(new Date())>0 && paymentDate.compareTo(travelDate)<0) 
						return paymentDate;
					else 
						return new Date();
				}
			}
		}
		return new Date();
	}


}
