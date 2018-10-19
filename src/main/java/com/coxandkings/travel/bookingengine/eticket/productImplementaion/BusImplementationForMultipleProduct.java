package com.coxandkings.travel.bookingengine.eticket.productImplementaion;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.FileType;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.TemplateTypes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.DynamicAttributes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.MainObject;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.service.impl.TemplateServiceImpl;
import com.coxandkings.travel.bookingengine.eticket.uploadFile.FTPUploadFile;
import com.coxandkings.travel.bookingengine.eticket.exceptions.FinanceException;
import com.coxandkings.travel.bookingengine.eticket.systemlogin.MDMDataSource;

public class BusImplementationForMultipleProduct {
	private static final Logger logger = Logger.getLogger(BusImplementation.class);

	public File generateTemplate(JSONObject retrieveResponse, String uniqueId) {
		logger.info("In BusImplementation class calling generateTemplate() method");
		String proudct = "Bus";
		JSONArray productlistArray = new JSONArray();
		productlistArray = retrieveResponse.getJSONObject("responseBody").getJSONArray("products");
		StringBuilder br = new StringBuilder();
		for (int i = 0; i <= productlistArray.length() - 1; i++) {

			if (productlistArray.getJSONObject(i).get("productSubCategory").equals(proudct)) {

				MainObject busDynamicProject = new MainObject();
				busDynamicProject.setUniqueId(uniqueId);
				busDynamicProject.setCompanyName("Ezeego");
				// busDynamicProject.setFileType(generateFileType(retrieveResponse));
				busDynamicProject.setFileType(FileType.BusTicket);
				MDMDataSource data = new MDMDataSource();
				try {
					busDynamicProject.setToken(data.getToken().getToken());
				} catch (FinanceException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					logger.info("RetriveResponse in null");
				}
				// busDynamicProject.setToken("Bearer
				// eyJ0eXBlIjoiSldUIiwiYWxnIjoiSFMyNTYifQ==.eyJ1c2VyIjp7Il9pZCI6IlVTUjEwMDAyOSIsInVzZXJEZXRhaWxzIjp7ImJhY2tVcFVzZXJJZCI6IlVTUjEwMDE3OSIsImxhc3RMb2dnZWRPbiI6IjIwMTgtMDctMDlUMTA6MzA6MzMuMjM1WiIsImVtcGxveWVlSWQiOiIxMyIsImZpcnN0TmFtZSI6IkRlbW8iLCJtaWRkbGVOYW1lIjoiIiwibGFzdE5hbWUiOiJVc2VyVHdvIiwiZW1haWwiOiJhbW9sLmJvcnNlQGNveGFuZGtpbmdzLmNvbSIsIm1vYmlsZSI6Ijc4OTk5Mjg2NjYiLCJkZXNpZ25hdGlvbiI6IkFzc29jaWF0ZSIsInJlcG9ydGluZ01hbmFnZXIiOiJVU1IxMDAwODAiLCJpc01hbmFnZXIiOmZhbHNlLCJCVSI6IkJsdWUgU3F1YXJlIiwiU0JVIjoiRG9tZXN0aWMiLCJzYWxlc09mZmljZSI6IlRFU1QgU2FsZXMgRGlnIiwic2FsZXNHcm91cCI6IlMxIiwiaXNTeXN0ZW1Vc2VyIjpmYWxzZSwiY29tcGFuaWVzIjpbeyJjb21wYW55TmFtZSI6IkNveCBhbmQgS2luZ3MiLCJyb2xlTmFtZSI6IkNlbnRyYWwgVGVhbSBVc2VyIiwicm9sZUlkIjoiUk9MRTEwMDAxMSIsImNvbXBhbnlJZCI6IkdDMjExMDAxNjIiLCJkZWZhdWx0Q29tcGFueSI6ZmFsc2UsIl9pZCI6IjVhNjlkNzg1NmNiZGI2MzNlNDAxOGRlYyJ9LHsiY29tcGFueU5hbWUiOiJFemVlZ28iLCJjb21wYW55SWQiOiJHQzIyIiwicm9sZU5hbWUiOiJIUiBtYW5hZ2VyIiwicm9sZUlkIjoiUk9MRTEwMDAwMiIsImRlZmF1bHRDb21wYW55Ijp0cnVlLCJfaWQiOiI1YWQ2ZTAwM2Y5NWY3MDA3NjM0M2RlNmUifV19fSwiY3VycmVudENvbXBhbnkiOnsiX2lkIjoiR0MyMiIsIm5hbWUiOiJFemVlZ28iLCJnb2MiOnsiX2lkIjoiR09DMTAwMDAyIiwibmFtZSI6IkV6ZWVnbzEifSwiZ2MiOnsiX2lkIjoiR0MyIiwibmFtZSI6IkV6ZWVnbzEifX0sInRpbWVTdGFtcCI6IjIwMTgtMDctMDlUMTE6MTI6NTcuNTk1WiJ9.uDq7t2OW8u87cdfv6filaqTF+5suD8ub8yVOabGJeio=");
				busDynamicProject.setTemplateTypes(TemplateTypes.Document);

				List<DynamicAttributes> busVariableList = new ArrayList<>();
				prepareVariablesForBus(busVariableList, retrieveResponse, productlistArray.getJSONObject(i));
				busDynamicProject.setDynamicVariables(busVariableList);
				logger.info("Input parameters for main object are " + busDynamicProject);
				TemplateServiceImpl templateServiceimpl = new TemplateServiceImpl();

				br.append(templateServiceimpl.postTempalte(busDynamicProject));
			}
		}
		TemplateServiceImpl printpdf = new TemplateServiceImpl();
		File toSend = printpdf.convertToPdf(br.toString(), "", uniqueId);
		File fileWithNewName = new File(toSend.getParent(), uniqueId + ".pdf");
		toSend.renameTo(fileWithNewName);
		FTPUploadFile ftp= new FTPUploadFile();
		ftp.uploadFile(uniqueId);
		return toSend;
	}

	private List<DynamicAttributes> prepareVariablesForBus(List<DynamicAttributes> busVariableList,
			JSONObject retrieveresponse, JSONObject product) {
		logger.info("preparing dynamic attributes for BUS product in prepareVariablesForBus() method");
		// JSONArray productlist =
		// retrieveresponse.getJSONObject("responseBody").getJSONArray("products");

		JSONArray paxlist = product.getJSONObject("orderDetails").getJSONArray("paxInfo");

		DynamicAttributes ezeego1_Ref = new DynamicAttributes();
		ezeego1_Ref.setName("TicketNo");
		ezeego1_Ref.setValue(retrieveresponse.getJSONObject("responseBody").getString("bookID"));
		busVariableList.add(ezeego1_Ref);

		DynamicAttributes JetBusways_Ref = new DynamicAttributes();
		JetBusways_Ref.setName("TicketNo");
		JetBusways_Ref.setValue(product.getJSONObject("orderDetails").getString("ticketNumber"));
		busVariableList.add(JetBusways_Ref);

		String passengers = "";
		for (int j = 0; j <= paxlist.length() - 1; j++) {
			passengers = passengers + paxlist.getJSONObject(j).get("firstName") + " "
					+ paxlist.getJSONObject(j).get("lastName") + " ,";
		}
		DynamicAttributes passengerlist = new DynamicAttributes();
		passengerlist.setName("PassengerName");
		passengerlist.setValue(passengers);
		busVariableList.add(passengerlist);

		DynamicAttributes PNRNo = new DynamicAttributes();
		PNRNo.setName("PNRNo");
		PNRNo.setValue(product.getJSONObject("orderDetails").getString("ticketPNR"));
		busVariableList.add(PNRNo);

		DynamicAttributes noOfPassengers = new DynamicAttributes();
		noOfPassengers.setName("NumberOfPassengers");
		noOfPassengers.setValue("" + paxlist.length());
		busVariableList.add(noOfPassengers);

		DynamicAttributes Travels = new DynamicAttributes();
		Travels.setName("TravelsName");
		Travels.setValue(product.getJSONObject("orderDetails").getJSONObject("busDetails").getString("busName"));
		busVariableList.add(Travels);

		String seatNo = "";
		for (int k = 0; k <= paxlist.length() - 1; k++) {
			seatNo = seatNo + paxlist.getJSONObject(k).get("seatNumber") + " ,";
		}
		DynamicAttributes Seats = new DynamicAttributes();
		Seats.setName("SeatNumber");
		Seats.setValue(seatNo);
		busVariableList.add(Seats);

		DynamicAttributes BusType = new DynamicAttributes();
		BusType.setName("RECEPIENT_PLACE_OF_SUPPLY");
		BusType.setValue(product.getJSONObject("orderDetails").getJSONObject("busDetails").getString("busType"));
		busVariableList.add(Travels);

		DynamicAttributes BoardingPoint = new DynamicAttributes();
		BoardingPoint.setName("RECEPIENT_Address");
		BoardingPoint.setValue(Integer.toString((int) product.getJSONObject("orderDetails").getJSONObject("busDetails")
				.getJSONObject("boardingPointDetails").get("id")));
		busVariableList.add(BoardingPoint);

		DynamicAttributes TotalFare = new DynamicAttributes();
		TotalFare.setName("TotalFare");
		TotalFare.setValue(
				product.getJSONObject("orderDetails").getJSONObject("orderTotalPriceInfo").getString("totalPrice"));
		busVariableList.add(TotalFare);
		logger.info("Dynamic attributes for Bus for product  : " + busVariableList);

		return busVariableList;

	}

	public void multipleProducts(JSONObject retrieveResponse, String uniqueId) {
		JSONArray productlist = retrieveResponse.getJSONObject("responseBody").getJSONArray("products");
		for (int i = 1; i <= productlist.length(); i++) {

			MainObject busDynamicProject = new MainObject();
			busDynamicProject.setUniqueId(uniqueId);
			busDynamicProject.setCompanyName("Ezeego");
			busDynamicProject.setFileType(FileType.BusTicket);
			busDynamicProject.setToken(
					"Bearer eyJ0eXBlIjoiSldUIiwiYWxnIjoiSFMyNTYifQ==.eyJ1c2VyIjp7Il9pZCI6IlVTUjEwMDAyOSIsInVzZXJEZXRhaWxzIjp7ImJhY2tVcFVzZXJJZCI6IlVTUjEwMDE3OSIsImxhc3RMb2dnZWRPbiI6IjIwMTgtMDctMDZUMDc6MDQ6NDUuMjE2WiIsImVtcGxveWVlSWQiOiIxMyIsImZpcnN0TmFtZSI6IkRlbW8iLCJtaWRkbGVOYW1lIjoiIiwibGFzdE5hbWUiOiJVc2VyVHdvIiwiZW1haWwiOiJhbW9sLmJvcnNlQGNveGFuZGtpbmdzLmNvbSIsIm1vYmlsZSI6Ijc4OTk5Mjg2NjYiLCJkZXNpZ25hdGlvbiI6IkFzc29jaWF0ZSIsInJlcG9ydGluZ01hbmFnZXIiOiJVU1IxMDAwODAiLCJpc01hbmFnZXIiOmZhbHNlLCJCVSI6IkJsdWUgU3F1YXJlIiwiU0JVIjoiRG9tZXN0aWMiLCJzYWxlc09mZmljZSI6IlRFU1QgU2FsZXMgRGlnIiwic2FsZXNHcm91cCI6IlMxIiwiaXNTeXN0ZW1Vc2VyIjpmYWxzZSwiY29tcGFuaWVzIjpbeyJjb21wYW55TmFtZSI6IkNveCBhbmQgS2luZ3MiLCJyb2xlTmFtZSI6IkNlbnRyYWwgVGVhbSBVc2VyIiwicm9sZUlkIjoiUk9MRTEwMDAxMSIsImNvbXBhbnlJZCI6IkdDMjExMDAxNjIiLCJkZWZhdWx0Q29tcGFueSI6ZmFsc2UsIl9pZCI6IjVhNjlkNzg1NmNiZGI2MzNlNDAxOGRlYyJ9LHsiY29tcGFueU5hbWUiOiJFemVlZ28iLCJjb21wYW55SWQiOiJHQzIyIiwicm9sZU5hbWUiOiJIUiBtYW5hZ2VyIiwicm9sZUlkIjoiUk9MRTEwMDAwMiIsImRlZmF1bHRDb21wYW55Ijp0cnVlLCJfaWQiOiI1YWQ2ZTAwM2Y5NWY3MDA3NjM0M2RlNmUifV19fSwiY3VycmVudENvbXBhbnkiOnsiX2lkIjoiR0MyMiIsIm5hbWUiOiJFemVlZ28iLCJnb2MiOnsiX2lkIjoiR09DMTAwMDAyIiwibmFtZSI6IkV6ZWVnbzEifSwiZ2MiOnsiX2lkIjoiR0MyIiwibmFtZSI6IkV6ZWVnbzEifX0sInRpbWVTdGFtcCI6IjIwMTgtMDctMDZUMDg6MjY6MjQuNDUyWiJ9.foxBtVFaGln8mYpkA2EYRXoK+YIY/45yuX3PpGdEPc8=");
			busDynamicProject.setTemplateTypes(TemplateTypes.Document);
			List<DynamicAttributes> busVariableList = new ArrayList<>();
			// prepareVariablesForBus(busVariableList, retrieveResponse);
			busDynamicProject.setDynamicVariables(busVariableList);

			TemplateServiceImpl templateServiceimpl = new TemplateServiceImpl();
			templateServiceimpl.postTempalte(busDynamicProject);
		}

	}

}
