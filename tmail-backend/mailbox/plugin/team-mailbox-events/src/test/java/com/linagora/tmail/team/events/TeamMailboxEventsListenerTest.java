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

package com.linagora.tmail.team.events;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.apache.james.events.EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;
import com.rabbitmq.client.BuiltinExchangeType;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import scala.jdk.javaapi.OptionConverters;

class TeamMailboxEventsListenerTest {
    record Published(String routingKey, String messageId, String contentType, Integer deliveryMode, String body) {
    }

    private static final Domain DOMAIN = Domain.of("linagora.com");
    private static final Username BOB = Username.of("bob@linagora.com");
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final boolean IS_DELIVERY = true;
    private static final boolean IS_NOT_DELIVERY = false;
    private static final String MESSAGE = """
        From: Alice <alice@external.com>\r
        To: marketing@linagora.com\r
        Cc: Bob <bob@linagora.com>\r
        Bcc: [REDACTED-EMAIL-0b4bc0f6]\r
        Subject: Hello team\r
        Date: Tue, 06 Oct 2026 10:00:00 +0000\r
        \r
        body\r
        """;
    private static final String SENTINEL = """
        Subject: sentinel\r
        \r
        body\r
        """;

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private InMemoryMailboxManager mailboxManager;
    private MessageIdManager messageIdManager;
    private MemoryEventDeadLetters eventDeadLetters;
    private TeamMailbox marketing;
    private MailboxSession teamSession;
    private String exchange;
    private TeamMailboxEventsListener testee;
    private ConcurrentLinkedQueue<Published> published;
    private Disposable consumer;

    @BeforeEach
    void setUp() throws Exception {
        eventDeadLetters = new MemoryEventDeadLetters();
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), RETRY_BACKOFF_CONFIGURATION, eventDeadLetters))
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
        mailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();

        TeamMailboxRepositoryImpl teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager,
            new StoreSubscriptionManager(mailboxManager.getMapperFactory(), mailboxManager.getMapperFactory(), mailboxManager.getEventBus()),
            mailboxManager.getMapperFactory(),
            Set.of());
        marketing = OptionConverters.toJava(TeamMailbox.fromJava(DOMAIN, "marketing")).orElseThrow();
        Mono.from(teamMailboxRepository.createTeamMailbox(marketing)).block();
        teamSession = mailboxManager.createSystemSession(marketing.owner());

        exchange = "tmail-" + UUID.randomUUID();
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("exchange", exchange);
        testee = new TeamMailboxEventsListener(mailboxManager, messageIdManager, Clock.fixed(NOW, ZoneOffset.UTC),
            rabbitMQExtension.getRabbitMQ().getConfiguration(), configuration);
        resources.getEventBus().register(testee);

        String queue = "team-mailbox-events-" + UUID.randomUUID();
        rabbitMQExtension.getSender().declareExchange(topicExchange(exchange)).block();
        rabbitMQExtension.getSender().declareQueue(QueueSpecification.queue(queue).autoDelete(true)).block();
        rabbitMQExtension.getSender().bind(BindingSpecification.binding(exchange, "team-mailbox.message.#", queue)).block();
        published = new ConcurrentLinkedQueue<>();
        consumer = rabbitMQExtension.getReceiverProvider().createReceiver()
            .consumeAutoAck(queue)
            .subscribe(delivery -> published.add(new Published(
                delivery.getEnvelope().getRoutingKey(),
                delivery.getProperties().getMessageId(),
                delivery.getProperties().getContentType(),
                delivery.getProperties().getDeliveryMode(),
                new String(delivery.getBody(), UTF_8))));
    }

    @AfterEach
    void tearDown() {
        consumer.dispose();
        testee.close();
    }

    @Test
    void deliveryIntoTeamInboxShouldPublishReceivedEvent() throws Exception {
        MessageId messageId = append(marketing.inboxPath(), MESSAGE, IS_DELIVERY);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published).hasSize(1));
        Published event = published.peek();
        assertThat(event).isEqualTo(new Published("team-mailbox.message.received", messageId.serialize() + ":received",
            "application/json", 2, event.body()));
        assertThatJson(event.body()).isEqualTo("""
            {
              "teamMailbox": "marketing@linagora.com",
              "domain": "linagora.com",
              "direction": "received",
              "mailboxPath": "marketing.INBOX",
              "mailboxId": "%s",
              "messageId": "%s",
              "subject": "Hello team",
              "from": [{"name": "Alice", "email": "alice@external.com"}],
              "to": [{"name": null, "email": "marketing@linagora.com"}],
              "cc": [{"name": "Bob", "email": "bob@linagora.com"}],
              "bcc": [{"name": null, "email": "[REDACTED-EMAIL-0b4bc0f6]"}],
              "date": "2026-10-06T10:00:00Z",
              "timestamp": "2026-10-06T12:00:00Z"
            }""".formatted(mailboxId(marketing.inboxPath()).serialize(), messageId.serialize()));
    }

    @Test
    void missingHeadersShouldBeNullInReceivedEvent() throws Exception {
        MessageId messageId = append(marketing.inboxPath(), "\r\nbody\r\n", IS_DELIVERY);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published).hasSize(1));
        assertThatJson(published.peek().body()).isEqualTo("""
            {
              "teamMailbox": "marketing@linagora.com",
              "domain": "linagora.com",
              "direction": "received",
              "mailboxPath": "marketing.INBOX",
              "mailboxId": "%s",
              "messageId": "%s",
              "subject": null,
              "from": null,
              "to": null,
              "cc": null,
              "bcc": null,
              "date": null,
              "timestamp": "2026-10-06T12:00:00Z"
            }""".formatted(mailboxId(marketing.inboxPath()).serialize(), messageId.serialize()));
    }

    @Test
    void amqpUriShouldTakePrecedenceOverTMailRabbitMQConfiguration() throws Exception {
        RabbitMQConfiguration unreachableTMailRabbitMQ = RabbitMQConfiguration.builder()
            .amqpUri(new URI("amqp://localhost:1"))
            .managementUri(rabbitMQExtension.getRabbitMQ().managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .build();
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("amqpUri", rabbitMQExtension.getRabbitMQ().amqpUri().toString());
        configuration.addProperty("exchange", exchange);
        RabbitMQTeamMailboxEventPublisher publisher = RabbitMQTeamMailboxEventPublisher.create(
            TeamMailboxEventsConfiguration.from(configuration), unreachableTMailRabbitMQ);
        MessageId messageId = append(marketing.mailboxPath(DefaultMailboxes.DRAFTS), MESSAGE, IS_NOT_DELIVERY);

        try {
            publisher.publish(Flux.just(new TeamMailboxEvent(marketing, TeamMailboxEvent.Direction.SENT, marketing.sentPath(),
                mailboxId(marketing.sentPath()), messageId, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), NOW))).block();
        } finally {
            publisher.close();
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
            .extracting(Published::messageId)
            .containsExactly(messageId.serialize() + ":sent"));
    }

    @Test
    void deliveryIntoTeamSubfolderShouldPublishReceivedEvent() throws Exception {
        MailboxPath subfolder = marketing.mailboxPath("Projects");
        mailboxManager.createMailbox(subfolder, teamSession);

        MessageId messageId = append(subfolder, MESSAGE, IS_DELIVERY);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
            .extracting(Published::messageId)
            .containsExactly(messageId.serialize() + ":received"));
        assertThatJson(published.peek().body()).node("mailboxPath").isEqualTo("marketing.Projects");
    }

    @Test
    void deliveryIntoPersonalMailboxShouldBeIgnored() throws Exception {
        MailboxSession bobSession = mailboxManager.createSystemSession(BOB);
        MailboxPath bobInbox = MailboxPath.inbox(BOB);
        mailboxManager.createMailbox(bobInbox, bobSession);

        mailboxManager.getMailbox(bobInbox, bobSession)
            .appendMessage(MessageManager.AppendCommand.builder().isDelivery(IS_DELIVERY).build(MESSAGE), bobSession);

        assertOnlySentinelIsPublished();
    }

    @Test
    void appendIntoTeamSentShouldPublishSentEvent() throws Exception {
        MessageId messageId = append(marketing.sentPath(), MESSAGE, IS_NOT_DELIVERY);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
            .extracting(Published::routingKey, Published::messageId)
            .containsExactly(tuple("team-mailbox.message.sent", messageId.serialize() + ":sent")));
        assertThatJson(published.peek().body()).node("mailboxPath").isEqualTo("marketing.Sent");
    }

    @Test
    void moveIntoTeamSentShouldPublishSentEvent() throws Exception {
        MessageId messageId = append(marketing.mailboxPath(DefaultMailboxes.DRAFTS), MESSAGE, IS_NOT_DELIVERY);

        messageIdManager.setInMailboxes(messageId, List.of(mailboxId(marketing.sentPath())), teamSession);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
            .extracting(Published::messageId)
            .containsExactly(messageId.serialize() + ":sent"));
    }

    @Test
    void copyIntoTeamSentShouldPublishSentEvent() throws Exception {
        MailboxId drafts = mailboxId(marketing.mailboxPath(DefaultMailboxes.DRAFTS));
        MessageId messageId = append(marketing.mailboxPath(DefaultMailboxes.DRAFTS), MESSAGE, IS_NOT_DELIVERY);

        messageIdManager.setInMailboxes(messageId, List.of(drafts, mailboxId(marketing.sentPath())), teamSession);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
            .extracting(Published::messageId)
            .containsExactly(messageId.serialize() + ":sent"));
    }

    @Test
    void moveBetweenTeamFoldersOtherThanSentShouldBeIgnored() throws Exception {
        MessageId messageId = append(marketing.mailboxPath(DefaultMailboxes.DRAFTS), MESSAGE, IS_NOT_DELIVERY);

        messageIdManager.setInMailboxes(messageId, List.of(mailboxId(marketing.mailboxPath(DefaultMailboxes.TRASH))), teamSession);

        assertOnlySentinelIsPublished();
    }

    @Test
    void appendIntoTeamFolderOtherThanSentShouldBeIgnored() throws Exception {
        append(marketing.mailboxPath(DefaultMailboxes.DRAFTS), MESSAGE, IS_NOT_DELIVERY);

        assertOnlySentinelIsPublished();
    }

    @Test
    void failingPublicationShouldDeadLetterTheEvent() throws Exception {
        rabbitMQExtension.getSender().deleteExchange(ExchangeSpecification.exchange(exchange), false).block();
        rabbitMQExtension.getSender().declareExchange(ExchangeSpecification.exchange(exchange).type(BuiltinExchangeType.DIRECT.getType())).block();

        append(marketing.inboxPath(), MESSAGE, IS_DELIVERY);

        await().atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> assertThat(eventDeadLetters.groupsWithFailedEvents().collectList().block())
                .containsExactly(testee.getDefaultGroup()));
    }

    private void assertOnlySentinelIsPublished() throws Exception {
        MessageId sentinel = append(marketing.inboxPath(), SENTINEL, IS_DELIVERY);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
            .extracting(Published::messageId)
            .containsExactly(sentinel.serialize() + ":received"));
    }

    private ExchangeSpecification topicExchange(String name) {
        return ExchangeSpecification.exchange(name)
            .type(BuiltinExchangeType.TOPIC.getType())
            .durable(true);
    }

    private MailboxId mailboxId(MailboxPath path) throws Exception {
        return mailboxManager.getMailbox(path, teamSession).getId();
    }

    private MessageId append(MailboxPath path, String message, boolean isDelivery) throws Exception {
        return mailboxManager.getMailbox(path, teamSession)
            .appendMessage(MessageManager.AppendCommand.builder()
                .isDelivery(isDelivery)
                .build(message), teamSession)
            .getId()
            .getMessageId();
    }
}
