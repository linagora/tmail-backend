#!/bin/bash
#
# Starts the distributed stack of tmail-backend and the MPT MCP server, waits for tmail to be healthy, then creates
# the domain and the two accounts of the test bench (WebAdmin), and checks the MPT server answers.
#
set -euo pipefail
. "$(dirname "$0")/common.sh"

mkdir -p "$QA_RUN" "$MPT_ARCHIVE"
if [ ! -s "$QA_RUN/jwt_privatekey" ]; then
  openssl genrsa -out "$QA_RUN/jwt_privatekey" 4096 2>/dev/null
  openssl rsa -in "$QA_RUN/jwt_privatekey" -pubout -out "$QA_RUN/jwt_publickey" 2>/dev/null
fi
chmod 644 "$QA_RUN/jwt_privatekey" "$QA_RUN/jwt_publickey"

echo "==> Starting the stack as compose project '$QA_PROJECT' ($TMAIL_IMAGE)"
compose up -d

echo "==> Waiting for tmail (healthcheck)"
for i in $(seq 1 120); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$(compose ps -aq tmail)" 2>/dev/null || true)"
  state="$(docker inspect -f '{{.State.Status}}' "$(compose ps -aq tmail)" 2>/dev/null || true)"
  [ "$status" = healthy ] && break
  if [ "$i" = 120 ] || [ "$state" = exited ] || [ "$state" = dead ]; then
    echo "tmail is not healthy ($status, $state)" >&2
    compose logs --tail 120 tmail >&2
    exit 1
  fi
  sleep 5
done

echo "==> The domain and the accounts of the test bench"
WEBADMIN="http://127.0.0.1:$QA_WEBADMIN_PORT"
curl -sf -X PUT "$WEBADMIN/domains/$QA_DOMAIN" >/dev/null
for pair in "$QA_USER:$QA_PASSWORD" "$QA_USER2:$QA_PASSWORD2"; do
  curl -sf -X PUT "$WEBADMIN/users/${pair%%:*}" -H 'Content-Type: application/json' -d "{\"password\":\"${pair#*:}\"}" >/dev/null
done

echo "==> The MPT MCP server"
for i in $(seq 1 30); do
  curl -sf -o /dev/null -X POST "http://127.0.0.1:$QA_MPT_PORT/mcp" -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"start.sh","version":"1"}}}' && break
  [ "$i" = 30 ] && { echo "the MPT MCP server does not answer" >&2; compose logs --tail 50 mcp-mpt >&2; exit 1; }
  sleep 2
done
echo "==> Ready: MPT MCP http://127.0.0.1:$QA_MPT_PORT/mcp, WebAdmin $WEBADMIN, accounts $QA_USER and $QA_USER2"
