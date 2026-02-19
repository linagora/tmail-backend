/********************************************************************
 * As a subpart of Twake Mail, this file is edited by Linagora.    *
 * *
 * https://twake-mail.com/                                         *
 * https://linagora.com                                            *
 * *
 * This file is subject to The Affero Gnu Public License           *
 * version 3.                                                      *
 * *
 * https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 * *
 * This program is distributed in the hope that it will be         *
 * useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 * PURPOSE. See the GNU Affero General Public License for          *
 * more details.                                                   *
 ********************************************************************/
package com.linagora.tmail.mailet.rag;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.extension.WireMockRagServerExtension;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.james.common.probe.JmapSettingsProbeModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class RagListenerIntegrationTest {
    private static final String RAG_INDEXER_ENDPOINT = "/indexer/partition/.*/file/.*";

    @RegisterExtension
    static WireMockRagServerExtension wireMockRagServerExtension = new WireMockRagServerExtension();

    private static final byte[] CONTENT = (
        "Subject: Test Subject\r\n" +
            "From: sender@example.com\r\n" +
            "To: recipient@example.com\r\n" +
            "Cc: cc@example.com\r\n" +
            "Date: Tue, 10 Oct 2023 10:00:00 +0000\r\n" +
            "\r\n" +
            "Body of the email\r\n" +
            "\r\n" +
            "On Tue, 10 Oct 2023 at 09:00, previous@example.com wrote:\r\n" +
            "> Hello,\r\n" +
            "> This is the previous reply").getBytes(StandardCharsets.UTF_8);

    private static final String DOMAIN = "james.org";
    static final String PASSWORD = "secret";

    private static final String bob = "bob@" + DOMAIN;
    private static final String alice = "alice@" + DOMAIN;

    private MailboxPath pathAlice;
    private MailboxPath pathBob;

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new JmapSettingsProbeModule()))
        .extension(wireMockRagServerExtension)
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(alice, PASSWORD)
            .addUser(bob, PASSWORD);
        pathAlice = MailboxPath.inbox(Username.of(alice));
        pathBob = MailboxPath.inbox(Username.of(bob));
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(pathAlice);
        jamesServer.getProbe(MailboxProbeImpl.class).createMailbox(pathBob);

        // Enable RAG for both users
        JmapSettingsProbe jmapSettingsProbe = jamesServer.getProbe(JmapSettingsProbe.class);
        jmapSettingsProbe.reset(Username.of(alice), java.util.Map.of("ai.rag.enabled", "true"));
        jmapSettingsProbe.reset(Username.of(bob), java.util.Map.of("ai.rag.enabled", "true"));

        wireMockRagServerExtension.setRagIndexerPostResponse(200,
                String.format("{\"task_status_url\":\"http://localhost:%d/status/1234\"}", wireMockRagServerExtension.getPort()));
    }

    @Test
    void reactiveEventShouldProcessAddedEventAndExtractContent(GuiceJamesServer server) throws Exception {
        MessageManager.AppendResult message1 = server.getProbe(MailboxProbeImpl.class)
            .appendMessageAndGetAppendResult(bob, pathBob,
                MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)));

        verify(1, postRequestedFor(urlMatching(RAG_INDEXER_ENDPOINT)));

        verify(postRequestedFor(urlMatching(RAG_INDEXER_ENDPOINT))
            .withHeader("Authorization", equalTo("Bearer fake-token"))
            .withHeader("Content-Type", containing("multipart/form-data"))
            .withRequestBodyPart(aMultipart()
                .withName("metadata")
                .withBody(equalToJson("{"
                    + "\"email.subject\":\"Test Subject\","
                    + "\"datetime\":\"2023-10-10T10:00:00Z\","
                    + "\"parent_id\":\"\","
                    + "\"relationship_id\":\"1\","
                    + "\"doctype\":\"com.linagora.email\","
                    + "\"email.preview\":\"Body of the email On Tue, 10 Oct 2023 at 09:00, previous@example.com wrote: > Hello, > This is the previous reply\""
                    + "}"))
                .build())
            .withRequestBodyPart(aMultipart()
                .withName("file")
                .withFileName(message1.getId().getMessageId().serialize() + ".txt")
                .withHeader("Content-Type", containing("text/plain"))
                .withBody(containing("# Email Headers\n" +
                    "\n" +
                    "Subject: Test Subject\n" +
                    "From: sender@example.com\n" +
                    "To: recipient@example.com\n" +
                    "Cc: cc@example.com\n" +
                    "Date: Tue, 10 Oct 2023 10:00:00 +0000\n" +
                    "\n" +
                    "# Email Content\n" +
                    "\n" +
                    "Body of the email"))
                .build()));
    }

    @Test
    void listenerShouldAddInReplyToMetadataWhenEmailHaveInReplyToHeader(GuiceJamesServer server) throws Exception {
        MessageManager.AppendResult originalMessage = server.getProbe(MailboxProbeImpl.class)
            .appendMessageAndGetAppendResult(bob, pathBob,
                MessageManager.AppendCommand.from(
                    Message.Builder.of()
                        .setSubject("Sujet Test")
                        .setMessageId("Message-ID-1")
                        .setFrom("sender@example.com")
                        .setTo("recipient@example.com")
                        .setDate(Date.from(ZonedDateTime.of(
                            2023, 10, 10, 10, 0, 0, 0, ZoneOffset.UTC).toInstant()))
                        .setBody("Contenu mail 1", StandardCharsets.UTF_8)));

        MessageManager.AppendResult messageResponse = server.getProbe(MailboxProbeImpl.class)
            .appendMessageAndGetAppendResult(bob, pathBob,
                MessageManager.AppendCommand.from(
                    Message.Builder.of()
                        .setSubject("Re: Sujet Test")
                        .setMessageId("Message-ID-2")
                        .setFrom("sender@example.com")
                        .setTo("recipient@example.com")
                        .setDate(Date.from(ZonedDateTime.of(
                            2023, 10, 10, 10, 0, 0, 0, ZoneOffset.UTC).toInstant()))
                        .addField(new RawField("In-Reply-To", "Message-ID-1"))
                        .setBody("Contenu mail 1", StandardCharsets.UTF_8)));
        assertEquals(originalMessage.getThreadId(), messageResponse.getThreadId());
        verify(2, postRequestedFor(urlMatching(RAG_INDEXER_ENDPOINT)));

        verify(postRequestedFor(urlMatching(RAG_INDEXER_ENDPOINT))
            .withHeader("Authorization", equalTo("Bearer fake-token"))
            .withHeader("Content-Type", containing("multipart/form-data"))
            .withRequestBodyPart(aMultipart()
                .withName("metadata")
                .withBody(equalToJson("{"
                    + "\"email.subject\":\"Re: Sujet Test\","
                    + "\"datetime\":\"2023-10-10T10:00:00Z\","
                    + "\"parent_id\":\""+ originalMessage.getId().getMessageId().serialize() + "\","
                    + "\"relationship_id\":\"1\","
                    + "\"doctype\":\"com.linagora.email\","
                    + "\"email.preview\":\"Contenu mail 1\""
                    + "}"))
                .build())
            .withRequestBodyPart(aMultipart()
                .withName("file")
                .withFileName(messageResponse.getId().getMessageId().serialize() + ".txt")
                .withHeader("Content-Type", containing("text/plain"))
                .build()));
    }

    @Test
    void HttpClientShouldSendPutRequestWhenDocumentAlreadyIndexed(GuiceJamesServer server) throws Exception {
        wireMockRagServerExtension.setRagIndexerPostResponse(409,
                "{\"details\":\"Document already exists\"}");

        wireMockRagServerExtension.setRagIndexerPutResponse(200,
                String.format("{\"task_status_url\":\"http://localhost:%d/status/1234\"}", wireMockRagServerExtension.getPort()));

        MessageManager.AppendResult message1 = server.getProbe(MailboxProbeImpl.class)
            .appendMessageAndGetAppendResult(bob, pathBob,
                MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)));

        verify(1, postRequestedFor(urlMatching(RAG_INDEXER_ENDPOINT)));
        verify(1, putRequestedFor(urlMatching(RAG_INDEXER_ENDPOINT)));

        verify(putRequestedFor(urlMatching(RAG_INDEXER_ENDPOINT))
            .withHeader("Authorization", equalTo("Bearer fake-token"))
            .withHeader("Content-Type", containing("multipart/form-data"))
            .withRequestBodyPart(aMultipart()
                .withName("metadata")
                .withBody(equalToJson("{"
                    + "\"email.subject\":\"Test Subject\","
                    + "\"datetime\":\"2023-10-10T10:00:00Z\","
                    + "\"parent_id\":\"\","
                    + "\"relationship_id\":\"1\","
                    + "\"doctype\":\"com.linagora.email\","
                    + "\"email.preview\":\"Body of the email On Tue, 10 Oct 2023 at 09:00, previous@example.com wrote: > Hello, > This is the previous reply\""
                    + "}"))
                .build())
            .withRequestBodyPart(aMultipart()
                .withName("file")
                .withFileName(message1.getId().getMessageId().serialize() + ".txt")
                .withHeader("Content-Type", containing("text/plain"))
                .withBody(containing("# Email Headers\n" +
                    "\n" +
                    "Subject: Test Subject\n" +
                    "From: sender@example.com\n" +
                    "To: recipient@example.com\n" +
                    "Cc: cc@example.com\n" +
                    "Date: Tue, 10 Oct 2023 10:00:00 +0000\n" +
                    "\n" +
                    "# Email Content\n" +
                    "\n" +
                    "Body of the email"))
                .build()));
    }
}