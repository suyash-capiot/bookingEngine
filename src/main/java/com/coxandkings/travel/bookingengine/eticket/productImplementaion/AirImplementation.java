package com.coxandkings.travel.bookingengine.eticket.productImplementaion;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.eticket.exceptions.FinanceException;
import com.coxandkings.travel.bookingengine.eticket.systemlogin.MDMDataSource;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.FileType;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.TemplateTypes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.DynamicAttributes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.MainObject;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.service.impl.TemplateServiceImpl;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirConstants;

public class AirImplementation implements AirConstants {
	private static final Logger logger = Logger.getLogger(AirImplementation.class);

	public void generateTemplate(JSONObject retrieveResponse, String uniqueId) {
		logger.info("In AirImplementation class calling generateTemplate() method");
		MainObject airDynamicProject = new MainObject();
		airDynamicProject.setUniqueId(uniqueId);
		airDynamicProject.setCompanyName("Ezeego");

		airDynamicProject.setFileType(generateFileType(retrieveResponse));
		// airDynamicProject.setFileType(FileType.Air1Adult);
		MDMDataSource data = new MDMDataSource();
		try {
			airDynamicProject.setToken(data.getToken().getToken());
		} catch (FinanceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// airDynamicProject.setToken("Bearer
		// eyJ0eXBlIjoiSldUIiwiYWxnIjoiSFMyNTYifQ==.eyJ1c2VyIjp7Il9pZCI6IlVTUjEwMDAyOSIsInVzZXJEZXRhaWxzIjp7ImJhY2tVcFVzZXJJZCI6IlVTUjEwMDE3OSIsImxhc3RMb2dnZWRPbiI6IjIwMTgtMDctMDVUMDg6NDg6MTUuNzgxWiIsImVtcGxveWVlSWQiOiIxMyIsImZpcnN0TmFtZSI6IkRlbW8iLCJtaWRkbGVOYW1lIjoiIiwibGFzdE5hbWUiOiJVc2VyVHdvIiwiZW1haWwiOiJhbW9sLmJvcnNlQGNveGFuZGtpbmdzLmNvbSIsIm1vYmlsZSI6Ijc4OTk5Mjg2NjYiLCJkZXNpZ25hdGlvbiI6IkFzc29jaWF0ZSIsInJlcG9ydGluZ01hbmFnZXIiOiJVU1IxMDAwODAiLCJpc01hbmFnZXIiOmZhbHNlLCJCVSI6IkJsdWUgU3F1YXJlIiwiU0JVIjoiRG9tZXN0aWMiLCJzYWxlc09mZmljZSI6IlRFU1QgU2FsZXMgRGlnIiwic2FsZXNHcm91cCI6IlMxIiwiY29tcGFuaWVzIjpbeyJjb21wYW55TmFtZSI6IkNveCBhbmQgS2luZ3MiLCJyb2xlTmFtZSI6IkNlbnRyYWwgVGVhbSBVc2VyIiwicm9sZUlkIjoiUk9MRTEwMDAxMSIsImNvbXBhbnlJZCI6IkdDMjExMDAxNjIiLCJkZWZhdWx0Q29tcGFueSI6ZmFsc2UsIl9pZCI6IjVhNjlkNzg1NmNiZGI2MzNlNDAxOGRlYyJ9LHsiY29tcGFueU5hbWUiOiJFemVlZ28iLCJjb21wYW55SWQiOiJHQzIyIiwicm9sZU5hbWUiOiJIUiBtYW5hZ2VyIiwicm9sZUlkIjoiUk9MRTEwMDAwMiIsImRlZmF1bHRDb21wYW55Ijp0cnVlLCJfaWQiOiI1YWQ2ZTAwM2Y5NWY3MDA3NjM0M2RlNmUifV19fSwiY3VycmVudENvbXBhbnkiOnsiX2lkIjoiR0MyMiIsIm5hbWUiOiJFemVlZ28iLCJnb2MiOnsiX2lkIjoiR09DMTAwMDAyIiwibmFtZSI6IkV6ZWVnbzEifSwiZ2MiOnsiX2lkIjoiR0MyIiwibmFtZSI6IkV6ZWVnbzEifX0sInRpbWVTdGFtcCI6IjIwMTgtMDctMDVUMDk6NTY6MTUuOTY1WiJ9.kYDJA9N2LJGH5bQnTF3ALMv3jGGD+UIdDz538OT3RCQ=");
		airDynamicProject.setTemplateTypes(TemplateTypes.Document);

		List<DynamicAttributes> airVariableList = new ArrayList<>();
		prepareVariablesForAir(airVariableList, retrieveResponse);
		airDynamicProject.setDynamicVariables(airVariableList);

		logger.info("Input parameters for main object are " + airDynamicProject);

		TemplateServiceImpl templateServiceimpl = new TemplateServiceImpl();
		templateServiceimpl.postTempalte(airDynamicProject);
		
	}

	private FileType generateFileType(JSONObject retrieveResponse) {
		int adultCount = 0;
		int childCount = 0;
		String functionName;

		JSONArray productlist = retrieveResponse.getJSONObject("responseBody").getJSONArray("products");

		for (int i = 0; i <= productlist.length() - 1; i++) {
			if (productlist.getJSONObject(i).get("productSubCategory").equals("Flight")) {
				JSONArray passengerArray = productlist.getJSONObject(0).getJSONObject("orderDetails")
						.getJSONArray("paxInfo");
				System.out.println("pax info" + passengerArray);
				System.out.println("length " + passengerArray.length());

				for (int j = 0; j <= passengerArray.length() - 1; j++) {
					System.out.println("at " + j + " " + passengerArray.getJSONObject(j));
					if (passengerArray.getJSONObject(j).get("paxType").equals("ADT")) {
						adultCount++;
					} else if (passengerArray.getJSONObject(j).get("paxType").equals("CHD")) {
						childCount++;
					}

				}
			}
		}
		System.out.println("adult " + adultCount + " Childcount" + childCount);
		functionName = "Air" + adultCount + "Adult" + childCount + "Child";
		if (functionName.equals("Air1Adult0Child")) {
			return FileType.Air1Adult;
		} else if (functionName.equals("Air2Adult0Child")) {
			return FileType.Air1Adult;
			// Below code added for testing purpose
		} else if (functionName.equals("Air2Adult2Child")) {
			return FileType.Air1Adult;

		} else if (functionName.equals("Air3Adult0Child")) {
			return FileType.Air1Adult;

		} else if (functionName.equals("Air4Adult0Child")) {
			return FileType.Air1Adult;
		} else if (functionName.equals("Air5Adult0Child")) {
			return FileType.Air1Adult;
		} else if (functionName.equals("Air6Adult0Child")) {
			return FileType.Air1Adult;
		} else if (functionName.equals("Air7Adult0Child")) {
			return FileType.Air1Adult;
		} else if (functionName.equals("Air8Adult0Child")) {
			return FileType.Air1Adult;
		} else if (functionName.equals("Air1Adult1Child")) {
			return FileType.Air1Adult;
		} else if (functionName.equals("Air1Adult2Child")) {
			return FileType.Air1Adult;
		} else
			return null;

	}

	private List<DynamicAttributes> prepareVariablesForAir(List<DynamicAttributes> airVariableList,
			JSONObject retrieveresponse) {
		JSONArray productlist, originDestinationOptions;
		
		logger.info("preparing dynamic attributes for AIR product in prepareVariablesForAir() method");
		try {
		productlist = retrieveresponse.getJSONObject(JSON_PROP_REQBODY).getJSONArray(JSON_PROP_PROD);
		originDestinationOptions = productlist.getJSONObject(0).getJSONObject("orderDetails")
				.getJSONObject("flightDetails").getJSONArray("originDestinationOptions");
		JSONArray flightSegment = originDestinationOptions.getJSONObject(0).getJSONArray("flightSegment");
		JSONArray paxInfo = productlist.getJSONObject(0).getJSONObject("orderDetails").getJSONArray("paxInfo");

		DynamicAttributes ezeego1_Ref = new DynamicAttributes();
		ezeego1_Ref.setName("BookingNumber");
		ezeego1_Ref.setValue(retrieveresponse.getJSONObject("responseBody").getString("bookID"));
		airVariableList.add(ezeego1_Ref);

		DynamicAttributes Supplier_Ref = new DynamicAttributes();
		Supplier_Ref.setName("SupplierReferenceNo");
		Supplier_Ref.setValue(productlist.getJSONObject(0).getJSONObject("orderDetails").getString("airlinePNR"));
		airVariableList.add(Supplier_Ref);

		DynamicAttributes From_To = new DynamicAttributes();
		From_To.setName("FromTo");
		From_To.setValue(new StringBuilder(flightSegment.getJSONObject(0).getString("originLocation")).append(" To ")
				.append(flightSegment.getJSONObject(0).getString("destinationLocation")).toString());
		airVariableList.add(From_To);

		DynamicAttributes marketingAirline = new DynamicAttributes();
		marketingAirline.setName("FlightDetails");
		marketingAirline.setValue(new StringBuilder(
				flightSegment.getJSONObject(0).getJSONObject("marketingAirline").getString("airlineCode")).append(" - ")
						.append(flightSegment.getJSONObject(0).getJSONObject("marketingAirline")
								.getString("flightNumber"))
						.toString());
		airVariableList.add(marketingAirline);

		DynamicAttributes departureDate = new DynamicAttributes();
		departureDate.setName("DepartureDate");
		departureDate.setValue(flightSegment.getJSONObject(0).getString("departureDate").substring(0, 10));
		airVariableList.add(departureDate);

		DynamicAttributes departureTime = new DynamicAttributes();
		departureTime.setName("DepartureTime");
		departureTime.setValue(flightSegment.getJSONObject(0).getString("departureDate").substring(11, 16));
		airVariableList.add(departureTime);

		DynamicAttributes arrivalTime = new DynamicAttributes();
		arrivalTime.setName("ArrivalTime");
		arrivalTime.setValue(flightSegment.getJSONObject(0).getString("arrivalDate").substring(11, 16));
		airVariableList.add(arrivalTime);

		DynamicAttributes maximumStops = new DynamicAttributes();
		maximumStops.setName("Stops");
		maximumStops.setValue(Integer.toString(flightSegment.length() - 1));
		airVariableList.add(maximumStops);

		DynamicAttributes cabinType = new DynamicAttributes();
		cabinType.setName("CabinType");
		cabinType.setValue(flightSegment.getJSONObject(0).getString("cabinType"));
		airVariableList.add(cabinType);

		DynamicAttributes status = new DynamicAttributes();
		status.setName("BookingStatus");
		status.setValue(productlist.getJSONObject(0).getString("status"));
		airVariableList.add(status);

		DynamicAttributes paxName = new DynamicAttributes();
		paxName.setName("PassengerName");
		paxName.setValue(new StringBuilder(paxInfo.getJSONObject(0).getString("firstName")).append(" ")
				.append(paxInfo.getJSONObject(0).getString("lastName")).toString());
		airVariableList.add(paxName);

		DynamicAttributes ticketNumber = new DynamicAttributes();
		ticketNumber.setName("TicketNumber");
		ticketNumber.setValue(paxInfo.getJSONObject(0).getString("ticketNumber"));
		airVariableList.add(ticketNumber);

		DynamicAttributes baseFare = new DynamicAttributes();
		baseFare.setName("AirFare");
		baseFare.setValue(productlist.getJSONObject(0).getJSONObject("orderDetails")
				.getJSONObject("orderTotalPriceInfo").getJSONObject("baseFare").get("amount").toString());
		airVariableList.add(baseFare);

		DynamicAttributes taxes = new DynamicAttributes();
		taxes.setName("Taxes");
		taxes.setValue(productlist.getJSONObject(0).getJSONObject("orderDetails").getJSONObject("orderTotalPriceInfo")
				.getJSONObject("taxes").get("amount").toString());
		airVariableList.add(taxes);

		logger.info("Dynamic attributes for Air are  " + airVariableList);
		
		}catch(Exception e) {
			e.printStackTrace();			
		}
		return airVariableList;

	}
		

}
