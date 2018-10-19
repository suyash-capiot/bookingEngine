package com.coxandkings.travel.bookingengine.controller.accoV3;

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
import com.coxandkings.travel.bookingengine.orchestrator.accoV3.AccoSearchProcessor;
import com.coxandkings.travel.bookingengine.utils.Utils;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/AccoService/v2.1")
public class AccoControllerV3 {

	public static final String PRODUCT = "ACCO";

	@GetMapping(value = "/ping",produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req) {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}

	@PostMapping(value = "/searchSR",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getAvailabilityAndPrice(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(AccoSearchProcessor.processV3(reqJson), HttpStatus.OK);
	}
	

}
