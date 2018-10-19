package com.coxandkings.travel.bookingengine.controller.login;

import java.io.InputStream;
import java.util.Map;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.coxandkings.travel.bookingengine.orchestrator.login.LoginProcessor;

@RestController
@RequestMapping("/LoginService/v1")
public class LoginController {
	
	@GetMapping(value = "/ping",produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> pingService(InputStream req) {
        return new ResponseEntity<String>("{\"operation\": \"ping\", \"status\": \"SUCCESS\"}", HttpStatus.OK);
    }
	

	@PostMapping(value = "/login",produces = MediaType.APPLICATION_JSON_VALUE,consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> login(InputStream req) throws Exception{
        JSONTokener jsonTok = new JSONTokener(req);
        JSONObject reqJson = new JSONObject(jsonTok);
        String res = LoginProcessor.process(reqJson);
        return new ResponseEntity<String>(res, HttpStatus.OK);
    }
	

	@GetMapping(value = "/login",produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> loginGet(@RequestParam Map<String, String> queryParameters) throws Exception{
        JSONObject res = LoginProcessor.processGetLogin(queryParameters.get("userID"),queryParameters.get("clientID"),queryParameters.get("password"));
       if(res.optString("errorCode").isEmpty())
        return new ResponseEntity<String>("success", HttpStatus.OK);
       else
    	 return new  ResponseEntity<String>("failure", HttpStatus.UNAUTHORIZED);
    }

}
