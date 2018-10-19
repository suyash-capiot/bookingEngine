package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

import java.util.List;

public class EmailToSend {
  private String fromMail;
  private List<String> toMail;
  private String subject;
  private String body;
  private String priority = "HIGH";
  private String[] documentReferenceIDs;

  public String getFromMail() {
    return fromMail;
  }

  public void setFromMail(String fromMail) {
    this.fromMail = fromMail;
  }

  public List<String> getToMail() {
    return toMail;
  }

  public void setToMail(List<String> toMail) {
    this.toMail = toMail;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String priority) {
    this.priority = priority;
  }

  public String[] getDocumentReferenceIDs() {
    return documentReferenceIDs;
  }

  public void setDocumentReferenceIDs(String[] documentReferenceIDs) {
    this.documentReferenceIDs = documentReferenceIDs;
  }


}
