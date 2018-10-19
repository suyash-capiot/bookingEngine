package com.coxandkings.travel.bookingengine.RandomTestClasses;

//import com.coxandkings.travel.bookingengine.resource.MDMResources.SearchClient;
//import com.coxandkings.travel.bookingengine.utils.JSONUtils;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;

public class TestMDMAPI {

    private static  String uri = "http://10.24.2.5:10050/client/v1/mgClientType?filter={filter}";
   // private static  String filterJSON = "{_id}" ;

//    public static void main (String args[]){
//        RestTemplate restTemplate = new RestTemplate();
//
//        SearchClient searchClient = restTemplate.getForObject(uri+filterJSON, SearchClient.class, "B2B");
//
//        //Just Printing the object received
//
//        Gson gson = new Gson();
//        String json = gson.toJson(searchClient);
//        System.out.println(json);
//
//    }

    public static void main(String[] args) throws IOException {
//        Map<String, String> vars = new HashMap<String, String>();
//        vars.put("clientStructure.clientEntityType", "B2B");
//        SearchClient result = RestUtils.getMethod(uri,SearchClient.class,vars);
//        String abc = JSONUtils.convertToJSONString(result);
//        System.out.println(abc);
        fetchByID();
    }

    public static void fetchByID() throws IOException {
        String filter = "{\"_id\":\"CLIENTTYPE14\"}";

        RestTemplate restTemplate = new RestTemplate();

      //  SearchClient result = restTemplate.getForObject(uri ,SearchClient.class,filter);
//        String abc = JSONUtils.convertToJSONString(result);
//        System.out.println(abc);
    }

}
