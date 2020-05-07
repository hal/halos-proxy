#!/bin/sh

# Registers three WildFly instances

curl --request POST --url http://localhost:8080/v1/instance/ \
--header 'content-type: application/json' \
--data '{"name":"wf0","host":"localhost","port":9990,"username":"admin","password":"admin"}'
curl --request POST --url http://localhost:8080/v1/instance/ \
--header 'content-type: application/json' \
--data '{"name":"wf1","host":"localhost","port":9991,"username":"admin","password":"admin"}'
curl --request POST --url http://localhost:8080/v1/instance/ \
--header 'content-type: application/json' \
--data '{"name":"wf2","host":"localhost","port":9992,"username":"admin","password":"admin"}'
