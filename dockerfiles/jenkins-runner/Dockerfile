FROM jenkins/jenkinsfile-runner:adoptopenjdk-11-hotspot

# Install git, docker
RUN apt-get update && \
  apt-get install -y wget git unzip apt-transport-https ca-certificates curl gnupg2 software-properties-common && \
  curl -fsSL https://download.docker.com/linux/$(. /etc/os-release; echo "$ID")/gpg > /tmp/dkey; apt-key add /tmp/dkey && \
  add-apt-repository \
    "deb [arch=amd64] https://download.docker.com/linux/$(. /etc/os-release; echo "$ID") \
    $(lsb_release -cs) \
    stable" && \
  apt-get update && \
  apt-get -y install docker-ce \
  && rm -rf /var/lib/apt/lists/*

# Install Maven 3.6.3
ENV MAVEN_VERSION 3.6.3
RUN curl -Lf https://downloads.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar -C /opt -xzv
ENV M2_HOME /opt/apache-maven-$MAVEN_VERSION
ENV maven.home $M2_HOME
ENV M2 $M2_HOME/bin
ENV PATH $M2:$PATH
