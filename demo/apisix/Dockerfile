FROM apache/apisix:3.9.1-debian
USER root
RUN apt-get update && apt -y install openjdk-11-jre-headless
ADD apache-apisix-java-plugin-runner-0.4.0-bin.tar.gz /usr/local/
ADD tmail-apisix-plugin-runner/target/tmail-apisix-plugin-runner-0.0.1-SNAPSHOT.jar /usr/local/apisix/token_revoker_plugin.jar