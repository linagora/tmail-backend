# Chaos environment: the resilience claims of the distributed app

`claims.yaml` says, for each service Twake Mail relies on, what it survives:

| Key | Meaning |
|---|---|
| `faultTolerance` | nodes that can be lost with no impact at all (absent: a single node) |
| `downImpact` | what stops working while the whole component is down; every other feature keeps working |
| `accidentRecovery` | `live`: once the component is back, everything works again without restarting tmail |
| `requiredOnStartup` | `false`: tmail starts without it, degraded as `downImpact` says |

This folder is the stack that checks them: every node of a replicated service is its own compose service, so that a
tool ([twake-chaos](https://github.com/linagora), the Twake QA tooling) can pause, stop and restart them one by one while
control scripts check the features. `topology.yaml` maps each component of `claims.yaml` onto its services.

| Component | Nodes | Replication |
|---|---|---|
| Cassandra 4.1 | `cassandra1..3` | keyspace RF 3, QUORUM reads / writes, SERIAL LWT |
| OpenSearch 2.19 | `opensearch1..3` | 3 shards, 1 replica |
| RabbitMQ 4.0 | `rabbitmq1..3` | quorum queues, replication factor 3, `autoheal` |
| Redis 8.0 | `redis1..3` | a Redis Cluster: each node the master of a third of the slots (6379) and the replica of a neighbour's (6380) |
| S3 (Scality, file backend on volumes) | `s3` | |
| Rspamd 3.14 | `rspamd` (its own standalone Redis: `rspamd-redis`) | |
| OpenLDAP | `ldap` (the users: the demo's schema and seed) | |
| LemonLDAP::NG | `sso` (OIDC; JMAP bearer tokens are introspected) | |

Each container has a memory limit (~12 GB in all). Tika is left out (not a claimed component, `tika.enabled=false`).
The configuration of tmail is the image's, with the files of `conf/tmail/` mounted over it: the nodes' lists, the
replication and consistency above, LDAP users, OIDC, Rspamd in `local-delivery` (`onMailetException: ignore`: Rspamd
down, the mail is delivered unchecked, as claimed).
`jmap.properties` answers simple `Email/query` calls from the Cassandra projection (`view.email.query.enabled`), and
`blob.properties` caches the small blobs in Cassandra (`cache.enable`), as the QA environment. The cache's keyspace has a
replication factor of 1 whatever `cassandra.replication.factor` says: with a node down, the blobs it held are read from S3
after the cache's timeout (100 ms).

## Usage

```bash
scripts/chaos/build.sh   # the image of this commit (Maven and Jib, in docker); TMAIL_IMAGE=<published image>: pulled instead
scripts/chaos/start.sh   # the backing services, the clusters formed and checked, tmail healthy, an SSO sign-in reaching JMAP
scripts/chaos/stop.sh    # everything removed, volumes included
```

Published on 127.0.0.1: JMAP 19700, WebAdmin 19701, SMTP 19702, submission 19703, IMAP 19704 (STARTTLS), the SSO 19705
(`Host: sso.example.com`). Accounts: the LDAP seed, e.g. `alice@tmail.com` / `secret`. start.sh writes the URLs and
accounts to `~/.cache/twake-chaos/tmail-chaos/env`. A fault by hand:

```bash
. scripts/chaos/common.sh
compose pause cassandra2     # a hung node; `stop` for a crashed one
compose unpause cassandra2
```
