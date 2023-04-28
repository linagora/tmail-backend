# TMail apisix plugin runner 

The plugin for handler revoked token:
- Provide the http server to receive logout_token from lemonLdap when has event logout channel
- Pre-filter for Apisix by checking token was revoked or not

## How to compile it 

- Java 11
- Run maven command:
```
mvn clean package
```

## How to use it
- Build Apisix docker image that support Java plugin runner
    - Require: 
      - Dockerfile (example: ../apisix/Dockerfile)
      - Runner Library `apache-apisix-java-plugin-runner-0.4.0-bin.tar.gz` (original: https://github.com/apache/apisix-java-plugin-runner)
    - Docker build: `docker build -t linagora/apisix:3.2.0-debian-javaplugin .`
- Import `tmail-apisix-plugin-runner-{version}.jar` into Apisix container. For detail ([Apache APISIX® Java Plugin Runner](https://apisix.apache.org/docs/java-plugin-runner/how-it-works/))

