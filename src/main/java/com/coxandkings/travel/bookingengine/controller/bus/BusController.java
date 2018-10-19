package com.coxandkings.travel.bookingengine.controller.bus;

import java.io.InputStream;

import org.json.JSONObject;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.orchestrator.bus.OpsUtility;
import com.coxandkings.travel.bookingengine.orchestrator.bus.BusBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.bus.BusCancelTicketProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.bus.BusGetPolicyProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.bus.BusRetrieveBookingProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.bus.BusSearchAsyncProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.bus.BusSearchProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.bus.BusSeatBlockingProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.bus.BusSeatMapProcessor;

@RestController
@RequestMapping("/BusService/v1")
public class BusController {

	

	public static final String PRODUCT = "BUS";
	
	
	@GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req) {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}

	@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> lowfareSearch(JSONObject reqJson) throws Exception {
		
		
		String res = BusSearchAsyncProcessor.process(reqJson);
		return (BusSearchAsyncProcessor.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
		
	}
	
	@PostMapping(value = "/aSearch", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> lowfareAsyncSearch(JSONObject reqJson) throws Exception {
		String res = BusSearchProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/SeatMap", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getLayout(JSONObject reqJson) throws Exception {
		String res = BusSeatMapProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	
	
	@PostMapping(value = "/SeatBlocking", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> blockTicket(JSONObject reqJson) throws Exception {
		
		String res = BusSeatBlockingProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/CreateBooking", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> bookTicket(JSONObject reqJson) throws Exception {
		
		String res = BusBookProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/RetrieveBooking", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getTicket(JSONObject reqJson) throws Exception {
		
		String res = BusRetrieveBookingProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/GetPolicies", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getPolicy(JSONObject reqJson) throws Exception {
		
		String res = BusGetPolicyProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	
	@PostMapping(value = "/CancelBooking", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> CancelTicket(JSONObject reqJson) throws Exception {
		
		String res = BusCancelTicketProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	//for offline booking
	@PostMapping(value = "/applyCommercials",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSuppComm(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(OpsUtility.applyCommercials(reqJson), HttpStatus.OK);
    }
	
	
//	@PostMapping(value = "/paxcalculation",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<String> calculate(JSONObject reqJson) throws Exception{
//		return new ResponseEntity<String>(OpsUtility.calculate(reqJson), HttpStatus.OK);
//    }
	
	 
}
