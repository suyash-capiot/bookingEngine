package com.coxandkings.travel.bookingengine.orchestrator.login;

import com.coxandkings.travel.bookingengine.utils.Constants;

public interface LoginConstants extends Constants {
	
	public static final String VERSION = "__v";
	public static final String USER = "user";
	public static final String TOKEN = "token";
	public static final String LAST_UPDATED = "lastUpdated";
	public static final String LOGIN_TIME = "loginTime";
	public static final String EXPIRE_IN = "expireIn";
	public static final String STATUS = "status";
	public static final String ID = "_id";
	public static final String USER_CONTEXT = "_userContext";
	public static final char KEYSEPARATOR = '|';
}
