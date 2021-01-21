package com.linagora.openpaas.james.app;

import static org.apache.james.user.ldap.DockerLdapSingleton.JAMES_USER;
import static org.apache.james.user.ldap.DockerLdapSingleton.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.commons.net.imap.IMAPClient;
import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.DockerElasticSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerContract;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.jmap.draft.JmapJamesServerContract;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

public class DistributedLdapServerTest {
    private static String JAMES_SERVER_HOST = "127.0.0.1";

    interface UserFromLdapShouldLogin {

        @Test
        default void userFromLdapShouldLoginViaImapProtocol(GuiceJamesServer server) throws IOException {
            IMAPClient imapClient = new IMAPClient();
            imapClient.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort());

            assertThat(imapClient.login(JAMES_USER.asString(), PASSWORD)).isTrue();
        }
    }

    interface ContractSuite extends JmapJamesServerContract, UserFromLdapShouldLogin, JamesServerContract {

        @Disabled("POP3 server is disabled")
        @Test
        default void connectPOP3ServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
            // POP3 server is disabled
        }

        @Disabled("LMTP server is disabled")
        @Test
        default void connectLMTPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) throws Exception {
            // LMTP server is disabled
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithAwsS3 implements ContractSuite {
        @RegisterExtension
        JamesServerExtension testExtension = baseJamesServerExtensionBuilder(BlobStoreConfiguration.s3()
            .disableCache()
            .passthrough())
            .extension(new AwsS3BlobStoreExtension())
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
            .build();
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WithCassandra implements ContractSuite {
        @RegisterExtension
        JamesServerExtension testExtension = baseJamesServerExtensionBuilder(BlobStoreConfiguration.builder()
            .cassandra()
            .disableCache()
            .passthrough())
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
            .build();
    }

    JamesServerBuilder baseJamesServerExtensionBuilder(BlobStoreConfiguration blobStoreConfiguration) {
        return new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
            CassandraRabbitMQJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .blobStore(blobStoreConfiguration)
                .searchConfiguration(SearchConfiguration.elasticSearch())
                .build())
            .extension(new DockerElasticSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .extension(new LdapTestExtension())
            .server(configuration -> DistributedLdapServer.createServer(configuration)
                .overrideWith(new TestJMAPServerModule()));
    }
}