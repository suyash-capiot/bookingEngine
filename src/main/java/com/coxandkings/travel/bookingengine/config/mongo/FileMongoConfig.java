package com.coxandkings.travel.bookingengine.config.mongo;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class FileMongoConfig extends AbstractMongoConfig {

	FileMongoConfig() throws FileNotFoundException, IOException {
		String cfgFile = System.getProperty(MONGO_CONFIG_FILE_PROP);
		System.out.println(String.format("FileMongoConfig: cfgFile=<%s>", cfgFile));
		if (cfgFile == null || cfgFile.isEmpty()) {
			throw new IllegalArgumentException(String.format("Configuration file for mongoDB not specified as system property %s", MONGO_CONFIG_FILE_PROP));
		}
		
		Properties props = new Properties();
		props.load( new FileReader(cfgFile));
		
		mMongoURL = props.getProperty(MONGO_URL_PROP);
		mMongoUser = props.getProperty(MONGO_USER_PROP);
		mMongoPwd = props.getProperty(MONGO_PASSWORD_PROP);
		mMongoDB = props.getProperty(MONGO_DATABASE_PROP);
		formatMongoURI();
		System.out.println(String.format("FileMongoConfig: mMongoURI=<%s>", mMongoURI));
	}
	
}
