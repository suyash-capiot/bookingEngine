package com.coxandkings.travel.bookingengine.orchestrator.rail;

import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.exception.ValidationException;

public class RailRequestValidator implements RailConstants {
	/** For search,reprice operation **/
	static void validateTravelDate(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException{
		String travelDate=reqBodyJson.optString(JSON_PROP_TRAVELDATE);
		if (travelDate==null || travelDate.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR6001");
		}
	}
	
	/** For search,reprice operation **/
	static void validateOriginalLocation(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException{
		String originalLocation=reqBodyJson.getString(JSON_PROP_ORIGINLOC);
		if (originalLocation==null || originalLocation.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR6002");
		}
	}
	
	/** For search,reprice operation **/
	static void validateDestinationLocation(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException{
		String destinationLocation=reqBodyJson.getString(JSON_PROP_DESTLOC);
		if (destinationLocation==null || destinationLocation.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR6003");
		}
	}
	
	/** For search,train schedule,retrieve,reprice operation **/
	static void validateSupplierRef(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException{
		String supplierRef=reqBodyJson.getString(JSON_PROP_SUPPREF);
		if (supplierRef==null || supplierRef.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR6004");
		}
	}
	
	/** For train schedule,reprice operation **/
	static void validateTrainNumber(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException{
		String trainNumber=reqBodyJson.getString(JSON_PROP_TRAINNUM);
		if (trainNumber==null || trainNumber.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR6005");
		}
	}
	
	/** For reprice operation **/
	static void validateReservationClass(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException{
		String reservationClass=reqBodyJson.getString(JSON_PROP_RESERVATIONCLASS);
		if (reservationClass==null || reservationClass.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR6006");
		}
	}
	
	/** For reprice operation **/
	static void validateReservationType(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException{
		String reservationType=reqBodyJson.getString(JSON_PROP_RESERVATIONTYPE);
		if (reservationType==null || reservationType.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR6007");
		}
	}
	
	/** For retrieve operation **/
	static void validateClientTransactionId(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException{
		String clientTransactionId=reqBodyJson.getString(JSON_PROP_CLIENTTRANSACTION_ID);
		if (clientTransactionId==null || clientTransactionId.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR6008");
		}
	}
	
	/** For retrieve operation **/
	static void validatePnr(JSONObject reqBodyJson,JSONObject reqHdrJson) throws ValidationException{
		String pnr=reqBodyJson.getString(JSON_PROP_PNR);
		if (pnr==null || pnr.isEmpty()) {
			throw new ValidationException(reqHdrJson,"TRLERR6009");
		}
	}
	
}
