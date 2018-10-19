
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"header", "dtmInput", "dtmOutput", "ruleName", "dynamicVariables"})
public class DocumentTemplateManagement {

  @JsonProperty("header")
  private Header header;
  @JsonProperty("dtmInput")
  private Object dtmInput;
  @JsonProperty("dtmOutput")
  private DtmOutput dtmOutput;
  @JsonProperty("ruleName")
  private String ruleName;
  @JsonProperty("dynamicVariables")
  private Object dynamicVariables;

  @JsonProperty("header")
  public Header getHeader() {
    return header;
  }

  @JsonProperty("header")
  public void setHeader(Header header) {
    this.header = header;
  }

  @JsonProperty("dtmInput")
  public Object getDtmInput() {
    return dtmInput;
  }

  @JsonProperty("dtmInput")
  public void setDtmInput(Object dtmInput) {
    this.dtmInput = dtmInput;
  }

  @JsonProperty("dtmOutput")
  public DtmOutput getDtmOutput() {
    return dtmOutput;
  }

  @JsonProperty("dtmOutput")
  public void setDtmOutput(DtmOutput dtmOutput) {
    this.dtmOutput = dtmOutput;
  }

  @JsonProperty("ruleName")
  public String getRuleName() {
    return ruleName;
  }

  @JsonProperty("ruleName")
  public void setRuleName(String ruleName) {
    this.ruleName = ruleName;
  }

  @JsonProperty("dynamicVariables")
  public Object getDynamicVariables() {
    return dynamicVariables;
  }

  @JsonProperty("dynamicVariables")
  public void setDynamicVariables(Object dynamicVariables) {
    this.dynamicVariables = dynamicVariables;
  }

}
