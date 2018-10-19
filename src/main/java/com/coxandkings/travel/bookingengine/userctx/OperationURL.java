package com.coxandkings.travel.bookingengine.userctx;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;


import static com.coxandkings.travel.bookingengine.utils.Constants.NS_COM;

public class OperationURL  {
	public static final String JSON_PROP_OPNAME = "operationName";
	public static final String JSON_PROP_EPURL = "endUrl";
	private String mOpName, mOpURL;
	
	OperationURL(JSONObject opUrlJson) {
		mOpName = opUrlJson.getString(JSON_PROP_OPNAME);
		mOpURL = opUrlJson.getString(JSON_PROP_EPURL);
	}

	OperationURL(org.bson.Document epUrlDoc) {
		mOpName = epUrlDoc.getString(JSON_PROP_OPNAME);
		mOpURL = epUrlDoc.getString(JSON_PROP_EPURL);
	}

	public String getOperationName() {
		return mOpName;
	}
	
	public String getOperationURL() {
		return mOpURL;
	}

	public Element toElement(Document ownerDoc) {
		Element opUrlElem = ownerDoc.createElementNS(NS_COM, "com:OperationURL");
		opUrlElem.setAttribute("operation", mOpName);
		opUrlElem.setTextContent(mOpURL);
		return opUrlElem;
	}

	public JSONObject toJSON() {
		JSONObject json = new JSONObject();
		json.put(JSON_PROP_OPNAME, mOpName);
		json.put(JSON_PROP_EPURL, mOpURL);
		return json;
	}
}
