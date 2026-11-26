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


package com.linagora.tmail.activity;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.apache.james.events.EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;
import com.rabbitmq.client.BuiltinExchangeType;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import scala.jdk.javaapi.OptionConverters;

class AccountActivityListenerTest {
    record Published(String routingKey, String messageId, String contentType, Integer deliveryMode, String body) {
    }

    private static final Domain DOMAIN = Domain.of("linagora.com");
    private static final Username BOB = Username.of("bob@linagora.com");
    private static final Username ALICE = Username.of("alice@linagora.com");
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final boolean IS_DELIVERY = true;
    private static final boolean IS_NOT_DELIVERY = false;
    private static final String MESSAGE = """
        Subject: Hello\r
        \r
        body\r
        """;

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private InMemoryIntegrationResources resources;
    private InMemoryMailboxManager mailboxManager;
    private MemoryEventDeadLetters eventDeadLetters;
    private MailboxSession bobSession;
    private TeamMailbox marketing;
    private AccountActivityListener testee;
    private ConcurrentLinkedQueue<Published> published;
    private Disposable consumer;

    @BeforeEach
    void setUp() throws Exception {
        eventDeadLetters = new MemoryEventDeadLetters();
        resources = InMemoryIntegrationResources.builder()
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

        bobSession = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), bobSession);
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS), bobSession);

        TeamMailboxRepositoryImpl teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager,
            new StoreSubscriptionManager(mailboxManager.getMapperFactory(), mailboxManager.getMapperFactory(), mailboxManager.getEventBus()),
            mailboxManager.getMapperFactory(),
            Set.of());
        marketing = OptionConverters.toJava(TeamMailbox.fromJava(DOMAIN, "marketing")).orElseThrow();
        Mono.from(teamMailboxRepository.createTeamMailbox(marketing)).block();

        String exchange = "activity-" + UUID.randomUUID();
        testee = new AccountActivityListener(rabbitMQExtension.getSender(), CLOCK, new AccountActivityListener.Configuration(exchange));
        resources.getEventBus().register(testee);

        String queue = "activity-" + UUID.randomUUID();
        rabbitMQExtension.getSender().declareExchange(ExchangeSpecification.exchange(exchange)
            .type(BuiltinExchangeType.TOPIC.getType())
            .durable(true)).block();
        rabbitMQExtension.getSender().declareQueue(QueueSpecification.queue(queue).autoDelete(true)).block();
        rabbitMQExtension.getSender().bind(BindingSpecification.binding(exchange, "#", queue)).block();
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
    }

    @Test
    void deliveryIntoInboxShouldPublishEmailReceived() throws Exception {
        MessageId messageId = append(bobSession, MailboxPath.inbox(BOB), IS_DELIVERY);
        MailboxId inboxId = mailboxId(bobSession, MailboxPath.inbox(BOB));
        String id = AccountActivityListener.id(inboxId, messageId, AccountActivityListener.ActivityType.EMAIL_RECEIVED);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published).hasSize(1));
        Published event = published.peek();
        assertThat(event).isEqualTo(new Published("mail.emailReceived", id, "application/json", 2, event.body()));
        assertThatJson(event.body()).isEqualTo("""
            {
              "specversion": "1.0",
              "id": "%1$s",
              "source": "twake://mail",
              "type": "com.twake.mail.emailReceived.v1",
              "time": "2026-10-06T12:00:00Z",
              "subject": "mailboxes/%2$s/%3$s",
              "twakeactor": "bob@linagora.com",
              "twakeresourceowner": "bob@linagora.com",
              "twakedomain": "linagora.com",
              "data": {
                "mailboxPath": "INBOX",
                "mailboxId": "%2$s",
                "messageId": "%3$s"
              }
            }""".formatted(id, inboxId.serialize(), messageId.serialize()));
    }

    @Test
    void actorShouldBeTheUserAddingTheMessageAndOwnerTheMailboxOwner() throws Exception {
        mailboxManager.applyRightsCommand(MailboxPath.inbox(BOB), MailboxACL.command()
            .forUser(ALICE)
            .rights(MailboxACL.FULL_RIGHTS)
            .asAddition(), bobSession);
        MailboxSession aliceSession = mailboxManager.createSystemSession(ALICE);

        append(aliceSession, MailboxPath.inbox(BOB), IS_DELIVERY);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published).hasSize(1));
        assertThatJson(published.peek().body()).isObject()
            .containsEntry("twakeactor", "alice@linagora.com")
            .containsEntry("twakeresourceowner", "bob@linagora.com")
            .containsEntry("twakedomain", "linagora.com");
    }

    @Test
    void idShouldBeANameBasedUuidOfTheMessageInItsMailbox() throws Exception {
        MessageId messageId = append(bobSession, MailboxPath.inbox(BOB), IS_DELIVERY);
        MailboxId inboxId = mailboxId(bobSession, MailboxPath.inbox(BOB));
        MailboxId draftsId = mailboxId(bobSession, MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS));

        String id = AccountActivityListener.id(inboxId, messageId, AccountActivityListener.ActivityType.EMAIL_RECEIVED);

        assertThat(UUID.fromString(id).version()).isEqualTo(3);
        assertThat(id)
            .isEqualTo(AccountActivityListener.id(inboxId, messageId, AccountActivityListener.ActivityType.EMAIL_RECEIVED))
            .isNotEqualTo(AccountActivityListener.id(draftsId, messageId, AccountActivityListener.ActivityType.EMAIL_RECEIVED));
    }

    @Test
    void appendWhichIsNotADeliveryShouldBeIgnored() throws Exception {
        append(bobSession, MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS), IS_NOT_DELIVERY);

        assertOnlySentinelIsPublished();
    }

    @Test
    void deliveryIntoTeamMailboxShouldBeIgnored() throws Exception {
        MailboxSession teamSession = mailboxManager.createSystemSession(marketing.owner());
        append(teamSession, marketing.inboxPath(), IS_DELIVERY);

        assertOnlySentinelIsPublished();
    }

    @Test
    void failedPublishShouldGoToEventDeadLetters() throws Exception {
        rabbitMQExtension.getRabbitMQ().pause();
        try {
            append(bobSession, MailboxPath.inbox(BOB), IS_DELIVERY);

            await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(eventDeadLetters.groupsWithFailedEvents().collectList().block())
                    .containsExactly(testee.getDefaultGroup()));
        } finally {
            rabbitMQExtension.getRabbitMQ().unpause();
        }
    }

    @Test
    void configurationShouldDefaultToActivityExchange() {
        assertThat(AccountActivityListener.Configuration.from(new BaseHierarchicalConfiguration()))
            .isEqualTo(new AccountActivityListener.Configuration("activity"));
    }

    @Test
    void configurationShouldReadExchange() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("exchange", "custom");

        assertThat(AccountActivityListener.Configuration.from(configuration))
            .isEqualTo(new AccountActivityListener.Configuration("custom"));
    }

    @Test
    void configurationShouldRejectBlankExchange() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("exchange", " ");

        assertThatThrownBy(() -> AccountActivityListener.Configuration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertOnlySentinelIsPublished() throws Exception {
        MessageId sentinel = append(bobSession, MailboxPath.inbox(BOB), IS_DELIVERY);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(published)
            .extracting(event -> messageIdOf(event.body()))
            .containsExactly(sentinel.serialize()));
    }

    private String messageIdOf(String body) throws Exception {
        return new ObjectMapper().readTree(body).at("/data/messageId").asText();
    }

    private MailboxId mailboxId(MailboxSession session, MailboxPath path) throws Exception {
        return mailboxManager.getMailbox(path, session).getId();
    }

    private MessageId append(MailboxSession session, MailboxPath path, boolean isDelivery) throws Exception {
        return mailboxManager.getMailbox(path, session)
            .appendMessage(MessageManager.AppendCommand.builder()
                .isDelivery(isDelivery)
                .build(MESSAGE), session)
            .getId()
            .getMessageId();
    }
}
