package com.coxandkings.travel.bookingengine.exception;

import java.text.MessageFormat;

import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.utils.BEResources;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;

@SuppressWarnings("serial")
public abstract class ProcessingException extends Exception implements Constants{
	
	ProcessingException(Throwable cause) {
		super(cause);
	}
	
	public JSONObject toJSON(String errCode) {
		JSONObject errJson = new JSONObject();
		String operation = TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION);
		errJson.put(JSON_PROP_ERRCODE, errCode);
		//errJson.put(JSON_PROP_ERRMSG, MessageFormat.format(BEResources.getMessage(errCode),getCause().getMessage(),String.format("%c%s", Character.toUpperCase(operation.charAt(0)),operation.substring(1))));
		errJson.put(JSON_PROP_ERRMSG, BEResources.getMessage(errCode));
		return errJson;
	}
	
	public String toString(String errCode) {
		return toJSON(errCode).toString();
	}
}
