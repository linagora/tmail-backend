#!/bin/bash
# A node of the Redis Cluster: two redis-server, 6379 (a master once the cluster is created) and 6380 (the replica of
# another node's master), announced by host name so that a restart keeps the topology. Either one exits: the node exits.
node() {
  redis-server --port "$1" --cluster-enabled yes --cluster-config-file "/data/nodes-$1.conf" --cluster-node-timeout 5000 \
    --cluster-announce-hostname "$(hostname)" --cluster-preferred-endpoint-type hostname \
    --appendonly yes --appenddirname "aof-$1" --maxmemory 96mb --maxmemory-policy noeviction --save ""
}
node 6379 & node 6380 &
wait -n
exit 1
