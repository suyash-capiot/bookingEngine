package com.coxandkings.travel.bookingengine.orchestrator.air;


import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.air.AirConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.air.enums.TripIndicator;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;


public class AirSeatMapProcessor implements AirConstants  {

	private static final Logger logger = LogManager.getLogger(AirSeatMapProcessor.class);
	
	public static String process(JSONObject reqJson) throws RequestProcessingException, InternalProcessingException {
		Element reqElem = null;
		JSONObject reqHdrJson = null, reqBodyJson = null;
		//OperationConfig opConfig = null;
		ServiceConfig opConfig = null;
		UserContext usrCtx = null;
		 TripIndicator tripInd =null;
		 Map<Integer, Map<Integer, ArrayList<Integer>>> priceItinMap =null;
		 
		 try {
				TrackingContext.setTrackingContext(reqJson);
				opConfig = AirConfig.getOperationConfig(TrackingContext.getTrackingContext().getTrackingParameter(ServletContext.RESTPROP_OPERATION));

				reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
				reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);
				
				validateRequestParameters(reqJson);
	            tripInd = AirSearchProcessor.deduceTripIndicator(reqHdrJson, reqBodyJson);
	            reqBodyJson.put(JSON_PROP_TRIPIND, tripInd.toString());

				usrCtx = UserContext.getUserContextForSession(reqHdrJson);
				priceItinMap = new HashMap<Integer, Map<Integer, ArrayList<Integer>>>();
	            reqElem = createSIRequest(opConfig, usrCtx, reqHdrJson, reqBodyJson,priceItinMap);
	           
		 }
			catch (Exception x) {
				logger.error("Exception during request processing", x);
				throw new RequestProcessingException(x);
			}
	
			try {
				Element resElem = null;
				//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), AirConfig.getHttpHeaders(), reqElem);
				//resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getSIServiceURL(), opConfig.getHttpHeaders(), reqElem);
				resElem = HTTPServiceConsumer.consumeXMLService(TARGET_SYSTEM_SUPPINTEG, opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
				if (resElem == null) {
					throw new Exception("Null response received from SI");
				}
				
				 JSONObject resJson = new JSONObject();
		         resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		         JSONObject resBodyJson = new JSONObject();
		         
		         
				Element[] wrapprElems=AirSearchProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./airi:ResponseBody/air:OTA_AirSeatMapRSWrapper"));
				//JSONArray fareInfoJsonArr=new JSONArray();
				
				JSONArray priceItinRqJsonArr=reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
				
				JSONArray priceItinRsJsonArr=new JSONArray();
				for(int i=0;i<priceItinRqJsonArr.length();i++) {
					Map<Integer,ArrayList<Integer>> odoMap=priceItinMap.get(i);
					JSONObject priceItinRqJson=priceItinRqJsonArr.getJSONObject(i);
					
					JSONObject priceItinRsJson=new JSONObject();
				//XMLUtils.getValueAtXPath((Element) resBodyElem.getParentNode(), "./tran1:SupplierID");
					
						
						priceItinRsJson.put(JSON_PROP_SUPPREF, priceItinRqJson.getString(JSON_PROP_SUPPREF));
						
					
						
					JSONObject airItinJson = priceItinRqJson.getJSONObject(JSON_PROP_AIRITINERARY);
					JSONObject airItinRes = new JSONObject();
					//JSONArray seatMapJsonArr=new JSONArray();
					
					JSONArray odoRqJsonArr=airItinJson.getJSONArray(JSON_PROP_ORIGDESTOPTS);
					
					JSONArray odoRsJsonArr=new JSONArray();
					for(int y=0;y<odoRqJsonArr.length();y++) {
						
						JSONObject odoRsJson=new JSONObject();
						
						ArrayList<Integer> sequenceList=odoMap.get(y);
						JSONArray flightSegRsJsonArr=new JSONArray();
						for(Integer seqCount:sequenceList) {
							
							
							
							Element wrapperElem=wrapprElems[seqCount];
							Element airSeatMapRQElem = XMLUtils.getFirstElementAtXPath(wrapperElem, "./ota:OTA_AirSeatMapRS");
							
						     Element seatMapResElem = XMLUtils.getFirstElementAtXPath(airSeatMapRQElem, "./ota:SeatMapResponses/ota:SeatMapResponse");
						    		 //Element referenceElem1[] = XMLUtils.getElementsAtXPath(groundServiceElem, "./ota:Reference");
							Element flightSegInfo []=  XMLUtils.getElementsAtXPath(seatMapResElem, "./ota:FlightSegmentInfo");
						
							
							
							for(Element flightSeg : flightSegInfo) {
								JSONObject flightSegRsJson=new JSONObject();
								flightSegRsJson.put(JSON_PROP_DESTLOC,  XMLUtils.getValueAtXPath(flightSeg, "./ota:ArrivalAirport/@LocationCode"));
								flightSegRsJson.put(JSON_PROP_DEPARTDATE,  XMLUtils.getValueAtXPath(flightSeg, "./@DepartureDateTime"));
								flightSegRsJson.put(JSON_PROP_ORIGLOC,  XMLUtils.getValueAtXPath(flightSeg, "./ota:DepartureAirport/@LocationCode"));
								flightSegRsJson.put(JSON_PROP_FLIGHTNBR,  XMLUtils.getValueAtXPath(flightSeg, "./@FlightNumber"));
							
								
								/*JSONObject operatingAirline = new JSONObject();
								operatingAirline.put(JSON_PROP_AIRLINECODE, XMLUtils.getValueAtXPath(flightSeg, "./@FlightNumber"))*/
								
								
								Element seatMapElem[] = XMLUtils.getElementsAtXPath(seatMapResElem, "./ota:SeatMapDetails");
								JSONArray seatMapArr = new JSONArray();
								
								for(Element seatMaps : seatMapElem) {
								JSONObject seatMapJson = new JSONObject();
								seatMapJson.put(JSON_PROP_FLIGHTNBR, XMLUtils.getValueAtXPath(flightSeg, "./@FlightNumber"));
								seatMapJson.put(JSON_PROP_FLIGHTREFRPH,  XMLUtils.getValueAtXPath(flightSeg, "./ota:TPA_Extensions/air:ExtendedRPH"));
								
								Element cabinClassElem[] = XMLUtils.getElementsAtXPath(seatMaps, "./ota:CabinClass");
								JSONArray cabinClassArr = new JSONArray();
								for(Element cabinClass : cabinClassElem) {
								JSONObject cabinClassJson = new JSONObject();
								cabinClassJson.put(JSON_PROP_UPPERDECKIND, XMLUtils.getValueAtXPath(cabinClass, "./@UpperDeckInd"));
								cabinClassJson.put(JSON_PROP_STARTINGROW, XMLUtils.getValueAtXPath(cabinClass, "./@StartingRow"));
								cabinClassJson.put(JSON_PROP_ENDINGROW, XMLUtils.getValueAtXPath(cabinClass, "./@EndingRow"));
								
								Element rowInfoElem[] = XMLUtils.getElementsAtXPath(cabinClass, "./ota:RowInfo");
								JSONArray rowInfoArr = new JSONArray();
								for(Element rowInfos : rowInfoElem) {
									JSONObject rowInfoJson = new JSONObject();
									rowInfoJson.put(JSON_PROP_CABINTYPE,  XMLUtils.getValueAtXPath(rowInfos, "./@CabinType"));
									rowInfoJson.put(JSON_PROP_ROWNBR,  XMLUtils.getValueAtXPath(rowInfos, "./@RowNumber"));
									rowInfoArr.put(rowInfoJson);
								
									
									Element seatInfoElem[] = XMLUtils.getElementsAtXPath(rowInfos, "./ota:SeatInfo");
									JSONArray seatInfoArr = new JSONArray();
									for(Element seatInfos : seatInfoElem) {
										
										JSONObject seatInfoJson = new JSONObject();
										seatInfoJson.put(JSON_PROP_BULKHEADIND,XMLUtils.getValueAtXPath(seatInfos, "./@BulkheadInd"));
										seatInfoJson.put(JSON_PROP_EXITROWIND,XMLUtils.getValueAtXPath(seatInfos, "./@ExitRowInd"));
										seatInfoJson.put(JSON_PROP_GALLYIND,XMLUtils.getValueAtXPath(seatInfos, "./@GallyInd"));
										seatInfoJson.put(JSON_PROP_SEATNBR,XMLUtils.getValueAtXPath(seatInfos, "./ota:Summary/@SeatNumber"));
										seatInfoJson.put(JSON_PROP_SEATSEQNBR,XMLUtils.getValueAtXPath(seatInfos, "./ota:Summary/@SeatSequenceNumber"));
										seatInfoJson.put(JSON_PROP_AVAILIND,XMLUtils.getValueAtXPath(seatInfos, "./ota:Summary/@AvailableInd"));
										seatInfoJson.put(JSON_PROP_OCCUPIEDIND,XMLUtils.getValueAtXPath(seatInfos, "./ota:Summary/@OccupiedInd"));
										seatInfoJson.put(JSON_PROP_SEATPREF,XMLUtils.getValueAtXPath(seatInfos, "./ota:Summary/@SeatPreference"));
										
										
										
										Element serviceElem[] = XMLUtils.getElementsAtXPath(seatInfos, "./ota:Service");
										JSONArray serviceFeesArr = new JSONArray();
										for(Element services : serviceElem) {
											
											JSONObject serviceJson = new JSONObject();
											serviceJson.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(services, "./ota:Fee/@Amount"));
											serviceJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(services, "./ota:Fee/@CurrencyCode"));
											serviceJson.put(JSON_PROP_TYPE, XMLUtils.getValueAtXPath(services, "./ota:Fee/@Type"));
											serviceJson.put(JSON_PROP_CODE, XMLUtils.getValueAtXPath(services, "./ota:Fee/@Code"));
											
											Element taxesElem = XMLUtils.getFirstElementAtXPath(services, "./ota:Fee/ota:Taxes");
											JSONObject taxesJson = new JSONObject();
											taxesJson.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(taxesElem, "./ota:Amount"));
											taxesJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(taxesElem, "./ota:CurrencyCode"));
											
											Element taxElem[] = XMLUtils.getElementsAtXPath(taxesElem, "./ota:Tax");
											JSONArray taxArr = new JSONArray();
											for(Element taxes : taxElem) {
											JSONObject taxJson = new JSONObject();
											taxJson.put(JSON_PROP_AMOUNT, XMLUtils.getValueAtXPath(taxes, "./ota:Amount"));
											taxJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(taxes, "./ota:CurrencyCode"));
											taxArr.put(taxJson);
											
											}
											
											taxesJson.put(JSON_PROP_TAX, taxArr);
											serviceJson.put(JSON_PROP_TAXES, taxesJson);
											serviceFeesArr.put(serviceJson);
											
										}
										
										seatInfoJson.put(JSON_PROP_SERVICEFEES, serviceFeesArr);
										
										Element featuresElem[] = XMLUtils.getElementsAtXPath(seatInfos, "./ota:Features");
										JSONArray featuresArr = new JSONArray();
										for(Element features : featuresElem) {
											
											JSONObject featuresJson = new JSONObject();
											featuresJson.put(JSON_PROP_EXTENSION,  XMLUtils.getValueAtXPath(features, "./@extension"));
											featuresArr.put(featuresJson);
										}
										
										seatInfoJson.put(JSON_PROP_FEATURES, featuresArr);
										seatInfoArr.put(seatInfoJson);
										
									}
									rowInfoJson.put(JSON_PROP_SEATINFO, seatInfoArr);
									//rowInfoArr.put(rowInfoJson);
									
								}
								cabinClassJson.put(JSON_PROP_ROWINFO, rowInfoArr);
										
									
										cabinClassArr.put(cabinClassJson);		
								}
							
								seatMapJson.put(JSON_PROP_CABINCLASS,cabinClassArr);
								
								
								seatMapArr.put(seatMapJson);
								}
								flightSegRsJson.put(JSON_PROP_SEATMAP, seatMapArr);
								
								flightSegRsJsonArr.put(flightSegRsJson);
								
							}
							
							
							odoRsJson.put(JSON_PROP_FLIGHTSEG, flightSegRsJsonArr);
						
				
						}
						
						odoRsJson.put(JSON_PROP_FLIGHTSEG, flightSegRsJsonArr);
						odoRsJsonArr.put(odoRsJson);
					}
										
					
					airItinRes.put(JSON_PROP_ORIGDESTOPTS, odoRsJsonArr);
					priceItinRsJson.put(JSON_PROP_AIRITINERARY, airItinRes);
					//priceItinRsJson.put(JSON_PROP_ORIGDESTOPTS, odoRsJsonArr);
					
					
					priceItinRsJsonArr.put(priceItinRsJson);
			
				}
				
				/*for (Element wrapprElem : wrapprElems) {
					
				
					JSONObject fareInfoJson=new JSONObject();
					//getFareRules(fareInfoJson,wrapprElem,flightSegSeqMap);
					
					fareInfoJsonArr.put(fareInfoJson);
					
				}*/
				resBodyJson.put(JSON_PROP_PRICEDITIN, priceItinRsJsonArr);
				resJson.put(JSON_PROP_RESBODY, resBodyJson);
				
			return resJson.toString();
		}
			catch (Exception x) {
				logger.error("Exception during request processing", x);
				throw new RequestProcessingException(x);
			}
		}




	private static void validateRequestParameters(JSONObject reqJson) throws ValidationException {
		AirRequestValidator.validateTripType(reqJson);
		AirRequestValidator.validatePassengerCounts(reqJson);
		AirRequestValidator.validatePaxDetails(reqJson);
	}




	//private static Element createSIRequest(OperationConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson,
	private static Element createSIRequest(ServiceConfig opConfig, UserContext usrCtx, JSONObject reqHdrJson,
			JSONObject reqBodyJson, Map<Integer, Map<Integer, ArrayList<Integer>>> priceItinMap) throws ValidationException {
		Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true);
		Document ownerDoc = reqElem.getOwnerDocument();
		
		Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./airi:RequestBody");
		
		int count=0;
		AirSearchProcessor.createHeader(reqHdrJson, reqElem);
		
		JSONArray pricedItinsJSONArr = reqBodyJson.getJSONArray(JSON_PROP_PRICEDITIN);
		Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./air:OTA_AirSeatMapRQWrapper");
		reqBodyElem.removeChild(wrapperElem);
		//Map<Integer, Map<Integer, ArrayList<Integer>>> priceItinMap = new HashMap<Integer, Map<Integer, ArrayList<Integer>>>();
		//Map<Integer, ArrayList<Integer>> odoMap = new HashMap<Integer, ArrayList<Integer>>();
		
		for(int y = 0 ; y < pricedItinsJSONArr.length() ; y++) {
		JSONObject pricedItinJson = pricedItinsJSONArr.getJSONObject(y);
		String suppID = pricedItinJson.getString(JSON_PROP_SUPPREF);
		JSONObject airItenObj = pricedItinJson.getJSONObject(JSON_PROP_AIRITINERARY);
		JSONArray odoArr = airItenObj.optJSONArray(JSON_PROP_ORIGDESTOPTS);
		if(odoArr==null) {
			throw new ValidationException("TRLERR025");
		}
		//define a array list of integer here
		
		Map<Integer, ArrayList<Integer>> odoMap = new HashMap<Integer, ArrayList<Integer>>();
		for(int i = 0 ; i < odoArr.length() ; i++ ) {
			JSONObject odoJson = odoArr.getJSONObject(i);
			//JSONObject flightSegObj = odoArr.getJSONObject(i);
			 //define a map of <Integer, ArrayList<Integer>> 
			ArrayList<Integer> cntList =new ArrayList<Integer>();
			JSONArray flightSegArr = odoJson.getJSONArray(JSON_PROP_FLIGHTSEG);
			
			for(int j = 0 ; j < flightSegArr.length() ; j++) {
				JSONObject flightSegObj = flightSegArr.getJSONObject(j);
				Element suppWrapperElem = (Element) wrapperElem.cloneNode(true);
				reqBodyElem.appendChild(suppWrapperElem);
			
			Element supplierIDElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:SupplierID");
			supplierIDElem.setTextContent(suppID);
			
			ProductSupplier prodSupplier=usrCtx.getSupplierForProduct(PROD_CATEG_TRANSPORT, PROD_CATEG_SUBTYPE_FLIGHT,suppID);
			Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem, "./air:RequestHeader/com:SupplierCredentialsList");
			Element suppCredsElem = XMLUtils.getFirstElementAtXPath(suppCredsListElem, String.format("./com:SupplierCredentials[com:SupplierID = '%s']", suppID));
			if (suppCredsElem == null) {
				suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
			}
			
			//fill list here
			cntList.add(count);
			
			Element sequenceElem = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./air:Sequence");
			sequenceElem.setTextContent(String.valueOf(count++));
			
			//priceItnMap.put(y, new ArrayList<Integer>(count));
			
			Element airSeatMapRQ = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_AirSeatMapRQ");
			airSeatMapRQ.setAttribute("TransactionIdentifier", pricedItinJson.optString(JSON_PROP_TRANSACTID));
			
			Element seatMapReqs = ownerDoc.createElementNS(NS_OTA, "ota:SeatMapRequests");
			Element seatMapReq = ownerDoc.createElementNS(NS_OTA, "ota:SeatMapRequest");
			Element flightSegInfo = ownerDoc.createElementNS(NS_OTA, "ota:FlightSegmentInfo");
			JSONArray fareInfoArr = flightSegObj.getJSONArray(JSON_PROP_FAREINFO);
			for(int k = 0 ; k < fareInfoArr.length() ; k++) {
				
				
				JSONObject fareInfoJson = fareInfoArr.getJSONObject(k);
				if( fareInfoJson.getString(JSON_PROP_PAXTYPE).equalsIgnoreCase("ADT")) {
			flightSegInfo.setAttribute("FareBasisCode", fareInfoJson.getString(JSON_PROP_FAREBASISCODE));
				}
			}
			flightSegInfo.setAttribute("ResBookDesigCode", flightSegObj.getString(JSON_PROP_RESBOOKDESIG));
			flightSegInfo.setAttribute("InfoSource", "");
			flightSegInfo.setAttribute("DepartureDateTime", flightSegObj.getString(JSON_PROP_DEPARTDATE));
			flightSegInfo.setAttribute("FlightNumber", flightSegObj.getJSONObject(JSON_PROP_OPERAIRLINE).getString(JSON_PROP_FLIGHTNBR));
			flightSegInfo.setAttribute("ArrivalDateTime", flightSegObj.getString(JSON_PROP_ARRIVEDATE));
			flightSegInfo.setAttribute("ConnectionType", flightSegObj.optString(JSON_PROP_CONNTYPE));
			
			Element deptAirport = ownerDoc.createElementNS(NS_OTA, "ota:DepartureAirport");
			deptAirport.setAttribute("LocationCode", flightSegObj.getString(JSON_PROP_ORIGLOC));
			flightSegInfo.appendChild(deptAirport);
			
			Element arrivalAirport = ownerDoc.createElementNS(NS_OTA, "ota:ArrivalAirport");
			arrivalAirport.setAttribute("LocationCode", flightSegObj.getString(JSON_PROP_DESTLOC));
			flightSegInfo.appendChild(arrivalAirport);
			
			Element operatingAirline = ownerDoc.createElementNS(NS_OTA, "ota:OperatingAirline");
			operatingAirline.setAttribute("Code",  flightSegObj.getJSONObject(JSON_PROP_OPERAIRLINE).getString(JSON_PROP_AIRLINECODE));
			operatingAirline.setAttribute("FlightNumber",  flightSegObj.getJSONObject(JSON_PROP_OPERAIRLINE).getString(JSON_PROP_FLIGHTNBR));
			flightSegInfo.appendChild(operatingAirline);
			
			Element tpa_Extention = ownerDoc.createElementNS(NS_OTA, "ota:TPA_Extensions");
			Element extendedRph = ownerDoc.createElementNS(NS_AIR, "air:ExtendedRPH");
			extendedRph.setTextContent(flightSegObj.getString(JSON_PROP_EXTENDEDRPH));
			tpa_Extention.appendChild(extendedRph);
			Element quoteId = ownerDoc.createElementNS(NS_AIR, "air:QuoteID");
			quoteId.setTextContent(flightSegObj.getString(JSON_PROP_QUOTEID));
			tpa_Extention.appendChild(quoteId);
			if(flightSegObj.optString(JSON_PROP_CABINTYPE)==null) {
				throw new ValidationException("TRLERR035");
			}
			Element cabinType = ownerDoc.createElementNS(NS_AIR, "air:CabinType");
			cabinType.setTextContent(flightSegObj.getString(JSON_PROP_CABINTYPE));
			tpa_Extention.appendChild(cabinType);
			
			flightSegInfo.appendChild(tpa_Extention);
			
			Element marketingAirline = ownerDoc.createElementNS(NS_OTA, "ota:MarketingAirline");
			JSONObject marketingAirJson = flightSegObj.getJSONObject(JSON_PROP_MARKAIRLINE);
			marketingAirline.setAttribute("Code", marketingAirJson.getString(JSON_PROP_AIRLINECODE));
			//marketingAirline.setAttribute("FlightNumber", marketingAirJson.getString("flightNumber"));
			flightSegInfo.appendChild(marketingAirline);
			
			seatMapReq.appendChild(flightSegInfo);
			seatMapReqs.appendChild(seatMapReq);
			airSeatMapRQ.appendChild(seatMapReqs);
			
			Element airTravelers = ownerDoc.createElementNS(NS_OTA, "ota:AirTravelers");
			JSONArray paxDetlArr =  reqBodyJson.getJSONArray(JSON_PROP_PAXDETAILS);
			for(int p = 0 ; p < paxDetlArr.length() ; p++) {
				JSONObject paxDetlJson = paxDetlArr.getJSONObject(p);
				
				Element airTraveler = ownerDoc.createElementNS(NS_OTA, "ota:AirTraveler");
				airTraveler.setAttribute("PassengerTypeCode", paxDetlJson.getString(JSON_PROP_PAXTYPE));
				
				Element personName = ownerDoc.createElementNS(NS_OTA, "ota:PersonName");
				Element namePrefix = ownerDoc.createElementNS(NS_OTA, "ota:NamePrefix");
				namePrefix.setTextContent(paxDetlJson.getString(JSON_PROP_TITLE));
				personName.appendChild(namePrefix);
				Element givenName = ownerDoc.createElementNS(NS_OTA, "ota:GivenName");
				givenName.setTextContent(paxDetlJson.getString(JSON_PROP_FIRSTNAME));
				personName.appendChild(givenName);
				Element surname = ownerDoc.createElementNS(NS_OTA, "ota:Surname");
				surname.setTextContent(paxDetlJson.getString(JSON_PROP_SURNAME));
				personName.appendChild(surname);
				airTraveler.appendChild(personName);
				
				Element passengerTypeQuantity = ownerDoc.createElementNS(NS_OTA, "ota:PassengerTypeQuantity");
				
				String paxdob = paxDetlJson.getString(JSON_PROP_DATEOFBIRTH);
				LocalDate dob = LocalDate.parse(paxdob);
				
				LocalDate localDate = LocalDate.now();
				//Period age = Period.between(dob, localDate);
				long years = ChronoUnit.YEARS.between(dob, localDate);
				String age = Long.toString(years);

				passengerTypeQuantity.setAttribute("Age", age.toString());
				
				airTraveler.appendChild(passengerTypeQuantity);
				airTravelers.appendChild(airTraveler);
		
			}
			airSeatMapRQ.appendChild(airTravelers);
			
			suppWrapperElem.appendChild(airSeatMapRQ);
			
			}
			odoMap.put(i, cntList);
		}
		//fill map here with pricedItin number as key and array list as value
		/*   for (Map.Entry me : priceItnMap.entrySet()) {
		          System.out.println("Key: "+me.getKey() + " & Value: " + me.getValue());
		        }*/
			priceItinMap.put(y, odoMap);
		
		}
		
		
		return reqElem;
		
	}
	
}
