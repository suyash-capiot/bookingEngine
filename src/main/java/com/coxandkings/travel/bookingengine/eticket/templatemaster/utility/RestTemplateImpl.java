package com.coxandkings.travel.bookingengine.eticket.templatemaster.utility;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RestTemplateImpl {

  /*
   * public BarterEntity callRESTAPI(String url, HttpMethod method, Object requestBody,
   * Class<BarterEntity> responseType) { RestTemplate restTemplate = new RestTemplate();
   * BarterEntity responseObject = restTemplate.getForObject(url, responseType); return
   * responseObject; }
   */

  public static <T> ResponseEntity<T> callRESTAPI(String url, HttpMethod method, Object requestBody,
      Class<T> responseType) throws Exception {
    RestTemplate restTemplate = new RestTemplate();
    HttpEntity<String> entity = null;
    if (method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT)) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      if (requestBody != null) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
          entity = new HttpEntity<String>(objectMapper.writeValueAsString(requestBody), headers);
        } catch (Exception ex) {
          throw ex;
        }
      }
    }
    return restTemplate.exchange(url, method, entity, responseType);
  }

}
