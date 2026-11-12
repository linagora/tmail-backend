# Postgres Twake Mail backend 1.0.23 release instructions

These instructions cover changes since 1.0.22, including the 1.0.22.1 and 1.0.22.2 tags.

## Schema migration

This release requires no Postgres schema change and no OpenSearch mapping change.

## Before upgrading

These changes stop the server from starting when they are not handled:

- [SMTPUTF8](#smtputf8): remove `SmtpUtf8MailHook` and `SmtpUtf8RcptHook` from a custom `smtpserver.xml`.
- [Delayed sends](#delayed-sends): with the RabbitMQ mail queue, `delay.sends.enabled=true` or an SMTP `FUTURERELEASE`
  handler is refused.

## Behaviour changes

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

With the RabbitMQ mail queue, which ignores delays, the server now refuses to start when `delay.sends.enabled=true` is
set in `jmap.properties`, or when an SMTP `FUTURERELEASE` handler is configured. The embedded Artemis mail queue is not
concerned. With delayed sends disabled (the default), `EmailSubmission/set` rejects a non-zero `holdFor` or
`holdUntil` with `invalidArguments` instead of sending at once.

### populateEmailQueryView task

`POST /mailboxes?task=populateEmailQueryView` now processes every mailbox of the registered domains, and reports
`processedMailboxCount` and `failedMailboxCount` instead of `processedUserCount` and `failedUserCount`. Nodes not yet
upgraded cannot read the details of a task run by an upgraded node: run it once every node is upgraded.

### OpenSearch hasAttachment

`hasAttachment` no longer depends on `opensearch.indexAttachments`, which the Postgres sample configuration
(`sample-configuration/distributed/opensearch.properties`) sets to `false`. With `opensearch.indexAttachments=false`, reindex your mails for `hasAttachment` to apply to existing
messages.

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

The Twake Space extension is now available on the Postgres server. It gives each TwakeSpace space a team mailbox,
from the `space` and `admin-panel` events, and reports its mail on the `activity` exchange. It requires RabbitMQ and is
enabled by creating `space.properties` with `enabled=true`: the Postgres sample configuration does not ship this file.

Purging the team mailboxes of deleted spaces needs an external cron calling `POST /spaces?action=purge`. See the
[Twake Space configuration](../../../../../../../docs/modules/ROOT/pages/tmail-backend/configure/twake-space.adoc) for
the configuration keys, the RabbitMQ permissions and an example of purge cron, and the "Migrating from the Twake Mail side
service" section of [upgrade-instructions.md](../../../../../../../upgrade-instructions.md) to replace
`twake-mail-side-service`.

## TMail deployment

Update the Postgres server image to `linagora/tmail-backend:postgresql-1.0.23`. The image is still built with jib and
runs as root: the non-root change of the distributed image does not apply.

## References

* Official Twake Mail release notes: https://github.com/linagora/tmail-backend/releases/tag/1.0.23
* James upgrade instructions: https://github.com/apache/james-project/blob/c4e8abb7f6e19dafc765e1398c2b4ad00ace865b/upgrade-instructions.md
