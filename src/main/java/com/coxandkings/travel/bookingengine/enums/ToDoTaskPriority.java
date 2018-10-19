package com.coxandkings.travel.bookingengine.enums;

public enum ToDoTaskPriority {
	
	HIGH("High", 100),

	MEDIUM("Medium", 50),

	LOW("Low", 0);

	private String name;
	private int ordinal;

	private ToDoTaskPriority(String name, int ordinal) {
		this.name = name;
		this.ordinal = ordinal;
	}

}
