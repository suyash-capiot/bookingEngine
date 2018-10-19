package com.coxandkings.travel.bookingengine.exception;

import java.text.MessageFormat;

import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.utils.BEResources;
import com.coxandkings.travel.bookingengine.utils.Constants;

@SuppressWarnings("serial")
public class ValidationException extends Exception implements Constants{
	private String mErrCode=null,mErrMsg;
	private JSONObject resHeader=null;
	
	public ValidationException(String errCode, String errMsg) {
		mErrCode = errCode;
		mErrMsg = errMsg;
	}

	public ValidationException(String errCode, Object...errParams) {
		mErrCode = errCode;
		mErrMsg = MessageFormat.format(BEResources.getMessage(errCode), errParams);
	}
	// constructors added below according to new format-
	public ValidationException(JSONObject reqJsonHeader,String errCode, Object...errParams) {
		mErrCode = errCode;
		resHeader=reqJsonHeader;
		mErrMsg = MessageFormat.format(BEResources.getMessage(errCode), errParams);
	}
	
	public JSONObject toJSON() {
		JSONObject valErrJson = new JSONObject();
		valErrJson.put(JSON_PROP_ERRSTATUS, "ERROR");
		valErrJson.put(JSON_PROP_ERRCODE, mErrCode);
		valErrJson.put(JSON_PROP_ERRMSG, mErrMsg);
		valErrJson.put(JSON_PROP_RESHEADER, resHeader);
		return valErrJson;
	}
	public String toString() {
		return toJSON().toString();
	}

}
