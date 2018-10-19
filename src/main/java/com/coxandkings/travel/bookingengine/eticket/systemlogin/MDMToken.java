package com.coxandkings.travel.bookingengine.eticket.systemlogin;

import java.io.Serializable;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import com.coxandkings.travel.bookingengine.eticket.exceptions.FinanceException;

@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Component(value = "mDMToken")
public class MDMToken implements Serializable {

  private static final Logger logger = Logger.getLogger(MDMToken.class);

  // Making static as in getToken() returns stale value
  private static String token;

  private static ZonedDateTime tokenExpiryTimestamp;

  private boolean tokenExpired;

  public String getToken() {
    logger.info("*** Entering MDMToken.getToken() method *** \n");

    return token;
  }

  public void setToken(String newToken) {
    logger.info("*** Entering MDMToken.setToken() method *** \n");
    token = newToken;
  }

  public ZonedDateTime getTokenExpiryTimestamp() {
    return tokenExpiryTimestamp;
  }

  public void setTokenExpiryTimestamp(ZonedDateTime tokenExpiryTimestamp) {
    this.tokenExpiryTimestamp = tokenExpiryTimestamp;
  }

  public boolean isTokenExpired() {
    logger.info("*** Entering MDMToken.isTokenExpired() method ***");
    // Current system date time; Convert to UTC; Compare with tokenExpiryTimestamp
    ZonedDateTime currentDateTime = ZonedDateTime.now();
    ZonedDateTime utcDateTime = currentDateTime.withZoneSameInstant(ZoneOffset.UTC);

    logger.info("UTC Date Time to String: " + utcDateTime.toString());
    logger.info("MDM Token Expiry TimeStamp to String: " + tokenExpiryTimestamp.toString());

    if (utcDateTime.isAfter(tokenExpiryTimestamp)) {
      tokenExpired = true;
      logger.info("*** MDM Token Expired ***");
    }
    return tokenExpired;
  }

  public void setTokenExpired(boolean tokenExpired) {
    this.tokenExpired = tokenExpired;
  }
}
