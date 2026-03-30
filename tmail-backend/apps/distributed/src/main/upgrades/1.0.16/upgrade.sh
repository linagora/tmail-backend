#!/bin/bash
# Upgrade script for Twake Mail distributed 1.0.16
# Run this script BEFORE deploying the 1.0.16 version.
#
# Usage: bash /root/upgrades/1.0.16/upgrade.sh
#
# This script requires network access to Cassandra.
# It is safe to run multiple times (idempotent).

set -euo pipefail

CONF_FILE="/root/conf/cassandra.properties"

echo "=== Twake Mail 1.0.16 upgrade script (Distributed/Cassandra) ==="
echo ""

# ---------------------------------------------------------------------------
# Parse cassandra.properties (uncommented lines only)
# ---------------------------------------------------------------------------
get_prop() {
    local key="$1"
    local default="${2:-}"
    local value
    value=$(grep -E "^${key}\s*=" "$CONF_FILE" 2>/dev/null | head -1 | cut -d'=' -f2- | tr -d '\r ')
    echo "${value:-$default}"
}

NODES=$(get_prop "cassandra.nodes" "cassandra")
KEYSPACE=$(get_prop "cassandra.keyspace" "apache_james")
CASS_USER=$(get_prop "cassandra.user" "")
CASS_PASSWORD=$(get_prop "cassandra.password" "")

# Use first node; strip port if included (e.g. "cassandra:9042")
HOST=$(echo "$NODES" | cut -d',' -f1 | cut -d':' -f1 | xargs)
NODE_PORT=$(echo "$NODES" | cut -d',' -f1 | awk -F: '{print $2}' | xargs)
PORT="${NODE_PORT:-9042}"

echo "Cassandra host : $HOST:$PORT"
echo "Keyspace       : $KEYSPACE"
echo ""

# ---------------------------------------------------------------------------
# Ensure Python3 + cqlsh are available
# ---------------------------------------------------------------------------
if ! command -v cqlsh &>/dev/null; then
    echo "[INFO] cqlsh not found — installing Python3 + cqlsh via apt..."
    apt-get update -qq
    apt-get install -y --no-install-recommends python3 python3-pip
    pip3 install --quiet cqlsh
    echo "[INFO] cqlsh installed."
fi

# ---------------------------------------------------------------------------
# Build cqlsh connection args
# ---------------------------------------------------------------------------
CQLSH_ARGS=("$HOST" "$PORT" "--keyspace=$KEYSPACE")
[[ -n "$CASS_USER"     ]] && CQLSH_ARGS+=("-u" "$CASS_USER")
[[ -n "$CASS_PASSWORD" ]] && CQLSH_ARGS+=("-p" "$CASS_PASSWORD")

run_cql() {
    cqlsh "${CQLSH_ARGS[@]}" -e "$1"
}

# ---------------------------------------------------------------------------
# Wait for Cassandra to be reachable
# ---------------------------------------------------------------------------
echo "[INFO] Checking Cassandra connectivity..."
MAX_RETRIES=10
for i in $(seq 1 $MAX_RETRIES); do
    if run_cql "SELECT now() FROM system.local;" &>/dev/null; then
        echo "[INFO] Cassandra is reachable."
        break
    fi
    if [[ $i -eq $MAX_RETRIES ]]; then
        echo "[ERROR] Cannot reach Cassandra at $HOST:$PORT after $MAX_RETRIES attempts. Aborting."
        exit 1
    fi
    echo "[INFO] Cassandra not ready yet (attempt $i/$MAX_RETRIES), retrying in 5s..."
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

COLUMN_EXISTS=$(run_cql "SELECT column_name FROM system_schema.columns WHERE keyspace_name='$KEYSPACE' AND table_name='labels' AND column_name='read_only';" \
    | grep -c "read_only" || true)

if [[ "$COLUMN_EXISTS" -gt 0 ]]; then
    echo "[OK] Column 'read_only' already exists in labels table. Nothing to do."
else
    echo "[INFO] Adding column 'read_only' to labels table..."
    run_cql "ALTER TABLE labels ADD read_only boolean;"
    echo "[OK] Column 'read_only' successfully added to labels table."
fi

echo ""
echo "=== All migrations completed successfully. You may now deploy 1.0.16. ==="
