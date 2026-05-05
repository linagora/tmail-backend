# Postgres Twake Mail backend 1.0.18 release instructions

## Schema migration

### Postgres: new column on the domains table

A new JSONB column `signature_templates` has been added to the `domains` Postgres table to store
per-language email signature templates.

This column is added automatically at startup via the data definition migration mechanism. No manual
SQL migration is required.

If for some reason you need to apply it manually:

```sql
ALTER TABLE domains ADD COLUMN signature_templates jsonb;
```

## New features

### Domain-based signature templates (Optional)

- [WebAdmin API](../../../../../../../docs/modules/ROOT/pages/tmail-backend/webadmin.adoc#_domain_signature_templates)
- [Listener configuration](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/extra-listener.adoc#_domain_based_signature_engine)

## Tmail deployment

Please update your tmail-backend docker image to the following version: `linagora/tmail-backend:postgresql-1.0.18`

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.18
