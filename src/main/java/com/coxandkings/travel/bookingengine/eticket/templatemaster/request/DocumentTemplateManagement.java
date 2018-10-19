
package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"header", "dtmInput", "dynamicVariables"})
public class DocumentTemplateManagement {

  @JsonProperty("header")
  private Header header;
  @JsonProperty("dtmInput")
  private TemplateInfo templateInfo;
  @JsonProperty("dynamicVariables")
  private List<DynamicAttributes> dynamicVariables;

  public DocumentTemplateManagement(TemplateInfo templateInfo, List<DynamicAttributes> attributes,
      String userId, String transactionId) {
    this.header = new Header(userId, transactionId);
    this.templateInfo = templateInfo;
    this.dynamicVariables = attributes;
  }

  @JsonProperty("header")
  public Header getHeader() {
    return header;
  }

  @JsonProperty("header")
  public void setHeader(Header header) {
    this.header = header;
  }

  public TemplateInfo getTemplateInfo() {
    return templateInfo;
  }

  public void setTemplateInfo(TemplateInfo templateInfo) {
    this.templateInfo = templateInfo;
  }

  public List<DynamicAttributes> getDynamicVariables() {
    return dynamicVariables;
  }

  public void setDynamicVariables(List<DynamicAttributes> dynamicVariables) {
    this.dynamicVariables = dynamicVariables;
  }

}
