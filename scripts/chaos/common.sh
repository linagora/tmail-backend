# Shared by build.sh, start.sh, stop.sh and the control scripts of the chaos stack of Twake Mail. Not run on its own.
CHAOS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# the checkout under test: the one this folder is in (CHAOS_REPO_DIR: another, e.g. the worktree of twake-chaos)
REPO_DIR="${CHAOS_REPO_DIR:-$(cd "$CHAOS_DIR/../.." && pwd)}"
export CHAOS_PROJECT="${CHAOS_PROJECT:-tmail-chaos}"
export CHAOS_JMAP_PORT="${CHAOS_JMAP_PORT:-19700}"
export CHAOS_WEBADMIN_PORT="${CHAOS_WEBADMIN_PORT:-19701}"
export CHAOS_SMTP_PORT="${CHAOS_SMTP_PORT:-19702}"
export CHAOS_SUBMISSION_PORT="${CHAOS_SUBMISSION_PORT:-19703}"
export CHAOS_IMAP_PORT="${CHAOS_IMAP_PORT:-19704}"
export CHAOS_SSO_PORT="${CHAOS_SSO_PORT:-19705}"
# the jwt keys of tmail, between start and stop
export CHAOS_RUN="${CHAOS_RUN:-${XDG_CACHE_HOME:-$HOME/.cache}/twake-chaos/$CHAOS_PROJECT}"
BUILT_IMAGE="$CHAOS_PROJECT-tmail:latest"
export TMAIL_IMAGE="${TMAIL_IMAGE:-$BUILT_IMAGE}"
# the accounts (LDAP seed, conf/ldap/10-seed.ldif): password "secret"
export CHAOS_DOMAIN=tmail.com
export CHAOS_USER=alice@tmail.com CHAOS_USER2=bob@tmail.com CHAOS_PASSWORD=secret
# the SSO: LemonLDAP's OIDC client "james" (password grant allowed for the control scripts)
export CHAOS_OIDC_CLIENT=james CHAOS_OIDC_SECRET=james CHAOS_SSO_HOST=sso.example.com

compose() {
  docker compose -p "$CHAOS_PROJECT" -f "$CHAOS_DIR/compose.yaml" "$@"
}

# health of a service's container: healthy, starting, unhealthy, running (no healthcheck), exited...
service_state() {
  local id
  id="$(compose ps -aq "$1" 2>/dev/null | head -1)"
  [ -n "$id" ] || { echo missing; return; }
  docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null || echo missing
}

# The Redis Cluster's intended layout: on each node redisN, 6379 is the master of a third of the slots and 6380 the replica
# of the next node's master (REDIS_REPLICA_OF: redis1:6380 copies redis2's master...).
REDIS_NODES="redis1 redis2 redis3"
declare -A REDIS_REPLICA_OF=([redis1]=redis2 [redis2]=redis3 [redis3]=redis1)

redis_role() {  # redis_role <node> <port>: master / slave / nothing when unreachable
  compose exec -T "$1" redis-cli -p "$2" role 2>/dev/null | head -1 | tr -d '\r'
}

# The layout restored and whole: every 6379 a master owning slots with its replica online, every 6380 a replica whose
# link is up, the cluster ok. After a failover (a node stopped, its replica promoted) this fails until redis_rebalance.
redis_healthy() {
  local n
  compose exec -T redis1 redis-cli -p 6379 cluster info 2>/dev/null | grep -q 'cluster_state:ok' || return 1
  for n in $REDIS_NODES; do
    [ "$(redis_role "$n" 6379)" = master ] || return 1
    compose exec -T "$n" redis-cli -p 6379 cluster nodes 2>/dev/null | grep 'myself,master' | grep -qE ' [0-9]+-[0-9]+' || return 1
    compose exec -T "$n" redis-cli -p 6379 info replication 2>/dev/null | grep -q '^connected_slaves:1' || return 1
    [ "$(redis_role "$n" 6380)" = slave ] || return 1
    compose exec -T "$n" redis-cli -p 6380 info replication 2>/dev/null | grep -q '^master_link_status:up' || return 1
    # the replica copies the intended neighbour's master (by node id: master_host is an IP)
    [ "$(compose exec -T "$n" redis-cli -p 6380 cluster nodes 2>/dev/null | awk '/myself,slave/ {print $4}')" = \
      "$(compose exec -T "${REDIS_REPLICA_OF[$n]}" redis-cli -p 6379 cluster myid 2>/dev/null | tr -d '\r')" ] || return 1
  done
}

# Back to the intended layout after failovers: a 6379 that came back as a replica takes its slots back (CLUSTER FAILOVER,
# with its master's agreement, no data lost). Idempotent.
redis_rebalance() {
  local n
  for n in $REDIS_NODES; do
    if [ "$(redis_role "$n" 6379)" = slave ]; then
      compose exec -T "$n" redis-cli -p 6379 cluster failover >/dev/null 2>&1 || true
    fi
  done
}

wait_healthy() {  # wait_healthy <seconds> <service>...
  local timeout="$1"; shift
  local deadline=$((SECONDS + timeout)) s
  for s in "$@"; do
    until [ "$(service_state "$s")" = healthy ]; do
      if [ $SECONDS -ge $deadline ]; then
        echo "$s is not healthy after ${timeout}s ($(service_state "$s"))" >&2
        compose logs --tail 80 "$s" >&2
        return 1
      fi
      sleep 3
    done
  done
}
