
package com.coxandkings.travel.bookingengine.eticket.templatemaster.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"lookup", "commands"})
public class TemplateRequestResource {

  @JsonProperty("lookup")
  private String lookup;
  @JsonProperty("commands")
  private List<Command> command;// = null;

  public TemplateRequestResource(TemplateInfo templateInfo, List<DynamicAttributes> attributes,
      String lookUpValue, String outIdentifier, Boolean entryPoint, String returnObject,
      String userId, String transactionId) {
    this.lookup = lookUpValue;
    Command insert;
    Command fireAllRules;
    Command getForObjects;
    insert = new Command();
    insert.setInsert(new Insert(templateInfo, attributes, outIdentifier, entryPoint, returnObject,
        userId, transactionId));
    fireAllRules = new Command();
    fireAllRules.setFireAllRules(new FireAllRules());
    getForObjects = new Command();
    getForObjects.setGetObjects(new GetObjects());
    this.command = new ArrayList<>();
    this.command.add(insert);
    this.command.add(fireAllRules);
    this.command.add(getForObjects);

  }

  @JsonProperty("lookup")
  public String getLookup() {
    return lookup;
  }

  @JsonProperty("lookup")
  public void setLookup(String lookup) {
    this.lookup = lookup;
  }

  public List<Command> getCommand() {
    return command;
  }

  public void setCommand(List<Command> command) {
    this.command = command;
  }
}
