package com.linagora.tmail.james.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.vault.VaultConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedServerWithoutDeletedMessageVaultTest {
    @RegisterExtension
    static JamesServerExtension testExtension =  new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .enableSingleSave())
            .searchConfiguration(SearchConfiguration.openSearch())
            .mailbox(new MailboxConfiguration(false))
            .eventBusKeysChoice(EventBusKeysChoice.RABBITMQ)
            .vaultConfiguration(VaultConfiguration.DEFAULT)
            .build())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule()))
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Test
    void distributedJamesServerShouldStartWithoutDeletedMessageVault(GuiceJamesServer server) {
        assertThat(server.isStarted()).isTrue();
    }
}
