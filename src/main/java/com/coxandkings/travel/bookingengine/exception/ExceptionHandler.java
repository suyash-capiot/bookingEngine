package com.coxandkings.travel.bookingengine.exception;

import com.coxandkings.travel.bookingengine.resource.ErrorResponseResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.text.MessageFormat;

@ControllerAdvice
public class ExceptionHandler extends ResponseEntityExceptionHandler {

    @Autowired
    private MessageSource messageSource;

    //Custom Exception-Handling
    @org.springframework.web.bind.annotation.ExceptionHandler(value = {BookingEngineException.class})
    protected ResponseEntity<Object> handleSavedSearchNotFoundException(BookingEngineException bookingEngineException) {
       String errorMessage= errorMessageFormatter(bookingEngineException);
        System.out.println(" errorMessage is "+ errorMessage);
        return buildResponseEntity(new ErrorResponseResource(HttpStatus.BAD_REQUEST,
                errorMessage, bookingEngineException));
    }

    //Throwable Exception-Handling
    @org.springframework.web.bind.annotation.ExceptionHandler(value = {IllegalArgumentException.class, IllegalStateException.class})
    protected ResponseEntity<Object> handleConflict(RuntimeException runtimeException) {
        System.out.println("Runtime Exception   "+ runtimeException.getMessage());
        return buildResponseEntity(new ErrorResponseResource(HttpStatus.CONFLICT,
                runtimeException.getLocalizedMessage(), runtimeException));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException
               httpRequestMethodNotSupportedException,  HttpHeaders headers, HttpStatus status, WebRequest request) {
        return buildResponseEntity(new ErrorResponseResource(HttpStatus.METHOD_NOT_ALLOWED,
               httpRequestMethodNotSupportedException.getLocalizedMessage(), httpRequestMethodNotSupportedException));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException
              missingServletRequestParameterException, HttpHeaders headers, HttpStatus status,WebRequest request) {
        return buildResponseEntity(new ErrorResponseResource(HttpStatus.BAD_REQUEST,
                missingServletRequestParameterException.getLocalizedMessage(), missingServletRequestParameterException));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException methodArgumentNotValidException,
                                                                  HttpHeaders headers, HttpStatus status, WebRequest request) {
        return buildResponseEntity(new ErrorResponseResource(HttpStatus.BAD_REQUEST,
                methodArgumentNotValidException.getLocalizedMessage(), methodArgumentNotValidException));
    }

    private ResponseEntity<Object> buildResponseEntity(ErrorResponseResource errorResponseResource) {
        return new ResponseEntity<>(errorResponseResource,  new HttpHeaders(),errorResponseResource.getStatus());
    }

    private String errorMessageFormatter(BookingEngineException bookingEngineException){
        StringBuffer formattedErrorMessage= new StringBuffer();
        int i=0;

        String errorMessage= messageSource.getMessage(bookingEngineException.getErrorCode(),null, bookingEngineException.getLocale());
        MessageFormat messageFormat = new MessageFormat(errorMessage);
        System.out.println(" errorMessgae is    "+ errorMessage);

        String[] paramCodes= bookingEngineException.getParamList();
        String[] paramValues = new String[paramCodes.length];

            for(String paramCode:paramCodes){
                System.out.println("param value is "+ paramCode);
                paramValues[i]=messageSource.getMessage(paramCode,null, bookingEngineException.getLocale());
                i++;
            }

        messageFormat.format(paramValues, formattedErrorMessage, null);
        return formattedErrorMessage.toString();
    }

   //TODO Handle Exception for postgres connections - No need As it will be converted to Business Exception in Service Layer
}