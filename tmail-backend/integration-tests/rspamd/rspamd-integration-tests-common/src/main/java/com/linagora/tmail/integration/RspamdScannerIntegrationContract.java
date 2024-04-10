package com.linagora.tmail.integration;

import static org.apache.james.utils.TestIMAPClient.INBOX;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.time.Duration;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.MimeMessageUtil;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class RspamdScannerIntegrationContract {
    protected static final Domain DOMAIN = Domain.of("example.com");
    protected static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    protected static final Username ALICE = Username.fromLocalPartWithDomain("alice", DOMAIN);
    protected static final String BOB_PASSWORD = "pass";
    protected static final String ALICE_PASSWORD = "pass";
    protected static final ConditionFactory AWAIT_CONDITION = Awaitility.await().atMost(Duration.ofSeconds(30));
    private SMTPMessageSender messageSender;
    private TestIMAPClient imapClient;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
        int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
        messageSender = new SMTPMessageSender(DOMAIN.asString())
            .connect("127.0.0.1", smtpPort);
        imapClient = new TestIMAPClient().connect("127.0.0.1", imapPort);

        DataProbeImpl dataProbe = server.getProbe(DataProbeImpl.class);
        dataProbe.fluent();
        dataProbe.addDomain(DOMAIN.asString());
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxPath.inbox(BOB));
        mailboxProbe.createMailbox(MailboxPath.forUser(BOB, "Spam"));

        mailboxProbe.createMailbox(MailboxPath.inbox(ALICE));
        mailboxProbe.createMailbox(MailboxPath.forUser(ALICE, "Spam"));
    }

    @Test
    void messageShouldMoveToInboxWhenNotSpam() throws IOException {
        assertThatCode(() -> messageSender.authenticate(BOB.asString(), BOB_PASSWORD)
            .sendMessage(BOB.asString(), ALICE.asString()))
            .doesNotThrowAnyException();

        imapClient.login(ALICE, ALICE_PASSWORD)
            .select(INBOX)
            .awaitMessage(AWAIT_CONDITION);
    }

    @Test
    void messageShouldMoveToSpamWhenSpam() throws Exception {
        MimeMessage mimeMessage = MimeMessageUtil.mimeMessageFromStream(
            ClassLoader.getSystemResourceAsStream("spam8.eml"));

        Mail mail = FakeMail.builder()
            .name("spam")
            .sender(BOB.asMailAddress())
            .recipient(ALICE.asMailAddress())
            .mimeMessage(mimeMessage)
            .build();

        messageSender.authenticate(BOB.asString(), BOB_PASSWORD)
            .sendMessage(mail);

        imapClient.login(ALICE, ALICE_PASSWORD)
            .select("Spam")
            .awaitMessage(AWAIT_CONDITION);
    }

}
