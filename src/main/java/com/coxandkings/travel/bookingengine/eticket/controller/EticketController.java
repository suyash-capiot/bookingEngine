package com.coxandkings.travel.bookingengine.eticket.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.coxandkings.travel.bookingengine.eticket.EticketTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

@RestController
@RequestMapping(path = "/api/v1")
public class EticketController {
	
	@Autowired
	private EticketTemplate bookingNo;

	@GetMapping(value = "/eticket/pdf/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> generatePDF(@PathVariable("id") String id) {
		long startTime = System.currentTimeMillis();
		String res = ("PDF has been successfully Generated in ");
		bookingNo.checkProduct(id);
		long endTime = System.currentTimeMillis();
		long timetaken = endTime - startTime;
		return new ResponseEntity<String>(res + timetaken, HttpStatus.OK);
	}

}
