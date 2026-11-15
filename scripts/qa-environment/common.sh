# Shared by build.sh, start.sh and stop.sh of the QA environment of tmail-backend's mail protocols. Not run on its own.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# the checkout under test: this one (QA_REPO_DIR: another, e.g. the worktree of a QA tool)
REPO_DIR="${QA_REPO_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
export QA_PROJECT="${QA_PROJECT:-tmail-backend-protocols-qa}"
export QA_MPT_PORT="${QA_MPT_PORT:-19300}"
export QA_WEBADMIN_PORT="${QA_WEBADMIN_PORT:-19301}"
# what the jwt keys of tmail live in, between start and stop
export QA_RUN="${QA_RUN:-${XDG_CACHE_HOME:-$HOME/.cache}/tmail-backend-qa/$QA_PROJECT}"
# the image under test: built from the commit by build.sh (TMAIL_IMAGE given: that one, no build)
BUILT_IMAGE="$QA_PROJECT-tmail:latest"
export TMAIL_IMAGE="${TMAIL_IMAGE:-$BUILT_IMAGE}"
# the accounts of the test bench: the MPT scripts read them as ${user} ${password} ${user2} ${password2} ${domain}
export QA_DOMAIN="${QA_DOMAIN:-tmail.local}"
export QA_USER="${QA_USER:-alice@$QA_DOMAIN}" QA_PASSWORD="${QA_PASSWORD:-alice-secret-1}"
export QA_USER2="${QA_USER2:-bob@$QA_DOMAIN}" QA_PASSWORD2="${QA_PASSWORD2:-bob-secret-2}"
export MPT_VARIABLES="user=$QA_USER,password=$QA_PASSWORD,user2=$QA_USER2,password2=$QA_PASSWORD2,domain=$QA_DOMAIN"
export QA_UID="$(id -u)" QA_GID="$(id -g)"
# where the MPT server archives its runs: the folder of the QA run (QA_RUN_DIR, as Twake QA gives it), else the cache
export MPT_ARCHIVE="${QA_RUN_DIR:+$QA_RUN_DIR/mpt}"
export MPT_ARCHIVE="${MPT_ARCHIVE:-$QA_RUN/mpt-archive}"

compose() {
  docker compose -p "$QA_PROJECT" -f "$SCRIPT_DIR/compose.yaml" "$@"
}
