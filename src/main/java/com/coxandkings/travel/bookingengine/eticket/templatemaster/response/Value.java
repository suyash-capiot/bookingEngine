
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"cnk.documenttemplatemanagement.DocumentTemplateManagement"})
public class Value {

  @JsonProperty("cnk.documenttemplatemanagement.DocumentTemplateManagement")
  private DocumentTemplateManagement documentTemplateManagement;

  @JsonProperty("cnk.documenttemplatemanagement.DocumentTemplateManagement")
  public DocumentTemplateManagement getDocumentTemplateManagement() {
    return documentTemplateManagement;
  }

  @JsonProperty("cnk.documenttemplatemanagement.DocumentTemplateManagement")
  public void setDocumentTemplateManagement(DocumentTemplateManagement DocumentTemplateManagement) {
    this.documentTemplateManagement = DocumentTemplateManagement;
  }

}
