# Non-root Docker image for Twake Mail distributed server, built WITHOUT jib.
#
# This Dockerfile consumes artifacts PREBUILT by the host/CI agent with plain
# Maven (no maven build runs inside the container), and reproduces the exact
# layout the jib-maven-plugin produced for this module (see pom.xml):
#
#   /root/conf                    <- src/main/conf
#   /root/classes                 <- target/classes
#   /root/resources               <- target/classes (resources live there too)
#   /root/libs/*                  <- target/dependency (repository layout) + src/main/extensions-jars
#   /root/glowroot                <- target/glowroot (downloaded + filtered in the package phase)
#   /root/async-profiler          <- target/async-profiler-2.9-linux-x64
#   /root/eml-template            <- src/main/eml-template
#   /root/doc/release-instructions<- src/doc/release-instructions
#   /usr/bin/james-cli            <- src/main/scripts/james-cli (755)
#   /usr/local/bin/curl           <- static curl binary (755)
#
# jib lays out appRoot=/root with dependencies + resources + classes on the
# classpath; we keep the same layout so existing volume mounts, docs and the
# james-cli script (hardcoded classpath /root/resources:/root/classes:/root/libs/*)
# keep working unchanged. The JVM expands /root/libs/* itself (classpath
# wildcard), and dependency jars are flattened into /root/libs, exactly like
# jib's dependencies layer, so filename collisions are avoided by putting
# each jar in its own repository-layout directory under /root/libs.
#
# BUILD CONTEXT: tmail-backend/apps/distributed (run from the repository root:
#   mvn -f tmail-backend/apps/distributed/pom.xml package -DskipTests \
#       -Dmaven.javadoc.skip=true -Dgit.useNativeGit=false
#   mvn -f tmail-backend/apps/distributed/pom.xml dependency:copy-dependencies \
#       -DincludeScope=runtime -DoutputDirectory=target/dependency
#   docker build -f tmail-backend/dockerfiles/distributed/Dockerfile \
#       -t <tag> tmail-backend/apps/distributed
# ) with target/dependency populated by dependency:copy-dependencies (FLAT
# layout: the classpath wildcard /root/libs/* only matches jars in that exact
# directory, not subdirectories, so a maven repository-layout tree would be
# invisible to the JVM). CI runs copy-dependencies explicitly before the
# docker build; locally `mvn install` of the distributed module leaves the
# same content in target/dependency via the same goal when run as documented.
#
# NON-ROOT: everything is installed under /root with ownership given to the
# `james` user (uid 1000), which is also the user the container runs as.
# /root is the traditional home of the root account, but files created inside
# it are owned by james:james and readable by it; the name is kept for layout
# compatibility (it is just a directory here, not the root home).
#
# PORTS: the container runs as a non-root user, so privileged ports (<1024)
# such as 80 (JMAP) and 25 (SMTP) cannot be bound. The shipped configuration
# still binds 0.0.0.0:25 and 0.0.0.0:80 by default; operators must either run
# the container with port mapping (e.g. -p 8025:25 -p 8080:80) and update
# /root/conf/smtpserver.xml + jmap settings accordingly, or override the binds
# in the conf files. The EXPOSE statements below are metadata only; they
# mirror the jib configuration for documentation purposes.

FROM eclipse-temurin:26-jre-noble

ARG JAMES_USER=james
ARG JAMES_UID=1000

# curl and healthcheck dependencies: we ship a static curl binary (see below),
# so nothing extra is needed here.
# eclipse-temurin:noble ships an `ubuntu` user/group at uid/gid 1000; rename
# it to `james` instead of creating a conflicting one.
RUN groupmod -n ${JAMES_USER} ubuntu && \
    usermod -l ${JAMES_USER} -d /root ubuntu && \
    mkdir -p /root/conf /root/libs /root/glowroot /root/async-profiler \
             /root/eml-template /root/doc/release-instructions \
             /root/classes /root/resources /root/var /root/log && \
    chown -R ${JAMES_USER}:${JAMES_USER} /root

WORKDIR /root

# --- jib layout reproduction -------------------------------------------------
# Module classes + resources (jib copies target/classes wholesale into
# /root/classes; resources compiled under target/classes cover /root/resources).
COPY target/classes /root/classes/

# Runtime dependencies, flattened into /root/libs like jib's dependencies
# layer. dependency:copy-dependencies must be run WITHOUT useRepositoryLayout
# (flat): the classpath wildcard /root/libs/* only matches jars in that exact
# directory, not in subdirectories.
COPY target/dependency/ /root/libs/

# Configuration files
COPY src/main/conf /root/conf/

# james-cli script
COPY src/main/scripts/james-cli /usr/bin/james-cli

# Extension jars
COPY src/main/extensions-jars/ /root/libs/

# eml templates + release instructions
COPY src/main/eml-template /root/eml-template/
COPY src/doc/release-instructions /root/doc/release-instructions/

# glowroot (downloaded + filtered during the maven package phase)
COPY target/glowroot /root/glowroot/

# async-profiler
COPY target/async-profiler-2.9-linux-x64 /root/async-profiler/

# static curl binary
COPY target/curl/curl /usr/local/bin/curl

# Fix ownership + permissions (jib equivalent permissions block).
RUN chmod 755 /usr/bin/james-cli /usr/local/bin/curl && \
    chmod 755 /root/async-profiler/profiler.sh \
              /root/async-profiler/build/libasyncProfiler.so \
              /root/async-profiler/build/jattach 2>/dev/null || true && \
    chown -R ${JAMES_USER}:${JAMES_USER} /root

USER ${JAMES_USER}

# Exposed ports (metadata only, mirrors the jib config):
#   80 JMAP, 143 IMAP, 993 IMAPS, 25 SMTP, 465 SMTP+STARTTLS, 587 SMTPS,
#   4000 GLOWROOT, 4190 ManageSieve, 8000 WEBADMIN
# Note: 80 and 25 are privileged (<1024) and CANNOT be bound by the non-root
# user running this container. Map them to high ports via docker -p / k8s
# port mapping and adapt the conf files, or change the binds in the conf.
EXPOSE 80 143 993 25 465 587 4000 4190 8000

# JVM flags mirror the jib configuration (root pom pluginManagement).
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -Dlogback.configurationFile=/root/conf/logback.xml -Dworking.directory=/root/ -Dextra.props=/root/conf/jvm.properties"

# Same classpath semantics as jib: /root/libs/* (dependencies, recursively)
# + /root/libs (extension jars) + /root/resources + /root/classes.
# The classpath wildcard ordering mirrors jib's (dependencies first, then
# resources, then classes) via the wildcard-expansion ordering of the JVM.
ENTRYPOINT ["java", \
    "-cp", "/root/libs/*:/root/libs:/root/resources:/root/classes", \
    "com.linagora.tmail.james.app.DistributedServer"]
