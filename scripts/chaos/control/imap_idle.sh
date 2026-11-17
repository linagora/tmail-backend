#!/bin/bash
# Realtime in a mail client: an IMAP session in IDLE on the INBOX is notified (`* n FETCH (FLAGS ...)`) when another
# client (the web client, JMAP) stars a mail of it. The mail: a fixture kept between runs.
. "$(dirname "$0")/lib.sh"

fx="$(ensure_fixture idle 2>&1)" || fail "$fx"
read -r id tok <<<"$fx"
bearer="$(token "$CHAOS_USER" 2>&1)" || fail "$bearer"
py 'import sys, lib
lib.main(lambda: lib.imap_realtime("idle", sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])))' \
  "$bearer" "$(account_id "$CHAOS_USER")" "$id" "$(( $(remaining) - 5 ))"
