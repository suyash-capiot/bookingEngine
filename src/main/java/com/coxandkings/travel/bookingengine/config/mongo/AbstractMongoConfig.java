package com.coxandkings.travel.bookingengine.config.mongo;


import java.net.URLEncoder;

import com.coxandkings.travel.bookingengine.utils.CryptoUtil;

public abstract class AbstractMongoConfig implements MongoConfig {
	protected String mMongoURL = "";
	protected String mMongoUser = "";
	protected String mMongoPwd = "";
	protected String mMongoDB = "";
	protected String mMongoURI = "";

	protected void formatMongoURI() {
		StringBuilder strBldr = new StringBuilder(mMongoURL);
		if (mMongoUser != null && mMongoUser.isEmpty() == false) {
			String mongoPwd = CryptoUtil.decrypt(mMongoPwd);
			int atCharIdx = strBldr.indexOf("@");
			if (atCharIdx > -1) {
				strBldr.replace("mongodb://".length(), atCharIdx, String.format("%s:%s", urlEncodeString(mMongoUser), urlEncodeString(mongoPwd)));
			}
			else {
				strBldr.insert("mongodb://".length(), String.format("%s:%s@", urlEncodeString(mMongoUser), urlEncodeString(mongoPwd)));
			}
		}
		
		if (mMongoDB != null && mMongoDB.isEmpty() == false) {
			int slashIdx = strBldr.indexOf("/", "mongodb://".length());
			int questionIdx = strBldr.indexOf("?");
			if (slashIdx > -1) {
				if (questionIdx > -1) {
					strBldr.replace(slashIdx + 1, questionIdx, mMongoDB);
				}
				else {
					strBldr.replace(slashIdx + 1, strBldr.length(), mMongoDB);
				}
			}
			else {
				strBldr.append(String.format("/%s", mMongoDB));
			}
		}
		
		mMongoURI = strBldr.toString();
	}
	
	@Override
	public String getConnectionURI() {
		return mMongoURI;
	}

	public String getDatabaseName() {
		return mMongoDB;
	}

	private String urlEncodeString(String strToBeEncoded) {
		try {
			return URLEncoder.encode(strToBeEncoded, "UTF-8");
		}
		catch (Exception x) {
			return strToBeEncoded;
		}
	}
}
