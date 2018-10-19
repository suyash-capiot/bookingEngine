package com.coxandkings.travel.bookingengine.userctx;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.HttpTemplate;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.config.MDMConfigV2;
import com.coxandkings.travel.bookingengine.config.MDMConfig;
import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.orchestrator.login.LoginConstants;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.mongodb.DBCollection;
import com.mongodb.QueryOperators;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import redis.clients.jedis.Jedis;

public class MDMUtils implements Constants, LoginConstants {

	private static final Logger logger = LogManager.getLogger(MDMUtils.class);

	static Document getLatestUpdatedDocument(Iterator<Document> resDocsIter) {
		long latestUpdatedTime = Instant.EPOCH.toEpochMilli();
		Document latestUpdatedDocument = null;
		// MongoCursor<Document> resDocsIter = resDocs.iterator();
		while (resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
			Object docUpdatedDate = resDoc.get(MDM_PROP_LASTUPDATED);
			if (docUpdatedDate instanceof String) {
				try {
					docUpdatedDate = new SimpleDateFormat("yyyy-mm-dd").parse((String) docUpdatedDate);
				} catch (ParseException e) {
					docUpdatedDate = null;
				}
			}
			// If the document retrieved from mongoDB does not have any last updated
			// dateTime, the
			// following assignment will ensure that if multiple documents have been
			// returned, the
			// first one will be selected.
			if (docUpdatedDate == null || !(docUpdatedDate instanceof Date)) {
				docUpdatedDate = new Date();
			}
			if (docUpdatedDate != null && ((Date) docUpdatedDate).toInstant().toEpochMilli() > latestUpdatedTime) {
				latestUpdatedDocument = resDoc;
				latestUpdatedTime = ((Date) docUpdatedDate).toInstant().toEpochMilli();
			}
		}

		if (resDocsIter instanceof MongoCursor<?>)
			((MongoCursor<?>) resDocsIter).close();

		return latestUpdatedDocument;
	}

	@Deprecated
	public static JSONObject getLatestUpdatedJson(JSONObject resJson) {
		long latestUpdatedTime = Instant.EPOCH.toEpochMilli();
		JSONObject latestJson = new JSONObject();
		JSONArray dataJsonArr = resJson.optJSONArray("data");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		for(int i=0;dataJsonArr!=null && i<dataJsonArr.length();i++)
		{
			if(dataJsonArr.getJSONObject(i)!=null)
			{
				JSONObject dataJson = dataJsonArr.getJSONObject(i);
				Date docUpdatedDate = null;
				try {
					docUpdatedDate = sdf.parse(dataJson.getString(MDM_PROP_LASTUPDATED));
				} catch (ParseException e) {
					// TODO : Log a warning here.
				}
				if (docUpdatedDate == null) {
					docUpdatedDate = new Date();
				}
				if (docUpdatedDate != null && docUpdatedDate.toInstant().toEpochMilli() > latestUpdatedTime) {
					latestJson = dataJson;
					latestUpdatedTime = docUpdatedDate.toInstant().toEpochMilli();
				}
			}

		}

		return latestJson;

	}

	public static Document getSupplierCommercials(String prodCateg, String prodCategSubType, String suppID) {
		// MongoCollection<Document> ctColl =
		// MDMConfig.getCollection(MDM_COLL_SUPPCOMMAINCASES);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_COMMERCIALDEFN).concat(".")
				.concat(MDM_PROP_PRODCATEG), prodCateg);
		filters.put(MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_COMMERCIALDEFN).concat(".")
				.concat(MDM_PROP_PRODCATEGSUBTYPE), prodCategSubType);
		filters.put(
				MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_COMMERCIALDEFN).concat(".").concat(MDM_PROP_SUPPID),
				suppID);
		
		//This part of code is for updated mdm api for supplierCommercials
		Iterator<Document> resDocsIter=getDocumentIterator(MDM_COLL_SUPPCOMMAINCASES, filters);
		long latestUpdatedTime = Instant.EPOCH.toEpochMilli();
		Document latestUpdatedDocument = null;
		while(resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
			String commercialHeadName = resDoc.get(MDM_PROP_COMMTYPE).toString();
					if(commercialHeadName.equalsIgnoreCase(MDM_VAL_STDCOMM)) {
						
						Object docUpdatedDate = resDoc.get(MDM_PROP_LASTUPDATED);
						if (docUpdatedDate instanceof String) {
							try {
								docUpdatedDate = new SimpleDateFormat("yyyy-mm-dd").parse((String) docUpdatedDate);
							} catch (ParseException e) {
								docUpdatedDate = null;
							}
						}
						if (docUpdatedDate == null || !(docUpdatedDate instanceof Date)) {
							docUpdatedDate = new Date();
						}
						if (docUpdatedDate != null && ((Date) docUpdatedDate).toInstant().toEpochMilli() > latestUpdatedTime) {
							latestUpdatedDocument = resDoc;
							latestUpdatedTime = ((Date) docUpdatedDate).toInstant().toEpochMilli();
						}
						
					}
					
		}
		

		if (resDocsIter instanceof MongoCursor<?>)
			((MongoCursor<?>) resDocsIter).close();
		
		return latestUpdatedDocument;
		
		
		//This part of code is for old mdm api for supplierCommercials
		//return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_SUPPCOMMAINCASES, filters));
	}
	
	
	
	public static Document getSupplierCommercialsFromIATACode(String prodCateg, String prodCategSubType, String iataCode,String companyId) {
		// MongoCollection<Document> ctColl =
		// MDMConfig.getCollection(MDM_COLL_SUPPCOMMAINCASES);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_COMPANYID, companyId);
		filters.put(MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_COMMERCIALDEFN).concat(".")
				.concat(MDM_PROP_PRODCATEG), prodCateg);
		filters.put(MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_COMMERCIALDEFN).concat(".")
				.concat(MDM_PROP_PRODCATEGSUBTYPE), prodCategSubType);
		filters.put(
				MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_STANDARDCOMM).concat(".").concat(MDM_PROP_PROD).concat(".").concat(MDM_PROP_TRANSPORTATION)
				.concat(".").concat(MDM_PROP_PROD).concat(".").concat(MDM_PROP_FLIGHT).concat(".").concat(MDM_PROP_IATANUMBERS),
				iataCode);
		
		//This part of code is for updated mdm api for supplierCommercials
		Iterator<Document> resDocsIter=getDocumentIterator(MDM_COLL_SUPPCOMMAINCASESIATA, filters);
		long latestUpdatedTime = Instant.EPOCH.toEpochMilli();
		Document latestUpdatedDocument = null;
		while(resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
			String commercialHeadName = resDoc.get(MDM_PROP_COMMTYPE).toString();
					if(commercialHeadName.equalsIgnoreCase(MDM_VAL_STDCOMM)) {
						
						Object docUpdatedDate = resDoc.get(MDM_PROP_LASTUPDATED);
						if (docUpdatedDate instanceof String) {
							try {
								docUpdatedDate = new SimpleDateFormat("yyyy-mm-dd").parse((String) docUpdatedDate);
							} catch (ParseException e) {
								docUpdatedDate = null;
							}
						}
						if (docUpdatedDate == null || !(docUpdatedDate instanceof Date)) {
							docUpdatedDate = new Date();
						}
						if (docUpdatedDate != null && ((Date) docUpdatedDate).toInstant().toEpochMilli() > latestUpdatedTime) {
							latestUpdatedDocument = resDoc;
							latestUpdatedTime = ((Date) docUpdatedDate).toInstant().toEpochMilli();
						}
						
					}
					
		}
		

		if (resDocsIter instanceof MongoCursor<?>)
			((MongoCursor<?>) resDocsIter).close();
		
		return latestUpdatedDocument;
		
		
		//This part of code is for old mdm api for supplierCommercials
		//return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_SUPPCOMMAINCASES, filters));
	}

	@Deprecated
	public static JSONObject getSupplierCommercialsv2(String prodCateg, String prodCategSubType, String suppID) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_COMMERCIALDEFN).concat(".")
				.concat(MDM_PROP_PRODCATEG), prodCateg);
		filters.put(MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_COMMERCIALDEFN).concat(".")
				.concat(MDM_PROP_PRODCATEGSUBTYPE), prodCategSubType);
		filters.put(
				MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_COMMERCIALDEFN).concat(".").concat(MDM_PROP_SUPPID),
				suppID);
		String tmpstr = MDMUtils
				.getData(String.format(MDMConfigV2.getURL(MDM_COLL_SUPPCOMMAINCASES), filters.toString()), true);

		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	public static Document getSupplierCredentialsConfig(String suppID, String cred) {
		// MongoCollection<Document> scColl =
		// MDMConfig.getCollection(MDM_COLL_SUPPCREDS);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(DBCollection.ID_FIELD_NAME, cred);
		filters.put(MDM_PROP_SUPP.concat(".").concat(MDM_PROP_SUPPID), suppID);

		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_SUPPCREDS, filters));
	}

	@Deprecated
	public static JSONObject getSupplierCredentialsConfigv2(String suppID, String cred) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(DBCollection.ID_FIELD_NAME, cred);
		filters.put(MDM_PROP_SUPP.concat(".").concat(MDM_PROP_SUPPID), suppID);

		String tmpstr = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_SUPPCREDS), filters.toString()),
				true);

		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	// As of now ProductCategory and ProductCategorySubType are not used because MDM
	// collection does not have
	// these attributes. However, as per CKIL, supplier settlement terms should be
	// defined at product category/
	// product category subtype and supplierId level.
	static Document getSupplierSettlementTerms(String prodCateg, String prodCategSubType, String suppID) {
		// MongoCollection<Document> scColl =
		// MDMConfig.getCollection(MDM_COLL_SUPPSETTLES);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_SUPPID, suppID);

		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_SUPPSETTLES, filters));
	}

	@Deprecated
	static JSONObject getSupplierSettlementTermsv2(String prodCateg, String prodCategSubType, String suppID) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_SUPPID, suppID);
		String tmpstr = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_SUPPSETTLES), filters.toString()),
				true);

		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	public static String getValueAtPathFromDocument(Document doc, String path) {
		if (doc == null || path == null || path.isEmpty()) {
			return "";
		}

		int dotIdx = path.indexOf('.');
		return (dotIdx == -1) ? ((doc.containsKey(path)) ? doc.getString(path) : "")
				: getValueAtPathFromDocument((Document) doc.get(path.substring(0, dotIdx)), path.substring(dotIdx + 1));
	}

	public static Object getValueObjectAtPathFromDocument(Document doc, String path) {
		if (doc == null || path == null || path.isEmpty()) {
			return null;
		}

		int dotIdx = path.indexOf('.');
		return (dotIdx == -1) ? ((doc.containsKey(path)) ? doc.get(path) : null)
				: getValueObjectAtPathFromDocument((Document) doc.get(path.substring(0, dotIdx)),
						path.substring(dotIdx + 1));
	}

	public static Object optValueObjectAtPathFromDocument(Document doc, String path, Object optVal) {
		Object val = getValueObjectAtPathFromDocument(doc, path);
		return val == null ? optVal : val;
	}

	@SuppressWarnings("unchecked")
	public static Document getStandardCommercialSettlementTerms(Document suppSettlementDoc, boolean isCommissionable) {
		if (suppSettlementDoc == null) {
			return null;
		}

		Document configDoc = (Document) ((isCommissionable) ? suppSettlementDoc.get(MDM_PROP_COMMISSIONCOMMS)
				: suppSettlementDoc.get(MDM_PROP_NONCOMMISSIONCOMMS));
		if (configDoc == null) {
			return null;
		}

		List<Document> commHeadDocs = (List<Document>) configDoc.get(MDM_PROP_COMMERCIALHEADS);
		if (commHeadDocs == null) {
			return null;
		}

		for (Document commHeadDoc : commHeadDocs) {
			String commHead = (String) commHeadDoc.getOrDefault(MDM_PROP_COMMERCIALHEAD, "");
			/*if ((isCommissionable) ? MDM_VAL_STDCOMMCOMMISSION.equals(commHead)
					: MDM_VAL_STDCOMMNONCOMMISSION.equals(commHead)) {
				return commHeadDoc;
			}*/
			if ( MDM_VAL_STDCOMMRECEIVABLE.equals(commHead))
					 {
				return commHeadDoc;
			}
			
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	@Deprecated
	public static JSONObject getStandardCommercialSettlementTermsv2(JSONObject suppSettlementJson,
			boolean isCommissionable) {
		if (suppSettlementJson == null) {
			return null;
		}

		JSONObject configJson = ((isCommissionable) ? suppSettlementJson.optJSONObject(MDM_PROP_COMMISSIONCOMMS)
				: suppSettlementJson.optJSONObject(MDM_PROP_NONCOMMISSIONCOMMS));
		if (configJson == null) {
			return null;
		}

		JSONArray commHeadJsonArr = configJson.optJSONArray(MDM_PROP_COMMERCIALHEADS);
		if (commHeadJsonArr == null || commHeadJsonArr.length() == 0) {
			return null;
		}
		for (int i = 0; i < commHeadJsonArr.length(); i++) {
			JSONObject commHeadJson = commHeadJsonArr.getJSONObject(i);
			String commHead = commHeadJson.optString(MDM_PROP_COMMERCIALHEAD, "");
			if ((isCommissionable) ? MDM_VAL_STDCOMMCOMMISSION.equals(commHead)
					: MDM_VAL_STDCOMMNONCOMMISSION.equals(commHead)) {
				return commHeadJson;
			}
		}

		return null;
	}

	public static Document getSupplier(String suppID) {
		// MongoCollection<Document> suppColl = MDMConfig.getCollection(MDM_COLL_SUPPS);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		//filters.put(MDM_PROP_STATUS.concat(".").concat(MDM_PROP_STATUS), "Active");
		filters.put(DBCollection.ID_FIELD_NAME, suppID);

		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_SUPPS, filters));
	}

	@Deprecated
	public static JSONObject getSupplierv2(String suppID) {
		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(DBCollection.ID_FIELD_NAME, suppID);
		String tmpstr = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_SUPPS), filters.toString()), true);
		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	public static Document getCorpTraveller(String clientType, String userID) {
		// MongoCollection<Document> corpTrvlrsColl =
		// MDMConfig.getCollection(MDM_COLL_CORPTRAVELLERS);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_CLIENTDETAILS.concat(".").concat(MDM_PROP_CLIENTTYPE), clientType);
		filters.put(MDM_PROP_TRAVELLERDETAILS.concat(".").concat(MDM_PROP_USERID), userID);

		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_CORPTRAVELLERS, filters));
	}

	@Deprecated
	public static JSONObject getCorpTravellerv2(String clientType, String userID) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_CLIENTDETAILS.concat(".").concat(MDM_PROP_CLIENTTYPE), clientType);
		filters.put(MDM_PROP_TRAVELLERDETAILS.concat(".").concat(MDM_PROP_USERID), "");

		String tmpstr = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_CORPTRAVELLERS), filters.toString()),
				true);

		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	public static Document getClientLocation(String clientLocID) {
		// MongoCollection<Document> clientLocsColl =
		// MDMConfig.getCollection(MDM_COLL_CORPTRAVELLERS);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(DBCollection.ID_FIELD_NAME, clientLocID);

		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_CORPTRAVELLERS, filters));
	}

	@Deprecated
	public static JSONObject getClientLocationv2(String clientLocID) {

		// Map<String, Object> filters = new HashMap<String, Object>();
		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(DBCollection.ID_FIELD_NAME, clientLocID);

		String tmpstr = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_CORPTRAVELLERS), filters.toString()),
				true);

		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	public static Document getClientGeneralConfigs(String clientID, String clientName) {
		return getGeneralConfigs(MDM_VAL_CLIENT, clientName);
	}

	@Deprecated
	public static JSONObject getClientGeneralConfigsv2(String clientID, String clientName) {
		return getGeneralConfigsv2(MDM_VAL_CLIENT, clientName);
	}

	private static Document getGeneralConfigs(String entityType, String entityName) {
		// MongoCollection<Document> genConfigsColl =
		// MDMConfig.getCollection(MDM_COLL_GENERALCONFIGS);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_ENTITYTYPE, entityType);
		filters.put(MDM_PROP_ENTITYNAME, entityName);

		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_GENERALCONFIGS, filters));
	}

	//////////////SR ///////////////////////
	public static Document getClientGeneralConfigs(String clientType, String clientName,String companyMrkt,String pointOfSale) {
		return CLIENT_TYPE_B2B.equals(clientType)?getGeneralConfigs(MDM_VAL_CLIENT,clientName,companyMrkt,pointOfSale):getGeneralConfigs(MDM_VAL_CLIENTTYPE_ENTITY,clientName,companyMrkt,pointOfSale);
	}
	
	//Added for LHR option to be sent in SR request
	@SuppressWarnings("unused")
	private static Document getGeneralConfigs(String entityType, String entityName,String companyMrkt,String pointOfSale) {

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_ENTITYTYPE, entityType);
		filters.put(MDM_PROP_ENTITYNAME, entityName);
		filters.put(MDM_PROP_COMPANYMKT, companyMrkt);
		filters.put(MDM_PROP_POS, pointOfSale);

		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_GENERALCONFIGS, filters));
	}
	
	@Deprecated
	private static JSONObject getGeneralConfigsv2(String entityType, String entityName) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_ENTITYTYPE, entityType);
		filters.put(MDM_PROP_ENTITYNAME, entityName);

		String tmpstr = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_GENERALCONFIGS), filters.toString()),
				true);

		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	static List<Document> getSupplierCredentialsForGDSQueueOperations(String suppID) {
		// MongoCollection<Document> scColl =
		// MDMConfig.getCollection(MDM_COLL_SUPPCREDS);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_SUPP.concat(".").concat(MDM_PROP_SUPPID), suppID);
		filters.put(MDM_PROP_CREDS.concat(".").concat(MDM_PROP_CATEG), MDM_VAL_ONLINE);
		filters.put(MDM_PROP_CREDS.concat(".").concat(MDM_PROP_TYPE), MDM_VAL_LIVE);
		filters.put(MDM_PROP_OWNERSHIP.concat(".").concat(MDM_PROP_OWNERSHIPWITH), MDM_VAL_COMPANY);

		List<Document> suppCredsDoc = new ArrayList<Document>();
		// FindIterable<Document> resDocs = scColl.find(new Document(filters));
		Iterator<Document> resDocsIter = getDocumentIterator(MDM_COLL_SUPPCREDS, filters);
		while (resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
			suppCredsDoc.add(resDoc);
		}

		if (resDocsIter instanceof MongoCursor<?>)
			((MongoCursor<?>) resDocsIter).close();

		return suppCredsDoc;
	}

	@Deprecated
	static JSONArray getSupplierCredentialsForGDSQueueOperationsv2(String suppID) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_SUPP.concat(".").concat(MDM_PROP_SUPPID), suppID);
		filters.put(MDM_PROP_CREDS.concat(".").concat(MDM_PROP_CATEG), MDM_VAL_ONLINE);
		filters.put(MDM_PROP_CREDS.concat(".").concat(MDM_PROP_TYPE), MDM_VAL_LIVE);
		filters.put(MDM_PROP_OWNERSHIP.concat(".").concat(MDM_PROP_OWNERSHIPWITH), MDM_VAL_COMPANY);
		String tmpstr = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_SUPPCREDS), filters.toString()),
				true);

		return new JSONArray(tmpstr);
	}

	static List<Document> getSupplierCredentialsForPseudoCityCode(String suppID, String pcc) {
		// MongoCollection<Document> scColl =
		// MDMConfig.getCollection(MDM_COLL_SUPPCREDS);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_SUPP.concat(".").concat(MDM_PROP_SUPPID), suppID);
		filters.put(MDM_PROP_CREDDEATILS.concat(".").concat(MDM_PROP_CREDS).concat(".").concat(MDM_PROP_TITLE),
				MDM_VAL_PCC);
		filters.put(MDM_PROP_CREDDEATILS.concat(".").concat(MDM_PROP_CREDS).concat(".").concat(MDM_PROP_VALUE), pcc);

		List<Document> suppCredsDoc = new ArrayList<Document>();
		// FindIterable<Document> resDocs = scColl.find(new Document(filters));
		Iterator<Document> resDocsIter = getDocumentIterator(MDM_COLL_SUPPCREDS, filters);
		while (resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
			suppCredsDoc.add(resDoc);
		}

		if (resDocsIter instanceof MongoCursor<?>)
			((MongoCursor<?>) resDocsIter).close();

		return suppCredsDoc;
	}

	@Deprecated
	static JSONArray getSupplierCredentialsForPseudoCityCodev2(String suppID, String pcc) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_SUPP.concat(".").concat(MDM_PROP_SUPPID), suppID);
		filters.put(MDM_PROP_CREDDEATILS.concat(".").concat(MDM_PROP_CREDS).concat(".").concat(MDM_PROP_TITLE),
				MDM_VAL_PCC);
		filters.put(MDM_PROP_CREDDEATILS.concat(".").concat(MDM_PROP_CREDS).concat(".").concat(MDM_PROP_VALUE), pcc);
		String tmpstr = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_SUPPCREDS), filters.toString()),
				true);

		return new JSONArray(tmpstr);
	}

	public static Document getSIPaymentDetailTemplate(String suppID, String settType) {
		// MongoCollection<Document> suppSettToSIPayColl =
		// MDMConfig.getCollection(MDM_COLL_SUPPSETTLE_TO_SIPAY);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put("_id", suppID.concat("|").concat(settType));
		//filters.put(MDM_PROP_SUPPID, suppID);
		filters.put(MDM_PROP_SETTLETYPE, settType);

		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_SUPPSETTLE_TO_SIPAY, filters));
	}

	@Deprecated
	public static JSONObject getSIPaymentDetailTemplatev2(String suppID, String settType) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_SUPPID, suppID);
		filters.put(MDM_PROP_SETTLETYPE, settType);
		String tmpstr = MDMUtils
				.getData(String.format(MDMConfigV2.getURL(MDM_COLL_SUPPSETTLE_TO_SIPAY), filters.toString()), true);

		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	public static Document getSupplierByName(String suppName) {
		// MongoCollection<Document> suppColl = MDMConfig.getCollection(MDM_COLL_SUPPS);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_STATUS.concat(".").concat(MDM_PROP_STATUS), "Active");
		filters.put(MDM_PROP_SUPP.concat(".").concat(MDM_PROP_NAME), suppName);

		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_SUPPS, filters));
	}

	@Deprecated
	public static JSONObject getSupplierByNamev2(String suppName) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_SUPP.concat(".").concat(MDM_PROP_NAME), suppName);
		String tmpstr = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_SUPPS), filters.toString()), true);

		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	public static List<Document> getPccHAPCredentials(String usageType) {
		// MongoCollection<Document> pccHAPColl =
		// MDMConfig.getCollection(MDM_COLL_PCCHAP);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_USAGE, usageType);

		List<Document> suppCredsDoc = new ArrayList<Document>();
		// FindIterable<Document> resDocs = pccHAPColl.find(new Document(filters));
		Iterator<Document> resDocsIter = getDocumentIterator(MDM_COLL_PCCHAP, filters);
		while (resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
			suppCredsDoc.add(resDoc);
		}

		if (resDocsIter instanceof MongoCursor<?>)
			((MongoCursor<?>) resDocsIter).close();

		return suppCredsDoc;
	}

	@Deprecated
	public static JSONArray getPccHAPCredentialsv2(String usageType) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_USAGE, usageType);
		String tmpstr = MDMUtils.getData(String.format(MDMConfigV2.getURL(MDM_COLL_PCCHAP), filters.toString()), true);
		JSONObject resJson = new JSONObject(tmpstr);
		return resJson.getJSONArray("data");
	}

	public static JSONObject getCountryData(String countryName) {
		// TODO:should this be called here or should be stored in redis?
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_DATA.concat(".").concat(MDM_PROP_VALUE), countryName);
		
		Map<String, String> pathParams = new HashMap<String, String>();
		pathParams.put(MDM_PROP_ANCILLARYTYPE, MDM_PROP_COUNTRY);

		Document doc = getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_ANCILLARYDATA, filters,pathParams));
		return doc==null?new JSONObject():new JSONObject(doc.toJson());
	}
	
	public static JSONObject getState(String countryName) {
		// TODO:should this be called here or should be stored in redis?
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_DATA.concat(".").concat(MDM_PROP_VALUE), countryName);
		
		Map<String, String> pathParams = new HashMap<String, String>();
		pathParams.put(MDM_PROP_ANCILLARYTYPE, MDM_PROP_STATE);

		Document doc = getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_ANCILLARYDATA, filters,pathParams));
		return doc==null?new JSONObject():new JSONObject(doc.toJson());
	}
	
	/*@Deprecated
	public static JSONObject getCountryData(String countryName) {

		// TODO:should this be called here or should be stored in redis?
		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_DATA.concat(".").concat(MDM_PROP_VALUE), countryName);

		String url = String.format(MDMConfigV2.getURL(MDM_COLL_ANCILLARYDATA), MDM_PROP_COUNTRY, filters.toString());
		String res = MDMUtils.getData(url, false);
		JSONArray countryAncillaryArr = new JSONArray(res);
		JSONObject countryAncillary = countryAncillaryArr.getJSONObject(0);

		return countryAncillary;
	}

	@Deprecated
	public static JSONObject getState(String countryName) {

		// TODO:should this be called here or should be stored in redis?
		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_DATA.concat(".").concat(MDM_PROP_VALUE), countryName);

		String url = String.format(MDMConfigV2.getURL(MDM_COLL_ANCILLARYDATA), MDM_PROP_STATE, filters.toString());
		String res = MDMUtils.getData(url, false);
		JSONArray stateAncillaryArr = new JSONArray(res);
		JSONObject stateAncillary = stateAncillaryArr.getJSONObject(0);

		return stateAncillary;
	}*/

	static Document getOrgHierarchyDocumentByName(String orgHierarchyType, String orgHierarchyEntityName) {
		// MongoCollection<Document> orgColl =
		// MDMConfig.getCollection(MDM_COLL_ORGANIZATION);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_TYPE, orgHierarchyType);
		filters.put(MDM_PROP_NAME, orgHierarchyEntityName);
		
		Map<String, String> pathParams = new HashMap<String, String>();
		pathParams.put(MDM_PROP_TYPE, orgHierarchyType);


		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_ORGANIZATION, filters,pathParams));
	}

	@Deprecated
	static JSONObject getOrgHierarchyDocumentByNamev2(String orgHierarchyType, String orgHierarchyEntityName) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_TYPE, orgHierarchyType);
		filters.put(MDM_PROP_NAME, orgHierarchyEntityName);
		String tmpstr = MDMUtils.getData(
				String.format(MDMConfigV2.getURL(MDM_COLL_ORGANIZATION), orgHierarchyType, filters.toString()), true);
		return getLatestUpdatedJson(new JSONObject(tmpstr));
	}

	public static Document getOrgHierarchyDocumentById(String orgHierarchyType, String orgHierarchyEntityId) {
		// MongoCollection<Document> orgColl =
		// MDMConfig.getCollection(MDM_COLL_ORGANIZATION);

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_TYPE, orgHierarchyType);
		filters.put(DBCollection.ID_FIELD_NAME, orgHierarchyEntityId);
		
		Map<String, String> pathParams = new HashMap<String, String>();
		pathParams.put(MDM_PROP_TYPE, orgHierarchyType);

		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_ORGANIZATION, filters,pathParams));
	}

	@Deprecated
	public static JSONObject getOrgHierarchyDocumentByIdv2(String orgHierarchyType, String orgHierarchyEntityId) {

		JSONObject filters = new JSONObject();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_TYPE, orgHierarchyType);
		filters.put(DBCollection.ID_FIELD_NAME, orgHierarchyEntityId);
		String tmpstr = MDMUtils.getData(
				String.format(MDMConfigV2.getURL(MDM_COLL_ORGANIZATION), orgHierarchyType, filters.toString()), true);
		return getLatestUpdatedJson(new JSONObject(tmpstr));

	}

	public static Iterator<Document> getDocumentIterator(String collName, Map<String, Object> filters,
			Map<String, String> pathVar) {
		Iterator<Document> docItr =MDMConfig.callApi() ? getDocumentIteratorFormApi(collName, filters, pathVar)
				: getDocumentIteratorFormCollection(collName, filters);
		return docItr!=null?docItr:new ArrayList<Document>().iterator();
	}

	public static Iterator<Document> getDocumentIterator(String collName, Map<String, Object> filters) {
		return getDocumentIterator(collName, filters,null);
	}

	@SuppressWarnings("unchecked")
	private static Iterator<Document> getDocumentIteratorFormApi(String collName, Map<String, Object> filters,
			Map<String, String> pathVar) {
		Document resDoc = Document.parse(getData(collName, filters, pathVar));
		Object dataDoc = resDoc.get("data");
		return dataDoc instanceof List<?> && ((List<?>)dataDoc).size() > 0 && ((List<?>)dataDoc).get(0) instanceof Document?((List<Document>)dataDoc).iterator():null;
	}

	private static Iterator<Document> getDocumentIteratorFormCollection(String collName, Map<String, Object> filters) {
		try {
			MongoCollection<Document> coll = MDMConfig.getCollection(collName);
			return coll.find(new Document(filters)).iterator();
		} catch (Exception e) {
			logger.warn(String.format("Unable to retrieve from mongo collection %s", collName));
		}
		return null;
	}

	@Deprecated
	public static String getData(String urlToRead, Boolean isJSONObject) {

		Object resJson = null;
		try {
			resJson = callAPI(urlToRead, isJSONObject);

			if (resJson == null) {
				JSONObject errorMsg = HTTPServiceConsumer.getErrorMessage("mdm", urlToRead, MDMConfigV2.getmHttpHeaders(), "GET", null);
				if (errorMsg != null) {
					Boolean isLoginSuccess = retryLogin();
					if (isLoginSuccess) {
						resJson = callAPI(urlToRead, isJSONObject);
						if (resJson != null)
							return resJson.toString();
					}
				}
				// TODO : This will happen only retrylogin Failed.
				return isJSONObject ? JSON_OBJECT_EMPTY : "[]";
			}
			return resJson.toString();
		} catch (Exception e) {
			logger.error("Error in retrieving data from MDM" + e);
		}
		return isJSONObject ? JSON_OBJECT_EMPTY : "[]";
	}

	@Deprecated
	public static Object callAPI(String urlToRead, Boolean isJSONObject) {

		URL url = null;
		Object resJson = null;
		String token = getTokenfromRedis();

		Map<String, String> mHttpHeaders = MDMConfigV2.getmHttpHeaders();
		if (!token.isEmpty()) {
			String mHttpOAuth = "Bearer ".concat(token);
			mHttpHeaders.put(HTTP_HEADER_AUTHORIZATION, mHttpOAuth);
		}
		try {
			// To Handle space and special Characters in URL
			url = new URL(urlToRead);
			URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(),
					url.getQuery(), url.getRef());
			url = uri.toURL();
		} catch (Exception e1) {
			logger.warn(String.format("Exception occured while forming URL %s ", urlToRead), e1);
		}
		try {
			resJson = isJSONObject ? HTTPServiceConsumer.consumeJSONService("mdm", url, mHttpHeaders, "GET", null)
					: HTTPServiceConsumer.consumeJSONReturnJSONArray("mdm", url, mHttpHeaders, "GET", null);
			if (resJson != null) {
				String mdmCollection = urlToRead.substring(0, urlToRead.indexOf("?"));
				mdmCollection = mdmCollection.substring(mdmCollection.lastIndexOf("/") + 1);
				logger.info(String.format("Received MDM response from collection[%s] and from url[%s] : %s",
						mdmCollection, urlToRead, resJson.toString()));

			} else {
				String mdmCollection = urlToRead.substring(0, urlToRead.indexOf("?"));
				mdmCollection = mdmCollection.substring(mdmCollection.lastIndexOf("/"), mdmCollection.lastIndexOf("/"));
				logger.error(String.format("Received MDM null response from collection[%s] and from url[%s]",
						mdmCollection, urlToRead));
			}
		} catch (Exception e) {
			logger.error("Error in retrieving data from MDM" + e);
		}
		return resJson;
	}

	public static String getData(String collName, Map<String, Object> filters, Map<String, String> pathVar) {

		HttpTemplate resTemplate = null;
		try {
			ServiceConfig apiCfg = MDMConfig.getApiConfig(collName);
			if (pathVar == null) {
				pathVar = new HashMap<String, String>();
			}
			pathVar.put("filterQuery", new JSONObject(filters).toString());
			URL urlToRead = apiCfg.getServiceURL(pathVar);
			resTemplate = callAPI(apiCfg,urlToRead);
			int resCode = resTemplate.getStatusCode();

			if ((resCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT || resCode == HttpURLConnection.HTTP_UNAUTHORIZED) && retryLogin()) {
				resTemplate = callAPI(apiCfg,urlToRead);
			}
		} catch (Exception e) {
			logger.error("Error in retrieving data from MDM" + e);
		}
		return resTemplate == null || Utils.isStringNullOrEmpty(resTemplate.getPayloadString()) ? JSON_OBJECT_EMPTY
				: resTemplate.getPayloadString();
	}

	public static HttpTemplate callAPI(ServiceConfig apiCfg,URL urlToRead) {

		HttpTemplate resJson = null;
		try {
			HashMap<String, String> httpHdrs = new HashMap<String, String>(apiCfg.getHttpHeaders());// so that original
																									// hdrs are not
																									// replaced
			httpHdrs.put(HTTP_HEADER_AUTHORIZATION,
					String.format(httpHdrs.get(HTTP_HEADER_AUTHORIZATION), getTokenfromRedis()));
			resJson = HTTPServiceConsumer.consumeService("MDM-API", urlToRead, httpHdrs,
					apiCfg.getHttpMethod(), null);
			/*if (resJson != null) {
				String mdmCollection = urlToRead.substring(0, urlToRead.indexOf("?"));
				mdmCollection = mdmCollection.substring(mdmCollection.lastIndexOf("/") + 1);
				logger.info(String.format("Received MDM response from collection[%s] and from url[%s] : %s",
						mdmCollection, urlToRead, resJson.toString()));

			} else {
				String mdmCollection = urlToRead.substring(0, urlToRead.indexOf("?"));
				mdmCollection = mdmCollection.substring(mdmCollection.lastIndexOf("/"), mdmCollection.lastIndexOf("/"));
				logger.error(String.format("Received MDM null response from collection[%s] and from url[%s]",
						mdmCollection, urlToRead));
			}*/
		} catch (Exception e) {
			logger.error("Error in retrieving data from MDM" + e);
		}
		return resJson;
	}

	private static JSONObject login() {

		JSONObject reqJson = new JSONObject();

		reqJson.put("username", MDMConfig.getApiUser());
		reqJson.put("password", MDMConfig.getApiPassword());

		JSONObject resJson = null;
		try {
			ServiceConfig usrLgnCfg = MDMConfig.getApiConfig("userLogin");
			resJson = HTTPServiceConsumer.consumeJSONService("MDM-LOGIN", usrLgnCfg.getServiceURL(),
					usrLgnCfg.getHttpHeaders(), reqJson);
		} catch (Exception e) {
			logger.error("An exception occured while logging into MDM");
			return null;
		}
		if (resJson == null) {
			// Will come here only if token not found in redis and unable to login in MDM
			// TODO : Figure out how to handle this.
			logger.error("An error occured while logging into MDM");
			return null;
		}

		return resJson;
	}

	private static void logout(String token) {

		ServiceConfig lgtCfg = MDMConfig.getApiConfig("userLogout");

		try {
			HashMap<String, String> httpHdrs = new HashMap<String, String>(lgtCfg.getHttpHeaders());// so that original
																									// hdrs are not
																									// replaced
			httpHdrs.put(HTTP_HEADER_AUTHORIZATION, String.format(httpHdrs.get(HTTP_HEADER_AUTHORIZATION), token));
			JSONObject res = HTTPServiceConsumer.consumeJSONService("MDM-LOGOUT", lgtCfg.getServiceURL(), httpHdrs,
					new JSONObject());
			if (res == null)
				logger.warn("Unable to Logout");

		} catch (Exception e) {
			logger.warn("Error in performing logout from MDM" + e);
		}

	}

	private static Boolean retryLogin() {

		String token = "";
		String redisKey = getRedisKeyForLogin();
		Map<String, String> loginResMap = null;
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			loginResMap = redisConn.hgetAll(redisKey);
		}
		// RedisConfig.releaseRedisConnectionToPool(redisConn);
		if (loginResMap != null && !loginResMap.isEmpty())
			token = loginResMap.get("token");

		logout(token);
		JSONObject resJson = login();
		if (resJson != null) {
			pushTokenToRedis(resJson);
			return true;
		}
		return false;
	}

	private static void pushTokenToRedis(JSONObject resJson) {

		String redisKey = getRedisKeyForLogin();
		if (resJson != null) {
			Map<String, String> loginResMap = new HashMap<String, String>();
			loginResMap.put(USER, resJson.get(USER).toString());
			loginResMap.put(TOKEN, resJson.get(TOKEN).toString());
			loginResMap.put(LAST_UPDATED, resJson.get(LAST_UPDATED).toString());
			loginResMap.put(LOGIN_TIME, resJson.get(LOGIN_TIME).toString());
			loginResMap.put(EXPIRE_IN, resJson.get(EXPIRE_IN).toString());
			loginResMap.put(STATUS, resJson.get(STATUS).toString());
			loginResMap.put(ID, resJson.get(ID).toString());
			if (loginResMap != null && !loginResMap.isEmpty()) {
				try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
					redisConn.hmset(redisKey, loginResMap);
				}
				// EXPIRE_IN is in milliseconds
				// redisConn.pexpireAt(redisKey,
				// Long.valueOf(loginResMap.get(EXPIRE_IN).toString()));
				// redisConn.hmset(redisKeyBackup, loginResMap);
			}
		}
	}

	public static String getTokenfromRedis() {

		String redisKey = getRedisKeyForLogin();

		Map<String, String> loginResMap = null;
		try (Jedis redisConn = RedisConfig.getRedisConnectionFromPool();) {
			loginResMap = redisConn.hgetAll(redisKey);
		}
		// RedisConfig.releaseRedisConnectionToPool(redisConn);

		String token = "";
		if (loginResMap != null && !loginResMap.isEmpty()) {
			token = loginResMap.get("token").toString();
		}

		return token;
	}

	private static String getRedisKeyForLogin() {
		return "MDM-credential".concat(MDMConfig.getApiUser().concat("|").concat(MDMConfig.getApiPassword()));
	}

	// CRM methods
	@Deprecated
	public static FindIterable<Document> getPartPaymentDetails(String prodCateg, String prodCategSubType,
			String entityId, String entityType, String companyMarket) {
		MongoCollection<Document> partColl = MDMConfig.getCollection("partPaymentMasters");

		Map<String, Object> filters = new HashMap<String, Object>();
		// filters.put(MDM_PROP_DELETED, false);
		filters.put("productCat", prodCateg);
		filters.put("productCatSubtype", prodCategSubType);
		filters.put("entityId", entityId);
		filters.put("ppmEntityType", entityType);
		filters.put("companyMarket", companyMarket);

		return partColl.find(new Document(filters));
	}
	
	public static List<Document> getPartPaymentDetailsV2(String prodCateg, String prodCategSubType,
			String entityId, String entityType, String companyMarket) {
		Map<String, Object> filters = new HashMap<String, Object>();
	
		//filters.put(MDM_PROP_DELETED, false);
		filters.put("productCat", prodCateg);
		filters.put("productCatSubtype", prodCategSubType);
		filters.put("entityId", entityId);
		filters.put("ppmEntityType", entityType);
		filters.put("companyMarket", companyMarket);

		List<Document> partPaymntDoc = new ArrayList<Document>();
		Iterator<Document> resDocsIter = getDocumentIterator("partPaymentMasters", filters);
		while (resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
			partPaymntDoc.add(resDoc);
		}

		if (resDocsIter instanceof MongoCursor<?>)
			((MongoCursor<?>) resDocsIter).close();

		return partPaymntDoc;
	}

	@Deprecated
	public static FindIterable<Document> getSupplierSettlement(String supplierRef) {
		MongoCollection<Document> coll = MDMConfig.getCollection("suppliersSettlements");

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put("deleted", false);
		filters.put("supplierId", supplierRef);

		return coll.find(new Document(filters));
	}

	public static List<Document> getSupplierSettlementV2(String supplierRef) {
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put("deleted", false);
		filters.put("supplierId", supplierRef);

		List<Document> suppSettlementDoc = new ArrayList<Document>();
		Iterator<Document> resDocsIter = getDocumentIterator("suppliersSettlements", filters);
		while (resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
			suppSettlementDoc.add(resDoc);
		}

		if (resDocsIter instanceof MongoCursor<?>)
			((MongoCursor<?>) resDocsIter).close();

		return suppSettlementDoc;
	}
	
	@Deprecated
	public static FindIterable<Document> getTimeLimitMasterDetails(String entityId, String entityType,
			String companyMarket) {
		MongoCollection<Document> coll = MDMConfig.getCollection("timeLimitMasters");

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put("deleted", false);
		filters.put("entityId", entityId);
		filters.put("tlmEntityType", entityType);
		filters.put("companyMarket", companyMarket);
		return coll.find(new Document(filters));
	}
	
	public static List<Document> getTimeLimitMasterDetailsV2(String entityId, String entityType,
			String companyMarket) {
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put("deleted", false);
		filters.put("entityId", entityId);
		filters.put("tlmEntityType", entityType);
		filters.put("companyMarket", companyMarket);
		
		List<Document> timeLimitDoc = new ArrayList<Document>();
		Iterator<Document> resDocsIter = getDocumentIterator("timeLimitMasters", filters);
		while (resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
			timeLimitDoc.add(resDoc);
		}

		if (resDocsIter instanceof MongoCursor<?>)
			((MongoCursor<?>) resDocsIter).close();

		return timeLimitDoc;
	}

	public static FindIterable<Document> getClientTypeDetails(String entityId) {
		MongoCollection<Document> coll = MDMConfig.getCollection("clientTypes");

		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put("_id", entityId);
		return coll.find(new Document(filters));
	}

	public static Map<String,String> getProductIdToIATACodeMapForCateg(String companyId) {
		// TODO Auto-generated method stub
		//MongoCollection<Document> coll = MDMConfig.getCollection("productAir");
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put("deleted", false);
		filters.put(MDM_PROP_SCREEN1.concat(".").concat("status"), "Active");
		filters.put(MDM_PROP_SCREEN1.concat(".").concat(MDM_PROP_COMPANYID), companyId);
		
		Document resArrDoc = Document.parse(getData(MDM_COLL_PRODAIR, filters, null));
		Object dataDoc = resArrDoc.get("data");
		Iterator<Document> resDocsIter= dataDoc instanceof List<?> && ((List<?>)dataDoc).size() > 0 && ((List<?>)dataDoc).get(0) instanceof Document?((List<Document>)dataDoc).iterator():null;
		
		//begin
		//should there be a latest document check here?
		
		Map<String,String> prodIdToIATACodeMap=new HashMap<String,String>();
		while(resDocsIter.hasNext()) {
			Document resDoc = resDocsIter.next();
				JSONObject productAirJson=new JSONObject(new JSONTokener(resDoc.toJson()));
				prodIdToIATACodeMap.put(productAirJson.getString("_id"), productAirJson.getJSONObject(MDM_PROP_SCREEN1).getString(MDM_PROP_IATACODEPROD));				
		}
		
		

		if (resDocsIter instanceof MongoCursor<?>)
			((MongoCursor<?>) resDocsIter).close();
		
		return prodIdToIATACodeMap;
		//end
	}

	public static Document getSupplierCompanyMarketMapping(String prodCategTransport, String prodCategSubtypeFlight,
			String suppID) {
		
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put("deleted", false);
		filters.put("basicDetails".concat(".").concat(MDM_PROP_SUPPID), suppID);
		
		//Iterator<Document> resDocsIter = getDocumentIterator("supComMarketMapping", filters);
		Document resDoc = Document.parse(getData("supComMarketMapping", filters, null));
		ArrayList<org.bson.Document> dataDocs=(ArrayList<Document>) resDoc.get("data");
		return getLatestUpdatedDocument(dataDocs.iterator());
	}

	public static Document getMarketDetailsFromOrganizationColl(String mCompanyMarketId, String mdmValTypemarket) {
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put("deleted", false);
		filters.put(MDM_PROP_TYPE, mdmValTypemarket);
		filters.put(DBCollection.ID_FIELD_NAME, mCompanyMarketId);
		
		Map<String, String> pathParams = new HashMap<String, String>();
		pathParams.put(MDM_PROP_TYPE, mdmValTypemarket);
		
	/*	Document resDoc = Document.parse(getData(MDM_COLL_ORGANIZATION, filters, pathParams));
		ArrayList<org.bson.Document> dataDocs=(ArrayList<Document>) resDoc.get("data");
		return getLatestUpdatedDocument(dataDocs.iterator());*/
		
		return getLatestUpdatedDocument(getDocumentIterator(MDM_COLL_ORGANIZATION, filters,pathParams));
		
	}

	public static String getClientGroupProductSuppliers(String clientID) {
		//MongoCollection<Document> ac2gColl = MDMConfig.getCollection(MDM_COLL_ASSOCCLIENTTOGROUP);
		
		Map<String, Object> filters = new HashMap<String, Object>();
		filters.put(MDM_PROP_DELETED, false);
		filters.put(MDM_PROP_CLIENTID, clientID);
		filters.put(MDM_PROP_GROUPS.concat(".").concat(MDM_PROP_PRODSUPP), new Document(QueryOperators.EXISTS, true));

		// Reference CKIL_323230 (2.2.2/BR09): For a client, there could be multiple client group mappings.
		// In such case, the mapping with latest timestamp would be used.
		Document latestUpdatedDocument = MDMUtils.getLatestUpdatedDocument(MDMUtils.getDocumentIterator(MDM_COLL_ASSOCCLIENTTOGROUP, filters));
		if (latestUpdatedDocument == null) {
			 return "";
		}
		
		String clientGroupID = ((Document) latestUpdatedDocument.get(MDM_PROP_GROUPS)).getString(MDM_PROP_PRODSUPP);
		return clientGroupID!=null?clientGroupID:"";
	}

}
