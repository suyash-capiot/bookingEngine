package com.coxandkings.travel.bookingengine.eticket.templatemaster.todo.enums;

import org.springframework.util.StringUtils;

public enum ToDoTaskNameValues {
  CONFIRM("Confirm"), RECONFIRM("Reconfirm"), CANCEL("Cancel"), BOOK("Book"), APPROVE(
      "Approve"), UPDATE(
          "Update"), CORRECTION("Correction"), SETTLEMENT("Settlement"), AMEND("Amend");

  private String value;

  ToDoTaskNameValues(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static ToDoTaskNameValues fromString(String toDoTaskNameValue) {
    ToDoTaskNameValues toDoTaskNameValues = null;

    if (StringUtils.isEmpty(toDoTaskNameValue)) {
      return toDoTaskNameValues;
    }

    for (ToDoTaskNameValues tmpToDoTaskNameValues : ToDoTaskNameValues.values()) {
      if (toDoTaskNameValue.equalsIgnoreCase(tmpToDoTaskNameValues.getValue())) {
        toDoTaskNameValues = tmpToDoTaskNameValues;
      }
    }

    return toDoTaskNameValues;
  }
}

