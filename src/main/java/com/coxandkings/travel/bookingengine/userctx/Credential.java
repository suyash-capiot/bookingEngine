package com.coxandkings.travel.bookingengine.userctx;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static com.coxandkings.travel.bookingengine.utils.Constants.NS_COM;

public class Credential  {
	public static final String JSON_PROP_TITLE = "title";
	public static final String JSON_PROP_VALUE = "value";
	public static final String JSON_PROP_ISENCRYPTED = "isEncrypted";
	private String mCredKey, mCredVal;
	private boolean mIsEncrypted;
	
	Credential(JSONObject credJson) {
		mCredKey = credJson.getString(JSON_PROP_TITLE);
		mCredVal = credJson.getString(JSON_PROP_VALUE);
		mIsEncrypted = Boolean.valueOf(credJson.optBoolean(JSON_PROP_ISENCRYPTED));
	}

	Credential(org.bson.Document credDoc) {
		mCredKey = credDoc.getString(JSON_PROP_TITLE);
		mCredVal = credDoc.getString(JSON_PROP_VALUE);
		mIsEncrypted = Boolean.valueOf(credDoc.getBoolean(JSON_PROP_ISENCRYPTED, false));
	}

	public String getKey() {
		return mCredKey;
	}
	
	public String getValue() {
		return mCredVal;
	}
	
	public boolean isEnrypted() {
		return mIsEncrypted;
	}
	
	public Element toElement(Document ownerDoc) {
		Element credElem = ownerDoc.createElementNS(NS_COM, "com:Credential");
		credElem.setAttribute("name", mCredKey);
		credElem.setAttribute("isEncrypted", String.valueOf(mIsEncrypted));
		credElem.setTextContent(mCredVal);
		return credElem;
	}
	
	public JSONObject toJSON() {
		JSONObject json = new JSONObject();
		json.put(JSON_PROP_TITLE, mCredKey);
		json.put(JSON_PROP_ISENCRYPTED, String.valueOf(mIsEncrypted));
		json.put(JSON_PROP_VALUE, mCredVal);
		return json;
	}
}
