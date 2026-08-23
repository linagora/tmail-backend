#!/usr/bin/env bash
#
# Reports a failed Jenkins build back to the originating GitHub pull request.
#
# The report lists the JUnit tests that failed (surefire/failsafe reports found
# anywhere in the workspace). When no failing test can be found - a compilation
# error, an infrastructure failure, ... - it falls back to the tail of the build
# log captured by the `tee` step of the Jenkinsfile.
#
# The point is to give a coding agent everything it needs to converge back to a
# green build without a human relaying the console output.
#
# Environment:
#   WORKSPACE               Jenkins workspace                (default: $PWD)
#   CI_LOG_DIR              Per stage build logs             (default: $WORKSPACE/ci-logs)
#   BUILD_LOG               Captured build output            (default: newest log of CI_LOG_DIR)
#   REPORT_FILE             Where the markdown is written    (default: $WORKSPACE/ci-failure-report.md)
#   GITHUB_API              GitHub API base URL              (default: https://api.github.com)
#   GITHUB_REPO             owner/repo to comment on         (default: linagora/tmail-backend)
#   GITHUB_TOKEN            Token used to post the review    (no post when empty)
#   CHANGE_ID               PR number                        (no post when empty)
#   BUILD_URL, JOB_NAME, BUILD_NUMBER, STAGE_NAME, BRANCH_NAME
#   MAX_TESTS               Max failed tests listed          (default: 30)
#   LOG_TAIL_LINES          Log lines on fallback            (default: 50)

set -uo pipefail

WORKSPACE="${WORKSPACE:-$PWD}"
CI_LOG_DIR="${CI_LOG_DIR:-$WORKSPACE/ci-logs}"
BUILD_LOG="${BUILD_LOG:-}"
REPORT_FILE="${REPORT_FILE:-$WORKSPACE/ci-failure-report.md}"
GITHUB_API="${GITHUB_API:-https://api.github.com}"
GITHUB_REPO="${GITHUB_REPO:-linagora/tmail-backend}"
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
CHANGE_ID="${CHANGE_ID:-}"
BUILD_URL="${BUILD_URL:-}"
JOB_NAME="${JOB_NAME:-unknown job}"
BUILD_NUMBER="${BUILD_NUMBER:-?}"
STAGE_NAME="${STAGE_NAME:-}"
MAX_TESTS="${MAX_TESTS:-30}"
LOG_TAIL_LINES="${LOG_TAIL_LINES:-50}"

MARKER='<!-- tmail-ci-failure-report -->'

# Each stage tees its output to $CI_LOG_DIR/<stage name>.log, so the most
# recently written one is the output of the stage that broke the build, and its
# name is the name of that stage.
resolve_build_log() {
    [ -n "$BUILD_LOG" ] && return 0

    local newest
    newest=$(ls -1t "$CI_LOG_DIR"/*.log 2>/dev/null | head -n 1)
    if [ -z "$newest" ]; then
        BUILD_LOG="$WORKSPACE/build-output.log"
        return 0
    fi

    BUILD_LOG="$newest"
    [ -z "$STAGE_NAME" ] && STAGE_NAME=$(basename "$newest" .log)
    return 0
}

# Extracts "<class>.<test><TAB><message>" for every failing test case of a
# surefire/failsafe XML report.
#
# Tags are read one per record (RS = "<") so that layout - a `<testcase/>` and
# its `<failure/>` on a single line, an attribute spanning several lines - never
# matters, then a two state automaton walks them: IDLE until a <testcase> opens,
# holding that test case until either a <failure>/<error> child reports it as
# failed or </testcase> clears it.
# <flakyFailure> and <rerunFailure> deliberately do not match: a test that
# eventually passed is not a build failure.
extract_failures_of() {
    awk 'BEGIN { RS = "<" } {
        if ($0 ~ /^testcase/) {
            cls = ""; nm = ""
            if (match($0, /classname="[^"]*"/)) cls = substr($0, RSTART + 11, RLENGTH - 12)
            stripped = $0
            sub(/classname="[^"]*"/, "", stripped)
            if (match(stripped, /name="[^"]*"/)) nm = substr(stripped, RSTART + 6, RLENGTH - 7)
            current = (cls == "" ? nm : cls "." nm)
        } else if ($0 ~ /^failure/ || $0 ~ /^error/) {
            if (current == "") next
            msg = ""
            if (match($0, /message="[^"]*"/)) msg = substr($0, RSTART + 9, RLENGTH - 10)
            else if (match($0, /type="[^"]*"/)) msg = substr($0, RSTART + 6, RLENGTH - 7)
            gsub(/&quot;/, "\"", msg)
            gsub(/&apos;/, "\047", msg)
            gsub(/&lt;/, "(", msg)
            gsub(/&gt;/, ")", msg)
            # Surefire escapes the control characters of a message - newlines
            # first of all - as numeric references. Decoded before &amp; so that
            # a literal "&#10;" in the message survives as text.
            gsub(/&#[0-9]+;/, " ", msg)
            gsub(/&#x[0-9A-Fa-f]+;/, " ", msg)
            gsub(/&amp;/, "\\&", msg)
            gsub(/\|/, "/", msg)
            gsub(/`/, "\047", msg)
            gsub(/[[:space:]]+/, " ", msg)
            sub(/^ +/, "", msg)
            sub(/ +$/, "", msg)
            if (length(msg) > 200) msg = substr(msg, 1, 200) "..."
            print current "\t" msg
            current = ""
        } else if ($0 ~ /^\/testcase/) {
            current = ""
        }
    }' "$1"
}

collect_failed_tests() {
    find "$WORKSPACE" -type f \
        \( -path '*/surefire-reports/*.xml' -o -path '*/failsafe-reports/*.xml' \) \
        -print0 2>/dev/null \
    | while IFS= read -r -d '' report; do
        extract_failures_of "$report"
    done | sort -u
}

# Escapes stdin into the content of a JSON string (without the quotes).
json_escape() {
    awk '
        {
            line = $0
            gsub(/\\/, "\\\\", line)
            gsub(/"/, "\\\"", line)
            gsub(/\t/, "\\t", line)
            gsub(/\r/, "", line)
            gsub(/[[:cntrl:]]/, " ", line)
            printf "%s\\n", line
        }
    '
}

write_report() {
    local failures="$1" count="$2"
    local title="Build failed"
    [ -n "$STAGE_NAME" ] && title="Build failed in stage \`$STAGE_NAME\`"

    {
        echo "$MARKER"
        echo "## :x: $title"
        echo
        echo "Job \`$JOB_NAME\` build #$BUILD_NUMBER."
        [ -n "$BUILD_URL" ] && echo "Console output: ${BUILD_URL}console"
        echo

        # The details are folded: the summary line is enough to tell at a glance
        # what broke, and the PR conversation stays readable.
        if [ "$count" -gt 0 ]; then
            echo "<details>"
            echo "<summary>$count failing test(s)</summary>"
            echo
            echo '| Test | Message |'
            echo '| --- | --- |'
            printf '%s\n' "$failures" | head -n "$MAX_TESTS" | while IFS=$'\t' read -r test message; do
                echo "| \`$test\` | ${message:-_no message_} |"
            done
            echo
            if [ "$count" -gt "$MAX_TESTS" ] ; then
                echo "_… and $((count - MAX_TESTS)) more failing test(s), see the console output._"
                echo
            fi
            echo "</details>"
        else
            echo "<details>"
            echo "<summary>No failing test reported - last $LOG_TAIL_LINES lines of the build output</summary>"
            echo
            echo '````'
            if [ -r "$BUILD_LOG" ]; then
                tail -n "$LOG_TAIL_LINES" "$BUILD_LOG"
            else
                echo "No build log captured at $BUILD_LOG."
            fi
            echo '````'
            echo
            echo "</details>"
        fi
    } > "$REPORT_FILE"
}

# Posts the report as a PR review, falling back on a plain issue comment when
# the review API refuses it.
post_to_github() {
    local payload="$WORKSPACE/ci-failure-report.json"
    local response="$WORKSPACE/ci-failure-report-response.json"
    local status

    printf '{"body":"%s","event":"COMMENT"}' "$(json_escape < "$REPORT_FILE")" > "$payload"

    status=$(curl -s -o "$response" -w '%{http_code}' -X POST \
        -H "Authorization: token $GITHUB_TOKEN" \
        -H "Content-Type: application/json" \
        --data-binary "@$payload" \
        "$GITHUB_API/repos/$GITHUB_REPO/pulls/$CHANGE_ID/reviews")

    if [ "$status" -ge 200 ] && [ "$status" -lt 300 ]; then
        echo "Failure report posted as a review on PR #$CHANGE_ID."
        return 0
    fi

    echo "WARNING: posting the review failed with HTTP $status, falling back to a comment."
    cat "$response"

    printf '{"body":"%s"}' "$(json_escape < "$REPORT_FILE")" > "$payload"
    status=$(curl -s -o "$response" -w '%{http_code}' -X POST \
        -H "Authorization: token $GITHUB_TOKEN" \
        -H "Content-Type: application/json" \
        --data-binary "@$payload" \
        "$GITHUB_API/repos/$GITHUB_REPO/issues/$CHANGE_ID/comments")

    if [ "$status" -ge 200 ] && [ "$status" -lt 300 ]; then
        echo "Failure report posted as a comment on PR #$CHANGE_ID."
        return 0
    fi

    echo "WARNING: posting the failure report failed with HTTP $status"
    cat "$response"
    return 1
}

resolve_build_log

failed_tests=$(collect_failed_tests)
if [ -z "$failed_tests" ]; then
    failed_test_count=0
else
    failed_test_count=$(printf '%s\n' "$failed_tests" | wc -l)
fi

write_report "$failed_tests" "$failed_test_count"

echo "===== CI failure report ====="
cat "$REPORT_FILE"
echo "============================="

if [ -z "$CHANGE_ID" ]; then
    echo "Not a pull request build: the failure report is not posted to GitHub."
    exit 0
fi
if [ -z "$GITHUB_TOKEN" ]; then
    echo "No GITHUB_TOKEN provided: the failure report is not posted to GitHub."
    exit 0
fi

post_to_github
