package com.coxandkings.travel.bookingengine.userctx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;
import com.mongodb.DBCollection;

public class ProductSupplier implements Constants {
	private String mSuppId, mCredsName, mCompanyId,mSuppName;
	private Map<String, Credential> mSuppCreds = new LinkedHashMap<String, Credential>();
	private List<OperationURL> mOpURLs = new ArrayList<OperationURL>();
	private SupplierSettlementTerms mSuppSettTerms;
	private boolean mIsGDS;
	private String mFinanceControlId;
	private String mSupplierMarket;
	
	
	private static Pattern mSubstitutionPattern = Pattern.compile("\\$\\{([^}]+)\\}");
	
	/*@Deprecated
	ProductSupplier(JSONObject prodSuppJson) throws Exception {
		JSONArray credsJson=null;
		JSONArray opUrlsJson = null;
		mCompanyId = prodSuppJson.optString(JSON_PROP_COMPANYID);
			JSONObject suppliers  = prodSuppJson.optJSONObject(MDM_PROP_SUPP);
			if(suppliers!=null)
			   mSuppId = suppliers.getString(MDM_PROP_SUPPID);
			else
				mSuppId="";
			
			mCredsName = prodSuppJson.optString(DBCollection.ID_FIELD_NAME);
		
			if (mSuppId.isEmpty() == false) {
				JSONObject suppMasterJson = MDMUtils.getSupplierv2(mSuppId);
				if (suppMasterJson != null) {
					JSONObject supplier = suppMasterJson.has(MDM_PROP_SUPP)?suppMasterJson.getJSONObject(MDM_PROP_SUPP):new JSONObject();
					
					mFinanceControlId = supplier.optString("financeControlId", "");
					
					JSONArray enablerCategories = supplier.optJSONArray(MDM_PROP_ENABLERCATEG);
					for(int i=0;enablerCategories!=null && i<enablerCategories.length();i++){
						mIsGDS = enablerCategories.optString(i).equals(MDM_VAL_SUPPGDS);
						if(mIsGDS)
							break;
					}
				}
			}
			JSONObject credJson = prodSuppJson.optJSONObject(MDM_PROP_CREDDEATILS);
			if(credJson!=null) {
				credsJson = credJson.optJSONArray(JSON_PROP_CREDS);
				opUrlsJson = credJson.optJSONArray(MDM_PROP_ENDPOINTURLS);
			}
			for (int i=0; credsJson!=null && i < credsJson.length(); i++) {
			Credential cred = new Credential(credsJson.getJSONObject(i)); 
			mSuppCreds.put(cred.getKey(), cred);
		}
		
			for (int i=0; opUrlsJson!=null && i < opUrlsJson.length(); i++) {
			mOpURLs.add(new OperationURL(opUrlsJson.getJSONObject(i)));
			}
	}*/
	
	@Deprecated
	ProductSupplier(String str)
	{
		JSONObject prodSuppJson = new JSONObject(str);
		mSuppId = prodSuppJson.getString(JSON_PROP_SUPPID);
		mSuppName=prodSuppJson.getString(JSON_PROP_SUPPNAME);
		mCredsName = prodSuppJson.getString(JSON_PROP_CREDSNAME);
		JSONArray credsJson = prodSuppJson.getJSONArray(JSON_PROP_CREDS);
		JSONArray opUrlsJson = prodSuppJson.getJSONArray(JSON_PROP_OPURLS);
		mIsGDS = prodSuppJson.optBoolean(JSON_PROP_ISGDS);
		mCompanyId = prodSuppJson.optString(JSON_PROP_COMPANYID);
		for (int i=0; credsJson!=null && i < credsJson.length(); i++) {
			Credential cred = new Credential(credsJson.getJSONObject(i)); 
			mSuppCreds.put(cred.getKey(), cred);
		}
		
		for (int i=0; opUrlsJson!=null && i < opUrlsJson.length(); i++) {
			mOpURLs.add(new OperationURL(opUrlsJson.getJSONObject(i)));
		}
		
	}
	
	ProductSupplier(JSONObject prodSuppJson) throws Exception {
		mSuppId = prodSuppJson.getString(JSON_PROP_SUPPID);
		mSuppName=prodSuppJson.getString(JSON_PROP_SUPPNAME);
		mCredsName = prodSuppJson.getString(JSON_PROP_CREDSNAME);
		mCompanyId = prodSuppJson.optString(JSON_PROP_COMPANYID);
		mIsGDS = prodSuppJson.optBoolean(JSON_PROP_ISGDS);
        mFinanceControlId=prodSuppJson.optString(JSON_PROP_FINANCECONTROLID);
        mSupplierMarket=prodSuppJson.getString(JSON_PROP_SUPPMARKET);
        
		JSONArray credsJson = prodSuppJson.getJSONArray(JSON_PROP_CREDS);
		for (int i=0; i < credsJson.length(); i++) {
			Credential cred = new Credential(credsJson.getJSONObject(i)); 
			mSuppCreds.put(cred.getKey(), cred);
		}

		JSONArray opUrlsJson = prodSuppJson.getJSONArray(JSON_PROP_OPURLS);
		for (int i=0; i < opUrlsJson.length(); i++) {
			mOpURLs.add(new OperationURL(opUrlsJson.getJSONObject(i)));
		}
	}

	@SuppressWarnings("unchecked")
	ProductSupplier(org.bson.Document prodSuppDoc) throws Exception {
		mCredsName = prodSuppDoc.getString(DBCollection.ID_FIELD_NAME);
		org.bson.Document suppDoc = (org.bson.Document) prodSuppDoc.get(MDM_PROP_SUPP);
		mSuppId = (suppDoc != null) ? suppDoc.getString(MDM_PROP_SUPPID) : "";
		mSuppName=(suppDoc != null) ? suppDoc.getString(MDM_PROP_SUPPNAME) : "";
		if (mSuppId.isEmpty() == false) {
			org.bson.Document suppMasterDoc = MDMUtils.getSupplier(mSuppId);
			if (suppMasterDoc != null) {
				mCompanyId = suppMasterDoc.getString(MDM_PROP_COMPANYID);
				Object enablerCategories = MDMUtils.getValueObjectAtPathFromDocument(suppMasterDoc, MDM_PROP_SUPP.concat(".").concat(MDM_PROP_ENABLERCATEG));
				mFinanceControlId = (String) MDMUtils.optValueObjectAtPathFromDocument(suppMasterDoc, MDM_PROP_SUPP.concat(".").concat("financeControlId"),"");
				mIsGDS = (enablerCategories != null && ((List<String>)enablerCategories).contains(MDM_VAL_SUPPGDS));
			}
		}

		org.bson.Document credDtlsDoc = (org.bson.Document) prodSuppDoc.get(MDM_PROP_CREDDEATILS);
		List<org.bson.Document> credsDocs = (List<org.bson.Document>) credDtlsDoc.get(MDM_PROP_CREDS);
		for (org.bson.Document credsDoc : credsDocs) {
			Credential cred = new Credential(credsDoc); 
			mSuppCreds.put(cred.getKey(), cred);
		}

		List<org.bson.Document> epUrlsDocs = (List<org.bson.Document>) credDtlsDoc.get(MDM_PROP_ENDPOINTURLS);
		for (org.bson.Document epUrlsDoc : epUrlsDocs) {
			mOpURLs.add(new OperationURL(epUrlsDoc));
		}
		
		List<String> supplierMrkts = (List<String>) prodSuppDoc.get(MDM_PROP_SUPPMARKET);
		mSupplierMarket=(supplierMrkts!=null && !supplierMrkts.isEmpty())?supplierMrkts.get(0):"";
			
	}
	
	public String getSupplierID() {
		return mSuppId;
	}
	
	public Credential getCredentialForKey(String key) {
		return mSuppCreds.get(key);
	}
	
	public Collection<Credential> getCredentials() {
		return mSuppCreds.values();
	}

	public String getCredentialsName() {
		return mCredsName;
	}

	public List<OperationURL> getOperationURLs() {
		return mOpURLs;
	}
	
	public boolean isGDS() {
		return mIsGDS;
	}
	
	public String getFinanceControlId(){
		return mFinanceControlId;
	}
	
	public String getSuppName() {
		return mSuppName;
	}

	public void setSuppName(String mSuppName) {
		this.mSuppName = mSuppName;
	}
	
	public String getmCompanyId() {
		return mCompanyId;
	}

	public void setmCompanyId(String mCompanyId) {
		this.mCompanyId = mCompanyId;
	}
	
public String getSupplierMrkt() {
		return mSupplierMarket;
	}
	
	public void setSupplirMrkt(String mSuppMarket) {
		this.mSupplierMarket = mSuppMarket;
	}
	public Element toElement(Document ownerDoc) {
		Element suppCredsElem = ownerDoc.createElementNS(NS_COM, "com:SupplierCredentials");
		
		Element suppIDElem = ownerDoc.createElementNS(NS_COM, "com:SupplierID");
		suppIDElem.setTextContent(mSuppId);
		suppCredsElem.appendChild(suppIDElem);
		
		Element credsElem = ownerDoc.createElementNS(NS_COM, "com:Credentials");
		credsElem.setAttribute("name", mCredsName);
		Collection<Credential> creds = mSuppCreds.values();
		for (Credential cred : creds) {
			credsElem.appendChild(cred.toElement(ownerDoc));
		}
		
		Element opUrlsElem = ownerDoc.createElementNS(NS_COM, "com:OperationURLs");
		for (OperationURL opUrl : mOpURLs) {
			opUrlsElem.appendChild(opUrl.toElement(ownerDoc));
		}
		credsElem.appendChild(opUrlsElem);
		suppCredsElem.appendChild(credsElem);
		
		return suppCredsElem;
	}
	
	public Element toElement(Document ownerDoc, int sequence) {
		Element suppCredsElem = toElement(ownerDoc);
		Element seqElem = ownerDoc.createElementNS(NS_COM, "com:Sequence");
		seqElem.setTextContent(String.valueOf(sequence));
		suppCredsElem.insertBefore(seqElem, XMLUtils.getFirstNodeAtXPath(suppCredsElem, "./com:Credentials"));
		return suppCredsElem;
	}
	
	public JSONObject toJSON() {
		JSONObject json = new JSONObject();
		json.put(JSON_PROP_SUPPID, mSuppId);
		json.put(JSON_PROP_SUPPNAME, mSuppName);
		json.put(JSON_PROP_CREDSNAME, mCredsName);
		json.put(JSON_PROP_COMPANYID, mCompanyId);
		json.put(JSON_PROP_ISGDS, mIsGDS);
		json.put(JSON_PROP_FINANCECONTROLID, mFinanceControlId);
		json.put(JSON_PROP_SUPPMARKET, mSupplierMarket);

		// Create Credentials JSON structure
		JSONArray credJsonArr = new JSONArray();
		Collection<Credential> creds = mSuppCreds.values();
		for (Credential cred : creds) {
			credJsonArr.put(cred.toJSON());
		}
		json.put(JSON_PROP_CREDS, credJsonArr);
		
		// Create OperationURLs JSON structure
		JSONArray opUrlJsonArr = new JSONArray();
		for (OperationURL opUrl : mOpURLs) {
			opUrlJsonArr.put(opUrl.toJSON());
		}
		json.put(JSON_PROP_OPURLS, opUrlJsonArr);
		
		return json;
	}
	
	public Element getPaymentDetailsElement(String prodCateg, String prodCategSubType, org.w3c.dom.Document ownerDoc) {
		if (mSuppSettTerms == null) {
			mSuppSettTerms = new SupplierSettlementTerms(this, prodCateg, prodCategSubType);
		}
		
		String siPayDtlTemplate = mSuppSettTerms.getPaymentDetailsElementTemplate();
		StringBuilder payElemStrBldr = new StringBuilder(siPayDtlTemplate);
		Matcher matcher = mSubstitutionPattern.matcher(siPayDtlTemplate);
		while (matcher.find()) {
			String substituteStr = matcher.group(); 
			String substituteID = substituteStr.substring(2, (substituteStr.length() - 1));
			Credential suppCred = mSuppCreds.get(substituteID);
			payElemStrBldr.replace(matcher.start(), matcher.end(), (suppCred != null) ? suppCred.getValue() : "");
		}

		Element payElem = XMLTransformer.toXMLElement(payElemStrBldr.toString());
		
		return (Element) ownerDoc.importNode(payElem, true);
	}
	
	public String toString() {
		return this.toJSON().toString();
	}
	
}
