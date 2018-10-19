package com.coxandkings.travel.bookingengine.eticket.templatemaster.service;

import java.util.List;

import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.FileType;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.TemplateTypes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.DynamicAttributes;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.EmailObject;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.MainObject;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.TemplateInfo;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.request.ToDoTaskResource;

public interface TemplateService {

	public String getTemplate(TemplateInfo templateInfo, List<DynamicAttributes> dynamicValues, TemplateTypes type,
			FileType filetype, String uniqueId);

	public String sendEmail(EmailObject emailObject);

	public TemplateInfo getTemplateInfo(String functionalArea, FileType fileType, String companyName, String token);

	public String postTempalte(MainObject main);
}
