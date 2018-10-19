
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"templates"})
public class DtmOutput {

  @JsonProperty("templates")
  private List<Template> templates = null;

  @JsonProperty("templates")
  public List<Template> getTemplates() {
    return templates;
  }

  @JsonProperty("templates")
  public void setTemplates(List<Template> templates) {
    this.templates = templates;
  }

}
