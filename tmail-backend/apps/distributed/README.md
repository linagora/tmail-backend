# Twake Mail backend Distributed server

This server is the distributed version of James relying on:

* Cassandra version 4.1.3
* S3 API-like object storage. Using here Zenko Cloudserver version 8.7.25
* OpenSearch version 2.1.0
* RabbitMQ version 3.12.1
* Tika version 2.8 (optional)

## Build

To be able to run Twake Mail backend server, you need to generate a JWT key pair first.
A really easy way to generate a basic JWT key pair is like this:

```
# private key
openssl genrsa -out jwt_privatekey 4096
# public key
openssl rsa -in jwt_privatekey -pubout > jwt_publickey
```

Then copy those two keys into the `src/main/conf` folder of this distributed app module.

To build the distributed server:

```
mvn clean install
mvn compile com.google.cloud.tools:jib-maven-plugin:3.4.3:dockerBuild
```

## Run

### Run manually

Firstly, create your own user network on Docker for the James environment:

```
docker network create --driver bridge emaily
```

You can then start in that newly created network all the other softwares the distributed James is relying on:

```
docker run -d --network emaily --name=cassandra cassandra:4.1.9

docker run -d --network emaily --name=rabbitmq rabbitmq:4.0.9-management

docker run -d --network emaily --env 'REMOTE_MANAGEMENT_DISABLE=1' --env 'SCALITY_ACCESS_KEY_ID=accessKey1' --env 'SCALITY_SECRET_ACCESS_KEY=secretKey1' --name=s3.docker.test ghcr.io/scality/cloudserver:c1ba296859690c1cbbec609aaae430f6b04b4745

docker run -d --network emaily --name=opensearch --env 'discovery.type=single-node' opensearchproject/opensearch:2.19.2

docker run -d --network emaily --name=tika apache/tika:3.2.0.0 #Optional
```

Then you can finally start the James distributed server. If you included the JWT keys in the build:

```
docker run -d --network emaily --hostname HOSTNAME -p "25:25" -p 80:80 -p "110:110" -p "143:143" -p "465:465" -p "587:587" -p "993:993" -p "8000:8000" --name james -t linagora/tmail-backend-distributed
```

If not, you need to bind them to the container for Twake Mail to start:

```
docker run --network emaily --hostname HOSTNAME \
--mount type=bind,source=[/ABSOLUTE/PATH/TO/JWT_PUBLICKEY],target=/root/conf/jwt_publickey \
--mount type=bind,source=[/ABSOLUTE/PATH/TO/JWT_PRIVATEKEY],target=/root/conf/jwt_privatekey \
-p "25:25" -p 80:80 -p "110:110" -p "143:143" -p "465:465" -p "587:587" -p "993:993" -p "8000:8000" \
--name james -t linagora/tmail-backend-distributed
```

Use the [JAVA_TOOL_OPTIONS environment option](https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#jvm-flags)
to pass extra JVM flags. For instance:

```
docker run -d --network emaily -e "JAVA_TOOL_OPTIONS=-Xmx500m -Xms500m" --hostname HOSTNAME -p "25:25" -p 80:80 -p "110:110" -p "143:143" -p "465:465" -p "587:587" -p "993:993" -p "8000:8000" --name james -t linagora/tmail-backend-distributed
```

### With docker-compose 

There is a docker-compose file in the root of this folder you can use to run everything in the same network at once.

First you need to generate your RSA keys and replace the local paths by the correct ones pointing to your keys:

```
services:
  tmail-backend:
    [...]
    volumes:
    - ${RSA_PUBLICKEY_PATH}:/root/conf/jwt_publickey # Replace with absolute path to your RSA public key
    - ${RSA_PRIVATEKEY_PATH}:/root/conf/jwt_privatekey # Replace with absolute path to your RSA private key
```

Then, you can just start it:

```
docker-compose up -d
```

### Run with Glowroot

[Glowroot APM]() is packaged as part of the docker distribution to easily enable valuable performances insights.
Disabled by default, its java agent can easily be enabled.

If you run the distributed server manually, you can do like this:

```
docker run -d --network emaily -e "JAVA_TOOL_OPTIONS=-javaagent:/root/glowroot/glowroot.jar" --hostname HOSTNAME -p "25:25" -p 80:80 -p "110:110" -p "143:143" -p "465:465" -p "587:587" -p "993:993" -p "8000:8000" --name james -t linagora/tmail-backend-distributed
```

If you use the docker-compose file, you can add an environment variable to the distributed server container:

```
services:
  james:
    [...]
    environment:
      - JAVA_TOOL_OPTIONS=-javaagent:/root/glowroot/glowroot.jar
```

### Using the CLI

The [CLI](https://james.apache.org/server/manage-cli.html) can easily be used once your distributed server is running:

```
docker exec james james-cli ListDomains
```
