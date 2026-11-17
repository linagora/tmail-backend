#!/bin/bash
# A spam from the outside is caught: a mail with the GTUBE pattern received by SMTP (25) is scanned by Rspamd
# (RspamdScanner, local-delivery) and filed in the user's Spam mailbox, its Rspamd verdict in its headers. Rspamd not
# checking it, it lands in the Inbox (onMailetException: ignore).
. "$(dirname "$0")/lib.sh"

subject="chaos spam $RUN_TOKEN"
out="$(py 'import os, sys, lib
GTUBE = "XJS*C4JDBQADN1.NSBN3*2IDNEN*GTUBE-STANDARD-ANTI-UBE-TEST-EMAIL*C.34X"
def probe():
    lib.smtp_send(int(os.environ["CHAOS_SMTP_PORT"]), "spammer@external.test", os.environ["CHAOS_USER"], sys.argv[1],
                  "Cheap pills\r\n\r\n" + GTUBE)
    return "accepted"
lib.main(probe)' "$subject")"; rc=$?
[ $rc = 0 ] || { echo "$out"; exit $rc; }

acc="$(account_id "$CHAOS_USER")"
resp="$(jmap "$CHAOS_USER" "[[\"Mailbox/get\",{\"accountId\":\"$acc\",\"properties\":[\"id\",\"name\",\"role\"]},\"m\"]]" 2>&1)" || fail "$resp"
inbox="$(jq -r '.methodResponses[0][1].list[] | select(.role == "inbox") | .id' <<<"$resp")"
# the Spam mailbox: created by the first spam delivered
spam_box() { jmap "$CHAOS_USER" "[[\"Mailbox/get\",{\"accountId\":\"$acc\",\"properties\":[\"id\",\"name\",\"role\"]},\"m\"]]" 2>/dev/null \
  | jq -r '.methodResponses[0][1].list[] | select(.role == "junk" or .name == "Spam") | .id' | head -1; }

found="" where="" err=""
while [ "$(remaining)" -gt 0 ]; do
  spam="$(spam_box)"
  if [ -n "$spam" ] && ids="$(find_in_mailbox "$CHAOS_USER" "$spam" "$RUN_TOKEN" 2>&1)" && [ -n "$ids" ]; then found="$(head -1 <<<"$ids")" where=Spam; break; fi
  if ids="$(find_in_mailbox "$CHAOS_USER" "$inbox" "$RUN_TOKEN" 2>&1)"; then
    [ -n "$ids" ] && { found="$(head -1 <<<"$ids")" where=Inbox; break; }
  else err="$ids"; fi
  sleep 0.5
done
[ -n "$found" ] || fail "the spam is not delivered after ${BUDGET}s${err:+ ($err)}"
trap 'destroy_emails "$CHAOS_USER" "$found"' EXIT
verdict="$(jmap "$CHAOS_USER" "[[\"Email/get\",{\"accountId\":\"$acc\",\"ids\":[\"$found\"],\"properties\":[\"header:org.apache.james.rspamd.status:asText\"]},\"g\"]]" 2>/dev/null \
  | jq -r '.methodResponses[0][1].list[0]["header:org.apache.james.rspamd.status:asText"] // empty')"
[ "$where" = Spam ] || fail "the spam is delivered in the Inbox: not checked (Rspamd verdict: ${verdict:-none})"
ok "the spam is filed in Spam (Rspamd: ${verdict:-no header})"
