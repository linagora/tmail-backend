package com.linagora.openpaas.james.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerContract;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.jmap.draft.JmapJamesServerContract;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.openpaas.encrypted.MailboxConfiguration;
import com.linagora.openpaas.encrypted.MailboxManagerClassProbe;

class DistributedServerTest implements JamesServerContract, JmapJamesServerContract {
    @RegisterExtension
    static JamesServerExtension testExtension =  new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.cassandra()
                .deduplication()
                .noCryptoConfig())
            .mailboxConfiguration(new MailboxConfiguration(false))
            .searchConfiguration(SearchConfiguration.elasticSearch())
            .build())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class)))
        .extension(new DockerElasticSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
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
    public void shouldUseCassandraMailboxManager(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(MailboxManagerClassProbe.class).getMailboxManagerClass())
            .isEqualTo(CassandraMailboxManager.class);
    }
}