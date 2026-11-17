#!/bin/bash
# A user attaches a file (JMAP upload) and downloads it back (JMAP download): the same bytes.
. "$(dirname "$0")/lib.sh"

src="$(mktemp)" dst="$(mktemp)"
trap 'rm -f "$src" "$dst"' EXIT
{ echo "chaos upload $RUN_TOKEN"; openssl rand -base64 3000; } >"$src"
blob="$(upload "$CHAOS_USER" "$src" application/octet-stream 2>&1)" || fail "$blob"
why="$(download "$CHAOS_USER" "$blob" "$dst" 2>&1)" || fail "$why"
[ "$(openssl dgst -sha256 -r <"$src")" = "$(openssl dgst -sha256 -r <"$dst")" ] || fail "the download differs from the upload"
ok "$(wc -c <"$src") bytes uploaded and downloaded"
