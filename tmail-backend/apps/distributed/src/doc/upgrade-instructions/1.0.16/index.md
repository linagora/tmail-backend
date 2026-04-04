# Twake Mail Distributed 1.0.16 — Upgrade Instructions

Run these steps **before** deploying the 1.0.16 version.

## Prerequisites

- Access to the Cassandra cluster used by the application.
- A CQL client (`cqlsh` or equivalent) able to reach that cluster.

## Migrations

### 1. Add `description` column to the `labels` table

This is a schema-only change. It completes instantly and requires no data backfill.

```cql
ALTER TABLE <keyspace>.labels ADD description text;
```

Replace `<keyspace>` with the keyspace configured in `cassandra.properties` (default: `apache_james`).

### 2. Add `read_only` column to the `labels` table

This is a schema-only change. It completes instantly and requires no data backfill.
Existing rows will have `NULL` for this column, which the application treats as `false` (not read-only).

```cql
ALTER TABLE <keyspace>.labels ADD read_only boolean;
```

### 3. Add `activated` column to the `domains` table

This is a schema-only change. It completes instantly and requires no data backfill.

```cql
ALTER TABLE <keyspace>.domains ADD activated boolean;
```

**Important:** Existing domains without the `activated` field will be considered **unactivated**.
You must manually set existing domains to `activated = true` after this migration.

### 4. Add `can_upgrade` and `is_paying` columns to the `domains` table

This is a schema-only change. It completes instantly and requires no data backfill.

```cql
ALTER TABLE <keyspace>.domains ADD can_upgrade boolean;
ALTER TABLE <keyspace>.domains ADD is_paying boolean;
```

## RabbitMQ cleanup

### LLM Mail Classifier Listener renamed

The listener `LlmMailPrioritizationClassifierListener` has been renamed to `LlmMailBackendClassifierListener`
(`com.linagora.tmail.listener.rag.LlmMailBackendClassifierListener`). It is registered automatically via
dependency injection — no `listeners.xml` change is required.

After deploying, delete the old RabbitMQ queue to avoid resource leaks and prevent the old listener from
processing stale messages:

```
mailboxEvent-workQueue-com.linagora.tmail.listener.rag.LlmMailPrioritizationClassifierListener$LlmMailPrioritizationClassifierGroup
```

## Post-migration

Once all migrations above have completed successfully, you may proceed with the 1.0.16 deployment.
