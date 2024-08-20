package com.linagora.tmail.james.app;

import static com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration.BlobStoreImplName.POSTGRES;
import static org.apache.james.PostgresJamesConfiguration.EventBusImpl.IN_MEMORY;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.ClockExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerConcreteContract;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.jmap.JmapJamesServerContract;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.blob.blobid.list.SingleSaveBlobStoreDAO;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.team.TeamMailboxProbe;

class EncryptedPostgresTmailServerTest implements JamesServerConcreteContract, JmapJamesServerContract {

    static class BlobStoreDaoClassProbe implements GuiceProbe {
        private final BlobStoreDAO blobStoreDAO;

        @Inject
        public BlobStoreDaoClassProbe(BlobStoreDAO blobStoreDAO) {
            this.blobStoreDAO = blobStoreDAO;
        }

        public BlobStoreDAO getBlobStoreDAO() {
            return blobStoreDAO;
        }
    }

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<PostgresTmailConfiguration>(tmpDir ->
        PostgresTmailConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .implementation(POSTGRES)
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .enableSingleSave())
            .searchConfiguration(SearchConfiguration.scanning())
            .mailbox(new MailboxConfiguration(true))
            .eventBusImpl(IN_MEMORY)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(TeamMailboxProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(BlobStoreDaoClassProbe.class))
            .overrideWith(new DelegationProbeModule()))
        .extension(PostgresExtension.empty())
        .extension(new ClockExtension())
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
    public void shouldUseEncryptedMailboxManager(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(MailboxManagerClassProbe.class).getMailboxManagerClass())
            .isEqualTo(EncryptedMailboxManager.class);
    }

    @Test
    public void shouldUseCassandraUsersDAOAsDefault(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(UsersRepositoryClassProbe.class).getUsersDAOClass())
            .isEqualTo(PostgresUsersDAO.class);
    }

    @Test
    public void blobStoreShouldBindingCorrectWhenEncryptedBlobStoreAndSingleSave(GuiceJamesServer jamesServer) {
        BlobStoreDAO blobStoreDAO = jamesServer.getProbe(BlobStoreDaoClassProbe.class).getBlobStoreDAO();
        assertThat(blobStoreDAO.getClass())
            .isEqualTo(SingleSaveBlobStoreDAO.class);
    }

}