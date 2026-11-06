# Postgres Twake Mail backend 1.0.23 release instructions

These instructions cover changes since 1.0.22.

## Behaviour changes

### Search: scanning to Lucene

`search.properties` accepts `implementation=lucene`: mails are indexed into an embedded Lucene index under
`/root/var/store/lucene` (`var/store/lucene` of the working directory). Unlike `scanning`, it supports
`SearchSnippet/get`, which no longer fails with `serverFail` "not implemented".

The `search.properties` packaged in the image now sets `implementation=lucene` instead of `scanning`. Deployments
that do not mount their own `search.properties` switch to Lucene on upgrade:

- The index starts empty. Messages received before the upgrade are not found by searches until you reindex all
  mails with the WebAdmin
  [reindexing task](https://james.apache.org/server/manage-webadmin.html#ReIndexing_all_mails):
  `curl -XPOST http://ip:port/mailboxes?task=reIndex`.
- Mount `/root/var/store/lucene` on a persistent volume. Otherwise the index is lost, and must be rebuilt, whenever
  the container is recreated (image upgrade, `docker compose down`, Kubernetes pod restart).
- Index changes are written to disk when Twake Mail stops cleanly: after a crash, reindex all mails again.
- The Lucene index is local to one instance. With several Twake Mail nodes, mount a `search.properties` with
  `implementation=opensearch` (see `sample-configuration/distributed/search.properties`).

To keep the previous behaviour, mount a `search.properties` with `implementation=scanning`.
