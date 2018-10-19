package com.coxandkings.travel.bookingengine.aspect;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.coxandkings.travel.bookingengine.config.PaymentConfig;
import com.coxandkings.travel.bookingengine.config.RedisConfig;
import com.coxandkings.travel.bookingengine.config.mongo.MongoCommonConfig;
import com.coxandkings.travel.bookingengine.exception.InternalProcessingException;
import com.coxandkings.travel.bookingengine.exception.RequestProcessingException;
import com.coxandkings.travel.bookingengine.exception.ValidationException;
import com.coxandkings.travel.bookingengine.orchestrator.atom.PaymentVerificationProcessor;
import com.coxandkings.travel.bookingengine.utils.Constants;
import com.coxandkings.travel.bookingengine.utils.ServletContext;
import com.coxandkings.travel.bookingengine.utils.TrackingContext;
import com.coxandkings.travel.bookingengine.utils.Utils;

import redis.clients.jedis.Jedis;

@Component
@Aspect
public class RestControllerAspect implements Constants{

	private static final Logger logger = LogManager.getLogger();
	private static final String REDIS_KEY_TXNID = "be:common:txnId's";

	@Pointcut("execution(* com.coxandkings.travel.bookingengine.controller.*.*.*(org.json.JSONObject))")
	public void controllerPointcut() {};

	@Pointcut("execution(* com.coxandkings.travel.bookingengine.controller.common.*.*(..))")
	public void commonCntlrPointcut() {};

	@SuppressWarnings("unchecked")
	@Around("controllerPointcut() && !commonCntlrPointcut()")//exclude common controller package
	public ResponseEntity<String> aroundRestCall(ProceedingJoinPoint jp) throws Throwable{
		try {
			long start = System.currentTimeMillis();
			HttpServletRequest request =((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest();
			HttpServletResponse response =((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getResponse();
			ServletContext.setServletContext(request, response);

			JSONObject reqJson = null,reqHdrJson=null;
			String txnId = null,operation = null;
			try {
				JSONTokener jsonTok = new JSONTokener(request.getInputStream());
				reqJson = new JSONObject(jsonTok);
				reqHdrJson = reqJson.getJSONObject(JSON_PROP_REQHEADER);
				operation = ServletContext.getServletContext().getRestTrackingParam(ServletContext.RESTPROP_OPERATION);
				txnId = MongoCommonConfig.getOperationListForTxnIdCreation().contains(operation)?createTxnIdAndPushToRedis(reqHdrJson):getTxnIdFromRedis(reqHdrJson);
				reqHdrJson.put(JSON_PROP_TRANSACTID,txnId);
				TrackingContext.setTrackingContext(reqJson);
			}
			catch(Exception x) {
				return new ResponseEntity<String>((new RequestProcessingException(x)).toString("TRLERR400"), HttpStatus.BAD_REQUEST);
			}
			
			Object[] args = jp.getArgs();
			args[0] = reqJson;

			logger.info(String.format("%s_RQ = %s",TARGET_SYSTEM_BKNGENG,reqJson.toString()));
			
			if(Utils.isStringNullOrEmpty(txnId)) {
				RequestProcessingException txIdRqEx = new RequestProcessingException(new Throwable("Invalid Transaction Id"));
				logger.error(txIdRqEx.getMessage());
		    	return new ResponseEntity<String>(txIdRqEx.toString("TRLERR400"), HttpStatus.BAD_REQUEST);
		    }
			
			//TODO:should this be as a part of configuration?
			if("book".equalsIgnoreCase(operation)&&PaymentConfig.isEnablePaymentVerify()) {
				boolean paymentVerify = PaymentVerificationProcessor.paymentVerify(reqJson.getJSONObject(JSON_PROP_REQBODY).getJSONArray("paymentInfo"),reqHdrJson.getJSONObject("clientContext").getString("clientID"),reqJson.getJSONObject(JSON_PROP_REQBODY).getString("bookID"));
				if(paymentVerify==false)
					return new ResponseEntity<String>((new RequestProcessingException( new Exception("Payment verification failed for the requested booking"))).toString("TRLERR600"), HttpStatus.BAD_REQUEST);
			}

			Object output =  (ResponseEntity<String>) jp.proceed(args);
			
			long elapsedTime = System.currentTimeMillis() - start;
			logger.info(String.format(String.format("%s%s_RS = %s",operation,"ResTime",elapsedTime)));
			return (ResponseEntity<String>) output;
		}
		catch (InternalProcessingException intx) {
			logger.error(intx.getMessage());
			return new ResponseEntity<String>(intx.toString("TRLERR500"),HttpStatus.INTERNAL_SERVER_ERROR);
		}
		catch (RequestProcessingException reqx) {
			logger.error(reqx.getMessage());
			return new ResponseEntity<String>(reqx.toString("TRLERR400"), HttpStatus.BAD_REQUEST);
		}
		catch (ValidationException valx) {
			logger.error(valx.getMessage());
			return new ResponseEntity<String>(valx.toString(), HttpStatus.BAD_REQUEST);
		}
		catch (Exception x) {
			logger.error(x.getMessage());
			return new ResponseEntity<String>((new InternalProcessingException(x)).toString("TRLERR500"), HttpStatus.BAD_REQUEST);
		}
	}

	@AfterReturning(pointcut = "controllerPointcut() && !commonCntlrPointcut()",returning = "response")
	public void afterReturningResponse(JoinPoint jp,ResponseEntity<String> response) {
		logger.info(String.format("%s_RS = %s",TARGET_SYSTEM_BKNGENG,response.getBody()));
	}

	private static String createTxnIdAndPushToRedis(JSONObject reqHdrJson) {
		String txnIdRedisKey = getRedisKeyForTxnId(reqHdrJson);
		
		//create TXN id as per mail on 4/24/18
		JSONObject clntCtx = reqHdrJson.getJSONObject(JSON_PROP_CLIENTCONTEXT);
		String txnId = String.format("%s%s%s",clntCtx.optString(JSON_PROP_CLIENTTYPE),clntCtx.optString(JSON_PROP_CLIENTID),
				new SimpleDateFormat("yyMMddHHmmssSSS").format(new Date()));
		
		try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool()){
			redisConn.set(txnIdRedisKey, txnId);
			//TODO:should expiry be set?
		}
		catch(Exception x) {
			return "";
		}
		
		return txnId;
		
	}
	
	private static String getTxnIdFromRedis(JSONObject reqHdrJson) {
		String txnId = "";
		try(Jedis redisConn = RedisConfig.getRedisConnectionFromPool()){
			txnId = redisConn.get(getRedisKeyForTxnId(reqHdrJson));
		}
		catch(Exception e) {}
		return txnId==null?"":txnId;
	}


	private static String getRedisKeyForTxnId(JSONObject reqHdrJson) {
		return String.format("%s:%s", REDIS_KEY_TXNID,reqHdrJson.optString(JSON_PROP_SESSIONID));
	}

}
