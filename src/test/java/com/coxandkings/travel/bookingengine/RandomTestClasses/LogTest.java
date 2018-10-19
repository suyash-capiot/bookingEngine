package com.coxandkings.travel.bookingengine.RandomTestClasses;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LogTest {

    private static final Logger logger = LogManager.getLogger(LogTest.class);
    public static void main(String[] args) {
        logger.info("A sample Test");
    }
}