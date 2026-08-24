# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)

## [Unreleased]

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
