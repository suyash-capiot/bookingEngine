package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

import java.util.List;

public class EmailObject {

  private String from;
  private List<String> to;
  private String subject;
  private String body;
  private String[] documentReferenceIDs;

  public String getFrom() {
    return from;
  }

  public void setFrom(String from) {
    this.from = from;
  }

  public List<String> getTo() {
    return to;
  }

  public void setTo(List<String> to) {
    this.to = to;
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

  public String[] getDocumentReferenceIDs() {
    return documentReferenceIDs;
  }

  public void setDocumentReferenceIDs(String[] documentReferenceIDs) {
    this.documentReferenceIDs = documentReferenceIDs;
  }

}
