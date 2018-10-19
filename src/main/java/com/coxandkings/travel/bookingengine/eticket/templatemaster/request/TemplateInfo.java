
package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"isActive", "groupOfCompanies", "groupCompany", "companyName", "businessUnit",
    "subBusinessUnit", "market", "office", "source", "productCategory", "productCategorySubType",
    "supplier", "clientType", "clientGroup", "clientName", "process", "function", "scenario",
    "rule1", "rule2", "rule3", "communicationType", "communicateTo", "incomingCommunicationType",
    "destination", "brochure", "tour"})
public class TemplateInfo implements Serializable {

  private static final long serialVersionUID = 8799061430179743580L;
  @JsonProperty("isActive")
  private Boolean isActive;
  @JsonProperty("groupOfCompanies")
  private String groupOfCompanies;
  @JsonProperty("groupCompany")
  private String groupCompany;
  @JsonProperty("companyName")
  private String companyName;
  @JsonProperty("businessUnit")
  private String businessUnit;
  @JsonProperty("subBusinessUnit")
  private String subBusinessUnit;
  @JsonProperty("market")
  private String market;
  @JsonProperty("office")
  private String office;
  @JsonProperty("source")
  private String source;
  @JsonProperty("productCategory")
  private String productCategory;
  @JsonProperty("productCategorySubType")
  private String productCategorySubType;
  @JsonProperty("supplier")
  private String supplier;
  @JsonProperty("clientType")
  private String clientType;
  @JsonProperty("clientGroup")
  private String clientGroup;
  @JsonProperty("clientName")
  private String clientName;
  @JsonProperty("process")
  private String process;
  @JsonProperty("function")
  private String function;
  @JsonProperty("scenario")
  private String scenario;
  @JsonProperty("rule1")
  private String rule1;
  @JsonProperty("rule2")
  private String rule2;
  @JsonProperty("rule3")
  private String rule3;
  @JsonProperty("communicationType")
  private String communicationType;
  @JsonProperty("communicateTo")
  private String communicateTo;
  @JsonProperty("incomingCommunicationType")
  private String incomingCommunicationType;
  @JsonProperty("destination")
  private String destination;
  @JsonProperty("brochure")
  private String brochure;
  @JsonProperty("tour")
  private String tour;

  @JsonProperty("isActive")
  public Boolean getIsActive() {
    return isActive;
  }

  @JsonProperty("isActive")
  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  @JsonProperty("groupOfCompanies")
  public String getGroupOfCompanies() {
    return groupOfCompanies;
  }

  @JsonProperty("groupOfCompanies")
  public void setGroupOfCompanies(String groupOfCompanies) {
    this.groupOfCompanies = groupOfCompanies;
  }

  @JsonProperty("groupCompany")
  public String getGroupCompany() {
    return groupCompany;
  }

  @JsonProperty("groupCompany")
  public void setGroupCompany(String groupCompany) {
    this.groupCompany = groupCompany;
  }

  @JsonProperty("companyName")
  public String getCompanyName() {
    return companyName;
  }

  @JsonProperty("companyName")
  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  @JsonProperty("businessUnit")
  public String getBusinessUnit() {
    return businessUnit;
  }

  @JsonProperty("businessUnit")
  public void setBusinessUnit(String businessUnit) {
    this.businessUnit = businessUnit;
  }

  @JsonProperty("subBusinessUnit")
  public String getSubBusinessUnit() {
    return subBusinessUnit;
  }

  @JsonProperty("subBusinessUnit")
  public void setSubBusinessUnit(String subBusinessUnit) {
    this.subBusinessUnit = subBusinessUnit;
  }

  @JsonProperty("market")
  public String getMarket() {
    return market;
  }

  @JsonProperty("market")
  public void setMarket(String market) {
    this.market = market;
  }

  @JsonProperty("office")
  public String getOffice() {
    return office;
  }

  @JsonProperty("office")
  public void setOffice(String office) {
    this.office = office;
  }

  @JsonProperty("source")
  public String getSource() {
    return source;
  }

  @JsonProperty("source")
  public void setSource(String source) {
    this.source = source;
  }

  @JsonProperty("productCategory")
  public String getProductCategory() {
    return productCategory;
  }

  @JsonProperty("productCategory")
  public void setProductCategory(String productCategory) {
    this.productCategory = productCategory;
  }

  @JsonProperty("productCategorySubType")
  public String getProductCategorySubType() {
    return productCategorySubType;
  }

  @JsonProperty("productCategorySubType")
  public void setProductCategorySubType(String productCategorySubType) {
    this.productCategorySubType = productCategorySubType;
  }

  public String getSupplier() {
    return supplier;
  }

  public void setSupplier(String supplier) {
    this.supplier = supplier;
  }

  @JsonProperty("process")
  public String getProcess() {
    return process;
  }

  @JsonProperty("process")
  public void setProcess(String process) {
    this.process = process;
  }

  @JsonProperty("scenario")
  public String getScenario() {
    return scenario;
  }

  @JsonProperty("scenario")
  public void setScenario(String scenario) {
    this.scenario = scenario;
  }

  @JsonProperty("rule1")
  public String getRule1() {
    return rule1;
  }

  @JsonProperty("rule1")
  public void setRule1(String rule1) {
    this.rule1 = rule1;
  }

  @JsonProperty("rule2")
  public String getRule2() {
    return rule2;
  }

  @JsonProperty("rule2")
  public void setRule2(String rule2) {
    this.rule2 = rule2;
  }

  @JsonProperty("rule3")
  public String getRule3() {
    return rule3;
  }

  @JsonProperty("rule3")
  public void setRule3(String rule3) {
    this.rule3 = rule3;
  }

  @JsonProperty("communicationType")
  public String getCommunicationType() {
    return communicationType;
  }

  @JsonProperty("communicationType")
  public void setCommunicationType(String communicationType) {
    this.communicationType = communicationType;
  }

  @JsonProperty("communicateTo")
  public String getCommunicateTo() {
    return communicateTo;
  }

  @JsonProperty("communicateTo")
  public void setCommunicateTo(String communicateTo) {
    this.communicateTo = communicateTo;
  }

  @JsonProperty("incomingCommunicationType")
  public String getIncomingCommunicationType() {
    return incomingCommunicationType;
  }

  @JsonProperty("incomingCommunicationType")
  public void setIncomingCommunicationType(String incomingCommunicationType) {
    this.incomingCommunicationType = incomingCommunicationType;
  }

  public String getClientType() {
    return clientType;
  }

  public void setClientType(String clientType) {
    this.clientType = clientType;
  }

  public String getClientGroup() {
    return clientGroup;
  }

  public void setClientGroup(String clientGroup) {
    this.clientGroup = clientGroup;
  }

  public String getClientName() {
    return clientName;
  }

  public void setClientName(String clientName) {
    this.clientName = clientName;
  }

  public String getFunction() {
    return function;
  }

  public void setFunction(String function) {
    this.function = function;
  }

  @JsonProperty("destination")
  public String getDestination() {
    return destination;
  }

  @JsonProperty("destination")
  public void setDestination(String destination) {
    this.destination = destination;
  }

  @JsonProperty("brochure")
  public String getBrochure() {
    return brochure;
  }

  @JsonProperty("brochure")
  public void setBrochure(String brochure) {
    this.brochure = brochure;
  }

  @JsonProperty("tour")
  public String getTour() {
    return tour;
  }

  @JsonProperty("tour")
  public void setTour(String tour) {
    this.tour = tour;
  }
}
