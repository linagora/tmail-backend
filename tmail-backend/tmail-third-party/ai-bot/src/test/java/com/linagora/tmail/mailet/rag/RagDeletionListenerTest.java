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
 *******************************************************************/

package com.linagora.tmail.mailet.rag;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.mail.Flags;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.extension.WireMockRagServerExtension;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryJavaUtils;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;
import com.linagora.tmail.mailet.rag.httpclient.OpenRagHttpClient;
import com.linagora.tmail.mailet.rag.httpclient.Partition;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RagDeletionListenerTest {

    @RegisterExtension
    static WireMockRagServerExtension wireMockRagServerExtension = new WireMockRagServerExtension();

    private EventBus eventBus;
    private JmapSettingsRepository jmapSettingsRepository;
    private JmapSettingsRepositoryJavaUtils jmapSettingsRepositoryUtils;
    private MailboxSessionMapperFactory mapperFactory;
    private MailboxMapper mailboxMapper;
    private MessageIdMapper messageIdMapper;

    @BeforeEach
    void setUp() {
        RetryBackoffConfiguration backoffConfiguration = RetryBackoffConfiguration.builder()
            .maxRetries(2)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0.5)
            .build();
        eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()),
            backoffConfiguration, new MemoryEventDeadLetters());

        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("openrag.url", wireMockRagServerExtension.getBaseUrl().toString());
        configuration.addProperty("openrag.token", "dummy-token");
        configuration.addProperty("openrag.ssl.trust.all.certs", "true");
        configuration.addProperty("openrag.partition.pattern", "{localPart}.twake.{domainName}");
        RagConfig ragConfig = RagConfig.from(configuration);
        OpenRagHttpClient openRagHttpClient = new OpenRagHttpClient(ragConfig);
        Partition.Factory partitionFactory = Partition.Factory.fromPattern(ragConfig.getPartitionPattern());

        jmapSettingsRepository = new MemoryJmapSettingsRepository();
        jmapSettingsRepositoryUtils = new JmapSettingsRepositoryJavaUtils(jmapSettingsRepository);

        MailboxSession session = mock(MailboxSession.class);
        SessionProvider sessionProvider = mock(SessionProvider.class);
        mapperFactory = mock(MailboxSessionMapperFactory.class);
        mailboxMapper = mock(MailboxMapper.class);
        messageIdMapper = mock(MessageIdMapper.class);

        when(sessionProvider.createSystemSession(Username.of("admin"))).thenReturn(session);
        when(mapperFactory.getMessageIdMapper(session)).thenReturn(messageIdMapper);
        when(mapperFactory.getMailboxMapper(session)).thenReturn(mailboxMapper);
        when(messageIdMapper.findMailboxesReactive(TestMessageId.of(1))).thenReturn(reactor.core.publisher.Flux.empty());

        RagDeletionListener ragDeletionListener = new RagDeletionListener(jmapSettingsRepository, sessionProvider, mapperFactory, partitionFactory, openRagHttpClient);
        eventBus.register(ragDeletionListener);
    }

    @Test
    void shouldDeleteRagDocumentOnMessageContentDeletionEvent() {
        wireMockRagServerExtension.setRagIndexerDeleteResponse(204, "");

        Username username = Username.of("bob@domain.tld");
        jmapSettingsRepositoryUtils.reset(username, ImmutableMap.of("ai.rag.enabled", "true"));

        MailboxEvents.MessageContentDeletionEvent messageContentDeletionEvent = new MailboxEvents.MessageContentDeletionEvent(
            Event.EventId.random(),
            username,
            InMemoryId.of(1),
            MailboxACL.EMPTY,
            TestMessageId.of(1),
            123L,
            Instant.now(),
            new Flags(),
            false,
            Optional.of("headerBlobId"),
            Optional.empty(),
            "bodyBlobId",
            Optional.empty());

        eventBus.dispatch(messageContentDeletionEvent, Set.of()).block();

        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(1, deleteRequestedFor(urlMatching("/indexer/partition/.*/file/.*"))));
    }

    @Test
    void shouldNotDeleteRagDocumentWhenAiRagSettingIsDisabled() {
        wireMockRagServerExtension.setRagIndexerDeleteResponse(204, "");

        Username username = Username.of("bob@domain.tld");
        jmapSettingsRepositoryUtils.reset(username, ImmutableMap.of("ai.rag.enabled", "false"));

        MailboxEvents.MessageContentDeletionEvent messageContentDeletionEvent = new MailboxEvents.MessageContentDeletionEvent(
            Event.EventId.random(),
            username,
            InMemoryId.of(1),
            MailboxACL.EMPTY,
            TestMessageId.of(1),
            123L,
            Instant.now(),
            new Flags(),
            false,
            Optional.of("headerBlobId"),
            Optional.empty(),
            "bodyBlobId",
            Optional.empty());

        eventBus.dispatch(messageContentDeletionEvent, Set.of()).block();

        Awaitility.await()
            .pollDelay(1, TimeUnit.SECONDS)
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(0, deleteRequestedFor(urlMatching("/indexer/partition/.*/file/.*"))));
    }

    @Test
    void shouldNotDeleteRagDocumentWhenMessageStillReferencedElsewhereByTheEventUser() {
        wireMockRagServerExtension.setRagIndexerDeleteResponse(204, "");

        Username username = Username.of("bob@domain.tld");
        jmapSettingsRepositoryUtils.reset(username, ImmutableMap.of("ai.rag.enabled", "true"));
        when(messageIdMapper.findMailboxesReactive(TestMessageId.of(1))).thenReturn(Flux.just(InMemoryId.of(2)));
        Mailbox mailbox = mock(Mailbox.class);
        when(mailboxMapper.findMailboxById(InMemoryId.of(2))).thenReturn(Mono.just(mailbox));
        when(mailbox.getUser()).thenReturn(username);

        MailboxEvents.MessageContentDeletionEvent messageContentDeletionEvent = new MailboxEvents.MessageContentDeletionEvent(
            Event.EventId.random(),
            username,
            InMemoryId.of(1),
            MailboxACL.EMPTY,
            TestMessageId.of(1),
            123L,
            Instant.now(),
            new Flags(),
            false,
            Optional.of("headerBlobId"),
            Optional.empty(),
            "bodyBlobId",
            Optional.empty());

        eventBus.dispatch(messageContentDeletionEvent, Set.of()).block();

        Awaitility.await()
            .pollDelay(1, TimeUnit.SECONDS)
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(0, deleteRequestedFor(urlMatching("/indexer/partition/.*/file/.*"))));
    }

    @Test
    void shouldDeleteRagDocumentWhenAnotherUserStillReferencesMessage() {
        wireMockRagServerExtension.setRagIndexerDeleteResponse(204, "");

        Username username = Username.of("bob@domain.tld");
        jmapSettingsRepositoryUtils.reset(username, ImmutableMap.of("ai.rag.enabled", "true"));
        when(messageIdMapper.findMailboxesReactive(TestMessageId.of(1))).thenReturn(Flux.just(InMemoryId.of(2)));
        Mailbox mailbox = mock(Mailbox.class);
        when(mailboxMapper.findMailboxById(InMemoryId.of(2))).thenReturn(Mono.just(mailbox));

        // Other users references the message should not prevent RAG cleanup for the event user `bob@domain.tld`
        when(mailbox.getUser()).thenReturn(Username.of("alice@domain.tld"));

        MailboxEvents.MessageContentDeletionEvent messageContentDeletionEvent = new MailboxEvents.MessageContentDeletionEvent(
            Event.EventId.random(),
            username,
            InMemoryId.of(1),
            MailboxACL.EMPTY,
            TestMessageId.of(1),
            123L,
            Instant.now(),
            new Flags(),
            false,
            Optional.of("headerBlobId"),
            Optional.empty(),
            "bodyBlobId",
            Optional.empty());

        eventBus.dispatch(messageContentDeletionEvent, Set.of()).block();

        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(1, deleteRequestedFor(urlMatching("/indexer/partition/.*/file/.*"))));
    }

    @Test
    void shouldNotDeleteRagDocumentWhenAiRagSettingIsNotSet() {
        wireMockRagServerExtension.setRagIndexerDeleteResponse(204, "");

        Username username = Username.of("bob@domain.tld");
        jmapSettingsRepositoryUtils.reset(username, ImmutableMap.of());

        MailboxEvents.MessageContentDeletionEvent messageContentDeletionEvent = new MailboxEvents.MessageContentDeletionEvent(
            Event.EventId.random(),
            username,
            InMemoryId.of(1),
            MailboxACL.EMPTY,
            TestMessageId.of(1),
            123L,
            Instant.now(),
            new Flags(),
            false,
            Optional.of("headerBlobId"),
            Optional.empty(),
            "bodyBlobId",
            Optional.empty());

        eventBus.dispatch(messageContentDeletionEvent, Set.of()).block();

        Awaitility.await()
            .pollDelay(1, TimeUnit.SECONDS)
            .atMost(2, TimeUnit.SECONDS)
            .untilAsserted(() -> verify(0, deleteRequestedFor(urlMatching("/indexer/partition/.*/file/.*"))));
    }
}
