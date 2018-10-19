package com.coxandkings.travel.bookingengine.controller.holidays;

import java.io.InputStream;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.orchestrator.holidays.HolidaysAddServiceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.HolidaysAmClProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.HolidaysBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.HolidaysGetPackageDetailsProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.HolidaysGetPackageDetailsProcessorV2;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.HolidaysRepriceProcessorV2;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.HolidaysRetrieveProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.HolidaysSearchAsyncProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.HolidaysSearchProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.holidays.OpsUtility;
import com.coxandkings.travel.bookingengine.utils.Utils;


@RestController
@RequestMapping("/HolidaysService/v1")
public class HolidaysController {


	@GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req) {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}

	@PostMapping(value = "/searchByTour", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getTour(JSONObject reqJson) throws Exception {
        String res = HolidaysSearchAsyncProcessor.process(reqJson);
        return (Utils.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
    }
	
	/*@PostMapping(value = "/searchByTour", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getTour(JSONObject reqJson) throws Exception {
          String res = HolidaysSearchProcessor.process(reqJson);
          return new ResponseEntity<String>(res, HttpStatus.OK);
	}*/
	
	@PostMapping(value = "/getPackageDetails", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getPackageDetails(JSONObject reqJson) throws Exception {
	    String res = HolidaysGetPackageDetailsProcessorV2.processV2(reqJson);
        return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/addservice", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAddService(JSONObject reqJson) throws Exception {
	    String res = HolidaysAddServiceProcessor.process(reqJson);
        return new ResponseEntity<String>(res, HttpStatus.OK);
    }
	
	@PostMapping(value = "/reprice", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getReprice(JSONObject reqJson) throws Exception {
	    String res = HolidaysRepriceProcessorV2.process(reqJson);
        return new ResponseEntity<String>(res, HttpStatus.OK);
	}
	
	@PostMapping(value = "/book", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getBook(JSONObject reqJson) throws Exception {
	    String res = HolidaysBookProcessor.process(reqJson);
        return new ResponseEntity<String>(res, HttpStatus.OK);
    }
	
	@PostMapping(value = "/modify", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getModification(JSONObject reqJson) throws Exception {
	    String res = HolidaysAmClProcessor.process(reqJson);
        return new ResponseEntity<String>(res, HttpStatus.OK);
    }
	
	@PostMapping(value = "/retrieve", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getRetrieveBooking(JSONObject reqJson) throws Exception {
        String res = HolidaysRetrieveProcessor.process(reqJson);
        return new ResponseEntity<String>(res, HttpStatus.OK);
    }
	
	@PostMapping(value = "/applyCommercials",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getSuppComm(JSONObject reqJson) throws Exception{
		return new ResponseEntity<String>(OpsUtility.applyCommercials(reqJson), HttpStatus.OK);
	}
}
