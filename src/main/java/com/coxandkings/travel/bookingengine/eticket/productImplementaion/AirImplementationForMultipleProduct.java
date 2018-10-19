package com.coxandkings.travel.bookingengine.eticket.productImplementaion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.coxandkings.travel.bookingengine.eticket.exceptions.FinanceException;
import com.coxandkings.travel.bookingengine.eticket.systemlogin.MDMDataSource;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.FileType;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.TemplateTypes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.DynamicAttributes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.MainObject;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.service.impl.TemplateServiceImpl;
import com.coxandkings.travel.bookingengine.eticket.uploadFile.FTPUploadFile;
import com.coxandkings.travel.bookingengine.eticket.util.AirEticketConstants;

public class AirImplementationForMultipleProduct implements AirEticketConstants {
	private static final Logger logger = Logger.getLogger(AirImplementation.class);

	public File generateTemplate(JSONObject retrieveResponse, String uniqueId) {
		logger.info("In AirImplementation class calling generateTemplate() method");
		String proudct = "Flight";
		JSONArray productlistArray = retrieveResponse.getJSONObject(JSON_PROP_RESBODY).getJSONArray(JSON_PROP_PRODS);
		StringBuilder br = new StringBuilder();

		try {
			for (int i = 0; i <= productlistArray.length() - 1; i++) {

				if (productlistArray.getJSONObject(i).get(JSON_PROP_PRODSUBCATEGORY).equals(proudct)) {

					MainObject airDynamicProject = new MainObject();
					airDynamicProject.setUniqueId(uniqueId);
					airDynamicProject.setCompanyName("Ezeego");
					// busDynamicProject.setFileType(generateFileType(retrieveResponse));
					airDynamicProject.setFileType(generateFileType(productlistArray.getJSONObject(i)));
					MDMDataSource data = new MDMDataSource();
					try {
						airDynamicProject.setToken(data.getToken().getToken());
					} catch (FinanceException e) { 
						// TODO Auto-generated catch block
						e.printStackTrace();
						logger.info("RetriveResponse in null");
					}
					// busDynamicProject.setToken("Bearer
					// eyJ0eXBlIjoiSldUIiwiYWxnIjoiSFMyNTYifQ==.eyJ1c2VyIjp7Il9pZCI6IlVTUjEwMDAyOSIsInVzZXJEZXRhaWxzIjp7ImJhY2tVcFVzZXJJZCI6IlVTUjEwMDE3OSIsImxhc3RMb2dnZWRPbiI6IjIwMTgtMDctMDlUMTA6MzA6MzMuMjM1WiIsImVtcGxveWVlSWQiOiIxMyIsImZpcnN0TmFtZSI6IkRlbW8iLCJtaWRkbGVOYW1lIjoiIiwibGFzdE5hbWUiOiJVc2VyVHdvIiwiZW1haWwiOiJhbW9sLmJvcnNlQGNveGFuZGtpbmdzLmNvbSIsIm1vYmlsZSI6Ijc4OTk5Mjg2NjYiLCJkZXNpZ25hdGlvbiI6IkFzc29jaWF0ZSIsInJlcG9ydGluZ01hbmFnZXIiOiJVU1IxMDAwODAiLCJpc01hbmFnZXIiOmZhbHNlLCJCVSI6IkJsdWUgU3F1YXJlIiwiU0JVIjoiRG9tZXN0aWMiLCJzYWxlc09mZmljZSI6IlRFU1QgU2FsZXMgRGlnIiwic2FsZXNHcm91cCI6IlMxIiwiaXNTeXN0ZW1Vc2VyIjpmYWxzZSwiY29tcGFuaWVzIjpbeyJjb21wYW55TmFtZSI6IkNveCBhbmQgS2luZ3MiLCJyb2xlTmFtZSI6IkNlbnRyYWwgVGVhbSBVc2VyIiwicm9sZUlkIjoiUk9MRTEwMDAxMSIsImNvbXBhbnlJZCI6IkdDMjExMDAxNjIiLCJkZWZhdWx0Q29tcGFueSI6ZmFsc2UsIl9pZCI6IjVhNjlkNzg1NmNiZGI2MzNlNDAxOGRlYyJ9LHsiY29tcGFueU5hbWUiOiJFemVlZ28iLCJjb21wYW55SWQiOiJHQzIyIiwicm9sZU5hbWUiOiJIUiBtYW5hZ2VyIiwicm9sZUlkIjoiUk9MRTEwMDAwMiIsImRlZmF1bHRDb21wYW55Ijp0cnVlLCJfaWQiOiI1YWQ2ZTAwM2Y5NWY3MDA3NjM0M2RlNmUifV19fSwiY3VycmVudENvbXBhbnkiOnsiX2lkIjoiR0MyMiIsIm5hbWUiOiJFemVlZ28iLCJnb2MiOnsiX2lkIjoiR09DMTAwMDAyIiwibmFtZSI6IkV6ZWVnbzEifSwiZ2MiOnsiX2lkIjoiR0MyIiwibmFtZSI6IkV6ZWVnbzEifX0sInRpbWVTdGFtcCI6IjIwMTgtMDctMDlUMTE6MTI6NTcuNTk1WiJ9.uDq7t2OW8u87cdfv6filaqTF+5suD8ub8yVOabGJeio=");
					airDynamicProject.setTemplateTypes(TemplateTypes.Document);

					List<DynamicAttributes> airVariableList = new ArrayList<>();
					prepareVariablesForAir(airVariableList, retrieveResponse, productlistArray.getJSONObject(i));
					airDynamicProject.setDynamicVariables(airVariableList);
					logger.info("Input parameters for main object are " + airDynamicProject);
					TemplateServiceImpl templateServiceimpl = new TemplateServiceImpl();

					br.append(templateServiceimpl.postTempalte(airDynamicProject));
				}
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// templateServiceimpl.postTempalte(airDynamicProject);
		TemplateServiceImpl printpdf = new TemplateServiceImpl();
		File toSend = printpdf.convertToPdf(br.toString(), "", uniqueId);
		File fileWithNewName = new File(toSend.getParent(), uniqueId + ".pdf");
		toSend.renameTo(fileWithNewName);

		FTPUploadFile ftp = new FTPUploadFile();
		ftp.uploadFile(uniqueId);
		return toSend;

	}

	private FileType generateFileType(JSONObject product) {

		int adultCount = 0;
		int childCount = 0;
		String functionName;

		try {
			JSONArray passengerArray = product.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONArray(JSON_PROP_PAXINFO);
			for (int j = 0; j <= passengerArray.length() - 1; j++) {
				System.out.println("at " + j + " " + passengerArray.getJSONObject(j));
				if (passengerArray.getJSONObject(j).get(JSON_PROP_PAXTYPE).equals(JSON_VAL_ADULT)) {
					adultCount++;
				} else if (passengerArray.getJSONObject(j).get(JSON_PROP_PAXTYPE).equals(JSON_VAL_CHILD)) {
					childCount++;
				}

			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("adult " + adultCount + " Childcount" + childCount);
		functionName = "Air" + adultCount + "Adult" + childCount + "Child";
		if (functionName.equals(PASSENGER_COUNT_1ADULT0CHILD)) {
			return FileType.Air1Adult;
			// Below code added for testing purpose
		} else if (functionName.equals(PASSENGER_COUNT_2ADULT0CHILD)) {
			return FileType.Air1Adult;

		} else if (functionName.equals(PASSENGER_COUNT_3ADULT0CHILD)) {
			return FileType.Air1Adult;

		} else if (functionName.equals(PASSENGER_COUNT_4ADULT0CHILD)) {
			return FileType.Air1Adult;
		} else if (functionName.equals(PASSENGER_COUNT_5ADULT0CHILD)) {
			return FileType.Air1Adult;
		} else if (functionName.equals(PASSENGER_COUNT_6ADULT0CHILD)) {
			return FileType.Air1Adult;
		} else if (functionName.equals(PASSENGER_COUNT_7ADULT0CHILD)) {
			return FileType.Air1Adult;
		} else if (functionName.equals(PASSENGER_COUNT_8ADULT0CHILD)) {
			return FileType.Air1Adult;
		} else if (functionName.equals(PASSENGER_COUNT_9ADULT0CHILD)) {
			return FileType.Air1Adult;
		} else if (functionName.equals(PASSENGER_COUNT_2ADULT2CHILD)) {
			return FileType.Air1Adult;
		} else
			return null;

	}

	private List<DynamicAttributes> prepareVariablesForAir(List<DynamicAttributes> airVariableList,
			JSONObject retrieveresponse, JSONObject product) {
		logger.info("preparing dynamic attributes for AIR product in prepareVariablesForAir() method");
		JSONArray originDestinationOptions = null, flightSegment = null, paxInfo = null;
		try {
			originDestinationOptions = product.getJSONObject(JSON_PROP_ORDERDETAILS)
					.getJSONObject(JSON_PROP_FLIGHTDETAILS).getJSONArray(JSON_PROP_ORIGDESTOPTS);
			flightSegment = originDestinationOptions.getJSONObject(0).getJSONArray(JSON_PROP_FLIGHTSEG);
			paxInfo = product.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONArray(JSON_PROP_PAXINFO);

			DynamicAttributes ezeego1_Ref = new DynamicAttributes();
			ezeego1_Ref.setName("BookingNumber");
			ezeego1_Ref.setValue(retrieveresponse.getJSONObject(JSON_PROP_RESBODY).getString(JSON_PROP_BOOKID));
			airVariableList.add(ezeego1_Ref);

			DynamicAttributes Supplier_Ref = new DynamicAttributes();
			Supplier_Ref.setName("SupplierReferenceNo");
			Supplier_Ref.setValue(product.getJSONObject(JSON_PROP_ORDERDETAILS).getString(JSON_PROP_AIRLINEPNR));
			airVariableList.add(Supplier_Ref);

			DynamicAttributes From_To = new DynamicAttributes();
			From_To.setName("FromTo");
			From_To.setValue(new StringBuilder(flightSegment.getJSONObject(0).getString(JSON_PROP_ORIGLOC))
					.append(" To ").append(flightSegment.getJSONObject(0).getString(JSON_PROP_DESTLOC)).toString());
			airVariableList.add(From_To);

			DynamicAttributes marketingAirline = new DynamicAttributes();
			marketingAirline.setName("FlightDetails");
			marketingAirline.setValue(new StringBuilder(flightSegment.getJSONObject(0)
					.getJSONObject(JSON_PROP_MARKAIRLINE).getString(JSON_PROP_AIRLINECODE)).append(" - ")
							.append(flightSegment.getJSONObject(0).getJSONObject(JSON_PROP_MARKAIRLINE)
									.getString(JSON_PROP_FLIGHTNBR))
							.toString());
			airVariableList.add(marketingAirline);

			DynamicAttributes departureDate = new DynamicAttributes();
			departureDate.setName("DepartureDate");
			departureDate.setValue(flightSegment.getJSONObject(0).getString(JSON_PROP_DEPARTDATE).substring(0, 10));
			airVariableList.add(departureDate);

			DynamicAttributes departureTime = new DynamicAttributes();
			departureTime.setName("DepartureTime");
			departureTime.setValue(flightSegment.getJSONObject(0).getString(JSON_PROP_DEPARTDATE).substring(11, 16));
			airVariableList.add(departureTime);

			DynamicAttributes arrivalTime = new DynamicAttributes();
			arrivalTime.setName("ArrivalTime");
			arrivalTime.setValue(flightSegment.getJSONObject(0).getString(JSON_PROP_ARRIVEDATE).substring(11, 16));
			airVariableList.add(arrivalTime);

			DynamicAttributes maximumStops = new DynamicAttributes();
			maximumStops.setName("Stops");
			maximumStops.setValue(Integer.toString(flightSegment.length() - 1));
			airVariableList.add(maximumStops);

			DynamicAttributes cabinType = new DynamicAttributes();
			cabinType.setName("CabinType");
			cabinType.setValue(flightSegment.getJSONObject(0).getString(JSON_PROP_CABINTYPE));
			airVariableList.add(cabinType);

			DynamicAttributes status = new DynamicAttributes();
			status.setName("BookingStatus");
			status.setValue(product.getString(JSON_PROP_STATUS));
			airVariableList.add(status);

			DynamicAttributes paxName = new DynamicAttributes();
			paxName.setName("PassengerName");
			paxName.setValue(new StringBuilder(paxInfo.getJSONObject(0).getString(JSON_PROP_FIRSTNAME)).append(" ")
					.append(paxInfo.getJSONObject(0).getString(JSON_PROP_LASTNAME)).toString());
			airVariableList.add(paxName);

			DynamicAttributes ticketNumber = new DynamicAttributes();
			ticketNumber.setName("TicketNumber");
			ticketNumber.setValue(paxInfo.getJSONObject(0).getString(JSON_PROP_TICKETNBR));
			airVariableList.add(ticketNumber);

			DynamicAttributes baseFare = new DynamicAttributes();
			baseFare.setName("AirFare");
			baseFare.setValue(product.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONObject(JSON_PROP_ORDERTOTALPRICE)
					.getJSONObject(JSON_PROP_BASEFARE).get(JSON_PROP_AMOUNT).toString());
			airVariableList.add(baseFare);

			DynamicAttributes taxes = new DynamicAttributes();
			taxes.setName("Taxes");
			taxes.setValue(product.getJSONObject(JSON_PROP_ORDERDETAILS).getJSONObject(JSON_PROP_ORDERTOTALPRICE)
					.getJSONObject(JSON_PROP_TAXES).get(JSON_PROP_AMOUNT).toString());
			airVariableList.add(taxes);

			logger.info("Dynamic attributes for Air are  " + airVariableList);

		} catch (Exception e) {
			logger.error("Exception during request processing for airVariableList", e);
		}
		return airVariableList;

	}

}
