
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "msg", "result"})
public class TemplateResponse {

  @JsonProperty("type")
  private String type;
  @JsonProperty("msg")
  private String msg;
  @JsonProperty("result")
  private Result result;

  @JsonProperty("type")
  public String getType() {
    return type;
  }

  @JsonProperty("type")
  public void setType(String type) {
    this.type = type;
  }

  @JsonProperty("msg")
  public String getMsg() {
    return msg;
  }

  @JsonProperty("msg")
  public void setMsg(String msg) {
    this.msg = msg;
  }

  @JsonProperty("result")
  public Result getResult() {
    return result;
  }

  @JsonProperty("result")
  public void setResult(Result result) {
    this.result = result;
  }

}
