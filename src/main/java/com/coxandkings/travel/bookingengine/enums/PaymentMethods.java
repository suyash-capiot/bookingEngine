package com.coxandkings.travel.bookingengine.enums;

public enum PaymentMethods {

	BANKTRANSFER("Bank Transfer"),

	NEFT("NEFT"),

	WIRETRANSFER("Wire Transfer"),

	CREDITCARD("Credit Card"),

	DEBITCARD("Debit Card"),

	CHEQUE("Cheque"),

	CASHONDELIVERY("Cash On Delivery"),

	DEPOSIT("Deposit"),

	IVRPAYMENT("IVR Payment"),
	
	CASH("Cash"),
	
	CREDIT("Credit"),
	
	GIFTVOUCHER("Gift Voucher"),
	
	CREDITNOTE("Credit Note");

	
	private String paymentName;
	
	
	private PaymentMethods(String taskName) {
		this.paymentName = taskName;
	}

	public String getPaymentName() {
		return paymentName;
	}



	public static PaymentMethods fromString(String paymentMethod) {
		
		PaymentMethods aPaymentName = null;
        if( paymentMethod == null || paymentMethod.isEmpty() )  {
            return aPaymentName;
        }

        for( PaymentMethods tmpName : PaymentMethods.values() )    {
            if( tmpName.getPaymentName().equalsIgnoreCase(paymentMethod))  {
            	aPaymentName = tmpName;
                break;
            }
        }
        return aPaymentName;
	}
	

}
