#!/bin/bash
# A user stars a mail and opens the "Starred" section (Email/query hasKeyword $flagged across mailboxes, as
# tmail-flutter: OpenSearch): the mail is there; unstarred, it leaves it. Each run flips the star of a fixture and polls
# until the section follows (the index updated).
. "$(dirname "$0")/lib.sh"

acc="$(account_id "$CHAOS_USER")"
fx="$(ensure_fixture flagged 2>&1)" || fail "$fx"
read -r id tok <<<"$fx"
resp="$(jmap "$CHAOS_USER" "[[\"Email/get\",{\"accountId\":\"$acc\",\"ids\":[\"$id\"],\"properties\":[\"keywords\"]},\"g\"]]" 2>&1)" || fail "$resp"
if jq -e '.methodResponses[0][1].list[0].keywords["$flagged"] == true' <<<"$resp" >/dev/null; then new=null want=false; else new=true want=true; fi
resp="$(jmap "$CHAOS_USER" "[[\"Email/set\",{\"accountId\":\"$acc\",\"update\":{\"$id\":{\"keywords/\$flagged\":$new}}},\"u\"]]" 2>&1)" \
  || fail "star the mail: $resp"
[ -z "$(jset_errors "$resp")" ] || fail "star the mail: $(jset_errors "$resp" | head -c 200)"
err=""
while [ "$(remaining)" -gt 0 ]; do
  if resp="$(jmap "$CHAOS_USER" "[[\"Email/query\",{\"accountId\":\"$acc\",\"filter\":{\"hasKeyword\":\"\$flagged\"},\"sort\":[{\"property\":\"receivedAt\",\"isAscending\":false}],\"limit\":500},\"q\"]]" 2>&1)"; then
    [ "$(jq --arg id "$id" '.methodResponses[0][1].ids | index($id) != null' <<<"$resp")" = "$want" ] \
      && ok "$( [ $want = true ] && echo starred, in || echo unstarred, out of ) the Starred section in $((SECONDS - START))s"
    err="the Starred section does not follow (the mail $( [ $want = true ] && echo starred || echo unstarred ))"
  else
    err="$resp"
  fi
  sleep 0.5
done
fail "$err"
