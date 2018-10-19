package com.coxandkings.travel.bookingengine.resource;

import org.springframework.http.HttpStatus;

public class ErrorResponseResource {

    private HttpStatus httpStatus;
    private String message;
    /*private String debugMessage; Not Required for now */

    public ErrorResponseResource() {

    }

    public ErrorResponseResource(HttpStatus httpStatus) {
        this();
        this.httpStatus = httpStatus;
    }

    public ErrorResponseResource(HttpStatus httpStatus, String message) {
        this();
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public ErrorResponseResource(HttpStatus httpStatus, Throwable ex) {
        this();
        this.httpStatus = httpStatus;
        this.message = "Unexpected error";
    /*    this.debugMessage = ex.getLocalizedMessage();*/
    }

    public ErrorResponseResource(HttpStatus httpStatus, String message, Throwable ex) {
        this();
        this.httpStatus = httpStatus;
        this.message = message;
       /* this.debugMessage = ex.getLocalizedMessage();*/
    }

    public HttpStatus getStatus() {
        return httpStatus;
    }

    public void setStatus(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

 /*   public String getDebugMessage() {
        return debugMessage;
    }

    public void setDebugMessage(String debugMessage) {
        this.debugMessage = debugMessage;
    }*/

    @Override
    public String toString() {
        return "ErrorResponseResource{" +
                "httpStatus=" + httpStatus +
                ", message='" + message + '\'' +
             /*   ", debugMessage='" + debugMessage + '\'' +*/
                '}';
    }
}
