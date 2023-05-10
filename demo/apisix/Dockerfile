FROM apache/apisix:3.2.0-debian
USER root
RUN apt-get update && apt -y install openjdk-11-jre-headless
ADD apache-apisix-java-plugin-runner-0.4.0-bin.tar.gz /usr/local/
