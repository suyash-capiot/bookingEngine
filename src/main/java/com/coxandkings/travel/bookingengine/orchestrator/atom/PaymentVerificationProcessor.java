package com.coxandkings.travel.bookingengine.orchestrator.atom;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.PaymentConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.enums.PaymentMethods;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class PaymentVerificationProcessor implements Constants {

	private static final Logger logger = LogManager.getLogger(HTTPServiceConsumer.class);

	public static boolean paymentVerify(JSONArray paymentInfoJsonArray, String clientID, String bookID) {

		HashMap<Integer, Boolean> verifyMap = new HashMap<Integer, Boolean>();

		for (int i = 0; i < paymentInfoJsonArray.length(); i++) {
			
			JSONObject paymentInfoJson = paymentInfoJsonArray.getJSONObject(i);
			String paymentMethod = paymentInfoJson.getString("paymentMethod");
			PaymentMethods paymentMethodEnum = PaymentMethods.fromString(paymentMethod);
			
			switch (paymentMethodEnum) {
			case CREDITCARD:
				verifyMap.put(i, atomPaymentVerify(paymentInfoJson));
				break;
			case DEBITCARD:
				verifyMap.put(i, atomPaymentVerify(paymentInfoJson));
				break;
			case DEPOSIT:
				verifyMap.put(i, depositPaymentVerify(paymentInfoJson,clientID,  bookID));
				break;
			case CREDIT:
				verifyMap.put(i, creditPaymentVerify(paymentInfoJson,clientID));
				
				break;
			//TODO: for  other payment methods we would need to add payment verification logic
			default:
				verifyMap.put(i, false);
				logger.warn("no mapping forund for the payment method", paymentMethodEnum);
				break;
			}
		}
		
		for (Map.Entry<Integer, Boolean> entry : verifyMap.entrySet()) {
		    if(entry.getValue()==false)
			return false;
		}

		return true;

	}

	public static boolean atomPaymentVerify(JSONObject paymentInfoJson) {
		try {
			String merchantId = paymentInfoJson.getString("merchantId");
			String merchantTxnId = paymentInfoJson.getString("merchantTxnId");
			String amount = paymentInfoJson.getString("amountPaid");
			String txnDate = paymentInfoJson.getString("transactionDate");

			//String url = PaymentConfig.getServiceURL("atomPayment");
			//URL atomURL = new URL(String.format(url, merchantId, merchantTxnId, amount, txnDate));

			//Map<String, String> httpHeaders = new HashMap<String, String>();
			//httpHeaders.put("Content-Type", "application/xml");

			//Element atomRes = HTTPServiceConsumer.ConsumeAtomService("Atom/Payment Verify", atomURL, httpHeaders);
			ServiceConfig svcCfg = PaymentConfig.getServiceConfig("atomPayment");
			Element atomRes = HTTPServiceConsumer.consumeXMLService("Atom/Payment Verify", svcCfg.getServiceURL(merchantId, merchantTxnId, amount, txnDate), svcCfg.getHttpProxy(), svcCfg.getHttpHeaders(), svcCfg.getHttpMethod(), svcCfg.getServiceTimeoutMillis(), null);
			String status = XMLUtils.getValueAtXPath(atomRes, "/VerifyOutput/@VERIFIED");
			if ("SUCCESS".equals(status))
				return true;

		} catch (MalformedURLException e) {
			logger.warn("URL for atom payment verification service is malformed", e);

		} catch (Exception ex) {
			logger.warn("There is an error occurred while calling atom payment verification service", ex);
		}

		return false;

	}

	public static boolean depositPaymentVerify(JSONObject paymentInfoJson, String clientID, String bookID) {
		
		
		String amount = paymentInfoJson.getString("amountPaid");
		String currency = paymentInfoJson.getString("amountCurrency");
		
		
		//String url = PaymentConfig.getServiceURL("financeDeposit");
		

		try {
			ServiceConfig svcCfg = PaymentConfig.getServiceConfig("financeDeposit");
			
			//URL finDepositURL = new URL(String.format(url, clientID, currency, amount, bookID));
			//JSONObject resJson = HTTPServiceConsumer.consumeJSONService("Fiannce/Deposit Verify",
			//		finDepositURL, PaymentConfig.getmHttpHeaders(),"GET", null);
			JSONObject resJson = HTTPServiceConsumer.consumeJSONService("Finance/Deposit Verify", svcCfg.getServiceURL(clientID, currency, amount, bookID), svcCfg.getHttpHeaders(), svcCfg.getHttpMethod(), null);
			boolean res = resJson.getBoolean("status");
			return res;
		}

		catch (MalformedURLException e) {
			logger.warn("URL for deposit payment verification service is malformed", e);
		}
		catch(Exception e) {
			logger.warn("There is an error occurred while calling deposit payment verification service", e);
		}

		return false;
	}
	

	private static Boolean creditPaymentVerify(JSONObject paymentInfoJson, String clientID) {

		JSONObject reqJson = new JSONObject();
		
		reqJson.put("clientId", clientID);
		reqJson.put("bookingDate", paymentInfoJson.getString("transactionDate"));
		reqJson.put("creditAmount", paymentInfoJson.getString("amountPaid"));
		reqJson.put("creditCurrency", paymentInfoJson.getString("amountCurrency"));
		reqJson.put("productCategory", paymentInfoJson.getString("productCategory"));		
		reqJson.put("productCategorySubType", paymentInfoJson.getString("productCategorySubType"));
		
		reqJson.put("productName", paymentInfoJson.optString("productName"));
		reqJson.put("branch", paymentInfoJson.optString("branch"));
		reqJson.put("productSubName", paymentInfoJson.optString("productSubName"));
		reqJson.put("employee", paymentInfoJson.optString("employee"));
		
		ServiceConfig svcCfg = PaymentConfig.getServiceConfig("financeCredit");
		
		JSONObject resJson;
		try {
			resJson = HTTPServiceConsumer.consumeJSONService("Finance/Credit Verify", svcCfg.getServiceURL(), svcCfg.getHttpHeaders(), svcCfg.getHttpMethod(),reqJson );
			boolean res = resJson.getBoolean("status");
			return res;
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			logger.warn("There is an error occurred while calling credit payment verification service", e);
		}
	
      return false;
		
	}

}
