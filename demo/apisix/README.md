# Apisix docker image for Team-Mail

Runs Apisix with the TeamMail plugin for OIDC single logout

In order to build the image:

0. Build the plugin for Apisix: `cd tmail-apisix-plugin-runner && mvn clean package`
1. Build the image: `docker build -t linagora/apisix:3.2.0-debian-javaplugin .`