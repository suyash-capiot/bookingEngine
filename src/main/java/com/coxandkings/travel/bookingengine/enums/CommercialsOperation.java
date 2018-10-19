package com.coxandkings.travel.bookingengine.enums;

public enum CommercialsOperation {
	Ammend, Ammmend, Booking,Book, Cancel, Payment, Reprice, Search;
	
	public static CommercialsOperation forString(String commOpStr) {
		for(CommercialsOperation commOp: CommercialsOperation.values()) {
			if(commOp.toString().equals(commOpStr))
				return commOp;
		}
		return null;
	}
}
