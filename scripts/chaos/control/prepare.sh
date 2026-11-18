#!/bin/bash
# Before a fault (topology.yaml app.prepare): fresh bearer tokens for the accounts of the controls, valid 1 h, so that
# during the fault the controls hold a token taken before it (a web client signed in before an LDAP or SSO outage).
. "$(dirname "$0")/lib.sh"
for user in "$CHAOS_USER" "$CHAOS_USER2"; do
  rm -f "$STATE_DIR/token.$user"
  token "$user" >/dev/null || broken "no token for $user before the fault"
done
ok "fresh tokens for $CHAOS_USER and $CHAOS_USER2"
