#!/bin/bash
#
# Starts the chaos stack of Twake Mail: the backing services first (Cassandra's nodes join one after the other), the Redis
# Cluster created (3 masters, each node also the replica of its neighbour's master), then tmail, healthy, then checks
# that an account signs in through the SSO and reaches JMAP. Writes $CHAOS_RUN/env: what the control scripts read.
#
set -euo pipefail
. "$(dirname "$0")/common.sh"

mkdir -p "$CHAOS_RUN"
if [ ! -s "$CHAOS_RUN/jwt_privatekey" ]; then
  openssl genrsa -out "$CHAOS_RUN/jwt_privatekey" 4096 2>/dev/null
  openssl rsa -in "$CHAOS_RUN/jwt_privatekey" -pubout -out "$CHAOS_RUN/jwt_publickey" 2>/dev/null
fi
chmod 644 "$CHAOS_RUN/jwt_privatekey" "$CHAOS_RUN/jwt_publickey"

echo "==> The backing services, compose project '$CHAOS_PROJECT'"
INFRA=$(compose config --services | grep -vx tmail)
compose up -d $INFRA
wait_healthy 600 cassandra1 cassandra2 cassandra3 rabbitmq1 rabbitmq2 rabbitmq3 redis1 redis2 redis3

echo "==> The Redis Cluster"
# the masters once (no slot assigned yet), then the replicas reconciled on every start: a start interrupted between the two
# leaves the cluster `ok` without its replicas
if ! compose exec -T redis1 redis-cli -p 6379 cluster info 2>/dev/null | grep -q 'cluster_slots_assigned:16384'; then
  compose exec -T redis1 redis-cli --cluster create redis1:6379 redis2:6379 redis3:6379 --cluster-yes >/dev/null
  sleep 2
fi
# a restarted stack may have a 6379 left as a replica by a failover: its slots back first, every 6379 a master (bounded)
redis_rebalance
for i in $(seq 1 30); do
  masters=0
  for n in $REDIS_NODES; do [ "$(redis_role "$n" 6379)" = master ] && masters=$((masters + 1)); done
  [ "$masters" = 3 ] && break
  [ "$i" = 30 ] && { echo "the 6379 of every Redis node is not a master" >&2; compose exec -T redis1 redis-cli -p 6379 cluster nodes >&2; exit 1; }
  redis_rebalance
  sleep 2
done
for replica in $REDIS_NODES; do
  master="${REDIS_REPLICA_OF[$replica]}"
  [ "$(redis_role "$replica" 6380)" = slave ] && continue
  id="$(compose exec -T "$master" redis-cli -p 6379 cluster myid | tr -d '\r')"
  if compose exec -T redis1 redis-cli -p 6379 cluster nodes | grep -q "^[0-9a-f]* $replica:6380"; then
    compose exec -T "$replica" redis-cli -p 6380 cluster replicate "$id" >/dev/null   # known, not a replica yet
  else
    compose exec -T redis1 redis-cli --cluster add-node "$replica:6380" redis1:6379 --cluster-slave --cluster-master-id "$id" >/dev/null
  fi
done
for i in $(seq 1 30); do
  redis_healthy && break
  [ "$i" = 30 ] && { echo "the Redis Cluster is not in its layout" >&2; compose exec -T redis1 redis-cli -p 6379 cluster nodes >&2; exit 1; }
  sleep 2
done

echo "==> OpenSearch: the 3 nodes in the cluster"
for i in $(seq 1 120); do
  n="$(compose exec -T opensearch1 curl -s 'http://localhost:9200/_cluster/health' 2>/dev/null | sed -n 's/.*"number_of_nodes":\([0-9]*\).*/\1/p' || true)"
  [ "$n" = 3 ] && break
  [ "$i" = 120 ] && { echo "OpenSearch has ${n:-no} node(s)" >&2; compose logs --tail 60 opensearch1 >&2; exit 1; }
  sleep 3
done

echo "==> RabbitMQ: the 3 nodes in the cluster"
for i in $(seq 1 60); do
  n="$(compose exec -T rabbitmq1 rabbitmqctl -q cluster_status --formatter json 2>/dev/null | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["running_nodes"]))' 2>/dev/null || true)"
  [ "$n" = 3 ] && break
  [ "$i" = 60 ] && { echo "RabbitMQ has ${n:-no} running node(s)" >&2; exit 1; }
  sleep 3
done

# what the control scripts read (written before tmail: a failed start can still be investigated with them)
cat > "$CHAOS_RUN/env" <<EOF
export CHAOS_PROJECT=$CHAOS_PROJECT
export CHAOS_JMAP_URL=http://127.0.0.1:$CHAOS_JMAP_PORT
export CHAOS_WEBADMIN_URL=http://127.0.0.1:$CHAOS_WEBADMIN_PORT
export CHAOS_SMTP_HOST=127.0.0.1 CHAOS_SMTP_PORT=$CHAOS_SMTP_PORT CHAOS_SUBMISSION_PORT=$CHAOS_SUBMISSION_PORT
export CHAOS_IMAP_HOST=127.0.0.1 CHAOS_IMAP_PORT=$CHAOS_IMAP_PORT
export CHAOS_SSO_URL=http://127.0.0.1:$CHAOS_SSO_PORT CHAOS_SSO_HOST=$CHAOS_SSO_HOST
export CHAOS_OIDC_CLIENT=$CHAOS_OIDC_CLIENT CHAOS_OIDC_SECRET=$CHAOS_OIDC_SECRET
export CHAOS_DOMAIN=$CHAOS_DOMAIN CHAOS_USER=$CHAOS_USER CHAOS_USER2=$CHAOS_USER2 CHAOS_PASSWORD=$CHAOS_PASSWORD
EOF

echo "==> tmail ($TMAIL_IMAGE)"
compose up -d tmail
# On a fresh 3 node cluster, tmail's first start creates the keyspaces and tables: a schema change slower than the driver's
# request timeout (10 s) makes it exit (DriverTimeoutException in CassandraSessionModule), the tables half created. It is
# restarted (twice at most) and said loudly: a finding about the first start on a replicated cluster, not hidden here.
for attempt in 1 2 3; do
  deadline=$((SECONDS + 600))
  until [ "$(service_state tmail)" = healthy ] || [ $SECONDS -ge $deadline ]; do
    case "$(docker inspect -f '{{.State.Status}}' "$(compose ps -aq tmail)" 2>/dev/null)" in exited|dead) break ;; esac
    sleep 3
  done
  [ "$(service_state tmail)" = healthy ] && break
  if [ "$attempt" = 3 ] || [ $SECONDS -ge $deadline ]; then
    echo "tmail is not healthy ($(service_state tmail))" >&2
    compose logs --tail 120 tmail >&2
    exit 1
  fi
  echo "!!! tmail exited during its start (attempt $attempt): $(compose logs --tail 400 tmail 2>&1 | grep -oE '[A-Za-z]+(Exception|Error): [^|]{0,160}' | tail -1)"
  echo "!!! restarting it (FINDING: tmail's first start on a fresh replicated cluster is not resilient)"
  compose start tmail >/dev/null
done


echo "==> An account signs in (SSO, password grant) and reaches JMAP"
for i in $(seq 1 40); do
  token="$(curl -s -H "Host: $CHAOS_SSO_HOST" -u "$CHAOS_OIDC_CLIENT:$CHAOS_OIDC_SECRET" \
    -d grant_type=password -d "username=$CHAOS_USER" -d "password=$CHAOS_PASSWORD" -d 'scope=openid email profile' \
    "http://127.0.0.1:$CHAOS_SSO_PORT/oauth2/token" | sed -n 's/.*"access_token" *: *"\([^"]*\)".*/\1/p' || true)"
  [ -n "$token" ] && curl -sf -o /dev/null -H "Authorization: Bearer $token" -H 'Accept: application/json; jmapVersion=rfc-8621' \
    "http://127.0.0.1:$CHAOS_JMAP_PORT/jmap/session" && break
  [ "$i" = 40 ] && { echo "$CHAOS_USER cannot reach JMAP through the SSO" >&2; exit 1; }
  sleep 3
done
echo "==> Ready: JMAP http://127.0.0.1:$CHAOS_JMAP_PORT, WebAdmin http://127.0.0.1:$CHAOS_WEBADMIN_PORT, SMTP $CHAOS_SMTP_PORT, IMAP $CHAOS_IMAP_PORT, SSO $CHAOS_SSO_PORT ($CHAOS_RUN/env)"
