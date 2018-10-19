package com.coxandkings.travel.bookingengine.userctx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.LogManager;
import org.bson.Document;
import org.json.*;

import com.coxandkings.travel.bookingengine.config.MDMConfig;
import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.mongo.MongoCommonConfig;
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.enums.OptionToDisplay;
import com.coxandkings.travel.bookingengine.enums.ShowRateOf;
import com.coxandkings.travel.bookingengine.enums.SortOrder;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.mongodb.DBCollection;
import com.mongodb.QueryOperators;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;

public class UserContext implements Constants {

	private static Logger logger = LogManager.getLogger(UserContext.class);
	private String mClientID, mClientName, mClientIATANumber, mClientCategory, mClientSubCategory, mClientCity, mClientState, mClientCountry, mUserID, mClientCompanyID;
	private ClientType mClientType;
	private List<String> mProdList = new ArrayList<String>();
	private Map<String, List<ProductSupplier>> mProdSuppMap = new HashMap<String, List<ProductSupplier>>();
	private List<ClientInfo> mClientHierarchyList;
	private OrgHierarchy mOrgHierarchy;
	private SortOrder mClientSortOrder;
	private ShowRateOf mClientShowRateOf;
	private OptionToDisplay mClientOptionToDisplay;
	
	private UserContext(JSONTokener jsonTokener) throws Exception{
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
		mClientCompanyID=usrCtxJson.optString(JSON_PROP_COMPANYID, "");
		mOrgHierarchy = new OrgHierarchy(usrCtxJson.getJSONObject(JSON_PROP_ORGHIERARCHY));
		
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
				prodSuppsList.add(new ProductSupplier(suppsJson.getJSONObject(j)));
			}
			mProdSuppMap.put(product, prodSuppsList);
		}
		
		mClientOptionToDisplay=OptionToDisplay.valueOfOrDefault(usrCtxJson.optString("optionToDisplay"),OptionToDisplay.All);
		mClientSortOrder = SortOrder.valueOfOrDefault(usrCtxJson.optString(JSON_PROP_CLIENTSORTORDER), SortOrder.Price);
		mClientShowRateOf = ShowRateOf.valueOfOrDefault(usrCtxJson.optString(JSON_PROP_CLIENTSHOWRATEOF), ShowRateOf.MultipleSuppliers);
	}
	
	@SuppressWarnings("unchecked")
	private UserContext(JSONObject reqHdr) throws Exception {
		JSONObject clientContextJson = reqHdr.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String clientID = clientContextJson.getString(JSON_PROP_CLIENTID);
		String clientLang = clientContextJson.getString(JSON_PROP_CLIENTLANG);
		String clientMkt = clientContextJson.getString(JSON_PROP_CLIENTMARKET);
		String clientType = clientContextJson.getString(JSON_PROP_CLIENTTYPE);
		String pointOfSale = clientContextJson.getString(JSON_PROP_POS);
		mUserID = reqHdr.optString(JSON_PROP_USERID);
		
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
		
		//Added for SR implementation
		getClientOptionToDisplay(clientType, mClientName, clientMkt, pointOfSale);
		
		mClientHierarchyList = getClientCommercialsHierarchyList(clientID, clientLang, clientMkt, clientType, pointOfSale, null);		
		
		Document clientEntityProdSuppDoc = getProductSuppliersForClient(clientContextJson, clientContextJson.optString(JSON_PROP_CLIENTID));
		// TODO: Should not have to check the same condition (clientEntityProdSuppDoc == null) multiple times. 
		if (clientEntityProdSuppDoc == null) {
			throw new Exception("No product supplier mapping was found for client entity");
		}
		
		List<Document> prodCatDocs = (List<Document>) clientEntityProdSuppDoc.get(MDM_PROP_PRODCATEGS);
		if (prodCatDocs == null) {
			throw new Exception("No product categories defined in product supplier mapping for client entity");
		}
		
		for (Document prodCatDoc : prodCatDocs) {
			String prodCategory = prodCatDoc.getString(MDM_PROP_PRODCATEG); 
			List<Document> prodSubCatDocs = (List<Document>) prodCatDoc.get(MDM_PROP_PRODCATEGSUBTYPES);
			if (prodSubCatDocs == null || prodSubCatDocs.size() == 0) {
				continue;
			}
			
			for (Document prodSubCatDoc : prodSubCatDocs) {
				String prodSubCategory = prodSubCatDoc.getString(MDM_PROP_SUBTYPE);
				
				String prod = prodCategory.concat("|").concat(prodSubCategory);
				mProdList.add(prod);
				List<Document> mappingDocs = (List<Document>) prodSubCatDoc.get(MDM_PROP_MAPPINGS);
				if (mappingDocs == null || mappingDocs.size() == 0) {
					continue;
				}

				List<ProductSupplier> prodSupps = new ArrayList<ProductSupplier>();
				List<String> dupCheckList = new ArrayList<String>();
				for (Document mappingDoc : mappingDocs) {
					Document suppDoc = (Document) mappingDoc.get(MDM_PROP_SUPP);
					String suppID = suppDoc.getString(MDM_PROP_SUPPID);
//This is commented due to SIT suppliers not being correctly setup. comment out later					
					/*org.bson.Document suppCommDoc =MDMUtils.getSupplier(suppID);
					if(suppCommDoc==null)
						continue;
					
					//Check the to and from Date of Supplier
					if(!isValidSupplier(suppCommDoc))
						continue;
					
					*/
					List<String> creds = (List<String>) suppDoc.get(MDM_PROP_SUPPCREDS);
					if (creds == null || creds.size() == 0) {
						continue;
					}
					
					for (String cred : creds) {
						String dupCheckKey = String.format("%s|%s", suppID, cred);
						if (dupCheckList.contains(dupCheckKey)) {
							continue;
						}
						
						dupCheckList.add(dupCheckKey);
						Document suppCredsDoc = MDMUtils.getSupplierCredentialsConfig(suppID, cred);
						if ( suppCredsDoc == null) {
							logger.warn(String.format("Supplier credentials %s for supplier %s could not be retrieved from MDM", cred, suppID));
							continue;
						}
						
						prodSupps.add(new ProductSupplier(suppCredsDoc));
					}
				}
				mProdSuppMap.put(prod, prodSupps);
			}
		}
	}
	
	private Boolean isValidSupplier(Document suppCommDoc) throws ParseException{
		Document status = (Document) suppCommDoc.get("status");
		Object toDate=status.get("toDate");
		Object formattedtoDate = null;
		if(!(toDate==null)) 
			formattedtoDate=formatDate(toDate,suppCommDoc);
		
		Object fromDate=status.get("fromDate");
		Object formattedfromDate = null;
		if(!(fromDate==null))
		    formattedfromDate=formatDate(fromDate,suppCommDoc);
		
		Date today = DATE_FORMAT.parse(DATE_FORMAT.format(new Date()));
		
		if(formattedtoDate==null && formattedfromDate!=null) {
			return (today.compareTo((Date) formattedfromDate)>=0);
		}else if(formattedtoDate!=null && formattedfromDate!=null) {
			return (today.compareTo((Date) formattedfromDate)>=0 && today.compareTo((Date) formattedtoDate)<=0);
		}else if(formattedtoDate!=null && formattedfromDate==null) {
			return (today.compareTo((Date) formattedtoDate)<=0);
		}else {
			//This case is when toDate and FromDate both are null but supplier is Active
			return true;
		}
	}

	private Object formatDate(Object date, Document suppCommDoc) throws ParseException {
		Calendar cal;
		if (date instanceof String && !((String) date).isEmpty()) {
			return date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").parse((String) date);	
		} else if (date instanceof Document && !((Document) date).isEmpty()) {
			Object obj = new JSONObject(((Document) date).toJson());
			cal = Calendar.getInstance();
			cal.set(((JSONObject) obj).optInt("year"), ((JSONObject) obj).optInt("month") - 1,((JSONObject) obj).optInt("day"));
			return date = cal.getTime();
		}else {
			logger.warn(String.format("date not found for supplier document with id: %s",
					suppCommDoc.getString(DBCollection.ID_FIELD_NAME)));
		}
		return null;
	}

	private UserContext(String userCtxDoc) throws Exception {
		this(new JSONTokener(userCtxDoc));
	}
	
	// TODO: This is only a temporary constructor for testing. Delete when done.
	private UserContext(File usrCtxFile) throws FileNotFoundException , Exception{
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
	
	public JSONArray getSubCatForProductCategory(String prodCategory) {
	    JSONArray subCatArr=new JSONArray();
		Set<String> prodSupps = mProdSuppMap.keySet();
		for (String prodSupp : prodSupps) {
			if (prodSupp.contains(prodCategory)) {
				int idx=prodSupp.indexOf("|");
				subCatArr.put(prodSupp.substring(idx+1));
			}
		}
		
		return subCatArr;
	}
	
	public static UserContext getUserContextForSession(JSONObject reqHdr) {
		try  (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			String sessionID = reqHdr.getString(JSON_PROP_SESSIONID);
			String userID = reqHdr.optString(JSON_PROP_USERID);
			String usrCtxStr = redisConn.get(sessionID);

			UserContext usrCtx = null; 
			if (usrCtxStr != null) { 
				usrCtx = new UserContext(usrCtxStr);
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
				usrCtx = new UserContext(reqHdr);
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
	
	private static Document getProductSuppliersForClient(JSONObject clientContextJson, String clientID) {
		Document clientEntityProdSuppDoc = null;
		String clientLanguage = clientContextJson.getString(JSON_PROP_CLIENTLANG);
		String clientMarket = clientContextJson.getString(JSON_PROP_CLIENTMARKET);
		String clientType = clientContextJson.getString(JSON_PROP_CLIENTTYPE);
		//String company = clientContextJson.getString(JSON_PROP_COMPANY);
		String pointOfSale = clientContextJson.getString(JSON_PROP_POS);
		
		if (Utils.isStringNotNullAndNotEmpty(clientID)) {
			clientEntityProdSuppDoc = getProductSuppliersConfig(clientID, clientMarket);
			if (clientEntityProdSuppDoc != null) {
				return clientEntityProdSuppDoc;
			}
			
			// Check if there is a Client Group associated with this Client ID
			// Reference CKIL_323230 (2.2.3/BR16): As per BRD, when tiers are configured for a
			// master agent, the corresponding client groups should be automatically created.
			// Therefore, following code should handle client groups as well as tiers.
			clientEntityProdSuppDoc = getClientGroupProductSuppliers(clientID, clientMarket);
			if (clientEntityProdSuppDoc != null) {
				return clientEntityProdSuppDoc;
			}
		}
		
		return getClientTypeProductSuppliers(clientType, clientMarket, clientLanguage, pointOfSale);
	}
	
	// Company is unique across groupOfCompanies/groupCompany hierarchy. Therefore, only company parameter should suffice.
	// Parameters for GroupOfCompanies and GroupCompany are not required.
	private static Document getClientTypeProductSuppliers(String clientType, String clientMarket, String clientLanguage, String pointOfSale) {
		String clientTypeEntity = getClientTypeEntity(clientType, clientMarket, clientLanguage, pointOfSale);
		return (clientTypeEntity != null && clientTypeEntity.isEmpty() == false) ? getProductSuppliersConfig(clientTypeEntity, clientMarket) : null;
	}
	
	private static Document getClientGroupProductSuppliers(String clientID, String clientMarket) {
		//MongoCollection<Document> ac2gColl = MDMConfig.getCollection(MDM_COLL_ASSOCCLIENTTOGROUP);
		
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_CLIENTID, clientID);
		filters.put(MDM_PROP_GROUPS.concat(".").concat(MDM_PROP_PRODSUPP), new Document(QueryOperators.EXISTS, true));

		// Reference CKIL_323230 (2.2.2/BR09): For a client, there could be multiple client group mappings.
		// In such case, the mapping with latest timestamp would be used.
		Document latestUpdatedDocument = MDMUtils.getLatestUpdatedDocument(MDMUtils.getDocumentIterator(MDM_COLL_ASSOCCLIENTTOGROUP, filters));
		if (latestUpdatedDocument == null) {
			 return null;
		}
		
		String clientGroupID = ((Document) latestUpdatedDocument.get(MDM_PROP_GROUPS)).getString(MDM_PROP_PRODSUPP);
		return getProductSuppliersConfig(clientGroupID, clientMarket);
	}
	
	// Get product suppliers and corresponding credentials configuration document for a client entity.
	// Client entity can be Client, Client Group (or Tier) or Client Type.
	private static Document getProductSuppliersConfig(String entityId, String market) {
		//MongoCollection<Document> edpsColl = MDMConfig.getCollection(MDM_COLL_ENABLEPRODSUPPS);
		
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_PRODSUPPATTACHEDTO.concat(".").concat(MDM_PROP_ENTITYID), entityId);
		filters.put(MDM_PROP_PRODSUPPATTACHEDTO.concat(".").concat(MDM_PROP_COMPANYMKT), market);

		return MDMUtils.getLatestUpdatedDocument(MDMUtils.getDocumentIterator(MDM_COLL_ENABLEPRODSUPPS, filters));
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
		usrCtxJson.put(JSON_PROP_COMPANYID, mClientCompanyID);
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
		
		usrCtxJson.put("optionToDisplay", mClientOptionToDisplay.toString());
		usrCtxJson.put(JSON_PROP_CLIENTSORTORDER, mClientSortOrder.toString());
		usrCtxJson.put(JSON_PROP_CLIENTSHOWRATEOF, mClientShowRateOf.toString());
		
		return usrCtxJson;
	}
	
	public String toString() {
		return toJSON().toString();
	}
	
	private static boolean hasClientCommercialsDefinition(String entityId, String entityType, String entityMarket) {
		//MongoCollection<Document> ccbmColl = MDMConfig.getCollection(MDM_COLL_CLIENTCOMMBUDMARGINS);
		
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_ENTITYID, entityId);
		filters.put(MDM_PROP_BUDGETMARGINATTACHEDTO.concat(".").concat(MDM_PROP_ENTITYTYPE), entityType);
		filters.put(MDM_PROP_BUDGETMARGINATTACHEDTO.concat(".").concat(MDM_PROP_COMPANYMKT), entityMarket);
		
		return (MDMUtils.getLatestUpdatedDocument(MDMUtils.getDocumentIterator(MDM_COLL_CLIENTCOMMBUDMARGINS, filters))) != null;
	}

	private static String getClientGroupWithCommercialsDefinition(String clientId, String clientMarket) {
		//MongoCollection<Document> ac2gColl = MDMConfig.getCollection(MDM_COLL_ASSOCCLIENTTOGROUP);
		
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_CLIENTID, clientId);
		filters.put(MDM_PROP_GROUPS.concat(".").concat(MDM_PROP_COMMERCIALSID), new Document(QueryOperators.EXISTS, true));
		
		return MDMUtils.getValueAtPathFromDocument(MDMUtils.getLatestUpdatedDocument(MDMUtils.getDocumentIterator(MDM_COLL_ASSOCCLIENTTOGROUP, filters)), MDM_PROP_GROUPS.concat(".").concat(MDM_PROP_COMMERCIALSID));
	}

	private static Document getClientProfile(String clientID,String clientType, String clientMarket, String clientLanguage, String pointOfSale) {
		//MongoCollection<Document> ccbmColl = MDMConfig.getCollection(MDM_COLL_CLIENTB2BS);
		
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(DBCollection.ID_FIELD_NAME, clientID);
		filters.put(MDM_PROP_CLIENTPROFILE.concat(".").concat(MDM_PROP_CLIENTDETAILS).concat(".").concat(MDM_PROP_POS),pointOfSale);
		filters.put(MDM_PROP_CLIENTPROFILE.concat(".").concat(MDM_PROP_CLIENTDETAILS).concat(".").concat(MDM_PROP_CLIENTTYPE),clientType);
		filters.put(MDM_PROP_CLIENTPROFILE.concat(".").concat(MDM_PROP_CLIENTDETAILS).concat(".").concat(MDM_PROP_CLIENTMKT),clientMarket);
		filters.put(MDM_PROP_CLIENTPROFILE.concat(".").concat(MDM_PROP_CLIENTDETAILS).concat(".").concat(MDM_PROP_LANGUAGE),clientLanguage);
		
		return MDMUtils.getLatestUpdatedDocument(MDMUtils.getDocumentIterator(MDM_COLL_CLIENTB2BS, filters));
	}

	private static Document getClientTypeEntityDoc(String clientType, String clientMarket, String clientLanguage, String pointOfSale) {
		//MongoCollection<Document> ctColl = MDMConfig.getCollection(MDM_COLL_CLIENTTYPES);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_CLIENTSTRUCT.concat(".").concat(MDM_PROP_CLIENTENTITYTYPE), clientType);
		filters.put(MDM_PROP_CLIENTSTRUCT.concat(".").concat(MDM_PROP_CLIENTMKT), clientMarket);
		filters.put(MDM_PROP_CLIENTSTRUCT.concat(".").concat(MDM_PROP_LANGUAGE), clientLanguage);
		filters.put(MDM_PROP_CLIENTSTRUCT.concat(".").concat(MDM_PROP_POS), pointOfSale);
		
		return MDMUtils.getLatestUpdatedDocument(MDMUtils.getDocumentIterator(MDM_COLL_CLIENTTYPES, filters));
	}

	private static String getClientTypeEntity(String clientType, String clientMarket, String clientLanguage, String pointOfSale) {
		return MDMUtils.getValueAtPathFromDocument(getClientTypeEntityDoc(clientType, clientMarket, clientLanguage, pointOfSale), DBCollection.ID_FIELD_NAME);
	}
	
	private static List<ClientInfo> getClientCommercialsHierarchyList(String clientID, String clientLang, String clientMkt, String clientType, String pointOfSale, List<ClientInfo> clHierList) {
		if (clHierList == null) {
			clHierList = new ArrayList<ClientInfo>();
		}
		
		// If there is parent association for this client, then parent should get added to hierarchy list before 
		// adding child element
		Document clientDoc = getClientProfile(clientID,clientType,clientMkt,clientLang,pointOfSale);
		String clientMarket = MDMUtils.getValueAtPathFromDocument(clientDoc, MDM_PROP_CLIENTPROFILE.concat(".").concat(MDM_PROP_CLIENTDETAILS).concat(".").concat(MDM_PROP_CLIENTMKT));
		//String parentClientID = getParentClientID(clientID);
		String parentClientID = MDMUtils.getValueAtPathFromDocument(clientDoc, MDM_PROP_CLIENTPROFILE.concat(".").concat(MDM_PROP_CLIENTDETAILS).concat(".").concat(MDM_PROP_PARENTASSOC));
		
		if (Utils.isStringNotNullAndNotEmpty(parentClientID)) {
			getClientCommercialsHierarchyList(parentClientID, clientLang, clientMkt, clientType, pointOfSale, clHierList);
		}
		
		ClientInfo clInfo = new ClientInfo();
		clInfo.setClientId(clientID);
		clInfo.setClientMarket(clientMarket);
		clInfo.setParentClientId(parentClientID);
		
		// Determine which entity (Client Specific / Client Group / Client Type) commercials should be applied 
		String clGrpID = null;
		Document clTypeDoc = null;
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
		else if ((clTypeDoc = getClientTypeEntityDoc(clientType, clientMkt, clientLang, pointOfSale)) != null) {
			clInfo.setCommercialsEntityType(ClientInfo.CommercialsEntityType.ClientType);
			//clInfo.setCommercialsEntityId(clTypeID);
			clInfo.setCommercialsEntityId(MDMUtils.getValueAtPathFromDocument(clTypeDoc, DBCollection.ID_FIELD_NAME));
			clInfo.setCommercialsEntityMarket(MDMUtils.getValueAtPathFromDocument(clTypeDoc, MDM_PROP_CLIENTSTRUCT.concat(".").concat(MDM_PROP_CLIENTMKT)));
		}
		else {
			// TODO: Log a message here
		}
		
		clHierList.add(clInfo);
		return clHierList;
	}
	
	@SuppressWarnings("unchecked")
	private static String getClientIATANumber(Document clientProfileDoc) {
		if (clientProfileDoc == null) {
			return "";
		}
		
		List<Document> affInfoList = (List<Document>) clientProfileDoc.get(MDM_PROP_AFFILIATIONINFO);
		if (affInfoList == null || affInfoList.size() == 0) {
			return "";
		}
		
		for (Document affInfo : affInfoList) {
			if ( MDM_VAL_IATA.equals(affInfo.getString(MDM_PROP_NAME)) ) {
				return (affInfo.containsKey(MDM_PROP_REGNO)) ? affInfo.getString(MDM_PROP_REGNO) : "";
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
	
	public String getClientCompanyId() {
		return mClientCompanyID;
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
	
	//Added for SR implementation
	public OptionToDisplay getClientOptionToDisplay() {
		return mClientOptionToDisplay;
	}
		
	public ClientType getClientType() {
		return mClientType;
	}
	
	@SuppressWarnings("unchecked")
	private void loadB2BUserInformation(JSONObject reqHdrJson) throws Exception {
		JSONObject clientContextJson = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		mUserID = reqHdrJson.optString(JSON_PROP_USERID);
		mClientID = clientContextJson.getString(JSON_PROP_CLIENTID);
		String clientLang = clientContextJson.getString(JSON_PROP_CLIENTLANG);
		String clientMkt = clientContextJson.getString(JSON_PROP_CLIENTMARKET);
		String clientType = clientContextJson.getString(JSON_PROP_CLIENTTYPE);
		String pointOfSale = clientContextJson.getString(JSON_PROP_POS);
		
		Document clientProfileDoc = getClientProfile(mClientID,clientType,clientMkt,clientLang,pointOfSale);
		if(clientProfileDoc==null || clientProfileDoc.isEmpty()) {
			throw new Exception(String.format("No B2B client found for clientID %s", mClientID));
		}
		Document clienDetailsDoc = (Document) MDMUtils.getValueObjectAtPathFromDocument(clientProfileDoc, MDM_PROP_CLIENTPROFILE.concat(".").concat(MDM_PROP_CLIENTDETAILS));
		Document orgHierarchyDoc =(Document) MDMUtils.getValueObjectAtPathFromDocument(clientProfileDoc, MDM_PROP_CLIENTPROFILE.concat(".").concat(MDM_PROP_ORGHIERARCHY)); 
		mOrgHierarchy = (orgHierarchyDoc != null) ? new OrgHierarchy(orgHierarchyDoc) : null;
		mClientName = MDMUtils.getValueAtPathFromDocument(clienDetailsDoc, MDM_PROP_CLIENTNAME);
		mClientCompanyID=MDMUtils.getValueAtPathFromDocument(orgHierarchyDoc, MDM_PROP_COMPANYID);
		mClientIATANumber = getClientIATANumber(clientProfileDoc);
		mClientCategory = MDMUtils.getValueAtPathFromDocument(clienDetailsDoc, MDM_PROP_CLIENTCAT);
		mClientSubCategory = MDMUtils.getValueAtPathFromDocument(clienDetailsDoc, MDM_PROP_CLIENTSUBCAT);
		Document clientGeneralConfigsDoc = MDMUtils.getClientGeneralConfigs(mClientID, mClientName);
		mClientSortOrder = SortOrder.valueOfOrDefault(MDMUtils.getValueAtPathFromDocument(clientGeneralConfigsDoc, MDM_PROP_DFTSORTORDER), SortOrder.Incentives);
		mClientShowRateOf = ShowRateOf.valueOfOrDefault(MDMUtils.getValueAtPathFromDocument(clientGeneralConfigsDoc, MDM_PROP_SHOWRATEOF), ShowRateOf.MultipleSuppliers);
		
		List<Document> locsList = (List<Document>) MDMUtils.getValueObjectAtPathFromDocument(clientProfileDoc, MDM_PROP_LOCDETAILS);
		if (locsList == null) {
			return;
		}
		
		for (Document clientLocDoc : locsList) {
			//Document clientLocDoc = MDMUtils.getClientLocation(loc.getString(DBCollection.ID_FIELD_NAME));
			Document addrDtlsDoc = (Document) MDMUtils.getValueObjectAtPathFromDocument(clientLocDoc, MDM_PROP_ADDRDETAILS);  
			String addrType = MDMUtils.getValueAtPathFromDocument(addrDtlsDoc, MDM_PROP_ADDRTYPE);
			if (MDM_VAL_ADDRTYPEHQ.equals(addrType)) {
				mClientCity = MDMUtils.getValueAtPathFromDocument(addrDtlsDoc, MDM_PROP_CITY);
				mClientState = MDMUtils.getValueAtPathFromDocument(addrDtlsDoc, MDM_PROP_STATE);
				mClientCountry = MDMUtils.getValueAtPathFromDocument(addrDtlsDoc, MDM_PROP_COUNTRY);
				break;
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
		
		Document corpTravellerDoc = MDMUtils.getCorpTraveller(clientType, mUserID);
		Document clienDetailsDoc = (Document) MDMUtils.getValueObjectAtPathFromDocument(corpTravellerDoc, MDM_PROP_CLIENTDETAILS);  
		Document clientTypeDoc = getClientTypeEntityDoc(clientType, clientMkt, clientLang, pointOfSale);
		if(clientTypeDoc==null || clientTypeDoc.isEmpty()) {
			logger.warn(String.format("No B2C client found for clientMkt=%s, clientLanguage=%s and pointOfSale=%s", clientMkt,clientLang,pointOfSale));
		}
		Document orgHierarchyDoc = (Document) MDMUtils.getValueObjectAtPathFromDocument(clientTypeDoc, MDM_PROP_ORGHIERARCHY);
		mOrgHierarchy = (orgHierarchyDoc != null) ? new OrgHierarchy(orgHierarchyDoc) : null;		
		mClientName = (clienDetailsDoc != null) ? MDMUtils.getValueAtPathFromDocument(clienDetailsDoc, MDM_PROP_CLIENTNAME) : "";
		mClientIATANumber = "";
		mClientCompanyID= MDMUtils.getValueAtPathFromDocument(orgHierarchyDoc, MDM_PROP_COMPANY);
		mClientCategory = (clienDetailsDoc != null) ? MDMUtils.getValueAtPathFromDocument(clienDetailsDoc, MDM_PROP_CLIENTCAT) : "";
		mClientSubCategory = "";
		mClientSortOrder = SortOrder.Price;
		mClientShowRateOf = ShowRateOf.MultipleSuppliers;
		
		if (corpTravellerDoc == null) {
			return;
		}
		
		List<Document> addressDocs = (List<Document>) MDMUtils.getValueObjectAtPathFromDocument(corpTravellerDoc, MDM_PROP_CONTACTDETAILS.concat(".").concat(MDM_PROP_ADDRESSES));
		if (addressDocs == null) {
			return;
		}
		
		for (Document addressDoc : addressDocs) {
			if (MDM_VAL_ADDRTYPERES.equals(addressDoc.getString(MDM_PROP_ADDRTYPE))) {
				mClientCity = (String) addressDoc.getOrDefault(MDM_PROP_CITY, "");
				mClientState = (String) addressDoc.getOrDefault(MDM_PROP_STATE, "");
				mClientCountry = (String) addressDoc.getOrDefault(MDM_PROP_COUNTRY, "");
				break;
			}
		}
	}
	
	//Added for SR implementation
    @SuppressWarnings({ "unchecked", "unlikely-arg-type" })
	private void getClientOptionToDisplay(String entityType, String entityName,String companyMrkt,String pointOfSale){
    	Document clientGeneralConfigsDoc=MDMUtils.getClientGeneralConfigs(entityType, entityName, companyMrkt, pointOfSale);
		List<Document> optionToDisplay= (List<Document>) MDMUtils.getValueObjectAtPathFromDocument(clientGeneralConfigsDoc, "optionsToDisplay");
		if(optionToDisplay==null)
			mClientOptionToDisplay=OptionToDisplay.All;
		else
			mClientOptionToDisplay=optionToDisplay.contains("LHR")?OptionToDisplay.LHR:OptionToDisplay.All;  
		
		//mClientOptionToDisplay=optionToDisplay==null?OptionToDisplay.All:optionToDisplay.contains("LHR")?OptionToDisplay.LHR:OptionToDisplay.All;
    }
 }
