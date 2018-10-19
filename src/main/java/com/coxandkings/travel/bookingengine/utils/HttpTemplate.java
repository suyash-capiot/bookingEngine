package com.coxandkings.travel.bookingengine.utils;

import java.util.List;
import java.util.Map;

public class HttpTemplate {

	//private Object mPayload;
	private String mPayloadStr;
	private String mErrStr;
	private int mStatusCode;
	private Map<String,List<String>> mHtttpHeaders;

	public int getStatusCode() {
		return mStatusCode;
	}

	public void setStatusCode(int statusCode) {
		this.mStatusCode = statusCode;
	}

	public String getPayloadString() {
		return mPayloadStr;
	}

	public void setPayload(String payloadStr) {
		this.mPayloadStr = payloadStr;
	}

	public String getErrorString() {
		return mErrStr;
	}

	public void setError(String errStr) {
		this.mErrStr = errStr;
	}

	public Map<String, List<String>> getHtttpHeaders() {
		return mHtttpHeaders;
	}

	public void setHtttpHeaders(Map<String, List<String>> map) {
		this.mHtttpHeaders = map;
	}
}
