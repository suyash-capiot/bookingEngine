package com.coxandkings.travel.bookingengine.exception;

import java.util.Locale;

public class BookingEngineException extends Exception{
    private String errorCode;
    private String[] paramList;
    private Locale locale;

    public BookingEngineException() {
    }

    public BookingEngineException(String errorCode, Locale locale) {
        this();
        this.errorCode = errorCode;
        this.locale = locale;
    }

    public BookingEngineException(String errorCode, String[] paramList, Locale locale) {
        this();
        this.errorCode = errorCode;
        this.paramList = paramList;
        this.locale = locale;
    }

    public BookingEngineException(String message, String errorCode, String[] paramList, Locale locale) {
        this();
        this.errorCode = errorCode;
        this.paramList = paramList;
        this.locale = locale;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCodes(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String[] getParamList() {
        return paramList;
    }

    public void setParamList(String[] paramList) {
        this.paramList = paramList;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }


    /*public BookingEngineException() {
    }

    public BookingEngineException(String exceptionMessage) {
        this();
        this.exceptionMessage = exceptionMessage;
    }

    public BookingEngineException(HttpStatus httpStatus, String exceptionMessage) {
        this();
        this.exceptionMessage = exceptionMessage;
    }
*/

}
