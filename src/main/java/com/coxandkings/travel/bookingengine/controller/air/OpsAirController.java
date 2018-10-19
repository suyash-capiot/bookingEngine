package com.coxandkings.travel.bookingengine.controller.air;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.orchestrator.air.AirRepriceProcessor;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/OpsAirService/v1")
public class OpsAirController {

	@PostMapping(value = "/reprice", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> rePrice(JSONObject reqJson) throws Exception {
			String res = AirRepriceProcessor.opsProcess(reqJson);
			return new ResponseEntity<String>(res, HttpStatus.OK);
	}
}
