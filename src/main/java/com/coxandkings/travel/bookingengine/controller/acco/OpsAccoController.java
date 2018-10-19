package com.coxandkings.travel.bookingengine.controller.acco;

import java.util.Map;

import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.coxandkings.travel.bookingengine.orchestrator.acco.AccoRepriceProcessor;
import com.coxandkings.travel.bookingengine.utils.redis.RedisHotelData;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/OpsAccoService/v1")
public class OpsAccoController {

	@PostMapping(value = "/reprice", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> rePrice(JSONObject reqJson) throws Exception {
		return new ResponseEntity<String>(AccoRepriceProcessor.opsProcess(reqJson), HttpStatus.OK);
	}
	
	@GetMapping(value = "/redisHotelData/{hotelCode}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Map<String,Object>> getHotelData(@PathVariable String hotelCode){
		
		return new ResponseEntity<Map<String,Object>>(RedisHotelData.getHotelInfo(hotelCode),HttpStatus.OK);
		
	}
	
}
