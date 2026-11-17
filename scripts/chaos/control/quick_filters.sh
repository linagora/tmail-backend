#!/bin/bash
# A user applies the quick filters of a mailbox (tmail-flutter's mailbox_filter_builder: Unread, With attachments,
# Starred): Email/query inMailbox + notKeyword $seen / hasAttachment / hasKeyword $flagged (OpenSearch). An unread
# fixture of the Inbox with an attachment is in the first two, and not in the third.
. "$(dirname "$0")/lib.sh"

acc="$(account_id "$CHAOS_USER")"
fx="$(ensure_fixture quickfilter with-attachment 2>&1)" || fail "$fx"
read -r id tok <<<"$fx"
inbox="$(mailbox_id "$CHAOS_USER" inbox 2>&1)" || fail "$inbox"
query() {  # query <filter> <callId>
  echo "[\"Email/query\",{\"accountId\":\"$acc\",\"filter\":$1,\"sort\":[{\"property\":\"receivedAt\",\"isAscending\":false}],\"limit\":500},\"$2\"]"
}
calls="[$(query "{\"inMailbox\":\"$inbox\",\"notKeyword\":\"\$seen\"}" unread),
  $(query "{\"inMailbox\":\"$inbox\",\"hasAttachment\":true}" attachments),
  $(query "{\"inMailbox\":\"$inbox\",\"hasKeyword\":\"\$flagged\"}" starred)]"
err=""
while [ "$(remaining)" -gt 0 ]; do
  if resp="$(jmap "$CHAOS_USER" "$calls" 2>&1)"; then
    jq -e --arg id "$id" '.methodResponses | (.[0][1].ids | index($id) != null) and (.[1][1].ids | index($id) != null)
      and (.[2][1].ids | index($id) == null)' <<<"$resp" >/dev/null && ok "unread, with attachments, starred: as expected"
    err="the fixture is not where expected: $(jq -c --arg id "$id" '[.methodResponses[] | {(.[2]): (.[1].ids | index($id) != null)}] | add' <<<"$resp")"
  else
    err="$resp"
  fi
  sleep 0.5
done
fail "quick filters: $err"
