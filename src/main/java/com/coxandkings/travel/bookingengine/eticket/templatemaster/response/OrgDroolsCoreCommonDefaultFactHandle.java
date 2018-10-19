
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"external-form"})
public class OrgDroolsCoreCommonDefaultFactHandle {

  @JsonProperty("external-form")
  private String externalForm;

  @JsonProperty("external-form")
  public String getExternalForm() {
    return externalForm;
  }

  @JsonProperty("external-form")
  public void setExternalForm(String externalForm) {
    this.externalForm = externalForm;
  }

}
