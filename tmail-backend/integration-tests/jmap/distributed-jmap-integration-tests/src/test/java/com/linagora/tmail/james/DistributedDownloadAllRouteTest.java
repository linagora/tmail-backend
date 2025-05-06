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

package com.linagora.tmail.james;

import static com.linagora.tmail.james.DistributedLinagoraSecondaryBlobStoreTest.ACCEPT_RFC8621_VERSION_HEADER;
import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.restassured.RestAssured.given;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.jmap.MessageIdProbe;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.DownloadAllContract;
import com.linagora.tmail.james.jmap.ZipUtil;
import com.linagora.tmail.james.jmap.ZipUtil.ZipEntryData;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.module.CassandraAttachmentProbeModule;
import com.linagora.tmail.james.probe.CassandraAttachmentProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedDownloadAllRouteTest implements DownloadAllContract {
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
            .searchConfiguration(SearchConfiguration.scanning())
            .build())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new DelegationProbeModule())
            .overrideWith(new CassandraAttachmentProbeModule()))
        .build();

    @Override
    public MessageId randomMessageId() {
        return MESSAGE_ID_FACTORY.of(Uuids.timeBased());
    }

    @Test
    void downloadAllShouldWorkAfterAttachmentMetadataGotDeleted(GuiceJamesServer server) throws MailboxException {
        MailboxPath path = MailboxPath.inbox(BOB);
        server.getProbe(MailboxProbeImpl.class).createMailbox(path);

        MessageId messageId = server.getProbe(MailboxProbeImpl.class)
            .appendMessage(BOB.asString(), path, AppendCommand.from(
                ClassLoaderUtils.getSystemResourceAsSharedStream(DownloadAllContract.EMAIL_FILE_NAME())))
            .getMessageId();

        CassandraAttachmentProbe attachmentProbe = server.getProbe(CassandraAttachmentProbe.class);
        server.getProbe(MessageIdProbe.class)
            .retrieveAttachmentIds(messageId, BOB)
            .forEach(attachmentProbe::delete);

        InputStream response = given()
            .basePath("")
            .header(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER)
        .when()
            .get("/downloadAll/" + DownloadAllContract.accountId() + "/" + messageId.serialize())
        .then()
            .statusCode(SC_OK)
            .contentType("application/zip")
            .header("cache-control", "private, immutable, max-age=31536000")
            .extract()
            .body()
            .asInputStream();

        assertThat(ZipUtil.readZipData(response))
            .containsExactlyInAnyOrderElementsOf(DownloadAllContract.ZIP_ENTRIES());
    }

    @Test
    void downloadAllShouldStillNotIncludeInlinedAfterAttachmentMetadataGotDeleted(GuiceJamesServer server) throws MailboxException {
        MailboxPath path = MailboxPath.inbox(BOB);
        server.getProbe(MailboxProbeImpl.class).createMailbox(path);

        MessageId messageId = server.getProbe(MailboxProbeImpl.class)
            .appendMessage(BOB.asString(), path, AppendCommand.from(
                ClassLoaderUtils.getSystemResourceAsSharedStream("eml/mixed-inline-and-normal-attachments.eml")))
            .getMessageId();

        CassandraAttachmentProbe attachmentProbe = server.getProbe(CassandraAttachmentProbe.class);
        server.getProbe(MessageIdProbe.class)
            .retrieveAttachmentIds(messageId, BOB)
            .forEach(attachmentProbe::delete);

        InputStream response = given()
            .basePath("")
            .header(ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER)
        .when()
            .get("/downloadAll/" + DownloadAllContract.accountId() + "/" + messageId.serialize())
        .then()
            .statusCode(SC_OK)
            .contentType("application/zip")
            .header("cache-control", "private, immutable, max-age=31536000")
            .extract()
            .body()
            .asInputStream();

        assertThat(ZipUtil.readZipData(response))
            .containsExactlyInAnyOrderElementsOf(ImmutableList.of(
                new ZipEntryData("text1",
                    "-----BEGIN RSA PRIVATE KEY-----\n" +
                        "MIIEogIBAAKCAQEAx7PG0+E//EMpm7IgI5Q9TMDSFya/1hE+vvTJrk0iGFllPeHL\n" +
                        "A5/VlTM0YWgG6X50qiMfE3VLazf2c19iXrT0mq/21PZ1wFnogv4zxUNaih+Bng62\n" +
                        "F0SyruE/O/Njqxh/Ccq6K/e05TV4T643USxAeG0KppmYW9x8HA/GvV832apZuxkV\n" +
                        "i6NVkDBrfzaUCwu4zH+HwOv/pI87E7KccHYC++Biaj3\n")));
    }
}
