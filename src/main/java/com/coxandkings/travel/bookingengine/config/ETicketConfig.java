package com.coxandkings.travel.bookingengine.config;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.config.mongo.MongoProductConfig;
import com.coxandkings.travel.bookingengine.utils.Constants;

public class ETicketConfig implements Constants {

	private static String mDBServiceURL;
	private static String mProcessDefUrl;
	private static String mDtmsUser;
	private static String mDtmsPass;
	private static String mUserId;
	private static String mLookup;
	private static String mTemplateUrl;
	private static String mModeshapeUrl;
	private static String mOutIdentifier;	
	private static String mEntryPoint;
	private static String mTransactionId;
	private static String mMailUrl;
	private static String mTodopostUrl;
	private static String mTodogetUrl;
	private static String mUserdir;
	private static String mFileseparator;
	
	private static String mSystemUsername;
	private static String mSystemUserPassword;
	private static String mLoginUrl;
	private static String mLogoutUrl;
	private static String mAuthTokenPrefix;
	
	private static String mFTPServer;
	private static String mFTPUser;
	private static String mFTPPassword;

	@LoadConfig(configType = ConfigType.COMMON)
	public static void loadConfig() {

		Document configDoc = MongoProductConfig.getConfig("ETicketConfig");
		mDBServiceURL = configDoc.getString("dBServiceURL");
		mProcessDefUrl = configDoc.getString("processDefUrl");
		mDtmsUser = configDoc.getString("dtmsUser");
		mDtmsPass = configDoc.getString("dtmsPass");
		mUserId = configDoc.getString("userId");
		mLookup = configDoc.getString("lookup");
		mTemplateUrl = configDoc.getString("templateUrl");
		mModeshapeUrl = configDoc.getString("modeshapeUrl");
		mOutIdentifier = configDoc.getString("outIdentifier");		
		mEntryPoint = configDoc.getString("entryPoint");
		mTransactionId = configDoc.getString("transactionId");
		mMailUrl = configDoc.getString("mailUrl");
		mTodopostUrl = configDoc.getString("todopostUrl");
		mTodogetUrl = configDoc.getString("todogetUrl");
		mUserdir = configDoc.getString("userdir");
		mFileseparator = configDoc.getString("fileseparator");
		
		mSystemUsername = configDoc.getString("systemUsername");
		mSystemUserPassword = configDoc.getString("systemUserPassword");
		mLoginUrl = configDoc.getString("loginUrl");
		mLogoutUrl = configDoc.getString("logoutUrl");
		mAuthTokenPrefix = configDoc.getString("authTokenPrefix");
		
		mFTPServer = configDoc.getString("ftpServer");
		mFTPUser = configDoc.getString("ftpUser");
		mFTPPassword = configDoc.getString("ftpPassword");

	}

	public static String getmDBServiceURL() {
		return mDBServiceURL;
	}

	public static String getmProcessDefUrl() {
		return mProcessDefUrl;
	}

	public static String getmDtmsUser() {
		return mDtmsUser;
	}

	public static String getmDtmsPass() {
		return mDtmsPass;
	}

	public static String getmUserId() {
		return mUserId;
	}

	public static String getmLookup() {
		return mLookup;
	}

	public static String getmTemplateUrl() {
		return mTemplateUrl;
	}

	public static String getmModeshapeUrl() {
		return mModeshapeUrl;
	}

	public static String getmOutIdentifier() {
		return mOutIdentifier;
	}

	public static String getmEntryPoint() {
		return mEntryPoint;
	}

	public static String getmTransactionId() {
		return mTransactionId;
	}

	public static String getmMailUrl() {
		return mMailUrl;
	}

	public static String getmTodopostUrl() {
		return mTodopostUrl;
	}

	public static String getmTodogetUrl() {
		return mTodogetUrl;
	}

	public static String getmUserdir() {
		return mUserdir;
	}

	public static String getmFileseparator() {
		return mFileseparator;
	}

	public static String getmSystemUsername() {
		return mSystemUsername;
	}

	public static String getmSystemUserPassword() {
		return mSystemUserPassword;
	}

	public static String getmLoginUrl() {
		return mLoginUrl;
	}

	public static String getmLogoutUrl() {
		return mLogoutUrl;
	}

	public static String getmAuthTokenPrefix() {
		return mAuthTokenPrefix;
	}
	public static String getmFTPServer() {
		return mFTPServer;
	}

	public static String getmFTPUser() {
		return mFTPUser;
	}

	public static String getmFTPPassword() {
		return mFTPPassword;
	}

	

}
