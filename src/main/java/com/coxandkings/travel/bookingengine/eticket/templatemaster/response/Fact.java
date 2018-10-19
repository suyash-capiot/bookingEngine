
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"key", "value"})
public class Fact {

  @JsonProperty("key")
  private String key;
  @JsonProperty("value")
  private Value_ value;

  @JsonProperty("key")
  public String getKey() {
    return key;
  }

  @JsonProperty("key")
  public void setKey(String key) {
    this.key = key;
  }

  @JsonProperty("value")
  public Value_ getValue() {
    return value;
  }

  @JsonProperty("value")
  public void setValue(Value_ value) {
    this.value = value;
  }

}
