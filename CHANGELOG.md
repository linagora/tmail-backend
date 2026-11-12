# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)

## [Unreleased]

### TMail

#### Changes

- ISSUE-2920 `SignatureText`: `{ldap:NAME}` placeholders are matched case-insensitively, so `{ldap:SN}` and `{ldap:givenname}` resolve against the configured `sn` and `givenName`.

#### Fixes

- ISSUE-2917 JMAP `Forward/set` no longer stores forwards differing only by case: `ALICE@domain.tld` and `alice@domain.tld` were both stored and `Forward/get` listed the address twice.
- ISSUE-2919 JMAP settings: a non-boolean value (`""`, `yes`, `1`) of `trash.cleanup.enabled` or `spam.cleanup.enabled` is read as disabled instead of reporting the user as failed on every WebAdmin cleanup task run.
- ISSUE-2916 Encrypted attachment blob ids are only accepted in their canonical form: ids with leading zeros or a sign no longer resolve to the same attachment.
- ISSUE-2912 A mail with a `Content-Type: multipart/encrypted` and no boundary is no longer stored in clear in the encrypted mailbox.

## [1.0.23] - 2026-11-11

Changes since 1.0.22, including the 1.0.22.1 and 1.0.22.2 tags.

### James

#### Added

- JAMES-4133 Internationalized email (RFC 6531, 6532, 6533 and 6855): SMTP and LMTP accept `SMTPUTF8`, UTF-8 addresses are normalized to NFC, DSN and MDN support UTF-8, `RemoteDelivery` uses `SMTPUTF8` when the remote server offers it, and IMAP advertises `UTF8=ACCEPT`.
- [ENHANCEMENT] JMAP Web Push: optional VAPID (RFC 8292) with `webpush.vapid.public.key`, `webpush.vapid.private.key` and `webpush.vapid.subject` in `jmap.properties`, advertised as `urn:ietf:params:jmap:webpush-vapid`. Pushes ask for a one week `TTL`.
- [ENHANCEMENT] Optional `Sender` header checks: the `SenderHeaderVerificationHook` SMTP hook and the `SenderHeaderEmailSubmissionSetValidation` JMAP validation (for `send.extra.validations`).
- [ENHANCEMENT] JMAP `Email/query` accepts an `OR` of `inMailbox` conditions.

#### Changes

- [ENHANCEMENT] Regex mappings are evaluated with RE2J: at most 500 characters, no `{` nor `}`, no lookaround, backreferences, possessive quantifiers nor atomic groups. Stored mappings breaking these rules are no longer applied. `james.rrt.regex.safe=false` restores `java.util.regex`.
- [ENHANCEMENT] JAMES-4236 WebAdmin `populateEmailQueryView` processes every mailbox of the registered domains rather than every user, and reports `processedMailboxCount` and `failedMailboxCount`.
- JAMES-4236 Delayed sends: the server refuses to start when `delay.sends.enabled` or SMTP `FUTURERELEASE` is configured with the RabbitMQ mail queue, which ignores delays; when delayed sends are disabled (the default), `EmailSubmission/set` rejects a non-zero `holdFor` or `holdUntil` with `invalidArguments` instead of sending at once; `EmailSubmission/set` answers `update` and `destroy` (`cannotUnsend`, `notFound`).
- JAMES-4236 JMAP RFC 8621 alignment: requested nullable properties are returned as `null`, `EmailSubmission/set` reports `forbiddenFrom`, `forbiddenMailFrom` and `invalidEmail`, a property sent both as `foo` and `#foo` is `invalidArguments`, `x-` mailbox names get no role, `null` resets an `Identity/set` property.
- [ENHANCEMENT] JMAP filtering rules: subject, header and starts-with conditions ignore case.
- JAMES-4238 Embedded Artemis mail queue: one JMS session per operation; messages without payload are discarded instead of blocking the queue.
- [ENHANCEMENT] RabbitMQ mail queue: missing dead letter exchanges, queues and bindings are recreated on startup.
- [ENHANCEMENT] LDAP: an empty password is rejected without querying the directory.

#### Fixes

- [FIX] JAMES-4236 `EmailSubmission/set` rejects an `identityId` that is not one of the user's identities with `invalidProperties`. A missing `identityId` is still accepted unless `james.jmap.email.submission.missing.identity.compatibility=false`.
- [FIX] JMAP `Email/query` date filters follow RFC 8621: `before` is exclusive and `after` inclusive. `james.jmap.email.query.date.boundary.backward.compatibility=true` restores the former boundaries.
- [FIX] Domains: A-labels (`xn--`) are read in their Unicode form; domains that IDNA2003 and IDNA2008 resolve differently (`ß`, `ς`, ZWJ, ZWNJ) and A-labels decoding to a capital unknown to IDNA2003 are rejected. Usernames can no longer start with `-` nor `$`.
- [FIX] OpenSearch: `hasAttachment` no longer depends on `indexAttachments`. Messages indexed with `indexAttachments=false` need a reindexing.
- [FIX] IMAP robustness: bounded `SEARCH` nesting, `BAD`/`NO` instead of a closed connection on malformed `SETACL`, `ENABLE`, `ID`, `PARTIAL` and mailbox names, stricter modified UTF-7, sequence sets above 2^31, pipelined commands after a large `APPEND`, ordered responses.
- [FIX] IMAP `QRESYNC` `VANISHED (EARLIER)` reports the expunged UIDs outside the remaining range, and no longer reports unassigned UIDs.
- [FIX] SMTP robustness: duplicated `RCPT` parameters answer `501`, `Received` header with an empty header block. JAMES-4239 `postmaster` is always a valid recipient.
- [FIX] MIME and mailets: unmodified messages are written byte for byte; `StripAttachment` keeps every part when attachment names are duplicated; `AddFooter`, `DSNBounce`, `ICALToHeader`, DKIM and MDN parsing handle malformed input.
- [FIX] Addresses and storage: `MailAddress` parsing is stricter, non-ASCII `From` and `Sender` are read entirely, `Content-Type` without subtype is stored, control characters in attachment names are replaced, `RemoteDelivery` no longer drops IDN recipients.
- [FIX] Unreadable mailbox events and some mail attributes no longer block event processing or requeue mails forever; interleaved per-recipient headers keep their order through the RabbitMQ mail queue.
- [FIX] JMAP: invalid JWTs answer `401`, Web Push accepts browser `p256dh` keys, `Email/set` handles empty raw headers, `Mailbox/set` creation references and the first `Identity/set` update are fixed.
- [FIX] Search: RFC 5256 base subject, UTF-8 safe subject truncation, quote markers of the first line.

#### Security

- [FIX] JAMES-4210 SMTP `AUTH PLAIN` and `AUTH LOGIN` honour `auth.requireSSL` (SMTP default `false`): when it is `true`, they answer `504` over a clear-text connection. See the release instructions.
- [FIX] Mailbox rights: renaming a mailbox into another owner's namespace requires the `x` right on every moved mailbox, deleting messages requires `t`, IMAP `REPLACE` requires `e` and a read-write selection.
- [FIX] JMAP `Mailbox/set` sharing: only `p` can be granted to `authenticated`, group identifiers are rejected, `sharedWith` updates are applied atomically.
- [FIX] SMTP `verifyIdentity` and `EmailSubmission/set` check the `From` and `Sender` addresses as both jakarta.mail and mime4j read them.
- [FIX] IMAP responses never carry raw line breaks from message values.
- [FIX] JMAP `/download` never renders active content inline.
- [FIX] Bounded recursion and cost on crafted input: IMAP `SEARCH`, MDN reports, `AddFooter`, calendar time zones, regex mappings (RE2J).
- [FIX] Remote values are sanitized before being written in headers and SMTP replies (`ICALToHeader`, DSN, DKIM result, HELO/EHLO validated before being stored, no control characters echoed in replies).
- [FIX] PROXY protocol: bytes buffered while waiting for the header are bounded, on every protocol with `proxyRequired` (CVE-2026-55851).
- [FIX] SMTP `DATA` end detection stays in sync after the size limit is exceeded.
- [FIX] DKIM body hash covers the whole body.

#### Upgrades

- [UPGRADE] Scala 2.13 → 3.10.
- [UPGRADE] Guava 33.7.2 (CVE-2026-102554), Spring 6.2.19 (CVE-2025-41249, CVE-2026-41848), SCRAM 3.3 (CVE-2026-53712).
- [UPGRADE] RE2J 1.8, ical4j 4.4.0, Jackson 2.22.4, jjwt 0.13.0, amqp-client 5.37.0, Artemis 2.57.0, BouncyCastle 1.86, log4j2 2.26.1, logback 1.6.6, SLF4J 2.0.20, Postgres JDBC 42.7.14 and Micrometer 1.17.2.

### TMail

#### Added

- Twake Space extension (`space.properties`, disabled by default, requires RabbitMQ): gives each TwakeSpace space a team mailbox from the `space` and `admin-panel` events, reports its mail on `activity`, with no table of its own. WebAdmin `POST /spaces?action=sync|purge|reindex` and `GET`/`PUT /domains/{domain}/team-mailboxes/{name}/space`; purging the team mailboxes of deleted spaces needs an external cron.
- AI: `GET /ai/v1/tools` lists mailbox tools for an AI assistant (`list_emails`, `search_emails`, `get_email_details`, `create_draft`) in the OpenAI function-calling format, and `POST /ai/v1/tools` runs a tool call as the authenticated user. Each tool carries a `confirm` flag (`true` for `create_draft`) telling the assistant to ask the user before running it.

#### Changes

- [ENHANCEMENT] The distributed Docker images (`distributed-*`, `distributed-ai-*`) are built from a Dockerfile instead of jib (since 1.0.22.2): they run as the non-root user `james` (uid 1000) and are published for amd64 only. See the release instructions.
- [ENHANCEMENT] `TeamMailboxEventsListener` moves to the Twake Space extension and is configured in `space.properties` instead of `listeners.xml`.
- [ENHANCEMENT] Twake Space replaces `twake-mail-side-service`: bind its team mailboxes to their spaces, see `upgrade-instructions.md`.
- ISSUE-5605 `Email/send` requires a non-empty `From` header (`invalidEmail` otherwise) and answers `forbiddenFrom`, `forbiddenMailFrom` and `noRecipients` instead of `serverFail`.
- ISSUE-5608 JMAP: nullable TMail properties are returned as `null` instead of being left out (`Label/get` `color` and `description`, `CalendarEvent/parse` fields, the encrypted email detailed view).
- ISSUE-2862 IMAP: with the regular path converter, team mailboxes of another domain are listed as `#TeamMailbox.<team>@<domain>`.
- ISSUE-2792 PGP keystore: keys that cannot encrypt and ElGamal keys above 4096 bits are rejected.
- ISSUE-2793 PGP: messages are encrypted with the valid, encryption-flagged keys or subkeys of the user (GnuPG default keys with an encryption subkey are accepted); sign-only, expired and revoked keys are skipped.

#### Fixes

- [FIX] Distributed Docker image: the JVM flags are on the `java` command line again, as with jib, so that a custom `JAVA_TOOL_OPTIONS` no longer prevents the server from starting (1.0.22.2 regression).
- ISSUE-2788, ISSUE-2796, ISSUE-2801, ISSUE-2802, ISSUE-2803, ISSUE-2804 `CalendarEvent/parse`: day and week based `DURATION` is supported; unparsable `DTSTART`, `DTEND` or `RECURRENCE-ID` and unknown `CLASS`, `STATUS`, `TRANSP` or `PARTSTAT` values are ignored instead of failing the whole invitation.
- ISSUE-2795, ISSUE-2797, ISSUE-2798, ISSUE-2799, ISSUE-2800, ISSUE-2805, ISSUE-2806, ISSUE-2843, ISSUE-2865, ISSUE-2882 Calendar replies and COUNTER acceptance: invalid calendars are an `invalidArguments` error instead of `serverFail`, a missing `DTSTAMP` is added, non-standard components are copied, attendee addresses are percent-encoded and `CALSCALE` is written once.
- ISSUE-2779, ISSUE-2787, ISSUE-2876 `CalendarEvent/reply` leaves cancelled events untouched and targets the master `VEVENT` wherever it is listed.
- ISSUE-2841, ISSUE-2842 Accepting a COUNTER applies all its changes and no longer duplicates attendees differing by case.
- ISSUE-2847, ISSUE-2848, ISSUE-2851, ISSUE-2852, ISSUE-2853, ISSUE-2864 CalDAV client: incomplete DAV answers no longer fail `CalendarEventAttendance/get` or event lookups; zero-length events count as free.
- ISSUE-2863 DAV collect mailets read `mailto:` calendar addresses carrying header fields.
- [FIX] Calendar parsing no longer fails after processing many invitations carrying time zone definitions.
- ISSUE-2790 Mails with a `Content-Type` without subtype are delivered to users having a PGP key.
- ISSUE-2810 The in-memory keystore stores a key uploaded twice only once.
- ISSUE-2789, ISSUE-2866 JMAP Download All truncates long attachment names to 200 bytes, without trailing dots or spaces.
- ISSUE-2791 The Sabre contact consumer tolerates vCards listing the same email address twice.
- ISSUE-2746, ISSUE-2747, ISSUE-2748, ISSUE-2749, ISSUE-2755 WebAdmin answers `400` with the reason instead of `500` for invalid team mailbox folder names, invalid or other-domain extra senders and team mailbox members, contacts without `emailAddress` and invalid labels.
- ISSUE-2808, ISSUE-2809 JMAP: an unknown calendar reply language or an invalid `Filter/set` `appendIn` mailbox id is an `invalidArguments` error of that method call instead of failing the whole request.
- ISSUE-2883 `Filter/set`: the self-forward check compares parsed addresses.
- ISSUE-2794 WebFinger accepts any absolute URI resource and answers `400` instead of `500` for invalid ones.

#### Security

- [FIX] `Mailbox/clear` requires the `DeleteMessages` and `PerformExpunge` rights.
- [FIX] `Email/send` checks the `From` and `Sender` addresses as both jakarta.mail and mime4j read them.
- [FIX] Domain signature templates only resolve the `{ldap:…}` attributes read for identities (`extraAttributes` of `IdentityProvisionListener`), never credentials, and HTML-escape them. See the release instructions.
- [FIX] Unauthenticated downloads and public assets are served with `X-Content-Type-Options: nosniff` and `Content-Security-Policy: sandbox`; active content (HTML, SVG, XML) is served as an attachment.
- [FIX] Downloading an encrypted attachment checks that the message is accessible.
- [FIX] `CalDavCollect` and `RestrictiveCalDavCollect` (`strict` and `sameDomain` modes) check the sender alignment of every `VEVENT`, honour `SENT-BY` delegates of the calendar user's domain only, and reject calendar addresses with a fragment. See the release instructions.
- [FIX] Calendar replies select the calendar object whose `VEVENT` `UID` is exactly the requested one; ISSUE-2878 `CalendarEventCounter/accept` checks the organizer of the `VEVENT` it patches.
- ISSUE-2760 JMAP Download All sanitizes ZIP entry names.
- [FIX] Parsing long blob ids and attachment names no longer overflows the stack.
- [FIX] RAG: extracting the latest reply of an email runs in linear time.
- [FIX] PGP keystore: malformed keys are rejected, key packet lengths are validated, and keys are limited to 1 MiB before decoding.

#### Upgrades

- [UPGRADE] Scala 2.13 → 3.10: custom extensions built against TMail Scala classes must be rebuilt; the key order of some JMAP JSON objects changes.
- [UPGRADE] FreeMarker is no longer shipped: it came with ez-vcard and was unused.

## [1.0.22] - 2026-10-14

Changes since 1.0.21.1.

### James

#### Added

- JAMES-4228 `EmailSubmission/set` accepts extra validations configured with `send.extra.validations` in `jmap.properties`; rejected recipients are advertised in the `SetError`.
- [ENHANCEMENT] WebAdmin `GET /users`: `limit` and `anchor` pagination, `query` username prefix search and `domain` filter. Pagination works with LDAP servers capping page sizes.
- [ENHANCEMENT] `SolveMessageInconsistencies` can clean up mailbox entries without backing content.
- [ENHANCEMENT] Blob folder hierarchy is configured with `james.blobstore.folder.hierarchy` (`james.s3.minio.compatibility.mode` remains an alias) and also applies to the file blob store.

#### Changes

- [UPGRADE] The embedded mail queue broker moves from ActiveMQ Classic (KahaDB) to ActiveMQ Artemis. Postgres deployments relying on the embedded mail queue must drain it before upgrading.
- [IMPROVEMENT] RabbitMQ health checks: a single `RabbitMQConsumers` component and a single `RabbitMQDeadLetterQueues` component replace the per consumer and per dead letter queue health checks.
- JAMES-4223 `objectstorage.s3.ifNoneMatch.enable` stays disabled by default.
- [FIX] SMTP: the EHLO reply no longer contains the legacy `AUTH=` line.
- [FIX] JMAP `Email/set` create rejects `bodyStructure` and unknown properties with `invalidProperties` instead of silently dropping them.

#### Fixes

- [FIX] RabbitMQ mail queue consumer health check restarts missing consumers again.
- [FIX] SMTP: consistently validate HELO/EHLO; malformed DSN `NOTIFY`, `ORCPT` and `ENVID` parameters return `501` instead of closing the connection.
- [FIX] WebAdmin `PUT /domainMappings` creates domain mappings rather than domain alias mappings.
- [FIX] Postgres: cancel queries upon jOOQ reactive timeout, buffer the next page locally while paginating, and better support cancellation.
- [FIX] JMAP `Email/get` read level when combined with attachments, and remove bidi control characters from attachment names.
- [FIX] DNS resolution fallback, event bus stop and `@PreDestroy` of provider-built singletons.
- [ENHANCEMENT] Harden the IMAP IDLE lifecycle, log OIDC validation failures and silence WebSocket handshake disconnections.

#### Upgrades

- [UPGRADE] jOOQ 3.20.20, r2dbc-postgresql 1.1.3, Postgres JDBC 42.7.13, log4j2 2.25.5, logback 1.5.38, BouncyCastle 1.85.2, amqp-client 5.35.0, angus-mail 2.0.5, Jsoup 1.23.2, Spark 3.0.5 and Micrometer.

### TMail

#### Added

- ISSUE-2665 Rate limiting on the number of recipients sent per minute, hour and day, configurable per user and per domain through WebAdmin and SaaS subscription messages, with defaults on the `SentRateLimiting` mailet. Requires a schema change.
- SaaS domain activation: persist the mail DNS validation status of SaaS domains, provision domains upon ownership verification, and optionally block outgoing mail of pending domains with the `MailDnsConfigurationValidation` JMAP validation and the `MailDnsConfigurationMailHook` SMTP hook. Requires a schema change.
- ISSUE-2645 The SaaS JMAP capability advertises `mailDnsConfigurationValidated`.
- Delete user data upon Twake Workplace user deletion events (B2C and B2B) when the TWP settings module is enabled.
- ISSUE-2714 Synchronize `appearance.theme` from Twake Workplace common settings; ISSUE-2724 the setting is then read-only.
- ISSUE-2712 Optional `TeamMailboxEventsListener` publishes team mailbox `received` and `sent` events to a RabbitMQ topic exchange.
- ISSUE-2720 `TransitedVia` and `TransitedTwiceVia` matchers for relay loop detection based on `Received` headers.
- `IsALDAPMailingList` accepts the extra filter of `LDAPMailingList`.
- Migration proxy: optional SMTP submission proxy.
- WebAdmin: task cleanup routes on the distributed and Postgres servers.
- [I18N] Spanish, German and Italian calendar reply and DSN templates.
- [ENHANCEMENT] Explicit audit log upon DAV collect mailets.

#### Changes

- ISSUE-2728 Docker images are based on `eclipse-temurin:27-jre-resolute` (formerly `eclipse-temurin:26-jre-noble`).
- ISSUE-2674 TMail RabbitMQ consumers and dead letter queues (event bus partitions, TWP settings, SaaS subscriptions, TWP user deletion) are reported by the James `RabbitMQConsumers` and `RabbitMQDeadLetterQueues` health checks.
- ISSUE-2691 `Forward/set` accepts partial patches.
- ISSUE-2692 `Label/set` removes a label colour with `color: null`.
- ISSUE-2693 `Filter/get` output is a valid `Filter/set` input; mismatching `condition` and `conditionGroup` is a request level `invalidArguments` error.

#### Fixes

- [FIX] RAG: send the email metadata to OpenRAG as an `email` object (`subject`, `preview`) instead of dotted `email.*` keys, and add `created_at` (the internal date of the message).
- [FIX] `objectstorage.s3.ifNoneMatch.enable` is now honored by the TMail S3 blob store.
- [FIX] Search: a single unbalanced double quote no longer breaks subject search.
- [FIX] Support Microsoft Send-By in ORGANIZER validation prior to ITIP processing.
- [FIX] Calendar replies to an invitation for a single occurrence of a recurring event, sent without its master event, no longer fail.
- ISSUE-2682 Register `PopulateEmailQueryViewListener` in the memory server `listeners.xml`.

## [1.0.21.1] - 2026-09-17

Changes since 1.0.20, including the 1.0.21 tag.

### James

#### Added

- JAMES-4225 Cassandra schema version 16: backfill message metadata in `messageIdTable` and `imapUidTable`. Distributed deployments with an older schema version must run the migration through WebAdmin.
- JAMES-4227 Optionally configure the JMAP preview length with `james.jmap.preview.length` (default: 256 characters).
- JAMES-4215 Optional Kerberos realm-to-mail-domain mapping for GSSAPI authentication.
- JAMES-4219 Optional customization of selected SMTP replies.
- [ENHANCEMENT] Disable JMAP WebPush with `webpush.enabled=false` (enabled by default).
- JAMES-4222 Optional unordered S3 listings for Ceph RADOS Gateway with `james.s3.rados.allow.unorder=true`.
- [ENHANCEMENT] WebAdmin: find a dead-lettered event by `eventId` across groups.
- [ENHANCEMENT] SMTP accounts declared in `ConfigurationAuthHook` can opt into sending on behalf of users with `allowUseOtherIdentity` (defaults to `false`).

#### Fixes

- [FIX] PostgreSQL: initialize the HSTORE extension correctly.
- [FIX] Reject OIDC tokens that introspection reports as inactive.
- [FIX] Cassandra 5 compatibility for timestamps and LZ4 compression options.
- [FIX] IMAP: preserve a pipelined command following a literal and improve resource handling.
- [FIX] JMAP downloads: sanitize an empty content type.
- [FIX] SMTP: accept MAC addresses in HELO arguments.
- [FIX] Ignore malformed ICS property values and prevent `IsSMIMESigned` from treating PGP/MIME signatures as S/MIME.

#### Enhancements

- JAMES-4224 New blob IDs default to 128 bits of entropy when `james.blobid.entropy` is unset; existing blobs remain readable. Set it to `256` to retain the former format.
- [ENHANCEMENT] Cassandra no longer writes the unused `messagev3.bodyOctets`, `messageIdTable.flagUser` and `imapUidTable.flagUser` columns. Dropping them is optional after all nodes are upgraded.
- [PERF] Parse Tika content on a parallel scheduler and optionally use native TLS with the Cassandra driver.

#### Upgrades

- [UPGRADE] ActiveMQ Artemis 2.55.0 → 2.56.0.
- [UPGRADE] Netty 4.1.132 → 4.1.135, MIME4J 0.8.14 → 0.8.15 and Cassandra Java driver 4.19.1 → 4.19.3.

### TMail

#### Added

- ISSUE-2608 Optional S3 bucket sharding for Ceph/RADOS deployments, configured with `tmail.blobstore.shards` in `blob.properties`. Sharding is disabled by default and requires a stable bucket layout from the start.
- Migration proxy: IMAP `AUTHENTICATE PLAIN` support.
- ISSUE-2576 Migration proxy: optional Kerberos/GSSAPI mode uses backend administrator delegation and requires verified backend TLS by default; password authentication remains the default mode.
- ISSUE-2585 Migration proxy: when configured with RabbitMQ, consume migration switch events to mark users migrated and disconnect their IMAP sessions, honoring configured queue arguments.
- WebAdmin: list a domain's tasks with `GET /domains/{domain}/tasks`, optionally filtered by status.
- ISSUE-2605 Optional `EnrichACLListener` qualifies local-part-only ACL entries with the mailbox owner's domain.
- [ENHANCEMENT] Bundled Mongolian (`mn`) templates for delivery status notifications and calendar replies; mounted configurations can opt in by adding `mn` to their supported languages.

#### Fixes

- [FIX] Avoid OpenSearch version conflicts when migrating contacts during a username change.

#### Enhancements

- [FIX] Limit DAV error response text in logs and expose it in the `sabreResponse` MDC field.
- ISSUE-2619 Avoid caching duplicated header blobs in the BlobId list DAO.
- [ENHANCEMENT] `UnauthenticatedBlobAccess/set` now returns `validUntil` alongside each created token.

## [1.0.20] - 2026-08-23

### James

#### Added

- JAMES-4210 Protocol-neutral SASL SPI: pluggable, per-server SASL mechanisms for IMAP, SMTP, POP3 and ManageSieve
- JAMES-4215 Optional SASL GSSAPI (Kerberos) mechanism
- JAMES-4215 Make the SMTP maximum line length configurable
- JAMES-4195 JMAP OIDC authentication, with a Redis backed token cache and a backchannel logout route
- JAMES-4216 Allow HTML templating for overquota emails
- JAMES-4209 Distributed app content recovery runner
- [ENHANCEMENT] Optional stricter RRT checks in `ValidRcptHandler` upon email reception
- [ENHANCEMENT] WebAdmin: default to auto-generated credentials, backed by a Guice bean for the default password generation value
- [ENHANCEMENT] Add an `autoMerge` mode (defaults to false) to `SolveMailboxInconsistencies`

#### Fixes

- [FIX] S3: the fallback bucket should not apply the bucket prefix
- [FIX] ManageSieve: adhere to the filename syntax and better validate script names in `SieveFileRepository`
- JAMES-4210 ManageSieve commands after STARTTLS should work
- JAMES-4218 IMAP: selection should reset applicable flags, APPEND should trigger flag unsolicited notifications
- [FIX] IMAP: EXAMINE should honor its read-only promises
- [FIX] IMAP: rare NPE within processor error handling
- [FIX] Handle partial rows in `attachmentV2`
- [FIX] Task manager: tolerate failures for additional information, allow canceling tasks with a truncated history, purge stalled tasks upon cleanup, and handle a missing Cassandra history like Postgres does
- [FIX] Cassandra event store: read snapshots with a single row and include the snapshot in the event batch
- [FIX] `CassandraMailRepository` should be more resilient to extra large mail repositories
- [FIX] `ClearMailRepository` should count only once
- [FIX] Avoid a 500 upon mail repository download
- [FIX] Include more IP ranges in WebPush target validation
- [FIX] Prevent potentially blocking calls upon uploads
- [FIX] JMAP `DownloadRoutes`: avoid a noisy benign `IllegalStateException` upon mid-stream errors
- [FIX] `ICALAttributeDTO` should sanitize an invalid dtstamp
- [FIX] `MessageManager::setFlags` should support overlapping ranges
- JAMES-4214 Pass the message rather than an `InputStream` so that several body parts can be read
- [FIX] RabbitMQ: allow disabling the notification queue auto-delete in favor of `x-expires`
- [FIX] Cassandra folder rename: base decisions on a truth table, use SERIAL for the initial picture, and upfront read the mailbox and its children
- [FIX] User rename: handle submailbox edge cases, simplify the rename dance when the destination exists, and return the correct quota when the target address is not empty
- [FIX] `SolveMailboxInconsistencies`: run several times until convergence, address all possible failures, leverage strong consistency, and recover from duplicated or failed `mailboxPathV3` registrations
- [FIX] Account for consistency choices within `SolveMessageInconsistenciesService` and `RecomputeMailboxCountersService`
- [FIX] Cross domain RRT was listing non existing addresses
- [FIX] Prevent PostgreSQL pool poisoning
- [FIX] `findNonPersonalMailboxes`: handle null rights
- [FIX] `StoreMailboxManager::renameSubMailboxes` should change the namespace
- [FIX] Quote usernames correctly

#### Enhancements

- [ENHANCEMENT] Prevent FETCH fragmentation
- [METRICS] Measure LWT time for UID and ModSeq
- [AUDIT TRAIL] Log the allocated messageId
- [DOCUMENTATION] Provide backup guidance

#### Upgrades

- [UPGRADE] ActiveMQ 6.2.6 → 6.2.7 and Artemis 2.53.0 → 2.55.0 (fixes several CVEs)
- [UPGRADE] RabbitMQ amqp-client 5.25.0 → 5.33.1
- [UPGRADE] jsoup 1.20.1 → 1.23.1. Note that null characters (`\0`) in HTML are now replaced or removed during tokenization rather than preserved.
- Bump `org.postgresql:postgresql` to latest

### TMail

#### Added

- ISSUE-2556 JMAP upload from URL extension: specification, implementation, Guice bindings for all TMail applications, contract tests and documentation. Opt-in through `upload.from.url.enabled`.
- New **migration proxy** application: an IMAP front proxy routing each user to the legacy or to the new backend during a migration, closing IMAP sessions upon migration. Docker images are published, and it is documented.
- ISSUE-2570 Allow not deduplicating in transit blobs, so that mails in transit do not outlive their processing (`mailprocessing.deduplication.enabled=false`)
- ISSUE-2405 Team mailbox migration via scoped IMAP login
- ISSUE-2515 WebAdmin routes to manage mailing lists (read and write)
- ISSUE-2488 Ease the configuration of the mailing list DN
- ISSUE-2464 OBM mailing list: handle the `externalContactEmail` attribute
- [ENHANCEMENT] Support subaddressing for LDAP mailing lists
- ISSUE-2536 WebAdmin: expose the sum of quota, globally and per domain
- ISSUE-2479 WebAdmin: `POST /users?action=reindex` to reindex all users
- ISSUE-2375 WebAdmin tasks to provision the email templates of a reference account into the `Templates` mailbox of a user or of every user of a domain
- ISSUE-2436 New `IsDmarcReportWithIssues` and `IsDmarcReportWithoutIssues` mailet matchers
- Scribe: a dedicated AI chat completion client and its own configuration (`scribe.url`, `scribe.token`), decoupled from the RAG configuration
- ISSUE-2514 Restrict Scribe and the AI label classifier to paying SaaS users through the `applyWhen` filter (`com.linagora.tmail.saas.filter.SaaSPayingUser`)
- ISSUE-2574 Set a distinct `User-Agent` header for DAV clients
- JAMES-4210 Adopt the shared James SASL SPI for TMail IMAP and SMTP, with TMail PLAIN SASL defaults
- Allow sending from, and accepting emails for, aliases of team mailboxes

#### Fixes

- ISSUE-2407 Naming strategy isolation for the event bus notification path: a notification is now only published onto the channels of the event bus that dispatched it
- ISSUE-2508 Downgrade `CalDavCollect` parse failures to WARN
- ISSUE-2456 Close TMail AMQP consumers upon shutdown
- ISSUE-2556 Redact sensitive tokens from remote URLs upon logs
- [FIX] `IllegalStateException` when a download stream fails after commit
- [FIX] `IndexOutOfBoundsException` in `DownloadRoutes` for multi-mailbox messages
- [FIX] `UnsupportedOperationException` when copying calendars with custom `X-` properties
- [FIX] Calendar copy should preserve the timezone binding
- [FIX] NPE in `FirebasePushListener` when the messaging error code is null
- [FIX] `AmqpUri`: `setUri(URI)` no longer throws `URISyntaxException`
- [FIX] Harden Sent contact indexing
- JAMES-4210 Fix a startup race between the Redis binding and the pub/sub subscription

#### Enhancements

- ISSUE-2462 Reuse the James JMAP OIDC authentication implementation. The TMail configuration keys, challenge realm, Redis key prefixes and backchannel logout route are preserved.
- [ENHANCEMENT] `CalDavCollect`: take RRT into account to check the user
- [ENHANCEMENT] WebAdmin: `password.generate` defaults to `false`, preserving the historical TMail behaviour despite the James default change
- [ENHANCEMENT] AI: `openrag.ssl.trust.all.certs` and `scribe.ssl.trust.all.certs` now default to `false` (secure by default)
- [ENHANCEMENT] AI: reorganize the AI packages while keeping the historical fully qualified class names for `AIBaseModule`, `RagDeletionModule`, `LlmClassifierListener`, `RagListener`, `AIBotMailet` and `RecipientsContain`
- [ENHANCEMENT] Allow configuring the SaaS subscription queue names
- ISSUE-2556 Bound the remote decompressor allocation
- ISSUE-2437 Better document the JMAP ecosystem endpoint
- [REFACTOR] Introduce `ManagedRabbitMQConsumer`

## [1.0.19] - 2026-06-08

### James

#### Added

- JAMES-4209 CassandraMessagesDAOV3: optionally write recovery infos
- JAMES-4204 WebAdmin route to restore a mailbox from a backup zip file (with a `force` parameter)
- JAMES-4205 Create default mailboxes after OIDC login
- Support StartTLS, SSL and Proxy protocol for LMTP
- [ENHANCEMENT] Attribute for forwarded mail

#### Fixes

- JAMES-4207 MANAGESIEVE: do not announce capabilities after authentication
- JAMES-4206 Don't log a stacktrace on every ManageSieve logout
- [FIX] Quota recomputation should trigger a `QuotaUpdate` event
- [FIX] MOVE/COPY should not exceed batch size in published events
- Implement negative ACL for JMAP

#### Enhancements

- JAMES-4212 Use a string-based representation for groups
- [ENHANCEMENT] Reduce WebAdmin validation boilerplate
- [ENHANCEMENT] Log stacktraces in DEBUG mode for protocol level failures

#### Upgrades

- [UPGRADE] ical4j 4.1.1 → 4.2.5
- [UPGRADE] commons-text 1.13.1 → 1.15.0
- [UPGRADE] commons-configuration2 2.12.0 → 2.15.0

### TMail

#### Added

- ISSUE-2346 Unauthenticated blob access
- ISSUE-2411 WebAdmin routes to manage JMAP settings (CRUD) and a settings statistics report
- ISSUE-2374 New `SuspiciousDisplayName` and `SuspiciousDomainInDisplayName` mailet matchers (anti-phishing)
- ISSUE-2383 Support signature templates with the combined user repository

#### Fixes

- ISSUE-2407 Do not dispatch Label events on the notification bus
- ISSUE-2391 `CalDavCollect` should not log an error upon malformed organizer
- [FIX] Use the `{{input}}` placeholder for the remote LLM prompt
- [FIX] `RedisOidcToken`: correctly `setReadFrom` for cluster topology

#### Enhancements

- [ENHANCEMENT] Add a Gaussian date decay to the default search sort in order to favour recent messages (distributed, opt-in)
- [ENHANCEMENT] Configurable language for on-prem deployments
- ISSUE-2407 Logs now include the event bus name
- ISSUE-2347 Allow to configure the Redis commands timeout

## [1.0.18] - 2026-05-07

### James

#### Added

- JAMES-4203 Identity events: events are now emitted on identity create/update/delete, enabling reactive integrations via `CustomIdentityDAO`
- JAMES-3893 Allow deleting identities via WebAdmin
- JAMES-4200 ActiveMQ: configuration option to adjust usage limit (`activemq.usageLimit`)

#### Fixes

- JAMES-4193 Correct BoringSSL TLS 1.3 cipher suite sanitizing that inadvertently disabled TLS 1.3
- JAMES-4182 Fix a blocking call in `CassandraAttachmentMapper::loadAttachmentContent`
- [FIX] Improve leak management when an error occurs during blob operations

#### Enhancements

- [ENHANCEMENT] `LeakAware`: explicit resource naming for better auditability of resource leaks
- JAMES-4182 ZstdBlobStoreDAO: new blob store implementation using Zstd compression (S3, file, Cassandra, Postgres backends)
- JAMES-4202 OpenSearch: group single UID clauses into a single term query for better performance
- JAMES-4123 Improvement for deleted message search override: handle the `ALL` criterion

#### Upgrades

- [UPGRADE] Netty 4.1.126.Final → 4.1.132.Final (CVE-2025-67735)
- [UPGRADE] ActiveMQ 6.2.4 → 6.2.5 (CVE-2026-41044, CVE-2026-41043, CVE-2026-40466)
- [UPGRADE] Spark Java 3.0.2 → 3.0.4 (CVE-2026-1605)
- [UPGRADE] MIME4J 0.8.13 → 0.8.14
- [UPGRADE] RSpamD 3.12.0 → 3.14.3
- [UPGRADE] kvrocks 2.12.1 → 2.15.0
- Bump `org.postgresql:postgresql` to latest

### TMail

#### Added

- ISSUE-2325 Domain-based signature engine: webadmin routes to manage per-domain signature templates, Cassandra and Postgres repository implementations, Guice wiring, flexible `IdentityProvisionListener` templates, and full documentation
- ISSUE-2325 Webadmin routes to modify all signatures of domain users at once
- ISSUE-2265 IMAP extension for Identity: expose identity email address in IMAP metadata via `IdentityMetadataListener`; avoid provisioning inbox on identity delete
- ISSUE-2351 `RestrictiveCalDavCollectIntegration`: restrictive CalDAV collection integration tests
- ISSUE-1216 Webadmin: Multi-tenant friendly tasks
- ISSUE-1180 User Data Tiering via tasks
- ISSUE-2327 Leverage optional URL loading for label categorization

#### Fixes

- ISSUE-2331 Fix `KeywordQueryView` with Row-Level Security (RLS) in Postgres
- ISSUE-2377 `TmailLocalDelivery` mailet now supports `onMailetException`
- ISSUE-2350 `CalendarEvent/reply`: be resilient when the referenced event is missing
- [FIX] `CalendarEvent/reply`: warn upon cancelled events
- [FIX] `CalendarEvent/parse`: be lenient on malformed addresses
- [FIX] Limit amount of collected contacts and improve parallelism
- ISSUE-2322 Fix flaky `shouldRemoveAllEmailsFromSearchEngineWhenCollectedContactWithMultipleEmailsIsDeleted` test

#### Enhancements

- ISSUE-5550 Improve RabbitMQ dead lettering
- [ENHANCEMENT] Avoid collecting spam contacts
- ISSUE-1227 Add alignment validation for `CalDavCollect`
- Bind `PopulateKeywordEmailQueryViewTask` module for Postgres app

#### Upgrades

- Switch to JDK 25
- [UPGRADE] netty-tcnative 2.0.65.Final → 2.0.77.Final

## Unspecified

- Renamed listener from `[LlmMailPrioritizationClassifierListener]` to `[LlmMailClassifierListener]` for clarity (#2136)

### Added
- ISSUE-1 Add a memory server application
- ISSUE-6 Produce simple docker images with JIB
