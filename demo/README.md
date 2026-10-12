# Tmail backend + LemonLDAP + Twake Directory Manager

This contains Tmail with OIDC integration with LemonLDAP with an OpenLDAP backend.

The directory is administered with the [Twake Directory Manager](https://github.com/linagora/twake-directory-manager),
the console of [ldap-rest](https://github.com/linagora/ldap-rest). Its
[`core/twake/james` plugin](https://github.com/linagora/ldap-rest/blob/master/docs/usage/plugins/integrations/james-mail.md)
pushes every change made through it to Tmail over WebAdmin.

```
 Directory Manager ──(LDAP)──> OpenLDAP <──(LDAP)── Tmail users repository, LemonLDAP
        │
        └──(WebAdmin: aliases, quotas, mailing lists, team mailboxes, identities, delegation)──> Tmail
```

## Quickstart

Run `./dev.sh start` to start all services and propagate configurations.

You can check the logs by this command: `docker compose logs -f`.

`./dev.sh stop` stops the services and drops the directory: the next start seeds it again.

## Local set up

Please add the following lines to your `/etc/hosts`:

```
127.0.0.1 api.manager.example.com manager.sso.example.com sso.example.com handler.sso.example.com test.sso.example.com directory.example.com tmail-backend
```

TeamMail backed by LemonLDAP is then accessible within your browser: http://test.sso.example.com:8080/

Username: `james-user@tmail.com`

Password: `secret`

User can logout from within TMail or visit `http://sso.example.com`, then click Logout and confirm.

## Managing the directory

The Twake Directory Manager is at http://directory.example.com:8081/static/console/. Sign in
through LemonLDAP as `admin` / `secret`, administrator of the whole directory.

OpenLDAP is seeded on its first start from `ldap/bootstrap`: the Twake schemas, the
nomenclatures the console expects and the accounts `admin`, `james-user`, `james-user2`,
`alice`, `bob` and `gpt` (the AI bot), all `@tmail.com` with the password `secret`.

What the console changes, Tmail sees:

- **accounts**: Tmail reads them from OpenLDAP (`objectClass=twakeAccount`), so a new account
  can sign in to Tmail right away;
- **display name**: becomes the name of the account's default identity;
- **aliases** (`mailAlternateAddress`): become Tmail address aliases;
- **mail quota** (`mailQuotaSize`): becomes the Tmail size quota;
- **groups with an address**: a _mailing list_ becomes a Tmail address group, a _team mailbox_ a
  Tmail team mailbox (use the `tmail.com` or `lists.tmail.com` domains);
- **delegation**: becomes a Tmail delegation;
- **a changed address**: renames the Tmail account.

Only changes made through ldap-rest are propagated: an entry written straight into OpenLDAP
reaches Tmail as an account, but its aliases, quota or list do not.

## Flow

Here's a recording of the flow:

![](./media/OIDC-flow.mp4)
