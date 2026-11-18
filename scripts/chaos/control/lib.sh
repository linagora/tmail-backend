# Shared by the control scripts of the chaos stack of Twake Mail. Sourced, never run.
#
# Conventions: exit 0 the feature works, 1 it does not, 2 the probe itself is broken. The last line printed says why.
# Every helper prints its result on stdout and returns non zero on failure, its reason on stderr.

CONTROL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../common.sh
. "$CONTROL_DIR/../common.sh"
if [ ! -r "$CHAOS_RUN/env" ]; then echo "no $CHAOS_RUN/env: start.sh has not run"; exit 2; fi
# shellcheck disable=SC1091
. "$CHAOS_RUN/env"

# state kept between runs (cached tokens, fixtures): never needed, only saves time
STATE_DIR="$CHAOS_RUN/control"
mkdir -p "$STATE_DIR" 2>/dev/null || { echo "cannot create $STATE_DIR"; exit 2; }

FEATURE="$(basename "$0" .sh)"
START=$SECONDS
# the budget of the script: twake-chaos kills it at timeout_seconds, we stop polling before, to say why
BUDGET="${CHAOS_CONTROL_BUDGET:-80}"
remaining() { echo $((START + BUDGET - SECONDS)); }
# the timeout of one call: as a client without one (tmail-flutter's dio has none) a slow answer is still an answer;
# bounded by the budget left. The slowness itself: twake-chaos times the scripts.
CALL_TIMEOUT=45
ct() { local r=$((START + BUDGET - SECONDS)); [ $r -gt $CALL_TIMEOUT ] && r=$CALL_TIMEOUT; [ $r -lt 5 ] && r=5; echo $r; }

RUN_TOKEN="$(date +%s)-$RANDOM$RANDOM"
JMAP_ACCEPT='Accept: application/json; jmapVersion=rfc-8621'
USING='["urn:ietf:params:jmap:core","urn:ietf:params:jmap:mail","urn:ietf:params:jmap:submission"]'

# the reason on one line, the last one
oneline() { local s="$*"; s="${s//$'\n'/ | }"; printf '%s\n' "${s:0:400}"; }
ok()     { oneline "OK: $*"; exit 0; }
fail()   { oneline "FAIL: $*"; exit 1; }
broken() { oneline "BROKEN: $*"; exit 2; }

account_id() {  # the JMAP account id of a user: sha256 of the username (as James computes it)
  printf '%s' "$1" | openssl dgst -sha256 -r | cut -d' ' -f1
}

# ---------------------------------------------------------------------------------------------------- the SSO
# sso_token_fresh <user>: a new access token from LemonLDAP (password grant: the "james" OIDC client)
sso_token_fresh() {
  local user="$1" body token
  body="$(curl -s -m "$(ct)" -H "Host: $CHAOS_SSO_HOST" -u "$CHAOS_OIDC_CLIENT:$CHAOS_OIDC_SECRET" \
    -d grant_type=password --data-urlencode "username=$user" --data-urlencode "password=$CHAOS_PASSWORD" \
    -d 'scope=openid email profile' "$CHAOS_SSO_URL/oauth2/token")" || { echo "the SSO is unreachable" >&2; return 1; }
  token="$(jq -r '.access_token // empty' <<<"$body" 2>/dev/null)"
  [ -n "$token" ] || { echo "the SSO gives no token: $(head -c 200 <<<"$body")" >&2; return 1; }
  printf '%s %s\n' "$(( $(date +%s) + $(jq -r '.expires_in // 120' <<<"$body") ))" "$token"
}

# token <user>: a bearer token, as a signed-in web client holds one: reused while valid, renewed when it has less than a
# minute left; the renewal failing, the one held is used until it expires. The chaos stack's LemonLDAP issues them for
# 1 h and twake-chaos fetches fresh ones just before every fault (prepare.sh, topology.yaml app.prepare): during a
# fault the controls use a token taken before it, so what does not need the SSO or LDAP is really measured. Only
# sso_sign_in asks for a new one (sso_token_fresh): that is its feature.
token() {
  local user="$1" file="$STATE_DIR/token.$1" now exp tok fresh
  now=$(date +%s)
  if [ -s "$file" ]; then read -r exp tok <"$file"; else exp=0 tok=; fi
  if [ $((exp - now)) -lt 60 ]; then
    if fresh="$(sso_token_fresh "$user")"; then
      printf '%s\n' "$fresh" >"$file.$$" && mv -f "$file.$$" "$file"
      read -r exp tok <<<"$fresh"
    elif [ $((exp - now)) -lt 5 ]; then
      echo "no valid token for $user (the SSO does not sign in)" >&2; return 1
    fi
  fi
  printf '%s' "$tok"
}

# ------------------------------------------------------------------------------------------------------- JMAP
# jmap <user> <methodCalls JSON>: the response of the JMAP API (fails on an HTTP error or a method error)
jmap() {
  local user="$1" calls="$2" tok resp code
  tok="$(token "$user")" || return 1
  resp="$(curl -s -m "$(ct)" -w '\n%{http_code}' -H "Authorization: Bearer $tok" -H "$JMAP_ACCEPT" \
    -H 'Content-Type: application/json' --data-binary "{\"using\":$USING,\"methodCalls\":$calls}" "$CHAOS_JMAP_URL/jmap")"
  code="${resp##*$'\n'}"; resp="${resp%$'\n'*}"
  if [ "$code" != 200 ]; then echo "JMAP HTTP ${code:-timeout}: $(head -c 200 <<<"$resp")" >&2; return 1; fi
  if jq -e '[.methodResponses[] | select(.[0] == "error")] | length > 0' <<<"$resp" >/dev/null 2>&1; then
    echo "JMAP error: $(jq -c '[.methodResponses[] | select(.[0] == "error") | .[1]][0]' <<<"$resp" | head -c 200)" >&2; return 1
  fi
  printf '%s' "$resp"
}

# jset_errors <response>: the notCreated / notUpdated / notDestroyed of the /set responses, empty when none
jset_errors() {
  jq -c '[.methodResponses[] | .[1] | (.notCreated, .notUpdated, .notDestroyed) | select(. != null and . != {})] | select(length > 0)' <<<"$1"
}

# mailbox_id <user> <role>: from Mailbox/get (Cassandra)
mailbox_id() {
  local user="$1" role="$2" acc resp id
  acc="$(account_id "$user")"
  resp="$(jmap "$user" "[[\"Mailbox/get\",{\"accountId\":\"$acc\",\"properties\":[\"id\",\"role\"]},\"m\"]]")" || return 1
  id="$(jq -r --arg r "$role" '.methodResponses[0][1].list[] | select(.role == $r) | .id' <<<"$resp" | head -1)"
  [ -n "$id" ] || { echo "$user has no $role mailbox" >&2; return 1; }
  printf '%s' "$id"
}

# upload <user> <file> <type>: the blobId of the uploaded content (S3)
upload() {
  local user="$1" file="$2" type="$3" tok resp
  tok="$(token "$user")" || return 1
  resp="$(curl -s -m "$(ct)" -w '\n%{http_code}' -H "Authorization: Bearer $tok" -H "$JMAP_ACCEPT" -H "Content-Type: $type" \
    --data-binary "@$file" "$CHAOS_JMAP_URL/upload/$(account_id "$user")")"
  [ "${resp##*$'\n'}" = 201 ] || { echo "upload: HTTP ${resp##*$'\n'}: $(head -c 200 <<<"${resp%$'\n'*}")" >&2; return 1; }
  jq -er '.blobId' <<<"${resp%$'\n'*}"
}

# download <user> <blobId> <out file>
download() {
  local user="$1" blob="$2" out="$3" tok code
  tok="$(token "$user")" || return 1
  code="$(curl -s -m "$(ct)" -o "$out" -w '%{http_code}' -H "Authorization: Bearer $tok" \
    "$CHAOS_JMAP_URL/download/$(account_id "$user")/$blob?type=application/octet-stream&name=probe.bin")"
  [ "$code" = 200 ] || { echo "download: HTTP ${code:-timeout}" >&2; return 1; }
}

# create_email <user> <mailbox id> <subject> <text body> [<keywords JSON>] [<attachment blobId>]: the id created
create_email() {
  local user="$1" mbox="$2" subject="$3" text="$4" keywords="${5:-}" att="${6:-}" acc email resp id
  [ -n "$keywords" ] || keywords='{"$seen":true}'
  acc="$(account_id "$user")"
  email="$(jq -nc --arg mb "$mbox" --arg s "$subject" --arg t "$text" --arg u "$user" --argjson k "$keywords" --arg att "$att" '{
      mailboxIds: {($mb): true}, keywords: $k, subject: $s,
      from: [{email: $u}], to: [{email: $u}],
      bodyValues: {b: {value: $t}}, textBody: [{partId: "b", type: "text/plain"}]}
    + (if $att == "" then {} else {attachments: [{blobId: $att, type: "application/octet-stream", name: "probe.bin"}]} end)')"
  resp="$(jmap "$user" "[[\"Email/set\",{\"accountId\":\"$acc\",\"create\":{\"e\":$email}},\"c\"]]")" || return 1
  id="$(jq -r '.methodResponses[0][1].created.e.id // empty' <<<"$resp")"
  [ -n "$id" ] || { echo "Email/set create: $(jset_errors "$resp" | head -c 200)" >&2; return 1; }
  printf '%s' "$id"
}

destroy_emails() {  # destroy_emails <user> <id>...: best effort, cleaning
  local user="$1"; shift
  [ $# -gt 0 ] || return 0
  jmap "$user" "[[\"Email/set\",{\"accountId\":\"$(account_id "$user")\",\"destroy\":$(printf '%s\n' "$@" | jq -R . | jq -sc .)},\"d\"]]" >/dev/null 2>&1 || true
}

# the mails this run waits for are received after it (a margin for the clocks): only those are listed, not older ones
SINCE="$(date -u -d '-10 sec' +%Y-%m-%dT%H:%M:%SZ)"

# find_in_mailbox <user> <mailbox id> <subject substring>: the ids of the mails of the mailbox received since the start of
# the run whose subject has it. Email/query with inMailbox and after, sorted by receivedAt: served by the email query
# view (Cassandra), not by OpenSearch.
find_in_mailbox() {
  local user="$1" mbox="$2" needle="$3" acc resp
  acc="$(account_id "$user")"
  resp="$(jmap "$user" "[[\"Email/query\",{\"accountId\":\"$acc\",\"filter\":{\"inMailbox\":\"$mbox\",\"after\":\"$SINCE\"},\"sort\":[{\"property\":\"receivedAt\",\"isAscending\":false}],\"limit\":50},\"q\"],
    [\"Email/get\",{\"accountId\":\"$acc\",\"#ids\":{\"resultOf\":\"q\",\"name\":\"Email/query\",\"path\":\"/ids\"},\"properties\":[\"id\",\"subject\"]},\"g\"]]")" || return 1
  jq -r --arg n "$needle" '.methodResponses[1][1].list[] | select((.subject // "") | contains($n)) | .id' <<<"$resp"
}

# wait_in_mailbox <user> <role> <subject substring>: polls until a mail of the mailbox has it, prints its id
wait_in_mailbox() {
  local user="$1" role="$2" needle="$3" mbox ids err=""
  mbox="$(mailbox_id "$user" "$role")" || return 1
  while [ "$(remaining)" -gt 0 ]; do
    if ids="$(find_in_mailbox "$user" "$mbox" "$needle" 2>&1)"; then
      [ -n "$ids" ] && { head -1 <<<"$ids"; return 0; }
      err=""
    else
      err="$ids"
    fi
    sleep 0.5
  done
  echo "not in $user's $role after ${BUDGET}s${err:+ ($err)}" >&2
  return 1
}

# ---------------------------------------------------------------------------------------------------- fixtures
# A fixture: a mail of alice's Inbox kept between runs for the probes that only read or flag one (they must work while
# S3 is down, where no mail can be created). Its id and token are kept in $STATE_DIR/fixture.<name>; it is created again
# when it is gone, or unreadable (`fixture_reset`).
#
# ensure_fixture <name> [with-attachment]: prints "<id> <token>"
ensure_fixture() {
  local name="$1" with_att="${2:-}" file="$STATE_DIR/fixture.$1" id tok acc resp inbox att="" tmp
  acc="$(account_id "$CHAOS_USER")"
  if [ -s "$file" ]; then
    read -r id tok <"$file"
    resp="$(jmap "$CHAOS_USER" "[[\"Email/get\",{\"accountId\":\"$acc\",\"ids\":[\"$id\"],\"properties\":[\"id\",\"mailboxIds\"]},\"g\"]]")" || return 1
    if jq -e '.methodResponses[0][1].list | length == 1' <<<"$resp" >/dev/null; then
      printf '%s %s' "$id" "$tok"; return 0
    fi
  fi
  # created (again)
  inbox="$(mailbox_id "$CHAOS_USER" inbox)" || return 1
  tok="fx$RANDOM$RANDOM$RANDOM"
  if [ -n "$with_att" ]; then
    tmp="$(mktemp)"; printf 'chaos attachment %s\n' "$tok" >"$tmp"
    att="$(upload "$CHAOS_USER" "$tmp" application/octet-stream)"; local rc=$?
    rm -f "$tmp"; [ $rc = 0 ] || { echo "fixture $name: $att" >&2; return 1; }
  fi
  id="$(create_email "$CHAOS_USER" "$inbox" "chaos fixture $name $tok" "The fixture $name of the control scripts: $tok" '{}' "$att")" \
    || { echo "fixture $name cannot be created" >&2; return 1; }
  printf '%s %s\n' "$id" "$tok" >"$file"
  printf '%s %s' "$id" "$tok"
}

fixture_reset() {  # fixture_reset <name>: forgets the fixture (destroyed, best effort): the next ensure_fixture creates it
  local id tok
  [ -s "$STATE_DIR/fixture.$1" ] && { read -r id tok <"$STATE_DIR/fixture.$1"; destroy_emails "$CHAOS_USER" "$id"; }
  rm -f "$STATE_DIR/fixture.$1"
}

# ---------------------------------------------------------------------------------------------------- Python
# py <code> [args]: python3 with lib.py importable (IMAP, SMTP)
py() {
  local code="$1"; shift
  PYTHONPATH="$CONTROL_DIR" python3 -c "$code" "$@"
}
