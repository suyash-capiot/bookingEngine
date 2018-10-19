package com.coxandkings.travel.bookingengine.controller;

import java.io.InputStream;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.test_suite.AccoTestSuite;

@RestController
@RequestMapping("/AutomatedTestService")
public class TestSuiteController {

	@PostMapping(value="/Acco",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> test(InputStream req) throws Exception {
		
		JSONObject reqJson = new JSONObject(new JSONTokener(req));
		AccoTestSuite test = new AccoTestSuite(reqJson);
		return new ResponseEntity<String>(test.process(reqJson).toString(), HttpStatus.OK);
	}
}
