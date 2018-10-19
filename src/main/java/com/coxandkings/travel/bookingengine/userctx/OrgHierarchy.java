package com.coxandkings.travel.bookingengine.userctx;

import java.util.ArrayList;

import org.bson.Document;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.mongodb.DBCollection;

public class OrgHierarchy implements Constants {

	private String mGroupOfCompaniesId, mGroupCompanyId, mCompanyId, mCompanyName, mCompanyState, mCompanyCity, mCompanyCountry, mSBU, mBU,mCompanyMarketId,mCompanyMarketName;
	private String mDivision, mSalesOffice, mSalesOfficeLoc, mSalesOfficeName;
	
	@Deprecated
	OrgHierarchy(String orgHierarchyStr)
	{
		JSONObject orgHierarchyJson = new JSONObject(orgHierarchyStr);
		
		mGroupOfCompaniesId = orgHierarchyJson.optString(JSON_PROP_GROUPOFCOMPANIESID);
		mGroupCompanyId = orgHierarchyJson.optString(JSON_PROP_GROUPCOMPANYID);
		mCompanyId = orgHierarchyJson.optString(JSON_PROP_COMPANYID, "");
		mCompanyName = orgHierarchyJson.optString(JSON_PROP_COMPANYNAME);
		mCompanyCity = orgHierarchyJson.optString(JSON_PROP_COMPANYCITY);
		mCompanyState = orgHierarchyJson.optString(JSON_PROP_COMPANYSTATE);
		mCompanyCountry = orgHierarchyJson.optString(JSON_PROP_COMPANYCOUNTRY);
		mSBU = orgHierarchyJson.optString(JSON_PROP_SBU);
		mBU = orgHierarchyJson.optString(JSON_PROP_BU);
		mDivision = orgHierarchyJson.optString(JSON_PROP_DIVISION);
		mSalesOffice = orgHierarchyJson.optString(JSON_PROP_SALESOFFICE);
		mSalesOfficeLoc = orgHierarchyJson.optString(JSON_PROP_SALESOFFICELOC);
		mSalesOfficeName = orgHierarchyJson.optString(JSON_PROP_SALESOFFICENAME);
	
	}
	
	/*@Deprecated
	OrgHierarchy(JSONObject orgHierarchyJson)
	{
		if(orgHierarchyJson!=null)
		{
			mSBU = orgHierarchyJson.optString(MDM_PROP_SBU);
			mBU = orgHierarchyJson.optString(MDM_PROP_BU);
			mSalesOfficeName = orgHierarchyJson.optString(JSON_PROP_SALESOFFICENAME); 

				
			if(orgHierarchyJson.has(MDM_PROP_COMPANYID))
					mCompanyId = orgHierarchyJson.optString(MDM_PROP_COMPANYID);
				else if(orgHierarchyJson.has(MDM_PROP_COMPANY))
					mCompanyId = orgHierarchyJson.optString(MDM_PROP_COMPANY);
				
				JSONObject orgEntityJson = MDMUtils.getOrgHierarchyDocumentByIdv2(MDM_VAL_TYPECOMPANY, mCompanyId);
				if(orgEntityJson!=null)
				{
					mGroupOfCompaniesId = orgEntityJson.optString(MDM_PROP_GROUPOFCOMPANIESID);
					mGroupCompanyId = orgEntityJson.optString(MDM_PROP_GROUPCOMPANYID);
					mCompanyName =  orgEntityJson.optString(MDM_PROP_NAME);
					JSONObject addrJson = orgEntityJson.optJSONObject(MDM_PROP_ADDRESS);
					if(addrJson!=null)
					{
						mCompanyCity = addrJson.optString(MDM_PROP_CITY);
						mCompanyState = addrJson.optString(MDM_PROP_STATE);
						mCompanyCountry = addrJson.optString(MDM_PROP_COUNTRY);
					}
					
				}
					

			if (mSalesOfficeName != null && mSalesOfficeName.isEmpty() == false)
			{
				 orgEntityJson  = MDMUtils.getOrgHierarchyDocumentByNamev2(MDM_VAL_TYPESO, mSalesOfficeName);
				if(orgEntityJson!=null)
				{
					mSalesOffice = orgEntityJson.optString(DBCollection.ID_FIELD_NAME);
					JSONObject addrJson = orgEntityJson.optJSONObject(MDM_PROP_ADDRESS);
					if(addrJson!=null)
					{
						mSalesOfficeLoc = addrJson.optString(MDM_PROP_CITY);
					}
					mDivision = orgEntityJson.optString(MDM_PROP_DIVISION);
				}
			}
			
		}
	}*/
	
	OrgHierarchy(JSONObject orgHierarchyJson) {
		mGroupOfCompaniesId = orgHierarchyJson.optString(JSON_PROP_GROUPOFCOMPANIESID, "");
		mGroupCompanyId = orgHierarchyJson.optString(JSON_PROP_GROUPCOMPANYID, "");
		mCompanyId = orgHierarchyJson.optString(JSON_PROP_COMPANYID, "");
		mCompanyName = orgHierarchyJson.optString(JSON_PROP_COMPANYNAME, "");
		mCompanyCity = orgHierarchyJson.optString(JSON_PROP_COMPANYCITY, "");
		mCompanyState = orgHierarchyJson.optString(JSON_PROP_COMPANYSTATE, "");
		mCompanyCountry = orgHierarchyJson.optString(JSON_PROP_COMPANYCOUNTRY, "");
		mSBU = orgHierarchyJson.optString(JSON_PROP_SBU, "");
		mBU = orgHierarchyJson.optString(JSON_PROP_BU, "");
		mCompanyMarketId= orgHierarchyJson.optString(JSON_PROP_COMPANYMKT, "");
		mCompanyMarketName= orgHierarchyJson.optString(JSON_PROP_COMPANYMKTNAME, "");
		mDivision = orgHierarchyJson.optString(JSON_PROP_DIVISION, "");
		mSalesOffice = orgHierarchyJson.optString(JSON_PROP_SALESOFFICE, "");
		mSalesOfficeLoc = orgHierarchyJson.optString(JSON_PROP_SALESOFFICELOC);
		mSalesOfficeName = orgHierarchyJson.optString(JSON_PROP_SALESOFFICENAME, "");
	}
	
	@SuppressWarnings("unchecked")
	// The org.bson.Document received here would either be from clientB2Bs (clientProfile.orgHierarchy) or clientTypes (orgHierarchy)
	OrgHierarchy(Document orgHierarchyDoc) {
		Document orgEntityDoc = null;
		if (orgHierarchyDoc != null) {
			mCompanyId = (orgHierarchyDoc.containsKey(MDM_PROP_COMPANYID)) ? orgHierarchyDoc.getString(MDM_PROP_COMPANYID) : (orgHierarchyDoc.containsKey(MDM_PROP_COMPANY)) ? orgHierarchyDoc.getString(MDM_PROP_COMPANY) : "";
			orgEntityDoc = MDMUtils.getOrgHierarchyDocumentById(MDM_VAL_TYPECOMPANY, mCompanyId);
			if (orgEntityDoc != null) {
				mGroupOfCompaniesId = (String) orgEntityDoc.getOrDefault(MDM_PROP_GROUPOFCOMPANIESID, "");
				mGroupCompanyId = (String) orgEntityDoc.getOrDefault(MDM_PROP_GROUPCOMPANYID, "");
				mCompanyName = (String) orgEntityDoc.getOrDefault(MDM_PROP_NAME, "");
				mCompanyCity = MDMUtils.getValueAtPathFromDocument(orgEntityDoc, MDM_PROP_ADDRESS.concat(".").concat(MDM_PROP_CITY));
				mCompanyState = MDMUtils.getValueAtPathFromDocument(orgEntityDoc, MDM_PROP_ADDRESS.concat(".").concat(MDM_PROP_STATE));
				mCompanyCountry = MDMUtils.getValueAtPathFromDocument(orgEntityDoc, MDM_PROP_ADDRESS.concat(".").concat(MDM_PROP_COUNTRY));
			}
			
			mSBU = (String) orgHierarchyDoc.getOrDefault(MDM_PROP_SBU, "");
			mBU = (String) orgHierarchyDoc.getOrDefault(MDM_PROP_BU, "");
			mCompanyMarketId=(String) orgHierarchyDoc.getOrDefault("marketId", "");
			mCompanyMarketName=(String) orgHierarchyDoc.getOrDefault(MDM_PROP_COMPANYMKT,"");
			ArrayList<Document> companyMarkets = (ArrayList<Document>) orgHierarchyDoc.getOrDefault("companyMarkets", new ArrayList<Document>());
			if(companyMarkets!=null && !companyMarkets.isEmpty()) {
				mCompanyMarketId=(String) companyMarkets.get(0).get("marketId");
			}
			if(Utils.isStringNotNullAndNotEmpty(mCompanyMarketId)) {
				org.bson.Document mKtDetailsDoc=MDMUtils.getMarketDetailsFromOrganizationColl(mCompanyMarketId,MDM_VAL_TYPEMARKET);
				if(mKtDetailsDoc!=null)
					mCompanyMarketName=mKtDetailsDoc.getString(MDM_PROP_NAME);	
			}
			// clientType document from MDM does not contain Sales Office information
			mSalesOfficeName = (String) orgHierarchyDoc.getOrDefault(MDM_PROP_REPORTSONAME, "");
			
			if (mSalesOfficeName != null && mSalesOfficeName.isEmpty() == false) {
				// TODO: Check if companyId also needs to be passed in the following call
				orgEntityDoc = MDMUtils.getOrgHierarchyDocumentByName(MDM_VAL_TYPESO, mSalesOfficeName);
				if (orgEntityDoc != null) {
					mSalesOffice = orgEntityDoc.getString(DBCollection.ID_FIELD_NAME);
					mSalesOfficeLoc = MDMUtils.getValueAtPathFromDocument(orgEntityDoc, MDM_PROP_ADDRESS.concat(".").concat(MDM_PROP_CITY));
					mDivision = orgEntityDoc.getString(MDM_PROP_DIVISION);
				}
			}
		}
	}
	
	public JSONObject toJSON() {
		JSONObject orgHierarchyJson = new JSONObject();
		
		orgHierarchyJson.put(JSON_PROP_GROUPOFCOMPANIESID, mGroupOfCompaniesId);
		orgHierarchyJson.put(JSON_PROP_GROUPCOMPANYID, mGroupCompanyId);
		orgHierarchyJson.put(JSON_PROP_COMPANYID, mCompanyId);
		orgHierarchyJson.put(JSON_PROP_COMPANYNAME, mCompanyName);
		orgHierarchyJson.put(JSON_PROP_COMPANYCITY, mCompanyCity);
		orgHierarchyJson.put(JSON_PROP_COMPANYSTATE, mCompanyState);
		orgHierarchyJson.put(JSON_PROP_COMPANYCOUNTRY, mCompanyCountry);
		orgHierarchyJson.put(JSON_PROP_SBU, mSBU);
		orgHierarchyJson.put(JSON_PROP_BU, mBU);
		orgHierarchyJson.put(JSON_PROP_COMPANYMKT, mCompanyMarketId);
		orgHierarchyJson.put(JSON_PROP_COMPANYMKTNAME, mCompanyMarketName);
		orgHierarchyJson.put(JSON_PROP_DIVISION, mDivision);
		orgHierarchyJson.put(JSON_PROP_SALESOFFICE, mSalesOffice);
		orgHierarchyJson.put(JSON_PROP_SALESOFFICELOC, mSalesOfficeLoc);
		orgHierarchyJson.put(JSON_PROP_SALESOFFICENAME, mSalesOfficeName);
		
		return orgHierarchyJson;
	}
	
	public String getCompanyId() {
		return mCompanyId;
	}
	
	public String getGroupCompaniesId() {
		return mGroupOfCompaniesId;
	}
	
	public String getGroupCompanyId() {
		return mGroupCompanyId;
	}

	public String getCompanyCity() {
		return mCompanyCity;
	}

	public String getCompanyCountry() {
		return mCompanyCountry;
	}

	public String getCompanyName() {
		return mCompanyName;
	}

	public String getCompanyState() {
		return mCompanyState;
	}

	public String getSBU() {
		return mSBU;
	}

	public String getBU() {
		return mBU;
	}

	public String getDivision() {
		return mDivision;
	}

	public String getSalesOffice() {
		return mSalesOffice;
	}

	public String getSalesOfficeLoc() {
		return mSalesOfficeLoc;
	}

	public String getSalesOfficeName() {
		return mSalesOfficeName;
	}
	
	public String getCompanyMarket() {
		return mCompanyMarketId;
	}
	
	public String getCompanyMarketName() {
		return mCompanyMarketName;
	}
	
}
