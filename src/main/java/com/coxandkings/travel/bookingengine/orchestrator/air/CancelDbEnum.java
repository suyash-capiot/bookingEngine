package com.coxandkings.travel.bookingengine.orchestrator.air;

public enum CancelDbEnum {
	JOU("CANCELJOU"),
	SSR("CANCELSSR"),
	PAX("CANCELPASSENGER"),
	ALL("FULLCANCELLATION"),
	PRE("PRECANCELLATION");
	
	private String cancelDbEnum;
	
	CancelDbEnum(String newcancelDbEnum){
		cancelDbEnum=newcancelDbEnum;
    }
	
	  public String getcancelDbEnum()    {
	        return cancelDbEnum;
	    }
}
