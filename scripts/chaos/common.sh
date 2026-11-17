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
