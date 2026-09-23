# Distributed Twake Mail backend 1.0.21.1 release instructions

These instructions cover changes since 1.0.20.

## Schema migration

No manual CQL schema change is required before deployment. After deploying 1.0.21.1, check the current
Apache James Cassandra schema version with `GET /cassandra/version`. If it is below 16, run the migration
to version 16 through an authenticated WebAdmin endpoint:

```
curl -XPOST 'http://ip:port/cassandra/version/upgrade' -d '16'
```

Version 16 backfills message metadata in `messageIdTable` and `imapUidTable` for messages written by older
James versions. If you skip it, those older rows remain incomplete. This release still starts with an
otherwise supported, upgradable schema version and falls back to reading `messagev3` for affected metadata
and header requests, so those messages remain readable, but the fallback adds a Cassandra read. A future
James version may remove that fallback, so complete the migration before upgrading to such a version.

The request returns a `taskId`; check `GET /tasks/{taskId}` until the task completes before
considering the upgrade finished. Keep the node running during the task: a server restart aborts the
migration. See the
[James upgrade instructions](../../../../../../../james-project/upgrade-instructions.md).

The new version no longer writes `messagev3.bodyOctets`, `messageIdTable.flagUser` or
`imapUidTable.flagUser`. Dropping these columns is optional and only reclaims disk space as SSTables are
compacted. If needed, run the following CQL **after all nodes run 1.0.21.1**. It uses the packaged default
keyspace, `apache_james`; replace that name if your `cassandra.properties` sets another `cassandra.keyspace`:

```sql
ALTER TABLE apache_james.messagev3 DROP bodyOctets;
ALTER TABLE apache_james.messageIdTable DROP flagUser;
ALTER TABLE apache_james.imapUidTable DROP flagUser;
```

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

### S3 bucket sharding for Ceph/RADOS

S3-backed distributed deployments can split each logical bucket into physical buckets. This is **disabled
by default**. Configure `tmail.blobstore.shards` and, if needed,
`tmail.blobstore.shards.ommited.buckets` in `blob.properties`. The names and spelling are intentional.

For a new S3-backed deployment, for example:

```
tmail.blobstore.shards=256
tmail.blobstore.shards.ommited.buckets=jmap-uploads,mail-processing
```

Do not turn sharding on for an existing non-empty blob store without a separate data migration plan:
enabling it changes physical bucket names and makes existing blobs unreachable. Once in use, the shard
count and omitted-bucket list must remain fixed. Provision the required buckets and check the Ceph user
bucket limit before enabling it. See the [blob store documentation](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/blob-store.adoc).

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

Update the distributed server image to `linagora/tmail-backend:distributed-1.0.21.1`. Deployments using
AI features should use `linagora/tmail-backend:distributed-ai-1.0.21.1`. If running the migration proxy,
update it to `linagora/tmail-migration-proxy:1.0.21.1`.

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.21.1
