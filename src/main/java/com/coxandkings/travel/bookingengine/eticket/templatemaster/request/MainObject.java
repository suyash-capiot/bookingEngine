package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

import java.util.List;

import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.FileType;
import com.coxandkings.travel.bookingengine.eticket.templatemaster.enums.TemplateTypes;

public class MainObject {


 
List<DynamicAttributes> dynamicVariables;
  TemplateTypes templateTypes;
  FileType fileType;
  private String uniqueId;
  private String companyName;
  private String token;


  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public TemplateTypes getTemplateTypes() {
    return templateTypes;
  }

  public void setTemplateTypes(TemplateTypes templateTypes) {
    this.templateTypes = templateTypes;
  }

  public FileType getFileType() {
    return fileType;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public void setFileType(FileType fileType) {
    this.fileType = fileType;
  }

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  public List<DynamicAttributes> getDynamicVariables() {
    return dynamicVariables;
  }

  public void setDynamicVariables(List<DynamicAttributes> dynamicVariables) {
      this.dynamicVariables = dynamicVariables;
  }

  // public TemplateInfo getTemplateInfo() {
  // return templateInfo;
  // }
  // public void setTemplateInfo(TemplateInfo templateInfo) {
  // this.templateInfo = templateInfo;
  // }
  // public Map<String, String> getDynamicValues() {
  // return dynamicValues;
  // }
  // public void setDynamicValues(Map<String, String> dynamicValues) {
  // this.dynamicValues = dynamicValues;
  // }
 

}
