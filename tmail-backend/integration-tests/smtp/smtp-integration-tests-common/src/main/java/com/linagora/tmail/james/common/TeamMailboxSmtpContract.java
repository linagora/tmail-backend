package com.linagora.tmail.james.common;

import static org.apache.james.utils.TestIMAPClient.INBOX;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxProbe;

public abstract class TeamMailboxSmtpContract {
    public static final Domain DOMAIN = Domain.of("domain.tld");
    public static final TeamMailbox MARKETING_TEAM_MAILBOX = TeamMailbox.apply(DOMAIN, TeamMailboxName.fromString("marketing").toOption().get());
    public static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    public static final String BOB_PASSWORD = "123456";
    public static final ConditionFactory AWAIT_TEN_SECONDS = Awaitility.await().atMost(Duration.ofSeconds(10));

    private SMTPMessageSender messageSender;
    private TestIMAPClient imapClient;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
        int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
        messageSender = new SMTPMessageSender("domain.tld")
            .connect("127.0.0.1", smtpPort);
        imapClient = new TestIMAPClient().connect("127.0.0.1", imapPort);

        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN.asString())
            .addUser(BOB.asString(), BOB_PASSWORD);

        server.getProbe(TeamMailboxProbe.class)
            .create(MARKETING_TEAM_MAILBOX)
            .addMember(MARKETING_TEAM_MAILBOX, BOB);
    }

    @Test
    void smtpShouldAcceptSubmissionFromATeamMailbox() throws Exception {
        assertThatCode(() -> messageSender.authenticate(BOB.asString(), BOB_PASSWORD)
            .sendMessage(MARKETING_TEAM_MAILBOX.asMailAddress().asString(), BOB.asString()))
            .doesNotThrowAnyException();

        imapClient.login(BOB, BOB_PASSWORD)
            .select(INBOX)
            .awaitMessage(AWAIT_TEN_SECONDS);
    }
}

