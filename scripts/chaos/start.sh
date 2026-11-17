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
if ! compose exec -T redis1 redis-cli -p 6379 cluster info 2>/dev/null | grep -q 'cluster_state:ok'; then
  compose exec -T redis1 redis-cli --cluster create redis1:6379 redis2:6379 redis3:6379 --cluster-yes >/dev/null
  sleep 2
  for pair in redis1:redis2 redis2:redis3 redis3:redis1; do  # <replica node>:<the node whose master it copies>
    replica="${pair%%:*}" master="${pair#*:}"
    id="$(compose exec -T "$master" redis-cli -p 6379 cluster myid | tr -d '\r')"
    compose exec -T redis1 redis-cli --cluster add-node "$replica:6380" redis1:6379 --cluster-slave --cluster-master-id "$id" >/dev/null
  done
  for i in $(seq 1 30); do
    [ "$(compose exec -T redis1 redis-cli -p 6379 cluster nodes | grep -c slave)" = 3 ] && \
      compose exec -T redis1 redis-cli -p 6379 cluster info | grep -q 'cluster_state:ok' && break
    [ "$i" = 30 ] && { echo "the Redis Cluster is not ok" >&2; compose exec -T redis1 redis-cli -p 6379 cluster nodes >&2; exit 1; }
    sleep 2
  done
fi

echo "==> OpenSearch: the 3 nodes in the cluster"
for i in $(seq 1 120); do
  n="$(compose exec -T opensearch1 curl -s 'http://localhost:9200/_cluster/health' 2>/dev/null | sed -n 's/.*"number_of_nodes":\([0-9]*\).*/\1/p')"
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
wait_healthy 600 tmail


echo "==> An account signs in (SSO, password grant) and reaches JMAP"
for i in $(seq 1 40); do
  token="$(curl -s -H "Host: $CHAOS_SSO_HOST" -u "$CHAOS_OIDC_CLIENT:$CHAOS_OIDC_SECRET" \
    -d grant_type=password -d "username=$CHAOS_USER" -d "password=$CHAOS_PASSWORD" -d 'scope=openid email profile' \
    "http://127.0.0.1:$CHAOS_SSO_PORT/oauth2/token" | sed -n 's/.*"access_token" *: *"\([^"]*\)".*/\1/p')"
  [ -n "$token" ] && curl -sf -o /dev/null -H "Authorization: Bearer $token" -H 'Accept: application/json; jmapVersion=rfc-8621' \
    "http://127.0.0.1:$CHAOS_JMAP_PORT/jmap/session" && break
  [ "$i" = 40 ] && { echo "$CHAOS_USER cannot reach JMAP through the SSO" >&2; exit 1; }
  sleep 3
done
echo "==> Ready: JMAP http://127.0.0.1:$CHAOS_JMAP_PORT, WebAdmin http://127.0.0.1:$CHAOS_WEBADMIN_PORT, SMTP $CHAOS_SMTP_PORT, IMAP $CHAOS_IMAP_PORT, SSO $CHAOS_SSO_PORT ($CHAOS_RUN/env)"
