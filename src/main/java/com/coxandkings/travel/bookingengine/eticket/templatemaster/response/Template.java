
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"templateID", "templateType", "templateContent", "emailSubject",
    "dynamicTemplateContent"})
public class Template {

  @JsonProperty("templateID")
  private String templateID;
  @JsonProperty("templateType")
  private String templateType;
  @JsonProperty("templateContent")
  private String templateContent;
  @JsonProperty("emailSubject")
  private Object emailSubject;
  @JsonProperty("dynamicTemplateContent")
  private String dynamicTemplateContent;

  @JsonProperty("templateID")
  public String getTemplateID() {
    return templateID;
  }

  @JsonProperty("templateID")
  public void setTemplateID(String templateID) {
    this.templateID = templateID;
  }

  @JsonProperty("templateType")
  public String getTemplateType() {
    return templateType;
  }

  @JsonProperty("templateType")
  public void setTemplateType(String templateType) {
    this.templateType = templateType;
  }

  @JsonProperty("templateContent")
  public String getTemplateContent() {
    return templateContent;
  }

  @JsonProperty("templateContent")
  public void setTemplateContent(String templateContent) {
    this.templateContent = templateContent;
  }

  @JsonProperty("emailSubject")
  public Object getEmailSubject() {
    return emailSubject;
  }

  @JsonProperty("emailSubject")
  public void setEmailSubject(Object emailSubject) {
    this.emailSubject = emailSubject;
  }

  @JsonProperty("dynamicTemplateContent")
  public String getDynamicTemplateContent() {
    return dynamicTemplateContent;
  }

  @JsonProperty("dynamicTemplateContent")
  public void setDynamicTemplateContent(String dynamicTemplateContent) {
    this.dynamicTemplateContent = dynamicTemplateContent;
  }

}
