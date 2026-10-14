# Distributed Twake Mail backend 1.0.22 release instructions

These instructions cover changes since 1.0.21.1.

## Schema migration

All the CQL below is harmless to the running 1.0.21.1 nodes, which ignore these columns: run it **before deploying
1.0.22**. It uses the packaged default keyspace, `apache_james`; replace that name if your `cassandra.properties` sets
another `cassandra.keyspace`.

### Sent recipients rate limiting columns

Mandatory: add the columns storing the per-user and per-domain rate limits on the number of recipients a user can
send emails to. The new version fails to start while these columns are missing.

```sql
ALTER TABLE apache_james.user ADD recipients_sent_per_minute bigint;
ALTER TABLE apache_james.user ADD recipients_sent_per_hour bigint;
ALTER TABLE apache_james.user ADD recipients_sent_per_day bigint;
ALTER TABLE apache_james.domains ADD recipients_sent_per_minute bigint;
ALTER TABLE apache_james.domains ADD recipients_sent_per_hour bigint;
ALTER TABLE apache_james.domains ADD recipients_sent_per_day bigint;
```

Existing rows read these columns as unset: rate limiting behaves as before until a recipient limit is configured.

### Mail DNS configuration status column

Mandatory: add the column storing the mail DNS validation status of SaaS domains.

```sql
ALTER TABLE apache_james.domains ADD mail_dns_configuration_validated boolean;
```

Existing domains read this column as unset and retain their sending behaviour.

## Behaviour changes

### Docker base image

Docker images are now based on `eclipse-temurin:27-jre-resolute` (Java 27 runtime, Ubuntu Resolute) instead of
`eclipse-temurin:26-jre-noble`. Review custom images built on top of the TMail images, as well as JVM options tied to
the previous runtime.

### RabbitMQ health checks

RabbitMQ consumers and dead letter queues are now reported by two aggregated components of `GET /healthcheck`:

- `RabbitMQConsumers` replaces the `EventbusConsumers-*` components (event bus partitions included),
  `DistributedTaskManagerConsumers`, `MailQueueConsumers`, `TWPSettingsQueueConsumerHealthCheck` and
  `SaaSSubscriptionQueueConsumerHealthCheck`.
- `RabbitMQDeadLetterQueues` replaces `RabbitMQMailboxEventBusDeadLetterQueueHealthCheck`,
  `RabbitMQJmapEventBusDeadLetterQueueHealthCheck`, `RabbitMQContentDeletionEventBusDeadLetterQueueHealthCheck`,
  `RabbitMQMailQueueDeadLetterQueueHealthCheck`, `TWPSettingsDeadLetterQueueHealthCheck` and
  `SaaSSubscriptionDeadLetterQueueHealthCheck`.

Their cause names the affected queues. Update monitoring and alerting relying on the former component names, for
instance `GET /healthcheck/checks/{componentName}` probes.

### S3 conditional writes

`objectstorage.s3.ifNoneMatch.enable` in `blob.properties` is now honored by the TMail S3 blob store: it was
previously ignored. It remains disabled by default. Only enable it on object storages supporting conditional writes
(AWS S3, MinIO...): OVH Object Storage and Swift before 2.36.0 reject such writes, making every blob write fail.

### SMTP

- The EHLO reply no longer advertises the legacy `AUTH=` line, only the RFC 5321 compliant `AUTH` one. Very old, non
  compliant clients may no longer offer authentication.
- HELO/EHLO arguments are validated consistently. Malformed DSN `NOTIFY`, `ORCPT` and `ENVID` parameters are rejected
  with `501` instead of closing the connection.

### JMAP

- `Email/set` create rejects `bodyStructure` and unknown properties with an `invalidProperties` error instead of
  silently dropping them (an email with `bodyStructure` used to be stored with an empty body).
- `Forward/set` accepts partial patches and `Label/set` removes a label colour with `color: null`.
- Mismatching `condition` and `conditionGroup` in `Filter/set` is a request level `invalidArguments` error, and the
  `Filter/get` output is a valid `Filter/set` input.

## New features

### Sent recipients rate limiting (Optional)

On top of the number of sent emails, the number of recipients a user can send emails to can now be limited per
minute, hour and day. An email counts for its number of recipients.

Limits are resolved as the other ones: user limit, then domain limit, then the `SentRateLimiting` mailet default.
Configure the defaults in `mailetcontainer.xml` (omitted means unlimited):

```xml
<mailet match="SenderIsLocal" class="com.linagora.tmail.mailets.SentRateLimiting">
    <!-- ... -->
    <recipientsPerMinuteDefault>30</recipientsPerMinuteDefault>
    <recipientsPerHourDefault>300</recipientsPerHourDefault>
    <recipientsPerDayDefault>3000</recipientsPerDayDefault>
</mailet>
```

Recipients are the ones of the email when it reaches the mailet: place it after recipient rewriting, in the relay
processor, to only account for remote recipients.

Per-user and per-domain values are set through the `recipientsSentPerMinute`, `recipientsSentPerHours` and
`recipientsSentPerDays` fields of the `/users/{user}/ratelimits` and `/domains/{domain}/ratelimits` WebAdmin routes,
and through the optional `recipientsSentPerMinute`, `recipientsSentPerHour` and `recipientsSentPerDay` fields of the
`features.mail` section of SaaS subscription messages.

For backward compatibility, both WebAdmin and SaaS subscription messages leave a recipient limit unchanged when its
field is omitted: existing WebAdmin clients and SaaS producers do not erase them. An explicit `null` unsets it. See the
[rate limiting documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/features/tmailRateLimiting.adoc)
and the [WebAdmin documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/webadmin.adoc).

### Mail DNS sending controls for SaaS domains

SaaS domains are now provisioned once their ownership is verified, before their mail DNS configuration is validated.
The mail DNS status carried by SaaS DNS events is stored in `mail_dns_configuration_validated` (see the schema
migration above) and advertised as `mailDnsConfigurationValidated` in the SaaS JMAP capability.

Before enabling the SaaS consumer or Calendar provisioning against 1.0.22, configure **both** guards, otherwise
pending domains can send emails:

* In `jmap.properties`, append `com.linagora.tmail.james.jmap.method.MailDnsConfigurationValidation` to
  `send.extra.validations`, preserving any existing validators.
* In `smtpserver.xml`, add `<handler class="com.linagora.tmail.smtp.MailDnsConfigurationMailHook"/>` to the handler
  chain of every submission/relay listener.

Pending domains then get `forbiddenToSend` over JMAP and `550 5.7.1` over SMTP. Domains without a stored status keep
their previous behaviour. Unauthenticated incoming SMTP delivery is unaffected. See the
[mail DNS sending controls documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/mail-dns-sending-controls.adoc).

### Twake Workplace integration

When the TWP settings module is enabled:

- TMail consumes the Twake Workplace user deletion events, B2C (`auth` / `user.deleted`) and B2B
  (`b2b` / `domain.user.deleted`), and deletes the data of the user with the same steps as the `deleteData` WebAdmin
  task. Events that cannot be processed go to the `tmail-user-deletion-dead-letter` queue. Exchanges and routing keys
  can be changed with the `twp.user.deletion.b2c.*` and `twp.user.deletion.b2b.*` keys of `rabbitmq.properties`.
- The optional `theme` field of TWP common settings is synchronized into the `appearance.theme` JMAP setting, which
  becomes read-only.

See the [Twake Workplace documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/twake-workplace.adoc)
and the [RabbitMQ documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/rabbitmq.adoc).

### Team mailbox events (Optional)

The optional `com.linagora.tmail.team.events.TeamMailboxEventsListener` publishes a persistent JSON event to a RabbitMQ
topic exchange each time a team mailbox receives a message, or gets one added to its `Sent` folder. Declare it in
`listeners.xml`:

```xml
<listener>
  <class>com.linagora.tmail.team.events.TeamMailboxEventsListener</class>
</listener>
```

`amqpUri` (defaults to the server of `rabbitmq.properties`), `exchange` (defaults to `tmail`), `receivedRoutingKey` and
`sentRoutingKey` can be configured. Consumers should deduplicate on the AMQP message id as events can be published
again upon retries. See the [listener documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/extra-listener.adoc).

### Relay loop detection matchers (Optional)

The `TransitedVia` and `TransitedTwiceVia` matchers count the `Received` headers mentioning a host name or IP.
`TransitedTwiceVia=<own host name>` matches emails that went out of the server and came back to it:

```xml
<mailet match="TransitedTwiceVia=smtp.example.com" class="ToProcessor">
  <processor>relay-loop</processor>
</mailet>
```

`Received` headers supplied by the sender are counted too: a crafted email can trigger the configured processing.
See the [extra mailets documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/extra-mailet.adoc).

### LDAP mailing lists

`IsALDAPMailingList` accepts an optional `#extraFilter` fourth argument. Deployments setting `extraFilter` on the
`LDAPMailingList` mailet should set the same filter on the matcher: a group accepted by the matcher but not expanded by
the mailet is sent back to the mailet forever.

```xml
<mailet match="com.linagora.tmail.mailet.IsALDAPMailingList=ou=groups,dc=example,dc=com#groupofnames#mail#(businessCategory=*)" class="ToProcessor">
  <processor>transport</processor>
</mailet>
```

See the [LDAP mailing list documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/ldap-mailing-list.adoc).

### Spanish, German and Italian templates

Calendar reply and DSN templates are now bundled in `es`, `de` and `it`. Deployments with mounted configuration files
can opt in by adding them to `calendarEvent.reply.supportedLanguages` in `jmap.properties` and to the `I18NDSNBounce`
`supportedLanguages` entries in `mailetcontainer.xml`. Deployments with custom template directories also need the new
templates.

### WebAdmin

- The James task cleanup routes are now available.
- `GET /users` accepts the `limit`, `anchor`, `query` (case insensitive username prefix) and `domain` parameters.
  Pagination works with LDAP servers capping page sizes.

### Migration proxy SMTP submission

The separately deployed migration proxy can now proxy SMTP submission (`submission.enabled=false` by default),
authenticating each client against the submission port of its backend. It is configured through the `submission.*`
keys of `migrationproxy.properties` and a `submissionproxy.xml` file. See the
[migration proxy configuration](../../../../../../../docs/modules/ROOT/pages/migration-proxy/configuration.adoc).

## TMail deployment

Update the distributed server image to `linagora/tmail-backend:distributed-1.0.22`. Deployments using AI features
should use `linagora/tmail-backend:distributed-ai-1.0.22`. If running the migration proxy, update it to
`linagora/tmail-migration-proxy:1.0.22`.

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.22
