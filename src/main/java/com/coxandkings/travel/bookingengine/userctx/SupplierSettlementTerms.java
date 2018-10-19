package com.coxandkings.travel.bookingengine.userctx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.Utils;

public class SupplierSettlementTerms implements Constants {
	private Logger logger = LogManager.getLogger(SupplierSettlementTerms.class);
	
	private SettlementType mSettType;
	private Document mConfigDoc;
	private JSONObject mConfigJson;
	private ProductSupplier mProdSupp;
	private String mSIPayDetailTemplate;
	
	private static Map<String, String> mSuppSettToSIPayMap = new HashMap<String, String>();
	private static final String EMPTY_PAY_DETAIL = "<ota:PaymentDetail/>";
	
	@SuppressWarnings("unchecked")
	SupplierSettlementTerms(ProductSupplier prodSupp, String prodCateg, String prodCategSubType) {
		mSettType = SettlementType.unknown;
		mConfigDoc = null;
		mProdSupp = prodSupp;
		
		String suppID = mProdSupp.getSupplierID();
		String credsName = mProdSupp.getCredentialsName();
		
		//JSONObject suppCommJson = MDMUtils.getSupplierCommercialsv2(prodCateg, prodCategSubType, suppID);
		//if (suppCommJson == null) {
		org.bson.Document suppCommDoc = MDMUtils.getSupplierCommercials(prodCateg, prodCategSubType, suppID);
		if (suppCommDoc == null) {
			logger.trace(String.format("Supplier commercials definition for productCategory=%s, productCategorySubType=%s and SupplierId=%s not found", prodCateg, prodCategSubType, suppID));
			return;
		}
		
		//boolean isCommissionable = Boolean.valueOf(MDMUtils.getValueAtPathFromDocument(suppCommDoc, MDM_PROP_STANDARDCOMM.concat(".").concat(MDM_PROP_ISCOMMISSIONABLE)));
		/*JSONObject suppCommData = suppCommJson.has(MDM_PROP_SUPPCOMMDATA)?suppCommJson.getJSONObject(MDM_PROP_SUPPCOMMDATA):new JSONObject();
		//JSONObject stdCommercial = suppCommData.has(MDM_PROP_STANDARDCOMM)?suppCommData.getJSONObject(MDM_PROP_STANDARDCOMM):new JSONObject();
		JSONObject stdCommercial = suppCommData.has(MDM_PROP_STANDARDCOMM)?suppCommData.getJSONArray(MDM_PROP_STANDARDCOMM).getJSONObject(0):new JSONObject();
		boolean isCommissionable = (boolean) stdCommercial.opt(MDM_PROP_ISCOMMISSIONABLE);
		JSONObject suppSettleJson = MDMUtils.getSupplierSettlementTermsv2(prodCateg, prodCategSubType, suppID);
		if (suppSettleJson == null) {
			logger.trace(String.format("Supplier settlement definition for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
			return;
		}*/
		ArrayList<org.bson.Document> stdCommercial=(ArrayList<Document>) MDMUtils.getValueObjectAtPathFromDocument(suppCommDoc, MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_STANDARDCOMM));
		if(stdCommercial==null || stdCommercial.size()==0) {
			logger.trace(String.format("Supplier settlement definition for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
			return;
		}
		
		//boolean isCommissionable = (Boolean) MDMUtils.getValueObjectAtPathFromDocument(suppCommDoc, MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_STANDARDCOMM).concat(".").concat(MDM_PROP_ISCOMMISSIONABLE));
		boolean isCommissionable =stdCommercial.get(0).getBoolean(MDM_PROP_ISCOMMISSIONABLE);
		
		org.bson.Document suppSettleDoc = MDMUtils.getSupplierSettlementTerms(prodCateg, prodCategSubType, suppID);
		if (suppSettleDoc == null) {
			logger.trace(String.format("Supplier settlement definition for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
			return;
		}
		
		//JSONObject stdCommSettlementJson = MDMUtils.getStandardCommercialSettlementTermsv2(suppSettleJson, isCommissionable); 
		//if (stdCommSettlementJson == null ) {
		org.bson.Document stdCommSettlementDoc = MDMUtils.getStandardCommercialSettlementTerms(suppSettleDoc, isCommissionable); 
		if (stdCommSettlementDoc == null) {
			logger.trace(String.format("Standard commercial settlement terms for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
			return;
		}
		
		//String typeOfSettlement = stdCommSettlementJson.optString(MDM_PROP_TYPEOFSETTLE);
		String typeOfSettlement = stdCommSettlementDoc.getString(MDM_PROP_TYPEOFSETTLE);
		if (typeOfSettlement == null) {
			logger.trace(String.format("Type of standard commercial settlement for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
			return;
		}
		if (MDM_VAL_CREDIT.equals(typeOfSettlement) == false && MDM_VAL_NOCREDIT.equals(typeOfSettlement) == false) {
			logger.trace(String.format("Type %s of standard commercial settlement is not %s or %s for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s", typeOfSettlement, MDM_VAL_CREDIT, MDM_VAL_NOCREDIT, prodCateg, prodCategSubType, suppID, credsName));
			return;
		}
		
		/*if (MDM_VAL_CREDIT.equals(typeOfSettlement)) {
			
			JSONArray credSettJsonArr = stdCommSettlementJson.optJSONArray(MDM_PROP_CREDITSETTLEMENT);
			if (credSettJsonArr == null || credSettJsonArr.length() == 0) {
				logger.trace(String.format("Configuration for credit settlement of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
				return;
			}
			if (credSettJsonArr.length() > 1) {
				logger.trace(String.format("Multiple configurations for credit settlement of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s found. Only first configuration will be considered.", prodCateg, prodCategSubType, suppID, credsName));
			}
			JSONObject defCredJson = credSettJsonArr.optJSONObject(0).optJSONObject(MDM_PROP_DEFCREDITTYPE);
			if (defCredJson == null) {
				logger.trace(String.format("Credit type definition of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
				return;
			}
			
			String creditType = defCredJson.optString(MDM_PROP_CREDITTYPE);
			String modeOfSecurity = defCredJson.optString(MDM_PROP_MODEOFSECURITY);
			mSettType = SettlementType.forStrings(typeOfSettlement, creditType, modeOfSecurity);
			mConfigJson = defCredJson;
			if (isCredentialsNameConfigured(mConfigDoc, credsName) == false) {
				mConfigJson = null;
				logger.trace(String.format("Credential %s is not configured in credit type definition of Standard commercial for productCategory=%s, productCategorySubType=%s and SupplierId=%s", credsName, prodCateg, prodCategSubType, suppID));
				return;
			}*/
			if (MDM_VAL_CREDIT.equals(typeOfSettlement)) {
			List<org.bson.Document> credSettDocs = (List<org.bson.Document>) stdCommSettlementDoc.get(MDM_PROP_CREDITSETTLEMENT);
			if (credSettDocs == null || credSettDocs.size() == 0) {
				logger.trace(String.format("Configuration for credit settlement of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
				return;
			}
			
			if (credSettDocs.size() > 1) {
				logger.trace(String.format("Multiple configurations for credit settlement of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s found. Only first configuration will be considered.", prodCateg, prodCategSubType, suppID, credsName));
			}

			org.bson.Document defCredDoc = (org.bson.Document) credSettDocs.get(0).get(MDM_PROP_DEFCREDITTYPE);
			if (defCredDoc == null) {
				logger.trace(String.format("Credit type definition of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
				return;
			}
			
			String creditType = defCredDoc.getString(MDM_PROP_CREDITTYPE);
			String modeOfSecurity = defCredDoc.getString(MDM_PROP_MODEOFSECURITY);
			mSettType = SettlementType.forStrings(typeOfSettlement, creditType, modeOfSecurity);
			mConfigDoc = defCredDoc;
			/*if (isCredentialsNameConfigured(mConfigDoc, credsName) == false) {
				mConfigDoc = null;
				logger.trace(String.format("Credential %s is not configured in credit type definition of Standard commercial for productCategory=%s, productCategorySubType=%s and SupplierId=%s", credsName, prodCateg, prodCategSubType, suppID));
				return;
			}*/
		}
		/*else {
			JSONArray noCredSettJsonArr = stdCommSettlementJson.getJSONArray(MDM_PROP_NOCREDITSETTLEMENT);
			if (noCredSettJsonArr == null || noCredSettJsonArr.length() == 0) {
				logger.trace(String.format("Configuration for no-credit settlement of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
				return;
			}
			
			if (noCredSettJsonArr.length() > 1) {
				logger.trace(String.format("Multiple configurations for no-credit settlement of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s found. Only first configuration will be considered.", prodCateg, prodCategSubType, suppID, credsName));
			}

			JSONObject defNoCredJson =  noCredSettJsonArr.getJSONObject(0);
			if (defNoCredJson == null) {
				logger.trace(String.format("No-credit type definition of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
				return;
			}
			
			String creditType = defNoCredJson.getString(MDM_PROP_CREDITTYPE);
			if (MDM_VAL_DEPOSIT.equals(creditType) == false && MDM_VAL_PREPAYMENT.equals(creditType) == false) {
				logger.trace(String.format("Credit type %s in no-credit type definition of Standard commercial is not %s or %s for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s", creditType, MDM_VAL_DEPOSIT, MDM_VAL_PREPAYMENT, prodCateg, prodCategSubType, suppID, credsName));
				return;
			}
			
			mSettType = SettlementType.forStrings(typeOfSettlement, creditType, null);
			String configDocName = (MDM_VAL_DEPOSIT.equals(creditType)) ? MDM_PROP_DEPOSITSETTLEMENT : MDM_PROP_PREPAYSETTLEMENT;
			mConfigJson =  defNoCredJson.getJSONObject(configDocName);
			if (isCredentialsNameConfiguredv2(mConfigJson, credsName) == false) {
				mConfigDoc = null;
				logger.trace(String.format("Credential %s is not configured in no-credit type definition %s of Standard commercial for productCategory=%s, productCategorySubType=%s and SupplierId=%s", credsName, configDocName, prodCateg, prodCategSubType, suppID));
				return;
			}
		}*/
			else {
				List<Document> noCredSettDocs = (List<Document>) stdCommSettlementDoc.get(MDM_PROP_NOCREDITSETTLEMENT);
				if (noCredSettDocs == null || noCredSettDocs.size() == 0) {
					logger.trace(String.format("Configuration for no-credit settlement of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
					return;
				}
				
				if (noCredSettDocs.size() > 1) {
					logger.trace(String.format("Multiple configurations for no-credit settlement of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s found. Only first configuration will be considered.", prodCateg, prodCategSubType, suppID, credsName));
				}

				org.bson.Document defNoCredDoc = (org.bson.Document) noCredSettDocs.get(0);
				if (defNoCredDoc == null) {
					logger.trace(String.format("No-credit type definition of Standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s not found", prodCateg, prodCategSubType, suppID, credsName));
					return;
				}
				
				String creditType = defNoCredDoc.getString(MDM_PROP_CREDITTYPE);
				if (MDM_VAL_DEPOSIT.equals(creditType) == false && MDM_VAL_PREPAYMENT.equals(creditType) == false) {
					logger.trace(String.format("Credit type %s in no-credit type definition of Standard commercial is not %s or %s for productCategory=%s, productCategorySubType=%s, SupplierId=%s and CredentialsName=%s", creditType, MDM_VAL_DEPOSIT, MDM_VAL_PREPAYMENT, prodCateg, prodCategSubType, suppID, credsName));
					return;
				}
				
				mSettType = SettlementType.forStrings(typeOfSettlement, creditType, null);
				String configDocName = (MDM_VAL_DEPOSIT.equals(creditType)) ? MDM_PROP_DEPOSITSETTLEMENT : MDM_PROP_PREPAYSETTLEMENT;
				mConfigDoc = (Document) defNoCredDoc.get(configDocName);
				/*if (isCredentialsNameConfigured(mConfigDoc, credsName) == false) {
					mConfigDoc = null;
					logger.trace(String.format("Credential %s is not configured in no-credit type definition %s of Standard commercial for productCategory=%s, productCategorySubType=%s and SupplierId=%s", credsName, configDocName, prodCateg, prodCategSubType, suppID));
					return;
				}*/
			}
		//TODO: from where we fetch this collection
		mSIPayDetailTemplate = getPaymentDetailTemplate();
	}
	
	//TODO:To fetch payment details through API
//	private String getPaymentDetailTemplate() {
//		String suppID = mProdSupp.getSupplierID();
//		String settType = mSettType.toString();
//		
//		String mapKey = String.format("%s|%s", suppID, settType);
////		if (mSuppSettToSIPayMap.containsKey(mapKey)) {
////			return mSuppSettToSIPayMap.get(mapKey);
////		}
//		
//		// TODD: Synchronize following block of code. Synchronize on what object? mProdSupp??
//		JSONObject suppSettToSIPayJson = MDMUtils.getSIPaymentDetailTemplatev2(suppID, settType);
//		if (suppSettToSIPayJson == null || suppSettToSIPayJson.length()==0) {
//			logger.error("SI payment detail template not found for settlement type {0} of supplier {1}.", settType, suppID);
//			return EMPTY_PAY_DETAIL;
//		}
//		
//		String siPayTemplateStr = suppSettToSIPayJson.optString(MDM_PROP_PYMTDTLTEMPLATE);
//		if (siPayTemplateStr == null || siPayTemplateStr.trim().isEmpty()) {
//			logger.warn("SI payment detail template for settlement type {0} of supplier {1} is null or empty.", settType, suppID);
//			siPayTemplateStr = EMPTY_PAY_DETAIL;
//		}
//		mSuppSettToSIPayMap.put(mapKey, siPayTemplateStr);
//		return siPayTemplateStr;
//	}
	
	
	private String getPaymentDetailTemplate() {
		String suppID = mProdSupp.getSupplierID();
		String settType = mSettType.toString();
		String credsName = mProdSupp.getCredentialsName();
		
		String mapKey = String.format("%s|%s", suppID, settType);
		if (mSuppSettToSIPayMap.containsKey(mapKey)) {
			return mSuppSettToSIPayMap.get(mapKey);
		}
		
		// TODD: Synchronize following block of code. Synchronize on what object? mProdSupp??
		Document suppSettToSIPayDoc = MDMUtils.getSIPaymentDetailTemplate(suppID, settType);
		if (suppSettToSIPayDoc == null) {
			logger.error("SI payment detail template not found for settlement type {0} of supplier {1}.", settType, suppID);
			return EMPTY_PAY_DETAIL;
		}
		
		String siPayTemplateStr = suppSettToSIPayDoc.getString(MDM_PROP_PYMTDTLTEMPLATE);
		if (siPayTemplateStr == null || siPayTemplateStr.trim().isEmpty()) {
			logger.warn("SI payment detail template for settlement type {0} of supplier {1} is null or empty.", settType, suppID);
			siPayTemplateStr = EMPTY_PAY_DETAIL;
		}
		mSuppSettToSIPayMap.put(mapKey, siPayTemplateStr);
		return siPayTemplateStr;
	}
	
	@SuppressWarnings("unchecked")
	private boolean isCredentialsNameConfigured(Document configDoc, String credsName) {
		if (configDoc == null || credsName == null || credsName.trim().isEmpty()) {
			return false;
		}
		
//		List<String> credNames = (List<String>) configDoc.get(MDM_PROP_CREDNAMES);
//		if (credNames == null) {
//			return false;
//		}
//		
//		for (String credName : credNames) {
//			if (credsName.equals(credName)) {
//				return true;
//			}
//		}
		
		JSONObject configDocJson= new JSONObject(configDoc.toJson());
		JSONArray credentialNamesJsonArr=configDocJson.getJSONArray("credentialNames");
		 if (credentialNamesJsonArr == null ) {
             return false;
		}
		for(int i=0;i<credentialNamesJsonArr.length();i++) 
		{
	        	JSONObject credentialNameJson=credentialNamesJsonArr.getJSONObject(i);
	        	if(credsName.equals(credentialNameJson.getString("id"))) {
	        		return true;
	        	}
	        	
	    }
		return false;
		
	}

	@Deprecated
	private boolean isCredentialsNameConfiguredv2(JSONObject configJson, String credsName) {
		if (configJson == null || credsName == null || credsName.trim().isEmpty()) {
			return false;
		}
		
		
		JSONArray credNames = configJson.getJSONArray(MDM_PROP_CREDNAMES);
		for(int i=0;i<credNames.length();i++)
		{
			JSONObject credName = credNames.getJSONObject(i);
			if (credsName.equals(credName.getString("id"))) {
				return true;
			}
			
		}
			
		
		
		return false;
		
	}
//	@SuppressWarnings("incomplete-switch")
//	public Element toPaymentDetailsElement(org.w3c.dom.Document ownerDoc) {
//		Element paymentDetail = ownerDoc.createElementNS(NS_OTA, "ota:PaymentDetail");
//		if (mConfigDoc != null) {
//			switch (mSettType) {
//				// TODO: Need to find out how each type of settlement type translates to PaymentDetail element in OTA
//				case creditUnsecured : {
//					paymentDetail.setAttribute(XML_ATTR_PAYTYPE, OTA_PAYTYPE_BUSSACCT);
//					
//					Credential agNameCred = mProdSupp.getCredentialForKey(CREDENTIAL_PROP_AGENTNAME);
//					if (agNameCred != null) {
//						paymentDetail.setAttribute(XML_ATTR_COSTCENTERID, agNameCred.getValue());
//					}
//					
//					Credential agCodeCred = mProdSupp.getCredentialForKey(CREDENTIAL_PROP_AGENTCODE);
//					if (agNameCred != null) {
//						paymentDetail.setAttribute(XML_ATTR_GUARANTEEID, agCodeCred.getValue());
//					}
//					
//					break;
//				}
//				case noCreditDeposit : {
//					break;
//				}
//			}
//		}
//		
//		return paymentDetail;
//	}
	

	public String getPaymentDetailsElementTemplate() {
		return mSIPayDetailTemplate;
	}

}
