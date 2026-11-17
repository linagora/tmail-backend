#!/bin/bash
# A signed-in user opens the web client: the mailbox list (Mailbox/get, with the counters), the system mailboxes there.
. "$(dirname "$0")/lib.sh"

acc="$(account_id "$CHAOS_USER")"
resp="$(jmap "$CHAOS_USER" "[[\"Mailbox/get\",{\"accountId\":\"$acc\",\"properties\":[\"id\",\"name\",\"role\",\"totalEmails\",\"unreadEmails\",\"myRights\"]},\"m\"]]" 2>&1)" \
  || fail "Mailbox/get: $resp"
for role in inbox sent drafts trash; do
  jq -e --arg r "$role" '.methodResponses[0][1].list | any(.role == $r and (.totalEmails | type) == "number")' <<<"$resp" >/dev/null \
    || fail "Mailbox/get: no $role mailbox with its counters"
done
ok "$(jq '.methodResponses[0][1].list | length' <<<"$resp") mailboxes listed"
