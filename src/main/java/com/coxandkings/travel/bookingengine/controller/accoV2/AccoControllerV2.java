package com.coxandkings.travel.bookingengine.controller.accoV2;

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

import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoGetPoliciesProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoModifyProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoRetrieveProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.acco.OpsUtility;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.AccoMRCancelProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.AccoMRSearchAsyncProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.AccoSearchAsyncProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.accoV2.MRSpliterCombiner;
import com.coxandkings.travel.bookingengine.utils.Utils;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/AccoService/v2")
public class AccoControllerV2 {

	public static final String PRODUCT = "ACCO";

	@GetMapping(value = "/ping",produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req) {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}

	@PostMapping(value = "/search",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getAvailabilityAndPrice(JSONObject reqJson) throws Exception{
		String res = AccoMRSearchAsyncProcessor.process(reqJson);
		return (Utils.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
	}

	@PostMapping(value = "/price",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getDetails(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(MRSpliterCombiner.process(reqJson), HttpStatus.OK);
	}
	
	@PostMapping(value = "/reprice", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> rePrice(JSONObject reqJson) throws Exception {
		return new ResponseEntity<String>(MRSpliterCombiner.process(reqJson), HttpStatus.OK);
	}
	
	@PostMapping(value = "/book",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> book(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(MRSpliterCombiner.process(reqJson), HttpStatus.OK);
	}

	/*@PostMapping(value = "/cancel",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> cancel(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(AccoMRCancelProcessor.process(reqJson), HttpStatus.OK);
	}*/
	
	@PostMapping(value = "/modify",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> modify(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(MRSpliterCombiner.processModify(reqJson), HttpStatus.OK);
		//return new ResponseEntity<String>(AccoModifyProcessor.process(reqJson), HttpStatus.OK);
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
