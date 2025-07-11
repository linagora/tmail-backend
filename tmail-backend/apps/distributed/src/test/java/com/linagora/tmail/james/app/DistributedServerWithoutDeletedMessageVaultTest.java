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
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Domain;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.vault.VaultConfiguration;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.io.Resources;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import reactor.core.publisher.Flux;

public class DistributedServerWithoutDeletedMessageVaultTest {
    private static final String JAMES_SERVER_HOST = "127.0.0.1";
    private static final String DOMAIN = "tmail.org";
    private static final String BOB = "bob@" + DOMAIN;
    private static final String SENDER = "sender@" + DOMAIN;
    private static final String PASSWORD = "secret";
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static JamesServerExtension testExtension =  new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.openSearch())
            .eventBusKeysChoice(EventBusKeysChoice.RABBITMQ)
            .vaultConfiguration(VaultConfiguration.DEFAULT)
            .build())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(EncryptedDistributedServerTest.BlobStoreDaoClassProbe.class)))
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Test
    void distributedJamesServerShouldStartWithoutDeletedMessageVault(GuiceJamesServer server) {
        assertThat(server.isStarted()).isTrue();
    }

    @Test
    void deletedMessageVaultBucketShouldNotBeCreatedWhenDeleteMessage(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB, PASSWORD)
            .addUser(SENDER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", BOB, DefaultMailboxes.INBOX);

        Port smtpPort = Port.of(server.getProbe(SmtpGuiceProbe.class).getSmtpPort().getValue());
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            sender.connect(JAMES_SERVER_HOST, smtpPort).authenticate(SENDER, PASSWORD);
            sendUniqueMessage(sender, message);
        }

        CALMLY_AWAIT.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        try (TestIMAPClient reader = new TestIMAPClient()) {
            reader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(BOB, PASSWORD)
                .select(TestIMAPClient.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1);

            reader.setFlagsForAllMessagesInMailbox("\\Deleted");
            reader.expunge();
            reader.awaitMessageCount(CALMLY_AWAIT, 0);
        }

        BlobStoreDAO blobStoreDAO = server.getProbe(EncryptedDistributedServerTest.BlobStoreDaoClassProbe.class).getBlobStoreDAO();
        assertThat(Flux.from(blobStoreDAO.listBuckets()).collectList().block().stream().map(BucketName::asString).filter(bucket -> bucket.contains("deleted-messages")))
            .hasSize(0);
    }

    private void sendUniqueMessage(SMTPMessageSender sender, String message) throws IOException {
        String uniqueMessage = message.replace("banana", "UUID " + UUID.randomUUID());
        sender.sendMessageWithHeaders(SENDER, BOB, uniqueMessage);
    }
}
