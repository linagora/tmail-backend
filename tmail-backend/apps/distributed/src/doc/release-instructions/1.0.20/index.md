# Distributed Twake Mail backend 1.0.20 release instructions

## Schema migration

This release requires no mandatory schema migration.

## Behaviour changes

### AI: certificate validation is now enabled by default

`openrag.ssl.trust.all.certs` and `scribe.ssl.trust.all.certs` in `ai.properties` now default to `false`
instead of `true`. Deployments relying on the previous default - typically those pointing to an OpenRAG or
Scribe endpoint served with a self-signed certificate - must now set the property explicitly:

```
openrag.ssl.trust.all.certs=true
scribe.ssl.trust.all.certs=true
```

Prefer trusting the private PKI over disabling the validation.

### WebAdmin credentials

Apache James now generates a random WebAdmin password upon startup when `password.generate` is not
configured, which makes WebAdmin authenticated out of the box. **TMail pins that default back to `false`**,
so existing TMail deployments are unaffected and WebAdmin keeps its previous behaviour.

Set `password.generate=true` in `webadmin.properties` to opt into the generated password. Note that, without
JWT nor a configured password, the WebAdmin endpoint remains unauthenticated and must not be exposed outside
of a trusted network. See the [WebAdmin documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/webadmin.adoc).

### IMAP / SMTP SASL

TMail adopted the new protocol-neutral SASL SPI of Apache James. No configuration change is required: when
`TMailImapPackage` or `TMailImapAuthPackage` is declared, the TMail delegation-aware PLAIN mechanism is
enabled automatically while the other default mechanisms are preserved.

If you explicitly configure `auth.saslMechanisms` in `imapserver.xml`, include the TMail factory in order to
keep IMAP delegation support:

```xml
<auth>
    <saslMechanisms>com.linagora.tmail.sasl.TMailPlainSaslMechanismFactory,OauthBearerSaslMechanismFactory,XOauth2SaslMechanismFactory</saslMechanisms>
</auth>
```

See the [IMAP auth delegation extension documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/imap-extensions/imapAuthDelegationExtension.adoc).

### JMAP OIDC authentication

The TMail-specific JMAP OIDC implementation was dropped in favour of the Apache James one. The
`oidc.*` keys in `jmap.properties`, the `OidcAuthenticationStrategy` class name, the challenge realm, the
Redis key prefixes and the backchannel logout route are all preserved: no configuration change is required.
They are now documented in the
[JMAP OIDC extension page](../../../../../../../docs/modules/ROOT/pages/tmail-backend/jmap-extensions/oidcAuthentication.adoc).

## New features configuration

### JMAP upload from URL (Optional)

TMail can now import an attachment directly from a remote URL, instead of having the client upload its bytes.
The feature is disabled by default. To enable it, set in `jmap.properties`:

```
upload.from.url.enabled=true
upload.from.url.allowed.sources=https://drive.example.com,https://%-drive.twake.linagora.com
# upload.from.url.source.response.timeout=1m
# upload.from.url.source.trust.all.ssl.certs=false
```

Only HTTPS sources are accepted, and the imported content is bounded by the existing `upload.max.size`
setting. Enabling the feature without `upload.from.url.allowed.sources` starts the server with a warning and
accepts any HTTPS source on port `443` that passes SSRF validation; pinning the allowed sources is strongly
advised.

See [JMAP configuration](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/jmap.adoc) and the
[upload from URL specification](../../../../../../../docs/modules/ROOT/pages/tmail-backend/jmap-extensions/uploadFromUrl.adoc).

### Mail processing deduplication (Optional)

Mails in transit - the ones held by the mail queue and the mail repositories - are, by default, deduplicated
with the copies eventually stored within the mailboxes, which means their blobs can only be reclaimed by the
deduplication garbage collector, several generations later.

Setting the following key in `blob.properties` stores each mail in transit as a standalone RFC822 object,
deleted as soon as the mail leaves the mail queue or the mail repository:

```
mailprocessing.deduplication.enabled=false
# mailprocessing.bucket=mail-processing
```

This trades storage volume for a bounded, self-managed transit storage, and allows replaying the mail queue
content out of the `mail-processing` bucket. See
[blob store configuration](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/blob-store.adoc).

### RabbitMQ notification queue expiry (Optional)

The per-node notification (key registration) queue can now be declared with a TTL rather than as auto-delete,
which makes it survive short consumer outages. In `rabbitmq.properties`:

```
notification.queue.autoDelete=false
notification.queue.ttl=3600000
```

Both keys are optional and the previous auto-delete behaviour remains the default.

### Scribe AI endpoint (Optional)

Scribe - the JMAP AI chat completion endpoint `/ai/v1/chat/completions` - now has its own configuration,
decoupled from the RAG one. In `ai.properties`:

```
scribe.url=https://chat.example.com
scribe.token=my-token
scribe.ssl.trust.all.certs=false
```

For backward compatibility, Scribe falls back to the legacy `baseURL` and `apiKey` properties when
`scribe.url` and `scribe.token` are absent.

AI features can be restricted to paying SaaS users through the `applyWhen` filter:

```
ai.jmap.capability.applyWhen=com.linagora.tmail.saas.filter.SaaSPayingUser
```

The same filter can be declared on the `LlmMailClassifierListener` in `listeners.xml`. See the
[AI bot documentation](../../../../../../../tmail-backend/tmail-third-party/ai-bot/README.md).

### Mailing lists (Optional)

WebAdmin routes to read and manage LDAP mailing lists are now available, the mailing list DN configuration
was eased, subaddressing is supported, and the OBM `externalContactEmail` attribute is handled. See the
[WebAdmin documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/webadmin.adoc).

### New WebAdmin routes

- Sum of the quota, globally and per domain.
- `POST /users?action=reindex` to reindex all users.
- Tasks to provision the email templates of a reference account into the `Templates` mailbox of a user or
  of every user of a domain.

### DMARC report matchers (Optional)

New `IsDmarcReportWithIssues` and `IsDmarcReportWithoutIssues` mailet matchers are available for use in the
mailet container.

### Team mailbox aliases and migration (Optional)

Team mailboxes now accept emails sent to their aliases, and members can send from those aliases. A team
mailbox can also be migrated through a scoped IMAP login: an administrator authenticating with a team mailbox
scope is routed to the team mailbox owner rather than to himself.

### Migration proxy (Optional, new application)

A new `migration-proxy` application is shipped: an IMAP front proxy routing each user to the legacy or to the
new backend during a migration, closing the IMAP sessions of a user upon migration. It is published as a
dedicated Docker image and is not required by an existing distributed deployment. See the
[migration proxy documentation](../../../../../../../docs/modules/ROOT/pages/migration-proxy/objective.adoc).

## Tmail deployment

Please update your tmail-backend docker image to the following version: `linagora/tmail-backend:distributed-1.0.20`

Deployments using the AI features should use `linagora/tmail-backend:distributed-ai-1.0.20` instead, and
deployments running the migration proxy should use `linagora/tmail-migration-proxy:1.0.20`.

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.20
