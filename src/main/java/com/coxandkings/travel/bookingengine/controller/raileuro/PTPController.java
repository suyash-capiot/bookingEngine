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

import com.coxandkings.travel.bookingengine.orchestrator.raileuro.ptp.PTPBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.ptp.PTPRetrieveProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.raileuro.ptp.PTPSearchProcessor;
import com.coxandkings.travel.bookingengine.utils.Utils;


@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/RailEuroPTPService/v1")
public class PTPController {

	@GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(JSONObject reqJson) {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}

	@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> lowfareSearch(JSONObject reqJson) throws Exception {
			String res = PTPSearchProcessor.process(reqJson);
			return (Utils.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
	}
	
	@PostMapping(value = "/book", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> bookVerify(JSONObject reqJson) throws Exception {
		String res = PTPBookProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/retrieve", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> retrieveBooking(JSONObject reqJson) throws Exception {
		String res = PTPRetrieveProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

}
