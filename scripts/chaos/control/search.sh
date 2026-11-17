#!/bin/bash
# A user searches their mails: Email/query with a full text filter (the search bar of the web client: OpenSearch) finds a
# mail by a word of its body. The mail: a fixture kept between runs (searching must work while none can be created); a
# new fixture is waited for until indexed.
. "$(dirname "$0")/lib.sh"

acc="$(account_id "$CHAOS_USER")"
fx="$(ensure_fixture search 2>&1)" || fail "$fx"
read -r id tok <<<"$fx"
err=""
while [ "$(remaining)" -gt 0 ]; do
  if resp="$(jmap "$CHAOS_USER" "[[\"Email/query\",{\"accountId\":\"$acc\",\"filter\":{\"text\":\"$tok\"},\"sort\":[{\"property\":\"receivedAt\",\"isAscending\":false}],\"limit\":20},\"q\"]]" 2>&1)"; then
    jq -e --arg id "$id" '.methodResponses[0][1].ids | index($id) != null' <<<"$resp" >/dev/null && ok "search found the mail"
    err="not found"
  else
    err="$resp"
  fi
  sleep 0.5
done
fail "search: $err"
