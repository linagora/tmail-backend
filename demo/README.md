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


