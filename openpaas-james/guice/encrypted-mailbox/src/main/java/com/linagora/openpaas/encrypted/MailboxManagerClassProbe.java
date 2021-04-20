package com.linagora.openpaas.encrypted;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.utils.GuiceProbe;

public class MailboxManagerClassProbe implements GuiceProbe {
    private final MailboxManager mailboxManager;

    @Inject
    public MailboxManagerClassProbe(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    public Class<? extends MailboxManager> getMailboxManagerClass() {
        return mailboxManager.getClass();
    }
}
