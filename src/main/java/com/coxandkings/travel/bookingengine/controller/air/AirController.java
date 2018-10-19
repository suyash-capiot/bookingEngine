package com.coxandkings.travel.bookingengine.controller.air;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.orchestrator.air.AirAmendProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirCancelProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirFareRuleProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirGetGDSQueueAsyncProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirIssueTicketProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirPriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirRemoveGDSQueueMessagesProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirRepriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirRetrieveProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirSearchAsyncProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirSeatMapProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.AirSsrProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.air.OpsUtility;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/AirService/v1")
public class AirController {

	@GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService() {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}

	@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> lowfareSearch(JSONObject reqJson) throws Exception {
		String res = AirSearchAsyncProcessor.process(reqJson);
		return (AirSearchAsyncProcessor.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
	}

	@PostMapping(value = "/price", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> priceVerify(JSONObject reqJson) throws Exception {
		String res = AirPriceProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	@PostMapping(value = "/reprice", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> rePrice(JSONObject reqJson) throws Exception {
		String res = AirRepriceProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

	@PostMapping(value = "/book", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> bookVerify(JSONObject reqJson) throws Exception {
		String res = AirBookProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

	@PostMapping(value = "/getssr", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getSsr(JSONObject reqJson) throws Exception{
		String res = AirSsrProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/cancel", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> cancel(JSONObject reqJson) throws Exception {
		String res = AirCancelProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

	@PostMapping(value = "/amend", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> amend(JSONObject reqJson) throws Exception {
		String res = AirAmendProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/fareRules", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> fareRules(JSONObject reqJson) throws Exception {
		String res = AirFareRuleProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	
	@PostMapping(value = "/seatmap", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> seatmap(JSONObject reqJson) throws Exception {
		String res = AirSeatMapProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/retrievepnr", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> retrieve(JSONObject reqJson) throws Exception {
		String res = AirRetrieveProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/getGDSQueue", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getGDSQueue(JSONObject reqJson) throws Exception {
		String res = AirGetGDSQueueAsyncProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

	@PostMapping(value = "/removeGDSQueueMessages", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> removeGDSQueueMessages(JSONObject reqJson) throws Exception {
		String res = AirRemoveGDSQueueMessagesProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/applyCommercials",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSuppComm(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(OpsUtility.applyCommercials(reqJson), HttpStatus.OK);
    }
	
	@PostMapping(value = "/issueTicket",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getIssueTicket(JSONObject reqJson) throws Exception{
		String res = AirIssueTicketProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
    }
	
}
