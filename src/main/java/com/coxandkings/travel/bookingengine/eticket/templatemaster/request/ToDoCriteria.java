package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;


public class ToDoCriteria {
  private String referenceId;
  private String assignedBy;
  private String productId;
  private String clientCategoryId;
  private String clientSubCategoryId;
  private String clientTypeId;
  private String companyId;
  private String companyMarketId;
  private Long dueOn;
  // private ToDoTaskNameValues taskNameId;
  // private ToDoTaskNameValues taskName;//supplement
  // private ToDoTaskTypeValues taskTypeId;
  // private ToDoTaskTypeValues taskType;
  // private ToDoTaskSubTypeValues taskSubTypeId;
  // private ToDoTaskSubTypeValues taskSubType;
  // private ToDoFunctionalAreaValues taskFunctionalAreaId;
  // private ToDoFunctionalAreaValues taskFunctionalArea;
  // private ToDoTaskStatusValues taskStatusId;
  // private ToDoTaskStatusValues taskStatus;
  // private ToDoTaskPriorityValues taskPriorityId;
  // private ToDoTaskPriorityValues taskPriority;
  private String fileHandlerId;
  private String secondaryFileHandlerId;
  private String suggestedActions;
  private String mainTaskId;
  private String mainTaskStatusTriggerId;
  private String lockedBy;
  private Boolean overdue;

  public String getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  public String getAssignedBy() {
    return assignedBy;
  }

  public void setAssignedBy(String assignedBy) {
    this.assignedBy = assignedBy;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
  }

  public String getClientCategoryId() {
    return clientCategoryId;
  }

  public void setClientCategoryId(String clientCategoryId) {
    this.clientCategoryId = clientCategoryId;
  }

  public String getClientSubCategoryId() {
    return clientSubCategoryId;
  }

  public void setClientSubCategoryId(String clientSubCategoryId) {
    this.clientSubCategoryId = clientSubCategoryId;
  }

  public String getClientTypeId() {
    return clientTypeId;
  }

  public void setClientTypeId(String clientTypeId) {
    this.clientTypeId = clientTypeId;
  }

  public String getCompanyId() {
    return companyId;
  }

  public void setCompanyId(String companyId) {
    this.companyId = companyId;
  }

  public String getCompanyMarketId() {
    return companyMarketId;
  }

  public void setCompanyMarketId(String companyMarketId) {
    this.companyMarketId = companyMarketId;
  }

  public Long getDueOn() {
    return dueOn;
  }

  public void setDueOn(Long dueOn) {
    this.dueOn = dueOn;
  }

  public String getFileHandlerId() {
    return fileHandlerId;
  }

  public void setFileHandlerId(String fileHandlerId) {
    this.fileHandlerId = fileHandlerId;
  }

  public String getSecondaryFileHandlerId() {
    return secondaryFileHandlerId;
  }

  public void setSecondaryFileHandlerId(String secondaryFileHandlerId) {
    this.secondaryFileHandlerId = secondaryFileHandlerId;
  }

  public String getSuggestedActions() {
    return suggestedActions;
  }

  public void setSuggestedActions(String suggestedActions) {
    this.suggestedActions = suggestedActions;
  }

  public String getMainTaskId() {
    return mainTaskId;
  }

  public void setMainTaskId(String mainTaskId) {
    this.mainTaskId = mainTaskId;
  }

  public String getMainTaskStatusTriggerId() {
    return mainTaskStatusTriggerId;
  }

  public void setMainTaskStatusTriggerId(String mainTaskStatusTriggerId) {
    this.mainTaskStatusTriggerId = mainTaskStatusTriggerId;
  }

  public String getLockedBy() {
    return lockedBy;
  }

  public void setLockedBy(String lockedBy) {
    this.lockedBy = lockedBy;
  }

  public Boolean getOverdue() {
    return overdue;
  }

  public void setOverdue(Boolean overdue) {
    this.overdue = overdue;
  }


}
