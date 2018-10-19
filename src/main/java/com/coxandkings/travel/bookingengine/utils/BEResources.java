package com.coxandkings.travel.bookingengine.utils;

import java.util.ResourceBundle;

public class BEResources {

	private static ResourceBundle resBdl = ResourceBundle.getBundle(BEResources.class.getSimpleName());
	
	public static String getMessage(String msgKey) {
		try {
			return resBdl.getString(msgKey);
		}
		catch (Exception x) {
			// TODO: Log a resource not found message here.
			return "";
		}
	}
}
