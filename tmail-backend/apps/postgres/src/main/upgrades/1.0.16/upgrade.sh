#!/bin/bash
# Upgrade script for Twake Mail postgres 1.0.16
# Run this script BEFORE deploying the 1.0.16 version.
#
# Usage: bash /root/upgrades/1.0.16/upgrade.sh
#
# This script requires network access to PostgreSQL.
# It is safe to run multiple times (idempotent).

set -euo pipefail

CONF_FILE="/root/conf/postgres.properties"

echo "=== Twake Mail 1.0.16 upgrade script (PostgreSQL) ==="
echo ""

# ---------------------------------------------------------------------------
# Parse postgres.properties
# Handles the ${env:VAR:-default} interpolation format used in the file.
# Environment variables take precedence; the file provides the fallback.
# ---------------------------------------------------------------------------
resolve_prop() {
    local key="$1"
    local env_var="$2"
    local default="$3"

    if [[ -n "${!env_var:-}" ]]; then
        echo "${!env_var}"
        return
    fi

    local raw
    raw=$(grep -E "^${key}\s*=" "$CONF_FILE" 2>/dev/null | head -1 | cut -d'=' -f2- | tr -d '\r' | xargs || true)

    if [[ "$raw" =~ \$\{env:([A-Z_]+):-([^}]*)\} ]]; then
        local var_name="${BASH_REMATCH[1]}"
        local var_default="${BASH_REMATCH[2]}"
        echo "${!var_name:-$var_default}"
    else
        echo "${raw:-$default}"
    fi
}

DB_NAME=$(resolve_prop   "database.name"     "POSTGRES_DB"       "postgres")
DB_SCHEMA=$(resolve_prop "database.schema"   "POSTGRES_SCHEMA"   "public")
DB_HOST=$(resolve_prop   "database.host"     "POSTGRES_HOST"     "postgres")
DB_PORT=$(resolve_prop   "database.port"     "POSTGRES_PORT"     "5432")
DB_USER=$(resolve_prop   "database.username" "POSTGRES_USER"     "tmail")
DB_PASS=$(resolve_prop   "database.password" "POSTGRES_PASSWORD" "secret1")

echo "PostgreSQL host : $DB_HOST:$DB_PORT"
echo "Database        : $DB_NAME"
echo "Schema          : $DB_SCHEMA"
echo "User            : $DB_USER"
echo ""

export PGPASSWORD="$DB_PASS"

# ---------------------------------------------------------------------------
# Ensure psql is available
# ---------------------------------------------------------------------------
if ! command -v psql &>/dev/null; then
    echo "[INFO] psql not found — installing postgresql-client via apt..."
    apt-get update -qq && apt-get install -y --no-install-recommends postgresql-client
    echo "[INFO] psql installed."
fi

run_sql() {
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -c "$1"
}

# ---------------------------------------------------------------------------
# Wait for PostgreSQL to be reachable
# ---------------------------------------------------------------------------
echo "[INFO] Checking PostgreSQL connectivity..."
MAX_RETRIES=10
for i in $(seq 1 $MAX_RETRIES); do
    if run_sql "SELECT 1;" &>/dev/null; then
        echo "[INFO] PostgreSQL is reachable."
        break
    fi
    if [[ $i -eq $MAX_RETRIES ]]; then
        echo "[ERROR] Cannot reach PostgreSQL at $DB_HOST:$DB_PORT after $MAX_RETRIES attempts. Aborting."
        exit 1
    fi
    echo "[INFO] PostgreSQL not ready yet (attempt $i/$MAX_RETRIES), retrying in 5s..."
    sleep 5
done

echo ""

# ---------------------------------------------------------------------------
# Migration 1: Add read_only column to labels table
# ---------------------------------------------------------------------------
echo "--- Migration: add read_only column to labels table ---"
echo ""
echo "This is a schema-only change. It completes instantly and does not"
echo "require any data backfill. Existing rows will have NULL for this"
echo "column, which the application treats as false (not read-only)."
echo ""

echo "[INFO] Adding column 'read_only' to ${DB_SCHEMA}.labels (IF NOT EXISTS)..."
run_sql "ALTER TABLE ${DB_SCHEMA}.labels ADD COLUMN IF NOT EXISTS read_only BOOLEAN;"
echo "[OK] Column 'read_only' is present in ${DB_SCHEMA}.labels."

echo ""
echo "=== All migrations completed successfully. You may now deploy 1.0.16. ==="
