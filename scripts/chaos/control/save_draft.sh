#!/bin/bash
# A user saves a draft (Email/set create in Drafts, $draft, as the composer does) and opens it again: its body back.
. "$(dirname "$0")/lib.sh"

acc="$(account_id "$CHAOS_USER")"
drafts="$(mailbox_id "$CHAOS_USER" drafts 2>&1)" || fail "$drafts"
id="$(create_email "$CHAOS_USER" "$drafts" "chaos draft $RUN_TOKEN" "A draft $RUN_TOKEN" '{"$draft":true,"$seen":true}' 2>&1)" \
  || fail "save the draft: $id"
trap 'destroy_emails "$CHAOS_USER" "$id"' EXIT
resp="$(jmap "$CHAOS_USER" "[[\"Email/get\",{\"accountId\":\"$acc\",\"ids\":[\"$id\"],\"properties\":[\"keywords\",\"mailboxIds\",\"bodyValues\",\"textBody\"],\"fetchTextBodyValues\":true},\"g\"]]" 2>&1)" \
  || fail "open the draft: $resp"
jq -e --arg t "$RUN_TOKEN" --arg d "$drafts" '.methodResponses[0][1].list[0] | (.keywords["$draft"] == true) and (.mailboxIds[$d] == true)
  and ([.bodyValues[]?.value] | any(contains($t)))' <<<"$resp" >/dev/null || fail "the draft read back is not the one saved"
ok "draft saved and read back"
