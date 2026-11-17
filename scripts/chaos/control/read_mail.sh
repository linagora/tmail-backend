#!/bin/bash
# A user opens a mail of their Inbox: Email/get of its body (fetchTextBodyValues, as the client's email view) and the
# download of its attachment. The mail: a fixture kept between runs (opening a mail must work while none can be created).
# Its content lost (the chaos S3 keeps the blobs in memory: a restart of S3 loses them), it is created again once: the
# feature works if that new mail opens.
. "$(dirname "$0")/lib.sh"

acc="$(account_id "$CHAOS_USER")"
open_fixture() {  # 0: opened; 1: cannot (the reason printed); 3: the fixture's content is lost
  local fx id tok resp blob out
  fx="$(ensure_fixture read with-attachment 2>&1)" || { echo "$fx"; return 1; }
  read -r id tok <<<"$fx"
  resp="$(jmap "$CHAOS_USER" "[[\"Email/get\",{\"accountId\":\"$acc\",\"ids\":[\"$id\"],\"properties\":[\"id\",\"subject\",\"from\",\"bodyValues\",\"textBody\",\"attachments\"],\"fetchTextBodyValues\":true},\"g\"]]" 2>&1)" \
    || { echo "Email/get: $resp"; grep -qiE 'blob not found|NoSuchKey|NoSuchBucket' <<<"$resp" && return 3; return 1; }
  jq -e --arg t "$tok" '[.methodResponses[0][1].list[0].bodyValues[]?.value] | any(contains($t))' <<<"$resp" >/dev/null \
    || { echo "Email/get: the body is not there ($(jq -c '.methodResponses[0][1] | {notFound, n: (.list | length)}' <<<"$resp"))"; return 3; }
  blob="$(jq -r '.methodResponses[0][1].list[0].attachments[0].blobId // empty' <<<"$resp")"
  [ -n "$blob" ] || { echo "Email/get: no attachment"; return 3; }
  out="$(mktemp)"
  download "$CHAOS_USER" "$blob" "$out" 2>&1 && grep -q "$tok" "$out"; local rc=$?
  rm -f "$out"
  [ $rc = 0 ] || { echo "the attachment does not download"; return 1; }
}

why="$(open_fixture)"; rc=$?
[ $rc = 0 ] && ok "mail opened: body and attachment"
[ $rc = 3 ] || fail "cannot open a mail: $why"
first="$why"
fixture_reset read
why="$(open_fixture)" && ok "mail opened: body and attachment (a new fixture: the former one's content is lost: $first)"
fail "cannot open a mail: $first; a new one: $why"
