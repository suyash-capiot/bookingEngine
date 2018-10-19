package com.coxandkings.travel.bookingengine.eticket.systemlogin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.coxandkings.travel.bookingengine.config.ETicketConfig;
import com.coxandkings.travel.bookingengine.eticket.exceptions.FinanceException;
import com.coxandkings.travel.bookingengine.eticket.util.JsonObjectProvider;

/**
 * MDMDataSource class is used for system login and logout.When application
 * start it will login and hold token and give back to calling method. When
 * application stop automatically logout method will be called
 */

@Component(value = "mDMDataSource")
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MDMDataSource {
	private static final Logger logger = Logger.getLogger(MDMDataSource.class);

	private static MDMToken mdmToken;

	@Autowired
	ServletContext servletContext;

	@Autowired
	private JsonObjectProvider<?> jsonObjectProvider;

	private String systemUsername = ETicketConfig.getmSystemUsername();

	private String systemUserPassword = ETicketConfig.getmSystemUserPassword();

	private String loginUrl = ETicketConfig.getmLoginUrl();

	private String logoutUrl = ETicketConfig.getmLogoutUrl();

	private String authTokenPrefix = ETicketConfig.getmAuthTokenPrefix().trim() + " ";

	@PostConstruct
	private void loginToMDM() throws FinanceException {
		/*
		 * 1) connect to MDM 2) Create MDMToken, assign to mdmToken 3) patch (to be
		 * removed later) - Write token to a text file to
		 * System.getProperty("java.io.tmpdir") - if MDM returns user already logged in,
		 * then read the file, call logout( data_from_file ) method
		 */

		mdmToken = new MDMToken();
		RestTemplate restTemplate = new RestTemplate();
		logger.info("Before Login to MDM as: " + systemUsername);
		UserLogin userLogin = new UserLogin(systemUsername, systemUserPassword);

		String loginJson = null;
		try {
			loginJson = restTemplate.postForObject(loginUrl + "?forceLogin=true", userLogin, String.class);
		} catch (Exception e) {
			logger.error(e);
			logger.info("Login to MDM failed - proceeding to logout using existing token");
			mdmToken.setToken(readToken());
			try {
				// logout();
				logger.info("Logout successfully using existing Token - preparing to Login again");
				loginJson = restTemplate.postForObject(loginUrl + "?forceLogin=true", userLogin, String.class);
				logger.info("Login sucessful (after logout with existing token)");
			} catch (Exception exc) {
				logger.error("Unable to login. May be user already logged in. ", exc);
				throw new FinanceException("Unable to login. May be user already logged in");
			}
		}

		if (null == loginJson) {
			throw new FinanceException("Unable to login");
		}

		String token = authTokenPrefix + jsonObjectProvider.getAttributeValue(loginJson, "$.token");
		Long expireUtcTime = (Long) jsonObjectProvider.getChildObject(loginJson, "$.expireIn", Long.class);
		// logger.info( "The Epoc Millis from MDM is: " + expireUtcTime );
		logger.info("Token " + token);

		// ToDo: remove after mmd provide system login
		ZoneId zoneId = ZoneId.of("UTC").normalized();
		ZonedDateTime expireTime = Instant.ofEpochMilli((expireUtcTime)).atZone(zoneId);
		mdmToken.setToken(token);
		mdmToken.setTokenExpiryTimestamp(expireTime);
		mdmToken.setTokenExpired(false);

		// ToDo: remove after mmd provide system login
		writeToken(token);
		logger.info("Login is successful ***");
	}

	private void writeToken(String token) throws FinanceException {

		/*
		 * String fileName = (System.getProperty("java.io.tmpdir")) + "MdmToken.txt";
		 * try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));) {
		 * writer.write(token); writer.close(); } catch (IOException e) { throw new
		 * FinanceException("Not able write token in file " + fileName); }
		 */
		servletContext.setAttribute("token", token);

	}

	private String readToken() {
		/*
		 * String fileName = (System.getProperty("java.io.tmpdir")) + "MdmToken.txt";
		 * String line = null; try (BufferedReader reader = new BufferedReader(new
		 * FileReader(fileName));) { line = reader.readLine(); reader.close(); } catch
		 * (IOException e) { logger.error("While Reading mdmToken file: " + e); }
		 */
		if (servletContext.getAttribute("token") != null) {
			return servletContext.getAttribute("token").toString();
		} else {
			return null;
		}
	}

	public MDMToken getToken() throws FinanceException {
		if (mdmToken.isTokenExpired()) {
			try {
				loginToMDM();
			} catch (FinanceException e) {
				throw new FinanceException("Not able to login to Mdm");
			}
		}
		return mdmToken;
	}

	// @PreDestroy
	// public void logout() throws FinanceException {
	//
	// logger.info("***Logout***");
	//
	// RestTemplate restTemplate = new RestTemplate();
	//
	// HttpHeaders httpHeaders = new HttpHeaders();
	// httpHeaders.set("Authorization", mdmToken.getToken());
	// HttpEntity httpEntity;
	// UserLogin userLogin = new UserLogin(systemUsername, systemUserPassword);
	// httpEntity = new HttpEntity(userLogin, httpHeaders);
	// try {
	// restTemplate.exchange(logoutUrl, HttpMethod.POST, httpEntity, String.class);
	// logger.info("System logout Successfully");
	// } catch (Exception e) {
	// logger.error("Not able to logout", e);
	//
	// throw new FinanceException("Not able to logout");
	// }
	//
	// }
}
