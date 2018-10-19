package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

public class DynamicAttributes {

  private String name;
  private String value;

  public DynamicAttributes() {}

  public DynamicAttributes(String name, String value) {
    this.name = name;
    this.value = value;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

@Override
public String toString() {
	return "DynamicAttributes [name=" + name + ", value=" + value + "]";
}
  
}
