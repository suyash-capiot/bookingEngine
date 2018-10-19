package com.coxandkings.travel.bookingengine.eticket.productImplementaion;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.eticket.EticketTemplate;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.FileType;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.TemplateTypes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.DynamicAttributes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.MainObject;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.service.TemplateService;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.service.impl.TemplateServiceImpl;
import com.itextpdf.text.pdf.PdfStructTreeController.returnType;



public class AccoImplementation {
//	private TemplateService templateService;
//	private TemplateServiceImpl templateServiceimpl;
	
	
	public void generateTemplate(JSONObject retrieveresponse, String uniqueId) {
		String product = "Accommodation";
		System.out.println("in acco method");
	//Add code for setting setting dynamic variables value	
		JSONArray productlist = retrieveresponse.getJSONObject("responseBody").getJSONArray("products");
		//System.out.println("productlist" +productlist);
		List<JSONObject> products = new ArrayList<>();
		//System.out.println(productlist.length());
		for (int i = 0; i <= productlist.length()-1; i++) {
			
			String productname = (String) productlist.getJSONObject(i).get("productCategory");
			
			if(productname.equals(product)){
				System.out.println("product at "+i +" "+productlist.getJSONObject(i).get("productCategory"));
			products.add(productlist.getJSONObject(i));
			}
		//System.out.println("category "+productlist.getJSONObject(i).get("productCategory"));
		}
		System.out.println(products);
		
		//Now calling pdf generation code
		
		MainObject accoDynamicProject = new MainObject();
		accoDynamicProject.setUniqueId("TUFAN123");
		accoDynamicProject.setCompanyName("Ezeego");
		accoDynamicProject.setFileType(FileType.Air2Adult);
		accoDynamicProject.setToken("Bearer eyJ0eXBlIjoiSldUIiwiYWxnIjoiSFMyNTYifQ==.eyJ1c2VyIjp7Il9pZCI6IlVTUjEwMDAyOSIsInVzZXJEZXRhaWxzIjp7ImJhY2tVcFVzZXJJZCI6IlVTUjEwMDE3OSIsImxhc3RMb2dnZWRPbiI6IjIwMTgtMDctMDRUMTA6Mjk6NDQuMTMzWiIsImVtcGxveWVlSWQiOiIxMyIsImZpcnN0TmFtZSI6IkRlbW8iLCJtaWRkbGVOYW1lIjoiIiwibGFzdE5hbWUiOiJVc2VyVHdvIiwiZW1haWwiOiJhbW9sLmJvcnNlQGNveGFuZGtpbmdzLmNvbSIsIm1vYmlsZSI6Ijc4OTk5Mjg2NjYiLCJkZXNpZ25hdGlvbiI6IkFzc29jaWF0ZSIsInJlcG9ydGluZ01hbmFnZXIiOiJVU1IxMDAwODAiLCJpc01hbmFnZXIiOmZhbHNlLCJCVSI6IkJsdWUgU3F1YXJlIiwiU0JVIjoiRG9tZXN0aWMiLCJzYWxlc09mZmljZSI6IlRFU1QgU2FsZXMgRGlnIiwic2FsZXNHcm91cCI6IlMxIiwiY29tcGFuaWVzIjpbeyJjb21wYW55TmFtZSI6IkNveCBhbmQgS2luZ3MiLCJyb2xlTmFtZSI6IkNlbnRyYWwgVGVhbSBVc2VyIiwicm9sZUlkIjoiUk9MRTEwMDAxMSIsImNvbXBhbnlJZCI6IkdDMjExMDAxNjIiLCJkZWZhdWx0Q29tcGFueSI6ZmFsc2UsIl9pZCI6IjVhNjlkNzg1NmNiZGI2MzNlNDAxOGRlYyJ9LHsiY29tcGFueU5hbWUiOiJFemVlZ28iLCJjb21wYW55SWQiOiJHQzIyIiwicm9sZU5hbWUiOiJIUiBtYW5hZ2VyIiwicm9sZUlkIjoiUk9MRTEwMDAwMiIsImRlZmF1bHRDb21wYW55Ijp0cnVlLCJfaWQiOiI1YWQ2ZTAwM2Y5NWY3MDA3NjM0M2RlNmUifV19fSwiY3VycmVudENvbXBhbnkiOnsiX2lkIjoiR0MyMiIsIm5hbWUiOiJFemVlZ28iLCJnb2MiOnsiX2lkIjoiR09DMTAwMDAyIiwibmFtZSI6IkV6ZWVnbzEifSwiZ2MiOnsiX2lkIjoiR0MyIiwibmFtZSI6IkV6ZWVnbzEifX0sInRpbWVTdGFtcCI6IjIwMTgtMDctMDVUMDU6Mzk6NDAuMDc0WiJ9.RL1NWfc1iziz4HL63Ejy3nK6L671jhWJDcGn3qqFkgc=");
		accoDynamicProject.setTemplateTypes(TemplateTypes.Document);		

		List<DynamicAttributes> accoVariableList = new ArrayList<>();
		prepareVariablesForAcco(accoVariableList);
		accoDynamicProject.setDynamicVariables(accoVariableList);
		System.out.println("main object" +accoDynamicProject);
		JSONObject templaterq = new JSONObject(accoDynamicProject);
		System.out.println("Final Template Object "+templaterq);
		
		TemplateServiceImpl templateServiceimpl= new TemplateServiceImpl();
		templateServiceimpl.postTempalte(accoDynamicProject);
		
		
	}
	private List<DynamicAttributes> prepareVariablesForAcco(List<DynamicAttributes> accoVariableList) {

		DynamicAttributes companyName = new DynamicAttributes();
		companyName.setName("Company_Name");
		companyName.setValue("Ezeego");
		accoVariableList.add(companyName);
		
		DynamicAttributes Recepient_Gstin = new DynamicAttributes();
		Recepient_Gstin.setName("RECEPIENT_GSTIN_OF_RECIPIENT");
		Recepient_Gstin.setValue("775245");
		accoVariableList.add(Recepient_Gstin);
		
		DynamicAttributes Recepient_address = new DynamicAttributes();
		Recepient_address.setName("RECEPIENT_Address");
		Recepient_address.setValue("Mumbai");
		accoVariableList.add(Recepient_address);
		
		DynamicAttributes Recepient_State = new DynamicAttributes();
		Recepient_State.setName("RECEPIENT_State");
		Recepient_State.setValue("Maharashtra");
		accoVariableList.add(Recepient_State);
		
		return accoVariableList;
	}
}
