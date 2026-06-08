# Postgres Twake Mail backend 1.0.19 release instructions

## Schema migration

This release requires no mandatory schema migration.

## New features

### Gaussian date decay in default search sort (Optional)

Relevance-based searches (no explicit sort) can apply a Gaussian date decay on the message date to
favour recent messages. This is opt-in and disabled by default. To enable it, set the following keys in
`opensearch.properties`:

```
opensearch.mailbox.score.date.based.decay.enabled=true
# opensearch.mailbox.score.date.based.decay.scale=365d
# opensearch.mailbox.score.date.based.decay.factor=0.5
# opensearch.mailbox.score.date.based.decay.boost.mode=multiply
```

No reindexing is required. See [OpenSearch configuration](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/opensearch.adoc).

### Anti-phishing matchers (Optional)

New `SuspiciousDisplayName` and `SuspiciousDomainInDisplayName` mailet matchers are available for use
in the mailet container.

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.19
