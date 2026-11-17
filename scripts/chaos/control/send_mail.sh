#!/bin/bash
# A user sends a mail from the web client to a colleague: Email/set (in Drafts) then EmailSubmission/set with
# onSuccessUpdateEmail (moved to Sent, no longer a draft), as tmail-flutter; the colleague receives it in their Inbox.
. "$(dirname "$0")/lib.sh"

acc="$(account_id "$CHAOS_USER")"
subject="chaos jmap send $RUN_TOKEN"
resp="$(jmap "$CHAOS_USER" "[[\"Mailbox/get\",{\"accountId\":\"$acc\",\"properties\":[\"id\",\"role\"]},\"m\"],[\"Identity/get\",{\"accountId\":\"$acc\"},\"i\"]]" 2>&1)" \
  || fail "$resp"
drafts="$(jq -r '.methodResponses[0][1].list[] | select(.role == "drafts") | .id' <<<"$resp")"
sent="$(jq -r '.methodResponses[0][1].list[] | select(.role == "sent") | .id' <<<"$resp")"
identity="$(jq -r '.methodResponses[1][1].list[0].id // empty' <<<"$resp")"
[ -n "$drafts" ] && [ -n "$sent" ] && [ -n "$identity" ] || fail "no drafts / sent mailbox or no identity"

email="$(jq -nc --arg d "$drafts" --arg s "$subject" --arg f "$CHAOS_USER" --arg t "$CHAOS_USER2" --arg tok "$RUN_TOKEN" '{
  mailboxIds: {($d): true}, keywords: {"$draft": true, "$seen": true}, subject: $s,
  from: [{email: $f}], to: [{email: $t}],
  bodyValues: {b: {value: ("Sent by the control scripts " + $tok)}}, textBody: [{partId: "b", type: "text/plain"}]}')"
submission="$(jq -nc --arg i "$identity" --arg f "$CHAOS_USER" --arg t "$CHAOS_USER2" '{
  identityId: $i, emailId: "#e", envelope: {mailFrom: {email: $f}, rcptTo: [{email: $t}]}}')"
update="$(jq -nc --arg d "$drafts" --arg s "$sent" '{"#k": {("mailboxIds/" + $d): null, ("mailboxIds/" + $s): true, "keywords/$draft": null}}')"
resp="$(jmap "$CHAOS_USER" "[[\"Email/set\",{\"accountId\":\"$acc\",\"create\":{\"e\":$email}},\"c\"],
  [\"EmailSubmission/set\",{\"accountId\":\"$acc\",\"create\":{\"k\":$submission},\"onSuccessUpdateEmail\":$update},\"s\"]]" 2>&1)" \
  || fail "send: $resp"
errors="$(jset_errors "$resp")"
[ -z "$errors" ] || fail "send: $(head -c 200 <<<"$errors")"
sent_id="$(jq -r '.methodResponses[0][1].created.e.id' <<<"$resp")"
trap 'destroy_emails "$CHAOS_USER" "$sent_id"' EXIT

got="$(wait_in_mailbox "$CHAOS_USER2" inbox "$subject" 2>&1)" || fail "sent, not received: $got"
destroy_emails "$CHAOS_USER2" "$got"
ok "sent by JMAP, in $CHAOS_USER2's Inbox in $((SECONDS - START))s"
