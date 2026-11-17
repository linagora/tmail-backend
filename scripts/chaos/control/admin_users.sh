#!/bin/bash
# An administrator manages the accounts through WebAdmin: the domains (GET /domains) and the users (GET /users, the
# LDAP's) listed, the user there.
. "$(dirname "$0")/lib.sh"

resp="$(curl -s -m "$(ct)" -w '\n%{http_code}' "$CHAOS_WEBADMIN_URL/domains")"
[ "${resp##*$'\n'}" = 200 ] || fail "GET /domains: HTTP ${resp##*$'\n'}"
jq -e --arg d "$CHAOS_DOMAIN" 'index($d) != null' <<<"${resp%$'\n'*}" >/dev/null || fail "GET /domains: no $CHAOS_DOMAIN"
resp="$(curl -s -m "$(ct)" -w '\n%{http_code}' "$CHAOS_WEBADMIN_URL/users")"
[ "${resp##*$'\n'}" = 200 ] || fail "GET /users: HTTP ${resp##*$'\n'}: $(head -c 150 <<<"${resp%$'\n'*}")"
jq -e --arg u "$CHAOS_USER" 'any(.[]; .username == $u)' <<<"${resp%$'\n'*}" >/dev/null || fail "GET /users: no $CHAOS_USER"
ok "domains and $(jq length <<<"${resp%$'\n'*}") users listed"
