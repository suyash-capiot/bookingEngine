package com.coxandkings.travel.bookingengine.RandomTestClasses;

import redis.clients.jedis.Jedis;

public class TestRedisConnectivity {
    public static void main(String[] args) {
        try {
            Jedis jedis = new Jedis("10.24.2.248", 6379);
            
            jedis.connect();
               // jedis.auth("foobar");
            System.out.println("Server is running: "+jedis.ping());
        }catch (Exception e ){
            e.printStackTrace();
        }
    }
}