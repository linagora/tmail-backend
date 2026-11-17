#!/bin/bash
# A client signs in with Basic authentication (user, password: checked in LDAP by tmail) and gets its JMAP session.
. "$(dirname "$0")/lib.sh"

resp="$(curl -s -m "$(ct)" -w '\n%{http_code}' -u "$CHAOS_USER:$CHAOS_PASSWORD" -H "$JMAP_ACCEPT" "$CHAOS_JMAP_URL/jmap/session")"
code="${resp##*$'\n'}"
[ "$code" = 200 ] || fail "JMAP session with Basic auth: HTTP ${code:-timeout}"
[ "$(jq -r .username <<<"${resp%$'\n'*}" 2>/dev/null)" = "$CHAOS_USER" ] || fail "JMAP session: not $CHAOS_USER's"
ok "Basic auth, JMAP session of $CHAOS_USER"
