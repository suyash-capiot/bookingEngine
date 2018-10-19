package com.coxandkings.travel.bookingengine.controller.activities;

import java.io.InputStream;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.activities.ActivityAmendProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.activities.ActivityBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.activities.ActivityCancelProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.activities.ActivityPriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.activities.ActivityRepriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.activities.ActivityRetrieveProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.activities.ActivitySearchAsyncProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.activities.ActivitySearchProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.activities.OpsUtility;
import com.coxandkings.travel.bookingengine.utils.Utils;

@RestController
@RequestMapping("/ActivityService/v1")
public class ActivityController {

	@GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req) {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}

	@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> lowfareSearch(JSONObject reqJson) throws Exception {
		String res = ActivitySearchAsyncProcessor.process(reqJson);
		return (Utils.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
	}

	@PostMapping(value = "/price", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getDetails(JSONObject reqJson) throws Exception {
		String res = ActivityPriceProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

	@PostMapping(value = "/reprice", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> rePrice(JSONObject reqJson) throws Exception {
		String res = ActivityRepriceProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

	@PostMapping(value = "/book", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> book(JSONObject reqJson) throws Exception {
		String res = ActivityBookProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

	@PostMapping(value = "/cancel", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> cancel(JSONObject reqJson) throws Exception {
		String res = ActivityCancelProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

	@PostMapping(value = "/retrieve", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> retrieve(InputStream req) throws Exception {
		JSONTokener jsonTok = new JSONTokener(req);
		JSONObject reqJson = new JSONObject(jsonTok);
		String res = ActivityRetrieveProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

	@PostMapping(value = "/amend", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> amend(JSONObject reqJson) throws Exception {
		String res = ActivityAmendProcessor.processV2(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}

	@PostMapping(value = "/applyCommercials",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getSuppComm(InputStream reqJson) throws Exception{
		JSONTokener jsonTok = new JSONTokener(reqJson);
		JSONObject req= new JSONObject(jsonTok);
		return new ResponseEntity<String>(OpsUtility.applyCommercials(req), HttpStatus.OK);

	}

	//For simplicity in debugging, Adding Sync Search Req Controller
	@PostMapping(value = "/searchTemp", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> lowfareSearchTemp(JSONObject reqJson) throws Exception {
		String res = ActivitySearchProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}



}
