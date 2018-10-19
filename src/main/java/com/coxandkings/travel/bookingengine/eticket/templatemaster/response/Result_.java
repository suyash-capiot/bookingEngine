
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"key", "value"})
public class Result_ {

  @JsonProperty("key")
  private String key;
  @JsonProperty("value")
  private List<Value> value = null;

  @JsonProperty("key")
  public String getKey() {
    return key;
  }

  @JsonProperty("key")
  public void setKey(String key) {
    this.key = key;
  }

  @JsonProperty("value")
  public List<Value> getValue() {
    return value;
  }

  @JsonProperty("value")
  public void setValue(List<Value> value) {
    this.value = value;
  }

}
