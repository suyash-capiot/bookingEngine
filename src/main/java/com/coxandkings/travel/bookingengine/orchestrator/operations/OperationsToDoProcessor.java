package com.coxandkings.travel.bookingengine.orchestrator.operations;

import java.time.ZonedDateTime;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.coxandkings.travel.bookingengine.config.MDMConfig;
import com.coxandkings.travel.bookingengine.config.OperationsShellConfig;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskName;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskPriority;
import com.coxandkings.travel.bookingengine.enums.ToDoTaskSubType;
import com.coxandkings.travel.bookingengine.userctx.MDMUtils;
import com.coxandkings.travel.bookingengine.userctx.UserContext;
import com.coxandkings.travel.bookingengine.utils.HTTPServiceConsumer;


public class OperationsToDoProcessor implements ToDoTaskConstants {
	
	private static final Logger logger = LogManager.getLogger(OperationsToDoProcessor.class);
	
	public static JSONObject callOperationTodo(ToDoTaskName taskName, ToDoTaskPriority  taskPriority, ToDoTaskSubType taskSubtype, String orderID, String bookID,JSONObject reqHdrJson,String remark) {
		
		JSONObject operationMessageJson= new JSONObject(new JSONTokener(OperationsShellConfig.getRequestJSONShell()));
		
		//Mandatory fields to filled are createdByUserId,productId,referenceId,taskFunctionalAreaId,
		//taskNameId,taskPriorityId,taskSubTypeId,taskTypeId
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		JSONObject clientContxt = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		
		operationMessageJson.put("createdByUserId", TODO_TASK_CREATEDBY_USERID);
		operationMessageJson.put("taskFunctionalAreaId", TODO_TASK_FUNCTIONAL_AREAID);
		operationMessageJson.put("taskNameId", taskName);
		operationMessageJson.put("taskPriorityId", taskPriority);
		operationMessageJson.put("taskSubTypeId",taskSubtype);
		operationMessageJson.put("taskTypeId", TODO_TASK_TYPE);
		operationMessageJson.put("productId", orderID);
		operationMessageJson.put("assignedBy", "");
		operationMessageJson.put("bookingRefId", bookID);
		operationMessageJson.put("clientCategoryId", usrCtx.getClientCategory());
		operationMessageJson.put("clientId", clientContxt.getString(JSON_PROP_CLIENTID));
		operationMessageJson.put("clientSubCategoryId", usrCtx.getClientSubCategory());
		operationMessageJson.put("clientTypeId", clientContxt.getString(JSON_PROP_CLIENTTYPE));
		operationMessageJson.put("companyId", usrCtx.getOrganizationHierarchy().getCompanyId());
		operationMessageJson.put("companyMarketId", usrCtx.getOrganizationHierarchy().getCompanyMarket());
		operationMessageJson.put("fileHandlerId", MDMConfig.getApiUser());
		operationMessageJson.put("id", "");
		operationMessageJson.put("mainTaskId", "");
		operationMessageJson.put("mainTaskStatusTriggerId", "");
		operationMessageJson.put("note", "");
		//Ops require a unique reference Id to accept a new Todo
		operationMessageJson.put("referenceId",ZonedDateTime.now());
		operationMessageJson.put("remark", remark);
		operationMessageJson.put("secondaryFileHandlerId", "");
		operationMessageJson.put("suggestedActions", "");
		operationMessageJson.put("taskGeneratedTypeId",TODO_TASK_GENERATED_TYPE);
		operationMessageJson.put("taskStatusId", "");
		operationMessageJson.put("taskSubTypeDesc", "");

		//We are not setting any time limit as of now, operations will decide
		operationMessageJson.put("dueOn",ZonedDateTime.now().plusDays(2));
	
		JSONObject opResJson = null;
		try {
			HashMap<String, String> httpHdrs = new HashMap<String, String>(OperationsShellConfig.getHttpHeaders());
			String token = MDMUtils.getTokenfromRedis();
			httpHdrs.put(HTTP_HEADER_AUTHORIZATION, String.format(httpHdrs.get(HTTP_HEADER_AUTHORIZATION), token));
			opResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_OPSTODO, OperationsShellConfig.getServiceURL(), httpHdrs, operationMessageJson);
			if(opResJson==null)
				throw new Exception("Null response received from Ops");
			if (logger.isInfoEnabled()) {
				logger.info(String.format("%s JSON Response = %s",TARGET_SYSTEM_OPSTODO, opResJson.toString()));
			}
		}
		catch (Exception x) {
			logger.warn("An exception was received when creating Operations ToDo task", x);
		}
		return opResJson;
	
		
	}

public static JSONObject callOperationTodo(ToDoTaskName taskName, ToDoTaskPriority  taskPriority, ToDoTaskSubType taskSubtype, String orderID, String bookID,JSONObject reqHdrJson, JSONObject reqBodyJson, String remark) {
		
		JSONObject operationMessageJson= new JSONObject(new JSONTokener(OperationsShellConfig.getRequestJSONShell()));
		
		//Mandatory fields to filled are createdByUserId,productId,referenceId,taskFunctionalAreaId,
		//taskNameId,taskPriorityId,taskSubTypeId,taskTypeId
		UserContext usrCtx = UserContext.getUserContextForSession(reqHdrJson);
		JSONObject clientContxt = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		
		operationMessageJson.put("createdByUserId", TODO_TASK_CREATEDBY_USERID);
		operationMessageJson.put("taskFunctionalAreaId", TODO_TASK_FUNCTIONAL_AREAID);
		operationMessageJson.put("taskNameId", taskName);
		operationMessageJson.put("taskPriorityId", taskPriority);
		operationMessageJson.put("taskSubTypeId",taskSubtype);
		operationMessageJson.put("taskTypeId", TODO_TASK_TYPE);
		operationMessageJson.put("productId", orderID);
		operationMessageJson.put("assignedBy", "");
		operationMessageJson.put("bookingRefId", bookID);
		operationMessageJson.put("clientCategoryId", usrCtx.getClientCategory());
		operationMessageJson.put("clientId", clientContxt.getString(JSON_PROP_CLIENTID));
		operationMessageJson.put("clientSubCategoryId", usrCtx.getClientSubCategory());
		operationMessageJson.put("clientTypeId", clientContxt.getString(JSON_PROP_CLIENTTYPE));
		operationMessageJson.put("companyId", usrCtx.getOrganizationHierarchy().getCompanyId());
		operationMessageJson.put("companyMarketId", usrCtx.getOrganizationHierarchy().getCompanyMarket());
		operationMessageJson.put("fileHandlerId", MDMConfig.getApiUser());
		operationMessageJson.put("id", "");
		operationMessageJson.put("mainTaskId", "");
		operationMessageJson.put("mainTaskStatusTriggerId", "");
		operationMessageJson.put("note", "");
		//Ops require a unique reference Id to accept a new Todo
		operationMessageJson.put("referenceId",ZonedDateTime.now());
		operationMessageJson.put("remark", remark);
		operationMessageJson.put("secondaryFileHandlerId", "");
		operationMessageJson.put("suggestedActions", "");
		operationMessageJson.put("taskGeneratedTypeId",TODO_TASK_GENERATED_TYPE);
		operationMessageJson.put("taskStatusId", "");
		operationMessageJson.put("taskSubTypeDesc", "");
		
		//As Discussed with Shivam and Pritish, put requestBody as it is. 18-10-10
		operationMessageJson.put("requestBody", reqBodyJson);
		
		//We are not setting any time limit as of now, operations will decide
		operationMessageJson.put("dueOn",ZonedDateTime.now().plusDays(2));
	
		JSONObject opResJson = null;
		try {
			HashMap<String, String> httpHdrs = new HashMap<String, String>(OperationsShellConfig.getHttpHeaders());
			String token = MDMUtils.getTokenfromRedis();
			httpHdrs.put(HTTP_HEADER_AUTHORIZATION, String.format(httpHdrs.get(HTTP_HEADER_AUTHORIZATION), token));
			opResJson = HTTPServiceConsumer.consumeJSONService(TARGET_SYSTEM_OPSTODO, OperationsShellConfig.getServiceURL(), httpHdrs, operationMessageJson);
			if(opResJson==null)
				throw new Exception("Null response received from Ops");
			if (logger.isInfoEnabled()) {
				logger.info(String.format("%s JSON Response = %s",TARGET_SYSTEM_OPSTODO, opResJson.toString()));
			}
		}
		catch (Exception x) {
			logger.warn("An exception was received when creating Operations ToDo task", x);
		}
		return opResJson;
	
		
	}


}
