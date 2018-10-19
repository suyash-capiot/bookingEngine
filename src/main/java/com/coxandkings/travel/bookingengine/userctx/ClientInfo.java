package com.coxandkings.travel.bookingengine.userctx;

import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.utils.Constants;

public class ClientInfo implements Constants {

	public enum CommercialsEntityType {

		ClientGroup(MDM_VAL_CLIENTTYPE_GROUP), ClientSpecific(MDM_VAL_CLIENTTYPE_CLIENT), ClientType(
				MDM_VAL_CLIENTTYPE_ENTITY);

		private String mCommEntityType;

		private CommercialsEntityType(String type) {
			mCommEntityType = type;
		}

		public String toString() {
			return mCommEntityType;
		}
	}

	private String mClientId, mClientMarket, mParentId, mCommEntityId, mCommEntityMarket;
	private CommercialsEntityType mCommEntityType;

	ClientInfo(JSONObject clientInfoJson) {
		mClientId = clientInfoJson.getString(JSON_PROP_CLIENTID);
		mClientMarket = clientInfoJson.getString(JSON_PROP_CLIENTMARKET);
		mParentId = clientInfoJson.optString(JSON_PROP_PARENTCLIENTID);
		mCommEntityType = CommercialsEntityType.valueOf(clientInfoJson.optString(JSON_PROP_COMMENTITYTYPE));
		mCommEntityId = clientInfoJson.optString(JSON_PROP_COMMENTITYID);
		mCommEntityMarket = clientInfoJson.optString(JSON_PROP_COMMENTITYMARKET);
	}

	ClientInfo() {
	}

	public String getClientId() {
		return mClientId;
	}

	public String getClientMarket() {
		return mClientMarket;
	}

	public String getParentClienttId() {
		return mParentId;
	}

	public String getCommercialsEntityId() {
		return mCommEntityId;
	}

	public String getCommercialsEntityMarket() {
		return mCommEntityMarket;
	}

	public CommercialsEntityType getCommercialsEntityType() {
		return mCommEntityType;
	}

	public boolean hasParent() {
		return (mParentId != null && mParentId.trim().isEmpty() == false);
	}

	void setClientId(String clientId) {
		this.mClientId = clientId;
	}

	void setClientMarket(String clientMarket) {
		this.mClientMarket = clientMarket;
	}

	void setParentClientId(String parentId) {
		this.mParentId = parentId;
	}

	void setCommercialsEntityId(String commEntityId) {
		this.mCommEntityId = commEntityId;
	}

	void setCommercialsEntityMarket(String commEntityMarket) {
		this.mCommEntityMarket = commEntityMarket;
	}

	void setCommercialsEntityType(ClientInfo.CommercialsEntityType commEntityType) {
		this.mCommEntityType = commEntityType;
	}

	public JSONObject toJSON() {
		JSONObject clientInfoJson = new JSONObject();

		clientInfoJson.put(JSON_PROP_CLIENTID, mClientId);
		clientInfoJson.put(JSON_PROP_CLIENTMARKET, mClientMarket);
		clientInfoJson.put(JSON_PROP_PARENTCLIENTID, mParentId);
		clientInfoJson.put(JSON_PROP_COMMENTITYTYPE, mCommEntityType);
		clientInfoJson.put(JSON_PROP_COMMENTITYID, mCommEntityId);
		clientInfoJson.put(JSON_PROP_COMMENTITYMARKET, mCommEntityMarket);

		return clientInfoJson;
	}
}
