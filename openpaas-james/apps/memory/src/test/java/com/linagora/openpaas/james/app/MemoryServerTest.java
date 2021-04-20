package com.linagora.openpaas.james.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerContract;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.draft.JmapJamesServerContract;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.openpaas.encrypted.MailboxConfiguration;
import com.linagora.openpaas.encrypted.MailboxManagerClassProbe;

class MemoryServerTest implements JamesServerContract, JmapJamesServerContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir -> MemoryConfiguration.builder()
        .workingDirectory(tmpDir)
        .configurationFromClasspath()
        .mailbox(new MailboxConfiguration(false))
        .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class)))
        .build();

    @Disabled("POP3 server is disabled")
    @Test
    public void connectPOP3ServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) {
        // POP3 server is disabled
    }

    @Disabled("LMTP server is disabled")
    @Test
    public void connectLMTPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) {
        // LMTP server is disabled
    }

    @Test
    public void shouldUseMemoryMailboxManager(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(MailboxManagerClassProbe.class).getMailboxManagerClass())
            .isEqualTo(InMemoryMailboxManager.class);
    }
}