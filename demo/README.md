# Tmail backend + LemonLDAP

This contains Tmail with OIDC integration with LemonLDAP with an OpenLDAP backend.

## Quickstart

Run `./dev.sh start` to start all services and propagate configurations.

You can check the logs by this command: `docker compose logs -f`.

## Local set up

Please add the following lines to your `/etc/hosts`:

```
127.0.0.1 api.manager.example.com manager.sso.example.com sso.example.com handler.sso.example.com test.sso.example.com tmail-backend
```

TeamMail backed by LemonLDAP is then accessible within your browser: http://test.sso.example.com:8080/

Username: `james-user@tmail.com`

Password: `secret`

User can logout from within TMail or visit `http://sso.example.com`, then click Logout and confirm.

## Flow

Here's a recording of the flow:

![](./media/OIDC-flow.mp4)