package com.coxandkings.travel.bookingengine.userctx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.config.MDMConfigV2;
import com.coxandkings.travel.bookingengine.config.MDMConfig;
import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.mongo.MongoCommonConfig;
import com.coxandkings.travel.bookingengine.enums.ShowRateOf;
import com.coxandkings.travel.bookingengine.enums.SortOrder;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.mongodb.DBCollection;
import com.mongodb.QueryOperators;
import com.mongodb.client.FindIterable;

import com.mongodb.client.MongoCursor;

import redis.clients.jedis.Jedis;

@Deprecated
public class UserContextV2 implements Constants{

	private static Logger logger = LogManager.getLogger(UserContextV2.class);
	private String mClientID, mClientName, mClientIATANumber, mClientCategory, mClientSubCategory, mClientCity, mClientState, mClientCountry, mUserID,mCredName;
	private ClientType mClientType;
	private List<String> mProdList = new ArrayList<String>();
	private Map<String, List<ProductSupplier>> mProdSuppMap = new HashMap<String, List<ProductSupplier>>();
	private List<ClientInfo> mClientHierarchyList;
	private OrgHierarchy mOrgHierarchy;
	private SortOrder mClientSortOrder;
	private ShowRateOf mClientShowRateOf;
	
	private UserContextV2(JSONTokener jsonTokener) throws Exception{
		JSONObject usrCtxJson = new JSONObject(jsonTokener);

		mUserID = usrCtxJson.optString(JSON_PROP_USERID, "");
		mClientID = usrCtxJson.optString(JSON_PROP_CLIENTID, "");
		mClientType = ClientType.valueOf(usrCtxJson.optString(JSON_PROP_CLIENTTYPE));
		mClientName = usrCtxJson.optString(JSON_PROP_CLIENTNAME, "");
		mClientCity = usrCtxJson.optString(JSON_PROP_CLIENTCITY, "");
		mClientState = usrCtxJson.optString(JSON_PROP_CLIENTSTATE, "");
		mClientCountry = usrCtxJson.optString(JSON_PROP_CLIENTCOUNTRY, "");
		mClientIATANumber = usrCtxJson.optString(JSON_PROP_IATANO, "");
		mClientCategory = usrCtxJson.optString(JSON_PROP_CLIENTCAT, "");
		mClientSubCategory = usrCtxJson.optString(JSON_PROP_CLIENTSUBCAT, "");
		mOrgHierarchy = new OrgHierarchy(usrCtxJson.getJSONObject(JSON_PROP_ORGHIERARCHY).toString());
		JSONArray clHierJsonArr = usrCtxJson.getJSONArray(JSON_PROP_CLIENTCOMMENTITYDTLS);
		mClientHierarchyList = new ArrayList<ClientInfo>(clHierJsonArr.length());
		for (int i=0; i < clHierJsonArr.length(); i++) {
			mClientHierarchyList.add(new ClientInfo(clHierJsonArr.getJSONObject(i)));
		}
		
		JSONArray productsJson = usrCtxJson.getJSONArray(JSON_PROP_PRODS);
		for (int i=0; i < productsJson.length(); i++) {
			mProdList.add((String) productsJson.getString(i));
		}
		
		JSONArray prodsSuppsJson = usrCtxJson.getJSONArray(JSON_PROP_PRODSUPPS);
		for (int i=0; i < prodsSuppsJson.length(); i++) {
			JSONObject prodSuppJson = prodsSuppsJson.getJSONObject(i);
			String product = prodSuppJson.getString(JSON_PROP_PROD);
			List<ProductSupplier> prodSuppsList = new ArrayList<ProductSupplier>();
			JSONArray suppsJson = prodSuppJson.getJSONArray(JSON_PROP_SUPPS);
			for (int j=0; j < suppsJson.length(); j++) {
				prodSuppsList.add(new ProductSupplier(suppsJson.getJSONObject(j).toString()));
				
			}
			mProdSuppMap.put(product, prodSuppsList);
		}
		
		mClientSortOrder = SortOrder.valueOfOrDefault(usrCtxJson.optString(JSON_PROP_CLIENTSORTORDER), SortOrder.Price);
		mClientShowRateOf = ShowRateOf.valueOfOrDefault(usrCtxJson.optString(JSON_PROP_CLIENTSHOWRATEOF), ShowRateOf.MultipleSuppliers);
	}
	
	
	private UserContextV2(JSONObject reqHdr) throws Exception {
		JSONObject clientContextJson = reqHdr.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientID = clientContextJson.getString(JSON_PROP_CLIENTID);
		String clientLang = clientContextJson.getString(JSON_PROP_CLIENTLANG);
		String clientMkt = clientContextJson.getString(JSON_PROP_CLIENTMARKET);
		String clientType = clientContextJson.getString(JSON_PROP_CLIENTTYPE);
		String pointOfSale = clientContextJson.getString(JSON_PROP_POS);
		mUserID = reqHdr.optString(JSON_PROP_USERID);
		
		
//		MDMUtils.pushMdmLoginDataToRedis();
		mClientType = ClientType.valueOf(clientType);
		if (mClientType == null) {
			throw new Exception("Client type is invalid");
		}
		
		if (ClientType.B2B == mClientType) {
			loadB2BUserInformation(reqHdr);
		}
		else if (ClientType.B2C == mClientType) {
			loadB2CUserInformation(reqHdr);
		}
		mClientHierarchyList = getClientCommercialsHierarchyList(clientID, clientLang, clientMkt, clientType, pointOfSale, null);		
		
		JSONObject clientEntityProdSuppJson = getProductSuppliersForClient(clientContextJson, clientContextJson.optString(JSON_PROP_CLIENTID));
		// TODO: Should not have to check the same condition (clientEntityProdSuppDoc == null) multiple times. 
		if (clientEntityProdSuppJson == null || clientEntityProdSuppJson.length()==0) {
			throw new Exception("No product supplier mapping was found for client entity");
		}
		
		JSONArray prodCatDocsJsonArr = clientEntityProdSuppJson.optJSONArray(MDM_PROP_PRODCATEGS);
		if (prodCatDocsJsonArr == null) {
			throw new Exception("No product categories defined in product supplier mapping for client entity");
		}
		
		for(int i=0;i<prodCatDocsJsonArr.length();i++) {
			
			JSONObject prodCatJson = prodCatDocsJsonArr.getJSONObject(i);
			String prodCategory = prodCatJson.getString(MDM_PROP_PRODCATEG); 
			
			JSONArray prodSubCatJsonArr = prodCatJson.getJSONArray(MDM_PROP_PRODCATEGSUBTYPES);
			if (prodSubCatJsonArr == null || prodSubCatJsonArr.length() == 0) {
				continue;
			}
			for(int j=0;j<prodSubCatJsonArr.length();j++)
			{
				JSONObject prodSubCatJson = prodSubCatJsonArr.getJSONObject(j);
				String prodSubCategory = prodSubCatJson.getString(MDM_PROP_SUBTYPE);
				String prod = prodCategory.concat("|").concat(prodSubCategory);
				mProdList.add(prod);
				JSONArray mappingsJsonArr = prodSubCatJson.optJSONArray(MDM_PROP_MAPPINGS);
				if (mappingsJsonArr == null || mappingsJsonArr.length() == 0) {
					continue;
				}
				List<ProductSupplier> prodSupps = new ArrayList<ProductSupplier>();
				List<String> dupCheckList = new ArrayList<String>();
				
				for(int k=0;k<mappingsJsonArr.length();k++)
				{
					JSONObject mappingJson = mappingsJsonArr.getJSONObject(k);
					JSONObject suppJson = mappingJson.getJSONObject(MDM_PROP_SUPP);
					String suppID = suppJson.getString(MDM_PROP_SUPPID);
					JSONArray creds = suppJson.getJSONArray(MDM_PROP_SUPPCREDS);
					
					if (creds == null || creds.length() == 0) {
						continue;
					}
					
					for(int l=0;l <creds.length();l++) {
						String cred = creds.getString(l);
					
						String dupCheckKey = String.format("%s|%s", suppID, cred);
						if (dupCheckList.contains(dupCheckKey)) {
							continue;
						}
						
						dupCheckList.add(dupCheckKey);
						JSONObject suppCredsJson = MDMUtils.getSupplierCredentialsConfigv2(suppID, cred);
						if ( suppCredsJson.length()==0) {
							logger.warn(String.format("Supplier credentials %s for supplier %s could not be retrieved from MDM", cred, suppID));
							continue;
						}
						
						prodSupps.add(new ProductSupplier(suppCredsJson));
					}
					
				}
				mProdSuppMap.put(prod, prodSupps);
			}
		}
	}
	
	
	private UserContextV2(String userCtxDoc) throws Exception {
		this(new JSONTokener(userCtxDoc));
	}
	
	// TODO: This is only a temporary constructor for testing. Delete when done.
	private UserContextV2(File usrCtxFile) throws FileNotFoundException , Exception{
		this(new JSONTokener(new FileReader(usrCtxFile)));
	}
	
	public List<ProductSupplier> getSuppliersForProduct(String prodCategory, String prodCategorySubType) {
		return mProdSuppMap.get(getProdSuppMapKey(prodCategory, prodCategorySubType));
	}
	
	public ProductSupplier getSupplierForProduct(String prodCategory, String prodCategorySubType, String suppID) {
		List<ProductSupplier> prodSupps = mProdSuppMap.get(getProdSuppMapKey(prodCategory, prodCategorySubType));
		for (ProductSupplier prodSupp : prodSupps) {
			if (prodSupp.getSupplierID().equals(suppID)) {
				return prodSupp;
			}
		}
		
		return null;
	}
	
	public static UserContextV2 getUserContextForSession(JSONObject reqHdr) {
		try  (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			String sessionID = reqHdr.getString(JSON_PROP_SESSIONID);
			String userID = reqHdr.optString(JSON_PROP_USERID);
			String usrCtxStr = redisConn.get(sessionID);

			UserContextV2 usrCtx = null; 
			if (usrCtxStr != null) { 
				usrCtx = new UserContextV2(usrCtxStr);
				// The following condition is added here as a B2C user will have to login at the 
				// time of Reprice and the proper user information should be loaded in user context 
				// TODO: Is a check for (clientType = B2C) required in below condition. 
				if (userID.equals(usrCtx.mUserID) == false) {
					usrCtx.loadB2CUserInformation(reqHdr);
					redisConn.getSet(sessionID, usrCtx.toJSON().toString());
					redisConn.pexpire(sessionID, (long) (MongoCommonConfig.getRedisSessionContextTTLMins() * 60 * 1000));
				}
			}
			else {
				usrCtx = new UserContextV2(reqHdr);
				redisConn.getSet(sessionID, usrCtx.toJSON().toString());
				redisConn.pexpire(sessionID, (long) (MongoCommonConfig.getRedisSessionContextTTLMins() * 60 * 1000));
			}

			return usrCtx;
		}
		catch (Exception x) {
			// TODO: Is this right thing to do?
			throw new RuntimeException(x);
		}
	}
	
	private static String getProdSuppMapKey(String prodCategory, String prodCategorySubType) {
		return prodCategory.concat("|").concat(prodCategorySubType);
	}
	
	private static JSONObject getProductSuppliersForClient(JSONObject clientContextJson, String clientID) {
		JSONObject clientEntityProdSuppJson = null;
		String clientLanguage = clientContextJson.getString(JSON_PROP_CLIENTLANG);
		String clientMarket = clientContextJson.getString(JSON_PROP_CLIENTMARKET);
		String clientType = clientContextJson.getString(JSON_PROP_CLIENTTYPE);
		//String company = clientContextJson.getString(JSON_PROP_COMPANY);
		String pointOfSale = clientContextJson.getString(JSON_PROP_POS);
		
		if (Utils.isStringNotNullAndNotEmpty(clientID)) {
			clientEntityProdSuppJson = getProductSuppliersConfig(clientID, clientMarket);
			if (clientEntityProdSuppJson.length()!=0) {
				return clientEntityProdSuppJson;
			}
			
			// Check if there is a Client Group associated with this Client ID
			// Reference CKIL_323230 (2.2.3/BR16): As per BRD, when tiers are configured for a
			// master agent, the corresponding client groups should be automatically created.
			// Therefore, following code should handle client groups as well as tiers.
			clientEntityProdSuppJson = getClientGroupProductSuppliers(clientID, clientMarket);
			if (clientEntityProdSuppJson != null || clientEntityProdSuppJson.length()!=0) {
				return clientEntityProdSuppJson;
			}
		}
		
		return getClientTypeProductSuppliers(clientType, clientMarket, clientLanguage, pointOfSale);
	}
	
	// Company is unique across groupOfCompanies/groupCompany hierarchy. Therefore, only company parameter should suffice.
	// Parameters for GroupOfCompanies and GroupCompany are not required.
	private static JSONObject getClientTypeProductSuppliers(String clientType, String clientMarket, String clientLanguage, String pointOfSale) {
		String clientTypeEntity = getClientTypeEntity(clientType, clientMarket, clientLanguage, pointOfSale);
		return (clientTypeEntity != null && clientTypeEntity.isEmpty() == false) ? getProductSuppliersConfig(clientTypeEntity, clientMarket) : null;
	}
	
	private static JSONObject getClientGroupProductSuppliers(String clientID, String clientMarket) {
		
		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_CLIENTID, clientID);
		filters.put(MDM_PROP_GROUPS.concat(".").concat(MDM_PROP_PRODSUPP), new Document(QueryOperators.EXISTS, true));
		
		String tmpstr  = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_ASSOCCLIENTTOGROUP), filters.toString()), true);

		// Reference CKIL_323230 (2.2.2/BR09): For a client, there could be multiple client group mappings.
		// In such case, the mapping with latest timestamp would be used.
		JSONObject latestUpdatedJson = getLatestUpdatedJson(new JSONObject(tmpstr));
		if (latestUpdatedJson.length()!=0) {
			 return null;
		}
		
		String clientGroupID = latestUpdatedJson.has(MDM_PROP_GROUPS)?latestUpdatedJson.getJSONObject(MDM_PROP_GROUPS).optString(MDM_PROP_PRODSUPP):"";
		return getProductSuppliersConfig(clientGroupID, clientMarket);
	}
	
	// Get product suppliers and corresponding credentials configuration document for a client entity.
	// Client entity can be Client, Client Group (or Tier) or Client Type.
	private static JSONObject getProductSuppliersConfig(String entityId, String market) {

		
		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_PRODSUPPATTACHEDTO.concat(".").concat(MDM_PROP_ENTITYID), entityId);
		filters.put(MDM_PROP_PRODSUPPATTACHEDTO.concat(".").concat(MDM_PROP_COMPANYMKT), market);
		
		String tmpstr  = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_ENABLEPRODSUPPS), filters.toString()), true);

		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	public static Document getLatestUpdatedDocument(FindIterable<Document> resDocs) {
		long latestUpdatedTime = Instant.EPOCH.toEpochMilli();
		Document latestUpdatedDocument = null;
		MongoCursor<Document> resDocsIter = resDocs.iterator();
		while (resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
			Date docUpdatedDate = resDoc.getDate(MDM_PROP_LASTUPDATED);
			if (docUpdatedDate != null && docUpdatedDate.toInstant().toEpochMilli() > latestUpdatedTime) {
				latestUpdatedDocument = resDoc;
				latestUpdatedTime = docUpdatedDate.toInstant().toEpochMilli();
			}
		}
		
		return latestUpdatedDocument;
	}
	
	public static JSONObject getLatestUpdatedJson(JSONObject resJson)
	{
		long latestUpdatedTime = Instant.EPOCH.toEpochMilli();
		JSONObject latestJson = new JSONObject();
		JSONArray dataJsonArr = resJson.optJSONArray("data");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		for(int i=0;dataJsonArr!=null && i<dataJsonArr.length();i++)
		{
			JSONObject dataJson = dataJsonArr.getJSONObject(i);
			Date docUpdatedDate = null;
			try {
				docUpdatedDate = sdf.parse(dataJson.getString(MDM_PROP_LASTUPDATED));
			} catch (ParseException e) {
				// TODO : Log a warning here.
			}
			if (docUpdatedDate != null && docUpdatedDate.toInstant().toEpochMilli() > latestUpdatedTime) {
				latestJson = dataJson;
				latestUpdatedTime = docUpdatedDate.toInstant().toEpochMilli();
			}
		}
		
		return latestJson;
		
	}
	public JSONObject toJSON() {
		JSONObject usrCtxJson = new JSONObject();
		
		usrCtxJson.put(JSON_PROP_USERID, mUserID);
		usrCtxJson.put(JSON_PROP_CLIENTID, mClientID);
		usrCtxJson.put(JSON_PROP_CLIENTTYPE, mClientType.toString());
		usrCtxJson.put(JSON_PROP_CLIENTNAME, mClientName);
		usrCtxJson.put(JSON_PROP_CLIENTCITY, mClientCity);
		usrCtxJson.put(JSON_PROP_CLIENTSTATE, mClientState);
		usrCtxJson.put(JSON_PROP_CLIENTCOUNTRY, mClientCountry);
		usrCtxJson.put(JSON_PROP_IATANO, mClientIATANumber);
		usrCtxJson.put(JSON_PROP_CLIENTCAT, mClientCategory);
		usrCtxJson.put(JSON_PROP_CLIENTSUBCAT, mClientSubCategory);
		usrCtxJson.put(JSON_PROP_ORGHIERARCHY, (mOrgHierarchy != null) ? mOrgHierarchy.toJSON() : new JSONObject());
		JSONArray clHierJsonArr = new JSONArray();
		for (ClientInfo clInfo : mClientHierarchyList) {
			clHierJsonArr.put(clInfo.toJSON());
		}
		usrCtxJson.put(JSON_PROP_CLIENTCOMMENTITYDTLS, clHierJsonArr);
		
		JSONArray prodsJsonArr = new JSONArray();
		for (String prod : mProdList) {
			prodsJsonArr.put(prod);
		}
		usrCtxJson.put(JSON_PROP_PRODS, prodsJsonArr);
		
		JSONArray prodSuppsJsonArr = new JSONArray();
		Iterator<Entry<String,List<ProductSupplier>>> prodSuppsIter = mProdSuppMap.entrySet().iterator();
		while (prodSuppsIter.hasNext()) {
			Entry<String,List<ProductSupplier>> prodSuppsEntry = prodSuppsIter.next();
			JSONObject prodSuppsJson = new JSONObject();
			prodSuppsJson.put(JSON_PROP_PROD, prodSuppsEntry.getKey());
			
			JSONArray suppsJsonArr = new JSONArray();
			List<ProductSupplier> suppsList = prodSuppsEntry.getValue();
			for (ProductSupplier supp : suppsList) {
				suppsJsonArr.put(supp.toJSON());
			}
			prodSuppsJson.put(JSON_PROP_SUPPS, suppsJsonArr);
			prodSuppsJsonArr.put(prodSuppsJson);
		}
		usrCtxJson.put(JSON_PROP_PRODSUPPS, prodSuppsJsonArr);
		
		usrCtxJson.put(JSON_PROP_CLIENTSORTORDER, mClientSortOrder.toString());
		usrCtxJson.put(JSON_PROP_CLIENTSHOWRATEOF, mClientShowRateOf.toString());
		
		return usrCtxJson;
	}
	
	public String toString() {
		return toJSON().toString();
	}
	
	private static boolean hasClientCommercialsDefinition(String entityId, String entityType, String entityMarket) {
		
		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_ENTITYID, entityId);
		filters.put(MDM_PROP_BUDGETMARGINATTACHEDTO.concat(".").concat(MDM_PROP_ENTITYTYPE), entityType);
		filters.put(MDM_PROP_BUDGETMARGINATTACHEDTO.concat(".").concat(MDM_PROP_COMPANYMKT), entityMarket);
	
		String res =  MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_CLIENTCOMMBUDMARGINS), filters.toString()), true);
		return (getLatestUpdatedJson(new JSONObject(res)).length()!=0);
	}

	private static String getClientGroupWithCommercialsDefinition(String clientId, String clientMarket) {
	
		
		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_CLIENTID, clientId);
//		filters.put(MDM_PROP_GROUPS.concat(".").concat(MDM_PROP_COMMERCIALSID), new Document(QueryOperators.EXISTS, true));
		
		
		String res  = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_ASSOCCLIENTTOGROUP), filters.toString()), true);
		JSONArray resJsonArr = new JSONArray();
		JSONArray dataJsonArr =new JSONObject(res).optJSONArray("data");
		for(int i=0;dataJsonArr!=null && i<dataJsonArr.length();i++)
		{	
			JSONObject groups = dataJsonArr.getJSONObject(i).optJSONObject(MDM_PROP_GROUPS);
			if(groups!=null && Utils.isStringNotNullAndNotEmpty(groups.optString(MDM_PROP_COMMERCIALSID)))
				resJsonArr.put(dataJsonArr.getJSONObject(i));
		}
		JSONObject resJson = new JSONObject();
		resJson.put("data", resJsonArr);
		JSONObject latestJson = getLatestUpdatedJson(resJson);
		res  = latestJson.has(MDM_PROP_GROUPS)?latestJson.getJSONObject(MDM_PROP_GROUPS).optString(MDM_PROP_COMMERCIALSID) :"";
		return res;
	}


	
	//TODO:
	private static JSONObject getClientProfile(String clientID)
	{
		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(DBCollection.ID_FIELD_NAME, clientID);
		
		String res  = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_CLIENTB2BS), filters.toString()), true);
		JSONObject latestJson = getLatestUpdatedJson(new JSONObject(res));
		return latestJson;
	}
	
	
	private static JSONObject getClientTypeEntityJson(String clientType, String clientMarket, String clientLanguage, String pointOfSale) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_CLIENTSTRUCT.concat(".").concat(MDM_PROP_CLIENTENTITYTYPE), clientType);
		filters.put(MDM_PROP_CLIENTSTRUCT.concat(".").concat(MDM_PROP_CLIENTMKT), clientMarket);
		filters.put(MDM_PROP_CLIENTSTRUCT.concat(".").concat(MDM_PROP_LANGUAGE), clientLanguage);
		filters.put(MDM_PROP_CLIENTSTRUCT.concat(".").concat(MDM_PROP_POS), pointOfSale);
		
	
		String res  = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_CLIENTTYPES), filters.toString()), true);

		return getLatestUpdatedJson(new JSONObject(res));
	}

	private static String getClientTypeEntity(String clientType, String clientMarket, String clientLanguage, String pointOfSale) {
		JSONObject clientEntityJson = getClientTypeEntityJson(clientType, clientMarket, clientLanguage, pointOfSale);
		
		return clientEntityJson.optString(DBCollection.ID_FIELD_NAME);
	}
	
	private static List<ClientInfo> getClientCommercialsHierarchyList(String clientID, String clientLang, String clientMkt, String clientType, String pointOfSale, List<ClientInfo> clHierList) {
		if (clHierList == null) {
			clHierList = new ArrayList<ClientInfo>();
		}
		
		// If there is parent association for this client, then parent should get added to hierarchy list before 
		// adding child element
		
		JSONObject clientJson =  getClientProfile(clientID);
		
		JSONObject clientProfile = clientJson.has(MDM_PROP_CLIENTPROFILE)? clientJson.getJSONObject(MDM_PROP_CLIENTPROFILE) : null;
		 JSONObject clientDtls = null;
		if(clientProfile!=null)
		   clientDtls = clientProfile.has(MDM_PROP_CLIENTDETAILS) ? clientProfile.getJSONObject(MDM_PROP_CLIENTDETAILS) : null;;
		
		   String clientMarket = "";
		   String parentClientID = "";
		if(clientDtls!=null)
		{
			   clientMarket = clientDtls.optString(MDM_PROP_CLIENTMKT);
			  //String parentClientID = getParentClientID(clientID);
			   parentClientID = clientDtls.optString(MDM_PROP_PARENTASSOC);
		}
		 
		
		if (Utils.isStringNotNullAndNotEmpty(parentClientID)) {
			getClientCommercialsHierarchyList(parentClientID, clientLang, clientMkt, clientType, pointOfSale, clHierList);
		}
		
		ClientInfo clInfo = new ClientInfo();
		clInfo.setClientId(clientID);
		clInfo.setClientMarket(clientMarket);
		clInfo.setParentClientId(parentClientID);
		
		// Determine which entity (Client Specific / Client Group / Client Type) commercials should be applied 
		String clGrpID = null;
		JSONObject clTypeJson = null;
		if (hasClientCommercialsDefinition(clientID, "Client", clientMkt)) {
			clInfo.setCommercialsEntityType(ClientInfo.CommercialsEntityType.ClientSpecific);
			clInfo.setCommercialsEntityMarket(clientMarket);
			clInfo.setCommercialsEntityId(clientID);
		}
		else if (Utils.isStringNotNullAndNotEmpty(clGrpID = getClientGroupWithCommercialsDefinition(clientID, clientMkt))) {
			clInfo.setCommercialsEntityType(ClientInfo.CommercialsEntityType.ClientGroup);
			clInfo.setCommercialsEntityId(clGrpID);
			clInfo.setCommercialsEntityMarket(clientMarket);
		}
		//else if (Utils.isStringNotNullAndNotEmpty(clTypeID = getClientTypeEntity(clientType, clientMkt, clientLang, pointOfSale))) {
		else if ((clTypeJson = getClientTypeEntityJson(clientType, clientMkt, clientLang, pointOfSale)).length() != 0) {
			clInfo.setCommercialsEntityType(ClientInfo.CommercialsEntityType.ClientType);
			//clInfo.setCommercialsEntityId(clTypeID);clTypeJson.get(DBCollection.ID_FIELD_NAME)
			clInfo.setCommercialsEntityId(clTypeJson.optString(DBCollection.ID_FIELD_NAME));
			String entityMarket = clTypeJson.has(MDM_PROP_CLIENTSTRUCT) ? clTypeJson.getJSONObject(MDM_PROP_CLIENTSTRUCT).optString(MDM_PROP_CLIENTMKT) :"";
			clInfo.setCommercialsEntityMarket(entityMarket);
		}
		else {
			// TODO: Log a message here
		}
		
		clHierList.add(clInfo);
		return clHierList;
	}
	
	@SuppressWarnings("unchecked")
	private static String getClientIATANumber(JSONObject clientProfileJson) {
		if (clientProfileJson == null) {
			return "";
		}
		
		JSONArray affInfoJsonArr = clientProfileJson.optJSONArray(MDM_PROP_AFFILIATIONINFO);
		if(affInfoJsonArr==null)
			return "";
		for(int i=0;i<affInfoJsonArr.length();i++)
		{
			JSONObject affInfo = affInfoJsonArr.getJSONObject(i);
			if ( MDM_VAL_IATA.equals(affInfo.getString(MDM_PROP_NAME)) ) {
				return (affInfo.has(MDM_PROP_REGNO)) ? affInfo.getString(MDM_PROP_REGNO) : "";
			}
		}
		
		return "";
	}

	public String getClientIATANUmber() {
		return mClientIATANumber;
	}

	public String getClientName() {
		return mClientName;
	}

	public List<ClientInfo> getClientHierarchy() {
		return mClientHierarchyList;
	}
	
	public OrgHierarchy getOrganizationHierarchy() {
		return mOrgHierarchy;
	}
	
	public String getClientCategory() {
		return mClientCategory;
	}
	
	public String getClientSubCategory() {
		return mClientSubCategory;
	}

	public JSONArray getClientCommercialsHierarchy() {
		JSONArray clCommHierarchyJsonArr = new JSONArray();
		for (int i = 0; i < mClientHierarchyList.size(); i++) {
			ClientInfo clInfo = mClientHierarchyList.get(i);
			JSONObject clCommHierarchyJson = new JSONObject();
			clCommHierarchyJson.put(JSON_PROP_ENTITYTYPE, clInfo.getCommercialsEntityType().toString());
			clCommHierarchyJson.put(JSON_PROP_ENTITYNAME, clInfo.getCommercialsEntityId());
			clCommHierarchyJson.put(JSON_PROP_ENTITYMARKET, clInfo.getCommercialsEntityMarket());
			if (i > 0) {
				clCommHierarchyJson.put(JSON_PROP_PARENTENTITYNAME, mClientHierarchyList.get(i - 1).getCommercialsEntityId());
			}
			clCommHierarchyJsonArr.put(clCommHierarchyJson);
		}
		
		return clCommHierarchyJsonArr;
	}
	
	public ClientInfo[] getClientCommercialsHierarchyArray() {
		return mClientHierarchyList.toArray(new ClientInfo[mClientHierarchyList.size()]);
	}
	
	public String getClientCity() {
		return mClientCity;
	}

	public String getClientCountry() {
		return mClientCountry;
	}

	public String getClientState() {
		return mClientState;
	}
	
	public SortOrder getClientSortOrder() {
		return mClientSortOrder;
	}
	
	public ClientType getClientType() {
		return mClientType;
	}
	
	@SuppressWarnings("unchecked")
	private void loadB2BUserInformation(JSONObject reqHdrJson) throws Exception {
		JSONObject clientContextJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		mUserID = reqHdrJson.optString(JSON_PROP_USERID);
		mClientID = clientContextJson.getString(JSON_PROP_CLIENTID);
		if(mClientID.isEmpty()) {
			throw new Exception("ClientID not found");
		}
		JSONObject clientProfileJson = getClientProfile(mClientID);
		if(clientProfileJson==null || clientProfileJson.length()==0) {
			throw new Exception(String.format("No Document found for clientID %s", mClientID));
		}
		JSONObject clientProfile = clientProfileJson.optJSONObject(MDM_PROP_CLIENTPROFILE);
		JSONObject clientDetailsJson = null;
		if(clientProfile!=null)
			 clientDetailsJson =clientProfile.optJSONObject(MDM_PROP_CLIENTDETAILS);
		JSONObject orgHierarchyJson = null;
		if(clientProfile!=null)
		    orgHierarchyJson = clientProfile.optJSONObject(MDM_PROP_ORGHIERARCHY);
		mOrgHierarchy = (orgHierarchyJson != null) ? new OrgHierarchy(orgHierarchyJson) : null;
		mClientName = (clientDetailsJson!=null)?clientDetailsJson.optString(MDM_PROP_CLIENTNAME):"";
		mClientIATANumber = getClientIATANumber(clientProfileJson);
		mClientCategory =  (clientDetailsJson!=null)?clientDetailsJson.optString(MDM_PROP_CLIENTCAT):"";
		mClientSubCategory = (clientDetailsJson!=null)?clientDetailsJson.optString(MDM_PROP_CLIENTSUBCAT):"";
		JSONObject clientGeneralConfigsJson = MDMUtils.getClientGeneralConfigsv2(mClientID, mClientName);
		//??????how 
		mClientSortOrder = SortOrder.valueOfOrDefault(clientGeneralConfigsJson.optString(MDM_PROP_DFTSORTORDER),SortOrder.Incentives);
		mClientShowRateOf = ShowRateOf.valueOfOrDefault(clientGeneralConfigsJson.optString(MDM_PROP_SHOWRATEOF), ShowRateOf.MultipleSuppliers);
		
		
		
		JSONArray locsList = clientProfileJson.optJSONArray(MDM_PROP_LOCDETAILS);
		if (locsList == null) {
			return;
		}
		for (int z=0;z<locsList.length();z++) {
			JSONObject locJson = locsList.getJSONObject(z);
			JSONObject clientLocJson = MDMUtils.getClientLocationv2(locJson.getString("_id"));
			JSONObject addrDtlsJson = null;
			if(clientLocJson.length()!=0)
				addrDtlsJson = clientLocJson.optJSONObject(MDM_PROP_ADDRDETAILS);  
			if(addrDtlsJson!=null) {
				String addrType = addrDtlsJson.optString(MDM_PROP_ADDRTYPE);
				if (MDM_VAL_ADDRTYPEHQ.equals(addrType)) {
					mClientCity = addrDtlsJson.optString(MDM_PROP_CITY);
					mClientState = addrDtlsJson.optString(MDM_PROP_STATE);
					mClientCountry = addrDtlsJson.optString(MDM_PROP_COUNTRY);
					break;
				}
			}
			
		}
		
	}

	@SuppressWarnings("unchecked")
	private void loadB2CUserInformation(JSONObject reqHdrJson) {
		JSONObject clientContextJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		mUserID = reqHdrJson.optString(JSON_PROP_USERID);
		mClientID = clientContextJson.getString(JSON_PROP_CLIENTID);
		String clientLang = clientContextJson.getString(JSON_PROP_CLIENTLANG);
		String clientMkt = clientContextJson.getString(JSON_PROP_CLIENTMARKET);
		String clientType = clientContextJson.getString(JSON_PROP_CLIENTTYPE);
		String pointOfSale = clientContextJson.getString(JSON_PROP_POS);
		
		JSONObject corpTravellerJson = MDMUtils.getCorpTravellerv2(clientType, mUserID);
		JSONObject clienDetailsJson  = corpTravellerJson.has(MDM_PROP_CLIENTDETAILS) ?corpTravellerJson.optJSONObject(MDM_PROP_CLIENTDETAILS):null;
		
		JSONObject clientTypeJson = getClientTypeEntityJson(clientType, clientMkt, clientLang, pointOfSale);
		JSONObject orgHierarchyJson = null;
		if(clientTypeJson.length()!=0)
		     orgHierarchyJson = clientTypeJson.has(MDM_PROP_ORGHIERARCHY)?clientTypeJson.optJSONObject(MDM_PROP_ORGHIERARCHY):null;
		mOrgHierarchy = (orgHierarchyJson != null) ? new OrgHierarchy(orgHierarchyJson) : null;		
		mClientName = (clienDetailsJson != null) ? clienDetailsJson.optString(MDM_PROP_CLIENTNAME) : "";
		mClientIATANumber = "";
		mClientCategory = (clienDetailsJson != null) ? clienDetailsJson.optString(MDM_PROP_CLIENTCAT) : "";
		mClientSubCategory = "";
		mClientSortOrder = SortOrder.Price;
		mClientShowRateOf = ShowRateOf.MultipleSuppliers;
		
		JSONObject contactDtls = corpTravellerJson.has(MDM_PROP_CONTACTDETAILS)?corpTravellerJson.optJSONObject(MDM_PROP_CONTACTDETAILS):null;
		if(contactDtls!=null)
		{
			JSONArray addressJsonArr = contactDtls.optJSONArray(MDM_PROP_ADDRESSES);
			if (addressJsonArr == null || addressJsonArr.length()==0) {
				return;
			}
			for(int i=0;i<addressJsonArr.length();i++)
			{
				JSONObject addressJson = addressJsonArr.getJSONObject(i);
				if (MDM_VAL_ADDRTYPERES.equals(addressJson.optString(MDM_PROP_ADDRTYPE))) {
					mClientCity =  addressJson.optString(MDM_PROP_CITY);
					mClientState =  addressJson.optString(MDM_PROP_STATE);
					mClientCountry =  addressJson.optString(MDM_PROP_COUNTRY);
					break;
				}
			}
		}
		
	}
}
