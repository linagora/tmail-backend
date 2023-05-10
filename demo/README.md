# Tmail backend + LemonLDAP

This contains Tmail with OIDC integration with LemonLDAP, through the Apisix api gateway, with an OpenLDAP backend.

## Quickstart

0. Build the plugin for Apisix: `cd apisix/tmail-apisix-plugin-runner && mvn clean package`
1. Build ` linagora/apisix:3.2.0-debian-javaplugin` image: `docker build -t linagora/apisix:3.2.0-debian-javaplugin apisix`
2. Run `./dev.sh start` to start all services and propagate configurations.
3. Follow the local set up instructions below

You can check the logs by this command: `docker compose logs -f`.

## Local set up

Please add the following lines to your `/etc/hosts`:

```
127.0.0.1 api.manager.example.com manager.sso.example.com sso.example.com handler.sso.example.com test.sso.example.com apisix.example.com
```

TeamMail backed by LemonLDAP is then accessible within your browser: http://test.sso.example.com:8080/

Username: `james-user@tmail.com`

Password: `secret`

User can logout from within TMail or visit `http://sso.example.com`, then click Logout and confirm.

## Flow

Here's a recording of the flow:

![](./media/OIDC-flow.mp4)