# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)

## [Unreleased]

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
