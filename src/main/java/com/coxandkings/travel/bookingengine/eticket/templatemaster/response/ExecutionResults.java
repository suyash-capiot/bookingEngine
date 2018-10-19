
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"results", "facts"})
public class ExecutionResults {

  @JsonProperty("results")
  private List<Result_> results = null;
  @JsonProperty("facts")
  private List<Fact> facts = null;

  @JsonProperty("results")
  public List<Result_> getResults() {
    return results;
  }

  @JsonProperty("results")
  public void setResults(List<Result_> results) {
    this.results = results;
  }

  @JsonProperty("facts")
  public List<Fact> getFacts() {
    return facts;
  }

  @JsonProperty("facts")
  public void setFacts(List<Fact> facts) {
    this.facts = facts;
  }

}
