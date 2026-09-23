# Postgres Twake Mail backend 1.0.21.1 release instructions

These instructions cover changes since 1.0.20.

## Schema migration

This release requires no mandatory PostgreSQL schema migration.

## Behaviour changes

### Blob ID format

When `james.blobid.entropy` is unset, James now defaults to 128 bits of entropy for new blob IDs rather
than 256. Existing blobs remain readable, but the same content stored under the old and new formats will
not deduplicate across formats. To keep generating the previous format, set in `jvm.properties` before
starting the new version:

```
james.blobid.entropy=256
```

### OIDC token introspection

James now rejects OIDC tokens whose introspection response reports `active=false`. No configuration
change is required.

## Optional features

### S3 bucket sharding

Postgres deployments configured with S3 blob storage can use TMail's optional bucket sharding.
It is **disabled by default**. Set `tmail.blobstore.shards` and, if needed,
`tmail.blobstore.shards.ommited.buckets` in `blob.properties`. Do not enable it for an existing non-empty
blob store without a separate data migration plan: changing the physical bucket layout makes existing
blobs unreachable. Keep the shard count and omitted-bucket list fixed once enabled. See the
[blob store documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/blob-store.adoc).

For Ceph RADOS Gateway, James also offers unordered bucket listings through
`james.s3.rados.allow.unorder=true` in `jvm.properties`. This option is disabled by default.

### JMAP settings

- Set `james.jmap.preview.length` in `jvm.properties` to change the length of newly computed JMAP
  previews (default: 256 characters). Existing previews keep their current length until reindexed.
- Set `webpush.enabled=false` in `jmap.properties` to disable JMAP WebPush. It remains enabled by default.

### WebAdmin domain tasks

`GET /domains/{domain}/tasks` lists only tasks belonging to that domain. The optional `status` query
parameter filters the results. See the [WebAdmin documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/webadmin.adoc).

`GET /events/deadLetter?eventId={eventId}` can now find a dead-lettered event across groups. Supplying
`group` narrows the search. This route is available without any new configuration.

### ACL normalization listener

The optional `com.linagora.tmail.listener.EnrichACLListener` qualifies local-part-only ACL entries with
the mailbox owner's domain. Configure it in `listeners.xml` only if this normalization is needed.
Such entries are rejected before reaching the listener unless
`james.rights.crossdomain.allow=true` is also set. See the
[listener documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/extra-listener.adoc).
That setting also permits cross-domain ACL entries generally, so review the deployment's sharing policy
before enabling it.

### Migration proxy authentication

The separately deployed migration proxy now supports `AUTHENTICATE PLAIN` in its default mode and
optional Kerberos/GSSAPI authentication (`kerberos.enabled=false` by default). Only when Kerberos is
enabled does it require administrator credentials for both backends and, by default, implicit TLS with
certificate and hostname validation.

Only proxies configured with `rabbitmq.properties` consume migration switch events. An external
orchestrator can publish `{"migratedUser":{"mailAddress":"user@example.com"}}` to the
`migration:switch` exchange with routing key `mail` to mark a user migrated and disconnect their IMAP
sessions. See the
[migration proxy configuration](../../../../../../../docs/modules/ROOT/pages/migration-proxy/configuration.adoc).

### Mongolian notification templates

Bundled configurations and templates now support `mn` for calendar replies and delivery status
notifications. Deployments with mounted configuration files can opt in by adding `mn` to
`calendarEvent.reply.supportedLanguages` in `jmap.properties` and to the `I18NDSNBounce`
`supportedLanguages` entries in `mailetcontainer.xml`. Deployments with custom template directories
also need the Mongolian templates.

## TMail deployment

Update the Postgres server image to `linagora/tmail-backend:postgresql-1.0.21.1`. If running the migration
proxy, update it to `linagora/tmail-migration-proxy:1.0.21.1`.

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.21.1
