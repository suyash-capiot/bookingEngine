package com.coxandkings.travel.bookingengine.eticket.templatemaster.assembler;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.coxandkings.travel.bookingengine.eticket.productImplementaion.BusImplementation;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.EmailObject;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.EmailToSend;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.TemplateInfo;

public class TemplateAssembler {
	private static final Logger logger = Logger.getLogger(TemplateAssembler.class);

  public static TemplateInfo convertToTemplate(JSONObject json) {
	logger.info("In convertToTemplate() method");   
    TemplateInfo info = new TemplateInfo();
    
    try {
      JSONArray jsonArray = json.getJSONArray("data");
      json = jsonArray.getJSONObject(0);
      info.setIsActive(true);
      info.setCompanyName(json.getJSONObject("applicability").getJSONArray("company")
          .getJSONObject(0).getString("name"));
      info.setFunction(
          json.getJSONObject("applicability").getJSONArray("function").get(0).toString());
      info.setProcess(
          json.getJSONObject("applicability").getJSONArray("businessProcess").get(0).toString());
 
    
    } catch (Exception e) {
      e.printStackTrace();
    }
    JSONObject infodata = new JSONObject(info);
    logger.info("Info output : "+infodata);
    return info;
  }

  public static EmailToSend convertEmail(EmailObject emailObject, EmailToSend emailToSend) {

    EmailToSend emailToReturn = new EmailToSend();
    emailToReturn.setBody(emailObject.getBody());
    emailToReturn.setFromMail(emailObject.getFrom());
    emailToReturn.setPriority("HIGH");
    emailToReturn.setSubject(emailObject.getSubject());
    emailToReturn.setToMail(emailObject.getTo());
    emailToReturn.setDocumentReferenceIDs(emailObject.getDocumentReferenceIDs());
    return emailToReturn;
  }
}
