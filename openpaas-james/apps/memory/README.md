# openpaas-james Memory server

This server, intended for testing, relies fully on lightweight memory
datastructures.

To build it:

```
mvn clean install
mvn compile com.google.cloud.tools:jib-maven-plugin:2.7.0:dockerBuild
```

Then run it:

```
docker run linagora/openpaas-james-memory
```

The [CLI](https://james.apache.org/server/manage-cli.html) can easily be used:

```
docker exec CONTAINER-ID james-cli ListDomains
```