Repository related to development for REST Api prototype model

In this project, we will develop a REST Api to parse a JSON schema model divided into three parts:

Prototype Part 1st:
Develop a Spring Boot based REST Api to parse a given sample JSON schema.
Save the JSON schema in a redis key value store.
Demonstrate the use of operations like GET, POST and DELETE for the first prototype demo.


Prototype Part 2:
Regress on your model and perform additional operations like PUT and PATCH.
Secure the REST Api with a security protocol like JWT or OAuth2.


Prototype Part 3:
Adding Elasticsearch capabilities
Using RedisSMQ for REST API queueing



Tech-Stack Used:
Java
Maven
Redis Server
Elasticsearch and Kibana(Local or cloud based)
RabbitMQ
Run as Spring Boot Application.

Querying Elasticsearch
Run both the application i.e FinalProject and Consumer Message Queue(CMQ). CMQ application will create the indexes.
Run POST query from Postman
Run custom search queries as per your use case
(Optional) For testing purpose - Inorder to test the indexes separately, Run the PUT query in Testing-ElasticSearchQueries on Kibana. This will create an index in elasticsearch



How to Use:
1. Start the services, including redis, elastic search, kibana;
2. Start the SpringBoot applitions, the restful api service and elastic search service;
3. Test the work flow, through Postman and Kibana Dev Tools
a.Postman
E-tag test
Token test, choose Auth 2.0 and send request to Google Token Server; then, use the id_token to replace the evn var {google-id_token}
CRUD/ GET/ POST/ PATCH/ PUT/ DELETE
b. Elastic Search/ Kibana
CRUD
Get object with specific conditions
After this, Run the start.sh to start all the necessary services
