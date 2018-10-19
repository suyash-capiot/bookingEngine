package com.coxandkings.travel.bookingengine.eticket.templatemaster.todo.enums;


public enum ToDoFunctionalAreaValues {
  OPERATIONS("Operations"), FINANCE("Finance"), SALES_MARKETING("Sales & Marketing");

  String value;

  ToDoFunctionalAreaValues(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static ToDoFunctionalAreaValues fromString(String toDoFunctionalAreaValue) {
    ToDoFunctionalAreaValues aFunctionalArea = null;
    if (toDoFunctionalAreaValue == null || toDoFunctionalAreaValue.isEmpty()) {
      return aFunctionalArea;
    }

    for (ToDoFunctionalAreaValues tmpBookingAttribute : ToDoFunctionalAreaValues.values()) {
      if (tmpBookingAttribute.getValue().equalsIgnoreCase(toDoFunctionalAreaValue)) {
        aFunctionalArea = tmpBookingAttribute;
        break;
      }
    }
    return aFunctionalArea;
  }
}

