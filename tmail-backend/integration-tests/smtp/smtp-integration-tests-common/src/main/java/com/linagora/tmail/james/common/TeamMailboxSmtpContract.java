package com.linagora.tmail.james.common;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
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
    private SMTPMessageSender messageSender;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        Port port = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
        messageSender = new SMTPMessageSender("domain.tld");
        messageSender.connect("127.0.0.1", port);

        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN.asString())
            .addUser(BOB.asString(), BOB_PASSWORD);

        server.getProbe(TeamMailboxProbe.class)
            .create(MARKETING_TEAM_MAILBOX)
            .addMember(MARKETING_TEAM_MAILBOX, BOB);
    }

    @Test
    void smtpShouldAcceptSubmissionFromATeamMailbox() {
        assertThatCode(() -> messageSender.authenticate(BOB.asString(), BOB_PASSWORD)
            .sendMessage(MARKETING_TEAM_MAILBOX.asMailAddress().asString(), BOB.asString()))
            .doesNotThrowAnyException();
    }
}

