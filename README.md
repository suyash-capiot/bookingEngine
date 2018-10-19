# Quick Start
```
git clone http://10.18.1.142/cnk-operations/bookingengine.git
cd  bookingengine
gradle build
gradle clean bootrun
Access [getLuv API](http://localhost:9090/clients/getLuv) on browser , you should get "Luv" as a String response. You are all set to work .
```


# Persistence
By default, the project uses [the H2 in-memory DB](http://www.h2database.com/html/main.html) .

Link to access the H2DB - http://localhost:9090/h2Console
Properties can be configured in applicaiton.properties
# H2
spring.h2.console.enabled=true
spring.h2.console.path=/h2Console

If you want to switch to - for example - PostGres - you'll need to specify a different property on startup:

And of course, if you are going to use PostGres, you'llneed to run a PostGres instance locally/remotely and you'll need to either change the default credentials here, or create the following user/password in your local/remote installation


# Technology Stack
The project uses the following technologies: <br/>
- **web/REST**: [Spring](https://spring.io/guides/gs/spring-boot/)  <br/>
- **Json**: [GSON](https://github.com/google/gson) <br/>
- **persistence**: [Spring Data JPA](http://www.springsource.org/spring-data/jpa) and [Hibernate](http://www.hibernate.org/) <br/>
- **persistence providers**: H2
- **testing**: [junit](http://www.junit.org/)<br/>


# Swagger Links to access
-To verify that Springfox is working, you can visit the following URL in your browser:

- **Swagger api-docs**: [api-docs] (http://localhost:9090/v2/api-docs) <br/>

The result is a JSON response with a large number of key-value pairs, which is not very human-readable. Fortunately, Swagger provides Swagger UI for this purpose.
- **Swagger UI**: [Swagger UI :Api console ] (http://localhost:9090/swagger-ui.html#/) for this project <br/>

You can access the apis from the above link , once the server is up and running .

# Testing
- command to run : gradle clean test
- A report is generated at the following location - /bookingengine/build/reports/tests/test/index.html . Access the file on any browser .
- **TestReport**: [Test report ] - [./bookingengine/build/reports/tests/test/index.html]

#Filebeat
A sample file has been added in the project resources  to pick up logs from a specific location and output those logs to the Elasticsearch

#ElasticSearch indexes
 curl 'localhost:9200/_cat/indices?v' to access the elasticsearch indexes