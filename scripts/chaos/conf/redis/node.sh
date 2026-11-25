#!/bin/bash
# A node of the Redis Cluster: two redis-server, 6379 (a master once the cluster is created) and 6380 (the replica of
# another node's master), announced by host name so that a restart keeps the topology. Either one exits: the node exits.
# A stop (SIGTERM) is passed to both, which shut down cleanly (AOF flushed).
node() {
  exec redis-server --port "$1" --cluster-enabled yes --cluster-config-file "/data/nodes-$1.conf" --cluster-node-timeout 5000 --cluster-bus-port-protected-mode no \
    --cluster-announce-hostname "$(hostname)" --cluster-preferred-endpoint-type hostname \
    --appendonly yes --appenddirname "aof-$1" --maxmemory 96mb --maxmemory-policy noeviction --save ""
}
node 6379 & first=$!
node 6380 & second=$!
trap 'kill -TERM "$first" "$second" 2>/dev/null; wait "$first" "$second"; exit 0' TERM INT
wait -n
kill -TERM "$first" "$second" 2>/dev/null
wait
exit 1
