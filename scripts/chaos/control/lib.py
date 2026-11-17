"""Shared by the control scripts (IMAP, SMTP), imported through lib.sh's `py`. Standard library only.

Each probe raises Fail (the feature does not work) or Broken (the probe itself cannot run); `main` turns them into the
exit codes of the contract (1, 2) and prints the reason last.
"""
import email.utils
import imaplib
import os
import smtplib
import socket
import ssl
import sys

# one network operation (a slow answer is still an answer: twake-chaos times the scripts)
TIMEOUT = 30


class Fail(Exception):
    pass


class Broken(Exception):
    pass


def env(name):
    value = os.environ.get(name)
    if not value:
        raise Broken(f"{name} is not set")
    return value


def tls_context():
    # the keystore of the image: a self-signed certificate
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return ctx


def imap_login(user=None):
    """A new IMAP connection, as a mail client: STARTTLS (plain LOGIN is refused before it), LOGIN."""
    user = user or env("CHAOS_USER")
    try:
        imap = imaplib.IMAP4(env("CHAOS_IMAP_HOST"), int(env("CHAOS_IMAP_PORT")), timeout=TIMEOUT)
    except (OSError, imaplib.IMAP4.error) as e:
        raise Fail(f"IMAP connection: {e}")
    try:
        imap.starttls(ssl_context=tls_context())
        imap.login(user, env("CHAOS_PASSWORD"))
    except (OSError, imaplib.IMAP4.error) as e:
        raise Fail(f"IMAP STARTTLS / LOGIN of {user}: {e}")
    return imap


def message(sender, rcpt, subject, body):
    return (f"From: {sender}\r\nTo: {rcpt}\r\nSubject: {subject}\r\nDate: {email.utils.formatdate()}\r\n"
            f"Message-ID: {email.utils.make_msgid(domain='chaos.test')}\r\nMIME-Version: 1.0\r\n"
            f"Content-Type: text/plain; charset=utf-8\r\n\r\n{body}\r\n")


def smtp_send(port, sender, rcpt, subject, body, login=None):
    """Sends one mail. login: (user, password) for submission (STARTTLS then AUTH), None for an inbound MX delivery."""
    try:
        smtp = smtplib.SMTP(env("CHAOS_SMTP_HOST"), port, timeout=TIMEOUT, local_hostname="probe.chaos.test")
    except (OSError, smtplib.SMTPException) as e:
        raise Fail(f"SMTP connection to {port}: {e}")
    try:
        smtp.ehlo()
        if login:
            smtp.starttls(context=tls_context())
            smtp.ehlo()
            smtp.login(*login)
        refused = smtp.sendmail(sender, [rcpt], message(sender, rcpt, subject, body).encode())
        if refused:
            raise Fail(f"SMTP refused {refused}")
        smtp.quit()
    except smtplib.SMTPAuthenticationError as e:
        raise Fail(f"SMTP AUTH on {port}: {e.smtp_code} {e.smtp_error!r}")
    except smtplib.SMTPResponseException as e:
        raise Fail(f"SMTP on {port}: {e.smtp_code} {e.smtp_error!r}")
    except (OSError, smtplib.SMTPException) as e:
        raise Fail(f"SMTP on {port}: {e}")


def jmap_flip_flag(token, account, email_id):
    """Another client (JMAP) stars / unstars a mail: the change a realtime channel has to tell."""
    import json
    get = jmap_post(token, [["Email/get", {"accountId": account, "ids": [email_id], "properties": ["keywords"]}, "g"]])
    try:
        flagged = get["methodResponses"][0][1]["list"][0]["keywords"].get("$flagged", False)
    except (KeyError, IndexError, TypeError):
        raise Fail(f"the change (JMAP Email/get) fails: {json.dumps(get)[:200]}")
    res = jmap_post(token, [["Email/set", {"accountId": account,
                                           "update": {email_id: {"keywords/$flagged": None if flagged else True}}}, "u"]])
    if res["methodResponses"][0][0] == "error" or res["methodResponses"][0][1].get("notUpdated"):
        raise Fail(f"the change (JMAP Email/set) fails: {json.dumps(res['methodResponses'][0])[:200]}")


def jmap_post(token, calls):
    import json
    import urllib.error
    import urllib.request
    body = json.dumps({"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"], "methodCalls": calls}).encode()
    req = urllib.request.Request(env("CHAOS_JMAP_URL") + "/jmap", data=body, method="POST", headers={
        "Authorization": "Bearer " + token, "Content-Type": "application/json",
        "Accept": "application/json; jmapVersion=rfc-8621"})
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            return json.load(r)
    except (urllib.error.URLError, OSError) as e:
        raise Fail(f"the change (JMAP) fails: {e}")


def imap_msn_of(imap, email_id):
    """The sequence number of a mail of the selected mailbox, by its EMAILID (RFC 8474: the JMAP id): no search needed."""
    typ, data = imap.fetch("1:*", "(EMAILID)")
    if typ != "OK":
        raise Fail(f"FETCH EMAILID: {typ} {data}")
    for line in data:
        line = line.decode() if isinstance(line, bytes) else str(line)
        if f"EMAILID ({email_id})" in line:
            return int(line.split()[0])
    raise Broken(f"the fixture {email_id} is not in the INBOX seen by IMAP")


def imap_realtime(mode, token, account, email_id, budget):
    """An IMAP client is told of a change made by another client (JMAP stars the mail email_id of the INBOX).
    mode idle: while in IDLE, the server pushes `* n FETCH (FLAGS ...)`. mode noop: the session selected, a NOOP returns it."""
    import time
    deadline = time.monotonic() + budget
    imap = imap_login()
    typ, data = imap.select("INBOX")
    if typ != "OK":
        raise Fail(f"SELECT INBOX: {typ} {data}")
    msn = imap_msn_of(imap, email_id)
    expected = f"* {msn} FETCH".encode()
    if mode == "idle":
        tag = imap._new_tag()
        imap.send(tag + b" IDLE\r\n")
        line = imap._get_line()
        if not line.startswith(b"+"):
            raise Fail(f"IDLE refused: {line[:120]!r}")
        jmap_flip_flag(token, account, email_id)
        while True:
            left = deadline - time.monotonic()
            if left <= 0:
                raise Fail(f"IDLE: no notification of the change after {budget}s")
            imap.sock.settimeout(left)
            try:
                line = imap._get_line()
            except (socket.timeout, TimeoutError, ssl.SSLError):
                raise Fail(f"IDLE: no notification of the change after {budget}s")
            if line.startswith(expected) and b"FLAGS" in line:
                imap.sock.settimeout(TIMEOUT)
                imap.send(b"DONE\r\n")
                return f"IDLE notified in {budget - left:.1f}s: {line.decode(errors='replace')}"
    else:
        jmap_flip_flag(token, account, email_id)
        while time.monotonic() < deadline:
            typ, data = imap.noop()
            for resp in imap.untagged_responses.pop("FETCH", []):
                resp = resp.decode() if isinstance(resp, bytes) else str(resp)
                if resp.startswith(f"{msn} ") and "FLAGS" in resp:
                    imap.logout()
                    return f"the selected session sees the change: * {resp}"
            time.sleep(0.3)
        raise Fail(f"the selected IMAP session does not see the change after {budget}s (NOOP)")


def ws_push(token, account, email_id, budget):
    """The web client's push (JMAP over WebSocket, RFC 8887, WebSocketPushEnable): a StateChange of Email arrives once
    another client changes a mail. The change is made again every 3 s (the enablement is asynchronous)."""
    import base64
    import json
    import time
    import urllib.parse
    url = urllib.parse.urlparse(env("CHAOS_JMAP_URL"))
    deadline = time.monotonic() + budget
    try:
        sock = socket.create_connection((url.hostname, url.port or 80), timeout=TIMEOUT)
    except OSError as e:
        raise Fail(f"WebSocket connection: {e}")
    key = base64.b64encode(os.urandom(16)).decode()
    sock.sendall((f"GET /jmap/ws HTTP/1.1\r\nHost: {url.hostname}:{url.port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                  f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: jmap\r\n"
                  f"Authorization: Bearer {token}\r\n\r\n").encode())
    buf = b""
    try:
        while b"\r\n\r\n" not in buf:
            chunk = sock.recv(4096)
            if not chunk:
                break
            buf += chunk
    except OSError as e:
        raise Fail(f"WebSocket handshake: {e}")
    head, _, buf = buf.partition(b"\r\n\r\n")
    status = head.split(b"\r\n")[0]
    if b" 101 " not in status:
        raise Fail("WebSocket handshake: " + (status.decode(errors="replace") or "no answer"))

    def send(obj):
        payload = json.dumps(obj).encode()
        mask = os.urandom(4)
        header = bytes([0x81])
        n = len(payload)
        header += bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + n.to_bytes(2, "big")
        sock.sendall(header + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))

    def recv_exact(n):
        nonlocal buf
        while len(buf) < n:
            chunk = sock.recv(65536)
            if not chunk:
                raise Fail("WebSocket closed by the server")
            buf += chunk
        out, buf = buf[:n], buf[n:]
        return out

    send({"@type": "WebSocketPushEnable", "dataTypes": ["Email"]})
    next_change = 0.0
    changes, frames = 0, []
    while True:
        now = time.monotonic()
        if now >= deadline:
            raise Fail(f"WebSocket push: no StateChange after {budget}s ({changes} changes made, frames received: {frames[:3]})")
        if now >= next_change:
            jmap_flip_flag(token, account, email_id)
            changes += 1
            next_change = now + 3
        sock.settimeout(max(0.1, min(deadline, next_change) - now))
        try:
            b0, b1 = recv_exact(2)
        except (socket.timeout, TimeoutError):
            continue
        sock.settimeout(TIMEOUT)
        n = b1 & 0x7F
        if n == 126:
            n = int.from_bytes(recv_exact(2), "big")
        elif n == 127:
            n = int.from_bytes(recv_exact(8), "big")
        payload = recv_exact(n)
        opcode = b0 & 0x0F
        frames.append((opcode, payload[:80]))
        if opcode == 8:
            raise Fail(f"WebSocket closed by the server: {payload[2:].decode(errors='replace')}")
        if opcode != 1:
            continue
        msg = json.loads(payload)
        if msg.get("@type") == "StateChange" and "Email" in msg.get("changed", {}).get(account, {}):
            sock.close()
            return f"StateChange pushed in {budget - (deadline - time.monotonic()):.1f}s"
        if msg.get("@type") == "RequestError":
            raise Fail(f"WebSocket push: {payload[:200]!r}")


def main(probe):
    try:
        print(f"OK: {probe()}")
        sys.exit(0)
    except Fail as e:
        print(f"FAIL: {e}")
        sys.exit(1)
    except Broken as e:
        print(f"BROKEN: {e}")
        sys.exit(2)
    except (socket.timeout, TimeoutError) as e:
        print(f"FAIL: timeout ({e})")
        sys.exit(1)
