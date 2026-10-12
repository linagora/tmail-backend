# Tmail backend + LemonLDAP + Twake Directory Manager + Twake Mail Admin

This contains Tmail with OIDC integration with LemonLDAP with an OpenLDAP backend.

The directory is administered with the [Twake Directory Manager](https://github.com/linagora/twake-directory-manager),
the console of [ldap-rest](https://github.com/linagora/ldap-rest). Its
[`core/twake/james` plugin](https://github.com/linagora/ldap-rest/blob/master/docs/usage/plugins/integrations/james-mail.md)
pushes every change made through it to Tmail over WebAdmin.

```
 Directory Manager ──(LDAP)──> OpenLDAP <──(LDAP)── Tmail users repository, LemonLDAP
        │
        └──(WebAdmin: aliases, quotas, mailing lists, team mailboxes, identities, delegation)──> Tmail
                                                                                              ↑
 Twake Mail Admin (technical, functional) ──(OIDC token)──> webadmin-proxy ──(WebAdmin)───────┘
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
`alice`, `bob`, `gpt` (the AI bot) and `support`, all `@tmail.com` with the password `secret`.

What the console changes, Tmail sees:

- **accounts**: Tmail reads them from OpenLDAP (`objectClass=twakeAccount`), so a new account
  can sign in to Tmail right away;
- **display name**: becomes the name of the account's default identity;
- **aliases** (`mailAlternateAddress`): become Tmail address aliases;
- **mail quota** (`mailQuotaSize`): becomes the Tmail size quota;
- **team mailboxes**: a group of type _shared mailbox_ becomes a Tmail team mailbox;
- **delegation**: becomes a Tmail delegation;
- **a changed address**: renames the Tmail account.

Mailing lists are not pushed to Tmail: the `LDAPMailingList` mailet reads them from LDAP at each
mail (`tmail/mailetcontainer.xml`), so membership changes apply at once and the list type is
enforced — open, members only, owners only, internal, same domain. A rejected sender gets a bounce.
Give lists an address on `lists.tmail.com`. The plugin would otherwise mirror lists as James
address groups, which cannot restrict senders: `DM_JAMES_MAILING_LIST_BRANCHES` points it at a
branch that does not exist, so it skips them (and logs an error for each list created).

Only changes made through ldap-rest are propagated: an entry written straight into OpenLDAP
reaches Tmail as an account, but its aliases, quota or list do not.

## Administering Twake Mail

Two [Twake Mail Admin](https://github.com/linagora/twake-mail-admin) consoles sign in through
LemonLDAP and reach WebAdmin through [webadmin-proxy](https://github.com/linagora/webadmin-proxy),
which grants each one a profile (`webadmin-proxy/configuration.json`):

| Console | URL | Account | Profile |
|---|---|---|---|
| Technical (global mode) | http://localhost:3333 | `admin` / `secret` | `ldap-rest-managed-denials.json` + `linagora-mail-admin-profile.json` |
| Functional (domain mode, `tmail.com`) | http://localhost:3334 | `support` / `secret`, `admin` / `secret` | `linagora-mail-functional-admin-ldap-rest.json`, bound to the account's domain |

Both profiles refuse what ldap-rest owns (user quotas, user aliases, renames, mailing lists): change
those in the Directory Manager. The consoles are served on `localhost`, the only plain http origin
browsers treat as secure, which their PKCE login needs.

The consoles need `SSO_ALLOW_INSECURE_REQUESTS`, to talk to the plain http LemonLDAP, which no
released image has yet. Build it from the `sso-allow-insecure-requests` branch of twake-mail-admin
before `./dev.sh start`:

```sh
docker build -t twake-mail-admin:local <path to twake-mail-admin>
```

## Flow

Here's a recording of the flow:

![](./media/OIDC-flow.mp4)
