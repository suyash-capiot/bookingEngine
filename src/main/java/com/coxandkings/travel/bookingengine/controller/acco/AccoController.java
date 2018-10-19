package com.coxandkings.travel.bookingengine.controller.acco;

import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoGetPoliciesProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoModifyProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoModifyProcessorV1;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoPriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoRepriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoRetrieveProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoSearchAsyncProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.acco.OpsUtility;
import com.coxandkings.travel.bookingengine.utils.Utils;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/AccoService/v1")
public class AccoController {

	public static final String PRODUCT = "ACCO";

	@GetMapping(value = "/ping",produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req) {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}

	@PostMapping(value = "/search",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getAvailabilityAndPrice(JSONObject reqJson) throws Exception{
		String res = AccoSearchAsyncProcessor.process(reqJson);
		return (Utils.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
	}

	@PostMapping(value = "/price",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getDetails(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(AccoPriceProcessor.process(reqJson), HttpStatus.OK);
	}

	@PostMapping(value = "/reprice", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> rePrice(JSONObject reqJson) throws Exception {
		return new ResponseEntity<String>(AccoRepriceProcessor.process(reqJson), HttpStatus.OK);
	}

	@PostMapping(value = "/book",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> book(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(AccoBookProcessor.processV2(reqJson), HttpStatus.OK);
	}

	@PostMapping(value = "/modify",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> modify(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(AccoModifyProcessorV1.process(reqJson), HttpStatus.OK);
	}
	
	@PostMapping(value = "/getPolicies",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getPolicies(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(AccoGetPoliciesProcessor.process(reqJson), HttpStatus.OK);
	}
	
	@PostMapping(value = "/retrieve",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> retrieve(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(AccoRetrieveProcessor.process(reqJson), HttpStatus.OK);

    }
	
	@PostMapping(value = "/applyCommercials",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSuppComm(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(OpsUtility.applyCommercials(reqJson), HttpStatus.OK);

    }
	
	@PostMapping(value = "/getkafkaRQ",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getKafkaRQ(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(OpsUtility.getKafkaRQ(reqJson), HttpStatus.OK);

    }
	
}
