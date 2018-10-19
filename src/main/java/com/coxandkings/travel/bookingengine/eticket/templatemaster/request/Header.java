
package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"userID", "transactionID"})
public class Header {

  @JsonProperty("userID")
  private String userID;
  @JsonProperty("transactionID")
  private String transactionID;

  public Header() {
    this.userID = "U123";
    this.transactionID = "T123";
  }

  public Header(String userID, String transactionID) {
    this.userID = userID;
    this.transactionID = transactionID;
  }

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
}
