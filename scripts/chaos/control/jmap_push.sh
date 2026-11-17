#!/bin/bash
# Realtime in the web client: its JMAP WebSocket with push enabled (RFC 8887, as tmail-flutter) receives a StateChange
# of Email when a mail of the user changes (starred by another client). The mail: a fixture kept between runs.
. "$(dirname "$0")/lib.sh"

fx="$(ensure_fixture push 2>&1)" || fail "$fx"
read -r id tok <<<"$fx"
bearer="$(token "$CHAOS_USER" 2>&1)" || fail "$bearer"
py 'import sys, lib
lib.main(lambda: lib.ws_push(sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])))' \
  "$bearer" "$(account_id "$CHAOS_USER")" "$id" "$(( $(remaining) - 5 ))"
