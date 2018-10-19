
package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"insert", "fire-all-rules", "get-objects"})
public class Command {
  @JsonProperty("insert")
  private Insert insert;
  @JsonProperty("fire-all-rules")
  private FireAllRules fireAllRules;
  @JsonProperty("get-objects")
  private GetObjects getObjects;

  public Command() {

  }

  public Insert getInsert() {
    return insert;
  }

  public void setInsert(Insert insert) {
    this.insert = insert;
  }

  public FireAllRules getFireAllRules() {
    return fireAllRules;
  }

  public void setFireAllRules(FireAllRules fireAllRules) {
    this.fireAllRules = fireAllRules;
  }

  public GetObjects getGetObjects() {
    return getObjects;
  }

  public void setGetObjects(GetObjects getObjects) {
    this.getObjects = getObjects;
  }
}
