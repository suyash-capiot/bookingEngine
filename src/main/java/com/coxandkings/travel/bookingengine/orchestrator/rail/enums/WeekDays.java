package com.coxandkings.travel.bookingengine.orchestrator.rail.enums;

public enum WeekDays {
	

	MON("Mon"), TUE("Tue"), WED("Weds"), THU("Thur"), FRI("Fri"), SAT("Sat"), SUN("Sun");
	
	private String day;
	private WeekDays(String subCateg) {
		this.day = subCateg;
	}
	
	public String toString() {
		return day;
	}
	
	public static WeekDays forString(String dayStr) { 
		WeekDays[] days = WeekDays.values();
		for (WeekDays day : days) {
			if (day.toString().equals(dayStr)) {
				return day;
			}
		}
		
		return null;
	}

}
