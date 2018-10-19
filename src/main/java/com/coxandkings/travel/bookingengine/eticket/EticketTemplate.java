package com.coxandkings.travel.bookingengine.eticket;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.coxandkings.travel.bookingengine.config.ETicketConfig;
import com.coxandkings.travel.bookingengine.eticket.productImplementaion.AccoImplementation;
import com.coxandkings.travel.bookingengine.eticket.productImplementaion.AirImplementation;
import com.coxandkings.travel.bookingengine.eticket.productImplementaion.AirImplementationForMultipleProduct;
import com.coxandkings.travel.bookingengine.eticket.productImplementaion.BusImplementation;
import com.coxandkings.travel.bookingengine.eticket.productImplementaion.BusImplementationForMultipleProduct;
import com.coxandkings.travel.bookingengine.eticket.productImplementaion.RailImplementation;

@Component
public class EticketTemplate {

	private static final Logger logger = Logger.getLogger(EticketTemplate.class);
	private String bookingEngineURL = ETicketConfig.getmDBServiceURL();

	public JSONObject retrieveByBookingID(String id) {
		try {
			String urlToLoad = String.format(bookingEngineURL, id);

			logger.info(String.format("Calling Retrieve Booking API for URL:%s ", urlToLoad));

			RestTemplate restTemplate = new RestTemplate();
			JSONObject resJson = new JSONObject(restTemplate.getForObject(urlToLoad, String.class));
			logger.info("Retrieve response :" + resJson);
			return resJson;

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String checkProduct(String id) {
		ETicketConfig.loadConfig();
		logger.info("In check product method");
		String uniqueId = id;

		EticketTemplate obj = new EticketTemplate();
		JSONObject retrivebooking = obj.retrieveByBookingID(id);
		JSONArray productsarray = new JSONArray();
		try {
			productsarray = retrivebooking.getJSONObject("responseBody").getJSONArray("products");
		} catch (JSONException e) {
			e.printStackTrace();
		}

		logger.info("count of product array is " + productsarray.length());
		List products = new ArrayList<>();
		for (int i = 0; i <= productsarray.length() - 1; i++) {
			products.add(productsarray.getJSONObject(i).get("productSubCategory"));
			logger.info(String.format("Product SubCategory for product %s is %s", i,
					productsarray.getJSONObject(i).get("productSubCategory")));
			// products.add(productsarray.getJSONObject(i).get("productCategory"));
		}
		// This code is added, when checkProcut method will received the Product name
		// also.

		/*String product = "Flight";
		switch (product) {

		case "Flight":
			AirImplementationForMultipleProduct air = new AirImplementationForMultipleProduct();
			air.generateTemplate(retrivebooking, uniqueId);
			break;

		case "Bus":
			logger.info("in BusImplementationForMultipleProduct");
			BusImplementationForMultipleProduct bus = new BusImplementationForMultipleProduct();
			bus.generateTemplate(retrivebooking, uniqueId);
			break;

		case "Rail":
			RailImplementation rail = new RailImplementation();
			rail.generateTemplate(retrivebooking);
			break;

		case "Accommodation":
			AccoImplementation acco = new AccoImplementation();
			acco.generateTemplate(retrivebooking, uniqueId);
			break;

		default:
			System.out.println("Invalid Product");
			break;

		}*/

		if (products.contains("Accommodation")) {
			AccoImplementation acco = new AccoImplementation();
			acco.generateTemplate(retrivebooking, uniqueId);
		} else if (products.contains("Flight")) {
			AirImplementationForMultipleProduct air = new AirImplementationForMultipleProduct();
			air.generateTemplate(retrivebooking, uniqueId);
		} else if (products.contains("Bus")) {
			/*
			 * BusImplementation bus= new BusImplementation(); //
			 * bus.multipleProducts(retrivebooking,uniqueId);
			 * bus.generateTemplate(retrivebooking,uniqueId);
			 */
			logger.info("in BusImplementationForMultipleProduct");
			BusImplementationForMultipleProduct bus = new BusImplementationForMultipleProduct();
			bus.generateTemplate(retrivebooking, uniqueId);

		} else if (products.contains("Rail")) {
			System.out.println("In rail");
			RailImplementation rail = new RailImplementation();
			rail.generateTemplate(retrivebooking);
		}
		return null;
	}

}
