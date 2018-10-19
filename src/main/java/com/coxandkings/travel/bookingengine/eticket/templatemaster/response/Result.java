
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"execution-results"})
public class Result {

  @JsonProperty("execution-results")
  private ExecutionResults executionResults;

  @JsonProperty("execution-results")
  public ExecutionResults getExecutionResults() {
    return executionResults;
  }

  @JsonProperty("execution-results")
  public void setExecutionResults(ExecutionResults executionResults) {
    this.executionResults = executionResults;
  }

}
