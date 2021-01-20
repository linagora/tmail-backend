# openpaas-james Distributed LDAP server

Distributed version of James mail server with LDAP support relying on:

* Cassandra version 3.11.3
* S3 API-like object storage. Using here Zenko Cloudserver version 8.2.6
* ElasticSearch version 6.3.2
* RabbitMQ version 3.8.3
* OpenLdap version latest
* Tika version 1.24 (optional)

## Build

To build the distributed server:

```
mvn clean install
mvn compile com.google.cloud.tools:jib-maven-plugin:2.7.0:dockerBuild
```

## Run

### Run manually

Firstly, create your own user network on Docker for the James environment:

```
docker network create --driver bridge emaily
```

You can then start in that newly created network all the other softwares the distributed James is relying on:

```
docker run -d --network emaily --name=cassandra cassandra:3.11.3

docker run -d --network emaily --name=rabbitmq rabbitmq:3.8.3-management

docker run -d --network emaily --env 'REMOTE_MANAGEMENT_DISABLE=1' --env 'SCALITY_ACCESS_KEY_ID=accessKey1' --env 'SCALITY_SECRET_ACCESS_KEY=secretKey1' --name=s3.docker.test zenko/cloudserver:8.2.6

docker run -d --network emaily --name=elasticsearch --env 'discovery.type=single-node' docker.elastic.co/elasticsearch/elasticsearch:6.3.2

docker run -d --network emaily --name=tika apache/tika:1.24 #Optional
```

You additionally need to run a LDAP server as well. Here we use OpenLDAP and we mount a file to it to prepopulate data. 
It contains a user `james-user` with the password `secret`.

```
docker run -d --network emaily --env SLAPD_DOMAIN=james.org --env SLAPD_PASSWORD=mysecretpassword --env SLAPD_CONFIG_PASSWORD=mysecretpassword --volume $PWD/src/main/ldap-container/populate.ldif:/etc/ldap/prepopulate/prepop.ldif --name=ldap dinkel/openldap:latest
```

Then you can finally start the James distributed server:

```
docker run -d --network emaily --hostname HOSTNAME -p "25:25" -p 80:80 -p "110:110" -p "143:143" -p "465:465" -p "587:587" -p "993:993" -p "8000:8000" --name james -t linagora/openpaas-james-distributed-ldap
```
