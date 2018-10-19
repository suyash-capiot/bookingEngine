
package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"out-identifier"})
public class GetObjects {

  @JsonProperty("out-identifier")
  private String outIdentifier;

  public GetObjects() {
    this.outIdentifier = "";
  }

  @JsonProperty("out-identifier")
  public String getOutIdentifier() {
    return outIdentifier;
  }

  @JsonProperty("out-identifier")
  public void setOutIdentifier(String outIdentifier) {
    this.outIdentifier = outIdentifier;
  }
}
