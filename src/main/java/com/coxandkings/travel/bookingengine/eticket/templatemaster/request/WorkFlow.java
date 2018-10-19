package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;


import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"workFlowOperationType", "createURL", "deleteURL", "updateURL", "getURL", "doc",
    "serviceName", "createdBy", "type"})
public class WorkFlow {
  @JsonProperty("workFlowOperationType")
  private String workFlowOperationType;
  @JsonProperty("createURL")
  private String createURL;
  @JsonProperty("deleteURL")
  private String deleteURL;
  @JsonProperty("updateURL")
  private String updateURL;
  @JsonProperty("getURL")
  private String getURL;
  @JsonProperty("doc")
  private Doc doc;
  @JsonProperty("serviceName")
  private String serviceName;
  @JsonProperty("createdBy")
  private String createdBy;
  @JsonProperty("type")
  private String type;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("workFlowOperationType")
  public String getWorkFlowOperationType() {
    return workFlowOperationType;
  }

  @JsonProperty("workFlowOperationType")
  public void setWorkFlowOperationType(String workFlowOperationType) {
    this.workFlowOperationType = workFlowOperationType;
  }

  @JsonProperty("createURL")
  public String getCreateURL() {
    return createURL;
  }

  @JsonProperty("createURL")
  public void setCreateURL(String createURL) {
    this.createURL = createURL;
  }

  @JsonProperty("deleteURL")
  public String getDeleteURL() {
    return deleteURL;
  }

  @JsonProperty("deleteURL")
  public void setDeleteURL(String deleteURL) {
    this.deleteURL = deleteURL;
  }

  @JsonProperty("updateURL")
  public String getUpdateURL() {
    return updateURL;
  }

  @JsonProperty("updateURL")
  public void setUpdateURL(String updateURL) {
    this.updateURL = updateURL;
  }

  @JsonProperty("getURL")
  public String getGetURL() {
    return getURL;
  }

  @JsonProperty("getURL")
  public void setGetURL(String getURL) {
    this.getURL = getURL;
  }

  @JsonProperty("doc")
  public Doc getDoc() {
    return doc;
  }

  @JsonProperty("doc")
  public void setDoc(Doc doc) {
    this.doc = doc;
  }

  @JsonProperty("serviceName")
  public String getServiceName() {
    return serviceName;
  }

  @JsonProperty("serviceName")
  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

}

