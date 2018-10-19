package com.coxandkings.travel.bookingengine.eticket.util;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class MessageUtil {
  private Locale locale;

  @Autowired
  private MessageSource messageSource;

  public MessageUtil() {
    this.locale = Locale.US;
  }

  public void setLocale(Locale locale) {
    this.locale = locale;
  }

  public String getMessage(String key, Object... params) {
    return messageSource.getMessage(key, params, locale);
  }
}
