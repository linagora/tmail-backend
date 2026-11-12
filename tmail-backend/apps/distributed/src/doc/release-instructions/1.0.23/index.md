# Distributed Twake Mail backend 1.0.23 release instructions

These instructions cover changes since 1.0.22, including the 1.0.22.1 and 1.0.22.2 tags.

## Schema migration

This release requires no Cassandra schema change and no OpenSearch mapping change.

## Before upgrading

These changes stop the server from starting when they are not handled:

- [Ports](#non-root-user): the container runtime must let the non-root user bind ports below 1024, or the binds move
  above 1024.
- [Team mailbox events listener](#team-mailbox-events-listener): remove `TeamMailboxEventsListener` from `listeners.xml`.
- [SMTPUTF8](#smtputf8): remove `SmtpUtf8MailHook` and `SmtpUtf8RcptHook` from a custom `smtpserver.xml`.
- [Delayed sends](#delayed-sends): `delay.sends.enabled=true` or an SMTP `FUTURERELEASE` handler is refused with the
  RabbitMQ mail queue.

## Docker image: non-root user

Mandatory review. Since 1.0.22.2, the `linagora/tmail-backend:distributed-*` and `distributed-ai-*` images are built
from `tmail-backend/dockerfiles/distributed/Dockerfile` instead of jib. The `postgresql-*` and `memory-*` images and the
migration proxy image are unchanged.

### JVM flags

The image passes its JVM flags on the `java` command line, as the jib image did, so `JAVA_TOOL_OPTIONS` stays
available for heap sizing or agents. The 1.0.22.2 image set them in its own `JAVA_TOOL_OPTIONS` instead, which a custom
`JAVA_TOOL_OPTIONS` replaced: deployments that added `-Dworking.directory=/root/` and
`-Dlogback.configurationFile=/root/conf/logback.xml` to their own `JAVA_TOOL_OPTIONS` to work around it can keep or
drop them.

### Non-root user

The server runs as the `james` user (uid and gid 1000), which owns every file under `/root`.

- **Ports.** The configuration shipped in the image binds ports below 1024: 25, 465 and 587 (`smtpserver.xml`), 143 and
  993 (`imapserver.xml`), and 80 (JMAP). A non-root process can only bind them when the container runtime allows
  unprivileged ports (`net.ipv4.ip_unprivileged_port_start`, set to 0 by default by Docker Engine 20.10 and later).
  Otherwise, the server fails to bind its ports at start (`Permission denied`): bind ports above 1024 in the
  configuration files and map the published ports to them.
- **Volumes.** Files mounted into the container (configuration, keystores, secrets) must be readable by uid 1000, and
  writable volumes must be writable by it. Volumes written by the former root container need a `chown 1000:1000`.
- **Architecture.** The images are published for `linux/amd64` only. The 1.0.22 jib images also shipped `linux/arm64`.

## Behaviour changes

### Team mailbox events listener

Mandatory if you use it. `com.linagora.tmail.team.events.TeamMailboxEventsListener` is now part of the Twake Space
extension: remove it from `listeners.xml` (the class no longer exists, and TMail fails to start while it is declared),
and configure it in `space.properties` instead. See the "Team mailbox events move to space.properties" section of
[upgrade-instructions.md](../../../../../../../upgrade-instructions.md). `enabled=true` loads the whole Twake Space
extension (the `tmail-space` queue and the `activity` publication). Once every node is upgraded, delete the former work
queue `mailboxEvent-workQueue-com.linagora.tmail.team.events.TeamMailboxEventsListener$TeamMailboxEventsListenerGroup`.

### Domains in A-label form

James now reads A-labels in their Unicode form: `xn--gr-zia.example` is read as `grå.example`, and domains and users
are looked up under that form. A domain created in its A-label form (`xn--`) before the upgrade is therefore no longer
found: `GET /domains` shows whether you have any.

Domains holding `ß`, `ς`, a zero width joiner or non-joiner, and A-labels decoding to a capital unknown to IDNA2003, are
now rejected, as well as usernames starting with `-` or `$`. Existing domains and accounts in these forms can no
longer be read after the upgrade, and need to be moved to another domain beforehand.

### SMTP authentication over clear-text connections

`auth.requireSSL` used to only hide the authentication capabilities over clear-text connections: clients sending
`AUTH PLAIN` or `AUTH LOGIN` anyway were still authenticated. SMTP servers setting `<requireSSL>true</requireSSL>` in
their `<auth>` block now answer them `504 Unrecognized Authentication Type` until `STARTTLS` or implicit TLS is used.
It defaults to `false` for SMTP, and the shipped `smtpserver.xml` does not set it. Deployments setting it to `true`
that intentionally allow clear-text authentication, for instance behind a load balancer terminating TLS, must set it to
`false` in the `<auth>` block of each concerned server.

### JMAP Email/query date boundaries

`Email/query` date filters now follow RFC 8621: `before` is exclusive and `after` is inclusive. Twake Mail clients page
their email lists with `before` set to the `receivedAt` of the last loaded email: with an exclusive `before`, emails
received in the same second as that email would be skipped. Keep the former boundaries by adding to `jvm.properties`
on every node:

```
james.jmap.email.query.date.boundary.backward.compatibility=true
```

The property is read at start: restart every node after changing it.

### Regex mappings

Regex mappings are now evaluated with RE2J, which supports a subset of `java.util.regex`: a regex longer than 500
characters, or using `{`, `}` (counted repetitions, `\p{..}`, `${1}` parameters), lookaround, backreferences,
possessive quantifiers, atomic groups, `\R` or `\h`, is no longer applied (an error is logged when it is evaluated),
and adding one is rejected. `james.rrt.regex.safe=false` in `jvm.properties` keeps the previous engine.

### Domain signature templates

`{ldap:<attribute>}` placeholders of domain signature templates now only resolve the attributes read for identities:
`givenName`, `sn` (or the configured `firstnameAttribute` and `surnameAttribute`), the username attribute, and the
`extraAttributes` of the `IdentityProvisionListener`. Credentials are never resolved, and values are HTML-escaped.

Templates using other attributes need them listed in the `extraAttributes` of the existing `IdentityProvisionListener`
configuration in `listeners.xml`, otherwise the placeholder stays as-is:

```xml
<listener>
  <class>com.linagora.tmail.james.jmap.event.IdentityProvisionListener</class>
  <configuration>
    <extraAttributes>telephoneNumber,title</extraAttributes>
    <!-- keep the existing defaultText, provider... -->
  </configuration>
</listener>
```

Signatures already provisioned keep their content until the template is applied again.

### PGP encrypted mailbox

Uploading a PGP key is stricter: keys that cannot encrypt, ElGamal keys above 4096 bits, and malformed keys or keys
above 1 MiB are rejected. At delivery, sign-only, expired and revoked keys are skipped with a warning. A user whose
stored keys are all unusable can no longer receive mail: the delivery fails until a valid key is uploaded or the
unusable keys are removed.

### DAV collect mailets

In `strict` and `sameDomain` modes, `CalDavCollect` and `RestrictiveCalDavCollect` now reject an invitation when one
of its `VEVENT` is not aligned with the sender or has no `ORGANIZER`, when `SENT-BY` names a delegate of another domain
than the calendar user's, and when a calendar address carries a fragment. Expect more invitations to be left out of
calendars, for instance Microsoft "on behalf of" invitations sent by a delegate of another domain.

### SMTPUTF8

SMTP and LMTP now accept `SMTPUTF8`, and IMAP advertises `UTF8=ACCEPT`. The `SmtpUtf8MailHook` and `SmtpUtf8RcptHook`
handlers are removed, as the support is now built in: remove them from a custom `smtpserver.xml`, otherwise the server
fails to start.

### Delayed sends

The RabbitMQ mail queue ignores delays: the server now refuses to start when `delay.sends.enabled=true` is set in
`jmap.properties`, or when an SMTP `FUTURERELEASE` handler is configured. With delayed sends disabled (the default),
`EmailSubmission/set` rejects a non-zero `holdFor` or `holdUntil` with `invalidArguments` instead of sending at once.

### populateEmailQueryView task

`POST /mailboxes?task=populateEmailQueryView` now processes every mailbox of the registered domains, and reports
`processedMailboxCount` and `failedMailboxCount` instead of `processedUserCount` and `failedUserCount`. Nodes not yet
upgraded cannot read the details of a task run by an upgraded node: run it once every node is upgraded.

### OpenSearch hasAttachment

`hasAttachment` no longer depends on `opensearch.indexAttachments`. Deployments setting `opensearch.indexAttachments=false`
(the default is `true`) need to reindex their mails for it to apply to existing messages.

### JMAP

- `Email/send` requires a non-empty `From` header (`invalidEmail` otherwise), checks the `From` and `Sender` addresses
  more strictly, and answers `forbiddenFrom`, `forbiddenMailFrom` and `noRecipients` instead of `serverFail`.
- Nullable TMail properties are returned as `null` instead of being left out: `Label/get` `color` and `description`,
  `CalendarEvent/parse` fields, the encrypted email detailed view.
- `Mailbox/clear` requires the `DeleteMessages` and `PerformExpunge` rights on the mailbox.
- Unauthenticated downloads and public assets are served with `X-Content-Type-Options: nosniff` and
  `Content-Security-Policy: sandbox`; HTML, SVG and XML content is served as an attachment.
- After the move to Scala 3, the key order of some JSON objects changes. Clients must not rely on it.

### JMAP EmailSubmission/set identityId

`EmailSubmission/set` now rejects an `identityId` that is not one of the user's identities (`Identity/get`) with an
`invalidProperties` error, and the email is not sent. A submission without `identityId` is still accepted, unless
`james.jmap.email.submission.missing.identity.compatibility=false` is set in `jvm.properties`.

Other JMAP changes of James: requested nullable properties are returned as `null`, `EmailSubmission/set` reports
`forbiddenFrom`, `forbiddenMailFrom` and `invalidEmail`, a property sent both as `foo` and `#foo` is an
`invalidArguments` error, and filtering rule conditions on subject and headers ignore case.

### IMAP

- With the regular path converter, team mailboxes of another domain are now listed as `#TeamMailbox.<team>@<domain>`,
  with the domain escaped. Their members' IMAP clients resynchronize these folders.
- Clients enabling `UTF8=ACCEPT` receive mailbox names in UTF-8 instead of modified UTF-7.

### Custom extensions

TMail moves from Scala 2.13 to Scala 3.10. Extensions built against TMail Scala classes, or bundling `_2.13` Scala
libraries such as play-json, must be rebuilt against 1.0.23. Custom `send.extra.validations` must also be adapted:
`EmailSubmissionSetValidation.validate` now takes the `MailboxSession` too.

## New features

### Twake Space (Optional)

The Twake Space extension gives each TwakeSpace space a team mailbox, from the `space` and `admin-panel` events, and
reports its mail on the `activity` exchange. It is enabled with `enabled=true` in `space.properties` and requires
RabbitMQ.

Purging the team mailboxes of deleted spaces needs an external cron calling `POST /spaces?action=purge`. See the
[Twake Space configuration](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/twake-space.adoc) for
the RabbitMQ permissions and an example of purge cron, and the "Migrating from the Twake Mail side service" section of
[upgrade-instructions.md](../../../../../../../upgrade-instructions.md) to replace `twake-mail-side-service`.

### AI tools (Optional)

With the AI bot module (`distributed-ai` image), `GET /ai/v1/tools` lists mailbox tools for an AI assistant
(`list_emails`, `search_emails`, `get_email_details`, `create_draft`) in the OpenAI function-calling format, and
`POST /ai/v1/tools` runs a tool call as the authenticated user. Reverse proxies in front of JMAP may need to let these
routes through.

## TMail deployment

Update the distributed server image to `linagora/tmail-backend:distributed-1.0.23`. Deployments using AI features
should use `linagora/tmail-backend:distributed-ai-1.0.23`. If running the migration proxy, update it to
`linagora/tmail-migration-proxy:1.0.23`.

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.23
* James upgrade instructions: https://github.com/apache/james-project/blob/c4e8abb7f6e19dafc765e1398c2b4ad00ace865b/upgrade-instructions.md
