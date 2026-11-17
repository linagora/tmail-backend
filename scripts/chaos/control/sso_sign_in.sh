#!/bin/bash
# A user signs in through the SSO: a new access token from LemonLDAP (its password checked in LDAP), then the JMAP session
# with it, as the web client's first call: tmail introspects this token, never seen before.
. "$(dirname "$0")/lib.sh"

fresh="$(sso_token_fresh "$CHAOS_USER" 2>&1)" || fail "SSO sign-in of $CHAOS_USER: $fresh"
tok="${fresh#* }"
resp="$(curl -s -m "$(ct)" -w '\n%{http_code}' -H "Authorization: Bearer $tok" -H "$JMAP_ACCEPT" "$CHAOS_JMAP_URL/jmap/session")"
code="${resp##*$'\n'}"
[ "$code" = 200 ] || fail "JMAP session with a new SSO token: HTTP ${code:-timeout}"
[ "$(jq -r .username <<<"${resp%$'\n'*}" 2>/dev/null)" = "$CHAOS_USER" ] || fail "JMAP session: not $CHAOS_USER's"
ok "new SSO token introspected, JMAP session of $CHAOS_USER"
