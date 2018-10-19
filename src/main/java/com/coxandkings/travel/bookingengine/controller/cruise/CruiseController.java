package com.coxandkings.travel.bookingengine.controller.cruise;

import java.io.InputStream;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.cruise.CruiseAmendProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.cruise.CruiseBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.cruise.CruiseCabinAvailProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.cruise.CruiseCancelProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.cruise.CruisePriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.cruise.CruiseRePriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.cruise.CruiseSearchAsyncProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.cruise.CruiseSearchProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.cruise.OpsUtility;
import com.coxandkings.travel.bookingengine.utils.Utils;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/CruiseService/v1")
public class CruiseController {
	
//	@Autowired
//	private CruiseBookProcessor cruiseBookProcessor;
	public static final String PRODUCT = "CRUISE";
	
	@GetMapping(value="/ping", produces= MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req)
	{
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}",HttpStatus.OK);
	}
	
	@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> lowfareSearch(JSONObject reqJson) throws Exception {
		try {
			String res = CruiseSearchAsyncProcessor.process(reqJson);
			return (Utils.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
		}
		catch (InternalProcessingException intx) {
			return new ResponseEntity<String>(intx.toString("CKIL231556_CRUSR_ER02"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		catch (RequestProcessingException reqx) {
			return new ResponseEntity<String>(reqx.toString("CKIL231556_CRUSR_ER01"), HttpStatus.BAD_REQUEST);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return new ResponseEntity<String>((new RequestProcessingException(e)).toString("CKIL231556_CRUSR_ER01"), HttpStatus.BAD_REQUEST);
		}
		/*
		catch (ValidationException valx) {
			return new ResponseEntity<String>(valx.toString(), HttpStatus.BAD_REQUEST);
		}
		catch (Exception x) {
			return new ResponseEntity<String>((new RequestProcessingException(x)).toString("CKIL231556_AIRSR_ER01"), HttpStatus.BAD_REQUEST);
		}*/
	}
	
	@PostMapping(value="/search1",produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> cruiseAvailability(JSONObject reqJson) throws Exception
	{
		try {
			String res = CruiseSearchProcessor.processV2(reqJson);
			return new ResponseEntity<String>(res,HttpStatus.OK);
		}
		catch (InternalProcessingException intx) {
			return new ResponseEntity<String>(intx.toString("CKIL231556_CRUSR_ER02"), HttpStatus.INTERNAL_SERVER_ERROR);
		}
		catch(RequestProcessingException reqx)
		{
			return new ResponseEntity<String>(reqx.toString("CKIL231556_CRUSR_ER01"), HttpStatus.BAD_REQUEST);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return new ResponseEntity<String>((new RequestProcessingException(e)).toString("CKIL231556_CRUSR_ER01"), HttpStatus.BAD_REQUEST);
		}
	}
	
	@PostMapping(value = "/price", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> categoryAvail(JSONObject reqJson) throws Exception {
		String res = CruisePriceProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/cabinAvail", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> cabinAvail(JSONObject reqJson) throws Exception {
		String res = CruiseCabinAvailProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/rePricing", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> rePrice(JSONObject reqJson) throws Exception {
		String res = CruiseRePriceProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/book", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> book(JSONObject reqJson) throws Exception {
		String res = CruiseBookProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/amend", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> amend(JSONObject reqJson) throws Exception {
		String res = CruiseAmendProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/cancel", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> cancel(JSONObject reqJson) throws Exception {
		String res = CruiseCancelProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/applyCommercials",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSuppComm(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(OpsUtility.applyCommercials(reqJson), HttpStatus.OK);
    }
	
}
