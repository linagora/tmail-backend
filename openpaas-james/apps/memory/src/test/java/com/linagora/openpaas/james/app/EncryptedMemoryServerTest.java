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
import com.linagora.openpaas.encrypted.EncryptedMailboxManager;
import com.linagora.openpaas.encrypted.MailboxConfiguration;

class EncryptedMemoryServerTest implements JamesServerContract, JmapJamesServerContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir -> MemoryConfiguration.builder()
        .workingDirectory(tmpDir)
        .configurationFromClasspath()
        .mailbox(new MailboxConfiguration(true))
        .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class)))
        .build();

    @Disabled("POP3 server is disabled")
    @Test
    public void connectPOP3ServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        // POP3 server is disabled
    }

    @Disabled("LMTP server is disabled")
    @Test
    public void connectLMTPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
        // LMTP server is disabled
    }

    @Test
    public void shouldUseEncryptedMailboxManager(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(MailboxManagerClassProbe.class).getMailboxManagerClass())
            .isEqualTo(EncryptedMailboxManager.class);
    }
}