
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"userID", "transactionID", "status", "message"})
public class Header {

  @JsonProperty("userID")
  private String userID;
  @JsonProperty("transactionID")
  private String transactionID;
  @JsonProperty("status")
  private String status;
  @JsonProperty("message")
  private String message;

  @JsonProperty("userID")
  public String getUserID() {
    return userID;
  }

  @JsonProperty("userID")
  public void setUserID(String userID) {
    this.userID = userID;
  }

  @JsonProperty("transactionID")
  public String getTransactionID() {
    return transactionID;
  }

  @JsonProperty("transactionID")
  public void setTransactionID(String transactionID) {
    this.transactionID = transactionID;
  }

  @JsonProperty("status")
  public String getStatus() {
    return status;
  }

  @JsonProperty("status")
  public void setStatus(String status) {
    this.status = status;
  }

  @JsonProperty("message")
  public String getMessage() {
    return message;
  }

  @JsonProperty("message")
  public void setMessage(String message) {
    this.message = message;
  }

}
