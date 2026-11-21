# QA environment: the mail protocols of the distributed app

A local stack of tmail-backend's distributed app (Cassandra, OpenSearch, RabbitMQ, S3, Redis, Tika), with its mail
protocols reachable over TLS and in plain text, and an [MPT](https://github.com/apache/james-project/tree/master/mpt)
MCP server (`chibenwa/mcp-mpt`) that runs Mail Protocol Tester scripts against them. Twake QA uses it to test the
protocols with AI agents; it serves as well for any manual or scripted protocol test.

| Service | Port in the stack | Published |
|---|---|---|
| SMTPS / submission / SMTP | 465 (TLS), 587 and 25 (STARTTLS) | no |
| IMAPS / IMAP | 993 (TLS), 143 (STARTTLS) | no |
| POP3S / POP3 | 995 (TLS), 110 (STLS) | no |
| ManageSieve | 4190 (TLS) | no |
| MPT MCP server (HTTP) | 8080 | `127.0.0.1:${QA_MPT_PORT:-19300}/mcp` |
| WebAdmin | 8000 | `127.0.0.1:${QA_WEBADMIN_PORT:-19301}` |

POP3 and ManageSieve are configured here (`conf/`), the image having none; the other protocols keep the configuration
of the image, TLS with its own self-signed keystore.

`conf/jvm.properties` is the one of the distributed app plus `james.mailbox.handleRecent=false`, the QA environment
dropping the legacy IMAP `\Recent` handling.

`conf/jmap.properties` and `conf/blob.properties` are the distributed app's with the read paths it ships disabled turned on:
- `view.email.query.enabled=true`: simple `Email/query` calls (a folder list, sorted by date) are answered by the Cassandra
  projection the app maintains on every write anyway, instead of OpenSearch;
- `cache.enable=true`: small blobs (the headers read by every row of a mail list) are cached in Cassandra
  (`apache_james_cache`) instead of read from S3 each time.

`conf/blob.properties` also turns `deduplication.enable=true` on: without it the content of a deleted message is removed
from S3 right away, racing the Deleted Messages Vault listener that copies it, so the vault often misses deleted messages.

`conf/opensearch.properties` is the distributed app's plus `opensearch.indexHeaders=false` and `opensearch.nb.replica=1`: the headers of a
mail are not indexed (they took the bulk of the index: each one a nested document and again in `_source`), so the JMAP `header` filter and the
IMAP `SEARCH HEADER` on headers other than From, To, Cc, Subject and the dates find nothing here, which no client of these tests uses. One replica,
as in production: on one node it stays unassigned and takes no disk.

## Usage

```bash
scripts/qa-environment/build.sh   # the image of this commit (Maven and Jib, in docker), James as the submodule
scripts/qa-environment/start.sh   # the stack, the domain tmail.local and two accounts, the MPT server checked
scripts/qa-environment/stop.sh    # everything removed, volumes included
```

`build.sh` builds the distributed app and what it needs (James included) with the local Maven repository (`~/.m2`), then
its image with Jib. To skip the build and run a published image: `TMAIL_IMAGE=linagora/tmail-backend:distributed-branch-master`.

The accounts, also the variables of the MPT scripts (`${user}`, `${password}`, `${user2}`, `${password2}`,
`${domain}`): `alice@tmail.local` / `alice-secret-1` and `bob@tmail.local` / `bob-secret-2` (`QA_USER`, `QA_PASSWORD`,
`QA_USER2`, `QA_PASSWORD2`, `QA_DOMAIN` to change them). The MPT server also creates isolated accounts through WebAdmin
(`create_account`), and archives every script it runs, with its transcript, in `QA_RUN_DIR/mpt` (else in the cache).

Requires Docker with Compose, and about 8 GB of memory for the stack.
