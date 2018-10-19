package com.coxandkings.travel.bookingengine.controller.forex;

import java.io.InputStream;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.orchestrator.forex.ConvertFrom;
import com.coxandkings.travel.bookingengine.orchestrator.forex.ConvertTo;
import com.coxandkings.travel.bookingengine.orchestrator.forex.Currencies;




@RestController
@RequestMapping("/ForeignExchangeService/v1")
public class ForexController {

	public static final String PRODUCT = "FOREIGN_EXCHANGE";
	
	@GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req) {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}
	
	@PostMapping(value = "/convertFrom", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> convertFrom(JSONObject reqJson) throws Exception {
		String res = ConvertFrom.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/convertTo", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> convertTo(JSONObject reqJson) throws Exception {
		String res = ConvertTo.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/currencies", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> currency(JSONObject reqJson) throws Exception {
		String res = Currencies.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
}
