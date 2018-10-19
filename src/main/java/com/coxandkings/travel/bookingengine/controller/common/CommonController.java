package com.coxandkings.travel.bookingengine.controller.common;


import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.coxandkings.travel.bookingengine.utils.BookingPriority;
import com.coxandkings.travel.bookingengine.utils.redis.RedisRoEData;

@RestController
public class CommonController {
	
	private static final Logger logger = LogManager.getLogger(CommonController.class);
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
	
	@GetMapping(value = {"/getRoe/{fromCcy}/{toCcy}/{market}","/getRoe/{fromCcy}/{toCcy}/{market}/{cutOffDate_str}"})
    public BigDecimal roeService(@PathVariable("fromCcy") String fromCcy,@PathVariable("toCcy") String toCcy,@PathVariable("market") String market,@PathVariable Optional<String> cutOffDate_str) {
		if(cutOffDate_str.isPresent()) {
			Date cutOffDate;
			try {
				cutOffDate = DATE_FORMAT.parse(cutOffDate_str.get());
			} catch (ParseException e) {
				logger.error("Date value/format incorrect. Expected format : ".concat(DATE_FORMAT.toPattern()));
				return new BigDecimal(1);
			}
			return RedisRoEData.getRateOfExchange(fromCcy, toCcy,market,cutOffDate);
		}
		return RedisRoEData.getRateOfExchange(fromCcy, toCcy,market);
    }
	
	@GetMapping(value = "/updateBookingPrioritOps/{bookID}/{orderID}/{bookingStatus}")
    public String getBookingPriorityForOps(@PathVariable("bookID") String bookID,@PathVariable("orderID") String orderID,@PathVariable("bookingStatus") String bookingStatus) {
		return BookingPriority.getPriorityForOps(bookID, orderID, bookingStatus);
    }
	
}
