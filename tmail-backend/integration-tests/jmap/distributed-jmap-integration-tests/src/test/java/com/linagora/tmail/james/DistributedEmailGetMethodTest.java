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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.james;

import static org.apache.james.MailsShouldBeWellReceived.CALMLY_AWAIT;
import static org.apache.james.MailsShouldBeWellReceived.DOMAIN;
import static org.apache.james.MailsShouldBeWellReceived.JAMES_USER;
import static org.apache.james.MailsShouldBeWellReceived.PASSWORD;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.rfc8621.contract.EmailGetMethodContract;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.opensearch.IndexBody;
import org.apache.james.mailbox.opensearch.OpenSearchMailboxConfiguration;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.probe.MessageFastViewProjectionProbe;
import com.linagora.tmail.james.common.probe.MessageIdManagerProbe;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedEmailGetMethodTest implements EmailGetMethodContract {
    public static final CassandraMessageId.Factory MESSAGE_ID_FACTORY = new CassandraMessageId.Factory();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .searchConfiguration(SearchConfiguration.openSearch())
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new DelegationProbeModule())
            .overrideWith(binder -> binder.bind(OpenSearchMailboxConfiguration.class)
                .toInstance(OpenSearchMailboxConfiguration.builder()
                    .indexBody(IndexBody.NO)
                    .build()))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(MessageFastViewProjectionProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(MessageIdManagerProbe.class)))
        .build();

    @Override
    public MessageId randomMessageId() {
        return MESSAGE_ID_FACTORY.of(Uuids.timeBased());
    }

    @Tag(BasicFeature.TAG)
    @Test
    void jmapPreviewShouldBeWellPreComputed(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);
        MessageId messageId = mailboxProbe.appendMessage(JAMES_USER, MailboxPath.inbox(Username.of(JAMES_USER)),
            MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("Subject")
                .setBody("This is content of the message", StandardCharsets.UTF_8)
                .build()))
            .getMessageId();

        CALMLY_AWAIT
            .atMost(Duration.ofSeconds(10))
            .until(() -> server.getProbe(MessageFastViewProjectionProbe.class)
                .retrieve(messageId)
                .isPresent());
    }

    @Test
    void jmapPreviewShouldBeWellRemovedWhenDeleteMessage(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);
        MessageId messageId = mailboxProbe.appendMessage(JAMES_USER, MailboxPath.inbox(Username.of(JAMES_USER)),
                MessageManager.AppendCommand.from(Message.Builder.of()
                    .setSubject("Subject")
                    .setBody("This is content of the message", StandardCharsets.UTF_8)
                    .build()))
            .getMessageId();
        CALMLY_AWAIT
            .atMost(Duration.ofSeconds(10))
            .until(() -> server.getProbe(MessageFastViewProjectionProbe.class)
                .retrieve(messageId)
                .isPresent());

        server.getProbe(MessageIdManagerProbe.class)
            .delete(Set.of(messageId), Username.of(JAMES_USER))
            .block();

        CALMLY_AWAIT
            .atMost(Duration.ofSeconds(10))
            .until(() -> server.getProbe(MessageFastViewProjectionProbe.class)
                .retrieve(messageId)
                .isEmpty());
    }
}
