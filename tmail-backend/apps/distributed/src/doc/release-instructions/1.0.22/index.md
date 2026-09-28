# Distributed Twake Mail backend 1.0.22 release instructions

These instructions cover changes since 1.0.21.1.

## Schema migration

### Sent recipients rate limiting columns

Mandatory: add the columns storing the per-user and per-domain rate limits on the number of recipients a user can
send emails to. The new version fails to start while these columns are missing: run the following CQL **before
deploying 1.0.22**. It is harmless to the running 1.0.21.1 nodes, which ignore these columns.

It uses the packaged default keyspace, `apache_james`; replace that name if your `cassandra.properties` sets another
`cassandra.keyspace`:

```sql
ALTER TABLE apache_james.user ADD recipients_sent_per_minute bigint;
ALTER TABLE apache_james.user ADD recipients_sent_per_hour bigint;
ALTER TABLE apache_james.user ADD recipients_sent_per_day bigint;
ALTER TABLE apache_james.domains ADD recipients_sent_per_minute bigint;
ALTER TABLE apache_james.domains ADD recipients_sent_per_hour bigint;
ALTER TABLE apache_james.domains ADD recipients_sent_per_day bigint;
```

Existing rows read these columns as unset: rate limiting behaves as before until a recipient limit is configured.

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

## TMail deployment

Update the distributed server image to `linagora/tmail-backend:distributed-1.0.22`. Deployments using AI features
should use `linagora/tmail-backend:distributed-ai-1.0.22`.

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.22
