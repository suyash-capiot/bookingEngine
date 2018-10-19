package com.coxandkings.travel.bookingengine.orchestrator.air;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.enums.ClientType;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.enums.TripIndicator;
import com.coxandkings.travel.bookingengine.userctx.ClientInfo;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.redis.RedisAirportData;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class AirSearchProcessor implements AirConstants {

    private static final Logger logger = LogManager.getLogger(AirSearchProcessor.class);
    private static String mFeesPriceCompQualifier = JSON_PROP_FEES.concat(SIGMA).concat(".").concat(JSON_PROP_FEE).concat(".");
    //private static String mRcvsPriceCompQualifier = JSON_PROP_RECEIVABLES.concat(SIGMA).concat(".").concat(JSON_PROP_RECEIVABLE).concat(".");
    private static String mTaxesPriceCompQualifier = JSON_PROP_TAXES.concat(SIGMA).concat(".").concat(JSON_PROP_TAX).concat(".");
    private static String mIncvPriceCompFormat = JSON_PROP_INCENTIVE.concat(".%s@").concat(JSON_PROP_CODE);
    
    public static Element getAirTravelerAvailElement(Document ownerDoc, JSONObject traveller) throws Exception{
        Element travellerElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:AirTravelerAvail");
        Element psgrElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:PassengerTypeQuantity");
        psgrElem.setAttribute("Code", traveller.getString(JSON_PROP_PAXTYPE));
        psgrElem.setAttribute("Quantity", String.valueOf(traveller.getInt(JSON_PROP_QTY)));
        travellerElem.appendChild(psgrElem);
        return travellerElem;
    }

    public static Element getOriginDestinationElement(Document ownerDoc, JSONObject origDest) throws Exception{
        Element origDestElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:OriginDestinationInformation");
        Element depatureElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DepartureDateTime");
        depatureElem.setTextContent(origDest.getString(JSON_PROP_DEPARTDATE).concat("T00:00:00"));
        origDestElem.appendChild(depatureElem);
        Element originElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:OriginLocation");
        originElem.setAttribute("LocationCode", origDest.getString(JSON_PROP_ORIGLOC));
        origDestElem.appendChild(originElem);
        Element destElem = ownerDoc.createElementNS(Constants.NS_OTA, "ota:DestinationLocation");
        destElem.setAttribute("LocationCode", origDest.getString(JSON_PROP_DESTLOC));
        origDestElem.appendChild(destElem);
        return origDestElem;
    }

    public static JSONObject getAirItineraryJSON(Element airItinElem,Element airItinPriceElem, Map<String, Element> ptcPaxFareInfoMap, JSONArray paxInfoArray)throws Exception {
        JSONObject airItinJson = new JSONObject();

        if (airItinElem != null) {
            JSONArray odOptJsonArr = new JSONArray();
            Element[] odOptElems = XMLUtils.getElementsAtXPath(airItinElem, "./ota:OriginDestinationOptions/ota:OriginDestinationOption");
            for (Element odOptElem : odOptElems) {
                odOptJsonArr.put(getOriginDestinationOptionJSON(odOptElem,airItinPriceElem,ptcPaxFareInfoMap,paxInfoArray));
            }

            airItinJson.put(JSON_PROP_ORIGDESTOPTS, odOptJsonArr);
        }

        return airItinJson;
    }

    public static JSONObject getAirItineraryPricingJSON(Element airItinPricingElem, Map<String, Element> ptcPaxFareInfoMap, JSONArray paxInfoArray)throws Exception {
        JSONObject airItinPricingJson = new JSONObject();

        JSONObject itinTotalFareJson = new JSONObject();
        airItinPricingJson.put(JSON_PROP_ITINTOTALFARE, itinTotalFareJson);

        JSONArray ptcFaresJsonArr = new JSONArray();
        Element[] ptcFareBkElems = XMLUtils.getElementsAtXPath(airItinPricingElem, "./ota:PTC_FareBreakdowns/ota:PTC_FareBreakdown");
        for (Element ptcFareBkElem : ptcFareBkElems) {
        	JSONObject ptcFareJson=getPTCFareBreakdownJSON(ptcFareBkElem);
            //ptcFaresJsonArr.put(getPTCFareBreakdownJSON(ptcFareBkElem));
        	ptcFaresJsonArr.put(ptcFareJson);
            String flightRefNumberRPHList=(XMLUtils.getValueAtXPath(ptcFareBkElem, "./@FlightRefNumberRPHList")).toString();
       
           Element[] ptcFareInfo = XMLUtils.getElementsAtXPath(ptcFareBkElem, "./ota:FareInfo");
           
        		   /*getValueAtXPath(ptcFareBkElems, "./FareInfo");*/
            
           // ptcPaxFareInfoMap.put(ptcFareBkElem,((XMLUtils.getValueAtXPath "./ota:PassengerTypeQuantity/@Code")), getFareInfoMap(ptcFareBkElem));
            String ptcPaxTypeCode = (XMLUtils.getValueAtXPath(ptcFareBkElem, "./ota:PassengerTypeQuantity/@Code")).toString();
            String splitParm = "|" ;
          // String[] flightRPHRef=null;
          String[] flightRPHRef;
          int rphCountLen = 0;
           if(flightRefNumberRPHList.indexOf(splitParm)>0) {
        	  flightRPHRef = flightRefNumberRPHList.split("|");
        	  rphCountLen = flightRPHRef.length;
           }
           else {
        	   flightRPHRef=new String[1];
        	   //TODO :  Need to check?????
        	   flightRPHRef[0]=flightRefNumberRPHList;
        	   rphCountLen = flightRPHRef.length;
           }
           
           
           if(ptcFareInfo.length>0 && ptcFareInfo!=null){
           
         if(null!=(XMLUtils.getFirstElementAtXPath(ptcFareBkElem,  "./ota:FareInfo/ota:DepartureAirport"))) {
        	 
        	 for(int i=0;i<ptcFareInfo.length;i++) {
        	      if(ptcFareInfo.length==1) {
        	    	  Element ptcFareInfoElem=ptcFareInfo[0];
        	    	  String fareInfoKey = XMLUtils.getValueAtXPath(ptcFareInfoElem, "./ota:DepartureAirport/@LocationCode").concat("|").concat(XMLUtils.getValueAtXPath(ptcFareInfoElem, "./ota:ArrivalAirport/@LocationCode").concat("|").concat(ptcPaxTypeCode));
              		ptcPaxFareInfoMap.put(fareInfoKey, ptcFareInfoElem);
        	      }
        	      else
        	      {
        	    	  Element ptcFareInfoElem=ptcFareInfo[i];
        	    	  String fareInfoKey = XMLUtils.getValueAtXPath(ptcFareInfoElem, "./ota:DepartureAirport/@LocationCode").concat("|").concat(XMLUtils.getValueAtXPath(ptcFareInfoElem, "./ota:ArrivalAirport/@LocationCode").concat("|").concat(ptcPaxTypeCode));
              		ptcPaxFareInfoMap.put(fareInfoKey, ptcFareInfoElem);
        	      }
        	 }
        	 
         }
         else {	       
           //int rphCountLen = flightRPHRef.length;
           // ptcPaxFareInfoMap.put(flightRPHRef[0], ptcFareInfoElem);
            for(int i = 1 ; i <= rphCountLen ; i++) {
            if(ptcFareInfo.length==1) {
        
            	Element ptcFareInfoElem=ptcFareInfo[0];      
            		ptcPaxFareInfoMap.put(flightRPHRef[i-1].concat("|").concat(ptcPaxTypeCode), ptcFareInfoElem);
            	
            }
            else {
            	Element ptcFareInfoElem=ptcFareInfo[i];
            	ptcPaxFareInfoMap.put(flightRPHRef[i-1].concat("|").concat(ptcPaxTypeCode), ptcFareInfoElem);
            }
        }
        }
        }
        }
        airItinPricingJson.put(JSON_PROP_PAXTYPEFARES, ptcFaresJsonArr);

       
        if((XMLUtils.getFirstElementAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR"))!=null) {
        	
       if((XMLUtils.getFirstElementAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR/ota:SpecialServiceRequests"))!=null) {
        JSONArray ssrJsonArr = new JSONArray();
        Element[] ssrElems = XMLUtils.getElementsAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR/ota:SpecialServiceRequests/ota:SpecialServiceRequest");
        
        for (Element ssrElem : ssrElems) {
        	ssrJsonArr.put(getSSRJson(ssrElem));
        }
        airItinPricingJson.put(JSON_PROP_SPECIALSVCREQS, ssrJsonArr);
        }
       if((XMLUtils.getFirstElementAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR/ota:SeatRequests"))!=null) {
    	   JSONArray seatMapJsonArr = new JSONArray();
           Element[] seatMapElems = XMLUtils.getElementsAtXPath(airItinPricingElem, "./ota:PriceRequestInformation/ota:TPA_Extensions/air:SSR/ota:SeatRequests/ota:SeatRequest");
           
           for (Element seatMapElem : seatMapElems) {
        	   seatMapJsonArr.put(getSeatMap(seatMapElem));
           }
           airItinPricingJson.put(JSON_PROP_SEATMAP, seatMapJsonArr);
           }
       }
        
        
        return airItinPricingJson;
    }
    
 public static JSONObject getSeatMap(Element seatMapElem) {
		
    	JSONObject seatMapJson= new JSONObject();
    	
    	seatMapJson.put(JSON_PROP_ROWNUMBER , XMLUtils.getValueAtXPath(seatMapElem, "./@RowNumber"));
    	seatMapJson.put(JSON_PROP_TRAVELRPHLIST,  XMLUtils.getValueAtXPath(seatMapElem, "./@TravelerRefNumberRPHList"));
    	seatMapJson.put(JSON_PROP_SEATNBR,  XMLUtils.getValueAtXPath(seatMapElem, "./@SeatNumber"));
    	seatMapJson.put(JSON_PROP_SEATPREF,  XMLUtils.getValueAtXPath(seatMapElem, "./@SeatPreference"));
    	seatMapJson.put(JSON_PROP_DEPTLOCCODE,  XMLUtils.getValueAtXPath(seatMapElem, "./ota:DepartureAirport/@LocationCode"));
    	seatMapJson.put(JSON_PROP_ARRIVALLOCCODE,  XMLUtils.getValueAtXPath(seatMapElem, "./ota:ArrivalAirport/@LocationCode"));
    	
		return seatMapJson;
	}


    public static JSONObject getSSRJson(Element ssrElem) {
		
    	JSONObject ssrJson= new JSONObject();
    	
    	ssrJson.put(JSON_PROP_SSRCODE, ssrElem.getAttribute("SSRCode"));
    	ssrJson.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(ssrElem, "./ota:ServicePrice/@Total"));
    	JSONObject servicePriceJson= new JSONObject();
    	servicePriceJson.put(JSON_PROP_CCYCODE,XMLUtils.getValueAtXPath(ssrElem, "./ota:ServicePrice/@CurrencyCode"));
    	servicePriceJson.put(JSON_PROP_BASEFARE, XMLUtils.getValueAtXPath(ssrElem, "./ota:ServicePrice/ota:BasePrice/@Amount"));
    	servicePriceJson.put(JSON_PROP_TAXES, XMLUtils.getValueAtXPath(ssrElem, "./ota:ServicePrice/ota:Taxes/@Amount"));
    	
    	ssrJson.put(JSON_PROP_SVCPRC,servicePriceJson );
    	
		return ssrJson;
	}

    public static JSONObject getFlightSegmentJSON(Element flightSegElem,JSONObject flightSegJson)throws Exception {
       // JSONObject flightSegJson = new JSONObject();

        flightSegJson.put(JSON_PROP_DEPARTDATE, XMLUtils.getValueAtXPath(flightSegElem, "./@DepartureDateTime"));
        flightSegJson.put(JSON_PROP_ARRIVEDATE, XMLUtils.getValueAtXPath(flightSegElem, "./@ArrivalDateTime"));
        flightSegJson.put(JSON_PROP_ORIGLOC, XMLUtils.getValueAtXPath(flightSegElem, "./ota:DepartureAirport/@LocationCode"));
        flightSegJson.put(JSON_PROP_DEPARTTERMINAL, XMLUtils.getValueAtXPath(flightSegElem, "./ota:DepartureAirport/@Terminal"));
        flightSegJson.put(JSON_PROP_ARRIVETERMINAL, XMLUtils.getValueAtXPath(flightSegElem, "./ota:ArrivalAirport/@Terminal"));
        flightSegJson.put(JSON_PROP_DESTLOC, XMLUtils.getValueAtXPath(flightSegElem, "./ota:ArrivalAirport/@LocationCode"));
        flightSegJson.put(JSON_PROP_OPERAIRLINE, getAirlineJSON((XMLUtils.getFirstElementAtXPath(flightSegElem, "./ota:OperatingAirline")),JSON_PROP_OPERAIRLINE.toString()));
        flightSegJson.put(JSON_PROP_MARKAIRLINE, getAirlineJSON((flightSegElem),JSON_PROP_MARKAIRLINE.toString()));
        flightSegJson.put(JSON_PROP_JOURNEYDUR, Utils.convertToInt(XMLUtils.getValueAtXPath(flightSegElem, "./ota:TPA_Extensions/air:JourneyDuration"), 0));
        flightSegJson.put(JSON_PROP_QUOTEID, XMLUtils.getValueAtXPath(flightSegElem, "./ota:TPA_Extensions/air:QuoteID"));
        flightSegJson.put(JSON_PROP_EXTENDEDRPH, XMLUtils.getValueAtXPath(flightSegElem, "./ota:TPA_Extensions/air:ExtendedRPH"));
        flightSegJson.put(JSON_PROP_AVAILCOUNT, Utils.convertToInt(XMLUtils.getValueAtXPath(flightSegElem, "./ota:TPA_Extensions/air:AvailableCount"), 0));
        flightSegJson.put(JSON_PROP_REFUNDIND, Boolean.valueOf(XMLUtils.getValueAtXPath(flightSegElem, "./ota:TPA_Extensions/air:RefundableIndicator")));
        flightSegJson.put(JSON_PROP_CABINTYPE, XMLUtils.getValueAtXPath(flightSegElem, "./ota:BookingClassAvails/@CabinType"));
        flightSegJson.put(JSON_PROP_RESBOOKDESIG, XMLUtils.getValueAtXPath(flightSegElem, "./ota:BookingClassAvails/ota:BookingClassAvail/@ResBookDesigCode"));
        flightSegJson.put(JSON_PROP_RPH, XMLUtils.getValueAtXPath(flightSegElem, "./ota:BookingClassAvails/ota:BookingClassAvail/@RPH"));
        flightSegJson.put(JSON_PROP_CONNTYPE, XMLUtils.getValueAtXPath(flightSegElem, "./@ConnectionType"));
        flightSegJson.put(JSON_PROP_STATUS, XMLUtils.getValueAtXPath(flightSegElem, "./@Status"));
        
        
        //adding baggeges and meal codes
        flightSegJson.put(JSON_PROP_MEALCODE, XMLUtils.getValueAtXPath(flightSegElem, "./@MealCode"));
        JSONArray baggagesJsonArr= new JSONArray();
        Element[] baggageElems=XMLUtils.getElementsAtXPath(flightSegElem, "./ota:TPA_Extensions/air:Baggages/air:Baggage");
        for(Element baggageElem:baggageElems) {
        	JSONObject baggageJson=new JSONObject();
        	baggageJson.put(JSON_PROP_WEIGHT, XMLUtils.getValueAtXPath(baggageElem, "./ota:Allowance/@Weight"));
        	baggageJson.put(JSON_PROP_UNIT, XMLUtils.getValueAtXPath(baggageElem, "./ota:Allowance/@Unit"));
        	baggageJson.put(JSON_PROP_PIECES, XMLUtils.getValueAtXPath(baggageElem, "./ota:Allowance/@Pieces"));
        	baggageJson.put(JSON_PROP_BAGGAGECODE, XMLUtils.getValueAtXPath(baggageElem, "./ota:Allowance/ota:baggageCode"));
        	baggageJson.put(JSON_PROP_BAGGAGECHARGE, XMLUtils.getValueAtXPath(baggageElem, "./ota:Allowance/ota:baggageDescription"));
        	baggageJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(baggageElem, "./ota:Allowance/ota:currencyCode"));
        	baggageJson.put(JSON_PROP_FLIGHTREFRPH, XMLUtils.getValueAtXPath(baggageElem, "./ota:Allowance/ota:FlightRef"));
        	baggagesJsonArr.put(baggageJson);
        }
        flightSegJson.put(JSON_PROP_BAGGAGES, baggagesJsonArr);
        return flightSegJson;
    }

    private static void getFareInfoArray(JSONArray flightSegsJsonArr, Element airItinPriceElem, Map<String, Element> ptcPaxFareInfoMap, StringBuilder odoDestLocKey, JSONArray paxInfoArray) {
		
		//Element[] ptcFareBrkDwnElems=XMLUtils.getElementsAtXPath(airItinPriceElem, "./ota:PTC_FareBreakdowns/ota:PTC_FareBreakdown");
	
		for(int i=0;i<flightSegsJsonArr.length();i++) {
			JSONObject flightSegJson=flightSegsJsonArr.getJSONObject(i);
			JSONArray fareInfoJsonArr=new JSONArray();
				//String extendedRPH = flightSegJson.optString(JSON_PROP_EXTENDEDRPH);
				//String deptcode = flightSegJson.optString(JSON_PROP_ORIGLOC);
				//String arrivalcode = flightSegJson.optString(JSON_PROP_DESTLOC);
					for(int j = 0 ; j < paxInfoArray.length() ; j++) {
						JSONObject paxInfoJson = paxInfoArray.getJSONObject(j);
						String paxType = paxInfoJson.optString(JSON_PROP_PAXTYPE);
						String etxRphKey = flightSegJson.optString(JSON_PROP_EXTENDEDRPH).concat("|").concat(paxType);
						String flightSegKey = flightSegJson.optString(JSON_PROP_ORIGLOC).concat("|").concat(flightSegJson.optString(JSON_PROP_DESTLOC)).concat("|").concat(paxType);
						String odoDestPaxKey=(odoDestLocKey.toString()).concat("|").concat(paxType);
						if(ptcPaxFareInfoMap.containsKey(etxRphKey)) {
							Element fareInfoElem=ptcPaxFareInfoMap.get(etxRphKey);
							JSONObject fareInfoJson=new JSONObject();
							getFareInfo(fareInfoElem,fareInfoJson,paxType);
							fareInfoJsonArr.put(fareInfoJson);
						}
						else if(ptcPaxFareInfoMap.containsKey(flightSegKey)) {
							Element fareInfoElem=ptcPaxFareInfoMap.get(flightSegKey);
							JSONObject fareInfoJson=new JSONObject();
							getFareInfo(fareInfoElem,fareInfoJson,paxType);
							fareInfoJsonArr.put(fareInfoJson);
						}
						else if(ptcPaxFareInfoMap.containsKey(odoDestPaxKey)) {
							Element fareInfoElem=ptcPaxFareInfoMap.get(odoDestPaxKey);
							JSONObject fareInfoJson=new JSONObject();
							getFareInfo(fareInfoElem,fareInfoJson,paxType);
							fareInfoJsonArr.put(fareInfoJson);
							
						}
		
						
					}
					
						
			
		/*	for(Element ptcFareBrkDwn:ptcFareBrkDwnElems)
			{
				JSONObject fareInfoJson=new JSONObject();
				
				String paxType=(XMLUtils.getValueAtXPath(ptcFareBrkDwn, "./ota:PassengerTypeQuantity/@Code")).toString();
			//	Map<String,Element> fareInfoElemsMap=(Map<String, Element>) ptcPaxFareInfoMap.get(paxType);
				
				String extendedRPH = flightSegJson.optString(JSON_PROP_EXTENDEDRPH);
				
				//String extendRPH = (XMLUtils.getValueAtXPath(flightSegJson, "")).toString();
				
				//TODO : get fareInfo from map.
				
				
				
				//Change this logic, after confirmation from SI.
				if(fareInfoElemsMap.containsKey((flightSegJson.getString(JSON_PROP_ORIGLOC)+"|"+flightSegJson.getString(JSON_PROP_DESTLOC))) || fareInfoElemsMap.containsKey(odoDestLocKey.toString()))
				{
					if(fareInfoElemsMap.containsKey((flightSegJson.getString(JSON_PROP_ORIGLOC)+"|"+flightSegJson.getString(JSON_PROP_DESTLOC)))) {
						Element fareInfoElem=fareInfoElemsMap.get((flightSegJson.getString(JSON_PROP_ORIGLOC)+"|"+flightSegJson.getString(JSON_PROP_DESTLOC)));
						getFareInfo(fareInfoElem,fareInfoJson,paxType);
					}
					else {
						Element fareInfoElem=fareInfoElemsMap.get(odoDestLocKey.toString());
						getFareInfo(fareInfoElem,fareInfoJson,paxType);
					}
					fareInfoJsonArr.put(fareInfoJson);
				}
				
			}*/
			flightSegJson.put(JSON_PROP_FAREINFO, fareInfoJsonArr);
		}
		
	}

	private static void getFareInfo(Element fareInfoElem, JSONObject fareInfoJson, String paxType) {
		fareInfoJson.put(JSON_PROP_PAXTYPE, paxType);
		
		fareInfoJson.put(JSON_PROP_FAREREFERENCE, XMLUtils.getValueAtXPath(fareInfoElem, "./ota:FareReference").toString());
		
		fareInfoJson.put(JSON_PROP_FAREBASISCODE, XMLUtils.getValueAtXPath(fareInfoElem, "./ota:FareInfo/@FareBasisCode").toString());
		
		fareInfoJson.put(JSON_PROP_FARETYPE, XMLUtils.getValueAtXPath(fareInfoElem, "./ota:FareInfo/@FareType").toString());
		
		
	}

	public static JSONObject getAirlineJSON(Element airlineElem,String airlineType)throws Exception {
        JSONObject airlineJson = new JSONObject();
        if (airlineElem != null) {
        	if(airlineType.equals(JSON_PROP_MARKAIRLINE))
        	{
        		  airlineJson.put(JSON_PROP_FLIGHTNBR, airlineElem.getAttribute("FlightNumber"));
        		  airlineJson.put(JSON_PROP_AIRLINECODE, XMLUtils.getValueAtXPath(airlineElem, "./ota:MarketingAirline/@Code"));
        	}
        	else
        	{
            airlineJson.put(JSON_PROP_FLIGHTNBR, XMLUtils.getValueAtXPath(airlineElem, "./@FlightNumber"));
            airlineJson.put(JSON_PROP_AIRLINECODE, XMLUtils.getValueAtXPath(airlineElem, "./@Code"));
        }
        }
        return airlineJson;
    }

    public static JSONObject getOriginDestinationOptionJSON(Element odOptElem, Element airItinPriceElem, Map<String, Element> ptcPaxFareInfoMap, JSONArray paxInfoArray)throws Exception {
        JSONObject odOptJson = new JSONObject();
        
        StringBuilder odoDestLocKey=new StringBuilder();
        JSONArray flightSegsJsonArr = new JSONArray();
        Element[] flightSegElems = XMLUtils.getElementsAtXPath(odOptElem, "./ota:FlightSegment");
        int flightSegIndex=0;
        LocalDateTime requiredDepartDate=null;
        LocalDateTime requiredArrivalDate=null;
        for (Element flightSegElem : flightSegElems) {
        	JSONObject flightSegJson = new JSONObject();
            flightSegsJsonArr.put(getFlightSegmentJSON(flightSegElem,flightSegJson));
           // String latestDepartDate= flightSegJson.getString(JSON_PROP_DEPARTDATE);
            LocalDateTime latestDepartDate = LocalDateTime.parse(flightSegJson.getString(JSON_PROP_DEPARTDATE));
            LocalDateTime latestArrivalDate = LocalDateTime.parse(flightSegJson.getString(JSON_PROP_ARRIVEDATE));
            
            if(flightSegIndex==0) {
            	odoDestLocKey.append((flightSegJson.getString(JSON_PROP_ORIGLOC))).append("|").append((flightSegJson.getString(JSON_PROP_DESTLOC)));
            	 requiredDepartDate=latestDepartDate;
            	 requiredArrivalDate=latestArrivalDate;
            }
            else {
            	if(/*requiredDepartDate==null ||*/ latestDepartDate.isBefore(requiredDepartDate)) {
               	 requiredDepartDate=latestDepartDate;
               	odoDestLocKey.replace( 0, odoDestLocKey.indexOf("|")-1, (flightSegJson.getString(JSON_PROP_ORIGLOC)));
               	//odoDestLocKey.append((flightSegJson.getString(JSON_PROP_ORIGLOC)), 0, odoDestLocKey.indexOf("|"));
               }       
               if(( /*requiredArrivalDate==null || */latestArrivalDate.isAfter(requiredArrivalDate))) {
              	 requiredArrivalDate=latestArrivalDate;
              	// String destLoc=(flightSegJson.getString(JSON_PROP_DESTLOC));
              	 odoDestLocKey.replace(odoDestLocKey.indexOf("|")+1,odoDestLocKey.length(),(flightSegJson.getString(JSON_PROP_DESTLOC)));
              	/* odoDestLocKey.delete(odoDestLocKey.indexOf("|"),odoDestLocKey.length());
              	 odoDestLocKey.append((flightSegJson.getString(JSON_PROP_DESTLOC)));*/
              	
              }
            }
            
          
            flightSegIndex++;
        }
        getFareInfoArray(flightSegsJsonArr,airItinPriceElem,ptcPaxFareInfoMap,odoDestLocKey,paxInfoArray);
        odOptJson.put(JSON_PROP_FLIGHTSEG, flightSegsJsonArr);
        return odOptJson;
    }

 

	public static JSONObject getPricedItineraryJSON(Element pricedItinElem, JSONArray paxInfoArray) throws Exception{
        JSONObject pricedItinJson = new JSONObject();
        Map<String, Element> ptcPaxFareInfoMap=new HashMap<String,Element>();
        Element airItinElem = XMLUtils.getFirstElementAtXPath(pricedItinElem, "./ota:AirItinerary");
        Element airItinPricingElem = XMLUtils.getFirstElementAtXPath(pricedItinElem, "./ota:AirItineraryPricingInfo");
        pricedItinJson.put(JSON_PROP_AIRPRICEINFO, getAirItineraryPricingJSON(airItinPricingElem,ptcPaxFareInfoMap,paxInfoArray));
        pricedItinJson.put(JSON_PROP_AIRITINERARY, getAirItineraryJSON(airItinElem,airItinPricingElem,ptcPaxFareInfoMap,paxInfoArray));
        
       

        return pricedItinJson;
    }


	public static JSONObject getPTCFareBreakdownJSON(Element ptcFareBkElem) throws Exception{
        JSONObject paxFareJson = new JSONObject();

        paxFareJson.put(JSON_PROP_PAXTYPE, XMLUtils.getValueAtXPath(ptcFareBkElem, "./ota:PassengerTypeQuantity/@Code"));
        Element baseFareElem = XMLUtils.getFirstElementAtXPath(ptcFareBkElem, "./ota:PassengerFare/ota:BaseFare");
        JSONObject baseFareJson = new JSONObject();
        BigDecimal baseFareAmt = new BigDecimal(0);
        String baseFareCurrency = "";
        if (baseFareElem != null) {
            baseFareAmt = Utils.convertToBigDecimal(baseFareElem.getAttribute(XML_ATTR_AMOUNT), 0);
            baseFareCurrency = baseFareElem.getAttribute(XML_ATTR_CURRENCYCODE);
            baseFareJson.put(JSON_PROP_AMOUNT, baseFareAmt);
            baseFareJson.put(JSON_PROP_CCYCODE, baseFareCurrency);
        }
        paxFareJson.put(JSON_PROP_BASEFARE, baseFareJson);

        //----------------------------------------------------------------
        // Taxes
        // This code always calculates taxes/fees totals and retrieve currencycode. In SI standardization 3.0, the top level elements for
        // ota:Taxes and ota:Fees parent elements will never have Amount and CurrencyCode attributes. Total amount and currency code for
        // taxes and fees will need to be calculated from child tax/fee elements.

        Element taxesElem = XMLUtils.getFirstElementAtXPath(ptcFareBkElem, "./ota:PassengerFare/ota:Taxes");
        JSONObject taxesJson = new JSONObject();
        BigDecimal taxesAmount = new BigDecimal(0);
        String taxesCurrency = "";
        JSONArray taxJsonArr = new JSONArray();
        Element[] taxElems = XMLUtils.getElementsAtXPath(taxesElem, "./ota:Tax");
        for (Element taxElem : taxElems) {
            JSONObject taxJson = new JSONObject();
            BigDecimal taxAmt = Utils.convertToBigDecimal(taxElem.getAttribute(XML_ATTR_AMOUNT), 0);
            String taxCurrency = taxElem.getAttribute(XML_ATTR_CURRENCYCODE);
            taxJson.put(JSON_PROP_TAXCODE, taxElem.getAttribute("TaxCode"));
            taxJson.put(JSON_PROP_AMOUNT, taxAmt);
            taxJson.put(JSON_PROP_CCYCODE, taxCurrency);
            taxJsonArr.put(taxJson);
            taxesAmount = taxesAmount.add(taxAmt);
            taxesCurrency = taxCurrency;
        }

        taxesJson.put(JSON_PROP_AMOUNT, taxesAmount);
        taxesJson.put(JSON_PROP_CCYCODE, taxesCurrency);
        taxesJson.put(JSON_PROP_TAX, taxJsonArr);
        paxFareJson.put(JSON_PROP_TAXES, taxesJson);

        //----------------------------------------------------------------
        // Fees
        // This code always calculates taxes/fees totals and retrieve currencycode. In SI standardization 3.0, the top level elements for
        // ota:Taxes and ota:Fees parent elements will never have Amount and CurrencyCode attributes. Total amount and currency code for
        // taxes and fees will need to be calculated from child tax/fee elements.

        Element feesElem = XMLUtils.getFirstElementAtXPath(ptcFareBkElem, "./ota:PassengerFare/ota:Fees");
        JSONObject feesJson = new JSONObject();
        BigDecimal feesAmount = new BigDecimal(0);
        String feesCurrency = "";

        JSONArray feeJsonArr = new JSONArray();
        Element[] feeElems = XMLUtils.getElementsAtXPath(feesElem, "./ota:Fee");
        for (Element feeElem : feeElems) {
            JSONObject feeJson = new JSONObject();
            BigDecimal feeAmt = Utils.convertToBigDecimal(feeElem.getAttribute(XML_ATTR_AMOUNT), 0);
            String feeCurrency = feeElem.getAttribute(XML_ATTR_CURRENCYCODE);
            feeJson.put(JSON_PROP_FEECODE, feeElem.getAttribute("FeeCode"));
            feeJson.put(JSON_PROP_AMOUNT, feeAmt);
            feeJson.put(JSON_PROP_CCYCODE, feeCurrency);
            feeJsonArr.put(feeJson);
            feesAmount = feesAmount.add(feeAmt);
            feesCurrency = feeCurrency;
        }

        feesJson.put(JSON_PROP_AMOUNT, feesAmount);
        feesJson.put(JSON_PROP_CCYCODE, feesCurrency);
        feesJson.put(JSON_PROP_FEE, feeJsonArr);
        paxFareJson.put(JSON_PROP_FEES, feesJson);
        //----------------------------------------------------------------

        JSONObject totalFareJson = new JSONObject();
        totalFareJson.put(JSON_PROP_AMOUNT, baseFareAmt.add(taxesAmount).add(feesAmount));
        totalFareJson.put(JSON_PROP_CCYCODE, baseFareCurrency);
        paxFareJson.put(JSON_PROP_TOTALFARE, totalFareJson);
        
        //Insert offer here
        Element[] fareInfoElems= XMLUtils.getElementsAtXPath(ptcFareBkElem, "./ota:FareInfo");
        
        Set<String> offerSet=new HashSet<String>();
        JSONArray offerJsonArr=new JSONArray();
        for(Element fareInfoElem:fareInfoElems) {
        	
        	Element[] offersElem=  XMLUtils.getElementsAtXPath(fareInfoElem, "./ota:FareInfo/ota:TPA_Extensions/air:Offers");
        	
        	if(offersElem!=null) {
        		 for(Element offerElem:offersElem) {
        			 JSONObject offerJson=new JSONObject();
        			 
        			 String bundleID= XMLUtils.getValueAtXPath(offerElem, "./ota:Summary/@BundleID");
        			 String extendedRph=  XMLUtils.getValueAtXPath(offerElem, "./ota:Summary/@ID");
        			
        			 if(offerSet.contains(bundleID.concat(bundleID).concat(extendedRph)) &&  Utils.isStringNullOrEmpty(XMLUtils.getValueAtXPath(offerElem, "./ota:Summary/@BundleID"))) {
        				 continue;
        			 }
        			 offerJson.put(JSON_PROP_BUNDLEID, XMLUtils.getValueAtXPath(offerElem, "./ota:Summary/@BundleID"));
        			 offerJson.put(JSON_PROP_OFFERNAME, XMLUtils.getValueAtXPath(offerElem, "./ota:Summary/@Name"));
        			 offerJson.put(JSON_PROP_EXTENDEDRPH, XMLUtils.getValueAtXPath(offerElem, "./ota:Summary/@ID"));
        			 Element[] shortDescElems=XMLUtils.getElementsAtXPath(offerElem, "./ota:ShortDescription");
        			 
        			 if(shortDescElems!=null) {
        				 JSONArray shortDescJsonArr=new JSONArray();
        				 for(Element shortDescElem:shortDescElems) {
            				 JSONObject shortDescJson=new JSONObject();
            				 shortDescJson.put(JSON_PROP_HEADING, XMLUtils.getValueAtXPath(shortDescElem, "./ota:ShortDescription/@TextFormat"));
            				 shortDescJson.put(JSON_PROP_DESC, XMLUtils.getValueAtXPath(shortDescElem, "./ota:ShortDescription"));
            				 
            				 shortDescJsonArr.put(shortDescJson);
            			 }
        				 offerJson.put(JSON_PROP_DESCS, shortDescJsonArr);
        			 }
        			 offerSet.add(bundleID.concat(bundleID).concat(extendedRph));
        			 offerJson.put(JSON_PROP_OFFERTYPE, "Online");
        			 offerJsonArr.put(offerJson);
        		 }
        		 paxFareJson.put(JSON_PROP_SUPPOFFERS, offerJsonArr);
        	}
        	
        }
        
      
  
        return paxFareJson;
    }

    public static void getSupplierResponsePricedItinerariesJSON(Element resBodyElem, JSONArray pricedItinsJsonArr, JSONArray paxInfoArray) throws Exception {
    	getSupplierResponsePricedItinerariesJSON(resBodyElem, pricedItinsJsonArr, false, 0,  paxInfoArray);
    }
    
    public static void getSupplierResponsePricedItinerariesJSON(Element resBodyElem, JSONArray pricedItinsJsonArr, boolean generateBookRefIdx, int bookRefIdx, JSONArray paxInfoArray) throws Exception {
    	boolean isCombinedReturnJourney = Boolean.valueOf(XMLUtils.getValueAtXPath(resBodyElem, "./@CombinedReturnJourney"));
        Element[] pricedItinElems = XMLUtils.getElementsAtXPath(resBodyElem, "./ota:PricedItineraries/ota:PricedItinerary");
        
        for (Element pricedItinElem : pricedItinElems) {
            JSONObject pricedItinJson = getPricedItineraryJSON(pricedItinElem,paxInfoArray);
            pricedItinJson.put(JSON_PROP_TRANSACTID, XMLUtils.getValueAtXPath(resBodyElem, "./@TransactionIdentifier"));
            pricedItinJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath((Element)resBodyElem.getParentNode(), "./air:SupplierID"));
            pricedItinJson.put(JSON_PROP_ISRETURNJRNYCOMBINED, isCombinedReturnJourney);
            if (generateBookRefIdx) {
            	pricedItinJson.put(JSON_PROP_BOOKREFIDX, bookRefIdx);
            }
            pricedItinsJsonArr.put(pricedItinJson);
        }
    }
    
	public static void createHeader(JSONObject reqHdrJson, Element reqElem) {
		Element sessionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SessionID");
		sessionElem.setTextContent(reqHdrJson.getString(JSON_PROP_SESSIONID));

		Element transactionElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:TransactionID");
		transactionElem.setTextContent(reqHdrJson.getString(JSON_PROP_TRANSACTID));

		Element userElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:UserID");
		userElem.setTextContent(reqHdrJson.getString(JSON_PROP_USERID));
	}
	public static void calculatePricesV4(JSONObject reqJson, JSONObject resJson, JSONObject suppCommResJson, JSONObject clientCommResJson, boolean retainSuppFares, UserContext usrCtx) throws ValidationException {
		calculatePricesV4(reqJson,resJson,suppCommResJson,clientCommResJson,retainSuppFares,usrCtx,null);
	}
    public static void calculatePricesV4(JSONObject reqJson, JSONObject resJson, JSONObject suppCommResJson, JSONObject clientCommResJson, boolean retainSuppFares, UserContext usrCtx,Date cutOffDate) throws ValidationException {
    	Map<String,BigDecimal> paxCountsMap = getPaxCountsFromRequest(reqJson);
        Map<String, String> suppCommToTypeMap = getSupplierCommercialsAndTheirType(suppCommResJson);
        Map<String, String> clntCommToTypeMap = getClientCommercialsAndTheirType(clientCommResJson);
        
        ClientType clientType = ClientType.valueOf(reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
        String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
    	String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
    	
    	//----------------------------------------------------------------------
    	// Retrieve array of pricedItinerary from SI XML converted response JSON
    	JSONArray resPricedItinsJsonArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRICEDITIN);
    	Map<String, JSONArray> ccommSuppBRIJsonMap = getSupplierWiseJourneyDetailsFromClientCommercials(clientCommResJson);
    	Map<String, Integer> suppIndexMap = new HashMap<String, Integer>();
    	
    	// For each pricedItinerary in search results
    	for (int i=0; i < resPricedItinsJsonArr.length(); i++) {
    		JSONObject resPricedItinsJson = resPricedItinsJsonArr.getJSONObject(i);
    		String suppID = resPricedItinsJson.getString(JSON_PROP_SUPPREF);
    		JSONObject resAirItinPricingInfoJson = resPricedItinsJson.getJSONObject(JSON_PROP_AIRPRICEINFO); 
    		
    		// The BRMS client commercials response JSON contains one businessRuleIntake for each supplier. Inside businessRuleIntake, the 
    		// order of each supplier search result is maintained within journeyDetails child array. Therefore, keep track of index for each 
    		// businessRuleIntake.journeyDetails for each supplier.
    		int idx = (suppIndexMap.containsKey(suppID)) ? (suppIndexMap.get(suppID) + 1) : 0;
    		suppIndexMap.put(suppID, idx);
    		JSONObject ccommJrnyDtlsJson = (ccommSuppBRIJsonMap.containsKey(suppID)) ? ccommSuppBRIJsonMap.get(suppID).getJSONObject(idx) : null;
    		if (ccommJrnyDtlsJson == null) {
    			logger.info(String.format("BRMS/ClientComm: \"businessRuleIntake\" JSON element for supplier %s not found.", suppID));
    		}

    		
    		// The following PriceComponentsGroup accepts price subcomponents one-by-one and automatically calculates totals
    		PriceComponentsGroup totalFareCompsGroup = new PriceComponentsGroup(JSON_PROP_ITINTOTALFARE, clientCcyCode, new BigDecimal(0), true);
    		PriceComponentsGroup totalIncentivesGroup = new PriceComponentsGroup(JSON_PROP_INCENTIVES, clientCcyCode, BigDecimal.ZERO, true);
    		JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
    		
    		//Adding clientCommercialInfo
    		JSONArray clientCommercialItinInfoArr= new JSONArray();
    		JSONArray paxTypeFaresJsonArr = resAirItinPricingInfoJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
    		for (int j=0; j < paxTypeFaresJsonArr.length(); j++) {
    			JSONObject paxTypeFareJson = paxTypeFaresJsonArr.getJSONObject(j);
    			JSONObject paxTypeBaseFareJson = paxTypeFareJson.getJSONObject(JSON_PROP_BASEFARE);
    			String paxType = paxTypeFareJson.getString(JSON_PROP_PAXTYPE);
    			BigDecimal paxCount = paxCountsMap.get(paxType);
    			String suppCcyCode = paxTypeBaseFareJson.getString(JSON_PROP_CCYCODE);
    			
    			JSONObject ccommJrnyPsgrDtlJson = getClientCommercialsJourneyDetailsForPassengerType(ccommJrnyDtlsJson, paxType);
    			JSONArray clientEntityCommJsonArr = null;
    			if (ccommJrnyPsgrDtlJson == null) {
    				logger.info(String.format("Passenger type %s details not found client commercial journeyDetails", paxType));
    			}
    			else {
        			// Only in case of reprice when (retainSuppFares == true), retain supplier commercial
        			if (retainSuppFares) {
        				appendSupplierCommercialsToPaxTypeFares(paxTypeFareJson, ccommJrnyPsgrDtlJson, suppID, suppCcyCode, suppCommToTypeMap);
        			}
        			// From the passenger type client commercial JSON, retrieve calculated client entity commercials
        			//JSONArray clientEntityCommJsonArr = ccommJrnyPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
    				clientEntityCommJsonArr = ccommJrnyPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
        			if (clientEntityCommJsonArr == null) {
        				logger.trace("Client commercials calculations not found");
        			}
    			}
    			
    			BigDecimal paxTypeTotalFare = new BigDecimal(0);
    			JSONObject commPriceJson = new JSONObject();
    			//suppPaxTypeFaresJsonArr.put(paxTypeFareJson);

    			// Reference CKIL_323141 - There are three types of client commercials that are 
    			// receivable from clients: Markup, Service Charge (Transaction Fee) & Look-to-Book.
    			// Of these, Markup and Service Charge (Transaction Fee) are transactional and need 
    			// to be considered for selling price.
    			// Reference CKIL_231556 (1.1.2.3/BR04) The display price will be calculated as - 
    			// (Total Supplier Price + Markup + Additional Company Receivable Commercials)
    			
    			JSONObject markupCalcJson = null;
    			JSONArray clientCommercials= new JSONArray();
    			PriceComponentsGroup paxReceivablesCompsGroup = null;
    			PriceComponentsGroup totalPaxReceivablesCompsGroup = null;
    			for (int k = 0; clientEntityCommJsonArr != null && k < clientEntityCommJsonArr.length(); k++) {
    				JSONArray clientEntityCommercialsJsonArr = new JSONArray();
    				JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(k);
    				
    				// TODO: In case of B2B, do we need to add markups for all client hierarchy levels?
    				if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) {
    					markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);
    					clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(markupCalcJson, clntCommToTypeMap, suppCcyCode));
    				}
    				
    		    	
    				//Additional commercialcalc clientCommercial
    				// TODO: In case of B2B, do we need to add additional receivable commercials for all client hierarchy levels?  
    				JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
    				// If totals of receivables at all levels is required, the following instance creation needs to move where
    				// variable 'paxReceivablesCompsGroup' is declared
					paxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode, new BigDecimal(0), true);
					totalPaxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode, new BigDecimal(0), true);
    				if (additionalCommsJsonArr != null) {
    					for(int p=0; p < additionalCommsJsonArr.length(); p++) {
    						JSONObject additionalCommJson = additionalCommsJsonArr.getJSONObject(p);
    						String additionalCommName = additionalCommJson.optString(JSON_PROP_COMMNAME);
    						String additionalCommType = clntCommToTypeMap.get(additionalCommName);
    						clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(additionalCommJson, clntCommToTypeMap, suppCcyCode));
    	    				
    						if (COMM_TYPE_RECEIVABLE.equals(additionalCommType)) {
    							String additionalCommCcy = additionalCommJson.optString(JSON_PROP_COMMCCY,suppCcyCode);
    							BigDecimal additionalCommAmt = additionalCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket,cutOffDate));
    							paxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt);
    							totalPaxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt.multiply(paxCount));
    						}
    					}
    				}

    			
    				
        			// For B2B clients, the incentives of the last client hierarchy level should be accumulated and returned in the response.
        			if (k == (clientEntityCommJsonArr.length() - 1)) {
        				for (int x = 0; x < clientEntityCommercialsJsonArr.length(); x++) {
        					JSONObject clientEntityCommercialsJson = clientEntityCommercialsJsonArr.getJSONObject(x);
        					if (COMM_TYPE_PAYABLE.equals(clientEntityCommercialsJson.getString(JSON_PROP_COMMTYPE))) {
        						String commCcy = clientEntityCommercialsJson.getString(JSON_PROP_COMMCCY);
        						String commName = clientEntityCommercialsJson.getString(JSON_PROP_COMMNAME);
        						BigDecimal commAmt = clientEntityCommercialsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(commCcy, clientCcyCode, clientMarket,cutOffDate)).multiply(paxCount);
        						totalIncentivesGroup.add(String.format(mIncvPriceCompFormat, commName), clientCcyCode, commAmt);
        					}
        				}
        			}
        			
        			//put retention commercial, it need not displayed or calculated, just populate in DB
        			if (retainSuppFares)
        			{
        			JSONArray retentionJsonArr = null;
    				if (clientEntityCommJson.has(JSON_PROP_RETENTIONCOMMDET) && clientEntityCommJson.length()>0) {
    					retentionJsonArr=clientEntityCommJson.getJSONArray(JSON_PROP_RETENTIONCOMMDET);
    					for(int p=0;p<retentionJsonArr.length();p++) {
    						JSONObject retentionCommJson=retentionJsonArr.getJSONObject(p);
    						clientEntityCommercialsJsonArr.put(convertToClientEntityCommercialJson(retentionCommJson, clntCommToTypeMap, suppCcyCode));
    					}
    				}
        			}
    				
    				JSONObject clientEntityDetailsJson = new JSONObject();
    				//JSONArray clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchy();
    				ClientInfo[] clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchyArray();
    				//clientEntityDetailsJson = clientEntityDetailsArr.getJSONObject(k);
    				clientEntityDetailsJson.put(JSON_PROP_CLIENTID, clientEntityDetailsArr[k].getClientId());
    				clientEntityDetailsJson.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsArr[k].getParentClienttId());
    				clientEntityDetailsJson.put(JSON_PROP_COMMENTITYTYPE, clientEntityDetailsArr[k].getCommercialsEntityType());
    				clientEntityDetailsJson.put(JSON_PROP_COMMENTITYID, clientEntityDetailsArr[k].getCommercialsEntityId());
    				clientEntityDetailsJson.put(JSON_PROP_CLIENTCOMM, clientEntityCommercialsJsonArr);
    				clientCommercials.put(clientEntityDetailsJson);
    				
    				
    			}

				//------------------------BEGIN----------------------------------
				BigDecimal baseFareAmt = paxTypeBaseFareJson.getBigDecimal(JSON_PROP_AMOUNT);
				JSONArray ccommTaxDetailsJsonArr = null; 
				if (markupCalcJson != null) {
					JSONObject fareBreakupJson = markupCalcJson.optJSONObject(JSON_PROP_FAREBREAKUP);
					if (fareBreakupJson != null) {
						baseFareAmt = fareBreakupJson.optBigDecimal(JSON_PROP_BASEFARE, baseFareAmt);
						ccommTaxDetailsJsonArr = fareBreakupJson.optJSONArray(JSON_PROP_TAXDETAILS);
					}
				}
				
				commPriceJson.put(JSON_PROP_PAXTYPE, paxTypeFareJson.getString(JSON_PROP_PAXTYPE));
				
				JSONObject baseFareJson = new JSONObject();
				baseFareJson.put(JSON_PROP_AMOUNT, baseFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket,cutOffDate)));
				baseFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);
				commPriceJson.put(JSON_PROP_BASEFARE, baseFareJson);
				paxTypeTotalFare = paxTypeTotalFare.add(baseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
				totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode, baseFareJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
				
				int offset = 0;
				JSONArray paxTypeTaxJsonArr = paxTypeFareJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
				JSONObject taxesJson = getCommercialPricesTaxesJson(paxTypeTaxJsonArr, ccommTaxDetailsJsonArr, offset, totalFareCompsGroup, paxCount, clientCcyCode, clientMarket,cutOffDate);
				commPriceJson.put(JSON_PROP_TAXES,  taxesJson);
				paxTypeTotalFare = paxTypeTotalFare.add(taxesJson.getBigDecimal(JSON_PROP_TOTAL));
				
				offset = paxTypeTaxJsonArr.length();
				JSONArray paxTypeFeeJsonArr = paxTypeFareJson.getJSONObject(JSON_PROP_FEES).getJSONArray(JSON_PROP_FEE);
				JSONObject feesJson = getCommercialPricesFeesJson(paxTypeFeeJsonArr, ccommTaxDetailsJsonArr, offset, totalFareCompsGroup, paxCount, clientCcyCode, clientMarket,cutOffDate);
				commPriceJson.put(JSON_PROP_FEES,  feesJson);
				paxTypeTotalFare = paxTypeTotalFare.add(feesJson.getBigDecimal(JSON_PROP_TOTAL));
				
				// If amount of receivables group is greater than zero, then append to commercial prices
				if (paxReceivablesCompsGroup != null && paxReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
					paxTypeTotalFare = paxTypeTotalFare.add(paxReceivablesCompsGroup.getComponentAmount());
					totalFareCompsGroup.addSubComponent(totalPaxReceivablesCompsGroup, null);
					commPriceJson.put(JSON_PROP_RECEIVABLES, paxReceivablesCompsGroup.toJSON());
				}
				//-------------------------END-----------------------------------

    			JSONObject clientCommercialItinInfoJson= new JSONObject();
    			clientCommercialItinInfoJson.put(JSON_PROP_PAXTYPE, paxType);
    			clientCommercialItinInfoJson.put(JSON_PROP_CLIENTENTITYCOMMS, clientCommercials);
    			clientCommercialItinInfoArr.put(clientCommercialItinInfoJson);
    			
    			suppPaxTypeFaresJsonArr.put(paxTypeFareJson);
    			JSONObject totalFareJson = new JSONObject();
    			totalFareJson.put(JSON_PROP_AMOUNT, paxTypeTotalFare);
    			totalFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);
    			commPriceJson.put(JSON_PROP_TOTALFARE, totalFareJson);
    			
    			paxTypeFaresJsonArr.put(j, commPriceJson);
    		}		
    		// Calculate ItinTotalFare. This fare will be the one used for sorting.
    		resAirItinPricingInfoJson.put(JSON_PROP_ITINTOTALFARE, totalFareCompsGroup.toJSON());
    		
    	
    		if ( clientType == ClientType.B2B) {
    			resAirItinPricingInfoJson.put(JSON_PROP_INCENTIVES, totalIncentivesGroup.toJSON());
    		}
    		//add ssr price
    		
    		JSONArray ssrJsonArr=resAirItinPricingInfoJson.optJSONArray(JSON_PROP_SPECIALSVCREQS);
    		
    		JSONArray supplplierSsrJsonArr=null;
    		BigDecimal ssrTotalAmt=new BigDecimal(0);
    		BigDecimal supplierSsrTotalAmt=new BigDecimal(0);
    		if(ssrJsonArr!=null && ssrJsonArr.length()>0) {
    		 supplplierSsrJsonArr=new JSONArray(new JSONTokener(ssrJsonArr.toString()));
    		String ssrCcyCode=null;
    		for(int l=0;l<ssrJsonArr.length();l++) {
    			JSONObject ssrJson=ssrJsonArr.getJSONObject(l);
    			ssrTotalAmt=ssrTotalAmt.add(ssrJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO));
    			if(ssrJson.has(JSON_PROP_SVCPRC) && ssrJson.getJSONObject(JSON_PROP_SVCPRC).length()>0)
    			{
    				ssrCcyCode=ssrJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_CCYCODE);
    				//change fareComponent to ROEtoo;improve this code make a single method;
    				BigDecimal priceCompAmt=null;
    				String priceCompStr=ssrJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_BASEFARE);
    				if(Utils.isStringNotNullAndNotEmpty(priceCompStr)) {
    					priceCompAmt=(ssrJson.getJSONObject(JSON_PROP_SVCPRC).getBigDecimal(JSON_PROP_BASEFARE)).multiply(RedisRoEData.getRateOfExchange(ssrCcyCode, clientCcyCode, clientMarket,cutOffDate));
    					ssrJson.getJSONObject(JSON_PROP_SVCPRC).put(JSON_PROP_BASEFARE, priceCompAmt.toString());
    				}
    				
    				priceCompStr=ssrJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_TAXES);
    				if(Utils.isStringNotNullAndNotEmpty(priceCompStr)) {
    					priceCompAmt=(ssrJson.getJSONObject(JSON_PROP_SVCPRC).getBigDecimal(JSON_PROP_TAXES)).multiply(RedisRoEData.getRateOfExchange(ssrCcyCode, clientCcyCode, clientMarket,cutOffDate));
    					ssrJson.getJSONObject(JSON_PROP_SVCPRC).put(JSON_PROP_TAXES, priceCompAmt.toString());
    				}
    				
    				priceCompStr=ssrJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_FEES);
    				if(Utils.isStringNotNullAndNotEmpty(priceCompStr)) {
    					priceCompAmt=(ssrJson.getJSONObject(JSON_PROP_SVCPRC).getBigDecimal(JSON_PROP_FEES)).multiply(RedisRoEData.getRateOfExchange(ssrCcyCode, clientCcyCode, clientMarket,cutOffDate));
    					ssrJson.getJSONObject(JSON_PROP_SVCPRC).put(JSON_PROP_FEES, priceCompAmt.toString());
    				}
    				
    				
    				ssrJson.getJSONObject(JSON_PROP_SVCPRC).put(JSON_PROP_CCYCODE,clientCcyCode);
    			}
    				
    			
    		}
    		if(!ssrTotalAmt.equals(BigDecimal.ZERO))
    		{
    			supplierSsrTotalAmt=ssrTotalAmt;
    			ssrTotalAmt=ssrTotalAmt.multiply(RedisRoEData.getRateOfExchange(ssrCcyCode, clientCcyCode, clientMarket,cutOffDate));
    			resAirItinPricingInfoJson.getJSONObject(JSON_PROP_ITINTOTALFARE).put(JSON_PROP_AMOUNT, (resAirItinPricingInfoJson.getJSONObject(JSON_PROP_ITINTOTALFARE).getBigDecimal(JSON_PROP_AMOUNT)).add(ssrTotalAmt));
    			
    		}
    		}
    		
    		// The supplier fares will be retained only in reprice operation. In reprice, after calculations, supplier
    		// prices are saved in Redis cache to be used im book operation.
    		if (retainSuppFares) {
	    		JSONObject suppItinPricingInfoJson  = new JSONObject();
	    		JSONObject suppItinTotalFareJson = new JSONObject();
	    		if(ssrJsonArr!=null && ssrJsonArr.length()>0) {
	    			suppItinTotalFareJson.put(JSON_PROP_SPECIALSVCREQS,supplplierSsrJsonArr);
	    			suppItinTotalFareJson.put(JSON_PROP_SSRAMT,supplierSsrTotalAmt);
	    		}
	    		suppItinPricingInfoJson.put(JSON_PROP_ITINTOTALFARE, suppItinTotalFareJson);
	    		suppItinPricingInfoJson.put(JSON_PROP_PAXTYPEFARES, suppPaxTypeFaresJsonArr);
	    		resPricedItinsJson.put(JSON_PROP_CLIENTCOMMITININFO, clientCommercialItinInfoArr);
	    		resPricedItinsJson.put(JSON_PROP_SUPPPRICEINFO, suppItinPricingInfoJson);
	    		addSupplierItinTotalFare(resPricedItinsJson, paxCountsMap);
    		}    		 
    	}
    }
     
    private static void appendSupplierCommercialsToPaxTypeFaresV2(JSONObject paxTypeFareJson,
			JSONObject ccommJrnyPsgrDtlJson, String suppID, String suppCcyCode, Map<String, String> suppCommToTypeMap, Boolean isSupplierGDS, String enablerSupplierID, String sourceSuppID) {
    
    	JSONArray suppCommJsonArr = paxTypeFareJson.has(JSON_PROP_SUPPCOMM)?paxTypeFareJson.getJSONArray(JSON_PROP_SUPPCOMM): new JSONArray();
		JSONArray ccommSuppCommJsonArr = ccommJrnyPsgrDtlJson.optJSONArray(JSON_PROP_COMMDETAILS);
		//JSONArray ccommSuppEntityCommJsonArr = ccommJrnyPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
		
		// If no supplier commercials have been defined in BRMS, the JSONArray for ccommSuppCommJsonArr will be null.
		// In this case, log a message and proceed with other calculations.
		if (ccommSuppCommJsonArr == null) {
			logger.warn(String.format("No supplier commercials found for supplier %s", suppID));
			return;
		}

		for (int x=0; x < ccommSuppCommJsonArr.length(); x++) {
			JSONObject ccommSuppCommJson = ccommSuppCommJsonArr.getJSONObject(x);
			JSONObject suppCommJson = new JSONObject();
			suppCommJson.put(JSON_PROP_COMMNAME, ccommSuppCommJson.getString(JSON_PROP_COMMNAME));
			suppCommJson.put(JSON_PROP_COMMTYPE, suppCommToTypeMap.get(ccommSuppCommJson.getString(JSON_PROP_COMMNAME)));
			suppCommJson.put(JSON_PROP_COMMAMOUNT, ccommSuppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
			suppCommJson.put(JSON_PROP_COMMCCY, ccommSuppCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
			suppCommJson.put(JSON_PROP_MDMRULEID,ccommSuppCommJson.optString("mdmruleId", ""));
			suppCommJson.put(JSON_PROP_COMMCALCPCT,ccommSuppCommJson.optString(JSON_PROP_COMMCALCPCT, ""));
			suppCommJson.put(JSON_PROP_COMMCALCAMT,ccommSuppCommJson.optString(JSON_PROP_COMMCALCAMT, ""));
			suppCommJson.put(JSON_PROP_COMMFARECOMP,ccommSuppCommJson.optString(JSON_PROP_COMMFARECOMP, ""));
			suppCommJson.put(JSON_PROP_SUPPID,suppID);
			if(isSupplierGDS) {
				if(Utils.isStringNotNullAndNotEmpty(enablerSupplierID)) {
					suppCommJson.put(JSON_PROP_ENABLERSUPPID,enablerSupplierID);
					if(Utils.isStringNotNullAndNotEmpty(sourceSuppID)) {
						suppCommJson.put(JSON_PROP_SOURCESUPPID,sourceSuppID);
					}
					else {
						suppCommJson.put(JSON_PROP_SOURCESUPPID,"");
					}
					
				}
			
			}
			suppCommJsonArr.put(suppCommJson);
		}
		paxTypeFareJson.put(JSON_PROP_SUPPCOMM, suppCommJsonArr);
		
	}

 public static void calculatePricesV5(JSONObject reqJson, JSONObject resJson, JSONObject suppCommResJson, JSONObject clientCommResJson, boolean retainSuppFares, UserContext usrCtx,List<Integer> sourceSupplierCommIndexes) throws Exception {
		calculatePricesV5(reqJson,resJson,suppCommResJson,clientCommResJson,retainSuppFares,usrCtx,null, sourceSupplierCommIndexes);
	}

   public static void calculatePricesV5(JSONObject reqJson, JSONObject resJson, JSONObject suppCommResJson, JSONObject clientCommResJson, boolean retainSuppFares, UserContext usrCtx,Date cutOffDate,List<Integer> sourceSupplierCommIndexes) throws Exception {
    	
    	Map<String,BigDecimal> paxCountsMap = getPaxCountsFromRequest(reqJson);
        Map<String, String> suppCommToTypeMap = getSupplierCommercialsAndTheirType(suppCommResJson);
        Map<String,Map<String,JSONObject>> suppIDentitycommToTypeMap = getClientCommercialsAndTheirTypeV2(clientCommResJson);
        
        ClientType clientType = ClientType.valueOf(reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTTYPE));
        String clientMarket = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTMARKET);
    	String clientCcyCode = reqJson.getJSONObject(JSON_PROP_REQHEADER).getJSONObject(JSON_PROP_CLIENTCONTEXT).getString(JSON_PROP_CLIENTCCY);
    	
    	JSONArray resPricedItinsJsonArr = resJson.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRICEDITIN);
    	Map<String, JSONArray> ccommSuppBRIJsonMap = getSupplierWiseJourneyDetailsFromClientCommercials(clientCommResJson);
    	//System.out.println("ccommSuppBRIJsonMap"+ccommSuppBRIJsonMap.toString());
    	Map<String, Integer> suppIndexMap = new HashMap<String, Integer>();

    	for (int i=0; i < resPricedItinsJsonArr.length(); i++) {
    		
    		JSONObject resPricedItinsJson = resPricedItinsJsonArr.getJSONObject(i);
    		String suppID = resPricedItinsJson.getString(JSON_PROP_SUPPREF);
    		JSONObject resAirItinPricingInfoJson = resPricedItinsJson.getJSONObject(JSON_PROP_AIRPRICEINFO); 
    		
    		ProductSupplier prodSupplier = usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID);
			if (prodSupplier == null) {
				throw new Exception(String.format("Product supplier %s not found for user/client", suppID));
			}
    		
    		String sourceSuppID=null;
    		int sourceIdx=0;
    		int idx = (suppIndexMap.containsKey(suppID)) ? (suppIndexMap.get(suppID) + 1) : 0;
    		suppIndexMap.put(suppID, idx);
    		Boolean isSupplierGDS=prodSupplier.isGDS();
    		String enablerSupplierID=null;
    		if(isSupplierGDS.equals(true)) {
    			suppIndexMap.put(sourceSuppID, idx);
    			enablerSupplierID=suppID;
    			String sourceSuppAirlineCode=resPricedItinsJson.getJSONObject(JSON_PROP_AIRITINERARY).getJSONArray(JSON_PROP_ORIGDESTOPTS).getJSONObject(0).getJSONArray(JSON_PROP_FLIGHTSEG)
    					.getJSONObject(0).getJSONObject(JSON_PROP_OPERAIRLINE).getString(JSON_PROP_AIRLINECODE);
    			
    		//	sourceSuppID=usrCtx.getSuppIdFromIATACode(sourceSuppAirlineCode);
    			
    			 org.bson.Document suppCommDoc = MDMUtils.getSupplierCommercialsFromIATACode(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, sourceSuppAirlineCode,usrCtx.getClientCompanyId());
          	      if (suppCommDoc == null) {
        				logger.trace(String.format("Supplier commercials definition for productCategory=%s, productCategorySubType=%s and SupplierId=%s not found during setting SuppComm RQ for GDS",PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID));
        			}
          	        org.bson.Document commDefData= (org.bson.Document) MDMUtils.getValueObjectAtPathFromDocument(suppCommDoc, MDM_PROP_SUPPCOMMDATA.concat(".").concat(MDM_PROP_COMMERCIALDEFN));
     				if(commDefData==null || commDefData.size()==0) {
     					logger.trace(String.format("Supplier standard commercial for productCategory=%s, productCategorySubType=%s, SupplierId=%s", PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT, suppID));
     				}
     				sourceSuppID =MDMUtils.getValueAtPathFromDocument(commDefData, MDM_PROP_SUPPID);
    	}
    		// The BRMS client commercials response JSON contains one businessRuleIntake for each supplier. Inside businessRuleIntake, the 
    		// order of each supplier search result is maintained within journeyDetails child array. Therefore, keep track of index for each 
    		// businessRuleIntake.journeyDetails for each supplier.
    		
    		
    		
    		
    		JSONObject ccommJrnyDtlsJson = (ccommSuppBRIJsonMap.containsKey(suppID)) ? ccommSuppBRIJsonMap.get(suppID).getJSONObject(idx) : null;
    		if (ccommJrnyDtlsJson == null) {
    			logger.info(String.format("BRMS/ClientComm: \"businessRuleIntake\" JSON element for supplier %s not found.", suppID));
    		}
    		JSONObject sourceCcommJrnyDtlsJson =null;
    		if(Utils.isStringNotNullAndNotEmpty(sourceSuppID) && !sourceSupplierCommIndexes.isEmpty() && sourceSupplierCommIndexes.contains(i) ) {
    		 sourceCcommJrnyDtlsJson = (ccommSuppBRIJsonMap.containsKey(sourceSuppID)) ? ccommSuppBRIJsonMap.get(sourceSuppID).getJSONObject(sourceIdx) : null;
    		 sourceIdx++;
    		}
    		
    		// The following PriceComponentsGroup accepts price subcomponents one-by-one and automatically calculates totals
    		PriceComponentsGroup totalFareCompsGroup = new PriceComponentsGroup(JSON_PROP_ITINTOTALFARE, clientCcyCode, new BigDecimal(0), true);
    		PriceComponentsGroup totalIncentivesGroup = new PriceComponentsGroup(JSON_PROP_INCENTIVES, clientCcyCode, BigDecimal.ZERO, true);
    		JSONArray suppPaxTypeFaresJsonArr = new JSONArray();
    		
    		//Adding clientCommercialInfo
    		JSONArray clientCommercialItinInfoArr= new JSONArray();
    		JSONArray paxTypeFaresJsonArr = resAirItinPricingInfoJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
    		
    		for (int j=0; j < paxTypeFaresJsonArr.length(); j++) {
    			
    			JSONObject paxTypeFareJson = paxTypeFaresJsonArr.getJSONObject(j);
    			JSONObject paxTypeBaseFareJson = paxTypeFareJson.getJSONObject(JSON_PROP_BASEFARE);
    			String paxType = paxTypeFareJson.getString(JSON_PROP_PAXTYPE);
    			BigDecimal paxCount = paxCountsMap.get(paxType);
    			String suppCcyCode = paxTypeBaseFareJson.getString(JSON_PROP_CCYCODE);
    			
    			JSONObject ccommJrnyPsgrDtlJson = getClientCommercialsJourneyDetailsForPassengerType(ccommJrnyDtlsJson, paxType);
    			JSONObject sourceCcommJrnyPsgrDtlJson=null;
    			
    			JSONArray clientEntityCommJsonArr = null;
    			if (ccommJrnyPsgrDtlJson == null) {
    				logger.info(String.format("Passenger type %s details not found client commercial journeyDetails", paxType));
    			}
    			else {
        			// Only in case of reprice when (retainSuppFares == true), retain supplier commercial
        			if (retainSuppFares) {
        				appendSupplierCommercialsToPaxTypeFaresV2(paxTypeFareJson, ccommJrnyPsgrDtlJson, suppID, suppCcyCode, suppCommToTypeMap,isSupplierGDS,enablerSupplierID,sourceSuppID);
        			}
        			// From the passenger type client commercial JSON, retrieve calculated client entity commercials
        			//JSONArray clientEntityCommJsonArr = ccommJrnyPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
    				clientEntityCommJsonArr = ccommJrnyPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
        			if (clientEntityCommJsonArr == null) {
        				logger.trace("Client commercials calculations not found");
        			}
    			}
    			
    			JSONArray sourceClientEntityCommJsonArr = null;
    			if(sourceCcommJrnyDtlsJson!=null) {
    				 sourceCcommJrnyPsgrDtlJson = getClientCommercialsJourneyDetailsForPassengerType(sourceCcommJrnyDtlsJson, paxType);
        			 sourceClientEntityCommJsonArr = null;
        			if (sourceCcommJrnyPsgrDtlJson == null) {
        				logger.info(String.format("Passenger type %s details not found client commercial journeyDetails", paxType));
        			}
        			else {
            			// Only in case of reprice when (retainSuppFares == true), retain supplier commercial
            			if (retainSuppFares) {
            				appendSupplierCommercialsToPaxTypeFaresV2(paxTypeFareJson, sourceCcommJrnyPsgrDtlJson, sourceSuppID, suppCcyCode, suppCommToTypeMap,isSupplierGDS,enablerSupplierID,sourceSuppID);
            			}
            			// From the passenger type client commercial JSON, retrieve calculated client entity commercials
            			//JSONArray clientEntityCommJsonArr = ccommJrnyPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
        				sourceClientEntityCommJsonArr = sourceCcommJrnyPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
            			if (sourceClientEntityCommJsonArr == null) {
            				logger.trace("Client commercials calculations not found");
            			}
        			}
        			
        			
    			}
    			
    			BigDecimal paxTypeTotalFare = new BigDecimal(0);
    			JSONObject commPriceJson = new JSONObject();
    			//suppPaxTypeFaresJsonArr.put(paxTypeFareJson);

    			// Reference CKIL_323141 - There are three types of client commercials that are 
    			// receivable from clients: Markup, Service Charge (Transaction Fee) & Look-to-Book.
    			// Of these, Markup and Service Charge (Transaction Fee) are transactional and need 
    			// to be considered for selling price.
    			// Reference CKIL_231556 (1.1.2.3/BR04) The display price will be calculated as - 
    			// (Total Supplier Price + Markup + Additional Company Receivable Commercials)
    			
    			JSONObject markupCalcJson = null;
    			JSONObject sourceMarkupCalcJson = null;
    			JSONArray clientCommercials= new JSONArray();
    			PriceComponentsGroup paxReceivablesCompsGroup = null;
    			PriceComponentsGroup totalPaxReceivablesCompsGroup = null;
    			Map<String,JSONObject> clntCommToTypeMap1=null;
    			Map<String,JSONObject> clntCommToTypeMap2=null;
    			
    			//Entity commercial array length will be same since the entities passed in both enabler and source suppleir will be the same
    			//fix this
    			if((clientEntityCommJsonArr!=null && clientEntityCommJsonArr.length()>0)) {
    				
    				JSONArray selectedArray=null;
    				selectedArray=greaterJsonArr(sourceClientEntityCommJsonArr,clientEntityCommJsonArr);
    				for(int k=0,l=0;k<selectedArray.length();k++,l++) {
    					
    					//fix this
    					
    					JSONArray clientEntityCommercialsJsonArr = new JSONArray();
    					JSONArray clientEntityCommercialsJsonArrForKafka = new JSONArray();
        				JSONObject clientEntityCommJson = clientEntityCommJsonArr.getJSONObject(k);
        				JSONObject sourceClientEntityCommJson=null;
        				if(sourceClientEntityCommJsonArr!=null ) {
        					 sourceClientEntityCommJson = sourceClientEntityCommJsonArr.optJSONObject(l);
        				}
        				
        				JSONObject clientCommercial= null;
        				JSONObject kafkaClientCommercial= null;
        				
        				//markup begin
        			
        				if (clientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS)) {
        					BigDecimal clientCommercialTotalAmt=new BigDecimal(0);
        					markupCalcJson = clientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);
        					 clntCommToTypeMap1=suppIDentitycommToTypeMap.get(suppID.concat("|").concat(clientEntityCommJson.getString(JSON_PROP_ENTITYNAME)));
        					JSONObject clientCommHead1=clntCommToTypeMap1.get(markupCalcJson.getString(JSON_PROP_COMMNAME));	
        					clientCommercial=convertToClientEntityCommercialJsonV2(markupCalcJson, clientCommHead1, suppCcyCode,suppID,true,isSupplierGDS,enablerSupplierID,sourceSuppID);
        					kafkaClientCommercial=new JSONObject( new JSONTokener(clientCommercial.toString()));
        					
        					
        					BigDecimal clientCommercialAmt1=clientCommercial.getBigDecimal(JSON_PROP_COMMAMOUNT);
        					
        					if (sourceClientEntityCommJson!=null && sourceClientEntityCommJson.length()>0 && sourceClientEntityCommJson.has(JSON_PROP_MARKUPCOMMDTLS))
        					{	
        						sourceMarkupCalcJson = sourceClientEntityCommJson.optJSONObject(JSON_PROP_MARKUPCOMMDTLS);
        					    clntCommToTypeMap2=suppIDentitycommToTypeMap.get(sourceSuppID.concat("|").concat(clientEntityCommJson.getString(JSON_PROP_ENTITYNAME)));
            					JSONObject clientCommHead2=clntCommToTypeMap2.get(sourceMarkupCalcJson.getString(JSON_PROP_COMMNAME));
            				/*	clientCommercial=convertToClientEntityCommercialJsonV2(markupCalcJson, clientCommHead2, suppCcyCode);
            					BigDecimal clientCommercialAmt2=clientCommercial.getBigDecimal(JSON_PROP_COMMAMOUNT);*/
            					kafkaClientCommercial.put(JSON_PROP_ISAPPLICABLE,isClientCommercialApplicable(kafkaClientCommercial,clientCommHead1,clientCommHead2));	
            					if(k==selectedArray.length()-1) {
            						clientEntityCommercialsJsonArrForKafka.put(convertToClientEntityCommercialJsonV2(sourceMarkupCalcJson, clientCommHead2, suppCcyCode,sourceSuppID
            								,isSourceClientCommercialApplicable(sourceMarkupCalcJson, clientCommHead2, clientCommHead1),isSupplierGDS,enablerSupplierID,sourceSuppID));
            					}
            					
            					BigDecimal clientCommercialAmt2=sourceMarkupCalcJson.getBigDecimal(JSON_PROP_COMMAMOUNT);
            					clientCommercialTotalAmt=compareAndCalculcateCommercialPrice(clientCommHead1,clientCommercialAmt1,clientCommHead2,clientCommercialAmt2);
            					clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommercialTotalAmt);
        					}
        					if(k==selectedArray.length()-1) {
        						clientEntityCommercialsJsonArrForKafka.put(kafkaClientCommercial);
        					}
        					else {
        						sourceMarkupCalcJson=null;
        					}
        					
        					if(clientCommercial!=null && (k==selectedArray.length()-1)) {
            					clientEntityCommercialsJsonArr.put(clientCommercial);
            				}
        					
        				}		
        				
        				//markup end
        				
        				//Additional commercialcalc clientCommercial begin
        				// TODO: In case of B2B, do we need to add additional receivable commercials for all client hierarchy levels?  
        				JSONArray additionalCommsJsonArr = clientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
        				
        				JSONArray sourceAdditionalCommsJsonArr =null;
        				Map<String,JSONObject> sourceAddComMap=new HashMap<String,JSONObject>();
        				if(sourceClientEntityCommJson!=null && sourceClientEntityCommJson.length()>0 && sourceClientEntityCommJson.has(JSON_PROP_ADDCOMMDETAILS)) {
        					 sourceAdditionalCommsJsonArr = sourceClientEntityCommJson.optJSONArray(JSON_PROP_ADDCOMMDETAILS);
        					 sourceAddComMap=createSourceCommsJsonArrMap(sourceAddComMap,sourceAdditionalCommsJsonArr);
        				}
        				
        				paxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode, new BigDecimal(0), true);
    					totalPaxReceivablesCompsGroup = new PriceComponentsGroup(JSON_PROP_RECEIVABLES, clientCcyCode, new BigDecimal(0), true);
    					
    					if (additionalCommsJsonArr != null) {
    						
        					for(int p=0; p < additionalCommsJsonArr.length(); p++) {
        						BigDecimal clientCommercialTotalAmt=new BigDecimal(0);
        						JSONObject additionalCommJson = additionalCommsJsonArr.getJSONObject(p);
        						String additionalCommName = additionalCommJson.optString(JSON_PROP_COMMNAME);
        					    clntCommToTypeMap1=suppIDentitycommToTypeMap.get(suppID.concat("|").concat(clientEntityCommJson.getString(JSON_PROP_ENTITYNAME)));
            					JSONObject clientCommHead1=clntCommToTypeMap1.get(additionalCommJson.getString(JSON_PROP_COMMNAME));	
            					String additionalCommType = clientCommHead1.optString(JSON_PROP_COMMTYPE);
            					clientCommercial=convertToClientEntityCommercialJsonV2(additionalCommJson, clientCommHead1, suppCcyCode,suppID,true,isSupplierGDS,enablerSupplierID,sourceSuppID);
            					kafkaClientCommercial=new JSONObject( new JSONTokener(clientCommercial.toString()));
            					//clientEntityCommercialsJsonArrForKafka.put(clientCommercial);
            					BigDecimal clientCommercialAmt1=clientCommercial.getBigDecimal(JSON_PROP_COMMAMOUNT);
        						
        						//is additionalCommType check needed
        						if(sourceAddComMap.size()>0 && sourceAddComMap.containsKey(additionalCommName)) {
        							JSONObject sourceAdditionalComm=sourceAddComMap.get(additionalCommName);
        						    clntCommToTypeMap2=suppIDentitycommToTypeMap.get(sourceSuppID.concat("|").concat(clientEntityCommJson.getString(JSON_PROP_ENTITYNAME)));
                					JSONObject clientCommHead2=clntCommToTypeMap2.get(additionalCommName);
        							BigDecimal clientCommercialAmt2=sourceAdditionalComm.getBigDecimal(JSON_PROP_COMMAMOUNT);
        							kafkaClientCommercial.put(JSON_PROP_ISAPPLICABLE,isClientCommercialApplicable(kafkaClientCommercial,clientCommHead1,clientCommHead2));	
        							clientEntityCommercialsJsonArrForKafka.put((convertToClientEntityCommercialJsonV2(sourceAdditionalComm, clientCommHead2, suppCcyCode,sourceSuppID
            								,isSourceClientCommercialApplicable(sourceAdditionalComm, clientCommHead2, clientCommHead1),isSupplierGDS,enablerSupplierID,sourceSuppID)));
        							clientCommercialTotalAmt=compareAndCalculcateCommercialPrice(clientCommHead1,clientCommercialAmt1,clientCommHead2,clientCommercialAmt2);
        							clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommercialTotalAmt);
        							
        						}
        						clientEntityCommercialsJsonArrForKafka.put(kafkaClientCommercial);
        						if (COMM_TYPE_RECEIVABLE.equals(additionalCommType)) {
        							String additionalCommCcy = additionalCommJson.optString(JSON_PROP_COMMCCY,suppCcyCode);
        							BigDecimal additionalCommAmt = clientCommercial.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(additionalCommCcy, clientCcyCode, clientMarket,cutOffDate));
        							paxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt);
        							totalPaxReceivablesCompsGroup.add(JSON_PROP_RECEIVABLE.concat(".").concat(additionalCommName).concat("@").concat(JSON_PROP_CODE), clientCcyCode, additionalCommAmt.multiply(paxCount));
        						}
        						if(clientCommercial!=null) {
                					clientEntityCommercialsJsonArr.put(clientCommercial);
                				}
        					}
        				}
    			
    					
    					
        				
        				//Additional commercialcalc clientCommercial end
    					
    					
    					//incentives commercialcalc clientCommercial begin
    					// For B2B clients, the incentives of the last client hierarchy level should be accumulated and returned in the response.
            			if (k == (clientEntityCommJsonArr.length() - 1)) {
            				for (int x = 0; x < clientEntityCommercialsJsonArr.length(); x++) {
            					JSONObject clientEntityCommercialsJson = clientEntityCommercialsJsonArr.getJSONObject(x);
            					if (COMM_TYPE_PAYABLE.equals(clientEntityCommercialsJson.getString(JSON_PROP_COMMTYPE))) {
            						String commCcy = clientEntityCommercialsJson.getString(JSON_PROP_COMMCCY);
            						String commName = clientEntityCommercialsJson.getString(JSON_PROP_COMMNAME);
            						BigDecimal commAmt = clientEntityCommercialsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(RedisRoEData.getRateOfExchange(commCcy, clientCcyCode, clientMarket,cutOffDate)).multiply(paxCount);
            						totalIncentivesGroup.add(String.format(mIncvPriceCompFormat, commName), clientCcyCode, commAmt);
            					}
            				}
            			}
            			//incentives commercialcalc clientCommercial end
            			
            			//put retention commercial, it need not displayed or calculated, just populate in DB
            			//retention begin
            			JSONArray sourcerRetentionCommsJsonArr =null;
        				Map<String,JSONObject> sourceRetenComMap=new HashMap<String,JSONObject>();
        				if(sourceClientEntityCommJson!=null && sourceClientEntityCommJson.length()>0 && sourceClientEntityCommJson.has(JSON_PROP_RETENTIONCOMMDET)) {
        					sourcerRetentionCommsJsonArr = sourceClientEntityCommJson.optJSONArray(JSON_PROP_RETENTIONCOMMDET);
        					 sourceRetenComMap=createSourceCommsJsonArrMap(sourceRetenComMap,sourcerRetentionCommsJsonArr);
        				}
        				
        				
            			
            			if (retainSuppFares)
            			{
            			JSONArray retentionJsonArr = null;
        				if (clientEntityCommJson.has(JSON_PROP_RETENTIONCOMMDET) && clientEntityCommJson.length()>0) {
        					retentionJsonArr=clientEntityCommJson.getJSONArray(JSON_PROP_RETENTIONCOMMDET);
        					for(int p=0;p<retentionJsonArr.length();p++) {
        						BigDecimal clientCommercialTotalAmt=new BigDecimal(0);
        						JSONObject retentionCommJson=retentionJsonArr.getJSONObject(p);
        					    clntCommToTypeMap1=suppIDentitycommToTypeMap.get(suppID.concat("|").concat(clientEntityCommJson.getString(JSON_PROP_ENTITYNAME)));
            					JSONObject clientCommHead1=clntCommToTypeMap1.get(retentionCommJson.getString(JSON_PROP_COMMNAME));
        						clientCommercial=convertToClientEntityCommercialJsonV2(retentionCommJson, clientCommHead1, suppCcyCode,suppID,true,isSupplierGDS,enablerSupplierID,sourceSuppID);
        						
        						kafkaClientCommercial=new JSONObject( new JSONTokener(clientCommercial.toString()));
        						clientEntityCommercialsJsonArrForKafka.put(kafkaClientCommercial);
        						BigDecimal clientCommercialAmt1=clientCommercial.getBigDecimal(JSON_PROP_COMMAMOUNT);
        						
        						if(sourceClientEntityCommJson!=null && sourceClientEntityCommJson.length()>0 && sourceClientEntityCommJson.has(JSON_PROP_RETENTIONCOMMDET)){
        							JSONObject sourceAdditionalComm=sourceRetenComMap.get(retentionCommJson.getString(JSON_PROP_COMMNAME));
        						    clntCommToTypeMap2=suppIDentitycommToTypeMap.get(sourceSuppID.concat("|").concat(clientEntityCommJson.getString(JSON_PROP_ENTITYNAME)));
                					JSONObject clientCommHead2=clntCommToTypeMap2.get(retentionCommJson.getString(JSON_PROP_COMMNAME));
                					kafkaClientCommercial.put(JSON_PROP_ISAPPLICABLE,isClientCommercialApplicable(kafkaClientCommercial,clientCommHead1,clientCommHead2));	
        							clientEntityCommercialsJsonArrForKafka.put((convertToClientEntityCommercialJsonV2(sourceAdditionalComm, clientCommHead2, suppCcyCode,sourceSuppID
            								,isSourceClientCommercialApplicable(sourceAdditionalComm, clientCommHead2, clientCommHead1),isSupplierGDS,enablerSupplierID,sourceSuppID)));
                					//clientEntityCommercialsJsonArrForKafka.put(convertToClientEntityCommercialJsonV2(markupCalcJson, clientCommHead1, suppCcyCode,sourceSuppID,false));
        							BigDecimal clientCommercialAmt2=sourceAdditionalComm.getBigDecimal(JSON_PROP_COMMAMOUNT);
        							clientCommercialTotalAmt=compareAndCalculcateCommercialPrice(clientCommHead1,clientCommercialAmt1,clientCommHead2,clientCommercialAmt2);
        							clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommercialTotalAmt);
        						}
        						clientEntityCommercialsJsonArrForKafka.put(clientCommercial);
        						
        						if(clientCommercial!=null) {
                					clientEntityCommercialsJsonArr.put(clientCommercial);
                				}
        					
        					}
        				}
        				
        				//retention end
        				
        				//fixed bEgin
        				
        				
        				JSONArray sourceFixedJsonArr = null;
        				Map<String,JSONObject> sourceFixedComMap=new HashMap<String,JSONObject>();
        				if(sourceClientEntityCommJson!=null && sourceClientEntityCommJson.length()>0 && sourceClientEntityCommJson.has(JSON_PROP_FIXEDCOMMDETAILS)) {
        					sourceFixedJsonArr = sourceClientEntityCommJson.optJSONArray(JSON_PROP_FIXEDCOMMDETAILS);
        					 sourceRetenComMap=createSourceCommsJsonArrMap(sourceFixedComMap,sourceFixedJsonArr);
        				}
        				
        				JSONArray fixedJsonArr = null;
        				if (clientEntityCommJson.has(JSON_PROP_FIXEDCOMMDETAILS) && clientEntityCommJson.length()>0) {
        					fixedJsonArr=clientEntityCommJson.getJSONArray(JSON_PROP_FIXEDCOMMDETAILS);
        					for(int p=0;p<fixedJsonArr.length();p++) {
        						BigDecimal clientCommercialTotalAmt=new BigDecimal(0);
        						JSONObject fixedCommJson=fixedJsonArr.getJSONObject(p);
        					    clntCommToTypeMap1=suppIDentitycommToTypeMap.get(suppID.concat("|").concat(clientEntityCommJson.getString(JSON_PROP_ENTITYNAME)));
            					JSONObject clientCommHead1=clntCommToTypeMap1.get(fixedCommJson.getString(JSON_PROP_COMMNAME));
        						clientCommercial=convertToClientEntityCommercialJsonV2(fixedCommJson, clientCommHead1, suppCcyCode,suppID,true,isSupplierGDS,enablerSupplierID,sourceSuppID);
        						
        						kafkaClientCommercial=new JSONObject( new JSONTokener(clientCommercial.toString()));
        						clientEntityCommercialsJsonArrForKafka.put(kafkaClientCommercial);
        						BigDecimal clientCommercialAmt1=clientCommercial.getBigDecimal(JSON_PROP_COMMAMOUNT);
        						
        						if(sourceClientEntityCommJson!=null && sourceClientEntityCommJson.length()>0 && sourceClientEntityCommJson.has(JSON_PROP_RETENTIONCOMMDET)){
        							JSONObject sourceAdditionalComm=sourceRetenComMap.get(fixedCommJson.getString(JSON_PROP_COMMNAME));
        						    clntCommToTypeMap2=suppIDentitycommToTypeMap.get(sourceSuppID.concat("|").concat(clientEntityCommJson.getString(JSON_PROP_ENTITYNAME)));
                					JSONObject clientCommHead2=clntCommToTypeMap2.get(fixedCommJson.getString(JSON_PROP_COMMNAME));
                					kafkaClientCommercial.put(JSON_PROP_ISAPPLICABLE,isClientCommercialApplicable(kafkaClientCommercial,clientCommHead1,clientCommHead2));	
        							clientEntityCommercialsJsonArrForKafka.put((convertToClientEntityCommercialJsonV2(sourceAdditionalComm, clientCommHead2, suppCcyCode,sourceSuppID
            								,isSourceClientCommercialApplicable(sourceAdditionalComm, clientCommHead2, clientCommHead1),isSupplierGDS,enablerSupplierID,sourceSuppID)));
                					//clientEntityCommercialsJsonArrForKafka.put(convertToClientEntityCommercialJsonV2(markupCalcJson, clientCommHead1, suppCcyCode,sourceSuppID,false));
        							BigDecimal clientCommercialAmt2=sourceAdditionalComm.getBigDecimal(JSON_PROP_COMMAMOUNT);
        							clientCommercialTotalAmt=compareAndCalculcateCommercialPrice(clientCommHead1,clientCommercialAmt1,clientCommHead2,clientCommercialAmt2);
        							clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommercialTotalAmt);
        						}
        						clientEntityCommercialsJsonArrForKafka.put(clientCommercial);
        						
        						if(clientCommercial!=null) {
                					clientEntityCommercialsJsonArr.put(clientCommercial);
                				}
        					
        					}
        				}
        				//fixed End
        				
        				
            			}
            			
            			
            			//Fixed Begin
            			
            			//Fixed End
            			
            			JSONObject clientEntityDetailsJson = new JSONObject();
        				//JSONArray clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchy();
        				ClientInfo[] clientEntityDetailsArr = usrCtx.getClientCommercialsHierarchyArray();
        				//clientEntityDetailsJson = clientEntityDetailsArr.getJSONObject(k);
        				clientEntityDetailsJson.put(JSON_PROP_CLIENTID, clientEntityDetailsArr[k].getClientId());
        				clientEntityDetailsJson.put(JSON_PROP_PARENTCLIENTID, clientEntityDetailsArr[k].getParentClienttId());
        				clientEntityDetailsJson.put(JSON_PROP_COMMENTITYTYPE, clientEntityDetailsArr[k].getCommercialsEntityType());
        				clientEntityDetailsJson.put(JSON_PROP_COMMENTITYID, clientEntityDetailsArr[k].getCommercialsEntityId());
        				clientEntityDetailsJson.put(JSON_PROP_CLIENTCOMM, clientEntityCommercialsJsonArrForKafka);
        				//clientEntityDetailsJson.put(JSON_PROP_CLIENTCOMM, clientEntityCommercialsJsonArr);
        				clientCommercials.put(clientEntityDetailsJson);
            			
        				
    				}
    			}
    				//------------------------BEGIN----------------------------------
    				BigDecimal baseFareAmt = paxTypeBaseFareJson.getBigDecimal(JSON_PROP_AMOUNT);
    				BigDecimal sourceBaseFareAmt = baseFareAmt;
    				JSONArray ccommTaxDetailsJsonArr = null; 
    				JSONArray sourceCcommTaxDetailsJsonArr = null; 
    				//sourceMarkupCalcJson
    				//check byproduct for markup
    				BigDecimal markupAmt=null;
    				if (markupCalcJson != null && clntCommToTypeMap1!=null) {
    					JSONObject fareBreakupJson = markupCalcJson.optJSONObject(JSON_PROP_FAREBREAKUP);
    					//JSONObject clientCommHead1=clntCommToTypeMap1.get(markupCalcJson.getString(JSON_PROP_COMMNAME));		
    					if (fareBreakupJson != null) {
    						markupAmt = fareBreakupJson.optBigDecimal(JSON_PROP_BASEFARE, baseFareAmt);
    						ccommTaxDetailsJsonArr = fareBreakupJson.optJSONArray(JSON_PROP_TAXDETAILS);
    					}
    					
    					if(sourceMarkupCalcJson!=null && clntCommToTypeMap2!=null) {
    						JSONObject clientCommHead2=clntCommToTypeMap2.get(sourceMarkupCalcJson.getString(JSON_PROP_COMMNAME));
    						JSONObject sourceFareBreakupJson = sourceMarkupCalcJson.optJSONObject(JSON_PROP_FAREBREAKUP);
    						if(sourceFareBreakupJson!=null && clientCommHead2!=null && !clientCommHead2.getBoolean(JSON_PROP_BYPRODUCT)) {
    							sourceBaseFareAmt = sourceMarkupCalcJson.getJSONObject(JSON_PROP_FAREBREAKUP).optBigDecimal(JSON_PROP_BASEFARE, BigDecimal.ZERO);
    							if(!sourceBaseFareAmt.equals( BigDecimal.ZERO)) {
    								markupAmt=markupAmt.add(sourceBaseFareAmt.subtract(baseFareAmt));
    							}
    							
    							//markupAmt=markupAmt.add(sourceBaseFareAmt);
    							sourceCcommTaxDetailsJsonArr= fareBreakupJson.optJSONArray(JSON_PROP_TAXDETAILS);
    							//baseFareAmt=baseFareAmt.add(sourceBaseFareAmt);
    						}
    						
    					}
    					baseFareAmt=markupAmt;
    				}
    				
    				commPriceJson.put(JSON_PROP_PAXTYPE, paxTypeFareJson.getString(JSON_PROP_PAXTYPE));
    				
    				JSONObject baseFareJson = new JSONObject();
    				baseFareJson.put(JSON_PROP_AMOUNT, baseFareAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket,cutOffDate)));
    				baseFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);
    				commPriceJson.put(JSON_PROP_BASEFARE, baseFareJson);
    				paxTypeTotalFare = paxTypeTotalFare.add(baseFareJson.getBigDecimal(JSON_PROP_AMOUNT));
    				totalFareCompsGroup.add(JSON_PROP_BASEFARE, clientCcyCode, baseFareJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
    				
    				
    				int offset = 0;
    				JSONArray paxTypeTaxJsonArr = paxTypeFareJson.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
    				JSONObject taxesJson = getCommercialPricesTaxesJsonV2(paxTypeTaxJsonArr, ccommTaxDetailsJsonArr, sourceCcommTaxDetailsJsonArr,offset, totalFareCompsGroup, paxCount, clientCcyCode, clientMarket,cutOffDate);
    				commPriceJson.put(JSON_PROP_TAXES,  taxesJson);
    				paxTypeTotalFare = paxTypeTotalFare.add(taxesJson.getBigDecimal(JSON_PROP_TOTAL));
    				
    				
    				offset = paxTypeTaxJsonArr.length();
    				JSONArray paxTypeFeeJsonArr = paxTypeFareJson.getJSONObject(JSON_PROP_FEES).getJSONArray(JSON_PROP_FEE);
    				JSONObject feesJson = getCommercialPricesFeesJsonV2(paxTypeFeeJsonArr, ccommTaxDetailsJsonArr,sourceCcommTaxDetailsJsonArr, offset, totalFareCompsGroup, paxCount, clientCcyCode, clientMarket,cutOffDate);
    				commPriceJson.put(JSON_PROP_FEES,  feesJson);
    				paxTypeTotalFare = paxTypeTotalFare.add(feesJson.getBigDecimal(JSON_PROP_TOTAL));
    				
    				// If amount of receivables group is greater than zero, then append to commercial prices
    				if (paxReceivablesCompsGroup != null && paxReceivablesCompsGroup.getComponentAmount().compareTo(BigDecimal.ZERO) == 1) {
    					paxTypeTotalFare = paxTypeTotalFare.add(paxReceivablesCompsGroup.getComponentAmount());
    					totalFareCompsGroup.addSubComponent(totalPaxReceivablesCompsGroup, null);
    					commPriceJson.put(JSON_PROP_RECEIVABLES, paxReceivablesCompsGroup.toJSON());
    				}
    				
    				//-------------------------END-----------------------------------
    				
    				JSONObject clientCommercialItinInfoJson= new JSONObject();
        			clientCommercialItinInfoJson.put(JSON_PROP_PAXTYPE, paxType);
        			clientCommercialItinInfoJson.put(JSON_PROP_CLIENTENTITYCOMMS, clientCommercials);
        			clientCommercialItinInfoArr.put(clientCommercialItinInfoJson);
        			
        			suppPaxTypeFaresJsonArr.put(paxTypeFareJson);
        			JSONObject totalFareJson = new JSONObject();
        			totalFareJson.put(JSON_PROP_AMOUNT, paxTypeTotalFare);
        			totalFareJson.put(JSON_PROP_CCYCODE, clientCcyCode);
        			commPriceJson.put(JSON_PROP_TOTALFARE, totalFareJson);
        			
        			paxTypeFaresJsonArr.put(j, commPriceJson);
    				
    			}
    			resAirItinPricingInfoJson.put(JSON_PROP_ITINTOTALFARE, totalFareCompsGroup.toJSON());
    			
    			if ( clientType == ClientType.B2B) {
        			resAirItinPricingInfoJson.put(JSON_PROP_INCENTIVES, totalIncentivesGroup.toJSON());
        		}
    			
    			
    			//add ssr price
        		
        		JSONArray ssrJsonArr=resAirItinPricingInfoJson.optJSONArray(JSON_PROP_SPECIALSVCREQS);
        		
        		JSONArray supplplierSsrJsonArr=null;
        		BigDecimal ssrTotalAmt=new BigDecimal(0);
        		BigDecimal supplierSsrTotalAmt=new BigDecimal(0);
        		if(ssrJsonArr!=null && ssrJsonArr.length()>0) {
        		 supplplierSsrJsonArr=new JSONArray(new JSONTokener(ssrJsonArr.toString()));
        		String ssrCcyCode=null;
        		for(int l=0;l<ssrJsonArr.length();l++) {
        			JSONObject ssrJson=ssrJsonArr.getJSONObject(l);
        			ssrTotalAmt=ssrTotalAmt.add(ssrJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO));
        			if(ssrJson.has(JSON_PROP_SVCPRC) && ssrJson.getJSONObject(JSON_PROP_SVCPRC).length()>0)
        			{
        				ssrCcyCode=ssrJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_CCYCODE);
        				//change fareComponent to ROEtoo;improve this code make a single method;
        				BigDecimal priceCompAmt=null;
        				String priceCompStr=ssrJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_BASEFARE);
        				if(Utils.isStringNotNullAndNotEmpty(priceCompStr)) {
        					priceCompAmt=(ssrJson.getJSONObject(JSON_PROP_SVCPRC).getBigDecimal(JSON_PROP_BASEFARE)).multiply(RedisRoEData.getRateOfExchange(ssrCcyCode, clientCcyCode, clientMarket,cutOffDate));
        					ssrJson.getJSONObject(JSON_PROP_SVCPRC).put(JSON_PROP_BASEFARE, priceCompAmt.toString());
        				}
        				
        				priceCompStr=ssrJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_TAXES);
        				if(Utils.isStringNotNullAndNotEmpty(priceCompStr)) {
        					priceCompAmt=(ssrJson.getJSONObject(JSON_PROP_SVCPRC).getBigDecimal(JSON_PROP_TAXES)).multiply(RedisRoEData.getRateOfExchange(ssrCcyCode, clientCcyCode, clientMarket,cutOffDate));
        					ssrJson.getJSONObject(JSON_PROP_SVCPRC).put(JSON_PROP_TAXES, priceCompAmt.toString());
        				}
        				
        				priceCompStr=ssrJson.getJSONObject(JSON_PROP_SVCPRC).optString(JSON_PROP_FEES);
        				if(Utils.isStringNotNullAndNotEmpty(priceCompStr)) {
        					priceCompAmt=(ssrJson.getJSONObject(JSON_PROP_SVCPRC).getBigDecimal(JSON_PROP_FEES)).multiply(RedisRoEData.getRateOfExchange(ssrCcyCode, clientCcyCode, clientMarket,cutOffDate));
        					ssrJson.getJSONObject(JSON_PROP_SVCPRC).put(JSON_PROP_FEES, priceCompAmt.toString());
        				}
        				
        				
        				ssrJson.getJSONObject(JSON_PROP_SVCPRC).put(JSON_PROP_CCYCODE,clientCcyCode);
        			}
        				
        			
        		}
        		if(!ssrTotalAmt.equals(BigDecimal.ZERO))
        		{
        			supplierSsrTotalAmt=ssrTotalAmt;
        			ssrTotalAmt=ssrTotalAmt.multiply(RedisRoEData.getRateOfExchange(ssrCcyCode, clientCcyCode, clientMarket,cutOffDate));
        			resAirItinPricingInfoJson.getJSONObject(JSON_PROP_ITINTOTALFARE).put(JSON_PROP_AMOUNT, (resAirItinPricingInfoJson.getJSONObject(JSON_PROP_ITINTOTALFARE).getBigDecimal(JSON_PROP_AMOUNT)).add(ssrTotalAmt));
        			
        		}
        		}
        		
        		if (retainSuppFares) {
    	    		JSONObject suppItinPricingInfoJson  = new JSONObject();
    	    		JSONObject suppItinTotalFareJson = new JSONObject();
    	    		if(ssrJsonArr!=null && ssrJsonArr.length()>0) {
    	    			suppItinTotalFareJson.put(JSON_PROP_SPECIALSVCREQS,supplplierSsrJsonArr);
    	    			suppItinTotalFareJson.put(JSON_PROP_SSRAMT,supplierSsrTotalAmt);
    	    		}
    	    		suppItinPricingInfoJson.put(JSON_PROP_ITINTOTALFARE, suppItinTotalFareJson);
    	    		suppItinPricingInfoJson.put(JSON_PROP_PAXTYPEFARES, suppPaxTypeFaresJsonArr);
    	    		resPricedItinsJson.put(JSON_PROP_CLIENTCOMMITININFO, clientCommercialItinInfoArr);
    	    		resPricedItinsJson.put(JSON_PROP_SUPPPRICEINFO, suppItinPricingInfoJson);
    	    		//put enabler and sourceID
    	    		if(Utils.isStringNotNullAndNotEmpty(enablerSupplierID)) {
    	    			resPricedItinsJson.put(JSON_PROP_ENABLERSUPPID,enablerSupplierID);
    	    			if(Utils.isStringNotNullAndNotEmpty(sourceSuppID)) {
    	    				resPricedItinsJson.put(JSON_PROP_SOURCESUPPID, sourceSuppID);
    	    			}
    	    			else {
    	    				resPricedItinsJson.put(JSON_PROP_SOURCESUPPID, "");
    	    			}
    	    			
    	    		}
    	    		else {
    	    			resPricedItinsJson.put(JSON_PROP_ENABLERSUPPID,"");
    	    			resPricedItinsJson.put(JSON_PROP_SOURCESUPPID, "");
    	    		}
    	    		
    	    		addSupplierItinTotalFare(resPricedItinsJson, paxCountsMap);
        		}
    			/*else if(clientEntityCommJsonArr!=null) {
    				
    			}*/
    			
    			
    		//}
    	}
    }
   
   private static Boolean isClientCommercialApplicable(JSONObject kafkaClientCommercial, JSONObject clientCommHead1,
		JSONObject clientCommHead2) {
	   Boolean isClient1ByProduct=clientCommHead1.getBoolean(JSON_PROP_BYPRODUCT);
	   Boolean isClient2ByProduct=clientCommHead2.getBoolean(JSON_PROP_BYPRODUCT);
	   Boolean isApplicable=false;
	   
	   switch(isClient1ByProduct+"-"+isClient2ByProduct) {
	   case "false-false": 
		   isApplicable=true;
		   break;
	   case "false-true": 
		   isApplicable=true;
		   break;
	   case "true-false":
		   isApplicable=false;
		   break;
	   case "true-true":
		   isApplicable=true;
		   break;
		   
	   }
	return isApplicable;
}
   
   private static Boolean isSourceClientCommercialApplicable(JSONObject kafkaClientCommercial, JSONObject clientCommHead1,
			JSONObject clientCommHead2) {
		   Boolean isClient1ByProduct=clientCommHead1.getBoolean(JSON_PROP_BYPRODUCT);
		   Boolean isClient2ByProduct=clientCommHead2.getBoolean(JSON_PROP_BYPRODUCT);
		   Boolean isApplicable=false;
		   
		   switch(isClient1ByProduct+"-"+isClient2ByProduct) {
		   case "false-false": 
			   isApplicable=true;
			   break;
		   case "false-true": 
			   isApplicable=true;
			   break;
		   case "true-false":
			   isApplicable=false;
			   break;
		   case "true-true":
			   isApplicable=false;
			   break;
			   
		   }
		return isApplicable;
	}

private static JSONObject getCommercialPricesFeesJsonV2(JSONArray paxTypeFeeJsonArr, JSONArray ccommTaxDetailsJsonArr,
		JSONArray sourceCcommTaxDetailsJsonArr, int offset, PriceComponentsGroup totalFareCompsGroup,
		BigDecimal paxCount, String clientCcyCode, String clientMarket, Date cutOffDate) {
		BigDecimal feesTotal = new BigDecimal(0);
		JSONObject feesJson = new JSONObject();
		JSONArray feeJsonArr = new JSONArray();
		String suppCcyCode = null;
		String feeCode = null;

		JSONObject paxTypeFeeJson = null;
		JSONObject feeJson = null;
		for (int l=0; l < paxTypeFeeJsonArr.length(); l++) {
			paxTypeFeeJson = paxTypeFeeJsonArr.getJSONObject(l);
			suppCcyCode = paxTypeFeeJson.getString(JSON_PROP_CCYCODE);
			feeCode = paxTypeFeeJson.getString(JSON_PROP_FEECODE);
			// Access the tax array returned by client commercials in a positional manner instead of 
			// searching in that array using taxcode/feecode.
			feeJson = paxTypeFeeJson;
			BigDecimal feeAmt =feeJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO);
			BigDecimal paxFeeAmt=feeJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO);
			
			feeJson.put(JSON_PROP_FEECODE, feeCode);
			feeJson.put(JSON_PROP_CCYCODE, clientCcyCode);
			JSONObject ccommFeeDetailJson = (ccommTaxDetailsJsonArr != null) ? ccommTaxDetailsJsonArr.optJSONObject(offset + l) : null;
			JSONObject suppCcommFeeDetailJson = (sourceCcommTaxDetailsJsonArr != null) ? sourceCcommTaxDetailsJsonArr.optJSONObject(offset + l) : null;
			if (ccommFeeDetailJson != null && Utils.isStringNotNullAndNotEmpty(feeCode) &&ccommFeeDetailJson.optString("taxName").equals(feeCode)) {
				feeJson = new JSONObject();
				 feeAmt = ccommFeeDetailJson.getBigDecimal(JSON_PROP_TAXVALUE);
				 if(suppCcommFeeDetailJson!=null) {
					 BigDecimal sourceTaxAmt=suppCcommFeeDetailJson.optBigDecimal(JSON_PROP_TAXVALUE,BigDecimal.ZERO);
					 if(!sourceTaxAmt.equals(BigDecimal.ZERO)) {
						 feeAmt=feeAmt.add(sourceTaxAmt.subtract(paxFeeAmt));
					 }
					 //feeAmt=feeAmt.add(suppCcommFeeDetailJson.optBigDecimal(JSON_PROP_TAXVALUE,BigDecimal.ZERO));
				 }
				 feeJson.put(JSON_PROP_FEECODE, feeCode);
				 feeJson.put(JSON_PROP_CCYCODE, clientCcyCode);
				feeJson.put(JSON_PROP_AMOUNT, feeAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket,cutOffDate)));
				
			}
			else {
				feeJson.put(JSON_PROP_AMOUNT, feeAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket,cutOffDate)));
			}
			feeJsonArr.put(feeJson);
			feesTotal = feesTotal.add(feeJson.getBigDecimal(JSON_PROP_AMOUNT));
			totalFareCompsGroup.add(mFeesPriceCompQualifier.concat(feeCode).concat("@").concat(JSON_PROP_FEECODE), clientCcyCode, feeJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
		}
 
		feesJson.put(JSON_PROP_FEE, feeJsonArr);
		feesJson.put(JSON_PROP_TOTAL, feesTotal);
		feesJson.put(JSON_PROP_CCYCODE, clientCcyCode);
		
    	return feesJson;
}

private static JSONObject getCommercialPricesTaxesJsonV2(JSONArray paxTypeTaxJsonArr, JSONArray ccommTaxDetailsJsonArr,
		JSONArray sourceCcommTaxDetailsJsonArr, int offset, PriceComponentsGroup totalFareCompsGroup, BigDecimal paxCount, String clientCcyCode,
		String clientMarket, Date cutOffDate) {
	   BigDecimal taxesTotal = new BigDecimal(0);
		JSONObject taxesJson = new JSONObject();
		JSONArray taxJsonArr = new JSONArray();
		String suppCcyCode = null;
		String taxCode = null;
		
		JSONObject ccommTaxDetailJson = null; 
		JSONObject sourceCcommTaxDetailJson = null; 
		JSONObject paxTypeTaxJson = null;
		JSONObject taxJson = null;
		for (int l=0; l < paxTypeTaxJsonArr.length(); l++) {
			paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
			suppCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
			taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXCODE);

			// Access the tax array returned by client commercials in a positional manner instead of 
			// searching in that array using taxcode/feecode.
			taxJson = paxTypeTaxJson;
			BigDecimal taxAmt = taxJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO);
			BigDecimal paxTaxAmt=taxJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO);
		
			taxJson.put(JSON_PROP_TAXCODE, taxCode);
			taxJson.put(JSON_PROP_CCYCODE, clientCcyCode);
			ccommTaxDetailJson = (ccommTaxDetailsJsonArr != null) ? ccommTaxDetailsJsonArr.optJSONObject(offset + l) : null;
			sourceCcommTaxDetailJson = (sourceCcommTaxDetailsJsonArr != null) ? sourceCcommTaxDetailsJsonArr.optJSONObject(offset + l) : null;
			//BigDecimal ccomTaxJson=new BigDecimal();
			if (ccommTaxDetailJson != null && Utils.isStringNotNullAndNotEmpty(taxCode) && ccommTaxDetailJson.optString(JSON_PROP_TAXNAME).equals(taxCode)) {
				// If tax JSON is found in commercials, replace existing tax details with one from commercials
				 
				taxAmt = ccommTaxDetailJson.getBigDecimal(JSON_PROP_TAXVALUE);
				 if(sourceCcommTaxDetailJson != null &&  sourceCcommTaxDetailJson.optString(JSON_PROP_TAXNAME).equals(taxCode) ) {
						BigDecimal sourceTaxAmt=sourceCcommTaxDetailJson.optBigDecimal(JSON_PROP_TAXVALUE,BigDecimal.ZERO);
					 if(!sourceTaxAmt.equals(BigDecimal.ZERO)) {
						 taxAmt=taxAmt.add(sourceTaxAmt.subtract(paxTaxAmt));
					 }
					// taxAmt=taxAmt.add(sourceCcommTaxDetailJson.optBigDecimal(JSON_PROP_TAXVALUE,BigDecimal.ZERO));
				 }
				taxJson = new JSONObject();
				taxJson.put(JSON_PROP_TAXCODE, taxCode);
				taxJson.put(JSON_PROP_CCYCODE, clientCcyCode);
				taxJson.put(JSON_PROP_AMOUNT, taxAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket,cutOffDate)));
			}
			else {
				taxJson.put(JSON_PROP_AMOUNT, taxAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket,cutOffDate)));
			}
			taxJsonArr.put(taxJson);
			taxesTotal = taxesTotal.add(taxJson.getBigDecimal(JSON_PROP_AMOUNT));
			totalFareCompsGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXCODE), clientCcyCode, taxJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
		}
		
	
			taxesJson.put(JSON_PROP_TAX, taxJsonArr);
			taxesJson.put(JSON_PROP_TOTAL, taxesTotal);
			taxesJson.put(JSON_PROP_CCYCODE, clientCcyCode);

		
		return taxesJson;

}

private static Map<String, JSONObject> createSourceCommsJsonArrMap(Map<String, JSONObject> sourceAddComMap,
		JSONArray sourceAdditionalCommsJsonArr) {
	   
	   for(int i=0;i<sourceAdditionalCommsJsonArr.length();i++) {
		   JSONObject sourceAdditionalCommsJson=sourceAdditionalCommsJsonArr.getJSONObject(i);
		   sourceAddComMap.put( sourceAdditionalCommsJson.getString(JSON_PROP_COMMNAME), sourceAdditionalCommsJson);
	   }
	   
	   return sourceAddComMap;
}

private static BigDecimal compareAndCalculcateCommercialPrice(JSONObject clientCommHead1,
		BigDecimal clientCommercialAmt1, JSONObject clientCommHead2, BigDecimal clientCommercialAmt2) {
	   BigDecimal totalAmt=new BigDecimal(0);
	   Boolean isClient1ByProduct=clientCommHead1.getBoolean(JSON_PROP_BYPRODUCT);
	   Boolean isClient2ByProduct=clientCommHead2.getBoolean(JSON_PROP_BYPRODUCT);
	   
	   switch(isClient1ByProduct+"-"+isClient2ByProduct) {
	   case "false-false": 
		   totalAmt=clientCommercialAmt1.add(clientCommercialAmt2);
		   break;
	   case "false-true": 
		   totalAmt=clientCommercialAmt1;
		   break;
	   case "true-false":
		   totalAmt=clientCommercialAmt2;
		   break;
	   case "true-true":
		   totalAmt=clientCommercialAmt1;
		   break;
		   
	   }
	   
	return totalAmt;
}

private static JSONObject convertToClientEntityCommercialJsonV2(JSONObject clientCommJson, JSONObject clientCommHead, String suppCcyCode,String suppID,Boolean isApplicable, Boolean isSupplierGDS, String enablerSupplierID, String sourceSuppID) {
   	JSONObject clientCommercial= new JSONObject();
		clientCommercial.put(JSON_PROP_COMMTYPE, clientCommHead.opt(JSON_PROP_COMMTYPE));
		clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
		clientCommercial.put(JSON_PROP_COMMNAME,clientCommJson.getString(JSON_PROP_COMMNAME));
		clientCommercial.put(JSON_PROP_COMMCCY,clientCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
		clientCommercial.put(JSON_PROP_COMMCALCPCT,clientCommJson.optString(JSON_PROP_COMMCALCPCT, ""));
		clientCommercial.put(JSON_PROP_COMMCALCAMT,clientCommJson.optString(JSON_PROP_COMMCALCAMT, ""));
		clientCommercial.put(JSON_PROP_COMMFARECOMP,clientCommJson.optString(JSON_PROP_COMMFARECOMP, ""));
		clientCommercial.put(JSON_PROP_MDMRULEID,clientCommJson.optString("mdmruleId", ""));
		clientCommercial.put(JSON_PROP_RETENTIONPERCENTAGE,clientCommJson.optString(JSON_PROP_RETENTIONPERCENTAGE, ""));
		clientCommercial.put(JSON_PROP_RETENTIONAMTPERCENTAGE,clientCommJson.optString(JSON_PROP_RETENTIONAMTPERCENTAGE, ""));
		clientCommercial.put(JSON_PROP_REMAININGAMT,clientCommJson.optString(JSON_PROP_REMAININGAMT, ""));
		clientCommercial.put(JSON_PROP_REMAININGPERCENTAGEAMT,clientCommJson.optString(JSON_PROP_REMAININGPERCENTAGEAMT, ""));
		
		clientCommercial.put(JSON_PROP_ISAPPLICABLE,isApplicable);
		if(isSupplierGDS) {
			if(Utils.isStringNotNullAndNotEmpty(enablerSupplierID)) {
				clientCommercial.put(JSON_PROP_ENABLERSUPPID,enablerSupplierID);
				if(Utils.isStringNotNullAndNotEmpty(sourceSuppID)) {
					clientCommercial.put(JSON_PROP_SOURCESUPPID,sourceSuppID);
				}
				else {
					clientCommercial.put(JSON_PROP_SOURCESUPPID,"");
				}
				
			}
		
		}
		clientCommercial.put(JSON_PROP_SUPPID,suppID);
   	return clientCommercial;
   }
     
     
    private static JSONArray greaterJsonArr(JSONArray firstJsonArr,
			JSONArray secondJsonArr) {
    	if(firstJsonArr!=null || secondJsonArr!=null) {
    		if(firstJsonArr==null) {
    			return secondJsonArr;
    		}
    		else if(secondJsonArr==null){
    			return firstJsonArr;
    		}
    		if(secondJsonArr.length()>=firstJsonArr.length()) {
    			return secondJsonArr;
    		}
    		else {
    			return firstJsonArr;
    		}
    	}
    	return null;
		
	}

	private static Map<String,Map<String,JSONObject>> getClientCommercialsAndTheirTypeV2(JSONObject clientCommResJson) {
    	        JSONArray commHeadJsonArr = null;
    	        JSONObject commHeadJson = null;
    	        JSONArray entityDtlsJsonArr = null;
    	        Map<String,Map<String,JSONObject>> suppIDentitycommToTypeMap = new HashMap<String,Map<String,JSONObject>>();
    	       // Map<String, JSONObject> commToTypeMap = new HashMap<String, JSONObject>();
    	        JSONArray ccommBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
    	        for (int i=0; i < ccommBRIJsonArr.length(); i++) {
    	        	JSONObject ccommBRIJson=ccommBRIJsonArr.getJSONObject(i);
    	        	if ((entityDtlsJsonArr = ccommBRIJson.optJSONArray(JSON_PROP_ENTITYDETAILS)) == null) {
    	        		continue;
    	        	}
    	        	String suppID=ccommBRIJson.getJSONObject(JSON_PROP_COMMONELEMS).getString(JSON_PROP_SUPP);
    	        	for (int j=0; j < entityDtlsJsonArr.length(); j++) {
    	        		JSONObject entityDtlJson=entityDtlsJsonArr.getJSONObject(j);
    	            	if ((commHeadJsonArr = entityDtlJson.optJSONArray(JSON_PROP_COMMHEAD)) == null) {
    	            		logger.warn("No commercial heads found in client commercials");
    	            		continue;
    	            	}
    	            	
    	            	String entityName=entityDtlJson.getString(JSON_PROP_ENTITYNAME);
    	            	 Map<String, JSONObject> commToTypeMap=null;
    	            	if(suppIDentitycommToTypeMap.containsKey(suppID.concat("|").concat(entityName))) {
    	            		commToTypeMap=suppIDentitycommToTypeMap.get(suppID.concat("|").concat(entityName));
    	            	}
    	            	else {
    	            		commToTypeMap = new HashMap<String, JSONObject>();
    	            	}
    	       
    	            	for (int k=0; k < commHeadJsonArr.length(); k++) {
    	            		JSONObject commHeadDetailJson=new JSONObject();
    	            		commHeadJson = commHeadJsonArr.getJSONObject(k);
    	            		commHeadDetailJson.put(JSON_PROP_COMMTYPE, commHeadJson.getString(JSON_PROP_COMMTYPE));
    	            		commHeadDetailJson.put(JSON_PROP_BYPRODUCT, commHeadJson.getBoolean(JSON_PROP_BYPRODUCT));
    	            		commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME), commHeadDetailJson);
    	            	}
    	            	suppIDentitycommToTypeMap.put(suppID.concat("|").concat(entityName), commToTypeMap);
    	        	}
    	        }
    	        
    	        return suppIDentitycommToTypeMap;
    	    
	}

	private static void addSupplierItinTotalFare(JSONObject pricedItinJson, Map<String, BigDecimal> paxInfoMap) {
		JSONObject suppItinPricingInfoJson = pricedItinJson.getJSONObject(JSON_PROP_SUPPPRICEINFO);
		JSONArray paxTypeFaresArr = suppItinPricingInfoJson.getJSONArray(JSON_PROP_PAXTYPEFARES);
		JSONObject suppItinPricingBrkUpInfoJson=new JSONObject();
		Map<String, JSONObject> suppCommTotalsMap = new HashMap<String, JSONObject>();
		Map<String, JSONObject> clientCommTotalsMap = new HashMap<String, JSONObject>();
		BigDecimal totalFareAmt = new BigDecimal(0);
		Map<String, HashMap<String, JSONObject>> suppItinPricingInfoMap=new HashMap<String,HashMap<String,JSONObject>>(); 
		
	
		String ccyCode = null;
		JSONObject clientEntityTotalCommercials=null;
		JSONArray totalClientArr= new JSONArray();
		for (int i = 0; i < paxTypeFaresArr.length(); i++) {
			JSONObject paxTypeFare = paxTypeFaresArr.getJSONObject(i);
			//calculate supplierBreakUp here
			calculateSupplierTotalFareBreakUp(paxTypeFare,paxInfoMap,suppItinPricingBrkUpInfoJson,suppItinPricingInfoMap);
			JSONObject paxTypeTotalFareJson = paxTypeFare.getJSONObject(JSON_PROP_TOTALFARE);
			totalFareAmt = totalFareAmt.add(paxTypeTotalFareJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE))));
			ccyCode = (ccyCode == null) ? paxTypeTotalFareJson.getString(JSON_PROP_CCYCODE) : ccyCode;
			
			JSONArray suppCommJsonArr = paxTypeFare.optJSONArray(JSON_PROP_SUPPCOMM);
			//the order of clientCommercialItinInfo will same as that of normal paxTypeFares
			JSONObject clientItinInfoJson=pricedItinJson.getJSONArray(JSON_PROP_CLIENTCOMMITININFO).getJSONObject(i);
			JSONArray clientCommJsonArr=clientItinInfoJson.optJSONArray(JSON_PROP_CLIENTENTITYCOMMS);
			
			// If no supplier commercials have been defined in BRMS, the JSONArray for suppCommJsonArr will be null.
			// In this case, log a message and proceed with other calculations.
			if (suppCommJsonArr == null) {
				logger.warn("No supplier commercials found");
			}
			else {
				for (int j=0; j < suppCommJsonArr.length(); j++) {
					JSONObject suppCommJson = suppCommJsonArr.getJSONObject(j);
					String suppCommName = suppCommJson.getString(JSON_PROP_COMMNAME);
					String suppId = suppCommJson.optString(JSON_PROP_SUPPID);
					JSONObject suppCommTotalsJson = null;
					if (suppCommTotalsMap.containsKey(suppCommName.concat("|").concat(suppId))) {
						suppCommTotalsJson = suppCommTotalsMap.get(suppCommName.concat("|").concat(suppId));
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommTotalsJson.getBigDecimal(JSON_PROP_COMMAMOUNT).add(suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE)))));
					}
					else {
						suppCommTotalsJson = new JSONObject();
						suppCommTotalsJson.put(JSON_PROP_COMMNAME, suppCommName);
						suppCommTotalsJson.put(JSON_PROP_COMMTYPE, suppCommJson.getString(JSON_PROP_COMMTYPE));
						suppCommTotalsJson.put(JSON_PROP_COMMAMOUNT, suppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE))));
						suppCommTotalsJson.put(JSON_PROP_COMMCCY,suppCommJson.get(JSON_PROP_COMMCCY).toString());
						suppCommTotalsJson.put(JSON_PROP_SUPPID, suppId);
						suppCommTotalsMap.put(suppCommName.concat("|").concat(suppId), suppCommTotalsJson);
					}
				}
			}
			
			if (clientCommJsonArr == null) {
				logger.warn("No client commercials found");
			}
			else {
				for (int l=0; l < clientCommJsonArr.length(); l++) {
					JSONObject clientCommJson = clientCommJsonArr.getJSONObject(l);
					JSONObject clientCommEntJson= new JSONObject();
					
					//clientCommEntJson
					JSONArray clientEntityCommJsonArr=clientCommJson.getJSONArray(JSON_PROP_CLIENTCOMM);
					
					
					
					JSONObject clientCommTotalsJson = null;
					JSONArray clientTotalEntityArray=new JSONArray();
					
					for(int m=0;m<clientEntityCommJsonArr.length();m++) {
						
						JSONObject clientCommEntityJson=clientEntityCommJsonArr.getJSONObject(m);
						String clientCommName = clientCommEntityJson.getString(JSON_PROP_COMMNAME);
						
						
						if (clientCommTotalsMap.containsKey(clientCommName) && clientCommEntityJson.getBoolean(JSON_PROP_ISAPPLICABLE)) {
							clientEntityTotalCommercials= clientCommTotalsMap.get(clientCommName);
							//clientCommTotalsJson=clientEntityTotalCommercials.getJSONObject("clientCommercialsTotal");
							//clientCommTotalsJson = clientCommTotalsMap.get(clientCommName);
							clientEntityTotalCommercials.put(JSON_PROP_COMMAMOUNT, clientEntityTotalCommercials.getBigDecimal(JSON_PROP_COMMAMOUNT).add(clientCommEntityJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE)))));
						}
						else if(clientCommEntityJson.getBoolean(JSON_PROP_ISAPPLICABLE)) {
							clientEntityTotalCommercials= new JSONObject();
							clientCommTotalsJson = new JSONObject();
							clientCommTotalsJson.put(JSON_PROP_COMMNAME, clientCommName);
							clientCommTotalsJson.put(JSON_PROP_COMMTYPE, clientCommEntityJson.getString(JSON_PROP_COMMTYPE));
							clientCommTotalsJson.put(JSON_PROP_COMMAMOUNT, clientCommEntityJson.getBigDecimal(JSON_PROP_COMMAMOUNT).multiply(paxInfoMap.get(paxTypeFare.get(JSON_PROP_PAXTYPE))));
							clientCommTotalsJson.put(JSON_PROP_COMMCCY,clientCommEntityJson.get(JSON_PROP_COMMCCY).toString());
							clientTotalEntityArray.put(clientCommTotalsJson);
						
						
							clientCommTotalsMap.put(clientCommName, clientCommTotalsJson);
						}
						
					}
					
					if((clientTotalEntityArray!=null) && (clientTotalEntityArray.length()>0) ) {
						clientCommEntJson.put("clientCommercialsTotal", clientTotalEntityArray);
						clientCommEntJson.put(JSON_PROP_CLIENTID, clientCommJson.optString(JSON_PROP_CLIENTID,""));
						clientCommEntJson.put(JSON_PROP_PARENTCLIENTID, clientCommJson.optString(JSON_PROP_PARENTCLIENTID,""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYTYPE, clientCommJson.optString(JSON_PROP_COMMENTITYTYPE,""));
						clientCommEntJson.put(JSON_PROP_COMMENTITYID, clientCommJson.optString(JSON_PROP_COMMENTITYID,""));
						totalClientArr.put(clientCommEntJson);
					}
				}
			}
		}
		appendSuppTotalFareBreakUp(suppItinPricingInfoJson,suppItinPricingInfoMap);
		// Convert map of Commercial Head to Commercial Amount to JSONArray and append in suppItinPricingInfoJson
		JSONArray suppCommTotalsJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> suppCommTotalsIter = suppCommTotalsMap.entrySet().iterator();
		while (suppCommTotalsIter.hasNext()) {
			suppCommTotalsJsonArr.put(suppCommTotalsIter.next().getValue());
		}
		suppItinPricingInfoJson.put(JSON_PROP_SUPPCOMMTOTALS, suppCommTotalsJsonArr);
		
	/*	JSONArray clientCommTotalsJsonArr = new JSONArray();
		Iterator<Entry<String, JSONObject>> clientCommTotalsIter = clientCommTotalsMap.entrySet().iterator();
		while (clientCommTotalsIter.hasNext()) {
			clientCommTotalsJsonArr.put(clientCommTotalsIter.next().getValue());
		}*/
		pricedItinJson.put(JSON_PROP_CLIENTENTITYTOTALCOMMS, totalClientArr);
		
		JSONObject itinTotalFare = suppItinPricingInfoJson.getJSONObject(JSON_PROP_ITINTOTALFARE);
		itinTotalFare.put(JSON_PROP_CCYCODE, ccyCode);
		BigDecimal totalAmt=itinTotalFare.optBigDecimal(JSON_PROP_SSRAMT, new BigDecimal(0));
		JSONArray ssrJsonArr=itinTotalFare.optJSONArray(JSON_PROP_SPECIALSVCREQS);
		if(ssrJsonArr!=null && ssrJsonArr.length()>0) {
			suppItinPricingInfoJson.put(JSON_PROP_SPECIALSVCREQS, ssrJsonArr);
			itinTotalFare.remove(JSON_PROP_SPECIALSVCREQS);
			itinTotalFare.remove(JSON_PROP_SSRAMT);
		}
		itinTotalFare.put(JSON_PROP_AMOUNT, totalFareAmt.add(totalAmt));
	}

    private static void appendSuppTotalFareBreakUp(JSONObject suppItinPricingInfoJson,
			Map<String, HashMap<String, JSONObject>> suppItinPricingInfoMap) {
    	HashMap<String, JSONObject> priceComponentMap=new  HashMap<String, JSONObject>();
    	
    	priceComponentMap=suppItinPricingInfoMap.get(JSON_PROP_BASEFARE);
    	JSONObject baseFareJson=priceComponentMap.get(JSON_PROP_BASEFARE);
    	suppItinPricingInfoJson.put(JSON_PROP_BASEFARE, baseFareJson);
    	
    	priceComponentMap=suppItinPricingInfoMap.get(JSON_PROP_TAXES);
    	JSONArray taxesJsonArr=new JSONArray();
    	JSONObject taxesTotalJson=new JSONObject();
    	BigDecimal totalAmount=new BigDecimal(0);
    	for(Map.Entry<String, JSONObject> entry:priceComponentMap.entrySet()) {
    		JSONObject taxJson=entry.getValue();
    		totalAmount=totalAmount.add(taxJson.getBigDecimal(JSON_PROP_AMOUNT));
    		taxesTotalJson.put(JSON_PROP_CCYCODE, taxJson.getString(JSON_PROP_CCYCODE));
    		taxesJsonArr.put( taxJson);
    	}
    	taxesTotalJson.put(JSON_PROP_AMOUNT, totalAmount);
    	taxesTotalJson.put(JSON_PROP_TAX, taxesJsonArr);
    	suppItinPricingInfoJson.put(JSON_PROP_TAXES, taxesTotalJson);
    	
    	priceComponentMap=suppItinPricingInfoMap.get(JSON_PROP_FEES);
    	totalAmount=new BigDecimal(0);
    	JSONObject feesTotalJson=new JSONObject();
    	JSONArray feesJsonArr=new JSONArray();
    	for(Map.Entry<String, JSONObject> entry:priceComponentMap.entrySet()) {
    		JSONObject feesJson=entry.getValue();
    		totalAmount=totalAmount.add(feesJson.getBigDecimal(JSON_PROP_AMOUNT));
    		feesTotalJson.put(JSON_PROP_CCYCODE, feesJson.getString(JSON_PROP_CCYCODE));
    		feesJsonArr.put( feesJson);
    	}
    	feesTotalJson.put(JSON_PROP_AMOUNT, totalAmount);
    	feesTotalJson.put(JSON_PROP_FEE, feesJsonArr);
    	suppItinPricingInfoJson.put(JSON_PROP_FEES, feesTotalJson);
	}

	private static void calculateSupplierTotalFareBreakUp(JSONObject paxTypeFare, Map<String, BigDecimal> paxInfoMap,
			JSONObject suppItinPricingInfoJson, Map<String, HashMap<String, JSONObject>> suppItinPricingInfoMap) {
    	
    	
    	HashMap<String, JSONObject> priceComponentMap=new  HashMap<String, JSONObject>();
    	
    	BigDecimal paxTypeQnt=paxInfoMap.get(paxTypeFare.getString(JSON_PROP_PAXTYPE));
    	JSONObject supplierTotalBaseFareJson=new JSONObject();
    	JSONObject supplierTotalFeesJson=null;
    	JSONObject supplierTotalTaxJson=null;
    	//JSONObject paxFeesJson=paxTypeFare.getJSONObject(JSON_PROP_FEES);
		JSONArray paxFeeJsonObj=paxTypeFare.getJSONObject(JSON_PROP_FEES).getJSONArray(JSON_PROP_FEE);
		///BASEFARE
		JSONObject paxTypeBaseFare=paxTypeFare.getJSONObject(JSON_PROP_BASEFARE);
		
	/*	if(supplierTotalBaseFareJson.has(JSON_PROP_BASEFARE)) {
			supplierTotalBaseFareJson=suppItinPricingInfoJson.getJSONObject(JSON_PROP_BASEFARE);
			supplierTotalBaseFareJson.put(JSON_PROP_AMOUNT,(supplierTotalBaseFareJson.getBigDecimal(JSON_PROP_AMOUNT)).add((paxTypeBaseFare.getBigDecimal(JSON_PROP_AMOUNT)).multiply(paxTypeQnt)));
		    supplierTotalBaseFareJson.put(JSON_PROP_CCYCODE, paxTypeBaseFare.getString(JSON_PROP_CCYCODE));
		}
		else {
			supplierTotalBaseFareJson=new JSONObject();
			supplierTotalBaseFareJson.put(JSON_PROP_AMOUNT,(paxTypeBaseFare.getBigDecimal(JSON_PROP_AMOUNT)).multiply(paxTypeQnt));
			supplierTotalBaseFareJson.put(JSON_PROP_CCYCODE, paxTypeBaseFare.getString(JSON_PROP_CCYCODE));
		}*/
		
		if(suppItinPricingInfoMap.containsKey(JSON_PROP_BASEFARE)) {
		    priceComponentMap=suppItinPricingInfoMap.get(JSON_PROP_BASEFARE);
			supplierTotalBaseFareJson=priceComponentMap.get(JSON_PROP_BASEFARE.toString());
			supplierTotalBaseFareJson.put(JSON_PROP_AMOUNT,(supplierTotalBaseFareJson.getBigDecimal(JSON_PROP_AMOUNT)).add((paxTypeBaseFare.getBigDecimal(JSON_PROP_AMOUNT)).multiply(paxTypeQnt)));
		    supplierTotalBaseFareJson.put(JSON_PROP_CCYCODE, paxTypeBaseFare.getString(JSON_PROP_CCYCODE));
		    priceComponentMap.put(JSON_PROP_BASEFARE, supplierTotalBaseFareJson);
		}
		else {
			priceComponentMap=new  HashMap<String, JSONObject>();
			supplierTotalBaseFareJson=new JSONObject();
			supplierTotalBaseFareJson.put(JSON_PROP_AMOUNT,(paxTypeBaseFare.getBigDecimal(JSON_PROP_AMOUNT)).multiply(paxTypeQnt));
			supplierTotalBaseFareJson.put(JSON_PROP_CCYCODE, paxTypeBaseFare.getString(JSON_PROP_CCYCODE));
			priceComponentMap.put(JSON_PROP_BASEFARE, supplierTotalBaseFareJson);
		}
		
		suppItinPricingInfoMap.put(JSON_PROP_BASEFARE,priceComponentMap);
		
		//FEES
		if(suppItinPricingInfoMap.containsKey(JSON_PROP_FEES)) {
			// supplierTotalFeesJsonArr=suppItinPricingInfoJson.getJSONArray(JSON_PROP_FEES);
			 priceComponentMap=suppItinPricingInfoMap.get(JSON_PROP_FEES);
			
		}
		else {
			 priceComponentMap=suppItinPricingInfoMap.get(JSON_PROP_FEES);
			 //supplierTotalFeesJsonArr=new JSONArray();
			 priceComponentMap=new  HashMap<String, JSONObject>();
			
			
		}
		for(int i=0;i<paxFeeJsonObj.length();i++) {
			
			JSONObject paxFeeJson=paxFeeJsonObj.getJSONObject(i);
			//Put check here for new fee type
			if(!priceComponentMap.containsKey(paxFeeJson.optString(JSON_PROP_FEECODE))) {
				supplierTotalFeesJson=new JSONObject();
				supplierTotalFeesJson.put(JSON_PROP_AMOUNT, paxFeeJson.getBigDecimal(JSON_PROP_AMOUNT));
				supplierTotalFeesJson.put(JSON_PROP_CCYCODE, paxFeeJson.getString(JSON_PROP_CCYCODE));
				supplierTotalFeesJson.put(JSON_PROP_FEECODE, paxFeeJson.getString(JSON_PROP_FEECODE));
				priceComponentMap.put(paxFeeJson.getString(JSON_PROP_FEECODE), supplierTotalFeesJson);
			}
			else {
				supplierTotalFeesJson=priceComponentMap.get(paxFeeJson.optString(JSON_PROP_FEECODE));
				supplierTotalFeesJson.put(JSON_PROP_AMOUNT, (supplierTotalFeesJson.getBigDecimal(JSON_PROP_AMOUNT)).add((paxFeeJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxTypeQnt))));
				supplierTotalFeesJson.put(JSON_PROP_CCYCODE, paxFeeJson.getString(JSON_PROP_CCYCODE));
				supplierTotalFeesJson.put(JSON_PROP_FEECODE, paxFeeJson.getString(JSON_PROP_FEECODE));
				priceComponentMap.put(paxFeeJson.getString(JSON_PROP_FEECODE), supplierTotalFeesJson);
			}
			
		}
		suppItinPricingInfoMap.put(JSON_PROP_FEES,priceComponentMap);
		//TAXES
		priceComponentMap=new  HashMap<String, JSONObject>();
		JSONArray paxTaxesJson=paxTypeFare.getJSONObject(JSON_PROP_TAXES).getJSONArray(JSON_PROP_TAX);
		
		
		if(suppItinPricingInfoMap.containsKey(JSON_PROP_TAXES)) {
			 priceComponentMap=suppItinPricingInfoMap.get(JSON_PROP_TAXES);
			
		}
		else {
			 priceComponentMap=new  HashMap<String, JSONObject>();
		}
		for(int j=0;j<paxTaxesJson.length();j++) {
			JSONObject paxTaxJson=paxTaxesJson.getJSONObject(j);
			
			if( !priceComponentMap.containsKey(paxTaxJson.optString(JSON_PROP_TAXCODE))) {
				supplierTotalTaxJson=new JSONObject();
				supplierTotalTaxJson.put(JSON_PROP_AMOUNT, paxTaxJson.getBigDecimal(JSON_PROP_AMOUNT));
				supplierTotalTaxJson.put(JSON_PROP_CCYCODE, paxTaxJson.getString(JSON_PROP_CCYCODE));
				supplierTotalTaxJson.put(JSON_PROP_TAXCODE, paxTaxJson.getString(JSON_PROP_TAXCODE));
				priceComponentMap.put(paxTaxJson.getString(JSON_PROP_TAXCODE), supplierTotalTaxJson);
			}
			else {
				supplierTotalTaxJson=priceComponentMap.get(paxTaxJson.getString(JSON_PROP_TAXCODE));
				supplierTotalTaxJson.put(JSON_PROP_AMOUNT, (supplierTotalTaxJson.getBigDecimal(JSON_PROP_AMOUNT)).add((paxTaxJson.getBigDecimal(JSON_PROP_AMOUNT)).multiply(paxTypeQnt)));
				supplierTotalTaxJson.put(JSON_PROP_CCYCODE, paxTaxJson.getString(JSON_PROP_CCYCODE));
				supplierTotalTaxJson.put(JSON_PROP_TAXCODE, paxTaxJson.getString(JSON_PROP_TAXCODE));
				priceComponentMap.put(paxTaxJson.getString(JSON_PROP_TAXCODE), supplierTotalTaxJson);
			}
		}
		suppItinPricingInfoMap.put(JSON_PROP_TAXES,priceComponentMap);
	}

	private static JSONArray getClientCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
    	return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.air_commercialscalculationengine.clienttransactionalrules.Root").getJSONArray("businessRuleIntake");
    }
    
    private static JSONArray getSupplierCommercialsBusinessRuleIntakeJSONArray(JSONObject commResJson) {
    	return commResJson.getJSONObject("result").getJSONObject("execution-results").getJSONArray("results").getJSONObject(0).getJSONObject("value").getJSONObject("cnk.air_commercialscalculationengine.suppliertransactionalrules.Root").getJSONArray("businessRuleIntake");
    }

    // Retrieve commercials head array from client commercials and find type (Receivable, Payable) for commercials 
    private static Map<String, String> getClientCommercialsAndTheirType(JSONObject clientCommResJson) {
        JSONArray commHeadJsonArr = null;
        JSONObject commHeadJson = null;
        JSONArray entityDtlsJsonArr = null;
        Map<String, String> commToTypeMap = new HashMap<String, String>();
        JSONArray ccommBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
        for (int i=0; i < ccommBRIJsonArr.length(); i++) {
        	if ((entityDtlsJsonArr = ccommBRIJsonArr.getJSONObject(i).optJSONArray(JSON_PROP_ENTITYDETAILS)) == null) {
        		continue;
        	}
        	for (int j=0; j < entityDtlsJsonArr.length(); j++) {
            	if ((commHeadJsonArr = entityDtlsJsonArr.getJSONObject(j).optJSONArray(JSON_PROP_COMMHEAD)) == null) {
            		logger.warn("No commercial heads found in client commercials");
            		continue;
            	}
            	
            	for (int k=0; k < commHeadJsonArr.length(); k++) {
            		commHeadJson = commHeadJsonArr.getJSONObject(k);
            		commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME), commHeadJson.getString(JSON_PROP_COMMTYPE));
            	}
        	}
        }
        
        return commToTypeMap;
    }

    // Retrieve commercials head array from supplier commercials and find type (Receivable, Payable) for commercials 
    private static Map<String, String> getSupplierCommercialsAndTheirType(JSONObject suppCommResJson) {
        JSONArray commHeadJsonArr = null;
        JSONObject commHeadJson = null;
        Map<String, String> commToTypeMap = new HashMap<String, String>();
        JSONArray scommBRIJsonArr = getSupplierCommercialsBusinessRuleIntakeJSONArray(suppCommResJson);
        for (int i=0; i < scommBRIJsonArr.length(); i++) {
        	if ((commHeadJsonArr = scommBRIJsonArr.getJSONObject(i).optJSONArray(JSON_PROP_COMMHEAD)) == null) {
        		logger.warn("No commercial heads found in supplier commercials");
        		continue;
        	}
        	
        	/*JSONObject scomBriJson=scommBRIJsonArr.getJSONObject(i);
        	String suppID=scomBriJson.getJSONObject(JSON_PROP_COMMONELEMS).getString(JSON_PROP_SUPP);*/
        	for (int j=0; j < commHeadJsonArr.length(); j++) {
        		commHeadJson = commHeadJsonArr.getJSONObject(j);
        		commToTypeMap.put(commHeadJson.getString(JSON_PROP_COMMHEADNAME), commHeadJson.getString(JSON_PROP_COMMTYPE));
        	}
        }
        
        return commToTypeMap;
    }

	// Retrieve array of businessRuleIntake.journeyDetails for each supplier from Client Commercials response JSON
    private static Map<String, JSONArray> getSupplierWiseJourneyDetailsFromClientCommercials(JSONObject clientCommResJson) {
    	JSONArray ccommSuppBRIJsonArr = getClientCommercialsBusinessRuleIntakeJSONArray(clientCommResJson);
    	Map<String, JSONArray> ccommSuppBRIJsonMap = new HashMap<String, JSONArray>();
    	for (int i=0; i < ccommSuppBRIJsonArr.length(); i++) {
    		JSONObject ccommSuppBRIJson = ccommSuppBRIJsonArr.getJSONObject(i);
    		String suppID = ccommSuppBRIJson.getJSONObject(JSON_PROP_COMMONELEMS).getString(JSON_PROP_SUPP);
    		JSONArray ccommJrnyDtlsJsonArr = ccommSuppBRIJson.getJSONArray(JSON_PROP_JOURNEYDETAILS);
    		ccommSuppBRIJsonMap.put(suppID, ccommJrnyDtlsJsonArr);
    	}
    	
    	return ccommSuppBRIJsonMap;
    }
    
    static Map<String,BigDecimal> getPaxCountsFromRequest(JSONObject reqJson) throws ValidationException{
    	Map<String,BigDecimal> paxInfoMap=new LinkedHashMap<String,BigDecimal>();
        JSONObject paxInfo=null;

        JSONArray reqPaxInfoJsonArr = reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_PAXINFO);
        for(int i=0;i<reqPaxInfoJsonArr.length();i++) {
        	paxInfo = reqPaxInfoJsonArr.getJSONObject(i);
        	if(!(paxInfo.has(JSON_PROP_PAXTYPE))) {
        		throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR027");
        	}
        	if(!(paxInfo.has(JSON_PROP_QTY))){
        		throw new ValidationException(reqJson.getJSONObject(JSON_PROP_REQHEADER),"TRLERR028");
        	}
        	
        	
        	paxInfoMap.put(paxInfo.getString(JSON_PROP_PAXTYPE), new BigDecimal(paxInfo.getInt(JSON_PROP_QTY)));
        }

        return paxInfoMap;
    }
    
    private static JSONObject getClientCommercialsJourneyDetailsForPassengerType(JSONObject ccommJrnyDtlsJson, String paxType) {
		if (ccommJrnyDtlsJson == null || paxType == null) {
			return null;
		}
		
		// Search this paxType in client commercials journeyDetails 
		JSONArray ccommJrnyPsgrDtlsJsonArr = ccommJrnyDtlsJson.getJSONArray(JSON_PROP_PSGRDETAILS);
		for (int k=0; k < ccommJrnyPsgrDtlsJsonArr.length(); k++) {
			JSONObject ccommJrnyPsgrDtlsJson = ccommJrnyPsgrDtlsJsonArr.getJSONObject(k);
			if (paxType.equals(ccommJrnyPsgrDtlsJson.getString(JSON_PROP_PSGRTYPE))) {
				return ccommJrnyPsgrDtlsJson;
			}
		}
		
		return null;
    }
    
    private static JSONObject getCommercialPricesTaxesJson(JSONArray paxTypeTaxJsonArr, JSONArray ccommTaxDetailsJsonArr, int offset, PriceComponentsGroup totalFareCompsGroup, BigDecimal paxCount, String clientCcyCode, String clientMarket,Date cutOffDate) {
		BigDecimal taxesTotal = new BigDecimal(0);
		JSONObject taxesJson = new JSONObject();
		JSONArray taxJsonArr = new JSONArray();
		String suppCcyCode = null;
		String taxCode = null;
		
		JSONObject ccommTaxDetailJson = null; 
		JSONObject paxTypeTaxJson = null;
		JSONObject taxJson = null;
		for (int l=0; l < paxTypeTaxJsonArr.length(); l++) {
			paxTypeTaxJson = paxTypeTaxJsonArr.getJSONObject(l);
			suppCcyCode = paxTypeTaxJson.getString(JSON_PROP_CCYCODE);
			taxCode = paxTypeTaxJson.getString(JSON_PROP_TAXCODE);

			// Access the tax array returned by client commercials in a positional manner instead of 
			// searching in that array using taxcode/feecode.
			taxJson = paxTypeTaxJson;
			BigDecimal taxAmt = taxJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO);
			taxJson.put(JSON_PROP_TAXCODE, taxCode);
			taxJson.put(JSON_PROP_CCYCODE, clientCcyCode);
			ccommTaxDetailJson = (ccommTaxDetailsJsonArr != null) ? ccommTaxDetailsJsonArr.optJSONObject(offset + l) : null;
			if (ccommTaxDetailJson != null && Utils.isStringNotNullAndNotEmpty(taxCode) && ccommTaxDetailJson.optString(JSON_PROP_TAXNAME).equals(taxCode)) {
				// If tax JSON is found in commercials, replace existing tax details with one from commercials
				 taxAmt = ccommTaxDetailJson.getBigDecimal(JSON_PROP_TAXVALUE);
				taxJson = new JSONObject();
				taxJson.put(JSON_PROP_TAXCODE, taxCode);
				taxJson.put(JSON_PROP_CCYCODE, clientCcyCode);
				taxJson.put(JSON_PROP_AMOUNT, taxAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket,cutOffDate)));
			}
			else {
				taxJson.put(JSON_PROP_AMOUNT, taxAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket,cutOffDate)));
			}
			taxJsonArr.put(taxJson);
			taxesTotal = taxesTotal.add(taxJson.getBigDecimal(JSON_PROP_AMOUNT));
			totalFareCompsGroup.add(mTaxesPriceCompQualifier.concat(taxCode).concat("@").concat(JSON_PROP_TAXCODE), clientCcyCode, taxJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
		}
		
	
			taxesJson.put(JSON_PROP_TAX, taxJsonArr);
			taxesJson.put(JSON_PROP_TOTAL, taxesTotal);
			taxesJson.put(JSON_PROP_CCYCODE, clientCcyCode);

		
		return taxesJson;
    }

    private static JSONObject getCommercialPricesFeesJson(JSONArray paxTypeFeeJsonArr, JSONArray ccommTaxDetailsJsonArr, int offset, PriceComponentsGroup totalFareCompsGroup, BigDecimal paxCount, String clientCcyCode, String clientMarket,Date cutOffDate) {
		BigDecimal feesTotal = new BigDecimal(0);
		JSONObject feesJson = new JSONObject();
		JSONArray feeJsonArr = new JSONArray();
		String suppCcyCode = null;
		String feeCode = null;

		JSONObject paxTypeFeeJson = null;
		JSONObject feeJson = null;
		for (int l=0; l < paxTypeFeeJsonArr.length(); l++) {
			paxTypeFeeJson = paxTypeFeeJsonArr.getJSONObject(l);
			suppCcyCode = paxTypeFeeJson.getString(JSON_PROP_CCYCODE);
			feeCode = paxTypeFeeJson.getString(JSON_PROP_FEECODE);
			// Access the tax array returned by client commercials in a positional manner instead of 
			// searching in that array using taxcode/feecode.
			feeJson = paxTypeFeeJson;
			BigDecimal feeAmt =feeJson.optBigDecimal(JSON_PROP_AMOUNT, BigDecimal.ZERO);
			feeJson.put(JSON_PROP_FEECODE, feeCode);
			feeJson.put(JSON_PROP_CCYCODE, clientCcyCode);
			JSONObject ccommFeeDetailJson = (ccommTaxDetailsJsonArr != null) ? ccommTaxDetailsJsonArr.optJSONObject(offset + l) : null;
			if (ccommFeeDetailJson != null && Utils.isStringNotNullAndNotEmpty(feeCode) &&ccommFeeDetailJson.optString("feeName").equals(feeCode)) {
				feeJson = new JSONObject();
				 feeAmt = ccommFeeDetailJson.getBigDecimal(JSON_PROP_TAXVALUE);
				 feeJson.put(JSON_PROP_FEECODE, feeCode);
				 feeJson.put(JSON_PROP_CCYCODE, clientCcyCode);
				feeJson.put(JSON_PROP_AMOUNT, feeAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket,cutOffDate)));
				
			}
			else {
				feeJson.put(JSON_PROP_AMOUNT, feeAmt.multiply(RedisRoEData.getRateOfExchange(suppCcyCode, clientCcyCode, clientMarket,cutOffDate)));
			}
			feeJsonArr.put(feeJson);
			feesTotal = feesTotal.add(feeJson.getBigDecimal(JSON_PROP_AMOUNT));
			totalFareCompsGroup.add(mFeesPriceCompQualifier.concat(feeCode).concat("@").concat(JSON_PROP_FEECODE), clientCcyCode, feeJson.getBigDecimal(JSON_PROP_AMOUNT).multiply(paxCount));
		}
 
		feesJson.put(JSON_PROP_FEE, feeJsonArr);
		feesJson.put(JSON_PROP_TOTAL, feesTotal);
		feesJson.put(JSON_PROP_CCYCODE, clientCcyCode);
		
    	return feesJson;
    }

    public static JSONObject getEmptyResponse(JSONObject reqHdrJson) {
        JSONObject resJson = new JSONObject();
        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
        resJson.put(JSON_PROP_RESBODY, new JSONObject());
        return resJson;
    }
    
    // Append the supplier commercials returned by commercials engine in supplier pax type fares. 
    // This is required only at reprice/book time for financial consumption and supplier settlement purpose.
    private static void appendSupplierCommercialsToPaxTypeFares(JSONObject paxTypeFareJson, JSONObject ccommJrnyPsgrDtlJson, String suppID, String suppCcyCode, Map<String,String> suppCommToTypeMap) {
		JSONArray suppCommJsonArr = new JSONArray();
		JSONArray ccommSuppCommJsonArr = ccommJrnyPsgrDtlJson.optJSONArray(JSON_PROP_COMMDETAILS);
		//JSONArray ccommSuppEntityCommJsonArr = ccommJrnyPsgrDtlJson.optJSONArray(JSON_PROP_ENTITYCOMMS);
		
		// If no supplier commercials have been defined in BRMS, the JSONArray for ccommSuppCommJsonArr will be null.
		// In this case, log a message and proceed with other calculations.
		if (ccommSuppCommJsonArr == null) {
			logger.warn(String.format("No supplier commercials found for supplier %s", suppID));
			return;
		}

		for (int x=0; x < ccommSuppCommJsonArr.length(); x++) {
			JSONObject ccommSuppCommJson = ccommSuppCommJsonArr.getJSONObject(x);
			JSONObject suppCommJson = new JSONObject();
			suppCommJson.put(JSON_PROP_COMMNAME, ccommSuppCommJson.getString(JSON_PROP_COMMNAME));
			suppCommJson.put(JSON_PROP_COMMTYPE, suppCommToTypeMap.get(ccommSuppCommJson.getString(JSON_PROP_COMMNAME)));
			suppCommJson.put(JSON_PROP_COMMAMOUNT, ccommSuppCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
			suppCommJson.put(JSON_PROP_COMMCCY, ccommSuppCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
			suppCommJson.put(JSON_PROP_MDMRULEID,ccommSuppCommJson.optString("mdmruleId", ""));
			suppCommJson.put(JSON_PROP_COMMCALCPCT,ccommSuppCommJson.optString(JSON_PROP_COMMCALCPCT, ""));
			suppCommJson.put(JSON_PROP_COMMCALCAMT,ccommSuppCommJson.optString(JSON_PROP_COMMCALCAMT, ""));
			suppCommJson.put(JSON_PROP_COMMFARECOMP,ccommSuppCommJson.optString(JSON_PROP_COMMFARECOMP, ""));
			
			suppCommJsonArr.put(suppCommJson);
		}
		paxTypeFareJson.put(JSON_PROP_SUPPCOMM, suppCommJsonArr);
    }
    
    private static JSONObject convertToClientEntityCommercialJson(JSONObject clientCommJson, Map<String,String> clntCommToTypeMap, String suppCcyCode) {
    	JSONObject clientCommercial= new JSONObject();
    	String commercialName = clientCommJson.getString(JSON_PROP_COMMNAME);
		clientCommercial.put(JSON_PROP_COMMTYPE, clntCommToTypeMap.getOrDefault(commercialName, "?"));
		clientCommercial.put(JSON_PROP_COMMAMOUNT, clientCommJson.getBigDecimal(JSON_PROP_COMMAMOUNT));
		clientCommercial.put(JSON_PROP_COMMNAME,clientCommJson.getString(JSON_PROP_COMMNAME));
		clientCommercial.put(JSON_PROP_COMMCCY,clientCommJson.optString(JSON_PROP_COMMCCY, suppCcyCode));
		clientCommercial.put(JSON_PROP_COMMCALCPCT,clientCommJson.optString(JSON_PROP_COMMCALCPCT, ""));
		clientCommercial.put(JSON_PROP_COMMCALCAMT,clientCommJson.optString(JSON_PROP_COMMCALCAMT, ""));
		clientCommercial.put(JSON_PROP_COMMFARECOMP,clientCommJson.optString(JSON_PROP_COMMFARECOMP, ""));
		clientCommercial.put(JSON_PROP_MDMRULEID,clientCommJson.optString("mdmruleId", ""));
    	return clientCommercial;
    }
    
	public static TripIndicator deduceTripIndicator(JSONObject reqHdrJson, JSONObject reqBodyJson) {
		JSONArray odoInfoJsonArr = reqBodyJson.optJSONArray(JSON_PROP_ORIGDESTINFO);
		return (odoInfoJsonArr != null) ? deduceSearchOperationTripIndicator(odoInfoJsonArr) : deduceOtherOperationsTripIndicator(reqBodyJson);
	}
	
	private static TripIndicator deduceSearchOperationTripIndicator(JSONArray odoInfoJsonArr) {
		TripIndicator tripIndicator = TripIndicator.DOMESTIC;
		JSONObject odoInfoJson = null;
		String prevCountry = null;
		String origCountry = null;
		String destCountry = null;
		// TODO: Check how user's country will affect tripIndicator
		for (int i=0; i < odoInfoJsonArr.length(); i++) {
			odoInfoJson = odoInfoJsonArr.getJSONObject(i);
			origCountry = RedisAirportData.getAirportInfo(odoInfoJson.getString(JSON_PROP_ORIGLOC), RedisAirportData.AIRPORT_COUNTRY);
			if (prevCountry != null && REDIS_VALUE_NIL.equals(origCountry) == false && prevCountry.equals(origCountry) == false) {
				tripIndicator = TripIndicator.INTERNATIONAL;
				break;
			}
			prevCountry = origCountry;
			
			destCountry = RedisAirportData.getAirportInfo(odoInfoJson.getString(JSON_PROP_DESTLOC), RedisAirportData.AIRPORT_COUNTRY);
			if (prevCountry != null && REDIS_VALUE_NIL.equals(destCountry) == false && prevCountry.equals(destCountry) == false) {
				tripIndicator = TripIndicator.INTERNATIONAL;
				break;
			}
			prevCountry = destCountry;
		}
		
		return tripIndicator;
	}

	public static TripIndicator deduceOtherOperationsTripIndicator(JSONObject reqBodyJson) {
		TripIndicator tripIndicator = TripIndicator.DOMESTIC;
		JSONObject pricedItinsJson = null;
		String prevCountry = null;
		String origCountry = null;
		String destCountry = null;
		
		JSONArray pricedItinsJsonArr = reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
		for (int i=0; i < pricedItinsJsonArr.length(); i++) {
			pricedItinsJson = pricedItinsJsonArr.getJSONObject(i);
			JSONObject airItinJson = pricedItinsJson.optJSONObject(JSON_PROP_AIRITINERARY);
			if (airItinJson == null) {
				continue;
			}
			
			JSONArray odoJsonArr = airItinJson.optJSONArray(JSON_PROP_ORIGDESTOPTS);
			if (odoJsonArr == null) {
				continue;
			}
			
			for (int j=0; j < odoJsonArr.length(); j++) {
				JSONObject odoJson = odoJsonArr.getJSONObject(j);
				JSONArray flSegsJsonArr = odoJson.getJSONArray(JSON_PROP_FLIGHTSEG);
				if (flSegsJsonArr == null) {
					continue;
				}
				
				for (int k=0; k < flSegsJsonArr.length(); k++) {
					JSONObject flSegJson = flSegsJsonArr.getJSONObject(k);
					
					origCountry = RedisAirportData.getAirportInfo(flSegJson.getString(JSON_PROP_ORIGLOC), RedisAirportData.AIRPORT_COUNTRY);
					if (prevCountry != null && REDIS_VALUE_NIL.equals(origCountry) == false && prevCountry.equals(origCountry) == false) {
						tripIndicator = TripIndicator.INTERNATIONAL;
						break;
					}
					prevCountry = origCountry;
					
					destCountry = RedisAirportData.getAirportInfo(flSegJson.getString(JSON_PROP_DESTLOC), RedisAirportData.AIRPORT_COUNTRY);
					if (prevCountry != null && REDIS_VALUE_NIL.equals(destCountry) == false && prevCountry.equals(destCountry) == false) {
						tripIndicator = TripIndicator.INTERNATIONAL;
						break;
					}
					prevCountry = destCountry;
					
				}
			}
			
		}
		
		return tripIndicator;
	}

	public static Element[] sortWrapperElementsBySequence(Element[] wrapperElems) {
		Map<Integer, Element> wrapperElemsMap = new TreeMap<Integer, Element>();
		for (Element wrapperElem : wrapperElems) {
			wrapperElemsMap.put(Utils.convertToInt(XMLUtils.getValueAtXPath(wrapperElem, "./air:Sequence"), 0), wrapperElem);
		}
		
		int  idx = 0;
		Element[] seqSortedWrapperElems = new Element[wrapperElems.length];
		Iterator<Map.Entry<Integer, Element>> wrapperElemsIter = wrapperElemsMap.entrySet().iterator();
		while (wrapperElemsIter.hasNext()) {
			seqSortedWrapperElems[idx++] = wrapperElemsIter.next().getValue();
		}
		
		return seqSortedWrapperElems;
	}
	
}
