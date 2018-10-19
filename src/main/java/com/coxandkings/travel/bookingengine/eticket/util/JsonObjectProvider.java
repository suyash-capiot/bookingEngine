package com.coxandkings.travel.bookingengine.eticket.util;

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

@Component
public class JsonObjectProvider<AnEntity> {

  private static Logger logger = Logger.getLogger(JsonObjectProvider.class);

  public String getAttributeValue(String jsonAsString, String jsonPathExpression) {
    logger.debug("*** Entering JsonObjectProvider.getAttributeValue() method ***");
    String jsonAttributeValue = null;
    try {
      jsonAttributeValue = (String) JsonPath.parse(jsonAsString).read(jsonPathExpression);
    } catch (Exception e) {
      logger.error(
          "Error occurred while parsing JSON string.  Please check JSON Path filter expression", e);
    }
    logger.debug("*** Exiting JsonObjectProvider.getAttributeValue() method ***");
    return jsonAttributeValue;
  }

  /**
   * The purpose of this method is to parse a JSON string using JSON Path, return a POJO based on
   * input type. This method returns null in case the filter/json path expression fails. Note: If
   * the child JSON structure that you expect is of type Map<String,String>, then then this method
   * will convert the Map to desired object instance
   *
   * @param jsonAsString - the input JSON data which contains data to be extracted
   * @param jsonPathExpression - the filter expression used by JSONPath APIs
   * @param desiredClassType - The output POJO class type
   * @return - Returns an object instance based on the class type (desiredType)
   */
  public AnEntity getChildObject(String jsonAsString, String jsonPathExpression,
      Class desiredClassType) {
    logger.debug("*** Entering JsonObjectProvider.getChildObject() method ***");
    AnEntity childObject = null;
    try {
      // childObject = (AnEntity) JsonPath.parse( jsonAsString ).read( jsonPathExpression,
      // desiredClassType );
      Object resultantObject = JsonPath.parse(jsonAsString).read(jsonPathExpression);
      if (resultantObject != null) {
        ObjectMapper mapper = new ObjectMapper();
        childObject = (AnEntity) mapper.convertValue(resultantObject, desiredClassType);

        System.out.println(childObject.toString());
      }
    } catch (Exception e) {
      logger.error(
          "Error occurred while parsing JSON string.  Please check JSON Path filter expression", e);
    }
    logger.debug("*** Exiting JsonObjectProvider.getChildObject() method ***");
    return childObject;
  }

  /**
   * The purpose of this method is to parse the Json using JSON Path and return the children data as
   * a List of a specific type. If the jsonPathExpression is wrong, this method returns null;
   *
   * @param jsonAsString - the JSON to be parsed
   * @param jsonPathExpression - the JSON Path expresson to fetch the children data
   * @param collectionEntityType - the Entity type to be converted to in the collection
   * @return - returns List of elements of type collectionEntityType
   */
  public List<AnEntity> getChildrenCollection(String jsonAsString, String jsonPathExpression,
      Class collectionEntityType) {
    logger.debug("*** Entering JsonObjectProvider.getChildrenCollection() method ***");
    ArrayList childCollection = null;

    try {
      List resultList = (List) JsonPath.parse(jsonAsString).read(jsonPathExpression, List.class);
      if (resultList != null) {
        ObjectMapper mapper = new ObjectMapper();
        childCollection = new ArrayList();
        for (Object anEntity : resultList) {
          childCollection.add(mapper.convertValue(anEntity, collectionEntityType));
        }
      }
    } catch (Exception e) {
      logger.error(
          "Error occurred while parsing JSON string.  Please check JSON Path filter expression", e);
    }

    logger.debug("*** Exiting JsonObjectProvider.getChildrenCollection() method ***");
    return childCollection;
  }

  public String getChildJSON(String jsonAsString, String jsonPathExpression) {
    logger.debug("*** Entering JsonObjectProvider.getChildJSON() method ***");
    String childObject = null;
    try {
      childObject = (String) JsonPath.parse(jsonAsString).read(jsonPathExpression);
    } catch (Exception e) {
      logger.error(
          "Error occurred while parsing JSON string.  Please check JSON Path filter expression", e);
    }
    logger.debug("*** Exiting JsonObjectProvider.getChildJSON() method ***");
    return childObject;
  }
}
