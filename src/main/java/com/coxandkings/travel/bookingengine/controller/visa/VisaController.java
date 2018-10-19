package com.coxandkings.travel.bookingengine.controller.visa;

import java.io.InputStream;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.visa.VisaBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.visa.VisaCancelProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.visa.VisaSearchProcessor;
import com.coxandkings.travel.bookingengine.utils.Utils;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/VisaService/v1")
public class VisaController {

public static final String PRODUCT = "VISA";
	
	@GetMapping(value="/ping", produces= MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req)
	{
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}",HttpStatus.OK);
	}
	
	@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> lowfareSearch(JSONObject reqJson) throws Exception {
		try {
			String res = VisaSearchProcessor.process(reqJson);
			return (Utils.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
		}
		catch (InternalProcessingException intx) {
			return new ResponseEntity<String>(intx.toString("TRLERR500"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		catch (RequestProcessingException reqx) {
			return new ResponseEntity<String>(reqx.toString("TRLERR400"), HttpStatus.BAD_REQUEST);
		}
		catch(Exception e)
		{
			return new ResponseEntity<String>((new RequestProcessingException(e)).toString("TRLERR500"), HttpStatus.BAD_REQUEST);
		}
	}
	
	@PostMapping(value = "/book", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> bookVisa(JSONObject reqJson) throws Exception {
		try {
			String res = VisaBookProcessor.process(reqJson);
			return (Utils.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
		}
		catch (InternalProcessingException intx) {
			return new ResponseEntity<String>(intx.toString("TRLERR500"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		catch (RequestProcessingException reqx) {
			return new ResponseEntity<String>(reqx.toString("TRLERR400"), HttpStatus.BAD_REQUEST);
		}
		catch(Exception e)
		{
			return new ResponseEntity<String>((new RequestProcessingException(e)).toString("TRLERR500"), HttpStatus.BAD_REQUEST);
		}
	}
	
	@PostMapping(value = "/cancel", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> cancel(JSONObject reqJson) throws Exception {
		String res = VisaCancelProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
}
