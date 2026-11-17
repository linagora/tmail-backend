#!/bin/bash
# A mail client opens a new IMAP connection: STARTTLS, LOGIN (LDAP), LIST, SELECT INBOX, FETCH of the flags, LOGOUT.
. "$(dirname "$0")/lib.sh"

py 'import lib
def probe():
    imap = lib.imap_login()
    try:
        typ, boxes = imap.list()
        if typ != "OK" or not boxes:
            raise lib.Fail(f"LIST: {typ} {boxes}")
        typ, data = imap.select("INBOX")  # read-write, as a mail client (not EXAMINE)
        if typ != "OK":
            raise lib.Fail(f"SELECT INBOX: {typ} {data}")
        exists = int(data[0])
        if exists:
            typ, data = imap.fetch(f"1:{min(exists, 20)}", "(UID FLAGS)")
            if typ != "OK":
                raise lib.Fail(f"FETCH: {typ} {data}")
        imap.logout()
        return f"logged in, {len(boxes)} mailboxes, INBOX selected ({exists} mails)"
    except lib.imaplib.IMAP4.error as e:
        raise lib.Fail(f"IMAP: {e}")
lib.main(probe)'
