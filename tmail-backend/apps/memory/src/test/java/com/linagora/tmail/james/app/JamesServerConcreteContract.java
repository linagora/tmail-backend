package com.linagora.tmail.james.app;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerContract;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.LmtpGuiceProbe;
import org.apache.james.modules.protocols.Pop3GuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;

interface JamesServerConcreteContract extends JamesServerContract {
    @Override
    default int imapPort(GuiceJamesServer server) {
        return server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    @Override
    default int imapsPort(GuiceJamesServer server) {
        return server.getProbe(ImapGuiceProbe.class).getImapStartTLSPort();
    }

    @Override
    default int smtpPort(GuiceJamesServer server) {
        return server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue();
    }

    @Override
    default int lmtpPort(GuiceJamesServer server) {
        return server.getProbe(LmtpGuiceProbe.class).getLmtpPort();
    }

    @Override
    default int pop3Port(GuiceJamesServer server) {
        return server.getProbe(Pop3GuiceProbe.class).getPop3Port();
    }
}
