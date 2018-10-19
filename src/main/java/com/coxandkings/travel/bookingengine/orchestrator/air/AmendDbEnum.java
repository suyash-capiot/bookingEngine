package com.coxandkings.travel.bookingengine.orchestrator.air;

public enum AmendDbEnum {
	
	REM("ADDREM"),
	SSR("ADDSSR"),
	PIS("UPDATEPIS");
	
	
	private String amendDbEnum;
	
	AmendDbEnum(String newamendDbEnum){
		amendDbEnum=newamendDbEnum;
    }
	
	  public String getcancelDbEnum()    {
	        return amendDbEnum;
	    }

}
