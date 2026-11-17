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

# RabbitMQ's queues from any node that answers; none answering is an error, not an empty list
rabbit="$(compose exec -T rabbitmq1 rabbitmqctl -q list_queues name messages --formatter json 2>/dev/null ||
          compose exec -T rabbitmq2 rabbitmqctl -q list_queues name messages --formatter json 2>/dev/null ||
          compose exec -T rabbitmq3 rabbitmqctl -q list_queues name messages --formatter json 2>/dev/null ||
          echo '{"unreachable": "rabbitmqctl list_queues failed on every node"}')"
python3 - "$W" "$rabbit" <<'PY'
import json, sys, urllib.error, urllib.parse, urllib.request
base, rabbit = sys.argv[1], sys.argv[2]
def get(path):
    """The JSON answer, also of an HTTP error (the health check answers 503 when a check fails); an error object else."""
    try:
        with urllib.request.urlopen(base + path, timeout=10) as r:
            return json.loads(r.read() or "null")
    except urllib.error.HTTPError as e:
        try:
            body = e.read()
        except Exception as read_error:  # the error body itself could not be read
            return {"unreachable": f"HTTP {e.code}, body unreadable: {type(read_error).__name__}"[:200]}
        if not body:
            return {"unreachable": f"HTTP {e.code}, empty body"}
        try:
            return json.loads(body)
        except ValueError:
            return {"unreachable": f"HTTP {e.code}: {body[:150]!r}"}
    except Exception as e:
        return {"unreachable": f"{type(e).__name__}: {e}"[:200]}
def count(answer):
    """The length of a list answer; the error object of a failed request kept as it is (not counted)."""
    return len(answer) if isinstance(answer, list) else (answer or 0)
out = {}
groups = get("/events/deadLetter/groups")
out["dead_letters"] = {g: count(get(f"/events/deadLetter/groups/{urllib.parse.quote(g, safe='')}"))
                       for g in groups} if isinstance(groups, list) else groups
repos = get("/mailRepositories")
out["mail_repositories"] = {r["repository"]: count(get(f"/mailRepositories/{r['path']}/mails"))
                            for r in repos} if isinstance(repos, list) else repos
queues = get("/mailQueues")
def size(answer):
    """The size of a queue's detail; the error object of a failed request kept as it is."""
    return answer["size"] if isinstance(answer, dict) and "size" in answer else (answer or {"unreachable": "no answer"})
out["mail_queues"] = {q: size(get(f"/mailQueues/{q}")) for q in queues} if isinstance(queues, list) else queues
failed = get("/tasks?status=failed")
out["failed_tasks"] = len(failed) if isinstance(failed, list) else failed
try:
    listed = json.loads(rabbit)
except ValueError:
    listed = {"unreachable": f"not JSON: {rabbit[:200]}"}
out["rabbitmq_queues"] = {q["name"]: q["messages"] for q in listed if q.get("messages")} if isinstance(listed, list) else listed
health = get("/healthcheck")
out["unhealthy_checks"] = ({c["componentName"]: f"{c['status']}: {c.get('cause') or ''}"[:200]
                            for c in health["checks"] if c["status"] != "healthy"}
                           if isinstance(health, dict) and isinstance(health.get("checks"), list) else health)
print(json.dumps(out))
PY
