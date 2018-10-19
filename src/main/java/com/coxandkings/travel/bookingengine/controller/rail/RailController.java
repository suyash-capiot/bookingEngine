package com.coxandkings.travel.bookingengine.controller.rail;

import java.io.InputStream;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.orchestrator.rail.RailBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.rail.RailCancelProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.rail.RailGetDetailsProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.rail.RailRepriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.rail.RailRetrieveProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.rail.RailSearchProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.rail.TrainScheduleProcessor;

@RestController
@RequestMapping("/RailService/v1")
public class RailController {
	
	//public static final String PRODUCT = "ACCO";
	
	/*@Autowired
	AccoBookProcessor  bookservice;
	*/

	@GetMapping(value = "/ping",produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> pingService(InputStream req) {
        return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
    }
	
	@PostMapping(value = "/search",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAvailabilityAndPrice(JSONObject reqJson) throws Exception{
			//JSONTokener jsonTok = new JSONTokener(req);
			//JSONObject reqJson = new JSONObject(jsonTok);
			return new ResponseEntity<String>(RailSearchProcessor.process(reqJson), HttpStatus.OK);
    }
	
	@PostMapping(value = "/reprice",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> rePrice(JSONObject reqJson) throws Exception{
        return new ResponseEntity<String>(RailRepriceProcessor.process(reqJson), HttpStatus.OK);
    }
	
	@PostMapping(value = "/trainSchedule",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> trainSchedule(JSONObject reqJson) throws Exception{
        return new ResponseEntity<String>(TrainScheduleProcessor.process(reqJson), HttpStatus.OK);
    }
	
	@PostMapping(value = "/getDetails",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getDetails(JSONObject reqJson) throws Exception{
        return new ResponseEntity<String>(RailGetDetailsProcessor.process(reqJson), HttpStatus.OK);
    }
	
	@PostMapping(value = "/retrieve",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> retrieve(JSONObject reqJson) throws Exception{
        return new ResponseEntity<String>(RailRetrieveProcessor.process(reqJson), HttpStatus.OK);
    }
	
	@PostMapping(value = "/cancel",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cancel(JSONObject reqJson) throws Exception{
		//JSONTokener jsonTok = new JSONTokener(req);
        //JSONObject reqJson = new JSONObject(jsonTok);
        return new ResponseEntity<String>(RailCancelProcessor.process(reqJson), HttpStatus.OK);
    }
	
	@PostMapping(value = "/book",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> book(JSONObject reqJson) throws Exception{
        return new ResponseEntity<String>(RailBookProcessor.process(reqJson), HttpStatus.OK);
    }	
	
}
