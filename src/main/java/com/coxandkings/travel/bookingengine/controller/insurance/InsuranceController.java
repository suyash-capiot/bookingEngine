package com.coxandkings.travel.bookingengine.controller.insurance;

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

import com.coxandkings.travel.bookingengine.orchestrator.insurance.InsuranceAmendProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.insurance.InsuranceBookProcessor;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/InsuranceService/v1")
public class InsuranceController {

	
//	@Autowired
//	private CruiseBookProcessor cruiseBookProcessor;
	public static final String PRODUCT = "INSURANCE";
	
	@GetMapping(value="/ping", produces= MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req)
	{
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}",HttpStatus.OK);
	}
	
	@PostMapping(value = "/insurancebook", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> book(JSONObject reqJson) throws Exception {
		String res = InsuranceBookProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/amend", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> amend(JSONObject reqJson) throws Exception {
		String res = InsuranceAmendProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

}
