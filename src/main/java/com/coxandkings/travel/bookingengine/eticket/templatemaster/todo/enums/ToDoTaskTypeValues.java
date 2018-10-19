package com.coxandkings.travel.bookingengine.eticket.templatemaster.todo.enums;

import org.springframework.util.StringUtils;

public enum ToDoTaskTypeValues {
  MAIN("Main task"), SUB("Sub task"), FOLLOWING("Followig task");



  private String value;

  ToDoTaskTypeValues(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static ToDoTaskTypeValues fromString(String toDoTaskTypeValue) {
    ToDoTaskTypeValues toDoTaskTypeValues = null;

    if (StringUtils.isEmpty(toDoTaskTypeValue)) {
      return toDoTaskTypeValues;
    }

    for (ToDoTaskTypeValues tmpTaskTypeValues : ToDoTaskTypeValues.values()) {
      if (toDoTaskTypeValue.equalsIgnoreCase(tmpTaskTypeValues.getValue())) {
        toDoTaskTypeValues = tmpTaskTypeValues;
      }
    }

    return toDoTaskTypeValues;
  }
}
