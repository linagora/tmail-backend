#!/bin/bash
#
# What the asynchronous work of tmail leaves behind, as one JSON object on stdout: twake-chaos takes it before a fault and
# after it (a grace period, then an observation window), and what grew is a failure that did not heal by itself.
#
#   dead_letters      the event bus's dead letters, per listener group (events a listener failed for good)
#   mail_repositories the mails per repository (var/mail/error: the mails the mailet container failed to process)
#   mail_queues       the mails waiting in spool / outgoing
#   failed_tasks      the WebAdmin tasks that failed
#   rabbitmq_queues   the messages (ready + unacknowledged) per RabbitMQ queue that has some
#   unhealthy_checks  tmail's health checks that are not healthy, with their cause
#
set -uo pipefail
. "$(dirname "$0")/common.sh"
W="http://127.0.0.1:$CHAOS_WEBADMIN_PORT"
get() { curl -s -m 10 "$W$1" 2>/dev/null; }

python3 - "$W" <<'PY' "$(compose exec -T rabbitmq1 rabbitmqctl -q list_queues name messages --formatter json 2>/dev/null ||
                         compose exec -T rabbitmq2 rabbitmqctl -q list_queues name messages --formatter json 2>/dev/null || echo '[]')"
import json, sys, urllib.parse, urllib.request
base, rabbit = sys.argv[1], sys.argv[2]
def get(path):
    try:
        with urllib.request.urlopen(base + path, timeout=10) as r:
            return json.loads(r.read() or "null")
    except Exception as e:
        return {"unreachable": f"{type(e).__name__}: {e}"[:200]}
out = {}
groups = get("/events/deadLetter/groups")
out["dead_letters"] = {g: len(get(f"/events/deadLetter/groups/{urllib.parse.quote(g, safe='')}") or [])
                       for g in groups} if isinstance(groups, list) else groups
repos = get("/mailRepositories")
out["mail_repositories"] = {r["repository"]: len(get(f"/mailRepositories/{r['path']}/mails") or [])
                            for r in repos} if isinstance(repos, list) else repos
queues = get("/mailQueues")
out["mail_queues"] = {q: (get(f"/mailQueues/{q}") or {}).get("size") for q in queues} if isinstance(queues, list) else queues
failed = get("/tasks?status=failed")
out["failed_tasks"] = len(failed) if isinstance(failed, list) else failed
try:
    out["rabbitmq_queues"] = {q["name"]: q["messages"] for q in json.loads(rabbit) if q.get("messages")}
except ValueError:
    out["rabbitmq_queues"] = {"unreachable": rabbit[:200]}
health = get("/healthcheck")
out["unhealthy_checks"] = {c["componentName"]: f"{c['status']}: {c.get('cause') or ''}"[:200]
                           for c in health.get("checks", []) if c["status"] != "healthy"} if isinstance(health, dict) else health
print(json.dumps(out))
PY
