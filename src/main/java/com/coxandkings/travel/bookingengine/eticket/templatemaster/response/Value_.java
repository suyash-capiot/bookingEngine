
package com.coxandkings.travel.bookingengine.eticket.templatemaster.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"org.drools.core.common.DefaultFactHandle"})
public class Value_ {

  @JsonProperty("org.drools.core.common.DefaultFactHandle")
  private OrgDroolsCoreCommonDefaultFactHandle orgDroolsCoreCommonDefaultFactHandle;

  @JsonProperty("org.drools.core.common.DefaultFactHandle")
  public OrgDroolsCoreCommonDefaultFactHandle getOrgDroolsCoreCommonDefaultFactHandle() {
    return orgDroolsCoreCommonDefaultFactHandle;
  }

  @JsonProperty("org.drools.core.common.DefaultFactHandle")
  public void setOrgDroolsCoreCommonDefaultFactHandle(
      OrgDroolsCoreCommonDefaultFactHandle orgDroolsCoreCommonDefaultFactHandle) {
    this.orgDroolsCoreCommonDefaultFactHandle = orgDroolsCoreCommonDefaultFactHandle;
  }

}
