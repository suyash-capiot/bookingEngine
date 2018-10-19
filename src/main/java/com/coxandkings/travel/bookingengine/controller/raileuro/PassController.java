package com.coxandkings.travel.bookingengine.controller.raileuro;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass.OpsUtility;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass.PassBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass.PassRetrieveProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.pass.PassSearchProcessor;
import com.coxandkings.travel.bookingengine.utils.Utils;


@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/RailEuroPassService/v1")
public class PassController {

	@GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(JSONObject reqJson) {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}

	@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getAvailabilityAndPrice(JSONObject reqJson) throws Exception {
			String res = PassSearchProcessor.process(reqJson);
			return (Utils.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
	}
	
	@PostMapping(value = "/book", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> createBooking(JSONObject reqJson) throws Exception {
		String res = PassBookProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}			
	
	@PostMapping(value = "/retrieve", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> retrieveBooking(JSONObject reqJson) throws Exception {
		String res = PassRetrieveProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/applyCommercials",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSuppComm(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(OpsUtility.applyCommercials(reqJson), HttpStatus.OK);

    }

}
