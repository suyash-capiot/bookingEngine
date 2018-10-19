package com.coxandkings.travel.bookingengine.eticket.templatemaster.todo.enums;

import org.springframework.util.StringUtils;

public enum ToDoTaskPriorityValues {
  HIGH("High", 100), MEDIUM("Medium", 50), LOW("Low", 0);



  private String value;
  private Integer weight;

  ToDoTaskPriorityValues(String value, Integer weight) {
    this.value = value;
    this.weight = weight;
  }

  public String getValue() {
    return value;
  }

  public Integer getWeight() {
    return weight;
  }

  public static ToDoTaskPriorityValues fromString(String toDoTaskPriorityValue) {
    ToDoTaskPriorityValues toDoTaskPriorityValues = null;

    if (StringUtils.isEmpty(toDoTaskPriorityValue)) {
      return toDoTaskPriorityValues;
    }

    for (ToDoTaskPriorityValues tmpToDoPriorityValues : ToDoTaskPriorityValues.values()) {
      if (toDoTaskPriorityValue.equalsIgnoreCase(tmpToDoPriorityValues.getValue())) {
        toDoTaskPriorityValues = tmpToDoPriorityValues;
      }
    }

    return toDoTaskPriorityValues;
  }
}
