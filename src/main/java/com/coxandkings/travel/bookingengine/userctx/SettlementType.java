package com.coxandkings.travel.bookingengine.userctx;

public enum SettlementType {
	creditSecuredBankGurantee(SupplierSettlementTerms.MDM_VAL_CREDIT, SupplierSettlementTerms.MDM_VAL_SECURED, SupplierSettlementTerms.MDM_VAL_BANKGUARANTEE), 
	creditSecuredCreditCard(SupplierSettlementTerms.MDM_VAL_CREDIT, SupplierSettlementTerms.MDM_VAL_SECURED, SupplierSettlementTerms.MDM_VAL_CREDITCARD), 
	creditSecuredSecurityDeposit(SupplierSettlementTerms.MDM_VAL_CREDIT, SupplierSettlementTerms.MDM_VAL_SECURED, SupplierSettlementTerms.MDM_VAL_SECURITYDEPOSIT), 
	creditUnsecured(SupplierSettlementTerms.MDM_VAL_CREDIT, SupplierSettlementTerms.MDM_VAL_UNSECURED, null), 
	noCreditDeposit(SupplierSettlementTerms.MDM_VAL_NOCREDIT, SupplierSettlementTerms.MDM_VAL_DEPOSIT, null), 
	noCreditPrePayment(SupplierSettlementTerms.MDM_VAL_NOCREDIT, SupplierSettlementTerms.MDM_VAL_PREPAYMENT, null), 
	unknown(null, null, null);
	
	private String mTypeOfSettlement, mCreditType, mModeOfSecurity;
	private SettlementType(String typeOfSett, String creditType, String modeOfSecurity) {
		mTypeOfSettlement = typeOfSett;
		mCreditType = creditType;
		mModeOfSecurity = modeOfSecurity;
	}
	
	public static SettlementType forStrings(String typeOfSett, String creditType, String modeOfSecurity) {
		typeOfSett = (typeOfSett == null || typeOfSett.trim().isEmpty()) ? null : typeOfSett;
		creditType = (creditType == null || creditType.trim().isEmpty()) ? null : creditType;
		modeOfSecurity = (modeOfSecurity == null || modeOfSecurity.trim().isEmpty()) ? null : modeOfSecurity;
		
		SettlementType[] settTypes = SettlementType.values();
		for (SettlementType settType : settTypes) {
			if (
					((settType.mTypeOfSettlement == null && typeOfSett == null) || (settType.mTypeOfSettlement != null && settType.mTypeOfSettlement.equals(typeOfSett)) || (typeOfSett != null && typeOfSett.equals(settType.mTypeOfSettlement)))
					&& ((settType.mCreditType == null && creditType == null) || (settType.mCreditType != null && settType.mCreditType.equals(creditType)) || (creditType != null && creditType.equals(settType.mCreditType)))
					&& ((settType.mModeOfSecurity == null && modeOfSecurity == null) || (settType.mModeOfSecurity != null && settType.mModeOfSecurity.equals(modeOfSecurity)) || (modeOfSecurity != null && modeOfSecurity.equals(settType.mModeOfSecurity)))
				) {
				return settType;
			}
		}
		
		return unknown;
	}
}