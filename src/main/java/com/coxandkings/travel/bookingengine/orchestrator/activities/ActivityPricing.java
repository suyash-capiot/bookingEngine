package com.coxandkings.travel.bookingengine.orchestrator.activities;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLTransformer;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

@FunctionalInterface
public interface ActivityPricing {
	public JSONArray getPricingJson(JSONArray pricingJsonArr, Element pricingElem);

	public static final ActivityPricing paxPricing = (pJsonArr, pElem) -> {
		Element[] pCategoryElems = XMLUtils.getElementsAtXPath(pElem, "./ns:ParticipantCategory");
		for (Element pCategoryElem : pCategoryElems) {
			JSONObject pricingJson = new JSONObject();
			pricingJson.put("participantCategory", ActivityPassengerType.valueOf(XMLUtils.getValueAtXPath(pCategoryElem, "./ns:QualifierInfo")).toString());
			pricingJson.put("totalPrice",
					Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(pCategoryElem, "./ns:Price/@Amount"), 0));
			pricingJson.put("currencyCode", XMLUtils.getValueAtXPath(pCategoryElem, "./ns:Price/@CurrencyCode"));
			pJsonArr.put(pricingJson);
		}
		return pJsonArr;
	};

	public static final ActivityPricing summaryPricing = (pJsonArr, pElem) -> {
		JSONObject pricingJson = new JSONObject();
		pricingJson.put("participantCategory", "SUMMARY");
		pricingJson.put("totalPrice",
				Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(pElem, "./ns:Summary/@Amount"), 0));
		pricingJson.put("currencyCode", XMLUtils.getValueAtXPath(pElem, "./ns:Summary/@CurrencyCode"));
		pJsonArr.put(pricingJson);
		return pJsonArr;
	};

	public static final ActivityPricing rePricePricing = (pJsonArr, pElem) -> {
		Element[] pCategoryElems = XMLUtils.getElementsAtXPath(pElem, "./ns:ParticipantCategory");
		if (null != XMLUtils.getValueAtXPath(pElem, "./@PerPaxPriceInd") && !"false".equals(XMLUtils.getValueAtXPath(pElem, "./@PerPaxPriceInd")) && 
		null != pCategoryElems && pCategoryElems.length > 0) {
			for (Element pCategoryElem : pCategoryElems) {
				JSONObject participantPricingJson = new JSONObject();
				participantPricingJson.put("participantCategory",
						ActivityPassengerType.valueOf(XMLUtils.getValueAtXPath(pCategoryElem, "./ns:QualifierInfo")).toString());
				participantPricingJson.put("totalPrice",
						Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(pCategoryElem, "./ns:Price/@Amount"), 0));
				participantPricingJson.put("currencyCode",
						XMLUtils.getValueAtXPath(pCategoryElem, "./ns:Price/@CurrencyCode"));
				participantPricingJson.put("Age",
						XMLUtils.getValueAtXPath(pCategoryElem, "./@Age"));
				pJsonArr.put(participantPricingJson);
			}

		}
			
		JSONObject pricingJson = new JSONObject();
		pricingJson.put("participantCategory", "SUMMARY");
		pricingJson.put("totalPrice",
				Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(pElem, "./ns:Summary/@Amount"), 0));
		pricingJson.put("currencyCode", XMLUtils.getValueAtXPath(pElem, "./ns:Summary/@CurrencyCode"));
		pJsonArr.put(pricingJson);
		return pJsonArr;

	};

	public static final Map<String, ActivityPricing> suppRepricePricing = ((Supplier<Map<String, ActivityPricing>>) () -> {
		Map<String, ActivityPricing> mutableMap = new HashMap<>();
		mutableMap.put("TOURICO", rePricePricing);
		mutableMap.put("HOTELBEDS", rePricePricing);
		mutableMap.put("WHL", rePricePricing);
		mutableMap.put("BEMYGUEST", rePricePricing);
		mutableMap.put("VIATOR", rePricePricing);
		mutableMap.put("SPORTSEVENTS365", rePricePricing);
		mutableMap.put("ACAMPORA", rePricePricing);
		mutableMap.put("GTA", rePricePricing);
		mutableMap.put("THETRAVELLER", rePricePricing);
		return mutableMap;
	}).get();

}
