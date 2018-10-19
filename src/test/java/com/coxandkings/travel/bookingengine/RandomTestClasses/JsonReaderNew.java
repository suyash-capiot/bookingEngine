package com.coxandkings.travel.bookingengine.RandomTestClasses;

import java.io.FileReader;

//import com.coxandkings.travel.bookingengine.utils.JSONUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
//import com.google.gson.Gson;

public class JsonReaderNew {
    JsonNode requestJson;
    Object responseObj;
//    private static final Gson gson = new Gson();
//public   <T> Object readJson(String filename, Class<T> class1) {
//   
//try {
//	 ObjectMapper mapper = new ObjectMapper();
//	 
//String filepath="src/main/resources/"+filename+".json";
//	 
//	 
//	 requestJson = mapper.readTree(new FileReader(filepath));
//	 System.out.println(requestJson);
//	 
//	 responseObj =   JSONUtils.converttoJavaObj(requestJson.toString(), class1);
//    		  
//        
//    return responseObj;
//    } catch (Exception e) {
//        e.printStackTrace();
//    }
//return responseObj;
//
//
//}		
		

/*public static void main(String args[]) {
	
	JsonReaderNew jsonread=new JsonReaderNew();
	SIResponse bookingEngineSearchResp=(SIResponse) jsonread.readJson("ResponseSI",SIResponse.class);
	
	
	//jsonread.storeInRedis(request);
	
}*/



		/*private  void storeInRedis(SearchResponse request) {
			
			Jedis jedis =new Jedis("10.24.2.248",6379);
			jedis.connect();
			jedis.select(1);
			jedis.set("priceRequest", request.toString());
			System.out.println(jedis.get("priceRequest"));
			
			
		}*/
}



