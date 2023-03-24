# Tmail backend + LemonLDAP

This contains Tmail backend and OIDC integration with LemonLDAP, through the Krakend api gateway, with an OpenLDAP backend.

## Quickstart

1. Run `./dev.sh start` to start all services and propagate configurations.
2. Visit `tmail-frontend` at http://test.sso.example.com:8080
3. Login with SSO, with the credentials `james-user@localhost / secret`
4. Try logging out, first by visiting `http://sso.example.com`, then click Logout and confirm.
5. Navigate back to `tmail-frontend` at http://test.sso.example.com:8080. The `/jmap` calls should now be rejected by `krakend` because the token is invalid.

You can check the logs by this command: `docker compose logs -f`.

## Flow

Here's a recording of the flow:

![](./media/OIDC-flow.mp4)


## Local set up

Please add the following lines to your `/etc/hosts`:

```
127.0.0.1 api.manager.example.com manager.sso.example.com sso.example.com handler.sso.example.com test.sso.example.com
```

TeamMail backed by LemonLDAP is then accecible within your browser: http://test.sso.example.com:8080/

Username: `james-user@localhost`

Password: `secret`
