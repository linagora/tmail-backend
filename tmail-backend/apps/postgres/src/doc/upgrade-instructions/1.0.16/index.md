# Twake Mail PostgreSQL 1.0.16 — Upgrade Instructions

Run these steps **before** deploying the 1.0.16 version.

## Prerequisites

- Access to the PostgreSQL database used by the application.
- A SQL client (`psql` or equivalent) able to reach that database.

## Migrations

### 1. Add `description` column to the `labels` table

This is a schema-only change. It completes instantly and requires no data backfill.

```sql
ALTER TABLE <schema>.labels ADD COLUMN description VARCHAR;
```

Replace `<schema>` with the schema configured in `postgres.properties` (default: `public`).

### 2. Add `read_only` column to the `labels` table

This is a schema-only change. It completes instantly and requires no data backfill.
Existing rows will have `NULL` for this column, which the application treats as `false` (not read-only).

```sql
ALTER TABLE <schema>.labels ADD COLUMN IF NOT EXISTS read_only BOOLEAN;
```

### 3. Add `activated` column to the `domains` table

This is a schema-only change. It completes instantly and requires no data backfill.

```sql
ALTER TABLE <schema>.domains ADD COLUMN activated BOOLEAN;
```

**Important:** Existing domains without the `activated` field will be considered **unactivated**.
You must manually set existing domains to `activated = true` after this migration.

### 4. Add `can_upgrade` and `is_paying` columns to the `domains` table

This is a schema-only change. It completes instantly and requires no data backfill.

```sql
ALTER TABLE <schema>.domains ADD COLUMN can_upgrade BOOLEAN;
ALTER TABLE <schema>.domains ADD COLUMN is_paying BOOLEAN;
```

## Post-migration

Once all migrations above have completed successfully, you may proceed with the 1.0.16 deployment.
