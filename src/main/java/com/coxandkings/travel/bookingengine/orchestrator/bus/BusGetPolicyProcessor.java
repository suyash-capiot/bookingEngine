package com.coxandkings.travel.bookingengine.orchestrator.bus;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.bus.BusConfig;
import com.coxandkings.travel.bookingengine.userctx.ProductSupplier;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;
import com.coxandkings.travel.bookingengine.utils.Utils;
import com.coxandkings.travel.bookingengine.utils.xml.XMLUtils;

public class BusGetPolicyProcessor implements BusConstants{

	public static String process(JSONObject reqJson) {
		
		try
		{
			//OperationConfig opConfig = BusConfig.getOperationConfig("GetPolicies");
			ServiceConfig opConfig = BusConfig.getOperationConfig("GetPolicies");
			Element reqElem = (Element) opConfig.getRequestXMLShell().cloneNode(true); // xmshell
			Document ownerDoc = reqElem.getOwnerDocument();
			
			Element reqBodyElem = XMLUtils.getFirstElementAtXPath(reqElem, "./busi:RequestBody");
			Element wrapperElem = XMLUtils.getFirstElementAtXPath(reqBodyElem, "./bus:OTA_BusGetCancellationPolicyRQWrapper");
			reqBodyElem.removeChild(wrapperElem);
			
           
			
			JSONObject reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
			JSONObject reqBodyJson = reqJson.getJSONObject(JSON_PROP_REQBODY);

			String sessionID = reqHdrJson.getString(JSON_PROP_SESSIONID);
			String transactionID = reqHdrJson.getString(JSON_PROP_TRANSACTID);
			String userID = reqHdrJson.getString(JSON_PROP_USERID);
			
			UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
			List<ProductSupplier> prodSuppliers = usrCtx.getSuppliersForProduct(PROD_CATEG_TRANSPORT,PROD_CATEG_SUBTYPE_BUS);
			                                                                    
			
			BusSearchProcessor.createHeader(reqElem, sessionID, transactionID, userID);
			
            JSONArray busserviceJSONArr = reqBodyJson.getJSONArray(JSON_PROP_SERVICE);
			
			for(int i=0;i<busserviceJSONArr.length();i++)
			{
				JSONObject busServiceJson = busserviceJSONArr.getJSONObject(i);
				String suppID = busServiceJson.getString(JSON_PROP_SUPPREF);
				if(busServiceJson.has("errorMessage"))
				{
					continue;
				}
				Element suppWrapperElem = null;
				suppWrapperElem = (Element) wrapperElem.cloneNode(true);
				reqBodyElem.appendChild(suppWrapperElem);
				
				
				Element suppCredsListElem = XMLUtils.getFirstElementAtXPath(reqElem,
						"./bus:RequestHeader/com:SupplierCredentialsList");
				for (ProductSupplier prodSupplier : prodSuppliers) {
					suppCredsListElem.appendChild(prodSupplier.toElement(ownerDoc));
				}
		        
		        XMLUtils.setValueAtXPath(suppWrapperElem, "./bus:SupplierID", busServiceJson.getString("supplierRef"));
		        XMLUtils.setValueAtXPath(suppWrapperElem, "./bus:Sequence", String.valueOf(i));
		        
		        Element otaGetPolicy = XMLUtils.getFirstElementAtXPath(suppWrapperElem, "./ota:OTA_BusGetCancellationPolicyRQ");
		        Element newElem;
		        
		        if(busServiceJson.get(JSON_PROP_OPERATORID).toString().isEmpty()==false)
				  {
					   newElem = ownerDoc.createElementNS("http://www.opentravel.org/OTA/2003/05", "operatorId");
					  newElem.setTextContent(busServiceJson.get("operatorId").toString());
					  otaGetPolicy.appendChild(newElem);
				  }
		        
		        if(busServiceJson.get(JSON_PROP_SERVICEID).toString().isEmpty()==false)
				  {
					   newElem = ownerDoc.createElementNS("http://www.opentravel.org/OTA/2003/05", "serviceId");
					  newElem.setTextContent(busServiceJson.get("serviceId").toString());
					  otaGetPolicy.appendChild(newElem);
				  }
		        if(busServiceJson.get(JSON_PROP_SOURCE).toString().isEmpty()==false)
				  {
					   newElem = ownerDoc.createElementNS("http://www.opentravel.org/OTA/2003/05", "sourceStationId");
					  newElem.setTextContent(busServiceJson.get(JSON_PROP_SOURCE).toString());
					  otaGetPolicy.appendChild(newElem);
				  }
		        if(busServiceJson.get(JSON_PROP_DESTINATION).toString().isEmpty()==false)
				  {
					   newElem = ownerDoc.createElementNS("http://www.opentravel.org/OTA/2003/05", "destinationStationId");
					  newElem.setTextContent(busServiceJson.get(JSON_PROP_DESTINATION).toString());
					  otaGetPolicy.appendChild(newElem);
				  }
		        if(busServiceJson.get("journeyDate").toString().isEmpty()==false)
				  {
					   newElem = ownerDoc.createElementNS("http://www.opentravel.org/OTA/2003/05", "journeyDate");
					  newElem.setTextContent(busServiceJson.get("journeyDate").toString());
					  otaGetPolicy.appendChild(newElem);
				  }
		        if(busServiceJson.get("PNRNo").toString().isEmpty()==false)
				  {
					   newElem = ownerDoc.createElementNS("http://www.opentravel.org/OTA/2003/05", "PNRNo");
					  newElem.setTextContent(busServiceJson.get("PNRNo").toString());
					  otaGetPolicy.appendChild(newElem);
				  }
		        if(busServiceJson.get("ticketNo").toString().isEmpty()==false)
				  {
					   newElem = ownerDoc.createElementNS("http://www.opentravel.org/OTA/2003/05", "TicketNo");
					  newElem.setTextContent(busServiceJson.get("ticketNo").toString());
					  otaGetPolicy.appendChild(newElem);
				  }
		        
			}
			

			  Element resElem = null;
	          //resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getSIServiceURL(), BusConfig.getHttpHeaders(), reqElem);
			  resElem = HTTPServiceConsumer.consumeXMLService("SI", opConfig.getServiceURL(), opConfig.getHttpHeaders(), reqElem);
	          if (resElem == null) {
	          	throw new Exception("Null response received from SI");
	          }

	          
	          JSONObject resBodyJson = new JSONObject();
		      JSONArray getpolicyJsonArr = new JSONArray();
		      
		        Element[] wrapperElems=BusSeatMapProcessor.sortWrapperElementsBySequence(XMLUtils.getElementsAtXPath(resElem, "./busi:ResponseBody/bus:OTA_BusGetCancellationPolicyRSWrapper"));

		      
		        for (Element wrapperElement : wrapperElems) {
		        	Element resBodyElem = XMLUtils.getFirstElementAtXPath(wrapperElement, "./ota:OTA_BusGetCancellationPolicyRS");
		        	getPoliciJSONArr(resBodyElem, getpolicyJsonArr);
		        }
		        resBodyJson.put("policyResponse", getpolicyJsonArr);
		        
		       
		        JSONObject resJson = new JSONObject();
		        resJson.put(JSON_PROP_RESHEADER, reqHdrJson);
		        resJson.put(JSON_PROP_RESBODY, resBodyJson);
		       
		return resJson.toString();
			
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return null;
		}
		
	}



	private static void getPoliciJSONArr(Element resBodyElem, JSONArray policyJsonArr) {

		JSONObject policyJson = new JSONObject();
		if(XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:Errors")!=null)
    	{
    		
	    	   Element errorMessage=XMLUtils.getFirstElementAtXPath(resBodyElem, "./ota:Errors/ota:Error");
	    	   String errMsgStr=XMLUtils.getValueAtXPath(errorMessage, "./@ShortText");
	    	   policyJson.put("errorMessage", errMsgStr);
    	}
		else
		{
			   policyJson.put(JSON_PROP_SUPPREF, XMLUtils.getValueAtXPath((Element)resBodyElem.getParentNode(), "./bus:SupplierID"));
		       policyJson.put("cancellationDetails", XMLUtils.getValueAtXPath(resBodyElem, "./ota:CancellationDetails"));
		       policyJson.put("service_Name", XMLUtils.getValueAtXPath(resBodyElem, "./ota:Service_Name"));
		       policyJson.put(JSON_PROP_SERVICEID, XMLUtils.getValueAtXPath(resBodyElem, "./ota:Service_key"));
		       policyJson.put("traveler_Agent_Name", XMLUtils.getValueAtXPath(resBodyElem, "./ota:Traveler_Agent_Name"));
		       policyJson.put(JSON_PROP_OPERATORID, XMLUtils.getValueAtXPath(resBodyElem, "./ota:operatorId"));
		       policyJson.put("status", XMLUtils.getValueAtXPath(resBodyElem, "./ota:status"));
		       policyJson.put("isCancellable", Boolean.valueOf(XMLUtils.getValueAtXPath(resBodyElem, "./ota:status")));
		       policyJson.put(JSON_PROP_TOTALFARE, Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(resBodyElem, "./ota:TotalFare"), 0));
		       policyJson.put("RefundAmount", Utils.convertToBigDecimal(XMLUtils.getValueAtXPath(resBodyElem, "./ota:RefundAmount"), 0));
		       policyJson.put(JSON_PROP_CCYCODE, XMLUtils.getValueAtXPath(resBodyElem, "./ota:CurrencyCode"));
		       policyJson.put("ChargePct", XMLUtils.getValueAtXPath(resBodyElem, "./ota:ChargePct"));
		       policyJson.put(JSON_PROP_SEATNO, XMLUtils.getValueAtXPath(resBodyElem, "./ota:SeatNo"));
		       policyJson.put(JSON_PROP_TICKETNO, XMLUtils.getValueAtXPath(resBodyElem, "./ota:TicketNo"));
		       policyJson.put("partiallyCancellable", Boolean.valueOf(XMLUtils.getValueAtXPath(resBodyElem, "./ota:partiallyCancellable")));
		       policyJson.put("responseStatus", XMLUtils.getValueAtXPath(resBodyElem, "./ota:responseStatus"));
		       policyJsonArr.put(policyJson);
		}
      
	}

	
}
