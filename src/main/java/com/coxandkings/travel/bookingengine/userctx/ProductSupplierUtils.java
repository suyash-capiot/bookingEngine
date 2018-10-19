package com.coxandkings.travel.bookingengine.userctx;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.utils.Constants;

public class ProductSupplierUtils implements Constants{
	
	private static final Logger logger = LogManager.getLogger(ProductSupplierUtils.class);
	
	@Deprecated
	public static List<ProductSupplier> getSupplierCredentialsForGDSQueueOperations_JSONImpl(String suppID) {
		List<ProductSupplier> prodSuppsList = new ArrayList<ProductSupplier>();
		
		JSONArray suppCredsListJsonArr = MDMUtils.getSupplierCredentialsForGDSQueueOperationsv2(suppID);
		for (int i =0;i<suppCredsListJsonArr.length();i++) {
			JSONObject suppCredsJson = suppCredsListJsonArr.getJSONObject(i);
			try { 
				prodSuppsList.add(new ProductSupplier(suppCredsJson));
			}
			catch (Exception x) {
				logger.error(String.format("An exception occurred during initialization of supplier %s credential %s", suppID, suppCredsJson.getString(JSON_PROP_CREDSNAME)), x); 
			}
		}
		return prodSuppsList;
	}
	
	public static List<ProductSupplier> getSupplierCredentialsForGDSQueueOperations(String suppID) {
		List<ProductSupplier> prodSuppsList = new ArrayList<ProductSupplier>();
		
		List<Document> suppCredsList = MDMUtils.getSupplierCredentialsForGDSQueueOperations(suppID);
		for (Document suppCredsDoc : suppCredsList) {
			try { 
				prodSuppsList.add(new ProductSupplier(suppCredsDoc));
			}
			catch (Exception x) {
				logger.error(String.format("An exception occurred during initialization of supplier %s credential %s", suppID, suppCredsDoc.getString("_id")), x); 
			}
		}
		return prodSuppsList;
	}

	@Deprecated
	public static List<ProductSupplier> getSupplierCredentialsForGDSQueueOperations_JSONImpl(String suppID, String pcc) {
		List<ProductSupplier> prodSuppsList = new ArrayList<ProductSupplier>();
		
		JSONArray suppCredsListJsonArr = MDMUtils.getSupplierCredentialsForPseudoCityCodev2(suppID, pcc);
		for (int i =0;i<suppCredsListJsonArr.length();i++) {
			JSONObject suppCredsJson = suppCredsListJsonArr.getJSONObject(i);
			try { 
				prodSuppsList.add(new ProductSupplier(suppCredsJson));
			}
			catch (Exception x) {
				logger.error(String.format("An exception occurred during initialization of supplier %s credential %s", suppID, suppCredsJson.getString(JSON_PROP_CREDSNAME)), x); 
			}
		}
		return prodSuppsList;
	}
	
	public static List<ProductSupplier> getSupplierCredentialsForGDSQueueOperations(String suppID, String pcc) {
		List<ProductSupplier> prodSuppsList = new ArrayList<ProductSupplier>();
		
		List<Document> suppCredsList = MDMUtils.getSupplierCredentialsForPseudoCityCode(suppID, pcc);
		for (Document suppCredsDoc : suppCredsList) {
			try { 
				prodSuppsList.add(new ProductSupplier(suppCredsDoc));
			}
			catch (Exception x) {
				logger.error(String.format("An exception occurred during initialization of supplier %s credential %s for PCC %s", suppID, suppCredsDoc.getString("_id"), pcc), x); 
			}
		}
		return prodSuppsList;
	}
	
	@Deprecated
	public static ProductSupplier getSupplierCredentialsForCredentialsName_JSONImpl(String suppID, String credsName) {
		JSONObject suppCredsJson = MDMUtils.getSupplierCredentialsConfigv2(suppID, credsName);
		if (suppCredsJson == null) {
			return null;
		}
		
		try {
			return (new ProductSupplier(suppCredsJson));
		}
		catch (Exception x) {
			logger.error(String.format("An exception occurred during initialization of supplier %s credential %s", suppID, credsName), x); 
		}
		
		return null;
	}

	public static ProductSupplier getSupplierCredentialsForCredentialsName(String suppID, String credsName) {
		Document suppCredsDoc = MDMUtils.getSupplierCredentialsConfig(suppID, credsName);
		if (suppCredsDoc == null) {
			return null;
		}
		
		try {
			return (new ProductSupplier(suppCredsDoc));
		}
		catch (Exception x) {
			logger.error(String.format("An exception occurred during initialization of supplier %s credential %s", suppID, credsName), x); 
		}
		
		return null;
	}
}
