#!/bin/bash
# A mail client (Thunderbird...) sends a mail: a new SMTP submission connection (587, STARTTLS, AUTH: LDAP), to a
# colleague, who receives it in their Inbox.
. "$(dirname "$0")/lib.sh"

subject="chaos smtp submission $RUN_TOKEN"
out="$(py 'import os, sys, lib
def probe():
    lib.smtp_send(int(os.environ["CHAOS_SUBMISSION_PORT"]), os.environ["CHAOS_USER"], os.environ["CHAOS_USER2"],
                  sys.argv[1], "Submitted by the control scripts", login=(os.environ["CHAOS_USER"], os.environ["CHAOS_PASSWORD"]))
    return "submitted"
lib.main(probe)' "$subject")"; rc=$?
[ $rc = 0 ] || { echo "$out"; exit $rc; }
got="$(wait_in_mailbox "$CHAOS_USER2" inbox "$subject" 2>&1)" || fail "submitted, not received: $got"
destroy_emails "$CHAOS_USER2" "$got"
ok "submitted on $CHAOS_SUBMISSION_PORT, in $CHAOS_USER2's Inbox in $((SECONDS - START))s"
