# Distributed Twake Mail backend 1.0.18 release instructions

## Schema migration

### Cassandra: new columns on the domains table

Two new frozen map columns have been added to the `domains` Cassandra table to store per-language
email signature templates.

These columns are added automatically at startup via the data definition migration mechanism. No
manual CQL migration is required.

If for some reason you need to apply them manually:

```cql
ALTER TABLE domains ADD signature_text_per_language frozen<map<text, text>>;
ALTER TABLE domains ADD signature_html_per_language frozen<map<text, text>>;
```

## New features

### Domain-based signature templates (Optional)

- [WebAdmin API](../../../../../../../docs/modules/ROOT/pages/tmail-backend/webadmin.adoc#_domain_signature_templates)
- [Listener configuration](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/extra-listener.adoc#_domain_based_signature_engine)

## Tmail deployment

Please update your tmail-backend docker image to the following version: `linagora/tmail-backend:distributed-1.0.18`

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.18
