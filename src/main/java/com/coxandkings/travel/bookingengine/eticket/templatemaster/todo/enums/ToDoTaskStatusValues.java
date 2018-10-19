package com.coxandkings.travel.bookingengine.eticket.templatemaster.todo.enums;

import org.springframework.util.StringUtils;

public enum ToDoTaskStatusValues {
  NEW("New"), ASSIGNED("Assigned"), IN_PROGRESS("In progress"), PENDING("Pending"), COMPLETED(
      "Completed"), OPEN("Open"), CLOSED("Closed"), FAILED("Failed"), REJECTED("Rejected");

  private String value;

  ToDoTaskStatusValues(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static ToDoTaskStatusValues fromString(String toDoTaskStatusValue) {
    ToDoTaskStatusValues toDoTaskStatusValues = null;

    if (StringUtils.isEmpty(toDoTaskStatusValue)) {
      return toDoTaskStatusValues;
    }

    for (ToDoTaskStatusValues tmpToDoTaskTypeValues : ToDoTaskStatusValues.values()) {
      if (tmpToDoTaskTypeValues.getValue().equalsIgnoreCase(toDoTaskStatusValue)) {
        toDoTaskStatusValues = tmpToDoTaskTypeValues;
        break;
      }
    }

    return toDoTaskStatusValues;
  }
}
