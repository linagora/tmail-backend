package com.linagora.openpaas.encrypted;

import org.apache.james.jmap.JMAPTestingConstants;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.security.Provider;
import java.security.Security;

public class EncryptedMessageManagerTest implements EncryptedMessageManagerContract {
    private MailboxManager mailboxManager;
    private MessageManager messageManager;
    private KeystoreManager keystoreManager;
    private MailboxPath path;
    private MailboxSession session;
    private EncryptedMessageManager testee;

    @BeforeAll
    static void setUpAll() throws Exception {
        String bouncyCastleProviderClassName = "org.bouncycastle.jce.provider.BouncyCastleProvider";
        Security.addProvider((Provider)Class.forName(bouncyCastleProviderClassName).getDeclaredConstructor().newInstance());
    }

    @BeforeEach
    void setUp() throws Exception {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        session = mailboxManager.createSystemSession(JMAPTestingConstants.BOB);
        path = MailboxPath.inbox(session);
        MailboxId mailboxId = mailboxManager.createMailbox(path, session).get();
        messageManager = mailboxManager.getMailbox(mailboxId, session);

        keystoreManager = new InMemoryKeystoreManager();
        testee = new EncryptedMessageManager(messageManager, keystoreManager);
    }

    @Override
    public KeystoreManager keystoreManager() {
        return keystoreManager;
    }

    @Override
    public MailboxManager mailboxManager() {
        return mailboxManager;
    }

    @Override
    public MailboxPath path() {
        return path;
    }

    @Override
    public MailboxSession session() {
        return session;
    }

    @Override
    public MessageManager messageManager() {
        return messageManager;
    }

    @Override
    public EncryptedMessageManager testee() {
        return testee;
    }
}
