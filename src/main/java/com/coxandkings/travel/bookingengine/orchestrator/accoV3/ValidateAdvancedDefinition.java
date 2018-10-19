package com.coxandkings.travel.bookingengine.orchestrator.accoV3;

import java.text.ParseException;
import java.util.Date;
import java.util.List;

import org.bson.Document;

import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.Utils;

public class ValidateAdvancedDefinition implements Constants{

	public static Boolean processOtherProducts(Document pccHapAdvDefnDoc) throws ParseException {
		if(pccHapAdvDefnDoc==null)
			return false;
			
		//if otherProduct is not present return false
		Document otherProductDoc = (Document) pccHapAdvDefnDoc.get("forOtherProduct");
		if(otherProductDoc==null || otherProductDoc.isEmpty()) {
			return false;
		}
			
		Document validityDoc = (Document) otherProductDoc.get("validity");
		if(validityDoc!=null) {
			String validityType=(String) validityDoc.get("validityTypeSelected");
			if(Utils.isStringNotNullAndNotEmpty(validityType)) {
				Document validityTypeDoc = (Document) validityDoc.get(validityType);
				switch(validityType) {
				case "travelDate":
					if(validateTravelDate(validityTypeDoc)==false) {
						return false;
					}
					break;
				case "saleDate":
					break;
				case "salePlusTravel":
					break;
				}
			}
		}
			
		return null;
		
		
	}

	@SuppressWarnings("unchecked")
	private static Boolean validateTravelDate(Document validityTypeDoc) throws ParseException {
		//if travelFrom t0 dates are not specified then check blockout dates
		String travelFrom=(String) validityTypeDoc.get("travelfrom");
		String travelTo=(String) validityTypeDoc.get("travelto");
		Date trvlFromDate = DATE_FORMAT.parse((String) travelFrom);
		Date trvlToDate = DATE_FORMAT.parse((String) travelTo);
		
		//In place of new Date there should be checkin Date and checkout date
		if(!(trvlFromDate.compareTo(new Date())<=0 && trvlToDate.compareTo(new Date())>=0)) {
			return false;
		}
		
		List<Document> blockOutList=(List<Document>) validityTypeDoc.get("blockout");
		return false;
		
		
	}
}
