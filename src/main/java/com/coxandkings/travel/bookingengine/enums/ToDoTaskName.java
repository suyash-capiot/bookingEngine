package com.coxandkings.travel.bookingengine.enums;

public enum ToDoTaskName {
	
	CONFIRM("Confirm"),

	RECONFIRM("Reconfirm"),

	CANCEL("Cancel"),

	BOOK("Book"),

	APPROVE("Approve"),

	UPDATE("Update"),

	CORRECTION("Correction"),

	SETTLEMENT("Settlement"),

	AMEND("Amend"),
	
	SEARCH("Search");

	
	private String taskName;
	
	
	private ToDoTaskName(String taskName) {
		this.taskName = taskName;
	}

}
