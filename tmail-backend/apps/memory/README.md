# Twake Mail backend Memory server

This server, intended for testing, relies fully on lightweight memory
datastructures.

To be able to run Twake Mail backend server, you need to generate a JWT key pair first.
A really easy way to generate a basic JWT key pair is like this:

```
# private key
openssl genrsa -out jwt_privatekey 4096
# public key
openssl rsa -in jwt_privatekey -pubout > jwt_publickey
```

You can copy those two keys into the `src/main/conf` folder of this memory app module if you want to build
Twake Mail server with them in the conf, or you can decide to mount them later when running the server.

Then to build your server:

```
mvn clean install
mvn compile com.google.cloud.tools:jib-maven-plugin:2.7.0:dockerBuild
```

You can add the -DskipTests flag as well if you don't want to run the tests.

```
mvn clean install -DskipTests
```

Then you can finally start the James distributed server. If you included the JWT keys in the build:

```
docker run linagora/tmail-backend-memory
```

If not, you need to bind them to the container for Twake Mail to start:

```
docker run --mount type=bind,source=[/ABSOLUTE/PATH/TO/JWT_PUBLICKEY],target=/root/conf/jwt_publickey \
--mount type=bind,source=[/ABSOLUTE/PATH/TO/JWT_PRIVATEKEY],target=/root/conf/jwt_privatekey \
linagora/tmail-backend-memory
```

Use the [JAVA_TOOL_OPTIONS environment option](https://github.com/GoogleContainerTools/jib/blob/master/docs/faq.md#jvm-flags) 
to pass extra JVM flags. For instance:

```
docker run -e "JAVA_TOOL_OPTIONS=-Xmx500m -Xms500m" linagora/tmail-backend-memory
```

[Glowroot APM]() is packaged as part of the docker distribution to easily enable valuable performances insights.
Disabled by default, its java agent can easily be enabled:

```
docker run -e "JAVA_TOOL_OPTIONS=-javaagent:/root/glowroot/glowroot.jar" linagora/tmail-backend-memory
```
The [CLI](https://james.apache.org/server/manage-cli.html) can easily be used:

```
docker exec CONTAINER-ID james-cli ListDomains
```

## Provisioning

The docker container embed a small provisioning script, creating some users, mailboxes and emails
to be used for testing purposes.

To run it:

```
docker exec CONTAINER-ID /root/provisioning/provisioning.sh
```

It will create the following accounts:

```
User: alice@localhost
Password: aliceSecret

User: bob@localhost
Password: bobSecret

User: empty@localhost
Password: emptrySecret
```

## Troubleshooting
### How to get around 'maven error: release version 21 not supported'.

This error commonly occurs due to a mismatch between the Java version used to compile your Maven project and the target Java version specified in the project configuration.

Maven uses the Java version set in the JAVA_HOME environment variable by default. Ensure that this version matches the target version specified in your project. The target version [is set to JDK 21](https://github.com/linagora/tmail-backend/blame/b28597e4e3028d1c2e79e988d20f80854932cb14/pom.xml#L14) as of the current configuration.

To resolve the issue, verify that JAVA_HOME is pointing to JDK 21.
