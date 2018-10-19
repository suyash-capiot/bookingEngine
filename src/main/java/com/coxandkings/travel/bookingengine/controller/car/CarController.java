package com.coxandkings.travel.bookingengine.controller.car;

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

import com.coxandkings.travel.bookingengine.orchestrator.car.CarConstants;
import com.coxandkings.travel.bookingengine.orchestrator.car.SubType;
import com.coxandkings.travel.bookingengine.orchestrator.car.rental.RentalBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.car.rental.RentalModifyProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.car.rental.RentalRetrieveProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.car.rental.RentalSearchAsyncProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive.CarBookProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive.CarGetPoliciesProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive.CarModifyProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive.CarPriceProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive.CarRetrieveProcessor;
import com.coxandkings.travel.bookingengine.orchestrator.car.selfdrive.CarSearchAsyncProcessor;
import com.coxandkings.travel.bookingengine.utils.Utils;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/CarService/v1")
public class CarController implements CarConstants{

	@GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> pingService(InputStream req) {
		return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
	}

	@PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> lowfareSearch(JSONObject reqJson) throws Exception {
		
		String res;
		String subTypeStr = reqJson.getJSONObject(JSON_PROP_REQBODY).optString(JSON_PROP_PRODCATEGSUBTYPE);
		SubType subType = SubType.forString(subTypeStr);
		if(subType == null) {
			res = "{\"errorMessage\" : \"Invalid prodSubType\"}";
			return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
		}
		res = subType.equals(SubType.RENTAL) ? RentalSearchAsyncProcessor.process(reqJson) : CarSearchAsyncProcessor.process(reqJson);
		return (Utils.isSynchronousSearchRequest(reqJson)) ? new ResponseEntity<String>(res, HttpStatus.OK) : new ResponseEntity<String>(HttpStatus.ACCEPTED);
	}
	
	@PostMapping(value = "/price", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> rePrice(JSONObject reqJson) throws Exception{
		
		String res;
		String subTypeStr = reqJson.getJSONObject(JSON_PROP_REQBODY).optString(JSON_PROP_PRODCATEGSUBTYPE);
		SubType subType = SubType.forString(subTypeStr);
		if(subType==null) {
			res = "{\"errorMessage\" : \"Invalid prodSubType\"}";
			return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
		}
		if(subType.equals(SubType.RENTAL)) {
			res = "{\"errorMessage\" : \"No price Operation for Rental\"}";
			return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
		}
		else {
			res = CarPriceProcessor.process(reqJson);
			return new ResponseEntity<String>(res, HttpStatus.OK);
		}
	}
	
	@PostMapping(value = "/book", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> bookVerify(JSONObject reqJson) throws Exception{
		
		String res;
		String subTypeStr = reqJson.getJSONObject(JSON_PROP_REQBODY).optString(JSON_PROP_PRODCATEGSUBTYPE);
		SubType subType = SubType.forString(subTypeStr);
		if(subType == null) {
			res = "{\"errorMessage\" : \"Invalid prodSubType\"}";
			return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
		}
		res = subType.equals(SubType.RENTAL) ? RentalBookProcessor.process(reqJson) : CarBookProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
	
	}
	
	@PostMapping(value = "/retrieve", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> retrieve(JSONObject reqJson) throws Exception{
		
		String res;
		String subTypeStr = reqJson.getJSONObject(JSON_PROP_REQBODY).optString(JSON_PROP_PRODCATEGSUBTYPE);
		SubType subType = SubType.forString(subTypeStr);
		if(subType == null) {
			res = "{\"errorMessage\" : \"Invalid prodSubType\"}";
			return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
		}
		res = subType.equals(SubType.RENTAL) ? RentalRetrieveProcessor.process(reqJson) : CarRetrieveProcessor.process(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
			
	}
	
	
	@PostMapping(value = "/getPolicies", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getPolicies(JSONObject reqJson) throws Exception{
		
		String res;
		String subTypeStr = reqJson.getJSONObject(JSON_PROP_REQBODY).optString(JSON_PROP_PRODCATEGSUBTYPE);
		SubType subType = SubType.forString(subTypeStr);
		switch(subType) {
			//TODO : Get Policies for Rental is under development by SI team.
			case RENTAL : res = "{\"errorMessage\" : \"No getPolicies Operation for Rental\"}";
				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
			case CAR : res = CarGetPoliciesProcessor.process(reqJson);
				return new ResponseEntity<String>(res, HttpStatus.OK);
			default : res = "{\"errorMessage\" : \"Invalid prodSubType\"}";
				return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
		}
	}
	
	@PostMapping(value = "/modify", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> modify(JSONObject reqJson) throws Exception{
		
		String res;
		String subTypeStr = reqJson.getJSONObject(JSON_PROP_REQBODY).optString(JSON_PROP_PRODCATEGSUBTYPE);
		SubType subType = SubType.forString(subTypeStr);
		if(subType == null) {
			res = "{\"errorMessage\" : \"Invalid prodSubType\"}";
			return new ResponseEntity<String>(res, HttpStatus.BAD_REQUEST);
		}
		res = subType.equals(SubType.RENTAL) ? RentalModifyProcessor.process(reqJson) : CarModifyProcessor.processV2(reqJson);
		return new ResponseEntity<String>(res, HttpStatus.OK);
		
	
	}
}
