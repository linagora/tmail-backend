/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.app;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerConcreteContract;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.jmap.JmapJamesServerContract;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.blob.blobid.list.SingleSaveBlobStoreDAO;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

class EncryptedDistributedServerTest implements JamesServerConcreteContract, JmapJamesServerContract {
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
    static JamesServerExtension testExtension =  new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .enableCache()
                .deduplication()
                .cryptoConfig(CryptoConfig.builder()
                    .password("myPass".toCharArray())
                    .salt("73616c7479")
                    .build())
                .enableSingleSave())
            .searchConfiguration(SearchConfiguration.openSearch())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .build())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(BlobStoreDaoClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class))
            .overrideWith(new DistributedEncryptedMailboxModule()))
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
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
            .isEqualTo(CassandraUsersDAO.class);
    }

    @Test
    public void blobStoreShouldBindingCorrectWhenEncryptedBlobStoreAndSingleSave(GuiceJamesServer jamesServer) {
        BlobStoreDAO blobStoreDAO = jamesServer.getProbe(BlobStoreDaoClassProbe.class).getBlobStoreDAO();
        assertThat(blobStoreDAO.getClass())
            .isEqualTo(SingleSaveBlobStoreDAO.class);
    }
}