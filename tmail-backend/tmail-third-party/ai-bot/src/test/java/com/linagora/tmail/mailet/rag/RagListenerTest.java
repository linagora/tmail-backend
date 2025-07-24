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

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import static org.mockito.Mockito.spy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import com.github.tomakehurst.wiremock.WireMockServer;
import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.SystemMailboxesProviderImpl;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RagListenerTest {

    private static final Username BOB = Username.of("bob@test.com");
    private static final Username ALICE = Username.of("alice@test.com");
    private static final Username USER_WITH_NO_DOMAIN = Username.of("user");
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(BOB);
    private static final MailboxPath USER_WITH_NO_DOMAIN_INBOX_PATH = MailboxPath.inbox(USER_WITH_NO_DOMAIN );
    private static final MailboxPath ALICE_INBOX_PATH = MailboxPath.inbox(ALICE);
    private static final MailboxPath BOB_SPAM_PATH = MailboxPath.forUser(BOB, "Spam");
    private static final MailboxPath BOB_MAILBOX_PATH = MailboxPath.forUser(BOB, "mailbox");
    private static final MailboxPath BOB_TRASH_PATH = MailboxPath.forUser(BOB, "Trash");

    private static final byte[] CONTENT = (
        "Subject: Test Subject\r\n" +
            "From: sender@example.com\r\n" +
            "To: recipient@example.com\r\n" +
            "Cc: cc@example.com\r\n" +
            "Date: Tue, 10 Oct 2023 10:00:00 +0000\r\n" +
            "\r\n" +
            "Body of the email").getBytes(StandardCharsets.UTF_8);
    StoreMailboxManager mailboxManager;
    RagListener ragListener;
    MessageIdManager messageIdManager;
    MessageManager aliceInboxMessageManager;
    MessageManager bobMailBoxMessageManager;
    MessageManager userWithNoDomainMailBoxMessageManager;
    MessageManager bobInboxMessageManager;
    MessageManager spamMessageManager;
    MessageManager trashMessageManager;
    MailboxId trashMailboxId;
    MailboxId spamMailboxId;
    SystemMailboxesProviderImpl systemMailboxesProvider;
    MailboxSession bobMailboxSession;
    MailboxSession aliceMailboxSession;
    MailboxSession userWithNoDomainMailboxSession;
    UpdatableTickingClock clock;
    MailboxSessionMapperFactory mapperFactory;
    MailboxId bobInboxId;
    MailboxId userWithNoDomainInboxId;
    MailboxId bobMailBoxId;
    MailboxId aliceInboxId;
    MemoryEventDeadLetters eventDeadLetters;
    HierarchicalConfiguration<ImmutableNode> config;
    WireMockServer wireMockServer;
    RagConfig ragConfig;

    @BeforeEach
    void setup() throws Exception {
        wireMockServer = new WireMockServer();
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
        stubFor(post(urlPathMatching("/indexer/partition/.*/file/.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"task_status_url\":\"http://localhost:8080/status/1234\"}")));

        stubFor(get(urlEqualTo("/status/1234"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"task_state\":\"COMPLETED\"}")));

        clock = new UpdatableTickingClock(Instant.now());
        mapperFactory = new InMemoryMailboxSessionMapperFactory(clock);
        RetryBackoffConfiguration backoffConfiguration = RetryBackoffConfiguration.builder()
            .maxRetries(2)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0.5)
            .build();
        eventDeadLetters = new MemoryEventDeadLetters();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), backoffConfiguration, eventDeadLetters))
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        mailboxManager = resources.getMailboxManager();;
        messageIdManager = spy(resources.getMessageIdManager());
        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(BOB, "12345");
        authenticator.addUser(ALICE, "12345");

        bobMailboxSession = MailboxSessionUtil.create(BOB);
        aliceMailboxSession = MailboxSessionUtil.create(ALICE);
        userWithNoDomainMailboxSession = MailboxSessionUtil.create(USER_WITH_NO_DOMAIN);
        bobInboxId = mailboxManager.createMailbox(BOB_INBOX_PATH, bobMailboxSession).get();
        userWithNoDomainInboxId = mailboxManager.createMailbox(USER_WITH_NO_DOMAIN_INBOX_PATH, userWithNoDomainMailboxSession).get();

        bobMailBoxId = mailboxManager.createMailbox(BOB_MAILBOX_PATH, bobMailboxSession).get();
        aliceInboxId = mailboxManager.createMailbox(ALICE_INBOX_PATH, aliceMailboxSession).get();
        spamMailboxId = mailboxManager.createMailbox(BOB_SPAM_PATH, bobMailboxSession).get();
        trashMailboxId = mailboxManager.createMailbox(BOB_TRASH_PATH, bobMailboxSession).get();
        spamMessageManager = mailboxManager.getMailbox(spamMailboxId, bobMailboxSession);
        trashMessageManager = mailboxManager.getMailbox(trashMailboxId, bobMailboxSession);
        bobInboxMessageManager = mailboxManager.getMailbox(bobInboxId, bobMailboxSession);
        userWithNoDomainMailBoxMessageManager = mailboxManager.getMailbox(userWithNoDomainInboxId, userWithNoDomainMailboxSession);
        bobMailBoxMessageManager = mailboxManager.getMailbox(bobMailBoxId, bobMailboxSession);
        aliceInboxMessageManager = mailboxManager.getMailbox(aliceInboxId, aliceMailboxSession);
        systemMailboxesProvider = new SystemMailboxesProviderImpl(mailboxManager);
        Configurations configurations = new Configurations();
        config = configurations.xml("listeners.xml");
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("openrag.url", "http://localhost:8080");
        configuration.addProperty("openrag.token", "dummy-token");
        configuration.addProperty("openrag.ssl.trust.all.certs", "true");
        configuration.addProperty("openrag.partition.pattern", "{localPart}.twake.{domainName}");
        ragConfig = RagConfig.from(configuration);
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, config, ragConfig);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void reactiveEventShouldProcessAddedEventAndExtractContent() throws Exception {
        mailboxManager.getEventBus().register(ragListener);
        MessageManager.AppendResult appendResult = bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        verify(2, anyRequestedFor(anyUrl()));

        verify(postRequestedFor(urlMatching("/indexer/partition/.*/file/.*"))
            .withHeader("Authorization", equalTo("Bearer dummy-token"))
            .withHeader("Content-Type", containing("multipart/form-data")));

        verify(getRequestedFor(urlEqualTo("/status/1234"))
            .withHeader("Authorization", equalTo("Bearer dummy-token"))
            .withHeader("Accept", equalTo("application/json")));
    }

    @Test
    void reactiveEventShouldLogNothingWhenEventIsNotAppended() throws Exception {

        MessageManager.AppendResult appendResult = bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);
        mailboxManager.getEventBus().register(ragListener);

        mailboxManager.moveMessagesReactive(
            MessageRange.from(appendResult.getId().getUid()),
            bobInboxId,
            bobMailBoxId,
            bobMailboxSession
        ).collectList().block();

        mailboxManager.copyMessages(MessageRange.from(appendResult.getId().getUid()), bobInboxId,bobMailBoxId,bobMailboxSession);

        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void reactiveEventShouldLogNothingWhenEventIsSpam() throws Exception {

        mailboxManager.getEventBus().register(ragListener);
        MessageManager.AppendResult appendResult = spamMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void reactiveEventShouldLogNothingWhenEventIsTrash() throws Exception {
        mailboxManager.getEventBus().register(ragListener);

        MessageManager.AppendResult appendResult = trashMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void reactiveEventShouldSkipListenerWhenUserNotInWhiteList() throws Exception {
        mailboxManager.getEventBus().register(ragListener);

        MessageManager.AppendResult appendResult = aliceInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            aliceMailboxSession);

        verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void reactiveEventShouldAllowAllUsersWhenWhiteListIsEmpty() throws Exception {
        config.setProperty("listener.configuration.users", "");
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, config, ragConfig);
        mailboxManager.getEventBus().register(ragListener);

        MessageManager.AppendResult appendResult = aliceInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            aliceMailboxSession);
        MessageManager.AppendResult appendResult2 = bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        verify(4, anyRequestedFor(anyUrl()));

        verify(postRequestedFor(urlMatching("/indexer/partition/.*/file/tmail_1"))
            .withHeader("Authorization", equalTo("Bearer dummy-token"))
            .withHeader("Content-Type", containing("multipart/form-data")));

        verify(postRequestedFor(urlMatching("/indexer/partition/.*/file/tmail_2"))
            .withHeader("Authorization", equalTo("Bearer dummy-token"))
            .withHeader("Content-Type", containing("multipart/form-data")));

        verify(getRequestedFor(urlEqualTo("/status/1234"))
            .withHeader("Authorization", equalTo("Bearer dummy-token"))
            .withHeader("Accept", equalTo("application/json")));
    }

    @Test
    void reactiveEventShouldProcessEmailWithAttachment() throws Exception {
        mailboxManager.getEventBus().register(ragListener);

        byte[] emailWithAttachment = (
            "Subject: Test Email with Attachment\r\n" +
                "From: sender@example.com\r\n" +
                "To: recipient@example.com\r\n" +
                "Date: Tue, 10 Oct 2023 10:00:00 +0000\r\n" +
                "Content-Type: multipart/mixed; boundary=\"boundary\"\r\n" +
                "\r\n" +
                "--boundary\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "\r\n" +
                "This is the body of the email.\r\n" +
                "--boundary\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: attachment; filename=\"test.txt\"\r\n" +
                "\r\n" +
                "This is the content of the attachment.\r\n" +
                "--boundary--").getBytes(StandardCharsets.UTF_8);

        MessageManager.AppendResult appendResult = bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(emailWithAttachment)),
            bobMailboxSession);

        verify(2, anyRequestedFor(anyUrl()));

        verify(postRequestedFor(urlMatching("/indexer/partition/.*/file/tmail_1"))
            .withHeader("Authorization", equalTo("Bearer dummy-token"))
            .withHeader("Content-Type", containing("multipart/form-data")));

        verify(getRequestedFor(urlEqualTo("/status/1234"))
            .withHeader("Authorization", equalTo("Bearer dummy-token"))
            .withHeader("Accept", equalTo("application/json")));
    }

    @Test
    void reactiveEventShouldNotIndexMessageWhenDomainNameIsMissing() throws Exception {
        config.setProperty("listener.configuration.users", "user");
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, config, ragConfig);
        mailboxManager.getEventBus().register(ragListener);

        userWithNoDomainMailBoxMessageManager.appendMessage(
                MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
                userWithNoDomainMailboxSession);

        verify(0, anyRequestedFor(anyUrl()));
    }
}