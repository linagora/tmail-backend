# Tmail backend Memory server

This server, intended for testing, relies fully on lightweight memory
datastructures.

To build it:

```
mvn clean install
mvn compile com.google.cloud.tools:jib-maven-plugin:2.7.0:dockerBuild
```

Then run it:

```
docker run linagora/tmail-backend-memory
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