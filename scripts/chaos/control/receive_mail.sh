#!/bin/bash
# A mail from the outside reaches a user: a new SMTP connection (25, no auth, the recipient checked: LDAP), the mail
# queued, processed and delivered in the user's Inbox, seen there by JMAP.
. "$(dirname "$0")/lib.sh"

subject="chaos inbound $RUN_TOKEN"
out="$(py 'import os, sys, lib
def probe():
    lib.smtp_send(int(os.environ["CHAOS_SMTP_PORT"]), "someone@external.test", os.environ["CHAOS_USER"], sys.argv[1],
                  "Received by the control scripts")
    return "accepted"
lib.main(probe)' "$subject")"; rc=$?
[ $rc = 0 ] || { echo "$out"; exit $rc; }
got="$(wait_in_mailbox "$CHAOS_USER" inbox "$subject" 2>&1)" || fail "accepted by SMTP, not delivered: $got"
destroy_emails "$CHAOS_USER" "$got"
ok "received by SMTP, in the Inbox in $((SECONDS - START))s"
