# What the control scripts saw (2026-11-17, tmail-backend 87695fb1c)

Each component was stopped whole (`docker compose stop` of its services in topology.yaml), every script was run
(`run_all.sh`, in parallel), then the component was restarted and its `healthy` command awaited. These are observations,
not verdicts: twake-chaos judges claims.yaml.

## On the healthy stack

- **spam_check fails on the healthy stack: Rspamd never checks a mail.** compose.yaml gives the rspamd container
  `PASSWORD: admin`, and `conf/rspamd/worker-controller.inc` reads `password = "{= env.PASSWORD =}"`. But Rspamd's
  templates only see the variables prefixed `RSPAMD_` (`RSPAMD_PASSWORD`), so the controller's password is empty
  (`rspamadm configdump worker`: `password = "";`). Every scan from tmail is answered `{"error":"Unauthorized"}`
  (RspamdScanner, `onMailetException: ignore`), and every mail is delivered unchecked in the Inbox. This is a bug of the
  stack, not of the probe: rspamd called directly (`rspamc` in the container) gives the GTUBE mail `Action: reject,
  Spam: true`, which tmail would file in Spam. Until it is fixed, spam_check fails all the time, so the rspamd claim
  cannot be told apart.
- **save_draft failed once out of about 60 parallel runs** with `serverFail: Failed executing SELECT mailboxid,uidvalidity
  FROM mailboxpathv3 ...`, `CASWriteUnknownException: CAS operation result is unknown - proposal was not accepted by a
  quorum`. That is the SERIAL read of a mailbox path (LWT) contending with the concurrent requests of the other probes on
  the same account. The client gets an error and no retry happens. It never showed when the scripts ran one at a time.
- Each script takes 0.1–3 s when run alone, 0–7 s when all 18 run at once.

## Per component: what failed (✗) against the claimed `impacts`

| Component down | Failed | Claimed but kept working | Failed but not claimed |
|---|---|---|---|
| cassandra | all 18 | – | – |
| s3 | read_mail, upload_download, save_draft, send_mail, smtp_submission, receive_mail, spam_check | – | **receive_mail** (and spam_check): the SMTP DATA is answered `451 4.0.0 Error processing message` (the mail is stored in S3 before it is queued) |
| opensearch | search, quick_filters, flagged_view | – | – |
| rabbitMQ | send_mail, smtp_submission, receive_mail, spam_check, save_draft, flagged_view, imap_idle, imap_session_update, jmap_push | – | **save_draft, flagged_view**: every mail write (Email/set create / update) fails with `serverFail: Retries exhausted: 2/2` (the event bus). **imap_idle, imap_session_update, jmap_push** fail at their trigger: the other client's change (a JMAP Email/set) fails, so there is nothing to notify of. Reads, search, IMAP login and select keep working. |
| redis | imap_idle, imap_session_update, jmap_push, save_draft, send_mail, flagged_view, imap_connection | – | **save_draft, send_mail, flagged_view**: every mail write fails after ~25 s (`serverFail: Did not observe any item or terminal signal within 10000ms in 'flatMap'`). **imap_connection**: IMAP `SELECT INBOX` is answered `NO SELECT processing failed` (EXAMINE works), so no mail client can open a mailbox. Every JMAP request also takes ~6 s longer (the OIDC token cache), and deliveries take 30–40 s instead of 1–2 s. The realtime probes fail at SELECT or at their trigger, not only at the notification. |
| rspamd | spam_check | – | – (spam_check fails on the healthy stack too, see above). Every delivery takes ~23 s instead of 1–2 s while Rspamd is down, since each mail waits for the call to Rspamd to fail. |
| ldap (tokens still held) | sso_sign_in, basic_sign_in, smtp_submission, receive_mail, spam_check, imap_connection, imap_idle, imap_session_update, admin_users | **send_mail**: a mail sent by JMAP was delivered to the colleague, though only after 56 s | **basic_sign_in** (`HTTP 500`: Basic auth checks the password in LDAP), **admin_users** (WebAdmin `GET /users`: `HTTP 500 LDAPException resultCode=91`) |
| ldap (> 120 s) | all 18 | – | mailbox_list, read_mail, upload_download, save_draft, search, quick_filters, flagged_view, jmap_push: LemonLDAP's access tokens live 120 s (`oidcRPMetaDataOptionsAccessTokenExpiration`), the `james` client has no refresh token, and a new one needs the password checked in LDAP (`invalid_grant`). After two minutes of LDAP down, no web user has a valid token left, so JMAP is lost to all of them. The claim says "new token introspection fails"; in practice, every token becomes a new one within 120 s. |

The ldap outcome depends on how long the outage has lasted when the scripts run. The JMAP probes reuse the token they
hold (as a signed-in web client does) and only fail once it has expired.

## Recovery (accidentRecovery: live)

Each time, every feature worked again without restarting tmail (spam_check aside):

- cassandra: errors for ~30 s after `nodetool status` shows 3 UN (mailbox_list, save_draft, send_mail,
  receive_mail, quick_filters, IMAP), then all good.
- redis: still slow, and IMAP SELECT still refused, right after `cluster_state:ok`; all good about 45 s later.
- ldap: topology.yaml has no `healthy` command for ldap, and tmail's LDAP calls still failed 5 s after
  `docker start` (slapd starting). All good 30 s later. twake-chaos may start its next scenario too early.
- s3: the chaos S3 keeps its blobs **in memory** (`S3BACKEND: mem`). A restart loses every blob written before it,
  so every mail stored before the outage is gone for good: `Email/get` of a list that holds one of them fails whole
  (`serverFail: Blob not found ... in bucket default`), so the web client's Inbox view breaks. This is caused by the
  stack, not by tmail. The probes avoid it: the deliveries are looked for among the mails received since the run
  started, and read_mail creates its mail again when its content is gone (it says so in its last line).
