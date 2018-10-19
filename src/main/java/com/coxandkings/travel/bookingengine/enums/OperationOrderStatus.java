package com.coxandkings.travel.bookingengine.enums;

public enum OperationOrderStatus {

	OK("Confirmed"),  XL("Cancelled");
		
		private String mOrderStatus;
		private OperationOrderStatus(String orderStatus) {
			mOrderStatus = orderStatus;
		}
		
		public String toString() {
			return mOrderStatus;
		}
		
	

}
