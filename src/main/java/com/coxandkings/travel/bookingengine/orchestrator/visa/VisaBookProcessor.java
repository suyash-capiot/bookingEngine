package com.coxandkings.travel.bookingengine.orchestrator.visa;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.coxandkings.travel.bookingengine.config.ServiceConfig;
import com.coxandkings.travel.bookingengine.config.cruise.CruiseConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.orchestrator.air.KafkaBookProducer;
import com.coxandkings.travel.bookingengine.orchestrator.operations.OperationsToDoProcessor;
import com.coxandkings.travel.bookingengine.userctx.UserContextV2;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;

public class VisaBookProcessor implements VisaConstants{
	
	
	public static String process(JSONObject reqJson) throws Exception{
		
		KafkaBookProducer bookProducer = new KafkaBookProducer();
		JSONObject kafkaMsgJson = new JSONObject();
		JSONObject reqHdrJson=null;	JSONObject reqBodyJson=null;
		
		try {
			TrackingContext.setTrackingContext(reqJson);
			
			reqHdrJson = reqJson.getJSONObject("requestHeader");
			reqBodyJson = reqJson.getJSONObject("requestBody");
			
			//kafkaMsgJson = reqJson;
			kafkaMsgJson=new JSONObject(new JSONTokener(reqJson.toString()));
			bookProducer.runProducer(1, kafkaMsgJson);
			
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("status", "SUCCESS");
			
			OperationsToDoProcessor.callOperationTodo(ToDoTaskName.AMEND, ToDoTaskPriority.MEDIUM,ToDoTaskSubType.ON_REQUEST, "", reqBodyJson.getString(JSON_PROP_BOOKID),reqHdrJson,"");
			
			reqBodyJson.put("statusDetails", jsonObject);
			return reqJson.toString();
			
		} catch (Exception e) {
			// TODO: handle exception
			throw e;
		}
		
		
	}

}
